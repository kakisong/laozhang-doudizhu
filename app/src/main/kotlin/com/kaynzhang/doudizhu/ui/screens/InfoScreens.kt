package com.kaynzhang.doudizhu.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kaynzhang.doudizhu.data.Speed
import com.kaynzhang.doudizhu.data.BuiltInPortraits
import com.kaynzhang.doudizhu.game.GameViewModel
import com.kaynzhang.doudizhu.ui.common.ButtonKind
import com.kaynzhang.doudizhu.ui.common.GameButton
import com.kaynzhang.doudizhu.ui.common.LineIcon
import com.kaynzhang.doudizhu.ui.common.MessageHost
import com.kaynzhang.doudizhu.ui.common.Overlay
import com.kaynzhang.doudizhu.ui.common.Panel
import com.kaynzhang.doudizhu.ui.common.PlayerPortrait
import com.kaynzhang.doudizhu.ui.common.TableBackdrop
import com.kaynzhang.doudizhu.ui.common.UiIcon
import com.kaynzhang.doudizhu.ui.common.formatCoins
import com.kaynzhang.doudizhu.ui.theme.DdzColors
import com.kaynzhang.doudizhu.ui.common.LocalUiSound
import kotlin.math.roundToInt

/** A shared page frame keeps secondary pages quiet, legible, and scrollable on short phones. */
@Composable
private fun Page(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    val clickSound = LocalUiSound.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        TableBackdrop()
        val compact = maxWidth < 760.dp
        Column(
            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.displayCutout)
                .padding(horizontal = if (compact) 20.dp else 36.dp, vertical = 12.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier.testTag("btn_page_back").heightIn(min = 48.dp)
                        .clickable(role = Role.Button, onClick = { clickSound(); onBack() }).padding(end = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    LineIcon(UiIcon.BACK, Modifier.size(20.dp), DdzColors.Cream)
                    Text("返回", color = DdzColors.Cream, fontSize = 17.sp, lineHeight = 24.sp)
                }
                Box(Modifier.width(1.dp).height(22.dp).background(DdzColors.Gold.copy(alpha = 0.3f)))
                Spacer(Modifier.width(20.dp))
                Text(title, color = DdzColors.Cream, fontSize = 26.sp, lineHeight = 34.sp, fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.height(8.dp))
            Hairline()
            Spacer(Modifier.height(16.dp))
            Box(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()), contentAlignment = Alignment.TopCenter) {
                content()
            }
        }
    }
}

@Composable
private fun Hairline() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(DdzColors.Cream.copy(alpha = 0.12f)))
}

