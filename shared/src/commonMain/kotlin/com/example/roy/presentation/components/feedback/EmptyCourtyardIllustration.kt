package com.example.roy.presentation.components.feedback

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.roy.presentation.theme.RoyTheme
import org.jetbrains.compose.resources.painterResource
import roy.shared.generated.resources.Res
import roy.shared.generated.resources.empty_courtyard_dark
import roy.shared.generated.resources.empty_courtyard_light

@Composable
internal fun EmptyCourtyardIllustration() {
    val courtyard = if (RoyTheme.isDark) Res.drawable.empty_courtyard_dark else Res.drawable.empty_courtyard_light
    Image(
        painter = painterResource(courtyard),
        contentDescription = null,
        modifier = Modifier.widthIn(max = 300.dp).fillMaxWidth().aspectRatio(300f / 248f),
    )
}
