package com.example.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.util.Base64
import androidx.core.content.FileProvider
import com.example.data.ChargeType
import com.example.data.CompanySettings
import com.example.data.InvoiceItem
import com.example.data.PaymentMode
import com.example.data.UpiSettings
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.Locale

data class InvoicePrintPayload(
    val company: CompanySettings,
    val upi: UpiSettings,
    val invoiceNo: String,
    val date: String,
    val customerName: String,
    val customerDetails: String,
    val items: List<InvoiceItem>,
    val subtotal: Double,
    val discount: Double,
    val grandTotal: Double,
    val paidAmount: Double,
    val dueAmount: Double,
    val paymentMode: PaymentMode,
    val signatureBase64: String,
    val customQrText: String,
    val extraChargeType: ChargeType = ChargeType.NONE,
    val extraChargeAmount: Double = 0.0
)

object InvoicePdfPrinter {
    // Exact ISO A4 Portrait in 72pt/inch (210mm x 297mm)
    const val A4_WIDTH_PT = 595
    const val A4_HEIGHT_PT = 842
    private const val MARGIN = 18f

    fun printInvoice(context: Context, payload: InvoicePrintPayload) {
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return
        val safeNo = sanitizeFileName(payload.invoiceNo.ifBlank { "Bill" })
        val jobName = "TSE_Invoice_$safeNo"
        val attrs = PrintAttributes.Builder()
            .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
            .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
            .build()
        printManager.print(jobName, InvoicePrintAdapter(payload), attrs)
    }

    fun writePdfToStream(payload: InvoicePrintPayload, outputStream: OutputStream): Boolean {
        val pdf = PdfDocument()
        return try {
            val pageInfo = PdfDocument.PageInfo.Builder(A4_WIDTH_PT, A4_HEIGHT_PT, 1).create()
            val page = pdf.startPage(pageInfo)
            drawInvoicePage(page.canvas, payload)
            pdf.finishPage(page)
            pdf.writeTo(outputStream)
            outputStream.flush()
            true
        } catch (e: Exception) {
            android.util.Log.e("InvoicePdfPrinter", "PDF generation failed", e)
            false
        } finally {
            pdf.close()
        }
    }

    fun sharePdf(context: Context, payload: InvoicePrintPayload): Boolean {
        return try {
            val dir = File(context.cacheDir, "pdfs").apply { mkdirs() }
            val safeNo = sanitizeFileName(payload.invoiceNo.ifBlank { "Bill" })
            val file = File(dir, "TSE_Invoice_$safeNo.pdf")
            FileOutputStream(file).use { out ->
                if (!writePdfToStream(payload, out)) return false
            }
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Tax Invoice ${payload.invoiceNo} - ${payload.company.companyName}")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Share A4 Invoice PDF"))
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun sanitizeFileName(raw: String): String {
        return raw.replace(Regex("[^A-Za-z0-9_-]"), "_").ifBlank { "Bill" }
    }

    private class InvoicePrintAdapter(
        private val payload: InvoicePrintPayload
    ) : PrintDocumentAdapter() {

        override fun onLayout(
            oldAttributes: PrintAttributes?,
            newAttributes: PrintAttributes?,
            cancellationSignal: CancellationSignal?,
            callback: LayoutResultCallback,
            extras: Bundle?
        ) {
            if (cancellationSignal?.isCanceled == true) {
                callback.onLayoutCancelled()
                return
            }
            val safeNo = sanitizeFileName(payload.invoiceNo.ifBlank { "Bill" })
            val info = PrintDocumentInfo.Builder("TSE_Invoice_$safeNo.pdf")
                .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                .setPageCount(1)
                .build()
            callback.onLayoutFinished(info, true)
        }

        override fun onWrite(
            pages: Array<out PageRange>?,
            destination: ParcelFileDescriptor,
            cancellationSignal: CancellationSignal?,
            callback: WriteResultCallback
        ) {
            try {
                FileOutputStream(destination.fileDescriptor).use { out ->
                    if (writePdfToStream(payload, out)) {
                        callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                    } else {
                        callback.onWriteFailed("Could not generate A4 PDF")
                    }
                }
            } catch (e: Exception) {
                callback.onWriteFailed(e.message ?: "PDF render failed")
            }
        }
    }

