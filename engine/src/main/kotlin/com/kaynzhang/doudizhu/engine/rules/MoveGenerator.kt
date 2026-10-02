package com.kaynzhang.doudizhu.engine.rules

import com.kaynzhang.doudizhu.engine.model.Counts
import com.kaynzhang.doudizhu.engine.model.Rk

/** How kicker (带牌) variants are generated. */
enum class KickerMode {
    /** Every legal kicker multiset. Exact, used for hints, validation and the solver. */
    ALL,

    /** One kicker choice per main part: loose low cards first. Used by fast playouts. */
    CHEAPEST,
}

/**
 * Growable struct-of-arrays list of moves: packed counts plus packed [Combo.key].
 * Reused across calls to keep search loops allocation-free.
 */
class MoveBuffer(capacity: Int = 64) {
    var counts = LongArray(capacity)
        private set
    var keys = IntArray(capacity)
        private set
    var size = 0
        private set

    /** Generation stops early once [size] reaches this. */
    var limit = Int.MAX_VALUE

    val full: Boolean get() = size >= limit

    fun clear() {
        size = 0
        limit = Int.MAX_VALUE
    }

    fun add(countsPacked: Long, key: Int) {
        if (size == counts.size) {
            counts = counts.copyOf(size * 2)
            keys = keys.copyOf(size * 2)
        }
        counts[size] = countsPacked
        keys[size] = key
        size++
    }

    fun countsAt(i: Int): Counts = Counts(counts[i])
    fun comboAt(i: Int): Combo = Combo.fromKey(keys[i])
    fun moveAt(i: Int): Move = Move(Counts(counts[i]), Combo.fromKey(keys[i]))
    fun toList(): List<Move> = List(size) { moveAt(it) }
}

/**
 * Enumerates legal moves at the counts level. Suits are irrelevant to the rules;
 * concrete cards are chosen later with [com.kaynzhang.doudizhu.engine.model.CardSet.pick].
 *
 * Output order is deterministic: by type, then rank, then length, then kicker ranks.
 */
object MoveGenerator {

    /** All moves that may be led from [hand]. */
    fun leads(hand: Counts, out: MoveBuffer, mode: KickerMode = KickerMode.ALL) {
        val h = hand.packed
        singles(h, 0, out)
        pairs(h, 0, out)
        triples(h, 0, out)
        tripleSingles(h, 0, out, mode)
        triplePairs(h, 0, out, mode)
        straights(h, 0, 0, out)
        pairStraights(h, 0, 0, out)
        planes(h, 0, 0, out)
        planeSingles(h, 0, 0, out, mode)
        planePairs(h, 0, 0, out, mode)
        fourTwoSingles(h, 0, out, mode)
        fourTwoPairs(h, 0, out, mode)
        bombs(h, 0, out)
        rocket(h, out)
    }

    /** All moves from [hand] that beat [prev]: same type first (ascending), then bombs, then the rocket. */
    fun beating(hand: Counts, prev: Combo, out: MoveBuffer, mode: KickerMode = KickerMode.ALL) =
        beating(hand.packed, prev.key, out, mode)

    fun beating(h: Long, prevKey: Int, out: MoveBuffer, mode: KickerMode = KickerMode.ALL) {
        val type = Combo.keyType(prevKey)
        val min = Combo.keyRank(prevKey) + 1
        val len = Combo.keyLen(prevKey)
        when (type) {
            ComboType.ROCKET -> return
            ComboType.BOMB -> {
                bombs(h, min, out)
                rocket(h, out)
                return
            }
            ComboType.SINGLE -> singles(h, min, out)
            ComboType.PAIR -> pairs(h, min, out)
            ComboType.TRIPLE -> triples(h, min, out)
            ComboType.TRIPLE_SINGLE -> tripleSingles(h, min, out, mode)
            ComboType.TRIPLE_PAIR -> triplePairs(h, min, out, mode)
            ComboType.STRAIGHT -> straights(h, min, len, out)
            ComboType.PAIR_STRAIGHT -> pairStraights(h, min, len, out)
            ComboType.PLANE -> planes(h, min, len, out)
            ComboType.PLANE_SINGLES -> planeSingles(h, min, len, out, mode)
            ComboType.PLANE_PAIRS -> planePairs(h, min, len, out, mode)
            ComboType.FOUR_TWO_SINGLES -> fourTwoSingles(h, min, out, mode)
            ComboType.FOUR_TWO_PAIRS -> fourTwoPairs(h, min, out, mode)
        }
        bombs(h, 0, out)
        rocket(h, out)
    }

