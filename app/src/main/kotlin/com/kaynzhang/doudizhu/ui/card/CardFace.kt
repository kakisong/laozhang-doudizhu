package com.kaynzhang.doudizhu.ui.card

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import com.kaynzhang.doudizhu.engine.model.Card
import com.kaynzhang.doudizhu.engine.model.Rk
import com.kaynzhang.doudizhu.ui.card.Suits.drawStar
import com.kaynzhang.doudizhu.ui.card.Suits.drawSuit
import com.kaynzhang.doudizhu.ui.theme.DdzColors
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Provided once per screen: card text shares the measurer's layout cache. */
val LocalCardText = staticCompositionLocalOf<TextMeasurer> { error("no card text measurer") }

/** Vector artwork with a jumbo upper index that remains visible in an overlapped hand. */
@Composable
fun CardFace(
    card: Card,
    faceUp: Boolean,
    modifier: Modifier = Modifier,
    shade: Float = 0f,
    selected: Boolean = false,
    jumbo: Boolean = false,
) {
    val measurer = LocalCardText.current
    Canvas(modifier) {
        when {
            !faceUp -> drawBack()
            jumbo -> drawJumbo(card, measurer)
            else -> drawFront(card, measurer)
        }
        val corner = CornerRadius(size.width * RADIUS)
        if (selected) {
            val edge = size.width * 0.018f
            drawRoundRect(
                DdzColors.GoldDeep,
                topLeft = Offset(edge, edge),
                size = Size(size.width - edge * 2, size.height - edge * 2),
                cornerRadius = corner,
                style = Stroke(size.width * 0.036f),
            )
        }
        if (shade > 0f) {
            drawRoundRect(Color.Black.copy(alpha = shade.coerceIn(0f, 1f)), cornerRadius = corner)
        }
    }
}

@Composable
fun CardBack(modifier: Modifier = Modifier) {
    Canvas(modifier) { drawBack() }
}

private const val RADIUS = 0.065f

private fun DrawScope.drawBlank() {
    val w = size.width
    val inset = w * 0.008f
    val corner = CornerRadius(w * RADIUS)
    drawRoundRect(
        Color(0x2D071C15),
        topLeft = Offset(w * 0.012f, w * 0.025f),
        size = size,
        cornerRadius = corner,
    )
    drawRoundRect(
        Brush.linearGradient(
            listOf(DdzColors.CardFace, Color(0xFFFFFFFC), DdzColors.CardFace),
            end = Offset(w, size.height),
        ),
        cornerRadius = corner,
    )
    drawRoundRect(
        DdzColors.CardBorder,
        topLeft = Offset(inset, inset),
        size = Size(w - inset * 2, size.height - inset * 2),
        cornerRadius = corner,
        style = Stroke((w * 0.013f).coerceAtLeast(0.7f)),
    )
    drawRoundRect(
        Color.White.copy(alpha = 0.72f),
        topLeft = Offset(w * 0.027f, w * 0.027f),
        size = Size(w * 0.946f, size.height - w * 0.054f),
        cornerRadius = CornerRadius(w * 0.044f),
        style = Stroke((w * 0.006f).coerceAtLeast(0.5f)),
    )
}

private fun DrawScope.drawFront(card: Card, measurer: TextMeasurer) {
    drawBlank()
    val color = if (card.isRed) DdzColors.SuitRed else DdzColors.SuitBlack
    if (card.isJoker) {
        drawJoker(card.rank == Rk.BJ, color, measurer)
        return
    }

    when (card.rank) {
        in Rk.JACK..Rk.KING -> drawCourt(card, color)
        else -> drawCentralSuit(card, color)
    }
    drawIndex(card, color, measurer, main = true)
    withTransform({ rotate(180f, center) }) {
        drawIndex(card, color, measurer, main = false)
    }
}

