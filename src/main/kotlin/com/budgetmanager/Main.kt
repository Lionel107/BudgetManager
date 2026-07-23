package com.budgetmanager

import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.budgetmanager.data.database.DatabaseManager
import com.budgetmanager.data.repository.CategoryRepository
import com.budgetmanager.data.repository.RecurringTransactionRepository
import com.budgetmanager.di.appModule
import com.budgetmanager.presentation.navigation.AppLayout
import com.budgetmanager.presentation.navigation.NavigationState
import com.budgetmanager.presentation.theme.BudgetManagerTheme
import com.budgetmanager.presentation.theme.ThemeModeState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.koin.core.context.GlobalContext.startKoin
import org.koin.core.context.GlobalContext.get as getKoin
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

fun main() {
    // Global error handler — writes stacktrace to ~/Desktop/budgetmanager_error.log + structured log
    Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))
        val logFile = File(System.getProperty("user.home"), "Desktop/budgetmanager_error.log")
        logFile.appendText("=== ${java.time.LocalDateTime.now()} [${thread.name}] ===\n$sw\n")
        com.budgetmanager.util.AppLogger.error("UncaughtException", "in thread ${thread.name}", throwable)
    }

    com.budgetmanager.util.AppLogger.info("Main", "Application starting")

    // 1. Initialize Koin DI
    startKoin {
        modules(appModule)
    }

    // 2. Note : les repositories utilisent désormais Supabase. Les opérations qui
    //    dépendent des données (catégories par défaut, récurrences, conseils)
    //    nécessitent une session authentifiée → elles sont lancées APRÈS le login
    //    (voir PostLoginInit dans le contenu authentifié plus bas).

    // 5. Load theme + density preferences
    val appPrefs = getKoin().get<com.budgetmanager.data.preferences.AppPreferences>()

    val effectiveTheme = run {
        val pref = appPrefs.themeMode
        if (appPrefs.autoEveningMode) {
            val hour = java.time.LocalTime.now().hour
            val nightStart = appPrefs.eveningStartHour
            val isNight = hour >= nightStart || hour < 7
            if (isNight && pref == "light") "dark" else pref
        } else pref
    }
    ThemeModeState.value = effectiveTheme
    com.budgetmanager.presentation.theme.FontScaleState.value = appPrefs.fontScale
    com.budgetmanager.presentation.theme.DensityState.value = appPrefs.density

    // 6. Launch the application window
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Budget Manager",
            state = rememberWindowState(
                width = 1280.dp,
                height = 800.dp,
                position = WindowPosition(Alignment.Center)
            )
        ) {
            BudgetManagerTheme {
                val authRepo = remember { getKoin().get<com.budgetmanager.data.remote.AuthRepository>() }
                com.budgetmanager.presentation.screens.auth.AuthGate(authRepo) {
                    com.budgetmanager.presentation.screens.auth.PostLoginInit()
                    val navigationState = remember { NavigationState() }
                    AppLayout(navigationState)
                }
            }
        }
    }
}
