package com.budgetmanager.presentation.screens.`import`

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
import com.budgetmanager.data.repository.CategoryRepository
import com.budgetmanager.data.repository.TransactionRepository
import com.budgetmanager.presentation.components.*
import com.budgetmanager.presentation.navigation.NavigationState
import com.budgetmanager.presentation.theme.*
import com.budgetmanager.util.ImportResult
import com.budgetmanager.util.ImportService
import kotlinx.coroutines.*
import org.koin.core.context.GlobalContext.get as getKoin
import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

data class ImportUiState(
    val selectedFile: File? = null,
    val isImporting: Boolean = false,
    val result: ImportResult? = null,
    val errorMessage: String? = null
)

class ImportScreenState {
    var uiState by mutableStateOf(ImportUiState())
        private set

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    fun selectFile() {
        val chooser = JFileChooser().apply {
            dialogTitle = "Choisir un fichier CSV"
            fileFilter = FileNameExtensionFilter("Fichiers CSV (*.csv)", "csv")
            isAcceptAllFileFilterUsed = false
            // Start on Desktop
            currentDirectory = File(System.getProperty("user.home"), "Desktop")
        }
        val result = chooser.showOpenDialog(null)
        if (result == JFileChooser.APPROVE_OPTION) {
            uiState = uiState.copy(
                selectedFile = chooser.selectedFile,
                result = null,
                errorMessage = null
            )
        }
    }

    fun importFile() {
        val file = uiState.selectedFile ?: return
        if (!file.exists()) {
            uiState = uiState.copy(errorMessage = "Le fichier n'existe pas")
            return
        }

        uiState = uiState.copy(isImporting = true, result = null, errorMessage = null)

        scope.launch(Dispatchers.IO) {
            try {
                val koin = getKoin()
                val transactionRepo = koin.get<TransactionRepository>()
                val accountRepo = koin.get<AccountRepository>()
                val categoryRepo = koin.get<CategoryRepository>()

                val importService = ImportService(transactionRepo, accountRepo, categoryRepo)
                val importResult = importService.importCsv(file)

                withContext(Dispatchers.Main) {
                    uiState = uiState.copy(isImporting = false, result = importResult)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    uiState = uiState.copy(
                        isImporting = false,
                        errorMessage = "Erreur: ${e.message}"
                    )
                }
            }
        }
    }

    fun reset() {
        uiState = ImportUiState()
    }

    fun dispose() { scope.cancel() }
}