private fun DrawScope.drawIndex(card: Card, color: Color, measurer: TextMeasurer, main: Boolean) {
    val w = size.width
    val label = Rk.label(card.rank)
    val tens = label.length > 1
    // Every rank has the same height; a two-digit index is condensed only horizontally.
    val font = if (main) 0.43f else 0.245f
    val axis = w * if (main) 0.20f else 0.12f
    val top = w * 0.025f
    val index = measurer.measure(
        label,
        TextStyle(
            color = color,
            fontFamily = FontFamily.Serif,
            fontSize = (w * font).toSp(),
            fontWeight = FontWeight.Bold,
            letterSpacing = if (tens) (-0.085).em else (-0.025).em,
        ),
    )
    val fit = if (tens) min(1f, w * (if (main) 0.38f else 0.22f) / index.size.width) else 1f
    val left = (axis - index.size.width * fit / 2f).coerceAtLeast(w * 0.024f)
    withTransform({
        translate(left, top)
        scale(fit, 1f, pivot = Offset.Zero)
    }) { drawText(index, topLeft = Offset.Zero) }
    if (main) {
        val suit = w * 0.215f
        // Use the full line box so Q's tail and J's serif never touch the suit below.
        val suitTop = top + index.size.height + w * 0.018f
        drawSuit(card.suit, Offset(axis - suit / 2f, suitTop), suit, color)
    }
}

/** One upright suit is faster to recognise than counting a field of small, reversible pips. */
private fun DrawScope.drawCentralSuit(card: Card, color: Color) {
    val w = size.width
    val suit = w * 0.47f
    val axis = w * 0.58f
    val y = size.height * 0.61f
    drawSuit(card.suit, Offset(axis - suit / 2, y - suit / 2), suit, color)
    val lineY = y + suit / 2 + w * 0.065f
    drawLine(
        DdzColors.CardBorder.copy(alpha = 0.65f),
        Offset(axis - w * 0.10f, lineY),
        Offset(axis + w * 0.10f, lineY),
        (w * 0.006f).coerceAtLeast(0.5f),
    )
}

/** Engraved double-headed court portrait: each rank has its own headdress and attribute. */
private fun DrawScope.drawCourt(card: Card, ink: Color) {
    val w = size.width
    val artWidth = w * 0.43f
    val artHeight = min(size.height * 0.46f, w * 0.67f)
    val left = w * 0.365f
    val top = size.height * 0.33f
    withTransform({
        translate(left, top)
        scale(artWidth / 100f, artHeight / 144f, pivot = Offset.Zero)
    }) {
        val gold = DdzColors.GoldDeep
        drawRect(DdzColors.Gold.copy(alpha = 0.09f), size = Size(100f, 144f))
        drawRect(gold.copy(alpha = 0.75f), size = Size(100f, 144f), style = Stroke(1.8f))
        drawRect(gold.copy(alpha = 0.42f), topLeft = Offset(4f, 4f), size = Size(92f, 136f), style = Stroke(0.8f))
        drawCourtHalf(card, ink)
        withTransform({ rotate(180f, Offset(50f, 72f)) }) { drawCourtHalf(card, ink) }
        drawLine(gold, Offset(6f, 72f), Offset(94f, 72f), 1.8f)
        for (x in 12..88 step 12) drawDiamond(Offset(x.toFloat(), 72f), 3f, ink)
    }
}

