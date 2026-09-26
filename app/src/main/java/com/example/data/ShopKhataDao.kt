package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ShopKhataDao {

    // Services
    @Query("SELECT * FROM services ORDER BY category ASC, name ASC")
    fun observeServices(): Flow<List<ServiceEntity>>

    @Query("SELECT * FROM services")
    suspend fun getAllServicesOnce(): List<ServiceEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertService(service: ServiceEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertServices(services: List<ServiceEntity>)

    @Query("DELETE FROM services WHERE id = :serviceId")
    suspend fun deleteService(serviceId: String)

    // Account Types
    @Query("SELECT * FROM account_types ORDER BY isDefault DESC, name ASC")
    fun observeAccountTypes(): Flow<List<AccountTypeEntity>>

    @Query("SELECT * FROM account_types")
    suspend fun getAllAccountTypesOnce(): List<AccountTypeEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAccountType(accountType: AccountTypeEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAccountTypes(accountTypes: List<AccountTypeEntity>)

    // Shop Accounts
    @Query("SELECT * FROM shop_accounts ORDER BY CASE WHEN category = 'CASH' THEN 0 WHEN category = 'MFS' THEN 1 WHEN category = 'RECHARGE' THEN 2 ELSE 3 END, serviceName ASC, nickname ASC")
    fun observeAccounts(): Flow<List<ShopAccountEntity>>

    @Query("SELECT * FROM shop_accounts")
    suspend fun getAllAccountsOnce(): List<ShopAccountEntity>

    @Query("SELECT * FROM shop_accounts WHERE id = :id LIMIT 1")
    suspend fun getAccountById(id: String): ShopAccountEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAccount(account: ShopAccountEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAccounts(accounts: List<ShopAccountEntity>)

    @Update
    suspend fun updateAccount(account: ShopAccountEntity)

    // Transaction Type Rules
    @Query("SELECT * FROM transaction_type_rules ORDER BY isSystemDefault DESC, name ASC")
    fun observeTransactionRules(): Flow<List<TransactionTypeRuleEntity>>

    @Query("SELECT * FROM transaction_type_rules")
    suspend fun getAllTransactionRulesOnce(): List<TransactionTypeRuleEntity>

    @Query("SELECT * FROM transaction_type_rules WHERE id = :id LIMIT 1")
    suspend fun getTransactionRuleById(id: String): TransactionTypeRuleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTransactionRule(rule: TransactionTypeRuleEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTransactionRules(rules: List<TransactionTypeRuleEntity>)

    // Customers
    @Query("SELECT * FROM customers ORDER BY currentDue DESC, name ASC")
    fun observeCustomers(): Flow<List<CustomerEntity>>

    @Query("SELECT * FROM customers")
    suspend fun getAllCustomersOnce(): List<CustomerEntity>

    @Query("SELECT * FROM customers WHERE id = :id LIMIT 1")
    suspend fun getCustomerById(id: String): CustomerEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCustomer(customer: CustomerEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCustomers(customers: List<CustomerEntity>)

    // Transactions
    @Query("SELECT * FROM shop_transactions ORDER BY timestamp DESC")
    fun observeTransactions(): Flow<List<ShopTransactionEntity>>

    @Query("SELECT * FROM shop_transactions ORDER BY timestamp ASC")
    suspend fun getAllTransactionsChronological(): List<ShopTransactionEntity>

    @Query("SELECT * FROM shop_transactions WHERE id = :id LIMIT 1")
    suspend fun getTransactionById(id: String): ShopTransactionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTransaction(transaction: ShopTransactionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTransactions(transactions: List<ShopTransactionEntity>)

    @Query("UPDATE shop_transactions SET syncStatus = 'SYNCED' WHERE id IN (:ids)")
    suspend fun markTransactionsSynced(ids: List<String>)

    @Query("DELETE FROM shop_transactions WHERE id = :id")
    suspend fun deleteTransactionById(id: String)

    @Query("DELETE FROM shop_accounts WHERE id = :id")
    suspend fun deleteAccountById(id: String)

    @Query("DELETE FROM customers WHERE id = :id")
    suspend fun deleteCustomerById(id: String)

    // Due Ledger Entries
    @Query("SELECT * FROM due_ledger_entries ORDER BY timestamp DESC")
    fun observeDueLedgerEntries(): Flow<List<DueLedgerEntryEntity>>

    @Query("SELECT * FROM due_ledger_entries")
    suspend fun getAllDueLedgerEntriesOnce(): List<DueLedgerEntryEntity>

    @Query("SELECT * FROM due_ledger_entries WHERE transactionId = :transactionId")
    suspend fun getDueEntriesForTransaction(transactionId: String): List<DueLedgerEntryEntity>

    @Query("DELETE FROM due_ledger_entries WHERE transactionId = :transactionId")
    suspend fun deleteDueEntriesForTransaction(transactionId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDueLedgerEntry(entry: DueLedgerEntryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDueLedgerEntries(entries: List<DueLedgerEntryEntity>)

    // Audit Logs
    @Query("SELECT * FROM audit_logs ORDER BY timestamp DESC")
    fun observeAuditLogs(): Flow<List<AuditLogEntity>>

    @Query("SELECT * FROM audit_logs")
    suspend fun getAllAuditLogsOnce(): List<AuditLogEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAuditLog(log: AuditLogEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAuditLogs(logs: List<AuditLogEntity>)

    // Settings
    @Query("SELECT * FROM shop_settings WHERE id = 1 LIMIT 1")
    fun observeSettings(): Flow<ShopSettingsEntity?>

    @Query("SELECT * FROM shop_settings WHERE id = 1 LIMIT 1")
    suspend fun getSettingsOnce(): ShopSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSettings(settings: ShopSettingsEntity)

    // Full Clear for Restore
    @Query("DELETE FROM services")
    suspend fun clearServices()

    @Query("DELETE FROM account_types")
    suspend fun clearAccountTypes()

    @Query("DELETE FROM shop_accounts")
    suspend fun clearAccounts()

    @Query("DELETE FROM transaction_type_rules")
    suspend fun clearTransactionRules()

    @Query("DELETE FROM customers")
    suspend fun clearCustomers()

    @Query("DELETE FROM shop_transactions")
    suspend fun clearTransactions()

    @Query("DELETE FROM due_ledger_entries")
    suspend fun clearDueLedgerEntries()

    @Query("DELETE FROM audit_logs")
    suspend fun clearAuditLogs()
}
