package com.budgetmanager.presentation.screens.transfer

import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.budgetmanager.domain.model.Account
import com.budgetmanager.domain.model.Transaction
import com.budgetmanager.domain.model.TransactionType
import com.budgetmanager.presentation.components.*
import com.budgetmanager.presentation.navigation.NavigationState
import com.budgetmanager.presentation.navigation.Screen
import com.budgetmanager.presentation.theme.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import org.koin.core.context.GlobalContext.get as getKoin
import java.math.BigDecimal
import java.time.LocalDateTime

data class TransferUiState(
    val accounts: List<Account> = emptyList(),
    val sourceAccountId: Long? = null,
    val destinationAccountId: Long? = null,
    val amount: String = "",
    val notes: String = "",
    val isSaving: Boolean = false,
    val successMessage: String? = null,
    val errorMessage: String? = null
)

class TransferScreenState {
    var uiState by mutableStateOf(TransferUiState())
        private set

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    init { loadData() }

    private fun loadData() {
        scope.launch {
            val koin = getKoin()
            val accountRepo = koin.get<com.budgetmanager.data.repository.AccountRepository>()
            accountRepo.getAllAccounts().collectLatest { accs ->
                uiState = uiState.copy(accounts = accs)
                if (accs.size >= 2) {
                    if (uiState.sourceAccountId == null) uiState = uiState.copy(sourceAccountId = accs[0].id)
                    if (uiState.destinationAccountId == null) uiState = uiState.copy(destinationAccountId = accs[1].id)
                }
            }
        }
    }

    fun updateSource(id: Long) {
        uiState = uiState.copy(sourceAccountId = id, errorMessage = null)
    }
    fun updateDestination(id: Long) {
        uiState = uiState.copy(destinationAccountId = id, errorMessage = null)
    }
    fun updateAmount(v: String) {
        uiState = uiState.copy(amount = v.filter { it.isDigit() || it == '.' }, errorMessage = null)
    }
    fun updateNotes(v: String) { uiState = uiState.copy(notes = v) }

    fun swapAccounts() {
        uiState = uiState.copy(
            sourceAccountId = uiState.destinationAccountId,
            destinationAccountId = uiState.sourceAccountId
        )
    }

    fun executeTransfer(onSuccess: () -> Unit) {
        val amount = uiState.amount.toBigDecimalOrNull()
        if (amount == null || amount <= BigDecimal.ZERO) {
            uiState = uiState.copy(errorMessage = "Veuillez saisir un montant valide.")
            return
        }
        val srcId = uiState.sourceAccountId
        val dstId = uiState.destinationAccountId
        if (srcId == null || dstId == null) {
            uiState = uiState.copy(errorMessage = "Veuillez sélectionner deux comptes.")
            return
        }
        if (srcId == dstId) {
            uiState = uiState.copy(errorMessage = "Les comptes source et destination doivent être différents.")
            return
        }

        uiState = uiState.copy(isSaving = true, errorMessage = null)
        scope.launch {
            try {
                val koin = getKoin()
                val transactionRepo = koin.get<com.budgetmanager.data.repository.TransactionRepository>()
                val accountRepo = koin.get<com.budgetmanager.data.repository.AccountRepository>()

                val srcAccount = uiState.accounts.find { it.id == srcId }!!
                val dstAccount = uiState.accounts.find { it.id == dstId }!!

                // Use the built-in transfer method
                accountRepo.transferBetweenAccounts(
                    fromId = srcId,
                    toId = dstId,
                    amount = amount,
                    notes = uiState.notes.ifBlank { null }
                )

                withContext(Dispatchers.Main) {
                    uiState = uiState.copy(isSaving = false, successMessage = "Transfert effectué !")
                    onSuccess()
                }
            } catch (e: Exception) {
                uiState = uiState.copy(isSaving = false, errorMessage = "Erreur: ${e.message}")
            }
        }
    }

    fun dispose() { scope.cancel() }
}

