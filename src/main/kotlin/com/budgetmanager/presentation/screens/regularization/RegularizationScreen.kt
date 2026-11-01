package com.budgetmanager.presentation.screens.regularization

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Balance
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.budgetmanager.data.repository.AccountRepository
import com.budgetmanager.data.repository.RegularizationRepository
import com.budgetmanager.domain.model.Account
import com.budgetmanager.domain.model.AccountRegularization
import com.budgetmanager.presentation.components.ConfirmDialog
import com.budgetmanager.presentation.components.NeumorphicButton
import com.budgetmanager.presentation.components.NeumorphicCard
import com.budgetmanager.presentation.components.NeumorphicTextField
import com.budgetmanager.presentation.components.SectionHeader
import com.budgetmanager.presentation.theme.*
import com.budgetmanager.util.DateUtils
import com.budgetmanager.util.FormatUtils
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext.get as getKoin
import java.math.BigDecimal
import java.math.RoundingMode

/** "1 234,56" / "1234.56" / "-12" → BigDecimal, sinon null. */
private fun parseAmount(s: String): BigDecimal? =
    s.replace(" ", "").replace(" ", "").replace(" ", "").replace("€", "").replace(",", ".")
        .takeIf { it.isNotBlank() }
        ?.toBigDecimalOrNull()
        ?.setScale(2, RoundingMode.HALF_UP)

private fun signedEur(v: BigDecimal): String =
    (if (v.signum() > 0) "+" else "") + FormatUtils.formatCurrency(v)

/**
 * Régularisation : quand on a perdu le fil de sa compta, on saisit le solde RÉEL
 * de chaque compte (celui de la banque) et l'app recale tout. L'écart est tracé
 * (historique + copilote IA) mais n'est ni une dépense ni un revenu.
 */
