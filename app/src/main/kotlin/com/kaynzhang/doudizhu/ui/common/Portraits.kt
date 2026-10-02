package com.kaynzhang.doudizhu.ui.common

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import com.kaynzhang.doudizhu.data.PortraitBeard
import com.kaynzhang.doudizhu.data.PortraitFace
import com.kaynzhang.doudizhu.data.PortraitGlasses
import com.kaynzhang.doudizhu.data.PortraitHair
import com.kaynzhang.doudizhu.data.PortraitHat
import com.kaynzhang.doudizhu.data.PortraitOutfit
import com.kaynzhang.doudizhu.data.PortraitSpec
import com.kaynzhang.doudizhu.ui.theme.DdzColors

/** Author-drawn silhouettes stay crisp from the settings grid to the smallest seat portrait. */
internal fun DrawScope.drawVectorPortrait(spec: PortraitSpec) {
    val unit = size.minDimension / 100f
    withTransform({
        translate((size.width - 100f * unit) / 2f, (size.height - 100f * unit) / 2f)
        scale(unit, unit, pivot = Offset.Zero)
    }) {
        val clip = Path().apply { addOval(Rect(0f, 0f, 100f, 100f)) }
        clipPath(clip) {
            drawRect(Brush.linearGradient(listOf(Color(spec.backdrop), DdzColors.Surface), Offset.Zero, Offset(100f, 110f)))
            drawCircle(DdzColors.Gold.copy(alpha = 0.13f), 42f, Offset(77f, 24f))
            drawPortraitClothes(spec)
            drawRearHair(spec)
            drawPortraitFace(spec)
            drawFrontHair(spec)
            drawPortraitFeatures(spec)
            drawPortraitHat(spec)
        }
        drawCircle(DdzColors.Gold.copy(alpha = 0.4f), 49f, Offset(50f, 50f), style = Stroke(1.4f))
    }
}

private fun silhouette(vararg points: Pair<Float, Float>): Path = Path().apply {
    points.forEachIndexed { i, (x, y) -> if (i == 0) moveTo(x, y) else lineTo(x, y) }
    close()
}

