package com.yourteam.sahara.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = TealPrimaryDark,
    onPrimary = TextPrimary,
    primaryContainer = TealContainerDark,
    onPrimaryContainer = DarkTextPrimary,
    secondary = GreenSecondaryDark,
    onSecondary = TextPrimary,
    secondaryContainer = GreenContainerDark,
    onSecondaryContainer = DarkTextPrimary,
    tertiary = AmberAccentDark,
    onTertiary = TextPrimary,
    tertiaryContainer = AmberContainerDark,
    onTertiaryContainer = DarkTextPrimary,
    background = DarkBackground,
    onBackground = DarkTextPrimary,
    surface = DarkSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkTextSecondary,
    outline = DarkCardBorderColor,
    error = ErrorRed
)

private val LightColorScheme = lightColorScheme(
    primary = TealPrimary,
    onPrimary = OnTealPrimary,
    primaryContainer = TealContainer,
    onPrimaryContainer = TextPrimary,
    secondary = GreenSecondary,
    onSecondary = OnGreenSecondary,
    secondaryContainer = GreenContainer,
    onSecondaryContainer = TextPrimary,
    tertiary = AmberAccent,
    onTertiary = TextPrimary,
    tertiaryContainer = AmberContainer,
    onTertiaryContainer = TextPrimary,
    background = WarmBackground,
    onBackground = TextPrimary,
    surface = WarmSurface,
    onSurface = TextPrimary,
    surfaceVariant = WarmSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    outline = CardBorderColor,
    error = ErrorRed
)

@Composable
fun SaharaTheme(
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
