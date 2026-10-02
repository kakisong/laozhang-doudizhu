package com.kaynzhang.doudizhu.ui.table

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Density
import kotlin.math.floor
import kotlin.math.min

/**
 * Pixel layout of the table, derived from the available size. Every card position on screen
 * (hand slots, play areas, seat anchors, the 底牌 row) comes from here, so sprites can animate
 * between any two places in one coordinate system.
 */
class TableGeometry(val w: Float, val h: Float, density: Density, hasCounter: Boolean) {
    private val px = density.density
    fun dp(v: Float): Float = v * px

    /** Landscape phones such as 1440×3168 @640dpi are only ~360dp tall: tighten the top rows there. */
    private val short = h < dp(400f)

    val margin = dp(10f)
    val topBarH = dp(if (short) 44f else 48f)
    val counterH = if (hasCounter) dp(if (short) 30f else 34f) else 0f

    val cardH = (h * 0.27f).coerceIn(dp(84f), dp(150f))
    val cardW = cardH * 0.7f
    val raise = cardH * 0.17f
    val handTop = h - cardH - dp(4f)

    val buttonsH = dp(46f)
    val buttonsTop = handTop - raise - buttonsH - dp(4f)

    val aiPlayTop = topBarH + counterH + dp(if (short) 8f else 10f)

    /** Top of the opponents' seat panels. */
    val seatTop = aiPlayTop - dp(4f)
    val tableScale = min(0.62f, (buttonsTop - dp(12f) - aiPlayTop) / (2f * cardH))
    val tableW = cardW * tableScale
    val tableH = cardH * tableScale
    val humanPlayTop = buttonsTop - tableH - dp(8f)

    val seatW = dp(96f)
    private val leftPlayLeft = margin + seatW + dp(12f)
    private val rightPlayRight = w - margin - seatW - dp(12f)

    val bottomScale = (topBarH - dp(8f)) / cardH
    val bottomW = cardW * bottomScale
    val bottomH = cardH * bottomScale

    /** Where freshly dealt cards come from. */
    val deck = Offset(w / 2 - cardW / 2, h * 0.3f)

    fun handStep(n: Int): Float = if (n <= 1) 0f else min(cardW * 0.56f, (w - 2 * margin - cardW) / (n - 1))

    private fun handLeft(n: Int): Float = (w - (cardW + handStep(n) * (n - 1))) / 2

    fun handSlot(i: Int, n: Int, selected: Boolean): Offset =
        Offset(handLeft(n) + i * handStep(n), handTop - if (selected) raise else 0f)

    /** Index of the hand card under x (each card owns its visible strip; the last one its full width), or -1. */
    fun handIndexAt(x: Float, n: Int): Int {
        if (n == 0) return -1
        val left = handLeft(n)
        val right = left + handStep(n) * (n - 1) + cardW
        if (x < left || x > right) return -1
        val step = handStep(n)
        if (step == 0f) return 0
        return floor((x - left) / step).toInt().coerceIn(0, n - 1)
    }

    /** Clamped variant used while dragging past either end of the hand. */
    fun handIndexClamped(x: Float, n: Int): Int {
        val left = handLeft(n)
        val right = left + handStep(n) * (n - 1) + cardW
        return handIndexAt(x.coerceIn(left, right - 1f), n)
    }

    private fun playStep(seat: Int, n: Int): Float {
        if (n <= 1) return 0f
        val room = if (seat == 0) w - 2 * leftPlayLeft else (w / 2 - leftPlayLeft - dp(16f))
        return min(tableW * 0.42f, (room - tableW) / (n - 1))
    }

    /** Top-left of the i-th of n cards shown in front of [seat] (already scaled by [tableScale]). */
    fun playSlot(seat: Int, i: Int, n: Int): Offset {
        val step = playStep(seat, n)
        val width = tableW + step * (n - 1)
        val left = when (seat) {
            0 -> (w - width) / 2
            1 -> rightPlayRight - width
            else -> leftPlayLeft
        }
        val top = if (seat == 0) humanPlayTop else aiPlayTop + dp(8f)
        return Offset(left + i * step, top)
    }

    /** Centre line of a seat's play area (for banners and bubbles). */
    fun playCenter(seat: Int): Offset = when (seat) {
        0 -> Offset(w / 2, humanPlayTop + tableH / 2)
        1 -> Offset(rightPlayRight - tableW * 1.5f, aiPlayTop + dp(8f) + tableH / 2)
        else -> Offset(leftPlayLeft + tableW * 1.5f, aiPlayTop + dp(8f) + tableH / 2)
    }

    /** Where an opponent's cards appear from (its avatar), in full-size card coordinates. */
    fun seatAnchor(seat: Int): Offset = when (seat) {
        1 -> Offset(w - margin - seatW / 2 - cardW / 2, aiPlayTop + dp(10f))
        2 -> Offset(margin + seatW / 2 - cardW / 2, aiPlayTop + dp(10f))
        else -> Offset(w / 2 - cardW / 2, h)
    }

    /** Top-left of the i-th 底牌 in the top bar. */
    fun bottomSlot(i: Int): Offset {
        val gap = dp(4f)
        val total = 3 * bottomW + 2 * gap
        return Offset(w / 2 - total / 2 + i * (bottomW + gap), dp(4f))
    }
}
