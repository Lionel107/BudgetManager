package com.budgetmanager.presentation.screens.analytics

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.budgetmanager.domain.model.CategorySpendingData
import com.budgetmanager.domain.model.MonthlySummary
import com.budgetmanager.domain.model.CategoryStatistics
import com.budgetmanager.presentation.components.*
import com.budgetmanager.presentation.navigation.NavigationState
import com.budgetmanager.presentation.theme.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import org.koin.core.context.GlobalContext.get as getKoin
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.LocalDateTime

enum class AnalyticsPeriod(val label: String) {
    THIS_MONTH("Ce mois"),
    LAST_MONTH("Mois dernier"),
    THIS_YEAR("Cette année")
}

enum class ChartType(val label: String) {
    BARS_6MONTHS("Barres 6 mois"),
    LINE_EVOLUTION("Courbe evolution"),
    PIE_BREAKDOWN("Camembert categories"),
    DONUT_BREAKDOWN("Donut categories"),
    STACKED_BARS("Barres empilees"),
    HEATMAP("Heatmap jours")
}

data class AnalyticsUiState(
    val selectedPeriod: AnalyticsPeriod = AnalyticsPeriod.THIS_MONTH,
    val selectedChartType: ChartType = ChartType.BARS_6MONTHS,
    val totalIncome: BigDecimal = BigDecimal.ZERO,
    val totalExpenses: BigDecimal = BigDecimal.ZERO,
    val netBalance: BigDecimal = BigDecimal.ZERO,
    val savingsRate: Float = 0f,
    val categorySpending: List<CategorySpendingData> = emptyList(),
    val transactionCount: Int = 0,
    val isLoading: Boolean = true,
    // Comparison with previous equivalent period
    val previousIncome: BigDecimal = BigDecimal.ZERO,
    val previousExpenses: BigDecimal = BigDecimal.ZERO,
    val previousNet: BigDecimal = BigDecimal.ZERO,
    // Last 6 months (for chart): list of (label, income, expenses)
    val sixMonthsHistory: List<Triple<String, BigDecimal, BigDecimal>> = emptyList(),
    val allTransactionsCache: List<com.budgetmanager.domain.model.Transaction> = emptyList()
)

class AnalyticsScreenState {
    var uiState by mutableStateOf(AnalyticsUiState())
        private set

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    init { loadData() }

    fun selectPeriod(period: AnalyticsPeriod) {
        uiState = uiState.copy(selectedPeriod = period, isLoading = true)
        loadData()
    }

    fun selectChartType(type: ChartType) {
        uiState = uiState.copy(selectedChartType = type)
    }

