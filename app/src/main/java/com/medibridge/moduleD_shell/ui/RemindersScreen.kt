package com.medibridge.moduleD_shell.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.medibridge.core.theme.*
import com.medibridge.moduleC_schedule.viewmodel.ScheduleViewModel

/**
 * RemindersScreen — shows the list of upcoming and past medication reminders
 * with live adherence tracking (Taken in Green, Missed in Red, Stored in Room DB).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemindersScreen(
    onBack: () -> Unit,
    viewModel: ScheduleViewModel = viewModel()
) {
    val liveReminders by viewModel.reminders.collectAsState()
    // Fall back to dummy reminders if database has not yet been populated
    val displayReminders = if (liveReminders.isNotEmpty()) liveReminders else dummyReminders

    val takenCount = displayReminders.count { it.status == ReminderStatus.TAKEN }
    val missedCount = displayReminders.count { it.status == ReminderStatus.MISSED }
    val pendingCount = displayReminders.count { it.status == ReminderStatus.PENDING }
    val totalDoses = displayReminders.size
    val adherencePercent = if (takenCount + missedCount > 0) {
        (takenCount * 100) / (takenCount + missedCount)
    } else {
        100
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text       = "Medication Reminders",
                            fontWeight = FontWeight.Bold,
                            style      = MaterialTheme.typography.titleMedium,
                            color      = MaterialTheme.colorScheme.onPrimary
                        )
                        Text(
                            text  = "Patient: Mr Tan Ah Kow (Age 55)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector        = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint               = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.seedDemoSchedule() }) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "Reset / Re-seed Schedule",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        LazyColumn(
            modifier            = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding      = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // ── Top Adherence & Stored Dose Dashboard ─────────────────────────
            item {
                AdherenceSummaryCard(
                    takenCount = takenCount,
                    missedCount = missedCount,
                    pendingCount = pendingCount,
                    totalDoses = totalDoses,
                    adherencePercent = adherencePercent,
                    onReloadSchedule = { viewModel.seedDemoSchedule() }
                )
            }

            // ── Section Header ────────────────────────────────────────────────
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text     = "Today's Prescribed Doses",
                        style    = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color    = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "$totalDoses Doses Scheduled",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // ── List of Reminders ─────────────────────────────────────────────
            if (displayReminders.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Filled.NotificationsNone,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = "No Scheduled Reminders Today",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "Click below to seed Mr Tan Ah Kow's clinical regimen into the database.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Spacer(Modifier.height(14.dp))
                            Button(onClick = { viewModel.seedDemoSchedule() }) {
                                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Load Patient Schedule")
                            }
                        }
                    }
                }
            } else {
                items(displayReminders, key = { it.id }) { reminder ->
                    ReminderCard(
                        reminder = reminder,
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
// Adherence Summary Card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AdherenceSummaryCard(
    takenCount: Int,
    missedCount: Int,
    pendingCount: Int,
    totalDoses: Int,
    adherencePercent: Int,
    onReloadSchedule: () -> Unit
) {
    val isDark = isSystemInDarkTheme()

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Medication,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Adherence & Tracker DB",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (adherencePercent >= 80) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
                ) {
                    Text(
                        text = "$adherencePercent% Adherence",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (adherencePercent >= 80) Color(0xFF2E7D32) else Color(0xFFC62828),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // 3 Metric Stat Boxes (Taken in Green, Missed in Red, Pending in Amber)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // TAKEN BOX (GREEN)
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    color = if (isDark) Color(0xFF132A17) else Color(0xFFE8F5E9),
                    border = BorderStroke(1.5.dp, Color(0xFF2E7D32))
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF2E7D32),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "TAKEN",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF2E7D32)
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "$takenCount",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (isDark) Color(0xFFA5D6A7) else Color(0xFF1B5E20)
                        )
                        Text(
                            text = "Tablets Taken",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF2E7D32)
                        )
                    }
                }

                // MISSED BOX (RED)
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    color = if (isDark) Color(0xFF331414) else Color(0xFFFFEBEE),
                    border = BorderStroke(1.5.dp, Color(0xFFC62828))
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.Cancel,
                                contentDescription = null,
                                tint = Color(0xFFC62828),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "MISSED",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFC62828)
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "$missedCount",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (isDark) Color(0xFFEF9A9A) else Color(0xFFB71C1C)
                        )
                        Text(
                            text = "Tablets Missed",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFC62828)
                        )
                    }
                }

                // PENDING BOX (AMBER)
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    color = if (isDark) Color(0xFF382F18) else Color(0xFFFFF8E1),
                    border = BorderStroke(1.5.dp, Color(0xFFF57F17))
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.Schedule,
                                contentDescription = null,
                                tint = Color(0xFFF57F17),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "PENDING",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFF57F17)
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "$pendingCount",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (isDark) Color(0xFFFFE082) else Color(0xFFE65100)
                        )
                        Text(
                            text = "Doses Left",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFF57F17)
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // Adherence Progress Bar
            val progressFraction = if (totalDoses > 0) takenCount.toFloat() / totalDoses.toFloat() else 0f
            LinearProgressIndicator(
                progress = { progressFraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp),
                color = Color(0xFF2E7D32),
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            Spacer(Modifier.height(10.dp))

            // Caretaker Escalation Link Info
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.PhoneInTalk,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Caretaker: Mr Tan Ah Beng (+65 9123 4567) · Auto Escalation Armed",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Data Model for a Reminder Item
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
)

enum class ReminderStatus { PENDING, TAKEN, MISSED }

val dummyReminders = listOf(
    ReminderItem("r1", "Metformin",  "8:00 AM",  "1 tablet · 500mg",   ReminderStatus.TAKEN,  adherenceStreak = 5, adherencePercent = 100),
    ReminderItem("r2", "Amlodipine", "9:00 AM",  "1 tablet · 5mg",     ReminderStatus.PENDING, adherenceStreak = 4, adherencePercent = 90),
    ReminderItem("r3", "Lisinopril", "10:00 PM", "1 tablet · 10mg",    ReminderStatus.PENDING, adherenceStreak = 4, adherencePercent = 95),
    ReminderItem("r4", "Vitamin D3", "9:00 AM",  "1 softgel · 1000IU", ReminderStatus.MISSED,  adherenceStreak = 2, adherencePercent = 60),
    ReminderItem("r5", "Donepezil",  "8:00 PM",  "1 tablet · 5mg",     ReminderStatus.PENDING, adherenceStreak = 7, adherencePercent = 100),
)

// ─────────────────────────────────────────────────────────────────────────────
// Reminder Card (Green Box if Taken, Red Box if Missed, Interactive if Pending)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ReminderCard(
    reminder: ReminderItem,
    onTaken: () -> Unit = {},
    onSnooze: () -> Unit = {},
    onMissed: () -> Unit = {}
) {
    val isDark = isSystemInDarkTheme()

    // Container styling based on status
    val containerColor = when (reminder.status) {
        ReminderStatus.TAKEN   -> if (isDark) Color(0xFF132A17) else Color(0xFFE8F5E9) // Distinct Green Box
        ReminderStatus.MISSED  -> if (isDark) Color(0xFF331414) else Color(0xFFFFEBEE) // Distinct Red Box
        ReminderStatus.PENDING -> MaterialTheme.colorScheme.surface
    }

    val borderColor = when (reminder.status) {
        ReminderStatus.TAKEN   -> Color(0xFF2E7D32) // Solid Green border
        ReminderStatus.MISSED  -> Color(0xFFC62828) // Solid Red border
        ReminderStatus.PENDING -> MaterialTheme.colorScheme.outlineVariant
    }

    val accentColor = when (reminder.status) {
        ReminderStatus.TAKEN   -> Color(0xFF2E7D32)
        ReminderStatus.MISSED  -> Color(0xFFC62828)
        ReminderStatus.PENDING -> MaterialTheme.colorScheme.primary
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(1.5.dp, borderColor),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (reminder.status == ReminderStatus.PENDING) 2.dp else 1.dp
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            // Left Accent Bar
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .fillMaxHeight()
                    .background(color = accentColor, shape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp))
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(14.dp)
            ) {
                // Top Row: Medicine Name & Scheduled Time
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = reminder.medicineName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = when (reminder.status) {
                            ReminderStatus.TAKEN  -> if (isDark) Color(0xFFA5D6A7) else Color(0xFF1B5E20)
                            ReminderStatus.MISSED -> if (isDark) Color(0xFFEF9A9A) else Color(0xFFB71C1C)
                            else                  -> MaterialTheme.colorScheme.onSurface
                        }
                    )

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = when (reminder.status) {
                            ReminderStatus.TAKEN  -> Color(0xFF2E7D32).copy(alpha = 0.15f)
                            ReminderStatus.MISSED -> Color(0xFFC62828).copy(alpha = 0.15f)
                            else                  -> MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                        }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Icon(
                                imageVector = when (reminder.status) {
                                    ReminderStatus.TAKEN  -> Icons.Filled.CheckCircle
                                    ReminderStatus.MISSED -> Icons.Filled.Warning
                                    else                  -> Icons.Filled.AccessTime
                                },
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = reminder.time,
                                style = MaterialTheme.typography.labelMedium,
                                color = accentColor,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))

                // Middle Row: Dose instructions & Adherence streak
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = reminder.dose,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Streak Badge
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.LocalFireDepartment,
                                contentDescription = null,
                                tint = Color(0xFFE65100),
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(Modifier.width(3.dp))
                            Text(
                                text = "${reminder.adherenceStreak}d streak · ${reminder.adherencePercent}%",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                // ── Action Buttons for PENDING / Status Badge for TAKEN & MISSED ──
                if (reminder.status == ReminderStatus.PENDING) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // TAKEN BUTTON (GREEN)
                        FilledTonalButton(
                            onClick = onTaken,
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = Color(0xFFE8F5E9),
                                contentColor = Color(0xFF2E7D32)
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Taken", fontWeight = FontWeight.Bold)
                        }

                        // SNOOZE BUTTON
                        OutlinedButton(
                            onClick = onSnooze,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.Snooze, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Snooze")
                        }

                        // MISSED BUTTON (RED)
                        OutlinedButton(
                            onClick = onMissed,
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color(0xFFC62828)
                            ),
                            border = BorderStroke(1.dp, Color(0xFFC62828)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Missed", fontWeight = FontWeight.Bold)
                        }
                    }
                } else if (reminder.status == ReminderStatus.TAKEN) {
                    // ── GREEN BOX CONFIRMATION BADGE ──
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF2E7D32)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "✓ Tablet Taken — Logged in Database",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                } else if (reminder.status == ReminderStatus.MISSED) {
                    // ── RED BOX CONFIRMATION BADGE ──
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFFC62828)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.ErrorOutline,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "✕ Tablet Missed — Caretaker Alerted",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }

                        Text(
                            text = "Auto-alert logged for ${reminder.caretakerPhone ?: "Mr Tan Ah Beng (+65 9123 4567)"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isDark) Color(0xFFEF9A9A) else Color(0xFFB71C1C),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}
