package com.kaynzhang.doudizhu.ui.table

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import com.kaynzhang.doudizhu.ui.common.GameButton
import com.kaynzhang.doudizhu.ui.common.LineIcon
import com.kaynzhang.doudizhu.ui.common.PlayerPortrait
import com.kaynzhang.doudizhu.ui.common.UiIcon
import com.kaynzhang.doudizhu.ui.common.formatCoins
import com.kaynzhang.doudizhu.ui.theme.DdzColors
import com.kaynzhang.doudizhu.ui.common.LocalUiSound
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
    onPause: () -> Unit,
    onToggleTrustee: () -> Unit,
) {
    val density = LocalDensity.current
    Row(
        Modifier
            .fillMaxWidth()
            .height(with(density) { g.topBarH.toDp() })
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TableTool("大厅", UiIcon.BACK, "btn_back", onClick = onBack)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.testTag("chip_multiplier")) {
            if (g.w >= g.dp(700f)) {
                Text(ui.room.title, color = DdzColors.Cream.copy(alpha = 0.6f), fontSize = 12.sp, lineHeight = 15.sp)
            }
            Text("倍数 ×${ui.multiplier}", color = DdzColors.Gold, fontSize = 16.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium)
        }
        Spacer(Modifier.weight(1f))
        TableTool(if (g.w < g.dp(620f)) "记牌" else "记牌器", UiIcon.CARDS, "btn_counter", active = ui.counter != null, onClick = onToggleCounter)
        Spacer(Modifier.width(4.dp))
        TableTool("暂停", UiIcon.PAUSE, "btn_pause", active = ui.paused, enabled = ui.phase != Phase.FINISHED, onClick = onPause)
        Spacer(Modifier.width(4.dp))
        TableMoreMenu(ui, onToggleTrustee)
    }
    val w = with(density) { g.bottomW.toDp() }
    val h = with(density) { g.bottomH.toDp() }
    ui.bottom.forEachIndexed { i, card ->
        val p = g.bottomSlot(i)
        val m = Modifier.topLeftAt(p.x, p.y).size(w, h)
        if (ui.bottomRevealed) CardFace(card, faceUp = true, modifier = m, jumbo = true) else CardBack(m)
    }
}

/** Quiet toolbar controls retain a full-height tap target and a visible selected state. */
@Composable
private fun TableTool(text: String, icon: UiIcon, tag: String, active: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    val clickSound = LocalUiSound.current
    val haptics = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val ink = when {
        !enabled -> DdzColors.Cream.copy(alpha = 0.35f)
        active -> DdzColors.Gold
        else -> DdzColors.Cream.copy(alpha = 0.84f)
    }
    Row(
        Modifier
            .testTag(tag)
            .heightIn(min = 48.dp)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, enabled = enabled) {
                haptics.performHapticFeedback(HapticFeedbackType.VirtualKey)
                clickSound()
                onClick()
            }
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(
            Modifier.size(26.dp).background(if (active) DdzColors.Gold.copy(alpha = 0.12f) else Color.Transparent, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center,
        ) {
            LineIcon(icon, Modifier.size(18.dp), color = ink)
        }
        Text(text, color = ink, fontSize = 16.sp, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium, maxLines = 1)
    }
}

