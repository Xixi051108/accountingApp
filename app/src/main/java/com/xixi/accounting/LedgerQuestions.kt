package com.xixi.accounting

import java.time.LocalDate
import java.time.DayOfWeek
import java.time.temporal.TemporalAdjusters

data class QueryPlan(val start: String = "", val end: String = "", val category: String = "", val merchant: String = "",
    val direction: String = "", val kind: String = "", val metric: String = "summary")
data class QueryResolution(val plan: QueryPlan? = null, val message: String = "")
data class LedgerAnswer(val text: String, val entries: List<LedgerEntry>)

object LedgerQuestions {
    fun fromModel(fields: Map<String, String>): QueryResolution {
        if (fields.keys != setOf("start", "end", "category", "merchant", "direction", "kind", "metric", "clarification"))
            return QueryResolution(message = "模型未返回有效查询条件，请重新提问")
        if (fields.getValue("clarification").isNotBlank()) return QueryResolution(message = fields.getValue("clarification").take(300))
        val plan = QueryPlan(fields.getValue("start"), fields.getValue("end"), fields.getValue("category"), fields.getValue("merchant"),
            fields.getValue("direction"), fields.getValue("kind"), fields.getValue("metric"))
        return if (valid(plan)) QueryResolution(plan) else QueryResolution(message = "查询条件无效，请补充明确的日期和条件")
    }

    private fun valid(plan: QueryPlan): Boolean {
        if (plan.category.isNotBlank() && plan.category !in ScreenshotParser.categories ||
            plan.kind.isNotBlank() && plan.kind !in LedgerRules.kinds || plan.direction !in listOf("", "支出", "收入") ||
            plan.metric !in listOf("summary", "max", "count") || plan.merchant.length > 100) return false
        if (plan.start.isBlank() && plan.end.isBlank()) return true
        return runCatching {
            val start = LocalDate.parse(plan.start)
            val end = LocalDate.parse(plan.end)
            start.year in 2000..2100 && end.year in 2000..2100 && start <= end
        }.getOrDefault(false)
    }