private fun DrawScope.drawPortraitClothes(s: PortraitSpec) {
    val skin = Color(s.skin)
    val coat = Color(s.coat)
    val accent = Color(s.accent)
    val lightCoat = lerp(coat, DdzColors.Cream, 0.14f)
    val shirt = DdzColors.Cream
    drawRoundRect(skin, Offset(41f, 60f), Size(18f, 21f), CornerRadius(6f))
    drawOval(lerp(skin, Color(0xFF9C7456), 0.24f), Offset(41f, 62f), Size(18f, 9f))
    drawOval(if (s.outfit == PortraitOutfit.OVERALLS) shirt else coat, Offset(8f, 72f), Size(84f, 61f))
    val collar = silhouette(37f to 73f, 44f to 78f, 50f to 75f, 56f to 78f, 63f to 73f, 50f to 95f)
    when (s.outfit) {
        PortraitOutfit.VEST -> {
            drawPath(collar, shirt)
            drawPath(silhouette(28f to 74f, 39f to 70f, 48f to 88f, 48f to 107f, 27f to 107f), lightCoat)
            drawPath(silhouette(72f to 74f, 61f to 70f, 52f to 88f, 52f to 107f, 73f to 107f), lightCoat)
            for (y in listOf(90f, 98f)) drawCircle(accent, 1.3f, Offset(50f, y))
        }
        PortraitOutfit.LAPELS -> {
            drawPath(collar, shirt)
            drawPath(silhouette(37f to 71f, 49f to 92f, 29f to 81f, 36f to 79f, 30f to 75f), lightCoat)
            drawPath(silhouette(63f to 71f, 51f to 92f, 71f to 81f, 64f to 79f, 70f to 75f), lightCoat)
            drawPath(silhouette(50f to 79f, 46f to 85f, 50f to 98f, 54f to 85f), accent)
            drawCircle(DdzColors.Gold, 1.6f, Offset(69f, 86f))
        }
        PortraitOutfit.CARDIGAN -> {
            drawPath(collar, shirt)
            drawLine(lightCoat, Offset(36f, 73f), Offset(50f, 94f), 5f, StrokeCap.Round)
            drawLine(lightCoat, Offset(64f, 73f), Offset(50f, 94f), 5f, StrokeCap.Round)
            drawLine(accent.copy(alpha = 0.5f), Offset(50f, 93f), Offset(50f, 108f), 1.4f)
            for (y in listOf(94f, 101f)) drawCircle(accent, 1.5f, Offset(54f, y))
        }
        PortraitOutfit.SCARF -> {
            val wrap = Path().apply {
                moveTo(32f, 73f); quadraticTo(47f, 85f, 66f, 74f)
                lineTo(65f, 86f); quadraticTo(48f, 92f, 32f, 82f); close()
            }
            drawPath(wrap, accent)
            drawPath(silhouette(58f to 80f, 68f to 83f, 66f to 103f, 57f to 97f), accent)
            drawLine(coat.copy(alpha = 0.45f), Offset(36f, 80f), Offset(59f, 84f), 1.2f, StrokeCap.Round)
            drawLine(coat.copy(alpha = 0.45f), Offset(61f, 87f), Offset(61f, 98f), 1.1f)
        }
        PortraitOutfit.HOODIE -> {
            val hood = Path().apply {
                moveTo(31f, 75f); quadraticTo(21f, 89f, 43f, 92f)
                lineTo(50f, 80f); lineTo(57f, 92f)
                quadraticTo(79f, 89f, 69f, 75f); lineTo(60f, 71f)
                quadraticTo(50f, 86f, 40f, 71f); close()
            }
            drawPath(hood, lightCoat)
            drawLine(accent, Offset(42f, 85f), Offset(41f, 96f), 1.6f, StrokeCap.Round)
            drawLine(accent, Offset(58f, 85f), Offset(59f, 96f), 1.6f, StrokeCap.Round)
        }
        PortraitOutfit.OVERALLS -> {
            drawRoundRect(coat, Offset(28f, 84f), Size(44f, 32f), CornerRadius(5f))
            drawLine(coat, Offset(32f, 73f), Offset(34f, 92f), 7f)
            drawLine(coat, Offset(68f, 73f), Offset(66f, 92f), 7f)
            drawRoundRect(accent, Offset(39f, 92f), Size(22f, 14f), CornerRadius(2f), style = Stroke(1.4f))
            drawCircle(DdzColors.Gold, 1.9f, Offset(34f, 86f)); drawCircle(DdzColors.Gold, 1.9f, Offset(66f, 86f))
        }
        PortraitOutfit.BOW_TIE -> {
            drawPath(collar, shirt)
            drawPath(silhouette(38f to 77f, 50f to 82f, 62f to 77f, 62f to 89f, 50f to 84f, 38f to 89f), accent)
            drawCircle(lerp(accent, DdzColors.Ink, 0.15f), 2.8f, Offset(50f, 83f))
            drawCircle(accent, 1.5f, Offset(50f, 96f))
        }
        PortraitOutfit.APRON -> {
            drawLine(accent, Offset(37f, 72f), Offset(39f, 88f), 4f)
            drawLine(accent, Offset(63f, 72f), Offset(61f, 88f), 4f)
            drawRoundRect(accent, Offset(32f, 83f), Size(36f, 34f), CornerRadius(6f))
            drawLine(coat.copy(alpha = 0.55f), Offset(40f, 96f), Offset(60f, 96f), 1.5f)
        }
        PortraitOutfit.POLO -> {
            drawPath(silhouette(38f to 71f, 49f to 80f, 42f to 87f, 33f to 77f), lightCoat)
            drawPath(silhouette(62f to 71f, 51f to 80f, 58f to 87f, 67f to 77f), lightCoat)
            drawLine(accent.copy(alpha = 0.5f), Offset(50f, 80f), Offset(50f, 94f), 2f)
            drawCircle(accent, 1.3f, Offset(50f, 85f))
        }
        PortraitOutfit.BLOUSE -> {
            val neck = Path().apply { moveTo(37f, 72f); quadraticTo(50f, 86f, 63f, 72f) }
            drawPath(neck, accent, style = Stroke(2.5f, cap = StrokeCap.Round))
            drawCircle(DdzColors.Gold, 2.3f, Offset(67f, 81f))
            drawLine(lightCoat, Offset(50f, 87f), Offset(50f, 105f), 1.8f)
        }
        PortraitOutfit.SHIRT -> {
            drawPath(silhouette(37f to 71f, 50f to 80f, 42f to 87f, 32f to 75f), shirt)
            drawPath(silhouette(63f to 71f, 50f to 80f, 58f to 87f, 68f to 75f), shirt)
            drawLine(lightCoat, Offset(50f, 83f), Offset(50f, 108f), 2f)
            for (y in listOf(89f, 99f)) drawCircle(accent, 1.3f, Offset(50f, y))
        }
    }
}

