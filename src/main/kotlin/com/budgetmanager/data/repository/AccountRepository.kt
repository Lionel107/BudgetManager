package com.budgetmanager.data.repository

import com.budgetmanager.data.database.Accounts
import com.budgetmanager.data.database.Transactions
import com.budgetmanager.domain.model.Account
import com.budgetmanager.domain.model.AccountType
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
import java.time.LocalDateTime

class AccountRepository {

    private val _refreshTrigger = MutableStateFlow(0L)

    fun refresh() {
        _refreshTrigger.value = System.currentTimeMillis()
    }

    fun getActiveAccounts(): Flow<List<Account>> {
        return _refreshTrigger.map {
            withContext(Dispatchers.IO) {
                transaction {
                    Accounts.selectAll()
                        .where { Accounts.isActive eq true }
                        .orderBy(Accounts.displayOrder)
                        .map { it.toAccount() }
                }
            }
        }
    }

    fun getAllAccounts(): Flow<List<Account>> {
        return _refreshTrigger.map {
            withContext(Dispatchers.IO) {
                transaction {
                    Accounts.selectAll()
                        .orderBy(Accounts.displayOrder)
                        .map { it.toAccount() }
                }
            }
        }
    }

    suspend fun getAccountById(id: Long): Account? = withContext(Dispatchers.IO) {
        transaction {
            Accounts.selectAll()
                .where { Accounts.id eq id }
                .map { it.toAccount() }
                .singleOrNull()
        }
    }

    suspend fun createAccount(account: Account): Long = withContext(Dispatchers.IO) {
        transaction {
            Accounts.insert {
                it[name] = account.name
                it[balance] = account.balance
                it[accountType] = account.accountType.name
                it[currencyCode] = account.currencyCode
                it[isActive] = account.isActive
                it[displayOrder] = account.displayOrder
                it[color] = account.color
                it[iconName] = account.iconName
                it[createdAt] = account.createdAt
                it[initialCapital] = account.initialCapital
                it[taxRate] = account.taxRate
            } get Accounts.id
        }.also { refresh() }
    }

    suspend fun updateAccount(account: Account) = withContext(Dispatchers.IO) {
        transaction {
            Accounts.update({ Accounts.id eq account.id }) {
                it[name] = account.name
                it[balance] = account.balance
                it[accountType] = account.accountType.name
                it[currencyCode] = account.currencyCode
                it[isActive] = account.isActive
                it[displayOrder] = account.displayOrder
                it[color] = account.color
                it[iconName] = account.iconName
                it[initialCapital] = account.initialCapital
                it[taxRate] = account.taxRate
            }
        }
        refresh()
    }

    suspend fun updateBalance(accountId: Long, amount: BigDecimal) = withContext(Dispatchers.IO) {
        // Atomic UPDATE balance = balance + ? to avoid race conditions on concurrent transfers
        transaction {
            exec(
                "UPDATE accounts SET balance = balance + ? WHERE id = ?",
                listOf(
                    org.jetbrains.exposed.sql.DecimalColumnType(15, 2) to amount,
                    org.jetbrains.exposed.sql.LongColumnType() to accountId
                )
            )
        }
        refresh()
    }

    /**
     * Soft-delete: marks account as inactive instead of removing the row.
     * Preserves linked transactions and lets the user restore the account later.
     */
    suspend fun deleteAccount(accountId: Long) = withContext(Dispatchers.IO) {
        transaction {
            Accounts.update({ Accounts.id eq accountId }) {
                it[isActive] = false
            }
        }
        refresh()
    }

    /** Hard-delete reserved for cleanup — only call when no transactions reference it. */
    suspend fun hardDeleteAccount(accountId: Long) = withContext(Dispatchers.IO) {
        transaction {
            Accounts.deleteWhere { id eq accountId }
        }
        refresh()
    }

    /** Restore a soft-deleted account. */
    suspend fun restoreAccount(accountId: Long) = withContext(Dispatchers.IO) {
        transaction {
            Accounts.update({ Accounts.id eq accountId }) {
                it[isActive] = true
            }
        }
        refresh()
    }

