package com.budgetmanager.presentation.screens.advisor

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.budgetmanager.data.repository.AdvisorRepository
import com.budgetmanager.data.repository.AdvisorResponse
import com.budgetmanager.data.repository.CategoryStat
import com.budgetmanager.presentation.components.NeumorphicButton
import com.budgetmanager.presentation.components.NeumorphicCard
import com.budgetmanager.presentation.components.SectionHeader
import com.budgetmanager.presentation.theme.*
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext.get as getKoin
import java.util.Locale

private sealed interface AdvisorUi {
    data object Idle : AdvisorUi
    data object Loading : AdvisorUi
    data class Error(val message: String) : AdvisorUi
    data class Ready(val data: AdvisorResponse) : AdvisorUi
}

private fun eur(v: Double): String = String.format(Locale.FRANCE, "%,.2f €", v)
private fun eurMonth(v: Double): String = String.format(Locale.FRANCE, "%,.0f €/mois", v)

@Composable
fun AdvisorScreen() {
    val repo = remember { getKoin().get<AdvisorRepository>() }
    val scope = rememberCoroutineScope()
    var ui by remember { mutableStateOf<AdvisorUi>(AdvisorUi.Idle) }

    fun analyze() {
        ui = AdvisorUi.Loading
        scope.launch {
            ui = try {
                val res = repo.getAdvice()
                if (res.error != null) AdvisorUi.Error(res.error!!) else AdvisorUi.Ready(res)
            } catch (e: Exception) {
                AdvisorUi.Error(e.message ?: "Erreur lors de l'analyse.")
            }
        }
    }

    LaunchedEffect(Unit) { analyze() }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
    ) {
        SectionHeader(title = "Conseiller IA")
        Spacer(Modifier.height(4.dp))
        Text(
            "Analyse de tes 12 derniers mois : budget annuel, épargne et dépenses saisonnières à provisionner.",
            style = MaterialTheme.typography.bodyMedium,
            color = NeumorphicTextSecondary
        )
        Spacer(Modifier.height(16.dp))

        when (val s = ui) {
            is AdvisorUi.Idle, is AdvisorUi.Loading -> Box(
                Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = NeumorphicPrimary)
                    Spacer(Modifier.height(12.dp))
                    Text("Analyse en cours…", color = NeumorphicTextSecondary)
                }
            }

            is AdvisorUi.Error -> NeumorphicCard(modifier = Modifier.fillMaxWidth()) {
                Text("Impossible d'analyser", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = NeumorphicBudgetAlert)
                Spacer(Modifier.height(8.dp))
                Text(s.message, style = MaterialTheme.typography.bodyMedium, color = NeumorphicTextSecondary)
                Spacer(Modifier.height(16.dp))
                NeumorphicButton(text = "Réessayer", icon = Icons.Filled.AutoAwesome, onClick = { analyze() })
            }

            is AdvisorUi.Ready -> {
                val a = s.data.analysis
                if (a == null) {
                    Text("Aucune donnée à analyser.", color = NeumorphicTextSecondary)
                } else {
                    // Vue d'ensemble
                    NeumorphicCard(modifier = Modifier.fillMaxWidth()) {
                        Text("Vue d'ensemble (12 mois)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(12.dp))
                        StatRow("Revenus", eur(a.totals.income), IncomeColor)
                        StatRow("Dépenses", eur(a.totals.expenses), ExpenseColor)
                        StatRow("Épargne", eur(a.totals.savings), if (a.totals.savings >= 0) IncomeColor else ExpenseColor)
                        StatRow("Taux d'épargne", String.format(Locale.FRANCE, "%.1f %%", a.totals.savingsRatePct), NeumorphicPrimary)
                    }

                    // Accompagnement IA
                    s.data.advice?.let { adv ->
                        Spacer(Modifier.height(14.dp))
                        NeumorphicCard(modifier = Modifier.fillMaxWidth()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("💡 ", style = MaterialTheme.typography.titleMedium)
                                Text("Ton accompagnement", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = NeumorphicPrimary)
                            }
                            if (adv.summary.isNotBlank()) {
                                Spacer(Modifier.height(8.dp))
                                Text(adv.summary, style = MaterialTheme.typography.bodyMedium, color = NeumorphicTextPrimary)
                            }
                            adv.tips.forEach { tip ->
                                Spacer(Modifier.height(8.dp))
                                Row {
                                    Text("• ", color = NeumorphicPrimary)
                                    Text(tip, style = MaterialTheme.typography.bodyMedium, color = NeumorphicTextSecondary)
                                }
                            }
                        }
                    }

                    // Dépenses saisonnières à provisionner (le cœur de la demande)
                    if (a.seasonal.isNotEmpty()) {
                        Spacer(Modifier.height(14.dp))
                        NeumorphicCard(modifier = Modifier.fillMaxWidth()) {
                            Text("📅 À provisionner (dépenses saisonnières)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Ces dépenses ne tombent que certains mois. Mets de côté chaque mois pour ne pas être surpris.",
                                style = MaterialTheme.typography.bodySmall, color = NeumorphicTextTertiary
                            )
                            a.seasonal.forEach { c ->
                                Spacer(Modifier.height(12.dp))
                                Column {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(c.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                                        Text(eurMonth(c.seasonalProvision), color = NeumorphicPrimary, fontWeight = FontWeight.SemiBold)
                                    }
                                    val months = if (c.peakMonths.isNotEmpty()) c.peakMonths.joinToString(", ") else "certains mois"
                                    Text(
                                        "${eur(c.annualTotal)} / an · surtout en $months",
                                        style = MaterialTheme.typography.bodySmall, color = NeumorphicTextSecondary
                                    )
                                }
                            }
                        }
                    }

                    // Budget annuel proposé (toutes catégories)
                    if (a.categories.isNotEmpty()) {
                        Spacer(Modifier.height(14.dp))
                        NeumorphicCard(modifier = Modifier.fillMaxWidth()) {
                            Text("Budget annuel proposé", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(4.dp))
                            Text("Montant lissé sur 12 mois par catégorie.", style = MaterialTheme.typography.bodySmall, color = NeumorphicTextTertiary)
                            a.categories.forEach { c -> BudgetRow(c) }
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    NeumorphicButton(text = "Relancer l'analyse", icon = Icons.Filled.AutoAwesome, onClick = { analyze() })
                    Spacer(Modifier.height(24.dp))
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        BudgetPlannerSection()
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun StatRow(label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = NeumorphicTextSecondary)
        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = color)
    }
}

@Composable
private fun BudgetRow(c: CategoryStat) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Column(Modifier.weight(1f)) {
            Text(c.name, style = MaterialTheme.typography.bodyMedium, color = NeumorphicTextPrimary)
            if (c.seasonal) Text("saisonnier", style = MaterialTheme.typography.labelSmall, color = NeumorphicPrimary)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(eurMonth(c.monthlyAverage), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text("${eur(c.annualTotal)} / an", style = MaterialTheme.typography.labelSmall, color = NeumorphicTextTertiary)
        }
    }
}
