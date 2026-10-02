package com.kaynzhang.doudizhu.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.kaynzhang.doudizhu.data.Settings

/** Shares focus between the music loop, effects and voices; interruptions discard stale speech. */
class AppAudio(
    context: Context,
    val sound: SoundManager,
    val voice: VoiceAnnouncer,
    val music: BackgroundMusic = BackgroundMusic(context),
) {
    private val manager = context.getSystemService(AudioManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private var foregroundOwner: Any? = null
    private var foreground = false
    private var paused = false
    private var interrupted = false
    private var transientInterruption = false
    private var focused = false
    private var request: AudioFocusRequest? = null
    private var effectUntil = 0L
    private val releaseFocus = Runnable {
        if (!music.isPlaying && !voice.busy && SystemClock.uptimeMillis() >= effectUntil) abandonFocus()
        else scheduleRelease()
    }

    private fun createFocusRequest(): AudioFocusRequest {
        val gain = desiredFocusGain()
        lateinit var created: AudioFocusRequest
        created = AudioFocusRequest.Builder(gain)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(if (gain == AudioManager.AUDIOFOCUS_GAIN) AudioAttributes.CONTENT_TYPE_MUSIC
                    else AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setWillPauseWhenDucked(true)
            .setOnAudioFocusChangeListener({ change ->
                // Abandoned requests can still have a callback queued on the main thread.
                if (request !== created) return@setOnAudioFocusChangeListener
                when (change) {
                    AudioManager.AUDIOFOCUS_GAIN -> {
                        focused = true
                        interrupted = false
                        transientInterruption = false
                        updateActive()
                        scheduleRelease()
                    }
                    AudioManager.AUDIOFOCUS_LOSS -> {
                        focused = false
                        interrupted = true
                        transientInterruption = false
                        handler.removeCallbacks(releaseFocus)
                        updateActive()
                        abandonFocus()
                    }
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                        focused = false
                        interrupted = true
                        transientInterruption = true
                        handler.removeCallbacks(releaseFocus)
                        updateActive()
                    }
                }
            }, handler).build()
        return created
    }

    init {
        sound.beforePlay = ::obtainFocus
        voice.beforePlay = ::obtainFocus
        music.beforePlay = ::obtainFocus
        music.onPlayingChanged = { playing ->
            if (playing) handler.removeCallbacks(releaseFocus) else scheduleRelease()
        }
        sound.onPlayback = { duration ->
            effectUntil = maxOf(effectUntil, SystemClock.uptimeMillis() + duration)
            scheduleRelease()
        }
        voice.onSpeakingChanged = { speaking ->
            sound.setVoiceDucking(speaking)
            music.setVoiceDucking(speaking)
            if (speaking) handler.removeCallbacks(releaseFocus) else scheduleRelease()
        }
        updateActive()
    }

    fun apply(settings: Settings) = onMain {
        sound.enabled = settings.sound
        sound.volume = settings.soundVolume.coerceIn(0, 100) / 100f
        voice.enabled = settings.voice
        voice.volume = settings.voiceVolume.coerceIn(0, 100) / 100f
        music.volume = settings.musicVolume.coerceIn(0, 100) / 100f
        music.enabled = settings.music
        if (focused && request?.focusGain != desiredFocusGain() &&
            (music.isPlaying || voice.busy || SystemClock.uptimeMillis() < effectUntil)) {
            obtainFocus()
        }
        scheduleRelease()
    }

    fun setForeground(owner: Any, value: Boolean) = onMain {
        if (value) {
            if (foregroundOwner !== owner) {
                // A new Activity's ViewModel can start before the previous Activity stops.
                // Its audio must not inherit the old table's pause or queued announcements.
                sound.stop()
                voice.stop()
                music.active = false
                abandonFocus()
                foregroundOwner = owner
                paused = false
            }
        } else if (foregroundOwner !== owner) return@onMain
        foreground = value
        if (value) {
            interrupted = false
            transientInterruption = false
        }
        updateActive()
        if (!value) abandonFocus()
    }

    fun setPaused(owner: Any, value: Boolean) = onMain {
        if (foregroundOwner !== owner) return@onMain
        paused = value
        if (!value && !transientInterruption) interrupted = false
        updateActive()
        if (value) {
            abandonFocus()
            // This request no longer receives gain callbacks; resume must make a fresh request.
            transientInterruption = false
        }
    }

    fun stop(owner: Any) = onMain {
        if (foregroundOwner !== owner) return@onMain
        sound.stop()
        voice.stop()
        // Changing tables clears announcements without restarting the continuous music loop.
        if (!music.isPlaying && !transientInterruption) abandonFocus()
    }

    /** A new tap may reclaim permanently lost focus; temporary call interruptions wait for gain. */
    fun onUserInteraction(owner: Any) = onMain {
        if (foregroundOwner !== owner) return@onMain
        if (interrupted && !transientInterruption) { interrupted = false; updateActive() }
        if (!interrupted) music.resume()
    }

    private fun updateActive() {
        val active = foreground && !paused && !interrupted
        sound.active = active
        voice.active = active
        music.active = active
    }

    private fun obtainFocus(): Boolean {
        if (!foreground || paused || interrupted) return false
        handler.removeCallbacks(releaseFocus)
        if (request?.focusGain != desiredFocusGain()) abandonFocus(clearEffects = false)
        if (!focused) {
            val candidate = request ?: createFocusRequest().also { request = it }
            focused = manager.requestAudioFocus(candidate) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            if (!focused) {
                manager.abandonAudioFocusRequest(candidate)
                request = null
                // Switching from speech to music may already have abandoned a granted request.
                // Denial must also silence any speech/effects still using that previous focus.
                interrupted = true
                transientInterruption = false
                updateActive()
            }
        }
        if (focused) scheduleRelease()
        return focused
    }

    private fun scheduleRelease() {
        handler.removeCallbacks(releaseFocus)
        if (interrupted || music.isPlaying) return
        handler.postDelayed(releaseFocus, (effectUntil - SystemClock.uptimeMillis()).coerceAtLeast(0) + 250)
    }

    private fun desiredFocusGain(): Int = if (music.enabled && music.volume > 0f)
        AudioManager.AUDIOFOCUS_GAIN else AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK

    private fun abandonFocus(clearEffects: Boolean = true) {
        handler.removeCallbacks(releaseFocus)
        val old = request
        request = null
        if (old != null) manager.abandonAudioFocusRequest(old)
        focused = false
        if (clearEffects) effectUntil = 0
    }

    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == handler.looper) block() else handler.post { block() }
    }
}
