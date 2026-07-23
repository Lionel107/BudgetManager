package com.budgetmanager.presentation.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.budgetmanager.presentation.components.*
import com.budgetmanager.presentation.navigation.NavigationState
import com.budgetmanager.presentation.navigation.Screen
import com.budgetmanager.presentation.theme.*
import kotlinx.coroutines.*
import org.koin.core.context.GlobalContext.get as getKoin
import com.budgetmanager.presentation.theme.ThemeModeState

data class SettingsUiState(
    val themeMode: String = "system",
    val currency: String = "EUR",
    val alertThreshold: Float = 0.9f,
    val savingsGoal: String = "0",
    val density: String = "normal",
    val fontScale: Float = 1.0f,
    val geminiApiKey: String = "",
    val vacationEnabled: Boolean = false,
    val vacationStart: String = "",
    val vacationEnd: String = "",
    val vacationBudget: String = "",
    val vacationTag: String = "vacances",
    val autoEvening: Boolean = false,
    val isSaving: Boolean = false,
    val saveMessage: String? = null
)

class SettingsScreenState {
    var uiState by mutableStateOf(SettingsUiState())
        private set

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    init { loadData() }

    private fun loadData() {
        scope.launch {
            try {
                val koin = getKoin()
                val appPrefs = koin.get<com.budgetmanager.data.preferences.AppPreferences>()
                uiState = uiState.copy(
                    themeMode = appPrefs.themeMode,
                    currency = appPrefs.currencyCode,
                    alertThreshold = appPrefs.budgetAlertThreshold,
                    savingsGoal = appPrefs.savingsGoal.let {
                        if (it.toDouble() > 0) String.format("%.0f", it) else ""
                    },
                    density = appPrefs.density,
                    fontScale = appPrefs.fontScale,
                    geminiApiKey = appPrefs.geminiApiKey,
                    vacationEnabled = appPrefs.vacationModeEnabled,
                    vacationStart = appPrefs.vacationStart,
                    vacationEnd = appPrefs.vacationEnd,
                    vacationBudget = appPrefs.vacationBudget.let {
                        if (it.toDouble() > 0) String.format("%.0f", it) else ""
                    },
                    vacationTag = appPrefs.vacationTag,
                    autoEvening = appPrefs.autoEveningMode
                )
            } catch (_: Exception) {}
        }
    }

    fun updateTheme(mode: String) {
        uiState = uiState.copy(themeMode = mode)
        ThemeModeState.value = mode
        // Persist immediately
        scope.launch {
            try {
                val koin = getKoin()
                val appPrefs = koin.get<com.budgetmanager.data.preferences.AppPreferences>()
                appPrefs.themeMode = mode
            } catch (_: Exception) {}
        }
    }
    fun updateCurrency(c: String) { uiState = uiState.copy(currency = c) }
    fun updateAlertThreshold(v: Float) { uiState = uiState.copy(alertThreshold = v) }
    fun updateSavingsGoal(v: String) { uiState = uiState.copy(savingsGoal = v.filter { it.isDigit() || it == '.' }) }

    fun updateDensity(d: String) {
        uiState = uiState.copy(density = d)
        com.budgetmanager.presentation.theme.DensityState.value = d
        scope.launch {
            try {
                val koin = getKoin()
                val appPrefs = koin.get<com.budgetmanager.data.preferences.AppPreferences>()
                appPrefs.density = d
            } catch (_: Exception) {}
        }
    }

    fun updateFontScale(v: Float) {
        uiState = uiState.copy(fontScale = v)
        com.budgetmanager.presentation.theme.FontScaleState.value = v
        scope.launch {
            try {
                val koin = getKoin()
                val appPrefs = koin.get<com.budgetmanager.data.preferences.AppPreferences>()
                appPrefs.fontScale = v
            } catch (_: Exception) {}
        }
    }

    fun updateGeminiKey(k: String) { uiState = uiState.copy(geminiApiKey = k) }

