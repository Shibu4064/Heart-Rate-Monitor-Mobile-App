package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme =
  darkColorScheme(
    primary = ElegantPrimary,
    onPrimary = ElegantOnPrimary,
    secondary = ElegantOnSecondaryContainer,
    secondaryContainer = ElegantSecondaryContainer,
    onSecondaryContainer = ElegantOnSecondaryContainer,
    background = ElegantBackground,
    onBackground = ElegantOnBackground,
    surface = ElegantSurface,
    onSurface = ElegantOnSurface,
    surfaceVariant = ElegantSurfaceVariant,
    onSurfaceVariant = ElegantOnSurfaceVariant,
    outline = ElegantOutline,
    outlineVariant = ElegantOutlineVariant,
    error = ElegantError,
    errorContainer = ElegantErrorContainer,
    onErrorContainer = ElegantOnErrorContainer
  )

private val LightColorScheme =
  darkColorScheme( // Keep light colors as Dark for supreme elegant consistency
    primary = ElegantPrimary,
    onPrimary = ElegantOnPrimary,
    secondary = ElegantOnSecondaryContainer,
    secondaryContainer = ElegantSecondaryContainer,
    onSecondaryContainer = ElegantOnSecondaryContainer,
    background = ElegantBackground,
    onBackground = ElegantOnBackground,
    surface = ElegantSurface,
    onSurface = ElegantOnSurface,
    surfaceVariant = ElegantSurfaceVariant,
    onSurfaceVariant = ElegantOnSurfaceVariant,
    outline = ElegantOutline,
    outlineVariant = ElegantOutlineVariant,
    error = ElegantError,
    errorContainer = ElegantErrorContainer,
    onErrorContainer = ElegantOnErrorContainer
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = true, // Force Dark theme by default for Elegant Dark styling
  // Dynamic color behaves nicely by default, but we default to false to show our handcrafted colors
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }

      darkTheme -> DarkColorScheme
      else -> LightColorScheme
    }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
