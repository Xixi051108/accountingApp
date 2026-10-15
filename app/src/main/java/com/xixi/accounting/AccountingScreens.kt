package com.xixi.accounting

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.time.LocalDate
import java.time.YearMonth

@Composable
private fun PaperCard(content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, Outline.copy(alpha = .65f)),
        colors = CardDefaults.cardColors(containerColor = Sheet)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
private fun Notice(text: String) {
    Surface(color = Honey.copy(alpha = .5f), shape = RoundedCornerShape(14.dp)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AppGlyph("shield", Modifier.size(17.dp))
            Text(text, fontSize = 12.sp, lineHeight = 19.sp, color = Ink, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun MonthSelector(value: YearMonth, change: (YearMonth) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = { change(value.minusMonths(1)) }, enabled = value.year > 2000) { Text("上月", fontSize = 12.sp) }
        Text("${value.year} 年 ${value.monthValue} 月", Modifier.weight(1f), fontSize = 14.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center, fontWeight = FontWeight.Medium)
        TextButton(onClick = { change(value.plusMonths(1)) }, enabled = value.year < 2100) { Text("下月", fontSize = 12.sp) }
    }
}

@Composable
private fun Metric(label: String, value: Long, color: Color = Ink, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(label, fontSize = 11.sp, color = Muted)
        Text(LedgerRules.formatMoney(value), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = color,
            style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = "tnum"))
    }
}

@Composable
private fun MonthReceipt(month: YearMonth, totals: Totals) {
    Surface(shape = RoundedCornerShape(24.dp), color = Honey) {
        Column(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Honey, Color(0xFFF8EED8)))).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${month.monthValue} 月账本  /  ${month.year}", fontSize = 12.sp, color = Amber, fontWeight = FontWeight.Medium)
                    Text("本月净支出", fontSize = 12.sp, color = Muted)
                    MoneyText(totals.netExpenseFen, size = 26)
                }
                FloatingIllustration(R.drawable.receipt_illustration, Modifier.size(100.dp))
            }
            ReceiptRule()
            Row(Modifier.fillMaxWidth()) {
                Metric("总支出", totals.expenseFen, modifier = Modifier.weight(1f))
                Metric("退款", totals.refundFen, modifier = Modifier.weight(1f))
                Metric("收入", totals.incomeFen, color = Green, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ActionCard(title: String, caption: String, icon: String, primary: Boolean, enabled: Boolean, modifier: Modifier, action: () -> Unit) {
    Card(onClick = action, enabled = enabled, modifier = modifier, shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = if (primary) Amber else Sheet)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 78.dp).padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AppGlyph(icon, color = if (primary) Color(0xFFFFE7BC) else Amber)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, fontSize = 13.sp, color = if (primary) Color.White else Ink, fontWeight = FontWeight.Medium)
                Text(caption, fontSize = 11.sp, color = if (primary) Color(0xFFF4E2C6) else Muted)
            }
        }
    }
}

