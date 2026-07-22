package com.budgetmanager.data.repository

import com.budgetmanager.data.database.Categories
import com.budgetmanager.data.database.Templates
import com.budgetmanager.domain.model.Template
import com.budgetmanager.domain.model.TransactionType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction

class TemplateRepository {

    private val _refreshTrigger = MutableStateFlow(0L)

    fun refresh() {
        _refreshTrigger.value = System.currentTimeMillis()
    }

    private fun baseQuery(): ColumnSet {
        return Templates.join(Categories, JoinType.LEFT, Templates.categoryId, Categories.id)
    }

    fun getAll(): Flow<List<Template>> {
        return _refreshTrigger.map {
            withContext(Dispatchers.IO) {
                transaction {
                    baseQuery().selectAll()
                        .orderBy(Templates.usageCount, SortOrder.DESC)
                        .map { it.toTemplate() }
                }
            }
        }
    }

    fun getByType(type: TransactionType): Flow<List<Template>> {
        return _refreshTrigger.map {
            withContext(Dispatchers.IO) {
                transaction {
                    baseQuery().selectAll()
                        .where { Templates.transactionType eq type.name }
                        .orderBy(Templates.usageCount, SortOrder.DESC)
                        .map { it.toTemplate() }
                }
            }
        }
    }

    suspend fun getById(id: Long): Template? = withContext(Dispatchers.IO) {
        transaction {
            baseQuery().selectAll()
                .where { Templates.id eq id }
                .map { it.toTemplate() }
                .singleOrNull()
        }
    }

    suspend fun create(template: Template): Long = withContext(Dispatchers.IO) {
        transaction {
            Templates.insert {
                it[name] = template.name
                it[defaultAmount] = template.defaultAmount
                it[categoryId] = template.categoryId
                it[transactionType] = template.transactionType.name
                it[iconName] = template.iconName
                it[color] = template.color
                it[displayOrder] = template.displayOrder
                it[usageCount] = template.usageCount
            } get Templates.id
        }.also { refresh() }
    }

    suspend fun update(template: Template) = withContext(Dispatchers.IO) {
        transaction {
            Templates.update({ Templates.id eq template.id }) {
                it[name] = template.name
                it[defaultAmount] = template.defaultAmount
                it[categoryId] = template.categoryId
                it[transactionType] = template.transactionType.name
                it[iconName] = template.iconName
                it[color] = template.color
                it[displayOrder] = template.displayOrder
            }
        }
        refresh()
    }

    suspend fun delete(id: Long) = withContext(Dispatchers.IO) {
        transaction {
            Templates.deleteWhere { Templates.id eq id }
        }
        refresh()
    }

    /** Increment usage count atomically — used for sorting "most used first". */
    suspend fun incrementUsage(id: Long) = withContext(Dispatchers.IO) {
        transaction {
            exec(
                "UPDATE templates SET usage_count = usage_count + 1 WHERE id = ?",
                listOf(LongColumnType() to id)
            )
        }
        refresh()
    }

    private fun ResultRow.toTemplate(): Template {
        return Template(
            id = this[Templates.id],
            name = this[Templates.name],
            defaultAmount = this[Templates.defaultAmount],
            categoryId = this[Templates.categoryId],
            categoryName = this.getOrNull(Categories.name),
            transactionType = TransactionType.valueOf(this[Templates.transactionType]),
            iconName = this[Templates.iconName],
            color = this[Templates.color],
            displayOrder = this[Templates.displayOrder],
            usageCount = this[Templates.usageCount]
        )
    }
}
