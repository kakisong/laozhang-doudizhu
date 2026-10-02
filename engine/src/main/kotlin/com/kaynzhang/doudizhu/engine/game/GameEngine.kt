package com.kaynzhang.doudizhu.engine.game

import com.kaynzhang.doudizhu.engine.model.CardSet
import com.kaynzhang.doudizhu.engine.model.Rng
import com.kaynzhang.doudizhu.engine.rules.ComboClassifier

/**
 * The rules as a pure reducer: `apply(state, action)` validates the action and returns the
 * next state plus the events it produced. Illegal actions throw [IllegalActionException].
 */
object GameEngine {

    const val HAND_SIZE = 17
    const val BOTTOM_SIZE = 3

    fun newGame(config: GameConfig, seed: Long): Transition = deal(config, seed, dealNo = 0, seq = 0)

    /** Starts from fixed hands (tests, scripted debug scenarios). */
    fun newGameWithHands(
        config: GameConfig,
        seed: Long,
        hands: List<CardSet>,
        bottom: CardSet,
        firstBidder: Int,
    ): Transition {
        require(hands.size == Seats.COUNT && hands.all { it.size == HAND_SIZE } && bottom.size == BOTTOM_SIZE)
        var all = bottom.bits
        for (h in hands) {
            require(all and h.bits == 0L) { "cards dealt twice" }
            all = all or h.bits
        }
        require(all == CardSet.FULL_DECK.bits) { "hands and bottom must cover the deck" }
        val state = GameState(
            config = config, seed = seed, dealNo = 0, phase = Phase.BIDDING,
            hands = hands, bottom = bottom, bidding = Bidding(firstBidder), turn = firstBidder,
        )
        return Transition(state, listOf(GameEvent.Dealt(0, firstBidder, isRedeal = false)))
    }

    private fun deal(config: GameConfig, seed: Long, dealNo: Int, seq: Int): Transition {
        val rng = Rng(Rng.mix(seed, dealNo.toLong()))
        val deck = CardSet.FULL_DECK.cards().toMutableList()
        rng.shuffle(deck)
        val hands = List(Seats.COUNT) { i -> CardSet.of(deck.subList(i * HAND_SIZE, (i + 1) * HAND_SIZE)) }
        val bottom = CardSet.of(deck.subList(Seats.COUNT * HAND_SIZE, deck.size))
        val first = rng.nextInt(Seats.COUNT)
        val state = GameState(
            config = config, seed = seed, dealNo = dealNo, seq = seq, phase = Phase.BIDDING,
            hands = hands, bottom = bottom, bidding = Bidding(first), turn = first,
        )
        return Transition(state, listOf(GameEvent.Dealt(dealNo, first, isRedeal = dealNo > 0)))
    }

    /** Seats allowed to act now: one seat while bidding or playing, every undecided seat while doubling. */
    fun actors(s: GameState): List<Int> = when (s.phase) {
        Phase.BIDDING, Phase.PLAYING -> listOf(s.turn)
        Phase.DOUBLING -> (0 until Seats.COUNT).filter { s.jiabei[it] == null }
        Phase.FINISHED -> emptyList()
    }

    fun observe(s: GameState, seat: Int): Observation {
        val allDecided = s.jiabei.all { it != null }
        return Observation(
            seat = seat,
            phase = s.phase,
            hand = s.hands[seat],
            landlord = s.landlord,
            bottom = if (s.landlord >= 0) s.bottom else null,
            handSizes = s.hands.map { it.size },
            playedBy = List(Seats.COUNT) { s.playedBy(it) },
            trickOwner = s.trickOwner,
            trick = s.trick,
            log = s.log,
            bidding = s.bidding,
            jiabei = s.jiabei.mapIndexed { i, j -> if (i == seat || allDecided) j else null },
            bombs = s.bombs,
            baseScore = s.config.baseScore,
            seq = s.seq,
        )
    }

    fun apply(s: GameState, a: Action): Transition = when (s.phase) {
        Phase.BIDDING -> bid(s, a as? Action.Bid ?: fail("expected a bid, got $a"))
        Phase.DOUBLING -> jiabei(s, a as? Action.Jiabei ?: fail("expected 加倍, got $a"))
        Phase.PLAYING -> when (a) {
            is Action.Play -> play(s, a)
            is Action.Pass -> pass(s, a)
            else -> fail("expected a play or pass, got $a")
        }
        Phase.FINISHED -> fail("game is over")
    }

