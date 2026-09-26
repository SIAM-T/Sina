package com.example.data

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

enum class CloudConnectionState {
    CONFIG_MISSING,
    UNAUTHENTICATED,
    AUTHENTICATED
}

data class CloudSyncResult(
    val success: Boolean,
    val message: String,
    val syncedTimestamp: Long = System.currentTimeMillis()
)

class FirebaseSyncManager(private val context: Context) {

    fun isFirebaseConfigured(): Boolean {
        return try {
            if (FirebaseApp.getApps(context).isEmpty()) {
                FirebaseApp.initializeApp(context)
            }
            FirebaseApp.getApps(context).isNotEmpty()
        } catch (e: Exception) {
            false
        }
    }

    fun getConnectionState(): CloudConnectionState {
        if (!isFirebaseConfigured()) return CloudConnectionState.CONFIG_MISSING
        return try {
            val user = FirebaseAuth.getInstance().currentUser
            if (user != null) CloudConnectionState.AUTHENTICATED else CloudConnectionState.UNAUTHENTICATED
        } catch (e: Exception) {
            CloudConnectionState.CONFIG_MISSING
        }
    }

    fun getCurrentUserEmail(): String? {
        if (!isFirebaseConfigured()) return null
        return try {
            val user = FirebaseAuth.getInstance().currentUser
            when {
                user == null -> null
                !user.email.isNullOrBlank() -> user.email
                user.isAnonymous -> "Cloud Device ID: ${user.uid.take(8)}"
                else -> user.uid.take(8)
            }
        } catch (e: Exception) {
            null
        }
    }

    suspend fun signInOrRegister(email: String, password: String, isRegister: Boolean): CloudSyncResult {
        if (!isFirebaseConfigured()) {
            return CloudSyncResult(
                false,
                "Firebase কনফিগার করা নেই। অফলাইন ডাটাবেস সম্পূর্ণ সচল আছে।"
            )
        }
        return try {
            val auth = FirebaseAuth.getInstance()
            if (isRegister) {
                auth.createUserWithEmailAndPassword(email.trim(), password).await()
            } else {
                auth.signInWithEmailAndPassword(email.trim(), password).await()
            }
            CloudSyncResult(true, "সফলভাবে Firebase ক্লাউডে লগইন হয়েছে (${auth.currentUser?.email})")
        } catch (e: Exception) {
            CloudSyncResult(false, e.localizedMessage ?: "Authentication failed")
        }
    }

    private suspend fun ensureUid(): String? {
        if (!isFirebaseConfigured()) return null
        return try {
            val auth = FirebaseAuth.getInstance()
            if (auth.currentUser == null) {
                auth.signInAnonymously().await()
            }
            auth.currentUser?.uid
        } catch (e: Exception) {
            null
        }
    }

    fun signOut() {
        if (!isFirebaseConfigured()) return
        try {
            FirebaseAuth.getInstance().signOut()
        } catch (_: Exception) {
        }
    }

