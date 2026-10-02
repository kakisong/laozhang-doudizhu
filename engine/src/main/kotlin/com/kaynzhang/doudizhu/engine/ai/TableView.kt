package com.kaynzhang.doudizhu.engine.ai

import com.kaynzhang.doudizhu.engine.game.Observation
import com.kaynzhang.doudizhu.engine.game.Seats
import com.kaynzhang.doudizhu.engine.model.Counts
import com.kaynzhang.doudizhu.engine.rules.Combo
import com.kaynzhang.doudizhu.engine.rules.MoveGenerator

/** Derived facts about the table from one seat's point of view, shared by the policies. */
class TableView(val obs: Observation) {
    val me: Int = obs.seat
    val landlord: Int = obs.landlord
    val isLandlord: Boolean = me == landlord
    val hand: Counts = obs.hand.counts()

    /** Cards held by the other two seats together (the 记牌器 view). */
    val unseen: Counts = obs.unseen.counts()

    val next: Int = Seats.next(me)
    val prev: Int = Seats.prev(me)

    fun size(seat: Int): Int = obs.handSizes[seat]
    fun isTeammate(seat: Int): Boolean = obs.isTeammate(seat)
    fun isOpponent(seat: Int): Boolean = obs.isOpponent(seat)

    val teammate: Int = if (isLandlord) -1 else (0 until Seats.COUNT).first { it != me && it != landlord }

    /** Fewest cards held by any opponent. */
    val opponentMin: Int = (0 until Seats.COUNT).filter { isOpponent(it) }.minOf { size(it) }

    /** Farmer seated right after the landlord (地主下家): feeds its teammate. */
    val isFarmerAfterLandlord: Boolean = !isLandlord && Seats.prev(me) == landlord

    /** Farmer seated right before the landlord (地主上家): tops weak plays (顶牌). */
    val isFarmerBeforeLandlord: Boolean = !isLandlord && Seats.next(me) == landlord

    /** True if no combination of unseen cards can beat [combo] (bombs included). */
    fun isControl(combo: Combo): Boolean = !MoveGenerator.canBeat(unseen, combo)
}
