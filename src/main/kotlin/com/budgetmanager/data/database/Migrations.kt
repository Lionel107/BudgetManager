package com.budgetmanager.data.database

import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

/**
 * Schema version tracking. Increment CURRENT_VERSION when adding a migration.
 * Migrations are applied in order, only those above the stored version.
 */
object SchemaVersion : Table("schema_version") {
    val version = integer("version")
    val appliedAt = long("applied_at")
    override val primaryKey = PrimaryKey(version)
}

object Migrations {

    const val CURRENT_VERSION = 8

    /**
     * Run all needed migrations to bring the DB to CURRENT_VERSION.
     * Safe to call on every startup — only runs missing migrations.
     */
    fun runAll() {
        transaction {
            // Ensure SchemaVersion table exists
            SchemaUtils.create(SchemaVersion)

            val applied = SchemaVersion.selectAll().map { it[SchemaVersion.version] }.toSet()

            for (v in 1..CURRENT_VERSION) {
                if (v !in applied) {
                    applyMigration(v)
                    SchemaVersion.insert {
                        it[version] = v
                        it[appliedAt] = System.currentTimeMillis()
                    }
                }
            }
        }
    }

    private fun applyMigration(version: Int) {
        when (version) {
            1 -> migrationV1_initialSchema()
            2 -> migrationV2_addCategoryIsActive()
            3 -> migrationV3_normalizedTags()
            4 -> migrationV4_transactionSplits()
            5 -> migrationV5_investmentFields()
            6 -> migrationV6_recurringTransferDestination()
            7 -> migrationV7_challenges()
            8 -> migrationV8_exchangeRates()
        }
    }

    /**
     * V1: Initial schema. SchemaUtils.create() is idempotent — only creates missing tables.
     */
    private fun migrationV1_initialSchema() {
        SchemaUtils.create(
            Accounts, Categories, Transactions, RecurringTransactions, Templates, Budgets
        )
    }

    /**
     * V2: Add is_active column to Categories for soft-delete support.
     */
    private fun migrationV2_addCategoryIsActive() {
        val tx = org.jetbrains.exposed.sql.transactions.TransactionManager.current()
        if (!columnExists("categories", "is_active")) {
            tx.exec("ALTER TABLE categories ADD COLUMN is_active INTEGER NOT NULL DEFAULT 1")
        }
    }

    /**
     * V3: Normalized Tags. Creates Tags + TransactionTags tables and migrates
     * the legacy `transactions.tags` CSV column into proper relations.
     * The legacy column is kept as-is for safety; new code uses the normalized form.
     */
    private fun migrationV3_normalizedTags() {
        val tx = org.jetbrains.exposed.sql.transactions.TransactionManager.current()

        // Create new tables
        SchemaUtils.create(Tags, TransactionTags)

        // Migrate legacy CSV data: read each transaction's tags column and split
        val tagNameToId = mutableMapOf<String, Long>()
        val pendingPairs = mutableListOf<Pair<Long, List<String>>>()

        // Use raw JDBC for the read — exec() with a callback handles the ResultSet.
        tx.exec("SELECT id, tags FROM transactions WHERE tags IS NOT NULL AND tags != ''") { rs ->
            while (rs.next()) {
                val txnId = rs.getLong(1)
                val csv = rs.getString(2) ?: continue
                val tagNames = csv.split(",").map { it.trim() }.filter { it.isNotBlank() }
                if (tagNames.isNotEmpty()) {
                    pendingPairs += txnId to tagNames
                }
            }
        }

        // Insert distinct tags + relations
        for ((txnId, tagNames) in pendingPairs) {
            for (raw in tagNames) {
                val name = raw.lowercase()
                val tagId = tagNameToId.getOrPut(name) {
                    Tags.insert {
                        it[Tags.name] = name
                        it[Tags.usageCount] = 0
                        it[Tags.createdAt] = System.currentTimeMillis()
                    } get Tags.id
                }
                // Avoid duplicate (transaction_id, tag_id) — should be unique by primary key
                runCatching {
                    TransactionTags.insert {
                        it[TransactionTags.transactionId] = txnId
                        it[TransactionTags.tagId] = tagId
                    }
                }
            }
        }

        // Update usage_count to reflect actual relations
        for ((_, tagId) in tagNameToId) {
            tx.exec(
                "UPDATE tags SET usage_count = (SELECT COUNT(*) FROM transaction_tags WHERE tag_id = $tagId) WHERE id = $tagId"
            )
        }
    }

    /**
     * V4: Transaction Splits — split a single transaction across multiple categories.
     * No data migration needed: existing transactions stay un-split.
     */
    private fun migrationV4_transactionSplits() {
        SchemaUtils.create(TransactionSplits)
    }

    /**
     * V5: Investment account support — adds initial_capital and tax_rate columns to accounts.
     * Existing accounts get NULL initial_capital (so the % gain widgets just don't render).
     */
    private fun migrationV5_investmentFields() {
        val tx = org.jetbrains.exposed.sql.transactions.TransactionManager.current()
        if (!columnExists("accounts", "initial_capital")) {
            tx.exec("ALTER TABLE accounts ADD COLUMN initial_capital DECIMAL(15, 2)")
        }
        if (!columnExists("accounts", "tax_rate")) {
            tx.exec("ALTER TABLE accounts ADD COLUMN tax_rate REAL NOT NULL DEFAULT 0.30")
        }
    }

    /**
     * V6: Recurring transfers — adds destination_account_id column for TRANSFER-type recurrences.
     */
    private fun migrationV6_recurringTransferDestination() {
        val tx = org.jetbrains.exposed.sql.transactions.TransactionManager.current()
        if (!columnExists("recurring_transactions", "destination_account_id")) {
            tx.exec("ALTER TABLE recurring_transactions ADD COLUMN destination_account_id INTEGER")
        }
    }

    /**
     * V7: Challenges — user-created spending or savings challenges.
     */
    private fun migrationV7_challenges() {
        SchemaUtils.create(Challenges)
    }

    /**
     * V8: Exchange rates table for multi-currency support.
     */
    private fun migrationV8_exchangeRates() {
        SchemaUtils.create(ExchangeRates)
    }

    private fun columnExists(tableName: String, columnName: String): Boolean {
        val tx = org.jetbrains.exposed.sql.transactions.TransactionManager.current()
        var found = false
        tx.exec("PRAGMA table_info($tableName)") { rs ->
            while (rs.next()) {
                val name = rs.getString("name")
                if (name.equals(columnName, ignoreCase = true)) {
                    found = true
                    break
                }
            }
        }
        return found
    }
}
