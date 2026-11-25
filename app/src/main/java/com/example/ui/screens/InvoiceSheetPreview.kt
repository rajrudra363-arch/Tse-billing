package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.CompanySettings
import com.example.data.DocumentType
import com.example.data.PaymentMode
import com.example.data.UpiSettings
import com.example.ui.BillDraft
import com.example.ui.components.QrCodeImage
import com.example.ui.components.TseShieldLogo
import com.example.ui.theme.NavyPrimary
import com.example.ui.theme.TealAccent
import com.example.util.InvoicePdfPrinter
import com.example.util.QrGenerator
import com.example.util.RupeesWords
import java.util.Locale

@Composable
fun InvoiceSheetPreview(
    draft: BillDraft,
    company: CompanySettings,
    upi: UpiSettings,
    modifier: Modifier = Modifier
) {
    val uriHandler = LocalUriHandler.current
    val isQuotation = draft.documentType == DocumentType.QUOTATION
    val waUri = remember(company.mobile) { QrGenerator.buildWhatsAppSupportUri(company.mobile) }

    // Printed A4 & sheet preview UPI QR never embeds an amount (amount-free static business UPI QR)
    val upiUri = remember(upi.upiId, upi.payeeName, draft.invoiceNo) {
        if (upi.upiId.isNotBlank()) QrGenerator.buildUpiUri(upi.upiId, upi.payeeName, 0.0, draft.invoiceNo)
        else ""
    }
    val authSignatureBmp = remember(company.authorisedSignatureBase64) {
        InvoicePdfPrinter.decodeBase64Bitmap(company.authorisedSignatureBase64)
    }

    val sheetBorder = Color(0xFF111111)
    val softFill = Color(0xFFF6F9FB)
    val headerFill = Color(0xFFEDF4F7)
    val helpBannerFill = Color(0xFFE0F2FE)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.White)
            .border(1.5.dp, sheetBorder)
            .padding(8.dp)
    ) {
        // 1. TOP HEADER
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .border(1.2.dp, sheetBorder)
        ) {
            Row(
                modifier = Modifier
                    .weight(if (isQuotation) 1f else 2f)
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TseShieldLogo(logoBase64 = company.logoBase64, modifier = Modifier.size(48.dp))
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = company.companyName.uppercase(Locale.ROOT),
                        color = NavyPrimary,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Black,
                        lineHeight = 22.sp
                    )
                    Text(
                        text = stringResource(R.string.company_tagline),
                        color = TealAccent,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Mob/WA: ${company.mobile} | ${company.email}",
                        color = Color.Black,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Office Address: ${company.address}",
                        color = Color(0xFF222222),
                        fontSize = 8.5.sp,
                        lineHeight = 11.sp
                    )
                }
            }

            if (!isQuotation) {
                VerticalDivider(color = sheetBorder, thickness = 1.2.dp)

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { runCatching { uriHandler.openUri(waUri) } }
                        .padding(6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Customer Care & Support",
                        color = NavyPrimary,
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(3.dp))
                    QrCodeImage(
                        content = waUri,
                        contentDescription = "Customer Care WhatsApp QR",
                        modifier = Modifier.size(58.dp)
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "Scan to WhatsApp us\n${company.mobile}",
                        color = Color(0xFF222222),
                        fontSize = 8.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 10.sp
                    )
                }
            }
        }

        Spacer(Modifier.height(5.dp))

        // 2. DOCUMENT BANNER
        val bannerText = when {
            isQuotation -> "QUOTATION"
            draft.isHelpBill -> "CUSTOMER CARE & SUPPORT SERVICE INVOICE"
            else -> "TAX INVOICE"
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(if (draft.isHelpBill && !isQuotation) helpBannerFill else softFill)
                .border(1.5.dp, sheetBorder)
                .padding(2.dp)
                .border(0.8.dp, sheetBorder)
                .padding(vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = bannerText,
                color = if (draft.isHelpBill && !isQuotation) Color(0xFF0B4F6C) else Color.Black,
                fontSize = if (draft.isHelpBill && !isQuotation) 10.5.sp else 12.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.4.sp,
                textAlign = TextAlign.Center
            )
        }

        Spacer(Modifier.height(5.dp))

        // 3. META ROW
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .border(1.2.dp, sheetBorder)
        ) {
            Column(
                modifier = Modifier
                    .weight(1.35f)
                    .padding(6.dp)
            ) {
                Text("M/S :", fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                Text(
                    text = buildString {
                        append(draft.customerName.ifBlank { "Walk-in Customer" })
                        if (draft.customerDetails.isNotBlank()) append("\n").append(draft.customerDetails)
                    },
                    fontSize = 9.5.sp,
                    color = Color.Black,
                    lineHeight = 12.sp
                )
            }
            VerticalDivider(color = sheetBorder, thickness = 1.2.dp)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(6.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text("DATE: ${draft.date}", fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                Text(
                    text = if (isQuotation) "QUOTATION NO.: ${draft.invoiceNo}" else "INVOICE NO.: ${draft.invoiceNo}",
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
                if (draft.isHelpBill && draft.helpRequestId > 0L && !isQuotation) {
                    Text(
                        text = "Support Ticket #${draft.helpRequestId}",
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        color = TealAccent
                    )
                }
            }
        }

        Spacer(Modifier.height(5.dp))

        // 4. ITEMS TABLE
        Column(modifier = Modifier.fillMaxWidth().border(1.2.dp, sheetBorder)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(headerFill)
                    .padding(vertical = 4.dp, horizontal = 3.dp)
            ) {
                TableCell("Sr", 0.55f, bold = true, align = TextAlign.Center)
                TableCell("Description", 2.5f, bold = true)
                TableCell("Qty", 0.65f, bold = true, align = TextAlign.Center)
                TableCell("Unit", 0.7f, bold = true, align = TextAlign.Center)
                TableCell("Rate", 1.0f, bold = true, align = TextAlign.End)
                TableCell("Amount", 1.1f, bold = true, align = TextAlign.End)
            }
            HorizontalDivider(color = sheetBorder, thickness = 1.dp)
            val previewItems = remember(
                draft.items,
                draft.extraChargeType,
                draft.extraChargeAmount,
                draft.secondaryChargeType,
                draft.secondaryChargeAmount
            ) {
                InvoicePdfPrinter.buildEffectivePrintItems(
                    draft.items,
                    draft.extraChargeType,
                    draft.extraChargeAmount,
                    draft.secondaryChargeType,
                    draft.secondaryChargeAmount
                )
            }
            if (previewItems.isEmpty()) {
                Text(
                    text = "No filled products yet — only filled items will print.",
                    fontSize = 8.5.sp,
                    color = Color(0xFF64748B),
                    modifier = Modifier.padding(8.dp)
                )
            } else {
                previewItems.forEachIndexed { idx, item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp, horizontal = 3.dp)
                    ) {
                        TableCell("${idx + 1}", 0.55f, align = TextAlign.Center)
                        TableCell(item.description, 2.5f)
                        val qtyStr = if (item.qty % 1.0 == 0.0) item.qty.toInt().toString() else String.format(Locale.US, "%.1f", item.qty)
                        TableCell(qtyStr, 0.65f, align = TextAlign.Center)
                        TableCell(item.unit.ifBlank { "Nos" }, 0.7f, align = TextAlign.Center)
                        TableCell(String.format(Locale.US, "%.2f", item.rate), 1.0f, align = TextAlign.End)
                        TableCell(String.format(Locale.US, "%.2f", item.amount), 1.1f, bold = true, align = TextAlign.End)
                    }
                    HorizontalDivider(color = Color(0xFFD5DCE0), thickness = 0.7.dp)
                }
            }
        }

        if (isQuotation) {
            // QUOTATION SUMMARY (No Payment Mode / QR / Customer Info QR / Customer Signature)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
                    .border(1.2.dp, sheetBorder)
            ) {
                Column(
                    modifier = Modifier
                        .weight(1.15f)
                        .padding(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("Estimated Amount in Words:", fontSize = 8.5.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                    Text(
                        text = RupeesWords.convert(draft.grandTotal),
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF111111),
                        lineHeight = 11.sp
                    )
                }
                VerticalDivider(color = sheetBorder, thickness = 1.2.dp)
                Column(modifier = Modifier.weight(0.95f)) {
                    SummaryLine("SUB TOTAL", draft.subtotal)
                    SummaryLine("DISCOUNT", draft.discount)
                    SummaryLine("QUOTATION TOTAL", draft.grandTotal, bg = headerFill, bold = true, isLast = true)
                }
            }

            // Note for Customer + Authorised Signatory on Right
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
                    .border(1.2.dp, sheetBorder)
            ) {
                Column(
                    modifier = Modifier
                        .weight(1.65f)
                        .fillMaxHeight()
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Note for Customer:",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = NavyPrimary
                    )
                    Text(
                        text = draft.quotationNote.ifBlank { stringResource(R.string.quotation_customer_note) },
                        fontSize = 8.2.sp,
                        color = Color(0xFF1E293B),
                        lineHeight = 11.sp
                    )
                }
                VerticalDivider(color = sheetBorder, thickness = 1.dp)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom
                ) {
                    if (authSignatureBmp != null) {
                        Image(
                            bitmap = authSignatureBmp.asImageBitmap(),
                            contentDescription = "Authorised Signatory",
                            modifier = Modifier.height(38.dp).fillMaxWidth(),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        Spacer(Modifier.height(38.dp))
                    }
                    HorizontalDivider(color = sheetBorder, thickness = 1.dp)
                    Spacer(Modifier.height(2.dp))
                    Text("Authorised Signatory", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Color.Black, textAlign = TextAlign.Center)
                    Text(
                        company.companyName,
                        fontSize = 7.sp,
                        fontWeight = FontWeight.Bold,
                        color = NavyPrimary,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Product Disclaimer
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(softFill)
                    .border(1.2.dp, sheetBorder)
                    .padding(horizontal = 8.dp, vertical = 5.dp)
            ) {
                Text(
                    text = "Product Disclaimer: This quotation is valid for 15 days. ${stringResource(R.string.product_disclaimer_text)}",
                    fontSize = 7.3.sp,
                    color = Color(0xFF334155),
                    textAlign = TextAlign.Center,
                    lineHeight = 10.sp
                )
            }
        } else {
            // TAX INVOICE / CUSTOMER CARE SUPPORT BILL
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
                    .border(1.2.dp, sheetBorder)
            ) {
                Column(
                    modifier = Modifier
                        .weight(1.15f)
                        .padding(6.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text("Rupees (Amount in Words):", fontSize = 8.5.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                    Text(RupeesWords.convert(draft.grandTotal), fontSize = 8.5.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF111111), lineHeight = 11.sp)
                    Text(
                        text = "Mode: ${if (draft.paymentMode == PaymentMode.ONLINE) "● Online  ○ Cash" else "○ Online  ● Cash"}",
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        QrCodeImage(
                            content = upiUri,
                            fallbackBase64 = upi.fallbackQrBase64,
                            contentDescription = "Online UPI Payment QR",
                            modifier = Modifier.size(62.dp)
                        )
                        Column {
                            Text("Online QR Payment :-", fontSize = 8.5.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                            if (upi.upiId.isNotBlank()) {
                                Text("UPI: ${upi.upiId}", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = NavyPrimary)
                            }
                            Text("A/c: ${upi.payeeName}", fontSize = 8.sp, color = Color(0xFF222222))
                        }
                    }
                }

                VerticalDivider(color = sheetBorder, thickness = 1.2.dp)

                val hasDue = draft.dueAmount > 0.009
                Column(modifier = Modifier.weight(0.95f)) {
                    SummaryLine("SUB TOTAL", draft.subtotal)
                    SummaryLine("DISCOUNT", draft.discount)
                    SummaryLine("GRAND TOTAL", draft.grandTotal, bg = headerFill, bold = true)
                    SummaryLine("PAID AMOUNT", draft.paidAmount)
                    SummaryLine(
                        label = "DUE BALANCE",
                        value = draft.dueAmount,
                        bg = if (hasDue) Color(0xFFFEF2F2) else Color(0xFFECFDF5),
                        textColor = if (hasDue) Color(0xFF991B1B) else Color(0xFF065F46),
                        bold = true,
                        isLast = true
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
                    .border(1.2.dp, sheetBorder)
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(34.dp)
                            .border(0.8.dp, sheetBorder)
                    )
                    Spacer(Modifier.height(3.dp))
                    HorizontalDivider(color = sheetBorder, thickness = 1.dp)
                    Spacer(Modifier.height(2.dp))
                    Text("Customer Signature", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Color.Black, textAlign = TextAlign.Center)
                }

                VerticalDivider(color = sheetBorder, thickness = 1.dp)

                Column(
                    modifier = Modifier
                        .weight(1.1f)
                        .fillMaxHeight()
                        .padding(6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("Customer Info QR", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = NavyPrimary)
                    Spacer(Modifier.height(3.dp))
                    if (draft.customQrText.isNotBlank()) {
                        QrCodeImage(
                            content = draft.customQrText,
                            contentDescription = "Customer Information QR",
                            modifier = Modifier.size(48.dp)
                        )
                    } else {
                        Text("No extra info QR", fontSize = 7.5.sp, color = Color(0xFF64748B), textAlign = TextAlign.Center)
                    }
                }

                VerticalDivider(color = sheetBorder, thickness = 1.dp)

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom
                ) {
                    if (authSignatureBmp != null) {
                        Image(
                            bitmap = authSignatureBmp.asImageBitmap(),
                            contentDescription = "Authorised Signatory",
                            modifier = Modifier.height(34.dp).fillMaxWidth(),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        Spacer(Modifier.height(34.dp))
                    }
                    HorizontalDivider(color = sheetBorder, thickness = 1.dp)
                    Spacer(Modifier.height(2.dp))
                    Text("Authorised Signatory", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Color.Black, textAlign = TextAlign.Center)
                    Text(
                        company.companyName,
                        fontSize = 7.sp,
                        fontWeight = FontWeight.Bold,
                        color = NavyPrimary,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(softFill)
                    .border(1.2.dp, sheetBorder)
                    .padding(horizontal = 8.dp, vertical = 5.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Thank you for choosing ${company.companyName}!",
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = NavyPrimary,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = if (draft.isHelpBill) {
                        "Thank you for trusting our Customer Care & Support team! Contact us anytime for technical assistance and maintenance."
                    } else {
                        stringResource(R.string.customer_appreciation_body)
                    },
                    fontSize = 7.8.sp,
                    color = NavyPrimary,
                    textAlign = TextAlign.Center,
                    lineHeight = 10.sp
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .border(1.2.dp, sheetBorder)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "Product Disclaimer: ${stringResource(R.string.product_disclaimer_text)}",
                    fontSize = 7.2.sp,
                    color = Color(0xFF334155),
                    textAlign = TextAlign.Center,
                    lineHeight = 9.5.sp
                )
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.TableCell(
    text: String,
    weight: Float,
    bold: Boolean = false,
    align: TextAlign = TextAlign.Start
) {
    Text(
        text = text,
        modifier = Modifier.weight(weight).padding(horizontal = 2.dp),
        fontSize = 8.5.sp,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        color = Color.Black,
        textAlign = align,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun SummaryLine(
    label: String,
    value: Double,
    bg: Color = Color.White,
    textColor: Color = Color.Black,
    bold: Boolean = false,
    isLast: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bg)
            .padding(horizontal = 6.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 8.5.sp,
            fontWeight = FontWeight.Bold,
            color = textColor
        )
        Text(
            text = "₹${String.format(Locale.US, "%.2f", value)}",
            fontSize = 9.sp,
            fontWeight = if (bold) FontWeight.Black else FontWeight.Bold,
            color = textColor
        )
    }
    if (!isLast) {
        HorizontalDivider(color = Color(0xFF222222), thickness = 0.8.dp)
    }
}
