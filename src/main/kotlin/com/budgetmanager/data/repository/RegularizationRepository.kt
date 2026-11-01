package com.budgetmanager.data.repository

import com.budgetmanager.data.remote.BigDecimalSerializer
import com.budgetmanager.data.remote.DtoDates
import com.budgetmanager.data.remote.SupabaseClientProvider
import com.budgetmanager.data.remote.dto.AccountRegularizationDto
import com.budgetmanager.domain.model.AccountRegularization
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

@Serializable
private data class RegularizeParams(
    @SerialName("p_account_id") val accountId: Long,
    @SerialName("p_real_balance") @Serializable(with = BigDecimalSerializer::class) val realBalance: BigDecimal,
    @SerialName("p_note") val note: String? = null
)

/**
 * Régularisation des comptes : recale un compte sur son solde réel via la RPC
 * `regularize_account` (atomique : historique + nouveau solde). Aucune transaction
 * n'est créée, donc les budgets et statistiques ne sont pas faussés.
 */
class RegularizationRepository(
    private val provider: SupabaseClientProvider,
    private val accountRepository: AccountRepository
) {

    private val db get() = provider.client
    private val _refreshTrigger = MutableStateFlow(0L)

    fun refresh() {
        _refreshTrigger.value = System.currentTimeMillis()
    }

    /** Historique des régularisations, la plus récente d'abord. */
    fun getHistory(): Flow<List<AccountRegularization>> = _refreshTrigger.map {
        db.from("account_regularizations").select(Columns.raw("*, account:accounts(name)")) {
            order("regularized_at", Order.DESCENDING)
            limit(50)
        }.decodeList<AccountRegularizationDto>().map { it.toDomain() }
    }

    /** Recale [accountId] sur [realBalance]. Rafraîchit les comptes et l'historique. */
    suspend fun regularize(accountId: Long, realBalance: BigDecimal, note: String?) {
        db.postgrest.rpc("regularize_account", RegularizeParams(accountId, realBalance, note?.takeIf { it.isNotBlank() }))
        accountRepository.refresh()
        refresh()
    }

    private fun AccountRegularizationDto.toDomain() = AccountRegularization(
        id = id ?: 0,
        accountId = accountId,
        accountName = account?.name,
        regularizedAt = DtoDates.parseDateTime(regularizedAt) ?: LocalDateTime.now(),
        previousBalance = previousBalance,
        realBalance = realBalance,
        difference = difference,
        note = note
    )
}
