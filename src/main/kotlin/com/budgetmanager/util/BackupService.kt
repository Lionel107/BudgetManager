package com.budgetmanager.util

import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Daily backup of the SQLite database. Keeps the last [maxBackups] copies.
 * Backups are stored in ~/.budgetmanager/backups/budget_manager_YYYY-MM-DD.db.
 *
 * Strategy: copy-and-rotate. SQLite is single-file so a plain file copy at startup
 * (when no concurrent writes are happening) is safe enough for this app.
 */
class BackupService(
    private val dbDir: File = File(System.getProperty("user.home"), ".budgetmanager"),
    private val maxBackups: Int = 7
) {

    private val backupDir: File = File(dbDir, "backups")
    private val dbFile: File = File(dbDir, "budget_manager.db")
    private val dateFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    /**
     * Run the daily backup if today's backup is missing.
     * Safe to call on every startup — no-op if already done today.
     */
    fun runDailyBackup(): Result<File> = runCatching {
        if (!dbFile.exists()) {
            error("Database file does not exist: ${dbFile.absolutePath}")
        }
        if (!backupDir.exists()) backupDir.mkdirs()

        val today = LocalDate.now()
        val target = File(backupDir, "budget_manager_${today.format(dateFmt)}.db")

        if (!target.exists()) {
            dbFile.copyTo(target, overwrite = false)
            rotateOldBackups()
        }
        target
    }

    /** Force a backup with a custom suffix (e.g. before a manual operation). */
    fun manualBackup(suffix: String = "manual"): Result<File> = runCatching {
        if (!dbFile.exists()) {
            error("Database file does not exist: ${dbFile.absolutePath}")
        }
        if (!backupDir.exists()) backupDir.mkdirs()

        val ts = LocalDate.now().format(dateFmt) + "_" + System.currentTimeMillis()
        val target = File(backupDir, "budget_manager_${suffix}_${ts}.db")
        dbFile.copyTo(target, overwrite = false)
        target
    }

    /** Restore the database from a given backup file. The current DB is itself backed up first. */
    fun restoreFromBackup(backupFile: File): Result<Unit> = runCatching {
        if (!backupFile.exists()) error("Backup file not found: ${backupFile.absolutePath}")
        // Save current DB as a "pre-restore" snapshot
        manualBackup(suffix = "pre_restore")
        backupFile.copyTo(dbFile, overwrite = true)
    }

    /** List all available backups, newest first. */
    fun listBackups(): List<File> {
        if (!backupDir.exists()) return emptyList()
        return backupDir.listFiles { f -> f.name.startsWith("budget_manager_") && f.name.endsWith(".db") }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
    }

    /** Delete backups beyond the retention limit. Daily backups are rotated; manual ones are kept. */
    private fun rotateOldBackups() {
        val daily = backupDir.listFiles { f ->
            f.name.matches(Regex("budget_manager_\\d{4}-\\d{2}-\\d{2}\\.db"))
        }?.sortedByDescending { it.lastModified() } ?: return

        if (daily.size > maxBackups) {
            daily.drop(maxBackups).forEach { runCatching { it.delete() } }
        }
    }
}
