package com.medibridge.moduleD_shell.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
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
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import com.medibridge.core.model.*
import com.medibridge.core.theme.*

/**
 * HomeScreen — main screen of MediBridge.
 *
 * Displays:
 *   • Gradient top app bar with scanner + notification icons
 *   • Scrollable list of medication cards (from mockMedications)
 *   • FAB launching Chatbot ("Medi")
 *   • Notification dropdown panel (stub)
 *
 * EXTENSION POINTS:
 *   • Replace mockMedications with StateFlow from a HomeViewModel backed by Room DB.
 *   • TODO: Module A — scanner icon navigates to camera/OCR screen.
 *   • TODO: Module B — conflict chip tap opens safety detail screen.
 *   • TODO: Module C — card tap opens schedule calendar for that medication.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onScannerClick: () -> Unit,
    onChatbotClick: () -> Unit,
    onReminderClick: () -> Unit
) {
    var showNotificationPanel by remember { mutableStateOf(false) }
    val medications = mockMedications  // TODO: replace with ViewModel StateFlow from Room

    Scaffold(
        topBar = {
            HomeTopBar(
                onScannerClick = onScannerClick,
                onNotificationClick = { showNotificationPanel = !showNotificationPanel }
            )
        },
        floatingActionButton = {
            ChatbotFab(onClick = onChatbotClick)
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->

        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(bottom = 88.dp)
            ) {
                // ── Header banner ─────────────────────────────────────────────
                item {
                    HomeHeaderBanner(medicationCount = medications.size)
                }

                // ── Section label ─────────────────────────────────────────────
                item {
                    Text(
                        text = "Your Medications",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
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
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Top App Bar
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeTopBar(
    onScannerClick: () -> Unit,
    onNotificationClick: () -> Unit
) {
    TopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.LocalHospital,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "MediBridge",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
        },
        actions = {
            // Scanner icon — TODO: Module A wires OCR camera here
            IconButton(onClick = onScannerClick) {
                Icon(
                    imageVector = Icons.Filled.DocumentScanner,
                    contentDescription = "Scan Prescription",
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
            // Notification/bell icon — opens reminder panel
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
// Header Banner
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun HomeHeaderBanner(medicationCount: Int) {
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
                text = "Good morning! 👋",
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
        color  = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.18f),
        modifier = Modifier
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
// Medication Card
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // ── Medicine image (circular) ─────────────────────────────────────
            MedicineImage(imageUrl = medication.imageUrl)

            Spacer(Modifier.width(14.dp))

            // ── Details ───────────────────────────────────────────────────────
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
