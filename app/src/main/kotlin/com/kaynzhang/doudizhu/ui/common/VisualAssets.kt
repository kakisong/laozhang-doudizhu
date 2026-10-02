package com.kaynzhang.doudizhu.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.dp
import com.kaynzhang.doudizhu.ui.theme.DdzColors
import kotlin.math.cos
import kotlin.math.sin

/** Shared vector icon family: stable on every device, without font or emoji substitutions. */
enum class UiIcon { BACK, SETTINGS, STATS, RULES, COIN, CHEVRON, CROWN, CARDS, CHECK, CLOSE, MORE, PAUSE }

@Composable
fun LineIcon(icon: UiIcon, modifier: Modifier = Modifier, color: Color = DdzColors.Cream) {
    Canvas(modifier) {
        val unit = size.minDimension / 24f
        withTransform({
            translate((size.width - unit * 24f) / 2, (size.height - unit * 24f) / 2)
            scale(unit, unit, pivot = Offset.Zero)
        }) {
            val stroke = Stroke(1.6f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            fun path(vararg points: Pair<Float, Float>, closed: Boolean = false) {
                val p = Path().apply {
                    points.forEachIndexed { i, (x, y) -> if (i == 0) moveTo(x, y) else lineTo(x, y) }
                    if (closed) close()
                }
                drawPath(p, color, style = stroke)
            }
            when (icon) {
                UiIcon.BACK -> { path(14f to 5f, 7f to 12f, 14f to 19f); path(7f to 12f, 21f to 12f) }
                UiIcon.CHEVRON -> path(9f to 5f, 16f to 12f, 9f to 19f)
                UiIcon.CHECK -> path(5f to 12f, 10f to 17f, 19f to 7f)
                UiIcon.CLOSE -> { path(6f to 6f, 18f to 18f); path(18f to 6f, 6f to 18f) }
                UiIcon.MORE -> for (x in listOf(5f, 12f, 19f)) drawCircle(color, 1.4f, Offset(x, 12f))
                UiIcon.PAUSE -> {
                    drawRoundRect(color, Offset(6f, 4f), Size(3f, 16f), CornerRadius(1f))
                    drawRoundRect(color, Offset(15f, 4f), Size(3f, 16f), CornerRadius(1f))
                }
                UiIcon.COIN -> {
                    drawCircle(color, 9f, Offset(12f, 12f), style = stroke)
                    drawCircle(color.copy(alpha = 0.6f), 6.2f, Offset(12f, 12f), style = Stroke(0.8f))
                    path(12f to 8f, 15f to 12f, 12f to 16f, 9f to 12f, closed = true)
                }
                UiIcon.CROWN -> {
                    path(3f to 7f, 7f to 11f, 12f to 4f, 17f to 11f, 21f to 7f, 19f to 18f, 5f to 18f, closed = true)
                    path(5f to 21f, 19f to 21f)
                }
                UiIcon.STATS -> {
                    path(4f to 4f, 4f to 20f, 21f to 20f)
                    path(8f to 16f, 8f to 12f); path(13f to 16f, 13f to 8f); path(18f to 16f, 18f to 4f)
                }
                UiIcon.RULES -> {
                    path(12f to 6f, 12f to 21f)
                    path(12f to 6f, 8f to 4f, 3f to 4f, 3f to 18f, 8f to 18f, 12f to 21f, 16f to 18f, 21f to 18f, 21f to 4f, 16f to 4f, closed = true)
                }
                UiIcon.CARDS -> {
                    drawRoundRect(color, Offset(8f, 4f), Size(12f, 17f), CornerRadius(2f), style = stroke)
                    path(5f to 18f, 3f to 7f, 6f to 6f)
                    path(14f to 8f, 17f to 12f, 14f to 16f, 11f to 12f, closed = true)
                }
                UiIcon.SETTINGS -> {
                    val gear = Path().apply {
                        for (i in 0 until 32) {
                            val radius = if (i % 4 in 1..2) 9.3f else 7.4f
                            val a = i * Math.PI / 16
                            val x = 12f + radius * cos(a).toFloat()
                            val y = 12f + radius * sin(a).toFloat()
                            if (i == 0) moveTo(x, y) else lineTo(x, y)
                        }
                        close()
                    }
                    drawPath(gear, color, style = stroke)
                    drawCircle(color, 3.1f, Offset(12f, 12f), style = stroke)
                }
            }
        }
    }
}

/** A quiet woven felt with warm edge lighting; static drawing avoids idle animation work. */
@Composable
fun TableBackdrop(modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxSize()) {
        drawRect(Brush.radialGradient(
            listOf(DdzColors.FeltCenter, DdzColors.FeltEdge),
            center = Offset(size.width * 0.48f, size.height * 0.34f),
            radius = size.width * 0.75f,
        ))
        // Sparse crossed threads suggest fabric without obscuring text or creating visual noise.
        val step = 7.dp.toPx()
        var x = -size.height
        while (x < size.width) {
            drawLine(Color.White.copy(alpha = 0.015f), Offset(x, 0f), Offset(x + size.height, size.height), 0.45.dp.toPx())
            drawLine(Color.Black.copy(alpha = 0.035f), Offset(x, size.height), Offset(x + size.height, 0f), 0.45.dp.toPx())
            x += step
        }
        drawRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.1f), Color.Transparent, Color.Black.copy(alpha = 0.16f))))
        drawLine(DdzColors.Gold.copy(alpha = 0.08f), Offset(0f, 1.dp.toPx()), Offset(size.width, 1.dp.toPx()), 1.dp.toPx())
    }
}

/** Stable built-in portraits also resolve names and avatar values from older saves. */
@Composable
fun PlayerPortrait(seed: String, modifier: Modifier = Modifier) {
    val portrait = remember(seed) { com.kaynzhang.doudizhu.data.BuiltInPortraits.resolve(seed) }
    Canvas(modifier) { drawVectorPortrait(portrait) }
}