    fun local(question: String, today: LocalDate, merchants: List<String> = emptyList()): QueryResolution {
        if (question.isBlank() || question.length > 200) return QueryResolution(message = "请输入 200 字以内的完整查账问题")
        if (Regex("删除|修改|新增|清空|记一笔|改成").containsMatchIn(question))
            return QueryResolution(message = "查账不会修改记录，请在账本页面新增、核对、编辑或删除")
        if (Regex("对比|比较|分别|预算|预测|平均|大于|小于|超过|少于").containsMatchIn(question))
            return QueryResolution(message = "请每次查询一个日期范围和一组条件；当前支持汇总、明细、笔数和最大一笔")
        val merchant = Regex("[「“\"]([^」”\"]{1,100})[」”\"]").find(question)?.groupValues?.get(1)
            ?: Regex("商户[:：\\s]*([^，。?？]+?)(?:的?(?:消费|支出|收入|账单|明细|花|退款)|[，。?？]|$)").find(question)?.groupValues?.get(1)?.trim()
            ?: merchants.filter { it.isNotBlank() && question.contains(it) }.maxByOrNull { it.length }.orEmpty()
        val filterText = if (merchant.isBlank()) question else question.replace(merchant, "")
        val categories = ScreenshotParser.categories.filter { filterText.contains(it) }
        if (categories.size > 1) return QueryResolution(message = "请一次查询一个分类，例如“本月餐饮花了多少”")
        val category = categories.singleOrNull() ?: if (Regex("吃饭|饭钱|早餐|午餐|晚餐").containsMatchIn(filterText)) "餐饮" else ""
        if (!Regex("多少|哪些|明细|账单|收支|消费|支出|收入|退款|花|几笔|最大|最高|最多|汇总|统计").containsMatchIn(filterText) && category.isBlank())
            return QueryResolution(message = "暂未理解，可试“本月餐饮花了多少”或“昨天的支出明细”")
        val relative = Regex("今天|昨天|前天|本周|这周|上周|本月|这个月|这月|上个月|上月|今年|去年|全部|所有|历史|最近\\d{1,3}天|近\\d{1,3}天").findAll(question).toList()
        if (relative.size > 1) return QueryResolution(message = "请一次指定一个日期范围，比较时可分别查询")
        var start: LocalDate? = null
        var end: LocalDate? = null
        val dateWord = relative.singleOrNull()?.value
        val absolutePattern = Regex("\\d{4}-\\d{2}-\\d{2}|(?:(?:\\d{4})年)?\\d{1,2}月(?:\\d{1,2}日)?")
        if (relative.isNotEmpty() && absolutePattern.containsMatchIn(question))
            return QueryResolution(message = "请不要混用相对日期和具体日期，指定一个明确范围即可")
        val remainder = absolutePattern.replace(relative.fold(filterText) { text, match -> text.replace(match.value, "") }, "")
            .replace(Regex("商户[:：\\s]*|[「」“”\"]"), "")
            .replace(Regex((ScreenshotParser.categories + listOf("内部转账", "明细", "账单", "收支", "消费", "支出", "收入", "收款", "退款", "转账", "红包",
                "多少钱", "多少笔", "多少", "有哪些", "有什么", "哪些", "一共", "总共", "总计", "总额", "合计", "花了", "花费", "花", "几笔", "最大", "最高", "最多", "一笔", "金额",
                "统计", "汇总", "记录", "查一下", "查询", "看看", "看一下", "查查", "请", "帮我", "的", "是", "到", "至", "吃饭", "饭钱", "早餐", "午餐", "晚餐"))
                .sortedByDescending { it.length }.joinToString("|") { Regex.escape(it) }), "").replace(Regex("[\\s，。?？!！、：:~～—]"), "")
        if (remainder.isNotBlank()) return QueryResolution(message = "部分条件暂未理解。查商户请写“本月商户：名称的消费明细”；金额比较请分次核对明细。")
        val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        when (dateWord) {
            "今天", "昨天", "前天" -> { start = today.minusDays(when (dateWord) { "昨天" -> 1; "前天" -> 2; else -> 0 }); end = start }
            "本周", "这周" -> { start = monday; end = monday.plusDays(6) }
            "上周" -> { start = monday.minusWeeks(1); end = monday.minusDays(1) }
            "本月", "这个月", "这月" -> { start = today.withDayOfMonth(1); end = start.withDayOfMonth(start.lengthOfMonth()) }
            "上个月", "上月" -> { start = today.minusMonths(1).withDayOfMonth(1); end = start.withDayOfMonth(start.lengthOfMonth()) }
            "今年", "去年" -> { start = LocalDate.of(today.year - if (dateWord == "去年") 1 else 0, 1, 1); end = start.withMonth(12).withDayOfMonth(31) }
            "全部", "所有", "历史" -> Unit
            else -> {
                if (dateWord != null) {
                    val days = Regex("\\d+").find(dateWord)!!.value.toLong()
                    if (days == 0L) return QueryResolution(message = "查询天数至少为 1 天")
                    start = today.minusDays(days - 1); end = today
                } else {
                    val iso = Regex("\\d{4}-\\d{2}-\\d{2}").findAll(question).map { it.value }.toList()
                    val chinese = Regex("(?:(\\d{4})年)?(\\d{1,2})月(?:(\\d{1,2})日)?").findAll(question).toList()
                    try {
                        if (iso.isNotEmpty() && iso.size <= 2) {
                            start = LocalDate.parse(iso.first()); end = LocalDate.parse(iso.last())
                        } else if (chinese.isNotEmpty() && chinese.size <= 2) {
                            val first = chinese.first()
                            val year = first.groupValues[1].toIntOrNull() ?: today.year
                            start = LocalDate.of(year, first.groupValues[2].toInt(), first.groupValues[3].toIntOrNull() ?: 1)
                            val last = chinese.last()
                            end = LocalDate.of(last.groupValues[1].toIntOrNull() ?: year, last.groupValues[2].toInt(), last.groupValues[3].toIntOrNull() ?: 1)
                            if (last.groupValues[3].isBlank()) end = end.withDayOfMonth(end.lengthOfMonth())
                        } else return QueryResolution(message = "请写明日期范围，例如“本月”“上周”或“全部账单”")
                    } catch (_: Exception) { return QueryResolution(message = "日期无效，请使用完整有效日期") }
                }
            }
        }
        val kind = when { filterText.contains("内部转账") -> "内部转账"; filterText.contains("退款") -> "退款";
            filterText.contains("转账") -> "转账"; filterText.contains("红包") -> "红包"; else -> "" }
        val direction = when {
            kind == "退款" -> ""
            Regex("收入|收款").containsMatchIn(filterText) && !Regex("支出|花|消费").containsMatchIn(filterText) -> "收入"
            Regex("支出|花|消费").containsMatchIn(filterText) && !filterText.contains("收入") -> "支出"
            else -> ""
        }
        val plan = QueryPlan(start?.toString().orEmpty(), end?.toString().orEmpty(), category, merchant, direction, kind,
            when { Regex("最大|最高|最多").containsMatchIn(filterText) -> "max"; Regex("几笔|多少笔").containsMatchIn(filterText) -> "count"; else -> "summary" })
        return if (valid(plan)) QueryResolution(plan) else QueryResolution(message = "日期或查询条件无效，请重新提问")
    }

