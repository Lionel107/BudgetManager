package com.budgetmanager.presentation.screens.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.budgetmanager.domain.model.Account
import com.budgetmanager.domain.model.Category
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
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private const val PAGE_SIZE = 50

enum class SortOption(val label: String) {
    DATE_DESC("Date (recent)"),
    DATE_ASC("Date (ancien)"),
    AMOUNT_DESC("Montant (decroissant)"),
    AMOUNT_ASC("Montant (croissant)"),
    TITLE("Titre A-Z")
}

data class TransactionListUiState(
    val transactions: List<Transaction> = emptyList(),
    val filteredTransactions: List<Transaction> = emptyList(),
    val accounts: List<Account> = emptyList(),
    val categories: List<Category> = emptyList(),
    val searchQuery: String = "",
    val selectedFilter: TransactionType? = null,
    val sortOption: SortOption = SortOption.DATE_DESC,
    // Advanced filters
    val showAdvancedFilters: Boolean = false,
    val dateFrom: LocalDate? = null,
    val dateTo: LocalDate? = null,
    val minAmount: BigDecimal? = null,
    val maxAmount: BigDecimal? = null,
    val selectedCategoryIds: Set<Long> = emptySet(),
    val selectedAccountIds: Set<Long> = emptySet(),
    val selectedTags: Set<String> = emptySet(),
    val isLoading: Boolean = true,
    val displayedCount: Int = PAGE_SIZE
)

class TransactionListScreenState {
    var uiState by mutableStateOf(TransactionListUiState())
        private set

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    init { loadData() }

    private fun loadData() {
        scope.launch {
            try {
                val koin = getKoin()
                val transactionRepo = koin.get<com.budgetmanager.data.repository.TransactionRepository>()
                val accountRepo = koin.get<com.budgetmanager.data.repository.AccountRepository>()
                val categoryRepo = koin.get<com.budgetmanager.data.repository.CategoryRepository>()

                launch {
                    accountRepo.getAllAccounts().collectLatest { accs ->
                        uiState = uiState.copy(accounts = accs)
                    }
                }
                launch {
                    categoryRepo.getAllCategories().collectLatest { cats ->
                        uiState = uiState.copy(categories = cats)
                    }
                }
                transactionRepo.getAllTransactions().collectLatest { txs ->
                    uiState = uiState.copy(transactions = txs, isLoading = false)
                    applyFilters()
                }
            } catch (e: Exception) {
                uiState = uiState.copy(isLoading = false)
            }
        }
    }

    fun updateSearch(query: String) {
        uiState = uiState.copy(searchQuery = query)
        applyFilters()
    }

    fun setFilter(type: TransactionType?) {
        uiState = uiState.copy(selectedFilter = if (uiState.selectedFilter == type) null else type)
        applyFilters()
    }

    fun setSort(option: SortOption) {
        uiState = uiState.copy(sortOption = option)
        applyFilters()
    }

    fun toggleAdvancedFilters() {
        uiState = uiState.copy(showAdvancedFilters = !uiState.showAdvancedFilters)
    }

    fun setDateFrom(date: LocalDate?) { uiState = uiState.copy(dateFrom = date); applyFilters() }
    fun setDateTo(date: LocalDate?) { uiState = uiState.copy(dateTo = date); applyFilters() }
    fun setMinAmount(amount: BigDecimal?) { uiState = uiState.copy(minAmount = amount); applyFilters() }
    fun setMaxAmount(amount: BigDecimal?) { uiState = uiState.copy(maxAmount = amount); applyFilters() }

    fun toggleCategory(id: Long) {
        val newSet = uiState.selectedCategoryIds.toMutableSet()
        if (id in newSet) newSet.remove(id) else newSet.add(id)
        uiState = uiState.copy(selectedCategoryIds = newSet)
        applyFilters()
    }

    fun toggleAccount(id: Long) {
        val newSet = uiState.selectedAccountIds.toMutableSet()
        if (id in newSet) newSet.remove(id) else newSet.add(id)
        uiState = uiState.copy(selectedAccountIds = newSet)
        applyFilters()
    }

