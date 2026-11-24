package com.example.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

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
    val headerNote: String = "Welcome to Total Solution Enterprises — CCTV Security, Sales, Installation & Support",
    val loginVoice: String = "Welcome to Total Solution Bill system. Select username and enter password.",
    val postLoginVoice: String = "Login successful. Welcome to Total Solution Enterprises.",
    val billGeneratingVoice: String = "Generating bill. Please verify customer summary.",
    val billCompletedVoice: String = "Bill generated. Total amount is {amount} rupees. Thank you for choosing Total Solution Enterprises.",
    val voiceModelId: String = VoicePreset.CLEAR.id
)

data class CompanySettings(
    val companyName: String = "TOTAL SOLUTION ENTERPRISES",
    val mobile: String = "7083805120",
    val email: String = "totalsolution7020@gmail.com",
    val address: String = "B1, Santa Anna Holiday Homes, Naika Vaddo, Calangute, North Goa 403516.",
    val authorisedSignatureBase64: String = "",
    val logoBase64: String = ""
)

data class UpiSettings(
    val upiId: String = "",
    val payeeName: String = "Manoj Kumar Saha",
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
    val companyName: String = ""
)

data class UserSession(
    val username: String,
    val displayName: String,
    val role: String,
    val phone: String = "",
    val photoBase64: String = ""
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
    const val CANONICAL_ADMIN_USERNAME = "@TotalSolutionEnterprises"
    const val PRIMARY_ADMIN_EMAIL = "rajrudra363@gmail.com"
    const val FIREBASE_EMAIL_AUTH_MARKER = "FIREBASE_EMAIL_AUTH_ONLY"

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
        val cleaned = seed.lowercase(java.util.Locale.US)
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
        const val LOCK_DURATION_MS = 2L * 60L * 60L * 1000L // 2 hours
        const val ADMIN_MAX_ATTEMPTS = 3
        const val ADMIN_LOCK_DURATION_MS = 15L * 60L * 1000L // 15 minutes (device-local)
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
    /**
     * Billing system is closed daily from 12:01 AM (00:01) to 6:00 AM (06:00) local time.
     */
    fun isNightClosed(hourOfDay: Int, minute: Int): Boolean {
        val totalMinutes = hourOfDay * 60 + minute
        return totalMinutes in 1..360
    }

    fun isNightClosedNow(nowMillis: Long = System.currentTimeMillis()): Boolean {
        val cal = Calendar.getInstance().apply { timeInMillis = nowMillis }
        return isNightClosed(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
    }
}
