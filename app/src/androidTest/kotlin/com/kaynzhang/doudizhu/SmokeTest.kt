package com.kaynzhang.doudizhu

import android.os.SystemClock
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kaynzhang.doudizhu.engine.game.Phase
import com.kaynzhang.doudizhu.game.GameViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
        waitFor("btn_table_more")

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

        if (!exists("btn_again")) {
            rule.onNodeWithTag("btn_table_more").performClick()
            waitFor("btn_trustee")
            rule.onNodeWithTag("btn_trustee").performClick()
        }
        waitFor("btn_again", timeoutMs = 240_000)
        rule.onNodeWithTag("btn_again").performClick()
        waitFor("btn_table_more")
    }

    @Test
    fun pauseStopsAutomaticTurnsAndResumeRestartsThem() {
        waitFor("room_novice")
        rule.onNodeWithTag("room_novice").performClick()
        waitFor("btn_pause")

        lateinit var vm: GameViewModel
        rule.runOnIdle {
            // Match MainActivity's default viewModels() key, so assertions inspect the visible game.
            vm = ViewModelProvider(rule.activity)[GameViewModel::class.java]
            vm.setTrustee(true)
        }
        // Reach actual play so the pause test covers pending AI decisions and automatic card plays.
        rule.waitUntil(30_000) {
            val table = vm.table.value
            table != null && ((table.phase == Phase.PLAYING && !table.dealing) || table.phase == Phase.FINISHED)
        }
        rule.runOnIdle {
            assertFalse("The game must still be running before it is paused", vm.table.value!!.phase == Phase.FINISHED)
        }

        rule.onNodeWithTag("btn_pause").performClick()
        rule.waitUntil(20_000) { vm.paused.value && exists("btn_resume_game") }
        val pausedSeq = rule.runOnIdle { vm.table.value!!.seq }

        // Let real coroutine delays elapse; advancing only the Compose clock would miss the AI loop.
        SystemClock.sleep(3_000)
        rule.runOnIdle {
            assertTrue("Pause must stay active until the player resumes", vm.paused.value)
            assertEquals("AI and trusteeship must not advance the paused game", pausedSeq, vm.table.value!!.seq)
        }

        rule.onNodeWithTag("btn_resume_game").performClick()
        rule.waitUntil(20_000) {
            val table = vm.table.value
            !vm.paused.value && table != null && (table.seq > pausedSeq || table.phase == Phase.FINISHED)
        }
        rule.runOnIdle { assertFalse("Continue must release the pause", vm.paused.value) }
    }
}