@Composable
internal fun LedgerScreen(entries: List<LedgerEntry>, busy: Boolean, message: String, import: () -> Unit,
    manual: () -> Unit, edit: (LedgerEntry) -> Unit) {
    var mode by rememberSaveable { mutableStateOf("本月") }
    var monthText by rememberSaveable { mutableStateOf(YearMonth.from(today()).toString()) }
    val month = YearMonth.parse(monthText)
    var search by rememberSaveable { mutableStateOf("") }
    val pending = entries.count { !it.posted }
    val totals = LedgerRules.totals(entries.filter { it.date.startsWith(monthText) })
    val displayed = entries.filter { entry ->
        (when (mode) { "待确认" -> !entry.posted; "本月" -> entry.posted && entry.date.startsWith(monthText); else -> entry.posted }) &&
            (search.isBlank() || "${entry.merchant} ${entry.category} ${entry.kind} ${entry.date}".contains(search.trim(), ignoreCase = true))
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { PageHeading("我的账本", "把日常收支，慢慢理清。", "本机保存") }
        item { MonthReceipt(month, totals) }
        item { BoxWithConstraints(Modifier.fillMaxWidth()) {
            if (maxWidth < 330.dp || LocalDensity.current.fontScale > 1.2f) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActionCard("导入账单截图", "选择截图，自动整理", "scan", true, !busy, Modifier.fillMaxWidth(), import)
                    ActionCard("手动记一笔", "随手补充一笔", "add", false, !busy, Modifier.fillMaxWidth(), manual)
                }
            } else Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ActionCard("导入账单截图", "选择截图，自动整理", "scan", true, !busy, Modifier.weight(1.25f), import)
                ActionCard("手动记一笔", "随手补充一笔", "add", false, !busy, Modifier.weight(1f), manual)
            }
        } }
        if (pending > 0) item {
            Surface(onClick = { mode = "待确认" }, color = Honey.copy(alpha = .45f), shape = RoundedCornerShape(16.dp)) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    IconTile("receipt", modifier = Modifier.size(34.dp))
                    Column(Modifier.weight(1f)) { Text("$pending 笔等待核对", fontSize = 14.sp, fontWeight = FontWeight.Medium); Text("补全信息后计入统计", fontSize = 11.sp, color = Muted) }
                    AppGlyph("arrow", Modifier.size(18.dp))
                }
            }
        }
        if (!message.startsWith("选择支付详情")) item {
            if (busy || message.startsWith("导入") || message.contains("失败")) Notice(message)
            else Text(message, color = Green, fontSize = 11.sp)
        }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("收支明细", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text("${displayed.size} 笔", fontSize = 12.sp, color = Muted)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("本月", "全部", "待确认").forEach { option ->
                    FilterChip(selected = mode == option, onClick = { mode = option },
                        label = { Text(if (option == "待确认" && pending > 0) "$option $pending" else option, fontSize = 12.sp) },
                        border = null, shape = RoundedCornerShape(10.dp),
                        colors = FilterChipDefaults.filterChipColors(containerColor = Sheet, selectedContainerColor = Sand, selectedLabelColor = Ink))
                }
            }
            if (mode == "本月") MonthSelector(month) { monthText = it.toString() }
            OutlinedTextField(search, { search = it.take(100) }, label = { Text("搜索商户、分类或日期", fontSize = 12.sp) },
                leadingIcon = { AppGlyph("search", Modifier.size(18.dp), Muted) }, singleLine = true,
                shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Outline, unfocusedContainerColor = Sheet))
        }
        if (displayed.isEmpty()) item { PaperCard {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                IconTile("receipt")
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(if (mode == "待确认") "暂时没有待确认记录" else "从第一笔开始", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Text(if (mode == "待确认") "需要你核对的账单会放在这里。" else "导入一张截图，留下日常的收支。", fontSize = 12.sp, color = Muted)
                }
            }
        } }
        displayed.groupBy { it.date }.forEach { (date, rows) ->
            item(key = "date:$date") {
                val parsed = runCatching { LocalDate.parse(date) }.getOrNull()
                val label = if (parsed != null) "${parsed.monthValue}月${parsed.dayOfMonth}日" else "日期待确认"
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(label, fontSize = 12.sp, color = Muted)
                    if (mode != "待确认") Text("支出 ¥${LedgerRules.formatMoney(LedgerRules.totals(rows).expenseFen)}", fontSize = 11.sp, color = Muted)
                }
            }
            items(rows, key = { it.id }) { entry -> EntryCard(entry, !busy) { edit(entry) } }
        }
        item { Text("仅统计已入账记录 · 退款抵扣支出 · 内部转账不计收支", color = Muted, fontSize = 11.sp, modifier = Modifier.padding(vertical = 6.dp)) }
    }
}

@Composable
private fun EntryCard(entry: LedgerEntry, enabled: Boolean = true, open: (() -> Unit)? = null) {
    val content: @Composable ColumnScope.() -> Unit = {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconTile(if (entry.kind in listOf("退款", "内部转账")) entry.kind else entry.category, if (entry.direction == "收入") Green else Amber)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(entry.merchant.ifBlank { entry.category }, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Text("${entry.date.ifBlank { entry.dateHint }} ${entry.time} · ${entry.category}", color = Muted, fontSize = 11.sp)
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(entry.amountFen?.let { "${if (entry.direction == "收入") "+" else if (entry.direction == "支出") "−" else ""}${LedgerRules.formatMoney(it)}" }
                    ?: "待补金额", color = if (entry.direction == "收入") Green else Ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = "tnum"))
                Text(if (entry.posted) entry.kind else "待确认", color = if (entry.posted) Muted else Amber, fontSize = 10.sp)
            }
        }
        if (!entry.posted) Text(entry.reason, color = Amber, fontSize = 11.sp, lineHeight = 17.sp)
    }
    if (open != null) Card(onClick = open, enabled = enabled, modifier = Modifier.fillMaxWidth(), border = BorderStroke(1.dp, Outline.copy(alpha = .6f)),
        colors = CardDefaults.cardColors(containerColor = Sheet), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    } else PaperCard(content)
}

