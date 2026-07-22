package com.budgetmanager.presentation.screens.transactions

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.budgetmanager.domain.model.*
import com.budgetmanager.domain.model.TransactionSplit
import com.budgetmanager.presentation.components.*
import com.budgetmanager.presentation.components.KawaiiState
import com.budgetmanager.presentation.components.KawaiiEventType
import com.budgetmanager.presentation.navigation.NavigationState
import com.budgetmanager.presentation.navigation.Screen
import com.budgetmanager.presentation.theme.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import org.koin.core.context.GlobalContext.get as getKoin
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

data class SplitLine(
    val categoryId: Long? = null,
    val amount: String = "",
    val notes: String = ""
)

/**
 * Outcome of validating the splits against the main amount.
 * If non-empty, the form cannot be saved and the message is shown to the user.
 */
data class SplitsValidation(val errorMessage: String? = null) {
    val isValid: Boolean get() = errorMessage == null
}

data class AddTransactionUiState(
    val title: String = "",
    val amount: String = "",
    val transactionType: TransactionType = TransactionType.EXPENSE,
    val selectedCategoryId: Long? = null,
    val selectedAccountId: Long? = null,
    val date: LocalDate = LocalDate.now(),
    val notes: String = "",
    val tags: String = "",
    val categories: List<Category> = emptyList(),
    val accounts: List<Account> = emptyList(),
    val isEditing: Boolean = false,
    val isSaving: Boolean = false,
    val editTransactionId: Long? = null,
    val splitsEnabled: Boolean = false,
    val splits: List<SplitLine> = emptyList()
)

class AddTransactionScreenState(editId: Long?, private val templateId: Long? = null) {
    var uiState by mutableStateOf(AddTransactionUiState(editTransactionId = editId, isEditing = editId != null))
        private set

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    var fromTemplateId: Long? = templateId
        private set

    init { loadData() }

    private fun loadData() {
        scope.launch {
            val koin = getKoin()
            val categoryRepo = koin.get<com.budgetmanager.data.repository.CategoryRepository>()
            val accountRepo = koin.get<com.budgetmanager.data.repository.AccountRepository>()
            val transactionRepo = koin.get<com.budgetmanager.data.repository.TransactionRepository>()
            val templateRepo = koin.get<com.budgetmanager.data.repository.TemplateRepository>()

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

            // Load existing transaction for editing
            val editId = uiState.editTransactionId
            if (editId != null) {
                val tx = transactionRepo.getTransactionById(editId)
                if (tx != null) {
                    val splitRepo = koin.get<com.budgetmanager.data.repository.SplitRepository>()
                    val existingSplits = splitRepo.getSplitsForTransaction(editId)
                    uiState = uiState.copy(
                        title = tx.title,
                        amount = tx.amount.toPlainString(),
                        transactionType = tx.transactionType,
                        selectedCategoryId = tx.categoryId,
                        selectedAccountId = tx.accountId,
                        date = tx.date.toLocalDate(),
                        notes = tx.notes ?: "",
                        tags = tx.tags.joinToString(", "),
                        splitsEnabled = existingSplits.isNotEmpty(),
                        splits = existingSplits.map { s ->
                            SplitLine(
                                categoryId = s.categoryId,
                                amount = s.amount.toPlainString(),
                                notes = s.notes ?: ""
                            )
                        }
                    )
                }
            }

            // Pre-fill from template
            val tmplId = fromTemplateId
            if (tmplId != null && editId == null) {
                val template = templateRepo.getById(tmplId)
                if (template != null) {
                    uiState = uiState.copy(
                        title = template.name,
                        amount = template.defaultAmount?.toPlainString() ?: "",
                        transactionType = template.transactionType,
                        selectedCategoryId = template.categoryId
                    )
                    templateRepo.incrementUsage(tmplId)
                }
            }
        }
    }

