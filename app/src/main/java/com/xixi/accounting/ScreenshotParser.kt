package com.xixi.accounting

import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneId
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

data class OcrLine(val text: String, val left: Int, val top: Int, val right: Int, val bottom: Int)

@Entity(tableName = "ledger", indices = [Index("sourceHash"), Index("transactionId")])
data class LedgerEntry(
    @PrimaryKey val id: String = java.util.UUID.randomUUID().toString(),
    val amountFen: Long? = null,
    val direction: String = "",
    val kind: String = "消费",
    val date: String = "",
    val time: String = "",
    val merchant: String = "",
    val category: String = "其他",
    val posted: Boolean = false,
    val reason: String = "",
    val sourceHash: String = "",
    val sourceRow: Int = 0,
    val transactionId: String = "",
    val dateHint: String = "",
)

object ScreenshotParser {
    val categories = listOf("餐饮", "交通", "购物", "日用", "住房", "娱乐", "医疗", "学习", "工资", "其他")
    private val datePattern = Regex("(?:(\\d{4})[年/.-])?(\\d{1,2})[月/-](\\d{1,2})日?")
    private val clock = Regex("(?<!\\d)([01]?\\d|2[0-3]):([0-5]\\d)(?::([0-5]\\d))?(?!\\d)")
    private val signedMoney = Regex("(?<![\\d.,])([+−–-])\\s*((?:\\d{1,3}(?:,\\d{3})+|\\d+)\\.\\d{2})(?![\\d.,])")
    private val unfinished = Regex("未支付成功|未付款成功|失败|待支付|待付款|等待付款|待收款|未完成|等待.{0,30}确认收款|已取消|已关闭|已撤销|退款处理中|待领取|Not\\s+Payment\\s+successful|Awaiting|Cancelled|Canceled|Failed", RegexOption.IGNORE_CASE)

    private fun clockValue(match: MatchResult): String = match.groupValues[1].padStart(2, '0') +
        ":" + match.groupValues[2] + match.groupValues[3].let { if (it.isBlank()) "" else ":$it" }

    fun money(text: String): Long? = runCatching {
        if (!Regex("(?:\\d{1,3}(?:,\\d{3})+|\\d+)(?:\\.\\d{1,2})?").matches(text.trim())) return null
        BigDecimal(text.trim().replace(",", "")).movePointRight(2).longValueExact().takeIf { it in 1..999_999_999_999L }
    }.getOrNull()

