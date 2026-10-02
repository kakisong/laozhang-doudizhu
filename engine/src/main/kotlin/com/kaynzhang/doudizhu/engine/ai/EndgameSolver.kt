package com.kaynzhang.doudizhu.engine.ai

import com.kaynzhang.doudizhu.engine.game.Seats
import com.kaynzhang.doudizhu.engine.model.Counts
import com.kaynzhang.doudizhu.engine.rules.Combo
import com.kaynzhang.doudizhu.engine.rules.KickerMode
import com.kaynzhang.doudizhu.engine.rules.MoveBuffer
import com.kaynzhang.doudizhu.engine.rules.MoveGenerator

/**
 * Exact perfect-information endgame search: "does the landlord's side win from here?".
 *
 * The two farmers act as one team that sees every hand, so the game is a two-player AND/OR
 * tree: landlord nodes need one winning move (OR), farmer nodes need every move to lose (AND).
 * Moves come from [MoveGenerator] with [KickerMode.ALL] so no kicker choice is missed; hands are
 * mutated in place and restored on the way back, so the search allocates nothing per node.
 *
 * Exact results go into an always-replace transposition table keyed by two independent 64-bit
 * hashes. A node budget bounds the work; running out yields [UNKNOWN], which is never cached.
 *
 * Not thread-safe: each search worker owns its own solver.
 */
class EndgameSolver(tableBits: Int = 18) {

    private val tableMask = (1 shl tableBits) - 1
    private val keysA = LongArray(1 shl tableBits)
    private val keysB = LongArray(1 shl tableBits)
    private val results = ByteArray(1 shl tableBits) // 0 empty, 1 landlord wins, 2 landlord loses

    private val hands = LongArray(Seats.COUNT)
    private var landlord = 0
    private var budget = 0
    private var aborted = false

    /** Nodes visited by the last call. */
    var lastNodes = 0
        private set

    private val frames = ArrayList<Frame>()

    /**
     * Solves the position. [trickKey] is the [Combo.key] to beat, or -1 when [turn] leads freely;
     * [owner] is the seat that played it (-1 when leading). Every hand must be non-empty.
     * Returns [WIN], [LOSS] (from the landlord's side) or [UNKNOWN] when [nodeBudget] ran out.
     */
    fun landlordWins(hands: LongArray, landlord: Int, turn: Int, trickKey: Int, owner: Int, nodeBudget: Int): Int {
        for (i in 0 until Seats.COUNT) this.hands[i] = hands[i]
        this.landlord = landlord
        budget = nodeBudget
        aborted = false
        lastNodes = 0
        val r = search(turn, trickKey, owner, 0)
        return if (aborted) UNKNOWN else r
    }

    /** Forgets every cached result (used to keep searches reproducible). */
    fun clear() = results.fill(0)

    private fun search(turn: Int, trickKey: Int, owner: Int, depth: Int): Int {
        if (++lastNodes > budget) {
            aborted = true
            return LOSS
        }
        val meta = turn.toLong() or ((owner + 1).toLong() shl 2) or (landlord.toLong() shl 4) or ((trickKey + 1).toLong() shl 8)
        val ha = hash(SEED_A, meta)
        val hb = hash(SEED_B, meta)
        val slot = (ha.toInt() ushr 7) and tableMask
        if (results[slot].toInt() != 0 && keysA[slot] == ha && keysB[slot] == hb) {
            return if (results[slot].toInt() == 1) WIN else LOSS
        }

        val isLandlord = turn == landlord
        val want = if (isLandlord) WIN else LOSS
        val hand = hands[turn]
        val frame = frame(depth)
        val buf = frame.buf
        buf.clear()
        if (trickKey < 0) MoveGenerator.leads(Counts(hand), buf, KickerMode.ALL)
        else MoveGenerator.beating(hand, trickKey, buf, KickerMode.ALL)

        var result = if (isLandlord) LOSS else WIN
        var decided = false
        for (i in 0 until buf.size) {
            if (buf.counts[i] == hand) {
                result = want
                decided = true
                break
            }
        }

        if (!decided) {
            val n = buf.size
            val order = frame.order(n)
            val prio = frame.prio
            for (i in 0 until n) {
                order[i] = i
                prio[i] = priority(turn, buf.counts[i], buf.keys[i])
            }
            // Insertion sort by priority, descending; stable so generation order breaks ties.
            for (i in 1 until n) {
                val x = order[i]
                var j = i - 1
                while (j >= 0 && prio[order[j]] < prio[x]) {
                    order[j + 1] = order[j]
                    j--
                }
                order[j + 1] = x
            }

            val canPass = trickKey >= 0
            val passFirst = canPass && !isLandlord && owner != landlord && owner != turn
            if (passFirst) {
                if (pass(turn, trickKey, owner, depth) == want) decided = true
                if (aborted) return LOSS
            }
            var i = 0
            while (!decided && i < n) {
                val idx = order[i++]
                val counts = buf.counts[idx]
                hands[turn] = hand - counts
                val r = search(Seats.next(turn), buf.keys[idx], turn, depth + 1)
                hands[turn] = hand
                if (aborted) return LOSS
                if (r == want) decided = true
            }
            if (!decided && canPass && !passFirst) {
                if (pass(turn, trickKey, owner, depth) == want) decided = true
                if (aborted) return LOSS
            }
            if (decided) result = want
        }

        keysA[slot] = ha
        keysB[slot] = hb
        results[slot] = if (result == WIN) 1 else 2
        return result
    }

    private fun pass(turn: Int, trickKey: Int, owner: Int, depth: Int): Int {
        val next = Seats.next(turn)
        return if (next == owner) search(next, -1, -1, depth + 1) else search(next, trickKey, owner, depth + 1)
    }

    /** Ordering: plays no opponent can answer first, then bigger plays. Emptying moves never get here. */
    private fun priority(turn: Int, counts: Long, key: Int): Int {
        var p = Counts(counts).total * 4
        var answerable = false
        for (s in 0 until Seats.COUNT) {
            if (s == turn || !isOpponent(turn, s)) continue
            if (MoveGenerator.canBeat(hands[s], key)) {
                answerable = true
                break
            }
        }
        if (!answerable) p += 1_000
        if (Combo.keyIsBomb(key)) p -= 50
        return p
    }

    private fun isOpponent(a: Int, b: Int): Boolean = (a == landlord) != (b == landlord)

    private fun hash(seed: Long, meta: Long): Long {
        var h = seed
        h = mix(h xor hands[0])
        h = mix(h xor hands[1])
        h = mix(h xor hands[2])
        return mix(h xor meta)
    }

    private fun frame(depth: Int): Frame {
        while (frames.size <= depth) frames.add(Frame())
        return frames[depth]
    }

    private class Frame {
        val buf = MoveBuffer(64)
        private var orderArr = IntArray(64)
        var prio = IntArray(64)
            private set

        fun order(n: Int): IntArray {
            if (orderArr.size < n) {
                orderArr = IntArray(n * 2)
                prio = IntArray(n * 2)
            }
            return orderArr
        }
    }

    companion object {
        const val LOSS = 0
        const val WIN = 1
        const val UNKNOWN = -1

        private const val SEED_A = 0x3C6EF372FE94F82BL
        private const val SEED_B = -0x5AB00AC5A1F14A42L

        private fun mix(z0: Long): Long {
            var z = z0 + -0x61c8864680b583ebL
            z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L
            z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
            return z xor (z ushr 31)
        }
    }
}
