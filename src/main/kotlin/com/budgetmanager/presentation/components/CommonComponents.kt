package com.budgetmanager.presentation.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.focus.focusRequester
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.budgetmanager.presentation.theme.*
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale

// ===== Currency Formatting =====

@Composable
fun CurrencyAmount(
    amount: BigDecimal,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.titleLarge,
    color: Color? = null,
    isIncome: Boolean? = null,
    showSign: Boolean = false,
    currencyCode: String = "EUR"
) {
    val formatter = remember(currencyCode) {
        NumberFormat.getCurrencyInstance(Locale.FRANCE).apply {
            runCatching { currency = java.util.Currency.getInstance(currencyCode) }
        }
    }
    val displayColor = color ?: when {
        isIncome == true -> IncomeColor
        isIncome == false -> ExpenseColor
        amount >= BigDecimal.ZERO -> NeumorphicTextPrimary
        else -> ExpenseColor
    }
    val prefix = when {
        showSign && amount > BigDecimal.ZERO -> "+"
        showSign && amount < BigDecimal.ZERO -> ""
        else -> ""
    }
    val formatted = runCatching { formatter.format(amount) }.getOrElse {
        "${String.format("%.2f", amount.toDouble())} $currencyCode"
    }
    Text(
        text = prefix + formatted,
        style = style,
        color = displayColor,
        fontWeight = FontWeight.Bold,
        modifier = modifier
    )
}

// ===== Neumorphic Card =====

@Composable
fun NeumorphicCard(
    modifier: Modifier = Modifier,
    elevation: Dp = 8.dp,
    borderRadius: Dp = 16.dp,
    backgroundColor: Color = NeumorphicElevated,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .neumorphicShadow(
                elevation = elevation,
                borderRadius = borderRadius,
                backgroundColor = backgroundColor
            )
            .padding(20.dp),
        content = content
    )
}

// ===== Balance Card =====

@Composable
fun BalanceCard(
    title: String,
    amount: BigDecimal,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    amountColor: Color? = null,
    onClick: (() -> Unit)? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    NeumorphicCard(
        modifier = modifier
            .then(
                if (onClick != null) Modifier
                    .pointerHoverIcon(PointerIcon.Hand)
                    .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
                    .hoverable(interactionSource)
                else Modifier
            ),
        elevation = if (isHovered) 10.dp else 8.dp
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(NeumorphicPrimary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = NeumorphicPrimary,
                    modifier = Modifier.size(26.dp)
                )
            }
            Spacer(Modifier.width(16.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = NeumorphicTextSecondary
                )
                Spacer(Modifier.height(6.dp))
                CurrencyAmount(
                    amount = amount,
                    style = MaterialTheme.typography.titleLarge,
                    color = amountColor
                )
            }
        }
    }
}

// ===== Transaction Item =====

@Composable
fun TransactionItem(
    title: String,
    amount: BigDecimal,
    category: String?,
    date: String,
    isIncome: Boolean,
    categoryColor: Color? = null,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isHovered) NeumorphicDepressed.copy(alpha = 0.5f) else Color.Transparent)
            .then(
                if (onClick != null) Modifier
                    .pointerHoverIcon(PointerIcon.Hand)
                    .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
                    .hoverable(interactionSource)
                else Modifier.hoverable(interactionSource)
            )
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Category color dot — larger for desktop
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(categoryColor ?: if (isIncome) IncomeColor else ExpenseColor)
        )

        Spacer(Modifier.width(16.dp))

        // Title + Category
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = NeumorphicTextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (category != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = category,
                    style = MaterialTheme.typography.bodySmall,
                    color = NeumorphicTextSecondary
                )
            }
        }

        // Date — more visible
        Text(
            text = date,
            style = MaterialTheme.typography.bodyMedium,
            color = NeumorphicTextSecondary,
            modifier = Modifier.padding(horizontal = 24.dp)
        )

        // Amount
        CurrencyAmount(
            amount = amount,
            isIncome = isIncome,
            showSign = true,
            style = MaterialTheme.typography.titleMedium
        )
    }
}

// ===== Budget Progress Bar =====

