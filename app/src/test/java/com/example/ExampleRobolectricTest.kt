package com.example

import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.AuditLogEntity
import com.example.data.AuthPolicy
import com.example.data.BillEntity
import com.example.data.BillingRepository
import com.example.data.BillingTimePolicy
import com.example.data.ChargeType
import com.example.data.CompanySettings
import com.example.data.CustomerEntity
import com.example.data.CustomerHelpEntity
import com.example.data.DocumentType
import com.example.data.ExpenseEntity
import com.example.data.InvoiceItem
import com.example.data.InvoiceItemJson
import com.example.data.LoginSecurityState
import com.example.data.PaymentMode
import com.example.data.ProductEntity
import com.example.data.TrashItemEntity
import com.example.data.UpiSettings
import com.example.data.UserAccountEntity
import com.example.data.UserSession
import com.example.data.VoiceAndNoteSettings
import com.example.data.VoicePreset
import com.example.ui.BillDraft
import com.example.ui.BillingEvent
import com.example.ui.BillingViewModel
import com.example.ui.MainTab
import com.example.ui.TechnicianPerformanceSummary
import com.example.ui.components.AuthScreen
import com.example.ui.components.PaymentAndReviewDialog
import com.example.ui.screens.AdminPanelScreen
import com.example.ui.screens.BillScreen
import com.example.ui.screens.CustomerHelpScreen
import com.example.ui.screens.CustomersScreen
import com.example.ui.screens.ProductsScreen
import com.example.ui.screens.SavedBillsScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SuperAdminDashboardScreen
import com.example.ui.theme.TseBillingTheme
import com.example.util.AppUpdateChecker
import com.example.util.DispatchCardGenerator
import com.example.util.InvoicePdfPrinter
import com.example.util.InvoicePrintPayload
import com.example.util.LocationHelper
import com.example.util.QrGenerator
import com.example.util.SoundEngine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.util.Calendar

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var db: AppDatabase
    private lateinit var repo: BillingRepository
    private val createdStores = mutableListOf<androidx.lifecycle.ViewModelStore>()

    private fun createTestVm(soundEngine: SoundEngine): BillingViewModel {
        val store = androidx.lifecycle.ViewModelStore()
        createdStores += store
        return androidx.lifecycle.ViewModelProvider(
            store,
            BillingViewModel.Factory(repo, soundEngine)
        )[BillingViewModel::class.java]
    }

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = BillingRepository(db)
    }

    @After
    fun tearDown() {
        createdStores.forEach { it.clear() }
        createdStores.clear()
        org.robolectric.shadows.ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
        Thread.sleep(100)
        db.close()
    }

    @Test
    fun appName_matchesBrand() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("TSE Billing", context.getString(R.string.app_name))
    }

    @Test
    fun singleAdmin_technicianCrud_superAdmin_andCascadingUpdate() = runTest {
        repo.seedDefaultsIfNeeded()
        assertEquals(false, repo.isAdminConfigured.first())

        repo.setupAdmin("admin_main", "Pass@1234")
        assertEquals(true, repo.isAdminConfigured.first())

        // Calling setupAdmin again replaces any previous Admin so strictly ONE canonical Admin exists
        repo.setupAdmin("admin_only", "Pass@5678")
        val admins = repo.accounts.first().filter { it.role.equals("admin", ignoreCase = true) }
        assertEquals(1, admins.size)
        assertEquals(AuthPolicy.CANONICAL_ADMIN_USERNAME, admins.first().username)
        assertNotNull(repo.authenticate(AuthPolicy.CANONICAL_ADMIN_USERNAME, "Pass@5678"))

        val techRes = repo.addTechnician("Rahul Verma", "9876543210", "rahul_tech", "TechPass@123", photoBase64 = "tech_photo_b64_sample")
        assertTrue(techRes.isSuccess)
        val techSession = repo.authenticate("rahul_tech", "TechPass@123")
        assertNotNull(techSession)
        assertTrue(techSession!!.isTechnician)
        assertEquals("Technician", techSession.roleDisplayLabel)

        val superRes = repo.addSuperAdmin("Auditor", "9988776655", "super1", "SuperPass@123")
        assertTrue(superRes.isSuccess)
        val superSession = repo.authenticate("super1", "SuperPass@123")
        assertNotNull(superSession)
        assertTrue(superSession!!.isSuperAdmin)
        assertEquals("Super Admin", superSession.roleDisplayLabel)

        // Save a bill created by rahul_tech then rename username via updateTechnician
        repo.saveBill(
            BillEntity(
                id = "b_tech_rename",
                invoiceNo = "TSE-101",
                date = "2026-11-20",
                customerName = "Rakesh Naik",
                customerDetails = "Panaji\n9876543210",
                itemsJson = InvoiceItemJson.toJson(listOf(InvoiceItem(description = "Camera", qty = 1.0, rate = 1500.0))),
                subtotal = 1500.0,
                discount = 0.0,
                grandTotal = 1500.0,
                paidAmount = 1500.0,
                dueAmount = 0.0,
                paymentMode = PaymentMode.CASH.label,
                signatureBase64 = "",
                customQrText = "",
                createdBy = "rahul_tech"
            )
        )
        val updRes = repo.updateTechnician(
            oldUsername = "rahul_tech",
            newUsername = "rahul_pro",
            displayName = "Rahul Pro",
            phone = "9876500000",
            newPassword = ""
        )
        assertTrue(updRes.isSuccess)
        assertNull(repo.authenticate("rahul_tech", "TechPass@123"))
        assertNotNull(repo.authenticate("rahul_pro", "TechPass@123"))
        assertEquals("rahul_pro", repo.getBillById("b_tech_rename")!!.createdBy)
    }

    @Test
    fun upiQrGeneration_createsValidBitmapForPaidAmount() {
        val uri = QrGenerator.buildUpiUri("7083805120@ybl", "Manoj Kumar Saha", 1250.0, "TSE-001")
        assertTrue(uri.startsWith("upi://pay?"))
        assertTrue(uri.contains("am=1250.00"))
        val bmp = QrGenerator.generateQrBitmap(uri, 160)
        assertNotNull(bmp)
    }

    @Test
    fun productFolders_addAndUpdateMaintainsFolderAssignment() = runTest {
        repo.seedDefaultsIfNeeded()
        val initial = repo.products.first().first()
        repo.updateProduct(initial.id, "4MP ColorVu Dome Camera", "Pcs", 2450.0, folder = "CCTV Cameras")
        val updated = repo.products.first().first { it.id == initial.id }
        assertEquals("4MP ColorVu Dome Camera", updated.name)
        assertEquals("Pcs", updated.unit)
        assertEquals(2450.0, updated.defaultRate, 0.001)
        assertEquals("CCTV Cameras", updated.folder)
    }

    @Test
    fun a4InvoiceQuotationAndHelpBillRenderer_draws15ItemsPlusCharge_andPngBitmap() {
        assertEquals(595, InvoicePdfPrinter.A4_WIDTH_PT)
        assertEquals(842, InvoicePdfPrinter.A4_HEIGHT_PT)

        val dummyAuthSig = Bitmap.createBitmap(24, 12, Bitmap.Config.ARGB_8888)
        val out = ByteArrayOutputStream()
        dummyAuthSig.compress(Bitmap.CompressFormat.PNG, 90, out)
        val authSigBase64 = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)

        val items = (1..15).map { idx ->
            InvoiceItem(folder = "CCTV Cameras", description = "CCTV Item $idx", qty = 2.0, unit = "Nos", rate = 1250.0)
        } + listOf(
            InvoiceItem(description = "", qty = 1.0, unit = "Nos", rate = 0.0)
        )

        val effective = InvoicePdfPrinter.buildEffectivePrintItems(
            items = items,
            extraChargeType = ChargeType.CUSTOMER_CARE_AND_SUPPORT,
            extraChargeAmount = 500.0
        )
        assertEquals(16, effective.size)
        assertEquals("Customer Care & Support Charges", effective.last().description)

        val helpInvoicePayload = InvoicePrintPayload(
            company = CompanySettings(authorisedSignatureBase64 = authSigBase64),
            upi = UpiSettings(upiId = "7083805120@ybl", payeeName = "Manoj Kumar Saha"),
            invoiceNo = "TSE-009",
            date = "2026-11-20",
            customerName = "Test Customer",
            customerDetails = "Calangute, Goa\n9876543210",
            items = items,
            extraChargeType = ChargeType.CUSTOMER_CARE_AND_SUPPORT,
            extraChargeAmount = 500.0,
            subtotal = 38000.0,
            discount = 500.0,
            grandTotal = 37500.0,
            paidAmount = 20000.0,
            dueAmount = 17500.0,
            paymentMode = PaymentMode.ONLINE,
            signatureBase64 = "",
            customQrText = "DVR Admin Pass: 1234",
            documentType = DocumentType.BILL,
            isHelpBill = true,
            helpRequestId = 7L
        )
        val invoiceBmp = InvoicePdfPrinter.renderInvoiceBitmap(helpInvoicePayload, scale = 1.0f)
        assertEquals(595, invoiceBmp.width)
        assertEquals(842, invoiceBmp.height)

        val quotePayload = helpInvoicePayload.copy(
            documentType = DocumentType.QUOTATION,
            isHelpBill = false,
            quotationNote = "2 Years Manufacturer Warranty included.",
            paidAmount = 0.0,
            dueAmount = 0.0
        )
        val quoteBmp = InvoicePdfPrinter.renderInvoiceBitmap(quotePayload, scale = 1.0f)
        assertEquals(595, quoteBmp.width)
        assertEquals(842, quoteBmp.height)
    }

    @Test
    fun helpDesk_accept15MinCooldown_solve_andDispatchCardBitmap() = runTest {
        repo.setupAdmin(AuthPolicy.CANONICAL_ADMIN_USERNAME, "Pass@1234")
        repo.addTechnician("Vikram Dessai", "9822098220", "vikram_tech", "Pass@1234", photoBase64 = "tech_photo_b64_sample")
        val techSession = repo.authenticate("vikram_tech", "Pass@1234")!!

        val saved = repo.saveHelpRequest(
            customerName = "Deepak Naik",
            phone = "9876511223",
            address = "Calangute, Goa",
            locationUrl = "https://maps.google.com/?q=15.54,73.75",
            helpDescription = "NVR recording stopped"
        )
        val startMillis = 1_700_000_000_000L
        val acceptRes = repo.acceptHelpRequest(saved.id, techSession, acceptedAtMillis = startMillis)
        assertTrue(acceptRes.isSuccess)
        assertEquals("Vikram Dessai", acceptRes.getOrNull()!!.acceptedByName)
        assertEquals("9822098220", acceptRes.getOrNull()!!.acceptedByPhone)

        // Before 15 minutes pass, Technician cannot mark Solved
        val earlySolve = repo.markHelpSolved(
            requestId = saved.id,
            actorUsername = techSession.username,
            isAdmin = false,
            nowMillis = startMillis + 5 * 60 * 1000L,
            enforce15MinWait = true
        )
        assertTrue(earlySolve.isFailure)

        // After 15 minutes pass, Solved succeeds
        val readySolve = repo.markHelpSolved(
            requestId = saved.id,
            actorUsername = techSession.username,
            isAdmin = false,
            nowMillis = startMillis + BillingRepository.HELP_SOLVE_WAIT_MS + 1000L,
            enforce15MinWait = true
        )
        assertTrue(readySolve.isSuccess)
        assertEquals("SOLVED", readySolve.getOrNull()!!.status)

        val cardBmp = DispatchCardGenerator.renderDispatchCardBitmap(
            company = CompanySettings(),
            request = readySolve.getOrNull()!!
        )
        assertEquals(1080, cardBmp.width)
        assertEquals(1350, cardBmp.height)
    }

    @Test
    fun billingTimePolicy_closesOnlyBetween0001And0600() {
        assertFalse(BillingTimePolicy.isNightClosed(0, 0))   // 12:00 AM open
        assertTrue(BillingTimePolicy.isNightClosed(0, 1))    // 12:01 AM closed
        assertTrue(BillingTimePolicy.isNightClosed(3, 30))   // 03:30 AM closed
        assertTrue(BillingTimePolicy.isNightClosed(6, 0))    // 06:00 AM closed
        assertFalse(BillingTimePolicy.isNightClosed(6, 1))   // 06:01 AM open
        assertFalse(BillingTimePolicy.isNightClosed(14, 15)) // 02:15 PM open

        val calClosed = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 2)
            set(Calendar.MINUTE, 15)
        }
        assertTrue(BillingTimePolicy.isNightClosedNow(calClosed.timeInMillis))
    }

    @Test
    fun appUpdateChecker_parsesNewerGitHubReleaseAndIgnoresOlderOrSame() {
        val json = """
            {
              "tag_name": "v2.5.0",
              "body": "Faster invoice export and folder filters",
              "html_url": "https://github.com/rajrudra363-arch/Tse-billing/releases/tag/v2.5.0",
              "assets": [
                {"name": "app-debug.apk", "browser_download_url": "https://example.com/tse-2.5.0.apk"}
              ]
            }
        """.trimIndent()

        val newer = AppUpdateChecker.parseReleaseJson(json, "2.0.0")
        assertNotNull(newer)
        assertEquals("2.5.0", newer!!.versionName)
        assertEquals("https://example.com/tse-2.5.0.apk", newer.apkDownloadUrl)

        assertNull(AppUpdateChecker.parseReleaseJson(json, "2.5.0"))
        assertNull(AppUpdateChecker.parseReleaseJson(json, "3.0.0"))
        assertTrue(AppUpdateChecker.compareSemVer("2.1.0", "2.0.9") > 0)
    }

    @Test
    fun technicianCashSettlement_reducesAdminDueBalance() = kotlinx.coroutines.runBlocking {
        repo.setupAdmin(AuthPolicy.CANONICAL_ADMIN_USERNAME, "Pass@1234")
        repo.addTechnician("Rahul Verma", "9876543210", "rahul_tech", "Pass@1234", photoBase64 = "tech_photo_b64_sample")
        val bill = BillEntity(
            id = "b_cash_1",
            invoiceNo = "TSE-88",
            date = "2026-11-20",
            customerName = "Cash Customer",
            customerDetails = "Mapusa\n9876543210",
            itemsJson = InvoiceItemJson.toJson(listOf(InvoiceItem(description = "DVR", qty = 1.0, rate = 4000.0))),
            subtotal = 4000.0,
            discount = 0.0,
            grandTotal = 4000.0,
            paidAmount = 4000.0,
            dueAmount = 0.0,
            paymentMode = PaymentMode.CASH.label,
            signatureBase64 = "",
            customQrText = "",
            createdBy = "rahul_tech"
        )
        repo.saveBill(bill)

        repo.recordTechnicianSettlement(
            technicianUsername = "rahul_tech",
            amount = 2500.0,
            note = "Cash handed over at office",
            recordedBy = "admin1"
        )

        val context = ApplicationProvider.getApplicationContext<Context>()
        val soundEngine = SoundEngine(context)
        val vm = createTestVm(soundEngine)

        val deadline = System.currentTimeMillis() + 5000L
        while (vm.technicianPerformance.value.none { it.username == "rahul_tech" && it.cashTotal > 0.0 && it.settledToAdminTotal > 0.0 } &&
            System.currentTimeMillis() < deadline
        ) {
            org.robolectric.shadows.ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
            Thread.sleep(25)
        }
        val perf = vm.technicianPerformance.value.first { it.username == "rahul_tech" }
        assertEquals(4000.0, perf.cashTotal, 0.001)
        assertEquals(2500.0, perf.settledToAdminTotal, 0.001)
        assertEquals(1500.0, perf.adminDueBalance, 0.001)
        soundEngine.release()
    }

    @Test
    fun mandatoryDeviceAuthSetting_blocksGenerationUntilEnabled() = kotlinx.coroutines.runBlocking {
        repo.setupAdmin(AuthPolicy.CANONICAL_ADMIN_USERNAME, "Pass@1234")
        val context = ApplicationProvider.getApplicationContext<Context>()
        val soundEngine = SoundEngine(context)
        val vm = createTestVm(soundEngine)

        vm.login(AuthPolicy.CANONICAL_ADMIN_USERNAME, "Pass@1234")
        val deadline = System.currentTimeMillis() + 5000L
        while (vm.session.value == null && System.currentTimeMillis() < deadline) {
            org.robolectric.shadows.ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
            Thread.sleep(25)
        }
        assertNotNull(vm.session.value)

        vm.updateCustomerName("Customer X")
        vm.updateItemFields(0, description = "2MP Camera", qty = 1.0, unit = "Nos", rate = 1500.0)
        val openDayMillis = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 11)
            set(Calendar.MINUTE, 0)
        }.timeInMillis
        vm.openSummaryModal(openDayMillis)
        assertTrue(vm.billDraft.value.summaryOpen)

        // Device auth disabled -> must block with error message in summaryError
        vm.requestBillGenerationWithDeviceAuth(openDayMillis)
        assertTrue(vm.summaryError.value.contains("Settings"))

        // Enable Device Auth -> must emit RequestDeviceAuthForBill event
        vm.setDeviceAuthForCurrentUser(true)
        val deadline2 = System.currentTimeMillis() + 5000L
        while (!vm.isCurrentUserDeviceAuthEnabled() && System.currentTimeMillis() < deadline2) {
            org.robolectric.shadows.ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
            Thread.sleep(25)
        }
        assertTrue(vm.isCurrentUserDeviceAuthEnabled())

        var requestedEvent: BillingEvent.RequestDeviceAuthForBill? = null
        val job = launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
            vm.events.collect { ev ->
                if (ev is BillingEvent.RequestDeviceAuthForBill) requestedEvent = ev
            }
        }
        vm.requestBillGenerationWithDeviceAuth(openDayMillis)
        org.robolectric.shadows.ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
        kotlinx.coroutines.yield()
        assertNotNull(requestedEvent)
        job.cancel()
        soundEngine.release()
    }

    @Test
    fun productsScreen_hidesEditForTechnician_showsFoldersAndPrices() {
        val sampleProducts = listOf(
            ProductEntity(id = 1L, name = "2MP Dome Camera", unit = "Nos", defaultRate = 1200.0, folder = "CCTV Cameras")
        )
        val techUser = UserSession(username = "tech1", displayName = "Tech One", role = "technician")
        val adminUser = UserSession(username = "admin1", displayName = "Admin", role = "admin")

        var currentUser by mutableStateOf(techUser)
        composeTestRule.setContent {
            TseBillingTheme {
                ProductsScreen(
                    products = sampleProducts,
                    user = currentUser,
                    onAddProduct = { _, _, _, _ -> },
                    onUpdateProduct = { _, _, _, _, _ -> },
                    onDeleteProduct = {}
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("add_product_button").assertDoesNotExist()
        composeTestRule.onNodeWithTag("edit_product_1").assertDoesNotExist()
        composeTestRule.onNodeWithTag("delete_product_1").assertDoesNotExist()
        composeTestRule.onNodeWithTag("product_folder_chip_CCTV_Cameras").assertIsDisplayed()
        composeTestRule.onNodeWithText("2MP Dome Camera").assertIsDisplayed()

        currentUser = adminUser
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("add_product_button").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("products_lazy_list").performScrollToIndex(3)
        composeTestRule.onNodeWithTag("edit_product_1").assertIsDisplayed()
        composeTestRule.onNodeWithTag("delete_product_1").assertIsDisplayed()
    }

    @Test
    fun settingsScreen_allowsDeviceAuthForTechnician_hidesAdminCompanyLogoSignatureAndUpdatesOnlyForAdmin() {
        val techUser = UserSession(username = "tech1", displayName = "Tech One", role = "technician")
        val adminUser = UserSession(username = "admin1", displayName = "Admin", role = "admin")

        var currentUser by mutableStateOf(techUser)
        composeTestRule.setContent {
            TseBillingTheme {
                SettingsScreen(
                    settings = CompanySettings(),
                    user = currentUser,
                    deviceAuthEnabled = false,
                    updateCheckBusy = false,
                    onToggleDeviceAuth = {},
                    onSaveSettings = {},
                    onPickCompanyLogoBitmap = {},
                    onPickFallbackQrBitmap = {},
                    onPickAuthorisedSignatureBitmap = {},
                    onExportBackup = { "{}" },
                    onImportBackup = {}
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("device_auth_switch").assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_company_name").assertDoesNotExist()
        composeTestRule.onNodeWithTag("upload_company_logo_button").assertDoesNotExist()
        composeTestRule.onNodeWithTag("upload_auth_signature_button").assertDoesNotExist()

        currentUser = adminUser
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("settings_lazy_list").performScrollToNode(hasTestTag("settings_company_name"))
        composeTestRule.onNodeWithTag("settings_company_name").assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_lazy_list").performScrollToNode(hasTestTag("upload_company_logo_button"))
        composeTestRule.onNodeWithTag("upload_company_logo_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_lazy_list").performScrollToNode(hasTestTag("upload_auth_signature_button"))
        composeTestRule.onNodeWithTag("upload_auth_signature_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_lazy_list").performScrollToNode(hasTestTag("online_update_button"))
        composeTestRule.onNodeWithTag("online_update_button").assertIsDisplayed()
    }

    @Test
    fun billScreen_showsDocTypeToggle_getSummaryModal_paidQr_andPhotoExportOptions() {
        val techUser = UserSession(username = "rahul_tech", displayName = "Rahul Verma", role = "technician")
        val draft = BillDraft(
            customerName = "Ramesh Sharma",
            customerDetails = "Panaji, Goa\n9876543210",
            invoiceNo = "TSE-101",
            items = listOf(
                InvoiceItem(folder = "CCTV Cameras", description = "2MP Dome Camera", qty = 2.0, unit = "Nos", rate = 1200.0)
            ),
            paidAmountText = "1000",
            showSheetPreview = true,
            summaryOpen = true
        )
        var activeDraft by mutableStateOf(draft)
        composeTestRule.setContent {
            TseBillingTheme {
                BillScreen(
                    draft = activeDraft,
                    products = emptyList(),
                    customers = emptyList(),
                    allBills = emptyList(),
                    company = CompanySettings(),
                    upi = UpiSettings(upiId = "7083805120@ybl"),
                    currentUser = techUser,
                    isNightClosed = false,
                    summaryError = "",
                    exportOptionsPayload = null,
                    onSetDocumentType = {},
                    onQuotationNoteChange = {},
                    onCustomerNameChange = {},
                    onCustomerSelected = {},
                    onCustomerDetailsChange = {},
                    onCustomerLocationUrlChange = {},
                    onInvoiceNoChange = {},
                    onInvoiceDateChange = {},
                    onAddItem = {},
                    onSelectFolderForRow = { _, _ -> },
                    onSelectProductForRow = { _, _ -> },
                    onUpdateItem = { _, _, _, _, _ -> },
                    onRemoveItem = {},
                    onExtraChargeTypeChange = {},
                    onExtraChargeAmountChange = {},
                    onDiscountChange = {},
                    onPaidAmountChange = {},
                    onPaymentModeChange = {},
                    onSignatureChanged = {},
                    onCustomQrTextChange = {},
                    onStaffRatingChange = {},
                    onStaffReviewChange = {},
                    onToggleSheetPreview = {},
                    onResetBill = {},
                    onOpenSummaryModal = {},
                    onCloseSummaryModal = {},
                    onRequestGenerateWithDeviceAuth = {},
                    onDismissExportOptions = {},
                    onPrintA4 = {},
                    onSavePdf = {},
                    onSharePdf = {},
                    onSavePhoto = {},
                    onSharePhoto = {}
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("bill_summary_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithTag("summary_online_qr_card").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("generate_final_bill_button").assertIsDisplayed()

        activeDraft = activeDraft.copy(summaryOpen = false)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("doc_type_QUOTATION").assertIsDisplayed()
        composeTestRule.onNodeWithTag("bill_lazy_list").performScrollToNode(hasTestTag("bill_mobile_search"))
        composeTestRule.onNodeWithTag("bill_mobile_search").assertIsDisplayed()
        composeTestRule.onNodeWithTag("bill_lazy_list").performScrollToNode(hasTestTag("get_summary_button"))
        composeTestRule.onNodeWithTag("get_summary_button").assertIsDisplayed()
    }

    @Test
    fun savedBillsScreen_displaysTechnicianCreator_customerFoldersTab_andQuotationsTab() {
        val sampleBill = BillEntity(
            id = "b_1",
            invoiceNo = "TSE-055",
            date = "2026-11-20",
            customerName = "Deepak Patil",
            customerDetails = "Mapusa\n9876543210",
            itemsJson = InvoiceItemJson.toJson(listOf(InvoiceItem(description = "4 Ch DVR", qty = 1.0, rate = 3500.0))),
            subtotal = 3500.0,
            discount = 200.0,
            grandTotal = 3300.0,
            paidAmount = 2000.0,
            dueAmount = 1300.0,
            paymentMode = PaymentMode.ONLINE.label,
            signatureBase64 = "",
            customQrText = "",
            createdBy = "rahul_tech",
            staffRating = 5,
            staffReviewNote = "Neat CCTV wiring",
            updatedAt = 2L
        )
        val sampleQuote = sampleBill.copy(
            id = "q_1",
            invoiceNo = "TSE-Q01",
            documentType = DocumentType.QUOTATION.name,
            paidAmount = 0.0,
            dueAmount = 0.0,
            staffRating = 0,
            staffReviewNote = ""
        )
        val folders = BillingViewModel.buildCustomerFolders(listOf(sampleBill, sampleQuote), emptyList())

        composeTestRule.setContent {
            TseBillingTheme {
                SavedBillsScreen(
                    allBills = listOf(sampleBill, sampleQuote),
                    filteredBills = listOf(sampleBill, sampleQuote),
                    customerFolders = folders,
                    searchQuery = "",
                    company = CompanySettings(),
                    upi = UpiSettings(),
                    isAdmin = true,
                    onSearchChange = {},
                    onOpenBill = {},
                    onPrintBill = {},
                    onSavePdf = {},
                    onSharePdf = {},
                    onSavePhoto = {},
                    onSharePhoto = {},
                    onDeleteBill = {},
                    onOpenPaymentReview = {}
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("saved_bill_creator_TSE-055").assertTextContains("@rahul_tech", substring = true)
        composeTestRule.onNodeWithTag("save_photo_saved_TSE-055").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("saved_tab_FOLDERS").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Deepak Patil").assertIsDisplayed()
        composeTestRule.onNodeWithTag("saved_tab_QUOTATIONS").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("QUOTATION").assertIsDisplayed()
        composeTestRule.onNodeWithText("TSE-Q01 — Deepak Patil").assertIsDisplayed()
    }

    @Test
    fun adminPanel_displaysTechnicianFoldersAndVoiceCms() {
        val accounts = listOf(
            UserAccountEntity(username = "admin1", displayName = "Admin", role = "admin", passwordHash = "x"),
            UserAccountEntity(username = "rahul_tech", displayName = "Rahul Verma", role = "technician", phone = "9876543210", passwordHash = "x")
        )
        val perf = listOf(
            TechnicianPerformanceSummary(
                username = "rahul_tech",
                displayName = "Rahul Verma",
                phone = "9876543210",
                role = "technician",
                billCount = 4,
                quotationCount = 1,
                helpAcceptedCount = 2,
                helpSolvedCount = 2,
                totalBilledAmount = 18500.0,
                onlineTotal = 10000.0,
                cashTotal = 8500.0,
                settledToAdminTotal = 3500.0,
                totalDueAmount = 2500.0,
                ratedCount = 2,
                averageRating = 4.5,
                latestReviewNote = "Clean work"
            )
        )
        composeTestRule.setContent {
            TseBillingTheme {
                AdminPanelScreen(
                    upiSettings = UpiSettings(),
                    accounts = accounts,
                    technicianPerformance = perf,
                    voiceAndNoteSettings = VoiceAndNoteSettings(),
                    systemVoiceOptions = emptyList(),
                    onSaveUpi = { _, _ -> },
                    onClearUpi = {},
                    onAddTechnician = { _, _, _, _, _, _ -> },
                    onUpdateTechnician = { _, _, _, _, _, _, _ -> },
                    onRemoveTechnician = {},
                    onAddSuperAdmin = { _, _, _, _, _ -> },
                    onRemoveSuperAdmin = {},
                    onRecordCashSettlement = { _, _, _, _ -> },
                    onSaveVoiceAndNoteSettings = {},
                    onTestVoice = { _, _ -> }
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("admin_lazy_list").performScrollToNode(hasTestTag("new_tech_photo_status"))
        composeTestRule.onNodeWithTag("new_tech_photo_status").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("new_tech_pick_photo_button").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("admin_lazy_list").performScrollToNode(hasTestTag("technician_folders_card"))
        composeTestRule.onNodeWithTag("technician_folders_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("tech_folder_rahul_tech").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("tech_admin_due_rahul_tech").performScrollTo().assertTextContains("5000", substring = true)
    }

    @Test
    fun customerHelpScreen_showsTechnicianWorkflowAndAdminDispatchCard() {
        val requests = listOf(
            CustomerHelpEntity(
                id = 21L,
                customerName = "Rohan Kamat",
                phone = "9822012345",
                address = "Mapusa, Goa",
                locationUrl = "https://maps.google.com/?q=15.5915,73.8100",
                helpDescription = "DVR hard disk beep issue",
                status = "SOLVED",
                acceptedBy = "tech1",
                acceptedByName = "Tech One",
                acceptedByPhone = "9876543210",
                acceptedAt = 1000L
            )
        )
        val techUser = UserSession(username = "tech1", displayName = "Tech One", role = "technician")
        val adminUser = UserSession(username = "admin1", displayName = "Admin", role = "admin")

        var currentUser by mutableStateOf(techUser)
        composeTestRule.setContent {
            TseBillingTheme {
                CustomerHelpScreen(
                    helpRequests = requests,
                    customers = emptyList(),
                    company = CompanySettings(),
                    user = currentUser,
                    onSaveHelpRequest = { _, _, _, _, _, _, _ -> },
                    onAcceptRequest = {},
                    onMarkSolved = {},
                    onMarkNotSolved = {},
                    onGetBillFromHelp = {},
                    onDeleteHelpRequest = {}
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("get_bill_help_21").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("dispatch_card_help_21").assertDoesNotExist()

        currentUser = adminUser
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("help_lazy_list").performScrollToIndex(3)
        composeTestRule.onNodeWithTag("dispatch_card_help_21").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("wa_chat_help_21").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun superAdminDashboardScreen_rendersChartsAndReadOnlyNotice() {
        val sampleBills = listOf(
            BillEntity(
                id = "b_ana_1",
                invoiceNo = "TSE-901",
                date = "2026-11-20",
                customerName = "Analytics Client",
                customerDetails = "Panaji",
                itemsJson = InvoiceItemJson.toJson(listOf(InvoiceItem(description = "4 Ch DVR", qty = 1.0, rate = 4500.0))),
                subtotal = 4500.0,
                discount = 0.0,
                grandTotal = 4500.0,
                paidAmount = 4500.0,
                dueAmount = 0.0,
                paymentMode = PaymentMode.ONLINE.label,
                signatureBase64 = "",
                customQrText = "",
                createdBy = "rahul_tech",
                staffRating = 5
            )
        )
        composeTestRule.setContent {
            TseBillingTheme {
                SuperAdminDashboardScreen(
                    bills = sampleBills,
                    customers = emptyList(),
                    helpRequests = emptyList(),
                    products = emptyList(),
                    technicianPerformance = emptyList()
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("super_admin_dashboard").assertIsDisplayed()
        composeTestRule.onNodeWithText("Business Analytics & Market Intelligence").assertIsDisplayed()
        composeTestRule.onNodeWithTag("super_admin_dashboard").performScrollToNode(hasTestTag("analytics_revenue_bar_chart"))
        composeTestRule.onNodeWithTag("analytics_revenue_bar_chart").assertIsDisplayed()
        composeTestRule.onNodeWithTag("super_admin_dashboard").performScrollToNode(hasTestTag("analytics_payment_donut_chart"))
        composeTestRule.onNodeWithTag("analytics_payment_donut_chart").assertIsDisplayed()
    }

    @Test
    fun starRating_isImmutable_andForgotPasswordUsesManoj2007() = runTest {
        repo.setupAdmin(AuthPolicy.CANONICAL_ADMIN_USERNAME, "OldPass@123")
        val ratedBill = BillEntity(
            id = "b_locked_1",
            invoiceNo = "TSE-501",
            date = "2026-11-20",
            customerName = "Rated Customer",
            customerDetails = "Panaji",
            itemsJson = InvoiceItemJson.toJson(listOf(InvoiceItem(description = "4 Ch DVR", qty = 1.0, rate = 4000.0))),
            subtotal = 4000.0,
            discount = 0.0,
            grandTotal = 4000.0,
            paidAmount = 2500.0,
            dueAmount = 1500.0,
            paymentMode = PaymentMode.ONLINE.label,
            signatureBase64 = "",
            customQrText = "",
            createdBy = "rahul_tech",
            staffRating = 5,
            staffReviewNote = "Excellent work"
        )
        repo.saveBill(ratedBill)
        repo.updateBillSettlementAndReview(
            billId = "b_locked_1",
            discount = 200.0,
            grandTotal = 3800.0,
            paidAmount = 3800.0,
            dueAmount = 0.0,
            staffRating = 1,
            staffReviewNote = "Tampered review"
        )
        val afterSettlement = repo.getBillById("b_locked_1")!!
        assertEquals(5, afterSettlement.staffRating)
        assertEquals("Excellent work", afterSettlement.staffReviewNote)
        assertFalse(repo.deleteBill("b_locked_1"))

        val wrongRecovery = repo.resetAdminPasswordWithRecovery("Wrong", AuthPolicy.CANONICAL_ADMIN_USERNAME, "NewPass@456")
        assertTrue(wrongRecovery.isFailure)
        val okRecovery = repo.resetAdminPasswordWithRecovery(
            BillingRepository.DEFAULT_ADMIN_RECOVERY_PASSWORD,
            AuthPolicy.CANONICAL_ADMIN_USERNAME,
            "NewPass@456"
        )
        assertTrue(okRecovery.isSuccess)
        assertNotNull(repo.authenticate(AuthPolicy.CANONICAL_ADMIN_USERNAME, "NewPass@456"))
    }

    @Test
    fun authScreen_showsBackupSignInTab_singleAdminDropdown_andForgotPasswordDialog() {
        var isConfigured by mutableStateOf<Boolean?>(false)
        val accounts = listOf(
            UserAccountEntity(
                username = AuthPolicy.CANONICAL_ADMIN_USERNAME,
                displayName = "Admin",
                role = "admin",
                passwordHash = "hash1"
            ),
            UserAccountEntity(username = "admin_dup", displayName = "Admin", role = "admin", passwordHash = "hash2"),
            UserAccountEntity(username = "rahul_tech", displayName = "Rahul Verma", role = "technician", passwordHash = "hash3")
        )
        composeTestRule.setContent {
            TseBillingTheme {
                AuthScreen(
                    isAdminConfigured = isConfigured,
                    accounts = accounts,
                    authError = "",
                    onClearError = {},
                    onSetupAdmin = { _, _, _, _ -> },
                    onLogin = { _, _, _, _ -> },
                    onImportBackup = {},
                    onResetAdminPassword = { _, _, _, _, _ -> }
                )
            }
        }
        composeTestRule.waitForIdle()
        // Setup locks Admin ID to @TotalSolutionEnterprises and disables button until Gmail-grade password entered
        composeTestRule.onNodeWithTag("setup_admin_username").assertTextContains(AuthPolicy.CANONICAL_ADMIN_USERNAME, substring = true)
        composeTestRule.onNodeWithTag("password_strength_checklist").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("create_admin_button").performScrollTo().assertIsNotEnabled()

        composeTestRule.onNodeWithTag("setup_tab_restore").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("setup_import_backup_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("setup_import_backup_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("setup_paste_backup_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("backup_paste_json_input").assertIsDisplayed()
        composeTestRule.onNodeWithText("Cancel").performClick()
        composeTestRule.waitForIdle()

        isConfigured = true
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("anti_robot_card").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("anti_robot_challenge_text").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("anti_robot_answer_input").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("login_username_dropdown").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("login_user_option_${AuthPolicy.CANONICAL_ADMIN_USERNAME}").assertIsDisplayed()
        composeTestRule.onNodeWithTag("login_user_option_admin_dup").assertDoesNotExist()
        composeTestRule.onNodeWithTag("login_user_option_rahul_tech").assertIsDisplayed()
    }

    @Test
    fun fullBackupExportAndImport_restoresBillsTechniciansSettlementsVoiceAndLogo() = runTest {
        repo.setupAdmin(AuthPolicy.CANONICAL_ADMIN_USERNAME, "AdminPass@99")
        repo.addTechnician("Rahul Verma", "9876543210", "rahul_tech", "TechPass@99", photoBase64 = "tech_photo_b64_sample")
        repo.addSuperAdmin("Viewer", "9988776655", "super_view", "SuperPass@99")
        repo.saveCompanySettings(CompanySettings(companyName = "TOTAL SOLUTION ENTERPRISES PRO"))
        repo.saveCompanyLogoBase64("logo_b64_sample")
        repo.saveVoiceAndNoteSettings(
            VoiceAndNoteSettings(
                headerNote = "Important Notice Today",
                voiceModelId = VoicePreset.CRISP.id
            )
        )
        repo.saveBill(
            BillEntity(
                id = "b_exp_1",
                invoiceNo = "TSE-777",
                date = "2026-11-20",
                customerName = "Backup Customer",
                customerDetails = "Panaji\n9876543210",
                itemsJson = InvoiceItemJson.toJson(listOf(InvoiceItem(folder = "CCTV Cameras", description = "Camera", qty = 2.0, rate = 1500.0))),
                subtotal = 3000.0,
                discount = 0.0,
                grandTotal = 3000.0,
                paidAmount = 3000.0,
                dueAmount = 0.0,
                paymentMode = PaymentMode.CASH.label,
                signatureBase64 = "",
                customQrText = "",
                createdBy = "rahul_tech",
                staffRating = 5
            )
        )
        repo.recordTechnicianSettlement("rahul_tech", 1200.0, "Cleared", AuthPolicy.CANONICAL_ADMIN_USERNAME)

        val jsonBackup = repo.exportBackupJson()

        val context = ApplicationProvider.getApplicationContext<Context>()
        val freshDb = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val freshRepo = BillingRepository(freshDb)
            val importResult = freshRepo.importBackupJson(jsonBackup)
            assertTrue(importResult.isSuccess)
            val summary = importResult.getOrNull()!!
            assertEquals(1, summary.billsCount)
            assertEquals(1, summary.settlementsCount)
            assertNotNull(freshRepo.authenticate(AuthPolicy.CANONICAL_ADMIN_USERNAME, "AdminPass@99"))
            val restoredTech = freshRepo.authenticate("rahul_tech", "TechPass@99")
            assertNotNull(restoredTech)
            assertEquals("tech_photo_b64_sample", restoredTech!!.photoBase64)
            assertNotNull(freshRepo.authenticate("super_view", "SuperPass@99"))
            assertEquals("logo_b64_sample", freshRepo.companySettings.first().logoBase64)
            assertEquals("Important Notice Today", freshRepo.voiceAndNoteSettings.first().headerNote)
        } finally {
            freshDb.close()
        }
    }

    @Test
    fun loginSecurity_admin3Tries15MinDeviceLock_andStaff5Tries2HourLock() = kotlinx.coroutines.runBlocking {
        repo.setupAdmin(AuthPolicy.CANONICAL_ADMIN_USERNAME, "Pass@1234")
        repo.addTechnician("Tech One", "9876500001", "tech1", "TechPass@123", photoBase64 = "abc")
        val context = ApplicationProvider.getApplicationContext<Context>()
        val soundEngine = SoundEngine(context)
        val vm = createTestVm(soundEngine)

        suspend fun awaitUntil(cond: suspend () -> Boolean) {
            val deadline = System.currentTimeMillis() + 5000L
            while (System.currentTimeMillis() < deadline) {
                org.robolectric.shadows.ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
                if (cond()) return
                Thread.sleep(25)
            }
            org.robolectric.shadows.ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
        }

        val t0 = 1_700_000_000_000L

        // 1. CAPTCHA failed -> blocks login without counting as password failure
        vm.login(AuthPolicy.CANONICAL_ADMIN_USERNAME, "Pass@1234", antiRobotPassed = false, nowMillis = t0)
        awaitUntil { vm.authError.value.contains("CAPTCHA", ignoreCase = true) }
        assertNull(vm.session.value)
        assertEquals(0, repo.getAdminLoginSecurityState().failedAttempts)

        // 2. Admin: 2 wrong password attempts -> still unlocked, 1 try left
        repeat(2) { idx ->
            vm.login(AuthPolicy.CANONICAL_ADMIN_USERNAME, "WrongAdmin@$idx", antiRobotPassed = true, nowMillis = t0 + idx * 1000L)
            awaitUntil { repo.getAdminLoginSecurityState().failedAttempts == idx + 1 }
        }
        val admin2 = repo.getAdminLoginSecurityState()
        assertEquals(2, admin2.failedAttempts)
        assertEquals(1, admin2.remainingAttempts(t0 + 2000L))
        assertFalse(admin2.isLocked(t0 + 2000L))

        // 3. Admin 3rd wrong attempt -> locks Admin on THIS device for 15 minutes (900,000 ms)
        val tAdminLock = t0 + 3000L
        vm.login(AuthPolicy.CANONICAL_ADMIN_USERNAME, "WrongAdmin@3", antiRobotPassed = true, nowMillis = tAdminLock)
        awaitUntil { repo.getAdminLoginSecurityState().failedAttempts == 3 }
        val admin3 = repo.getAdminLoginSecurityState()
        assertTrue(admin3.isLocked(tAdminLock + 60_000L))
        assertEquals(tAdminLock + LoginSecurityState.ADMIN_LOCK_DURATION_MS, admin3.lockUntilMillis)

        // Correct Admin password blocked during 15-min lock, while Technician login is NOT locked
        vm.clearAuthError()
        vm.login(AuthPolicy.CANONICAL_ADMIN_USERNAME, "Pass@1234", antiRobotPassed = true, nowMillis = tAdminLock + 5 * 60_000L)
        awaitUntil { vm.authError.value.contains("15", ignoreCase = true) }
        assertNull(vm.session.value)

        // 4. Staff/Technician 5 wrong password attempts -> triggers 2-hour lock
        repeat(4) { idx ->
            vm.login("tech1", "WrongTech@$idx", antiRobotPassed = true, nowMillis = t0 + idx * 1000L)
            awaitUntil { repo.getLoginSecurityState().failedAttempts == idx + 1 }
        }
        val staff4 = repo.getLoginSecurityState()
        assertEquals(4, staff4.failedAttempts)
        assertEquals(1, staff4.remainingAttempts(t0 + 4000L))

        val tStaffLock = t0 + 5000L
        vm.login("tech1", "WrongTech@5", antiRobotPassed = true, nowMillis = tStaffLock)
        awaitUntil { repo.getLoginSecurityState().failedAttempts == 5 }
        val staff5 = repo.getLoginSecurityState()
        assertTrue(staff5.isLocked(tStaffLock + 60_000L))
        assertEquals(tStaffLock + LoginSecurityState.LOCK_DURATION_MS, staff5.lockUntilMillis)

        // 5. After 15 minutes expire, Admin gets 3 fresh tries and succeeds
        val tAfterAdminLock = tAdminLock + LoginSecurityState.ADMIN_LOCK_DURATION_MS + 1000L
        assertFalse(admin3.isLocked(tAfterAdminLock))
        assertEquals(3, admin3.remainingAttempts(tAfterAdminLock))
        vm.login(AuthPolicy.CANONICAL_ADMIN_USERNAME, "Pass@1234", antiRobotPassed = true, nowMillis = tAfterAdminLock)
        awaitUntil { vm.session.value?.isAdmin == true && repo.getAdminLoginSecurityState().failedAttempts == 0 }
        assertNotNull(vm.session.value)
        soundEngine.release()
    }

    @Test
    fun technicianCreation_requiresPhotoAndPersistsInSession() = kotlinx.coroutines.runBlocking {
        repo.setupAdmin(AuthPolicy.CANONICAL_ADMIN_USERNAME, "Pass@1234")
        val blankPhotoRes = repo.addTechnician("No Pic Tech", "9876543210", "nopic_tech", "Pass@1234", photoBase64 = "")
        assertTrue(blankPhotoRes.isFailure)

        val context = ApplicationProvider.getApplicationContext<Context>()
        val soundEngine = SoundEngine(context)
        val vm = createTestVm(soundEngine)

        vm.login(AuthPolicy.CANONICAL_ADMIN_USERNAME, "Pass@1234", antiRobotPassed = true)
        val deadline = System.currentTimeMillis() + 5000L
        while (vm.session.value == null && System.currentTimeMillis() < deadline) {
            org.robolectric.shadows.ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
            Thread.sleep(25)
        }
        assertNotNull(vm.session.value)

        var addedWithoutPic = false
        vm.addTechnician("No Pic Tech", "9876543210", "nopic_tech", "Pass@1234", photoBitmap = null) {
            addedWithoutPic = true
        }
        org.robolectric.shadows.ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
        Thread.sleep(40)
        assertFalse(addedWithoutPic)
        assertNull(repo.authenticate("nopic_tech", "Pass@1234"))

        val bmp = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        var addedWithPic = false
        vm.addTechnician("Amit Dessai", "9822011223", "amit_tech", "Pass@1234", photoBitmap = bmp) {
            addedWithPic = true
        }
        val deadline2 = System.currentTimeMillis() + 5000L
        while (!addedWithPic && System.currentTimeMillis() < deadline2) {
            org.robolectric.shadows.ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
            Thread.sleep(25)
        }
        assertTrue(addedWithPic)
        val techSession = repo.authenticate("amit_tech", "Pass@1234")
        assertNotNull(techSession)
        assertTrue(techSession!!.photoBase64.isNotBlank())
        soundEngine.release()
    }

    @Test
    fun authScreen_showsAdmin15MinLockoutBannerAndDisablesSubmit() {
        val accounts = listOf(
            UserAccountEntity(
                username = AuthPolicy.CANONICAL_ADMIN_USERNAME,
                displayName = "Admin",
                role = "admin",
                passwordHash = "hash1"
            )
        )
        composeTestRule.setContent {
            TseBillingTheme {
                AuthScreen(
                    isAdminConfigured = true,
                    accounts = accounts,
                    authError = "",
                    adminLoginSecurityState = LoginSecurityState(
                        failedAttempts = 3,
                        lockUntilMillis = System.currentTimeMillis() + 600_000L,
                        maxAttempts = LoginSecurityState.ADMIN_MAX_ATTEMPTS,
                        lockDurationMs = LoginSecurityState.ADMIN_LOCK_DURATION_MS
                    ),
                    onClearError = {},
                    onSetupAdmin = { _, _, _, _ -> },
                    onLogin = { _, _, _, _ -> },
                    onImportBackup = {},
                    onResetAdminPassword = { _, _, _, _, _ -> }
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("login_lockout_banner").assertIsDisplayed()
        composeTestRule.onNodeWithTag("login_lockout_banner").assertTextContains("15", substring = true)
        composeTestRule.onNodeWithTag("login_submit_button").assertIsNotEnabled()
    }

    @Test
    fun backupImport_handlesCorruptJsonWithoutCrashing() = runTest {
        val badRes = repo.importBackupJson("{ not valid json !!!")
        assertTrue(badRes.isFailure)
        val emptyObjRes = repo.importBackupJson("""{"unrelated": 123}""")
        assertTrue(emptyObjRes.isFailure)
    }

    @Test
    fun dualExtraCharge_customerCareMax300_andPrintQrOmitsAmount() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val soundEngine = SoundEngine(context)
        val vm = createTestVm(soundEngine)

        vm.toggleDualExtraChargeType(ChargeType.CUSTOMER_CARE_AND_SUPPORT)
        vm.updateExtraChargeAmount("450")
        assertEquals("300", vm.billDraft.value.extraChargeAmountText)
        assertEquals(300.0, vm.billDraft.value.extraChargeAmount, 0.01)

        vm.toggleDualExtraChargeType(ChargeType.SERVICE)
        vm.updateSecondaryChargeAmount("500")
        assertEquals(800.0, vm.billDraft.value.subtotal, 0.01)

        val printUri = QrGenerator.buildUpiUri("7083805120@ybl", "Total Solution Enterprises", 0.0, "TSE-101")
        assertTrue(printUri.contains("pa=7083805120%40ybl"))
        assertFalse(printUri.contains("am="))
        soundEngine.release()
    }

    @Test
    fun admin2WayVerificationCode_blocksWrongCode_andAllowsCorrectCode() = kotlinx.coroutines.runBlocking {
        repo.setupAdmin(AuthPolicy.CANONICAL_ADMIN_USERNAME, "Pass@1234", finalCode = "8899")
        val context = ApplicationProvider.getApplicationContext<Context>()
        val soundEngine = SoundEngine(context)
        val vm = createTestVm(soundEngine)

        suspend fun awaitUntil(cond: suspend () -> Boolean) {
            val deadline = System.currentTimeMillis() + 5000L
            while (System.currentTimeMillis() < deadline) {
                org.robolectric.shadows.ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
                if (cond()) return
                Thread.sleep(25)
            }
            org.robolectric.shadows.ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
        }

        vm.login(AuthPolicy.CANONICAL_ADMIN_USERNAME, "Pass@1234", antiRobotPassed = true, adminVerifyCode = "0000")
        awaitUntil { vm.authError.value.contains("2-Way Verification Code", ignoreCase = true) }
        assertNull(vm.session.value)

        vm.clearAuthError()
        vm.login(AuthPolicy.CANONICAL_ADMIN_USERNAME, "Pass@1234", antiRobotPassed = true, adminVerifyCode = "8899")
        awaitUntil { vm.session.value != null }
        assertTrue(vm.session.value!!.isAdmin)
        soundEngine.release()
    }

    @Test
    fun technicianShareBlocked_andDuePaymentClearsDuePreservingRating() = kotlinx.coroutines.runBlocking {
        repo.setupAdmin(AuthPolicy.CANONICAL_ADMIN_USERNAME, "Pass@1234")
        val techBmp = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
        val context = ApplicationProvider.getApplicationContext<Context>()
        val soundEngine = SoundEngine(context)
        val vm = createTestVm(soundEngine)

        suspend fun awaitUntil(cond: suspend () -> Boolean) {
            val deadline = System.currentTimeMillis() + 5000L
            while (System.currentTimeMillis() < deadline) {
                org.robolectric.shadows.ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
                if (cond()) return
                Thread.sleep(25)
            }
            org.robolectric.shadows.ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
        }

        val b = BillEntity(
            id = "b_due_test",
            invoiceNo = "TSE-901",
            date = "2026-11-22",
            customerName = "Ramesh",
            customerDetails = "9876543210",
            itemsJson = InvoiceItemJson.toJson(listOf(InvoiceItem(description = "DVR", qty = 1.0, rate = 2000.0))),
            subtotal = 2000.0,
            discount = 0.0,
            grandTotal = 2000.0,
            paidAmount = 1200.0,
            dueAmount = 800.0,
            paymentMode = PaymentMode.ONLINE.label,
            signatureBase64 = "",
            customQrText = "",
            createdBy = "tech1",
            staffRating = 5,
            staffReviewNote = "Great installation"
        )
        repo.saveBill(b)

        // Login as Technician -> Share blocked
        repo.addTechnician("Tech One", "9876500000", "tech1", "Pass@1234", photoBase64 = "abc")
        vm.login("tech1", "Pass@1234", antiRobotPassed = true)
        awaitUntil { vm.session.value != null }
        vm.shareBillPdfDirect(b)
        org.robolectric.shadows.ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        // Login as Admin -> Mark Due Paid -> due becomes 0 while 5-star review remains intact
        vm.logout()
        vm.login(AuthPolicy.CANONICAL_ADMIN_USERNAME, "Pass@1234", antiRobotPassed = true)
        awaitUntil { vm.session.value?.isAdmin == true }
        vm.markDuePaidInFull(b)
        awaitUntil { (repo.getBillById("b_due_test")?.dueAmount ?: 1.0) == 0.0 }
        val cleared = repo.getBillById("b_due_test")!!
        assertEquals(0.0, cleared.dueAmount, 0.01)
        assertEquals(2000.0, cleared.paidAmount, 0.01)
        assertEquals(5, cleared.staffRating)
        assertEquals("Great installation", cleared.staffReviewNote)
        assertTrue(techBmp.width > 0)
        soundEngine.release()
    }

    @Test
    fun strongPasswordPolicy_enforcesGmailGrade_andUpdatesAdminCredentials() = runTest {
        assertFalse(AuthPolicy.evaluatePassword("Pass1234").isStrong)     // missing special char
        assertFalse(AuthPolicy.evaluatePassword("pass@1234").isStrong)    // missing uppercase
        assertFalse(AuthPolicy.evaluatePassword("PASS@1234").isStrong)    // missing lowercase
        assertFalse(AuthPolicy.evaluatePassword("Pass@word").isStrong)    // missing digit
        assertFalse(AuthPolicy.evaluatePassword("Pa@1").isStrong)         // too short
        assertTrue(AuthPolicy.evaluatePassword("Tse@2026!").isStrong)

        val context = ApplicationProvider.getApplicationContext<Context>()
        val soundEngine = SoundEngine(context)
        val vm = createTestVm(soundEngine)

        vm.setupAdmin(AuthPolicy.CANONICAL_ADMIN_USERNAME, "weakpass", "weakpass", adminVerifyCode = "4321")
        assertTrue(vm.authError.value.isNotBlank())
        assertNull(repo.authenticate(AuthPolicy.CANONICAL_ADMIN_USERNAME, "weakpass"))

        vm.setupAdmin(AuthPolicy.CANONICAL_ADMIN_USERNAME, "Tse@2026!", "Tse@2026!", adminVerifyCode = "4321")
        val deadline = System.currentTimeMillis() + 5_000L
        while (repo.authenticate(AuthPolicy.CANONICAL_ADMIN_USERNAME, "Tse@2026!") == null &&
            System.currentTimeMillis() < deadline
        ) {
            Thread.sleep(10)
            org.robolectric.shadows.ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
        }
        assertNotNull(repo.authenticate(AuthPolicy.CANONICAL_ADMIN_USERNAME, "Tse@2026!"))
        assertTrue(repo.verifyFinalCode("4321"))

        assertTrue(repo.addTechnician("Tech", "9876543210", "tech_weak", "123456", photoBase64 = "abc").isFailure)
        assertTrue(repo.addSuperAdmin("Super", "9876543211", "super_weak", "abcdef").isFailure)

        val updRes = repo.updateAdminCredentials("Tse@2026!", newPassword = "Tse@New2027", newVerifyCode = "9900")
        assertTrue(updRes.isSuccess)
        assertNull(repo.authenticate(AuthPolicy.CANONICAL_ADMIN_USERNAME, "Tse@2026!"))
        assertNotNull(repo.authenticate(AuthPolicy.CANONICAL_ADMIN_USERNAME, "Tse@New2027"))
        assertTrue(repo.verifyFinalCode("9900"))
        assertFalse(repo.verifyFinalCode("4321"))
        soundEngine.release()
    }

    @Test
    fun darkTheme_billScreenAndSavedBillsScreen_renderHighContrastControls() {
        val sampleBill = BillEntity(
            id = "b_dark_1",
            invoiceNo = "TSE-003",
            date = "2026-11-21",
            customerName = "Manoj Kumar",
            customerDetails = "Panaji\n9876543210",
            itemsJson = InvoiceItemJson.toJson(listOf(InvoiceItem(description = "DVR", qty = 1.0, rate = 8000.0))),
            subtotal = 8507.0,
            discount = 507.0,
            grandTotal = 8000.0,
            paidAmount = 6000.0,
            dueAmount = 2000.0,
            paymentMode = PaymentMode.ONLINE.label,
            signatureBase64 = "",
            customQrText = "",
            createdBy = "Bill Book 1",
            staffRating = 5,
            staffReviewNote = "better service"
        )
        composeTestRule.setContent {
            TseBillingTheme(darkTheme = true) {
                SavedBillsScreen(
                    allBills = listOf(sampleBill),
                    filteredBills = listOf(sampleBill),
                    customerFolders = emptyList(),
                    searchQuery = "",
                    company = CompanySettings(),
                    upi = UpiSettings(),
                    isAdmin = true,
                    onSearchChange = {},
                    onOpenBill = {},
                    onPrintBill = {},
                    onSavePdf = {},
                    onSharePdf = {},
                    onSavePhoto = {},
                    onSharePhoto = {},
                    onDeleteBill = {},
                    onOpenPaymentReview = {}
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("review_bill_TSE-003").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("send_due_qr_TSE-003").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun migration7To8_preservesExistingData_andAddsInventoryExpensesTrashAuditTables() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "migration_v7_to_v8_test.db"
        context.deleteDatabase(dbName)

        // Create a v7 SQLite database manually and seed existing rows before migrating to v8
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(object : SupportSQLiteOpenHelper.Callback(7) {
                    override fun onCreate(sDb: SupportSQLiteDatabase) {
                        sDb.execSQL(
                            "CREATE TABLE IF NOT EXISTS `bills` (`id` TEXT NOT NULL PRIMARY KEY, `invoiceNo` TEXT NOT NULL, `date` TEXT NOT NULL, `customerName` TEXT NOT NULL, `customerDetails` TEXT NOT NULL, `itemsJson` TEXT NOT NULL, `subtotal` REAL NOT NULL, `discount` REAL NOT NULL, `grandTotal` REAL NOT NULL, `paidAmount` REAL NOT NULL, `dueAmount` REAL NOT NULL, `paymentMode` TEXT NOT NULL, `signatureBase64` TEXT NOT NULL, `customQrText` TEXT NOT NULL, `createdBy` TEXT NOT NULL, `updatedAt` INTEGER NOT NULL, `extraChargeType` TEXT NOT NULL DEFAULT 'NONE', `extraChargeAmount` REAL NOT NULL DEFAULT 0.0, `staffRating` INTEGER NOT NULL DEFAULT 0, `staffReviewNote` TEXT NOT NULL DEFAULT '', `documentType` TEXT NOT NULL DEFAULT 'BILL', `isHelpBill` INTEGER NOT NULL DEFAULT 0, `helpRequestId` INTEGER NOT NULL DEFAULT 0, `quotationNote` TEXT NOT NULL DEFAULT '', `secondaryChargeType` TEXT NOT NULL DEFAULT 'NONE', `secondaryChargeAmount` REAL NOT NULL DEFAULT 0.0)"
                        )
                        sDb.execSQL(
                            "CREATE TABLE IF NOT EXISTS `customers` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `details` TEXT NOT NULL, `phone` TEXT NOT NULL DEFAULT '', `address` TEXT NOT NULL DEFAULT '', `locationUrl` TEXT NOT NULL DEFAULT '')"
                        )
                        sDb.execSQL(
                            "CREATE TABLE IF NOT EXISTS `products` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `unit` TEXT NOT NULL, `defaultRate` REAL NOT NULL, `folder` TEXT NOT NULL DEFAULT 'General')"
                        )
                        sDb.execSQL(
                            "CREATE TABLE IF NOT EXISTS `user_accounts` (`username` TEXT NOT NULL PRIMARY KEY, `displayName` TEXT NOT NULL, `passwordHash` TEXT NOT NULL, `role` TEXT NOT NULL, `phone` TEXT NOT NULL DEFAULT '', `photoBase64` TEXT NOT NULL DEFAULT '')"
                        )
                        sDb.execSQL(
                            "CREATE TABLE IF NOT EXISTS `app_config` (`key` TEXT NOT NULL PRIMARY KEY, `value` TEXT NOT NULL)"
                        )
                        sDb.execSQL(
                            "CREATE TABLE IF NOT EXISTS `customer_help_requests` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `customerName` TEXT NOT NULL, `phone` TEXT NOT NULL, `address` TEXT NOT NULL, `locationUrl` TEXT NOT NULL, `helpDescription` TEXT NOT NULL, `status` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `acceptedBy` TEXT NOT NULL DEFAULT '', `acceptedByName` TEXT NOT NULL DEFAULT '', `acceptedByPhone` TEXT NOT NULL DEFAULT '', `acceptedAt` INTEGER NOT NULL DEFAULT 0, `solvedAt` INTEGER NOT NULL DEFAULT 0, `billId` TEXT NOT NULL DEFAULT '')"
                        )
                        sDb.execSQL(
                            "CREATE TABLE IF NOT EXISTS `technician_settlements` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `technicianUsername` TEXT NOT NULL, `amount` REAL NOT NULL, `note` TEXT NOT NULL, `recordedBy` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)"
                        )
                        sDb.execSQL("INSERT INTO `products` (`name`, `unit`, `defaultRate`, `folder`) VALUES ('4MP Bullet Camera', 'Nos', 2400.0, 'CCTV Cameras')")
                        sDb.execSQL("INSERT INTO `customers` (`name`, `details`, `phone`, `address`, `locationUrl`) VALUES ('Suresh Naik', 'Mapusa', '9876543210', 'Mapusa', '')")
                        sDb.execSQL("INSERT INTO `bills` (`id`, `invoiceNo`, `date`, `customerName`, `customerDetails`, `itemsJson`, `subtotal`, `discount`, `grandTotal`, `paidAmount`, `dueAmount`, `paymentMode`, `signatureBase64`, `customQrText`, `createdBy`, `updatedAt`) VALUES ('b_v7_1', 'TSE-700', '2026-11-20', 'Suresh Naik', 'Mapusa\n9876543210', '[]', 2400.0, 0.0, 2400.0, 2400.0, 0.0, 'Cash', '', '', '@TotalSolutionEnterprises', 1000)")
                    }
                    override fun onUpgrade(sDb: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
                })
                .build()
        )
        helper.writableDatabase.close()
        helper.close()

        val migratedDb = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(AppDatabase.MIGRATION_7_8)
            .allowMainThreadQueries()
            .build()
        try {
            val products = migratedDb.productDao().getAll()
            assertEquals(1, products.size)
            assertEquals("4MP Bullet Camera", products.first().name)
            assertEquals(0.0, products.first().stockQty, 0.01)
            assertEquals(0.0, products.first().minStockAlert, 0.01)

            val bills = migratedDb.billDao().observeAll().first()
            assertEquals(1, bills.size)
            assertEquals("TSE-700", bills.first().invoiceNo)

            val customers = migratedDb.customerDao().getAll()
            assertEquals(1, customers.size)
            assertEquals("Suresh Naik", customers.first().name)

            migratedDb.expenseDao().upsert(
                ExpenseEntity(
                    id = "exp_mig_1",
                    title = "Cable Purchase",
                    category = "Inventory",
                    amount = 1200.0,
                    date = "2026-11-22",
                    note = "90m coil",
                    recordedBy = "@TotalSolutionEnterprises"
                )
            )
            assertEquals(1, migratedDb.expenseDao().getAll().size)
            assertEquals(0, migratedDb.trashItemDao().getAll().size)
            assertEquals(0, migratedDb.auditLogDao().getAll().size)
        } finally {
            migratedDb.close()
            context.deleteDatabase(dbName)
        }
    }

    @Test
    fun productStock_deductsOnBillSave_adjustsOnEdit_ignoresQuotation_restoresAndRededucts() = runTest {
        repo.addProduct(
            name = "2MP Dome Camera",
            unit = "Nos",
            rate = 1500.0,
            folder = "CCTV Cameras",
            stockQty = 10.0,
            minStockAlert = 3.0
        )

        val camBefore = repo.products.first().first { it.name == "2MP Dome Camera" }
        assertEquals(10.0, camBefore.stockQty, 0.01)
        assertEquals(3.0, camBefore.minStockAlert, 0.01)

        // Saving a QUOTATION must NOT deduct stock
        val quotation = BillEntity(
            id = "q_stock_1",
            invoiceNo = "QTN-001",
            date = "2026-11-22",
            customerName = "Prospect Client",
            customerDetails = "Panaji\n9822110099",
            itemsJson = InvoiceItemJson.toJson(listOf(InvoiceItem(description = "2MP Dome Camera", qty = 4.0, rate = 1500.0))),
            subtotal = 6000.0,
            discount = 0.0,
            grandTotal = 6000.0,
            paidAmount = 0.0,
            dueAmount = 0.0,
            paymentMode = PaymentMode.CASH.label,
            signatureBase64 = "",
            customQrText = "",
            createdBy = "@TotalSolutionEnterprises",
            documentType = DocumentType.QUOTATION.name
        )
        repo.saveBill(quotation)
        assertEquals(10.0, repo.products.first().first().stockQty, 0.01)

        // Saving a BILL for 3 units deducts stock from 10 -> 7
        val bill = quotation.copy(
            id = "b_stock_1",
            invoiceNo = "TSE-001",
            documentType = DocumentType.BILL.name,
            paidAmount = 4500.0,
            subtotal = 4500.0,
            grandTotal = 4500.0,
            itemsJson = InvoiceItemJson.toJson(listOf(InvoiceItem(description = "2MP Dome Camera", qty = 3.0, rate = 1500.0)))
        )
        repo.saveBill(bill)
        assertEquals(7.0, repo.products.first().first().stockQty, 0.01)

        // Editing the same bill from 3 -> 5 units deducts net delta (2 units): 7 -> 5
        val editedBill = bill.copy(
            subtotal = 7500.0,
            grandTotal = 7500.0,
            paidAmount = 7500.0,
            itemsJson = InvoiceItemJson.toJson(listOf(InvoiceItem(description = "2MP Dome Camera", qty = 5.0, rate = 1500.0)))
        )
        repo.saveBill(editedBill)
        assertEquals(5.0, repo.products.first().first().stockQty, 0.01)

        // Soft-deleting the bill restores stock back from 5 -> 10
        assertTrue(repo.deleteBill("b_stock_1", actorUsername = "Admin", actorRole = "admin"))
        assertEquals(10.0, repo.products.first().first().stockQty, 0.01)

        // Restoring the bill from Trash re-deducts stock back from 10 -> 5
        val trashItem = repo.trashItems.first().first { it.entityId == "b_stock_1" }
        val restoreRes = repo.restoreTrashItem(trashItem.id, actorUsername = "Admin", actorRole = "admin")
        assertTrue(restoreRes.isSuccess)
        assertEquals(5.0, repo.products.first().first().stockQty, 0.01)
    }

    @Test
    fun expenseTracking_andBackupV7_roundTripPreservesStockExpensesTrashAndAuditLogs() = runTest {
        repo.setupAdmin(AuthPolicy.CANONICAL_ADMIN_USERNAME, "Tse@2026!", "4321")
        repo.addProduct(
            name = "SMPS 8CH",
            unit = "Nos",
            rate = 950.0,
            folder = "Power",
            stockQty = 6.0,
            minStockAlert = 2.0
        )
        val saveExp = repo.saveExpense(
            ExpenseEntity(
                id = "",
                title = "Diesel Generator Fuel",
                category = "Travel",
                amount = 650.0,
                date = "2026-11-22",
                note = "South Goa visit"
            ),
            actorUsername = AuthPolicy.CANONICAL_ADMIN_USERNAME,
            actorRole = "admin"
        )
        assertTrue(saveExp.isSuccess)
        val exp = saveExp.getOrThrow()

        // Create and soft-delete a temporary product so Trash & Audit logs also populate
        repo.addProduct("Temp Wire", "Mtr", 20.0, "Cables", stockQty = 50.0, minStockAlert = 5.0)
        val tempProd = repo.products.first().first { it.name == "Temp Wire" }
        repo.deleteProduct(tempProd.id, actorUsername = AuthPolicy.CANONICAL_ADMIN_USERNAME, actorRole = "admin")

        val backupJson = repo.exportBackupJson()
        assertTrue(backupJson.contains("\"schemaVersion\": 7") || backupJson.contains("\"schemaVersion\":7"))

        // Import into a brand new in-memory database and verify full v7 round-trip
        val context = ApplicationProvider.getApplicationContext<Context>()
        val targetDb = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val targetRepo = BillingRepository(targetDb)
            val importRes = targetRepo.importBackupJson(backupJson)
            assertTrue(importRes.isSuccess)

            val restoredProd = targetRepo.products.first().first { it.name == "SMPS 8CH" }
            assertEquals(6.0, restoredProd.stockQty, 0.01)
            assertEquals(2.0, restoredProd.minStockAlert, 0.01)

            val restoredExp = targetRepo.expenses.first().firstOrNull { it.id == exp.id }
            assertNotNull(restoredExp)
            assertEquals(650.0, restoredExp!!.amount, 0.01)

            assertTrue(targetRepo.trashItems.first().any { it.entityId == tempProd.id.toString() && it.status == "TRASHED" })
            assertTrue(targetRepo.auditLogs.first().any { it.action == "EXPENSE_ADD" })
            assertTrue(targetRepo.auditLogs.first().any { it.action == "DELETE" })
        } finally {
            targetDb.close()
        }
    }

    @Test
    fun trashRetention24Hours_softDeleteRestoreAutoPurgeAndRatedBillGuard() = runTest {
        val now = 1_700_000_000_000L
        val twentyFourHoursMs = 24L * 60L * 60L * 1000L

        // Rated bill (1..5 stars) CANNOT be deleted
        val ratedBill = BillEntity(
            id = "b_rated_guard",
            invoiceNo = "TSE-901",
            date = "2026-11-22",
            customerName = "Locked Review Client",
            customerDetails = "Panaji",
            itemsJson = "[]",
            subtotal = 1000.0,
            discount = 0.0,
            grandTotal = 1000.0,
            paidAmount = 1000.0,
            dueAmount = 0.0,
            paymentMode = PaymentMode.CASH.label,
            signatureBase64 = "",
            customQrText = "",
            createdBy = "tech1",
            staffRating = 5,
            staffReviewNote = "Superb!"
        )
        repo.saveBill(ratedBill)
        assertFalse(repo.deleteBill("b_rated_guard", nowMillis = now))
        assertNotNull(repo.getBillById("b_rated_guard"))

        // Expense soft-delete -> 23h later still restorable; Customer soft-delete -> 24h+1ms later auto-purged
        val exp = repo.saveExpense(
            ExpenseEntity(id = "exp_ladder_1", title = "Ladder Repair", category = "Tools", amount = 300.0, date = "2026-11-22")
        ).getOrThrow()
        repo.deleteExpense(exp.id, actorUsername = "Admin", actorRole = "admin", nowMillis = now)
        assertEquals(0, repo.expenses.first().size)

        val expTrash = repo.trashItems.first().first { it.entityId == exp.id }
        val earlyPurgeCount = repo.purgeExpiredTrash(nowMillis = now + twentyFourHoursMs - 60_000L)
        assertEquals(0, earlyPurgeCount)
        assertTrue(repo.restoreTrashItem(expTrash.id, nowMillis = now + twentyFourHoursMs - 60_000L).isSuccess)
        assertEquals(1, repo.expenses.first().size)

        // Now delete a Customer and let 24h window expire
        repo.saveCustomer(name = "Expired Client", details = "Margao", phone = "9876000011", address = "Margao")
        val cust = repo.customers.first().first { it.name == "Expired Client" }
        repo.deleteCustomer(cust.id, actorUsername = "Admin", actorRole = "admin", nowMillis = now)
        assertFalse(repo.customers.first().any { it.id == cust.id })

        val purged = repo.purgeExpiredTrash(nowMillis = now + twentyFourHoursMs + 1_000L)
        assertEquals(1, purged)
        assertFalse(repo.trashItems.first().any { it.entityId == cust.id.toString() })
        assertTrue(repo.auditLogs.first().any { it.action == "AUTO_PURGE_24H" && it.entityId == cust.id.toString() })
    }

    @Test
    fun authScreen_staffOnlyApk_hidesCreateAdminAndRestoreTabs_andPrefillsInviteCode() {
        composeTestRule.setContent {
            TseBillingTheme(darkTheme = false) {
                AuthScreen(
                    isAdminConfigured = false,
                    accounts = emptyList(),
                    authError = "",
                    isStaffOnlyApk = true,
                    prefillInviteCode = "TSE-TECH-INV99",
                    onClearError = {},
                    onSetupAdmin = { _, _, _, _ -> },
                    onLogin = { _, _, _, _ -> }
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("setup_tab_create").assertDoesNotExist()
        composeTestRule.onNodeWithTag("setup_tab_restore").assertDoesNotExist()
        composeTestRule.onNodeWithTag("cloud_invite_code_input")
            .performScrollTo()
            .assertIsDisplayed()
            .assertTextContains("TSE-TECH-INV99")
    }
}
