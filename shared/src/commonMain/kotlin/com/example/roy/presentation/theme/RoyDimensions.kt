package com.example.roy.presentation.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.dp

@Immutable
class RoySpacing internal constructor() {
    val none = 0.dp
    val extraSmall = 4.dp
    val small = 8.dp
    val compact = 12.dp
    val medium = 16.dp
    val comfortable = 20.dp
    val large = 24.dp
    val extraLarge = 32.dp
    val space40 = 40.dp
    val space48 = 48.dp
    val space64 = 64.dp
}

@Immutable
class RoyRadii internal constructor() {
    val none = 0.dp
    val small = 8.dp
    val medium = 12.dp
    val large = 20.dp
    val extraLarge = 28.dp
    val pill = 999.dp
    val button = 18.dp
    val textField = 17.dp
    val card = 22.dp
}

@Immutable
class RoyDimensions internal constructor() {
    val spacing = RoySpacing()
    val radii = RoyRadii()
    val minimumTouchTarget = 48.dp
    val buttonMinHeight = 52.dp
    val textFieldMinHeight = 76.dp
    val iconSize = 24.dp
    val avatarSmall = 32.dp
    val avatarMedium = 48.dp
}
