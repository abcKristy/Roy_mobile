package com.example.roy.presentation.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.Font
import roy.shared.generated.resources.Res
import roy.shared.generated.resources.geist_medium
import roy.shared.generated.resources.geist_regular
import roy.shared.generated.resources.geist_semibold

@Immutable
class RoyTypography internal constructor(fontFamily: FontFamily) {
    private fun style(size: Int, lineHeight: Int, weight: FontWeight, family: FontFamily) =
        TextStyle(
            fontFamily = family,
            fontWeight = weight,
            fontSize = size.sp,
            lineHeight = lineHeight.sp,
            letterSpacing = 0.sp,
        )

    val display: TextStyle = style(40, 48, FontWeight.SemiBold, fontFamily)
    val headingLarge: TextStyle = style(32, 40, FontWeight.SemiBold, fontFamily)
    val headingMedium: TextStyle = style(24, 32, FontWeight.SemiBold, fontFamily)
    val headingSmall: TextStyle = style(20, 28, FontWeight.Medium, fontFamily)
    val body: TextStyle = style(16, 24, FontWeight.Normal, fontFamily)
    val bodySmall: TextStyle = style(14, 20, FontWeight.Normal, fontFamily)
    val caption: TextStyle = style(12, 16, FontWeight.Medium, fontFamily)
    val button: TextStyle = style(16, 20, FontWeight.SemiBold, fontFamily)
}

@Composable
internal fun rememberRoyTypography(): RoyTypography {
    val regular = Font(Res.font.geist_regular, FontWeight.Normal)
    val medium = Font(Res.font.geist_medium, FontWeight.Medium)
    val semiBold = Font(Res.font.geist_semibold, FontWeight.SemiBold)
    return remember(regular, medium, semiBold) {
        RoyTypography(FontFamily(regular, medium, semiBold))
    }
}
