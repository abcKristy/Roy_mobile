package com.example.roy.presentation.components.indicators

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke

@Composable
internal fun RoyLoadingIndicator(color: Color, modifier: Modifier) {
    val transition = rememberInfiniteTransition(label = "Indeterminate progress")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 900, easing = LinearEasing)),
        label = "Progress angle",
    )
    Canvas(modifier) {
        val stroke = Stroke(width = size.minDimension / 8f, cap = StrokeCap.Round)
        drawArc(color, startAngle = angle, sweepAngle = 270f, useCenter = false, style = stroke)
    }
}
