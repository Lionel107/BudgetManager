package com.budgetmanager.data.remote

import io.github.jan.supabase.gotrue.SessionStatus
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.gotrue.providers.builtin.Email
import kotlinx.coroutines.flow.StateFlow

/**
 * Point d'entrée unique pour l'authentification (e-mail / mot de passe).
 *
 * [sessionStatus] est observé par l'UI pour afficher soit l'écran de connexion,
 * soit l'application. supabase-kt gère la persistance et le refresh des tokens.
 */
class AuthRepository(provider: SupabaseClientProvider) {

    private val auth = provider.client.auth

    /** État de session observable : Authenticated / NotAuthenticated / LoadingFromStorage / NetworkError. */
    val sessionStatus: StateFlow<SessionStatus> get() = auth.sessionStatus

    /** Connexion. Lève une exception (message Supabase) en cas d'échec. */
    suspend fun signIn(email: String, password: String) {
        auth.signInWith(Email) {
            this.email = email.trim()
            this.password = password
        }
    }

    /**
     * Inscription. Selon la config Supabase (confirmation e-mail activée ou non),
     * l'utilisateur est soit connecté immédiatement, soit doit confirmer son e-mail.
     */
    suspend fun signUp(email: String, password: String) {
        auth.signUpWith(Email) {
            this.email = email.trim()
            this.password = password
        }
    }

    suspend fun signOut() {
        auth.signOut()
    }

    /** ID de l'utilisateur connecté (UUID Supabase), ou null. Utile en Phase 2c. */
    fun currentUserId(): String? = auth.currentUserOrNull()?.id
}
