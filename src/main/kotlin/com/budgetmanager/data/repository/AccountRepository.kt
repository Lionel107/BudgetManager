package com.budgetmanager.data.repository

import com.budgetmanager.data.remote.BigDecimalSerializer
import com.budgetmanager.data.remote.DtoDates
import com.budgetmanager.data.remote.SupabaseClientProvider
import com.budgetmanager.data.remote.dto.AccountDto
import com.budgetmanager.domain.model.Account
import com.budgetmanager.domain.model.AccountType
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.math.BigDecimal
import java.time.LocalDateTime

@Serializable
private data class IncrementBalanceParams(
    @SerialName("p_account_id") val accountId: Long,
    @SerialName("p_delta") @Serializable(with = BigDecimalSerializer::class) val delta: BigDecimal
)

@Serializable
private data class TransferParams(
    @SerialName("p_from_id") val fromId: Long,
    @SerialName("p_to_id") val toId: Long,
    @SerialName("p_amount") @Serializable(with = BigDecimalSerializer::class) val amount: BigDecimal,
    @SerialName("p_notes") val notes: String? = null
)

/**
 * Repository Comptes — backend Supabase (Postgrest). Les opérations sur le solde
 * (incrément atomique, transfert) passent par des fonctions RPC côté serveur.
 */
class AccountRepository(private val provider: SupabaseClientProvider) {

    private val db get() = provider.client
    private val _refreshTrigger = MutableStateFlow(0L)

    fun refresh() {
        _refreshTrigger.value = System.currentTimeMillis()
    }

    fun getActiveAccounts(): Flow<List<Account>> = _refreshTrigger.map {
        db.from("accounts").select {
            filter { eq("is_active", true) }
            order("display_order", Order.ASCENDING)
        }.decodeList<AccountDto>().map { it.toDomain() }
    }

    fun getAllAccounts(): Flow<List<Account>> = _refreshTrigger.map {
        db.from("accounts").select {
            order("display_order", Order.ASCENDING)
        }.decodeList<AccountDto>().map { it.toDomain() }
    }

    suspend fun getAccountById(id: Long): Account? =
        db.from("accounts").select { filter { eq("id", id) } }
            .decodeList<AccountDto>().firstOrNull()?.toDomain()

    suspend fun createAccount(account: Account): Long {
        val inserted = db.from("accounts").insert(account.toInsertDto()) { select() }
            .decodeSingle<AccountDto>()
        refresh()
        return inserted.id ?: 0L
    }

    suspend fun updateAccount(account: Account) {
        db.from("accounts").update(account.toInsertDto().copy(id = account.id)) {
            filter { eq("id", account.id) }
        }
        refresh()
    }

    /** Incrément atomique du solde (RPC serveur). */
    suspend fun updateBalance(accountId: Long, amount: BigDecimal) {
        db.postgrest.rpc("increment_account_balance", IncrementBalanceParams(accountId, amount))
        refresh()
    }

    /** Soft-delete : marque le compte inactif (préserve les transactions liées). */
    suspend fun deleteAccount(accountId: Long) {
        db.from("accounts").update({ set("is_active", false) }) {
            filter { eq("id", accountId) }
        }
        refresh()
    }

    suspend fun hardDeleteAccount(accountId: Long) {
        db.from("accounts").delete { filter { eq("id", accountId) } }
        refresh()
    }

    suspend fun restoreAccount(accountId: Long) {
        db.from("accounts").update({ set("is_active", true) }) {
            filter { eq("id", accountId) }
        }
        refresh()
    }

    /** Échange le displayOrder de deux comptes (renumérote si doublons). */
    suspend fun swapDisplayOrder(idA: Long, idB: Long) {
        val all = db.from("accounts").select {
            order("display_order", Order.ASCENDING)
        }.decodeList<AccountDto>()

        val orders = all.mapNotNull { it.displayOrder }.toSet()
        if (orders.size < all.size) {
            all.forEachIndexed { idx, dto ->
                db.from("accounts").update({ set("display_order", idx) }) {
                    filter { eq("id", dto.id ?: return@forEachIndexed) }
                }
            }
        }
        val orderA = all.find { it.id == idA }?.displayOrder ?: return
        val orderB = all.find { it.id == idB }?.displayOrder ?: return
        db.from("accounts").update({ set("display_order", orderB) }) { filter { eq("id", idA) } }
        db.from("accounts").update({ set("display_order", orderA) }) { filter { eq("id", idB) } }
        refresh()
    }

    fun getTotalBalance(): Flow<BigDecimal> = _refreshTrigger.map {
        db.from("accounts").select { filter { eq("is_active", true) } }
            .decodeList<AccountDto>()
            .fold(BigDecimal.ZERO) { acc, dto -> acc.add(dto.balance) }
    }

    /** Transfert atomique entre deux comptes (débit + crédit + 2 transactions, RPC serveur). */
    suspend fun transferBetweenAccounts(
        fromId: Long,
        toId: Long,
        amount: BigDecimal,
        notes: String? = null
    ) {
        db.postgrest.rpc("transfer_between_accounts", TransferParams(fromId, toId, amount, notes))
        refresh()
    }

    // ===== Mappers =====

    private fun AccountDto.toDomain() = Account(
        id = id ?: 0,
        name = name,
        balance = balance,
        accountType = AccountType.valueOf(accountType),
        currencyCode = currencyCode,
        isActive = isActive,
        displayOrder = displayOrder,
        color = color,
        iconName = iconName,
        createdAt = DtoDates.parseDateTime(createdAt) ?: LocalDateTime.now(),
        initialCapital = initialCapital,
        taxRate = taxRate
    )

    private fun Account.toInsertDto() = AccountDto(
        name = name,
        balance = balance,
        accountType = accountType.name,
        currencyCode = currencyCode,
        isActive = isActive,
        displayOrder = displayOrder,
        color = color,
        iconName = iconName,
        initialCapital = initialCapital,
        taxRate = taxRate
    )
}
