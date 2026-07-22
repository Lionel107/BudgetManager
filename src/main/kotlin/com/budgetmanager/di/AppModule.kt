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
    // Database
    single {
        DatabaseManager().apply { init() }
    }

    // Preferences
    single { AppPreferences() }

    // Repositories
    single { AccountRepository() }
    single { CategoryRepository() }
    single { TransactionRepository(get(), get()) }
    single { BudgetRepository() }
    single { RecurringTransactionRepository(get(), get()) }
    single { com.budgetmanager.data.repository.TemplateRepository() }
    single { com.budgetmanager.data.repository.TagRepository() }
    single { com.budgetmanager.data.repository.SplitRepository() }
    single { com.budgetmanager.data.repository.ChallengeRepository() }
    single { com.budgetmanager.data.repository.ExchangeRateRepository() }
}
