package com.example.ui

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.data.AppLanguage
import com.example.data.AuditLogEntity
import com.example.data.AuthPolicy
import com.example.data.BillEntity
import com.example.data.BillingRepository
import com.example.data.BillingTimePolicy
import com.example.data.ChargeType
import com.example.data.CloudAuthUser
import com.example.data.CloudSyncRepository
import com.example.data.CloudTechCreationResult
import com.example.data.CloudWorkspaceInfo
import com.example.data.CloudWorkspaceNotification
import com.example.data.CompanySettings
import com.example.data.CustomerEntity
import com.example.data.CustomerHelpEntity
import com.example.data.DocumentType
import com.example.data.ExpenseEntity
import com.example.data.GoogleAuthHelper
import com.example.data.HelpCategory
import com.example.data.InvoiceItem
import com.example.data.InvoiceItemJson
import com.example.data.LoginSecurityState
import com.example.data.PaymentMode
import com.example.data.ProductEntity
import com.example.data.TechActivationState
import com.example.data.TechnicianSettlementEntity
import com.example.data.TrashItemEntity
import com.example.data.UpiSettings
import com.example.data.UserAccountEntity
import com.example.data.UserSession
import com.example.data.VoiceAndNoteSettings
import com.example.util.ApkShareHelper
import com.example.util.AppUpdateChecker
import com.example.util.GitHubReleaseInfo
import com.example.util.InvoicePrintPayload
import com.example.util.SoundCue
import com.example.util.SoundEngine
import com.example.util.WhatsAppHelper
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
    val secondaryChargeType: ChargeType = ChargeType.NONE,
    val secondaryChargeAmountText: String = "0",
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
    val secondaryChargeAmount: Double
        get() = if (secondaryChargeType == ChargeType.NONE || secondaryChargeType == extraChargeType) 0.0
        else (secondaryChargeAmountText.toDoubleOrNull() ?: 0.0).coerceAtLeast(0.0)
    val subtotal: Double get() = itemsSubtotal + extraChargeAmount + secondaryChargeAmount
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
    data class NotifyTechnicianBill(
        val invoiceNo: String,
        val technicianUsername: String,
        val customerName: String
    ) : BillingEvent
    data class NotifySyncAlert(
        val id: String,
        val title: String,
        val message: String
    ) : BillingEvent
}

data class PendingPasswordChangeState(
    val uid: String,
    val email: String,
    val name: String,
    val username: String,
    val phone: String,
    val photoBase64: String,
    val role: String,
    val workspaceId: String,
    val companyName: String
)

