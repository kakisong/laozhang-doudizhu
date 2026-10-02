package com.kaynzhang.doudizhu.engine.ai

import com.kaynzhang.doudizhu.engine.game.Seats
import org.junit.jupiter.api.Tag
import kotlin.math.exp
import kotlin.test.Test

/**
 * Fits [BidModel] from self-play with a forced landlord (random hands, no bidding selection).
 * Run with `./gradlew :engine:slowTest --tests '*CalibrationTest*'` and paste the printed weights.
 */
@Tag("slow")
class CalibrationTest {

    @Test
    fun `fit bid model`() {
        val games = System.getProperty("ddz.calibration.games")?.toInt() ?: 20_000
        val analyzer = HandAnalyzer()
        val x17 = ArrayList<DoubleArray>()
        val x20 = ArrayList<DoubleArray>()
        val y = ArrayList<Boolean>()
        for (seed in 0 until games.toLong()) {
            val start = Benchmarks.forcedLandlordStart(seed)
            val landlord = start.landlord
            val hand20 = start.hands[landlord].counts()
            val hand17 = hand20 - start.bottom.counts()
            val bots = List(Seats.COUNT) { NormalBot() }
            val end = Simulator.play(bots, seed, start = start)
            x17.add(BidModel.features(hand17, analyzer))
            x20.add(BidModel.features(hand20, analyzer))
            y.add(end.result!!.landlordWon)
        }
        println("landlord win rate (random hands, Normal vs Normal): %.3f over %d games".format(y.count { it }.toDouble() / y.size, y.size))
        val w17 = Benchmarks.fitLogistic(x17, y)
        val w20 = Benchmarks.fitLogistic(x20, y)
        println("W17 = doubleArrayOf(${w17.joinToString { "%.4f".format(it) }})")
        println("W20 = doubleArrayOf(${w20.joinToString { "%.4f".format(it) }})")
        calibrationTable("17-card", x17, y, w17)
        calibrationTable("20-card", x20, y, w20)
    }

    private fun calibrationTable(label: String, xs: List<DoubleArray>, ys: List<Boolean>, w: DoubleArray) {
        val buckets = Array(10) { IntArray(2) }
        for (i in xs.indices) {
            var z = 0.0
            for (j in w.indices) z += w[j] * xs[i][j]
            val p = 1 / (1 + exp(-z))
            val b = (p * 10).toInt().coerceIn(0, 9)
            buckets[b][0]++
            if (ys[i]) buckets[b][1]++
        }
        println("$label calibration (predicted bucket: n, actual win rate):")
        for (b in 0 until 10) if (buckets[b][0] > 0) {
            println("  %.1f-%.1f: n=%5d actual=%.3f".format(b / 10.0, (b + 1) / 10.0, buckets[b][0], buckets[b][1].toDouble() / buckets[b][0]))
        }
    }

    @Test
    fun `bidding statistics with the fitted model`() {
        var redeals = 0
        var games = 0
        var landlordWins = 0
        var robs = 0
        for (seed in 0 until 3_000L) {
            val end = Simulator.play(List(3) { NormalBot() }, seed) { _, t ->
                redeals += t.events.count { it is com.kaynzhang.doudizhu.engine.game.GameEvent.Dealt && it.isRedeal }
            }
            games++
            robs += end.robs
            if (end.result!!.landlordWon) landlordWins++
        }
        println("Normal self-play with bidding: landlordWin=%.3f redeals/game=%.2f robs/game=%.2f".format(
            landlordWins.toDouble() / games, redeals.toDouble() / games, robs.toDouble() / games))
    }
}
