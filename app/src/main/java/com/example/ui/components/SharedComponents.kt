package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.*
import com.example.ui.theme.*
import kotlin.math.abs

fun parseHexColor(hex: String, fallback: Color = EmeraldPrimary): Color {
    return try {
        Color(android.graphics.Color.parseColor(hex))
    } catch (e: Exception) {
        fallback
    }
}

@Composable
fun ServiceBadge(
    serviceName: String,
    accountType: String,
    colorHex: String,
    modifier: Modifier = Modifier
) {
    val badgeColor = parseHexColor(colorHex)
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(badgeColor.copy(alpha = 0.14f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(badgeColor)
        )
        Text(
            text = "$serviceName • $accountType",
            style = MaterialTheme.typography.labelSmall,
            color = badgeColor,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun ReconcileBalanceDialog(
    account: ShopAccountEntity,
    onDismiss: () -> Unit,
    onConfirmReconcile: (actualBalance: Double, reason: String) -> Unit
) {
    var actualInput by remember { mutableStateOf(account.currentBalance.toLong().toString()) }
    var reason by remember { mutableStateOf("") }

    val actualVal = actualInput.toDoubleOrNull() ?: account.currentBalance
    val diff = actualVal - account.currentBalance

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = "ব্যালেন্স মিলকরণ (Reconcile Balance)",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${account.nickname} • ${account.phoneNumber}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Expected (হিসাব অনুযায়ী):",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = formatTaka(account.currentBalance),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                OutlinedTextField(
                    value = actualInput,
                    onValueChange = { actualInput = it },
                    label = { Text("Actual Balance (বর্তমান প্রকৃত ব্যালেন্স ৳)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("reconcile_actual_input")
                )

                Surface(
                    color = when {
                        diff > 0.001 -> IncomingGreenLight
                        diff < -0.001 -> DueCrimsonLight
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Difference (পার্থক্য / সমন্বয়):",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF0F172A)
                        )
                        Text(
                            text = formatSignedTaka(diff),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = when {
                                diff > 0.001 -> IncomingGreen
                                diff < -0.001 -> DueCrimson
                                else -> Color(0xFF0F172A)
                            }
                        )
                    }
                }

                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Reason (পার্থক্যের কারণ লিখুন)") },
                    placeholder = { Text("যেমন: ক্যাশ গণনায় গরমিল / পূর্বে এন্ট্রি বাদ পড়েছে") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("reconcile_reason_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirmReconcile(actualVal, reason) },
                enabled = abs(diff) > 0.001,
                modifier = Modifier.testTag("confirm_reconcile_button")
            ) {
                Text("সমন্বয় নিশ্চিত করুন")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("বাতিল")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionDetailAndEditDialog(
    transaction: ShopTransactionEntity,
    accounts: List<ShopAccountEntity>,
    rules: List<TransactionTypeRuleEntity>,
    customers: List<CustomerEntity>,
    auditLogs: List<AuditLogEntity>,
    onDismiss: () -> Unit,
    onSaveEdit: (
        newAccountId: String,
        newRuleId: String,
        newCustomerId: String?,
        newAmount: Double,
        newPaidAmount: Double,
        newPaymentMethodAccountId: String?,
        newSecondaryTransferAccountId: String?,
        newNote: String,
        editReason: String
    ) -> Unit,
    onReverse: (reason: String) -> Unit,
    onDeletePermanently: ((reason: String) -> Unit)? = null
) {
    var isEditing by remember { mutableStateOf(false) }
    var showReverseConfirm by remember { mutableStateOf(false) }
    var reverseReason by remember { mutableStateOf("") }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var deleteReason by remember { mutableStateOf("") }

    var selectedAccountId by remember { mutableStateOf(transaction.accountId) }
    var selectedRuleId by remember { mutableStateOf(transaction.transactionTypeId) }
    var selectedCustomerId by remember { mutableStateOf(transaction.customerId) }
    var amountInput by remember { mutableStateOf(transaction.amount.toLong().toString()) }
    var paidInput by remember { mutableStateOf(transaction.paidAmount.toLong().toString()) }
    var noteInput by remember { mutableStateOf(transaction.note) }
    var editReasonInput by remember { mutableStateOf("") }

    val txAuditLogs = remember(auditLogs, transaction.id) {
        auditLogs.filter { it.entityId == transaction.id }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .heightIn(max = 680.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isEditing) "লেনদেন সংশোধন (Edit Transaction)" else "লেনদেনের বিস্তারিত তথ্য",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${transaction.id} • ${formatDateTime(transaction.timestamp)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                if (transaction.isReversed) {
                    Surface(
                        color = DueCrimsonLight,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Undo, contentDescription = null, tint = DueCrimson)
                            Column {
                                Text(
                                    text = "এই লেনদেনটি রিভার্স (বাতিল) করা হয়েছে",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = DueCrimson,
                                    fontWeight = FontWeight.Bold
                                )
                                if (transaction.reversalReason.isNotBlank()) {
                                    Text(
                                        text = "কারণ: ${transaction.reversalReason}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFF7F1D1D)
                                    )
                                }
                            }
                        }
                    }
                }

                if (!isEditing) {
                    // Read-only comprehensive view
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("ধরন (Type):", style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    transaction.transactionTypeName,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("অ্যাকাউন্ট:", style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    "${transaction.accountName} (${transaction.accountPhone})",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            HorizontalDivider()
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("লেনদেনের পরিমাণ (Amount):", style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    formatTaka(transaction.amount),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("নগদ প্রাপ্ত/প্রদত্ত (Paid):", style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    formatTaka(transaction.paidAmount),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            if (transaction.dueAmount > 0.0) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("বাকি তৈরি (Due):", style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        formatTaka(transaction.dueAmount),
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = DueCrimson
                                    )
                                }
                            }
                            HorizontalDivider()
                            Text(
                                "ব্যালেন্স পরিবর্তন (Balance Impact):",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                "• ${transaction.accountName}: ${formatSignedTaka(transaction.primaryAccountDelta)} (ব্যালেন্স: ${formatTaka(transaction.primaryBalanceAfter)})",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            if (transaction.secondaryAccountName != null && abs(transaction.secondaryAccountDelta) > 0.001) {
                                Text(
                                    "• ${transaction.secondaryAccountName}: ${formatSignedTaka(transaction.secondaryAccountDelta)} (ব্যালেন্স: ${formatTaka(transaction.secondaryBalanceAfter ?: 0.0)})",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            if (transaction.customerName != null) {
                                Text(
                                    "• কাস্টমার: ${transaction.customerName} (${transaction.customerPhone ?: ""}) — বাকি পরিবর্তন: ${formatSignedTaka(transaction.customerDueDelta)}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            if (transaction.note.isNotBlank()) {
                                Text(
                                    "নোট: ${transaction.note}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Show Edit / Audit History for this transaction (#4 & #27)
                    if (txAuditLogs.isNotEmpty() || transaction.isEdited) {
                        Text(
                            text = "অডিট ও সংশোধন ইতিহাস (Edit & Audit History)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        txAuditLogs.forEach { log ->
                            Surface(
                                color = GoldLight.copy(alpha = 0.6f),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = "${log.title} • ${formatDateTime(log.timestamp)}",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF78350F)
                                    )
                                    Text(
                                        text = log.previousValueSummary,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFF78350F)
                                    )
                                    Text(
                                        text = log.newValueSummary,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF064E3B)
                                    )
                                    if (log.reason.isNotBlank()) {
                                        Text(
                                            text = "কারণ: ${log.reason}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color(0xFF475569)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (!transaction.isReversed) {
                        if (showReverseConfirm) {
                            Surface(
                                color = DueCrimsonLight,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        "আপনি কি নিশ্চিতভাবে এই লেনদেনটি রিভার্স (বাতিল) করতে চান?",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = DueCrimson
                                    )
                                    Text(
                                        "রিভার্স করলে পূর্বের সকল ব্যালেন্স স্বয়ংক্রিয়ভাবে আগের অবস্থায় ফিরে যাবে এবং অডিট ইতিহাসে রেকর্ড থাকবে।",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFF7F1D1D)
                                    )
                                    OutlinedTextField(
                                        value = reverseReason,
                                        onValueChange = { reverseReason = it },
                                        label = { Text("রিভার্স করার কারণ লিখুন") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        OutlinedButton(
                                            onClick = { showReverseConfirm = false },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text("না")
                                        }
                                        Button(
                                            onClick = { onReverse(reverseReason) },
                                            colors = ButtonDefaults.buttonColors(containerColor = DueCrimson),
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("confirm_reverse_tx_button")
                                        ) {
                                            Text("হ্যাঁ, রিভার্স করুন")
                                        }
                                    }
                                }
                            }
                        } else if (showDeleteConfirm && onDeletePermanently != null) {
                            Surface(
                                color = DueCrimsonLight,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        "স্থায়ীভাবে লেনদেন মুছে ফেলবেন? (Permanent Delete)",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = DueCrimson,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        "পরামর্শ: হিসাবের ধারাবাহিকতা রক্ষায় 'রিভার্স (Reverse)' করা উত্তম। স্থায়ীভাবে মুছে ফেললে ব্যালেন্স পুনঃগণনা হবে এবং অডিট লগে রেকর্ড থাকবে।",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFF7F1D1D)
                                    )
                                    OutlinedTextField(
                                        value = deleteReason,
                                        onValueChange = { deleteReason = it },
                                        label = { Text("মুছে ফেলার কারণ লিখুন") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        OutlinedButton(
                                            onClick = { showDeleteConfirm = false },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text("ফিরে যান")
                                        }
                                        Button(
                                            onClick = { onDeletePermanently(deleteReason) },
                                            colors = ButtonDefaults.buttonColors(containerColor = DueCrimson),
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("confirm_delete_tx_button")
                                        ) {
                                            Text("স্থায়ীভাবে মুছুন")
                                        }
                                    }
                                }
                            }
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = { showReverseConfirm = true },
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = DueCrimson),
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("reverse_tx_button")
                                    ) {
                                        Icon(Icons.Default.Undo, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("রিভার্স (Reverse)")
                                    }
                                    Button(
                                        onClick = { isEditing = true },
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("edit_tx_button")
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("এডিট (Edit)")
                                    }
                                }
                                if (onDeletePermanently != null) {
                                    TextButton(
                                        onClick = { showDeleteConfirm = true },
                                        modifier = Modifier
                                            .align(Alignment.End)
                                            .testTag("delete_tx_button")
                                    ) {
                                        Icon(Icons.Default.DeleteForever, contentDescription = null, tint = DueCrimson, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("স্থায়ীভাবে মুছুন (Delete with Audit)", color = DueCrimson)
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Edit Transaction Form
                    Text(
                        "অ্যাকাউন্ট নির্বাচন করুন:",
                        style = MaterialTheme.typography.labelLarge
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        accounts.forEach { acc ->
                            val selected = acc.id == selectedAccountId
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedAccountId = acc.id }
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "${acc.nickname} (${acc.phoneNumber})",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                    )
                                    if (selected) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }
                    }

                    if (rules.any { it.id == selectedRuleId }) {
                        Text(
                            "লেনদেনের ধরন (Transaction Type):",
                            style = MaterialTheme.typography.labelLarge
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            rules.forEach { r ->
                                FilterChip(
                                    selected = r.id == selectedRuleId,
                                    onClick = { selectedRuleId = r.id },
                                    label = { Text("${r.banglaName} (${r.name})") }
                                )
                            }
                        }
                    }

                    if (customers.isNotEmpty()) {
                        Text(
                            "কাস্টমার (Customer):",
                            style = MaterialTheme.typography.labelLarge
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            FilterChip(
                                selected = selectedCustomerId == null,
                                onClick = { selectedCustomerId = null },
                                label = { Text("সাধারণ / কাস্টমার ছাড়া") }
                            )
                            customers.forEach { c ->
                                FilterChip(
                                    selected = c.id == selectedCustomerId,
                                    onClick = { selectedCustomerId = c.id },
                                    label = { Text("${c.name} (${c.phone})") }
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = amountInput,
                        onValueChange = { amountInput = it },
                        label = { Text("নতুন পরিমাণ (New Amount ৳)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("edit_tx_amount_input")
                    )

                    OutlinedTextField(
                        value = paidInput,
                        onValueChange = { paidInput = it },
                        label = { Text("নগদ প্রাপ্ত/প্রদত্ত পরিমাণ (Paid Amount ৳)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("edit_tx_paid_input")
                    )

                    OutlinedTextField(
                        value = noteInput,
                        onValueChange = { noteInput = it },
                        label = { Text("নোট (Note)") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = editReasonInput,
                        onValueChange = { editReasonInput = it },
                        label = { Text("সংশোধনের কারণ (Edit Reason for Audit Log)") },
                        placeholder = { Text("যেমন: টাকার অংক ভুল লেখা হয়েছিল") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { isEditing = false },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("বাতিল")
                        }
                        Button(
                            onClick = {
                                val amt = amountInput.toDoubleOrNull() ?: transaction.amount
                                val pd = paidInput.toDoubleOrNull() ?: amt
                                onSaveEdit(
                                    selectedAccountId,
                                    selectedRuleId,
                                    selectedCustomerId,
                                    amt,
                                    pd,
                                    transaction.paymentMethodAccountId,
                                    transaction.secondaryAccountId,
                                    noteInput,
                                    editReasonInput
                                )
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("save_edited_tx_button")
                        ) {
                            Text("আপডেট ও হিসাব মেলান")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GlobalSearchDialog(
    query: String,
    onQueryChange: (String) -> Unit,
    accounts: List<ShopAccountEntity>,
    customers: List<CustomerEntity>,
    transactions: List<ShopTransactionEntity>,
    onSelectAccount: (ShopAccountEntity) -> Unit,
    onSelectCustomer: (CustomerEntity) -> Unit,
    onSelectTransaction: (ShopTransactionEntity) -> Unit,
    onDismiss: () -> Unit
) {
    val q = query.trim().lowercase()

    val matchingAccounts = remember(q, accounts) {
        if (q.isEmpty()) emptyList()
        else accounts.filter {
            it.nickname.lowercase().contains(q) ||
                it.phoneNumber.lowercase().contains(q) ||
                it.serviceName.lowercase().contains(q) ||
                it.accountType.lowercase().contains(q)
        }
    }

    val matchingCustomers = remember(q, customers) {
        if (q.isEmpty()) emptyList()
        else customers.filter {
            it.name.lowercase().contains(q) ||
                it.phone.lowercase().contains(q) ||
                it.address.lowercase().contains(q) ||
                it.notes.lowercase().contains(q)
        }
    }

    val matchingTransactions = remember(q, transactions) {
        if (q.isEmpty()) emptyList()
        else transactions.filter {
            it.id.lowercase().contains(q) ||
                it.accountName.lowercase().contains(q) ||
                it.accountPhone.lowercase().contains(q) ||
                (it.customerName?.lowercase()?.contains(q) == true) ||
                (it.customerPhone?.lowercase()?.contains(q) == true) ||
                it.transactionTypeName.lowercase().contains(q) ||
                it.note.lowercase().contains(q) ||
                it.amount.toLong().toString().contains(q)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        placeholder = { Text("খুঁজুন: ফোন (017...), কাস্টমার, অ্যাকাউন্ট, TXN ID, টাকা...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { onQueryChange("") }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear")
                                }
                            }
                        },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("global_search_input")
                    )
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = onDismiss) {
                        Text("বন্ধ")
                    }
                }

                Spacer(Modifier.height(12.dp))

                if (q.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.ManageSearch,
                                contentDescription = null,
                                modifier = Modifier.size(56.dp),
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "মোবাইল নাম্বার, কাস্টমারের নাম, অ্যাকাউন্ট, TXN ID বা টাকার পরিমাণ লিখুন",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        if (matchingAccounts.isNotEmpty()) {
                            item {
                                Text(
                                    "MFS ও রিচার্জ অ্যাকাউন্ট (${matchingAccounts.size})",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            items(matchingAccounts, key = { "acc_${it.id}" }) { acc ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            onSelectAccount(acc)
                                            onDismiss()
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp).fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(acc.nickname, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                            Text("${acc.phoneNumber} • ${acc.accountType}", style = MaterialTheme.typography.bodySmall)
                                        }
                                        Text(formatTaka(acc.currentBalance), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        if (matchingCustomers.isNotEmpty()) {
                            item {
                                Text(
                                    "কাস্টমার ও বকেয়া (${matchingCustomers.size})",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            items(matchingCustomers, key = { "cust_${it.id}" }) { cust ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            onSelectCustomer(cust)
                                            onDismiss()
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp).fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(cust.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                            Text(cust.phone, style = MaterialTheme.typography.bodySmall)
                                        }
                                        Text(
                                            text = if (cust.currentDue > 0) "বাকি: ${formatTaka(cust.currentDue)}" else "CLEAR (৳0)",
                                            color = if (cust.currentDue > 0) DueCrimson else IncomingGreen,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }

                        if (matchingTransactions.isNotEmpty()) {
                            item {
                                Text(
                                    "লেনদেন সমূহ (${matchingTransactions.size})",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            items(matchingTransactions, key = { "tx_${it.id}" }) { tx ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            onSelectTransaction(tx)
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp).fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                "${tx.accountName} • ${tx.transactionTypeName}",
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                "${tx.id} • ${tx.accountPhone} ${if (tx.customerName != null) "• ${tx.customerName}" else ""}",
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                        Text(
                                            formatTaka(tx.amount),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
