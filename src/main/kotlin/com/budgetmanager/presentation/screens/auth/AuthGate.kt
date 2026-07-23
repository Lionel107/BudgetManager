package com.budgetmanager.presentation.screens.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.budgetmanager.data.remote.AuthRepository
import com.budgetmanager.presentation.theme.NeumorphicBackground
import com.budgetmanager.presentation.theme.NeumorphicPrimary
import io.github.jan.supabase.gotrue.SessionStatus

/**
 * Aiguille l'UI selon l'état de session Supabase :
 *  - Authenticated        -> l'application ([authenticatedContent])
 *  - NotAuthenticated     -> l'écran de connexion
 *  - LoadingFromStorage / NetworkError -> écran de chargement
 */
@Composable
fun AuthGate(
    authRepo: AuthRepository,
    authenticatedContent: @Composable () -> Unit
) {
    val status by authRepo.sessionStatus.collectAsState()

    when (status) {
        is SessionStatus.Authenticated -> authenticatedContent()
        is SessionStatus.NotAuthenticated -> LoginScreen(authRepo)
        else -> Box(
            modifier = Modifier.fillMaxSize().background(NeumorphicBackground),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = NeumorphicPrimary)
        }
    }
}
