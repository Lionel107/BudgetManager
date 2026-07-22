package com.budgetmanager.presentation.screens.categories

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.unit.dp
import com.budgetmanager.domain.model.Category
import com.budgetmanager.domain.model.TransactionType
import com.budgetmanager.presentation.components.*
import com.budgetmanager.presentation.navigation.NavigationState
import com.budgetmanager.presentation.theme.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import org.koin.core.context.GlobalContext.get as getKoin
import java.math.BigDecimal

data class CategoryUiState(
    val incomeCategories: List<Category> = emptyList(),
    val expenseCategories: List<Category> = emptyList(),
    val showAddDialog: Boolean = false,
    val editingCategory: Category? = null,
    val showDeleteConfirm: Long? = null,
    val isLoading: Boolean = true
)

class CategoryScreenState {
    var uiState by mutableStateOf(CategoryUiState())
        private set

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    init { loadData() }

    private fun loadData() {
        scope.launch {
            val koin = getKoin()
            val categoryRepo = koin.get<com.budgetmanager.data.repository.CategoryRepository>()
            categoryRepo.getAllCategories().collectLatest { cats ->
                uiState = uiState.copy(
                    incomeCategories = cats.filter { it.categoryType == TransactionType.INCOME },
                    expenseCategories = cats.filter { it.categoryType == TransactionType.EXPENSE },
                    isLoading = false
                )
            }
        }
    }

    fun showAddDialog() { uiState = uiState.copy(showAddDialog = true, editingCategory = null) }
    fun showEditDialog(cat: Category) { uiState = uiState.copy(editingCategory = cat, showAddDialog = true) }
    fun hideDialog() { uiState = uiState.copy(showAddDialog = false, editingCategory = null) }
    fun showDeleteConfirm(id: Long) { uiState = uiState.copy(showDeleteConfirm = id) }
    fun hideDeleteConfirm() { uiState = uiState.copy(showDeleteConfirm = null) }

    fun saveCategory(name: String, type: TransactionType, color: String, parentId: Long?) {
        scope.launch {
            val koin = getKoin()
            val categoryRepo = koin.get<com.budgetmanager.data.repository.CategoryRepository>()
            val editing = uiState.editingCategory
            if (editing != null) {
                categoryRepo.updateCategory(editing.copy(name = name, categoryType = type, color = color, parentId = parentId))
            } else {
                categoryRepo.createCategory(Category(name = name, categoryType = type, color = color, parentId = parentId))
            }
            hideDialog()
        }
    }

    fun deleteCategory(id: Long) {
        val cat = (uiState.incomeCategories + uiState.expenseCategories).find { it.id == id }
        scope.launch {
            val koin = getKoin()
            val categoryRepo = koin.get<com.budgetmanager.data.repository.CategoryRepository>()
            categoryRepo.deleteCategory(id)
            hideDeleteConfirm()
            if (cat != null) {
                UndoBus.show(UndoableAction(
                    message = "Categorie \"${cat.name}\" supprimee",
                    onUndo = { categoryRepo.restoreCategory(id) }
                ))
            }
        }
    }

    fun dispose() { scope.cancel() }
}

