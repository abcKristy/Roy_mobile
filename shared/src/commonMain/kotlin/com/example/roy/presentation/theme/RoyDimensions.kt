package com.example.roy.presentation.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
class RoySpacing internal constructor() {
    val none: Dp = 0.dp
    val extraSmall: Dp = 4.dp
    val small: Dp = 8.dp
    val compact: Dp = 12.dp
    val medium: Dp = 16.dp
    val comfortable: Dp = 20.dp
    val large: Dp = 24.dp
    val extraLarge: Dp = 32.dp
    val space40: Dp = 40.dp
    val space48: Dp = 48.dp
    val space64: Dp = 64.dp
}

@Immutable
class RoyRadii internal constructor() {
    val none: Dp = 0.dp
    val small: Dp = 8.dp
    val medium: Dp = 12.dp
    val large: Dp = 20.dp
    val extraLarge: Dp = 28.dp
    val pill: Dp = 999.dp
    val button: Dp = 18.dp
    val textField: Dp = 17.dp
    val card: Dp = 22.dp
    val dialog: Dp = 26.dp
}

@Immutable
class RoyDimensions internal constructor() {
    val spacing: RoySpacing = RoySpacing()
    val radii: RoyRadii = RoyRadii()
    val minimumTouchTarget: Dp = 48.dp
    val buttonMinHeight: Dp = 52.dp
    val textFieldMinHeight: Dp = 76.dp
    val iconSize: Dp = 24.dp
    val avatarSmall: Dp = 32.dp
    val avatarMedium: Dp = 48.dp
}
