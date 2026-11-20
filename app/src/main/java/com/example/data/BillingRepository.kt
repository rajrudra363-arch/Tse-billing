package com.example.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.Locale

object AuthHasher {
    fun sha256(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}

class BillingRepository(private val db: AppDatabase) {
    private val billDao = db.billDao()
    private val customerDao = db.customerDao()
    private val productDao = db.productDao()
    private val userDao = db.userAccountDao()
    private val configDao = db.appConfigDao()
    private val helpDao = db.customerHelpDao()

    val bills: Flow<List<BillEntity>> = billDao.observeAll()
    val customers: Flow<List<CustomerEntity>> = customerDao.observeAll()
    val products: Flow<List<ProductEntity>> = productDao.observeAll()
    val accounts: Flow<List<UserAccountEntity>> = userDao.observeAll()
    val helpRequests: Flow<List<CustomerHelpEntity>> = helpDao.observeAll()

    val configMap: Flow<Map<String, String>> = configDao.observeAll().map { list ->
        list.associate { it.key to it.value }
    }

    val companySettings: Flow<CompanySettings> = configMap.map { m ->
        CompanySettings(
            companyName = m[KEY_COMPANY_NAME]?.takeIf { it.isNotBlank() } ?: DEFAULT_COMPANY_NAME,
            mobile = m[KEY_COMPANY_MOBILE]?.takeIf { it.isNotBlank() } ?: DEFAULT_COMPANY_MOBILE,
            email = m[KEY_COMPANY_EMAIL]?.takeIf { it.isNotBlank() } ?: DEFAULT_COMPANY_EMAIL,
            address = m[KEY_COMPANY_ADDRESS]?.takeIf { it.isNotBlank() } ?: DEFAULT_COMPANY_ADDRESS
        )
    }

    val upiSettings: Flow<UpiSettings> = configMap.map { m ->
        UpiSettings(
            upiId = m[KEY_UPI_ID] ?: "",
            payeeName = m[KEY_UPI_NAME]?.takeIf { it.isNotBlank() } ?: DEFAULT_PAYEE_NAME,
            fallbackQrBase64 = m[KEY_FALLBACK_QR] ?: ""
        )
    }

    val isAdminConfigured: Flow<Boolean> = combine(accounts, configMap) { users, cfg ->
        val hasAdmin = users.any { it.role.equals("admin", ignoreCase = true) }
        val hasFinalCode = !cfg[KEY_FINAL_CODE_HASH].isNullOrBlank()
        hasAdmin && hasFinalCode
    }

    val soundEnabled: Flow<Boolean> = configMap.map { m ->
        m[KEY_SOUND_ENABLED] != "0"
    }

    val language: Flow<AppLanguage> = configMap.map { m ->
        AppLanguage.fromCode(m[KEY_LANGUAGE] ?: "en")
    }

    suspend fun seedDefaultsIfNeeded() {
        val seeded = configDao.getValue(KEY_SEEDED) == "1"
        if (!seeded && productDao.getAll().isEmpty()) {
            productDao.insertAll(STARTER_PRODUCTS.map { ProductEntity(name = it, unit = "Nos", defaultRate = 0.0) })
            configDao.setAll(
                listOf(
                    AppConfigEntity(KEY_SEEDED, "1"),
                    AppConfigEntity(KEY_COMPANY_NAME, DEFAULT_COMPANY_NAME),
                    AppConfigEntity(KEY_COMPANY_MOBILE, DEFAULT_COMPANY_MOBILE),
                    AppConfigEntity(KEY_COMPANY_EMAIL, DEFAULT_COMPANY_EMAIL),
                    AppConfigEntity(KEY_COMPANY_ADDRESS, DEFAULT_COMPANY_ADDRESS),
                    AppConfigEntity(KEY_UPI_NAME, DEFAULT_PAYEE_NAME),
                    AppConfigEntity(KEY_SOUND_ENABLED, "1"),
                    AppConfigEntity(KEY_LANGUAGE, "en")
                )
            )
        }
    }

    suspend fun setupAdmin(username: String, password: String, finalCode: String) {
        val cleanUser = username.trim()
        userDao.upsert(
            UserAccountEntity(
                username = cleanUser,
                displayName = "Admin",
                passwordHash = AuthHasher.sha256(password),
                role = "admin"
            )
        )
        configDao.setValue(AppConfigEntity(KEY_FINAL_CODE_HASH, AuthHasher.sha256(finalCode)))
    }

