package com.budgetmanager.presentation.screens.challenges

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
import androidx.compose.ui.unit.dp
import com.budgetmanager.domain.model.*
import com.budgetmanager.presentation.components.*
import com.budgetmanager.presentation.navigation.NavigationState
import com.budgetmanager.presentation.theme.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import org.koin.core.context.GlobalContext.get as getKoin
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class ChallengesUiState(
    val challenges: List<Challenge> = emptyList(),
    val progresses: List<ChallengeProgress> = emptyList(),
    val categories: List<Category> = emptyList(),
    val isLoading: Boolean = true,
    val showAddDialog: Boolean = false,
    val editing: Challenge? = null
)

class ChallengesScreenState {
    var uiState by mutableStateOf(ChallengesUiState())
        private set

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    init { loadData() }

    private fun loadData() {
        scope.launch {
            val koin = getKoin()
            val repo = koin.get<com.budgetmanager.data.repository.ChallengeRepository>()
            val catRepo = koin.get<com.budgetmanager.data.repository.CategoryRepository>()

            launch {
                catRepo.getAllCategories().collectLatest { cats ->
                    uiState = uiState.copy(categories = cats)
                }
            }
            launch {
                repo.getAll().collectLatest { ch ->
                    val progresses = repo.computeProgress(ch)
                    uiState = uiState.copy(challenges = ch, progresses = progresses, isLoading = false)
                }
            }
        }
    }

    fun showAdd() { uiState = uiState.copy(showAddDialog = true, editing = null) }
    fun showEdit(c: Challenge) { uiState = uiState.copy(showAddDialog = true, editing = c) }
    fun hideDialog() { uiState = uiState.copy(showAddDialog = false, editing = null) }

    fun save(
        title: String, description: String, type: ChallengeType, target: BigDecimal,
        categoryId: Long?, start: LocalDate, end: LocalDate
    ) {
        scope.launch {
            val koin = getKoin()
            val repo = koin.get<com.budgetmanager.data.repository.ChallengeRepository>()
            val editing = uiState.editing
            if (editing != null) {
                repo.update(editing.copy(
                    title = title, description = description.ifBlank { null },
                    type = type, targetAmount = target,
                    categoryId = categoryId, startDate = start, endDate = end
                ))
            } else {
                repo.create(Challenge(
                    title = title, description = description.ifBlank { null },
                    type = type, targetAmount = target,
                    categoryId = categoryId, startDate = start, endDate = end
                ))
            }
            hideDialog()
        }
    }

    fun delete(id: Long) {
        val c = uiState.challenges.find { it.id == id }
        scope.launch {
            val koin = getKoin()
            val repo = koin.get<com.budgetmanager.data.repository.ChallengeRepository>()
            repo.delete(id)
            if (c != null) {
                UndoBus.show(UndoableAction(
                    message = "Defi \"${c.title}\" supprime",
                    onUndo = { repo.create(c.copy(id = 0)) }
                ))
            }
        }
    }

    fun dispose() { scope.cancel() }
}

@Composable
fun ChallengesScreen(navigationState: NavigationState) {
    val state = remember { ChallengesScreenState() }
    DisposableEffect(Unit) { onDispose { state.dispose() } }

    val ui = state.uiState
    var deleteId by remember { mutableStateOf<Long?>(null) }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Defis", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = NeumorphicTextPrimary)
            NeumorphicButton(text = "Nouveau defi", icon = Icons.Filled.Flag, onClick = { state.showAdd() })
        }
        Text(
            "Mets-toi au defi de respecter une limite ou d'atteindre un objectif d'epargne sur une periode definie.",
            style = MaterialTheme.typography.bodyMedium,
            color = NeumorphicTextSecondary,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        if (ui.progresses.isEmpty() && !ui.isLoading) {
            EmptyState(
                message = "Aucun defi en cours.\nCommence par te lancer un challenge !",
                icon = Icons.Filled.EmojiEvents,
                actionText = "Creer un defi",
                onAction = { state.showAdd() }
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ui.progresses.forEach { p ->
                    ChallengeCard(
                        progress = p,
                        onEdit = { state.showEdit(p.challenge) },
                        onDelete = { deleteId = p.challenge.id }
                    )
                }
            }
        }
    }

    if (ui.showAddDialog) {
        ChallengeFormDialog(
            editing = ui.editing,
            categories = ui.categories,
            onSave = { t, d, type, target, catId, start, end -> state.save(t, d, type, target, catId, start, end) },
            onDismiss = { state.hideDialog() }
        )
    }

    deleteId?.let { id ->
        ConfirmDialog(
            title = "Supprimer ce defi ?",
            message = "Cette action ne peut etre annulee.",
            onConfirm = { state.delete(id); deleteId = null },
            onDismiss = { deleteId = null }
        )
    }
}

