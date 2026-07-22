package com.budgetmanager.util

import com.budgetmanager.domain.model.Account
import com.budgetmanager.domain.model.AccountType
import com.budgetmanager.domain.model.Transaction
import com.budgetmanager.domain.model.TransactionType
import com.budgetmanager.domain.model.BudgetWithStatus
import com.budgetmanager.domain.model.BudgetState
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth

enum class AdviceLevel { INFO, GOOD, WARNING, CRITICAL }
enum class AdviceCategory {
    SAVINGS, BUDGET, RECURRING, INVESTMENT, CASH_FLOW, BALANCE, GENERAL
}

data class FinancialAdvice(
    val title: String,
    val message: String,
    val level: AdviceLevel,
    val category: AdviceCategory,
    val actionLabel: String? = null,
    val actionScreen: String? = null
)

/**
 * Generates personalized financial advice based on user data.
 * Pure local computation — no AI/API calls. Designed to give actionable, practical
 * recommendations.
 */
class AdviceEngine {

    fun analyze(
        accounts: List<Account>,
        transactions: List<Transaction>,
        budgets: List<BudgetWithStatus>,
        savingsGoal: BigDecimal
    ): List<FinancialAdvice> {
        val advice = mutableListOf<FinancialAdvice>()
        val today = LocalDate.now()
        val thisMonth = YearMonth.now()
        val lastMonth = thisMonth.minusMonths(1)

        // ===== Cash flow analysis =====
        val thisMonthIncome = sumByMonth(transactions, thisMonth, TransactionType.INCOME)
        val thisMonthExpenses = sumByMonth(transactions, thisMonth, TransactionType.EXPENSE)
        val lastMonthExpenses = sumByMonth(transactions, lastMonth, TransactionType.EXPENSE)
        val net = thisMonthIncome.subtract(thisMonthExpenses)

        if (thisMonthIncome > BigDecimal.ZERO) {
            val savingsRate = net.divide(thisMonthIncome, 4, RoundingMode.HALF_UP).toDouble()
            when {
                savingsRate < 0 -> advice += FinancialAdvice(
                    title = "Tu depenses plus que tu ne gagnes ce mois-ci",
                    message = "Tes depenses (${fmt(thisMonthExpenses)}) depassent tes revenus " +
                        "(${fmt(thisMonthIncome)}). Identifie les categories qui ont le plus augmente " +
                        "et reduis-les rapidement avant de creuser le decouvert.",
                    level = AdviceLevel.CRITICAL,
                    category = AdviceCategory.CASH_FLOW
                )
                savingsRate < 0.05 -> advice += FinancialAdvice(
                    title = "Ta marge est tres serree",
                    message = "Tu n'epargnes que ${(savingsRate * 100).toInt()}% de tes revenus. " +
                        "L'objectif sain est 10-20%. Liste tes 3 plus grosses depenses " +
                        "non-essentielles et vois si tu peux en couper au moins une.",
                    level = AdviceLevel.WARNING,
                    category = AdviceCategory.SAVINGS
                )
                savingsRate >= 0.20 -> advice += FinancialAdvice(
                    title = "Excellent taux d'epargne !",
                    message = "Tu epargnes ${(savingsRate * 100).toInt()}% de tes revenus. " +
                        "Pense a placer cet argent — un livret reglemente puis un PEA si tu vises long terme.",
                    level = AdviceLevel.GOOD,
                    category = AdviceCategory.SAVINGS
                )
            }
        }

        // Trend: vs last month
        if (lastMonthExpenses > BigDecimal.ZERO && thisMonthExpenses > BigDecimal.ZERO) {
            val change = thisMonthExpenses.subtract(lastMonthExpenses)
                .divide(lastMonthExpenses, 4, RoundingMode.HALF_UP).toDouble()
            if (change > 0.20) {
                advice += FinancialAdvice(
                    title = "Tes depenses ont augmente de ${(change * 100).toInt()}% par rapport au mois dernier",
                    message = "Tu as depense ${fmt(thisMonthExpenses.subtract(lastMonthExpenses))} de plus que le mois dernier. " +
                        "Verifie dans tes transactions ce qui a change.",
                    level = AdviceLevel.WARNING,
                    category = AdviceCategory.CASH_FLOW
                )
            } else if (change < -0.15) {
                advice += FinancialAdvice(
                    title = "Tu depenses moins que le mois dernier !",
                    message = "Tes depenses ont baisse de ${((-change) * 100).toInt()}% (-${fmt(lastMonthExpenses.subtract(thisMonthExpenses))}). " +
                        "Bravo, tu peux mettre la difference de cote.",
                    level = AdviceLevel.GOOD,
                    category = AdviceCategory.CASH_FLOW
                )
            }
        }

        // ===== Budget analysis =====
        val alertBudgets = budgets.filter { it.state == BudgetState.ALERT }
        val warningBudgets = budgets.filter { it.state == BudgetState.WARNING }
        if (alertBudgets.isNotEmpty()) {
            val names = alertBudgets.joinToString(", ") { it.budget.categoryName }
            val totalOver = alertBudgets.fold(BigDecimal.ZERO) { acc, b ->
                acc.add(b.spent.subtract(b.budget.limit).coerceAtLeast(BigDecimal.ZERO))
            }
            advice += FinancialAdvice(
                title = "${alertBudgets.size} budget(s) depasse(s)",
                message = "Categories en alerte : $names. Tu as depasse de ${fmt(totalOver)} au total. " +
                    "Soit ajuste les limites si elles sont irrealistes, soit reduis activement ces depenses ce mois-ci.",
                level = AdviceLevel.CRITICAL,
                category = AdviceCategory.BUDGET
            )
        }
        if (warningBudgets.isNotEmpty()) {
            advice += FinancialAdvice(
                title = "${warningBudgets.size} budget(s) approchent de la limite",
                message = "Surveille : ${warningBudgets.joinToString(", ") { it.budget.categoryName }}. " +
                    "Tu es a 70%+ du plafond, mefie-toi du reste du mois.",
                level = AdviceLevel.WARNING,
                category = AdviceCategory.BUDGET
            )
        }

        // ===== Top expense category =====
        val expensesThisMonth = transactions.filter {
            it.transactionType == TransactionType.EXPENSE &&
            YearMonth.from(it.date) == thisMonth
        }
        if (expensesThisMonth.isNotEmpty() && thisMonthIncome > BigDecimal.ZERO) {
            val byCategory = expensesThisMonth
                .filter { it.categoryName != null }
                .groupBy { it.categoryName!! }
                .mapValues { (_, txs) -> txs.fold(BigDecimal.ZERO) { acc, t -> acc.add(t.amount) } }

            val top = byCategory.maxByOrNull { it.value }
            if (top != null) {
                val pctOfIncome = top.value.divide(thisMonthIncome, 4, RoundingMode.HALF_UP).toDouble()
                if (pctOfIncome > 0.30) {
                    advice += FinancialAdvice(
                        title = "${top.key} represente ${(pctOfIncome * 100).toInt()}% de tes revenus",
                        message = "C'est important. Si tu reduis cette categorie de 20%, tu economiserais " +
                            "${fmt(top.value.multiply(BigDecimal("0.20")))} par mois — soit ${fmt(top.value.multiply(BigDecimal("2.40")))} par an.",
                        level = AdviceLevel.INFO,
                        category = AdviceCategory.BUDGET
                    )
                }
            }
        }

        // ===== Account balance warnings =====
        val checkingAccounts = accounts.filter { it.accountType == AccountType.CHECKING && it.isActive }
        for (acc in checkingAccounts) {
            // Average daily expense from this account
            val accExpenses = transactions.filter {
                it.accountId == acc.id &&
                it.transactionType == TransactionType.EXPENSE &&
                YearMonth.from(it.date) == thisMonth
            }
            if (accExpenses.size >= 5 && acc.balance > BigDecimal.ZERO) {
                val totalSpent = accExpenses.fold(BigDecimal.ZERO) { a, t -> a.add(t.amount) }
                val daysElapsed = today.dayOfMonth.coerceAtLeast(1)
                val dailyAvg = totalSpent.divide(BigDecimal(daysElapsed), 2, RoundingMode.HALF_UP)
                if (dailyAvg > BigDecimal.ZERO) {
                    val daysLeft = acc.balance.divide(dailyAvg, 0, RoundingMode.DOWN).toInt()
                    if (daysLeft < 7) {
                        advice += FinancialAdvice(
                            title = "Solde \"${acc.name}\" : ~$daysLeft jours d'autonomie",
                            message = "A ce rythme (${fmt(dailyAvg)}/jour), ton compte sera vide dans environ $daysLeft jours. " +
                                "Reduis tes depenses ou prevois un transfert depuis l'epargne.",
                            level = if (daysLeft < 3) AdviceLevel.CRITICAL else AdviceLevel.WARNING,
                            category = AdviceCategory.BALANCE
                        )
                    }
                }
            }
        }

        // ===== Investment accounts =====
        val investments = accounts.filter { it.accountType == AccountType.INVESTMENT && it.isActive }
        for (inv in investments) {
            if (inv.initialCapital != null && inv.initialCapital > BigDecimal.ZERO) {
                val gainPct = inv.gainPercent
                when {
                    gainPct > 0.10f -> advice += FinancialAdvice(
                        title = "${inv.name} : +${(gainPct * 100).toInt()}% de gains",
                        message = "Bon rendement ! Pense a la provision impots : ${fmt(inv.taxProvision)} a mettre de cote.",
                        level = AdviceLevel.GOOD,
                        category = AdviceCategory.INVESTMENT
                    )
                    gainPct < -0.10f -> advice += FinancialAdvice(
                        title = "${inv.name} : ${(gainPct * 100).toInt()}% de moins-value",
                        message = "Ton portefeuille est en perte. C'est normal a court terme, " +
                            "ne vends pas dans la panique. Verifie ton allocation et conserve une vision long terme.",
                        level = AdviceLevel.INFO,
                        category = AdviceCategory.INVESTMENT
                    )
                }
            }
        }

        // ===== Savings goal tracking =====
        if (savingsGoal > BigDecimal.ZERO) {
            if (net < savingsGoal) {
                val gap = savingsGoal.subtract(net.coerceAtLeast(BigDecimal.ZERO))
                advice += FinancialAdvice(
                    title = "Objectif d'epargne : il manque ${fmt(gap)} ce mois-ci",
                    message = "Ton objectif mensuel est ${fmt(savingsGoal)}, tu es a ${fmt(net.coerceAtLeast(BigDecimal.ZERO))}. " +
                        "Identifie une depense non-essentielle a couper pour combler.",
                    level = AdviceLevel.WARNING,
                    category = AdviceCategory.SAVINGS
                )
            } else {
                val excess = net.subtract(savingsGoal)
                advice += FinancialAdvice(
                    title = "Objectif d'epargne atteint !",
                    message = "Tu epargnes ${fmt(net)}, soit ${fmt(excess)} de plus que prevu. " +
                        "Tu peux place cet excedent sur un produit a meilleur rendement.",
                    level = AdviceLevel.GOOD,
                    category = AdviceCategory.SAVINGS
                )
            }
        }

        // ===== Recurring frequency check =====
        val recurringTxsThisMonth = transactions.filter {
            it.isRecurring && YearMonth.from(it.date) == thisMonth
        }
        val recurringExpenses = recurringTxsThisMonth.filter { it.transactionType == TransactionType.EXPENSE }
            .fold(BigDecimal.ZERO) { acc, t -> acc.add(t.amount) }
        if (recurringExpenses > BigDecimal.ZERO && thisMonthIncome > BigDecimal.ZERO) {
            val pct = recurringExpenses.divide(thisMonthIncome, 4, RoundingMode.HALF_UP).toDouble()
            if (pct > 0.50) {
                advice += FinancialAdvice(
                    title = "Tes charges fixes representent ${(pct * 100).toInt()}% de tes revenus",
                    message = "Plus de 50% en depenses recurrentes, c'est beaucoup. " +
                        "Examine tes abonnements (streaming, telephonie, gym) — il y a souvent au moins un a couper.",
                    level = AdviceLevel.WARNING,
                    category = AdviceCategory.RECURRING
                )
            }
        }

        // ===== Empty state advice =====
        if (transactions.isEmpty()) {
            advice += FinancialAdvice(
                title = "Bienvenue ! Commence par enregistrer tes transactions",
                message = "Plus tu auras de donnees, plus les conseils seront pertinents. " +
                    "Saisis tes revenus du mois, puis tes depenses au fur et a mesure.",
                level = AdviceLevel.INFO,
                category = AdviceCategory.GENERAL
            )
        } else if (budgets.isEmpty()) {
            advice += FinancialAdvice(
                title = "Cree des budgets pour tes principales categories",
                message = "Avoir un budget par categorie (alimentation, transport, loisirs) t'aide " +
                    "a controler tes depenses et a recevoir des alertes quand tu approches la limite.",
                level = AdviceLevel.INFO,
                category = AdviceCategory.BUDGET
            )
        }

        // Sort by severity: critical → warning → info → good
        return advice.sortedBy {
            when (it.level) {
                AdviceLevel.CRITICAL -> 0
                AdviceLevel.WARNING -> 1
                AdviceLevel.INFO -> 2
                AdviceLevel.GOOD -> 3
            }
        }
    }

    private fun sumByMonth(
        transactions: List<Transaction>,
        month: YearMonth,
        type: TransactionType
    ): BigDecimal {
        return transactions
            .filter { it.transactionType == type && YearMonth.from(it.date) == month }
            .fold(BigDecimal.ZERO) { acc, t -> acc.add(t.amount) }
    }

    private fun fmt(amount: BigDecimal): String =
        String.format("%.2f EUR", amount.toDouble())
}