@Composable
fun CategoryScreen(navigationState: NavigationState) {
    val state = remember { CategoryScreenState() }
    DisposableEffect(Unit) { onDispose { state.dispose() } }

    val ui = state.uiState

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
            // Header
            Text(
                text = "Categories",
                style = MaterialTheme.typography.headlineMedium,
                color = NeumorphicTextPrimary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 10.dp)
            )

            Spacer(Modifier.height(12.dp))

            // Expense categories
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Categories de depenses",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = ExpenseColor
                )
                NeumorphicButton(
                    text = "Ajouter depense",
                    icon = Icons.Filled.Add,
                    onClick = { state.showAddDialog() },
                    isPrimary = false
                )
            }
            Spacer(Modifier.height(12.dp))

            if (ui.expenseCategories.isEmpty()) {
                NeumorphicCard(modifier = Modifier.fillMaxWidth()) {
                    Text("Aucune categorie de depense. Cliquez sur 'Ajouter' pour en creer une.", style = MaterialTheme.typography.bodyMedium, color = NeumorphicTextTertiary)
                }
            } else {
                CategoryGrid(
                    categories = ui.expenseCategories,
                    onEdit = { state.showEditDialog(it) },
                    onDelete = { state.showDeleteConfirm(it.id) }
                )
            }

            Spacer(Modifier.height(24.dp))

            // Income categories
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Categories de revenus",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = IncomeColor
                )
                NeumorphicButton(
                    text = "Ajouter revenu",
                    icon = Icons.Filled.Add,
                    onClick = { state.showAddDialog() },
                    isPrimary = false
                )
            }
            Spacer(Modifier.height(12.dp))

            if (ui.incomeCategories.isEmpty()) {
                NeumorphicCard(modifier = Modifier.fillMaxWidth()) {
                    Text("Aucune categorie de revenu. Cliquez sur 'Ajouter' pour en creer une.", style = MaterialTheme.typography.bodyMedium, color = NeumorphicTextTertiary)
                }
            } else {
                CategoryGrid(
                    categories = ui.incomeCategories,
                    onEdit = { state.showEditDialog(it) },
                    onDelete = { state.showDeleteConfirm(it.id) }
                )
            }

            Spacer(Modifier.height(32.dp))
    }

    if (ui.showAddDialog) {
        CategoryFormDialog(
            category = ui.editingCategory,
            allCategories = ui.expenseCategories + ui.incomeCategories,
            onSave = { name, type, color, parentId -> state.saveCategory(name, type, color, parentId) },
            onDismiss = { state.hideDialog() }
        )
    }

    ui.showDeleteConfirm?.let { id ->
        ConfirmDialog(
            title = "Supprimer la catégorie",
            message = "Êtes-vous sûr de vouloir supprimer cette catégorie ?",
            onConfirm = { state.deleteCategory(id) },
            onDismiss = { state.hideDeleteConfirm() }
        )
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun CategoryGrid(
    categories: List<Category>,
    onEdit: (Category) -> Unit,
    onDelete: (Category) -> Unit
) {
    // Group by parent: top-level first, children grouped under them
    val parents = categories.filter { it.parentId == null }
    val childrenByParent = categories.filter { it.parentId != null }.groupBy { it.parentId!! }
    val parentIds = parents.map { it.id }.toSet()
    val orphans = categories.filter { it.parentId != null && it.parentId !in parentIds }

    // Parents without children — pack them in a flow row (compact)
    val standaloneParents = parents.filter { childrenByParent[it.id].isNullOrEmpty() }
    val parentsWithChildren = parents.filter { !childrenByParent[it.id].isNullOrEmpty() }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (standaloneParents.isNotEmpty()) {
            androidx.compose.foundation.layout.FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                standaloneParents.forEach { cat ->
                    CategoryCard(cat, onEdit = { onEdit(cat) }, onDelete = { onDelete(cat) })
                }
            }
        }

        // Parents with children: one section per parent, parent on top + children below
        parentsWithChildren.forEach { parent ->
            val children = childrenByParent[parent.id] ?: emptyList()
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                CategoryCard(parent, onEdit = { onEdit(parent) }, onDelete = { onDelete(parent) })
                androidx.compose.foundation.layout.FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(start = 28.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    children.forEach { child ->
                        CategoryCard(child, onEdit = { onEdit(child) }, onDelete = { onDelete(child) }, isChild = true)
                    }
                }
            }
        }

        // Orphans — group in a flow row too
        if (orphans.isNotEmpty()) {
            androidx.compose.foundation.layout.FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                orphans.forEach { cat ->
                    CategoryCard(cat, onEdit = { onEdit(cat) }, onDelete = { onDelete(cat) })
                }
            }
        }
    }
}

