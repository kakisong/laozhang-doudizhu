package com.kaynzhang.doudizhu.audio

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sin

/** Playback defaults complement the independent loudness of the synthesized samples. */
enum class Sfx(
    val volume: Float = 0.85f,
    val priority: Int = 2,
    val minIntervalMs: Long = 160L,
) {
    DEAL(volume = 0.8f, minIntervalMs = 600L),
    PLAY(minIntervalMs = 140L),
    CLICK(volume = 0.7f, priority = 1, minIntervalMs = 70L),
    BOMB(volume = 0.9f, priority = 4, minIntervalMs = 450L),
    ROCKET(volume = 0.9f, priority = 4, minIntervalMs = 650L),
    PLANE(volume = 0.85f, priority = 3, minIntervalMs = 500L),
    WIN(volume = 0.9f, priority = 5, minIntervalMs = 1_200L),
    LOSE(volume = 0.8f, priority = 5, minIntervalMs = 1_200L),
    ALERT(volume = 0.85f, priority = 3, minIntervalMs = 1_200L),
    TURN(volume = 0.85f, priority = 3, minIntervalMs = 350L),
    SELECT(volume = 0.75f, priority = 1, minIntervalMs = 55L),
    DESELECT(volume = 0.65f, priority = 1, minIntervalMs = 55L),
    HINT(volume = 0.8f, minIntervalMs = 250L),
    ERROR(volume = 0.8f, priority = 3, minIntervalMs = 400L),
    BID(minIntervalMs = 300L),
    LANDLORD(priority = 3, minIntervalMs = 650L),
    DOUBLE(minIntervalMs = 350L),
    PASS(volume = 0.75f, minIntervalMs = 200L),
    STRAIGHT(priority = 3, minIntervalMs = 350L),
    PAIR_STRAIGHT(priority = 3, minIntervalMs = 400L),
    SPRING(volume = 0.9f, priority = 4, minIntervalMs = 1_000L),
    RELIEF(volume = 0.65f, minIntervalMs = 400L),
}

/**
 * Deterministic 16-bit mono PCM with warm, short game cues and no external audio assets.
 * Frequent interface sounds are intentionally quieter than card and celebration sounds.
 */
object SoundSynth {
    const val RATE = 44_100
    const val VERSION = 2

    fun render(sfx: Sfx): ByteArray = wav(
        when (sfx) {
            Sfx.DEAL -> deal()
            Sfx.PLAY -> play()
            Sfx.CLICK -> singleTone(680.0, 0.075, 0.14, 0.025)
            Sfx.SELECT -> singleTone(480.0, 0.095, 0.17, 0.04, 650.0)
            Sfx.DESELECT -> singleTone(560.0, 0.09, 0.13, 0.035, 390.0)
            Sfx.HINT -> melody(doubleArrayOf(440.0, 659.25), 0.085, 0.16, 0.23)
            Sfx.ERROR -> melody(doubleArrayOf(349.23, 293.66), 0.11, 0.17, 0.23)
            Sfx.BID -> melody(doubleArrayOf(392.0, 523.25), 0.105, 0.2, 0.25)
            Sfx.LANDLORD -> chord(doubleArrayOf(261.63, 329.63, 392.0), 0.48, 0.17)
            Sfx.DOUBLE -> melody(doubleArrayOf(523.25, 783.99), 0.13, 0.24, 0.25)
            Sfx.PASS -> singleTone(370.0, 0.13, 0.18, 0.055, 260.0)
            Sfx.STRAIGHT -> melody(doubleArrayOf(392.0, 440.0, 523.25, 659.25), 0.065, 0.19, 0.22)
            Sfx.PAIR_STRAIGHT -> pairStraight()
            Sfx.BOMB -> bomb()
            Sfx.ROCKET -> rocket()
            Sfx.PLANE -> plane()
            Sfx.WIN -> win()
            Sfx.LOSE -> melody(doubleArrayOf(392.0, 349.23, 293.66), 0.145, 0.36, 0.21)
            Sfx.SPRING -> spring()
            Sfx.RELIEF -> melody(doubleArrayOf(523.25, 392.0), 0.095, 0.2, 0.16)
            Sfx.ALERT -> melody(doubleArrayOf(659.25, 659.25), 0.15, 0.15, 0.28)
            Sfx.TURN -> singleTone(783.99, 0.19, 0.25, 0.075)
        },
    )

