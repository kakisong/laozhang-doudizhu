package com.kaynzhang.doudizhu.engine.game

import com.kaynzhang.doudizhu.engine.model.CardSet
import com.kaynzhang.doudizhu.engine.model.Counts
import com.kaynzhang.doudizhu.engine.rules.ComboClassifier
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GameEngineTest {

    private val config = GameConfig(baseScore = 100, seatCoins = listOf(1_000_000, 1_000_000, 1_000_000))

    /** A fresh game whose first bidder is [first], with a fixed deal. */
    private fun start(first: Int): GameState {
        val deck = CardSet.FULL_DECK.cards()
        val hands = List(3) { i -> CardSet.of(deck.subList(i * 17, i * 17 + 17)) }
        return GameEngine.newGameWithHands(config, seed = 1, hands = hands, bottom = CardSet.of(deck.subList(51, 54)), firstBidder = first).state
    }

    private fun GameState.bid(seat: Int, take: Boolean) = GameEngine.apply(this, Action.Bid(seat, take)).state

    // Golden vectors from docs/RULES.md; A = seat 0 acts first, then B = 1, C = 2.
    @Test
    fun `A calls, nobody robs`() {
        val s = start(0).bid(0, true).bid(1, false).bid(2, false)
        assertEquals(0, s.landlord)
        assertEquals(0, s.robs)
        assertEquals(Phase.DOUBLING, s.phase)
        assertEquals(20, s.hands[0].size)
    }

    @Test
    fun `A calls, B robs, A robs back`() {
        val s = start(0).bid(0, true).bid(1, true).bid(2, false).bid(0, true)
        assertEquals(0, s.landlord)
        assertEquals(2, s.robs)
    }

    @Test
    fun `A calls, B and C rob, A declines`() {
        val s = start(0).bid(0, true).bid(1, true).bid(2, true).bid(0, false)
        assertEquals(2, s.landlord)
        assertEquals(2, s.robs)
    }

    @Test
    fun `everybody robs`() {
        val s = start(0).bid(0, true).bid(1, true).bid(2, true).bid(0, true)
        assertEquals(0, s.landlord)
        assertEquals(3, s.robs)
    }

    @Test
    fun `a seat that declined to call is never asked to rob`() {
        var s = start(0).bid(0, false).bid(1, true)
        assertEquals(2, s.turn)
        s = s.bid(2, true)
        assertEquals(BidStage.FINAL, s.bidding.stage)
        assertEquals(1, s.turn)
        s = s.bid(1, false)
        assertEquals(2, s.landlord)
        assertEquals(1, s.robs)
    }

    @Test
    fun `the last seat calling becomes landlord at once`() {
        val s = start(0).bid(0, false).bid(1, false).bid(2, true)
        assertEquals(2, s.landlord)
        assertEquals(0, s.robs)
    }

    @Test
    fun `first bidder in the middle of the order`() {
        // B starts: B calls, C robs, A robs, B declines -> A.
        val s = start(1).bid(1, true).bid(2, true).bid(0, true).bid(1, false)
        assertEquals(0, s.landlord)
        assertEquals(2, s.robs)
    }

    @Test
    fun `three passes redeal with a new deal number`() {
        val t0 = GameEngine.newGame(config, seed = 42)
        var s = t0.state
        val first = s.turn
        s = s.bid(first, false).bid(Seats.next(first), false)
        val t = GameEngine.apply(s, Action.Bid(Seats.next(Seats.next(first)), false))
        assertEquals(1, t.state.dealNo)
        assertEquals(Phase.BIDDING, t.state.phase)
        assertTrue(t.events.any { it is GameEvent.Dealt && it.isRedeal })
        assertTrue(t.state.hands[0] != t0.state.hands[0])
    }

    @Test
    fun `bidding out of turn is rejected`() {
        assertFailsWith<IllegalActionException> { start(0).bid(1, true) }
    }

    @Test
    fun `jiabei choices stay hidden until all three have chosen`() {
        var s = start(0).bid(0, true).bid(1, false).bid(2, false)
        s = GameEngine.apply(s, Action.Jiabei(2, true)).state
        assertNull(GameEngine.observe(s, 1).jiabei[2])
        assertEquals(true, GameEngine.observe(s, 2).jiabei[2])
        s = GameEngine.apply(s, Action.Jiabei(0, false)).state
        assertEquals(Phase.DOUBLING, s.phase)
        val t = GameEngine.apply(s, Action.Jiabei(1, false))
        assertEquals(Phase.PLAYING, t.state.phase)
        assertEquals(0, t.state.turn)
        assertEquals(true, GameEngine.observe(t.state, 1).jiabei[2])
        assertTrue(t.events.any { it is GameEvent.JiabeiRevealed })
    }

    @Test
    fun `a full scripted game ends in spring for the landlord`() {
        // Seat 0 holds everything it needs to go out without the farmers ever playing.
        var s = scripted(hand0 = "333444555666 小王大王 7 99", bottom = "888")
        s = s.bid(0, true).bid(1, false).bid(2, false)
        for (seat in 0..2) s = GameEngine.apply(s, Action.Jiabei(seat, false)).state
        for (spec in listOf("333444555666", "小王大王", "888+7")) {
            s = play(s, 0, spec)
            s = GameEngine.apply(s, Action.Pass(1)).state
            s = GameEngine.apply(s, Action.Pass(2)).state
            assertNull(s.trick)
        }
        s = play(s, 0, "99")
        assertEquals(Phase.FINISHED, s.phase)
        val r = s.result!!
        assertTrue(r.landlordWon && r.spring && !r.antiSpring)
        assertEquals(1, r.bombs)
        assertEquals(4, r.commonMultiplier) // rocket ×2, spring ×2
        assertEquals(listOf(800L, -400L, -400L), r.delta)
    }

    @Test
    fun `anti spring when farmers win after the landlord's single lead`() {
        var s = scripted(hand0 = "3456789 10 J Q K A 222 小王大王", bottom = "777")
        s = s.bid(0, false).bid(1, true).bid(2, false)
        assertEquals(1, s.landlord)
        for (seat in 0..2) s = GameEngine.apply(s, Action.Jiabei(seat, seat == 0)).state
        // The landlord (seat 1) leads its lowest single; seat 2 passes and seat 0 takes over for good.
        val low = CardSet.of(listOf(s.hands[1].cards().first()))
        s = GameEngine.apply(s, Action.Play(1, low, ComboClassifier.declareLead(low.counts())!!)).state
        s = GameEngine.apply(s, Action.Pass(2)).state
        for (spec in listOf("小王大王", "222")) {
            s = play(s, 0, spec)
            s = GameEngine.apply(s, Action.Pass(1)).state
            s = GameEngine.apply(s, Action.Pass(2)).state
        }
        s = play(s, 0, "3456789 10 J Q K A")
        assertEquals(Phase.FINISHED, s.phase)
        val r = s.result!!
        assertTrue(!r.landlordWon && r.antiSpring && !r.spring)
        // rocket ×2, anti-spring ×2; farmer 0 doubled.
        assertEquals(4, r.commonMultiplier)
        assertEquals(listOf(800L, -1200L, 400L), r.delta)
    }

    @Test
    fun `cannot pass while leading, cannot play cards not held, declaration must match`() {
        var s = start(0).bid(0, true).bid(1, false).bid(2, false)
        for (seat in 0..2) s = GameEngine.apply(s, Action.Jiabei(seat, false)).state
        assertFailsWith<IllegalActionException> { GameEngine.apply(s, Action.Pass(0)) }
        val notHeld = s.hands[1].cards().first()
        assertFailsWith<IllegalActionException> {
            GameEngine.apply(s, Action.Play(0, CardSet.of(listOf(notHeld)), ComboClassifier.declareLead(CardSet.of(listOf(notHeld)).counts())!!))
        }
        val two = CardSet.of(s.hands[0].cards().take(2))
        val pairCombo = ComboClassifier.declareLead(Counts.parse("33"))!!
        if (ComboClassifier.interpretations(two.counts()).isEmpty()) {
            assertFailsWith<IllegalActionException> { GameEngine.apply(s, Action.Play(0, two, pairCombo)) }
        }
    }

    @Test
    fun `state survives a JSON round trip`() {
        var s = start(2).bid(2, true).bid(0, true).bid(1, false).bid(2, false)
        val json = Json.encodeToString(GameState.serializer(), s)
        assertEquals(s, Json.decodeFromString(GameState.serializer(), json))
    }

    // Builds a game where seat 0 holds [hand0] and the bottom is [bottom]; the rest is split between 1 and 2.
    private fun scripted(hand0: String, bottom: String): GameState {
        val h0 = CardSet.fromCounts(Counts.parse(hand0))
        val rest = CardSet.FULL_DECK - h0
        val b = rest.pick(Counts.parse(bottom))
        val others = (rest - b).cards()
        val hands = listOf(h0, CardSet.of(others.take(17)), CardSet.of(others.drop(17)))
        return GameEngine.newGameWithHands(config, seed = 3, hands = hands, bottom = b, firstBidder = 0).state
    }

    private fun play(s: GameState, seat: Int, spec: String): GameState {
        val cards = s.hands[seat].pick(Counts.parse(spec))
        val combo = if (s.trick == null) ComboClassifier.declareLead(cards.counts())!! else ComboClassifier.declareFollow(cards.counts(), s.trick!!)!!
        return GameEngine.apply(s, Action.Play(seat, cards, combo)).state
    }
}