    suspend fun authenticate(username: String, password: String): UserSession? {
        val account = userDao.findByUsername(username.trim()) ?: return null
        val hash = AuthHasher.sha256(password)
        return if (account.passwordHash == hash) {
            UserSession(
                username = account.username,
                displayName = account.displayName.ifBlank { account.username },
                role = account.role
            )
        } else {
            null
        }
    }

    suspend fun verifyFinalCode(code: String): Boolean {
        val stored = configDao.getValue(KEY_FINAL_CODE_HASH) ?: return false
        return stored == AuthHasher.sha256(code)
    }

    suspend fun updateFinalCode(newCode: String) {
        configDao.setValue(AppConfigEntity(KEY_FINAL_CODE_HASH, AuthHasher.sha256(newCode)))
    }

    suspend fun addStaff(displayName: String, username: String, password: String): Result<Unit> {
        val existing = userDao.getAll()
        val staffCount = existing.count { it.role.equals("staff", ignoreCase = true) }
        if (staffCount >= 5) {
            return Result.failure(IllegalStateException("Maximum 5 staff accounts allowed"))
        }
        val cleanUser = username.trim()
        if (existing.any { it.username.equals(cleanUser, ignoreCase = true) }) {
            return Result.failure(IllegalStateException("Username already exists"))
        }
        userDao.upsert(
            UserAccountEntity(
                username = cleanUser,
                displayName = displayName.trim(),
                passwordHash = AuthHasher.sha256(password),
                role = "staff"
            )
        )
        return Result.success(Unit)
    }

    suspend fun removeStaff(username: String) {
        userDao.deleteByUsername(username)
    }

    suspend fun saveBill(bill: BillEntity, customerLocationUrl: String = "") {
        billDao.upsert(bill)
        val custName = bill.customerName.trim()
        if (custName.isNotEmpty()) {
            val old = customerDao.findByName(custName)
            val (parsedPhone, parsedAddress) = parsePhoneAndAddress(bill.customerDetails)
            val cleanLoc = customerLocationUrl.trim()
            if (old != null) {
                customerDao.upsert(
                    old.copy(
                        details = bill.customerDetails.ifBlank { old.details },
                        phone = parsedPhone.ifBlank { old.phone },
                        address = parsedAddress.ifBlank { old.address },
                        locationUrl = cleanLoc.ifBlank { old.locationUrl }
                    )
                )
            } else {
                customerDao.upsert(
                    CustomerEntity(
                        name = custName,
                        details = bill.customerDetails,
                        phone = parsedPhone,
                        address = parsedAddress,
                        locationUrl = cleanLoc
                    )
                )
            }
        }
    }

    suspend fun deleteBill(id: String) = billDao.deleteById(id)

    suspend fun saveCustomer(
        name: String,
        details: String,
        phone: String = "",
        address: String = "",
        locationUrl: String = "",
        id: Long = 0L
    ) {
        val clean = name.trim()
        val cleanPhone = phone.trim()
        val cleanAddress = address.trim()
        val cleanLoc = locationUrl.trim()
        val combinedDetails = details.trim().ifBlank {
            listOf(cleanAddress, cleanPhone).filter { it.isNotBlank() }.joinToString("\n")
        }
        val (fallbackPhone, fallbackAddress) = parsePhoneAndAddress(combinedDetails)
        val finalPhone = cleanPhone.ifBlank { fallbackPhone }
        val finalAddress = cleanAddress.ifBlank { fallbackAddress }

        if (id > 0L) {
            customerDao.upsert(
                CustomerEntity(
                    id = id,
                    name = clean,
                    details = combinedDetails,
                    phone = finalPhone,
                    address = finalAddress,
                    locationUrl = cleanLoc
                )
            )
            return
        }

        val existing = customerDao.findByName(clean)
        if (existing != null) {
            customerDao.upsert(
                existing.copy(
                    details = combinedDetails.ifBlank { existing.details },
                    phone = finalPhone.ifBlank { existing.phone },
                    address = finalAddress.ifBlank { existing.address },
                    locationUrl = cleanLoc.ifBlank { existing.locationUrl }
                )
            )
        } else {
            customerDao.upsert(
                CustomerEntity(
                    name = clean,
                    details = combinedDetails,
                    phone = finalPhone,
                    address = finalAddress,
                    locationUrl = cleanLoc
                )
            )
        }
    }

