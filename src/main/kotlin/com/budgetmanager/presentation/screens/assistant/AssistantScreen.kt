package com.budgetmanager.presentation.screens.assistant

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.budgetmanager.data.repository.UserProfileRepository
import com.budgetmanager.domain.model.UserProfile
import com.budgetmanager.presentation.components.NeumorphicButton
import com.budgetmanager.presentation.components.NeumorphicCard
import com.budgetmanager.presentation.components.NeumorphicTextField
import com.budgetmanager.presentation.components.SectionHeader
import com.budgetmanager.presentation.screens.advisor.BudgetPlannerSection
import com.budgetmanager.presentation.theme.*
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext.get as getKoin
import java.math.BigDecimal

/**
 * Écran de l'agent **Constructeur**. Pour l'instant : le PROFIL persistant éditable
 * (mémoire de l'assistant) + l'accueil guidé au premier passage. Le plan de budget
 * conversationnel viendra s'ajouter ici.
 */
@Composable
fun AssistantScreen() {
    val repo = remember { getKoin().get<UserProfileRepository>() }
    val scope = rememberCoroutineScope()

    var loading by remember { mutableStateOf(true) }
    var profile by remember { mutableStateOf(UserProfile()) }

    // Champs éditables
    var income by remember { mutableStateOf("") }
    var priorities by remember { mutableStateOf("") }
    var projects by remember { mutableStateOf("") }
    var neverCut by remember { mutableStateOf("") }
    var comfort by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    var saving by remember { mutableStateOf(false) }
    var savedAt by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        loading = true
        val p = runCatching { repo.get() }.getOrElse {
            error = it.message ?: "Chargement impossible."; loading = false; return@LaunchedEffect
        }
        profile = p
        income = p.monthlyIncome?.toPlainString() ?: ""
        priorities = p.priorities
        projects = p.projects
        neverCut = p.neverCut
        comfort = p.comfort
        notes = p.notes
        loading = false
    }

    fun save() {
        if (saving) return
        saving = true; savedAt = false; error = null
        scope.launch {
            val updated = UserProfile(
                monthlyIncome = income.trim().replace(",", ".").toBigDecimalOrNull()
                    ?.takeIf { it > BigDecimal.ZERO },
                priorities = priorities.trim(),
                projects = projects.trim(),
                neverCut = neverCut.trim(),
                comfort = comfort.trim(),
                notes = notes.trim(),
                onboardingDone = true
            )
            val res = runCatching { repo.save(updated) }
            saving = false
            res.onSuccess { profile = updated; savedAt = true }
                .onFailure { error = it.message ?: "Échec de l'enregistrement." }
        }
    }

    val firstTime = !profile.onboardingDone

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        SectionHeader(title = "Assistant")
        Spacer(Modifier.height(4.dp))
        Text(
            if (firstTime)
                "Bienvenue ! Pour construire le meilleur budget POUR TOI, l'assistant a besoin de te connaître un peu. Réponds à ce que tu veux — tu pourras tout modifier plus tard."
            else
                "Ton profil : la mémoire de l'assistant. Plus il est précis, plus le budget proposé te ressemblera.",
            style = MaterialTheme.typography.bodyMedium, color = NeumorphicTextSecondary
        )
        Spacer(Modifier.height(16.dp))

        if (loading) {
            Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = NeumorphicPrimary)
            }
        } else {
        NeumorphicCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                if (firstTime) "Faisons connaissance" else "Ton profil",
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(14.dp))

            NeumorphicTextField(
                value = income, onValueChange = { income = it; savedAt = false },
                label = "Revenu mensuel net (€)", placeholder = "Ex. 2200",
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(14.dp))
            ProfileField(
                label = "Ce qui compte le plus pour toi",
                placeholder = "Ex. mettre de l'argent de côté, voyager une fois par an, ne pas stresser sur les courses",
                value = priorities, onValueChange = { priorities = it; savedAt = false }
            )
            Spacer(Modifier.height(14.dp))
            ProfileField(
                label = "Tes projets à venir",
                placeholder = "Ex. acheter une voiture d'ici 2 ans, un mariage, des vacances cet été",
                value = projects, onValueChange = { projects = it; savedAt = false }
            )
            Spacer(Modifier.height(14.dp))
            ProfileField(
                label = "Ce que tu ne veux jamais réduire",
                placeholder = "Ex. le sport, les sorties avec les amis, la qualité de la nourriture",
                value = neverCut, onValueChange = { neverCut = it; savedAt = false }
            )
            Spacer(Modifier.height(14.dp))
            ProfileField(
                label = "Ton niveau de confort souhaité",
                placeholder = "Ex. je veux vivre correctement sans me priver, mais épargner reste prioritaire",
                value = comfort, onValueChange = { comfort = it; savedAt = false }
            )
            Spacer(Modifier.height(14.dp))
            ProfileField(
                label = "Autre chose à savoir (optionnel)",
                placeholder = "Tout ce qui peut aider l'assistant à mieux te conseiller",
                value = notes, onValueChange = { notes = it; savedAt = false }
            )

            Spacer(Modifier.height(18.dp))
            NeumorphicButton(
                text = when {
                    saving -> "Enregistrement…"
                    firstTime -> "Enregistrer mon profil"
                    else -> "Mettre à jour"
                },
                icon = Icons.Filled.Check,
                enabled = !saving,
                onClick = { save() }
            )
            if (savedAt) {
                Spacer(Modifier.height(10.dp))
                Text("✅ Profil enregistré.", color = IncomeColor, style = MaterialTheme.typography.bodySmall)
            }
            error?.let {
                Spacer(Modifier.height(10.dp))
                Text(it, color = NeumorphicBudgetAlert, style = MaterialTheme.typography.bodySmall)
            }
        }

        Spacer(Modifier.height(14.dp))
        Text(
            "Ton budget sur mesure",
            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = NeumorphicTextPrimary
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "L'assistant part de ton profil ci-dessus, de tes habitudes réelles et de tes objectifs pour te proposer un budget. Modifie les montants, fais-lui une remarque, puis applique.",
            style = MaterialTheme.typography.bodySmall, color = NeumorphicTextTertiary
        )
        Spacer(Modifier.height(10.dp))
        BudgetPlannerSection()

        Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ProfileField(
    label: String,
    placeholder: String,
    value: String,
    onValueChange: (String) -> Unit
) {
    NeumorphicTextField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        placeholder = placeholder,
        modifier = Modifier.fillMaxWidth(),
        singleLine = false,
        minLines = 2,
        maxLines = 4
    )
}
