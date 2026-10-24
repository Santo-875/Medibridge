package com.medibridge.moduleD_shell.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.medibridge.core.model.MedicationObject
import com.medibridge.core.theme.*

enum class DoseActionState {
    NOT_TAKEN, TAKEN, SNOOZED
}

@Composable
fun MediCard(
    medication: MedicationObject,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    actionState: DoseActionState = DoseActionState.NOT_TAKEN,
    onMarkTaken: (() -> Unit)? = null,
    onSnooze: (() -> Unit)? = null,
    onMarkNotTaken: (() -> Unit)? = null
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
            actionState == DoseActionState.TAKEN -> if (isDark) StatusVerifiedDark.copy(alpha = 0.45f) else StatusVerified.copy(alpha = 0.35f)
            medication.hasRealConflict -> if (isDark) StatusConflictDark.copy(alpha = 0.5f) else StatusConflict.copy(alpha = 0.4f)
            medication.needsVerification && !medication.verifiedByUser -> if (isDark) StatusReviewDark.copy(alpha = 0.45f) else StatusReview.copy(alpha = 0.35f)
            else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)
        },
        animationSpec = tween(300),
        label = "mediCardBorder"
    )

    val cardBgColor by animateColorAsState(
        targetValue = when (actionState) {
            DoseActionState.TAKEN -> if (isDark) Color(0xFF16251E) else Color(0xFFF3FAF6)
            DoseActionState.SNOOZED -> if (isDark) Color(0xFF261D12) else Color(0xFFFFFBEB)
            DoseActionState.NOT_TAKEN -> MaterialTheme.colorScheme.surface
        },
        animationSpec = tween(300),
        label = "mediCardBg"
    )

    val status = when {
        medication.hasRealConflict -> StatusType.Conflict
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
        tonalElevation = if (actionState == DoseActionState.TAKEN) 1.dp else 2.dp,
        shadowElevation = if (actionState == DoseActionState.TAKEN) 0.dp else 1.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = when (actionState) {
                        DoseActionState.TAKEN -> (if (isDark) StatusVerifiedDark else StatusVerified).copy(alpha = 0.16f)
                        DoseActionState.SNOOZED -> (if (isDark) StatusReviewDark else StatusReview).copy(alpha = 0.16f)
                        DoseActionState.NOT_TAKEN -> TealPrimary.copy(alpha = 0.12f)
                    },
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = when (actionState) {
                                DoseActionState.TAKEN -> Icons.Default.Check
                                DoseActionState.SNOOZED -> Icons.Default.Snooze
                                DoseActionState.NOT_TAKEN -> Icons.Default.Medication
                            },
                            contentDescription = null,
                            tint = when (actionState) {
                                DoseActionState.TAKEN -> if (isDark) StatusVerifiedDark else StatusVerified
                                DoseActionState.SNOOZED -> if (isDark) StatusReviewDark else StatusReview
                                DoseActionState.NOT_TAKEN -> TealPrimary
                            },
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = medication.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    val doseText = listOfNotNull(
                        medication.strength.takeIf { it.isNotBlank() },
                        medication.dose.takeIf { it.isNotBlank() }
                    ).joinToString(" • ")
                    if (doseText.isNotBlank()) {
                        Text(
                            text = doseText,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                StatusChip(status = status)
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val timeLabel = medication.schedule.firstOrNull()?.let { slot ->
                    "${slot.slot} (${slot.time})"
                } ?: medication.timing.ifEmpty { medication.frequency }.ifEmpty { "Daily" }

                MetadataPill(
                    icon = Icons.Default.Schedule,
                    text = timeLabel
                )

                if (medication.duration.isNotBlank()) {
                    MetadataPill(
                        icon = Icons.Default.CalendarToday,
                        text = medication.duration
                    )
                }
            }

            if (onMarkTaken != null || onSnooze != null || onMarkNotTaken != null) {
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
                )
                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val isTakenSelected = actionState == DoseActionState.TAKEN
                    val isSnoozedSelected = actionState == DoseActionState.SNOOZED
                    val isNotTakenSelected = actionState == DoseActionState.NOT_TAKEN

                    val takenScale by animateFloatAsState(if (isTakenSelected) 1.02f else 1.0f, label = "takenScale")
                    val snoozeScale by animateFloatAsState(if (isSnoozedSelected) 1.02f else 1.0f, label = "snoozeScale")
                    val notTakenScale by animateFloatAsState(if (isNotTakenSelected) 1.02f else 1.0f, label = "notTakenScale")

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isTakenSelected) {
                            if (isDark) StatusVerifiedBgDark else StatusVerifiedBg
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        },
                        border = BorderStroke(
                            1.dp,
                            if (isTakenSelected) (if (isDark) StatusVerifiedDark else StatusVerified) else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .scale(takenScale)
                            .clickable(enabled = onMarkTaken != null) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onMarkTaken?.invoke()
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = if (isTakenSelected) (if (isDark) StatusVerifiedDark else StatusVerified) else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "Taken",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (isTakenSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isTakenSelected) (if (isDark) StatusVerifiedDark else StatusVerified) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSnoozedSelected) {
                            if (isDark) StatusReviewBgDark else StatusReviewBg
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        },
                        border = BorderStroke(
                            1.dp,
                            if (isSnoozedSelected) (if (isDark) StatusReviewDark else StatusReview) else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .scale(snoozeScale)
                            .clickable(enabled = onSnooze != null) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSnooze?.invoke()
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Snooze,
                                contentDescription = null,
                                tint = if (isSnoozedSelected) (if (isDark) StatusReviewDark else StatusReview) else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "Snooze",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (isSnoozedSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSnoozedSelected) (if (isDark) StatusReviewDark else StatusReview) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isNotTakenSelected) {
                            MaterialTheme.colorScheme.surfaceVariant
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        },
                        border = BorderStroke(
                            1.dp,
                            if (isNotTakenSelected) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .scale(notTakenScale)
                            .clickable(enabled = onMarkNotTaken != null) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onMarkNotTaken?.invoke()
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = null,
                                tint = if (isNotTakenSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "Not Taken",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (isNotTakenSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isNotTakenSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
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
