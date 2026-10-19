package com.medibridge.moduleD_shell.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
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
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
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
import com.medibridge.core.model.MedicationObject
import com.medibridge.core.network.BackendClient
import com.medibridge.core.pipeline.MedicationIngestionPipeline
import com.medibridge.core.theme.*
import com.medibridge.moduleB_safety.logic.SafetyVerdict
import com.medibridge.moduleD_shell.ui.components.*
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
    data class Analyzing(val status: String = "Analyzing prescription or bill...") : ScannerUiState()
    data class Review(val items: List<MedicationIngestionPipeline.PipelineResultItem>) : ScannerUiState()
    data class Error(val message: String) : ScannerUiState()
}

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
            Toast.makeText(context, "Camera permission needed to capture prescriptions or bills", Toast.LENGTH_SHORT).show()
        }
    }

    var imageCapture: ImageCapture? by remember { mutableStateOf(null) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                try {
                    uiState = ScannerUiState.Analyzing("Reading document file (Image / PDF)...")
                    val isPdf = uri.toString().contains(".pdf", ignoreCase = true) ||
                            context.contentResolver.getType(uri)?.contains("pdf", ignoreCase = true) == true

                    val tempFile = copyUriToTempFile(context, uri, isPdf)
                    val fileToProcess = if (isPdf) {
                        uiState = ScannerUiState.Analyzing("Rendering PDF page to image before OCR...")
                        val renderedImage = renderPdfFirstPageToBitmap(context, tempFile)
                        renderedImage ?: tempFile
                    } else {
                        tempFile
                    }

                    uiState = ScannerUiState.Analyzing("Extracting prescription items with AI...")
                    val pipelineResults = processDocumentAndPipeline(context, fileToProcess)
                    uiState = ScannerUiState.Review(pipelineResults)
                } catch (e: Exception) {
                    uiState = ScannerUiState.Error(e.message ?: "Failed to process prescription image.")
                }
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
                        text = if (uiState is ScannerUiState.Review) "Review Prescription" else "Scan Prescription",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
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
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    if (uiState is ScannerUiState.Camera) {
                        IconButton(onClick = { filePickerLauncher.launch(arrayOf("image/*", "application/pdf")) }) {
                            Icon(
                                imageVector = Icons.Default.UploadFile,
                                contentDescription = "Upload Image or PDF"
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (val state = uiState) {
                is ScannerUiState.Camera -> {
                    if (hasCameraPermission) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            AndroidView(
                                modifier = Modifier.fillMaxSize(),
                                factory = { ctx ->
                                    val previewView = PreviewView(ctx)
                                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

                                    cameraProviderFuture.addListener({
                                        val cameraProvider = cameraProviderFuture.get()
                                        val preview = Preview.Builder().build().also {
                                            it.setSurfaceProvider(previewView.surfaceProvider)
                                        }

                                        val capture = ImageCapture.Builder()
                                            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                                            .build()
                                        imageCapture = capture

                                        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                                        try {
                                            cameraProvider.unbindAll()
                                            cameraProvider.bindToLifecycle(
                                                lifecycleOwner,
                                                cameraSelector,
                                                preview,
                                                capture
                                            )
                                        } catch (exc: Exception) {
                                            Log.e("ScannerScreen", "Camera binding failed", exc)
                                        }
                                    }, ContextCompat.getMainExecutor(ctx))

                                    previewView
                                }
                            )

                            // Viewfinder Overlay with Corner Guides
                            ViewfinderOverlay(
                                modifier = Modifier.fillMaxSize()
                            )

                            // Bottom Capture Bar
                            Surface(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .padding(bottom = 32.dp, start = 24.dp, end = 24.dp),
                                color = Color.Transparent
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Upload File Button
                                    IconButton(
                                        onClick = { filePickerLauncher.launch(arrayOf("image/*", "application/pdf")) },
                                        modifier = Modifier
                                            .size(50.dp)
                                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f), CircleShape)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PhotoLibrary,
                                            contentDescription = "Select from gallery",
                                            tint = MaterialTheme.colorScheme.onSurface
                                        )
                                    }

                                    // Primary Shutter Button
                                    Surface(
                                        modifier = Modifier.size(72.dp),
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.primary,
                                        shadowElevation = 6.dp,
                                        onClick = {
                                            val capture = imageCapture ?: return@Surface
                                            val photoFile = File(context.cacheDir, "scan_${System.currentTimeMillis()}.jpg")
                                            val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

                                            uiState = ScannerUiState.Analyzing("Capturing prescription...")

                                            capture.takePicture(
                                                outputOptions,
                                                ContextCompat.getMainExecutor(context),
                                                object : ImageCapture.OnImageSavedCallback {
                                                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                                        scope.launch {
                                                            try {
                                                                uiState = ScannerUiState.Analyzing("Extracting medicine details with OCR...")
                                                                val pipelineResults = processDocumentAndPipeline(context, photoFile)
                                                                uiState = ScannerUiState.Review(pipelineResults)
                                                            } catch (e: Exception) {
                                                                uiState = ScannerUiState.Error(e.message ?: "Analysis failed.")
                                                            }
                                                        }
                                                    }

                                                    override fun onError(exception: ImageCaptureException) {
                                                        Log.e("ScannerScreen", "Photo capture failed", exception)
                                                        uiState = ScannerUiState.Error("Photo capture failed: ${exception.message}")
                                                    }
                                                }
                                            )
                                        }
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Default.Camera,
                                                contentDescription = "Capture prescription",
                                                tint = MaterialTheme.colorScheme.onPrimary,
                                                modifier = Modifier.size(34.dp)
                                            )
                                        }
                                    }

                                    // Torch/Flash placeholder
                                    IconButton(
                                        onClick = {
                                            Toast.makeText(context, "Hold steady in good light", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier
                                            .size(50.dp)
                                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f), CircleShape)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.FlashlightOn,
                                            contentDescription = "Torch info",
                                            tint = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        // Permission Rationale Screen
                        EmptyState(
                            icon = Icons.Default.CameraAlt,
                            title = "Camera Permission Needed",
                            subtitle = "MediBridge uses the camera to scan physical prescriptions and pill bottle labels with OCR AI.",
                            actionLabel = "Grant Camera Access",
                            onActionClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                            modifier = Modifier.align(Alignment.Center)
                        )
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
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(52.dp)
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = state.status,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Extracting medicines, verifying doses, and checking safety interactions...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                is ScannerUiState.Review -> {
                    ReviewScreenContent(
                        items = state.items,
                        onSave = { finalizedItems ->
                            scope.launch {
                                MedicationIngestionPipeline.commitPipeline(context, finalizedItems)
                                Toast.makeText(context, "${finalizedItems.size} medicine(s) saved to regimen!", Toast.LENGTH_SHORT).show()
                                onBack()
                            }
                        },
                        onRetake = { uiState = ScannerUiState.Camera }
                    )
                }

                is ScannerUiState.Error -> {
                    EmptyState(
                        icon = Icons.Default.ErrorOutline,
                        title = "Prescription Processing Failed",
                        subtitle = state.message,
                        actionLabel = "Try Again",
                        onActionClick = { uiState = ScannerUiState.Camera },
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
        }
    }
}

@Composable
private fun ViewfinderOverlay(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "laser")
    val laserY by infiniteTransition.animateFloat(
        initialValue = 0.1f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "laserY"
    )

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                Text(
                    text = "Align prescription or label inside frame",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.78f)
                    .clip(RoundedCornerShape(20.dp))
                    .border(
                        BorderStroke(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)),
                        RoundedCornerShape(20.dp)
                    )
            ) {
                // Animated scanning laser line
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.005f)
                        .align(Alignment.TopCenter)
                        .offset(y = (laserY * 340).dp)
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
        }
    }
}

