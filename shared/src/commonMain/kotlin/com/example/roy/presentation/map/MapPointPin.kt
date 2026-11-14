package com.example.roy.presentation.map

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.roy.presentation.theme.RoyTheme
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import roy.shared.generated.resources.Res
import roy.shared.generated.resources.map_basketball
import roy.shared.generated.resources.map_football
import roy.shared.generated.resources.map_pin
import roy.shared.generated.resources.map_running
import roy.shared.generated.resources.map_selected
import roy.shared.generated.resources.map_single
import roy.shared.generated.resources.map_volleyball

@Composable
internal fun MapPointPin(
    topicKey: String?,
    isSelected: Boolean,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MapPinBody(
        image = if (isSelected) Res.drawable.map_selected else Res.drawable.map_single,
        isSelected = isSelected,
        contentDescription = contentDescription,
        onClick = onClick,
        modifier = modifier,
    ) {
        Image(
            painter = painterResource(mapTopicIcon(topicKey)),
            contentDescription = null,
            modifier = Modifier.align(Alignment.BottomCenter)
                .offset(y = if (isSelected) (-24).dp else (-19).dp)
                .size(24.dp)
                .background(RoyTheme.colors.surfaceElevated, CircleShape),
            colorFilter = ColorFilter.tint(RoyTheme.colors.textPrimary),
        )
    }
}

internal fun mapTopicIcon(topicKey: String?): DrawableResource = when (topicKey) {
    "football" -> Res.drawable.map_football
    "basketball" -> Res.drawable.map_basketball
    "volleyball" -> Res.drawable.map_volleyball
    "running" -> Res.drawable.map_running
    else -> Res.drawable.map_pin
}

@Composable
internal fun MapPinBody(
    image: DrawableResource,
    isSelected: Boolean,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit = {},
) {
    val targetWidth = RoyTheme.dimensions.minimumTouchTarget
    // The exported assets include transparent pixels below their pin tips and shadows.
    val pinWidth = if (isSelected) 44.dp else 38.dp
    val pinHeight = if (isSelected) 55.4654.dp else 48.dp
    Box(
        modifier = modifier
            .size(width = targetWidth, height = pinHeight)
            .selectable(selected = isSelected, role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
    ) {
        Image(
            painter = painterResource(image),
            contentDescription = null,
            modifier = Modifier.align(Alignment.BottomCenter).offset(y = 4.dp)
                .size(width = pinWidth, height = pinHeight),
        )
        icon()
    }
}
