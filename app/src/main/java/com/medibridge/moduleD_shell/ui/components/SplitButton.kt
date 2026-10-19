package com.medibridge.moduleD_shell.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Snooze
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun SplitButton(
    onMainClick: () -> Unit,
    onOptionSelected: (minutes: Int) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 48.dp,
    mainLabel: String = "Snooze"
) {
    val haptic = LocalHapticFeedback.current
    var expanded by remember { mutableStateOf(false) }

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1.0f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 400f),
        label = "splitBtnScale"
    )

    Surface(
        modifier = modifier
            .scale(scale)
            .height(height),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
    ) {
        Row(
            modifier = Modifier.fillMaxHeight(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Main button action
            Row(
                modifier = Modifier
                    .clickable(
                        interactionSource = interactionSource,
                        indication = ripple()
                    ) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onMainClick()
                    }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Snooze,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = mainLabel,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // Vertical divider
            VerticalDivider(
                modifier = Modifier
                    .fillMaxHeight(0.55f)
                    .width(1.dp),
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
            )

            // Dropdown trigger
            val dropdownInteractionSource = remember { MutableInteractionSource() }
            Box(
                modifier = Modifier
                    .clickable(
                        interactionSource = dropdownInteractionSource,
                        indication = ripple()
                    ) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        expanded = !expanded
                    }
                    .padding(horizontal = 8.dp)
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = "Snooze options",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )

                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    shape = RoundedCornerShape(14.dp)
                ) {
                    DropdownMenuItem(
                        text = { Text("10 minutes") },
                        onClick = {
                            expanded = false
                            onOptionSelected(10)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("30 minutes") },
                        onClick = {
                            expanded = false
                            onOptionSelected(30)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("1 hour") },
                        onClick = {
                            expanded = false
                            onOptionSelected(60)
                        }
                    )
                }
            }
        }
    }
}
