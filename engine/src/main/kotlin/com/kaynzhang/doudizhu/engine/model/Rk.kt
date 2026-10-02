package com.kaynzhang.doudizhu.engine.model

/**
 * Rank indices, ordered by strength.
 *
 * 0..11 are 3..A and may appear in chains (straights, pair straights, planes);
 * 12 is the 2, 13 the small joker, 14 the big joker.
 */
object Rk {
    const val THREE = 0
    const val FOUR = 1
    const val FIVE = 2
    const val SIX = 3
    const val SEVEN = 4
    const val EIGHT = 5
    const val NINE = 6
    const val TEN = 7
    const val JACK = 8
    const val QUEEN = 9
    const val KING = 10
    const val ACE = 11
    const val TWO = 12
    const val SJ = 13
    const val BJ = 14

    /** Number of distinct ranks. */
    const val COUNT = 15

    /** Highest rank that may be part of a chain. */
    const val MAX_CHAIN = ACE

    private val LABELS = arrayOf("3", "4", "5", "6", "7", "8", "9", "10", "J", "Q", "K", "A", "2", "小王", "大王")

    /** Short face label, e.g. "10", "K", "小王". */
    fun label(rank: Int): String = LABELS[rank]

    /** Parses a face label ("3".."10", "J", "Q", "K", "A", "2", "小王"/"SJ", "大王"/"BJ"). */
    fun parse(label: String): Int = when (label.uppercase()) {
        "SJ", "小王" -> SJ
        "BJ", "大王" -> BJ
        "T" -> TEN
        else -> LABELS.indexOf(label.uppercase()).also { require(it >= 0) { "unknown rank: $label" } }
    }
}
