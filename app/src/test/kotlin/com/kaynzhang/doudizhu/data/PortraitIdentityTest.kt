package com.kaynzhang.doudizhu.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class PortraitIdentityTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun aSaveWithoutAnAvatarKeepsThePlayersCoinsAndStats() {
        val legacyJson = """
            {
                "schema": 1,
                "profile": { "coins": 72345 },
                "stats": {
                    "games": 19, "wins": 8,
                    "landlordGames": 7, "landlordWins": 3,
                    "farmerGames": 12, "farmerWins": 5,
                    "bestWin": 4500, "springs": 2
                }
            }
        """.trimIndent()

        val restored = json.decodeFromString(AppData.serializer(), legacyJson)

        assertEquals(72_345L, restored.profile.coins)
        assertEquals("lao_zhang", restored.profile.avatarId)
        assertEquals(
            Stats(games = 19, wins = 8, landlordGames = 7, landlordWins = 3, farmerGames = 12, farmerWins = 5, bestWin = 4_500, springs = 2),
            restored.stats,
        )
    }

    @Test
    fun everyPlayerPortraitCanEnterEveryRoomWithoutAnOpponentSharingTheirIdentity() {
        val catalogIds = BuiltInPortraits.all.map { it.id }.toSet()
        for (human in BuiltInPortraits.all) {
            for (room in Room.entries) {
                for (seed in listOf(5, 29, 73, 211)) {
                    val opponents = Personas.forRoom(room, Random(seed), humanAvatarId = human.id).drop(1)
                    val ids = opponents.map { it.avatar }
                    val case = "${human.id} ${room.name} seed=$seed"

                    assertEquals("$case: both opponent seats must be populated", 2, opponents.size)
                    assertEquals("$case: opponents must have distinct portraits", 2, ids.toSet().size)
                    assertFalse("$case: the player's portrait must be reserved", human.id in ids)
                    assertTrue("$case: new opponents must store stable catalog IDs", ids.all { it in catalogIds })
                }
            }
        }
    }

    @Test
    fun refreshMigratesLegacyIdentitiesAndReservesThePlayerPortraitWithoutChangingRetainedCoins() {
        val legacy = listOf(
            Persona("我", "🙂", 0),
            Persona("王大爷", "👴", 12_345),
            Persona("李阿姨", "👵", 67_890),
        )
        val catalogIds = BuiltInPortraits.all.map { it.id }.toSet()
        for (humanId in listOf("lao_zhang", "li_ayi")) {
            val refreshed = Personas.refresh(legacy, Room.NORMAL, Random(37), humanAvatarId = humanId)
            val opponents = refreshed.drop(1)
            val ids = opponents.map { it.avatar }

            assertEquals(legacy.drop(1).map { it.coins }, opponents.map { it.coins })
            assertEquals("wang_daye", opponents[0].avatar)
            assertEquals("王大爷", opponents[0].name)
            assertEquals(2, ids.toSet().size)
            assertFalse(humanId in ids)
            assertTrue(ids.all { it in catalogIds })
            if (humanId == "lao_zhang") {
                assertEquals("li_ayi", opponents[1].avatar)
                assertEquals("李阿姨", opponents[1].name)
            }
        }
    }
}
