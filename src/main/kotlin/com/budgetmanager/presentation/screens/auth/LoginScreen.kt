package com.budgetmanager.presentation.screens.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.budgetmanager.data.remote.AuthRepository
import com.budgetmanager.presentation.components.NeumorphicButton
import com.budgetmanager.presentation.components.NeumorphicCard
import com.budgetmanager.presentation.components.NeumorphicTextField
import com.budgetmanager.presentation.theme.NeumorphicBackground
import com.budgetmanager.presentation.theme.NeumorphicPrimary
import com.budgetmanager.presentation.theme.NeumorphicTextPrimary
import com.budgetmanager.presentation.theme.NeumorphicTextSecondary
import com.budgetmanager.presentation.theme.NeumorphicBudgetAlert
import kotlinx.coroutines.launch

/**
 * Écran de connexion / inscription (e-mail + mot de passe).
 * Affiché tant que l'utilisateur n'a pas de session Supabase valide.
 */
@androidx.compose.runtime.Composable
fun LoginScreen(authRepo: AuthRepository) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isSignUp by remember { mutableStateOf(false) }
    var showPassword by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()

    fun submit() {
        if (loading) return
        error = null; info = null
        if (email.isBlank() || password.isBlank()) {
            error = "Renseigne un e-mail et un mot de passe."
            return
        }
        if (isSignUp && password.length < 6) {
            error = "Le mot de passe doit faire au moins 6 caractères."
            return
        }
        loading = true
        scope.launch {
            try {
                if (isSignUp) {
                    authRepo.signUp(email, password)
                    // Si la confirmation e-mail est activée côté Supabase, pas de session immédiate.
                    info = "Compte créé. Si un e-mail de confirmation est demandé, valide-le puis connecte-toi."
                    isSignUp = false
                } else {
                    authRepo.signIn(email, password)
                    // En cas de succès, sessionStatus bascule et l'app remplace cet écran.
                }
            } catch (e: Exception) {
                error = friendlyError(e)
            } finally {
                loading = false
            }
        }
    }

    Box(
        modifier = Modifier.fillMaxSize().background(NeumorphicBackground),
        contentAlignment = Alignment.Center
    ) {
        NeumorphicCard(modifier = Modifier.widthIn(max = 420.dp).padding(24.dp)) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Budget Manager",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = NeumorphicPrimary
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = if (isSignUp) "Créer un compte" else "Connexion à ton compte",
                    style = MaterialTheme.typography.bodyMedium,
                    color = NeumorphicTextSecondary
                )
                Spacer(Modifier.height(24.dp))

                NeumorphicTextField(
                    value = email,
                    onValueChange = { email = it; error = null },
                    label = "E-mail",
                    placeholder = "toi@exemple.fr",
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(16.dp))

                NeumorphicTextField(
                    value = password,
                    onValueChange = { password = it; error = null },
                    label = "Mot de passe",
                    placeholder = "••••••",
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = if (showPassword) "Masquer le mot de passe" else "Afficher le mot de passe",
                    style = MaterialTheme.typography.labelMedium,
                    color = NeumorphicPrimary,
                    modifier = Modifier
                        .align(Alignment.End)
                        .clickable { showPassword = !showPassword }
                        .padding(4.dp)
                )

                if (error != null) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = error!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = NeumorphicBudgetAlert,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (info != null) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = info!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = NeumorphicTextPrimary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(Modifier.height(24.dp))
                if (loading) {
                    CircularProgressIndicator(color = NeumorphicPrimary)
                } else {
                    NeumorphicButton(
                        text = if (isSignUp) "Créer le compte" else "Se connecter",
                        onClick = { submit() },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(Modifier.height(16.dp))
                Text(
                    text = if (isSignUp) "Déjà un compte ? Se connecter"
                    else "Pas encore de compte ? En créer un",
                    style = MaterialTheme.typography.bodyMedium,
                    color = NeumorphicPrimary,
                    modifier = Modifier
                        .clickable { isSignUp = !isSignUp; error = null; info = null }
                        .padding(8.dp)
                )
            }
        }
    }
}

/** Traduit les erreurs Supabase courantes en messages lisibles. */
private fun friendlyError(e: Exception): String {
    val msg = e.message?.lowercase() ?: ""
    return when {
        "invalid login" in msg || "invalid credentials" in msg -> "E-mail ou mot de passe incorrect."
        "already registered" in msg || "already been registered" in msg || "user already" in msg ->
            "Un compte existe déjà avec cet e-mail."
        "email not confirmed" in msg -> "E-mail non confirmé : vérifie ta boîte mail."
        "network" in msg || "connect" in msg || "timeout" in msg ->
            "Problème de connexion réseau. Réessaie."
        "password" in msg && "least" in msg -> "Mot de passe trop court (6 caractères minimum)."
        else -> e.message ?: "Une erreur est survenue. Réessaie."
    }
}
