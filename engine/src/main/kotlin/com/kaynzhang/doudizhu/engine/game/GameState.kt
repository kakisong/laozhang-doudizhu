package com.kaynzhang.doudizhu.engine.game

import com.kaynzhang.doudizhu.engine.model.CardSet
import com.kaynzhang.doudizhu.engine.rules.Combo
import kotlinx.serialization.Serializable

/** Seat indices. Seat 0 is the human at the bottom; play goes 0 → 1 (right) → 2 (left). */
object Seats {
    const val HUMAN = 0
    const val COUNT = 3

    fun next(seat: Int): Int = (seat + 1) % COUNT
    fun prev(seat: Int): Int = (seat + 2) % COUNT
}

/** Stakes of one game. [seatCoins] are the coins each seat brings; they cap settlement. */
@Serializable
data class GameConfig(
    val baseScore: Long,
    val seatCoins: List<Long>,
) {
    init {
        require(baseScore > 0)
        require(seatCoins.size == Seats.COUNT && seatCoins.all { it >= 0 })
    }
}

enum class Phase { BIDDING, DOUBLING, PLAYING, FINISHED }

enum class BidStage { CALL, ROB, FINAL, DONE }

enum class BidKind(val zh: String) {
    CALL("叫地主"),
    NO_CALL("不叫"),
    ROB("抢地主"),
    NO_ROB("不抢"),
}

@Serializable
data class BidRecord(val seat: Int, val kind: BidKind)

/** Public state of 叫地主/抢地主; see docs/RULES.md for the state machine. */
@Serializable
data class Bidding(
    val firstBidder: Int,
    val stage: BidStage = BidStage.CALL,
    val caller: Int = -1,
    val lastTaker: Int = -1,
    val robs: Int = 0,
    val callPasses: Int = 0,
    val robQueue: List<Int> = emptyList(),
    val history: List<BidRecord> = emptyList(),
)

/** One entry of the play log; [combo] is null for a pass. */
@Serializable
data class PlayRecord(val seat: Int, val cards: CardSet, val combo: Combo?) {
    val isPass: Boolean get() = combo == null
}

@Serializable
data class GameResult(
    val winner: Int,
    val landlord: Int,
    val landlordWon: Boolean,
    val spring: Boolean,
    val antiSpring: Boolean,
    val robs: Int,
    val bombs: Int,
    val jiabei: List<Boolean>,
    /** 2^(robs + bombs + spring) — shared by both farmers. */
    val commonMultiplier: Long,
    /** Uncapped amount per seat (the landlord's is the sum of both farmers'). */
    val raw: List<Long>,
    /** Actual coin change per seat after caps; always sums to zero. */
    val delta: List<Long>,
    val capped: Boolean,
) {
    /** Total multiplier seen by [seat] (for display): common × landlord 加倍 × own 加倍. */
    fun multiplierFor(seat: Int): Long {
        val landlordDouble = if (jiabei[landlord]) 2 else 1
        return if (seat == landlord) {
            commonMultiplier * landlordDouble * (0 until Seats.COUNT).filter { it != landlord }
                .sumOf { if (jiabei[it]) 2L else 1L }
        } else {
            commonMultiplier * landlordDouble * (if (jiabei[seat]) 2 else 1)
        }
    }
}

/**
 * The complete, serializable state of one game. Immutable: [GameEngine.apply] returns a new state.
 * Hidden information lives here; bots only ever see an [Observation].
 */
@Serializable
data class GameState(
    val schema: Int = SCHEMA,
    val config: GameConfig,
    val seed: Long,
    /** Incremented on every redeal. */
    val dealNo: Int,
    /** Number of actions applied so far; part of every decision seed. */
    val seq: Int = 0,
    val phase: Phase,
    val hands: List<CardSet>,
    val bottom: CardSet,
    val bidding: Bidding,
    val landlord: Int = -1,
    /** Per-seat 加倍 choice; null until that seat has decided. */
    val jiabei: List<Boolean?> = listOf(null, null, null),
    /** Seat to act during BIDDING and PLAYING; -1 during DOUBLING and after the game. */
    val turn: Int,
    /** Seat whose play must be beaten, or -1 when [turn] leads freely. */
    val trickOwner: Int = -1,
    val trick: Combo? = null,
    val log: List<PlayRecord> = emptyList(),
    /** Number of non-pass plays per seat (spring detection). */
    val playsBySeat: List<Int> = listOf(0, 0, 0),
    val bombs: Int = 0,
    val result: GameResult? = null,
) {
    val robs: Int get() = bidding.robs

    /** Cards played so far by each seat. */
    fun playedBy(seat: Int): CardSet {
        var bits = 0L
        for (rec in log) if (rec.seat == seat) bits = bits or rec.cards.bits
        return CardSet(bits)
    }

    /** The most recent action of [seat] in the current trick context (for display), or null. */
    fun lastRecordOf(seat: Int): PlayRecord? = log.lastOrNull { it.seat == seat }

    companion object {
        const val SCHEMA = 1
    }
}
