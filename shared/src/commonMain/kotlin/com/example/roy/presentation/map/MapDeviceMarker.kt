package com.example.roy.presentation.map

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.roy.presentation.theme.RoyTheme
import org.jetbrains.compose.resources.stringResource
import roy.shared.generated.resources.Res
import roy.shared.generated.resources.map_device_position

@Composable
internal fun MapDeviceMarker(modifier: Modifier = Modifier) {
    val colors = RoyTheme.colors
    val label = stringResource(Res.string.map_device_position)
    Box(
        modifier.size(RoyTheme.dimensions.spacing.medium)
            .background(colors.geo, CircleShape)
            .border(2.dp, colors.surfaceElevated, CircleShape)
            .semantics { contentDescription = label },
    )
}
