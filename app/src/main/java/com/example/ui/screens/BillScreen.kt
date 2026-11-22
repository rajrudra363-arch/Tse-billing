package com.example.ui.screens

import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Summarize
import androidx.compose.material.icons.filled.SupportAgent
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
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.BillEntity
import com.example.data.BillingRepository
import com.example.data.ChargeType
import com.example.data.CompanySettings
import com.example.data.CustomerEntity
import com.example.data.DocumentType
import com.example.data.InvoiceItem
import com.example.data.PaymentMode
import com.example.data.ProductEntity
import com.example.data.UpiSettings
import com.example.data.UserSession
import com.example.ui.BillDraft
import com.example.ui.components.CameraSignatureBox
import com.example.ui.components.ProvideSafeTextToolbar
import com.example.ui.components.QrCodeImage
import com.example.ui.components.bringIntoViewOnFocus
import com.example.ui.components.clearFocusAndHideToolbar
import com.example.ui.theme.AmberWarn
import com.example.ui.theme.GoldAccent
import com.example.ui.theme.GreenPaid
import com.example.ui.theme.GreenPaidBg
import com.example.ui.theme.NavyPrimary
import com.example.ui.theme.RedDue
import com.example.ui.theme.RedDueBg
import com.example.ui.theme.RedDueDark
import com.example.ui.theme.TealAccent
import com.example.util.InvoicePrintPayload
import com.example.util.LocationHelper
import com.example.util.QrGenerator
import com.example.util.RupeesWords
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BillScreen(
    draft: BillDraft,
    products: List<ProductEntity>,
    customers: List<CustomerEntity>,
    allBills: List<BillEntity>,
    company: CompanySettings,
    upi: UpiSettings,
    currentUser: UserSession,
    isNightClosed: Boolean,
    summaryError: String,
    exportOptionsPayload: InvoicePrintPayload?,
    onSetDocumentType: (DocumentType) -> Unit,
    onQuotationNoteChange: (String) -> Unit,
    onCustomerNameChange: (String) -> Unit,
    onCustomerSelected: (CustomerEntity) -> Unit,
    onCustomerDetailsChange: (String) -> Unit,
    onCustomerLocationUrlChange: (String) -> Unit,
    onInvoiceNoChange: (String) -> Unit,
    onInvoiceDateChange: (String) -> Unit,
    onAddItem: () -> Unit,
    onSelectFolderForRow: (Int, String) -> Unit,
    onSelectProductForRow: (Int, ProductEntity) -> Unit,
    onUpdateItem: (Int, String?, Double?, String?, Double?) -> Unit,
    onRemoveItem: (Int) -> Unit,
    onExtraChargeTypeChange: (ChargeType) -> Unit,
    onExtraChargeAmountChange: (String) -> Unit,
    onToggleDualExtraCharge: (ChargeType) -> Unit = onExtraChargeTypeChange,
    onSecondaryChargeAmountChange: (String) -> Unit = {},
    onDiscountChange: (String) -> Unit,
    onPaidAmountChange: (String) -> Unit,
    onPaymentModeChange: (PaymentMode) -> Unit,
    onSignatureChanged: (Bitmap?) -> Unit,
    onCustomQrTextChange: (String) -> Unit,
    onStaffRatingChange: (Int) -> Unit,
    onStaffReviewChange: (String) -> Unit,
    onToggleSheetPreview: (Boolean) -> Unit,
    onResetBill: () -> Unit,
    onOpenSummaryModal: () -> Unit,
    onCloseSummaryModal: () -> Unit,
    onRequestGenerateWithDeviceAuth: () -> Unit,
    onDismissExportOptions: () -> Unit,
    onPrintA4: (InvoicePrintPayload) -> Unit,
    onSavePdf: (InvoicePrintPayload) -> Unit,
    onSharePdf: (InvoicePrintPayload) -> Unit,
    onSavePhoto: (InvoicePrintPayload) -> Unit,
    onSharePhoto: (InvoicePrintPayload) -> Unit,
    onOpenPaymentAndReview: (InvoicePrintPayload) -> Unit = {}
) {
    val isAdmin = currentUser.isAdmin && !currentUser.isSuperAdmin
    val isQuotation = draft.documentType == DocumentType.QUOTATION
    var mobileSearchText by remember { mutableStateOf("") }
    var customerDropdownOpen by remember { mutableStateOf(false) }
    var focusNextOnIndex by remember { mutableStateOf<Int?>(null) }
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val textToolbar = LocalTextToolbar.current
    var locationBusy by remember { mutableStateOf(false) }

    val matchedCustomers = remember(mobileSearchText, customers) {
        if (mobileSearchText.filter { it.isDigit() }.length >= 4 || mobileSearchText.trim().length >= 3) {
            BillingRepository.findMatchingCustomersByPhone(customers, mobileSearchText)
        } else emptyList()
    }

    // Customer history summary based on matched phone / details / customer name
    val customerHistoryBills = remember(allBills, draft.customerName, draft.customerDetails) {
        val searchDigits = draft.customerDetails.filter { it.isDigit() }.takeLast(10)
        val cleanName = draft.customerName.trim()
        if (cleanName.isBlank() && searchDigits.length < 5) emptyList()
        else {
            allBills.filter { b ->
                DocumentType.fromString(b.documentType) == DocumentType.BILL && (
                    (searchDigits.length >= 5 && b.customerDetails.filter { it.isDigit() }.endsWith(searchDigits.takeLast(5))) ||
                        (cleanName.isNotBlank() && b.customerName.equals(cleanName, ignoreCase = true))
                    )
            }
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val granted = perms.values.any { it }
        if (granted) {
            locationBusy = true
            LocationHelper.fetchCurrentLocationMapsUrl(context) { url, error ->
                locationBusy = false
                if (url != null) {
                    onCustomerLocationUrlChange(url)
                } else if (!error.isNullOrBlank()) {
                    Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            Toast.makeText(context, "Location permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp)
            .testTag("bill_lazy_list"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Spacer(Modifier.height(4.dp)) }

        if (isNightClosed) {
            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = RedDueBg),
                    border = BorderStroke(1.5.dp, RedDue),
                    modifier = Modifier.fillMaxWidth().testTag("night_closed_banner")
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Default.NightsStay, contentDescription = null, tint = RedDueDark)
                        Column {
                            Text(
                                text = "Billing System Closed (12:01 AM – 6:00 AM)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Black,
                                color = RedDueDark
                            )
                            Text(
                                text = "Bill and Quotation generation resumes automatically at 6:00 AM.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF7F1D1D)
                            )
                        }
                    }
                }
            }
        }

        // Action Bar & Document Type Toggle
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
                                    draft.editingId != null && isQuotation -> "Edit Quotation"
                                    draft.editingId != null -> "Edit Invoice"
                                    isQuotation -> "Create Quotation"
                                    else -> "Create New Bill"
                                },
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Created by: ${currentUser.displayName} (@${currentUser.username}) · ${currentUser.roleDisplayLabel}",
                                style = MaterialTheme.typography.labelMedium,
                                color = TealAccent,
                                modifier = Modifier.testTag("bill_creator_badge")
                            )
                        }
                        FilterChip(
                            selected = draft.showSheetPreview,
                            onClick = {
                                clearFocusAndHideToolbar(focusManager, textToolbar)
                                onToggleSheetPreview(!draft.showSheetPreview)
                            },
                            label = { Text(if (draft.showSheetPreview) "Hide A4" else "A4 Preview") },
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

                    // Document Type Switch (Tax Invoice vs Quotation)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        DocumentType.entries.forEach { docType ->
                            FilterChip(
                                selected = draft.documentType == docType,
                                onClick = {
                                    clearFocusAndHideToolbar(focusManager, textToolbar)
                                    onSetDocumentType(docType)
                                },
                                label = { Text(docType.label, fontWeight = FontWeight.Bold) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("doc_type_${docType.name}")
                            )
                        }
                    }

                    if (draft.isHelpBill && !isQuotation) {
                        Surface(
                            color = Color(0xFFE0F2FE),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, TealAccent),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.SupportAgent, contentDescription = null, tint = TealAccent)
                                Text(
                                    text = "Customer Care & Support Invoice (#${draft.helpRequestId}) — Special Support Banner applied.",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0B4F6C)
                                )
                            }
                        }
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                clearFocusAndHideToolbar(focusManager, textToolbar)
                                if (draft.items.size < 15) {
                                    focusNextOnIndex = draft.items.size
                                }
                                onAddItem()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = GreenPaid),
                            modifier = Modifier.testTag("add_item_button")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("Add Item (${draft.items.size}/15)")
                        }

                        Button(
                            onClick = {
                                clearFocusAndHideToolbar(focusManager, textToolbar)
                                mobileSearchText = ""
                                onResetBill()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AmberWarn),
                            modifier = Modifier.testTag("new_bill_button")
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("New")
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
                        text = if (isQuotation) "LIVE A4 QUOTATION PREVIEW" else "LIVE A4 INVOICE SHEET PREVIEW",
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

        // Step 1 & 2: Customer Mobile Search, Details & History Card
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Step 1: Search Customer Mobile & Details", style = MaterialTheme.typography.titleMedium)

                    OutlinedTextField(
                        value = mobileSearchText,
                        onValueChange = { mobileSearchText = it },
                        label = { Text("Search by Mobile Number (or last 5 digits)") },
                        placeholder = { Text("Enter at least 5 digits to auto-fill customer...") },
                        leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null, tint = TealAccent) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus().testTag("bill_mobile_search")
                    )

                    if (matchedCustomers.isNotEmpty()) {
                        Surface(
                            color = GreenPaidBg,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "Matching Customer(s) — Tap to Auto-Fill:",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = GreenPaid,
                                    fontWeight = FontWeight.Bold
                                )
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    matchedCustomers.forEach { c ->
                                        FilterChip(
                                            selected = draft.customerName.equals(c.name, ignoreCase = true),
                                            onClick = {
                                                clearFocusAndHideToolbar(focusManager, textToolbar)
                                                onCustomerSelected(c)
                                            },
                                            label = { Text("${c.name} (${c.phone})") },
                                            modifier = Modifier.testTag("bill_customer_match_${c.id}")
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (customerHistoryBills.isNotEmpty()) {
                        val dueCount = customerHistoryBills.count { it.dueAmount > 0.009 }
                        val paidCount = customerHistoryBills.size - dueCount
                        val totalDue = customerHistoryBills.sumOf { it.dueAmount }
                        val visitingTechs = customerHistoryBills.map { it.createdBy }.filter { it.isNotBlank() }.distinct()
                        Surface(
                            color = if (totalDue > 0.009) RedDueBg else Color(0xFFE1ECF4),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, if (totalDue > 0.009) RedDue else TealAccent),
                            modifier = Modifier.fillMaxWidth().testTag("bill_customer_history_card")
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(
                                        Icons.Default.History,
                                        contentDescription = null,
                                        tint = if (totalDue > 0.009) RedDueDark else NavyPrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = "Customer History: $paidCount Paid · $dueCount Due · Total Due: ₹${String.format(Locale.US, "%.2f", totalDue)}",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = if (totalDue > 0.009) RedDueDark else NavyPrimary
                                    )
                                }
                                if (visitingTechs.isNotEmpty()) {
                                    Text(
                                        text = "Visiting Technician(s): ${visitingTechs.joinToString(", ") { "@$it" }}",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF1E293B)
                                    )
                                }
                            }
                        }
                    }

                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = draft.customerName,
                            onValueChange = onCustomerNameChange,
                            label = { Text("Customer Name") },
                            trailingIcon = {
                                if (customers.isNotEmpty()) {
                                    IconButton(onClick = {
                                        clearFocusAndHideToolbar(focusManager, textToolbar)
                                        customerDropdownOpen = true
                                    }) {
                                        Icon(Icons.Default.ArrowDropDown, contentDescription = "Pick saved customer")
                                    }
                                }
                            },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus().testTag("bill_customer_name")
                        )
                        DropdownMenu(
                            expanded = customerDropdownOpen,
                            onDismissRequest = { customerDropdownOpen = false }
                        ) {
                            customers.forEach { c ->
                                DropdownMenuItem(
                                    text = { Text("${c.name} (${c.phone})", maxLines = 1) },
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
                            label = { Text(if (isQuotation) "Quotation No." else "Invoice No.") },
                            singleLine = true,
                            modifier = Modifier.weight(1f).bringIntoViewOnFocus().testTag("bill_invoice_no")
                        )
                        OutlinedTextField(
                            value = draft.date,
                            onValueChange = onInvoiceDateChange,
                            label = { Text("Date (YYYY-MM-DD)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f).bringIntoViewOnFocus().testTag("bill_invoice_date")
                        )
                    }

                    OutlinedTextField(
                        value = draft.customerDetails,
                        onValueChange = onCustomerDetailsChange,
                        label = { Text("Customer Address & Mobile Number") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus().testTag("bill_customer_details")
                    )

                    OutlinedTextField(
                        value = draft.customerLocationUrl,
                        onValueChange = onCustomerLocationUrlChange,
                        label = { Text("Google Maps Location Link / GPS (Optional)") },
                        leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null, tint = TealAccent) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus().testTag("bill_customer_location")
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                clearFocusAndHideToolbar(focusManager, textToolbar)
                                if (LocationHelper.hasLocationPermission(context)) {
                                    locationBusy = true
                                    LocationHelper.fetchCurrentLocationMapsUrl(context) { url, error ->
                                        locationBusy = false
                                        if (url != null) {
                                            onCustomerLocationUrlChange(url)
                                        } else if (!error.isNullOrBlank()) {
                                            Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                } else {
                                    locationPermissionLauncher.launch(LocationHelper.LOCATION_PERMISSIONS)
                                }
                            },
                            enabled = !locationBusy,
                            modifier = Modifier.testTag("bill_gps_location_button")
                        ) {
                            Icon(Icons.Default.MyLocation, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (locationBusy) "Getting GPS..." else "GPS Pin")
                        }

                        OutlinedButton(
                            onClick = {
                                clearFocusAndHideToolbar(focusManager, textToolbar)
                                val query = draft.customerLocationUrl.ifBlank { draft.customerDetails.ifBlank { draft.customerName } }
                                if (query.isBlank()) {
                                    Toast.makeText(context, "Enter address or tap GPS Pin first", Toast.LENGTH_SHORT).show()
                                } else {
                                    LocationHelper.openInGoogleMaps(context, query)
                                }
                            },
                            modifier = Modifier.testTag("bill_open_map_button")
                        ) {
                            Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Open Maps")
                        }
                    }
                }
            }
        }

        // Step 2: Line Items with Folder Selector -> Product Selector
        itemsIndexed(draft.items, key = { _, item -> item.localId }) { index, item ->
            InvoiceItemEditorCard(
                index = index,
                item = item,
                products = products,
                isAdmin = isAdmin,
                isLastRow = (index == draft.items.lastIndex),
                canAddMore = draft.items.size < 15,
                shouldRequestFocus = (focusNextOnIndex == index),
                onFocusConsumed = { focusNextOnIndex = null },
                onEnterAddNext = {
                    if (draft.items.size < 15) {
                        if (index == draft.items.lastIndex) {
                            focusNextOnIndex = draft.items.size
                            onAddItem()
                        } else {
                            focusManager.moveFocus(FocusDirection.Next)
                        }
                    }
                },
                onSelectFolder = { folder -> onSelectFolderForRow(index, folder) },
                onSelectProduct = { prod -> onSelectProductForRow(index, prod) },
                onUpdate = { desc, qty, unit, rate -> onUpdateItem(index, desc, qty, unit, rate) },
                onDelete = { onRemoveItem(index) }
            )
        }

        // Service / Installation / Customer Care & Support Charges (Dual selection + ₹300 Customer Care cap)
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Build, contentDescription = null, tint = TealAccent)
                        Text("Service / Installation / Support Charges", style = MaterialTheme.typography.titleMedium)
                    }
                    Text(
                        text = if (draft.isHelpBill) {
                            "Tick up to 2 charges (Customer Care & Support Charge max ₹300 + 1 Service/Installation charge)."
                        } else {
                            "Select up to 2 charges. Customer Care & Support maximum ₹300. Selected charges are added to Product Details."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ChargeType.entries.forEach { chargeType ->
                            val isSelected = if (chargeType == ChargeType.NONE) {
                                draft.extraChargeType == ChargeType.NONE && draft.secondaryChargeType == ChargeType.NONE
                            } else {
                                draft.extraChargeType == chargeType || draft.secondaryChargeType == chargeType
                            }
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    clearFocusAndHideToolbar(focusManager, textToolbar)
                                    onToggleDualExtraCharge(chargeType)
                                },
                                label = {
                                    Text(
                                        text = if (chargeType == ChargeType.CUSTOMER_CARE_AND_SUPPORT) {
                                            "${chargeType.label} (Max ₹300)"
                                        } else {
                                            chargeType.label
                                        },
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = NavyPrimary,
                                    selectedLabelColor = Color.White,
                                    selectedLeadingIconColor = Color.White,
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    labelColor = MaterialTheme.colorScheme.onSurface
                                ),
                                modifier = Modifier.testTag("charge_chip_${chargeType.name}")
                            )
                        }
                    }
                    AnimatedVisibility(visible = draft.extraChargeType != ChargeType.NONE) {
                        OutlinedTextField(
                            value = draft.extraChargeAmountText,
                            onValueChange = onExtraChargeAmountChange,
                            label = {
                                Text(
                                    if (draft.extraChargeType == ChargeType.CUSTOMER_CARE_AND_SUPPORT) {
                                        "${draft.extraChargeType.label} (₹, Max 300)"
                                    } else {
                                        "${draft.extraChargeType.label} (₹)"
                                    }
                                )
                            },
                            supportingText = if (draft.extraChargeType == ChargeType.CUSTOMER_CARE_AND_SUPPORT) {
                                { Text("Maximum ₹300 allowed for Customer Care & Support Charge") }
                            } else null,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus().testTag("bill_extra_charge_amount")
                        )
                    }
                    val hasSecondCharge = draft.secondaryChargeType != ChargeType.NONE &&
                        draft.secondaryChargeType != draft.extraChargeType
                    AnimatedVisibility(visible = hasSecondCharge) {
                        OutlinedTextField(
                            value = draft.secondaryChargeAmountText,
                            onValueChange = onSecondaryChargeAmountChange,
                            label = {
                                Text(
                                    if (draft.secondaryChargeType == ChargeType.CUSTOMER_CARE_AND_SUPPORT) {
                                        "${draft.secondaryChargeType.label} (₹, Max 300)"
                                    } else {
                                        "${draft.secondaryChargeType.label} (₹)"
                                    }
                                )
                            },
                            supportingText = if (draft.secondaryChargeType == ChargeType.CUSTOMER_CARE_AND_SUPPORT) {
                                { Text("Maximum ₹300 allowed for Customer Care & Support Charge") }
                            } else null,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus().testTag("bill_secondary_charge_amount")
                        )
                    }
                }
            }
        }

        // Step 3: Payment Mode (NO premature QR amount, NO auto-bill-generation) + Info QR + Camera Signature
        // Or Quotation Customer Note
        if (isQuotation) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Quotation Message for Customer", style = MaterialTheme.typography.titleMedium)
                        OutlinedTextField(
                            value = draft.quotationNote,
                            onValueChange = onQuotationNoteChange,
                            label = { Text("Custom Note for Customer (Optional)") },
                            placeholder = { Text("Includes installation warranty, valid for 15 days...") },
                            minLines = 3,
                            modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus().testTag("quotation_note_input")
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f))
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Estimated Quotation Total", fontWeight = FontWeight.Bold, color = NavyPrimary)
                            Text(
                                text = "₹${String.format(Locale.US, "%.2f", draft.grandTotal)}",
                                fontWeight = FontWeight.Black,
                                color = NavyPrimary
                            )
                        }
                    }
                }
            }
        } else {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text("Step 3: Payment Mode, Customer Info QR & Signature", style = MaterialTheme.typography.titleMedium)

                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Select Payment Mode", style = MaterialTheme.typography.labelMedium)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                PaymentMode.entries.forEach { mode ->
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        RadioButton(
                                            selected = draft.paymentMode == mode,
                                            onClick = { onPaymentModeChange(mode) },
                                            modifier = Modifier.testTag("payment_mode_${mode.name}")
                                        )
                                        Text(mode.label, style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                            }
                            Text(
                                text = if (draft.paymentMode == PaymentMode.ONLINE) {
                                    "Online Payment selected: After tapping Get Summary, enter Paid Amount to generate a dynamic UPI QR for that exact amount."
                                } else {
                                    "Cash mode selected: Proceed to Get Summary to record discount, paid amount, due balance, and customer 5-star review."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = TealAccent
                            )
                        }

                        HorizontalDivider()

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(Icons.Default.QrCode2, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Text("Customer Information QR", style = MaterialTheme.typography.labelMedium)
                                }
                                OutlinedTextField(
                                    value = draft.customQrText,
                                    onValueChange = onCustomQrTextChange,
                                    placeholder = { Text("Warranty, DVR login or site details...") },
                                    minLines = 2,
                                    modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus().testTag("custom_qr_input")
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

                        HorizontalDivider()

                        CameraSignatureBox(
                            signatureBase64 = draft.signatureBase64,
                            onSignatureChanged = onSignatureChanged
                        )
                    }
                }
            }
        }

        // Step 4: Bottom Primary CTA -> Get Summary
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Current Sub Total", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = "₹${String.format(Locale.US, "%.2f", draft.subtotal)}",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Black,
                                color = NavyPrimary
                            )
                        }
                        Button(
                            onClick = {
                                clearFocusAndHideToolbar(focusManager, textToolbar)
                                onOpenSummaryModal()
                            },
                            enabled = !isNightClosed,
                            colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary),
                            modifier = Modifier.height(52.dp).testTag("get_summary_button")
                        ) {
                            Icon(Icons.Default.Summarize, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Get Summary", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(16.dp)) }
    }

    if (draft.summaryOpen) {
        BillSummaryDialog(
            draft = draft,
            company = company,
            upi = upi,
            currentUser = currentUser,
            summaryError = summaryError,
            onDiscountChange = onDiscountChange,
            onPaidAmountChange = onPaidAmountChange,
            onPaymentModeChange = onPaymentModeChange,
            onRatingChange = onStaffRatingChange,
            onReviewChange = onStaffReviewChange,
            onDismiss = onCloseSummaryModal,
            onGenerate = onRequestGenerateWithDeviceAuth
        )
    }

    if (exportOptionsPayload != null) {
        ExportDocumentOptionsDialog(
            payload = exportOptionsPayload,
            currentUser = currentUser,
            onDismiss = onDismissExportOptions,
            onPreviewA4 = {
                onToggleSheetPreview(true)
                onDismissExportOptions()
            },
            onPrintA4 = { onPrintA4(exportOptionsPayload) },
            onSavePdf = { onSavePdf(exportOptionsPayload) },
            onSharePdf = { onSharePdf(exportOptionsPayload) },
            onSavePhoto = { onSavePhoto(exportOptionsPayload) },
            onSharePhoto = { onSharePhoto(exportOptionsPayload) },
            onOpenPaymentAndReview = { onOpenPaymentAndReview(exportOptionsPayload) }
        )
    }
}

