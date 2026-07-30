package com.budgetmanager.presentation.screens.home

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.budgetmanager.domain.model.Account
import com.budgetmanager.domain.model.Transaction
import com.budgetmanager.domain.model.TransactionType
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
// Charte "Émeraude Tech" (thème clair, futuriste). Local à l'Accueil pour l'instant.
// ============================================================================
private val EmBg = Color(0xFFFFFFFF)
private val EmPanel = Color(0xFFFFFFFF)
private val EmBorder = Color(0xFFE1E7E4)
private val EmFg = Color(0xFF0C1512)
private val EmMuted = Color(0xFF5B6B65)
private val EmMuted2 = Color(0xFF94A39C)
private val EmAccent = Color(0xFF0FB985)
private val EmAccent2 = Color(0xFF06D6C4)
private val EmBright = Color(0xFF12E6A0)
private val EmRed = Color(0xFFF43F5E)
private val Mono = FontFamily.Monospace

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

    Box(Modifier.fillMaxSize().background(EmBg)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 16.dp)
        ) {
            Appear(0) {
                Column {
                    Text("Tableau de bord", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = EmFg, letterSpacing = (-0.5).sp)
                    Spacer(Modifier.height(4.dp))
                    Text("Ton aperçu financier", fontSize = 13.sp, color = EmMuted)
                }
            }
            Spacer(Modifier.height(24.dp))

            if (ui.vacationActive) {
                Appear(40) { VacationBanner(ui) }
                Spacer(Modifier.height(16.dp))
            }

            // Solde (héro)
            Appear(60) { HeroCard(ui, navigationState) }
            Spacer(Modifier.height(16.dp))

            // Trio
            Appear(120) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    StatCard("Revenus", ui.monthlyIncome, EmAccent, ui.incomeSeries, EmAccent, Icons.Filled.SouthWest, Modifier.weight(1f))
                    StatCard("Dépenses", ui.monthlyExpenses, EmFg, ui.expenseSeries, EmRed, Icons.Filled.NorthEast, Modifier.weight(1f))
                    StatCard("Net du mois", ui.monthlyIncome.subtract(ui.monthlyExpenses), EmFg, ui.netSeries, EmAccent2, Icons.Filled.ShowChart, Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(22.dp))

            // Actions
            Appear(180) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TechButton("Nouvelle transaction", Icons.Filled.Add, primary = true, modifier = Modifier.weight(1f)) { navigationState.navigateTo(Screen.ADD_TRANSACTION) }
                    TechButton("Transfert", Icons.Filled.SwapHoriz, primary = false, modifier = Modifier.weight(1f)) { navigationState.navigateTo(Screen.TRANSFER) }
                    TechButton("Récurrents", Icons.Filled.Repeat, primary = false, modifier = Modifier.weight(1f)) { navigationState.navigateTo(Screen.RECURRING) }
                    TechButton("Analyser", Icons.Filled.BarChart, primary = false, modifier = Modifier.weight(1f)) { navigationState.navigateTo(Screen.ANALYTICS) }
                }
            }
            Spacer(Modifier.height(22.dp))

            // Patrimoine + Projection
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                if (ui.netWorthHistory.isNotEmpty()) {
                    Appear(240, Modifier.weight(1.6f)) { NetWorthChart(ui.netWorthHistory) }
                }
                if (ui.monthlyIncome > BigDecimal.ZERO || ui.monthlyExpenses > BigDecimal.ZERO) {
                    Appear(280, Modifier.weight(1f)) {
                        TechCard(Modifier.fillMaxWidth()) {
                            Lab("// Projection")
                            Text("au rythme actuel", fontSize = 11.sp, color = EmMuted2)
                            Spacer(Modifier.height(16.dp))
                            ProjectionRow("1 mois", ui.projection1Month)
                            Spacer(Modifier.height(12.dp))
                            ProjectionRow("3 mois", ui.projection3Months)
                            Spacer(Modifier.height(12.dp))
                            ProjectionRow("6 mois", ui.projection6Months)
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))

            // Transactions
            Appear(340) {
                TechCard(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Transactions récentes", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = EmFg)
                        Text("Voir tout →", fontSize = 12.sp, color = EmMuted,
                            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { navigationState.navigateTo(Screen.TRANSACTIONS) }.padding(4.dp))
                    }
                    Spacer(Modifier.height(4.dp))
                    if (ui.recentTransactions.isEmpty()) {
                        Text(if (ui.isLoading) "Chargement…" else "Aucune transaction ce mois-ci.", fontSize = 13.sp, color = EmMuted2, modifier = Modifier.padding(vertical = 14.dp))
                    } else {
                        ui.recentTransactions.forEachIndexed { i, tx ->
                            TxnRow(tx, dateFormatter) { navigationState.navigateToEditTransaction(tx.id) }
                            if (i < ui.recentTransactions.lastIndex) Box(Modifier.fillMaxWidth().height(1.dp).background(EmBorder))
                        }
                    }
                }
            }
            Spacer(Modifier.height(30.dp))
        }
    }
}

