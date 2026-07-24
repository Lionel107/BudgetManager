package com.budgetmanager.presentation.screens.advisor

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.budgetmanager.data.repository.CategoryRepository
import com.budgetmanager.data.repository.PlanLine
import com.budgetmanager.data.repository.PlannerRepository
import com.budgetmanager.presentation.components.NeumorphicButton
import com.budgetmanager.presentation.components.NeumorphicCard
import com.budgetmanager.presentation.components.NeumorphicTextField
import com.budgetmanager.presentation.theme.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext.get as getKoin
import java.util.Locale

private fun eurMonth(v: Double): String = String.format(Locale.FRANCE, "%,.0f €/mois", v)
private fun amountToField(v: Double): String = String.format(Locale.US, "%.0f", v)
private fun parseAmount(s: String): Double = s.replace(",", ".").trim().toDoubleOrNull() ?: 0.0

/** Une ligne éditable du plan (catégorie + rationale + montant modifiable à la main). */
private data class MetaLine(val category: String, val rationale: String, val essential: Boolean, val isNew: Boolean)

@Composable
fun BudgetPlannerSection() {
    val repo = remember { getKoin().get<PlannerRepository>() }
    val catRepo = remember { getKoin().get<CategoryRepository>() }
    val scope = rememberCoroutineScope()

    var existingNames by remember { mutableStateOf<Set<String>>(emptySet()) }
    LaunchedEffect(Unit) {
        runCatching { existingNames = catRepo.getAllCategories().first().map { it.name }.toSet() }
    }

    var meta by remember { mutableStateOf<List<MetaLine>>(emptyList()) }
    val amounts = remember { mutableStateMapOf<String, String>() }  // catégorie -> montant (texte)
    var summary by remember { mutableStateOf("") }
    var monthlyIncome by remember { mutableStateOf(0.0) }

    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var remarks by remember { mutableStateOf("") }
    var applyMsg by remember { mutableStateOf<String?>(null) }
    var proposed by remember { mutableStateOf(false) }

    var newCatName by remember { mutableStateOf("") }
    var newCatAmount by remember { mutableStateOf("") }

    fun currentPlanLines(): List<PlanLine> = meta.map {
        PlanLine(category = it.category, monthlyAmount = parseAmount(amounts[it.category] ?: "0"), rationale = it.rationale, essential = it.essential)
    }

    fun loadResponse(plan: List<PlanLine>) {
        amounts.clear()
        meta = plan.map { p ->
            amounts[p.category] = amountToField(p.monthlyAmount)
            MetaLine(p.category, p.rationale, p.essential, isNew = p.category !in existingNames)
        }
    }

    fun propose(withRemarks: String?) {
        if (loading) return
        loading = true; error = null; applyMsg = null
        scope.launch {
            try {
                val res = repo.proposePlan(remarks = withRemarks, currentPlan = if (proposed) currentPlanLines() else null)
                if (res.error != null) error = res.error
                else {
                    summary = res.summary; monthlyIncome = res.monthlyIncome
                    loadResponse(res.plan); proposed = true; remarks = ""
                }
            } catch (e: Exception) {
                error = e.message ?: "Échec de la proposition."
            } finally { loading = false }
        }
    }

    NeumorphicCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("🧩 ", style = MaterialTheme.typography.titleMedium)
            Text("Planificateur de budget", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = NeumorphicPrimary)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "L'IA propose un budget mensuel (elle peut créer de nouvelles catégories). Tu peux modifier les montants à la main, lui faire une remarque, puis l'appliquer.",
            style = MaterialTheme.typography.bodySmall, color = NeumorphicTextTertiary
        )
        Spacer(Modifier.height(14.dp))

        if (loading) {
            Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = NeumorphicPrimary)
            }
        } else if (!proposed) {
            NeumorphicButton(text = "Proposer un plan de budget", icon = Icons.Filled.AutoAwesome, onClick = { propose(null) })
        } else {
            if (summary.isNotBlank()) {
                Text(summary, style = MaterialTheme.typography.bodyMedium, color = NeumorphicTextPrimary)
                Spacer(Modifier.height(6.dp))
            }
            if (monthlyIncome > 0) {
                Text("Revenu mensuel moyen : ${eurMonth(monthlyIncome)}", style = MaterialTheme.typography.bodySmall, color = NeumorphicTextSecondary)
            }
            Spacer(Modifier.height(12.dp))

            meta.forEach { line -> PlanEditRow(line, amounts) }

            // Ajout manuel d'une catégorie
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                NeumorphicTextField(value = newCatName, onValueChange = { newCatName = it }, label = "", placeholder = "Nouvelle catégorie", modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                NeumorphicTextField(value = newCatAmount, onValueChange = { newCatAmount = it }, label = "", placeholder = "€/mois", suffix = "€", modifier = Modifier.width(110.dp))
                Spacer(Modifier.width(8.dp))
                NeumorphicButton(text = "", icon = Icons.Filled.Add, isPrimary = false, onClick = {
                    val n = newCatName.trim()
                    if (n.isNotBlank() && meta.none { it.category.equals(n, true) }) {
                        amounts[n] = if (newCatAmount.isBlank()) "0" else newCatAmount.trim()
                        meta = meta + MetaLine(n, "Ajout manuel", true, isNew = n !in existingNames)
                        newCatName = ""; newCatAmount = ""
                    }
                })
            }

            // Totaux
            val total = meta.sumOf { parseAmount(amounts[it.category] ?: "0") }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Total dépenses budgétées", fontWeight = FontWeight.SemiBold)
                Text(eurMonth(total), fontWeight = FontWeight.SemiBold)
            }
            if (monthlyIncome > 0) {
                val save = monthlyIncome - total
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Épargne dégagée / mois", color = NeumorphicTextSecondary)
                    Text(eurMonth(save), color = if (save >= 0) IncomeColor else ExpenseColor, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(Modifier.height(16.dp))
            NeumorphicTextField(
                value = remarks, onValueChange = { remarks = it },
                label = "Une remarque pour l'IA ? (ex. « garde 150 € de loisirs », « je veux épargner plus »)",
                placeholder = "Ta remarque…", singleLine = false, maxLines = 3, modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NeumorphicButton(text = "Adapter avec l'IA", icon = Icons.Filled.Edit, isPrimary = false, onClick = { propose(remarks) })
                NeumorphicButton(text = "Appliquer ce budget", icon = Icons.Filled.Check, onClick = {
                    scope.launch {
                        applyMsg = null
                        try {
                            val n = repo.applyPlan(currentPlanLines())
                            existingNames = catRepo.getAllCategories().first().map { it.name }.toSet()
                            applyMsg = "✅ $n budget(s) appliqué(s). Ajuste-les si besoin dans l'onglet Budgets."
                        } catch (e: Exception) {
                            applyMsg = "Échec de l'application : ${e.message}"
                        }
                    }
                })
            }
        }

        error?.let {
            Spacer(Modifier.height(10.dp))
            Text(it, color = NeumorphicBudgetAlert, style = MaterialTheme.typography.bodySmall)
        }
        applyMsg?.let {
            Spacer(Modifier.height(10.dp))
            Text(it, color = if (it.startsWith("✅")) IncomeColor else NeumorphicBudgetAlert, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun PlanEditRow(line: MetaLine, amounts: androidx.compose.runtime.snapshots.SnapshotStateMap<String, String>) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(line.category, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                if (line.isNew) {
                    Spacer(Modifier.width(6.dp))
                    Text("nouvelle", style = MaterialTheme.typography.labelSmall, color = NeumorphicPrimary)
                } else if (!line.essential) {
                    Spacer(Modifier.width(6.dp))
                    Text("superflu", style = MaterialTheme.typography.labelSmall, color = NeumorphicTextTertiary)
                }
            }
            if (line.rationale.isNotBlank()) {
                Text(line.rationale, style = MaterialTheme.typography.labelSmall, color = NeumorphicTextTertiary)
            }
        }
        Spacer(Modifier.width(8.dp))
        NeumorphicTextField(
            value = amounts[line.category] ?: "",
            onValueChange = { amounts[line.category] = it },
            label = "", suffix = "€", modifier = Modifier.width(110.dp)
        )
    }
}
