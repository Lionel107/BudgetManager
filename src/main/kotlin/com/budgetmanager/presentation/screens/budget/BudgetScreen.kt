package com.budgetmanager.presentation.screens.budget

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.budgetmanager.domain.model.*
import com.budgetmanager.presentation.components.*
import com.budgetmanager.presentation.components.KawaiiState
import com.budgetmanager.presentation.components.KawaiiEventType
import com.budgetmanager.presentation.components.KawaiiBudgetSafeMessage
import com.budgetmanager.presentation.components.isRoseTheme
import com.budgetmanager.presentation.navigation.NavigationState
import com.budgetmanager.presentation.theme.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import org.koin.core.context.GlobalContext.get as getKoin
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth

data class BudgetUiState(
    val budgets: List<BudgetWithStatus> = emptyList(),
    val categories: List<Category> = emptyList(),
    val totalBudgetLimit: BigDecimal = BigDecimal.ZERO,
    val totalSpent: BigDecimal = BigDecimal.ZERO,
    val savingsGoal: Double = 0.0,
    val selectedTab: Int = 0,
    val showAddDialog: Boolean = false,
    val editingBudget: Budget? = null,
    val isLoading: Boolean = true,
    val allTransactions: List<com.budgetmanager.domain.model.Transaction> = emptyList(),
    val allAccounts: List<com.budgetmanager.domain.model.Account> = emptyList(),
    /** Used when opening the Add dialog with a category pre-selected from the suggestions. */
    val preselectedCategoryId: Long? = null
)

class BudgetScreenState {
    var uiState by mutableStateOf(BudgetUiState())
        private set

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    init { loadData() }

    private fun loadData() {
        scope.launch {
            val koin = getKoin()
            val budgetRepo = koin.get<com.budgetmanager.data.repository.BudgetRepository>()
            val categoryRepo = koin.get<com.budgetmanager.data.repository.CategoryRepository>()
            val appPrefs = koin.get<com.budgetmanager.data.preferences.AppPreferences>()

            launch {
                val now = YearMonth.now()
                val periodStart = now.atDay(1)
                val periodEnd = now.atEndOfMonth()
                budgetRepo.getBudgetsWithSpending(periodStart, periodEnd).collectLatest { statusList ->
                    val budgetWithStatusList = statusList.map { s ->
                        BudgetWithStatus(
                            budget = Budget(
                                id = s.budgetId,
                                categoryId = s.categoryId,
                                categoryName = s.categoryName,
                                categoryColor = s.categoryColor,
                                periodType = BudgetPeriodType.MONTHLY,
                                limit = s.budgetLimit
                            ),
                            spent = s.spent,
                            remaining = s.remaining,
                            percentage = s.percentage,
                            state = s.state
                        )
                    }
                    val totalLimit = budgetWithStatusList.fold(BigDecimal.ZERO) { acc, b -> acc.add(b.budget.limit) }
                    val totalSpent = budgetWithStatusList.fold(BigDecimal.ZERO) { acc, b -> acc.add(b.spent) }
                    uiState = uiState.copy(
                        budgets = budgetWithStatusList,
                        totalBudgetLimit = totalLimit,
                        totalSpent = totalSpent,
                        isLoading = false
                    )
                }
            }

            launch {
                categoryRepo.getAllCategories().collectLatest { cats ->
                    uiState = uiState.copy(categories = cats.filter { it.categoryType == TransactionType.EXPENSE })
                }
            }

            launch {
                val goal = appPrefs.savingsGoal.toDouble()
                uiState = uiState.copy(savingsGoal = goal)
            }

            // Load transactions + accounts for the advice engine
            launch {
                val transactionRepo = koin.get<com.budgetmanager.data.repository.TransactionRepository>()
                transactionRepo.getAllTransactions().collectLatest { txs ->
                    uiState = uiState.copy(allTransactions = txs)
                }
            }
            launch {
                val accountRepo = koin.get<com.budgetmanager.data.repository.AccountRepository>()
                accountRepo.getAllAccounts().collectLatest { accs ->
                    uiState = uiState.copy(allAccounts = accs)
                }
            }
        }
    }