@Composable
fun TransferScreen(navigationState: NavigationState) {
    val state = remember { TransferScreenState() }
    DisposableEffect(Unit) { onDispose { state.dispose() } }

    val ui = state.uiState
    var sourceDropdownExpanded by remember { mutableStateOf(false) }
    var destinationDropdownExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
            SectionHeader(title = "Transfert entre comptes")
            Spacer(Modifier.height(12.dp))

            if (ui.accounts.size < 2) {
                EmptyState(
                    message = "Vous avez besoin d'au moins 2 comptes pour effectuer un transfert.",
                    icon = Icons.Filled.SwapHoriz,
                    actionText = "Créer un compte",
                    onAction = { navigationState.navigateTo(Screen.ACCOUNTS) }
                )
            } else {
                NeumorphicCard(modifier = Modifier.fillMaxWidth(), elevation = 8.dp, borderRadius = 20.dp) {
                    // Source account
                    Text("Compte source", style = MaterialTheme.typography.labelLarge, color = NeumorphicTextSecondary)
                    Spacer(Modifier.height(8.dp))
                    Box {
                        val srcAcc = ui.accounts.find { it.id == ui.sourceAccountId }
                        OutlinedTextField(
                            value = srcAcc?.let { "${it.name} (${String.format("%.2f", it.balance)} €)" } ?: "",
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = {
                                IconButton(onClick = { sourceDropdownExpanded = true }) {
                                    Icon(Icons.Filled.ArrowDropDown, "")
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = readOnlyTextFieldColors()
                        )
                        DropdownMenu(
                            expanded = sourceDropdownExpanded,
                            onDismissRequest = { sourceDropdownExpanded = false }
                        ) {
                            ui.accounts.forEach { acc ->
                                DropdownMenuItem(
                                    text = { Text("${acc.name} (${String.format("%.2f", acc.balance)} €)") },
                                    onClick = { state.updateSource(acc.id); sourceDropdownExpanded = false }
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // Swap button
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        IconButton(
                            onClick = { state.swapAccounts() },
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(NeumorphicPrimary.copy(alpha = 0.1f))
                        ) {
                            Icon(Icons.Filled.SwapVert, "Inverser", tint = NeumorphicPrimary)
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // Destination account
                    Text("Compte destination", style = MaterialTheme.typography.labelLarge, color = NeumorphicTextSecondary)
                    Spacer(Modifier.height(8.dp))
                    Box {
                        val dstAcc = ui.accounts.find { it.id == ui.destinationAccountId }
                        OutlinedTextField(
                            value = dstAcc?.let { "${it.name} (${String.format("%.2f", it.balance)} €)" } ?: "",
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = {
                                IconButton(onClick = { destinationDropdownExpanded = true }) {
                                    Icon(Icons.Filled.ArrowDropDown, "")
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = readOnlyTextFieldColors()
                        )
                        DropdownMenu(
                            expanded = destinationDropdownExpanded,
                            onDismissRequest = { destinationDropdownExpanded = false }
                        ) {
                            ui.accounts.forEach { acc ->
                                DropdownMenuItem(
                                    text = { Text("${acc.name} (${String.format("%.2f", acc.balance)} €)") },
                                    onClick = { state.updateDestination(acc.id); destinationDropdownExpanded = false }
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(24.dp))

                    // Amount
                    NeumorphicTextField(
                        value = ui.amount,
                        onValueChange = { state.updateAmount(it) },
                        label = "Montant",
                        placeholder = "0.00",
                        suffix = "EUR",
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = TransferColor
                        )
                    )

                    Spacer(Modifier.height(16.dp))

                    // Notes
                    NeumorphicTextField(
                        value = ui.notes,
                        onValueChange = { state.updateNotes(it) },
                        label = "Notes (optionnel)",
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Error/Success messages
                if (ui.errorMessage != null) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        ui.errorMessage!!,
                        style = MaterialTheme.typography.bodyMedium,
                        color = ExpenseColor
                    )
                }
                if (ui.successMessage != null) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        ui.successMessage!!,
                        style = MaterialTheme.typography.bodyMedium,
                        color = IncomeColor
                    )
                }

                Spacer(Modifier.height(24.dp))

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    NeumorphicButton(
                        text = "Annuler",
                        onClick = { navigationState.goBack() },
                        isPrimary = false
                    )
                    Spacer(Modifier.width(12.dp))
                    NeumorphicButton(
                        text = "Effectuer le transfert",
                        icon = Icons.Filled.SwapHoriz,
                        onClick = { state.executeTransfer { /* stays on page to show success */ } },
                        enabled = ui.amount.isNotBlank() && !ui.isSaving
                    )
                }
            }

            Spacer(Modifier.height(32.dp))
    }
}
