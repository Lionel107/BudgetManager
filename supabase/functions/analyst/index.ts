// ============================================================================
// Edge Function : analyst  —  Agent Analyste (coach du respect du budget)
//
// Deux modes :
//   • mode "bilan" : rédige un bilan mensuel bienveillant à partir des chiffres
//     DÉTERMINISTES déjà calculés côté client (respect des budgets + objectifs).
//   • mode "chat"  : répond à une question de l'utilisateur en s'appuyant sur ce
//     même contexte + l'historique de conversation.
//
// PRINCIPE : l'IA ne calcule PAS. Tous les montants viennent du `context` fourni
// par le client (source de vérité = AnalysisRepository, déterministe). L'IA se
// contente de rédiger / arbitrer / conseiller.
//
// Clé Gemini = PAR UTILISATEUR (corps { geminiKey }), utilisée puis jetée, jamais
// stockée. Sans clé → 422 (le client affiche alors le bilan chiffré brut).
//
// Déploiement : supabase functions deploy analyst   (aucun secret à définir)
// ============================================================================

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

interface ChatTurn { role: string; text: string }

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });

  try {
    if (!req.headers.get("Authorization")) return json({ error: "Non authentifié." }, 401);

    let body: any = {};
    try { body = await req.json(); } catch { /* corps vide */ }

    const geminiKey: string | undefined = body?.geminiKey;
    const mode: string = body?.mode === "chat" ? "chat" : "bilan";
    const context = body?.context ?? {};
    const history: ChatTurn[] = Array.isArray(body?.history) ? body.history : [];
    const question: string = String(body?.question ?? "");

    if (!geminiKey) {
      return json({ error: "Ajoute ta clé Gemini dans les Réglages pour discuter avec l'analyste." }, 422);
    }

    const prompt = mode === "chat"
      ? buildChatPrompt(context, history, question)
      : buildBilanPrompt(context);

    const reply = await askGemini(geminiKey, prompt);
    return json({ reply });
  } catch (e) {
    console.error(e);
    return json({ error: String(e) }, 500);
  }
});

const SYSTEM = `Tu es l'analyste budgétaire personnel de l'utilisateur (particulier en France, en euros).
Ton rôle : vérifier s'il respecte ses budgets et ses objectifs, et l'aider concrètement à les tenir.
Règles STRICTES :
- Tous les chiffres te sont FOURNIS (déjà calculés). Ne recalcule rien, ne invente aucun montant.
- Ton bienveillant, encourageant, jamais moralisateur ni alarmiste. Tutoie l'utilisateur.
- Raisonne sur l'ANNÉE quand c'est pertinent (un pic isolé n'est pas un problème si le cumul reste sain).
- Sois concret : propose des actions précises (quelle catégorie réduire, de combien).
- Reste bref et lisible. Utilise un peu de Markdown (gras, listes) mais pas d'excès.`;

function buildBilanPrompt(context: unknown): string {
  return `${SYSTEM}

Voici le point de la situation (chiffres déterministes) :
${JSON.stringify(context)}

Rédige un BILAN du mois en Markdown, structuré ainsi :
1. Une phrase d'accroche chaleureuse sur la tendance générale.
2. **Budgets** : ceux qui sont respectés / à surveiller / dépassés (nomme-les).
3. **Objectifs** : où en est chaque objectif, et quoi faire pour ceux en retard.
4. **3 actions concrètes** à faire ce mois-ci (liste à puces).
Ne dépasse pas ~200 mots.`;
}

function buildChatPrompt(context: unknown, history: ChatTurn[], question: string): string {
  const convo = history
    .map((t) => `${t.role === "assistant" ? "Analyste" : "Utilisateur"}: ${t.text}`)
    .join("\n");
  return `${SYSTEM}

Contexte financier actuel (chiffres déterministes) :
${JSON.stringify(context)}

${convo ? `Conversation jusqu'ici :\n${convo}\n` : ""}
Nouvelle question de l'utilisateur : ${question}

Réponds directement, en Markdown léger, sans te répéter.`;
}

async function askGemini(key: string, prompt: string): Promise<string> {
  // Cascade : bascule au modèle suivant si quota (429) ou indisponible (503).
  const models = ["gemini-2.5-flash", "gemini-2.0-flash", "gemini-1.5-flash"];
  const reqBody = JSON.stringify({
    contents: [{ parts: [{ text: prompt }] }],
    // thinkingBudget: 0 → pas de "raisonnement" long = réponse rapide
    generationConfig: { temperature: 0.7, thinkingConfig: { thinkingBudget: 0 } },
  });
  let lastErr = "";
  for (const model of models) {
    const url = `https://generativelanguage.googleapis.com/v1beta/models/${model}:generateContent?key=${key}`;
    const resp = await fetch(url, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: reqBody,
    });
    if (resp.ok) {
      const data = await resp.json();
      const text = data?.candidates?.[0]?.content?.parts?.[0]?.text ?? "";
      if (text) return String(text);
      lastErr = `réponse vide (${model})`;
      continue;
    }
    lastErr = `HTTP ${resp.status} (${model})`;
    if (resp.status !== 429 && resp.status !== 503) throw new Error("Gemini " + lastErr);
  }
  throw new Error("Gemini : tous les modèles indisponibles — " + lastErr);
}
