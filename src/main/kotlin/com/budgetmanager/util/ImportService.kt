package com.budgetmanager.util

import com.budgetmanager.data.database.Transactions
import com.budgetmanager.data.repository.AccountRepository
import com.budgetmanager.data.repository.CategoryRepository
import com.budgetmanager.data.repository.TransactionRepository
import com.budgetmanager.domain.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.File
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

data class ImportResult(
    val totalLines: Int,
    val imported: Int,
    val skipped: Int,
    val errors: List<String>,
    val createdAccounts: List<String> = emptyList(),
    val createdCategories: List<String> = emptyList()
)

class ImportService(
    private val transactionRepository: TransactionRepository,
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository
) {

    private val dateFmt = DateTimeFormatter.ofPattern("dd/MM/yyyy")

    suspend fun importCsv(file: File): ImportResult = withContext(Dispatchers.IO) {
        val lines = file.readLines(Charsets.UTF_8)
            .map { it.trim() }
            .filter { it.isNotBlank() }

        if (lines.isEmpty()) {
            return@withContext ImportResult(0, 0, 0, listOf("Fichier vide"))
        }

        // Skip header line
        val header = lines.first().lowercase()
        val dataLines = if (header.contains("date") && header.contains("libelle")) {
            lines.drop(1)
        } else {
            lines
        }

        val totalLines = dataLines.size
        var imported = 0
        var skipped = 0
        val errors = mutableListOf<String>()
        val createdAccounts = mutableListOf<String>()
        val createdCategories = mutableListOf<String>()

        // Cache accounts and categories
        val accounts = accountRepository.getAllAccounts().first()
        val categories = categoryRepository.getAllCategories().first()
        val accountsByName = accounts.associateBy { it.name.lowercase() }.toMutableMap()
        val categoriesByName = categories.associateBy { it.name.lowercase() }.toMutableMap()

        for ((index, line) in dataLines.withIndex()) {
            val lineNum = index + 2
            try {
                val parts = parseCsvLine(line)
                if (parts.size < 5) {
                    errors.add("Ligne $lineNum : pas assez de colonnes (${parts.size}/6)")
                    skipped++
                    continue
                }

                val dateStr = parts[0].trim()
                val title = parts[1].trim()
                val categoryName = parts[2].trim()
                val typeStr = parts[3].trim()
                val amountStr = parts[4].trim()
                val accountName = if (parts.size > 5) parts[5].trim() else ""

                // Parse date
                val date = try {
                    LocalDate.parse(dateStr, dateFmt)
                } catch (_: Exception) {
                    errors.add("Ligne $lineNum : date invalide '$dateStr'")
                    skipped++
                    continue
                }

                // Parse type
                val transactionType = when (typeStr.lowercase()) {
                    "revenu" -> TransactionType.INCOME
                    "depense", "dépense" -> TransactionType.EXPENSE
                    "transfert" -> TransactionType.TRANSFER
                    else -> {
                        errors.add("Ligne $lineNum : type inconnu '$typeStr'")
                        skipped++
                        continue
                    }
                }

                // Parse amount
                val cleanAmount = amountStr.replace(",", ".").replace(" ", "")
                val amount = try {
                    BigDecimal(cleanAmount).abs()
                } catch (_: Exception) {
                    errors.add("Ligne $lineNum : montant invalide '$amountStr'")
                    skipped++
                    continue
                }

                // Find or create account
                val account = if (accountName.isNotBlank()) {
                    accountsByName[accountName.lowercase()]
                        ?: run {
                            val newId = accountRepository.createAccount(
                                Account(
                                    name = accountName,
                                    balance = BigDecimal.ZERO,
                                    accountType = AccountType.CHECKING
                                )
                            )
                            val newAccount = accountRepository.getAccountById(newId)
                            if (newAccount != null) {
                                accountsByName[accountName.lowercase()] = newAccount
                                createdAccounts.add(accountName)
                            }
                            newAccount
                        }
                } else {
                    accounts.firstOrNull()
                }

                if (account == null) {
                    errors.add("Ligne $lineNum : aucun compte disponible")
                    skipped++
                    continue
                }

                // Find or create category
                val category = if (categoryName.isNotBlank()) {
                    categoriesByName[categoryName.lowercase()]
                        ?: run {
                            val catType = if (transactionType == TransactionType.INCOME)
                                TransactionType.INCOME else TransactionType.EXPENSE
                            val newId = categoryRepository.createCategory(
                                Category(
                                    name = categoryName,
                                    categoryType = catType,
                                    color = "#6C63FF"
                                )
                            )
                            val newCat = categoryRepository.getCategoryById(newId)
                            if (newCat != null) {
                                categoriesByName[categoryName.lowercase()] = newCat
                                createdCategories.add(categoryName)
                            }
                            newCat
                        }
                } else null

                // Insert transaction directly in DB — NO balance update
                val now = LocalDateTime.now()
                transaction {
                    Transactions.insert {
                        it[Transactions.accountId] = account.id
                        it[Transactions.categoryId] = category?.id
                        it[Transactions.title] = title
                        it[Transactions.amount] = amount
                        it[Transactions.transactionType] = transactionType.name
                        it[Transactions.date] = date.atStartOfDay()
                        it[Transactions.notes] = null
                        it[Transactions.tags] = ""
                        it[Transactions.isRecurring] = false
                        it[Transactions.recurringTransactionId] = null
                        it[Transactions.createdAt] = now
                    }
                }

                imported++

            } catch (e: Exception) {
                errors.add("Ligne $lineNum : ${e.message}")
                skipped++
            }
        }

        // Refresh transaction list so the UI picks up new data
        transactionRepository.refresh()

        ImportResult(totalLines, imported, skipped, errors, createdAccounts.toList(), createdCategories.toList())
    }

    private fun parseCsvLine(line: String): List<String> {
        val semicolonCount = line.count { it == ';' }
        val commaCount = line.count { it == ',' }
        val separator = if (semicolonCount >= commaCount) ';' else ','

        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false

        for (c in line) {
            when {
                c == '"' -> inQuotes = !inQuotes
                c == separator && !inQuotes -> {
                    result.add(current.toString())
                    current.clear()
                }
                else -> current.append(c)
            }
        }
        result.add(current.toString())
        return result
    }
}
