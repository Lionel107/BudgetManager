package com.budgetmanager.presentation.screens.recurring

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.budgetmanager.domain.model.RecurringTransaction
import com.budgetmanager.domain.model.TransactionType
import com.budgetmanager.domain.model.FrequencyType
import com.budgetmanager.presentation.components.*
import com.budgetmanager.presentation.navigation.NavigationState
import com.budgetmanager.presentation.navigation.Screen
import com.budgetmanager.presentation.theme.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import org.koin.core.context.GlobalContext.get as getKoin
import java.time.format.DateTimeFormatter

data class RecurringUiState(
    val recurringTransactions: List<RecurringTransaction> = emptyList(),
    val isLoading: Boolean = true,
    val isProcessing: Boolean = false,
    val processMessage: String? = null
)

class RecurringScreenState {
    var uiState by mutableStateOf(RecurringUiState())
        private set

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    init { loadData() }

    private fun loadData() {
        scope.launch {
            try {
                val koin = getKoin()
                val recurringRepo = koin.get<com.budgetmanager.data.repository.RecurringTransactionRepository>()
                recurringRepo.getAll().collectLatest { txs ->
                    uiState = uiState.copy(recurringTransactions = txs, isLoading = false)
                }
            } catch (e: Exception) {
                uiState = uiState.copy(isLoading = false)
            }
        }
    }

    fun toggleActive(id: Long, newActiveState: Boolean) {
        scope.launch {
            val koin = getKoin()
            val recurringRepo = koin.get<com.budgetmanager.data.repository.RecurringTransactionRepository>()
            // Find the recurring transaction and update its active state
            val allTxs = uiState.recurringTransactions
            val rtx = allTxs.find { it.id == id }
            if (rtx != null) {
                recurringRepo.update(rtx.copy(isActive = newActiveState))
            }
        }
    }

    fun delete(id: Long) {
        val rtx = uiState.recurringTransactions.find { it.id == id }
        scope.launch {
            val koin = getKoin()
            val recurringRepo = koin.get<com.budgetmanager.data.repository.RecurringTransactionRepository>()
            recurringRepo.delete(id)
            if (rtx != null) {
                com.budgetmanager.presentation.components.UndoBus.show(
                    com.budgetmanager.presentation.components.UndoableAction(
                        message = "Recurrent \"${rtx.title}\" supprime",
                        onUndo = { recurringRepo.create(rtx.copy(id = 0)) }
                    )
                )
            }
        }
    }

    fun processNow() {
        if (uiState.isProcessing) return
        uiState = uiState.copy(isProcessing = true, processMessage = null)
        scope.launch {
            try {
                val koin = getKoin()
                val recurringRepo = koin.get<com.budgetmanager.data.repository.RecurringTransactionRepository>()
                recurringRepo.processRecurringTransactions()
                uiState = uiState.copy(isProcessing = false, processMessage = "Echeances generees")
                kotlinx.coroutines.delay(2500)
                uiState = uiState.copy(processMessage = null)
            } catch (e: Exception) {
                uiState = uiState.copy(isProcessing = false, processMessage = "Erreur: ${e.message}")
            }
        }
    }

    fun dispose() { scope.cancel() }
}