    suspend fun syncAllToFirestore(dao: ShopKhataDao): CloudSyncResult {
        if (!isFirebaseConfigured()) {
            return CloudSyncResult(
                false,
                "অফলাইন মোড সক্রিয়: আপনার সকল হিসাব লোকাল ডাটাবেসে নিরাপদে সংরক্ষিত আছে।"
            )
        }
        return try {
            val uid = ensureUid()
                ?: return CloudSyncResult(false, "ক্লাউড সিঙ্ক করতে ব্যাকআপ ও সিকিউরিটি স্ক্রিন থেকে Firebase লগইন করুন।")

            val db = FirebaseFirestore.getInstance()
            val shopDoc = db.collection("shops").document(uid)

            val settings = dao.getSettingsOnce() ?: ShopSettingsEntity()
            val accounts = dao.getAllAccountsOnce()
            val customers = dao.getAllCustomersOnce()
            val transactions = dao.getAllTransactionsChronological()
            val services = dao.getAllServicesOnce()
            val accountTypes = dao.getAllAccountTypesOnce()
            val rules = dao.getAllTransactionRulesOnce()
            val dueEntries = dao.getAllDueLedgerEntriesOnce()
            val audits = dao.getAllAuditLogsOnce()

            shopDoc.set(
                mapOf(
                    "shopName" to settings.shopName,
                    "ownerPhone" to settings.ownerPhone,
                    "updatedAt" to System.currentTimeMillis()
                ),
                SetOptions.merge()
            ).await()

            val batch = db.batch()
            accounts.forEach { acc ->
                val ref = shopDoc.collection("accounts").document(acc.id)
                batch.set(ref, acc, SetOptions.merge())
            }
            customers.forEach { cust ->
                val ref = shopDoc.collection("customers").document(cust.id)
                batch.set(ref, cust, SetOptions.merge())
            }
            services.forEach { srv ->
                val ref = shopDoc.collection("services").document(srv.id)
                batch.set(ref, srv, SetOptions.merge())
            }
            accountTypes.forEach { t ->
                val ref = shopDoc.collection("account_types").document(t.id)
                batch.set(ref, t, SetOptions.merge())
            }
            rules.forEach { rule ->
                val ref = shopDoc.collection("transaction_rules").document(rule.id)
                batch.set(ref, rule, SetOptions.merge())
            }
            batch.commit().await()

            // Sync transactions in chunks of 200 to respect Firestore batch limits
            transactions.chunked(200).forEach { chunk ->
                val txBatch = db.batch()
                chunk.forEach { tx ->
                    val ref = shopDoc.collection("transactions").document(tx.id)
                    txBatch.set(ref, tx.copy(syncStatus = "SYNCED"), SetOptions.merge())
                }
                txBatch.commit().await()
                dao.markTransactionsSynced(chunk.map { it.id })
            }

            dueEntries.chunked(200).forEach { chunk ->
                val dBatch = db.batch()
                chunk.forEach { entry ->
                    val ref = shopDoc.collection("due_entries").document(entry.id)
                    dBatch.set(ref, entry, SetOptions.merge())
                }
                dBatch.commit().await()
            }

            audits.chunked(200).forEach { chunk ->
                val aBatch = db.batch()
                chunk.forEach { log ->
                    val ref = shopDoc.collection("audit_logs").document(log.id)
                    aBatch.set(ref, log, SetOptions.merge())
                }
                aBatch.commit().await()
            }

            val now = System.currentTimeMillis()
            dao.upsertSettings(
                settings.copy(
                    lastCloudSyncTimestamp = now,
                    cloudUserEmail = getCurrentUserEmail()
                )
            )
            CloudSyncResult(true, "Firebase ক্লাউডে (mfskdpo) সকল হিসাব সফলভাবে সিঙ্ক হয়েছে!", now)
        } catch (e: Exception) {
            CloudSyncResult(false, "ক্লাউড সিঙ্ক বার্তা: ${e.localizedMessage ?: "অফলাইন ডাটাবেসে সংরক্ষিত আছে"}")
        }
    }

