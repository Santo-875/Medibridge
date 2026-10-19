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
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.medibridge.core.db.AppDatabase
import com.medibridge.core.db.NotificationEntity
import com.medibridge.moduleC_schedule.viewmodel.ScheduleViewModel

/**
 * Filter categories for the Reminders screen.
 */
enum class ReminderFilter(val label: String) {
    ALL("All"),
    PENDING("Pending"),
    TAKEN("Taken"),
    MISSED("Missed")
}

/**
 * RemindersScreen — Clean, human-friendly, interactive medication reminders & adherence tracker.
 *
 * Features:
 *   • Uncongested layout with interactive filter tabs (All, Pending, Taken, Missed)
 *   • Elegant circular Adherence meter & streak cards
 *   • Smooth interactive card animations for marking Taken, Snooze, or Missed
 *   • Caretaker escalation status linked to preferences
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemindersScreen(
    onBack: () -> Unit,
    onAddMedication: () -> Unit = {},
    viewModel: ScheduleViewModel = viewModel()
) {
    val context = LocalContext.current
    val db = remember { AppDatabase.getInstance(context) }
    val prefs = remember { context.getSharedPreferences("medibridge_prefs", Context.MODE_PRIVATE) }
    val caretakerPhone = remember { prefs.getString("caretaker_phone", "") ?: "" }

    val liveReminders by viewModel.reminders.collectAsState()
    val displayReminders = if (liveReminders.isNotEmpty()) liveReminders else dummyReminders

    // Reactive per-medication alerts
    val allAlerts by db.notificationDao().getAllNotifications().collectAsState(initial = emptyList())
    val alertsByMed: Map<String, NotificationEntity> = remember(allAlerts) {
        allAlerts
            .filter { it.type in listOf("WRONG_TIME", "MISSED_DOSE") && !it.isRead }
            .groupBy { it.medicationName }
            .mapValues { (_, list) -> list.maxByOrNull { it.timestamp }!! }
    }

    val takenCount = displayReminders.count { it.status == ReminderStatus.TAKEN }
    val missedCount = displayReminders.count { it.status == ReminderStatus.MISSED }
    val pendingCount = displayReminders.count { it.status == ReminderStatus.PENDING }
    val totalDoses = displayReminders.size
    val adherencePercent = if (takenCount + missedCount > 0) {
        (takenCount * 100) / (takenCount + missedCount)
    } else {
        100
    }

    var selectedFilter by remember { mutableStateOf(ReminderFilter.ALL) }

    // Smooth filter items
    val filteredReminders = remember(displayReminders, selectedFilter) {
        when (selectedFilter) {
            ReminderFilter.ALL -> displayReminders
            ReminderFilter.PENDING -> displayReminders.filter { it.status == ReminderStatus.PENDING }
            ReminderFilter.TAKEN -> displayReminders.filter { it.status == ReminderStatus.TAKEN }
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
                    Column {
                        Text(
                            text = "Medication Reminders",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Text(
                            text = "Daily Schedule & Adherence",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.15f),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onAddMedication) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.15f),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Filled.Add,
                                    contentDescription = "Add Medication",
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                    IconButton(
                        onClick = {
                            isRefreshing = true
                            viewModel.refreshSchedule()
                        }
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.15f),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Filled.Refresh,
                                    contentDescription = "Refresh Schedule",
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier
                                        .size(18.dp)
                                        .rotate(refreshRotation)
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddMedication,
                containerColor = Color(0xFF1E88E5),
                contentColor = Color.White,
                shape = RoundedCornerShape(16.dp),
                icon = { Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(20.dp)) },
                text = { Text("Add Dose", fontWeight = FontWeight.Bold) }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── TOP ADHERENCE DASHBOARD CARD ──────────────────────────────────
            item {
                AdherenceProgressCard(
                    takenCount = takenCount,
                    missedCount = missedCount,
                    pendingCount = pendingCount,
                    totalDoses = totalDoses,
                    adherencePercent = adherencePercent,
                    caretakerPhone = caretakerPhone
                )
            }

            // ── INTERACTIVE FILTER TABS ROW ───────────────────────────────────
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Today's Schedule",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "${filteredReminders.size} of $totalDoses Doses",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.height(8.dp))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(ReminderFilter.values()) { filter ->
                        val count = when (filter) {
                            ReminderFilter.ALL -> totalDoses
                            ReminderFilter.PENDING -> pendingCount
                            ReminderFilter.TAKEN -> takenCount
                            ReminderFilter.MISSED -> missedCount
                        }
                        val isSelected = selectedFilter == filter
                        val filterColor = when (filter) {
                            ReminderFilter.ALL -> Color(0xFF1E88E5)
                            ReminderFilter.PENDING -> Color(0xFFE65100)
                            ReminderFilter.TAKEN -> Color(0xFF2E7D32)
                            ReminderFilter.MISSED -> Color(0xFFC62828)
                        }

                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = if (isSelected) filterColor else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            border = if (!isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)) else null,
                            modifier = Modifier.clickable { selectedFilter = filter }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                            ) {
                                Text(
                                    text = filter.label,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.width(6.dp))
                                Surface(
                                    shape = CircleShape,
                                    color = if (isSelected) Color.White.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surface
                                ) {
                                    Text(
                                        text = "$count",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ── LIST OF REMINDER CARDS ─────────────────────────────────────────
            if (filteredReminders.isEmpty()) {
                item {
                    EmptyRemindersCard(
                        filter = selectedFilter,
                        onAddMedication = onAddMedication
                    )
                }
            } else {
                items(filteredReminders, key = { it.id }) { reminder ->
                    ReminderCard(
                        reminder = reminder,
                        alertEntity = alertsByMed[reminder.medicineName],
                        onTaken = {
                            viewModel.markTaken(
                                reminderId = reminder.id,
                                medicineName = reminder.medicineName,
                                dose = reminder.dose
                            )
                        },
                        onSnooze = {
                            viewModel.snooze(reminder.id, 15)
                        },
                        onMissed = {
                            viewModel.markMissed(
                                reminderId = reminder.id,
                                medicineName = reminder.medicineName,
                                dose = reminder.dose
                            )
                        }
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Adherence Progress Card (Uncongested, Human-Friendly Overview)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AdherenceProgressCard(
    takenCount: Int,
    missedCount: Int,
    pendingCount: Int,
    totalDoses: Int,
    adherencePercent: Int,
    caretakerPhone: String
) {
    val animatedProgress by animateFloatAsState(
        targetValue = if (totalDoses > 0) takenCount.toFloat() / totalDoses.toFloat() else 0f,
        animationSpec = tween(900, easing = FastOutSlowInEasing),
        label = "adherenceBar"
    )

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            // Header row with Adherence Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFFE8F5E9),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF2E7D32),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Daily Adherence",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "$takenCount of $totalDoses doses completed",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (adherencePercent >= 80) Color(0xFFE8F5E9) else Color(0xFFFFF3E0)
                ) {
                    Text(
                        text = "$adherencePercent%",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (adherencePercent >= 80) Color(0xFF2E7D32) else Color(0xFFE65100),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // Progress Bar
            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
                color = Color(0xFF2E7D32),
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            Spacer(Modifier.height(16.dp))

            // 3 Stat Pills (Taken, Pending, Missed)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatPill(
                    label = "Taken",
                    count = takenCount,
                    icon = Icons.Filled.Check,
                    containerColor = Color(0xFFE8F5E9),
                    contentColor = Color(0xFF2E7D32),
                    modifier = Modifier.weight(1f)
                )
                StatPill(
                    label = "Pending",
                    count = pendingCount,
                    icon = Icons.Filled.Schedule,
                    containerColor = Color(0xFFFFF8E1),
                    contentColor = Color(0xFFE65100),
                    modifier = Modifier.weight(1f)
                )
                StatPill(
                    label = "Missed",
                    count = missedCount,
                    icon = Icons.Filled.Close,
                    containerColor = Color(0xFFFFEBEE),
                    contentColor = Color(0xFFC62828),
                    modifier = Modifier.weight(1f)
                )
            }

            if (caretakerPhone.isNotBlank()) {
                Spacer(Modifier.height(14.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Security,
                            contentDescription = null,
                            tint = Color(0xFF1E88E5),
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Auto-Escalation Link Active: $caretakerPhone",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatPill(
    label: String,
    count: Int,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = containerColor,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = contentColor)
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = "$count",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = contentColor
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Interactive Reminder Card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ReminderCard(
    reminder: ReminderItem,
    alertEntity: NotificationEntity? = null,
    onTaken: () -> Unit = {},
    onSnooze: () -> Unit = {},
    onMissed: () -> Unit = {}
) {
    var showAlertDetail by remember { mutableStateOf(false) }
    val isDark = isSystemInDarkTheme()

    // Alert dialog
    if (showAlertDetail && alertEntity != null) {
        AlertDialog(
            onDismissRequest = { showAlertDetail = false },
            icon = {
                Icon(
                    imageVector = if (alertEntity.type == "WRONG_TIME") Icons.Filled.Schedule else Icons.Filled.Warning,
                    contentDescription = null,
                    tint = if (alertEntity.type == "WRONG_TIME") Color(0xFFE65100) else Color(0xFFC62828)
                )
            },
            title = {
                Text(
                    text = if (alertEntity.type == "WRONG_TIME") "Wrong Time Alert" else "Missed Dose Alert",
                    fontWeight = FontWeight.Bold
                )
            },
            text = { Text(alertEntity.message) },
            confirmButton = {
                TextButton(onClick = { showAlertDetail = false }) { Text("OK") }
            }
        )
    }

    // Card Colors & Accents based on status
    val cardBg = when (reminder.status) {
        ReminderStatus.TAKEN -> if (isDark) Color(0xFF132A17) else Color(0xFFF1F8E9)
        ReminderStatus.MISSED -> if (isDark) Color(0xFF2C1515) else Color(0xFFFFF5F5)
        ReminderStatus.PENDING -> MaterialTheme.colorScheme.surface
    }
    val cardBorder = when (reminder.status) {
        ReminderStatus.TAKEN -> Color(0xFF81C784).copy(alpha = 0.5f)
        ReminderStatus.MISSED -> Color(0xFFE57373).copy(alpha = 0.5f)
        ReminderStatus.PENDING -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    }
    val statusColor = when (reminder.status) {
        ReminderStatus.TAKEN -> Color(0xFF2E7D32)
        ReminderStatus.MISSED -> Color(0xFFC62828)
        ReminderStatus.PENDING -> Color(0xFF1E88E5)
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = BorderStroke(1.dp, cardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = if (reminder.status == ReminderStatus.PENDING) 2.dp else 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Top Row: Status Icon Pill + Medicine Name + Time Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    // Left Status Icon Pill
                    Surface(
                        shape = CircleShape,
                        color = when (reminder.status) {
                            ReminderStatus.TAKEN -> Color(0xFFE8F5E9)
                            ReminderStatus.MISSED -> Color(0xFFFFEBEE)
                            ReminderStatus.PENDING -> Color(0xFFE3F2FD)
                        },
                        modifier = Modifier.size(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = when (reminder.status) {
                                    ReminderStatus.TAKEN -> Icons.Filled.CheckCircle
                                    ReminderStatus.MISSED -> Icons.Filled.Warning
                                    ReminderStatus.PENDING -> Icons.Filled.Medication
                                },
                                contentDescription = null,
                                tint = statusColor,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Spacer(Modifier.width(12.dp))

                    Column {
                        Text(
                            text = reminder.medicineName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = reminder.dose,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Scheduled Time Badge
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = when (reminder.status) {
                        ReminderStatus.TAKEN -> Color(0xFFE8F5E9)
                        ReminderStatus.MISSED -> Color(0xFFFFEBEE)
                        ReminderStatus.PENDING -> Color(0xFFE3F2FD)
                    }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Schedule,
                            contentDescription = null,
                            tint = statusColor,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = reminder.time,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = statusColor
                        )
                    }
                }
            }

            // Streak & Alert row
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.LocalFireDepartment,
                            contentDescription = null,
                            tint = Color(0xFFE65100),
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "${reminder.adherenceStreak}d streak • ${reminder.adherencePercent}% compliance",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (alertEntity != null) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFC62828).copy(alpha = 0.12f),
                        modifier = Modifier.clickable { showAlertDetail = true }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Icon(Icons.Filled.Warning, contentDescription = null, tint = Color(0xFFC62828), modifier = Modifier.size(12.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Alert Notice", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color(0xFFC62828))
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // ── ACTION BUTTONS ────────────────────────────────────────────────
            when (reminder.status) {
                ReminderStatus.PENDING -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Mark Taken Button (Primary Emerald)
                        Button(
                            onClick = onTaken,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1.3f)
                        ) {
                            Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Mark Taken", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                        }

                        // Snooze Button (+15m)
                        OutlinedButton(
                            onClick = onSnooze,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.Snooze, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Snooze", style = MaterialTheme.typography.labelMedium)
                        }

                        // Missed / Skip Button
                        OutlinedButton(
                            onClick = onMissed,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFC62828)),
                            border = BorderStroke(1.dp, Color(0xFFEF9A9A)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(0.9f)
                        ) {
                            Text("Skip", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }

                ReminderStatus.TAKEN -> {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF2E7D32).copy(alpha = 0.12f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "Completed & Logged in Health Record",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF2E7D32)
                                )
                            }
                        }
                    }
                }

                ReminderStatus.MISSED -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFFC62828).copy(alpha = 0.12f),
                            modifier = Modifier.weight(1f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Filled.ErrorOutline, contentDescription = null, tint = Color(0xFFC62828), modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Dose Missed", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Color(0xFFC62828))
                            }
                        }

                        Spacer(Modifier.width(8.dp))

                        TextButton(
                            onClick = onTaken,
                            colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF2E7D32))
                        ) {
                            Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Take Now (Late)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Empty State Card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun EmptyRemindersCard(
    filter: ReminderFilter,
    onAddMedication: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                modifier = Modifier.size(60.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = when (filter) {
                            ReminderFilter.ALL -> Icons.Filled.NotificationsNone
                            ReminderFilter.PENDING -> Icons.Filled.CheckCircle
                            ReminderFilter.TAKEN -> Icons.Filled.Check
                            ReminderFilter.MISSED -> Icons.Filled.SentimentSatisfied
                        },
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            Text(
                text = when (filter) {
                    ReminderFilter.ALL -> "No Reminders Scheduled"
                    ReminderFilter.PENDING -> "All Caught Up!"
                    ReminderFilter.TAKEN -> "No Doses Taken Yet"
                    ReminderFilter.MISSED -> "Zero Missed Doses"
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(6.dp))

            Text(
                text = when (filter) {
                    ReminderFilter.ALL -> "Scanned prescriptions or recorded consultations will populate your reminders automatically."
                    ReminderFilter.PENDING -> "You have completed all pending medication doses for right now."
                    ReminderFilter.TAKEN -> "Doses you mark as taken will show up here."
                    ReminderFilter.MISSED -> "Great job keeping up with your medication schedule today!"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            if (filter == ReminderFilter.ALL) {
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = onAddMedication,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Add Medication Manually")
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Data Model & Fallbacks
// ─────────────────────────────────────────────────────────────────────────────

data class ReminderItem(
    val id: String,
    val medicineName: String,
    val time: String,
    val dose: String,
    val status: ReminderStatus,
    val adherenceStreak: Int = 0,
    val adherencePercent: Int = 100,
    val caretakerPhone: String? = null,
    val lastAlertType: String? = null
)

enum class ReminderStatus { PENDING, TAKEN, MISSED }

val dummyReminders = listOf(
    ReminderItem("r1", "Metformin",  "8:00 AM",  "1 tablet · 500mg",   ReminderStatus.TAKEN,  adherenceStreak = 5, adherencePercent = 100),
    ReminderItem("r2", "Amlodipine", "9:00 AM",  "1 tablet · 5mg",     ReminderStatus.PENDING, adherenceStreak = 4, adherencePercent = 90),
    ReminderItem("r3", "Lisinopril", "10:00 PM", "1 tablet · 10mg",    ReminderStatus.PENDING, adherenceStreak = 4, adherencePercent = 95),
    ReminderItem("r4", "Vitamin D3", "9:00 AM",  "1 softgel · 1000IU", ReminderStatus.MISSED,  adherenceStreak = 2, adherencePercent = 60),
    ReminderItem("r5", "Donepezil",  "8:00 PM",  "1 tablet · 5mg",     ReminderStatus.PENDING, adherenceStreak = 7, adherencePercent = 100),
)
