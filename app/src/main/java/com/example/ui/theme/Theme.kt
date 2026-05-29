package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = CipherPrimary,
    secondary = CipherSecondary,
    tertiary = CipherTertiary,
    background = CipherBackground,
    surface = CipherSurface,
    onPrimary = Color(0xFF381E72), // Rich Dark Purple for text/icons on Primary bg (#D0BCFF)
    onSecondary = Color(0xFF1D192B), // Very Dark Slate Grey-Purple for text/icons on Secondary bg (#E8DEF8)
    onBackground = CipherOnBackground,
    onSurface = CipherOnSurface,
    error = CipherError,
    surfaceVariant = CipherSurfaceVariant
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true, // Force Dark Security Theme for immersive E2EE vibes!
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
