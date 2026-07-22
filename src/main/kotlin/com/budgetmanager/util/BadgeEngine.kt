package com.budgetmanager.util

import com.budgetmanager.domain.model.Account
import com.budgetmanager.domain.model.BudgetState
import com.budgetmanager.domain.model.BudgetWithStatus
import com.budgetmanager.domain.model.Transaction
import com.budgetmanager.domain.model.TransactionType
import com.budgetmanager.domain.model.Challenge
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth

data class Badge(
    val id: String,
    val title: String,
    val description: String,
    val icon: String, // emoji
    val unlocked: Boolean,
    val progress: Float = 0f,   // 0..1 for locked badges
    val progressText: String = ""
)

/**
 * Computes badges from current user data. Pure functional, runs each time the
 * Badges screen is opened. Streaks are calculated from transactions / budgets.
 */
object BadgeEngine {

    fun computeAll(
        accounts: List<Account>,
        transactions: List<Transaction>,
        budgets: List<BudgetWithStatus>,
        challenges: List<Challenge>,
        savingsGoal: BigDecimal
    ): List<Badge> {
        val badges = mutableListOf<Badge>()

        // === First steps ===
        badges += badge(
            id = "first_transaction",
            title = "Premiere transaction",
            description = "Tu as enregistre ta toute premiere transaction.",
            icon = "🎉",
            unlocked = transactions.isNotEmpty()
        )

        badges += badge(
            id = "first_account",
            title = "Premier compte",
            description = "Tu as cree ton premier compte.",
            icon = "🏦",
            unlocked = accounts.isNotEmpty()
        )

        badges += badge(
            id = "first_budget",
            title = "Premier budget",
            description = "Tu as defini ton premier budget.",
            icon = "📊",
            unlocked = budgets.isNotEmpty()
        )

        // === Quantity milestones ===
        val txCount = transactions.size
        badges += milestoneBadge("tx_10", "10 transactions", "Tu as enregistre 10 transactions.", "📝", txCount, 10)
        badges += milestoneBadge("tx_50", "50 transactions", "Tu as enregistre 50 transactions.", "🗂️", txCount, 50)
        badges += milestoneBadge("tx_100", "100 transactions", "Centurion du suivi : 100 transactions !", "💯", txCount, 100)
        badges += milestoneBadge("tx_500", "500 transactions", "Maitre du suivi : 500 transactions !", "🏅", txCount, 500)

        // === Savings amount milestones ===
        val netSavings = transactions.fold(BigDecimal.ZERO) { acc, t ->
            when (t.transactionType) {
                TransactionType.INCOME -> acc.add(t.amount)
                TransactionType.EXPENSE -> acc.subtract(t.amount)
                else -> acc
            }
        }.toLong()
        badges += milestoneBadge("save_100", "100 EUR d'epargne nette", "Tu as economise 100 EUR au total.", "💵", netSavings.toInt(), 100)
        badges += milestoneBadge("save_1000", "1 000 EUR d'epargne", "Tu as economise 1 000 EUR au total.", "💰", netSavings.toInt(), 1000)
        badges += milestoneBadge("save_10000", "10 000 EUR d'epargne", "Tu es a 5 chiffres : 10 000 EUR economises !", "💎", netSavings.toInt(), 10000)

        // === Budget compliance streak ===
        val budgetStreak = computeBudgetStreak(transactions, budgets)
        badges += milestoneBadge("streak_7", "Semaine sans alerte", "7 jours d'affilee sans alerte de budget.", "🔥", budgetStreak, 7)
        badges += milestoneBadge("streak_30", "Mois maitrise", "30 jours d'affilee sans alerte de budget.", "⚡", budgetStreak, 30)
        badges += milestoneBadge("streak_90", "Trimestre parfait", "90 jours d'affilee sans depasser un budget.", "🏆", budgetStreak, 90)

        // === All budgets safe right now ===
        badges += badge(
            id = "all_budgets_safe",
            title = "Tout est sous controle",
            description = "Tous tes budgets actuels sont dans les limites.",
            icon = "✨",
            unlocked = budgets.isNotEmpty() && budgets.all { it.state == BudgetState.SAFE }
        )

        // === Challenges completed ===
        val completedChallenges = challenges.count { it.isCompleted }
        badges += milestoneBadge("challenge_1", "Premier defi reussi", "Tu as termine ton premier defi !", "🥉", completedChallenges, 1)
        badges += milestoneBadge("challenge_5", "5 defis reussis", "Tu en es a 5 defis termines.", "🥈", completedChallenges, 5)
        badges += milestoneBadge("challenge_10", "10 defis reussis", "Pro des defis : 10 reussis !", "🥇", completedChallenges, 10)

        // === Categorization quality ===
        val categorizedRatio = if (transactions.isNotEmpty())
            transactions.count { it.categoryId != null }.toFloat() / transactions.size else 0f
        if (transactions.size >= 10) {
            badges += badge(
                id = "categorize_all",
                title = "Maniaque du rangement",
                description = "100% de tes transactions sont categorisees.",
                icon = "📂",
                unlocked = categorizedRatio >= 1f,
                progress = categorizedRatio,
                progressText = "${(categorizedRatio * 100).toInt()}%"
            )
        }

        // === Savings goal achieved ===
        if (savingsGoal > BigDecimal.ZERO) {
            val now = YearMonth.now()
            val thisMonthIncome = transactions.filter {
                it.transactionType == TransactionType.INCOME && YearMonth.from(it.date) == now
            }.fold(BigDecimal.ZERO) { a, t -> a.add(t.amount) }
            val thisMonthExpenses = transactions.filter {
                it.transactionType == TransactionType.EXPENSE && YearMonth.from(it.date) == now
            }.fold(BigDecimal.ZERO) { a, t -> a.add(t.amount) }
            val thisMonthNet = thisMonthIncome.subtract(thisMonthExpenses)
            badges += badge(
                id = "savings_goal_month",
                title = "Objectif d'epargne atteint",
                description = "Tu as atteint ton objectif d'epargne mensuel ce mois-ci !",
                icon = "🎯",
                unlocked = thisMonthNet >= savingsGoal,
                progress = if (savingsGoal > BigDecimal.ZERO)
                    (thisMonthNet.toDouble() / savingsGoal.toDouble()).toFloat().coerceIn(0f, 1f) else 0f
            )
        }

        // === Diverse activity ===
        val uniqueCategories = transactions.mapNotNull { it.categoryId }.distinct().size
        badges += milestoneBadge("categories_5", "Diversifie", "Tu utilises 5 categories differentes.", "🌈", uniqueCategories, 5)

        return badges
    }

