package com.example.util

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.util.LruCache
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.util.Locale

object QrGenerator {
    private val cache = LruCache<String, Bitmap>(32)

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
        val pn = payeeName.trim().ifEmpty { "Total Solution Enterprises" }
        val builder = StringBuilder("upi://pay?pa=${Uri.encode(pa)}&pn=${Uri.encode(pn)}&cu=INR")
        if (amount > 0.009) {
            builder.append("&am=").append(String.format(Locale.US, "%.2f", amount))
        }
        if (invoiceNo.isNotBlank()) {
            builder.append("&tn=").append(Uri.encode("Invoice ${invoiceNo.trim()}"))
        }
        return builder.toString()
    }

    fun buildWhatsAppSupportUri(mobileRaw: String): String {
        val digitsOnly = mobileRaw.filter { it.isDigit() }
        val formatted = if (digitsOnly.length == 10) "91$digitsOnly" else digitsOnly
        val msg = Uri.encode("Hello Total Solution Enterprises, I need customer support.")
        return "https://wa.me/$formatted?text=$msg"
    }
}
