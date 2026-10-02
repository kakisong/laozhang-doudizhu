package com.kaynzhang.doudizhu.ui.table

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kaynzhang.doudizhu.engine.game.GameResult
import com.kaynzhang.doudizhu.game.HUMAN
import com.kaynzhang.doudizhu.game.TableUi
import com.kaynzhang.doudizhu.ui.common.ButtonKind
import com.kaynzhang.doudizhu.ui.common.GameButton
import com.kaynzhang.doudizhu.ui.common.LineIcon
import com.kaynzhang.doudizhu.ui.common.Overlay
import com.kaynzhang.doudizhu.ui.common.Panel
import com.kaynzhang.doudizhu.ui.common.UiIcon
import com.kaynzhang.doudizhu.ui.common.formatCoins
import com.kaynzhang.doudizhu.ui.theme.DdzColors

@Composable
fun ResultOverlay(ui: TableUi, onAgain: () -> Unit, onLobby: () -> Unit) {
    val r = ui.result ?: return
    val won = (r.landlord == HUMAN) == r.landlordWon
    Overlay {
        BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            // The ledger scrolls on short landscape screens; the result and both next steps stay visible.
            val short = maxHeight < 380.dp
            Panel(Modifier.widthIn(min = 360.dp, max = 560.dp).heightIn(max = maxHeight - 16.dp).padding(horizontal = 8.dp)) {
                Column {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(if (short) 42.dp else 50.dp)
                                .background(DdzColors.Gold.copy(alpha = if (won) 0.12f else 0.05f), CircleShape)
                                .border(0.5.dp, DdzColors.Gold.copy(alpha = 0.3f), CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            LineIcon(if (won) UiIcon.CROWN else UiIcon.CARDS, Modifier.size(if (short) 24.dp else 28.dp), color = DdzColors.Gold)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (won) "本局获胜" else "本局惜败", color = if (won) DdzColors.Gold else DdzColors.Cream,
                                fontSize = if (short) 28.sp else 32.sp, lineHeight = if (short) 33.sp else 38.sp, fontWeight = FontWeight.SemiBold,
                            )
                            val tag = when {
                                r.spring -> " · 春天"
                                r.antiSpring -> " · 反春"
                                else -> ""
                            }
                            Text((if (r.landlordWon) "地主获胜" else "农民获胜") + tag, color = DdzColors.Cream.copy(alpha = 0.64f), fontSize = 14.sp, lineHeight = 19.sp)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("我的金币", color = DdzColors.Cream.copy(alpha = 0.56f), fontSize = 12.sp, lineHeight = 16.sp)
                            val delta = r.delta[HUMAN]
                            Text(
                                (if (delta >= 0) "+" else "−") + formatCoins(kotlin.math.abs(delta)),
                                color = if (delta >= 0) DdzColors.Gold else DdzColors.Cream,
                                fontSize = if (short) 25.sp else 30.sp, lineHeight = if (short) 30.sp else 36.sp, fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                    Spacer(Modifier.height(if (short) 9.dp else 12.dp))
                    Column(
                        Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                    ) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("玩家", color = DdzColors.Cream.copy(alpha = 0.45f), fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.weight(1f))
                            Text("倍数", color = DdzColors.Cream.copy(alpha = 0.45f), fontSize = 12.sp, lineHeight = 16.sp, textAlign = TextAlign.Center, modifier = Modifier.width(72.dp))
                            Text("金币变动", color = DdzColors.Cream.copy(alpha = 0.45f), fontSize = 12.sp, lineHeight = 16.sp, textAlign = TextAlign.End, modifier = Modifier.width(94.dp))
                        }
                        for (seat in listOf(HUMAN, 1, 2)) {
                            val s = ui.seats[seat]
                            val delta = r.delta[seat]
                            Row(
                                Modifier.fillMaxWidth()
                                    .background(if (seat == HUMAN) DdzColors.Gold.copy(alpha = 0.08f) else DdzColors.Cream.copy(alpha = 0.025f), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 8.dp, vertical = if (short) 4.dp else 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RoleAvatar(s, if (short) 28 else 32, roleKnown = true, showDoubled = false)
                                Spacer(Modifier.width(8.dp))
                                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                    Text(
                                        s.name, color = DdzColors.Cream, fontSize = 16.sp,
                                        fontWeight = if (seat == HUMAN) FontWeight.SemiBold else FontWeight.Normal,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                                    )
                                    RoleBadge(seat == r.landlord)
                                }
                                Text(
                                    "×${r.multiplierFor(seat)}", color = DdzColors.Cream.copy(alpha = 0.7f),
                                    maxLines = 1, textAlign = TextAlign.Center, modifier = Modifier.width(72.dp),
                                    autoSize = TextAutoSize.StepBased(minFontSize = 11.sp, maxFontSize = 16.sp),
                                )
                                Text(
                                    (if (delta >= 0) "+" else "−") + formatCoins(kotlin.math.abs(delta)),
                                    color = if (delta >= 0) DdzColors.Gold else DdzColors.Cream.copy(alpha = 0.8f),
                                    fontSize = 19.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End, modifier = Modifier.width(94.dp),
                                )
                            }
                            Spacer(Modifier.height(3.dp))
                        }
                        Spacer(Modifier.height(if (short) 4.dp else 7.dp))
                        Text(breakdown(ui.baseScore, r), color = DdzColors.Cream.copy(alpha = 0.62f), fontSize = 13.sp, lineHeight = 19.sp, modifier = Modifier.padding(horizontal = 8.dp))
                        if (r.capped) {
                            Text(
                                "本局已按可用金币封顶结算", color = DdzColors.Gold.copy(alpha = 0.85f), fontSize = 13.sp,
                                modifier = Modifier.padding(start = 8.dp, top = 4.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(if (short) 9.dp else 12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        GameButton("返回大厅", onLobby, Modifier.weight(1f), kind = ButtonKind.NEUTRAL, tag = "btn_lobby", fontSize = 19)
                        GameButton("再来一局", onAgain, Modifier.weight(1f), tag = "btn_again", fontSize = 19)
                    }
                }
            }
        }
    }
}

private fun breakdown(base: Long, r: GameResult): String = buildList {
    add("底分 $base")
    if (r.robs > 0) add("抢地主 ×${1 shl r.robs}")
    if (r.bombs > 0) add("炸弹 ×${1L shl r.bombs}")
    if (r.spring) add("春天 ×2")
    if (r.antiSpring) add("反春 ×2")
    if (r.jiabei[r.landlord]) add("地主加倍 ×2")
    val farmers = r.jiabei.indices.count { it != r.landlord && r.jiabei[it] }
    if (farmers > 0) add("农民加倍 $farmers 人")
}.joinToString("  ·  ")
