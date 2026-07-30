package com.budgetmanager.presentation.screens.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.budgetmanager.domain.model.Account
import com.budgetmanager.domain.model.Transaction
import com.budgetmanager.domain.model.TransactionType
import com.budgetmanager.presentation.components.CurrencyAmount
import com.budgetmanager.presentation.navigation.NavigationState
import com.budgetmanager.presentation.navigation.Screen
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import org.koin.core.context.GlobalContext.get as getKoin
import java.math.BigDecimal
import java.time.YearMonth
import java.time.format.DateTimeFormatter

// ============================================================================
// Palette shadcn (thème clair). L'Accueil est le 1er écran migré vers ce style
// épuré ; on l'étendra ensuite au thème global.
// ============================================================================
private val ScBg = Color(0xFFFAFAFB)
private val ScCard = Color(0xFFFFFFFF)
private val ScBorder = Color(0xFFECECEF)
private val ScFg = Color(0xFF0A0A0B)
private val ScMuted = Color(0xFFF4F4F5)
private val ScMutedFg = Color(0xFF71717A)
private val ScPrimary = Color(0xFF6366F1)
private val ScGreen = Color(0xFF16A34A)
private val ScRed = Color(0xFFEF4444)
private val ScGreenSoft = Color(0x1A16A34A)
private val ScRedSoft = Color(0x1AEF4444)

data class HomeUiState(
    val totalBalance: BigDecimal = BigDecimal.ZERO,
    val monthlyIncome: BigDecimal = BigDecimal.ZERO,
    val monthlyExpenses: BigDecimal = BigDecimal.ZERO,
    val recentTransactions: List<Transaction> = emptyList(),
    val accounts: List<Account> = emptyList(),
    val isLoading: Boolean = true,
    val netWorthHistory: List<Pair<String, BigDecimal>> = emptyList(),
    val incomeSeries: List<Float> = emptyList(),
    val expenseSeries: List<Float> = emptyList(),
    val netSeries: List<Float> = emptyList(),
    val projection1Month: BigDecimal = BigDecimal.ZERO,
    val projection3Months: BigDecimal = BigDecimal.ZERO,
    val projection6Months: BigDecimal = BigDecimal.ZERO,
    val vacationActive: Boolean = false,
    val vacationDaysRemaining: Long? = null,
    val vacationSpent: BigDecimal = BigDecimal.ZERO,
    val vacationBudget: BigDecimal = BigDecimal.ZERO
)

class HomeScreenState {
    var uiState by mutableStateOf(HomeUiState())
        private set

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    init { loadData() }

