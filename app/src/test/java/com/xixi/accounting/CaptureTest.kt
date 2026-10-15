package com.xixi.accounting

import org.junit.Assert.*
import org.junit.Test

class CaptureTest {
    private fun notification(text: String, id: String = "n1", title: String = "微信支付") =
        CaptureInput("com.tencent.mm", CaptureSource.NOTIFICATION, id, 1000, title, text)

    @Test fun completedPaymentProducesCandidateWithIntegerFen() {
        val result = CaptureParser.parse(notification("支付成功\n支付金额：￥12.34"))!!
        assertEquals(1234L, result.amountFen)
        assertEquals(Direction.OUT, result.direction)
        assertEquals(TransactionKind.PAYMENT, result.kind)
        assertEquals(Completion.COMPLETED, result.completion)
    }

    @Test fun missingAmountStaysNullAndNeedsConfirmation() {
        val result = CaptureParser.parse(notification("支付成功"))!!
        assertNull(result.amountFen)
        assertEquals(Completion.UNCERTAIN, result.completion)
    }

    @Test fun unrelatedSourcesAndConversationsAreNotCaptured() {
        assertNull(CaptureParser.parse(notification("今晚吃饭花了12.34元", title = "朋友")))
        assertNull(CaptureParser.parse(notification("支付成功 ￥12.34", title = "朋友")))
        assertNull(CaptureParser.parse(notification("支付成功 ￥12.34", title = "微信")))
        assertNull(CaptureParser.parse(notification("你给我转账12元吧", title = "朋友")))
        assertNull(CaptureParser.parse(notification("下午开会", title = "微信支付")))
        assertNull(CaptureParser.parse(notification("支付成功 ￥12.34").copy(packageName = "com.example.other")))
    }

    @Test fun onlyExplicitCompletedStatesCanBeCompletedCandidates() {
        listOf("待支付 ￥10.00", "请收款 ￥10.00", "红包待领取 ￥10.00", "[红包]微信红包 ￥10.00").forEach {
            assertNotEquals(it, Completion.COMPLETED, CaptureParser.parse(notification(it))!!.completion)
        }
        assertEquals(Completion.PENDING, CaptureParser.parse(notification("红包待领取 ￥10.00"))!!.completion)
        assertEquals(Completion.UNCERTAIN, CaptureParser.parse(notification("支付金额 ￥10.00"))!!.completion)
        assertEquals(Completion.UNCERTAIN, CaptureParser.parse(notification("支付成功 待支付 ￥10.00"))!!.completion)
    }

    @Test fun amountMustBeUnambiguousPositiveAndExactlyRepresentable() {
        assertNull(CaptureParser.parse(notification("支付成功 ￥12.34 ￥18.00"))!!.amountFen)
        assertNull(CaptureParser.parse(notification("支付成功 ￥12.345"))!!.amountFen)
        assertNull(CaptureParser.parse(notification("支付成功 ￥0.00"))!!.amountFen)
        assertNull(CaptureParser.parse(notification("支付成功 ￥999999999999999999999"))!!.amountFen)
        assertEquals(123456L, CaptureParser.parse(notification("支付成功 金额：1,234.56元"))!!.amountFen)
        assertEquals(1234L, CaptureParser.parse(notification("支付成功 ￥12.34\n支付金额12.34元"))!!.amountFen)
        assertEquals(Completion.UNCERTAIN, CaptureParser.parse(notification("支付成功 ￥12.34 ￥18.00"))!!.completion)
    }

    @Test fun completedTypesRequireAnExplicitMoneyDirection() {
        val examples = listOf(
            Triple("退款成功 ￥5.00", TransactionKind.REFUND, Direction.IN),
            Triple("转账成功 转账给示例联系人 ￥5.00", TransactionKind.TRANSFER, Direction.OUT),
            Triple("收到转账 已收款 ￥5.00", TransactionKind.TRANSFER, Direction.IN),
            Triple("收款到账 ￥5.00", TransactionKind.RECEIPT, Direction.IN),
            Triple("已领取红包 ￥5.00", TransactionKind.RED_PACKET, Direction.IN),
            Triple("红包已发出 ￥5.00", TransactionKind.RED_PACKET, Direction.OUT),
        )
        examples.forEach { (text, kind, direction) ->
            val result = CaptureParser.parse(notification(text))!!
            assertEquals(text, kind, result.kind)
            assertEquals(text, direction, result.direction)
            assertEquals(text, Completion.COMPLETED, result.completion)
        }
        val unclear = CaptureParser.parse(notification("转账成功 ￥5.00"))!!
        assertNull(unclear.direction)
        assertEquals(Completion.UNCERTAIN, unclear.completion)
        assertNull(CaptureParser.parse(notification("支付成功 收款成功 ￥5.00"))!!.direction)
    }

    @Test fun pagesMustBeExplicitLiveResultsAndNeverHistoricalDetails() {
        fun page(text: String) = notification(text).copy(source = CaptureSource.PAGE)
        listOf("交易详情\n支付成功 ￥12.34", "账单详情\n收款成功 ￥12.34", "历史账单\n退款成功 ￥12.34", "你好 ￥12.34", "微信钱包 余额￥12.34").forEach {
            assertNull(it, CaptureParser.parse(page(it)))
            assertFalse(it, CaptureParser.isLivePaymentPage(it))
        }
        assertTrue(CaptureParser.isLivePaymentPage("支付成功"))
        assertEquals(1234L, CaptureParser.parse(page("支付成功 ￥12.34"))!!.amountFen)
        assertNull(CaptureParser.parse(page("交易详情 支付成功 ￥12.34").copy(source = CaptureSource.OCR)))
    }

