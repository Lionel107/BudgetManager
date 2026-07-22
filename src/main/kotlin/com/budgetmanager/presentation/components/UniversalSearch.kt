package com.budgetmanager.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.budgetmanager.presentation.navigation.NavigationState
import com.budgetmanager.presentation.navigation.Screen
import com.budgetmanager.presentation.theme.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext

sealed class SearchResult(val label: String, val subtitle: String) {
    data class TxResult(val txnId: Long, val title: String, val sub: String) : SearchResult(title, sub)
    data class AccountResult(val accountId: Long, val name: String, val sub: String) : SearchResult(name, sub)
    data class CategoryResult(val name: String, val sub: String) : SearchResult(name, sub)
    data class ScreenResult(val screen: Screen, val name: String, val sub: String) : SearchResult(name, sub)
}

private val SCREEN_INDEX = listOf(
    SearchResult.ScreenResult(Screen.HOME, "Accueil", "Tableau de bord"),
    SearchResult.ScreenResult(Screen.ACCOUNTS, "Comptes", "Gerer les comptes"),
    SearchResult.ScreenResult(Screen.TRANSACTIONS, "Transactions", "Liste des transactions"),
    SearchResult.ScreenResult(Screen.ADD_TRANSACTION, "Nouvelle transaction", "Ajouter une transaction"),
    SearchResult.ScreenResult(Screen.BUDGETS, "Budgets", "Gerer les budgets"),
    SearchResult.ScreenResult(Screen.ANALYTICS, "Analyse", "Statistiques"),
    SearchResult.ScreenResult(Screen.RECURRING, "Recurrents", "Transactions recurrentes"),
    SearchResult.ScreenResult(Screen.TRANSFER, "Transfert", "Transferer entre comptes"),
    SearchResult.ScreenResult(Screen.CATEGORIES, "Categories", "Gerer les categories"),
    SearchResult.ScreenResult(Screen.TEMPLATES, "Templates", "Modeles de transactions"),
    SearchResult.ScreenResult(Screen.EXPORT, "Exporter", "Exporter les donnees"),
    SearchResult.ScreenResult(Screen.IMPORT, "Importer", "Importer du CSV"),
    SearchResult.ScreenResult(Screen.SETTINGS, "Parametres", "Configuration de l'app")
)

@Composable
fun UniversalSearchDialog(
    navigationState: NavigationState,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<SearchResult>>(emptyList()) }
    val focusRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    LaunchedEffect(query) {
        if (query.isBlank()) {
            results = SCREEN_INDEX
            return@LaunchedEffect
        }
        scope.launch {
            try {
                val koin = GlobalContext.get()
                val txRepo = koin.get<com.budgetmanager.data.repository.TransactionRepository>()
                val accRepo = koin.get<com.budgetmanager.data.repository.AccountRepository>()
                val catRepo = koin.get<com.budgetmanager.data.repository.CategoryRepository>()

                val txs = txRepo.getAllTransactions().first()
                    .filter {
                        it.title.contains(query, true) ||
                        it.notes?.contains(query, true) == true ||
                        it.categoryName?.contains(query, true) == true ||
                        it.tags.any { t -> t.contains(query, true) }
                    }
                    .take(10)
                    .map { SearchResult.TxResult(
                        it.id,
                        it.title,
                        "${it.amount} EUR · ${it.categoryName ?: "Sans categorie"} · ${it.date.toLocalDate()}"
                    ) }

                val accs = accRepo.getAllAccounts().first()
                    .filter { it.name.contains(query, true) }
                    .take(5)
                    .map { SearchResult.AccountResult(it.id, it.name, "${it.balance} EUR") }

                val cats = catRepo.getAllCategories().first()
                    .filter { it.name.contains(query, true) }
                    .take(5)
                    .map { SearchResult.CategoryResult(it.name, it.categoryType.name) }

                val screens = SCREEN_INDEX.filter { it.label.contains(query, true) || it.subtitle.contains(query, true) }

                results = screens + accs + cats + txs
            } catch (_: Exception) { /* ignore */ }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NeumorphicElevated,
        shape = RoundedCornerShape(16.dp),
        title = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .neumorphicPressed(depth = 4.dp, borderRadius = 12.dp, backgroundColor = Color.White.copy(alpha = 0.6f))
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Search, null, tint = NeumorphicPrimary, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(10.dp))
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = NeumorphicTextPrimary),
                        cursorBrush = SolidColor(NeumorphicPrimary),
                        modifier = Modifier.weight(1f).focusRequester(focusRequester),
                        decorationBox = { inner ->
                            Box {
                                if (query.isEmpty()) {
                                    Text("Tape pour chercher (transactions, comptes, ecrans...)", color = NeumorphicTextTertiary, style = MaterialTheme.typography.bodyLarge)
                                }
                                inner()
                            }
                        }
                    )
                }
            }
        },
        text = {
            Box(modifier = Modifier.fillMaxWidth().heightIn(min = 200.dp, max = 500.dp)) {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(results) { result ->
                        SearchResultRow(result, onClick = {
                            when (result) {
                                is SearchResult.TxResult -> navigationState.navigateToEditTransaction(result.txnId)
                                is SearchResult.AccountResult -> navigationState.navigateTo(Screen.ACCOUNTS)
                                is SearchResult.CategoryResult -> navigationState.navigateTo(Screen.CATEGORIES)
                                is SearchResult.ScreenResult -> navigationState.navigateTo(result.screen)
                            }
                            onDismiss()
                        })
                    }
                    if (results.isEmpty() && query.isNotBlank()) {
                        item {
                            Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                                Text("Aucun resultat pour \"$query\"", color = NeumorphicTextTertiary)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            NeumorphicButton(text = "Fermer", onClick = onDismiss, isPrimary = false)
        }
    )
}

@Composable
private fun SearchResultRow(result: SearchResult, onClick: () -> Unit) {
    val (icon, color) = when (result) {
        is SearchResult.TxResult -> Icons.Filled.Receipt to NeumorphicPrimary
        is SearchResult.AccountResult -> Icons.Filled.AccountBalance to IncomeColor
        is SearchResult.CategoryResult -> Icons.Filled.Category to TransferColor
        is SearchResult.ScreenResult -> Icons.Filled.OpenInNew to NeumorphicTextSecondary
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(result.label, style = MaterialTheme.typography.bodyLarge, color = NeumorphicTextPrimary, fontWeight = FontWeight.Medium)
            Text(result.subtitle, style = MaterialTheme.typography.bodySmall, color = NeumorphicTextTertiary, maxLines = 1)
        }
    }
}
