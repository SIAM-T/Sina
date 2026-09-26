package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.*
import com.example.ui.components.ServiceBadge
import com.example.ui.components.parseHexColor
import com.example.ui.theme.*
import kotlin.math.max

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NewTransactionScreen(
    accounts: List<ShopAccountEntity>,
    rules: List<TransactionTypeRuleEntity>,
    customers: List<CustomerEntity>,
    preselectedAccountId: String?,
    preselectedRuleId: String?,
    computePreview: (
        primaryAccount: ShopAccountEntity,
        rule: TransactionTypeRuleEntity,
        amount: Double,
        paidAmount: Double,
        secondaryAccount: ShopAccountEntity?,
        customer: CustomerEntity?
    ) -> BalancePreview,
    onQuickCreateCustomer: (name: String, phone: String, onCreated: (CustomerEntity) -> Unit) -> Unit,
    onCreateCustomRule: (
        name: String,
        banglaName: String,
        accountDirection: String,
        cashDirection: String,
        allowDue: Boolean
    ) -> Unit,
    onConfirmTransaction: (
        accountId: String,
        ruleId: String,
        amount: Double,
        paidAmount: Double,
        paymentMethodAccountId: String?,
        customerId: String?,
        note: String,
        secondaryTransferAccountId: String?
    ) -> Unit,
    onOpenManageAccounts: () -> Unit = {}
) {
    val activeAccounts = remember(accounts) { accounts.filter { it.isActive } }
    val defaultAccount = remember(activeAccounts, preselectedAccountId) {
        activeAccounts.find { it.id == preselectedAccountId }
            ?: activeAccounts.firstOrNull { it.id != ShopKhataRepository.CASH_ACCOUNT_ID }
            ?: activeAccounts.firstOrNull()
    }

    var selectedAccountId by remember(defaultAccount?.id) {
        mutableStateOf(defaultAccount?.id ?: "")
    }
    val selectedAccount = activeAccounts.find { it.id == selectedAccountId } ?: defaultAccount

    // Filter rules applicable to the selected account (#7 Step 2)
    val availableRules = remember(rules, selectedAccount) {
        if (selectedAccount == null) rules
        else rules.filter { rule ->
            rule.allowedServiceCategories == "ALL" ||
                rule.allowedServiceCategories.equals(selectedAccount.category, ignoreCase = true) ||
                (selectedAccount.category == "RECHARGE" && rule.id == "rule_recharge")
        }
    }

    var selectedRuleId by remember(availableRules, preselectedRuleId) {
        val pre = availableRules.find { it.id == preselectedRuleId }
        val def = when (selectedAccount?.category) {
            "RECHARGE" -> availableRules.find { it.id == "rule_recharge" }
            else -> availableRules.find { it.id == "rule_cash_in" }
        } ?: availableRules.firstOrNull()
        mutableStateOf((pre ?: def)?.id ?: "")
    }
    val selectedRule = availableRules.find { it.id == selectedRuleId } ?: availableRules.firstOrNull()

    // Amount & Payment status
    var amountText by remember { mutableStateOf("") }
    var paymentStatusMode by remember { mutableStateOf("PAID") } // "PAID", "PARTIAL", "DUE"
    var customPaidText by remember { mutableStateOf("") }
    var noteText by remember { mutableStateOf("") }

    // Customer selection & Quick Add (#11, #12)
    var selectedCustomerId by remember { mutableStateOf<String?>(null) }
    var customerSearchText by remember { mutableStateOf("") }
    var showCustomerPicker by remember { mutableStateOf(false) }
    var showQuickAddCustomerDialog by remember { mutableStateOf(false) }

    // Secondary / Payment / Transfer destination account
    var selectedPaymentAccountId by remember { mutableStateOf(ShopKhataRepository.CASH_ACCOUNT_ID) }
    var selectedTransferTargetId by remember(selectedAccountId, activeAccounts) {
        val firstOther = activeAccounts.firstOrNull { it.id != selectedAccountId }
        mutableStateOf(firstOther?.id ?: ShopKhataRepository.CASH_ACCOUNT_ID)
    }

    // Custom Transaction Type Dialog (#25)
    var showCustomRuleDialog by remember { mutableStateOf(false) }

    // Confirmation Dialog (#42)
    var showConfirmModal by remember { mutableStateOf(false) }

    val selectedCustomer = customers.find { it.id == selectedCustomerId }
    val numericAmount = amountText.toDoubleOrNull() ?: 0.0
    val effectivePaidAmount = when {
        selectedRule?.allowDue != true -> numericAmount
        paymentStatusMode == "PAID" -> numericAmount
        paymentStatusMode == "DUE" -> 0.0
        else -> (customPaidText.toDoubleOrNull() ?: 0.0).coerceIn(0.0, numericAmount)
    }
    val remainingDue = if (selectedRule?.allowDue == true) max(0.0, numericAmount - effectivePaidAmount) else 0.0

    val secondaryAccount = remember(
        selectedRule,
        selectedPaymentAccountId,
        selectedTransferTargetId,
        activeAccounts
    ) {
        if (selectedRule?.accountDirection == "TRANSFER") {
            activeAccounts.find { it.id == selectedTransferTargetId }
        } else if (selectedRule?.cashDirection != "NONE") {
            activeAccounts.find { it.id == selectedPaymentAccountId }
        } else null
    }

    val livePreview = remember(
        selectedAccount,
        selectedRule,
        numericAmount,
        effectivePaidAmount,
        secondaryAccount,
        selectedCustomer
    ) {
        if (selectedAccount != null && selectedRule != null) {
            computePreview(
                selectedAccount,
                selectedRule,
                numericAmount,
                effectivePaidAmount,
                secondaryAccount,
                selectedCustomer
            )
        } else null
    }

    val requiresCustomerForDue = remainingDue > 0.001 && selectedCustomer == null

    if (showQuickAddCustomerDialog) {
        QuickAddCustomerDialog(
            initialSearch = customerSearchText,
            onDismiss = { showQuickAddCustomerDialog = false },
            onSave = { name, phone ->
                onQuickCreateCustomer(name, phone) { created ->
                    selectedCustomerId = created.id
                    showQuickAddCustomerDialog = false
                    showCustomerPicker = false
                }
            }
        )
    }

    if (showCustomRuleDialog) {
        CustomTransactionRuleDialog(
            onDismiss = { showCustomRuleDialog = false },
            onSave = { name, bangla, accDir, cashDir, allowDue ->
                onCreateCustomRule(name, bangla, accDir, cashDir, allowDue)
                showCustomRuleDialog = false
            }
        )
    }

    if (showConfirmModal && selectedAccount != null && selectedRule != null && livePreview != null) {
        AlertDialog(
            onDismissRequest = { showConfirmModal = false },
            title = {
                Text(
                    "লেনদেন নিশ্চিত করুন (Confirm Transaction)",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "${selectedAccount.nickname} (${selectedAccount.phoneNumber})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "ধরন: ${selectedRule.name} (${selectedRule.banglaName}) • পরিমাণ: ${formatTaka(numericAmount)}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    HorizontalDivider()
                    Text(
                        "ব্যালেন্স পরিবর্তন (Balance Changes):",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "• ${livePreview.primaryAccountName}: ${formatTaka(livePreview.primaryBefore)} → ${formatSignedTaka(livePreview.primaryDelta)} = ${formatTaka(livePreview.primaryAfter)}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (livePreview.secondaryAccountName != null && livePreview.secondaryAfter != null) {
                        Text(
                            "• ${livePreview.secondaryAccountName}: ${formatTaka(livePreview.secondaryBefore ?: 0.0)} → ${formatSignedTaka(livePreview.secondaryDelta)} = ${formatTaka(livePreview.secondaryAfter)}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    if (livePreview.customerName != null && livePreview.customerDueAfter != null) {
                        Text(
                            "• কাস্টমার (${livePreview.customerName}) বাকি: ${formatTaka(livePreview.customerDueBefore ?: 0.0)} → ${formatSignedTaka(livePreview.customerDueDelta)} = ${formatTaka(livePreview.customerDueAfter)}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (livePreview.customerDueDelta > 0) DueCrimson else EmeraldPrimary
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmModal = false
                        onConfirmTransaction(
                            selectedAccount.id,
                            selectedRule.id,
                            numericAmount,
                            effectivePaidAmount,
                            selectedPaymentAccountId,
                            selectedCustomer?.id,
                            noteText,
                            if (selectedRule.accountDirection == "TRANSFER") selectedTransferTargetId else null
                        )
                        amountText = ""
                        customPaidText = ""
                        noteText = ""
                        paymentStatusMode = "PAID"
                    },
                    modifier = Modifier.testTag("final_confirm_tx_button")
                ) {
                    Text("নিশ্চিত ও সেভ করুন")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmModal = false }) {
                    Text("ফিরে যান")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .padding(bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Step 1: Select Service / Account (#7 Step 1)
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "১. অ্যাকাউন্ট নির্বাচন করুন (Select Account)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    TextButton(
                        onClick = onOpenManageAccounts,
                        modifier = Modifier.testTag("tx_screen_add_account_btn")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("+ অ্যাকাউন্ট")
                    }
                }
                Text(
                    text = "একই নাম্বারে একাধিক অ্যাকাউন্ট থাকলেও সঠিক সার্ভিসটি সিলেক্ট করুন",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    activeAccounts.forEach { acc ->
                        val isSelected = acc.id == selectedAccount?.id
                        val accent = parseHexColor(acc.colorHex)
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (isSelected) accent.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .widthIn(min = 165.dp)
                                .border(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) accent else Color.Transparent,
                                    shape = RoundedCornerShape(16.dp)
                                )
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { selectedAccountId = acc.id }
                                .testTag("select_account_${acc.id}")
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    ServiceBadge(
                                        serviceName = acc.serviceName,
                                        accountType = acc.accountType,
                                        colorHex = acc.colorHex
                                    )
                                    if (isSelected) {
                                        Icon(
                                            Icons.Default.CheckCircle,
                                            contentDescription = "Selected",
                                            tint = accent,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = acc.nickname,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = acc.phoneNumber,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = formatTaka(acc.currentBalance),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = accent
                                )
                            }
                        }
                    }
                }
            }
        }

        // Step 2: Select Transaction Type (#7 Step 2)
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "২. লেনদেনের ধরন (Transaction Type)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    TextButton(onClick = { showCustomRuleDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("কাস্টম ধরন")
                    }
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    availableRules.forEach { rule ->
                        val isSelected = rule.id == selectedRule?.id
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedRuleId = rule.id },
                            label = {
                                Text(
                                    text = "${rule.banglaName} (${rule.name})",
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            },
                            leadingIcon = {
                                if (isSelected) {
                                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                            },
                            modifier = Modifier.testTag("select_rule_${rule.id}")
                        )
                    }
                }

                if (selectedRule != null && selectedRule.description.isNotBlank()) {
                    Text(
                        text = "নিয়ম: ${selectedRule.description}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                // If Account Transfer is selected, show Destination Account selector (#22)
                if (selectedRule?.accountDirection == "TRANSFER") {
                    HorizontalDivider()
                    Text(
                        text = "কোন অ্যাকাউন্টে টাকা যাবে? (Destination Account):",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        activeAccounts.filter { it.id != selectedAccount?.id }.forEach { targetAcc ->
                            val isTarget = targetAcc.id == selectedTransferTargetId
                            FilterChip(
                                selected = isTarget,
                                onClick = { selectedTransferTargetId = targetAcc.id },
                                label = {
                                    Text("${targetAcc.nickname} (${targetAcc.phoneNumber}) • ${formatTaka(targetAcc.currentBalance)}")
                                }
                            )
                        }
                    }
                }
            }
        }

        // Step 3: Enter Amount & Quick Denomination Chips
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "৩. টাকার পরিমাণ (Enter Amount ৳)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("পরিমাণ (Amount in Taka ৳)") },
                    placeholder = { Text("0") },
                    prefix = { Text("৳ ", fontWeight = FontWeight.Bold, fontSize = 20.sp) },
                    textStyle = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("tx_amount_input")
                )

                // Fast Denomination Shortcut Chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(50, 100, 200, 500, 1000, 2000, 5000, 10000).forEach { quickVal ->
                        SuggestionChip(
                            onClick = {
                                val current = amountText.toDoubleOrNull() ?: 0.0
                                amountText = (current + quickVal).toLong().toString()
                            },
                            label = { Text("+৳$quickVal", fontWeight = FontWeight.SemiBold) }
                        )
                    }
                    if (amountText.isNotEmpty()) {
                        SuggestionChip(
                            onClick = { amountText = "" },
                            label = { Text("Clear", color = DueCrimson) }
                        )
                    }
                }

                // Step 4: Payment Status (Paid / Partial / Due) (#13 & #14)
                if (selectedRule?.allowDue == true) {
                    HorizontalDivider()
                    Text(
                        text = "পেমেন্ট স্ট্যাটাস (Payment Status):",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            "PAID" to "নগদ পরিশোধ (Paid)",
                            "PARTIAL" to "আংশিক (Partial)",
                            "DUE" to "সম্পূর্ণ বাকি (Due)"
                        ).forEach { (mode, label) ->
                            val selected = paymentStatusMode == mode
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    paymentStatusMode = mode
                                    if (mode == "PARTIAL" && customPaidText.isBlank()) {
                                        customPaidText = ""
                                    }
                                },
                                label = { Text(label) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("payment_status_$mode")
                            )
                        }
                    }

                    AnimatedVisibility(visible = paymentStatusMode == "PARTIAL") {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = customPaidText,
                                onValueChange = { customPaidText = it },
                                label = { Text("কাস্টমার নগদ দিয়েছে (Customer Paid ৳)") },
                                prefix = { Text("৳ ") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("tx_partial_paid_input")
                            )
                        }
                    }

                    // Show clear breakdown of Transaction vs Received vs Customer Due (#14)
                    if (numericAmount > 0) {
                        Surface(
                            color = if (remainingDue > 0) GoldLight.copy(alpha = 0.65f) else EmeraldContainer,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp).fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text("মোট সার্ভিস", style = MaterialTheme.typography.labelSmall, color = Color(0xFF334155))
                                    Text(formatTaka(numericAmount), fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                                }
                                Column {
                                    Text("নগদ প্রাপ্ত (Received)", style = MaterialTheme.typography.labelSmall, color = Color(0xFF334155))
                                    Text(formatTaka(effectivePaidAmount), fontWeight = FontWeight.Bold, color = IncomingGreen)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("বাকি থাকবে (Due)", style = MaterialTheme.typography.labelSmall, color = Color(0xFF334155))
                                    Text(
                                        formatTaka(remainingDue),
                                        fontWeight = FontWeight.Bold,
                                        color = if (remainingDue > 0) DueCrimson else IncomingGreen
                                    )
                                }
                            }
                        }
                    }
                }

                if (selectedRule?.cashDirection != "NONE" && effectivePaidAmount > 0 && activeAccounts.size > 1) {
                    HorizontalDivider()
                    Text(
                        text = "পেমেন্ট মাধ্যম (Payment / Cash Account):",
                        style = MaterialTheme.typography.labelLarge
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        activeAccounts.filter { it.id != selectedAccount?.id }.forEach { payAcc ->
                            FilterChip(
                                selected = payAcc.id == selectedPaymentAccountId,
                                onClick = { selectedPaymentAccountId = payAcc.id },
                                label = { Text("${payAcc.nickname} (${payAcc.phoneNumber})") }
                            )
                        }
                    }
                }
            }
        }

        // Step 5: Customer Selection & Quick Customer Creation (#11 & #12)
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "৪. কাস্টমার নির্বাচন (Select Customer)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (requiresCustomerForDue) {
                            Text(
                                text = "* বাকি রাখার জন্য কাস্টমার সিলেক্ট করা আবশ্যক",
                                style = MaterialTheme.typography.labelSmall,
                                color = DueCrimson,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    FilledTonalButton(
                        onClick = { showQuickAddCustomerDialog = true },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("quick_add_customer_button")
                    ) {
                        Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("+ নতুন কাস্টমার")
                    }
                }

                if (selectedCustomer != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = selectedCustomer.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "📱 ${selectedCustomer.phone} • পূর্বের বাকি: ${formatTaka(selectedCustomer.currentDue)}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            IconButton(onClick = { selectedCustomerId = null }) {
                                Icon(Icons.Default.Close, contentDescription = "Remove Customer")
                            }
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = customerSearchText,
                        onValueChange = {
                            customerSearchText = it
                            showCustomerPicker = true
                        },
                        label = { Text("কাস্টমারের নাম বা ফোন নাম্বার দিয়ে খুঁজুন") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("customer_search_field")
                    )

                    val filteredCustomers = remember(customers, customerSearchText) {
                        val q = customerSearchText.trim().lowercase()
                        if (q.isEmpty()) customers.take(5)
                        else customers.filter {
                            it.name.lowercase().contains(q) || it.phone.lowercase().contains(q)
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        filteredCustomers.forEach { cust ->
                            AssistChip(
                                onClick = { selectedCustomerId = cust.id },
                                label = {
                                    Text("${cust.name} (${cust.phone})")
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(16.dp))
                                },
                                modifier = Modifier.testTag("pick_customer_${cust.id}")
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    label = { Text("নোট / বিবরণ (Optional Note)") },
                    placeholder = { Text("যেমন: রেফারেন্স নাম্বার বা অতিরিক্ত তথ্য") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("tx_note_input")
                )
            }
        }

        // Step 6: Automatic Balance Movement Preview Card (#8)
        if (livePreview != null && numericAmount > 0) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(20.dp))
                    .testTag("balance_movement_preview_card")
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.AutoGraph,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "স্বয়ংক্রিয় ব্যালেন্স পরিবর্তন প্রিভিউ (Automatic Balance Movement)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    HorizontalDivider()

                    // Primary Account row
                    PreviewRowItem(
                        label = "${livePreview.primaryAccountName} (${livePreview.primaryPhone})",
                        before = livePreview.primaryBefore,
                        delta = livePreview.primaryDelta,
                        after = livePreview.primaryAfter
                    )

                    // Secondary / Cash Account row
                    if (livePreview.secondaryAccountName != null && livePreview.secondaryAfter != null) {
                        PreviewRowItem(
                            label = livePreview.secondaryAccountName,
                            before = livePreview.secondaryBefore ?: 0.0,
                            delta = livePreview.secondaryDelta,
                            after = livePreview.secondaryAfter
                        )
                    }

                    // Customer Due row
                    if (livePreview.customerName != null && livePreview.customerDueAfter != null) {
                        PreviewRowItem(
                            label = "কাস্টমার বাকি (${livePreview.customerName})",
                            before = livePreview.customerDueBefore ?: 0.0,
                            delta = livePreview.customerDueDelta,
                            after = livePreview.customerDueAfter,
                            isDue = true
                        )
                    }
                }
            }
        }

        // Submit Button
        Button(
            onClick = { showConfirmModal = true },
            enabled = selectedAccount != null && selectedRule != null && numericAmount > 0.0 && !requiresCustomerForDue,
            shape = RoundedCornerShape(18.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("submit_transaction_button")
        ) {
            Icon(Icons.Default.CheckCircle, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (requiresCustomerForDue) "বাকি রাখতে কাস্টমার সিলেক্ট করুন" else "লেনদেন নিশ্চিত করুন (Confirm & Save)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun PreviewRowItem(
    label: String,
    before: Double,
    delta: Double,
    after: Double,
    isDue: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            Text(
                "পূর্বের: ${formatTaka(before)}  (${formatSignedTaka(delta)})",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "নতুন: ${formatTaka(after)}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = when {
                    isDue && after > 0 -> DueCrimson
                    delta > 0 -> IncomingGreen
                    delta < 0 -> OutgoingOrange
                    else -> MaterialTheme.colorScheme.onSurface
                }
            )
        }
    }
}

