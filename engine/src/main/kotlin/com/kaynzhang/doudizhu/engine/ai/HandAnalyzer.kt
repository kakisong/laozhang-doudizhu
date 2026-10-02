package com.kaynzhang.doudizhu.engine.ai

import com.kaynzhang.doudizhu.engine.model.Counts
import com.kaynzhang.doudizhu.engine.model.Rk
import com.kaynzhang.doudizhu.engine.rules.Combo
import com.kaynzhang.doudizhu.engine.rules.ComboType
import com.kaynzhang.doudizhu.engine.rules.Move

/**
 * 拆牌: splits a hand into combos and scores the split as Σ value(combo) − [TURN_COST] × combos.
 *
 * Exact memoized DP over "lowest remaining rank first". Kickers are tracked as two balances
 * (single and pair): a positive balance counts low cards set aside for a later triple, a negative
 * one counts earlier triples still waiting for a kicker. That lets a triple take a kicker below
 * or above it while the score stays additive, so memoizing on (counts, balances) is exact.
 *
 * Simplifications: A, 2 and jokers never serve as kickers; four of a kind stays a bomb;
 * plane wings come from set-aside low cards or from the lowest loose cards above the plane.
 *
 * Not thread-safe: give every worker its own instance. [maxMemo] bounds the cache (it is
 * cleared when full), which bounds memory at roughly 30 bytes per entry.
 */
class HandAnalyzer(private val maxMemo: Int = DEFAULT_MAX_MEMO) {

    private val memo = LongIntMap(1 shl 14)

    /** Best achievable split score for [hand]. */
    fun score(hand: Counts): Int {
        if (memo.size > maxMemo) memo.clear()
        return best(hand.packed, 0, 0)
    }

    /** The best split itself, kickers attached, in ascending rank order. */
    fun plan(hand: Counts): Plan {
        val total = score(hand)
        val items = ArrayList<Move>()
        val pendingSingles = ArrayDeque<Int>()
        val pendingPairs = ArrayDeque<Int>()
        val openSingle = ArrayDeque<Int>() // indices into items: triples waiting for a single kicker
        val openPair = ArrayDeque<Int>()

        fun attach(index: Int, kicker: Long, type: ComboType) {
            val old = items[index]
            items[index] = Move(Counts(old.counts.packed + kicker), Combo(type, old.combo.rank, 1))
        }

        var c = hand.packed
        var s = 0
        var p = 0
        while (c != 0L) {
            val target = best(c, s, p)
            var chosen = false
            var nc = 0L
            var ns = 0
            var np = 0
            var kind = 0
            var rank = 0
            var len = 0
            var wings = 0L
            forEachOption(c, s, p) { gain, oc, os, op, oKind, oRank, oLen, oWings ->
                if (!chosen) {
                    val child = best(oc, os, op)
                    if (child != NEG && gain + child == target) {
                        chosen = true
                        nc = oc; ns = os; np = op; kind = oKind; rank = oRank; len = oLen; wings = oWings
                    }
                }
            }
            check(chosen) { "no option reproduces the optimum for ${Counts(c)}" }
            val main = c - nc - wings
            when (kind) {
                K_STRAIGHT -> items.add(Move(Counts(main), Combo(ComboType.STRAIGHT, rank, len)))
                K_PAIR_STRAIGHT -> items.add(Move(Counts(main), Combo(ComboType.PAIR_STRAIGHT, rank, len)))
                K_PLANE -> items.add(Move(Counts(main), Combo(ComboType.PLANE, rank, len)))
                K_PLANE_PENDING_S -> {
                    var k = 0L
                    repeat(len) { k += unit(pendingSingles.removeLast(), 1) }
                    items.add(Move(Counts(main + k), Combo(ComboType.PLANE_SINGLES, rank, len)))
                }
                K_PLANE_PENDING_P -> {
                    var k = 0L
                    repeat(len) { k += unit(pendingPairs.removeLast(), 2) }
                    items.add(Move(Counts(main + k), Combo(ComboType.PLANE_PAIRS, rank, len)))
                }
                K_PLANE_WINGS_S -> items.add(Move(Counts(main + wings), Combo(ComboType.PLANE_SINGLES, rank, len)))
                K_PLANE_WINGS_P -> items.add(Move(Counts(main + wings), Combo(ComboType.PLANE_PAIRS, rank, len)))
                K_SINGLE -> items.add(Move(Counts(main), Combo(ComboType.SINGLE, rank)))
                K_PAIR -> items.add(Move(Counts(main), Combo(ComboType.PAIR, rank)))
                K_BOMB -> items.add(Move(Counts(main), Combo(ComboType.BOMB, rank)))
                K_ROCKET -> items.add(Move(Counts(main), Combo(ComboType.ROCKET, Rk.SJ)))
                K_TRIPLE -> items.add(Move(Counts(main), Combo(ComboType.TRIPLE, rank)))
                K_SINGLE_KICKER ->
                    if (openSingle.isNotEmpty()) attach(openSingle.removeLast(), unit(rank, 1), ComboType.TRIPLE_SINGLE)
                    else pendingSingles.addLast(rank)
                K_PAIR_KICKER ->
                    if (openPair.isNotEmpty()) attach(openPair.removeLast(), unit(rank, 2), ComboType.TRIPLE_PAIR)
                    else pendingPairs.addLast(rank)
                K_TRIPLE_S ->
                    if (pendingSingles.isNotEmpty()) {
                        items.add(Move(Counts(main + unit(pendingSingles.removeLast(), 1)), Combo(ComboType.TRIPLE_SINGLE, rank)))
                    } else {
                        items.add(Move(Counts(main), Combo(ComboType.TRIPLE, rank)))
                        openSingle.addLast(items.size - 1)
                    }
                K_TRIPLE_P ->
                    if (pendingPairs.isNotEmpty()) {
                        items.add(Move(Counts(main + unit(pendingPairs.removeLast(), 2)), Combo(ComboType.TRIPLE_PAIR, rank)))
                    } else {
                        items.add(Move(Counts(main), Combo(ComboType.TRIPLE, rank)))
                        openPair.addLast(items.size - 1)
                    }
            }
            c = nc
            s = ns
            p = np
        }
        check(pendingSingles.isEmpty() && pendingPairs.isEmpty())
        return Plan(items, total)
    }

