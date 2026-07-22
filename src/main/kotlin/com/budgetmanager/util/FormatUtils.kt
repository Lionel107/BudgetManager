package com.budgetmanager.util

import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale

object FormatUtils {

    private val frenchLocale = Locale.FRANCE
    private val currencyFormatter = NumberFormat.getCurrencyInstance(frenchLocale)
    private val numberFormatter = NumberFormat.getNumberInstance(frenchLocale).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }
    private val percentFormatter = NumberFormat.getPercentInstance(frenchLocale).apply {
        minimumFractionDigits = 1
        maximumFractionDigits = 1
    }

    fun formatCurrency(amount: BigDecimal, currencyCode: String = "EUR"): String {
        val formatter = NumberFormat.getCurrencyInstance(frenchLocale).apply {
            currency = java.util.Currency.getInstance(currencyCode)
        }
        return formatter.format(amount)
    }

    fun formatCurrency(amount: Double, currencyCode: String = "EUR"): String {
        return formatCurrency(BigDecimal.valueOf(amount), currencyCode)
    }

    fun formatNumber(amount: BigDecimal): String {
        return numberFormatter.format(amount)
    }

    fun formatNumber(amount: Double): String {
        return numberFormatter.format(amount)
    }

    fun formatPercent(value: Float): String {
        return percentFormatter.format(value.toDouble())
    }

    fun formatPercent(value: Double): String {
        return percentFormatter.format(value)
    }

    fun formatCompactCurrency(amount: BigDecimal, currencyCode: String = "EUR"): String {
        val absAmount = amount.abs()
        val sign = if (amount < BigDecimal.ZERO) "-" else ""
        val symbol = java.util.Currency.getInstance(currencyCode).symbol

        return when {
            absAmount >= BigDecimal("1000000") -> {
                val millions = absAmount.divide(BigDecimal("1000000"), 1, java.math.RoundingMode.HALF_UP)
                "$sign${numberFormatter.format(millions)}M $symbol"
            }
            absAmount >= BigDecimal("1000") -> {
                val thousands = absAmount.divide(BigDecimal("1000"), 1, java.math.RoundingMode.HALF_UP)
                "$sign${numberFormatter.format(thousands)}k $symbol"
            }
            else -> formatCurrency(amount, currencyCode)
        }
    }

    fun parseCurrencyInput(input: String): BigDecimal? {
        return try {
            val cleaned = input
                .replace("[^\\d,.-]".toRegex(), "")
                .replace(",", ".")
            BigDecimal(cleaned)
        } catch (e: Exception) {
            null
        }
    }
}
