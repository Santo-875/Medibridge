package com.medibridge.moduleD_shell.ui

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.medibridge.core.db.AppDatabase
import com.medibridge.core.db.NotificationEntity
import com.medibridge.core.model.MedicationObject
import com.medibridge.core.model.fromEntity
import com.medibridge.core.theme.*
import com.medibridge.moduleC_schedule.viewmodel.ScheduleViewModel
import com.medibridge.moduleD_shell.ui.components.*

enum class ReminderFilter(val label: String) {
    ALL("All"),
    PENDING("Pending"),
    TAKEN("Taken"),
    SKIPPED("Skipped"),
    MISSED("Missed")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemindersScreen(
    onBack: () -> Unit,
    onAddMedication: () -> Unit = {},
    viewModel: ScheduleViewModel = viewModel()
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val db = remember { AppDatabase.getInstance(context) }

    val liveReminders by viewModel.reminders.collectAsState()
    val displayReminders = liveReminders

    val allDbMeds by db.medicationDao().getAllMedications().collectAsState(initial = emptyList())
    val prnMeds: List<MedicationObject> = remember(allDbMeds) {
        allDbMeds.map { it.fromEntity() }.filter {
            com.medibridge.moduleC_schedule.engine.ScheduleEngine.isAsNeeded(it.frequency, it.timing)
        }
    }

    val allAlerts by db.notificationDao().getAllNotifications().collectAsState(initial = emptyList())
    val alertsByMed: Map<String, NotificationEntity> = remember(allAlerts) {
        allAlerts
            .filter { it.type in listOf("WRONG_TIME", "MISSED_DOSE") && !it.isRead }
            .groupBy { it.medicationName }
            .mapValues { (_, list) -> list.maxByOrNull { it.timestamp }!! }
    }

    val takenCount = displayReminders.count { it.status == ReminderStatus.TAKEN }
    val missedCount = displayReminders.count { it.status == ReminderStatus.MISSED }
    val skippedCount = displayReminders.count { it.status == ReminderStatus.SKIPPED }
    val pendingCount = displayReminders.count { it.status == ReminderStatus.PENDING || it.status == ReminderStatus.SNOOZED }
    val totalDoses = displayReminders.size
    val adherencePercent = if (takenCount + missedCount > 0) {
        (takenCount * 100) / (takenCount + missedCount)
    } else if (totalDoses > 0 && takenCount > 0) {
        (takenCount * 100) / totalDoses
    } else {
        0
    }

    var selectedFilter by remember { mutableStateOf(ReminderFilter.ALL) }

    val filteredReminders = remember(displayReminders, selectedFilter) {
        when (selectedFilter) {
            ReminderFilter.ALL -> displayReminders
            ReminderFilter.PENDING -> displayReminders.filter { it.status == ReminderStatus.PENDING || it.status == ReminderStatus.SNOOZED }
            ReminderFilter.TAKEN -> displayReminders.filter { it.status == ReminderStatus.TAKEN }
            ReminderFilter.SKIPPED -> displayReminders.filter { it.status == ReminderStatus.SKIPPED }
            ReminderFilter.MISSED -> displayReminders.filter { it.status == ReminderStatus.MISSED }
        }
    }

    // Interactive refresh animation
    var isRefreshing by remember { mutableStateOf(false) }
    val refreshRotation by animateFloatAsState(
        targetValue = if (isRefreshing) 360f else 0f,
        animationSpec = tween(600, easing = FastOutSlowInEasing),
        finishedListener = { isRefreshing = false },
        label = "refreshAnim"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Schedule & Reminders",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            isRefreshing = true
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            viewModel.refreshSchedule()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh schedule",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.rotate(refreshRotation)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
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
            // ── 1. Adherence Overview Card with interactive counters ───────────
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.16f)),
                    tonalElevation = 2.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Daily Routine Adherence",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (totalDoses > 0) "$takenCount of $totalDoses scheduled doses taken today" else "No doses scheduled for today",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Surface(
                                shape = CircleShape,
                                color = if (totalDoses == 0) MaterialTheme.colorScheme.surfaceVariant else if (adherencePercent >= 80) StatusVerifiedBg else StatusReviewBg
                            ) {
                                Text(
                                    text = if (totalDoses > 0) "$adherencePercent%" else "0%",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (totalDoses == 0) MaterialTheme.colorScheme.onSurfaceVariant else if (adherencePercent >= 80) StatusVerified else StatusReview,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Modern animated linear progress track
                        val animatedProgress by animateFloatAsState(
                            targetValue = if (totalDoses > 0) (takenCount.toFloat() / totalDoses.toFloat()).coerceIn(0f, 1f) else 0f,
                            animationSpec = tween(700, easing = FastOutSlowInEasing),
                            label = "adherence_bar"
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(animatedProgress)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (adherencePercent >= 80) StatusVerified else MaterialTheme.colorScheme.primary)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Interactive Quick Filters
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterStatChip(
                                label = "$takenCount Taken",
                                icon = Icons.Default.CheckCircle,
                                isSelected = selectedFilter == ReminderFilter.TAKEN,
                                activeColor = StatusVerified,
                                activeBg = StatusVerifiedBg,
                                onClick = {
                                    selectedFilter = if (selectedFilter == ReminderFilter.TAKEN) ReminderFilter.ALL else ReminderFilter.TAKEN
                                },
                                modifier = Modifier.weight(1f)
                            )

                            FilterStatChip(
                                label = "$pendingCount Pending",
                                icon = Icons.Default.Schedule,
                                isSelected = selectedFilter == ReminderFilter.PENDING,
                                activeColor = MaterialTheme.colorScheme.primary,
                                activeBg = MaterialTheme.colorScheme.primaryContainer,
                                onClick = {
                                    selectedFilter = if (selectedFilter == ReminderFilter.PENDING) ReminderFilter.ALL else ReminderFilter.PENDING
                                },
                                modifier = Modifier.weight(1f)
                            )

                            if (missedCount > 0) {
                                FilterStatChip(
                                    label = "$missedCount Missed",
                                    icon = Icons.Default.Warning,
                                    isSelected = selectedFilter == ReminderFilter.MISSED,
                                    activeColor = StatusConflict,
                                    activeBg = StatusConflictBg,
                                    onClick = {
                                        selectedFilter = if (selectedFilter == ReminderFilter.MISSED) ReminderFilter.ALL else ReminderFilter.MISSED
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }

            // ── 2. Filter Segmented Toggle ────────────────────────────────────
            item {
                SegmentedToggle(
                    items = listOf(ReminderFilter.ALL, ReminderFilter.PENDING, ReminderFilter.TAKEN, ReminderFilter.MISSED),
                    selectedItem = selectedFilter,
                    onItemSelected = { selectedFilter = it },
                    label = { it.label }
                )
            }

            // ── 3. Grouped Reminder Cards ──────────────────────────────────────
            if (displayReminders.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Default.AlarmOn,
                        title = "No Reminders Scheduled",
                        subtitle = "Prescribed medicines with active timings will appear here automatically. Take control of your daily health routine.",
                        actionLabel = "Add Medicine",
                        onActionClick = onAddMedication
                    )
                }
            } else if (filteredReminders.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Default.EventAvailable,
                        title = "No ${selectedFilter.label} Doses",
                        subtitle = "There are no reminders in this category for today.",
                        actionLabel = "View All Doses",
                        onActionClick = { selectedFilter = ReminderFilter.ALL }
                    )
                }
            } else {
                // Group by time of day
                val morningReminders = filteredReminders.filter { isMorning(it.time) }
                val afternoonReminders = filteredReminders.filter { isAfternoon(it.time) }
                val nightReminders = filteredReminders.filter { isNight(it.time) }

                if (morningReminders.isNotEmpty()) {
                    item {
                        SectionHeader(
                            title = "Morning Doses",
                            badgeCount = morningReminders.size
                        )
                    }
                    items(morningReminders, key = { it.id }) { item ->
                        ReminderCardItem(
                            reminder = item,
                            alert = alertsByMed[item.name],
                            onMarkTaken = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                viewModel.markTaken(item.id, item.name, item.dose)
                            },
                            onSnooze = { delayMin ->
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                viewModel.snooze(item.id, delayMin)
                            },
                            onSkip = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                viewModel.markSkipped(item.id, item.name, item.dose)
                            }
                        )
                    }
                }

                if (afternoonReminders.isNotEmpty()) {
                    item {
                        SectionHeader(
                            title = "Afternoon Doses",
                            badgeCount = afternoonReminders.size
                        )
                    }
                    items(afternoonReminders, key = { it.id }) { item ->
                        ReminderCardItem(
                            reminder = item,
                            alert = alertsByMed[item.name],
                            onMarkTaken = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                viewModel.markTaken(item.id, item.name, item.dose)
                            },
                            onSnooze = { delayMin ->
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                viewModel.snooze(item.id, delayMin)
                            },
                            onSkip = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                viewModel.markSkipped(item.id, item.name, item.dose)
                            }
                        )
                    }
                }

                if (nightReminders.isNotEmpty()) {
                    item {
                        SectionHeader(
                            title = "Evening & Night Doses",
                            badgeCount = nightReminders.size
                        )
                    }
                    items(nightReminders, key = { it.id }) { item ->
                        ReminderCardItem(
                            reminder = item,
                            alert = alertsByMed[item.name],
                            onMarkTaken = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                viewModel.markTaken(item.id, item.name, item.dose)
                            },
                            onSnooze = { delayMin ->
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                viewModel.snooze(item.id, delayMin)
                            },
                            onSkip = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                viewModel.markSkipped(item.id, item.name, item.dose)
                            }
                        )
                    }
                }
            }

            if (prnMeds.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(10.dp))
                    SectionHeader(
                        title = "As-Needed (PRN / SOS) Medicines",
                        badgeCount = prnMeds.size
                    )
                    Text(
                        text = "These medications do not trigger fixed daily alarms. Take and log only when required.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                items(prnMeds, key = { "prn_${it.id}" }) { prnMed ->
                    var doseLoggedRecently by remember { mutableStateOf(false) }
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.16f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.MedicalServices,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            Spacer(Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = prnMed.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = "${prnMed.dose} · ${prnMed.timing.ifBlank { "When required" }}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Spacer(Modifier.width(8.dp))

                            Button(
                                onClick = {
                                    doseLoggedRecently = true
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    viewModel.markTaken("${prnMed.id}_sos_${java.time.LocalDate.now()}", prnMed.name, prnMed.dose)
                                    android.widget.Toast.makeText(context, "Dose logged for ${prnMed.name}", android.widget.Toast.LENGTH_SHORT).show()
                                },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (doseLoggedRecently) StatusVerified else MaterialTheme.colorScheme.primary
                                ),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                    imageVector = if (doseLoggedRecently) Icons.Default.Check else Icons.Default.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = if (doseLoggedRecently) "Logged" else "Log Dose",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(48.dp))
            }
        }
    }
}

