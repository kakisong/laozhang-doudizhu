package com.kaynzhang.doudizhu

import android.content.pm.PackageManager
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kaynzhang.doudizhu.engine.ai.Bot
import com.kaynzhang.doudizhu.engine.ai.BotContext
import com.kaynzhang.doudizhu.engine.ai.Bots
import com.kaynzhang.doudizhu.engine.ai.SuperBot
import com.kaynzhang.doudizhu.engine.game.Action
import com.kaynzhang.doudizhu.engine.game.GameConfig
import com.kaynzhang.doudizhu.engine.game.GameEngine
import com.kaynzhang.doudizhu.engine.game.Observation
import com.kaynzhang.doudizhu.engine.game.Phase
import com.kaynzhang.doudizhu.engine.model.CardSet
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the bundled models and Android JNI, with fallback forbidden so packaging failures fail. */
@RunWith(AndroidJUnit4::class)
class OfflineAiSmokeTest {
    @Test
    fun allThreeModelsPlayCompleteGamesWithoutNetworkOrFallback(): Unit = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        @Suppress("DEPRECATION")
        val permissions = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS).requestedPermissions
        assertTrue(permissions?.none { it == android.Manifest.permission.INTERNET } ?: true)
        for (name in listOf("landlord", "landlord_up", "landlord_down")) {
            assertTrue("Missing packaged $name model", javaClass.getResourceAsStream("/douzero/$name.onnx")?.use { it.read() >= 0 } == true)
        }
        val unavailable = object : Bot {
            override suspend fun act(obs: Observation, ctx: BotContext): Action =
                error("DouZero inference fell back at seat ${obs.seat}, seq ${obs.seq}")
        }
        val bots = List(3) { SuperBot(fallback = unavailable) }
        val roleTurns = IntArray(3)
        val times = mutableListOf<Double>()
        for (seed in 310L..312L) {
            var state = GameEngine.newGame(GameConfig(100, List(3) { 100_000L }), seed).state
            var steps = 0
            while (state.phase != Phase.FINISHED) {
                val seat = GameEngine.actors(state).first()
                val started = System.nanoTime()
                val action = bots[seat].act(GameEngine.observe(state, seat), BotContext(Bots.decisionSeed(state, seat)))
                if (state.phase == Phase.PLAYING) {
                    times += (System.nanoTime() - started) / 1e6
                    roleTurns[(seat - state.landlord + 3) % 3]++
                }
                state = GameEngine.apply(state, action).state
                var all = CardSet.EMPTY
                var count = 0
                for (hand in state.hands) { all += hand; count += hand.size }
                for (record in state.log) { all += record.cards; count += record.cards.size }
                if (state.landlord < 0) { all += state.bottom; count += state.bottom.size }
                assertEquals(CardSet.FULL_DECK, all)
                assertEquals(54, count)
                assertTrue("Game stalled", ++steps < 1_000)
            }
            assertEquals(0L, state.result!!.delta.sum())
        }
        assertTrue(roleTurns.all { it > 0 })
        Log.i("DouZeroSmoke", "Android offline games=3 decisions=${times.size} meanMs=${times.average()} maxMs=${times.maxOrNull()}")
    }
}
