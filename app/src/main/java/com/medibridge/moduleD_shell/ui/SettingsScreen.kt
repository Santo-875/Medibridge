package com.medibridge.moduleD_shell.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.medibridge.moduleD_shell.viewmodel.SettingsViewModel

/**
 * SettingsScreen — app configuration and theme toggle.
 *
 * Currently provides:
 *   • Light / Dark mode toggle (persisted in SettingsViewModel StateFlow)
 *
 * FUTURE SETTINGS TO ADD:
 *   • Notification time preferences (Module C)
 *   • Language / locale selection
 *   • User profile / caregiver role switch
 *   • DataStore persistence for all settings
 *   • About / version info
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {
    val isDarkMode by viewModel.isDarkMode.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text       = "Settings",
                        fontWeight = FontWeight.Bold,
                        color      = MaterialTheme.colorScheme.onPrimary
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            // ── Appearance section ────────────────────────────────────────────
            SettingsSectionHeader(title = "Appearance")

            SettingsToggleRow(
                icon        = if (isDarkMode) Icons.Filled.DarkMode else Icons.Filled.LightMode,
                title       = "Dark Mode",
                description = "Switch between light and dark theme",
                checked     = isDarkMode,
                onToggle    = { viewModel.toggleDarkMode() }
            )

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            // ── Notifications section ──────────────────────────────────────────
            SettingsSectionHeader(title = "Notifications")

            SettingsInfoRow(
                icon        = Icons.Filled.NotificationsActive,
                title       = "Reminder Alerts",
                description = "Configure via Module C (Schedule Engine)"
            )

            SettingsInfoRow(
                icon        = Icons.AutoMirrored.Filled.VolumeUp,
                title       = "Alert Sound",
                description = "Coming in Module C integration"
            )

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            // ── Privacy & Access section ───────────────────────────────────────
            SettingsSectionHeader(title = "Privacy & Access")

            SettingsInfoRow(
                icon        = Icons.Filled.ManageAccounts,
                title       = "User Role",
                description = "Patient · Caregiver · Doctor — future auth layer"
            )

            SettingsInfoRow(
                icon        = Icons.Filled.Lock,
                title       = "Data Visibility",
                description = "Control which roles see each medication"
            )

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            // ── Backend Connection section ────────────────────────────────────
            SettingsSectionHeader(title = "Backend Connection")

            val currentUrl by viewModel.backendBaseUrl.collectAsState()
            var urlInput by remember(currentUrl) { mutableStateOf(currentUrl) }

            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                OutlinedTextField(
                    value = urlInput,
                    onValueChange = { urlInput = it },
                    label = { Text("FastAPI Backend Base URL") },
                    placeholder = { Text("http://10.0.2.2:8000/") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { viewModel.updateBackendBaseUrl(urlInput) },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Save URL")
                }
            }

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            // ── Pitch Demo Scenarios section (Step 6) ──────────────────────────
            SettingsSectionHeader(title = "Pitch Demo Scenarios")

            val context = androidx.compose.ui.platform.LocalContext.current
            val demoStatus by viewModel.demoStatus.collectAsState()

            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(
                    text = "Load pre-configured clinical scenarios for hackathon pitch demonstration:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))

                Button(
                    onClick = { viewModel.loadAllDemoScenarios(context) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Load All 3 Pitch Scenarios", fontWeight = FontWeight.Bold)
                }

                Spacer(Modifier.height(8.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { viewModel.loadScenario1(context) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("1: Diabetes", style = MaterialTheme.typography.labelSmall)
                    }
                    OutlinedButton(
                        onClick = { viewModel.loadScenario2(context) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("2: Hypertn", style = MaterialTheme.typography.labelSmall)
                    }
                    OutlinedButton(
                        onClick = { viewModel.loadScenario3(context) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("3: Caretaker", style = MaterialTheme.typography.labelSmall)
                    }
                }

                if (!demoStatus.isNullOrBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = demoStatus ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            // ── About section ─────────────────────────────────────────────────
            SettingsSectionHeader(title = "About")

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape  = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.LocalHospital,
                            tint        = MaterialTheme.colorScheme.primary,
                            contentDescription = null
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text       = "MediBridge AI",
                            style      = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color      = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text  = "Version 1.0.0-hackathon-shell",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                    Text(
                        text  = "Branch: integration",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text  = "4-module hackathon shell. Modules: A=Prescription, B=Safety, C=Schedule, D=Shell+Chat",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Reusable Settings UI components
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text     = title.uppercase(),
        style    = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color    = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 4.dp)
    )
}

@Composable
private fun SettingsToggleRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    checked: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape  = RoundedCornerShape(10.dp),
            color  = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(42.dp)
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(
                    imageVector        = icon,
                    contentDescription = null,
                    tint               = MaterialTheme.colorScheme.primary,
                    modifier           = Modifier.size(22.dp)
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text       = title,
                style      = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color      = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text  = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked         = checked,
            onCheckedChange = { onToggle() },
            colors          = SwitchDefaults.colors(
                checkedThumbColor   = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor   = MaterialTheme.colorScheme.primary
            )
        )
    }
}

@Composable
private fun SettingsInfoRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String
) {
    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape  = RoundedCornerShape(10.dp),
            color  = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.size(42.dp)
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(
                    imageVector        = icon,
                    contentDescription = null,
                    tint               = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier           = Modifier.size(22.dp)
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text       = title,
                style      = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color      = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text  = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            imageVector        = Icons.Filled.ChevronRight,
            contentDescription = null,
            tint               = MaterialTheme.colorScheme.outline
        )
    }
}
