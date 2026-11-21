package com.example.ui

import android.graphics.Bitmap
import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.data.AppLanguage
import com.example.data.BillEntity
import com.example.data.BillingRepository
import com.example.data.BillingTimePolicy
import com.example.data.ChargeType
import com.example.data.CompanySettings
import com.example.data.CustomerEntity
import com.example.data.CustomerHelpEntity
import com.example.data.DocumentType
import com.example.data.InvoiceItem
import com.example.data.InvoiceItemJson
import com.example.data.LoginSecurityState
import com.example.data.PaymentMode
import com.example.data.ProductEntity
import com.example.data.TechnicianSettlementEntity
import com.example.data.UpiSettings
import com.example.data.UserAccountEntity
import com.example.data.UserSession
import com.example.data.VoiceAndNoteSettings
import com.example.util.AppUpdateChecker
import com.example.util.GitHubReleaseInfo
import com.example.util.InvoicePrintPayload
import com.example.util.SoundCue
import com.example.util.SoundEngine
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class MainTab(val route: String) {
    BILL("bill"),
    SAVED("saved"),
    CUSTOMERS("customers"),
    HELP("help"),
    PRODUCTS("products"),
    ANALYTICS("analytics"),
    ADMIN("admin"),
    SETTINGS("settings")
}

data class TechnicianPerformanceSummary(
    val username: String,
    val displayName: String,
    val phone: String,
    val role: String,
    val billCount: Int,
    val quotationCount: Int,
    val helpAcceptedCount: Int,
    val helpSolvedCount: Int,
    val totalBilledAmount: Double,
    val onlineTotal: Double,
    val cashTotal: Double,
    val settledToAdminTotal: Double,
    val totalDueAmount: Double,
    val ratedCount: Int,
    val averageRating: Double,
    val latestReviewNote: String,
    val photoBase64: String = "",
    val bills: List<BillEntity> = emptyList(),
    val quotations: List<BillEntity> = emptyList(),
    val helpRequests: List<CustomerHelpEntity> = emptyList()
) {
    val adminDueBalance: Double
        get() = (cashTotal - settledToAdminTotal).coerceAtLeast(0.0)
}

data class CustomerFolderSummary(
    val customerKey: String,
    val customerName: String,
    val phone: String,
    val address: String,
    val locationUrl: String,
    val bills: List<BillEntity>,
    val quotations: List<BillEntity>,
    val paidCount: Int,
    val dueCount: Int,
    val totalDue: Double,
    val visitingTechnicians: List<String>
)

data class BillDraft(
    val editingId: String? = null,
    val openedFromSaved: Boolean = false,
    val createdBy: String = "",
    val documentType: DocumentType = DocumentType.BILL,
    val isHelpBill: Boolean = false,
    val helpRequestId: Long = 0L,
    val quotationNote: String = "",
    val customerName: String = "",
    val customerDetails: String = "",
    val customerLocationUrl: String = "",
    val invoiceNo: String = "TSE-001",
    val date: String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()),
    val items: List<InvoiceItem> = listOf(InvoiceItem()),
    val extraChargeType: ChargeType = ChargeType.NONE,
    val extraChargeAmountText: String = "0",
    val discountText: String = "0",
    val paidAmountText: String = "0",
    val paymentMode: PaymentMode = PaymentMode.ONLINE,
    val signatureBase64: String = "",
    val customQrText: String = "",
    val staffRating: Int = 0,
    val staffReviewNote: String = "",
    val showSheetPreview: Boolean = false,
    val summaryOpen: Boolean = false
) {
    val itemsSubtotal: Double get() = items.sumOf { it.amount }
    val extraChargeAmount: Double
        get() = if (extraChargeType == ChargeType.NONE) 0.0
        else (extraChargeAmountText.toDoubleOrNull() ?: 0.0).coerceAtLeast(0.0)
    val subtotal: Double get() = itemsSubtotal + extraChargeAmount
    val discount: Double get() = (discountText.toDoubleOrNull() ?: 0.0).coerceAtLeast(0.0)
    val grandTotal: Double get() = (subtotal - discount).coerceAtLeast(0.0)
    val paidAmount: Double get() = (paidAmountText.toDoubleOrNull() ?: 0.0).coerceAtLeast(0.0)
    val dueAmount: Double get() = (grandTotal - paidAmount).coerceAtLeast(0.0)
}

sealed interface BillingEvent {
    data class Toast(val message: String, val isError: Boolean = false) : BillingEvent
    data class PrintPdf(val payload: InvoicePrintPayload) : BillingEvent
    data class SavePdf(val payload: InvoicePrintPayload) : BillingEvent
    data class SharePdf(val payload: InvoicePrintPayload) : BillingEvent
    data class SavePhoto(val payload: InvoicePrintPayload) : BillingEvent
    data class SharePhoto(val payload: InvoicePrintPayload) : BillingEvent
    data class RequestDeviceAuthForBill(
        val title: String,
        val subtitle: String
    ) : BillingEvent
    data class NotifyAppUpdate(val release: GitHubReleaseInfo) : BillingEvent
}

