package com.budgetmanager.util

import com.budgetmanager.domain.model.Account
import com.budgetmanager.domain.model.AccountType
import com.budgetmanager.domain.model.BudgetState
import com.budgetmanager.domain.model.BudgetWithStatus
import com.budgetmanager.domain.model.Transaction
import com.budgetmanager.domain.model.TransactionType
import java.io.OutputStreamWriter
import java.math.BigDecimal
import java.math.RoundingMode
import java.net.HttpURLConnection
import java.net.URL
import java.time.YearMonth

/**
 * Wraps a call to Google's Gemini API to enrich the local advice engine
 * with more nuanced, AI-generated suggestions. Returns parsed [FinancialAdvice].
 *
 * Falls back to an empty list on any error (network, missing key, bad parse) —
 * the local rule engine is always used as a baseline.
 */
class GeminiAdviceService {

    /**
     * Send the user's financial summary to Gemini and parse 3-5 advice cards.
     * @return list of advice, or empty list on failure.
     */
    fun fetchAdvice(
        apiKey: String,
        accounts: List<Account>,
        transactions: List<Transaction>,
        budgets: List<BudgetWithStatus>,
        savingsGoal: BigDecimal
    ): List<FinancialAdvice> {
        if (apiKey.isBlank()) return emptyList()

        return try {
            val context = buildContext(accounts, transactions, budgets, savingsGoal)
            val prompt = buildPrompt(context)
            val response = callGemini(apiKey, prompt)
            val text = extractTextFromResponse(response)
            if (text.isNullOrBlank()) {
                AppLogger.warn("GeminiAdvice", "Could not extract text. Raw response: ${response.take(500)}")
                return emptyList()
            }
            val parsed = parseAdviceJson(text)
            if (parsed.isEmpty()) {
                AppLogger.warn("GeminiAdvice", "Parsed 0 advice from text: ${text.take(500)}")
            }
            parsed
        } catch (e: Exception) {
            AppLogger.warn("GeminiAdvice", "Failed to fetch advice", e)
            emptyList()
        }
    }

