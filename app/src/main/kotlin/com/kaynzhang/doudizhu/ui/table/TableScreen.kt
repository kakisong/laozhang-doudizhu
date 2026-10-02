package com.kaynzhang.doudizhu.ui.table

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleStartEffect
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
import com.kaynzhang.doudizhu.ui.theme.DdzColors
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

@Composable
fun TableScreen(vm: GameViewModel, onLeave: () -> Unit) {
    val state by vm.table.collectAsStateWithLifecycle()
    val ui = state ?: return

    LifecycleStartEffect(Unit) {
        vm.setForeground(true)
        onStopOrDispose { vm.setForeground(false) }
    }
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    var confirmExit by remember { mutableStateOf(false) }
    BackHandler { confirmExit = !confirmExit }

    val density = LocalDensity.current
    val measurer = rememberTextMeasurer(cacheSize = 64)
    val shake = remember { Animatable(0f) }

    // Card faces and table text use dp-based sizes: ignore the system font scale here.
    CompositionLocalProvider(LocalCardText provides measurer, LocalDensity provides Density(density.density, 1f)) {
        Box(
            Modifier
                .fillMaxSize()
                .background(DdzColors.felt)
                .graphicsLayer { translationX = shake.value },
        ) {
            BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.displayCutout)) {
                val d = LocalDensity.current
                val g = remember(constraints.maxWidth, constraints.maxHeight, ui.counter != null) {
                    TableGeometry(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat(), d, ui.counter != null)
                }
                TableContent(ui, g, vm, onToggleCounter = {
                    vm.updateSettings { it.copy(cardCounter = !it.cardCounter) }
                }, onBack = { confirmExit = true })
                EffectsLayer(vm.effects, g, shake)
                MessageHost(vm.messages, Modifier.align(Alignment.TopCenter).padding(top = 96.dp))
            }
            // Modal layers sit outside the cutout padding so their scrim covers the whole screen.
            if (ui.showResult) {
                ResultOverlay(ui, onAgain = { if (!vm.playAgain()) onLeave() }, onLobby = onLeave)
            }
            if (confirmExit) {
                ConfirmExit(finished = ui.phase == Phase.FINISHED, onStay = { confirmExit = false }, onLeave = onLeave)
            }
        }
    }
}

@Composable
private fun TableContent(ui: TableUi, g: TableGeometry, vm: GameViewModel, onToggleCounter: () -> Unit, onBack: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .drawBehind {
                // A faint oval marks the playing area.
                drawOval(
                    DdzColors.FeltLine,
                    topLeft = Offset(size.width * 0.12f, g.aiPlayTop - g.dp(6f)),
                    size = Size(size.width * 0.76f, g.buttonsTop - g.aiPlayTop + g.dp(4f)),
                    style = Stroke(g.dp(2f)),
                )
            }
            .pointerInput(Unit) { detectTapGestures { vm.clearSelection() } },
    )

    TopBar(ui, g, onBack = onBack, onToggleCounter = onToggleCounter, onToggleTrustee = { vm.setTrustee(!ui.trustee) })
    ui.counter?.let { CounterStrip(it, g) }
    SeatPanel(ui.seats[2], g, ui.phase)
    SeatPanel(ui.seats[1], g, ui.phase)
    HumanPanel(ui.seats[HUMAN], g)

    var preview by remember { mutableStateOf<IntRange?>(null) }
    val specs = remember(ui, g, preview) { buildSprites(ui, g, preview) }
    key(ui.dealKey) { CardLayer(specs, g) }

    for (seat in 0 until 3) {
        val status = ui.seats[seat].status
        if (status != null && ui.displays[seat].isEmpty()) Bubble(status, g, seat)
    }

    ActionBar(
        ui, g,
        onBid = vm::bid, onJiabei = vm::jiabei, onPass = vm::pass, onHint = vm::hint, onPlay = vm::play,
        onUntrustee = { vm.setTrustee(false) },
    )
    HandGestures(
        hand = ui.hand, selected = ui.selected, g = g, enabled = !ui.trustee,
        onTap = vm::toggle, onRange = vm::setSelected, onPreview = { preview = it }, onMiss = vm::clearSelection,
    )
}

/**
 * Tap toggles one card; a horizontal drag previews a range and, on release, sets every card in it
 * to the opposite of the first card's state. Hit-testing is by x only, so overlapped cards work.
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
    onMiss: () -> Unit,
) {
    val handState = rememberUpdatedState(hand)
    val selectedState = rememberUpdatedState(selected)
    val geo = rememberUpdatedState(g)
    val enabledState = rememberUpdatedState(enabled)
    val density = LocalDensity.current
    Box(
        Modifier
            .testTag("hand")
            .topLeftAt(0f, g.handTop - g.raise - g.dp(2f))
            .fillMaxWidth()
            .height(with(density) { (g.h - g.handTop + g.raise + g.dp(2f)).toDp() })
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val cards = handState.value
                    val start = geo.value.handIndexAt(down.position.x, cards.size)
                    if (!enabledState.value) return@awaitEachGesture
                    if (start < 0) {
                        onMiss()
                        return@awaitEachGesture
                    }
                    down.consume()
                    var end = start
                    var dragging = false
                    onPreview(start..start)
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        if (!dragging && abs(change.position.x - down.position.x) > viewConfiguration.touchSlop) dragging = true
                        if (dragging) {
                            end = geo.value.handIndexClamped(change.position.x, cards.size)
                            onPreview(min(start, end)..max(start, end))
                        }
                        change.consume()
                    }
                    onPreview(null)
                    if (start >= cards.size) return@awaitEachGesture
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
private fun ConfirmExit(finished: Boolean, onStay: () -> Unit, onLeave: () -> Unit) {
    Overlay {
        Panel {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("离开牌桌？", color = DdzColors.Gold, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(
                    if (finished) "返回大厅后可以换个场次继续。" else "当前牌局会自动保存，回到大厅可以继续上一局。",
                    color = DdzColors.Cream, fontSize = 15.sp,
                )
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    GameButton("继续游戏", onStay, kind = ButtonKind.SECONDARY, tag = "btn_stay")
                    GameButton("返回大厅", onLeave, kind = ButtonKind.NEUTRAL, tag = "btn_leave")
                }
            }
        }
    }
}
