package com.example.util

import android.Manifest
import android.app.DownloadManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class GitHubReleaseInfo(
    val tagName: String,
    val versionName: String,
    val releaseNotes: String,
    val htmlUrl: String,
    val apkDownloadUrl: String?
)

object AppUpdateChecker {
    private const val GITHUB_LATEST_RELEASE_API =
        "https://api.github.com/repos/rajrudra363-arch/Tse-billing/releases/latest"
    const val GITHUB_RELEASES_PAGE =
        "https://github.com/rajrudra363-arch/Tse-billing/releases"
    private const val CHANNEL_ID = "tse_updates"

    suspend fun checkLatestRelease(currentVersionName: String): GitHubReleaseInfo? =
        withContext(Dispatchers.IO) {
            runCatching {
                val conn = (URL(GITHUB_LATEST_RELEASE_API).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 6000
                    readTimeout = 6000
                    setRequestProperty("Accept", "application/vnd.github+json")
                    setRequestProperty("User-Agent", "TSE-Billing-Android/$currentVersionName")
                }
                val code = conn.responseCode
                if (code !in 200..299) return@runCatching null
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()
                parseReleaseJson(body, currentVersionName)
            }.getOrNull()
        }

    internal fun parseReleaseJson(rawJson: String, currentVersionName: String): GitHubReleaseInfo? {
        val obj = runCatching { JSONObject(rawJson) }.getOrNull() ?: return null
        val tag = obj.optString("tag_name", "").trim()
        val remoteVer = tag.removePrefix("v").removePrefix("V").trim()
        if (remoteVer.isBlank()) return null
        if (compareSemVer(remoteVer, currentVersionName) <= 0) return null

        val notes = obj.optString("body", "").trim().ifBlank { "New TS – TOTAL SOLUTION update available on GitHub." }
        val htmlUrl = obj.optString("html_url", GITHUB_RELEASES_PAGE).ifBlank { GITHUB_RELEASES_PAGE }
        val assets = obj.optJSONArray("assets")
        var apkUrl: String? = null
        if (assets != null) {
            for (i in 0 until assets.length()) {
                val a = assets.optJSONObject(i) ?: continue
                val name = a.optString("name", "")
                val dl = a.optString("browser_download_url", "")
                if (name.endsWith(".apk", ignoreCase = true) && dl.isNotBlank()) {
                    apkUrl = dl
                    break
                }
            }
        }
        return GitHubReleaseInfo(
            tagName = tag,
            versionName = remoteVer,
            releaseNotes = notes,
            htmlUrl = htmlUrl,
            apkDownloadUrl = apkUrl
        )
    }

    fun compareSemVer(aRaw: String, bRaw: String): Int {
        val aParts = aRaw.removePrefix("v").removePrefix("V")
            .substringBefore('-')
            .split('.')
            .map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
        val bParts = bRaw.removePrefix("v").removePrefix("V")
            .substringBefore('-')
            .split('.')
            .map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
        val maxLen = maxOf(aParts.size, bParts.size)
        for (i in 0 until maxLen) {
            val av = aParts.getOrElse(i) { 0 }
            val bv = bParts.getOrElse(i) { 0 }
            if (av != bv) return av.compareTo(bv)
        }
        return 0
    }

    fun postUpdateNotification(context: Context, release: GitHubReleaseInfo) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                CHANNEL_ID,
                "App Updates",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Alerts when a new TS – TOTAL SOLUTION release is available on GitHub"
            }
            nm.createNotificationChannel(ch)
        }
        val targetUrl = release.apkDownloadUrl ?: release.htmlUrl
        val openIntent = Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val pending = PendingIntent.getActivity(
            context,
            release.versionName.hashCode(),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("TS – TOTAL SOLUTION Update Available (v${release.versionName})")
            .setContentText("Tap to download update while keeping all bills, customers, and settings safe.")
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        nm.notify(7020, notif)
    }

    fun triggerUpdateDownloadAndInstall(context: Context, release: GitHubReleaseInfo): Boolean {
        val apkUrl = release.apkDownloadUrl
        if (!apkUrl.isNullOrBlank()) {
            val queued = runCatching {
                val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
                if (dm != null) {
                    val fileName = "TS-TOTAL-SOLUTION-v${release.versionName}.apk"
                    val req = DownloadManager.Request(Uri.parse(apkUrl)).apply {
                        setTitle("TS – TOTAL SOLUTION v${release.versionName} Update")
                        setDescription("Downloading latest APK release...")
                        setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                        setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                        setMimeType("application/vnd.android.package-archive")
                    }
                    dm.enqueue(req)
                    true
                } else {
                    false
                }
            }.getOrDefault(false)
            if (queued) return true
        }
        return runCatching {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(apkUrl ?: release.htmlUrl)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }
}
