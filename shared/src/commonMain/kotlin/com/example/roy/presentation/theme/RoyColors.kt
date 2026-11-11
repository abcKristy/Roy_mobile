package com.example.roy.presentation.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

@Immutable
data class RoyPrimaryGradient(
    val start: Color,
    val end: Color,
)

@Immutable
data class RoyColors(
    val primaryGradient: RoyPrimaryGradient,
    val primaryHover: Color,
    val primaryPressed: Color,
    val secondary: Color,
    val glass: Color,
    val accent: Color,
    val background: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textDisabled: Color,
    val textOnPrimary: Color,
    val outline: Color,
    val success: Color,
    val warning: Color,
    val error: Color,
    val pin: Color,
    val geo: Color,
    val scrim: Color,
    val surfaceBeige: Color,
)

internal val RoyLightColors = RoyColors(
    primaryGradient = RoyPrimaryGradient(
        start = Color(0xFFFF3D00),
        end = Color(0xFFFF1744),
    ),
    primaryHover = Color(0xFFFF6E40),
    primaryPressed = Color(0xFFDD2C00),
    secondary = Color(0xFFEEEDEA),
    glass = Color(0xFFFFFFFF).copy(alpha = 0.72f),
    accent = Color(0xFF2196F3),
    background = Color(0xFFFFFFFF),
    surface = Color(0xFFF7F6F4),
    surfaceElevated = Color(0xFFFFFFFF),
    textPrimary = Color(0xFF121212),
    textSecondary = Color(0xFF6F6C66),
    textDisabled = Color(0xFFA6A39C),
    textOnPrimary = Color(0xFFFFFFFF),
    outline = Color(0xFFE2E0DC),
    success = Color(0xFF1B8A4A),
    warning = Color(0xFFF5A524),
    error = Color(0xFFC62828),
    pin = Color(0xFFFF3D00),
    geo = Color(0xFF2196F3),
    scrim = Color(0xFF000000).copy(alpha = 0.4f),
    surfaceBeige = Color(0xFFF4EDE3),
)

internal val RoyDarkColors = RoyColors(
    primaryGradient = RoyPrimaryGradient(
        start = Color(0xFFFF3D00),
        end = Color(0xFFFF1744),
    ),
    primaryHover = Color(0xFFFF6E40),
    primaryPressed = Color(0xFFDD2C00),
    secondary = Color(0xFF2A2A2A),
    glass = Color(0xFF2A2A2A).copy(alpha = 0.72f),
    accent = Color(0xFF2196F3),
    background = Color(0xFF121212),
    surface = Color(0xFF1C1C1C),
    surfaceElevated = Color(0xFF2A2A2A),
    textPrimary = Color(0xFFFFFFFF),
    textSecondary = Color(0xFFA6A39C),
    textDisabled = Color(0xFF4A4844),
    textOnPrimary = Color(0xFFFFFFFF),
    outline = Color(0xFF3A3A3A),
    success = Color(0xFF2EAF62),
    warning = Color(0xFFFFD180),
    error = Color(0xFFEF5350),
    pin = Color(0xFFFF3D00),
    geo = Color(0xFF2196F3),
    scrim = Color(0xFF000000).copy(alpha = 0.4f),
    surfaceBeige = Color(0xFF2A2A2A),
)
