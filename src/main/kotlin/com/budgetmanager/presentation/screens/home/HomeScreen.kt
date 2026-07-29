package com.budgetmanager.presentation.screens.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
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
import kotlinx.coroutines.flow.first
import org.koin.core.context.GlobalContext.get as getKoin
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter

data class HomeUiState(
    val totalBalance: BigDecimal = BigDecimal.ZERO,
    val monthlyIncome: BigDecimal = BigDecimal.ZERO,
    val monthlyExpenses: BigDecimal = BigDecimal.ZERO,
    val recentTransactions: List<Transaction> = emptyList(),
    val accounts: List<Account> = emptyList(),
    val isLoading: Boolean = true,
    /** Net worth history: list of (yearMonth label, total balance) for last 12 months. */
    val netWorthHistory: List<Pair<String, BigDecimal>> = emptyList(),
    /** Projected balance in 1, 3, 6 months at current spend rate. */
    val projection1Month: BigDecimal = BigDecimal.ZERO,
    val projection3Months: BigDecimal = BigDecimal.ZERO,
    val projection6Months: BigDecimal = BigDecimal.ZERO,
    /** Vacation mode info */
    val vacationActive: Boolean = false,
    val vacationDaysRemaining: Long? = null,
    val vacationSpent: BigDecimal = BigDecimal.ZERO,
    val vacationBudget: BigDecimal = BigDecimal.ZERO
)

class HomeScreenState {
    var uiState by mutableStateOf(HomeUiState())
        private set

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    init {
        loadData()
    }

