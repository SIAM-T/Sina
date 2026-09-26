package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.*
import com.example.ui.components.ReconcileBalanceDialog
import com.example.ui.components.TransactionDetailAndEditDialog
import com.example.ui.components.parseHexColor
import com.example.ui.theme.*
import kotlin.math.abs

@Composable
fun TransactionHistoryScreen(
    transactions: List<ShopTransactionEntity>,
    accounts: List<ShopAccountEntity>,
    rules: List<TransactionTypeRuleEntity>,
    customers: List<CustomerEntity>,
    auditLogs: List<AuditLogEntity>,
    onEditTransaction: (
        txId: String,
        newAccId: String,
        newRuleId: String,
        newCustId: String?,
        newAmount: Double,
        newPaid: Double,
        newPayAccId: String?,
        newSecAccId: String?,
        newNote: String,
        editReason: String
    ) -> Unit,
    onReverseTransaction: (txId: String, reason: String) -> Unit,
    onDeleteTransaction: ((txId: String, reason: String) -> Unit)? = null
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedAccountFilter by remember { mutableStateOf("ALL") }
    var statusFilter by remember { mutableStateOf("ALL") } // "ALL", "DUE", "EDITED", "REVERSED"
    var selectedTransaction by remember { mutableStateOf<ShopTransactionEntity?>(null) }

    val filteredTransactions = remember(transactions, searchQuery, selectedAccountFilter, statusFilter) {
        val q = searchQuery.trim().lowercase()
        transactions.filter { tx ->
            val matchesAcc = selectedAccountFilter == "ALL" ||
                tx.accountId == selectedAccountFilter ||
                tx.secondaryAccountId == selectedAccountFilter

            val matchesStatus = when (statusFilter) {
                "DUE" -> tx.dueAmount > 0.01 && !tx.isReversed
                "EDITED" -> tx.isEdited
                "REVERSED" -> tx.isReversed
                else -> true
            }

            val matchesQuery = q.isEmpty() ||
                tx.id.lowercase().contains(q) ||
                tx.accountName.lowercase().contains(q) ||
                tx.accountPhone.lowercase().contains(q) ||
                (tx.customerName?.lowercase()?.contains(q) == true) ||
                (tx.customerPhone?.lowercase()?.contains(q) == true) ||
                tx.transactionTypeName.lowercase().contains(q) ||
                tx.note.lowercase().contains(q) ||
                tx.amount.toLong().toString().contains(q)

            matchesAcc && matchesStatus && matchesQuery
        }
    }

    val liveSelectedTx = remember(selectedTransaction, transactions) {
        selectedTransaction?.let { sel -> transactions.find { it.id == sel.id } }
    }

    if (liveSelectedTx != null) {
        TransactionDetailAndEditDialog(
            transaction = liveSelectedTx,
            accounts = accounts,
            rules = rules,
            customers = customers,
            auditLogs = auditLogs,
            onDismiss = { selectedTransaction = null },
            onSaveEdit = { accId, ruleId, custId, amt, paid, payAccId, secAccId, note, reason ->
                onEditTransaction(liveSelectedTx.id, accId, ruleId, custId, amt, paid, payAccId, secAccId, note, reason)
                selectedTransaction = null
            },
            onReverse = { reason ->
                onReverseTransaction(liveSelectedTx.id, reason)
                selectedTransaction = null
            },
            onDeletePermanently = if (onDeleteTransaction != null) {
                { reason ->
                    onDeleteTransaction(liveSelectedTx.id, reason)
                    selectedTransaction = null
                }
            } else null
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 96.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("খুঁজুন: ফোন (017...), কাস্টমার, TXN ID, টাকা, নোট...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("history_search_input")
                )

                // Account Filter Chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = selectedAccountFilter == "ALL",
                        onClick = { selectedAccountFilter = "ALL" },
                        label = { Text("সকল অ্যাকাউন্ট") }
                    )
                    accounts.forEach { acc ->
                        FilterChip(
                            selected = selectedAccountFilter == acc.id,
                            onClick = { selectedAccountFilter = acc.id },
                            label = { Text("${acc.nickname} (${acc.phoneNumber})") }
                        )
                    }
                }

                // Status Filter Chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        "ALL" to "সব লেনদেন (${transactions.size})",
                        "DUE" to "বাকি লেনদেন",
                        "EDITED" to "সংশোধিত (Edited)",
                        "REVERSED" to "রিভার্সড (Reversed)"
                    ).forEach { (key, label) ->
                        FilterChip(
                            selected = statusFilter == key,
                            onClick = { statusFilter = key },
                            label = { Text(label) }
                        )
                    }
                }
            }
        }

        if (filteredTransactions.isEmpty()) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("কোনো লেনদেন পাওয়া যায়নি।")
                    }
                }
            }
        } else {
            items(filteredTransactions, key = { it.id }) { tx ->
                TransactionHistoryCard(
                    tx = tx,
                    onClick = { selectedTransaction = tx }
                )
            }
        }
    }
}