class BillingViewModel(
    private val repository: BillingRepository,
    private val soundEngine: SoundEngine,
    private val cloudSync: CloudSyncRepository? = repository.cloudSync
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

    private val _cloudUser = MutableStateFlow<CloudAuthUser?>(null)
    val cloudUser: StateFlow<CloudAuthUser?> = _cloudUser.asStateFlow()

    private val _cloudBusy = MutableStateFlow(false)
    val cloudBusy: StateFlow<Boolean> = _cloudBusy.asStateFlow()

    private val _cloudTechBusy = MutableStateFlow(false)
    val cloudTechBusy: StateFlow<Boolean> = _cloudTechBusy.asStateFlow()

    private val _pendingTechPasswordChange = MutableStateFlow<PendingPasswordChangeState?>(null)
    val pendingTechPasswordChange: StateFlow<PendingPasswordChangeState?> = _pendingTechPasswordChange.asStateFlow()

    private val _cloudStatusMessage = MutableStateFlow("")
    val cloudStatusMessage: StateFlow<String> = _cloudStatusMessage.asStateFlow()

    private val _notifications = MutableStateFlow<List<CloudWorkspaceNotification>>(emptyList())
    val notifications: StateFlow<List<CloudWorkspaceNotification>> = _notifications.asStateFlow()

    private val _lastReadNotifAt = MutableStateFlow(0L)

    private val _activationBusy = MutableStateFlow(false)
    val activationBusy: StateFlow<Boolean> = _activationBusy.asStateFlow()

    private val _activationError = MutableStateFlow("")
    val activationError: StateFlow<String> = _activationError.asStateFlow()

    private val _lastCreatedTechPasskey = MutableStateFlow<CloudTechCreationResult?>(null)
    val lastCreatedTechPasskey: StateFlow<CloudTechCreationResult?> = _lastCreatedTechPasskey.asStateFlow()

    private val seenRemoteBillIds = mutableSetOf<String>()
    private var initialRemoteBillsLoaded = false
    private val seenRemoteNotifIds = mutableSetOf<String>()
    private var initialRemoteNotifsLoaded = false
    private val seenDueInstallReminderIds = mutableSetOf<Long>()

    private val _events = MutableSharedFlow<BillingEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<BillingEvent> = _events.asSharedFlow()

    val isAdminConfigured: StateFlow<Boolean?> = repository.isAdminConfigured
        .map<Boolean, Boolean?> { it }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val hasAdminVerifyCode: StateFlow<Boolean> = repository.hasAdminVerificationCode
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val isVolatileCloudMode: StateFlow<Boolean> = repository.isVolatileCloudMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val cloudWorkspaceInfo: StateFlow<CloudWorkspaceInfo> = repository.cloudWorkspaceInfo
        .stateIn(viewModelScope, SharingStarted.Eagerly, CloudWorkspaceInfo())

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

    val expenses: StateFlow<List<ExpenseEntity>> = repository.expenses
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val trashItems: StateFlow<List<TrashItemEntity>> = repository.trashItems
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val auditLogs: StateFlow<List<AuditLogEntity>> = repository.auditLogs
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val isStaffOnlyApk: StateFlow<Boolean> = repository.isStaffOnlyApk
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val prefillInviteCode: StateFlow<String> = repository.prefillInviteCode
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

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

    val adminLoginSecurityState: StateFlow<LoginSecurityState> = repository.adminLoginSecurityState
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            LoginSecurityState(
                maxAttempts = LoginSecurityState.ADMIN_MAX_ATTEMPTS,
                lockDurationMs = LoginSecurityState.ADMIN_LOCK_DURATION_MS
            )
        )

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
                req.acceptedBy.isBlank() ||
                    req.acceptedBy.equals(user.username, ignoreCase = true) ||
                    req.assignedTechnician.equals(user.username, ignoreCase = true)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val techActivationState: StateFlow<TechActivationState> = repository.techActivationState
        .stateIn(viewModelScope, SharingStarted.Eagerly, TechActivationState())

    private val trackedProductNames: StateFlow<Set<String>> = repository.configMap
        .map { m ->
            m.entries
                .filter { it.key.startsWith(BillingRepository.KEY_STOCK_TRACKED_PREFIX) && it.value == "1" }
                .map { it.key.removePrefix(BillingRepository.KEY_STOCK_TRACKED_PREFIX) }
                .toSet()
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    val unreadNotificationCount: StateFlow<Int> = combine(
        _notifications,
        _lastReadNotifAt,
        session,
        _cloudUser
    ) { allNotifs, readAt, activeUser, cUser ->
        val activeUid = cUser?.uid.orEmpty()
        val roleForUser = when {
            activeUser == null -> ""
            activeUser.isAdmin -> "admin"
            activeUser.isSuperAdmin -> "super_admin"
            else -> "technician"
        }
        allNotifs.count { notif ->
            val roleMatches = roleForUser.isBlank() || notif.targetRole == "all" || notif.targetRole.equals(roleForUser, ignoreCase = true)
            val userMatches = notif.targetUsername.isBlank() ||
                (activeUser != null && notif.targetUsername.equals(activeUser.username, ignoreCase = true))
            val notSelf = activeUid.isBlank() || notif.createdByUid != activeUid
            roleMatches && userMatches && notSelf && notif.createdAt > readAt
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    val dueInstallationReminders: StateFlow<List<CustomerHelpEntity>> = combine(helpRequests, session) { all, user ->
        if (user == null || !user.isAdmin) {
            emptyList()
        } else {
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            all.filter { req ->
                req.quotationStatus.equals("APPROVED", ignoreCase = true) &&
                    req.installationDate.isNotBlank() &&
                    req.installationDate <= today &&
                    !req.installationReminderSent &&
                    req.status != "SOLVED" &&
                    req.status != "RESOLVED" &&
                    req.status != "REJECTED"
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
            repository.purgeExpiredTrash()
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
        viewModelScope.launch {
            dueInstallationReminders.collect { dueList ->
                val fresh = dueList.filter { it.id !in seenDueInstallReminderIds }
                if (fresh.isNotEmpty()) {
                    fresh.forEach { req ->
                        seenDueInstallReminderIds += req.id
                        soundEngine.play(SoundCue.NOTIFICATION)
                        emitToast("Installation Due Today: ${req.customerName.ifBlank { req.phone }} (${req.installationDate})")
                        pushCloudNotification(
                            category = "INSTALLATION",
                            title = "Installation Scheduled: ${req.customerName.ifBlank { req.phone }}",
                            message = "Due ${req.installationDate} — ${req.helpDescription.take(80)}",
                            targetRole = "admin"
                        )
                    }
                }
            }
        }
        checkForAppUpdates(manual = false)
        initCloudSessionOnLaunch()
    }

    private fun initCloudSessionOnLaunch() {
        val sync = cloudSync ?: return
        viewModelScope.launch {
            val savedWs = repository.cloudWorkspaceInfo.first()
            if (savedWs.workspaceId.isNotBlank() && savedWs.deviceRole != "admin") {
                repository.enableVolatileCloudMode(savedWs.deviceRole, savedWs.workspaceId)
            }
            val currentAuth = GoogleAuthHelper.currentUser(sync.contextForAuth())
            if (currentAuth != null) {
                _cloudUser.value = currentAuth
                val forcedTech = sync.checkCurrentTechnicianPasswordChangeRequired(savedWs.workspaceId).getOrNull()
                if (forcedTech != null) {
                    _pendingTechPasswordChange.value = PendingPasswordChangeState(
                        uid = forcedTech.uid,
                        email = forcedTech.email,
                        name = forcedTech.name,
                        username = forcedTech.username,
                        phone = forcedTech.phone,
                        photoBase64 = forcedTech.photoBase64,
                        role = forcedTech.role,
                        workspaceId = forcedTech.workspaceId,
                        companyName = forcedTech.companyName
                    )
                } else {
                    syncExistingCloudWorkspace()
                }
            } else {
                val silent = GoogleAuthHelper.trySilentGoogleSignIn(sync.contextForAuth()).getOrNull()
                if (silent != null) {
                    _cloudUser.value = silent
                    syncExistingCloudWorkspace()
                }
            }
        }
    }

    private fun CloudSyncRepository.contextForAuth(): Context {
        val field = this.javaClass.getDeclaredField("context").apply { isAccessible = true }
        return field.get(this) as Context
    }

    private suspend fun syncExistingCloudWorkspace() {
        val sync = cloudSync ?: return
        val savedWs = repository.cloudWorkspaceInfo.first()
        val discovered = sync.discoverWorkspaceForSignedInUser().getOrNull()
        val wsInfo = discovered ?: savedWs.takeIf { it.workspaceId.isNotBlank() } ?: return
        val snap = sync.fetchFullWorkspaceSnapshot(wsInfo.workspaceId, wsInfo.deviceRole).getOrNull()
        if (snap != null) {
            seenRemoteBillIds.clear()
            seenRemoteBillIds.addAll(snap.bills.map { it.id })
            initialRemoteBillsLoaded = true
            repository.applyCloudSnapshot(snap)
            startCloudRealtimeSync(snap.workspace)
            _cloudStatusMessage.value = "Cloud Connected (${snap.workspace.deviceRole.uppercase(Locale.US)})"
        }
    }

    private fun startCloudRealtimeSync(ws: CloudWorkspaceInfo) {
        val sync = cloudSync ?: return
        seenRemoteNotifIds.clear()
        initialRemoteNotifsLoaded = false
        sync.startRealtimeWorkspaceSync(
            workspaceId = ws.workspaceId,
            deviceRole = ws.deviceRole,
            onWorkspaceMetaChanged = { info, company, upi, voice ->
                viewModelScope.launch {
                    repository.saveWorkspaceMetadata(info)
                    repository.mergeCloudMetaIntoAdmin(company, upi, voice)
                }
            },
            onAccountsChanged = { list ->
                viewModelScope.launch { repository.mergeCloudAccountsIntoAdmin(list) }
            },
            onProductsChanged = { list ->
                viewModelScope.launch { repository.mergeCloudProductsIntoAdmin(list) }
            },
            onCustomersChanged = { list ->
                viewModelScope.launch { repository.mergeCloudCustomersIntoAdmin(list) }
            },
            onBillsChanged = { list ->
                viewModelScope.launch {
                    val activeAdmin = _session.value?.takeIf { it.isAdmin }
                    if (initialRemoteBillsLoaded && activeAdmin != null) {
                        val newlyArrived = list.filter { b ->
                            b.id !in seenRemoteBillIds &&
                                DocumentType.fromString(b.documentType) == DocumentType.BILL &&
                                !b.createdBy.equals(activeAdmin.username, ignoreCase = true)
                        }
                        newlyArrived.forEach { b ->
                            emitToast("New bill ${b.invoiceNo} created by @${b.createdBy} — Send Bill PDF to customer!")
                            _events.tryEmit(
                                BillingEvent.NotifyTechnicianBill(
                                    invoiceNo = b.invoiceNo,
                                    technicianUsername = b.createdBy,
                                    customerName = b.customerName
                                )
                            )
                        }
                    }
                    seenRemoteBillIds.addAll(list.map { it.id })
                    initialRemoteBillsLoaded = true
                    repository.mergeCloudBillsIntoAdmin(list)
                }
            },
            onHelpRequestsChanged = { list ->
                viewModelScope.launch { repository.mergeCloudHelpsIntoAdmin(list) }
            },
            onSettlementsChanged = { list ->
                viewModelScope.launch { repository.mergeCloudSettlementsIntoAdmin(list) }
            },
            onExpensesChanged = { list ->
                viewModelScope.launch { repository.mergeCloudExpensesIntoAdmin(list) }
            },
            onTrashChanged = { list ->
                viewModelScope.launch { repository.mergeCloudTrashIntoAdmin(list) }
            },
            onAuditLogsChanged = { list ->
                viewModelScope.launch { repository.mergeCloudAuditLogsIntoAdmin(list) }
            },
            onNotificationsChanged = { list ->
                handleRemoteNotifications(list)
            }
        )
    }

    private fun handleRemoteNotifications(list: List<CloudWorkspaceNotification>) {
        val sorted = list.sortedByDescending { it.createdAt }
        _notifications.value = sorted
        if (!initialRemoteNotifsLoaded) {
            seenRemoteNotifIds.addAll(sorted.map { it.id })
            initialRemoteNotifsLoaded = true
            return
        }
        val activeUser = _session.value
        val activeUid = _cloudUser.value?.uid.orEmpty()
        val roleForUser = when {
            activeUser == null -> cloudWorkspaceInfo.value.deviceRole.lowercase(Locale.US)
            activeUser.isAdmin -> "admin"
            activeUser.isSuperAdmin -> "super_admin"
            else -> "technician"
        }
        val newlyArrived = sorted.filter { it.id !in seenRemoteNotifIds }
        seenRemoteNotifIds.addAll(sorted.map { it.id })

        var playedSound = false
        newlyArrived.forEach { notif ->
            if (activeUid.isNotBlank() && notif.createdByUid == activeUid) return@forEach
            val roleMatches = notif.targetRole == "all" || notif.targetRole.equals(roleForUser, ignoreCase = true)
            val userMatches = notif.targetUsername.isBlank() ||
                (activeUser != null && notif.targetUsername.equals(activeUser.username, ignoreCase = true))
            if (roleMatches && userMatches && activeUser != null) {
                if (!playedSound) {
                    soundEngine.play(SoundCue.NOTIFICATION)
                    playedSound = true
                }
                emitToast("${notif.title}: ${notif.message}")
                _events.tryEmit(
                    BillingEvent.NotifySyncAlert(
                        id = notif.id,
                        title = notif.title,
                        message = notif.message
                    )
                )
            }
        }
    }

    fun markNotificationsRead() {
        _lastReadNotifAt.value = System.currentTimeMillis()
    }

    fun sendCustomNotification(
        category: String,
        title: String,
        message: String,
        targetRole: String = "all",
        onDone: () -> Unit = {}
    ) {
        val cleanTitle = title.trim()
        val cleanMsg = message.trim()
        if (cleanTitle.isEmpty()) {
            soundEngine.play(SoundCue.ERROR)
            emitToast("Enter a notification title", isError = true)
            return
        }
        if (cleanMsg.isEmpty()) {
            soundEngine.play(SoundCue.ERROR)
            emitToast("Enter a notification message", isError = true)
            return
        }
        val sync = cloudSync
        val wsId = cloudWorkspaceInfo.value.workspaceId
        if (sync == null || wsId.isBlank()) {
            soundEngine.play(SoundCue.ERROR)
            emitToast("Cloud Sync must be active to send team notifications.", isError = true)
            return
        }
        pushCloudNotification(
            category = category,
            title = cleanTitle,
            message = cleanMsg,
            targetRole = targetRole
        )
        soundEngine.play(SoundCue.SAVE)
        emitToast("Notification sent to team")
        onDone()
    }

    fun verifyTechnicianActivation(email: String, passkey: String) {
        val sync = cloudSync ?: run {
            _activationError.value = "Cloud service not initialized."
            soundEngine.play(SoundCue.ERROR)
            return
        }
        val cleanEmail = email.trim().lowercase(Locale.US)
        if (!AuthPolicy.isValidEmail(cleanEmail)) {
            _activationError.value = "Enter your authorized Technician email address."
            soundEngine.play(SoundCue.ERROR)
            return
        }
        if (passkey.trim().length < 6) {
            _activationError.value = "Enter the Activation Passkey provided by Admin."
            soundEngine.play(SoundCue.ERROR)
            return
        }
        viewModelScope.launch {
            _activationBusy.value = true
            _activationError.value = ""
            val wsHint = repository.getActiveWorkspaceId()
            val res = sync.verifyTechnicianActivationViaCloud(cleanEmail, passkey, wsHint)
            _activationBusy.value = false
            if (res.isSuccess) {
                val state = res.getOrNull()!!
                repository.saveTechActivationState(state)
                if (state.workspaceId.isNotBlank()) {
                    repository.enableVolatileCloudMode("technician", state.workspaceId)
                }
                _activationError.value = ""
                soundEngine.play(SoundCue.SAVE)
                emitToast("Activation verified for @${state.username}. Enter password & CAPTCHA to sign in.")
            } else {
                val msg = res.exceptionOrNull()?.message ?: "Activation Passkey verification failed."
                _activationError.value = msg
                soundEngine.play(SoundCue.ERROR)
                emitToast(msg, isError = true)
            }
        }
    }

    fun clearTechActivation() {
        viewModelScope.launch {
            repository.clearTechActivationState()
            _activationError.value = ""
            soundEngine.play(SoundCue.CLICK)
        }
    }

    fun dismissCreatedTechPasskey() {
        _lastCreatedTechPasskey.value = null
    }

    private fun pushCloudNotification(
        category: String,
        title: String,
        message: String,
        targetRole: String = "all",
        targetUsername: String = ""
    ) {
        val sync = cloudSync ?: return
        val wsId = cloudWorkspaceInfo.value.workspaceId
        if (wsId.isBlank()) return
        val actor = _session.value?.username.orEmpty().ifBlank { _cloudUser.value?.email.orEmpty() }
        viewModelScope.launch {
            sync.pushNotification(
                workspaceId = wsId,
                category = category,
                title = title,
                message = message,
                targetRole = targetRole,
                createdBy = actor,
                targetUsername = targetUsername
            )
        }
    }

    fun purgeExpiredTrashIfReady() {
        viewModelScope.launch {
            repository.purgeExpiredTrash()
        }
    }

    fun handleIncomingInviteUri(uri: Uri?) {
        if (uri == null) return
        if (!uri.scheme.equals("tsebilling", ignoreCase = true) ||
            !uri.host.equals("join", ignoreCase = true)
        ) {
            return
        }
        val role = uri.getQueryParameter("role").orEmpty()
        val code = uri.getQueryParameter("code").orEmpty().trim()
        viewModelScope.launch {
            repository.setStaffOnlyInviteMode(role = role, inviteCode = code)
            if (code.isNotBlank() && _cloudUser.value != null) {
                joinWorkspaceWithInviteCode(code)
            } else if (code.isNotBlank()) {
                emitToast("Invite code loaded. Sign in with Google to connect.")
            }
        }
    }

    fun signInWithGoogle(activityContext: Context, onReady: (() -> Unit)? = null) {
        viewModelScope.launch {
            _cloudBusy.value = true
            _authError.value = ""
            val res = GoogleAuthHelper.signInWithGoogle(activityContext)
            if (res.isFailure) {
                _cloudBusy.value = false
                val msg = res.exceptionOrNull()?.message ?: "Google Sign-In failed."
                _authError.value = msg
                emitToast(msg, isError = true)
                return@launch
            }
            val authUser = res.getOrNull()!!
            _cloudUser.value = authUser
            val sync = cloudSync
            if (sync != null) {
                val discoveredRes = sync.discoverWorkspaceForSignedInUser()
                if (discoveredRes.isFailure) {
                    val msg = discoveredRes.exceptionOrNull()?.message ?: "Could not check Cloud Workspace."
                    _authError.value = msg
                    emitToast(msg, isError = true)
                    _cloudBusy.value = false
                    return@launch
                }
                val discovered = discoveredRes.getOrNull()
                if (discovered != null) {
                    val snapRes = sync.fetchFullWorkspaceSnapshot(discovered.workspaceId, discovered.deviceRole)
                    if (snapRes.isSuccess) {
                        val snap = snapRes.getOrNull()!!
                        seenRemoteBillIds.clear()
                        seenRemoteBillIds.addAll(snap.bills.map { it.id })
                        initialRemoteBillsLoaded = true
                        repository.applyCloudSnapshot(snap)
                        startCloudRealtimeSync(snap.workspace)
                        _cloudStatusMessage.value = "Cloud Connected (${snap.workspace.deviceRole.uppercase(Locale.US)})"
                        emitToast("Cloud synced as ${authUser.email}")
                    } else {
                        val msg = snapRes.exceptionOrNull()?.message ?: "Could not load Cloud Workspace data."
                        _authError.value = msg
                        emitToast(msg, isError = true)
                    }
                } else if (repository.isAdminConfigured.first()) {
                    ensureAdminCloudWorkspaceInternal(pushLocal = true)
                } else {
                    emitToast("Google signed in (${authUser.email}). Enter Invite Code or Create Admin.")
                }
            }
            _cloudBusy.value = false
            onReady?.invoke()
        }
    }

    fun joinWorkspaceWithInviteCode(inviteCode: String) {
        val sync = cloudSync ?: run {
            emitToast("Cloud service not initialized", isError = true)
            return
        }
        val clean = inviteCode.trim()
        if (clean.length < 8) {
            _authError.value = "Enter valid Workspace Invite Code from Admin."
            soundEngine.play(SoundCue.ERROR)
            return
        }
        viewModelScope.launch {
            _cloudBusy.value = true
            _authError.value = ""
            val joined = sync.joinWorkspaceByInviteCode(clean)
            if (joined.isFailure) {
                _cloudBusy.value = false
                val msg = joined.exceptionOrNull()?.message ?: "Invalid Invite Code."
                _authError.value = msg
                soundEngine.play(SoundCue.ERROR)
                emitToast(msg, isError = true)
                return@launch
            }
            val info = joined.getOrNull()!!
            val snapRes = sync.fetchFullWorkspaceSnapshot(info.workspaceId, info.deviceRole)
            if (snapRes.isSuccess) {
                val snap = snapRes.getOrNull()!!
                seenRemoteBillIds.clear()
                seenRemoteBillIds.addAll(snap.bills.map { it.id })
                initialRemoteBillsLoaded = true
                repository.applyCloudSnapshot(snap)
                startCloudRealtimeSync(snap.workspace)
                soundEngine.play(SoundCue.SAVE)
                emitToast("Connected to ${snap.workspace.companyName}. Select your username & password to log in.")
            } else {
                val msg = snapRes.exceptionOrNull()?.message ?: "Could not load workspace."
                _authError.value = msg
                emitToast(msg, isError = true)
            }
            _cloudBusy.value = false
        }
    }

    fun ensureAdminCloudWorkspace() {
        viewModelScope.launch {
            _cloudBusy.value = true
            ensureAdminCloudWorkspaceInternal(pushLocal = true)
            _cloudBusy.value = false
        }
    }

    private suspend fun ensureAdminCloudWorkspaceInternal(pushLocal: Boolean) {
        val sync = cloudSync ?: return
        val existingId = repository.getActiveWorkspaceId()
        val res = sync.ensureAdminWorkspace(
            company = companySettings.value,
            upi = upiSettings.value,
            voice = voiceAndNoteSettings.value,
            existingWorkspaceId = existingId
        )
        if (res.isSuccess) {
            val info = res.getOrNull()!!
            repository.saveWorkspaceMetadata(info)
            if (pushLocal) {
                repository.pushLocalDatasetToWorkspace(info.workspaceId)
            }
            startCloudRealtimeSync(info)
            _cloudStatusMessage.value = "Cloud Workspace Active"
            emitToast("Cloud Sync Active! Technician & Super Admin codes ready.")
        } else {
            val msg = res.exceptionOrNull()?.message ?: "Could not initialize Cloud Workspace."
            _authError.value = msg
            emitToast(msg, isError = true)
        }
    }

    fun signOutGoogleAccount(context: Context) {
        viewModelScope.launch {
            cloudSync?.stopListeners()
            GoogleAuthHelper.signOutGoogle(context)
            _cloudUser.value = null
            _pendingTechPasswordChange.value = null
            _cloudStatusMessage.value = ""
            emitToast("Google account signed out")
        }
    }

    fun loginTechnicianWithEmail(email: String, password: String) {
        val sync = cloudSync ?: run {
            emitToast("Cloud service not initialized.", isError = true)
            return
        }
        val cleanEmail = email.trim().lowercase(Locale.US)
        if (!AuthPolicy.isValidEmail(cleanEmail)) {
            _authError.value = "Enter a valid Technician email address."
            soundEngine.play(SoundCue.ERROR)
            return
        }
        if (password.isBlank()) {
            _authError.value = "Enter Technician password."
            soundEngine.play(SoundCue.ERROR)
            return
        }
        viewModelScope.launch {
            val nowMillis = System.currentTimeMillis()
            val secState = repository.getLoginSecurityState()
            if (secState.isLocked(nowMillis)) {
                val remText = formatLockRemaining(secState.remainingLockMillis(nowMillis))
                val msg = "Too many wrong password attempts (5/5). Login locked for $remText."
                _authError.value = msg
                soundEngine.play(SoundCue.ERROR)
                emitToast(msg, isError = true)
                return@launch
            }
            _cloudBusy.value = true
            _authError.value = ""
            val res = sync.signInTechnicianWithEmail(cleanEmail, password)
            if (res.isFailure) {
                _cloudBusy.value = false
                val updated = repository.recordLoginFailure(nowMillis)
                val baseMsg = res.exceptionOrNull()?.message ?: "Technician login failed."
                val msg = if (updated.isLocked(nowMillis)) {
                    "Wrong password entered 5 times. Login locked for 2 hours (02:00:00)."
                } else {
                    "$baseMsg (${updated.failedAttempts}/${LoginSecurityState.MAX_ATTEMPTS} failed)"
                }
                _authError.value = msg
                soundEngine.play(SoundCue.ERROR)
                emitToast(msg, isError = true)
                return@launch
            }
            repository.resetLoginSecurityState()
            val info = res.getOrNull()!!
            _cloudUser.value = CloudAuthUser(
                uid = info.uid,
                email = info.email,
                displayName = info.name
            )
            val snap = sync.fetchFullWorkspaceSnapshot(info.workspaceId, info.role).getOrNull()
            if (snap != null) {
                seenRemoteBillIds.clear()
                seenRemoteBillIds.addAll(snap.bills.map { it.id })
                initialRemoteBillsLoaded = true
                repository.applyCloudSnapshot(snap)
                startCloudRealtimeSync(snap.workspace)
            }
            _cloudBusy.value = false

            if (info.mustChangePassword) {
                _session.value = null
                _pendingTechPasswordChange.value = PendingPasswordChangeState(
                    uid = info.uid,
                    email = info.email,
                    name = info.name,
                    username = info.username,
                    phone = info.phone,
                    photoBase64 = info.photoBase64,
                    role = info.role,
                    workspaceId = info.workspaceId,
                    companyName = info.companyName
                )
                soundEngine.play(SoundCue.CLICK)
                emitToast("First login: please set a new password to continue.")
            } else {
                _pendingTechPasswordChange.value = null
                _session.value = UserSession(
                    username = info.username,
                    displayName = info.name,
                    role = info.role,
                    phone = info.phone,
                    photoBase64 = info.photoBase64
                )
                _activeTab.value = MainTab.BILL
                soundEngine.play(SoundCue.SAVE)
                emitToast("Login successful — Welcome ${info.name} (Technician)")
                val cfg = voiceAndNoteSettings.value
                soundEngine.speakEnglish(cfg.postLoginVoice, cfg.voiceModelId)
            }
        }
    }

    fun completeFirstLoginPasswordChange(newPassword: String, confirmPassword: String) {
        val pending = _pendingTechPasswordChange.value ?: return
        val sync = cloudSync ?: return
        val missing = AuthPolicy.evaluatePassword(newPassword).firstMissingRequirement()
        if (missing != null) {
            _authError.value = missing
            soundEngine.play(SoundCue.ERROR)
            return
        }
        if (newPassword != confirmPassword) {
            _authError.value = "New passwords do not match."
            soundEngine.play(SoundCue.ERROR)
            return
        }
        viewModelScope.launch {
            _cloudBusy.value = true
            _authError.value = ""
            val res = sync.updateCurrentTechnicianPassword(pending.workspaceId, newPassword)
            _cloudBusy.value = false
            if (res.isFailure) {
                val msg = res.exceptionOrNull()?.message ?: "Could not update password."
                _authError.value = msg
                soundEngine.play(SoundCue.ERROR)
                emitToast(msg, isError = true)
                return@launch
            }
            _pendingTechPasswordChange.value = null
            _session.value = UserSession(
                username = pending.username,
                displayName = pending.name,
                role = pending.role,
                phone = pending.phone,
                photoBase64 = pending.photoBase64
            )
            _activeTab.value = MainTab.BILL
            soundEngine.play(SoundCue.SAVE)
            emitToast("Password changed! Welcome ${pending.name} (Technician)")
            val cfg = voiceAndNoteSettings.value
            soundEngine.speakEnglish(cfg.postLoginVoice, cfg.voiceModelId)
        }
    }

    fun cancelPendingPasswordChangeAndSignOut(context: Context) {
        viewModelScope.launch {
            cloudSync?.stopListeners()
            GoogleAuthHelper.signOutGoogle(context)
            repository.clearVolatileSessionCache()
            _pendingTechPasswordChange.value = null
            _cloudUser.value = null
            _session.value = null
            _authError.value = ""
            soundEngine.play(SoundCue.CLICK)
            emitToast("Signed out")
        }
    }

    suspend fun shouldPromptInitialPermissions(): Boolean = !repository.hasPromptedPermissions()

    fun markInitialPermissionsPrompted() {
        viewModelScope.launch {
            repository.markPermissionsPrompted()
        }
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
                tab == MainTab.PRODUCTS ||
                tab == MainTab.SETTINGS
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
                "Super Admin is view-only (Analytics, History & Backups)"
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

    fun setupAdmin(
        username: String,
        password: String,
        confirmPassword: String,
        adminVerifyCode: String = ""
    ) {
        if (isStaffOnlyApk.value) {
            _authError.value = "Admin account creation is disabled on shared Technician/Super Admin APK."
            soundEngine.play(SoundCue.ERROR)
            return
        }
        val cleanUser = AuthPolicy.CANONICAL_ADMIN_USERNAME
        val cleanCode = adminVerifyCode.trim()
        val missingReq = AuthPolicy.evaluatePassword(password).firstMissingRequirement()
        if (missingReq != null) {
            _authError.value = missingReq
            soundEngine.play(SoundCue.ERROR)
            return
        }
        if (password != confirmPassword) {
            _authError.value = "Passwords do not match."
            soundEngine.play(SoundCue.ERROR)
            return
        }
        if (cleanCode.isNotEmpty() && cleanCode.length < 4) {
            _authError.value = "2-Way Verification Code must be at least 4 characters."
            soundEngine.play(SoundCue.ERROR)
            return
        }
        viewModelScope.launch {
            repository.setupAdmin(cleanUser, password, cleanCode)
            if (cloudSync != null && _cloudUser.value != null) {
                ensureAdminCloudWorkspaceInternal(pushLocal = true)
            }
            _authError.value = ""
            soundEngine.play(SoundCue.SAVE)
            emitToast("Admin ($cleanUser) account created. Please log in.")
        }
    }

    fun updateAdminVerifyCode(
        currentAdminPassword: String,
        newVerifyCode: String,
        onSuccess: () -> Unit = {}
    ) {
        updateAdminCredentials(
            currentAdminPassword = currentAdminPassword,
            newPassword = "",
            confirmNewPassword = "",
            newVerifyCode = newVerifyCode,
            onSuccess = onSuccess
        )
    }

    fun updateAdminCredentials(
        currentAdminPassword: String,
        newPassword: String,
        confirmNewPassword: String,
        newVerifyCode: String,
        onSuccess: () -> Unit = {}
    ) {
        val admin = _session.value ?: return
        if (!admin.isAdmin) return
        if (currentAdminPassword.isBlank()) {
            emitToast("Enter your current Admin password", isError = true)
            soundEngine.play(SoundCue.ERROR)
            return
        }
        if (newPassword.isEmpty() && newVerifyCode.trim().isEmpty()) {
            emitToast("Enter a new password and/or new 2-Way Verification Code", isError = true)
            soundEngine.play(SoundCue.ERROR)
            return
        }
        if (newPassword.isNotEmpty()) {
            val missing = AuthPolicy.evaluatePassword(newPassword).firstMissingRequirement()
            if (missing != null) {
                emitToast(missing, isError = true)
                soundEngine.play(SoundCue.ERROR)
                return
            }
            if (newPassword != confirmNewPassword) {
                emitToast("New passwords do not match", isError = true)
                soundEngine.play(SoundCue.ERROR)
                return
            }
        }
        if (newVerifyCode.trim().isNotEmpty() && newVerifyCode.trim().length < 4) {
            emitToast("Verification code must be at least 4 characters", isError = true)
            soundEngine.play(SoundCue.ERROR)
            return
        }
        viewModelScope.launch {
            val res = repository.updateAdminCredentials(
                currentPassword = currentAdminPassword,
                newPassword = newPassword,
                newVerifyCode = newVerifyCode
            )
            if (res.isSuccess) {
                soundEngine.play(SoundCue.SAVE)
                val msg = when {
                    newPassword.isNotEmpty() && newVerifyCode.trim().isNotEmpty() ->
                        "Admin password & 2-Way Verification Code updated!"
                    newPassword.isNotEmpty() -> "Admin password updated!"
                    else -> "Admin 2-Way Verification Code updated!"
                }
                emitToast(msg)
                onSuccess()
            } else {
                soundEngine.play(SoundCue.ERROR)
                emitToast(res.exceptionOrNull()?.message ?: "Could not update Admin credentials", isError = true)
            }
        }
    }

    fun login(
        username: String,
        password: String,
        antiRobotPassed: Boolean = true,
        nowMillis: Long = System.currentTimeMillis(),
        adminVerifyCode: String = ""
    ) {
        val cleanUsername = username.trim()
        if (cleanUsername.isBlank()) {
            _authError.value = "Please select a username."
            soundEngine.play(SoundCue.ERROR)
            return
        }
        val matchedAcc = accounts.value.firstOrNull { it.username.equals(cleanUsername, ignoreCase = true) }
        val isAdminTarget = matchedAcc?.let { BillingRepository.normalizeRole(it.role) == "admin" }
            ?: (cleanUsername.equals(AuthPolicy.CANONICAL_ADMIN_USERNAME, ignoreCase = true) ||
                cleanUsername.equals("TotalSolutionEnterprises", ignoreCase = true))

        val cachedSecurity = if (isAdminTarget) adminLoginSecurityState.value else loginSecurityState.value
        if (cachedSecurity.isLocked(nowMillis)) {
            val remText = formatLockRemaining(cachedSecurity.remainingLockMillis(nowMillis))
            _authError.value = if (isAdminTarget) {
                "Admin password locked on this phone for 15 min (3/3 wrong attempts). Try again in $remText (other phones still have 3 tries)."
            } else {
                "Too many wrong password attempts (5/5). Login locked for $remText."
            }
            soundEngine.play(SoundCue.ERROR)
            return
        }
        if (!antiRobotPassed) {
            _authError.value = "Please complete the Anti-Robot CAPTCHA verification first."
            soundEngine.play(SoundCue.ERROR)
            return
        }
        if (password.isBlank()) {
            _authError.value = "Please enter password."
            soundEngine.play(SoundCue.ERROR)
            return
        }
        viewModelScope.launch {
            val latestSecurity = if (isAdminTarget) {
                repository.getAdminLoginSecurityState()
            } else {
                repository.getLoginSecurityState()
            }
            if (latestSecurity.isLocked(nowMillis)) {
                val remText = formatLockRemaining(latestSecurity.remainingLockMillis(nowMillis))
                _authError.value = if (isAdminTarget) {
                    "Admin password locked on this phone for 15 min (3/3 wrong attempts). Try again in $remText (other phones still have 3 tries)."
                } else {
                    "Too many wrong password attempts (5/5). Login locked for $remText."
                }
                soundEngine.play(SoundCue.ERROR)
                return@launch
            }
            val user = repository.authenticate(cleanUsername, password)
            if (user == null) {
                if (isAdminTarget) {
                    val updated = repository.recordAdminLoginFailure(nowMillis)
                    if (updated.isLocked(nowMillis)) {
                        _authError.value = "Wrong Admin password entered 3 times. Locked for 15 minutes (00:15:00) on this phone only."
                    } else {
                        val rem = updated.remainingAttempts(nowMillis)
                        _authError.value = "Incorrect Admin password (${updated.failedAttempts}/${LoginSecurityState.ADMIN_MAX_ATTEMPTS}). $rem try(s) left before 15-min phone lock."
                    }
                } else {
                    val updated = repository.recordLoginFailure(nowMillis)
                    if (updated.isLocked(nowMillis)) {
                        _authError.value = "Wrong password entered 5 times. Login locked for 2 hours (02:00:00)."
                    } else {
                        val rem = updated.remainingAttempts(nowMillis)
                        _authError.value = "Incorrect password (${updated.failedAttempts}/${LoginSecurityState.MAX_ATTEMPTS}). $rem attempt(s) remaining before 2-hour lock."
                    }
                }
                soundEngine.play(SoundCue.ERROR)
                return@launch
            }
            if (user.isAdmin && repository.isAdminVerificationCodeSet()) {
                if (adminVerifyCode.isBlank()) {
                    _authError.value = "Please enter Admin 2-Way Verification Code."
                    soundEngine.play(SoundCue.ERROR)
                    return@launch
                }
                if (!repository.verifyFinalCode(adminVerifyCode)) {
                    val updated = repository.recordAdminLoginFailure(nowMillis)
                    val rem = updated.remainingAttempts(nowMillis)
                    _authError.value = if (updated.isLocked(nowMillis)) {
                        "Wrong Admin verification attempts (3/3). Locked for 15 minutes (00:15:00) on this phone only."
                    } else {
                        "Incorrect Admin 2-Way Verification Code (${updated.failedAttempts}/${LoginSecurityState.ADMIN_MAX_ATTEMPTS}). $rem try(s) left."
                    }
                    soundEngine.play(SoundCue.ERROR)
                    return@launch
                }
            }
            if (isAdminTarget || user.isAdmin) {
                repository.resetAdminLoginSecurityState()
            } else {
                repository.resetLoginSecurityState()
            }
            _authError.value = ""
            _session.value = user
            _activeTab.value = if (user.isSuperAdmin) MainTab.ANALYTICS else MainTab.BILL
            soundEngine.play(SoundCue.SAVE)
            emitToast("Login successful — Welcome ${user.displayName} (${user.roleDisplayLabel})")
            val cfg = voiceAndNoteSettings.value
            soundEngine.speakEnglish(cfg.postLoginVoice, cfg.voiceModelId)
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
        viewModelScope.launch {
            repository.clearVolatileSessionCache()
        }
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
        val missing = AuthPolicy.evaluatePassword(newPassword).firstMissingRequirement()
        if (missing != null) {
            soundEngine.play(SoundCue.ERROR)
            onResult(false, missing)
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
                val adminName = res.getOrNull() ?: AuthPolicy.CANONICAL_ADMIN_USERNAME
                soundEngine.play(SoundCue.SAVE)
                val msg = "Admin (${AuthPolicy.formatHandle(adminName)}) password has been reset. Please log in."
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

    fun toggleDualExtraChargeType(type: ChargeType) {
        if (type == ChargeType.NONE) {
            soundEngine.play(SoundCue.CLICK)
            _billDraft.update {
                it.copy(
                    extraChargeType = ChargeType.NONE,
                    extraChargeAmountText = "0",
                    secondaryChargeType = ChargeType.NONE,
                    secondaryChargeAmountText = "0"
                )
            }
            return
        }
        soundEngine.play(SoundCue.CLICK)
        _billDraft.update { d ->
            when {
                d.extraChargeType == type -> d.copy(
                    extraChargeType = d.secondaryChargeType,
                    extraChargeAmountText = if (d.secondaryChargeType == ChargeType.NONE) "0" else d.secondaryChargeAmountText,
                    secondaryChargeType = ChargeType.NONE,
                    secondaryChargeAmountText = "0"
                )
                d.secondaryChargeType == type -> d.copy(
                    secondaryChargeType = ChargeType.NONE,
                    secondaryChargeAmountText = "0"
                )
                d.extraChargeType == ChargeType.NONE -> d.copy(
                    extraChargeType = type,
                    extraChargeAmountText = clampChargeValue(type, d.extraChargeAmountText)
                )
                d.secondaryChargeType == ChargeType.NONE -> d.copy(
                    secondaryChargeType = type,
                    secondaryChargeAmountText = clampChargeValue(type, d.secondaryChargeAmountText)
                )
                else -> {
                    // Replace whichever slot isn't Customer Care & Support (so user keeps Customer Care + swaps other)
                    if (d.extraChargeType == ChargeType.CUSTOMER_CARE_AND_SUPPORT) {
                        d.copy(secondaryChargeType = type, secondaryChargeAmountText = clampChargeValue(type, d.secondaryChargeAmountText))
                    } else if (d.secondaryChargeType == ChargeType.CUSTOMER_CARE_AND_SUPPORT) {
                        d.copy(extraChargeType = type, extraChargeAmountText = clampChargeValue(type, d.extraChargeAmountText))
                    } else {
                        d.copy(secondaryChargeType = type, secondaryChargeAmountText = clampChargeValue(type, d.secondaryChargeAmountText))
                    }
                }
            }
        }
    }

    fun updateExtraChargeAmount(value: String) {
        val currentType = _billDraft.value.extraChargeType
        val clamped = clampChargeValue(currentType, value)
        if (currentType == ChargeType.CUSTOMER_CARE_AND_SUPPORT && (value.toDoubleOrNull() ?: 0.0) > MAX_CUSTOMER_CARE_CHARGE) {
            emitToast("Customer Care & Support Charge maximum limit is ₹300", isError = true)
        }
        _billDraft.update { it.copy(extraChargeAmountText = clamped) }
    }

    fun updateSecondaryChargeAmount(value: String) {
        val currentType = _billDraft.value.secondaryChargeType
        val clamped = clampChargeValue(currentType, value)
        if (currentType == ChargeType.CUSTOMER_CARE_AND_SUPPORT && (value.toDoubleOrNull() ?: 0.0) > MAX_CUSTOMER_CARE_CHARGE) {
            emitToast("Customer Care & Support Charge maximum limit is ₹300", isError = true)
        }
        _billDraft.update { it.copy(secondaryChargeAmountText = clamped) }
    }

    private fun clampChargeValue(type: ChargeType, raw: String): String {
        if (type != ChargeType.CUSTOMER_CARE_AND_SUPPORT) return raw
        val parsed = raw.toDoubleOrNull() ?: return raw
        return if (parsed > MAX_CUSTOMER_CARE_CHARGE) "300" else raw
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

    private fun checkDraftStockErrorSync(draft: BillDraft): String? {
        if (draft.documentType != DocumentType.BILL) return null
        val requestedByName = linkedMapOf<String, Double>()
        draft.items.forEach { item ->
            val key = item.description.trim().lowercase(Locale.ROOT)
            if (key.isNotEmpty() && item.qty > 0.0) {
                requestedByName[key] = (requestedByName[key] ?: 0.0) + item.qty
            }
        }
        if (requestedByName.isEmpty()) return null

        val existingBill = draft.editingId?.let { editId -> bills.value.firstOrNull { it.id == editId } }
        val oldByName = linkedMapOf<String, Double>()
        if (existingBill != null && DocumentType.fromString(existingBill.documentType) == DocumentType.BILL) {
            InvoiceItemJson.fromJson(existingBill.itemsJson).forEach { item ->
                val key = item.description.trim().lowercase(Locale.ROOT)
                if (key.isNotEmpty() && item.qty > 0.0) {
                    oldByName[key] = (oldByName[key] ?: 0.0) + item.qty
                }
            }
        }

        val byLowerName = products.value.associateBy { it.name.trim().lowercase(Locale.ROOT) }
        val trackedSet = trackedProductNames.value
        for ((key, newQty) in requestedByName) {
            val product = byLowerName[key] ?: continue
            val isTracked = product.stockQty > 0.0 || product.minStockAlert > 0.0 || key in trackedSet
            if (!isTracked) continue
            val oldQty = oldByName[key] ?: 0.0
            val deltaNeeded = newQty - oldQty
            if (deltaNeeded > 1e-6 && product.stockQty + 1e-6 < deltaNeeded) {
                val effectiveAvail = product.stockQty + oldQty
                return "Insufficient stock for ${product.name}! Available: ${formatInputNum(effectiveAvail)} ${product.unit}"
            }
        }
        return null
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
            (draft.extraChargeType != ChargeType.NONE && draft.extraChargeAmount > 0.0) ||
            (draft.secondaryChargeType != ChargeType.NONE && draft.secondaryChargeAmount > 0.0)
        if (!hasItems) {
            soundEngine.play(SoundCue.ERROR)
            emitToast("Please add at least one product or service charge first", isError = true)
            return
        }
        checkDraftStockErrorSync(draft)?.let { stockErr ->
            _summaryError.value = stockErr
            soundEngine.play(SoundCue.ERROR)
            emitToast(stockErr, isError = true)
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
        checkDraftStockErrorSync(draft)?.let { stockErr ->
            _summaryError.value = stockErr
            soundEngine.play(SoundCue.ERROR)
            emitToast(stockErr, isError = true)
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
        checkDraftStockErrorSync(draft)?.let { stockErr ->
            _summaryError.value = stockErr
            soundEngine.play(SoundCue.ERROR)
            emitToast(stockErr, isError = true)
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
            secondaryChargeType = draft.secondaryChargeType.name,
            secondaryChargeAmount = draft.secondaryChargeAmount,
            staffRating = ratingVal,
            staffReviewNote = reviewVal,
            documentType = draft.documentType.name,
            isHelpBill = draft.isHelpBill && !isQuotation,
            helpRequestId = if (isQuotation) 0L else draft.helpRequestId,
            quotationNote = draft.quotationNote.trim()
        )

        viewModelScope.launch {
            val saveRes = repository.saveBill(entity, draft.customerLocationUrl)
            if (saveRes.isFailure) {
                val msg = saveRes.exceptionOrNull()?.message ?: "Failed to save bill due to stock or network conflict."
                _summaryError.value = msg
                soundEngine.play(SoundCue.ERROR)
                emitToast(msg, isError = true)
                return@launch
            }
            _summaryError.value = ""
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
            val createdByTech = currentUser.isTechnician
            val docWord = if (isQuotation) "Quotation" else "Bill"
            val custLabel = entity.customerName.ifBlank { "Customer" }
            pushCloudNotification(
                category = "BILLING",
                title = "$docWord ${entity.invoiceNo} Synced",
                message = "@${currentUser.username} saved $docWord for $custLabel (Total ₹${String.format(Locale.US, "%.0f", entity.grandTotal)})",
                targetRole = if (createdByTech) "admin" else "all"
            )
            if (!isQuotation) {
                val billedNames = draft.items.map { it.description.trim().lowercase(Locale.ROOT) }.toSet()
                products.value.filter { p ->
                    p.minStockAlert > 0.0 && p.stockQty <= p.minStockAlert &&
                        p.name.trim().lowercase(Locale.ROOT) in billedNames
                }.forEach { low ->
                    pushCloudNotification(
                        category = "STOCK",
                        title = "Low Stock: ${low.name}",
                        message = "Only ${formatInputNum(low.stockQty)} ${low.unit} left (Alert level: ${formatInputNum(low.minStockAlert)})",
                        targetRole = "all"
                    )
                }
            }
            emitToast(
                if (isQuotation) "Quotation ${entity.invoiceNo} generated & saved!"
                else if (createdByTech) "Bill ${entity.invoiceNo} saved & Admin notified to send PDF to customer!"
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
            secondaryChargeType = ChargeType.fromString(bill.secondaryChargeType),
            secondaryChargeAmountText = formatInputNum(bill.secondaryChargeAmount),
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
        if (_session.value?.isAdmin != true) {
            soundEngine.play(SoundCue.ERROR)
            emitToast("Only Admin can share Bills & PDFs", isError = true)
            return
        }
        soundEngine.play(SoundCue.CLICK)
        _events.tryEmit(BillingEvent.SharePdf(billToPayload(bill)))
    }

    fun saveBillPhotoDirect(bill: BillEntity) {
        soundEngine.play(SoundCue.CLICK)
        _events.tryEmit(BillingEvent.SavePhoto(billToPayload(bill)))
    }

    fun shareBillPhotoDirect(bill: BillEntity) {
        if (_session.value?.isAdmin != true) {
            soundEngine.play(SoundCue.ERROR)
            emitToast("Only Admin can share Bill Photos", isError = true)
            return
        }
        soundEngine.play(SoundCue.CLICK)
        _events.tryEmit(BillingEvent.SharePhoto(billToPayload(bill)))
    }

    fun shareBillPdfToCustomerWhatsApp(context: Context, bill: BillEntity) {
        if (_session.value?.isAdmin != true) {
            soundEngine.play(SoundCue.ERROR)
            emitToast("Only Admin can send Bill PDF to customer", isError = true)
            return
        }
        val phone = customers.value.firstOrNull {
            it.name.equals(bill.customerName.trim(), ignoreCase = true)
        }?.phone.orEmpty()
        soundEngine.play(SoundCue.CLICK)
        val ok = WhatsAppHelper.sharePdfDirectToCustomerWhatsApp(context, billToPayload(bill), phone)
        if (!ok) {
            emitToast("Unable to open WhatsApp to send PDF", isError = true)
        }
    }

    fun shareDueQrToCustomerWhatsApp(context: Context, bill: BillEntity) {
        if (_session.value?.isAdmin != true) {
            soundEngine.play(SoundCue.ERROR)
            emitToast("Only Admin can send Due Balance QR", isError = true)
            return
        }
        if (bill.dueAmount <= 0.009) {
            emitToast("This bill has no due balance.")
            return
        }
        soundEngine.play(SoundCue.CLICK)
        val ok = WhatsAppHelper.shareDueQrToCustomerWhatsApp(context, companySettings.value, upiSettings.value, bill)
        if (!ok) {
            emitToast("Unable to share Due Payment QR", isError = true)
        }
    }

    fun markDuePaidInFull(bill: BillEntity) {
        if (_session.value?.isAdmin != true) {
            soundEngine.play(SoundCue.ERROR)
            emitToast("Only Admin can mark due payments received", isError = true)
            return
        }
        viewModelScope.launch {
            val updated = repository.updateBillSettlementAndReview(
                billId = bill.id,
                discount = bill.discount,
                grandTotal = bill.grandTotal,
                paidAmount = bill.grandTotal,
                dueAmount = 0.0,
                staffRating = bill.staffRating,
                staffReviewNote = bill.staffReviewNote
            )
            if (updated != null) {
                soundEngine.play(SoundCue.SAVE)
                emitToast("Due payment received for ${bill.invoiceNo}! Due balance is now ₹0.00")
                pushCloudNotification(
                    category = "PAYMENT",
                    title = "Due Cleared: ${bill.invoiceNo}",
                    message = "Full payment received for ${bill.customerName.ifBlank { "Customer" }}",
                    targetRole = "all"
                )
            } else {
                soundEngine.play(SoundCue.ERROR)
                emitToast("Bill record not found", isError = true)
            }
        }
    }

    fun shareApkViaWhatsApp(context: Context, targetRole: String) {
        if (_session.value?.isAdmin != true) {
            emitToast("Only Admin can share the app APK", isError = true)
            return
        }
        val pendingUpdate = _availableUpdate.value
        if (pendingUpdate != null) {
            soundEngine.play(SoundCue.ERROR)
            emitToast("Update to v${pendingUpdate.versionName} first before sharing APK!", isError = true)
            return
        }
        viewModelScope.launch {
            val wsInfo = cloudWorkspaceInfo.value
            val shareCode = if (targetRole == ApkShareHelper.ROLE_SUPER_ADMIN) {
                wsInfo.superShareCode
            } else {
                wsInfo.techShareCode
            }
            val res = ApkShareHelper.shareLatestApkOnWhatsApp(
                context = context,
                targetRole = targetRole,
                shareCode = shareCode,
                companyName = companySettings.value.companyName
            )
            if (res.isSuccess) {
                soundEngine.play(SoundCue.SAVE)
                val label = if (targetRole == ApkShareHelper.ROLE_SUPER_ADMIN) "Super Admin" else "Technician"
                emitToast("Sharing latest APK for $label via WhatsApp")
            } else {
                soundEngine.play(SoundCue.ERROR)
                emitToast(res.exceptionOrNull()?.message ?: "Could not prepare APK share", isError = true)
            }
        }
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
            secondaryChargeType = ChargeType.fromString(bill.secondaryChargeType),
            secondaryChargeAmount = bill.secondaryChargeAmount,
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
            val deleted = repository.deleteBill(id, currentUser.username, currentUser.role)
            if (!deleted) {
                soundEngine.play(SoundCue.ERROR)
                emitToast("Star rating is locked — rated bills cannot be deleted", isError = true)
                return@launch
            }
            if (_billDraft.value.editingId == id) {
                _billDraft.update { it.copy(editingId = null) }
            }
            soundEngine.play(SoundCue.DELETE)
            emitToast("Moved to Trash (recoverable for 24h)")
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
        if (_session.value?.isAdmin != true) {
            soundEngine.play(SoundCue.ERROR)
            emitToast("Only Admin can share Bills & PDFs", isError = true)
            return
        }
        soundEngine.play(SoundCue.CLICK)
        _events.tryEmit(BillingEvent.SharePdf(payload))
    }

    fun triggerSavePhoto(payload: InvoicePrintPayload) {
        soundEngine.play(SoundCue.CLICK)
        _events.tryEmit(BillingEvent.SavePhoto(payload))
    }

    fun triggerSharePhoto(payload: InvoicePrintPayload) {
        if (_session.value?.isAdmin != true) {
            soundEngine.play(SoundCue.ERROR)
            emitToast("Only Admin can share Bill Photos", isError = true)
            return
        }
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
            pushCloudNotification(
                category = "PAYMENT",
                title = "Payment Update: ${existing.invoiceNo}",
                message = "$statusMsg (${existing.customerName.ifBlank { "Customer" }})",
                targetRole = "all"
            )
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
            pushCloudNotification(
                category = "CUSTOMER",
                title = if (id > 0L) "Customer Updated" else "New Customer Added",
                message = "${name.trim()}${if (phone.isNotBlank()) " · ${phone.trim()}" else ""}",
                targetRole = "all"
            )
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
            repository.deleteCustomer(id, current.username, current.role)
            soundEngine.play(SoundCue.DELETE)
            emitToast("Customer moved to Trash (recoverable for 24h)")
        }
    }

    fun addProduct(
        name: String,
        unit: String,
        rateText: String,
        folder: String = "General",
        stockQtyText: String = "",
        minStockAlertText: String = ""
    ) {
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
        val stockQty = stockQtyText.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
        val minAlert = minStockAlertText.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
        viewModelScope.launch {
            repository.addProduct(name, unit, rateText.toDoubleOrNull() ?: 0.0, folder, stockQty, minAlert)
            soundEngine.play(SoundCue.SAVE)
            emitToast("Product added to folder '$folder'")
            pushCloudNotification(
                category = "PRODUCT",
                title = "New Product: ${name.trim()}",
                message = "₹${rateText.ifBlank { "0" }}/$unit · Stock ${formatInputNum(stockQty)}",
                targetRole = "all"
            )
        }
    }

    fun updateProduct(
        id: Long,
        name: String,
        unit: String,
        rateText: String,
        folder: String = "General",
        stockQtyText: String = "",
        minStockAlertText: String = ""
    ) {
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
        val stockQty = stockQtyText.takeIf { it.isNotBlank() }?.toDoubleOrNull()?.coerceAtLeast(0.0)
        val minAlert = minStockAlertText.takeIf { it.isNotBlank() }?.toDoubleOrNull()?.coerceAtLeast(0.0)
        viewModelScope.launch {
            repository.updateProduct(id, name, unit, rateText.toDoubleOrNull() ?: 0.0, folder, stockQty, minAlert)
            soundEngine.play(SoundCue.SAVE)
            emitToast("Product updated")
            pushCloudNotification(
                category = if (stockQty != null) "STOCK" else "PRODUCT",
                title = "Product Synced: ${name.trim()}",
                message = "Rate ₹${rateText.ifBlank { "0" }}/$unit${stockQty?.let { " · Stock ${formatInputNum(it)}" } ?: ""}",
                targetRole = "all"
            )
        }
    }

    fun deleteProduct(id: Long) {
        val current = _session.value ?: return
        if (!current.isAdmin) {
            soundEngine.play(SoundCue.ERROR)
            emitToast("Admin access only", isError = true)
            return
        }
        viewModelScope.launch {
            repository.deleteProduct(id, current.username, current.role)
            soundEngine.play(SoundCue.DELETE)
            emitToast("Product moved to Trash (recoverable for 24h)")
        }
    }

    /* ===== Business Expenses & 24h Trash Recovery ===== */
    fun saveExpense(
        title: String,
        category: String,
        amountText: String,
        note: String,
        date: String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()),
        id: String = "",
        onSuccess: () -> Unit = {}
    ) {
        val current = _session.value
        if (current == null || !current.isAdmin) {
            soundEngine.play(SoundCue.ERROR)
            emitToast("Admin access only", isError = true)
            return
        }
        val amt = amountText.toDoubleOrNull() ?: 0.0
        val expense = ExpenseEntity(
            id = id,
            title = title,
            category = category,
            amount = amt,
            date = date,
            note = note,
            recordedBy = current.username
        )
        viewModelScope.launch {
            val res = repository.saveExpense(expense, current.username, current.role)
            if (res.isSuccess) {
                soundEngine.play(SoundCue.SAVE)
                emitToast("Expense saved")
                onSuccess()
            } else {
                soundEngine.play(SoundCue.ERROR)
                emitToast(res.exceptionOrNull()?.message ?: "Could not save expense", isError = true)
            }
        }
    }

    fun deleteExpense(id: String) {
        val current = _session.value
        if (current == null || !current.isAdmin) {
            soundEngine.play(SoundCue.ERROR)
            emitToast("Admin access only", isError = true)
            return
        }
        viewModelScope.launch {
            repository.deleteExpense(id, current.username, current.role)
            soundEngine.play(SoundCue.DELETE)
            emitToast("Expense moved to Trash (recoverable for 24h)")
        }
    }

    fun restoreTrashItem(trashId: String) {
        val current = _session.value
        if (current == null || !current.isAdmin) {
            soundEngine.play(SoundCue.ERROR)
            emitToast("Admin access only", isError = true)
            return
        }
        viewModelScope.launch {
            val res = repository.restoreTrashItem(trashId, current.username, current.role)
            if (res.isSuccess) {
                soundEngine.play(SoundCue.SAVE)
                emitToast(res.getOrNull() ?: "Item restored")
            } else {
                soundEngine.play(SoundCue.ERROR)
                emitToast(res.exceptionOrNull()?.message ?: "Could not restore item", isError = true)
            }
        }
    }

    /* ===== Customer Help / Technician Service Desk / Quotations & Installations ===== */
    fun saveHelpRequest(
        id: Long = 0L,
        customerName: String,
        phone: String,
        address: String,
        locationUrl: String,
        helpDescription: String,
        status: String = "OPEN",
        category: String = HelpCategory.REGULAR_SERVICE.code,
        onSaved: ((CustomerHelpEntity) -> Unit)? = null
    ) {
        val current = _session.value ?: return
        val cat = HelpCategory.fromString(category)
        if (!current.isAdmin) {
            if (!(current.isTechnician && cat == HelpCategory.QUOTATION && id == 0L)) {
                soundEngine.play(SoundCue.ERROR)
                emitToast("Admin access only — Technicians can submit Quotation requests only", isError = true)
                return
            }
        }
        if (helpDescription.isBlank()) {
            soundEngine.play(SoundCue.ERROR)
            emitToast("Please enter customer help / requirement description", isError = true)
            return
        }
        if (customerName.isBlank() && phone.isBlank()) {
            soundEngine.play(SoundCue.ERROR)
            emitToast("Please enter customer name or mobile number", isError = true)
            return
        }
        val quotStatus = if (cat == HelpCategory.QUOTATION) "PENDING" else ""
        viewModelScope.launch {
            val saved = repository.saveHelpRequest(
                id = id,
                customerName = customerName,
                phone = phone,
                address = address,
                locationUrl = locationUrl,
                helpDescription = helpDescription,
                status = status,
                category = cat.code,
                quotationStatus = quotStatus
            )
            soundEngine.play(SoundCue.SAVE)
            val toastMsg = when {
                cat == HelpCategory.QUOTATION -> "Quotation request submitted for Admin approval"
                id > 0L -> "Service request updated"
                else -> "${cat.label} request saved for Technicians"
            }
            emitToast(toastMsg)
            pushCloudNotification(
                category = if (cat == HelpCategory.QUOTATION) "QUOTATION" else "SERVICE_REQUEST",
                title = if (cat == HelpCategory.QUOTATION) "New Quotation Request" else if (id > 0L) "Service Request Updated" else "New ${cat.label}",
                message = "${customerName.ifBlank { phone }} — ${helpDescription.trim().take(80)}",
                targetRole = if (cat == HelpCategory.QUOTATION && !current.isAdmin) "admin" else "technician"
            )
            onSaved?.invoke(saved)
        }
    }

    fun approveQuotationWithInstallationDate(
        requestId: Long,
        installationDate: String,
        assignedTechnician: String = "",
        onDone: () -> Unit = {}
    ) {
        val current = _session.value
        if (current == null || !current.isAdmin) {
            soundEngine.play(SoundCue.ERROR)
            emitToast("Admin access only", isError = true)
            return
        }
        viewModelScope.launch {
            val res = repository.approveQuotationWithInstallationDate(requestId, installationDate, assignedTechnician)
            if (res.isSuccess) {
                val updated = res.getOrNull()!!
                soundEngine.play(SoundCue.SAVE)
                emitToast("Quotation Approved! Installation scheduled for ${updated.installationDate}")
                pushCloudNotification(
                    category = "INSTALLATION",
                    title = "Installation Scheduled (${updated.installationDate})",
                    message = "${updated.customerName.ifBlank { updated.phone }} approved — ready for technician acceptance",
                    targetRole = "technician",
                    targetUsername = updated.assignedTechnician
                )
                onDone()
            } else {
                soundEngine.play(SoundCue.ERROR)
                emitToast(res.exceptionOrNull()?.message ?: "Could not approve quotation", isError = true)
            }
        }
    }

    fun rejectQuotation(requestId: Long, reason: String = "", onDone: () -> Unit = {}) {
        val current = _session.value
        if (current == null || !current.isAdmin) {
            soundEngine.play(SoundCue.ERROR)
            emitToast("Admin access only", isError = true)
            return
        }
        viewModelScope.launch {
            val res = repository.rejectQuotation(requestId, reason)
            if (res.isSuccess) {
                val updated = res.getOrNull()!!
                soundEngine.play(SoundCue.DELETE)
                emitToast("Quotation rejected")
                pushCloudNotification(
                    category = "QUOTATION",
                    title = "Quotation Rejected",
                    message = "${updated.customerName.ifBlank { "#$requestId" }}${if (reason.isNotBlank()) " — ${reason.trim()}" else ""}",
                    targetRole = "technician"
                )
                onDone()
            } else {
                soundEngine.play(SoundCue.ERROR)
                emitToast(res.exceptionOrNull()?.message ?: "Could not reject quotation", isError = true)
            }
        }
    }

    fun rejectHelpRequest(requestId: Long, reason: String = "", onDone: () -> Unit = {}) {
        val user = _session.value ?: return
        if (user.isSuperAdmin) {
            emitToast("Super Admin is view-only", isError = true)
            return
        }
        viewModelScope.launch {
            val res = repository.rejectHelpRequest(requestId, user.username, user.isAdmin, reason)
            if (res.isSuccess) {
                val req = res.getOrNull()
                soundEngine.play(SoundCue.DELETE)
                emitToast("Request rejected")
                pushCloudNotification(
                    category = if (req?.category == HelpCategory.INSTALLATION.code) "INSTALLATION" else "SERVICE_REQUEST",
                    title = "Job Rejected by @${user.username}",
                    message = "${req?.customerName.orEmpty().ifBlank { "#$requestId" }}${if (reason.isNotBlank()) ": ${reason.trim()}" else ""}",
                    targetRole = "admin"
                )
                onDone()
            } else {
                soundEngine.play(SoundCue.ERROR)
                emitToast(res.exceptionOrNull()?.message ?: "Could not reject request", isError = true)
            }
        }
    }

    fun dismissInstallationReminder(requestId: Long) {
        viewModelScope.launch {
            repository.markInstallationReminderSent(requestId)
            soundEngine.play(SoundCue.CLICK)
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
                val req = res.getOrNull()
                soundEngine.play(SoundCue.SAVE)
                emitToast("Request accepted by @${user.username}. Solved/Not Solved unlocks in 15 min.")
                pushCloudNotification(
                    category = if (req?.category == HelpCategory.INSTALLATION.code) "INSTALLATION" else "SERVICE_REQUEST",
                    title = "Service Accepted by @${user.username}",
                    message = "${req?.customerName.orEmpty().ifBlank { req?.phone.orEmpty() }} is now in progress",
                    targetRole = "admin"
                )
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
                val req = res.getOrNull()
                soundEngine.play(SoundCue.SAVE)
                emitToast("Marked Solved! Tap Get Bill to generate support invoice.")
                pushCloudNotification(
                    category = "SERVICE_REQUEST",
                    title = "Service Request Solved",
                    message = "${req?.customerName.orEmpty().ifBlank { "#$requestId" }} marked SOLVED by @${user.username}",
                    targetRole = "admin"
                )
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
                val req = res.getOrNull()
                soundEngine.play(SoundCue.CLICK)
                emitToast("Status updated: Not Solved")
                pushCloudNotification(
                    category = "SERVICE_REQUEST",
                    title = "Service Not Solved",
                    message = "${req?.customerName.orEmpty().ifBlank { "#$requestId" }} marked NOT SOLVED by @${user.username}",
                    targetRole = "admin"
                )
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
        val defaultCharge = if (request.category == HelpCategory.INSTALLATION.code) {
            ChargeType.INSTALLATION
        } else {
            ChargeType.CUSTOMER_CARE_AND_SUPPORT
        }
        _billDraft.value = BillDraft(
            documentType = DocumentType.BILL,
            isHelpBill = true,
            helpRequestId = request.id,
            customerName = request.customerName,
            customerDetails = details,
            customerLocationUrl = request.locationUrl,
            invoiceNo = repository.computeNextInvoiceNo(bills.value),
            date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()),
            extraChargeType = defaultCharge,
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
            repository.deleteHelpRequest(id, current.username, current.role)
            soundEngine.play(SoundCue.DELETE)
            emitToast("Help request moved to Trash (recoverable for 24h)")
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

    /**
     * Provisions a real Firebase Authentication Email/Password account for a Technician via Admin SDK
     * Cloud Function (`createTechnicianAccount`), keeping the Admin's Google Sign-In session intact.
     */
    fun createCloudTechnicianAccount(
        name: String,
        phone: String,
        email: String,
        username: String,
        initialPassword: String,
        photoBitmap: Bitmap?,
        onSuccess: () -> Unit
    ) {
        val currentSession = _session.value
        if (currentSession == null || !currentSession.isAdmin) {
            soundEngine.play(SoundCue.ERROR)
            emitToast("Admin access only", isError = true)
            return
        }
        val cleanName = name.trim()
        val cleanEmail = email.trim().lowercase(Locale.US)
        if (cleanName.length < 2) {
            soundEngine.play(SoundCue.ERROR)
            emitToast("Enter technician full name (min 2 chars)", isError = true)
            return
        }
        if (!AuthPolicy.isValidEmail(cleanEmail)) {
            soundEngine.play(SoundCue.ERROR)
            emitToast("Enter a valid Technician email address", isError = true)
            return
        }
        if (cleanEmail.equals(AuthPolicy.PRIMARY_ADMIN_EMAIL, ignoreCase = true)) {
            soundEngine.play(SoundCue.ERROR)
            emitToast("Admin email cannot be used as a technician account", isError = true)
            return
        }
        AuthPolicy.evaluatePassword(initialPassword).firstMissingRequirement()?.let { msg ->
            soundEngine.play(SoundCue.ERROR)
            emitToast(msg, isError = true)
            return
        }
        val sync = cloudSync ?: run {
            soundEngine.play(SoundCue.ERROR)
            emitToast("Cloud service not initialized", isError = true)
            return
        }
        viewModelScope.launch {
            _cloudTechBusy.value = true
            try {
                val activeGoogleUser = _cloudUser.value ?: GoogleAuthHelper.currentUser(sync.contextForAuth())
                if (activeGoogleUser == null) {
                    soundEngine.play(SoundCue.ERROR)
                    emitToast("Please sign in with Google (${AuthPolicy.PRIMARY_ADMIN_EMAIL}) first in Cloud Sync above.", isError = true)
                    return@launch
                }
                _cloudUser.value = activeGoogleUser

                var wsId = repository.getActiveWorkspaceId()
                if (wsId.isBlank()) {
                    ensureAdminCloudWorkspaceInternal(pushLocal = true)
                    wsId = repository.getActiveWorkspaceId()
                }
                if (wsId.isBlank()) {
                    soundEngine.play(SoundCue.ERROR)
                    emitToast("Activate Cloud Workspace before creating Firebase Technician accounts.", isError = true)
                    return@launch
                }

                val photoB64 = if (photoBitmap != null) encodeScaledPng(photoBitmap, maxW = 360f) else ""
                val res = sync.createTechnicianAccountViaCloud(
                    workspaceId = wsId,
                    name = cleanName,
                    email = cleanEmail,
                    initialPassword = initialPassword,
                    phone = phone.trim(),
                    username = username.trim(),
                    photoBase64 = photoB64
                )
                if (res.isSuccess) {
                    val created = res.getOrNull()!!
                    repository.mergeCloudAccountsIntoAdmin(
                        listOf(
                            UserAccountEntity(
                                username = created.username,
                                displayName = created.name,
                                passwordHash = AuthPolicy.FIREBASE_EMAIL_AUTH_MARKER,
                                role = "technician",
                                phone = phone.trim(),
                                photoBase64 = photoB64
                            )
                        )
                    )
                    if (created.activationPasskey.isNotBlank()) {
                        _lastCreatedTechPasskey.value = created
                    }
                    soundEngine.play(SoundCue.SAVE)
                    emitToast(
                        "Firebase Technician created: ${created.email} (@${created.username}). Share Activation Passkey securely."
                    )
                    onSuccess()
                } else {
                    soundEngine.play(SoundCue.ERROR)
                    emitToast(res.exceptionOrNull()?.message ?: "Failed to create Firebase Technician", isError = true)
                }
            } finally {
                _cloudTechBusy.value = false
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
                pushCloudNotification(
                    category = "PAYMENT",
                    title = "Cash Settlement Recorded",
                    message = "Admin cleared ₹${String.format(Locale.US, "%.2f", amount)} for @$technicianUsername",
                    targetRole = "technician",
                    targetUsername = technicianUsername
                )
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
        const val MAX_CUSTOMER_CARE_CHARGE = 300.0

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
        private val soundEngine: SoundEngine,
        private val cloudSync: CloudSyncRepository? = repository.cloudSync
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return BillingViewModel(repository, soundEngine, cloudSync) as T
        }
    }
}
