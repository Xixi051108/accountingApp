package com.xixi.accounting

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal val Honey = Color(0xFFF2E3BF)
internal val Outline = Color(0xFFE5DFD3)
internal val ChartColors = listOf(Color(0xFFB87B39), Color(0xFF7B9075), Color(0xFFC9AE7D), Color(0xFF9D8491), Color(0xFF8C9B9C))

@Composable
internal fun AppGlyph(name: String, modifier: Modifier = Modifier, color: Color = Amber) {
    Canvas(modifier.size(22.dp)) {
        scale(size.width / 24f, size.height / 24f, pivot = Offset.Zero) {
            val stroke = Stroke(1.6f, cap = StrokeCap.Round)
            fun line(x: Float, y: Float, x2: Float, y2: Float) = drawLine(color, Offset(x, y), Offset(x2, y2), 1.6f, StrokeCap.Round)
            when (name) {
                "scan" -> {
                    for ((x, y, dx, dy) in listOf(listOf(3f, 8f, 0f, -5f), listOf(16f, 3f, 5f, 0f), listOf(21f, 16f, 0f, 5f), listOf(8f, 21f, -5f, 0f))) {
                        line(x, y, x + dx, y + dy)
                        if (dx == 0f) line(x, y + dy, x + if (x < 12) 5f else -5f, y + dy)
                        else line(x + dx, y, x + dx, y + if (y < 12) 5f else -5f)
                    }
                    drawRoundRect(color, Offset(7f, 7f), Size(10f, 10f), CornerRadius(2f), style = stroke)
                    line(1f, 12f, 23f, 12f)
                }
                "add" -> { drawCircle(color, 9f, Offset(12f, 12f), style = stroke); line(8f, 12f, 16f, 12f); line(12f, 8f, 12f, 16f) }
                "search" -> { drawCircle(color, 6.5f, Offset(10f, 10f), style = stroke); line(15f, 15f, 21f, 21f) }
                "arrow" -> { line(5f, 12f, 19f, 12f); line(14f, 7f, 19f, 12f); line(19f, 12f, 14f, 17f) }
                "down" -> { line(7f, 10f, 12f, 15f); line(12f, 15f, 17f, 10f) }
                "shield" -> {
                    val path = Path().apply { moveTo(12f, 2f); lineTo(21f, 6f); lineTo(20f, 13f); quadraticTo(19f, 19f, 12f, 22f); quadraticTo(5f, 19f, 4f, 13f); lineTo(3f, 6f); close() }
                    drawPath(path, color, style = stroke); line(8f, 12f, 11f, 15f); line(11f, 15f, 16f, 9f)
                }
                "餐饮" -> {
                    drawArc(color, 0f, 180f, false, Offset(3f, 10f), Size(18f, 12f), style = stroke)
                    line(3f, 10f, 21f, 10f); line(8f, 19f, 16f, 19f); line(10f, 3f, 8f, 8f); line(15f, 3f, 13f, 8f)
                }
                "交通" -> {
                    drawRoundRect(color, Offset(5f, 3f), Size(14f, 16f), CornerRadius(4f), style = stroke)
                    line(5f, 11f, 19f, 11f); line(9f, 15f, 9.5f, 15f); line(15f, 15f, 15.5f, 15f); line(8f, 19f, 8f, 21f); line(16f, 19f, 16f, 21f)
                }
                "购物", "日用" -> {
                    drawRoundRect(color, Offset(4f, 7f), Size(16f, 14f), CornerRadius(3f), style = stroke)
                    drawArc(color, 180f, 180f, false, Offset(8f, 2f), Size(8f, 10f), style = stroke)
                }
                "住房" -> {
                    val path = Path().apply { moveTo(3f, 11f); lineTo(12f, 3f); lineTo(21f, 11f); moveTo(6f, 9f); lineTo(6f, 21f); lineTo(18f, 21f); lineTo(18f, 9f) }
                    drawPath(path, color, style = stroke); drawRect(color, Offset(10f, 14f), Size(4f, 7f), style = stroke)
                }
                "娱乐" -> {
                    drawCircle(color, 9f, Offset(12f, 12f), style = stroke)
                    drawPath(Path().apply { moveTo(10f, 7f); lineTo(17f, 12f); lineTo(10f, 17f); close() }, color, style = stroke)
                }
                "医疗" -> { drawRoundRect(color, Offset(3f, 5f), Size(18f, 16f), CornerRadius(4f), style = stroke); line(8f, 12f, 16f, 12f); line(12f, 8f, 12f, 16f) }
                "学习" -> {
                    drawRoundRect(color, Offset(4f, 3f), Size(16f, 18f), CornerRadius(2f), style = stroke)
                    line(9f, 3f, 9f, 21f); line(12f, 9f, 17f, 9f); line(12f, 13f, 17f, 13f)
                }
                "退款", "内部转账" -> { line(4f, 8f, 19f, 8f); line(4f, 8f, 8f, 4f); line(4f, 8f, 8f, 12f); line(20f, 16f, 5f, 16f); line(20f, 16f, 16f, 12f); line(20f, 16f, 16f, 20f) }
                "工资", "收入" -> { drawCircle(color, 9f, Offset(12f, 12f), style = stroke); line(12f, 6f, 12f, 18f); line(8f, 11f, 16f, 11f); line(8f, 15f, 16f, 15f) }
                "chat" -> {
                    drawRoundRect(color, Offset(3f, 3f), Size(18f, 15f), CornerRadius(5f), style = stroke)
                    line(7f, 18f, 7f, 22f); line(7f, 22f, 12f, 18f); line(8f, 10f, 8.5f, 10f); line(12f, 10f, 12.5f, 10f); line(16f, 10f, 16.5f, 10f)
                }
                else -> {
                    drawRoundRect(color, Offset(5f, 2f), Size(14f, 20f), CornerRadius(2f), style = stroke)
                    line(9f, 8f, 16f, 8f); line(9f, 12f, 16f, 12f); line(9f, 16f, 13f, 16f)
                }
            }
        }
    }
}

