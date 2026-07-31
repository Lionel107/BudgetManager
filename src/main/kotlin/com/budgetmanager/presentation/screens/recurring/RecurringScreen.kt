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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

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
            val rtx = uiState.recurringTransactions.find { it.id == id }
            if (rtx != null) recurringRepo.update(rtx.copy(isActive = newActiveState))
        }
    }

    fun delete(id: Long) {
        val rtx = uiState.recurringTransactions.find { it.id == id }
        scope.launch {
            val koin = getKoin()
            val recurringRepo = koin.get<com.budgetmanager.data.repository.RecurringTransactionRepository>()
            recurringRepo.delete(id)
            if (rtx != null) {
                UndoBus.show(UndoableAction(
                    message = "Récurrent « ${rtx.title} » supprimé",
                    onUndo = { recurringRepo.create(rtx.copy(id = 0)) }
                ))
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
                uiState = uiState.copy(isProcessing = false, processMessage = "Échéances générées")
                delay(2500)
                uiState = uiState.copy(processMessage = null)
            } catch (e: Exception) {
                uiState = uiState.copy(isProcessing = false, processMessage = "Erreur : ${e.message}")
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

    Column(modifier = Modifier.fillMaxSize().background(NeumorphicBackground)) {
        // En-tête + actions
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Récurrents", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = NeumorphicTextPrimary, letterSpacing = (-0.5).sp)
                Text("Tes paiements automatiques", fontSize = 13.sp, color = NeumorphicTextSecondary)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                NeumorphicButton(
                    text = if (ui.isProcessing) "Traitement…" else "Générer",
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

        ui.processMessage?.let { msg ->
            Spacer(Modifier.height(10.dp))
            Text(
                msg,
                fontSize = 13.sp,
                color = if (msg.startsWith("Erreur")) ExpenseColor else IncomeColor,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(Modifier.height(16.dp))

        if (ui.recurringTransactions.isEmpty() && !ui.isLoading) {
            EmptyState(
                message = "Aucune transaction récurrente.\nAjoute tes paiements réguliers pour un suivi automatique.",
                icon = Icons.Filled.Repeat,
                actionText = "Ajouter",
                onAction = { navigationState.navigateTo(Screen.ADD_RECURRING) }
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(ui.recurringTransactions, key = { it.id }) { rtx ->
                    RecurringItem(
                        rtx = rtx,
                        dateFormatter = dateFormatter,
                        onToggle = { state.toggleActive(rtx.id, it) },
                        onEdit = { navigationState.navigateToEditRecurring(rtx.id) },
                        onDelete = { deleteConfirmId = rtx.id }
                    )
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

@Composable
private fun RecurringItem(
    rtx: RecurringTransaction,
    dateFormatter: DateTimeFormatter,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val typeColor = when (rtx.transactionType) {
        TransactionType.INCOME -> IncomeColor
        TransactionType.EXPENSE -> ExpenseColor
        TransactionType.TRANSFER -> TransferColor
    }
    NeumorphicCard(modifier = Modifier.fillMaxWidth()) {
        // Ligne 1 : icône · titre/fréquence · montant + compte à rebours
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(typeColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Repeat, null, tint = typeColor, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    rtx.title,
                    fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                    color = if (rtx.isActive) NeumorphicTextPrimary else NeumorphicTextTertiary, maxLines = 1
                )
                Text(
                    frequencyLabel(rtx.frequencyType) + (rtx.categoryName?.let { " · $it" } ?: ""),
                    fontSize = 12.sp, color = NeumorphicTextTertiary, maxLines = 1
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    (if (rtx.transactionType == TransactionType.INCOME) "+" else "−") +
                        String.format(java.util.Locale.FRANCE, "%,.2f €", rtx.amount),
                    fontFamily = FontFamily.Monospace, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                    color = if (rtx.transactionType == TransactionType.INCOME) IncomeColor else NeumorphicTextPrimary
                )
                Spacer(Modifier.height(6.dp))
                CountdownBadge(rtx.nextDueDate, rtx.isActive)
            }
        }

        Spacer(Modifier.height(12.dp))
        // Séparateur fin
        Box(Modifier.fillMaxWidth().height(1.dp).background(NeumorphicDarkShadow))
        Spacer(Modifier.height(8.dp))

        // Ligne 2 : prochaine échéance + actions
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Event, null, tint = NeumorphicTextTertiary, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                "Prochain : ${rtx.nextDueDate.format(dateFormatter)}",
                fontSize = 12.sp, color = NeumorphicTextSecondary
            )
            Spacer(Modifier.weight(1f))
            Text(
                if (rtx.isActive) "Actif" else "En pause",
                fontSize = 12.sp, color = if (rtx.isActive) NeumorphicPrimary else NeumorphicTextTertiary, fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.width(6.dp))
            Switch(
                checked = rtx.isActive,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedTrackColor = NeumorphicPrimary,
                    checkedThumbColor = Color.White,
                    uncheckedTrackColor = NeumorphicDepressed,
                    uncheckedBorderColor = NeumorphicDarkShadow
                )
            )
            IconButton(onClick = onEdit, modifier = Modifier.size(34.dp)) {
                Icon(Icons.Filled.Edit, "Modifier", tint = NeumorphicTextSecondary, modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(34.dp)) {
                Icon(Icons.Filled.Delete, "Supprimer", tint = ExpenseColor.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
            }
        }
    }
}

/** Badge « dans X jours » jusqu'à la prochaine échéance. */
@Composable
private fun CountdownBadge(nextDue: LocalDate, active: Boolean) {
    val days = ChronoUnit.DAYS.between(LocalDate.now(), nextDue)
    val (txt, color) = when {
        !active -> "en pause" to NeumorphicTextTertiary
        days < 0 -> "en retard" to ExpenseColor
        days == 0L -> "aujourd'hui" to NeumorphicBudgetWarning
        days == 1L -> "demain" to NeumorphicBudgetWarning
        days <= 3 -> "dans $days j" to NeumorphicBudgetWarning
        days <= 31 -> "dans $days j" to NeumorphicPrimary
        else -> "dans ${days / 7} sem." to NeumorphicTextSecondary
    }
    Row(
        modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(color.copy(alpha = 0.13f)).padding(horizontal = 9.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.Schedule, null, tint = color, modifier = Modifier.size(13.dp))
        Spacer(Modifier.width(4.dp))
        Text(txt, fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = color)
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
