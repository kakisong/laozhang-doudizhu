package com.kaynzhang.doudizhu.engine.rules

import com.kaynzhang.doudizhu.engine.model.CardSet
import com.kaynzhang.doudizhu.engine.model.Counts
import com.kaynzhang.doudizhu.engine.model.Rk
import com.kaynzhang.doudizhu.engine.model.Rng
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/** Cross-checks [ComboClassifier] and [MoveGenerator] against the independent [RulesOracle]. */
class RulesOracleTest {

    private val universe = RulesOracle.universe

    @Test
    fun `classifier returns exactly the oracle declarations for every legal card set`() {
        assertTrue(universe.size > 25_000, "oracle looks too small: ${universe.size}")
        for ((packed, expected) in universe) {
            val actual = ComboClassifier.interpretations(Counts(packed)).toSet()
            if (actual != expected) fail("${Counts(packed)}: expected $expected, got $actual")
        }
    }

    @Test
    fun `classifier rejects card sets one card away from legal ones unless the oracle knows them`() {
        for (packed in universe.keys) {
            val c = Counts(packed)
            for (r in 0 until Rk.COUNT) {
                val cap = if (r >= Rk.SJ) 1 else 4
                if (c[r] < cap && c.total < RulesOracle.MAX_CARDS) check(c.plus(r, 1))
                if (c[r] > 0) check(c.minus(r, 1))
            }
        }
    }

    @Test
    fun `classifier rejects random card sets the oracle does not know`() {
        val rng = Rng(7)
        repeat(300_000) {
            val size = 1 + rng.nextInt(RulesOracle.MAX_CARDS)
            check(randomHand(rng, size).counts())
        }
    }

    @Test
    fun `lead generation matches the oracle for random and structured hands`() {
        val rng = Rng(11)
        val buf = MoveBuffer()
        for (hand in testHands(rng, 400)) {
            buf.clear()
            MoveGenerator.leads(hand, buf, KickerMode.ALL)
            val actual = (0 until buf.size).map { buf.counts[it] to buf.keys[it] }
            assertEquals(actual.size, actual.toSet().size, "duplicate moves for $hand")
            val expected = mutableSetOf<Pair<Long, Int>>()
            for ((packed, combos) in universe) {
                if (hand.containsAll(Counts(packed))) for (c in combos) expected.add(packed to c.key)
            }
            if (actual.toSet() != expected) {
                val missing = (expected - actual.toSet()).take(5).map { Move(Counts(it.first), Combo.fromKey(it.second)) }
                val extra = (actual.toSet() - expected).take(5).map { Move(Counts(it.first), Combo.fromKey(it.second)) }
                fail("hand $hand: missing $missing, extra $extra")
            }
        }
    }

    @Test
    fun `beating equals leads filtered by beats, and canBeat agrees`() {
        val rng = Rng(13)
        val all = universe.entries.flatMap { (p, cs) -> cs.map { Move(Counts(p), it) } }
        for (hand in testHands(rng, 300)) {
            val leads = MoveGenerator.leads(hand)
            repeat(25) {
                val prev = all[rng.nextInt(all.size)].combo
                val expected = leads.filter { it.combo.beats(prev) }.toSet()
                val actual = MoveGenerator.beating(hand, prev)
                assertEquals(expected, actual.toSet(), "hand $hand vs $prev")
                assertEquals(expected.isNotEmpty(), MoveGenerator.canBeat(hand, prev), "canBeat $hand vs $prev")
            }
        }
    }

    @Test
    fun `cheapest kicker mode yields one legal move per main part`() {
        val rng = Rng(17)
        for (hand in testHands(rng, 300)) {
            val all = MoveGenerator.leads(hand, KickerMode.ALL)
            val cheap = MoveGenerator.leads(hand, KickerMode.CHEAPEST)
            assertTrue(all.containsAll(cheap), "cheapest produced illegal moves for $hand")
            assertEquals(all.map { it.combo }.toSet(), cheap.map { it.combo }.toSet(), "main parts differ for $hand")
            assertEquals(cheap.size, cheap.map { it.combo }.toSet().size, "more than one kicker variant for $hand")
        }
    }

    private fun check(c: Counts) {
        val expected = universe[c.packed] ?: emptySet()
        val actual = ComboClassifier.interpretations(c).toSet()
        if (actual != expected) fail("$c: expected $expected, got $actual")
    }

    private fun randomHand(rng: Rng, size: Int): CardSet {
        val deck = CardSet.FULL_DECK.cards().toMutableList()
        rng.shuffle(deck)
        return CardSet.of(deck.take(size))
    }

    /** Random hands plus hands rich in triples, fours and chains, where the tricky cases live. */
    private fun testHands(rng: Rng, n: Int): List<Counts> {
        val out = mutableListOf<Counts>()
        repeat(n / 2) { out.add(randomHand(rng, 1 + rng.nextInt(RulesOracle.MAX_CARDS)).counts()) }
        repeat(n - n / 2) {
            val c = IntArray(Rk.COUNT)
            var total = 0
            val target = 8 + rng.nextInt(RulesOracle.MAX_CARDS - 7)
            while (total < target) {
                val r = rng.nextInt(Rk.COUNT)
                val cap = if (r >= Rk.SJ) 1 else 4
                val add = minOf(cap - c[r], 1 + rng.nextInt(4), target - total)
                if (add <= 0) continue
                c[r] += add
                total += add
            }
            out.add(Counts.fromArray(c))
        }
        return out
    }
}
