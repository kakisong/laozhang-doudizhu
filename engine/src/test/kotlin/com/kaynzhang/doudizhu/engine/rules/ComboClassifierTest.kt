package com.kaynzhang.doudizhu.engine.rules

import com.kaynzhang.doudizhu.engine.model.CardSet
import com.kaynzhang.doudizhu.engine.model.Counts
import com.kaynzhang.doudizhu.engine.model.Rk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Golden cases for the rules in docs/RULES.md. */
class ComboClassifierTest {

    private fun kinds(spec: String) = ComboClassifier.interpretations(Counts.parse(spec)).toSet()
    private fun lead(spec: String) = ComboClassifier.declareLead(Counts.parse(spec))
    private fun c(type: ComboType, rank: String, len: Int = 1) = Combo(type, Rk.parse(rank), len)

    @Test
    fun `counts helpers`() {
        val c = Counts.parse("33 444 小王大王")
        assertEquals(7, c.total)
        assertEquals(2, c[Rk.THREE])
        assertEquals(3, c[Rk.FOUR])
        assertEquals(Rk.THREE, c.lowestRank())
        assertEquals(Rk.BJ, c.highestRank())
        assertTrue(c.containsAll(Counts.parse("344大王")))
        assertFalse(c.containsAll(Counts.parse("333")))
        assertEquals(c, CardSet.fromCounts(c).counts())
        assertEquals(54, CardSet.FULL_DECK.size)
        assertEquals(54, CardSet.FULL_DECK.counts().total)
    }

    @Test
    fun `basic types`() {
        assertEquals(setOf(c(ComboType.SINGLE, "大王")), kinds("大王"))
        assertEquals(setOf(c(ComboType.PAIR, "2")), kinds("22"))
        assertEquals(setOf(c(ComboType.ROCKET, "小王")), kinds("小王大王"))
        assertEquals(setOf(c(ComboType.BOMB, "7")), kinds("7777"))
        assertEquals(setOf(c(ComboType.TRIPLE_SINGLE, "9")), kinds("999大王"))
        assertEquals(setOf(c(ComboType.TRIPLE_PAIR, "9")), kinds("99933"))
        assertEquals(setOf(c(ComboType.STRAIGHT, "10", 5)), kinds("10JQKA"))
        assertEquals(setOf(c(ComboType.PAIR_STRAIGHT, "3", 3)), kinds("334455"))
        assertEquals(setOf(c(ComboType.PLANE, "3", 2)), kinds("333444"))
        assertEquals(setOf(c(ComboType.PLANE_SINGLES, "3", 2)), kinds("333444+5+6"))
        assertEquals(setOf(c(ComboType.PLANE_PAIRS, "3", 2)), kinds("333444+55+66"))
        assertEquals(setOf(c(ComboType.FOUR_TWO_SINGLES, "5"), ), kinds("5555+3+8"))
        assertEquals(setOf(c(ComboType.FOUR_TWO_PAIRS, "4")), kinds("4444+55+77"))
    }

    @Test
    fun `2s and jokers never join chains`() {
        assertTrue(kinds("JQKA2").isEmpty())
        assertTrue(kinds("QQKKAA22").isEmpty())
        assertTrue(kinds("AAA222").isEmpty())
        assertTrue(kinds("KKKAAA+2+小王").isNotEmpty()) // plane K-A with 2 and a joker as wings
    }

