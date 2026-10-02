package com.kaynzhang.doudizhu.ui.table

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.key
import com.kaynzhang.doudizhu.engine.game.Phase
import com.kaynzhang.doudizhu.engine.model.Card
import com.kaynzhang.doudizhu.game.GameViewModel
import com.kaynzhang.doudizhu.game.HUMAN
import com.kaynzhang.doudizhu.game.TableUi
import com.kaynzhang.doudizhu.ui.card.LocalCardText
import com.kaynzhang.doudizhu.ui.common.ButtonKind
import com.kaynzhang.doudizhu.ui.common.GameButton
import com.kaynzhang.doudizhu.ui.common.MessageHost
import com.kaynzhang.doudizhu.ui.common.Overlay
import com.kaynzhang.doudizhu.ui.common.Panel
import com.kaynzhang.doudizhu.ui.common.TableBackdrop
import com.kaynzhang.doudizhu.ui.common.LineIcon
import com.kaynzhang.doudizhu.ui.common.UiIcon
import com.kaynzhang.doudizhu.ui.theme.DdzColors
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

@Composable
fun TableScreen(vm: GameViewModel, onLeave: () -> Unit) {
    val state by vm.table.collectAsStateWithLifecycle()
    val ui = state ?: return

    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    var confirmExit by remember { mutableStateOf(false) }
    BackHandler {
        when {
            confirmExit -> { confirmExit = false; vm.setPaused(false) }
            ui.showResult -> confirmExit = true
            else -> vm.setPaused(!ui.paused)
        }
    }

    val density = LocalDensity.current
    val measurer = rememberTextMeasurer(cacheSize = 128)
    val shake = remember { Animatable(0f) }

    // Card faces and table text use dp-based sizes: ignore the system font scale here.
    CompositionLocalProvider(LocalCardText provides measurer, LocalDensity provides Density(density.density, 1f)) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { translationX = shake.value },
        ) {
            TableBackdrop()
            BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.displayCutout)) {
                // Tablets: scale the whole table up so it reads like a big phone instead of spreading out
                // (keeping at least ~760dp of virtual width for the button slots and the 记牌器).
                val k = min(maxHeight.value / 420f, maxWidth.value / 760f).coerceIn(1f, 1.5f)
                val d = Density(density.density * k, 1f)
                CompositionLocalProvider(LocalDensity provides d) {
                    val g = remember(constraints.maxWidth, constraints.maxHeight, d.density, ui.counter != null) {
                        TableGeometry(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat(), d, ui.counter != null)
                    }
                    TableContent(ui, g, vm, onToggleCounter = {
                        vm.updateSettings { it.copy(cardCounter = !it.cardCounter) }
                    }, onBack = { vm.setPaused(true); confirmExit = true })
                    EffectsLayer(vm.effects, g, shake)
                    MessageHost(
                        vm.messages, Modifier.centerAt(g.w / 2, g.messageY),
                        maxWidth = with(d) { (g.w - g.dp(220f)).toDp() },
                    )
                }
            }
            // Modal layers sit outside the cutout padding so their scrim covers the whole screen.
            if (ui.showResult) {
                ResultOverlay(ui, onAgain = { if (!vm.playAgain()) onLeave() }, onLobby = onLeave)
            }
            if (confirmExit) {
                ConfirmExit(finished = ui.phase == Phase.FINISHED, onStay = { confirmExit = false; vm.setPaused(false) }, onLeave = onLeave)
            } else if (ui.paused) {
                PauseOverlay(onResume = { vm.setPaused(false) }, onLobby = onLeave)
            }
        }
    }
}

@Composable
private fun TableContent(ui: TableUi, g: TableGeometry, vm: GameViewModel, onToggleCounter: () -> Unit, onBack: () -> Unit) {
    // A fine double seam marks the playing area. Tapping the felt does nothing: a near miss of a button
    // must never wipe the cards an older player has carefully picked.
    Box(
        Modifier
            .fillMaxSize()
            .drawBehind {
                drawOval(
                    DdzColors.FeltLine,
                    topLeft = Offset(size.width * 0.12f, g.aiPlayTop - g.dp(6f)),
                    size = Size(size.width * 0.76f, g.humanRowTop + g.humanRowH - g.aiPlayTop + g.dp(8f)),
                    style = Stroke(g.dp(1f)),
                )
                drawOval(
                    DdzColors.Gold.copy(alpha = 0.06f),
                    topLeft = Offset(size.width * 0.12f + g.dp(5f), g.aiPlayTop - g.dp(1f)),
                    size = Size(size.width * 0.76f - g.dp(10f), g.humanRowTop + g.humanRowH - g.aiPlayTop - g.dp(2f)),
                    style = Stroke(g.dp(0.7f)),
                )
                val center = Offset(size.width / 2, (g.aiPlayTop + g.humanRowTop + g.humanRowH) / 2)
                val ornament = Path().apply {
                    moveTo(center.x, center.y - g.dp(7f))
                    lineTo(center.x + g.dp(5f), center.y)
                    lineTo(center.x, center.y + g.dp(7f))
                    lineTo(center.x - g.dp(5f), center.y)
                    close()
                }
                drawPath(ornament, DdzColors.Gold.copy(alpha = 0.12f), style = Stroke(g.dp(0.8f)))
                drawLine(DdzColors.FeltLine, center - Offset(g.dp(42f), 0f), center - Offset(g.dp(14f), 0f), g.dp(0.7f))
                drawLine(DdzColors.FeltLine, center + Offset(g.dp(14f), 0f), center + Offset(g.dp(42f), 0f), g.dp(0.7f))
            },
    )

    TopBar(ui, g, onBack = onBack, onToggleCounter = onToggleCounter, onToggleTrustee = { vm.setTrustee(!ui.trustee) }, onPause = { vm.setPaused(true) })
    ui.counter?.let { CounterStrip(it, g) }
    SeatPanel(ui.seats[2], g, ui.phase)
    SeatPanel(ui.seats[1], g, ui.phase)
    HumanPanel(ui.seats[HUMAN], g, roleKnown = ui.bottomRevealed)

    TrickPlate(ui, g)
    var preview by remember { mutableStateOf<IntRange?>(null) }
    val specs = remember(ui, g, preview) { buildSprites(ui, g, preview) }
    key(ui.dealKey) { CardLayer(specs, g) }

    for (seat in 0 until 3) {
        val status = ui.seats[seat].status
        if (status != null && ui.displays[seat].isEmpty()) Bubble(status, g, seat)
    }

    ActionBar(ui, g, onBid = vm::bid, onJiabei = vm::jiabei, onPass = vm::pass, onHint = vm::hint, onPlay = vm::play)
    HandGestures(
        hand = ui.hand, selected = ui.selected, g = g, enabled = !ui.trustee && !ui.paused,
        onTap = vm::toggle, onRange = vm::setSelected, onPreview = { preview = it },
    )
    // After the hand's gesture layer, or that layer would take the banner's taps.
    if (ui.trustee) TrusteeBanner(g, onCancel = { vm.setTrustee(false) })
}

