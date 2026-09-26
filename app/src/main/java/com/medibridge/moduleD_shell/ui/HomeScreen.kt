package com.medibridge.moduleD_shell.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.medibridge.core.db.AppDatabase
import com.medibridge.core.model.*
import com.medibridge.core.network.BackendClient
import com.medibridge.core.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File

/**
 * HomeScreen — main screen of MediBridge.
 *
 * Displays:
 *   • Top app bar with Patient / Caretaker mode toggle, patient name, audio recording button,
 *     paper/bill icon (BillScreen), OCR scanner icon (ScannerScreen), and notification bell.
 *   • Caretaker Mode section: Reminder Call Settings card with phone field, verification button, status chip.
 *   • Scrollable list of medication cards with Taken / Snooze buttons.
 *   • FAB area: Primary Medi Chatbot FAB stacked below Privacy Summary FAB.
 *   • PrivacySummarySheet bottom sheet filtering history by role (Doctor / Caretaker / Pharmacy).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onScannerClick: () -> Unit,
    onChatbotClick: () -> Unit,
    onReminderClick: () -> Unit,
    onSafetyClick: () -> Unit = {},
    onRecordClick: () -> Unit = {}
) {
    var showNotificationPanel by remember { mutableStateOf(false) }
    var showPrivacySheet by remember { mutableStateOf(false) }
    var isCaretakerMode by remember { mutableStateOf(false) }
    var patientName by remember { mutableStateOf("Mr Tan Ah Kow") }
    var caretakerPhone by remember { mutableStateOf("+65 9123 4567") }
    var callStatus by remember { mutableStateOf("Verified") } // "Not Verified", "Verified", "Call Scheduled"

    val context = LocalContext.current
    val db = remember { AppDatabase.getInstance(context) }
    val roomMedEntities by db.medicationDao().getAllMedications().collectAsState(initial = emptyList())
    val medications = remember(roomMedEntities) {
        roomMedEntities.map { it.fromEntity() }
    }

    Scaffold(
        topBar = {
            HomeTopBar(
                isCaretakerMode = isCaretakerMode,
                onModeToggle = { isCaretakerMode = it },
                patientName = patientName,
                isRecording = false,
                isProcessing = false,
                onRecordingToggle = onRecordClick,
                onScannerClick = onScannerClick,
                onNotificationClick = { showNotificationPanel = !showNotificationPanel },
                onSafetyClick = onSafetyClick
            )
        },
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Secondary smaller icon button stacked above Chatbot FAB
                SmallFloatingActionButton(
                    onClick = { showPrivacySheet = true },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                ) {
                    Icon(
                        imageVector = Icons.Filled.Security,
                        contentDescription = "Privacy Summary"
                    )
                }

                // Primary FAB: Chatbot ("Medi")
                ChatbotFab(onClick = onChatbotClick)
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->

        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(bottom = 100.dp)
            ) {
                // ── Header banner ─────────────────────────────────────────────
                item {
                    HomeHeaderBanner(
                        medicationCount = medications.size,
                        isCaretakerMode = isCaretakerMode,
                        patientName = patientName
                    )
                }

                // ── Caretaker Mode: Reminder Call Settings ────────────────────
                if (isCaretakerMode) {
                    item {
                        CaretakerCallSettingsCard(
                            phone = caretakerPhone,
                            onPhoneChange = { caretakerPhone = it },
                            callStatus = callStatus,
                            onVerifyClick = {
                                // Module E: verification call + bot scheduling here
                                callStatus = "Call Scheduled"
                            },
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                        )
                    }
                }

                // ── Section label ─────────────────────────────────────────────
                item {
                    Text(
                        text = if (isCaretakerMode) "$patientName's Medications" else "Your Medications",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }

                // ── Empty State if no medications ────────────────────────────
                if (medications.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Medication,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(44.dp)
                                )
                                Spacer(Modifier.height(10.dp))
                                Text(
                                    text = "No Active Medications",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "Tap the scanner icon at the top to scan a prescription or pharmacy bill, or tap the mic to record a doctor consultation.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    }
                }

                // ── Medication cards ──────────────────────────────────────────
                items(
                    items = medications,
                    key   = { it.id }
                ) { med ->
                    MedicationCard(
                        medication = med,
                        modifier   = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                }
            }

            // ── Notification dropdown panel ───────────────────────────────────
            AnimatedVisibility(
                visible = showNotificationPanel,
                enter   = slideInVertically() + fadeIn(),
                exit    = slideOutVertically() + fadeOut(),
                modifier = Modifier.align(Alignment.TopEnd)
            ) {
                NotificationPanel(
                    onDismiss = { showNotificationPanel = false },
                    onViewAll = { onReminderClick(); showNotificationPanel = false }
                )
            }
        }

        // ── Privacy Summary Bottom Sheet ──────────────────────────────────────
        if (showPrivacySheet) {
            PrivacySummarySheet(
                onDismiss = { showPrivacySheet = false }
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Top App Bar
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeTopBar(
    isCaretakerMode: Boolean,
    onModeToggle: (Boolean) -> Unit,
    patientName: String,
    isRecording: Boolean,
    isProcessing: Boolean,
    onRecordingToggle: () -> Unit,
    onScannerClick: () -> Unit,
    onNotificationClick: () -> Unit,
    onSafetyClick: () -> Unit
) {
    TopAppBar(
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Mode Toggle Button (Patient Mode / Caretaker Mode)
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.2f),
                    modifier = Modifier.clickable { onModeToggle(!isCaretakerMode) }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = if (isCaretakerMode) Icons.Filled.SupervisorAccount else Icons.Filled.Person,
                            contentDescription = "Mode Toggle",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = if (isCaretakerMode) "Caretaker" else "Patient",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }

                Spacer(Modifier.width(8.dp))

                // Display patient / user name
                Text(
                    text = patientName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        actions = {
            // Audio recording icon button — toggles audio recording state
            IconButton(onClick = onRecordingToggle) {
                if (isRecording) {
                    Icon(
                        imageVector = Icons.Filled.RadioButtonChecked,
                        contentDescription = "Stop Recording",
                        tint = MaterialTheme.colorScheme.error
                    )
                } else {
                    Icon(
                        imageVector = Icons.Outlined.Mic,
                        contentDescription = "Audio Recording",
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }


            // OCR Scanner icon button — opens Scanner screen
            IconButton(onClick = onScannerClick) {
                Icon(
                    imageVector = Icons.Filled.DocumentScanner,
                    contentDescription = "Scan Prescription",
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }

            // Safety / Medication-verification button (Module B)
            IconButton(onClick = onSafetyClick) {
                Icon(
                    imageVector = Icons.Filled.Shield,
                    contentDescription = "Medication Safety",
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }

            // Notification / bell icon — keeps existing alerts dropdown
            IconButton(onClick = onNotificationClick) {
                BadgedBox(
                    badge = {
                        Badge {
                            Text("3")  // TODO: wire real unread reminder count
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Notifications,
                        contentDescription = "Reminders",
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primary
        )
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Caretaker Call Settings Section
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CaretakerCallSettingsCard(
    phone: String,
    onPhoneChange: (String) -> Unit,
    callStatus: String,
    onVerifyClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.shadow(2.dp, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.PhoneInTalk,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Reminder Call Settings",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Status chip (Not Verified / Verified / Call Scheduled)
                val (chipBg, chipText) = when (callStatus) {
                    "Verified" -> Pair(StatusVerifiedBg, StatusVerified)
                    "Call Scheduled" -> Pair(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.primary)
                    else -> Pair(StatusReviewBg, StatusReview)
                }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = chipBg
                ) {
                    Text(
                        text = callStatus,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = chipText,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = phone,
                onValueChange = onPhoneChange,
                label = { Text("Phone Number") },
                placeholder = { Text("e.g. +1 555-0199") },
                leadingIcon = {
                    Icon(Icons.Filled.Phone, contentDescription = null, modifier = Modifier.size(18.dp))
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            Spacer(Modifier.height(12.dp))

            Button(
                onClick = {
                    // Module E: verification call + bot scheduling here
                    onVerifyClick()
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(Icons.Filled.Call, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Verify & Enable Call Reminders",
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Privacy Summary Bottom Sheet
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacySummarySheet(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val db = remember { AppDatabase.getInstance(context) }
    val privacyRepo = remember { com.medibridge.core.repository.PrivacySummaryRepository(db) }
    var selectedRoleIndex by remember { mutableIntStateOf(0) }
    val roles = listOf("Doctor", "Caretaker", "Pharmacy")

    var summaryText by remember { mutableStateOf("Generating clinical privacy view...") }

    LaunchedEffect(selectedRoleIndex) {
        summaryText = when (roles[selectedRoleIndex]) {
            "Doctor" -> privacyRepo.getDoctorSummary()
            "Caretaker" -> privacyRepo.getCaretakerSummary()
            "Pharmacy" -> privacyRepo.getPharmacySummary()
            else -> ""
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Shield,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "Privacy-Focused Summary",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.height(8.dp))
            Text(
                text = "Select a role to view filtered medication history according to privacy controls.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(16.dp))

            // Segmented Selector: Doctor / Caretaker / Pharmacy
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                roles.forEachIndexed { index, role ->
                    SegmentedButton(
                        selected = selectedRoleIndex == index,
                        onClick = { selectedRoleIndex = index },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = roles.size)
                    ) {
                        Text(text = role, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            val selectedRole = roles[selectedRoleIndex]

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "$selectedRole Access View",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = summaryText,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(12.dp))
                    // pulls from shared history, filtered by role — Module D RBAC + Module C summary
                    Text(
                        text = "// pulls from shared history, filtered by role — Module D RBAC + Module C summary",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Close Summary")
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Header Banner
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun HomeHeaderBanner(
    medicationCount: Int,
    isCaretakerMode: Boolean,
    patientName: String
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                brush = Brush.horizontalGradient(
                    colors = listOf(GradientStart, GradientEnd)
                )
            )
            .padding(horizontal = 20.dp, vertical = 20.dp)
    ) {
        Column {
            Text(
                text = if (isCaretakerMode) "Caretaker Overview 👋" else "Good morning! 👋",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "$medicationCount medicines scheduled today",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatChip(label = "✅ 2 Taken")
                StatChip(label = "⏳ 2 Pending")
                StatChip(label = "⚠️ 1 Alert")
            }
        }
    }
}

@Composable
private fun StatChip(label: String) {
    Surface(
        shape  = RoundedCornerShape(20.dp),
        color  = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.18f)
    ) {
        Text(
            text  = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Medication Card (Schedule Card)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun MedicationCard(
    medication: MedicationObject,
    modifier: Modifier = Modifier
) {
    val status = when {
        medication.conflicts.isNotEmpty() -> MedStatus.CONFLICT
        medication.needsVerification && !medication.verifiedByUser -> MedStatus.NEEDS_REVIEW
        else -> MedStatus.VERIFIED
    }

    Card(
        modifier = modifier
            .shadow(4.dp, RoundedCornerShape(16.dp)),
        shape  = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // ── Medicine image (circular) ─────────────────────────────────
                MedicineImage(imageUrl = medication.imageUrl)

                Spacer(Modifier.width(14.dp))

                // ── Details ───────────────────────────────────────────────────
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text     = medication.name,
                            style    = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color    = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        StatusChip(status = status)
                    }

                    Spacer(Modifier.height(4.dp))

                    // Dose · Strength · Timing
                    Text(
                        text  = "${medication.dose} · ${medication.strength} · ${medication.timing}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(Modifier.height(6.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Duration badge
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text  = medication.duration,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }

                        // Frequency
                        Text(
                            text  = medication.frequency,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Conflict warning — Module B populates this
                    if (medication.conflicts.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.Warning,
                                contentDescription = "Conflict",
                                tint   = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text  = medication.conflicts.first().detail,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Schedule Action Buttons: Taken / Snooze
            // TODO: Module C - missed state should be inferred automatically if time passes with no action
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                FilledTonalButton(
                    onClick = {
                        // TODO: Module C - mark dose as taken in DB adherence log
                    },
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = StatusVerifiedBg,
                        contentColor = StatusVerified
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Taken", style = MaterialTheme.typography.labelMedium)
                }

                OutlinedButton(
                    onClick = {
                        // TODO: Module C/D - reschedule notification by snooze duration
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.Snooze, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Snooze", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun MedicineImage(imageUrl: String?) {
    Box(
        modifier = Modifier
            .size(62.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        if (imageUrl != null) {
            // Coil AsyncImage — loads from URL (set by Module A)
            AsyncImage(
                model               = imageUrl,
                contentDescription  = "Medicine image",
                contentScale        = ContentScale.Crop,
                modifier            = Modifier.fillMaxSize()
            )
        } else {
            // Placeholder pill icon when no image available
            Icon(
                imageVector     = Icons.Filled.Medication,
                contentDescription = "Medicine",
                tint            = MaterialTheme.colorScheme.primary,
                modifier        = Modifier.size(32.dp)
            )
        }
    }
}

// Medication status enum used for chip color
private enum class MedStatus { VERIFIED, NEEDS_REVIEW, CONFLICT }

@Composable
private fun StatusChip(status: MedStatus) {
    val (label, bgColor, textColor) = when (status) {
        MedStatus.VERIFIED     -> Triple("✓ Verified",    StatusVerifiedBg, StatusVerified)
        MedStatus.NEEDS_REVIEW -> Triple("⚠ Review",      StatusReviewBg,   StatusReview)
        MedStatus.CONFLICT     -> Triple("✕ Conflict",    StatusConflictBg, StatusConflict)
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = bgColor
    ) {
        Text(
            text     = label,
            style    = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color    = textColor,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Chatbot FAB
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ChatbotFab(onClick: () -> Unit) {
    ExtendedFloatingActionButton(
        onClick           = onClick,
        icon              = {
            Icon(
                imageVector = Icons.Filled.SmartToy,
                contentDescription = "Medi Chatbot"
            )
        },
        text              = { Text("Medi", fontWeight = FontWeight.SemiBold) },
        containerColor    = MaterialTheme.colorScheme.primary,
        contentColor      = MaterialTheme.colorScheme.onPrimary
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Notification Panel (dropdown from bell icon)
// ─────────────────────────────────────────────────────────────────────────────

private val dummyNotifications = listOf(
    "💊 Metformin · Due in 30 min (8:00 AM)",
    "⏰ Amlodipine · Take now (9:00 AM)",
    "⚠️ Lisinopril · Verify dose before taking"
)

@Composable
private fun NotificationPanel(
    onDismiss: () -> Unit,
    onViewAll: () -> Unit
) {
    Card(
        modifier = Modifier
            .padding(top = 4.dp, end = 8.dp)
            .width(300.dp)
            .shadow(12.dp, RoundedCornerShape(16.dp)),
        shape  = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text  = "Upcoming Reminders",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Close",
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))
            dummyNotifications.forEach { note ->
                Text(
                    text     = note,
                    style    = MaterialTheme.typography.bodySmall,
                    color    = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onViewAll, modifier = Modifier.fillMaxWidth()) {
                Text("View All Reminders →")
            }
        }
    }
}
