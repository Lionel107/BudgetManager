package com.budgetmanager.data.repository

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
    val monthlyProvision: Double,
    val seasonal: Boolean = false,
    val peakMonths: List<String> = emptyList()
)

@Serializable
data class AdvisorAdvice(val summary: String = "", val tips: List<String> = emptyList())

/**
 * Appelle l'Edge Function `financial-advisor` (côté serveur). Le token de session
 * est ajouté automatiquement par le module Functions de supabase-kt → la RLS
 * garantit que l'analyse ne porte que sur les données de l'utilisateur connecté.
 */
class AdvisorRepository(private val provider: SupabaseClientProvider) {

    suspend fun getAdvice(): AdvisorResponse {
        val response = provider.client.functions.invoke("financial-advisor")
        return response.body<AdvisorResponse>()
    }
}