private fun DrawScope.drawCourtHalf(card: Card, ink: Color) {
    val gold = DdzColors.GoldDeep
    val paper = DdzColors.CardFace
    val dark = DdzColors.SuitBlack.copy(alpha = 0.9f)
    val queen = card.rank == Rk.QUEEN
    val king = card.rank == Rk.KING
    val robe = Path().apply {
        moveTo(17f, 71f)
        lineTo(23f, 56f)
        lineTo(39f, 48f)
        lineTo(61f, 48f)
        lineTo(78f, 56f)
        lineTo(84f, 71f)
        close()
    }
    drawPath(robe, ink)
    drawPath(robe, gold, style = Stroke(1.9f))
    // Fine diagonal brocade, contained by the coat silhouette.
    clipPath(robe) {
        for (x in -12..108 step 13) {
            drawLine(gold.copy(alpha = 0.85f), Offset(x.toFloat(), 47f), Offset(x + 25f, 74f), 1.0f)
        }
        drawLine(paper, Offset(33f, 48f), Offset(57f, 74f), 6f)
        drawLine(gold, Offset(33f, 48f), Offset(57f, 74f), 1.3f)
        for (y in 55..69 step 7) drawDiamond(Offset(67f, y.toFloat()), 2.2f, paper)
    }
    val hair = Path().apply {
        moveTo(33f, 30f)
        cubicTo(33f, 17f, 62f, 16f, 65f, 30f)
        lineTo(if (queen) 70f else 64f, 54f)
        lineTo(56f, 51f)
        lineTo(37f, 51f)
        lineTo(if (queen) 29f else 34f, 54f)
        close()
    }
    drawPath(hair, gold)
    drawPath(hair, dark.copy(alpha = 0.5f), style = Stroke(1.1f))
    val face = Path().apply {
        moveTo(39f, 27f)
        quadraticTo(49f, 21f, 59f, 28f)
        lineTo(59f, 40f)
        quadraticTo(57f, 49f, 50f, 51f)
        quadraticTo(41f, 48f, 39f, 40f)
        close()
    }
    drawPath(face, paper)
    drawPath(face, gold, style = Stroke(1.1f))
    drawLine(dark, Offset(42f, 34f), Offset(46f, 34f), 1.3f)
    drawLine(dark, Offset(53f, 34f), Offset(57f, 34f), 1.3f)
    val nose = Path().apply {
        moveTo(50f, 34f)
        lineTo(48f, 41f)
        lineTo(52f, 41f)
    }
    drawPath(nose, gold, style = Stroke(1.1f))
    drawLine(ink, Offset(47f, 45f), Offset(53f, 45f), 1.4f)
    if (king) {
        val beard = Path().apply {
            moveTo(40f, 42f)
            lineTo(46f, 46f)
            lineTo(50f, 44f)
            lineTo(54f, 46f)
            lineTo(59f, 42f)
            quadraticTo(59f, 51f, 50f, 56f)
            quadraticTo(40f, 51f, 40f, 42f)
            close()
        }
        drawPath(beard, gold)
        drawLine(paper.copy(alpha = 0.75f), Offset(49f, 48f), Offset(50f, 53f), 1.0f)
    }
    val collar = Path().apply {
        moveTo(36f, 50f)
        lineTo(49f, 58f)
        lineTo(62f, 50f)
        lineTo(58f, 58f)
        lineTo(49f, 64f)
        lineTo(39f, 57f)
        close()
    }
    drawPath(collar, paper)
    drawPath(collar, gold, style = Stroke(1.1f))

    if (queen || king) {
        val crown = Path().apply {
            moveTo(33f, 26f)
            lineTo(30f, 13f)
            lineTo(40f, 19f)
            lineTo(49f, if (queen) 8f else 6f)
            lineTo(59f, 19f)
            lineTo(69f, 13f)
            lineTo(65f, 26f)
            close()
        }
        drawPath(crown, DdzColors.Gold)
        drawPath(crown, gold, style = Stroke(1.7f))
        drawLine(ink, Offset(35f, 23f), Offset(63f, 23f), 2f)
        drawDiamond(Offset(49f, 18f), 3f, ink)
        for (x in listOf(31f, 49f, 68f)) drawCircle(gold, 1.9f, Offset(x, if (x == 49f) 7f else 12f))
    } else {
        val hat = Path().apply {
            moveTo(31f, 26f)
            quadraticTo(31f, 13f, 47f, 13f)
            quadraticTo(65f, 13f, 68f, 26f)
            close()
        }
        drawPath(hat, ink)
        drawPath(hat, gold, style = Stroke(1.7f))
        drawLine(gold, Offset(31f, 26f), Offset(68f, 26f), 3f)
        val feather = Path().apply {
            moveTo(59f, 21f)
            cubicTo(60f, 11f, 76f, 6f, 76f, 9f)
            cubicTo(72f, 17f, 66f, 19f, 59f, 21f)
            close()
        }
        drawPath(feather, paper)
        drawPath(feather, gold, style = Stroke(1.1f))
    }
    if (queen) {
        drawLine(gold, Offset(76f, 42f), Offset(69f, 68f), 2f)
        for (i in 0..5) {
            val angle = i * Math.PI / 3
            drawCircle(ink, 3.4f, Offset(76f + cos(angle).toFloat() * 4f, 40f + sin(angle).toFloat() * 4f))
        }
        drawCircle(DdzColors.Gold, 2.8f, Offset(76f, 40f))
    } else {
        drawLine(gold, Offset(76f, 31f), Offset(71f, 68f), 2.8f)
        if (king) {
            drawCircle(DdzColors.Gold, 5f, Offset(77f, 30f))
            drawCircle(ink, 5f, Offset(77f, 30f), style = Stroke(1.1f))
            drawLine(gold, Offset(77f, 21f), Offset(77f, 27f), 1.7f)
            drawLine(gold, Offset(74f, 23f), Offset(80f, 23f), 1.7f)
        } else {
            drawDiamond(Offset(77f, 29f), 5f, DdzColors.Gold)
        }
    }
}

