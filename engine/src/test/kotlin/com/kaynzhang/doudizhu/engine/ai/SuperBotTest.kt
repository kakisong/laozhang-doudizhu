package com.kaynzhang.doudizhu.engine.ai

import com.kaynzhang.doudizhu.engine.game.Action
import com.kaynzhang.doudizhu.engine.game.GameEngine
import com.kaynzhang.doudizhu.engine.game.GameState
import com.kaynzhang.doudizhu.engine.game.Observation
import com.kaynzhang.doudizhu.engine.game.Phase
import com.kaynzhang.doudizhu.engine.game.Seats
import com.kaynzhang.doudizhu.engine.model.CardSet
import com.kaynzhang.doudizhu.engine.model.Counts
import com.kaynzhang.doudizhu.engine.rules.Combo
import com.kaynzhang.doudizhu.engine.rules.ComboType
import com.kaynzhang.doudizhu.engine.rules.KickerMode
import com.kaynzhang.doudizhu.engine.rules.MoveGenerator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SuperBotTest {
    private val ctx = BotContext(17, SearchBudget.Samples(4))
    private val mustInfer = object : Bot {
        override suspend fun act(obs: Observation, ctx: BotContext): Action = error("unexpected fallback")
    }
    private val greedy = DouZeroEvaluator { _, actions -> FloatArray(actions.size) { actions[it].total.toFloat() } }

    @Test
    fun `super difficulty factory selects the offline bot`() {
        assertIs<SuperBot>(Bots.create(Difficulty.SUPER))
    }

    @Test
    fun `model scores every legal lead including expensive kickers`() = runBlocking {
        val obs = observation("3334445556789TJQKA")
        val target = Counts.parse("333444KA")
        val expected = MoveGenerator.leads(obs.hand.counts(), KickerMode.ALL)
        assertTrue(expected.size > 64)
        val evaluator = DouZeroEvaluator { features, actions ->
            assertEquals(DouZeroPosition.LANDLORD, features.position)
            assertEquals(expected.map { it.counts }, actions)
            assertTrue(Counts.EMPTY !in actions)
            assertTrue(target in actions)
            FloatArray(actions.size) { if (actions[it] == target) 100f else 0f }
        }
        val action = SuperBot(evaluator, mustInfer).act(obs, ctx)
        assertEquals(target, assertIs<Action.Play>(action).cards.counts())
    }

    @Test
    fun `follow scores all legal responses and an optional pass`() = runBlocking {
        val trick = Combo(ComboType.TRIPLE_SINGLE, 0)
        val obs = observation("44456789TJQKA").copy(trick = trick, trickOwner = Seats.prev(0))
        val expected = MoveGenerator.beating(obs.hand.counts(), trick, KickerMode.ALL).map { it.counts } + Counts.EMPTY
        val evaluator = DouZeroEvaluator { _, actions ->
            assertEquals(expected, actions)
            FloatArray(actions.size) { if (actions[it].isEmpty) 1f else 0f }
        }
        assertEquals(Action.Pass(obs.seat), SuperBot(evaluator, mustInfer).act(obs, ctx))
    }

    @Test
    fun `winning full hand and forced pass bypass inference`() = runBlocking {
        val evaluator = DouZeroEvaluator { _, _ -> error("inference should be unnecessary") }
        for (obs in listOf(
            observation("333444"),
            observation("小王大王").copy(trick = Combo(ComboType.BOMB, 12), trickOwner = 1),
        )) {
            assertEquals(obs.hand, assertIs<Action.Play>(SuperBot(evaluator, mustInfer).act(obs, ctx)).cards)
        }
        val obs = observation("345").copy(trick = Combo(ComboType.ROCKET, 13), trickOwner = 1)
        assertEquals(Action.Pass(obs.seat), SuperBot(evaluator, mustInfer).act(obs, ctx))
    }

    @Test
    fun `unavailable native runtime and invalid scores fall back to hard`() = runBlocking {
        val state = Benchmarks.forcedLandlordStart(72)
        val obs = GameEngine.observe(state, state.turn)
        val expected = HardBot().act(obs, ctx)
        val evaluators = listOf(
            DouZeroEvaluator { _, _ -> throw IllegalStateException("model missing") },
            DouZeroEvaluator { _, _ -> throw UnsatisfiedLinkError("native runtime missing") },
            DouZeroEvaluator { _, _ -> FloatArray(0) },
            DouZeroEvaluator { _, actions -> FloatArray(actions.size) { Float.NaN } },
            DouZeroEvaluator { _, actions -> FloatArray(actions.size) { Float.POSITIVE_INFINITY } },
        )
        for (evaluator in evaluators) assertEquals(expected, SuperBot(evaluator).act(obs, ctx))
    }

    @Test
    fun `inference cancellation propagates without fallback`(): Unit = runBlocking {
        val evaluator = DouZeroEvaluator { _, _ -> throw CancellationException("game interrupted") }
        assertFailsWith<CancellationException> { SuperBot(evaluator, mustInfer).act(observation("345"), ctx) }
    }

    @Test
    fun `bidding and doubling reuse hard policy without model inference`() = runBlocking {
        val evaluator = DouZeroEvaluator { _, _ -> error("models only support playing") }
        var state = GameEngine.newGame(Simulator.DEFAULT_CONFIG, 6).state
        val superBot = SuperBot(evaluator, mustInfer)
        val hard = HardBot()
        var obs = GameEngine.observe(state, state.turn)
        assertEquals(hard.act(obs, ctx), superBot.act(obs, ctx))
        state = GameEngine.apply(state, Action.Bid(state.turn, true)).state
        while (state.phase == Phase.BIDDING) state = GameEngine.apply(state, Action.Bid(state.turn, false)).state
        for (seat in 0 until Seats.COUNT) {
            obs = GameEngine.observe(state, seat)
            assertEquals(hard.act(obs, ctx), superBot.act(obs, ctx))
        }
    }

    @Test
    fun `different hidden allocations produce identical features and decisions`() = runBlocking {
        val state = Benchmarks.forcedLandlordStart(31)
        val me = state.turn
        val a = Seats.next(me)
        val b = Seats.prev(me)
        val cardA = (state.hands[a] - state.bottom).cards().first()
        val cardB = (state.hands[b] - state.bottom).cards().first()
        val hands = state.hands.toMutableList()
        hands[a] = state.hands[a] - cardA + cardB
        hands[b] = state.hands[b] - cardB + cardA
        val swapped = state.copy(hands = hands)
        val obs = GameEngine.observe(state, me)
        val other = GameEngine.observe(swapped, me)
        assertEquals(obs, other)
        val inputs = ArrayList<DouZeroInput>()
        val bot = SuperBot(DouZeroEvaluator { features, actions ->
            inputs.add(features)
            greedy.scores(features, actions)
        }, mustInfer)
        assertEquals(bot.act(obs, ctx), bot.act(other, ctx))
        assertEquals(2, inputs.size)
        assertContentEquals(inputs[0].x, inputs[1].x)
        assertContentEquals(inputs[0].z, inputs[1].z)
    }

    @Test
    fun `offline bot makes legal complete games and resumes from serialized history`() = runBlocking {
        for (seed in 0 until 6L) {
            val start = Benchmarks.forcedLandlordStart(seed)
            val bots = List(Seats.COUNT) { SuperBot(greedy, mustInfer) }
            var state = start
            repeat(12) {
                if (state.phase == Phase.PLAYING) {
                    val obs = GameEngine.observe(state, state.turn)
                    state = GameEngine.apply(state, bots[state.turn].act(obs, ctx)).state
                }
            }
            val restored = Json.decodeFromString(GameState.serializer(), Json.encodeToString(GameState.serializer(), state))
            if (state.phase == Phase.PLAYING) {
                val obs = GameEngine.observe(state, state.turn)
                val restoredObs = GameEngine.observe(restored, restored.turn)
                assertEquals(bots[state.turn].act(obs, ctx), SuperBot(greedy, mustInfer).act(restoredObs, ctx))
            }
            val finished = Simulator.play(bots, seed, start = restored)
            assertEquals(Phase.FINISHED, finished.phase)
            assertTrue(finished.result != null)
        }
    }

    private fun observation(spec: String): Observation {
        val counts = Counts.parse(spec)
        val state = Benchmarks.forcedLandlordStart(1)
        val seat = state.landlord
        val sizes = state.hands.map { it.size }.toMutableList().also { it[seat] = counts.total }
        return GameEngine.observe(state, seat).copy(hand = CardSet.fromCounts(counts), handSizes = sizes)
    }
}