    fun toggleVacation(enabled: Boolean) {
        uiState = uiState.copy(vacationEnabled = enabled)
        scope.launch {
            try {
                val koin = getKoin()
                val appPrefs = koin.get<com.budgetmanager.data.preferences.AppPreferences>()
                appPrefs.vacationModeEnabled = enabled
            } catch (_: Exception) {}
        }
    }

    fun updateVacationStart(d: String) { uiState = uiState.copy(vacationStart = d) }
    fun updateVacationEnd(d: String) { uiState = uiState.copy(vacationEnd = d) }
    fun updateVacationBudget(b: String) { uiState = uiState.copy(vacationBudget = b.filter { it.isDigit() || it == '.' }) }
    fun updateVacationTag(t: String) { uiState = uiState.copy(vacationTag = t) }

    fun toggleAutoEvening(enabled: Boolean) {
        uiState = uiState.copy(autoEvening = enabled)
        scope.launch {
            try {
                val koin = getKoin()
                val appPrefs = koin.get<com.budgetmanager.data.preferences.AppPreferences>()
                appPrefs.autoEveningMode = enabled
            } catch (_: Exception) {}
        }
    }

    fun save() {
        uiState = uiState.copy(isSaving = true)
        scope.launch {
            try {
                val koin = getKoin()
                val appPrefs = koin.get<com.budgetmanager.data.preferences.AppPreferences>()
                appPrefs.themeMode = uiState.themeMode
                appPrefs.currencyCode = uiState.currency
                appPrefs.budgetAlertThreshold = uiState.alertThreshold
                appPrefs.savingsGoal = java.math.BigDecimal(uiState.savingsGoal.ifBlank { "0" })
                appPrefs.density = uiState.density
                appPrefs.fontScale = uiState.fontScale
                appPrefs.geminiApiKey = uiState.geminiApiKey
                appPrefs.vacationModeEnabled = uiState.vacationEnabled
                appPrefs.vacationStart = uiState.vacationStart
                appPrefs.vacationEnd = uiState.vacationEnd
                appPrefs.vacationBudget = java.math.BigDecimal(uiState.vacationBudget.ifBlank { "0" })
                appPrefs.vacationTag = uiState.vacationTag.ifBlank { "vacances" }
                appPrefs.autoEveningMode = uiState.autoEvening
                uiState = uiState.copy(isSaving = false, saveMessage = "Paramètres enregistrés !")
                delay(2000)
                uiState = uiState.copy(saveMessage = null)
            } catch (e: Exception) {
                uiState = uiState.copy(isSaving = false, saveMessage = "Erreur: ${e.message}")
            }
        }
    }

    fun dispose() { scope.cancel() }
}

