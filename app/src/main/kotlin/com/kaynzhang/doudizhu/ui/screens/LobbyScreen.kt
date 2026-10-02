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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kaynzhang.doudizhu.data.Profile
import com.kaynzhang.doudizhu.data.Room
import com.kaynzhang.doudizhu.game.GameViewModel
import com.kaynzhang.doudizhu.ui.common.ButtonKind
import com.kaynzhang.doudizhu.ui.common.GameButton
import com.kaynzhang.doudizhu.ui.common.MessageHost
import com.kaynzhang.doudizhu.ui.common.formatCoins
import com.kaynzhang.doudizhu.ui.theme.DdzColors

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
    BoxWithConstraints(Modifier.fillMaxSize().background(DdzColors.felt)) {
        if (d == null) return@BoxWithConstraints
        // Landscape phones are often only ~790dp wide: tighten the side column so the room cards keep their text on one line.
        val compact = maxWidth < 860.dp
        Row(
            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.displayCutout)
                .padding(horizontal = if (compact) 20.dp else 28.dp, vertical = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(if (compact) 18.dp else 28.dp),
        ) {
            Column(Modifier.width(if (compact) 210.dp else 250.dp).fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(
                        "单机斗地主",
                        style = TextStyle(
                            color = DdzColors.Gold, fontSize = if (compact) 33.sp else 38.sp, fontWeight = FontWeight.Black,
                            shadow = Shadow(Color(0x99000000), blurRadius = 10f),
                        ),
                    )
                    Text("欢乐斗地主规则 · 完全离线", color = DdzColors.Cream.copy(alpha = 0.75f), fontSize = 13.sp)
                    Spacer(Modifier.height(18.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(52.dp).background(Color(0xFF2E5E48), CircleShape).border(2.dp, DdzColors.Gold, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) { Text("🙂", fontSize = 28.sp) }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("我", color = DdzColors.Cream, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text("🪙 " + formatCoins(d.profile.coins), color = DdzColors.Gold, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.testTag("coins"))
                        }
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (d.savedTable != null) {
                        GameButton("继续上一局", onResume, kind = ButtonKind.SUCCESS, tag = "btn_resume")
                    }
                    if (d.profile.canClaimRelief) {
                        GameButton("领取救济金 +${formatCoins(Profile.RELIEF_AMOUNT)}", vm::claimRelief, kind = ButtonKind.SECONDARY, tag = "btn_relief", fontSize = 15)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        GameButton("设置", onSettings, kind = ButtonKind.NEUTRAL, tag = "btn_settings", fontSize = 15)
                        GameButton("战绩", onStats, kind = ButtonKind.NEUTRAL, tag = "btn_stats", fontSize = 15)
                    }
                    GameButton("玩法说明", onRules, kind = ButtonKind.NEUTRAL, tag = "btn_rules", fontSize = 15)
                }
            }
            Row(
                Modifier.weight(1f).fillMaxHeight().padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(if (compact) 12.dp else 16.dp),
            ) {
                for (room in Room.entries) {
                    RoomCard(
                        room, affordable = d.profile.coins >= room.minCoins, compact = compact,
                        onClick = { onEnterRoom(room) }, modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        MessageHost(vm.messages, Modifier.align(Alignment.TopCenter).padding(top = 24.dp))
    }
}

@Composable
private fun RoomCard(room: Room, affordable: Boolean, compact: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val shape = RoundedCornerShape(20.dp)
    val colors = when (room) {
        Room.NOVICE -> listOf(Color(0xFF43A047), Color(0xFF1B5E20))
        Room.NORMAL -> listOf(Color(0xFF1E88E5), Color(0xFF0D47A1))
        Room.MASTER -> listOf(Color(0xFFE53935), Color(0xFF7F0000))
    }
    Column(
        modifier
            .fillMaxHeight()
            .testTag("room_${room.name.lowercase()}")
            .alpha(if (affordable) 1f else 0.55f)
            .background(Brush.verticalGradient(colors), shape)
            .border(2.dp, DdzColors.Gold.copy(alpha = 0.7f), shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(if (compact) 12.dp else 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(room.title, color = Color.White, fontSize = if (compact) 24.sp else 26.sp, fontWeight = FontWeight.Black, maxLines = 1)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("底分 ${room.baseScore}", color = DdzColors.Gold, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Spacer(Modifier.height(4.dp))
            Text("对手：${room.difficulty.zh}", color = Color.White, fontSize = 15.sp, maxLines = 1)
            Spacer(Modifier.height(8.dp))
            Text(room.blurb, color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp, textAlign = TextAlign.Center)
        }
        Text(
            if (affordable) "入场 ≥ ${formatCoins(room.minCoins)}" else "需 ${formatCoins(room.minCoins)} 金币",
            color = Color.White.copy(alpha = 0.9f), fontSize = 13.sp, maxLines = 1,
            modifier = Modifier.background(Color(0x33000000), RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}
