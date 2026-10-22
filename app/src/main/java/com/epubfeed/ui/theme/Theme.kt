package com.epubfeed.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.epubfeed.domain.model.ThemeMode

@Composable
fun EpubFeedTheme(
    themeMode: ThemeMode,
    content: @Composable () -> Unit,
) {
    val x = when (themeMode) {
        ThemeMode.LIGHTS_OUT -> LightsOutColors
        ThemeMode.DIM -> DimColors
        ThemeMode.LIGHT -> LightColors
    }
    val darkScheme = themeMode != ThemeMode.LIGHT
    val scheme = if (darkScheme) {
        darkColorScheme(
            primary = x.accent,
            onPrimary = x.onAccent,
            secondary = x.accent,
            background = x.background,
            onBackground = x.text,
            surface = x.surface,
            onSurface = x.text,
            surfaceVariant = x.elevated,
            onSurfaceVariant = x.muted,
            outline = x.divider,
            outlineVariant = x.divider,
            error = ColorTokens.error,
        )
    } else {
        lightColorScheme(
            primary = x.accent,
            onPrimary = x.onAccent,
            secondary = x.accent,
            background = x.background,
            onBackground = x.text,
            surface = x.surface,
            onSurface = x.text,
            surfaceVariant = x.elevated,
            onSurfaceVariant = x.muted,
            outline = x.divider,
            outlineVariant = x.divider,
            error = ColorTokens.error,
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkScheme
                isAppearanceLightNavigationBars = !darkScheme
            }
        }
    }

    CompositionLocalProvider(LocalXColors provides x) {
        MaterialTheme(
            colorScheme = scheme,
            typography = EpubFeedTypography,
            content = content,
        )
    }
}

object ColorTokens {
    val error = androidx.compose.ui.graphics.Color(0xFFF4212E)
}