private fun DrawScope.drawRearHair(s: PortraitSpec) {
    val hair = Color(s.hairColor)
    when (s.hair) {
        PortraitHair.BOB -> drawRoundRect(hair, Offset(22f, 17f), Size(56f, 62f), CornerRadius(25f))
        PortraitHair.BUN -> {
            drawCircle(hair, 12f, Offset(74f, 20f))
            drawOval(hair, Offset(23f, 17f), Size(54f, 48f))
            drawArc(lerp(hair, DdzColors.Cream, 0.2f), 255f, 130f, false, Offset(65f, 11f), Size(16f, 16f), style = Stroke(1.2f))
        }
        PortraitHair.PONYTAIL -> {
            val tail = Path().apply {
                moveTo(69f, 26f); cubicTo(88f, 20f, 90f, 57f, 81f, 81f)
                quadraticTo(79f, 64f, 66f, 57f); close()
            }
            drawPath(tail, hair)
            drawOval(Color(s.accent), Offset(70f, 30f), Size(7f, 6f))
        }
        PortraitHair.BRAID -> {
            drawOval(hair, Offset(23f, 17f), Size(54f, 51f))
            for (y in listOf(55f, 65f, 75f, 84f)) {
                drawOval(hair, Offset(19f, y - 6f), Size(12f, 13f))
                drawOval(hair, Offset(69f, y - 6f), Size(12f, 13f))
                drawLine(lerp(hair, DdzColors.Cream, 0.17f), Offset(22f, y + 2f), Offset(28f, y - 2f), 1.1f)
                drawLine(lerp(hair, DdzColors.Cream, 0.17f), Offset(72f, y + 2f), Offset(78f, y - 2f), 1.1f)
            }
            drawRoundRect(Color(s.accent), Offset(20f, 86f), Size(10f, 4f), CornerRadius(1f))
            drawRoundRect(Color(s.accent), Offset(70f, 86f), Size(10f, 4f), CornerRadius(1f))
        }
        PortraitHair.CURLS -> {
            drawOval(hair, Offset(21f, 16f), Size(58f, 53f))
            for (at in listOf(Offset(24f, 30f), Offset(20f, 44f), Offset(24f, 59f), Offset(76f, 30f), Offset(80f, 44f), Offset(76f, 59f))) {
                drawCircle(hair, 8f, at)
            }
        }
        else -> Unit
    }
}