    private fun loadData() {
        scope.launch {
            try {
                val koin = getKoin()
                val accountRepo = koin.get<com.budgetmanager.data.repository.AccountRepository>()
                val transactionRepo = koin.get<com.budgetmanager.data.repository.TransactionRepository>()

                // Collect accounts for total balance (multi-currency aware)
                launch {
                    val rateRepo = koin.get<com.budgetmanager.data.repository.ExchangeRateRepository>()
                    val appPrefs = koin.get<com.budgetmanager.data.preferences.AppPreferences>()
                    accountRepo.getAllAccounts().collectLatest { accounts ->
                        val mainCurrency = appPrefs.currencyCode
                        // Convert each account balance to the main currency
                        val totalBalance = accounts.fold(BigDecimal.ZERO) { acc, a ->
                            val converted = if (a.currencyCode.equals(mainCurrency, true)) a.balance
                                else rateRepo.convert(a.balance, a.currencyCode, mainCurrency)
                            acc.add(converted)
                        }
                        uiState = uiState.copy(
                            accounts = accounts,
                            totalBalance = totalBalance,
                            isLoading = false
                        )
                    }
                }

                // Collect current month transactions
                launch {
                    val now = YearMonth.now()
                    val start = now.atDay(1).atStartOfDay()
                    val end = now.atEndOfMonth().atTime(23, 59, 59)
                    transactionRepo.getTransactionsByDateRange(start, end).collectLatest { transactions ->
                        val income = transactions
                            .filter { it.transactionType == TransactionType.INCOME }
                            .fold(BigDecimal.ZERO) { acc, t -> acc.add(t.amount) }
                        val expenses = transactions
                            .filter { it.transactionType == TransactionType.EXPENSE }
                            .fold(BigDecimal.ZERO) { acc, t -> acc.add(t.amount) }

                        // Take most recent 10 for display
                        uiState = uiState.copy(
                            recentTransactions = transactions.take(10),
                            monthlyIncome = income,
                            monthlyExpenses = expenses
                        )

                        // Compute projections based on current month rate
                        val net = income.subtract(expenses)
                        val daysElapsed = java.time.LocalDate.now().dayOfMonth.coerceAtLeast(1)
                        val daysInMonth = java.time.LocalDate.now().lengthOfMonth()
                        val dailyNet = net.divide(java.math.BigDecimal(daysElapsed), 4, java.math.RoundingMode.HALF_UP)
                        val totalBalance = uiState.totalBalance
                        val projectedMonthlyNet = dailyNet.multiply(java.math.BigDecimal(daysInMonth))

                        uiState = uiState.copy(
                            projection1Month = totalBalance.add(projectedMonthlyNet),
                            projection3Months = totalBalance.add(projectedMonthlyNet.multiply(java.math.BigDecimal(3))),
                            projection6Months = totalBalance.add(projectedMonthlyNet.multiply(java.math.BigDecimal(6)))
                        )
                    }
                }

                // Vacation summary (multi-currency aware: converts to main currency)
                launch {
                    val appPrefs = koin.get<com.budgetmanager.data.preferences.AppPreferences>()
                    val rateRepo = koin.get<com.budgetmanager.data.repository.ExchangeRateRepository>()
                    val isActive = com.budgetmanager.util.VacationMode.isActive(appPrefs)
                    if (isActive) {
                        val start = com.budgetmanager.util.VacationMode.startDate(appPrefs)!!
                        val end = com.budgetmanager.util.VacationMode.endDate(appPrefs)!!
                        val vacTag = appPrefs.vacationTag.lowercase()
                        val mainCurrency = appPrefs.currencyCode
                        val accountCurrencies = accountRepo.getAllAccounts().first()
                            .associate { it.id to it.currencyCode }

                        val matchingTxs = transactionRepo.getAllTransactions().first()
                            .filter {
                                it.transactionType == TransactionType.EXPENSE &&
                                !it.date.toLocalDate().isBefore(start) &&
                                !it.date.toLocalDate().isAfter(end) &&
                                (it.tags.any { t -> t.equals(vacTag, true) } ||
                                 // Or just any expense in the period if no tag yet
                                 vacTag.isBlank())
                            }

                        var spent = BigDecimal.ZERO
                        for (tx in matchingTxs) {
                            val cur = accountCurrencies[tx.accountId] ?: mainCurrency
                            val converted = if (cur.equals(mainCurrency, true)) tx.amount
                                            else rateRepo.convert(tx.amount, cur, mainCurrency)
                            spent = spent.add(converted)
                        }
                        uiState = uiState.copy(
                            vacationActive = true,
                            vacationDaysRemaining = com.budgetmanager.util.VacationMode.daysRemaining(appPrefs),
                            vacationSpent = spent,
                            vacationBudget = appPrefs.vacationBudget
                        )
                    } else {
                        uiState = uiState.copy(vacationActive = false)
                    }
                }

                // Net worth history — all transactions to compute past balances
                launch {
                    transactionRepo.getAllTransactions().collectLatest { allTxs ->
                        // For each of the last 12 months, compute net worth at month end
                        val now = java.time.YearMonth.now()
                        val totalBalance = uiState.totalBalance
                        // Compute transaction net for months AFTER each target month, then back-calc
                        val history = (11 downTo 0).map { offset ->
                            val targetMonth = now.minusMonths(offset.toLong())
                            val targetMonthEnd = targetMonth.atEndOfMonth()
                            // Sum all transactions strictly after target month end
                            val futureTxs = allTxs.filter { it.date.toLocalDate().isAfter(targetMonthEnd) }
                            val futureNet = futureTxs.fold(BigDecimal.ZERO) { acc, t ->
                                when (t.transactionType) {
                                    TransactionType.INCOME -> acc.add(t.amount)
                                    TransactionType.EXPENSE -> acc.subtract(t.amount)
                                    TransactionType.TRANSFER -> acc
                                }
                            }
                            // Net worth at target month = current balance - future net
                            val nw = totalBalance.subtract(futureNet)
                            val label = targetMonth.format(java.time.format.DateTimeFormatter.ofPattern("MMM", java.util.Locale.FRANCE))
                                .replaceFirstChar { it.uppercaseChar() }
                            label to nw
                        }
                        uiState = uiState.copy(netWorthHistory = history)
                    }
                }
            } catch (e: Exception) {
                uiState = uiState.copy(isLoading = false)
            }
        }
    }

    fun dispose() {
        scope.cancel()
    }
}

