package com.example.ui

import android.graphics.Bitmap
import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.AppLanguage
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
    ADMIN("admin"),
    SETTINGS("settings")
}

data class BillDraft(
    val editingId: String? = null,
    val openedFromSaved: Boolean = false,
    val createdBy: String = "",
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
    val showSheetPreview: Boolean = false
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

    private val _finalModalVisible = MutableStateFlow(false)
    val finalModalVisible: StateFlow<Boolean> = _finalModalVisible.asStateFlow()

    private val _finalModalError = MutableStateFlow("")
    val finalModalError: StateFlow<String> = _finalModalError.asStateFlow()

    private val _exportOptionsPayload = MutableStateFlow<InvoicePrintPayload?>(null)
    val exportOptionsPayload: StateFlow<InvoicePrintPayload?> = _exportOptionsPayload.asStateFlow()

    private val _events = MutableSharedFlow<BillingEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<BillingEvent> = _events.asSharedFlow()

    val isAdminConfigured: StateFlow<Boolean?> = repository.isAdminConfigured
        .map<Boolean, Boolean?> { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val accounts: StateFlow<List<UserAccountEntity>> = repository.accounts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val bills: StateFlow<List<BillEntity>> = repository.bills
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredBills: StateFlow<List<BillEntity>> = combine(bills, _billSearchQuery) { all, q ->
        val query = q.trim().lowercase(Locale.ROOT)
        if (query.isEmpty()) all
        else all.filter {
            it.invoiceNo.lowercase(Locale.ROOT).contains(query) ||
                it.customerName.lowercase(Locale.ROOT).contains(query) ||
                it.customerDetails.lowercase(Locale.ROOT).contains(query) ||
                it.createdBy.lowercase(Locale.ROOT).contains(query)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val customers: StateFlow<List<CustomerEntity>> = repository.customers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _customerSearchQuery = MutableStateFlow("")
    val customerSearchQuery: StateFlow<String> = _customerSearchQuery.asStateFlow()

    val filteredCustomers: StateFlow<List<CustomerEntity>> = combine(customers, _customerSearchQuery) { all, q ->
        BillingRepository.filterCustomers(all, q)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val helpRequests: StateFlow<List<CustomerHelpEntity>> = repository.helpRequests
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val products: StateFlow<List<ProductEntity>> = repository.products
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val companySettings: StateFlow<CompanySettings> = repository.companySettings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CompanySettings())

    val upiSettings: StateFlow<UpiSettings> = repository.upiSettings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UpiSettings())

    val soundEnabled: StateFlow<Boolean> = repository.soundEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val language: StateFlow<AppLanguage> = repository.language
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppLanguage.EN)

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
            delay(1150)
            _splashVisible.value = false
        }
    }

    fun dismissSplash() {
        _splashVisible.value = false
    }

    fun speakLoginWelcome() {
        soundEngine.speakEnglish("Welcome to Total Solution Bill system. Select username and enter password.")
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

    fun setLanguage(lang: AppLanguage) {
        viewModelScope.launch {
            repository.setLanguage(lang)
            soundEngine.play(SoundCue.CLICK)
        }
    }

    fun selectTab(tab: MainTab) {
        val currentSession = _session.value ?: return
        val staffAllowed = tab == MainTab.BILL || tab == MainTab.SAVED || tab == MainTab.CUSTOMERS || tab == MainTab.HELP
        if (!staffAllowed && (currentSession.isStaff || !currentSession.isAdmin)) {
            soundEngine.play(SoundCue.ERROR)
            emitToast("Admin access only", isError = true)
            return
        }
        soundEngine.play(SoundCue.CLICK)
        _activeTab.value = tab
    }

    /* ===== Authentication ===== */
    fun clearAuthError() {
        _authError.value = ""
    }

    fun setupAdmin(username: String, password: String, confirmPassword: String, finalCode: String) {
        val cleanUser = username.trim()
        if (cleanUser.length < 3) {
            _authError.value = "Admin username kam se kam 3 characters ka hona chahiye."
            soundEngine.play(SoundCue.ERROR)
            return
        }
        if (password.length < 6) {
            _authError.value = "Admin password kam se kam 6 characters ka rakhein."
            soundEngine.play(SoundCue.ERROR)
            return
        }
        if (password != confirmPassword) {
            _authError.value = "Dono password match nahi kar rahe."
            soundEngine.play(SoundCue.ERROR)
            return
        }
        if (finalCode.length < 4) {
            _authError.value = "Final Bill Code kam se kam 4 characters ka rakhein."
            soundEngine.play(SoundCue.ERROR)
            return
        }
        viewModelScope.launch {
            repository.setupAdmin(cleanUser, password, finalCode)
            _authError.value = ""
            soundEngine.play(SoundCue.SAVE)
            emitToast("Admin account created. Ab login karein.")
        }
    }

    fun login(username: String, password: String) {
        if (username.isBlank()) {
            _authError.value = "Username select karein."
            soundEngine.play(SoundCue.ERROR)
            return
        }
        if (password.isBlank()) {
            _authError.value = "Password enter karein."
            soundEngine.play(SoundCue.ERROR)
            return
        }
        viewModelScope.launch {
            val user = repository.authenticate(username, password)
            if (user == null) {
                _authError.value = "Username ya password galat hai."
                soundEngine.play(SoundCue.ERROR)
            } else {
                _authError.value = ""
                _session.value = user
                _activeTab.value = MainTab.BILL
                soundEngine.play(SoundCue.SAVE)
                emitToast("Login successful — Welcome ${user.displayName}")
                soundEngine.speak("लॉगिन सफल हुआ। टोटल सॉल्यूशन एंटरप्राइजेज में स्वागत है।")
            }
        }
    }

    fun logout() {
        _session.value = null
        _finalModalVisible.value = false
        _authError.value = ""
        soundEngine.play(SoundCue.CLICK)
    }

    /* ===== Bill Editor ===== */
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
        if (current.size >= 11) {
            soundEngine.play(SoundCue.ERROR)
            emitToast("Maximum 11 items allowed in one bill", isError = true)
            return
        }
        soundEngine.play(SoundCue.ADD)
        _billDraft.update { it.copy(items = it.items + InvoiceItem()) }
    }

    fun selectProductForRow(index: Int, product: ProductEntity) {
        soundEngine.play(SoundCue.CLICK)
        _billDraft.update { draft ->
            val updated = draft.items.toMutableList()
            if (index in updated.indices) {
                val cur = updated[index]
                updated[index] = cur.copy(
                    description = product.name,
                    unit = product.unit,
                    rate = product.defaultRate
                )
            }
            draft.copy(items = updated)
        }
    }

    fun updateItemFields(index: Int, description: String? = null, qty: Double? = null, unit: String? = null, rate: Double? = null) {
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

    fun saveBill(silent: Boolean = false, onSaved: (() -> Unit)? = null) {
        val currentUser = _session.value ?: return
        val draft = _billDraft.value
        if (draft.openedFromSaved && !currentUser.isAdmin) {
            soundEngine.play(SoundCue.ERROR)
            emitToast("Only Admin can edit saved bills", isError = true)
            return
        }
        if (draft.invoiceNo.isBlank()) {
            soundEngine.play(SoundCue.ERROR)
            emitToast("Invoice number enter karein", isError = true)
            return
        }
        val billId = draft.editingId ?: "b_${System.currentTimeMillis()}"
        val billCreator = if (draft.editingId != null && draft.createdBy.isNotBlank()) {
            draft.createdBy
        } else {
            currentUser.username
        }
        val entity = BillEntity(
            id = billId,
            invoiceNo = draft.invoiceNo.trim(),
            date = draft.date.trim(),
            customerName = draft.customerName.trim(),
            customerDetails = draft.customerDetails.trim(),
            itemsJson = InvoiceItemJson.toJson(draft.items),
            subtotal = draft.subtotal,
            discount = draft.discount,
            grandTotal = draft.grandTotal,
            paidAmount = draft.paidAmount,
            dueAmount = draft.dueAmount,
            paymentMode = draft.paymentMode.label,
            signatureBase64 = draft.signatureBase64,
            customQrText = draft.customQrText.trim(),
            createdBy = billCreator,
            updatedAt = System.currentTimeMillis(),
            extraChargeType = draft.extraChargeType.name,
            extraChargeAmount = draft.extraChargeAmount
        )
        viewModelScope.launch {
            repository.saveBill(entity, draft.customerLocationUrl)
            _billDraft.update { it.copy(editingId = billId, createdBy = billCreator) }
            if (!silent) {
                soundEngine.play(SoundCue.SAVE)
                emitToast("Bill saved & customer updated (By @$billCreator)")
                val amtSpeech = formatAmountForSpeech(entity.grandTotal)
                val dueClause = if (entity.dueAmount > 0.009) {
                    " Due amount is ${formatAmountForSpeech(entity.dueAmount)} rupees."
                } else {
                    ""
                }
                soundEngine.speakEnglish(
                    "Bill saved. Total amount is $amtSpeech rupees.$dueClause Thank you for choosing Total Solution Enterprises."
                )
            }
            onSaved?.invoke()
        }
    }

    fun openBillForEdit(bill: BillEntity) {
        val currentUser = _session.value ?: return
        if (currentUser.isStaff || !currentUser.isAdmin) {
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
            customQrText = bill.customQrText
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

    private fun billToPayload(bill: BillEntity): InvoicePrintPayload {
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
            extraChargeAmount = bill.extraChargeAmount
        )
    }

    fun deleteBill(id: String) {
        val currentUser = _session.value ?: return
        if (currentUser.isStaff || !currentUser.isAdmin) {
            soundEngine.play(SoundCue.ERROR)
            emitToast("Only Admin can delete bills", isError = true)
            return
        }
        viewModelScope.launch {
            repository.deleteBill(id)
            if (_billDraft.value.editingId == id) {
                _billDraft.update { it.copy(editingId = null) }
            }
            soundEngine.play(SoundCue.DELETE)
            emitToast("Bill deleted")
        }
    }

    fun updateBillSearch(q: String) {
        _billSearchQuery.value = q
    }

    /* ===== Final Bill Verification & PDF Print ===== */
    fun openFinalCodeModal() {
        if (_session.value == null) return
        soundEngine.play(SoundCue.CLICK)
        _finalModalError.value = ""
        _finalModalVisible.value = true
    }

    fun closeFinalCodeModal() {
        _finalModalVisible.value = false
        _finalModalError.value = ""
    }

    fun confirmFinalBill(code: String) {
        if (code.isBlank()) {
            _finalModalError.value = "Admin Final Bill Code enter karein."
            soundEngine.play(SoundCue.ERROR)
            return
        }
        val draft = _billDraft.value
        if (draft.invoiceNo.isBlank()) {
            _finalModalError.value = "Invoice number enter karein."
            soundEngine.play(SoundCue.ERROR)
            return
        }
        viewModelScope.launch {
            val valid = repository.verifyFinalCode(code)
            if (!valid) {
                _finalModalError.value = "Code galat hai. Admin se current code lein."
                soundEngine.play(SoundCue.ERROR)
                return@launch
            }
            _finalModalVisible.value = false
            _finalModalError.value = ""
            saveBill(silent = true) {
                soundEngine.play(SoundCue.SAVE)
                val totalSpeech = formatAmountForSpeech(draft.grandTotal)
                val dueClause = if (draft.dueAmount > 0.009) {
                    " Due amount is ${formatAmountForSpeech(draft.dueAmount)} rupees."
                } else {
                    " Paid in full."
                }
                soundEngine.speakEnglish(
                    "Bill generated. Total amount is $totalSpeech rupees.$dueClause Thank you for choosing Total Solution Enterprises."
                )
                val payload = InvoicePrintPayload(
                    company = companySettings.value,
                    upi = upiSettings.value,
                    invoiceNo = draft.invoiceNo,
                    date = draft.date,
                    customerName = draft.customerName,
                    customerDetails = draft.customerDetails,
                    items = draft.items,
                    subtotal = draft.subtotal,
                    discount = draft.discount,
                    grandTotal = draft.grandTotal,
                    paidAmount = draft.paidAmount,
                    dueAmount = draft.dueAmount,
                    paymentMode = draft.paymentMode,
                    signatureBase64 = draft.signatureBase64,
                    customQrText = draft.customQrText,
                    extraChargeType = draft.extraChargeType,
                    extraChargeAmount = draft.extraChargeAmount
                )
                _exportOptionsPayload.value = payload
            }
        }
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
        _exportOptionsPayload.value = null
        _events.tryEmit(BillingEvent.PrintPdf(payload))
    }

    fun triggerSavePdf(payload: InvoicePrintPayload) {
        soundEngine.play(SoundCue.CLICK)
        _exportOptionsPayload.value = null
        _events.tryEmit(BillingEvent.SavePdf(payload))
    }

    fun triggerSharePdf(payload: InvoicePrintPayload) {
        soundEngine.play(SoundCue.CLICK)
        _exportOptionsPayload.value = null
        _events.tryEmit(BillingEvent.SharePdf(payload))
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

    /* ===== Customers & Products (Admin Only Edit/Delete) ===== */
    fun saveCustomer(
        name: String,
        phone: String,
        address: String,
        locationUrl: String,
        id: Long = 0L
    ) {
        val current = _session.value
        if (current == null || current.isStaff || !current.isAdmin) {
            soundEngine.play(SoundCue.ERROR)
            emitToast("Admin access only — Staff cannot edit customers", isError = true)
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
        if (current == null || current.isStaff || !current.isAdmin) {
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

    /* ===== Customer Help / WhatsApp Service Desk (Admin Edit, Staff View) ===== */
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
        if (current == null || current.isStaff || !current.isAdmin) {
            soundEngine.play(SoundCue.ERROR)
            emitToast("Admin access only — Staff cannot edit Help Requests", isError = true)
            return
        }
        if (helpDescription.isBlank()) {
            soundEngine.play(SoundCue.ERROR)
            emitToast("Customer need help description enter karein", isError = true)
            return
        }
        if (customerName.isBlank() && phone.isBlank()) {
            soundEngine.play(SoundCue.ERROR)
            emitToast("Customer name ya mobile number enter karein", isError = true)
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
            emitToast(if (id > 0L) "Help request updated" else "Help request saved for Staff")
            onSaved?.invoke(saved)
        }
    }

    fun toggleHelpRequestStatus(request: CustomerHelpEntity) {
        val current = _session.value
        if (current == null || current.isStaff || !current.isAdmin) {
            soundEngine.play(SoundCue.ERROR)
            emitToast("Admin access only", isError = true)
            return
        }
        val nextStatus = if (request.status == "RESOLVED") "OPEN" else "RESOLVED"
        viewModelScope.launch {
            repository.updateHelpStatus(request, nextStatus)
            soundEngine.play(SoundCue.CLICK)
            emitToast("Marked as $nextStatus")
        }
    }

    fun deleteHelpRequest(id: Long) {
        val current = _session.value
        if (current == null || current.isStaff || !current.isAdmin) {
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

    fun addProduct(name: String, unit: String, rateText: String) {
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
            repository.addProduct(name, unit, rateText.toDoubleOrNull() ?: 0.0)
            soundEngine.play(SoundCue.SAVE)
            emitToast("Product added")
        }
    }

    fun updateProduct(id: Long, name: String, unit: String, rateText: String) {
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
            repository.updateProduct(id, name, unit, rateText.toDoubleOrNull() ?: 0.0)
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

    /* ===== Admin Panel & Settings ===== */
    fun saveUpiSettings(upiId: String, payeeName: String) {
        if (_session.value?.isAdmin != true) return
        val cleanId = upiId.trim()
        if (cleanId.isEmpty() || !cleanId.contains("@")) {
            soundEngine.play(SoundCue.ERROR)
            emitToast("Valid UPI ID enter karein (jaise 7083805120@ybl)", isError = true)
            return
        }
        viewModelScope.launch {
            repository.saveUpiSettings(cleanId, payeeName)
            soundEngine.play(SoundCue.SAVE)
            emitToast("UPI ID saved & Payment QR generated!")
            soundEngine.speak("यूपीआई आईडी सेव हो गई है और पेमेंट क्यूआर कोड बन गया है।")
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

    fun changeFinalCode(newCode: String, confirmCode: String) {
        if (_session.value?.isAdmin != true) return
        if (newCode.length < 4) {
            soundEngine.play(SoundCue.ERROR)
            emitToast("Final Bill Code minimum 4 characters ka rakhein", isError = true)
            return
        }
        if (newCode != confirmCode) {
            soundEngine.play(SoundCue.ERROR)
            emitToast("Dono code match nahi kar rahe", isError = true)
            return
        }
        viewModelScope.launch {
            repository.updateFinalCode(newCode)
            soundEngine.play(SoundCue.SAVE)
            emitToast("Final Bill Code successfully updated")
        }
    }

    fun addStaff(name: String, username: String, password: String, onSuccess: () -> Unit) {
        if (_session.value?.isAdmin != true) return
        if (name.trim().length < 2) {
            soundEngine.play(SoundCue.ERROR)
            emitToast("Enter staff full name", isError = true)
            return
        }
        if (username.trim().length < 3) {
            soundEngine.play(SoundCue.ERROR)
            emitToast("Staff username minimum 3 characters", isError = true)
            return
        }
        if (password.length < 6) {
            soundEngine.play(SoundCue.ERROR)
            emitToast("Staff password minimum 6 characters rakhein", isError = true)
            return
        }
        viewModelScope.launch {
            val res = repository.addStaff(name, username, password)
            if (res.isSuccess) {
                soundEngine.play(SoundCue.SAVE)
                emitToast("Staff account created")
                onSuccess()
            } else {
                soundEngine.play(SoundCue.ERROR)
                emitToast(res.exceptionOrNull()?.message ?: "Could not add staff", isError = true)
            }
        }
    }

    fun removeStaff(username: String) {
        if (_session.value?.isAdmin != true) return
        viewModelScope.launch {
            repository.removeStaff(username)
            soundEngine.play(SoundCue.DELETE)
            emitToast("Staff removed")
        }
    }

    fun saveCompanySettings(settings: CompanySettings) {
        val current = _session.value
        if (current == null || current.isStaff || !current.isAdmin) {
            soundEngine.play(SoundCue.ERROR)
            emitToast("Admin access only", isError = true)
            return
        }
        viewModelScope.launch {
            repository.saveCompanySettings(settings)
            soundEngine.play(SoundCue.SAVE)
            emitToast("Company settings saved & updated")
        }
    }

    fun saveFallbackQrBitmap(bitmap: Bitmap) {
        val current = _session.value
        if (current == null || current.isStaff || !current.isAdmin) {
            soundEngine.play(SoundCue.ERROR)
            emitToast("Admin access only", isError = true)
            return
        }
        viewModelScope.launch {
            val out = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 90, out)
            val b64 = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
            repository.saveFallbackQrBase64(b64)
            soundEngine.play(SoundCue.SAVE)
            emitToast("Custom QR image saved")
        }
    }

    suspend fun exportBackupJson(): String = repository.exportBackupJson()

    fun importBackupJson(json: String) {
        val current = _session.value
        if (current == null || current.isStaff || !current.isAdmin) {
            soundEngine.play(SoundCue.ERROR)
            emitToast("Admin access only", isError = true)
            return
        }
        viewModelScope.launch {
            val res = repository.importBackupJson(json)
            if (res.isSuccess) {
                soundEngine.play(SoundCue.SAVE)
                emitToast("Backup imported successfully")
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
