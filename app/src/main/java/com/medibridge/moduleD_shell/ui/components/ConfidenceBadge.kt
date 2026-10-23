package com.medibridge.moduleD_shell.ui.components

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.medibridge.core.theme.*

@Composable
fun ConfidenceBadge(
    confidence: Float,
    modifier: Modifier = Modifier
) {
    val percent = (confidence.coerceIn(0f, 1f) * 100).toInt()
    val isDark = isSystemInDarkTheme()

    val (bg, fg) = when {
        percent >= 85 -> {
            val background = if (isDark) StatusVerifiedBgDark else StatusVerifiedBg
            val foreground = if (isDark) StatusVerifiedDark else StatusVerified
            background to foreground
        }
        percent >= 60 -> {
            val background = if (isDark) StatusReviewBgDark else StatusReviewBg
            val foreground = if (isDark) StatusReviewDark else StatusReview
            background to foreground
        }
        else -> {
            val background = if (isDark) StatusConflictBgDark else StatusConflictBg
            val foreground = if (isDark) StatusConflictDark else StatusConflict
            background to foreground
        }
    }

    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = bg
    ) {
        Text(
            text = "$percent% Confidence",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = fg,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}
