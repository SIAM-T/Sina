package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        ServiceEntity::class,
        AccountTypeEntity::class,
        ShopAccountEntity::class,
        TransactionTypeRuleEntity::class,
        CustomerEntity::class,
        ShopTransactionEntity::class,
        DueLedgerEntryEntity::class,
        AuditLogEntity::class,
        ShopSettingsEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class ShopKhataDatabase : RoomDatabase() {
    abstract fun dao(): ShopKhataDao

    companion object {
        @Volatile
        private var INSTANCE: ShopKhataDatabase? = null

        fun getInstance(context: Context): ShopKhataDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ShopKhataDatabase::class.java,
                    "hisab_khata_shop_db"
                )
                    .fallbackToDestructiveMigration(true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
