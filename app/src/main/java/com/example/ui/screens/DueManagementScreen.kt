package com.example.ui.screens

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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.*
import com.example.ui.theme.*
import kotlin.math.abs

@Composable
fun DueManagementScreen(
    customers: List<CustomerEntity>,
    accounts: List<ShopAccountEntity>,
    dueEntries: List<DueLedgerEntryEntity>,
    onCollectDue: (customerId: String, amount: Double, receivingAccountId: String, note: String) -> Unit,
    onManualAddDue: (customerId: String, amount: Double, reason: String) -> Unit,
    onManualDeductDue: (customerId: String, amount: Double, isActualPayment: Boolean, receivingAccountId: String?, reason: String) -> Unit,
    onAddOrEditCustomer: (existing: CustomerEntity?, name: String, phone: String, address: String, notes: String, customField: String, openingDue: Double) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var filterMode by remember { mutableStateOf("ALL") } // "ALL", "HAS_DUE", "CLEAR"

    var selectedCustomerForLedger by remember { mutableStateOf<CustomerEntity?>(null) }
    var customerForCollectModal by remember { mutableStateOf<CustomerEntity?>(null) }
    var customerForManualAddModal by remember { mutableStateOf<CustomerEntity?>(null) }
    var customerForManualDeductModal by remember { mutableStateOf<CustomerEntity?>(null) }
    var showCreateCustomerModal by remember { mutableStateOf(false) }

    val totalOutstandingDue = remember(customers) {
        customers.sumOf { it.currentDue }
    }
    val dueCustomersCount = remember(customers) {
        customers.count { it.currentDue > 0.01 }
    }

    val filteredCustomers = remember(customers, searchQuery, filterMode) {
        val q = searchQuery.trim().lowercase()
        customers.filter { c ->
            val matchesQuery = q.isEmpty() || c.name.lowercase().contains(q) || c.phone.lowercase().contains(q)
            val matchesFilter = when (filterMode) {
                "HAS_DUE" -> c.currentDue > 0.01
                "CLEAR" -> c.currentDue <= 0.01
                else -> true
            }
            matchesQuery && matchesFilter
        }
    }

    // Modals
    if (customerForCollectModal != null) {
        CollectDueDialog(
            customer = customerForCollectModal!!,
            allCustomers = customers,
            accounts = accounts,
            onDismiss = { customerForCollectModal = null },
            onConfirm = { custId, amt, accId, note ->
                onCollectDue(custId, amt, accId, note)
                customerForCollectModal = null
            }
        )
    }

    if (customerForManualAddModal != null) {
        ManualAddDueDialog(
            customer = customerForManualAddModal!!,
            allCustomers = customers,
            onDismiss = { customerForManualAddModal = null },
            onConfirm = { custId, amt, reason ->
                onManualAddDue(custId, amt, reason)
                customerForManualAddModal = null
            }
        )
    }

    if (customerForManualDeductModal != null) {
        ManualDeductDueDialog(
            customer = customerForManualDeductModal!!,
            allCustomers = customers,
            accounts = accounts,
            onDismiss = { customerForManualDeductModal = null },
            onConfirm = { custId, amt, isPayment, accId, reason ->
                onManualDeductDue(custId, amt, isPayment, accId, reason)
                customerForManualDeductModal = null
            }
        )
    }

    if (showCreateCustomerModal) {
        CustomerFormDialog(
            existing = null,
            onDismiss = { showCreateCustomerModal = false },
            onSave = { name, phone, address, notes, customField, openingDue ->
                onAddOrEditCustomer(null, name, phone, address, notes, customField, openingDue)
                showCreateCustomerModal = false
            }
        )
    }

    // If a customer is selected to view their full Customer Due Ledger (#15 & #20)
    val liveSelectedCustomer = remember(selectedCustomerForLedger, customers) {
        selectedCustomerForLedger?.let { sel -> customers.find { it.id == sel.id } }
    }
    if (liveSelectedCustomer != null) {
        CustomerDueLedgerDetailView(
            customer = liveSelectedCustomer,
            entries = dueEntries.filter { it.customerId == liveSelectedCustomer.id },
            onBack = { selectedCustomerForLedger = null },
            onCollectClick = { customerForCollectModal = liveSelectedCustomer },
            onManualAddClick = { customerForManualAddModal = liveSelectedCustomer },
            onManualDeductClick = { customerForManualDeductModal = liveSelectedCustomer }
        )
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 96.dp)
    ) {
        // 1. Due Summary Banner + Dedicated COLLECT DUE & Manual Due Actions
        item {
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF7F1D1D)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "মোট কাস্টমার বকেয়া (TOTAL CUSTOMER DUE)",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color(0xFFFECACA)
                            )
                            Text(
                                text = formatTaka(totalOutstandingDue),
                                style = MaterialTheme.typography.displayMedium,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "$dueCustomersCount জন কাস্টমারের কাছে বাকি আছে",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFFEE2E2)
                            )
                        }

                        Button(
                            onClick = {
                                val target = customers.firstOrNull { it.currentDue > 0 } ?: customers.firstOrNull()
                                if (target != null) customerForCollectModal = target else showCreateCustomerModal = true
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White,
                                contentColor = Color(0xFF7F1D1D)
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.testTag("top_collect_due_button")
                        ) {
                            Icon(Icons.Default.Payments, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("বকেয়া আদায়", fontWeight = FontWeight.Bold)
                        }
                    }

                    HorizontalDivider(color = Color.White.copy(alpha = 0.2f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val target = customers.firstOrNull()
                                if (target != null) customerForManualAddModal = target else showCreateCustomerModal = true
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.5f)),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("manual_add_due_top_btn")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("ম্যানুয়াল বাকি যোগ", style = MaterialTheme.typography.labelMedium)
                        }

                        OutlinedButton(
                            onClick = {
                                val target = customers.firstOrNull { it.currentDue > 0 } ?: customers.firstOrNull()
                                if (target != null) customerForManualDeductModal = target else showCreateCustomerModal = true
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.5f)),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("manual_deduct_due_top_btn")
                        ) {
                            Icon(Icons.Default.Remove, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("বাকি কমানো/সমন্বয়", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }

        // 2. Search & Filter + Add Customer Button
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("কাস্টমারের নাম বা মোবাইল নাম্বার...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("due_customer_search_input")
                    )
                    FilledTonalIconButton(
                        onClick = { showCreateCustomerModal = true },
                        modifier = Modifier
                            .size(52.dp)
                            .testTag("add_new_customer_in_due_btn")
                    ) {
                        Icon(Icons.Default.PersonAdd, contentDescription = "Add Customer")
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = filterMode == "ALL",
                        onClick = { filterMode = "ALL" },
                        label = { Text("সকল কাস্টমার (${customers.size})") }
                    )
                    FilterChip(
                        selected = filterMode == "HAS_DUE",
                        onClick = { filterMode = "HAS_DUE" },
                        label = { Text("বাকি আছে ($dueCustomersCount)") }
                    )
                    FilterChip(
                        selected = filterMode == "CLEAR",
                        onClick = { filterMode = "CLEAR" },
                        label = { Text("পরিশোধিত (CLEAR)") }
                    )
                }
            }
        }

        // 3. Customer Due Cards
        if (filteredCustomers.isEmpty()) {
            item(key = "empty_customers_card") {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            Icons.Default.People,
                            contentDescription = null,
                            tint = EmeraldPrimary,
                            modifier = Modifier.size(38.dp)
                        )
                        Text(
                            text = if (customers.isEmpty()) "কোনো কাস্টমার এখনও যোগ করা হয়নি" else "এই ফিল্টারে কোনো কাস্টমার পাওয়া যায়নি",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        if (customers.isEmpty()) {
                            Button(
                                onClick = { showCreateCustomerModal = true },
                                modifier = Modifier.testTag("empty_due_add_customer_btn")
                            ) {
                                Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("+ প্রথম কাস্টমার যোগ করুন")
                            }
                        }
                    }
                }
            }
        }

        items(filteredCustomers, key = { it.id }) { customer ->
            val isClear = customer.currentDue <= 0.01
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { selectedCustomerForLedger = customer }
                    .testTag("customer_due_card_${customer.id}")
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = customer.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "📱 ${customer.phone} ${if (customer.address.isNotBlank()) "• ${customer.address}" else ""}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            if (isClear) {
                                Surface(
                                    color = IncomingGreenLight,
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = "CLEAR • ৳0",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = IncomingGreen,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            } else {
                                Text(
                                    text = formatTaka(customer.currentDue),
                                    style = MaterialTheme.typography.titleLarge,
                                    color = DueCrimson,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "বর্তমান বাকি",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = DueCrimson
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { customerForCollectModal = customer },
                            enabled = !isClear,
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier
                                .height(34.dp)
                                .weight(1.2f)
                                .testTag("collect_due_btn_${customer.id}")
                        ) {
                            Text("বকেয়া আদায়", style = MaterialTheme.typography.labelMedium)
                        }
                        OutlinedButton(
                            onClick = { customerForManualAddModal = customer },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier
                                .height(34.dp)
                                .weight(1f)
                        ) {
                            Text("+ বাকি যোগ", style = MaterialTheme.typography.labelMedium)
                        }
                        OutlinedButton(
                            onClick = { selectedCustomerForLedger = customer },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier
                                .height(34.dp)
                                .weight(1f)
                        ) {
                            Text("খাতা দেখুন", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CustomerDueLedgerDetailView(
    customer: CustomerEntity,
    entries: List<DueLedgerEntryEntity>,
    onBack: () -> Unit,
    onCollectClick: () -> Unit,
    onManualAddClick: () -> Unit,
    onManualDeductClick: () -> Unit
) {
    val isClear = customer.currentDue <= 0.01

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 96.dp)
    ) {
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
                Column {
                    Text(
                        text = "${customer.name} — বকেয়া খাতা (Due Ledger)",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "📱 ${customer.phone} ${if (customer.address.isNotBlank()) "• ${customer.address}" else ""}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isClear) EmeraldDark else Color(0xFF7F1D1D)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Current Due (বর্তমান মোট বাকি)",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White.copy(alpha = 0.85f)
                            )
                            Text(
                                text = formatTaka(customer.currentDue),
                                style = MaterialTheme.typography.displayMedium,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Surface(
                            color = Color.White.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                text = if (isClear) "STATUS: CLEAR" else "OUTSTANDING DUE",
                                style = MaterialTheme.typography.labelLarge,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onCollectClick,
                            enabled = !isClear,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White,
                                contentColor = Color(0xFF0F172A)
                            ),
                            modifier = Modifier.weight(1.2f)
                        ) {
                            Text("বকেয়া আদায়", fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            onClick = onManualAddClick,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.5f)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("+ বাকি যোগ")
                        }
                        OutlinedButton(
                            onClick = onManualDeductClick,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.5f)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("- কর্তন")
                        }
                    }
                }
            }
        }

        item {
            Text(
                text = "বকেয়া ও পরিশোধের সম্পূর্ণ ইতিহাস (${entries.size} টি রেকর্ড)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        if (entries.isEmpty()) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Box(modifier = Modifier.padding(24.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text("এই কাস্টমারের কোনো বকেয়া লেনদেন নেই।")
                    }
                }
            }
        } else {
            items(entries, key = { it.id }) { entry ->
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = entry.actionLabel,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = formatSignedTaka(entry.dueDelta),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (entry.dueDelta > 0) DueCrimson else IncomingGreen
                            )
                        }

                        Text(
                            text = "${formatDateTime(entry.timestamp)} ${if (entry.accountUsedName != null) "• ${entry.accountUsedName}" else ""}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "সার্ভিস: ${formatTaka(entry.serviceAmount)} • জমা: ${formatTaka(entry.paidAmount)}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = "মোট বাকি: ${formatTaka(entry.resultingDue)}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (entry.note.isNotBlank()) {
                            Text(
                                text = "নোট: ${entry.note}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CollectDueDialog(
    customer: CustomerEntity,
    allCustomers: List<CustomerEntity>,
    accounts: List<ShopAccountEntity>,
    onDismiss: () -> Unit,
    onConfirm: (customerId: String, amount: Double, receivingAccountId: String, note: String) -> Unit
) {
    var selectedCustomerId by remember { mutableStateOf(customer.id) }
    val activeCust = allCustomers.find { it.id == selectedCustomerId } ?: customer

    var amountInput by remember(activeCust.id) {
        mutableStateOf(if (activeCust.currentDue > 0) activeCust.currentDue.toLong().toString() else "")
    }
    var receivingAccountId by remember { mutableStateOf(ShopKhataRepository.CASH_ACCOUNT_ID) }
    var noteInput by remember { mutableStateOf("") }

    val amountVal = amountInput.toDoubleOrNull() ?: 0.0
    val resultingDue = activeCust.currentDue - amountVal

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "বকেয়া আদায় করুন (COLLECT DUE)",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Customer selector pills if multiple customers have due
                Text("কাস্টমার:", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    allCustomers.forEach { c ->
                        FilterChip(
                            selected = c.id == activeCust.id,
                            onClick = { selectedCustomerId = c.id },
                            label = { Text("${c.name} (${formatTaka(c.currentDue)})") }
                        )
                    }
                }

                Surface(
                    color = DueCrimsonLight,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Current Due (বর্তমান বাকি):", style = MaterialTheme.typography.bodySmall, color = Color(0xFF7F1D1D))
                            Text(
                                formatTaka(activeCust.currentDue),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = DueCrimson
                            )
                        }
                        TextButton(
                            onClick = { amountInput = activeCust.currentDue.toLong().toString() }
                        ) {
                            Text("সম্পূর্ণ পরিশোধ (Full Clear)")
                        }
                    }
                }

                OutlinedTextField(
                    value = amountInput,
                    onValueChange = { amountInput = it },
                    label = { Text("আদায়ের পরিমাণ (Amount to Collect ৳)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("collect_due_amount_input")
                )

                Text(
                    "কোন মাধ্যমে টাকা নিচ্ছেন? (Payment Method):",
                    style = MaterialTheme.typography.labelLarge
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    accounts.filter { it.isActive }.forEach { acc ->
                        FilterChip(
                            selected = acc.id == receivingAccountId,
                            onClick = { receivingAccountId = acc.id },
                            label = { Text("${acc.nickname} (${acc.phoneNumber})") }
                        )
                    }
                }

                val recAcc = accounts.find { it.id == receivingAccountId }
                if (recAcc != null && amountVal > 0) {
                    Surface(
                        color = EmeraldContainer,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                "• ${recAcc.nickname}: +৳${formatAmount(amountVal)} (নতুন: ${formatTaka(recAcc.currentBalance + amountVal)})",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = EmeraldDark
                            )
                            Text(
                                "• ${activeCust.name} বাকি: -৳${formatAmount(amountVal)} → অবশিষ্ট: ${formatTaka(resultingDue)} ${if (resultingDue <= 0.01) "(STATUS: CLEAR)" else ""}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = EmeraldDark
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = noteInput,
                    onValueChange = { noteInput = it },
                    label = { Text("নোট (Optional Note)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(activeCust.id, amountVal, receivingAccountId, noteInput) },
                enabled = amountVal > 0.0,
                modifier = Modifier.testTag("confirm_collect_due_button")
            ) {
                Text("আদায় নিশ্চিত করুন")
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
fun ManualAddDueDialog(
    customer: CustomerEntity,
    allCustomers: List<CustomerEntity>,
    onDismiss: () -> Unit,
    onConfirm: (customerId: String, amount: Double, reason: String) -> Unit
) {
    var selectedCustomerId by remember { mutableStateOf(customer.id) }
    val activeCust = allCustomers.find { it.id == selectedCustomerId } ?: customer
    var amountInput by remember { mutableStateOf("") }
    var reasonInput by remember { mutableStateOf("") }
    val amountVal = amountInput.toDoubleOrNull() ?: 0.0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("ম্যানুয়াল বাকি যোগ (Manually Add Due)", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    allCustomers.forEach { c ->
                        FilterChip(
                            selected = c.id == activeCust.id,
                            onClick = { selectedCustomerId = c.id },
                            label = { Text(c.name) }
                        )
                    }
                }
                Text("বর্তমান বাকি: ${formatTaka(activeCust.currentDue)}", fontWeight = FontWeight.SemiBold)
                OutlinedTextField(
                    value = amountInput,
                    onValueChange = { amountInput = it },
                    label = { Text("যোগ করার পরিমাণ (Amount ৳)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("manual_add_due_amount")
                )
                OutlinedTextField(
                    value = reasonInput,
                    onValueChange = { reasonInput = it },
                    label = { Text("কারণ (Reason)") },
                    placeholder = { Text("যেমন: পূর্বের খাতার বকেয়া") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (amountVal > 0) {
                    Text(
                        "নতুন মোট বাকি হবে: ${formatTaka(activeCust.currentDue + amountVal)}",
                        color = DueCrimson,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(activeCust.id, amountVal, reasonInput) },
                enabled = amountVal > 0.0,
                modifier = Modifier.testTag("confirm_manual_add_due_btn")
            ) {
                Text("বাকি যোগ করুন")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("বাতিল") }
        }
    )
}

@Composable
fun ManualDeductDueDialog(
    customer: CustomerEntity,
    allCustomers: List<CustomerEntity>,
    accounts: List<ShopAccountEntity>,
    onDismiss: () -> Unit,
    onConfirm: (customerId: String, amount: Double, isActualPayment: Boolean, receivingAccountId: String?, reason: String) -> Unit
) {
    var selectedCustomerId by remember { mutableStateOf(customer.id) }
    val activeCust = allCustomers.find { it.id == selectedCustomerId } ?: customer
    var amountInput by remember { mutableStateOf("") }
    var isActualPayment by remember { mutableStateOf(false) } // False = adjustment only, True = actual payment into cash/account
    var receivingAccountId by remember { mutableStateOf(ShopKhataRepository.CASH_ACCOUNT_ID) }
    var reasonInput by remember { mutableStateOf("") }
    val amountVal = amountInput.toDoubleOrNull() ?: 0.0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("ম্যানুয়াল বাকি কমানো (Deduct Due)", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    allCustomers.forEach { c ->
                        FilterChip(
                            selected = c.id == activeCust.id,
                            onClick = { selectedCustomerId = c.id },
                            label = { Text("${c.name} (${formatTaka(c.currentDue)})") }
                        )
                    }
                }

                OutlinedTextField(
                    value = amountInput,
                    onValueChange = { amountInput = it },
                    label = { Text("কমানোর পরিমাণ (Amount to Deduct ৳)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("manual_deduct_due_amount")
                )

                Text(
                    "এই কর্তনটি কিসের জন্য? (Choose Impact):",
                    style = MaterialTheme.typography.labelLarge
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !isActualPayment,
                        onClick = { isActualPayment = false },
                        label = { Text("শুধু হিসাব সমন্বয় (ক্যাশ অপরিবর্তিত)") }
                    )
                    FilterChip(
                        selected = isActualPayment,
                        onClick = { isActualPayment = true },
                        label = { Text("নগদ/অ্যাকাউন্টে প্রাপ্ত পেমেন্ট") }
                    )
                }

                if (isActualPayment) {
                    Text("কোন অ্যাকাউন্টে জমা হয়েছে?", style = MaterialTheme.typography.labelMedium)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        accounts.filter { it.isActive }.forEach { acc ->
                            FilterChip(
                                selected = acc.id == receivingAccountId,
                                onClick = { receivingAccountId = acc.id },
                                label = { Text(acc.nickname) }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = reasonInput,
                    onValueChange = { reasonInput = it },
                    label = { Text("কারণ (Reason)") },
                    placeholder = { Text("যেমন: হিসাব সমন্বয় / ছাড় / পূর্বের পেমেন্ট") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(
                        activeCust.id,
                        amountVal,
                        isActualPayment,
                        if (isActualPayment) receivingAccountId else null,
                        reasonInput
                    )
                },
                enabled = amountVal > 0.0,
                modifier = Modifier.testTag("confirm_manual_deduct_due_btn")
            ) {
                Text("নিশ্চিত করুন")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("বাতিল") }
        }
    )
}

@Composable
fun CustomerFormDialog(
    existing: CustomerEntity?,
    onDismiss: () -> Unit,
    onSave: (name: String, phone: String, address: String, notes: String, customField: String, openingDue: Double) -> Unit
) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var phone by remember { mutableStateOf(existing?.phone ?: "") }
    var address by remember { mutableStateOf(existing?.address ?: "") }
    var notes by remember { mutableStateOf(existing?.notes ?: "") }
    var customField by remember { mutableStateOf(existing?.customField ?: "") }
    var openingDueInput by remember { mutableStateOf(existing?.openingDue?.toLong()?.toString() ?: "0") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (existing == null) "নতুন কাস্টমার যোগ করুন (Add Customer)" else "কাস্টমারের তথ্য এডিট করুন",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("কাস্টমারের নাম (Customer Name) *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("মোবাইল নাম্বার (Phone Number) *") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("ঠিকানা (Address)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = openingDueInput,
                    onValueChange = { openingDueInput = it },
                    label = { Text("প্রারম্ভিক বাকি (Opening Due ৳)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = customField,
                    onValueChange = { customField = it },
                    label = { Text("অতিরিক্ত তথ্য / দোকান নং (Custom Info)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("নোট (Notes)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        name,
                        phone,
                        address,
                        notes,
                        customField,
                        openingDueInput.toDoubleOrNull() ?: 0.0
                    )
                },
                enabled = name.isNotBlank() && phone.isNotBlank()
            ) {
                Text("সংরক্ষণ করুন")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("বাতিল") }
        }
    )
}
