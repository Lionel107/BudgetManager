package com.budgetmanager.presentation.screens.templates

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.budgetmanager.domain.model.Category
import com.budgetmanager.domain.model.Template
import com.budgetmanager.domain.model.TransactionType
import com.budgetmanager.presentation.components.*
import com.budgetmanager.presentation.navigation.NavigationState
import com.budgetmanager.presentation.theme.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import org.koin.core.context.GlobalContext.get as getKoin
import java.math.BigDecimal

data class TemplateUiState(
    val templates: List<Template> = emptyList(),
    val categories: List<Category> = emptyList(),
    val isLoading: Boolean = true,
    val showAddDialog: Boolean = false,
    val editingTemplate: Template? = null
)

class TemplateScreenState {
    var uiState by mutableStateOf(TemplateUiState())
        private set

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    init { loadData() }

    private fun loadData() {
        scope.launch {
            val koin = getKoin()
            val templateRepo = koin.get<com.budgetmanager.data.repository.TemplateRepository>()
            val categoryRepo = koin.get<com.budgetmanager.data.repository.CategoryRepository>()

            launch {
                templateRepo.getAll().collectLatest { templates ->
                    uiState = uiState.copy(templates = templates, isLoading = false)
                }
            }
            launch {
                categoryRepo.getAllCategories().collectLatest { cats ->
                    uiState = uiState.copy(categories = cats)
                }
            }
        }
    }

    fun showAddDialog() { uiState = uiState.copy(showAddDialog = true, editingTemplate = null) }
    fun showEditDialog(t: Template) { uiState = uiState.copy(editingTemplate = t, showAddDialog = true) }
    fun hideDialog() { uiState = uiState.copy(showAddDialog = false, editingTemplate = null) }

    fun saveTemplate(name: String, amount: BigDecimal?, categoryId: Long?, type: TransactionType) {
        scope.launch {
            val koin = getKoin()
            val templateRepo = koin.get<com.budgetmanager.data.repository.TemplateRepository>()
            val editing = uiState.editingTemplate
            if (editing != null) {
                templateRepo.update(editing.copy(
                    name = name, defaultAmount = amount,
                    categoryId = categoryId, transactionType = type
                ))
            } else {
                templateRepo.create(Template(
                    name = name, defaultAmount = amount,
                    categoryId = categoryId, transactionType = type
                ))
            }
            hideDialog()
        }
    }

    fun deleteTemplate(id: Long) {
        val t = uiState.templates.find { it.id == id }
        scope.launch {
            val koin = getKoin()
            val templateRepo = koin.get<com.budgetmanager.data.repository.TemplateRepository>()
            templateRepo.delete(id)
            if (t != null) {
                UndoBus.show(UndoableAction(
                    message = "Template \"${t.name}\" supprime",
                    onUndo = { templateRepo.create(t.copy(id = 0)) }
                ))
            }
        }
    }

    fun dispose() { scope.cancel() }
}

@Composable
fun TemplateScreen(navigationState: NavigationState) {
    val state = remember { TemplateScreenState() }
    DisposableEffect(Unit) { onDispose { state.dispose() } }

    val ui = state.uiState
    var deleteId by remember { mutableStateOf<Long?>(null) }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Templates", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = NeumorphicTextPrimary)
            NeumorphicButton(text = "Nouveau", icon = Icons.Filled.Add, onClick = { state.showAddDialog() })
        }

        Text(
            "Templates de saisie rapide pour tes transactions recurrentes (cafe du matin, abonnement, etc.). Clique pour pre-remplir une nouvelle transaction.",
            style = MaterialTheme.typography.bodyMedium,
            color = NeumorphicTextSecondary,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        if (ui.templates.isEmpty() && !ui.isLoading) {
            EmptyState(
                message = "Aucun template.\nCree des modeles pour saisir tes depenses recurrentes en un clic.",
                icon = Icons.Filled.LibraryBooks,
                actionText = "Creer un template",
                onAction = { state.showAddDialog() }
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ui.templates.forEach { t ->
                    TemplateCard(
                        template = t,
                        onUse = {
                            navigationState.navigateToNewTransactionFromTemplate(t.id)
                        },
                        onEdit = { state.showEditDialog(t) },
                        onDelete = { deleteId = t.id }
                    )
                }
            }
        }
    }

    if (ui.showAddDialog) {
        TemplateFormDialog(
            template = ui.editingTemplate,
            categories = ui.categories,
            onSave = { name, amount, catId, type -> state.saveTemplate(name, amount, catId, type) },
            onDismiss = { state.hideDialog() }
        )
    }

    deleteId?.let { id ->
        ConfirmDialog(
            title = "Supprimer le template",
            message = "Supprimer ce template ? Les transactions deja creees ne seront pas affectees.",
            onConfirm = { state.deleteTemplate(id); deleteId = null },
            onDismiss = { deleteId = null }
        )
    }
}

