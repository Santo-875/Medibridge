package com.medibridge.core.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary            = TealPrimary,
    onPrimary          = OnTeal,
    primaryContainer   = TealContainer,
    onPrimaryContainer = TealPrimaryDark,

    secondary          = StatusVerified,
    onSecondary        = OnTeal,
    secondaryContainer = StatusVerifiedBg,

    tertiary           = StatusReview,
    onTertiary         = OnTeal,
    tertiaryContainer  = StatusReviewBg,

    error              = StatusConflict,
    onError            = OnTeal,
    errorContainer     = StatusConflictBg,

    background         = LightBackground,
    onBackground       = LightOnBackground,

    surface            = LightSurface,
    onSurface          = LightOnSurface,
    surfaceVariant     = LightSurfaceVariant,
    onSurfaceVariant   = LightOnSurface,
    outline            = LightOutline,
    outlineVariant     = LightOutline
)

private val DarkColorScheme = darkColorScheme(
    primary            = TealPrimary,
    onPrimary          = OnTeal,
    primaryContainer   = TealContainerDark,
    onPrimaryContainer = TealContainer,

    secondary          = StatusVerifiedDark,
    onSecondary        = DarkBackground,
    secondaryContainer = StatusVerifiedBgDark,

    tertiary           = StatusReviewDark,
    onTertiary         = DarkBackground,
    tertiaryContainer  = StatusReviewBgDark,

    error              = StatusConflictDark,
    onError            = DarkBackground,
    errorContainer     = StatusConflictBgDark,

    background         = DarkBackground,
    onBackground       = DarkOnBackground,

    surface            = DarkSurface,
    onSurface          = DarkOnSurface,
    surfaceVariant     = DarkSurfaceVariant,
    onSurfaceVariant   = DarkOnSurface,
    outline            = DarkOutline,
    outlineVariant     = DarkOutline
)

@Composable
fun MediBridgeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.surface.toArgb()
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !darkTheme
            insetsController.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        shapes      = MediBridgeShapes,
        typography  = MediBridgeTypography,
        content     = content
    )
}