    private fun loadData() {
        scope.launch {
            try {
                val koin = getKoin()
                val transactionRepo = koin.get<com.budgetmanager.data.repository.TransactionRepository>()
                val accountRepo = koin.get<com.budgetmanager.data.repository.AccountRepository>()
                val rateRepo = koin.get<com.budgetmanager.data.repository.ExchangeRateRepository>()
                val appPrefs = koin.get<com.budgetmanager.data.preferences.AppPreferences>()
                val mainCurrency = appPrefs.currencyCode

                // Build accountId -> currencyCode map for currency conversion
                val accountCurrencies: Map<Long, String> = accountRepo.getAllAccounts()
                    .first()
                    .associate { it.id to it.currencyCode }

                // Helper to convert a tx amount to the user's main currency
                suspend fun toMain(amount: BigDecimal, accountId: Long): BigDecimal {
                    val cur = accountCurrencies[accountId] ?: mainCurrency
                    return if (cur.equals(mainCurrency, true)) amount
                           else rateRepo.convert(amount, cur, mainCurrency)
                }

                val now = LocalDate.now()
                val (startDate, endDate) = when (uiState.selectedPeriod) {
                    AnalyticsPeriod.THIS_MONTH -> now.withDayOfMonth(1) to now
                    AnalyticsPeriod.LAST_MONTH -> now.minusMonths(1).withDayOfMonth(1) to now.withDayOfMonth(1).minusDays(1)
                    AnalyticsPeriod.THIS_YEAR -> now.withDayOfYear(1) to now
                }
                // Previous equivalent period
                val (prevStart, prevEnd) = when (uiState.selectedPeriod) {
                    AnalyticsPeriod.THIS_MONTH -> {
                        val prev = now.minusMonths(1)
                        prev.withDayOfMonth(1) to prev.withDayOfMonth(prev.lengthOfMonth())
                    }
                    AnalyticsPeriod.LAST_MONTH -> {
                        val prev = now.minusMonths(2)
                        prev.withDayOfMonth(1) to prev.withDayOfMonth(prev.lengthOfMonth())
                    }
                    AnalyticsPeriod.THIS_YEAR -> {
                        val prev = now.minusYears(1)
                        prev.withDayOfYear(1) to prev.withDayOfYear(prev.lengthOfYear())
                    }
                }

                val startDateTime = startDate.atStartOfDay()
                val endDateTime = endDate.atTime(23, 59, 59)

                // Helper: sum amounts converted to main currency
                suspend fun sumConverted(
                    txs: List<com.budgetmanager.domain.model.Transaction>,
                    type: com.budgetmanager.domain.model.TransactionType
                ): BigDecimal {
                    var total = BigDecimal.ZERO
                    for (t in txs) {
                        if (t.transactionType == type) {
                            total = total.add(toMain(t.amount, t.accountId))
                        }
                    }
                    return total
                }

                // Subscribe to ALL transactions for the rich analyses
                launch {
                    transactionRepo.getAllTransactions().collectLatest { allTxs ->
                        // Previous period totals (currency-converted)
                        val prevTxs = allTxs.filter {
                            !it.date.toLocalDate().isBefore(prevStart) &&
                            !it.date.toLocalDate().isAfter(prevEnd)
                        }
                        val prevInc = sumConverted(prevTxs, com.budgetmanager.domain.model.TransactionType.INCOME)
                        val prevExp = sumConverted(prevTxs, com.budgetmanager.domain.model.TransactionType.EXPENSE)

                        // 6-month history (current month included), currency-converted
                        val history = (5 downTo 0).map { offset ->
                            val ym = java.time.YearMonth.from(now).minusMonths(offset.toLong())
                            val mTxs = allTxs.filter { java.time.YearMonth.from(it.date) == ym }
                            val mInc = sumConverted(mTxs, com.budgetmanager.domain.model.TransactionType.INCOME)
                            val mExp = sumConverted(mTxs, com.budgetmanager.domain.model.TransactionType.EXPENSE)
                            val label = ym.format(java.time.format.DateTimeFormatter.ofPattern("MMM", java.util.Locale.FRANCE))
                                .replaceFirstChar { it.uppercaseChar() }
                            Triple(label, mInc, mExp)
                        }

                        uiState = uiState.copy(
                            previousIncome = prevInc,
                            previousExpenses = prevExp,
                            previousNet = prevInc.subtract(prevExp),
                            sixMonthsHistory = history,
                            allTransactionsCache = allTxs
                        )
                    }
                }

                transactionRepo.getTransactionsByDateRange(startDateTime, endDateTime).collectLatest { txs ->
                    val income = sumConverted(txs, com.budgetmanager.domain.model.TransactionType.INCOME)
                    val expenses = sumConverted(txs, com.budgetmanager.domain.model.TransactionType.EXPENSE)
                    val net = income.subtract(expenses)
                    val savingsRate = if (income > BigDecimal.ZERO)
                        net.divide(income, 4, RoundingMode.HALF_UP).toFloat() else 0f

                    // Category spending aggregation (currency-converted)
                    val categoryMap = mutableMapOf<Long, CategorySpendingData>()
                    for (tx in txs.filter { it.transactionType == com.budgetmanager.domain.model.TransactionType.EXPENSE }) {
                        val catId = tx.categoryId ?: 0L
                        val converted = toMain(tx.amount, tx.accountId)
                        val existing = categoryMap[catId]
                        if (existing != null) {
                            categoryMap[catId] = existing.copy(
                                totalSpent = existing.totalSpent.add(converted),
                                transactionCount = existing.transactionCount + 1
                            )
                        } else {
                            categoryMap[catId] = CategorySpendingData(
                                categoryId = catId,
                                categoryName = tx.categoryName ?: "Non catégorisé",
                                categoryColor = tx.categoryColor ?: "#6C63FF",
                                totalSpent = converted,
                                transactionCount = 1
                            )
                        }
                    }

                    uiState = uiState.copy(
                        totalIncome = income,
                        totalExpenses = expenses,
                        netBalance = net,
                        savingsRate = savingsRate,
                        categorySpending = categoryMap.values.sortedByDescending { it.totalSpent },
                        transactionCount = txs.size,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                uiState = uiState.copy(isLoading = false)
            }
        }
    }

    fun dispose() { scope.cancel() }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun AnalyticsScreen(navigationState: NavigationState) {
    val state = remember { AnalyticsScreenState() }
    DisposableEffect(Unit) { onDispose { state.dispose() } }

    val ui = state.uiState

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
            SectionHeader(title = "Analyse")
            Spacer(Modifier.height(12.dp))

            // Period selector
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AnalyticsPeriod.entries.forEach { period ->
                    FilterChip(
                        label = period.label,
                        isSelected = ui.selectedPeriod == period,
                        onClick = { state.selectPeriod(period) }
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // Summary cards - 2x2 grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                NeumorphicCard(modifier = Modifier.weight(1f), elevation = 8.dp) {
                    Text("Revenus", style = MaterialTheme.typography.bodySmall, color = NeumorphicTextSecondary)
                    Spacer(Modifier.height(4.dp))
                    CurrencyAmount(amount = ui.totalIncome, style = MaterialTheme.typography.headlineMedium, color = IncomeColor)
                }
                NeumorphicCard(modifier = Modifier.weight(1f), elevation = 8.dp) {
                    Text("Dépenses", style = MaterialTheme.typography.bodySmall, color = NeumorphicTextSecondary)
                    Spacer(Modifier.height(4.dp))
                    CurrencyAmount(amount = ui.totalExpenses, style = MaterialTheme.typography.headlineMedium, color = ExpenseColor)
                }
            }

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                NeumorphicCard(modifier = Modifier.weight(1f), elevation = 8.dp) {
                    Text("Solde net", style = MaterialTheme.typography.bodySmall, color = NeumorphicTextSecondary)
                    Spacer(Modifier.height(4.dp))
                    CurrencyAmount(
                        amount = ui.netBalance,
                        style = MaterialTheme.typography.headlineMedium,
                        color = if (ui.netBalance >= BigDecimal.ZERO) IncomeColor else ExpenseColor,
                        showSign = true
                    )
                }
                NeumorphicCard(modifier = Modifier.weight(1f), elevation = 8.dp) {
                    Text("Taux d'épargne", style = MaterialTheme.typography.bodySmall, color = NeumorphicTextSecondary)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "${(ui.savingsRate * 100).toInt()}%",
                        style = MaterialTheme.typography.headlineMedium,
                        color = if (ui.savingsRate >= 0) IncomeColor else ExpenseColor,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "${ui.transactionCount} transactions",
                        style = MaterialTheme.typography.bodySmall,
                        color = NeumorphicTextTertiary
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // Comparison vs previous period
            ComparisonRow(ui)

            Spacer(Modifier.height(20.dp))

            // Chart type selector + chart rendering
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Graphiques", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                ChartType.entries.forEach { type ->
                    FilterChip(
                        label = type.label,
                        isSelected = ui.selectedChartType == type,
                        onClick = { state.selectChartType(type) }
                    )
                }
            }
            Spacer(Modifier.height(12.dp))

            NeumorphicCard(modifier = Modifier.fillMaxWidth(), elevation = 6.dp) {
                Text(
                    ui.selectedChartType.label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = NeumorphicTextPrimary
                )
                Spacer(Modifier.height(12.dp))
                RenderChart(ui)
            }
            Spacer(Modifier.height(20.dp))

            // Category spending breakdown
            Text("Dépenses par catégorie", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))

            if (ui.categorySpending.isEmpty() && !ui.isLoading) {
                EmptyState(
                    message = "Aucune dépense pour cette période.",
                    icon = Icons.Filled.PieChart
                )
            } else {
                // Two-column layout for desktop
                val left = ui.categorySpending.filterIndexed { i, _ -> i % 2 == 0 }
                val right = ui.categorySpending.filterIndexed { i, _ -> i % 2 == 1 }
                val maxSpent = ui.categorySpending.maxOfOrNull { it.totalSpent.toFloat() } ?: 1f

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        left.forEach { cat ->
                            CategorySpendingCard(cat, maxSpent, ui.totalExpenses)
                        }
                    }
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        right.forEach { cat ->
                            CategorySpendingCard(cat, maxSpent, ui.totalExpenses)
                        }
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun CategorySpendingCard(
    cat: CategorySpendingData,
    maxSpent: Float,
    totalExpenses: BigDecimal
) {
    val pct = if (totalExpenses > BigDecimal.ZERO)
        cat.totalSpent.toFloat() / totalExpenses.toFloat() else 0f
    val barWidth = if (maxSpent > 0f) cat.totalSpent.toFloat() / maxSpent else 0f

    NeumorphicCard(modifier = Modifier.fillMaxWidth(), elevation = 5.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(parseColor(cat.categoryColor))
            )
            Spacer(Modifier.width(10.dp))
            Text(cat.categoryName, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), color = NeumorphicTextPrimary)
            Text("${(pct * 100).toInt()}%", style = MaterialTheme.typography.labelMedium, color = NeumorphicTextTertiary)
        }
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(NeumorphicDepressed)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(barWidth.coerceIn(0f, 1f))
                    .clip(RoundedCornerShape(4.dp))
                    .background(parseColor(cat.categoryColor))
            )
        }
        Spacer(Modifier.height(4.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            CurrencyAmount(amount = cat.totalSpent, style = MaterialTheme.typography.labelLarge, color = NeumorphicTextPrimary)
            Text("${cat.transactionCount} tx", style = MaterialTheme.typography.bodySmall, color = NeumorphicTextTertiary)
        }
    }
}

