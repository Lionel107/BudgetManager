package com.budgetmanager.data.repository

import com.budgetmanager.data.database.Tags
import com.budgetmanager.data.database.TransactionTags
import com.budgetmanager.domain.model.Tag
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction

class TagRepository {

    private val _refreshTrigger = MutableStateFlow(0L)

    fun refresh() {
        _refreshTrigger.value = System.currentTimeMillis()
    }

    /** All tags ordered by usage (most-used first), then alphabetical. */
    fun getAll(): Flow<List<Tag>> {
        return _refreshTrigger.map {
            withContext(Dispatchers.IO) {
                transaction {
                    Tags.selectAll()
                        .orderBy(Tags.usageCount, SortOrder.DESC)
                        .orderBy(Tags.name)
                        .map { it.toTag() }
                }
            }
        }
    }

    /** Suggest tags whose name starts with the given prefix. Used for autocomplete. */
    suspend fun suggestByPrefix(prefix: String, limit: Int = 8): List<Tag> = withContext(Dispatchers.IO) {
        if (prefix.isBlank()) return@withContext emptyList()
        transaction {
            Tags.selectAll()
                .where { Tags.name like "${prefix.lowercase()}%" }
                .orderBy(Tags.usageCount, SortOrder.DESC)
                .limit(limit)
                .map { it.toTag() }
        }
    }

    suspend fun getOrCreate(name: String): Long = withContext(Dispatchers.IO) {
        val normalized = name.trim().lowercase()
        if (normalized.isBlank()) return@withContext -1
        transaction {
            val existing = Tags.selectAll().where { Tags.name eq normalized }.singleOrNull()
            existing?.get(Tags.id) ?: Tags.insert {
                it[Tags.name] = normalized
                it[Tags.usageCount] = 0
                it[Tags.createdAt] = System.currentTimeMillis()
            } get Tags.id
        }.also { refresh() }
    }

    suspend fun getTagsForTransaction(transactionId: Long): List<Tag> = withContext(Dispatchers.IO) {
        transaction {
            Tags.join(TransactionTags, JoinType.INNER, Tags.id, TransactionTags.tagId)
                .selectAll()
                .where { TransactionTags.transactionId eq transactionId }
                .map { it.toTag() }
        }
    }

    /** Replace all tags on a transaction with the given list. */
    suspend fun setTransactionTags(transactionId: Long, tagNames: List<String>) = withContext(Dispatchers.IO) {
        val cleaned = tagNames.map { it.trim().lowercase() }.filter { it.isNotBlank() }.distinct()
        val tagIds = cleaned.map { getOrCreate(it) }.filter { it >= 0 }

        transaction {
            // Decrement usage on previous tags
            val previous = TransactionTags.selectAll()
                .where { TransactionTags.transactionId eq transactionId }
                .map { it[TransactionTags.tagId] }

            TransactionTags.deleteWhere { TransactionTags.transactionId eq transactionId }

            for (tagId in tagIds) {
                TransactionTags.insert {
                    it[TransactionTags.transactionId] = transactionId
                    it[TransactionTags.tagId] = tagId
                }
            }

            // Recompute usage_count for affected tags (previous + new)
            val affected = (previous + tagIds).distinct()
            for (tagId in affected) {
                val count = TransactionTags.selectAll()
                    .where { TransactionTags.tagId eq tagId }
                    .count()
                Tags.update({ Tags.id eq tagId }) {
                    it[Tags.usageCount] = count.toInt()
                }
            }
        }
        refresh()
    }

    suspend fun delete(id: Long) = withContext(Dispatchers.IO) {
        transaction {
            TransactionTags.deleteWhere { TransactionTags.tagId eq id }
            Tags.deleteWhere { Tags.id eq id }
        }
        refresh()
    }

    /** Smart suggestion: based on the transaction title, suggest tags from past similar transactions. */
    suspend fun suggestForTitle(title: String, limit: Int = 5): List<Tag> = withContext(Dispatchers.IO) {
        if (title.isBlank()) return@withContext emptyList()
        val tokens = title.lowercase().split(Regex("[^a-z0-9]+")).filter { it.length >= 3 }
        if (tokens.isEmpty()) return@withContext emptyList()
        transaction {
            // Find tags that appear in transactions whose title contains any of these tokens
            val likeClauses = tokens.joinToString(" OR ") { "lower(t.title) LIKE '%$it%'" }
            val sql = """
                SELECT tg.id, tg.name, tg.color, tg.usage_count, tg.created_at,
                       COUNT(*) AS hits
                FROM tags tg
                JOIN transaction_tags tt ON tt.tag_id = tg.id
                JOIN transactions t ON t.id = tt.transaction_id
                WHERE $likeClauses
                GROUP BY tg.id
                ORDER BY hits DESC, tg.usage_count DESC
                LIMIT $limit
            """.trimIndent()

            val results = mutableListOf<Tag>()
            org.jetbrains.exposed.sql.transactions.TransactionManager.current().exec(sql) { rs ->
                while (rs.next()) {
                    results += Tag(
                        id = rs.getLong("id"),
                        name = rs.getString("name"),
                        color = rs.getString("color"),
                        usageCount = rs.getInt("usage_count"),
                        createdAt = rs.getLong("created_at")
                    )
                }
            }
            results
        }
    }

    private fun ResultRow.toTag(): Tag {
        return Tag(
            id = this[Tags.id],
            name = this[Tags.name],
            color = this[Tags.color],
            usageCount = this[Tags.usageCount],
            createdAt = this[Tags.createdAt]
        )
    }
}
