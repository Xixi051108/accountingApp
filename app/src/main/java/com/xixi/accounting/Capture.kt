package com.xixi.accounting

enum class CaptureSource { NOTIFICATION, PAGE, OCR }
enum class Direction { OUT, IN }
enum class TransactionKind { PAYMENT, REFUND, TRANSFER, RECEIPT, RED_PACKET }
enum class Completion { COMPLETED, PENDING, UNCERTAIN }

data class CaptureInput(
    val packageName: String,
    val source: CaptureSource,
    val eventId: String,
    val capturedAt: Long,
    val title: String = "",
    val text: String,
)

data class CaptureCandidate(
    val eventId: String,
    val capturedAt: Long,
    val source: CaptureSource,
    val amountFen: Long?,
    val direction: Direction?,
    val kind: TransactionKind?,
    val completion: Completion,
    val transactionId: String? = null,
    val reason: String,
    val conflicted: Boolean = false,
)

object CaptureParser {
    fun parse(input: CaptureInput): CaptureCandidate? {
        if (input.packageName != "com.tencent.mm") return null
        if (input.source != CaptureSource.NOTIFICATION && !isLivePaymentPage(input.text)) return null
        if (input.source == CaptureSource.NOTIFICATION) {
            val trustedTitles = setOf("微信支付", "微信收款助手", "微信转账通知", "微信红包通知", "收款到账通知")
            val structured = Regex("^\\[(?:转账|红包)]").containsMatchIn(input.text.trim())
            if (input.title !in trustedTitles && !structured) return null
        }
        val englishPayment = isEnglishPaymentSuccessful(input.text)
        val awaitingReceipt = englishPayment && Regex("\\bAwaiting\\b.{1,200}?\\bto confirm receipt\\b",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).containsMatchIn(input.text)
        if (!Regex("支付|付款|退款|转账|收款|红包").containsMatchIn(input.text) && !englishPayment) return null
        val number = "(?:\\d{1,3}(?:,\\d{3})+|\\d+)(?:\\.\\d{1,2})?"
        val money = Regex("(?<![\\d.,+\\-])(?:[￥¥]\\s*|(?:支付金额|付款金额|退款金额|转账金额|收款金额|红包金额|金额)\\s*[:：]?\\s*)($number)(?![\\d.,])|(?<![\\d.,+\\-])($number)\\s*元")
        val amounts = money.findAll(input.text).map {
            val value = it.groupValues[1].ifBlank { it.groupValues[2] }.replace(",", "")
            runCatching { value.toBigDecimal().movePointRight(2).longValueExact().takeIf { fen -> fen > 0 } }.getOrNull()
        }.toList()
        val amount = amounts.distinct().singleOrNull()
        val pending = Regex("待支付|等待付款|待付款|请收款|待收款|待领取|未领取|等待收款|退款处理中|退款申请").containsMatchIn(input.text)
        val text = input.text
        val kind = when {
            awaitingReceipt -> TransactionKind.TRANSFER
            englishPayment -> TransactionKind.PAYMENT
            text.contains("退款") -> TransactionKind.REFUND
            text.contains("红包") -> TransactionKind.RED_PACKET
            text.contains("转账") -> TransactionKind.TRANSFER
            text.contains("收款") -> TransactionKind.RECEIPT
            Regex("支付|付款").containsMatchIn(text) -> TransactionKind.PAYMENT
            else -> null
        }
        val incoming = Regex("退款成功|退款到账|退款已到账|收款成功|收款到账|已收款|已领取红包|红包领取成功|收到红包").containsMatchIn(text)
        val outgoing = englishPayment || Regex("支付成功|付款成功|支付完成|转账给|已成功转账|已转账|红包已发出|已发出红包").containsMatchIn(text)
        val direction = when {
            incoming && outgoing -> null
            incoming -> Direction.IN
            outgoing -> Direction.OUT
            else -> null
        }
        val completed = englishPayment || Regex("支付成功|付款成功|支付完成|退款成功|退款到账|退款已到账|转账成功|已成功转账|收款成功|收款到账|已收款|已领取红包|红包领取成功|红包已发出|已发出红包").containsMatchIn(text)
        val invalidated = Regex("未支付成功|未付款成功|支付失败|付款失败|退款失败|转账失败|收款失败|领取失败|已取消|已关闭|已撤销").containsMatchIn(text) ||
            Regex("^\\s*(?:Cancelled|Canceled|Payment failed|Payment cancelled|Payment canceled|Refunded|Awaiting payment)\\s*$",
                setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE)).containsMatchIn(text)
        val completion = when {
            awaitingReceipt -> Completion.UNCERTAIN
            invalidated -> Completion.UNCERTAIN
            pending && completed -> Completion.UNCERTAIN
            pending -> Completion.PENDING
            !completed || amount == null || direction == null || kind == null -> Completion.UNCERTAIN
            else -> Completion.COMPLETED
        }
        return CaptureCandidate(input.eventId, input.capturedAt, input.source, amount, direction,
            kind, completion,
            transactionId = Regex("(?:微信支付单号|微信交易号|交易单号|交易号)\\s*[:：]?\\s*([0-9A-Za-z]{8,64})(?![0-9A-Za-z])")
                .find(text)?.groupValues?.get(1),
            reason = if (awaitingReceipt) "付款方支付成功，对方仍待确认收款；保存为待核对转账候选。" else when (completion) { Completion.COMPLETED -> "完成线索，仅用于核对，未入账。"; Completion.PENDING -> "交易待处理，不计入收支。"; Completion.UNCERTAIN -> "金额、方向或完成状态不明确，需要核对。" },
            conflicted = amounts.distinct().size > 1 || (incoming && outgoing) || (pending && completed) || (invalidated && completed))
    }
    fun isLivePaymentPage(text: String): Boolean {
        if (Regex("交易详情|账单详情|历史账单|全部账单|账单记录|交易记录|交易时间|支付时间").containsMatchIn(text)) return false
        if (Regex("\\b(?:Transaction details|Transfer details|Payment details|Transaction history|Payment history|Transaction time|Payment time)\\b",
                RegexOption.IGNORE_CASE).containsMatchIn(text)) return false
        return isEnglishPaymentSuccessful(text) || Regex("支付成功|付款成功|支付完成|退款成功|退款到账|退款已到账|转账成功|已成功转账|收款成功|收款到账|已收款|已领取红包|红包领取成功|红包已发出|已发出红包|待支付|待付款|请收款|待领取|未领取|等待收款|退款处理中").containsMatchIn(text)
    }

    private fun isEnglishPaymentSuccessful(text: String) =
        Regex("^\\s*Payment successful\\s*$", setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE)).containsMatchIn(text)
}

