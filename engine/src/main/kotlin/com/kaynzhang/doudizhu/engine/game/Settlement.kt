package com.kaynzhang.doudizhu.engine.game

import java.math.BigInteger

/**
 * Coin settlement (docs/RULES.md §结算). Zero-sum, nobody goes below zero:
 * each farmer's stake is capped by that farmer's coins, and the two stakes are
 * scaled down together if the landlord cannot cover them.
 */
object Settlement {

    fun settle(
        config: GameConfig,
        landlord: Int,
        winner: Int,
        robs: Int,
        bombs: Int,
        spring: Boolean,
        antiSpring: Boolean,
        jiabei: List<Boolean>,
    ): GameResult {
        val landlordWon = winner == landlord
        val exponent = (robs + bombs + if (spring || antiSpring) 1 else 0).coerceAtMost(MAX_EXPONENT)
        val common = 1L shl exponent
        val landlordFactor = if (jiabei[landlord]) 2L else 1L
        val farmers = (0 until Seats.COUNT).filter { it != landlord }
        val coins = config.seatCoins

        val raw = LongArray(Seats.COUNT)
        val stake = LongArray(Seats.COUNT)
        for (f in farmers) {
            raw[f] = config.baseScore * common * landlordFactor * (if (jiabei[f]) 2L else 1L)
            stake[f] = minOf(raw[f], coins[f])
        }
        raw[landlord] = farmers.sumOf { raw[it] }

        val total = farmers.sumOf { stake[it] }
        if (total > coins[landlord]) {
            val cover = BigInteger.valueOf(coins[landlord])
            val sum = BigInteger.valueOf(total)
            for (f in farmers) stake[f] = BigInteger.valueOf(stake[f]).multiply(cover).divide(sum).toLong()
        }
        val capped = farmers.any { stake[it] != raw[it] }

        val delta = LongArray(Seats.COUNT)
        for (f in farmers) delta[f] = if (landlordWon) -stake[f] else stake[f]
        delta[landlord] = -farmers.sumOf { delta[it] }

        return GameResult(
            winner = winner,
            landlord = landlord,
            landlordWon = landlordWon,
            spring = spring,
            antiSpring = antiSpring,
            robs = robs,
            bombs = bombs,
            jiabei = jiabei,
            commonMultiplier = common,
            raw = raw.toList(),
            delta = delta.toList(),
            capped = capped,
        )
    }

    private const val MAX_EXPONENT = 40
}
