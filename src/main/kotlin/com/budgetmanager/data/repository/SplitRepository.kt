package com.budgetmanager.data.repository

import com.budgetmanager.data.database.Categories
import com.budgetmanager.data.database.TransactionSplits
import com.budgetmanager.domain.model.TransactionSplit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

class SplitRepository {

    suspend fun getSplitsForTransaction(transactionId: Long): List<TransactionSplit> = withContext(Dispatchers.IO) {
        transaction {
            TransactionSplits.join(Categories, JoinType.LEFT, TransactionSplits.categoryId, Categories.id)
                .selectAll()
                .where { TransactionSplits.transactionId eq transactionId }
                .map { it.toSplit() }
        }
    }

    suspend fun setSplits(transactionId: Long, splits: List<TransactionSplit>) = withContext(Dispatchers.IO) {
        transaction {
            // Replace strategy: delete all existing then insert new ones
            TransactionSplits.deleteWhere { TransactionSplits.transactionId eq transactionId }
            for (s in splits) {
                TransactionSplits.insert {
                    it[TransactionSplits.transactionId] = transactionId
                    it[TransactionSplits.categoryId] = s.categoryId
                    it[TransactionSplits.amount] = s.amount
                    it[TransactionSplits.notes] = s.notes
                }
            }
        }
    }

    suspend fun deleteSplitsForTransaction(transactionId: Long) = withContext(Dispatchers.IO) {
        transaction {
            TransactionSplits.deleteWhere { TransactionSplits.transactionId eq transactionId }
        }
    }

    private fun ResultRow.toSplit(): TransactionSplit {
        return TransactionSplit(
            id = this[TransactionSplits.id],
            transactionId = this[TransactionSplits.transactionId],
            categoryId = this[TransactionSplits.categoryId],
            categoryName = this.getOrNull(Categories.name),
            amount = this[TransactionSplits.amount],
            notes = this[TransactionSplits.notes]
        )
    }
}
