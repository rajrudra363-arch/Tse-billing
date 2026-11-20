package com.example.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.BillEntity
import com.example.data.ChargeType
import com.example.data.CompanySettings
import com.example.data.InvoiceItemJson
import com.example.data.PaymentMode
import com.example.data.UpiSettings
import com.example.ui.BillDraft
import com.example.ui.components.clearFocusAndHideToolbar
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
    searchQuery: String,
    company: CompanySettings,
    upi: UpiSettings,
    isAdmin: Boolean,
    onSearchChange: (String) -> Unit,
    onOpenBill: (BillEntity) -> Unit,
    onPrintBill: (BillEntity) -> Unit,
    onSavePdf: (BillEntity) -> Unit,
    onSharePdf: (BillEntity) -> Unit,
    onDeleteBill: (String) -> Unit
) {
    val dueBills = allBills.filter { it.dueAmount > 0.009 }
    val totalDue = dueBills.sumOf { it.dueAmount }
    val settledCount = allBills.size - dueBills.size
    var previewBill by remember { mutableStateOf<BillEntity?>(null) }
    val focusManager = LocalFocusManager.current
    val textToolbar = LocalTextToolbar.current

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
                    Text("Saved Bills", style = MaterialTheme.typography.titleLarge)

                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = onSearchChange,
                        placeholder = { Text("Search invoice / customer / staff...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("bill_search_input")
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SummaryChip("Total: ${allBills.size}", NavyPrimary, Color(0xFFE1ECF4))
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

        if (filteredBills.isEmpty()) {
            item {
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
                        Text("No saved bills found", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Create and save a bill from the New Bill tab to see history here.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(filteredBills, key = { it.id }) { bill ->
                val hasDue = bill.dueAmount > 0.009
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
                            Column(modifier = Modifier.weight(1f)) {
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
                                        text = "Created by: @${bill.createdBy}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = TealAccent,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.testTag("saved_bill_creator_${bill.invoiceNo}")
                                    )
                                }
                            }
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

                        Text(
                            text = "Grand Total: ₹${String.format(Locale.US, "%.2f", bill.grandTotal)} · Paid: ₹${String.format(Locale.US, "%.2f", bill.paidAmount)}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    clearFocusAndHideToolbar(focusManager, textToolbar)
                                    previewBill = bill
                                },
                                modifier = Modifier.testTag("preview_saved_${bill.invoiceNo}")
                            ) {
                                Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Preview")
                            }

                            OutlinedButton(
                                onClick = {
                                    clearFocusAndHideToolbar(focusManager, textToolbar)
                                    onPrintBill(bill)
                                },
                                modifier = Modifier.testTag("print_saved_${bill.invoiceNo}")
                            ) {
                                Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Print A4")
                            }

                            OutlinedButton(
                                onClick = {
                                    clearFocusAndHideToolbar(focusManager, textToolbar)
                                    onSavePdf(bill)
                                },
                                modifier = Modifier.testTag("save_pdf_saved_${bill.invoiceNo}")
                            ) {
                                Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Save PDF")
                            }

                            OutlinedButton(
                                onClick = {
                                    clearFocusAndHideToolbar(focusManager, textToolbar)
                                    onSharePdf(bill)
                                },
                                modifier = Modifier.testTag("share_pdf_saved_${bill.invoiceNo}")
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Share PDF")
                            }

                            if (isAdmin) {
                                Button(
                                    onClick = {
                                        clearFocusAndHideToolbar(focusManager, textToolbar)
                                        onOpenBill(bill)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = TealAccent),
                                    modifier = Modifier.testTag("open_saved_${bill.invoiceNo}")
                                ) {
                                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Open / Edit")
                                }

                                Button(
                                    onClick = { onDeleteBill(bill.id) },
                                    colors = ButtonDefaults.buttonColors(containerColor = RedDue),
                                    modifier = Modifier.testTag("delete_saved_${bill.invoiceNo}")
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Delete")
                                }
                            } else {
                                Text(
                                    text = "View only — Admin can edit or delete",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.align(Alignment.CenterVertically)
                                )
                            }
                        }
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
