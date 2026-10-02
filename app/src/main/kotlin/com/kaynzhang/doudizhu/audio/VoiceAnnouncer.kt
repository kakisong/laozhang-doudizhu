package com.kaynzhang.doudizhu.audio

import android.content.Context
import android.content.res.AssetManager
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.kaynzhang.doudizhu.engine.game.GameEvent
import com.kaynzhang.doudizhu.engine.rules.Combo
import org.json.JSONObject

/** Plays the three separately generated voices bundled with the game, entirely offline. */
class VoiceAnnouncer(context: Context) {
    private val assets = context.applicationContext.assets
    private val clips = loadCatalog(assets)
    private val main = Handler(Looper.getMainLooper())
    private val queue = VoiceQueue()
    private var player: MediaPlayer? = null
    private var generation = 0L
    private var speaking = false
    private var playbackEndsAtMs = 0L
    private var nextPumpAtMs = 0L
    private var watchdogTask: Runnable? = null
    private val pumpTask = Runnable { nextPumpAtMs = 0L; pump() }

    @Volatile private var released = false
    @Volatile private var timing = Timing()

    @Volatile
    var enabled = true
        set(value) {
            field = value
            if (!value) onMain { stopInternal() }
        }

    /** Leaving the foreground discards speech; returning never replays an old turn. */
    @Volatile
    var active = true
        set(value) {
            field = value
            if (!value) onMain { stopInternal() }
        }

    @Volatile
    var volume = 1f
        set(value) {
            val normalized = if (value.isFinite()) value.coerceIn(0f, 1f) else 1f
            field = normalized
            onMain {
                if (normalized == 0f) stopInternal()
                else runCatching { player?.setVolume(field, field) }
            }
        }

    /** The app's audio-focus gate. Denied focus clears speech instead of delaying it. */
    @Volatile
    var beforePlay: () -> Boolean = { true }

    /** Main-thread callback used to lower effects while speech is audible. */
    @Volatile
    var onSpeakingChanged: ((Boolean) -> Unit)? = null

    val busy: Boolean
        get() {
            val snapshot = timing
            val now = SystemClock.elapsedRealtime()
            return snapshot.hasPlayer || snapshot.pending.any { !it.expired(now) }
        }

    /** Includes the current recording and valid queued lines, for bot turn pacing. */
    val estimatedRemainingMs: Long
        get() {
            val snapshot = timing
            val now = SystemClock.elapsedRealtime()
            val current = (snapshot.playbackEndsAtMs - now).coerceAtLeast(0L)
            val gap = (snapshot.nextPumpAtMs - now).coerceAtLeast(0L)
            return current + gap + snapshot.pending.filterNot { it.expired(now) }
                .sumOf { it.durationMs + GAP_MS }
        }

    val nextAllowedAt: Long get() = SystemClock.elapsedRealtime() + estimatedRemainingMs

    fun durationMs(line: VoiceLine): Long = clips[line.seat to line.key]?.durationMs ?: 0L

    /** Adds one transition as a batch so a following alert cannot cut off the card announcement. */
    fun enqueue(lines: List<VoiceLine>) {
        if (released || !enabled || !active || volume == 0f || lines.isEmpty()) return
        val batch = lines.toList()
        onMain {
            if (released || !enabled || !active || volume == 0f) return@onMain
            val now = SystemClock.elapsedRealtime()
            queue.enqueue(batch, now) { line ->
                clips[line.seat to line.key]?.durationMs.also {
                    if (it == null) Log.w(TAG, "missing generated voice: seat=${line.seat}, key=${line.key}")
                }
            }
            publishTiming()
            if (player == null && nextPumpAtMs == 0L) pump()
        }
    }

    /** Compatibility entry point; only catalog phrases can be played. */
    fun say(text: String, seat: Int, interrupt: Boolean = true) {
        val key = VoicePhrases.keyForText(text) ?: run {
            Log.w(TAG, "no prerecorded line for requested phrase")
            return
        }
        if (released || !enabled || !active || volume == 0f) return
        onMain {
            if (interrupt) stopInternal()
            enqueue(listOf(VoiceLine(key, seat.coerceIn(0, 2))))
        }
    }

    fun stop() = onMain { stopInternal() }

    fun release() {
        released = true
        onMain { stopInternal() }
    }

