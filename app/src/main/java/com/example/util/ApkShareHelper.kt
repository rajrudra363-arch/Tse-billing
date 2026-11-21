package com.example.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object ApkShareHelper {
    const val ROLE_TECHNICIAN = "technician"
    const val ROLE_SUPER_ADMIN = "super_admin"

    fun buildInviteMessage(
        targetRole: String,
        shareCode: String,
        companyName: String,
        versionName: String = BuildConfig.VERSION_NAME
    ): String {
        val roleTitle = if (targetRole == ROLE_SUPER_ADMIN) "Super Admin (View-Only Analytics)" else "Technician (Field Billing)"
        val cleanCode = shareCode.ifBlank { "Ask Admin for Invite Code" }
        return buildString {
            appendLine("*$companyName — TSE Billing App (v$versionName)*")
            appendLine("Role Access: *$roleTitle*")
            appendLine()
            appendLine("Your Workspace Invite Code:")
            appendLine("*$cleanCode*")
            appendLine()
            appendLine("How to start:")
            appendLine("1. Install the attached APK file.")
            appendLine("2. Open the app -> Direct Login opens automatically.")
            appendLine("3. Sign in with Google, enter Invite Code *$cleanCode*, then log in with your Username & Password.")
            if (targetRole == ROLE_TECHNICIAN) {
                appendLine("Note: All bills & quotations you create save directly to Admin's cloud workspace.")
            } else {
                appendLine("Note: You can monitor all company analytics, bills, customers, and take phone backups.")
            }
            appendLine()
            append("Latest releases page: ${AppUpdateChecker.GITHUB_RELEASES_PAGE}")
        }
    }

    suspend fun copyInstalledApkForSharing(
        context: Context,
        targetRole: String,
        versionName: String = BuildConfig.VERSION_NAME
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val sourcePath = context.applicationInfo?.sourceDir
                ?.takeIf { it.isNotBlank() }
                ?: error("Installed APK path is unavailable.")
            val sourceFile = File(sourcePath)
            if (!sourceFile.exists() || !sourceFile.canRead()) {
                error("Cannot read installed APK package on this device.")
            }
            val dir = File(context.cacheDir, "apks").apply { mkdirs() }
            dir.listFiles()?.forEach { old ->
                if (old.isFile && old.name.endsWith(".apk")) {
                    old.delete()
                }
            }
            val roleSuffix = if (targetRole == ROLE_SUPER_ADMIN) "SuperAdmin" else "Technician"
            val destFile = File(dir, "TSE-Billing-v${versionName}-$roleSuffix.apk")
            sourceFile.inputStream().use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output, bufferSize = 64 * 1024)
                }
            }
            destFile
        }
    }

    fun getFileProviderUri(context: Context, apkFile: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apkFile)

    fun buildWhatsAppShareIntent(
        context: Context,
        apkUri: Uri?,
        messageText: String
    ): Intent {
        val baseIntent = Intent(Intent.ACTION_SEND).apply {
            if (apkUri != null) {
                type = "application/vnd.android.package-archive"
                putExtra(Intent.EXTRA_STREAM, apkUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } else {
                type = "text/plain"
            }
            putExtra(Intent.EXTRA_TEXT, messageText)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val waPkg = findInstalledWhatsAppPackage(context)
        if (waPkg != null) {
            val targeted = Intent(baseIntent).apply { setPackage(waPkg) }
            if (targeted.resolveActivity(context.packageManager) != null) {
                return targeted
            }
        }
        return Intent.createChooser(baseIntent, "Share TSE Billing APK via WhatsApp").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    suspend fun shareLatestApkOnWhatsApp(
        context: Context,
        targetRole: String,
        shareCode: String,
        companyName: String
    ): Result<Boolean> {
        val message = buildInviteMessage(
            targetRole = targetRole,
            shareCode = shareCode,
            companyName = companyName
        )
        val apkFile = copyInstalledApkForSharing(context, targetRole).getOrNull()
        val uri = apkFile?.let { runCatching { getFileProviderUri(context, it) }.getOrNull() }
        val intent = buildWhatsAppShareIntent(context, uri, message)
        return runCatching {
            context.startActivity(intent)
            apkFile != null && uri != null
        }
    }

    private fun findInstalledWhatsAppPackage(context: Context): String? {
        val candidates = listOf("com.whatsapp", "com.whatsapp.w4b")
        return candidates.firstOrNull { pkg -> isPackageInstalled(context.packageManager, pkg) }
    }

    private fun isPackageInstalled(pm: PackageManager, packageName: String): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(packageName, 0)
            }
            true
        } catch (_: Throwable) {
            false
        }
    }
}