private fun DrawScope.drawPortraitFace(s: PortraitSpec) {
    val skin = Color(s.skin)
    val earX = when (s.face) { PortraitFace.BROAD -> 24f; PortraitFace.LONG -> 29f; else -> 26f }
    drawOval(skin, Offset(earX, 41f), Size(10f, 16f))
    drawOval(skin, Offset(90f - earX, 41f), Size(10f, 16f))
    val face = Path().apply {
        when (s.face) {
            PortraitFace.OVAL -> {
                moveTo(50f, 19f); cubicTo(24f, 18f, 26f, 43f, 31f, 57f)
                cubicTo(35f, 72f, 65f, 72f, 69f, 57f); cubicTo(74f, 43f, 76f, 18f, 50f, 19f)
            }
            PortraitFace.ROUND -> {
                moveTo(50f, 23f); cubicTo(24f, 21f, 23f, 37f, 26f, 51f)
                cubicTo(25f, 76f, 75f, 76f, 74f, 51f); cubicTo(77f, 37f, 76f, 21f, 50f, 23f)
            }
            PortraitFace.SQUARE -> {
                moveTo(50f, 20f); cubicTo(25f, 20f, 27f, 30f, 27f, 44f)
                lineTo(30f, 61f); quadraticTo(32f, 70f, 40f, 71f); lineTo(60f, 71f)
                quadraticTo(68f, 70f, 70f, 61f); lineTo(73f, 44f); cubicTo(73f, 30f, 75f, 20f, 50f, 20f)
            }
            PortraitFace.LONG -> {
                moveTo(50f, 17f); cubicTo(31f, 16f, 30f, 33f, 32f, 51f)
                cubicTo(31f, 82f, 69f, 82f, 68f, 51f); cubicTo(70f, 33f, 69f, 16f, 50f, 17f)
            }
            PortraitFace.HEART -> {
                moveTo(50f, 20f); cubicTo(22f, 19f, 25f, 39f, 28f, 50f)
                quadraticTo(33f, 65f, 50f, 72f); quadraticTo(67f, 65f, 72f, 50f)
                cubicTo(75f, 39f, 78f, 19f, 50f, 20f)
            }
            PortraitFace.ANGULAR -> {
                moveTo(50f, 20f); quadraticTo(30f, 18f, 29f, 36f)
                lineTo(27f, 50f); lineTo(35f, 64f); quadraticTo(50f, 79f, 65f, 64f)
                lineTo(73f, 50f); lineTo(71f, 36f); quadraticTo(70f, 18f, 50f, 20f)
            }
            PortraitFace.BROAD -> {
                moveTo(50f, 22f); cubicTo(21f, 20f, 23f, 36f, 25f, 48f)
                lineTo(27f, 60f); quadraticTo(31f, 70f, 50f, 70f)
                quadraticTo(69f, 70f, 73f, 60f); lineTo(75f, 48f); cubicTo(77f, 36f, 79f, 20f, 50f, 22f)
            }
        }
        close()
    }
    drawPath(face, Brush.linearGradient(listOf(lerp(skin, Color.White, 0.13f), skin), Offset(30f, 22f), Offset(70f, 68f)))
    drawPath(face, lerp(skin, Color(0xFF9A7857), 0.26f), style = Stroke(0.65f))
    if (s.earrings) {
        drawCircle(DdzColors.Gold, 2.5f, Offset(earX + 4f, 57f), style = Stroke(1.7f))
        drawCircle(DdzColors.Gold, 2.5f, Offset(96f - earX, 57f), style = Stroke(1.7f))
    }
}

