package com.kaynzhang.doudizhu.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kaynzhang.doudizhu.data.Speed
import com.kaynzhang.doudizhu.game.GameViewModel
import com.kaynzhang.doudizhu.ui.common.ButtonKind
import com.kaynzhang.doudizhu.ui.common.Chip
import com.kaynzhang.doudizhu.ui.common.GameButton
import com.kaynzhang.doudizhu.ui.common.MessageHost
import com.kaynzhang.doudizhu.ui.common.Overlay
import com.kaynzhang.doudizhu.ui.common.Panel
import com.kaynzhang.doudizhu.ui.common.formatCoins
import com.kaynzhang.doudizhu.ui.theme.DdzColors

/** Common frame: title with a back chip and a scrollable body. */
@Composable
private fun Page(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(DdzColors.felt)) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.displayCutout).padding(horizontal = 28.dp, vertical = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Chip("‹ 返回", onClick = onBack, tag = "btn_page_back")
                Spacer(Modifier.width(16.dp))
                Text(title, color = DdzColors.Gold, fontSize = 24.sp, fontWeight = FontWeight.Black)
            }
            Spacer(Modifier.height(12.dp))
            Box(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())) { content() }
        }
    }
}

@Composable
fun SettingsScreen(vm: GameViewModel, onBack: () -> Unit) {
    val data by vm.appData.collectAsStateWithLifecycle()
    val s = data?.settings ?: return
    var confirmReset by remember { mutableStateOf(false) }
    Page("设置", onBack) {
        Column(Modifier.widthIn(max = 560.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ToggleRow("音效", s.sound) { v -> vm.updateSettings { it.copy(sound = v) } }
            ToggleRow("语音报牌（需要系统中文语音）", s.voice) { v -> vm.updateSettings { it.copy(voice = v) } }
            ToggleRow("记牌器", s.cardCounter) { v -> vm.updateSettings { it.copy(cardCounter = v) } }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 6.dp)) {
                Text("出牌速度", color = DdzColors.Cream, fontSize = 17.sp, modifier = Modifier.weight(1f))
                for (speed in Speed.entries) {
                    Chip(speed.zh, onClick = { vm.updateSettings { it.copy(speed = speed) } }, active = s.speed == speed, tag = "speed_${speed.name.lowercase()}")
                    Spacer(Modifier.width(8.dp))
                }
            }
            Spacer(Modifier.height(12.dp))
            GameButton("重置金币和战绩", { confirmReset = true }, kind = ButtonKind.NEUTRAL, tag = "btn_reset", fontSize = 15)
        }
    }
    if (confirmReset) {
        Overlay {
            Panel {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("确定重置？", color = DdzColors.Gold, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text("金币、战绩和未完成的牌局都会清除。", color = DdzColors.Cream, fontSize = 15.sp)
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        GameButton("取消", { confirmReset = false }, kind = ButtonKind.SECONDARY)
                        GameButton("重置", { vm.resetData(); confirmReset = false }, kind = ButtonKind.NEUTRAL, tag = "btn_reset_confirm")
                    }
                }
            }
        }
    }
    MessageHost(vm.messages)
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, color = DdzColors.Cream, fontSize = 17.sp, modifier = Modifier.weight(1f))
        Switch(
            checked = checked, onCheckedChange = onChange, modifier = Modifier.testTag("switch_$label"),
            colors = SwitchDefaults.colors(checkedThumbColor = DdzColors.Gold, checkedTrackColor = DdzColors.OrangeDeep),
        )
    }
}

@Composable
fun StatsScreen(vm: GameViewModel, onBack: () -> Unit) {
    val data by vm.appData.collectAsStateWithLifecycle()
    val st = data?.stats ?: return
    fun rate(w: Int, n: Int) = if (n == 0) "—" else "%.0f%%".format(100.0 * w / n)
    Page("战绩", onBack) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            StatTile("总局数", "${st.games}")
            StatTile("胜率", rate(st.wins, st.games))
            StatTile("地主胜率", rate(st.landlordWins, st.landlordGames))
            StatTile("农民胜率", rate(st.farmerWins, st.farmerGames))
            StatTile("单局最高赢取", formatCoins(st.bestWin))
            StatTile("春天次数", "${st.springs}")
        }
    }
}

@Composable
private fun StatTile(label: String, value: String) {
    Panel {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.widthIn(min = 88.dp)) {
            Text(value, color = DdzColors.Gold, fontSize = 26.sp, fontWeight = FontWeight.Black)
            Text(label, color = DdzColors.Cream, fontSize = 13.sp)
        }
    }
}

@Composable
fun RulesScreen(onBack: () -> Unit) {
    Page("玩法说明", onBack) {
        Column(Modifier.widthIn(max = 760.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            for ((title, body) in RULES) {
                Text(title, color = DdzColors.Gold, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(body, color = DdzColors.Cream, fontSize = 15.sp, lineHeight = 22.sp)
            }
        }
    }
}

/** Mirrors docs/RULES.md. */
private val RULES = listOf(
    "基本玩法" to "三人游戏，一副 54 张牌，每人 17 张，留 3 张底牌。叫到地主的人拿走底牌，单独对抗另外两名农民。谁先出完手牌，谁的一方获胜。",
    "牌的大小" to "大王 > 小王 > 2 > A > K > Q > J > 10 > … > 3，不分花色。顺子、连对、飞机只能用 3 到 A，不能含 2 和王。",
    "牌型" to "单张、对子、三张、三带一、三带一对、顺子（5 张起）、连对（3 对起）、飞机（2 组连续三张起，可以带同样数量的单牌或对子作翅膀）、四带二（带两张单牌或两对）、炸弹（四张相同）、王炸（大小王）。",
    "带牌规则" to "带的牌不能与主体同点数（例如 33334444 不能出）；带牌里不能同时有大小王；带牌里不能有四张相同的牌。",
    "压牌" to "王炸最大；炸弹可以压任何非炸弹的牌，炸弹之间比大小；其余牌型必须类型相同、张数相同，而且主体更大才能压。四带二不是炸弹。",
    "叫地主与抢地主" to "随机一人先叫。叫过“不叫”的人不能再抢；有人叫地主后，后面的人可以抢，每抢一次倍数翻倍；如果有人抢过，叫地主的人最后还能反抢一次。三人都不叫就重新发牌。",
    "加倍" to "地主确定后，三人同时选择是否加倍，互相看不到。地主加倍对两家农民都生效，农民加倍只影响自己与地主之间的输赢。",
    "翻倍与结算" to "每个炸弹或王炸倍数翻倍。春天（地主出完而农民一张没出）或反春（地主只出了第一手）再翻倍。每位农民与地主之间的输赢 = 底分 × 倍数，输赢不会超过双方携带的金币。",
    "操作" to "点击手牌选中，在手牌上横向滑动可以一次选中多张；点击空白处取消选择。“提示”会依次推荐能出的牌；压不过上家时会自动“要不起”。“托管”让电脑替你出牌。",
)
