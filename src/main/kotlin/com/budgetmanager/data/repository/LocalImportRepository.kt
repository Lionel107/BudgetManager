package com.budgetmanager.data.repository

import com.budgetmanager.data.database.Accounts
import com.budgetmanager.data.database.Budgets
import com.budgetmanager.data.database.Categories
import com.budgetmanager.data.database.DatabaseManager
import com.budgetmanager.data.database.Transactions
import com.budgetmanager.domain.model.Account
import com.budgetmanager.domain.model.AccountType
import com.budgetmanager.domain.model.Budget
import com.budgetmanager.domain.model.BudgetPeriodType
import com.budgetmanager.domain.model.Category
import com.budgetmanager.domain.model.Transaction
import com.budgetmanager.domain.model.TransactionType
import kotlinx.coroutines.flow.first
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.math.BigDecimal

/** Bilan d'un import local → cloud. */
data class ImportReport(
    val done: Boolean,
    val message: String,
    val accounts: Int = 0,
    val categories: Int = 0,
    val budgets: Int = 0,
    val transactions: Int = 0
)

// Représentations en mémoire des lignes locales (lues hors réseau).
private data class LocalAccount(
    val id: Long, val name: String, val balance: BigDecimal, val type: String,
    val currency: String, val color: String?, val icon: String?,
    val initialCapital: BigDecimal?, val taxRate: Float
)
private data class LocalCategory(val id: Long, val name: String, val type: String, val color: String)
private data class LocalBudget(val categoryId: Long, val periodType: String, val limit: BigDecimal)
private data class LocalTxn(
    val accountId: Long, val categoryId: Long?, val title: String, val amount: BigDecimal,
    val type: String, val date: java.time.LocalDateTime, val notes: String?, val tags: List<String>
)

/**
 * Migration UNIQUE de l'ancienne base locale SQLite (~/.budgetmanager) vers Supabase.
 *
 * Stratégie :
 *  - Catégories mappées PAR NOM sur celles du cloud (évite de dupliquer les catégories
 *    par défaut créées à l'inscription) ; les manquantes sont créées.
 *  - Comptes créés à solde 0, puis les transactions rejouées (le serveur ajuste le
 *    solde), puis le solde est RÉÉCRIT à la valeur locale exacte (pas de double-comptage).
 *  - Refuse de s'exécuter si le cloud contient déjà des transactions (anti-doublon).
 */
