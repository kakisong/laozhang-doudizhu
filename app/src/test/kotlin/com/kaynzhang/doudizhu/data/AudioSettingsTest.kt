package com.kaynzhang.doudizhu.data

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
        assertTrue(settings.music)
        assertEquals(25, settings.musicVolume)
    }

    @Test
    fun previousAppDocumentGetsMusicDefaultsWithoutLosingExistingPreferences() = runBlocking {
        val saved = """{"profile":{"coins":12345},"settings":{"sound":false,"voice":true,"soundVolume":45,"voiceVolume":85,"cardCounter":false,"speed":"NORMAL"},"stats":{"games":7,"wins":4}}"""
        val data = AppDataSerializer.readFrom(ByteArrayInputStream(saved.encodeToByteArray()))

        assertEquals(12345L, data.profile.coins)
        assertEquals(7, data.stats.games)
        assertEquals(4, data.stats.wins)
        assertEquals(Settings(sound = false, soundVolume = 45, voiceVolume = 85,
            cardCounter = false, speed = Speed.NORMAL), data.settings)
    }

    @Test
    fun volumeSettingsRoundTripWithoutChangingOtherPreferences() {
        val settings = Settings(soundVolume = 20, voiceVolume = 95, cardCounter = false,
            music = false, musicVolume = 40)
        assertEquals(settings, Json.decodeFromString<Settings>(Json.encodeToString(Settings.serializer(), settings)))
    }

    @Test
    fun importedAppDocumentClampsEachVolumeIndependently() = runBlocking {
        val saved = """{"settings":{"sound":false,"voice":true,"soundVolume":-20,"voiceVolume":125,"music":false,"musicVolume":-40}}"""
        val settings = AppDataSerializer.readFrom(ByteArrayInputStream(saved.encodeToByteArray())).settings

        assertEquals(0, settings.soundVolume)
        assertEquals(100, settings.voiceVolume)
        assertEquals(0, settings.musicVolume)
        assertFalse(settings.sound)
        assertTrue(settings.voice)
        assertFalse(settings.music)
    }

    @Test
    fun persistedAppDocumentClampsVolumesAndRetainsMusicMute() = runBlocking {
        val data = AppData(settings = Settings(soundVolume = 150, voiceVolume = -5,
            music = false, musicVolume = 200))
        val output = ByteArrayOutputStream()
        AppDataSerializer.writeTo(data, output)
        val persisted = Json.decodeFromString<AppData>(output.toString(Charsets.UTF_8.name()))

        assertEquals(data.copy(settings = data.settings.copy(soundVolume = 100, voiceVolume = 0,
            musicVolume = 100)), persisted)
        assertFalse(persisted.settings.music)
    }
}