@Composable
internal fun StatisticsScreen(entries: List<LedgerEntry>) {
    var monthText by rememberSaveable { mutableStateOf(YearMonth.from(today()).toString()) }
    val month = YearMonth.parse(monthText)
    val monthly = entries.filter { it.posted && it.date.startsWith(monthText) }
    val totals = LedgerRules.totals(monthly)
    val categories = monthly.filter { it.direction == "支出" && it.kind != "内部转账" }
        .groupBy { it.category }.mapValues { (_, rows) -> rows.sumOf { it.amountFen ?: 0 } }.toList().sortedByDescending { it.second }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { PageHeading("收支洞察", "看见每一笔，也看清每个月。") }
        item { MonthSelector(month) { monthText = it.toString() } }
        item { PaperCard {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) { Text("本月净支出", fontSize = 12.sp, color = Muted); MoneyText(totals.netExpenseFen, size = 25) }
                IconTile("receipt")
            }
            ReceiptRule()
            Row(Modifier.fillMaxWidth()) {
                Metric("支出", totals.expenseFen, modifier = Modifier.weight(1f)); Metric("退款", totals.refundFen, modifier = Modifier.weight(1f)); Metric("收入", totals.incomeFen, Green, Modifier.weight(1f))
            }
        } }
        item { PaperCard {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("支出构成", fontWeight = FontWeight.SemiBold); Text("${monthly.count { it.kind != "内部转账" }} 笔收支", color = Muted, fontSize = 11.sp) }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                ExpenseRing(categories, Modifier.weight(1f))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    if (categories.isEmpty()) Text("入账后，会在这里\n看到分类构成。", fontSize = 12.sp, color = Muted)
                    categories.take(3).forEachIndexed { index, (category, fen) ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(Modifier.size(7.dp).background(ChartColors[index], CircleShape))
                            Column { Text(category, fontSize = 12.sp, color = Muted); Text("¥${LedgerRules.formatMoney(fen)}", fontSize = 13.sp, fontWeight = FontWeight.Medium) }
                        }
                    }
                }
            }
        } }
        item { Text("分类明细", fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
        if (categories.isEmpty()) item { Notice("本月还没有支出。导入账单后，分类和统计会一起更新。") }
        items(categories, key = { it.first }) { (category, fen) ->
            val index = categories.indexOfFirst { it.first == category }
            PaperCard {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    IconTile(category, ChartColors[index % ChartColors.size])
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(category, fontSize = 14.sp); Text("¥${LedgerRules.formatMoney(fen)}", fontSize = 14.sp, fontWeight = FontWeight.Medium) }
                        LinearProgressIndicator(progress = { if (totals.expenseFen == 0L) 0f else fen.toFloat() / totals.expenseFen }, modifier = Modifier.fillMaxWidth().height(4.dp), color = ChartColors[index % ChartColors.size], trackColor = Sand)
                    }
                }
            }
        }
        item { Text("分类展示支出，退款单列抵扣；待确认和内部转账不计入。", color = Muted, fontSize = 11.sp) }
    }
}

