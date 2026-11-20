package com.example.ui.screens

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.data.CompanySettings
import com.example.data.UpiSettings
import com.example.data.UserAccountEntity
import com.example.data.UserSession
import com.example.ui.components.QrCodeImage
import com.example.ui.theme.AmberWarn
import com.example.ui.theme.GreenPaid
import com.example.ui.theme.RedDue
import com.example.ui.theme.TealAccent
import com.example.util.QrGenerator
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AdminPanelScreen(
    upiSettings: UpiSettings,
    accounts: List<UserAccountEntity>,
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

    val staffList = accounts.filter { it.role.equals("staff", ignoreCase = true) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp),
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
                        modifier = Modifier.fillMaxWidth().testTag("admin_upi_id_input")
                    )
                    OutlinedTextField(
                        value = payeeName,
                        onValueChange = { payeeName = it },
                        label = { Text("Account / Payee Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("admin_upi_name_input")
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { onSaveUpi(upiId, payeeName) },
                            colors = ButtonDefaults.buttonColors(containerColor = GreenPaid),
                            modifier = Modifier.testTag("save_upi_button")
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("Save UPI ID & Generate QR")
                        }
                        OutlinedButton(
                            onClick = {
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

        // 2. Change Final Bill Code
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
                        modifier = Modifier.fillMaxWidth().testTag("admin_new_final_code")
                    )
                    OutlinedTextField(
                        value = confirmFinalCode,
                        onValueChange = { confirmFinalCode = it },
                        label = { Text("Confirm New Code") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("admin_confirm_final_code")
                    )
                    Button(
                        onClick = {
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

        // 3. Staff Account Manager (Max 5)
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
                        modifier = Modifier.fillMaxWidth().testTag("new_staff_name")
                    )
                    OutlinedTextField(
                        value = staffUsername,
                        onValueChange = { staffUsername = it },
                        label = { Text("Staff Username (min 3 chars)") },
                        enabled = staffList.size < 5,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("new_staff_username")
                    )
                    OutlinedTextField(
                        value = staffPassword,
                        onValueChange = { staffPassword = it },
                        label = { Text("Staff Password (min 6 chars)") },
                        enabled = staffList.size < 5,
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("new_staff_password")
                    )
                    Button(
                        onClick = {
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
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(staff.displayName.ifBlank { staff.username }, fontWeight = FontWeight.Bold)
                                    Text("@${staff.username} · Staff", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    onPickFallbackQrBitmap: (android.graphics.Bitmap) -> Unit,
    onExportBackup: suspend () -> String,
    onImportBackup: (String) -> Unit
) {
    val canEditSettings = !user.isStaff && user.isAdmin
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var company by remember(settings.companyName) { mutableStateOf(settings.companyName) }
    var mobile by remember(settings.mobile) { mutableStateOf(settings.mobile) }
    var email by remember(settings.email) { mutableStateOf(settings.email) }
    var address by remember(settings.address) { mutableStateOf(settings.address) }

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

    val exportDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null && canEditSettings) {
            scope.launch {
                val json = onExportBackup()
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(json.toByteArray(Charsets.UTF_8))
                }
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
            .padding(horizontal = 12.dp),
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
                            modifier = Modifier.fillMaxWidth().testTag("settings_company_name")
                        )
                        OutlinedTextField(
                            value = mobile,
                            onValueChange = { mobile = it },
                            label = { Text("Mobile / WhatsApp") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("settings_mobile")
                        )
                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it },
                            label = { Text("Email") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("settings_email")
                        )
                        OutlinedTextField(
                            value = address,
                            onValueChange = { address = it },
                            label = { Text("Office Address") },
                            minLines = 2,
                            modifier = Modifier.fillMaxWidth().testTag("settings_address")
                        )
                        Button(
                            onClick = {
                                onSaveSettings(
                                    CompanySettings(
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
                        Text("Fallback QR & Data Backup", style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = "Agar UPI ID set nahi hai to static QR image gallery se pick kar sakte hain, aur JSON backup export/import kar sakte hain.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                },
                                modifier = Modifier.testTag("pick_qr_image_button")
                            ) {
                                Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("Upload Fallback QR")
                            }
                            Button(
                                onClick = { exportDocLauncher.launch("TSE-Billing-Backup.json") },
                                colors = ButtonDefaults.buttonColors(containerColor = GreenPaid),
                                modifier = Modifier.testTag("export_backup_button")
                            ) {
                                Icon(Icons.Default.CloudDownload, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("Export Backup")
                            }
                            OutlinedButton(
                                onClick = { importDocLauncher.launch(arrayOf("application/json", "*/*")) },
                                modifier = Modifier.testTag("import_backup_button")
                            ) {
                                Icon(Icons.Default.CloudUpload, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("Import Backup")
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(16.dp)) }
    }
}
