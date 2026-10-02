package com.kaynzhang.doudizhu.engine.ai

import com.kaynzhang.doudizhu.engine.game.GameEngine
import com.kaynzhang.doudizhu.engine.game.GameState
import com.kaynzhang.doudizhu.engine.game.Phase
import com.kaynzhang.doudizhu.engine.game.Seats
import com.kaynzhang.doudizhu.engine.model.CardSet
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HardBotTest {

    /** Mid-game states (Normal bots playing a few moves) where it is some seat's turn to play. */
    private fun midGameStates(count: Int): List<GameState> {
        val out = ArrayList<GameState>()
        var seed = 100L
        while (out.size < count) {
            val start = Benchmarks.forcedLandlordStart(seed++)
            val bots = List(Seats.COUNT) { NormalBot() }
            var s = start
            val stopAt = 3 + (seed % 9).toInt()
            var steps = 0
            runBlocking {
                while (s.phase == Phase.PLAYING && steps < stopAt) {
                    val seat = s.turn
                    s = GameEngine.apply(s, bots[seat].act(GameEngine.observe(s, seat), BotContext(Bots.decisionSeed(s, seat)))).state
                    steps++
                }
            }
            if (s.phase == Phase.PLAYING) out.add(s)
        }
        return out
    }

    @Test
    fun `decisions are reproducible for the same observation and seed`() = runBlocking {
        for (s in midGameStates(12)) {
            val seat = s.turn
            val obs = GameEngine.observe(s, seat)
            val ctx = BotContext(seed = 77, budget = SearchBudget.Samples(16))
            val bot = HardBot()
            val a = bot.act(obs, ctx)
            val b = bot.act(obs, ctx)
            val c = HardBot().act(obs, ctx)
            assertEquals(a, b)
            assertEquals(a, c)
        }
    }

    @Test
    fun `decisions depend on the observation only`() = runBlocking {
        var compared = 0
        for (s in midGameStates(12)) {
            val seat = s.turn
            val swapped = swapHidden(s, seat) ?: continue
            val obs = GameEngine.observe(s, seat)
            assertEquals(obs, GameEngine.observe(swapped, seat))
            val ctx = BotContext(seed = 5, budget = SearchBudget.Samples(16))
            assertEquals(HardBot().act(obs, ctx), HardBot().act(GameEngine.observe(swapped, seat), ctx))
            compared++
        }
        assertTrue(compared >= 8)
    }

    /** Swaps a few hidden cards between the two other seats, leaving the landlord's bottom cards alone. */
    private fun swapHidden(s: GameState, seat: Int): GameState? {
        val a = Seats.next(seat)
        val b = Seats.prev(seat)
        val fixed = s.bottom
        val fromA = (s.hands[a] - fixed).cards().take(3)
        val fromB = (s.hands[b] - fixed).cards().take(3)
        val n = minOf(fromA.size, fromB.size)
        if (n == 0) return null
        val moveA = CardSet.of(fromA.take(n))
        val moveB = CardSet.of(fromB.take(n))
        val hands = s.hands.toMutableList()
        hands[a] = s.hands[a] - moveA + moveB
        hands[b] = s.hands[b] - moveB + moveA
        return s.copy(hands = hands)
    }

    @Test
    fun `hard plays legal complete games against normal`() {
        for (seed in 0 until 40L) {
            val start = Benchmarks.forcedLandlordStart(seed)
            val bots = List(Seats.COUNT) { if (it == (seed % 3).toInt()) HardBot() else NormalBot() }
            val end = Simulator.play(bots, seed, start = start, budget = SearchBudget.Samples(12))
            assertTrue(end.result != null)
        }
    }
}
