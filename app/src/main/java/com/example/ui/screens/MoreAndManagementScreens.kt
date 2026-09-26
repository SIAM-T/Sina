package com.example.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.data.*
import com.example.ui.*
import com.example.ui.components.ReconcileBalanceDialog
import com.example.ui.components.ServiceBadge
import com.example.ui.components.parseHexColor
import com.example.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun MoreMenuScreen(
    settings: ShopSettingsEntity,
    accountsCount: Int,
    customersCount: Int,
    servicesCount: Int,
    auditCount: Int,
    onOpenSubScreen: (MoreSubScreen) -> Unit,
    onSyncCloudNow: () -> Unit,
    onLockAppNow: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 96.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = EmeraldDark),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(18.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = settings.shopName,
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (settings.ownerPhone.isNotBlank()) "মালিকের নাম্বার: ${settings.ownerPhone}" else "ডিজিটাল হিসাব খাতা ও MFS ব্যালেন্স ম্যানেজার",
                            style = MaterialTheme.typography.bodySmall,
                            color = EmeraldLight
                        )
                        Text(
                            text = if (settings.lastCloudSyncTimestamp != null)
                                "সর্বশেষ ক্লাউড সিঙ্ক: ${formatDateTime(settings.lastCloudSyncTimestamp)}"
                            else "অফলাইন লোকাল ডাটাবেস সক্রিয় (রিয়েল-টাইম সেভ)",
                            style = MaterialTheme.typography.labelSmall,
                            color = GoldLight
                        )
                    }
                    FilledTonalButton(
                        onClick = onSyncCloudNow,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = Color.White.copy(alpha = 0.16f),
                            contentColor = Color.White
                        )
                    ) {
                        Icon(Icons.Default.CloudSync, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("সিঙ্ক")
                    }
                }
            }
        }

        item {
            MoreMenuNavigationCard(
                icon = Icons.Default.PhoneAndroid,
                title = "অ্যাকাউন্ট ও ওপেনিং ব্যালেন্স (Accounts)",
                subtitle = "$accountsCount টি অ্যাকাউন্ট • একই নাম্বারে একাধিক MFS/রিচার্জ পরিচালনা",
                tint = BkashPink,
                onClick = { onOpenSubScreen(MoreSubScreen.ACCOUNTS) },
                testTag = "more_nav_accounts"
            )
        }

        item {
            MoreMenuNavigationCard(
                icon = Icons.Default.People,
                title = "কাস্টমার তালিকা ও খাতা (Customers)",
                subtitle = "$customersCount জন কাস্টমার • নাম, ফোন, ঠিকানা ও প্রারম্ভিক বাকি এডিট",
                tint = TransferBlue,
                onClick = { onOpenSubScreen(MoreSubScreen.CUSTOMERS) },
                testTag = "more_nav_customers"
            )
        }

        item {
            MoreMenuNavigationCard(
                icon = Icons.Default.Tune,
                title = "সার্ভিস, অ্যাকাউন্ট টাইপ ও লেনদেন নিয়ম (Rules)",
                subtitle = "$servicesCount টি সার্ভিস • কাস্টম অ্যাকাউন্ট টাইপ ও কনফিগারেবল লেনদেন নিয়ম",
                tint = NagadOrange,
                onClick = { onOpenSubScreen(MoreSubScreen.SERVICES_AND_RULES) },
                testTag = "more_nav_services_rules"
            )
        }

        item {
            MoreMenuNavigationCard(
                icon = Icons.Default.Assessment,
                title = "দৈনিক ও মাসিক রিপোর্ট (Daily & Monthly Reports)",
                subtitle = "ওপেনিং ও ক্লোজিং ক্যাশ, অ্যাকাউন্ট ইনকামিং/আউটগোয়িং ও বকেয়া সামারি",
                tint = EmeraldPrimary,
                onClick = { onOpenSubScreen(MoreSubScreen.REPORTS) },
                testTag = "more_nav_reports"
            )
        }

        item {
            MoreMenuNavigationCard(
                icon = Icons.Default.HistoryEdu,
                title = "অডিট ও এডিট ইতিহাস (Audit & Edit History)",
                subtitle = "$auditCount টি রেকর্ড • লেনদেন এডিট, রিভার্স ও ব্যালেন্স মিলকরণ লগ",
                tint = GoldAccent,
                onClick = { onOpenSubScreen(MoreSubScreen.AUDIT_HISTORY) },
                testTag = "more_nav_audit"
            )
        }

        item {
            MoreMenuNavigationCard(
                icon = Icons.Default.Security,
                title = "ব্যাকআপ, এক্সপোর্ট, Firebase ও PIN সিকিউরিটি",
                subtitle = "ডাটা ব্যাকআপ/রিস্টোর, CSV এক্সপোর্ট, Firebase Auth এবং অ্যাপ PIN লক",
                tint = RocketPurple,
                onClick = { onOpenSubScreen(MoreSubScreen.BACKUP_AND_SECURITY) },
                testTag = "more_nav_backup_security"
            )
        }

        item {
            OutlinedButton(
                onClick = onLockAppNow,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("lock_app_now_button")
            ) {
                Icon(Icons.Default.Lock, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("অ্যাপ লক করুন (Lock Session Now)")
            }
        }
    }
}