    private fun buildContext(
        accounts: List<Account>,
        transactions: List<Transaction>,
        budgets: List<BudgetWithStatus>,
        savingsGoal: BigDecimal
    ): String {
        val now = YearMonth.now()
        val prev = now.minusMonths(1)
        val prev2 = now.minusMonths(2)

        val thisMonthTx = transactions.filter { YearMonth.from(it.date) == now }
        val prevMonthTx = transactions.filter { YearMonth.from(it.date) == prev }
        val prev2MonthTx = transactions.filter { YearMonth.from(it.date) == prev2 }

        val thisIncome = sumByType(thisMonthTx, TransactionType.INCOME)
        val thisExpenses = sumByType(thisMonthTx, TransactionType.EXPENSE)
        val prevIncome = sumByType(prevMonthTx, TransactionType.INCOME)
        val prevExpenses = sumByType(prevMonthTx, TransactionType.EXPENSE)
        val prev2Expenses = sumByType(prev2MonthTx, TransactionType.EXPENSE)

        // Top 5 categories with breakdown of biggest transactions inside
        val topCategoriesDetailed = thisMonthTx
            .filter { it.transactionType == TransactionType.EXPENSE && it.categoryName != null }
            .groupBy { it.categoryName!! }
            .mapValues { (_, txs) -> txs.fold(BigDecimal.ZERO) { a, t -> a.add(t.amount) } to txs }
            .entries.sortedByDescending { it.value.first }
            .take(5)
            .joinToString("\n") { (cat, p) ->
                val (total, txs) = p
                val topTx = txs.sortedByDescending { it.amount }.take(3)
                    .joinToString("; ") { "${it.title} ${fmt(it.amount)}EUR" }
                "  - $cat: ${fmt(total)} EUR (${txs.size} tx, top: $topTx)"
            }

        // Top 15 individual expense transactions (with title and date)
        val topExpenses = thisMonthTx
            .filter { it.transactionType == TransactionType.EXPENSE }
            .sortedByDescending { it.amount }
            .take(15)
            .joinToString("\n") { t ->
                val tags = if (t.tags.isNotEmpty()) " #${t.tags.joinToString(",")}" else ""
                "  - ${t.date.toLocalDate()} | ${t.title} | ${fmt(t.amount)} EUR | ${t.categoryName ?: "non categorise"}$tags"
            }

        // Detect recurring purchases by title (same title appearing >= 2 times in last 3 months)
        val all3Months = thisMonthTx + prevMonthTx + prev2MonthTx
        val recurringByTitle = all3Months
            .filter { it.transactionType == TransactionType.EXPENSE && it.title.isNotBlank() }
            .groupBy { it.title.trim().lowercase() }
            .filter { it.value.size >= 2 }
            .map { (_, txs) ->
                val display = txs.first().title.trim()
                val totalAmount = txs.fold(BigDecimal.ZERO) { a, t -> a.add(t.amount) }
                val avg = totalAmount.divide(BigDecimal(txs.size), 2, RoundingMode.HALF_UP)
                Triple(display, txs.size, avg)
            }
            .sortedByDescending { it.third.multiply(BigDecimal(it.second)) }
            .take(10)
            .joinToString("\n") { (title, count, avg) ->
                "  - \"$title\" : $count fois en 3 mois, environ ${fmt(avg)} EUR par achat"
            }

        // Last 7 days vs the 7 previous days — detect spending acceleration
        val today = java.time.LocalDate.now()
        val last7 = transactions.filter {
            it.transactionType == TransactionType.EXPENSE &&
            it.date.toLocalDate() in today.minusDays(6)..today
        }.fold(BigDecimal.ZERO) { a, t -> a.add(t.amount) }
        val prev7 = transactions.filter {
            it.transactionType == TransactionType.EXPENSE &&
            it.date.toLocalDate() in today.minusDays(13)..today.minusDays(7)
        }.fold(BigDecimal.ZERO) { a, t -> a.add(t.amount) }

        // Accounts summary
        val accountSummary = accounts.filter { it.isActive }.joinToString("\n") { a ->
            val type = when (a.accountType) {
                AccountType.CHECKING -> "courant"
                AccountType.SAVINGS -> "epargne"
                AccountType.CASH -> "especes"
                AccountType.CREDIT_CARD -> "carte de credit"
                AccountType.INVESTMENT -> "investissement"
            }
            buildString {
                append("  - ${a.name} ($type): ${fmt(a.balance)} ${a.currencyCode}")
                if (a.accountType == AccountType.INVESTMENT && a.initialCapital != null) {
                    append(" — capital ${fmt(a.initialCapital)}, gain ${String.format("%.1f", a.gainPercent * 100)}%, provision impots ${fmt(a.taxProvision)}")
                }
            }
        }

        // Budgets
        val budgetSummary = budgets.joinToString("\n") { b ->
            val pct = (b.percentage * 100).toInt()
            val state = when (b.state) {
                BudgetState.SAFE -> "OK"
                BudgetState.WARNING -> "ATTENTION"
                BudgetState.ALERT -> "DEPASSE"
            }
            val remaining = b.budget.limit.subtract(b.spent)
            "  - ${b.budget.categoryName}: ${fmt(b.spent)} / ${fmt(b.budget.limit)} EUR ($pct%, restant ${fmt(remaining)}) — $state"
        }

        // Total balance and runway estimation
        val totalBalance = accounts.filter { it.isActive && it.accountType != AccountType.CREDIT_CARD }
            .fold(BigDecimal.ZERO) { a, acc -> a.add(acc.balance) }
        val daysElapsed = today.dayOfMonth.coerceAtLeast(1)
        val daysInMonth = today.lengthOfMonth()
        val dailyExpenses = if (daysElapsed > 0)
            thisExpenses.divide(BigDecimal(daysElapsed), 2, RoundingMode.HALF_UP) else BigDecimal.ZERO
        val daysRunway = if (dailyExpenses > BigDecimal.ZERO)
            totalBalance.divide(dailyExpenses, 0, RoundingMode.DOWN).toInt() else -1

        return buildString {
            appendLine("Date du jour : $today")
            appendLine("Solde total (hors carte credit) : ${fmt(totalBalance)} EUR")
            if (daysRunway >= 0) {
                appendLine("Au rythme actuel (~${fmt(dailyExpenses)} EUR/jour) : autonomie ~$daysRunway jours")
            }
            appendLine()
            appendLine("=== COMPTES ===")
            if (accountSummary.isBlank()) appendLine("  (aucun)") else appendLine(accountSummary)
            appendLine()
            appendLine("=== MOIS EN COURS ($now, jour $daysElapsed/$daysInMonth) ===")
            appendLine("  Revenus : ${fmt(thisIncome)} EUR")
            appendLine("  Depenses : ${fmt(thisExpenses)} EUR")
            appendLine("  Solde net : ${fmt(thisIncome.subtract(thisExpenses))} EUR")
            if (thisIncome > BigDecimal.ZERO) {
                val rate = thisIncome.subtract(thisExpenses).divide(thisIncome, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal(100))
                appendLine("  Taux d'epargne : ${String.format("%.1f", rate.toDouble())}%")
            }
            appendLine()
            appendLine("=== MOIS PRECEDENT ($prev) ===")
            appendLine("  Revenus : ${fmt(prevIncome)} EUR")
            appendLine("  Depenses : ${fmt(prevExpenses)} EUR")
            if (prevExpenses > BigDecimal.ZERO && prev2Expenses > BigDecimal.ZERO) {
                appendLine("  Mois -2 ($prev2) depenses : ${fmt(prev2Expenses)} EUR")
            }
            appendLine()
            appendLine("=== 7 DERNIERS JOURS vs 7 PRECEDENTS ===")
            appendLine("  Cette semaine : ${fmt(last7)} EUR")
            appendLine("  Semaine d'avant : ${fmt(prev7)} EUR")
            if (prev7 > BigDecimal.ZERO) {
                val diff = last7.subtract(prev7)
                val pct = diff.divide(prev7, 4, RoundingMode.HALF_UP).multiply(BigDecimal(100))
                appendLine("  Variation : ${if (diff >= BigDecimal.ZERO) "+" else ""}${String.format("%.1f", pct.toDouble())}%")
            }
            appendLine()
            appendLine("=== TOP CATEGORIES (avec exemples concrets) ===")
            if (topCategoriesDetailed.isBlank()) appendLine("  (aucune)") else appendLine(topCategoriesDetailed)
            appendLine()
            appendLine("=== 15 PLUS GROSSES DEPENSES DU MOIS ===")
            if (topExpenses.isBlank()) appendLine("  (aucune)") else appendLine(topExpenses)
            appendLine()
            appendLine("=== ACHATS RECURRENTS (titres revenant >=2 fois en 3 mois) ===")
            appendLine("Utile pour identifier abonnements, livraisons frequentes, etc.")
            if (recurringByTitle.isBlank()) appendLine("  (aucun pattern detecte)") else appendLine(recurringByTitle)
            appendLine()
            appendLine("=== BUDGETS ===")
            if (budgetSummary.isBlank()) appendLine("  (aucun budget defini)") else appendLine(budgetSummary)
            appendLine()
            if (savingsGoal > BigDecimal.ZERO) {
                appendLine("=== OBJECTIF EPARGNE MENSUEL : ${fmt(savingsGoal)} EUR ===")
            }
        }
    }