private fun DrawScope.drawFrontHair(s: PortraitSpec) {
    val hair = Color(s.hairColor)
    val path = Path().apply {
        when (s.hair) {
            PortraitHair.SIDE_PART -> {
                moveTo(27f, 43f); cubicTo(20f, 22f, 29f, 12f, 49f, 14f)
                cubicTo(69f, 9f, 79f, 29f, 72f, 44f); lineTo(66f, 32f)
                cubicTo(58f, 38f, 45f, 29f, 39f, 26f); quadraticTo(34f, 39f, 27f, 43f)
            }
            PortraitHair.CREW -> {
                moveTo(27f, 42f); lineTo(27f, 26f); quadraticTo(50f, 14f, 73f, 26f)
                lineTo(73f, 42f); lineTo(67f, 32f); lineTo(60f, 30f); lineTo(54f, 32f)
                lineTo(47f, 29f); lineTo(39f, 31f); lineTo(33f, 31f)
            }
            PortraitHair.WAVES -> {
                moveTo(26f, 43f); cubicTo(15f, 28f, 27f, 12f, 41f, 17f)
                cubicTo(48f, 7f, 66f, 12f, 67f, 17f); cubicTo(82f, 19f, 78f, 34f, 72f, 43f)
                lineTo(65f, 32f); cubicTo(58f, 36f, 54f, 26f, 45f, 30f)
                quadraticTo(35f, 23f, 26f, 43f)
            }
            PortraitHair.BOB -> {
                moveTo(24f, 53f); cubicTo(21f, 27f, 29f, 14f, 50f, 16f)
                cubicTo(75f, 14f, 80f, 32f, 76f, 54f); lineTo(68f, 45f); lineTo(65f, 29f)
                quadraticTo(50f, 37f, 35f, 28f); lineTo(32f, 45f)
            }
            PortraitHair.BUN, PortraitHair.PONYTAIL -> {
                moveTo(26f, 44f); cubicTo(22f, 23f, 34f, 13f, 52f, 14f)
                cubicTo(76f, 15f, 79f, 33f, 73f, 46f); lineTo(67f, 31f)
                quadraticTo(52f, 35f, 39f, 25f); quadraticTo(35f, 34f, 26f, 44f)
            }
            PortraitHair.BRAID -> {
                moveTo(26f, 44f); cubicTo(20f, 19f, 37f, 13f, 50f, 16f)
                cubicTo(63f, 13f, 80f, 19f, 74f, 44f); lineTo(66f, 33f)
                quadraticTo(56f, 31f, 50f, 23f); quadraticTo(44f, 31f, 34f, 33f)
            }
            PortraitHair.CURLS -> {
                moveTo(25f, 42f); lineTo(25f, 26f); quadraticTo(50f, 10f, 75f, 26f)
                lineTo(75f, 42f); lineTo(66f, 30f); quadraticTo(50f, 39f, 34f, 30f)
            }
            PortraitHair.RECEDING -> {
                moveTo(28f, 47f); cubicTo(20f, 30f, 27f, 18f, 38f, 23f)
                lineTo(34f, 33f); lineTo(33f, 49f); close()
                moveTo(72f, 47f); cubicTo(80f, 30f, 73f, 18f, 62f, 23f)
                lineTo(66f, 33f); lineTo(67f, 49f)
            }
            PortraitHair.BALD -> {
                moveTo(27f, 34f); quadraticTo(22f, 43f, 29f, 53f); lineTo(33f, 47f); lineTo(32f, 34f); close()
                moveTo(73f, 34f); quadraticTo(78f, 43f, 71f, 53f); lineTo(67f, 47f); lineTo(68f, 34f)
            }
            PortraitHair.POMPADOUR -> {
                moveTo(26f, 43f); cubicTo(16f, 25f, 25f, 9f, 44f, 13f)
                cubicTo(61f, 3f, 85f, 17f, 77f, 35f); lineTo(69f, 44f); lineTo(65f, 29f)
                cubicTo(54f, 37f, 43f, 24f, 35f, 32f)
            }
            PortraitHair.FRINGE -> {
                moveTo(25f, 45f); quadraticTo(21f, 15f, 51f, 15f)
                quadraticTo(78f, 13f, 75f, 43f); lineTo(65f, 33f); lineTo(61f, 39f)
                lineTo(52f, 29f); lineTo(46f, 38f); lineTo(39f, 30f); lineTo(33f, 41f)
            }
        }
        close()
    }
    drawPath(path, hair)
    if (s.hair == PortraitHair.CURLS) {
        for (at in listOf(Offset(28f, 29f), Offset(36f, 22f), Offset(49f, 22f), Offset(62f, 22f), Offset(72f, 29f))) {
            drawCircle(hair, 7.5f, at)
        }
    }
    if (s.hair == PortraitHair.SIDE_PART || s.hair == PortraitHair.POMPADOUR || s.hair == PortraitHair.WAVES) {
        val highlight = Path().apply { moveTo(37f, 23f); quadraticTo(49f, 18f, 63f, 24f) }
        drawPath(highlight, lerp(hair, DdzColors.Cream, 0.22f), style = Stroke(1.3f, cap = StrokeCap.Round))
    }
}

