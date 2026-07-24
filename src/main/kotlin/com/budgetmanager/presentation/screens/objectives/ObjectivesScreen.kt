package com.budgetmanager.presentation.screens.objectives

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import com.budgetmanager.data.repository.ObjectiveRepository
import com.budgetmanager.domain.model.Objective
import com.budgetmanager.domain.model.ObjectiveType
import com.budgetmanager.presentation.components.NeumorphicButton
import com.budgetmanager.presentation.components.NeumorphicCard
import com.budgetmanager.presentation.components.NeumorphicTextField
import com.budgetmanager.presentation.components.SectionHeader
import com.budgetmanager.presentation.theme.*
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext.get as getKoin
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Locale

@Composable
fun ObjectivesScreen() {
    val repo = remember { getKoin().get<ObjectiveRepository>() }
    val scope = rememberCoroutineScope()
    val objectives by remember { repo.getAll() }.collectAsState(initial = emptyList())
    var showDialog by remember { mutableStateOf(false) }
    var createError by remember { mutableStateOf<String?>(null) }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        SectionHeader(title = "Objectifs")
        Spacer(Modifier.height(4.dp))
        Text(
            "Définis tes buts (épargner un montant pour une date, ou ne pas dépasser un plafond). L'IA s'en sert pour te proposer un plan.",
            style = MaterialTheme.typography.bodyMedium, color = NeumorphicTextSecondary
        )
        Spacer(Modifier.height(12.dp))
        NeumorphicButton(text = "Nouvel objectif", icon = Icons.Filled.Add, onClick = { showDialog = true })
        createError?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = NeumorphicBudgetAlert, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(16.dp))

        if (objectives.isEmpty()) {
            NeumorphicCard(modifier = Modifier.fillMaxWidth()) {
                Text("Aucun objectif pour l'instant.", color = NeumorphicTextSecondary)
                Text("Clique « Nouvel objectif » pour commencer.", style = MaterialTheme.typography.bodySmall, color = NeumorphicTextTertiary)
            }
        } else {
            objectives.forEach { obj ->
                NeumorphicCard(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Flag, null, tint = NeumorphicPrimary, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(obj.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            val typeLabel = if (obj.type == ObjectiveType.SAVINGS) "Épargne" else "Plafond de dépense"
                            val dateLabel = obj.targetDate?.let { " · d'ici le $it" } ?: ""
                            Text(
                                "$typeLabel · ${eur(obj.targetAmount)}$dateLabel",
                                style = MaterialTheme.typography.bodySmall, color = NeumorphicTextSecondary
                            )
                        }
                        Icon(
                            Icons.Filled.Delete, "Supprimer", tint = ExpenseColor,
                            modifier = Modifier.size(22.dp).clickable {
                                scope.launch { runCatching { repo.delete(obj.id) } }
                            }
                        )
                    }
                }
            }
        }
    }

    if (showDialog) {
        ObjectiveDialog(
            onDismiss = { showDialog = false },
            onSave = { obj ->
                scope.launch {
                    createError = null
                    try { repo.create(obj) } catch (e: Exception) { createError = "Échec de la création : ${e.message}" }
                }
                showDialog = false
            }
        )
    }
}

@Composable
private fun ObjectiveDialog(onDismiss: () -> Unit, onSave: (Objective) -> Unit) {
    var title by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(ObjectiveType.SAVINGS) }
    var amount by remember { mutableStateOf("") }
    var dateStr by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            NeumorphicButton(text = "Créer", onClick = {
                val amt = amount.replace(",", ".").toBigDecimalOrNull()
                val date = dateStr.trim().takeIf { it.isNotBlank() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                when {
                    title.isBlank() -> error = "Donne un titre."
                    amt == null || amt <= BigDecimal.ZERO -> error = "Montant invalide."
                    dateStr.isNotBlank() && date == null -> error = "Date invalide (format AAAA-MM-JJ)."
                    else -> onSave(Objective(title = title.trim(), type = type, targetAmount = amt, targetDate = date))
                }
            })
        },
        dismissButton = { NeumorphicButton(text = "Annuler", isPrimary = false, onClick = onDismiss) },
        title = { Text("Nouvel objectif") },
        text = {
            Column {
                NeumorphicTextField(value = title, onValueChange = { title = it; error = null }, label = "Titre", placeholder = "Ex. Vacances d'été", modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))
                Text("Type", style = MaterialTheme.typography.labelMedium, color = NeumorphicTextSecondary)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NeumorphicButton(text = "Épargne", isPrimary = type == ObjectiveType.SAVINGS, onClick = { type = ObjectiveType.SAVINGS })
                    NeumorphicButton(text = "Plafond", isPrimary = type == ObjectiveType.SPENDING_LIMIT, onClick = { type = ObjectiveType.SPENDING_LIMIT })
                }
                Spacer(Modifier.height(12.dp))
                NeumorphicTextField(value = amount, onValueChange = { amount = it; error = null }, label = "Montant cible (€)", placeholder = "0", modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))
                NeumorphicTextField(value = dateStr, onValueChange = { dateStr = it; error = null }, label = "Date cible (optionnel)", placeholder = "AAAA-MM-JJ", modifier = Modifier.fillMaxWidth())
                if (error != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(error!!, color = NeumorphicBudgetAlert, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    )
}

private fun eur(v: BigDecimal): String = String.format(Locale.FRANCE, "%,.0f €", v)
