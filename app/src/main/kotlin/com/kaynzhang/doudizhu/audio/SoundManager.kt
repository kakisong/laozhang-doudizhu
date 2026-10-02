package com.kaynzhang.doudizhu.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

/**
 * App-scoped sound effects. The synthesized WAVs are written once to `noBackupFilesDir`
 * (unlike the cache, the system never purges it) and loaded into a [SoundPool].
 * Playing before a sample has loaded is silently skipped.
 */
class SoundManager(context: Context, scope: CoroutineScope) {

    private val pool = SoundPool.Builder()
        .setMaxStreams(6)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()

    private val ids = IntArray(Sfx.entries.size)

    @Volatile
    var enabled = true

    init {
        val dir = File(context.noBackupFilesDir, "sfx_v1")
        scope.launch(Dispatchers.IO) {
            dir.mkdirs()
            for (sfx in Sfx.entries) {
                try {
                    val file = File(dir, sfx.name.lowercase() + ".wav")
                    if (!file.exists()) {
                        val tmp = File(dir, file.name + ".tmp")
                        tmp.writeBytes(SoundSynth.render(sfx))
                        tmp.renameTo(file)
                    }
                    ids[sfx.ordinal] = pool.load(file.path, 1)
                } catch (e: Exception) {
                    Log.w("SoundManager", "could not prepare $sfx", e)
                }
            }
        }
    }

    fun play(sfx: Sfx, volume: Float = 1f) {
        if (!enabled) return
        val id = ids[sfx.ordinal]
        if (id != 0) pool.play(id, volume, volume, 1, 0, 1f)
    }

    fun pause() = pool.autoPause()

    fun resume() = pool.autoResume()
}
