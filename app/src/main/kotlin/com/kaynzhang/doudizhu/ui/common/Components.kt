package com.kaynzhang.doudizhu.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
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

/** Pill-shaped game button with a gradient and a press-down scale; at least 44dp tall. */
@Composable
fun GameButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: ButtonKind = ButtonKind.PRIMARY,
    enabled: Boolean = true,
    tag: String? = null,
    fontSize: Int = 17,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.94f else 1f, label = "press")
    val shape = RoundedCornerShape(50)
    val brush = if (enabled) Brush.verticalGradient(listOf(kind.top, kind.bottom))
    else Brush.verticalGradient(listOf(Color(0xFF6B7280), Color(0xFF374151)))
    Box(
        modifier
            .then(if (tag != null) Modifier.testTag(tag) else Modifier)
            .scale(scale)
            .shadow(if (enabled) 4.dp else 0.dp, shape)
            .background(brush, shape)
            .border(1.dp, Color.White.copy(alpha = 0.35f), shape)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .heightIn(min = 44.dp)
            .widthIn(min = 92.dp)
            .padding(horizontal = 18.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (enabled) Color.White else Color(0xFFBDBDBD), fontSize = fontSize.sp, fontWeight = FontWeight.Bold)
    }
}

/** Small translucent chip used in the top bar. */
@Composable
fun Chip(text: String, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, active: Boolean = false, tag: String? = null) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier
            .then(if (tag != null) Modifier.testTag(tag) else Modifier)
            .background(if (active) DdzColors.Gold.copy(alpha = 0.9f) else DdzColors.Panel, shape)
            .border(1.dp, DdzColors.Gold.copy(alpha = 0.45f), shape)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .heightIn(min = 32.dp)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (active) Color(0xFF3E2A00) else DdzColors.Cream, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier
            .background(DdzColors.PanelStrong, shape)
            .border(1.5.dp, DdzColors.Gold.copy(alpha = 0.6f), shape)
            .padding(18.dp),
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

/** Shows each message from [messages] for a moment near the top of the screen. */
@Composable
fun MessageHost(messages: Flow<String>, modifier: Modifier = Modifier) {
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
            delay(1_800)
            text = null
        }
    }
    AnimatedVisibility(visible = text != null, modifier = modifier, enter = fadeIn(), exit = fadeOut()) {
        Box(
            Modifier
                .background(Color(0xE6000000), RoundedCornerShape(50))
                .padding(horizontal = 20.dp, vertical = 10.dp),
        ) {
            Text(text ?: "", color = Color.White, fontSize = 15.sp)
        }
    }
}

/** 9,850 / 1.2万 / 3.4亿 */
fun formatCoins(n: Long): String = when {
    n >= 100_000_000 -> "%.1f亿".format(n / 100_000_000.0)
    n >= 10_000 -> "%.1f万".format(n / 10_000.0).replace(".0万", "万")
    else -> "%,d".format(n)
}