    private fun buffer(seconds: Double) = DoubleArray((seconds * RATE).roundToInt())

    private class Noise(seed: Long) {
        private val random = java.util.Random(seed)
        fun next(): Double = random.nextDouble() * 2.0 - 1.0
    }

    /** Six soft card flicks make one complete deal cue instead of one isolated click. */
    private fun deal(): DoubleArray {
        val out = buffer(0.5)
        val noise = Noise(1)
        repeat(6) { card ->
            val start = (card * 0.065 * RATE).roundToInt()
            val length = (0.07 * RATE).roundToInt()
            var low = 0.0
            var body = 0.0
            for (j in 0 until length) {
                val t = j.toDouble() / RATE
                low += (noise.next() - low) * 0.22
                body += (low - body) * 0.05
                out[start + j] += (low - body) * exp(-t / 0.017) * edge(t, 0.07) * 0.42
            }
            addTone(out, card * 0.065, 0.08, 230.0 + card * 12.0, 0.09, 0.025)
        }
        return out
    }

    private fun play(): DoubleArray {
        val out = buffer(0.18)
        val noise = Noise(2)
        var low = 0.0
        for (i in out.indices) {
            val t = i.toDouble() / RATE
            low += (noise.next() - low) * 0.13
            out[i] = low * exp(-t / 0.026) * edge(t, 0.18) * 0.45
        }
        addTone(out, 0.0, 0.16, 190.0, 0.29, 0.048, 145.0)
        addTone(out, 0.007, 0.1, 520.0, 0.08, 0.024)
        return out
    }

    private fun singleTone(
        frequency: Double,
        seconds: Double,
        gain: Double,
        decay: Double,
        endFrequency: Double = frequency,
    ) = buffer(seconds).also { addTone(it, 0.0, seconds, frequency, gain, decay, endFrequency) }

    /** Integrated frequency sweep avoids the pitch discontinuities of frequency * time. */
    private fun addTone(
        out: DoubleArray,
        startSeconds: Double,
        seconds: Double,
        frequency: Double,
        gain: Double,
        decay: Double,
        endFrequency: Double = frequency,
    ) {
        val start = (startSeconds * RATE).roundToInt()
        val length = (seconds * RATE).roundToInt()
        for (j in 0 until length) {
            val i = start + j
            if (i !in out.indices) break
            val t = j.toDouble() / RATE
            val phase = 2.0 * PI * (frequency * t + (endFrequency - frequency) * t * t / (2.0 * seconds))
            // A little low harmonic warmth without a brittle upper-frequency click.
            val tone = (sin(phase) + 0.16 * sin(phase * 2.0) + 0.05 * sin(phase * 3.0)) / 1.21
            out[i] += tone * gain * exp(-t / decay) * edge(t, seconds)
        }
    }

    private fun melody(notes: DoubleArray, step: Double, tail: Double, gain: Double): DoubleArray {
        val out = buffer(step * (notes.size - 1) + tail)
        notes.forEachIndexed { index, frequency ->
            val length = if (index == notes.lastIndex) tail else step * 1.75
            addTone(out, index * step, length, frequency, gain, length * 0.42)
        }
        return out
    }

    private fun chord(notes: DoubleArray, seconds: Double, gain: Double): DoubleArray =
        buffer(seconds).also { out ->
            notes.forEachIndexed { index, frequency ->
                val start = index * 0.025
                addTone(out, start, seconds - start, frequency, gain, 0.17)
            }
        }

    private fun pairStraight(): DoubleArray {
        val out = buffer(0.47)
        doubleArrayOf(392.0, 493.88, 587.33).forEachIndexed { pair, frequency ->
            repeat(2) { note ->
                addTone(out, pair * 0.11 + note * 0.045, 0.18, frequency, 0.22, 0.065)
            }
        }
        return out
    }

    private fun bomb(): DoubleArray = buffer(0.72).also { impact(it, 0.0, 0.7, 1.0, 3) }

    /** Rounded bass impact with filtered air, retaining impact without a sharp noise blast. */
    private fun impact(out: DoubleArray, startSeconds: Double, seconds: Double, gain: Double, seed: Long) {
        addTone(out, startSeconds, seconds, 98.0, 0.5 * gain, 0.18, 48.0)
        addTone(out, startSeconds, 0.14, 310.0, 0.18 * gain, 0.038, 130.0)
        val start = (startSeconds * RATE).roundToInt()
        val length = (seconds * RATE).roundToInt()
        val noise = Noise(seed)
        var low = 0.0
        for (j in 0 until length) {
            val i = start + j
            if (i !in out.indices) break
            val t = j.toDouble() / RATE
            low += (noise.next() - low) * 0.055
            out[i] += low * exp(-t / 0.12) * edge(t, seconds, attack = 0.008) * 1.2 * gain
        }
    }

