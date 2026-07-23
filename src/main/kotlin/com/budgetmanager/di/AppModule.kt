package com.budgetmanager.di

import com.budgetmanager.data.database.DatabaseManager
import com.budgetmanager.data.preferences.AppPreferences
import com.budgetmanager.data.repository.AccountRepository
import com.budgetmanager.data.repository.BudgetRepository
import com.budgetmanager.data.repository.CategoryRepository
import com.budgetmanager.data.repository.RecurringTransactionRepository
import com.budgetmanager.data.repository.TransactionRepository
import org.koin.dsl.module

val appModule = module {
    // Supabase (backend synchro) — Phase 2
    single { com.budgetmanager.data.remote.SupabaseClientProvider() }
    single { com.budgetmanager.data.remote.AuthRepository(get()) }
    single { com.budgetmanager.data.repository.AdvisorRepository(get()) }

    // Database
    single {
        DatabaseManager().apply { init() }
    }

    // Preferences
    single { AppPreferences() }

    // Repositories (backend Supabase)
    single { AccountRepository(get()) }
    single { CategoryRepository(get()) }
    single { TransactionRepository(get(), get(), get()) }
    single { BudgetRepository(get()) }
    single { RecurringTransactionRepository(get(), get(), get()) }
    single { com.budgetmanager.data.repository.TemplateRepository(get()) }
    single { com.budgetmanager.data.repository.TagRepository(get()) }
    single { com.budgetmanager.data.repository.SplitRepository(get()) }
    single { com.budgetmanager.data.repository.ChallengeRepository(get()) }
    single { com.budgetmanager.data.repository.ExchangeRateRepository(get()) }
}
