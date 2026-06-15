package com.muvio.shared.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Black = Color(0xFF000000)
private val DarkGrey = Color(0xFF121212)
private val SurfaceGrey = Color(0xFF1E1E1E)
private val ElevatedGrey = Color(0xFF2C2C2C)
private val Teal = Color(0xFF00C8A0)
private val TealDim = Color(0xFF009B7C)
private val White = Color(0xFFFFFFFF)
private val OffWhite = Color(0xFFE0E0E0)
private val Error = Color(0xFFCF6679)

private val DarkColors = darkColorScheme(
    primary = Teal,
    onPrimary = Black,
    primaryContainer = TealDim,
    onPrimaryContainer = White,
    secondary = TealDim,
    onSecondary = White,
    background = Black,
    onBackground = White,
    surface = DarkGrey,
    onSurface = White,
    surfaceVariant = SurfaceGrey,
    onSurfaceVariant = OffWhite,
    error = Error,
    outline = ElevatedGrey,
)

private val LightColors = lightColorScheme(
    primary = TealDim,
    onPrimary = White,
    background = White,
    onBackground = Black,
    surface = Color(0xFFF5F5F5),
    onSurface = Black,
    error = Error,
)

@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
