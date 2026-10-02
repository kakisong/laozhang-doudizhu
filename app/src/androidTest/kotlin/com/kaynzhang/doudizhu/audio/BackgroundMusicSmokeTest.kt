package com.kaynzhang.doudizhu.audio

import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kaynzhang.doudizhu.DdzApp
import com.kaynzhang.doudizhu.MainActivity
import com.kaynzhang.doudizhu.data.Settings
import com.kaynzhang.doudizhu.game.GameViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs

/** Exercises the packaged AAC and real MediaPlayer/focus callbacks while an Activity is foreground. */
@RunWith(AndroidJUnit4::class)
class BackgroundMusicSmokeTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val settings = Settings(sound = false, voice = true, voiceVolume = 5, music = true, musicVolume = 25)

    @Test
    fun bundledAacDecodesAdvancesAndIsConfiguredToLoop() = withAudio { fixture ->
        val context = instrumentation.targetContext
        val catalog = context.assets.open("music/catalog.json").bufferedReader().use { JSONObject(it.readText()) }
        val extractor = MediaExtractor()
        try {
            context.assets.openFd(catalog.getString("path")).use { file ->
                extractor.setDataSource(file.fileDescriptor, file.startOffset, file.length)
            }
            assertTrue("The bundled track must contain an audio stream", extractor.trackCount > 0)
            assertEquals("The packaged track must be AAC", MediaFormat.MIMETYPE_AUDIO_AAC,
                extractor.getTrackFormat(0).getString(MediaFormat.KEY_MIME))
        } finally {
            extractor.release()
        }

        onMain {
            assertFalse("Music starts disabled until settings are applied", fixture.music.enabled)
            assertFalse("Music starts inactive until its Activity is foreground", fixture.music.active)
            assertFalse("Constructing the player must not play it", fixture.music.isPlaying)
        }
        startMusic(fixture)
        val startedAt = onMain {
            assertTrue("The real prepared MediaPlayer must loop the bundled track", fixture.music.isLooping)
            assertEquals("The default music setting must apply 25 percent gain", 0.25f, fixture.music.effectiveVolume, 0.001f)
            fixture.music.currentPositionMs
        }
        await("AAC playback must advance after preparation") {
            onMain { fixture.music.isPlaying && fixture.music.currentPositionMs > startedAt + 200 }
        }
    }

    @Test
    fun muteBackgroundAndManualPauseKeepTheMusicPosition() = withAudio { fixture ->
        startMusic(fixture)

        val muted = pauseAndCheckPosition(fixture, "Zero volume") {
            fixture.audio.apply(settings.copy(musicVolume = 0))
        }
        onMain { assertEquals("A zero volume setting applies zero player gain", 0f, fixture.music.effectiveVolume, 0.001f) }
        resumeFromPosition(fixture, muted, "Restoring volume") { fixture.audio.apply(settings) }

        val switchedOff = pauseAndCheckPosition(fixture, "Music disabled") {
            fixture.audio.apply(settings.copy(music = false))
        }
        resumeFromPosition(fixture, switchedOff, "Enabling music") { fixture.audio.apply(settings) }

        val backgrounded = pauseAndCheckPosition(fixture, "Activity backgrounded") {
            fixture.audio.setForeground(fixture.owner, false)
        }
        onMain { assertFalse("Background music must be inactive outside the foreground", fixture.music.active) }
        resumeFromPosition(fixture, backgrounded, "Returning to the foreground") {
            fixture.audio.setForeground(fixture.owner, true)
        }

        val manuallyPaused = pauseAndCheckPosition(fixture, "Table manually paused") {
            fixture.audio.setPaused(fixture.owner, true)
        }
        onMain {
            fixture.audio.setForeground(fixture.owner, false)
            fixture.audio.setForeground(fixture.owner, true)
        }
        checkPausedPosition(fixture, "A foreground round trip must preserve the manual table pause", manuallyPaused)
        resumeFromPosition(fixture, manuallyPaused, "Player resumes the table") {
            fixture.audio.setPaused(fixture.owner, false)
        }

        val beforeTableChange = onMain { fixture.music.currentPositionMs }
        onMain {
            fixture.audio.stop(fixture.owner)
            assertTrue("Clearing a table's announcements must leave foreground music playing", fixture.music.isPlaying)
        }
        await("Changing tables must continue the same music loop") {
            onMain { fixture.music.isPlaying && fixture.music.currentPositionMs > beforeTableChange + 150 }
        }
    }

    @Test
    fun staleOwnerCannotStopNewOwnerAndRealSpeechDucksThenRestoresMusic() = withAudio { fixture ->
        startMusic(fixture)
        val oldOwner = fixture.owner
        val beforeReplacement = pauseAndCheckPosition(fixture, "Old owner manually paused") {
            fixture.audio.setPaused(oldOwner, true)
        }
        val newOwner = Any()
        fixture.owner = newOwner
        resumeFromPosition(fixture, beforeReplacement, "New foreground owner") {
            fixture.audio.setForeground(newOwner, true)
            // A new onStart can arrive before the old Activity's onStop/onCleared.
            fixture.audio.setForeground(oldOwner, false)
            fixture.audio.stop(oldOwner)
            fixture.audio.setPaused(oldOwner, true)
        }
        onMain {
            assertTrue("Stale lifecycle events must not deactivate the new owner's music", fixture.music.active)
            assertTrue("Stale lifecycle events must not pause the new owner's player", fixture.music.isPlaying)
        }

        val speechStarted = AtomicBoolean(false)
        val duckedGain = AtomicReference<Float>()
        val musicPlayingDuringSpeech = AtomicBoolean(false)
        onMain {
            val originalCallback = fixture.voice.onSpeakingChanged
            fixture.voice.onSpeakingChanged = { speaking ->
                originalCallback?.invoke(speaking)
                if (speaking) {
                    duckedGain.set(fixture.music.effectiveVolume)
                    musicPlayingDuringSpeech.set(fixture.music.isPlaying)
                    speechStarted.set(true)
                }
            }
            fixture.voice.enqueue(listOf(VoiceLine("redeal", 0, important = true)))
        }
        await("The packaged voice recording must actually start") { speechStarted.get() }
        assertTrue("Music must continue playing while the voice recording speaks", musicPlayingDuringSpeech.get())
        assertEquals("Real speech must lower the music's applied gain", 0.05f, duckedGain.get(), 0.001f)
        onMain {
            fixture.audio.stop(oldOwner)
            assertTrue("Clearing the stale owner must not discard the new owner's recording", fixture.voice.busy)
        }
        await("Speech completion must restore the configured music gain") {
            onMain { !fixture.voice.busy && fixture.music.isPlaying && abs(fixture.music.effectiveVolume - 0.25f) < 0.001f }
        }

        onMain {
            fixture.music.setVoiceDucking(true)
            assertEquals("Explicit ducking uses the same gain as speech", 0.05f, fixture.music.effectiveVolume, 0.001f)
            fixture.music.setVoiceDucking(false)
            assertEquals("Removing ducking restores the configured volume", 0.25f, fixture.music.effectiveVolume, 0.001f)
        }
    }

    @Test
    fun transientAudioFocusLossPausesAndRegainResumesTheSameTrack() = withAudio { fixture ->
        startMusic(fixture)
        val manager = instrumentation.targetContext.getSystemService(AudioManager::class.java)
        val interruptingRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setOnAudioFocusChangeListener({}, Handler(Looper.getMainLooper()))
            .build()
        try {
            val grant = onMain { manager.requestAudioFocus(interruptingRequest) }
            assertEquals("The foreground Activity must be allowed to request transient focus", AudioManager.AUDIOFOCUS_REQUEST_GRANTED, grant)
            await("Real transient focus loss must pause music") {
                onMain { !fixture.music.isPlaying && !fixture.music.active }
            }
            val interruptedAt = onMain { fixture.music.currentPositionMs }
            onMain {
                fixture.audio.stop(fixture.owner)
                fixture.audio.onUserInteraction(fixture.owner)
                assertFalse("A table change or tap must keep waiting for transient focus gain", fixture.music.isPlaying)
                assertFalse("A table change or tap must not reactivate interrupted music", fixture.music.active)
            }
            checkPausedPosition(fixture, "Audio focus interruption", interruptedAt)
            resumeFromPosition(fixture, interruptedAt, "Regaining audio focus") {
                manager.abandonAudioFocusRequest(interruptingRequest)
            }
        } finally {
            onMain { manager.abandonAudioFocusRequest(interruptingRequest) }
        }
    }

    private fun startMusic(fixture: AudioFixture) {
        onMain {
            fixture.audio.apply(settings)
            fixture.audio.setForeground(fixture.owner, true)
        }
        await("The real bundled music must decode and start playing") {
            onMain { fixture.music.isPlaying && fixture.music.currentPositionMs >= 700 }
        }
    }

    private fun pauseAndCheckPosition(fixture: AudioFixture, reason: String, pause: () -> Unit): Int {
        onMain(pause)
        await("$reason must stop audible music") { onMain { !fixture.music.isPlaying } }
        val position = onMain { fixture.music.currentPositionMs }
        checkPausedPosition(fixture, reason, position)
        return position
    }

    private fun checkPausedPosition(fixture: AudioFixture, reason: String, position: Int) {
        SystemClock.sleep(300)
        onMain {
            assertFalse("$reason must leave the MediaPlayer paused", fixture.music.isPlaying)
            val after = fixture.music.currentPositionMs
            assertTrue("$reason must freeze the playback position ($position -> $after)", abs(after - position) <= 150)
            assertTrue("$reason must preserve the prepared looping track", fixture.music.isLooping)
        }
    }

    private fun resumeFromPosition(fixture: AudioFixture, position: Int, reason: String, resume: () -> Unit) {
        onMain(resume)
        await("$reason must restart the paused player") { onMain { fixture.music.isPlaying } }
        onMain {
            val after = fixture.music.currentPositionMs
            assertTrue("$reason must resume its existing position, not restart at zero ($position -> $after)", after >= position - 150)
        }
        await("$reason must advance from the retained position") {
            onMain { fixture.music.isPlaying && fixture.music.currentPositionMs > position + 150 }
        }
    }

    private fun withAudio(test: (AudioFixture) -> Unit) {
        lateinit var vm: GameViewModel
        rule.runOnIdle { vm = ViewModelProvider(rule.activity)[GameViewModel::class.java] }
        rule.waitUntil(20_000) { vm.appData.value != null }
        val container = (rule.activity.application as DdzApp).container
        val previousSettings = onMain { vm.appData.value!!.settings }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        var sound: SoundManager? = null
        var voice: VoiceAnnouncer? = null
        var fixture: AudioFixture? = null
        try {
            val created = onMain {
                // The Activity remains foreground for Android's focus policy; its singleton loop
                // stays quiet so these independent real players do not compete with it.
                container.audio.apply(previousSettings.copy(music = false))
                val effects = SoundManager(instrumentation.targetContext, scope).also { sound = it }
                val announcer = VoiceAnnouncer(instrumentation.targetContext).also { voice = it }
                AudioFixture(AppAudio(instrumentation.targetContext, effects, announcer), announcer)
                    .also { fixture = it }
            }
            test(created)
        } finally {
            try {
                onMain {
                    fixture?.let {
                        it.audio.setForeground(it.owner, false)
                        it.audio.stop(it.owner)
                        it.music.release()
                    }
                    voice?.release()
                    sound?.release()
                    container.audio.apply(previousSettings)
                    // Independent audio focus can interrupt the singleton. Restore its actual
                    // ViewModel owner while the test Activity is still foreground.
                    vm.setForeground(true)
                }
            } finally {
                scope.cancel()
            }
        }
    }

    private fun <T> onMain(action: () -> T): T {
        var result: Result<T>? = null
        instrumentation.runOnMainSync { result = runCatching(action) }
        return result!!.getOrThrow()
    }

    private fun await(message: String, timeoutMs: Long = 10_000, condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (!condition() && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(25)
        assertTrue("$message (within ${timeoutMs} ms)", condition())
    }

    private class AudioFixture(val audio: AppAudio, val voice: VoiceAnnouncer) {
        val music: BackgroundMusic get() = audio.music
        var owner: Any = Any()
    }
}
