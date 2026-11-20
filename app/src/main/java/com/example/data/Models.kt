package com.example.data

import org.json.JSONArray
import org.json.JSONObject

data class InvoiceItem(
    val localId: Long = System.nanoTime(),
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
    INSTALLATION_AND_SERVICE("Installation & Service Charges");

    companion object {
        fun fromString(raw: String): ChargeType =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) || it.label.equals(raw, ignoreCase = true) } ?: NONE
    }
}

enum class AppLanguage(val code: String, val displayName: String) {
    EN("en", "English"),
    HI("hi", "हिन्दी");

    companion object {
        fun fromCode(code: String): AppLanguage =
            entries.firstOrNull { it.code == code } ?: EN
    }
}

data class CompanySettings(
    val companyName: String = "TOTAL SOLUTION ENTERPRISES",
    val mobile: String = "7083805120",
    val email: String = "totalsolution7020@gmail.com",
    val address: String = "B1, Santa Anna Holiday Homes, Naika Vaddo, Calangute, North Goa 403516.",
    val authorisedSignatureBase64: String = ""
)

data class UpiSettings(
    val upiId: String = "",
    val payeeName: String = "Manoj Kumar Saha",
    val fallbackQrBase64: String = ""
)

data class UserSession(
    val username: String,
    val displayName: String,
    val role: String
) {
    val isAdmin: Boolean get() = role.equals("admin", ignoreCase = true)
    val isStaff: Boolean get() = role.equals("staff", ignoreCase = true)
}