    fun canBeat(hand: Counts, prev: Combo): Boolean = canBeat(hand.packed, prev.key)

    fun canBeat(h: Long, prevKey: Int): Boolean {
        val buf = SCRATCH.get()
        buf.clear()
        buf.limit = 1
        beating(h, prevKey, buf, KickerMode.CHEAPEST)
        return buf.size > 0
    }

    fun leads(hand: Counts, mode: KickerMode = KickerMode.ALL): List<Move> =
        MoveBuffer().also { leads(hand, it, mode) }.toList()

    fun beating(hand: Counts, prev: Combo, mode: KickerMode = KickerMode.ALL): List<Move> =
        MoveBuffer().also { beating(hand, prev, it, mode) }.toList()

    private val SCRATCH = ThreadLocal.withInitial { MoveBuffer(8) }

    // ---- per-type generators; `min` is the lowest allowed main rank, `len` 0 means any ----

    private fun cnt(h: Long, r: Int): Int = ((h ushr (r shl 2)) and 0xF).toInt()
    private fun unit(r: Int, n: Int): Long = n.toLong() shl (r shl 2)

    private fun singles(h: Long, min: Int, out: MoveBuffer) {
        for (r in min until Rk.COUNT) {
            if (out.full) return
            if (cnt(h, r) >= 1) out.add(unit(r, 1), Combo.key(ComboType.SINGLE, r, 1))
        }
    }

    private fun pairs(h: Long, min: Int, out: MoveBuffer) {
        for (r in min..Rk.TWO) {
            if (out.full) return
            if (cnt(h, r) >= 2) out.add(unit(r, 2), Combo.key(ComboType.PAIR, r, 1))
        }
    }

    private fun triples(h: Long, min: Int, out: MoveBuffer) {
        for (r in min..Rk.TWO) {
            if (out.full) return
            if (cnt(h, r) >= 3) out.add(unit(r, 3), Combo.key(ComboType.TRIPLE, r, 1))
        }
    }

    private fun bombs(h: Long, min: Int, out: MoveBuffer) {
        for (r in min..Rk.TWO) {
            if (out.full) return
            if (cnt(h, r) == 4) out.add(unit(r, 4), Combo.key(ComboType.BOMB, r, 1))
        }
    }

    private fun rocket(h: Long, out: MoveBuffer) {
        if (!out.full && cnt(h, Rk.SJ) == 1 && cnt(h, Rk.BJ) == 1) {
            out.add(unit(Rk.SJ, 1) or unit(Rk.BJ, 1), Combo.key(ComboType.ROCKET, Rk.SJ, 1))
        }
    }

    private fun tripleSingles(h: Long, min: Int, out: MoveBuffer, mode: KickerMode) {
        for (r in min..Rk.TWO) {
            if (cnt(h, r) < 3) continue
            val main = unit(r, 3)
            val pool = h and (0xFL shl (r shl 2)).inv()
            val key = Combo.key(ComboType.TRIPLE_SINGLE, r, 1)
            if (mode == KickerMode.ALL) {
                for (q in 0 until Rk.COUNT) {
                    if (out.full) return
                    if (cnt(pool, q) >= 1) out.add(main + unit(q, 1), key)
                }
            } else {
                val k = cheapestSingles(pool, 1)
                if (k != NONE && !out.full) out.add(main + k, key)
            }
        }
    }

