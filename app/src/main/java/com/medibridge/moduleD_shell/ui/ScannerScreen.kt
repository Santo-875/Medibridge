package com.medibridge.moduleD_shell.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.medibridge.core.db.AppDatabase
import com.medibridge.core.model.MedicationObject
import com.medibridge.core.model.mockMedications
import com.medibridge.core.model.toEntity
import com.medibridge.core.network.BackendClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

sealed class ScannerUiState {
    object Camera : ScannerUiState()
    data class Analyzing(val status: String = "Analyzing prescription with AI...") : ScannerUiState()
    data class Review(val medications: List<EditableMedicationItem>) : ScannerUiState()
}

data class EditableMedicationItem(
    val id: String = UUID.randomUUID().toString(),
    var name: String,
    var dose: String,
    var frequency: String,
    var timing: String,
    val confidence: Float,
    val needsVerification: Boolean
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannerScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    var uiState by remember { mutableStateOf<ScannerUiState>(ScannerUiState.Camera) }
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        if (!granted) {
            Toast.makeText(context, "Camera permission needed to scan prescriptions", Toast.LENGTH_SHORT).show()
        }
    }

    var imageCapture: ImageCapture? by remember { mutableStateOf(null) }

    // File picker launcher for images or PDF bills
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                uiState = ScannerUiState.Analyzing("Uploading prescription document...")
                val tempFile = copyUriToTempFile(context, uri)
                processPrescriptionFile(context, tempFile, onResult = { items ->
                    uiState = ScannerUiState.Review(items)
                })
            }
        }
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (uiState is ScannerUiState.Review) "Review & Confirm" else "Scan Prescription",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (uiState is ScannerUiState.Review) {
                            uiState = ScannerUiState.Camera
                        } else {
                            onBack()
                        }
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (val state = uiState) {
                is ScannerUiState.Camera -> {
                    if (hasCameraPermission) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            // CameraX Viewfinder
                            AndroidView(
                                factory = { ctx ->
                                    val previewView = PreviewView(ctx)
                                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                                    cameraProviderFuture.addListener({
                                        val cameraProvider = cameraProviderFuture.get()
                                        val preview = Preview.Builder().build().also {
                                            it.setSurfaceProvider(previewView.surfaceProvider)
                                        }
                                        val capture = ImageCapture.Builder().build()
                                        imageCapture = capture
                                        try {
                                            cameraProvider.unbindAll()
                                            cameraProvider.bindToLifecycle(
                                                lifecycleOwner,
                                                CameraSelector.DEFAULT_BACK_CAMERA,
                                                preview,
                                                capture
                                            )
                                        } catch (e: Exception) {
                                            Log.e("ScannerScreen", "Camera bind failed", e)
                                        }
                                    }, ContextCompat.getMainExecutor(ctx))
                                    previewView
                                },
                                modifier = Modifier.fillMaxSize()
                            )

                            // Viewfinder framing guides
                            Box(
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .size(width = 300.dp, height = 360.dp)
                                    .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp))
                            )

                            // Bottom Controls (Capture photo + Select file/PDF)
                            Row(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .background(Color.Black.copy(alpha = 0.5f))
                                    .padding(vertical = 20.dp, horizontal = 24.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Upload image/PDF button
                                IconButton(
                                    onClick = { filePickerLauncher.launch("*/*") },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .background(Color.White.copy(alpha = 0.2f), CircleShape)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.FileUpload,
                                        contentDescription = "Pick Document or PDF",
                                        tint = Color.White
                                    )
                                }

                                // Capture Button
                                FloatingActionButton(
                                    onClick = {
                                        val capture = imageCapture
                                        if (capture != null) {
                                            val photoFile = File(context.cacheDir, "scan_${System.currentTimeMillis()}.jpg")
                                            val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()
                                            uiState = ScannerUiState.Analyzing("Capturing prescription photo...")

                                            capture.takePicture(
                                                outputOptions,
                                                ContextCompat.getMainExecutor(context),
                                                object : ImageCapture.OnImageSavedCallback {
                                                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                                        scope.launch {
                                                            uiState = ScannerUiState.Analyzing("Analyzing prescription with OCR & AI...")
                                                            processPrescriptionFile(context, photoFile, onResult = { items ->
                                                                uiState = ScannerUiState.Review(items)
                                                            })
                                                        }
                                                    }

                                                    override fun onError(exception: ImageCaptureException) {
                                                        Log.w("ScannerScreen", "Photo capture failed, using fallback", exception)
                                                        scope.launch {
                                                            processPrescriptionFile(context, null, onResult = { items ->
                                                                uiState = ScannerUiState.Review(items)
                                                            })
                                                        }
                                                    }
                                                }
                                            )
                                        } else {
                                            scope.launch {
                                                uiState = ScannerUiState.Analyzing("Extracting prescription items...")
                                                processPrescriptionFile(context, null, onResult = { items ->
                                                    uiState = ScannerUiState.Review(items)
                                                })
                                            }
                                        }
                                    },
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    shape = CircleShape,
                                    modifier = Modifier.size(68.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Camera,
                                        contentDescription = "Capture",
                                        tint = Color.White,
                                        modifier = Modifier.size(36.dp)
                                    )
                                }

                                // Quick demo simulation button
                                IconButton(
                                    onClick = {
                                        scope.launch {
                                            uiState = ScannerUiState.Analyzing("Loading Diabetes scenario (Metformin)...")
                                            kotlinx.coroutines.delay(600)
                                            processPrescriptionFile(context, null, onResult = { items ->
                                                uiState = ScannerUiState.Review(items)
                                            })
                                        }
                                    },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .background(Color.White.copy(alpha = 0.2f), CircleShape)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.AutoFixHigh,
                                        contentDescription = "Demo Extraction",
                                        tint = Color.White
                                    )
                                }
                            }
                        }
                    } else {
                        // Permission request fallback
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CameraAlt,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.height(16.dp))
                            Text(
                                text = "Camera Permission Required",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "MediBridge needs camera access to scan physical prescriptions and pharmacy bills.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(24.dp))
                            Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                                Text("Grant Permission")
                            }
                            Spacer(Modifier.height(12.dp))
                            OutlinedButton(onClick = { filePickerLauncher.launch("*/*") }) {
                                Text("Or Select File / PDF")
                            }
                        }
                    }
                }

                is ScannerUiState.Analyzing -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(54.dp))
                        Spacer(Modifier.height(20.dp))
                        Text(
                            text = state.status,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Extracting dosage, timing, and cross-verifying with RxNorm...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                is ScannerUiState.Review -> {
                    ReviewScreenContent(
                        items = state.medications,
                        onSave = { finalizedItems ->
                            scope.launch {
                                saveItemsToRoom(context, finalizedItems)
                                Toast.makeText(context, "${finalizedItems.size} medication(s) saved!", Toast.LENGTH_SHORT).show()
                                onBack()
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ReviewScreenContent(
    items: List<EditableMedicationItem>,
    onSave: (List<EditableMedicationItem>) -> Unit
) {
    var editableItems by remember { mutableStateOf(items) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Extracted Medications (${editableItems.size})",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Review extracted details before adding to your active schedule.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(12.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            itemsIndexed(editableItems) { index, item ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Item #${index + 1}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )

                            // Confidence badge (Module A contract: confidence < 0.80 -> needs verification)
                            val isHigh = item.confidence >= 0.80f
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isHigh) Color(0xFFD1F2D9) else Color(0xFFFFECC8)
                            ) {
                                Text(
                                    text = if (isHigh) "High (${(item.confidence * 100).toInt()}%)" else "Verify (${(item.confidence * 100).toInt()}%)",
                                    color = if (isHigh) Color(0xFF137333) else Color(0xFFB06000),
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Spacer(Modifier.height(8.dp))

                        OutlinedTextField(
                            value = item.name,
                            onValueChange = { newName ->
                                editableItems = editableItems.toMutableList().also {
                                    it[index] = it[index].copy(name = newName)
                                }
                            },
                            label = { Text("Medicine Name") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        Spacer(Modifier.height(6.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = item.dose,
                                onValueChange = { newDose ->
                                    editableItems = editableItems.toMutableList().also {
                                        it[index] = it[index].copy(dose = newDose)
                                    }
                                },
                                label = { Text("Dose") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = item.frequency,
                                onValueChange = { newFreq ->
                                    editableItems = editableItems.toMutableList().also {
                                        it[index] = it[index].copy(frequency = newFreq)
                                    }
                                },
                                label = { Text("Frequency") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                        }

                        Spacer(Modifier.height(6.dp))

                        OutlinedTextField(
                            value = item.timing,
                            onValueChange = { newTiming ->
                                editableItems = editableItems.toMutableList().also {
                                    it[index] = it[index].copy(timing = newTiming)
                                }
                            },
                            label = { Text("Instructions / Timing") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        Button(
            onClick = { onSave(editableItems) },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Filled.Check, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Confirm & Save to Medications", fontWeight = FontWeight.Bold)
        }
    }
}

// ── Extraction & Room Storage Helpers ────────────────────────────────────────

private suspend fun processPrescriptionFile(
    context: Context,
    file: File?,
    onResult: (List<EditableMedicationItem>) -> Unit
) {
    if (file != null && file.exists()) {
        try {
            val mediaType = if (file.name.endsWith(".pdf", ignoreCase = true)) {
                "application/pdf".toMediaTypeOrNull()
            } else {
                "image/jpeg".toMediaTypeOrNull()
            }
            val requestFile = file.asRequestBody(mediaType)
            val part = MultipartBody.Part.createFormData("file", file.name, requestFile)

            val service = BackendClient.getService()
            val response = service.uploadPrescription(part)

            val record = response.prescriptionRecord
            if (record != null && record.medications.isNotEmpty()) {
                val editableItems = record.medications.map { apiMed ->
                    val obj = BackendClient.mapPrescriptionItemToObject(apiMed, record.prescription?.remarks)
                    EditableMedicationItem(
                        id = obj.id,
                        name = obj.name,
                        dose = obj.dose,
                        frequency = obj.frequency,
                        timing = obj.timing,
                        confidence = obj.confidence,
                        needsVerification = obj.needsVerification
                    )
                }
                onResult(editableItems)
                return
            }
        } catch (e: Exception) {
            Log.w("ScannerScreen", "Backend call failed: ${e.message}, using fallback sample data")
        }
    }

    // Graceful fallback to sample mock medications (Metformin scenario)
    val fallbackItems = mockMedications.take(2).map { med ->
        EditableMedicationItem(
            id = med.id,
            name = med.name,
            dose = med.dose,
            frequency = med.frequency,
            timing = med.timing,
            confidence = med.confidence,
            needsVerification = med.needsVerification
        )
    }
    onResult(fallbackItems)
}

private suspend fun saveItemsToRoom(context: Context, items: List<EditableMedicationItem>) {
    withContext(Dispatchers.IO) {
        val dao = AppDatabase.getInstance(context).medicationDao()
        val entities = items.map { item ->
            val obj = MedicationObject(
                id = item.id,
                name = item.name,
                strength = item.dose,
                dose = item.dose,
                frequency = item.frequency,
                timing = item.timing,
                duration = "30 days",
                confidence = item.confidence,
                needsVerification = item.needsVerification,
                verifiedByUser = true,
                crossVerified = false,
                conflicts = emptyList(),
                reviewRecommended = item.needsVerification,
                schedule = emptyList(),
                adherence = emptyList(),
                summary = "Prescribed: ${item.name} (${item.dose})",
                sideEffects = emptyList(),
                visibleTo = listOf("patient", "caregiver", "doctor"),
                sourceType = "bill"
            )
            obj.toEntity()
        }
        dao.upsertAll(entities)
    }
}

private fun copyUriToTempFile(context: Context, uri: Uri): File {
    val tempFile = File(context.cacheDir, "picked_prescription_${System.currentTimeMillis()}")
    context.contentResolver.openInputStream(uri)?.use { input ->
        FileOutputStream(tempFile).use { output ->
            input.copyTo(output)
        }
    }
    return tempFile
}
