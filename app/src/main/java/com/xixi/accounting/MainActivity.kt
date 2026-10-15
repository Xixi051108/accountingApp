package com.xixi.accounting

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.Role
import androidx.lifecycle.ViewModelProvider
import java.time.LocalDate
import java.time.ZoneId

internal val Ink = Color(0xFF3B342A)
internal val Amber = Color(0xFF8C5B16)
internal val Paper = Color(0xFFF8F6F0)
internal val Sand = Color(0xFFEDE7DC)
internal val Muted = Color(0xFF756D61)
internal val Green = Color(0xFF54715B)
internal val Sheet = Color(0xFFFFFDFA)
internal fun today(): LocalDate = LocalDate.now(ZoneId.of("Asia/Shanghai"))

@Composable
internal fun AccountingTheme(content: @Composable () -> Unit) {
    val typography = remember {
        Typography(
            headlineLarge = TextStyle(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.SemiBold),
            headlineMedium = TextStyle(fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.Medium),
            headlineSmall = TextStyle(fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.Medium),
            titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.Medium),
            titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium),
            titleSmall = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
            bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 22.sp),
            bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
            bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 18.sp),
            labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
            labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium),
            labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 16.sp),
        )
    }
    MaterialTheme(colorScheme = lightColorScheme(primary = Amber, onPrimary = Color.White, background = Paper, onBackground = Ink,
        surface = Sheet, onSurface = Ink, surfaceVariant = Sand, onSurfaceVariant = Muted,
        secondaryContainer = Honey, onSecondaryContainer = Ink, outline = Outline, outlineVariant = Outline),
        typography = typography, content = content)
}

class MainActivity : ComponentActivity() {
    private lateinit var model: AccountingViewModel
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT))
        model = ViewModelProvider(this)[AccountingViewModel::class.java]
        if (savedInstanceState == null) handleShare(intent)
        setContent { AccountingTheme { AccountingApp(model) } }
    }

    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); handleShare(intent) }

    @Suppress("DEPRECATION")
    private fun handleShare(shared: Intent?) {
        if (shared?.type?.startsWith("image/") != true) return
        val uris = runCatching {
            when (shared.action) {
                Intent.ACTION_SEND -> listOfNotNull(shared.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))
                Intent.ACTION_SEND_MULTIPLE -> shared.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty()
                else -> emptyList()
            }
        }.getOrDefault(emptyList())
        model.selectImages(uris)
    }
}

@Composable
private fun AccountingApp(model: AccountingViewModel) {
    val entries by model.entries.collectAsState()
    val busy by model.busy.collectAsState()
    val message by model.message.collectAsState()
    val images by model.images.collectAsState()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(10)) { model.selectImages(it) }
    val import = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    Scaffold(containerColor = Paper, bottomBar = {
        Surface(color = Sheet, shadowElevation = 5.dp, shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)) {
            Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp)) {
                listOf("账本", "统计", "查账", "设置").forEachIndexed { index, label ->
                    val selected = index == tab
                    val color by animateColorAsState(if (selected) Amber else Muted, tween(180), label = "tab color")
                    Column(Modifier.weight(1f).heightIn(min = 52.dp).selectable(selected, role = Role.Tab, onClick = { tab = index }),
                        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(Modifier.size(42.dp, 28.dp).background(if (selected) Honey else Color.Transparent, RoundedCornerShape(12.dp)),
                            contentAlignment = androidx.compose.ui.Alignment.Center) { NavMark(index, color) }
                        Text(label, color = color, fontSize = 11.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
                    }
                }
            }
        }
    }) { padding ->
        Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 720.dp).fillMaxSize()) {
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Amber)
                Crossfade(targetState = tab, animationSpec = tween(180), label = "page transition") { page -> when (page) {
                    0 -> LedgerScreen(entries, busy, message, import, { editingId = "new" }, { editingId = it.id })
                    1 -> StatisticsScreen(entries)
                    2 -> QuestionsScreen(model, entries, busy)
                    3 -> SettingsScreen(model, entries.size, busy, message)
                } }
            }
        }
    }
    if (images.isNotEmpty()) ImportDialog(images.size, model::cancelImport, model::importImages)
    val editing = editingId?.let { id ->
        if (id == "new") remember(id) { LedgerEntry(date = today().toString(), direction = "支出") }
        else entries.firstOrNull { it.id == id }
    }
    if (editing != null) EntryEditor(editing, entries, busy, isNew = editingId == "new", message,
        dismiss = { if (!busy) editingId = null },
        save = { entry, rememberCategory -> model.save(entry, rememberCategory) { editingId = null } },
        delete = { model.delete(editing) { editingId = null } })
}

@Composable
private fun NavMark(index: Int, color: Color) {
    Canvas(Modifier.size(21.dp)) {
        val s = size.width; val stroke = Stroke(width = s * .075f)
        when (index) {
            0 -> {
                drawRoundRect(color, Offset(s * .2f, s * .1f), Size(s * .65f, s * .8f), CornerRadius(s * .1f), style = stroke)
                for (y in listOf(.35f, .52f, .69f)) drawLine(color, Offset(s * .35f, s * y), Offset(s * .68f, s * y), stroke.width)
            }
            1 -> for ((x, h) in listOf(.15f to .35f, .43f to .7f, .71f to .5f))
                drawRoundRect(color, Offset(s * x, s * (.9f - h)), Size(s * .15f, s * h), CornerRadius(s * .04f))
            2 -> {
                drawRoundRect(color, Offset(s * .1f, s * .15f), Size(s * .8f, s * .6f), CornerRadius(s * .15f), style = stroke)
                drawLine(color, Offset(s * .25f, s * .75f), Offset(s * .25f, s * .95f), stroke.width)
                drawLine(color, Offset(s * .25f, s * .95f), Offset(s * .43f, s * .75f), stroke.width)
            }
            else -> for ((y, x) in listOf(.25f to .65f, .5f to .35f, .75f to .6f)) {
                drawLine(color, Offset(s * .1f, s * y), Offset(s * .9f, s * y), stroke.width)
                drawCircle(Paper, s * .09f, Offset(s * x, s * y))
                drawCircle(color, s * .09f, Offset(s * x, s * y), style = stroke)
            }
        }
    }
}
