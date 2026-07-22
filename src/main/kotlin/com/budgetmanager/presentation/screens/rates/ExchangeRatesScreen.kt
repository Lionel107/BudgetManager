package com.budgetmanager.presentation.screens.rates

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
import com.budgetmanager.data.repository.ExchangeRate
import com.budgetmanager.presentation.components.*
import com.budgetmanager.presentation.navigation.NavigationState
import com.budgetmanager.presentation.theme.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import org.koin.core.context.GlobalContext.get as getKoin
import java.math.BigDecimal
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class RatesUiState(
    val rates: List<ExchangeRate> = emptyList(),
    val showAdd: Boolean = false
)

class RatesScreenState {
    var uiState by mutableStateOf(RatesUiState())
        private set

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    init { load() }

    private fun load() {
        scope.launch {
            val repo = getKoin().get<com.budgetmanager.data.repository.ExchangeRateRepository>()
            repo.getAll().collectLatest { rates ->
                uiState = uiState.copy(rates = rates)
            }
        }
    }

    fun showAdd() { uiState = uiState.copy(showAdd = true) }
    fun hideAdd() { uiState = uiState.copy(showAdd = false) }

    fun setRate(from: String, to: String, rate: BigDecimal) {
        scope.launch {
            getKoin().get<com.budgetmanager.data.repository.ExchangeRateRepository>()
                .setRate(from, to, rate)
            hideAdd()
        }
    }

    fun delete(id: Long) {
        val r = uiState.rates.find { it.id == id }
        scope.launch {
            val repo = getKoin().get<com.budgetmanager.data.repository.ExchangeRateRepository>()
            repo.delete(id)
            if (r != null) {
                UndoBus.show(UndoableAction(
                    message = "Taux ${r.from} → ${r.to} supprime",
                    onUndo = { repo.setRate(r.from, r.to, r.rate) }
                ))
            }
        }
    }

    fun dispose() { scope.cancel() }
}

@Composable
fun ExchangeRatesScreen(navigationState: NavigationState) {
    val state = remember { RatesScreenState() }
    DisposableEffect(Unit) { onDispose { state.dispose() } }

    val ui = state.uiState
    val tsFmt = remember { DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm") }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Taux de change", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = NeumorphicTextPrimary)
            NeumorphicButton(text = "Ajouter un taux", icon = Icons.Filled.Add, onClick = { state.showAdd() })
        }
        Text(
            "Definis les taux de change pour convertir tes comptes en devises differentes. " +
            "Exemple : 1 USD = 0.92 EUR signifie taux 0.92 pour la paire USD → EUR.",
            style = MaterialTheme.typography.bodyMedium,
            color = NeumorphicTextSecondary,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        if (ui.rates.isEmpty()) {
            EmptyState(
                message = "Aucun taux configure.\nAjoute le premier pour activer la conversion multi-devise.",
                icon = Icons.Filled.CurrencyExchange,
                actionText = "Ajouter un taux",
                onAction = { state.showAdd() }
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ui.rates.forEach { rate ->
                    NeumorphicCard(modifier = Modifier.fillMaxWidth(), elevation = 5.dp) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.CurrencyExchange, null, tint = NeumorphicPrimary, modifier = Modifier.size(24.dp))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "1 ${rate.from} = ${rate.rate.toPlainString()} ${rate.to}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = NeumorphicTextPrimary
                                )
                                Text(
                                    "Mis a jour le " + Instant.ofEpochMilli(rate.updatedAt)
                                        .atZone(ZoneId.systemDefault()).format(tsFmt),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = NeumorphicTextTertiary
                                )
                            }
                            IconButton(onClick = { state.delete(rate.id) }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Filled.Delete, "Supprimer", tint = ExpenseColor.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    if (ui.showAdd) {
        RateFormDialog(onSave = { f, t, r -> state.setRate(f, t, r) }, onDismiss = { state.hideAdd() })
    }
}

@Composable
private fun RateFormDialog(
    onSave: (String, String, BigDecimal) -> Unit,
    onDismiss: () -> Unit
) {
    var from by remember { mutableStateOf("USD") }
    var to by remember { mutableStateOf("EUR") }
    var rateText by remember { mutableStateOf("") }
    val currencies = listOf("EUR", "USD", "GBP", "CHF", "CAD", "JPY", "AUD", "CNY")
    var fromOpen by remember { mutableStateOf(false) }
    var toOpen by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nouveau taux", style = MaterialTheme.typography.headlineMedium, color = NeumorphicTextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Box(Modifier.weight(1f)) {
                        OutlinedTextField(
                            value = from, onValueChange = {}, readOnly = true,
                            label = { Text("De") },
                            trailingIcon = { IconButton(onClick = { fromOpen = true }) { Icon(Icons.Filled.ArrowDropDown, "") } },
                            modifier = Modifier.fillMaxWidth(),
                            colors = readOnlyTextFieldColors()
                        )
                        DropdownMenu(expanded = fromOpen, onDismissRequest = { fromOpen = false }) {
                            currencies.forEach { c ->
                                DropdownMenuItem(text = { Text(c) }, onClick = { from = c; fromOpen = false })
                            }
                        }
                    }
                    Box(Modifier.weight(1f)) {
                        OutlinedTextField(
                            value = to, onValueChange = {}, readOnly = true,
                            label = { Text("Vers") },
                            trailingIcon = { IconButton(onClick = { toOpen = true }) { Icon(Icons.Filled.ArrowDropDown, "") } },
                            modifier = Modifier.fillMaxWidth(),
                            colors = readOnlyTextFieldColors()
                        )
                        DropdownMenu(expanded = toOpen, onDismissRequest = { toOpen = false }) {
                            currencies.forEach { c ->
                                DropdownMenuItem(text = { Text(c) }, onClick = { to = c; toOpen = false })
                            }
                        }
                    }
                }
                NeumorphicTextField(
                    value = rateText,
                    onValueChange = { rateText = it.filter { c -> c.isDigit() || c == '.' || c == ',' } },
                    label = "Taux (1 $from = X $to)",
                    placeholder = "ex: 0.92",
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (from == to) {
                    Text("Choisis deux devises differentes.", style = MaterialTheme.typography.bodySmall, color = ExpenseColor)
                }
            }
        },
        confirmButton = {
            NeumorphicButton(
                text = "Enregistrer",
                onClick = {
                    val r = rateText.replace(",", ".").toBigDecimalOrNull()
                    if (r != null && r > BigDecimal.ZERO && from != to) {
                        onSave(from, to, r)
                    }
                },
                enabled = from != to && rateText.isNotBlank()
            )
        },
        dismissButton = {
            NeumorphicButton(text = "Annuler", onClick = onDismiss, isPrimary = false)
        },
        containerColor = NeumorphicElevated,
        shape = RoundedCornerShape(16.dp)
    )
}