    private fun parsePhoneAndAddress(details: String): Pair<String, String> {
        val lines = details.lines().map { it.trim() }.filter { it.isNotBlank() }
        if (lines.isEmpty()) return "" to ""
        val phoneLine = lines.firstOrNull { line ->
            val digits = line.count { it.isDigit() }
            digits in 7..15 && line.length <= 24
        }
        val addressLines = lines.filter { it != phoneLine }
        return (phoneLine ?: "") to addressLines.joinToString(", ")
    }

    suspend fun deleteCustomer(id: Long) = customerDao.deleteById(id)

    suspend fun saveHelpRequest(
        id: Long = 0L,
        customerName: String,
        phone: String,
        address: String,
        locationUrl: String,
        helpDescription: String,
        status: String = "OPEN"
    ): CustomerHelpEntity {
        val existingCustomers = customerDao.getAll()
        val matched = findMatchingCustomersByPhone(existingCustomers, phone).firstOrNull()
        val resolvedName = customerName.trim().ifBlank { matched?.name ?: "" }
        val resolvedPhone = if (matched != null) {
            val matchedPhone = matched.phone.ifBlank { parsePhoneAndAddress(matched.details).first }
            val inputDigits = phone.filter { it.isDigit() }
            if (inputDigits.length <= 5 && matchedPhone.isNotBlank()) matchedPhone else phone.trim().ifBlank { matchedPhone }
        } else {
            phone.trim()
        }
        val resolvedAddress = address.trim().ifBlank {
            matched?.let { it.address.ifBlank { parsePhoneAndAddress(it.details).second.ifBlank { it.details } } } ?: ""
        }
        val resolvedLocation = locationUrl.trim().ifBlank { matched?.locationUrl ?: "" }
        val now = System.currentTimeMillis()
        val existingHelp = if (id > 0L) helpDao.getAll().firstOrNull { it.id == id } else null
        val entity = CustomerHelpEntity(
            id = id,
            customerName = resolvedName,
            phone = resolvedPhone,
            address = resolvedAddress,
            locationUrl = resolvedLocation,
            helpDescription = helpDescription.trim(),
            status = status.ifBlank { "OPEN" },
            createdAt = existingHelp?.createdAt ?: now,
            updatedAt = now
        )
        helpDao.upsert(entity)
        return entity
    }

