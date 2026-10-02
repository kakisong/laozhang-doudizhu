package com.kaynzhang.doudizhu.engine.ai

import com.kaynzhang.doudizhu.engine.game.GameState
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A game saved mid-way and restored from JSON must continue exactly as if it had never stopped:
 * bots are stateless (they decide from the observation and a seed derived from the state).
 */
class ResumeTest {

    @Test
    fun `restoring a saved game continues identically`() {
        for (seed in 0 until 60L) {
            val bots = { listOf<Bot>(NormalBot(), HardBot(), EasyBot()) }
            val states = ArrayList<GameState>()
            val uninterrupted = Simulator.play(bots(), seed) { _, t -> states.add(t.state) }

            val cut = states[states.size / 2]
            val restored = Json.decodeFromString(GameState.serializer(), Json.encodeToString(GameState.serializer(), cut))
            val resumed = Simulator.play(bots(), seed, start = restored)

            assertEquals(uninterrupted, resumed, "game $seed diverged after restoring at seq ${cut.seq}")
        }
    }
}
