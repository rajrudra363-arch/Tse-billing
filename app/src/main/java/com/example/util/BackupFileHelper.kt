package com.example.util

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed interface BackupSaveOutcome {
    data class SavedToPhone(val displayPath: String, val fileName: String) : BackupSaveOutcome
    data class LaunchPickerFallback(val suggestedFileName: String) : BackupSaveOutcome
    data class Error(val message: String) : BackupSaveOutcome
}

object BackupFileHelper {
    fun buildTimestampedBackupFileName(nowMillis: Long = System.currentTimeMillis()): String {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date(nowMillis))
        return "TSE-Billing-Backup-$stamp.json"
    }

    suspend fun saveBackupDirectlyToPhoneDownloads(
        context: Context,
        jsonContent: String,
        fileName: String = buildTimestampedBackupFileName()
    ): BackupSaveOutcome = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return@withContext BackupSaveOutcome.LaunchPickerFallback(fileName)
        }
        try {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, "application/json")
                put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/TSE_Billing")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
            val uri: Uri = resolver.insert(collection, values)
                ?: return@withContext BackupSaveOutcome.LaunchPickerFallback(fileName)

            val wrote = runCatching {
                resolver.openOutputStream(uri)?.use { out ->
                    out.write(jsonContent.toByteArray(Charsets.UTF_8))
                    out.flush()
                } ?: error("Cannot open Downloads output stream")
            }
            if (wrote.isFailure) {
                runCatching { resolver.delete(uri, null, null) }
                return@withContext BackupSaveOutcome.LaunchPickerFallback(fileName)
            }

            val clearPending = ContentValues().apply {
                put(MediaStore.Downloads.IS_PENDING, 0)
            }
            runCatching { resolver.update(uri, clearPending, null, null) }

            BackupSaveOutcome.SavedToPhone(
                displayPath = "Downloads/TSE_Billing/$fileName",
                fileName = fileName
            )
        } catch (_: Throwable) {
            BackupSaveOutcome.LaunchPickerFallback(fileName)
        }
    }
}