@Composable
fun RecurringScreen(navigationState: NavigationState) {
    val state = remember { RecurringScreenState() }
    DisposableEffect(Unit) { onDispose { state.dispose() } }

    val ui = state.uiState
    val dateFormatter = remember { DateTimeFormatter.ofPattern("dd/MM/yyyy") }
    var deleteConfirmId by remember { mutableStateOf<Long?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SectionHeader(title = "Transactions récurrentes")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    NeumorphicButton(
                        text = if (ui.isProcessing) "Traitement..." else "Generer maintenant",
                        icon = Icons.Filled.PlayArrow,
                        onClick = { state.processNow() },
                        isPrimary = false,
                        enabled = !ui.isProcessing
                    )
                    NeumorphicButton(
                        text = "Ajouter",
                        icon = Icons.Filled.Add,
                        onClick = { navigationState.navigateTo(Screen.ADD_RECURRING) }
                    )
                }
            }

            // Process status message
            ui.processMessage?.let { msg ->
                Spacer(Modifier.height(8.dp))
                Text(
                    text = msg,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (msg.startsWith("Erreur")) ExpenseColor else IncomeColor,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(Modifier.height(16.dp))

            if (ui.recurringTransactions.isEmpty() && !ui.isLoading) {
                EmptyState(
                    message = "Aucune transaction récurrente configurée.\nAjoutez vos paiements réguliers pour un suivi automatique.",
                    icon = Icons.Filled.Repeat,
                    actionText = "Ajouter",
                    onAction = { navigationState.navigateTo(Screen.ADD_RECURRING) }
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(ui.recurringTransactions, key = { it.id }) { rtx ->
                        NeumorphicCard(modifier = Modifier.fillMaxWidth(), elevation = 6.dp) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Type indicator
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(
                                            when (rtx.transactionType) {
                                                TransactionType.INCOME -> IncomeColor.copy(alpha = 0.12f)
                                                TransactionType.EXPENSE -> ExpenseColor.copy(alpha = 0.12f)
                                                TransactionType.TRANSFER -> TransferColor.copy(alpha = 0.12f)
                                            }
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Repeat,
                                        contentDescription = null,
                                        tint = when (rtx.transactionType) {
                                            TransactionType.INCOME -> IncomeColor
                                            TransactionType.EXPENSE -> ExpenseColor
                                            TransactionType.TRANSFER -> TransferColor
                                        },
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                Spacer(Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = rtx.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (rtx.isActive) NeumorphicTextPrimary else NeumorphicTextTertiary
                                    )
                                    Text(
                                        text = "${frequencyLabel(rtx.frequencyType)} • Prochain: ${rtx.nextDueDate.format(dateFormatter)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = NeumorphicTextTertiary
                                    )
                                    if (rtx.categoryName != null) {
                                        Text(
                                            text = rtx.categoryName!!,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = NeumorphicTextTertiary
                                        )
                                    }
                                }

                                CurrencyAmount(
                                    amount = if (rtx.transactionType == TransactionType.EXPENSE) rtx.amount.negate() else rtx.amount,
                                    isIncome = rtx.transactionType == TransactionType.INCOME,
                                    style = MaterialTheme.typography.titleMedium,
                                    showSign = true
                                )

                                Spacer(Modifier.width(12.dp))

                                // Active toggle
                                Switch(
                                    checked = rtx.isActive,
                                    onCheckedChange = { state.toggleActive(rtx.id, it) },
                                    colors = SwitchDefaults.colors(
                                        checkedTrackColor = NeumorphicPrimary,
                                        checkedThumbColor = androidx.compose.ui.graphics.Color.White
                                    )
                                )

                                // Edit
                                IconButton(
                                    onClick = { navigationState.navigateToEditRecurring(rtx.id) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Filled.Edit, "Modifier", tint = NeumorphicTextSecondary, modifier = Modifier.size(18.dp))
                                }

                                // Delete
                                IconButton(
                                    onClick = { deleteConfirmId = rtx.id },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Filled.Delete, "Supprimer", tint = ExpenseColor.copy(alpha = 0.6f), modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }
    }

    deleteConfirmId?.let { id ->
        ConfirmDialog(
            title = "Supprimer",
            message = "Supprimer cette transaction récurrente ?",
            onConfirm = { state.delete(id); deleteConfirmId = null },
            onDismiss = { deleteConfirmId = null }
        )
    }
}

private fun frequencyLabel(type: FrequencyType): String = when (type) {
    FrequencyType.DAILY -> "Quotidien"
    FrequencyType.WEEKLY -> "Hebdomadaire"
    FrequencyType.BI_WEEKLY -> "Bi-mensuel"
    FrequencyType.MONTHLY -> "Mensuel"
    FrequencyType.QUARTERLY -> "Trimestriel"
    FrequencyType.YEARLY -> "Annuel"
}