object CaptureHistory {
    fun add(existing: List<CaptureCandidate>, candidate: CaptureCandidate): List<CaptureCandidate> {
        val previous = existing.firstOrNull {
            it.eventId == candidate.eventId || (candidate.transactionId != null && it.transactionId == candidate.transactionId)
        }
        fun quality(c: CaptureCandidate) = listOf(c.amountFen, c.direction, c.kind, c.transactionId).count { it != null } +
            if (c.completion == Completion.COMPLETED) 1 else 0
        fun differs(a: Any?, b: Any?) = a != null && b != null && a != b
        val conflict = previous != null && (previous.conflicted || candidate.conflicted ||
            differs(previous.amountFen, candidate.amountFen) || differs(previous.direction, candidate.direction) ||
            differs(previous.kind, candidate.kind) || (previous.completion == Completion.COMPLETED && candidate.completion == Completion.PENDING))
        val result = when {
            conflict -> previous!!.copy(
                amountFen = if (previous.conflicted || candidate.conflicted || differs(previous.amountFen, candidate.amountFen)) null else previous.amountFen,
                direction = if (previous.conflicted || candidate.conflicted || differs(previous.direction, candidate.direction)) null else previous.direction,
                kind = if (differs(previous.kind, candidate.kind)) null else previous.kind,
                completion = Completion.UNCERTAIN, conflicted = true, reason = "同一事件的信息存在冲突，需要人工核对。")
            previous != null && quality(previous) > quality(candidate) -> previous
            else -> candidate.copy(eventId = previous?.eventId ?: candidate.eventId,
                capturedAt = previous?.capturedAt ?: candidate.capturedAt)
        }
        // ponytail: bounded local diagnostics, not a persistent ledger or global dedup index.
        return (listOf(result) + existing.filter { it.eventId != result.eventId }).take(100)
    }
}
