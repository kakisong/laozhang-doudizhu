package com.kaynzhang.doudizhu.engine.ai

import com.kaynzhang.doudizhu.engine.game.GameEngine
import com.kaynzhang.doudizhu.engine.game.Phase
import com.kaynzhang.doudizhu.engine.game.Seats
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Tag
import kotlin.test.Test

/**
 * Duplicate-deal strength comparison: the same deal is played once with the bot under test in
 * a role and once with the baseline in that role, so deal luck cancels out of the difference.
 *
 * `./gradlew :engine:slowTest --tests '*StrengthBenchmarkTest*' -Dddz.bench.deals=1000`
 */
@Tag("slow")
class StrengthBenchmarkTest {

    private val deals = System.getProperty("ddz.bench.deals")?.toInt() ?: 1_000
    private val hardBudget = SearchBudget.Samples(System.getProperty("ddz.bench.samples")?.toInt() ?: 64)

    private fun win(b: Boolean) = if (b) 1.0 else 0.0

    @Test
    fun `hard landlord vs normal landlord`() {
        val t0 = System.nanoTime()
        val diffs = (0 until deals).map { i ->
            val seed = 10_000L + i
            win(Benchmarks.landlordWins(seed, HardBot(), { NormalBot() }, hardBudget)) -
                win(Benchmarks.landlordWins(seed, NormalBot(), { NormalBot() }))
        }
        report("Hard−Normal as landlord (farmers Normal)", diffs, t0)
    }

    @Test
    fun `hard farmers vs normal farmers`() {
        val t0 = System.nanoTime()
        val diffs = (0 until deals).map { i ->
            val seed = 20_000L + i
            // Positive = the farmer side wins more often with Hard farmers.
            win(!Benchmarks.landlordWins(seed, NormalBot(), { HardBot() }, hardBudget)) -
                win(!Benchmarks.landlordWins(seed, NormalBot(), { NormalBot() }))
        }
        report("Hard−Normal as farmers (landlord Normal)", diffs, t0)
    }

    @Test
    fun `normal vs easy`() {
        val t0 = System.nanoTime()
        val landlord = (0 until deals * 4).map { i ->
            val seed = 30_000L + i
            win(Benchmarks.landlordWins(seed, NormalBot(), { EasyBot() })) - win(Benchmarks.landlordWins(seed, EasyBot(), { EasyBot() }))
        }
        report("Normal−Easy as landlord (farmers Easy)", landlord, t0)
        val t1 = System.nanoTime()
        val farmers = (0 until deals * 4).map { i ->
            val seed = 40_000L + i
            win(!Benchmarks.landlordWins(seed, EasyBot(), { NormalBot() })) - win(!Benchmarks.landlordWins(seed, EasyBot(), { EasyBot() }))
        }
        report("Normal−Easy as farmers (landlord Easy)", farmers, t1)
    }

    @Test
    fun `hard decision time with the app budget`() = runBlocking {
        val bot = HardBot()
        val times = ArrayList<Double>()
        for (seed in 0 until 60L) {
            var s = Benchmarks.forcedLandlordStart(50_000 + seed)
            val bots = List(Seats.COUNT) { NormalBot() }
            while (s.phase == Phase.PLAYING) {
                val seat = s.turn
                val obs = GameEngine.observe(s, seat)
                val ctx = BotContext(Bots.decisionSeed(s, seat), SearchBudget.Millis(450))
                val action = if (seat == s.landlord) {
                    val t = System.nanoTime()
                    bot.act(obs, ctx).also { times.add((System.nanoTime() - t) / 1e6) }
                } else {
                    bots[seat].act(obs, ctx)
                }
                s = GameEngine.apply(s, action).state
            }
        }
        times.sort()
        println("Hard decision ms with Millis(450): n=%d mean=%.1f p50=%.1f p90=%.1f max=%.1f".format(
            times.size, times.average(), times[times.size / 2], times[times.size * 9 / 10], times.last()))
        println("searched ${bot.stats[1]} of ${bot.stats[0]} decisions, %.0f samples per search".format(bot.stats[2].toDouble() / bot.stats[1]))
        val p = HardBot.playoutStats()
        println("playouts=${p[0]} moves/playout=%.1f solver calls=${p[2]} answered=${p[3]}".format(p[1].toDouble() / p[0]))
    }

    private fun report(label: String, diffs: List<Double>, t0: Long) {
        val p = Benchmarks.paired(diffs)
        println("$label: $p ${if (p.significant) "(significant)" else "(not significant)"} in %.0fs".format((System.nanoTime() - t0) / 1e9))
    }
}
