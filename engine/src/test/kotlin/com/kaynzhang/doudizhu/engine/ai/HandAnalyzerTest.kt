package com.kaynzhang.doudizhu.engine.ai

import com.kaynzhang.doudizhu.engine.model.CardSet
import com.kaynzhang.doudizhu.engine.model.Counts
import com.kaynzhang.doudizhu.engine.model.Rk
import com.kaynzhang.doudizhu.engine.model.Rng
import com.kaynzhang.doudizhu.engine.rules.Combo
import com.kaynzhang.doudizhu.engine.rules.ComboClassifier
import com.kaynzhang.doudizhu.engine.rules.ComboType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HandAnalyzerTest {

    private val analyzer = HandAnalyzer()

    private fun plan(spec: String) = analyzer.plan(Counts.parse(spec))
    private fun combos(spec: String) = plan(spec).items.map { it.combo }.toSet()

    @Test
    fun `plans are legal partitions whose score adds up`() {
        val rng = Rng(5)
        repeat(3_000) {
            val deck = CardSet.FULL_DECK.cards().toMutableList()
            rng.shuffle(deck)
            val hand = CardSet.of(deck.take(1 + rng.nextInt(20))).counts()
            val p = analyzer.plan(hand)
            var sum = Counts.EMPTY
            var score = 0
            for (item in p.items) {
                assertTrue(item.combo in ComboClassifier.interpretations(item.counts), "illegal plan item $item for $hand")
                sum += item.counts
                score += HandAnalyzer.value(item.combo) - HandAnalyzer.TURN_COST
            }
            assertEquals(hand, sum, "plan does not cover $hand: $p")
            assertEquals(p.score, score, "score mismatch for $hand: $p")
            assertEquals(p.score, analyzer.score(hand))
        }
    }

    @Test
    fun `a low triple takes a higher loose single as kicker`() {
        assertEquals(
            setOf(Combo(ComboType.TRIPLE_SINGLE, Rk.THREE), Combo(ComboType.SINGLE, Rk.JACK)),
            combos("333 7 J"),
        )
        assertEquals(Counts.parse("3337"), plan("333 7 J").items.first { it.combo.type == ComboType.TRIPLE_SINGLE }.counts)
    }

    @Test
    fun `a high triple takes a lower loose card`() {
        assertEquals(setOf(Combo(ComboType.TRIPLE_SINGLE, Rk.NINE)), combos("3 999"))
        assertEquals(setOf(Combo(ComboType.TRIPLE_PAIR, Rk.NINE)), combos("33 999"))
    }

    @Test
    fun `straights and planes are found`() {
        assertEquals(setOf(Combo(ComboType.STRAIGHT, Rk.THREE, 7)), combos("3456789"))
        assertEquals(setOf(Combo(ComboType.PLANE_SINGLES, Rk.FIVE, 2)), combos("555666 3 4"))
        assertTrue(Combo(ComboType.ROCKET, Rk.SJ) in combos("3 小王 大王"))
        assertTrue(Combo(ComboType.BOMB, Rk.SEVEN) in combos("7777 9"))
    }

    @Test
    fun `big cards are never used as kickers`() {
        val p = plan("333 2")
        assertEquals(setOf(Combo(ComboType.TRIPLE, Rk.THREE), Combo(ComboType.SINGLE, Rk.TWO)), p.items.map { it.combo }.toSet())
    }

    @Test
    fun `analysis is fast enough for search`() {
        val rng = Rng(9)
        val hands = List(2_000) {
            val deck = CardSet.FULL_DECK.cards().toMutableList()
            rng.shuffle(deck)
            CardSet.of(deck.take(20)).counts()
        }
        hands.take(200).forEach { HandAnalyzer().score(it) } // warm up
        val start = System.nanoTime()
        for (h in hands) HandAnalyzer().score(h) // cold memo each time: worst case
        val perHandMicros = (System.nanoTime() - start) / 1_000.0 / hands.size
        println("HandAnalyzer cold 20-card hand: %.1f µs".format(perHandMicros))
        assertTrue(perHandMicros < 5_000, "too slow: $perHandMicros µs per hand")
    }
}
