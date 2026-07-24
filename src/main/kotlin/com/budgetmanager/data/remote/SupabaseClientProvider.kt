package com.budgetmanager.data.remote

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.serializer.KotlinXSerializer
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.seconds

/**
 * Fournit l'unique instance de [SupabaseClient] de l'application.
 *
 * Modules installés :
 *  - [Auth] : authentification e-mail/mot de passe, session persistée.
 *  - [Postgrest] : accès CRUD à la base via l'API REST (protégé par la RLS).
 *
 * Enregistré en singleton dans Koin (voir AppModule).
 */
class SupabaseClientProvider {

    val client: SupabaseClient = createSupabaseClient(
        supabaseUrl = SupabaseConfig.url,
        supabaseKey = SupabaseConfig.anonKey
    ) {
        // Délai des requêtes (les appels IA via Edge Function peuvent prendre >10 s).
        requestTimeout = 30.seconds

        // Sérialiseur : omet les champs null (id/user_id/timestamps -> générés par
        // Postgres à l'insert) et ignore les colonnes non mappées.
        defaultSerializer = KotlinXSerializer(Json {
            ignoreUnknownKeys = true
            explicitNulls = false
            encodeDefaults = true
        })

        install(Auth) {
            // Session persistée sur disque (~/.budgetmanager/session.json)
            sessionManager = FileSessionManager()
            // La session (tokens) est chargée au démarrage et rafraîchie automatiquement
            alwaysAutoRefresh = true
            autoLoadFromStorage = true
        }
        install(Postgrest)
        install(Functions)
    }
}
