package com.kaynzhang.doudizhu.engine.rules

import com.kaynzhang.doudizhu.engine.model.Counts
import com.kaynzhang.doudizhu.engine.model.Rk

/**
 * Decides which combos a set of cards can be declared as (see docs/RULES.md).
 *
 * Kicker constraints, shared with [MoveGenerator] through [Kickers]:
 *  - K1: a kicker never has the same rank as any rank of the main part;
 *  - K2: kickers never contain both jokers;
 *  - K3: kickers never contain four cards of one rank.
 */
object ComboClassifier {

    /** Every legal declaration of [c]; empty if the cards form no legal combo. */
    fun interpretations(c: Counts): List<Combo> {
        val n = c.total
        if (n == 0) return emptyList()
        val out = ArrayList<Combo>(2)
        if (n == 2 && c[Rk.SJ] == 1 && c[Rk.BJ] == 1) {
            out.add(Combo(ComboType.ROCKET, Rk.SJ))
            return out
        }
        val distinct = c.distinctRanks()
        val low = c.lowestRank()
        val high = c.highestRank()

        if (distinct == 1) {
            when (n) {
                1 -> out.add(Combo(ComboType.SINGLE, low))
                2 -> out.add(Combo(ComboType.PAIR, low))
                3 -> out.add(Combo(ComboType.TRIPLE, low))
                4 -> out.add(Combo(ComboType.BOMB, low))
            }
            return out
        }

        if (n == 4 || n == 5) {
            for (r in 0..Rk.TWO) {
                if (c[r] != 3) continue
                val rest = c.minus(r, 3)
                if (n == 4) out.add(Combo(ComboType.TRIPLE_SINGLE, r))
                else if (rest.distinctRanks() == 1 && rest[rest.lowestRank()] == 2) out.add(Combo(ComboType.TRIPLE_PAIR, r))
            }
        }

        if (n >= Combo.MIN_STRAIGHT && distinct == n && high <= Rk.MAX_CHAIN && high - low + 1 == n) {
            out.add(Combo(ComboType.STRAIGHT, low, n))
        }

        if (n >= 2 * Combo.MIN_PAIR_STRAIGHT && n % 2 == 0 && distinct == n / 2 &&
            high <= Rk.MAX_CHAIN && high - low + 1 == distinct && allCountsEqual(c, low, high, 2)
        ) {
            out.add(Combo(ComboType.PAIR_STRAIGHT, low, distinct))
        }

        if (n >= 3 * Combo.MIN_PLANE) addPlanes(c, n, out)

        if (n == 6 || n == 8) {
            for (r in 0..Rk.TWO) {
                if (c[r] != 4) continue
                val rest = c.minus(r, 4)
                if (n == 6 && Kickers.singlesOk(rest)) out.add(Combo(ComboType.FOUR_TWO_SINGLES, r))
                if (n == 8 && Kickers.isDistinctPairs(rest, 2)) out.add(Combo(ComboType.FOUR_TWO_PAIRS, r))
            }
        }
        return out
    }

    private fun addPlanes(c: Counts, n: Int, out: MutableList<Combo>) {
        for (a in 0 until Rk.MAX_CHAIN) {
            if (c[a] < 3) continue
            var rest = c.minus(a, 3)
            var b = a + 1
            while (b <= Rk.MAX_CHAIN && c[b] >= 3) {
                rest = rest.minus(b, 3)
                val len = b - a + 1
                if (3 * len > n) break
                val restCount = n - 3 * len
                if ((restCount == 0 || restCount == len || restCount == 2 * len) && !Kickers.touches(rest, a, b)) {
                    when (restCount) {
                        0 -> out.add(Combo(ComboType.PLANE, a, len))
                        len -> if (Kickers.singlesOk(rest)) out.add(Combo(ComboType.PLANE_SINGLES, a, len))
                        else -> if (Kickers.isDistinctPairs(rest, len)) out.add(Combo(ComboType.PLANE_PAIRS, a, len))
                    }
                }
                b++
            }
        }
    }

    private fun allCountsEqual(c: Counts, from: Int, to: Int, n: Int): Boolean {
        for (r in from..to) if (c[r] != n) return false
        return true
    }

    /**
     * The declaration used when [c] is led: most links first, then no kickers
     * over kickers, then the highest rank. So 333444555666 leads as a 4-link plane.
     */
    fun declareLead(c: Counts): Combo? = interpretations(c).maxWithOrNull(LEAD_PRIORITY)

    /** The declaration used to beat [prev], or null if [c] cannot beat it. */
    fun declareFollow(c: Counts, prev: Combo): Combo? =
        interpretations(c).filter { it.beats(prev) }.maxByOrNull { it.rank }

    private val LEAD_PRIORITY: Comparator<Combo> =
        compareBy<Combo> { it.len }.thenBy { if (it.type.hasKickers) 0 else 1 }.thenBy { it.rank }
}

/** The K1–K3 kicker predicates, shared by the classifier and the generator. */
internal object Kickers {
    /** K1: does [rest] contain any card whose rank lies in [from]..[to]? */
    fun touches(rest: Counts, from: Int, to: Int): Boolean {
        for (r in from..to) if (rest[r] != 0) return true
        return false
    }

    /** K2 and K3 for single-card kickers. */
    fun singlesOk(rest: Counts): Boolean {
        if (rest[Rk.SJ] == 1 && rest[Rk.BJ] == 1) return false
        for (r in 0..Rk.TWO) if (rest[r] == 4) return false
        return true
    }

    /** Exactly [pairs] pairs of distinct ranks and nothing else (jokers cannot pair). */
    fun isDistinctPairs(rest: Counts, pairs: Int): Boolean {
        var found = 0
        for (r in 0 until Rk.COUNT) {
            when (rest[r]) {
                0 -> {}
                2 -> found++
                else -> return false
            }
        }
        return found == pairs
    }
}
