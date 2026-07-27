package com.budgetmanager.data.repository

import com.budgetmanager.domain.model.ObjectiveProgress
import com.budgetmanager.domain.model.ObjectiveType
import kotlinx.coroutines.flow.first
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.util.Locale

/**
 * Calculs DÉTERMINISTES (sans IA) pour l'accompagnement : progression des objectifs.
 * (Les alertes automatiques déterministes viendront s'ajouter ici / dans un moteur dédié.)
 */
class AnalysisRepository(
    private val txRepo: TransactionRepository,
    private val objRepo: ObjectiveRepository
) {

    suspend fun objectiveProgress(): List<ObjectiveProgress> {
        val objectives = objRepo.getAll().first()
        if (objectives.isEmpty()) return emptyList()

        val now = LocalDate.now()
        val endNow = now.atTime(23, 59, 59)

        // Rythme d'épargne = moyenne (revenus - dépenses) sur les 3 derniers mois
        val start3 = now.withDayOfMonth(1).minusMonths(2).atStartOfDay()
        val income3 = txRepo.getTotalIncome(start3, endNow)
        val expenses3 = txRepo.getTotalExpenses(start3, endNow)
        val netMonthly = (income3 - expenses3).divide(BigDecimal(3), 2, RoundingMode.HALF_UP)

        // Dépenses du mois en cours (par catégorie + total) pour les plafonds
        val monthStart = now.withDayOfMonth(1).atStartOfDay()
        val catSpend = txRepo.getCategorySpending(monthStart, endNow).first()
            .associate { it.categoryId to it.totalSpent }
        val monthExpenses = txRepo.getTotalExpenses(monthStart, endNow)

        return objectives.map { obj ->
            when (obj.type) {
                ObjectiveType.SAVINGS -> {
                    val months = monthsUntil(obj.targetDate)
                    val required = obj.targetAmount.divide(BigDecimal(months), 2, RoundingMode.HALF_UP)
                    val ratio = if (required > BigDecimal.ZERO) netMonthly.toFloat() / required.toFloat() else 1f
                    val onTrack = netMonthly >= required
                    ObjectiveProgress(
                        objective = obj,
                        requiredMonthly = required,
                        currentMonthly = netMonthly,
                        ratio = ratio.coerceAtLeast(0f),
                        onTrack = onTrack,
                        label = "Épargne actuelle ~${eur(netMonthly)}/mois · besoin ~${eur(required)}/mois"
                    )
                }
                ObjectiveType.SPENDING_LIMIT -> {
                    val spent = if (obj.categoryId != null) (catSpend[obj.categoryId] ?: BigDecimal.ZERO) else monthExpenses
                    val cap = obj.targetAmount
                    val ratio = if (cap > BigDecimal.ZERO) spent.toFloat() / cap.toFloat() else 0f
                    val onTrack = spent <= cap
                    ObjectiveProgress(
                        objective = obj,
                        requiredMonthly = null,
                        currentMonthly = spent,
                        ratio = ratio,
                        onTrack = onTrack,
                        label = "Dépensé ce mois ${eur(spent)} / plafond ${eur(cap)}"
                    )
                }
            }
        }
    }

    private fun monthsUntil(date: LocalDate?): Int {
        if (date == null) return 12
        val now = LocalDate.now()
        val m = (date.year - now.year) * 12 + (date.monthValue - now.monthValue)
        return maxOf(1, m)
    }

    private fun eur(v: BigDecimal): String = String.format(Locale.FRANCE, "%,.0f €", v)
}
