package com.televisionalternativa.streamsonic_tv.ui.theme

import androidx.compose.runtime.Composable
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

@OptIn(ExperimentalTvMaterial3Api::class)
private val StreamsonicColorScheme = darkColorScheme(
    primary = CyanGlow,
    onPrimary = DarkBackground,
    primaryContainer = CyanGlow.copy(alpha = 0.15f),
    onPrimaryContainer = CyanGlow,
    secondary = PurpleGlow,
    onSecondary = DarkBackground,
    secondaryContainer = PurpleGlow.copy(alpha = 0.15f),
    onSecondaryContainer = PurpleGlow,
    tertiary = PinkGlow,
    onTertiary = DarkBackground,
    tertiaryContainer = PinkGlow.copy(alpha = 0.15f),
    onTertiaryContainer = PinkGlow,
    error = ErrorRed,
    onError = DarkBackground,
    errorContainer = ErrorRed.copy(alpha = 0.15f),
    onErrorContainer = ErrorRed,
    background = DarkBackground,
    onBackground = TextPrimary,
    surface = DarkSurface,
    onSurface = TextPrimary,
    surfaceVariant = CardBackground,
    onSurfaceVariant = TextSecondary,
    border = CardBorder,
    inverseSurface = TextPrimary,
    inverseOnSurface = DarkBackground
)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun StreamsonicTVTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = StreamsonicColorScheme,
        typography = Typography,
        content = content
    )
}
