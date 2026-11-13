package com.cujammu.pulse.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

private val CampusShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

private val DarkColorScheme = darkColorScheme(
    primary = CUJ_Gold,
    onPrimary = CUJ_NavyDark,
    primaryContainer = CUJ_NavyPrimary,
    onPrimaryContainer = CUJ_GoldLight,
    secondary = CUJ_BlueAccent,
    onSecondary = Color.White,
    background = DarkBg,
    onBackground = TextPrimaryDark,
    surface = DarkSurface,
    onSurface = TextPrimaryDark,
    error = ErrorRed,
    onError = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = CUJ_NavyPrimary,
    onPrimary = Color.White,
    primaryContainer = CUJ_GoldLight,
    onPrimaryContainer = CUJ_NavyDark,
    secondary = CUJ_BlueAccent,
    onSecondary = Color.White,
    background = LightBg,
    onBackground = TextPrimaryLight,
    surface = LightSurface,
    onSurface = TextPrimaryLight,
    error = ErrorRed,
    onError = Color.White
)

@Composable
fun CUJammuPulseTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        shapes = CampusShapes,
        content = content
    )
}
