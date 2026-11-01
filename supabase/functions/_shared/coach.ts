// ============================================================================
// Module partagé des agents IA : personnalité commune + contexte COMPLET.
//
//   • PERSONA          : le ton et les règles de conduite communs à tous les agents
//                        (analyst, budget-planner, financial-advisor).
//   • loadFullContext  : charge À CHAQUE APPEL l'intégralité du profil et du budget
//                        de l'utilisateur (RLS via son JWT), chiffres DÉTERMINISTES.
//   • askGemini        : appel Gemini avec instruction système + conversation
//                        multi-tours, cascade de modèles sur 429/503.
//
// Déploiement : le dossier _shared est embarqué automatiquement par
//   supabase functions deploy <nom>
// ============================================================================

// deno-lint-ignore-file no-explicit-any

export const PERSONA = `Tu es le copilote budgétaire perso de l'utilisateur (particulier en France, euros).

QUI TU ES
- Le pote calé en finances : détendu, chaleureux, un brin d'humour quand ça s'y prête. Tu tutoies, tu parles naturellement, sans jargon ni ton de banquier. Emojis avec parcimonie (0 à 2 par message).
- Compatissant : si l'utilisateur exprime du stress, de la fatigue, un coup dur ou de la culpabilité, accueille-le d'abord en une phrase sincère, PUIS aide-le concrètement. Jamais de jugement, jamais de morale, jamais de culpabilisation.

TA MISSION — PRIORITÉ ABSOLUE, RIEN NE PASSE AVANT
- Aider l'utilisateur à gérer son budget de façon OPTIMALE : tenir ses budgets, atteindre ses objectifs, épargner, anticiper les grosses dépenses.
- Ton côté cool ne doit JAMAIS adoucir une vérité utile. Si quelque chose cloche vraiment, tu le dis clairement, chiffres à l'appui, et tu proposes quoi faire. Gentil, oui ; complaisant, jamais.
- Franc : pas de langue de bois. Quand on te demande ton avis, donne-le, tranché.

RÉALISME — QUAND CRITIQUER, QUAND NE PAS LE FAIRE
- Une dépense qui rentre dans son budget est une dépense VALIDÉE. Ne la remets pas en cause et ne suggère pas de la réduire « par principe » : un budget est fait pour être dépensé.
- Ne critique JAMAIS : les dépenses essentielles (logement, courses, santé, énergie, transports, assurances, impôts, enfants…), les catégories "essential": true, tout ce qui figure dans profile.neverCut, ni les petits plaisirs qui restent dans leur budget.
- Une catégorie essentielle à 100 % en début de mois (loyer, prélèvements) est NORMALE.
- Ne signale un problème QUE s'il est réel : budget non essentiel "DÉPASSÉ", "RYTHME_RAPIDE" ou "PROCHE_LIMITE" ; objectif en retard ; marge du mois négative ; dépense vraiment inhabituelle. Un pic isolé n'est pas grave si le cumul annuel reste sain.
- Pour économiser, vise les leviers qui comptent vraiment (gros postes non essentiels, abonnements, dérives répétées), chiffrés et faisables, sans toucher à neverCut. Une économie de quelques euros ne vaut pas une remarque.
- Félicite sincèrement (et brièvement) ce qui va bien : budget tenu, objectif en avance, bonne épargne.

CHIFFRES
- Tous les chiffres sont dans le contexte, déjà calculés. Ne les invente pas et ne refais pas de calculs compliqués : cite-les. Si une info manque, dis-le et demande.
- Question « je peux me permettre X ? » : regarde currentMonth.margin (marge restante une fois les charges fixes à venir payées), le "remaining" du budget concerné et les objectifs. Réponds d'abord oui / non / oui mais…, puis explique en 1 ou 2 phrases.
- Le profil (priorités, projets, confort, notes) te sert à personnaliser : utilise-le sans le réciter.

FORME
- Conversation naturelle : réponses courtes par défaut (2 à 5 phrases), plus longues seulement si on te le demande ou pour un bilan. Markdown léger, pas de pavés ni de listes systématiques. Une seule question à la fois si tu as besoin d'une précision.`;

