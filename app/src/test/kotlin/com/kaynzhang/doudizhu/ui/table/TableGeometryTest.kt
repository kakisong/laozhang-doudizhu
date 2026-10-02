package com.kaynzhang.doudizhu.ui.table

import androidx.compose.ui.unit.Density
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The table layout must not overlap anywhere on the screens older players actually use:
 * the vivo V2520A (792×360dp, 711×320dp with a larger display size), 16:9 budget phones,
 * 854×480 Android Go phones (569×320dp), cutout-trimmed widths, the emulator and tablets
 * (scaled to a virtual 853×533, or 760×572 for 4:3).
 * Density 1 makes every pixel value read as dp.
 */
class TableGeometryTest {

    private val sizes = listOf(
        792f to 360f, 746f to 360f, 640f to 360f,
        711f to 320f, 670f to 320f, 569f to 320f,
        914f to 411f, 863f to 411f,
        853f to 533f, 760f to 572f,
    )

    private fun forEachLayout(check: (name: String, g: TableGeometry) -> Unit) {
        for ((w, h) in sizes) {
            for (counter in listOf(true, false)) {
                check("${w.toInt()}×${h.toInt()} counter=$counter", TableGeometry(w, h, Density(1f), counter))
            }
        }
    }

    private fun assertAtLeast(name: String, what: String, actual: Float, min: Float) =
        assertTrue("$name: $what = $actual, expected ≥ $min", actual >= min - 0.01f)

    @Test
    fun counterStripClearsTheSeatPanels() = forEachLayout { name, g ->
        val counterLeft = g.w / 2 - (15 * g.counterColW + 12f) / 2
        assertAtLeast(name, "gap counter↔seat panel", counterLeft - (g.margin + g.seatW), 4f)
    }

    @Test
    fun rowsStackWithoutOverlap() = forEachLayout { name, g ->
        assertAtLeast(name, "gap counter↔opponents' row", g.aiPlayTop - (g.topBarH + g.counterH), 0f)
        assertAtLeast(name, "gap opponents' row↔human row", g.humanRowTop - (g.aiPlayTop + g.tableH), 4f)
        assertAtLeast(name, "gap buttons↔raised hand", g.handTop - g.raise - 2f - (g.buttonTop + g.buttonH), 0f)
        assertAtLeast(name, "button inside human row", g.buttonTop - g.humanRowTop, -1f)
        assertAtLeast(name, "底牌 inside top bar", g.topBarH - (g.bottomSlot(0).y + g.bottomH), 0f)
    }

    @Test
    fun humanPanelClearsSeatPanelButtonsAndHand() = forEachLayout { name, g ->
        // The landlord crown pokes 10dp above the human's avatar.
        assertAtLeast(name, "gap left seat panel↔human crown", g.humanPanelTop - 10f - g.seatBottom, 2f)
        assertAtLeast(name, "gap human panel↔left slot", g.slotX(0) - (g.margin + g.humanPanelW), 8f)
        assertAtLeast(name, "gap human panel↔raised hand", g.handTop - g.raise - 2f - (g.humanPanelTop + 44f), 0f)
    }

    @Test
    fun actionSlotsFitOnScreen() = forEachLayout { name, g ->
        assertAtLeast(name, "right margin after last slot", g.w - g.margin - (g.slotX(2) + g.buttonW), 0f)
        assertAtLeast(name, "gap between slots", g.slotX(1) - (g.slotX(0) + g.buttonW), 24f)
        assertAtLeast(name, "button width", g.buttonW, 112f)
    }

    @Test
    fun cardsStayReadable() = forEachLayout { name, g ->
        // Corner index is 0.45 of the card width; table cards are scaled by tableScale.
        assertAtLeast(name, "table card rank (dp)", 0.45f * g.tableW, 16f)
        // A "10" index needs about 0.42 of the card width in the visible strip of a 20-card hand.
        assertAtLeast(name, "hand strip for a 10", g.handStep(20), 0.42f * g.cardW)
        assertAtLeast(name, "托管 banner height", g.h - 4f - g.trusteeTop, 52f)
    }
}
