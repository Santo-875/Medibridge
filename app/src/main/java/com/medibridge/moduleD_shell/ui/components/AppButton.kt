package com.medibridge.moduleD_shell.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

enum class ButtonVariant {
    Primary,
    Secondary,
    Ghost,
    Destructive,
    Text
}

enum class ButtonSize(val height: Dp, val iconSize: Dp) {
    Small(40.dp, 16.dp),
    Default(48.dp, 20.dp),
    Large(56.dp, 24.dp),
    Icon(48.dp, 22.dp)
}

val AppButtonShape = RoundedCornerShape(14.dp)

@Composable
fun AppButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: ButtonVariant = ButtonVariant.Primary,
    size: ButtonSize = ButtonSize.Default,
    text: String? = null,
    icon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    isSuccess: Boolean = false,
    useHaptic: Boolean = true
) {
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    var showSuccess by remember { mutableStateOf(false) }
    LaunchedEffect(isSuccess) {
        if (isSuccess) {
            showSuccess = true
            delay(1200)
            showSuccess = false
        }
    }

    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled && !isLoading) 0.96f else 1.0f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 400f),
        label = "btnScale"
    )

    val colorScheme = MaterialTheme.colorScheme
    val containerColor = when (variant) {
        ButtonVariant.Primary -> colorScheme.primary
        ButtonVariant.Secondary -> Color.Transparent
        ButtonVariant.Ghost -> Color.Transparent
        ButtonVariant.Destructive -> colorScheme.error
        ButtonVariant.Text -> Color.Transparent
    }

    val contentColor = when (variant) {
        ButtonVariant.Primary -> colorScheme.onPrimary
        ButtonVariant.Secondary -> colorScheme.primary
        ButtonVariant.Ghost -> colorScheme.onSurface
        ButtonVariant.Destructive -> colorScheme.onError
        ButtonVariant.Text -> colorScheme.primary
    }

    val border = when (variant) {
        ButtonVariant.Secondary -> BorderStroke(1.dp, colorScheme.outline.copy(alpha = 0.28f))
        else -> null
    }

    Surface(
        modifier = modifier
            .scale(scale)
            .height(size.height)
            .alpha(if (enabled) 1f else 0.5f)
            .clickable(
                enabled = enabled && !isLoading,
                interactionSource = interactionSource,
                indication = ripple(color = contentColor.copy(alpha = 0.2f))
            ) {
                if (useHaptic) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
                onClick()
            },
        shape = AppButtonShape,
        color = containerColor,
        contentColor = contentColor,
        border = border,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Box(
            modifier = Modifier
                .padding(
                    horizontal = if (size == ButtonSize.Icon) 0.dp else if (size == ButtonSize.Small) 12.dp else 18.dp
                ),
            contentAlignment = Alignment.Center
        ) {
            AnimatedContent(
                targetState = Triple(isLoading, showSuccess, text),
                label = "btnContent"
            ) { (loading, success, label) ->
                when {
                    loading -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(size.iconSize),
                            color = contentColor,
                            strokeWidth = 2.dp
                        )
                    }
                    success -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(size.iconSize),
                                tint = contentColor
                            )
                            if (!label.isNullOrEmpty() && size != ButtonSize.Icon) {
                                Text(
                                    text = "Done",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                    else -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (icon != null) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    modifier = Modifier.size(size.iconSize)
                                )
                            }
                            if (!label.isNullOrEmpty() && size != ButtonSize.Icon) {
                                Text(
                                    text = label,
                                    style = if (size == ButtonSize.Small) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            if (trailingIcon != null) {
                                Icon(
                                    imageVector = trailingIcon,
                                    contentDescription = null,
                                    modifier = Modifier.size(size.iconSize)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: ButtonSize = ButtonSize.Default,
    icon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    isSuccess: Boolean = false
) {
    AppButton(
        onClick = onClick,
        modifier = modifier,
        variant = ButtonVariant.Primary,
        size = size,
        text = text,
        icon = icon,
        trailingIcon = trailingIcon,
        enabled = enabled,
        isLoading = isLoading,
        isSuccess = isSuccess
    )
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: ButtonSize = ButtonSize.Default,
    icon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
    enabled: Boolean = true,
    isLoading: Boolean = false
) {
    AppButton(
        onClick = onClick,
        modifier = modifier,
        variant = ButtonVariant.Secondary,
        size = size,
        text = text,
        icon = icon,
        trailingIcon = trailingIcon,
        enabled = enabled,
        isLoading = isLoading
    )
}

@Composable
fun GhostButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: ButtonSize = ButtonSize.Default,
    icon: ImageVector? = null,
    enabled: Boolean = true
) {
    AppButton(
        onClick = onClick,
        modifier = modifier,
        variant = ButtonVariant.Ghost,
        size = size,
        text = text,
        icon = icon,
        enabled = enabled
    )
}

@Composable
fun DestructiveButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: ButtonSize = ButtonSize.Default,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    isLoading: Boolean = false
) {
    AppButton(
        onClick = onClick,
        modifier = modifier,
        variant = ButtonVariant.Destructive,
        size = size,
        text = text,
        icon = icon,
        enabled = enabled,
        isLoading = isLoading
    )
}
