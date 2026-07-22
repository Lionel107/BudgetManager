package com.budgetmanager.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.budgetmanager.presentation.theme.NeumorphicElevated
import com.budgetmanager.presentation.theme.NeumorphicPrimary
import com.budgetmanager.presentation.theme.NeumorphicTextPrimary
import com.budgetmanager.presentation.theme.NeumorphicTextSecondary
import com.budgetmanager.presentation.theme.neumorphicShadow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class UndoableAction(
    val message: String,
    val onUndo: suspend () -> Unit,
    val id: Long = System.currentTimeMillis(),
    val durationMs: Long = 5000
)

object UndoBus {
    var current by mutableStateOf<UndoableAction?>(null)
        private set

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var dismissJob: Job? = null

    fun show(action: UndoableAction) {
        dismissJob?.cancel()
        current = action
        dismissJob = scope.launch {
            delay(action.durationMs)
            if (current?.id == action.id) current = null
        }
    }

    fun undo() {
        val action = current ?: return
        dismissJob?.cancel()
        current = null
        scope.launch { runCatching { action.onUndo() } }
    }

    fun dismiss() {
        dismissJob?.cancel()
        current = null
    }
}

@Composable
fun UndoSnackbarOverlay() {
    val action = UndoBus.current

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomStart) {
        AnimatedVisibility(
            visible = action != null,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = spring()
            ) + fadeIn(),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = tween(200)
            ) + fadeOut(tween(200))
        ) {
            if (action != null) {
                Row(
                    modifier = Modifier
                        .padding(start = 96.dp, bottom = 24.dp)
                        .neumorphicShadow(
                            elevation = 10.dp,
                            borderRadius = 14.dp,
                            backgroundColor = NeumorphicElevated
                        )
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = action.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = NeumorphicTextPrimary,
                        fontWeight = FontWeight.Medium
                    )

                    Spacer(Modifier.width(20.dp))

                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .pointerHoverIcon(PointerIcon.Hand)
                            .clickable { UndoBus.undo() }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.Undo, null,
                            tint = NeumorphicPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Annuler",
                            style = MaterialTheme.typography.labelLarge,
                            color = NeumorphicPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Spacer(Modifier.width(4.dp))

                    Icon(
                        Icons.Filled.Close, "Fermer",
                        tint = NeumorphicTextSecondary,
                        modifier = Modifier
                            .size(18.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .pointerHoverIcon(PointerIcon.Hand)
                            .clickable { UndoBus.dismiss() }
                    )
                }
            }
        }
    }
}
