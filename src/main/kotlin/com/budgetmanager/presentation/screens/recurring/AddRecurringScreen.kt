package com.budgetmanager.presentation.screens.recurring

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import com.budgetmanager.domain.model.*
import com.budgetmanager.presentation.components.*
import com.budgetmanager.presentation.navigation.NavigationState
import com.budgetmanager.presentation.navigation.Screen
import com.budgetmanager.presentation.theme.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import org.koin.core.context.GlobalContext.get as getKoin
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class AddRecurringUiState(
    val title: String = "",
    val amount: String = "",
    val transactionType: TransactionType = TransactionType.EXPENSE,
    val frequencyType: FrequencyType = FrequencyType.MONTHLY,
    val selectedCategoryId: Long? = null,
    val selectedAccountId: Long? = null,
    val selectedDestinationAccountId: Long? = null,
    val startDate: LocalDate = LocalDate.now(),
    val endDate: LocalDate? = null,
    val notes: String = "",
    val categories: List<Category> = emptyList(),
    val accounts: List<Account> = emptyList(),
    val isEditing: Boolean = false,
    val editId: Long? = null,
    val isSaving: Boolean = false
)

class AddRecurringScreenState(editId: Long?) {
    var uiState by mutableStateOf(AddRecurringUiState(editId = editId, isEditing = editId != null))
        private set

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    init { loadData() }

    private fun loadData() {
        scope.launch {
            val koin = getKoin()
            val categoryRepo = koin.get<com.budgetmanager.data.repository.CategoryRepository>()
            val accountRepo = koin.get<com.budgetmanager.data.repository.AccountRepository>()
            val recurringRepo = koin.get<com.budgetmanager.data.repository.RecurringTransactionRepository>()

            launch {
                categoryRepo.getAllCategories().collectLatest { cats ->
                    uiState = uiState.copy(categories = cats)
                }
            }
            launch {
                accountRepo.getAllAccounts().collectLatest { accs ->
                    uiState = uiState.copy(accounts = accs)
                    if (uiState.selectedAccountId == null && accs.isNotEmpty()) {
                        uiState = uiState.copy(selectedAccountId = accs.first().id)
                    }
                }
            }

            // Load existing recurring transaction for editing
            val eid = uiState.editId
            if (eid != null) {
                recurringRepo.getAll().collectLatest { allRtxs ->
                    val rtx = allRtxs.find { it.id == eid }
                    if (rtx != null) {
                        uiState = uiState.copy(
                            title = rtx.title,
                            amount = rtx.amount.toPlainString(),
                            transactionType = rtx.transactionType,
                            frequencyType = rtx.frequencyType,
                            selectedCategoryId = rtx.categoryId,
                            selectedAccountId = rtx.accountId,
                            selectedDestinationAccountId = rtx.destinationAccountId,
                            startDate = rtx.startDate,
                            endDate = rtx.endDate,
                            notes = rtx.notes ?: ""
                        )
                    }
                }
            }
        }
    }

    fun updateTitle(v: String) { uiState = uiState.copy(title = v) }
    fun updateAmount(v: String) { uiState = uiState.copy(amount = v.filter { it.isDigit() || it == '.' || it == ',' }) }
    fun updateType(v: TransactionType) { uiState = uiState.copy(transactionType = v) }
    fun updateFrequency(v: FrequencyType) { uiState = uiState.copy(frequencyType = v) }
    fun updateCategory(id: Long?) { uiState = uiState.copy(selectedCategoryId = id) }
    fun updateAccount(id: Long?) { uiState = uiState.copy(selectedAccountId = id) }
    fun updateDestinationAccount(id: Long?) { uiState = uiState.copy(selectedDestinationAccountId = id) }
    fun updateNotes(v: String) { uiState = uiState.copy(notes = v) }
    fun updateStartDate(d: java.time.LocalDate) { uiState = uiState.copy(startDate = d) }

