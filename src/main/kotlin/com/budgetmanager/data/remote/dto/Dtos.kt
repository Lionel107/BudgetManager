package com.budgetmanager.data.remote.dto

import com.budgetmanager.data.remote.BigDecimalSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.math.BigDecimal

/**
 * DTO Postgrest — reflètent 1:1 les colonnes des tables Supabase (snake_case).
 *
 * Conventions :
 *  - id / userId / created_at / updated_at sont nullable et à null par défaut :
 *    à l'INSERT ils sont omis (Json explicitNulls=false) → générés par Postgres.
 *  - Les montants passent par [BigDecimalSerializer] (nombre JSON exact).
 *  - Les dates sont des String ISO (converties en Local(Date)Time dans les mappers).
 *  - Les champs "embed_*" reçoivent les jointures Postgrest (ex. category:categories(name,color)).
 */

@Serializable
data class CategoryRef(
    val name: String? = null,
    val color: String? = null
)

@Serializable
data class AccountRef(
    val name: String? = null
)

@Serializable
data class TagNameRef(val name: String? = null)

/** Ligne de jointure transaction_tags avec le tag embarqué (alias `tag:tags(name)`). */
@Serializable
data class TagLink(@SerialName("tag") val tag: TagNameRef? = null)

@Serializable
data class AccountDto(
    val id: Long? = null,
    @SerialName("user_id") val userId: String? = null,
    val name: String,
    @Serializable(with = BigDecimalSerializer::class) val balance: BigDecimal = BigDecimal.ZERO,
    @SerialName("account_type") val accountType: String,
    @SerialName("currency_code") val currencyCode: String = "EUR",
    @SerialName("is_active") val isActive: Boolean = true,
    @SerialName("display_order") val displayOrder: Int = 0,
    val color: String? = null,
    @SerialName("icon_name") val iconName: String? = null,
    @SerialName("initial_capital") @Serializable(with = BigDecimalSerializer::class) val initialCapital: BigDecimal? = null,
    @SerialName("tax_rate") val taxRate: Float = 0.30f,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class CategoryDto(
    val id: Long? = null,
    @SerialName("user_id") val userId: String? = null,
    val name: String,
    @SerialName("category_type") val categoryType: String,
    @SerialName("parent_id") val parentId: Long? = null,
    val color: String,
    @SerialName("icon_name") val iconName: String? = null,
    @SerialName("is_default") val isDefault: Boolean = false,
    @SerialName("display_order") val displayOrder: Int = 0,
    @SerialName("is_active") val isActive: Boolean = true,
    @SerialName("is_essential") val isEssential: Boolean = true
)

@Serializable
data class ObjectiveDto(
    val id: Long? = null,
    @SerialName("user_id") val userId: String? = null,
    val title: String,
    val type: String,
    @SerialName("target_amount") @Serializable(with = BigDecimalSerializer::class) val targetAmount: BigDecimal,
    @SerialName("target_date") val targetDate: String? = null,
    @SerialName("category_id") val categoryId: Long? = null,
    @SerialName("is_active") val isActive: Boolean = true,
    @SerialName("category") val category: CategoryRef? = null
)

@Serializable
data class TransactionDto(
    val id: Long? = null,
    @SerialName("user_id") val userId: String? = null,
    @SerialName("account_id") val accountId: Long,
    @SerialName("category_id") val categoryId: Long? = null,
    val title: String,
    @Serializable(with = BigDecimalSerializer::class) val amount: BigDecimal,
    @SerialName("transaction_type") val transactionType: String,
    val date: String,
    val notes: String? = null,
    @SerialName("is_recurring") val isRecurring: Boolean = false,
    @SerialName("recurring_transaction_id") val recurringTransactionId: Long? = null,
    @SerialName("created_at") val createdAt: String? = null,
    // Embeds (jointures optionnelles)
    @SerialName("category") val category: CategoryRef? = null,
    @SerialName("account") val account: AccountRef? = null,
    @SerialName("tag_links") val tagLinks: List<TagLink>? = null
)

@Serializable
data class RecurringTransactionDto(
    val id: Long? = null,
    @SerialName("user_id") val userId: String? = null,
    val title: String,
    @Serializable(with = BigDecimalSerializer::class) val amount: BigDecimal,
    @SerialName("category_id") val categoryId: Long? = null,
    @SerialName("account_id") val accountId: Long,
    @SerialName("frequency_type") val frequencyType: String,
    @SerialName("repeat_interval") val repeatInterval: Int = 1,
    @SerialName("start_date") val startDate: String,
    @SerialName("end_date") val endDate: String? = null,
    @SerialName("last_generated_date") val lastGeneratedDate: String? = null,
    @SerialName("next_due_date") val nextDueDate: String,
    @SerialName("transaction_type") val transactionType: String,
    @SerialName("is_active") val isActive: Boolean = true,
    val notes: String? = null,
    @SerialName("destination_account_id") val destinationAccountId: Long? = null,
    @SerialName("category") val category: CategoryRef? = null,
    @SerialName("account") val account: AccountRef? = null,
    @SerialName("destination") val destination: AccountRef? = null
)

@Serializable
data class TemplateDto(
    val id: Long? = null,
    @SerialName("user_id") val userId: String? = null,
    val name: String,
    @SerialName("default_amount") @Serializable(with = BigDecimalSerializer::class) val defaultAmount: BigDecimal? = null,
    @SerialName("category_id") val categoryId: Long? = null,
    @SerialName("transaction_type") val transactionType: String,
    @SerialName("icon_name") val iconName: String? = null,
    val color: String? = null,
    @SerialName("display_order") val displayOrder: Int = 0,
    @SerialName("usage_count") val usageCount: Int = 0,
    @SerialName("category") val category: CategoryRef? = null
)

@Serializable
data class TagDto(
    val id: Long? = null,
    @SerialName("user_id") val userId: String? = null,
    val name: String,
    val color: String? = null,
    @SerialName("usage_count") val usageCount: Int = 0,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class TransactionTagDto(
    @SerialName("user_id") val userId: String? = null,
    @SerialName("transaction_id") val transactionId: Long,
    @SerialName("tag_id") val tagId: Long
)

@Serializable
data class ExchangeRateDto(
    val id: Long? = null,
    @SerialName("user_id") val userId: String? = null,
    @SerialName("from_currency") val fromCurrency: String,
    @SerialName("to_currency") val toCurrency: String,
    @Serializable(with = BigDecimalSerializer::class) val rate: BigDecimal,
    @SerialName("updated_at") val updatedAt: String? = null
)

@Serializable
data class ChallengeDto(
    val id: Long? = null,
    @SerialName("user_id") val userId: String? = null,
    val title: String,
    val description: String? = null,
    val type: String,
    @SerialName("target_amount") @Serializable(with = BigDecimalSerializer::class) val targetAmount: BigDecimal,
    @SerialName("category_id") val categoryId: Long? = null,
    @SerialName("start_date") val startDate: String,
    @SerialName("end_date") val endDate: String,
    @SerialName("is_completed") val isCompleted: Boolean = false,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("category") val category: CategoryRef? = null
)

@Serializable
data class TransactionSplitDto(
    val id: Long? = null,
    @SerialName("user_id") val userId: String? = null,
    @SerialName("transaction_id") val transactionId: Long,
    @SerialName("category_id") val categoryId: Long? = null,
    @Serializable(with = BigDecimalSerializer::class) val amount: BigDecimal,
    val notes: String? = null,
    @SerialName("category") val category: CategoryRef? = null
)

@Serializable
data class BudgetDto(
    val id: Long? = null,
    @SerialName("user_id") val userId: String? = null,
    @SerialName("category_id") val categoryId: Long,
    @SerialName("period_type") val periodType: String,
    @SerialName("budget_limit") @Serializable(with = BigDecimalSerializer::class) val budgetLimit: BigDecimal,
    @SerialName("alert_threshold") val alertThreshold: Float = 0.9f,
    @SerialName("warning_threshold") val warningThreshold: Float = 0.7f,
    @SerialName("start_date") val startDate: String? = null,
    @SerialName("end_date") val endDate: String? = null,
    @SerialName("category") val category: CategoryRef? = null
)

@Serializable
data class UserProfileDto(
    @SerialName("user_id") val userId: String? = null,
    @SerialName("monthly_income") @Serializable(with = BigDecimalSerializer::class) val monthlyIncome: BigDecimal? = null,
    val priorities: String? = null,
    val projects: String? = null,
    @SerialName("never_cut") val neverCut: String? = null,
    val comfort: String? = null,
    val notes: String? = null,
    @SerialName("onboarding_done") val onboardingDone: Boolean = false
)