    private fun triplePairs(h: Long, min: Int, out: MoveBuffer, mode: KickerMode) {
        for (r in min..Rk.TWO) {
            if (cnt(h, r) < 3) continue
            val main = unit(r, 3)
            val pool = h and (0xFL shl (r shl 2)).inv()
            val key = Combo.key(ComboType.TRIPLE_PAIR, r, 1)
            if (mode == KickerMode.ALL) {
                for (q in 0..Rk.TWO) {
                    if (out.full) return
                    if (cnt(pool, q) >= 2) out.add(main + unit(q, 2), key)
                }
            } else {
                val k = cheapestPairs(pool, 1)
                if (k != NONE && !out.full) out.add(main + k, key)
            }
        }
    }

    /** Chains of [width] cards per link with at least [minLen] links. */
    private inline fun chains(
        h: Long, min: Int, len: Int, width: Int, minLen: Int,
        out: MoveBuffer, emit: (start: Int, links: Int, chain: Long) -> Unit,
    ) {
        for (a in min..Rk.MAX_CHAIN) {
            var chain = 0L
            var b = a
            while (b <= Rk.MAX_CHAIN && cnt(h, b) >= width) {
                chain += unit(b, width)
                val links = b - a + 1
                if (len != 0 && links > len) break
                if (links >= minLen && (len == 0 || links == len)) {
                    if (out.full) return
                    emit(a, links, chain)
                }
                b++
            }
        }
    }

    private fun straights(h: Long, min: Int, len: Int, out: MoveBuffer) =
        chains(h, min, len, 1, Combo.MIN_STRAIGHT, out) { a, links, chain ->
            out.add(chain, Combo.key(ComboType.STRAIGHT, a, links))
        }

    private fun pairStraights(h: Long, min: Int, len: Int, out: MoveBuffer) =
        chains(h, min, len, 2, Combo.MIN_PAIR_STRAIGHT, out) { a, links, chain ->
            if (2 * links <= MAX_HAND) out.add(chain, Combo.key(ComboType.PAIR_STRAIGHT, a, links))
        }

    private fun planes(h: Long, min: Int, len: Int, out: MoveBuffer) =
        chains(h, min, len, 3, Combo.MIN_PLANE, out) { a, links, chain ->
            if (3 * links <= MAX_HAND) out.add(chain, Combo.key(ComboType.PLANE, a, links))
        }

    private fun planeSingles(h: Long, min: Int, len: Int, out: MoveBuffer, mode: KickerMode) =
        chains(h, min, len, 3, Combo.MIN_PLANE, out) { a, links, chain ->
            if (4 * links <= MAX_HAND) {
                val pool = h and chainMask(a, links).inv()
                val key = Combo.key(ComboType.PLANE_SINGLES, a, links)
                if (mode == KickerMode.ALL) {
                    kickerSingles(pool, links, 0, 0L, chain, key, out)
                } else {
                    val k = cheapestSingles(pool, links)
                    if (k != NONE) out.add(chain + k, key)
                }
            }
        }

    private fun planePairs(h: Long, min: Int, len: Int, out: MoveBuffer, mode: KickerMode) =
        chains(h, min, len, 3, Combo.MIN_PLANE, out) { a, links, chain ->
            if (5 * links <= MAX_HAND) {
                val pool = h and chainMask(a, links).inv()
                val key = Combo.key(ComboType.PLANE_PAIRS, a, links)
                if (mode == KickerMode.ALL) {
                    kickerPairs(pool, links, 0, 0L, chain, key, out)
                } else {
                    val k = cheapestPairs(pool, links)
                    if (k != NONE) out.add(chain + k, key)
                }
            }
        }

