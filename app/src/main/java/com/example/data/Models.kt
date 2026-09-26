package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
@Entity(tableName = "services")
data class ServiceEntity(
    @PrimaryKey val id: String,
    val name: String,
    val banglaName: String,
    val category: String, // "MFS", "RECHARGE", "BILL", "CASH", "OTHER"
    val colorHex: String,
    val iconKey: String, // "bkash", "nagad", "rocket", "upay", "recharge", "cash", "bill", "custom"
    val isActive: Boolean = true,
    val updatedAt: Long = System.currentTimeMillis()
)

@JsonClass(generateAdapter = true)
@Entity(tableName = "account_types")
data class AccountTypeEntity(
    @PrimaryKey val id: String,
    val name: String,
    val banglaName: String,
    val isDefault: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
)

@JsonClass(generateAdapter = true)
@Entity(tableName = "shop_accounts")
data class ShopAccountEntity(
    @PrimaryKey val id: String,
    val serviceId: String,
    val serviceName: String,
    val category: String, // "MFS", "RECHARGE", "CASH", "OTHER"
    val accountType: String, // "Agent", "Personal", "Merchant", "Recharge", "Cash", or custom
    val phoneNumber: String, // Multiple accounts can share the exact same phone number!
    val nickname: String, // e.g., "bKash Agent", "Nagad Personal", "GP Recharge"
    val openingBalance: Double,
    val openingBalanceDate: Long,
    val currentBalance: Double,
    val isActive: Boolean = true,
    val isVisibleOnDashboard: Boolean = true,
    val colorHex: String = "#065F46",
    val notes: String = "",
    val updatedAt: Long = System.currentTimeMillis()
)

@JsonClass(generateAdapter = true)
@Entity(tableName = "transaction_type_rules")
data class TransactionTypeRuleEntity(
    @PrimaryKey val id: String,
    val name: String,
    val banglaName: String,
    val category: String, // "SERVICE", "TRANSFER", "DUE", "ADJUSTMENT"
    val allowedServiceCategories: String, // "ALL", "MFS", "RECHARGE", "CASH"
    /**
     * How the primary selected account is affected by `amount`:
     * - "OUTFLOW": Primary account decreases by `amount` (e.g., Customer Cash In, Send Money, Recharge)
     * - "INFLOW": Primary account increases by `amount` (e.g., Customer Cash Out, Receive Money, Payment)
     * - "TRANSFER": Primary (source) account decreases by `amount`, Secondary (destination) account increases by `amount`
     * - "DUE_COLLECT": Primary (receiving) account increases by `paidAmount`, Customer Due decreases by `paidAmount`
     * - "MANUAL_DUE_ADD": Primary account unchanged (0), Customer Due increases by `amount`
     * - "MANUAL_DUE_DEDUCT": Customer Due decreases by `amount`; if actual payment, receiving account increases by `paidAmount`
     * - "RECONCILE": Primary account changes by signed adjustment delta
     */
    val accountDirection: String,
    /**
     * How physical CASH (or selected payment account) is automatically affected during normal service transactions:
     * - "INFLOW_PAID": Cash/Payment account increases by `paidAmount` (actual money received from customer)
     * - "OUTFLOW_PAID": Cash/Payment account decreases by `paidAmount` (actual money paid to customer)
     * - "NONE": No secondary cash movement
     */
    val cashDirection: String,
    val customerRequired: Boolean = false,
    val allowDue: Boolean = true,
    val amountRequired: Boolean = true,
    val description: String = "",
    val isSystemDefault: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
)

