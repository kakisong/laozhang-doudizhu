package com.kaynzhang.doudizhu

import android.content.Intent
import android.os.SystemClock
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleCallback
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.kaynzhang.doudizhu.data.Room
import com.kaynzhang.doudizhu.engine.game.Action
import com.kaynzhang.doudizhu.engine.game.GameConfig
import com.kaynzhang.doudizhu.engine.game.GameEngine
import com.kaynzhang.doudizhu.engine.game.Phase
import com.kaynzhang.doudizhu.game.GameViewModel
import com.kaynzhang.doudizhu.game.TableUi
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Guards the real entry/recreation paths that previously reset the screen and muted shared audio. */
@RunWith(AndroidJUnit4::class)
class LifecycleSmokeTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun repeatedLauncherAndBlankExplicitIntentsKeepTheCurrentActivityAndTable() {
        val vm = enterAndPauseTable()
        val originalActivity = rule.activity
        val originalTable = rule.runOnIdle { vm.table.value!! }
        val created = AtomicInteger()
        val monitor = ActivityLifecycleMonitorRegistry.getInstance()
        val callback = ActivityLifecycleCallback { activity, stage ->
            if (activity is MainActivity && stage == Stage.CREATED) created.incrementAndGet()
        }
        rule.runOnIdle { monitor.addLifecycleCallback(callback) }