    fun createAndSelectCategory(name: String) {
        scope.launch {
            val koin = getKoin()
            val categoryRepo = koin.get<com.budgetmanager.data.repository.CategoryRepository>()
            val newId = categoryRepo.createCategory(
                com.budgetmanager.domain.model.Category(
                    name = name,
                    categoryType = uiState.transactionType,
                    color = "#6C63FF"
                )
            )
            uiState = uiState.copy(selectedCategoryId = newId)
        }
    }

    fun save(onSuccess: () -> Unit) {
        val amount = uiState.amount.replace(",", ".").toBigDecimalOrNull() ?: return
        val accountId = uiState.selectedAccountId ?: return
        if (uiState.title.isBlank()) return
        // For TRANSFER, destination is required
        if (uiState.transactionType == TransactionType.TRANSFER && uiState.selectedDestinationAccountId == null) return

        uiState = uiState.copy(isSaving = true)
        scope.launch {
            val koin = getKoin()
            val recurringRepo = koin.get<com.budgetmanager.data.repository.RecurringTransactionRepository>()

            val rtx = RecurringTransaction(
                id = uiState.editId ?: 0,
                title = uiState.title,
                amount = amount,
                transactionType = uiState.transactionType,
                frequencyType = uiState.frequencyType,
                accountId = accountId,
                categoryId = uiState.selectedCategoryId,
                destinationAccountId = if (uiState.transactionType == TransactionType.TRANSFER) uiState.selectedDestinationAccountId else null,
                startDate = uiState.startDate,
                endDate = uiState.endDate,
                nextDueDate = uiState.startDate,
                notes = uiState.notes.ifBlank { null }
            )

            if (uiState.isEditing) {
                recurringRepo.update(rtx)
            } else {
                recurringRepo.create(rtx)
            }

            withContext(Dispatchers.Main) {
                uiState = uiState.copy(isSaving = false)
                onSuccess()
            }
        }
    }

    fun dispose() { scope.cancel() }
}