@Composable
private fun ReminderCardItem(
    reminder: ReminderItem,
    alert: NotificationEntity?,
    onMarkTaken: () -> Unit,
    onSnooze: (Int) -> Unit,
    onSkip: () -> Unit = {}
) {
    val isPending = reminder.status == ReminderStatus.PENDING || reminder.status == ReminderStatus.SNOOZED
    val isTaken = reminder.status == ReminderStatus.TAKEN
    val isMissed = reminder.status == ReminderStatus.MISSED
    val isSkipped = reminder.status == ReminderStatus.SKIPPED

    val borderColor by animateColorAsState(
        targetValue = when {
            isTaken -> StatusVerified.copy(alpha = 0.35f)
            isMissed -> StatusConflict.copy(alpha = 0.4f)
            isSkipped -> MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
            reminder.status == ReminderStatus.SNOOZED -> StatusReview.copy(alpha = 0.4f)
            else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
        },
        animationSpec = tween(400),
        label = "reminder_border_anim"
    )

    val containerColor by animateColorAsState(
        targetValue = when {
            isTaken -> MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
            isSkipped -> MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
            else -> MaterialTheme.colorScheme.surface
        },
        animationSpec = tween(400),
        label = "reminder_bg_anim"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "pulse_trans")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
        border = BorderStroke(1.dp, borderColor),
        tonalElevation = if (isPending) 2.dp else 1.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(44.dp)
                ) {
                    if (isPending) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    scaleX = pulseScale
                                    scaleY = pulseScale
                                    alpha = pulseAlpha
                                }
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                        )
                    }

                    Surface(
                        shape = CircleShape,
                        color = when {
                            isTaken -> StatusVerifiedBg
                            isMissed -> StatusConflictBg
                            isSkipped -> MaterialTheme.colorScheme.surfaceVariant
                            reminder.status == ReminderStatus.SNOOZED -> StatusReviewBg
                            else -> MaterialTheme.colorScheme.primaryContainer
                        },
                        modifier = Modifier.size(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = when {
                                    isTaken -> Icons.Default.CheckCircle
                                    isMissed -> Icons.Default.Warning
                                    isSkipped -> Icons.Default.RemoveCircleOutline
                                    reminder.status == ReminderStatus.SNOOZED -> Icons.Default.Snooze
                                    else -> Icons.Default.Schedule
                                },
                                contentDescription = null,
                                tint = when {
                                    isTaken -> StatusVerified
                                    isMissed -> StatusConflict
                                    isSkipped -> MaterialTheme.colorScheme.outline
                                    reminder.status == ReminderStatus.SNOOZED -> StatusReview
                                    else -> MaterialTheme.colorScheme.primary
                                },
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = reminder.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${reminder.time} • ${reminder.dose}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                when (reminder.status) {
                    ReminderStatus.TAKEN -> {
                        Surface(
                            shape = CircleShape,
                            color = StatusVerifiedBg
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = StatusVerified,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Taken",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = StatusVerified
                                )
                            }
                        }
                    }
                    ReminderStatus.MISSED -> {
                        Surface(
                            shape = CircleShape,
                            color = StatusConflictBg
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ErrorOutline,
                                    contentDescription = null,
                                    tint = StatusConflict,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Missed",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = StatusConflict
                                )
                            }
                        }
                    }
                    ReminderStatus.SKIPPED -> {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.RemoveCircleOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Skipped",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    }
                    ReminderStatus.SNOOZED -> {
                        Surface(
                            shape = CircleShape,
                            color = StatusReviewBg
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Snooze,
                                    contentDescription = null,
                                    tint = StatusReview,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Snoozed",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = StatusReview
                                )
                            }
                        }
                    }
                    ReminderStatus.PENDING -> {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary)
                                    )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Pending",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }

            if (alert != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = alert.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            if (isPending) {
                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f)
                )
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PrimaryButton(
                        text = "Taken",
                        icon = Icons.Default.Check,
                        onClick = onMarkTaken,
                        size = ButtonSize.Small,
                        modifier = Modifier.weight(1.2f)
                    )

                    SplitButton(
                        mainLabel = "Snooze",
                        height = 40.dp,
                        onMainClick = { onSnooze(15) },
                        onOptionSelected = onSnooze
                    )

                    OutlinedButton(
                        onClick = onSkip,
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.height(40.dp)
                    ) {
                        Text("Skip", style = MaterialTheme.typography.labelSmall)
                    }
                }
            } else if (isTaken) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = StatusVerified,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Dose completed and logged on schedule",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else if (isSkipped) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.RemoveCircleOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Dose skipped for today",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterStatChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    activeColor: Color,
    activeBg: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    Surface(
        modifier = modifier
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            },
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) activeBg else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = if (isSelected) BorderStroke(1.dp, activeColor.copy(alpha = 0.5f)) else null
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) activeColor else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) activeColor else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun StatusBadge(label: String, type: StatusType) {
    val isDark = isSystemInDarkTheme()
    val (bg, fg) = when (type) {
        StatusType.Verified -> (if (isDark) StatusVerifiedBgDark else StatusVerifiedBg) to (if (isDark) StatusVerifiedDark else StatusVerified)
        StatusType.Review -> (if (isDark) StatusReviewBgDark else StatusReviewBg) to (if (isDark) StatusReviewDark else StatusReview)
        StatusType.Conflict -> (if (isDark) StatusConflictBgDark else StatusConflictBg) to (if (isDark) StatusConflictDark else StatusConflict)
    }

    Surface(
        shape = CircleShape,
        color = bg
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = fg,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}

private fun isMorning(timeStr: String): Boolean {
    return timeStr.contains("AM", ignoreCase = true) || timeStr.startsWith("0") || timeStr.startsWith("10") || timeStr.startsWith("11")
}

private fun isAfternoon(timeStr: String): Boolean {
    return timeStr.contains("PM", ignoreCase = true) && (timeStr.startsWith("12") || timeStr.startsWith("1") || timeStr.startsWith("2") || timeStr.startsWith("3") || timeStr.startsWith("4"))
}

private fun isNight(timeStr: String): Boolean {
    return !isMorning(timeStr) && !isAfternoon(timeStr)
}

// ─────────────────────────────────────────────────────────────────────────────
// Shared Contract Data Classes & Dummies (Required for ScheduleViewModel)
// ─────────────────────────────────────────────────────────────────────────────

data class ReminderItem(
    val id: String,
    val medicineName: String,
    val time: String,
    val dose: String,
    val status: ReminderStatus,
    val adherenceStreak: Int = 0,
    val adherencePercent: Int = 100,
    val caretakerPhone: String? = null
) {
    constructor(
        id: String,
        name: String,
        time: String,
        dose: String,
        status: ReminderStatus,
        adherenceStreak: Int = 0,
        adherencePercent: Int = 100
    ) : this(
        id = id,
        medicineName = name,
        time = time,
        dose = dose,
        status = status,
        adherenceStreak = adherenceStreak,
        adherencePercent = adherencePercent,
        caretakerPhone = null
    )

    val name: String get() = medicineName
}

enum class ReminderStatus { PENDING, TAKEN, SNOOZED, SKIPPED, MISSED }

val dummyReminders: List<ReminderItem> = emptyList()
