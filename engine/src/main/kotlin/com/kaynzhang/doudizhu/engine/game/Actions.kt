package com.kaynzhang.doudizhu.engine.game

import com.kaynzhang.doudizhu.engine.model.CardSet
import com.kaynzhang.doudizhu.engine.rules.Combo
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface Action {
    val seat: Int

    /** 叫地主/抢地主 when [take] is true, 不叫/不抢 otherwise; which one depends on the bidding stage. */
    @Serializable
    @SerialName("bid")
    data class Bid(override val seat: Int, val take: Boolean) : Action

    @Serializable
    @SerialName("jiabei")
    data class Jiabei(override val seat: Int, val yes: Boolean) : Action

    /** [combo] declares how [cards] are meant (matters for ambiguous planes). */
    @Serializable
    @SerialName("play")
    data class Play(override val seat: Int, val cards: CardSet, val combo: Combo) : Action

    @Serializable
    @SerialName("pass")
    data class Pass(override val seat: Int) : Action
}

/** Things that happened while applying an action; the UI turns these into sounds and speech. */
sealed interface GameEvent {
    data class Dealt(val dealNo: Int, val firstBidder: Int, val isRedeal: Boolean) : GameEvent
    data class BidMade(val seat: Int, val kind: BidKind, val robs: Int) : GameEvent
    data class LandlordSet(val seat: Int, val bottom: CardSet, val robs: Int) : GameEvent

    /** [seat] has decided about 加倍; the choice itself stays hidden until [JiabeiRevealed]. */
    data class JiabeiChosen(val seat: Int) : GameEvent
    data class JiabeiRevealed(val choices: List<Boolean>) : GameEvent
    data class Played(val seat: Int, val cards: CardSet, val combo: Combo, val cardsLeft: Int) : GameEvent
    data class Passed(val seat: Int) : GameEvent

    /** Both other seats passed; [leader] now leads freely. */
    data class TrickCleared(val leader: Int) : GameEvent

    /** 报单/报双: [seat] has 1 or 2 cards left. */
    data class Alert(val seat: Int, val cardsLeft: Int) : GameEvent
    data class Finished(val result: GameResult) : GameEvent
}

data class Transition(val state: GameState, val events: List<GameEvent>)

class IllegalActionException(message: String) : IllegalArgumentException(message)
