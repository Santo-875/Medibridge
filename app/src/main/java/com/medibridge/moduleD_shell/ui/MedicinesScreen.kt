package com.medibridge.moduleD_shell.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.medibridge.core.db.AppDatabase
import com.medibridge.core.model.MedicationObject
import com.medibridge.core.model.fromEntity
import com.medibridge.moduleC_schedule.viewmodel.ScheduleViewModel
import com.medibridge.moduleD_shell.ui.components.*

enum class MedicineFilter {
    ALL, VERIFIED, REVIEW, CONFLICT
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedicinesScreen(
    onMedicationClick: (String) -> Unit,
    onRemindersClick: () -> Unit,
    onAddClick: () -> Unit
) {
    val context = LocalContext.current
    val db = remember { AppDatabase.getInstance(context) }
    val scheduleViewModel: ScheduleViewModel = androidx.lifecycle.viewmodel.compose.viewModel()

    val roomMedEntities by db.medicationDao().getAllMedications().collectAsState(initial = emptyList())
    val medications = remember(roomMedEntities) {
        roomMedEntities.map { it.fromEntity() }
    }

    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf(MedicineFilter.ALL) }

    val filteredMeds = remember(medications, searchQuery, selectedFilter) {
        medications.filter { med ->
            val matchesQuery = searchQuery.isBlank() ||
                med.name.contains(searchQuery, ignoreCase = true) ||
                med.strength.contains(searchQuery, ignoreCase = true)

            val matchesFilter = when (selectedFilter) {
                MedicineFilter.ALL -> true
                MedicineFilter.VERIFIED -> !med.hasRealConflict && (!med.needsVerification || med.verifiedByUser)
                MedicineFilter.REVIEW -> med.needsVerification && !med.verifiedByUser
                MedicineFilter.CONFLICT -> med.hasRealConflict
            }
            matchesQuery && matchesFilter
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Medicines",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    IconButton(onClick = onAddClick) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add medication",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = onRemindersClick) {
                        Icon(
                            imageVector = Icons.Default.NotificationsActive,
                            contentDescription = "View reminders",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp)
        ) {
            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                placeholder = {
                    Text(
                        text = "Search by medicine name...",
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear search",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                )
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Filter segmented toggle
            SegmentedToggle(
                items = listOf(MedicineFilter.ALL, MedicineFilter.VERIFIED, MedicineFilter.REVIEW, MedicineFilter.CONFLICT),
                selectedItem = selectedFilter,
                onItemSelected = { selectedFilter = it },
                label = { filter ->
                    when (filter) {
                        MedicineFilter.ALL -> "All"
                        MedicineFilter.VERIFIED -> "Verified"
                        MedicineFilter.REVIEW -> "Review"
                        MedicineFilter.CONFLICT -> "Alerts"
                    }
                }
            )

            Spacer(modifier = Modifier.height(14.dp))

            val actionStates = remember { mutableStateMapOf<String, DoseActionState>() }
            val takenCount = medications.count { actionStates[it.id] == DoseActionState.TAKEN }
            val totalCount = medications.size
            val adherencePercent = if (totalCount > 0) (takenCount.toFloat() / totalCount.toFloat()) else 0f
            val animatedProgress by animateFloatAsState(
                targetValue = adherencePercent,
                animationSpec = tween(600, easing = FastOutSlowInEasing),
                label = "adherenceProgress"
            )

            if (medications.isNotEmpty()) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.16f)),
                    tonalElevation = 1.dp
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.EventAvailable,
                                    contentDescription = null,
                                    tint = com.medibridge.core.theme.TealPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Today's Schedule Progress",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Text(
                                text = "$takenCount of $totalCount Taken",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = if (takenCount == totalCount && totalCount > 0) com.medibridge.core.theme.StatusVerified else com.medibridge.core.theme.TealPrimary
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        LinearProgressIndicator(
                            progress = { animatedProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = if (takenCount == totalCount && totalCount > 0) com.medibridge.core.theme.StatusVerified else com.medibridge.core.theme.TealPrimary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            Spacer(modifier = Modifier.height(4.dp))

            SectionHeader(
                title = "All Prescriptions",
                badgeCount = filteredMeds.size
            )

            if (filteredMeds.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    if (medications.isEmpty()) {
                        EmptyState(
                            icon = Icons.Default.Medication,
                            title = "No Medicines Added",
                            subtitle = "Scan your prescription or speak to add your first medicine in seconds.",
                            actionLabel = "Add Medicine",
                            onActionClick = onAddClick
                        )
                    } else {
                        EmptyState(
                            icon = Icons.Default.SearchOff,
                            title = "No Matches Found",
                            subtitle = "Try adjusting your search terms or filter selection."
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(bottom = 90.dp, top = 4.dp)
                ) {
                    items(
                        items = filteredMeds,
                        key = { it.id }
                    ) { med ->
                        val currentAction = actionStates[med.id] ?: DoseActionState.NOT_TAKEN

                        MediCard(
                            medication = med,
                            onClick = { onMedicationClick(med.id) },
                            actionState = currentAction,
                            onMarkTaken = {
                                actionStates[med.id] = DoseActionState.TAKEN
                                scheduleViewModel.markTaken("${med.id}_08:00_today", med.name, med.dose)
                            },
                            onSnooze = {
                                actionStates[med.id] = DoseActionState.SNOOZED
                                scheduleViewModel.snooze("${med.id}_08:00_today", 15)
                            },
                            onMarkNotTaken = {
                                actionStates[med.id] = DoseActionState.NOT_TAKEN
                            }
                        )
                    }
                }
            }
        }
    }
}
