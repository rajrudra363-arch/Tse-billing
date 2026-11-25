package com.example.util

import android.content.ActivityNotFoundException
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.example.data.BillEntity
import com.example.data.CompanySettings
import com.example.data.UpiSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.Locale

sealed interface DownloadsSaveResult {
    data class Saved(val displayPath: String, val fileName: String) : DownloadsSaveResult
    data class NeedsPickerFallback(val suggestedFileName: String) : DownloadsSaveResult
}

object WhatsAppHelper {

    fun formatIndianPhoneForJid(rawPhone: String): String? {
        val digits = rawPhone.filter { it.isDigit() }
        if (digits.length < 7) return null
        return if (digits.length == 10) "91$digits" else digits
    }

    fun extractCustomerPhone(customerDetails: String, fallbackPhone: String = ""): String {
        val explicitDigits = fallbackPhone.filter { it.isDigit() }
        if (explicitDigits.length >= 7) return fallbackPhone
        val lineMatch = customerDetails.lines()
            .map { it.trim() }
            .firstOrNull { line ->
                val digits = line.count { it.isDigit() }
                digits in 7..15 && line.length <= 26
            }
        if (!lineMatch.isNullOrBlank()) return lineMatch
        val inlineMatch = Regex("(\\+?91[\\s-]?)?[6-9]\\d{9}").find(customerDetails)
        return inlineMatch?.value ?: ""
    }

    fun sharePdfDirectToCustomerWhatsApp(
        context: Context,
        payload: InvoicePrintPayload,
        customerPhone: String
    ): Boolean {
        return try {
            val dir = File(context.cacheDir, "pdfs").apply { mkdirs() }
            val safeNo = payload.invoiceNo.replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "Document" }
            val prefix = if (payload.documentType.name == "QUOTATION") "TSE_Quotation" else "TSE_Invoice"
            val outFile = File(dir, "${prefix}_$safeNo.pdf")
            FileOutputStream(outFile).use { out ->
                InvoicePdfPrinter.writePdfToStream(payload, out)
            }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", outFile)
            val phone = extractCustomerPhone(payload.customerDetails, customerPhone)
            val formattedJidPhone = formatIndianPhoneForJid(phone)
            val message = buildString {
                appendLine("*${payload.company.companyName}*")
                appendLine("${payload.documentType.label} No: *${payload.invoiceNo}*")
                appendLine("Customer: ${payload.customerName.ifBlank { "Customer" }}")
                appendLine("Sub Total: ₹${String.format(Locale.US, "%.2f", payload.subtotal)}")
                appendLine("Discount: ₹${String.format(Locale.US, "%.2f", payload.discount)}")
                appendLine("Grand Total: ₹${String.format(Locale.US, "%.2f", payload.grandTotal)}")
                if (payload.documentType.name != "QUOTATION") {
                    appendLine("Paid Amount: ₹${String.format(Locale.US, "%.2f", payload.paidAmount)}")
                    appendLine("Due Balance: ₹${String.format(Locale.US, "%.2f", payload.dueAmount)}")
                }
                append("Thank you for choosing ${payload.company.companyName}.")
            }

            for (pkg in listOf("com.whatsapp", "com.whatsapp.w4b")) {
                val waIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_TEXT, message)
                    if (!formattedJidPhone.isNullOrBlank()) {
                        putExtra("jid", "$formattedJidPhone@s.whatsapp.net")
                    }
                    setPackage(pkg)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                try {
                    context.startActivity(waIntent)
                    return true
                } catch (_: ActivityNotFoundException) {
                } catch (_: SecurityException) {
                }
            }

