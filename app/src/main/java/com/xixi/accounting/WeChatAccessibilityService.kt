package com.xixi.accounting

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import java.util.UUID

class WeChatAccessibilityService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private val store by lazy { CaptureStore.get(this) }
    private val recognizer by lazy { TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build()) }
    private var paymentActivity = false
    private var pageId: String? = null
    private var ocrAttempted = false
    private var ocrRunning = false
    private var destroyed = false
    private val inspect = Runnable { inspectPage() }

    override fun onServiceConnected() {
        super.onServiceConnected()
        store.setPageDiagnostic("页面服务已连接，等待可识别的实时支付窗口。")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val eventPackage = event.packageName?.toString()
        if (eventPackage != null && eventPackage != "com.tencent.mm") {
            leavePage()
            return
        }
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val activity = event.className?.toString().orEmpty()
            if (activity.startsWith("com.tencent.mm.")) {
                val allowed = listOf(".plugin.wallet.", ".plugin.remittance.", ".plugin.luckymoney.")
                    .any { activity.contains(it) } && !Regex("OrderInfo|Bill|Record|Detail").containsMatchIn(activity)
                if (!allowed) {
                    store.setPageDiagnostic("当前微信窗口被保守规则排除，可能是聊天或历史详情。可保存实测报告用于适配。", activity)
                    leavePage()
                    return
                }
                store.setPageDiagnostic("已收到支付窗口事件，正在读取可见文字。", activity)
                paymentActivity = true
            }
        }
        if (!paymentActivity) {
            if (eventPackage == "com.tencent.mm") store.setPageDiagnostic("已收到微信事件，但尚未识别到支付窗口；可保存实测报告用于适配。")
            return
        }
        handler.removeCallbacks(inspect)
        handler.postDelayed(inspect, 400)
    }

    private data class VisiblePage(val text: String, val bounds: Rect, val windowId: Int)

    private fun visiblePage(): VisiblePage? {
        if (!paymentActivity || destroyed) return null
        val root = rootInActiveWindow ?: run {
            store.setPageDiagnostic("支付窗口的文字树不可读取，OCR 保持当前设置；请记录为受限。")
            return null
        }
        if (root.packageName?.toString() != "com.tencent.mm") return null
        val lines = mutableListOf<String>()
        var editable = false
        var visited = 0
        fun read(node: AccessibilityNodeInfo) {
            if (visited++ >= 300) return
            if (node.isVisibleToUser) {
                if (node.isEditable) editable = true
                node.text?.toString()?.take(300)?.let { lines.add(it) }
                node.contentDescription?.toString()?.take(300)?.let { lines.add(it) }
            }
            for (index in 0 until node.childCount) node.getChild(index)?.let { read(it) }
        }
        read(root)
        if (editable) {
            store.setPageDiagnostic("当前窗口仍有输入控件，已跳过，以避免扫描聊天或支付输入页。")
            return null
        }
        val text = lines.distinct().joinToString("\n").take(4000)
        if (!CaptureParser.isLivePaymentPage(text)) {
            store.setPageDiagnostic("已收到支付窗口，但可见文字未匹配实时结果或属于历史详情；OCR 不会在此页面触发。")
            return null
        }
        val bounds = Rect().also { root.getBoundsInScreen(it) }
        return VisiblePage(text, bounds, root.windowId)
    }

    private fun inspectPage() {
        val page = visiblePage() ?: run { pageId = null; ocrAttempted = false; return }
        val id = pageId ?: "page:${UUID.randomUUID()}".also { pageId = it }
        val input = CaptureInput("com.tencent.mm", CaptureSource.PAGE, id, System.currentTimeMillis(), text = page.text)
        val candidate = CaptureParser.parse(input) ?: return
        store.record(candidate)
        store.setPageDiagnostic("页面文字已生成候选。OCR ${if (store.ocrEnabled) "已开启" else "关闭"}；候选未计入账本。")
        if (candidate.amountFen == null && store.ocrEnabled && !ocrAttempted && !ocrRunning) {
            ocrAttempted = true
            captureOcr(input, page)
        }
    }

    private fun captureOcr(input: CaptureInput, page: VisiblePage) {
        val generation = store.generation
        ocrRunning = true
        val callback = object : TakeScreenshotCallback {
            override fun onFailure(errorCode: Int) {
                ocrRunning = false
                if (pageId == input.eventId && store.generation == generation)
                    store.setDiagnostic("系统未允许本地截图（错误 $errorCode）。候选保留，请在实测中记录限制。")
            }

            override fun onSuccess(screenshot: ScreenshotResult) {
                val buffer = screenshot.hardwareBuffer
                var bitmap: Bitmap? = null
                var taskStarted = false
                try {
                    if (pageId != input.eventId || store.generation != generation || !store.ocrEnabled || visiblePage() == null)
                        return
                    val hardware = Bitmap.wrapHardwareBuffer(buffer, screenshot.colorSpace) ?: return
                    val software = try { hardware.copy(Bitmap.Config.ARGB_8888, false) } finally { hardware.recycle() }
                    if (software == null) return
                    // API 31–33 only offer a display screenshot; crop to the visible payment window.
                    bitmap = software
                    val bounds = if (Build.VERSION.SDK_INT >= 34) Rect(0, 0, software.width, software.height)
                        else Rect(page.bounds)
                    if (!bounds.intersect(0, 0, software.width, software.height) || bounds.isEmpty) return
                    bitmap = Bitmap.createBitmap(software, bounds.left, bounds.top, bounds.width(), bounds.height())
                    if (bitmap !== software) software.recycle()
                    val image = bitmap ?: return
                    val task = recognizer.process(InputImage.fromBitmap(image, 0))
                    bitmap = null
                    taskStarted = true
                    task
                        .addOnSuccessListener { result ->
                            if (pageId == input.eventId && store.generation == generation && store.ocrEnabled && visiblePage() != null) {
                                val ocrInput = input.copy(source = CaptureSource.OCR, text = result.text.take(4000))
                                CaptureParser.parse(ocrInput)?.let { store.record(it) }
                                    ?: store.setDiagnostic("OCR 未取得可核对的实时支付文字，请记录为受限。")
                            }
                        }
                        .addOnFailureListener {
                            if (store.generation == generation)
                                store.setDiagnostic("本地 OCR 识别失败，页面候选已保留。")
                        }
                        .addOnCompleteListener { image.recycle(); ocrRunning = false }
                } catch (_: RuntimeException) {
                    store.setDiagnostic("本地截图无法识别，页面候选已保留。")
                } finally {
                    bitmap?.recycle()
                    buffer.close()
                    if (!taskStarted) ocrRunning = false
                }
            }
        }
        try {
            if (Build.VERSION.SDK_INT >= 34) takeScreenshotOfWindow(page.windowId, mainExecutor, callback)
            else takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, callback)
        } catch (_: RuntimeException) {
            ocrRunning = false
            store.setDiagnostic("系统无法启动本地截图，请检查无障碍权限。")
        }
    }

    private fun leavePage() {
        paymentActivity = false
        pageId = null
        ocrAttempted = false
        handler.removeCallbacks(inspect)
    }

    override fun onInterrupt() { leavePage(); store.setPageDiagnostic("页面服务已中断，请检查无障碍授权。") }

    override fun onDestroy() {
        destroyed = true
        leavePage()
        store.setPageDiagnostic("页面服务已断开，请检查无障碍授权或后台运行设置。")
        recognizer.close()
        super.onDestroy()
    }
}
