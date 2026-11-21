package com.example.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import com.example.data.CompanySettings
import com.example.data.CustomerHelpEntity
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DispatchCardGenerator {
    private const val CARD_W = 1080
    private const val CARD_H = 1350

    fun renderDispatchCardBitmap(
        company: CompanySettings,
        request: CustomerHelpEntity
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(CARD_W, CARD_H, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Clean background
        canvas.drawColor(Color.parseColor("#F1F5F9"))

        val cardRect = RectF(36f, 36f, CARD_W - 36f, CARD_H - 36f)
        val cardFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; style = Paint.Style.FILL }
        val cardStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0B2238")
            style = Paint.Style.STROKE
            strokeWidth = 5f
        }
        canvas.drawRoundRect(cardRect, 28f, 28f, cardFill)
        canvas.drawRoundRect(cardRect, 28f, 28f, cardStroke)

        // Navy Header Band
        val headerRect = RectF(36f, 36f, CARD_W - 36f, 290f)
        val headerFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0B2238")
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(headerRect, 28f, 28f, headerFill)
        canvas.drawRect(36f, 180f, CARD_W - 36f, 290f, headerFill)

        // Gold Accent Divider
        val goldLine = Paint().apply { color = Color.parseColor("#E4B44E"); strokeWidth = 8f }
        canvas.drawLine(36f, 290f, CARD_W - 36f, 290f, goldLine)

        // Company Logo
        val customLogo = InvoicePdfPrinter.decodeBase64Bitmap(company.logoBase64)
        val logoBounds = RectF(72f, 70f, 206f, 204f)
        if (customLogo != null) {
            canvas.drawBitmap(customLogo, null, logoBounds, null)
        } else {
            val emblemBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#164E63") }
            val emblemStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#E4B44E")
                style = Paint.Style.STROKE
                strokeWidth = 5f
            }
            canvas.drawRoundRect(logoBounds, 22f, 22f, emblemBg)
            canvas.drawRoundRect(logoBounds, 22f, 22f, emblemStroke)
            val tsePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#F4D47E")
                textSize = 46f
                textAlign = Paint.Align.CENTER
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            canvas.drawText("TSE", logoBounds.centerX(), logoBounds.centerY() + 16f, tsePaint)
        }

        val hTitlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 36f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        drawTextBlock(canvas, company.companyName.uppercase(Locale.ROOT), 232f, 68f, CARD_W - 290, hTitlePaint)

        val hSubPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#F4D47E")
            textSize = 24f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        drawTextBlock(
            canvas,
            "Mob: ${company.mobile}  |  ${company.email}",
            232f,
            154f,
            CARD_W - 290,
            hSubPaint
        )

        val hAddrPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#E2E8F0")
            textSize = 21f
        }
        drawTextBlock(canvas, "Office: ${company.address}", 232f, 198f, CARD_W - 290, hAddrPaint)

        // Banner Heading
        val bannerRect = RectF(72f, 322f, CARD_W - 72f, 404f)
        val bannerFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#E0F2FE") }
        val bannerStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#087F8C")
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }
        canvas.drawRoundRect(bannerRect, 16f, 16f, bannerFill)
        canvas.drawRoundRect(bannerRect, 16f, 16f, bannerStroke)

        val bannerTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0B2238")
            textSize = 32f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText(
            "TECHNICIAN DISPATCH CARD (#${request.id})",
            CARD_W / 2f,
            375f,
            bannerTextPaint
        )

        // Assigned Technician Box
        var y = 434f
        y = drawSectionBox(
            canvas = canvas,
            top = y,
            title = "ASSIGNED TECHNICIAN DETAILS",
            bgHex = "#E6F6EF",
            borderHex = "#16845B",
            titleHex = "#16845B",
            lines = listOf(
                "Technician Name: ${request.acceptedByName.ifBlank { request.acceptedBy.ifBlank { "Assigned Technician" } }}",
                "Technician Mobile: ${request.acceptedByPhone.ifBlank { company.mobile }}",
                "Username ID: @${request.acceptedBy.ifBlank { "technician" }}"
            )
        )

        y += 26f

        // Customer Details Box
        y = drawSectionBox(
            canvas = canvas,
            top = y,
            title = "CUSTOMER SERVICE DETAILS",
            bgHex = "#F8FAFC",
            borderHex = "#334155",
            titleHex = "#0B2238",
            lines = listOf(
                "Customer Name: ${request.customerName.ifBlank { "Customer" }}",
                "Customer Mobile: ${request.phone.ifBlank { "N/A" }}",
                "Address: ${request.address.ifBlank { "N/A" }}",
                if (request.locationUrl.isNotBlank()) "Location Pin: ${request.locationUrl}" else "Location Pin: Provided on Maps"
            )
        )

        y += 26f

        // Issue Description Box
        drawSectionBox(
            canvas = canvas,
            top = y,
            title = "REPORTED SUPPORT ISSUE",
            bgHex = "#FFFBEB",
            borderHex = "#D97706",
            titleHex = "#B45309",
            lines = listOf(request.helpDescription.ifBlank { "On-site CCTV inspection & service support." })
        )

        // Footer Note
        val dateStr = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.US).format(Date())
        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#475569")
            textSize = 22f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText(
            "You may contact the assigned technician directly for arrival coordination.",
            CARD_W / 2f,
            CARD_H - 110f,
            footerPaint
        )
        canvas.drawText(
            "Generated on $dateStr • Thank you for choosing ${company.companyName}",
            CARD_W / 2f,
            CARD_H - 72f,
            Paint(footerPaint).apply { textSize = 19f; color = Color.parseColor("#087F8C") }
        )

        return bitmap
    }

    private fun drawSectionBox(
        canvas: Canvas,
        top: Float,
        title: String,
        bgHex: String,
        borderHex: String,
        titleHex: String,
        lines: List<String>
    ): Float {
        val contentText = lines.joinToString("\n")
        val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0F172A")
            textSize = 25f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }
        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor(titleHex)
            textSize = 23f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val maxW = CARD_W - 196
        val layout = StaticLayout.Builder.obtain(contentText, 0, contentText.length, bodyPaint, maxW)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(6f, 1f)
            .build()
        val boxHeight = 68f + layout.height + 28f
        val rect = RectF(72f, top, CARD_W - 72f, top + boxHeight)

        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor(bgHex) }
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor(borderHex)
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }
        canvas.drawRoundRect(rect, 18f, 18f, fill)
        canvas.drawRoundRect(rect, 18f, 18f, stroke)

        canvas.drawText(title, 98f, top + 40f, titlePaint)
        canvas.save()
        canvas.translate(98f, top + 58f)
        layout.draw(canvas)
        canvas.restore()

        return rect.bottom
    }

    private fun drawTextBlock(
        canvas: Canvas,
        text: String,
        x: Float,
        y: Float,
        maxW: Int,
        paint: TextPaint
    ) {
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, maxW.coerceAtLeast(100))
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setMaxLines(2)
            .build()
        canvas.save()
        canvas.translate(x, y)
        layout.draw(canvas)
        canvas.restore()
    }

    fun shareDispatchCardToCustomerWhatsApp(
        context: Context,
        company: CompanySettings,
        request: CustomerHelpEntity
    ): Boolean {
        return try {
            val bmp = renderDispatchCardBitmap(company, request)
            val dir = File(context.cacheDir, "photos").apply { mkdirs() }
            val file = File(dir, "TSE_Dispatch_${request.id}.png")
            FileOutputStream(file).use { out ->
                bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
                out.flush()
            }
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val caption = buildString {
                appendLine("*${company.companyName} — Technician Assigned*")
                appendLine("Technician: ${request.acceptedByName.ifBlank { request.acceptedBy }} (${request.acceptedByPhone.ifBlank { company.mobile }})")
                appendLine("Customer: ${request.customerName} (${request.phone})")
                appendLine("Address: ${request.address}")
                append("Issue: ${request.helpDescription}")
            }

            // Try targeted WhatsApp share with photo + customer JID hint, then chooser fallback
            val waIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, caption)
                val digits = request.phone.filter { it.isDigit() }
                if (digits.length >= 10) {
                    val full = if (digits.length == 10) "91$digits" else digits
                    putExtra("jid", "$full@s.whatsapp.net")
                }
                setPackage("com.whatsapp")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(waIntent)
                return true
            } catch (_: ActivityNotFoundException) {
            } catch (_: SecurityException) {
            }

            val chooser = Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "image/png"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_TEXT, caption)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                },
                "Share Technician Dispatch Card"
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun openCustomerWhatsAppChat(
        context: Context,
        phone: String,
        message: String = ""
    ): Boolean {
        val digits = phone.filter { it.isDigit() }
        if (digits.length < 7) return false
        val formatted = if (digits.length == 10) "91$digits" else digits
        val encodedMsg = if (message.isNotBlank()) "?text=${Uri.encode(message)}" else ""
        val uri = Uri.parse("https://wa.me/$formatted$encodedMsg")
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }
}