            val chooser = Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_TEXT, message)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                },
                "Send Bill PDF to Customer"
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
            true
        } catch (_: Throwable) {
            false
        }
    }

    fun renderDuePaymentQrCardBitmap(
        company: CompanySettings,
        upi: UpiSettings,
        bill: BillEntity
    ): Bitmap {
        val w = 900
        val h = 1120
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.parseColor("#F1F5F9"))

        val card = RectF(34f, 34f, w - 34f, h - 34f)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0B2238")
            style = Paint.Style.STROKE
            strokeWidth = 5f
        }
        canvas.drawRoundRect(card, 26f, 26f, fill)
        canvas.drawRoundRect(card, 26f, 26f, stroke)

        val header = RectF(34f, 34f, w - 34f, 200f)
        val hFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#0B2238") }
        canvas.drawRoundRect(header, 26f, 26f, hFill)
        canvas.drawRect(34f, 120f, w - 34f, 200f, hFill)
        val goldPaint = Paint().apply { color = Color.parseColor("#E4B44E"); strokeWidth = 6f }
        canvas.drawLine(34f, 200f, w - 34f, 200f, goldPaint)

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 34f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText(company.companyName.uppercase(Locale.ROOT), w / 2f, 102f, titlePaint)
        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#F4D47E")
            textSize = 24f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText("DUE BALANCE PAYMENT QR • ${bill.invoiceNo}", w / 2f, 154f, subPaint)

        val custPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0F172A")
            textSize = 27f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText("Customer: ${bill.customerName.ifBlank { "Customer" }}", w / 2f, 268f, custPaint)

        val dueStr = String.format(Locale.US, "₹%.2f", bill.dueAmount.coerceAtLeast(0.0))
        val duePill = RectF(160f, 300f, w - 160f, 386f)
        val dueFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FEE2E2") }
        val dueStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#B91C1C")
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }
        canvas.drawRoundRect(duePill, 20f, 20f, dueFill)
        canvas.drawRoundRect(duePill, 20f, 20f, dueStroke)
        val dueTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#B91C1C")
            textSize = 36f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText("Due Amount: $dueStr", w / 2f, 356f, dueTextPaint)

        val qrSize = 460
        val qrBmp = when {
            upi.upiId.isNotBlank() -> QrGenerator.generateQrBitmap(
                QrGenerator.buildUpiUri(upi.upiId, upi.payeeName, bill.dueAmount, bill.invoiceNo),
                qrSize
            )
            upi.fallbackQrBase64.isNotBlank() -> BitmapUtils.decodeBase64Bitmap(upi.fallbackQrBase64)
            else -> null
        }
        val qrLeft = (w - qrSize) / 2f
        val qrRect = RectF(qrLeft, 424f, qrLeft + qrSize, 424f + qrSize)
        if (qrBmp != null) {
            canvas.drawBitmap(qrBmp, null, qrRect, null)
            canvas.drawRect(qrRect, stroke)
        }

        val infoPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0B2238")
            textSize = 24f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText(
            if (upi.upiId.isNotBlank()) "UPI ID: ${upi.upiId}" else "Payee: ${upi.payeeName}",
            w / 2f,
            946f,
            infoPaint
        )
        canvas.drawText(
            "Scan using GPay / PhonePe / Paytm to pay exact $dueStr",
            w / 2f,
            994f,
            Paint(infoPaint).apply { color = Color.parseColor("#16845B"); textSize = 22f }
        )
        canvas.drawText(
            "Support: ${company.mobile}",
            w / 2f,
            1042f,
            Paint(infoPaint).apply { color = Color.parseColor("#475569"); textSize = 20f }
        )
        return bmp
    }

    fun shareDueQrToCustomerWhatsApp(
        context: Context,
        company: CompanySettings,
        upi: UpiSettings,
        bill: BillEntity
    ): Boolean {
        return try {
            val bmp = renderDuePaymentQrCardBitmap(company, upi, bill)
            val dir = File(context.cacheDir, "photos").apply { mkdirs() }
            val safeNo = bill.invoiceNo.replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "Bill" }
            val file = File(dir, "TSE_Due_QR_$safeNo.png")
            FileOutputStream(file).use { out ->
                bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
                out.flush()
            }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val phone = extractCustomerPhone(bill.customerDetails)
            val formattedJid = formatIndianPhoneForJid(phone)
            val dueStr = String.format(Locale.US, "₹%.2f", bill.dueAmount.coerceAtLeast(0.0))
            val msg = buildString {
                appendLine("*${company.companyName} — Due Payment Request*")
                appendLine("Invoice No: *${bill.invoiceNo}*")
                appendLine("Customer: ${bill.customerName}")
                appendLine("Due Balance: *$dueStr*")
                if (upi.upiId.isNotBlank()) {
                    appendLine("UPI ID: ${upi.upiId}")
                }
                append("Scan the attached QR code to pay your due balance of $dueStr.")
            }
            for (pkg in listOf("com.whatsapp", "com.whatsapp.w4b")) {
                val waIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "image/png"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_TEXT, msg)
                    if (!formattedJid.isNullOrBlank()) {
                        putExtra("jid", "$formattedJid@s.whatsapp.net")
                    }
                    setPackage(pkg)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                try {
                    context.startActivity(waIntent)
                    return true
                } catch (_: ActivityNotFoundException) {
                } catch (_: SecurityException) {
                }
            }
            val chooser = Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "image/png"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_TEXT, msg)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                },
                "Send Due Payment QR"
            ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            context.startActivity(chooser)
            true
        } catch (_: Throwable) {
            false
        }
    }

    suspend fun saveDocumentToDownloads(
        context: Context,
        fileName: String,
        mimeType: String,
        writer: (OutputStream) -> Boolean
    ): DownloadsSaveResult = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return@withContext DownloadsSaveResult.NeedsPickerFallback(fileName)
        }
        try {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, mimeType)
                put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/TSE_Billing")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri: Uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: return@withContext DownloadsSaveResult.NeedsPickerFallback(fileName)
            val ok = runCatching {
                resolver.openOutputStream(uri)?.use { out -> writer(out) } ?: false
            }.getOrDefault(false)
            if (!ok) {
                runCatching { resolver.delete(uri, null, null) }
                return@withContext DownloadsSaveResult.NeedsPickerFallback(fileName)
            }
            val clearPending = ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }
            runCatching { resolver.update(uri, clearPending, null, null) }
            DownloadsSaveResult.Saved("Downloads/TSE_Billing/$fileName", fileName)
        } catch (_: Throwable) {
            DownloadsSaveResult.NeedsPickerFallback(fileName)
        }
    }
}