    private fun sumByType(txs: List<Transaction>, type: TransactionType): BigDecimal =
        txs.filter { it.transactionType == type }.fold(BigDecimal.ZERO) { a, t -> a.add(t.amount) }

    private fun buildPrompt(context: String): String {
        return """
            Tu es un coach financier personnel francais, expert, direct et tres pragmatique.
            Ton role : aider l'utilisateur a ameliorer ses finances en lui donnant des CONSEILS
            CONCRETS, NOMMES et CHIFFRES, qu'il peut appliquer DES AUJOURD'HUI.

            VOICI SES DONNEES PERSONNELLES :

            $context

            ---

            REGLES STRICTES POUR TES CONSEILS :

            1. **Donne entre 6 et 10 conseils.** Pas moins. Mieux vaut trop que pas assez.

            2. **CITE LES TRANSACTIONS PAR NOM quand pertinent.** Tu as la liste des achats : utilise-la.
               - BIEN : "Tu as commande Uber Eats 4 fois ce mois (62 EUR au total). Cuisiner ces soirs
                 te ferait economiser ~50 EUR/mois soit 600 EUR/an."
               - PAS BIEN : "Reduisez vos depenses de livraison."

            3. **CHIFFRE l'impact en EUR par mois ET par an.** Les gens reagissent aux chiffres concrets.
               - BIEN : "Annuler Netflix (15 EUR/mois) economise 180 EUR/an, soit 5% de plus pour ton
                 epargne annuelle."
               - PAS BIEN : "Pense a couper les abonnements inutiles."

            4. **Detecte les patterns suspects** dans la liste des achats recurrents :
               - Abonnements possibles (memes noms reguliers) — propose de les couper ou de les
                 mutualiser
               - Achats impulsifs (gros montants ponctuels) — propose une regle de 24h avant achat > X EUR
               - Doublons (deux abonnements similaires) — suggere de choisir un seul
               - Depenses week-end > semaine ou inverse — propose des activites alternatives

            5. **Propose des ACTIONS PRECISES :**
               - Quelle categorie reduire, de combien, comment
               - Quel produit annuler ou changer de fournisseur (operateur, assurance, banque)
               - Quel montant mettre de cote, sur quel type de produit (livret A, PEA, assurance-vie)
               - Quelle habitude changer (repas a la maison, transports, energie)

            6. **Sois honnete sur les bonnes choses aussi.** S'il y a un bon comportement, dis-le
               (level GOOD) — c'est motivant.

            7. **Detecte les urgences :**
               - Solde proche zero (autonomie < 7 jours) → level CRITICAL
               - Depenses qui s'accelerent significativement → WARNING
               - Budgets depasses → WARNING ou CRITICAL selon ampleur

            8. **Conseils sur l'epargne et l'investissement :**
               - Si excedent regulier : proposer des supports adaptes au montant (Livret A jusqu'a
                 22950 EUR, LDDS 12000 EUR, PEA, AV, immobilier...)
               - Si comptes d'investissement existants : commenter le rendement, rappeler la
                 provision d'impots, suggerer rebalancage si besoin

            9. **Reste concret et utilisable.** Pas de generalites du type "Faites un budget", "Economisez
               quand vous pouvez", "Pensez long terme". Si tu ecris ca, c'est rate.

            10. **Tutoiement, ton bienveillant mais direct.** Pas de phrases bateau. Du concret.

            ---

            FORMAT DE REPONSE (OBLIGATOIRE) :
            Reponds UNIQUEMENT avec un tableau JSON valide. Pas de markdown, pas de backticks,
            pas de commentaire avant ou apres.

            [
              {
                "title": "Titre court max 70 caracteres (peut etre piquant ou drole)",
                "message": "2 a 4 phrases. Chiffre l'impact. Cite des noms reels de la liste fournie si pertinent. Propose une action concrete.",
                "level": "INFO | GOOD | WARNING | CRITICAL"
              }
            ]

            Trie tes conseils par ordre d'importance (les plus impactants en premier).
        """.trimIndent()
    }

