package com.budgetmanager.presentation.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.budgetmanager.presentation.theme.*
import com.budgetmanager.presentation.components.NotificationBell

data class SidebarItem(
    val screen: Screen,
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector
)

/**
 * Sidebar items grouped logically:
 *   1. Quotidien (usage frequent)     : Accueil, Transactions, Comptes
 *   2. Suivi & planification          : Budgets, Recurrents, Categories, Templates
 *   3. Analyse & motivation           : Analyse, Defis, Badges
 *   4. Configuration & donnees        : Taux de change, Importer, Exporter
 *
 * Note: separators are rendered by [Sidebar] when the screen group changes.
 */
private val mainNavItems = listOf(
    // --- Quotidien ---
    SidebarItem(Screen.HOME, "Accueil", Icons.Outlined.Home, Icons.Filled.Home),
    SidebarItem(Screen.TRANSACTIONS, "Transactions", Icons.Outlined.Receipt, Icons.Filled.Receipt),
    SidebarItem(Screen.ACCOUNTS, "Comptes", Icons.Outlined.AccountBalance, Icons.Filled.AccountBalance),
    // --- Suivi & planification ---
    SidebarItem(Screen.BUDGETS, "Budgets", Icons.Outlined.Wallet, Icons.Filled.Wallet),
    SidebarItem(Screen.OBJECTIVES, "Objectifs", Icons.Outlined.Flag, Icons.Filled.Flag),
    SidebarItem(Screen.RECURRING, "Récurrents", Icons.Outlined.Repeat, Icons.Filled.Repeat),
    SidebarItem(Screen.CATEGORIES, "Catégories", Icons.Outlined.Category, Icons.Filled.Category),
    SidebarItem(Screen.TEMPLATES, "Templates", Icons.Outlined.LibraryBooks, Icons.Filled.LibraryBooks),
    // --- Analyse & motivation ---
    SidebarItem(Screen.ANALYTICS, "Analyse", Icons.Outlined.Analytics, Icons.Filled.Analytics),
    SidebarItem(Screen.ADVISOR, "Conseiller IA", Icons.Outlined.AutoAwesome, Icons.Filled.AutoAwesome),
    SidebarItem(Screen.CHALLENGES, "Défis", Icons.Outlined.EmojiEvents, Icons.Filled.EmojiEvents),
    SidebarItem(Screen.BADGES, "Badges", Icons.Outlined.MilitaryTech, Icons.Filled.MilitaryTech),
    // --- Configuration & donnees ---
    SidebarItem(Screen.EXCHANGE_RATES, "Taux de change", Icons.Outlined.CurrencyExchange, Icons.Filled.CurrencyExchange),
    SidebarItem(Screen.IMPORT, "Importer", Icons.Outlined.FileUpload, Icons.Filled.FileUpload),
    SidebarItem(Screen.EXPORT, "Exporter", Icons.Outlined.FileDownload, Icons.Filled.FileDownload),
)

/** Indices of the FIRST item of each logical group → render a separator before them. */
private val groupBoundaries = setOf(3, 8, 12)

private val bottomNavItem = SidebarItem(Screen.SETTINGS, "Paramètres", Icons.Outlined.Settings, Icons.Filled.Settings)

@Composable
fun Sidebar(
    currentScreen: Screen,
    onNavigate: (Screen) -> Unit,
    onNotificationsClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(true) }
    val sidebarWidth by animateDpAsState(
        targetValue = if (isExpanded) 240.dp else 72.dp,
        animationSpec = tween(250)
    )

    Column(
        modifier = modifier
            .width(sidebarWidth)
            .fillMaxHeight()
            .neumorphicShadow(elevation = 6.dp, borderRadius = 0.dp, backgroundColor = NeumorphicBackground)
            .padding(vertical = 16.dp)
    ) {
        // App title / logo area + notification bell on the right when expanded
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Icon(
                    imageVector = Icons.Filled.AccountBalanceWallet,
                    contentDescription = null,
                    tint = NeumorphicPrimary,
                    modifier = Modifier.size(32.dp)
                )
                AnimatedVisibility(
                    visible = isExpanded,
                    enter = fadeIn(tween(200)),
                    exit = fadeOut(tween(150))
                ) {
                    Text(
                        text = "Budget Manager",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = NeumorphicTextPrimary,
                        modifier = Modifier.padding(start = 12.dp)
                    )
                }
            }
            // Notification bell, always visible (collapsed or expanded)
            NotificationBell(onClick = onNotificationsClick)
        }

        // Collapse toggle
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp)
        ) {
            val toggleInteraction = remember { MutableInteractionSource() }
            val toggleHovered by toggleInteraction.collectIsHoveredAsState()
            IconButton(
                onClick = { isExpanded = !isExpanded },
                modifier = Modifier
                    .size(36.dp)
                    .align(if (isExpanded) Alignment.CenterEnd else Alignment.Center)
                    .hoverable(toggleInteraction)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (toggleHovered) NeumorphicDepressed else Color.Transparent)
            ) {
                Icon(
                    imageVector = if (isExpanded) Icons.Filled.ChevronLeft else Icons.Filled.ChevronRight,
                    contentDescription = "Toggle sidebar",
                    tint = NeumorphicTextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // Main navigation items — scrollable when the screen is too small to fit all entries.
        // weight(1f) lets this section take all available vertical space and scroll inside.
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
            mainNavItems.forEachIndexed { index, item ->
                if (index in groupBoundaries) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        color = NeumorphicTextTertiary.copy(alpha = 0.18f)
                    )
                }
                SidebarNavItem(
                    item = item,
                    isSelected = currentScreen == item.screen,
                    isExpanded = isExpanded,
                    onClick = { onNavigate(item.screen) }
                )
            }
        }

        // Divider
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            color = NeumorphicTextTertiary.copy(alpha = 0.3f)
        )

        // Settings at bottom
        SidebarNavItem(
            item = bottomNavItem,
            isSelected = currentScreen == bottomNavItem.screen,
            isExpanded = isExpanded,
            onClick = { onNavigate(bottomNavItem.screen) }
        )

        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun SidebarNavItem(
    item: SidebarItem,
    isSelected: Boolean,
    isExpanded: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val bgColor = when {
        isSelected -> NeumorphicPrimary.copy(alpha = 0.12f)
        isHovered -> NeumorphicDepressed.copy(alpha = 0.5f)
        else -> Color.Transparent
    }

    val contentColor = when {
        isSelected -> NeumorphicPrimary
        isHovered -> NeumorphicTextPrimary
        else -> NeumorphicTextSecondary
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .hoverable(interactionSource)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (isSelected) item.selectedIcon else item.icon,
            contentDescription = item.label,
            tint = contentColor,
            modifier = Modifier.size(26.dp)
        )

        AnimatedVisibility(
            visible = isExpanded,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(150))
        ) {
            Text(
                text = item.label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                color = contentColor,
                modifier = Modifier.padding(start = 14.dp)
            )
        }
    }
}