private fun DrawScope.drawPortraitFeatures(s: PortraitSpec) {
    val ink = DdzColors.Ink
    val hair = Color(s.hairColor)
    val spread = when (s.face) { PortraitFace.LONG -> 7.5f; PortraitFace.BROAD -> 11f; else -> 9f }
    val left = 50f - spread
    val right = 50f + spread
    val mouthY = if (s.face == PortraitFace.LONG) 62f else 59f
    drawLine(hair, Offset(left - 3.2f, 38.7f), Offset(left + 2.6f, 38f), 1.7f, StrokeCap.Round)
    drawLine(hair, Offset(right - 2.6f, 38f), Offset(right + 3.2f, 38.7f), 1.7f, StrokeCap.Round)
    drawCircle(ink, 1.65f, Offset(left, 45f)); drawCircle(ink, 1.65f, Offset(right, 45f))
    drawCircle(Color(0xFFC77B6C).copy(alpha = 0.14f), 4.5f, Offset(left - 3f, 53f))
    drawCircle(Color(0xFFC77B6C).copy(alpha = 0.14f), 4.5f, Offset(right + 3f, 53f))
    val nose = Path().apply { moveTo(50f, 46f); lineTo(48.2f, 52f); quadraticTo(50f, 53f, 52f, 52f) }
    drawPath(nose, Color(0xFFA77958), style = Stroke(1.2f, cap = StrokeCap.Round))
    if (s.senior) {
        for (y in listOf(31.5f, 34f)) drawLine(Color(0xFFA98768).copy(alpha = 0.6f), Offset(43f, y), Offset(57f, y), 0.8f, StrokeCap.Round)
        drawLine(Color(0xFFA98768), Offset(left - 6f, 47f), Offset(left - 3.5f, 49f), 0.8f, StrokeCap.Round)
        drawLine(Color(0xFFA98768), Offset(right + 3.5f, 49f), Offset(right + 6f, 47f), 0.8f, StrokeCap.Round)
    }
    when (s.beard) {
        PortraitBeard.FULL -> {
            val beard = Path().apply {
                moveTo(30f, 53f); quadraticTo(38f, 62f, 43f, 57f); lineTo(57f, 57f)
                quadraticTo(64f, 63f, 70f, 53f); quadraticTo(70f, 73f, 50f, 79f)
                quadraticTo(30f, 73f, 30f, 53f); close()
            }
            drawPath(beard, hair)
            for (x in listOf(42f, 49f, 57f)) drawLine(lerp(hair, DdzColors.Cream, 0.28f), Offset(x, 67f), Offset(x + 1f, 73f), 1.1f, StrokeCap.Round)
        }
        PortraitBeard.GOATEE -> drawOval(hair, Offset(45f, mouthY + 3f), Size(10f, 10f))
        PortraitBeard.STUBBLE -> {
            for (at in listOf(Offset(36f, 58f), Offset(37f, 63f), Offset(42f, 67f), Offset(49f, 69f), Offset(57f, 67f), Offset(63f, 63f), Offset(64f, 58f))) {
                drawCircle(hair.copy(alpha = 0.48f), 0.8f, at)
            }
        }
        else -> Unit
    }
    if (s.beard == PortraitBeard.MOUSTACHE || s.beard == PortraitBeard.GOATEE || s.beard == PortraitBeard.FULL) {
        val moustache = Path().apply {
            moveTo(39f, 55f); quadraticTo(45f, 51f, 50f, 55f)
            quadraticTo(55f, 51f, 61f, 55f); quadraticTo(58f, 60f, 50f, 57f)
            quadraticTo(42f, 60f, 39f, 55f); close()
        }
        drawPath(moustache, hair)
    }
    val smile = Path().apply { moveTo(44f, mouthY); quadraticTo(50f, mouthY + 4.4f, 56f, mouthY) }
    drawPath(smile, if (s.beard == PortraitBeard.FULL) DdzColors.Cream else Color(0xFF956451), style = Stroke(1.65f, cap = StrokeCap.Round))
    when (s.glasses) {
        PortraitGlasses.ROUND -> {
            drawCircle(ink, 6.3f, Offset(left, 45f), style = Stroke(1.5f))
            drawCircle(ink, 6.3f, Offset(right, 45f), style = Stroke(1.5f))
            drawLine(ink, Offset(left + 6.3f, 43f), Offset(right - 6.3f, 43f), 1.4f)
        }
        PortraitGlasses.RECTANGLE -> {
            drawRoundRect(ink, Offset(left - 6.5f, 39.5f), Size(13f, 11f), CornerRadius(2.3f), style = Stroke(1.5f))
            drawRoundRect(ink, Offset(right - 6.5f, 39.5f), Size(13f, 11f), CornerRadius(2.3f), style = Stroke(1.5f))
            drawLine(ink, Offset(left + 6.5f, 43f), Offset(right - 6.5f, 43f), 1.4f)
        }
        PortraitGlasses.HALF_FRAME -> {
            for (x in listOf(left, right)) {
                drawArc(ink, 0f, 180f, false, Offset(x - 6.5f, 39f), Size(13f, 12f), style = Stroke(1.5f))
                drawLine(ink, Offset(x - 6.5f, 45f), Offset(x + 6.5f, 45f), 1.4f)
            }
            drawLine(ink, Offset(left + 6.5f, 44f), Offset(right - 6.5f, 44f), 1.4f)
        }
        PortraitGlasses.NONE -> Unit
    }
}

