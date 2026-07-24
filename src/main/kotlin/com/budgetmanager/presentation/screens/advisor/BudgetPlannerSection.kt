package com.budgetmanager.presentation.screens.advisor

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
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
import com.budgetmanager.data.repository.PlanLine
import com.budgetmanager.data.repository.PlanResponse
import com.budgetmanager.data.repository.PlannerRepository
import com.budgetmanager.presentation.components.NeumorphicButton
import com.budgetmanager.presentation.components.NeumorphicCard
import com.budgetmanager.presentation.components.NeumorphicTextField
import com.budgetmanager.presentation.theme.*
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext.get as getKoin
import java.util.Locale

private fun eurMonth(v: Double): String = String.format(Locale.FRANCE, "%,.0f €/mois", v)

@Composable
fun BudgetPlannerSection() {
    val repo = remember { getKoin().get<PlannerRepository>() }
    val scope = rememberCoroutineScope()

    var plan by remember { mutableStateOf<PlanResponse?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var remarks by remember { mutableStateOf("") }
    var applyMsg by remember { mutableStateOf<String?>(null) }

    fun propose(withRemarks: String?) {
        if (loading) return
        loading = true; error = null; applyMsg = null
        scope.launch {
            try {
                val res = repo.proposePlan(remarks = withRemarks, currentPlan = plan?.plan)
                if (res.error != null) error = res.error else { plan = res; remarks = "" }
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
            "L'IA propose un budget mensuel à partir de ton historique, tes objectifs et tes priorités. Tu peux lui faire des remarques, puis l'appliquer.",
            style = MaterialTheme.typography.bodySmall, color = NeumorphicTextTertiary
        )
        Spacer(Modifier.height(14.dp))

        val current = plan
        when {
            loading -> Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = NeumorphicPrimary)
            }
            current == null -> NeumorphicButton(
                text = "Proposer un plan de budget",
                icon = Icons.Filled.AutoAwesome,
                onClick = { propose(null) }
            )
            else -> {
                if (current.summary.isNotBlank()) {
                    Text(current.summary, style = MaterialTheme.typography.bodyMedium, color = NeumorphicTextPrimary)
                    Spacer(Modifier.height(6.dp))
                }
                if (current.monthlyIncome > 0) {
                    Text("Revenu mensuel moyen : ${eurMonth(current.monthlyIncome)}", style = MaterialTheme.typography.bodySmall, color = NeumorphicTextSecondary)
                }
                Spacer(Modifier.height(12.dp))

                current.plan.forEach { line -> PlanRow(line) }

                val totalPlanned = current.plan.sumOf { it.monthlyAmount }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Total dépenses budgétées", fontWeight = FontWeight.SemiBold)
                    Text(eurMonth(totalPlanned), fontWeight = FontWeight.SemiBold)
                }
                if (current.monthlyIncome > 0) {
                    val save = current.monthlyIncome - totalPlanned
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Épargne dégagée / mois", color = NeumorphicTextSecondary)
                        Text(eurMonth(save), color = if (save >= 0) IncomeColor else ExpenseColor, fontWeight = FontWeight.SemiBold)
                    }
                }

                Spacer(Modifier.height(16.dp))
                NeumorphicTextField(
                    value = remarks,
                    onValueChange = { remarks = it },
                    label = "Une remarque pour ajuster ? (ex. « garde 200 € de loisirs », « je veux épargner plus »)",
                    placeholder = "Ta remarque…",
                    singleLine = false,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    NeumorphicButton(text = "Adapter le plan", icon = Icons.Filled.Edit, isPrimary = false, onClick = { propose(remarks) })
                    NeumorphicButton(text = "Appliquer ce budget", icon = Icons.Filled.Check, onClick = {
                        scope.launch {
                            applyMsg = null
                            try {
                                val n = repo.applyPlan(current.plan)
                                applyMsg = "✅ $n budget(s) appliqué(s). Tu peux les ajuster dans l'onglet Budgets."
                            } catch (e: Exception) {
                                applyMsg = "Échec de l'application : ${e.message}"
                            }
                        }
                    })
                }
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
private fun PlanRow(line: PlanLine) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(line.category, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                if (!line.essential) {
                    Spacer(Modifier.width(6.dp))
                    Text("superflu", style = MaterialTheme.typography.labelSmall, color = NeumorphicTextTertiary)
                }
            }
            if (line.rationale.isNotBlank()) {
                Text(line.rationale, style = MaterialTheme.typography.labelSmall, color = NeumorphicTextTertiary)
            }
        }
        Text(eurMonth(line.monthlyAmount), fontWeight = FontWeight.SemiBold, color = NeumorphicPrimary)
    }
}
