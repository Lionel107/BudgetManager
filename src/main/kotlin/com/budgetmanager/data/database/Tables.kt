package com.budgetmanager.data.database

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.date
import org.jetbrains.exposed.sql.javatime.datetime

object Accounts : Table("accounts") {
    val id = long("id").autoIncrement()
    val name = varchar("name", 255)
    val balance = decimal("balance", 15, 2)
    val accountType = varchar("account_type", 50)
    val currencyCode = varchar("currency_code", 10).default("EUR")
    val isActive = bool("is_active").default(true)
    val displayOrder = integer("display_order").default(0)
    val color = varchar("color", 20).nullable()
    val iconName = varchar("icon_name", 100).nullable()
    val createdAt = datetime("created_at")
    // Investment-specific (only for AccountType.INVESTMENT)
    val initialCapital = decimal("initial_capital", 15, 2).nullable()
    val taxRate = float("tax_rate").default(0.30f) // default 30% on gains

    override val primaryKey = PrimaryKey(id)
}

object Categories : Table("categories") {
    val id = long("id").autoIncrement()
    val name = varchar("name", 255)
    val categoryType = varchar("category_type", 50)
    val parentId = long("parent_id").nullable()
    val color = varchar("color", 20)
    val iconName = varchar("icon_name", 100).nullable()
    val isDefault = bool("is_default").default(false)
    val displayOrder = integer("display_order").default(0)
    val isActive = bool("is_active").default(true) // soft-delete support

    override val primaryKey = PrimaryKey(id)
}

object Transactions : Table("transactions") {
    val id = long("id").autoIncrement()
    val accountId = long("account_id").references(Accounts.id)
    val categoryId = long("category_id").references(Categories.id).nullable()
    val title = varchar("title", 500)
    val amount = decimal("amount", 15, 2)
    val transactionType = varchar("transaction_type", 50)
    val date = datetime("date")
    val notes = text("notes").nullable()
    val tags = text("tags").default("") // Stored as comma-separated
    val isRecurring = bool("is_recurring").default(false)
    val recurringTransactionId = long("recurring_transaction_id").nullable()
    val createdAt = datetime("created_at")

    override val primaryKey = PrimaryKey(id)
}

object RecurringTransactions : Table("recurring_transactions") {
    val id = long("id").autoIncrement()
    val title = varchar("title", 500)
    val amount = decimal("amount", 15, 2)
    val categoryId = long("category_id").references(Categories.id).nullable()
    val accountId = long("account_id").references(Accounts.id)
    val frequencyType = varchar("frequency_type", 50)
    val interval = integer("repeat_interval").default(1)
    val startDate = date("start_date")
    val endDate = date("end_date").nullable()
    val lastGeneratedDate = date("last_generated_date").nullable()
    val nextDueDate = date("next_due_date")
    val transactionType = varchar("transaction_type", 50)
    val isActive = bool("is_active").default(true)
    val notes = text("notes").nullable()
    /** For TRANSFER recurrences only: the destination account. */
    val destinationAccountId = long("destination_account_id").nullable()

    override val primaryKey = PrimaryKey(id)
}

object Templates : Table("templates") {
    val id = long("id").autoIncrement()
    val name = varchar("name", 255)
    val defaultAmount = decimal("default_amount", 15, 2).nullable()
    val categoryId = long("category_id").references(Categories.id).nullable()
    val transactionType = varchar("transaction_type", 50)
    val iconName = varchar("icon_name", 100).nullable()
    val color = varchar("color", 20).nullable()
    val displayOrder = integer("display_order").default(0)
    val usageCount = integer("usage_count").default(0)

    override val primaryKey = PrimaryKey(id)
}

object Tags : Table("tags") {
    val id = long("id").autoIncrement()
    val name = varchar("name", 100)
    val color = varchar("color", 20).nullable()
    val usageCount = integer("usage_count").default(0)
    val createdAt = long("created_at").default(System.currentTimeMillis())

    override val primaryKey = PrimaryKey(id)
}

object TransactionTags : Table("transaction_tags") {
    val transactionId = long("transaction_id").references(Transactions.id)
    val tagId = long("tag_id").references(Tags.id)

    override val primaryKey = PrimaryKey(transactionId, tagId)
}

object ExchangeRates : Table("exchange_rates") {
    val id = long("id").autoIncrement()
    /** ISO 4217 codes (e.g. EUR, USD). */
    val fromCurrency = varchar("from_currency", 10)
    val toCurrency = varchar("to_currency", 10)
    /** 1 [fromCurrency] = rate [toCurrency]. E.g. 1 USD = 0.92 EUR → rate = 0.92. */
    val rate = decimal("rate", 18, 8)
    val updatedAt = long("updated_at").default(System.currentTimeMillis())

    override val primaryKey = PrimaryKey(id)
}

object Challenges : Table("challenges") {
    val id = long("id").autoIncrement()
    val title = varchar("title", 255)
    val description = text("description").nullable()
    /** Tracking type: SPEND_LIMIT (don't exceed X in category) or SAVE_AMOUNT (save X by end). */
    val type = varchar("type", 30)
    val targetAmount = decimal("target_amount", 15, 2)
    val categoryId = long("category_id").nullable()
    val startDate = date("start_date")
    val endDate = date("end_date")
    val isCompleted = bool("is_completed").default(false)
    val createdAt = long("created_at").default(System.currentTimeMillis())

    override val primaryKey = PrimaryKey(id)
}

object TransactionSplits : Table("transaction_splits") {
    val id = long("id").autoIncrement()
    val transactionId = long("transaction_id").references(Transactions.id)
    val categoryId = long("category_id").references(Categories.id).nullable()
    val amount = decimal("amount", 15, 2)
    val notes = text("notes").nullable()

    override val primaryKey = PrimaryKey(id)
}

object Budgets : Table("budgets") {
    val id = long("id").autoIncrement()
    val categoryId = long("category_id").references(Categories.id)
    val periodType = varchar("period_type", 50)
    val limit = decimal("budget_limit", 15, 2)
    val alertThreshold = float("alert_threshold").default(0.9f)
    val warningThreshold = float("warning_threshold").default(0.7f)
    val startDate = date("start_date").nullable()
    val endDate = date("end_date").nullable()

    override val primaryKey = PrimaryKey(id)
}