    // ---- bidding (叫地主 / 抢地主) ----

    private fun bid(s: GameState, a: Action.Bid): Transition {
        val b = s.bidding
        if (a.seat != s.turn) fail("seat ${a.seat} bid out of turn (turn ${s.turn})")
        return when (b.stage) {
            BidStage.CALL -> if (a.take) call(s, a.seat) else noCall(s, a.seat)
            BidStage.ROB -> rob(s, a)
            BidStage.FINAL -> {
                val kind = if (a.take) BidKind.ROB else BidKind.NO_ROB
                val robs = b.robs + if (a.take) 1 else 0
                val nb = b.copy(
                    robs = robs, stage = BidStage.DONE,
                    lastTaker = if (a.take) a.seat else b.lastTaker,
                    history = b.history + BidRecord(a.seat, kind),
                )
                setLandlord(s, nb, if (a.take) b.caller else b.lastTaker, GameEvent.BidMade(a.seat, kind, robs))
            }
            BidStage.DONE -> fail("bidding is over")
        }
    }

    private fun call(s: GameState, seat: Int): Transition {
        val b = s.bidding
        // Seats after the caller that have not spoken yet may rob; seats that said 不叫 may not.
        val queue = buildList {
            var t = Seats.next(seat)
            while (t != b.firstBidder) {
                add(t)
                t = Seats.next(t)
            }
        }
        val nb = b.copy(
            caller = seat, lastTaker = seat, robQueue = queue,
            stage = if (queue.isEmpty()) BidStage.DONE else BidStage.ROB,
            history = b.history + BidRecord(seat, BidKind.CALL),
        )
        val event = GameEvent.BidMade(seat, BidKind.CALL, 0)
        if (queue.isEmpty()) return setLandlord(s, nb, seat, event)
        return Transition(s.copy(seq = s.seq + 1, bidding = nb, turn = queue.first()), listOf(event))
    }

    private fun noCall(s: GameState, seat: Int): Transition {
        val b = s.bidding
        val event = GameEvent.BidMade(seat, BidKind.NO_CALL, 0)
        if (b.callPasses + 1 == Seats.COUNT) {
            val redeal = deal(s.config, s.seed, s.dealNo + 1, s.seq + 1)
            return Transition(redeal.state, listOf(event) + redeal.events)
        }
        val nb = b.copy(callPasses = b.callPasses + 1, history = b.history + BidRecord(seat, BidKind.NO_CALL))
        return Transition(s.copy(seq = s.seq + 1, bidding = nb, turn = Seats.next(seat)), listOf(event))
    }

    private fun rob(s: GameState, a: Action.Bid): Transition {
        val b = s.bidding
        val kind = if (a.take) BidKind.ROB else BidKind.NO_ROB
        val robs = b.robs + if (a.take) 1 else 0
        val rest = b.robQueue.drop(1)
        val nb = b.copy(
            robs = robs,
            lastTaker = if (a.take) a.seat else b.lastTaker,
            robQueue = rest,
            history = b.history + BidRecord(a.seat, kind),
        )
        val event = GameEvent.BidMade(a.seat, kind, robs)
        return when {
            rest.isNotEmpty() -> Transition(s.copy(seq = s.seq + 1, bidding = nb, turn = rest.first()), listOf(event))
            robs == 0 -> setLandlord(s, nb.copy(stage = BidStage.DONE), b.caller, event)
            else -> Transition(
                s.copy(seq = s.seq + 1, bidding = nb.copy(stage = BidStage.FINAL), turn = b.caller),
                listOf(event),
            )
        }
    }

    private fun setLandlord(s: GameState, bidding: Bidding, landlord: Int, event: GameEvent): Transition {
        val hands = s.hands.toMutableList()
        hands[landlord] = hands[landlord] + s.bottom
        val next = s.copy(
            seq = s.seq + 1, bidding = bidding, landlord = landlord, hands = hands,
            phase = Phase.DOUBLING, turn = -1,
        )
        return Transition(next, listOf(event, GameEvent.LandlordSet(landlord, s.bottom, bidding.robs)))
    }

