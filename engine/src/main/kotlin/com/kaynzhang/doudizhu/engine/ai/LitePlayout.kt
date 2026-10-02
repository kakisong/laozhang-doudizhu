package com.kaynzhang.doudizhu.engine.ai

import com.kaynzhang.doudizhu.engine.game.Seats
import com.kaynzhang.doudizhu.engine.model.Counts
import com.kaynzhang.doudizhu.engine.model.Rk
import com.kaynzhang.doudizhu.engine.rules.Combo
import com.kaynzhang.doudizhu.engine.rules.ComboClassifier
import com.kaynzhang.doudizhu.engine.rules.ComboType
import com.kaynzhang.doudizhu.engine.rules.KickerMode
import com.kaynzhang.doudizhu.engine.rules.Move
import com.kaynzhang.doudizhu.engine.rules.MoveBuffer
import com.kaynzhang.doudizhu.engine.rules.MoveGenerator

/**
 * Plays a sampled, fully known deal to the end with "NormalLite", a cheap cousin of
 * [NormalPolicy] used by [HardBot]'s playouts, and hands small endgames to [EndgameSolver].
 *
 * State is three packed [Counts] plus turn/trick; nothing here touches GameState. Every seat
 * sees the sampled hands of the others, which is what a determinized playout means.
 *
 * NormalLite leads like [NormalPolicy] (controls first once at most one loose combo is left,
 * feed a teammate about to go out, no singles/pairs an opponent with 1–2 cards could take,
 * otherwise the cheapest loose combo of the split). It follows with Normal's scoring rule on the
 * cheapest-kicker moves: split score after the move versus passing, the same pass adjustments,
 * overkill penalty and bomb gate; it blocks an opponent about to go out and never overtakes its
 * teammate unless that empties the hand.
 *
 * Not thread-safe: each search worker owns one.
 */
