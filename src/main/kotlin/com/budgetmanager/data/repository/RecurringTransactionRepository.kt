package com.budgetmanager.data.repository

import com.budgetmanager.data.database.Accounts
import com.budgetmanager.data.database.Categories
import com.budgetmanager.data.database.RecurringTransactions
import com.budgetmanager.data.database.Transactions
import com.budgetmanager.domain.model.FrequencyType
import com.budgetmanager.domain.model.RecurringTransaction
import com.budgetmanager.domain.model.TransactionType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDate
import java.time.LocalDateTime

class RecurringTransactionRepository(
    private val accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository
) {

    private val _refreshTrigger = MutableStateFlow(0L)

    fun refresh() {
        _refreshTrigger.value = System.currentTimeMillis()
    }

    private fun baseQuery(): ColumnSet {
        return RecurringTransactions
            .join(Accounts, JoinType.LEFT, RecurringTransactions.accountId, Accounts.id)
            .join(Categories, JoinType.LEFT, RecurringTransactions.categoryId, Categories.id)
    }

    fun getAll(): Flow<List<RecurringTransaction>> {
        return _refreshTrigger.map {
            withContext(Dispatchers.IO) {
                transaction {
                    baseQuery().selectAll()
                        .orderBy(RecurringTransactions.nextDueDate)
                        .map { it.toRecurringTransaction() }
                }
            }
        }
    }

    fun getActive(): Flow<List<RecurringTransaction>> {
        return _refreshTrigger.map {
            withContext(Dispatchers.IO) {
                transaction {
                    baseQuery().selectAll()
                        .where { RecurringTransactions.isActive eq true }
                        .orderBy(RecurringTransactions.nextDueDate)
                        .map { it.toRecurringTransaction() }
                }
            }
        }
    }

    suspend fun create(recurring: RecurringTransaction): Long = withContext(Dispatchers.IO) {
        val id = transaction {
            RecurringTransactions.insert {
                it[title] = recurring.title
                it[amount] = recurring.amount
                it[categoryId] = recurring.categoryId
                it[accountId] = recurring.accountId
                it[frequencyType] = recurring.frequencyType.name
                it[interval] = recurring.interval
                it[startDate] = recurring.startDate
                it[endDate] = recurring.endDate
                it[lastGeneratedDate] = recurring.lastGeneratedDate
                it[nextDueDate] = recurring.nextDueDate
                it[transactionType] = recurring.transactionType.name
                it[isActive] = recurring.isActive
                it[notes] = recurring.notes
                it[destinationAccountId] = recurring.destinationAccountId
            } get RecurringTransactions.id
        }
        // Process immediately if any due dates have passed
        processRecurringTransactions()
        id
    }

    suspend fun update(recurring: RecurringTransaction) = withContext(Dispatchers.IO) {
        transaction {
            RecurringTransactions.update({ RecurringTransactions.id eq recurring.id }) {
                it[title] = recurring.title
                it[amount] = recurring.amount
                it[categoryId] = recurring.categoryId
                it[accountId] = recurring.accountId
                it[frequencyType] = recurring.frequencyType.name
                it[interval] = recurring.interval
                it[startDate] = recurring.startDate
                it[endDate] = recurring.endDate
                it[lastGeneratedDate] = recurring.lastGeneratedDate
                it[nextDueDate] = recurring.nextDueDate
                it[transactionType] = recurring.transactionType.name
                it[isActive] = recurring.isActive
                it[notes] = recurring.notes
                it[destinationAccountId] = recurring.destinationAccountId
            }
        }
        // Process immediately in case the new date is now in the past
        processRecurringTransactions()
    }

    suspend fun delete(id: Long) = withContext(Dispatchers.IO) {
        transaction {
            RecurringTransactions.deleteWhere { RecurringTransactions.id eq id }
        }
        refresh()
    }

    suspend fun processRecurringTransactions() = withContext(Dispatchers.IO) {
        val today = LocalDate.now()

        transaction {
            val activeRecurrings = RecurringTransactions.selectAll()
                .where {
                    (RecurringTransactions.isActive eq true) and
                            (RecurringTransactions.nextDueDate lessEq today)
                }.map { row ->
                    val id = row[RecurringTransactions.id]
                    val title = row[RecurringTransactions.title]
                    val amount = row[RecurringTransactions.amount]
                    val categoryId = row[RecurringTransactions.categoryId]
                    val accountId = row[RecurringTransactions.accountId]
                    val frequencyType = FrequencyType.valueOf(row[RecurringTransactions.frequencyType])
                    val interval = row[RecurringTransactions.interval]
                    val endDate = row[RecurringTransactions.endDate]
                    val nextDueDate = row[RecurringTransactions.nextDueDate]
                    val transactionType = TransactionType.valueOf(row[RecurringTransactions.transactionType])
                    val notes = row[RecurringTransactions.notes]
                    val destinationAccountId = row[RecurringTransactions.destinationAccountId]

                    data class PendingInfo(
                        val id: Long,
                        val title: String,
                        val amount: java.math.BigDecimal,
                        val categoryId: Long?,
                        val accountId: Long,
                        val frequencyType: FrequencyType,
                        val interval: Int,
                        val endDate: LocalDate?,
                        val nextDueDate: LocalDate,
                        val transactionType: TransactionType,
                        val notes: String?,
                        val destinationAccountId: Long?
                    )

                    PendingInfo(id, title, amount, categoryId, accountId, frequencyType, interval, endDate, nextDueDate, transactionType, notes, destinationAccountId)
                }

            for (rec in activeRecurrings) {
                // Verify the source (and destination) accounts still exist.
                // If hard-deleted, deactivate the recurring to avoid an FK violation
                // that would leave the cursor advanced without an actual transaction.
                val srcExists = Accounts.selectAll()
                    .where { Accounts.id eq rec.accountId }
                    .count() > 0L
                val destExists = rec.destinationAccountId?.let { destId ->
                    Accounts.selectAll().where { Accounts.id eq destId }.count() > 0L
                } ?: true
                if (!srcExists || !destExists) {
                    RecurringTransactions.update({ RecurringTransactions.id eq rec.id }) {
                        it[isActive] = false
                    }
                    continue
                }

                var currentDueDate = rec.nextDueDate
                val safeInterval = if (rec.interval <= 0) 1 else rec.interval
                var iterations = 0
                val maxIterations = 1000 // safety: never loop forever

                // Generate transactions for all missed dates up to today
                while (!currentDueDate.isAfter(today) && iterations < maxIterations) {
                    iterations++

                    // Check end date — stop if we've passed it
                    if (rec.endDate != null && currentDueDate.isAfter(rec.endDate)) {
                        RecurringTransactions.update({ RecurringTransactions.id eq rec.id }) {
                            it[isActive] = false
                            it[lastGeneratedDate] = today
                        }
                        break
                    }

                    val now = LocalDateTime.now()

                    if (rec.transactionType == TransactionType.TRANSFER && rec.destinationAccountId != null) {
                        // TRANSFER: create two transactions (out + in) and update both balances atomically
                        Transactions.insert {
                            it[accountId] = rec.accountId
                            it[categoryId] = rec.categoryId
                            it[Transactions.title] = "${rec.title} (sortant)"
                            it[Transactions.amount] = rec.amount
                            it[transactionType] = TransactionType.TRANSFER.name
                            it[date] = currentDueDate.atStartOfDay()
                            it[Transactions.notes] = rec.notes
                            it[tags] = ""
                            it[isRecurring] = true
                            it[recurringTransactionId] = rec.id
                            it[createdAt] = now
                        }
                        Transactions.insert {
                            it[accountId] = rec.destinationAccountId
                            it[categoryId] = rec.categoryId
                            it[Transactions.title] = "${rec.title} (entrant)"
                            it[Transactions.amount] = rec.amount
                            it[transactionType] = TransactionType.TRANSFER.name
                            it[date] = currentDueDate.atStartOfDay()
                            it[Transactions.notes] = rec.notes
                            it[tags] = ""
                            it[isRecurring] = true
                            it[recurringTransactionId] = rec.id
                            it[createdAt] = now
                        }
                        // Source -, Destination +
                        exec(
                            "UPDATE accounts SET balance = balance - ? WHERE id = ?",
                            listOf(
                                org.jetbrains.exposed.sql.DecimalColumnType(15, 2) to rec.amount,
                                org.jetbrains.exposed.sql.LongColumnType() to rec.accountId
                            )
                        )
                        exec(
                            "UPDATE accounts SET balance = balance + ? WHERE id = ?",
                            listOf(
                                org.jetbrains.exposed.sql.DecimalColumnType(15, 2) to rec.amount,
                                org.jetbrains.exposed.sql.LongColumnType() to rec.destinationAccountId
                            )
                        )
                    } else {
                        Transactions.insert {
                            it[accountId] = rec.accountId
                            it[categoryId] = rec.categoryId
                            it[Transactions.title] = rec.title
                            it[Transactions.amount] = rec.amount
                            it[transactionType] = rec.transactionType.name
                            it[date] = currentDueDate.atStartOfDay()
                            it[Transactions.notes] = rec.notes
                            it[tags] = ""
                            it[isRecurring] = true
                            it[recurringTransactionId] = rec.id
                            it[createdAt] = now
                        }

                        // Atomic balance update — avoids race conditions
                        val balanceChange = when (rec.transactionType) {
                            TransactionType.EXPENSE -> rec.amount.negate()
                            TransactionType.INCOME -> rec.amount
                            TransactionType.TRANSFER -> java.math.BigDecimal.ZERO
                        }
                        if (balanceChange.compareTo(java.math.BigDecimal.ZERO) != 0) {
                            exec(
                                "UPDATE accounts SET balance = balance + ? WHERE id = ?",
                                listOf(
                                    org.jetbrains.exposed.sql.DecimalColumnType(15, 2) to balanceChange,
                                    org.jetbrains.exposed.sql.LongColumnType() to rec.accountId
                                )
                            )
                        }
                    }

                    // Advance to next due date
                    val nextDate = rec.frequencyType.calculateNextDate(currentDueDate, safeInterval)

                    // CRITICAL: persist the new nextDueDate immediately after each debit
                    // so re-running this never re-charges the same date
                    RecurringTransactions.update({ RecurringTransactions.id eq rec.id }) {
                        it[lastGeneratedDate] = today
                        it[nextDueDate] = nextDate
                    }

                    currentDueDate = nextDate
                }
            }
        }

        accountRepository.refresh()
        transactionRepository.refresh()
        refresh()
    }

    private fun ResultRow.toRecurringTransaction(): RecurringTransaction {
        return RecurringTransaction(
            id = this[RecurringTransactions.id],
            title = this[RecurringTransactions.title],
            amount = this[RecurringTransactions.amount],
            categoryId = this[RecurringTransactions.categoryId],
            categoryName = this.getOrNull(Categories.name),
            accountId = this[RecurringTransactions.accountId],
            accountName = this.getOrNull(Accounts.name),
            frequencyType = FrequencyType.valueOf(this[RecurringTransactions.frequencyType]),
            interval = this[RecurringTransactions.interval],
            startDate = this[RecurringTransactions.startDate],
            endDate = this[RecurringTransactions.endDate],
            lastGeneratedDate = this[RecurringTransactions.lastGeneratedDate],
            nextDueDate = this[RecurringTransactions.nextDueDate],
            transactionType = TransactionType.valueOf(this[RecurringTransactions.transactionType]),
            isActive = this[RecurringTransactions.isActive],
            notes = this[RecurringTransactions.notes],
            destinationAccountId = this[RecurringTransactions.destinationAccountId]
        )
    }
}
