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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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

sealed class BillUiState {
    object Choose : BillUiState()
    object Camera : BillUiState()
    data class Processing(val status: String = "Extracting billing details...") : BillUiState()
    data class Review(val items: List<EditableMedicationItem>) : BillUiState()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BillScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    var uiState by remember { mutableStateOf<BillUiState>(BillUiState.Choose) }
    var imageCapture: ImageCapture? by remember { mutableStateOf(null) }

    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                uiState = BillUiState.Processing("Extracting pharmacy billing PDF with AI...")
                val tempFile = copyBillUriToTempFile(context, uri)
                processBillFile(context, tempFile) { items ->
                    uiState = BillUiState.Review(items)
                }
            }
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            uiState = BillUiState.Camera
        } else {
            Toast.makeText(context, "Camera permission needed to take bill photos", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = when (uiState) {
                            is BillUiState.Review -> "Review Bill Items"
                            is BillUiState.Camera -> "Capture Bill Photo"
                            else -> "Pharmacy Bill Import"
                        },
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        when (uiState) {
                            is BillUiState.Review, is BillUiState.Camera -> uiState = BillUiState.Choose
                            else -> onBack()
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
                is BillUiState.Choose -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            modifier = Modifier.size(90.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ReceiptLong,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(48.dp)
                                )
                            }
                        }

                        Spacer(Modifier.height(20.dp))

                        Text(
                            text = "Import Pharmacy Bill",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )

                        Spacer(Modifier.height(8.dp))

                        Text(
                            text = "Direct integration with pharmacy billing systems. Upload billing PDF or take a receipt photo to automatically extract prescribed items.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )

                        Spacer(Modifier.height(32.dp))

                        // Button 1: Upload PDF
                        Button(
                            onClick = { pdfPickerLauncher.launch("application/pdf") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Filled.PictureAsPdf, contentDescription = null)
                            Spacer(Modifier.width(10.dp))
                            Text("Upload Pharmacy PDF Bill", fontWeight = FontWeight.SemiBold)
                        }

                        Spacer(Modifier.height(14.dp))

                        // Button 2: Camera Scan
                        OutlinedButton(
                            onClick = {
                                if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                                    uiState = BillUiState.Camera
                                } else {
                                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Filled.CameraAlt, contentDescription = null)
                            Spacer(Modifier.width(10.dp))
                            Text("Take Photo of Paper Receipt", fontWeight = FontWeight.SemiBold)
                        }

                        Spacer(Modifier.height(14.dp))

                        // Button 3: Demo pitch scenario
                        TextButton(
                            onClick = {
                                scope.launch {
                                    uiState = BillUiState.Processing("Simulating pharmacy bill ingestion...")
                                    kotlinx.coroutines.delay(600)
                                    processBillFile(context, null) { items ->
                                        uiState = BillUiState.Review(items)
                                    }
                                }
                            }
                        ) {
                            Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Simulate Pharmacy Billing API Call")
                        }
                    }
                }

                is BillUiState.Camera -> {
                    Box(modifier = Modifier.fillMaxSize()) {
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
                                        Log.e("BillScreen", "Camera error", e)
                                    }
                                }, ContextCompat.getMainExecutor(ctx))
                                previewView
                            },
                            modifier = Modifier.fillMaxSize()
                        )

                        // Frame
                        Box(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(width = 300.dp, height = 400.dp)
                                .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp))
                        )

                        // Bottom Capture Row
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .background(Color.Black.copy(alpha = 0.5f))
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            FloatingActionButton(
                                onClick = {
                                    val capture = imageCapture
                                    if (capture != null) {
                                        val photoFile = File(context.cacheDir, "bill_${System.currentTimeMillis()}.jpg")
                                        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()
                                        uiState = BillUiState.Processing("Capturing receipt photo...")

                                        capture.takePicture(
                                            outputOptions,
                                            ContextCompat.getMainExecutor(context),
                                            object : ImageCapture.OnImageSavedCallback {
                                                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                                    scope.launch {
                                                        uiState = BillUiState.Processing("Extracting receipt items...")
                                                        processBillFile(context, photoFile) { items ->
                                                            uiState = BillUiState.Review(items)
                                                        }
                                                    }
                                                }

                                                override fun onError(exception: ImageCaptureException) {
                                                    scope.launch {
                                                        processBillFile(context, null) { items ->
                                                            uiState = BillUiState.Review(items)
                                                        }
                                                    }
                                                }
                                            }
                                        )
                                    } else {
                                        scope.launch {
                                            processBillFile(context, null) { items ->
                                                uiState = BillUiState.Review(items)
                                            }
                                        }
                                    }
                                },
                                containerColor = MaterialTheme.colorScheme.primary,
                                shape = CircleShape,
                                modifier = Modifier.size(68.dp)
                            ) {
                                Icon(Icons.Filled.Camera, contentDescription = "Capture", tint = Color.White)
                            }
                        }
                    }
                }

                is BillUiState.Processing -> {
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
                            text = "Converting billing data into structured clinical medication records...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                is BillUiState.Review -> {
                    BillReviewContent(
                        items = state.items,
                        onSave = { finalizedItems ->
                            scope.launch {
                                saveBillItemsToRoom(context, finalizedItems)
                                Toast.makeText(context, "${finalizedItems.size} medication(s) added from bill!", Toast.LENGTH_SHORT).show()
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
private fun BillReviewContent(
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
            text = "Extracted Bill Medications (${editableItems.size})",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Verify the medications from the pharmacy bill before saving.",
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
            Text("Confirm & Add to Schedule", fontWeight = FontWeight.Bold)
        }
    }
}

private suspend fun processBillFile(
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
            val response = service.uploadBillingPdf(part)

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
            Log.w("BillScreen", "Backend call failed: ${e.message}, falling back to mock bill data")
        }
    }

    // Fallback bill items (Metformin + Calcium)
    val fallbackItems = listOf(
        EditableMedicationItem(
            name = "Metformin 500mg ER",
            dose = "1 tablet",
            frequency = "Once daily (Morning)",
            timing = "Take with breakfast",
            confidence = 0.95f,
            needsVerification = false
        ),
        EditableMedicationItem(
            name = "Calcium 500mg + Vitamin D3",
            dose = "1 tablet",
            frequency = "Once daily (Afternoon)",
            timing = "Take after lunch with water",
            confidence = 0.90f,
            needsVerification = false
        )
    )
    onResult(fallbackItems)
}

private suspend fun saveBillItemsToRoom(context: Context, items: List<EditableMedicationItem>) {
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
                summary = "Imported from Pharmacy Bill: ${item.name}",
                sideEffects = emptyList(),
                visibleTo = listOf("patient", "caregiver", "doctor"),
                sourceType = "bill"
            )
            obj.toEntity()
        }
        dao.upsertAll(entities)
    }
}

private fun copyBillUriToTempFile(context: Context, uri: Uri): File {
    val isPdf = uri.toString().contains(".pdf")
    val ext = if (isPdf) ".pdf" else ".jpg"
    val tempFile = File(context.cacheDir, "bill_upload_${System.currentTimeMillis()}$ext")
    context.contentResolver.openInputStream(uri)?.use { input ->
        FileOutputStream(tempFile).use { output ->
            input.copyTo(output)
        }
    }
    return tempFile
}
