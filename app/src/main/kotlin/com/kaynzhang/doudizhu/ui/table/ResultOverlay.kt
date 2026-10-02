package com.kaynzhang.doudizhu.ui.table

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kaynzhang.doudizhu.engine.game.GameResult
import com.kaynzhang.doudizhu.game.HUMAN
import com.kaynzhang.doudizhu.game.TableUi
import com.kaynzhang.doudizhu.ui.common.ButtonKind
import com.kaynzhang.doudizhu.ui.common.GameButton
import com.kaynzhang.doudizhu.ui.common.Overlay
import com.kaynzhang.doudizhu.ui.common.Panel
import com.kaynzhang.doudizhu.ui.common.formatCoins
import com.kaynzhang.doudizhu.ui.theme.DdzColors

@Composable
fun ResultOverlay(ui: TableUi, onAgain: () -> Unit, onLobby: () -> Unit) {
    val r = ui.result ?: return
    val won = (r.landlord == HUMAN) == r.landlordWon
    Overlay {
        Panel(Modifier.widthIn(min = 360.dp, max = 540.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (won) "胜 利" else "失 败", color = if (won) DdzColors.Win else DdzColors.Lose, fontSize = 34.sp, fontWeight = FontWeight.Black)
                val tag = when {
                    r.spring -> " · 春天"
                    r.antiSpring -> " · 反春"
                    else -> ""
                }
                Text((if (r.landlordWon) "地主获胜" else "农民获胜") + tag, color = DdzColors.Cream, fontSize = 14.sp)
                Spacer(Modifier.height(12.dp))
                for (seat in listOf(HUMAN, 1, 2)) {
                    val s = ui.seats[seat]
                    val delta = r.delta[seat]
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(s.avatar, fontSize = 20.sp)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            s.name + if (seat == r.landlord) "（地主）" else "（农民）",
                            color = if (seat == HUMAN) DdzColors.Gold else DdzColors.Cream, fontSize = 15.sp,
                            modifier = Modifier.weight(1f),
                        )
                        Text("×${r.multiplierFor(seat)}", color = DdzColors.Cream, fontSize = 14.sp, modifier = Modifier.width(72.dp))
                        Text(
                            (if (delta >= 0) "+" else "−") + formatCoins(kotlin.math.abs(delta)),
                            color = if (delta >= 0) Color(0xFFFFD54F) else Color(0xFF90CAF9),
                            fontSize = 16.sp, fontWeight = FontWeight.Bold,
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(breakdown(ui.baseScore, r), color = DdzColors.Cream.copy(alpha = 0.8f), fontSize = 12.sp)
                if (r.capped) {
                    Text("本局输赢超过了金币上限，已按金币封顶", color = DdzColors.Gold, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                }
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    GameButton("返回大厅", onLobby, kind = ButtonKind.NEUTRAL, tag = "btn_lobby")
                    GameButton("再来一局", onAgain, tag = "btn_again")
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
