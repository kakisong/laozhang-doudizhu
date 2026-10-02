package com.kaynzhang.doudizhu.ui.card

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import com.kaynzhang.doudizhu.engine.model.Card
import com.kaynzhang.doudizhu.engine.model.Rk
import com.kaynzhang.doudizhu.ui.card.Suits.drawStar
import com.kaynzhang.doudizhu.ui.card.Suits.drawSuit
import com.kaynzhang.doudizhu.ui.theme.DdzColors

/** Shared text measurer for card faces (provided once per screen, cached across cards). */
val LocalCardText = staticCompositionLocalOf<TextMeasurer> { error("no card text measurer") }

/**
 * One playing card drawn entirely with vector shapes and text. Everything is proportional
 * to the card width, so the same face works in the hand, on the table and in the 底牌 row.
 */
@Composable
fun CardFace(card: Card, faceUp: Boolean, modifier: Modifier = Modifier, shade: Float = 0f) {
    val measurer = LocalCardText.current
    Canvas(modifier) {
        if (faceUp) drawFront(card, measurer) else drawBack()
        if (shade > 0f) {
            drawRoundRect(Color.Black.copy(alpha = shade), cornerRadius = CornerRadius(size.width * RADIUS))
        }
    }
}

@Composable
fun CardBack(modifier: Modifier = Modifier) {
    Canvas(modifier) { drawBack() }
}

private const val RADIUS = 0.09f

private fun DrawScope.drawFront(card: Card, measurer: TextMeasurer) {
    val w = size.width
    val h = size.height
    val corner = CornerRadius(w * RADIUS)
    drawRoundRect(Color(0x40000000), topLeft = Offset(w * 0.015f, w * 0.03f), size = size, cornerRadius = corner)
    drawRoundRect(DdzColors.CardFace, size = size, cornerRadius = corner)
    drawRoundRect(DdzColors.CardBorder, size = size, cornerRadius = corner, style = Stroke(width = w * 0.018f))

    val color = if (card.isRed) DdzColors.SuitRed else DdzColors.SuitBlack
    if (card.isJoker) {
        drawJoker(card.rank == Rk.BJ, color, measurer)
        return
    }

    val label = Rk.label(card.rank)
    val tens = label.length > 1
    val index = measurer.measure(
        label,
        TextStyle(
            color = color,
            fontSize = (w * if (tens) 0.3f else 0.36f).toSp(),
            fontWeight = FontWeight.Bold,
            letterSpacing = if (tens) (-0.08).em else 0.em,
        ),
    )
    val indexLeft = w * 0.2f - index.size.width / 2f
    drawText(index, topLeft = Offset(indexLeft.coerceAtLeast(w * 0.03f), h * 0.02f))
    val small = w * 0.22f
    drawSuit(card.suit, Offset(w * 0.2f - small / 2, h * 0.02f + index.size.height * 0.9f), small, color)

    if (card.rank in Rk.JACK..Rk.KING) {
        // Court cards: a crown over a large letter, tucked into the lower right like a pip,
        // so an overlapped hand shows only the corner index.
        val crown = w * 0.34f
        drawCrown(Offset(w * 0.52f, h * 0.36f), crown)
        val big = measurer.measure(label, TextStyle(color = color, fontSize = (w * 0.5f).toSp(), fontWeight = FontWeight.Black))
        drawText(big, topLeft = Offset(w * 0.69f - big.size.width / 2f, h * 0.5f))
    } else {
        val pip = w * 0.46f
        drawSuit(card.suit, Offset(w * 0.46f, h * 0.52f), pip, color)
    }
}

private val crownPath = Path().apply {
    moveTo(6f, 78f)
    lineTo(14f, 28f)
    lineTo(34f, 52f)
    lineTo(50f, 12f)
    lineTo(66f, 52f)
    lineTo(86f, 28f)
    lineTo(94f, 78f)
    close()
    addRect(androidx.compose.ui.geometry.Rect(6f, 82f, 94f, 94f))
}

private fun DrawScope.drawCrown(topLeft: Offset, size: Float) {
    withTransform({
        translate(topLeft.x, topLeft.y)
        scale(size / 100f, size / 100f, pivot = Offset.Zero)
    }) {
        drawPath(crownPath, DdzColors.Gold)
        drawPath(crownPath, DdzColors.GoldDeep, style = Stroke(width = 5f))
    }
}

private fun DrawScope.drawJoker(big: Boolean, color: Color, measurer: TextMeasurer) {
    val w = size.width
    val h = size.height
    val style = TextStyle(color = color, fontSize = (w * 0.17f).toSp(), fontWeight = FontWeight.Bold)
    var y = h * 0.04f
    for (ch in "JOKER") {
        val l = measurer.measure(ch.toString(), style)
        drawText(l, topLeft = Offset(w * 0.2f - l.size.width / 2f, y))
        y += l.size.height * 0.82f
    }
    val star = w * 0.58f
    drawStar(Offset(w * 0.36f, h * 0.36f), star, if (big) DdzColors.SuitRed else DdzColors.SuitBlack)
    drawStar(Offset(w * 0.36f + star * 0.3f, h * 0.36f + star * 0.33f), star * 0.4f, if (big) DdzColors.Gold else DdzColors.CardFace)
}

private fun DrawScope.drawBack() {
    val w = size.width
    val h = size.height
    val corner = CornerRadius(w * RADIUS)
    drawRoundRect(Color(0x40000000), topLeft = Offset(w * 0.015f, w * 0.03f), size = size, cornerRadius = corner)
    drawRoundRect(DdzColors.BackRedDeep, size = size, cornerRadius = corner)
    val inset = w * 0.08f
    val inner = RoundRect(inset, inset, w - inset, h - inset, CornerRadius(w * 0.05f))
    val clip = Path().apply { addRoundRect(inner) }
    clipPath(clip) {
        drawRect(DdzColors.BackRed)
        val step = w * 0.16f
        var x = -h
        while (x < w + h) {
            drawLine(DdzColors.Gold.copy(alpha = 0.35f), Offset(x, 0f), Offset(x + h, h), strokeWidth = w * 0.02f)
            drawLine(DdzColors.Gold.copy(alpha = 0.35f), Offset(x + h, 0f), Offset(x, h), strokeWidth = w * 0.02f)
            x += step
        }
    }
    drawRoundRect(DdzColors.Gold, topLeft = Offset(inset, inset), size = Size(w - 2 * inset, h - 2 * inset), cornerRadius = CornerRadius(w * 0.05f), style = Stroke(w * 0.025f))
}
