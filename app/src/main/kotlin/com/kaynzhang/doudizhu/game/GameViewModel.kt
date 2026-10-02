package com.kaynzhang.doudizhu.game

import android.app.Application
import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kaynzhang.doudizhu.DdzApp
import com.kaynzhang.doudizhu.audio.Sfx
import com.kaynzhang.doudizhu.audio.GameAudioPlanner
import com.kaynzhang.doudizhu.audio.VoiceLine
import com.kaynzhang.doudizhu.data.AppData
import com.kaynzhang.doudizhu.data.BuiltInPortraits
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
    private val audio = container.audio
    /** Stable across this ViewModel's Activity recreation, distinct from a second Activity. */
    private val audioOwner = Any()

    val appData: StateFlow<AppData?> = store.data.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val session = MutableStateFlow<Session?>(null)
    private val selection = MutableStateFlow<Set<Card>>(emptySet())
    private val trustee = MutableStateFlow(false)
    private val foreground = MutableStateFlow(true)
    private val _paused = MutableStateFlow(false)
    val paused: StateFlow<Boolean> = _paused
    private val dealing = MutableStateFlow(false)
    private val showResult = MutableStateFlow(false)

    private val _effects = MutableSharedFlow<TableEffect>(extraBufferCapacity = 32)
    val effects: SharedFlow<TableEffect> = _effects

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages

    val table: StateFlow<TableUi?> = combine(
        session, selection, trustee, combine(dealing, showResult, _paused, ::Triple), appData,
    ) { sess, sel, auto, (deal, show, pause), data ->
        if (sess == null || data == null) null
        else buildTableUi(
            sess, sel, auto, deal, show, data.profile.coins, data.settings,
            paused = pause, humanAvatarId = data.profile.avatarId,
        )
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
    private var soundSequence: Job? = null
    private var lastFeedbackAt = 0L
    private var bots: List<Bot?> = listOf(null, null, null)
    private val assistant = NormalBot()
    private val hintAnalyzer = HandAnalyzer()
    private val hintPolicy = NormalPolicy(hintAnalyzer)
    private var hints: List<Move> = emptyList()
    private var hintSeq = -1
    private var hintIndex = 0
    private var dealUntil = 0L
    private var trusteeSince = 0L
    private var turnCueSeq = -1
    private val seeds = SecureRandom()

    // ---- table lifecycle ----

    /** Starts a table in [room]; returns false (with a message) if the player can't afford it. */
    fun enterRoom(room: Room): Boolean {
        val profile = appData.value?.profile ?: return false
        val coins = profile.coins
        if (coins < room.minCoins) {
            sound.play(Sfx.ERROR)
            val relief = if (appData.value?.profile?.canClaimRelief == true) "，可以先领救济金" else ""
            _messages.tryEmit("金币不够 %,d，进不了%s%s".format(room.minCoins, room.title, relief))
            return false
        }
        startGame(room, Personas.forRoom(room, humanAvatarId = profile.avatarId))
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
        val profile = appData.value?.profile ?: return false
        val coins = profile.coins
        if (coins < sess.room.minCoins) {
            sound.play(Sfx.ERROR)
            _messages.tryEmit("金币不足，请回大厅领取救济金或换个场次")
            return false
        }
        startGame(sess.room, Personas.refresh(sess.personas, sess.room, humanAvatarId = profile.avatarId))
        return true
    }

    /** Leaves the table; an unfinished game stays saved for 继续上一局. */
    fun leaveTable() {
        loop?.cancel()
        loop = null
        save()
        soundSequence?.cancel()
        audio.stop(audioOwner)
        session.value = null
        selection.value = emptySet()
        trustee.value = false
        showResult.value = false
        dealing.value = false
        setPaused(false)
    }

    fun setForeground(value: Boolean) {
        foreground.value = value
        audio.setForeground(audioOwner, value)
        if (!value) soundSequence?.cancel()
    }

    /** A local game has no clock to outrun: stop the entire turn scheduler until resumed. */
    fun setPaused(value: Boolean) {
        if (value && (session.value == null || session.value?.state?.phase == Phase.FINISHED)) return
        _paused.value = value
        audio.setPaused(audioOwner, value)
        if (value) soundSequence?.cancel()
        if (value) save()
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
        begin(
            room, Personas.forRoom(room, humanAvatarId = appData.value?.profile?.avatarId ?: BuiltInPortraits.DEFAULT_ID),
            state, events,
        )
    }

    private fun begin(room: Room, personas: List<Persona>, state: GameState, events: List<GameEvent>) {
        loop?.cancel()
        soundSequence?.cancel()
        audio.stop(audioOwner)
        setPaused(false)
        bots = listOf(null, Bots.create(room.difficulty), Bots.create(room.difficulty))
        selection.value = emptySet()
        trustee.value = keepTrustee
        trusteeSince = 0
        showResult.value = state.phase == Phase.FINISHED
        dealing.value = false
        dealUntil = 0
        hintSeq = -1
        turnCueSeq = -1
        val humanAvatarId = appData.value?.profile?.avatarId ?: BuiltInPortraits.DEFAULT_ID
        session.value = Session(room, Personas.normalize(personas, humanAvatarId), state)
        handleEvents(events, state)
        startLoop()
    }

    // ---- the turn loop ----

    private fun startLoop() {
        loop = viewModelScope.launch {
            combine(session, trustee, combine(foreground, _paused) { fg, pause -> fg && !pause }, ::Triple).collectLatest { (sess, auto, fg) ->
                val s = sess?.state ?: return@collectLatest
                if (!fg || s.phase == Phase.FINISHED) return@collectLatest
                val wait = dealUntil - SystemClock.uptimeMillis()
                if (wait > 0) delay(wait)

                val actors = GameEngine.actors(s)
                val seat = actors.firstOrNull { it != HUMAN || auto }
                if (seat == null) {
                    // Only the human can act. With nothing that beats the table, pass after a moment
                    // (the table explains why); otherwise a soft chime says it's their turn.
                    val trick = s.trick
                    if (s.phase == Phase.PLAYING && trick != null && !MoveGenerator.canBeat(s.hands[HUMAN].counts(), trick)) {
                        delay(if (turbo) 10 else (AUTO_PASS_MS * speedFactor).toLong())
                        waitForVoice()
                        dispatch(Action.Pass(HUMAN))
                    } else if (s.phase == Phase.BIDDING || s.phase == Phase.DOUBLING || s.phase == Phase.PLAYING) {
                        waitForVoice()
                        cueTurn(s.seq)
                    }
                    return@collectLatest
                }
                if (seat == HUMAN && !turbo) {
                    // A moment's grace after 托管 is switched on, so an accidental tap can be undone.
                    val grace = trusteeSince + TRUSTEE_GRACE_MS - SystemClock.uptimeMillis()
                    if (grace > 0) delay(grace)
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
                waitForVoice()
                dispatch(action)
            }
        }
    }

    private fun budget(room: Room): SearchBudget =
        if (room.difficulty == Difficulty.HARD) SearchBudget.Millis(HARD_THINK_MS) else SearchBudget.Samples(8)

    private val speedFactor: Double get() = appData.value?.settings?.speed?.factor ?: 1.0

    /** Plays the your-turn chime once per engine step (not again after returning from the background). */
    private fun cueTurn(seq: Int) {
        if (turnCueSeq == seq) return
        turnCueSeq = seq
        sound.play(Sfx.TURN)
    }

    /** Let natural recordings finish before another automatic action starts talking. */
    private suspend fun waitForVoice() {
        if (turbo) return
        val deadline = SystemClock.uptimeMillis() + 12_000
        while (voice.busy && SystemClock.uptimeMillis() < deadline) delay(60)
    }

    private fun pacing(s: GameState, seat: Int): Long {
        if (turbo) return 0
        val speed = speedFactor
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
            if (action.seat == HUMAN) feedback("这手牌不能出", "error_combo")
            return
        }
        if (action.seat == HUMAN || t.state.phase != sess.state.phase) selection.value = emptySet()
        val result = t.state.result
        val personas = if (result == null) sess.personas else sess.personas.mapIndexed { seat, p ->
            if (seat == HUMAN) p else p.copy(coins = (p.coins + result.delta[seat]).coerceAtLeast(0))
        }
        val cannotBeat = action is Action.Pass && sess.state.trick?.let {
            !MoveGenerator.canBeat(sess.state.hands[action.seat].counts(), it)
        } == true
        // Main.immediate collectors can react synchronously: queue this play before publishing
        // the next turn so its chime and automatic action always see the pending announcement.
        handleEvents(t.events, t.state, cannotBeat)
        session.value = sess.copy(state = t.state, personas = personas)
        if (result != null) {
            container.scope.launch { store.settle(t.state, result) }
            viewModelScope.launch {
                delay(if (turbo) 150 else RESULT_DELAY_MS)
                if (session.value?.state?.seed != t.state.seed) return@launch
                showResult.value = true
                if (autoRestart) {
                    delay(if (turbo) 150 else AUTO_RESTART_MS)
                    waitForVoice()
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

    private fun handleEvents(events: List<GameEvent>, state: GameState, cannotBeat: Boolean = false) {
        val plan = GameAudioPlanner.plan(events, state.seq, state.result?.let(::humanWon) == true, cannotBeat)
        val queueAhead = voice.estimatedRemainingMs
        if (!turbo) voice.enqueue(plan.lines)
        soundSequence?.cancel()
        soundSequence = viewModelScope.launch {
            val voiced = voice.enabled && voice.active && voice.volume > 0f && !turbo
            var previousEnd = 0L
            val cues = plan.sounds.map { cue ->
                val offset = if (cue.afterLine < 0) 0L else if (voiced) {
                    queueAhead + plan.lines.take(cue.afterLine + 1).sumOf { voice.durationMs(it) + 80 }
                } else 220L
                val start = maxOf(offset, previousEnd)
                previousEnd = start + sound.durationMs(cue.sound)
                start to cue.sound
            }
            var elapsed = 0L
            for ((offset, sfx) in cues) {
                if (offset > elapsed) delay(offset - elapsed)
                elapsed = offset
                if (foreground.value && !_paused.value && session.value?.state?.seed == state.seed && !turbo) sound.play(sfx)
            }
        }
        for (e in events) {
            when (e) {
                is GameEvent.Dealt -> {
                    if (e.isRedeal) _messages.tryEmit("没有人叫地主，重新发牌")
                    dealUntil = SystemClock.uptimeMillis() + dealMs
                    dealing.value = true
                    viewModelScope.launch {
                        delay(dealMs)
                        if (SystemClock.uptimeMillis() >= dealUntil) dealing.value = false
                    }
                }
                is GameEvent.LandlordSet -> {
                    _effects.tryEmit(TableEffect.LandlordChosen(e.seat))
                }
                is GameEvent.Played -> {
                    _effects.tryEmit(TableEffect.ComboPlayed(e.seat, e.combo, state.seq))
                }
                is GameEvent.Finished -> {
                    val won = humanWon(e.result)
                    _effects.tryEmit(TableEffect.GameOver(e.result, won))
                }
                else -> {}
            }
        }
    }

    // ---- human input ----

    fun bid(take: Boolean) = humanAct { Action.Bid(HUMAN, take) }

    fun jiabei(yes: Boolean) = humanAct { Action.Jiabei(HUMAN, yes) }

    fun pass() = humanAct { Action.Pass(HUMAN) }

    /** 出牌 is always tappable, so every way it can fail says why. */
    fun play() {
        val s = session.value?.state ?: return
        if (!humanCanAct(s)) return
        val chosen = selection.value.filter { it in s.hands[HUMAN] }
        if (chosen.isEmpty()) {
            feedback("请先点选要出的牌，或点「提示」", "error_empty")
            return
        }
        val cards = CardSet.of(chosen)
        val trick = s.trick
        val combo = if (trick == null) ComboClassifier.declareLead(cards.counts()) else ComboClassifier.declareFollow(cards.counts(), trick)
        if (combo == null) {
            val invalid = trick == null || ComboClassifier.interpretations(cards.counts()).isEmpty()
            feedback(if (invalid) "这几张牌不能一起出" else "这手牌大不过上家", if (invalid) "error_combo" else "error_small")
            return
        }
        humanAct { Action.Play(HUMAN, cards, combo) }
    }

    private fun humanCanAct(s: GameState): Boolean = !_paused.value && !trustee.value && !dealing.value && HUMAN in GameEngine.actors(s)

    private inline fun humanAct(make: () -> Action) {
        val s = session.value?.state ?: return
        if (!humanCanAct(s)) return
        audio.onUserInteraction(audioOwner)
        dispatch(make())
    }

    fun toggle(card: Card) {
        if (_paused.value || dealing.value || trustee.value) return
        audio.onUserInteraction(audioOwner)
        sound.play(if (card in selection.value) Sfx.DESELECT else Sfx.SELECT)
        selection.value = selection.value.let { if (card in it) it - card else it + card }
    }

    /** Sets every card in [cards] to [selected] (drag selection). */
    fun setSelected(cards: Collection<Card>, selected: Boolean) {
        if (_paused.value || dealing.value || trustee.value) return
        audio.onUserInteraction(audioOwner)
        val next = if (selected) selection.value + cards else selection.value - cards.toSet()
        if (next != selection.value) sound.play(if (selected) Sfx.SELECT else Sfx.DESELECT)
        selection.value = next
    }

    fun setTrustee(on: Boolean) {
        if (on == trustee.value) return
        if (on && !trustee.value) trusteeSince = SystemClock.uptimeMillis()
        trustee.value = on
        if (on) selection.value = emptySet()
        voice.enqueue(listOf(VoiceLine(if (on) "trustee_on" else "trustee_off", HUMAN)))
    }

    /** 提示: cycles through suggested plays, cheapest breakage first, bombs last. */
    fun hint() {
        val s = session.value?.state ?: return
        if (s.phase != Phase.PLAYING || s.turn != HUMAN || trustee.value || _paused.value) return
        if (hintSeq != s.seq) {
            hints = computeHints(s)
            hintIndex = 0
            hintSeq = s.seq
        }
        if (hints.isEmpty()) {
            feedback("没有能大过上家的牌", "error_small")
            return
        }
        val m = hints[hintIndex % hints.size]
        hintIndex++
        selection.value = s.hands[HUMAN].pick(m.counts).cards().toSet()
        sound.play(Sfx.HINT)
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
            val granted = store.claimRelief()
            _messages.tryEmit(
                if (granted) "已领取救济金 ${Profile.RELIEF_AMOUNT} 金币"
                else "金币少于 ${Profile.RELIEF_THRESHOLD} 时才能领取救济金",
            )
            if (granted) {
                sound.play(Sfx.RELIEF)
                voice.enqueue(listOf(VoiceLine("relief", HUMAN)))
            } else sound.play(Sfx.ERROR)
        }
    }

    fun clickUi() { audio.onUserInteraction(audioOwner); sound.play(Sfx.CLICK) }

    fun previewSound() {
        if (!sound.enabled || sound.volume == 0f) { _messages.tryEmit("请先开启音效并调高音效音量"); return }
        sound.play(Sfx.TURN)
    }

    fun previewVoice(seat: Int) {
        if (!voice.enabled || voice.volume == 0f) { _messages.tryEmit("请先开启语音报牌并调高语音音量"); return }
        voice.stop()
        voice.enqueue(listOf(VoiceLine("call", seat, important = true), VoiceLine("pair_3", seat, important = true), VoiceLine("rocket", seat, important = true)))
    }

    private fun feedback(message: String, key: String) {
        _messages.tryEmit(message)
        val now = SystemClock.uptimeMillis()
        if (now - lastFeedbackAt < 1_200) return
        lastFeedbackAt = now
        sound.play(Sfx.ERROR)
        voice.enqueue(listOf(VoiceLine(key, HUMAN)))
    }

    fun updateSettings(transform: (Settings) -> Settings) {
        viewModelScope.launch { store.updateSettings(transform) }
    }

    fun setAvatar(id: String) {
        viewModelScope.launch { store.updateAvatar(id) }
    }

    fun resetData() {
        viewModelScope.launch {
            store.resetAll()
            _messages.tryEmit("数据已重置")
        }
    }

    override fun onCleared() {
        soundSequence?.cancel()
        audio.setForeground(audioOwner, false)
        audio.stop(audioOwner)
    }

    private fun humanWon(r: GameResult): Boolean = (r.landlord == HUMAN) == r.landlordWon

    companion object {
        private const val TAG = "GameViewModel"
        const val DEAL_MS = 1_500L
        const val AUTO_PASS_MS = 1_500L
        const val TRUSTEE_GRACE_MS = 2_000L
        const val RESULT_DELAY_MS = 1_700L
        const val HARD_THINK_MS = 450L
        private const val AUTO_RESTART_MS = 1_200L
        private const val SLOW_DECISION_MS = 150L
    }
}