    private fun best(c: Long, s: Int, p: Int): Int {
        if (c == 0L) return if (s <= 0 && p <= 0) 0 else NEG
        val key = key(c, s, p)
        val cached = memo.get(key, MISSING)
        if (cached != MISSING) return cached
        var result = NEG
        forEachOption(c, s, p) { gain, nc, ns, np, _, _, _, _ ->
            val child = best(nc, ns, np)
            if (child != NEG && gain + child > result) result = gain + child
        }
        memo.put(key, result)
        return result
    }

    /** Enumerates the ways the lowest remaining rank can be used (see class doc). */
    private inline fun forEachOption(
        c: Long, s: Int, p: Int,
        f: (gain: Int, nc: Long, ns: Int, np: Int, kind: Int, rank: Int, len: Int, wings: Long) -> Unit,
    ) {
        val r = java.lang.Long.numberOfTrailingZeros(c) ushr 2
        val k = cnt(c, r)

        if (r <= Rk.MAX_CHAIN) {
            var chain = 0L
            var b = r
            while (b <= Rk.MAX_CHAIN && cnt(c, b) >= 1) {
                chain += unit(b, 1)
                val len = b - r + 1
                if (len >= Combo.MIN_STRAIGHT) f(chainValue(b, STRAIGHT_BASE) - TURN_COST, c - chain, s, p, K_STRAIGHT, r, len, 0L)
                b++
            }
            chain = 0L
            b = r
            while (b <= Rk.MAX_CHAIN && cnt(c, b) >= 2) {
                chain += unit(b, 2)
                val len = b - r + 1
                if (len >= Combo.MIN_PAIR_STRAIGHT) f(chainValue(b, STRAIGHT_BASE) - TURN_COST, c - chain, s, p, K_PAIR_STRAIGHT, r, len, 0L)
                b++
            }
            chain = 0L
            b = r
            while (b <= Rk.MAX_CHAIN && cnt(c, b) >= 3) {
                chain += unit(b, 3)
                val len = b - r + 1
                if (len >= Combo.MIN_PLANE) {
                    val gain = chainValue(b, PLANE_BASE) - TURN_COST
                    val rest = c - chain
                    f(gain, rest, s, p, K_PLANE, r, len, 0L)
                    if (s >= len) f(gain, rest, s - len, p, K_PLANE_PENDING_S, r, len, 0L)
                    if (p >= len) f(gain, rest, s, p - len, K_PLANE_PENDING_P, r, len, 0L)
                    val ws = looseAbove(rest, b, len, 1)
                    if (ws != NONE) f(gain, rest - ws, s, p, K_PLANE_WINGS_S, r, len, ws)
                    val wp = looseAbove(rest, b, len, 2)
                    if (wp != NONE) f(gain, rest - wp, s, p, K_PLANE_WINGS_P, r, len, wp)
                }
                b++
            }
        }

        val v = r - 10
        when (k) {
            1 -> {
                if (r == Rk.SJ && cnt(c, Rk.BJ) == 1) {
                    f(ROCKET_VALUE - TURN_COST, c - unit(Rk.SJ, 1) - unit(Rk.BJ, 1), s, p, K_ROCKET, r, 1, 0L)
                }
                f(v - TURN_COST, c - unit(r, 1), s, p, K_SINGLE, r, 1, 0L)
                if (r <= KICKER_MAX && s < MAX_BALANCE) f(0, c - unit(r, 1), s + 1, p, K_SINGLE_KICKER, r, 1, 0L)
            }
            2 -> {
                f(v - TURN_COST, c - unit(r, 2), s, p, K_PAIR, r, 1, 0L)
                if (r <= KICKER_MAX && p < MAX_BALANCE) f(0, c - unit(r, 2), s, p + 1, K_PAIR_KICKER, r, 1, 0L)
            }
            3 -> {
                val rest = c - unit(r, 3)
                f(v - TURN_COST, rest, s, p, K_TRIPLE, r, 1, 0L)
                if (s > -MAX_BALANCE) f(v - TURN_COST, rest, s - 1, p, K_TRIPLE_S, r, 1, 0L)
                if (p > -MAX_BALANCE) f(v - TURN_COST, rest, s, p - 1, K_TRIPLE_P, r, 1, 0L)
            }
            4 -> f(r + BOMB_BONUS - TURN_COST, c - unit(r, 4), s, p, K_BOMB, r, 1, 0L)
        }
    }

