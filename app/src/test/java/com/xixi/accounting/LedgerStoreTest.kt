package com.xixi.accounting

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], manifest = Config.NONE)
class LedgerStoreTest {
    @Test fun importConfirmEditReopenAndClearUseTheSamePersistentLedger() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        context.deleteDatabase("screenshot-ledger.db")
        var store = LedgerStore(LedgerDatabase.open(context))
        try {
            val first = LedgerEntry(date = "2026-10-12", time = "12:10", amountFen = 1200,
                direction = "支出", merchant = "替代商户")
            val pending = first.copy(id = "pending", time = "18:10", amountFen = null, reason = "金额缺失")
            val report = store.import(listOf(first, pending), "synthetic-image")
            assertEquals(1, report.posted); assertEquals(1, report.pending)
            assertTrue(store.import(listOf(first), "synthetic-image").duplicateImage)
            store.save(store.snapshot().first { !it.posted }.copy(amountFen = 800, category = "餐饮"), rememberCategory = true)
            assertEquals(2000L, LedgerRules.totals(store.snapshot()).netExpenseFen)
            store.close()
            store = LedgerStore(LedgerDatabase.open(context))
            assertEquals(2, store.snapshot().size)
            assertTrue(store.import(listOf(first), "synthetic-image").duplicateImage)
            val second = first.copy(id = "second-day", date = "2026-10-13")
            store.import(listOf(second), "synthetic-second-image")
            assertEquals("餐饮", store.snapshot().first { it.id == "second-day" }.category)
            store.delete(first.id)
            assertEquals(2000L, LedgerRules.totals(store.snapshot()).netExpenseFen)
            val changed = store.snapshot().first { it.id == "second-day" }.copy(amountFen = 1500)
            store.save(changed)
            assertEquals(2300L, LedgerRules.totals(store.snapshot()).netExpenseFen)
            store.clear()
            assertTrue(store.snapshot().isEmpty())
            assertFalse(store.import(listOf(first), "synthetic-image").duplicateImage)
            assertEquals("其他", store.snapshot().single().category)
        } finally { store.close(); context.deleteDatabase("screenshot-ledger.db") }
    }
}
