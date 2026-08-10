package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = CyberGreen,
    onPrimary = CyberDark,
    primaryContainer = CyberMutedGreen,
    onPrimaryContainer = CyberBrightGreen,
    secondary = CyberCyan,
    onSecondary = CyberDark,
    secondaryContainer = CyberGrey,
    onSecondaryContainer = CyberCyan,
    tertiary = CyberPink,
    onTertiary = CyberDark,
    tertiaryContainer = CyberSurfaceVariant,
    onTertiaryContainer = CyberPink,
    error = CyberCrimson,
    onError = CyberDark,
    errorContainer = CyberSurfaceVariant,
    onErrorContainer = CyberCrimson,
    background = CyberDark,
    onBackground = CyberGreen,
    surface = CyberCardBg,
    onSurface = CyberBrightGreen,
    surfaceVariant = CyberSurfaceVariant,
    onSurfaceVariant = CyberMutedText,
    outline = CyberBorder,
    outlineVariant = CyberBorderLight
)

private val LightColorScheme = DarkColorScheme

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = DarkColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

