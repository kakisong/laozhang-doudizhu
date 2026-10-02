package com.kaynzhang.doudizhu.engine.rules

import com.kaynzhang.doudizhu.engine.model.Counts
import com.kaynzhang.doudizhu.engine.model.Rk

/**
 * An independent rules oracle: every legal (cards, declaration) pair with at most 20 cards,
 * generated straight from the combo table in docs/RULES.md without using any production code
 * except the [Combo]/[Counts] value types.
 */
object RulesOracle {

    const val MAX_CARDS = 20

    /** counts.packed → every legal declaration of exactly those cards. */
    val universe: Map<Long, Set<Combo>> by lazy { build() }

    private fun build(): Map<Long, Set<Combo>> {
        val m = HashMap<Long, MutableSet<Combo>>()
        fun add(c: IntArray, combo: Combo) {
            if (c.sum() > MAX_CARDS) return
            m.getOrPut(Counts.fromArray(c).packed) { mutableSetOf() }.add(combo)
        }
        fun base() = IntArray(Rk.COUNT)

        for (r in 0 until Rk.COUNT) add(base().also { it[r] = 1 }, Combo(ComboType.SINGLE, r))
        for (r in 0..Rk.TWO) {
            add(base().also { it[r] = 2 }, Combo(ComboType.PAIR, r))
            add(base().also { it[r] = 3 }, Combo(ComboType.TRIPLE, r))
            add(base().also { it[r] = 4 }, Combo(ComboType.BOMB, r))
            for (q in 0 until Rk.COUNT) {
                if (q == r) continue
                add(base().also { it[r] = 3; it[q] = 1 }, Combo(ComboType.TRIPLE_SINGLE, r))
                if (q <= Rk.TWO) add(base().also { it[r] = 3; it[q] = 2 }, Combo(ComboType.TRIPLE_PAIR, r))
            }
        }
        add(base().also { it[Rk.SJ] = 1; it[Rk.BJ] = 1 }, Combo(ComboType.ROCKET, Rk.SJ))

        for (a in 0..Rk.ACE) {
            for (len in 1..(Rk.ACE - a + 1)) {
                val chainRanks = (a until a + len).toSet()
                fun chain(width: Int) = base().also { c -> for (r in chainRanks) c[r] = width }
                if (len >= 5) add(chain(1), Combo(ComboType.STRAIGHT, a, len))
                if (len >= 3) add(chain(2), Combo(ComboType.PAIR_STRAIGHT, a, len))
                if (len >= 2) {
                    add(chain(3), Combo(ComboType.PLANE, a, len))
                    val kickerRanks = (0 until Rk.COUNT).filter { it !in chainRanks }
                    for (k in singleKickerMultisets(kickerRanks, len)) {
                        add(chain(3).also { c -> for (r in k.indices) c[r] += k[r] }, Combo(ComboType.PLANE_SINGLES, a, len))
                    }
                    for (pairs in subsets(kickerRanks.filter { it <= Rk.TWO }, len)) {
                        add(chain(3).also { c -> for (r in pairs) c[r] += 2 }, Combo(ComboType.PLANE_PAIRS, a, len))
                    }
                }
            }
        }

        for (r in 0..Rk.TWO) {
            val others = (0 until Rk.COUNT).filter { it != r }
            for (k in singleKickerMultisets(others, 2)) {
                add(base().also { c -> c[r] = 4; for (q in k.indices) c[q] += k[q] }, Combo(ComboType.FOUR_TWO_SINGLES, r))
            }
            for (pairs in subsets(others.filter { it <= Rk.TWO }, 2)) {
                add(base().also { c -> c[r] = 4; for (q in pairs) c[q] += 2 }, Combo(ComboType.FOUR_TWO_PAIRS, r))
            }
        }
        return m
    }

    /** Multisets of [size] single kickers: ≤3 per rank (K3), ≤1 per joker, never both jokers (K2). */
    private fun singleKickerMultisets(ranks: List<Int>, size: Int): List<IntArray> {
        val out = mutableListOf<IntArray>()
        fun rec(i: Int, left: Int, acc: IntArray) {
            if (left == 0) {
                if (!(acc[Rk.SJ] == 1 && acc[Rk.BJ] == 1)) out.add(acc.copyOf())
                return
            }
            if (i == ranks.size) return
            val r = ranks[i]
            val cap = if (r >= Rk.SJ) 1 else 3
            for (t in 0..minOf(cap, left)) {
                acc[r] += t
                rec(i + 1, left - t, acc)
                acc[r] -= t
            }
        }
        rec(0, size, IntArray(Rk.COUNT))
        return out
    }

    private fun subsets(items: List<Int>, size: Int): List<List<Int>> {
        if (size == 0) return listOf(emptyList())
        if (items.size < size) return emptyList()
        val head = items.first()
        val tail = items.drop(1)
        return subsets(tail, size - 1).map { listOf(head) + it } + subsets(tail, size)
    }
}
