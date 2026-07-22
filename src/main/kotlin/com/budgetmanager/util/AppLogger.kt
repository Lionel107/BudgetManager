package com.budgetmanager.util

import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Simple file-based logger with daily rotation.
 * Writes to ~/.budgetmanager/logs/app-YYYY-MM-DD.log.
 *
 * Thread-safe via a queue + single writer thread.
 */
object AppLogger {

    enum class Level { DEBUG, INFO, WARN, ERROR }

    private val logDir: File by lazy {
        val home = File(System.getProperty("user.home"), ".budgetmanager")
        val dir = File(home, "logs")
        if (!dir.exists()) dir.mkdirs()
        dir
    }

    private val tsFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
    private val dateFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val queue = ConcurrentLinkedQueue<String>()
    @Volatile private var minLevel: Level = Level.INFO

    init {
        // Background flush thread
        Thread({
            while (true) {
                try {
                    val line = queue.poll()
                    if (line != null) flushLine(line)
                    else Thread.sleep(150)
                } catch (_: InterruptedException) {
                    break
                } catch (_: Exception) { /* swallow — never crash the logger */ }
            }
        }, "AppLogger-Writer").apply {
            isDaemon = true
            start()
        }
        // Rotate / purge old logs (keep last 14 days)
        runCatching { purgeOldLogs(14) }
    }

    fun setMinLevel(level: Level) { minLevel = level }

    fun debug(tag: String, message: String) = log(Level.DEBUG, tag, message)
    fun info(tag: String, message: String) = log(Level.INFO, tag, message)
    fun warn(tag: String, message: String, throwable: Throwable? = null) = log(Level.WARN, tag, message, throwable)
    fun error(tag: String, message: String, throwable: Throwable? = null) = log(Level.ERROR, tag, message, throwable)

    fun log(level: Level, tag: String, message: String, throwable: Throwable? = null) {
        if (level.ordinal < minLevel.ordinal) return
        val ts = LocalDateTime.now().format(tsFmt)
        val sb = StringBuilder()
        sb.append("[").append(ts).append("] ")
            .append(level.name.padEnd(5)).append(" ")
            .append("[").append(tag).append("] ")
            .append(message)
        if (throwable != null) {
            val sw = StringWriter()
            throwable.printStackTrace(PrintWriter(sw))
            sb.append("\n").append(sw.toString())
        }
        queue.offer(sb.toString())
    }

    private fun flushLine(line: String) {
        val today = LocalDateTime.now().toLocalDate().format(dateFmt)
        val file = File(logDir, "app-$today.log")
        file.appendText(line + "\n", Charsets.UTF_8)
    }

    private fun purgeOldLogs(keepDays: Int) {
        val cutoff = System.currentTimeMillis() - keepDays.toLong() * 24 * 60 * 60 * 1000
        logDir.listFiles { f -> f.name.startsWith("app-") && f.name.endsWith(".log") }
            ?.filter { it.lastModified() < cutoff }
            ?.forEach { runCatching { it.delete() } }
    }
}
