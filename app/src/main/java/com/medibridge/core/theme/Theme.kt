package com.medibridge.core.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// ─────────────────────────────────────────────────────────────────────────────
// MediBridge Material3 Color Schemes
//
// IMPORTANT: Every module composable MUST be wrapped inside MediBridgeTheme.
// Never use MaterialTheme.colorScheme values hardcoded — always reference
// these role names (primary, background, surface, etc.) so dark/light mode
// switches propagate automatically.
// ─────────────────────────────────────────────────────────────────────────────

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

/**
 * Root theme composable — wrap your entire app (and every module preview) in this.
 *
 * @param darkTheme  Controlled by SettingsViewModel; defaults to system setting.
 * @param content    The composable tree to theme.
 */
@Composable
fun MediBridgeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    // Sync the system status bar color with the theme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.primary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography  = MediBridgeTypography,
        content     = content
    )
}
