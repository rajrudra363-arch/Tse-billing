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
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material.icons.filled.TaskAlt
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
    accounts: List<UserAccountEntity> = emptyList()
) {
    val canManage = user.isAdmin && !user.isSuperAdmin
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
                    req.acceptedBy.contains(q, ignoreCase = true) ||
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
                            text = "Add WhatsApp customer support requests here. Entering a mobile number or last 5 digits auto-fills customer name, address, and Google Maps link from Customer List.",
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
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
                                "Tap Accept Request to claim a ticket. After 15 minutes of site work, mark Solved / Not Solved and tap Get Bill to invoice with Support Charges."
                            },
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
                placeholder = { Text("Search help by name, last 5 digits, address, or technician...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus().testTag("help_search_input")
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

                // Explicit high-contrast colors on crisp light surface backgrounds
                val cardBg = when {
                    isSolved -> Color(0xFFF0FDF4)
                    isAccepted -> Color(0xFFEFF6FF)
                    isNotSolved -> Color(0xFFFFFBEB)
                    else -> Color(0xFFFEF2F2)
                }
                val cardBorder = when {
                    isSolved -> GreenPaid
                    isAccepted -> Color(0xFF0284C7)
                    isNotSolved -> AmberWarn
                    else -> RedDue
                }
                val badgeBg = when {
                    isSolved -> GreenPaid
                    isAccepted -> Color(0xFF0284C7)
                    isNotSolved -> AmberWarn
                    else -> RedDue
                }
                val badgeText = when {
                    isSolved -> "SOLVED"
                    isAccepted -> "IN PROGRESS"
                    isNotSolved -> "NOT SOLVED"
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

                            if (!user.isSuperAdmin && normalizedStatus == "OPEN") {
                                Button(
                                    onClick = { onAcceptRequest(req.id) },
                                    colors = ButtonDefaults.buttonColors(containerColor = BlueAction, contentColor = Color.White),
                                    modifier = Modifier.testTag("accept_help_${req.id}")
                                ) {
                                    Icon(Icons.Default.TaskAlt, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Accept Request", fontWeight = FontWeight.Bold, color = Color.White)
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
