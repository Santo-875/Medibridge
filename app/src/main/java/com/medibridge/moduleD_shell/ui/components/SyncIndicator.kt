package com.medibridge.moduleD_shell.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.medibridge.core.sync.SyncState

/**
 * Minimalistic Online/Offline Dot Indicator.
 *
 * Displays:
 *  - Green dot alone when online.
 *  - Red dot alone when offline.
 *  - Smooth refreshing/spinning animation when clicked or actively syncing.
 */
@Composable
fun SyncIndicator(
    syncState: SyncState,
    isOnline: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    val isDark = isSystemInDarkTheme()
    var isManualRefreshing by remember { mutableStateOf(false) }
    val isSyncing = syncState == SyncState.SYNCING || isManualRefreshing
    val isEffectivelyOnline = isOnline && syncState != SyncState.OFFLINE

    val infiniteTransition = rememberInfiniteTransition(label = "sync_rotation")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 850, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    // Breathing pulse for the online/offline dot
    val dotAlpha by infiniteTransition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot_alpha"
    )

    val dotColor = when {
        isEffectivelyOnline -> if (isDark) Color(0xFF4ADE80) else Color(0xFF16A34A) // Green dot
        else -> if (isDark) Color(0xFFF87171) else Color(0xFFDC2626) // Red dot
    }

    Box(
        modifier = modifier
            .size(36.dp)
            .clip(CircleShape)
            .clickable {
                isManualRefreshing = true
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        // Subtle background touch container
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (isDark) 0.35f else 0.55f),
            modifier = Modifier.size(28.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (isSyncing) {
                    Icon(
                        imageVector = Icons.Default.Sync,
                        contentDescription = "Syncing",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .size(16.dp)
                            .rotate(rotation)
                    )
                } else {
                    // Dot alone with smooth breathing halo
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(dotColor.copy(alpha = dotAlpha))
                    )
                }
            }
        }
    }

    // Auto-reset manual refreshing flag after 1.5 seconds if syncState is IDLE or OFFLINE
    LaunchedEffect(isManualRefreshing) {
        if (isManualRefreshing) {
            kotlinx.coroutines.delay(1500)
            isManualRefreshing = false
        }
    }
}
