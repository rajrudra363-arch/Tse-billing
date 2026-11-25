package com.example.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Call
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.BillEntity
import com.example.data.BillingRepository
import com.example.data.CustomerEntity
import com.example.data.DocumentType
import com.example.data.ProductEntity
import com.example.data.UserSession
import com.example.ui.components.ProvideSafeTextToolbar
import com.example.ui.components.bringIntoViewOnFocus
import com.example.ui.components.clearFocusAndHideToolbar
import com.example.ui.theme.AmberWarn
import com.example.ui.theme.GreenPaid
import com.example.ui.theme.GreenPaidBg
import com.example.ui.theme.NavyPrimary
import com.example.ui.theme.RedDue
import com.example.ui.theme.RedDueBg
import com.example.ui.theme.RedDueDark
import com.example.ui.theme.TealAccent
import com.example.util.LocationHelper
import java.util.Locale

private val DEFAULT_PRODUCT_FOLDERS = listOf(
    "Combo",
    "CCTV Cameras",
    "DVR & NVR",
    "Storage & Power",
    "Cables & Accessories",
    "Services",
    "General"
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CustomersScreen(
    customers: List<CustomerEntity>,
    user: UserSession,
    searchQuery: String = "",
    bills: List<BillEntity> = emptyList(),
    onSearchChange: ((String) -> Unit)? = null,
    onSaveCustomer: (id: Long, name: String, phone: String, address: String, locationUrl: String) -> Unit,
    onDeleteCustomer: (Long) -> Unit,
    onDeleteBill: ((String) -> Unit)? = null,
    onOpenBill: ((BillEntity) -> Unit)? = null
) {
    val canManageCustomers = user.isAdmin && !user.isSuperAdmin
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val textToolbar = LocalTextToolbar.current

    var localSearch by remember { mutableStateOf(searchQuery) }
    val activeQuery = if (onSearchChange != null) searchQuery else localSearch
    val filteredCustomers = remember(customers, activeQuery) {
        BillingRepository.filterCustomers(customers, activeQuery)
    }

    var selectedCustomerForBills by remember { mutableStateOf<CustomerEntity?>(null) }
    var billToDeleteConfirm by remember { mutableStateOf<BillEntity?>(null) }

    var editingId by remember { mutableStateOf(0L) }
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var locationUrl by remember { mutableStateOf("") }
    var locationBusy by remember { mutableStateOf(false) }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val granted = perms.values.any { it }
        if (granted) {
            locationBusy = true
            LocationHelper.fetchCurrentLocationMapsUrl(context) { url, error ->
                locationBusy = false
                if (url != null) {
                    locationUrl = url
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
            .testTag("customers_lazy_list"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { Spacer(Modifier.height(4.dp)) }

        if (canManageCustomers) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = if (editingId > 0L) "Edit Customer (Admin)" else "Customer Manager (Admin)",
                            style = MaterialTheme.typography.titleLarge
                        )
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("Customer Name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus().testTag("new_customer_name")
                        )
                        OutlinedTextField(
                            value = phone,
                            onValueChange = { phone = it },
                            label = { Text("Mobile Number") },
                            leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus().testTag("new_customer_phone")
                        )
                        OutlinedTextField(
                            value = address,
                            onValueChange = { address = it },
                            label = { Text("Customer Address") },
                            minLines = 2,
                            modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus().testTag("new_customer_address")
                        )
                        OutlinedTextField(
                            value = locationUrl,
                            onValueChange = { locationUrl = it },
                            label = { Text("Google Maps Location Link / Coordinates") },
                            leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null, tint = TealAccent) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus().testTag("new_customer_location")
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
                                                locationUrl = url
                                            } else if (!error.isNullOrBlank()) {
                                                Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    } else {
                                        locationPermissionLauncher.launch(LocationHelper.LOCATION_PERMISSIONS)
                                    }
                                },
                                enabled = !locationBusy,
                                modifier = Modifier.testTag("customer_gps_location_button")
                            ) {
                                Icon(Icons.Default.MyLocation, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(if (locationBusy) "Getting GPS..." else "Use GPS Location")
                            }

                            OutlinedButton(
                                onClick = {
                                    clearFocusAndHideToolbar(focusManager, textToolbar)
                                    val query = locationUrl.ifBlank { address.ifBlank { name } }
                                    if (query.isBlank()) {
                                        Toast.makeText(context, "Enter address or GPS location first", Toast.LENGTH_SHORT).show()
                                    } else {
                                        LocationHelper.openInGoogleMaps(context, query)
                                    }
                                },
                                modifier = Modifier.testTag("customer_open_maps_picker_button")
                            ) {
                                Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Open Google Maps")
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    clearFocusAndHideToolbar(focusManager, textToolbar)
                                    onSaveCustomer(editingId, name, phone, address, locationUrl)
                                    if (name.isNotBlank()) {
                                        editingId = 0L
                                        name = ""
                                        phone = ""
                                        address = ""
                                        locationUrl = ""
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = GreenPaid, contentColor = Color.White),
                                modifier = Modifier.weight(1f).height(48.dp).testTag("save_customer_button")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, tint = Color.White)
                                Text(
                                    if (editingId > 0L) " Update Customer" else " Save Customer",
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                            if (editingId > 0L) {
                                OutlinedButton(
                                    onClick = {
                                        clearFocusAndHideToolbar(focusManager, textToolbar)
                                        editingId = 0L
                                        name = ""
                                        phone = ""
                                        address = ""
                                        locationUrl = ""
                                    },
                                    modifier = Modifier.height(48.dp)
                                ) {
                                    Text("Cancel")
                                }
                            }
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
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Customer Directory (View Only)", style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = if (user.isSuperAdmin) {
                                "Super Admin view-only mode: inspect customer details and saved locations."
                            } else {
                                "Technicians can view customer details, call, and open Google Maps locations, but cannot add, edit, or delete customers."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = activeQuery,
                        onValueChange = { q ->
                            if (onSearchChange != null) onSearchChange(q) else localSearch = q
                        },
                        label = { Text("Search Customer / Last 5 Digits of Mobile") },
                        placeholder = { Text("Type customer name or last 5 digits (e.g. 54321)") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus().testTag("customer_search_input")
                    )
                    Text(
                        text = if (activeQuery.isBlank()) {
                            "Total Customers: ${customers.size} · Enter the last 5 mobile digits to find customer details quickly."
                        } else {
                            "Showing ${filteredCustomers.size} of ${customers.size} customers"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (filteredCustomers.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.People, contentDescription = null, modifier = Modifier.size(44.dp), tint = MaterialTheme.colorScheme.primary)
                        Text(
                            text = if (activeQuery.isBlank()) "No customers saved yet" else "No matching customer found",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }
        } else {
            items(filteredCustomers, key = { it.id }) { cust ->
                val displayPhone = cust.phone.ifBlank { "" }
                val displayAddress = cust.address.ifBlank { cust.details }
                val mapTarget = cust.locationUrl.ifBlank { displayAddress }

                val custBills = remember(bills, cust) {
                    val custNameLower = cust.name.trim().lowercase(Locale.ROOT)
                    val custPhoneDigits = cust.phone.filter { it.isDigit() }
                    val last5 = if (custPhoneDigits.length >= 5) custPhoneDigits.takeLast(5) else ""
                    bills.filter { b ->
                        val bNameLower = b.customerName.trim().lowercase(Locale.ROOT)
                        val bDetails = b.customerDetails
                        val nameMatches = custNameLower.isNotBlank() && (bNameLower.contains(custNameLower) || custNameLower.contains(bNameLower))
                        val phoneMatches = last5.isNotBlank() && bDetails.contains(last5)
                        nameMatches || phoneMatches
                    }
                }

                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(cust.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                if (displayPhone.isNotBlank()) {
                                    Text(
                                        text = "Mobile: $displayPhone",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TealAccent,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                if (displayAddress.isNotBlank()) {
                                    Text(
                                        text = displayAddress,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (cust.locationUrl.isNotBlank()) {
                                    Text(
                                        text = "Maps Pin: ${cust.locationUrl}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                if (custBills.isNotEmpty()) {
                                    val totalBilled = custBills.sumOf { it.grandTotal }
                                    val totalDue = custBills.sumOf { it.dueAmount }
                                    Surface(
                                        color = if (totalDue > 0.009) RedDueBg else GreenPaidBg,
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = "${custBills.size} Bill(s) · ₹${String.format(Locale.US, "%.0f", totalBilled)}" + if (totalDue > 0.009) " (Due ₹${String.format(Locale.US, "%.0f", totalDue)})" else " (Clear)",
                                            color = if (totalDue > 0.009) RedDueDark else GreenPaid,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                            if (canManageCustomers) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = {
                                            clearFocusAndHideToolbar(focusManager, textToolbar)
                                            editingId = cust.id
                                            name = cust.name
                                            phone = cust.phone
                                            address = cust.address.ifBlank { cust.details }
                                            locationUrl = cust.locationUrl
                                        },
                                        modifier = Modifier.testTag("edit_customer_${cust.id}")
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = "Edit customer", tint = TealAccent)
                                    }
                                    IconButton(
                                        onClick = { onDeleteCustomer(cust.id) },
                                        modifier = Modifier.testTag("delete_customer_${cust.id}")
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete customer", tint = RedDue)
                                    }
                                }
                            }
                        }

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (displayPhone.isNotBlank()) {
                                OutlinedButton(
                                    onClick = {
                                        clearFocusAndHideToolbar(focusManager, textToolbar)
                                        LocationHelper.dialPhoneNumber(context, displayPhone)
                                    },
                                    modifier = Modifier.testTag("call_customer_${cust.id}")
                                ) {
                                    Icon(Icons.Default.Call, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Call")
                                }
                            }

                            if (mapTarget.isNotBlank()) {
                                OutlinedButton(
                                    onClick = {
                                        clearFocusAndHideToolbar(focusManager, textToolbar)
                                        val opened = LocationHelper.openInGoogleMaps(context, mapTarget)
                                        if (!opened) {
                                            Toast.makeText(context, "No Google Maps or browser app found", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = Modifier.testTag("open_customer_map_${cust.id}")
                                ) {
                                    Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(if (cust.locationUrl.isNotBlank()) "View Saved Location on Maps" else "Search Address on Maps")
                                }
                            }

                            if (canManageCustomers && onDeleteBill != null) {
                                OutlinedButton(
                                    onClick = {
                                        clearFocusAndHideToolbar(focusManager, textToolbar)
                                        selectedCustomerForBills = cust
                                    },
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = if (custBills.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    ),
                                    modifier = Modifier.testTag("manage_customer_bills_${cust.id}")
                                ) {
                                    Icon(Icons.Default.ReceiptLong, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = if (custBills.isNotEmpty()) "Customer Bills (${custBills.size}) / Delete" else "Customer Bills (0)",
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(16.dp)) }
    }

    val activeCustForBills = selectedCustomerForBills
    if (activeCustForBills != null) {
        val custBills = remember(bills, activeCustForBills) {
            val custNameLower = activeCustForBills.name.trim().lowercase(Locale.ROOT)
            val custPhoneDigits = activeCustForBills.phone.filter { it.isDigit() }
            val last5 = if (custPhoneDigits.length >= 5) custPhoneDigits.takeLast(5) else ""
            bills.filter { b ->
                val bNameLower = b.customerName.trim().lowercase(Locale.ROOT)
                val bDetails = b.customerDetails
                val nameMatches = custNameLower.isNotBlank() && (bNameLower.contains(custNameLower) || custNameLower.contains(bNameLower))
                val phoneMatches = last5.isNotBlank() && bDetails.contains(last5)
                nameMatches || phoneMatches
            }
        }

        AlertDialog(
            onDismissRequest = { selectedCustomerForBills = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.ReceiptLong, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("Customer Bills (${activeCustForBills.name})")
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (activeCustForBills.phone.isNotBlank()) {
                        Text("Mobile: ${activeCustForBills.phone}", style = MaterialTheme.typography.bodySmall, color = TealAccent)
                    }

                    if (custBills.isEmpty()) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text("No saved bills found for this customer.", style = MaterialTheme.typography.bodyMedium)
                                Text("Any new bill created with this customer's name or mobile will appear here.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    } else {
                        val totalBilled = custBills.sumOf { it.grandTotal }
                        val totalDue = custBills.sumOf { it.dueAmount }
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Total: ₹${String.format(Locale.US, "%.0f", totalBilled)}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                                Text(
                                    text = if (totalDue > 0.009) "Due: ₹${String.format(Locale.US, "%.0f", totalDue)}" else "Status: Fully Paid",
                                    color = if (totalDue > 0.009) RedDue else GreenPaid,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }

                        custBills.forEach { bill ->
                            val isQuotation = DocumentType.fromString(bill.documentType) == DocumentType.QUOTATION
                            val hasDue = !isQuotation && bill.dueAmount > 0.009
                            Card(
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (hasDue) RedDueBg else MaterialTheme.colorScheme.surfaceVariant
                                ),
                                border = BorderStroke(1.dp, if (hasDue) RedDue.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                            ) {
                                Column(modifier = Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "${bill.invoiceNo} · ${bill.date}",
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        Text(
                                            text = "₹${String.format(Locale.US, "%.2f", bill.grandTotal)}",
                                            fontWeight = FontWeight.Bold,
                                            color = if (hasDue) RedDueDark else GreenPaid
                                        )
                                    }
                                    if (bill.createdBy.isNotBlank()) {
                                        Text(
                                            text = "Technician: @${bill.createdBy}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (hasDue) {
                                        Text(
                                            text = "Due Balance: ₹${String.format(Locale.US, "%.2f", bill.dueAmount)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = RedDueDark,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (onOpenBill != null) {
                                            TextButton(onClick = {
                                                selectedCustomerForBills = null
                                                onOpenBill(bill)
                                            }) {
                                                Text("Open")
                                            }
                                        }
                                        if (canManageCustomers && onDeleteBill != null) {
                                            Button(
                                                onClick = { billToDeleteConfirm = bill },
                                                colors = ButtonDefaults.buttonColors(containerColor = RedDue, contentColor = Color.White),
                                                modifier = Modifier.testTag("delete_cust_bill_${bill.invoiceNo}")
                                            ) {
                                                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
                                                Spacer(Modifier.width(4.dp))
                                                Text("Delete Bill", color = Color.White, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedCustomerForBills = null }) {
                    Text("Close")
                }
            }
        )
    }

    val billToDelete = billToDeleteConfirm
    if (billToDelete != null) {
        AlertDialog(
            onDismissRequest = { billToDeleteConfirm = null },
            title = { Text("Delete Customer Bill (${billToDelete.invoiceNo})?") },
            text = {
                Text(
                    "Are you sure you want to delete Invoice ${billToDelete.invoiceNo} for ${billToDelete.customerName} (₹${String.format(Locale.US, "%.2f", billToDelete.grandTotal)})?\n\nThis bill will be moved to Trash (recoverable for 24 hours). Only Admin can delete customer bills."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val idToDelete = billToDelete.id
                        billToDeleteConfirm = null
                        onDeleteBill?.invoke(idToDelete)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RedDue, contentColor = Color.White)
                ) {
                    Text("Delete Bill", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { billToDeleteConfirm = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProductsScreen(
    products: List<ProductEntity>,
    user: UserSession,
    onAddProduct: (name: String, unit: String, rateText: String, folder: String) -> Unit,
    onUpdateProduct: (id: Long, name: String, unit: String, rateText: String, folder: String) -> Unit,
    onDeleteProduct: (Long) -> Unit,
    onAddProductWithStock: ((name: String, unit: String, rateText: String, folder: String, stockQtyText: String, minStockAlertText: String) -> Unit)? = null,
    onUpdateProductWithStock: ((id: Long, name: String, unit: String, rateText: String, folder: String, stockQtyText: String, minStockAlertText: String) -> Unit)? = null
) {
    val canManageProducts = user.isAdmin && !user.isSuperAdmin
    var name by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf("Nos") }
    var rate by remember { mutableStateOf("") }
    var stockQty by remember { mutableStateOf("") }
    var minStockAlert by remember { mutableStateOf("") }
    var folder by remember { mutableStateOf("CCTV Cameras") }
    var folderMenuOpen by remember { mutableStateOf(false) }
    var selectedFolderFilter by remember { mutableStateOf("All") }
    var searchQuery by remember { mutableStateOf("") }
    var editingProduct by remember { mutableStateOf<ProductEntity?>(null) }
    val focusManager = LocalFocusManager.current
    val textToolbar = LocalTextToolbar.current

    val allFolders = remember(products) {
        (DEFAULT_PRODUCT_FOLDERS + products.map { it.folder.ifBlank { "General" } })
            .distinct()
    }
    val existingFolderTabs = remember(products) {
        val dynamic = products.map { it.folder.ifBlank { "General" } }
        val merged = (listOf("Combo") + dynamic).distinct()
        val sorted = merged.sortedWith(
            compareBy<String> { !it.equals("Combo", ignoreCase = true) }
                .thenBy { it.lowercase(Locale.ROOT) }
        )
        listOf("All") + sorted
    }
    val visibleProducts = remember(products, selectedFolderFilter, searchQuery) {
        val searched = BillingRepository.filterProducts(products, searchQuery)
        if (selectedFolderFilter == "All") searched
        else searched.filter { it.folder.ifBlank { "General" }.equals(selectedFolderFilter, ignoreCase = true) }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp)
            .testTag("products_lazy_list"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { Spacer(Modifier.height(4.dp)) }

        if (canManageProducts) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Product Folders & Price Manager (Admin)", style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = "Add products or save ready-made setups in the 'Combo' folder for fast Quotation & Billing.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FilterChip(
                                selected = folder.equals("Combo", ignoreCase = true),
                                onClick = {
                                    folder = "Combo"
                                    if (unit == "Nos" || unit.isBlank()) unit = "Set"
                                    selectedFolderFilter = "Combo"
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFF5B21B6),
                                    selectedLabelColor = Color.White,
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    labelColor = MaterialTheme.colorScheme.onSurface
                                ),
                                label = { Text("Combo Folder (Quotation Setup)", fontWeight = FontWeight.SemiBold) },
                                modifier = Modifier.testTag("quick_select_combo_folder")
                            )
                        }

                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = folder,
                                onValueChange = { folder = it },
                                label = { Text("Folder Name (or type custom)") },
                                leadingIcon = { Icon(Icons.Default.Folder, contentDescription = null, tint = TealAccent) },
                                trailingIcon = {
                                    IconButton(onClick = { folderMenuOpen = true }) {
                                        Icon(Icons.Default.ArrowDropDown, contentDescription = "Pick folder")
                                    }
                                },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus().testTag("new_product_folder")
                            )
                            DropdownMenu(
                                expanded = folderMenuOpen,
                                onDismissRequest = { folderMenuOpen = false }
                            ) {
                                allFolders.forEach { f ->
                                    DropdownMenuItem(
                                        text = { Text(f) },
                                        onClick = {
                                            folder = f
                                            if (f.equals("Combo", ignoreCase = true) && unit == "Nos") {
                                                unit = "Set"
                                            }
                                            folderMenuOpen = false
                                        }
                                    )
                                }
                            }
                        }

                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = {
                                Text(
                                    if (folder.equals("Combo", ignoreCase = true)) {
                                        "Combo Setup Name (e.g. 4 Ch HD Full Combo)"
                                    } else {
                                        "Product Name (e.g. 4 Channel DVR)"
                                    }
                                )
                            },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus().testTag("new_product_name")
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = unit,
                                onValueChange = { unit = it },
                                label = { Text("Unit") },
                                singleLine = true,
                                modifier = Modifier.weight(1f).bringIntoViewOnFocus().testTag("new_product_unit")
                            )
                            OutlinedTextField(
                                value = rate,
                                onValueChange = { rate = it },
                                label = { Text("Default Rate (₹)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier.weight(1.2f).bringIntoViewOnFocus().testTag("new_product_rate")
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = stockQty,
                                onValueChange = { stockQty = it },
                                label = { Text("Opening Stock Qty") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier.weight(1f).bringIntoViewOnFocus().testTag("new_product_stock")
                            )
                            OutlinedTextField(
                                value = minStockAlert,
                                onValueChange = { minStockAlert = it },
                                label = { Text("Low-Stock Alert At") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier.weight(1f).bringIntoViewOnFocus().testTag("new_product_min_stock")
                            )
                        }
                        Button(
                            onClick = {
                                clearFocusAndHideToolbar(focusManager, textToolbar)
                                val resolvedFolder = folder.trim().ifBlank { "General" }
                                val addCall: (String) -> Unit = { nm ->
                                    if (onAddProductWithStock != null) {
                                        onAddProductWithStock(nm, unit, rate, resolvedFolder, stockQty, minStockAlert)
                                    } else {
                                        onAddProduct(nm, unit, rate, resolvedFolder)
                                    }
                                }
                                if (name.isNotBlank()) {
                                    addCall(name)
                                    name = ""
                                    rate = ""
                                    stockQty = ""
                                    minStockAlert = ""
                                } else {
                                    addCall("")
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = GreenPaid, contentColor = Color.White),
                            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("add_product_button")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = Color.White)
                            Text(" Add Product to Folder", fontWeight = FontWeight.Bold, color = Color.White)
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
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Product Folders & Price Catalog", style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = if (user.isSuperAdmin) {
                                "Super Admin read-only view: search or browse all product folders, unit prices, and stock."
                            } else {
                                "Search any product name below to check price, unit & available stock, or browse folders including Combo."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Search & Folder Selector Chips
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { newValue ->
                            searchQuery = newValue
                            if (newValue.isNotBlank() && selectedFolderFilter != "All") {
                                selectedFolderFilter = "All"
                            }
                        },
                        label = { Text("Search Product Name, Price & Stock") },
                        placeholder = { Text("Type dome, bullet, dvr, wire, smps, combo...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TealAccent) },
                        trailingIcon = {
                            if (searchQuery.isNotBlank()) {
                                IconButton(
                                    onClick = { searchQuery = "" },
                                    modifier = Modifier.testTag("product_search_clear")
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear search")
                                }
                            }
                        },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .bringIntoViewOnFocus()
                            .testTag("product_search_input")
                    )

                    if (searchQuery.isNotBlank()) {
                        Text(
                            text = "Found ${visibleProducts.size} matching & related product(s)",
                            style = MaterialTheme.typography.labelMedium,
                            color = TealAccent,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.Folder, contentDescription = null, tint = TealAccent)
                        Text("Product Folders", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    }
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        existingFolderTabs.forEach { folderName ->
                            val count = if (folderName == "All") products.size
                            else products.count { it.folder.ifBlank { "General" }.equals(folderName, ignoreCase = true) }
                            val isCombo = folderName.equals("Combo", ignoreCase = true)
                            FilterChip(
                                selected = selectedFolderFilter == folderName,
                                onClick = { selectedFolderFilter = folderName },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = if (isCombo) Color(0xFF5B21B6) else TealAccent,
                                    selectedLabelColor = Color.White,
                                    containerColor = if (isCombo) Color(0xFFEDE9FE) else MaterialTheme.colorScheme.surfaceVariant,
                                    labelColor = if (isCombo) Color(0xFF5B21B6) else MaterialTheme.colorScheme.onSurface
                                ),
                                label = { Text("$folderName ($count)", fontWeight = FontWeight.SemiBold) },
                                modifier = Modifier.testTag("product_folder_chip_${folderName.replace(" ", "_")}")
                            )
                        }
                    }
                }
            }
        }

        if (visibleProducts.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Inventory2, contentDescription = null, modifier = Modifier.size(44.dp), tint = MaterialTheme.colorScheme.primary)
                        Text("No products found in this folder", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        } else {
            items(visibleProducts, key = { it.id }) { prod ->
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            val prodFolder = prod.folder.ifBlank { "General" }
                            val isComboProd = prodFolder.equals("Combo", ignoreCase = true)
                            Surface(
                                color = if (isComboProd) Color(0xFFEDE9FE) else MaterialTheme.colorScheme.secondaryContainer,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = prodFolder,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isComboProd) Color(0xFF5B21B6) else MaterialTheme.colorScheme.onSecondaryContainer,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                            Text(prod.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "Unit: ${prod.unit} · Rate: ₹${String.format(Locale.US, "%.2f", prod.defaultRate)}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TealAccent,
                                fontWeight = FontWeight.SemiBold
                            )
                            val isLowStock = prod.minStockAlert > 0.0 && prod.stockQty <= prod.minStockAlert
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    color = if (isLowStock) RedDueBg else GreenPaidBg,
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = "Stock: ${formatStockNum(prod.stockQty)} ${prod.unit}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isLowStock) RedDueDark else GreenPaid,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                                if (isLowStock) {
                                    Surface(
                                        color = RedDue,
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = "LOW STOCK (≤${formatStockNum(prod.minStockAlert)})",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White,
                                            fontWeight = FontWeight.Black,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                        if (canManageProducts) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = {
                                        clearFocusAndHideToolbar(focusManager, textToolbar)
                                        editingProduct = prod
                                    },
                                    modifier = Modifier.testTag("edit_product_${prod.id}")
                                ) {
                                    Icon(Icons.Default.Edit, contentDescription = "Edit product", tint = TealAccent)
                                }
                                IconButton(
                                    onClick = { onDeleteProduct(prod.id) },
                                    modifier = Modifier.testTag("delete_product_${prod.id}")
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete product", tint = RedDue)
                                }
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(16.dp)) }
    }

    val productToEdit = editingProduct
    if (canManageProducts && productToEdit != null) {
        EditProductDialog(
            product = productToEdit,
            folderSuggestions = allFolders,
            onDismiss = { editingProduct = null },
            onSave = { updatedName, updatedUnit, updatedRate, updatedFolder, updatedStock, updatedMinAlert ->
                if (onUpdateProductWithStock != null) {
                    onUpdateProductWithStock(
                        productToEdit.id,
                        updatedName,
                        updatedUnit,
                        updatedRate,
                        updatedFolder,
                        updatedStock,
                        updatedMinAlert
                    )
                } else {
                    onUpdateProduct(productToEdit.id, updatedName, updatedUnit, updatedRate, updatedFolder)
                }
                editingProduct = null
            }
        )
    }
}

private fun formatStockNum(v: Double): String =
    if (v % 1.0 == 0.0) v.toInt().toString() else String.format(Locale.US, "%.1f", v)

@Composable
private fun EditProductDialog(
    product: ProductEntity,
    folderSuggestions: List<String>,
    onDismiss: () -> Unit,
    onSave: (name: String, unit: String, rateText: String, folder: String, stockQtyText: String, minStockAlertText: String) -> Unit
) {
    var editName by remember(product.id) { mutableStateOf(product.name) }
    var editUnit by remember(product.id) { mutableStateOf(product.unit) }
    var editFolder by remember(product.id) { mutableStateOf(product.folder.ifBlank { "General" }) }
    var folderMenu by remember { mutableStateOf(false) }
    var editRate by remember(product.id) {
        mutableStateOf(
            if (product.defaultRate == 0.0) "0"
            else if (product.defaultRate % 1.0 == 0.0) product.defaultRate.toInt().toString()
            else String.format(Locale.US, "%.2f", product.defaultRate)
        )
    }
    var editStock by remember(product.id) { mutableStateOf(formatStockNum(product.stockQty)) }
    var editMinAlert by remember(product.id) { mutableStateOf(formatStockNum(product.minStockAlert)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Edit, contentDescription = null, tint = TealAccent) },
        title = { Text("Edit Product, Price & Stock") },
        text = {
            ProvideSafeTextToolbar {
                val dialogFocus = LocalFocusManager.current
                val dialogToolbar = LocalTextToolbar.current
                Column(
                    modifier = Modifier.imePadding().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = editFolder,
                            onValueChange = { editFolder = it },
                            label = { Text("Folder") },
                            trailingIcon = {
                                IconButton(onClick = { folderMenu = true }) {
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = "Pick folder")
                                }
                            },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus().testTag("edit_product_folder_input")
                        )
                        DropdownMenu(expanded = folderMenu, onDismissRequest = { folderMenu = false }) {
                            folderSuggestions.forEach { f ->
                                DropdownMenuItem(
                                    text = { Text(f) },
                                    onClick = { editFolder = f; folderMenu = false }
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text("Product Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus().testTag("edit_product_name_input")
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = editUnit,
                            onValueChange = { editUnit = it },
                            label = { Text("Unit") },
                            singleLine = true,
                            modifier = Modifier.weight(1f).bringIntoViewOnFocus().testTag("edit_product_unit_input")
                        )
                        OutlinedTextField(
                            value = editRate,
                            onValueChange = { editRate = it },
                            label = { Text("Rate (₹)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1.2f).bringIntoViewOnFocus().testTag("edit_product_rate_input")
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = editStock,
                            onValueChange = { editStock = it },
                            label = { Text("Stock Qty") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f).bringIntoViewOnFocus().testTag("edit_product_stock_input")
                        )
                        OutlinedTextField(
                            value = editMinAlert,
                            onValueChange = { editMinAlert = it },
                            label = { Text("Low-Stock Alert") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f).bringIntoViewOnFocus().testTag("edit_product_min_stock_input")
                        )
                    }
                    DisposableEffect(dialogFocus, dialogToolbar) {
                        onDispose {
                            clearFocusAndHideToolbar(dialogFocus, dialogToolbar)
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
                    onSave(editName, editUnit, editRate, editFolder.trim().ifBlank { "General" }, editStock, editMinAlert)
                },
                colors = ButtonDefaults.buttonColors(containerColor = GreenPaid, contentColor = Color.White),
                modifier = Modifier.testTag("save_edited_product_button")
            ) {
                Text("Save Changes", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            val btnFocus = LocalFocusManager.current
            val btnToolbar = LocalTextToolbar.current
            TextButton(onClick = {
                clearFocusAndHideToolbar(btnFocus, btnToolbar)
                onDismiss()
            }) {
                Text("Cancel")
            }
        }
    )
}