    suspend fun pullAllFromFirestore(dao: ShopKhataDao): CloudSyncResult {
        if (!isFirebaseConfigured()) {
            return CloudSyncResult(false, "Firebase কনফিগার করা নেই।")
        }
        return try {
            val uid = ensureUid()
                ?: return CloudSyncResult(false, "ক্লাউড থেকে ডাটা ডাউনলোড করতে প্রথমে লগইন করুন।")

            val db = FirebaseFirestore.getInstance()
            val shopDoc = db.collection("shops").document(uid)

            val accSnap = shopDoc.collection("accounts").get().await()
            val custSnap = shopDoc.collection("customers").get().await()
            val txSnap = shopDoc.collection("transactions").get().await()
            val dueSnap = shopDoc.collection("due_entries").get().await()
            val audSnap = shopDoc.collection("audit_logs").get().await()

            var restoredItems = 0

            accSnap.documents.forEach { doc ->
                val id = doc.getString("id") ?: doc.id
                val serviceId = doc.getString("serviceId") ?: "srv_bkash"
                val serviceName = doc.getString("serviceName") ?: "MFS"
                val category = doc.getString("category") ?: "MFS"
                val accountType = doc.getString("accountType") ?: "Personal"
                val phoneNumber = doc.getString("phoneNumber") ?: ""
                val nickname = doc.getString("nickname") ?: serviceName
                val openingBalance = doc.getDouble("openingBalance") ?: 0.0
                val openingBalanceDate = doc.getLong("openingBalanceDate") ?: System.currentTimeMillis()
                val currentBalance = doc.getDouble("currentBalance") ?: openingBalance
                val isActive = doc.getBoolean("isActive") ?: true
                val isVisible = doc.getBoolean("isVisibleOnDashboard") ?: true
                val colorHex = doc.getString("colorHex") ?: "#065F46"
                val notes = doc.getString("notes") ?: ""
                dao.upsertAccount(
                    ShopAccountEntity(
                        id = id,
                        serviceId = serviceId,
                        serviceName = serviceName,
                        category = category,
                        accountType = accountType,
                        phoneNumber = phoneNumber,
                        nickname = nickname,
                        openingBalance = openingBalance,
                        openingBalanceDate = openingBalanceDate,
                        currentBalance = currentBalance,
                        isActive = isActive,
                        isVisibleOnDashboard = isVisible,
                        colorHex = colorHex,
                        notes = notes
                    )
                )
                restoredItems++
            }

            custSnap.documents.forEach { doc ->
                val id = doc.getString("id") ?: doc.id
                val name = doc.getString("name") ?: return@forEach
                val phone = doc.getString("phone") ?: ""
                val address = doc.getString("address") ?: ""
                val notes = doc.getString("notes") ?: ""
                val customField = doc.getString("customField") ?: ""
                val openingDue = doc.getDouble("openingDue") ?: 0.0
                val currentDue = doc.getDouble("currentDue") ?: openingDue
                dao.upsertCustomer(
                    CustomerEntity(
                        id = id,
                        name = name,
                        phone = phone,
                        address = address,
                        notes = notes,
                        customField = customField,
                        openingDue = openingDue,
                        currentDue = currentDue
                    )
                )
                restoredItems++
            }

            txSnap.documents.forEach { doc ->
                val id = doc.getString("id") ?: doc.id
                val accountId = doc.getString("accountId") ?: return@forEach
                val accountName = doc.getString("accountName") ?: ""
                val serviceName = doc.getString("serviceName") ?: ""
                val accountPhone = doc.getString("accountPhone") ?: ""
                val secondaryAccountId = doc.getString("secondaryAccountId")
                val secondaryAccountName = doc.getString("secondaryAccountName")
                val transactionTypeId = doc.getString("transactionTypeId") ?: ""
                val transactionTypeName = doc.getString("transactionTypeName") ?: ""
                val customerId = doc.getString("customerId")
                val customerName = doc.getString("customerName")
                val customerPhone = doc.getString("customerPhone")
                val amount = doc.getDouble("amount") ?: 0.0
                val paidAmount = doc.getDouble("paidAmount") ?: 0.0
                val dueAmount = doc.getDouble("dueAmount") ?: 0.0
                val paymentStatus = doc.getString("paymentStatus") ?: "PAID"
                val paymentMethodAccountId = doc.getString("paymentMethodAccountId")
                val paymentMethodName = doc.getString("paymentMethodName")
                val primaryAccountDelta = doc.getDouble("primaryAccountDelta") ?: 0.0
                val secondaryAccountDelta = doc.getDouble("secondaryAccountDelta") ?: 0.0
                val customerDueDelta = doc.getDouble("customerDueDelta") ?: 0.0
                val primaryBalanceAfter = doc.getDouble("primaryBalanceAfter") ?: 0.0
                val secondaryBalanceAfter = doc.getDouble("secondaryBalanceAfter")
                val customerDueAfter = doc.getDouble("customerDueAfter")
                val note = doc.getString("note") ?: ""
                val timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis()
                val isEdited = doc.getBoolean("isEdited") ?: false
                val editCount = (doc.getLong("editCount") ?: 0L).toInt()
                val isReversed = doc.getBoolean("isReversed") ?: false
                val reversalReason = doc.getString("reversalReason") ?: ""

                dao.upsertTransaction(
                    ShopTransactionEntity(
                        id = id,
                        accountId = accountId,
                        accountName = accountName,
                        serviceName = serviceName,
                        accountPhone = accountPhone,
                        secondaryAccountId = secondaryAccountId,
                        secondaryAccountName = secondaryAccountName,
                        transactionTypeId = transactionTypeId,
                        transactionTypeName = transactionTypeName,
                        customerId = customerId,
                        customerName = customerName,
                        customerPhone = customerPhone,
                        amount = amount,
                        paidAmount = paidAmount,
                        dueAmount = dueAmount,
                        paymentStatus = paymentStatus,
                        paymentMethodAccountId = paymentMethodAccountId,
                        paymentMethodName = paymentMethodName,
                        primaryAccountDelta = primaryAccountDelta,
                        secondaryAccountDelta = secondaryAccountDelta,
                        customerDueDelta = customerDueDelta,
                        primaryBalanceAfter = primaryBalanceAfter,
                        secondaryBalanceAfter = secondaryBalanceAfter,
                        customerDueAfter = customerDueAfter,
                        note = note,
                        timestamp = timestamp,
                        isEdited = isEdited,
                        editCount = editCount,
                        isReversed = isReversed,
                        reversalReason = reversalReason,
                        syncStatus = "SYNCED"
                    )
                )
                restoredItems++
            }

            dueSnap.documents.forEach { doc ->
                val id = doc.getString("id") ?: doc.id
                val customerId = doc.getString("customerId") ?: return@forEach
                val customerName = doc.getString("customerName") ?: ""
                val transactionId = doc.getString("transactionId")
                val actionType = doc.getString("actionType") ?: "DUE_CREATED"
                val actionLabel = doc.getString("actionLabel") ?: ""
                val serviceAmount = doc.getDouble("serviceAmount") ?: 0.0
                val paidAmount = doc.getDouble("paidAmount") ?: 0.0
                val dueDelta = doc.getDouble("dueDelta") ?: 0.0
                val resultingDue = doc.getDouble("resultingDue") ?: 0.0
                val accountUsedId = doc.getString("accountUsedId")
                val accountUsedName = doc.getString("accountUsedName")
                val note = doc.getString("note") ?: ""
                val timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis()
                dao.upsertDueLedgerEntry(
                    DueLedgerEntryEntity(
                        id = id,
                        customerId = customerId,
                        customerName = customerName,
                        transactionId = transactionId,
                        actionType = actionType,
                        actionLabel = actionLabel,
                        serviceAmount = serviceAmount,
                        paidAmount = paidAmount,
                        dueDelta = dueDelta,
                        resultingDue = resultingDue,
                        accountUsedId = accountUsedId,
                        accountUsedName = accountUsedName,
                        note = note,
                        timestamp = timestamp
                    )
                )
            }

            audSnap.documents.forEach { doc ->
                val id = doc.getString("id") ?: doc.id
                val entityType = doc.getString("entityType") ?: "TRANSACTION"
                val entityId = doc.getString("entityId") ?: ""
                val action = doc.getString("action") ?: "EDITED"
                val title = doc.getString("title") ?: ""
                val prev = doc.getString("previousValueSummary") ?: ""
                val next = doc.getString("newValueSummary") ?: ""
                val reason = doc.getString("reason") ?: ""
                val timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis()
                dao.insertAuditLog(
                    AuditLogEntity(
                        id = id,
                        entityType = entityType,
                        entityId = entityId,
                        action = action,
                        title = title,
                        previousValueSummary = prev,
                        newValueSummary = next,
                        reason = reason,
                        timestamp = timestamp
                    )
                )
            }

            val now = System.currentTimeMillis()
            val settings = dao.getSettingsOnce() ?: ShopSettingsEntity()
            dao.upsertSettings(
                settings.copy(
                    lastCloudSyncTimestamp = now,
                    cloudUserEmail = getCurrentUserEmail()
                )
            )
            CloudSyncResult(true, "ক্লাউড থেকে $restoredItems টি রেকর্ড সফলভাবে রিস্টোর হয়েছে!", now)
        } catch (e: Exception) {
            CloudSyncResult(false, "ক্লাউড ডাউনলোড ত্রুটি: ${e.localizedMessage ?: "Network unavailable"}")
        }
    }
}
