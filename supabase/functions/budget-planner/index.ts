// ============================================================================
// Edge Function : budget-planner (Phase 5 - D)
// Propose un budget mensuel par catégorie à partir de : historique 12 mois,
// caractère essentiel/superflu des catégories, objectifs, budgets actuels.
// Prend en compte les REMARQUES de l'utilisateur pour réadapter un plan précédent.
//
// Clé Gemini = par utilisateur (corps de requête), jamais stockée. Sans clé :
// plan déterministe basé sur les moyennes (dégradé gracieux).
// Corps : { geminiKey?, remarks?, currentPlan?: [{category, monthlyAmount}] }
// Réponse : { monthlyIncome, summary, plan: [{category, monthlyAmount, rationale, essential}] }
// ============================================================================

import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

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

function round2(n: number): number { return Math.round(n * 100) / 100; }

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

function monthsUntil(dateStr: string | null): number {
  if (!dateStr) return 12;
  const d = new Date(dateStr + "T00:00:00Z");
  const now = new Date();
  const m = (d.getUTCFullYear() - now.getUTCFullYear()) * 12 + (d.getUTCMonth() - now.getUTCMonth());
  return Math.max(1, m);
}

type Cat = { category: string; monthlyAverage: number; essential: boolean; seasonal: boolean };
type Obj = { type: string; targetAmount: number; targetDate: string | null; category: string | null };

/**
 * Plan déterministe ORIENTÉ OBJECTIFS (sans IA) : part des moyennes (pour te
 * connaître), applique les plafonds d'objectifs, puis RÉDUIT les postes non
 * essentiels pour dégager l'épargne mensuelle nécessaire à tes objectifs.
 */
