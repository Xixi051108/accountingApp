package com.xixi.accounting

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class WeChatNotificationService : NotificationListenerService() {
    override fun onListenerConnected() {
        CaptureStore.get(this).setDiagnostic("通知监听已连接，等待新支付通知。")
        // Do not enumerate existing notifications: first connection must not import history.
    }

    override fun onListenerDisconnected() {
        CaptureStore.get(this).setDiagnostic("通知监听已断开，请检查权限和后台运行设置。")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null || sbn.packageName != "com.tencent.mm") return
        val extras = sbn.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty().take(200)
        val text = listOfNotNull(
            extras.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
            extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString(),
        ).distinct().joinToString("\n").take(4000)
        val input = CaptureInput(sbn.packageName, CaptureSource.NOTIFICATION,
            "notification:${sbn.key}:${sbn.postTime}", System.currentTimeMillis(), title, text)
        CaptureParser.parse(input)?.let { CaptureStore.get(this).record(it) }
    }
}
