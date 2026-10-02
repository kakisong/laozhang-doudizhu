package com.kaynzhang.doudizhu.ui.card

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import com.kaynzhang.doudizhu.engine.model.Suit

/** Engraved deck silhouettes in a 100 × 100 view box; no platform symbol or emoji fonts. */
object Suits {
    private val spade = Path().apply {
        moveTo(50f, 3f)
        cubicTo(60f, 19f, 89f, 35f, 91f, 56f)
        cubicTo(94f, 78f, 68f, 87f, 54f, 73f)
        cubicTo(55f, 83f, 58f, 90f, 66f, 96f)
        lineTo(34f, 96f)
        cubicTo(42f, 90f, 45f, 83f, 46f, 73f)
        cubicTo(32f, 87f, 6f, 78f, 9f, 56f)
        cubicTo(11f, 35f, 40f, 19f, 50f, 3f)
        close()
    }

    private val heart = Path().apply {
        moveTo(50f, 94f)
        cubicTo(40f, 81f, 8f, 58f, 6f, 36f)
        cubicTo(3f, 15f, 17f, 5f, 31f, 7f)
        cubicTo(42f, 8f, 48f, 17f, 50f, 24f)
        cubicTo(52f, 17f, 58f, 8f, 69f, 7f)
        cubicTo(83f, 5f, 97f, 15f, 94f, 36f)
        cubicTo(92f, 58f, 60f, 81f, 50f, 94f)
        close()
    }

    private val diamond = Path().apply {
        moveTo(50f, 3f)
        lineTo(87f, 50f)
        lineTo(50f, 97f)
        lineTo(13f, 50f)
        close()
    }

    private val club = Path().apply {
        addOval(Rect(29f, 4f, 71f, 46f))
        addOval(Rect(6f, 35f, 49f, 78f))
        addOval(Rect(51f, 35f, 94f, 78f))
        addOval(Rect(33f, 32f, 67f, 73f))
        moveTo(45f, 61f)
        cubicTo(45f, 81f, 41f, 89f, 33f, 96f)
        lineTo(67f, 96f)
        cubicTo(59f, 89f, 55f, 81f, 55f, 61f)
        close()
    }

    private val star = Path().apply {
        for (i in 0 until 10) {
            val radius = if (i % 2 == 0) 47f else 21f
            val angle = Math.toRadians(-90.0 + i * 36.0)
            val x = 50f + radius * kotlin.math.cos(angle).toFloat()
            val y = 51f + radius * kotlin.math.sin(angle).toFloat()
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

    fun DrawScope.drawSuit(suit: Int, topLeft: Offset, size: Float, color: Color) =
        drawShape(path(suit), topLeft, size, color)

    fun DrawScope.drawStar(topLeft: Offset, size: Float, color: Color) =
        drawShape(star, topLeft, size, color)

    private fun DrawScope.drawShape(path: Path, topLeft: Offset, size: Float, color: Color) {
        withTransform({
            translate(topLeft.x, topLeft.y)
            scale(size / 100f, size / 100f, pivot = Offset.Zero)
        }) {
            drawPath(path, color)
        }
    }
}