@Composable
private fun ComparisonRow(ui: AnalyticsUiState) {
    val periodLabel = when (ui.selectedPeriod) {
        AnalyticsPeriod.THIS_MONTH -> "Mois précédent"
        AnalyticsPeriod.LAST_MONTH -> "Mois -2"
        AnalyticsPeriod.THIS_YEAR -> "Année précédente"
    }
    NeumorphicCard(modifier = Modifier.fillMaxWidth(), elevation = 6.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.CompareArrows, null, tint = NeumorphicPrimary, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            Text("Comparaison vs $periodLabel", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = NeumorphicTextPrimary)
        }
        Spacer(Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            ComparisonStat("Revenus", ui.totalIncome, ui.previousIncome, isIncome = true, modifier = Modifier.weight(1f))
            ComparisonStat("Depenses", ui.totalExpenses, ui.previousExpenses, isIncome = false, modifier = Modifier.weight(1f))
            ComparisonStat("Solde net", ui.netBalance, ui.previousNet, isIncome = ui.netBalance >= BigDecimal.ZERO, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun ComparisonStat(
    label: String,
    current: BigDecimal,
    previous: BigDecimal,
    isIncome: Boolean,
    modifier: Modifier = Modifier
) {
    val delta = current.subtract(previous)
    val pctChange = if (previous.compareTo(BigDecimal.ZERO) != 0) {
        delta.divide(previous.abs(), 4, RoundingMode.HALF_UP).multiply(BigDecimal(100))
    } else null

    // For expenses, going up is bad. For income/net, going up is good.
    val isPositiveChange = if (isIncome) delta >= BigDecimal.ZERO else delta <= BigDecimal.ZERO
    val deltaColor = if (isPositiveChange) IncomeColor else ExpenseColor
    val arrow = if (delta >= BigDecimal.ZERO) "↑" else "↓"

    Column(modifier = modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = NeumorphicTextSecondary)
        Spacer(Modifier.height(4.dp))
        CurrencyAmount(amount = current, style = MaterialTheme.typography.titleMedium)
        if (pctChange != null) {
            Text(
                "$arrow ${String.format("%.1f", pctChange.abs().toDouble())}%",
                style = MaterialTheme.typography.labelMedium,
                color = deltaColor,
                fontWeight = FontWeight.SemiBold
            )
        } else {
            Text("—", style = MaterialTheme.typography.labelSmall, color = NeumorphicTextTertiary)
        }
    }
}


@Composable
private fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(color))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = NeumorphicTextSecondary)
    }
}

