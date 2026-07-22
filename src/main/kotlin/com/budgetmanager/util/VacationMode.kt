package com.budgetmanager.util

import com.budgetmanager.data.preferences.AppPreferences
import java.time.LocalDate

/**
 * Helpers around vacation mode.
 */
object VacationMode {

    /** True if the vacation mode is active on [date] (or today if null). */
    fun isActive(prefs: AppPreferences, date: LocalDate = LocalDate.now()): Boolean {
        if (!prefs.vacationModeEnabled) return false
        val start = parseOrNull(prefs.vacationStart) ?: return false
        val end = parseOrNull(prefs.vacationEnd) ?: return false
        return !date.isBefore(start) && !date.isAfter(end)
    }

    fun startDate(prefs: AppPreferences): LocalDate? = parseOrNull(prefs.vacationStart)
    fun endDate(prefs: AppPreferences): LocalDate? = parseOrNull(prefs.vacationEnd)

    fun daysRemaining(prefs: AppPreferences, today: LocalDate = LocalDate.now()): Long? {
        val end = endDate(prefs) ?: return null
        if (today.isAfter(end)) return 0
        return java.time.temporal.ChronoUnit.DAYS.between(today, end)
    }

    private fun parseOrNull(s: String): LocalDate? =
        runCatching { LocalDate.parse(s) }.getOrNull()
}
