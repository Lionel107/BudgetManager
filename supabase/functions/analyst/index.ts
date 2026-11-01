// ============================================================================
// Edge Function : analyst  —  Agent Analyste (le copilote avec qui on discute)
//
// Deux modes :
//   • mode "bilan" : rédige un bilan mensuel.
//   • mode "chat"  : répond librement à l'utilisateur (conversation multi-tours).
//
// CONTEXTE COMPLET À CHAQUE APPEL : la fonction recharge elle-même, depuis la base
// (RLS via le JWT de l'utilisateur), l'intégralité de son profil et de son budget,
// puis le résume en une FICHE courte en français, problèmes en tête
// (voir _shared/coach.ts → loadFullContext + buildBriefing). Le `context` envoyé par
// l'app desktop n'est plus nécessaire (la PWA mobile n'en envoie pas).
//
// PRINCIPE : l'IA ne calcule PAS. Tous les montants sont déterministes.
//
// Clé Gemini = PAR UTILISATEUR (corps { geminiKey }), utilisée puis jetée, jamais
// stockée. Sans clé → 422 (le client affiche alors le bilan chiffré brut).
//
// Déploiement : supabase functions deploy analyst   (aucun secret à définir)
// ============================================================================

import { createClient } from "https://esm.sh/@supabase/supabase-js@2";
import { askGemini, buildBriefing, ChatTurn, loadFullContext, mentionsTransactions, PERSONA, REMINDER } from "../_shared/coach.ts";

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

/** Nombre max de tours d'historique renvoyés à Gemini (les plus récents). */
const MAX_HISTORY = 20;

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });

  try {
    const authHeader = req.headers.get("Authorization");
    if (!authHeader) return json({ error: "Non authentifié." }, 401);

    let body: any = {};
    try { body = await req.json(); } catch { /* corps vide */ }

    const geminiKey: string | undefined = body?.geminiKey;
    const mode: string = body?.mode === "chat" ? "chat" : "bilan";
    const history: ChatTurn[] = Array.isArray(body?.history) ? body.history : [];
    const question: string = String(body?.question ?? "").trim();

    if (!geminiKey) {
      return json({ error: "Ajoute ta clé Gemini dans les Réglages pour discuter avec l'analyste." }, 422);
    }

    const supabase = createClient(
      Deno.env.get("SUPABASE_URL")!,
      Deno.env.get("SUPABASE_ANON_KEY")!,
      { global: { headers: { Authorization: authHeader } } },
    );
    const full = await loadFullContext(supabase);
    // Les dernières opérations ne sont ajoutées que si la question en parle (fiche plus légère).
    const briefing = buildBriefing(full, mode === "bilan" || mentionsTransactions(question));
    const system = `${PERSONA}\n\n${briefing}`;

    let turns: ChatTurn[];
    if (mode === "chat") {
      // L'historique peut déjà contenir la question en dernier tour : on évite le doublon.
      const past = history.slice(-MAX_HISTORY);
      const last = past[past.length - 1];
      if (last && last.role === "user" && last.text.trim() === question) past.pop();
      turns = [...past, { role: "user", text: `${question || "Salut !"}\n\n${REMINDER}` }];
    } else {
      turns = [{ role: "user", text: BILAN_REQUEST }];
    }

    const reply = await askGemini(geminiKey, system, turns, { temperature: mode === "chat" ? 0.8 : 0.7 });
    return json({ reply });
  } catch (e) {
    console.error(e);
    return json({ error: String(e) }, 500);
  }
});

const BILAN_REQUEST = `Fais-moi le BILAN de mon mois en cours, en Markdown, structuré ainsi :
1. Une phrase d'accroche sur la tendance générale (sincère : positive si ça va, franche si ça coince).
2. **Budgets** : seulement les points de « À signaler » qui concernent les budgets, puis en une ligne ceux qui sont bien tenus. Ne commente pas les dépenses qui respectent leur budget.
3. **Objectifs** : où j'en suis, et quoi faire pour ceux en retard.
4. **Jusqu'à 3 actions concrètes et chiffrées** pour ce mois (moins s'il n'y a rien d'utile à dire : si tout va bien, dis-le simplement).
Ne dépasse pas ~200 mots.`;
