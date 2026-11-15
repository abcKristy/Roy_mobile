package com.example.roy.presentation.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.example.roy.presentation.theme.RoyTheme
import org.jetbrains.compose.resources.stringResource
import roy.shared.generated.resources.Res
import roy.shared.generated.resources.map_filter_active
import roy.shared.generated.resources.map_filter_inactive
import roy.shared.generated.resources.map_filter_title

@Composable
internal fun MapFilterButton(isActive: Boolean, onClick: () -> Unit) {
    val label = stringResource(Res.string.map_filter_title)
    val description = stringResource(if (isActive) Res.string.map_filter_active else Res.string.map_filter_inactive)
    val colors = RoyTheme.colors
    val dimensions = RoyTheme.dimensions
    Box(
        Modifier.size(dimensions.minimumTouchTarget).clip(CircleShape).background(colors.surfaceElevated)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics {
                contentDescription = label
                stateDescription = description
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(dimensions.iconSize)) {
            val stroke = size.width / 12
            for ((index, inset) in listOf(0.12f, 0.25f, 0.38f).withIndex()) {
                val y = size.height * (index + 1) / 4
                drawLine(
                    colors.textPrimary, Offset(size.width * inset, y), Offset(size.width * (1 - inset), y),
                    strokeWidth = stroke, cap = StrokeCap.Round,
                )
            }
            if (isActive) drawCircle(colors.error, radius = size.width / 7, center = Offset(size.width, 0f))
        }
    }
}
