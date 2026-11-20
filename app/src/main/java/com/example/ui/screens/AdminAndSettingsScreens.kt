package com.example.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.data.CompanySettings
import com.example.data.UpiSettings
import com.example.data.UserAccountEntity
import com.example.data.UserSession
import com.example.ui.StaffPerformanceSummary
import com.example.ui.components.QrCodeImage
import com.example.ui.components.bringIntoViewOnFocus
import com.example.ui.components.clearFocusAndHideToolbar
import com.example.ui.theme.AmberWarn
import com.example.ui.theme.GoldAccent
import com.example.ui.theme.GreenPaid
import com.example.ui.theme.NavyPrimary
import com.example.ui.theme.RedDue
import com.example.ui.theme.TealAccent
import com.example.util.InvoicePdfPrinter
import com.example.util.QrGenerator
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AdminPanelScreen(
    upiSettings: UpiSettings,
    accounts: List<UserAccountEntity>,
    staffPerformance: List<StaffPerformanceSummary> = emptyList(),
    onSaveUpi: (upiId: String, payeeName: String) -> Unit,
    onClearUpi: () -> Unit,
    onChangeFinalCode: (newCode: String, confirmCode: String) -> Unit,
    onAddStaff: (name: String, username: String, password: String, onDone: () -> Unit) -> Unit,
    onRemoveStaff: (username: String) -> Unit
) {
    var upiId by remember(upiSettings.upiId) { mutableStateOf(upiSettings.upiId) }
    var payeeName by remember(upiSettings.payeeName) { mutableStateOf(upiSettings.payeeName) }

    var newFinalCode by remember { mutableStateOf("") }
    var confirmFinalCode by remember { mutableStateOf("") }

    var staffName by remember { mutableStateOf("") }
    var staffUsername by remember { mutableStateOf("") }
    var staffPassword by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current
    val textToolbar = LocalTextToolbar.current

    val staffList = accounts.filter { it.role.equals("staff", ignoreCase = true) }
    val perfByUsername = remember(staffPerformance) {
        staffPerformance.associateBy { it.username.lowercase(Locale.US) }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp)
            .testTag("admin_lazy_list"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Spacer(Modifier.height(4.dp)) }

        // 1. Online Payment UPI ID (Auto QR Generator)
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
                        text = "UPI ID daalte hi invoice par automatic Payment QR generate ho jayega.",
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
                            Text("Save UPI ID & Generate QR")
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

        // 2. Staff Bills & 5-Star Customer Review Report
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.testTag("staff_performance_card")
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Star, contentDescription = null, tint = GoldAccent)
                        Text("Staff Bills & 5-Star Performance", style = MaterialTheme.typography.titleLarge)
                    }
                    Text(
                        text = "Kis staff ne kitne bill banaye aur customer ne kitna 5-star review diya — sab yahan dikhega.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (staffPerformance.isEmpty()) {
                        Text(
                            text = "Abhi tak koi bill generated record nahi hai.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        staffPerformance.forEach { summary ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("staff_perf_${summary.username}")
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "${summary.displayName} (@${summary.username})",
                                            fontWeight = FontWeight.Bold,
                                            color = NavyPrimary
                                        )
                                        Text(
                                            text = "Bills: ${summary.billCount}",
                                            fontWeight = FontWeight.ExtraBold,
                                            color = TealAccent
                                        )
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "Total: ₹${String.format(Locale.US, "%.2f", summary.totalBilledAmount)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = GreenPaid,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        if (summary.totalDueAmount > 0.0) {
                                            Text(
                                                text = "Due: ₹${String.format(Locale.US, "%.2f", summary.totalDueAmount)}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = RedDue,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                    if (summary.ratedCount > 0) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                Icons.Default.Star,
                                                contentDescription = null,
                                                tint = GoldAccent,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(Modifier.width(4.dp))
                                            Text(
                                                text = "Rating: ${String.format(Locale.US, "%.1f", summary.averageRating)} / 5 (${summary.ratedCount} review${if (summary.ratedCount == 1) "" else "s"})",
                                                style = MaterialTheme.typography.labelLarge,
                                                color = NavyPrimary,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        if (summary.latestReviewNote.isNotBlank()) {
                                            Text(
                                                text = "Latest: \"${summary.latestReviewNote}\"",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    } else {
                                        Text(
                                            text = "No customer ratings yet",
                                            style = MaterialTheme.typography.bodySmall,
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

        // 3. Change Final Bill Code
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Key, contentDescription = null, tint = AmberWarn)
                        Text("Change Final Bill Code", style = MaterialTheme.typography.titleLarge)
                    }
                    OutlinedTextField(
                        value = newFinalCode,
                        onValueChange = { newFinalCode = it },
                        label = { Text("New Final Bill Code (min 4 chars)") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus().testTag("admin_new_final_code")
                    )
                    OutlinedTextField(
                        value = confirmFinalCode,
                        onValueChange = { confirmFinalCode = it },
                        label = { Text("Confirm New Code") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus().testTag("admin_confirm_final_code")
                    )
                    Button(
                        onClick = {
                            clearFocusAndHideToolbar(focusManager, textToolbar)
                            onChangeFinalCode(newFinalCode, confirmFinalCode)
                            newFinalCode = ""
                            confirmFinalCode = ""
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AmberWarn),
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("update_final_code_button")
                    ) {
                        Text("Update Final Bill Code", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // 4. Staff Account Manager (Max 5)
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
                            Text("Staff Accounts", style = MaterialTheme.typography.titleLarge)
                        }
                        Text("(${staffList.size}/5)", style = MaterialTheme.typography.titleMedium, color = TealAccent)
                    }

                    OutlinedTextField(
                        value = staffName,
                        onValueChange = { staffName = it },
                        label = { Text("Staff Full Name") },
                        enabled = staffList.size < 5,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus().testTag("new_staff_name")
                    )
                    OutlinedTextField(
                        value = staffUsername,
                        onValueChange = { staffUsername = it },
                        label = { Text("Staff Username (min 3 chars)") },
                        enabled = staffList.size < 5,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus().testTag("new_staff_username")
                    )
                    OutlinedTextField(
                        value = staffPassword,
                        onValueChange = { staffPassword = it },
                        label = { Text("Staff Password (min 6 chars)") },
                        enabled = staffList.size < 5,
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus().testTag("new_staff_password")
                    )
                    Button(
                        onClick = {
                            clearFocusAndHideToolbar(focusManager, textToolbar)
                            onAddStaff(staffName, staffUsername, staffPassword) {
                                staffName = ""
                                staffUsername = ""
                                staffPassword = ""
                            }
                        },
                        enabled = staffList.size < 5,
                        colors = ButtonDefaults.buttonColors(containerColor = GreenPaid),
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("add_staff_button")
                    ) {
                        Text("Add Staff", fontWeight = FontWeight.Bold)
                    }

                    HorizontalDivider()

                    if (staffList.isEmpty()) {
                        Text(
                            text = "Abhi koi staff account nahi hai.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        staffList.forEach { staff ->
                            val stats = perfByUsername[staff.username.lowercase(Locale.US)]
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(staff.displayName.ifBlank { staff.username }, fontWeight = FontWeight.Bold)
                                    Text(
                                        text = "@${staff.username} · Staff · Bills: ${stats?.billCount ?: 0}" +
                                            if ((stats?.ratedCount ?: 0) > 0) {
                                                " · ★ ${String.format(Locale.US, "%.1f", stats!!.averageRating)}/5"
                                            } else "",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Button(
                                    onClick = { onRemoveStaff(staff.username) },
                                    colors = ButtonDefaults.buttonColors(containerColor = RedDue)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Remove")
                                }
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(16.dp)) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    settings: CompanySettings,
    user: UserSession,
    onSaveSettings: (CompanySettings) -> Unit,
    onPickFallbackQrBitmap: (Bitmap) -> Unit,
    onPickAuthorisedSignatureBitmap: (Bitmap?) -> Unit = {},
    onExportBackup: suspend () -> String,
    onImportBackup: (String) -> Unit,
    onBackupExported: () -> Unit = {}
) {
    val canEditSettings = !user.isStaff && user.isAdmin
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val textToolbar = LocalTextToolbar.current
    val uriHandler = LocalUriHandler.current

    var company by remember(settings.companyName) { mutableStateOf(settings.companyName) }
    var mobile by remember(settings.mobile) { mutableStateOf(settings.mobile) }
    var email by remember(settings.email) { mutableStateOf(settings.email) }
    var address by remember(settings.address) { mutableStateOf(settings.address) }

    val authSigPreview = remember(settings.authorisedSignatureBase64) {
        InvoicePdfPrinter.decodeBase64Bitmap(settings.authorisedSignatureBase64)
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
                        val maxDim = 600f
                        val scale = minOf(maxDim / bmp.width.coerceAtLeast(1), maxDim / bmp.height.coerceAtLeast(1), 1f)
                        val scaled = if (scale < 1f) {
                            Bitmap.createScaledBitmap(
                                bmp,
                                (bmp.width * scale).toInt().coerceAtLeast(1),
                                (bmp.height * scale).toInt().coerceAtLeast(1),
                                true
                            )
                        } else bmp
                        onPickAuthorisedSignatureBitmap(scaled)
                    }
                }
            }
        }
    }

    val exportDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null && canEditSettings) {
            scope.launch {
                val json = onExportBackup()
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(json.toByteArray(Charsets.UTF_8))
                }
                onBackupExported()
            }
        }
    }

    val importDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null && canEditSettings) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }.getOrNull()?.let { text ->
                onImportBackup(text)
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp)
            .testTag("settings_lazy_list"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Spacer(Modifier.height(4.dp)) }

        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = if (canEditSettings) "Company Settings" else "Company Settings (Read-Only)",
                        style = MaterialTheme.typography.titleLarge
                    )
                    if (canEditSettings) {
                        Text(
                            text = "Company details update karke save karein; header, invoice aur WhatsApp QR par turant update ho jayega.",
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
                            Text("Save Settings", fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Text(
                            text = "Staff users cannot edit or save company settings.",
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
                        Text(
                            text = "Admin yahan Authorised Signatory / Stamp photo upload kar sakta hai. Ye har Print/PDF invoice ke Authorised Signatory ke upar print hoga.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
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
                                Text("Upload Authorised Signatory", fontWeight = FontWeight.Bold)
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

            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Full Data Backup, Fallback QR & Online Update", style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = "Sabhi Bills (ratings ke saath), Admin & Staff Passwords, Final Code, Customers, Help Desk, Products, Authorised Signatory aur UPI QR ek .json file me export/import karein taaki update par koi data loss na ho.",
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
                                    exportDocLauncher.launch("TSE-Billing-Backup.json")
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = GreenPaid),
                                modifier = Modifier.testTag("export_backup_button")
                            ) {
                                Icon(Icons.Default.CloudDownload, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("Export Backup")
                            }
                            OutlinedButton(
                                onClick = {
                                    clearFocusAndHideToolbar(focusManager, textToolbar)
                                    importDocLauncher.launch(arrayOf("application/json", "*/*"))
                                },
                                modifier = Modifier.testTag("import_backup_button")
                            ) {
                                Icon(Icons.Default.CloudUpload, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("Import Backup")
                            }
                            Button(
                                onClick = {
                                    clearFocusAndHideToolbar(focusManager, textToolbar)
                                    runCatching {
                                        uriHandler.openUri("https://github.com/rajrudra363-arch/Tse-billing/releases")
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary),
                                modifier = Modifier.testTag("online_update_button")
                            ) {
                                Icon(Icons.Default.SystemUpdate, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("Online Update (GitHub Releases)")
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Text(
                                    text = "Online App Update Steps (Zero Data Loss):",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = NavyPrimary
                                )
                                Text(
                                    text = "1. Pehle 'Export Backup' dabakar .json file save karein.",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    text = "2. GitHub Releases ya Actions se naya APK download karke direct install/update karein (app uninstall na karein).",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    text = "3. Agar naya phone ho ya fresh install ho, to Setup/Settings me 'Import Backup' se saare bills & passwords wapas laayein.",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(16.dp)) }
    }
}
