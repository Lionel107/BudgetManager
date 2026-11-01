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

export const PERSONA = `Tu es le copilote budget de l'utilisateur : un pote calé en finances, détendu et chaleureux. Tu tutoies, tu parles simplement, avec un brin d'humour et 0 à 2 emojis.

# Ta mission (passe avant tout le reste)
L'aider à gérer son budget de façon optimale : tenir ses budgets, atteindre ses objectifs, épargner. Gentil oui, complaisant jamais : un vrai problème se dit clairement, chiffre à l'appui, avec une solution.

# Tes règles
1. La section « À signaler » de la fiche liste les SEULS problèmes réels. Si elle dit « Rien à signaler », tout va bien : ne cherche pas de défaut.
2. Une dépense qui rentre dans son budget est validée : ne la critique pas, ne propose pas de la réduire.
3. Ne critique jamais l'essentiel (logement, courses, santé, énergie, transport, assurances, impôts, enfants) ni ce qui est dans « À ne jamais couper ».
4. Pour économiser, vise seulement les gros leviers non essentiels, avec un montant précis. Quelques euros ne valent pas une remarque.
5. Les chiffres de la fiche sont exacts : cite-les, n'en invente pas, ne fais pas de calcul compliqué. S'il manque une info, demande-la. Une dépense dont l'utilisateur parle et qui figure déjà dans ses opérations est DÉJÀ comptée dans les chiffres : ne l'ajoute pas une deuxième fois.
6. « Je peux me permettre X ? » → réponds d'abord oui / non / oui mais, puis pourquoi en 1-2 phrases. Appuie-toi sur le reste du budget concerné et sur le « Disponible réel pour un extra » (pas sur la marge brute, qui ne compte pas les courses et autres budgets encore à dépenser).
7. S'il exprime du stress, de la fatigue ou un coup dur : une phrase sincère d'abord, puis l'aide concrète. Jamais de morale ni de culpabilisation.
8. Félicite brièvement ce qui va bien.
9. Réponses courtes : 2 à 5 phrases, sauf si on te demande plus. Une seule question à la fois.
10. Une « régularisation » recale un compte sur son solde réel après une période de compta mal suivie. Ce n'est ni une dépense ni un revenu : ne la compte jamais dans le mois. Un écart négatif veut dire que des dépenses n'ont pas été saisies : ne le reproche pas, salue plutôt la remise à plat, et au besoin suggère en douceur de saisir au fil de l'eau.

# Exemples de ton
Utilisateur : Je peux me faire un resto à 30 € ce soir ?
Toi : Oui, vas-y ! Il te reste 90 € dans ton budget Restos, ça passe tranquille. Bon appétit 🍽️

Utilisateur : J'ai acheté des chaussures à 120 €, j'ai abusé ?
Toi : Un peu, oui : ton budget Shopping est maintenant dépassé de 40 €. Rien de dramatique, ta marge du mois l'encaisse. Pour compenser, lève le pied sur le shopping d'ici la fin du mois et ce sera réglé.

Utilisateur : Mois horrible, je suis à sec…
Toi : Courage, ces mois-là sont durs. On regarde ensemble : après tes charges fixes, il te reste 120 € jusqu'à la fin du mois. On les garde pour les courses et l'essentiel, et on met le budget loisirs en pause quelques jours. Ça te va ?`;

