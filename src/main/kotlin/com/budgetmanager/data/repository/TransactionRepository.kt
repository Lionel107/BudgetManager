package com.budgetmanager.data.repository

import com.budgetmanager.data.database.Accounts
import com.budgetmanager.data.database.Categories
import com.budgetmanager.data.database.Transactions
import com.budgetmanager.domain.model.CategorySpendingData
import com.budgetmanager.domain.model.Transaction
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
import java.time.YearMonth

class TransactionRepository(
    private val accountRepository: AccountRepository,
    private val tagRepository: TagRepository? = null
) {

    private val _refreshTrigger = MutableStateFlow(0L)

    fun refresh() {
        _refreshTrigger.value = System.currentTimeMillis()
    }

    private fun baseQuery(): ColumnSet {
        return Transactions
            .join(Accounts, JoinType.LEFT, Transactions.accountId, Accounts.id)
            .join(Categories, JoinType.LEFT, Transactions.categoryId, Categories.id)
    }

    fun getAllTransactions(): Flow<List<Transaction>> {
        return _refreshTrigger.map {
            withContext(Dispatchers.IO) {
                transaction {
                    baseQuery().selectAll()
                        .orderBy(Transactions.date, SortOrder.DESC)
                        .map { it.toTransaction() }
                }
            }
        }
    }

    fun getTransactionsByDateRange(start: LocalDateTime, end: LocalDateTime): Flow<List<Transaction>> {
        return _refreshTrigger.map {
            withContext(Dispatchers.IO) {
                transaction {
                    baseQuery().selectAll().where {
                        (Transactions.date greaterEq start) and (Transactions.date lessEq end)
                    }.orderBy(Transactions.date, SortOrder.DESC)
                        .map { it.toTransaction() }
                }
            }
        }
    }

    fun getCurrentMonthTransactions(): Flow<List<Transaction>> {
        val now = YearMonth.now()
        val start = now.atDay(1).atStartOfDay()
        val end = now.atEndOfMonth().atTime(23, 59, 59)
        return getTransactionsByDateRange(start, end)
    }

    fun searchTransactions(query: String): Flow<List<Transaction>> {
        return _refreshTrigger.map {
            withContext(Dispatchers.IO) {
                transaction {
                    baseQuery().selectAll().where {
                        (Transactions.title like "%$query%") or
                                (Transactions.notes like "%$query%")
                    }.orderBy(Transactions.date, SortOrder.DESC)
                        .map { it.toTransaction() }
                }
            }
        }
    }

    suspend fun getTransactionById(id: Long): Transaction? = withContext(Dispatchers.IO) {
        transaction {
            baseQuery().selectAll()
                .where { Transactions.id eq id }
                .map { it.toTransaction() }
                .singleOrNull()
        }
    }

    suspend fun createTransaction(txn: Transaction): Long = withContext(Dispatchers.IO) {
        val id = transaction {
            Transactions.insert {
                it[accountId] = txn.accountId
                it[categoryId] = txn.categoryId
                it[title] = txn.title
                it[amount] = txn.amount
                it[transactionType] = txn.transactionType.name
                it[date] = txn.date
                it[notes] = txn.notes
                it[tags] = txn.tags.joinToString(",")
                it[isRecurring] = txn.isRecurring
                it[recurringTransactionId] = txn.recurringTransactionId
                it[createdAt] = txn.createdAt
            } get Transactions.id
        }

        // Sync to normalized tag table (if available)
        tagRepository?.setTransactionTags(id, txn.tags)

        // Update account balance
        val balanceChange = when (txn.transactionType) {
            TransactionType.EXPENSE -> txn.amount.negate()
            TransactionType.INCOME -> txn.amount
            TransactionType.TRANSFER -> BigDecimal.ZERO
        }
        if (balanceChange.compareTo(BigDecimal.ZERO) != 0) {
            accountRepository.updateBalance(txn.accountId, balanceChange)
        }

        refresh()
        id
    }

    suspend fun updateTransaction(old: Transaction, new: Transaction): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                transaction {
                    Transactions.update({ Transactions.id eq new.id }) {
                        it[accountId] = new.accountId
                        it[categoryId] = new.categoryId
                        it[title] = new.title
                        it[amount] = new.amount
                        it[transactionType] = new.transactionType.name
                        it[date] = new.date
                        it[notes] = new.notes
                        it[tags] = new.tags.joinToString(",")
                        it[isRecurring] = new.isRecurring
                        it[recurringTransactionId] = new.recurringTransactionId
                    }
                }

                // Sync to normalized tag table
                tagRepository?.setTransactionTags(new.id, new.tags)

                // Reverse old balance effect
                val oldBalanceChange = when (old.transactionType) {
                    TransactionType.EXPENSE -> old.amount // reverse: add back
                    TransactionType.INCOME -> old.amount.negate() // reverse: subtract
                    TransactionType.TRANSFER -> BigDecimal.ZERO
                }
                if (oldBalanceChange.compareTo(BigDecimal.ZERO) != 0) {
                    accountRepository.updateBalance(old.accountId, oldBalanceChange)
                }

                // Apply new balance effect
                val newBalanceChange = when (new.transactionType) {
                    TransactionType.EXPENSE -> new.amount.negate()
                    TransactionType.INCOME -> new.amount
                    TransactionType.TRANSFER -> BigDecimal.ZERO
                }
                if (newBalanceChange.compareTo(BigDecimal.ZERO) != 0) {
                    accountRepository.updateBalance(new.accountId, newBalanceChange)
                }

                refresh()
            }
        }

    suspend fun deleteTransaction(txn: Transaction): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                transaction {
                    Transactions.deleteWhere { id eq txn.id }
                }

                // Reverse balance effect
                val balanceChange = when (txn.transactionType) {
                    TransactionType.EXPENSE -> txn.amount // add back
                    TransactionType.INCOME -> txn.amount.negate() // subtract
                    TransactionType.TRANSFER -> BigDecimal.ZERO
                }
                if (balanceChange.compareTo(BigDecimal.ZERO) != 0) {
                    accountRepository.updateBalance(txn.accountId, balanceChange)
                }

                refresh()
            }
        }

    suspend fun getTotalIncome(start: LocalDateTime, end: LocalDateTime): BigDecimal =
        withContext(Dispatchers.IO) {
            transaction {
                Transactions.selectAll().where {
                    (Transactions.transactionType eq TransactionType.INCOME.name) and
                            (Transactions.date greaterEq start) and
                            (Transactions.date lessEq end)
                }.map { it[Transactions.amount] }
                    .fold(BigDecimal.ZERO) { acc, amount -> acc.add(amount) }
            }
        }

    suspend fun getTotalExpenses(start: LocalDateTime, end: LocalDateTime): BigDecimal =
        withContext(Dispatchers.IO) {
            transaction {
                Transactions.selectAll().where {
                    (Transactions.transactionType eq TransactionType.EXPENSE.name) and
                            (Transactions.date greaterEq start) and
                            (Transactions.date lessEq end)
                }.map { it[Transactions.amount] }
                    .fold(BigDecimal.ZERO) { acc, amount -> acc.add(amount) }
            }
        }

    fun getCategorySpending(start: LocalDateTime, end: LocalDateTime): Flow<List<CategorySpendingData>> {
        return _refreshTrigger.map {
            withContext(Dispatchers.IO) {
                transaction {
                    val results = mutableMapOf<Long, CategorySpendingData>()

                    baseQuery().selectAll().where {
                        (Transactions.transactionType eq TransactionType.EXPENSE.name) and
                                (Transactions.date greaterEq start) and
                                (Transactions.date lessEq end) and
                                (Transactions.categoryId.isNotNull())
                    }.forEach { row ->
                        val catId = row[Transactions.categoryId]!!
                        val catName = row[Categories.name]
                        val catColor = row[Categories.color]
                        val amount = row[Transactions.amount]

                        val existing = results[catId]
                        if (existing != null) {
                            results[catId] = existing.copy(
                                totalSpent = existing.totalSpent.add(amount),
                                transactionCount = existing.transactionCount + 1
                            )
                        } else {
                            results[catId] = CategorySpendingData(
                                categoryId = catId,
                                categoryName = catName,
                                categoryColor = catColor,
                                totalSpent = amount,
                                transactionCount = 1
                            )
                        }
                    }

                    results.values.sortedByDescending { it.totalSpent }
                }
            }
        }
    }

    private fun ResultRow.toTransaction(): Transaction {
        val tagsStr = this[Transactions.tags]
        return Transaction(
            id = this[Transactions.id],
            accountId = this[Transactions.accountId],
            accountName = try { this.getOrNull(Accounts.name) } catch (_: Exception) { null },
            categoryId = this[Transactions.categoryId],
            categoryName = try { this.getOrNull(Categories.name) } catch (_: Exception) { null },
            categoryColor = try { this.getOrNull(Categories.color) } catch (_: Exception) { null },
            title = this[Transactions.title],
            amount = this[Transactions.amount],
            transactionType = TransactionType.valueOf(this[Transactions.transactionType]),
            date = this[Transactions.date],
            notes = this[Transactions.notes],
            tags = if (tagsStr.isBlank()) emptyList() else tagsStr.split(",").filter { it.isNotBlank() },
            isRecurring = this[Transactions.isRecurring],
            recurringTransactionId = this[Transactions.recurringTransactionId],
            createdAt = this[Transactions.createdAt]
        )
    }
}
