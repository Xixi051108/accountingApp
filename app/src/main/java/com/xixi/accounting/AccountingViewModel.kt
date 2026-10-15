package com.xixi.accounting

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId

data class QuestionTurn(val question: String, val resolution: QueryResolution, val viaCloud: Boolean = false)

class AccountingViewModel(application: Application) : AndroidViewModel(application) {
    private val store = LedgerStore(LedgerDatabase.open(application))
    private val settings = DeepSeekSettings(application)
    val entries = store.entries.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    private val mutableBusy = MutableStateFlow(false)
    val busy = mutableBusy.asStateFlow()
    private val mutableMessage = MutableStateFlow("选择支付详情或账单列表截图，清楚的自动入账，存疑的先确认。")
    val message = mutableMessage.asStateFlow()
    private val mutableImages = MutableStateFlow<List<Uri>>(emptyList())
    val images = mutableImages.asStateFlow()
    private val mutableTurn = MutableStateFlow<QuestionTurn?>(null)
    val turn = mutableTurn.asStateFlow()
    private val mutableCloud = MutableStateFlow(settings.enabled)
    val cloud = mutableCloud.asStateFlow()
    private val mutableHasKey = MutableStateFlow(settings.hasKey)
    val hasKey = mutableHasKey.asStateFlow()

    fun selectImages(uris: List<Uri>) {
        if (!mutableBusy.value) mutableImages.value = uris.filter { it.scheme == "content" }.distinct().take(10)
    }
    fun cancelImport() { mutableImages.value = emptyList() }

    private fun operation(failure: String, block: suspend () -> Unit) {
        if (mutableBusy.value) return
        mutableBusy.value = true
        viewModelScope.launch {
            try { block() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutableMessage.value = failure }
            finally { mutableBusy.value = false }
        }
    }

    fun importImages(year: Int?) {
        val selected = mutableImages.value
        if (selected.isEmpty()) return
        mutableImages.value = emptyList()
        operation("导入未完成。已保存的账单保留，请重试；识别不清的图片可分段截图。") {
            var posted = 0; var pending = 0; var skipped = 0; var duplicates = 0; var unreadable = 0
            for ((index, uri) in selected.withIndex()) {
                mutableMessage.value = "正在本地识别 ${index + 1}/${selected.size} 张图片…"
                try {
                    val recognized = ScreenshotImporter.recognize(getApplication(), uri, year)
                    if (recognized.candidates.isEmpty()) { unreadable++; continue }
                    val report = store.import(recognized.candidates, recognized.hash)
                    posted += report.posted; pending += report.pending; skipped += report.skipped
                    if (report.duplicateImage) duplicates++
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { unreadable++ }
            }
            mutableMessage.value = "导入完成：已入账 $posted 笔，待确认 $pending 笔。" +
                (if (duplicates + skipped > 0) "重复图片 $duplicates 张，已有交易 $skipped 笔，已跳过。" else "") +
                (if (unreadable > 0) "$unreadable 张未能识别或保存，请检查图片、分段截图后重试，或手动记账。" else "")
        }
    }

    fun save(entry: LedgerEntry, remember: Boolean, onSuccess: () -> Unit) {
        LedgerRules.validation(entry)?.let { mutableMessage.value = it; return }
        operation("保存失败，原记录保留，请重试") {
            store.save(entry, remember)
            mutableMessage.value = "账单已保存，统计和查账已更新。"
            onSuccess()
        }
    }

    fun delete(entry: LedgerEntry, onSuccess: () -> Unit) = operation("删除失败，请重试") {
        store.delete(entry.id)
        mutableMessage.value = if (entry.posted) "账单已删除。" else "候选已放弃，不计入统计。"
        onSuccess()
    }

    fun ask(question: String) {
        val q = question.trim()
        if (q.isBlank()) return
        operation("查账失败，账本未改变，请重试") {
            val today = LocalDate.now(ZoneId.of("Asia/Shanghai"))
            val current = store.snapshot()
            val local = LedgerQuestions.local(q, today, current.map { it.merchant }.distinct())
            val blocked = Regex("删除|修改|新增|清空|记一笔|改成").containsMatchIn(q)
            if (local.plan != null || !settings.enabled || blocked || q.length > 200) {
                mutableTurn.value = QuestionTurn(q, local)
                mutableMessage.value = "查询完成，账本未改变。"
            } else {
                mutableMessage.value = "正在理解查询条件，仅发送本次问题和当前日期…"
                val resolution = try {
                    val key = withContext(Dispatchers.IO) { settings.readKey() }
                    DeepSeekQuery.resolve(q, today, key)
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { QueryResolution(message = "联网查询未完成，请检查网络、DeepSeek 密钥和账户余额，或使用本地示例查询。账本未改变。") }
                mutableTurn.value = QuestionTurn(q, resolution, viaCloud = true)
                mutableMessage.value = "查询结束，账本未改变。"
            }
        }
    }

    fun saveKey(key: String, onSuccess: () -> Unit) = operation("密钥保存失败，请检查填写并重试") {
        withContext(Dispatchers.IO) { settings.saveKey(key) }
        mutableHasKey.value = true
        mutableMessage.value = "密钥已加密保存在本机，开启辅助查询后才会使用。"
        onSuccess()
    }

    fun enableCloud(value: Boolean) {
        if (value && !settings.hasKey) { mutableMessage.value = "请先保存 DeepSeek 密钥"; return }
        operation("设置保存失败，请重试") {
            withContext(Dispatchers.IO) { settings.enabled = value }
            mutableCloud.value = value
            mutableMessage.value = if (value) "已开启可选联网理解，仅在本地无法理解问题时使用。" else "已关闭联网理解。"
        }
    }

    fun clearKey() = operation("清除密钥失败，请重试") {
        withContext(Dispatchers.IO) { settings.clear() }
        mutableCloud.value = false; mutableHasKey.value = false
        mutableMessage.value = "密钥已清除，联网理解已关闭。"
    }

    fun clearAll(onSuccess: () -> Unit) = operation("清除未完成，请重试") {
        store.clear()
        withContext(Dispatchers.IO) {
            settings.clear()
            check(getApplication<Application>().getSharedPreferences("capture-diagnostics", 0).edit().clear().commit())
        }
        mutableTurn.value = null; mutableImages.value = emptyList()
        mutableCloud.value = false; mutableHasKey.value = false
        mutableMessage.value = "本机账单、候选、导入记录、分类规则、旧诊断和密钥已清除。相册原图保留。"
        onSuccess()
    }

    override fun onCleared() { store.close() }
}