@Composable
internal fun QuestionsScreen(model: AccountingViewModel, entries: List<LedgerEntry>, busy: Boolean) {
    val turn by model.turn.collectAsState()
    val cloud by model.cloud.collectAsState()
    var question by rememberSaveable { mutableStateOf("") }
    var shown by remember(turn) { mutableIntStateOf(20) }
    val answer = remember(turn, entries) { turn?.resolution?.plan?.let { LedgerQuestions.answer(it, entries) } }
    LazyColumn(Modifier.fillMaxSize().imePadding(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { PageHeading("账本问答", "有疑问，就从账本里找答案。", if (cloud) "联网理解已开" else "本地查账") }
        item { Surface(shape = RoundedCornerShape(22.dp), color = Honey.copy(alpha = .55f)) {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 10.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("钱花在哪，\n问一问就知道。", fontSize = 18.sp, lineHeight = 26.sp, fontWeight = FontWeight.Medium)
                    Text("金额来自已入账记录，\n每个结果都有明细。", fontSize = 12.sp, lineHeight = 19.sp, color = Muted)
                }
                FloatingIllustration(R.drawable.fox_illustration, Modifier.size(112.dp))
            }
        } }
        item { PaperCard {
            OutlinedTextField(question, { question = it.take(200) }, label = { Text("想查点什么？", fontSize = 13.sp) },
                placeholder = { Text("例如：这个月餐饮花了多少", fontSize = 13.sp) }, minLines = 2, maxLines = 5,
                shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth(), textStyle = MaterialTheme.typography.bodyMedium)
            Button(onClick = { model.ask(question) }, enabled = !busy && question.isNotBlank(), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                Text(if (busy) "处理中…" else "查一查", fontSize = 14.sp); Spacer(Modifier.width(8.dp)); AppGlyph("arrow", Modifier.size(16.dp), Color.White)
            }
        } }
        item {
            Text("试着问问", fontSize = 13.sp, color = Muted, modifier = Modifier.padding(bottom = 8.dp))
            listOf("这个月餐饮花了多少", "昨天的支出明细", "上周有哪些退款", "全部账单最大一笔支出").chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { example ->
                        OutlinedButton(onClick = { question = example; model.ask(example) }, enabled = !busy, modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp), shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, Outline)) {
                            Text(example, fontSize = 12.sp, lineHeight = 17.sp)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
        if (turn != null) {
            item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Surface(color = Honey, shape = RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp)) { Text(turn!!.question, Modifier.padding(14.dp), fontSize = 13.sp) }
            } }
            item { PaperCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppGlyph("chat", Modifier.size(18.dp)); Text(if (turn!!.viaCloud) "DeepSeek 理解 · 本地计算" else "本地账本查询", color = Amber, fontSize = 11.sp)
                }
                Text(answer?.text ?: turn!!.resolution.message, fontSize = 14.sp, lineHeight = 23.sp)
            } }
            if (answer != null && answer.entries.isNotEmpty()) {
                item { Text("对应明细 · ${answer.entries.size} 笔", fontSize = 13.sp, fontWeight = FontWeight.Medium) }
                items(answer.entries.take(shown), key = { it.id }) { EntryCard(it) }
                if (answer.entries.size > shown) item { TextButton(onClick = { shown += 20 }, modifier = Modifier.fillMaxWidth()) { Text("显示更多明细") } }
            }
        }
        item { Text("每次请写完整条件，复杂比较可分次查询。结果随账本更新。", color = Muted, fontSize = 11.sp) }
    }
}