@Composable
fun AddRecurringScreen(navigationState: NavigationState) {
    val state = remember(navigationState.editRecurringId) {
        AddRecurringScreenState(navigationState.editRecurringId)
    }
    DisposableEffect(Unit) { onDispose { state.dispose() } }

    val ui = state.uiState
    val dateFormatter = remember { DateTimeFormatter.ofPattern("dd/MM/yyyy") }
    var frequencyDropdownExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { navigationState.navigateTo(Screen.RECURRING) }) {
                    Icon(Icons.Filled.ArrowBack, "Retour", tint = NeumorphicTextSecondary)
                }
                Spacer(Modifier.width(8.dp))
                SectionHeader(title = if (ui.isEditing) "Modifier la récurrence" else "Nouvelle récurrence")
            }

            Spacer(Modifier.height(12.dp))

            // Type selector
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf(TransactionType.EXPENSE, TransactionType.INCOME).forEach { type ->
                    val isSelected = ui.transactionType == type
                    val (label, color) = when (type) {
                        TransactionType.INCOME -> "Revenu" to IncomeColor
                        TransactionType.EXPENSE -> "Dépense" to ExpenseColor
                        else -> "" to TransferColor
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .then(
                                if (isSelected)
                                    Modifier.neumorphicPressed(depth = 4.dp, borderRadius = 12.dp, backgroundColor = color.copy(alpha = 0.12f))
                                else
                                    Modifier.neumorphicShadow(elevation = 5.dp, borderRadius = 12.dp)
                            )
                            .clickable { state.updateType(type) }
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(label, style = MaterialTheme.typography.titleMedium,
                            color = if (isSelected) color else NeumorphicTextSecondary,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                // Left column
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    NeumorphicTextField(
                        value = ui.title,
                        onValueChange = { state.updateTitle(it) },
                        label = "Titre",
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    NeumorphicTextField(
                        value = ui.amount,
                        onValueChange = { state.updateAmount(it) },
                        label = "Montant (EUR)",
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Frequency
                    Box {
                        OutlinedTextField(
                            value = when (ui.frequencyType) {
                                FrequencyType.DAILY -> "Quotidien"
                                FrequencyType.WEEKLY -> "Hebdomadaire"
                                FrequencyType.BI_WEEKLY -> "Bi-mensuel"
                                FrequencyType.MONTHLY -> "Mensuel"
                                FrequencyType.QUARTERLY -> "Trimestriel"
                                FrequencyType.YEARLY -> "Annuel"
                            },
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Fréquence") },
                            trailingIcon = {
                                IconButton(onClick = { frequencyDropdownExpanded = true }) {
                                    Icon(Icons.Filled.ArrowDropDown, "")
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = readOnlyTextFieldColors()
                        )
                        DropdownMenu(
                            expanded = frequencyDropdownExpanded,
                            onDismissRequest = { frequencyDropdownExpanded = false }
                        ) {
                            FrequencyType.entries.forEach { ft ->
                                DropdownMenuItem(
                                    text = {
                                        Text(when (ft) {
                                            FrequencyType.DAILY -> "Quotidien"
                                            FrequencyType.WEEKLY -> "Hebdomadaire"
                                            FrequencyType.BI_WEEKLY -> "Bi-mensuel"
                                            FrequencyType.MONTHLY -> "Mensuel"
                                            FrequencyType.QUARTERLY -> "Trimestriel"
                                            FrequencyType.YEARLY -> "Annuel"
                                        })
                                    },
                                    onClick = { state.updateFrequency(ft); frequencyDropdownExpanded = false }
                                )
                            }
                        }
                    }

                    NeumorphicTextField(
                        value = ui.notes,
                        onValueChange = { state.updateNotes(it) },
                        label = "Notes",
                        singleLine = false,
                        minLines = 2,
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Right column
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    // Category — SearchableDropdown avec création rapide
                    val filteredCats = ui.categories.filter {
                        it.categoryType == ui.transactionType || ui.transactionType == TransactionType.TRANSFER
                    }
                    SearchableDropdown(
                        label = "Categorie",
                        selectedId = ui.selectedCategoryId,
                        items = filteredCats.map { it.id to it.name },
                        onSelect = { state.updateCategory(it) },
                        itemColor = { id -> parseColor(filteredCats.find { it.id == id }?.color) },
                        onCreateNew = { name -> state.createAndSelectCategory(name) },
                        createNewLabel = "Creer la categorie",
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Account — SearchableDropdown simple
                    SearchableDropdown(
                        label = if (ui.transactionType == TransactionType.TRANSFER) "Compte source" else "Compte",
                        selectedId = ui.selectedAccountId,
                        items = ui.accounts.map { it.id to it.name },
                        onSelect = { state.updateAccount(it) },
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Destination account (TRANSFER only)
                    if (ui.transactionType == TransactionType.TRANSFER) {
                        SearchableDropdown(
                            label = "Compte destination",
                            selectedId = ui.selectedDestinationAccountId,
                            items = ui.accounts.filter { it.id != ui.selectedAccountId }.map { it.id to it.name },
                            onSelect = { state.updateDestinationAccount(it) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // Start date — saisie manuelle + calendrier
                    NeumorphicDatePicker(
                        date = ui.startDate,
                        onDateChange = { state.updateStartDate(it) },
                        label = "Date de debut",
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(Modifier.height(32.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                NeumorphicButton(
                    text = "Annuler",
                    onClick = { navigationState.navigateTo(Screen.RECURRING) },
                    isPrimary = false
                )
                Spacer(Modifier.width(12.dp))
                NeumorphicButton(
                    text = if (ui.isEditing) "Modifier" else "Créer",
                    icon = Icons.Filled.Save,
                    onClick = { state.save { navigationState.navigateTo(Screen.RECURRING) } },
                    enabled = ui.title.isNotBlank() && ui.amount.isNotBlank() && !ui.isSaving
                )
            }

            Spacer(Modifier.height(24.dp))
    }
}