@Composable
internal fun FloatingIllustration(resource: Int, modifier: Modifier = Modifier) {
    val motion = rememberInfiniteTransition(label = "illustration")
    val lift by motion.animateFloat(-3f, 3f, infiniteRepeatable(tween(3200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "gentle lift")
    Image(painterResource(resource), contentDescription = null,
        modifier = modifier.graphicsLayer { translationY = lift; rotationZ = lift * .18f })
}

@Composable
internal fun SmallBadge(text: String, color: Color = Amber) {
    Surface(color = color.copy(alpha = .09f), shape = RoundedCornerShape(50)) {
        Text(text, Modifier.padding(horizontal = 10.dp, vertical = 5.dp), fontSize = 11.sp, color = color, fontWeight = FontWeight.Medium)
    }
}

@Composable
internal fun PageHeading(title: String, subtitle: String, badge: String = "") {
    Row(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, fontSize = 20.sp, fontWeight = FontWeight.Medium, letterSpacing = .2.sp)
            Text(subtitle, fontSize = 12.sp, color = Muted)
        }
        if (badge.isNotBlank()) SmallBadge(badge, Green)
    }
}

@Composable
internal fun MoneyText(fen: Long, modifier: Modifier = Modifier, color: Color = Ink, size: Int = 26) {
    Text("¥ ${LedgerRules.formatMoney(fen)}", modifier, color = color, fontSize = size.sp,
        fontWeight = FontWeight.SemiBold, letterSpacing = (-.5).sp,
        style = MaterialTheme.typography.headlineMedium.copy(fontFeatureSettings = "tnum"))
}

@Composable
internal fun IconTile(symbol: String, color: Color = Amber, modifier: Modifier = Modifier) {
    Box(modifier.size(42.dp).background(color.copy(alpha = .1f), RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
        AppGlyph(symbol, color = color)
    }
}

@Composable
internal fun ReceiptRule() {
    Canvas(Modifier.fillMaxWidth().height(1.dp)) {
        var x = 0f
        while (x < size.width) {
            drawLine(Amber.copy(alpha = .18f), Offset(x, 0f), Offset(x + 5.dp.toPx(), 0f), 1.dp.toPx())
            x += 10.dp.toPx()
        }
    }
}

@Composable
internal fun ExpenseRing(categories: List<Pair<String, Long>>, modifier: Modifier = Modifier) {
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val progress by animateFloatAsState(if (entered) 1f else 0f, tween(650), label = "expense ring")
    val total = categories.sumOf { it.second }
    Box(modifier.aspectRatio(1f), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().padding(9.dp)) {
            val stroke = Stroke(17.dp.toPx(), cap = StrokeCap.Round)
            drawCircle(Sand, (size.minDimension - stroke.width) / 2, style = stroke)
            var start = -90f
            categories.forEachIndexed { index, (_, fen) ->
                val sweep = if (total == 0L) 0f else 360f * fen.toFloat() / total
                val inset = stroke.width / 2
                if (sweep > 0) drawArc(ChartColors[index % ChartColors.size], start, (sweep - 3f).coerceAtLeast(.5f) * progress, false,
                    Offset(inset, inset), Size(size.width - stroke.width, size.height - stroke.width), style = stroke)
                start += sweep
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(if (total == 0L) "暂无支出" else "支出构成", fontSize = 12.sp, color = Muted)
            Text(if (total == 0L) "—" else "${categories.size} 类", fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}
