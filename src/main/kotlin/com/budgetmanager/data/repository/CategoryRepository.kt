package com.budgetmanager.data.repository

import com.budgetmanager.data.database.Categories
import com.budgetmanager.domain.model.Category
import com.budgetmanager.domain.model.TransactionType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction

class CategoryRepository {

    private val _refreshTrigger = MutableStateFlow(0L)

    fun refresh() {
        _refreshTrigger.value = System.currentTimeMillis()
    }

    /** Returns only active (non-soft-deleted) categories. Use getAllIncludingInactive() to see deleted ones. */
    fun getAllCategories(): Flow<List<Category>> {
        return _refreshTrigger.map {
            withContext(Dispatchers.IO) {
                transaction {
                    Categories.selectAll()
                        .where { Categories.isActive eq true }
                        .orderBy(Categories.displayOrder)
                        .map { it.toCategory() }
                }
            }
        }
    }

    fun getAllCategoriesIncludingInactive(): Flow<List<Category>> {
        return _refreshTrigger.map {
            withContext(Dispatchers.IO) {
                transaction {
                    Categories.selectAll()
                        .orderBy(Categories.displayOrder)
                        .map { it.toCategory() }
                }
            }
        }
    }

    fun getExpenseCategories(): Flow<List<Category>> {
        return _refreshTrigger.map {
            withContext(Dispatchers.IO) {
                transaction {
                    Categories.selectAll()
                        .where {
                            (Categories.categoryType eq TransactionType.EXPENSE.name) and
                            (Categories.isActive eq true)
                        }
                        .orderBy(Categories.displayOrder)
                        .map { it.toCategory() }
                }
            }
        }
    }

    fun getIncomeCategories(): Flow<List<Category>> {
        return _refreshTrigger.map {
            withContext(Dispatchers.IO) {
                transaction {
                    Categories.selectAll()
                        .where {
                            (Categories.categoryType eq TransactionType.INCOME.name) and
                            (Categories.isActive eq true)
                        }
                        .orderBy(Categories.displayOrder)
                        .map { it.toCategory() }
                }
            }
        }
    }

    suspend fun getCategoryById(id: Long): Category? = withContext(Dispatchers.IO) {
        transaction {
            Categories.selectAll()
                .where { Categories.id eq id }
                .map { it.toCategory() }
                .singleOrNull()
        }
    }

    suspend fun createCategory(category: Category): Long = withContext(Dispatchers.IO) {
        transaction {
            Categories.insert {
                it[name] = category.name
                it[categoryType] = category.categoryType.name
                it[parentId] = category.parentId
                it[color] = category.color
                it[iconName] = category.iconName
                it[isDefault] = category.isDefault
                it[displayOrder] = category.displayOrder
            } get Categories.id
        }.also { refresh() }
    }

    suspend fun updateCategory(category: Category) = withContext(Dispatchers.IO) {
        transaction {
            Categories.update({ Categories.id eq category.id }) {
                it[name] = category.name
                it[categoryType] = category.categoryType.name
                it[parentId] = category.parentId
                it[color] = category.color
                it[iconName] = category.iconName
                it[isDefault] = category.isDefault
                it[displayOrder] = category.displayOrder
            }
        }
        refresh()
    }

    /** Soft-delete: marks category inactive. Linked transactions keep referencing it. */
    suspend fun deleteCategory(id: Long) = withContext(Dispatchers.IO) {
        transaction {
            Categories.update({ Categories.id eq id }) {
                it[isActive] = false
            }
        }
        refresh()
    }

    /** Hard-delete reserved for cleanup. */
    suspend fun hardDeleteCategory(id: Long) = withContext(Dispatchers.IO) {
        transaction {
            Categories.deleteWhere { Categories.id eq id }
        }
        refresh()
    }

    suspend fun restoreCategory(id: Long) = withContext(Dispatchers.IO) {
        transaction {
            Categories.update({ Categories.id eq id }) {
                it[isActive] = true
            }
        }
        refresh()
    }

    /**
     * Swap displayOrder between two categories. Normalizes first to handle
     * legacy data with duplicate orders.
     */
    suspend fun swapDisplayOrder(idA: Long, idB: Long) = withContext(Dispatchers.IO) {
        transaction {
            val all = Categories.selectAll()
                .orderBy(Categories.displayOrder, SortOrder.ASC)
                .orderBy(Categories.id, SortOrder.ASC)
                .map { it[Categories.id] to it[Categories.displayOrder] }

            val orders = all.map { it.second }.toSet()
            if (orders.size < all.size) {
                all.forEachIndexed { idx, (id, _) ->
                    Categories.update({ Categories.id eq id }) { it[displayOrder] = idx }
                }
            }

            val orderA = Categories.selectAll().where { Categories.id eq idA }
                .map { it[Categories.displayOrder] }.singleOrNull() ?: return@transaction
            val orderB = Categories.selectAll().where { Categories.id eq idB }
                .map { it[Categories.displayOrder] }.singleOrNull() ?: return@transaction
            Categories.update({ Categories.id eq idA }) { it[displayOrder] = orderB }
            Categories.update({ Categories.id eq idB }) { it[displayOrder] = orderA }
        }
        refresh()
    }

    suspend fun createDefaultCategories() = withContext(Dispatchers.IO) {
        transaction {
            val existingCount = Categories.selectAll().count()
            if (existingCount > 0) return@transaction

            val expenseCategories = listOf(
                Triple("Alimentation", "#4CAF50", "restaurant"),
                Triple("Transport", "#2196F3", "directions_car"),
                Triple("Logement", "#FF9800", "home"),
                Triple("Loisirs", "#9C27B0", "sports_esports"),
                Triple("Santé", "#F44336", "local_hospital"),
                Triple("Shopping", "#E91E63", "shopping_bag"),
                Triple("Éducation", "#00BCD4", "school")
            )

            val incomeCategories = listOf(
                Triple("Salaire", "#4CAF50", "account_balance"),
                Triple("Freelance", "#FF9800", "work"),
                Triple("Investissements", "#2196F3", "trending_up")
            )

            expenseCategories.forEachIndexed { index, (name, color, icon) ->
                Categories.insert {
                    it[Categories.name] = name
                    it[categoryType] = TransactionType.EXPENSE.name
                    it[Categories.color] = color
                    it[iconName] = icon
                    it[isDefault] = true
                    it[displayOrder] = index
                }
            }

            incomeCategories.forEachIndexed { index, (name, color, icon) ->
                Categories.insert {
                    it[Categories.name] = name
                    it[categoryType] = TransactionType.INCOME.name
                    it[Categories.color] = color
                    it[iconName] = icon
                    it[isDefault] = true
                    it[displayOrder] = index + expenseCategories.size
                }
            }
        }
        refresh()
    }

    private fun ResultRow.toCategory(): Category {
        return Category(
            id = this[Categories.id],
            name = this[Categories.name],
            categoryType = TransactionType.valueOf(this[Categories.categoryType]),
            parentId = this[Categories.parentId],
            color = this[Categories.color],
            iconName = this[Categories.iconName],
            isDefault = this[Categories.isDefault],
            displayOrder = this[Categories.displayOrder],
            isActive = this[Categories.isActive]
        )
    }
}
