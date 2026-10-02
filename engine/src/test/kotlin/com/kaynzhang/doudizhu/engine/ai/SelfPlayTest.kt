package com.kaynzhang.doudizhu.engine.ai

import com.kaynzhang.doudizhu.engine.game.GameEvent
import com.kaynzhang.doudizhu.engine.game.GameState
import com.kaynzhang.doudizhu.engine.game.Phase
import com.kaynzhang.doudizhu.engine.game.Seats
import com.kaynzhang.doudizhu.engine.game.Transition
import com.kaynzhang.doudizhu.engine.model.CardSet
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Whole games between bots, checking the rules' invariants after every single action. */
class SelfPlayTest {

    private val json = Json

    @Test
    fun `normal bots play legal, conserving games`() = run("normal", games = 2_000) { NormalBot() }

    @Test
    fun `easy bots play legal, conserving games`() = run("easy", games = 2_000) { EasyBot() }

    @Test
    fun `mixed tables play legal games`() {
        val stats = Stats()
        for (seed in 0 until 600L) {
            val bots = listOf(EasyBot(), NormalBot(), HardBot()).shuffled(java.util.Random(seed))
            playChecked(bots, seed, stats)
        }
    }

    private fun run(label: String, games: Int, bot: () -> Bot) {
        val stats = Stats()
        val bots = List(3) { bot() }
        for (seed in 0 until games.toLong()) playChecked(bots, seed, stats)
        println("$label: $stats")
    }

    private fun playChecked(bots: List<Bot>, seed: Long, stats: Stats) {
        var redeals = 0
        val end = Simulator.play(bots, seed) { before, t ->
            checkStep(before, t)
            redeals += t.events.count { it is GameEvent.Dealt && it.isRedeal }
            assertTrue(redeals < 50, "too many redeals in game $seed")
            if (seed % 50 == 0L) {
                val decoded = json.decodeFromString(GameState.serializer(), json.encodeToString(GameState.serializer(), t.state))
                assertEquals(t.state, decoded)
            }
        }
        val r = end.result!!
        assertTrue(end.hands[r.winner].isEmpty)
        assertEquals(0L, r.delta.sum(), "settlement not zero-sum in game $seed")
        for (i in 0 until Seats.COUNT) assertTrue(end.config.seatCoins[i] + r.delta[i] >= 0)
        // Spring flags agree with the log.
        val farmersPlayed = end.log.any { !it.isPass && it.seat != end.landlord }
        val landlordPlays = end.log.count { !it.isPass && it.seat == end.landlord }
        assertEquals(r.landlordWon && !farmersPlayed, r.spring)
        assertEquals(!r.landlordWon && landlordPlays == 1, r.antiSpring)
        assertEquals(end.log.count { it.combo?.isBomb == true }, r.bombs)
        stats.add(end, redeals)
    }

    private fun checkStep(before: GameState, t: Transition) {
        val s = t.state
        // Card conservation: hands + played (+ bottom until it is taken) = the whole deck, no duplicates.
        var bits = 0L
        var count = 0
        for (h in s.hands) {
            assertEquals(0L, bits and h.bits, "a card is in two places")
            bits = bits or h.bits
            count += h.size
        }
        for (rec in s.log) {
            assertEquals(0L, bits and rec.cards.bits, "a played card is still held")
            bits = bits or rec.cards.bits
            count += rec.cards.size
        }
        if (s.landlord < 0) {
            bits = bits or s.bottom.bits
            count += s.bottom.size
        }
        assertEquals(CardSet.FULL_DECK.bits, bits, "cards went missing after ${t.events}")
        assertEquals(54, count)
        if (s.phase == Phase.PLAYING && s.trick == null) assertTrue(s.trickOwner == -1)
        assertTrue(s.seq > before.seq)
    }

    private class Stats {
        var games = 0
        var landlordWins = 0
        var springs = 0
        var bombs = 0
        var redeals = 0
        var robs = 0
        var plays = 0

        fun add(s: GameState, redeals: Int) {
            val r = s.result!!
            games++
            if (r.landlordWon) landlordWins++
            if (r.spring || r.antiSpring) springs++
            bombs += r.bombs
            robs += r.robs
            this.redeals += redeals
            plays += s.log.size
        }

        override fun toString() = "games=$games landlordWin=%.3f spring=%.3f bombs/game=%.2f robs/game=%.2f redeals/game=%.2f actions/game=%.1f"
            .format(landlordWins.toDouble() / games, springs.toDouble() / games, bombs.toDouble() / games,
                robs.toDouble() / games, redeals.toDouble() / games, plays.toDouble() / games)
    }
}
