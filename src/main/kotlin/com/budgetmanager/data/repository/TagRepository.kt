package com.budgetmanager.data.repository

import com.budgetmanager.data.remote.DtoDates
import com.budgetmanager.data.remote.SupabaseClientProvider
import com.budgetmanager.data.remote.dto.TagDto
import com.budgetmanager.domain.model.Tag
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable private data class GetOrCreateTagParams(@SerialName("p_name") val name: String)
@Serializable private data class SetTxnTagsParams(
    @SerialName("p_transaction_id") val transactionId: Long,
    @SerialName("p_tag_ids") val tagIds: List<Long>
)
@Serializable private data class SuggestTagsParams(
    @SerialName("p_tokens") val tokens: List<String>,
    @SerialName("p_limit") val limit: Int
)
@Serializable private data class TagEmbed(@SerialName("tag") val tag: TagDto? = null)

/** Repository Tags — backend Supabase (Postgrest + RPC atomiques). */
class TagRepository(private val provider: SupabaseClientProvider) {

    private val db get() = provider.client
    private val _refreshTrigger = MutableStateFlow(0L)

    fun refresh() {
        _refreshTrigger.value = System.currentTimeMillis()
    }

    fun getAll(): Flow<List<Tag>> = _refreshTrigger.map {
        db.from("tags").select {
            order("usage_count", Order.DESCENDING)
            order("name", Order.ASCENDING)
        }.decodeList<TagDto>().map { it.toDomain() }
    }

    suspend fun suggestByPrefix(prefix: String, limit: Int = 8): List<Tag> {
        if (prefix.isBlank()) return emptyList()
        return db.from("tags").select {
            filter { ilike("name", "${prefix.lowercase()}%") }
            order("usage_count", Order.DESCENDING)
            limit(limit.toLong())
        }.decodeList<TagDto>().map { it.toDomain() }
    }

    suspend fun getOrCreate(name: String): Long {
        val normalized = name.trim().lowercase()
        if (normalized.isBlank()) return -1
        val id = db.postgrest.rpc("get_or_create_tag", GetOrCreateTagParams(normalized)).decodeAs<Long>()
        refresh()
        return id
    }

    suspend fun getTagsForTransaction(transactionId: Long): List<Tag> =
        db.from("transaction_tags").select(Columns.raw("tag:tags(*)")) {
            filter { eq("transaction_id", transactionId) }
        }.decodeList<TagEmbed>().mapNotNull { it.tag?.toDomain() }

    /** Remplace tous les tags d'une transaction (RPC atomique + recompteur d'usage). */
    suspend fun setTransactionTags(transactionId: Long, tagNames: List<String>) {
        val cleaned = tagNames.map { it.trim().lowercase() }.filter { it.isNotBlank() }.distinct()
        val tagIds = cleaned.map { getOrCreate(it) }.filter { it >= 0 }
        db.postgrest.rpc("set_transaction_tags", SetTxnTagsParams(transactionId, tagIds))
        refresh()
    }

    suspend fun delete(id: Long) {
        // transaction_tags supprimés en cascade (FK on delete cascade)
        db.from("tags").delete { filter { eq("id", id) } }
        refresh()
    }

    suspend fun suggestForTitle(title: String, limit: Int = 5): List<Tag> {
        if (title.isBlank()) return emptyList()
        val tokens = title.lowercase().split(Regex("[^a-z0-9]+")).filter { it.length >= 3 }
        if (tokens.isEmpty()) return emptyList()
        return db.postgrest.rpc("suggest_tags_for_title", SuggestTagsParams(tokens, limit))
            .decodeList<TagDto>().map { it.toDomain() }
    }

    private fun TagDto.toDomain() = Tag(
        id = id ?: 0,
        name = name,
        color = color,
        usageCount = usageCount,
        createdAt = DtoDates.parseEpochMillis(createdAt) ?: System.currentTimeMillis()
    )
}
