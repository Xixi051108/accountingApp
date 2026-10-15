package com.xixi.accounting

import android.content.Context
import android.os.Build
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

class CaptureStore private constructor(context: Context) {
    private val preferences = context.getSharedPreferences("capture-diagnostics", Context.MODE_PRIVATE)
    private val mutableCandidates = MutableStateFlow(readCandidates())
    val candidates = mutableCandidates.asStateFlow()
    private val mutableDiagnostic = MutableStateFlow("等待微信支付相关事件；普通聊天不会保存。")
    val diagnostic = mutableDiagnostic.asStateFlow()
    private val mutablePageDiagnostic = MutableStateFlow("页面服务尚未报告连接；开启后需观察实时支付页面。")
    val pageDiagnostic = mutablePageDiagnostic.asStateFlow()
    private val mutableResults = MutableStateFlow(scenarios.associateWith {
        preferences.getString("scenario:$it", "未验证") ?: "未验证"
    })
    val results = mutableResults.asStateFlow()
    var generation = 0
        private set

    var ocrEnabled: Boolean
        get() = preferences.getBoolean("ocr", false)
        set(value) { preferences.edit { putBoolean("ocr", value) } }

    var hyperOs: String
        get() = preferences.getString("hyperos", "") ?: ""
        set(value) { preferences.edit { putString("hyperos", value.take(80)) } }

    fun record(candidate: CaptureCandidate) {
        mutableCandidates.value = CaptureHistory.add(mutableCandidates.value, candidate)
        val array = JSONArray()
        mutableCandidates.value.forEach { c ->
            array.put(JSONObject().apply {
                put("eventId", c.eventId); put("capturedAt", c.capturedAt)
                put("source", c.source.name); put("amountFen", c.amountFen ?: JSONObject.NULL)
                put("direction", c.direction?.name ?: JSONObject.NULL)
                put("kind", c.kind?.name ?: JSONObject.NULL); put("completion", c.completion.name)
                put("transactionId", c.transactionId ?: JSONObject.NULL); put("reason", c.reason)
                put("conflicted", c.conflicted)
            })
        }
        preferences.edit { putString("candidates", array.toString()) }
        setDiagnostic("已保存支付候选，仅用于采集核对，未计入账本。")
    }

    fun setDiagnostic(message: String) { mutableDiagnostic.value = message }

    fun setPageDiagnostic(message: String, windowClass: String? = null) {
        mutablePageDiagnostic.value = message
        // Only code identifiers, never window titles, accessibility text or contact names.
        if (windowClass != null && Regex("^com\\.tencent\\.mm\\.[A-Za-z0-9_.$]{1,160}$").matches(windowClass) &&
            preferences.getString("wechat-window-class", "") != windowClass) {
            preferences.edit { putString("wechat-window-class", windowClass) }
        }
    }

    fun clearCandidates() {
        generation++
        mutableCandidates.value = emptyList()
        preferences.edit { remove("candidates") }
        preferences.edit { remove("wechat-window-class") }
        setDiagnostic("本机候选已清空。实测记录保留，可单独重置。")
    }

    fun setResult(scenario: String, result: String) {
        if (scenario !in scenarios || result !in outcomes) return
        mutableResults.value = mutableResults.value + (scenario to result)
        preferences.edit { putString("scenario:$scenario", result) }
    }

    fun resetResults() {
        mutableResults.value = scenarios.associateWith { "未验证" }
        preferences.edit {
            remove("hyperos")
            scenarios.forEach { remove("scenario:$it"); remove("fields:$it"); remove("limit:$it") }
        }
    }

    fun fields(scenario: String): Set<String> = preferences.getStringSet("fields:$scenario", emptySet())?.toSet() ?: emptySet()
    fun setFields(scenario: String, fields: Set<String>) {
        if (scenario in scenarios) preferences.edit { putStringSet("fields:$scenario", fields.intersect(fieldNames.toSet())) }
    }
    fun limitation(scenario: String): String = preferences.getString("limit:$scenario", "未记录") ?: "未记录"
    fun setLimitation(scenario: String, limitation: String) {
        if (scenario in scenarios && limitation in limitations) preferences.edit { putString("limit:$scenario", limitation) }
    }

    fun report(wechatVersion: String): String = buildString {
        appendLine("# 小米 15 微信采集验证报告")
        appendLine()
        appendLine("此报告由用户手动记录，不代表自动检测或完整流水覆盖。")
        appendLine("- App：${BuildConfig.VERSION_NAME}")
        appendLine("- 机型：${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine("- Android：${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        appendLine("- HyperOS：${hyperOs.ifBlank { "未填写" }.replace('\n', ' ')}")
        appendLine("- 微信：$wechatVersion")
        appendLine("- 页面诊断：${mutablePageDiagnostic.value}")
        appendLine("- 最近微信窗口类名（诊断元数据）：${preferences.getString("wechat-window-class", "未取得")}")
        appendLine()
        appendLine("| 场景 | 用户记录结果 | 可取得字段 | 限制 |")
        appendLine("| --- | --- | --- | --- |")
        scenarios.forEach { appendLine("| $it | ${mutableResults.value[it]} | ${fieldNames.filter { f -> f in fields(it) }.joinToString("、").ifBlank { "未记录" }} | ${limitation(it)} |") }
        appendLine()
        appendLine("报告不包含金额、交易号、商户、原始文字、截图或设备标识。")
    }

    private fun readCandidates(): List<CaptureCandidate> = runCatching {
        val array = JSONArray(preferences.getString("candidates", "[]"))
        (0 until array.length()).map { index ->
            val json = array.getJSONObject(index)
            CaptureCandidate(
                json.getString("eventId"), json.getLong("capturedAt"),
                CaptureSource.valueOf(json.getString("source")),
                if (json.isNull("amountFen")) null else json.getLong("amountFen"),
                if (json.isNull("direction")) null else Direction.valueOf(json.getString("direction")),
                if (json.isNull("kind")) null else TransactionKind.valueOf(json.getString("kind")),
                Completion.valueOf(json.getString("completion")),
                if (json.isNull("transactionId")) null else json.getString("transactionId"),
                json.getString("reason"),
                json.optBoolean("conflicted", false),
            )
        }.take(100)
    }.getOrElse { emptyList() }

    companion object {
        val outcomes = listOf("未验证", "可用", "受限", "失败")
        val fieldNames = listOf("金额", "方向", "完成状态", "交易号")
        val limitations = listOf("未记录", "未观察到限制", "未产生通知", "金额缺失", "方向不明", "状态不明", "页面不可读", "截图被禁止", "后台中断", "其他限制")
        val scenarios = listOf("通知权限", "无障碍权限", "消费通知", "退款通知", "转账通知", "收款通知", "红包通知",
            "消费页面", "退款页面", "转账页面", "收款页面", "红包页面", "本地 OCR", "锁屏", "重启", "后台运行")
        @Volatile private var instance: CaptureStore? = null
        fun get(context: Context): CaptureStore = instance ?: synchronized(this) {
            instance ?: CaptureStore(context.applicationContext).also { instance = it }
        }
    }
}
