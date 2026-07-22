package com.budgetmanager.data.preferences

import java.math.BigDecimal
import java.util.prefs.Preferences

class AppPreferences {

    private val prefs: Preferences = Preferences.userNodeForPackage(AppPreferences::class.java)

    companion object {
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_LANGUAGE = "language"
        private const val KEY_CURRENCY_CODE = "currency_code"
        private const val KEY_BUDGET_ALERT_THRESHOLD = "budget_alert_threshold"
        private const val KEY_SAVINGS_GOAL = "savings_goal"
        private const val KEY_DENSITY = "ui_density"
        private const val KEY_FONT_SCALE = "font_scale"
        private const val KEY_GEMINI_API_KEY = "gemini_api_key"

        private const val DEFAULT_THEME_MODE = "light"
        private const val DEFAULT_LANGUAGE = "fr"
        private const val DEFAULT_CURRENCY_CODE = "EUR"
        private const val DEFAULT_BUDGET_ALERT_THRESHOLD = 0.9f
        private const val DEFAULT_SAVINGS_GOAL = "0"
        private const val DEFAULT_DENSITY = "normal" // compact|normal|large
        private const val DEFAULT_FONT_SCALE = 1.0f
    }

    var themeMode: String
        get() = prefs.get(KEY_THEME_MODE, DEFAULT_THEME_MODE)
        set(value) = prefs.put(KEY_THEME_MODE, value)

    var language: String
        get() = prefs.get(KEY_LANGUAGE, DEFAULT_LANGUAGE)
        set(value) = prefs.put(KEY_LANGUAGE, value)

    var currencyCode: String
        get() = prefs.get(KEY_CURRENCY_CODE, DEFAULT_CURRENCY_CODE)
        set(value) = prefs.put(KEY_CURRENCY_CODE, value)

    var budgetAlertThreshold: Float
        get() = prefs.getFloat(KEY_BUDGET_ALERT_THRESHOLD, DEFAULT_BUDGET_ALERT_THRESHOLD)
        set(value) = prefs.putFloat(KEY_BUDGET_ALERT_THRESHOLD, value)

    var savingsGoal: BigDecimal
        get() = BigDecimal(prefs.get(KEY_SAVINGS_GOAL, DEFAULT_SAVINGS_GOAL))
        set(value) = prefs.put(KEY_SAVINGS_GOAL, value.toPlainString())

    /** UI density: "compact", "normal", or "large". */
    var density: String
        get() = prefs.get(KEY_DENSITY, DEFAULT_DENSITY)
        set(value) = prefs.put(KEY_DENSITY, value)

    /** Font scale multiplier (typically 0.85, 1.0, 1.15, 1.30). */
    var fontScale: Float
        get() = prefs.getFloat(KEY_FONT_SCALE, DEFAULT_FONT_SCALE)
        set(value) = prefs.putFloat(KEY_FONT_SCALE, value)

    /** Optional Gemini API key for advanced AI advice. Empty = use rule engine only. */
    var geminiApiKey: String
        get() = prefs.get(KEY_GEMINI_API_KEY, "")
        set(value) = prefs.put(KEY_GEMINI_API_KEY, value)

    /** Auto-switch to dark theme between [eveningStartHour] and 7am. */
    var autoEveningMode: Boolean
        get() = prefs.getBoolean("auto_evening_mode", false)
        set(value) = prefs.putBoolean("auto_evening_mode", value)

    var eveningStartHour: Int
        get() = prefs.getInt("evening_start_hour", 21)
        set(value) = prefs.putInt("evening_start_hour", value)

    // ===== Vacation mode =====
    var vacationModeEnabled: Boolean
        get() = prefs.getBoolean("vacation_enabled", false)
        set(value) = prefs.putBoolean("vacation_enabled", value)

    /** ISO date string YYYY-MM-DD, empty when not set. */
    var vacationStart: String
        get() = prefs.get("vacation_start", "")
        set(value) = prefs.put("vacation_start", value)

    var vacationEnd: String
        get() = prefs.get("vacation_end", "")
        set(value) = prefs.put("vacation_end", value)

    /** Total budget for the vacation period in EUR; 0 means no budget. */
    var vacationBudget: BigDecimal
        get() = BigDecimal(prefs.get("vacation_budget", "0"))
        set(value) = prefs.put("vacation_budget", value.toPlainString())

    /** Tag to auto-apply to transactions made during the vacation period. */
    var vacationTag: String
        get() = prefs.get("vacation_tag", "vacances")
        set(value) = prefs.put("vacation_tag", value)

    /** Year-month (e.g. "2026-04") when ALL_BUDGETS_SAFE celebration was last shown. */
    var lastAllBudgetsSafeYearMonth: String
        get() = prefs.get("last_all_budgets_safe_ym", "")
        set(value) = prefs.put("last_all_budgets_safe_ym", value)

    /** Year-month when BUDGET_ALERT animation was last shown for a given category. */
    fun lastBudgetAlertFor(category: String): String =
        prefs.get("last_budget_alert_${category.lowercase()}", "")

    fun setLastBudgetAlertFor(category: String, ym: String) =
        prefs.put("last_budget_alert_${category.lowercase()}", ym)

    /** Cached Gemini advice (JSON-encoded list) per year-month. */
    var cachedGeminiAdvice: String
        get() = prefs.get("cached_gemini_advice", "")
        set(value) = prefs.put("cached_gemini_advice", value)

    /** Year-month of the cached Gemini advice. */
    var cachedGeminiAdviceYearMonth: String
        get() = prefs.get("cached_gemini_advice_ym", "")
        set(value) = prefs.put("cached_gemini_advice_ym", value)
}
