package com.budgetmanager.data.repository

import com.budgetmanager.data.database.Budgets
import com.budgetmanager.data.database.Categories
import com.budgetmanager.data.database.Transactions
import com.budgetmanager.data.database.TransactionSplits
import com.budgetmanager.domain.model.Budget
import com.budgetmanager.domain.model.BudgetPeriodType
import com.budgetmanager.domain.model.BudgetState
import com.budgetmanager.domain.model.BudgetStatusData
import com.budgetmanager.domain.model.TransactionType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

class BudgetRepository {

    private val _refreshTrigger = MutableStateFlow(0L)

    fun refresh() {
        _refreshTrigger.value = System.currentTimeMillis()
    }

    fun getAllBudgets(): Flow<List<Budget>> {
        return _refreshTrigger.map {
            withContext(Dispatchers.IO) {
                transaction {
                    Budgets.join(Categories, JoinType.INNER, Budgets.categoryId, Categories.id)
                        .selectAll()
                        .map { it.toBudget() }
                }
            }
        }
    }

    fun getBudgetsWithSpending(periodStart: LocalDate, periodEnd: LocalDate): Flow<List<BudgetStatusData>> {
        return _refreshTrigger.map {
            withContext(Dispatchers.IO) {
                transaction {
                    val budgets = Budgets.join(Categories, JoinType.INNER, Budgets.categoryId, Categories.id)
                        .selectAll()
                        .map { it.toBudget() }

                    // Pre-fetch which transaction IDs (in the period) have splits — those
                    // bypass the main categoryId-based aggregation.
                    val periodStartDt = periodStart.atStartOfDay()
                    val periodEndDt = periodEnd.atTime(23, 59, 59)
                    val transactionsWithSplits: Set<Long> = TransactionSplits
                        .join(Transactions, JoinType.INNER, TransactionSplits.transactionId, Transactions.id)
                        .selectAll()
                        .where {
                            (Transactions.date greaterEq periodStartDt) and
                                    (Transactions.date lessEq periodEndDt) and
                                    (Transactions.transactionType eq TransactionType.EXPENSE.name)
                        }
                        .map { it[Transactions.id] }
                        .toSet()

                    budgets.map { budget ->
                        // Spending breakdown:
                        // 1) "Plain" amount = sum of EXPENSE transactions whose categoryId matches
                        //    AND that do NOT have any split (splits override the main category).
                        val plainSpent = Transactions.selectAll().where {
                            (Transactions.categoryId eq budget.categoryId) and
                                    (Transactions.transactionType eq TransactionType.EXPENSE.name) and
                                    (Transactions.date greaterEq periodStartDt) and
                                    (Transactions.date lessEq periodEndDt)
                        }
                            .filter { it[Transactions.id] !in transactionsWithSplits }
                            .map { it[Transactions.amount] }
                            .fold(BigDecimal.ZERO) { acc, amount -> acc.add(amount) }

                        // 2) Split-based amount = sum of split.amount where split.categoryId matches
                        //    for transactions in the period.
                        val splitSpent = TransactionSplits
                            .join(Transactions, JoinType.INNER, TransactionSplits.transactionId, Transactions.id)
                            .selectAll()
                            .where {
                                (TransactionSplits.categoryId eq budget.categoryId) and
                                        (Transactions.transactionType eq TransactionType.EXPENSE.name) and
                                        (Transactions.date greaterEq periodStartDt) and
                                        (Transactions.date lessEq periodEndDt)
                            }
                            .map { it[TransactionSplits.amount] }
                            .fold(BigDecimal.ZERO) { acc, amount -> acc.add(amount) }

                        val spent = plainSpent.add(splitSpent)

                        val percentage = if (budget.limit.compareTo(BigDecimal.ZERO) != 0) {
                            spent.divide(budget.limit, 4, RoundingMode.HALF_UP).toFloat()
                        } else {
                            0f
                        }

                        val remaining = budget.limit.subtract(spent).let {
                            if (it < BigDecimal.ZERO) BigDecimal.ZERO else it
                        }

                        val state = BudgetState.fromPercentage(
                            percentage,
                            budget.warningThreshold,
                            budget.alertThreshold
                        )

                        BudgetStatusData(
                            budgetId = budget.id,
                            categoryId = budget.categoryId,
                            categoryName = budget.categoryName,
                            categoryColor = budget.categoryColor,
                            budgetLimit = budget.limit,
                            spent = spent,
                            remaining = remaining,
                            percentage = percentage,
                            state = state
                        )
                    }
                }
            }
        }
    }

    suspend fun createBudget(budget: Budget): Long = withContext(Dispatchers.IO) {
        transaction {
            Budgets.insert {
                it[categoryId] = budget.categoryId
                it[periodType] = budget.periodType.name
                it[limit] = budget.limit
                it[alertThreshold] = budget.alertThreshold
                it[warningThreshold] = budget.warningThreshold
                it[startDate] = budget.startDate
                it[endDate] = budget.endDate
            } get Budgets.id
        }.also { refresh() }
    }

    /**
     * Used by undo: restore a deleted budget with its original ID.
     * If the row still exists (race), this is a no-op.
     */
    suspend fun restoreBudgetWithId(budget: Budget) = withContext(Dispatchers.IO) {
        transaction {
            val exists = Budgets.selectAll().where { Budgets.id eq budget.id }.count() > 0L
            if (!exists) {
                Budgets.insert {
                    it[id] = budget.id
                    it[categoryId] = budget.categoryId
                    it[periodType] = budget.periodType.name
                    it[limit] = budget.limit
                    it[alertThreshold] = budget.alertThreshold
                    it[warningThreshold] = budget.warningThreshold
                    it[startDate] = budget.startDate
                    it[endDate] = budget.endDate
                }
            }
        }
        refresh()
    }

    suspend fun updateBudget(budget: Budget) = withContext(Dispatchers.IO) {
        transaction {
            Budgets.update({ Budgets.id eq budget.id }) {
                it[categoryId] = budget.categoryId
                it[periodType] = budget.periodType.name
                it[limit] = budget.limit
                it[alertThreshold] = budget.alertThreshold
                it[warningThreshold] = budget.warningThreshold
                it[startDate] = budget.startDate
                it[endDate] = budget.endDate
            }
        }
        refresh()
    }

    suspend fun deleteBudget(id: Long) = withContext(Dispatchers.IO) {
        transaction {
            Budgets.deleteWhere { Budgets.id eq id }
        }
        refresh()
    }

    private fun ResultRow.toBudget(): Budget {
        return Budget(
            id = this[Budgets.id],
            categoryId = this[Budgets.categoryId],
            categoryName = this[Categories.name],
            categoryColor = this[Categories.color],
            periodType = BudgetPeriodType.valueOf(this[Budgets.periodType]),
            limit = this[Budgets.limit],
            alertThreshold = this[Budgets.alertThreshold],
            warningThreshold = this[Budgets.warningThreshold],
            startDate = this[Budgets.startDate],
            endDate = this[Budgets.endDate]
        )
    }
}