class LocalImportRepository(
    private val databaseManager: DatabaseManager,
    private val accountRepo: AccountRepository,
    private val categoryRepo: CategoryRepository,
    private val budgetRepo: BudgetRepository,
    private val transactionRepo: TransactionRepository
) {

    /** Nombre de transactions dans la base locale (0 si base absente/vide). */
    fun localTransactionCount(): Int = runCatching {
        transaction(databaseManager.getDatabase()) { Transactions.selectAll().count().toInt() }
    }.getOrDefault(0)

    suspend fun import(): ImportReport {
        // Garde-fou anti-doublon : ne rien faire si le cloud a déjà des transactions.
        val cloudTxns = runCatching { transactionRepo.getAllTransactions().first() }.getOrDefault(emptyList())
        if (cloudTxns.isNotEmpty()) {
            return ImportReport(false, "Le cloud contient déjà ${cloudTxns.size} transaction(s) : import annulé pour éviter les doublons.")
        }

        // 1) Lecture locale (en mémoire, hors réseau)
        val db = databaseManager.getDatabase()
        val localAccounts = mutableListOf<LocalAccount>()
        val localCategories = mutableListOf<LocalCategory>()
        val localBudgets = mutableListOf<LocalBudget>()
        val localTxns = mutableListOf<LocalTxn>()

        transaction(db) {
            Accounts.selectAll().forEach { r ->
                localAccounts += LocalAccount(
                    id = r[Accounts.id], name = r[Accounts.name], balance = r[Accounts.balance],
                    type = r[Accounts.accountType], currency = r[Accounts.currencyCode],
                    color = r[Accounts.color], icon = r[Accounts.iconName],
                    initialCapital = r[Accounts.initialCapital], taxRate = r[Accounts.taxRate]
                )
            }
            Categories.selectAll().forEach { r ->
                localCategories += LocalCategory(
                    id = r[Categories.id], name = r[Categories.name],
                    type = r[Categories.categoryType], color = r[Categories.color]
                )
            }
            Budgets.selectAll().forEach { r ->
                localBudgets += LocalBudget(
                    categoryId = r[Budgets.categoryId], periodType = r[Budgets.periodType], limit = r[Budgets.limit]
                )
            }
            Transactions.selectAll().forEach { r ->
                val tagsCsv = r[Transactions.tags]
                localTxns += LocalTxn(
                    accountId = r[Transactions.accountId], categoryId = r[Transactions.categoryId],
                    title = r[Transactions.title], amount = r[Transactions.amount],
                    type = r[Transactions.transactionType], date = r[Transactions.date],
                    notes = r[Transactions.notes],
                    tags = tagsCsv.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                )
            }
        }

        if (localAccounts.isEmpty() && localTxns.isEmpty()) {
            return ImportReport(false, "Aucune donnée locale à importer.")
        }

        // 2) Catégories : mapping par nom sur le cloud, création des manquantes
        val cloudCategories = categoryRepo.getAllCategoriesIncludingInactive().first()
        val cloudByName = cloudCategories.associateBy { it.name.trim().lowercase() }.toMutableMap()
        val catIdMap = HashMap<Long, Long>()
        var createdCats = 0
        for (lc in localCategories) {
            val key = lc.name.trim().lowercase()
            val existing = cloudByName[key]
            if (existing != null) {
                catIdMap[lc.id] = existing.id
            } else {
                val type = runCatching { TransactionType.valueOf(lc.type) }.getOrDefault(TransactionType.EXPENSE)
                val newId = categoryRepo.createCategory(
                    Category(name = lc.name, categoryType = type, color = lc.color, parentId = null)
                )
                catIdMap[lc.id] = newId
                cloudByName[key] = Category(id = newId, name = lc.name, categoryType = type, color = lc.color)
                createdCats++
            }
        }

        // 3) Comptes : créés à solde 0, map oldId → newId
        val accIdMap = HashMap<Long, Long>()
        val newIdToLocalBalance = HashMap<Long, BigDecimal>()
        for (la in localAccounts) {
            val type = runCatching { AccountType.valueOf(la.type) }.getOrDefault(AccountType.CHECKING)
            val newId = accountRepo.createAccount(
                Account(
                    name = la.name, balance = BigDecimal.ZERO, accountType = type,
                    currencyCode = la.currency, color = la.color, iconName = la.icon,
                    initialCapital = la.initialCapital, taxRate = la.taxRate
                )
            )
            accIdMap[la.id] = newId
            newIdToLocalBalance[newId] = la.balance
        }

        // 4) Transactions : remap comptes/catégories, rejouées (le solde s'ajuste)
        var importedTxns = 0
        for (lt in localTxns) {
            val newAccId = accIdMap[lt.accountId] ?: continue
            val type = runCatching { TransactionType.valueOf(lt.type) }.getOrDefault(TransactionType.EXPENSE)
            runCatching {
                transactionRepo.createTransaction(
                    Transaction(
                        accountId = newAccId,
                        categoryId = lt.categoryId?.let { catIdMap[it] },
                        title = lt.title, amount = lt.amount, transactionType = type,
                        date = lt.date, notes = lt.notes, tags = lt.tags
                    )
                )
                importedTxns++
            }
        }

        // 5) Réécriture des soldes exacts (évite le double-comptage)
        for (la in localAccounts) {
            val newId = accIdMap[la.id] ?: continue
            val type = runCatching { AccountType.valueOf(la.type) }.getOrDefault(AccountType.CHECKING)
            runCatching {
                accountRepo.updateAccount(
                    Account(
                        id = newId, name = la.name, balance = la.balance, accountType = type,
                        currencyCode = la.currency, color = la.color, iconName = la.icon,
                        initialCapital = la.initialCapital, taxRate = la.taxRate
                    )
                )
            }
        }

        // 6) Budgets : remap catégorie
        var importedBudgets = 0
        for (lb in localBudgets) {
            val newCatId = catIdMap[lb.categoryId] ?: continue
            val period = runCatching { BudgetPeriodType.valueOf(lb.periodType) }.getOrDefault(BudgetPeriodType.MONTHLY)
            runCatching {
                budgetRepo.createBudget(
                    Budget(
                        categoryId = newCatId, categoryName = "", categoryColor = "",
                        periodType = period, limit = lb.limit
                    )
                )
                importedBudgets++
            }
        }

        return ImportReport(
            done = true,
            message = "Import terminé : ${accIdMap.size} compte(s), $createdCats catégorie(s) créée(s), $importedBudgets budget(s), $importedTxns transaction(s).",
            accounts = accIdMap.size,
            categories = createdCats,
            budgets = importedBudgets,
            transactions = importedTxns
        )
    }
}
