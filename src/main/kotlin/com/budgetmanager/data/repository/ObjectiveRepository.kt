package com.budgetmanager.data.repository

import com.budgetmanager.data.remote.DtoDates
import com.budgetmanager.data.remote.SupabaseClientProvider
import com.budgetmanager.data.remote.dto.ObjectiveDto
import com.budgetmanager.domain.model.Objective
import com.budgetmanager.domain.model.ObjectiveType
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/** Repository Objectifs (épargne / plafond de dépense) — backend Supabase. */
class ObjectiveRepository(private val provider: SupabaseClientProvider) {

    private val db get() = provider.client
    private val cols = Columns.raw("*, category:categories(name)")
    private val _refreshTrigger = MutableStateFlow(0L)

    fun refresh() {
        _refreshTrigger.value = System.currentTimeMillis()
    }

    fun getAll(): Flow<List<Objective>> = _refreshTrigger.map {
        try {
            db.from("objectives").select(cols) {
                filter { eq("is_active", true) }
                order("target_date", Order.ASCENDING)
            }.decodeList<ObjectiveDto>().map { it.toDomain() }
        } catch (e: Exception) {
            // Repli si PostgREST ne connaît pas la relation objectives→categories
            // (contrainte FK absente ou cache de schéma périmé) : on lit sans la jointure.
            db.from("objectives").select(Columns.raw("*")) {
                filter { eq("is_active", true) }
                order("target_date", Order.ASCENDING)
            }.decodeList<ObjectiveDto>().map { it.toDomain() }
        }
    }

    suspend fun create(objective: Objective): Long {
        val inserted = db.from("objectives").insert(objective.toInsertDto()) { select() }
            .decodeSingle<ObjectiveDto>()
        refresh()
        return inserted.id ?: 0L
    }

    suspend fun update(objective: Objective) {
        db.from("objectives").update(objective.toInsertDto().copy(id = objective.id)) {
            filter { eq("id", objective.id) }
        }
        refresh()
    }

    suspend fun delete(id: Long) {
        db.from("objectives").delete { filter { eq("id", id) } }
        refresh()
    }

    private fun ObjectiveDto.toDomain() = Objective(
        id = id ?: 0,
        title = title,
        type = ObjectiveType.valueOf(type),
        targetAmount = targetAmount,
        targetDate = DtoDates.parseDate(targetDate),
        categoryId = categoryId,
        categoryName = category?.name,
        isActive = isActive
    )

    private fun Objective.toInsertDto() = ObjectiveDto(
        title = title,
        type = type.name,
        targetAmount = targetAmount,
        targetDate = targetDate?.let { DtoDates.formatDate(it) },
        categoryId = categoryId,
        isActive = isActive
    )
}
