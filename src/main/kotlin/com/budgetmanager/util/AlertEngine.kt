package com.budgetmanager.util

import com.budgetmanager.domain.model.BudgetWithStatus
import com.budgetmanager.domain.model.ObjectiveProgress
import com.budgetmanager.domain.model.ObjectiveType
import com.budgetmanager.domain.model.Transaction
import com.budgetmanager.domain.model.TransactionType
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Locale

/**
 * Moteur d'alertes **déterministes** (zéro appel IA) — brique 3 de l'accompagnant.
 *
 * Se concentre sur ce que l'[AdviceEngine] mensuel ne couvre pas :
 *  1. **Budget dépassé, raisonnement ANNUEL** : on n'alarme pas sur un pic isolé si le
 *     cumul de l'année reste sous le budget prorata (limite mensuelle × mois écoulés).
 *  2. **Proche de la limite (~80 %)** d'un budget mensuel.
 *  3. **Objectif d'épargne en retard** sur le rythme nécessaire (via [ObjectiveProgress]).
 *  4. **Dépense inhabituelle** : une transaction récente très au-dessus de la normale
 *     de sa catégorie.
 *
 * Ton volontairement **bienveillant**, jamais alarmiste. Renvoie des [FinancialAdvice]
 * pour être versé tel quel dans le `NotificationCenter` (qui ne notifie que WARNING/CRITICAL).
 */
class AlertEngine {

    /** Seuils réglés pour rester utiles sans spammer. */
    private companion object {
        const val NEAR_LIMIT_RATIO = 0.80
        const val UNUSUAL_FACTOR = 2.5           // × la moyenne de la catégorie
        val UNUSUAL_FLOOR: BigDecimal = BigDecimal(50)   // ignore les petits montants
        const val UNUSUAL_MIN_HISTORY = 5        // besoin d'un historique suffisant
        const val UNUSUAL_WINDOW_DAYS = 7L       // « récent »
    }

