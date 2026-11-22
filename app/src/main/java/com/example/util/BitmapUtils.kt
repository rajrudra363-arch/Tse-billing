package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

object BitmapUtils {

    fun calculateInSampleSize(
        options: BitmapFactory.Options,
        reqWidth: Int,
        reqHeight: Int
    ): Int {
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize.coerceAtLeast(1)
    }

    suspend fun decodeSampledBitmapFromUri(
        context: Context,
        uri: Uri,
        reqWidth: Int = 720,
        reqHeight: Int = 720
    ): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, bounds)
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                // Fallback direct decode if bounds could not be pre-read
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    return@runCatching BitmapFactory.decodeStream(stream)?.let {
                        scaleDown(it, reqWidth.coerceAtLeast(reqHeight))
                    }
                }
                return@runCatching null
            }
            val opts = BitmapFactory.Options().apply {
                inSampleSize = calculateInSampleSize(bounds, reqWidth, reqHeight)
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val decoded = context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, opts)
            }
            decoded?.let { scaleDown(it, reqWidth.coerceAtLeast(reqHeight)) }
        }.getOrNull()
    }

    suspend fun decodeSampledBitmapFromFile(
        file: File,
        reqWidth: Int = 720,
        reqHeight: Int = 720
    ): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            if (!file.exists() || file.length() <= 0L) return@runCatching null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            val opts = BitmapFactory.Options().apply {
                inSampleSize = if (bounds.outWidth > 0 && bounds.outHeight > 0) {
                    calculateInSampleSize(bounds, reqWidth, reqHeight)
                } else {
                    2
                }
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val decoded = BitmapFactory.decodeFile(file.absolutePath, opts)
            decoded?.let { scaleDown(it, reqWidth.coerceAtLeast(reqHeight)) }
        }.getOrNull()
    }

    fun decodeBase64Bitmap(b64: String, maxDimension: Int = 900): Bitmap? {
        if (b64.isBlank()) return null
        return try {
            val clean = b64.substringAfter("base64,", b64).trim()
            val bytes = Base64.decode(clean, Base64.DEFAULT)
            if (bytes.isEmpty()) return null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            val sample = if (bounds.outWidth > 0 && bounds.outHeight > 0) {
                calculateInSampleSize(bounds, maxDimension, maxDimension)
            } else {
                1
            }
            val opts = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        } catch (_: Throwable) {
            null
        }
    }

    fun scaleDown(bitmap: Bitmap, maxDim: Int = 720): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= maxDim && h <= maxDim) return bitmap
        val ratio = maxDim.toFloat() / maxOf(w, h).toFloat()
        val newW = (w * ratio).toInt().coerceAtLeast(1)
        val newH = (h * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, newW, newH, true)
    }

    fun bitmapToCompressedBase64(bitmap: Bitmap, maxDim: Int = 480, quality: Int = 82): String {
        val scaled = scaleDown(bitmap, maxDim)
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    fun rotateIfNeeded(bitmap: Bitmap, degrees: Float): Bitmap {
        if (degrees == 0f) return bitmap
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
}
