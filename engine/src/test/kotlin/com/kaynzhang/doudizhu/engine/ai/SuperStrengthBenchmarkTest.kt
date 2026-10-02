package com.kaynzhang.doudizhu.engine.ai

import com.kaynzhang.doudizhu.engine.game.Action
import com.kaynzhang.doudizhu.engine.game.Observation
import org.junit.jupiter.api.Tag
import kotlin.test.Test

/** Duplicate-deal comparison of DouZero and Hard at the same role, against Hard opponents. */
@Tag("slow")
class SuperStrengthBenchmarkTest {
    private val deals = System.getProperty("ddz.bench.deals")?.toInt() ?: 200
    private val budget = SearchBudget.Samples(System.getProperty("ddz.bench.samples")?.toInt() ?: 64)
    private val unavailable = object : Bot {
        override suspend fun act(obs: Observation, ctx: BotContext): Action = error("Model fallback in benchmark")
    }

    @Test
    fun `super landlord compared with hard on paired deals`() {
        val model = SuperBot(fallback = unavailable)
        val diffs = (0 until deals).map { deal ->
            val seed = 60_000L + deal
            win(Benchmarks.landlordWins(seed, model, { HardBot() }, budget)) -
                win(Benchmarks.landlordWins(seed, HardBot(), { HardBot() }, budget))
        }
        println("Super−Hard landlord against Hard farmers: ${Benchmarks.paired(diffs)} (n=$deals, samples=${budget.n})")
    }

    @Test
    fun `super farmers compared with hard on paired deals`() {
        val diffs = (0 until deals).map { deal ->
            val seed = 70_000L + deal
            win(!Benchmarks.landlordWins(seed, HardBot(), { SuperBot(fallback = unavailable) }, budget)) -
                win(!Benchmarks.landlordWins(seed, HardBot(), { HardBot() }, budget))
        }
        println("Super−Hard farmers against Hard landlord: ${Benchmarks.paired(diffs)} (n=$deals, samples=${budget.n})")
    }

    private fun win(won: Boolean) = if (won) 1.0 else 0.0
}
