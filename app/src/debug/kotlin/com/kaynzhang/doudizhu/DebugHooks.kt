package com.kaynzhang.doudizhu

import android.content.Intent
import android.util.Log
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import com.kaynzhang.doudizhu.data.Room
import com.kaynzhang.doudizhu.engine.game.GameConfig
import com.kaynzhang.doudizhu.engine.game.GameEngine
import com.kaynzhang.doudizhu.engine.model.CardSet
import com.kaynzhang.doudizhu.engine.model.Counts
import com.kaynzhang.doudizhu.engine.model.Rng
import com.kaynzhang.doudizhu.game.GameViewModel

/**
 * Test-harness entry points, compiled into debug builds only. Examples:
 *
 * ```
 * adb shell am start -n com.laozhang.doudizhu/com.kaynzhang.doudizhu.MainActivity --es room NOVICE --el seed 42 --ez autoplay true
 * adb shell am start -n com.laozhang.doudizhu/com.kaynzhang.doudizhu.MainActivity --es room NORMAL --es scenario bomb
 * adb shell am start -n com.laozhang.doudizhu/com.kaynzhang.doudizhu.MainActivity --es room NOVICE --ez autoplay true --ez loop true --ez turbo true
 * ```
 * Scenarios: bomb, rocket, plane, spring (the human gets a scripted hand, the rest is dealt by seed).
 */
object DebugHooks {

    @OptIn(ExperimentalComposeUiApi::class)
    val rootModifier: Modifier = Modifier.semantics { testTagsAsResourceId = true }

    fun parse(intent: Intent?): DebugLaunch? {
        val extras = intent?.extras ?: return null
        if (!extras.containsKey("room") && !extras.containsKey("scenario")) return null
        return DebugLaunch(
            room = runCatching { Room.valueOf(extras.getString("room") ?: "NORMAL") }.getOrDefault(Room.NORMAL),
            seed = if (extras.containsKey("seed")) extras.getLong("seed") else null,
            scenario = extras.getString("scenario"),
            autoplay = extras.getBoolean("autoplay", false),
            loop = extras.getBoolean("loop", false),
            turbo = extras.getBoolean("turbo", false),
        ).also { Log.i("DebugHooks", "launch $it") }
    }

    fun start(vm: GameViewModel, launch: DebugLaunch): Boolean {
        vm.turbo = launch.turbo
        val seed = launch.seed ?: System.nanoTime()
        val hand = SCENARIOS[launch.scenario]
        val coins = vm.appData.value?.profile?.coins ?: return false
        if (hand == null && launch.seed == null) {
            if (!vm.enterRoom(launch.room)) return false
        } else {
            val config = GameConfig(launch.room.baseScore, listOf(coins, 1_000_000L, 1_000_000L))
            val t = if (hand == null) {
                GameEngine.newGame(config, seed)
            } else {
                val mine = CardSet.fromCounts(Counts.parse(hand))
                val rest = (CardSet.FULL_DECK - mine).cards().toMutableList()
                Rng(seed).shuffle(rest)
                GameEngine.newGameWithHands(
                    config, seed,
                    hands = listOf(mine, CardSet.of(rest.subList(0, 17)), CardSet.of(rest.subList(17, 34))),
                    bottom = CardSet.of(rest.subList(34, 37)),
                    firstBidder = 0,
                )
            }
            vm.startScripted(launch.room, t.state, t.events)
        }
        vm.setTrustee(launch.autoplay)
        vm.keepTrustee = launch.autoplay && launch.loop
        vm.autoRestart = launch.loop
        return true
    }

    private val SCENARIOS = mapOf(
        "bomb" to "3333 5555 789 10 JQ A 22",
        "rocket" to "小王大王 22 AA KK QQ J 10 9 8 7 6 5",
        "plane" to "333444555 6789 10 JQK",
        "spring" to "小王大王 2222 AAA KKK QQQ JJ",
    )
}