    fun loadMore() {
        uiState = uiState.copy(displayedCount = uiState.displayedCount + PAGE_SIZE)
    }

    fun resetAdvancedFilters() {
        uiState = uiState.copy(
            dateFrom = null, dateTo = null,
            minAmount = null, maxAmount = null,
            selectedCategoryIds = emptySet(),
            selectedAccountIds = emptySet(),
            selectedTags = emptySet()
        )
        applyFilters()
    }

    private fun applyFilters() {
        var filtered = uiState.transactions
        val query = uiState.searchQuery
        if (query.isNotBlank()) {
            filtered = filtered.filter {
                it.title.contains(query, ignoreCase = true) ||
                    it.categoryName?.contains(query, ignoreCase = true) == true ||
                    it.notes?.contains(query, ignoreCase = true) == true ||
                    it.tags.any { tag -> tag.contains(query, ignoreCase = true) }
            }
        }
        uiState.selectedFilter?.let { type ->
            filtered = filtered.filter { it.transactionType == type }
        }
        // Date range
        uiState.dateFrom?.let { d ->
            filtered = filtered.filter { !it.date.toLocalDate().isBefore(d) }
        }
        uiState.dateTo?.let { d ->
            filtered = filtered.filter { !it.date.toLocalDate().isAfter(d) }
        }
        // Amount range
        uiState.minAmount?.let { min ->
            filtered = filtered.filter { it.amount >= min }
        }
        uiState.maxAmount?.let { max ->
            filtered = filtered.filter { it.amount <= max }
        }
        // Categories
        if (uiState.selectedCategoryIds.isNotEmpty()) {
            filtered = filtered.filter { it.categoryId in uiState.selectedCategoryIds }
        }
        // Accounts
        if (uiState.selectedAccountIds.isNotEmpty()) {
            filtered = filtered.filter { it.accountId in uiState.selectedAccountIds }
        }
        // Sort
        filtered = when (uiState.sortOption) {
            SortOption.DATE_DESC -> filtered.sortedByDescending { it.date }
            SortOption.DATE_ASC -> filtered.sortedBy { it.date }
            SortOption.AMOUNT_DESC -> filtered.sortedByDescending { it.amount }
            SortOption.AMOUNT_ASC -> filtered.sortedBy { it.amount }
            SortOption.TITLE -> filtered.sortedBy { it.title.lowercase() }
        }
        uiState = uiState.copy(filteredTransactions = filtered)
    }

    fun deleteTransaction(id: Long) {
        scope.launch {
            val koin = getKoin()
            val transactionRepo = koin.get<com.budgetmanager.data.repository.TransactionRepository>()
            val tx = transactionRepo.getTransactionById(id)
            if (tx != null) {
                transactionRepo.deleteTransaction(tx)
                com.budgetmanager.presentation.components.UndoBus.show(
                    com.budgetmanager.presentation.components.UndoableAction(
                        message = "Transaction \"${tx.title}\" supprimee",
                        onUndo = { transactionRepo.createTransaction(tx) }
                    )
                )
            }
        }
    }

    fun dispose() { scope.cancel() }
}

