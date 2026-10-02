package com.kaynzhang.doudizhu.engine.ai

import com.kaynzhang.doudizhu.engine.game.Action
import com.kaynzhang.doudizhu.engine.game.GameState
import com.kaynzhang.doudizhu.engine.game.Observation
import com.kaynzhang.doudizhu.engine.model.Rng

enum class Difficulty(val zh: String) {
    EASY("简单"),
    NORMAL("普通"),
    HARD("困难"),
    SUPER("超级"),
}

/** How much work a searching bot may do for one decision. */
sealed interface SearchBudget {
    /** Fixed number of sampled deals: deterministic, used by tests. */
    data class Samples(val n: Int) : SearchBudget

    /** Wall-clock budget with bounds on the sample count: used by the app. */
    data class Millis(val ms: Long, val min: Int = 24, val max: Int = 256) : SearchBudget
}

/** Per-decision inputs that are not part of the observation. */
class BotContext(
    /** Seed for any randomness in this decision; see [decisionSeed]. */
    val seed: Long,
    val budget: SearchBudget = SearchBudget.Samples(32),
)

/** A player. Implementations must decide from the [Observation] alone. */
interface Bot {
    suspend fun act(obs: Observation, ctx: BotContext): Action
}

object Bots {
    fun create(difficulty: Difficulty): Bot = when (difficulty) {
        Difficulty.EASY -> EasyBot()
        Difficulty.NORMAL -> NormalBot()
        Difficulty.HARD -> HardBot()
        Difficulty.SUPER -> SuperBot()
    }

    /** Seed for [seat]'s decision in [state]; changes with every action so decisions don't repeat. */
    fun decisionSeed(state: GameState, seat: Int): Long =
        Rng.mix(state.seed, state.dealNo.toLong(), state.seq.toLong(), seat.toLong())
}
