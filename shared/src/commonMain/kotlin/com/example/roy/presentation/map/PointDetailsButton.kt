package com.example.roy.presentation.map

import androidx.compose.runtime.Composable
import com.example.roy.presentation.components.TertiaryButton
import org.jetbrains.compose.resources.stringResource
import roy.shared.generated.resources.Res
import roy.shared.generated.resources.map_details

@Composable
internal fun PointDetailsButton(isEnabled: Boolean, onClick: () -> Unit) {
    TertiaryButton(
        label = stringResource(Res.string.map_details),
        onClick = onClick,
        isEnabled = isEnabled,
    )
}