    fun answer(plan: QueryPlan, entries: List<LedgerEntry>): LedgerAnswer {
        require(valid(plan)) { "查询条件无效" }
        val matches = entries.filter { it.posted && (it.kind != "内部转账" || plan.kind == "内部转账") &&
            (plan.start.isBlank() || it.date >= plan.start) && (plan.end.isBlank() || it.date <= plan.end) &&
            (plan.category.isBlank() || it.category == plan.category) &&
            (plan.merchant.isBlank() || it.merchant.contains(plan.merchant, ignoreCase = true)) &&
            (plan.kind.isBlank() || it.kind == plan.kind) &&
            (plan.direction.isBlank() || it.direction == plan.direction || (plan.direction == "支出" && it.kind == "退款"))
        }.sortedWith(compareByDescending<LedgerEntry> { it.date }.thenByDescending { it.time })
        val totals = LedgerRules.totals(matches)
        val scope = if (plan.start.isBlank()) "全部日期" else "${plan.start} 至 ${plan.end}"
        val text = "$scope${if (plan.category.isBlank()) "" else " · ${plan.category}"}${if (plan.merchant.isBlank()) "" else " · 商户包含「${plan.merchant}」"}${if (plan.kind.isBlank()) "" else " · ${plan.kind}"}${if (plan.direction.isBlank()) "" else " · ${plan.direction}"}\n" +
            if (matches.isEmpty()) "该范围内没有符合条件的已入账记录。仅查询已导入账本，待确认记录不计入。"
            else "找到 ${matches.size} 笔已入账记录。\n支出 ¥${LedgerRules.formatMoney(totals.expenseFen)}，退款 ¥${LedgerRules.formatMoney(totals.refundFen)}，净支出 ¥${LedgerRules.formatMoney(totals.netExpenseFen)}，收入 ¥${LedgerRules.formatMoney(totals.incomeFen)}。"
        if (matches.isNotEmpty() && plan.metric == "max") {
            val largest = (if (plan.direction == "支出") matches.filter { it.direction == "支出" } else matches).maxByOrNull { it.amountFen ?: 0 }
            if (largest != null) return LedgerAnswer("$scope · 符合条件的最大一笔：¥${LedgerRules.formatMoney(largest.amountFen!!)}\n${largest.date} ${largest.time} ${largest.merchant}。仅查询已入账记录。", listOf(largest))
        }
        if (plan.kind == "内部转账" && matches.isNotEmpty()) return LedgerAnswer("$scope · 找到 ${matches.size} 笔已入账内部转账，排除于收入与消费统计。", matches)
        return LedgerAnswer(text, matches)
    }
}
