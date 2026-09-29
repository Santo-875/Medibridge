package com.medibridge.moduleD_shell.ui

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.medibridge.core.db.AppDatabase
import com.medibridge.core.model.MedicationObject
import com.medibridge.core.model.ScheduleSlot
import com.medibridge.core.model.fromEntity
import com.medibridge.core.model.toEntity
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * AddEditMedicationScreen — Allows the user to manually add or edit a medication,
 * including one or more dose slots with user-set time windows (windowStart / windowEnd).
 *
 * The windowStart/windowEnd on each ScheduleSlot is read by checkAndAlertWrongTime()
 * in ScheduleRepository — no hardcoded fallback window when these are set.
 *
 * Reachable from:
 *   • Reminders screen top-bar "+" button / FAB → add mode (medicationId = null)
 *   • (Future) tapping a medication card → edit mode (medicationId = med.id)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditMedicationScreen(
    onBack: () -> Unit,
    medicationId: String? = null  // null = add, non-null = edit (pre-fill)
) {
    val context = LocalContext.current
    val db      = remember { AppDatabase.getInstance(context) }
    val scope   = rememberCoroutineScope()

    // ── Per-slot state container ───────────────────────────────────────────────
    data class SlotState(
        val label: String       = "Morning",
        val time: String        = "08:00",
        val windowStart: String = "07:00",
        val windowEnd: String   = "09:30"
    )

    // Presets for quick slot labelling
    val slotDefaults = mapOf(
        "Morning"   to SlotState("Morning",   "08:00", "07:00", "09:30"),
        "Afternoon" to SlotState("Afternoon", "13:00", "12:00", "14:30"),
        "Evening"   to SlotState("Evening",   "17:00", "16:00", "18:30"),
        "Night"     to SlotState("Night",     "21:00", "20:00", "22:30")
    )

    // ── Form state ─────────────────────────────────────────────────────────────
    var name       by remember { mutableStateOf("") }
    var strength   by remember { mutableStateOf("") }
    var dose       by remember { mutableStateOf("1 tablet") }
    var frequency  by remember { mutableStateOf("Once daily") }
    var duration   by remember { mutableStateOf("30 days") }
    var isSaving   by remember { mutableStateOf(false) }
    var isLoading  by remember { mutableStateOf(medicationId != null) }
    var slots      by remember { mutableStateOf(listOf(SlotState())) }

    // Frequency dropdown expansion
    var expandedFreq by remember { mutableStateOf(false) }
    val frequencyOptions = listOf(
        "Once daily", "Twice daily", "Three times daily",
        "Every 8 hours", "Every 12 hours", "As needed", "Weekly"
    )

    // Time-picker dialog state
    var activePickerSlotIdx by remember { mutableStateOf(-1) }
    var activePickerField   by remember { mutableStateOf("time") } // "time"|"windowStart"|"windowEnd"
    var pickerHour          by remember { mutableStateOf(8) }
    var pickerMinute        by remember { mutableStateOf(0) }

    // ── Pre-fill in edit mode ──────────────────────────────────────────────────
    LaunchedEffect(medicationId) {
        if (medicationId != null) {
            val entity = db.medicationDao().getMedicationById(medicationId)
            if (entity != null) {
                val med = entity.fromEntity()
                name      = med.name
                strength  = med.strength
                dose      = med.dose
                frequency = med.frequency
                duration  = med.duration
                slots = if (med.schedule.isNotEmpty()) {
                    med.schedule.map { s ->
                        SlotState(
                            label       = s.slot,
                            time        = s.time,
                            windowStart = s.windowStart ?: minuteOffset(s.time, -60),
                            windowEnd   = s.windowEnd   ?: minuteOffset(s.time, +90)
                        )
                    }
                } else listOf(SlotState())
            }
            isLoading = false
        }
    }

    // ── Save ───────────────────────────────────────────────────────────────────
    fun save() {
        if (name.isBlank()) {
            Toast.makeText(context, "Medication name is required", Toast.LENGTH_SHORT).show()
            return
        }
        isSaving = true
        scope.launch {
            try {
                val id = medicationId ?: UUID.randomUUID().toString()
                val scheduleSlots = slots.map { s ->
                    ScheduleSlot(
                        time        = s.time,
                        slot        = s.label,
                        withFood    = false,
                        windowStart = s.windowStart,
                        windowEnd   = s.windowEnd
                    )
                }
                val med = MedicationObject(
                    id                 = id,
                    name               = name.trim(),
                    strength           = strength.trim().ifBlank { "Standard" },
                    dose               = dose.trim().ifBlank { "1 tablet" },
                    frequency          = frequency,
                    timing             = slots.firstOrNull()?.label ?: "As prescribed",
                    duration           = duration.trim().ifBlank { "Ongoing" },
                    confidence         = 1.0f,
                    needsVerification  = false,
                    verifiedByUser     = true,
                    crossVerified      = false,
                    conflicts          = emptyList(),
                    reviewRecommended  = false,
                    schedule           = scheduleSlots,
                    adherence          = emptyList(),
                    summary            = "Manually added: ${name.trim()} ${strength.trim()}. $frequency.",
                    sideEffects        = emptyList(),
                    visibleTo          = listOf("patient", "caregiver", "doctor"),
                    sourceType         = "manual",
                    caretakerPhone     = "+65 9123 4567",
                    callReminderStatus = "not_set"
                )
                db.medicationDao().upsertMedication(med.toEntity())
                Toast.makeText(
                    context,
                    if (medicationId != null) "Medication updated" else "Medication added",
                    Toast.LENGTH_SHORT
                ).show()
                onBack()
            } catch (e: Exception) {
                android.util.Log.e("AddEditMedScreen", "Failed to save medication", e)
                Toast.makeText(context, "Save failed: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                isSaving = false
            }
        }
    }

    // ── Time-picker dialog ─────────────────────────────────────────────────────
    if (activePickerSlotIdx >= 0) {
        AlertDialog(
            onDismissRequest = { activePickerSlotIdx = -1 },
            title = {
                Text(
                    text = when (activePickerField) {
                        "windowStart" -> "Set Window Start"
                        "windowEnd"   -> "Set Window End"
                        else          -> "Set Dose Time"
                    },
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    val hStr = pickerHour.toString().padStart(2, '0')
                    val mStr = pickerMinute.toString().padStart(2, '0')
                    Text(
                        text = "Selected: $hStr:$mStr",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("Hour (0–23)", style = MaterialTheme.typography.labelSmall)
                    Slider(
                        value         = pickerHour.toFloat(),
                        onValueChange = { pickerHour = it.toInt() },
                        valueRange    = 0f..23f,
                        steps         = 22
                    )
                    Text("Minute", style = MaterialTheme.typography.labelSmall)
                    Slider(
                        value         = pickerMinute.toFloat(),
                        onValueChange = { pickerMinute = (it.toInt() / 5) * 5 },
                        valueRange    = 0f..55f,
                        steps         = 10
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val hh  = pickerHour.toString().padStart(2, '0')
                    val mm  = pickerMinute.toString().padStart(2, '0')
                    val tStr = "$hh:$mm"
                    val idx  = activePickerSlotIdx
                    slots = slots.mapIndexed { i, s ->
                        if (i == idx) when (activePickerField) {
                            "windowStart" -> s.copy(windowStart = tStr)
                            "windowEnd"   -> s.copy(windowEnd   = tStr)
                            else          -> s.copy(time        = tStr)
                        } else s
                    }
                    activePickerSlotIdx = -1
                }) { Text("Set") }
            },
            dismissButton = {
                TextButton(onClick = { activePickerSlotIdx = -1 }) { Text("Cancel") }
            }
        )
    }

    // ── Screen scaffold ────────────────────────────────────────────────────────
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text       = if (medicationId != null) "Edit Medication" else "Add Medication",
                        fontWeight = FontWeight.Bold,
                        color      = MaterialTheme.colorScheme.onPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                },
                actions = {
                    TextButton(
                        onClick  = { if (!isSaving) save() },
                        enabled  = !isSaving,
                        colors   = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier    = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color       = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Text("Save", fontWeight = FontWeight.Bold)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->

        if (isLoading) {
            Box(
                modifier        = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }
            return@Scaffold
        }

        LazyColumn(
            modifier       = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {

            // ── Medication Details ─────────────────────────────────────────
            item {
                FormCard(title = "Medication Details", icon = Icons.Filled.Medication) {
                    OutlinedTextField(
                        value         = name,
                        onValueChange = { name = it },
                        label         = { Text("Medication Name *") },
                        placeholder   = { Text("e.g. Metformin") },
                        modifier      = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                        singleLine    = true,
                        leadingIcon   = {
                            Icon(Icons.Filled.LocalPharmacy, null, modifier = Modifier.size(20.dp))
                        }
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value         = strength,
                        onValueChange = { strength = it },
                        label         = { Text("Strength") },
                        placeholder   = { Text("e.g. 500mg, 10mg/5ml") },
                        modifier      = Modifier.fillMaxWidth(),
                        singleLine    = true
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value         = dose,
                        onValueChange = { dose = it },
                        label         = { Text("Dose per intake") },
                        placeholder   = { Text("e.g. 1 tablet, 5ml") },
                        modifier      = Modifier.fillMaxWidth(),
                        singleLine    = true
                    )
                    Spacer(Modifier.height(10.dp))
                    ExposedDropdownMenuBox(
                        expanded        = expandedFreq,
                        onExpandedChange = { expandedFreq = it }
                    ) {
                        OutlinedTextField(
                            value         = frequency,
                            onValueChange = {},
                            label         = { Text("Frequency") },
                            readOnly      = true,
                            modifier      = Modifier.fillMaxWidth().menuAnchor(),
                            trailingIcon  = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedFreq)
                            }
                        )
                        ExposedDropdownMenu(
                            expanded        = expandedFreq,
                            onDismissRequest = { expandedFreq = false }
                        ) {
                            frequencyOptions.forEach { opt ->
                                DropdownMenuItem(
                                    text    = { Text(opt) },
                                    onClick = { frequency = opt; expandedFreq = false }
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value         = duration,
                        onValueChange = { duration = it },
                        label         = { Text("Duration") },
                        placeholder   = { Text("e.g. 30 days, Ongoing") },
                        modifier      = Modifier.fillMaxWidth(),
                        singleLine    = true
                    )
                }
            }

            // ── Dose Time Windows ──────────────────────────────────────────
            item {
                FormCard(title = "Dose Time Windows", icon = Icons.Filled.Schedule) {
                    Text(
                        text  = "Set a valid intake window per dose. Wrong-time alerts fire if the patient takes outside this range.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(14.dp))

                    slots.forEachIndexed { idx, slotState ->
                        if (idx > 0) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        }

                        // Slot header row
                        Row(
                            modifier              = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment     = Alignment.CenterVertically
                        ) {
                            Text(
                                text       = "Dose Slot ${idx + 1}",
                                style      = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color      = MaterialTheme.colorScheme.primary
                            )
                            if (slots.size > 1) {
                                IconButton(
                                    onClick = { slots = slots.filterIndexed { i, _ -> i != idx } },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        Icons.Filled.Close,
                                        contentDescription = "Remove slot",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(8.dp))

                        // Slot label chips
                        val labelOptions = listOf("Morning", "Afternoon", "Evening", "Night")
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            labelOptions.forEach { lbl ->
                                FilterChip(
                                    selected  = slotState.label == lbl,
                                    onClick   = {
                                        val defaults = slotDefaults[lbl] ?: SlotState(lbl)
                                        slots = slots.mapIndexed { i, s ->
                                            if (i == idx) defaults else s
                                        }
                                    },
                                    label     = { Text(lbl, style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                        }

                        Spacer(Modifier.height(10.dp))

                        // Time fields
                        Row(
                            modifier              = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TimeButton(
                                label    = "Dose Time",
                                value    = slotState.time,
                                modifier = Modifier.weight(1f),
                                onClick  = {
                                    val parts = slotState.time.split(":")
                                    pickerHour   = parts.getOrNull(0)?.toIntOrNull() ?: 8
                                    pickerMinute = parts.getOrNull(1)?.toIntOrNull() ?: 0
                                    activePickerField   = "time"
                                    activePickerSlotIdx = idx
                                }
                            )
                        }

                        Spacer(Modifier.height(8.dp))

                        Row(
                            modifier              = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TimeButton(
                                label    = "Window Start",
                                value    = slotState.windowStart,
                                modifier = Modifier.weight(1f),
                                onClick  = {
                                    val parts = slotState.windowStart.split(":")
                                    pickerHour   = parts.getOrNull(0)?.toIntOrNull() ?: 7
                                    pickerMinute = parts.getOrNull(1)?.toIntOrNull() ?: 0
                                    activePickerField   = "windowStart"
                                    activePickerSlotIdx = idx
                                }
                            )
                            TimeButton(
                                label    = "Window End",
                                value    = slotState.windowEnd,
                                modifier = Modifier.weight(1f),
                                onClick  = {
                                    val parts = slotState.windowEnd.split(":")
                                    pickerHour   = parts.getOrNull(0)?.toIntOrNull() ?: 9
                                    pickerMinute = parts.getOrNull(1)?.toIntOrNull() ?: 30
                                    activePickerField   = "windowEnd"
                                    activePickerSlotIdx = idx
                                }
                            )
                        }

                        Spacer(Modifier.height(4.dp))
                        Text(
                            text  = "Patient may take this dose between ${slotState.windowStart} – ${slotState.windowEnd}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(
                        onClick  = { slots = slots + SlotState() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Add Another Dose Slot")
                    }
                }
            }

            // ── Primary Save button ────────────────────────────────────────
            item {
                Button(
                    onClick  = { if (!isSaving) save() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    enabled = !isSaving,
                    shape   = RoundedCornerShape(14.dp)
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier    = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color       = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(Modifier.width(8.dp))
                    } else {
                        Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        text       = if (medicationId != null) "Update Medication" else "Save Medication",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Reusable sub-composables
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun FormCard(
    title: String,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(16.dp),
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector        = icon,
                    contentDescription = null,
                    tint               = MaterialTheme.colorScheme.primary,
                    modifier           = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text       = title,
                    style      = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(14.dp))
            content()
        }
    }
}

@Composable
private fun TimeButton(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick  = onClick,
        modifier = modifier,
        shape    = RoundedCornerShape(10.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text  = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Schedule,
                    contentDescription = null,
                    tint     = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text       = value,
                    fontWeight = FontWeight.Bold,
                    style      = MaterialTheme.typography.bodyMedium,
                    color      = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Time math helpers
// ─────────────────────────────────────────────────────────────────────────────

private fun minuteOffset(time: String, offsetMinutes: Int): String {
    return try {
        val parts = time.split(":").map { it.toInt() }
        val total = parts[0] * 60 + parts[1] + offsetMinutes
        val h     = ((total / 60) % 24 + 24) % 24
        val m     = ((total % 60) + 60) % 60
        "${h.toString().padStart(2, '0')}:${m.toString().padStart(2, '0')}"
    } catch (_: Exception) { "08:00" }
}
