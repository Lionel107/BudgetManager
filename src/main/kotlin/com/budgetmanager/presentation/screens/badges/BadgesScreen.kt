package com.budgetmanager.presentation.screens.badges

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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.budgetmanager.domain.model.*
import com.budgetmanager.presentation.components.*
import com.budgetmanager.presentation.navigation.NavigationState
import com.budgetmanager.presentation.theme.*
import com.budgetmanager.util.Badge
import com.budgetmanager.util.BadgeEngine
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.koin.core.context.GlobalContext.get as getKoin
import java.math.BigDecimal
import java.time.YearMonth

data class BadgesUiState(
    val badges: List<Badge> = emptyList(),
    val isLoading: Boolean = true
)

class BadgesScreenState {
    var uiState by mutableStateOf(BadgesUiState())
        private set

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    init { compute() }

    private fun compute() {
        scope.launch {
            try {
                val koin = getKoin()
                val accRepo = koin.get<com.budgetmanager.data.repository.AccountRepository>()
                val txRepo = koin.get<com.budgetmanager.data.repository.TransactionRepository>()
                val budgetRepo = koin.get<com.budgetmanager.data.repository.BudgetRepository>()
                val challengeRepo = koin.get<com.budgetmanager.data.repository.ChallengeRepository>()
                val appPrefs = koin.get<com.budgetmanager.data.preferences.AppPreferences>()

                val accounts = accRepo.getAllAccounts().first()
                val transactions = txRepo.getAllTransactions().first()
                val now = YearMonth.now()
                val budgetData = budgetRepo.getBudgetsWithSpending(now.atDay(1), now.atEndOfMonth()).first()
                val budgets = budgetData.map { s ->
                    BudgetWithStatus(
                        budget = Budget(
                            id = s.budgetId, categoryId = s.categoryId,
                            categoryName = s.categoryName, categoryColor = s.categoryColor,
                            periodType = BudgetPeriodType.MONTHLY, limit = s.budgetLimit
                        ),
                        spent = s.spent, remaining = s.remaining,
                        percentage = s.percentage, state = s.state
                    )
                }
                val challenges = challengeRepo.getAll().first()

                val badges = BadgeEngine.computeAll(accounts, transactions, budgets, challenges, appPrefs.savingsGoal)
                uiState = uiState.copy(badges = badges, isLoading = false)
            } catch (_: Exception) {
                uiState = uiState.copy(isLoading = false)
            }
        }
    }

    fun dispose() { scope.cancel() }
}

@Composable
fun BadgesScreen(navigationState: NavigationState) {
    val state = remember { BadgesScreenState() }
    DisposableEffect(Unit) { onDispose { state.dispose() } }

    val ui = state.uiState
    val unlocked = ui.badges.count { it.unlocked }
    val total = ui.badges.size

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Badges", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = NeumorphicTextPrimary)
            Spacer(Modifier.weight(1f))
            Text(
                "$unlocked / $total debloques",
                style = MaterialTheme.typography.titleMedium,
                color = NeumorphicPrimary,
                fontWeight = FontWeight.SemiBold
            )
        }
        Text(
            "Debloquer des badges en utilisant l'application au quotidien et en respectant tes objectifs.",
            style = MaterialTheme.typography.bodyMedium,
            color = NeumorphicTextSecondary,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        if (ui.badges.isEmpty()) {
            EmptyState(message = "Chargement des badges...", icon = Icons.Filled.EmojiEvents)
        } else {
            // Unlocked first
            val (unlockedList, lockedList) = ui.badges.partition { it.unlocked }

            if (unlockedList.isNotEmpty()) {
                Text("Debloques", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = IncomeColor)
                Spacer(Modifier.height(8.dp))
                BadgeGrid(unlockedList)
                Spacer(Modifier.height(24.dp))
            }

            if (lockedList.isNotEmpty()) {
                Text("A debloquer", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = NeumorphicTextSecondary)
                Spacer(Modifier.height(8.dp))
                BadgeGrid(lockedList)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun BadgeGrid(badges: List<Badge>) {
    val rows = badges.chunked(4)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        rows.forEach { row ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { b -> BadgeCard(b, Modifier.weight(1f)) }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun BadgeCard(badge: Badge, modifier: Modifier = Modifier) {
    NeumorphicCard(
        modifier = modifier.alpha(if (badge.unlocked) 1f else 0.55f),
        elevation = if (badge.unlocked) 8.dp else 4.dp,
        backgroundColor = if (badge.unlocked) NeumorphicPrimary.copy(alpha = 0.08f) else NeumorphicElevated
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(badge.icon, fontSize = 36.sp)
            Spacer(Modifier.height(6.dp))
            Text(
                badge.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = NeumorphicTextPrimary,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(4.dp))
            Text(
                badge.description,
                style = MaterialTheme.typography.bodySmall,
                color = NeumorphicTextSecondary,
                textAlign = TextAlign.Center,
                maxLines = 3
            )
            if (!badge.unlocked && badge.progress > 0f) {
                Spacer(Modifier.height(8.dp))
                BudgetProgressBar(
                    spent = badge.progress,
                    limit = 1f,
                    showLabel = false,
                    height = 6.dp
                )
                if (badge.progressText.isNotBlank()) {
                    Text(
                        badge.progressText,
                        style = MaterialTheme.typography.labelSmall,
                        color = NeumorphicTextTertiary,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            } else if (badge.unlocked) {
                Spacer(Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(IncomeColor.copy(alpha = 0.18f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        "✓ Debloque",
                        style = MaterialTheme.typography.labelSmall,
                        color = IncomeColor,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
