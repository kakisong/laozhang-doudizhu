package com.kaynzhang.doudizhu.engine.ai

import com.kaynzhang.doudizhu.engine.game.Bidding
import com.kaynzhang.doudizhu.engine.game.Observation
import com.kaynzhang.doudizhu.engine.game.Phase
import com.kaynzhang.doudizhu.engine.game.PlayRecord
import com.kaynzhang.doudizhu.engine.model.CardSet
import com.kaynzhang.doudizhu.engine.model.Counts
import com.kaynzhang.doudizhu.engine.rules.Combo
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.float
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Checked-in oracle produced by official DouZero encoders and the original PyTorch weights. */
class DouZeroParityTest {
    @Test
    fun `public observation features and native model scores match PyTorch for every role`() = runBlocking {
        val source = requireNotNull(javaClass.getResourceAsStream("/douzero/parity.json"))
            .bufferedReader().use { it.readText() }
        val cases = Json.parseToJsonElement(source).jsonObject.getValue("cases").jsonArray
        assertTrue(cases.size >= 3)
        for (entry in cases) {
            val case = entry.jsonObject
            val label = case.getValue("name").jsonPrimitive.content
            val obs = observation(case)
            val features = DouZeroFeatures.encode(obs)
            assertEquals(case.getValue("position").jsonPrimitive.content, features.position.resourceName, label)
            assertContentEquals(case.getValue("x").floats(), features.x, "$label state encoding")
            assertContentEquals(case.getValue("z").floats(), features.z, "$label history encoding")
            val actions = case.getValue("actions").jsonArray.map { Counts.fromArray(it.jsonArray.ints()) }
            val expected = case.getValue("scores").floats()
            val actual = OnnxDouZeroEvaluator.scores(features, actions)
            assertEquals(expected.size, actual.size, label)
            for (i in expected.indices) {
                assertTrue(abs(expected[i] - actual[i]) <= 1e-4f, "$label action $i: ${expected[i]} != ${actual[i]}")
            }
            assertEquals(expected.indices.maxBy { expected[it] }, actual.indices.maxBy { actual[it] }, "$label best action")
        }
    }

    @Test
    fun `scoring more than one native batch preserves action order`() = runBlocking {
        val source = requireNotNull(javaClass.getResourceAsStream("/douzero/parity.json"))
            .bufferedReader().use { it.readText() }
        val case = Json.parseToJsonElement(source).jsonObject.getValue("cases").jsonArray.first().jsonObject
        val features = DouZeroFeatures.encode(observation(case))
        val actions = case.getValue("actions").jsonArray.map { Counts.fromArray(it.jsonArray.ints()) }
        val expected = OnnxDouZeroEvaluator.scores(features, actions)
        val repeated = List(300) { actions[it % actions.size] }
        val actual = OnnxDouZeroEvaluator.scores(features, repeated)
        for (i in actual.indices) assertTrue(abs(expected[i % expected.size] - actual[i]) <= 1e-4f, "batch index $i")
    }

    private fun observation(case: JsonObject): Observation = Observation(
        seat = case.getValue("seat").jsonPrimitive.int,
        phase = Phase.PLAYING,
        hand = CardSet(case.getValue("handBits").jsonPrimitive.long),
        landlord = case.getValue("landlord").jsonPrimitive.int,
        bottom = CardSet(case.getValue("bottomBits").jsonPrimitive.long),
        handSizes = case.getValue("handSizes").jsonArray.ints().toList(),
        playedBy = case.getValue("playedByBits").jsonArray.map { CardSet(it.jsonPrimitive.long) },
        trickOwner = case.getValue("trickOwner").jsonPrimitive.int,
        trick = case.getValue("trickKey").combo(),
        log = case.getValue("log").jsonArray.map {
            val rec = it.jsonObject
            PlayRecord(rec.getValue("seat").jsonPrimitive.int, CardSet(rec.getValue("cardsBits").jsonPrimitive.long), rec.getValue("comboKey").combo())
        },
        bidding = Bidding(0),
        jiabei = listOf(false, false, false),
        bombs = case.getValue("bombs").jsonPrimitive.int,
        baseScore = 100,
        seq = 0,
    )

    private fun JsonElement.combo(): Combo? = if (this == JsonNull) null else Combo.fromKey(jsonPrimitive.int)
    private fun JsonElement.floats(): FloatArray = jsonArray.map { it.jsonPrimitive.float }.toFloatArray()
    private fun JsonArray.ints(): IntArray = map { it.jsonPrimitive.int }.toIntArray()
}