@Composable
fun SettingsScreen(navigationState: NavigationState) {
    val state = remember { SettingsScreenState() }
    DisposableEffect(Unit) { onDispose { state.dispose() } }

    val ui = state.uiState
    var currencyDropdownExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
            SectionHeader(title = "Paramètres")
            Spacer(Modifier.height(12.dp))

            // Theme
            NeumorphicCard(modifier = Modifier.fillMaxWidth(), elevation = 6.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Palette, null, tint = NeumorphicPrimary, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("Thème", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("light" to "Clair", "dark" to "Sombre", "blue" to "Bleu", "rose" to "Rose").forEach { (mode, label) ->
                        FilterChip(
                            label = label,
                            isSelected = ui.themeMode == mode,
                            onClick = { state.updateTheme(mode) }
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Density + font scale
            NeumorphicCard(modifier = Modifier.fillMaxWidth(), elevation = 6.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.FormatSize, null, tint = NeumorphicPrimary, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("Densite et taille du texte", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(12.dp))
                Text("Densite de l'interface", style = MaterialTheme.typography.labelMedium, color = NeumorphicTextSecondary)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("compact" to "Compacte", "normal" to "Normale", "large" to "Aeree").forEach { (k, l) ->
                        FilterChip(label = l, isSelected = ui.density == k, onClick = { state.updateDensity(k) })
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text("Taille de la police : ${(ui.fontScale * 100).toInt()}%", style = MaterialTheme.typography.labelMedium, color = NeumorphicTextSecondary)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0.85f to "85%", 1.0f to "100%", 1.15f to "115%", 1.30f to "130%").forEach { (v, l) ->
                        FilterChip(label = l, isSelected = ui.fontScale == v, onClick = { state.updateFontScale(v) })
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Currency
            NeumorphicCard(modifier = Modifier.fillMaxWidth(), elevation = 6.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Euro, null, tint = NeumorphicPrimary, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("Devise", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(12.dp))
                Box {
                    OutlinedTextField(
                        value = ui.currency,
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = {
                            IconButton(onClick = { currencyDropdownExpanded = true }) {
                                Icon(Icons.Filled.ArrowDropDown, "")
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = readOnlyTextFieldColors()
                    )
                    DropdownMenu(
                        expanded = currencyDropdownExpanded,
                        onDismissRequest = { currencyDropdownExpanded = false }
                    ) {
                        listOf("EUR", "USD", "GBP", "CHF", "CAD").forEach { cur ->
                            DropdownMenuItem(
                                text = { Text(cur) },
                                onClick = { state.updateCurrency(cur); currencyDropdownExpanded = false }
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Alert threshold
            NeumorphicCard(modifier = Modifier.fillMaxWidth(), elevation = 6.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Notifications, null, tint = NeumorphicPrimary, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("Seuil d'alerte budget", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Alerte lorsque ${(ui.alertThreshold * 100).toInt()}% du budget est atteint",
                    style = MaterialTheme.typography.bodyMedium,
                    color = NeumorphicTextSecondary
                )
                Spacer(Modifier.height(8.dp))
                Slider(
                    value = ui.alertThreshold,
                    onValueChange = { state.updateAlertThreshold(it) },
                    valueRange = 0.5f..1.0f,
                    steps = 9,
                    colors = SliderDefaults.colors(
                        thumbColor = NeumorphicPrimary,
                        activeTrackColor = NeumorphicPrimary,
                        inactiveTrackColor = NeumorphicDepressed
                    )
                )
            }

            Spacer(Modifier.height(16.dp))

            // Savings goal
            NeumorphicCard(modifier = Modifier.fillMaxWidth(), elevation = 6.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Savings, null, tint = IncomeColor, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("Objectif d'épargne mensuel", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Définissez le montant que vous souhaitez épargner chaque mois",
                    style = MaterialTheme.typography.bodyMedium,
                    color = NeumorphicTextSecondary
                )
                Spacer(Modifier.height(12.dp))
                NeumorphicTextField(
                    value = ui.savingsGoal,
                    onValueChange = { state.updateSavingsGoal(it) },
                    label = "Montant (EUR)",
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.height(16.dp))

            // Category management link
            NeumorphicCard(modifier = Modifier.fillMaxWidth(), elevation = 6.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Category, null, tint = NeumorphicPrimary, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Gestion des catégories", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text("Ajouter, modifier ou supprimer des catégories", style = MaterialTheme.typography.bodySmall, color = NeumorphicTextTertiary)
                    }
                    NeumorphicButton(
                        text = "Gérer",
                        onClick = { navigationState.navigateTo(Screen.CATEGORIES) },
                        isPrimary = false
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // Auto evening mode
            NeumorphicCard(modifier = Modifier.fillMaxWidth(), elevation = 6.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.NightsStay, null, tint = NeumorphicPrimary, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Mode soir automatique", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Bascule en theme sombre entre 21h et 7h pour fatiguer moins les yeux.",
                            style = MaterialTheme.typography.bodySmall, color = NeumorphicTextTertiary
                        )
                    }
                    Switch(
                        checked = ui.autoEvening,
                        onCheckedChange = { state.toggleAutoEvening(it) },
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = NeumorphicPrimary,
                            checkedThumbColor = androidx.compose.ui.graphics.Color.White
                        )
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // Vacation mode
            NeumorphicCard(modifier = Modifier.fillMaxWidth(), elevation = 6.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.BeachAccess, null, tint = NeumorphicPrimary, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Mode vacances", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Pendant la periode, les transactions sont taggees automatiquement et tu obtiens un bilan separe.",
                            style = MaterialTheme.typography.bodySmall, color = NeumorphicTextTertiary
                        )
                    }
                    Switch(
                        checked = ui.vacationEnabled,
                        onCheckedChange = { state.toggleVacation(it) },
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = NeumorphicPrimary,
                            checkedThumbColor = androidx.compose.ui.graphics.Color.White
                        )
                    )
                }
                if (ui.vacationEnabled) {
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        NeumorphicTextField(
                            value = ui.vacationStart,
                            onValueChange = { state.updateVacationStart(it) },
                            label = "Debut (AAAA-MM-JJ)",
                            placeholder = "2026-07-01",
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        NeumorphicTextField(
                            value = ui.vacationEnd,
                            onValueChange = { state.updateVacationEnd(it) },
                            label = "Fin (AAAA-MM-JJ)",
                            placeholder = "2026-07-15",
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        NeumorphicTextField(
                            value = ui.vacationBudget,
                            onValueChange = { state.updateVacationBudget(it) },
                            label = "Budget vacances (EUR, optionnel)",
                            suffix = "EUR",
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        NeumorphicTextField(
                            value = ui.vacationTag,
                            onValueChange = { state.updateVacationTag(it) },
                            label = "Tag auto",
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Gemini API key (optional)
            NeumorphicCard(modifier = Modifier.fillMaxWidth(), elevation = 6.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.AutoAwesome, null, tint = NeumorphicPrimary, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Cle API Gemini (optionnel)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Si renseignee, l'onglet Conseils utilisera Gemini pour des suggestions plus poussees. Sinon, le moteur de regles local s'applique.",
                            style = MaterialTheme.typography.bodySmall,
                            color = NeumorphicTextTertiary
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                NeumorphicTextField(
                    value = ui.geminiApiKey,
                    onValueChange = { state.updateGeminiKey(it) },
                    label = "Cle API",
                    placeholder = "AIza...",
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.height(16.dp))

            // About
            NeumorphicCard(modifier = Modifier.fillMaxWidth(), elevation = 6.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Info, null, tint = NeumorphicPrimary, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("À propos", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text("Budget Manager Desktop v2.0.0", style = MaterialTheme.typography.bodySmall, color = NeumorphicTextTertiary)
                        Text("Application de gestion budgétaire", style = MaterialTheme.typography.bodySmall, color = NeumorphicTextTertiary)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Déconnexion
            val logoutScope = rememberCoroutineScope()
            NeumorphicCard(modifier = Modifier.fillMaxWidth(), elevation = 6.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Logout, null, tint = ExpenseColor, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Compte", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text("Se déconnecter de ce compte", style = MaterialTheme.typography.bodySmall, color = NeumorphicTextTertiary)
                    }
                    NeumorphicButton(
                        text = "Se déconnecter",
                        icon = Icons.Filled.Logout,
                        isPrimary = false,
                        onClick = {
                            logoutScope.launch {
                                runCatching {
                                    getKoin().get<com.budgetmanager.data.remote.AuthRepository>().signOut()
                                }
                            }
                        }
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // Save button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (ui.saveMessage != null) {
                    Text(
                        ui.saveMessage!!,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (ui.saveMessage!!.startsWith("Erreur")) ExpenseColor else IncomeColor,
                        modifier = Modifier.padding(end = 16.dp)
                    )
                }
                NeumorphicButton(
                    text = "Enregistrer les paramètres",
                    icon = Icons.Filled.Save,
                    onClick = { state.save() },
                    enabled = !ui.isSaving
                )
            }

            Spacer(Modifier.height(32.dp))
    }
}
