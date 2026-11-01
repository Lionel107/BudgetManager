package com.budgetmanager.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.budgetmanager.presentation.theme.*
import com.budgetmanager.util.UpdateInfo
import com.budgetmanager.util.UpdateService
import kotlinx.coroutines.launch

/**
 * Bannière « mise à jour disponible » : vérifie GitHub Releases au démarrage, puis
 * propose d'installer en un clic (téléchargement → installation → relance).
 */
@Composable
fun UpdateBanner() {
    val scope = rememberCoroutineScope()
    var update by remember { mutableStateOf<UpdateInfo?>(null) }
    var dismissed by remember { mutableStateOf(false) }
    var showNotes by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf<Float?>(null) } // non null = téléchargement en cours
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { update = UpdateService.check() }

    val info = update
    AnimatedVisibility(visible = info != null && !dismissed) {
        if (info == null) return@AnimatedVisibility
        NeumorphicCard(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.SystemUpdateAlt, null, tint = NeumorphicPrimary, modifier = Modifier.size(26.dp))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Mise à jour ${info.version} disponible 🎉",
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold
                    )
                    val sub = when {
                        progress != null && progress!! >= 1f -> "Installation… l'app va se fermer puis se relancer."
                        progress != null -> "Téléchargement… ${(progress!! * 100).toInt()} %"
                        else -> "Tu as la ${UpdateService.currentVersion}. Tes données (dans le cloud) ne sont pas touchées."
                    }
                    Text(sub, style = MaterialTheme.typography.bodySmall, color = NeumorphicTextSecondary)
                    if (info.notes.isNotBlank() && progress == null) {
                        Text(
                            if (showNotes) "Masquer les nouveautés" else "Voir les nouveautés",
                            style = MaterialTheme.typography.bodySmall, color = NeumorphicPrimary,
                            modifier = Modifier.padding(top = 4.dp).clickable { showNotes = !showNotes }
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                if (progress == null) {
                    NeumorphicButton(text = "Plus tard", isPrimary = false, onClick = { dismissed = true })
                    Spacer(Modifier.width(8.dp))
                    NeumorphicButton(
                        text = "Mettre à jour",
                        icon = Icons.Filled.SystemUpdateAlt,
                        onClick = {
                            error = null
                            progress = 0f
                            scope.launch {
                                runCatching { UpdateService.downloadAndInstall(info) { progress = it } }
                                    .onFailure { error = it.message ?: "Échec de la mise à jour."; progress = null }
                            }
                        }
                    )
                } else {
                    CircularProgressIndicator(
                        progress = progress!!,
                        color = NeumorphicPrimary,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
            if (showNotes && progress == null) {
                Spacer(Modifier.height(10.dp))
                Text(
                    info.notes.replace("**", "").replace("`", ""),
                    style = MaterialTheme.typography.bodySmall, color = NeumorphicTextSecondary
                )
            }
            error?.let {
                Spacer(Modifier.height(8.dp))
                Text("⚠️ $it", style = MaterialTheme.typography.bodySmall, color = NeumorphicBudgetAlert)
            }
        }
    }
}
