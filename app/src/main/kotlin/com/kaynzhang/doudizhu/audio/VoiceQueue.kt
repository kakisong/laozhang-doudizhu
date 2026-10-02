package com.kaynzhang.doudizhu.audio

/** A prerecorded line for one player. Important alerts and results survive ordinary queue expiry. */
data class VoiceLine(
    val key: String,
    val seat: Int,
    val important: Boolean = false,
    val ttlMs: Long = 4_000L,
)

internal data class PendingVoice(
    val line: VoiceLine,
    val enqueuedAtMs: Long,
    val durationMs: Long,
) {
    fun expired(nowMs: Long): Boolean = !line.important && nowMs - enqueuedAtMs >= line.ttlMs
}

/** Main-thread-owned, bounded FIFO. A transition is added together, in engine event order. */
internal class VoiceQueue(private val capacity: Int = 16) {
    init { require(capacity > 0) }

    private val pending = ArrayDeque<PendingVoice>()
    val size: Int get() = pending.size

    fun enqueue(lines: List<VoiceLine>, nowMs: Long, durationFor: (VoiceLine) -> Long?) {
        expire(nowMs)
        for (line in lines) {
            if (line.key.isBlank() || line.seat !in 0..2 || (!line.important && line.ttlMs <= 0L)) continue
            val duration = durationFor(line)?.coerceAtLeast(1L) ?: continue
            if (pending.size == capacity) {
                val ordinary = pending.indexOfFirst { !it.line.important }
                if (ordinary >= 0) pending.removeAt(ordinary)
                else if (line.important) pending.removeFirst()
                else continue
            }
            pending.addLast(PendingVoice(line, nowMs, duration))
        }
    }

    fun poll(nowMs: Long): PendingVoice? {
        expire(nowMs)
        return pending.removeFirstOrNull()
    }

    fun durationMs(nowMs: Long, gapMs: Long): Long {
        expire(nowMs)
        return pending.sumOf { it.durationMs + gapMs }
    }

    fun snapshot(nowMs: Long): List<PendingVoice> {
        expire(nowMs)
        return pending.toList()
    }

    fun clear() = pending.clear()

    private fun expire(nowMs: Long) {
        pending.removeAll { it.expired(nowMs) }
    }
}
