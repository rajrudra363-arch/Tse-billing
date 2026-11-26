package com.example.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.util.Locale
import java.util.UUID

object AppIdentity {
    const val OFFICIAL_APP_NAME = "TS – TOTAL SOLUTION"
    const val OFFICIAL_COMPANY_NAME = "TOTAL SOLUTION"
}

data class InvoiceItem(
    val localId: Long = System.nanoTime(),
    val folder: String = "",
    val description: String = "",
    val qty: Double = 1.0,
    val unit: String = "Nos",
    val rate: Double = 0.0
) {
    val amount: Double get() = qty.coerceAtLeast(0.0) * rate.coerceAtLeast(0.0)
}

object InvoiceItemJson {
    fun toJson(items: List<InvoiceItem>): String {
        val arr = JSONArray()
        items.forEach { item ->
            arr.put(
                JSONObject().apply {
                    put("folder", item.folder)
                    put("desc", item.description)
                    put("qty", item.qty)
                    put("unit", item.unit)
                    put("rate", item.rate)
                }
            )
        }
        return arr.toString()
    }

    fun fromJson(raw: String): List<InvoiceItem> {
        if (raw.isBlank()) return emptyList()
        return try {
            val arr = JSONArray(raw)
            List(arr.length()) { i ->
                val obj = arr.getJSONObject(i)
                InvoiceItem(
                    localId = i.toLong() + 1L,
                    folder = obj.optString("folder", ""),
                    description = obj.optString("desc", ""),
                    qty = obj.optDouble("qty", 1.0),
                    unit = obj.optString("unit", "Nos"),
                    rate = obj.optDouble("rate", 0.0)
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}

enum class DocumentType(val label: String) {
    BILL("Tax Invoice"),
    QUOTATION("Quotation");

    companion object {
        fun fromString(raw: String): DocumentType =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) || it.label.equals(raw, ignoreCase = true) } ?: BILL
    }
}

enum class PaymentMode(val label: String) {
    ONLINE("Online Payment"),
    CASH("Cash");

    companion object {
        fun fromString(raw: String): PaymentMode =
            entries.firstOrNull { it.label.equals(raw, ignoreCase = true) || it.name.equals(raw, ignoreCase = true) } ?: ONLINE
    }
}

enum class BillPaymentStatus(val code: String, val label: String) {
    PAID("PAID", "Paid"),
    PARTIAL("PARTIAL", "Partially Paid"),
    UNPAID("UNPAID", "Unpaid");

    companion object {
        fun compute(grandTotal: Double, paidAmount: Double): BillPaymentStatus {
            val total = grandTotal.coerceAtLeast(0.0)
            val paid = paidAmount.coerceAtLeast(0.0)
            if (total <= 0.009) return if (paid > 0.009) PAID else UNPAID
            return when {
                paid + 0.009 >= total -> PAID
                paid > 0.009 -> PARTIAL
                else -> UNPAID
            }
        }

        fun resolveForBill(bill: BillEntity): BillPaymentStatus {
            val explicit = entries.firstOrNull { it.code.equals(bill.paymentStatus, ignoreCase = true) }
            if (bill.paymentStatus.isNotBlank() && explicit != null) {
                return explicit
            }
            return compute(bill.grandTotal, bill.paidAmount)
        }
    }
}

enum class PaymentGatewayStatus(val code: String, val label: String) {
    PENDING("PENDING", "Pending"),
    SUCCESS("SUCCESS", "Successful"),
    FAILED("FAILED", "Failed"),
    CANCELLED("CANCELLED", "Cancelled"),
    EXPIRED("EXPIRED", "Expired");

    companion object {
        fun fromString(raw: String): PaymentGatewayStatus {
            val norm = raw.trim().uppercase(Locale.US)
            return when (norm) {
                "PAID", "SUCCESS" -> SUCCESS
                "FAILED" -> FAILED
                "CANCELLED", "CANCELED" -> CANCELLED
                "EXPIRED" -> EXPIRED
                else -> PENDING
            }
        }
    }
}

enum class BillPaymentFilter(val label: String) {
    ALL("All"),
    SUCCESSFUL_PAYMENTS("Successful Payments"),
    PENDING_PAYMENTS("Pending Payments"),
    FAILED_PAYMENTS("Failed Payments"),
    PAID_BILLS("Paid Bills"),
    UNPAID_BILLS("Unpaid Bills"),
    PARTIALLY_PAID_BILLS("Partially Paid Bills")
}

object HelpWorkflowStatus {
    const val OPEN = "OPEN"
    const val ASSIGNED = "ASSIGNED"
    const val ACCEPTED = "ACCEPTED"
    const val POSTPONED = "POSTPONED"
    const val INSTALLATION_IN_PROGRESS = "INSTALLATION_IN_PROGRESS"
    const val INSTALLATION_COMPLETED = "INSTALLATION_COMPLETED"
    const val BILLED = "BILLED"
    const val SOLVED = "SOLVED"
    const val NOT_SOLVED = "NOT_SOLVED"
    const val RESOLVED = "RESOLVED"
    const val REJECTED = "REJECTED"
}

enum class ChargeType(val label: String) {
    NONE("None"),
    SERVICE("Service Charges"),
    INSTALLATION("Installation Charges"),
    INSTALLATION_AND_SERVICE("Installation & Service Charges"),
    CUSTOMER_CARE_AND_SUPPORT("Customer Care & Support Charges");

    companion object {
        fun fromString(raw: String): ChargeType =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) || it.label.equals(raw, ignoreCase = true) } ?: NONE
    }
}

