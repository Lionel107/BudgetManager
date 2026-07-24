package com.budgetmanager.data.repository

import com.budgetmanager.data.remote.SupabaseClientProvider
import com.budgetmanager.data.remote.dto.CategoryDto
import com.budgetmanager.domain.model.Category
import com.budgetmanager.domain.model.TransactionType
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * Repository Catégories — backend Supabase (Postgrest).
 * La RLS filtre automatiquement par utilisateur ; user_id est rempli à l'insert
 * par le défaut auth.uid() côté Postgres. Le pattern Flow + _refreshTrigger est
 * conservé : chaque écriture appelle refresh() pour re-déclencher les lectures.
 */
class CategoryRepository(private val provider: SupabaseClientProvider) {

    private val db get() = provider.client
    private val _refreshTrigger = MutableStateFlow(0L)

    fun refresh() {
        _refreshTrigger.value = System.currentTimeMillis()
    }

    fun getAllCategories(): Flow<List<Category>> = _refreshTrigger.map {
        db.from("categories").select {
            filter { eq("is_active", true) }
            order("display_order", Order.ASCENDING)
        }.decodeList<CategoryDto>().map { it.toDomain() }
    }

    fun getAllCategoriesIncludingInactive(): Flow<List<Category>> = _refreshTrigger.map {
        db.from("categories").select {
            order("display_order", Order.ASCENDING)
        }.decodeList<CategoryDto>().map { it.toDomain() }
    }

    fun getExpenseCategories(): Flow<List<Category>> = _refreshTrigger.map {
        db.from("categories").select {
            filter {
                eq("category_type", TransactionType.EXPENSE.name)
                eq("is_active", true)
            }
            order("display_order", Order.ASCENDING)
        }.decodeList<CategoryDto>().map { it.toDomain() }
    }

    fun getIncomeCategories(): Flow<List<Category>> = _refreshTrigger.map {
        db.from("categories").select {
            filter {
                eq("category_type", TransactionType.INCOME.name)
                eq("is_active", true)
            }
            order("display_order", Order.ASCENDING)
        }.decodeList<CategoryDto>().map { it.toDomain() }
    }

    suspend fun getCategoryById(id: Long): Category? =
        db.from("categories").select {
            filter { eq("id", id) }
        }.decodeList<CategoryDto>().firstOrNull()?.toDomain()

    suspend fun createCategory(category: Category): Long {
        val inserted = db.from("categories").insert(category.toInsertDto()) {
            select()
        }.decodeSingle<CategoryDto>()
        refresh()
        return inserted.id ?: 0L
    }

    suspend fun updateCategory(category: Category) {
        db.from("categories").update(category.toInsertDto().copy(id = category.id)) {
            filter { eq("id", category.id) }
        }
        refresh()
    }

    /** Soft-delete : marque la catégorie inactive. */
    suspend fun deleteCategory(id: Long) {
        db.from("categories").update({ set("is_active", false) }) {
            filter { eq("id", id) }
        }
        refresh()
    }

    /** Hard-delete réservé au nettoyage. */
    suspend fun hardDeleteCategory(id: Long) {
        db.from("categories").delete { filter { eq("id", id) } }
        refresh()
    }

    suspend fun restoreCategory(id: Long) {
        db.from("categories").update({ set("is_active", true) }) {
            filter { eq("id", id) }
        }
        refresh()
    }

    /** Échange le displayOrder de deux catégories (renumérote si doublons). */
    suspend fun swapDisplayOrder(idA: Long, idB: Long) {
        val all = db.from("categories").select {
            order("display_order", Order.ASCENDING)
        }.decodeList<CategoryDto>()

        val orders = all.mapNotNull { it.displayOrder }.toSet()
        if (orders.size < all.size) {
            all.forEachIndexed { idx, dto ->
                db.from("categories").update({ set("display_order", idx) }) {
                    filter { eq("id", dto.id ?: return@forEachIndexed) }
                }
            }
        }
        val orderA = all.find { it.id == idA }?.displayOrder ?: return
        val orderB = all.find { it.id == idB }?.displayOrder ?: return
        db.from("categories").update({ set("display_order", orderB) }) { filter { eq("id", idA) } }
        db.from("categories").update({ set("display_order", orderA) }) { filter { eq("id", idB) } }
        refresh()
    }

    /** Crée les catégories par défaut si l'utilisateur n'en a aucune. */
    suspend fun createDefaultCategories() {
        val existing = db.from("categories").select().decodeList<CategoryDto>()
        if (existing.isNotEmpty()) return

        val expense = listOf(
            Triple("Alimentation", "#4CAF50", "restaurant"),
            Triple("Transport", "#2196F3", "directions_car"),
            Triple("Logement", "#FF9800", "home"),
            Triple("Loisirs", "#9C27B0", "sports_esports"),
            Triple("Santé", "#F44336", "local_hospital"),
            Triple("Shopping", "#E91E63", "shopping_bag"),
            Triple("Éducation", "#00BCD4", "school")
        )
        val income = listOf(
            Triple("Salaire", "#4CAF50", "account_balance"),
            Triple("Freelance", "#FF9800", "work"),
            Triple("Investissements", "#2196F3", "trending_up")
        )

        val dtos = expense.mapIndexed { i, (name, color, icon) ->
            CategoryDto(name = name, categoryType = TransactionType.EXPENSE.name, color = color, iconName = icon, isDefault = true, displayOrder = i)
        } + income.mapIndexed { i, (name, color, icon) ->
            CategoryDto(name = name, categoryType = TransactionType.INCOME.name, color = color, iconName = icon, isDefault = true, displayOrder = i + expense.size)
        }
        db.from("categories").insert(dtos)
        refresh()
    }

    // ===== Mappers =====

    private fun CategoryDto.toDomain() = Category(
        id = id ?: 0,
        name = name,
        categoryType = TransactionType.valueOf(categoryType),
        parentId = parentId,
        color = color,
        iconName = iconName,
        isDefault = isDefault,
        displayOrder = displayOrder,
        isActive = isActive,
        isEssential = isEssential
    )

    private fun Category.toInsertDto() = CategoryDto(
        name = name,
        categoryType = categoryType.name,
        parentId = parentId,
        color = color,
        iconName = iconName,
        isDefault = isDefault,
        displayOrder = displayOrder,
        isActive = isActive,
        isEssential = isEssential
    )
}
