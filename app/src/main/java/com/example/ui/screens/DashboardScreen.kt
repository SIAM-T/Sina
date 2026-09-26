package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.*
import com.example.ui.MainTab
import com.example.ui.MoreSubScreen
import com.example.ui.components.ReconcileBalanceDialog
import com.example.ui.components.ServiceBadge
import com.example.ui.components.parseHexColor
import com.example.ui.getStartOfTodayMillis
import com.example.ui.theme.*
import kotlin.math.abs

@Composable
fun DashboardScreen(
    settings: ShopSettingsEntity,
    accounts: List<ShopAccountEntity>,
    customers: List<CustomerEntity>,
    transactions: List<ShopTransactionEntity>,
    onQuickNewTransaction: (accountId: String?, ruleId: String?) -> Unit,
    onOpenTab: (MainTab) -> Unit,
    onOpenMoreSubScreen: (MoreSubScreen) -> Unit,
    onOpenAccountStatement: (String) -> Unit,
    onReconcileAccount: (accountId: String, actual: Double, reason: String) -> Unit,
    onToggleShowAllAccounts: (Boolean) -> Unit,
    onToggleGroupingMode: (String) -> Unit
) {
    val cashAccount = remember(accounts) {
        accounts.find { it.id == ShopKhataRepository.CASH_ACCOUNT_ID }
    }
    val digitalAccounts = remember(accounts, settings) {
        accounts.filter { acc ->
            acc.id != ShopKhataRepository.CASH_ACCOUNT_ID &&
                (!settings.hideInactiveAccounts || acc.isActive) &&
                (settings.showAllAccountsOnDashboard || acc.isVisibleOnDashboard)
        }
    }

    val totalCustomerDue = remember(customers) {
        customers.sumOf { it.currentDue }
    }

    val startOfToday = remember { getStartOfTodayMillis() }
    val todayActiveTxs = remember(transactions, startOfToday) {
        transactions.filter { !it.isReversed && it.timestamp >= startOfToday }
    }
    val todayIncoming = remember(todayActiveTxs) {
        todayActiveTxs.sumOf { tx ->
            val posPrim = if (tx.primaryAccountDelta > 0) tx.primaryAccountDelta else 0.0
            val posSec = if (tx.secondaryAccountDelta > 0) tx.secondaryAccountDelta else 0.0
            posPrim + posSec
        }
    }
    val todayOutgoing = remember(todayActiveTxs) {
        todayActiveTxs.sumOf { tx ->
            val negPrim = if (tx.primaryAccountDelta < 0) abs(tx.primaryAccountDelta) else 0.0
            val negSec = if (tx.secondaryAccountDelta < 0) abs(tx.secondaryAccountDelta) else 0.0
            negPrim + negSec
        }
    }

    var accountToReconcile by remember { mutableStateOf<ShopAccountEntity?>(null) }
    val collapsedGroups = remember { mutableStateMapOf<String, Boolean>() }

    if (accountToReconcile != null) {
        ReconcileBalanceDialog(
            account = accountToReconcile!!,
            onDismiss = { accountToReconcile = null },
            onConfirmReconcile = { actual, reason ->
                onReconcileAccount(accountToReconcile!!.id, actual, reason)
                accountToReconcile = null
            }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 96.dp)
    ) {
        // 1. CASH IN HAND + CUSTOMER DUE Hero Card
        item {
            Card(
                shape = RoundedCornerShape(24.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("dashboard_cash_hero_card")
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(EmeraldDark, EmeraldPrimary, Color(0xFF047857))
                            )
                        )
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.img_shop_banner),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        alpha = 0.14f,
                        modifier = Modifier
                            .matchParentSize()
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Column {
                                Text(
                                    text = "CASH IN HAND • হাতে নগদ ক্যাশ",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = EmeraldLight
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = formatTaka(cashAccount?.currentBalance ?: 0.0),
                                    style = MaterialTheme.typography.displayLarge,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                AssistChip(
                                    onClick = { if (cashAccount != null) accountToReconcile = cashAccount },
                                    label = { Text("মিলকরণ", color = Color.White) },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.Balance,
                                            contentDescription = "Reconcile Cash",
                                            tint = GoldLight,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    },
                                    colors = AssistChipDefaults.assistChipColors(
                                        containerColor = Color.White.copy(alpha = 0.16f)
                                    ),
                                    border = null,
                                    modifier = Modifier.testTag("cash_reconcile_chip")
                                )
                                AssistChip(
                                    onClick = { if (cashAccount != null) onOpenAccountStatement(cashAccount.id) },
                                    label = { Text("বিবরণী", color = Color.White) },
                                    colors = AssistChipDefaults.assistChipColors(
                                        containerColor = Color.White.copy(alpha = 0.16f)
                                    ),
                                    border = null
                                )
                            }
                        }

                        HorizontalDivider(color = Color.White.copy(alpha = 0.18f))

                        // Customer Due + Today's Volume Row (Strictly NO profit/commission!)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.Black.copy(alpha = 0.22f))
                                    .clickable { onOpenTab(MainTab.DUE) }
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = "CUSTOMER DUE (মোট বাকি)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = GoldLight
                                )
                                Text(
                                    text = formatTaka(totalCustomerDue),
                                    style = MaterialTheme.typography.titleLarge,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "আজকের লেনদেন (${todayActiveTxs.size} টি)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = EmeraldLight
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text(
                                        text = "জমা: +৳${formatAmount(todayIncoming)}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = Color(0xFFA7F3D0),
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "প্রদান: -৳${formatAmount(todayOutgoing)}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = Color(0xFFFED7AA),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 2. Primary Fast Action CTA (+ NEW TRANSACTION &Account Transfer)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = { onQuickNewTransaction(null, null) },
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                    contentPadding = PaddingValues(vertical = 14.dp, horizontal = 16.dp),
                    modifier = Modifier
                        .weight(1.4f)
                        .testTag("dashboard_new_tx_cta")
                ) {
                    Icon(Icons.Default.AddCircle, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "+ নতুন লেনদেন (New Entry)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                FilledTonalButton(
                    onClick = { onQuickNewTransaction(null, "rule_transfer") },
                    shape = RoundedCornerShape(16.dp),
                    contentPadding = PaddingValues(vertical = 14.dp, horizontal = 12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("dashboard_transfer_cta")
                ) {
                    Icon(Icons.Default.SwapHoriz, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "ট্রান্সফার",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // 3. Dashboard 6 Quick Actions Grid (#33)
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        QuickActionTile(
                            icon = Icons.Default.AddCard,
                            title = "+ লেনদেন",
                            subtitle = "Transaction",
                            tint = EmeraldPrimary,
                            onClick = { onQuickNewTransaction(null, null) },
                            modifier = Modifier.weight(1f).testTag("quick_action_transaction")
                        )
                        QuickActionTile(
                            icon = Icons.Default.People,
                            title = "কাস্টমার",
                            subtitle = "Customers",
                            tint = TransferBlue,
                            onClick = { onOpenMoreSubScreen(MoreSubScreen.CUSTOMERS) },
                            modifier = Modifier.weight(1f).testTag("quick_action_customers")
                        )
                        QuickActionTile(
                            icon = Icons.Default.AccountBalanceWallet,
                            title = "বাকি আদায়",
                            subtitle = "Due (${formatTaka(totalCustomerDue)})",
                            tint = DueCrimson,
                            onClick = { onOpenTab(MainTab.DUE) },
                            modifier = Modifier.weight(1f).testTag("quick_action_due")
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        QuickActionTile(
                            icon = Icons.Default.Payments,
                            title = "ক্যাশ খাতা",
                            subtitle = "Cash Ledger",
                            tint = CashEmerald,
                            onClick = {
                                if (cashAccount != null) onOpenAccountStatement(cashAccount.id)
                            },
                            modifier = Modifier.weight(1f).testTag("quick_action_cash")
                        )
                        QuickActionTile(
                            icon = Icons.Default.PhoneAndroid,
                            title = "অ্যাকাউন্ট",
                            subtitle = "Accounts (${digitalAccounts.size})",
                            tint = BkashPink,
                            onClick = { onOpenMoreSubScreen(MoreSubScreen.ACCOUNTS) },
                            modifier = Modifier.weight(1f).testTag("quick_action_accounts")
                        )
                        QuickActionTile(
                            icon = Icons.Default.Assessment,
                            title = "রিপোর্ট",
                            subtitle = "Reports",
                            tint = GoldAccent,
                            onClick = { onOpenMoreSubScreen(MoreSubScreen.REPORTS) },
                            modifier = Modifier.weight(1f).testTag("quick_action_reports")
                        )
                    }
                }
            }
        }

        // 4. MFS & Recharge Accounts Section Header + Grouping & Visibility Controls (#1, #5, #6, #34)
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "MFS ও রিচার্জ ব্যালেন্স (DIGITAL BALANCES)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "একই নাম্বারে একাধিক সার্ভিস থাকলেও প্রতিটি হিসাব সম্পূর্ণ আলাদা",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(onClick = { onOpenMoreSubScreen(MoreSubScreen.ACCOUNTS) }) {
                        Text("+ অ্যাকাউন্ট")
                    }
                }

                // Grouping Mode & Visibility Filter Pills
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = settings.groupAccountsBy == "SERVICE",
                        onClick = { onToggleGroupingMode("SERVICE") },
                        label = { Text("সার্ভিস অনুযায়ী (bKash/Nagad)") },
                        leadingIcon = {
                            if (settings.groupAccountsBy == "SERVICE") {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                        },
                        modifier = Modifier.testTag("group_by_service_chip")
                    )
                    FilterChip(
                        selected = settings.groupAccountsBy == "PHONE",
                        onClick = { onToggleGroupingMode("PHONE") },
                        label = { Text("সিম/ফোন নাম্বার অনুযায়ী") },
                        leadingIcon = {
                            if (settings.groupAccountsBy == "PHONE") {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                        },
                        modifier = Modifier.testTag("group_by_phone_chip")
                    )
                    Spacer(Modifier.weight(1f))
                    TextButton(
                        onClick = { onToggleShowAllAccounts(!settings.showAllAccountsOnDashboard) }
                    ) {
                        Text(
                            text = if (settings.showAllAccountsOnDashboard) "সব দৃশ্যমান" else "নির্বাচিত",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }
        }

        // 5. Intelligent Expandable Account Groups (#6)
        if (digitalAccounts.isEmpty()) {
            item(key = "empty_digital_accounts") {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            Icons.Default.AccountBalanceWallet,
                            contentDescription = null,
                            tint = EmeraldPrimary,
                            modifier = Modifier.size(40.dp)
                        )
                        Text(
                            text = "কোনো MFS বা রিচার্জ অ্যাকাউন্ট এখনও যোগ করা হয়নি",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "আপনার বিকাশ, নগদ, রকেট বা রিচার্জ সিম নাম্বার ও ওপেনিং ব্যালেন্স দিয়ে অ্যাকাউন্ট যোগ করুন। একই নাম্বারে একাধিক সার্ভিস আলাদাভাবে যোগ করা যাবে।",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = { onOpenMoreSubScreen(MoreSubScreen.ACCOUNTS) },
                            modifier = Modifier.testTag("dashboard_add_first_account_btn")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("+ প্রথম অ্যাকাউন্ট যোগ করুন (Add Account)")
                        }
                    }
                }
            }
        }

        val groupedAccounts: Map<String, List<ShopAccountEntity>> = if (settings.groupAccountsBy == "PHONE") {
            digitalAccounts.groupBy { "সিম নাম্বার: ${it.phoneNumber}" }
        } else {
            digitalAccounts.groupBy {
                when (it.category) {
                    "RECHARGE" -> "Recharge (মোবাইল রিচার্জ ওয়ালেট)"
                    else -> it.serviceName
                }
            }
        }

        groupedAccounts.forEach { (groupTitle, groupItems) ->
            item(key = "group_$groupTitle") {
                val isCollapsed = collapsedGroups[groupTitle] == true
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // Group Header (Expand / Collapse)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { collapsedGroups[groupTitle] = !isCollapsed }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(parseHexColor(groupItems.firstOrNull()?.colorHex ?: "#065F46"))
                                )
                                Text(
                                    text = "$groupTitle (${groupItems.size} টি অ্যাকাউন্ট)",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Icon(
                                imageVector = if (isCollapsed) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
                                contentDescription = if (isCollapsed) "Expand" else "Collapse"
                            )
                        }

                        AnimatedVisibility(visible = !isCollapsed) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp)
                                    .padding(bottom = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                groupItems.forEach { acc ->
                                    IndividualAccountDashboardRow(
                                        account = acc,
                                        onNewTransaction = { onQuickNewTransaction(acc.id, null) },
                                        onOpenStatement = { onOpenAccountStatement(acc.id) },
                                        onReconcile = { accountToReconcile = acc }
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

@Composable
private fun QuickActionTile(
    icon: ImageVector,
    title: String,
    subtitle: String,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = tint.copy(alpha = 0.09f),
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(tint.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = title, tint = tint, modifier = Modifier.size(18.dp))
            }
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun IndividualAccountDashboardRow(
    account: ShopAccountEntity,
    onNewTransaction: () -> Unit,
    onOpenStatement: () -> Unit,
    onReconcile: () -> Unit
) {
    val accent = parseHexColor(account.colorHex)
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenStatement() }
            .testTag("dashboard_account_${account.id}")
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
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
                        Text(
                            text = account.nickname,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        ServiceBadge(
                            serviceName = account.serviceName,
                            accountType = account.accountType,
                            colorHex = account.colorHex
                        )
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "📱 ${account.phoneNumber}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = formatTaka(account.currentBalance),
                        style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp),
                        fontWeight = FontWeight.Bold,
                        color = accent
                    )
                    Text(
                        text = "স্বতন্ত্র ব্যালেন্স",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(
                    onClick = onNewTransaction,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier
                        .height(34.dp)
                        .weight(1.2f)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("লেনদেন", style = MaterialTheme.typography.labelMedium)
                }
                OutlinedButton(
                    onClick = onOpenStatement,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier
                        .height(34.dp)
                        .weight(1f)
                ) {
                    Icon(Icons.Default.ReceiptLong, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("স্টেটমেন্ট", style = MaterialTheme.typography.labelMedium)
                }
                OutlinedButton(
                    onClick = onReconcile,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier
                        .height(34.dp)
                        .weight(1f)
                ) {
                    Icon(Icons.Default.Balance, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("মিলকরণ", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}