    // ---- 加倍 ----

    private fun jiabei(s: GameState, a: Action.Jiabei): Transition {
        if (a.seat !in 0 until Seats.COUNT || s.jiabei[a.seat] != null) fail("seat ${a.seat} cannot choose 加倍 now")
        val choices = s.jiabei.toMutableList().also { it[a.seat] = a.yes }
        val events = mutableListOf<GameEvent>(GameEvent.JiabeiChosen(a.seat))
        val next = if (choices.all { it != null }) {
            events += GameEvent.JiabeiRevealed(choices.map { it!! })
            s.copy(seq = s.seq + 1, jiabei = choices, phase = Phase.PLAYING, turn = s.landlord)
        } else {
            s.copy(seq = s.seq + 1, jiabei = choices)
        }
        return Transition(next, events)
    }

    // ---- playing ----

    private fun play(s: GameState, a: Action.Play): Transition {
        if (a.seat != s.turn) fail("seat ${a.seat} played out of turn (turn ${s.turn})")
        val hand = s.hands[a.seat]
        if (a.cards.isEmpty || !hand.containsAll(a.cards)) fail("seat ${a.seat} does not hold ${a.cards}")
        if (a.combo !in ComboClassifier.interpretations(a.cards.counts())) {
            fail("${a.cards} cannot be declared as ${a.combo}")
        }
        val prev = s.trick
        if (prev != null && !a.combo.beats(prev)) fail("${a.combo} does not beat $prev")

        val left = hand - a.cards
        val hands = s.hands.toMutableList().also { it[a.seat] = left }
        val plays = s.playsBySeat.toMutableList().also { it[a.seat] = it[a.seat] + 1 }
        val bombs = s.bombs + if (a.combo.isBomb) 1 else 0
        val log = s.log + PlayRecord(a.seat, a.cards, a.combo)
        val events = mutableListOf<GameEvent>(GameEvent.Played(a.seat, a.cards, a.combo, left.size))
        val next = s.copy(
            seq = s.seq + 1, hands = hands, playsBySeat = plays, bombs = bombs, log = log,
            trickOwner = a.seat, trick = a.combo, turn = Seats.next(a.seat),
        )
        if (left.isEmpty) return finish(next, a.seat, events)
        if (left.size <= 2) events += GameEvent.Alert(a.seat, left.size)
        return Transition(next, events)
    }

    private fun pass(s: GameState, a: Action.Pass): Transition {
        if (a.seat != s.turn) fail("seat ${a.seat} passed out of turn (turn ${s.turn})")
        if (s.trick == null) fail("seat ${a.seat} must lead and cannot pass")
        val nextSeat = Seats.next(a.seat)
        val log = s.log + PlayRecord(a.seat, CardSet.EMPTY, null)
        val events = mutableListOf<GameEvent>(GameEvent.Passed(a.seat))
        val next = if (nextSeat == s.trickOwner) {
            events += GameEvent.TrickCleared(nextSeat)
            s.copy(seq = s.seq + 1, log = log, trick = null, trickOwner = -1, turn = nextSeat)
        } else {
            s.copy(seq = s.seq + 1, log = log, turn = nextSeat)
        }
        return Transition(next, events)
    }

    private fun finish(s: GameState, winner: Int, events: MutableList<GameEvent>): Transition {
        val landlordWon = winner == s.landlord
        val farmers = (0 until Seats.COUNT).filter { it != s.landlord }
        val spring = landlordWon && farmers.all { s.playsBySeat[it] == 0 }
        val antiSpring = !landlordWon && s.playsBySeat[s.landlord] == 1
        val result = Settlement.settle(
            config = s.config, landlord = s.landlord, winner = winner, robs = s.robs, bombs = s.bombs,
            spring = spring, antiSpring = antiSpring, jiabei = s.jiabei.map { it == true },
        )
        events += GameEvent.Finished(result)
        return Transition(s.copy(phase = Phase.FINISHED, turn = -1, result = result), events)
    }

    private fun fail(message: String): Nothing = throw IllegalActionException(message)
}
