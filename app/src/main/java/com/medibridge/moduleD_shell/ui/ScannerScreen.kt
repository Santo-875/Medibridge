package com.medibridge.moduleD_shell.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.medibridge.core.theme.GradientEnd
import com.medibridge.core.theme.GradientStart

/**
 * ScannerScreen — stub for Module A (Prescription OCR + AI Extraction).
 *
 * INTEGRATION POINT FOR MODULE A:
 *   1. Replace the placeholder UI with a CameraX PreviewView composable.
 *   2. Attach ML Kit / Google Vision / custom OCR model.
 *   3. On successful scan, call Module A extraction function:
 *        val medication = moduleA.extractMedication(bitmap)
 *   4. Navigate to a detail/verify screen with the extracted MedicationObject.
 *   5. On user confirmation, call dao.upsertMedication(medication.toEntity()).
 *
 * TODO: Module A integrates OCR + AI extraction here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannerScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text       = "Scan Prescription",
                        fontWeight = FontWeight.Bold,
                        color      = MaterialTheme.colorScheme.onPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector        = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint               = MaterialTheme.colorScheme.onPrimary
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
        Column(
            modifier            = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Camera viewfinder placeholder
            Box(
                modifier = Modifier
                    .size(260.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primaryContainer,
                                MaterialTheme.colorScheme.surfaceVariant
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // Scanner frame decoration
                    ScannerFrameIcon()
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text      = "Camera Preview",
                        style     = MaterialTheme.typography.bodyMedium,
                        color     = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text      = "Module A",
                        style     = MaterialTheme.typography.labelSmall,
                        color     = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(Modifier.height(32.dp))

            Text(
                text      = "Point your camera at a prescription",
                style     = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color     = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text      = "AI will extract medicine names, doses, and schedules automatically.",
                style     = MaterialTheme.typography.bodySmall,
                color     = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(32.dp))

            // Scan button — Module A wires CameraX shutter here
            Button(
                onClick = {
                    // TODO: Module A — trigger camera capture / gallery import
                    // val bitmap = cameraController.takePicture()
                    // val result = moduleA.runOcr(bitmap)
                    // navController.navigate(Screen.PrescriptionDetail.createRoute(result.id))
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape  = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(Icons.Filled.DocumentScanner, contentDescription = null)
                Spacer(Modifier.width(10.dp))
                Text(
                    text       = "Scan Prescription",
                    style      = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.height(12.dp))

            // Gallery import option
            OutlinedButton(
                onClick = {
                    // TODO: Module A — open image picker for gallery import
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Filled.Image, contentDescription = null)
                Spacer(Modifier.width(10.dp))
                Text(
                    text  = "Import from Gallery",
                    style = MaterialTheme.typography.labelLarge
                )
            }

            Spacer(Modifier.height(24.dp))

            // Module integration note (remove in production)
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier           = Modifier.padding(12.dp),
                    verticalAlignment  = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector        = Icons.Filled.Info,
                        contentDescription = null,
                        tint               = MaterialTheme.colorScheme.tertiary,
                        modifier           = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text  = "Module A stub — OCR & AI extraction wires here",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }
        }
    }
}

@Composable
private fun ScannerFrameIcon() {
    Box(
        modifier          = Modifier
            .size(80.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
        contentAlignment  = Alignment.Center
    ) {
        Icon(
            imageVector        = Icons.Filled.CameraAlt,
            contentDescription = "Camera",
            tint               = MaterialTheme.colorScheme.primary,
            modifier           = Modifier.size(40.dp)
        )
    }
}
