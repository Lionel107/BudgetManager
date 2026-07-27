package com.budgetmanager.presentation.screens.auth

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.budgetmanager.data.preferences.AppPreferences
import com.budgetmanager.data.repository.AccountRepository
import com.budgetmanager.data.repository.AnalysisRepository
import com.budgetmanager.data.repository.BudgetRepository
import com.budgetmanager.data.repository.CategoryRepository
import com.budgetmanager.data.repository.RecurringTransactionRepository
import com.budgetmanager.data.repository.TransactionRepository
import com.budgetmanager.domain.model.Budget
import com.budgetmanager.domain.model.BudgetPeriodType
import com.budgetmanager.domain.model.BudgetWithStatus
import com.budgetmanager.presentation.components.NotificationCenter
import com.budgetmanager.util.AdviceEngine
import com.budgetmanager.util.AlertEngine
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext.get as getKoin

/**
 * Initialisation exécutée UNE FOIS l'utilisateur authentifié (la RLS Supabase
 * exige une session). Remplace les étapes de démarrage qui étaient auparavant
 * dans Main.kt avant l'auth :
 *  - création des catégories par défaut si le compte est vide,
 *  - traitement des récurrences échues (+ re-traitement périodique),
 *  - génération des conseils/notifications (au démarrage puis chaque heure).
 *
 * Les boucles périodiques vivent dans le scope du LaunchedEffect : elles sont
 * automatiquement annulées à la déconnexion (sortie du contenu authentifié).
 */
@Composable
fun PostLoginInit() {
    LaunchedEffect(Unit) {
        val categoryRepo = getKoin().get<CategoryRepository>()
        val recurringRepo = getKoin().get<RecurringTransactionRepository>()

        runCatching { categoryRepo.createDefaultCategories() }
        runCatching { recurringRepo.processRecurringTransactions() }

        // Re-traite les récurrences toutes les 30 min (app laissée ouverte / minuit passé)
        launch {
            while (true) {
                delay(30 * 60 * 1000L)
                runCatching { recurringRepo.processRecurringTransactions() }
            }
        }

        // Conseils/notifications : au démarrage (après chargement) puis toutes les heures
        launch {
            delay(2000)
            while (true) {
                runCatching {
                    val accountRepo = getKoin().get<AccountRepository>()
                    val transactionRepo = getKoin().get<TransactionRepository>()
                    val budgetRepo = getKoin().get<BudgetRepository>()
                    val appPrefs = getKoin().get<AppPreferences>()

                    val accounts = accountRepo.getAllAccounts().first()
                    val transactions = transactionRepo.getAllTransactions().first()
                    val now = java.time.YearMonth.now()
                    val budgets = budgetRepo.getBudgetsWithSpending(now.atDay(1), now.atEndOfMonth()).first()
                        .map { s ->
                            BudgetWithStatus(
                                budget = Budget(
                                    id = s.budgetId,
                                    categoryId = s.categoryId,
                                    categoryName = s.categoryName,
                                    categoryColor = s.categoryColor,
                                    periodType = BudgetPeriodType.MONTHLY,
                                    limit = s.budgetLimit
                                ),
                                spent = s.spent,
                                remaining = s.remaining,
                                percentage = s.percentage,
                                state = s.state
                            )
                        }
                    val advices = AdviceEngine().analyze(accounts, transactions, budgets, appPrefs.savingsGoal)
                    advices.forEach { NotificationCenter.addFromAdvice(it) }

                    // Alertes déterministes dédiées (annuel-aware, objectifs, dépense inhabituelle)
                    val analysisRepo = getKoin().get<AnalysisRepository>()
                    val objectiveProgress = runCatching { analysisRepo.objectiveProgress() }.getOrDefault(emptyList())
                    val alerts = AlertEngine().analyze(objectiveProgress, budgets, transactions)
                    alerts.forEach { NotificationCenter.addFromAdvice(it) }
                }
                delay(60 * 60 * 1000L)
            }
        }
    }
}
