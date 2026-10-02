package com.kaynzhang.doudizhu.engine.ai

import com.kaynzhang.doudizhu.engine.game.Action
import com.kaynzhang.doudizhu.engine.game.GameEngine
import com.kaynzhang.doudizhu.engine.game.GameState
import com.kaynzhang.doudizhu.engine.game.Observation
import com.kaynzhang.doudizhu.engine.game.Phase
import com.kaynzhang.doudizhu.engine.game.Seats
import com.kaynzhang.doudizhu.engine.model.CardSet
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Uses the bundled native models; a missing or broken model must fail these checks. */
class DouZeroModelGameTest {
    private fun bot(): SuperBot = SuperBot(fallback = object : Bot {
        override suspend fun act(obs: Observation, ctx: BotContext): Action =
            error("bundled DouZero model failed during native integration test")
    })

    private fun bots(): List<Bot> = List(Seats.COUNT) { bot() }

    @Test
    fun `bundled models play legal complete games for all roles`() {
        for (seed in 10L..15L) {
            val finished = Simulator.play(bots(), seed, start = Benchmarks.forcedLandlordStart(seed))
            assertEquals(Phase.FINISHED, finished.phase)
            assertTrue(finished.result != null)
        }
    }

    @Test
    fun `native decisions and whole game outcomes survive serialized saves`() = runBlocking {
        for (seed in 10L..11L) {
            val originalBots = bots()
            var state = Benchmarks.forcedLandlordStart(seed)
            repeat(15) {
                if (state.phase == Phase.PLAYING) {
                    val seat = state.turn
                    val ctx = BotContext(Bots.decisionSeed(state, seat))
                    state = GameEngine.apply(state, originalBots[seat].act(GameEngine.observe(state, seat), ctx)).state
                }
            }
            assertEquals(Phase.PLAYING, state.phase)
            val restored = Json.decodeFromString(GameState.serializer(), Json.encodeToString(GameState.serializer(), state))
            val seat = state.turn
            val ctx = BotContext(Bots.decisionSeed(state, seat))
            assertEquals(originalBots[seat].act(GameEngine.observe(state, seat), ctx), bot().act(GameEngine.observe(restored, seat), ctx))
            val uninterruptedEnd = Simulator.play(originalBots, seed, start = state)
            val resumedEnd = Simulator.play(bots(), seed, start = restored)
            assertEquals(uninterruptedEnd, resumedEnd)
        }
    }

    @Test
    fun `native model decisions cannot see different hidden card allocations`() = runBlocking {
        for (seed in 21L..23L) {
            var state = Benchmarks.forcedLandlordStart(seed)
            val originalBots = bots()
            repeat(7) {
                val seat = state.turn
                state = GameEngine.apply(state, originalBots[seat].act(
                    GameEngine.observe(state, seat), BotContext(Bots.decisionSeed(state, seat)),
                )).state
            }
            assertEquals(Phase.PLAYING, state.phase)
            val seat = state.turn
            val a = Seats.next(seat)
            val b = Seats.prev(seat)
            val cardsA = CardSet.of((state.hands[a] - state.bottom).cards().take(2))
            val cardsB = CardSet.of((state.hands[b] - state.bottom).cards().take(2))
            assertEquals(2, cardsA.size)
            assertEquals(2, cardsB.size)
            val hands = state.hands.toMutableList()
            hands[a] = state.hands[a] - cardsA + cardsB
            hands[b] = state.hands[b] - cardsB + cardsA
            val swapped = state.copy(hands = hands)
            val obs = GameEngine.observe(state, seat)
            val other = GameEngine.observe(swapped, seat)
            assertEquals(obs, other)
            val ctx = BotContext(Bots.decisionSeed(state, seat))
            assertEquals(bot().act(obs, ctx), bot().act(other, ctx))
        }
    }
}
