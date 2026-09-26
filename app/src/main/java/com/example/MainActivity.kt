package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.*
import com.example.ui.*
import com.example.ui.components.GlobalSearchDialog
import com.example.ui.components.TransactionDetailAndEditDialog
import com.example.ui.screens.*
import com.example.ui.theme.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                HisabKhataApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HisabKhataApp(
    viewModel: ShopKhataViewModel = viewModel()
) {
    val currentTab by viewModel.currentTab.collectAsStateWithLifecycle()
    val moreSubScreen by viewModel.moreSubScreen.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    val services by viewModel.services.collectAsStateWithLifecycle()
    val accountTypes by viewModel.accountTypes.collectAsStateWithLifecycle()
    val rules by viewModel.transactionRules.collectAsStateWithLifecycle()
    val customers by viewModel.customers.collectAsStateWithLifecycle()
    val transactions by viewModel.transactions.collectAsStateWithLifecycle()
    val dueEntries by viewModel.dueLedgerEntries.collectAsStateWithLifecycle()
    val auditLogs by viewModel.auditLogs.collectAsStateWithLifecycle()

    val preselectedAccountId by viewModel.preselectedAccountId.collectAsStateWithLifecycle()
    val preselectedRuleId by viewModel.preselectedRuleId.collectAsStateWithLifecycle()
    val statementAccountId by viewModel.statementAccountId.collectAsStateWithLifecycle()
    val selectedCustomerIdForLedger by viewModel.selectedCustomerIdForLedger.collectAsStateWithLifecycle()

    val isGlobalSearchOpen by viewModel.isGlobalSearchOpen.collectAsStateWithLifecycle()
    val globalSearchQuery by viewModel.globalSearchQuery.collectAsStateWithLifecycle()
    val isLocked by viewModel.isLocked.collectAsStateWithLifecycle()
    val snackbarMessage by viewModel.snackbarMessage.collectAsStateWithLifecycle()

    var globalSelectedTransaction by remember { mutableStateOf<ShopTransactionEntity?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(snackbarMessage) {
        if (!snackbarMessage.isNullOrBlank()) {
            snackbarHostState.showSnackbar(snackbarMessage!!)
            viewModel.clearSnackbar()
        }
    }

    // BackHandler for non-Home tabs and More sub-screens
    BackHandler(enabled = currentTab != MainTab.HOME || moreSubScreen != MoreSubScreen.MENU) {
        viewModel.navigateBackInMore()
    }

    if (isLocked) {
        PinLockScreen(
            shopName = settings.shopName,
            onUnlockAttempt = { viewModel.unlockWithPin(it) }
        )
        return
    }

    if (isGlobalSearchOpen) {
        GlobalSearchDialog(
            query = globalSearchQuery,
            onQueryChange = { viewModel.updateGlobalSearchQuery(it) },
            accounts = accounts,
            customers = customers,
            transactions = transactions,
            onSelectAccount = { acc -> viewModel.openAccountStatement(acc.id) },
            onSelectCustomer = { cust -> viewModel.openCustomerLedger(cust.id) },
            onSelectTransaction = { tx ->
                viewModel.setGlobalSearchOpen(false)
                globalSelectedTransaction = tx
            },
            onDismiss = { viewModel.setGlobalSearchOpen(false) }
        )
    }

    if (globalSelectedTransaction != null) {
        val liveTx = transactions.find { it.id == globalSelectedTransaction!!.id } ?: globalSelectedTransaction!!
        TransactionDetailAndEditDialog(
            transaction = liveTx,
            accounts = accounts,
            rules = rules,
            customers = customers,
            auditLogs = auditLogs,
            onDismiss = { globalSelectedTransaction = null },
            onSaveEdit = { accId, ruleId, custId, amt, paid, payAccId, secAccId, note, reason ->
                viewModel.editTransaction(
                    liveTx.id, accId, ruleId, custId, amt, paid, payAccId, secAccId, note, reason
                ) { globalSelectedTransaction = null }
            },
            onReverse = { reason ->
                viewModel.reverseTransaction(liveTx.id, reason) { globalSelectedTransaction = null }
            },
            onDeletePermanently = { reason ->
                viewModel.deleteTransactionPermanently(liveTx.id, reason) { globalSelectedTransaction = null }
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = settings.shopName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "ডিজিটাল হিসাব খাতা ও MFS ব্যালেন্স ম্যানেজার",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.setGlobalSearchOpen(true) },
                        modifier = Modifier.testTag("top_bar_global_search_btn")
                    ) {
                        Icon(Icons.Default.Search, contentDescription = "Global Search")
                    }
                    IconButton(
                        onClick = { viewModel.syncCloudNow() },
                        modifier = Modifier.testTag("top_bar_sync_btn")
                    ) {
                        Icon(Icons.Default.CloudSync, contentDescription = "Cloud Sync")
                    }
                    IconButton(
                        onClick = { viewModel.lockNow() },
                        modifier = Modifier.testTag("top_bar_lock_btn")
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = "Lock App")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            NavigationBar(
                modifier = Modifier.testTag("main_bottom_nav")
            ) {
                NavigationBarItem(
                    selected = currentTab == MainTab.HOME,
                    onClick = { viewModel.selectTab(MainTab.HOME) },
                    icon = { Icon(Icons.Default.Dashboard, contentDescription = "Home") },
                    label = { Text("হোম (Home)") },
                    modifier = Modifier.testTag("nav_tab_home")
                )
                NavigationBarItem(
                    selected = currentTab == MainTab.TRANSACTION,
                    onClick = { viewModel.selectTab(MainTab.TRANSACTION) },
                    icon = { Icon(Icons.Default.AddCircle, contentDescription = "Transaction") },
                    label = { Text("লেনদেন (Entry)") },
                    modifier = Modifier.testTag("nav_tab_transaction")
                )
                NavigationBarItem(
                    selected = currentTab == MainTab.DUE,
                    onClick = { viewModel.selectTab(MainTab.DUE) },
                    icon = { Icon(Icons.Default.AccountBalanceWallet, contentDescription = "Due") },
                    label = { Text("বাকি (Due)") },
                    modifier = Modifier.testTag("nav_tab_due")
                )
                NavigationBarItem(
                    selected = currentTab == MainTab.HISTORY,
                    onClick = { viewModel.selectTab(MainTab.HISTORY) },
                    icon = { Icon(Icons.Default.History, contentDescription = "History") },
                    label = { Text("ইতিহাস (History)") },
                    modifier = Modifier.testTag("nav_tab_history")
                )
                NavigationBarItem(
                    selected = currentTab == MainTab.MORE,
                    onClick = { viewModel.selectTab(MainTab.MORE) },
                    icon = { Icon(Icons.Default.Apps, contentDescription = "More") },
                    label = { Text("আরও (More)") },
                    modifier = Modifier.testTag("nav_tab_more")
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentTab) {
                MainTab.HOME -> {
                    DashboardScreen(
                        settings = settings,
                        accounts = accounts,
                        customers = customers,
                        transactions = transactions,
                        onQuickNewTransaction = { accId, ruleId ->
                            viewModel.openNewTransactionWith(accId, ruleId)
                        },
                        onOpenTab = { viewModel.selectTab(it) },
                        onOpenMoreSubScreen = { viewModel.openMoreSubScreen(it) },
                        onOpenAccountStatement = { viewModel.openAccountStatement(it) },
                        onReconcileAccount = { accId, actual, reason ->
                            viewModel.reconcileAccount(accId, actual, reason)
                        },
                        onToggleShowAllAccounts = { showAll ->
                            viewModel.updateShopSettings { it.copy(showAllAccountsOnDashboard = showAll) }
                        },
                        onToggleGroupingMode = { mode ->
                            viewModel.updateShopSettings { it.copy(groupAccountsBy = mode) }
                        }
                    )
                }

                MainTab.TRANSACTION -> {
                    NewTransactionScreen(
                        accounts = accounts,
                        rules = rules,
                        customers = customers,
                        preselectedAccountId = preselectedAccountId,
                        preselectedRuleId = preselectedRuleId,
                        computePreview = { prim, rule, amt, paid, sec, cust ->
                            viewModel.repository.computeBalancePreview(prim, rule, amt, paid, sec, cust)
                        },
                        onQuickCreateCustomer = { name, phone, onCreated ->
                            viewModel.saveCustomer(null, name, phone, "", "", "", 0.0, onCreated)
                        },
                        onCreateCustomRule = { name, bangla, accDir, cashDir, allowDue ->
                            viewModel.saveTransactionRule(
                                null, name, bangla, "ALL", accDir, cashDir, false, allowDue, "কাস্টম লেনদেন নিয়ম"
                            )
                        },
                        onConfirmTransaction = { accId, ruleId, amt, paid, payAccId, custId, note, secTransId ->
                            viewModel.createTransaction(
                                accountId = accId,
                                ruleId = ruleId,
                                amount = amt,
                                paidAmount = paid,
                                paymentMethodAccountId = payAccId,
                                customerId = custId,
                                note = note,
                                secondaryTransferAccountId = secTransId
                            ) {
                                viewModel.selectTab(MainTab.HOME)
                            }
                        },
                        onOpenManageAccounts = {
                            viewModel.openMoreSubScreen(MoreSubScreen.ACCOUNTS)
                        }
                    )
                }

                MainTab.DUE -> {
                    DueManagementScreen(
                        customers = customers,
                        accounts = accounts,
                        dueEntries = dueEntries,
                        onCollectDue = { custId, amt, recAccId, note ->
                            viewModel.collectDue(custId, amt, recAccId, note)
                        },
                        onManualAddDue = { custId, amt, reason ->
                            viewModel.manualAddDue(custId, amt, reason)
                        },
                        onManualDeductDue = { custId, amt, isPay, recAccId, reason ->
                            viewModel.manualDeductDue(custId, amt, isPay, recAccId, reason)
                        },
                        onAddOrEditCustomer = { existing, name, phone, address, notes, customField, openingDue ->
                            viewModel.saveCustomer(existing?.id, name, phone, address, notes, customField, openingDue)
                        }
                    )
                }

                MainTab.HISTORY -> {
                    TransactionHistoryScreen(
                        transactions = transactions,
                        accounts = accounts,
                        rules = rules,
                        customers = customers,
                        auditLogs = auditLogs,
                        onEditTransaction = { txId, accId, ruleId, custId, amt, paid, payAccId, secAccId, note, reason ->
                            viewModel.editTransaction(txId, accId, ruleId, custId, amt, paid, payAccId, secAccId, note, reason)
                        },
                        onReverseTransaction = { txId, reason ->
                            viewModel.reverseTransaction(txId, reason)
                        },
                        onDeleteTransaction = { txId, reason ->
                            viewModel.deleteTransactionPermanently(txId, reason)
                        }
                    )
                }

                MainTab.MORE -> {
                    when (moreSubScreen) {
                        MoreSubScreen.MENU -> {
                            MoreMenuScreen(
                                settings = settings,
                                accountsCount = accounts.size,
                                customersCount = customers.size,
                                servicesCount = services.size,
                                auditCount = auditLogs.size,
                                onOpenSubScreen = { viewModel.openMoreSubScreen(it) },
                                onSyncCloudNow = { viewModel.syncCloudNow() },
                                onLockAppNow = { viewModel.lockNow() }
                            )
                        }

                        MoreSubScreen.ACCOUNTS -> {
                            AccountsManagementScreen(
                                accounts = accounts,
                                services = services,
                                accountTypes = accountTypes,
                                onBack = { viewModel.openMoreSubScreen(MoreSubScreen.MENU) },
                                onSaveAccount = { id, srvId, accType, phone, nick, openBal, openDate, isAct, isVis, notes ->
                                    viewModel.saveAccount(id, srvId, accType, phone, nick, openBal, openDate, isAct, isVis, notes)
                                },
                                onToggleDashboardVisibility = { viewModel.toggleAccountDashboardVisibility(it) },
                                onOpenStatement = { viewModel.openAccountStatement(it) },
                                onReconcile = { accId, actual, reason ->
                                    viewModel.reconcileAccount(accId, actual, reason)
                                }
                            )
                        }

                        MoreSubScreen.CUSTOMERS -> {
                            CustomersDirectoryScreen(
                                customers = customers,
                                onBack = { viewModel.openMoreSubScreen(MoreSubScreen.MENU) },
                                onSaveCustomer = { existing, name, phone, address, notes, customField, openingDue ->
                                    viewModel.saveCustomer(existing?.id, name, phone, address, notes, customField, openingDue)
                                },
                                onOpenCustomerLedger = { viewModel.openCustomerLedger(it.id) }
                            )
                        }

                        MoreSubScreen.SERVICES_AND_RULES -> {
                            ServicesAndRulesScreen(
                                services = services,
                                accountTypes = accountTypes,
                                rules = rules,
                                onBack = { viewModel.openMoreSubScreen(MoreSubScreen.MENU) },
                                onSaveService = { id, name, bangla, cat, color, active ->
                                    viewModel.saveService(id, name, bangla, cat, color, active)
                                },
                                onSaveAccountType = { id, name, bangla ->
                                    viewModel.saveAccountType(id, name, bangla)
                                },
                                onSaveRule = { id, name, bangla, cat, accDir, cashDir, custReq, allowDue, desc ->
                                    viewModel.saveTransactionRule(id, name, bangla, cat, accDir, cashDir, custReq, allowDue, desc)
                                }
                            )
                        }

                        MoreSubScreen.REPORTS -> {
                            ReportsScreen(
                                accounts = accounts,
                                transactions = transactions,
                                customers = customers,
                                buildReport = { label, start, end ->
                                    viewModel.buildFinancialReport(label, start, end, accounts, transactions, customers)
                                },
                                onBack = { viewModel.openMoreSubScreen(MoreSubScreen.MENU) }
                            )
                        }

                        MoreSubScreen.AUDIT_HISTORY -> {
                            AuditHistoryScreen(
                                auditLogs = auditLogs,
                                onBack = { viewModel.openMoreSubScreen(MoreSubScreen.MENU) }
                            )
                        }

                        MoreSubScreen.BACKUP_AND_SECURITY -> {
                            BackupAndSecurityScreen(
                                settings = settings,
                                repository = viewModel.repository,
                                onBack = { viewModel.openMoreSubScreen(MoreSubScreen.MENU) },
                                onUpdateSettings = { viewModel.updateShopSettings(it) },
                                onSignInCloud = { email, pass, reg -> viewModel.signInCloud(email, pass, reg) },
                                onSignOutCloud = { viewModel.signOutCloud() },
                                onSyncCloudNow = { viewModel.syncCloudNow() },
                                onPullCloudNow = { viewModel.pullFromCloudNow() },
                                onBuildCsv = { viewModel.buildCsvExport(it) },
                                onShowMessage = { viewModel.showMessage(it) }
                            )
                        }

                        MoreSubScreen.ACCOUNT_STATEMENT -> {
                            val acc = accounts.find { it.id == statementAccountId } ?: accounts.firstOrNull()
                            if (acc != null) {
                                AccountStatementScreen(
                                    account = acc,
                                    allAccounts = accounts,
                                    transactions = transactions,
                                    rules = rules,
                                    customers = customers,
                                    auditLogs = auditLogs,
                                    onBack = { viewModel.openMoreSubScreen(MoreSubScreen.MENU) },
                                    onNewTransactionForAccount = { viewModel.openNewTransactionWith(it, null) },
                                    onReconcileAccount = { actual, reason ->
                                        viewModel.reconcileAccount(acc.id, actual, reason)
                                    },
                                    onEditTransaction = { txId, accId, ruleId, custId, amt, paid, payAccId, secAccId, note, reason ->
                                        viewModel.editTransaction(txId, accId, ruleId, custId, amt, paid, payAccId, secAccId, note, reason)
                                    },
                                    onReverseTransaction = { txId, reason ->
                                        viewModel.reverseTransaction(txId, reason)
                                    },
                                    onDeleteTransaction = { txId, reason ->
                                        viewModel.deleteTransactionPermanently(txId, reason)
                                    }
                                )
                            }
                        }

                        MoreSubScreen.CUSTOMER_LEDGER -> {
                            val cust = customers.find { it.id == selectedCustomerIdForLedger } ?: customers.firstOrNull()
                            if (cust != null) {
                                CustomerDueLedgerDetailView(
                                    customer = cust,
                                    entries = dueEntries.filter { it.customerId == cust.id },
                                    onBack = { viewModel.openMoreSubScreen(MoreSubScreen.CUSTOMERS) },
                                    onCollectClick = { viewModel.selectTab(MainTab.DUE) },
                                    onManualAddClick = { viewModel.selectTab(MainTab.DUE) },
                                    onManualDeductClick = { viewModel.selectTab(MainTab.DUE) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PinLockScreen(
    shopName: String,
    onUnlockAttempt: (String) -> Boolean
) {
    var pinInput by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(EmeraldDark),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth(0.88f)
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Icon(
                    Icons.Default.Lock,
                    contentDescription = null,
                    tint = EmeraldPrimary,
                    modifier = Modifier.size(48.dp)
                )
                Text(
                    text = shopName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "হিসাব খাতা আনলক করতে আপনার সিকিউরিটি PIN দিন",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = pinInput,
                    onValueChange = {
                        pinInput = it
                        errorText = null
                    },
                    label = { Text("PIN কোড") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("pin_unlock_input")
                )

                if (errorText != null) {
                    Text(errorText!!, color = DueCrimson, style = MaterialTheme.typography.labelMedium)
                }

                Button(
                    onClick = {
                        val ok = onUnlockAttempt(pinInput)
                        if (!ok) {
                            errorText = "ভুল PIN দিয়েছেন, আবার চেষ্টা করুন"
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("pin_unlock_button")
                ) {
                    Text("আনলক করুন (Unlock)")
                }
            }
        }
    }
}