/** Standard red/black JOKER indices accompany the engraved jester seal. */
private fun DrawScope.drawJoker(big: Boolean, color: Color, measurer: TextMeasurer) {
    drawJokerIndex(color, measurer, main = true)
    withTransform({ rotate(180f, center) }) { drawJokerIndex(color, measurer, main = false) }
    val w = size.width
    val width = w * 0.42f
    val height = min(size.height * 0.54f, w * 0.80f)
    val left = w * 0.375f
    val top = size.height * 0.29f
    withTransform({
        translate(left, top)
        scale(width / 100f, height / 140f, pivot = Offset.Zero)
    }) {
        val gold = DdzColors.GoldDeep
        val frame = Path().apply {
            moveTo(50f, 1f)
            lineTo(98f, 70f)
            lineTo(50f, 139f)
            lineTo(2f, 70f)
            close()
        }
        drawPath(frame, DdzColors.Gold.copy(alpha = 0.08f))
        drawPath(frame, gold, style = Stroke(1.7f))
        withTransform({ scale(0.89f, 0.89f, pivot = Offset(50f, 70f)) }) {
            drawPath(frame, color.copy(alpha = 0.70f), style = Stroke(0.9f))
        }
        for (i in 0 until 16) {
            val angle = i * Math.PI / 8
            val start = Offset(50f + cos(angle).toFloat() * 23f, 73f + sin(angle).toFloat() * 23f)
            val end = Offset(50f + cos(angle).toFloat() * 29f, 73f + sin(angle).toFloat() * 29f)
            drawLine(gold.copy(alpha = 0.80f), start, end, 1.2f)
        }
        drawCircle(color, 22f, Offset(50f, 73f))
        drawCircle(gold, 22f, Offset(50f, 73f), style = Stroke(1.5f))
        drawCircle(DdzColors.CardFace, 15f, Offset(50f, 73f))
        val cap = Path().apply {
            moveTo(33f, 66f)
            cubicTo(29f, 60f, 26f, 49f, 22f, 46f)
            cubicTo(34f, 43f, 40f, 48f, 44f, 55f)
            quadraticTo(46f, 39f, 50f, 35f)
            quadraticTo(57f, 40f, 58f, 55f)
            cubicTo(64f, 46f, 74f, 43f, 80f, 45f)
            quadraticTo(69f, 51f, 67f, 66f)
            quadraticTo(50f, 60f, 33f, 66f)
            close()
        }
        drawPath(cap, if (big) color else gold)
        drawPath(cap, gold, style = Stroke(1.7f))
        for (point in listOf(Offset(22f, 46f), Offset(50f, 35f), Offset(80f, 45f))) {
            drawCircle(DdzColors.Gold, 3.5f, point)
            drawCircle(gold, 3.5f, point, style = Stroke(1f))
        }
        drawLine(color, Offset(41f, 73f), Offset(46f, 71f), 1.6f)
        drawLine(color, Offset(55f, 71f), Offset(60f, 73f), 1.6f)
        val smile = Path().apply {
            moveTo(43f, 79f)
            quadraticTo(50f, 86f, 58f, 78f)
        }
        drawPath(smile, color, style = Stroke(1.7f))
        val collar = Path().apply {
            moveTo(34f, 90f)
            lineTo(38f, 101f)
            lineTo(47f, 94f)
            lineTo(51f, 107f)
            lineTo(56f, 94f)
            lineTo(65f, 101f)
            lineTo(67f, 90f)
            close()
        }
        drawPath(collar, if (big) color else gold)
        drawPath(collar, gold, style = Stroke(1.2f))
        drawStar(Offset(42f, 14f), 16f, color)
        drawDiamond(Offset(50f, 122f), 4f, gold)
    }
}