    fun parse(lines: List<OcrLine>, confirmedYear: Int? = null): List<LedgerEntry> {
        val defaultYear = confirmedYear ?: LocalDate.now(ZoneId.of("Asia/Shanghai")).year
        val rows = mutableListOf<MutableList<OcrLine>>()
        for (line in lines.sortedBy { it.top }) {
            val row = rows.lastOrNull()
            val center = (line.top + line.bottom) / 2
            if (row != null && row.any { center in it.top..it.bottom && (it.top + it.bottom) / 2 in line.top..line.bottom }) row.add(line)
            else rows.add(mutableListOf(line))
        }
        val texts = rows.map { row -> row.sortedBy { it.left }.joinToString(" ") { it.text }.replace('：', ':')
            .replace(Regex("(\\d{1,2})时\\s*(\\d{2})分(?:\\s*(\\d{2})秒)?")) {
                it.groupValues[1] + ":" + it.groupValues[2] + it.groupValues[3].let { seconds -> if (seconds.isBlank()) "" else ":$seconds" }
            }
            .replace(Regex("\\s*([年月日:.])\\s*"), "$1").trim() }
        if (texts.any { Regex("账单详情|交易详情|支付成功|付款成功|退款成功|收款成功|转账成功|Payment successful|Payment details|Transaction details", RegexOption.IGNORE_CASE).containsMatchIn(it) })
            return listOf(parseDetail(texts, defaultYear))
        var date = ""
        var dateHint = ""
        var dateReason = "缺少交易日期"
        val result = mutableListOf<LedgerEntry>()
        var pending: LedgerEntry? = null
        fun flush() {
            pending?.let { entry -> result.add(entry.copy(reason = listOf(entry.reason,
                if (entry.time.isBlank()) "缺少交易时间，请核对" else "").filter { it.isNotBlank() }.joinToString("；"))) }
            pending = null
        }
        for (text in texts) {
            val match = datePattern.find(text)
            if (match != null && match.range.first == 0) {
                dateHint = match.value
                val year = match.groupValues[1].toIntOrNull() ?: defaultYear
                date = if (year in 2000..2100) runCatching {
                    LocalDate.of(year, match.groupValues[2].toInt(), match.groupValues[3].toInt()).toString()
                }.getOrDefault("") else ""
                dateReason = "交易日期无效，请核对"
                val time = clock.find(text)
                if (pending != null && time != null && !signedMoney.containsMatchIn(text)) {
                    val reasons = pending!!.reason.split("；").filter {
                        it.isNotBlank() && it !in listOf("缺少交易日期", "交易日期无效，请核对")
                    }.toMutableList()
                    if (date.isBlank()) reasons.add(dateReason)
                    if (unfinished.containsMatchIn(text)) reasons.add("完成状态需要确认")
                    pending = pending!!.copy(date = date, dateHint = dateHint, time = clockValue(time), reason = reasons.joinToString("；"))
                } else flush()
                continue
            }
            if (Regex("^(?:合计|总计|今日合计|本月合计|收入合计|支出合计|余额|总资产|记一笔)").containsMatchIn(text) ||
                (Regex("(?:^|\\s)(?:出|支出)\\s*[￥¥]?\\d").containsMatchIn(text) && Regex("(?:入|收入)\\s*[￥¥]?\\d").containsMatchIn(text))) continue
            val time = clock.find(text)
            val amounts = signedMoney.findAll(text).toList()
            val category = categories.firstOrNull { text.startsWith(it) }
            if (time != null && pending != null && amounts.isEmpty() && category == null && !text.startsWith("退款")) {
                val merchant = text.substring(time.range.last + 1).trim(' ', '|', '丨', '｜')
                val reason = if (unfinished.containsMatchIn(text)) listOf(pending!!.reason, "完成状态需要确认").filter { it.isNotBlank() }.joinToString("；") else pending!!.reason
                pending = pending!!.copy(time = clockValue(time), merchant = merchant.ifBlank { pending!!.merchant }.take(200), reason = reason)
                continue
            }
            if (amounts.isNotEmpty() || category != null || text.startsWith("退款")) {
                flush()
                val amount = amounts.singleOrNull()
                val label = text.substringBefore(amount?.value ?: "\u0000").trim()
                pending = LedgerEntry(
                    amountFen = amount?.groupValues?.get(2)?.let(::money),
                    direction = amount?.let { if (it.groupValues[1] == "+") "收入" else "支出" }.orEmpty(),
                    kind = when { text.contains("退款") -> "退款"; text.contains("转账") -> "转账";
                        text.contains("红包") -> "红包"; amount?.groupValues?.get(1) == "+" -> "收入"; else -> "消费" },
                    date = date, dateHint = dateHint, time = time?.let(::clockValue).orEmpty(), category = category ?: "其他",
                    merchant = if (time != null) text.substring(time.range.last + 1).substringBefore(amount?.value ?: "\u0000").trim(' ', '|', '丨', '｜').take(200)
                        else if (category == null && label != "退款") label.take(200) else "",
                    reason = listOfNotNull(if (date.isBlank()) dateReason else null,
                        if (amount == null) "金额或方向不清楚" else null,
                        if (unfinished.containsMatchIn(text)) "完成状态需要确认" else null).joinToString("；"),
                )
            }
        }
        flush()
        return result
    }

