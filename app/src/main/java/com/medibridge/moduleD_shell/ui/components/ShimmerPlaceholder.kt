package com.medibridge.moduleD_shell.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun rememberShimmerBrush(): Brush {
    val transition = rememberInfiniteTransition(label = "shimmerTransition")
    val translateAnim = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerTranslate"
    )

    val baseColor = MaterialTheme.colorScheme.surfaceVariant
    val highlightColor = MaterialTheme.colorScheme.surface

    return Brush.linearGradient(
        colors = listOf(
            baseColor.copy(alpha = 0.6f),
            highlightColor.copy(alpha = 0.9f),
            baseColor.copy(alpha = 0.6f)
        ),
        start = Offset(x = translateAnim.value - 400f, y = translateAnim.value - 400f),
        end = Offset(x = translateAnim.value, y = translateAnim.value)
    )
}

@Composable
fun ShimmerPlaceholder(
    modifier: Modifier = Modifier,
    height: Dp = 20.dp,
    width: Dp? = null,
    radius: Dp = 8.dp
) {
    val brush = rememberShimmerBrush()
    val shape = RoundedCornerShape(radius)

    val sizeModifier = if (width != null) {
        modifier.width(width).height(height)
    } else {
        modifier.fillMaxWidth().height(height)
    }

    Box(
        modifier = sizeModifier
            .clip(shape)
            .background(brush)
    )
}

@Composable
fun ShimmerCard(
    modifier: Modifier = Modifier,
    height: Dp = 100.dp
) {
    ShimmerPlaceholder(
        modifier = modifier,
        height = height,
        radius = 16.dp
    )
}
