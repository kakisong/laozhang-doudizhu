package com.kaynzhang.doudizhu.engine.model

/**
 * SplitMix64. Implemented locally so that deals and AI decisions stay bit-identical
 * across Kotlin/JDK versions, which keeps seeded tests and saved games reproducible.
 */
class Rng(seed: Long) {
    private var state = seed

    fun nextLong(): Long {
        state += GOLDEN
        return scramble(state)
    }

    /** Uniform in 0 until [bound]. */
    fun nextInt(bound: Int): Int {
        require(bound > 0)
        // Multiply-shift on 31 random bits; bias is below 2^-31 for our tiny bounds.
        return (((nextLong() ushr 33) * bound) ushr 31).toInt()
    }

    /** Uniform in [0, 1). */
    fun nextDouble(): Double = (nextLong() ushr 11) * (1.0 / (1L shl 53))

    fun nextBoolean(probability: Double): Boolean = nextDouble() < probability

    /** Fisher–Yates shuffle in place. */
    fun <T> shuffle(list: MutableList<T>) {
        for (i in list.size - 1 downTo 1) {
            val j = nextInt(i + 1)
            val t = list[i]
            list[i] = list[j]
            list[j] = t
        }
    }

    companion object {
        private const val GOLDEN = -0x61c8864680b583ebL // 0x9E3779B97F4A7C15

        private fun scramble(z0: Long): Long {
            var z = z0
            z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L // 0xBF58476D1CE4E5B9
            z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L // 0x94D049BB133111EB
            return z xor (z ushr 31)
        }

        /** Deterministically combines several values into one seed. */
        fun mix(vararg parts: Long): Long {
            var h = 0x1234_5678_9ABC_DEFL
            for (p in parts) h = scramble(h + GOLDEN + p)
            return h
        }
    }
}
