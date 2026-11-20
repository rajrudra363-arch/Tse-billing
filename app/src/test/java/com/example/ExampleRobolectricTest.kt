package com.example

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.BillEntity
import com.example.data.BillingRepository
import com.example.data.CompanySettings
import com.example.data.InvoiceItem
import com.example.data.InvoiceItemJson
import com.example.data.PaymentMode
import com.example.data.ProductEntity
import com.example.data.UpiSettings
import com.example.data.UserSession
import com.example.ui.BillDraft
import com.example.ui.screens.BillScreen
import com.example.ui.screens.ProductsScreen
import com.example.ui.screens.SavedBillsScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.TseBillingTheme
import com.example.util.InvoicePdfPrinter
import com.example.util.InvoicePrintPayload
import com.example.util.QrGenerator
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
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
    fun a4InvoiceRenderer_drawsSinglePageA4Canvas() {
        assertEquals(595, InvoicePdfPrinter.A4_WIDTH_PT)
        assertEquals(842, InvoicePdfPrinter.A4_HEIGHT_PT)

        val items = (1..11).map { idx ->
            InvoiceItem(description = "CCTV Item $idx", qty = 2.0, unit = "Nos", rate = 1250.0)
        }
        val payload = InvoicePrintPayload(
            company = CompanySettings(),
            upi = UpiSettings(upiId = "7083805120@ybl", payeeName = "Manoj Kumar Saha"),
            invoiceNo = "TSE-009",
            date = "2026-11-19",
            customerName = "Test Customer",
            customerDetails = "Calangute, Goa\n9876543210",
            items = items,
            subtotal = 27500.0,
            discount = 500.0,
            grandTotal = 27000.0,
            paidAmount = 20000.0,
            dueAmount = 7000.0,
            paymentMode = PaymentMode.ONLINE,
            signatureBase64 = "",
            customQrText = "DVR Admin Pass: 1234"
        )
        val bitmap = android.graphics.Bitmap.createBitmap(
            InvoicePdfPrinter.A4_WIDTH_PT,
            InvoicePdfPrinter.A4_HEIGHT_PT,
            android.graphics.Bitmap.Config.ARGB_8888
        )
        val canvas = android.graphics.Canvas(bitmap)
        InvoicePdfPrinter.drawInvoicePage(canvas, payload)
        assertEquals(595, bitmap.width)
        assertEquals(842, bitmap.height)
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
    fun settingsScreen_hidesEditAndSaveForStaff_showsForAdmin() {
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
                    onExportBackup = { "{}" },
                    onImportBackup = {}
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("settings_company_name").assertDoesNotExist()
        composeTestRule.onNodeWithTag("save_settings_button").assertDoesNotExist()
        composeTestRule.onNodeWithTag("pick_qr_image_button").assertDoesNotExist()
        composeTestRule.onNodeWithTag("import_backup_button").assertDoesNotExist()

        currentUser = adminUser
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("settings_company_name").assertIsDisplayed()
        composeTestRule.onNodeWithTag("save_settings_button").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun billScreen_showsStaffUsernameInVerificationModal_andRendersA4Preview() {
        val staffUser = UserSession(username = "rahul_staff", displayName = "Rahul Verma", role = "staff")
        val draft = BillDraft(
            customerName = "Ramesh Sharma",
            customerDetails = "Panaji, Goa\n9876543210",
            invoiceNo = "TSE-101",
            items = listOf(InvoiceItem(description = "2MP Dome Camera", qty = 2.0, unit = "Nos", rate = 1200.0)),
            showSheetPreview = true
        )
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
                    finalModalVisible = true,
                    finalModalError = "",
                    exportOptionsPayload = null,
                    onCustomerNameChange = {},
                    onCustomerSelected = {},
                    onCustomerDetailsChange = {},
                    onInvoiceNoChange = {},
                    onInvoiceDateChange = {},
                    onAddItem = {},
                    onSelectProductForRow = { _, _ -> },
                    onUpdateItem = { _, _, _, _, _ -> },
                    onRemoveItem = {},
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
        composeTestRule.onNodeWithTag("bill_creator_badge").assertTextContains("@rahul_staff", substring = true)
        composeTestRule.onNodeWithText("LIVE A4 INVOICE SHEET PREVIEW").assertIsDisplayed()
        composeTestRule.onNodeWithText("TAX INVOICE").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Customer Signature").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun savedBillsScreen_displaysCreatorUsername_excludedFromPrintPayload() {
        val sampleBill = BillEntity(
            id = "b_1",
            invoiceNo = "TSE-055",
            date = "2026-11-19",
            customerName = "Deepak Patil",
            customerDetails = "Mapusa",
            itemsJson = InvoiceItemJson.toJson(listOf(InvoiceItem(description = "4 Ch DVR", qty = 1.0, rate = 3500.0))),
            subtotal = 3500.0,
            discount = 0.0,
            grandTotal = 3500.0,
            paidAmount = 3500.0,
            dueAmount = 0.0,
            paymentMode = PaymentMode.ONLINE.label,
            signatureBase64 = "",
            customQrText = "",
            createdBy = "rahul_staff",
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
    }

    @Test
    fun mainActivity_createsAndRendersContent() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        assertNotNull(activity)
        controller.pause().stop().destroy()
    }
}
