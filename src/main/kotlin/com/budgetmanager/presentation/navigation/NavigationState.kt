package com.budgetmanager.presentation.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class Screen {
    HOME, ACCOUNTS, TRANSACTIONS, ADD_TRANSACTION, BUDGETS, ANALYTICS,
    RECURRING, ADD_RECURRING, TRANSFER, CATEGORIES, TEMPLATES, CHALLENGES, BADGES, EXCHANGE_RATES, EXPORT, IMPORT, ADVISOR, ANALYST, ASSISTANT, OBJECTIVES, SETTINGS
}

class NavigationState {
    var currentScreen by mutableStateOf(Screen.HOME)
        private set
    var editTransactionId: Long? by mutableStateOf(null)
        private set
    var editRecurringId: Long? by mutableStateOf(null)
        private set
    var fromTemplateId: Long? by mutableStateOf(null)
        private set
    // Flag: ouvre automatiquement le dialog "Nouveau compte" à l'arrivée sur ACCOUNTS
    var openAddAccountDialog by mutableStateOf(false)

    fun navigateTo(screen: Screen) {
        editTransactionId = null
        editRecurringId = null
        fromTemplateId = null
        openAddAccountDialog = false
        currentScreen = screen
    }

    fun navigateToNewTransactionFromTemplate(templateId: Long) {
        editTransactionId = null
        fromTemplateId = templateId
        currentScreen = Screen.ADD_TRANSACTION
    }

    fun navigateToNewAccount() {
        editTransactionId = null
        editRecurringId = null
        openAddAccountDialog = true
        currentScreen = Screen.ACCOUNTS
    }

    fun navigateToEditTransaction(id: Long) {
        editTransactionId = id
        currentScreen = Screen.ADD_TRANSACTION
    }

    fun navigateToEditRecurring(id: Long) {
        editRecurringId = id
        currentScreen = Screen.ADD_RECURRING
    }

    fun goBack() {
        editTransactionId = null
        editRecurringId = null
        openAddAccountDialog = false
        currentScreen = Screen.HOME
    }
}
