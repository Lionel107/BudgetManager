package com.budgetmanager.data.repository

import com.budgetmanager.data.remote.BigDecimalSerializer
import com.budgetmanager.data.remote.DtoDates
import com.budgetmanager.data.remote.SupabaseClientProvider
import com.budgetmanager.data.remote.dto.BudgetDto
import com.budgetmanager.data.remote.dto.TransactionDto
import com.budgetmanager.domain.model.Budget
import com.budgetmanager.domain.model.BudgetPeriodType
import com.budgetmanager.domain.model.BudgetState
import com.budgetmanager.domain.model.BudgetStatusData
import com.budgetmanager.domain.model.TransactionType
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.LocalDateTime

@Serializable
private data class TxnMini(
    val id: Long? = null,
    val date: String? = null,
    @SerialName("transaction_type") val transactionType: String? = null
)

@Serializable
private data class SplitAggRow(
    @SerialName("category_id") val categoryId: Long? = null,
    @Serializable(with = BigDecimalSerializer::class) val amount: BigDecimal,
    @SerialName("transaction") val transaction: TxnMini? = null
)

/** Repository Budgets — backend Supabase (Postgrest). */
class BudgetRepository(private val provider: SupabaseClientProvider) {

    private val db get() = provider.client
    private val cols = Columns.raw("*, category:categories!inner(name,color)")
    private val _refreshTrigger = MutableStateFlow(0L)

    fun refresh() {
        _refreshTrigger.value = System.currentTimeMillis()
    }

    fun getAllBudgets(): Flow<List<Budget>> = _refreshTrigger.map {
        db.from("budgets").select(cols).decodeList<BudgetDto>().map { it.toDomain() }
    }

    fun getBudgetsWithSpending(periodStart: LocalDate, periodEnd: LocalDate): Flow<List<BudgetStatusData>> =
        _refreshTrigger.map {
            val startDt = periodStart.atStartOfDay()
            val endDt = periodEnd.atTime(23, 59, 59)

            val budgets = db.from("budgets").select(cols).decodeList<BudgetDto>().map { it.toDomain() }

            // Dépenses de la période (filtrées serveur)
            val expenses = db.from("transactions").select {
                filter {
                    eq("transaction_type", TransactionType.EXPENSE.name)
                    gte("date", DtoDates.formatDateTime(startDt))
                    lte("date", DtoDates.formatDateTime(endDt))
                }
            }.decodeList<TransactionDto>()

            // Ventilations + transaction parente (filtrage période/type côté client)
            val splitRows = db.from("transaction_splits").select(
                Columns.raw("category_id, amount, transaction:transactions(id,date,transaction_type)")
            ).decodeList<SplitAggRow>().filter { row ->
                val t = row.transaction ?: return@filter false
                if (t.transactionType != TransactionType.EXPENSE.name) return@filter false
                val d = DtoDates.parseDateTime(t.date) ?: return@filter false
                !d.isBefore(startDt) && !d.isAfter(endDt)
            }

            val txnIdsWithSplits: Set<Long> = splitRows.mapNotNull { it.transaction?.id }.toSet()

            budgets.map { budget ->
                // 1) Dépenses "simples" : catégorie du budget, hors transactions ventilées
                val plainSpent = expenses
                    .filter { it.categoryId == budget.categoryId && (it.id !in txnIdsWithSplits) }
                    .fold(BigDecimal.ZERO) { acc, t -> acc.add(t.amount) }

                // 2) Dépenses issues des ventilations pointant vers la catégorie du budget
                val splitSpent = splitRows
                    .filter { it.categoryId == budget.categoryId }
                    .fold(BigDecimal.ZERO) { acc, s -> acc.add(s.amount) }

                val spent = plainSpent.add(splitSpent)
                val percentage = if (budget.limit.compareTo(BigDecimal.ZERO) != 0) {
                    spent.divide(budget.limit, 4, RoundingMode.HALF_UP).toFloat()
                } else 0f
                val remaining = budget.limit.subtract(spent).let {
                    if (it < BigDecimal.ZERO) BigDecimal.ZERO else it
                }
                val state = BudgetState.fromPercentage(percentage, budget.warningThreshold, budget.alertThreshold)

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

    suspend fun createBudget(budget: Budget): Long {
        val inserted = db.from("budgets").insert(budget.toInsertDto()) { select() }
            .decodeSingle<BudgetDto>()
        refresh()
        return inserted.id ?: 0L
    }

    /** Undo : recrée un budget supprimé avec son ID d'origine (no-op s'il existe déjà). */
    suspend fun restoreBudgetWithId(budget: Budget) {
        val exists = db.from("budgets").select { filter { eq("id", budget.id) } }
            .decodeList<BudgetDto>().isNotEmpty()
        if (!exists) {
            db.from("budgets").insert(budget.toInsertDto().copy(id = budget.id))
        }
        refresh()
    }

    suspend fun updateBudget(budget: Budget) {
        db.from("budgets").update(budget.toInsertDto().copy(id = budget.id)) {
            filter { eq("id", budget.id) }
        }
        refresh()
    }

    suspend fun deleteBudget(id: Long) {
        db.from("budgets").delete { filter { eq("id", id) } }
        refresh()
    }

    private fun BudgetDto.toDomain() = Budget(
        id = id ?: 0,
        categoryId = categoryId,
        categoryName = category?.name ?: "",
        categoryColor = category?.color ?: "#999999",
        periodType = BudgetPeriodType.valueOf(periodType),
        limit = budgetLimit,
        alertThreshold = alertThreshold,
        warningThreshold = warningThreshold,
        startDate = DtoDates.parseDate(startDate),
        endDate = DtoDates.parseDate(endDate)
    )

    private fun Budget.toInsertDto() = BudgetDto(
        categoryId = categoryId,
        periodType = periodType.name,
        budgetLimit = limit,
        alertThreshold = alertThreshold,
        warningThreshold = warningThreshold,
        startDate = startDate?.let { DtoDates.formatDate(it) },
        endDate = endDate?.let { DtoDates.formatDate(it) }
    )
}