@Composable
fun RegularizationScreen() {
    val accountRepo = remember { getKoin().get<AccountRepository>() }
    val repo = remember { getKoin().get<RegularizationRepository>() }
    val scope = rememberCoroutineScope()

    val accounts by remember { accountRepo.getActiveAccounts() }.collectAsState(initial = emptyList())
    val history by remember { repo.getHistory() }.collectAsState(initial = emptyList())

    // Solde réel saisi, par id de compte
    val inputs = remember { mutableStateMapOf<Long, String>() }
    var note by remember { mutableStateOf("") }
    var confirming by remember { mutableStateOf(false) }
    var working by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<Pair<String, Boolean>?>(null) } // (texte, succès)

    // Comptes dont le solde réel saisi diffère du solde de l'app
    val changes: List<Pair<Account, BigDecimal>> = accounts.mapNotNull { a ->
        val real = parseAmount(inputs[a.id].orEmpty()) ?: return@mapNotNull null
        if (real.compareTo(a.balance.setScale(2, RoundingMode.HALF_UP)) == 0) null else a to real
    }
    val invalid = accounts.any { a -> inputs[a.id].orEmpty().isNotBlank() && parseAmount(inputs[a.id]!!) == null }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        SectionHeader(title = "Régularisation")
        Spacer(Modifier.height(4.dp))
        Text(
            "Tu as perdu le fil de ta compta ? Pas de panique : indique le solde RÉEL de chaque compte " +
                "(celui affiché par ta banque) et l'app remet tout à plat. L'écart n'est compté ni comme " +
                "une dépense ni comme un revenu, donc tes budgets restent justes. Il est gardé dans " +
                "l'historique et ton copilote IA en tient compte.",
            style = MaterialTheme.typography.bodyMedium, color = NeumorphicTextSecondary
        )
        Spacer(Modifier.height(16.dp))

        NeumorphicCard(modifier = Modifier.fillMaxWidth()) {
            Text("Soldes réels", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "Laisse vide un compte qui est déjà juste.",
                style = MaterialTheme.typography.bodySmall, color = NeumorphicTextTertiary
            )
            if (accounts.isEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text("Aucun compte actif.", color = NeumorphicTextSecondary)
            }
            accounts.forEach { a ->
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(a.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Dans l'app : ${FormatUtils.formatCurrency(a.balance, a.currencyCode)}",
                            style = MaterialTheme.typography.bodySmall, color = NeumorphicTextSecondary
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    NeumorphicTextField(
                        value = inputs[a.id].orEmpty(),
                        onValueChange = { inputs[a.id] = it; message = null },
                        label = "Solde réel",
                        placeholder = "0,00",
                        suffix = "€",
                        modifier = Modifier.width(180.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    val raw = inputs[a.id].orEmpty()
                    val real = parseAmount(raw)
                    val (label, color) = when {
                        raw.isBlank() -> "—" to NeumorphicTextTertiary
                        real == null -> "Montant invalide" to NeumorphicBudgetAlert
                        else -> {
                            val diff = real - a.balance
                            when {
                                diff.signum() == 0 -> "Déjà juste ✓" to IncomeColor
                                diff.signum() > 0 -> signedEur(diff) to IncomeColor
                                else -> signedEur(diff) to ExpenseColor
                            }
                        }
                    }
                    Text(
                        label, color = color, fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(130.dp)
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            NeumorphicTextField(
                value = note,
                onValueChange = { note = it },
                label = "Note (optionnel)",
                placeholder = "Ex. Remise à plat après 2 mois sans saisie",
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                NeumorphicButton(
                    text = if (changes.isEmpty()) "Régulariser" else "Régulariser ${changes.size} compte${if (changes.size > 1) "s" else ""}",
                    icon = Icons.Filled.Balance,
                    enabled = changes.isNotEmpty() && !invalid && !working,
                    onClick = { confirming = true }
                )
                if (working) {
                    Spacer(Modifier.width(12.dp))
                    CircularProgressIndicator(color = NeumorphicPrimary, modifier = Modifier.size(20.dp))
                }
            }
            message?.let { (text, ok) ->
                Spacer(Modifier.height(10.dp))
                Text(text, color = if (ok) IncomeColor else NeumorphicBudgetAlert, style = MaterialTheme.typography.bodyMedium)
            }
        }

        Spacer(Modifier.height(14.dp))
        HistoryCard(history)
        Spacer(Modifier.height(24.dp))
    }

    if (confirming) {
        val total = changes.fold(BigDecimal.ZERO) { acc, (a, real) -> acc + (real - a.balance) }
        val lines = changes.joinToString("\n") { (a, real) ->
            "• ${a.name} : ${FormatUtils.formatCurrency(a.balance)} → ${FormatUtils.formatCurrency(real)} (${signedEur(real - a.balance)})"
        }
        ConfirmDialog(
            title = "Remettre les comptes à plat ?",
            message = "$lines\n\nÉcart total : ${signedEur(total)}.\nAucune transaction ne sera créée.",
            confirmText = "Régulariser",
            onDismiss = { confirming = false },
            onConfirm = {
                confirming = false
                working = true
                scope.launch {
                    val failed = mutableListOf<String>()
                    for ((a, real) in changes) {
                        runCatching { repo.regularize(a.id, real, note.trim()) }
                            .onSuccess { inputs.remove(a.id) }
                            .onFailure { failed += "${a.name} (${it.message})" }
                    }
                    working = false
                    message = if (failed.isEmpty()) {
                        note = ""
                        "C'est fait : tes comptes sont à jour. Tu repars sur une base saine 👍" to true
                    } else {
                        "Échec pour : ${failed.joinToString(", ")}" to false
                    }
                }
            }
        )
    }
}

@Composable
private fun HistoryCard(history: List<AccountRegularization>) {
    NeumorphicCard(modifier = Modifier.fillMaxWidth()) {
        Text("Historique", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        if (history.isEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text("Aucune régularisation pour l'instant.", color = NeumorphicTextSecondary, style = MaterialTheme.typography.bodyMedium)
        }
        history.forEach { r ->
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "${r.accountName ?: "Compte"} · ${DateUtils.formatShortDate(r.regularizedAt)}",
                        style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "${FormatUtils.formatCurrency(r.previousBalance)} → ${FormatUtils.formatCurrency(r.realBalance)}" +
                            (r.note?.let { " · $it" } ?: ""),
                        style = MaterialTheme.typography.bodySmall, color = NeumorphicTextSecondary
                    )
                }
                Text(
                    signedEur(r.difference),
                    color = if (r.difference.signum() < 0) ExpenseColor else IncomeColor,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
