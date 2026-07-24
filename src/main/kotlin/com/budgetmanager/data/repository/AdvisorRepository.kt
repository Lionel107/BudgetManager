package com.budgetmanager.data.repository

import com.budgetmanager.data.preferences.AppPreferences
import com.budgetmanager.data.remote.SupabaseClientProvider
import io.github.jan.supabase.functions.functions
import io.ktor.client.call.body
import kotlinx.serialization.Serializable

// ===== DTO de réponse de l'Edge Function financial-advisor =====

@Serializable
data class AdvisorResponse(
    val analysis: AdvisorAnalysis? = null,
    val advice: AdvisorAdvice? = null,
    val error: String? = null
)

@Serializable
data class AdvisorAnalysis(
    val period: AdvisorPeriod,
    val totals: AdvisorTotals,
    val categories: List<CategoryStat> = emptyList(),
    val seasonal: List<CategoryStat> = emptyList()
)

@Serializable
data class AdvisorPeriod(val from: String, val to: String, val months: Int)

@Serializable
data class AdvisorTotals(
    val income: Double,
    val expenses: Double,
    val savings: Double,
    val savingsRatePct: Double
)

@Serializable
data class CategoryStat(
    val name: String,
    val annualTotal: Double,
    val monthlyAverage: Double = 0.0,        // coût lissé (total / 12)
    val seasonal: Boolean = false,
    val peakMonths: List<String> = emptyList(),
    val seasonalProvision: Double = 0.0      // à mettre de côté chaque mois pour les pics
)

@Serializable
data class AdvisorAdvice(val summary: String = "", val tips: List<String> = emptyList())

@Serializable
private data class AdvisorRequest(val geminiKey: String)

/**
 * Appelle l'Edge Function `financial-advisor` (côté serveur). Le token de session
 * est ajouté automatiquement par le module Functions de supabase-kt → la RLS
 * garantit que l'analyse ne porte que sur les données de l'utilisateur connecté.
 *
 * La clé Gemini PERSO de l'utilisateur (Réglages → stockée en local) est envoyée
 * dans le corps de la requête : utilisée côté serveur puis jetée, jamais stockée.
 */
class AdvisorRepository(
    private val provider: SupabaseClientProvider,
    private val prefs: AppPreferences
) {

    suspend fun getAdvice(): AdvisorResponse {
        val response = provider.client.functions.invoke(
            function = "financial-advisor",
            body = AdvisorRequest(geminiKey = prefs.geminiApiKey)
        )
        return response.body<AdvisorResponse>()
    }
}
