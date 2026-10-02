package com.kaynzhang.doudizhu.ui.table

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kaynzhang.doudizhu.engine.game.Phase
import com.kaynzhang.doudizhu.engine.model.Rk
import com.kaynzhang.doudizhu.game.Controls
import com.kaynzhang.doudizhu.game.SeatUi
import com.kaynzhang.doudizhu.game.TableUi
import com.kaynzhang.doudizhu.ui.card.CardBack
import com.kaynzhang.doudizhu.ui.card.CardFace
import com.kaynzhang.doudizhu.ui.common.ButtonKind
import com.kaynzhang.doudizhu.ui.common.Chip
import com.kaynzhang.doudizhu.ui.common.GameButton
import com.kaynzhang.doudizhu.ui.common.formatCoins
import com.kaynzhang.doudizhu.ui.theme.DdzColors
import kotlin.math.roundToInt

/** Places the child so that its centre sits at ([x], [y]) in the parent's pixel space. */
fun Modifier.centerAt(x: Float, y: Float): Modifier = layout { measurable, constraints ->
    val p = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
    layout(p.width, p.height) { p.place((x - p.width / 2f).roundToInt(), (y - p.height / 2f).roundToInt()) }
}

fun Modifier.topLeftAt(x: Float, y: Float): Modifier = offset { IntOffset(x.roundToInt(), y.roundToInt()) }

@Composable
fun TopBar(
    ui: TableUi,
    g: TableGeometry,
    onBack: () -> Unit,
    onToggleCounter: () -> Unit,
    onToggleTrustee: () -> Unit,
) {
    val density = LocalDensity.current
    Row(
        Modifier
            .fillMaxWidth()
            .height(with(density) { g.topBarH.toDp() })
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Chip("‹ 大厅", onClick = onBack, tag = "btn_back")
        Spacer(Modifier.width(6.dp))
        Chip(ui.room.title)
        Spacer(Modifier.weight(1f))
        Chip("底分 ${ui.baseScore}  倍数 ×${ui.multiplier}", tag = "chip_multiplier")
        Spacer(Modifier.width(6.dp))
        Chip("记牌器", onClick = onToggleCounter, active = ui.counter != null, tag = "btn_counter")
        Spacer(Modifier.width(6.dp))
        Chip(if (ui.trustee) "取消托管" else "托管", onClick = onToggleTrustee, active = ui.trustee, tag = "btn_trustee")
    }
    val w = with(density) { g.bottomW.toDp() }
    val h = with(density) { g.bottomH.toDp() }
    ui.bottom.forEachIndexed { i, card ->
        val p = g.bottomSlot(i)
        val m = Modifier.topLeftAt(p.x, p.y).size(w, h)
        if (ui.bottomRevealed) CardFace(card, faceUp = true, modifier = m) else CardBack(m)
    }
}

private val COUNTER_ORDER = listOf(Rk.BJ, Rk.SJ, Rk.TWO, Rk.ACE, Rk.KING, Rk.QUEEN, Rk.JACK, Rk.TEN, Rk.NINE, Rk.EIGHT, Rk.SEVEN, Rk.SIX, Rk.FIVE, Rk.FOUR, Rk.THREE)