/** Occasional computer assistance stays one tap below the primary table controls. */
@Composable
private fun TableMoreMenu(ui: TableUi, onToggleTrustee: () -> Unit) {
    val clickSound = LocalUiSound.current
    var expanded by remember { mutableStateOf(false) }
    val available = ui.phase != Phase.FINISHED && !ui.paused && !ui.showResult
    LaunchedEffect(available) {
        if (!available) expanded = false
    }
    val haptics = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    Box {
        Box(
            Modifier.size(48.dp)
                .testTag("btn_table_more")
                .semantics { contentDescription = "更多功能" }
                .clickable(interactionSource = interaction, indication = null, role = Role.Button, enabled = available) {
                    haptics.performHapticFeedback(HapticFeedbackType.VirtualKey)
                    clickSound()
                    expanded = true
                },
            contentAlignment = Alignment.Center,
        ) {
            LineIcon(UiIcon.MORE, Modifier.size(22.dp), color = if (expanded) DdzColors.Gold else DdzColors.Cream.copy(alpha = 0.84f))
        }
        DropdownMenu(
            expanded = expanded && available,
            onDismissRequest = { expanded = false },
            modifier = Modifier.width(224.dp),
            shape = RoundedCornerShape(12.dp),
            containerColor = DdzColors.PanelStrong,
            tonalElevation = 0.dp,
            shadowElevation = 4.dp,
            border = BorderStroke(1.dp, DdzColors.Hairline),
        ) {
            DropdownMenuItem(
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(if (ui.trustee) "停止代打" else "电脑代打", color = DdzColors.Cream, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                        Text(if (ui.trustee) "恢复自己出牌" else "临时交给电脑出牌", color = DdzColors.TextMuted, fontSize = 13.sp, lineHeight = 18.sp)
                    }
                },
                leadingIcon = {
                    LineIcon(if (ui.trustee) UiIcon.CHECK else UiIcon.CARDS, Modifier.size(20.dp), color = DdzColors.Gold)
                },
                modifier = Modifier.testTag("btn_trustee").heightIn(min = 64.dp),
                enabled = ui.phase != Phase.FINISHED,
                onClick = {
                    expanded = false
                    haptics.performHapticFeedback(HapticFeedbackType.VirtualKey)
                    clickSound()
                    onToggleTrustee()
                },
            )
        }
    }
}

private val COUNTER_ORDER = listOf(Rk.BJ, Rk.SJ, Rk.TWO, Rk.ACE, Rk.KING, Rk.QUEEN, Rk.JACK, Rk.TEN, Rk.NINE, Rk.EIGHT, Rk.SEVEN, Rk.SIX, Rk.FIVE, Rk.FOUR, Rk.THREE)

/** 记牌器: how many cards of each rank are still out of sight. */
@Composable
fun CounterStrip(counts: List<Int>, g: TableGeometry) {
    val colW = with(LocalDensity.current) { g.counterColW.toDp() }
    Row(
        Modifier
            .centerAt(g.w / 2, g.topBarH + g.counterH / 2)
            .background(DdzColors.PanelStrong.copy(alpha = 0.8f), RoundedCornerShape(8.dp))
            .border(0.5.dp, DdzColors.Cream.copy(alpha = 0.13f), RoundedCornerShape(8.dp))
            .padding(horizontal = 6.dp, vertical = 1.dp),
    ) {
        for (r in COUNTER_ORDER) {
            Column(Modifier.width(colW), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    when (r) { Rk.BJ -> "大"; Rk.SJ -> "小"; else -> Rk.label(r) },
                    color = DdzColors.Cream.copy(alpha = 0.68f), fontSize = 12.sp, lineHeight = 14.sp, maxLines = 1,
                )
                val n = counts[r]
                Text(
                    "$n", fontSize = 17.sp, lineHeight = 19.sp, fontWeight = FontWeight.SemiBold,
                    color = when {
                        n == 0 -> DdzColors.Cream.copy(alpha = 0.35f)
                        n == 4 || (r >= Rk.TWO && n > 0) -> DdzColors.Gold
                        else -> DdzColors.Cream
                    },
                )
            }
        }
    }
}