    fun updateTitle(v: String) { uiState = uiState.copy(title = v) }
    fun updateAmount(v: String) { uiState = uiState.copy(amount = v.filter { it.isDigit() || it == '.' || it == ',' }) }
    fun updateType(v: TransactionType) { uiState = uiState.copy(transactionType = v) }
    fun updateCategory(id: Long?) { uiState = uiState.copy(selectedCategoryId = id) }
    fun updateAccount(id: Long?) { uiState = uiState.copy(selectedAccountId = id) }
    fun updateDate(d: LocalDate) { uiState = uiState.copy(date = d) }
    fun updateNotes(v: String) { uiState = uiState.copy(notes = v) }
    fun updateTags(v: String) { uiState = uiState.copy(tags = v) }

    // Splits
    fun toggleSplits() {
        uiState = if (!uiState.splitsEnabled) {
            // Initialize with one empty split when enabling
            uiState.copy(splitsEnabled = true, splits = listOf(SplitLine()))
        } else {
            uiState.copy(splitsEnabled = false, splits = emptyList())
        }
    }

    fun addSplit() {
        uiState = uiState.copy(splits = uiState.splits + SplitLine())
    }

    fun removeSplit(index: Int) {
        uiState = uiState.copy(splits = uiState.splits.filterIndexed { i, _ -> i != index })
    }

    fun updateSplitCategory(index: Int, categoryId: Long?) {
        uiState = uiState.copy(splits = uiState.splits.mapIndexed { i, s ->
            if (i == index) s.copy(categoryId = categoryId) else s
        })
    }

    fun updateSplitAmount(index: Int, amount: String) {
        val cleaned = amount.filter { it.isDigit() || it == '.' || it == ',' }
        uiState = uiState.copy(splits = uiState.splits.mapIndexed { i, s ->
            if (i == index) s.copy(amount = cleaned) else s
        })
    }

    fun updateSplitNotes(index: Int, notes: String) {
        uiState = uiState.copy(splits = uiState.splits.mapIndexed { i, s ->
            if (i == index) s.copy(notes = notes) else s
        })
    }

    /** Total of all split amounts as BigDecimal. */
    fun splitsTotal(): java.math.BigDecimal {
        return uiState.splits.fold(java.math.BigDecimal.ZERO) { acc, s ->
            acc.add(s.amount.replace(",", ".").toBigDecimalOrNull() ?: java.math.BigDecimal.ZERO)
        }
    }

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

    /**
     * Validate the splits section. Returns a non-null error message when the user
     * must fix something before saving.
     */
    fun validateSplits(): SplitsValidation {
        if (!uiState.splitsEnabled) return SplitsValidation()
        if (uiState.splits.isEmpty()) {
            return SplitsValidation("Active le decoupage avec au moins une ligne.")
        }
        // Each line must have a parseable positive amount
        uiState.splits.forEachIndexed { idx, s ->
            val sa = s.amount.replace(",", ".").toBigDecimalOrNull()
            if (sa == null || sa <= java.math.BigDecimal.ZERO) {
                return SplitsValidation("Ligne ${idx + 1} : montant invalide.")
            }
        }
        // Must balance to the main amount (tolerance 0.01 EUR)
        val mainAmount = uiState.amount.replace(",", ".").toBigDecimalOrNull() ?: java.math.BigDecimal.ZERO
        val splitsTotal = splitsTotal()
        val diff = mainAmount.subtract(splitsTotal).abs()
        if (diff > java.math.BigDecimal("0.01")) {
            val sign = if (mainAmount > splitsTotal) "manque" else "depasse"
            return SplitsValidation(
                "Le total des decoupages ($sign de ${String.format("%.2f", diff)} EUR) doit egaler le montant total."
            )
        }
        return SplitsValidation()
    }

