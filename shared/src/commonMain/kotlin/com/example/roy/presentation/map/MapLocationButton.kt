package com.example.roy.presentation.map

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.example.roy.presentation.components.indicators.RoyLoadingIndicator
import com.example.roy.presentation.theme.RoyTheme
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import roy.shared.generated.resources.Res
import roy.shared.generated.resources.map_geo
import roy.shared.generated.resources.map_locate_action
import roy.shared.generated.resources.map_locate_pending

@Composable
internal fun MapLocationButton(
    isLocating: Boolean,
    isEnabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = RoyTheme.colors
    val label = stringResource(Res.string.map_locate_action)
    val pending = stringResource(Res.string.map_locate_pending)
    Box(
        modifier = modifier.size(56.dp)
            .clip(CircleShape)
            .background(if (isEnabled || isLocating) colors.geo else colors.surfaceElevated)
            .clickable(enabled = isEnabled && !isLocating, role = Role.Button, onClick = onClick)
            .semantics {
                contentDescription = label
                if (isLocating) stateDescription = pending
            },
        contentAlignment = Alignment.Center,
    ) {
        if (isLocating) {
            RoyLoadingIndicator(colors.textOnPrimary, Modifier.size(RoyTheme.dimensions.iconSize))
        } else {
            Image(
                painter = painterResource(Res.drawable.map_geo),
                contentDescription = null,
                modifier = Modifier.size(RoyTheme.dimensions.iconSize),
                colorFilter = ColorFilter.tint(if (isEnabled) colors.textOnPrimary else colors.textSecondary),
            )
        }
    }
}
