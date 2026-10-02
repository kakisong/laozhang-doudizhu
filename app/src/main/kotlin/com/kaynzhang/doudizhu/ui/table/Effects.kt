package com.kaynzhang.doudizhu.ui.table

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.kaynzhang.doudizhu.engine.rules.ComboType
import com.kaynzhang.doudizhu.game.TableEffect
import com.kaynzhang.doudizhu.ui.theme.DdzColors
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

private sealed interface Fx {
    val id: Int

    data class Banner(override val id: Int, val text: String, val x: Float, val y: Float, val color: Color, val big: Boolean) : Fx
    data class Flash(override val id: Int) : Fx
    data class Rocket(override val id: Int, val x: Float, val y: Float) : Fx
    data class Plane(override val id: Int, val y: Float) : Fx
}

/**
 * Short-lived celebratory effects driven by [TableEffect]s: combo banners, the bomb flash and
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
                            active += Fx.Flash(id())
                            active += Fx.Banner(id(), "炸弹", c.x, c.y, DdzColors.SuitRed, big = true)
                            launch { shakeTable(shake) }
                        }
                        ComboType.ROCKET -> {
                            active += Fx.Rocket(id(), c.x, c.y)
                            active += Fx.Flash(id())
                            active += Fx.Banner(id(), "王炸", c.x, c.y, DdzColors.SuitRed, big = true)
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
                    val c = g.playCenter(e.seat)
                    active += Fx.Banner(id(), "👑 地主", c.x, c.y, DdzColors.Gold, big = false)
                }
                is TableEffect.GameOver -> {
                    if (e.result.spring || e.result.antiSpring) {
                        active += Fx.Banner(id(), if (e.result.spring) "春天！" else "反春！", g.w / 2, g.h * 0.38f, DdzColors.Win, big = true)
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
    for (i in 0 until 6) shake.animateTo(if (i % 2 == 0) 14f else -14f, tween(45))
    shake.animateTo(0f, tween(60))
}

@Composable
private fun FxView(fx: Fx, g: TableGeometry, done: () -> Unit) {
    when (fx) {
        is Fx.Banner -> {
            val scale = remember { Animatable(0.4f) }
            val alpha = remember { Animatable(1f) }
            LaunchedEffect(Unit) {
                scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                alpha.animateTo(0f, tween(450, delayMillis = if (fx.big) 700 else 450))
                done()
            }
            Text(
                fx.text,
                modifier = Modifier.centerAt(fx.x, fx.y).graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                    this.alpha = alpha.value
                },
                style = TextStyle(
                    color = fx.color,
                    fontSize = if (fx.big) 52.sp else 34.sp,
                    fontWeight = FontWeight.Black,
                    shadow = Shadow(Color(0xCC000000), blurRadius = 12f),
                ),
            )
        }
        is Fx.Flash -> {
            val alpha = remember { Animatable(0.75f) }
            LaunchedEffect(Unit) {
                alpha.animateTo(0f, tween(380))
                done()
            }
            Box(Modifier.fillMaxSize().graphicsLayer { this.alpha = alpha.value }.background(Color(0xFFFFF3C4)))
        }
        is Fx.Rocket -> {
            val t = remember { Animatable(0f) }
            LaunchedEffect(Unit) {
                t.animateTo(1f, tween(750, easing = FastOutSlowInEasing))
                done()
            }
            Text(
                "🚀",
                fontSize = 64.sp,
                modifier = Modifier.centerAt(fx.x, fx.y).graphicsLayer {
                    translationY = -t.value * g.h * 0.7f
                    rotationZ = -45f
                    alpha = 1f - t.value * 0.6f
                },
            )
        }
        is Fx.Plane -> {
            val t = remember { Animatable(0f) }
            LaunchedEffect(Unit) {
                t.animateTo(1f, tween(1_300, easing = LinearEasing))
                done()
            }
            Text(
                "✈️",
                fontSize = 72.sp,
                modifier = Modifier.centerAt(0f, fx.y).graphicsLayer {
                    translationX = -g.w * 0.1f + t.value * g.w * 1.2f
                    translationY = -t.value * g.h * 0.08f
                },
            )
        }
    }
}
