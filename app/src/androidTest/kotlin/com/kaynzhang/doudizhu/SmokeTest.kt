package com.kaynzhang.doudizhu

import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-end smoke test on a device/emulator: enter the novice room, answer bidding and 加倍 by
 * hand, play a few turns with 提示 + 出牌, let 托管 finish the game, then start the next one.
 */
@RunWith(AndroidJUnit4::class)
class SmokeTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private fun exists(tag: String) = rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()

    private fun waitFor(tag: String, timeoutMs: Long = 20_000) = rule.waitUntil(timeoutMs) { exists(tag) }

    private fun click(tag: String) {
        if (exists(tag)) rule.onNodeWithTag(tag).performClick()
    }

    @Test
    fun playAGameByHandAndWithTrusteeship() {
        waitFor("room_novice")
        rule.onNodeWithTag("room_novice").performClick()
        waitFor("btn_trustee")

        var manualPlays = 0
        val deadline = System.currentTimeMillis() + 90_000
        while (System.currentTimeMillis() < deadline && manualPlays < 3 && !exists("btn_again")) {
            when {
                exists("btn_call") -> click("btn_call")
                exists("btn_norob") -> click("btn_norob")
                exists("btn_nojiabei") -> click("btn_nojiabei")
                exists("btn_play") -> {
                    click("btn_hint")
                    rule.waitForIdle()
                    click("btn_play")
                    manualPlays++
                }
                exists("btn_cannot") -> click("btn_cannot")
                else -> Thread.sleep(150)
            }
            rule.waitForIdle()
        }

        if (!exists("btn_again")) click("btn_trustee")
        waitFor("btn_again", timeoutMs = 240_000)
        rule.onNodeWithTag("btn_again").performClick()
        waitFor("btn_trustee")
    }
}