    private fun badge(
        id: String, title: String, description: String, icon: String,
        unlocked: Boolean, progress: Float = if (unlocked) 1f else 0f,
        progressText: String = ""
    ) = Badge(id, title, description, icon, unlocked, progress, progressText)

    private fun milestoneBadge(
        id: String, title: String, description: String, icon: String,
        current: Int, target: Int
    ): Badge {
        val unlocked = current >= target
        val ratio = (current.toFloat() / target).coerceIn(0f, 1f)
        return Badge(
            id = id, title = title, description = description, icon = icon,
            unlocked = unlocked,
            progress = ratio,
            progressText = "$current / $target"
        )
    }

    /**
     * Compute consecutive days where no budget was exceeded.
     * Walks back from today, day by day, until we find a day with expenses
     * exceeding any active budget proportionally.
     */
    private fun computeBudgetStreak(transactions: List<Transaction>, budgets: List<BudgetWithStatus>): Int {
        if (budgets.isEmpty()) return 0
        // Per-budget daily allowed expense
        val today = LocalDate.now()
        var streak = 0
        var day = today

        // Track running monthly spend per category, rebuild incrementally
        // Simple approach: for each day, compute expenses up to that day for the running month
        // and check if it exceeds the budget.
        while (streak < 365) { // safety
            val month = YearMonth.from(day)
            val monthExpenses = transactions.filter {
                it.transactionType == TransactionType.EXPENSE &&
                YearMonth.from(it.date) == month &&
                !it.date.toLocalDate().isAfter(day)
            }
            var dayOk = true
            for (b in budgets) {
                val catSpent = monthExpenses
                    .filter { it.categoryId == b.budget.categoryId }
                    .fold(BigDecimal.ZERO) { acc, t -> acc.add(t.amount) }
                if (catSpent > b.budget.limit) {
                    dayOk = false
                    break
                }
            }
            if (!dayOk) break
            streak++
            day = day.minusDays(1)
        }
        return streak
    }
}
