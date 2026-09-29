package com.medibridge.moduleD_shell.ui

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.medibridge.core.db.AppDatabase
import com.medibridge.core.db.RecordingEntity
import com.medibridge.core.model.MedicationObject
import com.medibridge.core.network.BackendClient
import com.medibridge.core.pipeline.MedicationIngestionPipeline
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * RecordingScreen — Bilingual doctor consultation voice recorder.
 *
 * v4 additions:
 *   • Sub-tabs: Transcribed | Raw/Untranslated
 *   • Top-right "Voice Storage" summary icon
 *   • Backend failure stores status="untranslated" with real audio path, no fallback merge
 *   • "Convert to text" retry button per untranslated item
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordingScreen(
    onBack: () -> Unit = {},
    onNavigateToReminders: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()
    val db      = remember { AppDatabase.getInstance(context) }

    // Recording state
    var isRecording          by remember { mutableStateOf(false) }
    var isProcessing         by remember { mutableStateOf(false) }
    var recordingDurationSeconds by remember { mutableStateOf(0) }
    var mediaRecorder        by remember { mutableStateOf<MediaRecorder?>(null) }
    var currentAudioFile     by remember { mutableStateOf<File?>(null) }

    // Result state (most recent session)
    var lastTamilTranscript  by remember { mutableStateOf<String?>(null) }
    var lastEnglishTranscript by remember { mutableStateOf<String?>(null) }
    var lastSummaryText      by remember { mutableStateOf<String?>(null) }
    var extractedMeds        by remember { mutableStateOf<List<MedicationObject>>(emptyList()) }
    var statusMessage        by remember { mutableStateOf<String?>(null) }
    var selectedLanguageTab  by remember { mutableStateOf(0) } // 0=Tamil 1=English

    // Sub-tab: 0=Transcribed, 1=Raw/Untranslated
    var selectedSubTab       by remember { mutableStateOf(0) }

    // Voice Storage dialog
    var showStorageDialog    by remember { mutableStateOf(false) }

    // Retry state per recording id
    var retryingIds          by remember { mutableStateOf(setOf<Long>()) }

    // Historical recordings from DB
    val allRecordings by db.recordingDao().getAllRecordings().collectAsState(initial = emptyList())
    val transcribedRecordings   = allRecordings.filter { it.status == "transcribed" }
    val untranslatedRecordings  = allRecordings.filter { it.status == "untranslated" || it.status == "pending" }

    // Storage stats
    val totalStorageBytes = allRecordings.sumOf { r ->
        try { File(r.filePath).length() } catch (_: Exception) { 0L }
    }
    val storageMb = totalStorageBytes / (1024.0 * 1024.0)

    // Permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            Toast.makeText(context, "Microphone permission granted", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Microphone permission is required to record consultations", Toast.LENGTH_LONG).show()
        }
    }

    // Recording timer
    LaunchedEffect(isRecording) {
        if (isRecording) {
            recordingDurationSeconds = 0
            while (isRecording) {
                delay(1000)
                recordingDurationSeconds++
            }
        }
    }

    // Pulse animation for recording button
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue  = 1.25f,
        animationSpec = infiniteRepeatable(
            animation  = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    fun startRecording() {
        val hasPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasPermission) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        try {
            val audioFile = File(context.cacheDir, "consultation_${System.currentTimeMillis()}.m4a")
            currentAudioFile = audioFile
            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION") MediaRecorder()
            }
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            recorder.setOutputFile(audioFile.absolutePath)
            recorder.prepare()
            recorder.start()
            mediaRecorder = recorder
            isRecording   = true
            statusMessage = null
        } catch (e: Exception) {
            Log.e("RecordingScreen", "Failed to start MediaRecorder", e)
            Toast.makeText(context, "Failed to start recorder: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Attempt backend transcription for [file], returning (tamilText, englishText, doctorNotes, meds).
     * Returns null for all strings on failure, never substitutes the bilingual demo fallback.
     */
    suspend fun attemptBackendTranscription(
        file: File
    ): Triple<String, String, String>? {
        return try {
            val reqBody  = file.asRequestBody("audio/m4a".toMediaTypeOrNull())
            val part     = MultipartBody.Part.createFormData("file", file.name, reqBody)
            val resp     = BackendClient.getService().uploadSpeech(part)
            val tamil    = resp.transcriptTamil   ?: ""
            val english  = resp.transcriptEnglish ?: resp.transcript ?: ""
            val summary  = resp.summary
            val notes    = summary?.doctorNotesSummary ?: summary?.diagnosis ?: ""
            if (tamil.isBlank() && english.isBlank()) null
            else Triple(tamil, english, notes)
        } catch (e: Exception) {
            Log.w("RecordingScreen", "Backend transcription failed: ${e.message}")
            null
        }
    }

    fun stopRecording() {
        try {
            mediaRecorder?.stop()
            mediaRecorder?.release()
        } catch (e: Exception) {
            Log.e("RecordingScreen", "Error stopping recorder", e)
        }
        mediaRecorder  = null
        isRecording    = false
        isProcessing   = true

        scope.launch {
            try {
                val file = currentAudioFile

                if (file != null && file.exists()) {
                    val backendResult = attemptBackendTranscription(file)

                    if (backendResult != null) {
                        val (tamil, english, notes) = backendResult

                        // ── Real backend success ──────────────────────────────
                        val combinedTranscript = buildString {
                            if (tamil.isNotBlank())   append("Tamil:\n$tamil\n\n")
                            if (english.isNotBlank()) append("English:\n$english")
                        }.trim()

                        val recEntity = RecordingEntity(
                            timestamp  = System.currentTimeMillis(),
                            filePath   = file.absolutePath,
                            transcript = combinedTranscript,
                            summary    = notes.ifBlank { combinedTranscript },
                            status     = "transcribed"
                        )
                        val rowId = db.recordingDao().insertRecording(recEntity)
                        Log.i("RecordingScreen", "Inserted transcribed RecordingEntity row=$rowId")

                        lastTamilTranscript   = tamil.ifBlank { null }
                        lastEnglishTranscript = english.ifBlank { null }
                        lastSummaryText       = notes.ifBlank { null }
                        statusMessage         = "Consultation transcribed and saved."

                        // Try to ingest medications via pipeline
                        try {
                            val resp2    = BackendClient.getService().uploadSpeech(
                                MultipartBody.Part.createFormData(
                                    "file", file.name,
                                    file.asRequestBody("audio/m4a".toMediaTypeOrNull())
                                )
                            )
                            val summary2 = resp2.summary
                            if (summary2 != null && summary2.medications.isNotEmpty()) {
                                val bilingualSummary = buildString {
                                    if (tamil.isNotBlank())   append("Tamil: $tamil\n")
                                    if (english.isNotBlank()) append("English: $english\n")
                                    if (notes.isNotBlank())   append("Notes: $notes")
                                }.trim()
                                val candidateMeds = summary2.medications.map { medItem ->
                                    BackendClient.mapSpeechMedicationToObject(medItem, summary2).copy(
                                        summary           = bilingualSummary,
                                        consultationNotes = bilingualSummary
                                    )
                                }
                                val evaluated = MedicationIngestionPipeline.evaluatePipeline(context, candidateMeds)
                                MedicationIngestionPipeline.commitPipeline(context, evaluated)
                                extractedMeds = candidateMeds
                                statusMessage = "Transcribed & saved ${candidateMeds.size} medication(s)."
                            }
                        } catch (pipeEx: Exception) {
                            Log.e("RecordingScreen", "Pipeline ingestion failed (non-fatal)", pipeEx)
                        }

                    } else {
                        // ── Backend failed → store as untranslated with real audio path ──
                        // Do NOT use the bilingual demo fallback here.
                        val recEntity = RecordingEntity(
                            timestamp  = System.currentTimeMillis(),
                            filePath   = file.absolutePath,
                            transcript = "",
                            summary    = "",
                            status     = "untranslated"
                        )
                        val rowId = db.recordingDao().insertRecording(recEntity)
                        Log.w("RecordingScreen", "Backend unavailable — stored untranslated recording row=$rowId path=${file.absolutePath}")
                        statusMessage = "Backend unavailable — recording saved as Raw (audio path preserved). Use 'Convert to text' when online."
                        lastTamilTranscript   = null
                        lastEnglishTranscript = null
                        lastSummaryText       = null
                    }
                } else {
                    statusMessage = "No audio file captured."
                }

                Toast.makeText(context, "Recording saved", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Log.e("RecordingScreen", "Processing error during audio ingestion", e)
                statusMessage = "Error saving recording: ${e.message}"
                Toast.makeText(context, "Error saving recording (check Logcat)", Toast.LENGTH_LONG).show()
            } finally {
                isProcessing = false
            }
        }
    }

    // ── Retry transcription for an untranslated recording ────────────────────
    fun retryTranscription(recording: RecordingEntity) {
        retryingIds = retryingIds + recording.id
        scope.launch {
            try {
                val file = File(recording.filePath)
                if (!file.exists()) {
                    Toast.makeText(context, "Audio file not found at ${recording.filePath}", Toast.LENGTH_LONG).show()
                    retryingIds = retryingIds - recording.id
                    return@launch
                }
                val result = attemptBackendTranscription(file)
                if (result != null) {
                    val (tamil, english, notes) = result
                    val combined = buildString {
                        if (tamil.isNotBlank())   append("Tamil:\n$tamil\n\n")
                        if (english.isNotBlank()) append("English:\n$english")
                    }.trim()
                    db.recordingDao().updateTranscript(
                        id         = recording.id,
                        transcript = combined,
                        summary    = notes.ifBlank { combined },
                        status     = "transcribed"
                    )
                    Toast.makeText(context, "Transcription succeeded!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Transcription still unavailable. Try again later.", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Log.e("RecordingScreen", "Retry transcription failed", e)
                Toast.makeText(context, "Retry failed: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                retryingIds = retryingIds - recording.id
            }
        }
    }

    // ── Voice Storage summary dialog ─────────────────────────────────────────
    if (showStorageDialog) {
        AlertDialog(
            onDismissRequest = { showStorageDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Filled.Storage,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = {
                Text("Voice Storage Summary", fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    StorageStat("Total recordings", "${allRecordings.size}")
                    StorageStat("Transcribed", "${transcribedRecordings.size}")
                    StorageStat("Raw / Untranslated", "${untranslatedRecordings.size}")
                    StorageStat("Estimated storage", String.format("%.2f MB", storageMb))
                }
            },
            confirmButton = {
                TextButton(onClick = { showStorageDialog = false }) { Text("Close") }
            }
        )
    }

    // ── Scaffold ──────────────────────────────────────────────────────────────
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Voice Consultation", fontWeight = FontWeight.Bold)
                        Text(
                            "Sarvam AI · தமிழ் & English Translation",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Voice Storage icon
                    IconButton(onClick = { showStorageDialog = true }) {
                        BadgedBox(
                            badge = {
                                if (untranslatedRecordings.isNotEmpty()) {
                                    Badge { Text("${untranslatedRecordings.size}") }
                                }
                            }
                        ) {
                            Icon(
                                imageVector        = Icons.Filled.Storage,
                                contentDescription = "Voice Storage",
                                tint               = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {

            // ── Header info card ───────────────────────────────────────────
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape    = RoundedCornerShape(16.dp),
                    colors   = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    )
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector        = Icons.Filled.RecordVoiceOver,
                            contentDescription = null,
                            tint               = MaterialTheme.colorScheme.primary,
                            modifier           = Modifier.size(36.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                text       = "Bilingual Audio Pipeline",
                                style      = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color      = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text  = "Speak in தமிழ் (Tamil) or English. Sarvam AI transcribes Tamil speech, translates it to English, extracts prescriptions, and checks safety.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                            )
                        }
                    }
                }
            }

            // ── Recording Controls Card ────────────────────────────────────
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape    = RoundedCornerShape(20.dp),
                    colors   = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                    )
                ) {
                    Column(
                        modifier            = Modifier.fillMaxWidth().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val minutes    = recordingDurationSeconds / 60
                        val seconds    = recordingDurationSeconds % 60
                        val timerText  = String.format(Locale.US, "%02d:%02d", minutes, seconds)

                        Text(
                            text  = if (isRecording) timerText else if (isProcessing) "Processing..." else "Ready to Record",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(20.dp))

                        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(120.dp)) {
                            if (isRecording) {
                                Box(
                                    modifier = Modifier
                                        .size(110.dp)
                                        .scale(pulseScale)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.error.copy(alpha = 0.25f))
                                )
                            }
                            FilledIconButton(
                                onClick = {
                                    if (isRecording) stopRecording()
                                    else if (!isProcessing) startRecording()
                                },
                                modifier = Modifier.size(80.dp),
                                colors   = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = if (isRecording) MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.primary
                                ),
                                enabled = !isProcessing
                            ) {
                                if (isProcessing) {
                                    CircularProgressIndicator(
                                        color     = Color.White,
                                        modifier  = Modifier.size(36.dp),
                                        strokeWidth = 3.dp
                                    )
                                } else {
                                    Icon(
                                        imageVector        = if (isRecording) Icons.Filled.Stop else Icons.Filled.Mic,
                                        contentDescription = if (isRecording) "Stop Recording" else "Start Recording",
                                        tint               = Color.White,
                                        modifier           = Modifier.size(40.dp)
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = when {
                                isRecording  -> "Recording audio... Tap red button to finish"
                                isProcessing -> "Uploading audio to Sarvam AI & translating..."
                                else         -> "Tap the microphone to begin consultation recording"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // ── Status message ─────────────────────────────────────────────
            if (statusMessage != null) {
                item {
                    Surface(
                        shape  = RoundedCornerShape(12.dp),
                        color  = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text       = statusMessage!!,
                                style      = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color      = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
            }

            // ── Results Card (last session — Tamil/English tabs) ───────────
            if (lastTamilTranscript != null || lastEnglishTranscript != null) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape    = RoundedCornerShape(16.dp),
                        colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier              = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment     = Alignment.CenterVertically
                            ) {
                                Text(
                                    text       = "Consultation Output",
                                    style      = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                SingleChoiceSegmentedButtonRow {
                                    SegmentedButton(
                                        selected = selectedLanguageTab == 0,
                                        onClick  = { selectedLanguageTab = 0 },
                                        shape    = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                                    ) { Text("தமிழ்", style = MaterialTheme.typography.labelSmall) }
                                    SegmentedButton(
                                        selected = selectedLanguageTab == 1,
                                        onClick  = { selectedLanguageTab = 1 },
                                        shape    = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                                    ) { Text("English", style = MaterialTheme.typography.labelSmall) }
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                            val displayedText = if (selectedLanguageTab == 0)
                                lastTamilTranscript ?: "No Tamil transcript available."
                            else
                                lastEnglishTranscript ?: "No English translation available."

                            Surface(
                                shape    = RoundedCornerShape(8.dp),
                                color    = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text     = displayedText,
                                    style    = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                            if (!lastSummaryText.isNullOrBlank()) {
                                Spacer(Modifier.height(8.dp))
                                Text("Clinical Summary Note:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.height(2.dp))
                                Text(lastSummaryText!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (extractedMeds.isNotEmpty()) {
                                Spacer(Modifier.height(12.dp))
                                Text("Ingested Medications (${extractedMeds.size}):", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(6.dp))
                                extractedMeds.forEach { med ->
                                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("• ${med.name} (${med.strength})", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                        Text(med.frequency, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                                Spacer(Modifier.height(12.dp))
                                Button(onClick = onNavigateToReminders, modifier = Modifier.fillMaxWidth()) {
                                    Icon(Icons.Filled.Notifications, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("View Schedule in Reminders")
                                }
                            }
                        }
                    }
                }
            }

            // ── Sub-tabs: Transcribed | Raw/Untranslated ───────────────────
            item {
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Text(
                        text       = "Past Consultation Recordings",
                        style      = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.height(8.dp))
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = selectedSubTab == 0,
                        onClick  = { selectedSubTab = 0 },
                        shape    = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Transcribed (${transcribedRecordings.size})")
                        }
                    }
                    SegmentedButton(
                        selected = selectedSubTab == 1,
                        onClick  = { selectedSubTab = 1 },
                        shape    = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.HourglassEmpty, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Raw (${untranslatedRecordings.size})")
                        }
                    }
                }
            }

            // ── Transcribed sub-tab ────────────────────────────────────────
            if (selectedSubTab == 0) {
                if (transcribedRecordings.isEmpty()) {
                    item {
                        Text(
                            text  = "No transcribed recordings yet.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    items(transcribedRecordings) { recording ->
                        RecordingHistoryCard(recording = recording)
                    }
                }
            }

            // ── Raw / Untranslated sub-tab ─────────────────────────────────
            if (selectedSubTab == 1) {
                if (untranslatedRecordings.isEmpty()) {
                    item {
                        Text(
                            text  = "No raw recordings pending transcription.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    items(untranslatedRecordings) { recording ->
                        val isRetrying = recording.id in retryingIds
                        UntranslatedRecordingCard(
                            recording  = recording,
                            isRetrying = isRetrying,
                            onRetry    = { retryTranscription(recording) }
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Transcribed recording history card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun RecordingHistoryCard(recording: RecordingEntity) {
    val dateFormatted = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(Date(recording.timestamp))
    var isExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth().clickable { isExpanded = !isExpanded },
        shape    = RoundedCornerShape(12.dp),
        colors   = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint     = Color(0xFF2E7D32),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            dateFormatted,
                            style      = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color      = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text     = recording.summary.ifBlank { recording.transcript },
                        style    = MaterialTheme.typography.bodySmall,
                        maxLines = if (isExpanded) 20 else 1
                    )
                }
                Icon(
                    imageVector        = if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = null,
                    tint               = MaterialTheme.colorScheme.outline
                )
            }
            if (isExpanded) {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                Text("Full Transcript:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(2.dp))
                Text(
                    text  = recording.transcript,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text  = "Audio: ${recording.filePath}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Untranslated / raw recording card with retry
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun UntranslatedRecordingCard(
    recording: RecordingEntity,
    isRetrying: Boolean,
    onRetry: () -> Unit
) {
    val dateFormatted = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(Date(recording.timestamp))
    val audioExists   = remember(recording.filePath) { File(recording.filePath).exists() }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(12.dp),
        colors   = CardDefaults.cardColors(
            containerColor = Color(0xFFFFF8E1)  // warm amber tint
        ),
        border   = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF57F17))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.HourglassEmpty,
                    contentDescription = null,
                    tint     = Color(0xFFF57F17),
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text       = dateFormatted,
                    style      = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color      = Color(0xFFE65100)
                )
                Spacer(Modifier.width(6.dp))
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color(0xFFF57F17).copy(alpha = 0.15f)
                ) {
                    Text(
                        text     = "RAW",
                        style    = MaterialTheme.typography.labelSmall,
                        color    = Color(0xFFE65100),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text  = "Audio: ${recording.filePath}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!audioExists) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text  = "⚠ Audio file not found at stored path",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            Spacer(Modifier.height(10.dp))
            Button(
                onClick  = onRetry,
                enabled  = !isRetrying && audioExists,
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(8.dp),
                colors   = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFE65100)
                )
            ) {
                if (isRetrying) {
                    CircularProgressIndicator(
                        modifier    = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color       = Color.White
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Retrying...", color = Color.White)
                } else {
                    Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
                    Spacer(Modifier.width(6.dp))
                    Text("Convert to Text", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Storage stat row helper
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun StorageStat(label: String, value: String) {
    Row(
        modifier              = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
    }
}
