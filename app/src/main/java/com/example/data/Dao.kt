package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BillDao {
    @Query("SELECT * FROM bills ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<BillEntity>>

    @Query("SELECT * FROM bills ORDER BY updatedAt DESC")
    suspend fun getAll(): List<BillEntity>

    @Query("SELECT * FROM bills WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): BillEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(bill: BillEntity)

    @Query("UPDATE bills SET createdBy = :newUsername WHERE LOWER(createdBy) = LOWER(:oldUsername)")
    suspend fun reassignCreator(oldUsername: String, newUsername: String)

    @Query("DELETE FROM bills WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM bills")
    suspend fun deleteAll()
}

@Dao
interface CustomerDao {
    @Query("SELECT * FROM customers ORDER BY name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<CustomerEntity>>

    @Query("SELECT * FROM customers ORDER BY name COLLATE NOCASE ASC")
    suspend fun getAll(): List<CustomerEntity>

    @Query("SELECT * FROM customers WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): CustomerEntity?

    @Query("SELECT * FROM customers WHERE LOWER(name) = LOWER(:name) LIMIT 1")
    suspend fun findByName(name: String): CustomerEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(customer: CustomerEntity): Long

    @Query("DELETE FROM customers WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM customers")
    suspend fun deleteAll()
}

@Dao
interface ProductDao {
    @Query("SELECT * FROM products ORDER BY folder COLLATE NOCASE ASC, id ASC")
    fun observeAll(): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products ORDER BY folder COLLATE NOCASE ASC, id ASC")
    suspend fun getAll(): List<ProductEntity>

    @Query("SELECT * FROM products WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): ProductEntity?

    @Query("SELECT * FROM products WHERE LOWER(name) = LOWER(:name) LIMIT 1")
    suspend fun findByName(name: String): ProductEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(products: List<ProductEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(product: ProductEntity): Long

    @Query("DELETE FROM products WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM products")
    suspend fun deleteAll()
}

@Dao
interface UserAccountDao {
    @Query(
        """
        SELECT * FROM user_accounts
        ORDER BY CASE
            WHEN LOWER(role) = 'admin' THEN 0
            WHEN LOWER(role) = 'super_admin' THEN 1
            ELSE 2
        END, username COLLATE NOCASE ASC
        """
    )
    fun observeAll(): Flow<List<UserAccountEntity>>

    @Query("SELECT * FROM user_accounts")
    suspend fun getAll(): List<UserAccountEntity>

    @Query("SELECT * FROM user_accounts WHERE LOWER(username) = LOWER(:username) LIMIT 1")
    suspend fun findByUsername(username: String): UserAccountEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(account: UserAccountEntity)

    @Query("DELETE FROM user_accounts WHERE LOWER(role) = 'admin'")
    suspend fun deleteAllAdmins()

    @Query("DELETE FROM user_accounts WHERE username = :username")
    suspend fun deleteByUsername(username: String)

    @Query("DELETE FROM user_accounts")
    suspend fun deleteAll()
}

@Dao
interface AppConfigDao {
    @Query("SELECT * FROM app_config")
    fun observeAll(): Flow<List<AppConfigEntity>>

    @Query("SELECT * FROM app_config")
    suspend fun getAll(): List<AppConfigEntity>

    @Query("SELECT value FROM app_config WHERE `key` = :key LIMIT 1")
    suspend fun getValue(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setValue(entry: AppConfigEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setAll(entries: List<AppConfigEntity>)
}

@Dao
interface CustomerHelpDao {
    @Query(
        """
        SELECT * FROM customer_help_requests
        ORDER BY CASE
            WHEN status = 'OPEN' THEN 0
            WHEN status = 'ACCEPTED' THEN 1
            WHEN status = 'NOT_SOLVED' THEN 2
            ELSE 3
        END, updatedAt DESC
        """
    )
    fun observeAll(): Flow<List<CustomerHelpEntity>>

    @Query("SELECT * FROM customer_help_requests ORDER BY updatedAt DESC")
    suspend fun getAll(): List<CustomerHelpEntity>

    @Query("SELECT * FROM customer_help_requests WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): CustomerHelpEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(request: CustomerHelpEntity): Long

    @Query("UPDATE customer_help_requests SET acceptedBy = :newUsername, acceptedByName = :newName, acceptedByPhone = :newPhone WHERE LOWER(acceptedBy) = LOWER(:oldUsername)")
    suspend fun updateTechnicianInfo(oldUsername: String, newUsername: String, newName: String, newPhone: String)

    @Query("DELETE FROM customer_help_requests WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM customer_help_requests")
    suspend fun deleteAll()
}

@Dao
interface TechnicianSettlementDao {
    @Query("SELECT * FROM technician_settlements ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<TechnicianSettlementEntity>>

    @Query("SELECT * FROM technician_settlements ORDER BY createdAt DESC")
    suspend fun getAll(): List<TechnicianSettlementEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(settlement: TechnicianSettlementEntity): Long

    @Query("UPDATE technician_settlements SET technicianUsername = :newUsername WHERE LOWER(technicianUsername) = LOWER(:oldUsername)")
    suspend fun reassignTechnician(oldUsername: String, newUsername: String)

    @Query("DELETE FROM technician_settlements")
    suspend fun deleteAll()
}

@Dao
interface ExpenseDao {
    @Query("SELECT * FROM expenses ORDER BY date DESC, updatedAt DESC")
    fun observeAll(): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expenses ORDER BY date DESC, updatedAt DESC")
    suspend fun getAll(): List<ExpenseEntity>

    @Query("SELECT * FROM expenses WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): ExpenseEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(expense: ExpenseEntity)

    @Query("DELETE FROM expenses WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM expenses")
    suspend fun deleteAll()
}

@Dao
interface TrashItemDao {
    @Query("SELECT * FROM trash_items WHERE status = 'TRASHED' ORDER BY deletedAt DESC")
    fun observeActive(): Flow<List<TrashItemEntity>>

    @Query("SELECT * FROM trash_items ORDER BY deletedAt DESC")
    suspend fun getAll(): List<TrashItemEntity>

    @Query("SELECT * FROM trash_items WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): TrashItemEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: TrashItemEntity)

    @Query("DELETE FROM trash_items WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM trash_items")
    suspend fun deleteAll()
}

@Dao
interface AuditLogDao {
    @Query("SELECT * FROM audit_logs ORDER BY createdAt DESC LIMIT 200")
    fun observeRecent(): Flow<List<AuditLogEntity>>

    @Query("SELECT * FROM audit_logs ORDER BY createdAt DESC")
    suspend fun getAll(): List<AuditLogEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: AuditLogEntity)

    @Query("DELETE FROM audit_logs")
    suspend fun deleteAll()
}

@Dao
interface ProductComboFileDao {
    @Query("SELECT * FROM product_combo_files ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<ProductComboFileEntity>>

    @Query("SELECT * FROM product_combo_files ORDER BY updatedAt DESC")
    suspend fun getAll(): List<ProductComboFileEntity>

    @Query("SELECT * FROM product_combo_files WHERE comboFileId = :id LIMIT 1")
    suspend fun findById(id: String): ProductComboFileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(combo: ProductComboFileEntity)

    @Query("DELETE FROM product_combo_files WHERE comboFileId = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM product_combo_files")
    suspend fun deleteAll()
}

@Dao
interface EmployeeDao {
    @Query("SELECT * FROM employees ORDER BY name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<EmployeeEntity>>

    @Query("SELECT * FROM employees ORDER BY name COLLATE NOCASE ASC")
    suspend fun getAll(): List<EmployeeEntity>

    @Query("SELECT * FROM employees WHERE employeeId = :id LIMIT 1")
    suspend fun findById(id: String): EmployeeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(employee: EmployeeEntity)

    @Query("DELETE FROM employees WHERE employeeId = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM employees")
    suspend fun deleteAll()
}

@Dao
interface SalaryPaymentDao {
    @Query("SELECT * FROM salary_payments ORDER BY paymentDate DESC, createdAt DESC")
    fun observeAll(): Flow<List<SalaryPaymentEntity>>

    @Query("SELECT * FROM salary_payments WHERE employeeId = :employeeId ORDER BY paymentDate DESC, createdAt DESC")
    fun observeForEmployee(employeeId: String): Flow<List<SalaryPaymentEntity>>

    @Query("SELECT * FROM salary_payments ORDER BY paymentDate DESC, createdAt DESC")
    suspend fun getAll(): List<SalaryPaymentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(payment: SalaryPaymentEntity)

    @Query("DELETE FROM salary_payments")
    suspend fun deleteAll()
}