@Composable
private fun MoreMenuNavigationCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    tint: Color,
    onClick: () -> Unit,
    testTag: String
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(tint.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = title, tint = tint, modifier = Modifier.size(24.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun AccountsManagementScreen(
    accounts: List<ShopAccountEntity>,
    services: List<ServiceEntity>,
    accountTypes: List<AccountTypeEntity>,
    onBack: () -> Unit,
    onSaveAccount: (
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
    ) -> Unit,
    onToggleDashboardVisibility: (ShopAccountEntity) -> Unit,
    onOpenStatement: (String) -> Unit,
    onReconcile: (accountId: String, actual: Double, reason: String) -> Unit
) {
    var editingAccount by remember { mutableStateOf<ShopAccountEntity?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var reconcilingAccount by remember { mutableStateOf<ShopAccountEntity?>(null) }

    if (showCreateDialog || editingAccount != null) {
        AccountEditDialog(
            existing = editingAccount,
            services = services,
            accountTypes = accountTypes,
            onDismiss = {
                showCreateDialog = false
                editingAccount = null
            },
            onSave = { srvId, accType, phone, nick, openBal, isAct, isVis, notes ->
                onSaveAccount(
                    editingAccount?.id,
                    srvId,
                    accType,
                    phone,
                    nick,
                    openBal,
                    editingAccount?.openingBalanceDate ?: System.currentTimeMillis(),
                    isAct,
                    isVis,
                    notes
                )
                showCreateDialog = false
                editingAccount = null
            }
        )
    }

    if (reconcilingAccount != null) {
        ReconcileBalanceDialog(
            account = reconcilingAccount!!,
            onDismiss = { reconcilingAccount = null },
            onConfirmReconcile = { actual, reason ->
                onReconcile(reconcilingAccount!!.id, actual, reason)
                reconcilingAccount = null
            }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 96.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                    Column {
                        Text("সকল অ্যাকাউন্ট (Manage Accounts)", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("একই মোবাইল নাম্বারে একাধিক MFS ও রিচার্জ সিম যোগ করুন", style = MaterialTheme.typography.bodySmall)
                    }
                }
                Button(
                    onClick = { showCreateDialog = true },
                    modifier = Modifier.testTag("add_new_account_button")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("নতুন")
                }
            }
        }

        items(accounts, key = { it.id }) { acc ->
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(acc.nickname, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                ServiceBadge(acc.serviceName, acc.accountType, acc.colorHex)
                            }
                            Text("📱 ${acc.phoneNumber} • ওপেনিং: ${formatTaka(acc.openingBalance)}", style = MaterialTheme.typography.bodySmall)
                            if (acc.notes.isNotBlank()) {
                                Text(acc.notes, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                formatTaka(acc.currentBalance),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = parseHexColor(acc.colorHex)
                            )
                            Text(
                                if (acc.isActive) "Active" else "Inactive",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (acc.isActive) IncomingGreen else DueCrimson
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { editingAccount = acc },
                            modifier = Modifier.weight(1f).height(34.dp),
                            contentPadding = PaddingValues(4.dp)
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("এডিট", style = MaterialTheme.typography.labelMedium)
                        }
                        OutlinedButton(
                            onClick = { reconcilingAccount = acc },
                            modifier = Modifier.weight(1f).height(34.dp),
                            contentPadding = PaddingValues(4.dp)
                        ) {
                            Icon(Icons.Default.Balance, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("মিলকরণ", style = MaterialTheme.typography.labelMedium)
                        }
                        OutlinedButton(
                            onClick = { onOpenStatement(acc.id) },
                            modifier = Modifier.weight(1f).height(34.dp),
                            contentPadding = PaddingValues(4.dp)
                        ) {
                            Text("স্টেটমেন্ট", style = MaterialTheme.typography.labelMedium)
                        }
                        IconButton(
                            onClick = { onToggleDashboardVisibility(acc) },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = if (acc.isVisibleOnDashboard) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = "Toggle Dashboard Visibility",
                                tint = if (acc.isVisibleOnDashboard) EmeraldPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountEditDialog(
    existing: ShopAccountEntity?,
    services: List<ServiceEntity>,
    accountTypes: List<AccountTypeEntity>,
    onDismiss: () -> Unit,
    onSave: (
        serviceId: String,
        accountType: String,
        phoneNumber: String,
        nickname: String,
        openingBalance: Double,
        isActive: Boolean,
        isVisibleOnDashboard: Boolean,
        notes: String
    ) -> Unit
) {
    var selectedServiceId by remember { mutableStateOf(existing?.serviceId ?: services.firstOrNull()?.id ?: "srv_bkash") }
    var selectedType by remember { mutableStateOf(existing?.accountType ?: accountTypes.firstOrNull()?.name ?: "Agent") }
    var phone by remember { mutableStateOf(existing?.phoneNumber ?: "") }
    var nickname by remember { mutableStateOf(existing?.nickname ?: "") }
    var openingBalanceInput by remember { mutableStateOf(existing?.openingBalance?.toLong()?.toString() ?: "0") }
    var isActive by remember { mutableStateOf(existing?.isActive ?: true) }
    var isVisible by remember { mutableStateOf(existing?.isVisibleOnDashboard ?: true) }
    var notes by remember { mutableStateOf(existing?.notes ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (existing == null) "নতুন অ্যাকাউন্ট তৈরি করুন" else "অ্যাকাউন্ট এডিট করুন",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("১. সার্ভিস সিলেক্ট করুন (Service):", style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    services.filter { it.isActive }.forEach { srv ->
                        FilterChip(
                            selected = srv.id == selectedServiceId,
                            onClick = {
                                selectedServiceId = srv.id
                                if (nickname.isBlank()) nickname = "${srv.name} $selectedType"
                            },
                            label = { Text("${srv.name} (${srv.banglaName})") }
                        )
                    }
                }

                Text("২. অ্যাকাউন্ট টাইপ (Account Type):", style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    accountTypes.forEach { t ->
                        FilterChip(
                            selected = t.name == selectedType,
                            onClick = { selectedType = t.name },
                            label = { Text("${t.name} (${t.banglaName})") }
                        )
                    }
                }

                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("মোবাইল নাম্বার (Phone Number)") },
                    placeholder = { Text("যেমন: 017XXXXXXXX") },
                    supportingText = { Text("একই নাম্বারে একাধিক MFS/রিচার্জ অ্যাকাউন্ট খোলা যাবে") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("account_phone_input")
                )

                OutlinedTextField(
                    value = nickname,
                    onValueChange = { nickname = it },
                    label = { Text("অ্যাকাউন্টের নাম (Account Nickname)") },
                    placeholder = { Text("যেমন: bKash Agent / Nagad Personal") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("account_nickname_input")
                )

                OutlinedTextField(
                    value = openingBalanceInput,
                    onValueChange = { openingBalanceInput = it },
                    label = { Text("ওপেনিং ব্যালেন্স (Opening Balance ৳)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("account_opening_balance_input")
                )

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("নোট (Notes)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = isActive, onCheckedChange = { isActive = it })
                    Text("অ্যাকাউন্ট সচল (Active)")
                    Spacer(Modifier.width(12.dp))
                    Checkbox(checked = isVisible, onCheckedChange = { isVisible = it })
                    Text("ড্যাশবোর্ডে দেখান")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val srv = services.find { it.id == selectedServiceId }
                    val finalNick = nickname.ifBlank { "${srv?.name ?: "Account"} $selectedType" }
                    onSave(
                        selectedServiceId,
                        selectedType,
                        phone,
                        finalNick,
                        openingBalanceInput.toDoubleOrNull() ?: 0.0,
                        isActive,
                        isVisible,
                        notes
                    )
                },
                enabled = phone.isNotBlank(),
                modifier = Modifier.testTag("save_account_confirm_btn")
            ) {
                Text("সংরক্ষণ করুন")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("বাতিল") }
        }
    )
}

@Composable
fun CustomersDirectoryScreen(
    customers: List<CustomerEntity>,
    onBack: () -> Unit,
    onSaveCustomer: (existing: CustomerEntity?, name: String, phone: String, address: String, notes: String, customField: String, openingDue: Double) -> Unit,
    onOpenCustomerLedger: (CustomerEntity) -> Unit
) {
    var editingCustomer by remember { mutableStateOf<CustomerEntity?>(null) }
    var showCreate by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf("") }

    val filtered = remember(customers, search) {
        val q = search.trim().lowercase()
        if (q.isEmpty()) customers
        else customers.filter { it.name.lowercase().contains(q) || it.phone.lowercase().contains(q) || it.address.lowercase().contains(q) }
    }

    if (showCreate || editingCustomer != null) {
        CustomerFormDialog(
            existing = editingCustomer,
            onDismiss = {
                showCreate = false
                editingCustomer = null
            },
            onSave = { name, phone, address, notes, customField, openingDue ->
                onSaveCustomer(editingCustomer, name, phone, address, notes, customField, openingDue)
                showCreate = false
                editingCustomer = null
            }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 96.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                    Text("কাস্টমার তালিকা (Customers)", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
                Button(onClick = { showCreate = true }) {
                    Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("নতুন কাস্টমার")
                }
            }
        }

        item {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                placeholder = { Text("কাস্টমারের নাম, ফোন বা ঠিকানা দিয়ে খুঁজুন...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        items(filtered, key = { it.id }) { cust ->
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth().clickable { onOpenCustomerLedger(cust) }
            ) {
                Row(
                    modifier = Modifier.padding(14.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(cust.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("📱 ${cust.phone} ${if (cust.address.isNotBlank()) "• ${cust.address}" else ""}", style = MaterialTheme.typography.bodySmall)
                        if (cust.customField.isNotBlank() || cust.notes.isNotBlank()) {
                            Text("${cust.customField} ${cust.notes}".trim(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            if (cust.currentDue > 0.01) "বাকি: ${formatTaka(cust.currentDue)}" else "CLEAR (৳0)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (cust.currentDue > 0.01) DueCrimson else IncomingGreen
                        )
                        TextButton(onClick = { editingCustomer = cust }) {
                            Text("এডিট")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ServicesAndRulesScreen(
    services: List<ServiceEntity>,
    accountTypes: List<AccountTypeEntity>,
    rules: List<TransactionTypeRuleEntity>,
    onBack: () -> Unit,
    onSaveService: (id: String?, name: String, bangla: String, category: String, colorHex: String, isActive: Boolean) -> Unit,
    onSaveAccountType: (id: String?, name: String, bangla: String) -> Unit,
    onSaveRule: (id: String?, name: String, bangla: String, cat: String, accDir: String, cashDir: String, custReq: Boolean, allowDue: Boolean, desc: String) -> Unit
) {
    var selectedSection by remember { mutableStateOf("SERVICES") }
    var showAddService by remember { mutableStateOf(false) }
    var editingService by remember { mutableStateOf<ServiceEntity?>(null) }

    var showAddType by remember { mutableStateOf(false) }
    var editingRule by remember { mutableStateOf<TransactionTypeRuleEntity?>(null) }
    var showAddRule by remember { mutableStateOf(false) }

    if (showAddService || editingService != null) {
        var name by remember { mutableStateOf(editingService?.name ?: "") }
        var bangla by remember { mutableStateOf(editingService?.banglaName ?: "") }
        var category by remember { mutableStateOf(editingService?.category ?: "MFS") }
        var colorHex by remember { mutableStateOf(editingService?.colorHex ?: "#065F46") }
        var active by remember { mutableStateOf(editingService?.isActive ?: true) }

        AlertDialog(
            onDismissRequest = { showAddService = false; editingService = null },
            title = { Text(if (editingService == null) "নতুন সার্ভিস যোগ করুন" else "সার্ভিস এডিট করুন") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Service Name (e.g., mCash / Palli Bidyut)") }, singleLine = true)
                    OutlinedTextField(value = bangla, onValueChange = { bangla = it }, label = { Text("বাংলা নাম") }, singleLine = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("MFS", "RECHARGE", "BILL", "OTHER").forEach { cat ->
                            FilterChip(selected = category == cat, onClick = { category = cat }, label = { Text(cat) })
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = active, onCheckedChange = { active = it })
                        Text("Active (সচল)")
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onSaveService(editingService?.id, name, bangla, category, colorHex, active)
                        showAddService = false
                        editingService = null
                    },
                    enabled = name.isNotBlank()
                ) { Text("সেভ") }
            },
            dismissButton = { TextButton(onClick = { showAddService = false; editingService = null }) { Text("বাতিল") } }
        )
    }

    if (showAddType) {
        var name by remember { mutableStateOf("") }
        var bangla by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddType = false },
            title = { Text("কাস্টম অ্যাকাউন্ট টাইপ তৈরি করুন") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Type Name (e.g., Distributor / Sub-Agent)") }, singleLine = true)
                    OutlinedTextField(value = bangla, onValueChange = { bangla = it }, label = { Text("বাংলা নাম (যেমন: ডিস্ট্রিবিউটর)") }, singleLine = true)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onSaveAccountType(null, name, bangla)
                        showAddType = false
                    },
                    enabled = name.isNotBlank()
                ) { Text("সেভ") }
            },
            dismissButton = { TextButton(onClick = { showAddType = false }) { Text("বাতিল") } }
        )
    }

    if (showAddRule || editingRule != null) {
        var name by remember { mutableStateOf(editingRule?.name ?: "") }
        var bangla by remember { mutableStateOf(editingRule?.banglaName ?: "") }
        var accDir by remember { mutableStateOf(editingRule?.accountDirection ?: "OUTFLOW") }
        var cashDir by remember { mutableStateOf(editingRule?.cashDirection ?: "INFLOW_PAID") }
        var allowDue by remember { mutableStateOf(editingRule?.allowDue ?: true) }
        var desc by remember { mutableStateOf(editingRule?.description ?: "") }

        AlertDialog(
            onDismissRequest = { showAddRule = false; editingRule = null },
            title = { Text(if (editingRule == null) "নতুন লেনদেন নিয়ম (Rule)" else "লেনদেন নিয়ম এডিট করুন") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Rule Name") }, singleLine = true)
                    OutlinedTextField(value = bangla, onValueChange = { bangla = it }, label = { Text("বাংলা নাম") }, singleLine = true)
                    Text("নির্বাচিত অ্যাকাউন্টে প্রভাব:", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(selected = accDir == "OUTFLOW", onClick = { accDir = "OUTFLOW" }, label = { Text("অ্যাকাউন্ট -") })
                        FilterChip(selected = accDir == "INFLOW", onClick = { accDir = "INFLOW" }, label = { Text("অ্যাকাউন্ট +") })
                        FilterChip(selected = accDir == "TRANSFER", onClick = { accDir = "TRANSFER" }, label = { Text("ট্রান্সফার") })
                    }
                    Text("হাতে ক্যাশে প্রভাব:", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(selected = cashDir == "INFLOW_PAID", onClick = { cashDir = "INFLOW_PAID" }, label = { Text("ক্যাশ +") })
                        FilterChip(selected = cashDir == "OUTFLOW_PAID", onClick = { cashDir = "OUTFLOW_PAID" }, label = { Text("ক্যাশ -") })
                        FilterChip(selected = cashDir == "NONE", onClick = { cashDir = "NONE" }, label = { Text("নেই") })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = allowDue, onCheckedChange = { allowDue = it })
                        Text("কাস্টমার বাকি (Due) অনুমোদিত")
                    }
                    OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text("নিয়মের বিবরণ") })
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onSaveRule(editingRule?.id, name, bangla, "ALL", accDir, cashDir, false, allowDue, desc)
                        showAddRule = false
                        editingRule = null
                    },
                    enabled = name.isNotBlank()
                ) { Text("সেভ") }
            },
            dismissButton = { TextButton(onClick = { showAddRule = false; editingRule = null }) { Text("বাতিল") } }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 96.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") }
                Text("সার্ভিস, টাইপ ও লেনদেন নিয়ম", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = selectedSection == "SERVICES", onClick = { selectedSection = "SERVICES" }, label = { Text("সার্ভিস (${services.size})") })
                FilterChip(selected = selectedSection == "TYPES", onClick = { selectedSection = "TYPES" }, label = { Text("অ্যাকাউন্ট টাইপ (${accountTypes.size})") })
                FilterChip(selected = selectedSection == "RULES", onClick = { selectedSection = "RULES" }, label = { Text("লেনদেন নিয়ম (${rules.size})") })
            }
        }

        when (selectedSection) {
            "SERVICES" -> {
                item {
                    Button(onClick = { showAddService = true }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("+ নতুন সার্ভিস যোগ করুন (Add Custom Service)")
                    }
                }
                items(services, key = { it.id }) { srv ->
                    Card(modifier = Modifier.fillMaxWidth().clickable { editingService = srv }) {
                        Row(
                            modifier = Modifier.padding(14.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("${srv.name} (${srv.banglaName})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text("Category: ${srv.category} • ${if (srv.isActive) "Active" else "Inactive"}", style = MaterialTheme.typography.bodySmall)
                            }
                            Icon(Icons.Default.Edit, contentDescription = "Edit")
                        }
                    }
                }
            }
            "TYPES" -> {
                item {
                    Button(onClick = { showAddType = true }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("+ কাস্টম অ্যাকাউন্ট টাইপ তৈরি করুন")
                    }
                }
                items(accountTypes, key = { it.id }) { t ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.padding(14.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("${t.name} (${t.banglaName})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(if (t.isDefault) "System" else "Custom", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
            "RULES" -> {
                item {
                    Button(onClick = { showAddRule = true }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("+ নতুন লেনদেনের ধরন ও নিয়ম যোগ করুন")
                    }
                }
                items(rules, key = { it.id }) { rule ->
                    Card(modifier = Modifier.fillMaxWidth().clickable { editingRule = rule }) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("${rule.banglaName} (${rule.name})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Icon(Icons.Default.Edit, contentDescription = "Edit")
                            }
                            Text(
                                "Account: ${rule.accountDirection} • Cash: ${rule.cashDirection} • Due Allowed: ${if (rule.allowDue) "Yes" else "No"}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            if (rule.description.isNotBlank()) {
                                Text(rule.description, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ReportsScreen(
    accounts: List<ShopAccountEntity>,
    transactions: List<ShopTransactionEntity>,
    customers: List<CustomerEntity>,
    buildReport: (periodLabel: String, start: Long, end: Long) -> PeriodFinancialReport,
    onBack: () -> Unit
) {
    var periodMode by remember { mutableStateOf("TODAY") }
    var customDaysInput by remember { mutableStateOf("7") }
    val now = System.currentTimeMillis()
    val customDays = (customDaysInput.toIntOrNull() ?: 7).coerceIn(1, 3650)
    val customStartMillis = now - (customDays * 24L * 3600_000L)

    val report = remember(periodMode, customDays, accounts, transactions, customers) {
        when (periodMode) {
            "TODAY" -> buildReport("আজকের দৈনিক রিপোর্ট (Daily Report)", getStartOfTodayMillis(), now)
            "MONTH" -> buildReport("চলতি মাসের রিপোর্ট (Monthly Report)", getStartOfMonthMillis(), now)
            "CUSTOM" -> buildReport("গত $customDays দিনের রিপোর্ট (Custom Date Range)", customStartMillis, now)
            else -> buildReport("সর্বমোট রিপোর্ট (All-Time Summary)", 0L, now)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 96.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") }
                Column {
                    Text("লেনদেন ও ব্যালেন্স রিপোর্ট (Reports)", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("শুধুমাত্র ক্যাশ, অ্যাকাউন্ট মুভমেন্ট ও বকেয়া হিসাব", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = periodMode == "TODAY",
                        onClick = { periodMode = "TODAY" },
                        label = { Text("আজকের রিপোর্ট (Daily)") }
                    )
                    FilterChip(
                        selected = periodMode == "MONTH",
                        onClick = { periodMode = "MONTH" },
                        label = { Text("মাসিক রিপোর্ট (Monthly)") }
                    )
                    FilterChip(
                        selected = periodMode == "CUSTOM",
                        onClick = { periodMode = "CUSTOM" },
                        label = { Text("কাস্টম রেঞ্জ (Custom)") }
                    )
                    FilterChip(
                        selected = periodMode == "ALL_TIME",
                        onClick = { periodMode = "ALL_TIME" },
                        label = { Text("সর্বমোট (All)") }
                    )
                }

                if (periodMode == "CUSTOM") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = customDaysInput,
                            onValueChange = { customDaysInput = it },
                            label = { Text("কত দিনের রিপোর্ট? (Days)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        listOf("7", "15", "30", "90").forEach { d ->
                            FilterChip(
                                selected = customDaysInput == d,
                                onClick = { customDaysInput = d },
                                label = { Text("${d} দিন") }
                            )
                        }
                    }
                }
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = EmeraldDark),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("ক্যাশ হিসাব (CASH MOVEMENT)", style = MaterialTheme.typography.labelLarge, color = GoldLight)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("Opening Cash (শুরুর ক্যাশ)", style = MaterialTheme.typography.labelSmall, color = EmeraldLight)
                            Text(formatTaka(report.openingCash), style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Closing Cash (বর্তমান ক্যাশ)", style = MaterialTheme.typography.labelSmall, color = EmeraldLight)
                            Text(formatTaka(report.closingCash), style = MaterialTheme.typography.titleLarge, color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                    HorizontalDivider(color = Color.White.copy(alpha = 0.2f))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("ক্যাশ জমা (+Received): +৳${formatAmount(report.cashReceived)}", style = MaterialTheme.typography.bodyMedium, color = Color(0xFFA7F3D0), fontWeight = FontWeight.Bold)
                        Text("ক্যাশ প্রদান (-Paid): -৳${formatAmount(report.cashPaid)}", style = MaterialTheme.typography.bodyMedium, color = Color(0xFFFED7AA), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("লেনদেন ভলিউম ও বকেয়া সামারি", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("মোট লেনদেন সংখ্যা (Transactions):")
                        Text("${report.totalTransactionCount} টি", fontWeight = FontWeight.Bold)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("মোট লেনদেন ভলিউম (Total Volume):")
                        Text(formatTaka(report.totalTransactionVolume), fontWeight = FontWeight.Bold, color = EmeraldPrimary)
                    }
                    HorizontalDivider()
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("নতুন বাকি তৈরি (Due Created):")
                        Text("+৳${formatAmount(report.dueCreatedInPeriod)}", fontWeight = FontWeight.Bold, color = DueCrimson)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("বকেয়া আদায় (Due Collected):")
                        Text("-৳${formatAmount(report.dueCollectedInPeriod)}", fontWeight = FontWeight.Bold, color = IncomingGreen)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("বর্তমান মোট কাস্টমার বাকি (Current Due):")
                        Text(formatTaka(report.currentTotalCustomerDue), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = DueCrimson)
                    }
                }
            }
        }

        item {
            Text(
                "অ্যাকাউন্ট ভিত্তিক ইনকামিং ও আউটগোয়িং (Account-wise Movement)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        items(report.accountSummaries, key = { it.account.id }) { item ->
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text(item.account.nickname, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text("📱 ${item.account.phoneNumber} • ${item.txCount} টি লেনদেন", style = MaterialTheme.typography.labelSmall)
                        }
                        Text(
                            "ক্লোজিং: ${formatTaka(item.closingBalance)}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = parseHexColor(item.account.colorHex)
                        )
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("ওপেনিং: ${formatTaka(item.openingInPeriod)}", style = MaterialTheme.typography.bodySmall)
                        Text("Incoming: +৳${formatAmount(item.incoming)}", style = MaterialTheme.typography.bodySmall, color = IncomingGreen, fontWeight = FontWeight.Bold)
                        Text("Outgoing: -৳${formatAmount(item.outgoing)}", style = MaterialTheme.typography.bodySmall, color = OutgoingOrange, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun AuditHistoryScreen(
    auditLogs: List<AuditLogEntity>,
    onBack: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 96.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") }
                Column {
                    Text("অডিট ও সংশোধন ইতিহাস (Audit Log)", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("সকল এডিট, রিভার্স ও ব্যালেন্স মিলকরণের স্থায়ী রেকর্ড", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        if (auditLogs.isEmpty()) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Box(modifier = Modifier.padding(32.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text("এখনও কোনো লেনদেন এডিট বা রিভার্স করা হয়নি।")
                    }
                }
            }
        } else {
            items(auditLogs, key = { it.id }) { log ->
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(log.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(formatDateTime(log.timestamp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Surface(color = DueCrimsonLight.copy(alpha = 0.5f), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                            Text(log.previousValueSummary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(8.dp), color = Color(0xFF7F1D1D))
                        }
                        Surface(color = IncomingGreenLight.copy(alpha = 0.6f), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                            Text(log.newValueSummary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(8.dp), color = EmeraldDark, fontWeight = FontWeight.Bold)
                        }
                        if (log.reason.isNotBlank()) {
                            Text("কারণ: ${log.reason}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BackupAndSecurityScreen(
    settings: ShopSettingsEntity,
    repository: ShopKhataRepository,
    onBack: () -> Unit,
    onUpdateSettings: ((ShopSettingsEntity) -> ShopSettingsEntity) -> Unit,
    onSignInCloud: (email: String, pass: String, isRegister: Boolean) -> Unit,
    onSignOutCloud: () -> Unit,
    onSyncCloudNow: () -> Unit,
    onPullCloudNow: () -> Unit = {},
    onBuildCsv: (String) -> String,
    onShowMessage: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var shopNameInput by remember(settings.shopName) { mutableStateOf(settings.shopName) }
    var ownerPhoneInput by remember(settings.ownerPhone) { mutableStateOf(settings.ownerPhone) }
    var pinInput by remember(settings.pinCode) { mutableStateOf(settings.pinCode) }
    var isPinEnabled by remember(settings.isPinEnabled) { mutableStateOf(settings.isPinEnabled) }

    var cloudEmail by remember { mutableStateOf(settings.cloudUserEmail ?: "") }
    var cloudPassword by remember { mutableStateOf("") }

    var backupJsonPreview by remember { mutableStateOf("") }
    var showRestoreDialog by remember { mutableStateOf(false) }
    var restoreJsonInput by remember { mutableStateOf("") }

    fun shareText(title: String, content: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, content)
        }
        context.startActivity(Intent.createChooser(intent, title))
    }

    if (showRestoreDialog) {
        AlertDialog(
            onDismissRequest = { showRestoreDialog = false },
            title = { Text("ব্যাকআপ ডাটা রিস্টোর করুন (Restore Backup JSON)") },
            text = {
                OutlinedTextField(
                    value = restoreJsonInput,
                    onValueChange = { restoreJsonInput = it },
                    label = { Text("পূর্বের এক্সপোর্ট করা JSON পেস্ট করুন") },
                    modifier = Modifier.fillMaxWidth().height(180.dp)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            val ok = repository.restoreFromBackupJson(restoreJsonInput)
                            if (ok) {
                                onShowMessage("সকল ডাটা সফলভাবে রিস্টোর ও ব্যালেন্স পুনঃগণনা করা হয়েছে!")
                                showRestoreDialog = false
                            } else {
                                onShowMessage("সঠিক ব্যাকআপ JSON পাওয়া যায়নি")
                            }
                        }
                    },
                    enabled = restoreJsonInput.isNotBlank()
                ) { Text("রিস্টোর করুন") }
            },
            dismissButton = { TextButton(onClick = { showRestoreDialog = false }) { Text("বাতিল") } }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 96.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") }
                Text("ব্যাকআপ, ক্লাউড ও সিকিউরিটি", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
        }

        item {
            Card(shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.CloudDone, contentDescription = null, tint = EmeraldPrimary)
                        Text("Firebase ক্লাউড ডাটাবেস ও সিঙ্ক", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        text = if (settings.lastCloudSyncTimestamp != null)
                            "সর্বশেষ সফল সিঙ্ক: ${formatDateTime(settings.lastCloudSyncTimestamp)}"
                        else "অফলাইন মোডে ১০০% কাজ করছে। ইন্টারনেট ও Firebase লগইন থাকলে ক্লাউডে সিঙ্ক হবে।",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = cloudEmail,
                        onValueChange = { cloudEmail = it },
                        label = { Text("Shop Owner Email (Firebase Auth)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = cloudPassword,
                        onValueChange = { cloudPassword = it },
                        label = { Text("Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = { onSignInCloud(cloudEmail, cloudPassword, false) },
                            modifier = Modifier.weight(1f)
                        ) { Text("লগইন ও সিঙ্ক") }
                        OutlinedButton(
                            onClick = { onSignInCloud(cloudEmail, cloudPassword, true) },
                            modifier = Modifier.weight(1f)
                        ) { Text("নতুন রেজিস্টার") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        FilledTonalButton(
                            onClick = onSyncCloudNow,
                            modifier = Modifier.weight(1f).testTag("cloud_sync_upload_btn")
                        ) {
                            Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("ক্লাউডে সেভ (Sync)")
                        }
                        OutlinedButton(
                            onClick = onPullCloudNow,
                            modifier = Modifier.weight(1f).testTag("cloud_sync_pull_btn")
                        ) {
                            Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("ক্লাউড রিস্টোর (Pull)")
                        }
                    }
                    if (!settings.cloudUserEmail.isNullOrBlank()) {
                        TextButton(onClick = onSignOutCloud) {
                            Text("লগআউট (${settings.cloudUserEmail})", color = DueCrimson)
                        }
                    }
                }
            }
        }

        item {
            Card(shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("ব্যাকআপ, রিকভারি ও এক্সপোর্ট (Backup & Export)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        text = if (settings.lastBackupTimestamp != null)
                            "সর্বশেষ লোকাল ব্যাকআপ: ${formatDateTime(settings.lastBackupTimestamp)}"
                        else "এখনও ব্যাকআপ ফাইল তৈরি করা হয়নি",
                        style = MaterialTheme.typography.bodySmall,
                        color = EmeraldPrimary
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = {
                                scope.launch {
                                    val json = repository.exportFullBackupJson()
                                    backupJsonPreview = json
                                    shareText("Hisab Khata Full Backup JSON", json)
                                }
                            },
                            modifier = Modifier.weight(1f).testTag("export_full_backup_btn")
                        ) {
                            Icon(Icons.Default.Backup, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("সম্পূর্ণ ব্যাকআপ (Backup)")
                        }
                        OutlinedButton(
                            onClick = {
                                restoreJsonInput = backupJsonPreview
                                showRestoreDialog = true
                            },
                            modifier = Modifier.weight(1f).testTag("restore_backup_btn")
                        ) {
                            Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("রিস্টোর (Restore)")
                        }
                    }

                    HorizontalDivider()
                    Text("CSV শিট এক্সপোর্ট করুন (Export Statements & Ledgers):", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        FilledTonalButton(
                            onClick = { shareText("Transactions Export CSV", onBuildCsv("TRANSACTIONS")) },
                            modifier = Modifier.weight(1f)
                        ) { Text("লেনদেন CSV") }
                        FilledTonalButton(
                            onClick = { shareText("Customer & Due List CSV", onBuildCsv("CUSTOMERS_DUE")) },
                            modifier = Modifier.weight(1f)
                        ) { Text("কাস্টমার ও বাকি CSV") }
                        FilledTonalButton(
                            onClick = { shareText("Accounts Statement CSV", onBuildCsv("ACCOUNTS")) },
                            modifier = Modifier.weight(1f)
                        ) { Text("অ্যাকাউন্ট CSV") }
                    }
                }
            }
        }

        item {
            Card(shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("দোকানের নাম ও অ্যাপ PIN সিকিউরিটি", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = shopNameInput,
                        onValueChange = { shopNameInput = it },
                        label = { Text("দোকানের নাম (Shop Name)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = ownerPhoneInput,
                        onValueChange = { ownerPhoneInput = it },
                        label = { Text("মালিকের মোবাইল নাম্বার") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = { if (it.length <= 6) pinInput = it },
                        label = { Text("৪-সংখ্যার সিকিউরিটি PIN (App Lock PIN)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("security_pin_input")
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(checked = isPinEnabled, onCheckedChange = { isPinEnabled = it })
                        Spacer(Modifier.width(8.dp))
                        Text("অ্যাপ ওপেন করার সময় PIN লক চালু রাখুন")
                    }
                    Button(
                        onClick = {
                            onUpdateSettings {
                                it.copy(
                                    shopName = shopNameInput.trim().ifBlank { it.shopName },
                                    ownerPhone = ownerPhoneInput.trim(),
                                    pinCode = pinInput.trim(),
                                    isPinEnabled = isPinEnabled && pinInput.isNotBlank()
                                )
                            }
                            onShowMessage("দোকানের তথ্য ও সিকিউরিটি সেটিংস সংরক্ষিত হয়েছে")
                        },
                        modifier = Modifier.fillMaxWidth().testTag("save_security_settings_btn")
                    ) {
                        Text("সেটিংস সেভ করুন")
                    }
                }
            }
        }
    }
}