/**
 * Tap toggles one card; a horizontal drag previews a range and, on release, sets every card in it
 * to the opposite of the first card's state. Hit-testing is by x only, so overlapped cards work.
 * A drag only starts after half a card strip of sideways movement, so a shaky tap stays a tap.
 */
@Composable
private fun HandGestures(
    hand: List<Card>,
    selected: Set<Card>,
    g: TableGeometry,
    enabled: Boolean,
    onTap: (Card) -> Unit,
    onRange: (List<Card>, Boolean) -> Unit,
    onPreview: (IntRange?) -> Unit,
) {
    val handState = rememberUpdatedState(hand)
    val selectedState = rememberUpdatedState(selected)
    val geo = rememberUpdatedState(g)
    val enabledState = rememberUpdatedState(enabled)
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    Box(
        Modifier
            .topLeftAt(0f, g.handTop - g.raise - g.dp(2f))
            .testTag("hand")
            .fillMaxWidth()
            .height(with(density) { (g.h - g.handTop + g.raise + g.dp(2f)).toDp() })
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val cards = handState.value
                    val start = geo.value.handIndexAt(down.position.x, cards.size)
                    if (!enabledState.value || start < 0) return@awaitEachGesture
                    down.consume()
                    var end = start
                    var dragging = false
                    val dragSlop = max(viewConfiguration.touchSlop, geo.value.handStep(cards.size) * 0.5f)
                    onPreview(start..start)
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        if (!dragging && abs(change.position.x - down.position.x) > dragSlop) dragging = true
                        if (dragging) {
                            end = geo.value.handIndexClamped(change.position.x, cards.size)
                            onPreview(min(start, end)..max(start, end))
                        }
                        change.consume()
                    }
                    onPreview(null)
                    if (start >= cards.size) return@awaitEachGesture
                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    if (!dragging) {
                        onTap(cards[start])
                    } else {
                        val range = cards.subList(min(start, end), max(start, end) + 1)
                        onRange(range, cards[start] !in selectedState.value)
                    }
                }
            },
    )
}

@Composable
private fun PauseOverlay(onResume: () -> Unit, onLobby: () -> Unit) {
    Overlay {
        Panel(Modifier.widthIn(max = 480.dp).padding(horizontal = 16.dp).testTag("pause_overlay")) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                LineIcon(UiIcon.PAUSE, Modifier.size(32.dp), DdzColors.Gold)
                Spacer(Modifier.height(12.dp))
                Text("已暂停", color = DdzColors.Cream, fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(8.dp))
                Text("牌局停在这里，随时可以继续。", color = DdzColors.TextMuted, fontSize = 18.sp, lineHeight = 26.sp)
                Spacer(Modifier.height(24.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    GameButton("返回大厅", onLobby, kind = ButtonKind.NEUTRAL, tag = "btn_pause_lobby")
                    GameButton("继续游戏", onResume, tag = "btn_resume_game")
                }
            }
        }
    }
}

@Composable
private fun ConfirmExit(finished: Boolean, onStay: () -> Unit, onLeave: () -> Unit) {
    Overlay {
        Panel {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("离开牌桌？", color = DdzColors.Gold, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                Text(
                    if (finished) "返回大厅后可以换个场次继续。" else "这一局会自动保存，回到大厅点「继续上一局」就能接着打。",
                    color = DdzColors.Cream, fontSize = 18.sp, lineHeight = 26.sp,
                )
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                    GameButton("继续游戏", onStay, kind = ButtonKind.SECONDARY, tag = "btn_stay")
                    GameButton("返回大厅", onLeave, kind = ButtonKind.NEUTRAL, tag = "btn_leave")
                }
            }
        }
    }
}
