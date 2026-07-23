package com.budgetmanager.data.repository

import com.budgetmanager.data.remote.DtoDates
import com.budgetmanager.data.remote.SupabaseClientProvider
import com.budgetmanager.data.remote.dto.RecurringTransactionDto
import com.budgetmanager.domain.model.FrequencyType
import com.budgetmanager.domain.model.RecurringTransaction
import com.budgetmanager.domain.model.TransactionType
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.rpc
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/** Repository Récurrences — backend Supabase. Le traitement des échéances est
 * délégué à la fonction serveur process_recurring_transactions (atomique). */
class RecurringTransactionRepository(
    private val provider: SupabaseClientProvider,
    private val accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository
) {

    private val db get() = provider.client
    private val _refreshTrigger = MutableStateFlow(0L)

    // Jointures : catégorie + compte source + compte destination (2 FK vers accounts → désambiguïsées par contrainte)
    private val cols = Columns.raw(
        "*, category:categories(name), " +
            "account:accounts!recurring_transactions_account_id_fkey(name), " +
            "destination:accounts!recurring_transactions_destination_account_id_fkey(name)"
    )

    fun refresh() {
        _refreshTrigger.value = System.currentTimeMillis()
    }

    fun getAll(): Flow<List<RecurringTransaction>> = _refreshTrigger.map {
        db.from("recurring_transactions").select(cols) {
            order("next_due_date", Order.ASCENDING)
        }.decodeList<RecurringTransactionDto>().map { it.toDomain() }
    }

    fun getActive(): Flow<List<RecurringTransaction>> = _refreshTrigger.map {
        db.from("recurring_transactions").select(cols) {
            filter { eq("is_active", true) }
            order("next_due_date", Order.ASCENDING)
        }.decodeList<RecurringTransactionDto>().map { it.toDomain() }
    }

    suspend fun create(recurring: RecurringTransaction): Long {
        val inserted = db.from("recurring_transactions").insert(recurring.toInsertDto()) { select() }
            .decodeSingle<RecurringTransactionDto>()
        processRecurringTransactions()
        return inserted.id ?: 0L
    }

    suspend fun update(recurring: RecurringTransaction) {
        db.from("recurring_transactions").update(recurring.toInsertDto().copy(id = recurring.id)) {
            filter { eq("id", recurring.id) }
        }
        processRecurringTransactions()
    }

    suspend fun delete(id: Long) {
        db.from("recurring_transactions").delete { filter { eq("id", id) } }
        refresh()
    }

    /** Génère les échéances échues côté serveur (atomique), puis rafraîchit les vues. */
    suspend fun processRecurringTransactions() {
        db.postgrest.rpc("process_recurring_transactions")
        accountRepository.refresh()
        transactionRepository.refresh()
        refresh()
    }

    private fun RecurringTransactionDto.toDomain() = RecurringTransaction(
        id = id ?: 0,
        title = title,
        amount = amount,
        categoryId = categoryId,
        categoryName = category?.name,
        accountId = accountId,
        accountName = account?.name,
        frequencyType = FrequencyType.valueOf(frequencyType),
        interval = repeatInterval,
        startDate = DtoDates.parseDate(startDate) ?: LocalDate.now(),
        endDate = DtoDates.parseDate(endDate),
        lastGeneratedDate = DtoDates.parseDate(lastGeneratedDate),
        nextDueDate = DtoDates.parseDate(nextDueDate) ?: LocalDate.now(),
        transactionType = TransactionType.valueOf(transactionType),
        isActive = isActive,
        notes = notes,
        destinationAccountId = destinationAccountId,
        destinationAccountName = destination?.name
    )

    private fun RecurringTransaction.toInsertDto() = RecurringTransactionDto(
        title = title,
        amount = amount,
        categoryId = categoryId,
        accountId = accountId,
        frequencyType = frequencyType.name,
        repeatInterval = interval,
        startDate = DtoDates.formatDate(startDate),
        endDate = endDate?.let { DtoDates.formatDate(it) },
        lastGeneratedDate = lastGeneratedDate?.let { DtoDates.formatDate(it) },
        nextDueDate = DtoDates.formatDate(nextDueDate),
        transactionType = transactionType.name,
        isActive = isActive,
        notes = notes,
        destinationAccountId = destinationAccountId
    )
}
