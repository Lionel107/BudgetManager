package com.budgetmanager.domain.model

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.LocalDateTime

data class Account(
    val id: Long = 0,
    val name: String,
    val balance: BigDecimal,
    val accountType: AccountType,
    val currencyCode: String = "EUR",
    val isActive: Boolean = true,
    val displayOrder: Int = 0,
    val color: String? = null,
    val iconName: String? = null,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    /** Initial invested capital — only used when accountType == INVESTMENT. */
    val initialCapital: BigDecimal? = null,
    /** Tax rate applied to gains for tax provisioning. Default 0.30 (30%). */
    val taxRate: Float = 0.30f
) {
    /** Gain in absolute value (current balance - initial capital). 0 if not investment. */
    val gainAbsolute: BigDecimal
        get() = if (accountType == AccountType.INVESTMENT && initialCapital != null) {
            balance.subtract(initialCapital)
        } else BigDecimal.ZERO

    /** Gain as a percentage [-1.0, +inf]. 0f if no initial capital or not investment. */
    val gainPercent: Float
        get() = if (accountType == AccountType.INVESTMENT && initialCapital != null && initialCapital.compareTo(BigDecimal.ZERO) > 0) {
            gainAbsolute.toDouble().div(initialCapital.toDouble()).toFloat()
        } else 0f

    /** Tax provision: tax rate applied to positive gains only. */
    val taxProvision: BigDecimal
        get() = if (gainAbsolute > BigDecimal.ZERO) {
            gainAbsolute.multiply(BigDecimal(taxRate.toDouble()))
        } else BigDecimal.ZERO

    /** Net gain after tax provision. */
    val netGain: BigDecimal
        get() = gainAbsolute.subtract(taxProvision)
}

enum class ChallengeType {
    SPEND_LIMIT,   // Don't spend more than X in [category] during [period]
    SAVE_AMOUNT    // Save at least X (income - expenses) during [period]
}

