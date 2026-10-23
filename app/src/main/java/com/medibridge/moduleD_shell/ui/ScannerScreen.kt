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
import com.medibridge.core.network.ApiMedicationItem
import com.medibridge.core.network.ApiSupplyItem
import com.medibridge.core.network.ApiValidationInfo
import com.medibridge.core.network.BackendClient
import com.medibridge.core.pipeline.MedicationIngestionPipeline
import com.medibridge.core.theme.*
import com.medibridge.moduleB_safety.logic.SafetyVerdict
import com.medibridge.moduleD_shell.ui.components.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.CancellationException
import org.json.JSONObject
import retrofit2.HttpException
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import com.google.gson.Gson
import com.medibridge.core.db.PrescriptionRecordEntity
import com.medibridge.core.pipeline.ReconciliationStatus

data class ScannedPrescriptionPackage(
    val items: List<MedicationIngestionPipeline.PipelineResultItem>,
    val imageFile: File?,
    val doctorName: String = "",
    val clinicName: String = "",
    val prescriptionDate: String = "",
    val remarks: String = "",
    val deliveryType: String = "",
    val rawOcrText: String = "",
    val supplies: List<ApiSupplyItem> = emptyList()
)

sealed class ScannerUiState {
    object Camera : ScannerUiState()
    data class Analyzing(val status: String = "Analyzing prescription or bill...") : ScannerUiState()
    data class Review(val pkg: ScannedPrescriptionPackage) : ScannerUiState()
    data class CaptureIssue(
        val headline: String,
        val tip: String,
        val technicalDetails: String? = null
    ) : ScannerUiState()
    data class Error(
        val title: String = "Processing Failed",
        val message: String,
        val technicalDetails: String? = null
    ) : ScannerUiState()
}