    fun selectTab(tab: Int) { uiState = uiState.copy(selectedTab = tab) }
    fun showAddDialog() { uiState = uiState.copy(showAddDialog = true, editingBudget = null, preselectedCategoryId = null) }
    fun showEditDialog(budget: Budget) { uiState = uiState.copy(editingBudget = budget, showAddDialog = true) }
    fun showAddDialogForCategory(categoryId: Long) {
        uiState = uiState.copy(showAddDialog = true, editingBudget = null, preselectedCategoryId = categoryId)
    }
    fun hideDialog() { uiState = uiState.copy(showAddDialog = false, editingBudget = null) }

    fun saveBudget(categoryId: Long, limit: BigDecimal, periodType: BudgetPeriodType) {
        scope.launch {
            val koin = getKoin()
            val budgetRepo = koin.get<com.budgetmanager.data.repository.BudgetRepository>()
            val category = uiState.categories.find { it.id == categoryId } ?: return@launch

            val editing = uiState.editingBudget
            if (editing != null) {
                budgetRepo.updateBudget(editing.copy(categoryId = categoryId, limit = limit, periodType = periodType))
            } else {
                budgetRepo.createBudget(
                    Budget(
                        categoryId = categoryId,
                        categoryName = category.name,
                        categoryColor = category.color,
                        limit = limit,
                        periodType = periodType
                    )
                )
            }
            hideDialog()
        }
    }

    /** Quick-create a budget for a category with a suggested limit (typically last-month spending). */
    fun createBudgetSuggestion(categoryId: Long, limit: BigDecimal) {
        scope.launch {
            val koin = getKoin()
            val budgetRepo = koin.get<com.budgetmanager.data.repository.BudgetRepository>()
            val category = uiState.categories.find { it.id == categoryId } ?: return@launch
            budgetRepo.createBudget(
                Budget(
                    categoryId = categoryId,
                    categoryName = category.name,
                    categoryColor = category.color,
                    limit = limit,
                    periodType = BudgetPeriodType.MONTHLY
                )
            )
        }
    }

    fun deleteBudget(id: Long) {
        val budgetWithStatus = uiState.budgets.find { it.budget.id == id }
        scope.launch {
            val koin = getKoin()
            val budgetRepo = koin.get<com.budgetmanager.data.repository.BudgetRepository>()
            budgetRepo.deleteBudget(id)
            if (budgetWithStatus != null) {
                val b = budgetWithStatus.budget
                UndoBus.show(UndoableAction(
                    message = "Budget \"${b.categoryName}\" supprime",
                    // Preserve the original ID so any downstream references stay valid
                    onUndo = { budgetRepo.restoreBudgetWithId(b) }
                ))
            }
        }
    }

    fun dispose() { scope.cancel() }
}

