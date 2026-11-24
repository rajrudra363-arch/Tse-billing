package com.example.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.R

object AdminNotificationHelper {
    private const val CHANNEL_ID = "tse_admin_alerts"

    fun notifyTechnicianBillCreated(
        context: Context,
        invoiceNo: String,
        technicianUsername: String,
        customerName: String
    ) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                if (nm != null && nm.getNotificationChannel(CHANNEL_ID) == null) {
                    val channel = NotificationChannel(
                        CHANNEL_ID,
                        "TSE Admin Bill Alerts",
                        NotificationManager.IMPORTANCE_HIGH
                    ).apply {
                        description = "Alerts Admin when a Technician creates a bill to send to the customer."
                    }
                    nm.createNotificationChannel(channel)
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val granted = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
                if (!granted) return
            }
            val custLabel = customerName.ifBlank { "Customer" }
            notifySyncAlert(
                context = context,
                notificationId = invoiceNo.hashCode(),
                title = "New Bill $invoiceNo by @$technicianUsername",
                message = "Technician @$technicianUsername created bill $invoiceNo for $custLabel. Open Saved Bills and tap WhatsApp PDF to send it to the customer."
            )
        } catch (_: Throwable) {
            // Safe fallback: UI Snackbar still notifies Admin
        }
    }

    fun notifySyncAlert(
        context: Context,
        notificationId: Int,
        title: String,
        message: String
    ) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                if (nm != null && nm.getNotificationChannel(CHANNEL_ID) == null) {
                    val channel = NotificationChannel(
                        CHANNEL_ID,
                        "TSE Admin & Sync Alerts",
                        NotificationManager.IMPORTANCE_HIGH
                    ).apply {
                        description = "Live Firebase sync notifications for Billing, Payments, Stock, and Service Requests."
                    }
                    nm.createNotificationChannel(channel)
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val granted = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
                if (!granted) return
            }
            val notif = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher_round)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build()
            NotificationManagerCompat.from(context).notify(notificationId, notif)
        } catch (_: Throwable) {
            // Safe fallback: UI Snackbar still displays
        }
    }
}
