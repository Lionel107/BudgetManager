package com.budgetmanager.presentation.screens.analyst

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.budgetmanager.data.repository.AnalystContext
import com.budgetmanager.data.repository.AnalystRepository
import com.budgetmanager.data.repository.AnalystTurn
import com.budgetmanager.presentation.components.NeumorphicButton
import com.budgetmanager.presentation.components.NeumorphicCard
import com.budgetmanager.presentation.components.NeumorphicTextField
import com.budgetmanager.presentation.components.SectionHeader
import com.budgetmanager.presentation.theme.*
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext.get as getKoin
import java.util.Locale

private fun eur(v: Double): String = String.format(Locale.FRANCE, "%,.0f €", v)

@Composable
fun AnalystScreen() {
    val repo = remember { getKoin().get<AnalystRepository>() }
    val scope = rememberCoroutineScope()

    var context by remember { mutableStateOf<AnalystContext?>(null) }
    var bilan by remember { mutableStateOf<String?>(null) }
    var loadingBilan by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    // Fil de conversation (rôle "user" / "assistant")
    val messages = remember { mutableStateListOf<AnalystTurn>() }
    var input by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }

    // Charge le contexte + le bilan au premier affichage.
    LaunchedEffect(Unit) {
        loadingBilan = true
        error = null
        val ctx = runCatching { repo.buildContext() }.getOrNull()
        context = ctx
        if (ctx == null) {
            error = "Impossible de charger tes données."
            loadingBilan = false
            return@LaunchedEffect
        }
        val res = runCatching { repo.bilan(ctx) }.getOrElse {
            error = it.message ?: "Erreur lors du bilan."; loadingBilan = false; return@LaunchedEffect
        }
        if (res.error != null) error = res.error else bilan = res.reply
        loadingBilan = false
    }

    fun send() {
        val q = input.trim()
        val ctx = context
        if (q.isBlank() || ctx == null || sending) return
        input = ""
        messages.add(AnalystTurn("user", q))
        sending = true
        scope.launch {
            val history = messages.toList()
            val res = runCatching { repo.chat(ctx, history, q) }.getOrElse {
                messages.add(AnalystTurn("assistant", "⚠️ ${it.message ?: "Erreur."}")); sending = false; return@launch
            }
            messages.add(AnalystTurn("assistant", res.error ?: res.reply.ifBlank { "…" }))
            sending = false
        }
    }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        SectionHeader(title = "Analyste")
        Spacer(Modifier.height(4.dp))
        Text(
            "Ton coach du respect du budget : bilan du mois et discussion à la demande. " +
                "Les chiffres sont calculés localement ; l'IA les commente.",
            style = MaterialTheme.typography.bodyMedium, color = NeumorphicTextSecondary
        )
        Spacer(Modifier.height(16.dp))

        // ---- Chiffres déterministes du mois (toujours affichés) ----
        context?.let { ctx ->
            NeumorphicCard(modifier = Modifier.fillMaxWidth()) {
                Text("Ce mois — ${ctx.month}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(10.dp))
                StatRow("Revenus", eur(ctx.totals.income), IncomeColor)
                StatRow("Dépenses", eur(ctx.totals.expenses), ExpenseColor)
                StatRow("Épargne", eur(ctx.totals.savings), if (ctx.totals.savings >= 0) IncomeColor else ExpenseColor)
                if (ctx.budgets.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text("Budgets", style = MaterialTheme.typography.labelLarge, color = NeumorphicTextSecondary)
                    ctx.budgets.forEach { b ->
                        val over = b.spent > b.limit
                        StatRow(
                            b.name,
                            "${eur(b.spent)} / ${eur(b.limit)} (${b.pct}%)",
                            if (over) ExpenseColor else if (b.pct >= 80) NeumorphicBudgetAlert else IncomeColor
                        )
                    }
                }
                if (ctx.objectives.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text("Objectifs", style = MaterialTheme.typography.labelLarge, color = NeumorphicTextSecondary)
                    ctx.objectives.forEach { o ->
                        StatRow(
                            (if (o.onTrack) "✅ " else "⚠️ ") + o.title,
                            if (o.onTrack) "dans les temps" else "à rattraper",
                            if (o.onTrack) IncomeColor else NeumorphicBudgetAlert
                        )
                    }
                }
            }
        }

        // ---- Bilan rédigé par l'IA ----
        Spacer(Modifier.height(14.dp))
        NeumorphicCard(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("📋 ", style = MaterialTheme.typography.titleMedium)
                Text("Bilan du mois", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = NeumorphicPrimary)
            }
            Spacer(Modifier.height(8.dp))
            when {
                loadingBilan -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(color = NeumorphicPrimary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("Rédaction du bilan…", color = NeumorphicTextSecondary)
                }
                error != null -> {
                    Text(error!!, color = NeumorphicBudgetAlert, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(10.dp))
                    NeumorphicButton(text = "Réessayer", icon = Icons.Filled.AutoAwesome, onClick = {
                        scope.launch {
                            val ctx = context ?: return@launch
                            loadingBilan = true; error = null
                            val res = runCatching { repo.bilan(ctx) }.getOrElse {
                                error = it.message; loadingBilan = false; return@launch
                            }
                            if (res.error != null) error = res.error else bilan = res.reply
                            loadingBilan = false
                        }
                    })
                }
                bilan != null -> Text(bilan!!, style = MaterialTheme.typography.bodyMedium, color = NeumorphicTextPrimary)
            }
        }

        // ---- Chat ----
        Spacer(Modifier.height(14.dp))
        NeumorphicCard(modifier = Modifier.fillMaxWidth()) {
            Text("💬 Pose une question", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = NeumorphicPrimary)
            Spacer(Modifier.height(6.dp))
            if (messages.isEmpty()) {
                Text(
                    "Ex. « Est-ce que je peux me permettre 200 € de loisirs ce mois-ci ? »",
                    style = MaterialTheme.typography.bodySmall, color = NeumorphicTextTertiary
                )
            }
            messages.forEach { m ->
                Spacer(Modifier.height(10.dp))
                val isUser = m.role == "user"
                Row(Modifier.fillMaxWidth(), horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isUser) NeumorphicPrimary.copy(alpha = 0.12f) else NeumorphicDepressed)
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(
                            m.text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isUser) NeumorphicTextPrimary else NeumorphicTextSecondary
                        )
                    }
                }
            }
            if (sending) {
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(color = NeumorphicPrimary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("L'analyste réfléchit…", style = MaterialTheme.typography.bodySmall, color = NeumorphicTextTertiary)
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                NeumorphicTextField(
                    value = input,
                    onValueChange = { input = it },
                    label = "",
                    placeholder = "Ta question…",
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(10.dp))
                IconButton(onClick = { send() }, enabled = !sending && input.isNotBlank()) {
                    Icon(Icons.AutoMirrored.Filled.Send, "Envoyer", tint = NeumorphicPrimary)
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun StatRow(label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = NeumorphicTextSecondary, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = color)
    }
}
