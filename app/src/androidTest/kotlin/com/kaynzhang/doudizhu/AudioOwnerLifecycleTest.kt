package com.kaynzhang.doudizhu

import android.os.SystemClock
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kaynzhang.doudizhu.audio.AppAudio
import com.kaynzhang.doudizhu.audio.SoundManager
import com.kaynzhang.doudizhu.audio.VoiceAnnouncer
import com.kaynzhang.doudizhu.audio.VoiceLine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger

/** Reproduces the second Activity's onStart arriving before the old Activity's onStop/onCleared. */
@RunWith(AndroidJUnit4::class)
class AudioOwnerLifecycleTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test
    fun staleActivityLifecycleCannotSilenceTheNewForegroundPlayersVoice() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val oldOwner = Any()
        val newOwner = Any()
        val started = AtomicInteger()
        lateinit var sound: SoundManager
        lateinit var voice: VoiceAnnouncer
        lateinit var audio: AppAudio
        instrumentation.runOnMainSync {
            sound = SoundManager(instrumentation.targetContext, scope)
            voice = VoiceAnnouncer(instrumentation.targetContext)
            voice.volume = 0.05f
            audio = AppAudio(instrumentation.targetContext, sound, voice)
            val audioCallback = voice.onSpeakingChanged
            voice.onSpeakingChanged = {
                audioCallback?.invoke(it)
                if (it) started.incrementAndGet()
            }
        }
        try {
            instrumentation.runOnMainSync {
                audio.setForeground(oldOwner, true)
                audio.setPaused(oldOwner, true)
                audio.setForeground(newOwner, true)
                // Real device failure order: new onStart, then old onStop and onCleared.
                audio.setForeground(oldOwner, false)
                audio.stop(oldOwner)
                audio.setPaused(oldOwner, true)
                assertTrue("Old owner must not disable the new owner's voice", voice.active)
                assertTrue("Old owner must not disable the new owner's effects", sound.active)
                voice.enqueue(listOf(VoiceLine("alert_one", 0, important = true)))
            }
            await(5_000) { started.get() == 1 }
            instrumentation.runOnMainSync {
                audio.stop(oldOwner)
                assertTrue("Old ViewModel clearing must not stop the new owner's current recording", voice.busy)
                audio.setForeground(newOwner, false)
                assertFalse("Current owner leaving still disables audio", voice.active)
                assertFalse("Current owner leaving discards queued speech", voice.busy)
                audio.setForeground(newOwner, true)
                voice.enqueue(listOf(VoiceLine("call", 1, important = true)))
            }
            await(5_000) { started.get() == 2 }
            await(5_000) { !voice.busy }
            instrumentation.runOnMainSync {
                audio.setPaused(newOwner, true)
                audio.setForeground(newOwner, false)
                audio.setForeground(newOwner, true)
                assertFalse("Returning to the same owner preserves a manual table pause", voice.active)
                audio.setPaused(newOwner, false)
                assertTrue("Current owner can resume its paused table", voice.active)
            }
        } finally {
            instrumentation.runOnMainSync {
                audio.setForeground(newOwner, false)
                audio.stop(newOwner)
                voice.release()
                sound.release()
            }
            scope.cancel()
        }
    }

    private fun await(timeoutMs: Long, condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (!condition() && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(30)
        assertTrue("Audio lifecycle condition did not complete within ${timeoutMs}ms", condition())
    }
}
