package com.kaynzhang.doudizhu.audio

import com.kaynzhang.doudizhu.engine.game.BidKind
import com.kaynzhang.doudizhu.engine.game.GameEvent
import com.kaynzhang.doudizhu.engine.game.GameResult
import com.kaynzhang.doudizhu.engine.model.CardSet
import com.kaynzhang.doudizhu.engine.rules.Combo
import com.kaynzhang.doudizhu.engine.rules.ComboType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GameAudioPlanTest {

    @Test
    fun playedCardsStayBeforeTheirOneOrTwoCardAlert() {
        for (cardsLeft in 1..2) {
            val plan = GameAudioPlanner.plan(
                listOf(played(Combo(ComboType.PAIR, 5), seat = 2, cardsLeft = cardsLeft), GameEvent.Alert(2, cardsLeft)),
                seq = 31, humanWon = false,
            )

            assertEquals(listOf("pair_8", if (cardsLeft == 1) "alert_one" else "alert_two"), plan.lines.map { it.key })
            assertEquals(listOf(2, 2), plan.lines.map { it.seat })
            assertTrue(plan.lines.first().important)
            assertTrue(plan.lines.last().important)
            assertEquals(listOf(SoundCue(Sfx.PLAY), SoundCue(Sfx.ALERT, afterLine = 0)), plan.sounds)
        }
    }

    @Test
    fun lastPlaySpringAndResultAreKeptInThatOrder() {
        for (antiSpring in listOf(false, true)) {
            for (humanWon in listOf(false, true)) {
                val plan = GameAudioPlanner.plan(
                    listOf(
                        played(Combo(ComboType.SINGLE, 11), seat = 1, cardsLeft = 0),
                        GameEvent.Finished(result(spring = !antiSpring, antiSpring = antiSpring)),
                    ),
                    seq = 88, humanWon = humanWon,
                )

                assertEquals(
                    listOf("single_a", if (antiSpring) "anti_spring" else "spring", if (humanWon) "win" else "lose"),
                    plan.lines.map { it.key },
                )
                assertEquals(
                    listOf(SoundCue(Sfx.PLAY), SoundCue(Sfx.SPRING, 0), SoundCue(if (humanWon) Sfx.WIN else Sfx.LOSE, 1)),
                    plan.sounds,
                )
                assertTrue(plan.lines.all { it.important })
            }
        }
    }

    @Test
    fun privateDoublingChoicesProduceNoAudioUntilRevealed() {
        for (seat in 0..2) {
            val hidden = GameAudioPlanner.plan(listOf(GameEvent.JiabeiChosen(seat)), seq = seat, humanWon = false)
            assertTrue("seat $seat choice is private", hidden.lines.isEmpty())
            assertTrue("seat $seat cannot reveal its choice with a sound", hidden.sounds.isEmpty())
        }

        val revealed = GameAudioPlanner.plan(
            listOf(GameEvent.JiabeiChosen(2), GameEvent.JiabeiRevealed(listOf(true, false, true))),
            seq = 8, humanWon = false,
        )
        assertEquals(listOf("double", "no_double", "double"), revealed.lines.map { it.key })
        assertEquals(listOf(0, 1, 2), revealed.lines.map { it.seat })
        assertTrue(revealed.lines.all { it.important })
        assertEquals(listOf(SoundCue(Sfx.DOUBLE)), revealed.sounds)
    }

    @Test
    fun eachComboKeepsItsVoiceIdentityAndSpecialSound() {
        val expected = mapOf(
            ComboType.SINGLE to ("single_3" to Sfx.PLAY),
            ComboType.PAIR to ("pair_3" to Sfx.PLAY),
            ComboType.TRIPLE to ("triple_3" to Sfx.PLAY),
            ComboType.TRIPLE_SINGLE to ("triple_single" to Sfx.PLAY),
            ComboType.TRIPLE_PAIR to ("triple_pair" to Sfx.PLAY),
            ComboType.STRAIGHT to ("straight" to Sfx.STRAIGHT),
            ComboType.PAIR_STRAIGHT to ("pair_straight" to Sfx.PAIR_STRAIGHT),
            ComboType.PLANE to ("plane" to Sfx.PLANE),
            ComboType.PLANE_SINGLES to ("plane_singles" to Sfx.PLANE),
            ComboType.PLANE_PAIRS to ("plane_pairs" to Sfx.PLANE),
            ComboType.FOUR_TWO_SINGLES to ("four_two_singles" to Sfx.PLAY),
            ComboType.FOUR_TWO_PAIRS to ("four_two_pairs" to Sfx.PLAY),
            ComboType.BOMB to ("bomb" to Sfx.BOMB),
            ComboType.ROCKET to ("rocket" to Sfx.ROCKET),
        )
        assertEquals(ComboType.entries.toSet(), expected.keys)
        for ((type, identity) in expected) {
            val len = when (type) {
                ComboType.STRAIGHT -> 5
                ComboType.PAIR_STRAIGHT -> 3
                ComboType.PLANE, ComboType.PLANE_SINGLES, ComboType.PLANE_PAIRS -> 2
                else -> 1
            }
            val plan = GameAudioPlanner.plan(listOf(played(Combo(type, 0, len))), seq = 4, humanWon = false)
            assertEquals(type.name, listOf(identity.first), plan.lines.map { it.key })
            assertEquals(type.name, listOf(SoundCue(identity.second)), plan.sounds)
        }
    }

    @Test
    fun emptyHandNeverAnnouncesOneOrTwoCardsLeft() {
        val plan = GameAudioPlanner.plan(
            listOf(played(Combo(ComboType.SINGLE, 0), cardsLeft = 0), GameEvent.Alert(0, 0), GameEvent.Finished(result())),
            seq = 40, humanWon = true,
        )

        assertEquals(listOf("single_3", "win"), plan.lines.map { it.key })
        assertEquals(listOf(SoundCue(Sfx.PLAY), SoundCue(Sfx.WIN, 0)), plan.sounds)
        assertFalse(plan.sounds.any { it.sound == Sfx.ALERT })
    }

    @Test
    fun biddingConclusionKeepsTheBidBeforeTheLandlord() {
        val plan = GameAudioPlanner.plan(
            listOf(GameEvent.BidMade(1, BidKind.ROB, 1), GameEvent.LandlordSet(1, CardSet(0L), 1)),
            seq = 5, humanWon = false,
        )

        assertEquals(listOf("rob", "landlord"), plan.lines.map { it.key })
        assertEquals(listOf(1, 1), plan.lines.map { it.seat })
        assertEquals(listOf(SoundCue(Sfx.BID), SoundCue(Sfx.LANDLORD, 0)), plan.sounds)
    }

    @Test
    fun passAndTrickClearedAddOnlyThePlayersPassLine() {
        val plan = GameAudioPlanner.plan(
            listOf(GameEvent.Passed(2), GameEvent.TrickCleared(0)),
            seq = 11, humanWon = false,
        )

        assertEquals(listOf("pass_2"), plan.lines.map { it.key })
        assertEquals(listOf(2), plan.lines.map { it.seat })
        assertEquals(listOf(SoundCue(Sfx.PASS)), plan.sounds)
    }

    @Test
    fun cannotBeatSaysSoWhileVoluntaryPassingDoesNot() {
        for (seq in 0..5) {
            val event = listOf(GameEvent.Passed(0))
            val automatic = GameAudioPlanner.plan(event, seq, humanWon = false, cannotBeat = true)
            val voluntary = GameAudioPlanner.plan(event, seq, humanWon = false, cannotBeat = false)
            assertEquals(listOf("pass_1"), automatic.lines.map { it.key })
            assertTrue(voluntary.lines.single().key in setOf("pass_0", "pass_2"))
        }
    }

    private fun played(combo: Combo, seat: Int = 0, cardsLeft: Int = 5) =
        GameEvent.Played(seat, CardSet(0L), combo, cardsLeft)

    private fun result(spring: Boolean = false, antiSpring: Boolean = false) = GameResult(
        winner = 0,
        landlord = 0,
        landlordWon = true,
        spring = spring,
        antiSpring = antiSpring,
        robs = 0,
        bombs = 0,
        jiabei = listOf(false, false, false),
        commonMultiplier = if (spring || antiSpring) 2 else 1,
        raw = listOf(200L, 100L, 100L),
        delta = listOf(200L, -100L, -100L),
        capped = false,
    )
}