@Composable
private fun InvoiceItemEditorCard(
    index: Int,
    item: InvoiceItem,
    products: List<ProductEntity>,
    isAdmin: Boolean,
    isLastRow: Boolean,
    canAddMore: Boolean,
    shouldRequestFocus: Boolean,
    onFocusConsumed: () -> Unit,
    onEnterAddNext: () -> Unit,
    onSelectFolder: (String) -> Unit,
    onSelectProduct: (ProductEntity) -> Unit,
    onUpdate: (String?, Double?, String?, Double?) -> Unit,
    onDelete: () -> Unit
) {
    var folderMenuOpen by remember { mutableStateOf(false) }
    var productMenuOpen by remember { mutableStateOf(false) }
    val itemFocusManager = LocalFocusManager.current
    val itemTextToolbar = LocalTextToolbar.current
    val firstFieldFocus = remember { FocusRequester() }
    val qtyFocus = remember { FocusRequester() }

    val availableFolders = remember(products) {
        listOf("All Folders") + products.map { it.folder.ifBlank { "General" } }.distinct().sorted()
    }
    val selectedFolder = item.folder.ifBlank { "All Folders" }
    val folderFilteredProducts = remember(products, selectedFolder) {
        if (selectedFolder == "All Folders" || selectedFolder.isBlank()) products
        else products.filter { it.folder.ifBlank { "General" }.equals(selectedFolder, ignoreCase = true) }
    }

    LaunchedEffect(shouldRequestFocus) {
        if (shouldRequestFocus) {
            runCatching { firstFieldFocus.requestFocus() }
            onFocusConsumed()
        }
    }

    var qtyText by remember(item.localId) {
        mutableStateOf(if (item.qty % 1.0 == 0.0) item.qty.toInt().toString() else item.qty.toString())
    }
    LaunchedEffect(item.localId, item.qty) {
        val currentParsed = qtyText.toDoubleOrNull()
        if (currentParsed != item.qty && !(qtyText.isEmpty() && item.qty == 0.0)) {
            qtyText = if (item.qty % 1.0 == 0.0) item.qty.toInt().toString() else item.qty.toString()
        }
    }
    var rateText by remember(item.localId) {
        mutableStateOf(if (item.rate == 0.0) "0" else if (item.rate % 1.0 == 0.0) item.rate.toInt().toString() else item.rate.toString())
    }
    LaunchedEffect(item.localId, item.rate) {
        val currentParsed = rateText.toDoubleOrNull()
        if (currentParsed != item.rate && !(rateText.isEmpty() && item.rate == 0.0)) {
            rateText = if (item.rate == 0.0) "0" else if (item.rate % 1.0 == 0.0) item.rate.toInt().toString() else item.rate.toString()
        }
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
                    IconButton(
                        onClick = {
                            clearFocusAndHideToolbar(itemFocusManager, itemTextToolbar)
                            onDelete()
                        },
                        modifier = Modifier.testTag("delete_item_$index")
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Remove item", tint = RedDue)
                    }
                }
            }

            // Folder Dropdown -> filters Products below
            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = {
                        clearFocusAndHideToolbar(itemFocusManager, itemTextToolbar)
                        folderMenuOpen = true
                    },
                    modifier = Modifier.fillMaxWidth().height(46.dp).testTag("item_folder_btn_$index"),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.Folder, contentDescription = null, tint = TealAccent, modifier = Modifier.size(18.dp))
                            Text(
                                text = "Folder: $selectedFolder",
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1
                            )
                        }
                        Icon(Icons.Default.ArrowDropDown, contentDescription = "Choose product folder")
                    }
                }
                DropdownMenu(
                    expanded = folderMenuOpen,
                    onDismissRequest = { folderMenuOpen = false },
                    modifier = Modifier.widthIn(max = 320.dp)
                ) {
                    availableFolders.forEach { folder ->
                        DropdownMenuItem(
                            text = { Text(folder, fontWeight = FontWeight.SemiBold) },
                            onClick = {
                                onSelectFolder(if (folder == "All Folders") "" else folder)
                                folderMenuOpen = false
                            }
                        )
                    }
                }
            }

            // Product Dropdown (filtered by chosen folder, showing prices)
            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = {
                        clearFocusAndHideToolbar(itemFocusManager, itemTextToolbar)
                        productMenuOpen = true
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("item_product_btn_$index"),
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
                    folderFilteredProducts.forEach { prod ->
                        DropdownMenuItem(
                            text = {
                                Text("${prod.name} (${prod.unit} · ₹${String.format(Locale.US, "%.0f", prod.defaultRate)})")
                            },
                            onClick = {
                                onSelectProduct(prod)
                                productMenuOpen = false
                                runCatching { qtyFocus.requestFocus() }
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
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(
                        onNext = { runCatching { qtyFocus.requestFocus() } }
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(firstFieldFocus)
                        .bringIntoViewOnFocus()
                        .onPreviewKeyEvent {
                            if (it.type == KeyEventType.KeyUp && (it.key == Key.Enter || it.key == Key.NumPadEnter)) {
                                if (isLastRow && canAddMore && item.description.isNotBlank()) {
                                    onEnterAddNext()
                                } else {
                                    runCatching { qtyFocus.requestFocus() }
                                }
                                true
                            } else {
                                false
                            }
                        }
                        .testTag("item_desc_input_$index")
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val qtyModifier = Modifier
                    .weight(0.8f)
                    .let { m -> if (!isAdmin) m.focusRequester(firstFieldFocus) else m }
                    .focusRequester(qtyFocus)
                    .bringIntoViewOnFocus()
                    .onPreviewKeyEvent {
                        if (it.type == KeyEventType.KeyUp && (it.key == Key.Enter || it.key == Key.NumPadEnter)) {
                            onEnterAddNext()
                            true
                        } else {
                            false
                        }
                    }
                    .testTag("item_qty_input_$index")

                OutlinedTextField(
                    value = qtyText,
                    onValueChange = {
                        qtyText = it
                        onUpdate(null, it.toDoubleOrNull() ?: 0.0, null, null)
                    },
                    label = { Text("Qty") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = if (isAdmin) ImeAction.Next else ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { itemFocusManager.moveFocus(FocusDirection.Next) },
                        onDone = { onEnterAddNext() }
                    ),
                    singleLine = true,
                    modifier = qtyModifier
                )
                OutlinedTextField(
                    value = item.unit,
                    onValueChange = { if (isAdmin) onUpdate(null, null, it, null) },
                    label = { Text("Unit") },
                    readOnly = !isAdmin,
                    enabled = isAdmin,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(
                        onNext = { itemFocusManager.moveFocus(FocusDirection.Next) }
                    ),
                    singleLine = true,
                    modifier = Modifier.weight(0.8f).bringIntoViewOnFocus()
                )
                OutlinedTextField(
                    value = rateText,
                    onValueChange = {
                        if (isAdmin) {
                            rateText = it
                            onUpdate(null, null, null, it.toDoubleOrNull() ?: 0.0)
                        }
                    },
                    label = { Text(if (isAdmin) "Rate (₹)" else "Rate") },
                    readOnly = !isAdmin,
                    enabled = isAdmin,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { onEnterAddNext() }
                    ),
                    singleLine = true,
                    modifier = Modifier
                        .weight(1.2f)
                        .bringIntoViewOnFocus()
                        .onPreviewKeyEvent {
                            if (it.type == KeyEventType.KeyUp && (it.key == Key.Enter || it.key == Key.NumPadEnter)) {
                                onEnterAddNext()
                                true
                            } else {
                                false
                            }
                        }
                        .testTag("item_rate_input_$index")
                )
            }

            if (isLastRow && canAddMore && (item.description.isNotBlank() || item.rate > 0.0)) {
                OutlinedButton(
                    onClick = {
                        clearFocusAndHideToolbar(itemFocusManager, itemTextToolbar)
                        onEnterAddNext()
                    },
                    modifier = Modifier.fillMaxWidth().testTag("quick_add_next_item_$index")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Add Product #${index + 2} (Enter)", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun BillSummaryDialog(
    draft: BillDraft,
    company: CompanySettings,
    upi: UpiSettings,
    currentUser: UserSession,
    summaryError: String,
    onDiscountChange: (String) -> Unit,
    onPaidAmountChange: (String) -> Unit,
    onPaymentModeChange: (PaymentMode) -> Unit = {},
    onRatingChange: (Int) -> Unit,
    onReviewChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onGenerate: () -> Unit
) {
    val isQuotation = draft.documentType == DocumentType.QUOTATION
    val focusManager = LocalFocusManager.current
    val textToolbar = LocalTextToolbar.current
    val hasDue = draft.dueAmount > 0.009
    val context = LocalContext.current
    val density = LocalDensity.current
    // True 1.5 inch x 1.5 inch physical QR size using screen xdpi (safe bounds on all screens/dialogs)
    val qrPhysicalSizeDp = remember(density, context) {
        val xdpi = context.resources.displayMetrics.xdpi.takeIf { it >= 72f } ?: 160f
        with(density) { (1.5f * xdpi).toDp() }.coerceIn(135.dp, 220.dp)
    }

    val dynamicOnlineUri = remember(upi.upiId, upi.payeeName, draft.paidAmount, draft.invoiceNo) {
        if (upi.upiId.isNotBlank()) {
            QrGenerator.buildUpiUri(upi.upiId, upi.payeeName, draft.paidAmount, draft.invoiceNo)
        } else ""
    }

    AlertDialog(
        onDismissRequest = {
            clearFocusAndHideToolbar(focusManager, textToolbar)
            onDismiss()
        },
        modifier = Modifier.testTag("bill_summary_dialog"),
        icon = { Icon(Icons.Default.Summarize, contentDescription = null, tint = TealAccent) },
        title = {
            Column {
                Text(if (isQuotation) "Quotation Summary" else "Final Bill Summary")
                Text(
                    text = "${draft.invoiceNo} · Generated by @${currentUser.username}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            ProvideSafeTextToolbar {
                DisposableEffect(focusManager, textToolbar) {
                    onDispose { clearFocusAndHideToolbar(focusManager, textToolbar) }
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .imePadding()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    InvoiceSheetPreview(
                        draft = draft,
                        company = company,
                        upi = upi
                    )

                    if (!isQuotation) {
                        // Strict ordered summary: Sub Total -> Discount -> Grand Total -> Paid Amount -> Due Balance -> Payment Mode
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                // 1. Sub Total
                                TotalRow("1. SUB TOTAL", draft.subtotal)
                                HorizontalDivider()

                                // 2. Discount
                                OutlinedTextField(
                                    value = draft.discountText,
                                    onValueChange = onDiscountChange,
                                    label = { Text("2. Discount (₹)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus().testTag("summary_discount_input")
                                )

                                // 3. Grand Total
                                TotalRow(
                                    label = "3. GRAND TOTAL",
                                    amount = draft.grandTotal,
                                    bg = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                                    bold = true
                                )

                                // 4. Paid Amount
                                OutlinedTextField(
                                    value = draft.paidAmountText,
                                    onValueChange = onPaidAmountChange,
                                    label = { Text("4. Paid Amount (₹)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus().testTag("summary_paid_input")
                                )

                                // 5. Due Balance (Grand Total - Paid Amount)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (hasDue) RedDueBg else GreenPaidBg)
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = if (hasDue) "5. DUE BALANCE (Grand - Paid)" else "5. DUE BALANCE (PAID IN FULL)",
                                        fontWeight = FontWeight.Black,
                                        color = if (hasDue) RedDueDark else GreenPaid,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Text(
                                        text = "₹${String.format(Locale.US, "%.2f", draft.dueAmount)}",
                                        fontWeight = FontWeight.Black,
                                        color = if (hasDue) RedDueDark else GreenPaid,
                                        modifier = Modifier.testTag("summary_due_amount")
                                    )
                                }

                                HorizontalDivider()

                                // 6. Payment Mode Toggle (Online Payment vs Cash)
                                Text(
                                    text = "6. Payment Mode",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = NavyPrimary
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    PaymentMode.entries.forEach { mode ->
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            RadioButton(
                                                selected = draft.paymentMode == mode,
                                                onClick = { onPaymentModeChange(mode) },
                                                modifier = Modifier.testTag("summary_payment_mode_${mode.name}")
                                            )
                                            Text(mode.label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                }
                            }
                        }

                        if (draft.paymentMode == PaymentMode.ONLINE) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFFE0F2FE),
                                border = BorderStroke(1.2.dp, TealAccent),
                                modifier = Modifier.fillMaxWidth().testTag("summary_online_qr_card")
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "Scan & Pay Paid Amount: ₹${String.format(Locale.US, "%.2f", draft.paidAmount)}",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Black,
                                        color = NavyPrimary
                                    )
                                    QrCodeImage(
                                        content = dynamicOnlineUri,
                                        fallbackBase64 = upi.fallbackQrBase64,
                                        contentDescription = "Dynamic Online Paid Amount UPI QR (1.5 inch)",
                                        modifier = Modifier
                                            .size(qrPhysicalSizeDp)
                                            .testTag("summary_online_qr_image")
                                    )
                                    Text(
                                        text = "1.5\" × 1.5\" dynamic QR strictly for ₹${String.format(Locale.US, "%.2f", draft.paidAmount)}. Printed A4 bill QR remains amount-free.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFF1E293B)
                                    )
                                    Button(
                                        onClick = {
                                            clearFocusAndHideToolbar(focusManager, textToolbar)
                                            onGenerate()
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = TealAccent,
                                            contentColor = Color.White
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(48.dp)
                                            .testTag("confirm_online_pay_generate_button")
                                    ) {
                                        Icon(Icons.Default.Payments, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Payment Received -> Auto Generate Bill", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        HorizontalDivider()

                        Text(
                            text = "Customer 5-Star Rating for @${currentUser.username}",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = NavyPrimary
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            for (star in 1..5) {
                                val selected = star <= draft.staffRating
                                IconButton(
                                    onClick = {
                                        clearFocusAndHideToolbar(focusManager, textToolbar)
                                        onRatingChange(star)
                                    },
                                    modifier = Modifier.size(48.dp).testTag("bill_summary_star_$star")
                                ) {
                                    Icon(
                                        imageVector = if (selected) Icons.Default.Star else Icons.Default.StarBorder,
                                        contentDescription = "Rate $star star",
                                        tint = if (selected) GoldAccent else MaterialTheme.colorScheme.outline,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                            }
                        }
                        OutlinedTextField(
                            value = draft.staffReviewNote,
                            onValueChange = onReviewChange,
                            label = { Text("Customer Review Feedback (Optional)") },
                            minLines = 2,
                            modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus().testTag("bill_summary_review_note")
                        )
                    }

                    if (summaryError.isNotEmpty()) {
                        Surface(
                            color = RedDueBg,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, RedDue),
                            modifier = Modifier.fillMaxWidth().testTag("summary_error_text")
                        ) {
                            Text(
                                text = summaryError,
                                color = RedDueDark,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    clearFocusAndHideToolbar(focusManager, textToolbar)
                    onGenerate()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = GreenPaid,
                    contentColor = Color.White
                ),
                modifier = Modifier.testTag("generate_final_bill_button")
            ) {
                Icon(Icons.Default.Fingerprint, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (isQuotation) "Generate Quotation" else "Generate Bill", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = {
                clearFocusAndHideToolbar(focusManager, textToolbar)
                onDismiss()
            }) {
                Text("Back to Edit")
            }
        }
    )
}

@Composable
private fun ExportDocumentOptionsDialog(
    payload: InvoicePrintPayload,
    currentUser: UserSession,
    onDismiss: () -> Unit,
    onPreviewA4: () -> Unit,
    onPrintA4: () -> Unit,
    onSavePdf: () -> Unit,
    onSharePdf: () -> Unit,
    onSavePhoto: () -> Unit,
    onSharePhoto: () -> Unit,
    onOpenPaymentAndReview: () -> Unit
) {
    val isQuotation = payload.documentType == DocumentType.QUOTATION
    val canAdminShare = currentUser.isAdmin && !currentUser.isSuperAdmin
    val docTitle = if (isQuotation) "Quotation Ready (${payload.invoiceNo})" else "Bill Ready (${payload.invoiceNo})"

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = TealAccent) },
        title = { Text(docTitle) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = if (canAdminShare) {
                        "Saved by @${currentUser.username}. Print, Save, or Share A4 PDF & Photo:"
                    } else {
                        "Saved by @${currentUser.username} & Admin notified. Technicians can Print, Preview, or Save locally (only Admin can share)."
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
                Button(
                    onClick = onPrintA4,
                    colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary, contentColor = Color.White),
                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("export_print_a4_button")
                ) {
                    Icon(Icons.Default.Print, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Print on A4 Paper", fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = onSavePdf,
                    colors = ButtonDefaults.buttonColors(containerColor = GreenPaid, contentColor = Color.White),
                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("export_save_pdf_button")
                ) {
                    Icon(Icons.Default.PictureAsPdf, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Save A4 PDF", fontWeight = FontWeight.Bold)
                }
                if (canAdminShare) {
                    OutlinedButton(
                        onClick = onSharePdf,
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("export_share_pdf_button")
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, tint = NavyPrimary)
                        Spacer(Modifier.width(8.dp))
                        Text("Share A4 PDF", fontWeight = FontWeight.Bold, color = NavyPrimary)
                    }
                }
                Button(
                    onClick = onSavePhoto,
                    colors = ButtonDefaults.buttonColors(containerColor = TealAccent, contentColor = Color.White),
                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("export_save_photo_button")
                ) {
                    Icon(Icons.Default.Image, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Save Photo (PNG)", fontWeight = FontWeight.Bold)
                }
                if (canAdminShare) {
                    OutlinedButton(
                        onClick = onSharePhoto,
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("export_share_photo_button")
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, tint = NavyPrimary)
                        Spacer(Modifier.width(8.dp))
                        Text("Share Photo (PNG)", fontWeight = FontWeight.Bold, color = NavyPrimary)
                    }
                }
                OutlinedButton(
                    onClick = onPreviewA4,
                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("export_preview_a4_button")
                ) {
                    Icon(Icons.Default.Visibility, contentDescription = null, tint = NavyPrimary)
                    Spacer(Modifier.width(8.dp))
                    Text("View A4 Preview", fontWeight = FontWeight.Bold, color = NavyPrimary)
                }
                if (!isQuotation && canAdminShare) {
                    HorizontalDivider()
                    Button(
                        onClick = onOpenPaymentAndReview,
                        colors = ButtonDefaults.buttonColors(containerColor = AmberWarn, contentColor = Color.White),
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("export_payment_review_button")
                    ) {
                        Icon(Icons.Default.Payments, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Adjust Payment / Review", fontWeight = FontWeight.Bold)
                    }
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