    /** The [n] lowest ranks above [top] (up to [KICKER_MAX]) holding exactly [width] cards, or [NONE]. */
    private fun looseAbove(c: Long, top: Int, n: Int, width: Int): Long {
        var acc = 0L
        var found = 0
        var q = top + 1
        while (q <= KICKER_MAX && found < n) {
            if (cnt(c, q) == width) {
                acc += unit(q, width)
                found++
            }
            q++
        }
        return if (found == n) acc else NONE
    }

    class Plan(val items: List<Move>, val score: Int) {
        val turns: Int get() = items.size
        override fun toString(): String = "Plan(score=$score, ${items.joinToString()})"
    }

    companion object {
        /** Cost of one extra turn (手数). */
        const val TURN_COST = 7
        const val BOMB_BONUS = 7
        const val ROCKET_VALUE = 20
        private const val STRAIGHT_BASE = 9
        private const val PLANE_BASE = 8

        /** Highest rank that may serve as a kicker (K). */
        const val KICKER_MAX = Rk.KING

        /** Value of a single combo, excluding [TURN_COST]; kickers count for nothing. */
        fun value(combo: Combo): Int = when (combo.type) {
            ComboType.SINGLE, ComboType.PAIR, ComboType.TRIPLE,
            ComboType.TRIPLE_SINGLE, ComboType.TRIPLE_PAIR -> combo.rank - 10
            ComboType.STRAIGHT, ComboType.PAIR_STRAIGHT -> chainValue(combo.topRank, STRAIGHT_BASE)
            ComboType.PLANE, ComboType.PLANE_SINGLES, ComboType.PLANE_PAIRS -> chainValue(combo.topRank, PLANE_BASE)
            ComboType.FOUR_TWO_SINGLES, ComboType.FOUR_TWO_PAIRS -> combo.rank - 9
            ComboType.BOMB -> combo.rank + BOMB_BONUS
            ComboType.ROCKET -> ROCKET_VALUE
        }

        private fun chainValue(top: Int, base: Int): Int = top - base

        private const val NEG = Int.MIN_VALUE / 4
        private const val MISSING = Int.MIN_VALUE
        private const val NONE = -1L
        private const val MAX_BALANCE = 15
        const val DEFAULT_MAX_MEMO = 400_000

        private const val K_STRAIGHT = 1
        private const val K_PAIR_STRAIGHT = 2
        private const val K_PLANE = 3
        private const val K_PLANE_PENDING_S = 4
        private const val K_PLANE_PENDING_P = 5
        private const val K_PLANE_WINGS_S = 6
        private const val K_PLANE_WINGS_P = 7
        private const val K_SINGLE = 8
        private const val K_PAIR = 9
        private const val K_BOMB = 10
        private const val K_ROCKET = 11
        private const val K_TRIPLE = 12
        private const val K_SINGLE_KICKER = 13
        private const val K_PAIR_KICKER = 14
        private const val K_TRIPLE_S = 15
        private const val K_TRIPLE_P = 16

        private fun cnt(h: Long, r: Int): Int = ((h ushr (r shl 2)) and 0xF).toInt()
        private fun unit(r: Int, n: Int): Long = n.toLong() shl (r shl 2)

        /** Squeezes 15 four-bit lanes (values ≤ 4) into 45 bits, then appends both balances. */
        private fun key(c: Long, s: Int, p: Int): Long {
            var x = (c and 0x0707070707070707L) or ((c and 0x7070707070707070L) ushr 1)
            x = (x and 0x003F003F003F003FL) or ((x and 0x3F003F003F003F00L) ushr 2)
            x = (x and 0x00000FFF00000FFFL) or ((x and 0x0FFF00000FFF0000L) ushr 4)
            x = (x and 0xFFFFFFL) or ((x and 0x00FFFFFF00000000L) ushr 8)
            return (x shl 10) or ((s + 16).toLong() shl 5) or (p + 16).toLong()
        }
    }
}