@Composable
fun SettingsScreen(vm: GameViewModel, onBack: () -> Unit) {
    val data by vm.appData.collectAsStateWithLifecycle()
    val current = data ?: return
    val s = current.settings
    var confirmReset by remember { mutableStateOf(false) }
    var choosePortrait by remember { mutableStateOf(false) }
    val portrait = BuiltInPortraits.resolve(current.profile.avatarId)
    Page("设置", onBack) {
        Column(Modifier.widthIn(max = 760.dp).fillMaxWidth().padding(bottom = 16.dp)) {
            Text("我的形象", color = DdzColors.Gold, fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium)
            Row(
                Modifier.fillMaxWidth().heightIn(min = 88.dp)
                    .clickable(role = Role.Button) { vm.clickUi(); choosePortrait = true }
                    .testTag("btn_choose_portrait").padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                PlayerPortrait(portrait.id, Modifier.size(58.dp))
                Column(Modifier.weight(1f)) {
                    Text(portrait.name, color = DdzColors.Cream, fontSize = 19.sp, lineHeight = 26.sp)
                    Text("${BuiltInPortraits.all.size} 款内置形象，点击更换", color = DdzColors.TextMuted, fontSize = 15.sp, lineHeight = 21.sp)
                }
                LineIcon(UiIcon.CHEVRON, Modifier.size(20.dp), DdzColors.TextMuted)
            }
            Hairline()
            Spacer(Modifier.height(20.dp))
            Text("声音与牌桌", color = DdzColors.Gold, fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(6.dp))
            ToggleRow("背景音乐", "内置轻快音乐，离线循环播放", "music", s.music) { v ->
                vm.updateSettings { it.copy(music = v) }
            }
            VolumeRow("音乐音量", s.musicVolume, s.music, "slider_music_volume", onCommit = { v ->
                vm.updateSettings { it.copy(musicVolume = v) }
            })
            Hairline()
            ToggleRow("音效", "选牌、出牌与轮到你的提示音", "音效（含轮到你时的提示音）", s.sound) { v ->
                vm.updateSettings { it.copy(sound = v) }
            }
            VolumeRow("音效音量", s.soundVolume, s.sound, "slider_sound_volume", onCommit = { v ->
                vm.updateSettings { it.copy(soundVolume = v) }
            })
            GameButton("试听提示音", vm::previewSound, kind = ButtonKind.SECONDARY, tag = "btn_preview_sound", fontSize = 16)
            Spacer(Modifier.height(12.dp))
            Hairline()
            ToggleRow("语音报牌", "内置自然人声，离线也能听清报牌", "语音报牌", s.voice) { v ->
                vm.updateSettings { it.copy(voice = v) }
            }
            VolumeRow("语音音量", s.voiceVolume, s.voice, "slider_voice_volume", onCommit = { v ->
                vm.updateSettings { it.copy(voiceVolume = v) }
            })
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf("温暖男声", "沉稳男声", "清亮女声").forEachIndexed { seat, label ->
                    GameButton(label, { vm.previewVoice(seat) }, Modifier.weight(1f), kind = ButtonKind.SECONDARY,
                        tag = "btn_preview_voice_$seat", fontSize = 16)
                }
            }
            Text("报牌时音乐和音效会自动减轻，让人声更清楚", Modifier.padding(top = 10.dp, bottom = 14.dp),
                color = DdzColors.TextMuted, fontSize = 15.sp, lineHeight = 22.sp)
            Hairline()
            ToggleRow("记牌器", "显示尚未打出的牌", "记牌器", s.cardCounter) { v ->
                vm.updateSettings { it.copy(cardCounter = v) }
            }
            Hairline()
            Row(
                Modifier.fillMaxWidth().heightIn(min = 84.dp).padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("出牌速度", color = DdzColors.Cream, fontSize = 19.sp, lineHeight = 26.sp)
                    Spacer(Modifier.height(3.dp))
                    Text("电脑出牌的快慢", color = DdzColors.Cream.copy(alpha = 0.65f), fontSize = 15.sp, lineHeight = 21.sp)
                }
                Row(
                    Modifier.background(DdzColors.PanelStrong, RoundedCornerShape(12.dp))
                        .border(1.dp, DdzColors.Cream.copy(alpha = 0.16f), RoundedCornerShape(12.dp)).padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    for (speed in Speed.entries) {
                        SpeedButton(speed.zh, selected = s.speed == speed, tag = "speed_${speed.name.lowercase()}") {
                            vm.updateSettings { it.copy(speed = speed) }
                        }
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            Hairline()
            Row(
                Modifier.fillMaxWidth().padding(top = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text("数据管理", color = DdzColors.Cream, fontSize = 19.sp, lineHeight = 26.sp)
                    Spacer(Modifier.height(3.dp))
                    Text("重新开始金币与战绩", color = DdzColors.Cream.copy(alpha = 0.65f), fontSize = 15.sp, lineHeight = 21.sp)
                }
                GameButton("重置金币和战绩", { confirmReset = true }, kind = ButtonKind.NEUTRAL, tag = "btn_reset", fontSize = 17)
            }
        }
    }
    if (choosePortrait) {
        PortraitPicker(
            selectedId = portrait.id,
            onSelect = { vm.setAvatar(it); choosePortrait = false },
            onDismiss = { choosePortrait = false },
        )
    }
    if (confirmReset) {
        Overlay {
            Panel(Modifier.widthIn(max = 560.dp).padding(horizontal = 18.dp)) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("确定重置？", color = DdzColors.Cream, fontSize = 26.sp, lineHeight = 34.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(12.dp))
                    Text("金币、战绩和没打完的牌局都会清除。", color = DdzColors.Cream.copy(alpha = 0.8f), fontSize = 18.sp, lineHeight = 27.sp)
                    Spacer(Modifier.height(24.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
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
private fun VolumeRow(title: String, value: Int, enabled: Boolean, tag: String, onCommit: (Int) -> Unit) {
    var level by remember(value) { mutableFloatStateOf(value.coerceIn(0, 100).toFloat()) }
    Column(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = DdzColors.Cream, fontSize = 16.sp, modifier = Modifier.weight(1f))
            Text(if (!enabled) "已关闭" else if (level < 1f) "静音" else "${level.roundToInt()}%",
                color = DdzColors.Gold, fontSize = 16.sp)
        }
        Slider(value = level, onValueChange = { level = it }, valueRange = 0f..100f, steps = 19,
            enabled = enabled, onValueChangeFinished = { onCommit(level.roundToInt()) },
            modifier = Modifier.fillMaxWidth().testTag(tag).semantics { contentDescription = title },
            colors = SliderDefaults.colors(thumbColor = DdzColors.Gold, activeTrackColor = DdzColors.Gold,
                inactiveTrackColor = DdzColors.Cream.copy(alpha = 0.16f)))
    }
}

/** The entire row is a 76dp switch target; the detail line stays separate from its setting name. */
@Composable
private fun ToggleRow(title: String, detail: String, tagLabel: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val clickSound = LocalUiSound.current
    Row(
        Modifier.fillMaxWidth().heightIn(min = 76.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = { clickSound(); onChange(it) })
            .testTag("switch_$tagLabel").padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = DdzColors.Cream, fontSize = 19.sp, lineHeight = 26.sp)
            Spacer(Modifier.height(3.dp))
            Text(detail, color = DdzColors.Cream.copy(alpha = 0.65f), fontSize = 15.sp, lineHeight = 21.sp)
        }
        Switch(
            checked = checked, onCheckedChange = null, modifier = Modifier.padding(start = 16.dp),
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color(0xFF17392D), checkedTrackColor = DdzColors.Gold,
                checkedBorderColor = Color.Transparent,
                uncheckedThumbColor = DdzColors.Cream.copy(alpha = 0.75f),
                uncheckedTrackColor = DdzColors.PanelStrong,
                uncheckedBorderColor = DdzColors.Cream.copy(alpha = 0.25f),
            ),
        )
    }
}

@Composable
private fun SpeedButton(text: String, selected: Boolean, tag: String, onClick: () -> Unit) {
    val clickSound = LocalUiSound.current
    val shape = RoundedCornerShape(8.dp)
    Box(
        Modifier.testTag(tag).size(64.dp, 52.dp)
            .background(if (selected) DdzColors.Gold else Color.Transparent, shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = { clickSound(); onClick() }),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (selected) Color(0xFF17392D) else DdzColors.Cream, fontSize = 19.sp, lineHeight = 26.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun StatsScreen(vm: GameViewModel, onBack: () -> Unit) {
    val data by vm.appData.collectAsStateWithLifecycle()
    val current = data ?: return
    val st = current.stats
    fun rate(w: Int, n: Int) = if (n == 0) "—" else "%.0f%%".format(100.0 * w / n)
    val tiles = listOf(
        "总局数" to "${st.games}",
        "胜率" to rate(st.wins, st.games),
        "地主胜率" to rate(st.landlordWins, st.landlordGames),
        "农民胜率" to rate(st.farmerWins, st.farmerGames),
        "单局最高赢取" to formatCoins(st.bestWin),
        "春天次数" to "${st.springs}",
    )
    Page("战绩", onBack) {
        Column(Modifier.widthIn(max = 820.dp).fillMaxWidth().padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            DailyStatsSection(current)
            Spacer(Modifier.height(12.dp))
            Hairline()
            Spacer(Modifier.height(4.dp))
            Text("累计战绩", color = DdzColors.Gold, fontSize = 18.sp, lineHeight = 25.sp, fontWeight = FontWeight.Medium)
            for (row in tiles.chunked(3)) {
                Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    for ((label, value) in row) StatTile(label, value, Modifier.weight(1f).fillMaxHeight())
                }
            }
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier.background(DdzColors.PanelStrong, shape)
            .border(1.dp, DdzColors.Gold.copy(alpha = 0.18f), shape).padding(horizontal = 18.dp, vertical = 18.dp),
    ) {
        Text(
            value, color = DdzColors.Gold, fontWeight = FontWeight.Medium, maxLines = 1, lineHeight = 42.sp,
            autoSize = TextAutoSize.StepBased(minFontSize = 24.sp, maxFontSize = 34.sp),
        )
        Spacer(Modifier.height(8.dp))
        Text(label, color = DdzColors.Cream.copy(alpha = 0.8f), fontSize = 16.sp, lineHeight = 22.sp, maxLines = 1)
    }
}

@Composable
fun RulesScreen(onBack: () -> Unit) {
    Page("玩法说明", onBack) {
        Column(Modifier.widthIn(max = 860.dp).fillMaxWidth().padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            for ((index, rule) in RULES.withIndex()) {
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    Text("%02d".format(index + 1), color = DdzColors.Gold.copy(alpha = 0.75f), fontSize = 16.sp, lineHeight = 22.sp, modifier = Modifier.width(32.dp).padding(top = 3.dp))
                    Column(Modifier.weight(1f)) {
                        Text(rule.first, color = DdzColors.Gold, fontSize = 20.sp, lineHeight = 27.sp, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(8.dp))
                        Text(rule.second, color = DdzColors.Cream.copy(alpha = 0.9f), fontSize = 18.sp, lineHeight = 29.sp)
                        Spacer(Modifier.height(20.dp))
                        Hairline()
                    }
                }
            }
        }
    }
}

/** Gameplay rules mirror docs/RULES.md; room details describe the available opponents. */
private val RULES = listOf(
    "基本玩法" to "三人游戏，一副 54 张牌，每人 17 张，留 3 张底牌。叫到地主的人拿走底牌，单独对抗另外两名农民。谁先出完手牌，谁的一方获胜。",
    "场次与难度" to "新手场、普通场、高手场、超级场分别对应简单、普通、困难、超级四档难度。超级场使用预训练的 DouZero AI 模型，模型随游戏内置，在手机上离线出牌，无需联网。所有对手都只根据自己的手牌和公开信息决策，不偷看其他人的手牌。超级场底分 10,000，入场需 100,000 金币。",
    "牌的大小" to "大王 > 小王 > 2 > A > K > Q > J > 10 > … > 3，不分花色。顺子、连对、飞机只能用 3 到 A，不能含 2 和王。",
    "牌型" to "单张、对子、三张、三带一、三带一对、顺子（5 张起）、连对（3 对起）、飞机（2 组连续三张起，可以带同样数量的单牌或对子作翅膀）、四带二（带两张单牌或两对）、炸弹（四张相同）、王炸（大小王）。",
    "带牌规则" to "带的牌不能与主体同点数（例如 33334444 不能出）；带牌里不能同时有大小王；带牌里不能有四张相同的牌。",
    "压牌" to "王炸最大；炸弹可以压任何非炸弹的牌，炸弹之间比大小；其余牌型必须类型相同、张数相同，而且主体更大才能压。四带二不是炸弹。",
    "叫地主与抢地主" to "随机一人先叫。叫过“不叫”的人不能再抢；有人叫地主后，后面的人可以抢，每抢一次倍数翻倍；如果有人抢过，叫地主的人最后还能反抢一次。三人都不叫就重新发牌。",
    "加倍" to "地主确定后，三人同时选择是否加倍，互相看不到。地主加倍对两家农民都生效，农民加倍只影响自己与地主之间的输赢。",
    "翻倍与结算" to "每个炸弹或王炸倍数翻倍。春天（地主出完而农民一张没出）或反春（地主只出了第一手）再翻倍。每位农民与地主之间的输赢 = 底分 × 倍数，输赢不会超过双方携带的金币。",
    "操作" to "点一下手牌选中，再点一下取消；在手牌上横着划，可以一次选中或取消一串牌。不知道出什么就点“提示”，它会依次推荐能出的牌；选好后点“出牌”，出不了的时候会告诉你原因。按钮的位置不会变：左边是“不出”“不叫”这类放弃，中间是“提示”，右边是“出牌”“叫地主”这类确定。跟牌时，要压的那手牌带金色底板；压不过时会自动“要不起”。轮到你时会“叮”一声。点“暂停”会停住牌局，点“继续”后接着打。打开“更多”里的“电脑代打”，可以开启或取消电脑代打；代打时也可以点牌桌下方的“取消托管”收回。",
)
