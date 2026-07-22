package com.budgetmanager.presentation.screens.export

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.budgetmanager.data.repository.AccountRepository
import com.budgetmanager.data.repository.TransactionRepository
import com.budgetmanager.domain.model.Account
import com.budgetmanager.domain.model.Transaction
import com.budgetmanager.domain.model.TransactionType
import com.budgetmanager.presentation.components.*
import com.budgetmanager.presentation.navigation.NavigationState
import com.budgetmanager.presentation.theme.*
import com.budgetmanager.util.ExportService
import com.budgetmanager.util.ExportFileFormat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import org.koin.core.context.GlobalContext.get as getKoin
import java.awt.Desktop
import java.io.File
import java.math.BigDecimal
import java.text.NumberFormat
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

data class ExportUiState(
    val transactions: List<Transaction> = emptyList(),
    val accounts: List<Account> = emptyList(),
    val availableMonths: List<YearMonth> = emptyList(),
    val selectedMonths: Set<YearMonth> = emptySet(),
    val format: ExportFileFormat = ExportFileFormat.HTML,
    val fileName: String = "releve_${YearMonth.now()}.html",
    val isLoading: Boolean = true,
    val isExporting: Boolean = false,
    val message: String? = null,
    val isError: Boolean = false,
    val exportedFilePath: String? = null
)

class ExportScreenState {
    var uiState by mutableStateOf(ExportUiState())
        private set

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    init { loadData() }

    private fun loadData() {
        scope.launch {
            try {
                val koin = getKoin()
                val transactionRepo = koin.get<TransactionRepository>()
                val accountRepo = koin.get<AccountRepository>()

                launch {
                    accountRepo.getAllAccounts().collectLatest { accs ->
                        uiState = uiState.copy(accounts = accs)
                    }
                }

                transactionRepo.getAllTransactions().collectLatest { txs ->
                    try {
                        val months = if (txs.isEmpty()) {
                            listOf(YearMonth.now())
                        } else {
                            val earliest = txs.minOf { YearMonth.from(it.date) }
                            val latest = YearMonth.now()
                            generateSequence(earliest) { it.plusMonths(1) }
                                .takeWhile { !it.isAfter(latest) }
                                .toList()
                        }
                        uiState = uiState.copy(
                            transactions = txs,
                            availableMonths = months,
                            isLoading = false
                        )
                    } catch (e: Exception) {
                        uiState = uiState.copy(
                            isLoading = false,
                            message = "Erreur chargement: ${e.message}",
                            isError = true
                        )
                    }
                }
            } catch (e: Exception) {
                uiState = uiState.copy(isLoading = false, message = "Erreur: ${e.message}", isError = true)
            }
        }
    }

    fun toggleMonth(month: YearMonth) {
        val s = uiState.selectedMonths.toMutableSet()
        if (s.contains(month)) s.remove(month) else s.add(month)
        uiState = uiState.copy(selectedMonths = s, message = null)
    }

    fun selectCurrentMonth() { uiState = uiState.copy(selectedMonths = setOf(YearMonth.now()), message = null) }

    fun selectQuarter() {
        val now = YearMonth.now()
        val months = (0L..2L).map { now.minusMonths(it) }.filter { uiState.availableMonths.contains(it) }.toSet()
        uiState = uiState.copy(selectedMonths = months, message = null)
    }

    fun selectFullYear() {
        val now = YearMonth.now()
        val months = (1..now.monthValue).map { YearMonth.of(now.year, it) }.filter { uiState.availableMonths.contains(it) }.toSet()
        uiState = uiState.copy(selectedMonths = months, message = null)
    }

    fun selectAll() { uiState = uiState.copy(selectedMonths = uiState.availableMonths.toSet(), message = null) }
    fun clearSelection() { uiState = uiState.copy(selectedMonths = emptySet(), message = null) }
    fun updateFileName(n: String) { uiState = uiState.copy(fileName = n, message = null) }

    fun setFormat(f: ExportFileFormat) {
        val ext = when (f) { ExportFileFormat.CSV -> ".csv"; ExportFileFormat.HTML -> ".html" }
        val base = uiState.fileName.substringBeforeLast(".")
        uiState = uiState.copy(format = f, fileName = "$base$ext", message = null)
    }

