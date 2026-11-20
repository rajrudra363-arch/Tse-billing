package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.data.AppLanguage
import com.example.data.CompanySettings
import com.example.data.CustomerEntity
import com.example.data.InvoiceItem
import com.example.data.PaymentMode
import com.example.data.ProductEntity
import com.example.data.UpiSettings
import com.example.data.UserSession
import com.example.ui.BillDraft
import com.example.ui.components.QrCodeImage
import com.example.ui.components.SignaturePadBox
import com.example.ui.theme.AmberWarn
import com.example.ui.theme.GreenPaid
import com.example.ui.theme.NavyPrimary
import com.example.ui.theme.RedDue
import com.example.ui.theme.RedDueBg
import com.example.ui.theme.RedDueDark
import com.example.ui.theme.TealAccent
import com.example.util.InvoicePrintPayload
import com.example.util.QrGenerator
import com.example.util.RupeesWords
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BillScreen(
    draft: BillDraft,
    products: List<ProductEntity>,
    customers: List<CustomerEntity>,
    company: CompanySettings,
    upi: UpiSettings,
    language: AppLanguage,
    currentUser: UserSession,
    finalModalVisible: Boolean,
    finalModalError: String,
    exportOptionsPayload: InvoicePrintPayload?,
    onCustomerNameChange: (String) -> Unit,
    onCustomerSelected: (CustomerEntity) -> Unit,
    onCustomerDetailsChange: (String) -> Unit,
    onInvoiceNoChange: (String) -> Unit,
    onInvoiceDateChange: (String) -> Unit,
    onAddItem: () -> Unit,
    onSelectProductForRow: (Int, ProductEntity) -> Unit,
    onUpdateItem: (Int, String?, Double?, String?, Double?) -> Unit,
    onRemoveItem: (Int) -> Unit,
    onDiscountChange: (String) -> Unit,
    onPaidAmountChange: (String) -> Unit,
    onPaymentModeChange: (PaymentMode) -> Unit,
    onSignatureChanged: (android.graphics.Bitmap?) -> Unit,
    onCustomQrTextChange: (String) -> Unit,
    onToggleSheetPreview: (Boolean) -> Unit,
    onResetBill: () -> Unit,
    onSaveBill: () -> Unit,
    onOpenFinalCodeModal: () -> Unit,
    onCloseFinalCodeModal: () -> Unit,
    onConfirmFinalBill: (String) -> Unit,
    onDismissExportOptions: () -> Unit,
    onPrintA4: (InvoicePrintPayload) -> Unit,
    onSavePdf: (InvoicePrintPayload) -> Unit,
    onSharePdf: (InvoicePrintPayload) -> Unit
) {
    val isAdmin = currentUser.isAdmin && !currentUser.isStaff
    val isHi = language == AppLanguage.HI
    var customerDropdownOpen by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Spacer(Modifier.height(4.dp)) }

        // Action Bar Card
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = when {
                                    draft.editingId != null -> if (isHi) "बिल एडिट करें" else "Edit Invoice"
                                    isHi -> "नया बिल बनाएं"
                                    else -> "Create Invoice"
                                },
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Bill by: ${currentUser.displayName} (@${currentUser.username}) · ${if (isAdmin) "Admin" else "Staff"}",
                                style = MaterialTheme.typography.labelMedium,
                                color = TealAccent,
                                modifier = Modifier.testTag("bill_creator_badge")
                            )
                        }
                        FilterChip(
                            selected = draft.showSheetPreview,
                            onClick = { onToggleSheetPreview(!draft.showSheetPreview) },
                            label = { Text(if (draft.showSheetPreview) "Hide A4 Preview" else "A4 Preview") },
                            leadingIcon = {
                                Icon(
                                    if (draft.showSheetPreview) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            modifier = Modifier.testTag("toggle_sheet_preview")
                        )
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onAddItem,
                            colors = ButtonDefaults.buttonColors(containerColor = GreenPaid),
                            modifier = Modifier.testTag("add_item_button")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text(if (isHi) "आइटम (${draft.items.size}/11)" else "Add Item (${draft.items.size}/11)")
                        }

                        Button(
                            onClick = onResetBill,
                            colors = ButtonDefaults.buttonColors(containerColor = AmberWarn),
                            modifier = Modifier.testTag("new_bill_button")
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text(if (isHi) "नया बिल" else "New Bill")
                        }

                        Button(
                            onClick = onSaveBill,
                            colors = ButtonDefaults.buttonColors(containerColor = TealAccent),
                            modifier = Modifier.testTag("save_bill_button")
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text(if (isHi) "सेव करें" else "Save Bill")
                        }

                        Button(
                            onClick = onOpenFinalCodeModal,
                            colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary),
                            modifier = Modifier.testTag("final_print_button")
                        ) {
                            Icon(Icons.Default.Print, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text(if (isHi) "प्रिंट / PDF (A4)" else "Print / PDF (A4)")
                        }
                    }
                }
            }
        }

        // Toggleable Live A4 Sheet Preview
        item {
            AnimatedVisibility(
                visible = draft.showSheetPreview,
                enter = expandVertically(spring(stiffness = Spring.StiffnessMediumLow)) + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "LIVE A4 INVOICE SHEET PREVIEW",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    InvoiceSheetPreview(
                        draft = draft,
                        company = company,
                        upi = upi
                    )
                }
            }
        }

        // Customer & Invoice Metadata Card
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = if (isHi) "ग्राहक और बिल विवरण" else "Customer & Invoice Details",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = draft.customerName,
                            onValueChange = onCustomerNameChange,
                            label = { Text(if (isHi) "ग्राहक का नाम" else "Customer Name") },
                            trailingIcon = {
                                if (customers.isNotEmpty()) {
                                    IconButton(onClick = { customerDropdownOpen = true }) {
                                        Icon(Icons.Default.ArrowDropDown, contentDescription = "Pick saved customer")
                                    }
                                }
                            },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("bill_customer_name")
                        )
                        DropdownMenu(
                            expanded = customerDropdownOpen,
                            onDismissRequest = { customerDropdownOpen = false }
                        ) {
                            customers.forEach { c ->
                                DropdownMenuItem(
                                    text = { Text("${c.name} — ${c.details}", maxLines = 1) },
                                    onClick = {
                                        onCustomerSelected(c)
                                        customerDropdownOpen = false
                                    }
                                )
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = draft.invoiceNo,
                            onValueChange = onInvoiceNoChange,
                            label = { Text(if (isHi) "बिल नंबर" else "Invoice No.") },
                            singleLine = true,
                            modifier = Modifier.weight(1f).testTag("bill_invoice_no")
                        )
                        OutlinedTextField(
                            value = draft.date,
                            onValueChange = onInvoiceDateChange,
                            label = { Text(if (isHi) "तारीख (YYYY-MM-DD)" else "Date (YYYY-MM-DD)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f).testTag("bill_invoice_date")
                        )
                    }

                    OutlinedTextField(
                        value = draft.customerDetails,
                        onValueChange = onCustomerDetailsChange,
                        label = { Text(if (isHi) "पता / मोबाइल नंबर" else "Customer Address / Mobile") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth().testTag("bill_customer_details")
                    )
                }
            }
        }

        // Line Items
        itemsIndexed(draft.items, key = { _, item -> item.localId }) { index, item ->
            InvoiceItemEditorCard(
                index = index,
                item = item,
                products = products,
                isAdmin = isAdmin,
                onSelectProduct = { prod -> onSelectProductForRow(index, prod) },
                onUpdate = { desc, qty, unit, rate -> onUpdateItem(index, desc, qty, unit, rate) },
                onDelete = { onRemoveItem(index) }
            )
        }

        // Totals + Payment Mode + Dynamic UPI QR Card
        item {
            val hasDue = draft.dueAmount > 0.009
            val qrAmt = if (hasDue) draft.dueAmount else draft.grandTotal
            val upiUri = remember(upi.upiId, upi.payeeName, qrAmt, draft.invoiceNo) {
                if (upi.upiId.isNotBlank()) QrGenerator.buildUpiUri(upi.upiId, upi.payeeName, qrAmt, draft.invoiceNo)
                else ""
            }

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = if (isHi) "कुल हिसाब और भुगतान" else "Totals & Payment Summary",
                        style = MaterialTheme.typography.titleMedium
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = draft.discountText,
                            onValueChange = onDiscountChange,
                            label = { Text("DISCOUNT (₹)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f).testTag("bill_discount_input")
                        )
                        OutlinedTextField(
                            value = draft.paidAmountText,
                            onValueChange = onPaidAmountChange,
                            label = { Text("PAID AMOUNT (₹)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f).testTag("bill_paid_input")
                        )
                    }

                    // Breakdown Table: Sub Total -> Discount -> Grand Total -> Paid Amount -> Due Amount
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                    ) {
                        TotalRow("SUB TOTAL", draft.subtotal)
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                        TotalRow("DISCOUNT", draft.discount)
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                        TotalRow(
                            label = "GRAND TOTAL",
                            amount = draft.grandTotal,
                            bg = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                            bold = true
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                        TotalRow("PAID AMOUNT", draft.paidAmount)
                        HorizontalDivider(color = if (hasDue) RedDue else MaterialTheme.colorScheme.outline)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(if (hasDue) RedDueBg else Color.Transparent)
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "DUE AMOUNT",
                                    fontWeight = FontWeight.Black,
                                    color = if (hasDue) RedDueDark else MaterialTheme.colorScheme.onSurface
                                )
                                if (hasDue) {
                                    Surface(
                                        color = RedDue,
                                        shape = RoundedCornerShape(999.dp)
                                    ) {
                                        Text(
                                            text = "DUE",
                                            color = Color.White,
                                            style = MaterialTheme.typography.labelSmall,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                            Text(
                                text = "₹${String.format(Locale.US, "%.2f", draft.dueAmount)}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Black,
                                color = if (hasDue) RedDueDark else GreenPaid
                            )
                        }
                    }

                    Text(
                        text = "In Words: ${RupeesWords.convert(draft.grandTotal)}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    HorizontalDivider()

                    // Payment Mode + Auto UPI QR
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = if (isHi) "भुगतान का तरीका" else "Payment Mode",
                                style = MaterialTheme.typography.labelMedium
                            )
                            PaymentMode.entries.forEach { mode ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(
                                        selected = draft.paymentMode == mode,
                                        onClick = { onPaymentModeChange(mode) }
                                    )
                                    Text(mode.label, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                            if (upi.upiId.isNotBlank()) {
                                Text(
                                    text = "UPI: ${upi.upiId}\nA/c: ${upi.payeeName}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = TealAccent
                                )
                            } else {
                                Text(
                                    text = "Set UPI ID in Admin Panel for auto QR",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        QrCodeImage(
                            content = upiUri,
                            fallbackBase64 = upi.fallbackQrBase64,
                            contentDescription = "Auto-generated UPI Payment QR",
                            modifier = Modifier.size(104.dp)
                        )
                    }
                }
            }
        }

        // Signature Pad + Customer Information QR Card
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    SignaturePadBox(
                        hasSavedSignature = draft.signatureBase64.isNotBlank(),
                        onSignatureChanged = onSignatureChanged
                    )

                    HorizontalDivider()

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Default.QrCode2, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Text(
                                    text = "Customer Information QR",
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                            OutlinedTextField(
                                value = draft.customQrText,
                                onValueChange = onCustomQrTextChange,
                                placeholder = { Text("Warranty, DVR login or site note...") },
                                minLines = 2,
                                modifier = Modifier.fillMaxWidth().testTag("custom_qr_input")
                            )
                        }
                        if (draft.customQrText.isNotBlank()) {
                            QrCodeImage(
                                content = draft.customQrText,
                                contentDescription = "Customer Information QR Preview",
                                modifier = Modifier.size(84.dp)
                            )
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(16.dp)) }
    }

    if (finalModalVisible) {
        FinalCodeDialog(
            currentUser = currentUser,
            error = finalModalError,
            onDismiss = onCloseFinalCodeModal,
            onConfirm = onConfirmFinalBill
        )
    }

    if (exportOptionsPayload != null) {
        ExportA4OptionsDialog(
            payload = exportOptionsPayload,
            currentUser = currentUser,
            onDismiss = onDismissExportOptions,
            onPreviewA4 = {
                onToggleSheetPreview(true)
                onDismissExportOptions()
            },
            onPrintA4 = { onPrintA4(exportOptionsPayload) },
            onSavePdf = { onSavePdf(exportOptionsPayload) },
            onSharePdf = { onSharePdf(exportOptionsPayload) }
        )
    }
}

@Composable
private fun InvoiceItemEditorCard(
    index: Int,
    item: InvoiceItem,
    products: List<ProductEntity>,
    isAdmin: Boolean,
    onSelectProduct: (ProductEntity) -> Unit,
    onUpdate: (String?, Double?, String?, Double?) -> Unit,
    onDelete: () -> Unit
) {
    var productMenuOpen by remember { mutableStateOf(false) }
    var qtyText by remember(item.localId, item.qty) {
        mutableStateOf(if (item.qty % 1.0 == 0.0) item.qty.toInt().toString() else item.qty.toString())
    }
    var rateText by remember(item.localId, item.rate) {
        mutableStateOf(if (item.rate == 0.0) "0" else if (item.rate % 1.0 == 0.0) item.rate.toInt().toString() else item.rate.toString())
    }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.Description, contentDescription = null, tint = TealAccent, modifier = Modifier.size(18.dp))
                    Text("Item #${index + 1}", fontWeight = FontWeight.Bold, color = NavyPrimary)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "₹${String.format(Locale.US, "%.2f", item.amount)}",
                        fontWeight = FontWeight.Black,
                        color = TealAccent
                    )
                    IconButton(onClick = onDelete, modifier = Modifier.testTag("delete_item_$index")) {
                        Icon(Icons.Default.Delete, contentDescription = "Remove item", tint = RedDue)
                    }
                }
            }

            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = { productMenuOpen = true },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = item.description.ifBlank { "Choose CCTV product..." },
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                        Icon(Icons.Default.ArrowDropDown, contentDescription = "Choose product")
                    }
                }
                DropdownMenu(
                    expanded = productMenuOpen,
                    onDismissRequest = { productMenuOpen = false },
                    modifier = Modifier.widthIn(max = 320.dp)
                ) {
                    products.forEach { prod ->
                        DropdownMenuItem(
                            text = {
                                Text("${prod.name} (${prod.unit} · ₹${prod.defaultRate.toInt()})")
                            },
                            onClick = {
                                onSelectProduct(prod)
                                productMenuOpen = false
                            }
                        )
                    }
                }
            }

            if (isAdmin) {
                OutlinedTextField(
                    value = item.description,
                    onValueChange = { onUpdate(it, null, null, null) },
                    label = { Text("Custom / Edit Description") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = qtyText,
                    onValueChange = {
                        qtyText = it
                        onUpdate(null, it.toDoubleOrNull() ?: 0.0, null, null)
                    },
                    label = { Text("Qty") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(0.8f)
                )
                OutlinedTextField(
                    value = item.unit,
                    onValueChange = { if (isAdmin) onUpdate(null, null, it, null) },
                    label = { Text("Unit") },
                    readOnly = !isAdmin,
                    enabled = isAdmin,
                    singleLine = true,
                    modifier = Modifier.weight(0.8f)
                )
                OutlinedTextField(
                    value = rateText,
                    onValueChange = {
                        if (isAdmin) {
                            rateText = it
                            onUpdate(null, null, null, it.toDoubleOrNull() ?: 0.0)
                        }
                    },
                    label = { Text(if (isAdmin) "Rate (₹)" else "Rate (Admin)") },
                    readOnly = !isAdmin,
                    enabled = isAdmin,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1.2f)
                )
            }
        }
    }
}