function buildDeterministicPlan(cats: Cat[], objectives: Obj[], monthlyIncome: number) {
  const limitFor: Record<string, number> = {};
  let requiredSavings = 0;
  for (const o of objectives) {
    if (o.type === "SPENDING_LIMIT" && o.category) limitFor[o.category] = o.targetAmount;
    if (o.type === "SAVINGS") requiredSavings += o.targetAmount / monthsUntil(o.targetDate);
  }
  requiredSavings = round2(requiredSavings);

  const plan = cats.map((c) => {
    let amt = c.monthlyAverage;
    let rationale = c.seasonal ? "Provision mensuelle (dépense saisonnière)" : "Basé sur ta moyenne";
    if (limitFor[c.category] != null && amt > limitFor[c.category]) {
      amt = limitFor[c.category];
      rationale = "Plafonné selon ton objectif";
    }
    return { category: c.category, monthlyAmount: round2(amt), rationale, essential: c.essential };
  });

  let summary: string;
  if (requiredSavings > 0) {
    const plannedExpenses = plan.reduce((s, p) => s + p.monthlyAmount, 0);
    const margin = monthlyIncome - plannedExpenses;
    const gap = round2(requiredSavings - margin);
    if (gap > 0) {
      const flexible = plan.filter((p) => !p.essential && p.monthlyAmount > 0);
      const totalFlexible = flexible.reduce((s, p) => s + p.monthlyAmount, 0);
      const maxCut = totalFlexible * 0.6; // plancher : on ne descend pas sous 40% du poste
      const cut = Math.min(gap, maxCut);
      if (cut > 0 && totalFlexible > 0) {
        for (const p of flexible) {
          const reduce = cut * (p.monthlyAmount / totalFlexible);
          p.monthlyAmount = round2(Math.max(p.monthlyAmount * 0.4, p.monthlyAmount - reduce));
          p.rationale = "Réduit pour financer ton objectif d'épargne";
        }
      }
      summary = `Pour tes objectifs, il faut dégager ~${requiredSavings} €/mois d'épargne. J'ai réduit les postes non essentiels en conséquence` +
        (cut < gap ? ". Objectif ambitieux : la marge reste insuffisante, revois le montant/la date ou réduis davantage un poste." : ".");
    } else {
      summary = `Bonne nouvelle : ton objectif (~${requiredSavings} €/mois d'épargne) est déjà couvert par ta marge actuelle. Plan basé sur tes moyennes.`;
    }
  } else {
    summary = "Aucun objectif défini : plan basé sur tes moyennes. Crée un objectif dans l'onglet « Objectifs » pour que je construise un budget qui t'aide à l'atteindre.";
  }
  return { plan, summary };
}

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });

  try {
    const authHeader = req.headers.get("Authorization");
    if (!authHeader) return json({ error: "Non authentifié." }, 401);

    let geminiKey: string | undefined;
    let remarks: string | undefined;
    let currentPlan: unknown;
    try {
      const body = await req.json();
      geminiKey = body?.geminiKey;
      remarks = body?.remarks;
      currentPlan = body?.currentPlan;
    } catch { /* pas de corps */ }

    const supabase = createClient(
      Deno.env.get("SUPABASE_URL")!,
      Deno.env.get("SUPABASE_ANON_KEY")!,
      { global: { headers: { Authorization: authHeader } } },
    );

    const now = new Date();
    const monthKeys = monthKeysWindow(now);
    const fromIso = new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth() - 11, 1)).toISOString();

    const [txnRes, catRes, objRes, budRes] = await Promise.all([
      supabase.from("transactions").select("amount, transaction_type, date, category:categories(name)").gte("date", fromIso),
      supabase.from("categories").select("name, is_essential, category_type").eq("is_active", true).eq("category_type", "EXPENSE"),
      supabase.from("objectives").select("title, type, target_amount, target_date, category:categories(name)").eq("is_active", true),
      supabase.from("budgets").select("budget_limit, category:categories(name)"),
    ]);

    const txns = (txnRes.data ?? []) as any[];
    if (txns.length === 0) return json({ error: "Pas assez de données pour proposer un budget." }, 422);

    const essentialOf: Record<string, boolean> = {};
    const existingCategories: string[] = [];
    for (const c of (catRes.data ?? []) as any[]) {
      essentialOf[c.name] = c.is_essential !== false;
      existingCategories.push(c.name);
    }

    const currentBudgetOf: Record<string, number> = {};
    for (const b of (budRes.data ?? []) as any[]) {
      if (b.category?.name) currentBudgetOf[b.category.name] = Number(b.budget_limit) || 0;
    }

    // Agrégation dépenses par catégorie/mois + revenu
    let income = 0;
    const perCat: Record<string, Record<string, number>> = {};
    for (const t of txns) {
      const amt = Number(t.amount) || 0;
      if (t.transaction_type === "INCOME") { income += amt; continue; }
      if (t.transaction_type !== "EXPENSE") continue;
      const cat = t.category?.name ?? "Sans catégorie";
      const mk = String(t.date).slice(0, 7);
      perCat[cat] ??= {};
      perCat[cat][mk] = (perCat[cat][mk] ?? 0) + amt;
    }

    const monthlyIncome = round2(income / 12);
    const catContext = Object.entries(perCat).map(([name, byMonth]) => {
      const values = monthKeys.map((k) => byMonth[k] ?? 0);
      const total = values.reduce((a, b) => a + b, 0);
      const med = median(values);
      const spikes = values.filter((v) => (v - med) >= 200 && v >= med * 1.4).length;
      const seasonal = total >= 150 && spikes >= 1 && spikes <= 4;
      return {
        category: name,
        monthlyAverage: round2(total / 12),
        essential: essentialOf[name] !== false,
        seasonal,
        currentBudget: currentBudgetOf[name] ?? null,
      };
    }).sort((a, b) => b.monthlyAverage - a.monthlyAverage);

    const objectives = ((objRes.data ?? []) as any[]).map((o) => ({
      title: o.title,
      type: o.type,
      targetAmount: Number(o.target_amount) || 0,
      targetDate: o.target_date,
      category: o.category?.name ?? null,
    }));

    // ---- Construction du plan ----
    type PlanItem = { category: string; monthlyAmount: number; rationale: string; essential: boolean };
    const hasCurrent = Array.isArray(currentPlan) && (currentPlan as any[]).length > 0;

    let plan: PlanItem[];
    let summary: string;

    if (hasCurrent) {
      // L'utilisateur a déjà un plan (potentiellement édité à la main) → on le PRÉSERVE.
      plan = (currentPlan as any[]).map((p) => ({
        category: String(p.category ?? ""),
        monthlyAmount: Number(p.monthlyAmount) || 0,
        rationale: "Ton montant",
        essential: essentialOf[String(p.category)] !== false,
      }));
      summary = "Tes montants actuels sont conservés.";
    } else {
      // Première proposition → plan déterministe orienté objectifs.
      const base = buildDeterministicPlan(catContext, objectives, monthlyIncome);
      plan = base.plan;
      summary = base.summary;
    }

    // Affinage IA (remarques, optimisation objectifs) si clé dispo et Gemini répond.
    if (geminiKey) {
      try {
        const g = await askGeminiPlan(geminiKey, { monthlyIncome, categories: catContext, existingCategories, objectives, remarks, currentPlan });
        if (g.plan.length > 0) {
          summary = g.summary;
          plan = g.plan.map((p) => ({ ...p, essential: essentialOf[p.category] !== false }));
        } else {
          summary = (hasCurrent ? "Tes montants sont conservés. " : summary + " ") + "IA : réponse vide.";
        }
      } catch (e) {
        console.error("Gemini plan:", e);
        const msg = String(e);
        // IMPORTANT : en cas d'échec IA, on NE recalcule PAS (montants conservés).
        // On affiche le détail BRUT de l'erreur Gemini pour diagnostiquer (quelle limite).
        summary = (hasCurrent ? "Tes montants sont conservés. " : summary + " ") +
          `IA non appliquée — ${msg.slice(0, 260)}`;
      }
    } else {
      summary += " [DEBUG] Aucune clé Gemini reçue par la fonction — vérifie Réglages → Clé API.";
    }

    return json({ monthlyIncome, summary, plan });
  } catch (e) {
    console.error(e);
    return json({ error: String(e) }, 500);
  }
});

