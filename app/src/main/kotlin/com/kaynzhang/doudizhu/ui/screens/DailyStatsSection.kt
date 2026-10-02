package com.kaynzhang.doudizhu.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kaynzhang.doudizhu.data.AppData
import com.kaynzhang.doudizhu.data.DailyStats
import com.kaynzhang.doudizhu.data.Stats
import com.kaynzhang.doudizhu.ui.common.formatCoins
import com.kaynzhang.doudizhu.ui.theme.DdzColors
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/** Daily game history; the surrounding stats page owns scrolling. */
@Composable
fun DailyStatsSection(data: AppData) {
    val today = LocalDate.now()
    val daily = data.dailyStats[today.toString()] ?: DailyStats()
    val fontScale = LocalDensity.current.fontScale
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("今日对局", color = DdzColors.Cream, fontSize = 22.sp, lineHeight = 30.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.weight(1f))
            Text(today.format(DAY_LABEL), color = DdzColors.TextMuted, fontSize = 15.sp, lineHeight = 21.sp)
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val columns = if (maxWidth >= 700.dp && fontScale <= 1.12f) 4 else 2
            val values = listOf(
                DailyValue("局数", "${daily.stats.games}", "daily_today_games"),
                DailyValue("胜场", "${daily.stats.wins}", "daily_today_wins"),
                DailyValue("胜率", winRate(daily.stats), "daily_today_rate"),
                DailyValue("净金币", signedCoins(daily.netCoins), "daily_today_coins", coinColor(daily.netCoins)),
            )
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                for (row in values.chunked(columns)) {
                    Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        for (value in row) TodayMetric(value, Modifier.weight(1f).fillMaxHeight())
                    }
                }
            }
        }
        Spacer(Modifier.height(2.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("近七日", color = DdzColors.Cream, fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.weight(1f))
            Text("按牌局结束日期记录", color = DdzColors.TextMuted, fontSize = 14.sp, lineHeight = 20.sp)
        }
        val shape = RoundedCornerShape(14.dp)
        Column(
            Modifier.fillMaxWidth().background(DdzColors.PanelStrong, shape)
                .border(1.dp, DdzColors.Gold.copy(alpha = 0.18f), shape).padding(horizontal = 16.dp),
        ) {
            Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                HistoryHeader("日期", Modifier.weight(1.2f), TextAlign.Start)
                HistoryHeader("局数", Modifier.weight(1f))
                HistoryHeader("胜率", Modifier.weight(1f))
                HistoryHeader("净金币", Modifier.weight(1.3f))
            }
            DailyHairline()
            for (offset in 0L..6L) {
                val day = today.minusDays(offset)
                val record = data.dailyStats[day.toString()]
                val stats = record ?: DailyStats()
                Row(
                    Modifier.fillMaxWidth().testTag("daily_row_$day").heightIn(min = 60.dp).padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1.2f)) {
                        Text(day.format(HISTORY_LABEL), color = if (offset == 0L) DdzColors.Gold else DdzColors.Cream, fontSize = 16.sp, lineHeight = 22.sp)
                        val note = if (offset == 0L) "今天" else if (record == null) "无记录" else null
                        if (note != null) Text(note, color = DdzColors.TextMuted, fontSize = 14.sp, lineHeight = 20.sp)
                    }
                    HistoryValue("${stats.stats.games}", Modifier.weight(1f))
                    HistoryValue(winRate(stats.stats), Modifier.weight(1f))
                    HistoryValue(signedCoins(stats.netCoins), Modifier.weight(1.3f), coinColor(stats.netCoins))
                }
                if (offset < 6L) DailyHairline()
            }
        }
    }
}

private data class DailyValue(val label: String, val value: String, val tag: String, val color: Color = DdzColors.Gold)

@Composable
private fun TodayMetric(value: DailyValue, modifier: Modifier) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier.background(DdzColors.PanelStrong, shape)
            .border(1.dp, DdzColors.Gold.copy(alpha = 0.18f), shape).padding(horizontal = 16.dp, vertical = 16.dp),
    ) {
        Text(value.label, color = DdzColors.TextMuted, fontSize = 16.sp, lineHeight = 22.sp)
        Spacer(Modifier.height(6.dp))
        Text(
            value.value, color = value.color, fontWeight = FontWeight.Medium, maxLines = 1,
            lineHeight = 34.sp, modifier = Modifier.testTag(value.tag),
            autoSize = TextAutoSize.StepBased(minFontSize = 16.sp, maxFontSize = 28.sp),
        )
    }
}

@Composable
private fun HistoryHeader(text: String, modifier: Modifier, alignment: TextAlign = TextAlign.End) {
    Text(text, modifier = modifier, color = DdzColors.TextMuted, fontSize = 15.sp, lineHeight = 21.sp, textAlign = alignment, maxLines = 1)
}

@Composable
private fun HistoryValue(text: String, modifier: Modifier, color: Color = DdzColors.Cream) {
    Text(
        text, modifier = modifier, color = color, lineHeight = 24.sp, maxLines = 1, textAlign = TextAlign.End,
        autoSize = TextAutoSize.StepBased(minFontSize = 14.sp, maxFontSize = 16.sp),
    )
}

@Composable
private fun DailyHairline() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(DdzColors.Hairline.copy(alpha = 0.5f)))
}

private fun winRate(stats: Stats): String =
    if (stats.games == 0) "—" else "${(100.0 * stats.wins / stats.games).roundToInt()}%"

private fun signedCoins(coins: Long): String = when {
    coins > 0 -> "+${formatCoins(coins)}"
    coins < 0 -> "−${formatCoins(-coins)}"
    else -> "0"
}

private fun coinColor(coins: Long): Color = if (coins > 0) DdzColors.Gold else DdzColors.Cream

private val DAY_LABEL = DateTimeFormatter.ofPattern("M月d日")
private val HISTORY_LABEL = DateTimeFormatter.ofPattern("M/d")