@Composable
internal fun RoleAvatar(
    seat: SeatUi,
    size: Int,
    roleKnown: Boolean,
    showDoubled: Boolean = true,
    overlay: @Composable BoxScope.() -> Unit = {},
) {
    val landlord = roleKnown && seat.isLandlord
    // Only a seat that is thinking animates, so idle seats cost no frames.
    val ringAlpha = if (seat.thinking) {
        val pulse = rememberInfiniteTransition(label = "think")
        val ring by pulse.animateFloat(if (landlord) 0.8f else 0.4f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "ring")
        ring
    } else {
        if (landlord) 1f else 0.25f
    }
    Box {
        Box(
            Modifier
                .size(size.dp)
                .background(if (landlord) DdzColors.Gold.copy(alpha = 0.15f) else Color.Transparent, CircleShape)
                .border(
                    if (landlord) 2.5.dp else if (seat.thinking) 1.5.dp else 1.dp,
                    (if (landlord) DdzColors.Gold else DdzColors.Cream).copy(alpha = ringAlpha),
                    CircleShape,
                )
                .padding(3.dp),
            contentAlignment = Alignment.Center,
        ) {
            PlayerPortrait(seat.avatar, Modifier.size((size - 6).dp))
        }
        if (landlord) {
            val crownSize = (size * 0.44f).coerceIn(16f, 23f)
            Box(
                Modifier.align(Alignment.TopEnd).offset(x = 4.dp, y = (-3).dp)
                    .size(crownSize.dp).background(DdzColors.Gold, CircleShape)
                    .border(1.dp, DdzColors.PanelStrong, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                LineIcon(UiIcon.CROWN, Modifier.size((crownSize * 0.66f).dp), color = DdzColors.Ink)
            }
        }
        if (showDoubled && seat.doubled) {
            Text(
                "×2", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = DdzColors.Gold,
                modifier = Modifier.align(Alignment.TopStart).offset(x = (-6).dp, y = (-2).dp)
                    .background(DdzColors.PanelStrong, RoundedCornerShape(5.dp)).padding(horizontal = 4.dp),
            )
        }
        overlay()
    }
}

/** A written role accompanies the avatar treatment, so the distinction never relies on colour. */
@Composable
internal fun RoleBadge(isLandlord: Boolean, roleKnown: Boolean = true) {
    val landlord = roleKnown && isLandlord
    val shape = RoundedCornerShape(4.dp)
    Box(
        Modifier
            .background(if (landlord) DdzColors.Gold else DdzColors.PanelStrong, shape)
            .border(0.5.dp, if (landlord) DdzColors.Gold else DdzColors.Cream.copy(alpha = 0.3f), shape)
            .padding(horizontal = 4.dp, vertical = 1.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            when { !roleKnown -> "待定"; isLandlord -> "地主"; else -> "农民" },
            color = if (landlord) DdzColors.Ink else DdzColors.Cream,
            fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
        )
    }
}

/** Cards left, as a badge on the avatar; turns red and shows 报单/报双 at one or two cards. */
@Composable
private fun CardCountBadge(cards: Int, alert: Boolean, modifier: Modifier) {
    val shape = RoundedCornerShape(6.dp)
    val background = if (alert) DdzColors.SuitRed else DdzColors.PanelStrong
    Row(
        modifier
            .background(background, shape)
            .border(0.5.dp, DdzColors.Cream.copy(alpha = 0.22f), shape)
            .padding(horizontal = 5.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LineIcon(UiIcon.CARDS, Modifier.size(14.dp), color = DdzColors.Cream.copy(alpha = 0.75f))
        Spacer(Modifier.width(3.dp))
        Text("$cards", color = DdzColors.Cream, fontSize = 20.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold)
        if (alert) {
            Spacer(Modifier.width(3.dp))
            Text(if (cards == 1) "报单" else "报双", color = DdzColors.Cream, fontSize = 13.sp, lineHeight = 17.sp, fontWeight = FontWeight.Medium)
        }
    }
}

/**
 * An opponent in a top corner: avatar with a cards-left badge on the side facing the table, then
 * name and coins on a dark plate (gold on bare felt is too faint to read).
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
        RoleAvatar(seat, g.seatAvatar, roleKnown = phase != Phase.BIDDING) {
            CardCountBadge(
                seat.cards,
                alert = phase == Phase.PLAYING && seat.cards in 1..2,
                modifier = Modifier
                    .align(if (left) Alignment.BottomEnd else Alignment.BottomStart)
                    .offset(x = if (left) 27.dp else (-27).dp, y = 4.dp),
            )
        }
        Spacer(Modifier.height(5.dp))
        Column(
            Modifier.background(DdzColors.Panel.copy(alpha = 0.75f), RoundedCornerShape(6.dp)).padding(horizontal = 2.dp, vertical = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally)) {
                Text(
                    seat.name, color = DdzColors.Cream, fontSize = 16.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                )
                RoleBadge(seat.isLandlord, roleKnown = phase != Phase.BIDDING)
            }
            CoinBalance(seat.coins)
        }
    }
}

/** The human's own badge, left of the action buttons; names the role once the landlord is known. */
@Composable
fun HumanPanel(seat: SeatUi, g: TableGeometry, roleKnown: Boolean) {
    Row(
        Modifier
            .topLeftAt(g.margin, g.humanPanelTop)
            .width(with(LocalDensity.current) { g.humanPanelW.toDp() }),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RoleAvatar(seat, 40, roleKnown = roleKnown)
        Spacer(Modifier.width(8.dp))
        Column {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("我", color = DdzColors.Cream, fontSize = 16.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                RoleBadge(seat.isLandlord, roleKnown = roleKnown)
            }
            CoinBalance(seat.coins)
        }
    }
}

@Composable
private fun CoinBalance(coins: Long) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        LineIcon(UiIcon.COIN, Modifier.size(12.dp), color = DdzColors.Gold.copy(alpha = 0.8f))
        Text(formatCoins(coins), color = DdzColors.Gold, fontSize = 14.sp, lineHeight = 18.sp, maxLines = 1)
    }
}