@Composable
fun TransactionHistoryCard(
    tx: ShopTransactionEntity,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (tx.isReversed) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("tx_card_${tx.id}")
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
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = tx.accountName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            textDecoration = if (tx.isReversed) TextDecoration.LineThrough else null
                        )
                        if (tx.isEdited) {
                            Surface(
                                color = GoldLight,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "EDITED",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF78350F),
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        if (tx.isReversed) {
                            Surface(
                                color = DueCrimsonLight,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "REVERSED",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = DueCrimson,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Text(
                        text = "${tx.transactionTypeName} • 📱 ${tx.accountPhone}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = formatTaka(tx.amount),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (tx.isReversed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = formatDateTime(tx.timestamp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))

            // Exact balance movement chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "${tx.accountName}: ${formatSignedTaka(tx.primaryAccountDelta)}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (tx.primaryAccountDelta >= 0) IncomingGreen else OutgoingOrange
                    )
                    if (tx.secondaryAccountName != null && abs(tx.secondaryAccountDelta) > 0.001) {
                        Text(
                            text = "• ${tx.secondaryAccountName}: ${formatSignedTaka(tx.secondaryAccountDelta)}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (tx.secondaryAccountDelta >= 0) IncomingGreen else OutgoingOrange,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (tx.dueAmount > 0.01 && !tx.isReversed) {
                    Text(
                        text = "বাকি: ${formatTaka(tx.dueAmount)}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = DueCrimson
                    )
                }
            }

            if (tx.customerName != null || tx.note.isNotBlank()) {
                Text(
                    text = buildString {
                        if (tx.customerName != null) append("👤 ${tx.customerName} (${tx.customerPhone ?: ""})  ")
                        if (tx.note.isNotBlank()) append("📝 ${tx.note}")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Dedicated Account Statement Screen (#31):
 * Shows Opening Balance, +Incoming, -Outgoing, ±Adjustments, Current Balance,
 * and every transaction affecting that account (clickable to open/edit/reverse).
 */
@Composable
fun AccountStatementScreen(
    account: ShopAccountEntity,
    allAccounts: List<ShopAccountEntity>,
    transactions: List<ShopTransactionEntity>,
    rules: List<TransactionTypeRuleEntity>,
    customers: List<CustomerEntity>,
    auditLogs: List<AuditLogEntity>,
    onBack: () -> Unit,
    onNewTransactionForAccount: (String) -> Unit,
    onReconcileAccount: (actual: Double, reason: String) -> Unit,
    onEditTransaction: (
        txId: String,
        newAccId: String,
        newRuleId: String,
        newCustId: String?,
        newAmount: Double,
        newPaid: Double,
        newPayAccId: String?,
        newSecAccId: String?,
        newNote: String,
        editReason: String
    ) -> Unit,
    onReverseTransaction: (txId: String, reason: String) -> Unit,
    onDeleteTransaction: ((txId: String, reason: String) -> Unit)? = null
) {
    var selectedTx by remember { mutableStateOf<ShopTransactionEntity?>(null) }
    var showReconcile by remember { mutableStateOf(false) }

    val accountTransactions = remember(transactions, account.id) {
        transactions.filter {
            it.accountId == account.id || (it.secondaryAccountId == account.id && abs(it.secondaryAccountDelta) > 0.001)
        }
    }

    val activeAccountTxs = remember(accountTransactions) {
        accountTransactions.filter { !it.isReversed }
    }

    val totalIncoming = remember(activeAccountTxs, account.id) {
        activeAccountTxs.sumOf { tx ->
            val delta = if (tx.accountId == account.id) tx.primaryAccountDelta else tx.secondaryAccountDelta
            if (delta > 0 && tx.transactionTypeId != "sys_reconcile") delta else 0.0
        }
    }

    val totalOutgoing = remember(activeAccountTxs, account.id) {
        activeAccountTxs.sumOf { tx ->
            val delta = if (tx.accountId == account.id) tx.primaryAccountDelta else tx.secondaryAccountDelta
            if (delta < 0 && tx.transactionTypeId != "sys_reconcile") abs(delta) else 0.0
        }
    }

    val totalAdjustments = remember(activeAccountTxs, account.id) {
        activeAccountTxs.sumOf { tx ->
            if (tx.transactionTypeId == "sys_reconcile" && tx.accountId == account.id) tx.primaryAccountDelta else 0.0
        }
    }

    if (showReconcile) {
        ReconcileBalanceDialog(
            account = account,
            onDismiss = { showReconcile = false },
            onConfirmReconcile = { actual, reason ->
                onReconcileAccount(actual, reason)
                showReconcile = false
            }
        )
    }

    if (selectedTx != null) {
        TransactionDetailAndEditDialog(
            transaction = selectedTx!!,
            accounts = allAccounts,
            rules = rules,
            customers = customers,
            auditLogs = auditLogs,
            onDismiss = { selectedTx = null },
            onSaveEdit = { accId, ruleId, custId, amt, paid, payAccId, secAccId, note, reason ->
                onEditTransaction(selectedTx!!.id, accId, ruleId, custId, amt, paid, payAccId, secAccId, note, reason)
                selectedTx = null
            },
            onReverse = { reason ->
                onReverseTransaction(selectedTx!!.id, reason)
                selectedTx = null
            },
            onDeletePermanently = if (onDeleteTransaction != null) {
                { reason ->
                    onDeleteTransaction(selectedTx!!.id, reason)
                    selectedTx = null
                }
            } else null
        )
    }

    val accent = parseHexColor(account.colorHex)

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
                        text = "${account.nickname} — স্টেটমেন্ট (Statement)",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "📱 ${account.phoneNumber} • ${account.serviceName} (${account.accountType})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Deterministic Balance Equation Card (#31 & #32)
        item {
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = accent),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column {
                            Text(
                                text = "Current Balance (বর্তমান ব্যালেন্স)",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White.copy(alpha = 0.85f)
                            )
                            Text(
                                text = formatTaka(account.currentBalance),
                                style = MaterialTheme.typography.displayMedium,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "Opening Balance (শুরু):",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                            Text(
                                text = formatTaka(account.openingBalance),
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    HorizontalDivider(color = Color.White.copy(alpha = 0.2f))

                    // Formula Breakdown: Opening + Incoming - Outgoing ± Adjustments = Current Balance
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("+ Incoming (জমা)", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.8f))
                            Text("+৳${formatAmount(totalIncoming)}", style = MaterialTheme.typography.titleSmall, color = Color.White, fontWeight = FontWeight.Bold)
                        }
                        Column {
                            Text("- Outgoing (প্রদান)", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.8f))
                            Text("-৳${formatAmount(totalOutgoing)}", style = MaterialTheme.typography.titleSmall, color = Color.White, fontWeight = FontWeight.Bold)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("± Adjustments", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.8f))
                            Text(formatSignedTaka(totalAdjustments), style = MaterialTheme.typography.titleSmall, color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { onNewTransactionForAccount(account.id) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = accent),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("নতুন লেনদেন", fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            onClick = { showReconcile = true },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.6f)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Balance, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("ব্যালেন্স মিলকরণ")
                        }
                    }
                }
            }
        }

        item {
            Text(
                text = "লেনদেন বিবরণী (${accountTransactions.size} টি এন্ট্রি — ক্লিক করে বিস্তারিত/এডিট করুন)",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
        }

        items(accountTransactions, key = { it.id }) { tx ->
            val isPrimary = tx.accountId == account.id
            val deltaForThisAcc = if (isPrimary) tx.primaryAccountDelta else tx.secondaryAccountDelta
            val balanceAfterForThisAcc = if (isPrimary) tx.primaryBalanceAfter else (tx.secondaryBalanceAfter ?: account.currentBalance)

            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { selectedTx = tx }
            ) {
                Row(
                    modifier = Modifier.padding(14.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = tx.transactionTypeName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            textDecoration = if (tx.isReversed) TextDecoration.LineThrough else null
                        )
                        Text(
                            text = "${formatDateTime(tx.timestamp)} • ${tx.id}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (tx.customerName != null || tx.note.isNotBlank()) {
                            Text(
                                text = "${tx.customerName ?: ""} ${tx.note}".trim(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = formatSignedTaka(deltaForThisAcc),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = when {
                                tx.isReversed -> MaterialTheme.colorScheme.onSurfaceVariant
                                deltaForThisAcc >= 0 -> IncomingGreen
                                else -> OutgoingOrange
                            }
                        )
                        Text(
                            text = "ব্যালেন্স: ${formatTaka(balanceAfterForThisAcc)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
