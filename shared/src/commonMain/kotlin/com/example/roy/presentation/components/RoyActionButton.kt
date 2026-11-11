package com.example.roy.presentation.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.roy.presentation.theme.RoyTheme
import org.jetbrains.compose.resources.stringResource
import roy.shared.generated.resources.Res
import roy.shared.generated.resources.action_in_progress

@Composable
internal fun RoyActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier,
    isEnabled: Boolean,
    isSubmitting: Boolean,
    background: Brush?,
    textColor: Color,
    outlineColor: Color? = null,
) {
    val dimensions = RoyTheme.dimensions
    val shape = RoundedCornerShape(dimensions.radii.button)
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val isFocused by interactionSource.collectIsFocusedAsState()
    val canClick = isEnabled && !isSubmitting
    val progressLabel = if (isSubmitting) stringResource(Res.string.action_in_progress) else null
    val surface = background?.let { Modifier.background(it, shape) } ?: Modifier
    val outline = when {
        canClick && isFocused -> Modifier.border(2.dp, RoyTheme.colors.textPrimary, shape)
        outlineColor != null -> Modifier.border(1.dp, outlineColor, shape)
        else -> Modifier
    }

    Box(
        modifier = modifier
            .sizeIn(minWidth = dimensions.minimumTouchTarget, minHeight = dimensions.buttonMinHeight)
            .clickable(
                enabled = canClick,
                role = Role.Button,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .clearAndSetSemantics {
                contentDescription = text
                progressLabel?.let { stateDescription = it }
            },
        propagateMinConstraints = true,
    ) {
        Box(
            modifier = Modifier
                .graphicsLayer {
                    val scale = if (canClick && isPressed) 0.98f else 1f
                    scaleX = scale
                    scaleY = scale
                }
                .then(surface)
                .then(outline)
                .padding(horizontal = dimensions.spacing.medium, vertical = dimensions.spacing.small),
            contentAlignment = Alignment.Center,
        ) {
            BasicText(
                text = text,
                modifier = Modifier.alpha(if (isSubmitting) 0f else 1f),
                style = RoyTheme.typography.button.copy(color = textColor, textAlign = TextAlign.Center),
            )
            if (isSubmitting) ButtonProgress(textColor)
        }
    }
}

@Composable
private fun ButtonProgress(color: Color) {
    val transition = rememberInfiniteTransition(label = "Button progress")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 900, easing = LinearEasing)),
        label = "Button progress angle",
    )
    Canvas(Modifier.size(RoyTheme.dimensions.spacing.medium)) {
        val stroke = Stroke(width = size.minDimension / 8f, cap = StrokeCap.Round)
        drawArc(color, startAngle = angle, sweepAngle = 270f, useCenter = false, style = stroke)
    }
}
