package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.theme.GreenPaid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal suspend fun readBackupTextFromUri(context: Context, uri: Uri): Result<String> =
    withContext(Dispatchers.IO) {
        try {
            val raw = context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use {
                it.readText()
            }
            if (raw.isNullOrBlank()) {
                Result.failure(IllegalArgumentException("Selected backup file is empty or unreadable."))
            } else {
                Result.success(raw)
            }
        } catch (t: Throwable) {
            Result.failure(Exception(t.message ?: "Unable to read the selected backup file.", t))
        }
    }

internal fun canResolveDocumentPicker(context: Context, action: String, mimeType: String = "*/*"): Boolean {
    return try {
        val intent = Intent(action).apply {
            if (action == Intent.ACTION_OPEN_DOCUMENT || action == Intent.ACTION_GET_CONTENT) {
                addCategory(Intent.CATEGORY_OPENABLE)
            }
            type = mimeType
        }
        intent.resolveActivity(context.packageManager) != null
    } catch (_: Throwable) {
        false
    }
}

@Composable
fun BackupPasteImportDialog(
    onDismiss: () -> Unit,
    onRetryFilePicker: () -> Unit,
    onImportJsonText: (String) -> Unit
) {
    var jsonText by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Restore Backup (.json)",
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "If your phone's file manager could not open or list the .json file, paste the full backup JSON text below and tap Restore.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = jsonText,
                    onValueChange = {
                        jsonText = it
                        localError = ""
                    },
                    label = { Text("Backup JSON Content") },
                    placeholder = { Text("{ \"bills\": [...], \"accounts\": [...] }") },
                    minLines = 5,
                    maxLines = 8,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp)
                        .testTag("backup_paste_json_input")
                )
                if (localError.isNotBlank()) {
                    Text(
                        text = localError,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                OutlinedButton(
                    onClick = onRetryFilePicker,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Try File Picker Again")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val trimmed = jsonText.trim()
                    if (!trimmed.startsWith("{")) {
                        localError = "Please paste valid ${com.example.data.AppIdentity.OFFICIAL_APP_NAME} backup JSON starting with '{'."
                    } else {
                        onImportJsonText(trimmed)
                        onDismiss()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = GreenPaid, contentColor = Color.White),
                modifier = Modifier.testTag("backup_paste_confirm_button")
            ) {
                Text("Restore Backup", fontWeight = FontWeight.Bold, color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