@Composable
fun BudgetProgressBar(
    spent: Float,
    limit: Float,
    modifier: Modifier = Modifier,
    showLabel: Boolean = true,
    height: Dp = 8.dp
) {
    val percentage = if (limit > 0f) (spent / limit).coerceIn(0f, 1f) else 0f
    val animatedProgress by animateFloatAsState(targetValue = percentage, animationSpec = tween(600))
    val barColor = when {
        percentage >= 0.9f -> NeumorphicBudgetAlert
        percentage >= 0.7f -> NeumorphicBudgetWarning
        else -> NeumorphicBudgetSafe
    }

    Column(modifier = modifier) {
        if (showLabel) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${(percentage * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = barColor,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "${String.format("%.0f", spent)} / ${String.format("%.0f", limit)} €",
                    style = MaterialTheme.typography.labelSmall,
                    color = NeumorphicTextTertiary
                )
            }
            Spacer(Modifier.height(4.dp))
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .clip(RoundedCornerShape(height / 2))
                .background(NeumorphicDepressed)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(animatedProgress)
                    .clip(RoundedCornerShape(height / 2))
                    .background(barColor)
            )
        }
    }
}

// ===== Empty State =====

@Composable
fun EmptyState(
    message: String,
    icon: ImageVector = Icons.Filled.Inbox,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = NeumorphicTextTertiary,
            modifier = Modifier.size(64.dp)
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = NeumorphicTextSecondary,
            textAlign = TextAlign.Center
        )
        if (actionText != null && onAction != null) {
            Spacer(Modifier.height(16.dp))
            NeumorphicButton(text = actionText, onClick = onAction)
        }
    }
}

// ===== Neumorphic Button =====

@Composable
fun NeumorphicButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    isPrimary: Boolean = true
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val contentColor = when {
        !enabled -> NeumorphicTextTertiary
        isPrimary && isHovered -> Color.White
        isPrimary -> Color.White
        else -> NeumorphicTextPrimary
    }

    Row(
        modifier = modifier
            .then(
                when {
                    !enabled -> Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(NeumorphicDepressed)
                    isPrimary && isHovered -> Modifier.neumorphicPressed(
                        depth = 4.dp,
                        borderRadius = 12.dp,
                        backgroundColor = NeumorphicPrimary.copy(alpha = 0.85f)
                    )
                    isPrimary -> Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(NeumorphicPrimary)
                    isHovered -> Modifier.neumorphicPressed(
                        depth = 4.dp,
                        borderRadius = 12.dp,
                        backgroundColor = NeumorphicDepressed
                    )
                    else -> Modifier.neumorphicShadow(
                        elevation = 6.dp,
                        borderRadius = 12.dp,
                        backgroundColor = NeumorphicElevated
                    )
                }
            )
            .pointerHoverIcon(if (enabled) PointerIcon.Hand else PointerIcon.Default)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick
            )
            .hoverable(interactionSource)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = contentColor,
            fontWeight = FontWeight.SemiBold
        )
    }
}

// ===== Section Header =====

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    actionText: String? = null,
    onAction: (() -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(top = 0.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = NeumorphicTextPrimary,
            fontWeight = FontWeight.Bold
        )
        if (actionText != null && onAction != null) {
            Text(
                text = actionText,
                style = MaterialTheme.typography.labelLarge,
                color = NeumorphicPrimary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .pointerHoverIcon(PointerIcon.Hand)
                    .clickable(onClick = onAction)
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            )
        }
    }
}

// ===== Search Bar =====

@Composable
fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Rechercher..."
) {
    Row(
        modifier = modifier
            .neumorphicPressed(depth = 4.dp, borderRadius = 14.dp)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Filled.Search,
            contentDescription = null,
            tint = NeumorphicTextSecondary,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.width(14.dp))
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = NeumorphicTextPrimary),
            cursorBrush = SolidColor(NeumorphicPrimary),
            singleLine = true,
            modifier = Modifier.weight(1f),
            decorationBox = { innerTextField ->
                Box {
                    if (query.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = MaterialTheme.typography.bodyLarge,
                            color = NeumorphicTextTertiary
                        )
                    }
                    innerTextField()
                }
            }
        )
        if (query.isNotEmpty()) {
            IconButton(
                onClick = { onQueryChange("") },
                modifier = Modifier.size(20.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Clear",
                    tint = NeumorphicTextTertiary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

// ===== Confirm Dialog =====

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    confirmText: String = "Confirmer",
    dismissText: String = "Annuler"
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(title, style = MaterialTheme.typography.headlineMedium, color = NeumorphicTextPrimary)
        },
        text = {
            Text(message, style = MaterialTheme.typography.bodyLarge, color = NeumorphicTextSecondary)
        },
        confirmButton = {
            NeumorphicButton(text = confirmText, onClick = onConfirm)
        },
        dismissButton = {
            NeumorphicButton(text = dismissText, onClick = onDismiss, isPrimary = false)
        },
        containerColor = NeumorphicElevated,
        shape = RoundedCornerShape(16.dp)
    )
}

