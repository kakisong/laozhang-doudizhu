package com.kaynzhang.doudizhu.ui.card

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import com.kaynzhang.doudizhu.engine.model.Suit

/**
 * Suit shapes drawn as paths in a 100×100 box. Drawing them ourselves avoids the ♠♥♣♦ glyphs,
 * which some vendor fonts render as colour emoji.
 */
object Suits {
    private val spade = Path().apply {
        moveTo(50f, 5f)
        cubicTo(62f, 25f, 92f, 40f, 92f, 62f)
        cubicTo(92f, 77f, 81f, 86f, 69f, 86f)
        cubicTo(61f, 86f, 55f, 82f, 52f, 77f)
        cubicTo(53f, 85f, 56f, 91f, 62f, 95f)
        lineTo(38f, 95f)
        cubicTo(44f, 91f, 47f, 85f, 48f, 77f)
        cubicTo(45f, 82f, 39f, 86f, 31f, 86f)
        cubicTo(19f, 86f, 8f, 77f, 8f, 62f)
        cubicTo(8f, 40f, 38f, 25f, 50f, 5f)
        close()
    }

    private val heart = Path().apply {
        moveTo(50f, 92f)
        cubicTo(22f, 68f, 5f, 52f, 5f, 33f)
        cubicTo(5f, 18f, 17f, 7f, 30f, 7f)
        cubicTo(39f, 7f, 46f, 12f, 50f, 20f)
        cubicTo(54f, 12f, 61f, 7f, 70f, 7f)
        cubicTo(83f, 7f, 95f, 18f, 95f, 33f)
        cubicTo(95f, 52f, 78f, 68f, 50f, 92f)
        close()
    }

    private val diamond = Path().apply {
        moveTo(50f, 4f)
        quadraticTo(66f, 30f, 86f, 50f)
        quadraticTo(66f, 70f, 50f, 96f)
        quadraticTo(34f, 70f, 14f, 50f)
        quadraticTo(34f, 30f, 50f, 4f)
        close()
    }

    private val club = Path().apply {
        addOval(Rect(Offset(50f, 27f), 20f))
        addOval(Rect(Offset(27f, 57f), 20f))
        addOval(Rect(Offset(73f, 57f), 20f))
        moveTo(46f, 50f)
        cubicTo(46f, 75f, 42f, 87f, 33f, 96f)
        lineTo(67f, 96f)
        cubicTo(58f, 87f, 54f, 75f, 54f, 50f)
        close()
    }

    private val star = Path().apply {
        for (i in 0 until 10) {
            val r = if (i % 2 == 0) 48f else 20f
            val a = Math.toRadians(-90.0 + i * 36.0)
            val x = 50f + r * kotlin.math.cos(a).toFloat()
            val y = 52f + r * kotlin.math.sin(a).toFloat()
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }

    fun path(suit: Int): Path = when (suit) {
        Suit.SPADE -> spade
        Suit.HEART -> heart
        Suit.CLUB -> club
        else -> diamond
    }

    /** Draws [suit] with its top-left corner at [topLeft] and the given [size]. */
    fun DrawScope.drawSuit(suit: Int, topLeft: Offset, size: Float, color: Color) = drawShape(path(suit), topLeft, size, color)

    fun DrawScope.drawStar(topLeft: Offset, size: Float, color: Color) = drawShape(star, topLeft, size, color)

    private fun DrawScope.drawShape(path: Path, topLeft: Offset, size: Float, color: Color) {
        withTransform({
            translate(topLeft.x, topLeft.y)
            scale(size / 100f, size / 100f, pivot = Offset.Zero)
        }) {
            drawPath(path, color)
        }
    }
}