    fun save(onSuccess: () -> Unit) {
        val amountStr = uiState.amount.replace(",", ".")
        val amount = amountStr.toBigDecimalOrNull() ?: return
        val accountId = uiState.selectedAccountId ?: return
        if (uiState.title.isBlank()) return

        // Block save if splits are not balanced
        val splitsCheck = validateSplits()
        if (!splitsCheck.isValid) return

        uiState = uiState.copy(isSaving = true)
        scope.launch {
            val koin = getKoin()
            val transactionRepo = koin.get<com.budgetmanager.data.repository.TransactionRepository>()
            val splitRepo = koin.get<com.budgetmanager.data.repository.SplitRepository>()
            val appPrefs = koin.get<com.budgetmanager.data.preferences.AppPreferences>()
            val baseTags = uiState.tags.split(",").map { it.trim() }.filter { it.isNotBlank() }
            // Auto-apply vacation tag if the transaction date is within the vacation period
            val tags = if (com.budgetmanager.util.VacationMode.isActive(appPrefs, uiState.date)) {
                val vacTag = appPrefs.vacationTag.trim().lowercase()
                if (vacTag.isNotBlank() && vacTag !in baseTags.map { it.lowercase() }) baseTags + vacTag
                else baseTags
            } else baseTags

            val tx = Transaction(
                id = uiState.editTransactionId ?: 0,
                title = uiState.title,
                amount = amount,
                transactionType = uiState.transactionType,
                accountId = accountId,
                categoryId = uiState.selectedCategoryId,
                date = uiState.date.atStartOfDay(),
                notes = uiState.notes.ifBlank { null },
                tags = tags
            )

            val savedId: Long
            if (uiState.isEditing) {
                val oldTx = transactionRepo.getTransactionById(tx.id)
                if (oldTx != null) {
                    transactionRepo.updateTransaction(oldTx, tx)
                }
                savedId = tx.id
            } else {
                savedId = transactionRepo.createTransaction(tx)
            }

            // Persist splits
            if (uiState.splitsEnabled && uiState.splits.isNotEmpty()) {
                val splits = uiState.splits.mapNotNull { s ->
                    val sa = s.amount.replace(",", ".").toBigDecimalOrNull()
                    if (sa != null && sa > java.math.BigDecimal.ZERO) {
                        TransactionSplit(
                            transactionId = savedId,
                            categoryId = s.categoryId,
                            amount = sa,
                            notes = s.notes.ifBlank { null }
                        )
                    } else null
                }
                splitRepo.setSplits(savedId, splits)
            } else {
                // Splits disabled — clean up any pre-existing splits
                splitRepo.deleteSplitsForTransaction(savedId)
            }

            withContext(Dispatchers.Main) {
                // Kawaii: trigger animation based on transaction type and amount tier
                val tier = com.budgetmanager.presentation.components.tierFor(amount)
                when (uiState.transactionType) {
                    TransactionType.INCOME -> KawaiiState.trigger(KawaiiEventType.INCOME_SAVED, tier = tier)
                    TransactionType.EXPENSE -> KawaiiState.trigger(KawaiiEventType.EXPENSE_SAVED, tier = tier)
                    else -> {}
                }
                uiState = uiState.copy(isSaving = false)
                onSuccess()
            }
        }
    }

    fun dispose() { scope.cancel() }
}

