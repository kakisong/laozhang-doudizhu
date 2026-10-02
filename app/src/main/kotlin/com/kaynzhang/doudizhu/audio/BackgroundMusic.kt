package com.kaynzhang.doudizhu.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.util.Log

/** One bundled loop. Pausing keeps its position; speech and effects use separate players. */
class BackgroundMusic(context: Context) {
    private val assets = context.applicationContext.assets
    private val main = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private var prepared = false
    private var failed = false
    private var ducked = false
    private var prepareTimeout: Runnable? = null
    @Volatile private var released = false

    @Volatile
    var enabled = false
        set(value) {
            val changed = field != value
            field = value
            onMain {
                if (changed && value) failed = false
                updatePlayback()
            }
        }

    @Volatile
    var active = false
        set(value) {
            val changed = field != value
            field = value
            onMain {
                if (changed && value) failed = false
                updatePlayback()
            }
        }

    @Volatile
    var volume = 0.25f
        set(value) {
            field = if (value.isFinite()) value.coerceIn(0f, 1f) else 0.25f
            onMain { updatePlayback() }
        }

    @Volatile var beforePlay: () -> Boolean = { true }
    @Volatile var onPlayingChanged: ((Boolean) -> Unit)? = null
    @Volatile var isPlaying = false
        private set
    @Volatile var isLooping = false
        private set
    @Volatile var effectiveVolume = 0.25f
        private set

    /** Read on the main thread, like the underlying MediaPlayer. */
    val currentPositionMs: Int
        get() = if (prepared) runCatching { player?.currentPosition ?: 0 }.getOrDefault(0) else 0

    fun setVoiceDucking(speaking: Boolean) = onMain {
        ducked = speaking
        applyVolume()
    }

    /** Retries a denied audio-focus request on a new user interaction. */
    fun resume() = onMain { updatePlayback() }

    fun release() {
        released = true
        onMain { releasePlayer() }
    }

    private fun updatePlayback() {
        if (released) return
        applyVolume()
        if (!enabled || !active || volume == 0f) {
            if (isPlaying) {
                try {
                    player?.pause()
                    setPlaying(false)
                } catch (failure: Exception) {
                    fail(failure)
                }
            }
            return
        }
        if (player == null) {
            if (!failed) prepare()
            return
        }
        if (!prepared || isPlaying) return
        val current = player ?: return
        if (!runCatching { beforePlay() }.getOrDefault(false)) return
        // Focus changes can synchronously pause or release this player.
        if (released || current !== player || isPlaying || !enabled || !active || volume == 0f) return
        try {
            current.start()
            setPlaying(true)
        } catch (failure: Exception) {
            fail(failure)
        }
    }

    private fun prepare() {
        try {
            val created = MediaPlayer()
            player = created
            created.setAudioAttributes(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            created.setOnPreparedListener { ready ->
                if (released || player !== ready) return@setOnPreparedListener
                cancelTimeout()
                prepared = true
                ready.isLooping = true
                isLooping = true
                updatePlayback()
            }
            created.setOnErrorListener { broken, what, extra ->
                if (player === broken) fail(IllegalStateException("music playback error $what/$extra"))
                true
            }
            assets.openFd(ASSET).use { file ->
                created.setDataSource(file.fileDescriptor, file.startOffset, file.length)
            }
            created.prepareAsync()
            prepareTimeout = Runnable {
                if (player === created && !prepared) fail(IllegalStateException("music preparation timed out"))
            }.also { main.postDelayed(it, 8_000L) }
        } catch (failure: Exception) {
            fail(failure)
        }
    }

    private fun applyVolume() {
        effectiveVolume = volume * if (ducked) 0.2f else 1f
        if (prepared) runCatching { player?.setVolume(effectiveVolume, effectiveVolume) }
    }

    private fun setPlaying(value: Boolean) {
        if (isPlaying == value) return
        isPlaying = value
        runCatching { onPlayingChanged?.invoke(value) }
            .onFailure { Log.w(TAG, "playback callback failed", it) }
    }

    private fun fail(failure: Exception) {
        Log.w(TAG, "could not play bundled music", failure)
        failed = true
        releasePlayer()
    }

    private fun releasePlayer() {
        cancelTimeout()
        val old = player
        player = null
        prepared = false
        isLooping = false
        runCatching {
            old?.setOnPreparedListener(null)
            old?.setOnErrorListener(null)
        }
        runCatching { old?.release() }
        setPlaying(false)
    }

    private fun cancelTimeout() {
        prepareTimeout?.let(main::removeCallbacks)
        prepareTimeout = null
    }

    private fun onMain(action: () -> Unit) {
        if (Looper.myLooper() == main.looper) action() else main.post { action() }
    }

    companion object {
        private const val TAG = "DdzMusic"
        private const val ASSET = "music/table_theme.m4a"
    }
}
