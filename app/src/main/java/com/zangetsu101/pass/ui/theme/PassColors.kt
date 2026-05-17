package com.zangetsu101.pass.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color

object PassColorsDark {
    val Background = Color(0xFF0B0D0B)
    val Surface = Color(0xFF0F120F)
    val Raised = Color(0xFF131A13)
    val Border = Color(0xFF1D2B1D)
    val Border2 = Color(0xFF243324)
    val Accent = Color(0xFF39FF6B)
    val AccentDim = Color(0x2039FF6B)
    val AccentMid = Color(0x4439FF6B)
    val TextPrimary = Color(0xFFC8E6C9)
    val TextDim = Color(0xFF527A52)
    val TextFaint = Color(0xFF2E4A2E)
    val Danger = Color(0xFFFF5555)
    val Warning = Color(0xFFFFCC44)
}

val PassDarkColorScheme =
    darkColorScheme(
        background = PassColorsDark.Background,
        onBackground = PassColorsDark.TextPrimary,
        surface = PassColorsDark.Surface,
        onSurface = PassColorsDark.TextPrimary,
        surfaceVariant = PassColorsDark.Raised,
        onSurfaceVariant = PassColorsDark.TextDim,
        primary = PassColorsDark.Accent,
        onPrimary = PassColorsDark.Background,
        primaryContainer = PassColorsDark.AccentDim,
        onPrimaryContainer = PassColorsDark.Accent,
        secondary = PassColorsDark.TextDim,
        onSecondary = PassColorsDark.Background,
        secondaryContainer = PassColorsDark.Raised,
        onSecondaryContainer = PassColorsDark.TextPrimary,
        error = PassColorsDark.Danger,
        onError = PassColorsDark.Background,
        outline = PassColorsDark.Border2,
        outlineVariant = PassColorsDark.Border,
        scrim = PassColorsDark.Background,
    )