// ===================== Composants =====================

@Composable
private fun Appear(delayMillis: Int, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(delayMillis.toLong()); shown = true }
    val alpha by animateFloatAsState(if (shown) 1f else 0f, tween(500), label = "a")
    val dy by animateFloatAsState(if (shown) 0f else 14f, tween(500), label = "y")
    Box(modifier.graphicsLayer { this.alpha = alpha; translationY = dy.dp.toPx() }) { content() }
}

@Composable
private fun Lab(text: String) {
    Text(text, fontFamily = Mono, fontSize = 11.sp, letterSpacing = 2.sp, color = EmMuted2, fontWeight = FontWeight.SemiBold)
}

/** Carte tech : blanc, bordure fine, coins doux ; survol = bordure émeraude + glow. */
@Composable
private fun TechCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val border = if (hovered) EmAccent.copy(alpha = 0.55f) else EmBorder
    Column(
        modifier
            .hoverable(interaction)
            .then(if (hovered) Modifier.shadow(14.dp, RoundedCornerShape(18.dp), ambientColor = EmAccent, spotColor = EmAccent) else Modifier)
            .clip(RoundedCornerShape(18.dp))
            .background(EmPanel)
            .border(1.dp, border, RoundedCornerShape(18.dp))
            .padding(20.dp),
        content = content
    )
}

@Composable
private fun HeroCard(ui: HomeUiState, navigationState: NavigationState) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier.fillMaxWidth()
            .shadow(18.dp, RoundedCornerShape(18.dp), ambientColor = EmAccent, spotColor = EmAccent)
            .clip(RoundedCornerShape(18.dp))
            .background(EmPanel)
            .border(1.dp, EmAccent.copy(alpha = 0.25f), RoundedCornerShape(18.dp))
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(interaction, null) { navigationState.navigateToNewAccount() }
            .padding(22.dp)
    ) {
        // barre d'accent gauche
        Box(Modifier.align(Alignment.CenterStart).width(4.dp).height(64.dp).clip(RoundedCornerShape(4.dp))
            .background(Brush.verticalGradient(listOf(EmAccent, EmAccent2))))
        Column(Modifier.padding(start = 14.dp)) {
            Lab("// Solde total")
            Spacer(Modifier.height(10.dp))
            AmountMono(ui.totalBalance, 34.sp, brush = Brush.linearGradient(listOf(EmFg, EmAccent, EmAccent2)))
            Spacer(Modifier.height(10.dp))
            val net = ui.monthlyIncome.subtract(ui.monthlyExpenses)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    (if (net >= BigDecimal.ZERO) "▲ +" else "▼ ") + String.format(java.util.Locale.FRANCE, "%,.0f €", net.abs()),
                    fontFamily = Mono, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (net >= BigDecimal.ZERO) EmAccent else EmRed
                )
                Spacer(Modifier.width(8.dp))
                Text("ce mois · ${ui.accounts.size} comptes actifs", fontSize = 12.sp, color = EmMuted)
            }
        }
    }
}

