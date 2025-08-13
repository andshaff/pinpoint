package com.studio32b.pinpoint.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF1E88E5),
    secondary = Color(0xFFFFB300),
    tertiary = Color(0xFFE53935),
    background = Color(0xFF121212),
    surface = Color(0xFF1F1F1F),
    onPrimary = Color.White,
    onSecondary = Color.Black,
    onBackground = Color(0xFFE0E0E0),
    onSurface = Color(0xFFE0E0E0)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF6B7280),
    secondary = Color(0xFFFFB300),
    tertiary = Color(0xFFE53935),
    background = Color(0xFFFFFFFF),
    surface = Color(0xFFFFFFFF),
    onPrimary = Color.White,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F)
)

@Composable
fun PinPointTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = androidx.compose.material3.Typography(
            bodyMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Normal),
            titleLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold),
            headlineMedium = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold)
        ),
        content = content
    )
}
