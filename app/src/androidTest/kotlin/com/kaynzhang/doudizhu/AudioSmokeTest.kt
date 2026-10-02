package com.kaynzhang.doudizhu

import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kaynzhang.doudizhu.audio.VoiceAnnouncer
import com.kaynzhang.doudizhu.audio.VoiceLine
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger

/** Exercises actual packaged files and Android's decoder, including cancellation during playback. */
@RunWith(AndroidJUnit4::class)
class AudioSmokeTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test
    fun generatedVoicesDecodeForEverySeatAndCompleteInOrder() {
        val started = AtomicInteger()
        lateinit var voice: VoiceAnnouncer
        instrumentation.runOnMainSync {
            voice = VoiceAnnouncer(instrumentation.targetContext)
            voice.volume = 0.05f
            voice.onSpeakingChanged = { if (it) started.incrementAndGet() }
        }
        try {
            for (seat in 0..2) {
                instrumentation.runOnMainSync {
                    voice.enqueue(listOf(VoiceLine("call", seat, important = true), VoiceLine("pair_3", seat, important = true), VoiceLine("rocket", seat, important = true)))
                    assertTrue("Packaged catalog must enqueue seat $seat", voice.busy)
                }
                await(20_000) { !voice.busy }
                assertTrue("Seat $seat must decode and start playing", started.get() > seat)
            }
        } finally {
            instrumentation.runOnMainSync { voice.release() }
        }
    }

    @Test
    fun pauseAndMuteDiscardPendingSpeechWithoutReplay() {
        val started = AtomicInteger()
        lateinit var voice: VoiceAnnouncer
        instrumentation.runOnMainSync {
            voice = VoiceAnnouncer(instrumentation.targetContext)
            voice.volume = 0.05f
            voice.onSpeakingChanged = { if (it) started.incrementAndGet() }
            voice.enqueue(listOf(VoiceLine("alert_one", 0, important = true), VoiceLine("win", 0, important = true)))
        }
        try {
            await(5_000) { started.get() > 0 }
            instrumentation.runOnMainSync {
                voice.active = false
                assertFalse(voice.busy)
                voice.active = true
                assertFalse("Returning must not replay old announcements", voice.busy)
            }
            SystemClock.sleep(300)
            assertTrue("No stale prepared callback may start the discarded win", started.get() == 1)
            instrumentation.runOnMainSync {
                voice.enqueue(listOf(VoiceLine("call", 1, important = true)))
                assertTrue(voice.busy)
                voice.volume = 0f
                assertFalse("Zero voice volume must also release turn pacing", voice.busy)
            }
        } finally {
            instrumentation.runOnMainSync { voice.release() }
        }
    }

    private fun await(timeoutMs: Long, condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (!condition() && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(30)
        assertTrue("Audio condition did not complete within ${timeoutMs}ms", condition())
    }
}