    private fun loadData() {
        scope.launch {
            try {
                val koin = getKoin()
                val accountRepo = koin.get<com.budgetmanager.data.repository.AccountRepository>()
                val transactionRepo = koin.get<com.budgetmanager.data.repository.TransactionRepository>()

                launch {
                    val rateRepo = koin.get<com.budgetmanager.data.repository.ExchangeRateRepository>()
                    val appPrefs = koin.get<com.budgetmanager.data.preferences.AppPreferences>()
                    accountRepo.getAllAccounts().collectLatest { accounts ->
                        val mainCurrency = appPrefs.currencyCode
                        val totalBalance = accounts.fold(BigDecimal.ZERO) { acc, a ->
                            val converted = if (a.currencyCode.equals(mainCurrency, true)) a.balance
                                else rateRepo.convert(a.balance, a.currencyCode, mainCurrency)
                            acc.add(converted)
                        }
                        uiState = uiState.copy(accounts = accounts, totalBalance = totalBalance, isLoading = false)
                    }
                }

                launch {
                    val now = YearMonth.now()
                    val start = now.atDay(1).atStartOfDay()
                    val end = now.atEndOfMonth().atTime(23, 59, 59)
                    transactionRepo.getTransactionsByDateRange(start, end).collectLatest { transactions ->
                        val income = transactions.filter { it.transactionType == TransactionType.INCOME }
                            .fold(BigDecimal.ZERO) { acc, t -> acc.add(t.amount) }
                        val expenses = transactions.filter { it.transactionType == TransactionType.EXPENSE }
                            .fold(BigDecimal.ZERO) { acc, t -> acc.add(t.amount) }
                        uiState = uiState.copy(
                            recentTransactions = transactions.take(10),
                            monthlyIncome = income,
                            monthlyExpenses = expenses
                        )
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

                launch {
                    val appPrefs = koin.get<com.budgetmanager.data.preferences.AppPreferences>()
                    val rateRepo = koin.get<com.budgetmanager.data.repository.ExchangeRateRepository>()
                    val isActive = com.budgetmanager.util.VacationMode.isActive(appPrefs)
                    if (isActive) {
                        val start = com.budgetmanager.util.VacationMode.startDate(appPrefs)!!
                        val end = com.budgetmanager.util.VacationMode.endDate(appPrefs)!!
                        val vacTag = appPrefs.vacationTag.lowercase()
                        val mainCurrency = appPrefs.currencyCode
                        val accountCurrencies = accountRepo.getAllAccounts().first().associate { it.id to it.currencyCode }
                        val matchingTxs = transactionRepo.getAllTransactions().first().filter {
                            it.transactionType == TransactionType.EXPENSE &&
                            !it.date.toLocalDate().isBefore(start) &&
                            !it.date.toLocalDate().isAfter(end) &&
                            (it.tags.any { t -> t.equals(vacTag, true) } || vacTag.isBlank())
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

                launch {
                    transactionRepo.getAllTransactions().collectLatest { allTxs ->
                        val now = java.time.YearMonth.now()
                        val totalBalance = uiState.totalBalance
                        val history = (11 downTo 0).map { offset ->
                            val targetMonth = now.minusMonths(offset.toLong())
                            val targetMonthEnd = targetMonth.atEndOfMonth()
                            val futureTxs = allTxs.filter { it.date.toLocalDate().isAfter(targetMonthEnd) }
                            val futureNet = futureTxs.fold(BigDecimal.ZERO) { acc, t ->
                                when (t.transactionType) {
                                    TransactionType.INCOME -> acc.add(t.amount)
                                    TransactionType.EXPENSE -> acc.subtract(t.amount)
                                    TransactionType.TRANSFER -> acc
                                }
                            }
                            val nw = totalBalance.subtract(futureNet)
                            val label = targetMonth.format(DateTimeFormatter.ofPattern("MMM", java.util.Locale.FRANCE))
                                .replaceFirstChar { it.uppercaseChar() }
                            label to nw
                        }
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

    fun dispose() { scope.cancel() }
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
            .background(ScBg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 16.dp)
    ) {
        // En-tête
        Appear(0) {
            Column {
                Text("Tableau de bord", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = ScFg, letterSpacing = (-0.5).sp)
                Spacer(Modifier.height(4.dp))
                Text("Ton aperçu financier", fontSize = 14.sp, color = ScMutedFg)
            }
        }
        Spacer(Modifier.height(28.dp))

        if (ui.vacationActive) {
            Appear(40) { VacationBanner(ui) }
            Spacer(Modifier.height(18.dp))
        }

        // Carte solde (héro)
        Appear(60) {
            ShadcnCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerHoverIcon(PointerIcon.Hand)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                        navigationState.navigateToNewAccount()
                    }
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                    Text("SOLDE TOTAL", fontSize = 12.sp, letterSpacing = 1.2.sp, fontWeight = FontWeight.SemiBold, color = ScMutedFg)
                    val net = ui.monthlyIncome.subtract(ui.monthlyExpenses)
                    Badge(if (net >= BigDecimal.ZERO) "▲ ce mois" else "▼ ce mois", net >= BigDecimal.ZERO)
                }
                Spacer(Modifier.height(14.dp))
                CurrencyAmount(amount = ui.totalBalance, style = MaterialTheme.typography.displaySmall, color = ScFg)
                Spacer(Modifier.height(10.dp))
                val net = ui.monthlyIncome.subtract(ui.monthlyExpenses)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (net >= BigDecimal.ZERO) Icons.Filled.TrendingUp else Icons.Filled.TrendingDown,
                        null, tint = if (net >= BigDecimal.ZERO) ScGreen else ScRed, modifier = Modifier.size(15.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        (if (net >= BigDecimal.ZERO) "+" else "") + String.format(java.util.Locale.FRANCE, "%,.0f € ce mois", net) +
                            " · ${ui.accounts.size} comptes",
                        fontSize = 13.sp, color = ScMutedFg
                    )
                }
            }
        }
        Spacer(Modifier.height(18.dp))

        // Trio stats
        Appear(120) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                StatCard("Revenus", ui.monthlyIncome, ScGreen, ui.incomeSeries, ScGreen, Icons.Filled.ArrowDownward, Modifier.weight(1f))
                StatCard("Dépenses", ui.monthlyExpenses, ScFg, ui.expenseSeries, ScRed, Icons.Filled.ArrowUpward, Modifier.weight(1f))
                StatCard("Net du mois", ui.monthlyIncome.subtract(ui.monthlyExpenses), ScFg, ui.netSeries, ScPrimary, Icons.Filled.ShowChart, Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(24.dp))

        // Actions
        Appear(180) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ScButton("Nouvelle transaction", Icons.Filled.Add, primary = true, modifier = Modifier.weight(1f)) {
                    navigationState.navigateTo(Screen.ADD_TRANSACTION)
                }
                ScButton("Transfert", Icons.Filled.SwapHoriz, primary = false, modifier = Modifier.weight(1f)) {
                    navigationState.navigateTo(Screen.TRANSFER)
                }
                ScButton("Récurrents", Icons.Filled.Repeat, primary = false, modifier = Modifier.weight(1f)) {
                    navigationState.navigateTo(Screen.RECURRING)
                }
                ScButton("Statistiques", Icons.Filled.BarChart, primary = false, modifier = Modifier.weight(1f)) {
                    navigationState.navigateTo(Screen.ANALYTICS)
                }
            }
        }
        Spacer(Modifier.height(24.dp))

        // Patrimoine + Projections
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            if (ui.netWorthHistory.isNotEmpty()) {
                Appear(240, Modifier.weight(1.6f)) { NetWorthChart(ui.netWorthHistory) }
            }
            if (ui.monthlyIncome > BigDecimal.ZERO || ui.monthlyExpenses > BigDecimal.ZERO) {
                Appear(280, Modifier.weight(1f)) {
                    ShadcnCard(Modifier.fillMaxWidth()) {
                        Text("Projection", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = ScFg)
                        Text("au rythme actuel", fontSize = 12.sp, color = ScMutedFg)
                        Spacer(Modifier.height(16.dp))
                        ProjectionRow("Dans 1 mois", ui.projection1Month)
                        Spacer(Modifier.height(12.dp))
                        ProjectionRow("Dans 3 mois", ui.projection3Months)
                        Spacer(Modifier.height(12.dp))
                        ProjectionRow("Dans 6 mois", ui.projection6Months)
                    }
                }
            }
        }
        Spacer(Modifier.height(18.dp))

        // Transactions
        Appear(340) {
            ShadcnCard(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Transactions récentes", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = ScFg)
                    Text("Voir tout →", fontSize = 13.sp, color = ScMutedFg,
                        modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { navigationState.navigateTo(Screen.TRANSACTIONS) }.padding(4.dp))
                }
                Spacer(Modifier.height(6.dp))
                if (ui.recentTransactions.isEmpty()) {
                    Text(
                        if (ui.isLoading) "Chargement…" else "Aucune transaction ce mois-ci.",
                        fontSize = 13.sp, color = ScMutedFg, modifier = Modifier.padding(vertical = 16.dp)
                    )
                } else {
                    ui.recentTransactions.forEachIndexed { i, tx ->
                        TxnRow(tx, dateFormatter) { navigationState.navigateToEditTransaction(tx.id) }
                        if (i < ui.recentTransactions.lastIndex) {
                            Box(Modifier.fillMaxWidth().height(1.dp).background(ScBorder))
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(32.dp))
    }
}

// ===================== Composants shadcn =====================

/** Apparition douce (fondu + léger glissement), avec délai. */
@Composable
private fun Appear(delayMillis: Int, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(delayMillis.toLong()); shown = true }
    val alpha by animateFloatAsState(if (shown) 1f else 0f, tween(450), label = "a")
    val dy by animateFloatAsState(if (shown) 0f else 12f, tween(450), label = "y")
    Box(modifier.graphicsLayer { this.alpha = alpha; translationY = dy.dp.toPx() }) { content() }
}

/** Carte plate shadcn : fond blanc, bordure fine, ombre discrète, coins doux. */
@Composable
private fun ShadcnCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .shadow(3.dp, RoundedCornerShape(14.dp), ambientColor = Color(0x14101420), spotColor = Color(0x14101420))
            .clip(RoundedCornerShape(14.dp))
            .background(ScCard)
            .border(1.dp, ScBorder, RoundedCornerShape(14.dp))
            .padding(22.dp),
        content = content
    )
}

@Composable
private fun Badge(text: String, positive: Boolean) {
    Box(
        Modifier.clip(RoundedCornerShape(999.dp))
            .background(if (positive) ScGreenSoft else ScRedSoft)
            .padding(horizontal = 9.dp, vertical = 3.dp)
    ) {
        Text(text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (positive) ScGreen else ScRed)
    }
}

@Composable
private fun ScButton(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, primary: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val bg = when {
        primary -> ScPrimary
        hovered -> ScMuted
        else -> ScCard
    }
    val fg = if (primary) Color.White else ScFg
    Row(
        modifier
            .height(42.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .then(if (primary) Modifier else Modifier.border(1.dp, ScBorder, RoundedCornerShape(10.dp)))
            .pointerHoverIcon(PointerIcon.Hand)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = fg, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, fontSize = 13.5.sp, fontWeight = FontWeight.Medium, color = fg, maxLines = 1)
    }
}

/** Carte de statistique shadcn : icône + mini-courbe, valeur, badge de tendance. */
@Composable
private fun StatCard(
    label: String,
    amount: BigDecimal,
    amountColor: Color,
    series: List<Float>,
    sparkColor: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val lift by animateFloatAsState(if (hovered) -3f else 0f, tween(200), label = "lift")
    Column(
        modifier
            .graphicsLayer { translationY = lift.dp.toPx() }
            .hoverable(interaction)
            .shadow(if (hovered) 10.dp else 3.dp, RoundedCornerShape(14.dp), ambientColor = Color(0x14101420), spotColor = Color(0x14101420))
            .clip(RoundedCornerShape(14.dp))
            .background(ScCard)
            .border(1.dp, ScBorder, RoundedCornerShape(14.dp))
            .padding(20.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(34.dp).clip(RoundedCornerShape(9.dp)).background(ScMuted), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = ScMutedFg, modifier = Modifier.size(17.dp))
            }
            if (series.size >= 2) Sparkline(series, sparkColor, Modifier.width(60.dp).height(28.dp))
        }
        Spacer(Modifier.height(14.dp))
        CurrencyAmount(amount = amount, style = MaterialTheme.typography.titleLarge, color = amountColor)
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (series.size >= 2) {
                val prev = series[series.size - 2]; val delta = series.last() - prev
                val pct = if (kotlin.math.abs(prev) > 0.01f) "${(kotlin.math.abs(delta / prev) * 100).toInt()}%" else "—"
                Badge((if (delta >= 0f) "▲ " else "▼ ") + pct, delta >= 0f)
                Spacer(Modifier.width(8.dp))
            }
            Text(label, fontSize = 12.5.sp, color = ScMutedFg)
        }
    }
}

