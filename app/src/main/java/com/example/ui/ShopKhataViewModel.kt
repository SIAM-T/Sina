package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar
import kotlin.math.abs

enum class MainTab {
    HOME, TRANSACTION, DUE, HISTORY, MORE
}

enum class MoreSubScreen {
    MENU,
    ACCOUNTS,
    CUSTOMERS,
    SERVICES_AND_RULES,
    REPORTS,
    AUDIT_HISTORY,
    BACKUP_AND_SECURITY,
    ACCOUNT_STATEMENT,
    CUSTOMER_LEDGER
}

data class AccountFlowSummary(
    val account: ShopAccountEntity,
    val openingInPeriod: Double,
    val incoming: Double,
    val outgoing: Double,
    val adjustments: Double,
    val closingBalance: Double,
    val txCount: Int
)

data class PeriodFinancialReport(
    val periodLabel: String,
    val startTimestamp: Long,
    val endTimestamp: Long,
    val openingCash: Double,
    val cashReceived: Double,
    val cashPaid: Double,
    val cashAdjustments: Double,
    val closingCash: Double,
    val accountSummaries: List<AccountFlowSummary>,
    val totalTransactionVolume: Double,
    val totalTransactionCount: Int,
    val dueCreatedInPeriod: Double,
    val dueCollectedInPeriod: Double,
    val currentTotalCustomerDue: Double
)

class ShopKhataViewModel(application: Application) : AndroidViewModel(application) {

    private val database = ShopKhataDatabase.getInstance(application)
    private val syncManager = FirebaseSyncManager(application)
    val repository = ShopKhataRepository(database.dao(), syncManager)

    val services: StateFlow<List<ServiceEntity>> = repository.servicesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val accountTypes: StateFlow<List<AccountTypeEntity>> = repository.accountTypesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val accounts: StateFlow<List<ShopAccountEntity>> = repository.accountsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val transactionRules: StateFlow<List<TransactionTypeRuleEntity>> = repository.transactionRulesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val customers: StateFlow<List<CustomerEntity>> = repository.customersFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val transactions: StateFlow<List<ShopTransactionEntity>> = repository.transactionsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val dueLedgerEntries: StateFlow<List<DueLedgerEntryEntity>> = repository.dueLedgerFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val auditLogs: StateFlow<List<AuditLogEntity>> = repository.auditLogsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val settings: StateFlow<ShopSettingsEntity> = repository.settingsFlow
        .map { it ?: ShopSettingsEntity() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ShopSettingsEntity())

    // Navigation & Active Selection State
    private val _currentTab = MutableStateFlow(MainTab.HOME)
    val currentTab: StateFlow<MainTab> = _currentTab.asStateFlow()

    private val _moreSubScreen = MutableStateFlow(MoreSubScreen.MENU)
    val moreSubScreen: StateFlow<MoreSubScreen> = _moreSubScreen.asStateFlow()

    private val _preselectedAccountId = MutableStateFlow<String?>(null)
    val preselectedAccountId: StateFlow<String?> = _preselectedAccountId.asStateFlow()

    private val _preselectedRuleId = MutableStateFlow<String?>(null)
    val preselectedRuleId: StateFlow<String?> = _preselectedRuleId.asStateFlow()

    private val _statementAccountId = MutableStateFlow<String?>(null)
    val statementAccountId: StateFlow<String?> = _statementAccountId.asStateFlow()

    private val _selectedCustomerIdForLedger = MutableStateFlow<String?>(null)
    val selectedCustomerIdForLedger: StateFlow<String?> = _selectedCustomerIdForLedger.asStateFlow()

    // Global Search Query
    private val _globalSearchQuery = MutableStateFlow("")
    val globalSearchQuery: StateFlow<String> = _globalSearchQuery.asStateFlow()

    private val _isGlobalSearchOpen = MutableStateFlow(false)
    val isGlobalSearchOpen: StateFlow<Boolean> = _isGlobalSearchOpen.asStateFlow()

    // PIN Lock State
    private val _isLocked = MutableStateFlow(false)
    val isLocked: StateFlow<Boolean> = _isLocked.asStateFlow()

    // Banner / Snackbar Feedback
    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    init {
        viewModelScope.launch {
            repository.ensureSeeded()
            val s = database.dao().getSettingsOnce()
            if (s != null && s.isPinEnabled && s.pinCode.isNotBlank()) {
                _isLocked.value = true
            }
        }
    }

