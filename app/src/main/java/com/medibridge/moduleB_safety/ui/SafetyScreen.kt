package com.medibridge.moduleB_safety.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.medibridge.core.model.MedicationConflict
import com.medibridge.core.model.MedicationObject
import com.medibridge.core.theme.*
import com.medibridge.moduleB_safety.logic.SafetyEngine
import com.medibridge.moduleB_safety.logic.SafetyEvaluationResult
import com.medibridge.moduleB_safety.logic.SafetyRules
import com.medibridge.moduleB_safety.logic.SafetyVerdict

/**
 * Module B: Medication Safety & Reconciliation Screen.
 *
 * Provides comprehensive cross-verification, duplicate detection, therapeutic overlap checking,
 * and order-independent drug-drug interaction detection.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SafetyScreen(
    onBack: () -> Unit,
    viewModel: SafetyViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val evaluation = uiState.evaluationResult

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Shield,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "Medication Safety",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to Home",
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
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // ── 1. Demo Scenario Selector ──────────────────────────────────────
            item {
                Text(
                    text = "Demo Verification Scenarios",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    uiState.scenarios.forEachIndexed { index, scenario ->
                        val isSelected = index == uiState.selectedScenarioIndex
                        FilterChip(
                            selected = isSelected,
                            onClick = { viewModel.selectScenario(index) },
                            label = {
                                Text(
                                    text = scenario.title,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.primary
                            )
                        )
                    }
                }
            }

            // ── 2. Candidate Medication Card ──────────────────────────────────
            item {
                CandidateMedicationCard(
                    medication = uiState.currentMedication,
                    crossVerified = evaluation.crossVerified
                )
            }

            // ── 3. Primary Safety Verdict Banner ──────────────────────────────
            item {
                SafetyVerdictBanner(evaluation = evaluation)
            }

            // ── 4. Professional Review Recommendation (if flagged) ───────────
            if (evaluation.reviewRecommended) {
                item {
                    ReviewRecommendationBanner()
                }
            }

            // ── 5. Detected Conflicts Section ────────────────────────────────
            item {
                Text(
                    text = if (evaluation.conflicts.isNotEmpty()) {
                        "Safety Findings (${evaluation.conflicts.size})"
                    } else {
                        "Safety Findings"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            if (evaluation.conflicts.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(2.dp, RoundedCornerShape(16.dp)),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(StatusVerifiedBg),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.CheckCircle,
                                    contentDescription = null,
                                    tint = StatusVerified,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(Modifier.width(14.dp))
                            Column {
                                Text(
                                    text = "No Known Conflict Detected",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = StatusVerified
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = "Cross-verification complete against configured rules. No duplicate therapies or drug-drug interactions detected.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            } else {
                items(
                    items = evaluation.conflicts,
                    key = { it.type + it.withMedId + it.detail }
                ) { conflict ->
                    ConflictCard(
                        conflict = conflict,
                        history = uiState.activeHistory
                    )
                }
            }

            // ── 6. Medication History Cross-Verification List ───────────────────
            item {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Patient Medication History (${uiState.activeHistory.size} active)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "Cross-checked against existing prescriptions in the patient profile.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            items(
                items = uiState.activeHistory,
                key = { it.id }
            ) { historyMed ->
                HistoryMedicationItem(
                    medication = historyMed,
                    isConflicting = evaluation.conflicts.any { it.withMedId == historyMed.id }
                )
            }

            // ── 7. Prototype Medical Notice Disclaimer ────────────────────────
            item {
                Spacer(Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .size(18.dp)
                                .padding(top = 1.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "MediBridge Module B Prototype: Evaluated using local deterministic safety rules and configured knowledge base for demonstration. Always verify prescriptions with an authorized physician or pharmacist.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 16.sp
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Candidate Medication Card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CandidateMedicationCard(
    medication: MedicationObject,
    crossVerified: Boolean
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(3.dp, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "CANDIDATE MEDICATION",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 1.sp
                )

                // Cross verification badge
                if (crossVerified) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = StatusVerifiedBg
                    ) {
                        Text(
                            text = "✓ Cross-Verified",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = StatusVerified,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = StatusReviewBg
                    ) {
                        Text(
                            text = "⚠ Unverified Formula",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = StatusReview,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            Text(
                text = medication.name,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(Modifier.height(4.dp))

            Text(
                text = "${medication.strength} · ${medication.dose} · ${medication.frequency}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(4.dp))

            Text(
                text = "Timing: ${medication.timing} · Duration: ${medication.duration}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Safety Verdict Banner
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SafetyVerdictBanner(evaluation: SafetyEvaluationResult) {
    val (bgColor, borderColor, textColor, icon) = when (evaluation.verdict) {
        SafetyVerdict.SAFE -> Quadruple(
            StatusVerifiedBg,
            StatusVerified.copy(alpha = 0.4f),
            StatusVerified,
            Icons.Filled.Verified
        )
        SafetyVerdict.DUPLICATE -> Quadruple(
            StatusReviewBg,
            StatusReview.copy(alpha = 0.5f),
            StatusReview,
            Icons.Filled.ContentCopy
        )
        SafetyVerdict.OVERLAP -> Quadruple(
            StatusReviewBg,
            StatusReview.copy(alpha = 0.5f),
            StatusReview,
            Icons.Filled.DynamicFeed
        )
        SafetyVerdict.INTERACTION -> Quadruple(
            StatusConflictBg,
            StatusConflict.copy(alpha = 0.5f),
            StatusConflict,
            Icons.Filled.Warning
        )
        SafetyVerdict.UNVERIFIED -> Quadruple(
            StatusReviewBg,
            StatusReview.copy(alpha = 0.5f),
            StatusReview,
            Icons.AutoMirrored.Filled.HelpOutline
        )
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = bgColor,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, borderColor, RoundedCornerShape(16.dp))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(textColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = textColor,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(Modifier.width(14.dp))
            Column {
                Text(
                    text = evaluation.headline,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = textColor
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = evaluation.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = textColor.copy(alpha = 0.9f),
                    lineHeight = 18.sp
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Professional Review Banner
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ReviewRecommendationBanner() {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.MedicalServices,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    text = "Professional Review Recommended",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
                Text(
                    text = "A pharmacist or clinician should review this regimen prior to administration.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Conflict Card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ConflictCard(
    conflict: MedicationConflict,
    history: List<MedicationObject>
) {
    val conflictingMed = history.find { it.id == conflict.withMedId }

    val (badgeText, badgeBg, badgeColor) = when (conflict.type) {
        SafetyEngine.CONFLICT_TYPE_DUPLICATE -> Triple("DUPLICATE", StatusReviewBg, StatusReview)
        SafetyEngine.CONFLICT_TYPE_OVERLAP -> Triple("THERAPEUTIC OVERLAP", StatusReviewBg, StatusReview)
        SafetyEngine.CONFLICT_TYPE_INTERACTION -> Triple("DRUG INTERACTION", StatusConflictBg, StatusConflict)
        else -> Triple("VERIFICATION WARNING", StatusReviewBg, StatusReview)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(2.dp, RoundedCornerShape(14.dp)),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = badgeBg
                ) {
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = badgeColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }

                if (conflictingMed != null) {
                    Text(
                        text = "With: ${conflictingMed.name}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            Text(
                text = conflict.detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 18.sp
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// History Medication Item
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun HistoryMedicationItem(
    medication: MedicationObject,
    isConflicting: Boolean
) {
    val therapeuticClass = SafetyRules.getTherapeuticClass(medication.name)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isConflicting) {
                    Modifier.border(1.dp, StatusConflict.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                } else {
                    Modifier
                }
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isConflicting) StatusConflictBg.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(
                        if (isConflicting) StatusConflictBg else MaterialTheme.colorScheme.surfaceVariant
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isConflicting) Icons.Filled.Warning else Icons.Filled.Medication,
                    contentDescription = null,
                    tint = if (isConflicting) StatusConflict else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = medication.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = medication.strength,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.height(2.dp))

                Text(
                    text = "${medication.frequency} · ${medication.timing}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (therapeuticClass != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = therapeuticClass,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