@Composable
internal fun SettingsScreen(model: AccountingViewModel, count: Int, busy: Boolean, message: String) {
    val enabled by model.cloud.collectAsState()
    val hasKey by model.hasKey.collectAsState()
    var key by remember { mutableStateOf("") }
    var clear by remember { mutableStateOf(false) }
    var privacyExpanded by rememberSaveable { mutableStateOf(false) }
    var cloudExpanded by rememberSaveable { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize().imePadding(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { PageHeading("设置", "简单记录，安心保管。") }
        item { PaperCard {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                IconTile("shield", Green, Modifier.size(50.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("我的本地账本", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text("已保存 $count 条记录", fontSize = 12.sp, color = Muted)
                }
                SmallBadge("本机保存", Green)
            }
        } }
        if (Regex("密钥|设置|清除|联网").containsMatchIn(message)) item { Notice(message) }
        item { PaperCard {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconTile("shield")
                Column(Modifier.weight(1f)) { Text("隐私与数据", fontSize = 14.sp, fontWeight = FontWeight.Medium); Text("截图本地识别，原图不另存", fontSize = 11.sp, color = Muted) }
                TextButton(onClick = { privacyExpanded = !privacyExpanded }) { Text(if (privacyExpanded) "收起" else "详情", fontSize = 12.sp) }
            }
            AnimatedVisibility(privacyExpanded) { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                HorizontalDivider(color = Outline)
                Text("图片在手机上识别，处理后释放，不自动上传或另存。账本保存在本机，不自动云备份。相册原图由你保管。", fontSize = 12.sp, color = Muted)
                Text("可在账本中选择图片，或从相册分享；每次最多 10 张，长截图建议分段。旧版采集候选不会自动变成正式账单。", fontSize = 12.sp, color = Muted)
            } }
        } }
        item { PaperCard {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconTile("chat", Green)
                Column(Modifier.weight(1f)) { Text("DeepSeek 问题理解", fontSize = 14.sp, fontWeight = FontWeight.Medium); Text(if (enabled) "已开启 · 仅在需要时联网" else "可选功能 · 常用查账可离线使用", fontSize = 11.sp, color = Muted) }
                TextButton(onClick = { cloudExpanded = !cloudExpanded }) { Text(if (cloudExpanded) "收起" else "配置", fontSize = 12.sp) }
            }
            AnimatedVisibility(cloudExpanded) { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                HorizontalDivider(color = Outline)
                Text("开启后，本地无法理解的问题会发送给 DeepSeek，并附当前日期和分类选项。不发送截图或整本账本；问题里写入的商户或金额会随问题发送。费用由你的 DeepSeek 账户承担。", fontSize = 12.sp, color = Muted)
                SmallBadge(if (hasKey) "密钥已加密保存" else "尚未配置密钥")
                OutlinedTextField(key, { key = it.take(512) }, label = { Text(if (hasKey) "填写新密钥以替换" else "DeepSeek API 密钥") },
                    visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
                Button(onClick = { model.saveKey(key) { key = "" } }, enabled = !busy && key.isNotBlank(), shape = RoundedCornerShape(12.dp)) { Text("加密保存密钥") }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("允许联网理解查账问题", Modifier.weight(1f), fontSize = 13.sp)
                    Switch(checked = enabled, onCheckedChange = model::enableCloud, enabled = !busy && hasKey)
                }
                TextButton(onClick = model::clearKey, enabled = !busy && hasKey) { Text("清除密钥并关闭联网", fontSize = 12.sp) }
            } }
        } }
        item { PaperCard {
            Text("数据管理", fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text("清除账单、候选、导入记录、分类规则、旧诊断和密钥，相册原图保留。此操作无法恢复。", fontSize = 12.sp, color = Muted)
            OutlinedButton(onClick = { clear = true }, enabled = !busy, shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("清除全部本机数据", fontSize = 12.sp) }
        } }
        item { Text("${stringResource(R.string.app_name)} · ${BuildConfig.VERSION_NAME}", color = Muted, fontSize = 11.sp, modifier = Modifier.fillMaxWidth().padding(8.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
    }
    if (clear) AlertDialog(onDismissRequest = { if (!busy) clear = false }, title = { Text("清除全部本机数据？") },
        text = { Text("账单和密钥将永久删除，无法恢复。相册里的原图会保留。") },
        confirmButton = { TextButton(onClick = { model.clearAll { clear = false } }, enabled = !busy) { Text("确认清除") } },
        dismissButton = { TextButton(onClick = { clear = false }, enabled = !busy) { Text("取消") } })
}

@Composable
internal fun ImportDialog(count: Int, dismiss: () -> Unit, start: (Int?) -> Unit) {
    var year by rememberSaveable { mutableStateOf(today().year.toString()) }
    var error by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = dismiss, title = { Text("识别 $count 张账单截图") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("清楚的交易自动入账；模糊、缺失或疑似重复的先确认。每日合计不会再记一笔。")
            OutlinedTextField(year, { year = it.filter(Char::isDigit).take(4); error = "" }, label = { Text("截图年份（可选）") },
                placeholder = { Text("例如 ${today().year}") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
            Text("截图未显示年份时默认使用当年，可修改为其他年份。", color = Muted)
            if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
        }
    }, confirmButton = { TextButton(onClick = {
        val value = year.toIntOrNull()
        if (year.isNotBlank() && (value == null || value !in 2000..2100)) error = "请输入 2000–2100 之间的年份"
        else start(value)
    }) { Text("开始识别") } }, dismissButton = { TextButton(onClick = dismiss) { Text("取消") } })
}

@Composable
private fun Choice(label: String, value: String, options: List<String>, change: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text(label, color = Muted, style = MaterialTheme.typography.labelMedium)
        Box {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) { Text(value.ifBlank { "请选择" }) }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { option -> DropdownMenuItem(text = { Text(option) }, onClick = { change(option); expanded = false }) }
            }
        }
    }
}

