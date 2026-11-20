package com.pulse.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object PulseColors {
    val Red = Color(0xFFE5484D)
    val Yellow = Color(0xFFF5A623)
    val Green = Color(0xFF53C22B)
    val Teal = Color(0xFF00A2A7)
    val Blue = Color(0xFF3B82F6)
    val Purple = Color(0xFF8B5CF6)
    val Background = Color(0xFF0A0A0C)
    val Surface = Color(0xFF151519)
    val SurfaceHigh = Color(0xFF1F1F26)
    val OnSurface = Color(0xFFEDEDF2)
    val OnSurfaceDim = Color(0xFF9A9AA5)
}

private val DarkScheme = darkColorScheme(
    primary = PulseColors.Teal,
    onPrimary = Color.Black,
    secondary = PulseColors.Purple,
    background = PulseColors.Background,
    surface = PulseColors.Surface,
    surfaceVariant = PulseColors.SurfaceHigh,
    onBackground = PulseColors.OnSurface,
    onSurface = PulseColors.OnSurface,
    onSurfaceVariant = PulseColors.OnSurfaceDim,
    error = PulseColors.Red
)

@Composable
fun PulseTheme(content: @Composable () -> Unit) {
    isSystemInDarkTheme()
    MaterialTheme(colorScheme = DarkScheme, content = content)
}

fun recoveryColor(score: Int): Color = when {
    score <= 33 -> PulseColors.Red
    score <= 66 -> PulseColors.Yellow
    else -> PulseColors.Green
}

fun strainColor(strain: Double): Color = when {
    strain < 6 -> PulseColors.Teal
    strain < 12 -> PulseColors.Yellow
    else -> PulseColors.Red
}
