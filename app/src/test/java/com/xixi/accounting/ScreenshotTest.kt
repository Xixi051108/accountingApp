package com.xixi.accounting

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class ScreenshotTest {
    private fun line(text: String, y: Int, x: Int = 20) = OcrLine(text, x, y, x + 200, y + 24)

    @Test fun listScreenshotBecomesSeparateTransactionsWithoutDailyTotals() {
        val result = ScreenshotParser.parse(listOf(
            line("2026年10月12日", 20), line("出 31.80 入 0.00", 20, 650),
            line("餐饮", 120), line("-13.80", 120, 800), line("12:10 | 示例食堂", 160),
            line("交通", 250), line("-18.00", 250, 800), line("18:20 | 示例公交", 290),
        ))
        assertEquals(2, result.size)
        assertEquals(listOf(1380L, 1800L), result.map { it.amountFen })
        assertEquals("2026-10-12", result[0].date)
        assertEquals("示例食堂", result[0].merchant)
        assertEquals("餐饮", result[0].category)
        assertEquals("支出", result[1].direction)
    }

    @Test fun historicalPaymentDetailsAreImportableButNotInventedAsLiveEvents() {
        val result = ScreenshotParser.parse(listOf(
            line("交易详情", 10), line("示例文具店", 60), line("-26.50", 100),
            line("交易状态 支付成功", 160), line("交易时间 2026-10-11 15:26:10", 210),
            line("交易单号 20261011000000012345", 260),
        )).single()
        assertEquals(2650L, result.amountFen)
        assertEquals("2026-10-11", result.date)
        assertEquals("15:26:10", result.time)
        assertEquals("示例文具店", result.merchant)
        assertEquals("20261011000000012345", result.transactionId)
        assertEquals("", result.reason)
    }

    @Test fun missingYearDefaultsToCurrentYearWhileHiddenAmountAndPendingStatusRequireConfirmation() {
        val input = listOf(line("10月12日 昨天 出 7.20", 20),
            line("餐饮 -7.20", 100), line("12:10 | 示例餐厅", 140),
            line("退款", 200), line("13:20 | 转账-退款", 240))
        val withoutYear = ScreenshotParser.parse(input)
        assertEquals(2, withoutYear.size)
        assertEquals("${LocalDate.now(ZoneId.of("Asia/Shanghai")).year}-10-12", withoutYear[0].date)
        assertEquals("", withoutYear[0].reason)
        assertNull(withoutYear[1].amountFen)
        assertEquals("2026-10-12", ScreenshotParser.parse(input, 2026)[0].date)
        val pending = ScreenshotParser.parse(listOf(line("支付成功", 10), line("¥7.20", 50),
            line("等待对方确认收款", 100), line("交易时间 2026-10-12 12:10", 150))).single()
        assertTrue(pending.reason.contains("完成状态"))
    }

    @Test fun detailWithoutYearUsesCurrentYearAndExplicitScreenshotYearTakesPrecedence() {
        fun detail(date: String) = listOf(line("交易详情", 10), line("支付成功", 50), line("¥18.20", 90),
            line("交易时间 $date 15:10", 130))
        assertEquals("${LocalDate.now(ZoneId.of("Asia/Shanghai")).year}-10-11", ScreenshotParser.parse(detail("10月11日")).single().date)
        assertEquals("2024-10-11", ScreenshotParser.parse(detail("2024年10月11日"), 2026).single().date)
        assertEquals("2023-10-11", ScreenshotParser.parse(detail("10月11日"), 2023).single().date)
    }

    @Test fun incomingListRowsAndRefundsUseDifferentAccountingTypes() {
        val candidates = ScreenshotParser.parse(listOf(line("2026年10月12日", 20),
            line("工资 +2200.00", 100), line("09:10 | 示例单位", 140),
            line("退款 +2.00", 200), line("10:10 | 示例退款", 240)))
        val result = LedgerRules.prepareImport(emptyList(), candidates, "income-image").entries
        assertEquals(2, result.size)
        assertTrue(result.all { it.posted })
        assertEquals(220000L, LedgerRules.totals(result).incomeFen)
        assertEquals(200L, LedgerRules.totals(result).refundFen)
    }

    @Test fun compactRowsRetainTheirTimeAndIncompleteDatesRemainVisibleForReview() {
        val compact = ScreenshotParser.parse(listOf(line("2026年10月12日", 20), line("出 8.60 入 0.00", 60),
            line("餐饮 12:10 | 示例茶店 -8.60", 120)))
        assertTrue(LedgerRules.prepareImport(emptyList(), compact, "compact").entries.single().posted)
        assertEquals("12:10", compact.single().time)
        assertEquals("示例茶店", compact.single().merchant)
        val missingYear = ScreenshotParser.parse(listOf(line("10月12日", 20), line("餐饮 -8.60", 100), line("12:10 | 示例茶店", 140))).single()
        assertEquals("10月12日", missingYear.dateHint)
        val failed = ScreenshotParser.parse(listOf(line("2026年10月12日", 20), line("餐饮 -8.60", 100), line("12:10 | 支付失败", 140)))
        assertFalse(LedgerRules.prepareImport(emptyList(), failed, "failed").entries.single().posted)
    }
}