    /** King bomb: rising launch, then two distinct impacts for the two jokers. */
    private fun rocket(): DoubleArray {
        val out = buffer(0.96)
        addTone(out, 0.0, 0.32, 240.0, 0.18, 0.6, 960.0)
        impact(out, 0.30, 0.54, 0.85, 4)
        impact(out, 0.46, 0.48, 0.7, 6)
        addTone(out, 0.48, 0.4, 783.99, 0.13, 0.14)
        return out
    }

    /** A moving, pulsing propeller timbre distinguishes airplane from either bomb. */
    private fun plane(): DoubleArray {
        val seconds = 0.86
        val out = buffer(seconds)
        val noise = Noise(5)
        var low = 0.0
        for (i in out.indices) {
            val t = i.toDouble() / RATE
            val travel = sin(PI * t / seconds)
            val rotor = 0.7 + 0.3 * cos(2.0 * PI * 23.0 * t)
            low += (noise.next() - low) * (0.06 + 0.09 * travel)
            val phase = 2.0 * PI * (125.0 * t + 90.0 * t * t / (2.0 * seconds))
            out[i] = (0.27 * sin(phase) + 0.65 * low) * rotor * travel * travel * edge(t, seconds)
        }
        return out
    }

    private fun win(): DoubleArray {
        val out = melody(doubleArrayOf(523.25, 659.25, 783.99, 1_046.5), 0.125, 0.48, 0.29)
        addTone(out, 0.375, 0.46, 523.25, 0.12, 0.2)
        addTone(out, 0.4, 0.43, 659.25, 0.1, 0.18)
        return out
    }

    private fun spring(): DoubleArray {
        val out = melody(doubleArrayOf(392.0, 523.25, 659.25, 783.99, 1_046.5), 0.075, 0.46, 0.27)
        addTone(out, 0.3, 0.44, 523.25, 0.12, 0.19)
        return out
    }

    /** Raised-cosine edges on every component prevent clicks when tones overlap or end. */
    private fun edge(t: Double, seconds: Double, attack: Double = 0.005, release: Double = 0.022): Double {
        val inProgress = (t / attack).coerceIn(0.0, 1.0)
        val outProgress = ((seconds - t) / release).coerceIn(0.0, 1.0)
        return (0.5 - 0.5 * cos(PI * inProgress)) * (0.5 - 0.5 * cos(PI * outProgress))
    }

    /**
     * Preserve each cue's authored loudness; only attenuate if a mix exceeds safe headroom.
     * Final edges protect even future cues that do not use the component envelope.
     */
    private fun wav(samples: DoubleArray): ByteArray {
        val fadeIn = (0.004 * RATE).roundToInt()
        val fadeOut = (0.018 * RATE).roundToInt()
        for (i in samples.indices) {
            val attack = (i.toDouble() / fadeIn).coerceAtMost(1.0)
            val release = ((samples.lastIndex - i).toDouble() / fadeOut).coerceAtMost(1.0)
            samples[i] *= (0.5 - 0.5 * cos(PI * attack)) * (0.5 - 0.5 * cos(PI * release))
        }
        val peak = samples.maxOf { abs(it) }
        val gain = if (peak > 0.82) 0.82 / peak else 1.0
        val data = ByteBuffer.allocate(samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (sample in samples) {
            data.putShort((sample * gain * Short.MAX_VALUE).roundToInt().coerceIn(-32767, 32767).toShort())
        }
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray(Charsets.US_ASCII)); putInt(36 + samples.size * 2); put("WAVE".toByteArray(Charsets.US_ASCII))
            put("fmt ".toByteArray(Charsets.US_ASCII)); putInt(16); putShort(1); putShort(1); putInt(RATE); putInt(RATE * 2)
            putShort(2); putShort(16)
            put("data".toByteArray(Charsets.US_ASCII)); putInt(samples.size * 2)
        }
        return ByteArrayOutputStream(44 + samples.size * 2).apply {
            write(header.array())
            write(data.array())
        }.toByteArray()
    }
}
