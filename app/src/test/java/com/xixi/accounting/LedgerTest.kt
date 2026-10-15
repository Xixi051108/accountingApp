package com.xixi.accounting

import org.junit.Assert.*
import org.junit.Test

class LedgerTest {
    private fun entry(time: String = "12:10", amount: Long = 1500) = LedgerEntry(
        amountFen = amount, date = "2026-10-12", time = time, direction = "支出", merchant = "示例餐厅", category = "餐饮")

    @Test fun clearTransactionsPostWhileUncertainAndOverlappingRowsAwaitConfirmation() {
        val first = LedgerRules.prepareImport(emptyList(), listOf(entry(), entry("18:20"), entry("19:00").copy(amountFen = null)), "image-a")
        assertEquals(3, first.entries.size)
        assertTrue(first.entries[0].posted)
        assertTrue(first.entries[1].posted)
        assertFalse(first.entries[2].posted)
        assertEquals(3000L, LedgerRules.totals(first.entries).netExpenseFen)
        val overlap = LedgerRules.prepareImport(first.entries, listOf(entry(), entry("20:10")), "image-b").entries
        assertFalse(overlap[0].posted)
        assertTrue(overlap[0].reason.contains("重复"))
        assertTrue(overlap[1].posted)
        assertEquals(2, overlap.size)
    }

    @Test fun confirmationEditsDeletionRefundsAndInternalTransfersShareStatistics() {
        val pending = entry().copy(amountFen = null)
        assertThrows(IllegalArgumentException::class.java) { LedgerRules.confirm(pending) }
        val expense = LedgerRules.confirm(pending.copy(amountFen = 1800))
        val refund = LedgerRules.confirm(entry("13:10", 200).copy(direction = "收入", kind = "退款"))
        val internal = LedgerRules.confirm(entry("14:10", 50000).copy(kind = "内部转账"))
        assertEquals(1600L, LedgerRules.totals(listOf(expense, refund, internal, pending)).netExpenseFen)
        assertEquals(-200L, LedgerRules.totals(listOf(refund)).netExpenseFen)
        val replay = LedgerRules.prepareImport(listOf(expense.copy(transactionId = "txn-12345")),
            listOf(expense.copy(transactionId = "txn-12345")), "replay")
        assertTrue(replay.entries.isEmpty())
        assertEquals(1, replay.skipped)
        assertFalse(LedgerRules.prepareImport(listOf(expense.copy(transactionId = "txn-12345")),
            listOf(expense.copy(amountFen = 1900, transactionId = "txn-12345")), "conflict").entries.single().posted)
    }
}
