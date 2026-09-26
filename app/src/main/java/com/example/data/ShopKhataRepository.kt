package com.example.data

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.abs
import kotlin.math.max

data class BalancePreview(
    val primaryAccountName: String,
    val primaryPhone: String,
    val primaryBefore: Double,
    val primaryDelta: Double,
    val primaryAfter: Double,
    val secondaryAccountName: String?,
    val secondaryBefore: Double?,
    val secondaryDelta: Double,
    val secondaryAfter: Double?,
    val customerName: String?,
    val customerDueBefore: Double?,
    val customerDueDelta: Double,
    val customerDueAfter: Double?,
    val summaryBangla: String
)

class ShopKhataRepository(
    private val dao: ShopKhataDao,
    val syncManager: FirebaseSyncManager
) {
    val servicesFlow = dao.observeServices()
    val accountTypesFlow = dao.observeAccountTypes()
    val accountsFlow = dao.observeAccounts()
    val transactionRulesFlow = dao.observeTransactionRules()
    val customersFlow = dao.observeCustomers()
    val transactionsFlow = dao.observeTransactions()
    val dueLedgerFlow = dao.observeDueLedgerEntries()
    val auditLogsFlow = dao.observeAuditLogs()
    val settingsFlow = dao.observeSettings()

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    companion object {
        const val CASH_ACCOUNT_ID = "acc_cash"
    }

    suspend fun ensureSeeded() {
        val existingAccounts = dao.getAllAccountsOnce()
        if (existingAccounts.isNotEmpty()) return

        val now = System.currentTimeMillis()
        val startOfToday = now - (3600_000L * 5)

        // 1. Seed Default Services (MFS, Recharge, Bill, Cash)
        val defaultServices = listOf(
            ServiceEntity("srv_cash", "Cash", "নগদ ক্যাশ", "CASH", "#047857", "cash", true, startOfToday),
            ServiceEntity("srv_bkash", "bKash", "বিকাশ", "MFS", "#E2136E", "bkash", true, startOfToday),
            ServiceEntity("srv_nagad", "Nagad", "নগদ", "MFS", "#F37021", "nagad", true, startOfToday),
            ServiceEntity("srv_rocket", "Rocket", "রকেট", "MFS", "#8C3494", "rocket", true, startOfToday),
            ServiceEntity("srv_upay", "Upay", "উপায়", "MFS", "#0054A6", "upay", true, startOfToday),
            ServiceEntity("srv_gp", "GP Recharge", "গ্রামীণফোন ফ্লেক্সিলোড", "RECHARGE", "#0078D4", "recharge", true, startOfToday),
            ServiceEntity("srv_robi", "Robi Recharge", "রবি ইজি লোড", "RECHARGE", "#E31837", "recharge", true, startOfToday),
            ServiceEntity("srv_bl", "Banglalink Recharge", "বাংলালিংক আইটপআপ", "RECHARGE", "#F58220", "recharge", true, startOfToday),
            ServiceEntity("srv_airtel", "Airtel Recharge", "এয়ারটেল রিচার্জ", "RECHARGE", "#ED1C24", "recharge", true, startOfToday),
            ServiceEntity("srv_teletalk", "Teletalk Recharge", "টেলিটক টেলিচার্জ", "RECHARGE", "#2E7D32", "recharge", true, startOfToday),
            ServiceEntity("srv_bill", "Bill Payment", "বিদ্যুৎ ও ইউটিলিটি বিল", "BILL", "#0F766E", "bill", true, startOfToday)
        )
        dao.upsertServices(defaultServices)

        // 2. Seed Flexible Account Types
        val defaultAccountTypes = listOf(
            AccountTypeEntity("type_agent", "Agent", "এজেন্ট", true, startOfToday),
            AccountTypeEntity("type_personal", "Personal", "পার্সোনাল", true, startOfToday),
            AccountTypeEntity("type_merchant", "Merchant", "মার্চেন্ট", true, startOfToday),
            AccountTypeEntity("type_recharge", "Recharge", "রিচার্জ সিম", true, startOfToday),
            AccountTypeEntity("type_cash", "Cash Drawer", "ক্যাশ ড্রয়ার", true, startOfToday),
            AccountTypeEntity("type_other", "Other", "অন্যান্য", true, startOfToday)
        )
        dao.upsertAccountTypes(defaultAccountTypes)

        // 3. Seed Physical Cash Account ONLY (Zero mock MFS accounts, zero mock customers, zero mock transactions)
        val cashAccount = ShopAccountEntity(
            id = CASH_ACCOUNT_ID,
            serviceId = "srv_cash",
            serviceName = "Cash",
            category = "CASH",
            accountType = "Cash Drawer",
            phoneNumber = "Shop Cash",
            nickname = "CASH IN HAND (নগদ ক্যাশ)",
            openingBalance = 0.0,
            openingBalanceDate = startOfToday,
            currentBalance = 0.0,
            isActive = true,
            isVisibleOnDashboard = true,
            colorHex = "#047857",
            notes = "দোকানের নগদ ক্যাশ ব্যালেন্স"
        )
        dao.upsertAccount(cashAccount)

        // 4. Seed Configurable Transaction Rules
        val defaultRules = listOf(
            TransactionTypeRuleEntity(
                id = "rule_cash_in",
                name = "Cash In",
                banglaName = "ক্যাশ ইন",
                category = "SERVICE",
                allowedServiceCategories = "MFS",
                accountDirection = "OUTFLOW",
                cashDirection = "INFLOW_PAID",
                customerRequired = false,
                allowDue = true,
                amountRequired = true,
                description = "অ্যাকাউন্ট থেকে টাকা কমবে (-), ক্যাশে প্রাপ্ত টাকা যোগ হবে (+), বাকি থাকলে কাস্টমার ডিউ বাড়বে",
                isSystemDefault = true
            ),
            TransactionTypeRuleEntity(
                id = "rule_cash_out",
                name = "Cash Out",
                banglaName = "ক্যাশ আউট",
                category = "SERVICE",
                allowedServiceCategories = "MFS",
                accountDirection = "INFLOW",
                cashDirection = "OUTFLOW_PAID",
                customerRequired = false,
                allowDue = false,
                amountRequired = true,
                description = "অ্যাকাউন্টে টাকা জমা হবে (+), ক্যাশ থেকে কাস্টমারকে দেওয়া টাকা কমবে (-)",
                isSystemDefault = true
            ),
            TransactionTypeRuleEntity(
                id = "rule_send_money",
                name = "Send Money",
                banglaName = "সেন্ড মানি",
                category = "SERVICE",
                allowedServiceCategories = "MFS",
                accountDirection = "OUTFLOW",
                cashDirection = "INFLOW_PAID",
                customerRequired = false,
                allowDue = true,
                amountRequired = true,
                description = "অ্যাকাউন্ট থেকে টাকা কমবে (-), ক্যাশ বাড়বে (+), বাকি থাকলে কাস্টমার ডিউ বাড়বে",
                isSystemDefault = true
            ),
            TransactionTypeRuleEntity(
                id = "rule_receive",
                name = "Receive Money",
                banglaName = "রিসিভ মানি",
                category = "SERVICE",
                allowedServiceCategories = "MFS",
                accountDirection = "INFLOW",
                cashDirection = "OUTFLOW_PAID",
                customerRequired = false,
                allowDue = false,
                amountRequired = true,
                description = "অ্যাকাউন্টে টাকা আসবে (+), ক্যাশ থেকে প্রদান করলে ক্যাশ কমবে (-)",
                isSystemDefault = true
            ),
            TransactionTypeRuleEntity(
                id = "rule_recharge",
                name = "Mobile Recharge",
                banglaName = "মোবাইল রিচার্জ",
                category = "SERVICE",
                allowedServiceCategories = "ALL",
                accountDirection = "OUTFLOW",
                cashDirection = "INFLOW_PAID",
                customerRequired = false,
                allowDue = true,
                amountRequired = true,
                description = "রিচার্জ ওয়ালেট থেকে কমবে (-), ক্যাশ বাড়বে (+) অথবা কাস্টমারের নামে বাকি থাকবে",
                isSystemDefault = true
            ),
            TransactionTypeRuleEntity(
                id = "rule_payment",
                name = "Payment / Bill",
                banglaName = "পেমেন্ট / বিল পরিশোধ",
                category = "SERVICE",
                allowedServiceCategories = "ALL",
                accountDirection = "OUTFLOW",
                cashDirection = "INFLOW_PAID",
                customerRequired = false,
                allowDue = true,
                amountRequired = true,
                description = "অ্যাকাউন্ট থেকে বিল/পেমেন্ট কমবে (-), ক্যাশে প্রাপ্ত টাকা বাড়বে (+)",
                isSystemDefault = true
            ),
            TransactionTypeRuleEntity(
                id = "rule_transfer",
                name = "Account Transfer",
                banglaName = "অ্যাকাউন্ট ট্রান্সফার",
                category = "TRANSFER",
                allowedServiceCategories = "ALL",
                accountDirection = "TRANSFER",
                cashDirection = "NONE",
                customerRequired = false,
                allowDue = false,
                amountRequired = true,
                description = "এক অ্যাকাউন্ট থেকে অন্য অ্যাকাউন্টে বা ক্যাশে ব্যালেন্স স্থানান্তর",
                isSystemDefault = true
            ),
            TransactionTypeRuleEntity(
                id = "rule_deposit",
                name = "Deposit / Add Balance",
                banglaName = "ব্যালেন্স জমা (Deposit)",
                category = "SERVICE",
                allowedServiceCategories = "ALL",
                accountDirection = "INFLOW",
                cashDirection = "NONE",
                customerRequired = false,
                allowDue = false,
                amountRequired = true,
                description = "নির্বাচিত অ্যাকাউন্ট বা ক্যাশে সরাসরি টাকা জমা (+)",
                isSystemDefault = true
            ),
            TransactionTypeRuleEntity(
                id = "rule_withdrawal",
                name = "Withdrawal / Cash Outflow",
                banglaName = "ব্যালেন্স উত্তোলন (Withdrawal)",
                category = "SERVICE",
                allowedServiceCategories = "ALL",
                accountDirection = "OUTFLOW",
                cashDirection = "NONE",
                customerRequired = false,
                allowDue = false,
                amountRequired = true,
                description = "নির্বাচিত অ্যাকাউন্ট বা ক্যাশ থেকে সরাসরি টাকা উত্তোলন (-)",
                isSystemDefault = true
            ),
            TransactionTypeRuleEntity(
                id = "rule_other",
                name = "Other",
                banglaName = "অন্যান্য (Other)",
                category = "SERVICE",
                allowedServiceCategories = "ALL",
                accountDirection = "OUTFLOW",
                cashDirection = "INFLOW_PAID",
                customerRequired = false,
                allowDue = true,
                amountRequired = true,
                description = "অন্যান্য ডিজিটাল সার্ভিস লেনদেন",
                isSystemDefault = true
            )
        )
        dao.upsertTransactionRules(defaultRules)

        // 5. Seed Default Settings (No mock customers or mock transactions)
        if (dao.getSettingsOnce() == null) {
            dao.upsertSettings(ShopSettingsEntity())
        }
    }

    /**
     * Deterministically previews the exact balance impact before confirming a transaction.
     */
    fun computeBalancePreview(
        primaryAccount: ShopAccountEntity,
        rule: TransactionTypeRuleEntity,
        amount: Double,
        paidAmount: Double,
        secondaryAccount: ShopAccountEntity?,
        customer: CustomerEntity?
    ): BalancePreview {
        val cleanAmount = max(0.0, amount)
        val cleanPaid = if (rule.allowDue) paidAmount.coerceIn(0.0, cleanAmount) else cleanAmount
        val unpaidDue = if (rule.allowDue) max(0.0, cleanAmount - cleanPaid) else 0.0

        var primaryDelta = 0.0
        var secondaryDelta = 0.0
        var dueDelta = 0.0

        when (rule.accountDirection) {
            "OUTFLOW" -> {
                primaryDelta = -cleanAmount
                if (rule.cashDirection == "INFLOW_PAID" && secondaryAccount != null && secondaryAccount.id != primaryAccount.id) {
                    secondaryDelta = cleanPaid
                } else if (rule.cashDirection == "OUTFLOW_PAID" && secondaryAccount != null && secondaryAccount.id != primaryAccount.id) {
                    secondaryDelta = -cleanPaid
                }
                if (rule.allowDue && customer != null) {
                    dueDelta = unpaidDue
                }
            }
            "INFLOW" -> {
                primaryDelta = cleanAmount
                if (rule.cashDirection == "OUTFLOW_PAID" && secondaryAccount != null && secondaryAccount.id != primaryAccount.id) {
                    secondaryDelta = -cleanPaid
                } else if (rule.cashDirection == "INFLOW_PAID" && secondaryAccount != null && secondaryAccount.id != primaryAccount.id) {
                    secondaryDelta = cleanPaid
                }
                if (rule.allowDue && customer != null) {
                    dueDelta = unpaidDue
                }
            }
            "TRANSFER" -> {
                primaryDelta = -cleanAmount
                if (secondaryAccount != null && secondaryAccount.id != primaryAccount.id) {
                    secondaryDelta = cleanAmount
                }
                dueDelta = 0.0
            }
        }

        val primaryAfter = primaryAccount.currentBalance + primaryDelta
        val secondaryBefore = secondaryAccount?.currentBalance
        val secondaryAfter = if (secondaryAccount != null && secondaryAccount.id != primaryAccount.id && secondaryDelta != 0.0) {
            secondaryAccount.currentBalance + secondaryDelta
        } else {
            secondaryAccount?.currentBalance
        }
        val dueBefore = customer?.currentDue
        val dueAfter = if (customer != null) customer.currentDue + dueDelta else null

        val summaryParts = mutableListOf<String>()
        summaryParts.add("${primaryAccount.nickname}: ${formatSignedTaka(primaryDelta)}")
        if (secondaryAccount != null && secondaryAccount.id != primaryAccount.id && secondaryDelta != 0.0) {
            summaryParts.add("${secondaryAccount.nickname}: ${formatSignedTaka(secondaryDelta)}")
        }
        if (customer != null && dueDelta != 0.0) {
            summaryParts.add("${customer.name} বাকি: ${formatSignedTaka(dueDelta)}")
        }

        return BalancePreview(
            primaryAccountName = primaryAccount.nickname,
            primaryPhone = primaryAccount.phoneNumber,
            primaryBefore = primaryAccount.currentBalance,
            primaryDelta = primaryDelta,
            primaryAfter = primaryAfter,
            secondaryAccountName = if (secondaryAccount != null && secondaryAccount.id != primaryAccount.id && (secondaryDelta != 0.0 || rule.accountDirection == "TRANSFER")) secondaryAccount.nickname else null,
            secondaryBefore = secondaryBefore,
            secondaryDelta = secondaryDelta,
            secondaryAfter = secondaryAfter,
            customerName = customer?.name,
            customerDueBefore = dueBefore,
            customerDueDelta = dueDelta,
            customerDueAfter = dueAfter,
            summaryBangla = summaryParts.joinToString("  •  ")
        )
    }

    /**
     * Records a standard or custom service/transfer transaction and recalculates all balances deterministically.
     */
    suspend fun recordServiceTransaction(
        accountId: String,
        ruleId: String,
        amount: Double,
        paidAmount: Double,
        paymentMethodAccountId: String?,
        customerId: String?,
        note: String,
        secondaryTransferAccountId: String? = null,
        customTimestamp: Long? = null
    ): ShopTransactionEntity? {
        val primaryAcc = dao.getAccountById(accountId) ?: return null
        val rule = dao.getTransactionRuleById(ruleId) ?: return null
        val customer = if (!customerId.isNullOrBlank()) dao.getCustomerById(customerId) else null

        val effectiveSecondaryId = when {
            rule.accountDirection == "TRANSFER" -> secondaryTransferAccountId
            rule.cashDirection != "NONE" -> paymentMethodAccountId ?: CASH_ACCOUNT_ID
            else -> null
        }
        val secondaryAcc = if (!effectiveSecondaryId.isNullOrBlank() && effectiveSecondaryId != primaryAcc.id) {
            dao.getAccountById(effectiveSecondaryId)
        } else null

        val cleanAmount = max(0.0, amount)
        val cleanPaid = if (rule.allowDue) paidAmount.coerceIn(0.0, cleanAmount) else cleanAmount
        val unpaidDue = if (rule.allowDue && customer != null) max(0.0, cleanAmount - cleanPaid) else 0.0

        val preview = computeBalancePreview(
            primaryAccount = primaryAcc,
            rule = rule,
            amount = cleanAmount,
            paidAmount = cleanPaid,
            secondaryAccount = secondaryAcc,
            customer = customer
        )

        val paymentStatus = when {
            !rule.allowDue || customer == null -> "PAID"
            unpaidDue <= 0.001 -> "PAID"
            cleanPaid <= 0.001 -> "DUE"
            else -> "PARTIAL"
        }

        val now = customTimestamp ?: System.currentTimeMillis()
        val txId = generateTransactionId(now)

        val tx = ShopTransactionEntity(
            id = txId,
            accountId = primaryAcc.id,
            accountName = primaryAcc.nickname,
            serviceName = primaryAcc.serviceName,
            accountPhone = primaryAcc.phoneNumber,
            secondaryAccountId = secondaryAcc?.id,
            secondaryAccountName = secondaryAcc?.nickname,
            transactionTypeId = rule.id,
            transactionTypeName = "${rule.name} (${rule.banglaName})",
            customerId = customer?.id,
            customerName = customer?.name,
            customerPhone = customer?.phone,
            amount = cleanAmount,
            paidAmount = cleanPaid,
            dueAmount = unpaidDue,
            paymentStatus = paymentStatus,
            paymentMethodAccountId = secondaryAcc?.id,
            paymentMethodName = secondaryAcc?.nickname,
            primaryAccountDelta = preview.primaryDelta,
            secondaryAccountDelta = preview.secondaryDelta,
            customerDueDelta = preview.customerDueDelta,
            primaryBalanceAfter = preview.primaryAfter,
            secondaryBalanceAfter = preview.secondaryAfter,
            customerDueAfter = preview.customerDueAfter,
            note = note.trim(),
            timestamp = now,
            updatedAt = now
        )

        dao.upsertTransaction(tx)

        // If linked to a customer, also record in customer's Due Ledger
        if (customer != null) {
            val actionType = when (paymentStatus) {
                "DUE" -> "DUE_CREATED"
                "PARTIAL" -> "PARTIAL_PAYMENT"
                else -> "PAID_SERVICE"
            }
            val actionLabel = when (paymentStatus) {
                "DUE" -> "${primaryAcc.nickname} - ${rule.banglaName} (সম্পূর্ণ বাকি)"
                "PARTIAL" -> "${primaryAcc.nickname} - ${rule.banglaName} (আংশিক পরিশোধ)"
                else -> "${primaryAcc.nickname} - ${rule.banglaName} (নগদ পরিশোধ)"
            }
            val dueEntry = DueLedgerEntryEntity(
                id = "DUE-${UUID.randomUUID().toString().take(8).uppercase()}",
                customerId = customer.id,
                customerName = customer.name,
                transactionId = tx.id,
                actionType = actionType,
                actionLabel = actionLabel,
                serviceAmount = cleanAmount,
                paidAmount = cleanPaid,
                dueDelta = preview.customerDueDelta,
                resultingDue = preview.customerDueAfter ?: customer.currentDue,
                accountUsedId = primaryAcc.id,
                accountUsedName = "${primaryAcc.nickname} (${primaryAcc.phoneNumber})",
                note = note.trim(),
                timestamp = now
            )
            dao.upsertDueLedgerEntry(dueEntry)
        }

        recalculateAllBalances()
        return dao.getTransactionById(txId)
    }

    /**
     * Collects outstanding due from a customer into Cash or any MFS/Recharge account (#18 & #19).
     */
    suspend fun collectCustomerDue(
        customerId: String,
        amountToCollect: Double,
        receivingAccountId: String,
        note: String
    ): Boolean {
        val customer = dao.getCustomerById(customerId) ?: return false
        val receivingAccount = dao.getAccountById(receivingAccountId) ?: return false
        val cleanCollect = max(0.0, amountToCollect)
        if (cleanCollect <= 0.0) return false

        val now = System.currentTimeMillis()
        val txId = generateTransactionId(now)
        val newDue = customer.currentDue - cleanCollect
        val isFullClear = newDue <= 0.01

        val tx = ShopTransactionEntity(
            id = txId,
            accountId = receivingAccount.id,
            accountName = receivingAccount.nickname,
            serviceName = receivingAccount.serviceName,
            accountPhone = receivingAccount.phoneNumber,
            secondaryAccountId = null,
            secondaryAccountName = null,
            transactionTypeId = "sys_due_collect",
            transactionTypeName = if (isFullClear) "Full Due Payment (সম্পূর্ণ বকেয়া পরিশোধ - CLEAR)" else "Due Collection (বকেয়া আদায়)",
            customerId = customer.id,
            customerName = customer.name,
            customerPhone = customer.phone,
            amount = cleanCollect,
            paidAmount = cleanCollect,
            dueAmount = 0.0,
            paymentStatus = "PAID",
            paymentMethodAccountId = receivingAccount.id,
            paymentMethodName = receivingAccount.nickname,
            primaryAccountDelta = cleanCollect, // Receiving account / Cash increases!
            secondaryAccountDelta = 0.0,
            customerDueDelta = -cleanCollect, // Customer due decreases!
            primaryBalanceAfter = receivingAccount.currentBalance + cleanCollect,
            secondaryBalanceAfter = null,
            customerDueAfter = newDue,
            note = note.ifBlank { if (isFullClear) "সম্পূর্ণ বকেয়া পরিশোধ (CLEAR)" else "বকেয়া আদায় (${receivingAccount.nickname})" },
            timestamp = now,
            updatedAt = now
        )
        dao.upsertTransaction(tx)

        val dueEntry = DueLedgerEntryEntity(
            id = "DUE-${UUID.randomUUID().toString().take(8).uppercase()}",
            customerId = customer.id,
            customerName = customer.name,
            transactionId = tx.id,
            actionType = if (isFullClear) "FULL_DUE_CLEAR" else "DUE_COLLECTED",
            actionLabel = if (isFullClear) "সম্পূর্ণ বকেয়া পরিশোধ (STATUS: CLEAR)" else "বকেয়া আদায় (${receivingAccount.nickname})",
            serviceAmount = 0.0,
            paidAmount = cleanCollect,
            dueDelta = -cleanCollect,
            resultingDue = newDue,
            accountUsedId = receivingAccount.id,
            accountUsedName = receivingAccount.nickname,
            note = tx.note,
            timestamp = now
        )
        dao.upsertDueLedgerEntry(dueEntry)

        recalculateAllBalances()
        return true
    }

    /**
     * Manually adds due to a customer without creating a normal service sale (#16).
     */
    suspend fun manualAddCustomerDue(
        customerId: String,
        amount: Double,
        reason: String
    ): Boolean {
        val customer = dao.getCustomerById(customerId) ?: return false
        val cleanAmount = max(0.0, amount)
        if (cleanAmount <= 0.0) return false

        val now = System.currentTimeMillis()
        val txId = generateTransactionId(now)
        val cashAcc = dao.getAccountById(CASH_ACCOUNT_ID)
        val newDue = customer.currentDue + cleanAmount

        val tx = ShopTransactionEntity(
            id = txId,
            accountId = cashAcc?.id ?: "manual_due",
            accountName = "Manual Due Entry (ম্যানুয়াল বাকি যোগ)",
            serviceName = "Due Ledger",
            accountPhone = customer.phone,
            transactionTypeId = "sys_manual_due_add",
            transactionTypeName = "Manual Add Due (পূর্বের বকেয়া যোগ)",
            customerId = customer.id,
            customerName = customer.name,
            customerPhone = customer.phone,
            amount = cleanAmount,
            paidAmount = 0.0,
            dueAmount = cleanAmount,
            paymentStatus = "DUE",
            primaryAccountDelta = 0.0, // No account balance change
            secondaryAccountDelta = 0.0,
            customerDueDelta = cleanAmount,
            primaryBalanceAfter = cashAcc?.currentBalance ?: 0.0,
            customerDueAfter = newDue,
            note = reason.ifBlank { "পূর্বের বকেয়া বা ম্যানুয়াল বাকি যোগ" },
            timestamp = now,
            updatedAt = now
        )
        dao.upsertTransaction(tx)

        dao.upsertDueLedgerEntry(
            DueLedgerEntryEntity(
                id = "DUE-${UUID.randomUUID().toString().take(8).uppercase()}",
                customerId = customer.id,
                customerName = customer.name,
                transactionId = tx.id,
                actionType = "MANUAL_INCREASE",
                actionLabel = "ম্যানুয়াল বাকি যোগ (Manual Due Increase)",
                serviceAmount = cleanAmount,
                paidAmount = 0.0,
                dueDelta = cleanAmount,
                resultingDue = newDue,
                accountUsedId = null,
                accountUsedName = "হিসাব খাতা সমন্বয়",
                note = tx.note,
                timestamp = now
            )
        )

        dao.insertAuditLog(
            AuditLogEntity(
                id = "AUD-${UUID.randomUUID().toString().take(8).uppercase()}",
                entityType = "CUSTOMER",
                entityId = customer.id,
                action = "MANUAL_DUE_ADD",
                title = "ম্যানুয়াল বাকি যোগ: ${customer.name}",
                previousValueSummary = "পূর্বের বাকি: ৳${formatAmount(customer.currentDue)}",
                newValueSummary = "নতুন বাকি: ৳${formatAmount(newDue)} (+৳${formatAmount(cleanAmount)})",
                reason = tx.note,
                timestamp = now
            )
        )

        recalculateAllBalances()
        return true
    }

    /**
     * Manually deducts due from a customer (#17).
     * Owner chooses whether this is an ACTUAL PAYMENT (increases selected account/cash)
     * or a MANUAL ADJUSTMENT (does not change any cash/account balance).
     */
    suspend fun manualDeductCustomerDue(
        customerId: String,
        amount: Double,
        isActualPayment: Boolean,
        receivingAccountId: String?,
        reason: String
    ): Boolean {
        val customer = dao.getCustomerById(customerId) ?: return false
        val cleanAmount = max(0.0, amount)
        if (cleanAmount <= 0.0) return false

        val now = System.currentTimeMillis()
        val txId = generateTransactionId(now)
        val targetAcc = if (isActualPayment && !receivingAccountId.isNullOrBlank()) {
            dao.getAccountById(receivingAccountId)
        } else {
            dao.getAccountById(CASH_ACCOUNT_ID)
        }
        val newDue = customer.currentDue - cleanAmount

        val tx = ShopTransactionEntity(
            id = txId,
            accountId = targetAcc?.id ?: CASH_ACCOUNT_ID,
            accountName = if (isActualPayment) (targetAcc?.nickname ?: "Cash") else "Due Adjustment (শুধু বাকি সমন্বয়)",
            serviceName = if (isActualPayment) (targetAcc?.serviceName ?: "Cash") else "Adjustment",
            accountPhone = targetAcc?.phoneNumber ?: customer.phone,
            transactionTypeId = "sys_manual_due_deduct",
            transactionTypeName = if (isActualPayment) "Due Deduct via Payment (বাকি জমা)" else "Manual Due Adjustment (বাকি মওকুফ/সমন্বয়)",
            customerId = customer.id,
            customerName = customer.name,
            customerPhone = customer.phone,
            amount = cleanAmount,
            paidAmount = if (isActualPayment) cleanAmount else 0.0,
            dueAmount = 0.0,
            paymentStatus = if (isActualPayment) "PAID" else "NA",
            paymentMethodAccountId = if (isActualPayment) targetAcc?.id else null,
            paymentMethodName = if (isActualPayment) targetAcc?.nickname else "Adjustment Only",
            primaryAccountDelta = if (isActualPayment) cleanAmount else 0.0,
            secondaryAccountDelta = 0.0,
            customerDueDelta = -cleanAmount,
            primaryBalanceAfter = (targetAcc?.currentBalance ?: 0.0) + (if (isActualPayment) cleanAmount else 0.0),
            customerDueAfter = newDue,
            note = reason.ifBlank { if (isActualPayment) "বাকি কমানো ও নগদ/অ্যাকাউন্টে জমা" else "ম্যানুয়াল বাকি সমন্বয় (ব্যালেন্স পরিবর্তন ছাড়া)" },
            timestamp = now,
            updatedAt = now
        )
        dao.upsertTransaction(tx)

        dao.upsertDueLedgerEntry(
            DueLedgerEntryEntity(
                id = "DUE-${UUID.randomUUID().toString().take(8).uppercase()}",
                customerId = customer.id,
                customerName = customer.name,
                transactionId = tx.id,
                actionType = if (isActualPayment) "MANUAL_DECREASE_PAYMENT" else "MANUAL_ADJUSTMENT",
                actionLabel = if (isActualPayment) "বাকি কর্তন (নগদ/অ্যাকাউন্টে প্রাপ্ত)" else "ম্যানুয়াল বাকি সমন্বয় (Adjustment)",
                serviceAmount = 0.0,
                paidAmount = if (isActualPayment) cleanAmount else 0.0,
                dueDelta = -cleanAmount,
                resultingDue = newDue,
                accountUsedId = if (isActualPayment) targetAcc?.id else null,
                accountUsedName = if (isActualPayment) targetAcc?.nickname else "শুধু হিসাব সমন্বয় (ক্যাশ অপরিবর্তিত)",
                note = tx.note,
                timestamp = now
            )
        )

        dao.insertAuditLog(
            AuditLogEntity(
                id = "AUD-${UUID.randomUUID().toString().take(8).uppercase()}",
                entityType = "CUSTOMER",
                entityId = customer.id,
                action = "MANUAL_DUE_DEDUCT",
                title = "ম্যানুয়াল বাকি কর্তন: ${customer.name}",
                previousValueSummary = "পূর্বের বাকি: ৳${formatAmount(customer.currentDue)}",
                newValueSummary = "নতুন বাকি: ৳${formatAmount(newDue)} (-৳${formatAmount(cleanAmount)})",
                reason = tx.note,
                timestamp = now
            )
        )

        recalculateAllBalances()
        return true
    }

    /**
     * Reconciles an account or physical cash balance (#21 & #36).
     * Creates an auditable adjustment transaction rather than silently overwriting the balance.
     */
    suspend fun reconcileAccountBalance(
        accountId: String,
        actualBalance: Double,
        reason: String
    ): Boolean {
        val acc = dao.getAccountById(accountId) ?: return false
        val expected = acc.currentBalance
        val diff = actualBalance - expected
        if (abs(diff) < 0.001) return false

        val now = System.currentTimeMillis()
        val txId = generateTransactionId(now)

        val tx = ShopTransactionEntity(
            id = txId,
            accountId = acc.id,
            accountName = acc.nickname,
            serviceName = acc.serviceName,
            accountPhone = acc.phoneNumber,
            transactionTypeId = "sys_reconcile",
            transactionTypeName = "Balance Reconciliation (ব্যালেন্স মিলকরণ সমন্বয়)",
            amount = abs(diff),
            paidAmount = abs(diff),
            dueAmount = 0.0,
            paymentStatus = "NA",
            primaryAccountDelta = diff,
            secondaryAccountDelta = 0.0,
            customerDueDelta = 0.0,
            primaryBalanceAfter = actualBalance,
            note = "প্রত্যাশিত: ৳${formatAmount(expected)}, প্রকৃত: ৳${formatAmount(actualBalance)} (${formatSignedTaka(diff)}) — কারণ: ${reason.ifBlank { "ক্যাশ/ব্যালেন্স গণনা সমন্বয়" }}",
            timestamp = now,
            updatedAt = now
        )
        dao.upsertTransaction(tx)

        dao.insertAuditLog(
            AuditLogEntity(
                id = "AUD-${UUID.randomUUID().toString().take(8).uppercase()}",
                entityType = "RECONCILE",
                entityId = acc.id,
                action = "RECONCILED",
                title = "ব্যালেন্স মিলকরণ: ${acc.nickname} (${acc.phoneNumber})",
                previousValueSummary = "Expected (হিসাব অনুযায়ী): ৳${formatAmount(expected)}",
                newValueSummary = "Actual (প্রকৃত ব্যালেন্স): ৳${formatAmount(actualBalance)} (পার্থক্য: ${formatSignedTaka(diff)})",
                reason = reason.ifBlank { "ব্যালেন্স গণনা সমন্বয়" },
                timestamp = now
            )
        )

        recalculateAllBalances()
        return true
    }

    /**
     * Edits an existing transaction, records full audit history (#4 & #27),
     * and automatically recalculates all affected account, cash, and customer due balances.
     */
    suspend fun editExistingTransaction(
        transactionId: String,
        newAccountId: String,
        newRuleId: String,
        newCustomerId: String?,
        newAmount: Double,
        newPaidAmount: Double,
        newPaymentMethodAccountId: String?,
        newSecondaryTransferAccountId: String?,
        newNote: String,
        editReason: String,
        newTimestamp: Long? = null
    ): Boolean {
        val oldTx = dao.getTransactionById(transactionId) ?: return false
        if (oldTx.isReversed) return false

        val primaryAcc = dao.getAccountById(newAccountId) ?: return false
        val rule = dao.getTransactionRuleById(newRuleId)
        val customer = if (!newCustomerId.isNullOrBlank()) dao.getCustomerById(newCustomerId) else null

        val cleanAmount = max(0.0, newAmount)
        val allowDue = rule?.allowDue ?: (oldTx.customerDueDelta != 0.0)
        val cleanPaid = if (allowDue) newPaidAmount.coerceIn(0.0, cleanAmount) else cleanAmount
        val unpaidDue = if (allowDue && customer != null) max(0.0, cleanAmount - cleanPaid) else 0.0

        var newPrimaryDelta = 0.0
        var newSecondaryDelta = 0.0
        var newDueDelta = 0.0
        var effectiveSecondaryAcc: ShopAccountEntity? = null

        if (rule != null) {
            val secId = when {
                rule.accountDirection == "TRANSFER" -> newSecondaryTransferAccountId
                rule.cashDirection != "NONE" -> newPaymentMethodAccountId ?: CASH_ACCOUNT_ID
                else -> null
            }
            effectiveSecondaryAcc = if (!secId.isNullOrBlank() && secId != primaryAcc.id) dao.getAccountById(secId) else null
            val preview = computeBalancePreview(primaryAcc, rule, cleanAmount, cleanPaid, effectiveSecondaryAcc, customer)
            newPrimaryDelta = preview.primaryDelta
            newSecondaryDelta = preview.secondaryDelta
            newDueDelta = preview.customerDueDelta
        } else {
            // System transaction edit (e.g., Due collect, Manual due, Reconcile)
            val signPrimary = if (oldTx.primaryAccountDelta >= 0) 1.0 else -1.0
            newPrimaryDelta = if (oldTx.primaryAccountDelta == 0.0) 0.0 else signPrimary * cleanAmount
            val signDue = if (oldTx.customerDueDelta >= 0) 1.0 else -1.0
            newDueDelta = if (oldTx.customerDueDelta == 0.0) 0.0 else signDue * cleanAmount
        }

        val paymentStatus = when {
            !allowDue || customer == null -> "PAID"
            unpaidDue <= 0.001 -> "PAID"
            cleanPaid <= 0.001 -> "DUE"
            else -> "PARTIAL"
        }

        val now = System.currentTimeMillis()
        val updatedTx = oldTx.copy(
            accountId = primaryAcc.id,
            accountName = primaryAcc.nickname,
            serviceName = primaryAcc.serviceName,
            accountPhone = primaryAcc.phoneNumber,
            secondaryAccountId = effectiveSecondaryAcc?.id,
            secondaryAccountName = effectiveSecondaryAcc?.nickname,
            transactionTypeId = rule?.id ?: oldTx.transactionTypeId,
            transactionTypeName = if (rule != null) "${rule.name} (${rule.banglaName})" else oldTx.transactionTypeName,
            customerId = customer?.id,
            customerName = customer?.name,
            customerPhone = customer?.phone,
            amount = cleanAmount,
            paidAmount = cleanPaid,
            dueAmount = unpaidDue,
            paymentStatus = paymentStatus,
            paymentMethodAccountId = effectiveSecondaryAcc?.id,
            paymentMethodName = effectiveSecondaryAcc?.nickname,
            primaryAccountDelta = newPrimaryDelta,
            secondaryAccountDelta = newSecondaryDelta,
            customerDueDelta = newDueDelta,
            note = newNote.trim(),
            timestamp = newTimestamp ?: oldTx.timestamp,
            isEdited = true,
            editCount = oldTx.editCount + 1,
            lastEditedAt = now,
            syncStatus = "PENDING",
            updatedAt = now
        )
        dao.upsertTransaction(updatedTx)

        // Update linked due ledger entry
        dao.deleteDueEntriesForTransaction(oldTx.id)
        if (customer != null && (newDueDelta != 0.0 || cleanAmount > 0.0)) {
            dao.upsertDueLedgerEntry(
                DueLedgerEntryEntity(
                    id = "DUE-${UUID.randomUUID().toString().take(8).uppercase()}",
                    customerId = customer.id,
                    customerName = customer.name,
                    transactionId = updatedTx.id,
                    actionType = if (unpaidDue > 0) "DUE_CREATED" else "PAID_SERVICE",
                    actionLabel = "${primaryAcc.nickname} - ${updatedTx.transactionTypeName} (সংশোধিত)",
                    serviceAmount = cleanAmount,
                    paidAmount = cleanPaid,
                    dueDelta = newDueDelta,
                    resultingDue = customer.currentDue - oldTx.customerDueDelta + newDueDelta,
                    accountUsedId = primaryAcc.id,
                    accountUsedName = "${primaryAcc.nickname} (${primaryAcc.phoneNumber})",
                    note = newNote.trim(),
                    timestamp = updatedTx.timestamp
                )
            )
        }

        // Record Audit History entry (#4 & #27)
        dao.insertAuditLog(
            AuditLogEntity(
                id = "AUD-${UUID.randomUUID().toString().take(8).uppercase()}",
                entityType = "TRANSACTION",
                entityId = oldTx.id,
                action = "EDITED",
                title = "Transaction edited (${oldTx.id})",
                previousValueSummary = "Previous: ৳${formatAmount(oldTx.amount)} | ${oldTx.accountName} (${formatSignedTaka(oldTx.primaryAccountDelta)}) | Paid: ৳${formatAmount(oldTx.paidAmount)} | Due: ৳${formatAmount(oldTx.dueAmount)}",
                newValueSummary = "New: ৳${formatAmount(updatedTx.amount)} | ${updatedTx.accountName} (${formatSignedTaka(updatedTx.primaryAccountDelta)}) | Paid: ৳${formatAmount(updatedTx.paidAmount)} | Due: ৳${formatAmount(updatedTx.dueAmount)}",
                reason = editReason.ifBlank { "লেনদেন তথ্য সংশোধন" },
                timestamp = now
            )
        )

        recalculateAllBalances()
        return true
    }

    /**
     * Reverses a transaction without deleting the historical record (#28 & #39).
     */
    suspend fun reverseTransaction(transactionId: String, reason: String): Boolean {
        val tx = dao.getTransactionById(transactionId) ?: return false
        if (tx.isReversed) return false

        val now = System.currentTimeMillis()
        val reversedTx = tx.copy(
            isReversed = true,
            reversedAt = now,
            reversalReason = reason.ifBlank { "লেনদেন বাতিল / রিভার্স করা হয়েছে" },
            syncStatus = "PENDING",
            updatedAt = now
        )
        dao.upsertTransaction(reversedTx)

        // If customer due was involved, add a clear reversal record in Due History
        if (tx.customerId != null && tx.customerDueDelta != 0.0) {
            val cust = dao.getCustomerById(tx.customerId)
            dao.upsertDueLedgerEntry(
                DueLedgerEntryEntity(
                    id = "DUE-${UUID.randomUUID().toString().take(8).uppercase()}",
                    customerId = tx.customerId,
                    customerName = tx.customerName ?: cust?.name ?: "",
                    transactionId = tx.id,
                    actionType = "REVERSAL",
                    actionLabel = "লেনদেন রিভার্স করা হয়েছে (${tx.id})",
                    serviceAmount = tx.amount,
                    paidAmount = tx.paidAmount,
                    dueDelta = -tx.customerDueDelta,
                    resultingDue = (cust?.currentDue ?: 0.0) - tx.customerDueDelta,
                    accountUsedId = tx.accountId,
                    accountUsedName = tx.accountName,
                    note = "রিভার্স কারণ: ${reversedTx.reversalReason}",
                    timestamp = now
                )
            )
        }

        dao.insertAuditLog(
            AuditLogEntity(
                id = "AUD-${UUID.randomUUID().toString().take(8).uppercase()}",
                entityType = "TRANSACTION",
                entityId = tx.id,
                action = "REVERSED",
                title = "Transaction Reversed (${tx.id})",
                previousValueSummary = "Original: ৳${formatAmount(tx.amount)} on ${tx.accountName} (${formatSignedTaka(tx.primaryAccountDelta)})",
                newValueSummary = "Reversed: Balance impact undone (${formatSignedTaka(-tx.primaryAccountDelta)})",
                reason = reversedTx.reversalReason,
                timestamp = now
            )
        )

        recalculateAllBalances()
        return true
    }

    /**
     * Permanently removes a transaction ONLY after explicit owner confirmation and records an Audit Log entry (#28).
     */
    suspend fun deleteTransactionWithAudit(transactionId: String, reason: String): Boolean {
        val tx = dao.getTransactionById(transactionId) ?: return false
        val now = System.currentTimeMillis()

        dao.deleteDueEntriesForTransaction(tx.id)
        dao.deleteTransactionById(tx.id)

        dao.insertAuditLog(
            AuditLogEntity(
                id = "AUD-${UUID.randomUUID().toString().take(8).uppercase()}",
                entityType = "TRANSACTION",
                entityId = tx.id,
                action = "DELETED",
                title = "Transaction Permanently Removed (${tx.id})",
                previousValueSummary = "Removed: ৳${formatAmount(tx.amount)} | ${tx.accountName} (${tx.accountPhone}) | ${tx.transactionTypeName}",
                newValueSummary = "Deleted from ledger and balances recalculated",
                reason = reason.ifBlank { "মালিক কর্তৃক স্থায়ীভাবে মুছে ফেলা হয়েছে" },
                timestamp = now
            )
        )

        recalculateAllBalances()
        return true
    }

    suspend fun deleteServiceById(serviceId: String) {
        if (serviceId == "srv_cash") return
        dao.deleteService(serviceId)
    }

    /**
     * Core Deterministic Balance Engine (#32 & #39):
     * Derives every account's currentBalance and every customer's currentDue
     * directly from Opening Balance + chronological sum of active transactions.
     */
    suspend fun recalculateAllBalances() {
        val accounts = dao.getAllAccountsOnce().associateBy { it.id }.toMutableMap()
        val customers = dao.getAllCustomersOnce().associateBy { it.id }.toMutableMap()
        val chronologicalTxs = dao.getAllTransactionsChronological()

        val runningAccountBalances = accounts.mapValues { it.value.openingBalance }.toMutableMap()
        val runningCustomerDues = customers.mapValues { it.value.openingDue }.toMutableMap()

        val updatedTransactions = mutableListOf<ShopTransactionEntity>()

        for (tx in chronologicalTxs) {
            if (tx.isReversed) {
                updatedTransactions.add(tx)
                continue
            }

            // Primary account update
            val currentPrim = runningAccountBalances[tx.accountId] ?: 0.0
            val nextPrim = currentPrim + tx.primaryAccountDelta
            if (runningAccountBalances.containsKey(tx.accountId)) {
                runningAccountBalances[tx.accountId] = nextPrim
            }

            // Secondary account update (Cash or transfer target)
            var nextSec: Double? = null
            val secId = tx.secondaryAccountId
            if (!secId.isNullOrBlank() && runningAccountBalances.containsKey(secId)) {
                val currentSec = runningAccountBalances[secId] ?: 0.0
                nextSec = currentSec + tx.secondaryAccountDelta
                runningAccountBalances[secId] = nextSec
            }

            // Customer due update
            var nextDue: Double? = null
            val custId = tx.customerId
            if (!custId.isNullOrBlank() && runningCustomerDues.containsKey(custId)) {
                val currentDue = runningCustomerDues[custId] ?: 0.0
                nextDue = currentDue + tx.customerDueDelta
                runningCustomerDues[custId] = nextDue
            }

            if (tx.primaryBalanceAfter != nextPrim || tx.secondaryBalanceAfter != nextSec || tx.customerDueAfter != nextDue) {
                updatedTransactions.add(
                    tx.copy(
                        primaryBalanceAfter = nextPrim,
                        secondaryBalanceAfter = nextSec,
                        customerDueAfter = nextDue
                    )
                )
            }
        }

        if (updatedTransactions.isNotEmpty()) {
            dao.upsertTransactions(updatedTransactions)
        }

        val updatedAccounts = accounts.values.map { acc ->
            val derivedBalance = runningAccountBalances[acc.id] ?: acc.openingBalance
            acc.copy(currentBalance = derivedBalance)
        }
        dao.upsertAccounts(updatedAccounts)

        val updatedCustomers = customers.values.map { cust ->
            val derivedDue = runningCustomerDues[cust.id] ?: cust.openingDue
            cust.copy(currentDue = derivedDue)
        }
        dao.upsertCustomers(updatedCustomers)
    }

    // Account CRUD with Audit
    suspend fun saveAccount(
        existingId: String?,
        serviceId: String,
        accountType: String,
        phoneNumber: String,
        nickname: String,
        openingBalance: Double,
        openingBalanceDate: Long,
        isActive: Boolean,
        isVisibleOnDashboard: Boolean,
        notes: String
    ) {
        val services = dao.getAllServicesOnce()
        val service = services.find { it.id == serviceId }
        val now = System.currentTimeMillis()

        if (existingId != null) {
            val oldAcc = dao.getAccountById(existingId)
            val updated = ShopAccountEntity(
                id = existingId,
                serviceId = serviceId,
                serviceName = service?.name ?: oldAcc?.serviceName ?: "Custom",
                category = service?.category ?: oldAcc?.category ?: "MFS",
                accountType = accountType,
                phoneNumber = phoneNumber.trim(),
                nickname = nickname.trim(),
                openingBalance = openingBalance,
                openingBalanceDate = openingBalanceDate,
                currentBalance = oldAcc?.currentBalance ?: openingBalance,
                isActive = isActive,
                isVisibleOnDashboard = isVisibleOnDashboard,
                colorHex = service?.colorHex ?: oldAcc?.colorHex ?: "#065F46",
                notes = notes.trim(),
                updatedAt = now
            )
            dao.upsertAccount(updated)
            if (oldAcc != null && oldAcc.openingBalance != openingBalance) {
                dao.insertAuditLog(
                    AuditLogEntity(
                        id = "AUD-${UUID.randomUUID().toString().take(8).uppercase()}",
                        entityType = "ACCOUNT",
                        entityId = existingId,
                        action = "EDITED",
                        title = "Account edited: ${updated.nickname} (${updated.phoneNumber})",
                        previousValueSummary = "Opening Balance: ৳${formatAmount(oldAcc.openingBalance)}",
                        newValueSummary = "Opening Balance: ৳${formatAmount(openingBalance)}",
                        reason = "অ্যাকাউন্ট তথ্য হালনাগাদ",
                        timestamp = now
                    )
                )
            }
        } else {
            val newId = "acc_${UUID.randomUUID().toString().take(8)}"
            val created = ShopAccountEntity(
                id = newId,
                serviceId = serviceId,
                serviceName = service?.name ?: "Custom",
                category = service?.category ?: "MFS",
                accountType = accountType,
                phoneNumber = phoneNumber.trim(),
                nickname = nickname.trim(),
                openingBalance = openingBalance,
                openingBalanceDate = openingBalanceDate,
                currentBalance = openingBalance,
                isActive = isActive,
                isVisibleOnDashboard = isVisibleOnDashboard,
                colorHex = service?.colorHex ?: "#065F46",
                notes = notes.trim(),
                updatedAt = now
            )
            dao.upsertAccount(created)
        }
        recalculateAllBalances()
    }

    // Customer CRUD
    suspend fun saveCustomer(
        existingId: String?,
        name: String,
        phone: String,
        address: String,
        notes: String,
        customField: String,
        openingDue: Double
    ): CustomerEntity {
        val now = System.currentTimeMillis()
        val id = existingId ?: "cust_${UUID.randomUUID().toString().take(8)}"
        val existing = if (existingId != null) dao.getCustomerById(existingId) else null
        val customer = CustomerEntity(
            id = id,
            name = name.trim(),
            phone = phone.trim(),
            address = address.trim(),
            notes = notes.trim(),
            customField = customField.trim(),
            openingDue = openingDue,
            currentDue = existing?.currentDue ?: openingDue,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now
        )
        dao.upsertCustomer(customer)
        recalculateAllBalances()
        return dao.getCustomerById(id) ?: customer
    }

    // Service CRUD
    suspend fun saveService(
        existingId: String?,
        name: String,
        banglaName: String,
        category: String,
        colorHex: String,
        isActive: Boolean
    ) {
        val id = existingId ?: "srv_${UUID.randomUUID().toString().take(8)}"
        dao.upsertService(
            ServiceEntity(
                id = id,
                name = name.trim(),
                banglaName = banglaName.trim().ifBlank { name.trim() },
                category = category,
                colorHex = colorHex,
                iconKey = category.lowercase(),
                isActive = isActive,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    // Custom Account Type CRUD
    suspend fun saveAccountType(existingId: String?, name: String, banglaName: String) {
        val id = existingId ?: "type_${UUID.randomUUID().toString().take(8)}"
        dao.upsertAccountType(
            AccountTypeEntity(
                id = id,
                name = name.trim(),
                banglaName = banglaName.trim().ifBlank { name.trim() },
                isDefault = false,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    // Custom Transaction Rule CRUD
    suspend fun saveTransactionRule(
        existingId: String?,
        name: String,
        banglaName: String,
        allowedServiceCategories: String,
        accountDirection: String,
        cashDirection: String,
        customerRequired: Boolean,
        allowDue: Boolean,
        description: String
    ) {
        val id = existingId ?: "rule_${UUID.randomUUID().toString().take(8)}"
        dao.upsertTransactionRule(
            TransactionTypeRuleEntity(
                id = id,
                name = name.trim(),
                banglaName = banglaName.trim().ifBlank { name.trim() },
                category = if (accountDirection == "TRANSFER") "TRANSFER" else "SERVICE",
                allowedServiceCategories = allowedServiceCategories,
                accountDirection = accountDirection,
                cashDirection = cashDirection,
                customerRequired = customerRequired,
                allowDue = allowDue,
                amountRequired = true,
                description = description.trim(),
                isSystemDefault = false,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun updateSettings(transform: (ShopSettingsEntity) -> ShopSettingsEntity) {
        val current = dao.getSettingsOnce() ?: ShopSettingsEntity()
        dao.upsertSettings(transform(current))
    }

    suspend fun syncWithFirebase(): CloudSyncResult {
        return syncManager.syncAllToFirestore(dao)
    }

    suspend fun pullFromFirebase(): CloudSyncResult {
        val res = syncManager.pullAllFromFirestore(dao)
        if (res.success) {
            recalculateAllBalances()
        }
        return res
    }

    // Full JSON Backup & Restore (#40)
    suspend fun exportFullBackupJson(): String {
        val now = System.currentTimeMillis()
        val settings = (dao.getSettingsOnce() ?: ShopSettingsEntity()).copy(lastBackupTimestamp = now)
        dao.upsertSettings(settings)

        val payload = FullShopBackupPayload(
            exportedAt = now,
            settings = settings,
            services = dao.getAllServicesOnce(),
            accountTypes = dao.getAllAccountTypesOnce(),
            accounts = dao.getAllAccountsOnce(),
            transactionRules = dao.getAllTransactionRulesOnce(),
            customers = dao.getAllCustomersOnce(),
            transactions = dao.getAllTransactionsChronological(),
            dueEntries = dao.getAllDueLedgerEntriesOnce(),
            auditLogs = dao.getAllAuditLogsOnce()
        )
        val adapter = moshi.adapter(FullShopBackupPayload::class.java).indent("  ")
        return adapter.toJson(payload)
    }

    suspend fun restoreFromBackupJson(json: String): Boolean {
        return try {
            val adapter = moshi.adapter(FullShopBackupPayload::class.java)
            val payload = adapter.fromJson(json) ?: return false

            dao.clearServices()
            dao.clearAccountTypes()
            dao.clearAccounts()
            dao.clearTransactionRules()
            dao.clearCustomers()
            dao.clearTransactions()
            dao.clearDueLedgerEntries()
            dao.clearAuditLogs()

            dao.upsertSettings(payload.settings.copy(lastBackupTimestamp = System.currentTimeMillis()))
            dao.upsertServices(payload.services)
            dao.upsertAccountTypes(payload.accountTypes)
            dao.upsertAccounts(payload.accounts)
            dao.upsertTransactionRules(payload.transactionRules)
            dao.upsertCustomers(payload.customers)
            dao.upsertTransactions(payload.transactions)
            dao.upsertDueLedgerEntries(payload.dueEntries)
            dao.upsertAuditLogs(payload.auditLogs)

            recalculateAllBalances()
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun generateTransactionId(timestamp: Long): String {
        val datePart = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date(timestamp))
        val randPart = UUID.randomUUID().toString().take(4).uppercase()
        return "TXN-$datePart-$randPart"
    }
}

fun formatAmount(amount: Double): String {
    val isWhole = abs(amount - amount.toLong()) < 0.005
    return if (isWhole) {
        String.format(Locale.US, "%,d", amount.toLong())
    } else {
        String.format(Locale.US, "%,.2f", amount)
    }
}

fun formatTaka(amount: Double): String {
    return if (amount < 0) {
        "-৳${formatAmount(abs(amount))}"
    } else {
        "৳${formatAmount(amount)}"
    }
}

fun formatSignedTaka(delta: Double): String {
    return when {
        delta > 0.001 -> "+৳${formatAmount(delta)}"
        delta < -0.001 -> "-৳${formatAmount(abs(delta))}"
        else -> "৳0"
    }
}

fun formatDateTime(timestamp: Long): String {
    return SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.US).format(Date(timestamp))
}

fun formatShortDate(timestamp: Long): String {
    return SimpleDateFormat("dd MMM", Locale.US).format(Date(timestamp))
}
