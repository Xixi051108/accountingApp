package com.xixi.accounting

import java.time.LocalDate
import java.text.Normalizer
import java.util.Locale

data class ImportResult(val entries: List<LedgerEntry>, val skipped: Int = 0)
data class Totals(val expenseFen: Long, val refundFen: Long, val incomeFen: Long) {
    val netExpenseFen get() = expenseFen - refundFen
}

object LedgerRules {
    val kinds = listOf("消费", "收入", "退款", "转账", "红包", "内部转账")
    fun merchantKey(merchant: String): String = Normalizer.normalize(merchant, Normalizer.Form.NFKC).filterNot { it.isWhitespace() }

    private fun timesOverlap(first: String, second: String): Boolean {
        if (first.isBlank() || second.isBlank()) return true
        val precision = if (first.length == 8 && second.length == 8) 8 else 5
        return first.take(precision) == second.take(precision)
    }

    fun possibleDuplicate(first: LedgerEntry, second: LedgerEntry): Boolean {
        if (first.transactionId.isNotBlank() && second.transactionId.isNotBlank()) return first.transactionId == second.transactionId
        val firstMerchant = merchantKey(first.merchant)
        val secondMerchant = merchantKey(second.merchant)
        return first.date.isNotBlank() && first.date == second.date && first.amountFen != null && first.amountFen == second.amountFen &&
            first.direction == second.direction && first.kind == second.kind && timesOverlap(first.time, second.time) &&
            (firstMerchant == secondMerchant || firstMerchant.isBlank() || secondMerchant.isBlank())
    }

    fun validation(entry: LedgerEntry): String? = when {
        entry.amountFen == null || entry.amountFen !in 1..999_999_999_999L -> "请输入有效金额，最多两位小数"
        entry.direction !in listOf("收入", "支出") -> "请选择收支方向"
        entry.kind !in kinds -> "请选择交易类型"
        entry.kind == "退款" && entry.direction != "收入" -> "退款应为流入，请核对方向"
        entry.kind == "消费" && entry.direction != "支出" -> "消费应为支出，请核对类型"
        entry.kind == "收入" && entry.direction != "收入" -> "收入类型应为流入，请核对方向"
        !Regex("\\d{4}-\\d{2}-\\d{2}").matches(entry.date) || runCatching { LocalDate.parse(entry.date) }.isFailure -> "请输入完整有效日期，例如 2026-10-15"
        entry.time.isNotBlank() && !Regex("(?:[01]\\d|2[0-3]):[0-5]\\d(?::[0-5]\\d)?").matches(entry.time) -> "时间格式应为 HH:mm 或 HH:mm:ss"
        entry.category !in ScreenshotParser.categories -> "请选择有效分类"
        entry.merchant.length > 200 -> "商户或备注请控制在 200 字内"
        else -> null
    }

    fun prepareImport(existing: List<LedgerEntry>, candidates: List<LedgerEntry>, hash: String): ImportResult {
        val added = mutableListOf<LedgerEntry>()
        var skipped = 0
        for ((index, candidate) in candidates.withIndex()) {
            val all = existing + added
            val identical = all.firstOrNull { candidate.transactionId.isNotBlank() && it.transactionId == candidate.transactionId }
            if (identical != null && identical.amountFen == candidate.amountFen && identical.direction == candidate.direction &&
                identical.kind == candidate.kind && identical.date == candidate.date && timesOverlap(identical.time, candidate.time)) { skipped++; continue }
            val overlap = all.any { possibleDuplicate(it, candidate) }
            val reason = listOfNotNull(candidate.reason.takeIf { it.isNotBlank() }, validation(candidate),
                if (identical != null) "同一交易号内容冲突，请核对已有账单" else if (overlap) "疑似重复，请核对已有账单" else null)
                .distinct().joinToString("；")
            added.add(candidate.copy(sourceHash = hash, sourceRow = index, posted = reason.isBlank(), reason = reason))
        }
        return ImportResult(added, skipped)
    }

    fun confirm(entry: LedgerEntry): LedgerEntry {
        require(validation(entry) == null) { validation(entry).orEmpty() }
        return entry.copy(posted = true, reason = "")
    }

    fun totals(entries: List<LedgerEntry>): Totals {
        val posted = entries.filter { it.posted && it.kind != "内部转账" }
        return Totals(
            posted.filter { it.direction == "支出" }.sumOf { it.amountFen ?: 0L },
            posted.filter { it.kind == "退款" && it.direction == "收入" }.sumOf { it.amountFen ?: 0L },
            posted.filter { it.direction == "收入" && it.kind != "退款" }.sumOf { it.amountFen ?: 0L },
        )
    }

    fun formatMoney(fen: Long): String = String.format(Locale.ROOT, "%s%d.%02d", if (fen < 0) "-" else "", kotlin.math.abs(fen) / 100, kotlin.math.abs(fen) % 100)
}
