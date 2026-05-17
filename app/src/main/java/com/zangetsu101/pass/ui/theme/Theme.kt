package com.zangetsu101.pass.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable

@Composable
fun PassTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = PassDarkColorScheme,
        shapes = PassShapes,
        typography =
            Typography(
                bodyLarge = PassType.Body,
                bodyMedium = PassType.Body,
                bodySmall = PassType.Caption,
                labelLarge = PassType.Label,
                labelMedium = PassType.Label,
                labelSmall = PassType.Caption,
                titleLarge = PassType.Title,
                titleMedium = PassType.Title,
                titleSmall = PassType.Body,
                headlineLarge = PassType.Display,
                headlineMedium = PassType.Display,
                headlineSmall = PassType.Title,
                displayLarge = PassType.Display,
                displayMedium = PassType.Display,
                displaySmall = PassType.Display,
            ),
        content = content,
    )
}
