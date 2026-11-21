package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.FolderShared
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.example.BuildConfig
import com.example.data.CompanySettings
import com.example.data.UpiSettings
import com.example.data.UserAccountEntity
import com.example.data.UserSession
import com.example.data.VoiceAndNoteSettings
import com.example.data.VoicePreset
import com.example.ui.TechnicianPerformanceSummary
import com.example.ui.components.BackupPasteImportDialog
import com.example.ui.components.ProvideSafeTextToolbar
import com.example.ui.components.QrCodeImage
import com.example.ui.components.TseShieldLogo
import com.example.ui.components.bringIntoViewOnFocus
import com.example.ui.components.clearFocusAndHideToolbar
import com.example.ui.components.readBackupTextFromUri
import com.example.ui.theme.GoldAccent
import com.example.ui.theme.GreenPaid
import com.example.ui.theme.GreenPaidBg
import com.example.ui.theme.NavyPrimary
import com.example.ui.theme.RedDue
import com.example.ui.theme.RedDueBg
import com.example.ui.theme.RedDueDark
import com.example.ui.theme.TealAccent
import com.example.util.DeviceAuthHelper
import com.example.util.InvoicePdfPrinter
import com.example.util.QrGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AdminPanelScreen(
    upiSettings: UpiSettings,
    accounts: List<UserAccountEntity>,
    technicianPerformance: List<TechnicianPerformanceSummary>,
    voiceAndNoteSettings: VoiceAndNoteSettings,
    systemVoiceOptions: List<Pair<String, String>>,
    onSaveUpi: (upiId: String, payeeName: String) -> Unit,
    onClearUpi: () -> Unit,
    onAddTechnician: (name: String, phone: String, username: String, password: String, photoBitmap: Bitmap?, onDone: () -> Unit) -> Unit,
    onUpdateTechnician: (oldUsername: String, newUsername: String, name: String, phone: String, newPassword: String, newPhotoBitmap: Bitmap?, onDone: () -> Unit) -> Unit,
    onRemoveTechnician: (username: String) -> Unit,
    onAddSuperAdmin: (name: String, phone: String, username: String, password: String, onDone: () -> Unit) -> Unit,
    onRemoveSuperAdmin: (username: String) -> Unit,
    onRecordCashSettlement: (technicianUsername: String, amountText: String, note: String, onDone: () -> Unit) -> Unit,
    onSaveVoiceAndNoteSettings: (VoiceAndNoteSettings) -> Unit,
    onTestVoice: (script: String, modelId: String) -> Unit
) {
    val context = LocalContext.current
    var upiId by remember(upiSettings.upiId) { mutableStateOf(upiSettings.upiId) }
    var payeeName by remember(upiSettings.payeeName) { mutableStateOf(upiSettings.payeeName) }

    var techName by remember { mutableStateOf("") }
    var techPhone by remember { mutableStateOf("") }
    var techUsername by remember { mutableStateOf("") }
    var techPassword by remember { mutableStateOf("") }
    var techPhotoBitmap by remember { mutableStateOf<Bitmap?>(null) }

    val techPhotoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream)?.let { bmp ->
                        techPhotoBitmap = bmp
                    }
                }
            }
        }
    }

    val techCameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bmp ->
        if (bmp != null) {
            techPhotoBitmap = bmp
        }
    }

    val techCameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            techCameraLauncher.launch(null)
        } else {
            Toast.makeText(context, "Camera permission needed to capture technician photo", Toast.LENGTH_SHORT).show()
        }
    }

    var superName by remember { mutableStateOf("") }
    var superPhone by remember { mutableStateOf("") }
    var superUsername by remember { mutableStateOf("") }
    var superPassword by remember { mutableStateOf("") }

    var expandedFolderUser by remember { mutableStateOf<String?>(null) }
    var settleInputs by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var editingTech by remember { mutableStateOf<UserAccountEntity?>(null) }

    var headerNote by remember(voiceAndNoteSettings.headerNote) { mutableStateOf(voiceAndNoteSettings.headerNote) }
    var loginVoice by remember(voiceAndNoteSettings.loginVoice) { mutableStateOf(voiceAndNoteSettings.loginVoice) }
    var postLoginVoice by remember(voiceAndNoteSettings.postLoginVoice) { mutableStateOf(voiceAndNoteSettings.postLoginVoice) }
    var billGenVoice by remember(voiceAndNoteSettings.billGeneratingVoice) { mutableStateOf(voiceAndNoteSettings.billGeneratingVoice) }
    var billDoneVoice by remember(voiceAndNoteSettings.billCompletedVoice) { mutableStateOf(voiceAndNoteSettings.billCompletedVoice) }
    var voiceModelId by remember(voiceAndNoteSettings.voiceModelId) { mutableStateOf(voiceAndNoteSettings.voiceModelId) }
    var voiceDropdownOpen by remember { mutableStateOf(false) }

    val focusManager = LocalFocusManager.current
    val textToolbar = LocalTextToolbar.current

    val technicianAccounts = remember(accounts) {
        accounts.filter {
            it.role.equals("technician", ignoreCase = true) || it.role.equals("staff", ignoreCase = true)
        }
    }
    val superAdminAccounts = remember(accounts) {
        accounts.filter { it.role.equals("super_admin", ignoreCase = true) }
    }
    val perfByUsername = remember(technicianPerformance) {
        technicianPerformance.associateBy { it.username.lowercase(Locale.US) }
    }

    val allVoiceChoices = remember(systemVoiceOptions) {
        VoicePreset.entries.map { it.id to it.label } + systemVoiceOptions
    }
    val selectedVoiceLabel = allVoiceChoices.firstOrNull { it.first == voiceModelId }?.second ?: VoicePreset.CLEAR.label

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp)
            .testTag("admin_lazy_list"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Spacer(Modifier.height(4.dp)) }

        // 1. Technician Accounts & Cash/Folder Manager
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.PersonAdd, contentDescription = null, tint = TealAccent)
                            Text("Technician Accounts (${technicianAccounts.size})", style = MaterialTheme.typography.titleLarge)
                        }
                    }

                    OutlinedTextField(
                        value = techName,
                        onValueChange = { techName = it },
                        label = { Text("Technician Full Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus().testTag("new_tech_name")
                    )
                    OutlinedTextField(
                        value = techPhone,
                        onValueChange = { techPhone = it },
                        label = { Text("Technician Mobile Number") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus().testTag("new_tech_phone")
                    )
                    OutlinedTextField(
                        value = techUsername,
                        onValueChange = { techUsername = it },
                        label = { Text("Technician Username (min 3 chars)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus().testTag("new_tech_username")
                    )
                    OutlinedTextField(
                        value = techPassword,
                        onValueChange = { techPassword = it },
                        label = { Text("Technician Password (min 6 chars)") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus().testTag("new_tech_password")
                    )
                    // Mandatory Technician Profile Photo Picker
                    Surface(
                        color = if (techPhotoBitmap != null) GreenPaidBg else Color(0xFFF8FAFC),
                        border = BorderStroke(1.dp, if (techPhotoBitmap != null) GreenPaid else MaterialTheme.colorScheme.outline),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(58.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFE2E8F0))
                                    .border(1.5.dp, if (techPhotoBitmap != null) GreenPaid else TealAccent, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                val bmp = techPhotoBitmap
                                if (bmp != null) {
                                    Image(
                                        bitmap = bmp.asImageBitmap(),
                                        contentDescription = "New technician photo preview",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Icon(Icons.Default.Person, contentDescription = "Photo required", tint = NavyPrimary, modifier = Modifier.size(30.dp))
                                }
                            }
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = if (techPhotoBitmap != null) "Technician Photo Ready" else "Technician Photo (Required)",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = if (techPhotoBitmap != null) GreenPaid else RedDueDark,
                                    modifier = Modifier.testTag("new_tech_photo_status")
                                )
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            clearFocusAndHideToolbar(focusManager, textToolbar)
                                            techPhotoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                        },
                                        modifier = Modifier.testTag("new_tech_pick_photo_button")
                                    ) {
                                        Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Upload Pic")
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            clearFocusAndHideToolbar(focusManager, textToolbar)
                                            val granted = ContextCompat.checkSelfPermission(
                                                context,
                                                Manifest.permission.CAMERA
                                            ) == PackageManager.PERMISSION_GRANTED
                                            if (granted) techCameraLauncher.launch(null)
                                            else techCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                        },
                                        modifier = Modifier.testTag("new_tech_camera_photo_button")
                                    ) {
                                        Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Take Pic")
                                    }
                                }
                            }
                        }
                    }

                    Button(
                        onClick = {
                            clearFocusAndHideToolbar(focusManager, textToolbar)
                            onAddTechnician(techName, techPhone, techUsername, techPassword, techPhotoBitmap) {
                                techName = ""
                                techPhone = ""
                                techUsername = ""
                                techPassword = ""
                                techPhotoBitmap = null
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = GreenPaid),
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("add_technician_button")
                    ) {
                        Text("Add Technician", fontWeight = FontWeight.Bold)
                    }

                    HorizontalDivider()

                    if (technicianAccounts.isEmpty()) {
                        Text(
                            text = "No Technician accounts added yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        technicianAccounts.forEach { tech ->
                            val stats = perfByUsername[tech.username.lowercase(Locale.US)]
                            val techAvatar = remember(tech.photoBase64) {
                                tech.photoBase64.takeIf { it.isNotBlank() }?.let { InvoicePdfPrinter.decodeBase64Bitmap(it) }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFFE2E8F0)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (techAvatar != null) {
                                            Image(
                                                bitmap = techAvatar.asImageBitmap(),
                                                contentDescription = "${tech.displayName} avatar",
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop
                                            )
                                        } else {
                                            Icon(Icons.Default.Person, contentDescription = null, tint = TealAccent)
                                        }
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(tech.displayName.ifBlank { tech.username }, fontWeight = FontWeight.Bold)
                                        Text(
                                            text = "@${tech.username} · ${tech.phone.ifBlank { "No phone" }} · Bills: ${stats?.billCount ?: 0}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = {
                                            clearFocusAndHideToolbar(focusManager, textToolbar)
                                            editingTech = tech
                                        },
                                        modifier = Modifier.testTag("edit_tech_${tech.username}")
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = "Edit Technician", tint = TealAccent)
                                    }
                                    IconButton(
                                        onClick = { onRemoveTechnician(tech.username) },
                                        modifier = Modifier.testTag("remove_tech_${tech.username}")
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Remove Technician", tint = RedDue)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 2. Technician Folders & Admin Cash Due Balance Settlement
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.testTag("technician_folders_card")
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.FolderShared, contentDescription = null, tint = GoldAccent)
                        Text("Technician Folders & Cash Settlement", style = MaterialTheme.typography.titleLarge)
                    }
                    Text(
                        text = "Inspect each Technician's Bills, Quotations, Help Desk jobs, 5-Star Ratings, Online vs Cash totals, and settle collected cash (Admin Due Balance).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (technicianPerformance.isEmpty()) {
                        Text(
                            text = "No Technician performance records yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        technicianPerformance.forEach { summary ->
                            val isExpanded = expandedFolderUser == summary.username
                            val hasCashDue = summary.adminDueBalance > 0.009
                            val currentSettleInput = settleInputs[summary.username] ?: ""

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (hasCashDue) Color(0xFFFFFBEB) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                border = BorderStroke(1.dp, if (hasCashDue) GoldAccent else MaterialTheme.colorScheme.outline),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("tech_folder_${summary.username}")
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                expandedFolderUser = if (isExpanded) null else summary.username
                                            },
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "${summary.displayName} (@${summary.username}) · ${if (summary.role == "admin") "Admin" else "Technician"}",
                                                fontWeight = FontWeight.Bold,
                                                color = NavyPrimary
                                            )
                                            Text(
                                                text = "Bills: ${summary.billCount} · Quotations: ${summary.quotationCount} · Help Solved: ${summary.helpSolvedCount}/${summary.helpAcceptedCount}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Color(0xFF334155)
                                            )
                                        }
                                        Icon(
                                            if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                            contentDescription = "Toggle Technician Folder",
                                            tint = NavyPrimary
                                        )
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "Online: ₹${String.format(Locale.US, "%.0f", summary.onlineTotal)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = TealAccent,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "Cash: ₹${String.format(Locale.US, "%.0f", summary.cashTotal)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = GreenPaid,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "Admin Cash Due: ₹${String.format(Locale.US, "%.0f", summary.adminDueBalance)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (hasCashDue) RedDueDark else GreenPaid,
                                            fontWeight = FontWeight.Black,
                                            modifier = Modifier.testTag("tech_admin_due_${summary.username}")
                                        )
                                    }

                                    if (summary.ratedCount > 0) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Star, contentDescription = null, tint = GoldAccent, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text(
                                                text = "${String.format(Locale.US, "%.1f", summary.averageRating)} / 5 (${summary.ratedCount} reviews)",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = NavyPrimary,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }

                                    AnimatedVisibility(visible = isExpanded) {
                                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            HorizontalDivider()
                                            Text(
                                                text = "Settled to Admin: ₹${String.format(Locale.US, "%.2f", summary.settledToAdminTotal)} · Remaining Cash Balance: ₹${String.format(Locale.US, "%.2f", summary.adminDueBalance)}",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = NavyPrimary
                                            )
                                            if (summary.latestReviewNote.isNotBlank()) {
                                                Text(
                                                    text = "Latest Customer Review: “${summary.latestReviewNote}”",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = Color(0xFF334155)
                                                )
                                            }
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                OutlinedTextField(
                                                    value = currentSettleInput,
                                                    onValueChange = { txt ->
                                                        settleInputs = settleInputs + (summary.username to txt)
                                                    },
                                                    label = { Text("Cash Received (₹)") },
                                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                                    singleLine = true,
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .bringIntoViewOnFocus()
                                                        .testTag("settle_input_${summary.username}")
                                                )
                                                Button(
                                                    onClick = {
                                                        clearFocusAndHideToolbar(focusManager, textToolbar)
                                                        val amt = currentSettleInput.ifBlank {
                                                            String.format(Locale.US, "%.2f", summary.adminDueBalance)
                                                        }
                                                        onRecordCashSettlement(summary.username, amt, "Cash handed to Admin") {
                                                            settleInputs = settleInputs - summary.username
                                                        }
                                                    },
                                                    colors = ButtonDefaults.buttonColors(containerColor = GreenPaid),
                                                    modifier = Modifier.height(50.dp).testTag("settle_cash_${summary.username}")
                                                ) {
                                                    Icon(Icons.Default.Payments, contentDescription = null, modifier = Modifier.size(18.dp))
                                                    Spacer(Modifier.width(4.dp))
                                                    Text("Clear Cash")
                                                }
                                            }
                                            summary.bills.take(5).forEach { b ->
                                                Text(
                                                    text = "• ${b.invoiceNo} (${b.customerName.ifBlank { "Walk-in" }}) — ₹${String.format(Locale.US, "%.0f", b.grandTotal)} [${b.paymentMode}]",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = Color(0xFF1E293B)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. Super Admin Accounts (Read-Only Analytics & Market Role)
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.AdminPanelSettings, contentDescription = null, tint = NavyPrimary)
                        Text("Super Admin Accounts (Read-Only Analytics)", style = MaterialTheme.typography.titleLarge)
                    }
                    Text(
                        text = "Super Admin can view live analytics, charts, bills, customers, products, and help desk, but cannot modify anything or generate bills.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = superName,
                        onValueChange = { superName = it },
                        label = { Text("Super Admin Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus().testTag("new_super_name")
                    )
                    OutlinedTextField(
                        value = superPhone,
                        onValueChange = { superPhone = it },
                        label = { Text("Mobile (Optional)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = superUsername,
                            onValueChange = { superUsername = it },
                            label = { Text("Username") },
                            singleLine = true,
                            modifier = Modifier.weight(1f).bringIntoViewOnFocus().testTag("new_super_username")
                        )
                        OutlinedTextField(
                            value = superPassword,
                            onValueChange = { superPassword = it },
                            label = { Text("Password") },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier.weight(1f).bringIntoViewOnFocus().testTag("new_super_password")
                        )
                    }
                    Button(
                        onClick = {
                            clearFocusAndHideToolbar(focusManager, textToolbar)
                            onAddSuperAdmin(superName, superPhone, superUsername, superPassword) {
                                superName = ""
                                superPhone = ""
                                superUsername = ""
                                superPassword = ""
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary),
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("add_super_admin_button")
                    ) {
                        Text("Create Super Admin Account", fontWeight = FontWeight.Bold)
                    }

                    if (superAdminAccounts.isNotEmpty()) {
                        HorizontalDivider()
                        superAdminAccounts.forEach { sa ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(sa.displayName.ifBlank { sa.username }, fontWeight = FontWeight.Bold)
                                    Text("@${sa.username} · Super Admin (View-Only)", style = MaterialTheme.typography.bodySmall)
                                }
                                IconButton(
                                    onClick = { onRemoveSuperAdmin(sa.username) },
                                    modifier = Modifier.testTag("remove_super_${sa.username}")
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Remove Super Admin", tint = RedDue)
                                }
                            }
                        }
                    }
                }
            }
        }

        // 4. Header Marquee Note & Voice Announcements CMS
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Campaign, contentDescription = null, tint = TealAccent)
                        Text("Header Note & Voice Prompts CMS", style = MaterialTheme.typography.titleLarge)
                    }
                    Text(
                        text = "Customize the continuous scrolling Header Note and English Voice Announcements played on login, billing, and completion.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = headerNote,
                        onValueChange = { headerNote = it },
                        label = { Text("Scrolling Header Note Banner") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus().testTag("cms_header_note")
                    )

                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { voiceDropdownOpen = true },
                            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("cms_voice_model_dropdown"),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Voice Model: $selectedVoiceLabel", fontWeight = FontWeight.SemiBold)
                                Icon(Icons.Default.ArrowDropDown, contentDescription = "Choose Voice Model")
                            }
                        }
                        DropdownMenu(
                            expanded = voiceDropdownOpen,
                            onDismissRequest = { voiceDropdownOpen = false }
                        ) {
                            allVoiceChoices.forEach { (id, label) ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = {
                                        voiceModelId = id
                                        voiceDropdownOpen = false
                                    }
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = loginVoice,
                        onValueChange = { loginVoice = it },
                        label = { Text("Login Screen Welcome Voice") },
                        modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus().testTag("cms_login_voice")
                    )
                    OutlinedTextField(
                        value = postLoginVoice,
                        onValueChange = { postLoginVoice = it },
                        label = { Text("After Login Success Voice") },
                        modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus().testTag("cms_post_login_voice")
                    )
                    OutlinedTextField(
                        value = billGenVoice,
                        onValueChange = { billGenVoice = it },
                        label = { Text("Summary / During Bill Generation Voice") },
                        modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus().testTag("cms_bill_gen_voice")
                    )
                    OutlinedTextField(
                        value = billDoneVoice,
                        onValueChange = { billDoneVoice = it },
                        label = { Text("After Bill Generated Voice (supports {amount} & {due})") },
                        modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus().testTag("cms_bill_done_voice")
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { onTestVoice(billDoneVoice, voiceModelId) },
                            modifier = Modifier.weight(1f).height(48.dp).testTag("cms_test_voice_button")
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("Test Voice")
                        }
                        Button(
                            onClick = {
                                clearFocusAndHideToolbar(focusManager, textToolbar)
                                onSaveVoiceAndNoteSettings(
                                    VoiceAndNoteSettings(
                                        headerNote = headerNote.trim(),
                                        loginVoice = loginVoice.trim(),
                                        postLoginVoice = postLoginVoice.trim(),
                                        billGeneratingVoice = billGenVoice.trim(),
                                        billCompletedVoice = billDoneVoice.trim(),
                                        voiceModelId = voiceModelId
                                    )
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = TealAccent),
                            modifier = Modifier.weight(1.3f).height(48.dp).testTag("cms_save_voice_button")
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("Save Voice & Note", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // 5. Online Payment UPI ID
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.QrCode2, contentDescription = null, tint = TealAccent)
                        Text("Online Payment UPI ID (Auto QR)", style = MaterialTheme.typography.titleLarge)
                    }
                    Text(
                        text = "Enter your business UPI ID to generate dynamic Payment QR codes for exact Paid Amounts inside Billing Summary.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = upiId,
                        onValueChange = { upiId = it },
                        label = { Text("UPI ID (e.g. 7083805120@ybl)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus().testTag("admin_upi_id_input")
                    )
                    OutlinedTextField(
                        value = payeeName,
                        onValueChange = { payeeName = it },
                        label = { Text("Account / Payee Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus().testTag("admin_upi_name_input")
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                clearFocusAndHideToolbar(focusManager, textToolbar)
                                onSaveUpi(upiId, payeeName)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = GreenPaid),
                            modifier = Modifier.testTag("save_upi_button")
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("Save UPI ID")
                        }
                        OutlinedButton(
                            onClick = {
                                clearFocusAndHideToolbar(focusManager, textToolbar)
                                upiId = ""
                                onClearUpi()
                            },
                            modifier = Modifier.testTag("clear_upi_button")
                        ) {
                            Text("Clear UPI ID")
                        }
                    }

                    AnimatedVisibility(visible = upiSettings.upiId.isNotBlank()) {
                        val previewUri = remember(upiSettings.upiId, upiSettings.payeeName) {
                            QrGenerator.buildUpiUri(upiSettings.upiId, upiSettings.payeeName, 0.0, "")
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            QrCodeImage(
                                content = previewUri,
                                contentDescription = "Active UPI QR Preview",
                                modifier = Modifier.size(92.dp)
                            )
                            Column {
                                Text("Active UPI QR", style = MaterialTheme.typography.titleMedium, color = GreenPaid)
                                Text("UPI ID: ${upiSettings.upiId}", style = MaterialTheme.typography.bodyMedium)
                                Text("A/c: ${upiSettings.payeeName}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(16.dp)) }
    }

    val techToEdit = editingTech
    if (techToEdit != null) {
        EditTechnicianDialog(
            account = techToEdit,
            onDismiss = { editingTech = null },
            onSave = { newUsername, newName, newPhone, newPassword, newPhoto ->
                onUpdateTechnician(techToEdit.username, newUsername, newName, newPhone, newPassword, newPhoto) {
                    editingTech = null
                }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditTechnicianDialog(
    account: UserAccountEntity,
    onDismiss: () -> Unit,
    onSave: (newUsername: String, name: String, phone: String, newPassword: String, newPhotoBitmap: Bitmap?) -> Unit
) {
    val context = LocalContext.current
    var name by remember(account.username) { mutableStateOf(account.displayName) }
    var phone by remember(account.username) { mutableStateOf(account.phone) }
    var username by remember(account.username) { mutableStateOf(account.username) }
    var newPassword by remember(account.username) { mutableStateOf("") }
    var newPhotoBitmap by remember(account.username) { mutableStateOf<Bitmap?>(null) }
    val existingPhotoBitmap = remember(account.photoBase64) {
        account.photoBase64.takeIf { it.isNotBlank() }?.let { InvoicePdfPrinter.decodeBase64Bitmap(it) }
    }
    val previewPhoto = newPhotoBitmap ?: existingPhotoBitmap

    val editPhotoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream)?.let { bmp -> newPhotoBitmap = bmp }
                }
            }
        }
    }
    val editCameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bmp ->
        if (bmp != null) newPhotoBitmap = bmp
    }
    val editCameraPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) editCameraLauncher.launch(null)
    }

    val focusManager = LocalFocusManager.current
    val textToolbar = LocalTextToolbar.current

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Edit, contentDescription = null, tint = TealAccent) },
        title = { Text("Edit Technician (@${account.username})") },
        text = {
            ProvideSafeTextToolbar {
                DisposableEffect(focusManager, textToolbar) {
                    onDispose { clearFocusAndHideToolbar(focusManager, textToolbar) }
                }
                Column(
                    modifier = Modifier.imePadding().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(54.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFE2E8F0)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (previewPhoto != null) {
                                Image(
                                    bitmap = previewPhoto.asImageBitmap(),
                                    contentDescription = "Technician photo",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Icon(Icons.Default.Person, contentDescription = null, tint = NavyPrimary)
                            }
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(
                                onClick = {
                                    editPhotoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                },
                                modifier = Modifier.testTag("edit_tech_pick_photo_button")
                            ) {
                                Text("Change Pic")
                            }
                            OutlinedButton(
                                onClick = {
                                    val granted = ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.CAMERA
                                    ) == PackageManager.PERMISSION_GRANTED
                                    if (granted) editCameraLauncher.launch(null)
                                    else editCameraPermLauncher.launch(Manifest.permission.CAMERA)
                                }
                            ) {
                                Text("Camera")
                            }
                        }
                    }
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Technician Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus().testTag("edit_tech_name")
                    )
                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = { Text("Mobile Number") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus().testTag("edit_tech_phone")
                    )
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text("Username ID") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus().testTag("edit_tech_username")
                    )
                    OutlinedTextField(
                        value = newPassword,
                        onValueChange = { newPassword = it },
                        label = { Text("New Password (leave blank to keep current)") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus().testTag("edit_tech_password")
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    clearFocusAndHideToolbar(focusManager, textToolbar)
                    onSave(username, name, phone, newPassword, newPhotoBitmap)
                },
                colors = ButtonDefaults.buttonColors(containerColor = GreenPaid),
                modifier = Modifier.testTag("save_edit_tech_button")
            ) {
                Text("Save Changes", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = {
                clearFocusAndHideToolbar(focusManager, textToolbar)
                onDismiss()
            }) {
                Text("Cancel")
            }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    settings: CompanySettings,
    user: UserSession,
    deviceAuthEnabled: Boolean,
    updateCheckBusy: Boolean,
    onToggleDeviceAuth: (Boolean) -> Unit,
    onSaveSettings: (CompanySettings) -> Unit,
    onPickCompanyLogoBitmap: (Bitmap?) -> Unit,
    onPickFallbackQrBitmap: (Bitmap) -> Unit,
    onPickAuthorisedSignatureBitmap: (Bitmap?) -> Unit = {},
    onExportBackup: suspend () -> String,
    onImportBackup: (String) -> Unit,
    onBackupExported: () -> Unit = {},
    onCheckForUpdates: () -> Unit = {}
) {
    val canEditSettings = user.isAdmin && !user.isSuperAdmin
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val textToolbar = LocalTextToolbar.current

    var company by remember(settings.companyName) { mutableStateOf(settings.companyName) }
    var mobile by remember(settings.mobile) { mutableStateOf(settings.mobile) }
    var email by remember(settings.email) { mutableStateOf(settings.email) }
    var address by remember(settings.address) { mutableStateOf(settings.address) }
    var testLockResult by remember { mutableStateOf("") }

    val authSigPreview = remember(settings.authorisedSignatureBase64) {
        InvoicePdfPrinter.decodeBase64Bitmap(settings.authorisedSignatureBase64)
    }

    val logoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null && canEditSettings) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream)?.let { bmp ->
                        onPickCompanyLogoBitmap(bmp)
                    }
                }
            }
        }
    }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null && canEditSettings) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream)?.let { bmp ->
                        onPickFallbackQrBitmap(bmp)
                    }
                }
            }
        }
    }

    val authSignaturePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null && canEditSettings) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream)?.let { bmp ->
                        onPickAuthorisedSignatureBitmap(bmp)
                    }
                }
            }
        }
    }

    var showPasteBackupDialog by remember { mutableStateOf(false) }

    fun handleImportedUri(uri: Uri?) {
        if (uri == null || !canEditSettings) return
        scope.launch {
            val res = readBackupTextFromUri(context, uri)
            res.onSuccess { text ->
                onImportBackup(text)
            }.onFailure { err ->
                Toast.makeText(
                    context,
                    err.message ?: "Unable to read selected backup file.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    val exportDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null && canEditSettings) {
            scope.launch {
                val writeResult = runCatching {
                    val json = onExportBackup()
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri)?.use { out ->
                            out.write(json.toByteArray(Charsets.UTF_8))
                        } ?: error("Output stream unavailable")
                    }
                }
                if (writeResult.isSuccess) {
                    onBackupExported()
                } else {
                    Toast.makeText(
                        context,
                        writeResult.exceptionOrNull()?.message ?: "Could not save backup file.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    val importDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        handleImportedUri(uri)
    }

    val importGetContentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        handleImportedUri(uri)
    }

    fun launchSafeSettingsBackupImport() {
        clearFocusAndHideToolbar(focusManager, textToolbar)
        val launchedOpenDoc = runCatching {
            importDocLauncher.launch(arrayOf("*/*"))
            true
        }.getOrDefault(false)
        if (launchedOpenDoc) return

        val launchedGetContent = runCatching {
            importGetContentLauncher.launch("*/*")
            true
        }.getOrDefault(false)
        if (!launchedGetContent) {
            Toast.makeText(
                context,
                "No system file picker available. Paste your backup JSON below.",
                Toast.LENGTH_LONG
            ).show()
            showPasteBackupDialog = true
        }
    }

    val availabilityMsg = remember(context) { DeviceAuthHelper.getAvailabilityDescription(context) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp)
            .testTag("settings_lazy_list"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Spacer(Modifier.height(4.dp)) }

        // Mandatory Device Authentication Card (Available & required for BOTH Admin and Technician)
        if (!user.isSuperAdmin) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (deviceAuthEnabled) GreenPaidBg else RedDueBg
                    ),
                    border = BorderStroke(1.5.dp, if (deviceAuthEnabled) GreenPaid else RedDue)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(
                                Icons.Default.Fingerprint,
                                contentDescription = null,
                                tint = if (deviceAuthEnabled) GreenPaid else RedDueDark
                            )
                            Text(
                                text = "Device Authentication (Mandatory for Billing)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (deviceAuthEnabled) GreenPaid else RedDueDark
                            )
                        }
                        Text(
                            text = "Both Admin and Technicians must enable Face, Fingerprint, or Device Screen Lock verification before generating bills or quotations.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF1E293B)
                        )
                        Text(
                            text = "Device Status: $availabilityMsg",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF0F172A)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (deviceAuthEnabled) "Enabled for @${user.username}" else "Disabled (Required)",
                                fontWeight = FontWeight.Bold,
                                color = if (deviceAuthEnabled) GreenPaid else RedDueDark
                            )
                            Switch(
                                checked = deviceAuthEnabled,
                                onCheckedChange = onToggleDeviceAuth,
                                modifier = Modifier.testTag("device_auth_switch")
                            )
                        }
                        val activity = context as? FragmentActivity
                        if (activity != null) {
                            OutlinedButton(
                                onClick = {
                                    DeviceAuthHelper.authenticate(
                                        activity = activity,
                                        title = "Test Device Lock",
                                        subtitle = "Verify Face, Fingerprint, or Screen Password",
                                        onSuccess = { testLockResult = "Verified successfully!" },
                                        onError = { err -> testLockResult = err }
                                    )
                                },
                                modifier = Modifier.testTag("test_device_auth_button")
                            ) {
                                Icon(Icons.Default.Shield, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Test Device Lock Prompt", color = Color(0xFF0B2238))
                            }
                            if (testLockResult.isNotBlank()) {
                                Text(testLockResult, style = MaterialTheme.typography.bodySmall, color = NavyPrimary)
                            }
                        }
                    }
                }
            }
        }

        // Company Profile Card
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = if (canEditSettings) "Company Settings" else "Company Information (Read-Only)",
                        style = MaterialTheme.typography.titleLarge
                    )
                    if (canEditSettings) {
                        Text(
                            text = "Updates apply immediately across the top header, invoices, quotations, and dispatch cards.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value = company,
                            onValueChange = { company = it },
                            label = { Text("Company Name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus().testTag("settings_company_name")
                        )
                        OutlinedTextField(
                            value = mobile,
                            onValueChange = { mobile = it },
                            label = { Text("Mobile / WhatsApp") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus().testTag("settings_mobile")
                        )
                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it },
                            label = { Text("Email") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus().testTag("settings_email")
                        )
                        OutlinedTextField(
                            value = address,
                            onValueChange = { address = it },
                            label = { Text("Office Address") },
                            minLines = 2,
                            modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus().testTag("settings_address")
                        )
                        Button(
                            onClick = {
                                clearFocusAndHideToolbar(focusManager, textToolbar)
                                onSaveSettings(
                                    settings.copy(
                                        companyName = company,
                                        mobile = mobile,
                                        email = email,
                                        address = address
                                    )
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = TealAccent),
                            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("save_settings_button")
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Save Company Settings", fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Text(
                            text = "Technicians can view company contact details and configure Device Authentication above.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text("Company: ${settings.companyName}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text("Mobile / WhatsApp: ${settings.mobile}", style = MaterialTheme.typography.bodyMedium)
                        Text("Email: ${settings.email}", style = MaterialTheme.typography.bodyMedium)
                        Text("Address: ${settings.address}", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        if (canEditSettings) {
            // Custom Company Logo Upload / Reset
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Company Logo (Admin Only)", style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = "Upload a custom logo photo or use the built-in TSE Shield emblem across header, A4 Print, PDF, and PNG bills.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            TseShieldLogo(
                                logoBase64 = settings.logoBase64,
                                modifier = Modifier.size(72.dp)
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        clearFocusAndHideToolbar(focusManager, textToolbar)
                                        logoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = TealAccent),
                                    modifier = Modifier.testTag("upload_company_logo_button")
                                ) {
                                    Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                                    Spacer(Modifier.width(6.dp))
                                    Text("Change Logo")
                                }
                                if (settings.logoBase64.isNotBlank()) {
                                    OutlinedButton(
                                        onClick = { onPickCompanyLogoBitmap(null) },
                                        modifier = Modifier.testTag("reset_company_logo_button")
                                    ) {
                                        Text("Reset Default")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Authorised Signatory Upload
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Draw, contentDescription = null, tint = TealAccent)
                            Text("Authorised Signatory Photo (Admin Only)", style = MaterialTheme.typography.titleLarge)
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(100.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color.White)
                                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (authSigPreview != null) {
                                Image(
                                    bitmap = authSigPreview.asImageBitmap(),
                                    contentDescription = "Authorised Signatory Photo Preview",
                                    modifier = Modifier.fillMaxSize().padding(6.dp),
                                    contentScale = ContentScale.Fit
                                )
                            } else {
                                Text(
                                    text = "No Authorised Signatory photo uploaded yet",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF64748B)
                                )
                            }
                        }
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    clearFocusAndHideToolbar(focusManager, textToolbar)
                                    authSignaturePicker.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = TealAccent),
                                modifier = Modifier.testTag("upload_auth_signature_button")
                            ) {
                                Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("Upload Signatory", fontWeight = FontWeight.Bold)
                            }
                            if (authSigPreview != null) {
                                OutlinedButton(
                                    onClick = {
                                        clearFocusAndHideToolbar(focusManager, textToolbar)
                                        onPickAuthorisedSignatureBitmap(null)
                                    },
                                    modifier = Modifier.testTag("clear_auth_signature_button")
                                ) {
                                    Icon(Icons.Default.Clear, contentDescription = null)
                                    Spacer(Modifier.width(4.dp))
                                    Text("Clear Photo")
                                }
                            }
                        }
                    }
                }
            }

            // Full Backup & In-App GitHub Auto-Update
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Backup, Fallback QR & App Updates (v${BuildConfig.VERSION_NAME})", style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = "Export/Import a .json backup preserving all Bills, Accounts, Technicians, Customers, Products, Settlements, and Voice Settings. Check GitHub Releases directly inside the app for APK updates.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    clearFocusAndHideToolbar(focusManager, textToolbar)
                                    photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                },
                                modifier = Modifier.testTag("pick_qr_image_button")
                            ) {
                                Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("Upload Fallback QR")
                            }
                            Button(
                                onClick = {
                                    clearFocusAndHideToolbar(focusManager, textToolbar)
                                    val launched = runCatching {
                                        exportDocLauncher.launch("TSE-Billing-Backup.json")
                                        true
                                    }.getOrDefault(false)
                                    if (!launched) {
                                        scope.launch {
                                            val json = onExportBackup()
                                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                type = "text/plain"
                                                putExtra(Intent.EXTRA_SUBJECT, "TSE-Billing-Backup.json")
                                                putExtra(Intent.EXTRA_TEXT, json)
                                            }
                                            runCatching {
                                                context.startActivity(Intent.createChooser(shareIntent, "Save / Share Backup JSON"))
                                            }.onFailure {
                                                Toast.makeText(context, "Unable to open file exporter.", Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = GreenPaid),
                                modifier = Modifier.testTag("export_backup_button")
                            ) {
                                Icon(Icons.Default.CloudDownload, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("Export Backup")
                            }
                            OutlinedButton(
                                onClick = { launchSafeSettingsBackupImport() },
                                modifier = Modifier.testTag("import_backup_button")
                            ) {
                                Icon(Icons.Default.CloudUpload, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("Import Backup")
                            }
                            OutlinedButton(
                                onClick = {
                                    clearFocusAndHideToolbar(focusManager, textToolbar)
                                    showPasteBackupDialog = true
                                },
                                modifier = Modifier.testTag("paste_import_backup_button")
                            ) {
                                Text("Paste JSON")
                            }
                            Button(
                                onClick = {
                                    clearFocusAndHideToolbar(focusManager, textToolbar)
                                    onCheckForUpdates()
                                },
                                enabled = !updateCheckBusy,
                                colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary),
                                modifier = Modifier.testTag("online_update_button")
                            ) {
                                Icon(Icons.Default.SystemUpdate, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text(if (updateCheckBusy) "Checking..." else "Check App Update")
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(16.dp)) }
    }

    if (showPasteBackupDialog) {
        BackupPasteImportDialog(
            onDismiss = { showPasteBackupDialog = false },
            onRetryFilePicker = {
                showPasteBackupDialog = false
                launchSafeSettingsBackupImport()
            },
            onImportJsonText = onImportBackup
        )
    }
}