    /**
     * Ordered list of model IDs to try. If one returns 503/UNAVAILABLE/429,
     * we fall back to the next. Newest/most capable first.
     */
    private val modelFallbacks = listOf(
        "gemini-2.5-flash",
        "gemini-2.0-flash",
        "gemini-1.5-flash"
    )

    private fun callGemini(apiKey: String, prompt: String): String {
        val body = """
            {
              "contents": [
                {
                  "parts": [
                    { "text": ${jsonString(prompt)} }
                  ]
                }
              ],
              "generationConfig": {
                "temperature": 0.75,
                "maxOutputTokens": 4000,
                "thinkingConfig": { "thinkingBudget": 0 },
                "responseMimeType": "application/json"
              }
            }
        """.trimIndent()

        var lastError: String = "Unknown error"
        var lastStatus: Int = 0

        for (model in modelFallbacks) {
            val urlStr = "https://generativelanguage.googleapis.com/v1beta/models/" +
                         "$model:generateContent?key=$apiKey"
            val connection = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 10_000
                readTimeout = 30_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                setRequestProperty("Accept", "application/json")
            }
            try {
                OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { it.write(body) }
                val status = connection.responseCode
                val streamReader = if (status in 200..299) connection.inputStream else connection.errorStream
                val responseBody = streamReader?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""

                if (status in 200..299) {
                    AppLogger.info("GeminiAdvice", "Success using model $model")
                    return responseBody
                }

                lastStatus = status
                lastError = responseBody
                AppLogger.warn("GeminiAdvice", "Model $model returned $status, trying next fallback")

                // Only retry on transient errors. For auth/quota issues, give up immediately.
                if (status !in listOf(429, 500, 502, 503, 504)) {
                    error(humanError(status, responseBody))
                }
            } catch (e: IllegalStateException) {
                throw e // bubble up the human-friendly error
            } catch (e: Exception) {
                lastError = e.message ?: "Network error"
                AppLogger.warn("GeminiAdvice", "Model $model failed with exception", e)
            } finally {
                connection.disconnect()
            }
        }