enum class AppLanguage(val code: String, val displayName: String) {
    EN("en", "English");

    companion object {
        fun fromCode(code: String): AppLanguage = EN
    }
}

enum class VoicePreset(
    val id: String,
    val label: String,
    val pitch: Float,
    val rate: Float
) {
    CLEAR("preset_clear", "Clear Professional", 1.0f, 0.96f),
    WARM("preset_warm", "Warm Deep", 0.88f, 0.92f),
    CRISP("preset_crisp", "Crisp Energetic", 1.08f, 1.05f),
    FORMAL("preset_formal", "Calm Formal", 0.95f, 0.88f);

    companion object {
        fun fromId(id: String): VoicePreset =
            entries.firstOrNull { it.id == id } ?: CLEAR
    }
}

data class VoiceAndNoteSettings(
    val headerNote: String = "Welcome to ${AppIdentity.OFFICIAL_COMPANY_NAME} — CCTV Security, Sales, Installation & Support",
    val loginVoice: String = "Welcome to ${AppIdentity.OFFICIAL_APP_NAME}. Select your role and sign in.",
    val postLoginVoice: String = "Login successful. Welcome to ${AppIdentity.OFFICIAL_COMPANY_NAME}.",
    val billGeneratingVoice: String = "Generating bill. Please verify customer summary.",
    val billCompletedVoice: String = "Bill generated. Total amount is {amount} rupees. Thank you for choosing ${AppIdentity.OFFICIAL_COMPANY_NAME}.",
    val voiceModelId: String = VoicePreset.CLEAR.id
)

data class CompanySettings(
    val companyName: String = AppIdentity.OFFICIAL_COMPANY_NAME,
    val mobile: String = "7083805120",
    val email: String = "totalsolution7020@gmail.com",
    val address: String = "B1, Santa Anna Holiday Homes, Naika Vaddo, Calangute, North Goa 403516.",
    val authorisedSignatureBase64: String = "",
    val logoBase64: String = ""
)

data class UpiSettings(
    val upiId: String = "",
    val payeeName: String = AppIdentity.OFFICIAL_COMPANY_NAME,
    val fallbackQrBase64: String = ""
)

enum class HelpCategory(val code: String, val label: String) {
    REGULAR_SERVICE("REGULAR_SERVICE", "Regular Service"),
    EMERGENCY("EMERGENCY", "Customer Need Help (Emergency)"),
    INSTALLATION("INSTALLATION", "Installation"),
    QUOTATION("QUOTATION", "Quotation");

    companion object {
        fun fromString(raw: String): HelpCategory =
            entries.firstOrNull {
                it.code.equals(raw.trim(), ignoreCase = true) ||
                    it.label.equals(raw.trim(), ignoreCase = true)
            } ?: REGULAR_SERVICE
    }
}

