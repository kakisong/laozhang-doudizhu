package com.kaynzhang.doudizhu.audio

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.sqrt
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** JVM signal checks: audible identities and smooth PCM boundaries, without Android APIs. */
class SoundSynthTest {

    @Test
    fun everyCueHasAValidMonoPcmWaveHeader() {
        for (cue in Sfx.entries) {
            val bytes = SoundSynth.render(cue)
            val header = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            fun text(offset: Int) = String(bytes, offset, 4, Charsets.US_ASCII)

            assertEquals(cue.name, "RIFF", text(0))
            assertEquals(cue.name, bytes.size - 8, header.getInt(4))
            assertEquals(cue.name, "WAVE", text(8))
            assertEquals(cue.name, "fmt ", text(12))
            assertEquals(cue.name, 16, header.getInt(16))
            assertEquals(cue.name, 1, header.getShort(20).toInt())
            assertEquals(cue.name, 1, header.getShort(22).toInt())
            assertEquals(cue.name, SoundSynth.RATE, header.getInt(24))
            assertEquals(cue.name, SoundSynth.RATE * 2, header.getInt(28))
            assertEquals(cue.name, 2, header.getShort(32).toInt())
            assertEquals(cue.name, 16, header.getShort(34).toInt())
            assertEquals(cue.name, "data", text(36))
            assertEquals(cue.name, bytes.size - 44, header.getInt(40))
            assertEquals(cue.name, 0, (bytes.size - 44) % 2)
        }
    }

    @Test
    fun renderingIsDeterministicAndEveryCueHasSound() {
        for (cue in Sfx.entries) {
            val first = SoundSynth.render(cue)
            assertArrayEquals(cue.name, first, SoundSynth.render(cue))
            assertTrue("$cue must contain audible energy", rms(pcm(first)) > 0.012)
        }
    }

    @Test
    fun cuesStayShortEnoughForResponsivePlay() {
        for (cue in Sfx.entries) {
            val duration = pcm(SoundSynth.render(cue)).size.toDouble() / SoundSynth.RATE
            val bounds = when (cue) {
                Sfx.CLICK, Sfx.SELECT, Sfx.DESELECT, Sfx.PASS -> 0.05..0.15
                Sfx.PLAY, Sfx.TURN -> 0.1..0.25
                Sfx.HINT, Sfx.ERROR, Sfx.BID, Sfx.DOUBLE, Sfx.ALERT, Sfx.RELIEF -> 0.2..0.4
                Sfx.DEAL, Sfx.LANDLORD, Sfx.STRAIGHT, Sfx.PAIR_STRAIGHT -> 0.3..0.6
                Sfx.BOMB, Sfx.ROCKET, Sfx.PLANE, Sfx.WIN, Sfx.LOSE, Sfx.SPRING -> 0.55..1.0
            }
            assertTrue("$cue duration $duration outside $bounds", duration in bounds)
        }
    }

    @Test
    fun pcmHasHeadroomAndNoHardBeginningOrEnding() {
        for (cue in Sfx.entries) {
            val samples = pcm(SoundSynth.render(cue))
            val peak = peak(samples)
            assertTrue("$cue peak $peak", peak in 0.03..0.8201)
            assertEquals("$cue start", 0.0, samples.first(), 0.0)
            assertEquals("$cue end", 0.0, samples.last(), 0.0)
            assertTrue("$cue attack edge", peak(samples.copyOfRange(0, 8)) <= 2.0 / Short.MAX_VALUE)
            assertTrue("$cue release edge", peak(samples.copyOfRange(samples.size - 8, samples.size)) <= 2.0 / Short.MAX_VALUE)
            assertTrue("$cue attack fades in", rms(window(samples, 0.0, 0.001)) < peak * 0.02)
            assertTrue("$cue tail fades out", rms(window(samples, samples.size.toDouble() / SoundSynth.RATE - 0.002, 0.002)) < peak * 0.02)
            assertTrue("$cue DC offset", abs(samples.average()) < 0.015)
            val largestStep = samples.indices.drop(1).maxOf { abs(samples[it] - samples[it - 1]) }
            assertTrue("$cue has an abrupt PCM discontinuity: $largestStep", largestStep < 0.15)
        }
    }

