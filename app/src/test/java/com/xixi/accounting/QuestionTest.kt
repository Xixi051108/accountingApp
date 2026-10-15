package com.xixi.accounting

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class QuestionTest {
    private val today = LocalDate.of(2026, 10, 15)
    @Test fun monthlyDiningQuestionUsesPostedLedgerAndRefundsWithTraceableDetails() {
        val plan = LedgerQuestions.local("这个月餐饮花了多少", today).plan!!
        assertEquals("2026-10-01", plan.start)
        assertEquals("2026-10-31", plan.end)
        assertEquals("餐饮", plan.category)
        val expense = LedgerEntry(amountFen = 3000, direction = "支出", date = "2026-10-12", category = "餐饮", posted = true)
        val refund = expense.copy(id = "refund", amountFen = 300, direction = "收入", kind = "退款")
        val result = LedgerQuestions.answer(plan, listOf(expense, refund,
            expense.copy(id = "pending", posted = false), expense.copy(id = "old", date = "2026-09-30"),
            expense.copy(id = "internal", kind = "内部转账")))
        assertEquals(2, result.entries.size)
        assertTrue(result.text.contains("27.00"))
        assertTrue(result.text.contains("已入账"))
    }

    @Test fun questionsResolveDatesMerchantsAndRefundsWithoutSilentlyIgnoringConditions() {
        val refund = LedgerQuestions.local("上周有哪些退款", today).plan!!
        assertEquals("2026-10-05", refund.start)
        assertEquals("2026-10-11", refund.end)
        assertEquals("退款", refund.kind)
        val merchant = LedgerQuestions.local("昨天商户：示例书店的消费明细", today).plan!!
        assertEquals("2026-10-14", merchant.start)
        assertEquals("示例书店", merchant.merchant)
        assertNull(LedgerQuestions.local("对比上周和本周", today).plan)
        assertNull(LedgerQuestions.local("删除这个月账单", today).plan)
        assertNull(LedgerQuestions.local("这个月餐饮和交通分别花了多少", today).plan)
        assertNull(LedgerQuestions.local("本月海底捞多少钱", today).plan)
        assertNull(LedgerQuestions.local("本月100元以上的餐饮账单", today).plan)
        assertNull(LedgerQuestions.local("昨天2026-10-12的账单", today).plan)
    }

    @Test fun modelOnlySuppliesValidatedReadFiltersNeverSqlOrLedgerWrites() {
        val fields = mapOf("start" to "2026-10-01", "end" to "2026-10-15", "category" to "餐饮",
            "merchant" to "", "direction" to "支出", "kind" to "", "metric" to "summary", "clarification" to "")
        assertNotNull(LedgerQuestions.fromModel(fields).plan)
        assertNull(LedgerQuestions.fromModel(fields + ("sql" to "DELETE FROM ledger")).plan)
        assertNull(LedgerQuestions.fromModel(fields + ("end" to "2026-02-30")).plan)
        assertNull(LedgerQuestions.fromModel(fields + ("start" to "2026-11-01")).plan)
        assertNull(LedgerQuestions.fromModel(fields + ("metric" to "delete")).plan)
    }
}