data class TechActivationState(
    val verified: Boolean = false,
    val email: String = "",
    val username: String = "",
    val name: String = "",
    val workspaceId: String = "",
    val companyName: String = AppIdentity.OFFICIAL_COMPANY_NAME,
    val techId: String = "",
    val phone: String = "",
    val mustChangePassword: Boolean = false
)

data class UserSession(
    val username: String,
    val displayName: String,
    val role: String,
    val phone: String = "",
    val photoBase64: String = "",
    val email: String = "",
    val uid: String = "",
    val techId: String = "",
    val companyId: String = ""
) {
    val isAdmin: Boolean get() = role.equals("admin", ignoreCase = true)
    val isSuperAdmin: Boolean get() = role.equals("super_admin", ignoreCase = true)
    val isTechnician: Boolean
        get() = role.equals("technician", ignoreCase = true) || role.equals("staff", ignoreCase = true)
    val isStaff: Boolean get() = isTechnician

    val roleDisplayLabel: String
        get() = when {
            isAdmin -> "Admin"
            isSuperAdmin -> "Super Admin"
            else -> "Technician"
        }
}

object AuthPolicy {
    const val CANONICAL_ADMIN_USERNAME = "@TotalSolutionAdmin"
    const val PRIMARY_ADMIN_EMAIL = "rajrudra363@gmail.com"
    const val FIREBASE_EMAIL_AUTH_MARKER = "FIREBASE_EMAIL_AUTH_ONLY"
    const val TECH_LOGIN_DISABLED_MESSAGE =
        "Your login has been disabled by Admin. Please contact your Admin."

    data class PasswordStrengthResult(
        val hasMinLength: Boolean,
        val hasUppercase: Boolean,
        val hasLowercase: Boolean,
        val hasDigit: Boolean,
        val hasSpecialChar: Boolean,
        val hasNoWhitespace: Boolean
    ) {
        val isStrong: Boolean
            get() = hasMinLength && hasUppercase && hasLowercase && hasDigit && hasSpecialChar && hasNoWhitespace

        val score: Int
            get() = listOf(
                hasMinLength,
                hasUppercase,
                hasLowercase,
                hasDigit,
                hasSpecialChar && hasNoWhitespace
            ).count { it }

        fun firstMissingRequirement(): String? = when {
            !hasNoWhitespace -> "Password cannot contain spaces."
            !hasMinLength -> "Password must be at least 8 characters."
            !hasUppercase -> "Include at least 1 uppercase letter (A-Z)."
            !hasLowercase -> "Include at least 1 lowercase letter (a-z)."
            !hasDigit -> "Include at least 1 number (0-9)."
            !hasSpecialChar -> "Include at least 1 special symbol (@, #, $, %, !, etc.)."
            else -> null
        }
    }

    fun evaluatePassword(password: String): PasswordStrengthResult = PasswordStrengthResult(
        hasMinLength = password.length >= 8,
        hasUppercase = password.any { it.isUpperCase() },
        hasLowercase = password.any { it.isLowerCase() },
        hasDigit = password.any { it.isDigit() },
        hasSpecialChar = password.any { !it.isLetterOrDigit() && !it.isWhitespace() },
        hasNoWhitespace = password.none { it.isWhitespace() }
    )

    fun isValidEmail(email: String): Boolean {
        val trimmed = email.trim()
        if (trimmed.length < 5 || trimmed.length > 254 || trimmed.any { it.isWhitespace() }) return false
        val atIdx = trimmed.indexOf('@')
        if (atIdx <= 0 || atIdx != trimmed.lastIndexOf('@')) return false
        val domain = trimmed.substring(atIdx + 1)
        val dotIdx = domain.indexOf('.')
        return dotIdx > 0 && dotIdx < domain.lastIndex && !domain.startsWith('.') && !domain.endsWith('.')
    }

    fun deriveUsernameFromEmailOrName(email: String, name: String): String {
        val seed = email.trim().substringBefore('@').ifBlank { name.trim() }
        val cleaned = seed.lowercase(Locale.US)
            .replace(Regex("[^a-z0-9_]"), "_")
            .replace(Regex("_+"), "_")
            .trim('_')
            .take(32)
        return if (cleaned.length >= 3) cleaned else "${cleaned}tech".take(8)
    }