    internal fun drawInvoicePage(canvas: Canvas, p: InvoicePrintPayload) {
        val left = MARGIN
        val right = A4_WIDTH_PT - MARGIN
        val top = MARGIN
        val bottom = A4_HEIGHT_PT - MARGIN
        val width = right - left

        val bgWhite = Paint().apply { color = Color.WHITE; style = Paint.Style.FILL }
        canvas.drawRect(0f, 0f, A4_WIDTH_PT.toFloat(), A4_HEIGHT_PT.toFloat(), bgWhite)

        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#111111")
            style = Paint.Style.STROKE
            strokeWidth = 1.1f
        }
        val thinBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#222222")
            style = Paint.Style.STROKE
            strokeWidth = 0.7f
        }
        val fillHeader = Paint().apply { color = Color.parseColor("#EDF4F7"); style = Paint.Style.FILL }
        val fillSoft = Paint().apply { color = Color.parseColor("#F7FAFC"); style = Paint.Style.FILL }
        val fillGrand = Paint().apply { color = Color.parseColor("#EEF5F8"); style = Paint.Style.FILL }
        val fillDue = Paint().apply { color = Color.parseColor("#FEE2E2"); style = Paint.Style.FILL }
        val dueBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#B91C1C")
            style = Paint.Style.STROKE
            strokeWidth = 1.2f
        }

        // Outer A4 border
        canvas.drawRect(left, top, right, bottom, borderPaint)

        // 1. TOP HEADER: 2/3 Company Details + 1/3 Customer Care & Support QR (y: 18 -> 126)
        val headerH = 108f
        val splitX = left + width * 0.67f
        val headerBottom = top + headerH
        canvas.drawLine(left, headerBottom, right, headerBottom, borderPaint)
        canvas.drawLine(splitX, top, splitX, headerBottom, borderPaint)

        // Draw TSE Shield Logo
        drawShieldLogo(canvas, RectF(left + 8f, top + 18f, left + 74f, top + 88f))

        // Company Details (2/3 left)
        val infoX = left + 82f
        val infoW = (splitX - infoX - 8f).toInt().coerceAtLeast(100)
        val companyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#102F4B")
            textSize = 21f
            isFakeBoldText = true
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        var curY = top + 7f
        curY += drawWrappedText(
            canvas = canvas,
            text = p.company.companyName.uppercase(Locale.ROOT),
            x = infoX,
            y = curY,
            maxWidth = infoW,
            paint = companyPaint,
            maxLines = 2
        )

        val tagPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#087F8C")
            textSize = 8.4f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        curY += 2f
        curY += drawWrappedText(canvas, "CCTV Security • Sales • Installation • Support", infoX, curY, infoW, tagPaint, maxLines = 1)

        val contactPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#111111")
            textSize = 8.2f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        curY += 2f
        curY += drawWrappedText(
            canvas,
            "Mob/WhatsApp: ${p.company.mobile} | Email: ${p.company.email}",
            infoX,
            curY,
            infoW,
            contactPaint,
            maxLines = 2
        )

        val addrPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#222222")
            textSize = 7.8f
        }
        curY += 2f
        drawWrappedText(canvas, "Office Address: ${p.company.address}", infoX, curY, infoW, addrPaint, maxLines = 2)

        // Right 1/3: Customer Care & Support WhatsApp QR
        val careCenterX = splitX + (right - splitX) / 2f
        val centerBold = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#102F4B")
            textSize = 8.5f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText("Customer Care & Support", careCenterX, top + 14f, centerBold)
        val waQr = QrGenerator.generateQrBitmap(QrGenerator.buildWhatsAppSupportUri(p.company.mobile), 180)
        if (waQr != null) {
            val qrRect = RectF(careCenterX - 28f, top + 19f, careCenterX + 28f, top + 75f)
            canvas.drawBitmap(waQr, null, qrRect, null)
            canvas.drawRect(qrRect, thinBorder)
        }
        val careSub = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#222222")
            textSize = 7.2f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("Scan to WhatsApp us", careCenterX, top + 86f, careSub)
        canvas.drawText(p.company.mobile, careCenterX, top + 97f, careSub)

        // 2. TAX INVOICE DOUBLE-BORDERED BANNER (y: 127 -> 151)
        val bannerTop = headerBottom + 5f
        val bannerH = 24f
        val bannerRect = RectF(left + 5f, bannerTop, right - 5f, bannerTop + bannerH)
        canvas.drawRect(bannerRect, fillSoft)
        canvas.drawRect(bannerRect, borderPaint)
        canvas.drawRect(
            RectF(bannerRect.left + 2.5f, bannerRect.top + 2.5f, bannerRect.right - 2.5f, bannerRect.bottom - 2.5f),
            thinBorder
        )
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 12f
            textAlign = Paint.Align.CENTER
            letterSpacing = 0.15f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText("TAX INVOICE", left + width / 2f, bannerTop + 16.5f, titlePaint)

        // 3. META ROW (M/S | DATE & INVOICE NO) (y: 160 -> 216)
        val metaTop = bannerTop + bannerH + 5f
        val metaH = 56f
        val metaBottom = metaTop + metaH
        val metaSplitX = left + width * 0.58f
        canvas.drawRect(left + 5f, metaTop, right - 5f, metaBottom, borderPaint)
        canvas.drawLine(metaSplitX, metaTop, metaSplitX, metaBottom, borderPaint)

        val labelPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 9f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 8.6f
        }
        canvas.drawText("M/S :", left + 11f, metaTop + 14f, labelPaint)
        val custBlock = buildString {
            append(p.customerName.ifBlank { "Walk-in Customer" })
            if (p.customerDetails.isNotBlank()) {
                append("\n").append(p.customerDetails)
            }
        }
        drawWrappedText(canvas, custBlock, left + 40f, metaTop + 5f, (metaSplitX - left - 46f).toInt(), bodyPaint, maxLines = 4)

        canvas.drawText("DATE: ${p.date}", metaSplitX + 8f, metaTop + 20f, labelPaint)
        canvas.drawText("INVOICE NO. : ${p.invoiceNo}", metaSplitX + 8f, metaTop + 40f, labelPaint)

        // 4. ITEMS TABLE (Header + 15+ Fixed Rows)
        val effectiveItems = buildEffectivePrintItems(p.items, p.extraChargeType, p.extraChargeAmount)
        val tableTop = metaBottom + 5f
        val tableLeft = left + 5f
        val tableRight = right - 5f
        val tableW = tableRight - tableLeft
        val headerRowH = 22f
        val totalRows = maxOf(15, effectiveItems.size)
        val itemRowH = (25.5f * 11f) / totalRows.toFloat()
        val tableBottom = tableTop + headerRowH + (itemRowH * totalRows)

        val colWidths = floatArrayOf(34f, tableW - 34f - 42f - 46f - 72f - 82f, 42f, 46f, 72f, 82f)
        val colHeaders = arrayOf("Sr No.", "Description", "Qty", "Units.", "Rate (₹)", "Amount (₹)")

        canvas.drawRect(tableLeft, tableTop, tableRight, tableTop + headerRowH, fillHeader)
        canvas.drawRect(tableLeft, tableTop, tableRight, tableBottom, borderPaint)

        var xCursor = tableLeft
        for (c in colWidths.indices) {
            if (c > 0) canvas.drawLine(xCursor, tableTop, xCursor, tableBottom, thinBorder)
            val hPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                textSize = 8.2f
                textAlign = Paint.Align.CENTER
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            canvas.drawText(colHeaders[c], xCursor + colWidths[c] / 2f, tableTop + 14.5f, hPaint)
            xCursor += colWidths[c]
        }
        canvas.drawLine(tableLeft, tableTop + headerRowH, tableRight, tableTop + headerRowH, borderPaint)

        val cellLeftPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 7.8f }
        val cellCenterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 7.8f; textAlign = Paint.Align.CENTER }
        val cellRightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 7.8f; textAlign = Paint.Align.RIGHT }

        for (r in 0 until totalRows) {
            val rowTop = tableTop + headerRowH + r * itemRowH
            val rowBase = rowTop + (itemRowH * 0.68f)
            if (r > 0) canvas.drawLine(tableLeft, rowTop, tableRight, rowTop, thinBorder)

            val item = effectiveItems.getOrNull(r)
            if (item != null && (item.description.isNotBlank() || item.rate > 0.0)) {
                var cx = tableLeft
                canvas.drawText("${r + 1}", cx + colWidths[0] - 6f, rowBase, cellRightPaint)
                cx += colWidths[0]

                val descText = ellipsizeText(item.description, cellLeftPaint, colWidths[1] - 10f)
                canvas.drawText(descText, cx + 5f, rowBase, cellLeftPaint)
                cx += colWidths[1]

                val qtyStr = if (item.qty % 1.0 == 0.0) item.qty.toInt().toString() else String.format(Locale.US, "%.2f", item.qty)
                canvas.drawText(qtyStr, cx + colWidths[2] / 2f, rowBase, cellCenterPaint)
                cx += colWidths[2]

                canvas.drawText(item.unit.ifBlank { "Nos" }, cx + colWidths[3] / 2f, rowBase, cellCenterPaint)
                cx += colWidths[3]

                canvas.drawText(String.format(Locale.US, "%.2f", item.rate), cx + colWidths[4] - 5f, rowBase, cellRightPaint)
                cx += colWidths[4]

                canvas.drawText(String.format(Locale.US, "%.2f", item.amount), cx + colWidths[5] - 5f, rowBase, cellRightPaint)
            }
        }

        // 5. AMOUNT SUMMARY & TOTALS ROW
        val sumTop = tableBottom
        val sumH = 114f
        val sumBottom = sumTop + sumH
        val sumSplitX = tableLeft + tableW * 0.57f
        canvas.drawRect(tableLeft, sumTop, tableRight, sumBottom, borderPaint)
        canvas.drawLine(sumSplitX, sumTop, sumSplitX, sumBottom, borderPaint)

        // Left side: Words + Payment Mode + Online Payment QR (using full bill amount)
        val wordsAreaW = (sumSplitX - tableLeft) * 0.42f
        val modeAreaW = (sumSplitX - tableLeft) * 0.24f
        val wordsX = tableLeft + 6f
        canvas.drawText("Rupees (Amount in Words):", wordsX, sumTop + 14f, labelPaint)
        val wordsText = RupeesWords.convert(p.grandTotal)
        drawWrappedText(canvas, wordsText, wordsX, sumTop + 20f, (wordsAreaW - 10f).toInt(), bodyPaint, maxLines = 5)

        val modeX = tableLeft + wordsAreaW + 4f
        canvas.drawText("Payment Mode", modeX, sumTop + 14f, labelPaint)
        drawRadioIndicator(canvas, modeX + 6f, sumTop + 32f, p.paymentMode == PaymentMode.ONLINE)
        canvas.drawText("Online", modeX + 14f, sumTop + 35f, bodyPaint)
        drawRadioIndicator(canvas, modeX + 6f, sumTop + 52f, p.paymentMode == PaymentMode.CASH)
        canvas.drawText("Cash", modeX + 14f, sumTop + 55f, bodyPaint)

        val qrColStart = tableLeft + wordsAreaW + modeAreaW
        val qrColW = sumSplitX - qrColStart
        val qrColCenterX = qrColStart + (qrColW / 2f)
        val smallBold = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 7.8f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText("Online QR Payment :-", qrColCenterX, sumTop + 13f, smallBold)

        val qrAmt = if (p.grandTotal > 0.009) p.grandTotal else p.subtotal
        val payQrBitmap = when {
            p.upi.upiId.isNotBlank() -> QrGenerator.generateQrBitmap(
                QrGenerator.buildUpiUri(p.upi.upiId, p.upi.payeeName, qrAmt, p.invoiceNo),
                200
            )
            p.upi.fallbackQrBase64.isNotBlank() -> decodeBase64Bitmap(p.upi.fallbackQrBase64)
            else -> null
        }
        if (payQrBitmap != null) {
            val qrRect = RectF(qrColCenterX - 31f, sumTop + 17f, qrColCenterX + 31f, sumTop + 79f)
            canvas.drawBitmap(payQrBitmap, null, qrRect, null)
            canvas.drawRect(qrRect, thinBorder)
        }
        val smallCenter = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#111111")
            textSize = 6.8f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        if (p.upi.upiId.isNotBlank()) {
            canvas.drawText(ellipsizeText("UPI: ${p.upi.upiId}", smallCenter, qrColW - 6f), qrColCenterX, sumTop + 91f, smallCenter)
        }
        canvas.drawText(ellipsizeText("A/c: ${p.upi.payeeName}", smallCenter, qrColW - 6f), qrColCenterX, sumTop + 102f, smallCenter)

        // Right side: Totals 3 rows (SUB TOTAL, ADVANCE, GRAND TOTAL) — Discount & Due removed from Print
        val totLeft = sumSplitX
        val totRowH = sumH / 3f
        val totValSplit = tableRight - 84f

        val totLabels = arrayOf("SUB TOTAL", "ADVANCE", "GRAND TOTAL")
        val totValues = arrayOf(p.subtotal, p.paidAmount, p.grandTotal)

        for (i in 0 until 3) {
            val rTop = sumTop + i * totRowH
            val rBot = rTop + totRowH
            val isGrand = (i == 2)
            if (isGrand) {
                canvas.drawRect(totLeft, rTop, tableRight, rBot, fillGrand)
            }
            if (i > 0) canvas.drawLine(totLeft, rTop, tableRight, rTop, thinBorder)
            canvas.drawLine(totValSplit, rTop, totValSplit, rBot, thinBorder)

            val lPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                textSize = if (isGrand) 9.6f else 8.8f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val vPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                textSize = if (isGrand) 10.2f else 9.0f
                textAlign = Paint.Align.RIGHT
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val baselineY = rTop + (totRowH * 0.60f)
            canvas.drawText(totLabels[i], totLeft + 6f, baselineY, lPaint)
            canvas.drawText("₹${String.format(Locale.US, "%.2f", totValues[i])}", tableRight - 6f, baselineY, vPaint)
        }

        // 6. BOTTOM SECTION
        val footTop = sumBottom
        val footRow1H = 92f
        val footRow2H = 38f
        val col1X = tableLeft + tableW * 0.32f
        val col2X = tableLeft + tableW * 0.68f

        val row1Bot = footTop + footRow1H
        canvas.drawRect(tableLeft, footTop, tableRight, row1Bot, borderPaint)
        canvas.drawLine(col1X, footTop, col1X, row1Bot, thinBorder)
        canvas.drawLine(col2X, footTop, col2X, row1Bot, thinBorder)

        // Left: Customer Scanned Signature
        val sigBitmap = decodeBase64Bitmap(p.signatureBase64)
        if (sigBitmap != null) {
            canvas.drawBitmap(sigBitmap, null, RectF(tableLeft + 14f, footTop + 8f, col1X - 14f, row1Bot - 24f), null)
        }
        canvas.drawLine(tableLeft + 10f, row1Bot - 20f, col1X - 10f, row1Bot - 20f, thinBorder)
        canvas.drawText("Customer Signature", tableLeft + (col1X - tableLeft) / 2f, row1Bot - 8f, smallBold)

        // Center: Customer Information QR
        val midCenterX = col1X + (col2X - col1X) / 2f
        canvas.drawText("Customer Information QR", midCenterX, footTop + 12f, smallBold)
        val custQr = if (p.customQrText.isNotBlank()) QrGenerator.generateQrBitmap(p.customQrText, 180) else null
        if (custQr != null) {
            val cQrRect = RectF(midCenterX - 28f, footTop + 18f, midCenterX + 28f, footTop + 74f)
            canvas.drawBitmap(custQr, null, cQrRect, null)
            canvas.drawRect(cQrRect, thinBorder)
        } else {
            val emptyInfo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#64748B")
                textSize = 7.5f
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText("No extra info QR added", midCenterX, footTop + 50f, emptyInfo)
        }

        // Right: Authorised Signatory (Admin uploaded photo + company name)
        val authSigBitmap = decodeBase64Bitmap(p.company.authorisedSignatureBase64)
        if (authSigBitmap != null) {
            canvas.drawBitmap(authSigBitmap, null, RectF(col2X + 14f, footTop + 6f, tableRight - 14f, row1Bot - 31f), null)
        }
        val rightCenterX = col2X + (tableRight - col2X) / 2f
        canvas.drawLine(col2X + 10f, row1Bot - 28f, tableRight - 10f, row1Bot - 28f, thinBorder)
        canvas.drawText("Authorised Signatory", rightCenterX, row1Bot - 16f, smallBold)
        canvas.drawText(
            ellipsizeText(p.company.companyName, smallCenter, tableRight - col2X - 10f),
            rightCenterX,
            row1Bot - 6f,
            smallCenter
        )

        // Row 2: Customer Message Row (wrapped safely with StaticLayout)
        val row2Top = row1Bot
        val row2Bot = row2Top + footRow2H
        canvas.drawRect(tableLeft, row2Top, tableRight, row2Bot, fillSoft)
        canvas.drawRect(tableLeft, row2Top, tableRight, row2Bot, borderPaint)
        val msgTitlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#102F4B")
            textSize = 8.4f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val msgBodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#102F4B")
            textSize = 7.5f
        }
        val titleH = drawWrappedText(
            canvas,
            "Thank you for choosing ${p.company.companyName}!",
            tableLeft + 8f,
            row2Top + 4f,
            (tableW - 16f).toInt(),
            msgTitlePaint,
            Layout.Alignment.ALIGN_CENTER,
            maxLines = 1
        )
        drawWrappedText(
            canvas,
            "We value your trust. Your safety and satisfaction are our priority. Please contact us anytime for CCTV service and support.",
            tableLeft + 8f,
            row2Top + 5f + titleH,
            (tableW - 16f).toInt(),
            msgBodyPaint,
            Layout.Alignment.ALIGN_CENTER,
            maxLines = 2
        )

        // Row 3: Product Disclaimer Row
        val row3Top = row2Bot
        val row3Bot = bottom - 4f
        canvas.drawRect(tableLeft, row3Top, tableRight, row3Bot, borderPaint)
        val discPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#334155")
            textSize = 7.1f
        }
        drawWrappedText(
            canvas,
            "Product Disclaimer: Product warranty is subject to the respective manufacturer's terms and conditions. Physical damage, water damage, voltage fluctuation, improper installation or unauthorised tampering are not covered unless the manufacturer's policy states otherwise.",
            tableLeft + 8f,
            row3Top + 6f,
            (tableW - 16f).toInt(),
            discPaint,
            Layout.Alignment.ALIGN_CENTER,
            maxLines = 4
        )
    }

    private fun drawShieldLogo(canvas: Canvas, bounds: RectF) {
        val w = bounds.width()
        val h = bounds.height()
        val ox = bounds.left
        val oy = bounds.top
        fun sx(v: Float) = ox + (v / 120f) * w
        fun sy(v: Float) = oy + (v / 120f) * h

        val shield = Path().apply {
            moveTo(sx(60f), sy(5f))
            lineTo(sx(108f), sy(23f))
            lineTo(sx(108f), sy(57f))
            cubicTo(sx(108f), sy(86f), sx(88f), sy(104f), sx(60f), sy(115f))
            cubicTo(sx(32f), sy(104f), sx(12f), sy(86f), sx(12f), sy(57f))
            lineTo(sx(12f), sy(23f))
            close()
        }
        val fillBlue = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#102F4B"); style = Paint.Style.FILL }
        val strokeGold = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#D4A12A"); style = Paint.Style.STROKE; strokeWidth = 3f }
        canvas.drawPath(shield, fillBlue)
        canvas.drawPath(shield, strokeGold)

        val lensOuter = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; style = Paint.Style.FILL }
        val lensCyan = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#087F8C"); style = Paint.Style.FILL }
        canvas.drawCircle(sx(60f), sy(52f), w * 0.16f, lensOuter)
        canvas.drawCircle(sx(60f), sy(52f), w * 0.09f, lensCyan)

        val tsePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#F4D47E")
            textSize = h * 0.22f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText("TSE", sx(60f), sy(98f), tsePaint)
    }

    private fun drawRadioIndicator(canvas: Canvas, cx: Float, cy: Float, selected: Boolean) {
        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = 1f
        }
        canvas.drawCircle(cx, cy, 4.2f, ring)
        if (selected) {
            val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; style = Paint.Style.FILL }
            canvas.drawCircle(cx, cy, 2.3f, dot)
        }
    }

    private fun drawWrappedText(
        canvas: Canvas,
        text: String,
        x: Float,
        y: Float,
        maxWidth: Int,
        paint: TextPaint,
        alignment: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL,
        maxLines: Int = Int.MAX_VALUE
    ): Float {
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, maxWidth.coerceAtLeast(20))
            .setAlignment(alignment)
            .setIncludePad(false)
            .setMaxLines(maxLines)
            .setEllipsize(TextUtils.TruncateAt.END)
            .build()
        canvas.save()
        canvas.translate(x, y)
        layout.draw(canvas)
        canvas.restore()
        return layout.height.toFloat()
    }

    private fun ellipsizeText(text: String, paint: Paint, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        var end = text.length
        while (end > 3 && paint.measureText(text.substring(0, end) + "...") > maxWidth) {
            end--
        }
        return text.substring(0, end.coerceAtLeast(0)) + "..."
    }

    fun buildEffectivePrintItems(
        items: List<InvoiceItem>,
        extraChargeType: ChargeType,
        extraChargeAmount: Double
    ): List<InvoiceItem> {
        val active = items.filter { it.description.isNotBlank() || it.rate > 0.0 }.toMutableList()
        if (extraChargeType != ChargeType.NONE && extraChargeAmount > 0.0) {
            active.add(
                InvoiceItem(
                    localId = -999L,
                    description = extraChargeType.label,
                    qty = 1.0,
                    unit = "Job",
                    rate = extraChargeAmount
                )
            )
        }
        return active
    }

    fun decodeBase64Bitmap(raw: String): Bitmap? {
        if (raw.isBlank()) return null
        return try {
            val clean = if (raw.contains(",")) raw.substringAfter(",") else raw
            val bytes = Base64.decode(clean, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (_: Exception) {
            null
        }
    }
}
