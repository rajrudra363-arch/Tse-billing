package com.example.util

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.util.LruCache
import com.example.data.AppIdentity
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.util.Locale

object QrGenerator {
    private val cache = LruCache<String, Bitmap>(32)

    const val CUSTOMER_SUPPORT_WHATSAPP_MESSAGE =
        "Hello! I need support from TOTAL SOLUTION. Please assist me. Thank you! 😊"

    fun generateQrBitmap(content: String, sizePx: Int = 360): Bitmap? {
        val trimmed = content.trim()
        if (trimmed.isEmpty()) return null
        val key = "$sizePx|$trimmed"
        cache.get(key)?.let { return it }
        return try {
            val hints = mapOf(
                EncodeHintType.CHARACTER_SET to "UTF-8",
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
                EncodeHintType.MARGIN to 1
            )
            val matrix = QRCodeWriter().encode(trimmed, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
            val pixels = IntArray(sizePx * sizePx)
            for (y in 0 until sizePx) {
                val offset = y * sizePx
                for (x in 0 until sizePx) {
                    pixels[offset + x] = if (matrix[x, y]) Color.BLACK else Color.WHITE
                }
            }
            Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888).apply {
                setPixels(pixels, 0, sizePx, 0, 0, sizePx, sizePx)
                cache.put(key, this)
            }
        } catch (_: Exception) {
            null
        }
    }

    fun buildUpiUri(upiId: String, payeeName: String, amount: Double, invoiceNo: String): String {
        val pa = upiId.trim()
        val pn = payeeName.trim().ifEmpty { AppIdentity.OFFICIAL_COMPANY_NAME }
        val builder = StringBuilder("upi://pay?pa=${Uri.encode(pa)}&pn=${Uri.encode(pn)}&cu=INR")
        if (amount > 0.009) {
            builder.append("&am=").append(String.format(Locale.US, "%.2f", amount))
        }
        if (invoiceNo.isNotBlank()) {
            builder.append("&tn=").append(Uri.encode("Invoice ${invoiceNo.trim()}"))
        }
        return builder.toString()
    }

    /**
     * Part 14: Universal HTTPS WhatsApp Support link using company WhatsApp number and locked TOTAL SOLUTION greeting.
     * Returns empty string when no valid phone number is configured (never invents a fallback number).
     */
    fun buildWhatsAppSupportUri(mobileRaw: String): String {
        val digitsOnly = mobileRaw.filter { it.isDigit() }
        if (digitsOnly.length < 7) return ""
        val formatted = if (digitsOnly.length == 10) "91$digitsOnly" else digitsOnly
        val msg = Uri.encode(CUSTOMER_SUPPORT_WHATSAPP_MESSAGE)
        return "https://wa.me/$formatted?text=$msg"
    }

    /**
     * Part 15: Universal HTTPS WhatsApp introduction link sent manually by assigned Technician to customer.
     */
    fun buildTechnicianIntroWhatsAppUri(customerMobileRaw: String, technicianName: String): String {
        val digitsOnly = customerMobileRaw.filter { it.isDigit() }
        if (digitsOnly.length < 7) return ""
        val formatted = if (digitsOnly.length == 10) "91$digitsOnly" else digitsOnly
        val safeTechName = technicianName.trim().ifBlank { "Technician" }
        val text = "Hello! My name is $safeTechName. I am the Technician assigned by ${AppIdentity.OFFICIAL_COMPANY_NAME} for your service request. I am contacting you regarding your service. Thank you!"
        return "https://wa.me/$formatted?text=${Uri.encode(text)}"
    }
}
