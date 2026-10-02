package com.kaynzhang.doudizhu.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VoiceQueueTest {
    @Test
    fun aPlayAndItsAlertKeepTheirOrderWhenTheNextPlayerActs() {
        val queue = VoiceQueue()
        val played = VoiceLine("single_3", 0)
        val alert = VoiceLine("alert_one", 0, important = true)
        val next = VoiceLine("pass_1", 1)

        queue.enqueue(listOf(played, alert), 100L) { 650L }
        queue.enqueue(listOf(next), 120L) { 550L }

        assertEquals(played, queue.poll(120L)?.line)
        assertEquals(alert, queue.poll(900L)?.line)
        assertEquals(next, queue.poll(1_600L)?.line)
    }

    @Test
    fun anOldCardAnnouncementExpiresButItsCriticalAlertRemains() {
        val queue = VoiceQueue()
        val alert = VoiceLine("alert_one", 0, important = true)
        queue.enqueue(listOf(VoiceLine("single_3", 0, ttlMs = 700L), alert), 100L) { 500L }

        assertEquals(alert, queue.poll(800L)?.line)
        assertNull(queue.poll(800L))
    }

    @Test
    fun boundedQueueDropsOrdinaryBacklogBeforeImportantLines() {
        val queue = VoiceQueue(capacity = 3)
        val alert = VoiceLine("alert_two", 0, important = true)
        val result = VoiceLine("win", 0, important = true)
        queue.enqueue(listOf(VoiceLine("single_3", 0), alert, VoiceLine("single_4", 1)), 0L) { 500L }
        queue.enqueue(listOf(result), 10L) { 500L }

        assertEquals(3, queue.size)
        assertEquals(alert, queue.poll(10L)?.line)
        assertEquals("single_4", queue.poll(10L)?.line?.key)
        assertEquals(result, queue.poll(10L)?.line)
    }

    @Test
    fun ordinaryLinesCannotDisplaceACriticalFullQueue() {
        val queue = VoiceQueue(capacity = 2)
        queue.enqueue(listOf(VoiceLine("alert_one", 1, important = true), VoiceLine("win", 0, important = true)), 0L) { 600L }
        queue.enqueue(listOf(VoiceLine("single_3", 2)), 20L) { 600L }

        assertEquals(2, queue.size)
        assertEquals("alert_one", queue.poll(20L)?.line?.key)
        assertEquals("win", queue.poll(20L)?.line?.key)
    }

    @Test
    fun stoppingClearsEverythingSoReturningCannotReplayAnOldTurn() {
        val queue = VoiceQueue()
        queue.enqueue(listOf(VoiceLine("single_3", 0), VoiceLine("alert_one", 0, important = true)), 0L) { 600L }
        queue.clear()

        assertEquals(0L, queue.durationMs(10L, gapMs = 70L))
        assertNull(queue.poll(10L))
    }

    @Test
    fun missingAssetsDoNotAddDelayOrBlockTheFollowingRecording() {
        val queue = VoiceQueue()
        queue.enqueue(listOf(VoiceLine("missing", 0), VoiceLine("win", 0, important = true)), 0L) {
            if (it.key == "missing") null else 1_200L
        }

        assertEquals(1_270L, queue.durationMs(0L, 70L))
        assertEquals("win", queue.poll(0L)?.line?.key)
        assertNull(queue.poll(0L))
    }

    @Test
    fun staleLinesInTheMiddleAreRemovedFromPacingEstimates() {
        val queue = VoiceQueue()
        queue.enqueue(listOf(VoiceLine("win", 0, important = true), VoiceLine("pass_1", 1, ttlMs = 100L)), 0L) { 900L }

        assertEquals(970L, queue.durationMs(100L, 70L))
        assertEquals(1, queue.size)
    }
}
