package com.budgetmanager.data.remote

import io.github.jan.supabase.gotrue.SessionManager
import io.github.jan.supabase.gotrue.user.UserSession
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Persiste la session Supabase (tokens) dans un fichier local, à côté de la base :
 * `~/.budgetmanager/session.json`.
 *
 * Permet de rester connecté entre deux lancements de l'app sans redemander
 * l'e-mail/mot de passe. supabase-kt rafraîchit automatiquement le token via
 * cette session au démarrage (autoLoadFromStorage + alwaysAutoRefresh).
 */
class FileSessionManager(
    private val file: File = File(System.getProperty("user.home"), ".budgetmanager/session.json")
) : SessionManager {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    override suspend fun saveSession(session: UserSession) {
        runCatching {
            file.parentFile?.mkdirs()
            file.writeText(json.encodeToString(UserSession.serializer(), session))
        }
    }

    override suspend fun loadSession(): UserSession? {
        if (!file.exists()) return null
        return runCatching {
            json.decodeFromString(UserSession.serializer(), file.readText())
        }.getOrNull()
    }

    override suspend fun deleteSession() {
        runCatching { file.delete() }
    }
}
