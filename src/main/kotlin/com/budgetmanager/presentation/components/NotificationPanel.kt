package com.budgetmanager.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.budgetmanager.presentation.theme.*
import com.budgetmanager.util.AdviceLevel
import java.time.format.DateTimeFormatter

/**
 * Bell icon with badge. Click opens the notification dropdown.
 */
@Composable
fun NotificationBell(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val unread = NotificationCenter.unreadCount

    Box(
        modifier = modifier
            .size(40.dp)
            .clip(RoundedCornerShape(10.dp))
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Filled.Notifications,
            "Notifications",
            tint = NeumorphicTextSecondary,
            modifier = Modifier.size(22.dp)
        )
        if (unread > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(2.dp)
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(NeumorphicBudgetAlert),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (unread > 9) "9+" else unread.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * Notification dialog. Shows all notifications, allows dismissing them.
 */
@Composable
fun NotificationDialog(
    onDismiss: () -> Unit
) {
    val timeFmt = remember { DateTimeFormatter.ofPattern("HH:mm") }

    LaunchedEffect(Unit) {
        NotificationCenter.markAllSeen()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Notifications, null, tint = NeumorphicPrimary, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(12.dp))
                Text("Notifications", style = MaterialTheme.typography.headlineMedium, color = NeumorphicTextPrimary)
                Spacer(Modifier.weight(1f))
                if (NotificationCenter.notifications.isNotEmpty()) {
                    Text(
                        "Tout effacer",
                        style = MaterialTheme.typography.labelMedium,
                        color = ExpenseColor,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .pointerHoverIcon(PointerIcon.Hand)
                            .clickable { NotificationCenter.clear() }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        },
        text = {
            Box(modifier = Modifier.fillMaxWidth().heightIn(min = 200.dp, max = 500.dp)) {
                if (NotificationCenter.notifications.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Filled.NotificationsNone, null, tint = NeumorphicTextTertiary, modifier = Modifier.size(48.dp))
                            Spacer(Modifier.height(12.dp))
                            Text("Aucune notification", color = NeumorphicTextSecondary, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(
                            NotificationCenter.notifications,
                            // Defensive: combine id + index to guarantee uniqueness even with stale data
                            key = { "${it.id}-${it.title.hashCode()}" }
                        ) { notif ->
                            val color = when (notif.level) {
                                AdviceLevel.CRITICAL -> NeumorphicBudgetAlert
                                AdviceLevel.WARNING -> NeumorphicBudgetWarning
                                AdviceLevel.GOOD -> NeumorphicBudgetSafe
                                AdviceLevel.INFO -> NeumorphicPrimary
                            }
                            val icon = when (notif.level) {
                                AdviceLevel.CRITICAL -> Icons.Filled.Warning
                                AdviceLevel.WARNING -> Icons.Filled.ErrorOutline
                                AdviceLevel.GOOD -> Icons.Filled.CheckCircle
                                AdviceLevel.INFO -> Icons.Filled.Info
                            }
                            NeumorphicCard(modifier = Modifier.fillMaxWidth(), elevation = 4.dp) {
                                Row(verticalAlignment = Alignment.Top) {
                                    Icon(icon, null, tint = color, modifier = Modifier.size(22.dp))
                                    Spacer(Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            notif.title,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = color,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            notif.message,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = NeumorphicTextSecondary
                                        )
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            notif.time.format(timeFmt),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = NeumorphicTextTertiary
                                        )
                                    }
                                    IconButton(
                                        onClick = { NotificationCenter.dismiss(notif.id) },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(Icons.Filled.Close, "Fermer", tint = NeumorphicTextTertiary, modifier = Modifier.size(14.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            NeumorphicButton(text = "Fermer", onClick = onDismiss, isPrimary = false)
        },
        containerColor = NeumorphicElevated,
        shape = RoundedCornerShape(16.dp)
    )
}
