package com.kaynzhang.doudizhu.engine.ai

import com.kaynzhang.doudizhu.engine.game.Observation
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
 * The heuristic player behind the 普通 difficulty (and the candidate generator of 困难).
 *
 * Leading follows the hand's best split ([HandAnalyzer.plan]): win outright when the loose
 * combos are down to one, feed a teammate about to go out, avoid singles/pairs an opponent
 * with 1–2 cards could take, otherwise shed long low combos first.
 * Following compares the split score of the hand after each move with passing, adjusted for
 * who owns the trick, 顶牌, danger, overkill and a strict bomb gate.
 */
class NormalPolicy(private val analyzer: HandAnalyzer = HandAnalyzer()) {

    /** The play to make, or null to pass. */
    fun choose(obs: Observation): Move? = candidates(obs, 1).first()

    /**
     * Plays ordered from most to least preferred, at most [max] of them; null means pass.
     * The first entry is what [choose] plays.
     */
    fun candidates(obs: Observation, max: Int): List<Move?> {
        val v = TableView(obs)
        return if (obs.isLeading) leadCandidates(v, max) else followCandidates(v, max)
    }

    // ---- leading ----

    private fun leadCandidates(v: TableView, max: Int): List<Move?> {
        val hand = v.hand
        ComboClassifier.declareLead(hand)?.let { return listOf(Move(hand, it)) }

        val out = LinkedHashSet<Move>()
        val items = analyzer.plan(hand).items
        val isControl = BooleanArray(items.size) { v.isControl(items[it].combo) }
        val nonControls = isControl.count { !it }

        if (nonControls <= 1) {
            items.indices.filter { isControl[it] }
                .sortedWith(compareBy({ items[it].combo.isBomb }, { HandAnalyzer.value(items[it].combo) }))
                .forEach { out.add(items[it]) }
        }
        feed(v)?.let { out.add(it) }

        val loose = items.indices.filter { !isControl[it] && !items[it].combo.isBomb }.map { items[it] }
            .ifEmpty { items.filter { !it.combo.isBomb } }
            .ifEmpty { items }
        val (safe, risky) = loose.partition { !dangerous(v, it.combo) }
        safe.sortedBy { leadCost(it) }.forEach { out.add(it) }
        if (safe.isEmpty()) forcedLeads(v).forEach { out.add(it) }
        risky.sortedBy { leadCost(it) }.forEach { out.add(it) }
        items.forEach { out.add(it) }

        if (out.size < max) {
            // Alternatives outside the plan, one per type, for the searching bot to consider.
            val seen = out.map { it.combo.type }.toMutableSet()
            for (m in MoveGenerator.leads(hand, KickerMode.CHEAPEST)) {
                if (out.size >= max) break
                if (!m.combo.isBomb && seen.add(m.combo.type)) out.add(m)
            }
        }
        return out.take(max)
    }

    /** 地主下家 leads its smallest single/pair when the teammate is about to go out. */
    private fun feed(v: TableView): Move? {
        if (!v.isFarmerAfterLandlord) return null
        val hand = v.hand
        return when (v.size(v.teammate)) {
            1 -> hand.lowestRank().let { Move(Counts.of(it), Combo(ComboType.SINGLE, it)) }
            2 -> (0..Rk.TWO).firstOrNull { hand[it] >= 2 }?.let { Move(Counts.of(it, it), Combo(ComboType.PAIR, it)) }
            else -> null
        }
    }

    private fun dangerous(v: TableView, c: Combo): Boolean =
        ((c.type == ComboType.SINGLE && v.opponentMin == 1) || (c.type == ComboType.PAIR && v.opponentMin == 2)) &&
            !v.isControl(c)

    /** Every loose combo is a single/pair an opponent could take: pick the least bad lead. */
    private fun forcedLeads(v: TableView): List<Move> {
        val hand = v.hand
        val base = analyzer.score(hand)
        val all = MoveGenerator.leads(hand, KickerMode.CHEAPEST).filter { !it.combo.isBomb }
        val safe = all.filter { !dangerous(v, it.combo) }
        if (safe.isNotEmpty()) {
            return safe.sortedByDescending { analyzer.score(hand - it.counts) - base + it.counts.total }
        }
        // Nothing safe: lead the highest single (or pair), hoping it holds.
        return all.filter { it.combo.type == ComboType.SINGLE || it.combo.type == ComboType.PAIR }
            .sortedByDescending { it.combo.rank }
    }

