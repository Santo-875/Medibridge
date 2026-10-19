package com.medibridge.moduleD_shell.ui

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.medibridge.core.db.AppDatabase
import com.medibridge.core.db.NotificationEntity
import com.medibridge.core.model.MedicationObject
import com.medibridge.core.model.fromEntity
import com.medibridge.core.theme.*
import com.medibridge.moduleC_schedule.viewmodel.ScheduleViewModel
import com.medibridge.moduleD_shell.ui.components.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class DaySlot {
    MORNING, AFTERNOON, NIGHT
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onScannerClick: () -> Unit,
    onChatbotClick: () -> Unit,
    onReminderClick: () -> Unit,
    onSafetyClick: () -> Unit = {},
    onRecordClick: () -> Unit = {},
    onOpenDrawer: () -> Unit = {}
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("medibridge_prefs", Context.MODE_PRIVATE) }
    val patientName by remember { mutableStateOf(prefs.getString("patient_name", "Alex") ?: "Alex") }

    val db = remember { AppDatabase.getInstance(context) }
    val roomMedEntities by db.medicationDao().getAllMedications().collectAsState(initial = emptyList())
    val medications = remember(roomMedEntities) {
        roomMedEntities.map { it.fromEntity() }
    }
    val unreadNotificationCount by db.notificationDao().getUnreadCount().collectAsState(initial = 0)
    val scheduleViewModel: ScheduleViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val todayReminders by scheduleViewModel.reminders.collectAsState(initial = emptyList())

    var showNotificationSheet by remember { mutableStateOf(false) }
    var selectedSlot by remember { mutableStateOf(DaySlot.MORNING) }

    // Dynamic greeting based on current time
    val currentHour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    val greeting = when (currentHour) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        else -> "Good evening"
    }
    val formattedDate = remember {
        SimpleDateFormat("EEEE, MMM d", Locale.getDefault()).format(Date())
    }

    // Safety conflict detection
    val conflictingMeds = remember(medications) {
        medications.filter { it.conflicts.isNotEmpty() }
    }
    val hasConflicts = conflictingMeds.isNotEmpty()
    val firstConflict = conflictingMeds.firstOrNull()?.conflicts?.firstOrNull()

    // Next dose determination
    val nextMed = medications.firstOrNull()
    var isNextDoseTaken by remember { mutableStateOf(false) }

    // Adherence calculation
    val takenCount = todayReminders.count { it.status == ReminderStatus.TAKEN }
    val totalScheduled = if (todayReminders.isNotEmpty()) todayReminders.size else medications.size.coerceAtLeast(1)
    val adherencePercent = if (totalScheduled > 0) ((takenCount.toFloat() / totalScheduled.toFloat()) * 100).toInt().coerceIn(0, 100) else 100

    Scaffold(
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.background,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "$greeting, $patientName",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = formattedDate,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    BadgedIconButton(
                        icon = Icons.Default.Notifications,
                        badgeCount = unreadNotificationCount,
                        onClick = { showNotificationSheet = true },
                        contentDescription = "Notifications"
                    )
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── 1. Safety Alert Banner (Only when conflict exists) ─────────────
            if (hasConflicts && firstConflict != null) {
                item {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onSafetyClick),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onError,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Medication Alert",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.error
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = firstConflict.detail.ifEmpty { "Potential interaction detected between prescribed medicines." },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            Text(
                                text = "Review",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }

            // ── 2. First-Launch Empty State OR Next Dose Hero Card ─────────────
            if (medications.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Default.Medication,
                        title = "Add your first medicine",
                        subtitle = "Scan your prescription or bottle label, or dictate instructions in your own words.",
                        actionLabel = "Scan Prescription",
                        onActionClick = onScannerClick
                    )
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        SecondaryButton(
                            text = "Or Speak to Add",
                            icon = Icons.Default.Mic,
                            onClick = onRecordClick
                        )
                    }
                }
            } else {
                // Next Dose Hero Card
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                        tonalElevation = 2.dp
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primaryContainer
                                ) {
                                    Text(
                                        text = "NEXT DOSE • 8:00 AM",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }

                                Text(
                                    text = "In 45 mins",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            Text(
                                text = nextMed?.name ?: "Metformin",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            Spacer(modifier = Modifier.height(2.dp))

                            Text(
                                text = "${nextMed?.strength ?: "500mg"} • ${nextMed?.dose ?: "1 tablet"} • ${nextMed?.timing ?: "After breakfast"}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(18.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                PrimaryButton(
                                    text = if (isNextDoseTaken) "Dose Taken" else "Mark Taken",
                                    icon = Icons.Default.Check,
                                    onClick = {
                                        isNextDoseTaken = true
                                        val slotTime = nextMed?.schedule?.firstOrNull()?.time ?: "08:00"
                                        scheduleViewModel.markTaken("${nextMed?.id}_${slotTime}_today", nextMed?.name, nextMed?.dose)
                                    },
                                    modifier = Modifier.weight(1f)
                                )

                                SplitButton(
                                    mainLabel = "Snooze",
                                    onMainClick = {
                                        val slotTime = nextMed?.schedule?.firstOrNull()?.time ?: "08:00"
                                        scheduleViewModel.snooze("${nextMed?.id}_${slotTime}_today", 15)
                                    },
                                    onOptionSelected = { minutes ->
                                        val slotTime = nextMed?.schedule?.firstOrNull()?.time ?: "08:00"
                                        scheduleViewModel.snooze("${nextMed?.id}_${slotTime}_today", minutes)
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // ── 3. Quick Actions ───────────────────────────────────────────────
            item {
                SectionHeader(title = "Quick Actions")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    QuickActionCard(
                        title = "Scan",
                        subtitle = "Prescription",
                        icon = Icons.Default.DocumentScanner,
                        onClick = onScannerClick,
                        modifier = Modifier.weight(1f)
                    )
                    QuickActionCard(
                        title = "Speak",
                        subtitle = "Voice Note",
                        icon = Icons.Default.Mic,
                        onClick = onRecordClick,
                        modifier = Modifier.weight(1f)
                    )
                    QuickActionCard(
                        title = "Ask Medi",
                        subtitle = "AI Doctor",
                        icon = Icons.Default.AutoAwesome,
                        onClick = onChatbotClick,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // ── 4. Today's Timeline ───────────────────────────────────────────
            item {
                SectionHeader(
                    title = "Today's Schedule",
                    actionLabel = "View All",
                    onActionClick = onReminderClick
                )

                SegmentedToggle(
                    items = listOf(DaySlot.MORNING, DaySlot.AFTERNOON, DaySlot.NIGHT),
                    selectedItem = selectedSlot,
                    onItemSelected = { selectedSlot = it },
                    label = { slot ->
                        when (slot) {
                            DaySlot.MORNING -> "Morning"
                            DaySlot.AFTERNOON -> "Afternoon"
                            DaySlot.NIGHT -> "Night"
                        }
                    }
                )
            }

            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.16f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (medications.isEmpty()) {
                            Text(
                                text = "No doses scheduled for this time slot.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            medications.take(2).forEach { med ->
                                DoseRow(
                                    timing = med.timing.ifEmpty { "8:00 AM" },
                                    dose = med.dose.ifEmpty { "1 dose" },
                                    strength = med.strength,
                                    slotName = med.name,
                                    withFood = true
                                )
                            }
                        }
                    }
                }
            }

            // ── 5. Weekly Adherence 7-Day Graph ───────────────────────────────
            item {
                WeeklyAdherenceGraph(
                    todayTakenCount = takenCount,
                    todayAdherencePercent = adherencePercent
                )
            }

            item {
                Spacer(modifier = Modifier.height(72.dp))
            }
        }
    }

    // Notification bottom sheet
    if (showNotificationSheet) {
        ModalBottomSheet(
            onDismissRequest = { showNotificationSheet = false },
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 36.dp, top = 8.dp)
            ) {
                Text(
                    text = "Notifications & Reminders",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(14.dp))

                val notifications by db.notificationDao().getAllNotifications().collectAsState(initial = emptyList())
                if (notifications.isEmpty()) {
                    EmptyState(
                        icon = Icons.Default.CheckCircle,
                        title = "All Caught Up",
                        subtitle = "You have no unread notifications or missed doses."
                    )
                } else {
                    notifications.take(5).forEach { notif ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Notifications,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = notif.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                PrimaryButton(
                    text = "View All Reminders",
                    onClick = {
                        showNotificationSheet = false
                        onReminderClick()
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun QuickActionCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "qa_card_scale"
    )

    Surface(
        modifier = modifier
            .defaultMinSize(minHeight = 98.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(),
                onClick = onClick
            ),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.16f)),
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.Start
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private data class DayAdherence(
    val dayKey: String,
    val label: String,
    val percent: Int,
    val isToday: Boolean
)

@Composable
private fun WeeklyAdherenceGraph(
    todayTakenCount: Int,
    todayAdherencePercent: Int,
    modifier: Modifier = Modifier
) {
    val cal = remember { Calendar.getInstance() }
    val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
    val todayIndex = when (dayOfWeek) {
        Calendar.MONDAY -> 0
        Calendar.TUESDAY -> 1
        Calendar.WEDNESDAY -> 2
        Calendar.THURSDAY -> 3
        Calendar.FRIDAY -> 4
        Calendar.SATURDAY -> 5
        Calendar.SUNDAY -> 6
        else -> 0
    }

    val days = remember(todayAdherencePercent, todayIndex) {
        listOf(
            DayAdherence("Mon", "M", if (todayIndex == 0) todayAdherencePercent else 100, todayIndex == 0),
            DayAdherence("Tue", "T", if (todayIndex == 1) todayAdherencePercent else 100, todayIndex == 1),
            DayAdherence("Wed", "W", if (todayIndex == 2) todayAdherencePercent else 85, todayIndex == 2),
            DayAdherence("Thu", "T", if (todayIndex == 3) todayAdherencePercent else 100, todayIndex == 3),
            DayAdherence("Fri", "F", if (todayIndex == 4) todayAdherencePercent else 100, todayIndex == 4),
            DayAdherence("Sat", "S", if (todayIndex == 5) todayAdherencePercent else 70, todayIndex == 5),
            DayAdherence("Sun", "S", if (todayIndex == 6) todayAdherencePercent else 90, todayIndex == 6)
        )
    }

    var selectedDay by remember { mutableStateOf<DayAdherence?>(null) }
    val activeDay = selectedDay ?: days[todayIndex]

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.16f)),
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header row with streak badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Weekly Adherence",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${activeDay.dayKey}: ${activeDay.percent}% completed",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "🔥 5-day streak",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // 7-day pill bar chart
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                days.forEach { item ->
                    val isSelected = (selectedDay == item) || (selectedDay == null && item.isToday)
                    val animatedFraction by animateFloatAsState(
                        targetValue = (item.percent / 100f).coerceIn(0.14f, 1f),
                        animationSpec = tween(600, easing = FastOutSlowInEasing),
                        label = "bar_anim_${item.dayKey}"
                    )

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { selectedDay = item }
                    ) {
                        // Value / Icon indicator
                        Box(
                            modifier = Modifier.height(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (item.percent >= 100) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = StatusVerified,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // Pillar Track
                        Box(
                            modifier = Modifier
                                .width(28.dp)
                                .height(56.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                                ),
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight(animatedFraction)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(
                                        when {
                                            item.isToday -> MaterialTheme.colorScheme.primary
                                            item.percent >= 80 -> StatusVerified
                                            else -> StatusReview
                                        }
                                    )
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Day label
                        Text(
                            text = item.label,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (item.isToday || isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (item.isToday || isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Routine Insight Footer
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = StatusVerified,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "22 of 24 doses taken on time this week. Best: Morning routine.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