@Composable
fun HomeScreen(navigationState: NavigationState) {
    val state = remember { HomeScreenState() }
    DisposableEffect(Unit) { onDispose { state.dispose() } }

    val ui = state.uiState
    val dateFormatter = remember { DateTimeFormatter.ofPattern("dd/MM/yyyy") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
            // Page header
            SectionHeader(title = "Tableau de bord")
            Spacer(Modifier.height(12.dp))

            // Vacation banner
            if (ui.vacationActive) {
                VacationBanner(ui)
                Spacer(Modifier.height(12.dp))
            }

            // Total balance card - cliquable → Nouveau compte
            val balanceCardInteraction = remember { MutableInteractionSource() }
            val balanceCardHovered by balanceCardInteraction.collectIsHoveredAsState()

            NeumorphicCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerHoverIcon(PointerIcon.Hand)
                    .hoverable(balanceCardInteraction)
                    .clickable(
                        interactionSource = balanceCardInteraction,
                        indication = null
                    ) { navigationState.navigateToNewAccount() },
                elevation = if (balanceCardHovered) 12.dp else 10.dp,
                borderRadius = 20.dp
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Solde total",
                        style = MaterialTheme.typography.bodyMedium,
                        color = NeumorphicTextSecondary
                    )
                    Spacer(Modifier.height(8.dp))
                    CurrencyAmount(
                        amount = ui.totalBalance,
                        style = MaterialTheme.typography.displayLarge
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "${ui.accounts.size} compte(s) actif(s)",
                        style = MaterialTheme.typography.bodySmall,
                        color = NeumorphicTextTertiary
                    )
                    Spacer(Modifier.height(8.dp))
                    // Hint visuel
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = null,
                            tint = if (balanceCardHovered) NeumorphicPrimary else NeumorphicTextTertiary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "Ajouter un compte",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (balanceCardHovered) NeumorphicPrimary else NeumorphicTextTertiary
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // Income / Expenses / Net — À PLAT (posé sur le fond, filets fins)
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FlatStat("Revenus du mois", ui.monthlyIncome, IncomeColor, Modifier.weight(1f))
                StatDivider()
                FlatStat("Dépenses du mois", ui.monthlyExpenses, ExpenseColor, Modifier.weight(1f))
                StatDivider()
                FlatStat("Net du mois", ui.monthlyIncome.subtract(ui.monthlyExpenses), null, Modifier.weight(1f))
            }

            Spacer(Modifier.height(24.dp))

            // Quick actions
            SectionHeader(title = "Actions rapides")
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                NeumorphicButton(
                    text = "Nouvelle transaction",
                    icon = Icons.Filled.Add,
                    onClick = { navigationState.navigateTo(Screen.ADD_TRANSACTION) },
                    modifier = Modifier.weight(1f)
                )
                NeumorphicButton(
                    text = "Transfert",
                    icon = Icons.Filled.SwapHoriz,
                    onClick = { navigationState.navigateTo(Screen.TRANSFER) },
                    isPrimary = false,
                    modifier = Modifier.weight(1f)
                )
                NeumorphicButton(
                    text = "Récurrents",
                    icon = Icons.Filled.Repeat,
                    onClick = { navigationState.navigateTo(Screen.RECURRING) },
                    isPrimary = false,
                    modifier = Modifier.weight(1f)
                )
                NeumorphicButton(
                    text = "Statistiques",
                    icon = Icons.Filled.BarChart,
                    onClick = { navigationState.navigateTo(Screen.ANALYTICS) },
                    isPrimary = false,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(24.dp))

            // Net worth evolution + projections
            if (ui.netWorthHistory.isNotEmpty()) {
                SectionHeader(title = "Patrimoine - 12 mois")
                Spacer(Modifier.height(8.dp))
                NetWorthChart(ui.netWorthHistory)
                Spacer(Modifier.height(20.dp))
            }

            // Projections
            if (ui.monthlyIncome > BigDecimal.ZERO || ui.monthlyExpenses > BigDecimal.ZERO) {
                SectionHeader(title = "Projection (au rythme actuel)")
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    ProjectionCard("Dans 1 mois", ui.projection1Month, modifier = Modifier.weight(1f))
                    StatDivider()
                    ProjectionCard("Dans 3 mois", ui.projection3Months, modifier = Modifier.weight(1f))
                    StatDivider()
                    ProjectionCard("Dans 6 mois", ui.projection6Months, modifier = Modifier.weight(1f))
                }
                Spacer(Modifier.height(20.dp))
            }

            // Recent transactions
            SectionHeader(
                title = "Transactions récentes",
                actionText = "Voir tout",
                onAction = { navigationState.navigateTo(Screen.TRANSACTIONS) }
            )
            Spacer(Modifier.height(8.dp))

            if (ui.recentTransactions.isEmpty() && !ui.isLoading) {
                EmptyState(
                    message = "Aucune transaction enregistrée.\nCommencez par ajouter une transaction !",
                    icon = Icons.Filled.ReceiptLong,
                    actionText = "Ajouter une transaction",
                    onAction = { navigationState.navigateTo(Screen.ADD_TRANSACTION) }
                )
            } else {
                Column(modifier = Modifier.fillMaxWidth()) {
                    ui.recentTransactions.forEachIndexed { index, tx ->
                        TransactionItem(
                            title = tx.title,
                            amount = if (tx.transactionType == TransactionType.EXPENSE) tx.amount.negate() else tx.amount,
                            category = tx.categoryName,
                            date = tx.date.format(dateFormatter),
                            isIncome = tx.transactionType == TransactionType.INCOME,
                            categoryColor = parseColor(tx.categoryColor),
                            onClick = { navigationState.navigateToEditTransaction(tx.id) }
                        )
                        if (index < ui.recentTransactions.lastIndex) {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                color = NeumorphicTextTertiary.copy(alpha = 0.3f)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun NetWorthChart(history: List<Pair<String, BigDecimal>>) {
    val maxValue = history.maxOfOrNull { it.second.toFloat() } ?: 1f
    val minValue = history.minOfOrNull { it.second.toFloat() } ?: 0f
    val range = (maxValue - minValue).coerceAtLeast(1f)
    val current = history.lastOrNull()?.second ?: BigDecimal.ZERO
    val firstValue = history.firstOrNull()?.second ?: BigDecimal.ZERO
    val totalChange = current.subtract(firstValue)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .neumorphicPressed(depth = 5.dp, borderRadius = 20.dp)
            .padding(20.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("Net worth actuel", style = MaterialTheme.typography.labelSmall, color = NeumorphicTextSecondary)
                CurrencyAmount(amount = current, style = MaterialTheme.typography.headlineMedium)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("12 mois", style = MaterialTheme.typography.labelSmall, color = NeumorphicTextSecondary)
                Text(
                    if (totalChange >= BigDecimal.ZERO) "+ ${String.format("%.0f", totalChange)} EUR"
                    else "- ${String.format("%.0f", totalChange.abs())} EUR",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (totalChange >= BigDecimal.ZERO) IncomeColor else ExpenseColor,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth().height(120.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            history.forEach { (label, value) ->
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom
                ) {
                    val height = ((value.toFloat() - minValue) / range).coerceIn(0.05f, 1f)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.8f)
                            .fillMaxHeight(height)
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                            .background(NeumorphicPrimary)
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            history.forEach { (label, _) ->
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = NeumorphicTextTertiary,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun VacationBanner(ui: HomeUiState) {
    val daysLeft = ui.vacationDaysRemaining
    val hasBudget = ui.vacationBudget > BigDecimal.ZERO
    val pct = if (hasBudget) (ui.vacationSpent.toDouble() / ui.vacationBudget.toDouble()).coerceAtLeast(0.0) else 0.0

    NeumorphicCard(modifier = Modifier.fillMaxWidth(), elevation = 6.dp, backgroundColor = TransferColor.copy(alpha = 0.08f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.BeachAccess, null, tint = TransferColor, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Mode vacances actif" + (daysLeft?.let { " · $it jour(s) restants" } ?: ""),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = TransferColor
                )
                Text(
                    if (hasBudget)
                        "Depense ${String.format("%.0f", ui.vacationSpent)} / ${String.format("%.0f", ui.vacationBudget)} EUR (${(pct * 100).toInt()}%)"
                    else
                        "Depense vacances : ${String.format("%.0f", ui.vacationSpent)} EUR",
                    style = MaterialTheme.typography.bodyMedium,
                    color = NeumorphicTextSecondary
                )
            }
        }
        if (hasBudget) {
            Spacer(Modifier.height(8.dp))
            BudgetProgressBar(
                spent = ui.vacationSpent.toFloat(),
                limit = ui.vacationBudget.toFloat(),
                showLabel = false
            )
        }
    }
}

@Composable
private fun ProjectionCard(label: String, amount: BigDecimal, modifier: Modifier = Modifier) {
    // À plat (contenu posé sur le fond)
    Column(modifier = modifier.padding(horizontal = 10.dp, vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = NeumorphicTextSecondary)
        Spacer(Modifier.height(4.dp))
        CurrencyAmount(
            amount = amount,
            style = MaterialTheme.typography.titleLarge,
            color = if (amount >= BigDecimal.ZERO) IncomeColor else ExpenseColor
        )
    }
}

/** Statistique à plat (label + montant), pour les rangées Revenus/Dépenses/Net. */
@Composable
private fun FlatStat(title: String, amount: BigDecimal, amountColor: Color?, modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(horizontal = 10.dp, vertical = 2.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.labelMedium,
            color = NeumorphicTextTertiary
        )
        Spacer(Modifier.height(6.dp))
        CurrencyAmount(
            amount = amount,
            style = MaterialTheme.typography.titleLarge,
            color = amountColor
        )
    }
}

/** Fin filet vertical de séparation entre stats à plat. */
@Composable
private fun StatDivider() {
    Box(
        Modifier
            .width(1.dp)
            .height(38.dp)
            .background(NeumorphicTextTertiary.copy(alpha = 0.28f))
    )
}