    @Test
    fun interfaceCuesKeepIndependentQuietLoudness() {
        val click = pcm(SoundSynth.render(Sfx.CLICK))
        val play = pcm(SoundSynth.render(Sfx.PLAY))
        val bomb = pcm(SoundSynth.render(Sfx.BOMB))
        val turn = pcm(SoundSynth.render(Sfx.TURN))

        assertTrue("click must be quieter than putting cards down", peak(click) < peak(play) * 0.7)
        assertTrue("click must not be normalized to bomb volume", peak(click) < peak(bomb) * 0.35)
        assertTrue("turn should remain clearer than a tap", rms(turn) > rms(click) * 1.35)
        assertTrue("bomb must retain more energy than a tap", rms(bomb) > rms(click) * 1.5)
        val levels = Sfx.entries.map { (peak(pcm(SoundSynth.render(it))) * 100).toInt() }.toSet()
        assertTrue("samples should have independently authored gains", levels.size >= 8)
    }

    @Test
    fun dealingContainsASequenceOfSoftFlicks() {
        val samples = pcm(SoundSynth.render(Sfx.DEAL))
        for (card in 0 until 6) {
            val start = card * 0.065
            val flick = rms(window(samples, start + 0.005, 0.022))
            val rest = rms(window(samples, start + 0.047, 0.012))
            assertTrue("deal flick $card is audible", flick > 0.018)
            assertTrue("deal flick $card should decay before the next card", flick > rest * 2.0)
        }
        assertTrue("deal ends cleanly after the sequence", peak(window(samples, 0.43, 0.07)) == 0.0)
    }

    @Test
    fun bombAirplaneAndKingBombHaveDistinctTemporalSignatures() {
        val bomb = pcm(SoundSynth.render(Sfx.BOMB))
        val airplane = pcm(SoundSynth.render(Sfx.PLANE))
        val rocket = pcm(SoundSynth.render(Sfx.ROCKET))

        assertTrue("bomb is an early impact", rms(window(bomb, 0.015, 0.09)) > rms(window(bomb, 0.4, 0.09)) * 3.0)
        assertTrue("airplane approaches gradually", rms(window(airplane, 0.0, 0.08)) < rms(window(airplane, 0.35, 0.12)) * 0.15)
        assertTrue("airplane departs gradually", rms(window(airplane, 0.78, 0.08)) < rms(window(airplane, 0.35, 0.12)) * 0.15)
        assertTrue("king bomb has an audible launch", rms(window(rocket, 0.05, 0.12)) > 0.03)
        assertTrue("king bomb impact follows the launch", rms(window(rocket, 0.315, 0.08)) > rms(window(rocket, 0.05, 0.12)) * 1.8)
        assertTrue("second joker impact adds fresh energy", rms(window(rocket, 0.47, 0.055)) > rms(window(rocket, 0.425, 0.025)) * 1.15)
    }

    @Test
    fun playbackDefaultsPrioritizeEventsOverFrequentTaps() {
        for (cue in Sfx.entries) {
            assertTrue("$cue volume", cue.volume > 0f && cue.volume <= 1f)
            assertTrue("$cue priority", cue.priority in 1..5)
            assertTrue("$cue throttle", cue.minIntervalMs in 40L..2_000L)
        }
        assertTrue(Sfx.WIN.priority > Sfx.CLICK.priority)
        assertTrue(Sfx.BOMB.priority > Sfx.SELECT.priority)
        assertTrue(Sfx.ALERT.minIntervalMs >= 1_000L)
        assertTrue(Sfx.DEAL.minIntervalMs >= 500L)
        assertTrue(SoundSynth.VERSION >= 2)
    }

    private fun pcm(bytes: ByteArray): DoubleArray {
        val buffer = ByteBuffer.wrap(bytes, 44, bytes.size - 44).order(ByteOrder.LITTLE_ENDIAN)
        return DoubleArray((bytes.size - 44) / 2) { buffer.short.toDouble() / Short.MAX_VALUE }
    }

    private fun peak(samples: DoubleArray) = samples.maxOf { abs(it) }

    private fun rms(samples: DoubleArray) = sqrt(samples.sumOf { it * it } / samples.size)

    private fun window(samples: DoubleArray, start: Double, seconds: Double): DoubleArray {
        val first = (start * SoundSynth.RATE).toInt().coerceIn(0, samples.size - 1)
        val last = ((start + seconds) * SoundSynth.RATE).toInt().coerceIn(first + 1, samples.size)
        return samples.copyOfRange(first, last)
    }
}