    fun analyze(
        objectiveProgress: List<ObjectiveProgress>,
        budgets: List<BudgetWithStatus>,
        transactions: List<Transaction>
    ): List<FinancialAdvice> {
        val alerts = mutableListOf<FinancialAdvice>()
        val today = LocalDate.now()
        val monthsElapsed = today.monthValue                 // janvier=1 … décembre=12
        val yearStart = today.withDayOfYear(1)

        // Cumul annuel des dépenses par catégorie (année en cours)
        val annualSpendByCategory: Map<Long, BigDecimal> = transactions
            .filter {
                it.transactionType == TransactionType.EXPENSE &&
                    it.categoryId != null &&
                    !it.date.toLocalDate().isBefore(yearStart)
            }
            .groupBy { it.categoryId!! }
            .mapValues { (_, txs) -> txs.fold(BigDecimal.ZERO) { a, t -> a.add(t.amount) } }

        // ===== 1 + 2 : budgets (annuel-aware + proche de la limite) =====
        for (b in budgets) {
            val limit = b.budget.limit
            if (limit <= BigDecimal.ZERO) continue

            if (b.spent > limit) {
                // Dépassement ce mois : est-ce un vrai dérapage annuel ou un pic isolé ?
                val annualLimit = limit.multiply(BigDecimal(monthsElapsed))
                val annualSpent = annualSpendByCategory[b.budget.categoryId] ?: b.spent
                if (annualSpent > annualLimit) {
                    val over = annualSpent.subtract(annualLimit)
                    alerts += FinancialAdvice(
                        title = "Budget « ${b.budget.categoryName} » dépassé sur l'année",
                        message = "Sur l'année, tu es à ${eur(annualSpent)} pour un budget prorata de " +
                            "${eur(annualLimit)} (${eur(over)} de trop). Ce n'est pas qu'un pic : " +
                            "ajuste la limite si elle est irréaliste, sinon lève le pied sur cette catégorie.",
                        level = AdviceLevel.CRITICAL,
                        category = AdviceCategory.BUDGET
                    )
                }
                // sinon : pic isolé, l'année reste saine → pas d'alarme (bienveillance)
            } else {
                val ratio = b.spent.divide(limit, 4, RoundingMode.HALF_UP).toDouble()
                if (ratio >= NEAR_LIMIT_RATIO) {
                    alerts += FinancialAdvice(
                        title = "« ${b.budget.categoryName} » proche de la limite",
                        message = "Tu es à ${(ratio * 100).toInt()}% du plafond ${eur(limit)} " +
                            "(${eur(b.spent)} dépensés). Garde un œil dessus pour la fin du mois.",
                        level = AdviceLevel.WARNING,
                        category = AdviceCategory.BUDGET
                    )
                }
            }
        }

        // ===== 3 : objectif d'épargne en retard =====
        for (p in objectiveProgress) {
            if (p.objective.type == ObjectiveType.SAVINGS && !p.onTrack) {
                val need = p.requiredMonthly
                val gapMsg = if (need != null) {
                    val gap = need.subtract(p.currentMonthly).coerceAtLeast(BigDecimal.ZERO)
                    "Il faudrait ~${eur(need)}/mois et tu es à ~${eur(p.currentMonthly)} " +
                        "(manque ~${eur(gap)}/mois)."
                } else {
                    "Ton rythme d'épargne actuel ne suffit pas à tenir l'objectif."
                }
                alerts += FinancialAdvice(
                    title = "Objectif « ${p.objective.title} » : rythme à rattraper",
                    message = "$gapMsg Cible une dépense non essentielle à réduire pour combler l'écart.",
                    level = AdviceLevel.WARNING,
                    category = AdviceCategory.SAVINGS
                )
            }
        }

        // ===== 4 : dépense inhabituelle =====
        val expenses = transactions.filter { it.transactionType == TransactionType.EXPENSE }
        val meanByCategory: Map<Long, BigDecimal> = expenses
            .filter { it.categoryId != null }
            .groupBy { it.categoryId!! }
            .filterValues { it.size >= UNUSUAL_MIN_HISTORY }
            .mapValues { (_, txs) ->
                txs.fold(BigDecimal.ZERO) { a, t -> a.add(t.amount) }
                    .divide(BigDecimal(txs.size), 2, RoundingMode.HALF_UP)
            }
        val windowStart = LocalDateTime.now().minusDays(UNUSUAL_WINDOW_DAYS)
        expenses
            .asSequence()
            .filter { it.date.isAfter(windowStart) && it.categoryId != null && it.amount >= UNUSUAL_FLOOR }
            .mapNotNull { tx ->
                val mean = meanByCategory[tx.categoryId] ?: return@mapNotNull null
                val threshold = mean.multiply(BigDecimal(UNUSUAL_FACTOR))
                if (tx.amount > threshold) tx to mean else null
            }
            .sortedByDescending { it.first.amount }
            .take(3)   // au plus 3 pour éviter le spam
            .forEach { (tx, mean) ->
                val label = tx.categoryName ?: "cette catégorie"
                alerts += FinancialAdvice(
                    title = "Dépense inhabituelle : ${eur(tx.amount)} (${tx.title})",
                    message = "« ${tx.title} » ($label) est bien au-dessus de ta moyenne habituelle " +
                        "(~${eur(mean)}). Si c'est normal (achat ponctuel), ignore ; sinon vérifie.",
                    level = AdviceLevel.WARNING,
                    category = AdviceCategory.CASH_FLOW
                )
            }

        return alerts.sortedBy {
            when (it.level) {
                AdviceLevel.CRITICAL -> 0
                AdviceLevel.WARNING -> 1
                AdviceLevel.INFO -> 2
                AdviceLevel.GOOD -> 3
            }
        }
    }

    private fun eur(v: BigDecimal): String = String.format(Locale.FRANCE, "%,.0f €", v)
}
