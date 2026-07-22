package com.budgetmanager.util

import com.budgetmanager.domain.model.*
import java.io.File
import java.math.BigDecimal
import java.text.NumberFormat
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class ExportFileFormat { CSV, HTML }

class ExportService {

    private val curr = NumberFormat.getCurrencyInstance(Locale.FRANCE).apply {
        currency = java.util.Currency.getInstance("EUR")
    }
    private val dateFmt = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    private val monthFmt = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.FRANCE)

    fun export(
        format: ExportFileFormat,
        months: List<YearMonth>,
        transactions: List<Transaction>,
        accounts: List<Account>,
        outputPath: String
    ) {
        when (format) {
            ExportFileFormat.CSV -> exportCsv(months, transactions, accounts, outputPath)
            ExportFileFormat.HTML -> exportHtml(months, transactions, accounts, outputPath)
        }
    }

    // ======================== CSV ========================

    private fun exportCsv(months: List<YearMonth>, transactions: List<Transaction>, accounts: List<Account>, path: String) {
        val sb = StringBuilder()
        sb.appendLine("Date;Libelle;Categorie;Type;Montant;Compte")

        months.sorted().forEach { m ->
            val ms = m.atDay(1).atStartOfDay()
            val me = m.atEndOfMonth().atTime(23, 59, 59)
            transactions.filter { it.date >= ms && it.date <= me }.sortedBy { it.date }.forEach { tx ->
                val type = when (tx.transactionType) {
                    TransactionType.INCOME -> "Revenu"
                    TransactionType.EXPENSE -> "Depense"
                    TransactionType.TRANSFER -> "Transfert"
                }
                val sign = if (tx.transactionType == TransactionType.EXPENSE) "-" else ""
                sb.appendLine("${tx.date.format(dateFmt)};${tx.title};${tx.categoryName ?: ""};$type;$sign${tx.amount.toPlainString()};${tx.accountName ?: ""}")
            }
        }

        File(path).writeText(sb.toString(), Charsets.UTF_8)
    }

    // ======================== HTML ========================

    private fun exportHtml(months: List<YearMonth>, transactions: List<Transaction>, accounts: List<Account>, path: String) {
        val sorted = months.sorted()
        val periodText = if (sorted.size == 1) sorted.first().format(monthFmt).replaceFirstChar { it.uppercaseChar() }
        else "${sorted.first().format(monthFmt)} - ${sorted.last().format(monthFmt)}".replaceFirstChar { it.uppercaseChar() }

        val html = buildString {
            appendLine("<!DOCTYPE html><html lang='fr'><head><meta charset='UTF-8'>")
            appendLine("<title>Releve de comptes - $periodText</title>")
            appendLine("<style>")
            appendLine(CSS)
            appendLine("</style></head><body>")

            // Header
            appendLine("<div class='header'>")
            appendLine("<h1>Releve de comptes</h1>")
            appendLine("<p class='period'>$periodText</p>")
            appendLine("<p class='date'>Genere le ${LocalDate.now().format(dateFmt)}</p>")
            appendLine("<hr class='accent'>")
            appendLine("</div>")

            // Account summary
            if (accounts.isNotEmpty()) {
                appendLine("<h2>Synthese des comptes</h2>")
                appendLine("<table><thead><tr><th>Compte</th><th>Type</th><th class='right'>Solde</th></tr></thead><tbody>")
                var total = BigDecimal.ZERO
                accounts.forEach { a ->
                    val typeLabel = when (a.accountType) {
                        AccountType.CHECKING -> "Courant"; AccountType.SAVINGS -> "Epargne"
                        AccountType.CASH -> "Especes"; AccountType.CREDIT_CARD -> "Carte credit"
                        AccountType.INVESTMENT -> "Investissement"
                    }
                    val cls = if (a.balance < BigDecimal.ZERO) "expense" else ""
                    appendLine("<tr><td>${a.name}</td><td>$typeLabel</td><td class='right $cls'>${curr.format(a.balance)}</td></tr>")
                    total = total.add(a.balance)
                }
                val totalCls = if (total < BigDecimal.ZERO) "expense" else "income"
                appendLine("<tr class='total'><td colspan='2'>Total</td><td class='right $totalCls'>${curr.format(total)}</td></tr>")
                appendLine("</tbody></table>")
            }

            // Per month
            sorted.forEach { m ->
                val ms = m.atDay(1).atStartOfDay()
                val me = m.atEndOfMonth().atTime(23, 59, 59)
                val mtx = transactions.filter { it.date >= ms && it.date <= me }.sortedBy { it.date }
                val title = m.format(monthFmt).replaceFirstChar { it.uppercaseChar() }

                appendLine("<h2 class='month-title'>$title</h2>")

                if (mtx.isEmpty()) {
                    appendLine("<p class='empty'>Aucune transaction pour ce mois.</p>")
                    return@forEach
                }

                val inc = mtx.filter { it.transactionType == TransactionType.INCOME }.fold(BigDecimal.ZERO) { a, t -> a.add(t.amount) }
                val exp = mtx.filter { it.transactionType == TransactionType.EXPENSE }.fold(BigDecimal.ZERO) { a, t -> a.add(t.amount) }
                val net = inc.subtract(exp)

                appendLine("<div class='summary-row'>")
                appendLine("<div class='summary-card income-bg'><span class='label'>Revenus</span><span class='income'>${curr.format(inc)}</span></div>")
                appendLine("<div class='summary-card expense-bg'><span class='label'>Depenses</span><span class='expense'>${curr.format(exp)}</span></div>")
                val netCls = if (net >= BigDecimal.ZERO) "income" else "expense"
                appendLine("<div class='summary-card'><span class='label'>Solde net</span><span class='$netCls'>${curr.format(net)}</span></div>")
                appendLine("</div>")

                appendLine("<table><thead><tr><th>Date</th><th>Libelle</th><th>Categorie</th><th>Type</th><th class='right'>Montant</th></tr></thead><tbody>")
                mtx.forEach { tx ->
                    val typeLabel = when (tx.transactionType) {
                        TransactionType.INCOME -> "Revenu"; TransactionType.EXPENSE -> "Depense"; TransactionType.TRANSFER -> "Transfert"
                    }
                    val typeCls = when (tx.transactionType) {
                        TransactionType.INCOME -> "income"; TransactionType.EXPENSE -> "expense"; TransactionType.TRANSFER -> "transfer"
                    }
                    val sign = if (tx.transactionType == TransactionType.EXPENSE) "-" else "+"
                    appendLine("<tr><td>${tx.date.format(dateFmt)}</td><td>${tx.title}</td><td>${tx.categoryName ?: "-"}</td><td class='$typeCls'>$typeLabel</td><td class='right $typeCls'>$sign${curr.format(tx.amount)}</td></tr>")
                }
                appendLine("</tbody></table>")
            }

            // Global summary if multi-month
            if (sorted.size > 1) {
                appendLine("<div class='page-break'></div>")
                appendLine("<h2>Synthese globale</h2>")
                appendLine("<table><thead><tr><th>Mois</th><th class='right'>Revenus</th><th class='right'>Depenses</th><th class='right'>Solde net</th></tr></thead><tbody>")
                var gi = BigDecimal.ZERO; var ge = BigDecimal.ZERO
                sorted.forEach { m ->
                    val ms = m.atDay(1).atStartOfDay(); val me = m.atEndOfMonth().atTime(23, 59, 59)
                    val mtx = transactions.filter { it.date >= ms && it.date <= me }
                    val i = mtx.filter { it.transactionType == TransactionType.INCOME }.fold(BigDecimal.ZERO) { a, t -> a.add(t.amount) }
                    val e = mtx.filter { it.transactionType == TransactionType.EXPENSE }.fold(BigDecimal.ZERO) { a, t -> a.add(t.amount) }
                    val n = i.subtract(e); gi = gi.add(i); ge = ge.add(e)
                    val nc = if (n >= BigDecimal.ZERO) "income" else "expense"
                    appendLine("<tr><td>${m.format(monthFmt).replaceFirstChar { it.uppercaseChar() }}</td><td class='right income'>${curr.format(i)}</td><td class='right expense'>${curr.format(e)}</td><td class='right $nc'><strong>${curr.format(n)}</strong></td></tr>")
                }
                val gn = gi.subtract(ge); val gnc = if (gn >= BigDecimal.ZERO) "income" else "expense"
                appendLine("<tr class='total'><td>TOTAL</td><td class='right income'>${curr.format(gi)}</td><td class='right expense'>${curr.format(ge)}</td><td class='right $gnc'><strong>${curr.format(gn)}</strong></td></tr>")
                appendLine("</tbody></table>")
            }

            appendLine("<div class='footer'>Budget Manager &mdash; Genere le ${LocalDate.now().format(dateFmt)}</div>")
            appendLine("</body></html>")
        }

        File(path).writeText(html, Charsets.UTF_8)
    }

    companion object {
        private val CSS = """
            * { margin: 0; padding: 0; box-sizing: border-box; }
            body { font-family: 'Segoe UI', Helvetica, Arial, sans-serif; color: #2d3436; background: #fff; padding: 40px; max-width: 900px; margin: 0 auto; font-size: 13px; }
            h1 { color: #6C63FF; font-size: 28px; text-align: center; margin-bottom: 4px; }
            h2 { font-size: 18px; margin: 28px 0 12px 0; padding-bottom: 6px; border-bottom: 2px solid #e8edf4; }
            h2.month-title { color: #6C63FF; border-bottom-color: #6C63FF; }
            .header { text-align: center; margin-bottom: 30px; }
            .period { font-size: 14px; color: #636e72; }
            .date { font-size: 11px; color: #b2bec3; font-style: italic; margin-top: 2px; }
            hr.accent { border: none; border-top: 3px solid #6C63FF; margin: 16px auto; width: 60%; }
            table { width: 100%; border-collapse: collapse; margin-bottom: 20px; }
            th { background: #f5f7fa; color: #2d3436; font-weight: 600; padding: 10px 12px; text-align: left; border-bottom: 2px solid #dce1eb; font-size: 12px; }
            td { padding: 8px 12px; border-bottom: 1px solid #eef1f5; }
            tr:hover { background: #f8f9fb; }
            tr.total { background: #f5f7fa; font-weight: 700; }
            tr.total td { border-top: 2px solid #dce1eb; }
            .right { text-align: right; }
            .income { color: #00b894; }
            .expense { color: #e17055; }
            .transfer { color: #74b9ff; }
            .summary-row { display: flex; gap: 12px; margin-bottom: 16px; }
            .summary-card { flex: 1; padding: 14px; border-radius: 10px; background: #f5f7fa; text-align: center; }
            .summary-card .label { display: block; font-size: 11px; color: #636e72; margin-bottom: 4px; }
            .summary-card span:last-child { font-size: 16px; font-weight: 700; }
            .income-bg { background: #f0fff8; }
            .expense-bg { background: #fff5f0; }
            .empty { color: #b2bec3; font-style: italic; margin: 8px 0 20px; }
            .footer { text-align: center; color: #b2bec3; font-size: 10px; margin-top: 40px; padding-top: 16px; border-top: 1px solid #eef1f5; }
            .page-break { page-break-before: always; }
            @media print { body { padding: 20px; } .page-break { page-break-before: always; } }
        """.trimIndent()
    }
}