data class Challenge(
    val id: Long = 0,
    val title: String,
    val description: String? = null,
    val type: ChallengeType,
    val targetAmount: BigDecimal,
    val categoryId: Long? = null,
    val categoryName: String? = null,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val isCompleted: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

data class ChallengeProgress(
    val challenge: Challenge,
    /** Current value relative to the target (e.g. amount spent so far, or amount saved). */
    val currentAmount: BigDecimal,
    /** 0.0 = no progress, 1.0 = target reached. May exceed 1.0. */
    val progressRatio: Float,
    /** True if user is on track to succeed. */
    val onTrack: Boolean,
    val daysTotal: Int,
    val daysElapsed: Int,
    val daysRemaining: Int,
    val transactionCount: Int = 0
)

data class TransactionSplit(
    val id: Long = 0,
    val transactionId: Long,
    val categoryId: Long? = null,
    val categoryName: String? = null,
    val amount: BigDecimal,
    val notes: String? = null
)

data class Tag(
    val id: Long = 0,
    val name: String,
    val color: String? = null,
    val usageCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)

data class Category(
    val id: Long = 0,
    val name: String,
    val categoryType: TransactionType,
    val parentId: Long? = null,
    val color: String,
    val iconName: String? = null,
    val isDefault: Boolean = false,
    val displayOrder: Int = 0,
    val isActive: Boolean = true,
    /** Dépense essentielle (besoin) vs superflu — utilisé par l'accompagnant IA. */
    val isEssential: Boolean = true
)

/** Objectif financier : épargner un montant, ou ne pas dépasser un plafond, pour une date. */
data class Objective(
    val id: Long = 0,
    val title: String,
    val type: ObjectiveType,
    val targetAmount: BigDecimal,
    val targetDate: LocalDate? = null,
    val categoryId: Long? = null,
    val categoryName: String? = null,
    val isActive: Boolean = true
)

enum class ObjectiveType { SAVINGS, SPENDING_LIMIT }

data class Transaction(
    val id: Long = 0,
    val accountId: Long,
    val accountName: String? = null,
    val categoryId: Long? = null,
    val categoryName: String? = null,
    val categoryColor: String? = null,
    val title: String,
    val amount: BigDecimal,
    val transactionType: TransactionType,
    val date: LocalDateTime,
    val notes: String? = null,
    val tags: List<String> = emptyList(),
    val isRecurring: Boolean = false,
    val recurringTransactionId: Long? = null,
    val createdAt: LocalDateTime = LocalDateTime.now()
)

data class RecurringTransaction(
    val id: Long = 0,
    val title: String,
    val amount: BigDecimal,
    val categoryId: Long? = null,
    val categoryName: String? = null,
    val accountId: Long,
    val accountName: String? = null,
    val frequencyType: FrequencyType,
    val interval: Int = 1,
    val startDate: LocalDate,
    val endDate: LocalDate? = null,
    val lastGeneratedDate: LocalDate? = null,
    val nextDueDate: LocalDate,
    val transactionType: TransactionType,
    val isActive: Boolean = true,
    val notes: String? = null,
    /** Destination account for TRANSFER-type recurrences only. */
    val destinationAccountId: Long? = null,
    val destinationAccountName: String? = null
)

data class Template(
    val id: Long = 0,
    val name: String,
    val defaultAmount: BigDecimal? = null,
    val categoryId: Long? = null,
    val categoryName: String? = null,
    val transactionType: TransactionType,
    val iconName: String? = null,
    val color: String? = null,
    val displayOrder: Int = 0,
    val usageCount: Int = 0
)

data class Budget(
    val id: Long = 0,
    val categoryId: Long,
    val categoryName: String,
    val categoryColor: String,
    val periodType: BudgetPeriodType,
    val limit: BigDecimal,
    val alertThreshold: Float = 0.9f,
    val warningThreshold: Float = 0.7f,
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null
)

data class BudgetWithStatus(
    val budget: Budget,
    val spent: BigDecimal,
    val remaining: BigDecimal,
    val percentage: Float,
    val state: BudgetState,
    val transactionCount: Int = 0
) {
    companion object {
        fun create(budget: Budget, spent: BigDecimal, transactionCount: Int = 0): BudgetWithStatus {
            val percentage = if (budget.limit.compareTo(BigDecimal.ZERO) != 0) {
                spent.divide(budget.limit, 4, RoundingMode.HALF_UP).toFloat()
            } else {
                0f
            }
            val remaining = budget.limit.subtract(spent).coerceAtLeast(BigDecimal.ZERO)
            val state = BudgetState.fromPercentage(percentage, budget.warningThreshold, budget.alertThreshold)
            return BudgetWithStatus(
                budget = budget,
                spent = spent,
                remaining = remaining,
                percentage = percentage,
                state = state,
                transactionCount = transactionCount
            )
        }
    }
}

data class CategoryStatistics(
    val categoryId: Long,
    val categoryName: String,
    val categoryColor: String,
    val totalSpent: BigDecimal,
    val transactionCount: Int,
    val averagePerTransaction: BigDecimal,
    val percentageOfTotal: Float
)

data class MonthlySummary(
    val year: Int,
    val month: Int,
    val totalIncome: BigDecimal,
    val totalExpenses: BigDecimal,
    val netBalance: BigDecimal,
    val transactionCount: Int,
    val topExpenseCategories: List<CategoryStatistics>
) {
    val savingsRate: Float
        get() = if (totalIncome.compareTo(BigDecimal.ZERO) > 0) {
            totalIncome.subtract(totalExpenses)
                .divide(totalIncome, 4, RoundingMode.HALF_UP)
                .toFloat()
        } else {
            0f
        }
}

data class TransactionSearchQuery(
    val searchText: String? = null,
    val categoryIds: List<Long>? = null,
    val accountIds: List<Long>? = null,
    val types: List<TransactionType>? = null,
    val minAmount: BigDecimal? = null,
    val maxAmount: BigDecimal? = null,
    val startDate: LocalDateTime? = null,
    val endDate: LocalDateTime? = null,
    val tags: List<String>? = null
)

// Used by repositories for category spending data
data class CategorySpendingData(
    val categoryId: Long,
    val categoryName: String,
    val categoryColor: String,
    val totalSpent: BigDecimal,
    val transactionCount: Int
)

// Used by budget repository for budget status
data class BudgetStatusData(
    val budgetId: Long,
    val categoryId: Long,
    val categoryName: String,
    val categoryColor: String,
    val budgetLimit: BigDecimal,
    val spent: BigDecimal,
    val remaining: BigDecimal,
    val percentage: Float,
    val state: BudgetState
)

private fun BigDecimal.coerceAtLeast(minimum: BigDecimal): BigDecimal {
    return if (this < minimum) minimum else this
}
