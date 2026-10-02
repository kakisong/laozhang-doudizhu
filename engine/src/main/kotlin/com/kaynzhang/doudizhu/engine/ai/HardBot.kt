package com.kaynzhang.doudizhu.engine.ai

import com.kaynzhang.doudizhu.engine.game.Action
import com.kaynzhang.doudizhu.engine.game.Observation
import com.kaynzhang.doudizhu.engine.game.Phase
import com.kaynzhang.doudizhu.engine.game.Seats
import com.kaynzhang.doudizhu.engine.model.CardSet
import com.kaynzhang.doudizhu.engine.model.Rng
import com.kaynzhang.doudizhu.engine.rules.Move
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicIntegerArray
import kotlin.math.sqrt

/**
 * 困难: [NormalPolicy]'s best few candidates, re-ranked by perfect-information Monte Carlo (PIMC).
 *
 * For every sample the unseen cards are dealt at random to the other two seats (the landlord's
 * unplayed bottom cards are known to be in the landlord's hand), every candidate is applied, and
 * the deal is played out by [LitePlayout]; small endgames are solved exactly by [EndgameSolver].
 * All candidates share the same samples, so their win rates are compared on equal footing, and
 * Normal's own choice is kept unless another candidate beats it by more than a standard error.
 *
 * The bot only reads its [Observation]: it never sees the real hidden cards. With
 * [SearchBudget.Samples] the decision is bit-identical for a given observation and seed however
 * the threads are scheduled: samples are split statically between the workers, and every worker
 * clears its solver cache before a search, so each sample is evaluated the same way every time.
 */
class HardBot(private val analyzer: HandAnalyzer = HandAnalyzer()) : Bot {
    private val policy = NormalPolicy(analyzer)

    /** Counters for tests and benchmarks: decisions made, decisions searched, samples evaluated. */
    internal val stats = LongArray(3)

    override suspend fun act(obs: Observation, ctx: BotContext): Action {
        val rng = Rng(ctx.seed)
        return when (obs.phase) {
            Phase.BIDDING -> Action.Bid(obs.seat, BidPolicy.bid(obs, Difficulty.HARD, analyzer, rng))
            Phase.DOUBLING -> Action.Jiabei(obs.seat, BidPolicy.jiabei(obs, Difficulty.HARD, analyzer, rng))
            Phase.PLAYING -> choose(obs, ctx).toAction(obs)
            Phase.FINISHED -> error("the game is over")
        }
    }

    /** The play to make (null = pass). */
    suspend fun choose(obs: Observation, ctx: BotContext): Move? {
        stats[0]++
        val all = policy.candidates(obs, ALL_CANDIDATES)
        if (all.size == 1) return all.first()
        // Down to one loose combo: Normal already plays the winning line.
        val view = TableView(obs)
        if (analyzer.plan(view.hand).items.count { !view.isControl(it.combo) } <= 1) return all.first()

        val candidates = ArrayList<Move?>(all.take(CANDIDATES))
        if (!obs.isLeading && null !in candidates) candidates.add(null)
        all.firstOrNull { it != null && it.combo.isBomb }?.let { if (it !in candidates) candidates.add(it) }
        if (candidates.size == 1) return candidates.first()

        val wins = POOL_LOCK.withLock { search(obs, candidates, ctx) }
        stats[1]++
        stats[2] += wins[0].size
        return candidates[pick(wins)]
    }

    /** Per-candidate outcomes over the completed samples: `result[c][i]` is 1 if the bot's side won. */
    private suspend fun search(obs: Observation, candidates: List<Move?>, ctx: BotContext): Array<ByteArray> {
        val budget = ctx.budget
        val maxSamples: Int
        val minSamples: Int
        val deadline: Long
        when (budget) {
            is SearchBudget.Samples -> {
                maxSamples = budget.n
                minSamples = budget.n
                deadline = Long.MAX_VALUE
            }
            is SearchBudget.Millis -> {
                maxSamples = budget.max
                minSamples = budget.min
                deadline = System.nanoTime() + budget.ms * 1_000_000
            }
        }
        val adaptive = budget is SearchBudget.Millis
        val results = Array(candidates.size) { ByteArray(maxSamples) }
        val done = AtomicIntegerArray(maxSamples)
        val stop = AtomicBoolean(false)
        val setup = SearchSetup(obs, candidates)

        coroutineScope {
            (0 until WORKERS).map { w ->
                async(DISPATCHER) {
                    val worker = POOL[w]
                    worker.solver.clear()
                    var i = w
                    while (i < maxSamples && !stop.get()) {
                        if (i >= minSamples && System.nanoTime() > deadline) break
                        ensureActive()
                        worker.evaluate(setup, Rng.mix(ctx.seed, i.toLong()), results, i)
                        done.set(i, 1)
                        if (adaptive && w == 0 && i >= EARLY_STOP_MIN && clearWinner(results, done)) stop.set(true)
                        i += WORKERS
                    }
                }
            }.awaitAll()
        }
        val n = completedPrefix(done)
        return Array(candidates.size) { results[it].copyOf(n) }
    }

    /** Best mean; Normal's first choice (index 0) stays unless beaten by more than one standard error. */
    private fun pick(wins: Array<ByteArray>): Int {
        val n = wins[0].size
        if (n == 0) return 0
        var best = 0
        var bestSum = wins[0].sum()
        for (c in 1 until wins.size) {
            val s = wins[c].sum()
            if (s > bestSum) {
                best = c
                bestSum = s
            }
        }
        if (best == 0) return 0
        val (mean, se) = pairedDiff(wins[best], wins[0], n)
        return if (mean > se) best else 0
    }