/** Rappel placé juste avant la question : les petits modèles suivent mieux ce qui est récent. */
export const REMINDER = `(Rappel : appuie-toi sur la fiche. Ne critique pas une dépense qui respecte son budget. Réponse courte et naturelle.)`;

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
export async function loadFullContext(supabase: any): Promise<any> {
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

  const since180 = new Date(now.getTime() - 180 * 86400000);
  const [prof, accounts, cats, budgets, objectives, recurring, txns, regs] = await Promise.all([
    safe(supabase.from("user_profiles").select("monthly_income, priorities, projects, never_cut, comfort, notes").maybeSingle()),
    safe(supabase.from("accounts").select("name, account_type, balance").eq("is_active", true).order("display_order")),
    safe(supabase.from("categories").select("name, category_type, is_essential").eq("is_active", true)),
    safe(supabase.from("budgets").select("period_type, budget_limit, category:categories(name, is_essential)")),
    safe(supabase.from("objectives").select("title, type, target_amount, target_date, created_at, category:categories(name)").eq("is_active", true)),
    safe(supabase.from("recurring_transactions").select("title, amount, transaction_type, frequency_type, repeat_interval, next_due_date, end_date, category:categories(name)").eq("is_active", true)),
    safe(supabase.from("transactions").select("title, amount, transaction_type, date, category:categories(name)").gte("date", windowStart.toISOString()).order("date", { ascending: false })),
    safe(supabase.from("account_regularizations").select("regularized_at, previous_balance, real_balance, difference, note, account:accounts(name)").gte("regularized_at", since180.toISOString()).order("regularized_at", { ascending: false })),
  ]);

  const tx = (txns ?? []) as any[];
  const essentialOf: Record<string, boolean> = {};
  for (const c of (cats ?? []) as any[]) essentialOf[c.name] = c.is_essential !== false;

  // ---- Agrégats 12 mois + mois en cours ----
  const monthKey = (d: string) => String(d).slice(0, 7);
  const curKey = isoDay(monthStart).slice(0, 7);
  let income12 = 0;
  let curIncome = 0, curExpenses = 0;
  const netByMonth: Record<string, number> = {}; // revenus − dépenses par mois
  const perCatMonth: Record<string, Record<string, number>> = {};
  const curPerCat: Record<string, number> = {};
  const ytdPerCat: Record<string, number> = {};
  const weekPerCat: Record<string, number> = {};
  for (const t of tx) {
    const amt = Number(t.amount) || 0;
    const mk = monthKey(t.date);
    const d = new Date(t.date);
    if (t.transaction_type === "INCOME") {
      if (mk !== curKey) income12 += amt;
      netByMonth[mk] = (netByMonth[mk] ?? 0) + amt;
      if (mk === curKey) curIncome += amt;
      continue;
    }
    if (t.transaction_type !== "EXPENSE") continue;
    netByMonth[mk] = (netByMonth[mk] ?? 0) - amt;
    const cat = t.category?.name ?? "Sans catégorie";
    (perCatMonth[cat] ??= {})[mk] = (perCatMonth[cat][mk] ?? 0) + amt;
    if (mk === curKey) { curExpenses += amt; curPerCat[cat] = (curPerCat[cat] ?? 0) + amt; }
    if (d >= yearStart) ytdPerCat[cat] = (ytdPerCat[cat] ?? 0) + amt;
    if (d >= weekStart) weekPerCat[cat] = (weekPerCat[cat] ?? 0) + amt;
  }

  // Moyennes sur les mois COMPLETS réellement couverts par l'historique (hors mois
  // en cours) : ni divisées par 12 à tort, ni diluées par un mois à peine commencé.
  const oldest = tx.length ? new Date(tx[tx.length - 1].date) : now;
  const completeMonths = Math.min(12, (y - oldest.getUTCFullYear()) * 12 + (m - oldest.getUTCMonth()));
  const monthsCovered = Math.max(1, completeMonths);

  const spendingHistory = Object.entries(perCatMonth).map(([name, byMonth]) => {
    const total = Object.entries(byMonth)
      .filter(([k]) => completeMonths < 1 || k !== curKey)
      .reduce((a, [, v]) => a + v, 0);
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

  // ---- Charges fixes (récurrences) + ce qui reste à tomber ce mois-ci ----
  let dueRestOfMonth = 0, incomeRestOfMonth = 0;
  const dueByCat: Record<string, number> = {};          // charges fixes encore à prélever ce mois, par catégorie
  const fixedMonthlyByCat: Record<string, number> = {}; // poids mensuel des charges fixes, par catégorie
  const recurringList = ((recurring ?? []) as any[]).map((r) => {
    const amt = Number(r.amount) || 0;
    let occ = 0;
    let d = new Date(String(r.next_due_date) + "T00:00:00Z");
    const end = r.end_date ? new Date(String(r.end_date) + "T23:59:59Z") : null;
    while (d <= monthEnd && occ < 40 && (!end || d <= end)) {
      if (d >= new Date(isoDay(now) + "T00:00:00Z")) occ++;
      d = addPeriod(d, r.frequency_type, r.repeat_interval);
    }
    const rcat = r.category?.name ?? null;
    if (r.transaction_type === "EXPENSE") {
      dueRestOfMonth += occ * amt;
      if (rcat) {
        dueByCat[rcat] = (dueByCat[rcat] ?? 0) + occ * amt;
        fixedMonthlyByCat[rcat] = (fixedMonthlyByCat[rcat] ?? 0) + monthlyEquivalent(amt, r.frequency_type, r.repeat_interval);
      }
    }
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
    // Budget essentiellement occupé par une charge fixe (abonnement…) : le voir plein est normal.
    const mostlyFixed = (fixedMonthlyByCat[name] ?? 0) >= limit * 0.8;
    let status = "OK";
    if (spent > limit + 0.5) status = "DÉPASSÉ";
    else if (!essential && !mostlyFixed && spent >= limit * 0.9) status = "PROCHE_LIMITE";
    else if (!essential && !mostlyFixed && progress != null && progress >= 0.2 && spent > limit * progress * 1.25) status = "RYTHME_RAPIDE";
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
  // Une régularisation négative CE mois-ci = de l'argent réellement parti sans être saisi :
  // pas une dépense de budget, mais il manque bien sur le compte → on le retire de la marge.
  const unrecordedThisMonth = round2(((regs ?? []) as any[])
    .filter((r) => String(r.regularized_at).slice(0, 7) === curKey && Number(r.difference) < 0)
    .reduce((s2, r) => s2 - Number(r.difference), 0));
  const margin = round2(expectedIncome - curExpenses - dueRestOfMonth - unrecordedThisMonth);
  // Disponible réel pour un extra = marge − ce qui reste à dépenser dans les budgets mensuels
  // (hors charges fixes déjà comptées dans la marge).
  const stillBudgeted = budgetList
    .filter((b) => b.period === "MONTHLY" && b.remaining > 0)
    .reduce((s2, b) => s2 + Math.max(0, b.remaining - (dueByCat[b.category] ?? 0)), 0);
  const availableToSpend = round2(margin - stillBudgeted);
  // Capacité d'épargne prévue = revenu − budgets (équivalent mensuel). Sert à vérifier
  // que les objectifs sont atteignables, plutôt que d'exiger l'impossible.
  const monthlyBudgets = budgetList.reduce((s2, b) =>
    s2 + (b.period === "YEARLY" ? b.limit / 12 : b.period === "WEEKLY" ? b.limit * 52 / 12 : b.limit), 0);
  const refIncome = statedIncome || round2(income12 / monthsCovered);
  const savingsCapacity = budgetList.length && refIncome ? round2(refIncome - monthlyBudgets) : null;

  // ---- Objectifs (même logique que AnalysisRepository.objectiveProgress) ----
  // Rythme d'épargne = moyenne (revenus − dépenses) des 3 derniers mois COMPLETS
  // (le mois en cours, incomplet, fausserait la moyenne).
  const paceMonths = Math.max(1, Math.min(3, completeMonths));
  let net3 = 0;
  for (let i = 1; i <= paceMonths; i++) {
    const dt = new Date(Date.UTC(y, m - i, 1));
    net3 += netByMonth[isoDay(dt).slice(0, 7)] ?? 0;
  }
  if (completeMonths < 1) net3 = netByMonth[curKey] ?? 0;
  const savingsPace = round2(net3 / paceMonths);
  const objectiveList = ((objectives ?? []) as any[]).map((o) => {
    const target = Number(o.target_amount) || 0;
    let monthsLeft = 12;
    if (o.target_date) {
      const td = new Date(String(o.target_date) + "T00:00:00Z");
      monthsLeft = Math.max(1, (td.getUTCFullYear() - y) * 12 + (td.getUTCMonth() - m));
    }
    const cat = o.category?.name ?? null;
    if (o.type === "SAVINGS") {
      const needed = round2(target / monthsLeft);
      return { title: o.title, type: o.type, targetAmount: round2(target), targetDate: o.target_date ?? null,
        monthlyNeeded: needed, currentPace: savingsPace, onTrack: savingsPace >= needed };
    }
    const spent = cat ? (curPerCat[cat] ?? 0) : curExpenses;
    return { title: o.title, type: o.type, targetAmount: round2(target), category: cat,
      spentThisMonth: round2(spent), onTrack: spent <= target };
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
    savingsPace,
    savingsCapacity,
    currentMonth: {
      income: round2(curIncome),
      expenses: round2(curExpenses),
      fixedChargesStillDue: round2(dueRestOfMonth),
      incomeStillExpected: round2(incomeRestOfMonth),
      unrecordedSpending: unrecordedThisMonth,
      // Mois recalé par une régularisation : les dépenses saisies sont incomplètes.
      incomplete: unrecordedThisMonth > 0,
      margin,
      availableToSpend,
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
    // Comptes recalés sur leur solde réel (6 derniers mois) : ni dépense ni revenu.
    regularizations: ((regs ?? []) as any[]).map((r) => ({
      date: String(r.regularized_at).slice(0, 10),
      account: r.account?.name ?? "?",
      previousBalance: round2(Number(r.previous_balance) || 0),
      realBalance: round2(Number(r.real_balance) || 0),
      difference: round2(Number(r.difference) || 0),
      note: r.note ?? null,
    })),
  };
}

// ---------------------------------------------------------------------------
// Fiche en français pour le modèle
// ---------------------------------------------------------------------------

const eur = (n: number) => `${Math.round(n).toLocaleString("fr-FR")} €`;
const PERIODE: Record<string, string> = { MONTHLY: "mois", YEARLY: "an", WEEKLY: "semaine", CUSTOM: "période" };

/** Les problèmes réels, calculés de façon déterministe (le modèle n'a pas à les chercher). */
function alertsOf(c: any): string[] {
  const out: string[] = [];
  for (const b of c.budgets) {
    const p = PERIODE[b.period] ?? "mois";
    if (b.status === "DÉPASSÉ") out.push(`Budget ${b.category} dépassé de ${eur(-b.remaining)} (${eur(b.spent)} sur ${eur(b.limit)} par ${p}).`);
    else if (b.status === "PROCHE_LIMITE") out.push(`Budget ${b.category} presque épuisé : ${b.pct} % utilisé, reste ${eur(b.remaining)}.`);
    else if (b.status === "RYTHME_RAPIDE") out.push(`Budget ${b.category} part vite : ${b.pct} % utilisé au jour ${c.dayOfMonth} sur ${c.daysInMonth}.`);
  }
  if (c.currentMonth.margin < 0) out.push(`Mois dans le rouge : marge de ${eur(c.currentMonth.margin)} une fois les charges fixes à venir payées.`);
  const needed = c.objectives.filter((o: any) => o.type === "SAVINGS").reduce((s: number, o: any) => s + o.monthlyNeeded, 0);
  if (c.savingsCapacity != null && needed > c.savingsCapacity + 5) {
    out.push(`Objectifs trop ambitieux pour le budget actuel : ils demandent ${eur(needed)}/mois, le budget en laisse au mieux ${eur(c.savingsCapacity)}. Ne demande pas l'impossible : propose de décaler une date ou de réduire un montant, chiffres à l'appui.`);
  }
  for (const o of c.objectives) {
    if (o.onTrack) continue;
    if (o.type === "SAVINGS") out.push(`Objectif « ${o.title} » en retard : il faut épargner ${eur(o.monthlyNeeded)}/mois, rythme actuel ${eur(o.currentPace)}/mois.`);
    else out.push(`Plafond « ${o.title} » dépassé : ${eur(o.spentThisMonth)} dépensés pour ${eur(o.targetAmount)} max.`);
  }
  for (const h of c.spendingByCategory) {
    if (h.unusualThisMonth && !h.essential) out.push(`Dépense inhabituelle en ${h.category} : ${eur(h.thisMonth)} ce mois, ~${eur(h.monthlyAverage)} d'habitude.`);
  }
  return out;
}

/**
 * Transforme le contexte complet en une fiche courte, en français, problèmes en tête.
 * Bien plus digeste pour un petit modèle qu'un gros JSON brut.
 * [withRecent] : ajoute les dernières opérations (seulement quand la question en parle).
 */
export function buildBriefing(c: any, withRecent = false): string {
  const L: string[] = [];
  const alerts = alertsOf(c);
  L.push(`# Fiche budget — ${c.today} (jour ${c.dayOfMonth}/${c.daysInMonth} de ${c.month})`);

  L.push("", "## À signaler");
  if (alerts.length) alerts.forEach((a) => L.push(`- ${a}`));
  else L.push("- Rien à signaler : tout est dans les clous.");

  // Mois incomplet : un budget « tenu » l'est peut-être seulement faute de saisie.
  const good = c.currentMonth.incomplete ? [] : c.budgets.filter((b: any) => b.status === "OK" && !b.essential).map((b: any) => b.category);
  const goodObj = c.objectives.filter((o: any) => o.onTrack).map((o: any) => o.title);
  if (good.length || goodObj.length) {
    L.push("", "## Ce qui va bien");
    if (good.length) L.push(`- Budgets tenus : ${good.join(", ")}.`);
    if (goodObj.length) L.push(`- Objectifs dans les temps : ${goodObj.join(", ")}.`);
  }

  const p = c.profile;
  if (p) {
    L.push("", "## Profil");
    if (p.monthlyIncomeDeclared) L.push(`- Revenu mensuel déclaré : ${eur(p.monthlyIncomeDeclared)}`);
    if (p.priorities) L.push(`- Priorités : ${p.priorities}`);
    if (p.neverCut) L.push(`- À ne jamais couper : ${p.neverCut}`);
    if (p.projects) L.push(`- Projets : ${p.projects}`);
    if (p.comfort) L.push(`- Train de vie souhaité : ${p.comfort}`);
    if (p.notes) L.push(`- Notes : ${p.notes}`);
  }

  const cm = c.currentMonth;
  L.push("", "## Ce mois-ci");
  if (cm.incomplete) L.push(`- ⚠️ Mois incomplet : une régularisation a révélé ${eur(cm.unrecordedSpending)} de dépenses non saisies. Les budgets du mois paraissent mieux tenus qu'en réalité : ne les félicite pas.`);
  L.push(`- Revenus reçus : ${eur(cm.income)}${cm.incomeStillExpected > 0 ? ` (+${eur(cm.incomeStillExpected)} attendus)` : ""}`);
  L.push(`- Dépenses faites : ${eur(cm.expenses)}`);
  L.push(`- Charges fixes encore à prélever : ${eur(cm.fixedChargesStillDue)}`);
  if (cm.unrecordedSpending > 0) L.push(`- Dépenses non saisies (régularisation) : ${eur(cm.unrecordedSpending)}`);
  L.push(`- Marge du mois (revenus − dépenses − charges fixes à venir) : ${eur(cm.margin)}`);
  L.push(`- Disponible réel pour un extra (une fois réservé ce qui reste dans les budgets du mois) : ${eur(cm.availableToSpend)}`);
  L.push(`- Épargne moyenne des 3 derniers mois : ${eur(c.savingsPace)}/mois`);
  if (c.savingsCapacity != null) L.push(`- Épargne permise par le budget (revenu − budgets) : ${eur(c.savingsCapacity)}/mois`);

  if (c.budgets.length) {
    L.push("", "## Budgets");
    for (const b of c.budgets) {
      L.push(`- ${b.category}${b.essential ? " (essentiel)" : ""} : ${eur(b.spent)} / ${eur(b.limit)} par ${PERIODE[b.period] ?? "mois"}, reste ${eur(b.remaining)}`);
    }
  }

  if (c.objectives.length) {
    L.push("", "## Objectifs");
    for (const o of c.objectives) {
      if (o.type === "SAVINGS") L.push(`- ${o.title} : épargner ${eur(o.targetAmount)}${o.targetDate ? ` d'ici le ${o.targetDate}` : ""} → ${eur(o.monthlyNeeded)}/mois nécessaires`);
      else L.push(`- ${o.title} : ne pas dépasser ${eur(o.targetAmount)}/mois${o.category ? ` en ${o.category}` : ""} (${eur(o.spentThisMonth)} ce mois)`);
    }
  }

  if (c.accounts.length) {
    L.push("", "## Comptes");
    L.push(c.accounts.map((a: any) => `${a.name} ${eur(a.balance)}`).join(" · "));
  }

  const fixed = c.fixedCharges.items.filter((r: any) => r.type === "EXPENSE");
  if (fixed.length) {
    L.push("", `## Charges fixes (~${eur(c.fixedCharges.monthlyTotal)}/mois)`);
    fixed.sort((a: any, b: any) => b.monthlyEquivalent - a.monthlyEquivalent).slice(0, 10)
      .forEach((r: any) => L.push(`- ${r.title} : ${eur(r.monthlyEquivalent)}/mois (prochaine le ${r.nextDue})`));
  }

  const habits = c.spendingByCategory.filter((h: any) => h.monthlyAverage >= 5).slice(0, 10);
  if (habits.length) {
    L.push("", `## Habitudes de dépense (moyenne sur ${c.history.monthsCovered} mois)`);
    habits.forEach((h: any) => L.push(`- ${h.category}${h.essential ? " (essentiel)" : ""} : ~${eur(h.monthlyAverage)}/mois, ${eur(h.thisMonth)} ce mois`));
  }

  const regs = c.regularizations ?? [];
  if (regs.length) {
    const last = regs[0].date;
    const total = regs.filter((r: any) => r.date === last).reduce((s: number, r: any) => s + r.difference, 0);
    L.push("", "## Régularisations (comptes recalés sur leur solde réel — ni dépense ni revenu)");
    regs.slice(0, 8).forEach((r: any) =>
      L.push(`- ${r.date} ${r.account} : ${eur(r.previousBalance)} → ${eur(r.realBalance)} (écart ${r.difference > 0 ? "+" : ""}${eur(r.difference)})${r.note ? ` — ${r.note}` : ""}`));
    L.push(`- Dernière remise à plat le ${last} : les soldes des comptes sont justes depuis. Les données d'avant peuvent être incomplètes` +
      (total < 0 ? ` (environ ${eur(-total)} de dépenses n'avaient pas été saisies), donc les moyennes d'habitudes peuvent sous-estimer les vraies dépenses.` : "."));
  }

  if (withRecent && c.recentTransactions.length) {
    L.push("", "## Dernières opérations");
    c.recentTransactions.slice(0, 15).forEach((t: any) =>
      L.push(`- ${t.date} ${t.title} : ${t.type === "INCOME" ? "+" : "-"}${eur(t.amount)}${t.category ? ` (${t.category})` : ""}`));
  }
  return L.join("\n");
}

/** La question parle-t-elle d'opérations précises ? (sinon on n'envoie pas la liste) */
export function mentionsTransactions(q: string): boolean {
  return /(dépens|achat|achet|opération|transaction|dernier|hier|semaine|payé|pourquoi|où (est|sont|part)|passé)/i.test(q);
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
