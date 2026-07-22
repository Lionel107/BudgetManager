package com.budgetmanager.presentation.components

import androidx.compose.runtime.mutableStateListOf
import com.budgetmanager.util.AdviceLevel
import com.budgetmanager.util.FinancialAdvice
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicLong

private val idGenerator = AtomicLong(System.currentTimeMillis())

data class AppNotification(
    val id: Long = idGenerator.incrementAndGet(),
    val title: String,
    val message: String,
    val level: AdviceLevel,
    val time: LocalDateTime = LocalDateTime.now(),
    val seen: Boolean = false
)

/**
 * In-memory notification center. Populated from advice engine, budget alerts,
 * recurring transactions ready to fire, etc.
 *
 * Persistence intentionally skipped — notifications are derived from current state,
 * so they re-populate at each launch.
 */
object NotificationCenter {
    val notifications = mutableStateListOf<AppNotification>()

    /**
     * Titles the user has already seen or dismissed during this session.
     * Prevents the background scheduler from re-creating the same alert over and over.
     * Reset on app restart so genuinely new occurrences will surface again.
     */
    private val acknowledgedTitles = mutableSetOf<String>()

    fun add(notification: AppNotification) {
        val key = notification.title.trim().lowercase()
        // 1. Skip if user has already seen/dismissed this exact title in this session
        if (key in acknowledgedTitles) return
        // 2. Skip if an identical (unseen) notification is already in the list
        if (notifications.any { it.title.trim().lowercase() == key }) return

        notifications.add(0, notification)
        // Keep most recent 50
        if (notifications.size > 50) notifications.removeAt(notifications.size - 1)
    }

    fun addFromAdvice(advice: FinancialAdvice) {
        // Only critical/warning advice becomes a notification (avoid spam)
        if (advice.level == AdviceLevel.CRITICAL || advice.level == AdviceLevel.WARNING) {
            add(AppNotification(
                title = advice.title,
                message = advice.message,
                level = advice.level
            ))
        }
    }

    /** Mark every current notification as seen AND remember their titles so they won't reappear. */
    fun markAllSeen() {
        for (i in notifications.indices) {
            val n = notifications[i]
            acknowledgedTitles += n.title.trim().lowercase()
            notifications[i] = n.copy(seen = true)
        }
    }

    fun dismiss(id: Long) {
        val target = notifications.firstOrNull { it.id == id } ?: return
        acknowledgedTitles += target.title.trim().lowercase()
        notifications.removeAll { it.id == id }
    }

    fun clear() {
        // Acknowledging all current notifications too so they don't pop right back
        notifications.forEach { acknowledgedTitles += it.title.trim().lowercase() }
        notifications.clear()
    }

    val unreadCount: Int get() = notifications.count { !it.seen }
}