private fun extractDetailedError(e: Throwable): ScannerUiState {
    Log.e("ScannerScreen", "Detailed scan error caught", e)

    if (e is TimeoutCancellationException) {
        val serverUrl = BackendClient.getBaseUrl()
        return ScannerUiState.Error(
            title = "Scan Timed Out (120s)",
            message = "The prescription scan exceeded 120 seconds. The server might be busy or processing a complex image.",
            technicalDetails = "Exception: TimeoutCancellationException\nDuration: 120,000 ms\nTarget Server: $serverUrl\nSuggestion: Ensure backend server and ngrok tunnel are running."
        )
    }

    if (e is HttpException) {
        val code = e.code()
        val errorBodyStr = try {
            e.response()?.errorBody()?.string()
        } catch (_: Exception) {
            null
        }
        val requestUrl = e.response()?.raw()?.request?.url?.toString() ?: BackendClient.getBaseUrl()

        var userMsg = "HTTP $code: ${e.message()}"
        var rawTextFromBackend: String? = null

        if (!errorBodyStr.isNullOrBlank()) {
            try {
                val json = JSONObject(errorBodyStr)
                if (json.has("detail")) {
                    val detailObj = json.get("detail")
                    if (detailObj is JSONObject) {
                        if (detailObj.has("message")) {
                            userMsg = detailObj.getString("message")
                        }
                        if (detailObj.has("raw_text")) {
                            rawTextFromBackend = detailObj.getString("raw_text")
                        }
                    } else {
                        userMsg = detailObj.toString()
                    }
                } else if (json.has("message")) {
                    userMsg = json.getString("message")
                }
            } catch (_: Exception) {
                userMsg = errorBodyStr
            }
        }

        val tech = buildString {
            appendLine("HTTP Status: $code ${e.message()}")
            appendLine("Target URL: $requestUrl")
            if (!rawTextFromBackend.isNullOrBlank()) {
                appendLine("\n--- OCR Text Detected by Backend ---")
                appendLine(rawTextFromBackend)
            }
            appendLine("\n--- Full Server Response Body ---")
            appendLine(errorBodyStr ?: "(empty response body)")
        }

        if (code == 422) {
            val lowerMsg = userMsg.lowercase()
            if (lowerMsg.contains("could not read any text") || lowerMsg.contains("no medications found")) {
                return ScannerUiState.CaptureIssue(
                    headline = "No text detected in this image",
                    tip = userMsg,
                    technicalDetails = tech
                )
            }
            if (lowerMsg.contains("blurry")) {
                return ScannerUiState.CaptureIssue(
                    headline = "Image Too Blurry",
                    tip = userMsg,
                    technicalDetails = tech
                )
            }
            if (lowerMsg.contains("resolution")) {
                return ScannerUiState.CaptureIssue(
                    headline = "Image Resolution Too Small",
                    tip = userMsg,
                    technicalDetails = tech
                )
            }
        }

        return ScannerUiState.Error(
            title = "Server Error (HTTP $code)",
            message = userMsg,
            technicalDetails = tech
        )
    }

    if (e is java.net.ConnectException || e is java.net.UnknownHostException) {
        val serverUrl = BackendClient.getBaseUrl()
        return ScannerUiState.Error(
            title = "Connection Failed",
            message = "Could not reach backend server at:\n$serverUrl\nCheck your connection and verify the backend / ngrok tunnel is active.",
            technicalDetails = "Exception: ${e::class.java.name}\nMessage: ${e.message}\nTarget Server: $serverUrl"
        )
    }

    if (e is java.net.SocketTimeoutException) {
        val serverUrl = BackendClient.getBaseUrl()
        return ScannerUiState.Error(
            title = "Network Socket Timeout",
            message = "Network connection timed out waiting for server response.",
            technicalDetails = "Exception: java.net.SocketTimeoutException\nMessage: ${e.message}\nTarget Server: $serverUrl"
        )
    }

    if (e is IllegalStateException) {
        val msg = e.message ?: "No medications found"
        return ScannerUiState.Error(
            title = "Prescription Processing Issue",
            message = msg,
            technicalDetails = "Exception: java.lang.IllegalStateException\nMessage: $msg"
        )
    }

    val tech = buildString {
        appendLine("Exception: ${e::class.java.name}")
        appendLine("Message: ${e.localizedMessage ?: e.message}")
        appendLine("\nStacktrace:")
        e.stackTrace.take(8).forEach {
            appendLine("  at ${it.className}.${it.methodName}(${it.fileName}:${it.lineNumber})")
        }
    }

    return ScannerUiState.Error(
        title = "Unexpected Error",
        message = e.localizedMessage ?: e.message ?: "An unexpected error occurred during processing.",
        technicalDetails = tech
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannerScreen(
    onBack: () -> Unit,
    onNavigateToManualEntry: () -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    var uiState by remember { mutableStateOf<ScannerUiState>(ScannerUiState.Camera) }
    var processingJob: Job? by remember { mutableStateOf(null) }
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
            processingJob = scope.launch {
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

                    ensureServerReady { status ->
                        uiState = ScannerUiState.Analyzing(status)
                    }
                    uiState = ScannerUiState.Analyzing("Extracting prescription items with AI...")
                    val scannedPkg = withTimeout(240_000L) {
                        processDocumentAndPipeline(context, fileToProcess)
                    }
                    uiState = ScannerUiState.Review(scannedPkg)
                } catch (e: TimeoutCancellationException) {
                    uiState = ScannerUiState.Error(
                        title = "Prescription Scanning Timed Out",
                        message = "The prescription analysis took longer than 4 minutes. Please ensure the backend server is running and try again.",
                        technicalDetails = "TimeoutCancellationException: ${e.message}"
                    )
                } catch (_: CancellationException) {
                    uiState = ScannerUiState.Camera
                } catch (e: Throwable) {
                    uiState = extractDetailedError(e)
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
                        if (uiState is ScannerUiState.Review || uiState is ScannerUiState.CaptureIssue || uiState is ScannerUiState.Error) {
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
                                                        processingJob = scope.launch {
                                                            try {
                                                                ensureServerReady { status ->
                                                                    uiState = ScannerUiState.Analyzing(status)
                                                                }
                                                                uiState = ScannerUiState.Analyzing("Extracting medicine details with OCR...")
                                                                val scannedPkg = withTimeout(240_000L) {
                                                                    processDocumentAndPipeline(context, photoFile)
                                                                }
                                                                uiState = ScannerUiState.Review(scannedPkg)
                                                            } catch (e: TimeoutCancellationException) {
                                                                uiState = ScannerUiState.Error(
                                                                    title = "Prescription Scanning Timed Out",
                                                                    message = "The prescription analysis took longer than 4 minutes. Please ensure the backend server is running and try again.",
                                                                    technicalDetails = "TimeoutCancellationException: ${e.message}"
                                                                )
                                                            } catch (_: CancellationException) {
                                                                uiState = ScannerUiState.Camera
                                                            } catch (e: Throwable) {
                                                                uiState = extractDetailedError(e)
                                                            }
                                                        }
                                                    }

                                                    override fun onError(exception: ImageCaptureException) {
                                                        Log.e("ScannerScreen", "Photo capture failed", exception)
                                                        uiState = extractDetailedError(exception)
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
                        Spacer(modifier = Modifier.height(24.dp))
                        OutlinedButton(
                            onClick = {
                                processingJob?.cancel()
                                uiState = ScannerUiState.Camera
                            }
                        ) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Cancel")
                        }
                    }
                }

                is ScannerUiState.Review -> {
                    ReviewScreenContent(
                        pkg = state.pkg,
                        onSave = { finalizedPkg ->
                            scope.launch {
                                val prescEntity = PrescriptionRecordEntity(
                                    doctorName = finalizedPkg.doctorName,
                                    clinicName = finalizedPkg.clinicName,
                                    prescriptionDate = finalizedPkg.prescriptionDate.ifBlank {
                                        java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
                                    },
                                    imagePath = finalizedPkg.imageFile?.absolutePath ?: "",
                                    rawOcrText = finalizedPkg.rawOcrText,
                                    remarks = finalizedPkg.remarks,
                                    deliveryType = finalizedPkg.deliveryType,
                                    medicationsJson = Gson().toJson(finalizedPkg.items.map { it.medication })
                                )
                                MedicationIngestionPipeline.commitPipeline(context, finalizedPkg.items, prescEntity)
                                Toast.makeText(context, "${finalizedPkg.items.size} medicine(s) verified & saved!", Toast.LENGTH_SHORT).show()
                                onBack()
                            }
                        },
                        onRetake = { uiState = ScannerUiState.Camera }
                    )
                }

                is ScannerUiState.CaptureIssue -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                            modifier = Modifier.size(80.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.CameraAlt,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(40.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(20.dp))
                        Text(
                            text = state.headline,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = state.tip,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(28.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = { uiState = ScannerUiState.Camera },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Retake")
                            }
                            OutlinedButton(
                                onClick = onNavigateToManualEntry,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Enter manually")
                            }
                        }
                    }
                }

                is ScannerUiState.Error -> {
                    val clipboardManager = LocalClipboardManager.current
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = state.title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )

                        if (!state.technicalDetails.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "EXACT ERROR DETAILS",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        TextButton(
                                            onClick = {
                                                clipboardManager.setText(AnnotatedString(state.technicalDetails))
                                                Toast.makeText(context, "Error details copied to clipboard", Toast.LENGTH_SHORT).show()
                                            },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.ContentCopy,
                                                contentDescription = "Copy",
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Copy", style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(
                                                MaterialTheme.colorScheme.surface,
                                                RoundedCornerShape(6.dp)
                                            )
                                            .border(
                                                1.dp,
                                                MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                                RoundedCornerShape(6.dp)
                                            )
                                            .padding(10.dp)
                                    ) {
                                        Text(
                                            text = state.technicalDetails,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = { uiState = ScannerUiState.Camera }
                            ) {
                                Text("Retry")
                            }
                            OutlinedButton(
                                onClick = onNavigateToManualEntry
                            ) {
                                Text("Enter Manually")
                            }
                        }
                    }
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
    pkg: ScannedPrescriptionPackage,
    onSave: (ScannedPrescriptionPackage) -> Unit,
    onRetake: () -> Unit
) {
    var reviewPkg by remember(pkg) { mutableStateOf(pkg) }
    var checkedItemIds by remember(pkg) {
        mutableStateOf(
            pkg.items.filter { item ->
                item.medication.confidence >= 0.70f && !item.medication.needsVerification
            }.map { it.medication.id }.toSet()
        )
    }
    var showFullImageDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        SectionHeader(
            title = "Verify Prescription",
            badgeCount = reviewPkg.items.size,
            actionLabel = "Retake",
            onActionClick = onRetake
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            if (reviewPkg.imageFile?.exists() == true) {
                item {
                    val bitmap = remember(reviewPkg.imageFile) {
                        BitmapFactory.decodeFile(reviewPkg.imageFile!!.absolutePath)
                    }
                    if (bitmap != null) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showFullImageDialog = true },
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = "Prescription thumbnail",
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Original Prescription Photo",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Tap to view & zoom document",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Default.ZoomIn,
                                    contentDescription = "Zoom",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }

            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.16f))
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Prescription Details",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = reviewPkg.doctorName,
                                onValueChange = { reviewPkg = reviewPkg.copy(doctorName = it) },
                                label = { Text("Prescribed By") },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp)
                            )
                            OutlinedTextField(
                                value = reviewPkg.clinicName,
                                onValueChange = { reviewPkg = reviewPkg.copy(clinicName = it) },
                                label = { Text("Clinic / Hospital") },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp)
                            )
                        }
                        if (reviewPkg.remarks.isNotBlank()) {
                            OutlinedTextField(
                                value = reviewPkg.remarks,
                                onValueChange = { reviewPkg = reviewPkg.copy(remarks = it) },
                                label = { Text("Clinical Notes / Remarks") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp)
                            )
                        }
                    }
                }
            }

            if (reviewPkg.supplies.isNotEmpty()) {
                item {
                    var suppliesExpanded by remember { mutableStateOf(false) }
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { suppliesExpanded = !suppliesExpanded },
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.MedicalServices,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = "Medical Supplies (${reviewPkg.supplies.size})",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Icon(
                                    imageVector = if (suppliesExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = if (suppliesExpanded) "Collapse" else "Expand"
                                )
                            }
                            if (suppliesExpanded) {
                                Spacer(Modifier.height(8.dp))
                                reviewPkg.supplies.forEach { supply ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "• ${supply.name}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        if (!supply.quantity.isNullOrBlank()) {
                                            Text(
                                                text = "Qty: ${supply.quantity}",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            itemsIndexed(reviewPkg.items) { index, item ->
                val med = item.medication
                val recon = item.reconciliation
                val isUncertain = med.confidence < 0.70f || med.needsVerification || recon?.requiresVerification == true

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(
                        if (isUncertain) 1.5.dp else 1.dp,
                        if (isUncertain) StatusReview else MaterialTheme.colorScheme.outline.copy(alpha = 0.16f)
                    )
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
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Checkbox(
                                    checked = checkedItemIds.contains(med.id),
                                    onCheckedChange = { checked ->
                                        checkedItemIds = if (checked) checkedItemIds + med.id else checkedItemIds - med.id
                                    }
                                )
                                Column(modifier = Modifier.padding(start = 4.dp)) {
                                    val titleText = if (med.strength.isNotBlank() && !med.name.contains(med.strength, ignoreCase = true)) {
                                        "${med.name} ${med.strength}".trim()
                                    } else {
                                        med.name.trim()
                                    }
                                    Text(
                                        text = titleText.ifBlank { "Item #${index + 1}" },
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    val printedSub = item.printedName.takeIf { it.isNotBlank() }
                                        ?: med.summary.removePrefix("Prescribed: ").trim()
                                    if (printedSub.isNotBlank() && !printedSub.equals(titleText, ignoreCase = true)) {
                                        Text(
                                            text = printedSub,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                ConfidenceBadge(confidence = med.confidence)

                                val isVerified = med.confidence >= 0.70f && !med.needsVerification
                                StatusChip(
                                    status = if (isVerified) StatusType.Verified else StatusType.Review,
                                    customLabel = if (isVerified) "VERIFIED" else "UNVERIFIED"
                                )

                                IconButton(
                                    onClick = {
                                        reviewPkg = reviewPkg.copy(items = reviewPkg.items.filter { it.medication.id != med.id })
                                        checkedItemIds = checkedItemIds - med.id
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteOutline,
                                        contentDescription = "Remove item",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }

                        if (isUncertain) {
                            Spacer(Modifier.height(8.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = StatusReviewBg,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = StatusReview,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = if (med.confidence < 0.70f) "Low Confidence (<70%) - Please verify details against the prescription photo." else "Unverified - Please verify details against the prescription photo.",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = med.name,
                            onValueChange = { newName ->
                                val updatedItems = reviewPkg.items.toMutableList()
                                val updatedMed = med.copy(name = newName)
                                updatedItems[index] = updatedItems[index].copy(medication = updatedMed)
                                reviewPkg = reviewPkg.copy(items = updatedItems)
                            },
                            label = { Text("Medicine Name") },
                            placeholder = { Text("Not found, please fill") },
                            isError = med.name.isBlank(),
                            supportingText = if (med.name.isBlank()) { { Text("Not found, please fill", color = MaterialTheme.colorScheme.error) } } else null,
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
                                    val updatedItems = reviewPkg.items.toMutableList()
                                    val updatedMed = med.copy(strength = newStrength)
                                    updatedItems[index] = updatedItems[index].copy(medication = updatedMed)
                                    reviewPkg = reviewPkg.copy(items = updatedItems)
                                },
                                label = { Text("Strength") },
                                placeholder = { Text("Not found, please fill") },
                                isError = med.strength.isBlank(),
                                supportingText = if (med.strength.isBlank()) { { Text("Not found, please fill", color = MaterialTheme.colorScheme.error) } } else null,
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp)
                            )

                            OutlinedTextField(
                                value = med.dose,
                                onValueChange = { newDose ->
                                    val updatedItems = reviewPkg.items.toMutableList()
                                    val updatedMed = med.copy(dose = newDose)
                                    updatedItems[index] = updatedItems[index].copy(medication = updatedMed)
                                    reviewPkg = reviewPkg.copy(items = updatedItems)
                                },
                                label = { Text("Dose") },
                                placeholder = { Text("Not found, please fill") },
                                isError = med.dose.isBlank(),
                                supportingText = if (med.dose.isBlank()) { { Text("Not found, please fill", color = MaterialTheme.colorScheme.error) } } else null,
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = med.frequency,
                                onValueChange = { newFreq ->
                                    val updatedItems = reviewPkg.items.toMutableList()
                                    val updatedMed = med.copy(frequency = newFreq)
                                    updatedItems[index] = updatedItems[index].copy(medication = updatedMed)
                                    reviewPkg = reviewPkg.copy(items = updatedItems)
                                },
                                label = { Text("Frequency") },
                                placeholder = { Text("Not found, please fill") },
                                isError = med.frequency.isBlank(),
                                supportingText = if (med.frequency.isBlank()) { { Text("Not found, please fill", color = MaterialTheme.colorScheme.error) } } else null,
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp)
                            )

                            OutlinedTextField(
                                value = med.duration,
                                onValueChange = { newDuration ->
                                    val updatedItems = reviewPkg.items.toMutableList()
                                    val updatedMed = med.copy(duration = newDuration)
                                    updatedItems[index] = updatedItems[index].copy(medication = updatedMed)
                                    reviewPkg = reviewPkg.copy(items = updatedItems)
                                },
                                label = { Text("Duration") },
                                placeholder = { Text("Not found, please fill") },
                                isError = med.duration.isBlank(),
                                supportingText = if (med.duration.isBlank()) { { Text("Not found, please fill", color = MaterialTheme.colorScheme.error) } } else null,
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = med.timing,
                            onValueChange = { newTiming ->
                                val updatedItems = reviewPkg.items.toMutableList()
                                val updatedMed = med.copy(timing = newTiming)
                                updatedItems[index] = updatedItems[index].copy(medication = updatedMed)
                                reviewPkg = reviewPkg.copy(items = updatedItems)
                            },
                            label = { Text("Instructions / Meal Relation") },
                            placeholder = { Text("Not found, please fill") },
                            isError = med.timing.isBlank(),
                            supportingText = if (med.timing.isBlank()) { { Text("Not found, please fill", color = MaterialTheme.colorScheme.error) } } else null,
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        val selectedItems = reviewPkg.items.filter { checkedItemIds.contains(it.medication.id) }

        PrimaryButton(
            text = "Verify & Save to Regimen (${selectedItems.size})",
            icon = Icons.Default.CheckCircle,
            enabled = selectedItems.isNotEmpty(),
            onClick = { onSave(reviewPkg.copy(items = selectedItems)) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        )
    }

    if (showFullImageDialog && reviewPkg.imageFile?.exists() == true) {
        Dialog(onDismissRequest = { showFullImageDialog = false }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.80f)
            ) {
                Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Original Prescription Photo",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(onClick = { showFullImageDialog = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    val bitmap = remember(reviewPkg.imageFile) {
                        BitmapFactory.decodeFile(reviewPkg.imageFile!!.absolutePath)
                    }
                    if (bitmap != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = "Original Prescription",
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        }
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

private fun optimizeImageForUpload(context: Context, file: File): File {
    if (file.name.endsWith(".pdf", ignoreCase = true)) return file
    return try {
        val options = android.graphics.BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        android.graphics.BitmapFactory.decodeFile(file.absolutePath, options)
        val origW = options.outWidth
        val origH = options.outHeight
        if (origW <= 0 || origH <= 0) return file

        val maxDim = 1600
        var sampleSize = 1
        while (origW / (sampleSize * 2) >= maxDim || origH / (sampleSize * 2) >= maxDim) {
            sampleSize *= 2
        }

        val decodeOptions = android.graphics.BitmapFactory.Options().apply {
            inSampleSize = sampleSize
        }
        val bitmap = android.graphics.BitmapFactory.decodeFile(file.absolutePath, decodeOptions) ?: return file

        val curW = bitmap.width
        val curH = bitmap.height
        val finalBitmap = if (curW > maxDim || curH > maxDim) {
            val scale = maxDim.toFloat() / Math.max(curW, curH)
            val scaledW = (curW * scale).toInt()
            val scaledH = (curH * scale).toInt()
            Bitmap.createScaledBitmap(bitmap, scaledW, scaledH, true)
        } else {
            bitmap
        }

        val compressedFile = File(context.cacheDir, "upload_opt_${System.currentTimeMillis()}.jpg")
        FileOutputStream(compressedFile).use { out ->
            finalBitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
        }
        compressedFile
    } catch (e: Exception) {
        Log.e("ScannerScreen", "Image compression fallback to original", e)
        file
    }
}

private suspend fun processDocumentAndPipeline(
    context: Context,
    file: File?
): ScannedPrescriptionPackage {
    val candidateMeds = mutableListOf<MedicationObject>()
    var suppliesList = listOf<ApiSupplyItem>()
    val uploadTarget = if (file != null && file.exists()) optimizeImageForUpload(context, file) else file
    var doctorName = ""
    var clinicName = ""
    var prescriptionDate = ""
    var remarks = ""
    var deliveryType = ""
    var rawText = ""

    if (uploadTarget != null && uploadTarget.exists()) {
        try {
            val isPdf = uploadTarget.name.endsWith(".pdf", ignoreCase = true)
            val mediaType = if (isPdf) {
                "application/pdf".toMediaTypeOrNull()
            } else {
                "image/jpeg".toMediaTypeOrNull()
            }
            val requestFile = uploadTarget.asRequestBody(mediaType)
            val part = MultipartBody.Part.createFormData("file", uploadTarget.name, requestFile)

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

            rawText = response.rawText ?: ""
            suppliesList = response.supplies
            val medsList = if (response.medications.isNotEmpty()) response.medications else response.prescriptionRecord?.medications ?: emptyList()
            val presc = response.prescriptionRecord?.prescription
            if (medsList.isNotEmpty() || suppliesList.isNotEmpty()) {
                doctorName = presc?.prescribedBy ?: ""
                clinicName = ""
                prescriptionDate = presc?.date ?: ""
                remarks = presc?.remarks ?: ""
                deliveryType = presc?.deliveryType ?: ""

                val mapped = medsList.map { apiMed ->
                    BackendClient.mapPrescriptionItemToObject(apiMed, presc?.remarks)
                }
                candidateMeds.addAll(mapped)
            } else {
                val detectedText = response.rawText?.trim()
                val details = if (!detectedText.isNullOrBlank()) {
                    val preview = if (detectedText.length > 200) detectedText.take(200) + "..." else detectedText
                    "No medications recognized in document.\n\nOCR Read Text:\n\"$preview\"\n\nPlease ensure you are scanning a doctor's prescription with clear medication names and dosages."
                } else {
                    "No medications could be extracted from this document. Please ensure the prescription text is sharp and well-lit."
                }
                throw IllegalStateException(details)
            }
        } catch (e: Exception) {
            Log.e("ScannerScreen", "Prescription OCR processing error: ${e.message}", e)
            throw e
        }
    }

    if (candidateMeds.isEmpty() && suppliesList.isEmpty()) {
        throw IllegalStateException("No medications found to ingest. Please ensure prescription is legible.")
    }

    val evaluated = MedicationIngestionPipeline.evaluatePipeline(context, candidateMeds)
    return ScannedPrescriptionPackage(
        items = evaluated,
        imageFile = uploadTarget,
        doctorName = doctorName,
        clinicName = clinicName,
        prescriptionDate = prescriptionDate,
        remarks = remarks,
        deliveryType = deliveryType,
        rawOcrText = rawText,
        supplies = suppliesList
    )
}

private suspend fun ensureServerReady(onStatus: (String) -> Unit) {
    val service = BackendClient.getService()
    val startTime = System.currentTimeMillis()
    while (System.currentTimeMillis() - startTime < 60_000L) {
        try {
            val health = service.checkHealth()
            if (health.ocrReady) {
                return
            }
        } catch (_: Exception) {}
        onStatus("Server is warming up, please wait a moment...")
        kotlinx.coroutines.delay(5_000L)
    }
}

