package com.budgetmanager.data.repository

import com.budgetmanager.data.preferences.AppPreferences
import com.budgetmanager.data.remote.SupabaseClientProvider
import com.budgetmanager.domain.model.Budget
import com.budgetmanager.domain.model.BudgetPeriodType
import com.budgetmanager.domain.model.Category
import com.budgetmanager.domain.model.TransactionType
import io.github.jan.supabase.functions.functions
import io.ktor.client.call.body
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import java.math.BigDecimal
import java.math.RoundingMode

// ===== DTO d'échange avec l'Edge Function budget-planner =====

@Serializable
data class PlanLine(
    val category: String,
    val monthlyAmount: Double,
    val rationale: String = "",
    val essential: Boolean = true
)

@Serializable
data class PlanResponse(
    val monthlyIncome: Double = 0.0,
    val summary: String = "",
    val plan: List<PlanLine> = emptyList(),
    val reply: String = "",
    val error: String? = null
)

/** Un tour de conversation avec le planificateur (role = "user" | "assistant"). */
@Serializable
data class PlanTurn(val role: String, val text: String)

@Serializable
private data class PlanLineReq(val category: String, val monthlyAmount: Double)

@Serializable
private data class PlanRequest(
    val geminiKey: String,
    val remarks: String? = null,
    val message: String? = null,
    val history: List<PlanTurn> = emptyList(),
    val currentPlan: List<PlanLineReq>? = null
)

/**
 * Planificateur de budget IA (Phase 5 - D). Propose un plan via l'Edge Function
 * `budget-planner`, permet de l'ajuster par remarques, et de l'APPLIQUER (crée /
 * met à jour les budgets mensuels réels, ensuite modifiables à la main).
 */
class PlannerRepository(
    private val provider: SupabaseClientProvider,
    private val prefs: AppPreferences,
    private val categoryRepository: CategoryRepository,
    private val budgetRepository: BudgetRepository
) {

    /** Demande un plan (remarks/currentPlan pour réadapter un plan existant). */
    suspend fun proposePlan(remarks: String? = null, currentPlan: List<PlanLine>? = null): PlanResponse {
        val response = provider.client.functions.invoke(
            function = "budget-planner",
            body = PlanRequest(
                geminiKey = prefs.geminiApiKey,
                remarks = remarks?.takeIf { it.isNotBlank() },
                currentPlan = currentPlan?.map { PlanLineReq(it.category, it.monthlyAmount) }
            )
        )
        return response.body<PlanResponse>()
    }

    /**
     * Tour de conversation : l'utilisateur envoie un [message], l'assistant renvoie
     * un plan ajusté + une réponse rédigée (champ `reply`). [history] = tours précédents.
     */
    suspend fun chatRefine(
        message: String,
        history: List<PlanTurn>,
        currentPlan: List<PlanLine>?
    ): PlanResponse {
        val response = provider.client.functions.invoke(
            function = "budget-planner",
            body = PlanRequest(
                geminiKey = prefs.geminiApiKey,
                message = message,
                history = history,
                currentPlan = currentPlan?.map { PlanLineReq(it.category, it.monthlyAmount) }
            )
        )
        return response.body<PlanResponse>()
    }

    /**
     * Applique le plan : pour chaque ligne, crée (ou met à jour si elle existe déjà)
     * un budget MENSUEL sur la catégorie correspondante. Les catégories inconnues
     * (proposées par l'IA ou ajoutées à la main) sont CRÉÉES automatiquement.
     * Retourne le nombre de budgets appliqués.
     */
    suspend fun applyPlan(plan: List<PlanLine>): Int {
        val categories = categoryRepository.getAllCategories().first()
        val nameToId = categories.associate { it.name to it.id }.toMutableMap()
        val existingByCategory = budgetRepository.getAllBudgets().first().associateBy { it.categoryId }

        var applied = 0
        var newColorIdx = 0
        for (line in plan) {
            if (line.category.isBlank() || line.monthlyAmount <= 0) continue

            // Catégorie inconnue -> on la crée (dépense, couleur par défaut)
            var catId = nameToId[line.category]
            if (catId == null) {
                catId = categoryRepository.createCategory(
                    Category(
                        name = line.category,
                        categoryType = TransactionType.EXPENSE,
                        color = NEW_CATEGORY_COLORS[newColorIdx++ % NEW_CATEGORY_COLORS.size],
                        isEssential = line.essential
                    )
                )
                nameToId[line.category] = catId
            }

            val limit = BigDecimal.valueOf(line.monthlyAmount).setScale(2, RoundingMode.HALF_UP)
            val existing = existingByCategory[catId]
            if (existing != null) {
                budgetRepository.updateBudget(existing.copy(limit = limit, periodType = BudgetPeriodType.MONTHLY))
            } else {
                budgetRepository.createBudget(
                    Budget(
                        categoryId = catId,
                        categoryName = line.category,
                        categoryColor = "",
                        periodType = BudgetPeriodType.MONTHLY,
                        limit = limit
                    )
                )
            }
            applied++
        }
        return applied
    }

    private companion object {
        val NEW_CATEGORY_COLORS = listOf("#6C63FF", "#E84393", "#00CEC9", "#FF9F43", "#2ED573", "#5F27CD")
    }
}