// ===== Filter Chip (for transaction filtering etc.) =====

@Composable
fun FilterChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(
                when {
                    isSelected -> NeumorphicPrimary
                    isHovered -> NeumorphicDepressed
                    else -> NeumorphicElevated
                }
            )
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .hoverable(interactionSource)
            .padding(horizontal = 20.dp, vertical = 10.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (isSelected) Color.White else NeumorphicTextSecondary,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

// ===== Desktop TextField Colors =====

/**
 * Explicit text field colors for editable fields on Compose Desktop.
 * Ensures proper text rendering, visible cursor, and clear focus indicators.
 */
@Composable
fun editableTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = NeumorphicTextPrimary,
    unfocusedTextColor = NeumorphicTextPrimary,
    disabledTextColor = NeumorphicTextTertiary,
    cursorColor = NeumorphicPrimary,
    focusedBorderColor = NeumorphicPrimary,
    unfocusedBorderColor = NeumorphicTextTertiary.copy(alpha = 0.6f),
    focusedContainerColor = Color.White.copy(alpha = 0.45f),
    unfocusedContainerColor = Color.White.copy(alpha = 0.25f),
    focusedLabelColor = NeumorphicPrimary,
    unfocusedLabelColor = NeumorphicTextSecondary,
    focusedPlaceholderColor = NeumorphicTextTertiary,
    unfocusedPlaceholderColor = NeumorphicTextTertiary,
)

// ===== Neumorphic TextField =====

@Composable
fun NeumorphicTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = 1,
    suffix: String? = null,
    prefix: String? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    textStyle: TextStyle = TextStyle.Default,
    enabled: Boolean = true
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Column(modifier = modifier) {
        if (label.isNotBlank()) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = if (isFocused) NeumorphicPrimary else NeumorphicTextSecondary,
                modifier = Modifier.padding(bottom = 6.dp, start = 4.dp)
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .neumorphicPressed(
                    depth = if (isFocused) 5.dp else 3.dp,
                    borderRadius = 12.dp,
                    backgroundColor = if (isFocused) Color.White.copy(alpha = 0.5f) else NeumorphicDepressed.copy(alpha = 0.6f)
                )
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (leadingIcon != null) {
                    leadingIcon()
                    Spacer(Modifier.width(10.dp))
                }
                if (prefix != null) {
                    Text(prefix, style = MaterialTheme.typography.bodyLarge, color = NeumorphicTextSecondary)
                    Spacer(Modifier.width(6.dp))
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = singleLine,
                    minLines = minLines,
                    maxLines = if (singleLine) 1 else maxLines,
                    enabled = enabled,
                    interactionSource = interactionSource,
                    textStyle = if (textStyle == TextStyle.Default)
                        MaterialTheme.typography.bodyLarge.copy(color = NeumorphicTextPrimary)
                    else textStyle.copy(color = if (textStyle.color == Color.Unspecified) NeumorphicTextPrimary else textStyle.color),
                    cursorBrush = SolidColor(NeumorphicPrimary),
                    modifier = Modifier.weight(1f),
                    decorationBox = { innerTextField ->
                        Box {
                            if (value.isEmpty() && placeholder.isNotBlank()) {
                                Text(
                                    placeholder,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = NeumorphicTextTertiary
                                )
                            }
                            innerTextField()
                        }
                    }
                )
                if (suffix != null) {
                    Spacer(Modifier.width(6.dp))
                    Text(suffix, style = MaterialTheme.typography.bodyLarge, color = NeumorphicTextSecondary)
                }
            }
        }
    }
}

/**
 * Colors for readOnly selector fields (dropdowns) - visually distinct from editable fields.
 */
@Composable
fun readOnlyTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = NeumorphicTextPrimary,
    unfocusedTextColor = NeumorphicTextPrimary,
    disabledTextColor = NeumorphicTextTertiary,
    cursorColor = Color.Transparent,
    focusedBorderColor = NeumorphicPrimary.copy(alpha = 0.5f),
    unfocusedBorderColor = NeumorphicTextTertiary.copy(alpha = 0.4f),
    focusedContainerColor = NeumorphicDepressed.copy(alpha = 0.3f),
    unfocusedContainerColor = NeumorphicDepressed.copy(alpha = 0.2f),
    focusedLabelColor = NeumorphicPrimary,
    unfocusedLabelColor = NeumorphicTextSecondary,
)

