package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = CyanGlow,
    onPrimary = NavyDeep,
    primaryContainer = SurfaceAltDark,
    onPrimaryContainer = GoldSoft,
    secondary = GoldAccent,
    onSecondary = NavyDeep,
    secondaryContainer = Color(0xFF283F54),
    onSecondaryContainer = GoldSoft,
    tertiary = TealAccent,
    background = BgDark,
    onBackground = Color(0xFFEBF2F7),
    surface = SurfaceDark,
    onSurface = Color(0xFFEBF2F7),
    surfaceVariant = SurfaceAltDark,
    onSurfaceVariant = Color(0xFFB3C7D6),
    outline = BorderDark,
    error = Color(0xFFFF6B78),
    errorContainer = Color(0xFF5D1720),
    onErrorContainer = Color(0xFFFFDAD9)
)

private val LightColorScheme = lightColorScheme(
    primary = TealAccent,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD7F0F3),
    onPrimaryContainer = NavyDeep,
    secondary = NavyPrimary,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE1ECF4),
    onSecondaryContainer = NavyDeep,
    tertiary = GoldAccent,
    onTertiary = NavyDeep,
    background = BgLight,
    onBackground = Color(0xFF172033),
    surface = SurfaceLight,
    onSurface = Color(0xFF172033),
    surfaceVariant = SurfaceAltLight,
    onSurfaceVariant = Color(0xFF476174),
    outline = BorderLight,
    error = RedDue,
    onError = Color.White,
    errorContainer = RedDueBg,
    onErrorContainer = RedDueDark
)

@Composable
fun TseBillingTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
