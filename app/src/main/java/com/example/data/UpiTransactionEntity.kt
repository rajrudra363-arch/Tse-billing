package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "upi_transactions")
data class UpiTransactionEntity(
    @PrimaryKey val paymentId: String,
    val billId: String,
    val invoiceNo: String,
    val orderId: String,
    val amount: Double,
    val status: String, // PENDING, PAID, FAILED, EXPIRED
    val paymentGateway: String, // RAZORPAY / CASHFREE
    val gatewaySignature: String = "",
    val qrCodeUrl: String = "",
    val customerEmail: String = "",
    val customerPhone: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
