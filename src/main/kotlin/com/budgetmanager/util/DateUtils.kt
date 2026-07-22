package com.budgetmanager.util

import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale

object DateUtils {

    private val frenchLocale = Locale.FRANCE

    private val fullDateFormatter = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", frenchLocale)
    private val shortDateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy", frenchLocale)
    private val monthYearFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", frenchLocale)
    private val dayMonthFormatter = DateTimeFormatter.ofPattern("dd MMM", frenchLocale)
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm", frenchLocale)
    private val dateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", frenchLocale)

    fun formatFullDate(date: LocalDate): String {
        return date.format(fullDateFormatter).replaceFirstChar { it.uppercaseChar() }
    }

    fun formatFullDate(dateTime: LocalDateTime): String {
        return formatFullDate(dateTime.toLocalDate())
    }

    fun formatShortDate(date: LocalDate): String {
        return date.format(shortDateFormatter)
    }

    fun formatShortDate(dateTime: LocalDateTime): String {
        return dateTime.format(shortDateFormatter)
    }

    fun formatMonthYear(date: LocalDate): String {
        return date.format(monthYearFormatter).replaceFirstChar { it.uppercaseChar() }
    }

    fun formatMonthYear(year: Int, month: Int): String {
        return formatMonthYear(LocalDate.of(year, month, 1))
    }

    fun formatDayMonth(date: LocalDate): String {
        return date.format(dayMonthFormatter)
    }

    fun formatTime(dateTime: LocalDateTime): String {
        return dateTime.format(timeFormatter)
    }

    fun formatDateTime(dateTime: LocalDateTime): String {
        return dateTime.format(dateTimeFormatter)
    }

    fun formatRelativeDate(date: LocalDate): String {
        val today = LocalDate.now()
        val yesterday = today.minusDays(1)
        val tomorrow = today.plusDays(1)

        return when (date) {
            today -> "Aujourd'hui"
            yesterday -> "Hier"
            tomorrow -> "Demain"
            else -> {
                val daysBetween = java.time.temporal.ChronoUnit.DAYS.between(date, today)
                when {
                    daysBetween in 2..6 -> "${date.dayOfWeek.getDisplayName(TextStyle.FULL, frenchLocale).replaceFirstChar { it.uppercaseChar() }}"
                    daysBetween in 7..13 -> "La semaine dernière"
                    date.year == today.year -> formatDayMonth(date)
                    else -> formatShortDate(date)
                }
            }
        }
    }

    fun formatRelativeDate(dateTime: LocalDateTime): String {
        return formatRelativeDate(dateTime.toLocalDate())
    }

    fun getStartOfMonth(year: Int, month: Int): LocalDateTime {
        return LocalDate.of(year, month, 1).atStartOfDay()
    }

    fun getEndOfMonth(year: Int, month: Int): LocalDateTime {
        return LocalDate.of(year, month, 1)
            .with(TemporalAdjusters.lastDayOfMonth())
            .atTime(23, 59, 59)
    }

    fun getCurrentMonthStart(): LocalDateTime {
        val now = LocalDate.now()
        return getStartOfMonth(now.year, now.monthValue)
    }

    fun getCurrentMonthEnd(): LocalDateTime {
        val now = LocalDate.now()
        return getEndOfMonth(now.year, now.monthValue)
    }

    fun getStartOfWeek(date: LocalDate = LocalDate.now()): LocalDate {
        return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    }

    fun getEndOfWeek(date: LocalDate = LocalDate.now()): LocalDate {
        return date.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
    }

    fun getStartOfYear(year: Int = LocalDate.now().year): LocalDateTime {
        return LocalDate.of(year, 1, 1).atStartOfDay()
    }

    fun getEndOfYear(year: Int = LocalDate.now().year): LocalDateTime {
        return LocalDate.of(year, 12, 31).atTime(23, 59, 59)
    }

    fun getMonthName(month: Int): String {
        return Month.of(month).getDisplayName(TextStyle.FULL, frenchLocale)
            .replaceFirstChar { it.uppercaseChar() }
    }

    fun getDayName(dayOfWeek: DayOfWeek): String {
        return dayOfWeek.getDisplayName(TextStyle.FULL, frenchLocale)
            .replaceFirstChar { it.uppercaseChar() }
    }

    fun getMonthsList(): List<String> {
        return (1..12).map { getMonthName(it) }
    }

    fun parseShortDate(dateString: String): LocalDate? {
        return try {
            LocalDate.parse(dateString, shortDateFormatter)
        } catch (e: Exception) {
            null
        }
    }
}
