package com.budgetmanager.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.budgetmanager.data.repository.TagRepository
import com.budgetmanager.domain.model.Tag
import com.budgetmanager.presentation.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext

/**
 * Smart tag input. Type to autocomplete from existing tags.
 * Press Enter or comma to confirm a tag. Backspace on empty input removes the last chip.
 */
@Composable
fun TagInput(
    tags: List<String>,
    onTagsChange: (List<String>) -> Unit,
    transactionTitle: String = "",
    label: String = "Tags",
    modifier: Modifier = Modifier
) {
    val tagRepo = remember { GlobalContext.get().get<TagRepository>() }
    var input by remember { mutableStateOf("") }
    var suggestions by remember { mutableStateOf<List<Tag>>(emptyList()) }
    var smartSuggestions by remember { mutableStateOf<List<Tag>>(emptyList()) }
    val interaction = remember { MutableInteractionSource() }
    val isFocused by interaction.collectIsFocusedAsState()
    val coroutineScope = rememberCoroutineScope()

    // Autocomplete on input change
    LaunchedEffect(input) {
        delay(150)
        suggestions = if (input.isBlank()) emptyList()
        else tagRepo.suggestByPrefix(input).filter { it.name !in tags }
    }

    // Smart suggestions based on title (only when not typing and few tags exist)
    LaunchedEffect(transactionTitle) {
        delay(300)
        if (transactionTitle.length >= 3 && tags.size < 3) {
            smartSuggestions = tagRepo.suggestForTitle(transactionTitle).filter { it.name !in tags }
        } else {
            smartSuggestions = emptyList()
        }
    }

    fun addTag(name: String) {
        val cleaned = name.trim().lowercase()
        if (cleaned.isNotBlank() && cleaned !in tags) {
            onTagsChange(tags + cleaned)
        }
        input = ""
    }

    fun removeTag(name: String) {
        onTagsChange(tags - name)
    }

    Column(modifier = modifier) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (isFocused) NeumorphicPrimary else NeumorphicTextSecondary,
            modifier = Modifier.padding(bottom = 6.dp, start = 4.dp)
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .neumorphicPressed(
                    depth = if (isFocused) 5.dp else 3.dp,
                    borderRadius = 12.dp,
                    backgroundColor = if (isFocused) Color.White.copy(alpha = 0.5f) else NeumorphicDepressed.copy(alpha = 0.6f)
                )
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                tags.forEach { tag ->
                    TagChip(name = tag, onRemove = { removeTag(tag) })
                }
                BasicTextField(
                    value = input,
                    onValueChange = { newVal ->
                        // Comma submits the tag
                        if (newVal.endsWith(",") || newVal.endsWith(";")) {
                            addTag(newVal.dropLast(1))
                        } else {
                            input = newVal
                        }
                    },
                    singleLine = true,
                    interactionSource = interaction,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = NeumorphicTextPrimary),
                    cursorBrush = SolidColor(NeumorphicPrimary),
                    modifier = Modifier.widthIn(min = 80.dp).padding(vertical = 4.dp),
                    decorationBox = { inner ->
                        Box {
                            if (input.isEmpty() && tags.isEmpty()) {
                                Text(
                                    "Saisir un tag (Entree ou , pour valider)",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = NeumorphicTextTertiary
                                )
                            }
                            inner()
                        }
                    }
                )
            }
        }

        // Suggestion dropdown
        if (suggestions.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    "Suggestions :",
                    style = MaterialTheme.typography.labelSmall,
                    color = NeumorphicTextTertiary,
                    modifier = Modifier.padding(top = 6.dp)
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    suggestions.take(8).forEach { tag ->
                        SuggestionChip(
                            name = tag.name,
                            badge = if (tag.usageCount > 0) "${tag.usageCount}x" else null,
                            onClick = { addTag(tag.name) }
                        )
                    }
                }
            }
        }

        // Smart suggestions based on title
        if (smartSuggestions.isNotEmpty() && suggestions.isEmpty()) {
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    Icons.Filled.AutoAwesome, null,
                    tint = NeumorphicPrimary,
                    modifier = Modifier.size(14.dp).align(Alignment.Top)
                )
                Text(
                    "Habituellement utilises pour ce libelle :",
                    style = MaterialTheme.typography.labelSmall,
                    color = NeumorphicPrimary,
                    modifier = Modifier.padding(top = 2.dp)
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    smartSuggestions.take(5).forEach { tag ->
                        SuggestionChip(
                            name = tag.name,
                            badge = "✨",
                            onClick = { addTag(tag.name) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TagChip(name: String, onRemove: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(NeumorphicPrimary.copy(alpha = 0.15f))
            .padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "#$name",
            style = MaterialTheme.typography.labelMedium,
            color = NeumorphicPrimary,
            fontWeight = FontWeight.Medium
        )
        Box(
            modifier = Modifier
                .padding(start = 4.dp)
                .size(18.dp)
                .clip(CircleShape)
                .pointerHoverIcon(PointerIcon.Hand)
                .clickable { onRemove() },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Close, "Retirer", tint = NeumorphicPrimary, modifier = Modifier.size(12.dp))
        }
    }
}

@Composable
private fun SuggestionChip(name: String, badge: String? = null, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(NeumorphicElevated)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            name,
            style = MaterialTheme.typography.labelSmall,
            color = NeumorphicTextPrimary
        )
        if (badge != null) {
            Spacer(Modifier.width(4.dp))
            Text(
                badge,
                style = MaterialTheme.typography.labelSmall,
                color = NeumorphicTextTertiary
            )
        }
    }
}

// Compose 1.6.x has FlowRow in foundation; provide a simple wrapper that opts in to the experimental API
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun FlowRow(
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable () -> Unit
) {
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = horizontalArrangement,
        verticalArrangement = verticalArrangement,
        content = { content() }
    )
}