class BillingViewModel(
    private val repository: BillingRepository,
    private val soundEngine: SoundEngine
) : ViewModel() {

    private val _splashVisible = MutableStateFlow(true)
    val splashVisible: StateFlow<Boolean> = _splashVisible.asStateFlow()

    private val _session = MutableStateFlow<UserSession?>(null)
    val session: StateFlow<UserSession?> = _session.asStateFlow()

    private val _activeTab = MutableStateFlow(MainTab.BILL)
    val activeTab: StateFlow<MainTab> = _activeTab.asStateFlow()

    private val _billDraft = MutableStateFlow(BillDraft())
    val billDraft: StateFlow<BillDraft> = _billDraft.asStateFlow()

    private val _billSearchQuery = MutableStateFlow("")
    val billSearchQuery: StateFlow<String> = _billSearchQuery.asStateFlow()

    private val _authError = MutableStateFlow("")
    val authError: StateFlow<String> = _authError.asStateFlow()

    private val _summaryError = MutableStateFlow("")
    val summaryError: StateFlow<String> = _summaryError.asStateFlow()

    private val _exportOptionsPayload = MutableStateFlow<InvoicePrintPayload?>(null)
    val exportOptionsPayload: StateFlow<InvoicePrintPayload?> = _exportOptionsPayload.asStateFlow()

    private val _settlementDialogBill = MutableStateFlow<BillEntity?>(null)
    val settlementDialogBill: StateFlow<BillEntity?> = _settlementDialogBill.asStateFlow()

    private val _availableUpdate = MutableStateFlow<GitHubReleaseInfo?>(null)
    val availableUpdate: StateFlow<GitHubReleaseInfo?> = _availableUpdate.asStateFlow()

    private val _updateCheckBusy = MutableStateFlow(false)
    val updateCheckBusy: StateFlow<Boolean> = _updateCheckBusy.asStateFlow()

    private val _events = MutableSharedFlow<BillingEvent>(extraBufferCapacity = 12)
    val events: SharedFlow<BillingEvent> = _events.asSharedFlow()

    val isAdminConfigured: StateFlow<Boolean?> = repository.isAdminConfigured
        .map<Boolean, Boolean?> { it }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val accounts: StateFlow<List<UserAccountEntity>> = repository.accounts
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val bills: StateFlow<List<BillEntity>> = repository.bills
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val customers: StateFlow<List<CustomerEntity>> = repository.customers
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val helpRequests: StateFlow<List<CustomerHelpEntity>> = repository.helpRequests
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val settlements: StateFlow<List<TechnicianSettlementEntity>> = repository.settlements
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val products: StateFlow<List<ProductEntity>> = repository.products
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val companySettings: StateFlow<CompanySettings> = repository.companySettings
        .stateIn(viewModelScope, SharingStarted.Eagerly, CompanySettings())

    val upiSettings: StateFlow<UpiSettings> = repository.upiSettings
        .stateIn(viewModelScope, SharingStarted.Eagerly, UpiSettings())

    val voiceAndNoteSettings: StateFlow<VoiceAndNoteSettings> = repository.voiceAndNoteSettings
        .stateIn(viewModelScope, SharingStarted.Eagerly, VoiceAndNoteSettings())

    val deviceAuthEnabledByUser: StateFlow<Map<String, Boolean>> = repository.deviceAuthEnabledByUser
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    val soundEnabled: StateFlow<Boolean> = repository.soundEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val language: StateFlow<AppLanguage> = repository.language
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppLanguage.EN)

    val loginSecurityState: StateFlow<LoginSecurityState> = repository.loginSecurityState
        .stateIn(viewModelScope, SharingStarted.Eagerly, LoginSecurityState())

    val visibleBills: StateFlow<List<BillEntity>> = combine(bills, session) { all, user ->
        when {
            user == null -> emptyList()
            user.isAdmin || user.isSuperAdmin -> all
            else -> all.filter { it.createdBy.equals(user.username, ignoreCase = true) }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val filteredBills: StateFlow<List<BillEntity>> = combine(visibleBills, _billSearchQuery) { all, q ->
        val query = q.trim().lowercase(Locale.ROOT)
        if (query.isEmpty()) all
        else all.filter {
            it.invoiceNo.lowercase(Locale.ROOT).contains(query) ||
                it.customerName.lowercase(Locale.ROOT).contains(query) ||
                it.customerDetails.lowercase(Locale.ROOT).contains(query) ||
                it.createdBy.lowercase(Locale.ROOT).contains(query)
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val visibleHelpRequests: StateFlow<List<CustomerHelpEntity>> = combine(helpRequests, session) { all, user ->
        when {
            user == null -> emptyList()
            user.isAdmin || user.isSuperAdmin -> all
            else -> all.filter { req ->
                req.acceptedBy.isBlank() || req.acceptedBy.equals(user.username, ignoreCase = true)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val technicianPerformance: StateFlow<List<TechnicianPerformanceSummary>> = combine(
        accounts,
        bills,
        helpRequests,
        settlements
    ) { accList, docList, helpList, setList ->
        val techAccounts = accList.filter { !it.role.equals("super_admin", ignoreCase = true) }
        val knownUsernames = techAccounts.map { it.username.lowercase(Locale.ROOT) }.toSet()
        val extraCreators = docList
            .map { it.createdBy.trim() }
            .filter { it.isNotBlank() && it.lowercase(Locale.ROOT) !in knownUsernames }
            .distinctBy { it.lowercase(Locale.ROOT) }
            .map { uname -> UserAccountEntity(username = uname, displayName = uname, passwordHash = "", role = "technician") }

        (techAccounts + extraCreators).map { acc ->
            val userDocs = docList.filter { it.createdBy.equals(acc.username, ignoreCase = true) }
            val userBills = userDocs.filter { DocumentType.fromString(it.documentType) == DocumentType.BILL }
            val userQuotations = userDocs.filter { DocumentType.fromString(it.documentType) == DocumentType.QUOTATION }
            val userHelps = helpList.filter { it.acceptedBy.equals(acc.username, ignoreCase = true) }
            val ratedBills = userBills.filter { it.staffRating in 1..5 }
            val avg = if (ratedBills.isNotEmpty()) ratedBills.map { it.staffRating }.average() else 0.0
            val latestNote = ratedBills.firstOrNull { it.staffReviewNote.isNotBlank() }?.staffReviewNote ?: ""
            val onlineTotal = userBills
                .filter { PaymentMode.fromString(it.paymentMode) == PaymentMode.ONLINE }
                .sumOf { it.paidAmount }
            val cashTotal = userBills
                .filter { PaymentMode.fromString(it.paymentMode) == PaymentMode.CASH }
                .sumOf { it.paidAmount }
            val settledTotal = setList
                .filter { it.technicianUsername.equals(acc.username, ignoreCase = true) }
                .sumOf { it.amount }

            TechnicianPerformanceSummary(
                username = acc.username,
                displayName = acc.displayName.ifBlank { acc.username },
                phone = acc.phone,
                role = BillingRepository.normalizeRole(acc.role),
                billCount = userBills.size,
                quotationCount = userQuotations.size,
                helpAcceptedCount = userHelps.size,
                helpSolvedCount = userHelps.count { it.status == "SOLVED" || it.status == "RESOLVED" },
                totalBilledAmount = userBills.sumOf { it.grandTotal },
                onlineTotal = onlineTotal,
                cashTotal = cashTotal,
                settledToAdminTotal = settledTotal,
                totalDueAmount = userBills.sumOf { it.dueAmount },
                ratedCount = ratedBills.size,
                averageRating = avg,
                latestReviewNote = latestNote,
                photoBase64 = acc.photoBase64,
                bills = userBills,
                quotations = userQuotations,
                helpRequests = userHelps
            )
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val customerFolders: StateFlow<List<CustomerFolderSummary>> = combine(
        visibleBills,
        customers
    ) { docs, custList ->
        buildCustomerFolders(docs, custList)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _customerSearchQuery = MutableStateFlow("")
    val customerSearchQuery: StateFlow<String> = _customerSearchQuery.asStateFlow()

    val filteredCustomers: StateFlow<List<CustomerEntity>> = combine(customers, _customerSearchQuery) { all, q ->
        BillingRepository.filterCustomers(all, q)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        viewModelScope.launch {
            repository.seedDefaultsIfNeeded()
            val initialBills = repository.bills.first()
            _billDraft.update { d ->
                if (d.editingId == null) d.copy(invoiceNo = repository.computeNextInvoiceNo(initialBills))
                else d
            }
        }
        viewModelScope.launch {
            soundEnabled.collect { enabled ->
                soundEngine.enabled = enabled
            }
        }
        viewModelScope.launch {
            voiceAndNoteSettings.collect { cfg ->
                soundEngine.applyVoiceModel(cfg.voiceModelId)
            }
        }
        viewModelScope.launch {
            delay(1150)
            _splashVisible.value = false
        }
        checkForAppUpdates(manual = false)
    }

    fun dismissSplash() {
        _splashVisible.value = false
    }

    fun speakLoginWelcome() {
        val cfg = voiceAndNoteSettings.value
        soundEngine.speakEnglish(cfg.loginVoice, cfg.voiceModelId)
    }

    fun availableSystemVoices(): List<Pair<String, String>> = soundEngine.availableSystemVoices()

    fun testVoiceAnnouncement(script: String, modelId: String) {
        val cfg = voiceAndNoteSettings.value
        val sample = script
            .replace("{amount}", "2500")
            .replace("{due}", "500")
            .ifBlank { cfg.postLoginVoice }
        soundEngine.speakEnglish(sample, modelId)
    }

    fun toggleSound() {
        viewModelScope.launch {
            val next = !soundEnabled.value
            repository.setSoundEnabled(next)
            soundEngine.enabled = next
            if (next) {
                soundEngine.play(SoundCue.CLICK)
                emitToast("Sound & Voice Enabled")
            } else {
                emitToast("Sound & Voice Muted")
            }
        }
    }

    fun checkForAppUpdates(manual: Boolean = true) {
        if (_updateCheckBusy.value) return
        viewModelScope.launch {
            _updateCheckBusy.value = true
            val release = AppUpdateChecker.checkLatestRelease(BuildConfig.VERSION_NAME)
            _updateCheckBusy.value = false
            if (release != null) {
                _availableUpdate.value = release
                _events.tryEmit(BillingEvent.NotifyAppUpdate(release))
                if (manual) {
                    emitToast("Update v${release.versionName} available!")
                }
            } else if (manual) {
                emitToast("You are on the latest version (v${BuildConfig.VERSION_NAME})")
            }
        }
    }

    fun dismissUpdateDialog() {
        _availableUpdate.value = null
    }

    fun isCurrentUserDeviceAuthEnabled(): Boolean {
        val user = _session.value ?: return false
        return deviceAuthEnabledByUser.value[user.username.lowercase(Locale.ROOT)] == true
    }

    fun setDeviceAuthForCurrentUser(enabled: Boolean) {
        val user = _session.value ?: return
        if (user.isSuperAdmin) {
            emitToast("Super Admin account is read-only", isError = true)
            return
        }
        viewModelScope.launch {
            repository.setDeviceAuthEnabled(user.username, enabled)
            soundEngine.play(SoundCue.SAVE)
            emitToast(
                if (enabled) "Device Authentication enabled for @${user.username}"
                else "Device Authentication disabled"
            )
        }
    }

    fun selectTab(tab: MainTab) {
        val currentSession = _session.value ?: return
        val allowed = when {
            currentSession.isAdmin -> true
            currentSession.isSuperAdmin -> tab == MainTab.ANALYTICS ||
                tab == MainTab.SAVED ||
                tab == MainTab.CUSTOMERS ||
                tab == MainTab.HELP ||
                tab == MainTab.PRODUCTS
            else -> tab == MainTab.BILL ||
                tab == MainTab.SAVED ||
                tab == MainTab.CUSTOMERS ||
                tab == MainTab.HELP ||
                tab == MainTab.PRODUCTS ||
                tab == MainTab.SETTINGS
        }
        if (!allowed) {
            soundEngine.play(SoundCue.ERROR)
            val msg = if (currentSession.isSuperAdmin) {
                "Super Admin is view-only (Analytics & History)"
            } else {
                "Admin access required"
            }
            emitToast(msg, isError = true)
            return
        }
        soundEngine.play(SoundCue.CLICK)
        _activeTab.value = tab
    }

    /* ===== Authentication ===== */
    fun clearAuthError() {
        _authError.value = ""
    }

    fun setupAdmin(username: String, password: String, confirmPassword: String) {
        val cleanUser = username.trim()
        if (cleanUser.length < 3) {
            _authError.value = "Admin username must be at least 3 characters."
            soundEngine.play(SoundCue.ERROR)
            return
        }
        if (password.length < 6) {
            _authError.value = "Admin password must be at least 6 characters."
            soundEngine.play(SoundCue.ERROR)
            return
        }
        if (password != confirmPassword) {
            _authError.value = "Passwords do not match."
            soundEngine.play(SoundCue.ERROR)
            return
        }
        viewModelScope.launch {
            repository.setupAdmin(cleanUser, password)
            _authError.value = ""
            soundEngine.play(SoundCue.SAVE)
            emitToast("Admin account created. Please log in.")
        }
    }

    fun login(
        username: String,
        password: String,
        antiRobotPassed: Boolean = true,
        nowMillis: Long = System.currentTimeMillis()
    ) {
        val cachedSecurity = loginSecurityState.value
        if (cachedSecurity.isLocked(nowMillis)) {
            val remText = formatLockRemaining(cachedSecurity.remainingLockMillis(nowMillis))
            _authError.value = "Too many wrong password attempts (5/5). Login locked for $remText."
            soundEngine.play(SoundCue.ERROR)
            return
        }
        if (username.isBlank()) {
            _authError.value = "Please select a username."
            soundEngine.play(SoundCue.ERROR)
            return
        }
        if (!antiRobotPassed) {
            _authError.value = "Please complete the Anti-Robot verification check first."
            soundEngine.play(SoundCue.ERROR)
            return
        }
        if (password.isBlank()) {
            _authError.value = "Please enter password."
            soundEngine.play(SoundCue.ERROR)
            return
        }
        viewModelScope.launch {
            val latestSecurity = repository.getLoginSecurityState()
            if (latestSecurity.isLocked(nowMillis)) {
                val remText = formatLockRemaining(latestSecurity.remainingLockMillis(nowMillis))
                _authError.value = "Too many wrong password attempts (5/5). Login locked for $remText."
                soundEngine.play(SoundCue.ERROR)
                return@launch
            }
            val user = repository.authenticate(username, password)
            if (user == null) {
                val updated = repository.recordLoginFailure(nowMillis)
                if (updated.isLocked(nowMillis)) {
                    _authError.value = "Wrong password entered 5 times. Login locked for 2 hours (02:00:00)."
                } else {
                    val rem = updated.remainingAttempts(nowMillis)
                    _authError.value = "Incorrect password (${updated.failedAttempts}/${LoginSecurityState.MAX_ATTEMPTS}). $rem attempt(s) remaining before 2-hour lock."
                }
                soundEngine.play(SoundCue.ERROR)
            } else {
                repository.resetLoginSecurityState()
                _authError.value = ""
                _session.value = user
                _activeTab.value = if (user.isSuperAdmin) MainTab.ANALYTICS else MainTab.BILL
                soundEngine.play(SoundCue.SAVE)
                emitToast("Login successful — Welcome ${user.displayName} (${user.roleDisplayLabel})")
                val cfg = voiceAndNoteSettings.value
                soundEngine.speakEnglish(cfg.postLoginVoice, cfg.voiceModelId)
            }
        }
    }

    private fun formatLockRemaining(millis: Long): String {
        val totalSec = ((millis + 999L) / 1000L).coerceAtLeast(0L)
        val hours = totalSec / 3600L
        val mins = (totalSec % 3600L) / 60L
        val secs = totalSec % 60L
        return String.format(Locale.US, "%02d:%02d:%02d", hours, mins, secs)
    }

    fun logout() {
        _session.value = null
        _billDraft.update { it.copy(summaryOpen = false) }
        _summaryError.value = ""
        _authError.value = ""
        soundEngine.play(SoundCue.CLICK)
    }

    fun resetAdminPassword(
        recoveryPassword: String,
        targetAdminUsername: String?,
        newPassword: String,
        confirmPassword: String,
        onResult: (ok: Boolean, message: String) -> Unit
    ) {
        if (recoveryPassword.isBlank()) {
            soundEngine.play(SoundCue.ERROR)
            onResult(false, "Please enter Default Recovery Password.")
            return
        }
        if (newPassword.length < 6) {
            soundEngine.play(SoundCue.ERROR)
            onResult(false, "New password must be at least 6 characters.")
            return
        }
        if (newPassword != confirmPassword) {
            soundEngine.play(SoundCue.ERROR)
            onResult(false, "New passwords do not match.")
            return
        }
        viewModelScope.launch {
            val res = repository.resetAdminPasswordWithRecovery(
                recoveryPassword = recoveryPassword,
                targetAdminUsername = targetAdminUsername,
                newPassword = newPassword
            )
            if (res.isSuccess) {
                val adminName = res.getOrNull() ?: "Admin"
                soundEngine.play(SoundCue.SAVE)
                val msg = "Admin (@$adminName) password has been reset. Please log in."
                emitToast(msg)
                onResult(true, msg)
            } else {
                soundEngine.play(SoundCue.ERROR)
                onResult(false, res.exceptionOrNull()?.message ?: "Recovery failed")
            }
        }
    }

    /* ===== Bill & Quotation Editor ===== */
    fun setDocumentType(docType: DocumentType) {
        val current = _session.value ?: return
        if (current.isSuperAdmin) return
        soundEngine.play(SoundCue.CLICK)
        _billDraft.update { d ->
            d.copy(
                documentType = docType,
                summaryOpen = false
            )
        }
    }

    fun updateQuotationNote(note: String) {
        _billDraft.update { it.copy(quotationNote = note) }
    }

    fun searchCustomerByMobileInput(mobileQuery: String) {
        val digits = mobileQuery.filter { it.isDigit() }
        if (digits.length < 5) return
        val matched = BillingRepository.findMatchingCustomersByPhone(customers.value, mobileQuery).firstOrNull()
        if (matched != null) {
            selectCustomerSuggestion(matched)
            emitToast("Customer auto-filled: ${matched.name}")
        }
    }

    fun updateCustomerName(name: String) {
        val matched = customers.value.firstOrNull { it.name.equals(name.trim(), ignoreCase = true) }
        _billDraft.update { d ->
            val matchedDetails = matched?.let { c ->
                c.details.ifBlank { listOf(c.address, c.phone).filter { it.isNotBlank() }.joinToString("\n") }
            }
            d.copy(
                customerName = name,
                customerDetails = matchedDetails ?: d.customerDetails,
                customerLocationUrl = matched?.locationUrl ?: d.customerLocationUrl
            )
        }
    }

    fun selectCustomerSuggestion(customer: CustomerEntity) {
        soundEngine.play(SoundCue.CLICK)
        val combined = customer.details.ifBlank {
            listOf(customer.address, customer.phone).filter { it.isNotBlank() }.joinToString("\n")
        }
        _billDraft.update {
            it.copy(
                customerName = customer.name,
                customerDetails = combined,
                customerLocationUrl = customer.locationUrl
            )
        }
    }

    fun updateCustomerDetails(details: String) {
        _billDraft.update { it.copy(customerDetails = details) }
    }

    fun updateCustomerLocationUrl(url: String) {
        _billDraft.update { it.copy(customerLocationUrl = url) }
    }

    fun updateInvoiceNo(no: String) {
        _billDraft.update { it.copy(invoiceNo = no) }
    }

    fun updateInvoiceDate(date: String) {
        _billDraft.update { it.copy(date = date) }
    }

    fun updateExtraChargeType(type: ChargeType) {
        soundEngine.play(SoundCue.CLICK)
        _billDraft.update {
            if (type == ChargeType.NONE) it.copy(extraChargeType = ChargeType.NONE, extraChargeAmountText = "0")
            else it.copy(extraChargeType = type)
        }
    }

    fun updateExtraChargeAmount(value: String) {
        _billDraft.update { it.copy(extraChargeAmountText = value) }
    }

    fun updateDiscount(value: String) {
        _billDraft.update { it.copy(discountText = value) }
    }

    fun updatePaidAmount(value: String) {
        _billDraft.update { it.copy(paidAmountText = value) }
    }

    fun updatePaymentMode(mode: PaymentMode) {
        soundEngine.play(SoundCue.CLICK)
        _billDraft.update { it.copy(paymentMode = mode) }
    }

    fun updateCustomQrText(text: String) {
        _billDraft.update { it.copy(customQrText = text) }
    }

    fun updateStaffRatingInDraft(rating: Int) {
        _billDraft.update { it.copy(staffRating = rating.coerceIn(0, 5)) }
    }

    fun updateStaffReviewInDraft(note: String) {
        _billDraft.update { it.copy(staffReviewNote = note) }
    }

    fun toggleSheetPreview(show: Boolean) {
        soundEngine.play(SoundCue.CLICK)
        _billDraft.update { it.copy(showSheetPreview = show) }
    }

    fun updateSignatureBitmap(bitmap: Bitmap?) {
        val encoded = if (bitmap != null) {
            val out = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 95, out)
            Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
        } else {
            ""
        }
        _billDraft.update { it.copy(signatureBase64 = encoded) }
    }

    fun addItem() {
        val current = _billDraft.value.items
        if (current.size >= 15) {
            soundEngine.play(SoundCue.ERROR)
            emitToast("Maximum 15 items allowed in one document", isError = true)
            return
        }
        soundEngine.play(SoundCue.ADD)
        _billDraft.update { it.copy(items = it.items + InvoiceItem()) }
    }

    fun selectFolderForRow(index: Int, folder: String) {
        soundEngine.play(SoundCue.CLICK)
        _billDraft.update { draft ->
            val updated = draft.items.toMutableList()
            if (index in updated.indices) {
                updated[index] = updated[index].copy(folder = folder)
            }
            draft.copy(items = updated)
        }
    }

    fun selectProductForRow(index: Int, product: ProductEntity) {
        soundEngine.play(SoundCue.CLICK)
        _billDraft.update { draft ->
            val updated = draft.items.toMutableList()
            if (index in updated.indices) {
                val cur = updated[index]
                updated[index] = cur.copy(
                    folder = product.folder,
                    description = product.name,
                    unit = product.unit,
                    rate = product.defaultRate
                )
            }
            draft.copy(items = updated)
        }
    }

    fun updateItemFields(
        index: Int,
        description: String? = null,
        qty: Double? = null,
        unit: String? = null,
        rate: Double? = null
    ) {
        val isAdmin = _session.value?.isAdmin == true
        _billDraft.update { draft ->
            val updated = draft.items.toMutableList()
            if (index in updated.indices) {
                val cur = updated[index]
                updated[index] = if (isAdmin) {
                    cur.copy(
                        description = description ?: cur.description,
                        qty = qty ?: cur.qty,
                        unit = unit ?: cur.unit,
                        rate = rate ?: cur.rate
                    )
                } else {
                    cur.copy(qty = qty ?: cur.qty)
                }
            }
            draft.copy(items = updated)
        }
    }

    fun removeItem(index: Int) {
        soundEngine.play(SoundCue.DELETE)
        _billDraft.update { draft ->
            val updated = draft.items.toMutableList()
            if (index in updated.indices) {
                updated.removeAt(index)
            }
            if (updated.isEmpty()) updated.add(InvoiceItem())
            draft.copy(items = updated)
        }
    }

    fun resetBill() {
        soundEngine.play(SoundCue.CLICK)
        _billDraft.value = BillDraft(
            invoiceNo = repository.computeNextInvoiceNo(bills.value),
            date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        )
    }

    /* ===== Summary & Device-Authenticated Bill Generation ===== */
    fun openSummaryModal(nowMillis: Long = System.currentTimeMillis()) {
        val user = _session.value ?: return
        if (user.isSuperAdmin) {
            soundEngine.play(SoundCue.ERROR)
            emitToast("Super Admin account cannot generate bills", isError = true)
            return
        }
        if (BillingTimePolicy.isNightClosedNow(nowMillis)) {
            soundEngine.play(SoundCue.ERROR)
            emitToast("Billing system is closed daily from 12:01 AM to 6:00 AM.", isError = true)
            return
        }
        val draft = _billDraft.value
        if (draft.invoiceNo.isBlank()) {
            soundEngine.play(SoundCue.ERROR)
            emitToast("Please enter Invoice / Quotation number", isError = true)
            return
        }
        val hasItems = draft.items.any { it.description.isNotBlank() || it.rate > 0.0 } ||
            (draft.extraChargeType != ChargeType.NONE && draft.extraChargeAmount > 0.0)
        if (!hasItems) {
            soundEngine.play(SoundCue.ERROR)
            emitToast("Please add at least one product or service charge first", isError = true)
            return
        }
        _summaryError.value = ""
        soundEngine.play(SoundCue.CLICK)
        val cfg = voiceAndNoteSettings.value
        soundEngine.speakEnglish(cfg.billGeneratingVoice, cfg.voiceModelId)
        _billDraft.update { d ->
            val defaultPaid = if (d.editingId == null && d.paidAmount == 0.0 && d.documentType == DocumentType.BILL) {
                formatInputNum(d.grandTotal)
            } else {
                d.paidAmountText
            }
            d.copy(summaryOpen = true, paidAmountText = defaultPaid)
        }
    }

    fun closeSummaryModal() {
        _summaryError.value = ""
        _billDraft.update { it.copy(summaryOpen = false) }
    }

    fun requestBillGenerationWithDeviceAuth(nowMillis: Long = System.currentTimeMillis()) {
        val user = _session.value ?: return
        if (user.isSuperAdmin) {
            _summaryError.value = "Super Admin cannot generate bills."
            soundEngine.play(SoundCue.ERROR)
            return
        }
        if (BillingTimePolicy.isNightClosedNow(nowMillis)) {
            _summaryError.value = "Billing system is closed from 12:01 AM to 6:00 AM."
            soundEngine.play(SoundCue.ERROR)
            return
        }
        if (!isCurrentUserDeviceAuthEnabled()) {
            _summaryError.value = "Mandatory: Please enable Device Authentication (Face / Fingerprint / Screen Lock) in Settings first."
            soundEngine.play(SoundCue.ERROR)
            emitToast("Enable Device Authentication in Settings to generate bills", isError = true)
            return
        }
        val draft = _billDraft.value
        if (draft.invoiceNo.isBlank()) {
            _summaryError.value = "Invoice number is required."
            soundEngine.play(SoundCue.ERROR)
            return
        }
        _summaryError.value = ""
        val docLabel = if (draft.documentType == DocumentType.QUOTATION) "Quotation" else "Bill"
        _events.tryEmit(
            BillingEvent.RequestDeviceAuthForBill(
                title = "Verify Device to Generate $docLabel",
                subtitle = "${draft.invoiceNo} · Total ₹${String.format(Locale.US, "%.2f", draft.grandTotal)}"
            )
        )
    }

    fun commitGeneratedBill(onDone: (() -> Unit)? = null) {
        val currentUser = _session.value ?: return
        if (currentUser.isSuperAdmin) return
        val draft = _billDraft.value
        if (draft.openedFromSaved && !currentUser.isAdmin) {
            soundEngine.play(SoundCue.ERROR)
            emitToast("Only Admin can modify saved bills", isError = true)
            return
        }

        val billId = draft.editingId ?: "b_${System.currentTimeMillis()}"
        val billCreator = if (draft.editingId != null && draft.createdBy.isNotBlank()) {
            draft.createdBy
        } else {
            currentUser.username
        }
        val isQuotation = draft.documentType == DocumentType.QUOTATION
        val paidVal = if (isQuotation) 0.0 else draft.paidAmount
        val dueVal = if (isQuotation) 0.0 else (draft.grandTotal - paidVal).coerceAtLeast(0.0)
        val ratingVal = if (isQuotation) 0 else draft.staffRating.coerceIn(0, 5)
        val reviewVal = if (isQuotation) "" else draft.staffReviewNote.trim()

        val entity = BillEntity(
            id = billId,
            invoiceNo = draft.invoiceNo.trim(),
            date = draft.date.trim(),
            customerName = draft.customerName.trim(),
            customerDetails = draft.customerDetails.trim(),
            itemsJson = InvoiceItemJson.toJson(draft.items),
            subtotal = draft.subtotal,
            discount = if (isQuotation) 0.0 else draft.discount,
            grandTotal = draft.grandTotal,
            paidAmount = paidVal,
            dueAmount = dueVal,
            paymentMode = if (isQuotation) "Quotation" else draft.paymentMode.label,
            signatureBase64 = if (isQuotation) "" else draft.signatureBase64,
            customQrText = if (isQuotation) "" else draft.customQrText.trim(),
            createdBy = billCreator,
            updatedAt = System.currentTimeMillis(),
            extraChargeType = draft.extraChargeType.name,
            extraChargeAmount = draft.extraChargeAmount,
            staffRating = ratingVal,
            staffReviewNote = reviewVal,
            documentType = draft.documentType.name,
            isHelpBill = draft.isHelpBill && !isQuotation,
            helpRequestId = if (isQuotation) 0L else draft.helpRequestId,
            quotationNote = draft.quotationNote.trim()
        )

        viewModelScope.launch {
            repository.saveBill(entity, draft.customerLocationUrl)
            _billDraft.update { it.copy(editingId = billId, createdBy = billCreator, summaryOpen = false) }
            soundEngine.play(SoundCue.SAVE)

            val cfg = voiceAndNoteSettings.value
            val amtSpeech = formatAmountForSpeech(entity.grandTotal)
            val dueSpeech = formatAmountForSpeech(entity.dueAmount)
            val spoken = cfg.billCompletedVoice
                .replace("{amount}", amtSpeech)
                .replace("{due}", dueSpeech)
                .ifBlank {
                    "Bill generated. Total amount is $amtSpeech rupees. Thank you for choosing Total Solution Enterprises."
                }
            soundEngine.speakEnglish(spoken, cfg.voiceModelId)

            val payload = billToPayload(entity)
            _exportOptionsPayload.value = payload
            emitToast(
                if (isQuotation) "Quotation ${entity.invoiceNo} generated & saved!"
                else "Bill ${entity.invoiceNo} generated & saved!"
            )
            onDone?.invoke()
        }
    }

    fun openBillForEdit(bill: BillEntity) {
        val currentUser = _session.value ?: return
        if (!currentUser.isAdmin) {
            soundEngine.play(SoundCue.ERROR)
            emitToast("Only Admin can edit saved bills", isError = true)
            return
        }
        val parsedItems = InvoiceItemJson.fromJson(bill.itemsJson).ifEmpty { listOf(InvoiceItem()) }
        val matchedLoc = customers.value.firstOrNull { it.name.equals(bill.customerName.trim(), ignoreCase = true) }?.locationUrl ?: ""
        _billDraft.value = BillDraft(
            editingId = bill.id,
            openedFromSaved = true,
            createdBy = bill.createdBy,
            documentType = DocumentType.fromString(bill.documentType),
            isHelpBill = bill.isHelpBill,
            helpRequestId = bill.helpRequestId,
            quotationNote = bill.quotationNote,
            customerName = bill.customerName,
            customerDetails = bill.customerDetails,
            customerLocationUrl = matchedLoc,
            invoiceNo = bill.invoiceNo,
            date = bill.date,
            items = parsedItems,
            extraChargeType = ChargeType.fromString(bill.extraChargeType),
            extraChargeAmountText = formatInputNum(bill.extraChargeAmount),
            discountText = formatInputNum(bill.discount),
            paidAmountText = formatInputNum(bill.paidAmount),
            paymentMode = PaymentMode.fromString(bill.paymentMode),
            signatureBase64 = bill.signatureBase64,
            customQrText = bill.customQrText,
            staffRating = bill.staffRating,
            staffReviewNote = bill.staffReviewNote
        )
        _activeTab.value = MainTab.BILL
        soundEngine.play(SoundCue.CLICK)
    }

    fun printBillDirect(bill: BillEntity) {
        soundEngine.play(SoundCue.CLICK)
        _events.tryEmit(BillingEvent.PrintPdf(billToPayload(bill)))
    }

    fun saveBillPdfDirect(bill: BillEntity) {
        soundEngine.play(SoundCue.CLICK)
        _events.tryEmit(BillingEvent.SavePdf(billToPayload(bill)))
    }

    fun shareBillPdfDirect(bill: BillEntity) {
        soundEngine.play(SoundCue.CLICK)
        _events.tryEmit(BillingEvent.SharePdf(billToPayload(bill)))
    }

    fun saveBillPhotoDirect(bill: BillEntity) {
        soundEngine.play(SoundCue.CLICK)
        _events.tryEmit(BillingEvent.SavePhoto(billToPayload(bill)))
    }

    fun shareBillPhotoDirect(bill: BillEntity) {
        soundEngine.play(SoundCue.CLICK)
        _events.tryEmit(BillingEvent.SharePhoto(billToPayload(bill)))
    }

    fun billToPayload(bill: BillEntity): InvoicePrintPayload {
        return InvoicePrintPayload(
            company = companySettings.value,
            upi = upiSettings.value,
            invoiceNo = bill.invoiceNo,
            date = bill.date,
            customerName = bill.customerName,
            customerDetails = bill.customerDetails,
            items = InvoiceItemJson.fromJson(bill.itemsJson),
            subtotal = bill.subtotal,
            discount = bill.discount,
            grandTotal = bill.grandTotal,
            paidAmount = bill.paidAmount,
            dueAmount = bill.dueAmount,
            paymentMode = PaymentMode.fromString(bill.paymentMode),
            signatureBase64 = bill.signatureBase64,
            customQrText = bill.customQrText,
            extraChargeType = ChargeType.fromString(bill.extraChargeType),
            extraChargeAmount = bill.extraChargeAmount,
            documentType = DocumentType.fromString(bill.documentType),
            isHelpBill = bill.isHelpBill,
            helpRequestId = bill.helpRequestId,
            quotationNote = bill.quotationNote
        )
    }

    fun deleteBill(id: String) {
        val currentUser = _session.value ?: return
        if (!currentUser.isAdmin) {
            soundEngine.play(SoundCue.ERROR)
            emitToast("Only Admin can delete saved bills", isError = true)
            return
        }
        viewModelScope.launch {
            val deleted = repository.deleteBill(id)
            if (!deleted) {
                soundEngine.play(SoundCue.ERROR)
                emitToast("Star rating is locked — rated bills cannot be deleted", isError = true)
                return@launch
            }
            if (_billDraft.value.editingId == id) {
                _billDraft.update { it.copy(editingId = null) }
            }
            soundEngine.play(SoundCue.DELETE)
            emitToast("Record deleted")
        }
    }

    fun updateBillSearch(q: String) {
        _billSearchQuery.value = q
    }

    private fun formatAmountForSpeech(amount: Double): String {
        val rounded = kotlin.math.round(amount * 100.0) / 100.0
        return if (rounded % 1.0 == 0.0) rounded.toLong().toString()
        else String.format(Locale.US, "%.2f", rounded)
    }

    fun dismissExportOptions() {
        _exportOptionsPayload.value = null
    }

    fun triggerPrintA4(payload: InvoicePrintPayload) {
        soundEngine.play(SoundCue.CLICK)
        _events.tryEmit(BillingEvent.PrintPdf(payload))
    }

    fun triggerSavePdf(payload: InvoicePrintPayload) {
        soundEngine.play(SoundCue.CLICK)
        _events.tryEmit(BillingEvent.SavePdf(payload))
    }

    fun triggerSharePdf(payload: InvoicePrintPayload) {
        soundEngine.play(SoundCue.CLICK)
        _events.tryEmit(BillingEvent.SharePdf(payload))
    }

    fun triggerSavePhoto(payload: InvoicePrintPayload) {
        soundEngine.play(SoundCue.CLICK)
        _events.tryEmit(BillingEvent.SavePhoto(payload))
    }

    fun triggerSharePhoto(payload: InvoicePrintPayload) {
        soundEngine.play(SoundCue.CLICK)
        _events.tryEmit(BillingEvent.SharePhoto(payload))
    }

    fun openSettlementReviewForExport(payload: InvoicePrintPayload) {
        soundEngine.play(SoundCue.CLICK)
        _exportOptionsPayload.value = null
        val editingId = _billDraft.value.editingId
        viewModelScope.launch {
            val matched = (if (!editingId.isNullOrBlank()) repository.getBillById(editingId) else null)
                ?: bills.value.firstOrNull { it.invoiceNo == payload.invoiceNo }
            if (matched != null) {
                _settlementDialogBill.value = matched
            } else {
                emitToast("Bill record not found", isError = true)
            }
        }
    }

    fun openSettlementReviewDialog(bill: BillEntity) {
        if (_session.value?.isSuperAdmin == true) {
            emitToast("Super Admin is read-only", isError = true)
            return
        }
        soundEngine.play(SoundCue.CLICK)
        _settlementDialogBill.value = bill
    }

    fun dismissSettlementReviewDialog() {
        _settlementDialogBill.value = null
    }

    fun saveBillSettlementAndReview(
        billId: String,
        discountText: String,
        paidText: String,
        rating: Int,
        reviewNote: String
    ) {
        if (_session.value?.isAdmin != true) {
            emitToast("Only Admin can update saved bill settlements", isError = true)
            return
        }
        viewModelScope.launch {
            val existing = repository.getBillById(billId) ?: bills.value.firstOrNull { it.id == billId }
            if (existing == null) {
                soundEngine.play(SoundCue.ERROR)
                emitToast("Bill not found", isError = true)
                return@launch
            }
            val disc = (discountText.toDoubleOrNull() ?: 0.0).coerceAtLeast(0.0)
            val grand = (existing.subtotal - disc).coerceAtLeast(0.0)
            val paid = (paidText.toDoubleOrNull() ?: 0.0).coerceAtLeast(0.0)
            val due = (grand - paid).coerceAtLeast(0.0)
            val ratingAlreadyLocked = existing.staffRating in 1..5
            val finalRating = if (ratingAlreadyLocked) existing.staffRating else rating.coerceIn(0, 5)
            val finalNote = if (ratingAlreadyLocked) existing.staffReviewNote else reviewNote
            val updated = repository.updateBillSettlementAndReview(
                billId = billId,
                discount = disc,
                grandTotal = grand,
                paidAmount = paid,
                dueAmount = due,
                staffRating = finalRating,
                staffReviewNote = finalNote
            )
            _settlementDialogBill.value = null
            if (updated != null && _billDraft.value.editingId == billId) {
                _billDraft.update {
                    it.copy(
                        discountText = formatInputNum(updated.discount),
                        paidAmountText = formatInputNum(updated.paidAmount),
                        staffRating = updated.staffRating,
                        staffReviewNote = updated.staffReviewNote
                    )
                }
            }
            soundEngine.play(SoundCue.SAVE)
            val statusMsg = if (due > 0.009) {
                "Updated · Due ₹${String.format(Locale.US, "%.2f", due)} highlighted in Bill History"
            } else {
                "Payment settled in full"
            }
            val ratingMsg = if (finalRating > 0) {
                " · ${finalRating}★ for @${existing.createdBy}${if (ratingAlreadyLocked) " (Rating locked)" else ""}"
            } else ""
            emitToast("$statusMsg$ratingMsg")
        }
    }

    fun notifyPdfSaved(success: Boolean) {
        if (success) {
            soundEngine.play(SoundCue.SAVE)
            emitToast("A4 PDF saved successfully")
        } else {
            soundEngine.play(SoundCue.ERROR)
            emitToast("Could not save PDF", isError = true)
        }
    }

    fun notifyPhotoSaved(success: Boolean) {
        if (success) {
            soundEngine.play(SoundCue.SAVE)
            emitToast("Document photo saved successfully")
        } else {
            soundEngine.play(SoundCue.ERROR)
            emitToast("Could not save document photo", isError = true)
        }
    }

    /* ===== Customers & Products ===== */
    fun saveCustomer(
        name: String,
        phone: String,
        address: String,
        locationUrl: String,
        id: Long = 0L
    ) {
        val current = _session.value
        if (current == null || !current.isAdmin) {
            soundEngine.play(SoundCue.ERROR)
            emitToast("Admin access only — Technicians cannot edit customers", isError = true)
            return
        }
        if (name.isBlank()) {
            soundEngine.play(SoundCue.ERROR)
            emitToast("Customer name required", isError = true)
            return
        }
        val combinedDetails = listOf(address.trim(), phone.trim()).filter { it.isNotBlank() }.joinToString("\n")
        viewModelScope.launch {
            repository.saveCustomer(
                name = name,
                details = combinedDetails,
                phone = phone,
                address = address,
                locationUrl = locationUrl,
                id = id
            )
            soundEngine.play(SoundCue.SAVE)
            emitToast(if (id > 0L) "Customer updated" else "Customer saved")
        }
    }

    fun updateCustomerSearch(query: String) {
        _customerSearchQuery.value = query
    }

    fun deleteCustomer(id: Long) {
        val current = _session.value
        if (current == null || !current.isAdmin) {
            soundEngine.play(SoundCue.ERROR)
            emitToast("Admin access only", isError = true)
            return
        }
        viewModelScope.launch {
            repository.deleteCustomer(id)
            soundEngine.play(SoundCue.DELETE)
            emitToast("Customer removed")
        }
    }

    fun addProduct(name: String, unit: String, rateText: String, folder: String = "General") {
        if (_session.value?.isAdmin != true) {
            soundEngine.play(SoundCue.ERROR)
            emitToast("Admin access only", isError = true)
            return
        }
        if (name.isBlank()) {
            soundEngine.play(SoundCue.ERROR)
            emitToast("Product name required", isError = true)
            return
        }
        viewModelScope.launch {
            repository.addProduct(name, unit, rateText.toDoubleOrNull() ?: 0.0, folder)
            soundEngine.play(SoundCue.SAVE)
            emitToast("Product added to folder '$folder'")
        }
    }

    fun updateProduct(id: Long, name: String, unit: String, rateText: String, folder: String = "General") {
        if (_session.value?.isAdmin != true) {
            soundEngine.play(SoundCue.ERROR)
            emitToast("Admin access only", isError = true)
            return
        }
        if (name.isBlank()) {
            soundEngine.play(SoundCue.ERROR)
            emitToast("Product name required", isError = true)
            return
        }
        viewModelScope.launch {
            repository.updateProduct(id, name, unit, rateText.toDoubleOrNull() ?: 0.0, folder)
            soundEngine.play(SoundCue.SAVE)
            emitToast("Product updated")
        }
    }

    fun deleteProduct(id: Long) {
        if (_session.value?.isAdmin != true) {
            soundEngine.play(SoundCue.ERROR)
            emitToast("Admin access only", isError = true)
            return
        }
        viewModelScope.launch {
            repository.deleteProduct(id)
            soundEngine.play(SoundCue.DELETE)
            emitToast("Product deleted")
        }
    }

    /* ===== Customer Help / Technician Service Desk ===== */
    fun saveHelpRequest(
        id: Long = 0L,
        customerName: String,
        phone: String,
        address: String,
        locationUrl: String,
        helpDescription: String,
        status: String = "OPEN",
        onSaved: ((CustomerHelpEntity) -> Unit)? = null
    ) {
        val current = _session.value
        if (current == null || !current.isAdmin) {
            soundEngine.play(SoundCue.ERROR)
            emitToast("Admin access only — Technicians cannot create or edit Help Requests", isError = true)
            return
        }
        if (helpDescription.isBlank()) {
            soundEngine.play(SoundCue.ERROR)
            emitToast("Please enter customer help issue description", isError = true)
            return
        }
        if (customerName.isBlank() && phone.isBlank()) {
            soundEngine.play(SoundCue.ERROR)
            emitToast("Please enter customer name or mobile number", isError = true)
            return
        }
        viewModelScope.launch {
            val saved = repository.saveHelpRequest(
                id = id,
                customerName = customerName,
                phone = phone,
                address = address,
                locationUrl = locationUrl,
                helpDescription = helpDescription,
                status = status
            )
            soundEngine.play(SoundCue.SAVE)
            emitToast(if (id > 0L) "Help request updated" else "Help request saved for Technicians")
            onSaved?.invoke(saved)
        }
    }

    fun acceptHelpRequest(requestId: Long) {
        val user = _session.value ?: return
        if (user.isSuperAdmin) {
            emitToast("Super Admin is view-only", isError = true)
            return
        }
        viewModelScope.launch {
            val res = repository.acceptHelpRequest(requestId, user)
            if (res.isSuccess) {
                soundEngine.play(SoundCue.SAVE)
                emitToast("Request accepted by @${user.username}. Solved/Not Solved unlocks in 15 min.")
            } else {
                soundEngine.play(SoundCue.ERROR)
                emitToast(res.exceptionOrNull()?.message ?: "Could not accept request", isError = true)
            }
        }
    }

    fun markHelpSolved(requestId: Long, nowMillis: Long = System.currentTimeMillis()) {
        val user = _session.value ?: return
        if (user.isSuperAdmin) return
        viewModelScope.launch {
            val res = repository.markHelpSolved(
                requestId = requestId,
                actorUsername = user.username,
                isAdmin = user.isAdmin,
                nowMillis = nowMillis,
                enforce15MinWait = !user.isAdmin
            )
            if (res.isSuccess) {
                soundEngine.play(SoundCue.SAVE)
                emitToast("Marked Solved! Tap Get Bill to generate support invoice.")
            } else {
                soundEngine.play(SoundCue.ERROR)
                emitToast(res.exceptionOrNull()?.message ?: "Could not mark Solved", isError = true)
            }
        }
    }

    fun markHelpNotSolved(requestId: Long, nowMillis: Long = System.currentTimeMillis()) {
        val user = _session.value ?: return
        if (user.isSuperAdmin) return
        viewModelScope.launch {
            val res = repository.markHelpNotSolved(
                requestId = requestId,
                actorUsername = user.username,
                isAdmin = user.isAdmin,
                nowMillis = nowMillis,
                enforce15MinWait = !user.isAdmin
            )
            if (res.isSuccess) {
                soundEngine.play(SoundCue.CLICK)
                emitToast("Status updated: Not Solved")
            } else {
                soundEngine.play(SoundCue.ERROR)
                emitToast(res.exceptionOrNull()?.message ?: "Could not update status", isError = true)
            }
        }
    }

    fun startBillFromHelpRequest(request: CustomerHelpEntity) {
        val user = _session.value ?: return
        if (user.isSuperAdmin) {
            emitToast("Super Admin is view-only", isError = true)
            return
        }
        val details = listOf(request.address.trim(), request.phone.trim())
            .filter { it.isNotBlank() }
            .joinToString("\n")
        soundEngine.play(SoundCue.CLICK)
        _billDraft.value = BillDraft(
            documentType = DocumentType.BILL,
            isHelpBill = true,
            helpRequestId = request.id,
            customerName = request.customerName,
            customerDetails = details,
            customerLocationUrl = request.locationUrl,
            invoiceNo = repository.computeNextInvoiceNo(bills.value),
            date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()),
            extraChargeType = ChargeType.CUSTOMER_CARE_AND_SUPPORT,
            extraChargeAmountText = "0"
        )
        _activeTab.value = MainTab.BILL
        emitToast("Support Help Bill ready for ${request.customerName.ifBlank { request.phone }}")
    }

    fun deleteHelpRequest(id: Long) {
        val current = _session.value
        if (current == null || !current.isAdmin) {
            soundEngine.play(SoundCue.ERROR)
            emitToast("Admin access only", isError = true)
            return
        }
        viewModelScope.launch {
            repository.deleteHelpRequest(id)
            soundEngine.play(SoundCue.DELETE)
            emitToast("Help request deleted")
        }
    }

    /* ===== Admin Panel & Settings ===== */
    fun saveUpiSettings(upiId: String, payeeName: String) {
        if (_session.value?.isAdmin != true) return
        val cleanId = upiId.trim()
        if (cleanId.isEmpty() || !cleanId.contains("@")) {
            soundEngine.play(SoundCue.ERROR)
            emitToast("Please enter a valid UPI ID (e.g. 7083805120@ybl)", isError = true)
            return
        }
        viewModelScope.launch {
            repository.saveUpiSettings(cleanId, payeeName)
            soundEngine.play(SoundCue.SAVE)
            emitToast("UPI ID saved & Payment QR updated!")
        }
    }

    fun clearUpiId() {
        if (_session.value?.isAdmin != true) return
        viewModelScope.launch {
            repository.clearUpiId()
            soundEngine.play(SoundCue.DELETE)
            emitToast("UPI ID cleared")
        }
    }

    fun addTechnician(
        name: String,
        phone: String,
        username: String,
        password: String,
        photoBitmap: Bitmap?,
        onSuccess: () -> Unit
    ) {
        if (_session.value?.isAdmin != true) return
        viewModelScope.launch {
            val photoB64 = if (photoBitmap != null) encodeScaledPng(photoBitmap, maxW = 360f) else ""
            val res = repository.addTechnician(name, phone, username, password, photoB64)
            if (res.isSuccess) {
                soundEngine.play(SoundCue.SAVE)
                emitToast("Technician @$username added")
                onSuccess()
            } else {
                soundEngine.play(SoundCue.ERROR)
                emitToast(res.exceptionOrNull()?.message ?: "Could not add technician", isError = true)
            }
        }
    }

    fun updateTechnician(
        oldUsername: String,
        newUsername: String,
        name: String,
        phone: String,
        newPassword: String,
        newPhotoBitmap: Bitmap? = null,
        onSuccess: () -> Unit
    ) {
        if (_session.value?.isAdmin != true) return
        viewModelScope.launch {
            val photoB64 = if (newPhotoBitmap != null) encodeScaledPng(newPhotoBitmap, maxW = 360f) else null
            val res = repository.updateTechnician(oldUsername, newUsername, name, phone, newPassword, photoB64)
            if (res.isSuccess) {
                soundEngine.play(SoundCue.SAVE)
                emitToast("Technician @$newUsername updated")
                onSuccess()
            } else {
                soundEngine.play(SoundCue.ERROR)
                emitToast(res.exceptionOrNull()?.message ?: "Could not update technician", isError = true)
            }
        }
    }

    fun removeTechnician(username: String) {
        if (_session.value?.isAdmin != true) return
        viewModelScope.launch {
            repository.removeTechnician(username)
            soundEngine.play(SoundCue.DELETE)
            emitToast("Technician @$username removed")
        }
    }

    fun addSuperAdmin(
        name: String,
        phone: String,
        username: String,
        password: String,
        onSuccess: () -> Unit
    ) {
        if (_session.value?.isAdmin != true) return
        viewModelScope.launch {
            val res = repository.addSuperAdmin(name, phone, username, password)
            if (res.isSuccess) {
                soundEngine.play(SoundCue.SAVE)
                emitToast("Super Admin @$username added (Analytics view-only)")
                onSuccess()
            } else {
                soundEngine.play(SoundCue.ERROR)
                emitToast(res.exceptionOrNull()?.message ?: "Could not add Super Admin", isError = true)
            }
        }
    }

    fun removeSuperAdmin(username: String) {
        if (_session.value?.isAdmin != true) return
        viewModelScope.launch {
            repository.removeSuperAdmin(username)
            soundEngine.play(SoundCue.DELETE)
            emitToast("Super Admin @$username removed")
        }
    }

    fun recordTechnicianCashSettlement(
        technicianUsername: String,
        amountText: String,
        note: String,
        onSuccess: () -> Unit = {}
    ) {
        val admin = _session.value ?: return
        if (!admin.isAdmin) return
        val amount = amountText.toDoubleOrNull() ?: 0.0
        viewModelScope.launch {
            val res = repository.recordTechnicianSettlement(
                technicianUsername = technicianUsername,
                amount = amount,
                note = note,
                recordedBy = admin.username
            )
            if (res.isSuccess) {
                soundEngine.play(SoundCue.SAVE)
                emitToast("Cleared ₹${String.format(Locale.US, "%.2f", amount)} from @$technicianUsername cash balance")
                onSuccess()
            } else {
                soundEngine.play(SoundCue.ERROR)
                emitToast(res.exceptionOrNull()?.message ?: "Settlement failed", isError = true)
            }
        }
    }

    fun saveVoiceAndNoteSettings(settings: VoiceAndNoteSettings) {
        if (_session.value?.isAdmin != true) return
        viewModelScope.launch {
            repository.saveVoiceAndNoteSettings(settings)
            soundEngine.applyVoiceModel(settings.voiceModelId)
            soundEngine.play(SoundCue.SAVE)
            emitToast("Header Note & Voice settings saved")
        }
    }

    fun saveCompanySettings(settings: CompanySettings) {
        if (_session.value?.isAdmin != true) return
        viewModelScope.launch {
            repository.saveCompanySettings(settings)
            soundEngine.play(SoundCue.SAVE)
            emitToast("Company settings saved")
        }
    }

    fun saveFallbackQrBitmap(bitmap: Bitmap) {
        if (_session.value?.isAdmin != true) return
        viewModelScope.launch {
            val out = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 95, out)
            val b64 = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
            repository.saveFallbackQrBase64(b64)
            soundEngine.play(SoundCue.SAVE)
            emitToast("Fallback QR image saved")
        }
    }

    fun saveAuthorisedSignatureBitmap(bitmap: Bitmap?) {
        if (_session.value?.isAdmin != true) return
        viewModelScope.launch {
            val b64 = if (bitmap != null) encodeScaledPng(bitmap, maxW = 700f) else ""
            repository.saveAuthorisedSignatureBase64(b64)
            soundEngine.play(if (b64.isNotEmpty()) SoundCue.SAVE else SoundCue.DELETE)
            emitToast(if (b64.isNotEmpty()) "Authorised Signatory image saved" else "Authorised Signatory cleared")
        }
    }

    fun saveCompanyLogoBitmap(bitmap: Bitmap?) {
        if (_session.value?.isAdmin != true) return
        viewModelScope.launch {
            val b64 = if (bitmap != null) encodeScaledPng(bitmap, maxW = 512f) else ""
            repository.saveCompanyLogoBase64(b64)
            soundEngine.play(if (b64.isNotEmpty()) SoundCue.SAVE else SoundCue.DELETE)
            emitToast(if (b64.isNotEmpty()) "Company Logo updated" else "Company Logo reset to default")
        }
    }

    private fun encodeScaledPng(bitmap: Bitmap, maxW: Float): String {
        val scale = minOf(
            maxW / bitmap.width.coerceAtLeast(1),
            maxW / bitmap.height.coerceAtLeast(1),
            1f
        )
        val finalBmp = if (scale < 1f) {
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * scale).toInt().coerceAtLeast(1),
                (bitmap.height * scale).toInt().coerceAtLeast(1),
                true
            )
        } else {
            bitmap
        }
        val out = ByteArrayOutputStream()
        finalBmp.compress(Bitmap.CompressFormat.PNG, 92, out)
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    suspend fun exportBackupJson(): String = repository.exportBackupJson()

    fun notifyBackupExported() {
        soundEngine.play(SoundCue.SAVE)
        emitToast("Full backup exported (Bills, Accounts, Technicians & Settings)")
    }

    fun importBackupJson(json: String, allowPreSetupRestore: Boolean = false) {
        val current = _session.value
        val canPreSetupRestore = allowPreSetupRestore && current == null && isAdminConfigured.value != true
        val canAdminRestore = current != null && current.isAdmin
        if (!canPreSetupRestore && !canAdminRestore) {
            soundEngine.play(SoundCue.ERROR)
            emitToast("Admin access only", isError = true)
            return
        }
        viewModelScope.launch {
            val res = repository.importBackupJson(json)
            if (res.isSuccess) {
                val summary = res.getOrNull()
                val latestBills = repository.bills.first()
                _billDraft.update { d ->
                    if (d.editingId == null) d.copy(invoiceNo = repository.computeNextInvoiceNo(latestBills))
                    else d
                }
                soundEngine.play(SoundCue.SAVE)
                val msg = if (summary != null) {
                    "Restored ${summary.billsCount} bills, ${summary.customersCount} customers, ${summary.productsCount} products, ${summary.accountsCount} accounts"
                } else {
                    "Backup imported successfully"
                }
                _authError.value = ""
                emitToast(msg)
            } else {
                soundEngine.play(SoundCue.ERROR)
                emitToast("Invalid backup file", isError = true)
            }
        }
    }

    private fun emitToast(msg: String, isError: Boolean = false) {
        _events.tryEmit(BillingEvent.Toast(msg, isError))
    }

    private fun formatInputNum(v: Double): String {
        return if (v == 0.0) "0"
        else if (v % 1.0 == 0.0) v.toInt().toString()
        else String.format(Locale.US, "%.2f", v)
    }

    companion object {
        fun buildCustomerFolders(
            docs: List<BillEntity>,
            customers: List<CustomerEntity>
        ): List<CustomerFolderSummary> {
            val grouped = docs.groupBy { bill ->
                val phoneDigits = extractDigitsFromDetails(bill.customerDetails)
                if (phoneDigits.length >= 5) "ph:${phoneDigits.takeLast(10)}"
                else "nm:${bill.customerName.trim().lowercase(Locale.ROOT).ifBlank { "walk_in" }}"
            }
            return grouped.map { (key, list) ->
                val sortedList = list.sortedByDescending { it.updatedAt }
                val sample = sortedList.first()
                val rawPhone = extractDigitsFromDetails(sample.customerDetails)
                val matchedCustomer = customers.firstOrNull { cust ->
                    (rawPhone.length >= 5 && cust.phone.filter { it.isDigit() }.endsWith(rawPhone.takeLast(5))) ||
                        cust.name.equals(sample.customerName.trim(), ignoreCase = true)
                }
                val billsOnly = sortedList.filter { DocumentType.fromString(it.documentType) == DocumentType.BILL }
                val quotesOnly = sortedList.filter { DocumentType.fromString(it.documentType) == DocumentType.QUOTATION }
                val dueCount = billsOnly.count { it.dueAmount > 0.009 }
                val paidCount = billsOnly.size - dueCount
                val totalDue = billsOnly.sumOf { it.dueAmount }
                val techs = sortedList.map { it.createdBy.trim() }.filter { it.isNotBlank() }.distinct()
                CustomerFolderSummary(
                    customerKey = key,
                    customerName = matchedCustomer?.name ?: sample.customerName.ifBlank { "Walk-in Customer" },
                    phone = matchedCustomer?.phone?.ifBlank { rawPhone } ?: rawPhone,
                    address = matchedCustomer?.address?.ifBlank { sample.customerDetails } ?: sample.customerDetails,
                    locationUrl = matchedCustomer?.locationUrl ?: "",
                    bills = billsOnly,
                    quotations = quotesOnly,
                    paidCount = paidCount,
                    dueCount = dueCount,
                    totalDue = totalDue,
                    visitingTechnicians = techs
                )
            }.sortedByDescending { it.totalDue > 0.0 }
        }

        private fun extractDigitsFromDetails(details: String): String {
            details.lines().forEach { line ->
                val d = line.filter { it.isDigit() }
                if (d.length in 7..15) return d
            }
            val fallback = details.filter { it.isDigit() }
            return if (fallback.length in 7..15) fallback else ""
        }
    }

    class Factory(
        private val repository: BillingRepository,
        private val soundEngine: SoundEngine
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return BillingViewModel(repository, soundEngine) as T
        }
    }
}