// ===== SearchableDropdown =====

/**
 * Dropdown avec barre de recherche intégrée + création rapide de catégorie.
 * - Tape pour filtrer les options existantes
 * - Si aucun résultat → bouton "Créer [texte]" apparaît
 * - onCreateNew(name) appelé si l'utilisateur veut créer une nouvelle catégorie
 */
@Composable
fun SearchableDropdown(
    label: String,
    selectedId: Long?,
    items: List<Pair<Long, String>>,
    onSelect: (Long) -> Unit,
    modifier: Modifier = Modifier,
    itemColor: (@Composable (Long) -> Color)? = null,
    onCreateNew: ((String) -> Unit)? = null,
    createNewLabel: String = "Creer"
) {
    var query by remember { mutableStateOf("") }
    var isEditing by remember { mutableStateOf(false) }
    val selectedName = items.find { it.first == selectedId }?.second ?: ""
    val focusRequester = remember { androidx.compose.ui.focus.FocusRequester() }

    val displayText = if (isEditing) query else selectedName
    val filtered = remember(query, items) {
        if (query.isBlank()) items
        else items.filter { it.second.contains(query, ignoreCase = true) }
    }
    val exactMatch = items.any { it.second.equals(query.trim(), ignoreCase = true) }
    val showCreateButton = onCreateNew != null && query.isNotBlank() && !exactMatch
    val showDropdown = isEditing

    Column(modifier = modifier) {
        // Label
        if (label.isNotBlank()) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = if (isEditing) NeumorphicPrimary else NeumorphicTextSecondary,
                modifier = Modifier.padding(bottom = 6.dp, start = 4.dp)
            )
        }

        // Input field
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .neumorphicPressed(
                    depth = if (isEditing) 5.dp else 3.dp,
                    borderRadius = 12.dp,
                    backgroundColor = if (isEditing) Color.White.copy(alpha = 0.5f) else NeumorphicDepressed.copy(alpha = 0.6f)
                )
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicTextField(
                    value = displayText,
                    onValueChange = { newValue ->
                        query = newValue
                        if (!isEditing) isEditing = true
                    },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = NeumorphicTextPrimary),
                    cursorBrush = SolidColor(NeumorphicPrimary),
                    modifier = Modifier.weight(1f).focusRequester(focusRequester),
                    decorationBox = { innerTextField ->
                        Box {
                            if (displayText.isEmpty()) {
                                Text("Rechercher...", style = MaterialTheme.typography.bodyLarge, color = NeumorphicTextTertiary)
                            }
                            innerTextField()
                        }
                    }
                )
                IconButton(
                    onClick = {
                        if (isEditing) {
                            isEditing = false
                            query = ""
                        } else {
                            isEditing = true
                            query = ""
                        }
                    },
                    modifier = Modifier.size(32.dp).pointerHoverIcon(PointerIcon.Hand)
                ) {
                    Icon(
                        imageVector = if (isEditing) Icons.Filled.Close else Icons.Filled.ArrowDropDown,
                        contentDescription = null,
                        tint = NeumorphicTextSecondary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        // Dropdown list (NOT DropdownMenu — just a visible list below the field)
        if (showDropdown) {
            Spacer(Modifier.height(4.dp))
            NeumorphicCard(
                modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp),
                elevation = 6.dp,
                borderRadius = 12.dp
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                ) {
                    if (filtered.isEmpty() && !showCreateButton) {
                        Text(
                            "Aucun resultat",
                            style = MaterialTheme.typography.bodyMedium,
                            color = NeumorphicTextTertiary,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }

                    filtered.forEach { (id, name) ->
                        val isItemSelected = id == selectedId
                        val itemInteraction = remember { MutableInteractionSource() }
                        val itemHovered by itemInteraction.collectIsHoveredAsState()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    when {
                                        isItemSelected -> NeumorphicPrimary.copy(alpha = 0.1f)
                                        itemHovered -> NeumorphicDepressed.copy(alpha = 0.5f)
                                        else -> Color.Transparent
                                    }
                                )
                                .pointerHoverIcon(PointerIcon.Hand)
                                .hoverable(itemInteraction)
                                .clickable {
                                    onSelect(id)
                                    isEditing = false
                                    query = ""
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (itemColor != null) {
                                Box(Modifier.size(10.dp).clip(CircleShape).background(itemColor(id)))
                                Spacer(Modifier.width(10.dp))
                            }
                            Text(
                                text = name,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isItemSelected) NeumorphicPrimary else NeumorphicTextPrimary,
                                fontWeight = if (isItemSelected) FontWeight.SemiBold else FontWeight.Normal,
                                modifier = Modifier.weight(1f)
                            )
                            if (isItemSelected) {
                                Icon(Icons.Filled.Check, null, tint = NeumorphicPrimary, modifier = Modifier.size(16.dp))
                            }
                        }
                    }

                    // Create button
                    if (showCreateButton) {
                        HorizontalDivider(color = NeumorphicTextTertiary.copy(alpha = 0.2f), modifier = Modifier.padding(vertical = 4.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .pointerHoverIcon(PointerIcon.Hand)
                                .clickable {
                                    onCreateNew?.invoke(query.trim())
                                    isEditing = false
                                    query = ""
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Add, null, tint = NeumorphicPrimary, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "$createNewLabel \"${query.trim()}\"",
                                style = MaterialTheme.typography.bodyMedium,
                                color = NeumorphicPrimary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}

// ===== Date Picker =====

/**
 * Champ de date neumorphique avec :
 * - Saisie manuelle au format dd/MM/yyyy avec validation
 * - Calendrier popup pour sélection visuelle
 * - Boutons rapides (Aujourd'hui, Hier)
 */
@Composable
fun NeumorphicDatePicker(
    date: java.time.LocalDate,
    onDateChange: (java.time.LocalDate) -> Unit,
    label: String = "Date",
    modifier: Modifier = Modifier
) {
    var showCalendar by remember { mutableStateOf(false) }
    var textValue by remember(date) { mutableStateOf(date.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"))) }
    var isError by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Column(modifier = modifier) {
        // Label
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (isFocused) NeumorphicPrimary else NeumorphicTextSecondary,
            modifier = Modifier.padding(bottom = 6.dp, start = 4.dp)
        )

        // Champ de saisie neumorphique
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .neumorphicPressed(
                    depth = if (isFocused) 5.dp else 3.dp,
                    borderRadius = 12.dp,
                    backgroundColor = if (isError) Color(0xFFFDE8E8)
                        else if (isFocused) Color.White.copy(alpha = 0.5f)
                        else NeumorphicDepressed.copy(alpha = 0.6f)
                )
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.CalendarMonth,
                    contentDescription = null,
                    tint = if (isFocused) NeumorphicPrimary else NeumorphicTextSecondary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(10.dp))
                BasicTextField(
                    value = textValue,
                    onValueChange = { newVal ->
                        // Autoriser uniquement chiffres et /
                        val filtered = newVal.filter { it.isDigit() || it == '/' }
                        if (filtered.length <= 10) {
                            textValue = filtered
                            // Tenter de parser
                            isError = false
                            if (filtered.length == 10) {
                                try {
                                    val parsed = java.time.LocalDate.parse(
                                        filtered,
                                        java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")
                                    )
                                    onDateChange(parsed)
                                } catch (_: Exception) {
                                    isError = true
                                }
                            }
                        }
                    },
                    singleLine = true,
                    interactionSource = interactionSource,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = NeumorphicTextPrimary),
                    cursorBrush = SolidColor(NeumorphicPrimary),
                    modifier = Modifier.weight(1f),
                    decorationBox = { innerTextField ->
                        Box {
                            if (textValue.isEmpty()) {
                                Text("jj/mm/aaaa", style = MaterialTheme.typography.bodyLarge, color = NeumorphicTextTertiary)
                            }
                            innerTextField()
                        }
                    }
                )
                // Bouton calendrier
                IconButton(
                    onClick = { showCalendar = !showCalendar },
                    modifier = Modifier.size(32.dp).pointerHoverIcon(PointerIcon.Hand)
                ) {
                    Icon(
                        imageVector = if (showCalendar) Icons.Filled.KeyboardArrowUp else Icons.Filled.EditCalendar,
                        contentDescription = "Ouvrir le calendrier",
                        tint = NeumorphicPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // Message d'erreur
        if (isError) {
            Text(
                text = "Format invalide (jj/mm/aaaa)",
                style = MaterialTheme.typography.labelSmall,
                color = ExpenseColor,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp)
            )
        }

        // Boutons rapides
        Row(
            modifier = Modifier.padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                label = "Aujourd'hui",
                isSelected = date == java.time.LocalDate.now(),
                onClick = { onDateChange(java.time.LocalDate.now()) }
            )
            FilterChip(
                label = "Hier",
                isSelected = date == java.time.LocalDate.now().minusDays(1),
                onClick = { onDateChange(java.time.LocalDate.now().minusDays(1)) }
            )
        }

        // Popup calendrier
        if (showCalendar) {
            Spacer(Modifier.height(8.dp))
            CalendarPopup(
                selectedDate = date,
                onDateSelected = {
                    onDateChange(it)
                    showCalendar = false
                }
            )
        }
    }
}

/**
 * Mini calendrier mensuel neumorphique.
 */
@Composable
private fun CalendarPopup(
    selectedDate: java.time.LocalDate,
    onDateSelected: (java.time.LocalDate) -> Unit
) {
    var displayMonth by remember(selectedDate) { mutableStateOf(java.time.YearMonth.from(selectedDate)) }
    val today = java.time.LocalDate.now()
    val daysOfWeek = listOf("Lu", "Ma", "Me", "Je", "Ve", "Sa", "Di")

    NeumorphicCard(
        modifier = Modifier.fillMaxWidth(),
        elevation = 6.dp,
        borderRadius = 14.dp
    ) {
        // Header: mois/année + navigation
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { displayMonth = displayMonth.minusMonths(1) },
                modifier = Modifier.size(32.dp).pointerHoverIcon(PointerIcon.Hand)
            ) {
                Icon(Icons.Filled.ChevronLeft, "Mois precedent", tint = NeumorphicTextSecondary, modifier = Modifier.size(20.dp))
            }

            Text(
                text = displayMonth.format(java.time.format.DateTimeFormatter.ofPattern("MMMM yyyy", java.util.Locale.FRANCE)),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = NeumorphicTextPrimary
            )

            IconButton(
                onClick = { displayMonth = displayMonth.plusMonths(1) },
                modifier = Modifier.size(32.dp).pointerHoverIcon(PointerIcon.Hand)
            ) {
                Icon(Icons.Filled.ChevronRight, "Mois suivant", tint = NeumorphicTextSecondary, modifier = Modifier.size(20.dp))
            }
        }

        Spacer(Modifier.height(8.dp))

        // Jours de la semaine
        Row(modifier = Modifier.fillMaxWidth()) {
            daysOfWeek.forEach { day ->
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        text = day,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = NeumorphicTextTertiary
                    )
                }
            }
        }

        Spacer(Modifier.height(4.dp))

        // Grille des jours
        val firstDay = displayMonth.atDay(1)
        // Lundi=1 ... Dimanche=7
        val startOffset = (firstDay.dayOfWeek.value - 1)
        val daysInMonth = displayMonth.lengthOfMonth()

        // Construire les semaines
        val totalCells = startOffset + daysInMonth
        val weeks = (totalCells + 6) / 7

        for (week in 0 until weeks) {
            Row(modifier = Modifier.fillMaxWidth()) {
                for (dayOfWeek in 0..6) {
                    val cellIndex = week * 7 + dayOfWeek
                    val dayNumber = cellIndex - startOffset + 1

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .padding(2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (dayNumber in 1..daysInMonth) {
                            val thisDate = displayMonth.atDay(dayNumber)
                            val isSelected = thisDate == selectedDate
                            val isToday = thisDate == today

                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        when {
                                            isSelected -> NeumorphicPrimary
                                            isToday -> NeumorphicPrimary.copy(alpha = 0.1f)
                                            else -> Color.Transparent
                                        }
                                    )
                                    .pointerHoverIcon(PointerIcon.Hand)
                                    .clickable { onDateSelected(thisDate) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = dayNumber.toString(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal,
                                    color = when {
                                        isSelected -> Color.White
                                        isToday -> NeumorphicPrimary
                                        else -> NeumorphicTextPrimary
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ===== Color helper =====

@Composable
fun parseColor(hex: String?): Color {
    if (hex == null) return NeumorphicPrimary
    return try {
        val cleanHex = hex.removePrefix("#")
        val colorLong = cleanHex.toLong(16)
        if (cleanHex.length == 6) {
            Color(0xFF000000 or colorLong)
        } else {
            Color(colorLong)
        }
    } catch (_: Exception) {
        NeumorphicPrimary
    }
}
