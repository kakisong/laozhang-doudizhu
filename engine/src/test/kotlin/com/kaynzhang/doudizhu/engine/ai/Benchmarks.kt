package com.kaynzhang.doudizhu.engine.ai

import com.kaynzhang.doudizhu.engine.game.Action
import com.kaynzhang.doudizhu.engine.game.GameEngine
import com.kaynzhang.doudizhu.engine.game.GameState
import com.kaynzhang.doudizhu.engine.game.Phase
import com.kaynzhang.doudizhu.engine.game.Seats
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sqrt

/** Shared helpers for the slow benchmark and calibration tests. */
object Benchmarks {

    /** A game whose first bidder is forced to become landlord, nobody doubles. */
    fun forcedLandlordStart(seed: Long): GameState {
        var s = GameEngine.newGame(Simulator.DEFAULT_CONFIG, seed).state
        s = GameEngine.apply(s, Action.Bid(s.turn, true)).state
        while (s.phase == Phase.BIDDING) s = GameEngine.apply(s, Action.Bid(s.turn, false)).state
        for (seat in 0 until Seats.COUNT) s = GameEngine.apply(s, Action.Jiabei(seat, false)).state
        return s
    }

    /** Landlord outcome of one deal with the given landlord/farmer bots (true = landlord won). */
    fun landlordWins(seed: Long, landlordBot: Bot, farmerBot: () -> Bot, budget: SearchBudget = SearchBudget.Samples(16)): Boolean {
        val start = forcedLandlordStart(seed)
        val bots = List(Seats.COUNT) { if (it == start.landlord) landlordBot else farmerBot() }
        return Simulator.play(bots, seed, start = start, budget = budget).result!!.landlordWon
    }

    data class Paired(val n: Int, val meanDiff: Double, val ci95: Double) {
        override fun toString() = "n=$n diff=%+.3f ±%.3f".format(meanDiff, ci95)
        val significant: Boolean get() = abs(meanDiff) > ci95
    }

    /** Mean and 95% confidence half-width of paired differences. */
    fun paired(diffs: List<Double>): Paired {
        val n = diffs.size
        val mean = diffs.average()
        val variance = diffs.sumOf { (it - mean) * (it - mean) } / (n - 1)
        return Paired(n, mean, 1.96 * sqrt(variance / n))
    }

    /** Logistic regression by Newton–Raphson (IRLS) with a small ridge term. */
    fun fitLogistic(xs: List<DoubleArray>, ys: List<Boolean>, iterations: Int = 25): DoubleArray {
        val d = xs.first().size
        val w = DoubleArray(d)
        repeat(iterations) {
            val grad = DoubleArray(d)
            val hess = Array(d) { DoubleArray(d) }
            for (i in xs.indices) {
                val x = xs[i]
                var z = 0.0
                for (j in 0 until d) z += w[j] * x[j]
                val p = 1.0 / (1.0 + exp(-z))
                val y = if (ys[i]) 1.0 else 0.0
                for (j in 0 until d) {
                    grad[j] += (y - p) * x[j]
                    for (k in 0 until d) hess[j][k] += p * (1 - p) * x[j] * x[k]
                }
            }
            for (j in 0 until d) hess[j][j] += 1e-6
            val step = solve(hess, grad)
            for (j in 0 until d) w[j] += step[j]
        }
        return w
    }

    private fun solve(a: Array<DoubleArray>, b: DoubleArray): DoubleArray {
        val n = b.size
        val m = Array(n) { i -> a[i].copyOf(n + 1).also { it[n] = b[i] } }
        for (col in 0 until n) {
            val pivot = (col until n).maxBy { abs(m[it][col]) }
            val tmp = m[col]; m[col] = m[pivot]; m[pivot] = tmp
            for (row in 0 until n) {
                if (row == col) continue
                val f = m[row][col] / m[col][col]
                for (k in col..n) m[row][k] -= f * m[col][k]
            }
        }
        return DoubleArray(n) { m[it][n] / m[it][it] }
    }
}
