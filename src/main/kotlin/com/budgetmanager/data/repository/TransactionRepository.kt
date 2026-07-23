package com.budgetmanager.data.repository

import com.budgetmanager.data.remote.BigDecimalSerializer
import com.budgetmanager.data.remote.DtoDates
import com.budgetmanager.data.remote.SupabaseClientProvider
import com.budgetmanager.data.remote.dto.TransactionDto
import com.budgetmanager.domain.model.CategorySpendingData
import com.budgetmanager.domain.model.Transaction
import com.budgetmanager.domain.model.TransactionType
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.rpc
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.YearMonth

@Serializable
private data class CreateTxnParams(
    @SerialName("p_account_id") val accountId: Long,
    @SerialName("p_title") val title: String,
    @SerialName("p_amount") @Serializable(with = BigDecimalSerializer::class) val amount: BigDecimal,
    @SerialName("p_type") val type: String,
    @SerialName("p_date") val date: String,
    @SerialName("p_category_id") val categoryId: Long? = null,
    @SerialName("p_notes") val notes: String? = null,
    @SerialName("p_is_recurring") val isRecurring: Boolean = false,
    @SerialName("p_recurring_id") val recurringId: Long? = null
)

@Serializable
private data class UpdateTxnParams(
    @SerialName("p_id") val id: Long,
    @SerialName("p_account_id") val accountId: Long,
    @SerialName("p_title") val title: String,
    @SerialName("p_amount") @Serializable(with = BigDecimalSerializer::class) val amount: BigDecimal,
    @SerialName("p_type") val type: String,
    @SerialName("p_date") val date: String,
    @SerialName("p_category_id") val categoryId: Long? = null,
    @SerialName("p_notes") val notes: String? = null,
    @SerialName("p_is_recurring") val isRecurring: Boolean = false,
    @SerialName("p_recurring_id") val recurringId: Long? = null
)

@Serializable private data class DeleteTxnParams(@SerialName("p_id") val id: Long)

/**
 * Repository Transactions — backend Supabase (Postgrest). Les mutations qui
 * touchent le solde passent par des fonctions RPC atomiques (insert/update/delete
 * + ajustement du solde). Les tags normalisés sont synchronisés via TagRepository.
 */