    /** Lets the adaptive search stop once the leader is 3 standard errors ahead of the runner-up. */
    private fun clearWinner(results: Array<ByteArray>, done: AtomicIntegerArray): Boolean {
        val n = completedPrefix(done)
        if (n < EARLY_STOP_MIN || results.size < 2) return false
        val sums = IntArray(results.size) { c ->
            var s = 0
            for (i in 0 until n) s += results[c][i]
            s
        }
        val order = sums.indices.sortedByDescending { sums[it] }
        val (mean, se) = pairedDiff(results[order[0]], results[order[1]], n)
        return se > 0 && mean > 3 * se
    }

    private fun completedPrefix(done: AtomicIntegerArray): Int {
        var n = 0
        while (n < done.length() && done.get(n) == 1) n++
        return n
    }

    private fun ByteArray.sum(): Int {
        var s = 0
        for (b in this) s += b
        return s
    }

    /** What every worker needs to evaluate a sample, fixed for the whole decision. */
    private class SearchSetup(obs: Observation, val candidates: List<Move?>) {
        val me = obs.seat
        val landlord = obs.landlord
        val myHand = obs.hand.counts().packed
        val handSizes = IntArray(Seats.COUNT) { obs.handSizes[it] }
        val trickKey = obs.trick?.key ?: -1
        val owner = obs.trickOwner
        val landlordKnown: CardSet = if (obs.landlord == obs.seat) CardSet.EMPTY else obs.landlordKnownCards
        val hidden: IntArray = (obs.unseen - landlordKnown).cards().map { it.bit }.toIntArray()
    }

    /** Search state of one thread. */
    private class Worker {
        val analyzer = HandAnalyzer(maxMemo = WORKER_MEMO)
        val solver = EndgameSolver(tableBits = 16)
        val playout = LitePlayout(analyzer, solver)
        private var deck = IntArray(0)
        private val dealt = LongArray(Seats.COUNT)

        fun evaluate(setup: SearchSetup, seed: Long, results: Array<ByteArray>, sample: Int) {
            deal(setup, Rng(seed))
            val me = setup.me
            val iAmLandlord = me == setup.landlord
            for (c in setup.candidates.indices) {
                for (s in 0 until Seats.COUNT) playout.hands[s] = dealt[s]
                playout.landlord = setup.landlord
                playout.resetCache()
                val move = setup.candidates[c]
                val landlordWon = if (move == null) {
                    val next = Seats.next(me)
                    if (next == setup.owner) playout.run(next, -1, -1) else playout.run(next, setup.trickKey, setup.owner)
                } else {
                    playout.hands[me] -= move.counts.packed
                    if (playout.hands[me] == 0L) iAmLandlord else playout.run(Seats.next(me), move.combo.key, me)
                }
                results[c][sample] = if (landlordWon == iAmLandlord) 1 else 0
            }
        }

        /** Deals the hidden cards to the other two seats; the landlord keeps its known bottom cards. */
        private fun deal(setup: SearchSetup, rng: Rng) {
            if (deck.size != setup.hidden.size) deck = IntArray(setup.hidden.size)
            System.arraycopy(setup.hidden, 0, deck, 0, deck.size)
            for (i in deck.size - 1 downTo 1) {
                val j = rng.nextInt(i + 1)
                val t = deck[i]
                deck[i] = deck[j]
                deck[j] = t
            }
            val me = setup.me
            dealt[me] = setup.myHand
            var pos = 0
            for (s in 0 until Seats.COUNT) {
                if (s == me) continue
                var bits = if (s == setup.landlord) setup.landlordKnown.bits else 0L
                repeat(setup.handSizes[s] - java.lang.Long.bitCount(bits)) { bits = bits or (1L shl deck[pos++]) }
                dealt[s] = CardSet(bits).counts().packed
            }
            check(pos == deck.size) { "a sample must place every hidden card" }
        }
    }

    companion object {
        private const val WORKERS = 3
        private const val CANDIDATES = 6
        private const val ALL_CANDIDATES = 64
        private const val EARLY_STOP_MIN = 32
        private const val WORKER_MEMO = 100_000

        private val DISPATCHER = Dispatchers.Default.limitedParallelism(WORKERS)

        /**
         * One worker pool shared by every HardBot: only one seat thinks at a time, and the pool
         * (analyzer caches, solver tables) is the bulk of the bot's memory.
         */
        private val POOL by lazy { Array(WORKERS) { Worker() } }
        private val POOL_LOCK = Mutex()

        /** Mean and standard error of the paired difference a[i] − b[i] over the first [n] samples. */
        internal fun pairedDiff(a: ByteArray, b: ByteArray, n: Int): Pair<Double, Double> {
            var sum = 0.0
            var sumSq = 0.0
            for (i in 0 until n) {
                val d = (a[i] - b[i]).toDouble()
                sum += d
                sumSq += d * d
            }
            val mean = sum / n
            if (n < 2) return mean to Double.MAX_VALUE
            val variance = (sumSq - n * mean * mean) / (n - 1)
            return mean to sqrt(maxOf(variance, 0.0) / n)
        }

        /** Playout counters summed over the shared pool: playouts, moves, solver calls, solver answers. */
        internal fun playoutStats(): LongArray {
            val out = LongArray(4)
            for (w in POOL) {
                out[0] += w.playout.playouts
                out[1] += w.playout.moves
                out[2] += w.playout.solverCalls
                out[3] += w.playout.solved
            }
            return out
        }
    }
}
