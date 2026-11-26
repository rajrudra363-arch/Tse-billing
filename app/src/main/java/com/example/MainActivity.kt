package com.example

import android.Manifest
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.AppDatabase
import com.example.data.BillingRepository
import com.example.data.BillingTimePolicy
import com.example.data.CloudSyncRepository
import com.example.data.CloudWorkspaceNotification
import com.example.data.CompanySettings
import com.example.data.DocumentType
import com.example.data.HelpCategory
import com.example.data.UserSession
import com.example.data.VoiceAndNoteSettings
import com.example.ui.BillingEvent
import com.example.ui.BillingViewModel
import com.example.ui.MainTab
import com.example.ui.components.AuthScreen
import com.example.ui.components.MandatoryPasswordChangeScreen
import com.example.ui.components.PaymentAndReviewDialog
import com.example.ui.components.ProvideSafeTextToolbar
import com.example.ui.components.SplashOverlay
import com.example.ui.components.TseShieldLogo
import com.example.ui.components.clearFocusAndHideToolbar
import com.example.ui.screens.AdminPanelScreen
import com.example.ui.screens.BillScreen
import com.example.ui.screens.CustomerHelpScreen
import com.example.ui.screens.CustomersScreen
import com.example.ui.screens.EmployeeManagementScreen
import com.example.ui.screens.ProductsScreen
import com.example.ui.screens.SavedBillsScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SuperAdminDashboardScreen
import com.example.ui.theme.AmberWarn
import com.example.ui.theme.CyanGlow
import com.example.ui.theme.GoldAccent
import com.example.ui.theme.GoldSoft
import com.example.ui.theme.GreenPaid
import com.example.ui.theme.NavyDeep
import com.example.ui.theme.RedDue
import com.example.ui.theme.TealAccent
import com.example.ui.theme.TseBillingTheme
import com.example.util.AdminNotificationHelper
import com.example.util.AppUpdateChecker
import com.example.util.DeviceAuthHelper
import com.example.util.DownloadsSaveResult
import com.example.util.GitHubReleaseInfo
import com.example.util.InvoicePdfPrinter
import com.example.util.InvoicePrintPayload
import com.example.util.SoundEngine
import com.example.util.WhatsAppHelper
import com.example.util.configureAppCheck
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : FragmentActivity() {
    private lateinit var soundEngine: SoundEngine

    private val viewModel: BillingViewModel by viewModels {
        val db = AppDatabase.getInstance(applicationContext)
        val cloudSync = CloudSyncRepository(applicationContext)
        BillingViewModel.Factory(BillingRepository(db, cloudSync), soundEngine, cloudSync)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureAppCheck(this, intent)
        soundEngine = SoundEngine(applicationContext)
        viewModel.handleIncomingInviteUri(intent?.data)
        enableEdgeToEdge()

        setContent {
            TseBillingTheme {
                TseBillingApp(viewModel = viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        viewModel.handleIncomingInviteUri(intent.data)
    }

    override fun onDestroy() {
        super.onDestroy()
        soundEngine.release()
    }
}

private data class NavItem(
    val tab: MainTab,
    val label: String,
    val icon: ImageVector
)

private tailrec fun Context.findFragmentActivity(): FragmentActivity? = when (this) {
    is FragmentActivity -> this
    is ContextWrapper -> baseContext.findFragmentActivity()
    else -> null
}

@Composable
fun TseBillingApp(viewModel: BillingViewModel) {
    ProvideSafeTextToolbar {
        val context = LocalContext.current
        val focusManager = LocalFocusManager.current
        val textToolbar = LocalTextToolbar.current
        val snackbarHostState = remember { SnackbarHostState() }

        val splashVisible by viewModel.splashVisible.collectAsStateWithLifecycle()
        val session by viewModel.session.collectAsStateWithLifecycle()
        val isAdminConfigured by viewModel.isAdminConfigured.collectAsStateWithLifecycle()
        val accounts by viewModel.accounts.collectAsStateWithLifecycle()
        val authError by viewModel.authError.collectAsStateWithLifecycle()
        val summaryError by viewModel.summaryError.collectAsStateWithLifecycle()

        val activeTab by viewModel.activeTab.collectAsStateWithLifecycle()
        val billDraft by viewModel.billDraft.collectAsStateWithLifecycle()
        val bills by viewModel.bills.collectAsStateWithLifecycle()
        val visibleBills by viewModel.visibleBills.collectAsStateWithLifecycle()
        val filteredBills by viewModel.filteredBills.collectAsStateWithLifecycle()
        val billSearch by viewModel.billSearchQuery.collectAsStateWithLifecycle()
        val customers by viewModel.customers.collectAsStateWithLifecycle()
        val customerSearch by viewModel.customerSearchQuery.collectAsStateWithLifecycle()
        val visibleHelpRequests by viewModel.visibleHelpRequests.collectAsStateWithLifecycle()
        val helpRequests by viewModel.helpRequests.collectAsStateWithLifecycle()
        val products by viewModel.products.collectAsStateWithLifecycle()
        val company by viewModel.companySettings.collectAsStateWithLifecycle()
        val upi by viewModel.upiSettings.collectAsStateWithLifecycle()
        val voiceAndNote by viewModel.voiceAndNoteSettings.collectAsStateWithLifecycle()
        val deviceAuthByUser by viewModel.deviceAuthEnabledByUser.collectAsStateWithLifecycle()
        val technicianPerformance by viewModel.technicianPerformance.collectAsStateWithLifecycle()
        val customerFolders by viewModel.customerFolders.collectAsStateWithLifecycle()
        val soundEnabled by viewModel.soundEnabled.collectAsStateWithLifecycle()
        val exportOptionsPayload by viewModel.exportOptionsPayload.collectAsStateWithLifecycle()
        val settlementDialogBill by viewModel.settlementDialogBill.collectAsStateWithLifecycle()
        val availableUpdate by viewModel.availableUpdate.collectAsStateWithLifecycle()
        val updateCheckBusy by viewModel.updateCheckBusy.collectAsStateWithLifecycle()
        val loginSecurityState by viewModel.loginSecurityState.collectAsStateWithLifecycle()
        val adminLoginSecurityState by viewModel.adminLoginSecurityState.collectAsStateWithLifecycle()
        val cloudUser by viewModel.cloudUser.collectAsStateWithLifecycle()
        val cloudBusy by viewModel.cloudBusy.collectAsStateWithLifecycle()
        val cloudTechBusy by viewModel.cloudTechBusy.collectAsStateWithLifecycle()
        val pendingTechPasswordChange by viewModel.pendingTechPasswordChange.collectAsStateWithLifecycle()
        val cloudWorkspaceInfo by viewModel.cloudWorkspaceInfo.collectAsStateWithLifecycle()
        val hasAdminVerifyCode by viewModel.hasAdminVerifyCode.collectAsStateWithLifecycle()
        val expenses by viewModel.expenses.collectAsStateWithLifecycle()
        val trashItems by viewModel.trashItems.collectAsStateWithLifecycle()
        val auditLogs by viewModel.auditLogs.collectAsStateWithLifecycle()
        val isStaffOnlyApk by viewModel.isStaffOnlyApk.collectAsStateWithLifecycle()
        val prefillInviteCode by viewModel.prefillInviteCode.collectAsStateWithLifecycle()
        val notifications by viewModel.notifications.collectAsStateWithLifecycle()
        val unreadNotificationCount by viewModel.unreadNotificationCount.collectAsStateWithLifecycle()
        val techActivationState by viewModel.techActivationState.collectAsStateWithLifecycle()
        val activationBusy by viewModel.activationBusy.collectAsStateWithLifecycle()
        val activationError by viewModel.activationError.collectAsStateWithLifecycle()
        val lastCreatedTechPasskey by viewModel.lastCreatedTechPasskey.collectAsStateWithLifecycle()
        val dueInstallationReminders by viewModel.dueInstallationReminders.collectAsStateWithLifecycle()
        val comboPackages by viewModel.comboPackages.collectAsStateWithLifecycle()
        val employees by viewModel.employees.collectAsStateWithLifecycle()
        val salaryPayments by viewModel.salaryPayments.collectAsStateWithLifecycle()
        val employeeSalarySummaries by viewModel.employeeSalarySummaries.collectAsStateWithLifecycle()
        val upiTransactions by viewModel.upiTransactions.collectAsStateWithLifecycle()
        val activeUpiTransaction by viewModel.activeUpiTransaction.collectAsStateWithLifecycle()
        val qrGatewayBusy by viewModel.qrGatewayBusy.collectAsStateWithLifecycle()
        val billPaymentFilter by viewModel.billPaymentFilter.collectAsStateWithLifecycle()
        val otpState by viewModel.otpState.collectAsStateWithLifecycle()
        val adminVerificationState by viewModel.adminVerificationState.collectAsStateWithLifecycle()
        val passkeyBusy by viewModel.passkeyBusy.collectAsStateWithLifecycle()
        var showNotificationCenter by remember { mutableStateOf(false) }

        val lifecycleOwner = LocalLifecycleOwner.current
        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    viewModel.purgeExpiredTrashIfReady()
                    viewModel.refreshAdminVerificationIfWaiting()
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }

        val scope = rememberCoroutineScope()
        var pendingSavePdfPayload by remember { mutableStateOf<InvoicePrintPayload?>(null) }
        var pendingSavePhotoPayload by remember { mutableStateOf<InvoicePrintPayload?>(null) }
        var pendingNotifRelease by remember { mutableStateOf<GitHubReleaseInfo?>(null) }
        var showUpdateDialog by remember { mutableStateOf(false) }

        // Real-time 1-second clock + Night closure check (12:01 AM - 6:00 AM)
        var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
        LaunchedEffect(Unit) {
            while (true) {
                nowMillis = System.currentTimeMillis()
                delay(1000L)
            }
        }
        val liveTimeText = remember(nowMillis) {
            SimpleDateFormat("hh:mm:ss a", Locale.US).format(Date(nowMillis))
        }
        val isNightClosed = remember(nowMillis) {
            BillingTimePolicy.isNightClosedNow(nowMillis)
        }

        val savePdfLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.CreateDocument("application/pdf")
        ) { uri ->
            val payload = pendingSavePdfPayload
            pendingSavePdfPayload = null
            if (uri != null && payload != null) {
                scope.launch {
                    val ok = withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri)?.use { out ->
                            InvoicePdfPrinter.writePdfToStream(payload, out)
                        } ?: false
                    }
                    viewModel.notifyPdfSaved(ok)
                }
            }
        }

        val savePhotoLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.CreateDocument("image/png")
        ) { uri ->
            val payload = pendingSavePhotoPayload
            pendingSavePhotoPayload = null
            if (uri != null && payload != null) {
                scope.launch {
                    val ok = withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri)?.use { out ->
                            InvoicePdfPrinter.saveInvoicePhotoToStream(payload, out)
                        } ?: false
                    }
                    viewModel.notifyPhotoSaved(ok)
                }
            }
        }

        val notifPermissionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { granted ->
            val rel = pendingNotifRelease
            pendingNotifRelease = null
            if (granted && rel != null) {
                AppUpdateChecker.postUpdateNotification(context, rel)
            }
        }

        val initialPermissionsLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestMultiplePermissions()
        ) {
            viewModel.markInitialPermissionsPrompted()
        }

        LaunchedEffect(splashVisible) {
            if (!splashVisible && viewModel.shouldPromptInitialPermissions()) {
                val perms = buildList {
                    add(Manifest.permission.CAMERA)
                    add(Manifest.permission.ACCESS_FINE_LOCATION)
                    add(Manifest.permission.ACCESS_COARSE_LOCATION)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        add(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
                val missing = perms.filter {
                    ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
                }
                if (missing.isNotEmpty()) {
                    runCatching { initialPermissionsLauncher.launch(missing.toTypedArray()) }
                        .onFailure { viewModel.markInitialPermissionsPrompted() }
                } else {
                    viewModel.markInitialPermissionsPrompted()
                }
            }
        }

        LaunchedEffect(availableUpdate) {
            if (availableUpdate != null) {
                showUpdateDialog = true
            }
        }

        LaunchedEffect(Unit) {
            viewModel.events.collect { event ->
                when (event) {
                    is BillingEvent.Toast -> snackbarHostState.showSnackbar(event.message)
                    is BillingEvent.PrintPdf -> InvoicePdfPrinter.printInvoice(context, event.payload)
                    is BillingEvent.SavePdf -> {
                        val prefix = if (event.payload.documentType == DocumentType.QUOTATION) "Quotation" else "Invoice"
                        val cleanNo = event.payload.invoiceNo.replace(Regex("[^A-Za-z0-9_-]"), "_").ifBlank { "Bill" }
                        val fileName = "TSE_${prefix}_$cleanNo.pdf"
                        val saveRes = WhatsAppHelper.saveDocumentToDownloads(
                            context = context,
                            fileName = fileName,
                            mimeType = "application/pdf"
                        ) { out ->
                            InvoicePdfPrinter.writePdfToStream(event.payload, out)
                        }
                        when (saveRes) {
                            is DownloadsSaveResult.Saved -> {
                                viewModel.notifyPdfSaved(true)
                                snackbarHostState.showSnackbar("Saved PDF to ${saveRes.displayPath}")
                            }
                            is DownloadsSaveResult.NeedsPickerFallback -> {
                                pendingSavePdfPayload = event.payload
                                runCatching { savePdfLauncher.launch(fileName) }.onFailure {
                                    pendingSavePdfPayload = null
                                    snackbarHostState.showSnackbar("Unable to open PDF saver.")
                                }
                            }
                        }
                    }
                    is BillingEvent.SharePdf -> {
                        val ok = InvoicePdfPrinter.sharePdf(context, event.payload)
                        if (!ok) snackbarHostState.showSnackbar("Could not share A4 PDF")
                    }
                    is BillingEvent.SavePhoto -> {
                        val prefix = if (event.payload.documentType == DocumentType.QUOTATION) "Quotation" else "Invoice"
                        val cleanNo = event.payload.invoiceNo.replace(Regex("[^A-Za-z0-9_-]"), "_").ifBlank { "Bill" }
                        val fileName = "TSE_${prefix}_$cleanNo.png"
                        val saveRes = WhatsAppHelper.saveDocumentToDownloads(
                            context = context,
                            fileName = fileName,
                            mimeType = "image/png"
                        ) { out ->
                            InvoicePdfPrinter.saveInvoicePhotoToStream(event.payload, out)
                        }
                        when (saveRes) {
                            is DownloadsSaveResult.Saved -> {
                                viewModel.notifyPhotoSaved(true)
                                snackbarHostState.showSnackbar("Saved Photo to ${saveRes.displayPath}")
                            }
                            is DownloadsSaveResult.NeedsPickerFallback -> {
                                pendingSavePhotoPayload = event.payload
                                runCatching { savePhotoLauncher.launch(fileName) }.onFailure {
                                    pendingSavePhotoPayload = null
                                    snackbarHostState.showSnackbar("Unable to open Photo saver.")
                                }
                            }
                        }
                    }
                    is BillingEvent.SharePhoto -> {
                        val ok = InvoicePdfPrinter.shareInvoicePhoto(context, event.payload)
                        if (!ok) snackbarHostState.showSnackbar("Could not share document photo")
                    }
                    is BillingEvent.RequestDeviceAuthForBill -> {
                        val activity = context.findFragmentActivity()
                        if (activity == null) {
                            snackbarHostState.showSnackbar("Unable to open device lock prompt on this screen.")
                        } else {
                            DeviceAuthHelper.authenticate(
                                activity = activity,
                                title = event.title,
                                subtitle = event.subtitle,
                                onSuccess = { viewModel.commitGeneratedBill() },
                                onError = { err ->
                                    scope.launch { snackbarHostState.showSnackbar(err) }
                                }
                            )
                        }
                    }
                    is BillingEvent.NotifyAppUpdate -> {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            val granted = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.POST_NOTIFICATIONS
                            ) == PackageManager.PERMISSION_GRANTED
                            if (granted) {
                                AppUpdateChecker.postUpdateNotification(context, event.release)
                            } else {
                                pendingNotifRelease = event.release
                                notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        } else {
                            AppUpdateChecker.postUpdateNotification(context, event.release)
                        }
                    }
                    is BillingEvent.NotifyTechnicianBill -> {
                        AdminNotificationHelper.notifyTechnicianBillCreated(
                            context = context,
                            invoiceNo = event.invoiceNo,
                            technicianUsername = event.technicianUsername,
                            customerName = event.customerName
                        )
                    }
                    is BillingEvent.NotifySyncAlert -> {
                        AdminNotificationHelper.notifySyncAlert(
                            context = context,
                            notificationId = event.id.hashCode(),
                            title = event.title,
                            message = event.message
                        )
                    }
                }
            }
        }

        var loginWelcomeSpoken by remember { mutableStateOf(false) }
        LaunchedEffect(splashVisible, session, isAdminConfigured) {
            if (!splashVisible && session == null && isAdminConfigured == true) {
                if (!loginWelcomeSpoken) {
                    loginWelcomeSpoken = true
                    viewModel.speakLoginWelcome()
                }
            } else if (session != null) {
                loginWelcomeSpoken = false
            }
        }

        val navItems = remember(session, isStaffOnlyApk) {
            val s = session
            when {
                s == null -> emptyList()
                s.isSuperAdmin -> listOf(
                    NavItem(MainTab.ANALYTICS, "Analytics", Icons.Default.Analytics),
                    NavItem(MainTab.SAVED, "Saved", Icons.Default.ReceiptLong),
                    NavItem(MainTab.CUSTOMERS, "Customers", Icons.Default.People),
                    NavItem(MainTab.HELP, "Help Desk", Icons.Default.SupportAgent),
                    NavItem(MainTab.PRODUCTS, "Products", Icons.Default.Inventory2),
                    NavItem(MainTab.EMPLOYEES, "Employees", Icons.Default.People),
                    NavItem(MainTab.SETTINGS, "Settings", Icons.Default.Settings)
                )
                s.isAdmin && !isStaffOnlyApk -> listOf(
                    NavItem(MainTab.BILL, "New Bill", Icons.Default.Receipt),
                    NavItem(MainTab.SAVED, "Saved", Icons.Default.ReceiptLong),
                    NavItem(MainTab.CUSTOMERS, "Customers", Icons.Default.People),
                    NavItem(MainTab.HELP, "Help Desk", Icons.Default.SupportAgent),
                    NavItem(MainTab.PRODUCTS, "Products", Icons.Default.Inventory2),
                    NavItem(MainTab.ANALYTICS, "Analytics", Icons.Default.Analytics),
                    NavItem(MainTab.ADMIN, "Admin", Icons.Default.AdminPanelSettings),
                    NavItem(MainTab.SETTINGS, "Settings", Icons.Default.Settings)
                )
                else -> listOf(
                    NavItem(MainTab.BILL, "New Bill", Icons.Default.Receipt),
                    NavItem(MainTab.SAVED, "Saved", Icons.Default.ReceiptLong),
                    NavItem(MainTab.CUSTOMERS, "Customers", Icons.Default.People),
                    NavItem(MainTab.HELP, "Help Desk", Icons.Default.SupportAgent),
                    NavItem(MainTab.PRODUCTS, "Products", Icons.Default.Inventory2),
                    NavItem(MainTab.SETTINGS, "Settings", Icons.Default.Settings)
                )
            }
        }

        val homeTabForSession = if (session?.isSuperAdmin == true) MainTab.ANALYTICS else MainTab.BILL
        if (session != null && activeTab != homeTabForSession) {
            BackHandler {
                clearFocusAndHideToolbar(focusManager, textToolbar)
                viewModel.selectTab(homeTabForSession)
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            val activePendingPasswordChange = pendingTechPasswordChange
            if (activePendingPasswordChange != null) {
                MandatoryPasswordChangeScreen(
                    pendingState = activePendingPasswordChange,
                    authError = authError,
                    busy = cloudBusy,
                    logoBase64 = company.logoBase64,
                    passkeyBusy = passkeyBusy,
                    onSubmitNewPassword = viewModel::completeFirstLoginPasswordChange,
                    onCompleteFirstLogin = { uname, newP, confP, photoBmp ->
                        viewModel.completeFirstLoginPasswordChange(
                            newPassword = newP,
                            confirmPassword = confP,
                            usernameInput = uname,
                            photoBitmap = photoBmp
                        )
                    },
                    onRegisterPasskey = {
                        viewModel.registerPasskeyForCurrentUser(context.findFragmentActivity() ?: context)
                    },
                    onSignOut = { viewModel.cancelPendingPasswordChangeAndSignOut(context) }
                )
                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .imePadding()
                )
            } else if (session == null) {
                AuthScreen(
                    isAdminConfigured = isAdminConfigured,
                    accounts = accounts,
                    authError = authError,
                    logoBase64 = company.logoBase64,
                    loginSecurityState = loginSecurityState,
                    adminLoginSecurityState = adminLoginSecurityState,
                    cloudUser = cloudUser,
                    cloudBusy = cloudBusy,
                    hasAdminVerifyCode = hasAdminVerifyCode,
                    isStaffOnlyApk = isStaffOnlyApk,
                    prefillInviteCode = prefillInviteCode,
                    techActivationState = techActivationState,
                    activationBusy = activationBusy,
                    activationError = activationError,
                    otpState = otpState,
                    adminVerificationState = adminVerificationState,
                    passkeyBusy = passkeyBusy,
                    onClearError = viewModel::clearAuthError,
                    onSetupAdmin = viewModel::setupAdmin,
                    onLogin = { u, p, antiRobotPassed, verifyCode ->
                        viewModel.login(u, p, antiRobotPassed, adminVerifyCode = verifyCode)
                    },
                    onImportBackup = { json -> viewModel.importBackupJson(json, allowPreSetupRestore = true) },
                    onSignInGoogle = { viewModel.signInWithGoogle(context.findFragmentActivity() ?: context) },
                    onSignInTechnicianEmail = viewModel::loginTechnicianWithEmail,
                    onJoinInviteCode = viewModel::joinWorkspaceWithInviteCode,
                    onVerifyTechActivation = viewModel::verifyTechnicianActivation,
                    onClearTechActivation = viewModel::clearTechActivation,
                    onResetAdminPassword = viewModel::resetAdminPassword,
                    onRequestOtp = viewModel::requestEmailOtp,
                    onVerifyOtp = viewModel::verifyEmailOtp,
                    onClearOtp = viewModel::clearOtpState,
                    onCheckAdminEmailVerification = viewModel::checkAdminEmailVerificationAndComplete,
                    onResendAdminVerificationEmail = viewModel::resendAdminVerificationEmail,
                    onCancelPendingAdminVerification = viewModel::cancelPendingAdminVerification,
                    onRegisterCompanyAdmin = viewModel::registerAdminWithEmailVerification,
                    onResetPasswordWithOtp = viewModel::resetPasswordViaEmailOtp,
                    onLoginAdminEmailOrUser = viewModel::loginAdminWithEmailOrUsername,
                    onLoginSuperAdmin = viewModel::loginSuperAdmin,
                    onLoginWithPasskey = { expectedRole, emailHint, antiRobotPassed ->
                        viewModel.loginWithPasskey(
                            context = context.findFragmentActivity() ?: context,
                            expectedRole = expectedRole,
                            emailHint = emailHint,
                            antiRobotPassed = antiRobotPassed
                        )
                    }
                )
                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .imePadding()
                )
            } else {
                val currentSession = session!!
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val useRail = maxWidth >= 700.dp

                    Scaffold(
                        modifier = Modifier
                            .fillMaxSize()
                            .imePadding(),
                        contentWindowInsets = WindowInsets(0, 0, 0, 0),
                        snackbarHost = {
                            SnackbarHost(
                                hostState = snackbarHostState,
                                modifier = Modifier.navigationBarsPadding()
                            )
                        },
                        topBar = {
                            TseTopBar(
                                company = company,
                                session = currentSession,
                                voiceAndNote = voiceAndNote,
                                liveTimeText = liveTimeText,
                                isNightClosed = isNightClosed,
                                soundEnabled = soundEnabled,
                                hasAvailableUpdate = availableUpdate != null,
                                unreadNotifications = unreadNotificationCount,
                                onToggleSound = viewModel::toggleSound,
                                onOpenUpdateDialog = { showUpdateDialog = true },
                                onOpenNotifications = {
                                    viewModel.markNotificationsRead()
                                    showNotificationCenter = true
                                },
                                onLogout = {
                                    clearFocusAndHideToolbar(focusManager, textToolbar)
                                    viewModel.logout()
                                }
                            )
                        },
                        bottomBar = {
                            if (!useRail && navItems.size > 1) {
                                if (navItems.size <= 5) {
                                    NavigationBar(
                                        modifier = Modifier.navigationBarsPadding(),
                                        containerColor = MaterialTheme.colorScheme.surface
                                    ) {
                                        navItems.forEach { item ->
                                            val selected = activeTab == item.tab
                                            NavigationBarItem(
                                                selected = selected,
                                                onClick = {
                                                    clearFocusAndHideToolbar(focusManager, textToolbar)
                                                    viewModel.selectTab(item.tab)
                                                },
                                                icon = { Icon(item.icon, contentDescription = item.label) },
                                                label = {
                                                    Text(
                                                        text = item.label,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                },
                                                modifier = Modifier.testTag("nav_${item.tab.route}")
                                            )
                                        }
                                    }
                                } else {
                                    Surface(
                                        tonalElevation = 3.dp,
                                        color = MaterialTheme.colorScheme.surface,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .navigationBarsPadding()
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .horizontalScroll(rememberScrollState())
                                                .padding(horizontal = 4.dp, vertical = 4.dp),
                                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            navItems.forEach { item ->
                                                val selected = activeTab == item.tab
                                                NavigationBarItem(
                                                    selected = selected,
                                                    onClick = {
                                                        clearFocusAndHideToolbar(focusManager, textToolbar)
                                                        viewModel.selectTab(item.tab)
                                                    },
                                                    icon = { Icon(item.icon, contentDescription = item.label) },
                                                    label = {
                                                        Text(
                                                            text = item.label,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    },
                                                    modifier = Modifier
                                                        .widthIn(min = 76.dp)
                                                        .testTag("nav_${item.tab.route}")
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    ) { innerPadding ->
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                                .background(MaterialTheme.colorScheme.background)
                        ) {
                            if (useRail && navItems.size > 1) {
                                NavigationRail(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .navigationBarsPadding(),
                                    containerColor = MaterialTheme.colorScheme.surface
                                ) {
                                    Spacer(Modifier.height(8.dp))
                                    navItems.forEach { item ->
                                        NavigationRailItem(
                                            selected = activeTab == item.tab,
                                            onClick = {
                                                clearFocusAndHideToolbar(focusManager, textToolbar)
                                                viewModel.selectTab(item.tab)
                                            },
                                            icon = { Icon(item.icon, contentDescription = item.label) },
                                            label = { Text(item.label) },
                                            modifier = Modifier.testTag("rail_${item.tab.route}")
                                        )
                                    }
                                }
                            }

                            AnimatedContent(
                                targetState = activeTab,
                                transitionSpec = {
                                    (fadeIn(tween(240)) + slideInVertically(tween(240)) { it / 18 }) togetherWith
                                        fadeOut(tween(150))
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                                label = "tabContent"
                            ) { tab ->
                                when (tab) {
                                    MainTab.EMPLOYEES -> EmployeeManagementScreen(
                                         user = currentSession,
                                         summaries = employeeSalarySummaries,
                                         salaryPayments = salaryPayments,
                                         onSaveEmployee = { emp, onDone -> viewModel.saveEmployee(emp, onDone) },
                                         onDeleteEmployee = viewModel::deleteEmployee,
                                         onRecordSalaryPayment = viewModel::recordSalaryPayment
                                    )

                                    MainTab.BILL -> BillScreen(
                                        draft = billDraft,
                                        products = products,
                                        customers = customers,
                                        allBills = bills,
                                        company = company,
                                        upi = upi,
                                        currentUser = currentSession,
                                        isNightClosed = isNightClosed,
                                        summaryError = summaryError,
                                        exportOptionsPayload = exportOptionsPayload,
                                        onSetDocumentType = viewModel::setDocumentType,
                                        onQuotationNoteChange = viewModel::updateQuotationNote,
                                        onCustomerNameChange = viewModel::updateCustomerName,
                                        onCustomerSelected = viewModel::selectCustomerSuggestion,
                                        onCustomerDetailsChange = viewModel::updateCustomerDetails,
                                        onCustomerLocationUrlChange = viewModel::updateCustomerLocationUrl,
                                        onInvoiceNoChange = viewModel::updateInvoiceNo,
                                        onInvoiceDateChange = viewModel::updateInvoiceDate,
                                        onAddItem = viewModel::addItem,
                                        onSelectFolderForRow = viewModel::selectFolderForRow,
                                        onSelectProductForRow = viewModel::selectProductForRow,
                                        onUpdateItem = viewModel::updateItemFields,
                                        onRemoveItem = viewModel::removeItem,
                                        onExtraChargeTypeChange = viewModel::updateExtraChargeType,
                                        onExtraChargeAmountChange = viewModel::updateExtraChargeAmount,
                                        onToggleDualExtraCharge = viewModel::toggleDualExtraChargeType,
                                        onSecondaryChargeAmountChange = viewModel::updateSecondaryChargeAmount,
                                        onDiscountChange = viewModel::updateDiscount,
                                        onPaidAmountChange = viewModel::updatePaidAmount,
                                        onPaymentModeChange = viewModel::updatePaymentMode,
                                        onSignatureChanged = viewModel::updateSignatureBitmap,
                                        onCustomQrTextChange = viewModel::updateCustomQrText,
                                        onStaffRatingChange = viewModel::updateStaffRatingInDraft,
                                        onStaffReviewChange = viewModel::updateStaffReviewInDraft,
                                        onToggleSheetPreview = viewModel::toggleSheetPreview,
                                        onResetBill = viewModel::resetBill,
                                        onOpenSummaryModal = { viewModel.openSummaryModal() },
                                        onCloseSummaryModal = viewModel::closeSummaryModal,
                                        onRequestGenerateWithDeviceAuth = { viewModel.requestBillGenerationWithDeviceAuth() },
                                        onDismissExportOptions = viewModel::dismissExportOptions,
                                        onPrintA4 = viewModel::triggerPrintA4,
                                        onSavePdf = viewModel::triggerSavePdf,
                                        onSharePdf = viewModel::triggerSharePdf,
                                        onSavePhoto = viewModel::triggerSavePhoto,
                                        onSharePhoto = viewModel::triggerSharePhoto,
                                        onOpenPaymentAndReview = viewModel::openSettlementReviewForExport,
                                        comboPackages = comboPackages,
                                        onAddComboPackage = viewModel::addComboPackageToDraft
                                    )

                                    MainTab.SAVED -> SavedBillsScreen(
                                        allBills = visibleBills,
                                        filteredBills = filteredBills,
                                        customerFolders = customerFolders,
                                        searchQuery = billSearch,
                                        company = company,
                                        upi = upi,
                                        isAdmin = currentSession.isAdmin,
                                        isSuperAdmin = currentSession.isSuperAdmin,
                                        onSearchChange = viewModel::updateBillSearch,
                                        onOpenBill = viewModel::openBillForEdit,
                                        onPrintBill = viewModel::printBillDirect,
                                        onSavePdf = viewModel::saveBillPdfDirect,
                                        onSharePdf = viewModel::shareBillPdfDirect,
                                        onWhatsAppPdf = { bill -> viewModel.shareBillPdfToCustomerWhatsApp(context, bill) },
                                        onSendDueQr = { bill -> viewModel.shareDueQrToCustomerWhatsApp(context, bill) },
                                        onMarkDuePaid = viewModel::markDuePaidInFull,
                                        onSavePhoto = viewModel::saveBillPhotoDirect,
                                        onSharePhoto = viewModel::shareBillPhotoDirect,
                                        onDeleteBill = viewModel::deleteBill,
                                        onOpenPaymentReview = viewModel::openSettlementReviewDialog
                                    )

                                    MainTab.CUSTOMERS -> CustomersScreen(
                                        customers = customers,
                                        bills = bills,
                                        user = currentSession,
                                        searchQuery = customerSearch,
                                        onSearchChange = viewModel::updateCustomerSearch,
                                        onSaveCustomer = { id, name, phone, address, locationUrl ->
                                            viewModel.saveCustomer(
                                                name = name,
                                                phone = phone,
                                                address = address,
                                                locationUrl = locationUrl,
                                                id = id
                                            )
                                        },
                                        onDeleteCustomer = viewModel::deleteCustomer,
                                        onDeleteBill = viewModel::deleteBill,
                                        onOpenBill = viewModel::openBillForEdit
                                    )

                                    MainTab.HELP -> CustomerHelpScreen(
                                        helpRequests = visibleHelpRequests,
                                        customers = customers,
                                        accounts = accounts,
                                        company = company,
                                        user = currentSession,
                                        dueInstallationReminders = dueInstallationReminders,
                                        onSaveHelpRequest = { id, name, phone, address, locationUrl, desc, status ->
                                            viewModel.saveHelpRequest(
                                                id = id,
                                                customerName = name,
                                                phone = phone,
                                                address = address,
                                                locationUrl = locationUrl,
                                                helpDescription = desc,
                                                status = status
                                            )
                                        },
                                        onSaveHelpRequestWithCategory = { id, name, phone, address, locationUrl, desc, status, category, onDone ->
                                            viewModel.saveHelpRequest(
                                                id = id,
                                                customerName = name,
                                                phone = phone,
                                                address = address,
                                                locationUrl = locationUrl,
                                                helpDescription = desc,
                                                status = status,
                                                category = category,
                                                onSaved = { onDone() }
                                            )
                                        },
                                        onApproveQuotation = { reqId, instDate, tech, onDone ->
                                            viewModel.approveQuotationWithInstallationDate(reqId, instDate, tech, onDone = onDone)
                                        },
                                        onRejectQuotation = viewModel::rejectQuotation,
                                        onRejectHelpRequest = viewModel::rejectHelpRequest,
                                        onDismissInstallationReminder = viewModel::dismissInstallationReminder,
                                        onAcceptRequest = viewModel::acceptHelpRequest,
                                        onMarkSolved = { viewModel.markHelpSolved(it) },
                                        onMarkNotSolved = { viewModel.markHelpNotSolved(it) },
                                        onGetBillFromHelp = viewModel::startBillFromHelpRequest,
                                        onDeleteHelpRequest = viewModel::deleteHelpRequest
                                    )

                                    MainTab.PRODUCTS -> ProductsScreen(
                                        products = products,
                                        user = currentSession,
                                        onAddProduct = { n, u, r, f -> viewModel.addProduct(n, u, r, f) },
                                        onUpdateProduct = { id, n, u, r, f -> viewModel.updateProduct(id, n, u, r, f) },
                                        onDeleteProduct = viewModel::deleteProduct,
                                        onAddProductWithStock = viewModel::addProduct,
                                        onUpdateProductWithStock = viewModel::updateProduct,
                                        comboPackages = comboPackages,
                                        onSaveComboPackage = viewModel::saveComboPackage,
                                        onDeleteComboPackage = viewModel::deleteComboPackage
                                    )

                                    MainTab.ANALYTICS -> SuperAdminDashboardScreen(
                                        bills = bills,
                                        customers = customers,
                                        helpRequests = helpRequests,
                                        products = products,
                                        technicianPerformance = technicianPerformance,
                                        expenses = expenses
                                    )

                                    MainTab.ADMIN -> if (currentSession.isAdmin && !isStaffOnlyApk) {
                                        AdminPanelScreen(
                                            upiSettings = upi,
                                            accounts = accounts,
                                            technicianPerformance = technicianPerformance,
                                            voiceAndNoteSettings = voiceAndNote,
                                            systemVoiceOptions = viewModel.availableSystemVoices(),
                                            cloudUser = cloudUser,
                                            cloudWorkspaceInfo = cloudWorkspaceInfo,
                                            cloudBusy = cloudBusy,
                                            cloudTechBusy = cloudTechBusy,
                                            cloudAuthError = authError,
                                            availableUpdate = availableUpdate,
                                            expenses = expenses,
                                            trashItems = trashItems,
                                            auditLogs = auditLogs,
                                            otpState = otpState,
                                            lastCreatedTechPasskey = lastCreatedTechPasskey,
                                            onSaveUpi = viewModel::saveUpiSettings,
                                            onClearUpi = viewModel::clearUpiId,
                                            onAddTechnician = viewModel::addTechnician,
                                            onCreateCloudTechnician = { n, p, e, u, pw, bmp, done ->
                                                viewModel.createCloudTechnicianAccount(n, p, e, u, pw, bmp, onSuccess = done)
                                            },
                                            onCreateCloudTechnicianOtp = { n, p, e, u, pw, bmp, chId, tok, done ->
                                                viewModel.createCloudTechnicianAccount(
                                                    name = n,
                                                    phone = p,
                                                    email = e,
                                                    username = u,
                                                    initialPassword = pw,
                                                    photoBitmap = bmp,
                                                    otpChallengeId = chId,
                                                    otpVerificationToken = tok,
                                                    requireOtpVerification = true,
                                                    onSuccess = done
                                                )
                                            },
                                            onRequestOtp = viewModel::requestEmailOtp,
                                            onVerifyOtp = viewModel::verifyEmailOtp,
                                            onToggleTechnicianLogin = viewModel::setTechnicianLoginEnabled,
                                            onUpdateTechnician = viewModel::updateTechnician,
                                            onRemoveTechnician = viewModel::removeTechnician,
                                            onAddSuperAdmin = viewModel::addSuperAdmin,
                                            onRemoveSuperAdmin = viewModel::removeSuperAdmin,
                                            onRecordCashSettlement = viewModel::recordTechnicianCashSettlement,
                                            onSaveVoiceAndNoteSettings = viewModel::saveVoiceAndNoteSettings,
                                            onTestVoice = viewModel::testVoiceAnnouncement,
                                            onSignInGoogle = { viewModel.signInWithGoogle(context.findFragmentActivity() ?: context) },
                                            onEnsureCloudWorkspace = viewModel::ensureAdminCloudWorkspace,
                                            onShareApkWhatsApp = { targetRole -> viewModel.shareApkViaWhatsApp(context, targetRole) },
                                            onUpdateAdminVerifyCode = viewModel::updateAdminVerifyCode,
                                            onUpdateAdminCredentials = viewModel::updateAdminCredentials,
                                            onSaveExpense = { title, cat, amt, note, onDone ->
                                                viewModel.saveExpense(title, cat, amt, note, onSuccess = onDone)
                                            },
                                            onDeleteExpense = viewModel::deleteExpense,
                                            onRestoreTrash = viewModel::restoreTrashItem,
                                            onPurgeExpiredTrash = viewModel::purgeExpiredTrashIfReady,
                                            onDismissCreatedTechPasskey = viewModel::dismissCreatedTechPasskey,
                                            onSendTeamNotification = viewModel::sendCustomNotification
                                        )
                                    } else {
                                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                            Text("Admin access required", style = MaterialTheme.typography.titleMedium)
                                        }
                                    }

                                    MainTab.SETTINGS -> SettingsScreen(
                                        settings = company,
                                        user = currentSession,
                                        deviceAuthEnabled = deviceAuthByUser[currentSession.username.lowercase(Locale.ROOT)] == true,
                                        updateCheckBusy = updateCheckBusy,
                                        passkeyBusy = passkeyBusy,
                                        onToggleDeviceAuth = viewModel::setDeviceAuthForCurrentUser,
                                        onRegisterPasskey = {
                                            viewModel.registerPasskeyForCurrentUser(context.findFragmentActivity() ?: context)
                                        },
                                        onSaveSettings = viewModel::saveCompanySettings,
                                        onPickCompanyLogoBitmap = viewModel::saveCompanyLogoBitmap,
                                        onPickFallbackQrBitmap = viewModel::saveFallbackQrBitmap,
                                        onPickAuthorisedSignatureBitmap = viewModel::saveAuthorisedSignatureBitmap,
                                        onExportBackup = viewModel::exportBackupJson,
                                        onImportBackup = viewModel::importBackupJson,
                                        onBackupExported = viewModel::notifyBackupExported,
                                        onCheckForUpdates = { viewModel.checkForAppUpdates(manual = true) }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            val activeSettlementBill = settlementDialogBill
            if (activeSettlementBill != null) {
                PaymentAndReviewDialog(
                    bill = activeSettlementBill,
                    onDismiss = viewModel::dismissSettlementReviewDialog,
                    onSave = viewModel::saveBillSettlementAndReview
                )
            }

            val updateRelease = availableUpdate
            if (showUpdateDialog && updateRelease != null) {
                AlertDialog(
                    onDismissRequest = {
                        showUpdateDialog = false
                        viewModel.dismissUpdateDialog()
                    },
                    modifier = Modifier.testTag("app_update_dialog"),
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.SystemUpdate, contentDescription = null, tint = TealAccent)
                            Text("Update Available (${updateRelease.tagName})", fontWeight = FontWeight.Bold)
                        }
                    },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "A new version of TSE Billing is available on GitHub Releases.",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = updateRelease.releaseNotes,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Surface(
                                color = Color(0xFFE8F5E9),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = "All existing bills, customers, Technician records, and settings remain safe after updating.",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = GreenPaid,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                val started = AppUpdateChecker.triggerUpdateDownloadAndInstall(context, updateRelease)
                                showUpdateDialog = false
                                viewModel.dismissUpdateDialog()
                                scope.launch {
                                    snackbarHostState.showSnackbar(
                                        if (started) "Downloading TSE Billing v${updateRelease.versionName} update..."
                                        else "Unable to start update download."
                                    )
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = GreenPaid, contentColor = Color.White),
                            modifier = Modifier.testTag("update_now_button")
                        ) {
                            Text("Update Now", fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = {
                                showUpdateDialog = false
                                viewModel.dismissUpdateDialog()
                            },
                            modifier = Modifier.testTag("update_later_button")
                        ) {
                            Text("Later")
                        }
                    }
                )
            }

            if (showNotificationCenter && session != null) {
                NotificationCenterDialog(
                    notifications = notifications,
                    currentUser = session!!,
                    onSendNotification = { category, title, message, targetRole, onDone ->
                        viewModel.sendCustomNotification(category, title, message, targetRole, onDone)
                    },
                    onDismiss = { showNotificationCenter = false }
                )
            }

            // Non-blocking Animated Splash Overlay
            AnimatedVisibility(
                visible = splashVisible,
                enter = fadeIn(tween(150)),
                exit = fadeOut(tween(320))
            ) {
                SplashOverlay(
                    logoBase64 = company.logoBase64,
                    onSkip = viewModel::dismissSplash
                )
            }
        }
    }
}

@Composable
private fun NotificationCenterDialog(
    notifications: List<CloudWorkspaceNotification>,
    currentUser: UserSession,
    onSendNotification: (category: String, title: String, message: String, targetRole: String, onDone: () -> Unit) -> Unit,
    onDismiss: () -> Unit
) {
    var msgTitle by remember { mutableStateOf("") }
    var msgBody by remember { mutableStateOf("") }
    val fmt = remember { SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("notification_center_dialog"),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Notifications, contentDescription = null, tint = TealAccent)
                Text("Team Notifications (${notifications.size})", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = if (currentUser.isAdmin) "Send Team Alert" else "Send Message to Admin",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                        OutlinedTextField(
                            value = msgTitle,
                            onValueChange = { msgTitle = it },
                            label = { Text("Title") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("notif_center_title_input")
                        )
                        OutlinedTextField(
                            value = msgBody,
                            onValueChange = { msgBody = it },
                            label = { Text("Message") },
                            minLines = 2,
                            modifier = Modifier.fillMaxWidth().testTag("notif_center_message_input")
                        )
                        Button(
                            onClick = {
                                val targetRole = if (currentUser.isAdmin) "all" else "admin"
                                onSendNotification("GENERAL", msgTitle, msgBody, targetRole) {
                                    msgTitle = ""
                                    msgBody = ""
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = TealAccent, contentColor = Color.White),
                            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("send_notif_dialog_button")
                        ) {
                            Icon(Icons.Default.Send, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Send", fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }

                if (notifications.isEmpty()) {
                    Text(
                        text = "No notifications yet. Product, Stock, Bill, Payment, Service & Quotation alerts appear here in real time.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(notifications, key = { it.id }) { notif ->
                            Surface(
                                color = MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(10.dp),
                                tonalElevation = 1.dp,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = notif.category,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Black,
                                            color = TealAccent
                                        )
                                        Text(
                                            text = fmt.format(Date(notif.createdAt)),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text(notif.title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                                    Text(notif.message, style = MaterialTheme.typography.bodySmall)
                                    if (notif.createdBy.isNotBlank()) {
                                        Text(
                                            text = "By ${notif.createdBy}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TseTopBar(
    company: CompanySettings,
    session: UserSession,
    voiceAndNote: VoiceAndNoteSettings,
    liveTimeText: String,
    isNightClosed: Boolean,
    soundEnabled: Boolean,
    hasAvailableUpdate: Boolean,
    unreadNotifications: Int = 0,
    onToggleSound: () -> Unit,
    onOpenUpdateDialog: () -> Unit,
    onOpenNotifications: () -> Unit = {},
    onLogout: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.horizontalGradient(listOf(NavyDeep, Color(0xFF123E55), TealAccent)))
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TseShieldLogo(
                    logoBase64 = company.logoBase64,
                    modifier = Modifier.size(40.dp)
                )
                val techAvatarBmp = remember(session.photoBase64) {
                    session.photoBase64.takeIf { it.isNotBlank() }?.let { InvoicePdfPrinter.decodeBase64Bitmap(it) }
                }
                if (techAvatarBmp != null) {
                    Image(
                        bitmap = techAvatarBmp.asImageBitmap(),
                        contentDescription = "${session.displayName} profile photo",
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .border(1.5.dp, GoldAccent, CircleShape),
                        contentScale = ContentScale.Crop
                    )
                }
                Column {
                    Text(
                        text = company.companyName,
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${session.displayName} (${session.roleDisplayLabel})",
                        color = GoldSoft,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (hasAvailableUpdate) {
                    IconButton(
                        onClick = onOpenUpdateDialog,
                        modifier = Modifier.testTag("topbar_update_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.SystemUpdate,
                            contentDescription = "Update available",
                            tint = AmberWarn
                        )
                    }
                }

                IconButton(
                    onClick = onOpenNotifications,
                    modifier = Modifier.size(48.dp).testTag("notification_bell_button")
                ) {
                    BadgedBox(
                        badge = {
                            if (unreadNotifications > 0) {
                                Badge(modifier = Modifier.testTag("notification_unread_badge")) {
                                    Text(if (unreadNotifications > 99) "99+" else unreadNotifications.toString())
                                }
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = "Open team notifications",
                            tint = GoldSoft
                        )
                    }
                }

                IconButton(
                    onClick = onToggleSound,
                    modifier = Modifier.testTag("sound_toggle_button")
                ) {
                    Icon(
                        imageVector = if (soundEnabled) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                        contentDescription = if (soundEnabled) "Mute voice and sound" else "Enable voice and sound",
                        tint = if (soundEnabled) CyanGlow else Color.White.copy(alpha = 0.6f)
                    )
                }

                IconButton(
                    onClick = onLogout,
                    modifier = Modifier.testTag("logout_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Logout,
                        contentDescription = stringResource(R.string.app_name) + " Logout",
                        tint = GoldAccent
                    )
                }
            }
        }

        // Contrasting Header Marquee Note + Live Real-Time Clock Strip
        Surface(
            color = if (isNightClosed) Color(0xFF5A161E) else Color(0xFF0A2433),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Campaign,
                    contentDescription = "Notice ticker",
                    tint = GoldAccent,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = voiceAndNote.headerNote.ifBlank {
                        "Welcome to Total Solution Enterprises — Authorized CCTV Sales & Support"
                    },
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    modifier = Modifier
                        .weight(1f)
                        .basicMarquee(iterations = Int.MAX_VALUE)
                        .testTag("header_marquee_note")
                )

                if (isNightClosed) {
                    Surface(
                        color = RedDue,
                        shape = RoundedCornerShape(999.dp),
                        modifier = Modifier.testTag("night_closed_chip")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.NightsStay,
                                contentDescription = "Night closure active",
                                tint = Color.White,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = "CLOSED 12:01AM-6AM",
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                }

                Surface(
                    color = GoldAccent.copy(alpha = 0.22f),
                    shape = RoundedCornerShape(999.dp),
                    modifier = Modifier.testTag("live_clock_pill")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = "Live time",
                            tint = GoldSoft,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = liveTimeText,
                            color = GoldSoft,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        HorizontalDivider(thickness = 2.5.dp, color = GoldAccent)
    }
}
