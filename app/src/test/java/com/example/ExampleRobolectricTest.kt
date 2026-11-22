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
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
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

        repo.setupAdmin("admin_main", "Pass1234")
        assertEquals(true, repo.isAdminConfigured.first())

        // Calling setupAdmin again replaces any previous Admin so strictly ONE Admin exists
        repo.setupAdmin("admin_only", "Pass5678")
        val admins = repo.accounts.first().filter { it.role.equals("admin", ignoreCase = true) }
        assertEquals(1, admins.size)
        assertEquals("admin_only", admins.first().username)

        val techRes = repo.addTechnician("Rahul Verma", "9876543210", "rahul_tech", "TechPass123", photoBase64 = "tech_photo_b64_sample")
        assertTrue(techRes.isSuccess)
        val techSession = repo.authenticate("rahul_tech", "TechPass123")
        assertNotNull(techSession)
        assertTrue(techSession!!.isTechnician)
        assertEquals("Technician", techSession.roleDisplayLabel)

        val superRes = repo.addSuperAdmin("Auditor", "9988776655", "super1", "SuperPass123")
        assertTrue(superRes.isSuccess)
        val superSession = repo.authenticate("super1", "SuperPass123")
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
        assertNull(repo.authenticate("rahul_tech", "TechPass123"))
        assertNotNull(repo.authenticate("rahul_pro", "TechPass123"))
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
        repo.setupAdmin("admin1", "Pass1234")
        repo.addTechnician("Vikram Dessai", "9822098220", "vikram_tech", "Pass1234", photoBase64 = "tech_photo_b64_sample")
        val techSession = repo.authenticate("vikram_tech", "Pass1234")!!

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
        repo.setupAdmin("admin1", "Pass1234")
        repo.addTechnician("Rahul Verma", "9876543210", "rahul_tech", "Pass1234", photoBase64 = "tech_photo_b64_sample")
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
        repo.setupAdmin("admin1", "Pass1234")
        val context = ApplicationProvider.getApplicationContext<Context>()
        val soundEngine = SoundEngine(context)
        val vm = createTestVm(soundEngine)

        vm.login("admin1", "Pass1234")
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
        composeTestRule.onNodeWithTag("new_tech_photo_status").assertIsDisplayed()
        composeTestRule.onNodeWithTag("new_tech_pick_photo_button").assertIsDisplayed()
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
        repo.setupAdmin("admin1", "oldPass123")
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

        val wrongRecovery = repo.resetAdminPasswordWithRecovery("Wrong", "admin1", "newPass456")
        assertTrue(wrongRecovery.isFailure)
        val okRecovery = repo.resetAdminPasswordWithRecovery(
            BillingRepository.DEFAULT_ADMIN_RECOVERY_PASSWORD,
            "admin1",
            "newPass456"
        )
        assertTrue(okRecovery.isSuccess)
        assertNotNull(repo.authenticate("admin1", "newPass456"))
    }

    @Test
    fun authScreen_showsBackupSignInTab_singleAdminDropdown_andForgotPasswordDialog() {
        var isConfigured by mutableStateOf<Boolean?>(false)
        val accounts = listOf(
            UserAccountEntity(username = "admin1", displayName = "Admin", role = "admin", passwordHash = "hash1"),
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
        composeTestRule.onNodeWithTag("setup_tab_restore").performClick()
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
        composeTestRule.onNodeWithTag("login_user_option_admin1").assertIsDisplayed()
        composeTestRule.onNodeWithTag("login_user_option_admin_dup").assertDoesNotExist()
        composeTestRule.onNodeWithTag("login_user_option_rahul_tech").assertIsDisplayed()
    }

    @Test
    fun fullBackupExportAndImport_restoresBillsTechniciansSettlementsVoiceAndLogo() = runTest {
        repo.setupAdmin("admin_tse", "AdminPass99")
        repo.addTechnician("Rahul Verma", "9876543210", "rahul_tech", "TechPass99", photoBase64 = "tech_photo_b64_sample")
        repo.addSuperAdmin("Viewer", "9988776655", "super_view", "SuperPass99")
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
        repo.recordTechnicianSettlement("rahul_tech", 1200.0, "Cleared", "admin_tse")

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
            assertNotNull(freshRepo.authenticate("admin_tse", "AdminPass99"))
            val restoredTech = freshRepo.authenticate("rahul_tech", "TechPass99")
            assertNotNull(restoredTech)
            assertEquals("tech_photo_b64_sample", restoredTech!!.photoBase64)
            assertNotNull(freshRepo.authenticate("super_view", "SuperPass99"))
            assertEquals("logo_b64_sample", freshRepo.companySettings.first().logoBase64)
            assertEquals("Important Notice Today", freshRepo.voiceAndNoteSettings.first().headerNote)
        } finally {
            freshDb.close()
        }
    }

    @Test
    fun loginSecurity_antiRobotCheck_5WrongAttempts2HourLock_then5MoreTriesAfter2Hours() = kotlinx.coroutines.runBlocking {
        repo.setupAdmin("admin1", "Pass1234")
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

        // Anti-Robot failed -> blocks login without counting as password failure
        vm.login("admin1", "Pass1234", antiRobotPassed = false, nowMillis = t0)
        awaitUntil { vm.authError.value.contains("Anti-Robot") }
        assertNull(vm.session.value)
        assertTrue(vm.authError.value.contains("Anti-Robot"))
        assertEquals(0, repo.getLoginSecurityState().failedAttempts)

        // 4 wrong password attempts -> still unlocked, 1 try left
        repeat(4) { idx ->
            vm.login("admin1", "wrong_$idx", antiRobotPassed = true, nowMillis = t0 + idx * 1000L)
            awaitUntil { repo.getLoginSecurityState().failedAttempts == idx + 1 }
        }
        val state4 = repo.getLoginSecurityState()
        assertEquals(4, state4.failedAttempts)
        assertFalse(state4.isLocked(t0 + 4000L))
        assertEquals(1, state4.remainingAttempts(t0 + 4000L))

        // 5th wrong password -> triggers 2-hour lock (7,200,000 ms)
        val tLockStart = t0 + 5000L
        vm.login("admin1", "wrong_5", antiRobotPassed = true, nowMillis = tLockStart)
        awaitUntil { repo.getLoginSecurityState().failedAttempts == 5 }
        val state5 = repo.getLoginSecurityState()
        assertEquals(5, state5.failedAttempts)
        assertTrue(state5.isLocked(tLockStart + 60_000L))
        assertEquals(tLockStart + LoginSecurityState.LOCK_DURATION_MS, state5.lockUntilMillis)

        // Even the correct password is blocked while 2-hour lock is active
        vm.clearAuthError()
        vm.login("admin1", "Pass1234", antiRobotPassed = true, nowMillis = tLockStart + 30 * 60_000L)
        awaitUntil { vm.authError.value.contains("locked", ignoreCase = true) }
        assertNull(vm.session.value)
        assertTrue(vm.authError.value.contains("locked", ignoreCase = true))

        // After 2 hours expire, user gets 5 fresh tries again
        val tAfterLock = tLockStart + LoginSecurityState.LOCK_DURATION_MS + 1000L
        assertFalse(state5.isLocked(tAfterLock))
        assertEquals(0, state5.effectiveFailedAttempts(tAfterLock))
        assertEquals(5, state5.remainingAttempts(tAfterLock))

        // 4 more wrong attempts after 2-hour expiry must NOT lock yet
        repeat(4) { idx ->
            vm.login("admin1", "wrong_cycle2_$idx", antiRobotPassed = true, nowMillis = tAfterLock + idx * 1000L)
            awaitUntil { repo.getLoginSecurityState().failedAttempts == idx + 1 && repo.getLoginSecurityState().lockUntilMillis == 0L }
        }
        val cycle2After4 = repo.getLoginSecurityState()
        assertEquals(4, cycle2After4.failedAttempts)
        assertFalse(cycle2After4.isLocked(tAfterLock + 4000L))

        // Correct password on 5th try of cycle 2 succeeds and resets failure counter to 0
        vm.login("admin1", "Pass1234", antiRobotPassed = true, nowMillis = tAfterLock + 5000L)
        awaitUntil { vm.session.value != null && repo.getLoginSecurityState().failedAttempts == 0 }
        assertNotNull(vm.session.value)
        assertEquals(0, repo.getLoginSecurityState().failedAttempts)
        assertEquals(0L, repo.getLoginSecurityState().lockUntilMillis)
        soundEngine.release()
    }

    @Test
    fun technicianCreation_requiresPhotoAndPersistsInSession() = kotlinx.coroutines.runBlocking {
        repo.setupAdmin("admin1", "Pass1234")
        val blankPhotoRes = repo.addTechnician("No Pic Tech", "9876543210", "nopic_tech", "Pass1234", photoBase64 = "")
        assertTrue(blankPhotoRes.isFailure)

        val context = ApplicationProvider.getApplicationContext<Context>()
        val soundEngine = SoundEngine(context)
        val vm = createTestVm(soundEngine)

        vm.login("admin1", "Pass1234", antiRobotPassed = true)
        val deadline = System.currentTimeMillis() + 5000L
        while (vm.session.value == null && System.currentTimeMillis() < deadline) {
            org.robolectric.shadows.ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
            Thread.sleep(25)
        }
        assertNotNull(vm.session.value)

        var addedWithoutPic = false
        vm.addTechnician("No Pic Tech", "9876543210", "nopic_tech", "Pass1234", photoBitmap = null) {
            addedWithoutPic = true
        }
        org.robolectric.shadows.ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
        Thread.sleep(40)
        assertFalse(addedWithoutPic)
        assertNull(repo.authenticate("nopic_tech", "Pass1234"))

        val bmp = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        var addedWithPic = false
        vm.addTechnician("Amit Dessai", "9822011223", "amit_tech", "Pass1234", photoBitmap = bmp) {
            addedWithPic = true
        }
        val deadline2 = System.currentTimeMillis() + 5000L
        while (!addedWithPic && System.currentTimeMillis() < deadline2) {
            org.robolectric.shadows.ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
            Thread.sleep(25)
        }
        assertTrue(addedWithPic)
        val techSession = repo.authenticate("amit_tech", "Pass1234")
        assertNotNull(techSession)
        assertTrue(techSession!!.photoBase64.isNotBlank())
        soundEngine.release()
    }

    @Test
    fun authScreen_shows2HourLockoutBannerAndDisablesSubmit() {
        val accounts = listOf(
            UserAccountEntity(username = "admin1", displayName = "Admin", role = "admin", passwordHash = "hash1")
        )
        composeTestRule.setContent {
            TseBillingTheme {
                AuthScreen(
                    isAdminConfigured = true,
                    accounts = accounts,
                    authError = "",
                    loginSecurityState = LoginSecurityState(
                        failedAttempts = 5,
                        lockUntilMillis = System.currentTimeMillis() + 3_600_000L
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
        repo.setupAdmin("admin2fa", "Pass1234", finalCode = "8899")
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

        vm.login("admin2fa", "Pass1234", antiRobotPassed = true, adminVerifyCode = "0000")
        awaitUntil { vm.authError.value.contains("2-Way Verification Code", ignoreCase = true) }
        assertNull(vm.session.value)

        vm.clearAuthError()
        vm.login("admin2fa", "Pass1234", antiRobotPassed = true, adminVerifyCode = "8899")
        awaitUntil { vm.session.value != null }
        assertTrue(vm.session.value!!.isAdmin)
        soundEngine.release()
    }

    @Test
    fun technicianShareBlocked_andDuePaymentClearsDuePreservingRating() = kotlinx.coroutines.runBlocking {
        repo.setupAdmin("admin_main", "Pass1234")
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
        repo.addTechnician("Tech One", "9876500000", "tech1", "Pass1234", photoBase64 = "abc")
        vm.login("tech1", "Pass1234", antiRobotPassed = true)
        awaitUntil { vm.session.value != null }
        vm.shareBillPdfDirect(b)
        org.robolectric.shadows.ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        // Login as Admin -> Mark Due Paid -> due becomes 0 while 5-star review remains intact
        vm.logout()
        vm.login("admin_main", "Pass1234", antiRobotPassed = true)
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
}