@Composable
private fun RenderChart(ui: AnalyticsUiState) {
    when (ui.selectedChartType) {
        ChartType.BARS_6MONTHS -> {
            if (ui.sixMonthsHistory.isEmpty()) {
                EmptyChartState()
            } else {
                SixMonthsChartBars(ui.sixMonthsHistory)
            }
        }
        ChartType.LINE_EVOLUTION -> {
            // Net balance over 6 months
            if (ui.sixMonthsHistory.isEmpty()) {
                EmptyChartState()
            } else {
                val points = ui.sixMonthsHistory.map { (label, inc, exp) ->
                    LinePoint(label, inc.subtract(exp).toFloat())
                }
                LineChart(points = points)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Solde net mensuel (revenus - depenses)",
                    style = MaterialTheme.typography.labelSmall,
                    color = NeumorphicTextTertiary
                )
            }
        }
        ChartType.PIE_BREAKDOWN -> {
            if (ui.categorySpending.isEmpty()) {
                EmptyChartState()
            } else {
                val slices = assignColors(ui.categorySpending.map { it.categoryName to it.totalSpent.toFloat() })
                PieChart(slices = slices, donut = false)
            }
        }
        ChartType.DONUT_BREAKDOWN -> {
            if (ui.categorySpending.isEmpty()) {
                EmptyChartState()
            } else {
                val slices = assignColors(ui.categorySpending.map { it.categoryName to it.totalSpent.toFloat() })
                PieChart(slices = slices, donut = true)
            }
        }
        ChartType.STACKED_BARS -> {
            if (ui.sixMonthsHistory.isEmpty()) {
                EmptyChartState()
            } else {
                val cols = ui.sixMonthsHistory.map { (label, inc, exp) ->
                    StackedBarColumn(
                        label = label,
                        segments = listOf(
                            inc.toFloat() to IncomeColor,
                            exp.toFloat() to ExpenseColor
                        )
                    )
                }
                StackedBarChart(
                    columns = cols,
                    legend = listOf("Revenus" to IncomeColor, "Depenses" to ExpenseColor)
                )
            }
        }
        ChartType.HEATMAP -> {
            val today = LocalDate.now()
            val (year, month) = when (ui.selectedPeriod) {
                AnalyticsPeriod.LAST_MONTH -> {
                    val lm = today.minusMonths(1)
                    lm.year to lm.monthValue
                }
                else -> today.year to today.monthValue
            }
            val valuePerDay = ui.allTransactionsCache
                .asSequence()
                .filter {
                    it.transactionType == com.budgetmanager.domain.model.TransactionType.EXPENSE &&
                    it.date.year == year && it.date.monthValue == month
                }
                .groupBy { it.date.dayOfMonth }
                .mapValues { (_, txs) -> txs.fold(BigDecimal.ZERO) { a, t -> a.add(t.amount) }.toFloat() }

            DailyHeatmap(year = year, month = month, valuePerDay = valuePerDay)
        }
    }
}

