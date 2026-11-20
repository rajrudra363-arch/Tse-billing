package com.example.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.example.data.BillingRepository
import com.example.data.CustomerEntity
import com.example.data.CustomerHelpEntity
import com.example.data.UserSession
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CustomerHelpScreen(
    helpRequests: List<CustomerHelpEntity>,
    customers: List<CustomerEntity>,
    user: UserSession,
    onSaveHelpRequest: (
        id: Long,
        customerName: String,
        phone: String,
        address: String,
        locationUrl: String,
        helpDescription: String,
        status: String
    ) -> Unit,
    onToggleStatus: (CustomerHelpEntity) -> Unit,
    onDeleteHelpRequest: (Long) -> Unit
) {
    val canManage = !user.isStaff && user.isAdmin
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val textToolbar = LocalTextToolbar.current

    var editingId by remember { mutableStateOf(0L) }
    var editingStatus by remember { mutableStateOf("OPEN") }
    var phone by remember { mutableStateOf("") }
    var customerName by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var locationUrl by remember { mutableStateOf("") }
    var helpDescription by remember { mutableStateOf("") }
    var lastAutoMatchedId by remember { mutableStateOf<Long?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var locationBusy by remember { mutableStateOf(false) }

    val matchedCustomers = remember(phone, customers) {
        BillingRepository.findMatchingCustomersByPhone(customers, phone)
    }

    LaunchedEffect(matchedCustomers) {
        val uniqueMatch = matchedCustomers.singleOrNull()
        if (uniqueMatch != null && uniqueMatch.id != lastAutoMatchedId) {
            lastAutoMatchedId = uniqueMatch.id
            if (customerName.isBlank()) customerName = uniqueMatch.name
            val resolvedAddr = uniqueMatch.address.ifBlank { uniqueMatch.details }
            if (address.isBlank() && resolvedAddr.isNotBlank()) address = resolvedAddr
            if (locationUrl.isBlank() && uniqueMatch.locationUrl.isNotBlank()) locationUrl = uniqueMatch.locationUrl
        }
    }

    val filteredRequests = remember(helpRequests, searchQuery) {
        val q = searchQuery.trim()
        if (q.isEmpty()) helpRequests
        else {
            val qDigits = q.filter { it.isDigit() }
            helpRequests.filter { req ->
                val phoneDigits = req.phone.filter { it.isDigit() }
                req.customerName.contains(q, ignoreCase = true) ||
                    req.phone.contains(q, ignoreCase = true) ||
                    req.address.contains(q, ignoreCase = true) ||
                    req.helpDescription.contains(q, ignoreCase = true) ||
                    (qDigits.isNotEmpty() && (phoneDigits.endsWith(qDigits) || phoneDigits.contains(qDigits)))
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
                    locationUrl = url
                } else if (!error.isNullOrBlank()) {
                    Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            Toast.makeText(context, "Location permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    fun applyCustomerSuggestion(cust: CustomerEntity) {
        lastAutoMatchedId = cust.id
        customerName = cust.name
        if (cust.phone.isNotBlank()) phone = cust.phone
        val addr = cust.address.ifBlank { cust.details }
        if (addr.isNotBlank()) address = addr
        if (cust.locationUrl.isNotBlank()) locationUrl = cust.locationUrl
    }

    fun resolveForFormAction(): ResolvedHelpForm {
        val matched = matchedCustomers.firstOrNull()
        val finalName = customerName.trim().ifBlank { matched?.name ?: "" }
        val inputDigits = phone.filter { it.isDigit() }
        val matchedPhone = matched?.phone?.ifBlank { "" } ?: ""
        val finalPhone = if (matched != null && inputDigits.length <= 5 && matchedPhone.isNotBlank()) {
            matchedPhone
        } else {
            phone.trim().ifBlank { matchedPhone }
        }
        val finalAddress = address.trim().ifBlank {
            matched?.let { it.address.ifBlank { it.details } } ?: ""
        }
        val finalLocation = locationUrl.trim().ifBlank { matched?.locationUrl ?: "" }
        return ResolvedHelpForm(
            name = finalName,
            phone = finalPhone,
            address = finalAddress,
            locationUrl = finalLocation
        )
    }

    fun clearForm() {
        editingId = 0L
        editingStatus = "OPEN"
        phone = ""
        customerName = ""
        address = ""
        locationUrl = ""
        helpDescription = ""
        lastAutoMatchedId = null
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp)
            .testTag("help_lazy_list"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { Spacer(Modifier.height(4.dp)) }

        if (canManage) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.SupportAgent, contentDescription = null, tint = TealAccent)
                            Text(
                                text = if (editingId > 0L) "Edit Customer Help Request" else "WhatsApp Customer Help Desk (Admin)",
                                style = MaterialTheme.typography.titleLarge
                            )
                        }
                        Text(
                            text = "WhatsApp par aane wali customer problem yahan add karein. Mobile number (ya last 5 digits) dalne par Customer List se naam, address aur Google location auto-fill ho jayega.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        OutlinedTextField(
                            value = phone,
                            onValueChange = {
                                phone = it
                                if (it.filter { c -> c.isDigit() }.length < 5) {
                                    lastAutoMatchedId = null
                                }
                            },
                            label = { Text("Mobile Number or Last 5 Digits") },
                            leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("help_phone_input")
                        )

                        if (matchedCustomers.isNotEmpty()) {
                            Surface(
                                color = GreenPaidBg,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth().testTag("help_customer_matched_badge")
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = if (matchedCustomers.size == 1) {
                                            "Customer List Match: ${matchedCustomers.first().name} (${matchedCustomers.first().phone.ifBlank { "Saved" }})"
                                        } else {
                                            "${matchedCustomers.size} customers matched — tap to fill details:"
                                        },
                                        style = MaterialTheme.typography.labelMedium,
                                        color = GreenPaid,
                                        fontWeight = FontWeight.Bold
                                    )
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        matchedCustomers.forEach { match ->
                                            FilterChip(
                                                selected = customerName.equals(match.name, ignoreCase = true),
                                                onClick = { applyCustomerSuggestion(match) },
                                                label = { Text("${match.name} · ${match.phone}") },
                                                modifier = Modifier.testTag("help_match_chip_${match.id}")
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        OutlinedTextField(
                            value = customerName,
                            onValueChange = { customerName = it },
                            label = { Text("Customer Name") },
                            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("help_name_input")
                        )

                        OutlinedTextField(
                            value = address,
                            onValueChange = { address = it },
                            label = { Text("Customer Address") },
                            minLines = 2,
                            modifier = Modifier.fillMaxWidth().testTag("help_address_input")
                        )

                        OutlinedTextField(
                            value = locationUrl,
                            onValueChange = { locationUrl = it },
                            label = { Text("Google Maps Location Link / Coordinates") },
                            leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null, tint = TealAccent) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("help_location_input")
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
                                modifier = Modifier.testTag("help_gps_location_button")
                            ) {
                                Icon(Icons.Default.MyLocation, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(if (locationBusy) "Getting GPS..." else "GPS Pin")
                            }

                            OutlinedButton(
                                onClick = {
                                    clearFocusAndHideToolbar(focusManager, textToolbar)
                                    val query = locationUrl.ifBlank { address.ifBlank { customerName } }
                                    if (query.isBlank()) {
                                        Toast.makeText(context, "Enter address or GPS location first", Toast.LENGTH_SHORT).show()
                                    } else {
                                        LocationHelper.openInGoogleMaps(context, query)
                                    }
                                },
                                modifier = Modifier.testTag("help_open_maps_button")
                            ) {
                                Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Open Maps")
                            }
                        }

                        OutlinedTextField(
                            value = helpDescription,
                            onValueChange = { helpDescription = it },
                            label = { Text("Customer Need Help / Issue Description") },
                            placeholder = { Text("e.g. Camera 2 no signal, DVR beep sound, mobile app view setup...") },
                            minLines = 3,
                            modifier = Modifier.fillMaxWidth().testTag("help_description_input")
                        )

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Button(
                                onClick = {
                                    clearFocusAndHideToolbar(focusManager, textToolbar)
                                    val resolved = resolveForFormAction()
                                    onSaveHelpRequest(
                                        editingId,
                                        resolved.name,
                                        resolved.phone,
                                        resolved.address,
                                        resolved.locationUrl,
                                        helpDescription,
                                        editingStatus
                                    )
                                    if (helpDescription.isNotBlank() && (resolved.name.isNotBlank() || resolved.phone.isNotBlank())) {
                                        clearForm()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = GreenPaid),
                                modifier = Modifier.height(48.dp).testTag("save_help_button")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Spacer(Modifier.width(4.dp))
                                Text(if (editingId > 0L) "Update Help" else "Save for Staff", fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = {
                                    clearFocusAndHideToolbar(focusManager, textToolbar)
                                    val resolved = resolveForFormAction()
                                    if (helpDescription.isBlank() || (resolved.name.isBlank() && resolved.phone.isBlank())) {
                                        Toast.makeText(context, "Number aur description enter karein", Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }
                                    onSaveHelpRequest(
                                        editingId,
                                        resolved.name,
                                        resolved.phone,
                                        resolved.address,
                                        resolved.locationUrl,
                                        helpDescription,
                                        editingStatus
                                    )
                                    val msg = LocationHelper.buildHelpShareMessage(
                                        customerName = resolved.name,
                                        phone = resolved.phone,
                                        address = resolved.address,
                                        locationUrl = resolved.locationUrl,
                                        helpDescription = helpDescription
                                    )
                                    LocationHelper.shareText(context, msg, preferWhatsApp = true)
                                    clearForm()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary),
                                modifier = Modifier.height(48.dp).testTag("save_and_share_help_button")
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null)
                                Spacer(Modifier.width(4.dp))
                                Text("Save & Share", fontWeight = FontWeight.Bold)
                            }

                            if (editingId > 0L) {
                                OutlinedButton(
                                    onClick = {
                                        clearFocusAndHideToolbar(focusManager, textToolbar)
                                        clearForm()
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
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.SupportAgent, contentDescription = null, tint = TealAccent)
                            Text("Customer Help Desk (Staff View)", style = MaterialTheme.typography.titleLarge)
                        }
                        Text(
                            text = "Admin adds WhatsApp customer support requests here. Staff can view problem description, call customer, and open Google Maps location.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search help by name, last 5 digits, address or issue...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("help_search_input")
            )
        }

        if (filteredRequests.isEmpty()) {
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
                        Icon(Icons.Default.SupportAgent, contentDescription = null, modifier = Modifier.size(44.dp), tint = MaterialTheme.colorScheme.primary)
                        Text("No customer help requests yet", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        } else {
            items(filteredRequests, key = { it.id }) { req ->
                val isResolved = req.status == "RESOLVED"
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isResolved) MaterialTheme.colorScheme.surface else RedDueBg.copy(alpha = 0.35f)
                    ),
                    border = BorderStroke(1.dp, if (isResolved) GreenPaid.copy(alpha = 0.45f) else AmberWarn)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = req.customerName.ifBlank { "Customer (${req.phone})" },
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = NavyPrimary
                                )
                                if (req.phone.isNotBlank()) {
                                    Text(
                                        text = "Mobile: ${req.phone}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TealAccent,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                if (req.address.isNotBlank()) {
                                    Text(
                                        text = "Address: ${req.address}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (req.locationUrl.isNotBlank()) {
                                    Text(
                                        text = "Google Maps: ${req.locationUrl}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            Surface(
                                color = if (isResolved) GreenPaid else RedDue,
                                shape = RoundedCornerShape(999.dp)
                            ) {
                                Text(
                                    text = if (isResolved) "RESOLVED" else "OPEN",
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Surface(
                            color = if (isResolved) GreenPaidBg else Color.White,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = "NEED HELP / ISSUE:",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isResolved) GreenPaid else RedDueDark
                                )
                                Text(
                                    text = req.helpDescription,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (req.phone.isNotBlank()) {
                                OutlinedButton(
                                    onClick = {
                                        clearFocusAndHideToolbar(focusManager, textToolbar)
                                        LocationHelper.dialPhoneNumber(context, req.phone)
                                    },
                                    modifier = Modifier.testTag("call_help_${req.id}")
                                ) {
                                    Icon(Icons.Default.Call, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Call")
                                }
                            }

                            val mapTarget = req.locationUrl.ifBlank { req.address }
                            if (mapTarget.isNotBlank()) {
                                OutlinedButton(
                                    onClick = {
                                        clearFocusAndHideToolbar(focusManager, textToolbar)
                                        LocationHelper.openInGoogleMaps(context, mapTarget)
                                    },
                                    modifier = Modifier.testTag("map_help_${req.id}")
                                ) {
                                    Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Maps")
                                }
                            }

                            OutlinedButton(
                                onClick = {
                                    clearFocusAndHideToolbar(focusManager, textToolbar)
                                    val msg = LocationHelper.buildHelpShareMessage(
                                        customerName = req.customerName,
                                        phone = req.phone,
                                        address = req.address,
                                        locationUrl = req.locationUrl,
                                        helpDescription = req.helpDescription
                                    )
                                    LocationHelper.shareText(context, msg, preferWhatsApp = false)
                                },
                                modifier = Modifier.testTag("share_help_${req.id}")
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Share")
                            }

                            if (canManage) {
                                Button(
                                    onClick = { onToggleStatus(req) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isResolved) AmberWarn else GreenPaid
                                    ),
                                    modifier = Modifier.testTag("status_help_${req.id}")
                                ) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(if (isResolved) "Reopen" else "Resolve")
                                }

                                IconButton(
                                    onClick = {
                                        clearFocusAndHideToolbar(focusManager, textToolbar)
                                        editingId = req.id
                                        editingStatus = req.status
                                        customerName = req.customerName
                                        phone = req.phone
                                        address = req.address
                                        locationUrl = req.locationUrl
                                        helpDescription = req.helpDescription
                                    },
                                    modifier = Modifier.testTag("edit_help_${req.id}")
                                ) {
                                    Icon(Icons.Default.Edit, contentDescription = "Edit help request", tint = TealAccent)
                                }

                                IconButton(
                                    onClick = { onDeleteHelpRequest(req.id) },
                                    modifier = Modifier.testTag("delete_help_${req.id}")
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete help request", tint = RedDue)
                                }
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(16.dp)) }
    }
}

private data class ResolvedHelpForm(
    val name: String,
    val phone: String,
    val address: String,
    val locationUrl: String
)