// ---------------------------------------------------------------------------
// Contexte complet
// ---------------------------------------------------------------------------

function round2(n: number): number { return Math.round(n * 100) / 100; }

const MOIS_FR = ["", "janvier", "février", "mars", "avril", "mai", "juin",
  "juillet", "août", "septembre", "octobre", "novembre", "décembre"];

function isoDay(d: Date): string { return d.toISOString().slice(0, 10); }

function addPeriod(d: Date, freq: string, interval: number): Date {
  const n = new Date(d);
  const k = Math.max(1, interval || 1);
  switch (freq) {
    case "DAILY": n.setUTCDate(n.getUTCDate() + k); break;
    case "WEEKLY": n.setUTCDate(n.getUTCDate() + 7 * k); break;
    case "BI_WEEKLY": n.setUTCDate(n.getUTCDate() + 14 * k); break;
    case "QUARTERLY": n.setUTCMonth(n.getUTCMonth() + 3 * k); break;
    case "YEARLY": n.setUTCFullYear(n.getUTCFullYear() + k); break;
    default: n.setUTCMonth(n.getUTCMonth() + k); // MONTHLY
  }
  return n;
}

/** Montant mensuel équivalent d'une récurrence (pour les charges fixes). */
function monthlyEquivalent(amount: number, freq: string, interval: number): number {
  const k = Math.max(1, interval || 1);
  const perYear: Record<string, number> = {
    DAILY: 365, WEEKLY: 52, BI_WEEKLY: 26, MONTHLY: 12, QUARTERLY: 4, YEARLY: 1,
  };
  return round2((amount * (perYear[freq] ?? 12)) / k / 12);
}

/**
 * Charge l'intégralité du profil + budget de l'utilisateur (RLS appliquée par le
 * client Supabase passé, créé avec son JWT). Tout est calculé ici, l'IA ne fait
 * que lire. Ne lève jamais : une table absente donne simplement une section vide.
 */