    suspend fun updateHelpStatus(request: CustomerHelpEntity, newStatus: String) {
        helpDao.upsert(
            request.copy(
                status = newStatus,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun deleteHelpRequest(id: Long) = helpDao.deleteById(id)

    suspend fun addProduct(name: String, unit: String, rate: Double) {
        productDao.upsert(
            ProductEntity(
                name = name.trim(),
                unit = unit.trim().ifEmpty { "Nos" },
                defaultRate = rate.coerceAtLeast(0.0)
            )
        )
    }

    suspend fun updateProduct(id: Long, name: String, unit: String, rate: Double) {
        productDao.upsert(
            ProductEntity(
                id = id,
                name = name.trim(),
                unit = unit.trim().ifEmpty { "Nos" },
                defaultRate = rate.coerceAtLeast(0.0)
            )
        )
    }

    suspend fun deleteProduct(id: Long) = productDao.deleteById(id)

    suspend fun saveCompanySettings(settings: CompanySettings) {
        configDao.setAll(
            listOf(
                AppConfigEntity(KEY_COMPANY_NAME, settings.companyName.trim().ifEmpty { DEFAULT_COMPANY_NAME }),
                AppConfigEntity(KEY_COMPANY_MOBILE, settings.mobile.trim().ifEmpty { DEFAULT_COMPANY_MOBILE }),
                AppConfigEntity(KEY_COMPANY_EMAIL, settings.email.trim().ifEmpty { DEFAULT_COMPANY_EMAIL }),
                AppConfigEntity(KEY_COMPANY_ADDRESS, settings.address.trim().ifEmpty { DEFAULT_COMPANY_ADDRESS })
            )
        )
    }

    suspend fun saveUpiSettings(upiId: String, payeeName: String) {
        configDao.setAll(
            listOf(
                AppConfigEntity(KEY_UPI_ID, upiId.trim()),
                AppConfigEntity(KEY_UPI_NAME, payeeName.trim().ifEmpty { DEFAULT_PAYEE_NAME })
            )
        )
    }

    suspend fun clearUpiId() {
        configDao.setValue(AppConfigEntity(KEY_UPI_ID, ""))
    }

    suspend fun saveFallbackQrBase64(base64: String) {
        configDao.setValue(AppConfigEntity(KEY_FALLBACK_QR, base64))
    }

    suspend fun setSoundEnabled(enabled: Boolean) {
        configDao.setValue(AppConfigEntity(KEY_SOUND_ENABLED, if (enabled) "1" else "0"))
    }

    suspend fun setLanguage(language: AppLanguage) {
        configDao.setValue(AppConfigEntity(KEY_LANGUAGE, language.code))
    }

    fun computeNextInvoiceNo(bills: List<BillEntity>): String {
        var max = 0
        val regex = Regex("(\\d+)$")
        bills.forEach { b ->
            val match = regex.find(b.invoiceNo)
            if (match != null) {
                val n = match.groupValues[1].toIntOrNull() ?: 0
                if (n > max) max = n
            }
        }
        val next = maxOf(max, bills.size) + 1
        return String.format(Locale.US, "TSE-%03d", next)
    }

    suspend fun exportBackupJson(): String {
        val bills = billDao.getAll()
        val customers = customerDao.getAll()
        val products = productDao.getAll()
        val users = userDao.getAll()
        val cfg = configDao.getValue(KEY_COMPANY_NAME)

        val root = JSONObject()
        root.put("bills", JSONArray().apply {
            bills.forEach { b ->
                put(JSONObject().apply {
                    put("id", b.id)
                    put("no", b.invoiceNo)
                    put("date", b.date)
                    put("customer", b.customerName)
                    put("details", b.customerDetails)
                    put("items", JSONArray(b.itemsJson))
                    put("subtotal", b.subtotal)
                    put("discount", b.discount)
                    put("grandTotal", b.grandTotal)
                    put("paid", b.paidAmount)
                    put("due", b.dueAmount)
                    put("paymentMode", b.paymentMode)
                    put("signature", b.signatureBase64)
                    put("customQrText", b.customQrText)
                    put("createdBy", b.createdBy)
                    put("updatedAt", b.updatedAt)
                    put("extraChargeType", b.extraChargeType)
                    put("extraChargeAmount", b.extraChargeAmount)
                })
            }
        })
        root.put("customers", JSONArray().apply {
            customers.forEach { c ->
                put(JSONObject().apply {
                    put("name", c.name)
                    put("details", c.details)
                    put("phone", c.phone)
                    put("address", c.address)
                    put("locationUrl", c.locationUrl)
                })
            }
        })
        root.put("products", JSONArray().apply {
            products.forEach { p ->
                put(JSONObject().apply {
                    put("name", p.name)
                    put("unit", p.unit)
                    put("rate", p.defaultRate)
                })
            }
        })
        root.put("settings", JSONObject().apply {
            put("company", cfg ?: DEFAULT_COMPANY_NAME)
            put("mobile", configDao.getValue(KEY_COMPANY_MOBILE) ?: DEFAULT_COMPANY_MOBILE)
            put("email", configDao.getValue(KEY_COMPANY_EMAIL) ?: DEFAULT_COMPANY_EMAIL)
            put("address", configDao.getValue(KEY_COMPANY_ADDRESS) ?: DEFAULT_COMPANY_ADDRESS)
        })
        root.put("upi", JSONObject().apply {
            put("id", configDao.getValue(KEY_UPI_ID) ?: "")
            put("name", configDao.getValue(KEY_UPI_NAME) ?: DEFAULT_PAYEE_NAME)
        })
        root.put("qr", configDao.getValue(KEY_FALLBACK_QR) ?: "")
        root.put("accounts", JSONArray().apply {
            users.forEach { u ->
                put(JSONObject().apply {
                    put("username", u.username)
                    put("displayName", u.displayName)
                    put("passwordHash", u.passwordHash)
                    put("role", u.role)
                })
            }
        })
        val helpList = helpDao.getAll()
        root.put("helpRequests", JSONArray().apply {
            helpList.forEach { h ->
                put(JSONObject().apply {
                    put("customerName", h.customerName)
                    put("phone", h.phone)
                    put("address", h.address)
                    put("locationUrl", h.locationUrl)
                    put("helpDescription", h.helpDescription)
                    put("status", h.status)
                    put("createdAt", h.createdAt)
                    put("updatedAt", h.updatedAt)
                })
            }
        })
        return root.toString(2)
    }

    suspend fun importBackupJson(rawJson: String): Result<Unit> {
        return try {
            val root = JSONObject(rawJson)
            val billsArr = root.optJSONArray("bills") ?: return Result.failure(IllegalArgumentException("Missing bills"))
            val productsArr = root.optJSONArray("products") ?: return Result.failure(IllegalArgumentException("Missing products"))

            billDao.deleteAll()
            for (i in 0 until billsArr.length()) {
                val obj = billsArr.getJSONObject(i)
                val itemsStr = obj.optJSONArray("items")?.toString() ?: "[]"
                val sub = obj.optDouble("subtotal", 0.0)
                val disc = obj.optDouble("discount", 0.0)
                val grand = if (obj.has("grandTotal")) obj.optDouble("grandTotal", 0.0) else maxOf(0.0, sub - disc)
                val paid = if (obj.has("paid")) obj.optDouble("paid", 0.0) else obj.optDouble("advance", 0.0)
                val due = if (obj.has("due")) obj.optDouble("due", 0.0) else maxOf(0.0, grand - paid)
                billDao.upsert(
                    BillEntity(
                        id = obj.optString("id", "b${System.currentTimeMillis()}_$i"),
                        invoiceNo = obj.optString("no", "TSE-${i + 1}"),
                        date = obj.optString("date", ""),
                        customerName = obj.optString("customer", ""),
                        customerDetails = obj.optString("details", ""),
                        itemsJson = itemsStr,
                        subtotal = sub,
                        discount = disc,
                        grandTotal = grand,
                        paidAmount = paid,
                        dueAmount = due,
                        paymentMode = obj.optString("paymentMode", PaymentMode.ONLINE.label),
                        signatureBase64 = obj.optString("signature", ""),
                        customQrText = obj.optString("customQrText", ""),
                        createdBy = obj.optString("createdBy", "Admin"),
                        updatedAt = obj.optLong("updatedAt", System.currentTimeMillis()),
                        extraChargeType = obj.optString("extraChargeType", "NONE"),
                        extraChargeAmount = obj.optDouble("extraChargeAmount", 0.0)
                    )
                )
            }

            root.optJSONArray("customers")?.let { custArr ->
                customerDao.deleteAll()
                for (i in 0 until custArr.length()) {
                    val obj = custArr.getJSONObject(i)
                    val name = obj.optString("name", "").trim()
                    if (name.isNotEmpty()) {
                        customerDao.upsert(
                            CustomerEntity(
                                name = name,
                                details = obj.optString("details", ""),
                                phone = obj.optString("phone", ""),
                                address = obj.optString("address", ""),
                                locationUrl = obj.optString("locationUrl", "")
                            )
                        )
                    }
                }
            }

            productDao.deleteAll()
            val importedProducts = mutableListOf<ProductEntity>()
            for (i in 0 until productsArr.length()) {
                val obj = productsArr.getJSONObject(i)
                val name = obj.optString("name", "").trim()
                if (name.isNotEmpty()) {
                    importedProducts.add(
                        ProductEntity(
                            name = name,
                            unit = obj.optString("unit", "Nos"),
                            defaultRate = obj.optDouble("rate", 0.0)
                        )
                    )
                }
            }
            if (importedProducts.isNotEmpty()) productDao.insertAll(importedProducts)

            root.optJSONObject("settings")?.let { s ->
                saveCompanySettings(
                    CompanySettings(
                        companyName = s.optString("company", DEFAULT_COMPANY_NAME),
                        mobile = s.optString("mobile", DEFAULT_COMPANY_MOBILE),
                        email = s.optString("email", DEFAULT_COMPANY_EMAIL),
                        address = s.optString("address", DEFAULT_COMPANY_ADDRESS)
                    )
                )
            }

            root.optJSONObject("upi")?.let { u ->
                saveUpiSettings(
                    upiId = u.optString("id", ""),
                    payeeName = u.optString("name", DEFAULT_PAYEE_NAME)
                )
            }

            if (root.has("qr")) {
                saveFallbackQrBase64(root.optString("qr", ""))
            }

            root.optJSONArray("accounts")?.let { accArr ->
                if (accArr.length() > 0) {
                    userDao.deleteAll()
                    for (i in 0 until accArr.length()) {
                        val u = accArr.getJSONObject(i)
                        userDao.upsert(
                            UserAccountEntity(
                                username = u.getString("username"),
                                displayName = u.optString("displayName", u.getString("username")),
                                passwordHash = u.getString("passwordHash"),
                                role = u.optString("role", "staff")
                            )
                        )
                    }
                }
            }

            root.optJSONArray("helpRequests")?.let { helpArr ->
                helpDao.deleteAll()
                for (i in 0 until helpArr.length()) {
                    val h = helpArr.getJSONObject(i)
                    helpDao.upsert(
                        CustomerHelpEntity(
                            customerName = h.optString("customerName", ""),
                            phone = h.optString("phone", ""),
                            address = h.optString("address", ""),
                            locationUrl = h.optString("locationUrl", ""),
                            helpDescription = h.optString("helpDescription", ""),
                            status = h.optString("status", "OPEN"),
                            createdAt = h.optLong("createdAt", System.currentTimeMillis()),
                            updatedAt = h.optLong("updatedAt", System.currentTimeMillis())
                        )
                    )
                }
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    companion object {
        const val KEY_SEEDED = "seeded_v1"
        const val KEY_COMPANY_NAME = "company_name"
        const val KEY_COMPANY_MOBILE = "company_mobile"
        const val KEY_COMPANY_EMAIL = "company_email"
        const val KEY_COMPANY_ADDRESS = "company_address"
        const val KEY_UPI_ID = "upi_id"
        const val KEY_UPI_NAME = "upi_name"
        const val KEY_FALLBACK_QR = "fallback_qr"
        const val KEY_FINAL_CODE_HASH = "final_code_hash"
        const val KEY_SOUND_ENABLED = "sound_enabled"
        const val KEY_LANGUAGE = "language"

        const val DEFAULT_COMPANY_NAME = "TOTAL SOLUTION ENTERPRISES"
        const val DEFAULT_COMPANY_MOBILE = "7083805120"
        const val DEFAULT_COMPANY_EMAIL = "totalsolution7020@gmail.com"
        const val DEFAULT_COMPANY_ADDRESS = "B1, Santa Anna Holiday Homes, Naika Vaddo, Calangute, North Goa 403516."
        const val DEFAULT_PAYEE_NAME = "Manoj Kumar Saha"

        val STARTER_PRODUCTS = listOf(
            "2MP HD Dome Camera", "2MP HD Bullet Camera", "5MP HD Dome Camera", "5MP HD Bullet Camera",
            "IP Camera 2MP", "IP Camera 4MP", "4 Channel DVR", "8 Channel DVR", "16 Channel DVR",
            "4 Channel NVR", "8 Channel NVR", "1TB Surveillance Hard Disk", "2TB Surveillance Hard Disk",
            "4TB Surveillance Hard Disk", "4 Ch Power Supply (SMPS)", "8 Ch Power Supply (SMPS)",
            "3+1 CCTV Cable", "CAT6 Networking Cable", "BNC Connector", "DC Pin Connector",
            "PVC Junction Box (4x4)", "DVR Rack / Cabinet", "HDMI Cable",
            "Camera Installation & Configuration Charges"
        )

        fun extractCustomerPhonesDigits(customer: CustomerEntity): List<String> {
            return buildList {
                val pDigits = customer.phone.filter { it.isDigit() }
                if (pDigits.isNotEmpty()) add(pDigits)
                customer.details.lines().forEach { line ->
                    val d = line.filter { it.isDigit() }
                    if (d.length >= 5) add(d)
                }
            }.distinct()
        }

        fun filterCustomers(customers: List<CustomerEntity>, rawQuery: String): List<CustomerEntity> {
            val q = rawQuery.trim()
            if (q.isEmpty()) return customers
            val qDigits = q.filter { it.isDigit() }
            return customers.filter { cust ->
                cust.name.contains(q, ignoreCase = true) ||
                    cust.address.contains(q, ignoreCase = true) ||
                    cust.details.contains(q, ignoreCase = true) ||
                    cust.phone.contains(q, ignoreCase = true) ||
                    (qDigits.isNotEmpty() && extractCustomerPhonesDigits(cust).any { digits ->
                        digits.endsWith(qDigits) || digits.contains(qDigits)
                    })
            }
        }

        fun findMatchingCustomersByPhone(customers: List<CustomerEntity>, phoneInput: String): List<CustomerEntity> {
            val qDigits = phoneInput.filter { it.isDigit() }
            if (qDigits.length < 5) return emptyList()
            return customers.filter { cust ->
                extractCustomerPhonesDigits(cust).any { digits ->
                    digits == qDigits || digits.endsWith(qDigits) || (qDigits.length >= 7 && digits.contains(qDigits))
                }
            }
        }
    }
}
