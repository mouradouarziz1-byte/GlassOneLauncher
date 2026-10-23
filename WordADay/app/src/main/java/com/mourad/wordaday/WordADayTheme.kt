package com.mourad.wordaday

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = Color(0xFF5C5EF4),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE7E7FF),
    onPrimaryContainer = Color(0xFF15155F),
    secondary = Color(0xFF7655D7),
    secondaryContainer = Color(0xFFEBDDFF),
    tertiary = Color(0xFFB34B76),
    background = Color(0xFFF8F8FE),
    surface = Color(0xFFF8F8FE),
    surfaceVariant = Color(0xFFE9E9F2),
    outline = Color(0xFF777681)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFC3C2FF),
    onPrimary = Color(0xFF292878),
    primaryContainer = Color(0xFF414194),
    onPrimaryContainer = Color(0xFFE3E1FF),
    secondary = Color(0xFFD1BCFF),
    secondaryContainer = Color(0xFF4D3C78),
    tertiary = Color(0xFFFFB0CB),
    background = Color(0xFF111116),
    surface = Color(0xFF111116),
    surfaceVariant = Color(0xFF29292F),
    outline = Color(0xFF91909A)
)

@Composable
internal fun WordADayTheme(
    mode: ThemeMode,
    dynamicColor: Boolean,
    content: @Composable () -> Unit
) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current

    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && dark -> dynamicDarkColorScheme(context)
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colors,
        typography = MaterialTheme.typography,
        shapes = MaterialTheme.shapes,
        content = content
    )
}
