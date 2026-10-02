package com.kaynzhang.doudizhu.engine.model

import kotlinx.serialization.Serializable

/**
 * One physical card, identified by its bit index in a [CardSet].
 *
 * Regular cards use bit `4 * rank + suit` (rank 0..12, suit 0..3);
 * the small joker is bit 52 and the big joker bit 56, so every rank owns one nibble.
 */
@Serializable
@JvmInline
value class Card(val bit: Int) {
    val rank: Int get() = bit ushr 2
    val suit: Int get() = bit and 3
    val isJoker: Boolean get() = rank >= Rk.SJ

    /** Hearts and diamonds are red; the big joker is drawn red too. */
    val isRed: Boolean get() = if (isJoker) rank == Rk.BJ else suit == Suit.HEART || suit == Suit.DIAMOND

    override fun toString(): String =
        if (isJoker) Rk.label(rank) else Suit.symbol(suit) + Rk.label(rank)

    companion object {
        fun of(rank: Int, suit: Int = 0): Card {
            require(rank in 0 until Rk.COUNT) { "bad rank $rank" }
            require(if (rank >= Rk.SJ) suit == 0 else suit in 0..3) { "bad suit $suit for rank $rank" }
            return Card(rank * 4 + suit)
        }

        val SMALL_JOKER = Card(Rk.SJ * 4)
        val BIG_JOKER = Card(Rk.BJ * 4)
    }
}

object Suit {
    const val SPADE = 0
    const val HEART = 1
    const val CLUB = 2
    const val DIAMOND = 3

    fun symbol(suit: Int): String = when (suit) {
        SPADE -> "♠"
        HEART -> "♥"
        CLUB -> "♣"
        else -> "♦"
    }
}

/** An unordered set of physical cards stored as a 64-bit mask (see [Card] for the layout). */
@Serializable
@JvmInline
value class CardSet(val bits: Long) {

    val size: Int get() = java.lang.Long.bitCount(bits)
    val isEmpty: Boolean get() = bits == 0L

    operator fun contains(card: Card): Boolean = (bits ushr card.bit) and 1L != 0L
    operator fun plus(other: CardSet): CardSet = CardSet(bits or other.bits)
    operator fun plus(card: Card): CardSet = CardSet(bits or (1L shl card.bit))
    operator fun minus(other: CardSet): CardSet = CardSet(bits and other.bits.inv())
    operator fun minus(card: Card): CardSet = CardSet(bits and (1L shl card.bit).inv())

    fun containsAll(other: CardSet): Boolean = other.bits and bits.inv() == 0L
    fun intersect(other: CardSet): CardSet = CardSet(bits and other.bits)

    /** Per-rank counts via a lane-wise popcount of each nibble. */
    fun counts(): Counts {
        var x = bits
        x -= (x ushr 1) and 0x5555555555555555L
        x = (x and 0x3333333333333333L) + ((x ushr 2) and 0x3333333333333333L)
        return Counts(x)
    }

    /** Cards in ascending order (by rank, then suit). */
    fun cards(): List<Card> {
        val out = ArrayList<Card>(size)
        var x = bits
        while (x != 0L) {
            val bit = java.lang.Long.numberOfTrailingZeros(x)
            out.add(Card(bit))
            x = x and (x - 1)
        }
        return out
    }

    /** Cards of one rank. */
    fun ofRank(rank: Int): CardSet = CardSet(bits and (0xFL shl (rank shl 2)))

    /**
     * Picks concrete cards matching [counts] from this set, lowest suits first.
     * Throws if this set does not contain enough cards of some rank.
     */
    fun pick(counts: Counts): CardSet {
        var out = 0L
        for (r in 0 until Rk.COUNT) {
            var need = counts[r]
            if (need == 0) continue
            var lane = (bits ushr (r shl 2)) and 0xFL
            while (need > 0) {
                require(lane != 0L) { "not enough cards of rank ${Rk.label(r)} in $this for $counts" }
                val low = lane and -lane
                out = out or (low shl (r shl 2))
                lane = lane xor low
                need--
            }
        }
        return CardSet(out)
    }

    override fun toString(): String = cards().joinToString(" ", "[", "]")

    companion object {
        val EMPTY = CardSet(0L)

        /** All 54 cards. */
        val FULL_DECK = CardSet(0x0FFFFFFFFFFFFFL or (1L shl 52) or (1L shl 56))

        fun of(cards: Iterable<Card>): CardSet {
            var b = 0L
            for (c in cards) b = b or (1L shl c.bit)
            return CardSet(b)
        }

        /** Builds a set with the given per-rank counts from a full deck (lowest suits first). */
        fun fromCounts(counts: Counts): CardSet = FULL_DECK.pick(counts)
    }
}