internal class LitePlayout(
    private val analyzer: HandAnalyzer,
    private val solver: EndgameSolver,
) {
    val hands = LongArray(Seats.COUNT)
    var landlord = 0

    /** Nodes the solver may spend per playout; 0 disables the solver. */
    var solverBudget = SOLVER_BUDGET

    private val buf = MoveBuffer(64)
    private val planHand = LongArray(Seats.COUNT) { -1L }
    private val planItems = arrayOfNulls<List<Move>>(Seats.COUNT)

    private var moveCounts = 0L
    private var moveKey = PASS

    /** Test hook: sees every move before it is applied (key [PASS] for a pass). */
    internal var observer: ((seat: Int, trickKey: Int, counts: Long, key: Int) -> Unit)? = null

    /** Counters for tests and tuning: playouts, moves played out, solver calls, solver answers. */
    internal var playouts = 0L
    internal var moves = 0L
    internal var solverCalls = 0L
    internal var solved = 0L

    /**
     * Plays out from ([turn0], [trickKey0], [owner0]) with the current [hands]; [trickKey0] is -1
     * when [turn0] leads freely. Returns true if the landlord's side wins. Mutates [hands].
     */
    fun run(turn0: Int, trickKey0: Int, owner0: Int): Boolean {
        var turn = turn0
        var trick = trickKey0
        var owner = owner0
        var solverTried = solverBudget <= 0
        var steps = 0
        playouts++
        while (true) {
            if (!solverTried && smallEndgame()) {
                // One attempt per playout: if the budget runs out now, it would again later.
                solverTried = true
                solverCalls++
                val r = solver.landlordWins(hands, landlord, turn, trick, owner, solverBudget)
                if (r != EndgameSolver.UNKNOWN) {
                    solved++
                    return r == EndgameSolver.WIN
                }
            }
            if (trick < 0) lead(turn) else follow(turn, trick, owner)
            moves++
            observer?.invoke(turn, trick, moveCounts, moveKey)
            val next = Seats.next(turn)
            if (moveKey == PASS) {
                if (next == owner) {
                    trick = -1
                    owner = -1
                }
            } else {
                hands[turn] -= moveCounts
                if (hands[turn] == 0L) return turn == landlord
                trick = moveKey
                owner = turn
            }
            turn = next
            check(++steps < MAX_STEPS) { "playout did not finish" }
        }
    }

    /** Resets cached plans; call whenever [hands] are replaced from outside. */
    fun resetCache() = planHand.fill(-1L)

    private fun smallEndgame(): Boolean {
        var total = 0
        for (h in hands) {
            val n = Counts(h).total
            if (n > SOLVER_MAX_HAND) return false
            total += n
        }
        return total <= SOLVER_MAX_TOTAL
    }

    // ---- leading ----

    private fun lead(seat: Int) {
        val hand = hands[seat]
        ComboClassifier.declareLead(Counts(hand))?.let { return play(hand, it.key) }

        // 地主下家 feeds a teammate that is about to go out.
        if (seat != landlord && Seats.prev(seat) == landlord) {
            val mateCards = Counts(hands[Seats.next(seat)]).total
            if (mateCards == 1) {
                val r = Counts(hand).lowestRank()
                return play(unit(r, 1), Combo.key(ComboType.SINGLE, r, 1))
            }
            if (mateCards == 2) {
                for (r in 0..Rk.TWO) if (cnt(hand, r) >= 2) return play(unit(r, 2), Combo.key(ComboType.PAIR, r, 1))
            }
        }

        val items = plan(seat)
        val others = othersOf(seat)
        var loose = 0
        var bestControl: Move? = null
        for (m in items) {
            if (!MoveGenerator.canBeat(others, m.combo.key)) {
                if (bestControl == null || controlOrder(m) < controlOrder(bestControl)) bestControl = m
            } else {
                loose++
            }
        }
        if (loose <= 1 && bestControl != null) return play(bestControl.counts.packed, bestControl.combo.key)

        val oppMin = opponentMin(seat)
        var best: Move? = null
        var bestCost = Double.MAX_VALUE
        var fallback: Move? = null
        for (m in items) {
            if (m.combo.isBomb || !MoveGenerator.canBeat(others, m.combo.key)) continue
            val dangerous = (m.combo.type == ComboType.SINGLE && oppMin == 1) || (m.combo.type == ComboType.PAIR && oppMin == 2)
            if (dangerous) {
                if (fallback == null || m.combo.rank > fallback.combo.rank) fallback = m
                continue
            }
            val cost = HandAnalyzer.value(m.combo) - 1.5 * (m.counts.total - 1)
            if (cost < bestCost) {
                bestCost = cost
                best = m
            }
        }
        val pick = best ?: fallback ?: items.firstOrNull { !it.combo.isBomb } ?: items.first()
        play(pick.counts.packed, pick.combo.key)
    }

    /** Controls in playing order: non-bombs before bombs, weaker first. */
    private fun controlOrder(m: Move): Int = (if (m.combo.isBomb) 1_000 else 0) + HandAnalyzer.value(m.combo)

    // ---- following ----

    private fun follow(seat: Int, trick: Int, owner: Int) {
        val hand = hands[seat]
        buf.clear()
        MoveGenerator.beating(hand, trick, buf, KickerMode.CHEAPEST)
        if (buf.size == 0) return pass()
        ComboClassifier.declareFollow(Counts(hand), Combo.fromKey(trick))?.let { return play(hand, it.key) }

        val mateOwns = (owner == landlord) == (seat == landlord)
        if (mateOwns) return pass()

        // Block: the next seat is an opponent that could go out on this single/pair.
        val next = Seats.next(seat)
        val trickType = Combo.keyTypeOrdinal(trick)
        if (isOpponent(seat, next)) {
            val nextCards = Counts(hands[next]).total
            val blockType = when {
                trickType == SINGLE_ORD && nextCards == 1 -> SINGLE_ORD
                trickType == PAIR_ORD && nextCards == 2 -> PAIR_ORD
                else -> -1
            }
            if (blockType >= 0) {
                var pick = -1
                for (i in 0 until buf.size) {
                    if (Combo.keyTypeOrdinal(buf.keys[i]) == blockType) pick = i // generated in ascending rank
                }
                if (pick >= 0) return play(buf.counts[pick], buf.keys[pick])
            }
        }

        val ownerCards = Counts(hands[owner]).total
        val isLandlord = seat == landlord
        val oppMin = opponentMin(seat)
        val landlordCards = Counts(hands[landlord]).total
        var bestScore = analyzer.score(Counts(hand)) + when {
            ownerCards <= 2 -> -25.0
            isLandlord -> -3.0
            else -> -1.0
        }
        var best = -1
        val others = othersOf(seat)
        for (i in 0 until buf.size) {
            val key = buf.keys[i]
            val rest = Counts(hand - buf.counts[i])
            val score = if (Combo.keyIsBomb(key)) {
                if (ownerCards > 4 && (isLandlord || landlordCards > 2)) continue
                analyzer.score(rest) + 15.0
            } else {
                var sc = analyzer.score(rest).toDouble()
                if ((trickType == SINGLE_ORD || trickType == PAIR_ORD) && oppMin > 5) {
                    sc -= 0.5 * maxOf(0, Combo.keyRank(key) - Combo.keyRank(trick) - 2)
                }
                if (!MoveGenerator.canBeat(others, key)) sc += 3.0
                sc
            }
            if (score > bestScore) {
                bestScore = score
                best = i
            }
        }
        if (best >= 0) play(buf.counts[best], buf.keys[best]) else pass()
    }

    // ---- helpers ----

    private fun plan(seat: Int): List<Move> {
        val hand = hands[seat]
        if (planHand[seat] != hand) {
            planItems[seat] = analyzer.plan(Counts(hand)).items
            planHand[seat] = hand
        }
        return planItems[seat]!!
    }

    /** Both other hands together: what "can anybody beat this" is checked against. */
    private fun othersOf(seat: Int): Long = hands[Seats.next(seat)] + hands[Seats.prev(seat)]

    private fun isOpponent(a: Int, b: Int): Boolean = (a == landlord) != (b == landlord)

    private fun opponentMin(seat: Int): Int {
        var min = Int.MAX_VALUE
        for (s in 0 until Seats.COUNT) if (isOpponent(seat, s)) min = minOf(min, Counts(hands[s]).total)
        return min
    }

    private fun play(counts: Long, key: Int) {
        moveCounts = counts
        moveKey = key
    }

    private fun pass() {
        moveCounts = 0L
        moveKey = PASS
    }

    companion object {
        const val PASS = -1
        const val SOLVER_BUDGET = 20_000
        const val SOLVER_MAX_TOTAL = 18
        const val SOLVER_MAX_HAND = 7
        private const val MAX_STEPS = 1_000

        private val SINGLE_ORD = ComboType.SINGLE.ordinal
        private val PAIR_ORD = ComboType.PAIR.ordinal

        private fun cnt(h: Long, r: Int): Int = ((h ushr (r shl 2)) and 0xF).toInt()
        private fun unit(r: Int, n: Int): Long = n.toLong() shl (r shl 2)
    }
}
