package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.BillEntity
import com.example.data.ChargeType
import com.example.data.CompanySettings
import com.example.data.DocumentType
import com.example.data.InvoiceItemJson
import com.example.data.PaymentMode
import com.example.data.UpiSettings
import com.example.ui.BillDraft
import com.example.ui.CustomerFolderSummary
import com.example.ui.components.bringIntoViewOnFocus
import com.example.ui.components.clearFocusAndHideToolbar
import com.example.ui.theme.GoldAccent
import com.example.ui.theme.GreenPaid
import com.example.ui.theme.GreenPaidBg
import com.example.ui.theme.NavyPrimary
import com.example.ui.theme.RedDue
import com.example.ui.theme.RedDueBg
import com.example.ui.theme.RedDueDark
import com.example.ui.theme.TealAccent
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SavedBillsScreen(
    allBills: List<BillEntity>,
    filteredBills: List<BillEntity>,
    customerFolders: List<CustomerFolderSummary>,
    searchQuery: String,
    company: CompanySettings,
    upi: UpiSettings,
    isAdmin: Boolean,
    isSuperAdmin: Boolean = false,
    onSearchChange: (String) -> Unit,
    onOpenBill: (BillEntity) -> Unit,
    onPrintBill: (BillEntity) -> Unit,
    onSavePdf: (BillEntity) -> Unit,
    onSharePdf: (BillEntity) -> Unit,
    onSavePhoto: (BillEntity) -> Unit,
    onSharePhoto: (BillEntity) -> Unit,
    onDeleteBill: (String) -> Unit,
    onOpenPaymentReview: (BillEntity) -> Unit = {}
) {
    // 0 = All Bills, 1 = Customer Folders, 2 = Quotations
    var viewTab by remember { mutableIntStateOf(0) }
    var expandedFolderKey by remember { mutableStateOf<String?>(null) }
    var previewBill by remember { mutableStateOf<BillEntity?>(null) }

    val focusManager = LocalFocusManager.current
    val textToolbar = LocalTextToolbar.current

    val taxBillsOnly = remember(allBills) {
        allBills.filter { DocumentType.fromString(it.documentType) == DocumentType.BILL }
    }
    val quotationsOnly = remember(filteredBills) {
        filteredBills.filter { DocumentType.fromString(it.documentType) == DocumentType.QUOTATION }
    }
    val filteredTaxBills = remember(filteredBills) {
        filteredBills.filter { DocumentType.fromString(it.documentType) == DocumentType.BILL }
    }
    val filteredFolders = remember(customerFolders, searchQuery) {
        val q = searchQuery.trim().lowercase(Locale.ROOT)
        if (q.isEmpty()) customerFolders
        else customerFolders.filter { folder ->
            folder.customerName.lowercase(Locale.ROOT).contains(q) ||
                folder.phone.lowercase(Locale.ROOT).contains(q) ||
                folder.visitingTechnicians.any { it.lowercase(Locale.ROOT).contains(q) } ||
                folder.bills.any { it.invoiceNo.lowercase(Locale.ROOT).contains(q) }
        }
    }

    val dueBills = taxBillsOnly.filter { it.dueAmount > 0.009 }
    val totalDue = dueBills.sumOf { it.dueAmount }
    val settledCount = taxBillsOnly.size - dueBills.size

    val pulseTransition = rememberInfiniteTransition(label = "duePulse")
    val pulseAlpha by pulseTransition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Reverse),
        label = "pulseAlpha"
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { Spacer(Modifier.height(4.dp)) }

        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Saved Bills, Customer Folders & Quotations", style = MaterialTheme.typography.titleLarge)

                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = onSearchChange,
                        placeholder = { Text("Search invoice, customer, mobile or technician...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus().testTag("bill_search_input")
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = viewTab == 0,
                            onClick = { viewTab = 0 },
                            label = { Text("Bills (${filteredTaxBills.size})") },
                            modifier = Modifier.testTag("saved_tab_ALL")
                        )
                        FilterChip(
                            selected = viewTab == 1,
                            onClick = { viewTab = 1 },
                            label = { Text("Customer Folders (${filteredFolders.size})") },
                            leadingIcon = { Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            modifier = Modifier.testTag("saved_tab_FOLDERS")
                        )
                        FilterChip(
                            selected = viewTab == 2,
                            onClick = { viewTab = 2 },
                            label = { Text("Quotations (${quotationsOnly.size})") },
                            modifier = Modifier.testTag("saved_tab_QUOTATIONS")
                        )
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SummaryChip("Total Bills: ${taxBillsOnly.size}", NavyPrimary, Color(0xFFE1ECF4))
                        SummaryChip(
                            "Due Bills: ${dueBills.size} (₹${String.format(Locale.US, "%.2f", totalDue)})",
                            RedDueDark,
                            RedDueBg
                        )
                        SummaryChip("Settled: $settledCount", GreenPaid, GreenPaidBg)
                    }
                }
            }
        }

        when (viewTab) {
            1 -> {
                if (filteredFolders.isEmpty()) {
                    item { EmptySavedCard("No customer folders found yet.") }
                } else {
                    items(filteredFolders, key = { it.customerKey }) { folder ->
                        val isExpanded = expandedFolderKey == folder.customerKey
                        val folderHasDue = folder.totalDue > 0.009
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (folderHasDue) RedDueBg.copy(alpha = 0.45f) else MaterialTheme.colorScheme.surface
                            ),
                            border = BorderStroke(
                                width = if (folderHasDue) 1.6.dp else 1.dp,
                                color = if (folderHasDue) RedDue else MaterialTheme.colorScheme.outline
                            ),
                            modifier = Modifier.testTag("customer_folder_${folder.customerKey}")
                        ) {
                            Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            expandedFolderKey = if (isExpanded) null else folder.customerKey
                                        },
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        modifier = Modifier.weight(1f),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Folder,
                                            contentDescription = null,
                                            tint = if (folderHasDue) RedDueDark else TealAccent,
                                            modifier = Modifier.size(30.dp)
                                        )
                                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                            Text(
                                                text = folder.customerName,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = NavyPrimary
                                            )
                                            if (folder.phone.isNotBlank()) {
                                                Text("Mobile: ${folder.phone}", style = MaterialTheme.typography.bodySmall, color = TealAccent)
                                            }
                                            Text(
                                                text = "Paid: ${folder.paidCount} · Due: ${folder.dueCount} · Quotations: ${folder.quotations.size}",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            if (folder.visitingTechnicians.isNotEmpty()) {
                                                Text(
                                                    text = "Technicians: ${folder.visitingTechnicians.joinToString(", ") { "@$it" }}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = NavyPrimary,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            }
                                        }
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        if (folderHasDue) {
                                            Surface(color = RedDue, shape = RoundedCornerShape(999.dp)) {
                                                Text(
                                                    text = "DUE ₹${String.format(Locale.US, "%.0f", folder.totalDue)}",
                                                    color = Color.White,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                )
                                            }
                                        } else {
                                            Surface(color = GreenPaid, shape = RoundedCornerShape(999.dp)) {
                                                Text(
                                                    text = "CLEAR",
                                                    color = Color.White,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                )
                                            }
                                        }
                                        Icon(
                                            if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                            contentDescription = "Expand folder",
                                            tint = NavyPrimary
                                        )
                                    }
                                }

                                AnimatedVisibility(visible = isExpanded) {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        HorizontalDivider()
                                        (folder.bills + folder.quotations).forEach { doc ->
                                            SavedDocCard(
                                                bill = doc,
                                                pulseAlpha = pulseAlpha,
                                                isAdmin = isAdmin,
                                                isSuperAdmin = isSuperAdmin,
                                                onPreview = {
                                                    clearFocusAndHideToolbar(focusManager, textToolbar)
                                                    previewBill = doc
                                                },
                                                onPrintBill = { onPrintBill(doc) },
                                                onSavePdf = { onSavePdf(doc) },
                                                onSharePdf = { onSharePdf(doc) },
                                                onSavePhoto = { onSavePhoto(doc) },
                                                onSharePhoto = { onSharePhoto(doc) },
                                                onOpenPaymentReview = { onOpenPaymentReview(doc) },
                                                onOpenBill = { onOpenBill(doc) },
                                                onDeleteBill = { onDeleteBill(doc.id) }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            2 -> {
                if (quotationsOnly.isEmpty()) {
                    item { EmptySavedCard("No saved quotations yet.") }
                } else {
                    items(quotationsOnly, key = { it.id }) { doc ->
                        SavedDocCard(
                            bill = doc,
                            pulseAlpha = pulseAlpha,
                            isAdmin = isAdmin,
                            isSuperAdmin = isSuperAdmin,
                            onPreview = {
                                clearFocusAndHideToolbar(focusManager, textToolbar)
                                previewBill = doc
                            },
                            onPrintBill = { onPrintBill(doc) },
                            onSavePdf = { onSavePdf(doc) },
                            onSharePdf = { onSharePdf(doc) },
                            onSavePhoto = { onSavePhoto(doc) },
                            onSharePhoto = { onSharePhoto(doc) },
                            onOpenPaymentReview = { onOpenPaymentReview(doc) },
                            onOpenBill = { onOpenBill(doc) },
                            onDeleteBill = { onDeleteBill(doc.id) }
                        )
                    }
                }
            }

            else -> {
                if (filteredTaxBills.isEmpty()) {
                    item { EmptySavedCard("No saved bills found.") }
                } else {
                    items(filteredTaxBills, key = { it.id }) { doc ->
                        SavedDocCard(
                            bill = doc,
                            pulseAlpha = pulseAlpha,
                            isAdmin = isAdmin,
                            isSuperAdmin = isSuperAdmin,
                            onPreview = {
                                clearFocusAndHideToolbar(focusManager, textToolbar)
                                previewBill = doc
                            },
                            onPrintBill = { onPrintBill(doc) },
                            onSavePdf = { onSavePdf(doc) },
                            onSharePdf = { onSharePdf(doc) },
                            onSavePhoto = { onSavePhoto(doc) },
                            onSharePhoto = { onSharePhoto(doc) },
                            onOpenPaymentReview = { onOpenPaymentReview(doc) },
                            onOpenBill = { onOpenBill(doc) },
                            onDeleteBill = { onDeleteBill(doc.id) }
                        )
                    }
                }
            }
        }

        item { Spacer(Modifier.height(16.dp)) }
    }

    val billToPreview = previewBill
    if (billToPreview != null) {
        val previewDraft = remember(billToPreview) {
            BillDraft(
                editingId = billToPreview.id,
                createdBy = billToPreview.createdBy,
                documentType = DocumentType.fromString(billToPreview.documentType),
                isHelpBill = billToPreview.isHelpBill,
                helpRequestId = billToPreview.helpRequestId,
                quotationNote = billToPreview.quotationNote,
                customerName = billToPreview.customerName,
                customerDetails = billToPreview.customerDetails,
                invoiceNo = billToPreview.invoiceNo,
                date = billToPreview.date,
                items = InvoiceItemJson.fromJson(billToPreview.itemsJson),
                extraChargeType = ChargeType.fromString(billToPreview.extraChargeType),
                extraChargeAmountText = if (billToPreview.extraChargeAmount == 0.0) "0" else billToPreview.extraChargeAmount.toString(),
                discountText = billToPreview.discount.toString(),
                paidAmountText = billToPreview.paidAmount.toString(),
                paymentMode = PaymentMode.fromString(billToPreview.paymentMode),
                signatureBase64 = billToPreview.signatureBase64,
                customQrText = billToPreview.customQrText
            )
        }
        AlertDialog(
            onDismissRequest = {
                clearFocusAndHideToolbar(focusManager, textToolbar)
                previewBill = null
            },
            title = { Text("A4 Preview (${billToPreview.invoiceNo})") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    InvoiceSheetPreview(
                        draft = previewDraft,
                        company = company,
                        upi = upi
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    clearFocusAndHideToolbar(focusManager, textToolbar)
                    onPrintBill(billToPreview)
                    previewBill = null
                }) {
                    Text("Print A4")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    clearFocusAndHideToolbar(focusManager, textToolbar)
                    previewBill = null
                }) {
                    Text("Close")
                }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SavedDocCard(
    bill: BillEntity,
    pulseAlpha: Float,
    isAdmin: Boolean,
    isSuperAdmin: Boolean,
    onPreview: () -> Unit,
    onPrintBill: () -> Unit,
    onSavePdf: () -> Unit,
    onSharePdf: () -> Unit,
    onSavePhoto: () -> Unit,
    onSharePhoto: () -> Unit,
    onOpenPaymentReview: () -> Unit,
    onOpenBill: () -> Unit,
    onDeleteBill: () -> Unit
) {
    val isQuotation = DocumentType.fromString(bill.documentType) == DocumentType.QUOTATION
    val hasDue = !isQuotation && bill.dueAmount > 0.009
    val cardBg by animateColorAsState(
        targetValue = if (hasDue) RedDueBg.copy(alpha = 0.65f) else MaterialTheme.colorScheme.surface,
        label = "billCardBg"
    )
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = if (hasDue) {
            BorderStroke(2.dp, RedDue.copy(alpha = pulseAlpha))
        } else {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        }
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (isQuotation) {
                            Surface(color = NavyPrimary, shape = RoundedCornerShape(6.dp)) {
                                Text(
                                    text = "QUOTATION",
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        if (bill.isHelpBill && !isQuotation) {
                            Surface(color = Color(0xFF0B4F6C), shape = RoundedCornerShape(6.dp)) {
                                Text(
                                    text = "SUPPORT BILL #${bill.helpRequestId}",
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Text(
                        text = "${bill.invoiceNo} — ${bill.customerName.ifBlank { "Walk-in Customer" }}",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (hasDue) RedDueDark else NavyPrimary
                    )
                    Text(
                        text = "${bill.date} · ${bill.paymentMode}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (bill.createdBy.isNotBlank()) {
                        Text(
                            text = "Technician: @${bill.createdBy}",
                            style = MaterialTheme.typography.labelMedium,
                            color = TealAccent,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.testTag("saved_bill_creator_${bill.invoiceNo}")
                        )
                    }
                }

                if (!isQuotation) {
                    Surface(
                        color = if (hasDue) RedDue else GreenPaid,
                        shape = RoundedCornerShape(999.dp)
                    ) {
                        Text(
                            text = if (hasDue) {
                                "DUE: ₹${String.format(Locale.US, "%.2f", bill.dueAmount)}"
                            } else {
                                "PAID IN FULL"
                            },
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Text(
                text = if (isQuotation) {
                    "Quotation Total: ₹${String.format(Locale.US, "%.2f", bill.grandTotal)}"
                } else buildString {
                    append("Grand Total: ₹${String.format(Locale.US, "%.2f", bill.grandTotal)}")
                    if (bill.discount > 0.009) {
                        append(" · Disc: ₹${String.format(Locale.US, "%.2f", bill.discount)}")
                    }
                    append(" · Paid: ₹${String.format(Locale.US, "%.2f", bill.paidAmount)}")
                },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )

            if (!isQuotation && (bill.staffRating > 0 || bill.staffReviewNote.isNotBlank())) {
                Surface(
                    color = Color(0xFFFFF8E1),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, GoldAccent.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth().testTag("saved_bill_review_${bill.invoiceNo}")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (bill.staffRating > 0) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Star,
                                    contentDescription = null,
                                    tint = GoldAccent,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(2.dp))
                                Text(
                                    text = "${bill.staffRating}/5",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Black,
                                    color = NavyPrimary
                                )
                                Spacer(Modifier.width(4.dp))
                                Icon(
                                    Icons.Default.Lock,
                                    contentDescription = "Rating Locked",
                                    tint = NavyPrimary,
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                        }
                        if (bill.staffReviewNote.isNotBlank()) {
                            Text(
                                text = "“${bill.staffReviewNote}”",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF334155),
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (bill.staffRating > 0) {
                            Text(
                                text = "Locked",
                                style = MaterialTheme.typography.labelSmall,
                                color = NavyPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onPreview,
                    modifier = Modifier.testTag("preview_saved_${bill.invoiceNo}")
                ) {
                    Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Preview")
                }

                OutlinedButton(
                    onClick = onPrintBill,
                    modifier = Modifier.testTag("print_saved_${bill.invoiceNo}")
                ) {
                    Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Print")
                }

                OutlinedButton(
                    onClick = onSavePdf,
                    modifier = Modifier.testTag("save_pdf_saved_${bill.invoiceNo}")
                ) {
                    Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("PDF")
                }

                OutlinedButton(
                    onClick = onSharePdf,
                    modifier = Modifier.testTag("share_pdf_saved_${bill.invoiceNo}")
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Share PDF")
                }

                OutlinedButton(
                    onClick = onSavePhoto,
                    modifier = Modifier.testTag("save_photo_saved_${bill.invoiceNo}")
                ) {
                    Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Photo")
                }

                OutlinedButton(
                    onClick = onSharePhoto,
                    modifier = Modifier.testTag("share_photo_saved_${bill.invoiceNo}")
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Share Photo")
                }

                if (isAdmin && !isSuperAdmin) {
                    if (!isQuotation) {
                        Button(
                            onClick = onOpenPaymentReview,
                            colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary),
                            modifier = Modifier.testTag("review_bill_${bill.invoiceNo}")
                        ) {
                            Icon(Icons.Default.Payments, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(if (bill.staffRating > 0) "Pay / View ★" else "Pay / 5★")
                        }
                    }

                    Button(
                        onClick = onOpenBill,
                        colors = ButtonDefaults.buttonColors(containerColor = TealAccent),
                        modifier = Modifier.testTag("open_saved_${bill.invoiceNo}")
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Open / Edit")
                    }

                    if (bill.staffRating == 0) {
                        Button(
                            onClick = onDeleteBill,
                            colors = ButtonDefaults.buttonColors(containerColor = RedDue),
                            modifier = Modifier.testTag("delete_saved_${bill.invoiceNo}")
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Delete")
                        }
                    }
                } else {
                    Text(
                        text = if (isSuperAdmin) "View only (Super Admin)" else "View only — Admin can edit or delete",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.CenterVertically)
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptySavedCard(message: String) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                Icons.Default.ReceiptLong,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(message, style = MaterialTheme.typography.titleMedium)
            Text(
                "Generate and save bills or quotations in New Bill to view them organized here.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SummaryChip(text: String, textColor: Color, bgColor: Color) {
    Surface(color = bgColor, shape = RoundedCornerShape(999.dp)) {
        Text(
            text = text,
            color = textColor,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}