    private fun parseDetail(rows: List<String>, confirmedYear: Int): LedgerEntry {
        val text = rows.joinToString("\n")
        val amounts = signedMoney.findAll(text).map { money(it.groupValues[2]) }.toMutableList()
        val labeled = Regex("(?:[￥¥]\\s*|(?:支付金额|付款金额|退款金额|转账金额|收款金额|金额)\\s*:?\\s*)((?:\\d{1,3}(?:,\\d{3})+|\\d+)(?:\\.\\d{1,2})?)(?![\\d.,])")
        amounts.addAll(labeled.findAll(text).map { money(it.groupValues[1]) })
        val amount = amounts.distinct().singleOrNull()
        val timeLabel = Regex("交易时间|支付时间|付款时间|收款时间|退款时间")
        val dateIndex = rows.indexOfFirst { timeLabel.containsMatchIn(it) }.takeIf { it >= 0 }
            ?: rows.indexOfFirst { it.contains("创建时间") }.takeIf { it >= 0 }
        val dateRows = if (dateIndex != null) listOf(rows[dateIndex]) + rows.drop(dateIndex + 1).take(2)
            .takeWhile { !Regex("时间|单号|交易号").containsMatchIn(it) && (datePattern.containsMatchIn(it) || clock.matches(it)) }
            else rows.filter { datePattern.containsMatchIn(it) }
        val dateText = dateRows.joinToString("\n")
        val dateMatch = datePattern.find(dateText)
        val year = dateMatch?.groupValues?.get(1)?.toIntOrNull() ?: confirmedYear
        val date = if (dateMatch != null && year in 2000..2100) runCatching {
            LocalDate.of(year, dateMatch.groupValues[2].toInt(), dateMatch.groupValues[3].toInt()).toString()
        }.getOrDefault("") else ""
        val refund = Regex("退款成功|退款到账|退款金额|^退款$", RegexOption.MULTILINE).containsMatchIn(text)
        val signed = signedMoney.findAll(text).map { if (it.groupValues[1] == "+") "收入" else "支出" }.distinct().toList()
        val direction = signed.singleOrNull() ?: when {
            signed.size > 1 -> ""
            refund || Regex("收款成功|收款到账|已收款|已领取红包").containsMatchIn(text) -> "收入"
            Regex("支付成功|付款成功|Payment successful", RegexOption.IGNORE_CASE).containsMatchIn(text) -> "支出"
            else -> ""
        }
        val completed = Regex("支付成功|付款成功|交易成功|退款成功|退款到账|收款成功|收款到账|已收款|已领取红包|Payment successful", RegexOption.IGNORE_CASE).containsMatchIn(text)
        val notCompleted = unfinished.containsMatchIn(text)
        val label = Regex("^(?:收款方|商户名称|商户|交易对方|对方账户)\\s*:?\\s*(.*)$")
        val merchantIndex = rows.indexOfFirst { label.containsMatchIn(it) }
        val merchant = if (merchantIndex >= 0) {
            label.find(rows[merchantIndex])!!.groupValues[1].ifBlank { rows.getOrNull(merchantIndex + 1).orEmpty() }
        } else rows.takeWhile { !signedMoney.containsMatchIn(it) && !labeled.containsMatchIn(it) }
            .lastOrNull { !Regex("详情|成功|微信支付|账单|Payment|Transaction", RegexOption.IGNORE_CASE).containsMatchIn(it) }.orEmpty()
        return LedgerEntry(amountFen = amount, direction = direction,
            kind = when { refund -> "退款"; text.contains("转账") -> "转账"; text.contains("红包") -> "红包"; direction == "收入" -> "收入"; else -> "消费" },
            date = date, dateHint = dateMatch?.value.orEmpty(), time = clock.find(dateText)?.let(::clockValue).orEmpty(), merchant = merchant.take(200),
            category = rows.firstOrNull { it in categories } ?: "其他",
            transactionId = Regex("(?:微信支付单号|微信交易号|交易单号|交易号)\\s*:?\\s*([A-Za-z0-9]{8,64})(?![A-Za-z0-9])").find(text)?.groupValues?.get(1).orEmpty(),
            reason = listOfNotNull(if (amount == null) "金额缺失或冲突" else null,
                if (direction.isBlank()) "收支方向不明" else null,
                if (date.isBlank()) "交易日期不完整" else null,
                if (!completed || notCompleted) "完成状态需要确认" else null).joinToString("；"))
    }
}
