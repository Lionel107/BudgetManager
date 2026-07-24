// ============================================================================
// Edge Function : financial-advisor
// Analyse les 12 derniers mois de l'utilisateur (RLS appliquée via son JWT),
// calcule des statistiques DÉTERMINISTES (saisonnalité + budget annuel lissé),
// puis demande à Gemini un accompagnement rédigé.
//
// Clé Gemini = PAR UTILISATEUR : envoyée par le client dans le corps de la requête
// ({ "geminiKey": "..." }), utilisée côté serveur pour l'appel, JAMAIS stockée
// (ni base, ni secret, ni log). Sans clé, seule l'analyse chiffrée est renvoyée.
//
// Déploiement : supabase functions deploy financial-advisor  (aucun secret à définir)
// ============================================================================

import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const MOIS_FR = ["", "janvier", "février", "mars", "avril", "mai", "juin",
  "juillet", "août", "septembre", "octobre", "novembre", "décembre"];

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { ...corsHeaders, "Content-Type": "application/json" },
  });
}

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });

  try {
    const authHeader = req.headers.get("Authorization");
    if (!authHeader) return json({ error: "Non authentifié." }, 401);

    // Clé Gemini de l'utilisateur, envoyée par le client (jamais stockée côté serveur).
    let geminiKey: string | undefined;
    try {
      const body = await req.json();
      geminiKey = body?.geminiKey;
    } catch { /* pas de corps de requête */ }

    // Client Supabase agissant AU NOM de l'utilisateur (RLS appliquée)
    const supabase = createClient(
      Deno.env.get("SUPABASE_URL")!,
      Deno.env.get("SUPABASE_ANON_KEY")!,
      { global: { headers: { Authorization: authHeader } } },
    );

    // Fenêtre de 12 mois (en UTC, cohérent avec les dates stockées)
    const now = new Date();
    const monthKeys = monthKeysWindow(now); // ["2025-08", ..., "2026-07"]
    const fromIso = new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth() - 11, 1)).toISOString();

    const { data: txns, error } = await supabase
      .from("transactions")
      .select("amount, transaction_type, date, category:categories(name)")
      .gte("date", fromIso);

    if (error) return json({ error: "Lecture des transactions: " + error.message }, 500);
    if (!txns || txns.length === 0) {
      return json({ error: "Pas assez de données. Saisis quelques transactions d'abord." }, 422);
    }

    // ---- Statistiques déterministes ----
    let income = 0, expenses = 0;
    const perCat: Record<string, { total: number; byMonth: Record<string, number> }> = {};

    for (const t of txns as any[]) {
      const amt = Number(t.amount) || 0;
      const monthKey = String(t.date).slice(0, 7); // YYYY-MM
      if (t.transaction_type === "INCOME") { income += amt; continue; }
      if (t.transaction_type !== "EXPENSE") continue; // ignore les transferts
      expenses += amt;
      const cat = t.category?.name ?? "Sans catégorie";
      perCat[cat] ??= { total: 0, byMonth: {} };
      perCat[cat].total += amt;
      perCat[cat].byMonth[monthKey] = (perCat[cat].byMonth[monthKey] ?? 0) + amt;
    }

    const categories = Object.entries(perCat).map(([name, d]) => {
      // Valeurs par mois sur la fenêtre (mois sans dépense = 0) -> "mois typique" = médiane
      const values = monthKeys.map((k) => d.byMonth[k] ?? 0);
      const med = median(values);

      // Un mois est un PIC s'il dépasse nettement le mois typique
      // (excédent absolu significatif ET nettement au-dessus de la médiane).
      const spikeIdx = values
        .map((v, i) => ({ v, i }))
        .filter(({ v }) => (v - med) >= 200 && v >= med * 1.4);

      // Excédent saisonnier = ce qui dépasse le mois typique sur les mois de pic
      const excess = spikeIdx.reduce((s, { v }) => s + (v - med), 0);

      // Saisonnier : quelques pics (1 à 4 mois) portant un excédent notable
      const seasonal = d.total >= 150 && spikeIdx.length >= 1 && spikeIdx.length <= 4 && excess >= 200;

      const peakMonths = seasonal
        ? spikeIdx.map(({ i }) => MOIS_FR[parseInt(monthKeys[i].slice(5, 7), 10)])
        : [];

      return {
        name,
        annualTotal: round2(d.total),
        monthlyAverage: round2(d.total / 12),       // pour le budget annuel lissé
        seasonal,
        peakMonths,
        seasonalProvision: seasonal ? round2(excess / 12) : 0, // à mettre de côté chaque mois
      };
    }).sort((a, b) => b.annualTotal - a.annualTotal);

    const seasonal = categories.filter((c) => c.seasonal);
    const savings = income - expenses;
    const analysis = {
      period: { from: fromIso.slice(0, 10), to: now.toISOString().slice(0, 10), months: 12 },
      totals: {
        income: round2(income),
        expenses: round2(expenses),
        savings: round2(savings),
        savingsRatePct: income > 0 ? round2((savings / income) * 100) : 0,
      },
      categories,
      seasonal,
    };

    // ---- Accompagnement rédigé par Gemini (dégradé gracieux si pas de clé) ----
    // La clé vient du client (clé perso de l'utilisateur), jamais du serveur.
    let advice: { summary: string; tips: string[] } | null = null;
    if (geminiKey) {
      try {
        advice = await askGemini(geminiKey, analysis);
      } catch (e) {
        console.error("Gemini:", e);
      }
    }

    return json({ analysis, advice });
  } catch (e) {
    console.error(e);
    return json({ error: String(e) }, 500);
  }
});

