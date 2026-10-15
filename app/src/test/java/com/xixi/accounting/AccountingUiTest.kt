package com.xixi.accounting

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import android.graphics.Bitmap
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], qualifiers = "w393dp-h852dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AccountingUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun capture(name: String) {
        val screenshotDir = File(requireNotNull(System.getProperty("accounting.screenshotDir"))).apply { mkdirs() }
        compose.runOnIdle {
            val view = compose.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bitmap))
            File(screenshotDir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }

    private fun addEntry(amount: String, category: String, merchant: String) {
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("手动记一笔"))
        compose.onNodeWithText("手动记一笔").performClick()
        compose.onNodeWithText("金额（元）").performTextInput(amount)
        compose.onNodeWithText("商户或备注").performScrollTo().performTextInput(merchant)
        compose.onNodeWithText("其他").performScrollTo().performClick()
        compose.onNodeWithText(category).performClick()
        compose.onNodeWithText("确认入账").performClick()
        compose.waitUntil(10000) {
            compose.onAllNodesWithText("确认入账").fetchSemanticsNodes().isEmpty()
        }
    }

    @Test fun manualEntryCanBeConfirmedAndQueriedThroughTheActualScreens() {
        capture("ledger-empty")
        addEntry("23.45", "餐饮", "示例餐厅")
        addEntry("18.00", "交通", "示例出行")
        addEntry("46.00", "购物", "示例商店")
        compose.onNode(hasScrollAction()).performScrollToIndex(0)
        capture("ledger")
        compose.onNodeWithText("统计").performClick()
        capture("statistics")
        compose.onNodeWithText("查账").performClick()
        capture("questions")
        compose.onNodeWithText("这个月餐饮花了多少").performScrollTo().performClick()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("查一查"))
        compose.waitUntil(10000) { compose.onAllNodes(hasText("查一查") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("净支出 ¥23.45", substring = true))
        compose.onNodeWithText("净支出 ¥23.45", substring = true).assertIsDisplayed()
        capture("query-answer")
        compose.onNodeWithText("设置").performClick()
        capture("settings")
    }

    @Test
    @Config(sdk = [32], qualifiers = "w320dp-h640dp-xhdpi")
    fun smallScreenWithLargerTextAndReducedMotionKeepsActionsReachable() {
        RuntimeEnvironment.setFontScale(1.3f)
        android.provider.Settings.Global.putFloat(compose.activity.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        compose.activityRule.scenario.recreate()
        try {
            compose.onNode(hasScrollAction()).performScrollToNode(hasText("导入账单截图"))
            compose.onNodeWithText("导入账单截图").assertIsDisplayed()
            capture("ledger-small-large-text")
            compose.onNodeWithText("设置").performClick()
            compose.onNodeWithText("配置").performClick()
            compose.onNode(hasScrollAction()).performScrollToNode(hasText("DeepSeek API 密钥"))
            compose.onNodeWithText("DeepSeek API 密钥").assertIsDisplayed()
            capture("settings-small-large-text")
        } finally { RuntimeEnvironment.setFontScale(1f) }
    }

    @Test
    @Config(sdk = [32], qualifiers = "w800dp-h360dp-land-xhdpi")
    fun landscapeAllowsScrollingAndCancellingAnEntry() {
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("手动记一笔"))
        compose.onNodeWithText("手动记一笔").performClick()
        compose.onNodeWithText("金额（元）").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("确认入账").assertIsDisplayed()
        capture("editor-landscape")
        compose.onNodeWithText("取消").performClick()
        compose.onNodeWithText("统计").performClick()
        capture("statistics-landscape")
    }
}