/** Speech bubble in front of a seat, e.g. "抢地主" or "不出". */
@Composable
fun Bubble(text: String, g: TableGeometry, seat: Int) {
    val c = g.playCenter(seat)
    Box(
        Modifier
            .centerAt(c.x, c.y)
            .background(DdzColors.Cream, RoundedCornerShape(10.dp))
            .border(0.5.dp, DdzColors.Gold.copy(alpha = 0.55f), RoundedCornerShape(10.dp))
            .padding(horizontal = 16.dp, vertical = 7.dp),
    ) {
        Text(text, color = DdzColors.FeltEdge, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
    }
}

/**
 * A gold plate behind the cards the human has to beat, so it is obvious which of the two plays
 * on the table counts. Drawn under the card layer.
 */
@Composable
fun TrickPlate(ui: TableUi, g: TableGeometry) {
    val seat = ui.trickOwner
    if (seat !in 1..2 || (ui.controls != Controls.FOLLOW && ui.controls != Controls.CANNOT_BEAT)) return
    val n = ui.displays[seat].size
    if (n == 0) return
    val first = g.playSlot(seat, 0, n)
    val last = g.playSlot(seat, n - 1, n)
    val pad = g.dp(5f)
    val density = LocalDensity.current
    val shape = RoundedCornerShape(10.dp)
    Box(
        Modifier
            .topLeftAt(first.x - pad, first.y - pad)
            .size(with(density) { (last.x - first.x + g.tableW + 2 * pad).toDp() }, with(density) { (g.tableH + 2 * pad).toDp() })
            .background(DdzColors.Gold.copy(alpha = 0.08f), shape)
            .border(1.dp, DdzColors.Gold.copy(alpha = 0.65f), shape),
    )
}

/**
 * The human's choices in three fixed slots: left declines (不出, 不叫, 不抢, 不加倍, 要不起), the middle
 * is 提示 (or a plain question while bidding), right confirms (出牌, 叫地主, 抢地主, 加倍). Positions
 * never move between states, so a remembered spot can't turn into a different button.
 */
@Composable
fun ActionBar(
    ui: TableUi,
    g: TableGeometry,
    onBid: (Boolean) -> Unit,
    onJiabei: (Boolean) -> Unit,
    onPass: () -> Unit,
    onHint: () -> Unit,
    onPlay: () -> Unit,
) {
    val density = LocalDensity.current
    val bw = with(density) { g.buttonW.toDp() }
    val bh = with(density) { g.buttonH.toDp() }

    @Composable
    fun Slot(i: Int, text: String, kind: ButtonKind, tag: String, glow: Boolean = false, haptic: HapticFeedbackType = HapticFeedbackType.VirtualKey, onClick: () -> Unit) {
        GameButton(
            text, onClick, Modifier.topLeftAt(g.slotX(i), g.buttonTop).size(bw, bh),
            kind = kind, tag = tag, fontSize = 22, glow = glow, haptic = haptic,
        )
    }

    /** Plain text centred across slots [from]..[to]. */
    @Composable
    fun Note(text: String, from: Int, to: Int) {
        val x = (g.slotX(from) + g.slotX(to) + g.buttonW) / 2
        Text(
            text, modifier = Modifier.centerAt(x, g.buttonTop + g.buttonH / 2), maxLines = 1,
            style = TextStyle(
                color = DdzColors.Cream.copy(alpha = 0.86f), fontSize = 17.sp, fontWeight = FontWeight.Medium,
            ),
        )
    }

    when (ui.controls) {
        Controls.CALL -> {
            Slot(0, "不叫", ButtonKind.NEUTRAL, "btn_nocall") { onBid(false) }
            Note("要叫地主吗？", 1, 1)
            Slot(2, "叫地主", ButtonKind.PRIMARY, "btn_call") { onBid(true) }
        }
        Controls.ROB -> {
            Slot(0, "不抢", ButtonKind.NEUTRAL, "btn_norob") { onBid(false) }
            Note("要抢地主吗？", 1, 1)
            Slot(2, "抢地主", ButtonKind.PRIMARY, "btn_rob") { onBid(true) }
        }
        Controls.JIABEI -> {
            Slot(0, "不加倍", ButtonKind.NEUTRAL, "btn_nojiabei") { onJiabei(false) }
            Note("要加倍吗？", 1, 1)
            Slot(2, "加倍", ButtonKind.SUCCESS, "btn_jiabei") { onJiabei(true) }
        }
        Controls.LEAD, Controls.FOLLOW -> {
            if (ui.controls == Controls.FOLLOW) Slot(0, "不出", ButtonKind.NEUTRAL, "btn_pass", onClick = onPass)
            Slot(1, "提示", ButtonKind.SECONDARY, "btn_hint", onClick = onHint)
            Slot(
                2, "出牌", ButtonKind.PRIMARY, "btn_play", glow = ui.canPlay,
                haptic = if (ui.canPlay) HapticFeedbackType.Confirm else HapticFeedbackType.Reject, onClick = onPlay,
            )
        }
        Controls.CANNOT_BEAT -> {
            Slot(0, "要不起", ButtonKind.NEUTRAL, "btn_cannot", onClick = onPass)
            Note("要不起，自动跳过", 1, 2)
        }
        Controls.NONE -> {}
    }
}

/**
 * Shown over the lower part of the hand while the computer plays for the human (托管), with a big
 * way back. The corner indices stay visible above it.
 */
@Composable
fun TrusteeBanner(g: TableGeometry, onCancel: () -> Unit) {
    val density = LocalDensity.current
    Row(
        Modifier
            .topLeftAt(0f, g.trusteeTop)
            .fillMaxWidth()
            .height(with(density) { (g.h - g.dp(4f) - g.trusteeTop).toDp() })
            .background(Brush.verticalGradient(listOf(DdzColors.FeltEdge.copy(alpha = 0.85f), DdzColors.PanelStrong)))
            // Swallow taps so nothing underneath reacts while 托管 is on.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            LineIcon(UiIcon.CHECK, Modifier.size(22.dp), color = DdzColors.Gold)
            Text("托管中，电脑正在帮你出牌", color = DdzColors.Cream, fontSize = 18.sp, fontWeight = FontWeight.Medium)
        }
        GameButton("取消托管", onCancel, Modifier.size(140.dp, 52.dp), kind = ButtonKind.SECONDARY, tag = "btn_untrustee", fontSize = 19)
    }
}