function round2(n: number): number {
  return Math.round(n * 100) / 100;
}

// 12 clés de mois "YYYY-MM" (UTC) se terminant au mois courant.
function monthKeysWindow(now: Date): string[] {
  const keys: string[] = [];
  for (let i = 11; i >= 0; i--) {
    const dt = new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth() - i, 1));
    keys.push(`${dt.getUTCFullYear()}-${String(dt.getUTCMonth() + 1).padStart(2, "0")}`);
  }
  return keys;
}

function median(arr: number[]): number {
  if (arr.length === 0) return 0;
  const s = [...arr].sort((a, b) => a - b);
  const n = s.length;
  return n % 2 ? s[(n - 1) / 2] : (s[n / 2 - 1] + s[n / 2]) / 2;
}

async function askGemini(
  key: string,
  analysis: unknown,
): Promise<{ summary: string; tips: string[] }> {
  const prompt = `Tu es un accompagnant de gestion budgétaire bienveillant et concret, pour un particulier en France (euros).
Voici l'analyse déterministe de ses 12 derniers mois (montants déjà calculés, ne les recalcule pas) :
${JSON.stringify(analysis)}

Rédige un accompagnement PERSONNALISÉ en français. Insiste sur la SAISONNALITÉ : les catégories marquées "seasonal" ont des dépenses concentrées sur certains mois (champ peakMonths, ex. assurance en mars, vacances l'été, cadeaux en décembre). Recommande de mettre de côté chaque mois le montant "seasonalProvision" (€/mois) pour absorber ces pics sans être surpris. Utilise "monthlyAverage" pour parler du coût lissé d'une catégorie.
Réponds UNIQUEMENT en JSON valide, sans texte autour, au format :
{"summary": "2-3 phrases de synthèse chaleureuse et utile", "tips": ["conseil 1", "conseil 2", "conseil 3", "conseil 4"]}`;

  const url =
    "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=" +
    key;
  const resp = await fetch(url, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      contents: [{ parts: [{ text: prompt }] }],
      generationConfig: { temperature: 0.7, responseMimeType: "application/json" },
    }),
  });
  if (!resp.ok) throw new Error("Gemini HTTP " + resp.status);
  const data = await resp.json();
  const text = data?.candidates?.[0]?.content?.parts?.[0]?.text ?? "";
  const parsed = JSON.parse(text);
  return {
    summary: String(parsed.summary ?? ""),
    tips: Array.isArray(parsed.tips) ? parsed.tips.map((t: unknown) => String(t)) : [],
  };
}
