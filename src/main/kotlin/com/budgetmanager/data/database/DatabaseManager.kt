package com.budgetmanager.data.database

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.File

class DatabaseManager {

    private lateinit var database: Database

    fun init() {
        val dbDir = File(System.getProperty("user.home"), ".budgetmanager")
        if (!dbDir.exists()) {
            dbDir.mkdirs()
        }
        val dbFile = File(dbDir, "budget_manager.db")
        val dbUrl = "jdbc:sqlite:${dbFile.absolutePath}"

        database = Database.connect(
            url = dbUrl,
            driver = "org.sqlite.JDBC"
        )

        // Apply versioned migrations (creates tables on first run, then evolves schema)
        Migrations.runAll()
    }

    fun getDatabase(): Database = database
}