@Composable
private fun ReviewScreenContent(
    items: List<MedicationIngestionPipeline.PipelineResultItem>,
    onSave: (List<MedicationIngestionPipeline.PipelineResultItem>) -> Unit,
    onRetake: () -> Unit
) {
    var reviewItems by remember(items) { mutableStateOf(items) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        SectionHeader(
            title = "Extracted Medications",
            badgeCount = reviewItems.size,
            actionLabel = "Retake",
            onActionClick = onRetake
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            itemsIndexed(reviewItems) { index, item ->
                val med = item.medication
                val eval = item.evaluation

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.16f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Item #${index + 1}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )

                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                ConfidenceBadge(confidence = med.confidence)

                                val status = when (eval.verdict) {
                                    SafetyVerdict.SAFE -> StatusType.Verified
                                    SafetyVerdict.INTERACTION, SafetyVerdict.DUPLICATE -> StatusType.Conflict
                                    SafetyVerdict.OVERLAP, SafetyVerdict.UNVERIFIED -> StatusType.Review
                                    else -> StatusType.Review
                                }
                                StatusChip(status = status, customLabel = eval.verdict.name)
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = med.name,
                            onValueChange = { newName ->
                                reviewItems = reviewItems.toMutableList().also {
                                    val updated = med.copy(name = newName)
                                    it[index] = it[index].copy(medication = updated)
                                }
                            },
                            label = { Text("Medicine Name") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = med.strength,
                                onValueChange = { newStrength ->
                                    reviewItems = reviewItems.toMutableList().also {
                                        val updated = med.copy(strength = newStrength)
                                        it[index] = it[index].copy(medication = updated)
                                    }
                                },
                                label = { Text("Strength") },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp)
                            )

                            OutlinedTextField(
                                value = med.dose,
                                onValueChange = { newDose ->
                                    reviewItems = reviewItems.toMutableList().also {
                                        val updated = med.copy(dose = newDose)
                                        it[index] = it[index].copy(medication = updated)
                                    }
                                },
                                label = { Text("Dose") },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = med.timing,
                            onValueChange = { newTiming ->
                                reviewItems = reviewItems.toMutableList().also {
                                    val updated = med.copy(timing = newTiming)
                                    it[index] = it[index].copy(medication = updated)
                                }
                            },
                            label = { Text("Instructions / Timing") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        PrimaryButton(
            text = "Save to Regimen (${reviewItems.size})",
            icon = Icons.Default.Check,
            onClick = { onSave(reviewItems) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Helpers for document processing
// ─────────────────────────────────────────────────────────────────────────────

private fun copyUriToTempFile(context: Context, uri: Uri, isPdf: Boolean): File {
    val ext = if (isPdf) ".pdf" else ".jpg"
    val tempFile = File(context.cacheDir, "imported_${System.currentTimeMillis()}$ext")
    context.contentResolver.openInputStream(uri)?.use { input ->
        FileOutputStream(tempFile).use { output ->
            input.copyTo(output)
        }
    }
    return tempFile
}

private fun renderPdfFirstPageToBitmap(context: Context, pdfFile: File): File? {
    return try {
        val fileDescriptor = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(fileDescriptor)
        if (renderer.pageCount > 0) {
            val page = renderer.openPage(0)
            val bitmap = Bitmap.createBitmap(page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(android.graphics.Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            page.close()
            renderer.close()
            fileDescriptor.close()

            val renderedImageFile = File(context.cacheDir, "rendered_page_${System.currentTimeMillis()}.png")
            FileOutputStream(renderedImageFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            renderedImageFile
        } else {
            renderer.close()
            fileDescriptor.close()
            null
        }
    } catch (e: Exception) {
        Log.e("ScannerScreen", "PDF rendering error", e)
        null
    }
}

private suspend fun processDocumentAndPipeline(
    context: Context,
    file: File?
): List<MedicationIngestionPipeline.PipelineResultItem> {
    val candidateMeds = mutableListOf<MedicationObject>()

    if (file != null && file.exists()) {
        try {
            val isPdf = file.name.endsWith(".pdf", ignoreCase = true)
            val mediaType = if (isPdf) {
                "application/pdf".toMediaTypeOrNull()
            } else {
                "image/jpeg".toMediaTypeOrNull()
            }
            val requestFile = file.asRequestBody(mediaType)
            val part = MultipartBody.Part.createFormData("file", file.name, requestFile)

            val service = BackendClient.getService()
            val response = if (isPdf) {
                try {
                    service.uploadBillingPdf(part)
                } catch (e: Exception) {
                    service.uploadPrescription(part)
                }
            } else {
                service.uploadPrescription(part)
            }

            val record = response.prescriptionRecord
            if (record != null && record.medications.isNotEmpty()) {
                val mapped = record.medications.map { apiMed ->
                    BackendClient.mapPrescriptionItemToObject(apiMed, record.prescription?.remarks)
                }
                candidateMeds.addAll(mapped)
            } else {
                throw IllegalStateException("No medications could be extracted from this document. Please ensure the prescription text is sharp and well-lit.")
            }
        } catch (e: Exception) {
            Log.e("ScannerScreen", "Prescription OCR processing error: ${e.message}", e)
            throw e
        }
    }

    if (candidateMeds.isEmpty()) {
        throw IllegalStateException("No valid medications found to review.")
    }

    return MedicationIngestionPipeline.evaluatePipeline(context, candidateMeds)
}
