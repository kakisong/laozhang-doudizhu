package com.kaynzhang.doudizhu.audio

import com.kaynzhang.doudizhu.engine.game.BidKind
import com.kaynzhang.doudizhu.engine.game.GameEvent
import com.kaynzhang.doudizhu.engine.model.Rk
import com.kaynzhang.doudizhu.engine.rules.Combo
import com.kaynzhang.doudizhu.engine.rules.ComboType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoicePhrasesTest {
    @Test
    fun everyLegalSinglePairAndTripleHasARecordingKey() {
        for (rank in 0 until Rk.COUNT) {
            assertNotNull(VoicePhrases.text(VoicePhrases.comboKey(Combo(ComboType.SINGLE, rank))))
            if (rank <= Rk.TWO) {
                assertNotNull(VoicePhrases.text(VoicePhrases.comboKey(Combo(ComboType.PAIR, rank))))
                assertNotNull(VoicePhrases.text(VoicePhrases.comboKey(Combo(ComboType.TRIPLE, rank))))
            }
        }
        for (type in ComboType.entries) {
            assertNotNull(VoicePhrases.text(VoicePhrases.comboKey(Combo(type, Rk.THREE))))
        }
    }

    @Test
    fun distinctWingTypesAndBothJokersAreAnnouncedAccurately() {
        assertEquals("飞机带单", VoicePhrases.text(VoicePhrases.comboKey(Combo(ComboType.PLANE_SINGLES, Rk.THREE, 2))))
        assertEquals("飞机带对", VoicePhrases.text(VoicePhrases.comboKey(Combo(ComboType.PLANE_PAIRS, Rk.THREE, 2))))
        assertEquals("小王", VoicePhrases.text(VoicePhrases.comboKey(Combo(ComboType.SINGLE, Rk.SJ))))
        assertEquals("大王", VoicePhrases.text(VoicePhrases.comboKey(Combo(ComboType.SINGLE, Rk.BJ))))
    }

    @Test
    fun biddingPassingAndAlertsResolveOnlyToSupportedKeys() {
        for (kind in BidKind.entries) {
            assertEquals(kind.zh, VoicePhrases.key(GameEvent.BidMade(1, kind, 0), 0)?.let(VoicePhrases::text))
        }
        for (seat in 0..2) for (seq in 0..12) {
            assertNotNull(VoicePhrases.key(GameEvent.Passed(seat), seq)?.let(VoicePhrases::text))
            assertTrue(VoicePhrases.key(GameEvent.Passed(seat), seq) in setOf("pass_0", "pass_2"))
        }
        assertEquals("alert_one", VoicePhrases.key(GameEvent.Alert(2, 1), 0))
        assertEquals("alert_two", VoicePhrases.key(GameEvent.Alert(1, 2), 0))
        assertNull(VoicePhrases.key(GameEvent.Alert(1, 3), 0))
        assertNull(VoicePhrases.key(GameEvent.Dealt(1, 0, false), 0))
        assertEquals("redeal", VoicePhrases.key(GameEvent.Dealt(2, 0, true), 0))
    }

    @Test
    fun compatibilityTextLookupCoversTheWholeGeneratedCatalog() {
        assertEquals(76, VoicePhrases.all.size)
        for ((key, text) in VoicePhrases.all) {
            assertEquals(key, VoicePhrases.keyForText(text))
            assertEquals(key, VoicePhrases.keyForText(key))
        }
        assertNull(VoicePhrases.keyForText("这句没有专门录制"))
    }
}
