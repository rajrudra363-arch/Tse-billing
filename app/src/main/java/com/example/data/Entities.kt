package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "bills")
data class BillEntity(
    @PrimaryKey val id: String,
    val invoiceNo: String,
    val date: String,
    val customerName: String,
    val customerDetails: String,
    val itemsJson: String,
    val subtotal: Double,
    val discount: Double,
    val grandTotal: Double,
    val paidAmount: Double,
    val dueAmount: Double,
    val paymentMode: String,
    val signatureBase64: String,
    val customQrText: String,
    val createdBy: String,
    val updatedAt: Long = System.currentTimeMillis(),
    val extraChargeType: String = "NONE",
    val extraChargeAmount: Double = 0.0,
    val secondaryChargeType: String = "NONE",
    val secondaryChargeAmount: Double = 0.0,
    val staffRating: Int = 0,
    val staffReviewNote: String = "",
    val documentType: String = "BILL",
    val isHelpBill: Boolean = false,
    val helpRequestId: Long = 0L,
    val quotationNote: String = ""
)

@Entity(tableName = "customers")
data class CustomerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val details: String,
    val phone: String = "",
    val address: String = "",
    val locationUrl: String = ""
)

@Entity(tableName = "products")
data class ProductEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val unit: String = "Nos",
    val defaultRate: Double = 0.0,
    val folder: String = "General",
    val stockQty: Double = 0.0,
    val minStockAlert: Double = 0.0
)

@Entity(tableName = "user_accounts")
data class UserAccountEntity(
    @PrimaryKey val username: String,
    val displayName: String,
    val passwordHash: String,
    val role: String,
    val phone: String = "",
    val photoBase64: String = ""
)

@Entity(tableName = "app_config")
data class AppConfigEntity(
    @PrimaryKey val key: String,
    val value: String
)

@Entity(tableName = "customer_help_requests")
data class CustomerHelpEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val customerName: String,
    val phone: String,
    val address: String,
    val locationUrl: String,
    val helpDescription: String,
    val status: String = "OPEN",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val acceptedBy: String = "",
    val acceptedByName: String = "",
    val acceptedByPhone: String = "",
    val acceptedAt: Long = 0L,
    val solvedAt: Long = 0L,
    val billId: String = "",
    val category: String = "REGULAR_SERVICE",
    val quotationStatus: String = "",
    val installationDate: String = "",
    val installationReminderSent: Boolean = false,
    val rejectedReason: String = "",
    val assignedTechnician: String = ""
)

@Entity(tableName = "technician_settlements")
data class TechnicianSettlementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val technicianUsername: String,
    val amount: Double,
    val note: String = "",
    val recordedBy: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "expenses")
data class ExpenseEntity(
    @PrimaryKey val id: String,
    val title: String,
    val category: String = "General",
    val amount: Double = 0.0,
    val date: String = "",
    val note: String = "",
    val recordedBy: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "trash_items")
data class TrashItemEntity(
    @PrimaryKey val id: String,
    val entityType: String,
    val entityId: String,
    val title: String,
    val summary: String,
    val payloadJson: String,
    val deletedBy: String,
    val deletedByRole: String,
    val status: String = "TRASHED",
    val deletedAt: Long = System.currentTimeMillis(),
    val expireAt: Long = System.currentTimeMillis() + 86_400_000L,
    val restoredAt: Long = 0L,
    val restoredBy: String = ""
)

@Entity(tableName = "audit_logs")
data class AuditLogEntity(
    @PrimaryKey val id: String,
    val action: String,
    val entityType: String,
    val entityId: String,
    val title: String,
    val actorUsername: String,
    val actorRole: String,
    val details: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