private fun DrawScope.drawPortraitHat(s: PortraitSpec) {
    val hat = lerp(Color(s.coat), DdzColors.Cream, 0.12f)
    val band = lerp(hat, DdzColors.Ink, 0.25f)
    when (s.hat) {
        PortraitHat.FLAT_CAP -> {
            val cap = Path().apply {
                moveTo(21f, 31f); quadraticTo(28f, 9f, 55f, 13f)
                quadraticTo(73f, 16f, 80f, 30f); quadraticTo(49f, 35f, 21f, 31f); close()
            }
            drawPath(cap, hat)
            drawLine(band, Offset(22f, 32f), Offset(80f, 31f), 4f, StrokeCap.Round)
            drawLine(DdzColors.Gold.copy(alpha = 0.5f), Offset(29f, 27f), Offset(64f, 25f), 1f, StrokeCap.Round)
        }
        PortraitHat.WORK_CAP -> {
            drawRoundRect(hat, Offset(25f, 15f), Size(48f, 20f), CornerRadius(7f))
            drawRoundRect(band, Offset(23f, 31f), Size(58f, 6f), CornerRadius(2f))
            drawLine(DdzColors.Cream.copy(alpha = 0.55f), Offset(30f, 26f), Offset(68f, 26f), 1.1f)
            drawRoundRect(Color(s.accent), Offset(44f, 20f), Size(12f, 6f), CornerRadius(1f))
        }
        PortraitHat.BASEBALL -> {
            val cap = Path().apply { moveTo(23f, 34f); quadraticTo(23f, 8f, 49f, 12f); quadraticTo(75f, 9f, 76f, 34f); close() }
            drawPath(cap, hat)
            drawOval(band, Offset(54f, 31f), Size(33f, 7f))
            drawLine(band, Offset(50f, 15f), Offset(50f, 31f), 1.3f)
            drawCircle(Color(s.accent), 2f, Offset(49f, 12f))
        }
        PortraitHat.BERET -> {
            withTransform({ rotate(-9f, Offset(50f, 26f)) }) {
                drawOval(hat, Offset(18f, 11f), Size(65f, 27f))
                drawRoundRect(band, Offset(25f, 29f), Size(48f, 6f), CornerRadius(2f))
                drawLine(hat, Offset(56f, 11f), Offset(60f, 8f), 3f, StrokeCap.Round)
            }
        }
        PortraitHat.BOWLER -> {
            drawRoundRect(hat, Offset(29f, 9f), Size(44f, 28f), CornerRadius(15f))
            drawRect(band, Offset(29f, 27f), Size(44f, 7f))
            drawRoundRect(hat, Offset(20f, 33f), Size(64f, 6f), CornerRadius(3f))
        }
        PortraitHat.NONE -> Unit
    }
}
