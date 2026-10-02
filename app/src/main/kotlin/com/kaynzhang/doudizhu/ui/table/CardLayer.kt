package com.kaynzhang.doudizhu.ui.table

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import com.kaynzhang.doudizhu.engine.model.Card
import com.kaynzhang.doudizhu.engine.model.Rk
import com.kaynzhang.doudizhu.game.GameViewModel
import com.kaynzhang.doudizhu.game.TableUi
import com.kaynzhang.doudizhu.ui.card.CardFace
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** Where one card should be right now. NaN coordinates mean "stay where you are" (fading out). */
data class SpriteSpec(
    val card: Card,
    val x: Float,
    val y: Float,
    val scale: Float,
    val alpha: Float,
    val z: Float,
    /** Start position when the sprite first appears. */
    val fromX: Float,
    val fromY: Float,
    val fromAlpha: Float,
    val delayMs: Int,
    val shade: Float,
    val label: String? = null,
    val selected: Boolean = false,
)

fun buildSprites(ui: TableUi, g: TableGeometry, preview: IntRange?): List<SpriteSpec> {
    val out = ArrayList<SpriteSpec>(54)
    val dealStep = (GameViewModel.DEAL_MS * 0.75 / 17).toInt()

    ui.gone.forEach { c ->
        out += SpriteSpec(c, Float.NaN, Float.NaN, g.tableScale, 0f, 1f, g.deck.x, g.deck.y, 0f, 0, 0f)
    }

    for (seat in ui.displays.indices) {
        val cards = ui.displays[seat]
        val from = g.seatAnchor(seat)
        cards.forEachIndexed { i, c ->
            val p = g.playSlot(seat, i, cards.size)
            out += SpriteSpec(c, p.x, p.y, g.tableScale, 1f, 100f + i, from.x, from.y, 0f, 0, 0f)
        }
    }

    val n = ui.hand.size
    ui.hand.forEachIndexed { i, c ->
        val sel = c in ui.selected
        val p = g.handSlot(i, n, sel)
        val bottomIndex = ui.bottom.indexOf(c)
        val from = if (!ui.dealing && bottomIndex >= 0) g.bottomSlot(bottomIndex) else g.deck
        out += SpriteSpec(
            card = c, x = p.x, y = p.y, scale = 1f, alpha = 1f, z = 200f + i,
            fromX = from.x, fromY = from.y, fromAlpha = if (ui.dealing) 0f else 1f,
            delayMs = if (ui.dealing) (n - 1 - i) * dealStep else 0,
            shade = if (preview != null && i in preview) 0.28f else 0f,
            label = describe(c), selected = sel,
        )
    }
    return out
}

private fun describe(c: Card): String =
    if (c.isJoker) Rk.label(c.rank) else listOf("黑桃", "红桃", "梅花", "方块")[c.suit] + Rk.label(c.rank)

/** All visible cards, each an independently animated sprite keyed by card identity. */
@Composable
fun CardLayer(specs: List<SpriteSpec>, g: TableGeometry) {
    Box(Modifier.fillMaxSize()) {
        for (spec in specs) {
            key(spec.card.bit) { CardSprite(spec, g) }
        }
    }
}

@Composable
private fun CardSprite(spec: SpriteSpec, g: TableGeometry) {
    val pos = remember { Animatable(Offset(spec.fromX, spec.fromY), Offset.VectorConverter) }
    val alpha = remember { Animatable(spec.fromAlpha) }
    var first by remember { mutableStateOf(true) }
    val scale by animateFloatAsState(spec.scale, tween(260), label = "scale")

    LaunchedEffect(spec.x, spec.y) {
        if (spec.x.isNaN()) return@LaunchedEffect
        val entering = first
        first = false
        if (entering && spec.delayMs > 0) delay(spec.delayMs.toLong())
        pos.animateTo(Offset(spec.x, spec.y), tween(if (entering) 360 else 220, easing = FastOutSlowInEasing))
    }
    LaunchedEffect(spec.alpha) {
        if (first && spec.delayMs > 0) delay(spec.delayMs.toLong())
        alpha.animateTo(spec.alpha, tween(if (spec.alpha == 0f) 380 else 200))
    }

    val density = LocalDensity.current
    val wDp = with(density) { g.cardW.toDp() }
    val hDp = with(density) { g.cardH.toDp() }
    Box(
        Modifier
            .zIndex(spec.z)
            .offset { IntOffset(pos.value.x.roundToInt(), pos.value.y.roundToInt()) }
            .graphicsLayer {
                this.alpha = alpha.value
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin(0f, 0f)
            }
            .size(wDp, hDp)
            .then(
                if (spec.label != null) {
                    Modifier.semantics {
                        contentDescription = spec.label + if (spec.selected) "，已选中" else ""
                        selected = spec.selected
                    }
                } else {
                    Modifier
                },
            ),
    ) {
        CardFace(spec.card, faceUp = true, modifier = Modifier.fillMaxSize(), shade = spec.shade, selected = spec.selected)
    }
}