@Composable
private fun TotalRow(
    label: String,
    amount: Double,
    bg: Color = Color.Transparent,
    bold: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bg)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontWeight = if (bold) FontWeight.Black else FontWeight.SemiBold,
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = "₹${String.format(Locale.US, "%.2f", amount)}",
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

@Composable
private fun FinalCodeDialog(
    currentUser: UserSession,
    error: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var code by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Lock, contentDescription = null, tint = TealAccent) },
        title = { Text("Admin Final Bill Verification") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics(mergeDescendants = true) {}
                        .testTag("final_dialog_staff_info")
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "Generated By: ${currentUser.displayName} (@${currentUser.username})",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            text = "Role: ${if (currentUser.isAdmin) "Admin" else "Staff"} · Yeh username saved bill record me dikhega (bill print me nahi aayega).",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
                Text(
                    text = "Bill final karne aur A4 PDF print karne ke liye Admin ka Final Bill Code enter karein.",
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it },
                    label = { Text("Final Bill Code") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("final_code_input")
                )
                if (error.isNotEmpty()) {
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(code) },
                colors = ButtonDefaults.buttonColors(containerColor = GreenPaid),
                modifier = Modifier.testTag("confirm_final_bill_button")
            ) {
                Text("Verify & Print PDF")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun ExportA4OptionsDialog(
    payload: InvoicePrintPayload,
    currentUser: UserSession,
    onDismiss: () -> Unit,
    onPreviewA4: () -> Unit,
    onPrintA4: () -> Unit,
    onSavePdf: () -> Unit,
    onSharePdf: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = TealAccent) },
        title = { Text("A4 Bill Ready (${payload.invoiceNo})") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Saved by @${currentUser.username} (print/PDF par show nahi hoga). Pura bill exact A4 size page par tayyar hai:",
                    style = MaterialTheme.typography.bodyMedium
                )
                Button(
                    onClick = onPrintA4,
                    colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary),
                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("export_print_a4_button")
                ) {
                    Icon(Icons.Default.Print, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Print on A4 Paper", fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = onSavePdf,
                    colors = ButtonDefaults.buttonColors(containerColor = GreenPaid),
                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("export_save_pdf_button")
                ) {
                    Icon(Icons.Default.PictureAsPdf, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Save A4 PDF", fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = onSharePdf,
                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("export_share_pdf_button")
                ) {
                    Icon(Icons.Default.Share, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Share A4 PDF", fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = onPreviewA4,
                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("export_preview_a4_button")
                ) {
                    Icon(Icons.Default.Visibility, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("View A4 Preview", fontWeight = FontWeight.Bold)
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}