    fun clearSnackbar() {
        _snackbarMessage.value = null
    }

    fun showMessage(msg: String) {
        _snackbarMessage.value = msg
    }

    fun selectTab(tab: MainTab) {
        _currentTab.value = tab
        if (tab != MainTab.MORE) {
            _moreSubScreen.value = MoreSubScreen.MENU
        }
    }

    fun openNewTransactionWith(accountId: String? = null, ruleId: String? = null) {
        _preselectedAccountId.value = accountId
        _preselectedRuleId.value = ruleId
        _currentTab.value = MainTab.TRANSACTION
    }

    fun openAccountStatement(accountId: String) {
        _statementAccountId.value = accountId
        _currentTab.value = MainTab.MORE
        _moreSubScreen.value = MoreSubScreen.ACCOUNT_STATEMENT
    }

    fun openCustomerLedger(customerId: String) {
        _selectedCustomerIdForLedger.value = customerId
        _currentTab.value = MainTab.MORE
        _moreSubScreen.value = MoreSubScreen.CUSTOMER_LEDGER
    }

    fun openMoreSubScreen(subScreen: MoreSubScreen) {
        _currentTab.value = MainTab.MORE
        _moreSubScreen.value = subScreen
    }

    fun navigateBackInMore(): Boolean {
        return if (_moreSubScreen.value != MoreSubScreen.MENU) {
            _moreSubScreen.value = MoreSubScreen.MENU
            true
        } else if (_currentTab.value != MainTab.HOME) {
            _currentTab.value = MainTab.HOME
            true
        } else {
            false
        }
    }

    fun setGlobalSearchOpen(open: Boolean) {
        _isGlobalSearchOpen.value = open
        if (!open) _globalSearchQuery.value = ""
    }

    fun updateGlobalSearchQuery(query: String) {
        _globalSearchQuery.value = query
    }

    fun unlockWithPin(enteredPin: String): Boolean {
        val currentPin = settings.value.pinCode
        return if (enteredPin == currentPin || currentPin.isBlank()) {
            _isLocked.value = false
            true
        } else {
            false
        }
    }

    fun lockNow() {
        if (settings.value.isPinEnabled && settings.value.pinCode.isNotBlank()) {
            _isLocked.value = true
        } else {
            showMessage("প্রথমে সিকিউরিটি সেটিংস থেকে ৪-সংখ্যার PIN সেট করুন")
            openMoreSubScreen(MoreSubScreen.BACKUP_AND_SECURITY)
        }
    }

    // Transaction Entry Actions
    fun createTransaction(
        accountId: String,
        ruleId: String,
        amount: Double,
        paidAmount: Double,
        paymentMethodAccountId: String?,
        customerId: String?,
        note: String,
        secondaryTransferAccountId: String? = null,
        onSuccess: (ShopTransactionEntity) -> Unit = {}
    ) {
        viewModelScope.launch {
            val created = repository.recordServiceTransaction(
                accountId = accountId,
                ruleId = ruleId,
                amount = amount,
                paidAmount = paidAmount,
                paymentMethodAccountId = paymentMethodAccountId,
                customerId = customerId,
                note = note,
                secondaryTransferAccountId = secondaryTransferAccountId
            )
            if (created != null) {
                showMessage("লেনদেন সংরক্ষিত হয়েছে (${created.id})")
                onSuccess(created)
                triggerAutoCloudSync()
            } else {
                showMessage("লেনদেন সংরক্ষণে ত্রুটি হয়েছে")
            }
        }
    }

    fun editTransaction(
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
        onComplete: () -> Unit = {}
    ) {
        viewModelScope.launch {
            val ok = repository.editExistingTransaction(
                transactionId = transactionId,
                newAccountId = newAccountId,
                newRuleId = newRuleId,
                newCustomerId = newCustomerId,
                newAmount = newAmount,
                newPaidAmount = newPaidAmount,
                newPaymentMethodAccountId = newPaymentMethodAccountId,
                newSecondaryTransferAccountId = newSecondaryTransferAccountId,
                newNote = newNote,
                editReason = editReason
            )
            if (ok) {
                showMessage("লেনদেন সংশোধিত হয়েছে এবং সকল ব্যালেন্স পুনঃগণনা করা হয়েছে")
                onComplete()
                triggerAutoCloudSync()
            }
        }
    }