    private fun pump() {
        if (released || !enabled || !active || volume == 0f) {
            stopInternal()
            return
        }
        if (player != null) return
        val item = queue.poll(SystemClock.elapsedRealtime())
        if (item == null) {
            playbackEndsAtMs = 0L
            publishTiming()
            setSpeaking(false)
            return
        }
        val clip = clips[item.line.seat to item.line.key]
        if (clip == null) {
            main.post(pumpTask)
            return
        }

        val token = ++generation
        val created = try {
            MediaPlayer()
        } catch (failure: Exception) {
            Log.w(TAG, "could not allocate voice player", failure)
            scheduleNext()
            return
        }
        player = created
        playbackEndsAtMs = SystemClock.elapsedRealtime() + clip.durationMs + PREPARE_ALLOWANCE_MS
        publishTiming()
        try {
            created.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            created.setVolume(volume, volume)
            created.setOnPreparedListener { prepared ->
                if (!isCurrent(prepared, token)) return@setOnPreparedListener
                if (!enabled || !active || volume == 0f || !runCatching { beforePlay() }.getOrDefault(false)) {
                    stopInternal()
                    return@setOnPreparedListener
                }
                // Audio focus can synchronously stop us while beforePlay is requesting it.
                if (!isCurrent(prepared, token) || !enabled || !active || volume == 0f) return@setOnPreparedListener
                try {
                    val duration = prepared.duration.coerceAtLeast(1).toLong()
                    playbackEndsAtMs = SystemClock.elapsedRealtime() + duration
                    prepared.start()
                    armWatchdog(prepared, token, duration + COMPLETION_ALLOWANCE_MS)
                    setSpeaking(true)
                    publishTiming()
                } catch (failure: Exception) {
                    Log.w(TAG, "could not start ${clip.path}", failure)
                    finishPlayback(prepared, token)
                }
            }
            created.setOnCompletionListener { finished -> finishPlayback(finished, token) }
            created.setOnErrorListener { failed, what, extra ->
                if (isCurrent(failed, token)) {
                    Log.w(TAG, "voice playback error $what/$extra: ${clip.path}")
                    finishPlayback(failed, token)
                }
                true
            }
            assets.openFd(clip.path).use { descriptor ->
                created.setDataSource(descriptor.fileDescriptor, descriptor.startOffset, descriptor.length)
            }
            created.prepareAsync()
            armWatchdog(created, token, PREPARE_TIMEOUT_MS)
        } catch (failure: Exception) {
            Log.w(TAG, "could not prepare ${clip.path}", failure)
            finishPlayback(created, token)
        }
    }

    private fun finishPlayback(finished: MediaPlayer, token: Long) {
        if (!isCurrent(finished, token)) return
        cancelWatchdog()
        player = null
        playbackEndsAtMs = 0L
        releasePlayer(finished)
        scheduleNext()
    }

    private fun scheduleNext() {
        if (queue.snapshot(SystemClock.elapsedRealtime()).isEmpty()) {
            setSpeaking(false)
            publishTiming()
        } else {
            nextPumpAtMs = SystemClock.elapsedRealtime() + GAP_MS
            main.postDelayed(pumpTask, GAP_MS)
            publishTiming()
        }
    }

    private fun stopInternal() {
        ++generation
        main.removeCallbacks(pumpTask)
        cancelWatchdog()
        queue.clear()
        val old = player
        player = null
        playbackEndsAtMs = 0L
        nextPumpAtMs = 0L
        if (old != null) releasePlayer(old)
        publishTiming()
        setSpeaking(false)
    }

    private fun isCurrent(candidate: MediaPlayer, token: Long): Boolean =
        !released && player === candidate && generation == token

    private fun publishTiming() {
        timing = Timing(player != null, playbackEndsAtMs, nextPumpAtMs, queue.snapshot(SystemClock.elapsedRealtime()))
    }

    private fun setSpeaking(value: Boolean) {
        if (speaking == value) return
        speaking = value
        runCatching { onSpeakingChanged?.invoke(value) }
            .onFailure { Log.w(TAG, "speaking callback failed", it) }
    }

    private fun onMain(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) action() else main.post { action() }
    }

    private fun releasePlayer(old: MediaPlayer) {
        runCatching {
            old.setOnPreparedListener(null)
            old.setOnCompletionListener(null)
            old.setOnErrorListener(null)
        }
        runCatching { old.release() }.onFailure { Log.w(TAG, "could not release voice player", it) }
    }

    private fun armWatchdog(candidate: MediaPlayer, token: Long, timeoutMs: Long) {
        cancelWatchdog()
        val watchdog = Runnable {
            if (isCurrent(candidate, token)) {
                Log.w(TAG, "voice player timed out; continuing queue")
                finishPlayback(candidate, token)
            }
        }
        watchdogTask = watchdog
        main.postDelayed(watchdog, timeoutMs)
    }

    private fun cancelWatchdog() {
        watchdogTask?.let(main::removeCallbacks)
        watchdogTask = null
    }

    private data class Timing(
        val hasPlayer: Boolean = false,
        val playbackEndsAtMs: Long = 0L,
        val nextPumpAtMs: Long = 0L,
        val pending: List<PendingVoice> = emptyList(),
    )

    private data class Clip(val path: String, val durationMs: Long)

    companion object {
        private const val TAG = "VoiceAnnouncer"
        private const val GAP_MS = 70L
        private const val PREPARE_ALLOWANCE_MS = 150L
        private const val PREPARE_TIMEOUT_MS = 5_000L
        private const val COMPLETION_ALLOWANCE_MS = 1_000L

        /** Legacy phrase helpers retain their source-compatible API. */
        fun phrase(event: GameEvent, seq: Int): String? = VoicePhrases.key(event, seq)?.let(VoicePhrases::text)
        fun combo(combo: Combo): String = VoicePhrases.text(VoicePhrases.comboKey(combo)) ?: combo.type.zh

        private fun loadCatalog(assets: AssetManager): Map<Pair<Int, String>, Clip> = try {
            val json = assets.open("voices/catalog.json").bufferedReader().use { JSONObject(it.readText()) }
            val voices = json.getJSONObject("voices")
            buildMap {
                for (seat in 0..2) {
                    val lines = voices.optJSONObject(seat.toString()) ?: continue
                    for (key in lines.keys()) {
                        val entry = lines.getJSONObject(key)
                        val path = entry.getString("path")
                        val duration = entry.getLong("durationMs")
                        if (path.startsWith("voices/$seat/") && ".." !in path && duration in 1L..30_000L) {
                            put(seat to key, Clip(path, duration))
                        }
                    }
                }
            }
        } catch (failure: Exception) {
            Log.w(TAG, "generated voice catalog unavailable; speech stays silent", failure)
            emptyMap()
        }
    }
}