/** 记牌器: how many cards of each rank are still out of sight. */
@Composable
fun CounterStrip(counts: List<Int>, g: TableGeometry) {
    Row(
        Modifier
            .centerAt(g.w / 2, g.topBarH + g.counterH / 2)
            .background(DdzColors.Panel, RoundedCornerShape(10.dp))
            .border(1.dp, DdzColors.Gold.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
            .padding(horizontal = 6.dp, vertical = 1.dp),
    ) {
        for (r in COUNTER_ORDER) {
            Column(Modifier.width(25.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    when (r) { Rk.BJ -> "大"; Rk.SJ -> "小"; else -> Rk.label(r) },
                    color = DdzColors.Cream.copy(alpha = 0.75f), fontSize = 10.sp, lineHeight = 11.sp,
                )
                val n = counts[r]
                Text(
                    "$n", fontSize = 13.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold,
                    color = when {
                        n == 0 -> DdzColors.Cream.copy(alpha = 0.3f)
                        n == 4 || (r >= Rk.TWO && n > 0) -> DdzColors.Gold
                        else -> Color.White
                    },
                )
            }
        }
    }
}

@Composable
private fun Avatar(seat: SeatUi, size: Int, overlay: @Composable BoxScope.() -> Unit = {}) {
    // Only a seat that is thinking animates, so idle seats cost no frames.
    val ringAlpha = if (seat.thinking) {
        val pulse = rememberInfiniteTransition(label = "think")
        val ring by pulse.animateFloat(0.35f, 1f, infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "ring")
        ring
    } else {
        0.6f
    }
    Box {
        Box(
            Modifier
                .size(size.dp)
                .background(Brush.verticalGradient(listOf(Color(0xFF3E6B57), Color(0xFF1B3A2C))), CircleShape)
                .border(if (seat.thinking) 3.dp else 2.dp, DdzColors.Gold.copy(alpha = ringAlpha), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(seat.avatar, fontSize = (size * 0.52f).sp)
        }
        if (seat.isLandlord) {
            Text("👑", fontSize = (size * 0.36f).sp, modifier = Modifier.align(Alignment.TopEnd).offset(x = 6.dp, y = (-10).dp))
        }
        if (seat.doubled) {
            Text(
                "×2", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White,
                modifier = Modifier.align(Alignment.TopStart).offset(x = (-6).dp, y = (-2).dp)
                    .background(DdzColors.SuitRed, RoundedCornerShape(6.dp)).padding(horizontal = 4.dp),
            )
        }
        overlay()
    }
}

/** Cards left, as a badge on the avatar; turns red and shows 报单/报双 at one or two cards. */
@Composable
private fun CardCountBadge(cards: Int, alert: Boolean, modifier: Modifier) {
    val shape = RoundedCornerShape(8.dp)
    val background = if (alert) {
        val blink = rememberInfiniteTransition(label = "alert")
        val a by blink.animateFloat(0.45f, 1f, infiniteRepeatable(tween(450), RepeatMode.Reverse), label = "a")
        DdzColors.SuitRed.copy(alpha = a)
    } else {
        DdzColors.PanelStrong
    }
    Row(
        modifier
            .background(background, shape)
            .border(1.dp, DdzColors.Gold.copy(alpha = 0.6f), shape)
            .padding(horizontal = 4.dp, vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CardBack(Modifier.size(11.dp, 15.dp))
        Spacer(Modifier.width(3.dp))
        Text("$cards", color = Color.White, fontSize = 15.sp, lineHeight = 16.sp, fontWeight = FontWeight.Black)
        if (alert) {
            Spacer(Modifier.width(3.dp))
            Text(if (cards == 1) "报单" else "报双", color = Color.White, fontSize = 11.sp, lineHeight = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/**
 * An opponent: avatar with a cards-left badge on the side facing the table, then name and coins.
 * Kept short (about 86dp) so it clears the human's badge on 360dp-tall landscape phones.
 */
@Composable
fun SeatPanel(seat: SeatUi, g: TableGeometry, phase: Phase) {
    val density = LocalDensity.current
    val left = seat.seat == 2
    val x = if (left) g.margin else g.w - g.margin - g.seatW
    Column(
        Modifier
            .topLeftAt(x, g.seatTop)
            .width(with(density) { g.seatW.toDp() }),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Avatar(seat, 52) {
            CardCountBadge(
                seat.cards,
                alert = phase == Phase.PLAYING && seat.cards in 1..2,
                modifier = Modifier
                    .align(if (left) Alignment.BottomEnd else Alignment.BottomStart)
                    .offset(x = if (left) 18.dp else (-18).dp, y = 4.dp),
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(seat.name, color = DdzColors.Cream, fontSize = 13.sp, lineHeight = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Text("🪙" + formatCoins(seat.coins), color = DdzColors.Gold, fontSize = 11.sp, lineHeight = 13.sp, maxLines = 1)
    }
}

/** The human's own badge, left of the action buttons. */
@Composable
fun HumanPanel(seat: SeatUi, g: TableGeometry) {
    Row(
        Modifier.topLeftAt(g.margin, g.buttonsTop - g.dp(2f)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(seat, 42)
        Spacer(Modifier.width(8.dp))
        Column {
            Text(if (seat.isLandlord) "我 · 地主" else "我", color = DdzColors.Cream, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text("🪙" + formatCoins(seat.coins), color = DdzColors.Gold, fontSize = 12.sp)
        }
    }
}

/** Speech bubble in front of a seat, e.g. "抢地主" or "不出". */
@Composable
fun Bubble(text: String, g: TableGeometry, seat: Int) {
    val c = g.playCenter(seat)
    Box(
        Modifier
            .centerAt(c.x, c.y)
            .background(Color(0xF2FFF8E7), RoundedCornerShape(14.dp))
            .border(1.5.dp, DdzColors.GoldDeep, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 6.dp),
    ) {
        Text(text, color = Color(0xFF7A3B00), fontSize = 17.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun ActionBar(
    ui: TableUi,
    g: TableGeometry,
    onBid: (Boolean) -> Unit,
    onJiabei: (Boolean) -> Unit,
    onPass: () -> Unit,
    onHint: () -> Unit,
    onPlay: () -> Unit,
    onUntrustee: () -> Unit,
) {
    val density = LocalDensity.current
    Row(
        Modifier
            .fillMaxWidth()
            .topLeftAt(0f, g.buttonsTop)
            .height(with(density) { g.buttonsH.toDp() }),
        horizontalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (ui.controls) {
            Controls.CALL -> {
                GameButton("不叫", { onBid(false) }, kind = ButtonKind.NEUTRAL, tag = "btn_nocall")
                GameButton("叫地主", { onBid(true) }, tag = "btn_call")
            }
            Controls.ROB -> {
                GameButton("不抢", { onBid(false) }, kind = ButtonKind.NEUTRAL, tag = "btn_norob")
                GameButton("抢地主", { onBid(true) }, tag = "btn_rob")
            }
            Controls.JIABEI -> {
                GameButton("不加倍", { onJiabei(false) }, kind = ButtonKind.NEUTRAL, tag = "btn_nojiabei")
                GameButton("加倍", { onJiabei(true) }, kind = ButtonKind.SUCCESS, tag = "btn_jiabei")
            }
            Controls.LEAD -> {
                GameButton("提示", onHint, kind = ButtonKind.SECONDARY, tag = "btn_hint")
                GameButton("出牌", onPlay, enabled = ui.canPlay, tag = "btn_play")
            }
            Controls.FOLLOW -> {
                GameButton("不出", onPass, kind = ButtonKind.NEUTRAL, tag = "btn_pass")
                GameButton("提示", onHint, kind = ButtonKind.SECONDARY, tag = "btn_hint")
                GameButton("出牌", onPlay, enabled = ui.canPlay, tag = "btn_play")
            }
            Controls.CANNOT_BEAT -> GameButton("要不起", onPass, kind = ButtonKind.NEUTRAL, tag = "btn_cannot")
            Controls.NONE -> if (ui.trustee) GameButton("取消托管", onUntrustee, kind = ButtonKind.SECONDARY, tag = "btn_untrustee")
        }
    }
}
