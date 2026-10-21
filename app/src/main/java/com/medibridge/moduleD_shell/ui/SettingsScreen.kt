package com.medibridge.moduleD_shell.ui

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
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
import com.medibridge.core.theme.*
import com.medibridge.moduleC_schedule.tts.ReminderLanguage
import com.medibridge.moduleD_shell.ui.components.*
import com.medibridge.moduleD_shell.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class AppTextSize(val label: String) {
    DEFAULT("Standard"),
    MEDIUM("Medium"),
    LARGE("Large")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {
    val context = LocalContext.current
    val isDarkMode by viewModel.isDarkMode.collectAsState()
    val ttsLanguage by viewModel.ttsLanguage.collectAsState()
    val caretakerPhone by viewModel.caretakerPhone.collectAsState()
    val snoozeInterval by viewModel.snoozeIntervalMinutes.collectAsState()
    val demoStatus by viewModel.demoStatus.collectAsState()

    var selectedTextSize by remember { mutableStateOf(AppTextSize.DEFAULT) }

    val sessionManager = remember { com.medibridge.core.session.UserSessionManager.getInstance(context) }
    val activeProfileId by sessionManager.activeProfileIdFlow.collectAsState(initial = com.medibridge.core.session.UserSessionManager.DEFAULT_PROFILE_ID)
    val activeProfileName by sessionManager.activeProfileNameFlow.collectAsState(initial = "Alex")
    val userId by sessionManager.userIdFlow.collectAsState(initial = "")

    val db = remember { com.medibridge.core.db.AppDatabase.getInstance(context) }
    val careLinks by db.careLinkDao().getCareLinksForProfile(activeProfileId).collectAsState(initial = emptyList())
    var showCareLinkDialog by remember { mutableStateOf(false) }

    val recordingsFlow = remember(context) { viewModel.observeRecordings(context) }
    val recordingsList by recordingsFlow.collectAsState(initial = emptyList())
    var selectedRecordingForDialog by remember { mutableStateOf<RecordingEntity?>(null) }
    var phoneInput by remember(caretakerPhone) { mutableStateOf(caretakerPhone) }
    var phoneSavedFeedback by remember { mutableStateOf(false) }

    val authManager = remember { com.medibridge.core.auth.AuthTokenManager.getInstance(context) }
    val currentUsername = authManager.getUsername() ?: ""
    val currentName = authManager.getUserName() ?: activeProfileName
    var showLogoutDialog by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    var showUrlDialog by remember { mutableStateOf(false) }
    var currentUrl by remember { mutableStateOf(com.medibridge.core.network.BackendClient.getBaseUrl()) }
    var urlInput by remember { mutableStateOf(currentUrl) }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            shape = RoundedCornerShape(20.dp),
            icon = { Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Log Out", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to log out? Your local data will be cleared.") },
            confirmButton = {
                DestructiveButton(
                    text = "Log Out",
                    size = ButtonSize.Small,
                    onClick = {
                        showLogoutDialog = false
                        coroutineScope.launch {
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                db.clearAllTables()
                            }
                            com.medibridge.core.auth.AuthTokenManager.getInstance(context).clearAll()
                            com.medibridge.core.auth.AuthEvents.emitUnauthorized()
                        }
                    }
                )
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Care Link dialog
    if (showCareLinkDialog) {
        CareLinkDialog(
            profileId = activeProfileId,
            profileName = activeProfileName,
            onDismiss = { showCareLinkDialog = false }
        )
    }

    // Dialog showing full transcript & clinical summary of clicked recording
    selectedRecordingForDialog?.let { recording ->
        val dateFormatted = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(Date(recording.timestamp))
        AlertDialog(
            onDismissRequest = { selectedRecordingForDialog = null },
            shape = RoundedCornerShape(20.dp),
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

    // Dialog allowing user to update backend server URL
    if (showUrlDialog) {
        AlertDialog(
            onDismissRequest = { showUrlDialog = false },
            shape = RoundedCornerShape(20.dp),
            icon = { Icon(Icons.Default.Link, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
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
                PrimaryButton(
                    text = "Save & Connect",
                    size = ButtonSize.Small,
                    onClick = {
                        val trimmed = urlInput.trim()
                        if (trimmed.isNotBlank()) {
                            com.medibridge.core.network.BackendClient.updateBaseUrl(trimmed, context)
                            currentUrl = com.medibridge.core.network.BackendClient.getBaseUrl()
                            android.widget.Toast.makeText(context, "Backend URL updated", android.widget.Toast.LENGTH_SHORT).show()
                        }
                        showUrlDialog = false
                    }
                )
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
                        text = "Profile & Settings",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
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
            // ── Profile Header Card ──────────────────────────────────────────
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.16f)),
                tonalElevation = 1.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(54.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(30.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = currentName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (currentUsername.isNotBlank()) "@$currentUsername" else "Authenticated",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    DestructiveButton(
                        text = "Log Out",
                        icon = Icons.AutoMirrored.Filled.Logout,
                        size = ButtonSize.Small,
                        onClick = { showLogoutDialog = true }
                    )
                }
            }

            // ── 0. Care Links & Family Section ────────────────────────────────
            SettingsCard(title = "Care Links & Caretakers", icon = Icons.Default.FamilyRestroom) {
                Text(
                    text = "Share access with family members and caretakers without requiring accounts. Invitees can view doses or manage prescriptions using secure 6-character codes.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    PrimaryButton(
                        text = "Invite Caretaker",
                        icon = Icons.Default.PersonAdd,
                        size = ButtonSize.Small,
                        onClick = { showCareLinkDialog = true },
                        modifier = Modifier.weight(1f)
                    )

                    SecondaryButton(
                        text = "Join with Code",
                        icon = Icons.Default.VpnKey,
                        size = ButtonSize.Small,
                        onClick = { showCareLinkDialog = true },
                        modifier = Modifier.weight(1f)
                    )
                }

                if (careLinks.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f))
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Active Care Links (${careLinks.size})",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    val coroutineScope = rememberCoroutineScope()
                    careLinks.forEach { link ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = if (link.inviteCode.isNotEmpty()) "Code: ${link.inviteCode}" else "Caretaker Link",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Permission: ${link.permission} • Status: ${link.status}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                TextButton(
                                    onClick = {
                                        coroutineScope.launch {
                                            try {
                                                com.medibridge.core.network.BackendClient.getService()
                                                    .revokeCareLink(com.medibridge.core.network.CareRevokeRequest(link.id))
                                                db.careLinkDao().revokeCareLink(link.id)
                                                android.widget.Toast.makeText(context, "Care link revoked", android.widget.Toast.LENGTH_SHORT).show()
                                            } catch (e: Exception) {
                                                android.widget.Toast.makeText(context, "Could not revoke: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                ) {
                                    Text(
                                        text = "Revoke",
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ── 1. Appearance & Theme ─────────────────────────────────────────
            SettingsCard(title = "Appearance", icon = Icons.Default.Palette) {
                Text(
                    text = "Theme Mode",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))

                ThemeSelector(
                    isDark = isDarkMode,
                    onThemeChanged = { enabled -> viewModel.setDarkMode(enabled) }
                )

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f))
                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Text Size Scale",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Adjust text scale for comfortable clinical reading.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))

                SegmentedToggle(
                    items = listOf(AppTextSize.DEFAULT, AppTextSize.MEDIUM, AppTextSize.LARGE),
                    selectedItem = selectedTextSize,
                    onItemSelected = { selectedTextSize = it },
                    label = { it.label }
                )
            }

            // ── 2. Reminder Voice & Audio ─────────────────────────────────────
            SettingsCard(title = "Voice & Language", icon = Icons.AutoMirrored.Filled.VolumeUp) {
                Text(
                    text = "Spoken Reminder Language",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Select speech language for spoken medication reminders.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))

                SegmentedToggle(
                    items = listOf(ReminderLanguage.ENGLISH, ReminderLanguage.TAMIL),
                    selectedItem = ttsLanguage,
                    onItemSelected = { viewModel.setTtsLanguage(it) },
                    label = { if (it == ReminderLanguage.ENGLISH) "English" else "தமிழ் (Tamil)" }
                )

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f))
                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Reminder Repeat Snooze Interval",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))

                SegmentedToggle(
                    items = listOf(10, 15, 30),
                    selectedItem = snoozeInterval,
                    onItemSelected = { viewModel.setSnoozeIntervalMinutes(it) },
                    label = { "$it min" }
                )
            }

            // ── 3. Caretaker Emergency Escalation ─────────────────────────────
            SettingsCard(title = "Emergency Contact", icon = Icons.Default.ContactPhone) {
                Text(
                    text = "Designated Emergency Contact",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Phone number notified if a critical dose is missed for over 60 minutes.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = phoneInput,
                        onValueChange = {
                            phoneInput = it
                            phoneSavedFeedback = false
                        },
                        label = { Text("Phone Number") },
                        placeholder = { Text("+1 555-0199") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    )

                    PrimaryButton(
                        text = if (phoneSavedFeedback) "Saved" else "Save",
                        icon = if (phoneSavedFeedback) Icons.Default.Check else null,
                        size = ButtonSize.Default,
                        onClick = {
                            viewModel.updateCaretakerPhone(phoneInput)
                            val prefs = context.getSharedPreferences("medibridge_prefs", Context.MODE_PRIVATE)
                            prefs.edit().putString("caretaker_phone", phoneInput.trim()).apply()
                            phoneSavedFeedback = true
                        }
                    )
                }
            }

            // ── 4. Developer Section (Hidden / Collapsible) ─────────────────
            var showDeveloperSection by remember { mutableStateOf(false) }
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clickable { showDeveloperSection = !showDeveloperSection },
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.16f)),
                tonalElevation = 1.dp
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                modifier = Modifier.size(34.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Code,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = "Developer Options",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Icon(
                            imageVector = if (showDeveloperSection) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (showDeveloperSection) "Collapse" else "Expand",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (showDeveloperSection) {
                        Spacer(modifier = Modifier.height(14.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f))
                        Spacer(modifier = Modifier.height(14.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Server Endpoint",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = currentUrl,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }

                            SecondaryButton(
                                text = "Edit URL",
                                icon = Icons.Default.Edit,
                                size = ButtonSize.Small,
                                onClick = {
                                    urlInput = currentUrl
                                    showUrlDialog = true
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SettingsCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.16f)),
        tonalElevation = 1.dp
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 14.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(34.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
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
