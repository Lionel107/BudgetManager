package com.budgetmanager.data.repository

import com.budgetmanager.data.preferences.AppPreferences
import com.budgetmanager.data.remote.SupabaseClientProvider
import com.budgetmanager.domain.model.ObjectiveType
import io.github.jan.supabase.functions.functions
import io.ktor.client.call.body
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import java.time.LocalDate

// ===== Contexte DÉTERMINISTE envoyé à l'agent Analyste (l'IA ne recalcule rien) =====

@Serializable
data class AnalystContext(
    val month: String,
    val totals: AnalystTotals,
    val budgets: List<AnalystBudget> = emptyList(),
    val objectives: List<AnalystObjective> = emptyList()
)

@Serializable
data class AnalystTotals(val income: Double, val expenses: Double, val savings: Double)

@Serializable
data class AnalystBudget(
    val name: String,
    val limit: Double,
    val spent: Double,
    val pct: Int,
    val state: String
)

@Serializable
data class AnalystObjective(
    val title: String,
    val type: String,
    val onTrack: Boolean,
    val label: String
)

@Serializable
data class AnalystTurn(val role: String, val text: String)

@Serializable
data class AnalystResponse(val reply: String = "", val error: String? = null)

@Serializable
private data class AnalystRequest(
    val geminiKey: String,
    val mode: String,
    val context: AnalystContext,
    val history: List<AnalystTurn> = emptyList(),
    val question: String = ""
)

private val MOIS_FR = arrayOf(
    "", "janvier", "février", "mars", "avril", "mai", "juin",
    "juillet", "août", "septembre", "octobre", "novembre", "décembre"
)

/**
 * Agent **Analyste** (coach du respect). Construit le contexte financier DÉTERMINISTE
 * du mois en cours (respect des budgets + progression des objectifs), puis appelle
 * l'Edge Function `analyst` qui se contente de RÉDIGER (bilan ou réponse de chat).
 *
 * La clé Gemini perso de l'utilisateur (Réglages, stockée en local) part dans le corps
 * de la requête : utilisée côté serveur puis jetée, jamais stockée.
 */
class AnalystRepository(
    private val provider: SupabaseClientProvider,
    private val prefs: AppPreferences,
    private val txRepo: TransactionRepository,
    private val budgetRepo: BudgetRepository,
    private val analysisRepo: AnalysisRepository
) {

    /** Snapshot déterministe du mois en cours (source de vérité des chiffres). */
    suspend fun buildContext(): AnalystContext {
        val now = LocalDate.now()
        val monthStart = now.withDayOfMonth(1)
        val monthEnd = now.withDayOfMonth(now.lengthOfMonth())

        val income = txRepo.getTotalIncome(monthStart.atStartOfDay(), monthEnd.atTime(23, 59, 59))
        val expenses = txRepo.getTotalExpenses(monthStart.atStartOfDay(), monthEnd.atTime(23, 59, 59))

        val budgets = budgetRepo.getBudgetsWithSpending(monthStart, monthEnd).first().map { b ->
            AnalystBudget(
                name = b.categoryName,
                limit = b.budgetLimit.toDouble(),
                spent = b.spent.toDouble(),
                pct = (b.percentage * 100).toInt(),
                state = b.state.name
            )
        }

        val objectives = analysisRepo.objectiveProgress().map { p ->
            AnalystObjective(
                title = p.objective.title,
                type = if (p.objective.type == ObjectiveType.SAVINGS) "épargne" else "plafond",
                onTrack = p.onTrack,
                label = p.label
            )
        }

        return AnalystContext(
            month = "${MOIS_FR[now.monthValue]} ${now.year}",
            totals = AnalystTotals(
                income = income.toDouble(),
                expenses = expenses.toDouble(),
                savings = income.subtract(expenses).toDouble()
            ),
            budgets = budgets,
            objectives = objectives
        )
    }

    suspend fun bilan(context: AnalystContext): AnalystResponse = invoke(
        AnalystRequest(geminiKey = prefs.geminiApiKey, mode = "bilan", context = context)
    )

    suspend fun chat(
        context: AnalystContext,
        history: List<AnalystTurn>,
        question: String
    ): AnalystResponse = invoke(
        AnalystRequest(
            geminiKey = prefs.geminiApiKey,
            mode = "chat",
            context = context,
            history = history,
            question = question
        )
    )

    private suspend fun invoke(req: AnalystRequest): AnalystResponse {
        val response = provider.client.functions.invoke(function = "analyst", body = req)
        return response.body<AnalystResponse>()
    }
}
