package com.budgetmanager.data.repository

import com.budgetmanager.data.database.Categories
import com.budgetmanager.data.database.Challenges
import com.budgetmanager.data.database.Transactions
import com.budgetmanager.domain.model.Challenge
import com.budgetmanager.domain.model.ChallengeProgress
import com.budgetmanager.domain.model.ChallengeType
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

class ChallengeRepository {

    private val _refreshTrigger = MutableStateFlow(0L)

    fun refresh() {
        _refreshTrigger.value = System.currentTimeMillis()
    }

    fun getAll(): Flow<List<Challenge>> {
        return _refreshTrigger.map {
            withContext(Dispatchers.IO) {
                transaction {
                    Challenges
                        .join(Categories, JoinType.LEFT, Challenges.categoryId, Categories.id)
                        .selectAll()
                        .orderBy(Challenges.endDate, SortOrder.ASC)
                        .map { it.toChallenge() }
                }
            }
        }
    }

    suspend fun create(challenge: Challenge): Long = withContext(Dispatchers.IO) {
        transaction {
            Challenges.insert {
                it[title] = challenge.title
                it[description] = challenge.description
                it[type] = challenge.type.name
                it[targetAmount] = challenge.targetAmount
                it[categoryId] = challenge.categoryId
                it[startDate] = challenge.startDate
                it[endDate] = challenge.endDate
                it[isCompleted] = challenge.isCompleted
                it[createdAt] = challenge.createdAt
            } get Challenges.id
        }.also { refresh() }
    }

    suspend fun update(challenge: Challenge) = withContext(Dispatchers.IO) {
        transaction {
            Challenges.update({ Challenges.id eq challenge.id }) {
                it[title] = challenge.title
                it[description] = challenge.description
                it[targetAmount] = challenge.targetAmount
                it[categoryId] = challenge.categoryId
                it[startDate] = challenge.startDate
                it[endDate] = challenge.endDate
                it[isCompleted] = challenge.isCompleted
            }
        }
        refresh()
    }

    suspend fun delete(id: Long) = withContext(Dispatchers.IO) {
        transaction {
            Challenges.deleteWhere { Challenges.id eq id }
        }
        refresh()
    }

    /**
     * Compute current progress for each challenge by querying transactions in its date range.
     */
    suspend fun computeProgress(challenges: List<Challenge>): List<ChallengeProgress> = withContext(Dispatchers.IO) {
        transaction {
            challenges.map { ch ->
                val txs = Transactions.selectAll().where {
                    (Transactions.date greaterEq ch.startDate.atStartOfDay()) and
                    (Transactions.date lessEq ch.endDate.atTime(23, 59, 59))
                }.toList()

                val current: BigDecimal = when (ch.type) {
                    ChallengeType.SPEND_LIMIT -> {
                        // Total spent in the target category (or all expenses if no category)
                        txs.filter {
                            it[Transactions.transactionType] == TransactionType.EXPENSE.name &&
                            (ch.categoryId == null || it[Transactions.categoryId] == ch.categoryId)
                        }.fold(BigDecimal.ZERO) { acc, row -> acc.add(row[Transactions.amount]) }
                    }
                    ChallengeType.SAVE_AMOUNT -> {
                        // Income - expenses
                        val income = txs.filter { it[Transactions.transactionType] == TransactionType.INCOME.name }
                            .fold(BigDecimal.ZERO) { acc, row -> acc.add(row[Transactions.amount]) }
                        val expenses = txs.filter { it[Transactions.transactionType] == TransactionType.EXPENSE.name }
                            .fold(BigDecimal.ZERO) { acc, row -> acc.add(row[Transactions.amount]) }
                        income.subtract(expenses)
                    }
                }

                val target = ch.targetAmount
                val ratio = if (target.compareTo(BigDecimal.ZERO) != 0) {
                    current.divide(target, 4, RoundingMode.HALF_UP).toFloat().coerceAtLeast(0f)
                } else 0f

                val today = LocalDate.now()
                val daysTotal = (java.time.temporal.ChronoUnit.DAYS.between(ch.startDate, ch.endDate).toInt() + 1)
                    .coerceAtLeast(1)
                val daysElapsed = java.time.temporal.ChronoUnit.DAYS.between(ch.startDate, today)
                    .toInt().coerceIn(0, daysTotal)
                val daysRemaining = (daysTotal - daysElapsed).coerceAtLeast(0)
                val onTrack = when (ch.type) {
                    ChallengeType.SPEND_LIMIT -> {
                        // On track if proportional usage stays below limit
                        val expectedRatio = daysElapsed.toFloat() / daysTotal
                        ratio <= expectedRatio + 0.1f
                    }
                    ChallengeType.SAVE_AMOUNT -> ratio >= (daysElapsed.toFloat() / daysTotal) - 0.1f
                }

                val txCount = txs.count { row ->
                    when (ch.type) {
                        ChallengeType.SPEND_LIMIT -> row[Transactions.transactionType] == TransactionType.EXPENSE.name &&
                            (ch.categoryId == null || row[Transactions.categoryId] == ch.categoryId)
                        ChallengeType.SAVE_AMOUNT -> true
                    }
                }

                ChallengeProgress(ch, current, ratio, onTrack, daysTotal, daysElapsed, daysRemaining, txCount)
            }
        }
    }

    private fun ResultRow.toChallenge(): Challenge {
        return Challenge(
            id = this[Challenges.id],
            title = this[Challenges.title],
            description = this[Challenges.description],
            type = ChallengeType.valueOf(this[Challenges.type]),
            targetAmount = this[Challenges.targetAmount],
            categoryId = this[Challenges.categoryId],
            categoryName = this.getOrNull(Categories.name),
            startDate = this[Challenges.startDate],
            endDate = this[Challenges.endDate],
            isCompleted = this[Challenges.isCompleted],
            createdAt = this[Challenges.createdAt]
        )
    }
}
