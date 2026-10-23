package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DualSenseColorScheme = darkColorScheme(
    primary = DualSenseAccentCyan,
    onPrimary = Color.Black,
    primaryContainer = DualSenseAccentBlue,
    onPrimaryContainer = Color.White,
    secondary = DualSenseAccentBlue,
    onSecondary = Color.White,
    background = DualSenseDarkSurface,
    onBackground = DualSenseWhite,
    surface = DualSenseCardBackground,
    onSurface = DualSenseWhite
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DualSenseColorScheme,
        typography = Typography,
        content = content
    )
}