@Composable
private fun StatCard(
    label: String, amount: BigDecimal, amountColor: Color,
    series: List<Float>, sparkColor: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier
) {
    TechCard(modifier) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).border(1.dp, EmBorder, RoundedCornerShape(11.dp)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = EmMuted, modifier = Modifier.size(16.dp))
            }
            if (series.size >= 2) Sparkline(series, sparkColor, Modifier.width(60.dp).height(28.dp))
        }
        Spacer(Modifier.height(12.dp))
        AmountMono(amount, 22.sp, color = amountColor, weight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (series.size >= 2) {
                val prev = series[series.size - 2]; val delta = series.last() - prev
                val pct = if (kotlin.math.abs(prev) > 0.01f) "${(kotlin.math.abs(delta / prev) * 100).toInt()}%" else "—"
                Badge((if (delta >= 0f) "▲ " else "▼ ") + pct, delta >= 0f)
                Spacer(Modifier.width(8.dp))
            }
            Lab(label)
        }
    }
}

@Composable
private fun Badge(text: String, positive: Boolean) {
    val c = if (positive) EmAccent else EmRed
    Box(Modifier.clip(RoundedCornerShape(999.dp)).background(c.copy(alpha = 0.13f)).padding(horizontal = 9.dp, vertical = 3.dp)) {
        Text(text, fontFamily = Mono, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = c)
    }
}

@Composable
private fun TechButton(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, primary: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val shape = RoundedCornerShape(14.dp)
    val base = if (primary) Modifier
        .then(if (hovered) Modifier.shadow(16.dp, shape, ambientColor = EmAccent, spotColor = EmAccent) else Modifier.shadow(8.dp, shape, ambientColor = EmAccent, spotColor = EmAccent))
        .clip(shape).background(Brush.linearGradient(listOf(EmAccent, EmAccent2)))
    else Modifier.clip(shape).background(EmPanel).border(1.dp, if (hovered) EmAccent.copy(alpha = 0.6f) else EmBorder, shape)
    Row(
        modifier.height(44.dp).then(base)
            .pointerHoverIcon(PointerIcon.Hand).hoverable(interaction)
            .clickable(interaction, null, onClick = onClick)
            .padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = if (primary) Color(0xFF04140E) else EmFg, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (primary) Color(0xFF04140E) else EmFg, maxLines = 1)
    }
}

@Composable
private fun ProjectionRow(label: String, amount: BigDecimal) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontFamily = Mono, fontSize = 12.sp, color = EmMuted2)
        AmountMono(amount, 15.sp, color = if (amount >= BigDecimal.ZERO) EmFg else EmRed, weight = FontWeight.SemiBold, animated = false)
    }
}

@Composable
private fun TxnRow(tx: Transaction, dateFormatter: DateTimeFormatter, onClick: () -> Unit) {
    val isIncome = tx.transactionType == TransactionType.INCOME
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).border(1.dp, EmBorder, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
            Icon(if (isIncome) Icons.Filled.SouthWest else Icons.Filled.NorthEast, null, tint = if (isIncome) EmAccent else EmMuted, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(tx.title, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = EmFg, maxLines = 1)
            Text((tx.categoryName ?: "Sans catégorie") + " · " + tx.date.format(dateFormatter), fontSize = 12.sp, color = EmMuted2, maxLines = 1)
        }
        Spacer(Modifier.width(8.dp))
        Text(
            (if (isIncome) "+" else "−") + String.format(java.util.Locale.FRANCE, "%,.2f €", tx.amount),
            fontFamily = Mono, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (isIncome) EmAccent else EmFg
        )
    }
}

@Composable
private fun VacationBanner(ui: HomeUiState) {
    TechCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(EmAccent.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.BeachAccess, null, tint = EmAccent, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Mode vacances" + (ui.vacationDaysRemaining?.let { " · $it j restants" } ?: ""), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = EmFg)
                Text(
                    String.format(java.util.Locale.FRANCE, "Dépensé %,.0f €", ui.vacationSpent) +
                        (if (ui.vacationBudget > BigDecimal.ZERO) String.format(java.util.Locale.FRANCE, " / %,.0f €", ui.vacationBudget) else ""),
                    fontSize = 12.sp, color = EmMuted2
                )
            }
        }
    }
}

/** Montant en typo mono, animé (compteur) au premier affichage. */
@Composable
private fun AmountMono(
    amount: BigDecimal, fontSize: TextUnit, brush: Brush? = null, color: Color = EmFg,
    weight: FontWeight = FontWeight.Bold, animated: Boolean = true
) {
    val target = amount.toFloat()
    var started by remember { mutableStateOf(!animated) }
    LaunchedEffect(target) { started = true }
    val v by animateFloatAsState(if (started) target else 0f, tween(1100), label = "amt")
    val shown = if (animated) v else target
    val style = if (brush != null)
        TextStyle(brush = brush, fontFamily = Mono, fontWeight = weight, fontSize = fontSize)
    else
        TextStyle(color = color, fontFamily = Mono, fontWeight = weight, fontSize = fontSize)
    Text(String.format(java.util.Locale.FRANCE, "%,.2f €", shown), style = style, maxLines = 1)
}

