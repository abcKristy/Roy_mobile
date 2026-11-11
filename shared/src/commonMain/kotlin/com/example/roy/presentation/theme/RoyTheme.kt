package com.example.roy.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf

private val LocalRoyColors = compositionLocalOf<RoyColors> { error("RoyTheme is required") }
private val LocalRoyTypography = staticCompositionLocalOf<RoyTypography> { error("RoyTheme is required") }
private val LocalRoyDimensions = staticCompositionLocalOf<RoyDimensions> { error("RoyTheme is required") }
private val defaultDimensions = RoyDimensions()

object RoyTheme {
    val colors: RoyColors
        @Composable @ReadOnlyComposable get() = LocalRoyColors.current

    val typography: RoyTypography
        @Composable @ReadOnlyComposable get() = LocalRoyTypography.current

    val dimensions: RoyDimensions
        @Composable @ReadOnlyComposable get() = LocalRoyDimensions.current
}

@Composable
fun RoyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) RoyDarkColors else RoyLightColors
    CompositionLocalProvider(
        LocalRoyColors provides colors,
        LocalRoyTypography provides rememberRoyTypography(),
        LocalRoyDimensions provides defaultDimensions,
        content = content,
    )
}
