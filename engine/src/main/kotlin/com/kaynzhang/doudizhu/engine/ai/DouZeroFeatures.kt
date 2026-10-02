package com.kaynzhang.doudizhu.engine.ai

import com.kaynzhang.doudizhu.engine.game.Observation
import com.kaynzhang.doudizhu.engine.game.Phase
import com.kaynzhang.doudizhu.engine.game.Seats
import com.kaynzhang.doudizhu.engine.model.Counts
import com.kaynzhang.doudizhu.engine.model.Rk

/** The three independent networks shipped by the original DouZero WP model. */
enum class DouZeroPosition(val resourceName: String, val xSize: Int) {
    LANDLORD("landlord", 373),
    LANDLORD_UP("landlord_up", 484),
    LANDLORD_DOWN("landlord_down", 484);

    val noActionSize: Int get() = xSize - DouZeroFeatures.CARD_SIZE
}

/** [x] excludes the candidate action; [z] is the row-major 5 × 162 history tensor. */
data class DouZeroInput(val position: DouZeroPosition, val x: FloatArray, val z: FloatArray)

/** Scores each candidate from the acting player's perspective, in the supplied order. */
fun interface DouZeroEvaluator {
    suspend fun scores(features: DouZeroInput, actions: List<Counts>): FloatArray
}

/**
 * The feature layout of kwai/DouZero's env/env.py, derived only from public [Observation].
 * Cards use four thermometer bits per ordinary rank, followed by the two joker bits.
 */
object DouZeroFeatures {
    const val CARD_SIZE = 54
    const val HISTORY_ACTIONS = 15
    const val HISTORY_ROWS = 5
    const val HISTORY_WIDTH = 162

    fun encode(obs: Observation): DouZeroInput {
        require(obs.phase == Phase.PLAYING)
        require(obs.seat in 0 until Seats.COUNT && obs.landlord in 0 until Seats.COUNT)
        val up = Seats.prev(obs.landlord)
        val down = Seats.next(obs.landlord)
        val position = when (obs.seat) {
            obs.landlord -> DouZeroPosition.LANDLORD
            up -> DouZeroPosition.LANDLORD_UP
            else -> DouZeroPosition.LANDLORD_DOWN
        }
        val x = FloatArray(position.noActionSize)
        var offset = 0
        fun appendCards(counts: Counts) {
            writeCards(counts, x, offset)
            offset += CARD_SIZE
        }
        fun appendSize(seat: Int, maximum: Int) {
            val size = obs.handSizes[seat]
            require(size in 1..maximum) { "invalid remaining hand size: $size" }
            x[offset + size - 1] = 1f
            offset += maximum
        }

        // DouZero takes the last action, or the preceding action if the last was a pass.
        // After two passes this is empty, even though an earlier play exists in the log.
        val latest = obs.log.lastOrNull()
        val lastMove = if (latest?.isPass == true) obs.log.getOrNull(obs.log.lastIndex - 1) else latest
        fun lastOf(seat: Int): Counts = obs.log.lastOrNull { it.seat == seat }?.cards?.counts() ?: Counts.EMPTY

        appendCards(obs.hand.counts())
        appendCards(obs.unseen.counts())
        if (position == DouZeroPosition.LANDLORD) {
            appendCards(lastMove?.cards?.counts() ?: Counts.EMPTY)
            appendCards(obs.playedBy[up].counts())
            appendCards(obs.playedBy[down].counts())
            appendSize(up, 17)
            appendSize(down, 17)
        } else {
            val teammate = if (obs.seat == up) down else up
            appendCards(obs.playedBy[obs.landlord].counts())
            appendCards(obs.playedBy[teammate].counts())
            appendCards(lastMove?.cards?.counts() ?: Counts.EMPTY)
            appendCards(lastOf(obs.landlord))
            appendCards(lastOf(teammate))
            appendSize(obs.landlord, 20)
            appendSize(teammate, 17)
        }
        require(obs.bombs in 0..14)
        x[offset + obs.bombs] = 1f

        val z = FloatArray(HISTORY_ROWS * HISTORY_WIDTH)
        val history = obs.log.takeLast(HISTORY_ACTIONS)
        val padding = HISTORY_ACTIONS - history.size
        history.forEachIndexed { index, record ->
            writeCards(record.cards.counts(), z, (padding + index) * CARD_SIZE)
        }
        return DouZeroInput(position, x, z)
    }

    fun cards(counts: Counts): FloatArray = FloatArray(CARD_SIZE).also { writeCards(counts, it) }

    /** Writes a complete 54-bit card feature, clearing any existing data in its destination. */
    fun writeCards(counts: Counts, destination: FloatArray, offset: Int = 0) {
        require(offset >= 0 && offset + CARD_SIZE <= destination.size)
        destination.fill(0f, offset, offset + CARD_SIZE)
        for (rank in 0..Rk.TWO) {
            val count = counts[rank]
            require(count in 0..4)
            repeat(count) { destination[offset + rank * 4 + it] = 1f }
        }
        require(counts[Rk.SJ] in 0..1 && counts[Rk.BJ] in 0..1)
        destination[offset + 52] = counts[Rk.SJ].toFloat()
        destination[offset + 53] = counts[Rk.BJ].toFloat()
    }
}
