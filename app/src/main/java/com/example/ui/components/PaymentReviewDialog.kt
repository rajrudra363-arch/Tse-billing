package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.BillEntity
import com.example.ui.theme.GoldAccent
import com.example.ui.theme.GreenPaid
import com.example.ui.theme.GreenPaidBg
import com.example.ui.theme.NavyPrimary
import com.example.ui.theme.RedDue
import com.example.ui.theme.RedDueBg
import com.example.ui.theme.RedDueDark
import com.example.ui.theme.TealAccent
import java.util.Locale

@Composable
fun PaymentAndReviewDialog(
    bill: BillEntity,
    onDismiss: () -> Unit,
    onSave: (billId: String, discountText: String, paidText: String, rating: Int, reviewNote: String) -> Unit
) {
    var discountText by remember(bill.id) {
        mutableStateOf(formatNum(bill.discount))
    }
    var paidText by remember(bill.id) {
        mutableStateOf(formatNum(bill.paidAmount))
    }
    val ratingLocked = bill.staffRating in 1..5
    var rating by remember(bill.id) {
        mutableIntStateOf(bill.staffRating.coerceIn(0, 5))
    }
    var reviewNote by remember(bill.id) {
        mutableStateOf(bill.staffReviewNote)
    }

    val discountVal = (discountText.toDoubleOrNull() ?: 0.0).coerceAtLeast(0.0)
    val grandTotal = (bill.subtotal - discountVal).coerceAtLeast(0.0)
    val paidVal = (paidText.toDoubleOrNull() ?: 0.0).coerceAtLeast(0.0)
    val dueVal = (grandTotal - paidVal).coerceAtLeast(0.0)
    val hasDue = dueVal > 0.009

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Payments, contentDescription = null, tint = TealAccent) },
        title = {
            Column {
                Text("Payment & 5-Star Review")
                Text(
                    text = "${bill.invoiceNo} · ${bill.customerName.ifBlank { "Walk-in Customer" }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            ProvideSafeTextToolbar {
                val focusManager = LocalFocusManager.current
                val textToolbar = LocalTextToolbar.current
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .imePadding()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Technician: @${bill.createdBy.ifBlank { "Admin" }}",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Sub Total", style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    text = "₹${String.format(Locale.US, "%.2f", bill.subtotal)}",
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            HorizontalDivider()
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f))
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("GRAND TOTAL", fontWeight = FontWeight.Black, color = NavyPrimary)
                                Text(
                                    text = "₹${String.format(Locale.US, "%.2f", grandTotal)}",
                                    fontWeight = FontWeight.Black,
                                    color = NavyPrimary,
                                    modifier = Modifier.testTag("settlement_grand_total")
                                )
                            }
                            HorizontalDivider()
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(if (hasDue) RedDueBg else GreenPaidBg)
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (hasDue) "DUE AMOUNT [DUE]" else "PAID IN FULL",
                                    fontWeight = FontWeight.Black,
                                    color = if (hasDue) RedDueDark else GreenPaid
                                )
                                Text(
                                    text = "₹${String.format(Locale.US, "%.2f", dueVal)}",
                                    fontWeight = FontWeight.Black,
                                    color = if (hasDue) RedDueDark else GreenPaid,
                                    modifier = Modifier.testTag("settlement_due_amount")
                                )
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = discountText,
                            onValueChange = { discountText = it },
                            label = { Text("Discount (₹)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .bringIntoViewOnFocus()
                                .testTag("settlement_discount_input")
                        )
                        OutlinedTextField(
                            value = paidText,
                            onValueChange = { paidText = it },
                            label = { Text("Customer Paid (₹)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .bringIntoViewOnFocus()
                                .testTag("settlement_paid_input")
                        )
                    }

                    HorizontalDivider()

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (ratingLocked) {
                            Icon(
                                Icons.Default.Lock,
                                contentDescription = "Rating Locked",
                                tint = NavyPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Text(
                            text = if (ratingLocked) {
                                "5-Star Review for @${bill.createdBy.ifBlank { "Admin" }} (Locked)"
                            } else {
                                "Customer 5-Star Review for @${bill.createdBy.ifBlank { "Admin" }}"
                            },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = NavyPrimary
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        for (star in 1..5) {
                            val selected = star <= rating
                            IconButton(
                                onClick = {
                                    if (!ratingLocked) {
                                        clearFocusAndHideToolbar(focusManager, textToolbar)
                                        rating = star
                                    }
                                },
                                enabled = !ratingLocked,
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("staff_star_$star")
                            ) {
                                Icon(
                                    imageVector = if (selected) Icons.Default.Star else Icons.Default.StarBorder,
                                    contentDescription = "Rate $star star",
                                    tint = if (selected) GoldAccent else MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.size(34.dp)
                                )
                            }
                        }
                    }

                    Text(
                        text = if (ratingLocked) {
                            "$rating / 5 Stars — Locked (Cannot be edited or deleted)"
                        } else {
                            when (rating) {
                                5 -> "5 / 5 Stars — Excellent Service (Permanent once saved)"
                                4 -> "4 / 5 Stars — Very Good Service (Permanent once saved)"
                                3 -> "3 / 5 Stars — Good Service (Permanent once saved)"
                                2 -> "2 / 5 Stars — Needs Improvement (Permanent once saved)"
                                1 -> "1 / 5 Star — Poor Service (Permanent once saved)"
                                else -> "Tap stars to rate technician work quality (Permanent once saved)"
                            }
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = if (rating > 0) TealAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold
                    )

                    OutlinedTextField(
                        value = reviewNote,
                        onValueChange = { if (!ratingLocked) reviewNote = it },
                        readOnly = ratingLocked,
                        enabled = !ratingLocked,
                        label = { Text(if (ratingLocked) "Customer Review Feedback (Locked)" else "Customer Review Feedback (Optional)") },
                        placeholder = { Text("Behavior, work quality, neatness...") },
                        minLines = 2,
                        modifier = Modifier
                            .fillMaxWidth()
                            .bringIntoViewOnFocus()
                            .testTag("staff_review_note_input")
                    )

                    DisposableEffect(focusManager, textToolbar) {
                        onDispose {
                            clearFocusAndHideToolbar(focusManager, textToolbar)
                        }
                    }
                }
            }
        },
        confirmButton = {
            val btnFocus = LocalFocusManager.current
            val btnToolbar = LocalTextToolbar.current
            Button(
                onClick = {
                    clearFocusAndHideToolbar(btnFocus, btnToolbar)
                    onSave(bill.id, discountText, paidText, rating, reviewNote)
                },
                colors = ButtonDefaults.buttonColors(containerColor = if (hasDue) RedDue else GreenPaid),
                modifier = Modifier.testTag("save_settlement_review_button")
            ) {
                Text(if (ratingLocked) "Update Payment" else "Save Payment & Review", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            val btnFocus = LocalFocusManager.current
            val btnToolbar = LocalTextToolbar.current
            TextButton(
                onClick = {
                    clearFocusAndHideToolbar(btnFocus, btnToolbar)
                    onDismiss()
                }
            ) {
                Text("Close")
            }
        }
    )
}

private fun formatNum(v: Double): String {
    return if (v == 0.0) "0"
    else if (v % 1.0 == 0.0) v.toInt().toString()
    else String.format(Locale.US, "%.2f", v)
}
