package com.kaynzhang.doudizhu.engine.ai

import com.kaynzhang.doudizhu.engine.game.Seats
import com.kaynzhang.doudizhu.engine.model.CardSet
import com.kaynzhang.doudizhu.engine.model.Counts
import com.kaynzhang.doudizhu.engine.model.Rng
import com.kaynzhang.doudizhu.engine.rules.Combo
import com.kaynzhang.doudizhu.engine.rules.ComboClassifier
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

class LitePlayoutTest {

    @Test
    fun `playouts from full deals are legal and finish`() {
        val rng = Rng(31)
        val playout = LitePlayout(HandAnalyzer(), EndgameSolver())
        var plays = 0
        var passes = 0
        var landlordWins = 0
        val games = 2_000
        repeat(games) { g ->
            val deck = CardSet.FULL_DECK.cards().toMutableList()
            rng.shuffle(deck)
            val landlord = rng.nextInt(Seats.COUNT)
            var pos = 0
            for (s in 0 until Seats.COUNT) {
                val n = if (s == landlord) 20 else 17
                playout.hands[s] = CardSet.of(deck.subList(pos, pos + n)).counts().packed
                pos += n
            }
            playout.landlord = landlord
            playout.resetCache()
            playout.solverBudget = if (g % 2 == 0) LitePlayout.SOLVER_BUDGET else 0
            val shadow = playout.hands.copyOf()
            playout.observer = { seat, trick, counts, key ->
                if (key == LitePlayout.PASS) {
                    if (trick < 0) fail("pass while leading")
                    passes++
                } else {
                    val combo = Combo.fromKey(key)
                    if (!Counts(shadow[seat]).containsAll(Counts(counts))) fail("seat $seat plays cards it does not hold")
                    if (combo !in ComboClassifier.interpretations(Counts(counts))) fail("illegal declaration $combo for ${Counts(counts)}")
                    if (trick >= 0 && !Combo.beats(key, trick)) fail("$combo does not beat ${Combo.fromKey(trick)}")
                    shadow[seat] -= counts
                    plays++
                }
            }
            if (playout.run(landlord, -1, -1)) landlordWins++
        }
        println("lite playouts: plays/game=%.1f passes/game=%.1f landlordWin=%.3f solved=%d".format(
            plays.toDouble() / games, passes.toDouble() / games, landlordWins.toDouble() / games, playout.solved))
        assertTrue(plays > games * 10)
    }
}