@Composable
internal fun EntryEditor(entry: LedgerEntry, entries: List<LedgerEntry>, busy: Boolean, isNew: Boolean, message: String,
    dismiss: () -> Unit, save: (LedgerEntry, Boolean) -> Unit, delete: () -> Unit) {
    var amount by rememberSaveable(entry.id) { mutableStateOf(entry.amountFen?.let(LedgerRules::formatMoney).orEmpty()) }
    var date by rememberSaveable(entry.id) { mutableStateOf(entry.date) }
    var time by rememberSaveable(entry.id) { mutableStateOf(entry.time) }
    var merchant by rememberSaveable(entry.id) { mutableStateOf(entry.merchant) }
    var category by rememberSaveable(entry.id) { mutableStateOf(entry.category) }
    var direction by rememberSaveable(entry.id) { mutableStateOf(entry.direction) }
    var kind by rememberSaveable(entry.id) { mutableStateOf(entry.kind) }
    var rememberCategory by rememberSaveable(entry.id) { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var deleteConfirm by remember { mutableStateOf(false) }
    var duplicateConfirmed by rememberSaveable(entry.id) { mutableStateOf(false) }
    val possibleDuplicates = entries.filter { it.id != entry.id && it.posted &&
        ((entry.transactionId.isNotBlank() && entry.transactionId == it.transactionId) ||
            (it.amountFen == ScreenshotParser.money(amount) && it.date == date.trim() && it.time.take(5) == time.trim().take(5))) }
    Dialog(onDismissRequest = dismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth(.95f).widthIn(max = 600.dp).fillMaxHeight(.94f), shape = RoundedCornerShape(24.dp), color = Sheet) {
            Column(Modifier.imePadding()) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (isNew) "手动记一笔" else if (entry.posted) "编辑账单" else "核对后入账", Modifier.weight(1f), fontSize = 18.sp, fontWeight = FontWeight.Medium)
                    TextButton(onClick = dismiss, enabled = !busy) { Text("取消") }
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (entry.reason.isNotBlank()) Notice(entry.reason)
                    if (entry.dateHint.isNotBlank()) Text("截图中的日期：${entry.dateHint}", color = Muted)
                    if (!isNew) Text("只有你点击确认保存，才会写入修改。", color = Muted)
                    OutlinedTextField(amount, { amount = it.take(20); error = "" }, label = { Text("金额（元）") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    OutlinedTextField(date, { date = it.take(10); error = "" }, label = { Text("交易日期 YYYY-MM-DD") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(time, { time = it.take(8); error = "" }, label = { Text("交易时间 HH:mm（可留空）") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(merchant, { merchant = it.take(200) }, label = { Text("商户或备注") }, modifier = Modifier.fillMaxWidth(), maxLines = 3)
                    Choice("收支方向", direction, listOf("支出", "收入")) { direction = it; if (kind in listOf("消费", "收入")) kind = if (it == "收入") "收入" else "消费" }
                    Choice("交易类型", kind, LedgerRules.kinds) { kind = it; when (it) { "退款", "收入" -> direction = "收入"; "消费" -> direction = "支出" } }
                    Choice("分类", category, ScreenshotParser.categories) { category = it }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(rememberCategory, { rememberCategory = it }, enabled = merchant.isNotBlank())
                        Text("记住该商户的分类（只影响之后导入的“其他”分类）", Modifier.weight(1f))
                    }
                    if (possibleDuplicates.isNotEmpty()) {
                        Text("可能重复的已入账记录", color = Amber, fontWeight = FontWeight.Medium)
                        possibleDuplicates.take(3).forEach { Text("${it.date} ${it.time} · ${it.merchant} · ¥${it.amountFen?.let(LedgerRules::formatMoney)}", color = Muted) }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(duplicateConfirmed, { duplicateConfirmed = it })
                            Text("已核对，这是一笔独立交易，仍要入账", Modifier.weight(1f))
                        }
                    }
                    if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
                    Text(message, color = Muted, style = MaterialTheme.typography.bodySmall)
                }
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Button(onClick = {
                        val changed = entry.copy(amountFen = ScreenshotParser.money(amount), date = date.trim(), time = time.trim(),
                            merchant = merchant.trim(), category = category, direction = direction, kind = kind)
                        error = LedgerRules.validation(changed).orEmpty()
                        if (error.isBlank() && possibleDuplicates.isNotEmpty() && !duplicateConfirmed) error = "请先核对上面的疑似重复记录"
                        if (error.isBlank()) save(changed, rememberCategory)
                    }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(if (entry.posted) "确认保存修改" else "确认入账") }
                    if (!isNew) TextButton(onClick = { deleteConfirm = true }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(if (entry.posted) "删除这笔账单" else "放弃这条候选") }
                }
            }
        }
    }
    if (deleteConfirm) AlertDialog(onDismissRequest = { if (!busy) deleteConfirm = false }, title = { Text(if (entry.posted) "删除这笔账单？" else "放弃这条候选？") },
        text = { Text("此操作无法恢复，统计会同时更新。") }, confirmButton = { TextButton(onClick = delete, enabled = !busy) { Text("确认删除") } },
        dismissButton = { TextButton(onClick = { deleteConfirm = false }, enabled = !busy) { Text("取消") } })
}
