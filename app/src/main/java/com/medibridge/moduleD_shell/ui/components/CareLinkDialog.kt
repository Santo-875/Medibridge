package com.medibridge.moduleD_shell.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.medibridge.core.network.BackendClient
import com.medibridge.core.network.CareAcceptRequest
import com.medibridge.core.network.CareInviteRequest
import com.medibridge.core.session.UserSessionManager
import com.medibridge.core.sync.SyncRepository
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CareLinkDialog(
    profileId: String,
    profileName: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Invite, 1 = Join

    // Invite state
    var selectedPermission by remember { mutableStateOf("view") }
    var generatedCode by remember { mutableStateOf<String?>(null) }
    var isGenerating by remember { mutableStateOf(false) }

    // Join state
    var joinCodeInput by remember { mutableStateOf("") }
    var isJoining by remember { mutableStateOf(false) }
    var joinResultStatus by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth(0.95f),
        title = {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FamilyRestroom,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Care Links",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.height(10.dp))
                PrimaryTabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Invite Caretaker", fontWeight = FontWeight.SemiBold) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Join with Code", fontWeight = FontWeight.SemiBold) }
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (selectedTab == 0) {
                    // ── TAB 0: INVITE CARETAKER ──────────────────────────────
                    Text(
                        text = "Share an invite code to let a family member or caretaker access $profileName's medication schedule.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Text(
                        text = "Access Permission",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("view" to "Read Only", "manage" to "Manage", "alerts" to "Alerts Only").forEach { (perm, label) ->
                            FilterChip(
                                selected = selectedPermission == perm,
                                onClick = { selectedPermission = perm },
                                label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    if (generatedCode == null) {
                        PrimaryButton(
                            text = if (isGenerating) "Generating..." else "Generate Invite Code",
                            onClick = {
                                isGenerating = true
                                coroutineScope.launch {
                                    try {
                                        val api = BackendClient.getService()
                                        val res = api.createCareInvite(
                                            CareInviteRequest(
                                                profileId = profileId,
                                                permission = selectedPermission
                                            )
                                        )
                                        generatedCode = res.inviteCode
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Could not generate code: ${e.message}", Toast.LENGTH_SHORT).show()
                                    } finally {
                                        isGenerating = false
                                    }
                                }
                            },
                            enabled = !isGenerating,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "YOUR 6-CHARACTER INVITE CODE",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = generatedCode ?: "",
                                    style = MaterialTheme.typography.headlineLarge,
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 4.sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Spacer(Modifier.height(10.dp))
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    SecondaryButton(
                                        text = "Copy",
                                        icon = Icons.Default.ContentCopy,
                                        size = ButtonSize.Small,
                                        onClick = {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            clipboard.setPrimaryClip(ClipData.newPlainText("MediBridge Code", generatedCode))
                                            Toast.makeText(context, "Code copied to clipboard", Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                    PrimaryButton(
                                        text = "Share",
                                        icon = Icons.Default.Share,
                                        size = ButtonSize.Small,
                                        onClick = {
                                            val sendIntent = Intent().apply {
                                                action = Intent.ACTION_SEND
                                                putExtra(
                                                    Intent.EXTRA_TEXT,
                                                    "Join as my MediBridge caretaker! Enter invite code: $generatedCode in the MediBridge app."
                                                )
                                                type = "text/plain"
                                            }
                                            context.startActivity(Intent.createChooser(sendIntent, "Share Caretaker Invite Code"))
                                        }
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // ── TAB 1: JOIN WITH CODE ────────────────────────────────
                    Text(
                        text = "Enter the 6-character code given to you by the patient or caregiver to connect their profile to your device.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = joinCodeInput,
                        onValueChange = {
                            if (it.length <= 6) joinCodeInput = it.uppercase()
                        },
                        label = { Text("6-Character Code") },
                        placeholder = { Text("e.g. K9W3XY") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    PrimaryButton(
                        text = if (isJoining) "Connecting..." else "Join Profile",
                        onClick = {
                            val code = joinCodeInput.trim().uppercase()
                            if (code.length != 6) {
                                Toast.makeText(context, "Please enter a valid 6-character code", Toast.LENGTH_SHORT).show()
                                return@PrimaryButton
                            }
                            isJoining = true
                            coroutineScope.launch {
                                try {
                                    val api = BackendClient.getService()
                                    val res = api.acceptCareInvite(CareAcceptRequest(inviteCode = code))
                                    // Trigger sync to pull patient profile and meds
                                    SyncRepository.getInstance(context).syncNow()
                                    // Switch active profile
                                    UserSessionManager.getInstance(context).setActiveProfile(
                                        res.profileId,
                                        res.profileName
                                    )
                                    joinResultStatus = "Connected to ${res.profileName}!"
                                    Toast.makeText(context, "Connected to ${res.profileName}", Toast.LENGTH_LONG).show()
                                    onDismiss()
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Failed to join: ${e.message}", Toast.LENGTH_SHORT).show()
                                } finally {
                                    isJoining = false
                                }
                            }
                        },
                        enabled = joinCodeInput.trim().length == 6 && !isJoining,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}
