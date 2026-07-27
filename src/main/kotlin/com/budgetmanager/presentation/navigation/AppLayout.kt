package com.budgetmanager.presentation.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import com.budgetmanager.presentation.theme.NeumorphicBackground
import com.budgetmanager.presentation.components.KawaiiOverlay
import com.budgetmanager.presentation.components.UndoSnackbarOverlay
import com.budgetmanager.presentation.components.NotificationBell
import com.budgetmanager.presentation.components.NotificationDialog
import com.budgetmanager.presentation.components.UniversalSearchDialog
import com.budgetmanager.presentation.screens.home.HomeScreen
import com.budgetmanager.presentation.screens.accounts.AccountsScreen
import com.budgetmanager.presentation.screens.transactions.TransactionListScreen
import com.budgetmanager.presentation.screens.transactions.AddTransactionScreen
import com.budgetmanager.presentation.screens.budget.BudgetScreen
import com.budgetmanager.presentation.screens.analytics.AnalyticsScreen
import com.budgetmanager.presentation.screens.recurring.RecurringScreen
import com.budgetmanager.presentation.screens.recurring.AddRecurringScreen
import com.budgetmanager.presentation.screens.transfer.TransferScreen
import com.budgetmanager.presentation.screens.categories.CategoryScreen
import com.budgetmanager.presentation.screens.settings.SettingsScreen
import com.budgetmanager.presentation.screens.export.ExportScreen
import com.budgetmanager.presentation.screens.`import`.ImportScreen
import com.budgetmanager.presentation.screens.templates.TemplateScreen
import com.budgetmanager.presentation.screens.challenges.ChallengesScreen
import com.budgetmanager.presentation.screens.badges.BadgesScreen
import com.budgetmanager.presentation.screens.rates.ExchangeRatesScreen
import com.budgetmanager.presentation.screens.advisor.AdvisorScreen
import com.budgetmanager.presentation.screens.analyst.AnalystScreen
import com.budgetmanager.presentation.screens.objectives.ObjectivesScreen

@Composable
fun AppLayout(navigationState: NavigationState) {
    var showNotifications by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        runCatching { focusRequester.requestFocus() }
    }

    Box(
        Modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { ev ->
                if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val ctrl = ev.isCtrlPressed
                when {
                    ctrl && ev.key == Key.N -> { navigationState.navigateTo(Screen.ADD_TRANSACTION); true }
                    ctrl && ev.key == Key.K -> { showSearch = true; true }
                    ctrl && ev.key == Key.F -> { navigationState.navigateTo(Screen.TRANSACTIONS); true }
                    ctrl && ev.key == Key.B -> { navigationState.navigateTo(Screen.BUDGETS); true }
                    ctrl && ev.key == Key.H -> { navigationState.navigateTo(Screen.HOME); true }
                    ctrl && ev.key == Key.E -> { navigationState.navigateTo(Screen.EXPORT); true }
                    ctrl && ev.key == Key.I -> { navigationState.navigateTo(Screen.IMPORT); true }
                    ctrl && ev.key == Key.Comma -> { navigationState.navigateTo(Screen.SETTINGS); true }
                    ev.key == Key.Escape -> {
                        if (showSearch) { showSearch = false; true }
                        else if (showNotifications) { showNotifications = false; true }
                        else if (navigationState.currentScreen != Screen.HOME) {
                            navigationState.navigateTo(Screen.HOME); true
                        } else false
                    }
                    else -> false
                }
            }
    ) {
        Row(Modifier.fillMaxSize().background(NeumorphicBackground)) {
            Sidebar(
                currentScreen = navigationState.currentScreen,
                onNavigate = { navigationState.navigateTo(it) },
                onNotificationsClick = { showNotifications = true }
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 28.dp, end = 28.dp, top = 16.dp, bottom = 16.dp)
            ) {
                when (navigationState.currentScreen) {
                    Screen.HOME -> HomeScreen(navigationState)
                    Screen.ACCOUNTS -> AccountsScreen(navigationState)
                    Screen.TRANSACTIONS -> TransactionListScreen(navigationState)
                    Screen.ADD_TRANSACTION -> AddTransactionScreen(navigationState)
                    Screen.BUDGETS -> BudgetScreen(navigationState)
                    Screen.ANALYTICS -> AnalyticsScreen(navigationState)
                    Screen.RECURRING -> RecurringScreen(navigationState)
                    Screen.ADD_RECURRING -> AddRecurringScreen(navigationState)
                    Screen.TRANSFER -> TransferScreen(navigationState)
                    Screen.CATEGORIES -> CategoryScreen(navigationState)
                    Screen.EXPORT -> ExportScreen(navigationState)
                    Screen.IMPORT -> ImportScreen(navigationState)
                    Screen.TEMPLATES -> TemplateScreen(navigationState)
                    Screen.CHALLENGES -> ChallengesScreen(navigationState)
                    Screen.BADGES -> BadgesScreen(navigationState)
                    Screen.EXCHANGE_RATES -> ExchangeRatesScreen(navigationState)
                    Screen.ADVISOR -> AdvisorScreen()
                    Screen.ANALYST -> AnalystScreen()
                    Screen.OBJECTIVES -> ObjectivesScreen()
                    Screen.SETTINGS -> SettingsScreen(navigationState)
                }
            }
        }

        // Notification dialog — opened from the bell in the sidebar
        if (showNotifications) {
            NotificationDialog(onDismiss = { showNotifications = false })
        }

        if (showSearch) {
            UniversalSearchDialog(navigationState = navigationState, onDismiss = { showSearch = false })
        }

        // Kawaii animations overlay (rose theme only)
        KawaiiOverlay()

        // Global undo snackbar (any theme)
        UndoSnackbarOverlay()
    }
}
