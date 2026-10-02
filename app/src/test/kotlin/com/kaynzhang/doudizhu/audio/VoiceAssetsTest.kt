package com.kaynzhang.doudizhu.audio

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest

/** The generated recordings must cover the same contract used by the game, for every seat. */
class VoiceAssetsTest {
    @Test
    fun everyPlayerHasARealRecordingForEveryAnnouncedPhrase() {
        val moduleAssets = File("src/main/assets")
        val assets = if (moduleAssets.isDirectory) moduleAssets else File("app/src/main/assets")
        val catalogFile = File(assets, "voices/catalog.json")
        assertTrue("the offline voice catalog must be bundled", catalogFile.isFile)
        val catalog = Json.parseToJsonElement(catalogFile.readText()).jsonObject
        val voices = catalog.getValue("voices").jsonObject

        assertEquals(setOf("0", "1", "2"), voices.keys)
        for (seat in 0..2) {
            val lines = voices.getValue(seat.toString()).jsonObject
            assertEquals("seat $seat must cover every game phrase", VoicePhrases.all.keys, lines.keys)
            for ((key, expectedText) in VoicePhrases.all) {
                val entry = lines.getValue(key).jsonObject
                val path = entry.getValue("path").jsonPrimitive.content
                val durationMs = entry.getValue("durationMs").jsonPrimitive.long
                val label = "seat $seat / $key"
                assertEquals("$label text", expectedText, entry.getValue("text").jsonPrimitive.content)
                assertTrue("$label path must belong to its seat", path.startsWith("voices/$seat/") && ".." !in path)
                assertTrue("$label duration must be playable", durationMs in 1L..30_000L)
                val recording = File(assets, path)
                assertTrue("$label recording is missing or empty", recording.isFile && recording.length() > 100L)
                val hash = MessageDigest.getInstance("SHA-256").digest(recording.readBytes())
                    .joinToString("") { "%02x".format(it.toInt() and 0xff) }
                assertEquals("$label must match the verified recording", entry.getValue("sha256").jsonPrimitive.content, hash)
                if (recording.extension == "m4a") {
                    val header = recording.inputStream().use { it.readNBytes(12) }
                    assertEquals("$label must contain an MPEG-4 recording", "ftyp", header.copyOfRange(4, 8).decodeToString())
                }
            }
        }
    }
}
