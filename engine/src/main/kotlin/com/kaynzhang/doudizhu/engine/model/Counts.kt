package com.kaynzhang.doudizhu.engine.model

/**
 * Number of cards per rank, packed 4 bits per rank (rank r lives in bits 4r..4r+3).
 *
 * Every lane holds 0..4, so lane-wise addition and subtraction on the raw Long are
 * safe as long as no lane over- or underflows; [containsAll] guards subtraction.
 */
@JvmInline
value class Counts(val packed: Long) {

    operator fun get(rank: Int): Int = ((packed ushr (rank shl 2)) and 0xF).toInt()

    val isEmpty: Boolean get() = packed == 0L

    /** Total number of cards. */
    val total: Int
        get() {
            // Fold nibbles into bytes, then sum the bytes with a multiply.
            val bytes = (packed and NIBBLE_LO) + ((packed ushr 4) and NIBBLE_LO)
            return ((bytes * BYTE_ONES) ushr 56).toInt()
        }

    /** True if every lane of this is >= the same lane of [other]. */
    fun containsAll(other: Counts): Boolean =
        (((packed or HIGH_BITS) - other.packed) and HIGH_BITS) == HIGH_BITS

    operator fun plus(other: Counts): Counts = Counts(packed + other.packed)

    /** Lane-wise difference; caller guarantees [containsAll]. */
    operator fun minus(other: Counts): Counts = Counts(packed - other.packed)

    fun plus(rank: Int, n: Int): Counts = Counts(packed + (n.toLong() shl (rank shl 2)))

    fun minus(rank: Int, n: Int): Counts = Counts(packed - (n.toLong() shl (rank shl 2)))

    fun with(rank: Int, n: Int): Counts {
        val shift = rank shl 2
        return Counts((packed and (0xFL shl shift).inv()) or (n.toLong() shl shift))
    }

    /** Lowest rank with a non-zero count, or -1 if empty. */
    fun lowestRank(): Int =
        if (packed == 0L) -1 else java.lang.Long.numberOfTrailingZeros(packed) ushr 2

    /** Highest rank with a non-zero count, or -1 if empty. */
    fun highestRank(): Int =
        if (packed == 0L) -1 else (63 - java.lang.Long.numberOfLeadingZeros(packed)) ushr 2

    /** Number of ranks with at least one card. */
    fun distinctRanks(): Int {
        var n = 0
        for (r in 0 until Rk.COUNT) if (get(r) > 0) n++
        return n
    }

    fun toIntArray(): IntArray = IntArray(Rk.COUNT) { get(it) }

    /** Compact human-readable form, e.g. "334455小王". */
    override fun toString(): String = buildString {
        for (r in 0 until Rk.COUNT) repeat(this@Counts[r]) { append(Rk.label(r)) }
        if (this@Counts.isEmpty) append("∅")
    }

    companion object {
        val EMPTY = Counts(0L)

        private const val NIBBLE_LO = 0x0F0F0F0F0F0F0F0FL
        private const val BYTE_ONES = 0x0101010101010101L
        private const val HIGH_BITS = -0x7777777777777778L // 0x8888888888888888

        fun of(vararg ranks: Int): Counts {
            var c = EMPTY
            for (r in ranks) c = c.plus(r, 1)
            return c
        }

        fun fromArray(counts: IntArray): Counts {
            var p = 0L
            for (r in 0 until Rk.COUNT) p = p or (counts[r].toLong() shl (r shl 2))
            return Counts(p)
        }

        /**
         * Parses a compact spec such as "333444+5+6", "10JQKA" or "小王大王".
         * Separators (space, '+', ',') are ignored; "T" is accepted for 10.
         */
        fun parse(spec: String): Counts {
            var c = EMPTY
            var i = 0
            while (i < spec.length) {
                val ch = spec[i]
                when {
                    ch == ' ' || ch == '+' || ch == ',' -> i++
                    spec.startsWith("10", i) -> { c = c.plus(Rk.TEN, 1); i += 2 }
                    spec.startsWith("小王", i) -> { c = c.plus(Rk.SJ, 1); i += 2 }
                    spec.startsWith("大王", i) -> { c = c.plus(Rk.BJ, 1); i += 2 }
                    spec.startsWith("SJ", i) -> { c = c.plus(Rk.SJ, 1); i += 2 }
                    spec.startsWith("BJ", i) -> { c = c.plus(Rk.BJ, 1); i += 2 }
                    else -> { c = c.plus(Rk.parse(ch.toString()), 1); i++ }
                }
            }
            return c
        }
    }
}