        try {
            val launcher = Intent(Intent.ACTION_MAIN).apply {
                setClass(originalActivity, MainActivity::class.java)
                addCategory(Intent.CATEGORY_LAUNCHER)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val blankExplicit = Intent(originalActivity, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

            for (intent in listOf(launcher, blankExplicit)) {
                rule.runOnIdle { originalActivity.startActivity(intent) }
                InstrumentationRegistry.getInstrumentation().waitForIdleSync()
                // The old activity's onStop can follow the new onStart by several hundred ms.
                // Let real framework callbacks run so a duplicate cannot pass an early assertion.
                SystemClock.sleep(1_000)
                rule.waitForIdle()

                assertEquals("A repeated entry must not create another MainActivity", 0, created.get())
                val resumed = resumedMainActivities()
                assertEquals("The game must have one resumed activity", 1, resumed.size)
                assertSame("The existing activity must receive the entry intent", originalActivity, resumed.single())
                rule.runOnIdle {
                    assertSame(vm, ViewModelProvider(resumed.single())[GameViewModel::class.java])
                    assertPausedTableUnchanged(vm, originalTable)
                }
                assertTrue("The table must remain visible", exists("btn_resume_game"))
                assertFalse("Repeated entry must not send the player to the lobby", exists("room_novice"))
            }
        } finally {
            rule.runOnIdle { monitor.removeLifecycleCallback(callback) }
        }
    }

    @Test
    fun activityRecreationKeepsThePausedTableAndVoiceWorksAfterResuming() {
        val vm = enterAndPauseTable()
        val originalActivity = rule.activity
        val originalTable = rule.runOnIdle { vm.table.value!! }
        val container = (originalActivity.application as DdzApp).container
        val previousSettings = rule.runOnIdle { vm.appData.value!!.settings }
        val voiceStarted = AtomicBoolean(false)
        var previousSpeakingCallback: ((Boolean) -> Unit)? = null
        var callbackInstalled = false

        try {
            rule.runOnIdle { vm.updateSettings { it.copy(voice = true, voiceVolume = 80) } }
            rule.waitUntil(20_000) {
                vm.appData.value?.settings?.let { it.voice && it.voiceVolume == 80 } == true &&
                    container.voice.enabled && container.voice.volume > 0f
            }

            rule.activityRule.scenario.recreate()
            rule.waitUntil(20_000) { exists("btn_resume_game") }
            rule.runOnIdle {
                assertNotSame("Recreation must replace the activity", originalActivity, rule.activity)
                assertSame("Configuration recreation must retain the game's ViewModel", vm,
                    ViewModelProvider(rule.activity)[GameViewModel::class.java])
                assertPausedTableUnchanged(vm, originalTable)
                assertFalse("A paused table must keep speech inactive", container.voice.active)
            }
            assertFalse("Recreation must preserve TABLE navigation", exists("room_novice"))

            rule.onNodeWithTag("btn_resume_game").performClick()
            rule.waitUntil(20_000) { !vm.paused.value && container.voice.active && container.sound.active }
            rule.runOnIdle {
                previousSpeakingCallback = container.voice.onSpeakingChanged
                container.voice.onSpeakingChanged = { speaking ->
                    previousSpeakingCallback?.invoke(speaking)
                    if (speaking) voiceStarted.set(true)
                }
                callbackInstalled = true
                // A prepared MediaPlayer must actually start; busy alone only proves queueing.
                vm.previewVoice(0)
            }
            rule.waitUntil(20_000) { voiceStarted.get() }
            rule.runOnIdle { assertTrue("Speech must start after recreation and resume", voiceStarted.get()) }
        } finally {
            rule.runOnIdle {
                if (callbackInstalled) {
                    container.voice.stop()
                    container.voice.onSpeakingChanged = previousSpeakingCallback
                }
                vm.updateSettings { previousSettings }
            }
        }
    }

    @Test
    fun leavingAPausedTableAndContinuingRestoresTheSameGameAndStartsSpeech() {
        val vm = enterAndPauseTable()
        val seed = System.nanoTime()
        rule.runOnIdle {
            // Build a real post-bidding state whose next action belongs to the human, so a slow
            // device cannot advance a computer turn before the restoration assertions run.
            val coins = vm.appData.value!!.profile.coins
            val config = GameConfig(Room.NOVICE.baseScore, listOf(coins, 100_000L, 100_000L))
            val dealt = GameEngine.newGame(config, seed).state
            var state = GameEngine.newGameWithHands(config, seed, dealt.hands, dealt.bottom, firstBidder = 0).state
            state = GameEngine.apply(state, Action.Bid(0, true)).state
            while (state.phase == Phase.BIDDING) {
                state = GameEngine.apply(state, Action.Bid(state.turn, false)).state
            }
            while (state.phase == Phase.DOUBLING) {
                state = GameEngine.apply(state, Action.Jiabei(GameEngine.actors(state).first(), false)).state
            }
            assertEquals(Phase.PLAYING, state.phase)
            assertEquals(0, state.turn)
            vm.startScripted(Room.NOVICE, state, emptyList())
            vm.setPaused(true)
        }
        rule.waitUntil(20_000) { vm.paused.value && vm.table.value?.phase == Phase.PLAYING }
        val before = rule.runOnIdle { vm.table.value!! }
        rule.waitUntil(20_000) {
            vm.appData.value?.savedTable?.state?.let { it.seed == seed && it.seq == before.seq } == true
        }

        rule.onNodeWithTag("btn_pause_lobby").performClick()
        rule.waitUntil(20_000) { exists("room_novice") && exists("btn_resume") }
        rule.runOnIdle { assertFalse("Returning to the lobby must end the live session", vm.inGame) }
        rule.onNodeWithTag("btn_resume").performClick()
        rule.waitUntil(20_000) { vm.inGame && exists("btn_pause") && vm.table.value?.phase == Phase.PLAYING }
        rule.runOnIdle {
            val restored = vm.table.value!!
            assertEquals("Continue must restore the persisted seed", seed, vm.appData.value!!.savedTable!!.state.seed)
            assertEquals("Continue must restore the same deal", before.dealKey, restored.dealKey)
            assertEquals("Continue must restore the same engine step", before.seq, restored.seq)
            assertEquals("Continue must restore the same hand", before.hand, restored.hand)
            assertFalse("Continue must release the game pause", vm.paused.value)
        }

        val container = (rule.activity.application as DdzApp).container
        val previousSettings = rule.runOnIdle { vm.appData.value!!.settings }
        val voiceStarted = AtomicBoolean(false)
        var previousSpeakingCallback: ((Boolean) -> Unit)? = null
        var callbackInstalled = false
        try {
            rule.runOnIdle { vm.updateSettings { it.copy(voice = true, voiceVolume = 80) } }
            rule.waitUntil(20_000) {
                vm.appData.value?.settings?.let { it.voice && it.voiceVolume == 80 } == true &&
                    container.voice.enabled && container.voice.volume > 0f && container.voice.active && container.sound.active
            }
            rule.runOnIdle {
                previousSpeakingCallback = container.voice.onSpeakingChanged
                container.voice.onSpeakingChanged = { speaking ->
                    previousSpeakingCallback?.invoke(speaking)
                    if (speaking) voiceStarted.set(true)
                }
                callbackInstalled = true
                // Exercise the restored game's real card announcement, not just a preview.
                vm.hint()
                vm.play()
            }
            rule.waitUntil(20_000) { voiceStarted.get() }
            assertTrue("Playing a card after continuing must start its voice announcement", voiceStarted.get())
        } finally {
            rule.runOnIdle {
                if (callbackInstalled) {
                    container.voice.stop()
                    container.voice.onSpeakingChanged = previousSpeakingCallback
                }
                vm.updateSettings { previousSettings }
            }
        }
    }

    private fun enterAndPauseTable(): GameViewModel {
        rule.waitUntil(20_000) { exists("room_novice") }
        rule.onNodeWithTag("room_novice").performClick()
        rule.waitUntil(20_000) { exists("btn_pause") }
        rule.onNodeWithTag("btn_pause").performClick()
        lateinit var vm: GameViewModel
        rule.runOnIdle { vm = ViewModelProvider(rule.activity)[GameViewModel::class.java] }
        rule.waitUntil(20_000) { vm.paused.value && exists("btn_resume_game") }
        return vm
    }

    private fun assertPausedTableUnchanged(vm: GameViewModel, previous: TableUi) {
        assertTrue("The pause must remain under player control", vm.paused.value)
        val table = vm.table.value!!
        assertEquals("The current deal must survive", previous.dealKey, table.dealKey)
        assertEquals("No turns may advance or restart while paused", previous.seq, table.seq)
        assertEquals("The phase must survive", previous.phase, table.phase)
        assertEquals("The player's hand must survive", previous.hand, table.hand)
    }

    private fun resumedMainActivities(): List<MainActivity> {
        var activities = emptyList<MainActivity>()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            activities = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                .filterIsInstance<MainActivity>()
        }
        return activities
    }

    private fun exists(tag: String) = rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
}
