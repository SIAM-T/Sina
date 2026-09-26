package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    private lateinit var context: Context
    private lateinit var database: ShopKhataDatabase
    private lateinit var dao: ShopKhataDao
    private lateinit var repository: ShopKhataRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, ShopKhataDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.dao()
        repository = ShopKhataRepository(dao, FirebaseSyncManager(context))
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `app name matches Hisab Khata and initial seed has zero mock data`() = runBlocking {
        val appName = context.getString(R.string.app_name)
        assertEquals("Hisab Khata", appName)

        repository.ensureSeeded()

        val accounts = dao.getAllAccountsOnce()
        val customers = dao.getAllCustomersOnce()
        val transactions = dao.getAllTransactionsChronological()
        val dueEntries = dao.getAllDueLedgerEntriesOnce()

        // Only Physical Cash Drawer exists initially with 0 balance; zero mock MFS accounts, customers, or transactions
        assertEquals(1, accounts.size)
        assertEquals(ShopKhataRepository.CASH_ACCOUNT_ID, accounts.first().id)
        assertEquals(0.0, accounts.first().currentBalance, 0.001)
        assertTrue(customers.isEmpty())
        assertTrue(transactions.isEmpty())
        assertTrue(dueEntries.isEmpty())
    }

    @Test
    fun `multiple accounts with same phone number maintain independent balances`() = runBlocking {
        repository.ensureSeeded()
        val sharedPhone = "01700000000"

        repository.saveAccount(null, "srv_bkash", "Agent", sharedPhone, "bKash Agent", 25000.0, 1000L, true, true, "")
        repository.saveAccount(null, "srv_nagad", "Personal", sharedPhone, "Nagad Personal", 8000.0, 1000L, true, true, "")
        repository.saveAccount(null, "srv_rocket", "Personal", sharedPhone, "Rocket", 12000.0, 1000L, true, true, "")
        repository.saveAccount(null, "srv_gp", "Recharge", sharedPhone, "GP Recharge", 5000.0, 1000L, true, true, "")

        val accounts = dao.getAllAccountsOnce().filter { it.phoneNumber == sharedPhone }
        assertEquals(4, accounts.size)

        val bkash = accounts.first { it.nickname == "bKash Agent" }
        val nagad = accounts.first { it.nickname == "Nagad Personal" }
        val rocket = accounts.first { it.nickname == "Rocket" }
        val gp = accounts.first { it.nickname == "GP Recharge" }

        // Perform Cash In of 2,000 on bKash Agent (Paid in cash)
        repository.recordServiceTransaction(
            accountId = bkash.id,
            ruleId = "rule_cash_in",
            amount = 2000.0,
            paidAmount = 2000.0,
            paymentMethodAccountId = ShopKhataRepository.CASH_ACCOUNT_ID,
            customerId = null,
            note = "Customer cash in"
        )

        val updatedAccounts = dao.getAllAccountsOnce().associateBy { it.id }
        assertEquals(23000.0, updatedAccounts[bkash.id]!!.currentBalance, 0.001)
        assertEquals(8000.0, updatedAccounts[nagad.id]!!.currentBalance, 0.001)
        assertEquals(12000.0, updatedAccounts[rocket.id]!!.currentBalance, 0.001)
        assertEquals(5000.0, updatedAccounts[gp.id]!!.currentBalance, 0.001)
        assertEquals(2000.0, updatedAccounts[ShopKhataRepository.CASH_ACCOUNT_ID]!!.currentBalance, 0.001)
    }

    @Test
    fun `partial due, full due, due collection, manual due adjustments, edit, reverse, and reconcile work deterministically`() = runBlocking {
        repository.ensureSeeded()
        repository.saveAccount(null, "srv_bkash", "Agent", "01700000000", "bKash Agent", 20000.0, 1000L, true, true, "")
        val bkash = dao.getAllAccountsOnce().first { it.nickname == "bKash Agent" }
        val customer = repository.saveCustomer(null, "Rahim Store", "01800000000", "Bazar Road", "", "", 0.0)

        // 1. Partial payment transaction: Service 1,000, Customer paid 600, Due 400
        val tx = repository.recordServiceTransaction(
            accountId = bkash.id,
            ruleId = "rule_cash_in",
            amount = 1000.0,
            paidAmount = 600.0,
            paymentMethodAccountId = ShopKhataRepository.CASH_ACCOUNT_ID,
            customerId = customer.id,
            note = "Partial cash in"
        )
        assertNotNull(tx)
        assertEquals(19000.0, dao.getAccountById(bkash.id)!!.currentBalance, 0.001)
        assertEquals(600.0, dao.getAccountById(ShopKhataRepository.CASH_ACCOUNT_ID)!!.currentBalance, 0.001)
        assertEquals(400.0, dao.getCustomerById(customer.id)!!.currentDue, 0.001)

        // 2. Edit transaction from 1,000 to 1,200 (Paid 600 -> Due becomes 600) and verify Audit Log
        val edited = repository.editExistingTransaction(
            transactionId = tx!!.id,
            newAccountId = bkash.id,
            newRuleId = "rule_cash_in",
            newCustomerId = customer.id,
            newAmount = 1200.0,
            newPaidAmount = 600.0,
            newPaymentMethodAccountId = ShopKhataRepository.CASH_ACCOUNT_ID,
            newSecondaryTransferAccountId = null,
            newNote = "Edited amount",
            editReason = "Entered 1000 instead of 1200"
        )
        assertTrue(edited)
        assertEquals(18800.0, dao.getAccountById(bkash.id)!!.currentBalance, 0.001)
        assertEquals(600.0, dao.getAccountById(ShopKhataRepository.CASH_ACCOUNT_ID)!!.currentBalance, 0.001)
        assertEquals(600.0, dao.getCustomerById(customer.id)!!.currentDue, 0.001)
        assertTrue(dao.getAllAuditLogsOnce().any { it.entityId == tx.id && it.action == "EDITED" })

        // 3. Collect 600 due into Cash -> Customer Due becomes 0 (CLEAR) and Cash becomes 1,200
        val collected = repository.collectCustomerDue(
            customerId = customer.id,
            amountToCollect = 600.0,
            receivingAccountId = ShopKhataRepository.CASH_ACCOUNT_ID,
            note = "Full due paid"
        )
        assertTrue(collected)
        assertEquals(0.0, dao.getCustomerById(customer.id)!!.currentDue, 0.001)
        assertEquals(1200.0, dao.getAccountById(ShopKhataRepository.CASH_ACCOUNT_ID)!!.currentBalance, 0.001)

        // 4. Reconcile Cash balance from 1,200 to 1,150 (-50 adjustment)
        val reconciled = repository.reconcileAccountBalance(
            accountId = ShopKhataRepository.CASH_ACCOUNT_ID,
            actualBalance = 1150.0,
            reason = "Shortage of 50 taka"
        )
        assertTrue(reconciled)
        assertEquals(1150.0, dao.getAccountById(ShopKhataRepository.CASH_ACCOUNT_ID)!!.currentBalance, 0.001)

        // 5. Export and Restore JSON Backup
        val backupJson = repository.exportFullBackupJson()
        assertTrue(backupJson.isNotBlank())
        assertTrue(repository.restoreFromBackupJson(backupJson))
        assertEquals(18800.0, dao.getAccountById(bkash.id)!!.currentBalance, 0.001)
        assertEquals(1150.0, dao.getAccountById(ShopKhataRepository.CASH_ACCOUNT_ID)!!.currentBalance, 0.001)
    }
}
