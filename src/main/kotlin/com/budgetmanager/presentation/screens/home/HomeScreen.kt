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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
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
    /** Séries mensuelles (7 derniers mois, ancien→récent) pour les mini-courbes du trio. */
    val incomeSeries: List<Float> = emptyList(),
    val expenseSeries: List<Float> = emptyList(),
    val netSeries: List<Float> = emptyList(),
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

                        // Séries mensuelles (7 derniers mois) pour les mini-courbes
                        val monthsList = (6 downTo 0).map { now.minusMonths(it.toLong()) }
                        fun monthlySum(ym: java.time.YearMonth, type: TransactionType): Float =
                            allTxs.filter { it.transactionType == type && java.time.YearMonth.from(it.date) == ym }
                                .fold(BigDecimal.ZERO) { acc, t -> acc.add(t.amount) }.toFloat()
                        val incSeries = monthsList.map { monthlySum(it, TransactionType.INCOME) }
                        val expSeries = monthsList.map { monthlySum(it, TransactionType.EXPENSE) }
                        val netSer = incSeries.zip(expSeries) { i, e -> i - e }

                        uiState = uiState.copy(
                            netWorthHistory = history,
                            incomeSeries = incSeries,
                            expenseSeries = expSeries,
                            netSeries = netSer
                        )
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
                    Spacer(Modifier.height(12.dp))
                    NetChip(ui.monthlyIncome.subtract(ui.monthlyExpenses))
                    Spacer(Modifier.height(12.dp))
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

            Spacer(Modifier.height(32.dp))

            // Income / Expenses / Net — cartes neumorphiques avec mini-courbe + badge de tendance
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                StatCard("Revenus du mois", ui.monthlyIncome, IncomeColor, ui.incomeSeries, IncomeColor, Modifier.weight(1f))
                StatCard("Dépenses du mois", ui.monthlyExpenses, ExpenseColor, ui.expenseSeries, ExpenseColor, Modifier.weight(1f))
                StatCard("Net du mois", ui.monthlyIncome.subtract(ui.monthlyExpenses), null, ui.netSeries, NeumorphicPrimary, Modifier.weight(1f))
            }

            Spacer(Modifier.height(32.dp))

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

            Spacer(Modifier.height(32.dp))

            // Net worth evolution + projections
            if (ui.netWorthHistory.isNotEmpty()) {
                SectionHeader(title = "Patrimoine - 12 mois")
                Spacer(Modifier.height(8.dp))
                NetWorthChart(ui.netWorthHistory)
                Spacer(Modifier.height(28.dp))
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
                Spacer(Modifier.height(28.dp))
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
        Spacer(Modifier.height(16.dp))
        val primaryColor = NeumorphicPrimary
        val gridColor = NeumorphicTextTertiary.copy(alpha = 0.20f)
        val chartValues = history.map { it.second.toFloat() }
        Canvas(modifier = Modifier.fillMaxWidth().height(130.dp)) {
            if (chartValues.size < 2) return@Canvas
            val minV = chartValues.minOrNull() ?: 0f
            val maxV = chartValues.maxOrNull() ?: 0f
            val rng = (maxV - minV).coerceAtLeast(0.01f)
            val padY = size.height * 0.16f
            val padX = 6.dp.toPx()
            val stepX = (size.width - padX * 2) / (chartValues.size - 1)
            val pts = chartValues.mapIndexed { i, v ->
                Offset(padX + i * stepX, size.height - padY - ((v - minV) / rng) * (size.height - padY * 2))
            }
            val dash = PathEffect.dashPathEffect(floatArrayOf(2f, 8f), 0f)
            for (g in 1..3) {
                val y = size.height / 4f * g
                drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f, pathEffect = dash)
            }
            drawPath(
                buildSmooth(pts, true, size.height),
                Brush.verticalGradient(listOf(primaryColor.copy(alpha = 0.30f), primaryColor.copy(alpha = 0f)))
            )
            val line = buildSmooth(pts, false, 0f)
            drawPath(line, primaryColor.copy(alpha = 0.18f), style = Stroke(7.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawPath(line, primaryColor, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawCircle(primaryColor, radius = 4.5.dp.toPx(), center = pts.last())
        }
        Spacer(Modifier.height(8.dp))
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

/**
 * Construit une courbe LISSE (Catmull-Rom → Bézier cubique) passant par [points].
 * [close] = true ferme l'aire jusqu'à [bottom] pour un remplissage dégradé.
 */
private fun buildSmooth(points: List<Offset>, close: Boolean, bottom: Float): Path {
    val p = Path()
    if (points.isEmpty()) return p
    if (close) { p.moveTo(points[0].x, bottom); p.lineTo(points[0].x, points[0].y) }
    else p.moveTo(points[0].x, points[0].y)
    for (i in 0 until points.size - 1) {
        val p0 = points.getOrElse(i - 1) { points[i] }
        val p1 = points[i]; val p2 = points[i + 1]; val p3 = points.getOrElse(i + 2) { p2 }
        val c1x = p1.x + (p2.x - p0.x) / 6f; val c1y = p1.y + (p2.y - p0.y) / 6f
        val c2x = p2.x - (p3.x - p1.x) / 6f; val c2y = p2.y - (p3.y - p1.y) / 6f
        p.cubicTo(c1x, c1y, c2x, c2y, p2.x, p2.y)
    }
    if (close) { p.lineTo(points.last().x, bottom); p.close() }
    return p
}

/** Mini-courbe LISSE lumineuse (aire dégradée + ligne + halo néon + point final). */
@Composable
private fun Sparkline(values: List<Float>, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        if (values.size < 2) return@Canvas
        val minV = values.minOrNull() ?: 0f
        val maxV = values.maxOrNull() ?: 0f
        val range = (maxV - minV).coerceAtLeast(0.01f)
        val padY = size.height * 0.14f
        val stepX = size.width / (values.size - 1)
        val points = values.mapIndexed { i, v ->
            Offset(i * stepX, size.height - padY - ((v - minV) / range) * (size.height - padY * 2))
        }
        drawPath(buildSmooth(points, true, size.height),
            Brush.verticalGradient(listOf(color.copy(alpha = 0.22f), color.copy(alpha = 0f))))
        val line = buildSmooth(points, false, 0f)
        drawPath(line, color.copy(alpha = 0.18f), style = Stroke(6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(line, color, style = Stroke(2.2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawCircle(color, radius = 2.8.dp.toPx(), center = points.last())
    }
}

/** Carte de statistique : label + mini-courbe, montant, badge de tendance. */
@Composable
private fun StatCard(
    label: String,
    amount: BigDecimal,
    amountColor: Color?,
    series: List<Float>,
    sparkColor: Color,
    modifier: Modifier = Modifier
) {
    NeumorphicCard(modifier = modifier, elevation = 6.dp, borderRadius = 18.dp) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = NeumorphicTextTertiary,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
            if (series.size >= 2) {
                Sparkline(series, sparkColor, Modifier.width(56.dp).height(24.dp))
            }
        }
        Spacer(Modifier.height(10.dp))
        CurrencyAmount(amount = amount, style = MaterialTheme.typography.titleLarge, color = amountColor)
        if (series.size >= 2) {
            val prev = series[series.size - 2]
            val last = series.last()
            val delta = last - prev
            val pctTxt = if (kotlin.math.abs(prev) > 0.01f)
                "${(kotlin.math.abs(delta / prev) * 100).toInt()} %" else "—"
            Spacer(Modifier.height(8.dp))
            TrendPill(up = delta >= 0f, text = pctTxt, color = sparkColor)
        }
    }
}

/** Petit badge de tendance (flèche + %), teinté par la couleur de la métrique. */
@Composable
private fun TrendPill(up: Boolean, text: String, color: Color) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            if (up) Icons.Filled.ArrowDropUp else Icons.Filled.ArrowDropDown,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(16.dp)
        )
        Text(text, style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Bold)
    }
}

/** Chip « net ce mois » en creux, sous le solde. */
@Composable
private fun NetChip(net: BigDecimal) {
    val positive = net >= BigDecimal.ZERO
    val c = if (positive) IncomeColor else ExpenseColor
    Row(
        modifier = Modifier
            .neumorphicPressed(depth = 3.dp, borderRadius = 50.dp)
            .padding(horizontal = 15.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            if (positive) Icons.Filled.TrendingUp else Icons.Filled.TrendingDown,
            contentDescription = null, tint = c, modifier = Modifier.size(15.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            (if (positive) "+" else "") + String.format(java.util.Locale.FRANCE, "%,.0f € ce mois", net),
            style = MaterialTheme.typography.labelMedium,
            color = c,
            fontWeight = FontWeight.SemiBold
        )
    }
}
