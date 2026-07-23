package com.budgetmanager.data.repository

import com.budgetmanager.data.remote.SupabaseClientProvider
import com.budgetmanager.data.remote.dto.TransactionSplitDto
import com.budgetmanager.domain.model.TransactionSplit
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns

/** Repository Ventilations (splits) — backend Supabase (Postgrest). */
class SplitRepository(private val provider: SupabaseClientProvider) {

    private val db get() = provider.client
    private val cols = Columns.raw("*, category:categories(name)")

    suspend fun getSplitsForTransaction(transactionId: Long): List<TransactionSplit> =
        db.from("transaction_splits").select(cols) {
            filter { eq("transaction_id", transactionId) }
        }.decodeList<TransactionSplitDto>().map { it.toDomain() }

    /** Remplace toutes les ventilations d'une transaction. */
    suspend fun setSplits(transactionId: Long, splits: List<TransactionSplit>) {
        db.from("transaction_splits").delete { filter { eq("transaction_id", transactionId) } }
        if (splits.isNotEmpty()) {
            val dtos = splits.map {
                TransactionSplitDto(
                    transactionId = transactionId,
                    categoryId = it.categoryId,
                    amount = it.amount,
                    notes = it.notes
                )
            }
            db.from("transaction_splits").insert(dtos)
        }
    }

    suspend fun deleteSplitsForTransaction(transactionId: Long) {
        db.from("transaction_splits").delete { filter { eq("transaction_id", transactionId) } }
    }

    private fun TransactionSplitDto.toDomain() = TransactionSplit(
        id = id ?: 0,
        transactionId = transactionId,
        categoryId = categoryId,
        categoryName = category?.name,
        amount = amount,
        notes = notes
    )
}
