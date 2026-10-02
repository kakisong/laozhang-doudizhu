package com.kaynzhang.doudizhu.audio

import com.kaynzhang.doudizhu.engine.game.BidKind
import com.kaynzhang.doudizhu.engine.game.GameEvent
import com.kaynzhang.doudizhu.engine.rules.Combo
import com.kaynzhang.doudizhu.engine.rules.ComboType

/** Stable asset keys shared by the game planner and the generated voice catalog. */
object VoicePhrases {
    private val rankKeys = listOf("3", "4", "5", "6", "7", "8", "9", "10", "j", "q", "k", "a", "2", "sj", "bj")
    private val ranks = listOf("三", "四", "五", "六", "七", "八", "九", "十", "勾", "圈", "K", "尖", "二", "小王", "大王")

    val all: Map<String, String> = buildMap {
        rankKeys.forEachIndexed { index, key -> put("single_$key", ranks[index]) }
        rankKeys.take(13).forEachIndexed { index, key ->
            put("pair_$key", "对${ranks[index]}")
            put("triple_$key", "三个${ranks[index]}")
        }
        putAll(mapOf(
            "triple_single" to "三带一",
            "triple_pair" to "三带一对",
            "straight" to "顺子",
            "pair_straight" to "连对",
            "plane" to "飞机",
            "plane_singles" to "飞机带单",
            "plane_pairs" to "飞机带对",
            "four_two_singles" to "四带二",
            "four_two_pairs" to "四带两对",
            "bomb" to "炸弹",
            "rocket" to "王炸",
            "call" to "叫地主",
            "no_call" to "不叫",
            "rob" to "抢地主",
            "no_rob" to "不抢",
            "double" to "加倍",
            "no_double" to "不加倍",
            "pass_0" to "不要",
            "pass_1" to "要不起",
            "pass_2" to "过",
            "alert_one" to "我只剩一张牌啦",
            "alert_two" to "我只剩两张牌啦",
            "landlord" to "我来当地主",
            "win" to "赢啦",
            "lose" to "下把再来",
            "spring" to "春天",
            "anti_spring" to "反春天",
            "redeal" to "没人叫地主，重新发牌",
            "trustee_on" to "交给我吧",
            "trustee_off" to "回来啦",
            "error_empty" to "先选好要出的牌",
            "error_combo" to "这手牌型不对哦",
            "error_small" to "这手牌还不够大",
            "turn" to "轮到你出牌啦",
            "relief" to "救济金到账啦",
        ))
    }

    private val keysByText = all.entries.associate { it.value to it.key }

    fun text(key: String): String? = all[key]

    fun keyForText(text: String): String? = keysByText[text.trim()] ?: text.takeIf { it in all }

    fun key(event: GameEvent, seq: Int): String? = when (event) {
        is GameEvent.BidMade -> when (event.kind) {
            BidKind.CALL -> "call"
            BidKind.NO_CALL -> "no_call"
            BidKind.ROB -> "rob"
            BidKind.NO_ROB -> "no_rob"
        }
        is GameEvent.Played -> comboKey(event.combo)
        // “要不起” is reserved for the planner's verified cannot-beat case.
        is GameEvent.Passed -> if (Math.floorMod(seq + event.seat, 2) == 0) "pass_0" else "pass_2"
        is GameEvent.Alert -> when (event.cardsLeft) { 1 -> "alert_one"; 2 -> "alert_two"; else -> null }
        is GameEvent.LandlordSet -> "landlord"
        is GameEvent.Dealt -> "redeal".takeIf { event.isRedeal }
        else -> null
    }

    fun comboKey(combo: Combo): String = when (combo.type) {
        ComboType.SINGLE -> "single_${rankKeys[combo.rank]}"
        ComboType.PAIR -> "pair_${rankKeys[combo.rank]}"
        ComboType.TRIPLE -> "triple_${rankKeys[combo.rank]}"
        ComboType.TRIPLE_SINGLE -> "triple_single"
        ComboType.TRIPLE_PAIR -> "triple_pair"
        ComboType.STRAIGHT -> "straight"
        ComboType.PAIR_STRAIGHT -> "pair_straight"
        ComboType.PLANE -> "plane"
        ComboType.PLANE_SINGLES -> "plane_singles"
        ComboType.PLANE_PAIRS -> "plane_pairs"
        ComboType.FOUR_TWO_SINGLES -> "four_two_singles"
        ComboType.FOUR_TWO_PAIRS -> "four_two_pairs"
        ComboType.BOMB -> "bomb"
        ComboType.ROCKET -> "rocket"
    }
}
