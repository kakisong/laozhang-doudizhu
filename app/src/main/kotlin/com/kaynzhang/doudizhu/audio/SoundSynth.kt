package com.kaynzhang.doudizhu.audio

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin

enum class Sfx {
    DEAL, PLAY, CLICK, BOMB, ROCKET, PLANE, WIN, LOSE, ALERT, TURN,
}

/**
 * Synthesizes every sound effect from code (16-bit mono PCM), so the app ships no audio
 * assets and carries no licensing baggage. Output is deterministic.
 */
object SoundSynth {
    const val RATE = 44_100

    fun render(sfx: Sfx): ByteArray = wav(
        when (sfx) {
            Sfx.DEAL -> deal()
            Sfx.PLAY -> play()
            Sfx.CLICK -> tone(1_800.0, 0.03, decay = 0.006)
            Sfx.BOMB -> bomb(0.0)
            Sfx.ROCKET -> rocket()
            Sfx.PLANE -> plane()
            Sfx.WIN -> melody(doubleArrayOf(523.25, 659.25, 783.99, 1_046.5), noteSeconds = 0.12, lastSeconds = 0.45)
            Sfx.LOSE -> melody(doubleArrayOf(392.0, 329.63, 261.63), noteSeconds = 0.2, lastSeconds = 0.55, soft = true)
            Sfx.ALERT -> beeps()
            Sfx.TURN -> tone(1_320.0, 0.18, decay = 0.05, volume = 0.45)
        },
    )

    private fun buffer(seconds: Double) = DoubleArray((seconds * RATE).toInt())

    private class Noise(seed: Long) {
        private val r = java.util.Random(seed)
        fun next(): Double = r.nextDouble() * 2 - 1
    }

    private fun deal(): DoubleArray {
        val out = buffer(0.04)
        val n = Noise(1)
        var prev = 0.0
        for (i in out.indices) {
            val t = i.toDouble() / RATE
            val x = n.next()
            out[i] = (x - prev) * exp(-t / 0.007) * 0.6 // high-passed flick
            prev = x
        }
        return out
    }

    private fun play(): DoubleArray {
        val out = buffer(0.11)
        val n = Noise(2)
        var lp = 0.0
        for (i in out.indices) {
            val t = i.toDouble() / RATE
            lp += (n.next() - lp) * 0.35
            out[i] = lp * exp(-t / 0.018) * 0.9 + sin(2 * PI * 140 * t) * exp(-t / 0.03) * 0.5
        }
        return out
    }

    private fun tone(freq: Double, seconds: Double, decay: Double, volume: Double = 0.6): DoubleArray {
        val out = buffer(seconds)
        for (i in out.indices) {
            val t = i.toDouble() / RATE
            out[i] = sin(2 * PI * freq * t) * exp(-t / decay) * volume * minOf(1.0, t / 0.002)
        }
        return out
    }

    /** Low rumble plus a noise burst; [offset] seconds of silence first (used by the rocket). */
    private fun bomb(offset: Double): DoubleArray {
        val out = buffer(offset + 1.1)
        val n = Noise(3)
        var lp = 0.0
        val start = (offset * RATE).toInt()
        for (i in start until out.size) {
            val t = (i - start).toDouble() / RATE
            lp += (n.next() - lp) * 0.08
            val rumble = sin(2 * PI * (55 - 20 * t) * t) * exp(-t / 0.35)
            out[i] = (lp * 3.0 * exp(-t / 0.22) + rumble * 0.8) * minOf(1.0, t / 0.004)
        }
        return out
    }

    private fun rocket(): DoubleArray {
        val out = bomb(0.55)
        val n = Noise(4)
        var phase = 0.0
        val rise = (0.55 * RATE).toInt()
        for (i in 0 until rise) {
            val t = i.toDouble() / RATE
            val f = 280 + 1_300 * (t / 0.55)
            phase += 2 * PI * f / RATE
            val env = (t / 0.55) * 0.5
            out[i] += (sin(phase) * 0.35 + n.next() * 0.25) * env
        }
        return out
    }

    private fun plane(): DoubleArray {
        val out = buffer(1.2)
        val n = Noise(5)
        var lp = 0.0
        for (i in out.indices) {
            val t = i.toDouble() / RATE
            val sweep = 0.02 + 0.25 * sin(PI * t / 1.2)
            lp += (n.next() - lp) * sweep
            out[i] = lp * sin(PI * t / 1.2) * 1.4
        }
        return out
    }

    private fun melody(notes: DoubleArray, noteSeconds: Double, lastSeconds: Double, soft: Boolean = false): DoubleArray {
        val total = noteSeconds * (notes.size - 1) + lastSeconds
        val out = buffer(total)
        for ((k, f) in notes.withIndex()) {
            val start = (k * noteSeconds * RATE).toInt()
            val len = ((if (k == notes.size - 1) lastSeconds else noteSeconds * 1.6) * RATE).toInt()
            for (j in 0 until len) {
                val i = start + j
                if (i >= out.size) break
                val t = j.toDouble() / RATE
                // Triangle-ish timbre from odd harmonics; the softer variant drops the upper ones.
                var v = sin(2 * PI * f * t) + sin(2 * PI * 3 * f * t) / 9
                if (!soft) v += sin(2 * PI * 5 * f * t) / 25
                out[i] += v * exp(-t / (if (k == notes.size - 1) 0.18 else 0.09)) * 0.4 * minOf(1.0, t / 0.004)
            }
        }
        return out
    }

    private fun beeps(): DoubleArray {
        val a = tone(1_000.0, 0.08, decay = 0.05)
        val out = buffer(0.24)
        a.copyInto(out, 0)
        a.copyInto(out, (0.14 * RATE).toInt())
        return out
    }

    /** Normalizes to −1 dBFS-ish and wraps the samples in a RIFF/WAVE container. */
    private fun wav(samples: DoubleArray): ByteArray {
        val peak = samples.maxOf { abs(it) }.coerceAtLeast(1e-9)
        val gain = 0.85 / peak
        val data = ByteBuffer.allocate(samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (s in samples) data.putShort((s * gain * Short.MAX_VALUE).toInt().coerceIn(-32768, 32767).toShort())
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + samples.size * 2); put("WAVE".toByteArray())
            put("fmt ".toByteArray()); putInt(16); putShort(1); putShort(1); putInt(RATE); putInt(RATE * 2)
            putShort(2); putShort(16)
            put("data".toByteArray()); putInt(samples.size * 2)
        }
        return ByteArrayOutputStream(44 + samples.size * 2).apply {
            write(header.array())
            write(data.array())
        }.toByteArray()
    }
}
