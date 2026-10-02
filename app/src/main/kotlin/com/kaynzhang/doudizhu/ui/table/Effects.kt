package com.kaynzhang.doudizhu.ui.table

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kaynzhang.doudizhu.engine.rules.ComboType
import com.kaynzhang.doudizhu.game.TableEffect
import com.kaynzhang.doudizhu.ui.common.LineIcon
import com.kaynzhang.doudizhu.ui.common.UiIcon
import com.kaynzhang.doudizhu.ui.theme.DdzColors
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

private sealed interface Fx {
    val id: Int

    data class Banner(override val id: Int, val text: String, val x: Float, val y: Float, val color: Color, val big: Boolean, val crown: Boolean = false) : Fx
    data class Flash(override val id: Int, val x: Float, val y: Float) : Fx
    data class Rocket(override val id: Int, val x: Float, val y: Float) : Fx
    data class Plane(override val id: Int, val y: Float) : Fx
}

/**
 * Short-lived celebratory effects driven by [TableEffect]s: combo banners, the bomb rings and
 * table shake, the rocket, the plane and the 春天 banner. [shake] is the table's x-translation.
 */
@Composable
fun EffectsLayer(effects: Flow<TableEffect>, g: TableGeometry, shake: Animatable<Float, *>) {
    val active = remember { mutableStateListOf<Fx>() }
    val nextId = remember { mutableIntStateOf(0) }
    LaunchedEffect(effects, g) {
        effects.collect { e ->
            fun id() = nextId.intValue++
            when (e) {
                is TableEffect.ComboPlayed -> {
                    val c = g.playCenter(e.seat)
                    when (e.combo.type) {
                        ComboType.BOMB -> {
                            active += Fx.Flash(id(), c.x, c.y)
                            active += Fx.Banner(id(), "炸弹", c.x, c.y, DdzColors.Gold, big = true)
                            launch { shakeTable(shake) }
                        }
                        ComboType.ROCKET -> {
                            active += Fx.Rocket(id(), c.x, c.y)
                            active += Fx.Flash(id(), c.x, c.y)
                            active += Fx.Banner(id(), "王炸", c.x, c.y, DdzColors.Gold, big = true)
                            launch { shakeTable(shake) }
                        }
                        ComboType.PLANE, ComboType.PLANE_SINGLES, ComboType.PLANE_PAIRS -> {
                            active += Fx.Plane(id(), g.h * 0.42f)
                            active += Fx.Banner(id(), "飞机", c.x, c.y, DdzColors.Gold, big = false)
                        }
                        ComboType.STRAIGHT -> active += Fx.Banner(id(), "顺子", c.x, c.y, DdzColors.Gold, big = false)
                        ComboType.PAIR_STRAIGHT -> active += Fx.Banner(id(), "连对", c.x, c.y, DdzColors.Gold, big = false)
                        ComboType.FOUR_TWO_SINGLES, ComboType.FOUR_TWO_PAIRS ->
                            active += Fx.Banner(id(), e.combo.type.zh, c.x, c.y, DdzColors.Gold, big = false)
                        else -> {}
                    }
                }
                is TableEffect.LandlordChosen -> {
                    // The human's own row is about to show the 加倍 buttons: announce it mid-table instead.
                    val c = if (e.seat == 0) Offset(g.w / 2, g.messageY) else g.playCenter(e.seat)
                    active += Fx.Banner(id(), "地主已定", c.x, c.y, DdzColors.Gold, big = false, crown = true)
                }
                is TableEffect.GameOver -> {
                    if (e.result.spring || e.result.antiSpring) {
                        active += Fx.Banner(id(), if (e.result.spring) "春天" else "反春", g.w / 2, g.messageY, DdzColors.Gold, big = true)
                    }
                }
            }
        }
    }
    for (fx in active) {
        key(fx.id) { FxView(fx, g) { active.remove(fx) } }
    }
}

private suspend fun shakeTable(shake: Animatable<Float, *>) {
    for (i in 0 until 4) shake.animateTo(if (i % 2 == 0) 5f else -5f, tween(45))
    shake.animateTo(0f, tween(90))
}