export async function loadFullContext(supabase: any): Promise<Record<string, unknown>> {
  const now = new Date();
  const y = now.getUTCFullYear(), m = now.getUTCMonth();
  const monthStart = new Date(Date.UTC(y, m, 1));
  const monthEnd = new Date(Date.UTC(y, m + 1, 0, 23, 59, 59));
  const daysInMonth = monthEnd.getUTCDate();
  const dayOfMonth = now.getUTCDate();
  const yearStart = new Date(Date.UTC(y, 0, 1));
  const daysInYear = (Date.UTC(y + 1, 0, 1) - yearStart.getTime()) / 86400000;
  const dayOfYear = Math.floor((now.getTime() - yearStart.getTime()) / 86400000) + 1;
  const weekStart = new Date(Date.UTC(y, m, dayOfMonth - ((now.getUTCDay() + 6) % 7)));
  const windowStart = new Date(Date.UTC(y, m - 11, 1)); // 12 mois glissants (inclut le 1er janvier)

  const safe = async (q: any) => {
    try { const r = await q; return (r?.data ?? null) as any; } catch { return null; }
  };

  const [prof, accounts, cats, budgets, objectives, recurring, txns] = await Promise.all([
    safe(supabase.from("user_profiles").select("monthly_income, priorities, projects, never_cut, comfort, notes").maybeSingle()),
    safe(supabase.from("accounts").select("name, account_type, balance").eq("is_active", true).order("display_order")),
    safe(supabase.from("categories").select("name, category_type, is_essential").eq("is_active", true)),
    safe(supabase.from("budgets").select("period_type, budget_limit, category:categories(name, is_essential)")),
    safe(supabase.from("objectives").select("title, type, target_amount, target_date, created_at, category:categories(name)").eq("is_active", true)),
    safe(supabase.from("recurring_transactions").select("title, amount, transaction_type, frequency_type, repeat_interval, next_due_date, end_date, category:categories(name)").eq("is_active", true)),
    safe(supabase.from("transactions").select("title, amount, transaction_type, date, category:categories(name)").gte("date", windowStart.toISOString()).order("date", { ascending: false })),
  ]);

  const tx = (txns ?? []) as any[];
  const essentialOf: Record<string, boolean> = {};
  for (const c of (cats ?? []) as any[]) essentialOf[c.name] = c.is_essential !== false;

  // ---- Agrégats 12 mois + mois en cours ----
  const monthKey = (d: string) => String(d).slice(0, 7);
  const curKey = isoDay(monthStart).slice(0, 7);
  let income12 = 0;
  let curIncome = 0, curExpenses = 0;
  const perCatMonth: Record<string, Record<string, number>> = {};
  const curPerCat: Record<string, number> = {};
  const ytdPerCat: Record<string, number> = {};
  const weekPerCat: Record<string, number> = {};
  for (const t of tx) {
    const amt = Number(t.amount) || 0;
    const mk = monthKey(t.date);
    const d = new Date(t.date);
    if (t.transaction_type === "INCOME") {
      income12 += amt;
      if (mk === curKey) curIncome += amt;
      continue;
    }
    if (t.transaction_type !== "EXPENSE") continue;
    const cat = t.category?.name ?? "Sans catégorie";
    (perCatMonth[cat] ??= {})[mk] = (perCatMonth[cat][mk] ?? 0) + amt;
    if (mk === curKey) { curExpenses += amt; curPerCat[cat] = (curPerCat[cat] ?? 0) + amt; }
    if (d >= yearStart) ytdPerCat[cat] = (ytdPerCat[cat] ?? 0) + amt;
    if (d >= weekStart) weekPerCat[cat] = (weekPerCat[cat] ?? 0) + amt;
  }

  // Nombre de mois réellement couverts par l'historique (1 à 12) : évite de diviser
  // par 12 quand l'utilisateur n'a que quelques mois de données.
  const oldest = tx.length ? new Date(tx[tx.length - 1].date) : now;
  const monthsCovered = Math.min(12, Math.max(1,
    (y - oldest.getUTCFullYear()) * 12 + (m - oldest.getUTCMonth()) + 1));

  const spendingHistory = Object.entries(perCatMonth).map(([name, byMonth]) => {
    const total = Object.values(byMonth).reduce((a, b) => a + b, 0);
    const avg = total / monthsCovered;
    const cur = curPerCat[name] ?? 0;
    return {
      category: name,
      essential: essentialOf[name] !== false,
      monthlyAverage: round2(avg),
      thisMonth: round2(cur),
      // Dépense du mois nettement au-dessus de l'habitude (déterministe)
      unusualThisMonth: avg > 0 && cur - avg >= 100 && cur >= avg * 1.5,
    };
  }).sort((a, b) => b.monthlyAverage - a.monthlyAverage);

  // ---- Budgets : consommé sur la période + rythme ----
  const budgetList = ((budgets ?? []) as any[]).map((b) => {
    const name = b.category?.name ?? "?";
    const essential = b.category?.is_essential !== false;
    const limit = Number(b.budget_limit) || 0;
    let spent: number, progress: number | null;
    switch (b.period_type) {
      case "YEARLY": spent = ytdPerCat[name] ?? 0; progress = dayOfYear / daysInYear; break;
      case "WEEKLY": spent = weekPerCat[name] ?? 0; progress = null; break;
      default: spent = curPerCat[name] ?? 0; progress = dayOfMonth / daysInMonth;
    }
    let status = "OK";
    if (spent > limit + 0.5) status = "DÉPASSÉ";
    else if (!essential && spent >= limit * 0.9) status = "PROCHE_LIMITE";
    else if (!essential && progress != null && progress >= 0.2 && spent > limit * progress * 1.25) status = "RYTHME_RAPIDE";
    return {
      category: name,
      period: b.period_type,
      essential,
      limit: round2(limit),
      spent: round2(spent),
      remaining: round2(limit - spent),
      pct: limit > 0 ? Math.round((spent / limit) * 100) : null,
      status,
    };
  });

  // ---- Charges fixes (récurrences) + ce qui reste à tomber ce mois-ci ----
  let dueRestOfMonth = 0, incomeRestOfMonth = 0;
  const recurringList = ((recurring ?? []) as any[]).map((r) => {
    const amt = Number(r.amount) || 0;
    let occ = 0;
    let d = new Date(String(r.next_due_date) + "T00:00:00Z");
    const end = r.end_date ? new Date(String(r.end_date) + "T23:59:59Z") : null;
    while (d <= monthEnd && occ < 40 && (!end || d <= end)) {
      if (d >= new Date(isoDay(now) + "T00:00:00Z")) occ++;
      d = addPeriod(d, r.frequency_type, r.repeat_interval);
    }
    if (r.transaction_type === "EXPENSE") dueRestOfMonth += occ * amt;
    if (r.transaction_type === "INCOME") incomeRestOfMonth += occ * amt;
    return {
      title: r.title,
      type: r.transaction_type,
      amount: round2(amt),
      frequency: r.frequency_type,
      monthlyEquivalent: monthlyEquivalent(amt, r.frequency_type, r.repeat_interval),
      nextDue: r.next_due_date,
      category: r.category?.name ?? null,
    };
  });
  const fixedChargesMonthly = round2(recurringList
    .filter((r) => r.type === "EXPENSE")
    .reduce((s, r) => s + r.monthlyEquivalent, 0));

  // ---- Profil ----
  const statedIncome = Number(prof?.monthly_income) || 0;
  const profile = prof ? {
    monthlyIncomeDeclared: statedIncome || null,
    priorities: prof.priorities ?? null,
    projects: prof.projects ?? null,
    neverCut: prof.never_cut ?? null,
    comfort: prof.comfort ?? null,
    notes: prof.notes ?? null,
  } : null;

  // Marge du mois = revenus du mois (réels, ou déclarés s'ils ne sont pas encore
  // tombés) − dépenses déjà faites − charges fixes encore à prélever d'ici la fin du mois.
  const expectedIncome = Math.max(curIncome + incomeRestOfMonth, statedIncome);
  const margin = round2(expectedIncome - curExpenses - dueRestOfMonth);

  // ---- Objectifs ----
  const objectiveList = ((objectives ?? []) as any[]).map((o) => {
    const target = Number(o.target_amount) || 0;
    let monthsLeft: number | null = null;
    if (o.target_date) {
      const td = new Date(String(o.target_date) + "T00:00:00Z");
      monthsLeft = Math.max(1, (td.getUTCFullYear() - y) * 12 + (td.getUTCMonth() - m));
    }
    return {
      title: o.title,
      type: o.type,
      targetAmount: round2(target),
      targetDate: o.target_date ?? null,
      category: o.category?.name ?? null,
      // Pour un objectif d'épargne : effort mensuel nécessaire d'ici l'échéance
      monthlySavingsNeeded: o.type === "SAVINGS" && monthsLeft ? round2(target / monthsLeft) : null,
    };
  });

  // ---- Dernières opérations (30 jours, 30 max) ----
  const since30 = new Date(now.getTime() - 30 * 86400000);
  const recentTransactions = tx
    .filter((t) => new Date(t.date) >= since30)
    .slice(0, 30)
    .map((t) => ({
      date: String(t.date).slice(0, 10),
      title: t.title,
      amount: round2(Number(t.amount) || 0),
      type: t.transaction_type,
      category: t.category?.name ?? null,
    }));

  return {
    today: isoDay(now),
    month: `${MOIS_FR[m + 1]} ${y}`,
    dayOfMonth,
    daysInMonth,
    profile,
    accounts: ((accounts ?? []) as any[]).map((a) => ({ name: a.name, type: a.account_type, balance: round2(Number(a.balance) || 0) })),
    currentMonth: {
      income: round2(curIncome),
      expenses: round2(curExpenses),
      fixedChargesStillDue: round2(dueRestOfMonth),
      incomeStillExpected: round2(incomeRestOfMonth),
      margin,
    },
    history: {
      monthsCovered,
      averageMonthlyIncome: round2(income12 / monthsCovered),
      averageMonthlyExpenses: round2(spendingHistory.reduce((s, c) => s + c.monthlyAverage, 0)),
    },
    budgets: budgetList,
    objectives: objectiveList,
    fixedCharges: { monthlyTotal: fixedChargesMonthly, items: recurringList },
    spendingByCategory: spendingHistory,
    recentTransactions,
  };
}