private fun DrawScope.drawJokerIndex(color: Color, measurer: TextMeasurer, main: Boolean) {
    val w = size.width
    val font = w * if (main) 0.235f else 0.125f
    val axis = w * if (main) 0.20f else 0.12f
    val style = TextStyle(
        color = color,
        fontFamily = FontFamily.Serif,
        fontSize = font.toSp(),
        fontWeight = FontWeight.Bold,
    )
    val letters = "JOKER".map { measurer.measure(it.toString(), style) }
    val lineHeight = letters.maxOf { it.size.height }.toFloat()
    val step = lineHeight * 0.84f
    var y = w * 0.045f
    for (text in letters) {
        drawText(text, topLeft = Offset(axis - text.size.width / 2f, y))
        y += step
    }
}

/** Compact bottom cards keep a readable, centred rank and suit. */
private fun DrawScope.drawJumbo(card: Card, measurer: TextMeasurer) {
    val w = size.width
    val h = size.height
    drawBlank()
    val color = if (card.isRed) DdzColors.SuitRed else DdzColors.SuitBlack
    if (card.isJoker) {
        val style = TextStyle(
            color = color,
            fontFamily = FontFamily.Serif,
            fontSize = (w * 0.235f).toSp(),
            fontWeight = FontWeight.Bold,
        )
        val letters = "JOKER".map { measurer.measure(it.toString(), style) }
        val lineHeight = letters.maxOf { it.size.height }.toFloat()
        val step = lineHeight * 0.84f
        val columnHeight = lineHeight + step * (letters.size - 1)
        var y = (h - columnHeight) / 2f
        for (text in letters) {
            drawText(text, topLeft = Offset((w - text.size.width) / 2f, y))
            y += step
        }
        return
    }
    val label = Rk.label(card.rank)
    val tens = label.length > 1
    val rank = measurer.measure(
        label,
        TextStyle(
            color = color,
            fontFamily = FontFamily.Serif,
            fontSize = (w * 0.61f).toSp(),
            fontWeight = FontWeight.Bold,
            letterSpacing = if (tens) (-0.085).em else (-0.025).em,
        ),
    )
    val suit = w * 0.32f
    val rankToSuit = rank.size.height + w * 0.018f
    val top = (h - rankToSuit - suit) / 2f
    val fit = if (tens) min(1f, w * 0.85f / rank.size.width) else 1f
    withTransform({
        translate((w - rank.size.width * fit) / 2f, top)
        scale(fit, 1f, pivot = Offset.Zero)
    }) { drawText(rank, topLeft = Offset.Zero) }
    drawSuit(card.suit, Offset((w - suit) / 2f, top + rankToSuit), suit, color)
}