    fun formatHandle(username: String): String {
        val clean = username.trim()
        return if (clean.isBlank()) "" else if (clean.startsWith("@")) clean else "@$clean"
    }
}

data class LoginSecurityState(
    val failedAttempts: Int = 0,
    val lockUntilMillis: Long = 0L,
    val maxAttempts: Int = MAX_ATTEMPTS,
    val lockDurationMs: Long = LOCK_DURATION_MS
) {
    companion object {
        const val MAX_ATTEMPTS = 5
        const val LOCK_DURATION_MS = 2L * 60L * 60L * 1000L
        const val ADMIN_MAX_ATTEMPTS = 3
        const val ADMIN_LOCK_DURATION_MS = 15L * 60L * 1000L
    }

    fun isLocked(nowMillis: Long = System.currentTimeMillis()): Boolean =
        lockUntilMillis > nowMillis

    fun remainingLockMillis(nowMillis: Long = System.currentTimeMillis()): Long =
        (lockUntilMillis - nowMillis).coerceAtLeast(0L)

    fun effectiveFailedAttempts(nowMillis: Long = System.currentTimeMillis()): Int =
        if (lockUntilMillis in 1..nowMillis) 0 else failedAttempts.coerceAtLeast(0)

    fun remainingAttempts(nowMillis: Long = System.currentTimeMillis()): Int =
        if (isLocked(nowMillis)) 0
        else (maxAttempts - effectiveFailedAttempts(nowMillis)).coerceIn(0, maxAttempts)
}

object BillingTimePolicy {
    fun isNightClosed(hourOfDay: Int, minute: Int): Boolean {
        val totalMinutes = hourOfDay * 60 + minute
        return totalMinutes in 1..360
    }

    fun isNightClosedNow(nowMillis: Long = System.currentTimeMillis()): Boolean {
        val cal = Calendar.getInstance().apply { timeInMillis = nowMillis }
        return isNightClosed(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
    }
}

/**
 * Part 11: PRODUCT COMBO FILE models.
 * Allows selecting multiple existing products, custom separate qty per item, and a separate combo price
 * without modifying original product prices, purchase prices, or deducting stock.
 */
data class ProductComboItem(
    val productId: Long = 0L,
    val productName: String = "",
    val unit: String = "Nos",
    val qty: Double = 1.0,
    val catalogRate: Double = 0.0,
    val folder: String = ""
) {
    val rate: Double get() = catalogRate
    val amount: Double get() = qty.coerceAtLeast(0.0) * catalogRate.coerceAtLeast(0.0)
}

data class ProductComboFile(
    val comboFileId: String = UUID.randomUUID().toString(),
    val companyId: String = "",
    val name: String = "",
    val description: String = "",
    val items: List<ProductComboItem> = emptyList(),
    val comboPrice: Double = 0.0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    val id: String get() = comboFileId
    val catalogSum: Double get() = items.sumOf { it.amount }
    val effectiveComboPrice: Double get() = if (comboPrice > 0.0) comboPrice else catalogSum
    val totalAmount: Double get() = effectiveComboPrice
    val totalQty: Double get() = items.sumOf { it.qty }
}

data class ComboPackageItem(
    val productId: Long = 0L,
    val productName: String = "",
    val unit: String = "Nos",
    val qty: Double = 1.0,
    val rate: Double = 0.0,
    val folder: String = ""
) {
    val amount: Double get() = qty.coerceAtLeast(0.0) * rate.coerceAtLeast(0.0)
    val catalogRate: Double get() = rate
}

data class ComboPackage(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val description: String = "",
    val items: List<ComboPackageItem> = emptyList(),
    val comboPrice: Double = 0.0,
    val createdAt: Long = System.currentTimeMillis()
) {
    val comboFileId: String get() = id
    val totalAmount: Double get() = if (comboPrice > 0.0) comboPrice else items.sumOf { it.amount }
    val totalQty: Double get() = items.sumOf { it.qty }
}

object ProductComboFileJson {
    fun itemsToJson(items: List<ProductComboItem>): String {
        val arr = JSONArray()
        items.forEach { itm ->
            arr.put(
                JSONObject().apply {
                    put("productId", itm.productId)
                    put("productName", itm.productName)
                    put("unit", itm.unit)
                    put("qty", itm.qty)
                    put("catalogRate", itm.catalogRate)
                    put("folder", itm.folder)
                }
            )
        }
        return arr.toString()
    }

