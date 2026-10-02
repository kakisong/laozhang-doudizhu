package com.kaynzhang.doudizhu.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kaynzhang.doudizhu.ui.theme.DdzColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow

enum class ButtonKind(val top: Color, val bottom: Color) {
    PRIMARY(DdzColors.Orange, DdzColors.OrangeDeep),
    SECONDARY(DdzColors.Blue, DdzColors.BlueDeep),
    SUCCESS(DdzColors.Green, DdzColors.GreenDeep),
    NEUTRAL(DdzColors.Gray, DdzColors.GrayDeep),
}

/**
 * A quiet, tactile game button with a press-down scale and a light tap vibration; at least
 * 52dp tall. [glow] adds a pulsing gold ring that says "ready" (for example 出牌 with a playable
 * selection). There is no disabled look: a button that can't act should explain why when tapped.
 */
@Composable
fun GameButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: ButtonKind = ButtonKind.PRIMARY,
    tag: String? = null,
    fontSize: Int = 19,
    glow: Boolean = false,
    haptic: HapticFeedbackType = HapticFeedbackType.VirtualKey,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.98f else 1f, label = "press")
    val haptics = LocalHapticFeedback.current
    val clickSound = LocalUiSound.current
    val shape = RoundedCornerShape(12.dp)
    val prominent = kind == ButtonKind.PRIMARY || kind == ButtonKind.SUCCESS
    // Only a glowing button animates, so ordinary buttons cost no frames.
    val ring = if (glow) {
        val pulse = rememberInfiniteTransition(label = "glow")
        val a by pulse.animateFloat(0.5f, 1f, infiniteRepeatable(tween(1100), RepeatMode.Reverse), label = "ring")
        a
    } else {
        0f
    }
    Box(
        modifier
            .then(if (tag != null) Modifier.testTag(tag) else Modifier)
            .scale(scale)
            .shadow(if (prominent) 3.dp else 0.dp, shape)
            .background(Brush.verticalGradient(listOf(kind.top, kind.bottom)), shape)
            .border(
                if (glow) 1.5.dp else 1.dp,
                if (glow) DdzColors.Cream.copy(alpha = ring) else if (prominent) DdzColors.Gold else DdzColors.Hairline,
                shape,
            )
            .clickable(interactionSource = interaction, indication = null, role = Role.Button) {
                haptics.performHapticFeedback(haptic)
                clickSound()
                onClick()
            }
            .heightIn(min = 52.dp)
            .widthIn(min = 100.dp)
            .padding(horizontal = 18.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text, maxLines = 1,
            style = TextStyle(
                color = if (prominent) DdzColors.Ink else DdzColors.Cream,
                fontSize = fontSize.sp, fontWeight = FontWeight.SemiBold,
            ),
        )
    }
}

/**
 * A compact control used in the top bar and page headers: 40dp to look at, but a clickable chip
 * takes a 48dp-tall touch target and brightens while pressed.
 */
@Composable
fun Chip(
    text: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    active: Boolean = false,
    tag: String? = null,
    fontSize: Int = 16,
) {
    val shape = RoundedCornerShape(10.dp)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val haptics = LocalHapticFeedback.current
    val clickSound = LocalUiSound.current
    val fill = when {
        active -> DdzColors.Gold.copy(alpha = if (pressed) 0.22f else 0.12f)
        pressed -> DdzColors.PanelStrong
        else -> DdzColors.Panel
    }
    Box(
        modifier
            .then(if (tag != null) Modifier.testTag(tag) else Modifier)
            .then(
                if (onClick != null) {
                    Modifier.clickable(interactionSource = interaction, indication = null, role = Role.Button) {
                        haptics.performHapticFeedback(HapticFeedbackType.VirtualKey)
                        clickSound()
                        onClick()
                    }
                } else {
                    Modifier
                },
            )
            .heightIn(min = 48.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .background(fill, shape)
                .border(1.dp, DdzColors.Gold.copy(alpha = if (active || pressed) 0.5f else 0.18f), shape)
                .heightIn(min = 40.dp)
                .padding(horizontal = 14.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text, color = if (active) DdzColors.Gold else DdzColors.Cream,
                fontSize = fontSize.sp, fontWeight = FontWeight.Medium, maxLines = 1,
            )
        }
    }
}

@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier
            .background(DdzColors.PanelStrong, shape)
            .border(1.dp, DdzColors.Hairline, shape)
            .padding(22.dp),
        content = content,
    )
}

/** Full-screen scrim that swallows touches, with [content] centered. Used instead of dialogs (keeps immersive mode). */
@Composable
fun Overlay(content: @Composable BoxScope.() -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(DdzColors.Scrim)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center,
        content = content,
    )
}

/** Shows each message from [messages] for three seconds: large, centred, readable at arm's length. */
@Composable
fun MessageHost(messages: Flow<String>, modifier: Modifier = Modifier, maxWidth: Dp = 520.dp) {
    var text by remember { mutableStateOf<String?>(null) }
    var counter by remember { mutableIntStateOf(0) }
    LaunchedEffect(messages) {
        messages.collect {
            text = it
            counter++
        }
    }
    LaunchedEffect(counter) {
        if (text != null) {
            delay(3_000)
            text = null
        }
    }
    AnimatedVisibility(visible = text != null, modifier = modifier, enter = fadeIn(), exit = fadeOut()) {
        val shape = RoundedCornerShape(12.dp)
        Box(
            Modifier
                .widthIn(max = maxWidth)
                .background(DdzColors.PanelStrong, shape)
                .border(1.dp, DdzColors.Gold.copy(alpha = 0.4f), shape)
                .padding(horizontal = 22.dp, vertical = 10.dp),
        ) {
            Text(text ?: "", color = Color.White, fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        }
    }
}

/** 9,850 / 1.2万 / 3.4亿 */
fun formatCoins(n: Long): String = when {
    n >= 100_000_000 -> "%.1f亿".format(n / 100_000_000.0)
    n >= 10_000 -> "%.1f万".format(n / 10_000.0).replace(".0万", "万")
    else -> "%,d".format(n)
}