/** Fine lattice, ivory edges, and a compass medallion make a reversible woven card back. */
private fun DrawScope.drawBack() {
    val w = size.width
    val h = size.height
    drawBlank()
    val inset = w * 0.062f
    val bounds = RoundRect(inset, inset, w - inset, h - inset, CornerRadius(w * 0.035f))
    val clip = Path().apply { addRoundRect(bounds) }
    clipPath(clip) {
        drawRect(
            Brush.radialGradient(
                listOf(DdzColors.BackRed, DdzColors.BackRedDeep),
                center = center,
                radius = h * 0.65f,
            ),
        )
        val step = w * 0.105f
        var x = -h
        while (x < w + h) {
            drawLine(DdzColors.Cream.copy(alpha = 0.20f), Offset(x, 0f), Offset(x + h, h), (w * 0.006f).coerceAtLeast(0.5f))
            drawLine(DdzColors.Cream.copy(alpha = 0.20f), Offset(x + h, 0f), Offset(x, h), (w * 0.006f).coerceAtLeast(0.5f))
            x += step
        }
        var y = inset + step / 2
        while (y < h - inset) {
            var dotX = inset + step / 2
            while (dotX < w - inset) {
                drawCircle(DdzColors.Gold.copy(alpha = 0.28f), w * 0.008f, Offset(dotX, y))
                dotX += step
            }
            y += step
        }
        val badge = Size(w * 0.54f, w * 0.71f)
        val badgeAt = Offset((w - badge.width) / 2, (h - badge.height) / 2)
        drawOval(DdzColors.BackRedDeep, topLeft = badgeAt, size = badge)
        drawOval(DdzColors.Gold, topLeft = badgeAt, size = badge, style = Stroke(w * 0.016f))
        val ringInset = w * 0.035f
        drawOval(
            DdzColors.Gold.copy(alpha = 0.68f),
            topLeft = badgeAt + Offset(ringInset, ringInset),
            size = Size(badge.width - ringInset * 2, badge.height - ringInset * 2),
            style = Stroke((w * 0.006f).coerceAtLeast(0.6f)),
        )
        val compass = Path().apply {
            for (i in 0 until 16) {
                val angle = -Math.PI / 2 + i * Math.PI / 8
                val radius = if (i % 2 == 0) w * 0.205f else w * 0.072f
                val point = Offset(w / 2 + cos(angle).toFloat() * radius, h / 2 + sin(angle).toFloat() * radius)
                if (i == 0) moveTo(point.x, point.y) else lineTo(point.x, point.y)
            }
            close()
        }
        drawPath(compass, DdzColors.Gold)
        drawPath(compass, DdzColors.CardFace.copy(alpha = 0.66f), style = Stroke(w * 0.007f))
        drawDiamond(Offset(w / 2, h / 2), w * 0.105f, DdzColors.BackRed)
        drawDiamond(Offset(w / 2, h / 2), w * 0.065f, DdzColors.Gold)
        drawCircle(DdzColors.BackRedDeep, w * 0.022f, Offset(w / 2, h / 2))
        for (at in listOf(
            Offset(w * 0.145f, w * 0.15f),
            Offset(w * 0.855f, w * 0.15f),
            Offset(w * 0.145f, h - w * 0.15f),
            Offset(w * 0.855f, h - w * 0.15f),
        )) drawDiamond(at, w * 0.034f, DdzColors.Gold)
    }
    drawRoundRect(
        DdzColors.GoldDeep,
        topLeft = Offset(inset, inset),
        size = Size(w - inset * 2, h - inset * 2),
        cornerRadius = CornerRadius(w * 0.035f),
        style = Stroke((w * 0.012f).coerceAtLeast(0.7f)),
    )
    val inner = inset + w * 0.027f
    drawRoundRect(
        DdzColors.Cream.copy(alpha = 0.52f),
        topLeft = Offset(inner, inner),
        size = Size(w - inner * 2, h - inner * 2),
        cornerRadius = CornerRadius(w * 0.023f),
        style = Stroke((w * 0.006f).coerceAtLeast(0.5f)),
    )
}

private fun DrawScope.drawDiamond(center: Offset, radius: Float, color: Color) {
    val path = Path().apply {
        moveTo(center.x, center.y - radius)
        lineTo(center.x + radius, center.y)
        lineTo(center.x, center.y + radius)
        lineTo(center.x - radius, center.y)
        close()
    }
    drawPath(path, color)
}
