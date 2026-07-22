package com.budgetmanager.domain.model

import java.time.LocalDate

enum class TransactionType {
    INCOME,
    EXPENSE,
    TRANSFER
}

enum class BudgetState {
    SAFE,
    WARNING,
    ALERT;

    companion object {
        fun fromPercentage(percentage: Float, warningThreshold: Float = 0.7f, alertThreshold: Float = 0.9f): BudgetState {
            return when {
                percentage >= alertThreshold -> ALERT
                percentage >= warningThreshold -> WARNING
                else -> SAFE
            }
        }
    }
}

enum class AccountType {
    CHECKING,
    SAVINGS,
    CASH,
    CREDIT_CARD,
    INVESTMENT
}

enum class FrequencyType {
    DAILY,
    WEEKLY,
    BI_WEEKLY,
    MONTHLY,
    QUARTERLY,
    YEARLY;

    fun calculateNextDate(fromDate: LocalDate, interval: Int = 1): LocalDate {
        return when (this) {
            DAILY -> fromDate.plusDays(interval.toLong())
            WEEKLY -> fromDate.plusWeeks(interval.toLong())
            BI_WEEKLY -> fromDate.plusWeeks(2L * interval)
            MONTHLY -> fromDate.plusMonths(interval.toLong())
            QUARTERLY -> fromDate.plusMonths(3L * interval)
            YEARLY -> fromDate.plusYears(interval.toLong())
        }
    }
}

enum class BudgetPeriodType {
    WEEKLY,
    MONTHLY,
    YEARLY,
    CUSTOM
}

enum class ExportFormat {
    CSV,
    PDF,
    DOCX
}