@Composable
private fun QuickAddCustomerDialog(
    initialSearch: String,
    onDismiss: () -> Unit,
    onSave: (name: String, phone: String) -> Unit
) {
    val startsWithDigit = initialSearch.firstOrNull()?.isDigit() == true
    var name by remember { mutableStateOf(if (!startsWithDigit) initialSearch else "") }
    var phone by remember { mutableStateOf(if (startsWithDigit) initialSearch else "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("+ দ্রুত নতুন কাস্টমার যোগ করুন (Quick Add Customer)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("কাস্টমারের নাম (Customer Name)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("quick_customer_name_input")
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("মোবাইল নাম্বার (Phone Number)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("quick_customer_phone_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(name, phone) },
                enabled = name.isNotBlank() && phone.isNotBlank(),
                modifier = Modifier.testTag("quick_customer_save_button")
            ) {
                Text("সেভ ও সিলেক্ট করুন")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("বাতিল")
            }
        }
    )
}

@Composable
private fun CustomTransactionRuleDialog(
    onDismiss: () -> Unit,
    onSave: (name: String, banglaName: String, accountDirection: String, cashDirection: String, allowDue: Boolean) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var banglaName by remember { mutableStateOf("") }
    var accountDirection by remember { mutableStateOf("OUTFLOW") } // "OUTFLOW", "INFLOW", "TRANSFER"
    var cashDirection by remember { mutableStateOf("INFLOW_PAID") } // "INFLOW_PAID", "OUTFLOW_PAID", "NONE"
    var allowDue by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("কাস্টম লেনদেনের ধরন তৈরি করুন", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("English Name (e.g., Remittance Payout)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = banglaName,
                    onValueChange = { banglaName = it },
                    label = { Text("বাংলা নাম (যেমন: রেমিট্যান্স প্রদান)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("নির্বাচিত অ্যাকাউন্টের ব্যালেন্স কী হবে?", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = accountDirection == "OUTFLOW",
                        onClick = {
                            accountDirection = "OUTFLOW"
                            cashDirection = "INFLOW_PAID"
                        },
                        label = { Text("অ্যাকাউন্ট কমবে (-)") }
                    )
                    FilterChip(
                        selected = accountDirection == "INFLOW",
                        onClick = {
                            accountDirection = "INFLOW"
                            cashDirection = "OUTFLOW_PAID"
                        },
                        label = { Text("অ্যাকাউন্ট বাড়বে (+)") }
                    )
                }

                Text("হাতে ক্যাশের ব্যালেন্স কী হবে?", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = cashDirection == "INFLOW_PAID",
                        onClick = { cashDirection = "INFLOW_PAID" },
                        label = { Text("ক্যাশ +") }
                    )
                    FilterChip(
                        selected = cashDirection == "OUTFLOW_PAID",
                        onClick = { cashDirection = "OUTFLOW_PAID" },
                        label = { Text("ক্যাশ -") }
                    )
                    FilterChip(
                        selected = cashDirection == "NONE",
                        onClick = { cashDirection = "NONE" },
                        label = { Text("অপরিবর্তিত") }
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = allowDue, onCheckedChange = { allowDue = it })
                    Text("বাকি (Due) রাখার সুবিধা চালু থাকবে")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(name, banglaName, accountDirection, cashDirection, allowDue) },
                enabled = name.isNotBlank()
            ) {
                Text("সংরক্ষণ করুন")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("বাতিল")
            }
        }
    )
}
