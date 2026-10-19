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
 * SettingsScreen — User-facing preferences and local storage configuration.
 *
 * Exposes consumer settings:
 *   • Appearance (Dark / Light Theme)
 *   • Reminder Voice & Audio (TTS language English / Tamil, notification snooze duration)
 *   • Caretaker Emergency Contact (Phone number for missed-dose alerts)
 *   • Consultation Audio Archive
 *   • Data & Storage Management (Reset / clear local Room database)
 *   • About MediBridge (Build version, security enclaves, backend status)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {
    val context = LocalContext.current
    val isDarkMode by viewModel.isDarkMode.collectAsState()
    val ttsLanguage by viewModel.ttsLanguage.collectAsState()
    val caretakerPhone by viewModel.caretakerPhone.collectAsState()
    val snoozeInterval by viewModel.snoozeIntervalMinutes.collectAsState()
    val demoStatus by viewModel.demoStatus.collectAsState()

    val recordingsFlow = remember(context) { viewModel.observeRecordings(context) }
    val recordingsList by recordingsFlow.collectAsState(initial = emptyList())
    var selectedRecordingForDialog by remember { mutableStateOf<RecordingEntity?>(null) }
    var phoneInput by remember(caretakerPhone) { mutableStateOf(caretakerPhone) }
    var phoneSavedFeedback by remember { mutableStateOf(false) }

    var showUrlDialog by remember { mutableStateOf(false) }
    var currentUrl by remember { mutableStateOf(com.medibridge.core.network.BackendClient.getBaseUrl()) }
    var urlInput by remember { mutableStateOf(currentUrl) }

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

    // Dialog allowing user to update backend server URL without rebuilding APK
    if (showUrlDialog) {
        AlertDialog(
            onDismissRequest = { showUrlDialog = false },
            icon = { Icon(Icons.Filled.Link, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Backend Server URL", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Paste your active Cloudflare Tunnel, Ngrok, or local IP address here. Changes take effect immediately without rebuilding the APK.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = urlInput,
                        onValueChange = { urlInput = it },
                        label = { Text("Server URL") },
                        placeholder = { Text("https://your-tunnel.trycloudflare.com/") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = urlInput.trim()
                        if (trimmed.isNotBlank()) {
                            com.medibridge.core.network.BackendClient.updateBaseUrl(trimmed, context)
                            currentUrl = com.medibridge.core.network.BackendClient.getBaseUrl()
                            android.widget.Toast.makeText(context, "Backend URL updated", android.widget.Toast.LENGTH_SHORT).show()
                        }
                        showUrlDialog = false
                    }
                ) {
                    Text("Save & Connect")
                }
            },
            dismissButton = {
                TextButton(onClick = { showUrlDialog = false }) {
                    Text("Cancel")
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
                .padding(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── 1. Appearance ────────────────────────────────────────────────
            SettingsCard(title = "Appearance", icon = Icons.Filled.Palette) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Dark Mode",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = if (isDarkMode) "Dark theme enabled for low-light environments" else "Light theme enabled",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = isDarkMode,
                        onCheckedChange = { viewModel.toggleDarkMode() },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                            checkedTrackColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }
            }

            // ── 2. Reminder Voice & Audio ─────────────────────────────────────
            SettingsCard(title = "Voice & Alarms", icon = Icons.AutoMirrored.Filled.VolumeUp) {
                Text(
                    text = "Spoken Reminder Language",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Select speech language for on-device spoken medication reminders and verbal dose prompts.",
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

                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Spacer(Modifier.height(14.dp))

                Text(
                    text = "Reminder Snooze Interval",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Time before repeating a pending dose notification if unacknowledged.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))

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

            // ── 3. Caretaker Emergency Escalation ─────────────────────────────
            SettingsCard(title = "Caretaker Emergency Escalation", icon = Icons.Filled.ContactPhone) {
                Text(
                    text = "Designated Emergency Contact",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Phone number automatically contacted if a critical medication dose is missed by more than 60 minutes.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = phoneInput,
                    onValueChange = {
                        phoneInput = it
                        phoneSavedFeedback = false
                    },
                    label = { Text("Emergency Phone Number") },
                    placeholder = { Text("+1 555-0199 or +91 9876543210") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(Modifier.height(10.dp))

                Button(
                    onClick = {
                        viewModel.updateCaretakerPhone(phoneInput)
                        val prefs = context.getSharedPreferences("medibridge_prefs", android.content.Context.MODE_PRIVATE)
                        prefs.edit().putString("caretaker_phone", phoneInput.trim()).apply()
                        phoneSavedFeedback = true
                        android.widget.Toast.makeText(context, "Caretaker contact saved", android.widget.Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.align(Alignment.End),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(if (phoneSavedFeedback) Icons.Filled.Check else Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (phoneSavedFeedback) "Saved" else "Save Contact")
                }
            }

            // ── 4. Past Audio Consultations Archive ───────────────────────────
            SettingsCard(title = "Consultation Audio Archive (${recordingsList.size})", icon = Icons.Filled.History) {
                if (recordingsList.isEmpty()) {
                    Text(
                        text = "No recorded doctor consultations yet. Use the Record tab to capture audio and transcribe doctor advice in real time.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        recordingsList.take(4).forEach { rec ->
                            val dateStr = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(Date(rec.timestamp))
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedRecordingForDialog = rec }
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
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
            }

            // ── 5. Data & Storage Management ──────────────────────────────────
            SettingsCard(title = "Data & Storage Management", icon = Icons.Filled.Storage) {
                Text(
                    text = "Reset Local Records",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Permanently clears all scanned prescriptions, schedule reminders, safety interaction flags, and consultation recordings from this device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(14.dp))

                var showConfirmDialog by remember { mutableStateOf(false) }

                OutlinedButton(
                    onClick = { showConfirmDialog = true },
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier.align(Alignment.End),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Filled.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Clear All Data")
                }

                if (showConfirmDialog) {
                    AlertDialog(
                        onDismissRequest = { showConfirmDialog = false },
                        title = { Text("Reset Patient Database?") },
                        text = { Text("This will permanently remove all cached medications, schedules, and recordings stored on this device. This cannot be undone.") },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    viewModel.clearAllData(context)
                                    showConfirmDialog = false
                                    android.widget.Toast.makeText(context, "All patient records cleared", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            ) {
                                Text("Reset", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showConfirmDialog = false }) {
                                Text("Cancel")
                            }
                        }
                    )
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

            // ── 6. About MediBridge & Security ────────────────────────────────
            SettingsCard(title = "System & Security", icon = Icons.Filled.Security) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.VerifiedUser,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "MediBridge Clinical Enclave",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Version 1.0 • Build 2026",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                Spacer(Modifier.height(10.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            urlInput = com.medibridge.core.network.BackendClient.getBaseUrl()
                            showUrlDialog = true
                        },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Backend Service Connection",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = if (currentUrl.length > 36) currentUrl.take(34) + "..." else currentUrl,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "Edit",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(2.dp))
                            Icon(
                                imageVector = Icons.Filled.Edit,
                                contentDescription = "Edit URL",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Local Storage Encryption",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "Encrypted SQLite",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SettingsCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            content()
        }
    }
}
