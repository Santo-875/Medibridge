package com.medibridge.moduleD_shell.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.medibridge.core.db.RecordingEntity
import com.medibridge.moduleC_schedule.tts.ReminderLanguage
import com.medibridge.moduleD_shell.viewmodel.SettingsViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * SettingsScreen — App configuration with functional controls for:
 *   • Dark Mode
 *   • Recordings section (list of past recordings, tap for full transcript/summary)
 *   • Reminder voice: TTS language toggle (English / Tamil)
 *   • Reminders & Caretaker alerts: Snooze intervals & Caretaker phone number
 *   • Backend FastAPI URL
 *   • 4 Demo Scenarios (including "No Scenario" clean state)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {
    val context = LocalContext.current
    val isDarkMode by viewModel.isDarkMode.collectAsState()
    val ttsLanguage by viewModel.ttsLanguage.collectAsState()
    val currentUrl by viewModel.backendBaseUrl.collectAsState()
    val caretakerPhone by viewModel.caretakerPhone.collectAsState()
    val snoozeInterval by viewModel.snoozeIntervalMinutes.collectAsState()
    val activeScenario by viewModel.activeScenario.collectAsState()
    val demoStatus by viewModel.demoStatus.collectAsState()

    val recordingsList by viewModel.observeRecordings(context).collectAsState(initial = emptyList())
    var selectedRecordingForDialog by remember { mutableStateOf<RecordingEntity?>(null) }

    var urlInput by remember(currentUrl) { mutableStateOf(currentUrl) }
    var phoneInput by remember(caretakerPhone) { mutableStateOf(caretakerPhone) }

    // Dialog showing full transcript & clinical summary of clicked recording
    selectedRecordingForDialog?.let { recording ->
        val dateFormatted = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(Date(recording.timestamp))
        AlertDialog(
            onDismissRequest = { selectedRecordingForDialog = null },
            title = {
                Text(
                    text = "Audio Consultation Note",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        text = "Recorded: $dateFormatted",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "Clinical Summary:",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = recording.summary.ifBlank { "No summary available." },
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "Full Audio Transcript:",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = recording.transcript.ifBlank { "Audio recorded without transcript." },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedRecordingForDialog = null }) {
                    Text("Close")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Settings",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            // ── Appearance Section ────────────────────────────────────────────
            SettingsSectionHeader(title = "Appearance")

            SettingsToggleRow(
                icon = if (isDarkMode) Icons.Filled.DarkMode else Icons.Filled.LightMode,
                title = "Dark Mode",
                description = "Switch between light and dark theme",
                checked = isDarkMode,
                onToggle = { viewModel.toggleDarkMode() }
            )

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            // ── Reminder Voice Section (TTS) ──────────────────────────────────
            SettingsSectionHeader(title = "Reminder Voice & Audio")

            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Voice Assistant Language (TTS)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Controls the on-device Text-to-Speech synthesizer for spoken medication alarms and phone call reminders.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))

                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    val languages = listOf(ReminderLanguage.ENGLISH to "English", ReminderLanguage.TAMIL to "தமிழ் (Tamil)")
                    languages.forEachIndexed { index, (lang, label) ->
                        SegmentedButton(
                            selected = ttsLanguage == lang,
                            onClick = { viewModel.setTtsLanguage(lang) },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = languages.size)
                        ) {
                            Text(text = label, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            // ── Past Audio Recordings Section ─────────────────────────────────
            SettingsSectionHeader(title = "Consultation Recordings (${recordingsList.size})")

            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                if (recordingsList.isEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "No audio notes saved yet",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = "Tap the microphone icon on Home to record a doctor consultation session.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    recordingsList.take(5).forEach { rec ->
                        val dateStr = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(Date(rec.timestamp))
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable { selectedRecordingForDialog = rec },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = dateStr,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        text = rec.summary.ifBlank { rec.transcript },
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Filled.ChevronRight,
                                    contentDescription = "View Details",
                                    tint = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            // ── Caretaker & Reminders Configuration ───────────────────────────
            SettingsSectionHeader(title = "Reminders & Caretaker Alerts")

            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                OutlinedTextField(
                    value = phoneInput,
                    onValueChange = { phoneInput = it },
                    label = { Text("Caretaker Escalation Phone") },
                    placeholder = { Text("+1 555-0199") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { viewModel.updateCaretakerPhone(phoneInput) },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Save Phone")
                }

                Spacer(Modifier.height(10.dp))
                Text(
                    text = "Notification Snooze Duration",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(6.dp))
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    val intervals = listOf(10 to "10 min", 15 to "15 min", 30 to "30 min")
                    intervals.forEachIndexed { index, (mins, label) ->
                        SegmentedButton(
                            selected = snoozeInterval == mins,
                            onClick = { viewModel.setSnoozeIntervalMinutes(mins) },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = intervals.size)
                        ) {
                            Text(text = label, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            // ── Backend Connection Section ────────────────────────────────────
            SettingsSectionHeader(title = "Backend Connection")

            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                OutlinedTextField(
                    value = urlInput,
                    onValueChange = { urlInput = it },
                    label = { Text("FastAPI Backend Base URL") },
                    placeholder = { Text("http://10.0.2.2:8000/") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { viewModel.updateBackendBaseUrl(urlInput) },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Save URL")
                }
            }

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            // ── Pitch Demo Scenarios Section ──────────────────────────────────
            SettingsSectionHeader(title = "Clinical Scenarios & Demo Modes")

            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(
                    text = "Select a scenario to seed test data or choose 'No Scenario' to start completely fresh with live scanning & voice:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))

                // Row 1: Scenarios 1 & 2
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { viewModel.selectScenario(context, 1) },
                        modifier = Modifier.weight(1f),
                        colors = if (activeScenario == 1) ButtonDefaults.buttonColors() else ButtonDefaults.outlinedButtonColors()
                    ) {
                        Text("1: Diabetes", style = MaterialTheme.typography.labelSmall)
                    }
                    Button(
                        onClick = { viewModel.selectScenario(context, 2) },
                        modifier = Modifier.weight(1f),
                        colors = if (activeScenario == 2) ButtonDefaults.buttonColors() else ButtonDefaults.outlinedButtonColors()
                    ) {
                        Text("2: Hypertn", style = MaterialTheme.typography.labelSmall)
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Row 2: Scenario 3 & No Scenario (Option 4)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { viewModel.selectScenario(context, 3) },
                        modifier = Modifier.weight(1f),
                        colors = if (activeScenario == 3) ButtonDefaults.buttonColors() else ButtonDefaults.outlinedButtonColors()
                    ) {
                        Text("3: Caretaker", style = MaterialTheme.typography.labelSmall)
                    }
                    Button(
                        onClick = { viewModel.selectScenario(context, 4) },
                        modifier = Modifier.weight(1f),
                        colors = if (activeScenario == 4) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary) else ButtonDefaults.outlinedButtonColors()
                    ) {
                        Text("4: No Scenario (Empty)", style = MaterialTheme.typography.labelSmall)
                    }
                }

                if (!demoStatus.isNullOrBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = demoStatus ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            // ── About Section ─────────────────────────────────────────────────
            SettingsSectionHeader(title = "About MediBridge")

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.LocalHospital,
                            tint = MaterialTheme.colorScheme.primary,
                            contentDescription = null
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "MediBridge AI",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Version 1.0.0 (Unified Integration)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                    Text(
                        text = "Shared AppDatabase · Central Safety Engine · End-to-End Pipeline",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 4.dp)
    )
}

@Composable
private fun SettingsToggleRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    checked: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(42.dp)
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = { onToggle() },
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.primary
            )
        )
    }
}