@Composable
fun ImportScreen(navigationState: NavigationState) {
    val state = remember { ImportScreenState() }
    DisposableEffect(Unit) { onDispose { state.dispose() } }

    val ui = state.uiState

    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
            Text(
                "Importer des donnees",
                style = MaterialTheme.typography.headlineMedium,
                color = NeumorphicTextPrimary,
                fontWeight = FontWeight.Bold
            )
        }

        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Info card
            NeumorphicCard(modifier = Modifier.fillMaxWidth(), elevation = 6.dp) {
                Row(verticalAlignment = Alignment.Top) {
                    Icon(Icons.Filled.Info, null, tint = NeumorphicPrimary, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            "Format attendu",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Le fichier CSV doit contenir les colonnes suivantes, separees par des points-virgules (;) :",
                            style = MaterialTheme.typography.bodyMedium,
                            color = NeumorphicTextSecondary
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Date ; Libelle ; Categorie ; Type ; Montant ; Compte",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = NeumorphicPrimary
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "- Date : format jj/mm/aaaa\n" +
                            "- Type : Revenu, Depense ou Transfert\n" +
                            "- Montant : nombre (ex: 42.50)\n" +
                            "- Les comptes et categories sont crees automatiquement s'ils n'existent pas\n" +
                            "- Compatible avec les fichiers exportes par Budget Manager",
                            style = MaterialTheme.typography.bodySmall,
                            color = NeumorphicTextTertiary
                        )
                    }
                }
            }

            // File selector
            NeumorphicCard(modifier = Modifier.fillMaxWidth(), elevation = 6.dp) {
                Text(
                    "Fichier a importer",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // File path display
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .neumorphicPressed(depth = 3.dp, borderRadius = 12.dp)
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                    ) {
                        Text(
                            text = ui.selectedFile?.name ?: "Aucun fichier selectionne",
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (ui.selectedFile != null) NeumorphicTextPrimary else NeumorphicTextTertiary
                        )
                    }

                    NeumorphicButton(
                        text = "Parcourir",
                        icon = Icons.Filled.FolderOpen,
                        onClick = { state.selectFile() },
                        isPrimary = false
                    )
                }

                if (ui.selectedFile != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Chemin : ${ui.selectedFile!!.absolutePath}",
                        style = MaterialTheme.typography.labelSmall,
                        color = NeumorphicTextTertiary
                    )
                }
            }

            // Import button
            if (ui.selectedFile != null && ui.result == null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    NeumorphicButton(
                        text = if (ui.isImporting) "Importation..." else "Importer",
                        icon = Icons.Filled.Upload,
                        onClick = { state.importFile() },
                        enabled = !ui.isImporting
                    )
                }
            }

            // Loading
            if (ui.isImporting) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = NeumorphicPrimary)
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Importation en cours...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = NeumorphicTextSecondary
                        )
                    }
                }
            }

            // Result
            ui.result?.let { result ->
                NeumorphicCard(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = 6.dp,
                    backgroundColor = if (result.errors.isEmpty())
                        IncomeColor.copy(alpha = 0.08f) else NeumorphicElevated
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (result.errors.isEmpty()) Icons.Filled.CheckCircle else Icons.Filled.Info,
                            null,
                            tint = if (result.errors.isEmpty()) IncomeColor else NeumorphicPrimary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "Importation terminee",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Lignes lues", style = MaterialTheme.typography.labelMedium, color = NeumorphicTextSecondary)
                            Text("${result.totalLines}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        }
                        Column(Modifier.weight(1f)) {
                            Text("Importees", style = MaterialTheme.typography.labelMedium, color = NeumorphicTextSecondary)
                            Text("${result.imported}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = IncomeColor)
                        }
                        Column(Modifier.weight(1f)) {
                            Text("Ignorees", style = MaterialTheme.typography.labelMedium, color = NeumorphicTextSecondary)
                            Text("${result.skipped}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = if (result.skipped > 0) ExpenseColor else NeumorphicTextPrimary)
                        }
                    }

                    // Newly created accounts (highlighted so user can verify the type)
                    if (result.createdAccounts.isNotEmpty()) {
                        Spacer(Modifier.height(16.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Info, null, tint = NeumorphicBudgetWarning, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "Comptes crees automatiquement (${result.createdAccounts.size}) :",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = NeumorphicBudgetWarning
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        result.createdAccounts.forEach { name ->
                            Text("• $name (type par defaut : Compte courant)",
                                style = MaterialTheme.typography.bodySmall,
                                color = NeumorphicTextSecondary)
                        }
                        Text(
                            "Pense a changer le type/devise dans la page Comptes si necessaire.",
                            style = MaterialTheme.typography.labelSmall,
                            color = NeumorphicTextTertiary,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }

                    if (result.createdCategories.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Info, null, tint = NeumorphicPrimary, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "Categories creees automatiquement (${result.createdCategories.size}) :",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = NeumorphicPrimary
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            result.createdCategories.joinToString(", "),
                            style = MaterialTheme.typography.bodySmall,
                            color = NeumorphicTextSecondary
                        )
                    }

                    // Errors detail
                    if (result.errors.isNotEmpty()) {
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "Details des erreurs :",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = ExpenseColor
                        )
                        Spacer(Modifier.height(8.dp))
                        result.errors.take(20).forEach { err ->
                            Text(
                                "- $err",
                                style = MaterialTheme.typography.bodySmall,
                                color = NeumorphicTextSecondary
                            )
                        }
                        if (result.errors.size > 20) {
                            Text(
                                "... et ${result.errors.size - 20} autres erreurs",
                                style = MaterialTheme.typography.bodySmall,
                                color = NeumorphicTextTertiary
                            )
                        }
                    }
                }

                // Reset button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)
                ) {
                    NeumorphicButton(
                        text = "Nouvel import",
                        icon = Icons.Filled.Refresh,
                        onClick = { state.reset() },
                        isPrimary = false
                    )
                }
            }

            // Error message
            ui.errorMessage?.let { msg ->
                NeumorphicCard(
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = ExpenseColor.copy(alpha = 0.08f)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Error, null, tint = ExpenseColor, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(msg, style = MaterialTheme.typography.bodyMedium, color = ExpenseColor)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}
