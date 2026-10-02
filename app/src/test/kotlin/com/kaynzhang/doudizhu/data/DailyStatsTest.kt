package com.kaynzhang.doudizhu.data

import com.kaynzhang.doudizhu.engine.game.GameResult
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class DailyStatsTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun aLegacySaveKeepsLifetimeStatsWithoutInventingDailyHistory() {
        val legacyJson = """
            {
                "schema": 1,
                "profile": { "coins": 72345 },
                "stats": {
                    "games": 19, "wins": 8,
                    "landlordGames": 7, "landlordWins": 3,
                    "farmerGames": 12, "farmerWins": 5,
                    "bestWin": 4500, "springs": 2
                },
                "lastSettledSeed": 527
            }
        """.trimIndent()

        val restored = json.decodeFromString(AppData.serializer(), legacyJson)

        assertEquals(72_345L, restored.profile.coins)
        assertEquals(
            Stats(
                games = 19, wins = 8,
                landlordGames = 7, landlordWins = 3,
                farmerGames = 12, farmerWins = 5,
                bestWin = 4_500, springs = 2,
            ),
            restored.stats,
        )
        assertEquals(527L, restored.lastSettledSeed)
        assertTrue(restored.dailyStats.isEmpty())
    }

    @Test
    fun settlementsAccumulateByDayWhileLifetimeStatsIncludeBothDays() {
        val data = AppData(profile = Profile(coins = 10_000))
            .settled(101, result(landlord = 0, landlordWon = true, farmerStake = 200, spring = true), "2026-10-01")
            .settled(102, result(landlord = 1, landlordWon = true, farmerStake = 150), "2026-10-01")
            .settled(103, result(landlord = 2, landlordWon = false, farmerStake = 300, antiSpring = true), "2026-10-02")

        assertEquals(10_550L, data.profile.coins)
        assertEquals(
            Stats(
                games = 3, wins = 2,
                landlordGames = 1, landlordWins = 1,
                farmerGames = 2, farmerWins = 1,
                bestWin = 400, springs = 2,
            ),
            data.stats,
        )
        assertEquals(setOf("2026-10-01", "2026-10-02"), data.dailyStats.keys)
        assertEquals(
            DailyStats(
                stats = Stats(
                    games = 2, wins = 1,
                    landlordGames = 1, landlordWins = 1,
                    farmerGames = 1, farmerWins = 0,
                    bestWin = 400, springs = 1,
                ),
                netCoins = 250,
            ),
            data.dailyStats["2026-10-01"],
        )
        assertEquals(
            DailyStats(
                stats = Stats(games = 1, wins = 1, farmerGames = 1, farmerWins = 1, bestWin = 300, springs = 1),
                netCoins = 300,
            ),
            data.dailyStats["2026-10-02"],
        )
        assertEquals(103L, data.lastSettledSeed)
    }

    @Test
    fun repeatingTheLastSeedOnAnotherDayCannotPayOutOrCreateAnotherDailyRecord() {
        val game = result(landlord = 0, landlordWon = true, farmerStake = 500)
        val settled = AppData(profile = Profile(coins = 10_000)).settled(527, game, "2026-10-01")

        val repeated = settled.settled(527, game, "2026-10-02")

        assertSame(settled, repeated)
        assertEquals(11_000L, repeated.profile.coins)
        assertEquals(1, repeated.stats.games)
        assertEquals(setOf("2026-10-01"), repeated.dailyStats.keys)
        assertEquals(1, repeated.dailyStats.getValue("2026-10-01").stats.games)
        assertEquals(1_000L, repeated.dailyStats.getValue("2026-10-01").netCoins)
    }

    @Test
    fun dailyNetCoinsIncludesLossesAndCanStayNegative() {
        val daily = DailyStats()
            .record(result(landlord = 2, landlordWon = false, farmerStake = 100))
            .record(result(landlord = 1, landlordWon = true, farmerStake = 450))

        assertEquals(-350L, daily.netCoins)
        assertEquals(2, daily.stats.games)
        assertEquals(1, daily.stats.wins)
        assertEquals(100L, daily.stats.bestWin)
        assertEquals(2, daily.stats.farmerGames)
    }

    @Test
    fun dailyRecordsUseTheRequestedHumanSeatForBothRoleAndCoinChange() {
        val daily = DailyStats()
            .record(result(landlord = 1, landlordWon = true, farmerStake = 300), humanSeat = 1)
            .record(result(landlord = 2, landlordWon = false, farmerStake = 100), humanSeat = 1)

        assertEquals(700L, daily.netCoins)
        assertEquals(
            Stats(games = 2, wins = 2, landlordGames = 1, landlordWins = 1, farmerGames = 1, farmerWins = 1, bestWin = 600),
            daily.stats,
        )
    }

    @Test
    fun serializedDailyHistoryCanContinueAccumulatingAfterRestore() {
        val original = AppData(profile = Profile(coins = 10_000))
            .settled(201, result(landlord = 0, landlordWon = false, farmerStake = 200), "2026-10-01")
            .settled(202, result(landlord = 1, landlordWon = false, farmerStake = 300), "2026-10-02")
        val restored = json.decodeFromString(AppData.serializer(), json.encodeToString(AppData.serializer(), original))

        assertEquals(original, restored)
        val continued = restored.settled(203, result(landlord = 2, landlordWon = false, farmerStake = 100), "2026-10-02")

        assertEquals(10_000L, continued.profile.coins)
        assertEquals(3, continued.stats.games)
        assertEquals(2, continued.stats.wins)
        assertEquals(original.dailyStats["2026-10-01"], continued.dailyStats["2026-10-01"])
        assertEquals(-400L, continued.dailyStats.getValue("2026-10-01").netCoins)
        assertEquals(400L, continued.dailyStats.getValue("2026-10-02").netCoins)
        assertEquals(2, continued.dailyStats.getValue("2026-10-02").stats.games)
        assertEquals(2, continued.dailyStats.getValue("2026-10-02").stats.wins)
    }

    private fun result(
        landlord: Int,
        landlordWon: Boolean,
        farmerStake: Long,
        spring: Boolean = false,
        antiSpring: Boolean = false,
    ): GameResult {
        val raw = (0..2).map { if (it == landlord) farmerStake * 2 else farmerStake }
        return GameResult(
            winner = if (landlordWon) landlord else (landlord + 1) % 3,
            landlord = landlord,
            landlordWon = landlordWon,
            spring = spring,
            antiSpring = antiSpring,
            robs = 0,
            bombs = 0,
            jiabei = listOf(false, false, false),
            commonMultiplier = if (spring || antiSpring) 2 else 1,
            raw = raw,
            delta = raw.mapIndexed { seat, amount -> if ((seat == landlord) == landlordWon) amount else -amount },
            capped = false,
        )
    }
}
