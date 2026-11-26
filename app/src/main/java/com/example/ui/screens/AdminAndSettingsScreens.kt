package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
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
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.FolderShared
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.FragmentActivity
import com.example.BuildConfig
import com.example.data.AppIdentity
import com.example.data.AuditLogEntity
import com.example.data.AuthPolicy
import com.example.data.CloudAuthUser
import com.example.data.CloudTechCreationResult
import com.example.data.CloudWorkspaceInfo
import com.example.data.CompanySettings
import com.example.data.ExpenseEntity
import com.example.data.TrashItemEntity
import com.example.data.UpiSettings
import com.example.data.UserAccountEntity
import com.example.data.UserSession
import com.example.data.VoiceAndNoteSettings
import com.example.data.VoicePreset
import com.example.ui.OtpUiState
import com.example.ui.TechnicianPerformanceSummary
import com.example.ui.components.BackupPasteImportDialog
import com.example.ui.components.PasswordStrengthChecklist
import com.example.ui.components.ProvideSafeTextToolbar
import com.example.ui.components.QrCodeImage
import com.example.ui.components.TseShieldLogo
import com.example.ui.components.bringIntoViewOnFocus
import com.example.ui.components.clearFocusAndHideToolbar
import com.example.ui.components.readBackupTextFromUri
import com.example.ui.theme.BlueAction
import com.example.ui.theme.GoldAccent
import com.example.ui.theme.GreenPaid
import com.example.ui.theme.GreenPaidBg
import com.example.ui.theme.NavyDeep
import com.example.ui.theme.RedDue
import com.example.ui.theme.RedDueBg
import com.example.ui.theme.RedDueDark
import com.example.ui.theme.TealAccent
import com.example.util.ApkShareHelper
import com.example.util.BackupFileHelper
import com.example.util.BackupSaveOutcome
import com.example.util.BitmapUtils
import com.example.util.DeviceAuthHelper
import com.example.util.GitHubReleaseInfo
import com.example.util.InvoicePdfPrinter
import com.example.util.QrGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AdminPanelScreen(
    upiSettings: UpiSettings,
    accounts: List<UserAccountEntity>,
    technicianPerformance: List<TechnicianPerformanceSummary>,
    voiceAndNoteSettings: VoiceAndNoteSettings,
    systemVoiceOptions: List<Pair<String, String>>,
    cloudUser: CloudAuthUser? = null,
    cloudWorkspaceInfo: CloudWorkspaceInfo = CloudWorkspaceInfo(),
    cloudBusy: Boolean = false,
    cloudTechBusy: Boolean = false,
    cloudAuthError: String = "",
    availableUpdate: GitHubReleaseInfo? = null,
    expenses: List<ExpenseEntity> = emptyList(),
    trashItems: List<TrashItemEntity> = emptyList(),
    auditLogs: List<AuditLogEntity> = emptyList(),
    otpState: OtpUiState = OtpUiState(),
    onSaveUpi: (upiId: String, payeeName: String) -> Unit,
    onClearUpi: () -> Unit,
    onAddTechnician: (name: String, phone: String, username: String, password: String, photoBitmap: Bitmap?, onDone: () -> Unit) -> Unit,
    onCreateCloudTechnician: (name: String, phone: String, email: String, username: String, initialPassword: String, photoBitmap: Bitmap?, onDone: () -> Unit) -> Unit = { _, _, _, _, _, _, _ -> },
    onCreateCloudTechnicianOtp: (name: String, phone: String, email: String, username: String, initialPassword: String, photoBitmap: Bitmap?, otpChallengeId: String, otpToken: String, onDone: () -> Unit) -> Unit = { n, p, e, u, pw, bmp, _, _, done ->
        onCreateCloudTechnician(n, p, e, u, pw, bmp, done)
    },
    onRequestOtp: (email: String, purpose: String) -> Unit = { _, _ -> },
    onVerifyOtp: (otp: String, onVerified: () -> Unit) -> Unit = { _, _ -> },
    onToggleTechnicianLogin: (UserAccountEntity, Boolean) -> Unit = { _, _ -> },
    onUpdateTechnician: (oldUsername: String, newUsername: String, name: String, phone: String, newPassword: String, newPhotoBitmap: Bitmap?, onDone: () -> Unit) -> Unit,
    onRemoveTechnician: (username: String) -> Unit,
    onAddSuperAdmin: (name: String, phone: String, username: String, password: String, onDone: () -> Unit) -> Unit,
    onRemoveSuperAdmin: (username: String) -> Unit,
    onRecordCashSettlement: (technicianUsername: String, amountText: String, note: String, onDone: () -> Unit) -> Unit,
    onSaveVoiceAndNoteSettings: (VoiceAndNoteSettings) -> Unit,
    onTestVoice: (script: String, modelId: String) -> Unit,
    onSignInGoogle: () -> Unit = {},
    onEnsureCloudWorkspace: () -> Unit = {},
    onShareApkWhatsApp: (targetRole: String) -> Unit = {},
    onUpdateAdminVerifyCode: (currentAdminPass: String, newCode: String, onSuccess: () -> Unit) -> Unit = { _, _, _ -> },
    onUpdateAdminCredentials: (currentAdminPass: String, newPass: String, confirmNewPass: String, newCode: String, onSuccess: () -> Unit) -> Unit = { _, _, _, _, _ -> },
    onSaveExpense: (title: String, category: String, amountText: String, note: String, onSuccess: () -> Unit) -> Unit = { _, _, _, _, _ -> },
    onDeleteExpense: (String) -> Unit = {},
    onRestoreTrash: (String) -> Unit = {},
    onPurgeExpiredTrash: () -> Unit = {},
    lastCreatedTechPasskey: CloudTechCreationResult? = null,
    onDismissCreatedTechPasskey: () -> Unit = {},
    onSendTeamNotification: (category: String, title: String, message: String, targetRole: String, onDone: () -> Unit) -> Unit = { _, _, _, _, _ -> }
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var upiId by remember(upiSettings.upiId) { mutableStateOf(upiSettings.upiId) }
    var payeeName by remember(upiSettings.payeeName) { mutableStateOf(upiSettings.payeeName) }

    var techName by remember { mutableStateOf("") }
    var techPhone by remember { mutableStateOf("") }
    var techEmail by remember { mutableStateOf("") }
    var techOtpInput by remember { mutableStateOf("") }
    var techUsername by remember { mutableStateOf("") }
    var techPassword by remember { mutableStateOf("") }
    var techPhotoBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var pendingTechCamPath by rememberSaveable { mutableStateOf<String?>(null) }

    var curAdminPassFor2Way by remember { mutableStateOf("") }
    var newAdminPassword by remember { mutableStateOf("") }
    var confirmAdminPassword by remember { mutableStateOf("") }
    var newAdmin2WayCode by remember { mutableStateOf("") }

    val techPhotoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val bmp = BitmapUtils.decodeSampledBitmapFromUri(context, uri, 900, 900)
                if (bmp != null) {
                    techPhotoBitmap = bmp
                } else {
                    Toast.makeText(context, "Could not load selected image.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val techCameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { saved ->
        val path = pendingTechCamPath
        pendingTechCamPath = null
        if (saved && !path.isNullOrBlank()) {
            scope.launch {
                val file = File(path)
                val bmp = BitmapUtils.decodeSampledBitmapFromFile(file, 900, 900)
                runCatching { file.delete() }
                if (bmp != null) {
                    techPhotoBitmap = bmp
                } else {
                    Toast.makeText(context, "Could not read camera photo.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun launchSafeTechCamera() {
        val launched = runCatching {
            val dir = File(context.cacheDir, "captured_photos").apply { mkdirs() }
            val photoFile = File.createTempFile("tech_", ".jpg", dir)
            pendingTechCamPath = photoFile.absolutePath
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", photoFile)
            techCameraLauncher.launch(uri)
            true
        }.getOrDefault(false)
        if (!launched) {
            pendingTechCamPath = null
            Toast.makeText(context, "Camera unavailable on this device. Please use Upload Pic.", Toast.LENGTH_SHORT).show()
        }
    }

    val techCameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            launchSafeTechCamera()
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

        // 0. Cloud Workspace & WhatsApp APK Sharing (Technician & Super Admin)
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.testTag("admin_cloud_apk_share_card")
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Cloud, contentDescription = null, tint = TealAccent)
                        Text("Cloud Sync & Share APK (WhatsApp)", style = MaterialTheme.typography.titleLarge)
                    }
                    Text(
                        text = "Share APK directly to WhatsApp for Technicians (limited access: billing & help only, no local data/share/backup) or Super Admins (view-only analytics + phone backup). They open straight to Login.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (cloudUser == null) {
                        Button(
                            onClick = onSignInGoogle,
                            enabled = !cloudBusy,
                            colors = ButtonDefaults.buttonColors(containerColor = BlueAction, contentColor = Color.White),
                            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("admin_google_signin_btn")
                        ) {
                            if (cloudBusy) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                            }
                            Icon(Icons.Default.Cloud, contentDescription = null, tint = Color.White)
                            Spacer(Modifier.width(6.dp))
                            Text("Sign in with Google for Cloud Sync", fontWeight = FontWeight.Bold, color = Color.White)
                        }
                        if (cloudAuthError.isNotBlank()) {
                            val clipboard = LocalClipboardManager.current
                            Surface(
                                color = RedDueBg,
                                border = BorderStroke(1.dp, RedDue),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth().testTag("admin_cloud_auth_error_banner")
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.Top,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = cloudAuthError,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = RedDueDark,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(
                                        onClick = {
                                            clipboard.setText(AnnotatedString(cloudAuthError))
                                            Toast.makeText(context, "Error copied", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.ContentCopy,
                                            contentDescription = "Copy error",
                                            tint = RedDueDark,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    } else if (cloudWorkspaceInfo.workspaceId.isBlank()) {
                        Button(
                            onClick = onEnsureCloudWorkspace,
                            enabled = !cloudBusy,
                            colors = ButtonDefaults.buttonColors(containerColor = TealAccent, contentColor = Color.White),
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) {
                            Text("Activate Admin Cloud Workspace", fontWeight = FontWeight.Bold, color = Color.White)
                        }
                        if (cloudAuthError.isNotBlank()) {
                            val clipboard = LocalClipboardManager.current
                            Surface(
                                color = RedDueBg,
                                border = BorderStroke(1.dp, RedDue),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth().testTag("admin_cloud_auth_error_banner")
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.Top,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = cloudAuthError,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = RedDueDark,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(
                                        onClick = {
                                            clipboard.setText(AnnotatedString(cloudAuthError))
                                            Toast.makeText(context, "Error copied", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.ContentCopy,
                                            contentDescription = "Copy error",
                                            tint = RedDueDark,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        Surface(
                            color = GreenPaidBg,
                            border = BorderStroke(1.dp, GreenPaid),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "Cloud Active (${cloudUser.email})",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = GreenPaid
                                )
                                Text(
                                    text = "Technician Invite Code: ${cloudWorkspaceInfo.techShareCode}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = NavyDeep
                                )
                                Text(
                                    text = "Super Admin Invite Code: ${cloudWorkspaceInfo.superShareCode}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = NavyDeep
                                )
                            }
                        }
                    }

                    val pendingUpdate = availableUpdate
                    if (pendingUpdate != null) {
                        Surface(
                            color = RedDueBg,
                            border = BorderStroke(1.2.dp, RedDue),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth().testTag("apk_share_update_required_banner")
                        ) {
                            Text(
                                text = "Update v${pendingUpdate.versionName} available! Admin must update first before sharing APK to Technician or Super Admin.",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = RedDueDark,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { onShareApkWhatsApp(ApkShareHelper.ROLE_TECHNICIAN) },
                            enabled = availableUpdate == null,
                            colors = ButtonDefaults.buttonColors(containerColor = GreenPaid, contentColor = Color.White),
                            modifier = Modifier.testTag("share_apk_technician_btn")
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Share Technician APK", fontWeight = FontWeight.Bold, color = Color.White)
                        }
                        Button(
                            onClick = { onShareApkWhatsApp(ApkShareHelper.ROLE_SUPER_ADMIN) },
                            enabled = availableUpdate == null,
                            colors = ButtonDefaults.buttonColors(containerColor = BlueAction, contentColor = Color.White),
                            modifier = Modifier.testTag("share_apk_super_admin_btn")
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Share Super Admin APK", fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }
        }

        // 0b. Team Notification Broadcast (Admin -> Technicians / All)
        item {
            var notifCategory by remember { mutableStateOf("GENERAL") }
            var notifTargetRole by remember { mutableStateOf("all") }
            var notifTitle by remember { mutableStateOf("") }
            var notifMessage by remember { mutableStateOf("") }
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.testTag("admin_send_notification_card")
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Notifications, contentDescription = null, tint = TealAccent)
                        Text("Send Real-Time Team Notification", style = MaterialTheme.typography.titleMedium)
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("all" to "All Team", "technician" to "Technicians", "super_admin" to "Super Admin").forEach { (rCode, rLabel) ->
                            FilterChip(
                                selected = notifTargetRole == rCode,
                                onClick = { notifTargetRole = rCode },
                                label = { Text(rLabel) }
                            )
                        }
                        listOf("GENERAL" to "General", "STOCK" to "Stock", "SERVICE_REQUEST" to "Service").forEach { (cCode, cLabel) ->
                            FilterChip(
                                selected = notifCategory == cCode,
                                onClick = { notifCategory = cCode },
                                label = { Text(cLabel) }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = notifTitle,
                        onValueChange = { notifTitle = it },
                        label = { Text("Notification Title") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus().testTag("admin_notif_title_input")
                    )
                    OutlinedTextField(
                        value = notifMessage,
                        onValueChange = { notifMessage = it },
                        label = { Text("Message to Team") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus().testTag("admin_notif_message_input")
                    )
                    Button(
                        onClick = {
                            clearFocusAndHideToolbar(focusManager, textToolbar)
                            onSendTeamNotification(notifCategory, notifTitle, notifMessage, notifTargetRole) {
                                notifTitle = ""
                                notifMessage = ""
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = TealAccent, contentColor = Color.White),
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("admin_send_notif_button")
                    ) {
                        Icon(Icons.Default.Send, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Send Alert Notification", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }

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
                        value = techEmail,
                        onValueChange = { techEmail = it },
                        label = { Text("Technician Email (OTP & Cloud Login)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus().testTag("new_tech_email")
                    )
                    val techOtpVerified = otpState.otpVerified && otpState.email.equals(techEmail.trim(), ignoreCase = true)
                    Surface(
                        color = if (techOtpVerified) GreenPaidBg else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        border = BorderStroke(1.dp, if (techOtpVerified) GreenPaid else MaterialTheme.colorScheme.outline),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (techOtpVerified) "Technician Email Verified" else "Verify Technician Email (OTP)",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (techOtpVerified) GreenPaid else MaterialTheme.colorScheme.onSurface
                                )
                                OutlinedButton(
                                    onClick = { onRequestOtp(techEmail, "TECH_VERIFY") },
                                    enabled = !otpState.busy && AuthPolicy.isValidEmail(techEmail.trim()),
                                    modifier = Modifier.testTag("new_tech_send_otp_button")
                                ) {
                                    Text(if (otpState.challengeId.isNotBlank()) "Resend OTP" else "Send OTP", fontWeight = FontWeight.Bold)
                                }
                            }
                            if (!techOtpVerified && otpState.challengeId.isNotBlank() && otpState.purpose == "TECH_VERIFY") {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    OutlinedTextField(
                                        value = techOtpInput,
                                        onValueChange = { techOtpInput = it.filter { ch -> ch.isDigit() }.take(6) },
                                        label = { Text("Enter Tech OTP") },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f).testTag("new_tech_otp_input")
                                    )
                                    Button(
                                        onClick = { onVerifyOtp(techOtpInput) {} },
                                        enabled = !otpState.busy && techOtpInput.length >= 4,
                                        colors = ButtonDefaults.buttonColors(containerColor = TealAccent, contentColor = Color.White),
                                        modifier = Modifier.height(50.dp).testTag("new_tech_verify_otp_button")
                                    ) {
                                        Text("Verify", color = Color.White, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                    OutlinedTextField(
                        value = techUsername,
                        onValueChange = { techUsername = it },
                        label = { Text("Technician Username (optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus().testTag("new_tech_username")
                    )
                    OutlinedTextField(
                        value = techPassword,
                        onValueChange = { techPassword = it },
                        label = { Text("Technician Initial Strong Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus().testTag("new_tech_password")
                    )
                    PasswordStrengthChecklist(password = techPassword)
                    // Mandatory Technician Profile Photo Picker
                    Surface(
                        color = if (techPhotoBitmap != null) GreenPaidBg else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
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
                                    Icon(Icons.Default.Person, contentDescription = "Photo required", tint = NavyDeep, modifier = Modifier.size(30.dp))
                                }
                            }
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = if (techPhotoBitmap != null) "Technician Photo Ready" else "Technician Photo (Required)",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = if (techPhotoBitmap != null) GreenPaid else RedDue,
                                    modifier = Modifier.testTag("new_tech_photo_status")
                                )
                                val picTint = if (techPhotoBitmap != null) NavyDeep else MaterialTheme.colorScheme.onSurface
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            clearFocusAndHideToolbar(focusManager, textToolbar)
                                            runCatching {
                                                techPhotoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                            }.onFailure {
                                                Toast.makeText(context, "Unable to open photo picker.", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        border = BorderStroke(1.dp, if (techPhotoBitmap != null) NavyDeep else MaterialTheme.colorScheme.outline),
                                        modifier = Modifier.testTag("new_tech_pick_photo_button")
                                    ) {
                                        Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp), tint = picTint)
                                        Spacer(Modifier.width(4.dp))
                                        Text("Upload Pic", color = picTint, fontWeight = FontWeight.Bold)
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            clearFocusAndHideToolbar(focusManager, textToolbar)
                                            val granted = ContextCompat.checkSelfPermission(
                                                context,
                                                Manifest.permission.CAMERA
                                            ) == PackageManager.PERMISSION_GRANTED
                                            if (granted) launchSafeTechCamera()
                                            else techCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                        },
                                        border = BorderStroke(1.dp, if (techPhotoBitmap != null) NavyDeep else MaterialTheme.colorScheme.outline),
                                        modifier = Modifier.testTag("new_tech_camera_photo_button")
                                    ) {
                                        Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp), tint = picTint)
                                        Spacer(Modifier.width(4.dp))
                                        Text("Take Pic", color = picTint, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }

                    Button(
                        onClick = {
                            clearFocusAndHideToolbar(focusManager, textToolbar)
                            val otpReady = otpState.otpVerified && otpState.email.equals(techEmail.trim(), ignoreCase = true)
                            val onDoneClear: () -> Unit = {
                                techName = ""
                                techPhone = ""
                                techEmail = ""
                                techOtpInput = ""
                                techUsername = ""
                                techPassword = ""
                                techPhotoBitmap = null
                            }
                            if (otpReady) {
                                onCreateCloudTechnicianOtp(
                                    techName,
                                    techPhone,
                                    techEmail,
                                    techUsername,
                                    techPassword,
                                    techPhotoBitmap,
                                    otpState.challengeId,
                                    otpState.verificationToken,
                                    onDoneClear
                                )
                            } else {
                                onCreateCloudTechnician(
                                    techName,
                                    techPhone,
                                    techEmail,
                                    techUsername,
                                    techPassword,
                                    techPhotoBitmap,
                                    onDoneClear
                                )
                            }
                        },
                        enabled = !cloudTechBusy,
                        colors = ButtonDefaults.buttonColors(containerColor = BlueAction, contentColor = Color.White),
                        modifier = Modifier.fillMaxWidth().height(50.dp).testTag("create_cloud_technician_button")
                    ) {
                        if (cloudTechBusy) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = if (cloudTechBusy) "Creating Firebase Account..." else "Create Technician Account (Firebase)",
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Firebase Technician accounts use Email/Password via Cloud Function. Admin Google session (${AuthPolicy.PRIMARY_ADMIN_EMAIL}) stays signed in, passwords are never saved to Firestore, and Technicians must set a new password on first login.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(10.dp)
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            clearFocusAndHideToolbar(focusManager, textToolbar)
                            onAddTechnician(techName, techPhone, techUsername, techPassword, techPhotoBitmap) {
                                techName = ""
                                techPhone = ""
                                techEmail = ""
                                techUsername = ""
                                techPassword = ""
                                techPhotoBitmap = null
                            }
                        },
                        border = BorderStroke(1.2.dp, GreenPaid),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = GreenPaid),
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("add_technician_button")
                    ) {
                        Text("Add Technician (Local / Invite Code)", fontWeight = FontWeight.Bold, color = GreenPaid)
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
                                            text = "${AuthPolicy.formatHandle(tech.username)} · ${tech.techId.ifBlank { "No ID" }} · ${tech.phone.ifBlank { "No phone" }} · Bills: ${stats?.billCount ?: 0}",
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

        // 1b. Admin ONLY — TECHNICIAN LOGIN CONTROL (Part 18)
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.2.dp, TealAccent.copy(alpha = 0.45f)),
                modifier = Modifier.testTag("technician_login_control_card")
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Shield, contentDescription = null, tint = TealAccent)
                        Text("TECHNICIAN LOGIN CONTROL", style = MaterialTheme.typography.titleLarge)
                    }
                    Text(
                        text = "Admin-only control: enable or disable any company Technician's login access instantly (blocks Password & Passkey sign-in).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (technicianAccounts.isEmpty()) {
                        Text(
                            text = "No Technician accounts to control yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        technicianAccounts.forEach { tech ->
                            val isEnabled = tech.loginEnabled
                            Surface(
                                color = if (isEnabled) GreenPaidBg else RedDueBg,
                                border = BorderStroke(1.dp, if (isEnabled) GreenPaid else RedDue),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth().testTag("tech_login_control_row_${tech.username}")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(
                                            text = "${tech.displayName.ifBlank { tech.username }} (${tech.techId.ifBlank { AuthPolicy.formatHandle(tech.username) }})",
                                            fontWeight = FontWeight.Bold,
                                            color = NavyDeep
                                        )
                                        Text(
                                            text = tech.email.ifBlank { AuthPolicy.formatHandle(tech.username) },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color(0xFF334155)
                                        )
                                        Text(
                                            text = if (isEnabled) "STATUS: ENABLED" else "STATUS: DISABLED",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Black,
                                            color = if (isEnabled) GreenPaid else RedDueDark
                                        )
                                    }
                                    Switch(
                                        checked = isEnabled,
                                        onCheckedChange = { checked -> onToggleTechnicianLogin(tech, checked) },
                                        modifier = Modifier.testTag("tech_login_toggle_${tech.username}")
                                    )
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

                            val folderTitleColor = if (hasCashDue) NavyDeep else MaterialTheme.colorScheme.onSurface
                            val folderSubColor = if (hasCashDue) Color(0xFF334155) else MaterialTheme.colorScheme.onSurfaceVariant
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
                                                text = "${summary.displayName} (${AuthPolicy.formatHandle(summary.username)}) · ${if (summary.role == "admin") "Admin" else "Technician"}",
                                                fontWeight = FontWeight.Bold,
                                                color = folderTitleColor
                                            )
                                            Text(
                                                text = "Bills: ${summary.billCount} · Quotations: ${summary.quotationCount} · Help Solved: ${summary.helpSolvedCount}/${summary.helpAcceptedCount}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = folderSubColor
                                            )
                                        }
                                        Icon(
                                            if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                            contentDescription = "Toggle Technician Folder",
                                            tint = folderTitleColor
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
                                                color = folderTitleColor,
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
                                                color = folderTitleColor
                                            )
                                            if (summary.latestReviewNote.isNotBlank()) {
                                                Text(
                                                    text = "Latest Customer Review: “${summary.latestReviewNote}”",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = folderSubColor
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
                                                    colors = ButtonDefaults.buttonColors(containerColor = GreenPaid, contentColor = Color.White),
                                                    modifier = Modifier.height(50.dp).testTag("settle_cash_${summary.username}")
                                                ) {
                                                    Icon(Icons.Default.Payments, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                                    Spacer(Modifier.width(4.dp))
                                                    Text("Clear Cash", color = Color.White, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                            summary.bills.take(5).forEach { b ->
                                                Text(
                                                    text = "• ${b.invoiceNo} (${b.customerName.ifBlank { "Walk-in" }}) — ₹${String.format(Locale.US, "%.0f", b.grandTotal)} [${b.paymentMode}]",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = folderSubColor
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
                        Icon(Icons.Default.AdminPanelSettings, contentDescription = null, tint = TealAccent)
                        Text("Super Admin Accounts (Read-Only Analytics)", style = MaterialTheme.typography.titleLarge)
                    }
                    Text(
                        text = "Super Admin can view live analytics, charts, bills, customers, products, and help desk, and export phone backups, but cannot modify or create bills.",
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
                            label = { Text("Strong Password") },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier.weight(1f).bringIntoViewOnFocus().testTag("new_super_password")
                        )
                    }
                    PasswordStrengthChecklist(password = superPassword)
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
                        colors = ButtonDefaults.buttonColors(containerColor = BlueAction, contentColor = Color.White),
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("add_super_admin_button")
                    ) {
                        Text("Create Super Admin Account", fontWeight = FontWeight.Bold, color = Color.White)
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
                                    Text("${AuthPolicy.formatHandle(sa.username)} · Super Admin (View-Only)", style = MaterialTheme.typography.bodySmall)
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
                            colors = ButtonDefaults.buttonColors(containerColor = TealAccent, contentColor = Color.White),
                            modifier = Modifier.weight(1.3f).height(48.dp).testTag("cms_save_voice_button")
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null, tint = Color.White)
                            Spacer(Modifier.width(4.dp))
                            Text("Save Voice & Note", fontWeight = FontWeight.Bold, color = Color.White)
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
                            colors = ButtonDefaults.buttonColors(containerColor = GreenPaid, contentColor = Color.White),
                            modifier = Modifier.testTag("save_upi_button")
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null, tint = Color.White)
                            Spacer(Modifier.width(4.dp))
                            Text("Save UPI ID", color = Color.White, fontWeight = FontWeight.Bold)
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

        // 6. Admin Security Credentials & 2-Way Verification Code
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.testTag("admin_verify_code_card")
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = TealAccent)
                        Text("Admin Password & 2-Way Code", style = MaterialTheme.typography.titleLarge)
                    }
                    Text(
                        text = "Official Admin ID is fixed as ${AuthPolicy.CANONICAL_ADMIN_USERNAME}. Update your strong login password and/or 2-Way Verification Code anytime.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = curAdminPassFor2Way,
                        onValueChange = { curAdminPassFor2Way = it },
                        label = { Text("Current Admin Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus().testTag("current_admin_pass_input")
                    )
                    OutlinedTextField(
                        value = newAdminPassword,
                        onValueChange = { newAdminPassword = it },
                        label = { Text("New Admin Password (Optional, Gmail-grade strong)") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus().testTag("new_admin_password_change_input")
                    )
                    AnimatedVisibility(visible = newAdminPassword.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            PasswordStrengthChecklist(password = newAdminPassword)
                            OutlinedTextField(
                                value = confirmAdminPassword,
                                onValueChange = { confirmAdminPassword = it },
                                label = { Text("Confirm New Admin Password") },
                                visualTransformation = PasswordVisualTransformation(),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus().testTag("confirm_admin_password_change_input")
                            )
                        }
                    }
                    OutlinedTextField(
                        value = newAdmin2WayCode,
                        onValueChange = { newAdmin2WayCode = it },
                        label = { Text("New 2-Way Verification Code (min 4 chars)") },
                        leadingIcon = { Icon(Icons.Default.Key, contentDescription = null) },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus().testTag("new_admin_verify_code_input")
                    )
                    Button(
                        onClick = {
                            clearFocusAndHideToolbar(focusManager, textToolbar)
                            if (newAdminPassword.isNotEmpty() || confirmAdminPassword.isNotEmpty()) {
                                onUpdateAdminCredentials(
                                    curAdminPassFor2Way,
                                    newAdminPassword,
                                    confirmAdminPassword,
                                    newAdmin2WayCode
                                ) {
                                    curAdminPassFor2Way = ""
                                    newAdminPassword = ""
                                    confirmAdminPassword = ""
                                    newAdmin2WayCode = ""
                                }
                            } else {
                                onUpdateAdminVerifyCode(curAdminPassFor2Way, newAdmin2WayCode) {
                                    curAdminPassFor2Way = ""
                                    newAdmin2WayCode = ""
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = BlueAction, contentColor = Color.White),
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("save_admin_verify_code_button")
                    ) {
                        Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = Color.White)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = if (newAdminPassword.isNotEmpty()) "Save Password & 2-Way Code" else "Save Admin Verification Code",
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }

        // Business Expenses Manager
        item {
            var expTitle by remember { mutableStateOf("") }
            var expCategory by remember { mutableStateOf("Travel / Fuel") }
            var expAmount by remember { mutableStateOf("") }
            var expNote by remember { mutableStateOf("") }
            val totalExp = expenses.sumOf { it.amount }
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.testTag("admin_expenses_card")
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.ReceiptLong, contentDescription = null, tint = TealAccent)
                            Text("Business Expenses", style = MaterialTheme.typography.titleMedium)
                        }
                        Text(
                            text = "Total: ₹${String.format(Locale.US, "%.2f", totalExp)}",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Black,
                            color = RedDue
                        )
                    }
                    OutlinedTextField(
                        value = expTitle,
                        onValueChange = { expTitle = it },
                        label = { Text("Expense Title (e.g. Fuel, Cable Purchase)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus().testTag("new_expense_title")
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = expCategory,
                            onValueChange = { expCategory = it },
                            label = { Text("Category") },
                            singleLine = true,
                            modifier = Modifier.weight(1f).bringIntoViewOnFocus().testTag("new_expense_category")
                        )
                        OutlinedTextField(
                            value = expAmount,
                            onValueChange = { expAmount = it },
                            label = { Text("Amount (₹)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f).bringIntoViewOnFocus().testTag("new_expense_amount")
                        )
                    }
                    OutlinedTextField(
                        value = expNote,
                        onValueChange = { expNote = it },
                        label = { Text("Note (Optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus()
                    )
                    Button(
                        onClick = {
                            clearFocusAndHideToolbar(focusManager, textToolbar)
                            onSaveExpense(expTitle, expCategory, expAmount, expNote) {
                                expTitle = ""
                                expAmount = ""
                                expNote = ""
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = GreenPaid, contentColor = Color.White),
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("add_expense_button")
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, tint = Color.White)
                        Spacer(Modifier.width(6.dp))
                        Text("Record Business Expense", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                    if (expenses.isNotEmpty()) {
                        HorizontalDivider()
                        expenses.take(15).forEach { exp ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(exp.title, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                    Text(
                                        text = "${exp.category} · ${exp.date} · @${exp.recordedBy}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "₹${String.format(Locale.US, "%.2f", exp.amount)}",
                                        fontWeight = FontWeight.Black,
                                        color = RedDue
                                    )
                                    IconButton(
                                        onClick = { onDeleteExpense(exp.id) },
                                        modifier = Modifier.testTag("delete_expense_${exp.id}")
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Move expense to trash", tint = RedDue)
                                    }
                                }
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }
        }

        // Trash & 24-Hour Recovery
        item {
            val trashedActive = remember(trashItems) {
                trashItems.filter { it.status == "TRASHED" }
            }
            val nowMs = System.currentTimeMillis()
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.testTag("admin_trash_section")
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.RestoreFromTrash, contentDescription = null, tint = TealAccent)
                            Text("Trash & 24-Hour Recovery (${trashedActive.size})", style = MaterialTheme.typography.titleMedium)
                        }
                        if (trashedActive.isNotEmpty()) {
                            TextButton(onClick = onPurgeExpiredTrash) {
                                Text("Clean Expired", color = RedDue, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Text(
                        text = "Deleted records stay recoverable here for 24 hours. After 24 hours they are permanently removed and logged.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (trashedActive.isEmpty()) {
                        Text(
                            text = "Trash is empty.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        trashedActive.forEach { tr ->
                            val remMs = (tr.expireAt - nowMs).coerceAtLeast(0L)
                            val remHrs = remMs / 3_600_000L
                            val remMins = (remMs % 3_600_000L) / 60_000L
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(
                                            text = "[${tr.entityType}] ${tr.title}",
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = tr.summary,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = if (remMs > 0L) {
                                                "Auto-deletes in ${remHrs}h ${remMins}m · Deleted by @${tr.deletedBy}"
                                            } else {
                                                "24h window expired · Pending purge"
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = RedDue,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    Button(
                                        onClick = { onRestoreTrash(tr.id) },
                                        colors = ButtonDefaults.buttonColors(containerColor = TealAccent, contentColor = Color.White),
                                        modifier = Modifier.testTag("restore_trash_${tr.id}")
                                    ) {
                                        Text("Restore", fontWeight = FontWeight.Bold, color = Color.White)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Permanent Audit Log
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.testTag("admin_audit_log_card")
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.History, contentDescription = null, tint = BlueAction)
                        Text("Security & Deletion Audit Log (${auditLogs.size})", style = MaterialTheme.typography.titleMedium)
                    }
                    if (auditLogs.isEmpty()) {
                        Text(
                            text = "No audit events recorded yet.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        val fmt = remember { java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US) }
                        auditLogs.take(25).forEach { log ->
                            val actionColor = when (log.action) {
                                "RESTORE", "EXPENSE_ADD" -> GreenPaid
                                "AUTO_PURGE_24H", "DELETE" -> RedDue
                                else -> BlueAction
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${log.action} · ${log.entityType}: ${log.title}",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = actionColor
                                    )
                                    Text(
                                        text = "${log.details} · by @${log.actorUsername} (${log.actorRole})",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    text = fmt.format(java.util.Date(log.createdAt)),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            HorizontalDivider()
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

    val createdPasskeyInfo = lastCreatedTechPasskey
    if (createdPasskeyInfo != null) {
        val clipboard = LocalClipboardManager.current
        val shareText = buildString {
            appendLine("${AppIdentity.OFFICIAL_APP_NAME} Technician Account Ready")
            appendLine("Name: ${createdPasskeyInfo.name}")
            if (createdPasskeyInfo.techId.isNotBlank()) appendLine("Tech ID: ${createdPasskeyInfo.techId}")
            appendLine("Email: ${createdPasskeyInfo.email}")
            appendLine("Username: @${createdPasskeyInfo.username}")
            appendLine("Activation Passkey: ${createdPasskeyInfo.activationPasskey}")
            append("Open the app, choose TECHNICIAN LOGIN -> First-Time Activate, enter your Email & Activation Passkey, then complete setup.")
        }
        AlertDialog(
            onDismissRequest = onDismissCreatedTechPasskey,
            modifier = Modifier.testTag("tech_passkey_dialog"),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Key, contentDescription = null, tint = GoldAccent)
                    Text("Technician Activation Passkey", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Share this one-time Activation Passkey with ${createdPasskeyInfo.name}. Required when opening the Technician APK for the first time:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Surface(
                        color = GreenPaidBg,
                        border = BorderStroke(1.5.dp, GreenPaid),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Email: ${createdPasskeyInfo.email}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                            Text("Username: @${createdPasskeyInfo.username}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                            Text(
                                text = createdPasskeyInfo.activationPasskey.ifBlank { "Generated" },
                                style = MaterialTheme.typography.headlineSmall,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Black,
                                color = NavyDeep,
                                modifier = Modifier.testTag("tech_passkey_value")
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = {
                                clipboard.setText(AnnotatedString(shareText))
                                Toast.makeText(context, "Passkey copied!", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f).testTag("copy_tech_passkey_button")
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Copy")
                        }
                        Button(
                            onClick = {
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, shareText)
                                    setPackage("com.whatsapp")
                                }
                                runCatching { context.startActivity(intent) }.onFailure {
                                    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, shareText)
                                    }, "Share Technician Passkey"))
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = GreenPaid, contentColor = Color.White),
                            modifier = Modifier.weight(1f).testTag("share_tech_passkey_whatsapp_button")
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("WhatsApp", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = onDismissCreatedTechPasskey) {
                    Text("Done")
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
    val scope = rememberCoroutineScope()
    var name by remember(account.username) { mutableStateOf(account.displayName) }
    var phone by remember(account.username) { mutableStateOf(account.phone) }
    var username by remember(account.username) { mutableStateOf(account.username) }
    var newPassword by remember(account.username) { mutableStateOf("") }
    var newPhotoBitmap by remember(account.username) { mutableStateOf<Bitmap?>(null) }
    var pendingEditCamPath by rememberSaveable { mutableStateOf<String?>(null) }
    val existingPhotoBitmap = remember(account.photoBase64) {
        account.photoBase64.takeIf { it.isNotBlank() }?.let { InvoicePdfPrinter.decodeBase64Bitmap(it) }
    }
    val previewPhoto = newPhotoBitmap ?: existingPhotoBitmap

    val editPhotoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val bmp = BitmapUtils.decodeSampledBitmapFromUri(context, uri, 900, 900)
                if (bmp != null) {
                    newPhotoBitmap = bmp
                } else {
                    Toast.makeText(context, "Could not load selected image.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    val editCameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { saved ->
        val path = pendingEditCamPath
        pendingEditCamPath = null
        if (saved && !path.isNullOrBlank()) {
            scope.launch {
                val file = File(path)
                val bmp = BitmapUtils.decodeSampledBitmapFromFile(file, 900, 900)
                runCatching { file.delete() }
                if (bmp != null) {
                    newPhotoBitmap = bmp
                } else {
                    Toast.makeText(context, "Could not read camera photo.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    fun launchSafeEditCamera() {
        val launched = runCatching {
            val dir = File(context.cacheDir, "captured_photos").apply { mkdirs() }
            val photoFile = File.createTempFile("tech_edit_", ".jpg", dir)
            pendingEditCamPath = photoFile.absolutePath
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", photoFile)
            editCameraLauncher.launch(uri)
            true
        }.getOrDefault(false)
        if (!launched) {
            pendingEditCamPath = null
            Toast.makeText(context, "Camera unavailable on this device. Please use Change Pic.", Toast.LENGTH_SHORT).show()
        }
    }
    val editCameraPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) launchSafeEditCamera()
    }

    val focusManager = LocalFocusManager.current
    val textToolbar = LocalTextToolbar.current

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Edit, contentDescription = null, tint = TealAccent) },
        title = { Text("Edit Technician (${AuthPolicy.formatHandle(account.username)})") },
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
                                Icon(Icons.Default.Person, contentDescription = null, tint = NavyDeep)
                            }
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(
                                onClick = {
                                    runCatching {
                                        editPhotoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                    }
                                },
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                modifier = Modifier.testTag("edit_tech_pick_photo_button")
                            ) {
                                Text("Change Pic", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                            }
                            OutlinedButton(
                                onClick = {
                                    val granted = ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.CAMERA
                                    ) == PackageManager.PERMISSION_GRANTED
                                    if (granted) launchSafeEditCamera()
                                    else editCameraPermLauncher.launch(Manifest.permission.CAMERA)
                                },
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                            ) {
                                Text("Camera", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
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
                        label = { Text("New Strong Password (optional)") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus().testTag("edit_tech_password")
                    )
                    AnimatedVisibility(visible = newPassword.isNotEmpty()) {
                        PasswordStrengthChecklist(password = newPassword)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    clearFocusAndHideToolbar(focusManager, textToolbar)
                    onSave(username, name, phone, newPassword, newPhotoBitmap)
                },
                colors = ButtonDefaults.buttonColors(containerColor = GreenPaid, contentColor = Color.White),
                modifier = Modifier.testTag("save_edit_tech_button")
            ) {
                Text("Save Changes", fontWeight = FontWeight.Bold, color = Color.White)
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
    passkeyBusy: Boolean = false,
    onToggleDeviceAuth: (Boolean) -> Unit,
    onSaveSettings: (CompanySettings) -> Unit,
    onPickCompanyLogoBitmap: (Bitmap?) -> Unit,
    onPickFallbackQrBitmap: (Bitmap) -> Unit,
    onPickAuthorisedSignatureBitmap: (Bitmap?) -> Unit = {},
    onRegisterPasskey: () -> Unit = {},
    onExportBackup: suspend () -> String,
    onImportBackup: (String) -> Unit,
    onBackupExported: () -> Unit = {},
    onCheckForUpdates: () -> Unit = {}
) {
    val canEditSettings = user.isAdmin && !user.isSuperAdmin
    val canExportPhoneBackup = user.isAdmin || user.isSuperAdmin
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val textToolbar = LocalTextToolbar.current

    val lockedCompanyName = AppIdentity.OFFICIAL_COMPANY_NAME
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
            scope.launch {
                val bmp = BitmapUtils.decodeSampledBitmapFromUri(context, uri, 900, 900)
                if (bmp != null) onPickCompanyLogoBitmap(bmp)
                else Toast.makeText(context, "Could not decode selected logo image.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null && canEditSettings) {
            scope.launch {
                val bmp = BitmapUtils.decodeSampledBitmapFromUri(context, uri, 900, 900)
                if (bmp != null) onPickFallbackQrBitmap(bmp)
                else Toast.makeText(context, "Could not decode selected QR image.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val authSignaturePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null && canEditSettings) {
            scope.launch {
                val bmp = BitmapUtils.decodeSampledBitmapFromUri(context, uri, 900, 900)
                if (bmp != null) onPickAuthorisedSignatureBitmap(bmp)
                else Toast.makeText(context, "Could not decode signature image.", Toast.LENGTH_SHORT).show()
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
        if (uri != null && canExportPhoneBackup) {
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

    fun launchDirectPhoneBackupExport() {
        clearFocusAndHideToolbar(focusManager, textToolbar)
        scope.launch {
            val json = onExportBackup()
            when (val outcome = BackupFileHelper.saveBackupDirectlyToPhoneDownloads(context, json)) {
                is BackupSaveOutcome.SavedToPhone -> {
                    Toast.makeText(context, "Saved to ${outcome.displayPath}", Toast.LENGTH_LONG).show()
                    onBackupExported()
                }
                is BackupSaveOutcome.LaunchPickerFallback -> {
                    val launched = runCatching {
                        exportDocLauncher.launch(outcome.suggestedFileName)
                        true
                    }.getOrDefault(false)
                    if (!launched) {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, outcome.suggestedFileName)
                            putExtra(Intent.EXTRA_TEXT, json)
                        }
                        runCatching {
                            context.startActivity(Intent.createChooser(shareIntent, "Save / Share Backup JSON"))
                        }.onFailure {
                            Toast.makeText(context, "Unable to open file exporter.", Toast.LENGTH_LONG).show()
                        }
                    }
                }
                is BackupSaveOutcome.Error -> {
                    Toast.makeText(context, outcome.message, Toast.LENGTH_LONG).show()
                }
            }
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
                                border = BorderStroke(1.dp, NavyDeep),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = NavyDeep),
                                modifier = Modifier.testTag("test_device_auth_button")
                            ) {
                                Icon(Icons.Default.Shield, contentDescription = null, modifier = Modifier.size(18.dp), tint = NavyDeep)
                                Spacer(Modifier.width(6.dp))
                                Text("Test Device Lock Prompt", color = NavyDeep, fontWeight = FontWeight.Bold)
                            }
                            if (testLockResult.isNotBlank()) {
                                Text(testLockResult, style = MaterialTheme.typography.bodySmall, color = NavyDeep)
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
                            value = lockedCompanyName,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Company Name (Permanently Locked)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("settings_company_name")
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
                                        companyName = AppIdentity.OFFICIAL_COMPANY_NAME,
                                        mobile = mobile,
                                        email = email,
                                        address = address
                                    )
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = TealAccent, contentColor = Color.White),
                            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("save_settings_button")
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null, tint = Color.White)
                            Spacer(Modifier.width(6.dp))
                            Text("Save Company Settings", fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    } else {
                        Text(
                            text = "View company contact details. Technicians have no backup access; Admin & Super Admin manage backups below.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text("Company: ${AppIdentity.OFFICIAL_COMPANY_NAME}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text("Mobile / WhatsApp: ${settings.mobile}", style = MaterialTheme.typography.bodyMedium)
                        Text("Email: ${settings.email}", style = MaterialTheme.typography.bodyMedium)
                        Text("Address: ${settings.address}", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        // FIDO2 Passkey Registration Card (for all signed-in roles)
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.testTag("settings_passkey_card")
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Fingerprint, contentDescription = null, tint = NavyDeep)
                        Text("FIDO2 Passkey Security", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        text = "Register a server-verified Android Credential Manager Passkey (biometric / device lock) for passwordless login.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = onRegisterPasskey,
                        enabled = !passkeyBusy,
                        colors = ButtonDefaults.buttonColors(containerColor = NavyDeep, contentColor = Color.White),
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("register_passkey_button")
                    ) {
                        if (passkeyBusy) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                        }
                        Icon(Icons.Default.Fingerprint, contentDescription = null, tint = Color.White)
                        Spacer(Modifier.width(6.dp))
                        Text(if (passkeyBusy) "Registering Passkey..." else "Register FIDO2 Passkey", fontWeight = FontWeight.Bold, color = Color.White)
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
                            text = "Upload a custom logo photo or use the built-in TS Shield emblem across header, A4 Print, PDF, and PNG bills.",
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
                                        runCatching {
                                            logoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = TealAccent, contentColor = Color.White),
                                    modifier = Modifier.testTag("upload_company_logo_button")
                                ) {
                                    Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = Color.White)
                                    Spacer(Modifier.width(6.dp))
                                    Text("Change Logo", color = Color.White, fontWeight = FontWeight.Bold)
                                }
                                if (settings.logoBase64.isNotBlank()) {
                                    OutlinedButton(
                                        onClick = { onPickCompanyLogoBitmap(null) },
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                        modifier = Modifier.testTag("reset_company_logo_button")
                                    ) {
                                        Text("Reset Default", color = MaterialTheme.colorScheme.onSurface)
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
                                    runCatching {
                                        authSignaturePicker.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = TealAccent, contentColor = Color.White),
                                modifier = Modifier.testTag("upload_auth_signature_button")
                            ) {
                                Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = Color.White)
                                Spacer(Modifier.width(6.dp))
                                Text("Upload Signatory", fontWeight = FontWeight.Bold, color = Color.White)
                            }
                            if (authSigPreview != null) {
                                OutlinedButton(
                                    onClick = {
                                        clearFocusAndHideToolbar(focusManager, textToolbar)
                                        onPickAuthorisedSignatureBitmap(null)
                                    },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = RedDueDark),
                                    modifier = Modifier.testTag("clear_auth_signature_button")
                                ) {
                                    Icon(Icons.Default.Clear, contentDescription = null, tint = RedDueDark)
                                    Spacer(Modifier.width(4.dp))
                                    Text("Clear Photo", color = RedDueDark)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Backup (Admin & Super Admin Phone Downloads) + Fallback QR & In-App App Updates
        if (canExportPhoneBackup) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.testTag("backup_updates_card")
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Backup & App Updates (v${BuildConfig.VERSION_NAME})", style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = if (canEditSettings) {
                                "Save backups directly into phone Downloads/Total_Solution or import from phone storage. Check GitHub Releases for APK updates."
                            } else {
                                "Super Admin can export a full backup directly to phone Downloads/Total_Solution and check for updates. Technicians have no backup access."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (canEditSettings) {
                                OutlinedButton(
                                    onClick = {
                                        clearFocusAndHideToolbar(focusManager, textToolbar)
                                        runCatching {
                                            photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                        }
                                    },
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                    modifier = Modifier.testTag("pick_qr_image_button")
                                ) {
                                    Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.width(6.dp))
                                    Text("Upload Fallback QR", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                                }
                            }
                            Button(
                                onClick = { launchDirectPhoneBackupExport() },
                                colors = ButtonDefaults.buttonColors(containerColor = GreenPaid, contentColor = Color.White),
                                modifier = Modifier.testTag("export_backup_button")
                            ) {
                                Icon(Icons.Default.CloudDownload, contentDescription = null, tint = Color.White)
                                Spacer(Modifier.width(6.dp))
                                Text("Save Backup to Phone", fontWeight = FontWeight.Bold, color = Color.White)
                            }
                            if (canEditSettings) {
                                OutlinedButton(
                                    onClick = { launchSafeSettingsBackupImport() },
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                    modifier = Modifier.testTag("import_backup_button")
                                ) {
                                    Icon(Icons.Default.CloudUpload, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.width(6.dp))
                                    Text("Import Backup", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                                }
                                OutlinedButton(
                                    onClick = {
                                        clearFocusAndHideToolbar(focusManager, textToolbar)
                                        showPasteBackupDialog = true
                                    },
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                    modifier = Modifier.testTag("paste_import_backup_button")
                                ) {
                                    Text("Paste JSON", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                                }
                            }
                            Button(
                                onClick = {
                                    clearFocusAndHideToolbar(focusManager, textToolbar)
                                    onCheckForUpdates()
                                },
                                enabled = !updateCheckBusy,
                                colors = ButtonDefaults.buttonColors(containerColor = BlueAction, contentColor = Color.White),
                                modifier = Modifier.testTag("online_update_button")
                            ) {
                                Icon(Icons.Default.SystemUpdate, contentDescription = null, tint = Color.White)
                                Spacer(Modifier.width(6.dp))
                                Text(if (updateCheckBusy) "Checking..." else "Check App Update", color = Color.White, fontWeight = FontWeight.Bold)
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
