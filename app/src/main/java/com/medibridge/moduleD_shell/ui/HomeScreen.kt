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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import android.content.Context
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
import com.medibridge.moduleC_schedule.viewmodel.ScheduleViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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
    onRecordClick: () -> Unit = {},
    onOpenDrawer: () -> Unit = {}
) {
    var showNotificationPanel by remember { mutableStateOf(false) }
    var showPrivacySheet by remember { mutableStateOf(false) }
    var isCaretakerMode by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("medibridge_prefs", Context.MODE_PRIVATE) }
    var patientName by remember { mutableStateOf(prefs.getString("patient_name", "My Health Profile") ?: "My Health Profile") }
    var caretakerPhone by remember { mutableStateOf(prefs.getString("caretaker_phone", "") ?: "") }
    var callStatus by remember { mutableStateOf("Ready") }

    val db = remember { AppDatabase.getInstance(context) }
    val roomMedEntities by db.medicationDao().getAllMedications().collectAsState(initial = emptyList())
    val medications = remember(roomMedEntities) {
        roomMedEntities.map { it.fromEntity() }
    }
    // Live unread notification count — drives badge on bell icon
    val unreadNotificationCount by db.notificationDao().getUnreadCount().collectAsState(initial = 0)
    // ScheduleViewModel — used by MedicationCard Taken/Snooze buttons
    val scheduleViewModel: ScheduleViewModel = androidx.lifecycle.viewmodel.compose.viewModel()

    Scaffold(
        topBar = {
            HomeTopBar(
                isCaretakerMode = isCaretakerMode,
                patientName = patientName,
                onOpenDrawer = onOpenDrawer,
                onNotificationClick = { showNotificationPanel = !showNotificationPanel },
                onSafetyClick = onSafetyClick,
                unreadCount = unreadNotificationCount
            )
        },
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Secondary smaller icon button stacked above Chatbot FAB - Emerald privacy shield
                SmallFloatingActionButton(
                    onClick = { showPrivacySheet = true },
                    containerColor = Color(0xFFE8F5E9),
                    contentColor = Color(0xFF2E7D32)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Security,
                        contentDescription = "Privacy Summary",
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Primary FAB: Chatbot ("Medi AI")
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
                        medications = medications,
                        isCaretakerMode = isCaretakerMode,
                        onToggleMode = { isCaretakerMode = !isCaretakerMode }
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

                // ── Section label (Spacious Rhythm & Eyebrow Tag) ──────────────
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                        ) {
                            Text(
                                text = "ACTIVE REGIMEN",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.1.sp,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isCaretakerMode) "$patientName's Medications" else "Daily Prescriptions",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Text(
                                    text = "${medications.size} Total",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
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
                                    text = "Use the Scan tab below to capture or upload a prescription, or use the Record tab to transcribe a doctor consultation in real-time.",
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
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        onTaken = {
                            val slotTime = med.schedule.firstOrNull()?.time ?: "08:00"
                            val reminderId = "${med.id}_${slotTime}_${java.time.LocalDate.now()}"
                            scheduleViewModel.markTaken(reminderId, med.name, med.dose)
                        },
                        onSnooze = {
                            val slotTime = med.schedule.firstOrNull()?.time ?: "08:00"
                            val reminderId = "${med.id}_${slotTime}_${java.time.LocalDate.now()}"
                            scheduleViewModel.snooze(reminderId, 15)
                        }
                    )
                }
            }
        }

        // ── Notification Modal Bottom Sheet ───────────────────────────────────
        if (showNotificationPanel) {
            NotificationBottomSheet(
                db = db,
                onDismiss = { showNotificationPanel = false },
                onViewAll = {
                    showNotificationPanel = false
                    onReminderClick()
                }
            )
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
    patientName: String,
    onOpenDrawer: () -> Unit,
    onNotificationClick: () -> Unit,
    onSafetyClick: () -> Unit,
    unreadCount: Int = 0
) {
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onOpenDrawer) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.15f),
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.Menu,
                            contentDescription = "Open Navigation Drawer",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        },
        title = {
            Column {
                Text(
                    text = "MediBridge",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Text(
                    text = if (isCaretakerMode) "Caretaker View • $patientName" else "Patient • $patientName",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        actions = {
            // Notification / bell icon with live badge - Amber badge container
            IconButton(onClick = onNotificationClick) {
                BadgedBox(
                    badge = {
                        if (unreadCount > 0) {
                            Badge(
                                containerColor = Color(0xFFD32F2F),
                                contentColor = Color.White
                            ) {
                                Text(if (unreadCount > 9) "9+" else unreadCount.toString())
                            }
                        }
                    }
                ) {
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFFFFF8E1),
                        modifier = Modifier.size(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.Notifications,
                                contentDescription = "Alerts & Notifications",
                                tint = Color(0xFFF57F17),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
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
    medications: List<MedicationObject>,
    isCaretakerMode: Boolean,
    onToggleMode: () -> Unit
) {
    val takenCount = medications.sumOf { med -> med.adherence.count { it.status.equals("taken", ignoreCase = true) } }
    val pendingCount = medications.sumOf { med -> med.adherence.count { it.status.equals("pending", ignoreCase = true) } }
    val alertCount = medications.count { it.conflicts.isNotEmpty() }

    // ── Taste-Skill Double-Bezel (Doppelrand) Architecture ─────────────────────
    // Outer Shell: hairline outer ring + generous radius + concentric inner offset
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .background(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                shape = RoundedCornerShape(26.dp)
            )
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.20f),
                shape = RoundedCornerShape(26.dp)
            )
            .padding(4.dp)
    ) {
        // Inner Core: Concentric surface with subtle gradient & ambient lighting
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = Color.Transparent,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color(0xFF0F766E), // Deep Teal Core
                                Color(0xFF0D9488)  // Vibrant Clinical Teal
                            )
                        )
                    )
                    .padding(20.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Micro-Eyebrow Tag
                        Surface(
                            shape = CircleShape,
                            color = Color.White.copy(alpha = 0.16f)
                        ) {
                            Text(
                                text = if (isCaretakerMode) "CARETAKER CONSOLE" else "PATIENT HEALTH DOSSIER",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.2.sp,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }

                        // Button-in-Button Mode Switch Pill
                        Surface(
                            shape = CircleShape,
                            color = Color.White.copy(alpha = 0.22f),
                            modifier = Modifier.clickable { onToggleMode() }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 4.dp)
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = Color.White,
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = if (isCaretakerMode) Icons.Filled.SupervisorAccount else Icons.Filled.Person,
                                            contentDescription = "Switch Mode",
                                            tint = Color(0xFF0F766E),
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = if (isCaretakerMode) "Caretaker View" else "Patient View",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    Text(
                        text = "${medications.size} medications scheduled today",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    Spacer(Modifier.height(16.dp))

                    // Spot Pastel Bento Metric Pills
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        PastelStatPill(
                            label = "$takenCount Taken",
                            icon = Icons.Filled.CheckCircle,
                            bgColor = Color(0xFFE8F5E9),
                            textColor = Color(0xFF1B5E20),
                            iconColor = Color(0xFF2E7D32),
                            modifier = Modifier.weight(1f)
                        )

                        PastelStatPill(
                            label = "$pendingCount Pending",
                            icon = Icons.Filled.Schedule,
                            bgColor = Color(0xFFE3F2FD),
                            textColor = Color(0xFF0D47A1),
                            iconColor = Color(0xFF1976D2),
                            modifier = Modifier.weight(1f)
                        )

                        if (alertCount > 0) {
                            PastelStatPill(
                                label = "$alertCount Alert",
                                icon = Icons.Filled.Warning,
                                bgColor = Color(0xFFFFEBEE),
                                textColor = Color(0xFFB71C1C),
                                iconColor = Color(0xFFD32F2F),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PastelStatPill(
    label: String,
    icon: ImageVector,
    bgColor: Color,
    textColor: Color,
    iconColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = bgColor,
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = textColor,
                maxLines = 1
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Medication Card (Double-Bezel Architecture + Button-in-Button CTAs)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun MedicationCard(
    medication: MedicationObject,
    modifier: Modifier = Modifier,
    onTaken: () -> Unit = {},
    onSnooze: () -> Unit = {}
) {
    val status = when {
        medication.conflicts.isNotEmpty() -> MedStatus.CONFLICT
        medication.needsVerification && !medication.verifiedByUser -> MedStatus.NEEDS_REVIEW
        else -> MedStatus.VERIFIED
    }

    // Double-Bezel: Outer machined frame
    Box(
        modifier = modifier
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                shape = RoundedCornerShape(22.dp)
            )
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.14f),
                shape = RoundedCornerShape(22.dp)
            )
            .padding(4.dp)
    ) {
        // Inner Core: Elevated card surface
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Medicine image
                    MedicineImage(imageUrl = medication.imageUrl)

                    Spacer(Modifier.width(14.dp))

                    // Details
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(
                                text = medication.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            StatusChip(status = status)
                        }

                        Spacer(Modifier.height(4.dp))

                        // Dose · Strength · Timing
                        Text(
                            text = "${medication.dose} · ${medication.strength} · ${medication.timing}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(Modifier.height(8.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Duration pill tag
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = medication.duration,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }

                            // Frequency
                            Text(
                                text = medication.frequency,
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
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = medication.conflicts.first().detail,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                // Button-in-Button Schedule Action Buttons: Taken / Snooze
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Island CTA: Taken with circular icon wrapper
                    Button(
                        onClick = onTaken,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = StatusVerified,
                            contentColor = Color.White
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = Color.White.copy(alpha = 0.22f),
                                modifier = Modifier.size(20.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }
                            Spacer(Modifier.width(8.dp))
                            Text("Taken", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                        }
                    }

                    // Island CTA: Snooze with circular icon wrapper
                    OutlinedButton(
                        onClick = onSnooze,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f),
                                modifier = Modifier.size(20.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Filled.Snooze,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }
                            Spacer(Modifier.width(8.dp))
                            Text("Snooze 15m", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelMedium)
                        }
                    }
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
        MedStatus.VERIFIED     -> Triple("Verified",     StatusVerifiedBg, StatusVerified)
        MedStatus.NEEDS_REVIEW -> Triple("Needs Review", StatusReviewBg,   StatusReview)
        MedStatus.CONFLICT     -> Triple("Conflict",     StatusConflictBg, StatusConflict)
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
    val infiniteTransition = rememberInfiniteTransition(label = "fabPulse")
    val fabScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "fabScale"
    )

    ExtendedFloatingActionButton(
        onClick = onClick,
        modifier = Modifier.scale(fabScale),
        icon = {
            Icon(
                imageVector = Icons.Filled.AutoAwesome,
                contentDescription = "Medi Chatbot",
                tint = Color(0xFFFFD54F), // Radiant gold/amber star
                modifier = Modifier.size(22.dp)
            )
        },
        text = { Text("Medi AI", fontWeight = FontWeight.Bold, color = Color.White) },
        containerColor = Color(0xFF00897B), // Vibrant medical teal
        contentColor = Color.White
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Notification Panel (dropdown from bell icon)
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotificationBottomSheet(
    db: AppDatabase,
    onDismiss: () -> Unit,
    onViewAll: () -> Unit
) {
    val notifications by db.notificationDao().getAllNotifications().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        db.notificationDao().markAllRead()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Notifications,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "Alerts & Notifications",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (notifications.isNotEmpty()) {
                    TextButton(onClick = { scope.launch { db.notificationDao().deleteAll() } }) {
                        Text("Clear All", color = MaterialTheme.colorScheme.error)
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            HorizontalDivider()
            Spacer(Modifier.height(10.dp))

            if (notifications.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "No active alerts",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "You are on track with your prescribed medication regimen.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp)
                ) {
                    items(notifications, key = { it.id }) { item ->
                        val (icon, tint) = when (item.type) {
                            "WRONG_TIME" -> Icons.Filled.Warning to Color(0xFFF57C00)
                            "MISSED_DOSE" -> Icons.Filled.ErrorOutline to Color(0xFFD32F2F)
                            "SAFETY_INTERACTION", "SAFETY_DUPLICATE" -> Icons.Filled.Dangerous to Color(0xFFC2185B)
                            else -> Icons.Filled.Info to MaterialTheme.colorScheme.primary
                        }

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
                                    .padding(14.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = item.type,
                                    tint = tint,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.message,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (!item.isRead) FontWeight.Bold else FontWeight.Normal,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    val timeAgo = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(Date(item.timestamp))
                                    Text(
                                        text = timeAgo,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onViewAll,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("View Full Schedule & Reminders")
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}
