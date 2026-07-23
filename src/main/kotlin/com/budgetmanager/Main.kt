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

    // 2. Force Database initialization FIRST (triggers DatabaseManager.init())
    val dbManager = getKoin().get<DatabaseManager>()
    // DatabaseManager.init() is called in Koin's single{} block, so DB is now ready

    // 3. Create default categories if first run
    val categoryRepo = getKoin().get<CategoryRepository>()
    runBlocking {
        categoryRepo.createDefaultCategories()
    }

    // 3b. Daily backup (runs once per day at most)
    runCatching { com.budgetmanager.util.BackupService().runDailyBackup() }

    // 4. Process any pending recurring transactions
    val recurringRepo = getKoin().get<RecurringTransactionRepository>()
    runBlocking {
        recurringRepo.processRecurringTransactions()
    }

    // 4b. Background scheduler — re-process every 30 minutes while app is running
    //     handles: app left open across midnight / for several days
    val backgroundScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    backgroundScope.launch {
        while (true) {
            delay(30 * 60 * 1000L) // 30 minutes
            try {
                recurringRepo.processRecurringTransactions()
            } catch (_: Exception) { /* swallow — retry next tick */ }
        }
    }

    // 4c. Populate notifications from advice engine on startup + periodically
    backgroundScope.launch {
        delay(2000) // wait for initial data load
        while (true) {
            try {
                val accountRepo = getKoin().get<com.budgetmanager.data.repository.AccountRepository>()
                val transactionRepo = getKoin().get<com.budgetmanager.data.repository.TransactionRepository>()
                val budgetRepo = getKoin().get<com.budgetmanager.data.repository.BudgetRepository>()
                val appPrefs = getKoin().get<com.budgetmanager.data.preferences.AppPreferences>()

                val accounts = accountRepo.getAllAccounts().first()
                val transactions = transactionRepo.getAllTransactions().first()
                val now = java.time.YearMonth.now()
                val budgets = budgetRepo.getBudgetsWithSpending(now.atDay(1), now.atEndOfMonth()).first()
                    .map { s ->
                        com.budgetmanager.domain.model.BudgetWithStatus(
                            budget = com.budgetmanager.domain.model.Budget(
                                id = s.budgetId,
                                categoryId = s.categoryId,
                                categoryName = s.categoryName,
                                categoryColor = s.categoryColor,
                                periodType = com.budgetmanager.domain.model.BudgetPeriodType.MONTHLY,
                                limit = s.budgetLimit
                            ),
                            spent = s.spent,
                            remaining = s.remaining,
                            percentage = s.percentage,
                            state = s.state
                        )
                    }
                val advices = com.budgetmanager.util.AdviceEngine().analyze(
                    accounts, transactions, budgets, appPrefs.savingsGoal
                )
                advices.forEach { com.budgetmanager.presentation.components.NotificationCenter.addFromAdvice(it) }
            } catch (_: Exception) { /* skip */ }
            delay(60 * 60 * 1000L) // refresh hourly
        }
    }

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
                    val navigationState = remember { NavigationState() }
                    AppLayout(navigationState)
                }
            }
        }
    }
}