@Composable
fun TransactionListScreen(navigationState: NavigationState) {
    val state = remember { TransactionListScreenState() }
    DisposableEffect(Unit) { onDispose { state.dispose() } }

    val ui = state.uiState
    val dateFormatter = remember { DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm") }
    var deleteConfirmId by remember { mutableStateOf<Long?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
            // Header — aligné en haut comme Accueil
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Transactions",
                    style = MaterialTheme.typography.headlineMedium,
                    color = NeumorphicTextPrimary,
                    fontWeight = FontWeight.Bold
                )
                NeumorphicButton(
                    text = "Nouvelle transaction",
                    icon = Icons.Filled.Add,
                    onClick = { navigationState.navigateTo(Screen.ADD_TRANSACTION) }
                )
            }

            // Search
            SearchBar(
                query = ui.searchQuery,
                onQueryChange = { state.updateSearch(it) },
                placeholder = "Rechercher une transaction...",
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(12.dp))

            // Filter chips + sort + advanced toggle
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                FilterChip(
                    label = "Tout",
                    isSelected = ui.selectedFilter == null,
                    onClick = { state.setFilter(null) }
                )
                FilterChip(
                    label = "Revenus",
                    isSelected = ui.selectedFilter == TransactionType.INCOME,
                    onClick = { state.setFilter(TransactionType.INCOME) }
                )
                FilterChip(
                    label = "Dépenses",
                    isSelected = ui.selectedFilter == TransactionType.EXPENSE,
                    onClick = { state.setFilter(TransactionType.EXPENSE) }
                )
                FilterChip(
                    label = "Transferts",
                    isSelected = ui.selectedFilter == TransactionType.TRANSFER,
                    onClick = { state.setFilter(TransactionType.TRANSFER) }
                )
                Spacer(Modifier.weight(1f))
                // Sort dropdown
                var sortMenuExpanded by remember { mutableStateOf(false) }
                Box {
                    NeumorphicButton(
                        text = "Tri: ${ui.sortOption.label}",
                        icon = Icons.Filled.Sort,
                        onClick = { sortMenuExpanded = true },
                        isPrimary = false
                    )
                    DropdownMenu(
                        expanded = sortMenuExpanded,
                        onDismissRequest = { sortMenuExpanded = false }
                    ) {
                        SortOption.entries.forEach { opt ->
                            DropdownMenuItem(
                                text = { Text(opt.label) },
                                onClick = { state.setSort(opt); sortMenuExpanded = false }
                            )
                        }
                    }
                }
                Spacer(Modifier.width(8.dp))
                NeumorphicButton(
                    text = if (ui.showAdvancedFilters) "Masquer filtres" else "Filtres +",
                    icon = Icons.Filled.FilterList,
                    onClick = { state.toggleAdvancedFilters() },
                    isPrimary = false
                )
            }

            // Advanced filters panel
            if (ui.showAdvancedFilters) {
                Spacer(Modifier.height(12.dp))
                AdvancedFiltersPanel(ui, state)
            }

            // Active filters summary
            val activeFilterCount = listOfNotNull(
                ui.dateFrom, ui.dateTo, ui.minAmount, ui.maxAmount
            ).size + ui.selectedCategoryIds.size + ui.selectedAccountIds.size
            if (activeFilterCount > 0) {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "$activeFilterCount filtre(s) actif(s) - ${ui.filteredTransactions.size} resultat(s)",
                        style = MaterialTheme.typography.labelSmall,
                        color = NeumorphicPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "Reinitialiser",
                        style = MaterialTheme.typography.labelSmall,
                        color = ExpenseColor,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .pointerHoverIcon(PointerIcon.Hand)
                            .clickable { state.resetAdvancedFilters() }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            if (ui.filteredTransactions.isEmpty() && !ui.isLoading) {
                EmptyState(
                    message = if (ui.searchQuery.isNotBlank()) "Aucun résultat pour \"${ui.searchQuery}\""
                    else "Aucune transaction trouvée.",
                    icon = Icons.Filled.ReceiptLong,
                    actionText = "Ajouter une transaction",
                    onAction = { navigationState.navigateTo(Screen.ADD_TRANSACTION) }
                )
            } else {
                NeumorphicCard(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    elevation = 6.dp
                ) {
                    val visible = ui.filteredTransactions.take(ui.displayedCount)
                    val hasMore = ui.filteredTransactions.size > ui.displayedCount

                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(visible, key = { it.id }) { tx ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(modifier = Modifier.weight(1f)) {
                                    TransactionItem(
                                        title = tx.title,
                                        amount = if (tx.transactionType == TransactionType.EXPENSE) tx.amount.negate() else tx.amount,
                                        category = tx.categoryName,
                                        date = tx.date.format(dateFormatter),
                                        isIncome = tx.transactionType == TransactionType.INCOME,
                                        categoryColor = parseColor(tx.categoryColor),
                                        onClick = { navigationState.navigateToEditTransaction(tx.id) }
                                    )
                                }
                                IconButton(
                                    onClick = { deleteConfirmId = tx.id },
                                    modifier = Modifier
                                        .padding(end = 8.dp)
                                        .size(36.dp)
                                        .pointerHoverIcon(PointerIcon.Hand)
                                ) {
                                    Icon(
                                        Icons.Filled.Delete,
                                        contentDescription = "Supprimer",
                                        tint = NeumorphicTextTertiary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                color = NeumorphicTextTertiary.copy(alpha = 0.3f)
                            )
                        }

                        // Load more footer
                        if (hasMore) {
                            item {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    NeumorphicButton(
                                        text = "Afficher plus (${ui.filteredTransactions.size - ui.displayedCount} restants)",
                                        icon = Icons.Filled.ExpandMore,
                                        onClick = { state.loadMore() },
                                        isPrimary = false
                                    )
                                }
                            }
                        }
                    }
                }
            }
    }

    deleteConfirmId?.let { id ->
        ConfirmDialog(
            title = "Supprimer la transaction",
            message = "Êtes-vous sûr de vouloir supprimer cette transaction ?",
            onConfirm = { state.deleteTransaction(id); deleteConfirmId = null },
            onDismiss = { deleteConfirmId = null }
        )
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun AdvancedFiltersPanel(
    ui: TransactionListUiState,
    state: TransactionListScreenState
) {
    NeumorphicCard(modifier = Modifier.fillMaxWidth(), elevation = 4.dp) {
        Text("Filtres avances", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = NeumorphicTextPrimary)
        Spacer(Modifier.height(12.dp))

        // Date range
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(modifier = Modifier.weight(1f)) {
                NullableDatePicker(
                    label = "A partir du",
                    date = ui.dateFrom,
                    onChange = { state.setDateFrom(it) }
                )
            }
            Box(modifier = Modifier.weight(1f)) {
                NullableDatePicker(
                    label = "Jusqu'au",
                    date = ui.dateTo,
                    onChange = { state.setDateTo(it) }
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // Amount range
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NeumorphicTextField(
                value = ui.minAmount?.toPlainString() ?: "",
                onValueChange = { v ->
                    state.setMinAmount(v.replace(",", ".").toBigDecimalOrNull())
                },
                label = "Montant min",
                suffix = "EUR",
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            NeumorphicTextField(
                value = ui.maxAmount?.toPlainString() ?: "",
                onValueChange = { v ->
                    state.setMaxAmount(v.replace(",", ".").toBigDecimalOrNull())
                },
                label = "Montant max",
                suffix = "EUR",
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(12.dp))

        // Categories multi-select
        if (ui.categories.isNotEmpty()) {
            Text("Categories", style = MaterialTheme.typography.labelLarge, color = NeumorphicTextSecondary)
            Spacer(Modifier.height(6.dp))
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                ui.categories.forEach { cat ->
                    FilterChip(
                        label = cat.name,
                        isSelected = cat.id in ui.selectedCategoryIds,
                        onClick = { state.toggleCategory(cat.id) }
                    )
                }
            }
        }

        // Accounts multi-select
        if (ui.accounts.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text("Comptes", style = MaterialTheme.typography.labelLarge, color = NeumorphicTextSecondary)
            Spacer(Modifier.height(6.dp))
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                ui.accounts.forEach { acc ->
                    FilterChip(
                        label = acc.name,
                        isSelected = acc.id in ui.selectedAccountIds,
                        onClick = { state.toggleAccount(acc.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun NullableDatePicker(
    label: String,
    date: LocalDate?,
    onChange: (LocalDate?) -> Unit
) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = NeumorphicTextSecondary, modifier = Modifier.padding(bottom = 6.dp, start = 4.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(Modifier.weight(1f)) {
                NeumorphicDatePicker(
                    date = date ?: LocalDate.now(),
                    onDateChange = { onChange(it) },
                    label = ""
                )
            }
            if (date != null) {
                IconButton(onClick = { onChange(null) }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.Close, "Effacer", tint = NeumorphicTextTertiary, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

