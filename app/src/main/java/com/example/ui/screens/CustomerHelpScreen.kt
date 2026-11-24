package com.example.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.BillingRepository
import com.example.data.CompanySettings
import com.example.data.CustomerEntity
import com.example.data.CustomerHelpEntity
import com.example.data.HelpCategory
import com.example.data.UserAccountEntity
import com.example.data.UserSession
import com.example.ui.components.bringIntoViewOnFocus
import com.example.ui.components.clearFocusAndHideToolbar
import com.example.ui.theme.AmberWarn
import com.example.ui.theme.BlueAction
import com.example.ui.theme.GoldAccent
import com.example.ui.theme.GreenPaid
import com.example.ui.theme.GreenPaidBg
import com.example.ui.theme.RedDue
import com.example.ui.theme.RedDueBg
import com.example.ui.theme.RedDueDark
import com.example.ui.theme.TealAccent
import com.example.util.BitmapUtils
import com.example.util.DispatchCardGenerator
import com.example.util.LocationHelper
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CustomerHelpScreen(
    helpRequests: List<CustomerHelpEntity>,
    customers: List<CustomerEntity>,
    company: CompanySettings,
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
    onAcceptRequest: (Long) -> Unit,
    onMarkSolved: (Long) -> Unit,
    onMarkNotSolved: (Long) -> Unit,
    onGetBillFromHelp: (CustomerHelpEntity) -> Unit,
    onDeleteHelpRequest: (Long) -> Unit,
    accounts: List<UserAccountEntity> = emptyList(),
    dueInstallationReminders: List<CustomerHelpEntity> = emptyList(),
    onSaveHelpRequestWithCategory: (
        id: Long,
        customerName: String,
        phone: String,
        address: String,
        locationUrl: String,
        helpDescription: String,
        status: String,
        category: String,
        onSaved: () -> Unit
    ) -> Unit = { id, name, phone, address, locationUrl, desc, status, _, onSaved ->
        onSaveHelpRequest(id, name, phone, address, locationUrl, desc, status)
        onSaved()
    },
    onApproveQuotation: (requestId: Long, installationDate: String, assignedTechnician: String, onDone: () -> Unit) -> Unit = { _, _, _, _ -> },
    onRejectQuotation: (requestId: Long, reason: String, onDone: () -> Unit) -> Unit = { _, _, _ -> },
    onRejectHelpRequest: (requestId: Long, reason: String, onDone: () -> Unit) -> Unit = { _, _, _ -> },
    onDismissInstallationReminder: (Long) -> Unit = {}
) {
    val canManage = user.isAdmin && !user.isSuperAdmin
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val textToolbar = LocalTextToolbar.current

    var editingId by remember { mutableStateOf(0L) }
    var editingStatus by remember { mutableStateOf("OPEN") }
    var selectedCategory by remember { mutableStateOf(HelpCategory.REGULAR_SERVICE) }
    var filterCategory by remember { mutableStateOf<HelpCategory?>(null) }
    var phone by remember { mutableStateOf("") }
    var customerName by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var locationUrl by remember { mutableStateOf("") }
    var helpDescription by remember { mutableStateOf("") }
    var lastAutoMatchedId by remember { mutableStateOf<Long?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var locationBusy by remember { mutableStateOf(false) }
    var techQuoteExpanded by remember { mutableStateOf(false) }
    var techQuoteName by remember { mutableStateOf("") }
    var techQuotePhone by remember { mutableStateOf("") }
    var techQuoteAddress by remember { mutableStateOf("") }
    var techQuoteDesc by remember { mutableStateOf("") }
    var approveQuoteTarget by remember { mutableStateOf<CustomerHelpEntity?>(null) }
    var rejectQuoteTarget by remember { mutableStateOf<CustomerHelpEntity?>(null) }
    var rejectHelpTarget by remember { mutableStateOf<CustomerHelpEntity?>(null) }

    // 1-second ticker for 15-minute solve cooldown timers on accepted requests
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val hasActiveCountdown = remember(helpRequests) {
        helpRequests.any { it.status == "ACCEPTED" && it.acceptedAt > 0L }
    }
    LaunchedEffect(hasActiveCountdown) {
        while (hasActiveCountdown) {
            nowMillis = System.currentTimeMillis()
            delay(1000)
        }
    }

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

    val filteredRequests = remember(helpRequests, searchQuery, filterCategory) {
        val q = searchQuery.trim()
        val catFiltered = if (filterCategory == null) helpRequests else helpRequests.filter {
            HelpCategory.fromString(it.category) == filterCategory
        }
        if (q.isEmpty()) catFiltered
        else {
            val qDigits = q.filter { it.isDigit() }
            catFiltered.filter { req ->
                val phoneDigits = req.phone.filter { it.isDigit() }
                req.customerName.contains(q, ignoreCase = true) ||
                    req.phone.contains(q, ignoreCase = true) ||
                    req.address.contains(q, ignoreCase = true) ||
                    req.helpDescription.contains(q, ignoreCase = true) ||
                    req.acceptedBy.contains(q, ignoreCase = true) ||
                    req.installationDate.contains(q, ignoreCase = true) ||
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
        selectedCategory = HelpCategory.REGULAR_SERVICE
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
                        if (dueInstallationReminders.isNotEmpty()) {
                            Surface(
                                color = Color(0xFFFFFBEB),
                                border = BorderStroke(1.5.dp, AmberWarn),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth().testTag("installation_reminders_banner")
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Icon(Icons.Default.NotificationsActive, contentDescription = "Installation reminder", tint = AmberWarn)
                                        Text(
                                            text = "Installation Reminder Due (${dueInstallationReminders.size})",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Black,
                                            color = Color(0xFF92400E)
                                        )
                                    }
                                    dueInstallationReminders.forEach { rem ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "${rem.customerName.ifBlank { rem.phone }} · Date: ${rem.installationDate}",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF78350F),
                                                modifier = Modifier.weight(1f)
                                            )
                                            TextButton(
                                                onClick = { onDismissInstallationReminder(rem.id) },
                                                modifier = Modifier.testTag("dismiss_reminder_${rem.id}")
                                            ) {
                                                Text("Dismiss", fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.SupportAgent, contentDescription = null, tint = TealAccent)
                            Text(
                                text = if (editingId > 0L) "Edit Customer Help Request" else "WhatsApp Customer Help Desk (Admin)",
                                style = MaterialTheme.typography.titleLarge
                            )
                        }
                        Text(
                            text = "Add Regular Service, Emergency Help, Installation, or Quotation requests here. Mobile number auto-fills saved customers.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Text("Request Category", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            HelpCategory.entries.forEach { cat ->
                                val isSelected = selectedCategory == cat
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { selectedCategory = cat },
                                    label = { Text(cat.label, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = if (cat == HelpCategory.EMERGENCY) RedDue else TealAccent,
                                        selectedLabelColor = Color.White
                                    ),
                                    modifier = Modifier.testTag("help_category_${cat.code}")
                                )
                            }
                        }

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
                            modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus().testTag("help_phone_input")
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
                            modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus().testTag("help_name_input")
                        )

                        OutlinedTextField(
                            value = address,
                            onValueChange = { address = it },
                            label = { Text("Customer Address") },
                            minLines = 2,
                            modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus().testTag("help_address_input")
                        )

                        OutlinedTextField(
                            value = locationUrl,
                            onValueChange = { locationUrl = it },
                            label = { Text("Google Maps Location Link / Coordinates") },
                            leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null, tint = TealAccent) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus().testTag("help_location_input")
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
                            label = { Text("Customer Support Issue Description") },
                            placeholder = { Text("e.g. Camera 2 no signal, DVR beep sound, mobile app view setup...") },
                            minLines = 3,
                            modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus().testTag("help_description_input")
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
                                    onSaveHelpRequestWithCategory(
                                        editingId,
                                        resolved.name,
                                        resolved.phone,
                                        resolved.address,
                                        resolved.locationUrl,
                                        helpDescription,
                                        editingStatus,
                                        selectedCategory.code
                                    ) {}
                                    if (helpDescription.isNotBlank() && (resolved.name.isNotBlank() || resolved.phone.isNotBlank())) {
                                        clearForm()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = GreenPaid, contentColor = Color.White),
                                modifier = Modifier.height(48.dp).testTag("save_help_button")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, tint = Color.White)
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    if (editingId > 0L) "Update Help" else "Save for Technicians",
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }

                            Button(
                                onClick = {
                                    clearFocusAndHideToolbar(focusManager, textToolbar)
                                    val resolved = resolveForFormAction()
                                    if (helpDescription.isBlank() || (resolved.name.isBlank() && resolved.phone.isBlank())) {
                                        Toast.makeText(context, "Please enter mobile number and issue description", Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }
                                    onSaveHelpRequestWithCategory(
                                        editingId,
                                        resolved.name,
                                        resolved.phone,
                                        resolved.address,
                                        resolved.locationUrl,
                                        helpDescription,
                                        editingStatus,
                                        selectedCategory.code
                                    ) {}
                                    val msg = LocationHelper.buildHelpShareMessage(
                                        customerName = resolved.name,
                                        phone = resolved.phone,
                                        address = resolved.address,
                                        locationUrl = resolved.locationUrl,
                                        helpDescription = "[${selectedCategory.label}] $helpDescription"
                                    )
                                    LocationHelper.shareText(context, msg, preferWhatsApp = true)
                                    clearForm()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = BlueAction, contentColor = Color.White),
                                modifier = Modifier.height(48.dp).testTag("save_and_share_help_button")
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, tint = Color.White)
                                Spacer(Modifier.width(4.dp))
                                Text("Save & Share", fontWeight = FontWeight.Bold, color = Color.White)
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
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.SupportAgent, contentDescription = null, tint = TealAccent)
                            Text(
                                text = if (user.isSuperAdmin) "Customer Help Desk (Super Admin View)" else "Customer Help Desk (Technician Workflow)",
                                style = MaterialTheme.typography.titleLarge
                            )
                        }
                        Text(
                            text = if (user.isSuperAdmin) {
                                "Super Admin read-only view of all customer help desk tickets."
                            } else {
                                "Accept or Reject assigned Service/Installation requests, submit new Quotation requests for Admin approval, and invoice solved jobs."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (!user.isSuperAdmin) {
                            OutlinedButton(
                                onClick = { techQuoteExpanded = !techQuoteExpanded },
                                modifier = Modifier.testTag("tech_toggle_quotation_form")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(if (techQuoteExpanded) "Hide Quotation Form" else "Submit Quotation Request")
                            }
                            if (techQuoteExpanded) {
                                HorizontalDivider()
                                OutlinedTextField(
                                    value = techQuoteName,
                                    onValueChange = { techQuoteName = it },
                                    label = { Text("Customer Name") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth().testTag("tech_quote_name_input")
                                )
                                OutlinedTextField(
                                    value = techQuotePhone,
                                    onValueChange = { techQuotePhone = it },
                                    label = { Text("Customer Mobile") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth().testTag("tech_quote_phone_input")
                                )
                                OutlinedTextField(
                                    value = techQuoteAddress,
                                    onValueChange = { techQuoteAddress = it },
                                    label = { Text("Site Address") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth().testTag("tech_quote_address_input")
                                )
                                OutlinedTextField(
                                    value = techQuoteDesc,
                                    onValueChange = { techQuoteDesc = it },
                                    label = { Text("Quotation Details / Camera Requirement") },
                                    minLines = 2,
                                    modifier = Modifier.fillMaxWidth().testTag("tech_quote_desc_input")
                                )
                                Button(
                                    onClick = {
                                        clearFocusAndHideToolbar(focusManager, textToolbar)
                                        onSaveHelpRequestWithCategory(
                                            0L,
                                            techQuoteName,
                                            techQuotePhone,
                                            techQuoteAddress,
                                            "",
                                            techQuoteDesc,
                                            "OPEN",
                                            HelpCategory.QUOTATION.code
                                        ) {
                                            techQuoteName = ""
                                            techQuotePhone = ""
                                            techQuoteAddress = ""
                                            techQuoteDesc = ""
                                            techQuoteExpanded = false
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = TealAccent, contentColor = Color.White),
                                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("tech_submit_quotation_button")
                                ) {
                                    Text("Submit Quotation to Admin", fontWeight = FontWeight.Bold, color = Color.White)
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = filterCategory == null,
                        onClick = { filterCategory = null },
                        label = { Text("All (${helpRequests.size})") },
                        modifier = Modifier.testTag("filter_help_category_ALL")
                    )
                    HelpCategory.entries.forEach { cat ->
                        FilterChip(
                            selected = filterCategory == cat,
                            onClick = { filterCategory = if (filterCategory == cat) null else cat },
                            label = { Text(cat.label) },
                            modifier = Modifier.testTag("filter_help_category_${cat.code}")
                        )
                    }
                }
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search help by name, last 5 digits, address, or technician...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus().testTag("help_search_input")
                )
            }
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
                        Text("No customer help requests found", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        } else {
            items(filteredRequests, key = { it.id }) { req ->
                val normalizedStatus = req.status.uppercase(Locale.ROOT)
                val isSolved = normalizedStatus == "SOLVED" || normalizedStatus == "RESOLVED"
                val isAccepted = normalizedStatus == "ACCEPTED"
                val isNotSolved = normalizedStatus == "NOT_SOLVED"

                val categoryObj = HelpCategory.fromString(req.category)
                val isRejected = normalizedStatus == "REJECTED" || req.quotationStatus.equals("REJECTED", ignoreCase = true)
                val catBadgeColor = when (categoryObj) {
                    HelpCategory.EMERGENCY -> RedDue
                    HelpCategory.INSTALLATION -> BlueAction
                    HelpCategory.QUOTATION -> Color(0xFF7C3AED)
                    HelpCategory.REGULAR_SERVICE -> TealAccent
                }

                // Explicit high-contrast colors on crisp light surface backgrounds
                val cardBg = when {
                    isSolved -> Color(0xFFF0FDF4)
                    isAccepted -> Color(0xFFEFF6FF)
                    isNotSolved || isRejected -> Color(0xFFFFFBEB)
                    else -> Color(0xFFFEF2F2)
                }
                val cardBorder = when {
                    isSolved -> GreenPaid
                    isAccepted -> Color(0xFF0284C7)
                    isNotSolved -> AmberWarn
                    isRejected -> RedDueDark
                    else -> RedDue
                }
                val badgeBg = when {
                    isSolved -> GreenPaid
                    isAccepted -> Color(0xFF0284C7)
                    isNotSolved -> AmberWarn
                    isRejected -> RedDueDark
                    else -> RedDue
                }
                val badgeText = when {
                    isSolved -> "SOLVED"
                    isAccepted -> "IN PROGRESS"
                    isNotSolved -> "NOT SOLVED"
                    isRejected -> "REJECTED"
                    else -> "OPEN"
                }

                val remainingWaitMs = if (isAccepted && req.acceptedAt > 0L) {
                    (BillingRepository.HELP_SOLVE_WAIT_MS - (nowMillis - req.acceptedAt)).coerceAtLeast(0L)
                } else 0L
                val canMarkOutcome = !user.isSuperAdmin && isAccepted && (canManage || remainingWaitMs == 0L)

                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = cardBg),
                    border = BorderStroke(1.5.dp, cardBorder)
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
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Surface(
                                        color = catBadgeColor.copy(alpha = 0.14f),
                                        border = BorderStroke(1.dp, catBadgeColor),
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier.testTag("help_category_badge_${req.id}")
                                    ) {
                                        Text(
                                            text = categoryObj.label.uppercase(Locale.US),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Black,
                                            color = catBadgeColor,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                    if (req.quotationStatus.isNotBlank()) {
                                        Text(
                                            text = "Quote: ${req.quotationStatus}",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF7C3AED)
                                        )
                                    }
                                }
                                Text(
                                    text = req.customerName.ifBlank { "Customer (${req.phone})" },
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0B2238)
                                )
                                if (req.phone.isNotBlank()) {
                                    Text(
                                        text = "Mobile: ${req.phone}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color(0xFF0B4F6C),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                if (req.address.isNotBlank()) {
                                    Text(
                                        text = "Address: ${req.address}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color(0xFF1E293B)
                                    )
                                }
                                if (req.installationDate.isNotBlank()) {
                                    Text(
                                        text = "Installation Date: ${req.installationDate}" +
                                            if (req.assignedTechnician.isNotBlank()) " · Assigned: @${req.assignedTechnician}" else "",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = BlueAction
                                    )
                                }
                                if (req.rejectedReason.isNotBlank()) {
                                    Text(
                                        text = "Rejection Reason: ${req.rejectedReason}",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = RedDueDark
                                    )
                                }
                                if (req.locationUrl.isNotBlank()) {
                                    Text(
                                        text = "Google Maps: ${req.locationUrl}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFF0369A1),
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                            Surface(
                                color = badgeBg,
                                shape = RoundedCornerShape(999.dp)
                            ) {
                                Text(
                                    text = badgeText,
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }

                        val assignedTechAccount = remember(accounts, req.acceptedBy) {
                            accounts.firstOrNull { it.username.equals(req.acceptedBy, ignoreCase = true) }
                        }

                        if (req.acceptedBy.isNotBlank()) {
                            if (canManage) {
                                // Admin-Only Technician ID Card with Portrait & Complete Details
                                val techPhotoBmp = remember(assignedTechAccount?.photoBase64) {
                                    BitmapUtils.decodeBase64Bitmap(assignedTechAccount?.photoBase64.orEmpty(), 240)
                                }
                                Surface(
                                    color = Color(0xFF0B2238),
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.5.dp, GoldAccent),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("admin_tech_id_card_${req.id}")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(56.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFF164E63))
                                                .border(2.dp, GoldAccent, CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (techPhotoBmp != null) {
                                                Image(
                                                    bitmap = techPhotoBmp.asImageBitmap(),
                                                    contentDescription = "Technician ID photo",
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentScale = ContentScale.Crop
                                                )
                                            } else {
                                                Icon(
                                                    Icons.Default.Person,
                                                    contentDescription = "Technician avatar",
                                                    tint = GoldAccent,
                                                    modifier = Modifier.size(30.dp)
                                                )
                                            }
                                        }
                                        Column(
                                            modifier = Modifier.weight(1f),
                                            verticalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            Text(
                                                text = "TECHNICIAN ID CARD • ADMIN ONLY",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Black,
                                                color = GoldAccent
                                            )
                                            Text(
                                                text = req.acceptedByName.ifBlank {
                                                    assignedTechAccount?.displayName.orEmpty().ifBlank { req.acceptedBy }
                                                },
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Black,
                                                color = Color.White
                                            )
                                            Text(
                                                text = "ID: @${req.acceptedBy} · Mob: ${
                                                    req.acceptedByPhone.ifBlank { assignedTechAccount?.phone.orEmpty().ifBlank { company.mobile } }
                                                }",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Color(0xFFE2E8F0)
                                            )
                                            Text(
                                                text = "${company.companyName} · Ticket #${req.id} ($badgeText)",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color(0xFF67E8F9)
                                            )
                                        }
                                    }
                                }
                            } else {
                                Surface(
                                    color = Color.White,
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "Assigned Technician: ${req.acceptedByName.ifBlank { req.acceptedBy }} (@${req.acceptedBy})" +
                                            if (req.acceptedByPhone.isNotBlank()) " · ${req.acceptedByPhone}" else "",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF0B2238),
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }

                        // Crisp white issue description surface with dark charcoal text
                        Surface(
                            color = Color.White,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = "CUSTOMER ISSUE / REQUIREMENT:",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Black,
                                    color = if (isSolved) GreenPaid else RedDueDark
                                )
                                Text(
                                    text = req.helpDescription,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF0F172A)
                                )
                            }
                        }

                        if (isAccepted && remainingWaitMs > 0L && !canManage) {
                            val mins = (remainingWaitMs / 60_000L).toInt()
                            val secs = ((remainingWaitMs % 60_000L) / 1000L).toInt()
                            Surface(
                                color = Color(0xFFFFFBEB),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, AmberWarn),
                                modifier = Modifier.fillMaxWidth().testTag("help_wait_timer_${req.id}")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(Icons.Default.HourglassTop, contentDescription = null, tint = AmberWarn, modifier = Modifier.size(16.dp))
                                    Text(
                                        text = "Solved / Not Solved unlocks in ${String.format(Locale.US, "%02d:%02d", mins, secs)}",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF92400E)
                                    )
                                }
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
                                    Text("Call", color = Color(0xFF0B2238))
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
                                    Text("Maps", color = Color(0xFF0B2238))
                                }
                            }

                            if (canManage && categoryObj == HelpCategory.QUOTATION && req.quotationStatus.equals("PENDING", ignoreCase = true)) {
                                Button(
                                    onClick = { approveQuoteTarget = req },
                                    colors = ButtonDefaults.buttonColors(containerColor = GreenPaid, contentColor = Color.White),
                                    modifier = Modifier.testTag("approve_quotation_${req.id}")
                                ) {
                                    Icon(Icons.Default.Event, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Approve & Set Install Date", fontWeight = FontWeight.Bold, color = Color.White)
                                }
                                OutlinedButton(
                                    onClick = { rejectQuoteTarget = req },
                                    border = BorderStroke(1.dp, RedDue),
                                    modifier = Modifier.testTag("reject_quotation_${req.id}")
                                ) {
                                    Icon(Icons.Default.Cancel, contentDescription = null, tint = RedDue, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Reject Quote", color = RedDue, fontWeight = FontWeight.Bold)
                                }
                            }

                            if (!user.isSuperAdmin && normalizedStatus == "OPEN" && !(categoryObj == HelpCategory.QUOTATION && req.quotationStatus.equals("PENDING", ignoreCase = true))) {
                                Button(
                                    onClick = { onAcceptRequest(req.id) },
                                    colors = ButtonDefaults.buttonColors(containerColor = BlueAction, contentColor = Color.White),
                                    modifier = Modifier.testTag("accept_help_${req.id}")
                                ) {
                                    Icon(Icons.Default.TaskAlt, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Accept Request", fontWeight = FontWeight.Bold, color = Color.White)
                                }
                                OutlinedButton(
                                    onClick = { rejectHelpTarget = req },
                                    border = BorderStroke(1.dp, RedDue),
                                    modifier = Modifier.testTag("reject_help_${req.id}")
                                ) {
                                    Icon(Icons.Default.Cancel, contentDescription = null, tint = RedDue, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Reject", color = RedDue, fontWeight = FontWeight.Bold)
                                }
                            }

                            if (canMarkOutcome) {
                                Button(
                                    onClick = { onMarkSolved(req.id) },
                                    colors = ButtonDefaults.buttonColors(containerColor = GreenPaid, contentColor = Color.White),
                                    modifier = Modifier.testTag("solve_help_${req.id}")
                                ) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Solved", fontWeight = FontWeight.Bold, color = Color.White)
                                }
                                OutlinedButton(
                                    onClick = { onMarkNotSolved(req.id) },
                                    modifier = Modifier.testTag("not_solve_help_${req.id}")
                                ) {
                                    Icon(Icons.Default.Cancel, contentDescription = null, tint = RedDue, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Not Solved", color = RedDue, fontWeight = FontWeight.Bold)
                                }
                            }

                            if (!user.isSuperAdmin && isSolved) {
                                if (req.billId.isBlank()) {
                                    Button(
                                        onClick = { onGetBillFromHelp(req) },
                                        colors = ButtonDefaults.buttonColors(containerColor = TealAccent, contentColor = Color.White),
                                        modifier = Modifier.testTag("get_bill_help_${req.id}")
                                    ) {
                                        Icon(Icons.Default.Receipt, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Get Bill", fontWeight = FontWeight.Bold, color = Color.White)
                                    }
                                } else {
                                    Surface(color = GreenPaidBg, shape = RoundedCornerShape(999.dp)) {
                                        Text(
                                            text = "Billed",
                                            color = GreenPaid,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                        )
                                    }
                                }
                            }

                            val techPhotoB64 = accounts
                                .firstOrNull { it.username.equals(req.acceptedBy, ignoreCase = true) }
                                ?.photoBase64
                                .orEmpty()
                            val isAssignedTech = !user.isSuperAdmin &&
                                req.acceptedBy.isNotBlank() &&
                                req.acceptedBy.equals(user.username, ignoreCase = true)

                            if (canManage && req.acceptedBy.isNotBlank()) {
                                Button(
                                    onClick = {
                                        val ok = DispatchCardGenerator.shareDispatchCardToCustomerWhatsApp(
                                            context = context,
                                            company = company,
                                            request = req,
                                            technicianPhotoBase64 = techPhotoB64
                                        )
                                        if (!ok) {
                                            Toast.makeText(context, "Could not create Dispatch Card photo", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = GreenPaid, contentColor = Color.White),
                                    modifier = Modifier.testTag("dispatch_card_help_${req.id}")
                                ) {
                                    Icon(Icons.Default.Badge, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Dispatch Card (WhatsApp)", color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }

                            if ((canManage || isAssignedTech) && req.phone.isNotBlank()) {
                                OutlinedButton(
                                    onClick = {
                                        val chatMsg = if (req.acceptedBy.isNotBlank()) {
                                            "Hello ${req.customerName}, I am ${req.acceptedByName.ifBlank { req.acceptedBy }} (${req.acceptedByPhone.ifBlank { company.mobile }}) from ${company.companyName} regarding your support ticket #${req.id}."
                                        } else {
                                            "Hello ${req.customerName}, regarding your support request with ${company.companyName}."
                                        }
                                        val opened = DispatchCardGenerator.openCustomerWhatsAppChat(context, req.phone, chatMsg)
                                        if (!opened) {
                                            Toast.makeText(context, "WhatsApp not available", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    border = BorderStroke(1.2.dp, GreenPaid),
                                    modifier = Modifier.testTag("wa_chat_help_${req.id}")
                                ) {
                                    Icon(Icons.Default.Share, contentDescription = null, tint = GreenPaid, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("WhatsApp Chat", color = GreenPaid, fontWeight = FontWeight.Bold)
                                }
                            }

                            if (canManage) {
                                IconButton(
                                    onClick = {
                                        clearFocusAndHideToolbar(focusManager, textToolbar)
                                        editingId = req.id
                                        editingStatus = req.status
                                        selectedCategory = HelpCategory.fromString(req.category)
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

    val quoteToApprove = approveQuoteTarget
    if (quoteToApprove != null) {
        val defaultDate = remember(quoteToApprove.id) {
            SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        }
        var installDate by remember(quoteToApprove.id) { mutableStateOf(defaultDate) }
        var assignedTech by remember(quoteToApprove.id) { mutableStateOf("") }
        val techs = remember(accounts) {
            accounts.filter { it.role.equals("technician", ignoreCase = true) || it.role.equals("staff", ignoreCase = true) }
        }
        AlertDialog(
            onDismissRequest = { approveQuoteTarget = null },
            modifier = Modifier.testTag("approve_quotation_dialog"),
            title = { Text("Approve Quotation & Set Installation Date", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "${quoteToApprove.customerName} — ${quoteToApprove.helpDescription}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = installDate,
                        onValueChange = { installDate = it },
                        label = { Text("Installation Date (YYYY-MM-DD)") },
                        placeholder = { Text("2026-11-28") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("approve_install_date_input")
                    )
                    if (techs.isNotEmpty()) {
                        Text("Assign Technician (Optional)", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(
                                selected = assignedTech.isBlank(),
                                onClick = { assignedTech = "" },
                                label = { Text("Any Technician") }
                            )
                            techs.forEach { t ->
                                FilterChip(
                                    selected = assignedTech.equals(t.username, ignoreCase = true),
                                    onClick = { assignedTech = t.username },
                                    label = { Text("@${t.username}") }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onApproveQuotation(quoteToApprove.id, installDate.trim(), assignedTech) {
                            approveQuoteTarget = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GreenPaid, contentColor = Color.White),
                    modifier = Modifier.testTag("confirm_approve_quotation_button")
                ) {
                    Text("Approve & Schedule", fontWeight = FontWeight.Bold, color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { approveQuoteTarget = null }) { Text("Cancel") }
            }
        )
    }

    val quoteToReject = rejectQuoteTarget
    if (quoteToReject != null) {
        var reason by remember(quoteToReject.id) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { rejectQuoteTarget = null },
            modifier = Modifier.testTag("reject_quotation_dialog"),
            title = { Text("Reject Quotation Request", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Rejection Reason (Optional)") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth().testTag("reject_reason_input")
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onRejectQuotation(quoteToReject.id, reason.trim()) {
                            rejectQuoteTarget = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RedDue, contentColor = Color.White),
                    modifier = Modifier.testTag("confirm_reject_quotation_button")
                ) {
                    Text("Reject", fontWeight = FontWeight.Bold, color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { rejectQuoteTarget = null }) { Text("Cancel") }
            }
        )
    }

    val helpToReject = rejectHelpTarget
    if (helpToReject != null) {
        var reason by remember(helpToReject.id) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { rejectHelpTarget = null },
            modifier = Modifier.testTag("reject_help_dialog"),
            title = { Text("Reject Request", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Reason (Optional)") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth().testTag("reject_help_reason_input")
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onRejectHelpRequest(helpToReject.id, reason.trim()) {
                            rejectHelpTarget = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RedDue, contentColor = Color.White),
                    modifier = Modifier.testTag("confirm_reject_help_button")
                ) {
                    Text("Confirm Reject", fontWeight = FontWeight.Bold, color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { rejectHelpTarget = null }) { Text("Cancel") }
            }
        )
    }
}

private data class ResolvedHelpForm(
    val name: String,
    val phone: String,
    val address: String,
    val locationUrl: String
)
