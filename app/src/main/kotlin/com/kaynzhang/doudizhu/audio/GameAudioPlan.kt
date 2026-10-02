package com.kaynzhang.doudizhu.audio

import com.kaynzhang.doudizhu.engine.game.GameEvent
import com.kaynzhang.doudizhu.engine.rules.Combo
import com.kaynzhang.doudizhu.engine.rules.ComboType

data class SoundCue(val sound: Sfx, val afterLine: Int = -1)
data class GameAudioPlan(val lines: List<VoiceLine>, val sounds: List<SoundCue>)

/** Plans a complete engine transition before playing it, including its follow-up announcements. */
object GameAudioPlanner {
    fun plan(events: List<GameEvent>, seq: Int, humanWon: Boolean, cannotBeat: Boolean = false): GameAudioPlan {
        val lines = mutableListOf<VoiceLine>()
        val sounds = mutableListOf<SoundCue>()
        for (event in events) {
            when (event) {
                is GameEvent.Dealt -> {
                    sounds += SoundCue(Sfx.DEAL)
                    if (event.isRedeal) lines += VoiceLine("redeal", 0, important = true)
                }
                is GameEvent.BidMade -> {
                    sounds += SoundCue(Sfx.BID)
                    VoicePhrases.key(event, seq)?.let { lines += VoiceLine(it, event.seat) }
                }
                is GameEvent.LandlordSet -> {
                    sounds += SoundCue(Sfx.LANDLORD, lines.lastIndex)
                    lines += VoiceLine("landlord", event.seat, important = true)
                }
                is GameEvent.JiabeiChosen -> Unit // Choices stay private until all three have decided.
                is GameEvent.JiabeiRevealed -> {
                    sounds += SoundCue(Sfx.DOUBLE)
                    event.choices.forEachIndexed { seat, yes ->
                        lines += VoiceLine(if (yes) "double" else "no_double", seat, important = true)
                    }
                }
                is GameEvent.Played -> {
                    sounds += SoundCue(effect(event.combo))
                    val followedByImportant = events.any { it is GameEvent.Alert || it is GameEvent.Finished }
                    lines += VoiceLine(VoicePhrases.comboKey(event.combo), event.seat, important = followedByImportant)
                }
                is GameEvent.Passed -> {
                    sounds += SoundCue(Sfx.PASS)
                    val key = if (cannotBeat) "pass_1" else if (Math.floorMod(seq + event.seat, 2) == 0) "pass_0" else "pass_2"
                    lines += VoiceLine(key, event.seat)
                }
                is GameEvent.Alert -> if (event.cardsLeft in 1..2) {
                    sounds += SoundCue(Sfx.ALERT, lines.lastIndex)
                    lines += VoiceLine(if (event.cardsLeft == 1) "alert_one" else "alert_two", event.seat, important = true)
                }
                is GameEvent.Finished -> {
                    if (event.result.spring || event.result.antiSpring) {
                        sounds += SoundCue(Sfx.SPRING, lines.lastIndex)
                        lines += VoiceLine(if (event.result.spring) "spring" else "anti_spring", 0, important = true)
                    }
                    sounds += SoundCue(if (humanWon) Sfx.WIN else Sfx.LOSE, lines.lastIndex)
                    lines += VoiceLine(if (humanWon) "win" else "lose", 0, important = true)
                }
                is GameEvent.TrickCleared -> Unit
            }
        }
        return GameAudioPlan(lines, sounds)
    }

    private fun effect(combo: Combo) = when (combo.type) {
        ComboType.BOMB -> Sfx.BOMB
        ComboType.ROCKET -> Sfx.ROCKET
        ComboType.PLANE, ComboType.PLANE_SINGLES, ComboType.PLANE_PAIRS -> Sfx.PLANE
        ComboType.STRAIGHT -> Sfx.STRAIGHT
        ComboType.PAIR_STRAIGHT -> Sfx.PAIR_STRAIGHT
        else -> Sfx.PLAY
    }
}
