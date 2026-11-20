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
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.BillEntity
import com.example.data.BillingRepository
import com.example.data.ChargeType
import com.example.data.CompanySettings
import com.example.data.CustomerEntity
import com.example.data.CustomerHelpEntity
import com.example.data.InvoiceItem
import com.example.data.InvoiceItemJson
import com.example.data.PaymentMode
import com.example.data.ProductEntity
import com.example.data.UpiSettings
import com.example.data.UserAccountEntity
import com.example.data.UserSession
import com.example.ui.BillDraft
import com.example.ui.BillingViewModel
import com.example.ui.MainTab
import com.example.ui.StaffPerformanceSummary
import com.example.ui.components.AuthScreen
import com.example.ui.components.PaymentAndReviewDialog
import com.example.ui.screens.AdminPanelScreen
import com.example.ui.screens.BillScreen
import com.example.ui.screens.CustomerHelpScreen
import com.example.ui.screens.CustomersScreen
import com.example.ui.screens.ProductsScreen
import com.example.ui.screens.SavedBillsScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.TseBillingTheme
import com.example.util.InvoicePdfPrinter
import com.example.util.InvoicePrintPayload
import com.example.util.LocationHelper
import com.example.util.QrGenerator
import com.example.util.SoundEngine
import kotlinx.coroutines.flow.first
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
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var db: AppDatabase
    private lateinit var repo: BillingRepository

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
        db.close()
    }

    @Test
    fun appName_matchesBrand() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("TSE Billing", context.getString(R.string.app_name))
    }

    @Test
    fun adminSetup_login_andStaffLimitFlow() = runTest {
        repo.seedDefaultsIfNeeded()
        assertEquals(false, repo.isAdminConfigured.first())

        repo.setupAdmin("admin1", "pass1234", "9999")
        assertEquals(true, repo.isAdminConfigured.first())

        val okSession = repo.authenticate("admin1", "pass1234")
        assertNotNull(okSession)
        assertTrue(okSession!!.isAdmin)
        assertNull(repo.authenticate("admin1", "wrong"))
        assertTrue(repo.verifyFinalCode("9999"))

        repeat(5) { idx ->
            val res = repo.addStaff("Staff $idx", "staff$idx", "pass1234")
            assertTrue(res.isSuccess)
        }
        val sixth = repo.addStaff("Staff 6", "staff6", "pass1234")
        assertTrue(sixth.isFailure)
    }

    @Test
    fun upiQrGeneration_createsValidBitmap() {
        val uri = QrGenerator.buildUpiUri("7083805120@ybl", "Manoj Kumar Saha", 2500.0, "TSE-001")
        assertTrue(uri.startsWith("upi://pay?"))
        assertTrue(uri.contains("am=2500.00"))
        val bmp = QrGenerator.generateQrBitmap(uri, 160)
        assertNotNull(bmp)
    }

    @Test
    fun updateProduct_updatesNameUnitAndPrice() = runTest {
        repo.seedDefaultsIfNeeded()
        val initial = repo.products.first().first()
        repo.updateProduct(initial.id, "4MP ColorVu Dome Camera", "Pcs", 2450.0)
        val updated = repo.products.first().first { it.id == initial.id }
        assertEquals("4MP ColorVu Dome Camera", updated.name)
        assertEquals("Pcs", updated.unit)
        assertEquals(2450.0, updated.defaultRate, 0.001)
    }

    @Test
    fun a4InvoiceRenderer_draws15ItemsPlusCharge_omitsBlankRows_andRendersAuthSig() {
        assertEquals(595, InvoicePdfPrinter.A4_WIDTH_PT)
        assertEquals(842, InvoicePdfPrinter.A4_HEIGHT_PT)

        val dummyAuthSig = Bitmap.createBitmap(24, 12, Bitmap.Config.ARGB_8888)
        val out = ByteArrayOutputStream()
        dummyAuthSig.compress(Bitmap.CompressFormat.PNG, 90, out)
        val authSigBase64 = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)

        // Include 15 filled items plus 2 unfilled blank rows
        val items = (1..15).map { idx ->
            InvoiceItem(description = "CCTV Item $idx", qty = 2.0, unit = "Nos", rate = 1250.0)
        } + listOf(
            InvoiceItem(description = "", qty = 1.0, unit = "Nos", rate = 0.0),
            InvoiceItem(description = "   ", qty = 1.0, unit = "Nos", rate = 0.0)
        )

        val effective = InvoicePdfPrinter.buildEffectivePrintItems(
            items = items,
            extraChargeType = ChargeType.SERVICE,
            extraChargeAmount = 500.0
        )
        assertEquals(16, effective.size)
        assertEquals("Service Charges", effective.last().description)

        val payload = InvoicePrintPayload(
            company = CompanySettings(authorisedSignatureBase64 = authSigBase64),
            upi = UpiSettings(upiId = "7083805120@ybl", payeeName = "Manoj Kumar Saha"),
            invoiceNo = "TSE-009",
            date = "2026-11-19",
            customerName = "Test Customer",
            customerDetails = "Calangute, Goa\n9876543210",
            items = items,
            extraChargeType = ChargeType.SERVICE,
            extraChargeAmount = 500.0,
            subtotal = 38000.0,
            discount = 500.0,
            grandTotal = 37500.0,
            paidAmount = 20000.0,
            dueAmount = 17500.0,
            paymentMode = PaymentMode.ONLINE,
            signatureBase64 = "",
            customQrText = "DVR Admin Pass: 1234"
        )
        val bitmap = Bitmap.createBitmap(
            InvoicePdfPrinter.A4_WIDTH_PT,
            InvoicePdfPrinter.A4_HEIGHT_PT,
            Bitmap.Config.ARGB_8888
        )
        val canvas = android.graphics.Canvas(bitmap)
        InvoicePdfPrinter.drawInvoicePage(canvas, payload)
        assertEquals(595, bitmap.width)
        assertEquals(842, bitmap.height)
    }

    @Test
    fun addItem_allowsUpTo15Items_blocks16th() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val soundEngine = SoundEngine(context)
        val vm = BillingViewModel(repo, soundEngine)

        assertEquals(1, vm.billDraft.value.items.size)
        repeat(20) {
            vm.addItem()
        }
        assertEquals(15, vm.billDraft.value.items.size)
        soundEngine.release()
    }

    @Test
    fun productsScreen_hidesEditAndSaveForStaff_showsForAdmin() {
        val sampleProducts = listOf(ProductEntity(id = 1L, name = "2MP Dome Camera", unit = "Nos", defaultRate = 1200.0))
        val staffUser = UserSession(username = "staff1", displayName = "Staff One", role = "staff")
        val adminUser = UserSession(username = "admin1", displayName = "Admin", role = "admin")

        var currentUser by mutableStateOf(staffUser)
        composeTestRule.setContent {
            TseBillingTheme {
                ProductsScreen(
                    products = sampleProducts,
                    user = currentUser,
                    onAddProduct = { _, _, _ -> },
                    onUpdateProduct = { _, _, _, _ -> },
                    onDeleteProduct = {}
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("add_product_button").assertDoesNotExist()
        composeTestRule.onNodeWithTag("edit_product_1").assertDoesNotExist()
        composeTestRule.onNodeWithTag("delete_product_1").assertDoesNotExist()
        composeTestRule.onNodeWithTag("save_edited_product_button").assertDoesNotExist()

        currentUser = adminUser
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("add_product_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("edit_product_1").assertIsDisplayed()
        composeTestRule.onNodeWithTag("delete_product_1").assertIsDisplayed()
    }

    @Test
    fun settingsScreen_hidesEditSaveAndAuthSignatureForStaff_showsForAdmin() {
        val staffUser = UserSession(username = "staff1", displayName = "Staff One", role = "staff")
        val adminUser = UserSession(username = "admin1", displayName = "Admin", role = "admin")

        var currentUser by mutableStateOf(staffUser)
        composeTestRule.setContent {
            TseBillingTheme {
                SettingsScreen(
                    settings = CompanySettings(),
                    user = currentUser,
                    onSaveSettings = {},
                    onPickFallbackQrBitmap = {},
                    onPickAuthorisedSignatureBitmap = {},
                    onExportBackup = { "{}" },
                    onImportBackup = {}
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("settings_company_name").assertDoesNotExist()
        composeTestRule.onNodeWithTag("save_settings_button").assertDoesNotExist()
        composeTestRule.onNodeWithTag("upload_auth_signature_button").assertDoesNotExist()
        composeTestRule.onNodeWithTag("pick_qr_image_button").assertDoesNotExist()
        composeTestRule.onNodeWithTag("import_backup_button").assertDoesNotExist()

        currentUser = adminUser
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("settings_lazy_list").performScrollToIndex(1)
        composeTestRule.onNodeWithTag("settings_company_name").assertIsDisplayed()
        composeTestRule.onNodeWithTag("save_settings_button").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_lazy_list").performScrollToIndex(2)
        composeTestRule.onNodeWithTag("upload_auth_signature_button").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun billScreen_showsCameraSignatureScan_staffUsernameInModal_and3RowA4Preview() {
        val staffUser = UserSession(username = "rahul_staff", displayName = "Rahul Verma", role = "staff")
        val draft = BillDraft(
            customerName = "Ramesh Sharma",
            customerDetails = "Panaji, Goa\n9876543210",
            invoiceNo = "TSE-101",
            items = listOf(
                InvoiceItem(description = "2MP Dome Camera", qty = 2.0, unit = "Nos", rate = 1200.0),
                InvoiceItem(description = "", qty = 1.0, unit = "Nos", rate = 0.0)
            ),
            paidAmountText = "500",
            showSheetPreview = true
        )
        var showModal by mutableStateOf(true)
        composeTestRule.setContent {
            TseBillingTheme {
                BillScreen(
                    draft = draft,
                    products = emptyList(),
                    customers = emptyList(),
                    company = CompanySettings(),
                    upi = UpiSettings(upiId = "7083805120@ybl"),
                    language = com.example.data.AppLanguage.EN,
                    currentUser = staffUser,
                    finalModalVisible = showModal,
                    finalModalError = "",
                    exportOptionsPayload = null,
                    onCustomerNameChange = {},
                    onCustomerSelected = {},
                    onCustomerDetailsChange = {},
                    onCustomerLocationUrlChange = {},
                    onInvoiceNoChange = {},
                    onInvoiceDateChange = {},
                    onAddItem = {},
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
                    onToggleSheetPreview = {},
                    onResetBill = {},
                    onSaveBill = {},
                    onOpenFinalCodeModal = {},
                    onCloseFinalCodeModal = {},
                    onConfirmFinalBill = {},
                    onDismissExportOptions = {},
                    onPrintA4 = {},
                    onSavePdf = {},
                    onSharePdf = {}
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("final_dialog_staff_info").assertIsDisplayed()
        composeTestRule.onNodeWithTag("final_dialog_staff_info").assertTextContains("@rahul_staff", substring = true)

        showModal = false
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("bill_creator_badge").assertTextContains("@rahul_staff", substring = true)
        composeTestRule.onNodeWithText("Add Item (2/15)").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("bill_lazy_list").performScrollToIndex(2)
        composeTestRule.onNodeWithText("LIVE A4 INVOICE SHEET PREVIEW").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("TAX INVOICE").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("SUB TOTAL").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("ADVANCE").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("GRAND TOTAL").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Customer Signature").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("bill_lazy_list").performScrollToIndex(8)
        composeTestRule.onNodeWithTag("scan_signature_camera_button").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun savedBillsScreen_displaysCreatorUsername_dueHighlight_and5StarReview() {
        val sampleBill = BillEntity(
            id = "b_1",
            invoiceNo = "TSE-055",
            date = "2026-11-19",
            customerName = "Deepak Patil",
            customerDetails = "Mapusa",
            itemsJson = InvoiceItemJson.toJson(listOf(InvoiceItem(description = "4 Ch DVR", qty = 1.0, rate = 3500.0))),
            subtotal = 3500.0,
            discount = 200.0,
            grandTotal = 3300.0,
            paidAmount = 2000.0,
            dueAmount = 1300.0,
            paymentMode = PaymentMode.ONLINE.label,
            signatureBase64 = "",
            customQrText = "",
            createdBy = "rahul_staff",
            staffRating = 5,
            staffReviewNote = "Neat CCTV wiring and quick setup",
            updatedAt = 1L
        )
        composeTestRule.setContent {
            TseBillingTheme {
                SavedBillsScreen(
                    allBills = listOf(sampleBill),
                    filteredBills = listOf(sampleBill),
                    searchQuery = "",
                    company = CompanySettings(),
                    upi = UpiSettings(),
                    isAdmin = true,
                    onSearchChange = {},
                    onOpenBill = {},
                    onOpenPaymentReview = {},
                    onPrintBill = {},
                    onSavePdf = {},
                    onSharePdf = {},
                    onDeleteBill = {}
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("saved_bill_creator_TSE-055").assertIsDisplayed()
        composeTestRule.onNodeWithTag("saved_bill_creator_TSE-055").assertTextContains("@rahul_staff", substring = true)
        composeTestRule.onNodeWithTag("saved_bill_review_TSE-055").assertIsDisplayed()
        composeTestRule.onNodeWithText("5/5").assertIsDisplayed()
        composeTestRule.onNodeWithText("Neat CCTV wiring", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag("review_bill_TSE-055").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun saveBillSettlementAndReview_updatesDiscountDueRating_andStaffPerformanceSummary() = runTest {
        repo.setupAdmin("admin1", "pass1234", "9999")
        repo.addStaff("Rahul Verma", "rahul_staff", "pass1234")

        val bill = BillEntity(
            id = "b_settle_1",
            invoiceNo = "TSE-201",
            date = "2026-11-20",
            customerName = "Karan Naik",
            customerDetails = "Panaji",
            itemsJson = InvoiceItemJson.toJson(listOf(InvoiceItem(description = "4 Ch DVR", qty = 1.0, rate = 5000.0))),
            subtotal = 5000.0,
            discount = 0.0,
            grandTotal = 5000.0,
            paidAmount = 0.0,
            dueAmount = 5000.0,
            paymentMode = PaymentMode.ONLINE.label,
            signatureBase64 = "",
            customQrText = "",
            createdBy = "rahul_staff"
        )
        repo.saveBill(bill)

        repo.updateBillSettlementAndReview(
            billId = "b_settle_1",
            discount = 500.0,
            grandTotal = 4500.0,
            paidAmount = 3000.0,
            dueAmount = 1500.0,
            staffRating = 5,
            staffReviewNote = "Excellent behavior and on-time finish"
        )

        val updated = repo.getBillById("b_settle_1")
        assertNotNull(updated)
        assertEquals(500.0, updated!!.discount, 0.001)
        assertEquals(4500.0, updated.grandTotal, 0.001)
        assertEquals(3000.0, updated.paidAmount, 0.001)
        assertEquals(1500.0, updated.dueAmount, 0.001)
        assertEquals(5, updated.staffRating)
        assertEquals("Excellent behavior and on-time finish", updated.staffReviewNote)
    }

    @Test
    fun adminPanel_displaysStaffBillsCountAndStarRating() {
        val accounts = listOf(
            UserAccountEntity(username = "admin1", displayName = "Admin", role = "admin", passwordHash = "x"),
            UserAccountEntity(username = "rahul_staff", displayName = "Rahul Verma", role = "staff", passwordHash = "x")
        )
        val perf = listOf(
            StaffPerformanceSummary(
                username = "rahul_staff",
                displayName = "Rahul Verma",
                role = "staff",
                billCount = 4,
                totalBilledAmount = 18500.0,
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
                    staffPerformance = perf,
                    onSaveUpi = { _, _ -> },
                    onClearUpi = {},
                    onChangeFinalCode = { _, _ -> },
                    onAddStaff = { _, _, _, _ -> },
                    onRemoveStaff = {}
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("admin_lazy_list").performScrollToIndex(2)
        composeTestRule.onNodeWithText("Staff Bills & 5-Star Performance").assertIsDisplayed()
        composeTestRule.onNodeWithTag("staff_perf_rahul_staff").assertIsDisplayed()
        composeTestRule.onNodeWithText("Bills: 4").assertIsDisplayed()
        composeTestRule.onNodeWithText("Rating: 4.5 / 5 (2 reviews)").assertIsDisplayed()
    }

    @Test
    fun chargeType_appendsAtLastOfPrintItems_andAddsToTotals() {
        val items = listOf(
            InvoiceItem(description = "2MP Dome Camera", qty = 2.0, unit = "Nos", rate = 1200.0),
            InvoiceItem(description = "4 Ch DVR", qty = 1.0, unit = "Nos", rate = 3500.0)
        )
        val draft = BillDraft(
            items = items,
            extraChargeType = ChargeType.INSTALLATION_AND_SERVICE,
            extraChargeAmountText = "850",
            discountText = "100",
            paidAmountText = "5000"
        )
        assertEquals(6750.0, draft.subtotal, 0.001)
        assertEquals(6650.0, draft.grandTotal, 0.001)
        assertEquals(1650.0, draft.dueAmount, 0.001)

        val effective = InvoicePdfPrinter.buildEffectivePrintItems(
            items = items,
            extraChargeType = draft.extraChargeType,
            extraChargeAmount = draft.extraChargeAmount
        )
        assertEquals(3, effective.size)
        assertEquals("Installation & Service Charges", effective.last().description)
        assertEquals(850.0, effective.last().amount, 0.001)
    }

    @Test
    fun saveBill_autoSavesCustomerWithPhoneAddressAndLocationUrl() = runTest {
        val bill = BillEntity(
            id = "b_auto_1",
            invoiceNo = "TSE-010",
            date = "2026-11-20",
            customerName = "Rakesh Naik",
            customerDetails = "Mapusa Market, Goa\n9876543210",
            itemsJson = InvoiceItemJson.toJson(listOf(InvoiceItem(description = "DVR", qty = 1.0, rate = 4000.0))),
            subtotal = 4500.0,
            discount = 0.0,
            grandTotal = 4500.0,
            paidAmount = 4500.0,
            dueAmount = 0.0,
            paymentMode = PaymentMode.ONLINE.label,
            signatureBase64 = "",
            customQrText = "",
            createdBy = "staff1",
            extraChargeType = ChargeType.SERVICE.name,
            extraChargeAmount = 500.0
        )
        val mapsPin = "https://maps.google.com/?q=15.591500,73.810000"
        repo.saveBill(bill, customerLocationUrl = mapsPin)

        val savedCustomers = repo.customers.first()
        assertEquals(1, savedCustomers.size)
        val cust = savedCustomers.first()
        assertEquals("Rakesh Naik", cust.name)
        assertEquals("9876543210", cust.phone)
        assertEquals("Mapusa Market, Goa", cust.address)
        assertEquals(mapsPin, cust.locationUrl)
    }

    @Test
    fun customersScreen_hidesEditAddDeleteForStaff_showsForAdmin() {
        val sampleCustomers = listOf(
            CustomerEntity(
                id = 10L,
                name = "Suresh Desai",
                details = "Ponda, Goa\n9988776655",
                phone = "9988776655",
                address = "Ponda, Goa",
                locationUrl = "https://maps.google.com/?q=15.4027,74.0078"
            )
        )
        val staffUser = UserSession(username = "staff1", displayName = "Staff One", role = "staff")
        val adminUser = UserSession(username = "admin1", displayName = "Admin", role = "admin")

        var currentUser by mutableStateOf(staffUser)
        composeTestRule.setContent {
            TseBillingTheme {
                CustomersScreen(
                    customers = sampleCustomers,
                    user = currentUser,
                    onSaveCustomer = { _, _, _, _, _ -> },
                    onDeleteCustomer = {}
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Suresh Desai").assertIsDisplayed()
        composeTestRule.onNodeWithTag("open_customer_map_10").assertIsDisplayed()
        composeTestRule.onNodeWithTag("save_customer_button").assertDoesNotExist()
        composeTestRule.onNodeWithTag("edit_customer_10").assertDoesNotExist()
        composeTestRule.onNodeWithTag("delete_customer_10").assertDoesNotExist()

        currentUser = adminUser
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("customers_lazy_list").performScrollToIndex(1)
        composeTestRule.onNodeWithTag("save_customer_button").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("customers_lazy_list").performScrollToIndex(3)
        composeTestRule.onNodeWithTag("edit_customer_10").assertIsDisplayed()
        composeTestRule.onNodeWithTag("delete_customer_10").assertIsDisplayed()
    }

    @Test
    fun customerSearch_matchesLast5DigitsOfMobile() {
        val customers = listOf(
            CustomerEntity(
                id = 1L,
                name = "Suresh Desai",
                details = "Ponda, Goa\n9988776655",
                phone = "9988776655",
                address = "Ponda, Goa",
                locationUrl = "https://maps.google.com/?q=15.4027,74.0078"
            ),
            CustomerEntity(
                id = 2L,
                name = "Amit Verma",
                details = "Panaji, Goa\n9123454321",
                phone = "9123454321",
                address = "Panaji, Goa"
            )
        )
        val staffUser = UserSession(username = "staff1", displayName = "Staff One", role = "staff")
        composeTestRule.setContent {
            TseBillingTheme {
                CustomersScreen(
                    customers = customers,
                    user = staffUser,
                    onSaveCustomer = { _, _, _, _, _ -> },
                    onDeleteCustomer = {}
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("customer_search_input").performTextInput("54321")
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Amit Verma").assertIsDisplayed()
        composeTestRule.onNodeWithText("Suresh Desai").assertDoesNotExist()
    }

    @Test
    fun helpRequest_autoFillsFromCustomerListByLast5Digits_andFormatsShare() = runTest {
        repo.saveCustomer(
            name = "Rakesh Naik",
            details = "",
            phone = "9876543210",
            address = "Calangute, North Goa",
            locationUrl = "https://maps.google.com/?q=15.5439,73.7553"
        )

        val savedHelp = repo.saveHelpRequest(
            customerName = "",
            phone = "43210",
            address = "",
            locationUrl = "",
            helpDescription = "Camera 3 showing no video signal"
        )
        assertEquals("Rakesh Naik", savedHelp.customerName)
        assertEquals("9876543210", savedHelp.phone)
        assertEquals("Calangute, North Goa", savedHelp.address)
        assertEquals("https://maps.google.com/?q=15.5439,73.7553", savedHelp.locationUrl)
        assertEquals("Camera 3 showing no video signal", savedHelp.helpDescription)

        val msg = LocationHelper.buildHelpShareMessage(
            customerName = savedHelp.customerName,
            phone = savedHelp.phone,
            address = savedHelp.address,
            locationUrl = savedHelp.locationUrl,
            helpDescription = savedHelp.helpDescription
        )
        assertTrue(msg.contains("Rakesh Naik"))
        assertTrue(msg.contains("9876543210"))
        assertTrue(msg.contains("Calangute, North Goa"))
        assertTrue(msg.contains("https://maps.google.com/?q=15.5439,73.7553"))
        assertTrue(msg.contains("Camera 3 showing no video signal"))
    }

    @Test
    fun customerHelpScreen_hidesAdminFormForStaff_autoFillsForAdmin() {
        val customers = listOf(
            CustomerEntity(
                id = 5L,
                name = "Rohan Kamat",
                details = "Mapusa, Goa\n9822012345",
                phone = "9822012345",
                address = "Mapusa, Goa",
                locationUrl = "https://maps.google.com/?q=15.5915,73.8100"
            )
        )
        val requests = listOf(
            CustomerHelpEntity(
                id = 21L,
                customerName = "Rohan Kamat",
                phone = "9822012345",
                address = "Mapusa, Goa",
                locationUrl = "https://maps.google.com/?q=15.5915,73.8100",
                helpDescription = "DVR hard disk beep issue"
            )
        )
        val staffUser = UserSession(username = "staff1", displayName = "Staff One", role = "staff")
        val adminUser = UserSession(username = "admin1", displayName = "Admin", role = "admin")

        var currentUser by mutableStateOf(staffUser)
        composeTestRule.setContent {
            TseBillingTheme {
                CustomerHelpScreen(
                    helpRequests = requests,
                    customers = customers,
                    user = currentUser,
                    onSaveHelpRequest = { _, _, _, _, _, _, _ -> },
                    onToggleStatus = {},
                    onDeleteHelpRequest = {}
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("help_lazy_list").performScrollToIndex(3)
        composeTestRule.onNodeWithText("DVR hard disk beep issue").assertIsDisplayed()
        composeTestRule.onNodeWithTag("call_help_21").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("map_help_21").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("help_phone_input").assertDoesNotExist()
        composeTestRule.onNodeWithTag("save_help_button").assertDoesNotExist()
        composeTestRule.onNodeWithTag("edit_help_21").assertDoesNotExist()
        composeTestRule.onNodeWithTag("delete_help_21").assertDoesNotExist()

        currentUser = adminUser
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("help_lazy_list").performScrollToIndex(1)
        composeTestRule.onNodeWithTag("help_phone_input").assertIsDisplayed()
        composeTestRule.onNodeWithTag("help_phone_input").performTextInput("12345")
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("help_customer_matched_badge").assertIsDisplayed()
        composeTestRule.onNodeWithTag("help_name_input").assertTextContains("Rohan Kamat")
        composeTestRule.onNodeWithTag("help_lazy_list").performScrollToIndex(3)
        composeTestRule.onNodeWithTag("edit_help_21").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("delete_help_21").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun staffViewModel_canAccessSavedCustomersAndHelp_cannotEditHelpOrOpenAdminTabs() {
        kotlinx.coroutines.runBlocking {
            repo.setupAdmin("admin1", "pass1234", "9999")
            repo.addStaff("Staff One", "staff1", "pass1234")
        }
        val context = ApplicationProvider.getApplicationContext<Context>()
        val soundEngine = SoundEngine(context)
        val vm = BillingViewModel(repo, soundEngine)
        vm.login("staff1", "pass1234")

        val deadline = System.currentTimeMillis() + 3000L
        while (vm.session.value == null && System.currentTimeMillis() < deadline) {
            org.robolectric.shadows.ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
            Thread.sleep(20)
        }
        assertNotNull(vm.session.value)

        vm.selectTab(MainTab.SAVED)
        assertEquals(MainTab.SAVED, vm.activeTab.value)

        vm.selectTab(MainTab.CUSTOMERS)
        assertEquals(MainTab.CUSTOMERS, vm.activeTab.value)

        vm.selectTab(MainTab.HELP)
        assertEquals(MainTab.HELP, vm.activeTab.value)

        vm.selectTab(MainTab.PRODUCTS)
        assertEquals(MainTab.HELP, vm.activeTab.value)

        vm.saveHelpRequest(
            customerName = "Test",
            phone = "9876543210",
            address = "Goa",
            locationUrl = "",
            helpDescription = "Staff should not save"
        )
        org.robolectric.shadows.ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
        kotlinx.coroutines.runBlocking {
            assertEquals(0, repo.helpRequests.first().size)
        }
        soundEngine.release()
    }

    @Test
    fun starRating_isImmutable_cannotBeEditedOrDeleted() = runTest {
        repo.setupAdmin("admin1", "pass1234", "9999")
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
            createdBy = "rahul_staff",
            staffRating = 5,
            staffReviewNote = "Excellent work"
        )
        repo.saveBill(ratedBill)

        // Attempt to overwrite starRating/review via updateBillSettlementAndReview
        repo.updateBillSettlementAndReview(
            billId = "b_locked_1",
            discount = 200.0,
            grandTotal = 3800.0,
            paidAmount = 3800.0,
            dueAmount = 0.0,
            staffRating = 1,
            staffReviewNote = "Tampered review"
        )
        val afterSettlement = repo.getBillById("b_locked_1")
        assertNotNull(afterSettlement)
        assertEquals(3800.0, afterSettlement!!.paidAmount, 0.001)
        assertEquals(0.0, afterSettlement.dueAmount, 0.001)
        assertEquals(5, afterSettlement.staffRating)
        assertEquals("Excellent work", afterSettlement.staffReviewNote)

        // Attempt to overwrite starRating via saveBill
        repo.saveBill(afterSettlement.copy(staffRating = 2, staffReviewNote = "Cleared"))
        val afterSave = repo.getBillById("b_locked_1")
        assertEquals(5, afterSave!!.staffRating)
        assertEquals("Excellent work", afterSave.staffReviewNote)

        // Attempt to delete rated bill -> blocked
        val deletedRated = repo.deleteBill("b_locked_1")
        assertFalse(deletedRated)
        assertNotNull(repo.getBillById("b_locked_1"))

        // Unrated bill can still be deleted
        val unratedBill = ratedBill.copy(id = "b_unrated_1", invoiceNo = "TSE-502", staffRating = 0, staffReviewNote = "")
        repo.saveBill(unratedBill)
        val deletedUnrated = repo.deleteBill("b_unrated_1")
        assertTrue(deletedUnrated)
        assertNull(repo.getBillById("b_unrated_1"))
    }

    @Test
    fun savedBillsAndDialog_lockStarRatingUi_andHideDeleteForRatedBill() {
        val ratedBill = BillEntity(
            id = "b_ui_lock",
            invoiceNo = "TSE-601",
            date = "2026-11-20",
            customerName = "Sunil Kumar",
            customerDetails = "Mapusa",
            itemsJson = InvoiceItemJson.toJson(listOf(InvoiceItem(description = "DVR", qty = 1.0, rate = 3000.0))),
            subtotal = 3000.0,
            discount = 0.0,
            grandTotal = 3000.0,
            paidAmount = 3000.0,
            dueAmount = 0.0,
            paymentMode = PaymentMode.ONLINE.label,
            signatureBase64 = "",
            customQrText = "",
            createdBy = "rahul_staff",
            staffRating = 4,
            staffReviewNote = "Good installation"
        )
        var showDialog by mutableStateOf(false)
        composeTestRule.setContent {
            TseBillingTheme {
                SavedBillsScreen(
                    allBills = listOf(ratedBill),
                    filteredBills = listOf(ratedBill),
                    searchQuery = "",
                    company = CompanySettings(),
                    upi = UpiSettings(),
                    isAdmin = true,
                    onSearchChange = {},
                    onOpenBill = {},
                    onOpenPaymentReview = { showDialog = true },
                    onPrintBill = {},
                    onSavePdf = {},
                    onSharePdf = {},
                    onDeleteBill = {}
                )
                if (showDialog) {
                    PaymentAndReviewDialog(
                        bill = ratedBill,
                        onDismiss = { showDialog = false },
                        onSave = { _, _, _, _, _ -> }
                    )
                }
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Locked").assertIsDisplayed()
        composeTestRule.onNodeWithTag("delete_saved_TSE-601").assertDoesNotExist()
        composeTestRule.onNodeWithTag("review_bill_TSE-601").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("staff_star_1").assertIsNotEnabled()
        composeTestRule.onNodeWithTag("staff_star_5").assertIsNotEnabled()
        composeTestRule.onNodeWithTag("staff_review_note_input").assertIsNotEnabled()
        composeTestRule.onNodeWithText("Update Payment").assertIsDisplayed()
    }

    @Test
    fun forgotAdminPassword_withManoj2007_resetsAdminPasswordAndFinalCode() = runTest {
        repo.setupAdmin("admin1", "oldPass123", "1111")
        assertNotNull(repo.authenticate("admin1", "oldPass123"))

        val wrongRecovery = repo.resetAdminPasswordWithRecovery(
            recoveryPassword = "WrongPassword",
            targetAdminUsername = "admin1",
            newPassword = "newPass456",
            newFinalCode = "2222"
        )
        assertTrue(wrongRecovery.isFailure)
        assertNotNull(repo.authenticate("admin1", "oldPass123"))

        val okRecovery = repo.resetAdminPasswordWithRecovery(
            recoveryPassword = BillingRepository.DEFAULT_ADMIN_RECOVERY_PASSWORD,
            targetAdminUsername = "admin1",
            newPassword = "newPass456",
            newFinalCode = "2222"
        )
        assertTrue(okRecovery.isSuccess)
        assertNull(repo.authenticate("admin1", "oldPass123"))
        val newSession = repo.authenticate("admin1", "newPass456")
        assertNotNull(newSession)
        assertTrue(newSession!!.isAdmin)
        assertTrue(repo.verifyFinalCode("2222"))
        assertFalse(repo.verifyFinalCode("1111"))
    }

    @Test
    fun authScreen_showsPreSetupBackupRestore_andForgotPasswordDialog() {
        var isConfigured by mutableStateOf<Boolean?>(false)
        val accounts = listOf(
            UserAccountEntity(username = "admin1", displayName = "Admin", role = "admin", passwordHash = "hash")
        )
        composeTestRule.setContent {
            TseBillingTheme {
                AuthScreen(
                    isAdminConfigured = isConfigured,
                    accounts = accounts,
                    authError = "",
                    onClearError = {},
                    onSetupAdmin = { _, _, _, _ -> },
                    onLogin = { _, _ -> },
                    onImportBackup = {},
                    onResetAdminPassword = { _, _, _, _, _, _ -> }
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("setup_import_backup_button").performScrollTo().assertIsDisplayed()

        isConfigured = true
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("forgot_password_button").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("forgot_password_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("forgot_password_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithTag("forgot_master_password_input").assertIsDisplayed()
        composeTestRule.onNodeWithTag("reset_admin_password_button").assertIsDisplayed()
    }

    @Test
    fun fullBackupExportAndImport_restoresBillsAdminPasswordStaffFinalCodeAndSettings() = runTest {
        repo.setupAdmin("admin_tse", "AdminPass99", "4321")
        repo.addStaff("Rahul Verma", "rahul_staff", "StaffPass99")
        repo.saveCompanySettings(
            CompanySettings(
                companyName = "TOTAL SOLUTION ENTERPRISES PRO",
                mobile = "7083805120",
                email = "tse@example.com",
                address = "Goa"
            )
        )
        repo.saveAuthorisedSignatureBase64("auth_sig_b64")
        repo.saveUpiSettings("7083805120@ybl", "Manoj Saha")
        repo.saveBill(
            BillEntity(
                id = "b_exp_1",
                invoiceNo = "TSE-777",
                date = "2026-11-20",
                customerName = "Backup Customer",
                customerDetails = "Panaji\n9876543210",
                itemsJson = InvoiceItemJson.toJson(listOf(InvoiceItem(description = "Camera", qty = 2.0, rate = 1500.0))),
                subtotal = 3000.0,
                discount = 100.0,
                grandTotal = 2900.0,
                paidAmount = 2000.0,
                dueAmount = 900.0,
                paymentMode = PaymentMode.ONLINE.label,
                signatureBase64 = "cust_sig",
                customQrText = "",
                createdBy = "rahul_staff",
                staffRating = 5,
                staffReviewNote = "Super fast"
            ),
            customerLocationUrl = "https://maps.google.com/?q=15.49,73.82"
        )
        repo.saveHelpRequest(
            customerName = "Backup Customer",
            phone = "9876543210",
            address = "Panaji",
            locationUrl = "https://maps.google.com/?q=15.49,73.82",
            helpDescription = "Cable check"
        )

        val jsonBackup = repo.exportBackupJson()

        // Restore into a completely fresh empty Room database
        val context = ApplicationProvider.getApplicationContext<Context>()
        val freshDb = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val freshRepo = BillingRepository(freshDb)
            assertEquals(false, freshRepo.isAdminConfigured.first())

            val importResult = freshRepo.importBackupJson(jsonBackup)
            assertTrue(importResult.isSuccess)
            val summary = importResult.getOrNull()!!
            assertEquals(1, summary.billsCount)
            assertEquals(1, summary.customersCount)
            assertEquals(2, summary.accountsCount)
            assertEquals(1, summary.helpCount)

            assertEquals(true, freshRepo.isAdminConfigured.first())
            assertNotNull(freshRepo.authenticate("admin_tse", "AdminPass99"))
            assertNotNull(freshRepo.authenticate("rahul_staff", "StaffPass99"))
            assertTrue(freshRepo.verifyFinalCode("4321"))

            val restoredBill = freshRepo.getBillById("b_exp_1")
            assertNotNull(restoredBill)
            assertEquals(5, restoredBill!!.staffRating)
            assertEquals("Super fast", restoredBill.staffReviewNote)
            assertEquals("TOTAL SOLUTION ENTERPRISES PRO", freshRepo.companySettings.first().companyName)
            assertEquals("auth_sig_b64", freshRepo.companySettings.first().authorisedSignatureBase64)
            assertEquals("7083805120@ybl", freshRepo.upiSettings.first().upiId)
        } finally {
            freshDb.close()
        }
    }

    @Test
    fun mainActivity_createsAndRendersContent() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        assertNotNull(activity)
        controller.pause().stop().destroy()
    }
}
