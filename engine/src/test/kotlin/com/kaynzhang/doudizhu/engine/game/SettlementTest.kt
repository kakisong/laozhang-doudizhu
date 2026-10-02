package com.kaynzhang.doudizhu.engine.game

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SettlementTest {

    private fun settle(coins: List<Long>, landlordWon: Boolean, robs: Int = 0, bombs: Int = 0, jiabei: List<Boolean> = listOf(false, false, false)) =
        Settlement.settle(
            GameConfig(100, coins), landlord = 0, winner = if (landlordWon) 0 else 1,
            robs = robs, bombs = bombs, spring = false, antiSpring = false, jiabei = jiabei,
        )

    @Test
    fun `uncapped settlement multiplies base, robs, bombs and jiabei`() {
        val r = settle(listOf(1_000_000, 1_000_000, 1_000_000), landlordWon = true, robs = 1, bombs = 2, jiabei = listOf(true, true, false))
        // common = 2^(1+2) = 8; landlord ×2; farmer 1 ×2
        assertEquals(8, r.commonMultiplier)
        assertEquals(listOf(4800L, 3200L, 1600L), r.raw)
        assertEquals(listOf(4800L, -3200L, -1600L), r.delta)
        assertFalse(r.capped)
        assertEquals(32, r.multiplierFor(1))
        assertEquals(16, r.multiplierFor(2))
        assertEquals(48, r.multiplierFor(0))
    }

    @Test
    fun `a farmer short of coins loses only what they have`() {
        val r = settle(listOf(1_000_000, 150, 1_000_000), landlordWon = true, bombs = 1)
        assertEquals(listOf(350L, -150L, -200L), r.delta)
        assertTrue(r.capped)
        assertEquals(0L, r.delta.sum())
    }

    @Test
    fun `a landlord short of coins pays farmers proportionally`() {
        val r = settle(listOf(300, 1_000_000, 1_000_000), landlordWon = false, bombs = 2, jiabei = listOf(false, true, false))
        // raw: farmer1 800, farmer2 400 -> scaled to 300 total: 200 and 100
        assertEquals(listOf(-300L, 200L, 100L), r.delta)
        assertTrue(r.capped)
    }

    @Test
    fun `both caps at once never make anybody negative`() {
        val r = settle(listOf(250, 100, 1_000), landlordWon = false, robs = 3, bombs = 3)
        assertEquals(0L, r.delta.sum())
        assertTrue(r.delta[0] >= -250)
        assertTrue(r.delta[1] <= 100 && r.delta[2] <= 1_000)
    }

    @Test
    fun `broke seats settle to zero`() {
        val r = settle(listOf(0, 500, 500), landlordWon = false)
        assertEquals(listOf(0L, 0L, 0L), r.delta)
    }
}
