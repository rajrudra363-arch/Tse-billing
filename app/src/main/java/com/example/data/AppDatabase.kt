package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        BillEntity::class,
        CustomerEntity::class,
        ProductEntity::class,
        UserAccountEntity::class,
        AppConfigEntity::class,
        CustomerHelpEntity::class,
        TechnicianSettlementEntity::class
    ],
    version = 7,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun billDao(): BillDao
    abstract fun customerDao(): CustomerDao
    abstract fun productDao(): ProductDao
    abstract fun userAccountDao(): UserAccountDao
    abstract fun appConfigDao(): AppConfigDao
    abstract fun customerHelpDao(): CustomerHelpDao
    abstract fun technicianSettlementDao(): TechnicianSettlementDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE bills ADD COLUMN extraChargeType TEXT NOT NULL DEFAULT 'NONE'")
                db.execSQL("ALTER TABLE bills ADD COLUMN extraChargeAmount REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE customers ADD COLUMN phone TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE customers ADD COLUMN address TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE customers ADD COLUMN locationUrl TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `customer_help_requests` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `customerName` TEXT NOT NULL,
                        `phone` TEXT NOT NULL,
                        `address` TEXT NOT NULL,
                        `locationUrl` TEXT NOT NULL,
                        `helpDescription` TEXT NOT NULL,
                        `status` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE bills ADD COLUMN staffRating INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE bills ADD COLUMN staffReviewNote TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Bills: documentType, help bill flags, quotationNote
                db.execSQL("ALTER TABLE bills ADD COLUMN documentType TEXT NOT NULL DEFAULT 'BILL'")
                db.execSQL("ALTER TABLE bills ADD COLUMN isHelpBill INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE bills ADD COLUMN helpRequestId INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE bills ADD COLUMN quotationNote TEXT NOT NULL DEFAULT ''")

                // Products: folder grouping
                db.execSQL("ALTER TABLE products ADD COLUMN folder TEXT NOT NULL DEFAULT 'General'")

                // User accounts: phone + normalize 'staff' -> 'technician' + ensure single admin
                db.execSQL("ALTER TABLE user_accounts ADD COLUMN phone TEXT NOT NULL DEFAULT ''")
                db.execSQL("UPDATE user_accounts SET role = 'technician' WHERE LOWER(role) = 'staff'")
                db.execSQL(
                    """
                    DELETE FROM user_accounts
                    WHERE LOWER(role) = 'admin'
                    AND rowid NOT IN (
                        SELECT MIN(rowid) FROM user_accounts WHERE LOWER(role) = 'admin'
                    )
                    """.trimIndent()
                )

                // Customer help requests: technician acceptance & 15-min solve workflow
                db.execSQL("ALTER TABLE customer_help_requests ADD COLUMN acceptedBy TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE customer_help_requests ADD COLUMN acceptedByName TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE customer_help_requests ADD COLUMN acceptedByPhone TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE customer_help_requests ADD COLUMN acceptedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE customer_help_requests ADD COLUMN solvedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE customer_help_requests ADD COLUMN billId TEXT NOT NULL DEFAULT ''")

                // Technician settlements ledger
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `technician_settlements` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `technicianUsername` TEXT NOT NULL,
                        `amount` REAL NOT NULL,
                        `note` TEXT NOT NULL,
                        `recordedBy` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE user_accounts ADD COLUMN photoBase64 TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE bills ADD COLUMN secondaryChargeType TEXT NOT NULL DEFAULT 'NONE'")
                db.execSQL("ALTER TABLE bills ADD COLUMN secondaryChargeAmount REAL NOT NULL DEFAULT 0.0")
            }
        }

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "tse_billing.db"
                ).addMigrations(
                    MIGRATION_1_2,
                    MIGRATION_2_3,
                    MIGRATION_3_4,
                    MIGRATION_4_5,
                    MIGRATION_5_6,
                    MIGRATION_6_7
                ).build().also { INSTANCE = it }
            }
        }
    }
}
