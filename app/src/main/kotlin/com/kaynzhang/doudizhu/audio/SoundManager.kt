package com.kaynzhang.doudizhu.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Short, layered effects. All SoundPool state is confined to the main thread. */
class SoundManager(context: Context, scope: CoroutineScope) {
    private val handler = Handler(Looper.getMainLooper())
    private val pool = SoundPool.Builder().setMaxStreams(6)
        .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build()
    private val ids = IntArray(Sfx.entries.size)
    private val durations = LongArray(Sfx.entries.size)
    private val loaded = mutableSetOf<Int>()
    private val streams = mutableMapOf<Int, Float>()
    private val pending = mutableMapOf<Sfx, Pair<Long, Float>>()
    private val lastPlayed = LongArray(Sfx.entries.size) { Long.MIN_VALUE / 2 }
    private var released = false
    private var duck = 1f

    var beforePlay: () -> Boolean = { true }
    var onPlayback: (Long) -> Unit = {}

    @Volatile var enabled = true
        set(value) { field = value; if (!value) onMain { stopNow() } }
    @Volatile var active = true
        set(value) { field = value; if (!value) onMain { stopNow() } }
    @Volatile var volume = 0.75f
        set(value) {
            field = value.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0f
            onMain { if (field == 0f) stopNow() else updateVolumes() }
        }

    init {
        pool.setOnLoadCompleteListener { _, id, status ->
            onMain {
                if (!released && status == 0) {
                    loaded += id
                    val sfx = Sfx.entries.firstOrNull { ids[it.ordinal] == id }
                    if (sfx != null) pending.remove(sfx)?.let { (requested, gain) ->
                        if (SystemClock.uptimeMillis() - requested <= STARTUP_GRACE_MS) playNow(sfx, gain)
                    }
                } else if (status != 0) Log.w(TAG, "sample $id failed to load ($status)")
            }
        }
        val dir = File(context.noBackupFilesDir, "sfx_v${SoundSynth.VERSION}")
        scope.launch(Dispatchers.IO) {
            if (!dir.isDirectory && !dir.mkdirs()) {
                Log.w(TAG, "cannot create effect cache")
                return@launch
            }
            for (sfx in Sfx.entries) {
                try {
                    val file = File(dir, "${sfx.name.lowercase()}.wav")
                    if (!file.exists()) {
                        val tmp = File(dir, "${file.name}.tmp")
                        tmp.writeBytes(SoundSynth.render(sfx))
                        check(tmp.renameTo(file)) { "cannot publish ${file.name}" }
                    }
                    val duration = (file.length() - 44).coerceAtLeast(0) * 1_000 / (SoundSynth.RATE * 2)
                    withContext(Dispatchers.Main) {
                        if (!released) {
                            durations[sfx.ordinal] = duration
                            ids[sfx.ordinal] = pool.load(file.path, sfx.priority)
                        }
                    }
                } catch (e: Exception) { Log.w(TAG, "could not prepare $sfx", e) }
            }
        }
    }

    fun play(sfx: Sfx, volume: Float = 1f) = onMain { playNow(sfx, volume) }

    fun durationMs(sfx: Sfx): Long = durations[sfx.ordinal].takeIf { it > 0 } ?: 350L

    private fun playNow(sfx: Sfx, gain: Float) {
        if (released || !enabled || !active || volume <= 0f || !gain.isFinite()) return
        val id = ids[sfx.ordinal]
        val now = SystemClock.uptimeMillis()
        if (now - lastPlayed[sfx.ordinal] < sfx.minIntervalMs) return
        if (id == 0 || id !in loaded) {
            // Only a recent request may survive startup; old sounds never replay on returning.
            pending[sfx] = now to gain
            return
        }
        if (!beforePlay()) return
        val base = (sfx.volume * gain.coerceIn(0f, 1f)).coerceIn(0f, 1f)
        val level = base * volume * duck
        val stream = pool.play(id, level, level, sfx.priority, 0, 1f)
        if (stream == 0) return
        lastPlayed[sfx.ordinal] = now
        streams[stream] = base
        val duration = durations[sfx.ordinal]
        onPlayback(duration)
        handler.postDelayed({ streams.remove(stream) }, duration + 80)
    }

    fun setVoiceDucking(speaking: Boolean) = onMain {
        duck = if (speaking) 0.32f else 1f
        updateVolumes()
    }

    private fun updateVolumes() {
        if (released) return
        streams.forEach { (id, base) ->
            val level = base * volume * duck
            pool.setVolume(id, level, level)
        }
    }

    fun stop() = onMain { stopNow() }
    fun pause() { active = false }
    fun resume() { active = true }

    private fun stopNow() {
        pending.clear()
        if (!released) streams.keys.forEach(pool::stop)
        streams.clear()
        lastPlayed.fill(Long.MIN_VALUE / 2)
    }

    fun release() = onMain {
        if (!released) { stopNow(); released = true; pool.release() }
    }

    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == handler.looper) block() else handler.post { block() }
    }

    companion object {
        private const val TAG = "SoundManager"
        private const val STARTUP_GRACE_MS = 250L
    }
}
