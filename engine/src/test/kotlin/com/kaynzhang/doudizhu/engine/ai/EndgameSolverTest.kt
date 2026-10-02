package com.kaynzhang.doudizhu.engine.ai

import com.kaynzhang.doudizhu.engine.game.Seats
import com.kaynzhang.doudizhu.engine.model.CardSet
import com.kaynzhang.doudizhu.engine.model.Counts
import com.kaynzhang.doudizhu.engine.model.Rng
import com.kaynzhang.doudizhu.engine.rules.Combo
import com.kaynzhang.doudizhu.engine.rules.MoveGenerator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EndgameSolverTest {

    /** Plain minimax without table or ordering: the reference the solver must agree with. */
    private fun naive(hands: LongArray, landlord: Int, turn: Int, trick: Int, owner: Int): Boolean {
        val hand = hands[turn]
        val isLandlord = turn == landlord
        val moves = if (trick < 0) MoveGenerator.leads(Counts(hand)) else MoveGenerator.beating(Counts(hand), Combo.fromKey(trick))
        for (m in moves) {
            val rest = hand - m.counts.packed
            val landlordWins = if (rest == 0L) {
                isLandlord
            } else {
                hands[turn] = rest
                val r = naive(hands, landlord, Seats.next(turn), m.combo.key, turn)
                hands[turn] = hand
                r
            }
            if (landlordWins == isLandlord) return isLandlord
        }
        if (trick >= 0) {
            val next = Seats.next(turn)
            val r = if (next == owner) naive(hands, landlord, next, -1, -1) else naive(hands, landlord, next, trick, owner)
            if (r == isLandlord) return isLandlord
        }
        return !isLandlord
    }

    private class Position(val hands: LongArray, val landlord: Int, val turn: Int, val trick: Int, val owner: Int)

    private fun randomPosition(rng: Rng, maxCards: Int): Position {
        val deck = CardSet.FULL_DECK.cards().toMutableList()
        rng.shuffle(deck)
        var pos = 0
        val hands = LongArray(Seats.COUNT) {
            val n = 1 + rng.nextInt(maxCards)
            CardSet.of(deck.subList(pos, pos + n)).counts().packed.also { pos += n }
        }
        val landlord = rng.nextInt(Seats.COUNT)
        val turn = rng.nextInt(Seats.COUNT)
        if (rng.nextBoolean(0.4)) return Position(hands, landlord, turn, -1, -1)
        // A trick someone else played: any legal combo made from a few spare cards.
        val spare = CardSet.of(deck.subList(pos, pos + 1 + rng.nextInt(6))).counts()
        val leads = MoveGenerator.leads(spare)
        val trick = leads[rng.nextInt(leads.size)].combo.key
        val owner = (turn + 1 + rng.nextInt(2)) % Seats.COUNT
        return Position(hands, landlord, turn, trick, owner)
    }

    @Test
    fun `solver agrees with plain minimax on random small endgames`() {
        val rng = Rng(21)
        val solver = EndgameSolver(tableBits = 16)
        var wins = 0
        repeat(6_000) {
            val p = randomPosition(rng, maxCards = 5)
            val expected = naive(p.hands.copyOf(), p.landlord, p.turn, p.trick, p.owner)
            val actual = solver.landlordWins(p.hands, p.landlord, p.turn, p.trick, p.owner, nodeBudget = 50_000_000)
            assertTrue(actual != EndgameSolver.UNKNOWN)
            assertEquals(if (expected) EndgameSolver.WIN else EndgameSolver.LOSS, actual, "position #$it")
            if (actual == EndgameSolver.WIN) wins++
        }
        // Both outcomes must be well represented for the comparison to mean anything.
        assertTrue(wins in 600..5_400, "wins=$wins")
    }

    @Test
    fun `cached results do not change answers and the budget reports unknown`() {
        val rng = Rng(22)
        val positions = List(500) { randomPosition(rng, maxCards = 6) }
        val cold = positions.map { EndgameSolver(12).landlordWins(it.hands, it.landlord, it.turn, it.trick, it.owner, 10_000_000) }
        val warm = EndgameSolver(12)
        repeat(2) { round ->
            positions.forEachIndexed { i, p ->
                assertEquals(cold[i], warm.landlordWins(p.hands, p.landlord, p.turn, p.trick, p.owner, 10_000_000), "round $round #$i")
            }
        }
        val big = randomPosition(Rng(3), maxCards = 9)
        val tiny = EndgameSolver().landlordWins(big.hands, big.landlord, big.turn, big.trick, big.owner, nodeBudget = 3)
        assertEquals(EndgameSolver.UNKNOWN, tiny)
    }

    @Test
    fun `classic endgame - the landlord's rocket and a single win against two singles`() {
        // Landlord (seat 0) holds 小王大王 + 3 and leads; farmers hold one 2 each.
        val hands = longArrayOf(Counts.parse("3 小王大王").packed, Counts.parse("2").packed, Counts.parse("2").packed)
        assertEquals(EndgameSolver.WIN, EndgameSolver().landlordWins(hands, 0, 0, -1, -1, 1_000_000))
        // With the lead going to the farmers instead, the first farmer just plays its 2 and wins.
        assertEquals(EndgameSolver.LOSS, EndgameSolver().landlordWins(hands, 0, 1, -1, -1, 1_000_000))
    }

    @Test
    fun `farmers cooperate - one farmer passes so its teammate can finish`() {
        // Seat 2 (landlord) played a single 5; seat 0 could beat it, but only seat 1 can finish.
        val hands = longArrayOf(Counts.parse("9 10").packed, Counts.parse("A").packed, Counts.parse("K K 3").packed)
        val r = EndgameSolver().landlordWins(hands, landlord = 2, turn = 0, trickKey = Combo(com.kaynzhang.doudizhu.engine.rules.ComboType.SINGLE, com.kaynzhang.doudizhu.engine.model.Rk.FIVE).key, owner = 2, nodeBudget = 1_000_000)
        assertEquals(EndgameSolver.LOSS, r)
    }
}
