package com.medibridge.moduleD_shell.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.medibridge.core.model.MedicationObject
import com.medibridge.core.theme.*

@Composable
fun MediCard(
    medication: MedicationObject,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onTakenClick: (() -> Unit)? = null,
    onSnoozeClick: (() -> Unit)? = null,
    isTaken: Boolean = false
) {
    val haptic = LocalHapticFeedback.current
    val isDark = isSystemInDarkTheme()
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1.0f,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 450f),
        label = "mediCardScale"
    )

    val borderColor by animateColorAsState(
        targetValue = when {
            isTaken -> if (isDark) StatusVerifiedDark.copy(alpha = 0.45f) else StatusVerified.copy(alpha = 0.35f)
            medication.conflicts.isNotEmpty() -> if (isDark) StatusConflictDark.copy(alpha = 0.5f) else StatusConflict.copy(alpha = 0.4f)
            medication.needsVerification && !medication.verifiedByUser -> if (isDark) StatusReviewDark.copy(alpha = 0.45f) else StatusReview.copy(alpha = 0.35f)
            else -> TealPrimary.copy(alpha = 0.25f)
        },
        animationSpec = tween(350),
        label = "mediCardBorder"
    )

    val cardBgColor by animateColorAsState(
        targetValue = if (isTaken) {
            if (isDark) Color(0xFF16251E) else Color(0xFFF3FAF6)
        } else {
            MaterialTheme.colorScheme.surface
        },
        animationSpec = tween(350),
        label = "mediCardBg"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "pendingPulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pendingPulseAlpha"
    )
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pendingPulseScale"
    )

    val status = when {
        medication.conflicts.isNotEmpty() -> StatusType.Conflict
        medication.needsVerification && !medication.verifiedByUser -> StatusType.Review
        else -> StatusType.Verified
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(color = TealPrimary.copy(alpha = 0.12f))
            ) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            },
        shape = RoundedCornerShape(18.dp),
        color = cardBgColor,
        border = BorderStroke(1.2.dp, borderColor),
        tonalElevation = if (isTaken) 1.dp else 2.dp,
        shadowElevation = if (isTaken) 0.dp else 1.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header: Icon + Name & Strength + StatusChip
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isTaken) {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                    } else {
                        TealPrimary.copy(alpha = 0.12f)
                    },
                    modifier = Modifier.size(46.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isTaken) Icons.Default.Check else Icons.Default.Medication,
                            contentDescription = null,
                            tint = if (isTaken) {
                                if (isDark) StatusVerifiedDark else StatusVerified
                            } else {
                                TealPrimary
                            },
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = medication.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (medication.strength.isNotEmpty()) {
                        Text(
                            text = medication.strength,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                StatusChip(status = status)
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Dose Indicators & Metadata Chips Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // TAKEN vs PENDING Indicator Badge
                AnimatedContent(
                    targetState = isTaken,
                    transitionSpec = {
                        (fadeIn(tween(300)) + scaleIn(tween(300))).togetherWith(
                            fadeOut(tween(200)) + scaleOut(tween(200))
                        )
                    },
                    label = "doseStatusIndicator"
                ) { taken ->
                    if (taken) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                            border = BorderStroke(1.dp, (if (isDark) StatusVerifiedDark else StatusVerified).copy(alpha = 0.35f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = if (isDark) StatusVerifiedDark else StatusVerified,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "Taken Today",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isDark) StatusVerifiedDark else StatusVerified
                                )
                            }
                        }
                    } else {
                        Box(contentAlignment = Alignment.Center) {
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .graphicsLayer {
                                        scaleX = pulseScale
                                        scaleY = pulseScale
                                        alpha = pulseAlpha
                                    }
                                    .clip(CircleShape)
                                    .background((if (isDark) StatusReviewDark else StatusReview).copy(alpha = 0.12f))
                            )
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                border = BorderStroke(1.dp, (if (isDark) StatusReviewDark else StatusReview).copy(alpha = 0.35f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(7.dp)
                                            .clip(CircleShape)
                                            .background(if (isDark) StatusReviewDark else StatusReview)
                                    )
                                    Text(
                                        text = "Pending Dose",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isDark) StatusReviewDark else StatusReview
                                    )
                                }
                            }
                        }
                    }
                }

                // Timing / When Pill Chip
                MetadataPill(
                    icon = Icons.Default.Schedule,
                    text = medication.timing.ifEmpty { medication.frequency }.ifEmpty { "Daily" }
                )

                // Dose Pill Chip
                MetadataPill(
                    icon = Icons.Default.Vaccines,
                    text = medication.dose.ifEmpty { "1 dose" }
                )
            }

            // Quick Actions: Taken Toggle & Snooze
            if (onTakenClick != null || onSnoozeClick != null) {
                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.14f)
                )
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (onTakenClick != null) {
                        AnimatedContent(
                            targetState = isTaken,
                            modifier = Modifier.weight(1f),
                            transitionSpec = {
                                (fadeIn(tween(250)) + slideInVertically()).togetherWith(
                                    fadeOut(tween(200)) + slideOutVertically()
                                )
                            },
                            label = "takenButtonAnim"
                        ) { taken ->
                            if (taken) {
                                SecondaryButton(
                                    text = "Taken ✓  (Tap to Undo)",
                                    icon = Icons.Default.CheckCircle,
                                    onClick = onTakenClick,
                                    size = ButtonSize.Small,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            } else {
                                PrimaryButton(
                                    text = "Mark as Taken",
                                    icon = Icons.Default.Check,
                                    onClick = onTakenClick,
                                    size = ButtonSize.Small,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }

                    if (onSnoozeClick != null && !isTaken) {
                        SecondaryButton(
                            text = "Snooze",
                            icon = Icons.Default.Snooze,
                            onClick = onSnoozeClick,
                            size = ButtonSize.Small,
                            modifier = Modifier.weight(0.5f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MetadataPill(
    icon: ImageVector,
    text: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.16f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(12.dp)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}
