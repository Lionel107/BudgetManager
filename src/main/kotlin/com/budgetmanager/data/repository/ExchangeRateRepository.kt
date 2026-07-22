package com.budgetmanager.data.repository

import com.budgetmanager.data.database.ExchangeRates
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

data class ExchangeRate(
    val id: Long,
    val from: String,
    val to: String,
    val rate: BigDecimal,
    val updatedAt: Long
)

class ExchangeRateRepository {

    private val _refreshTrigger = MutableStateFlow(0L)

    fun refresh() {
        _refreshTrigger.value = System.currentTimeMillis()
    }

    fun getAll(): Flow<List<ExchangeRate>> {
        return _refreshTrigger.map {
            withContext(Dispatchers.IO) {
                transaction {
                    ExchangeRates.selectAll()
                        .orderBy(ExchangeRates.fromCurrency)
                        .map { it.toExchangeRate() }
                }
            }
        }
    }

    suspend fun getAllList(): List<ExchangeRate> = withContext(Dispatchers.IO) {
        transaction {
            ExchangeRates.selectAll().map { it.toExchangeRate() }
        }
    }

    suspend fun setRate(from: String, to: String, rate: BigDecimal) = withContext(Dispatchers.IO) {
        val fromUp = from.uppercase()
        val toUp = to.uppercase()
        transaction {
            val existing = ExchangeRates.selectAll()
                .where { (ExchangeRates.fromCurrency eq fromUp) and (ExchangeRates.toCurrency eq toUp) }
                .singleOrNull()
            if (existing != null) {
                ExchangeRates.update({ ExchangeRates.id eq existing[ExchangeRates.id] }) {
                    it[ExchangeRates.rate] = rate
                    it[updatedAt] = System.currentTimeMillis()
                }
            } else {
                ExchangeRates.insert {
                    it[fromCurrency] = fromUp
                    it[toCurrency] = toUp
                    it[ExchangeRates.rate] = rate
                    it[updatedAt] = System.currentTimeMillis()
                }
            }
        }
        refresh()
    }

    suspend fun delete(id: Long) = withContext(Dispatchers.IO) {
        transaction {
            ExchangeRates.deleteWhere { ExchangeRates.id eq id }
        }
        refresh()
    }

    /** Convert [amount] from [from] currency to [to] using stored rates. Falls back to raw amount if missing or zero. */
    suspend fun convert(amount: BigDecimal, from: String, to: String): BigDecimal {
        if (from.equals(to, ignoreCase = true)) return amount
        val all = getAllList()
        val direct = all.firstOrNull { it.from.equals(from, true) && it.to.equals(to, true) }
        if (direct != null && direct.rate > BigDecimal.ZERO) {
            return amount.multiply(direct.rate).setScale(2, RoundingMode.HALF_UP)
        }
        val inverse = all.firstOrNull { it.from.equals(to, true) && it.to.equals(from, true) }
        if (inverse != null && inverse.rate > BigDecimal.ZERO) {
            return amount.divide(inverse.rate, 8, RoundingMode.HALF_UP).setScale(2, RoundingMode.HALF_UP)
        }
        return amount // No (valid) rate defined → keep raw amount
    }

    private fun ResultRow.toExchangeRate(): ExchangeRate {
        return ExchangeRate(
            id = this[ExchangeRates.id],
            from = this[ExchangeRates.fromCurrency],
            to = this[ExchangeRates.toCurrency],
            rate = this[ExchangeRates.rate],
            updatedAt = this[ExchangeRates.updatedAt]
        )
    }
}