    fun exportFile() {
        if (uiState.selectedMonths.isEmpty()) {
            uiState = uiState.copy(message = "Selectionnez au moins un mois.", isError = true)
            return
        }

        val months = uiState.selectedMonths.toList()
        val txs = uiState.transactions.toList()
        val accs = uiState.accounts.toList()
        val fmt = uiState.format
        var fName = uiState.fileName.trim()
        val ext = when (fmt) { ExportFileFormat.CSV -> ".csv"; ExportFileFormat.HTML -> ".html" }
        if (!fName.lowercase().endsWith(ext)) fName += ext

        val desktopDir = File(System.getProperty("user.home"), "Desktop")
        if (!desktopDir.exists()) desktopDir.mkdirs()
        val outputFile = File(desktopDir, fName)

        uiState = uiState.copy(isExporting = true, message = null, exportedFilePath = null)

        scope.launch(Dispatchers.IO) {
            try {
                ExportService().export(
                    format = fmt,
                    months = months,
                    transactions = txs,
                    accounts = accs,
                    outputPath = outputFile.absolutePath
                )
                val label = when (fmt) { ExportFileFormat.CSV -> "CSV"; ExportFileFormat.HTML -> "HTML" }
                withContext(Dispatchers.Main) {
                    uiState = uiState.copy(
                        isExporting = false,
                        message = "$label exporte : ${outputFile.absolutePath}",
                        isError = false,
                        exportedFilePath = outputFile.absolutePath
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    uiState = uiState.copy(
                        isExporting = false,
                        message = "Erreur: ${e.message}",
                        isError = true
                    )
                }
            }
        }
    }

    fun openFile() {
        val p = uiState.exportedFilePath ?: return
        try { Desktop.getDesktop().open(File(p)) } catch (_: Exception) {}
    }

    fun dispose() { scope.cancel() }
}

@Composable
fun ExportScreen(navigationState: NavigationState) {
    val state = remember { ExportScreenState() }
    DisposableEffect(Unit) { onDispose { state.dispose() } }

    val ui = state.uiState
    val monthFmt = remember { DateTimeFormatter.ofPattern("MMM yyyy", Locale.FRANCE) }
    val currFmt = remember { NumberFormat.getCurrencyInstance(Locale.FRANCE) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
            Text("Exporter les comptes", style = MaterialTheme.typography.headlineMedium, color = NeumorphicTextPrimary, fontWeight = FontWeight.Bold)
        }

        if (ui.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = NeumorphicPrimary)
            }
        } else {

        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Quick selection
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(label = "Mois en cours", isSelected = ui.selectedMonths == setOf(YearMonth.now()), onClick = { state.selectCurrentMonth() })
                FilterChip(label = "Trimestre", isSelected = false, onClick = { state.selectQuarter() })
                FilterChip(label = "Annee", isSelected = false, onClick = { state.selectFullYear() })
                FilterChip(label = "Tout", isSelected = ui.selectedMonths.size == ui.availableMonths.size && ui.selectedMonths.isNotEmpty(), onClick = { state.selectAll() })
                if (ui.selectedMonths.isNotEmpty()) {
                    FilterChip(label = "Effacer", isSelected = false, onClick = { state.clearSelection() })
                }
            }

            // Month grid
            if (ui.availableMonths.isNotEmpty()) {
                NeumorphicCard(modifier = Modifier.fillMaxWidth(), elevation = 6.dp) {
                    Text("${ui.selectedMonths.size} mois selectionne(s)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = NeumorphicTextPrimary)
                    Spacer(Modifier.height(12.dp))
                    val byYear = ui.availableMonths.groupBy { it.year }.toSortedMap(reverseOrder())
                    byYear.forEach { (year, months) ->
                        Text(year.toString(), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = NeumorphicTextSecondary)
                        Spacer(Modifier.height(6.dp))
                        months.chunked(6).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 6.dp)) {
                                row.forEach { m ->
                                    FilterChip(
                                        label = m.format(monthFmt).replaceFirstChar { it.uppercaseChar() },
                                        isSelected = m in ui.selectedMonths,
                                        onClick = { state.toggleMonth(m) }
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }

            // Preview
            if (ui.selectedMonths.isNotEmpty()) {
                val selTx = ui.transactions.filter { YearMonth.from(it.date) in ui.selectedMonths }
                val inc = selTx.filter { it.transactionType == TransactionType.INCOME }.fold(BigDecimal.ZERO) { a, t -> a.add(t.amount) }
                val exp = selTx.filter { it.transactionType == TransactionType.EXPENSE }.fold(BigDecimal.ZERO) { a, t -> a.add(t.amount) }
                val net = inc.subtract(exp)

                NeumorphicCard(modifier = Modifier.fillMaxWidth(), elevation = 4.dp) {
                    Text("Apercu", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = NeumorphicTextPrimary)
                    Spacer(Modifier.height(10.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text("Transactions", style = MaterialTheme.typography.labelMedium, color = NeumorphicTextSecondary)
                            Text("${selTx.size}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        }
                        Column(Modifier.weight(1f)) {
                            Text("Revenus", style = MaterialTheme.typography.labelMedium, color = NeumorphicTextSecondary)
                            Text(currFmt.format(inc), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = IncomeColor)
                        }
                        Column(Modifier.weight(1f)) {
                            Text("Depenses", style = MaterialTheme.typography.labelMedium, color = NeumorphicTextSecondary)
                            Text(currFmt.format(exp), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = ExpenseColor)
                        }
                        Column(Modifier.weight(1f)) {
                            Text("Solde net", style = MaterialTheme.typography.labelMedium, color = NeumorphicTextSecondary)
                            Text(currFmt.format(net), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = if (net >= BigDecimal.ZERO) IncomeColor else ExpenseColor)
                        }
                    }
                }
            }

            // Format selector + File name + Export
            NeumorphicCard(modifier = Modifier.fillMaxWidth(), elevation = 4.dp) {
                Text("Format d'export", style = MaterialTheme.typography.labelLarge, color = NeumorphicTextSecondary)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        label = "HTML (imprimable en PDF)",
                        isSelected = ui.format == ExportFileFormat.HTML,
                        onClick = { state.setFormat(ExportFileFormat.HTML) }
                    )
                    FilterChip(
                        label = "CSV (tableur)",
                        isSelected = ui.format == ExportFileFormat.CSV,
                        onClick = { state.setFormat(ExportFileFormat.CSV) }
                    )
                }
                Spacer(Modifier.height(14.dp))
                Text("Nom du fichier", style = MaterialTheme.typography.labelLarge, color = NeumorphicTextSecondary)
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    NeumorphicTextField(
                        value = ui.fileName,
                        onValueChange = { state.updateFileName(it) },
                        label = "",
                        placeholder = "releve",
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    NeumorphicButton(
                        text = if (ui.isExporting) "Export..." else "Exporter",
                        icon = Icons.Filled.Download,
                        onClick = { state.exportFile() },
                        enabled = !ui.isExporting && ui.selectedMonths.isNotEmpty() && ui.fileName.isNotBlank()
                    )
                }
                Text("Le fichier sera enregistre sur le Bureau", style = MaterialTheme.typography.labelSmall, color = NeumorphicTextTertiary, modifier = Modifier.padding(top = 6.dp))
            }

            // Result
            ui.message?.let { msg ->
                NeumorphicCard(
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = if (ui.isError) ExpenseColor.copy(alpha = 0.08f) else IncomeColor.copy(alpha = 0.08f)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Icon(if (ui.isError) Icons.Filled.Error else Icons.Filled.CheckCircle, null, tint = if (ui.isError) ExpenseColor else IncomeColor, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(msg, style = MaterialTheme.typography.bodyMedium, color = if (ui.isError) ExpenseColor else IncomeColor, modifier = Modifier.weight(1f))
                        if (!ui.isError && ui.exportedFilePath != null) {
                            Spacer(Modifier.width(12.dp))
                            NeumorphicButton(text = "Ouvrir", icon = Icons.Filled.OpenInNew, onClick = { state.openFile() })
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }

        } // end else
    }
}