@Composable
private fun FxView(fx: Fx, g: TableGeometry, done: () -> Unit) {
    when (fx) {
        is Fx.Banner -> {
            val scale = remember { Animatable(0.9f) }
            val alpha = remember { Animatable(1f) }
            LaunchedEffect(Unit) {
                scale.animateTo(1f, tween(180, easing = FastOutSlowInEasing))
                alpha.animateTo(0f, tween(450, delayMillis = if (fx.big) 700 else 450))
                done()
            }
            val shape = RoundedCornerShape(10.dp)
            Row(
                Modifier.centerAt(fx.x, fx.y).graphicsLayer {
                        scaleX = scale.value
                        scaleY = scale.value
                        this.alpha = alpha.value
                    }
                    .background(DdzColors.PanelStrong.copy(alpha = 0.95f), shape)
                    .border(0.5.dp, fx.color.copy(alpha = 0.55f), shape)
                    .padding(horizontal = if (fx.big) 24.dp else 18.dp, vertical = if (fx.big) 10.dp else 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (fx.crown) LineIcon(UiIcon.CROWN, Modifier.size(25.dp), color = fx.color)
                Text(
                    fx.text,
                    style = TextStyle(color = fx.color, fontSize = if (fx.big) 36.sp else 26.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 2.sp),
                )
            }
        }
        is Fx.Flash -> {
            val t = remember { Animatable(0f) }
            LaunchedEffect(Unit) {
                t.animateTo(1f, tween(520, easing = FastOutSlowInEasing))
                done()
            }
            Canvas(Modifier.fillMaxSize()) {
                val center = Offset(fx.x, fx.y)
                val radius = g.dp(32f) + t.value * g.h * 0.38f
                val opacity = (1f - t.value) * 0.55f
                drawCircle(
                    Brush.radialGradient(listOf(DdzColors.Gold.copy(alpha = opacity * 0.3f), Color.Transparent), center = center, radius = radius),
                    radius = radius, center = center,
                )
                drawCircle(DdzColors.Gold.copy(alpha = opacity), radius, center, style = Stroke(g.dp(1.2f)))
                drawCircle(DdzColors.Cream.copy(alpha = opacity * 0.65f), radius * 0.78f, center, style = Stroke(g.dp(0.6f)))
            }
        }
        is Fx.Rocket -> {
            val t = remember { Animatable(0f) }
            LaunchedEffect(Unit) {
                t.animateTo(1f, tween(750, easing = FastOutSlowInEasing))
                done()
            }
            Canvas(
                Modifier.centerAt(fx.x, fx.y).size(44.dp, 94.dp).graphicsLayer {
                    translationY = -t.value * g.h * 0.7f
                    alpha = 1f - t.value * 0.6f
                },
            ) {
                val w = size.width
                val h = size.height
                val body = Path().apply {
                    moveTo(w * 0.5f, 0f)
                    cubicTo(w * 0.28f, h * 0.18f, w * 0.31f, h * 0.38f, w * 0.34f, h * 0.56f)
                    lineTo(w * 0.66f, h * 0.56f)
                    cubicTo(w * 0.69f, h * 0.38f, w * 0.72f, h * 0.18f, w * 0.5f, 0f)
                    close()
                }
                val fins = Path().apply {
                    moveTo(w * 0.34f, h * 0.35f)
                    lineTo(w * 0.1f, h * 0.62f)
                    lineTo(w * 0.36f, h * 0.54f)
                    moveTo(w * 0.66f, h * 0.35f)
                    lineTo(w * 0.9f, h * 0.62f)
                    lineTo(w * 0.64f, h * 0.54f)
                }
                drawPath(fins, DdzColors.Gold.copy(alpha = 0.65f), style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round))
                drawPath(body, DdzColors.Cream)
                drawCircle(DdzColors.FeltEdge, w * 0.085f, Offset(w * 0.5f, h * 0.29f))
                drawCircle(DdzColors.Gold, w * 0.045f, Offset(w * 0.5f, h * 0.29f))
                drawLine(DdzColors.Gold.copy(alpha = 0.8f), Offset(w * 0.5f, h * 0.61f), Offset(w * 0.5f, h), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
                drawLine(DdzColors.Gold.copy(alpha = 0.3f), Offset(w * 0.37f, h * 0.65f), Offset(w * 0.37f, h * 0.85f), strokeWidth = 1.dp.toPx(), cap = StrokeCap.Round)
                drawLine(DdzColors.Gold.copy(alpha = 0.3f), Offset(w * 0.63f, h * 0.65f), Offset(w * 0.63f, h * 0.85f), strokeWidth = 1.dp.toPx(), cap = StrokeCap.Round)
            }
        }
        is Fx.Plane -> {
            val t = remember { Animatable(0f) }
            LaunchedEffect(Unit) {
                t.animateTo(1f, tween(1_300, easing = LinearEasing))
                done()
            }
            Canvas(
                Modifier.centerAt(0f, fx.y).size(90.dp, 40.dp).graphicsLayer {
                    translationX = -g.w * 0.1f + t.value * g.w * 1.2f
                    translationY = -t.value * g.h * 0.08f
                    alpha = 0.9f
                },
            ) {
                val w = size.width
                val h = size.height
                val plane = Path().apply {
                    moveTo(w, h * 0.46f)
                    lineTo(w * 0.53f, h * 0.38f)
                    lineTo(w * 0.32f, 0f)
                    lineTo(w * 0.23f, 0f)
                    lineTo(w * 0.37f, h * 0.4f)
                    lineTo(w * 0.13f, h * 0.42f)
                    lineTo(w * 0.03f, h * 0.19f)
                    lineTo(0f, h * 0.19f)
                    lineTo(w * 0.03f, h * 0.5f)
                    lineTo(0f, h * 0.81f)
                    lineTo(w * 0.03f, h * 0.81f)
                    lineTo(w * 0.13f, h * 0.58f)
                    lineTo(w * 0.37f, h * 0.6f)
                    lineTo(w * 0.23f, h)
                    lineTo(w * 0.32f, h)
                    lineTo(w * 0.53f, h * 0.62f)
                    lineTo(w, h * 0.54f)
                    close()
                }
                drawPath(plane, DdzColors.Gold)
                drawLine(DdzColors.Cream.copy(alpha = 0.8f), Offset(w * 0.2f, h * 0.5f), Offset(w * 0.87f, h * 0.5f), strokeWidth = 0.8.dp.toPx())
            }
        }
    }
}