@JsonClass(generateAdapter = true)
@Entity(tableName = "customers")
data class CustomerEntity(
    @PrimaryKey val id: String,
    val name: String,
    val phone: String,
    val address: String = "",
    val notes: String = "",
    val customField: String = "",
    val openingDue: Double = 0.0,
    val currentDue: Double = 0.0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@JsonClass(generateAdapter = true)
@Entity(tableName = "shop_transactions")
data class ShopTransactionEntity(
    @PrimaryKey val id: String,
    val accountId: String,
    val accountName: String,
    val serviceName: String,
    val accountPhone: String,
    val secondaryAccountId: String? = null,
    val secondaryAccountName: String? = null,
    val transactionTypeId: String,
    val transactionTypeName: String,
    val customerId: String? = null,
    val customerName: String? = null,
    val customerPhone: String? = null,
    val amount: Double, // Total transaction/service amount
    val paidAmount: Double, // Amount actually received/paid
    val dueAmount: Double, // Unpaid due from this transaction
    val paymentStatus: String, // "PAID", "PARTIAL", "DUE", "NA"
    val paymentMethodAccountId: String? = null,
    val paymentMethodName: String? = null,
    val primaryAccountDelta: Double, // Signed change to primary account
    val secondaryAccountDelta: Double, // Signed change to secondary/cash account
    val customerDueDelta: Double, // Signed change to customer due
    val primaryBalanceAfter: Double,
    val secondaryBalanceAfter: Double? = null,
    val customerDueAfter: Double? = null,
    val note: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val isEdited: Boolean = false,
    val editCount: Int = 0,
    val lastEditedAt: Long? = null,
    val isReversed: Boolean = false,
    val reversedAt: Long? = null,
    val reversalReason: String = "",
    val syncStatus: String = "PENDING", // "SYNCED", "PENDING"
    val updatedAt: Long = System.currentTimeMillis()
)

@JsonClass(generateAdapter = true)
@Entity(tableName = "due_ledger_entries")
data class DueLedgerEntryEntity(
    @PrimaryKey val id: String,
    val customerId: String,
    val customerName: String,
    val transactionId: String? = null,
    /**
     * Action types:
     * - "DUE_CREATED" (During full due service transaction)
     * - "PARTIAL_PAYMENT" (During partial paid service transaction)
     * - "PAID_SERVICE" (Full paid service transaction recorded on customer ledger)
     * - "DUE_COLLECTED" (Partial or full due collection)
     * - "FULL_DUE_CLEAR" (Complete due payment resulting in ৳0 due)
     * - "MANUAL_INCREASE" (Owner manually added due)
     * - "MANUAL_DECREASE_PAYMENT" (Owner manually deducted due via actual payment)
     * - "MANUAL_ADJUSTMENT" (Owner manually adjusted/waived due without cash impact)
     * - "REVERSAL" (Reversed transaction/due action)
     */
    val actionType: String,
    val actionLabel: String,
    val serviceAmount: Double,
    val paidAmount: Double,
    val dueDelta: Double, // +ve increases due, -ve reduces due
    val resultingDue: Double,
    val accountUsedId: String? = null,
    val accountUsedName: String? = null,
    val note: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

@JsonClass(generateAdapter = true)
@Entity(tableName = "audit_logs")
data class AuditLogEntity(
    @PrimaryKey val id: String,
    val entityType: String, // "TRANSACTION", "ACCOUNT", "CUSTOMER", "RECONCILE"
    val entityId: String,
    val action: String, // "EDITED", "REVERSED", "RECONCILED", "MANUAL_DUE", "CREATED"
    val title: String,
    val previousValueSummary: String,
    val newValueSummary: String,
    val reason: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

@JsonClass(generateAdapter = true)
@Entity(tableName = "shop_settings")
data class ShopSettingsEntity(
    @PrimaryKey val id: Int = 1,
    val shopName: String = "ডিজিটাল হিসাব খাতা",
    val ownerPhone: String = "",
    val pinCode: String = "", // Empty = no PIN enabled
    val isPinEnabled: Boolean = false,
    val showAllAccountsOnDashboard: Boolean = true,
    val hideInactiveAccounts: Boolean = false,
    val groupAccountsBy: String = "SERVICE", // "SERVICE" or "PHONE"
    val lastBackupTimestamp: Long? = null,
    val lastCloudSyncTimestamp: Long? = null,
    val cloudUserEmail: String? = null
)

@JsonClass(generateAdapter = true)
data class FullShopBackupPayload(
    val exportedAt: Long = System.currentTimeMillis(),
    val appVersion: String = "1.0",
    val settings: ShopSettingsEntity,
    val services: List<ServiceEntity>,
    val accountTypes: List<AccountTypeEntity>,
    val accounts: List<ShopAccountEntity>,
    val transactionRules: List<TransactionTypeRuleEntity>,
    val customers: List<CustomerEntity>,
    val transactions: List<ShopTransactionEntity>,
    val dueEntries: List<DueLedgerEntryEntity>,
    val auditLogs: List<AuditLogEntity>
)
