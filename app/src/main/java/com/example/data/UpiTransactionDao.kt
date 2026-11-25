package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface UpiTransactionDao {
    @Query("SELECT * FROM upi_transactions ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<UpiTransactionEntity>>

    @Query("SELECT * FROM upi_transactions WHERE billId = :billId ORDER BY updatedAt DESC")
    fun observeForBill(billId: String): Flow<List<UpiTransactionEntity>>

    @Query("SELECT * FROM upi_transactions WHERE paymentId = :paymentId LIMIT 1")
    suspend fun findById(paymentId: String): UpiTransactionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(transaction: UpiTransactionEntity)

    @Query("DELETE FROM upi_transactions WHERE paymentId = :paymentId")
    suspend fun deleteById(paymentId: String)

    @Query("DELETE FROM upi_transactions")
    suspend fun deleteAll()
}