    fun reverseTransaction(transactionId: String, reason: String, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            val ok = repository.reverseTransaction(transactionId, reason)
            if (ok) {
                showMessage("লেনদেন রিভার্স (বাতিল) করা হয়েছে ও পূর্বের ব্যালেন্স পুনরুদ্ধার হয়েছে")
                onComplete()
                triggerAutoCloudSync()
            }
        }
    }

    fun deleteTransactionPermanently(transactionId: String, reason: String, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            val ok = repository.deleteTransactionWithAudit(transactionId, reason)
            if (ok) {
                showMessage("লেনদেন স্থায়ীভাবে মুছে ফেলা হয়েছে ও অডিট ইতিহাসে রেকর্ড করা হয়েছে")
                onComplete()
                triggerAutoCloudSync()
            }
        }
    }

    // Due Actions
    fun collectDue(
        customerId: String,
        amount: Double,
        receivingAccountId: String,
        note: String,
        onComplete: () -> Unit = {}
    ) {
        viewModelScope.launch {
            val ok = repository.collectCustomerDue(customerId, amount, receivingAccountId, note)
            if (ok) {
                showMessage("বকেয়া সফলভাবে আদায় হয়েছে ও ব্যালেন্স আপডেট হয়েছে")
                onComplete()
                triggerAutoCloudSync()
            }
        }
    }

    fun manualAddDue(customerId: String, amount: Double, reason: String, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            val ok = repository.manualAddCustomerDue(customerId, amount, reason)
            if (ok) {
                showMessage("কাস্টমারের নামে ম্যানুয়াল বাকি যোগ করা হয়েছে")
                onComplete()
                triggerAutoCloudSync()
            }
        }
    }

    fun manualDeductDue(
        customerId: String,
        amount: Double,
        isActualPayment: Boolean,
        receivingAccountId: String?,
        reason: String,
        onComplete: () -> Unit = {}
    ) {
        viewModelScope.launch {
            val ok = repository.manualDeductCustomerDue(
                customerId = customerId,
                amount = amount,
                isActualPayment = isActualPayment,
                receivingAccountId = receivingAccountId,
                reason = reason
            )
            if (ok) {
                showMessage("কাস্টমারের বাকি কমানো হয়েছে")
                onComplete()
                triggerAutoCloudSync()
            }
        }
    }

    // Reconcile Balance
    fun reconcileAccount(accountId: String, actualBalance: Double, reason: String, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            val ok = repository.reconcileAccountBalance(accountId, actualBalance, reason)
            if (ok) {
                showMessage("ব্যালেন্স মিলকরণ সমন্বয় রেকর্ড করা হয়েছে")
                onComplete()
                triggerAutoCloudSync()
            } else {
                showMessage("হিসাব অনুযায়ী ব্যালেন্স ও প্রকৃত ব্যালেন্স একই আছে")
            }
        }
    }

    // Account, Customer, Service, Rule Management
    fun saveAccount(
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
        viewModelScope.launch {
            repository.saveAccount(
                existingId = existingId,
                serviceId = serviceId,
                accountType = accountType,
                phoneNumber = phoneNumber,
                nickname = nickname,
                openingBalance = openingBalance,
                openingBalanceDate = openingBalanceDate,
                isActive = isActive,
                isVisibleOnDashboard = isVisibleOnDashboard,
                notes = notes
            )
            showMessage("অ্যাকাউন্ট সংরক্ষিত হয়েছে")
            triggerAutoCloudSync()
        }
    }

    fun toggleAccountDashboardVisibility(account: ShopAccountEntity) {
        viewModelScope.launch {
            database.dao().upsertAccount(account.copy(isVisibleOnDashboard = !account.isVisibleOnDashboard))
            triggerAutoCloudSync()
        }
    }

    fun saveCustomer(
        existingId: String?,
        name: String,
        phone: String,
        address: String,
        notes: String,
        customField: String,
        openingDue: Double,
        onSaved: (CustomerEntity) -> Unit = {}
    ) {
        viewModelScope.launch {
            val saved = repository.saveCustomer(
                existingId = existingId,
                name = name,
                phone = phone,
                address = address,
                notes = notes,
                customField = customField,
                openingDue = openingDue
            )
            showMessage("কাস্টমার সংরক্ষিত হয়েছে: ${saved.name}")
            onSaved(saved)
            triggerAutoCloudSync()
        }
    }

    fun saveService(
        existingId: String?,
        name: String,
        banglaName: String,
        category: String,
        colorHex: String,
        isActive: Boolean
    ) {
        viewModelScope.launch {
            repository.saveService(existingId, name, banglaName, category, colorHex, isActive)
            showMessage("সার্ভিস সংরক্ষিত হয়েছে")
            triggerAutoCloudSync()
        }
    }

    fun deleteService(serviceId: String) {
        viewModelScope.launch {
            repository.deleteServiceById(serviceId)
            showMessage("সার্ভিস মুছে ফেলা হয়েছে")
        }
    }

    fun saveAccountType(existingId: String?, name: String, banglaName: String) {
        viewModelScope.launch {
            repository.saveAccountType(existingId, name, banglaName)
            showMessage("অ্যাকাউন্ট টাইপ সংরক্ষিত হয়েছে")
        }
    }

    fun saveTransactionRule(
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
        viewModelScope.launch {
            repository.saveTransactionRule(
                existingId = existingId,
                name = name,
                banglaName = banglaName,
                allowedServiceCategories = allowedServiceCategories,
                accountDirection = accountDirection,
                cashDirection = cashDirection,
                customerRequired = customerRequired,
                allowDue = allowDue,
                description = description
            )
            showMessage("লেনদেন নিয়ম (Transaction Rule) সংরক্ষিত হয়েছে")
        }
    }

    fun updateShopSettings(transform: (ShopSettingsEntity) -> ShopSettingsEntity) {
        viewModelScope.launch {
            repository.updateSettings(transform)
        }
    }

    fun syncCloudNow() {
        viewModelScope.launch {
            val res = repository.syncWithFirebase()
            showMessage(res.message)
        }
    }

    fun pullFromCloudNow() {
        viewModelScope.launch {
            val res = repository.pullFromFirebase()
            showMessage(res.message)
        }
    }

    private fun triggerAutoCloudSync() {
        viewModelScope.launch {
            try {
                repository.syncWithFirebase()
            } catch (_: Exception) {
            }
        }
    }

    fun signInCloud(email: String, pass: String, isRegister: Boolean) {
        viewModelScope.launch {
            val res = syncManager.signInOrRegister(email, pass, isRegister)
            showMessage(res.message)
            if (res.success) {
                repository.syncWithFirebase()
            }
        }
    }

    fun signOutCloud() {
        viewModelScope.launch {
            syncManager.signOut()
            repository.updateSettings { it.copy(cloudUserEmail = null) }
            showMessage("ক্লাউড অ্যাকাউন্ট থেকে লগআউট করা হয়েছে")
        }
    }

    /**
     * Generates a comprehensive financial movement report (Strictly NO profit/commission!)
     * for Daily, Monthly, or Custom date ranges (#30).
     */
    fun buildFinancialReport(
        periodLabel: String,
        startTimestamp: Long,
        endTimestamp: Long,
        allAccounts: List<ShopAccountEntity>,
        allTransactions: List<ShopTransactionEntity>,
        allCustomers: List<CustomerEntity>
    ): PeriodFinancialReport {
        val activeTxs = allTransactions.filter { !it.isReversed }.sortedBy { it.timestamp }
        val cashAccount = allAccounts.find { it.id == ShopKhataRepository.CASH_ACCOUNT_ID }

        fun calculateSummaryForAccount(acc: ShopAccountEntity): AccountFlowSummary {
            var balanceBeforePeriod = acc.openingBalance
            var incomingInPeriod = 0.0
            var outgoingInPeriod = 0.0
            var adjustmentsInPeriod = 0.0
            var countInPeriod = 0

            for (tx in activeTxs) {
                val deltaForAcc = when {
                    tx.accountId == acc.id -> tx.primaryAccountDelta
                    tx.secondaryAccountId == acc.id -> tx.secondaryAccountDelta
                    else -> 0.0
                }
                if (abs(deltaForAcc) < 0.0001 && tx.accountId != acc.id && tx.secondaryAccountId != acc.id) continue

                if (tx.timestamp < startTimestamp) {
                    balanceBeforePeriod += deltaForAcc
                } else if (tx.timestamp in startTimestamp..endTimestamp) {
                    countInPeriod++
                    if (tx.transactionTypeId == "sys_reconcile") {
                        adjustmentsInPeriod += deltaForAcc
                    } else if (deltaForAcc > 0) {
                        incomingInPeriod += deltaForAcc
                    } else if (deltaForAcc < 0) {
                        outgoingInPeriod += abs(deltaForAcc)
                    }
                }
            }

            val closing = balanceBeforePeriod + incomingInPeriod - outgoingInPeriod + adjustmentsInPeriod
            return AccountFlowSummary(
                account = acc,
                openingInPeriod = balanceBeforePeriod,
                incoming = incomingInPeriod,
                outgoing = outgoingInPeriod,
                adjustments = adjustmentsInPeriod,
                closingBalance = closing,
                txCount = countInPeriod
            )
        }

        val cashSummary = cashAccount?.let { calculateSummaryForAccount(it) }
        val nonCashSummaries = allAccounts
            .filter { it.id != ShopKhataRepository.CASH_ACCOUNT_ID }
            .map { calculateSummaryForAccount(it) }

        val periodTxs = activeTxs.filter { it.timestamp in startTimestamp..endTimestamp }
        val totalVolume = periodTxs.sumOf { it.amount }
        val dueCreated = periodTxs.filter { it.customerDueDelta > 0 }.sumOf { it.customerDueDelta }
        val dueCollected = periodTxs.filter { it.customerDueDelta < 0 }.sumOf { abs(it.customerDueDelta) }
        val totalCurrentDue = allCustomers.sumOf { it.currentDue }

        return PeriodFinancialReport(
            periodLabel = periodLabel,
            startTimestamp = startTimestamp,
            endTimestamp = endTimestamp,
            openingCash = cashSummary?.openingInPeriod ?: 0.0,
            cashReceived = cashSummary?.incoming ?: 0.0,
            cashPaid = cashSummary?.outgoing ?: 0.0,
            cashAdjustments = cashSummary?.adjustments ?: 0.0,
            closingCash = cashSummary?.closingBalance ?: 0.0,
            accountSummaries = nonCashSummaries,
            totalTransactionVolume = totalVolume,
            totalTransactionCount = periodTxs.size,
            dueCreatedInPeriod = dueCreated,
            dueCollectedInPeriod = dueCollected,
            currentTotalCustomerDue = totalCurrentDue
        )
    }

    fun buildCsvExport(type: String): String {
        val sb = StringBuilder()
        when (type) {
            "TRANSACTIONS" -> {
                sb.appendLine("Transaction ID,Date Time,Account,Phone,Type,Customer,Amount,Paid,Due,Primary Impact,Secondary Impact,Status,Note")
                transactions.value.forEach { tx ->
                    val status = if (tx.isReversed) "REVERSED" else tx.paymentStatus
                    sb.appendLine("\"${tx.id}\",\"${formatDateTime(tx.timestamp)}\",\"${tx.accountName}\",\"${tx.accountPhone}\",\"${tx.transactionTypeName}\",\"${tx.customerName ?: ""}\",${tx.amount},${tx.paidAmount},${tx.dueAmount},${tx.primaryAccountDelta},${tx.secondaryAccountDelta},\"$status\",\"${tx.note.replace("\"", "'")}\"")
                }
            }
            "CUSTOMERS_DUE" -> {
                sb.appendLine("Customer ID,Name,Phone,Address,Opening Due,Current Due,Notes")
                customers.value.forEach { c ->
                    sb.appendLine("\"${c.id}\",\"${c.name}\",\"${c.phone}\",\"${c.address}\",${c.openingDue},${c.currentDue},\"${c.notes.replace("\"", "'")}\"")
                }
            }
            "ACCOUNTS" -> {
                sb.appendLine("Account ID,Nickname,Service,Type,Phone Number,Opening Balance,Current Balance,Status")
                accounts.value.forEach { a ->
                    sb.appendLine("\"${a.id}\",\"${a.nickname}\",\"${a.serviceName}\",\"${a.accountType}\",\"${a.phoneNumber}\",${a.openingBalance},${a.currentBalance},\"${if (a.isActive) "Active" else "Inactive"}\"")
                }
            }
        }
        return sb.toString()
    }
}

fun getStartOfTodayMillis(): Long {
    val cal = Calendar.getInstance()
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    return cal.timeInMillis
}

fun getStartOfMonthMillis(): Long {
    val cal = Calendar.getInstance()
    cal.set(Calendar.DAY_OF_MONTH, 1)
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    return cal.timeInMillis
}