    fun itemsFromJson(raw: String): List<ProductComboItem> {
        if (raw.isBlank()) return emptyList()
        return try {
            val arr = JSONArray(raw)
            List(arr.length()) { i ->
                val obj = arr.getJSONObject(i)
                ProductComboItem(
                    productId = obj.optLong("productId", obj.optLong("prodId", 0L)),
                    productName = obj.optString("productName", obj.optString("prodName", "")),
                    unit = obj.optString("unit", "Nos"),
                    qty = obj.optDouble("qty", 1.0),
                    catalogRate = obj.optDouble("catalogRate", obj.optDouble("rate", 0.0)),
                    folder = obj.optString("folder", "")
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun toEntity(combo: ProductComboFile): ProductComboFileEntity = ProductComboFileEntity(
        comboFileId = combo.comboFileId,
        companyId = combo.companyId,
        name = combo.name.trim(),
        description = combo.description.trim(),
        itemsJson = itemsToJson(combo.items),
        comboPrice = combo.comboPrice.coerceAtLeast(0.0),
        createdAt = combo.createdAt,
        updatedAt = combo.updatedAt
    )

    fun fromEntity(entity: ProductComboFileEntity): ProductComboFile = ProductComboFile(
        comboFileId = entity.comboFileId,
        companyId = entity.companyId,
        name = entity.name,
        description = entity.description,
        items = itemsFromJson(entity.itemsJson),
        comboPrice = entity.comboPrice,
        createdAt = entity.createdAt,
        updatedAt = entity.updatedAt
    )
}

object ComboPackageJson {
    fun toJsonObject(pkg: ComboPackage): JSONObject = JSONObject().apply {
        put("id", pkg.id)
        put("name", pkg.name)
        put("desc", pkg.description)
        put("createdAt", pkg.createdAt)
        put("comboPrice", pkg.comboPrice)
        val itemsArr = JSONArray()
        pkg.items.forEach { itm ->
            itemsArr.put(
                JSONObject().apply {
                    put("prodId", itm.productId)
                    put("prodName", itm.productName)
                    put("unit", itm.unit)
                    put("qty", itm.qty)
                    put("rate", itm.rate)
                    put("folder", itm.folder)
                }
            )
        }
        put("items", itemsArr)
    }

    fun fromJsonObject(obj: JSONObject): ComboPackage {
        val itemsArr = obj.optJSONArray("items") ?: JSONArray()
        val items = List(itemsArr.length()) { j ->
            val itmObj = itemsArr.getJSONObject(j)
            ComboPackageItem(
                productId = itmObj.optLong("prodId", itmObj.optLong("productId", 0L)),
                productName = itmObj.optString("prodName", itmObj.optString("productName", "")),
                unit = itmObj.optString("unit", "Nos"),
                qty = itmObj.optDouble("qty", 1.0),
                rate = itmObj.optDouble("rate", itmObj.optDouble("catalogRate", 0.0)),
                folder = itmObj.optString("folder", "")
            )
        }
        return ComboPackage(
            id = obj.optString("id", obj.optString("comboFileId", UUID.randomUUID().toString())),
            name = obj.optString("name", "Combo File"),
            description = obj.optString("desc", obj.optString("description", "")),
            items = items,
            comboPrice = obj.optDouble("comboPrice", 0.0),
            createdAt = obj.optLong("createdAt", System.currentTimeMillis())
        )
    }

    fun toSingleJson(pkg: ComboPackage): String = toJsonObject(pkg).toString()

    fun fromSingleJson(raw: String): ComboPackage? {
        if (raw.isBlank()) return null
        return try {
            fromJsonObject(JSONObject(raw))
        } catch (_: Exception) {
            null
        }
    }

    fun toJson(packages: List<ComboPackage>): String {
        val arr = JSONArray()
        packages.forEach { pkg -> arr.put(toJsonObject(pkg)) }
        return arr.toString()
    }

    fun fromJson(raw: String): List<ComboPackage> {
        if (raw.isBlank()) return emptyList()
        return try {
            val arr = JSONArray(raw)
            List(arr.length()) { i -> fromJsonObject(arr.getJSONObject(i)) }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