    /**
     * Swap displayOrder between two accounts. First normalizes all accounts to
     * have unique sequential orders (handles legacy data where every row has 0).
     */
    suspend fun swapDisplayOrder(idA: Long, idB: Long) = withContext(Dispatchers.IO) {
        transaction {
            // Step 1: normalize — assign sequential orders if duplicates exist
            val all = Accounts.selectAll()
                .orderBy(Accounts.displayOrder, SortOrder.ASC)
                .orderBy(Accounts.id, SortOrder.ASC)
                .map { it[Accounts.id] to it[Accounts.displayOrder] }

            val orders = all.map { it.second }.toSet()
            if (orders.size < all.size) {
                // Duplicates exist — renumber everyone
                all.forEachIndexed { idx, (id, _) ->
                    Accounts.update({ Accounts.id eq id }) { it[displayOrder] = idx }
                }
            }

            // Step 2: swap
            val orderA = Accounts.selectAll().where { Accounts.id eq idA }
                .map { it[Accounts.displayOrder] }.singleOrNull() ?: return@transaction
            val orderB = Accounts.selectAll().where { Accounts.id eq idB }
                .map { it[Accounts.displayOrder] }.singleOrNull() ?: return@transaction
            Accounts.update({ Accounts.id eq idA }) { it[displayOrder] = orderB }
            Accounts.update({ Accounts.id eq idB }) { it[displayOrder] = orderA }
        }
        refresh()
    }

    fun getTotalBalance(): Flow<BigDecimal> {
        return _refreshTrigger.map {
            withContext(Dispatchers.IO) {
                transaction {
                    Accounts.selectAll()
                        .where { Accounts.isActive eq true }
                        .map { it[Accounts.balance] }
                        .fold(BigDecimal.ZERO) { acc, balance -> acc.add(balance) }
                }
            }
        }
    }

    suspend fun transferBetweenAccounts(
        fromId: Long,
        toId: Long,
        amount: BigDecimal,
        notes: String? = null
    ) = withContext(Dispatchers.IO) {
        transaction {
            // Atomic balance updates — avoids race condition with concurrent transfers
            exec(
                "UPDATE accounts SET balance = balance - ? WHERE id = ?",
                listOf(
                    org.jetbrains.exposed.sql.DecimalColumnType(15, 2) to amount,
                    org.jetbrains.exposed.sql.LongColumnType() to fromId
                )
            )
            exec(
                "UPDATE accounts SET balance = balance + ? WHERE id = ?",
                listOf(
                    org.jetbrains.exposed.sql.DecimalColumnType(15, 2) to amount,
                    org.jetbrains.exposed.sql.LongColumnType() to toId
                )
            )

            val now = LocalDateTime.now()

            // Create transfer-out transaction
            Transactions.insert {
                it[accountId] = fromId
                it[title] = "Transfert sortant"
                it[Transactions.amount] = amount
                it[transactionType] = TransactionType.TRANSFER.name
                it[date] = now
                it[Transactions.notes] = notes
                it[createdAt] = now
            }

            // Create transfer-in transaction
            Transactions.insert {
                it[accountId] = toId
                it[title] = "Transfert entrant"
                it[Transactions.amount] = amount
                it[transactionType] = TransactionType.TRANSFER.name
                it[date] = now
                it[Transactions.notes] = notes
                it[createdAt] = now
            }
        }
        refresh()
    }

    private fun ResultRow.toAccount(): Account {
        return Account(
            id = this[Accounts.id],
            name = this[Accounts.name],
            balance = this[Accounts.balance],
            accountType = AccountType.valueOf(this[Accounts.accountType]),
            currencyCode = this[Accounts.currencyCode],
            isActive = this[Accounts.isActive],
            displayOrder = this[Accounts.displayOrder],
            color = this[Accounts.color],
            iconName = this[Accounts.iconName],
            createdAt = this[Accounts.createdAt],
            initialCapital = this[Accounts.initialCapital],
            taxRate = this[Accounts.taxRate]
        )
    }
}
