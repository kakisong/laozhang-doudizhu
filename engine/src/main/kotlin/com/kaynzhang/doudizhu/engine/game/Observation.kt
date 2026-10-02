package com.kaynzhang.doudizhu.engine.game

import com.kaynzhang.doudizhu.engine.model.CardSet
import com.kaynzhang.doudizhu.engine.rules.Combo

/**
 * Everything one seat is allowed to know. Bots decide from this alone, so a bot can
 * never peek at hidden cards, and a restored save can be continued without bot memory.
 */
data class Observation(
    val seat: Int,
    val phase: Phase,
    val hand: CardSet,
    val landlord: Int,
    /** The bottom cards, public once the landlord is known; null during bidding. */
    val bottom: CardSet?,
    val handSizes: List<Int>,
    val playedBy: List<CardSet>,
    val trickOwner: Int,
    val trick: Combo?,
    val log: List<PlayRecord>,
    val bidding: Bidding,
    /** Own 加倍 choice; other seats' choices stay null until everybody has decided. */
    val jiabei: List<Boolean?>,
    val bombs: Int,
    val baseScore: Long,
    val seq: Int,
) {
    /** Cards neither in this hand nor played yet (what a 记牌器 shows). */
    val unseen: CardSet
        get() {
            var bits = CardSet.FULL_DECK.bits and hand.bits.inv()
            for (p in playedBy) bits = bits and p.bits.inv()
            return CardSet(bits)
        }

    val isLeading: Boolean get() = trick == null
    val isLandlord: Boolean get() = seat == landlord

    fun isTeammate(other: Int): Boolean =
        other != seat && landlord >= 0 && seat != landlord && other != landlord

    fun isOpponent(other: Int): Boolean = other != seat && !isTeammate(other)

    /**
     * Bottom cards the landlord still holds. They were shown to everybody, so a
     * farmer knows exactly where these cards are.
     */
    val landlordKnownCards: CardSet
        get() = if (bottom == null || landlord < 0) CardSet.EMPTY else bottom - playedBy[landlord]
}