// ===================== Graphiques =====================

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

/** Position interpolée le long d'une polyligne (t = 0..1). */
private fun pointAlong(points: List<Offset>, t: Float): Offset {
    if (points.size < 2) return points.firstOrNull() ?: Offset.Zero
    val segLens = (0 until points.size - 1).map { (points[it + 1] - points[it]).getDistance() }
    val total = segLens.sum().coerceAtLeast(0.001f)
    var d = t.coerceIn(0f, 1f) * total
    for (i in segLens.indices) {
        if (d <= segLens[i]) {
            val f = if (segLens[i] > 0f) d / segLens[i] else 0f
            return Offset(points[i].x + (points[i + 1].x - points[i].x) * f, points[i].y + (points[i + 1].y - points[i].y) * f)
        }
        d -= segLens[i]
    }
    return points.last()
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
        val points = values.mapIndexed { i, v -> Offset(i * stepX, size.height - padY - ((v - minV) / range) * (size.height - padY * 2)) }
        drawPath(buildSmooth(points, true, size.height), Brush.verticalGradient(listOf(color.copy(alpha = 0.20f), color.copy(alpha = 0f))))
        val line = buildSmooth(points, false, 0f)
        drawPath(line, color.copy(alpha = 0.20f), style = Stroke(5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(line, color, style = Stroke(2f.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawCircle(color, radius = 2.4.dp.toPx(), center = points.last())
    }
}

@Composable
private fun NetWorthChart(history: List<Pair<String, BigDecimal>>) {
    val current = history.lastOrNull()?.second ?: BigDecimal.ZERO
    val firstValue = history.firstOrNull()?.second ?: BigDecimal.ZERO
    val totalChange = current.subtract(firstValue)

    // point de données qui circule
    val infinite = rememberInfiniteTransition(label = "flow")
    val flow by infinite.animateFloat(0f, 1f, infiniteRepeatable(tween(2800), RepeatMode.Restart), label = "flowT")

    TechCard(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            Column {
                Lab("// Patrimoine")
                Spacer(Modifier.height(6.dp))
                AmountMono(current, 20.sp, color = EmFg, animated = false)
            }
            Badge(
                (if (totalChange >= BigDecimal.ZERO) "▲ +" else "▼ ") + String.format(java.util.Locale.FRANCE, "%,.0f €", totalChange.abs()),
                totalChange >= BigDecimal.ZERO
            )
        }
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
            val pts = values.mapIndexed { i, v -> Offset(padX + i * stepX, size.height - padY - ((v - minV) / rng) * (size.height - padY * 2)) }
            val dash = PathEffect.dashPathEffect(floatArrayOf(2f, 7f), 0f)
            for (g in 1..3) {
                val y = size.height / 4f * g
                drawLine(EmBorder, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f, pathEffect = dash)
            }
            drawPath(buildSmooth(pts, true, size.height), Brush.verticalGradient(listOf(EmAccent.copy(alpha = 0.22f), EmAccent.copy(alpha = 0f))))
            val line = buildSmooth(pts, false, 0f)
            drawPath(line, EmAccent.copy(alpha = 0.20f), style = Stroke(7.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawPath(line, EmBright, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawCircle(EmBright.copy(alpha = 0.18f), radius = 8.dp.toPx(), center = pts.last())
            drawCircle(EmBright, radius = 4.dp.toPx(), center = pts.last())
            // point qui circule le long de la courbe
            val fp = pointAlong(pts, flow)
            drawCircle(EmBright.copy(alpha = 0.25f), radius = 7.dp.toPx(), center = fp)
            drawCircle(EmBright, radius = 3.dp.toPx(), center = fp)
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth()) {
            history.forEach { (label, _) ->
                Text(label, fontFamily = Mono, fontSize = 10.sp, color = EmMuted2, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, maxLines = 1)
            }
        }
    }
}
