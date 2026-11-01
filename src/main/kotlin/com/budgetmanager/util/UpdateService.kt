package com.budgetmanager.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.system.exitProcess

/** Une mise à jour disponible sur GitHub Releases. */
data class UpdateInfo(
    val version: String,
    val title: String,
    val notes: String,
    val msiUrl: String,
    val msiName: String,
    val sizeBytes: Long
)

@Serializable
private data class GhAsset(
    val name: String,
    @SerialName("browser_download_url") val url: String,
    val size: Long = 0
)

@Serializable
private data class GhRelease(
    @SerialName("tag_name") val tag: String,
    val name: String? = null,
    val body: String? = null,
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    val assets: List<GhAsset> = emptyList()
)

/**
 * Mises à jour automatiques via les Releases GitHub (dépôt public, API sans clé).
 *
 *  1. [check] compare la dernière release (tag `vX.Y.Z`) à la version courante
 *     (propriété JVM `budgetmanager.version`, injectée par build.gradle.kts).
 *  2. [downloadAndInstall] télécharge le .msi dans %TEMP%, lance l'installation
 *     (msiexec /passive : barre de progression, aucune question) via un petit script
 *     qui attend la fermeture de l'app puis la relance, et quitte l'app.
 *
 * Le MSI a un upgradeUuid fixe : il remplace l'ancienne version en place, les
 * données (cloud) ne sont pas touchées.
 */
object UpdateService {

    private const val LATEST_URL = "https://api.github.com/repos/Lionel107/BudgetManager/releases/latest"
    private val json = Json { ignoreUnknownKeys = true }

    val currentVersion: String = System.getProperty("budgetmanager.version") ?: "0.0.0"

    /** Renvoie la mise à jour disponible, ou null (à jour, hors ligne, pas de .msi…). Ne lève jamais. */
    suspend fun check(): UpdateInfo? = withContext(Dispatchers.IO) {
        runCatching {
            val conn = (URL(LATEST_URL).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8_000
                readTimeout = 8_000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "BudgetManager/$currentVersion")
            }
            try {
                if (conn.responseCode != 200) return@runCatching null
                val release = json.decodeFromString<GhRelease>(conn.inputStream.bufferedReader().use { it.readText() })
                if (release.draft || release.prerelease) return@runCatching null
                val latest = release.tag.removePrefix("v")
                if (!isNewer(latest, currentVersion)) return@runCatching null
                val msi = release.assets.firstOrNull { it.name.endsWith(".msi", ignoreCase = true) } ?: return@runCatching null
                UpdateInfo(
                    version = latest,
                    title = release.name ?: "Budget Manager $latest",
                    notes = release.body.orEmpty(),
                    msiUrl = msi.url,
                    msiName = msi.name,
                    sizeBytes = msi.size
                )
            } finally {
                conn.disconnect()
            }
        }.onFailure { AppLogger.warn("Update", "Vérification des mises à jour impossible", it) }
            .getOrNull()
    }

    /** "2.10.0" > "2.9.1" (comparaison numérique, segment par segment). */
    fun isNewer(candidate: String, current: String): Boolean {
        val a = candidate.split('.', '-').map { it.toIntOrNull() ?: 0 }
        val b = current.split('.', '-').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    /**
     * Télécharge le .msi ([onProgress] reçoit 0..1), lance l'installeur puis QUITTE l'app.
     * Ne revient qu'en cas d'erreur (exception).
     */
    suspend fun downloadAndInstall(info: UpdateInfo, onProgress: (Float) -> Unit) {
        val msi = withContext(Dispatchers.IO) {
            val target = File(System.getProperty("java.io.tmpdir"), info.msiName)
            val conn = (URL(info.msiUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 30_000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "BudgetManager/$currentVersion")
            }
            try {
                if (conn.responseCode != 200) error("Téléchargement impossible (HTTP ${conn.responseCode}).")
                val total = conn.contentLengthLong.takeIf { it > 0 } ?: info.sizeBytes
                conn.inputStream.use { input ->
                    target.outputStream().use { out ->
                        val buf = ByteArray(64 * 1024)
                        var done = 0L
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            done += n
                            if (total > 0) onProgress((done.toFloat() / total).coerceIn(0f, 1f))
                        }
                    }
                }
            } finally {
                conn.disconnect()
            }
            if (info.sizeBytes > 0 && target.length() != info.sizeBytes) {
                target.delete()
                error("Fichier téléchargé incomplet, réessaie.")
            }
            target
        }
        launchInstallerAndExit(msi)
    }

    /**
     * Petit script .bat : attend que l'app soit fermée, installe le MSI, puis relance
     * l'app si on connaît son exécutable (cas de l'app installée, pas de `gradlew run`).
     */
    private fun launchInstallerAndExit(msi: File) {
        val exe = ProcessHandle.current().info().command().orElse("")
            .takeIf { it.endsWith("BudgetManager.exe", ignoreCase = true) }
        val script = File(System.getProperty("java.io.tmpdir"), "budgetmanager-update.bat")
        script.writeText(
            buildString {
                appendLine("@echo off")
                appendLine("timeout /t 3 /nobreak >nul")
                appendLine("msiexec /i \"${msi.absolutePath}\" /passive /norestart")
                if (exe != null) appendLine("start \"\" \"$exe\"")
                appendLine("del \"%~f0\"")
            },
            Charsets.ISO_8859_1 // cmd.exe lit les .bat dans la page de code ANSI
        )
        AppLogger.info("Update", "Installation de ${msi.name}, l'app va se fermer")
        ProcessBuilder("cmd", "/c", "start", "\"\"", "/min", script.absolutePath).start()
        exitProcess(0)
    }
}