@Composable
private fun ProjectionRow(label: String, amount: BigDecimal) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 13.sp, color = ScMutedFg)
        CurrencyAmount(
            amount = amount,
            style = MaterialTheme.typography.titleMedium,
            color = if (amount >= BigDecimal.ZERO) ScFg else ScRed
        )
    }
}

@Composable
private fun TxnRow(tx: Transaction, dateFormatter: DateTimeFormatter, onClick: () -> Unit) {
    val isIncome = tx.transactionType == TransactionType.INCOME
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).background(ScMuted), contentAlignment = Alignment.Center) {
            Icon(
                if (isIncome) Icons.Filled.SouthWest else Icons.Filled.NorthEast,
                null, tint = if (isIncome) ScGreen else ScMutedFg, modifier = Modifier.size(16.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(tx.title, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = ScFg, maxLines = 1)
            Text(
                (tx.categoryName ?: "Sans catégorie") + " · " + tx.date.format(dateFormatter),
                fontSize = 12.sp, color = ScMutedFg, maxLines = 1
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            (if (isIncome) "+" else "−") + String.format(java.util.Locale.FRANCE, "%,.2f €", tx.amount),
            fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = if (isIncome) ScGreen else ScFg
        )
    }
}

@Composable
private fun VacationBanner(ui: HomeUiState) {
    ShadcnCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(ScPrimary.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.BeachAccess, null, tint = ScPrimary, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "Mode vacances" + (ui.vacationDaysRemaining?.let { " · $it j restants" } ?: ""),
                    fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = ScFg
                )
                Text(
                    String.format(java.util.Locale.FRANCE, "Dépensé %,.0f €", ui.vacationSpent) +
                        (if (ui.vacationBudget > BigDecimal.ZERO) String.format(java.util.Locale.FRANCE, " / %,.0f €", ui.vacationBudget) else ""),
                    fontSize = 12.sp, color = ScMutedFg
                )
            }
        }
    }
}