@Composable
private fun ChallengeCard(
    progress: ChallengeProgress,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val c = progress.challenge
    val ratio = progress.progressRatio
    val isSpendLimit = c.type == ChallengeType.SPEND_LIMIT
    val isOverTarget = if (isSpendLimit) ratio > 1f else false
    val isComplete = if (isSpendLimit) progress.daysRemaining == 0 && !isOverTarget
                      else ratio >= 1f

    val barColor = when {
        isComplete -> IncomeColor
        isOverTarget -> ExpenseColor
        !progress.onTrack -> NeumorphicBudgetWarning
        else -> NeumorphicPrimary
    }

    NeumorphicCard(modifier = Modifier.fillMaxWidth(), elevation = 6.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(barColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (isSpendLimit) Icons.Filled.Block else Icons.Filled.Savings,
                    null, tint = barColor, modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(c.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = NeumorphicTextPrimary)
                Text(
                    buildString {
                        append(if (isSpendLimit) "Limite : ${String.format("%.0f", c.targetAmount)} EUR"
                               else "Objectif : ${String.format("%.0f", c.targetAmount)} EUR")
                        if (c.categoryName != null) append(" · ${c.categoryName}")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = NeumorphicTextTertiary
                )
            }
            IconButton(onClick = onEdit, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Filled.Edit, "Modifier", tint = NeumorphicTextSecondary, modifier = Modifier.size(16.dp))
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Filled.Delete, "Supprimer", tint = ExpenseColor.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
            }
        }
        Spacer(Modifier.height(10.dp))
        BudgetProgressBar(
            spent = progress.currentAmount.toFloat(),
            limit = c.targetAmount.toFloat(),
            height = 10.dp
        )
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                "Jour ${progress.daysElapsed}/${progress.daysTotal}" +
                    if (progress.daysRemaining > 0) " · ${progress.daysRemaining} jour(s) restants" else " · termine",
                style = MaterialTheme.typography.labelMedium,
                color = NeumorphicTextSecondary
            )
            Text(
                when {
                    isComplete -> "✅ Reussi !"
                    isOverTarget -> "❌ Limite depassee"
                    progress.onTrack -> "✓ Sur la bonne voie"
                    else -> "⚠ En difficulte"
                },
                style = MaterialTheme.typography.labelMedium,
                color = barColor,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun ChallengeFormDialog(
    editing: Challenge?,
    categories: List<Category>,
    onSave: (String, String, ChallengeType, BigDecimal, Long?, LocalDate, LocalDate) -> Unit,
    onDismiss: () -> Unit
) {
    var title by remember { mutableStateOf(editing?.title ?: "") }
    var description by remember { mutableStateOf(editing?.description ?: "") }
    var type by remember { mutableStateOf(editing?.type ?: ChallengeType.SPEND_LIMIT) }
    var targetText by remember { mutableStateOf(editing?.targetAmount?.toPlainString() ?: "") }
    var categoryId by remember { mutableStateOf(editing?.categoryId) }
    var startDate by remember { mutableStateOf(editing?.startDate ?: LocalDate.now()) }
    var endDate by remember { mutableStateOf(editing?.endDate ?: LocalDate.now().plusDays(7)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (editing != null) "Modifier le defi" else "Nouveau defi", style = MaterialTheme.typography.headlineMedium, color = NeumorphicTextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                NeumorphicTextField(
                    value = title, onValueChange = { title = it },
                    label = "Titre (ex: Pas de cafe cette semaine)",
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                NeumorphicTextField(
                    value = description, onValueChange = { description = it },
                    label = "Description (optionnel)",
                    singleLine = false, minLines = 2, maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Type de defi", style = MaterialTheme.typography.labelMedium, color = NeumorphicTextSecondary)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(label = "Limiter une depense", isSelected = type == ChallengeType.SPEND_LIMIT, onClick = { type = ChallengeType.SPEND_LIMIT })
                    FilterChip(label = "Epargner un montant", isSelected = type == ChallengeType.SAVE_AMOUNT, onClick = { type = ChallengeType.SAVE_AMOUNT })
                }
                NeumorphicTextField(
                    value = targetText,
                    onValueChange = { targetText = it.filter { c -> c.isDigit() || c == '.' || c == ',' } },
                    label = if (type == ChallengeType.SPEND_LIMIT) "Limite a ne pas depasser (EUR)" else "Montant a epargner (EUR)",
                    suffix = "EUR",
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                if (type == ChallengeType.SPEND_LIMIT) {
                    SearchableDropdown(
                        label = "Categorie cible (optionnel)",
                        selectedId = categoryId,
                        items = listOf(0L to "(toutes les depenses)") +
                                categories.filter { it.categoryType == TransactionType.EXPENSE }.map { it.id to it.name },
                        onSelect = { categoryId = if (it == 0L) null else it },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Box(modifier = Modifier.weight(1f)) {
                        NeumorphicDatePicker(
                            date = startDate, onDateChange = { startDate = it },
                            label = "Debut", modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        NeumorphicDatePicker(
                            date = endDate, onDateChange = { endDate = it },
                            label = "Fin", modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        },
        confirmButton = {
            NeumorphicButton(
                text = if (editing != null) "Modifier" else "Creer",
                onClick = {
                    val target = targetText.replace(",", ".").toBigDecimalOrNull() ?: BigDecimal.ZERO
                    if (title.isNotBlank() && target > BigDecimal.ZERO && !endDate.isBefore(startDate)) {
                        onSave(title.trim(), description.trim(), type, target, categoryId, startDate, endDate)
                    }
                },
                enabled = title.isNotBlank() && targetText.isNotBlank() && !endDate.isBefore(startDate)
            )
        },
        dismissButton = {
            NeumorphicButton(text = "Annuler", onClick = onDismiss, isPrimary = false)
        },
        containerColor = NeumorphicElevated,
        shape = RoundedCornerShape(16.dp)
    )
}