    @Test fun candidatesDeduplicateReliableIdentityButRetainRealSameAmountEvents() {
        val first = CaptureParser.parse(notification("支付成功 ￥12.34\n交易号：202610120000001"))!!
        assertEquals("202610120000001", first.transactionId)
        val replay = CaptureHistory.add(CaptureHistory.add(emptyList(), first), first)
        assertEquals(1, replay.size)
        val sameTransaction = first.copy(eventId = "page1", source = CaptureSource.PAGE)
        assertEquals(1, CaptureHistory.add(replay, sameTransaction).size)
        val second = CaptureParser.parse(notification("支付成功 ￥12.34", "n2"))!!
        assertEquals(2, CaptureHistory.add(replay, second).size)
        val third = second.copy(eventId = "n3")
        assertEquals(3, CaptureHistory.add(CaptureHistory.add(replay, second), third).size)
        val noReliableLink = second.copy(eventId = "page2", source = CaptureSource.PAGE)
        assertEquals(3, CaptureHistory.add(CaptureHistory.add(replay, second), noReliableLink).size)
        val missing = CaptureParser.parse(notification("支付成功", "partial"))!!
        val filled = second.copy(eventId = "partial")
        val updated = CaptureHistory.add(CaptureHistory.add(emptyList(), missing), filled)
        assertEquals(1, updated.size)
        assertEquals(1234L, updated.single().amountFen)
    }

    @Test fun localCandidatesKeepOnlyTheLatestOneHundredEvents() {
        var history = emptyList<CaptureCandidate>()
        repeat(101) { index ->
            history = CaptureHistory.add(history, CaptureParser.parse(notification("支付成功 ￥1.00", "n$index"))!!)
        }
        assertEquals(100, history.size)
        assertEquals("n100", history.first().eventId)
        assertFalse(history.any { it.eventId == "n0" })
    }

    @Test fun contradictoryUpdatesOfOneEventStayUncertainEvenAfterReplay() {
        val first = CaptureParser.parse(notification("支付成功 ￥12.34"))!!
        val contradictory = CaptureParser.parse(notification("支付成功 ￥18.00"))!!
        val history = CaptureHistory.add(CaptureHistory.add(emptyList(), first), contradictory)
        assertEquals(1, history.size)
        assertNull(history.single().amountFen)
        assertEquals(Completion.UNCERTAIN, history.single().completion)
        assertEquals(Completion.UNCERTAIN, CaptureHistory.add(history, first).single().completion)
        val conflictText = CaptureParser.parse(notification("支付成功 ￥12.34 ￥18.00"))!!
        assertNull(CaptureHistory.add(listOf(first), conflictText).single().amountFen)
        val pending = CaptureParser.parse(notification("待支付 ￥12.34"))!!
        assertEquals(Completion.UNCERTAIN, CaptureHistory.add(listOf(first), pending).single().completion)
    }

    @Test fun receivedButUnclaimedPacketsAndNegatedSuccessAreNotCompleted() {
        listOf("收到红包 ￥10.00", "未支付成功 ￥10.00", "支付失败 上次支付成功 ￥10.00", "支付成功 已取消 ￥10.00").forEach { text ->
            assertNotEquals(text, Completion.COMPLETED, CaptureParser.parse(notification(text))!!.completion)
        }
    }

    @Test fun englishOutgoingTransferResultIsCapturedWithoutAssumingReceipt() {
        // Synthetic amount and recipient; never commit the user's payment text or screenshot.
        val input = notification("Payment successful\nAwaiting 示例收款方 to confirm receipt\n¥28.60\nDone")
            .copy(source = CaptureSource.PAGE)
        assertTrue(CaptureParser.isLivePaymentPage(input.text))
        val result = CaptureParser.parse(input)!!
        assertEquals(2860L, result.amountFen)
        assertEquals(Direction.OUT, result.direction)
        assertEquals(TransactionKind.TRANSFER, result.kind)
        assertEquals(Completion.UNCERTAIN, result.completion)
        assertFalse(result.conflicted)
    }

    @Test fun englishPagesStillRejectHistoryAndUnclearCompletion() {
        fun page(text: String) = notification(text).copy(source = CaptureSource.PAGE)
        listOf("Transaction details\nPayment successful\n¥28.60", "Transfer details\nPayment successful\n¥28.60").forEach {
            assertNull(CaptureParser.parse(page(it)))
        }
        assertEquals(Completion.UNCERTAIN, CaptureParser.parse(page("Payment successful\nCancelled\n¥28.60"))!!.completion)
        assertNull(CaptureParser.parse(page("Payment successful\nAwaiting 示例收款方 to confirm receipt\n¥28.60\n¥18.00"))!!.amountFen)
        assertNull(CaptureParser.parse(page("Payment successful\nAwaiting 示例收款方 to confirm receipt"))!!.amountFen)
        assertFalse(CaptureParser.isLivePaymentPage("Not Payment successful\n¥28.60"))
        assertNull(CaptureParser.parse(page("Payment successful\n¥28.60").copy(packageName = "com.example.other")))
        assertNull(CaptureParser.parse(notification("Payment successful\n¥28.60", title = "朋友")))
        val completed = CaptureParser.parse(page("Payment successful\n¥28.60"))!!
        assertEquals(Completion.COMPLETED, completed.completion)
        assertEquals(Direction.OUT, completed.direction)
        assertEquals(TransactionKind.PAYMENT, completed.kind)
    }
}
