package com.kaynzhang.doudizhu.engine.ai

import com.kaynzhang.doudizhu.engine.game.GameConfig
import com.kaynzhang.doudizhu.engine.game.GameEngine
import com.kaynzhang.doudizhu.engine.game.GameState
import com.kaynzhang.doudizhu.engine.game.Phase
import com.kaynzhang.doudizhu.engine.game.Transition
import kotlinx.coroutines.runBlocking

/** Plays whole games between bots (test-only). */
object Simulator {

    val DEFAULT_CONFIG = GameConfig(baseScore = 100, seatCoins = listOf(1_000_000L, 1_000_000L, 1_000_000L))

    fun play(
        bots: List<Bot>,
        seed: Long,
        config: GameConfig = DEFAULT_CONFIG,
        budget: SearchBudget = SearchBudget.Samples(8),
        start: GameState = GameEngine.newGame(config, seed).state,
        maxSteps: Int = 1_000,
        onStep: (before: GameState, t: Transition) -> Unit = { _, _ -> },
    ): GameState = runBlocking {
        var s = start
        var steps = 0
        while (s.phase != Phase.FINISHED) {
            val seat = GameEngine.actors(s).first()
            val action = bots[seat].act(GameEngine.observe(s, seat), BotContext(Bots.decisionSeed(s, seat), budget))
            val t = GameEngine.apply(s, action)
            onStep(s, t)
            s = t.state
            check(++steps <= maxSteps) { "game $seed did not finish within $maxSteps steps" }
        }
        s
    }
}