// ===================== Graphiques =====================

/** Courbe LISSE (Catmull-Rom → Bézier). close=true ferme l'aire jusqu'à bottom. */
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
            Brush.verticalGradient(listOf(color.copy(alpha = 0.20f), color.copy(alpha = 0f))))
        drawPath(buildSmooth(points, false, 0f), color,
            style = Stroke(2f.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawCircle(color, radius = 2.4.dp.toPx(), center = points.last())
    }
}

@Composable
private fun NetWorthChart(history: List<Pair<String, BigDecimal>>) {
    val current = history.lastOrNull()?.second ?: BigDecimal.ZERO
    val firstValue = history.firstOrNull()?.second ?: BigDecimal.ZERO
    val totalChange = current.subtract(firstValue)

    ShadcnCard(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            Column {
                Text("Patrimoine", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = ScFg)
                Text("12 derniers mois", fontSize = 12.sp, color = ScMutedFg)
            }
            Badge(
                (if (totalChange >= BigDecimal.ZERO) "▲ +" else "▼ ") +
                    String.format(java.util.Locale.FRANCE, "%,.0f €", totalChange.abs()),
                totalChange >= BigDecimal.ZERO
            )
        }
        Spacer(Modifier.height(6.dp))
        CurrencyAmount(amount = current, style = MaterialTheme.typography.headlineSmall, color = ScFg)
        Spacer(Modifier.height(14.dp))

        val values = history.map { it.second.toFloat() }
        Canvas(Modifier.fillMaxWidth().height(150.dp)) {
            if (values.size < 2) return@Canvas
            val minV = values.minOrNull() ?: 0f
            val maxV = values.maxOrNull() ?: 0f
            val rng = (maxV - minV).coerceAtLeast(0.01f)
            val padY = size.height * 0.16f
            val padX = 4.dp.toPx()
            val stepX = (size.width - padX * 2) / (values.size - 1)
            val pts = values.mapIndexed { i, v ->
                Offset(padX + i * stepX, size.height - padY - ((v - minV) / rng) * (size.height - padY * 2))
            }
            val dash = PathEffect.dashPathEffect(floatArrayOf(2f, 7f), 0f)
            for (g in 1..3) {
                val y = size.height / 4f * g
                drawLine(ScBorder, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f, pathEffect = dash)
            }
            drawPath(buildSmooth(pts, true, size.height),
                Brush.verticalGradient(listOf(ScPrimary.copy(alpha = 0.22f), ScPrimary.copy(alpha = 0f))))
            drawPath(buildSmooth(pts, false, 0f), ScPrimary,
                style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawCircle(ScPrimary.copy(alpha = 0.18f), radius = 8.dp.toPx(), center = pts.last())
            drawCircle(ScPrimary, radius = 4.dp.toPx(), center = pts.last())
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth()) {
            history.forEach { (label, _) ->
                Text(label, fontSize = 10.sp, color = ScMutedFg, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, maxLines = 1)
            }
        }
    }
}
