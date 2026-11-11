package com.example.roy.presentation.components.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.roy.presentation.theme.RoyTheme

@Composable
fun RoyCard(
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = RoyTheme.colors
    val dimensions = RoyTheme.dimensions
    val shape = RoundedCornerShape(dimensions.radii.card)
    val interactions = if (onClick != null) remember { MutableInteractionSource() } else null
    val isFocused = interactions?.collectIsFocusedAsState()?.value == true
    val isPressed = interactions?.collectIsPressedAsState()?.value == true
    val outline = if (isFocused || isSelected || isPressed) {
        val width = if (isFocused && isSelected) 4.dp else 2.dp
        val color = if (isSelected || isPressed) colors.accent else colors.textPrimary
        Modifier.border(width, color, shape)
    } else {
        Modifier
    }
    val action = if (onClick != null) {
        Modifier
            .sizeIn(minWidth = dimensions.minimumTouchTarget, minHeight = dimensions.minimumTouchTarget)
            .clickable(
                interactionSource = interactions,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics { selected = isSelected }
    } else {
        Modifier
    }

    Column(
        modifier = modifier
            .then(action)
            .clip(shape)
            .background(if (isPressed) colors.surface else colors.surfaceElevated)
            .then(outline)
            .padding(dimensions.spacing.medium),
        verticalArrangement = Arrangement.spacedBy(dimensions.spacing.small),
        content = content,
    )
}
