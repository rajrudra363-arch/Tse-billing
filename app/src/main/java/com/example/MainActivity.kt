package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
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
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.AppDatabase
import com.example.data.AppLanguage
import com.example.data.BillingRepository
import com.example.data.CompanySettings
import com.example.data.UserSession
import com.example.ui.BillingEvent
import com.example.ui.BillingViewModel
import com.example.ui.MainTab
import com.example.ui.components.AuthScreen
import com.example.ui.components.ProvideSafeTextToolbar
import com.example.ui.components.SplashOverlay
import com.example.ui.components.TseShieldLogo
import com.example.ui.components.clearFocusAndHideToolbar
import com.example.ui.screens.AdminPanelScreen
import com.example.ui.screens.BillScreen
import com.example.ui.screens.CustomersScreen
import com.example.ui.screens.ProductsScreen
import com.example.ui.screens.SavedBillsScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.CyanGlow
import com.example.ui.theme.GoldAccent
import com.example.ui.theme.GoldSoft
import com.example.ui.theme.NavyDeep
import com.example.ui.theme.TealAccent
import com.example.ui.theme.TseBillingTheme
import com.example.util.InvoicePdfPrinter
import com.example.util.InvoicePrintPayload
import com.example.util.SoundEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private lateinit var soundEngine: SoundEngine

    private val viewModel: BillingViewModel by viewModels {
        val db = AppDatabase.getInstance(applicationContext)
        BillingViewModel.Factory(BillingRepository(db), soundEngine)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        soundEngine = SoundEngine(applicationContext)
        enableEdgeToEdge()

        setContent {
            TseBillingTheme {
                TseBillingApp(viewModel = viewModel)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        soundEngine.release()
    }
}

private data class NavItem(
    val tab: MainTab,
    val labelEn: String,
    val labelHi: String,
    val icon: ImageVector
)

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

    val activeTab by viewModel.activeTab.collectAsStateWithLifecycle()
    val billDraft by viewModel.billDraft.collectAsStateWithLifecycle()
    val bills by viewModel.bills.collectAsStateWithLifecycle()
    val filteredBills by viewModel.filteredBills.collectAsStateWithLifecycle()
    val billSearch by viewModel.billSearchQuery.collectAsStateWithLifecycle()
    val customers by viewModel.customers.collectAsStateWithLifecycle()
    val products by viewModel.products.collectAsStateWithLifecycle()
    val company by viewModel.companySettings.collectAsStateWithLifecycle()
    val upi by viewModel.upiSettings.collectAsStateWithLifecycle()
    val soundEnabled by viewModel.soundEnabled.collectAsStateWithLifecycle()
    val language by viewModel.language.collectAsStateWithLifecycle()
    val finalModalVisible by viewModel.finalModalVisible.collectAsStateWithLifecycle()
    val finalModalError by viewModel.finalModalError.collectAsStateWithLifecycle()
    val exportOptionsPayload by viewModel.exportOptionsPayload.collectAsStateWithLifecycle()

    val scope = rememberCoroutineScope()
    var pendingSavePayload by remember { mutableStateOf<InvoicePrintPayload?>(null) }

    val savePdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        val payload = pendingSavePayload
        pendingSavePayload = null
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

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is BillingEvent.Toast -> snackbarHostState.showSnackbar(event.message)
                is BillingEvent.PrintPdf -> InvoicePdfPrinter.printInvoice(context, event.payload)
                is BillingEvent.SavePdf -> {
                    pendingSavePayload = event.payload
                    val cleanNo = event.payload.invoiceNo.replace(Regex("[^A-Za-z0-9_-]"), "_").ifBlank { "Bill" }
                    savePdfLauncher.launch("TSE_Invoice_$cleanNo.pdf")
                }
                is BillingEvent.SharePdf -> {
                    val ok = InvoicePdfPrinter.sharePdf(context, event.payload)
                    if (!ok) snackbarHostState.showSnackbar("Could not share A4 PDF")
                }
            }
        }
    }

    val navItems = remember(session) {
        buildList {
            add(NavItem(MainTab.BILL, "New Bill", "नया बिल", Icons.Default.Receipt))
            if (session?.isAdmin == true) {
                add(NavItem(MainTab.SAVED, "Saved", "सेव बिल", Icons.Default.ReceiptLong))
                add(NavItem(MainTab.CUSTOMERS, "Customers", "ग्राहक", Icons.Default.People))
                add(NavItem(MainTab.PRODUCTS, "Products", "प्रोडक्ट", Icons.Default.Inventory2))
                add(NavItem(MainTab.ADMIN, "Admin", "एडमिन", Icons.Default.AdminPanelSettings))
                add(NavItem(MainTab.SETTINGS, "Settings", "सेटिंग्स", Icons.Default.Settings))
            }
        }
    }

    if (session != null && activeTab != MainTab.BILL) {
        BackHandler {
            clearFocusAndHideToolbar(focusManager, textToolbar)
            viewModel.selectTab(MainTab.BILL)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (session == null) {
            AuthScreen(
                isAdminConfigured = isAdminConfigured,
                accounts = accounts,
                authError = authError,
                onClearError = viewModel::clearAuthError,
                onSetupAdmin = viewModel::setupAdmin,
                onLogin = viewModel::login
            )
        } else {
            val currentSession = session!!
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val useRail = maxWidth >= 700.dp

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
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
                            soundEnabled = soundEnabled,
                            language = language,
                            onToggleSound = viewModel::toggleSound,
                            onToggleLanguage = {
                                clearFocusAndHideToolbar(focusManager, textToolbar)
                                val next = if (language == AppLanguage.EN) AppLanguage.HI else AppLanguage.EN
                                viewModel.setLanguage(next)
                            },
                            onLogout = {
                                clearFocusAndHideToolbar(focusManager, textToolbar)
                                viewModel.logout()
                            }
                        )
                    },
                    bottomBar = {
                        if (!useRail && navItems.size > 1) {
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
                                        icon = { Icon(item.icon, contentDescription = item.labelEn) },
                                        label = {
                                            Text(
                                                text = if (language == AppLanguage.HI) item.labelHi else item.labelEn,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        },
                                        modifier = Modifier.testTag("nav_${item.tab.route}")
                                    )
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
                                modifier = Modifier.fillMaxHeight().navigationBarsPadding(),
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
                                        icon = { Icon(item.icon, contentDescription = item.labelEn) },
                                        label = { Text(if (language == AppLanguage.HI) item.labelHi else item.labelEn) },
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
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            label = "tabContent"
                        ) { tab ->
                            when (tab) {
                                MainTab.BILL -> BillScreen(
                                    draft = billDraft,
                                    products = products,
                                    customers = customers,
                                    company = company,
                                    upi = upi,
                                    language = language,
                                    currentUser = currentSession,
                                    finalModalVisible = finalModalVisible,
                                    finalModalError = finalModalError,
                                    exportOptionsPayload = exportOptionsPayload,
                                    onCustomerNameChange = viewModel::updateCustomerName,
                                    onCustomerSelected = viewModel::selectCustomerSuggestion,
                                    onCustomerDetailsChange = viewModel::updateCustomerDetails,
                                    onInvoiceNoChange = viewModel::updateInvoiceNo,
                                    onInvoiceDateChange = viewModel::updateInvoiceDate,
                                    onAddItem = viewModel::addItem,
                                    onSelectProductForRow = viewModel::selectProductForRow,
                                    onUpdateItem = viewModel::updateItemFields,
                                    onRemoveItem = viewModel::removeItem,
                                    onDiscountChange = viewModel::updateDiscount,
                                    onPaidAmountChange = viewModel::updatePaidAmount,
                                    onPaymentModeChange = viewModel::updatePaymentMode,
                                    onSignatureChanged = viewModel::updateSignatureBitmap,
                                    onCustomQrTextChange = viewModel::updateCustomQrText,
                                    onToggleSheetPreview = viewModel::toggleSheetPreview,
                                    onResetBill = viewModel::resetBill,
                                    onSaveBill = { viewModel.saveBill() },
                                    onOpenFinalCodeModal = viewModel::openFinalCodeModal,
                                    onCloseFinalCodeModal = viewModel::closeFinalCodeModal,
                                    onConfirmFinalBill = viewModel::confirmFinalBill,
                                    onDismissExportOptions = viewModel::dismissExportOptions,
                                    onPrintA4 = viewModel::triggerPrintA4,
                                    onSavePdf = viewModel::triggerSavePdf,
                                    onSharePdf = viewModel::triggerSharePdf
                                )

                                MainTab.SAVED -> SavedBillsScreen(
                                    allBills = bills,
                                    filteredBills = filteredBills,
                                    searchQuery = billSearch,
                                    company = company,
                                    upi = upi,
                                    isAdmin = currentSession.isAdmin,
                                    onSearchChange = viewModel::updateBillSearch,
                                    onOpenBill = viewModel::openBillForEdit,
                                    onPrintBill = viewModel::printBillDirect,
                                    onSavePdf = viewModel::saveBillPdfDirect,
                                    onSharePdf = viewModel::shareBillPdfDirect,
                                    onDeleteBill = viewModel::deleteBill
                                )

                                MainTab.CUSTOMERS -> CustomersScreen(
                                    customers = customers,
                                    isAdmin = currentSession.isAdmin,
                                    onSaveCustomer = viewModel::saveCustomer,
                                    onDeleteCustomer = viewModel::deleteCustomer
                                )

                                MainTab.PRODUCTS -> ProductsScreen(
                                    products = products,
                                    user = currentSession,
                                    onAddProduct = viewModel::addProduct,
                                    onUpdateProduct = viewModel::updateProduct,
                                    onDeleteProduct = viewModel::deleteProduct
                                )

                                MainTab.ADMIN -> AdminPanelScreen(
                                    upiSettings = upi,
                                    accounts = accounts,
                                    onSaveUpi = viewModel::saveUpiSettings,
                                    onClearUpi = viewModel::clearUpiId,
                                    onChangeFinalCode = viewModel::changeFinalCode,
                                    onAddStaff = viewModel::addStaff,
                                    onRemoveStaff = viewModel::removeStaff
                                )

                                MainTab.SETTINGS -> SettingsScreen(
                                    settings = company,
                                    user = currentSession,
                                    onSaveSettings = viewModel::saveCompanySettings,
                                    onPickFallbackQrBitmap = viewModel::saveFallbackQrBitmap,
                                    onExportBackup = viewModel::exportBackupJson,
                                    onImportBackup = viewModel::importBackupJson
                                )
                            }
                        }
                    }
                }
            }
        }

        // Non-blocking Animated Splash Overlay
        AnimatedVisibility(
            visible = splashVisible,
            enter = fadeIn(tween(150)),
            exit = fadeOut(tween(320))
        ) {
            SplashOverlay(onSkip = viewModel::dismissSplash)
        }
    }
    }
}

@Composable
private fun TseTopBar(
    company: CompanySettings,
    session: UserSession,
    soundEnabled: Boolean,
    language: AppLanguage,
    onToggleSound: () -> Unit,
    onToggleLanguage: () -> Unit,
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
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TseShieldLogo(modifier = Modifier.size(40.dp))
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
                        text = if (session.isAdmin) {
                            "${session.displayName} (Admin)"
                        } else {
                            "${session.displayName} (Staff · Bill Only)"
                        },
                        color = GoldSoft,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onToggleSound,
                    modifier = Modifier.testTag("sound_toggle_button")
                ) {
                    Icon(
                        imageVector = if (soundEnabled) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                        contentDescription = if (soundEnabled) "Mute sound" else "Enable sound",
                        tint = if (soundEnabled) CyanGlow else Color.White.copy(alpha = 0.6f)
                    )
                }

                TextButton(
                    onClick = onToggleLanguage,
                    modifier = Modifier.testTag("language_toggle_button")
                ) {
                    Icon(Icons.Default.Language, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(3.dp))
                    Text(
                        text = if (language == AppLanguage.EN) "EN" else "हि",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
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
        HorizontalDivider(thickness = 3.dp, color = GoldAccent)
    }
}
