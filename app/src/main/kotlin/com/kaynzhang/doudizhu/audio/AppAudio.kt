package com.kaynzhang.doudizhu.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.kaynzhang.doudizhu.data.Settings

/** Shares audio focus between effects and voices; interruptions discard stale playback. */
class AppAudio(context: Context, val sound: SoundManager, val voice: VoiceAnnouncer) {
    private val manager = context.getSystemService(AudioManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private var foregroundOwner: Any? = null
    private var foreground = false
    private var paused = false
    private var interrupted = false
    private var transientInterruption = false
    private var focused = false
    private var effectUntil = 0L
    private val releaseFocus = Runnable {
        if (!voice.busy && SystemClock.uptimeMillis() >= effectUntil) abandonFocus() else scheduleRelease()
    }
    private val request: AudioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
        .setWillPauseWhenDucked(true)
        .setOnAudioFocusChangeListener({ change ->
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
                    manager.abandonAudioFocusRequest(request)
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

    init {
        sound.beforePlay = ::obtainFocus
        voice.beforePlay = ::obtainFocus
        sound.onPlayback = { duration ->
            effectUntil = maxOf(effectUntil, SystemClock.uptimeMillis() + duration)
            scheduleRelease()
        }
        voice.onSpeakingChanged = { speaking ->
            sound.setVoiceDucking(speaking)
            if (speaking) handler.removeCallbacks(releaseFocus) else scheduleRelease()
        }
        updateActive()
    }

    fun apply(settings: Settings) = onMain {
        sound.enabled = settings.sound
        sound.volume = settings.soundVolume.coerceIn(0, 100) / 100f
        voice.enabled = settings.voice
        voice.volume = settings.voiceVolume.coerceIn(0, 100) / 100f
    }

    fun setForeground(owner: Any, value: Boolean) = onMain {
        if (value) {
            if (foregroundOwner !== owner) {
                // A new Activity's ViewModel can start before the previous Activity stops.
                // Its audio must not inherit the old table's pause or queued announcements.
                sound.stop()
                voice.stop()
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
        if (!value) interrupted = false
        updateActive()
        if (value) abandonFocus()
    }

    fun stop(owner: Any) = onMain {
        if (foregroundOwner !== owner) return@onMain
        sound.stop()
        voice.stop()
        abandonFocus()
    }

    /** A new tap may reclaim permanently lost focus; temporary call interruptions wait for gain. */
    fun onUserInteraction(owner: Any) = onMain {
        if (foregroundOwner !== owner) return@onMain
        if (interrupted && !transientInterruption) { interrupted = false; updateActive() }
    }

    private fun updateActive() {
        val active = foreground && !paused && !interrupted
        sound.active = active
        voice.active = active
    }

    private fun obtainFocus(): Boolean {
        if (!foreground || paused || interrupted) return false
        handler.removeCallbacks(releaseFocus)
        if (!focused) focused = manager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        if (focused) scheduleRelease()
        return focused
    }

    private fun scheduleRelease() {
        handler.removeCallbacks(releaseFocus)
        if (interrupted) return
        handler.postDelayed(releaseFocus, (effectUntil - SystemClock.uptimeMillis()).coerceAtLeast(0) + 250)
    }

    private fun abandonFocus() {
        handler.removeCallbacks(releaseFocus)
        manager.abandonAudioFocusRequest(request)
        focused = false
        effectUntil = 0
    }

    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == handler.looper) block() else handler.post { block() }
    }
}
