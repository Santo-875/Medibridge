package com.medibridge.moduleD_shell.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.medibridge.core.theme.*

/**
 * RemindersScreen — shows the list of upcoming and past medication reminders.
 *
 * EXTENSION POINTS:
 *   • Replace dummyReminders with a Flow from a RemindersViewModel (Room DB query
 *     on adherence/schedule fields of MedicationEntity).
 *   • TODO: Module C wires real schedule data here.
 *   • TODO: Module D hooks notification system (AlarmManager / WorkManager) to
 *     the Taken/Missed/Snooze actions below.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemindersScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text       = "Reminders",
                        fontWeight = FontWeight.Bold,
                        color      = MaterialTheme.colorScheme.onPrimary
                    )
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
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text(
                    text     = "Today's Reminders",
                    style    = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color    = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }

            items(dummyReminders) { reminder ->
                ReminderCard(reminder = reminder)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Data model for a reminder item (stub — replace with DB-backed model)
// ─────────────────────────────────────────────────────────────────────────────

data class ReminderItem(
    val id: String,
    val medicineName: String,
    val time: String,
    val dose: String,
    val status: ReminderStatus
)

enum class ReminderStatus { PENDING, TAKEN, MISSED }

private val dummyReminders = listOf(
    ReminderItem("r1", "Metformin",   "8:00 AM",  "1 tablet · 500mg", ReminderStatus.TAKEN),
    ReminderItem("r2", "Amlodipine",  "9:00 AM",  "1 tablet · 5mg",   ReminderStatus.PENDING),
    ReminderItem("r3", "Lisinopril",  "10:00 PM", "1 tablet · 10mg",  ReminderStatus.PENDING),
    ReminderItem("r4", "Vitamin D3",  "9:00 AM",  "1 softgel · 1000IU", ReminderStatus.MISSED),
    ReminderItem("r5", "Metformin",   "8:00 PM",  "1 tablet · 500mg", ReminderStatus.PENDING),
)

// ─────────────────────────────────────────────────────────────────────────────
// Reminder Card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ReminderCard(reminder: ReminderItem) {
    val accentColor = when (reminder.status) {
        ReminderStatus.TAKEN   -> StatusVerified
        ReminderStatus.MISSED  -> StatusConflict
        ReminderStatus.PENDING -> StatusReview
    }

    Card(
        shape  = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            // Left accent bar
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .fillMaxHeight()
                    .background(color = accentColor, shape = RoundedCornerShape(topStart = 14.dp, bottomStart = 14.dp))
            )

            Column(modifier = Modifier
                .weight(1f)
                .padding(14.dp)
            ) {
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Text(
                        text       = reminder.medicineName,
                        style      = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color      = MaterialTheme.colorScheme.onSurface
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector        = Icons.Filled.AccessTime,
                            contentDescription = null,
                            tint               = MaterialTheme.colorScheme.primary,
                            modifier           = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(3.dp))
                        Text(
                            text  = reminder.time,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(Modifier.height(4.dp))
                Text(
                    text  = reminder.dose,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(10.dp))

                // Action buttons (UI only — wire to AlarmManager/WorkManager in Module C/D)
                // TODO: Module C - missed state should be inferred automatically if time passes with no action
                if (reminder.status == ReminderStatus.PENDING) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Taken button
                        FilledTonalButton(
                            onClick = {
                                // TODO: Module C/D — mark dose as taken in DB (adherence log)
                                // dao.markAdherence(reminder.id, date = today(), status = "taken")
                            },
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = StatusVerifiedBg,
                                contentColor   = StatusVerified
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Taken", style = MaterialTheme.typography.labelMedium)
                        }

                        // Snooze button
                        OutlinedButton(
                            onClick = {
                                // TODO: Module D — reschedule notification by 15/30 min
                                // notificationManager.snooze(reminder.id, delayMinutes = 15)
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.Snooze, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Snooze", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                } else {
                    // Status badge for taken/missed
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = if (reminder.status == ReminderStatus.TAKEN) StatusVerifiedBg else StatusConflictBg
                    ) {
                        Text(
                            text     = if (reminder.status == ReminderStatus.TAKEN) "✓ Taken" else "✕ Missed",
                            style    = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color    = if (reminder.status == ReminderStatus.TAKEN) StatusVerified else StatusConflict,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}
