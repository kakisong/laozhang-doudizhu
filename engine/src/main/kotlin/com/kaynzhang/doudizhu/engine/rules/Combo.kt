package com.kaynzhang.doudizhu.engine.rules

import com.kaynzhang.doudizhu.engine.model.Rk
import kotlinx.serialization.Serializable

enum class ComboType(val zh: String, val hasKickers: Boolean = false) {
    SINGLE("单张"),
    PAIR("对子"),
    TRIPLE("三张"),
    TRIPLE_SINGLE("三带一", hasKickers = true),
    TRIPLE_PAIR("三带一对", hasKickers = true),
    STRAIGHT("顺子"),
    PAIR_STRAIGHT("连对"),
    PLANE("飞机"),
    PLANE_SINGLES("飞机带翅膀", hasKickers = true),
    PLANE_PAIRS("飞机带翅膀", hasKickers = true),
    FOUR_TWO_SINGLES("四带二", hasKickers = true),
    FOUR_TWO_PAIRS("四带两对", hasKickers = true),
    BOMB("炸弹"),
    ROCKET("王炸");

    companion object {
        private val VALUES = entries.toTypedArray()
        fun of(ordinal: Int): ComboType = VALUES[ordinal]
    }
}

/**
 * A declared card combination.
 *
 * [rank] is the lowest rank of the main part (the triple of 三带一, the four of 四带二,
 * the first link of a chain). [len] is the number of links for chains and 1 otherwise.
 * Kickers never take part in comparisons.
 */
@Serializable
data class Combo(val type: ComboType, val rank: Int, val len: Int = 1) {

    val isBomb: Boolean get() = type == ComboType.BOMB || type == ComboType.ROCKET

    val cardCount: Int get() = cardCount(type, len)

    /** Highest rank of the main part. */
    val topRank: Int get() = rank + len - 1

    /** Packed form used by hot search code: type(4 bits) | rank(4 bits) | len(4 bits). */
    val key: Int get() = key(type, rank, len)

    fun beats(prev: Combo): Boolean = beats(key, prev.key)

    override fun toString(): String = when (type) {
        ComboType.ROCKET -> type.zh
        ComboType.STRAIGHT, ComboType.PAIR_STRAIGHT, ComboType.PLANE,
        ComboType.PLANE_SINGLES, ComboType.PLANE_PAIRS ->
            "${type.zh}(${Rk.label(rank)}-${Rk.label(topRank)})"
        else -> "${type.zh}(${Rk.label(rank)})"
    }

    companion object {
        fun key(type: ComboType, rank: Int, len: Int): Int = (type.ordinal shl 8) or (rank shl 4) or len

        fun fromKey(key: Int): Combo = Combo(keyType(key), keyRank(key), keyLen(key))

        fun keyType(key: Int): ComboType = ComboType.of(key ushr 8)
        fun keyTypeOrdinal(key: Int): Int = key ushr 8
        fun keyRank(key: Int): Int = (key ushr 4) and 0xF
        fun keyLen(key: Int): Int = key and 0xF

        private val BOMB_ORD = ComboType.BOMB.ordinal
        private val ROCKET_ORD = ComboType.ROCKET.ordinal

        fun keyIsBomb(key: Int): Boolean {
            val t = key ushr 8
            return t == BOMB_ORD || t == ROCKET_ORD
        }

        /** True if a play with [key] may be played on top of [prevKey]. */
        fun beats(key: Int, prevKey: Int): Boolean {
            val t = key ushr 8
            val pt = prevKey ushr 8
            return when {
                pt == ROCKET_ORD -> false
                t == ROCKET_ORD -> true
                t == BOMB_ORD -> pt != BOMB_ORD || keyRank(key) > keyRank(prevKey)
                pt == BOMB_ORD -> false
                // Same type and link count; the rank lives above the len bits.
                else -> t == pt && keyLen(key) == keyLen(prevKey) && keyRank(key) > keyRank(prevKey)
            }
        }

        fun cardCount(type: ComboType, len: Int): Int = when (type) {
            ComboType.SINGLE -> 1
            ComboType.PAIR -> 2
            ComboType.TRIPLE -> 3
            ComboType.TRIPLE_SINGLE -> 4
            ComboType.TRIPLE_PAIR -> 5
            ComboType.STRAIGHT -> len
            ComboType.PAIR_STRAIGHT -> 2 * len
            ComboType.PLANE -> 3 * len
            ComboType.PLANE_SINGLES -> 4 * len
            ComboType.PLANE_PAIRS -> 5 * len
            ComboType.FOUR_TWO_SINGLES -> 6
            ComboType.FOUR_TWO_PAIRS -> 8
            ComboType.BOMB -> 4
            ComboType.ROCKET -> 2
        }

        /** Minimum chain lengths. */
        const val MIN_STRAIGHT = 5
        const val MIN_PAIR_STRAIGHT = 3
        const val MIN_PLANE = 2
    }
}

/** A concrete move at the counts level: the cards used and the declared combo. */
data class Move(val counts: com.kaynzhang.doudizhu.engine.model.Counts, val combo: Combo) {
    override fun toString(): String = "$combo $counts"
}