@Composable
private fun TemplateCard(
    template: Template,
    onUse: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    NeumorphicCard(modifier = Modifier.fillMaxWidth(), elevation = 6.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(10.dp))
                    .background(
                        when (template.transactionType) {
                            TransactionType.INCOME -> IncomeColor.copy(alpha = 0.15f)
                            TransactionType.EXPENSE -> ExpenseColor.copy(alpha = 0.15f)
                            TransactionType.TRANSFER -> TransferColor.copy(alpha = 0.15f)
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.LibraryBooks, null,
                    tint = when (template.transactionType) {
                        TransactionType.INCOME -> IncomeColor
                        TransactionType.EXPENSE -> ExpenseColor
                        TransactionType.TRANSFER -> TransferColor
                    },
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(template.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = NeumorphicTextPrimary)
                Text(
                    buildString {
                        if (template.defaultAmount != null) append("${template.defaultAmount} EUR ")
                        if (template.categoryName != null) append("• ${template.categoryName} ")
                        if (template.usageCount > 0) append("• utilise ${template.usageCount}x")
                    }.trim(),
                    style = MaterialTheme.typography.bodySmall,
                    color = NeumorphicTextTertiary
                )
            }
            NeumorphicButton(
                text = "Utiliser",
                icon = Icons.Filled.PlayArrow,
                onClick = onUse,
                isPrimary = false
            )
            Spacer(Modifier.width(6.dp))
            IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.Edit, "Modifier", tint = NeumorphicTextSecondary, modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.Delete, "Supprimer", tint = ExpenseColor.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun TemplateFormDialog(
    template: Template?,
    categories: List<Category>,
    onSave: (String, BigDecimal?, Long?, TransactionType) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(template?.name ?: "") }
    var amountStr by remember { mutableStateOf(template?.defaultAmount?.toPlainString() ?: "") }
    var categoryId by remember { mutableStateOf(template?.categoryId) }
    var type by remember { mutableStateOf(template?.transactionType ?: TransactionType.EXPENSE) }
    val filteredCategories = categories.filter {
        when (type) {
            TransactionType.INCOME -> it.categoryType == TransactionType.INCOME
            else -> it.categoryType == TransactionType.EXPENSE
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (template != null) "Modifier le template" else "Nouveau template", style = MaterialTheme.typography.headlineMedium, color = NeumorphicTextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                NeumorphicTextField(
                    value = name, onValueChange = { name = it },
                    label = "Nom du template (ex: Cafe du matin)",
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )

                // Type selector
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(TransactionType.EXPENSE to "Depense", TransactionType.INCOME to "Revenu").forEach { (t, label) ->
                        FilterChip(label = label, isSelected = type == t, onClick = { type = t; categoryId = null })
                    }
                }

                NeumorphicTextField(
                    value = amountStr,
                    onValueChange = { amountStr = it.filter { c -> c.isDigit() || c == '.' || c == ',' } },
                    label = "Montant par defaut (optionnel)",
                    suffix = "EUR",
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )

                SearchableDropdown(
                    label = "Categorie (optionnel)",
                    selectedId = categoryId,
                    items = filteredCategories.map { it.id to it.name },
                    onSelect = { categoryId = it },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            NeumorphicButton(
                text = if (template != null) "Modifier" else "Creer",
                onClick = {
                    val amount = amountStr.replace(",", ".").toBigDecimalOrNull()
                    onSave(name.trim(), amount, categoryId, type)
                },
                enabled = name.isNotBlank()
            )
        },
        dismissButton = {
            NeumorphicButton(text = "Annuler", onClick = onDismiss, isPrimary = false)
        },
        containerColor = NeumorphicElevated,
        shape = RoundedCornerShape(16.dp)
    )
}
