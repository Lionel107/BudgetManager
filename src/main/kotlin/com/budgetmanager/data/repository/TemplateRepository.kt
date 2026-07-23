package com.budgetmanager.data.repository

import com.budgetmanager.data.remote.SupabaseClientProvider
import com.budgetmanager.data.remote.dto.TemplateDto
import com.budgetmanager.domain.model.Template
import com.budgetmanager.domain.model.TransactionType
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

@Serializable private data class IncrementTemplateParams(@SerialName("p_id") val id: Long)

/** Repository Modèles (templates) — backend Supabase (Postgrest). */
class TemplateRepository(private val provider: SupabaseClientProvider) {

    private val db get() = provider.client
    private val _refreshTrigger = MutableStateFlow(0L)

    // Colonnes + jointure catégorie (pour categoryName)
    private val cols = Columns.raw("*, category:categories(name)")

    fun refresh() {
        _refreshTrigger.value = System.currentTimeMillis()
    }

    fun getAll(): Flow<List<Template>> = _refreshTrigger.map {
        db.from("templates").select(cols) {
            order("usage_count", Order.DESCENDING)
        }.decodeList<TemplateDto>().map { it.toDomain() }
    }

    fun getByType(type: TransactionType): Flow<List<Template>> = _refreshTrigger.map {
        db.from("templates").select(cols) {
            filter { eq("transaction_type", type.name) }
            order("usage_count", Order.DESCENDING)
        }.decodeList<TemplateDto>().map { it.toDomain() }
    }

    suspend fun getById(id: Long): Template? =
        db.from("templates").select(cols) { filter { eq("id", id) } }
            .decodeList<TemplateDto>().firstOrNull()?.toDomain()

    suspend fun create(template: Template): Long {
        val inserted = db.from("templates").insert(template.toInsertDto()) { select() }
            .decodeSingle<TemplateDto>()
        refresh()
        return inserted.id ?: 0L
    }

    suspend fun update(template: Template) {
        db.from("templates").update(template.toInsertDto().copy(id = template.id)) {
            filter { eq("id", template.id) }
        }
        refresh()
    }

    suspend fun delete(id: Long) {
        db.from("templates").delete { filter { eq("id", id) } }
        refresh()
    }

    /** Incrément atomique du compteur d'usage (RPC serveur). */
    suspend fun incrementUsage(id: Long) {
        db.postgrest.rpc("increment_template_usage", IncrementTemplateParams(id))
        refresh()
    }

    private fun TemplateDto.toDomain() = Template(
        id = id ?: 0,
        name = name,
        defaultAmount = defaultAmount,
        categoryId = categoryId,
        categoryName = category?.name,
        transactionType = TransactionType.valueOf(transactionType),
        iconName = iconName,
        color = color,
        displayOrder = displayOrder,
        usageCount = usageCount
    )

    private fun Template.toInsertDto() = TemplateDto(
        name = name,
        defaultAmount = defaultAmount,
        categoryId = categoryId,
        transactionType = transactionType.name,
        iconName = iconName,
        color = color,
        displayOrder = displayOrder,
        usageCount = usageCount
    )
}
