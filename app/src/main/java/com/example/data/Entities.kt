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
    val extraChargeAmount: Double = 0.0
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
    val defaultRate: Double = 0.0
)

@Entity(tableName = "user_accounts")
data class UserAccountEntity(
    @PrimaryKey val username: String,
    val displayName: String,
    val passwordHash: String,
    val role: String
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
    val updatedAt: Long = System.currentTimeMillis()
)

