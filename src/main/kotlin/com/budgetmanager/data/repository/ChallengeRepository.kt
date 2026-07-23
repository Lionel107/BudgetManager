package com.budgetmanager.data.repository

import com.budgetmanager.data.remote.DtoDates
import com.budgetmanager.data.remote.SupabaseClientProvider
import com.budgetmanager.data.remote.dto.ChallengeDto
import com.budgetmanager.data.remote.dto.TransactionDto
import com.budgetmanager.domain.model.Challenge
import com.budgetmanager.domain.model.ChallengeProgress
import com.budgetmanager.domain.model.ChallengeType
import com.budgetmanager.domain.model.TransactionType
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

/** Repository Défis (challenges) — backend Supabase (Postgrest). */
class ChallengeRepository(private val provider: SupabaseClientProvider) {

    private val db get() = provider.client
    private val cols = Columns.raw("*, category:categories(name)")
    private val _refreshTrigger = MutableStateFlow(0L)

    fun refresh() {
        _refreshTrigger.value = System.currentTimeMillis()
    }

    fun getAll(): Flow<List<Challenge>> = _refreshTrigger.map {
        db.from("challenges").select(cols) {
            order("end_date", Order.ASCENDING)
        }.decodeList<ChallengeDto>().map { it.toDomain() }
    }

    suspend fun create(challenge: Challenge): Long {
        val inserted = db.from("challenges").insert(challenge.toInsertDto()) { select() }
            .decodeSingle<ChallengeDto>()
        refresh()
        return inserted.id ?: 0L
    }

    suspend fun update(challenge: Challenge) {
        db.from("challenges").update(challenge.toInsertDto().copy(id = challenge.id)) {
            filter { eq("id", challenge.id) }
        }
        refresh()
    }

    suspend fun delete(id: Long) {
        db.from("challenges").delete { filter { eq("id", id) } }
        refresh()
    }

    /** Calcule la progression de chaque défi en interrogeant les transactions de sa période. */
    suspend fun computeProgress(challenges: List<Challenge>): List<ChallengeProgress> {
        return challenges.map { ch ->
            val txs = db.from("transactions").select {
                filter {
                    gte("date", DtoDates.formatDateTime(ch.startDate.atStartOfDay()))
                    lte("date", DtoDates.formatDateTime(ch.endDate.atTime(23, 59, 59)))
                }
            }.decodeList<TransactionDto>()

            val current: BigDecimal = when (ch.type) {
                ChallengeType.SPEND_LIMIT -> txs.filter {
                    it.transactionType == TransactionType.EXPENSE.name &&
                        (ch.categoryId == null || it.categoryId == ch.categoryId)
                }.fold(BigDecimal.ZERO) { acc, row -> acc.add(row.amount) }

                ChallengeType.SAVE_AMOUNT -> {
                    val income = txs.filter { it.transactionType == TransactionType.INCOME.name }
                        .fold(BigDecimal.ZERO) { acc, row -> acc.add(row.amount) }
                    val expenses = txs.filter { it.transactionType == TransactionType.EXPENSE.name }
                        .fold(BigDecimal.ZERO) { acc, row -> acc.add(row.amount) }
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
                    val expectedRatio = daysElapsed.toFloat() / daysTotal
                    ratio <= expectedRatio + 0.1f
                }
                ChallengeType.SAVE_AMOUNT -> ratio >= (daysElapsed.toFloat() / daysTotal) - 0.1f
            }

            val txCount = txs.count { row ->
                when (ch.type) {
                    ChallengeType.SPEND_LIMIT -> row.transactionType == TransactionType.EXPENSE.name &&
                        (ch.categoryId == null || row.categoryId == ch.categoryId)
                    ChallengeType.SAVE_AMOUNT -> true
                }
            }

            ChallengeProgress(ch, current, ratio, onTrack, daysTotal, daysElapsed, daysRemaining, txCount)
        }
    }

    private fun ChallengeDto.toDomain() = Challenge(
        id = id ?: 0,
        title = title,
        description = description,
        type = ChallengeType.valueOf(type),
        targetAmount = targetAmount,
        categoryId = categoryId,
        categoryName = category?.name,
        startDate = DtoDates.parseDate(startDate) ?: LocalDate.now(),
        endDate = DtoDates.parseDate(endDate) ?: LocalDate.now(),
        isCompleted = isCompleted,
        createdAt = DtoDates.parseEpochMillis(createdAt) ?: System.currentTimeMillis()
    )

    private fun Challenge.toInsertDto() = ChallengeDto(
        title = title,
        description = description,
        type = type.name,
        targetAmount = targetAmount,
        categoryId = categoryId,
        startDate = DtoDates.formatDate(startDate),
        endDate = DtoDates.formatDate(endDate),
        isCompleted = isCompleted
    )
}
