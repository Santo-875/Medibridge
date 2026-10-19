package com.medibridge.moduleD_shell.ui.components

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

enum class AppThemeMode(val title: String, val icon: ImageVector) {
    Light("Light", Icons.Default.LightMode),
    Dark("Dark", Icons.Default.DarkMode),
    System("System", Icons.Default.BrightnessAuto)
}

@Composable
fun ThemeSelector(
    isDark: Boolean,
    onThemeChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val systemInDark = isSystemInDarkTheme()
    var selectedMode by remember(isDark) {
        mutableStateOf(if (isDark) AppThemeMode.Dark else AppThemeMode.Light)
    }

    SegmentedToggle(
        items = listOf(AppThemeMode.Light, AppThemeMode.Dark, AppThemeMode.System),
        selectedItem = selectedMode,
        onItemSelected = { mode ->
            selectedMode = mode
            when (mode) {
                AppThemeMode.Light -> onThemeChanged(false)
                AppThemeMode.Dark -> onThemeChanged(true)
                AppThemeMode.System -> onThemeChanged(systemInDark)
            }
        },
        modifier = modifier,
        label = { it.title }
    )
}
