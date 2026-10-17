package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = CyberCyan,
    onPrimary = Color(0xFF001B2E),
    primaryContainer = CyberBlue,
    onPrimaryContainer = Color.White,
    secondary = CyberElectric,
    onSecondary = Color(0xFF001827),
    background = DeepNavyBg,
    onBackground = MetallicSilver,
    surface = DarkSurface,
    onSurface = MetallicSilver,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = MetallicMuted,
    outline = DarkBorder,
    error = StatusRed,
    onError = Color.White
)

@Composable
fun TESVPNTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    // TESVPN is best experienced in its custom stealth dark mode
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}

// Keep backwards-compatible alias
@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) = TESVPNTheme(content = content)