async function askGeminiPlan(
  key: string,
  ctx: unknown,
): Promise<{ summary: string; plan: { category: string; monthlyAmount: number; rationale: string }[] }> {
  const prompt = `Tu es un planificateur budgétaire bienveillant et réaliste (particulier, France, euros).
Contexte (chiffres déjà calculés sur 12 mois) :
${JSON.stringify(ctx)}

Règles :
- OBJECTIF PRINCIPAL : construis le budget IDÉAL qui permet d'ATTEINDRE les "objectives", quitte à réduire le superflu. Ne te contente PAS de recopier les habitudes ("monthlyAverage") : sers-t'en pour connaître l'utilisateur, mais ajuste pour dégager l'épargne nécessaire aux objectifs.
- Propose un budget MENSUEL par catégorie de dépense, réaliste.
- Utilise EN PRIORITÉ les catégories de "existingCategories" (réutilise leur nom EXACT).
- Si une dépense n'entre dans AUCUNE catégorie existante, tu PEUX proposer une NOUVELLE
  catégorie (nom court et clair) : elle sera créée automatiquement à l'application.
- Priorise les catégories "essential:true" ; comprime plutôt les "essential:false" si besoin d'épargner.
- Les catégories "seasonal:true" ne tombent que certains mois : budgète-les à leur moyenne mensuelle (provision) pour lisser sur l'année, sans t'alarmer d'un mois isolé.
- Intègre les "objectives" : pour un objectif SAVINGS (épargner targetAmount d'ici targetDate), dégage la marge mensuelle nécessaire (revenu - dépenses). Pour SPENDING_LIMIT, respecte le plafond sur la catégorie visée.
- La somme des budgets doit laisser une épargne cohérente vs monthlyIncome.
- Si "remarks" est fourni, AJUSTE le "currentPlan" en tenant compte de la remarque (garde les montants édités par l'utilisateur sauf si la remarque demande de les changer).
Réponds UNIQUEMENT en JSON valide :
{"summary": "2-3 phrases expliquant le plan et l'épargne dégagée", "plan": [{"category": "<nom de catégorie, existante ou nouvelle>", "monthlyAmount": <nombre>, "rationale": "courte justification"}]}`;

  const url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=" + key;
  const resp = await fetch(url, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      contents: [{ parts: [{ text: prompt }] }],
      generationConfig: { temperature: 0.6, responseMimeType: "application/json" },
    }),
  });
  if (!resp.ok) {
    const errBody = await resp.text().catch(() => "");
    throw new Error("Gemini HTTP " + resp.status + " — " + errBody.slice(0, 400));
  }
  const data = await resp.json();
  const text = data?.candidates?.[0]?.content?.parts?.[0]?.text ?? "";
  const parsed = JSON.parse(text);
  return {
    summary: String(parsed.summary ?? ""),
    plan: Array.isArray(parsed.plan)
      ? parsed.plan.map((p: any) => ({
          category: String(p.category ?? ""),
          monthlyAmount: Number(p.monthlyAmount) || 0,
          rationale: String(p.rationale ?? ""),
        }))
      : [],
  };
}