    private fun fourTwoSingles(h: Long, min: Int, out: MoveBuffer, mode: KickerMode) {
        for (r in min..Rk.TWO) {
            if (cnt(h, r) != 4) continue
            val pool = h and (0xFL shl (r shl 2)).inv()
            val key = Combo.key(ComboType.FOUR_TWO_SINGLES, r, 1)
            if (mode == KickerMode.ALL) {
                kickerSingles(pool, 2, 0, 0L, unit(r, 4), key, out)
            } else {
                val k = cheapestSingles(pool, 2)
                if (k != NONE && !out.full) out.add(unit(r, 4) + k, key)
            }
            if (out.full) return
        }
    }

    private fun fourTwoPairs(h: Long, min: Int, out: MoveBuffer, mode: KickerMode) {
        for (r in min..Rk.TWO) {
            if (cnt(h, r) != 4) continue
            val pool = h and (0xFL shl (r shl 2)).inv()
            val key = Combo.key(ComboType.FOUR_TWO_PAIRS, r, 1)
            if (mode == KickerMode.ALL) {
                kickerPairs(pool, 2, 0, 0L, unit(r, 4), key, out)
            } else {
                val k = cheapestPairs(pool, 2)
                if (k != NONE && !out.full) out.add(unit(r, 4) + k, key)
            }
            if (out.full) return
        }
    }

    private fun chainMask(a: Int, links: Int): Long {
        var m = 0L
        for (r in a until a + links) m = m or (0xFL shl (r shl 2))
        return m
    }

    /** Every multiset of [need] single kickers from [pool] obeying K2/K3 (K1 is the caller's pool mask). */
    private fun kickerSingles(pool: Long, need: Int, from: Int, acc: Long, main: Long, key: Int, out: MoveBuffer) {
        if (need == 0) {
            out.add(main + acc, key)
            return
        }
        for (q in from until Rk.COUNT) {
            if (out.full) return
            val c = cnt(pool, q)
            if (c == 0) continue
            if (q == Rk.BJ && cnt(acc, Rk.SJ) == 1) continue // K2
            val max = minOf(c, need, 3) // K3
            for (t in 1..max) kickerSingles(pool, need - t, q + 1, acc + unit(q, t), main, key, out)
        }
    }

    /** Every set of [need] pairs of distinct ranks from [pool]. */
    private fun kickerPairs(pool: Long, need: Int, from: Int, acc: Long, main: Long, key: Int, out: MoveBuffer) {
        if (need == 0) {
            out.add(main + acc, key)
            return
        }
        for (q in from..Rk.TWO) {
            if (out.full) return
            if (cnt(pool, q) >= 2) kickerPairs(pool, need - 1, q + 1, acc + unit(q, 2), main, key, out)
        }
    }

    private const val NONE = -1L
    private const val MAX_HAND = 20

    /**
     * Greedy kicker choice: loose singles first, then cards from pairs, triples, fours;
     * 2s and jokers only as a last resort. Returns packed counts or [NONE].
     */
    private fun cheapestSingles(pool: Long, need: Int): Long {
        var acc = 0L
        var left = need
        for (cls in 1..8) {
            val size = if (cls > 4) cls - 4 else cls
            val big = cls > 4
            for (q in 0 until Rk.COUNT) {
                if (left == 0) return acc
                if ((q >= Rk.TWO) != big || cnt(pool, q) != size) continue
                if (q == Rk.BJ && cnt(acc, Rk.SJ) == 1) continue // K2
                val t = minOf(size, left, 3)
                acc += unit(q, t)
                left -= t
            }
        }
        return if (left == 0) acc else NONE
    }

    /** Greedy choice of [need] pairs of distinct ranks: exact pairs first, 2s last. */
    private fun cheapestPairs(pool: Long, need: Int): Long {
        var acc = 0L
        var left = need
        for (cls in 2..8) {
            val size = if (cls > 4) cls - 4 else cls
            if (size < 2) continue
            val big = cls > 4
            for (q in 0..Rk.TWO) {
                if (left == 0) return acc
                if ((q == Rk.TWO) != big || cnt(pool, q) != size) continue
                acc += unit(q, 2)
                left--
            }
        }
        return if (left == 0) acc else NONE
    }
}
