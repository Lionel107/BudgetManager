package com.budgetmanager.data.repository

import com.budgetmanager.data.remote.DtoDates
import com.budgetmanager.data.remote.SupabaseClientProvider
import com.budgetmanager.data.remote.dto.ExchangeRateDto
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.math.BigDecimal
import java.math.RoundingMode

data class ExchangeRate(
    val id: Long,
    val from: String,
    val to: String,
    val rate: BigDecimal,
    val updatedAt: Long
)

/** Repository Taux de change — backend Supabase (Postgrest). */
class ExchangeRateRepository(private val provider: SupabaseClientProvider) {

    private val db get() = provider.client
    private val _refreshTrigger = MutableStateFlow(0L)

    fun refresh() {
        _refreshTrigger.value = System.currentTimeMillis()
    }

    fun getAll(): Flow<List<ExchangeRate>> = _refreshTrigger.map {
        db.from("exchange_rates").select {
            order("from_currency", Order.ASCENDING)
        }.decodeList<ExchangeRateDto>().map { it.toDomain() }
    }

    suspend fun getAllList(): List<ExchangeRate> =
        db.from("exchange_rates").select().decodeList<ExchangeRateDto>().map { it.toDomain() }

    suspend fun setRate(from: String, to: String, rate: BigDecimal) {
        val dto = ExchangeRateDto(
            fromCurrency = from.uppercase(),
            toCurrency = to.uppercase(),
            rate = rate
        )
        db.from("exchange_rates").upsert(dto, onConflict = "user_id,from_currency,to_currency")
        refresh()
    }

    suspend fun delete(id: Long) {
        db.from("exchange_rates").delete { filter { eq("id", id) } }
        refresh()
    }

    /** Convertit [amount] de [from] vers [to]. Retourne le montant brut si aucun taux valide. */
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
        return amount
    }

    private fun ExchangeRateDto.toDomain() = ExchangeRate(
        id = id ?: 0,
        from = fromCurrency,
        to = toCurrency,
        rate = rate,
        updatedAt = DtoDates.parseEpochMillis(updatedAt) ?: System.currentTimeMillis()
    )
}