@Composable
private fun CategoryCard(
    category: Category,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    isChild: Boolean = false,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    // Compact card with edit/delete always visible (no hover requirement)
    NeumorphicCard(
        modifier = modifier
            .wrapContentWidth()
            .hoverable(interactionSource),
        elevation = if (isHovered) 8.dp else 5.dp
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (isChild) {
                Text(
                    "└",
                    color = NeumorphicTextTertiary,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(end = 6.dp)
                )
            }
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(parseColor(category.color).copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier.size(12.dp).clip(CircleShape).background(parseColor(category.color))
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                category.name,
                style = MaterialTheme.typography.bodyLarge,
                color = NeumorphicTextPrimary,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = onEdit, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Filled.Edit, "Modifier", tint = NeumorphicTextSecondary, modifier = Modifier.size(16.dp))
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Filled.Delete, "Supprimer", tint = ExpenseColor.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun CategoryFormDialog(
    category: Category?,
    allCategories: List<Category>,
    onSave: (String, TransactionType, String, Long?) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(category?.name ?: "") }
    var type by remember { mutableStateOf(category?.categoryType ?: TransactionType.EXPENSE) }
    var parentId by remember { mutableStateOf(category?.parentId) }
    var selectedColorIndex by remember {
        mutableStateOf(
            category?.let { cat ->
                CategoryColors.indexOfFirst {
                    String.format("#%06X", 0xFFFFFF and it.hashCode()) == cat.color
                }.takeIf { it >= 0 }
            } ?: 0
        )
    }
    // Parent must be same type and not the category itself (avoid self-reference loops)
    val parentCandidates = allCategories.filter {
        it.categoryType == type && it.id != category?.id && it.parentId == null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (category != null) "Modifier la catégorie" else "Nouvelle catégorie",
                style = MaterialTheme.typography.headlineMedium,
                color = NeumorphicTextPrimary
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                NeumorphicTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = "Nom",
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Type selector
                Text("Type", style = MaterialTheme.typography.labelLarge, color = NeumorphicTextSecondary)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(TransactionType.EXPENSE to "Dépense", TransactionType.INCOME to "Revenu").forEach { (t, label) ->
                        FilterChip(
                            label = label,
                            isSelected = type == t,
                            onClick = { type = t; parentId = null }
                        )
                    }
                }

                // Parent category (optional - for hierarchy)
                if (parentCandidates.isNotEmpty()) {
                    SearchableDropdown(
                        label = "Categorie parente (optionnel)",
                        selectedId = parentId,
                        items = listOf(0L to "(aucune - categorie principale)") +
                                parentCandidates.map { it.id to it.name },
                        onSelect = { parentId = if (it == 0L) null else it },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Color picker
                Text("Couleur", style = MaterialTheme.typography.labelLarge, color = NeumorphicTextSecondary)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    CategoryColors.chunked(8).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            row.forEachIndexed { localIndex, color ->
                                val globalIndex = CategoryColors.indexOf(color)
                                Box(
                                    modifier = Modifier
                                        .size(30.dp)
                                        .clip(CircleShape)
                                        .background(color)
                                        .clickable { selectedColorIndex = globalIndex }
                                        .then(
                                            if (globalIndex == selectedColorIndex) Modifier
                                            else Modifier
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (globalIndex == selectedColorIndex) {
                                        Icon(
                                            Icons.Filled.Check,
                                            null,
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            NeumorphicButton(
                text = if (category != null) "Modifier" else "Créer",
                onClick = {
                    val colorHex = CategoryColors.getOrNull(selectedColorIndex)?.let { c ->
                        val argb = c.hashCode()
                        String.format("#%06X", 0xFFFFFF and argb)
                    } ?: "#6C63FF"
                    onSave(name, type, colorHex, parentId)
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
