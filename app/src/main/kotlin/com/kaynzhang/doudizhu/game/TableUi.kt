package com.kaynzhang.doudizhu.game

import com.kaynzhang.doudizhu.data.BuiltInPortraits
import com.kaynzhang.doudizhu.data.Persona
import com.kaynzhang.doudizhu.data.Room
import com.kaynzhang.doudizhu.data.Settings
import com.kaynzhang.doudizhu.data.portraitId
import com.kaynzhang.doudizhu.engine.game.BidStage
import com.kaynzhang.doudizhu.engine.game.GameResult
import com.kaynzhang.doudizhu.engine.game.GameState
import com.kaynzhang.doudizhu.engine.game.Phase
import com.kaynzhang.doudizhu.engine.game.Seats
import com.kaynzhang.doudizhu.engine.model.Card
import com.kaynzhang.doudizhu.engine.model.CardSet
import com.kaynzhang.doudizhu.engine.model.Rk
import com.kaynzhang.doudizhu.engine.rules.Combo
import com.kaynzhang.doudizhu.engine.rules.ComboClassifier
import com.kaynzhang.doudizhu.engine.rules.MoveGenerator

const val HUMAN = Seats.HUMAN

/** Which buttons the human gets right now. */
enum class Controls { NONE, CALL, ROB, JIABEI, LEAD, FOLLOW, CANNOT_BEAT }

data class SeatUi(
    val seat: Int,
    val name: String,
    val avatar: String,
    val coins: Long,
    val cards: Int,
    val isLandlord: Boolean,
    /** Speech-bubble text such as "抢地主" or "不出". */
    val status: String?,
    val thinking: Boolean,
    val doubled: Boolean,
)

/** Everything the table screen draws, derived from the engine state from the human's point of view. */
data class TableUi(
    /** Changes with every deal; sprites are keyed on it so each deal starts fresh. */
    val dealKey: Long,
    val seq: Int,
    val room: Room,
    val phase: Phase,
    val turn: Int,
    val seats: List<SeatUi>,
    /** Human hand in display order (big cards on the left). */
    val hand: List<Card>,
    val selected: Set<Card>,
    /** Cards currently shown in front of each seat. */
    val displays: List<List<Card>>,
    val displayCombos: List<Combo?>,
    /** Played cards no longer on display (fading out). */
    val gone: List<Card>,
    /** Seat whose play the next player has to beat, or -1 when the trick is open. */
    val trickOwner: Int,
    val bottom: List<Card>,
    val bottomRevealed: Boolean,
    val baseScore: Long,
    val multiplier: Long,
    val controls: Controls,
    val canPlay: Boolean,
    /** Unseen cards per rank (记牌器), or null when switched off. */
    val counter: List<Int>?,
    val trustee: Boolean,
    val dealing: Boolean,
    val result: GameResult?,
    val showResult: Boolean,
    val paused: Boolean = false,
)

data class Session(val room: Room, val personas: List<Persona>, val state: GameState)

val DISPLAY_ORDER: Comparator<Card> = compareByDescending<Card> { it.rank }.thenBy { it.suit }