class TransactionRepository(
    private val provider: SupabaseClientProvider,
    private val accountRepository: AccountRepository,
    private val tagRepository: TagRepository? = null
) {

    private val db get() = provider.client
    private val _refreshTrigger = MutableStateFlow(0L)

    private val cols = Columns.raw(
        "*, category:categories(name,color), account:accounts(name), tag_links:transaction_tags(tag:tags(name))"
    )

    fun refresh() {
        _refreshTrigger.value = System.currentTimeMillis()
    }

    fun getAllTransactions(): Flow<List<Transaction>> = _refreshTrigger.map {
        db.from("transactions").select(cols) {
            order("date", Order.DESCENDING)
        }.decodeList<TransactionDto>().map { it.toDomain() }
    }

    fun getTransactionsByDateRange(start: LocalDateTime, end: LocalDateTime): Flow<List<Transaction>> =
        _refreshTrigger.map {
            db.from("transactions").select(cols) {
                filter {
                    gte("date", DtoDates.formatDateTime(start))
                    lte("date", DtoDates.formatDateTime(end))
                }
                order("date", Order.DESCENDING)
            }.decodeList<TransactionDto>().map { it.toDomain() }
        }

    fun getCurrentMonthTransactions(): Flow<List<Transaction>> {
        val now = YearMonth.now()
        return getTransactionsByDateRange(now.atDay(1).atStartOfDay(), now.atEndOfMonth().atTime(23, 59, 59))
    }

    fun searchTransactions(query: String): Flow<List<Transaction>> = _refreshTrigger.map {
        db.from("transactions").select(cols) {
            filter {
                or {
                    ilike("title", "%$query%")
                    ilike("notes", "%$query%")
                }
            }
            order("date", Order.DESCENDING)
        }.decodeList<TransactionDto>().map { it.toDomain() }
    }

    suspend fun getTransactionById(id: Long): Transaction? =
        db.from("transactions").select(cols) { filter { eq("id", id) } }
            .decodeList<TransactionDto>().firstOrNull()?.toDomain()

    suspend fun createTransaction(txn: Transaction): Long {
        val id = db.postgrest.rpc(
            "create_transaction",
            CreateTxnParams(
                accountId = txn.accountId,
                title = txn.title,
                amount = txn.amount,
                type = txn.transactionType.name,
                date = DtoDates.formatDateTime(txn.date),
                categoryId = txn.categoryId,
                notes = txn.notes,
                isRecurring = txn.isRecurring,
                recurringId = txn.recurringTransactionId
            )
        ).decodeAs<Long>()

        tagRepository?.setTransactionTags(id, txn.tags)
        accountRepository.refresh()
        refresh()
        return id
    }

    suspend fun updateTransaction(old: Transaction, new: Transaction): Result<Unit> = runCatching {
        db.postgrest.rpc(
            "update_transaction",
            UpdateTxnParams(
                id = new.id,
                accountId = new.accountId,
                title = new.title,
                amount = new.amount,
                type = new.transactionType.name,
                date = DtoDates.formatDateTime(new.date),
                categoryId = new.categoryId,
                notes = new.notes,
                isRecurring = new.isRecurring,
                recurringId = new.recurringTransactionId
            )
        )
        tagRepository?.setTransactionTags(new.id, new.tags)
        accountRepository.refresh()
        refresh()
    }

    suspend fun deleteTransaction(txn: Transaction): Result<Unit> = runCatching {
        db.postgrest.rpc("delete_transaction", DeleteTxnParams(txn.id))
        accountRepository.refresh()
        refresh()
    }

    suspend fun getTotalIncome(start: LocalDateTime, end: LocalDateTime): BigDecimal =
        sumAmounts(TransactionType.INCOME, start, end)

    suspend fun getTotalExpenses(start: LocalDateTime, end: LocalDateTime): BigDecimal =
        sumAmounts(TransactionType.EXPENSE, start, end)

    private suspend fun sumAmounts(type: TransactionType, start: LocalDateTime, end: LocalDateTime): BigDecimal =
        db.from("transactions").select {
            filter {
                eq("transaction_type", type.name)
                gte("date", DtoDates.formatDateTime(start))
                lte("date", DtoDates.formatDateTime(end))
            }
        }.decodeList<TransactionDto>().fold(BigDecimal.ZERO) { acc, dto -> acc.add(dto.amount) }

    fun getCategorySpending(start: LocalDateTime, end: LocalDateTime): Flow<List<CategorySpendingData>> =
        _refreshTrigger.map {
            val rows = db.from("transactions").select(cols) {
                filter {
                    eq("transaction_type", TransactionType.EXPENSE.name)
                    gte("date", DtoDates.formatDateTime(start))
                    lte("date", DtoDates.formatDateTime(end))
                }
            }.decodeList<TransactionDto>()

            val results = mutableMapOf<Long, CategorySpendingData>()
            rows.forEach { dto ->
                val catId = dto.categoryId ?: return@forEach  // ignore les dépenses sans catégorie
                val existing = results[catId]
                results[catId] = if (existing != null) {
                    existing.copy(
                        totalSpent = existing.totalSpent.add(dto.amount),
                        transactionCount = existing.transactionCount + 1
                    )
                } else {
                    CategorySpendingData(
                        categoryId = catId,
                        categoryName = dto.category?.name ?: "",
                        categoryColor = dto.category?.color ?: "#999999",
                        totalSpent = dto.amount,
                        transactionCount = 1
                    )
                }
            }
            results.values.sortedByDescending { it.totalSpent }
        }

    private fun TransactionDto.toDomain() = Transaction(
        id = id ?: 0,
        accountId = accountId,
        accountName = account?.name,
        categoryId = categoryId,
        categoryName = category?.name,
        categoryColor = category?.color,
        title = title,
        amount = amount,
        transactionType = TransactionType.valueOf(transactionType),
        date = DtoDates.parseDateTime(date) ?: LocalDateTime.now(),
        notes = notes,
        tags = tagLinks?.mapNotNull { it.tag?.name } ?: emptyList(),
        isRecurring = isRecurring,
        recurringTransactionId = recurringTransactionId,
        createdAt = DtoDates.parseDateTime(createdAt) ?: LocalDateTime.now()
    )
}
