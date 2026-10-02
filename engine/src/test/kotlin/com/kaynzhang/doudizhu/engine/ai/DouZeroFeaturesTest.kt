package com.kaynzhang.doudizhu.engine.ai

import com.kaynzhang.doudizhu.engine.game.Action
import com.kaynzhang.doudizhu.engine.game.GameEngine
import com.kaynzhang.doudizhu.engine.game.GameState
import com.kaynzhang.doudizhu.engine.game.PlayRecord
import com.kaynzhang.doudizhu.engine.game.Seats
import com.kaynzhang.doudizhu.engine.model.CardSet
import com.kaynzhang.doudizhu.engine.model.Counts
import com.kaynzhang.doudizhu.engine.model.Rk
import com.kaynzhang.doudizhu.engine.rules.Combo
import com.kaynzhang.doudizhu.engine.rules.ComboType
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DouZeroFeaturesTest {
    @Test
    fun `cards use rank major thermometer bits with jokers at the end`() {
        val cards = DouZeroFeatures.cards(Counts.parse("3333445522小王大王"))
        val expected = FloatArray(54)
        for (index in listOf(0, 1, 2, 3, 4, 5, 8, 9, 48, 49, 52, 53)) expected[index] = 1f
        assertContentEquals(expected, cards)
        assertContentEquals(FloatArray(54), DouZeroFeatures.cards(Counts.EMPTY))
        val buffer = FloatArray(60) { 7f }
        DouZeroFeatures.writeCards(Counts.of(Rk.BJ), buffer, 3)
        assertContentEquals(FloatArray(54).also { it[53] = 1f }, buffer.copyOfRange(3, 57))
        assertEquals(7f, buffer[2])
        assertEquals(7f, buffer[57])
    }

    @Test
    fun `role and hand size slots follow the landlord for every seat rotation`() {
        for (landlord in 0 until Seats.COUNT) {
            val start = Benchmarks.forcedLandlordStart(landlord.toLong())
            val state = start.copy(landlord = landlord)
            for (seat in 0 until Seats.COUNT) {
                // Make each role's count distinct while retaining a real public hand and deck.
                val obs = GameEngine.observe(state, seat).copy(handSizes = List(3) {
                    when (it) { landlord -> 20; Seats.prev(landlord) -> 13; else -> 11 }
                })
                val input = DouZeroFeatures.encode(obs)
                val position = when (seat) {
                    landlord -> DouZeroPosition.LANDLORD
                    Seats.prev(landlord) -> DouZeroPosition.LANDLORD_UP
                    else -> DouZeroPosition.LANDLORD_DOWN
                }
                assertEquals(position, input.position)
                assertEquals(position.noActionSize, input.x.size)
                assertEquals(810, input.z.size)
                assertCards(obs.hand.counts(), input.x, 0)
                assertCards(obs.unseen.counts(), input.x, 54)
                if (seat == landlord) {
                    assertOneHot(input.x, 270, 17, 12)
                    assertOneHot(input.x, 287, 17, 10)
                    assertOneHot(input.x, 304, 15, 0)
                } else {
                    assertOneHot(input.x, 378, 20, 19)
                    assertOneHot(input.x, 398, 17, if (seat == Seats.prev(landlord)) 10 else 12)
                    assertOneHot(input.x, 415, 15, 0)
                }
                assertTrue(input.z.all { it == 0f })
            }
        }
    }

    @Test
    fun `last move survives one pass and clears after two passes`() {
        var state = Benchmarks.forcedLandlordStart(19)
        val landlord = state.landlord
        val card = state.hands[landlord].cards().first()
        val counts = Counts.of(card.rank)
        state = GameEngine.apply(state, Action.Play(landlord, CardSet.of(listOf(card)), Combo(ComboType.SINGLE, card.rank))).state
        state = GameEngine.apply(state, Action.Pass(state.turn)).state
        val following = DouZeroFeatures.encode(GameEngine.observe(state, state.turn))
        assertCards(counts, following.x, 216)
        assertCards(counts, following.x, 270)
        state = GameEngine.apply(state, Action.Pass(state.turn)).state
        val leading = DouZeroFeatures.encode(GameEngine.observe(state, state.turn))
        assertTrue(GameEngine.observe(state, state.turn).isLeading)
        assertCards(Counts.EMPTY, leading.x, 108)
    }

    @Test
    fun `farmer last action slots retain each seats latest pass`() {
        val state = Benchmarks.forcedLandlordStart(2)
        val landlord = state.landlord
        val up = Seats.prev(landlord)
        val down = Seats.next(landlord)
        val landlordPlay = record(landlord, "3")
        val teammatePlay = record(up, "4")
        val log = listOf(landlordPlay, record(down, ""), teammatePlay, record(landlord, ""))
        val obs = GameEngine.observe(state, down).copy(log = log, bombs = 2)
        val features = DouZeroFeatures.encode(obs)
        assertCards(teammatePlay.cards.counts(), features.x, 216)
        assertCards(Counts.EMPTY, features.x, 270)
        assertCards(teammatePlay.cards.counts(), features.x, 324)
        assertOneHot(features.x, 415, 15, 2)
    }

    @Test
    fun `history is left padded and includes passes in chronological order`() {
        var state = Benchmarks.forcedLandlordStart(5)
        state = playSingle(state)
        state = GameEngine.apply(state, Action.Pass(state.turn)).state
        val features = DouZeroFeatures.encode(GameEngine.observe(state, state.turn))
        assertTrue(features.z.take(13 * 54).all { it == 0f })
        assertCards(state.log[0].cards.counts(), features.z, 13 * 54)
        assertCards(Counts.EMPTY, features.z, 14 * 54)
    }

    @Test
    fun `history truncates older actions without removing pass slots`() {
        var state = Benchmarks.forcedLandlordStart(5)
        repeat(6) {
            state = playSingle(state)
            state = GameEngine.apply(state, Action.Pass(state.turn)).state
            state = GameEngine.apply(state, Action.Pass(state.turn)).state
        }
        assertEquals(18, state.log.size)
        val features = DouZeroFeatures.encode(GameEngine.observe(state, state.turn))
        state.log.drop(3).forEachIndexed { index, record -> assertCards(record.cards.counts(), features.z, index * 54) }
    }

    private fun playSingle(state: GameState): GameState {
        val card = state.hands[state.turn].cards().first()
        return GameEngine.apply(state, Action.Play(state.turn, CardSet.of(listOf(card)), Combo(ComboType.SINGLE, card.rank))).state
    }

    private fun record(seat: Int, spec: String): PlayRecord {
        val counts = Counts.parse(spec)
        return PlayRecord(seat, CardSet.fromCounts(counts), if (counts.isEmpty) null else Combo(ComboType.SINGLE, counts.lowestRank()))
    }

    private fun assertCards(counts: Counts, features: FloatArray, offset: Int) =
        assertContentEquals(DouZeroFeatures.cards(counts), features.copyOfRange(offset, offset + 54))

    private fun assertOneHot(features: FloatArray, offset: Int, length: Int, index: Int) =
        assertContentEquals(FloatArray(length).also { it[index] = 1f }, features.copyOfRange(offset, offset + length))
}
