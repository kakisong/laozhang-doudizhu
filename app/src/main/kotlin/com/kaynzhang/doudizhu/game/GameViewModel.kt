package com.kaynzhang.doudizhu.game

import android.app.Application
import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kaynzhang.doudizhu.DdzApp
import com.kaynzhang.doudizhu.audio.Sfx
import com.kaynzhang.doudizhu.audio.VoiceAnnouncer
import com.kaynzhang.doudizhu.data.AppData
import com.kaynzhang.doudizhu.data.Persona
import com.kaynzhang.doudizhu.data.Personas
import com.kaynzhang.doudizhu.data.Profile
import com.kaynzhang.doudizhu.data.Room
import com.kaynzhang.doudizhu.data.SavedTable
import com.kaynzhang.doudizhu.data.Settings
import com.kaynzhang.doudizhu.engine.ai.Bot
import com.kaynzhang.doudizhu.engine.ai.BotContext
import com.kaynzhang.doudizhu.engine.ai.Bots
import com.kaynzhang.doudizhu.engine.ai.Difficulty
import com.kaynzhang.doudizhu.engine.ai.HandAnalyzer
import com.kaynzhang.doudizhu.engine.ai.NormalBot
import com.kaynzhang.doudizhu.engine.ai.NormalPolicy
import com.kaynzhang.doudizhu.engine.ai.SearchBudget
import com.kaynzhang.doudizhu.engine.game.Action
import com.kaynzhang.doudizhu.engine.game.GameConfig
import com.kaynzhang.doudizhu.engine.game.GameEngine
import com.kaynzhang.doudizhu.engine.game.GameEvent
import com.kaynzhang.doudizhu.engine.game.GameResult
import com.kaynzhang.doudizhu.engine.game.GameState
import com.kaynzhang.doudizhu.engine.game.IllegalActionException
import com.kaynzhang.doudizhu.engine.game.Phase
import com.kaynzhang.doudizhu.engine.model.Card
import com.kaynzhang.doudizhu.engine.model.CardSet
import com.kaynzhang.doudizhu.engine.model.Rng
import com.kaynzhang.doudizhu.engine.rules.Combo
import com.kaynzhang.doudizhu.engine.rules.ComboClassifier
import com.kaynzhang.doudizhu.engine.rules.ComboType
import com.kaynzhang.doudizhu.engine.rules.KickerMode
import com.kaynzhang.doudizhu.engine.rules.Move
import com.kaynzhang.doudizhu.engine.rules.MoveGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.SecureRandom

/** Transient table effects (banners, shakes); never replayed after restoring a save. */
sealed interface TableEffect {
    data class ComboPlayed(val seat: Int, val combo: Combo, val seq: Int) : TableEffect
    data class LandlordChosen(val seat: Int) : TableEffect
    data class GameOver(val result: GameResult, val humanWon: Boolean) : TableEffect
}

/**
 * Runs one table: owns the engine state, schedules computer turns with human-like pacing,
 * turns engine events into sound, speech and effects, and persists every step.
 */
class GameViewModel(app: Application) : AndroidViewModel(app) {

    private val container = (app as DdzApp).container
    private val store = container.store
    private val sound = container.sound
    private val voice = container.voice

    val appData: StateFlow<AppData?> = store.data.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val session = MutableStateFlow<Session?>(null)
    private val selection = MutableStateFlow<Set<Card>>(emptySet())
    private val trustee = MutableStateFlow(false)
    private val foreground = MutableStateFlow(true)
    private val dealing = MutableStateFlow(false)
    private val showResult = MutableStateFlow(false)

    private val _effects = MutableSharedFlow<TableEffect>(extraBufferCapacity = 32)
    val effects: SharedFlow<TableEffect> = _effects

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages

    val table: StateFlow<TableUi?> = combine(
        session, selection, trustee, combine(dealing, showResult, ::Pair), appData,
    ) { sess, sel, auto, (deal, show), data ->
        if (sess == null || data == null) null
        else buildTableUi(sess, sel, auto, deal, show, data.profile.coins, data.settings)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val inGame: Boolean get() = session.value != null

    /** Debug soak runs: start the next game automatically after each result. */
    internal var autoRestart = false

    /** Debug soak runs: no pacing and near-instant animations, so hundreds of games fit in minutes. */
    internal var turbo = false

    /** Debug soak runs: keep 托管 on across games (players get it reset every game). */
    internal var keepTrustee = false

    private val dealMs get() = if (turbo) 60L else DEAL_MS

    private var loop: Job? = null
    private var bots: List<Bot?> = listOf(null, null, null)
    private val assistant = NormalBot()
    private val hintAnalyzer = HandAnalyzer()
    private val hintPolicy = NormalPolicy(hintAnalyzer)
    private var hints: List<Move> = emptyList()
    private var hintSeq = -1
    private var hintIndex = 0
    private var dealUntil = 0L
    private val seeds = SecureRandom()

    // ---- table lifecycle ----

    /** Starts a table in [room]; returns false (with a message) if the player can't afford it. */
    fun enterRoom(room: Room): Boolean {
        val coins = appData.value?.profile?.coins ?: return false
        if (coins < room.minCoins) {
            _messages.tryEmit("金币不足 ${room.minCoins}，无法进入${room.title}")
            return false
        }
        startGame(room, Personas.forRoom(room))
        return true
    }

    fun resumeSaved(): Boolean {
        val saved = appData.value?.savedTable ?: return false
        begin(saved.room, saved.personas, saved.state, emptyList())
        return true
    }

    /** Next game at the same table; false if the player can no longer afford the room. */
    fun playAgain(): Boolean {
        val sess = session.value ?: return false
        val coins = appData.value?.profile?.coins ?: return false
        if (coins < sess.room.minCoins) {
            _messages.tryEmit("金币不足，请回大厅领取救济金或换个场次")
            return false
        }
        startGame(sess.room, Personas.refresh(sess.personas, sess.room))
        return true
    }

    /** Leaves the table; an unfinished game stays saved for 继续上一局. */
    fun leaveTable() {
        loop?.cancel()
        loop = null
        save()
        voice.stop()
        session.value = null
        selection.value = emptySet()
        trustee.value = false
        showResult.value = false
        dealing.value = false
    }

    fun setForeground(value: Boolean) {
        foreground.value = value
        if (value) sound.resume() else {
            sound.pause()
            voice.stop()
        }
    }

    private fun startGame(room: Room, personas: List<Persona>) {
        val coins = appData.value?.profile?.coins ?: return
        val config = GameConfig(room.baseScore, listOf(coins, personas[1].coins, personas[2].coins))
        val t = GameEngine.newGame(config, seeds.nextLong())
        begin(room, personas, t.state, t.events)
        save()
    }

    /** Debug/test entry: a table with a prepared state. */
    internal fun startScripted(room: Room, state: GameState, events: List<GameEvent>) {
        begin(room, Personas.forRoom(room), state, events)
    }

    private fun begin(room: Room, personas: List<Persona>, state: GameState, events: List<GameEvent>) {
        loop?.cancel()
        bots = listOf(null, Bots.create(room.difficulty), Bots.create(room.difficulty))
        selection.value = emptySet()
        trustee.value = keepTrustee
        showResult.value = state.phase == Phase.FINISHED
        dealing.value = false
        dealUntil = 0
        hintSeq = -1
        session.value = Session(room, personas, state)
        handleEvents(events, state)
        startLoop()
    }

    // ---- the turn loop ----

    private fun startLoop() {
        loop = viewModelScope.launch {
            combine(session, trustee, foreground, ::Triple).collectLatest { (sess, auto, fg) ->
                val s = sess?.state ?: return@collectLatest
                if (!fg || s.phase == Phase.FINISHED) return@collectLatest
                val wait = dealUntil - SystemClock.uptimeMillis()
                if (wait > 0) delay(wait)

                val actors = GameEngine.actors(s)
                val seat = actors.firstOrNull { it != HUMAN || auto }
                if (seat == null) {
                    // Only the human can act. With nothing that beats the table, pass after a moment.
                    val trick = s.trick
                    if (s.phase == Phase.PLAYING && trick != null && !MoveGenerator.canBeat(s.hands[HUMAN].counts(), trick)) {
                        delay(if (turbo) 10 else AUTO_PASS_MS)
                        dispatch(Action.Pass(HUMAN))
                    }
                    return@collectLatest
                }
                val bot = if (seat == HUMAN) assistant else bots[seat] ?: return@collectLatest
                val started = SystemClock.uptimeMillis()
                val action = withContext(Dispatchers.Default) {
                    bot.act(GameEngine.observe(s, seat), BotContext(Bots.decisionSeed(s, seat), budget(sess.room)))
                }
                val thought = SystemClock.uptimeMillis() - started
                if (thought > SLOW_DECISION_MS) Log.i(TAG, "seat $seat thought ${thought}ms (${sess.room.difficulty}, ${s.phase})")
                val remaining = pacing(s, seat) - thought
                if (remaining > 0) delay(remaining)
                dispatch(action)
            }
        }
    }

    private fun budget(room: Room): SearchBudget =
        if (room.difficulty == Difficulty.HARD) SearchBudget.Millis(HARD_THINK_MS) else SearchBudget.Samples(8)

    private fun pacing(s: GameState, seat: Int): Long {
        if (turbo) return 0
        val speed = appData.value?.settings?.speed?.factor ?: 1.0
        val base = when (s.phase) {
            Phase.BIDDING -> 800.0
            Phase.DOUBLING -> 550.0
            Phase.PLAYING -> if (s.trick == null) 950.0 else 780.0
            Phase.FINISHED -> 0.0
        }
        val jitter = 0.85 + Rng(Bots.decisionSeed(s, seat)).nextDouble() * 0.3
        val assist = if (seat == HUMAN) 0.6 else 1.0
        return (base * speed * jitter * assist).toLong()
    }

    private fun dispatch(action: Action) {
        val sess = session.value ?: return
        val t = try {
            GameEngine.apply(sess.state, action)
        } catch (e: IllegalActionException) {
            Log.w(TAG, "rejected $action", e)
            if (action.seat == HUMAN) _messages.tryEmit("这手牌不能出")
            return
        }
        if (action.seat == HUMAN || t.state.phase != sess.state.phase) selection.value = emptySet()
        val result = t.state.result
        val personas = if (result == null) sess.personas else sess.personas.mapIndexed { seat, p ->
            if (seat == HUMAN) p else p.copy(coins = (p.coins + result.delta[seat]).coerceAtLeast(0))
        }
        session.value = sess.copy(state = t.state, personas = personas)
        handleEvents(t.events, t.state)
        if (result != null) {
            container.scope.launch { store.settle(t.state, result) }
            viewModelScope.launch {
                delay(if (turbo) 150 else RESULT_DELAY_MS)
                showResult.value = true
                if (autoRestart) {
                    delay(if (turbo) 150 else AUTO_RESTART_MS)
                    if (session.value?.state?.seed == t.state.seed && !playAgain()) autoRestart = false
                }
            }
        } else {
            save()
        }
    }

    private fun save() {
        val sess = session.value ?: return
        if (sess.state.phase == Phase.FINISHED) return
        store.saveTable(SavedTable(sess.room, sess.personas, sess.state))
    }

    private fun handleEvents(events: List<GameEvent>, state: GameState) {
        for (e in events) {
            when (e) {
                is GameEvent.Dealt -> {
                    if (e.isRedeal) _messages.tryEmit("没有人叫地主，重新发牌")
                    sound.play(Sfx.DEAL)
                    dealUntil = SystemClock.uptimeMillis() + dealMs
                    dealing.value = true
                    viewModelScope.launch {
                        delay(dealMs)
                        if (SystemClock.uptimeMillis() >= dealUntil) dealing.value = false
                    }
                }
                is GameEvent.BidMade -> {
                    sound.play(Sfx.CLICK)
                    voice.say(e.kind.zh, e.seat)
                }
                is GameEvent.LandlordSet -> {
                    sound.play(Sfx.TURN)
                    _effects.tryEmit(TableEffect.LandlordChosen(e.seat))
                }
                is GameEvent.JiabeiChosen -> if (e.seat == HUMAN) sound.play(Sfx.CLICK)
                is GameEvent.Played -> {
                    sound.play(sfxFor(e.combo))
                    voice.say(VoiceAnnouncer.combo(e.combo), e.seat)
                    _effects.tryEmit(TableEffect.ComboPlayed(e.seat, e.combo, state.seq))
                }
                is GameEvent.Passed -> VoiceAnnouncer.phrase(e, state.seq)?.let { voice.say(it, e.seat) }
                is GameEvent.Alert -> {
                    sound.play(Sfx.ALERT)
                    VoiceAnnouncer.phrase(e, state.seq)?.let { voice.say(it, e.seat, interrupt = false) }
                }
                is GameEvent.Finished -> {
                    val won = humanWon(e.result)
                    sound.play(if (won) Sfx.WIN else Sfx.LOSE)
                    _effects.tryEmit(TableEffect.GameOver(e.result, won))
                }
                else -> {}
            }
        }
    }

    private fun sfxFor(c: Combo): Sfx = when (c.type) {
        ComboType.BOMB -> Sfx.BOMB
        ComboType.ROCKET -> Sfx.ROCKET
        ComboType.PLANE, ComboType.PLANE_SINGLES, ComboType.PLANE_PAIRS -> Sfx.PLANE
        else -> Sfx.PLAY
    }

    // ---- human input ----

    fun bid(take: Boolean) = humanAct { Action.Bid(HUMAN, take) }

    fun jiabei(yes: Boolean) = humanAct { Action.Jiabei(HUMAN, yes) }

    fun pass() = humanAct { Action.Pass(HUMAN) }

    fun play() {
        val s = session.value?.state ?: return
        val chosen = selection.value.filter { it in s.hands[HUMAN] }
        if (chosen.isEmpty()) return
        val cards = CardSet.of(chosen)
        val trick = s.trick
        val combo = if (trick == null) ComboClassifier.declareLead(cards.counts()) else ComboClassifier.declareFollow(cards.counts(), trick)
        if (combo == null) {
            _messages.tryEmit(if (trick == null || ComboClassifier.interpretations(cards.counts()).isEmpty()) "所选的牌不符合规则" else "所选的牌大不过上家")
            return
        }
        humanAct { Action.Play(HUMAN, cards, combo) }
    }

    private inline fun humanAct(make: () -> Action) {
        val s = session.value?.state ?: return
        if (trustee.value || dealing.value || HUMAN !in GameEngine.actors(s)) return
        dispatch(make())
    }

    fun toggle(card: Card) {
        selection.value = selection.value.let { if (card in it) it - card else it + card }
    }

    /** Sets every card in [cards] to [selected] (drag selection). */
    fun setSelected(cards: Collection<Card>, selected: Boolean) {
        selection.value = if (selected) selection.value + cards else selection.value - cards.toSet()
    }

    fun clearSelection() {
        selection.value = emptySet()
    }

    fun setTrustee(on: Boolean) {
        trustee.value = on
        if (on) selection.value = emptySet()
    }

    /** 提示: cycles through suggested plays, cheapest breakage first, bombs last. */
    fun hint() {
        val s = session.value?.state ?: return
        if (s.phase != Phase.PLAYING || s.turn != HUMAN || trustee.value) return
        if (hintSeq != s.seq) {
            hints = computeHints(s)
            hintIndex = 0
            hintSeq = s.seq
        }
        if (hints.isEmpty()) {
            _messages.tryEmit("没有能大过上家的牌")
            return
        }
        val m = hints[hintIndex % hints.size]
        hintIndex++
        selection.value = s.hands[HUMAN].pick(m.counts).cards().toSet()
    }

    private fun computeHints(s: GameState): List<Move> {
        val trick = s.trick
        if (trick == null) return hintPolicy.candidates(GameEngine.observe(s, HUMAN), 10).filterNotNull()
        val hand = s.hands[HUMAN].counts()
        val best = LinkedHashMap<Int, Pair<Move, Int>>()
        for (m in MoveGenerator.beating(hand, trick, KickerMode.ALL)) {
            val score = hintAnalyzer.score(hand - m.counts)
            val cur = best[m.combo.key]
            if (cur == null || score > cur.second) best[m.combo.key] = m to score
        }
        val (bombs, normal) = best.values.partition { it.first.combo.isBomb }
        return normal.sortedByDescending { it.second }.map { it.first } + bombs.map { it.first }
    }

    // ---- lobby actions ----

    fun claimRelief() {
        viewModelScope.launch {
            _messages.tryEmit(
                if (store.claimRelief()) "已领取救济金 ${Profile.RELIEF_AMOUNT} 金币"
                else "金币少于 ${Profile.RELIEF_THRESHOLD} 时才能领取救济金",
            )
        }
    }

    fun updateSettings(transform: (Settings) -> Settings) {
        viewModelScope.launch { store.updateSettings(transform) }
    }

    fun resetData() {
        viewModelScope.launch {
            store.resetAll()
            _messages.tryEmit("数据已重置")
        }
    }

    private fun humanWon(r: GameResult): Boolean = (r.landlord == HUMAN) == r.landlordWon

    companion object {
        private const val TAG = "GameViewModel"
        const val DEAL_MS = 1_500L
        const val AUTO_PASS_MS = 1_500L
        const val RESULT_DELAY_MS = 1_700L
        const val HARD_THINK_MS = 450L
        private const val AUTO_RESTART_MS = 1_200L
        private const val SLOW_DECISION_MS = 150L
    }
}