fun buildTableUi(
    session: Session,
    selected: Set<Card>,
    trustee: Boolean,
    dealing: Boolean,
    showResult: Boolean,
    humanCoins: Long,
    settings: Settings,
    paused: Boolean = false,
    humanAvatarId: String = BuiltInPortraits.DEFAULT_ID,
): TableUi {
    val s = session.state
    val finished = s.phase == Phase.FINISHED
    val allDecided = s.jiabei.all { it != null }

    val displays = MutableList(Seats.COUNT) { emptyList<Card>() }
    val combos = MutableList<Combo?>(Seats.COUNT) { null }
    val statuses = MutableList<String?>(Seats.COUNT) { null }
    for (seat in 0 until Seats.COUNT) {
        val last = s.lastRecordOf(seat)
        when {
            finished && seat != HUMAN && s.result?.winner != seat -> displays[seat] = s.hands[seat].cards().sortedWith(DISPLAY_ORDER)
            s.phase == Phase.PLAYING && s.turn == seat -> {}
            last == null -> {}
            last.isPass -> if (!finished) statuses[seat] = "不出"
            else -> {
                displays[seat] = playOrder(last.cards)
                combos[seat] = last.combo
            }
        }
        when (s.phase) {
            Phase.BIDDING -> statuses[seat] = s.bidding.history.lastOrNull { it.seat == seat }?.kind?.zh
            Phase.DOUBLING -> statuses[seat] = when {
                s.jiabei[seat] == null -> null
                seat == HUMAN || allDecided -> if (s.jiabei[seat] == true) "加倍" else "不加倍"
                else -> "已选择"
            }
            else -> {}
        }
    }
    val shown = displays.flatten().toSet()
    val gone = s.log.flatMap { it.cards.cards() }.filter { it !in shown }

    val hand = s.hands[HUMAN].cards().sortedWith(DISPLAY_ORDER)
    val controls = when {
        dealing || trustee || paused -> Controls.NONE
        s.phase == Phase.BIDDING && s.turn == HUMAN -> if (s.bidding.stage == BidStage.CALL) Controls.CALL else Controls.ROB
        s.phase == Phase.DOUBLING && s.jiabei[HUMAN] == null -> Controls.JIABEI
        s.phase == Phase.PLAYING && s.turn == HUMAN -> when {
            s.trick == null -> Controls.LEAD
            MoveGenerator.canBeat(s.hands[HUMAN].counts(), s.trick!!) -> Controls.FOLLOW
            else -> Controls.CANNOT_BEAT
        }
        else -> Controls.NONE
    }
    // While the human is choosing, their old bubble (e.g. "叫地主" before the final re-rob) would sit on the buttons.
    if (controls != Controls.NONE) statuses[HUMAN] = null

    val seats = (0 until Seats.COUNT).map { seat ->
        val persona = session.personas[seat]
        SeatUi(
            seat = seat,
            name = persona.name,
            avatar = if (seat == HUMAN) BuiltInPortraits.resolve(humanAvatarId).id else persona.portraitId,
            coins = if (seat == HUMAN) humanCoins else persona.coins,
            cards = s.hands[seat].size,
            isLandlord = s.landlord == seat,
            status = statuses[seat],
            thinking = !finished && !dealing && !paused && when (s.phase) {
                Phase.BIDDING, Phase.PLAYING -> s.turn == seat
                Phase.DOUBLING -> s.jiabei[seat] == null
                Phase.FINISHED -> false
            },
            doubled = allDecided && s.jiabei[seat] == true,
        )
    }

    val canPlay = (controls == Controls.LEAD || controls == Controls.FOLLOW) && selected.isNotEmpty() && run {
        val counts = CardSet.of(selected).counts()
        val trick = s.trick
        if (trick == null) ComboClassifier.declareLead(counts) != null else ComboClassifier.declareFollow(counts, trick) != null
    }

    val counter = if (settings.cardCounter) {
        var unseen = CardSet.FULL_DECK - s.hands[HUMAN]
        for (rec in s.log) unseen -= rec.cards
        val c = unseen.counts()
        List(Rk.COUNT) { c[it] }
    } else {
        null
    }

    return TableUi(
        dealKey = s.seed * 31 + s.dealNo,
        seq = s.seq,
        room = session.room,
        phase = s.phase,
        turn = s.turn,
        seats = seats,
        hand = hand,
        selected = selected.filterTo(HashSet()) { it in s.hands[HUMAN] },
        displays = displays,
        displayCombos = combos,
        gone = gone,
        trickOwner = if (s.trick != null) s.trickOwner else -1,
        bottom = s.bottom.cards().sortedWith(DISPLAY_ORDER),
        bottomRevealed = s.landlord >= 0,
        baseScore = s.config.baseScore,
        multiplier = s.result?.multiplierFor(HUMAN) ?: currentMultiplier(s),
        controls = controls,
        canPlay = canPlay,
        counter = counter,
        trustee = trustee,
        dealing = dealing,
        result = s.result,
        showResult = showResult && finished,
        paused = paused,
    )
}

/** Main part first (by how many of a rank were played), each group ascending: 333+7, 34567. */
private fun playOrder(cards: CardSet): List<Card> {
    val counts = cards.counts()
    return cards.cards().sortedWith(compareByDescending<Card> { counts[it.rank] }.thenBy { it.rank }.thenBy { it.suit })
}

/** The human's multiplier so far: 2^(robs+bombs), times the 加倍 factors once they are public. */
private fun currentMultiplier(s: GameState): Long {
    val common = 1L shl (s.robs + s.bombs).coerceAtMost(40)
    if (s.landlord < 0) return common
    val revealed = s.jiabei.all { it != null }
    fun factor(seat: Int) = if ((revealed || seat == HUMAN) && s.jiabei[seat] == true) 2L else 1L
    val landlordFactor = factor(s.landlord)
    return if (s.landlord == HUMAN) {
        common * landlordFactor * (0 until Seats.COUNT).filter { it != HUMAN }.sumOf { factor(it) }
    } else {
        common * landlordFactor * factor(HUMAN)
    }
}