    private fun leadCost(m: Move): Double = HandAnalyzer.value(m.combo) - 1.5 * (m.counts.total - 1)

    // ---- following ----

    private fun followCandidates(v: TableView, max: Int): List<Move?> {
        val obs = v.obs
        val prev = obs.trick!!
        val owner = obs.trickOwner
        val buf = MoveBuffer()
        MoveGenerator.beating(v.hand, prev, buf, KickerMode.ALL)
        if (buf.size == 0) return listOf(null)
        for (i in 0 until buf.size) if (buf.counts[i] == v.hand.packed) return listOf(buf.moveAt(i))

        val mateTrick = v.isTeammate(owner)
        if (mateTrick && v.size(owner) <= 2) return listOf(null) // teammate is going out; stay clear
        block(v, prev, buf)?.let { return listOf(it) }

        val base = analyzer.score(v.hand).toDouble()
        val scored = ArrayList<Pair<Move?, Double>>(buf.size + 1)
        scored.add(null to base + passAdjust(v, prev, owner, mateTrick))
        for (i in 0 until buf.size) {
            val m = buf.moveAt(i)
            val rest = v.hand - m.counts
            val score = if (m.combo.isBomb) {
                if (!bombAllowed(v, rest, owner, mateTrick)) continue
                analyzer.score(rest) + 15.0
            } else {
                analyzer.score(rest) - overkill(v, m.combo, prev) + if (v.isControl(m.combo)) 3.0 else 0.0
            }
            scored.add(m to score)
        }
        // Stable sort keeps generation order (smaller first) among equal scores.
        return scored.sortedByDescending { it.second }.take(max).map { it.first }
    }

    private fun passAdjust(v: TableView, prev: Combo, owner: Int, mateTrick: Boolean): Double = when {
        mateTrick -> if (shouldTop(v, prev, owner)) 0.0 else 10.0
        v.size(owner) <= 2 -> -25.0
        v.isLandlord -> -3.0
        else -> -1.0
    }

    /** 顶牌: 地主上家 raises a teammate's low single/pair so the landlord has to spend more. */
    private fun shouldTop(v: TableView, prev: Combo, owner: Int): Boolean =
        v.isFarmerBeforeLandlord && owner == v.teammate &&
            (prev.type == ComboType.SINGLE || prev.type == ComboType.PAIR) &&
            prev.rank < Rk.TEN && v.size(v.landlord) > 2

    /**
     * The next seat is an opponent that could go out on this single/pair: play the highest
     * single/pair that beats it, unless nothing unseen can beat the trick anyway.
     */
    private fun block(v: TableView, prev: Combo, buf: MoveBuffer): Move? {
        val nextCards = v.size(v.next)
        if (!v.isOpponent(v.next)) return null
        val type = when {
            prev.type == ComboType.SINGLE && nextCards == 1 -> ComboType.SINGLE
            prev.type == ComboType.PAIR && nextCards == 2 -> ComboType.PAIR
            else -> return null
        }
        if (v.isControl(prev)) return null
        var best: Move? = null
        for (i in 0 until buf.size) {
            val m = buf.moveAt(i)
            if (m.combo.type == type && (best == null || m.combo.rank > best.combo.rank)) best = m
        }
        return best
    }

    private fun bombAllowed(v: TableView, rest: Counts, owner: Int, mateTrick: Boolean): Boolean {
        if (mateTrick) return false
        if (v.isOpponent(owner) && v.size(owner) <= 4) return true
        if (!v.isLandlord && v.size(v.landlord) <= 2) return true
        val after = analyzer.plan(rest).items
        return after.count { !v.isControl(it.combo) } <= 1
    }

    private fun overkill(v: TableView, m: Combo, prev: Combo): Double =
        if ((prev.type == ComboType.SINGLE || prev.type == ComboType.PAIR) && v.opponentMin > 5) {
            0.5 * maxOf(0, m.rank - prev.rank - 2)
        } else {
            0.0
        }
}
