package com.budgetmanager.data.repository

import com.budgetmanager.data.remote.SupabaseClientProvider
import com.budgetmanager.data.remote.dto.UserProfileDto
import com.budgetmanager.domain.model.UserProfile
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * Profil utilisateur persistant (une ligne par utilisateur, RLS propriétaire).
 * Sert de mémoire à l'agent Constructeur. Absent tant que l'accueil guidé n'a pas eu lieu.
 */
class UserProfileRepository(private val provider: SupabaseClientProvider) {

    private val db get() = provider.client
    private val _refreshTrigger = MutableStateFlow(0L)

    fun refresh() {
        _refreshTrigger.value = System.currentTimeMillis()
    }

    /** Flux du profil (valeurs par défaut vides tant qu'aucune ligne n'existe). */
    fun profileFlow(): Flow<UserProfile> = _refreshTrigger.map { get() }

    /** Lecture ponctuelle. Renvoie un profil vide si l'utilisateur n'en a pas encore. */
    suspend fun get(): UserProfile {
        val dto = db.from("user_profiles").select().decodeList<UserProfileDto>().firstOrNull()
        return dto?.toDomain() ?: UserProfile()
    }

    /** Crée ou met à jour le profil de l'utilisateur (upsert sur user_id). */
    suspend fun save(profile: UserProfile) {
        db.from("user_profiles").upsert(profile.toDto(), onConflict = "user_id")
        refresh()
    }

    /** Marque l'accueil guidé comme terminé, sans toucher au reste. */
    suspend fun markOnboardingDone() {
        val current = get()
        save(current.copy(onboardingDone = true))
    }

    private fun UserProfileDto.toDomain() = UserProfile(
        monthlyIncome = monthlyIncome,
        priorities = priorities.orEmpty(),
        projects = projects.orEmpty(),
        neverCut = neverCut.orEmpty(),
        comfort = comfort.orEmpty(),
        notes = notes.orEmpty(),
        onboardingDone = onboardingDone
    )

    private fun UserProfile.toDto() = UserProfileDto(
        monthlyIncome = monthlyIncome,
        priorities = priorities.ifBlank { null },
        projects = projects.ifBlank { null },
        neverCut = neverCut.ifBlank { null },
        comfort = comfort.ifBlank { null },
        notes = notes.ifBlank { null },
        onboardingDone = onboardingDone
    )
}
