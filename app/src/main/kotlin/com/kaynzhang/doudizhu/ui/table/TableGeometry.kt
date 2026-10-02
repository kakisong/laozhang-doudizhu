package com.kaynzhang.doudizhu.ui.table

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Density
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Pixel layout of the table, derived from the available size. Every card position on screen
 * (hand slots, play areas, seat anchors, the 底牌 row) comes from here, so sprites can animate
 * between any two places in one coordinate system.
 *
 * Sized for older players on landscape phones that are only 320–360dp tall: the human's play
 * area doubles as the action-button row (the two are never needed at the same time), and the
 * height this saves goes to the cards on the table.
 */
class TableGeometry(val w: Float, val h: Float, density: Density, hasCounter: Boolean) {
    private val px = density.density
    fun dp(v: Float): Float = v * px

    /** Landscape phones such as 1440×3168 @640dpi are only ~360dp tall (~320dp with a larger display size). */
    private val short = h < dp(400f)

    val margin = dp(10f)
    val topBarH = dp(48f)
    val counterH = if (hasCounter) dp(36f) else 0f
    private val rowsTop = topBarH + counterH + dp(4f)

    /** Opponents sit in the top corners, beside the centred 记牌器 rather than below it. */
    val seatW = dp(96f)
    val seatTop = topBarH + dp(6f)
    val seatAvatar = if (short) 48 else 52
    val seatBottom = seatTop + dp(seatAvatar + 42f)

    /** Width of one 记牌器 column, narrow enough that the strip never reaches the seat panels. */
    val counterColW = ((w - dp(236f)) / 15f).coerceIn(dp(22f), dp(30f))

    // Capped by width too, so even a 20-card hand shows every corner index (a "10" needs ~0.42 of a card).
    val cardH = (h * 0.27f).coerceIn(dp(84f), dp(150f)).coerceAtMost((w - 2 * margin) / 6.3f)
    val cardW = cardH * 0.7f
    val raise = cardH * 0.2f
    val handTop = h - cardH - dp(4f)

    // Two rows share the space between the counter and the hand: the opponents' plays, then the
    // human's row (action buttons on the human's turn, the human's last play otherwise).
    private val humanRowBottom = handTop - raise - dp(4f)
    private val rowsSpace = humanRowBottom - rowsTop - dp(6f)
    val tableH = (if (rowsSpace >= dp(112f)) rowsSpace / 2 else rowsSpace - dp(56f)).coerceIn(cardH * 0.5f, cardH * 0.78f)
    val tableScale = tableH / cardH
    val tableW = cardW * tableScale
    val humanRowH = max(dp(56f), tableH)
    val humanRowTop = humanRowBottom - humanRowH
    val aiPlayTop = rowsTop + max(0f, rowsSpace - tableH - humanRowH) / 2

    /** Action buttons sit in three fixed slots so each kind of choice is always in the same place. */
    val humanPanelW = dp(120f)
    private val slotGap = dp(32f)
    private val slotsMinLeft = margin + humanPanelW + dp(12f)
    val buttonW = min(dp(124f), (w - slotsMinLeft - margin - 2 * slotGap) / 3)
    val buttonH = dp(56f)
    val buttonTop = humanRowTop + (humanRowH - buttonH) / 2
    private val slotsLeft = max((w - 3 * buttonW - 2 * slotGap) / 2, slotsMinLeft)

    /** Left edge of action slot [i]: 0 declines (不出, 不叫…), 1 is 提示, 2 confirms (出牌, 叫地主…). */
    fun slotX(i: Int): Float = slotsLeft + i * (buttonW + slotGap)

    /** The human's badge, left of the buttons; kept clear of the left opponent's panel (and its crown). */
    val humanPanelTop = max(seatBottom + dp(14f), humanRowTop + (humanRowH - dp(44f)) / 2)

    /** Vertical centre of the opponents' row, where table-wide messages and banners go. */
    val messageY = aiPlayTop + tableH / 2

    /** Top of the 托管 banner: it covers the lower part of the hand but leaves the corner indices visible. */
    val trusteeTop = max(handTop + cardW * 0.56f, h - dp(64f))

    private val leftPlayLeft = margin + seatW + dp(12f)
    private val rightPlayRight = w - margin - seatW - dp(12f)
    private val humanPlayLeft = margin + humanPanelW + dp(12f)

    val bottomH = topBarH - dp(6f)
    val bottomW = bottomH * 0.7f

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
        val room = if (seat == 0) w - 2 * humanPlayLeft else (w / 2 - leftPlayLeft - dp(8f))
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
        val top = if (seat == 0) humanRowTop + (humanRowH - tableH) / 2 else aiPlayTop
        return Offset(left + i * step, top)
    }

    /** Centre line of a seat's play area (for banners and bubbles). */
    fun playCenter(seat: Int): Offset = when (seat) {
        0 -> Offset(w / 2, humanRowTop + humanRowH / 2)
        1 -> Offset(rightPlayRight - tableW * 1.5f, aiPlayTop + tableH / 2)
        else -> Offset(leftPlayLeft + tableW * 1.5f, aiPlayTop + tableH / 2)
    }

    /** Where an opponent's cards appear from (its avatar), in table-card coordinates. */
    fun seatAnchor(seat: Int): Offset {
        val cy = seatTop + dp(seatAvatar / 2f) - tableH / 2
        return when (seat) {
            1 -> Offset(w - margin - seatW / 2 - tableW / 2, cy)
            2 -> Offset(margin + seatW / 2 - tableW / 2, cy)
            else -> Offset(w / 2 - cardW / 2, h)
        }
    }

    /** Top-left of the i-th 底牌 in the top bar. */
    fun bottomSlot(i: Int): Offset {
        val gap = dp(4f)
        val total = 3 * bottomW + 2 * gap
        return Offset(w / 2 - total / 2 + i * (bottomW + gap), dp(3f))
    }
}