// ---------------------------------------------------------------------------
// Gemini
// ---------------------------------------------------------------------------

export interface ChatTurn { role: string; text: string }

/**
 * Modèles essayés dans l'ordre (meilleur d'abord), tous testés OK sur le palier
 * gratuit en nov. 2026. Quand l'un est épuisé (429), saturé (503) ou retiré (404),
 * on passe au suivant. La conversation complète est renvoyée à chaque appel : changer
 * de modèle en cours de discussion ne fait donc PAS perdre le fil.
 */
const MODELS = ["gemini-3.6-flash", "gemini-3.5-flash", "gemini-3.1-flash-lite", "gemini-2.5-flash"];

/**
 * Appelle Gemini avec une instruction système et une conversation multi-tours.
 * Cascade sur MODELS ; seules une clé invalide/refusée (401/403) stoppe tout.
 */
export async function askGemini(
  key: string,
  system: string,
  turns: ChatTurn[],
  opts: { temperature?: number; json?: boolean } = {},
): Promise<string> {
  // Gemini exige une alternance user/model qui commence par "user".
  const contents: { role: string; parts: { text: string }[] }[] = [];
  for (const t of turns) {
    const role = t.role === "assistant" || t.role === "model" ? "model" : "user";
    const text = String(t.text ?? "").trim();
    if (!text) continue;
    const last = contents[contents.length - 1];
    if (last && last.role === role) last.parts[0].text += "\n\n" + text;
    else contents.push({ role, parts: [{ text }] });
  }
  while (contents.length && contents[0].role !== "user") contents.shift();
  if (contents.length === 0) contents.push({ role: "user", parts: [{ text: "Bonjour" }] });

  const reqBody = JSON.stringify({
    systemInstruction: { parts: [{ text: system }] },
    contents,
    generationConfig: {
      temperature: opts.temperature ?? 0.8,
      ...(opts.json ? { responseMimeType: "application/json" } : {}),
      // thinkingBudget: 0 → pas de "raisonnement" long = réponse rapide
      thinkingConfig: { thinkingBudget: 0 },
    },
  });
  let lastErr = "";
  for (const model of MODELS) {
    const url = `https://generativelanguage.googleapis.com/v1beta/models/${model}:generateContent?key=${key}`;
    const resp = await fetch(url, { method: "POST", headers: { "Content-Type": "application/json" }, body: reqBody });
    if (resp.ok) {
      const data = await resp.json();
      const text = data?.candidates?.[0]?.content?.parts?.[0]?.text ?? "";
      if (text) return String(text);
      lastErr = `réponse vide (${model})`;
      continue;
    }
    const errBody = await resp.text().catch(() => "");
    lastErr = `HTTP ${resp.status} (${model}) ${errBody.slice(0, 120)}`;
    if (resp.status === 401 || resp.status === 403) throw new Error("Gemini " + lastErr);
    // 400 (réglage refusé par ce modèle), 404 (retiré), 429, 5xx → modèle suivant
  }
  throw new Error("Gemini : tous les modèles indisponibles — " + lastErr);
}