    @Test
    fun `kicker rules K1 to K3`() {
        // K1: a chain rank's fourth card cannot be a wing; 33334444 is illegal outright.
        assertTrue(kinds("33334444").isEmpty())
        assertTrue(kinds("3334444+5").isEmpty())
        // K2: both jokers cannot be kickers together, one is fine.
        assertTrue(kinds("333444+小王大王").isEmpty())
        assertTrue(kinds("5555+小王大王").isEmpty())
        assertEquals(setOf(c(ComboType.PLANE_SINGLES, "3", 2)), kinds("333444+5+小王"))
        // K3: no four of a kind among the kickers.
        assertTrue(kinds("3333+5555").isEmpty())
        assertTrue(kinds("333444555666+7777").isEmpty())
        // Duplicated single wings are fine.
        assertEquals(setOf(c(ComboType.PLANE_SINGLES, "3", 2)), kinds("333444+55"))
        assertEquals(setOf(c(ComboType.PLANE_SINGLES, "3", 3)), kinds("333444555+777"))
        // 四带二 may take a pair as its two singles; 四带两对 needs two different pairs.
        assertEquals(setOf(c(ComboType.FOUR_TWO_SINGLES, "5")), kinds("5555+33"))
        assertTrue(kinds("4444+55+55").isEmpty())
    }

    @Test
    fun `ambiguous planes list every reading and lead with the strongest`() {
        assertEquals(
            setOf(c(ComboType.PLANE, "3", 4), c(ComboType.PLANE_SINGLES, "3", 3), c(ComboType.PLANE_SINGLES, "4", 3)),
            kinds("333444555666"),
        )
        assertEquals(c(ComboType.PLANE, "3", 4), lead("333444555666"))

        assertEquals(
            setOf(c(ComboType.PLANE_SINGLES, "3", 4), c(ComboType.PLANE_SINGLES, "4", 4)),
            kinds("333444555666777+8"),
        )
        assertEquals(c(ComboType.PLANE_SINGLES, "4", 4), lead("333444555666777+8"))

        // Following a 3-link plane with wings picks the reading that beats it.
        val prev = c(ComboType.PLANE_SINGLES, "3", 3)
        assertEquals(c(ComboType.PLANE_SINGLES, "5", 3), ComboClassifier.declareFollow(Counts.parse("444555666777"), prev))
        assertNull(ComboClassifier.declareFollow(Counts.parse("333444555+666"), c(ComboType.PLANE_SINGLES, "4", 3)))
    }

    @Test
    fun `comparisons`() {
        val single2 = c(ComboType.SINGLE, "2")
        assertTrue(c(ComboType.SINGLE, "小王").beats(single2))
        assertFalse(c(ComboType.PAIR, "A").beats(single2))
        assertTrue(c(ComboType.BOMB, "3").beats(c(ComboType.STRAIGHT, "10", 5)))
        assertTrue(c(ComboType.BOMB, "4").beats(c(ComboType.BOMB, "3")))
        assertFalse(c(ComboType.BOMB, "3").beats(c(ComboType.BOMB, "4")))
        assertTrue(c(ComboType.ROCKET, "小王").beats(c(ComboType.BOMB, "2")))
        assertFalse(c(ComboType.BOMB, "2").beats(c(ComboType.ROCKET, "小王")))
        // Chains only beat chains of the same length.
        assertFalse(c(ComboType.STRAIGHT, "4", 6).beats(c(ComboType.STRAIGHT, "3", 5)))
        assertTrue(c(ComboType.STRAIGHT, "4", 5).beats(c(ComboType.STRAIGHT, "3", 5)))
        // 四带二 is not a bomb.
        assertFalse(c(ComboType.FOUR_TWO_SINGLES, "2").beats(c(ComboType.TRIPLE_SINGLE, "3")))
        assertTrue(c(ComboType.BOMB, "3").beats(c(ComboType.FOUR_TWO_PAIRS, "2")))
    }

    @Test
    fun `hint order puts same-type moves first, then bombs, then the rocket`() {
        val moves = MoveGenerator.beating(Counts.parse("3 5 9 2222 小王 大王"), c(ComboType.SINGLE, "4"))
        val first = moves.first().combo
        assertEquals(c(ComboType.SINGLE, "5"), first)
        val types = moves.map { it.combo.type }
        assertTrue(types.indexOf(ComboType.BOMB) > types.lastIndexOf(ComboType.SINGLE))
        assertEquals(ComboType.ROCKET, types.last())
    }
}
