package com.budgetmanager.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Vérifie qu'une clé API Gemini est fonctionnelle, via un appel léger à l'endpoint
 * `models` (pas de génération de contenu, donc quasi gratuit). Distingue :
 *  - clé valide, quota atteint, clé invalide, ou erreur réseau.
 */
object GeminiKeyTester {

    suspend fun test(key: String): String = withContext(Dispatchers.IO) {
        val k = key.trim()
        if (k.isBlank()) return@withContext "⚠️ Aucune clé saisie."
        try {
            val url = URL("https://generativelanguage.googleapis.com/v1beta/models?key=$k")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 10_000
            }
            val code = conn.responseCode
            conn.disconnect()
            when {
                code in 200..299 -> "✅ Clé valide et fonctionnelle."
                code == 429 -> "⚠️ Clé valide, mais quota atteint pour l'instant (réessaie plus tard)."
                code == 400 || code == 401 || code == 403 -> "❌ Clé invalide ou non autorisée. Vérifie-la sur aistudio.google.com/apikey."
                else -> "❌ Réponse inattendue (HTTP $code)."
            }
        } catch (e: Exception) {
            "❌ Échec réseau : ${e.message}"
        }
    }
}