@Composable
private fun EmptyChartState() {
    Box(modifier = Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
        Text(
            "Pas assez de donnees pour ce graphique.",
            style = MaterialTheme.typography.bodyMedium,
            color = NeumorphicTextTertiary
        )
    }
}

/** The original 6-month bars chart, renamed to avoid collision with the wrapper. */
@Composable
private fun SixMonthsChartBars(history: List<Triple<String, BigDecimal, BigDecimal>>) {
    val maxValue = history.flatMap { listOf(it.second, it.third) }
        .maxOfOrNull { it.toFloat() } ?: 1f

    Row(
        modifier = Modifier.fillMaxWidth().height(180.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        history.forEach { (label, income, expenses) ->
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    val incHeight = if (maxValue > 0f) (income.toFloat() / maxValue).coerceIn(0f, 1f) else 0f
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(incHeight)
                            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                            .background(IncomeColor)
                    )
                    val expHeight = if (maxValue > 0f) (expenses.toFloat() / maxValue).coerceIn(0f, 1f) else 0f
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(expHeight)
                            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                            .background(ExpenseColor)
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(label, style = MaterialTheme.typography.labelSmall, color = NeumorphicTextSecondary, textAlign = TextAlign.Center)
            }
        }
    }
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        LegendItem(IncomeColor, "Revenus")
        LegendItem(ExpenseColor, "Depenses")
    }
}