        // All models exhausted
        error(humanError(lastStatus, lastError))
    }

    private fun humanError(status: Int, body: String): String = when (status) {
        429 -> "Quota Gemini depasse (15 req/min en free tier). Reessaye dans 1 minute."
        503, 500, 502, 504 -> "Tous les modeles Gemini sont saturees actuellement. Reessaye dans quelques minutes."
        401, 403 -> "Cle API invalide ou non autorisee. Verifie qu'elle vient bien de aistudio.google.com/apikey."
        400 -> "Requete invalide envoyee a Gemini. ${body.take(150)}"
        else -> "Erreur Gemini ($status). ${body.take(150)}"
    }

    /**
     * Extract text content from a Gemini API response. Concatenates ALL non-thought
     * text parts across all candidates. Robust to:
     * - Multiple parts in the response (text + thinking)
     * - "thought": true marker on internal reasoning parts (we skip those)
     * - Unicode escapes
     */
    private fun extractTextFromResponse(json: String): String? {
        val parts = extractAllTextParts(json)
        if (parts.isEmpty()) return null
        // Concatenate — usually it's a single part but Gemini can split
        return parts.joinToString("\n").takeIf { it.isNotBlank() }
    }

    /** Find every "text": "..." value, skipping parts marked with "thought": true. */
    private fun extractAllTextParts(json: String): List<String> {
        val results = mutableListOf<String>()
        var i = 0
        while (i < json.length) {
            // Find next "text" key
            val textKeyIdx = json.indexOf("\"text\"", i)
            if (textKeyIdx < 0) break

            // Check if the enclosing object has "thought": true — if so, skip it.
            // Heuristic: look backwards within a few hundred chars for "thought"
            val lookback = json.substring(maxOf(0, textKeyIdx - 300), textKeyIdx)
            val isThought = lookback.contains("\"thought\"") && lookback.contains("true")

            // Find the opening quote of the value
            var p = textKeyIdx + 6 // past "text"
            while (p < json.length && json[p] != '"') p++
            if (p >= json.length) break
            p++ // skip opening quote

            // Read the string value
            val sb = StringBuilder()
            while (p < json.length) {
                val c = json[p]
                if (c == '\\' && p + 1 < json.length) {
                    when (json[p + 1]) {
                        'n' -> sb.append('\n')
                        't' -> sb.append('\t')
                        'r' -> sb.append('\r')
                        '"' -> sb.append('"')
                        '\\' -> sb.append('\\')
                        '/' -> sb.append('/')
                        'u' -> {
                            if (p + 5 < json.length) {
                                val hex = json.substring(p + 2, p + 6)
                                sb.append(hex.toInt(16).toChar())
                                p += 4
                            }
                        }
                        else -> sb.append(json[p + 1])
                    }
                    p += 2
                } else if (c == '"') {
                    if (!isThought) results.add(sb.toString())
                    p++
                    break
                } else {
                    sb.append(c)
                    p++
                }
            }
            i = p
        }
        return results
    }

    /**
     * Parse the JSON array that Gemini returned. Tolerant to surrounding text/whitespace
     * and to backticks if the model wrapped the output despite instructions.
     */
    private fun parseAdviceJson(raw: String): List<FinancialAdvice> {
        // Strip optional markdown fences
        val cleaned = raw
            .replace(Regex("^\\s*```(?:json)?\\s*", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s*```\\s*$"), "")
            .trim()

        // Locate the JSON array boundaries
        val start = cleaned.indexOf('[')
        val end = cleaned.lastIndexOf(']')
        if (start < 0 || end < 0 || end <= start) return emptyList()
        val arrayBody = cleaned.substring(start + 1, end)

        // Split by top-level } , { pairs — simple state machine to handle nested commas
        val items = mutableListOf<String>()
        var depth = 0
        val current = StringBuilder()
        var inString = false
        var prev = ' '
        for (c in arrayBody) {
            if (c == '"' && prev != '\\') inString = !inString
            if (!inString) {
                when (c) {
                    '{' -> depth++
                    '}' -> {
                        depth--
                        if (depth == 0) {
                            current.append(c)
                            items += current.toString()
                            current.clear()
                            prev = c
                            continue
                        }
                    }
                }
            }
            if (depth > 0 || c == '{') current.append(c)
            prev = c
        }

        return items.mapNotNull { parseAdviceObject(it) }
    }

    /** Parse a single { "title": "...", "message": "...", "level": "..." } object. */
    private fun parseAdviceObject(json: String): FinancialAdvice? {
        val title = extractJsonField(json, "title") ?: return null
        val message = extractJsonField(json, "message") ?: return null
        val levelStr = extractJsonField(json, "level") ?: "INFO"
        val level = when (levelStr.uppercase()) {
            "CRITICAL" -> AdviceLevel.CRITICAL
            "WARNING" -> AdviceLevel.WARNING
            "GOOD" -> AdviceLevel.GOOD
            else -> AdviceLevel.INFO
        }
        return FinancialAdvice(
            title = title,
            message = message,
            level = level,
            category = AdviceCategory.GENERAL
        )
    }

    private fun extractJsonField(json: String, field: String): String? {
        val key = "\"$field\""
        val idx = json.indexOf(key)
        if (idx < 0) return null
        var i = idx + key.length
        // Skip whitespace and colon
        while (i < json.length && json[i] != '"') {
            if (json[i] !in " :\t\r\n") return null
            i++
        }
        if (i >= json.length) return null
        i++ // skip opening quote
        val sb = StringBuilder()
        while (i < json.length) {
            val c = json[i]
            if (c == '\\' && i + 1 < json.length) {
                when (json[i + 1]) {
                    'n' -> sb.append('\n')
                    '"' -> sb.append('"')
                    '\\' -> sb.append('\\')
                    else -> sb.append(json[i + 1])
                }
                i += 2
            } else if (c == '"') {
                return sb.toString()
            } else {
                sb.append(c)
                i++
            }
        }
        return null
    }

    private fun jsonString(s: String): String {
        val escaped = s
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
        return "\"$escaped\""
    }

    private fun fmt(amount: BigDecimal): String = String.format("%.2f", amount.toDouble())
}
