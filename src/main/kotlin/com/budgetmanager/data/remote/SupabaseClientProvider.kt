package com.budgetmanager.data.remote

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.gotrue.FlowType
import io.github.jan.supabase.postgrest.Postgrest

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
        install(Auth) {
            // PKCE : flux d'auth recommandé pour les clients publics (desktop/mobile)
            flowType = FlowType.PKCE
            // La session (tokens) est conservée et rafraîchie automatiquement
            alwaysAutoRefresh = true
            autoLoadFromStorage = true
        }
        install(Postgrest)
    }
}