@Composable
fun AddTransactionScreen(navigationState: NavigationState) {
    val state = remember(navigationState.editTransactionId, navigationState.fromTemplateId) {
        AddTransactionScreenState(navigationState.editTransactionId, navigationState.fromTemplateId)
    }
    DisposableEffect(Unit) { onDispose { state.dispose() } }

    val ui = state.uiState
    val dateFormatter = remember { DateTimeFormatter.ofPattern("dd/MM/yyyy") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
            // Header with back button
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { navigationState.navigateTo(Screen.TRANSACTIONS) },
                    modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
                ) {
                    Icon(Icons.Filled.ArrowBack, "Retour", tint = NeumorphicTextSecondary)
                }
                Spacer(Modifier.width(8.dp))
                SectionHeader(title = if (ui.isEditing) "Modifier la transaction" else "Nouvelle transaction")
            }

            Spacer(Modifier.height(12.dp))

            // Vacation banner — tells the user the vacation tag will be auto-applied
            val appPrefs = remember { org.koin.core.context.GlobalContext.get().get<com.budgetmanager.data.preferences.AppPreferences>() }
            val vacationActive = remember(ui.date) {
                com.budgetmanager.util.VacationMode.isActive(appPrefs, ui.date)
            }
            if (vacationActive && ui.transactionType == TransactionType.EXPENSE) {
                NeumorphicCard(modifier = Modifier.fillMaxWidth(), elevation = 4.dp,
                    backgroundColor = TransferColor.copy(alpha = 0.10f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.BeachAccess, null, tint = TransferColor, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Mode vacances actif : le tag #${appPrefs.vacationTag} sera ajoute automatiquement.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TransferColor
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            // Transaction type selector
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TransactionType.entries.forEach { type ->
                    val isSelected = ui.transactionType == type
                    val (label, color) = when (type) {
                        TransactionType.INCOME -> "Revenu" to IncomeColor
                        TransactionType.EXPENSE -> "Depense" to ExpenseColor
                        TransactionType.TRANSFER -> "Transfert" to TransferColor
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
                            .pointerHoverIcon(PointerIcon.Hand)
                            .clickable {
                                if (type == TransactionType.TRANSFER) {
                                    navigationState.navigateTo(Screen.TRANSFER)
                                } else {
                                    state.updateType(type)
                                }
                            }
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.titleMedium,
                            color = if (isSelected) color else NeumorphicTextSecondary,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // Form in 2-column layout
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Left column
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    // Amount (large, prominent)
                    NeumorphicTextField(
                        value = ui.amount,
                        onValueChange = { state.updateAmount(it) },
                        label = "Montant",
                        placeholder = "0.00",
                        suffix = "EUR",
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = MaterialTheme.typography.headlineLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = when (ui.transactionType) {
                                TransactionType.INCOME -> IncomeColor
                                TransactionType.EXPENSE -> ExpenseColor
                                TransactionType.TRANSFER -> TransferColor
                            }
                        )
                    )

                    // Title
                    NeumorphicTextField(
                        value = ui.title,
                        onValueChange = { state.updateTitle(it) },
                        label = "Titre",
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Notes
                    NeumorphicTextField(
                        value = ui.notes,
                        onValueChange = { state.updateNotes(it) },
                        label = "Notes",
                        singleLine = false,
                        minLines = 3,
                        maxLines = 5,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Tags — smart input with autocomplete + suggestions based on title
                    TagInput(
                        tags = if (ui.tags.isBlank()) emptyList()
                               else ui.tags.split(",").map { it.trim() }.filter { it.isNotBlank() },
                        onTagsChange = { newTags -> state.updateTags(newTags.joinToString(", ")) },
                        transactionTitle = ui.title,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Right column
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    // Category — SearchableDropdown avec création rapide
                    val filteredCategories = ui.categories.filter {
                        when (ui.transactionType) {
                            TransactionType.INCOME -> it.categoryType == TransactionType.INCOME
                            TransactionType.EXPENSE -> it.categoryType == TransactionType.EXPENSE
                            TransactionType.TRANSFER -> true
                        }
                    }
                    SearchableDropdown(
                        label = "Categorie",
                        selectedId = ui.selectedCategoryId,
                        items = filteredCategories.map { it.id to it.name },
                        onSelect = { state.updateCategory(it) },
                        itemColor = { id -> parseColor(filteredCategories.find { it.id == id }?.color) },
                        onCreateNew = { name -> state.createAndSelectCategory(name) },
                        createNewLabel = "Creer la categorie",
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Account — SearchableDropdown simple (pas de création)
                    SearchableDropdown(
                        label = "Compte",
                        selectedId = ui.selectedAccountId,
                        items = ui.accounts.map { it.id to it.name },
                        onSelect = { state.updateAccount(it) },
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Date — saisie manuelle + calendrier
                    NeumorphicDatePicker(
                        date = ui.date,
                        onDateChange = { state.updateDate(it) },
                        label = "Date",
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Splits section — only useful for expenses (categorize a single bill across categories)
            if (ui.transactionType == TransactionType.EXPENSE) {
                Spacer(Modifier.height(20.dp))
                SplitsSection(ui, state)
            }

            Spacer(Modifier.height(32.dp))

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                NeumorphicButton(
                    text = "Annuler",
                    onClick = { navigationState.navigateTo(Screen.TRANSACTIONS) },
                    isPrimary = false
                )
                Spacer(Modifier.width(12.dp))
                NeumorphicButton(
                    text = if (ui.isEditing) "Modifier" else "Enregistrer",
                    icon = Icons.Filled.Save,
                    onClick = { state.save { navigationState.navigateTo(Screen.TRANSACTIONS) } },
                    enabled = ui.title.isNotBlank() && ui.amount.isNotBlank() && !ui.isSaving &&
                              state.validateSplits().isValid
                )
            }

            Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SplitsSection(
    ui: AddTransactionUiState,
    state: AddTransactionScreenState
) {
    val totalAmount = ui.amount.replace(",", ".").toBigDecimalOrNull() ?: java.math.BigDecimal.ZERO
    val splitsTotal = state.splitsTotal()
    val remaining = totalAmount.subtract(splitsTotal)
    val isBalanced = remaining.compareTo(java.math.BigDecimal.ZERO) == 0

    NeumorphicCard(modifier = Modifier.fillMaxWidth(), elevation = 4.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.CallSplit, null, tint = NeumorphicPrimary, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "Decouper cette transaction",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = NeumorphicTextPrimary
                )
                Text(
                    "Repartis le montant entre plusieurs categories (ex: courses + restaurant)",
                    style = MaterialTheme.typography.bodySmall,
                    color = NeumorphicTextTertiary
                )
            }
            Switch(
                checked = ui.splitsEnabled,
                onCheckedChange = { state.toggleSplits() },
                colors = SwitchDefaults.colors(
                    checkedTrackColor = NeumorphicPrimary,
                    checkedThumbColor = androidx.compose.ui.graphics.Color.White
                )
            )
        }

        if (ui.splitsEnabled) {
            Spacer(Modifier.height(12.dp))

            // Splits list
            ui.splits.forEachIndexed { index, split ->
                val filteredCategories = ui.categories.filter {
                    when (ui.transactionType) {
                        TransactionType.INCOME -> it.categoryType == TransactionType.INCOME
                        else -> it.categoryType == TransactionType.EXPENSE
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(modifier = Modifier.weight(2f)) {
                        SearchableDropdown(
                            label = if (index == 0) "Categorie" else "",
                            selectedId = split.categoryId,
                            items = filteredCategories.map { it.id to it.name },
                            onSelect = { state.updateSplitCategory(index, it) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        NeumorphicTextField(
                            value = split.amount,
                            onValueChange = { state.updateSplitAmount(index, it) },
                            label = if (index == 0) "Montant" else "",
                            suffix = "EUR",
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    IconButton(
                        onClick = { state.removeSplit(index) },
                        modifier = Modifier.size(32.dp).pointerHoverIcon(PointerIcon.Hand)
                    ) {
                        Icon(Icons.Filled.Close, "Supprimer cette ligne", tint = ExpenseColor.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                NeumorphicButton(
                    text = "Ajouter une ligne",
                    icon = Icons.Filled.Add,
                    onClick = { state.addSplit() },
                    isPrimary = false
                )

                // Balance indicator
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "Total reparti : ${String.format("%.2f", splitsTotal)} EUR",
                        style = MaterialTheme.typography.labelMedium,
                        color = NeumorphicTextSecondary
                    )
                    if (isBalanced) {
                        Text(
                            "Equilibre",
                            style = MaterialTheme.typography.labelMedium,
                            color = IncomeColor,
                            fontWeight = FontWeight.SemiBold
                        )
                    } else if (remaining > java.math.BigDecimal.ZERO) {
                        Text(
                            "Restant a repartir : ${String.format("%.2f", remaining)} EUR",
                            style = MaterialTheme.typography.labelMedium,
                            color = NeumorphicBudgetWarning,
                            fontWeight = FontWeight.SemiBold
                        )
                    } else {
                        Text(
                            "Depasse de ${String.format("%.2f", remaining.negate())} EUR",
                            style = MaterialTheme.typography.labelMedium,
                            color = ExpenseColor,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // Explicit validation error (blocks save)
            val validation = state.validateSplits()
            if (!validation.isValid && validation.errorMessage != null) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Error, null, tint = ExpenseColor, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        validation.errorMessage,
                        style = MaterialTheme.typography.labelMedium,
                        color = ExpenseColor
                    )
                }
            }
        }
    }
}
