package com.kaynzhang.doudizhu.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AudioSettingsTest {
    @Test
    fun olderSavesKeepTheirSwitchesAndGetIndependentVolumeDefaults() {
        val settings = Json.decodeFromString<Settings>("""{"sound":false,"voice":true,"speed":"FAST"}""")
        assertFalse(settings.sound)
        assertEquals(true, settings.voice)
        assertEquals(Speed.FAST, settings.speed)
        assertEquals(75, settings.soundVolume)
        assertEquals(90, settings.voiceVolume)
    }

    @Test
    fun volumeSettingsRoundTripWithoutChangingOtherPreferences() {
        val settings = Settings(soundVolume = 20, voiceVolume = 95, cardCounter = false)
        assertEquals(settings, Json.decodeFromString<Settings>(Json.encodeToString(Settings.serializer(), settings)))
    }
}
