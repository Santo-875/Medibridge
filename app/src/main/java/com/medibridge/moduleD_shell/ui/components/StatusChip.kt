package com.medibridge.moduleD_shell.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.medibridge.core.theme.*

enum class StatusType {
    Verified,
    Review,
    Conflict
}

@Composable
fun StatusChip(
    status: StatusType,
    modifier: Modifier = Modifier,
    customLabel: String? = null
) {
    val isDark = isSystemInDarkTheme()

    val (fg, icon: ImageVector, defaultLabel) = when (status) {
        StatusType.Verified -> {
            val foreground = if (isDark) StatusVerifiedDark else StatusVerified
            Triple(foreground, Icons.Default.CheckCircle, "Verified")
        }
        StatusType.Review -> {
            val foreground = if (isDark) StatusReviewDark else StatusReview
            Triple(foreground, Icons.Default.Warning, "Review")
        }
        StatusType.Conflict -> {
            val foreground = if (isDark) StatusConflictDark else StatusConflict
            Triple(foreground, Icons.Default.ErrorOutline, "Conflict")
        }
    }

    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
        border = BorderStroke(1.dp, fg.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = fg,
                modifier = Modifier.size(13.dp)
            )
            Text(
                text = customLabel ?: defaultLabel,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = fg
            )
        }
    }
}