@Composable
fun BudgetScreen(navigationState: NavigationState) {
    val state = remember { BudgetScreenState() }
    DisposableEffect(Unit) { onDispose { state.dispose() } }

    val ui = state.uiState
    var deleteConfirmId by remember { mutableStateOf<Long?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
            // Header — aligné en haut comme Accueil
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Budgets",
                    style = MaterialTheme.typography.headlineMedium,
                    color = NeumorphicTextPrimary,
                    fontWeight = FontWeight.Bold
                )
                NeumorphicButton(
                    text = "Nouveau budget",
                    icon = Icons.Filled.Add,
                    onClick = { state.showAddDialog() }
                )
            }

            // Savings goal display
            if (ui.savingsGoal > 0) {
                NeumorphicCard(modifier = Modifier.fillMaxWidth(), elevation = 6.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Savings, null, tint = IncomeColor, modifier = Modifier.size(28.dp))
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Objectif d'épargne mensuel", style = MaterialTheme.typography.labelLarge, color = NeumorphicTextSecondary)
                            Text(
                                "${String.format("%.0f", ui.savingsGoal)} €",
                                style = MaterialTheme.typography.titleLarge,
                                color = IncomeColor,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            // Tab bar
            val tabs = listOf("Vue d'ensemble", "Graphiques", "Conseils")
            TabRow(
                selectedTabIndex = ui.selectedTab,
                containerColor = NeumorphicElevated,
                contentColor = NeumorphicPrimary,
                modifier = Modifier.clip(RoundedCornerShape(12.dp))
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = ui.selectedTab == index,
                        onClick = { state.selectTab(index) },
                        text = { Text(title, fontWeight = if (ui.selectedTab == index) FontWeight.Bold else FontWeight.Normal) }
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            when (ui.selectedTab) {
                0 -> BudgetOverviewTab(ui, state, onDelete = { deleteConfirmId = it })
                1 -> BudgetChartsTab(ui)
                2 -> BudgetAdviceTab(ui)
            }
    }

    // Add/Edit Dialog
    if (ui.showAddDialog) {
        BudgetFormDialog(
            budget = ui.editingBudget,
            categories = ui.categories,
            allTransactions = ui.allTransactions,
            preselectedCategoryId = ui.preselectedCategoryId,
            onSave = { catId, limit, period -> state.saveBudget(catId, limit, period) },
            onDismiss = { state.hideDialog() }
        )
    }

    deleteConfirmId?.let { id ->
        ConfirmDialog(
            title = "Supprimer le budget",
            message = "Êtes-vous sûr de vouloir supprimer ce budget ?",
            onConfirm = { state.deleteBudget(id); deleteConfirmId = null },
            onDismiss = { deleteConfirmId = null }
        )
    }
}

@Composable
private fun BudgetOverviewTab(
    ui: BudgetUiState,
    state: BudgetScreenState,
    onDelete: (Long) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Overall budget card
        NeumorphicCard(modifier = Modifier.fillMaxWidth(), elevation = 8.dp) {
            Text("Budget global", style = MaterialTheme.typography.titleMedium, color = NeumorphicTextPrimary)
            Spacer(Modifier.height(12.dp))
            BudgetProgressBar(
                spent = ui.totalSpent.toFloat(),
                limit = ui.totalBudgetLimit.toFloat(),
                height = 12.dp
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Dépensé", style = MaterialTheme.typography.bodySmall, color = NeumorphicTextTertiary)
                    CurrencyAmount(amount = ui.totalSpent, style = MaterialTheme.typography.titleMedium, color = ExpenseColor)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Budget total", style = MaterialTheme.typography.bodySmall, color = NeumorphicTextTertiary)
                    CurrencyAmount(amount = ui.totalBudgetLimit, style = MaterialTheme.typography.titleMedium)
                }
            }
        }

        // State indicators
        val safeCount = ui.budgets.count { it.state == BudgetState.SAFE }
        val warningCount = ui.budgets.count { it.state == BudgetState.WARNING }
        val alertCount = ui.budgets.count { it.state == BudgetState.ALERT }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            StateIndicatorChip("Sain: $safeCount", NeumorphicBudgetSafe, Modifier.weight(1f))
            StateIndicatorChip("Attention: $warningCount", NeumorphicBudgetWarning, Modifier.weight(1f))
            StateIndicatorChip("Alerte: $alertCount", NeumorphicBudgetAlert, Modifier.weight(1f))
        }

        // Kawaii: trigger celebration or alert (rose theme only)
        // Only fire ONCE per month per event to avoid spam every time the screen opens.
        if (ui.budgets.isNotEmpty() && isRoseTheme()) {
            LaunchedEffect(ui.budgets) {
                val koin = org.koin.core.context.GlobalContext.get()
                val appPrefs = koin.get<com.budgetmanager.data.preferences.AppPreferences>()
                val currentYm = java.time.YearMonth.now().toString() // e.g. "2026-04"

                if (safeCount == ui.budgets.size) {
                    if (appPrefs.lastAllBudgetsSafeYearMonth != currentYm) {
                        KawaiiState.trigger(KawaiiEventType.ALL_BUDGETS_SAFE)
                        appPrefs.lastAllBudgetsSafeYearMonth = currentYm
                    }
                } else if (alertCount > 0) {
                    val alertCategory = ui.budgets.first { it.state == BudgetState.ALERT }.budget.categoryName
                    if (appPrefs.lastBudgetAlertFor(alertCategory) != currentYm) {
                        KawaiiState.trigger(KawaiiEventType.BUDGET_ALERT, alertCategory)
                        appPrefs.setLastBudgetAlertFor(alertCategory, currentYm)
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // Budget list by category
        if (ui.budgets.isEmpty() && !ui.isLoading) {
            EmptyState(
                message = "Aucun budget défini.\nCréez votre premier budget pour suivre vos dépenses.",
                icon = Icons.Filled.Wallet,
                actionText = "Créer un budget",
                onAction = { state.showAddDialog() }
            )
        } else {
            ui.budgets.forEach { budgetWithStatus ->
                BudgetCategoryCard(
                    budgetWithStatus = budgetWithStatus,
                    onEdit = { state.showEditDialog(budgetWithStatus.budget) },
                    onDelete = { onDelete(budgetWithStatus.budget.id) }
                )
            }

            // Suggestions section: categories without a budget but with last-month expenses
            BudgetSuggestionsSection(ui, state)
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun BudgetSuggestionsSection(ui: BudgetUiState, state: BudgetScreenState) {
    val budgetedCategoryIds = remember(ui.budgets) { ui.budgets.map { it.budget.categoryId }.toSet() }
    val lastMonth = remember { java.time.YearMonth.now().minusMonths(1) }

    // Group last-month expenses by category for categories without a budget
    val suggestions = remember(ui.allTransactions, budgetedCategoryIds, ui.categories) {
        ui.allTransactions
            .asSequence()
            .filter {
                it.transactionType == com.budgetmanager.domain.model.TransactionType.EXPENSE &&
                it.categoryId != null &&
                it.categoryId !in budgetedCategoryIds &&
                java.time.YearMonth.from(it.date) == lastMonth
            }
            .groupBy { it.categoryId!! }
            .map { (catId, txs) ->
                val total = txs.fold(BigDecimal.ZERO) { acc, t -> acc.add(t.amount) }
                val catName = ui.categories.find { it.id == catId }?.name ?: txs.first().categoryName ?: "Categorie"
                Triple(catId, catName, total)
            }
            .filter { it.third > BigDecimal.ZERO }
            .sortedByDescending { it.third }
            .take(5)
    }

    // Categories without budget but with no recent activity either
    val categoriesWithoutBudget = ui.categories.filter {
        it.id !in budgetedCategoryIds && it.id !in suggestions.map { s -> s.first }.toSet()
    }

    if (suggestions.isNotEmpty()) {
        Spacer(Modifier.height(20.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.AutoAwesome, null, tint = NeumorphicPrimary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                "Suggestions basees sur le mois dernier",
                style = MaterialTheme.typography.titleMedium,
                color = NeumorphicTextPrimary,
                fontWeight = FontWeight.SemiBold
            )
        }
        Text(
            "Categories ou tu as depense sans avoir de budget. Cree-en un en un clic.",
            style = MaterialTheme.typography.bodySmall,
            color = NeumorphicTextTertiary,
            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
        )
        suggestions.forEach { (catId, catName, total) ->
            NeumorphicCard(modifier = Modifier.fillMaxWidth(), elevation = 5.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(catName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = NeumorphicTextPrimary)
                        Text(
                            "Mois dernier : ${String.format("%.2f", total)} EUR",
                            style = MaterialTheme.typography.bodySmall,
                            color = NeumorphicTextSecondary
                        )
                    }
                    NeumorphicButton(
                        text = "Creer (${String.format("%.0f", total)})",
                        icon = Icons.Filled.Add,
                        onClick = {
                            state.createBudgetSuggestion(catId, total)
                        },
                        isPrimary = false
                    )
                }
            }
        }
    }

    // Categories WITHOUT historical spending — they're invisible from the suggestions
    // above, so we explicitly list them here so users know they CAN create a budget too.
    if (categoriesWithoutBudget.isNotEmpty()) {
        Spacer(Modifier.height(20.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.MoreHoriz, null, tint = NeumorphicTextSecondary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                "Autres categories (sans depense recente)",
                style = MaterialTheme.typography.titleMedium,
                color = NeumorphicTextPrimary,
                fontWeight = FontWeight.SemiBold
            )
        }
        Text(
            "Tu peux quand meme leur definir un budget si tu prevois des depenses.",
            style = MaterialTheme.typography.bodySmall,
            color = NeumorphicTextTertiary,
            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
        )
        androidx.compose.foundation.layout.FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            categoriesWithoutBudget.take(20).forEach { cat ->
                FilterChip(
                    label = "+ ${cat.name}",
                    isSelected = false,
                    onClick = { state.showAddDialogForCategory(cat.id) }
                )
            }
        }
    }
}

@Composable
private fun StateIndicatorChip(label: String, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(color.copy(alpha = 0.18f))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = color, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun BudgetCategoryCard(
    budgetWithStatus: BudgetWithStatus,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val b = budgetWithStatus
    val stateColor = when (b.state) {
        BudgetState.SAFE -> NeumorphicBudgetSafe
        BudgetState.WARNING -> NeumorphicBudgetWarning
        BudgetState.ALERT -> NeumorphicBudgetAlert
    }

    NeumorphicCard(modifier = Modifier.fillMaxWidth(), elevation = 6.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(12.dp).clip(RoundedCornerShape(3.dp)).background(parseColor(b.budget.categoryColor))
            )
            Spacer(Modifier.width(10.dp))
            Text(b.budget.categoryName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text(
                when (b.state) {
                    BudgetState.SAFE -> "Sain"
                    BudgetState.WARNING -> "Attention"
                    BudgetState.ALERT -> "Alerte"
                },
                style = MaterialTheme.typography.labelMedium,
                color = stateColor,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = onEdit, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Filled.Edit, "Modifier", tint = NeumorphicTextTertiary, modifier = Modifier.size(16.dp))
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Filled.Delete, "Supprimer", tint = ExpenseColor.copy(alpha = 0.6f), modifier = Modifier.size(16.dp))
            }
        }
        Spacer(Modifier.height(10.dp))
        BudgetProgressBar(spent = b.spent.toFloat(), limit = b.budget.limit.toFloat())
        Spacer(Modifier.height(6.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Restant: ${String.format("%.2f", b.remaining)} €", style = MaterialTheme.typography.bodySmall, color = stateColor)
            Text("${b.transactionCount} transaction(s)", style = MaterialTheme.typography.bodySmall, color = NeumorphicTextTertiary)
        }
        // Kawaii: cute message for safe budgets
        if (b.state == BudgetState.SAFE) {
            KawaiiBudgetSafeMessage()
        }
    }
}

@Composable
private fun BudgetChartsTab(ui: BudgetUiState) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Répartition des dépenses par catégorie", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

        if (ui.budgets.isEmpty()) {
            EmptyState(message = "Aucune donnée à afficher.", icon = Icons.Filled.BarChart)
        } else {
            // Category spending bars
            NeumorphicCard(modifier = Modifier.fillMaxWidth()) {
                ui.budgets.forEach { b ->
                    val percentage = if (b.budget.limit > BigDecimal.ZERO) b.spent.toFloat() / b.budget.limit.toFloat() else 0f

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(parseColor(b.budget.categoryColor)))
                        Spacer(Modifier.width(10.dp))
                        Text(b.budget.categoryName, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(120.dp), color = NeumorphicTextPrimary)
                        Box(
                            modifier = Modifier.weight(1f).height(20.dp).clip(RoundedCornerShape(4.dp)).background(NeumorphicDepressed)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(percentage.coerceIn(0f, 1f))
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(parseColor(b.budget.categoryColor))
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Text("${(percentage * 100).toInt()}%", style = MaterialTheme.typography.labelMedium, color = NeumorphicTextSecondary, modifier = Modifier.width(40.dp), textAlign = TextAlign.End)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Text("Progression des budgets", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            NeumorphicCard(modifier = Modifier.fillMaxWidth()) {
                ui.budgets.forEach { b ->
                    Column(modifier = Modifier.padding(vertical = 6.dp)) {
                        Text(b.budget.categoryName, style = MaterialTheme.typography.bodyMedium, color = NeumorphicTextPrimary)
                        Spacer(Modifier.height(4.dp))
                        BudgetProgressBar(spent = b.spent.toFloat(), limit = b.budget.limit.toFloat())
                    }
                }
            }
        }
    }
}

@Composable
private fun BudgetAdviceTab(ui: BudgetUiState) {
    // derivedStateOf ensures the rule engine re-runs whenever any of the inputs change,
    // without rerunning when unrelated UI state changes (cheaper than recomputing every recomposition).
    val localAdvices by remember {
        derivedStateOf {
            com.budgetmanager.util.AdviceEngine().analyze(
                accounts = ui.allAccounts,
                transactions = ui.allTransactions,
                budgets = ui.budgets,
                savingsGoal = java.math.BigDecimal(ui.savingsGoal.toString())
            )
        }
    }

    var aiAdvices by remember { mutableStateOf<List<com.budgetmanager.util.FinancialAdvice>>(emptyList()) }
    var isLoadingAi by remember { mutableStateOf(false) }
    var aiError by remember { mutableStateOf<String?>(null) }
    var hasGeminiKey by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Load cached Gemini advice on first composition + check key
    LaunchedEffect(Unit) {
        try {
            val koin = org.koin.core.context.GlobalContext.get()
            val appPrefs = koin.get<com.budgetmanager.data.preferences.AppPreferences>()
            hasGeminiKey = appPrefs.geminiApiKey.isNotBlank()
            val ym = java.time.YearMonth.now().toString()
            if (appPrefs.cachedGeminiAdviceYearMonth == ym && appPrefs.cachedGeminiAdvice.isNotBlank()) {
                aiAdvices = parseCachedAdvice(appPrefs.cachedGeminiAdvice)
            }
        } catch (_: Exception) {}
    }

    fun refreshAi() {
        if (isLoadingAi) return
        scope.launch {
            isLoadingAi = true
            aiError = null
            try {
                val koin = org.koin.core.context.GlobalContext.get()
                val appPrefs = koin.get<com.budgetmanager.data.preferences.AppPreferences>()
                val key = appPrefs.geminiApiKey
                if (key.isBlank()) {
                    aiError = "Ajoute ta cle API Gemini dans les Parametres."
                    isLoadingAi = false
                    return@launch
                }
                val result = withContext(Dispatchers.IO) {
                    com.budgetmanager.util.GeminiAdviceService().fetchAdvice(
                        apiKey = key,
                        accounts = ui.allAccounts,
                        transactions = ui.allTransactions,
                        budgets = ui.budgets,
                        savingsGoal = java.math.BigDecimal(ui.savingsGoal.toString())
                    )
                }
                if (result.isEmpty()) {
                    aiError = "Pas de reponse exploitable de Gemini. Cle valide ? Connexion OK ?"
                } else {
                    aiAdvices = result
                    appPrefs.cachedGeminiAdvice = serializeAdvice(result)
                    appPrefs.cachedGeminiAdviceYearMonth = java.time.YearMonth.now().toString()
                }
            } catch (e: Exception) {
                aiError = "Erreur: ${e.message}"
            } finally {
                isLoadingAi = false
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Conseils financiers personnalises", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(8.dp))
            Text(
                "${localAdvices.size + aiAdvices.size} conseil(s)",
                style = MaterialTheme.typography.labelSmall,
                color = NeumorphicTextTertiary
            )
        }

        // Gemini refresh row
        if (hasGeminiKey) {
            NeumorphicCard(modifier = Modifier.fillMaxWidth(), elevation = 4.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.AutoAwesome, null, tint = NeumorphicPrimary, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Conseils IA (Gemini)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            if (aiAdvices.isNotEmpty()) "${aiAdvices.size} conseils generes par IA. Cache du mois."
                            else "Clique pour generer des conseils personnalises avec Gemini.",
                            style = MaterialTheme.typography.bodySmall,
                            color = NeumorphicTextTertiary
                        )
                    }
                    NeumorphicButton(
                        text = if (isLoadingAi) "..." else "Rafraichir IA",
                        icon = Icons.Filled.Refresh,
                        onClick = { refreshAi() },
                        enabled = !isLoadingAi,
                        isPrimary = false
                    )
                }
                if (aiError != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(aiError!!, style = MaterialTheme.typography.bodySmall, color = ExpenseColor)
                }
            }
        } else {
            Text(
                "Astuce : ajoute une cle API Gemini dans les Parametres pour obtenir des conseils plus pousses par IA.",
                style = MaterialTheme.typography.bodySmall,
                color = NeumorphicTextTertiary
            )
        }

        // AI advice cards (when available)
        if (aiAdvices.isNotEmpty()) {
            Text(
                "✨ Suggestions IA",
                style = MaterialTheme.typography.titleSmall,
                color = NeumorphicPrimary,
                fontWeight = FontWeight.SemiBold
            )
            aiAdvices.forEach { advice ->
                SmartAdviceCard(advice, sourceLabel = "Gemini")
            }
        }

        // Local rule-based advice
        if (localAdvices.isNotEmpty()) {
            Text(
                "Analyse locale",
                style = MaterialTheme.typography.titleSmall,
                color = NeumorphicTextSecondary,
                fontWeight = FontWeight.SemiBold
            )
            localAdvices.forEach { advice ->
                SmartAdviceCard(advice, sourceLabel = "Regle locale")
            }
        }

        if (localAdvices.isEmpty() && aiAdvices.isEmpty()) {
            NeumorphicCard(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.CheckCircle, null, tint = IncomeColor, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "Tout va bien ! Aucun point d'attention detecte.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = NeumorphicTextPrimary
                    )
                }
            }
        }
    }
}

