package com.kaynzhang.doudizhu.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kaynzhang.doudizhu.data.Profile
import com.kaynzhang.doudizhu.R
import com.kaynzhang.doudizhu.data.Room
import com.kaynzhang.doudizhu.game.GameViewModel
import com.kaynzhang.doudizhu.ui.common.ButtonKind
import com.kaynzhang.doudizhu.ui.common.GameButton
import com.kaynzhang.doudizhu.ui.common.LineIcon
import com.kaynzhang.doudizhu.ui.common.MessageHost
import com.kaynzhang.doudizhu.ui.common.TableBackdrop
import com.kaynzhang.doudizhu.ui.common.UiIcon
import com.kaynzhang.doudizhu.ui.common.formatCoins
import com.kaynzhang.doudizhu.ui.theme.DdzColors
import com.kaynzhang.doudizhu.ui.common.LocalUiSound

@Composable
fun LobbyScreen(
    vm: GameViewModel,
    onEnterRoom: (Room) -> Unit,
    onResume: () -> Unit,
    onSettings: () -> Unit,
    onStats: () -> Unit,
    onRules: () -> Unit,
) {
    val data by vm.appData.collectAsStateWithLifecycle()
    val d = data
    BoxWithConstraints(Modifier.fillMaxSize()) {
        TableBackdrop()
        if (d == null) return@BoxWithConstraints
        val narrow = maxWidth < 760.dp
        val veryNarrow = maxWidth < 640.dp
        val short = maxHeight < 380.dp
        Row(
            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.displayCutout)
                .padding(horizontal = if (veryNarrow) 16.dp else if (narrow) 18.dp else 30.dp, vertical = if (short) 14.dp else 24.dp),
            horizontalArrangement = Arrangement.spacedBy(if (veryNarrow) 14.dp else if (narrow) 18.dp else 30.dp),
        ) {
            Column(
                Modifier.width(if (veryNarrow) 140.dp else if (narrow) 170.dp else 218.dp).fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    if (!short) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                            LineIcon(UiIcon.CARDS, Modifier.size(22.dp), DdzColors.Gold)
                            Text("闲时牌局", color = DdzColors.Cream.copy(alpha = 0.75f), fontSize = 15.sp, lineHeight = 21.sp, letterSpacing = 2.sp)
                        }
                        Spacer(Modifier.height(10.dp))
                    }
                    Text(
                        stringResource(R.string.app_name), color = DdzColors.Cream, fontWeight = FontWeight.Bold, maxLines = 1,
                        lineHeight = if (short) 32.sp else 44.sp,
                        autoSize = TextAutoSize.StepBased(minFontSize = if (veryNarrow) 20.sp else 24.sp, maxFontSize = if (veryNarrow) 26.sp else if (narrow) 30.sp else 36.sp),
                    )
                    Spacer(Modifier.height(if (short) 18.dp else 28.dp))
                    Box(Modifier.width(38.dp).height(1.dp).background(DdzColors.Gold.copy(alpha = 0.65f)))
                    Spacer(Modifier.height(if (short) 14.dp else 22.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        LineIcon(UiIcon.COIN, Modifier.size(18.dp), DdzColors.Gold)
                        Text("我的金币", color = DdzColors.Cream.copy(alpha = 0.75f), fontSize = 16.sp, lineHeight = 22.sp)
                    }
                    Spacer(Modifier.height(5.dp))
                    Text(
                        formatCoins(d.profile.coins), color = DdzColors.Gold, fontWeight = FontWeight.Medium,
                        maxLines = 1, modifier = Modifier.testTag("coins"), lineHeight = if (narrow) 38.sp else 44.sp,
                        autoSize = TextAutoSize.StepBased(minFontSize = 22.sp, maxFontSize = if (narrow) 31.sp else 38.sp),
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(if (short) 8.dp else 12.dp)) {
                    if (d.savedTable != null) {
                        GameButton("继续上一局", onResume, Modifier.fillMaxWidth(), kind = ButtonKind.SUCCESS, tag = "btn_resume", fontSize = if (veryNarrow) 15 else 18)
                    }
                    if (d.profile.canClaimRelief) {
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 52.dp)
                                .testTag("btn_relief").clickable(role = Role.Button, onClick = vm::claimRelief)
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("领取救济金", color = DdzColors.Cream, fontSize = 16.sp, lineHeight = 22.sp)
                                Text("+${formatCoins(Profile.RELIEF_AMOUNT)} 金币", color = DdzColors.Gold, fontSize = 14.sp, lineHeight = 20.sp)
                            }
                            LineIcon(UiIcon.CHEVRON, Modifier.size(18.dp), DdzColors.Gold)
                        }
                    } else if (!short) {
                        Text("无需联网，随时开局", color = DdzColors.Cream.copy(alpha = 0.6f), fontSize = 14.sp, lineHeight = 20.sp)
                    }
                }
            }
            Column(Modifier.weight(1f).fillMaxHeight()) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    if (!narrow) {
                        Text("选择场次", color = DdzColors.Cream.copy(alpha = 0.72f), fontSize = 17.sp, lineHeight = 24.sp)
                    }
                    Spacer(Modifier.weight(1f))
                    LobbyNav("设置", UiIcon.SETTINGS, "btn_settings", onSettings)
                    LobbyNav("战绩", UiIcon.STATS, "btn_stats", onStats)
                    LobbyNav("玩法说明", UiIcon.RULES, "btn_rules", onRules)
                }
                Spacer(Modifier.height(if (short) 10.dp else 18.dp))
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(if (narrow) 10.dp else 16.dp)) {
                    for (room in Room.entries) {
                        RoomCard(
                            room = room, affordable = d.profile.coins >= room.minCoins,
                            compact = narrow || short, short = short,
                            onClick = { onEnterRoom(room) }, modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
        MessageHost(vm.messages, Modifier.align(Alignment.TopCenter).padding(top = 24.dp))
    }
}

@Composable
private fun LobbyNav(label: String, icon: UiIcon, tag: String, onClick: () -> Unit) {
    val clickSound = LocalUiSound.current
    Row(
        Modifier.testTag(tag).heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClick = { clickSound(); onClick() }).padding(horizontal = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        LineIcon(icon, Modifier.size(18.dp), DdzColors.Cream.copy(alpha = 0.75f))
        Text(label, color = DdzColors.Cream, fontSize = 17.sp, lineHeight = 24.sp, maxLines = 1)
    }
}

/** One generous touch target; the room remains tappable to explain a missing coin balance. */
@Composable
private fun RoomCard(
    room: Room,
    affordable: Boolean,
    compact: Boolean,
    short: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val shape = RoundedCornerShape(16.dp)
    val clickSound = LocalUiSound.current
    val top = when (room) {
        Room.NOVICE -> Color(0xFF254D40)
        Room.NORMAL -> Color(0xFF204437)
        Room.MASTER -> Color(0xFF1C3B31)
    }
    val ink = if (affordable) DdzColors.Cream else DdzColors.Cream.copy(alpha = 0.65f)
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(
        modifier.fillMaxHeight().testTag("room_${room.name.lowercase()}")
            .background(Brush.verticalGradient(listOf(top, Color(0xFF132D24))), shape)
            .border(1.dp, DdzColors.Gold.copy(alpha = if (affordable) 0.32f else 0.13f), shape)
            .clickable(role = Role.Button, onClick = { clickSound(); onClick() }),
    ) {
        // Reserve room for the complete entry line before showing decorative descriptions.
        val compactCard = compact || maxHeight < 280.dp || fontScale > 1.12f
        val tightCard = maxWidth < 132.dp
        val descriptionMinHeight = ((if (compactCard) 232f else 280f) * fontScale).dp
        val showDescription = !short && maxHeight >= descriptionMinHeight && fontScale <= 1.12f
        Column(
            Modifier.fillMaxSize().padding(
                horizontal = if (tightCard) 10.dp else if (compactCard) 12.dp else 18.dp,
                vertical = if (compactCard) 12.dp else 18.dp,
            ),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("0${room.ordinal + 1}", color = DdzColors.Gold.copy(alpha = 0.75f), fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = 2.sp)
                    Spacer(Modifier.weight(1f))
                    LineIcon(
                        if (room == Room.MASTER) UiIcon.CROWN else UiIcon.CARDS,
                        Modifier.size(if (compactCard) 18.dp else 24.dp), DdzColors.Gold.copy(alpha = 0.7f),
                    )
                }
                Spacer(Modifier.height(if (compactCard) 5.dp else 8.dp))
                Text(
                    room.title, color = ink, fontWeight = FontWeight.Medium, maxLines = 1,
                    lineHeight = if (compactCard) 28.sp else 32.sp,
                    autoSize = TextAutoSize.StepBased(minFontSize = 19.sp, maxFontSize = if (compactCard) 22.sp else 26.sp),
                )
            }
            Column {
                Text("底分", color = ink.copy(alpha = 0.7f), fontSize = 14.sp, lineHeight = 18.sp)
                Text(
                    formatCoins(room.baseScore), color = if (affordable) DdzColors.Gold else ink,
                    fontWeight = FontWeight.Medium, maxLines = 1,
                    lineHeight = if (compactCard) 34.sp else 44.sp,
                    autoSize = TextAutoSize.StepBased(minFontSize = 23.sp, maxFontSize = if (compactCard) 28.sp else 38.sp),
                )
                Spacer(Modifier.height(if (compactCard) 3.dp else 6.dp))
                Text(
                    if (tightCard) room.difficulty.zh else "对手 · ${room.difficulty.zh}",
                    color = ink, fontSize = if (tightCard) 16.sp else if (compactCard) 14.sp else 15.sp,
                    lineHeight = if (tightCard) 22.sp else 20.sp, maxLines = 1,
                )
                if (showDescription) {
                    Spacer(Modifier.height(7.dp))
                    Text(
                        room.blurb, color = ink.copy(alpha = 0.72f), fontSize = 14.sp, lineHeight = 20.sp,
                        minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Column {
                Box(Modifier.fillMaxWidth().height(1.dp).background(DdzColors.Cream.copy(alpha = 0.12f)))
                Spacer(Modifier.height(if (compactCard) 8.dp else 12.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (affordable) "入场 ${formatCoins(room.minCoins)}" else if (tightCard) "需 ${formatCoins(room.minCoins)}" else "需 ${formatCoins(room.minCoins)} 金币",
                        color = ink, fontWeight = FontWeight.Medium, maxLines = 1, lineHeight = 20.sp,
                        modifier = Modifier.weight(1f),
                        autoSize = TextAutoSize.StepBased(minFontSize = 12.sp, maxFontSize = if (compactCard) 14.sp else 16.sp),
                    )
                    if (!tightCard) {
                        Spacer(Modifier.width(2.dp))
                        LineIcon(UiIcon.CHEVRON, Modifier.size(16.dp), if (affordable) DdzColors.Gold else ink)
                    }
                }
            }
        }
    }
}