/** Simple JSON-like serialization for caching AI advice locally. */
private fun serializeAdvice(advices: List<com.budgetmanager.util.FinancialAdvice>): String {
    return advices.joinToString("|") { a ->
        "${a.level.name}~${a.title.replace("~", " ").replace("|", " ")}~${a.message.replace("~", " ").replace("|", " ")}"
    }
}

private fun parseCachedAdvice(raw: String): List<com.budgetmanager.util.FinancialAdvice> {
    if (raw.isBlank()) return emptyList()
    return raw.split("|").mapNotNull { entry ->
        val parts = entry.split("~")
        if (parts.size < 3) return@mapNotNull null
        val level = runCatching { com.budgetmanager.util.AdviceLevel.valueOf(parts[0]) }
            .getOrDefault(com.budgetmanager.util.AdviceLevel.INFO)
        com.budgetmanager.util.FinancialAdvice(
            title = parts[1],
            message = parts[2],
            level = level,
            category = com.budgetmanager.util.AdviceCategory.GENERAL
        )
    }
}

@Composable
private fun SmartAdviceCard(
    advice: com.budgetmanager.util.FinancialAdvice,
    sourceLabel: String? = null
) {
    val (icon, color) = when (advice.level) {
        com.budgetmanager.util.AdviceLevel.CRITICAL -> Icons.Filled.Warning to NeumorphicBudgetAlert
        com.budgetmanager.util.AdviceLevel.WARNING -> Icons.Filled.ErrorOutline to NeumorphicBudgetWarning
        com.budgetmanager.util.AdviceLevel.GOOD -> Icons.Filled.CheckCircle to NeumorphicBudgetSafe
        com.budgetmanager.util.AdviceLevel.INFO -> Icons.Filled.Lightbulb to NeumorphicPrimary
    }

    NeumorphicCard(modifier = Modifier.fillMaxWidth(), elevation = 6.dp) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(icon, null, tint = color, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    advice.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = color,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    advice.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = NeumorphicTextSecondary
                )
            }
            if (sourceLabel != null) {
                Text(
                    sourceLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = NeumorphicTextTertiary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(NeumorphicDepressed.copy(alpha = 0.6f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun BudgetFormDialog(
    budget: Budget?,
    categories: List<Category>,
    allTransactions: List<com.budgetmanager.domain.model.Transaction> = emptyList(),
    preselectedCategoryId: Long? = null,
    onSave: (Long, BigDecimal, BudgetPeriodType) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedCategoryId by remember {
        mutableStateOf(budget?.categoryId ?: preselectedCategoryId ?: categories.firstOrNull()?.id)
    }
    var limitText by remember { mutableStateOf(budget?.limit?.toPlainString() ?: "") }
    var periodType by remember { mutableStateOf(budget?.periodType ?: BudgetPeriodType.MONTHLY) }
    var periodDropdownExpanded by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Compute previous-month spending for selected category to offer a suggestion
    val suggestedAmount: BigDecimal? = remember(selectedCategoryId, allTransactions) {
        val catId = selectedCategoryId ?: return@remember null
        val lastMonth = java.time.YearMonth.now().minusMonths(1)
        val total = allTransactions
            .filter {
                it.transactionType == com.budgetmanager.domain.model.TransactionType.EXPENSE &&
                it.categoryId == catId &&
                java.time.YearMonth.from(it.date) == lastMonth
            }
            .fold(BigDecimal.ZERO) { acc, t -> acc.add(t.amount) }
        if (total > BigDecimal.ZERO) total else null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (budget != null) "Modifier le budget" else "Nouveau budget",
                style = MaterialTheme.typography.headlineMedium,
                color = NeumorphicTextPrimary
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Category — SearchableDropdown avec création rapide
                SearchableDropdown(
                    label = "Categorie",
                    selectedId = selectedCategoryId,
                    items = categories.map { it.id to it.name },
                    onSelect = { selectedCategoryId = it },
                    itemColor = { id -> parseColor(categories.find { it.id == id }?.color) },
                    onCreateNew = { name ->
                        scope.launch {
                            val koin = org.koin.core.context.GlobalContext.get()
                            val categoryRepo = koin.get<com.budgetmanager.data.repository.CategoryRepository>()
                            val newId = categoryRepo.createCategory(
                                Category(name = name, categoryType = TransactionType.EXPENSE, color = "#6C63FF")
                            )
                            selectedCategoryId = newId
                        }
                    },
                    createNewLabel = "Creer la categorie",
                    modifier = Modifier.fillMaxWidth()
                )

                // Limit
                NeumorphicTextField(
                    value = limitText,
                    onValueChange = { limitText = it.filter { c -> c.isDigit() || c == '.' || c == ',' } },
                    label = "Limite (EUR)",
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Suggestion based on last month's spending
                if (suggestedAmount != null && budget == null) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.AutoAwesome, null, tint = NeumorphicPrimary, modifier = Modifier.size(16.dp))
                        Text(
                            "Mois dernier : ${String.format("%.2f", suggestedAmount)} EUR depenses dans cette categorie",
                            style = MaterialTheme.typography.bodySmall,
                            color = NeumorphicTextSecondary,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NeumorphicButton(
                            text = "Reprendre ${String.format("%.0f", suggestedAmount)}",
                            onClick = { limitText = String.format("%.2f", suggestedAmount) },
                            isPrimary = false
                        )
                        val withMargin = suggestedAmount.multiply(BigDecimal("1.10"))
                        NeumorphicButton(
                            text = "+10% (${String.format("%.0f", withMargin)})",
                            onClick = { limitText = String.format("%.2f", withMargin) },
                            isPrimary = false
                        )
                        val tighter = suggestedAmount.multiply(BigDecimal("0.90"))
                        NeumorphicButton(
                            text = "-10% (${String.format("%.0f", tighter)})",
                            onClick = { limitText = String.format("%.2f", tighter) },
                            isPrimary = false
                        )
                    }
                }

                // Period
                Box {
                    OutlinedTextField(
                        value = when (periodType) {
                            BudgetPeriodType.WEEKLY -> "Hebdomadaire"
                            BudgetPeriodType.MONTHLY -> "Mensuel"
                            BudgetPeriodType.YEARLY -> "Annuel"
                            BudgetPeriodType.CUSTOM -> "Personnalisé"
                        },
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Période") },
                        trailingIcon = {
                            IconButton(onClick = { periodDropdownExpanded = true }) {
                                Icon(Icons.Filled.ArrowDropDown, "")
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = readOnlyTextFieldColors()
                    )
                    DropdownMenu(
                        expanded = periodDropdownExpanded,
                        onDismissRequest = { periodDropdownExpanded = false }
                    ) {
                        BudgetPeriodType.entries.forEach { pt ->
                            DropdownMenuItem(
                                text = {
                                    Text(when (pt) {
                                        BudgetPeriodType.WEEKLY -> "Hebdomadaire"
                                        BudgetPeriodType.MONTHLY -> "Mensuel"
                                        BudgetPeriodType.YEARLY -> "Annuel"
                                        BudgetPeriodType.CUSTOM -> "Personnalisé"
                                    })
                                },
                                onClick = { periodType = pt; periodDropdownExpanded = false }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            NeumorphicButton(
                text = if (budget != null) "Modifier" else "Créer",
                onClick = {
                    val catId = selectedCategoryId ?: return@NeumorphicButton
                    val limit = limitText.toBigDecimalOrNull() ?: return@NeumorphicButton
                    onSave(catId, limit, periodType)
                },
                enabled = selectedCategoryId != null && limitText.isNotBlank()
            )
        },
        dismissButton = {
            NeumorphicButton(text = "Annuler", onClick = onDismiss, isPrimary = false)
        },
        containerColor = NeumorphicElevated,
        shape = RoundedCornerShape(16.dp)
    )
}
