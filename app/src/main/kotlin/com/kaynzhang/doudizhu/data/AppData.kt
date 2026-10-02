package com.kaynzhang.doudizhu.data

import com.kaynzhang.doudizhu.engine.ai.Difficulty
import com.kaynzhang.doudizhu.engine.game.GameResult
import com.kaynzhang.doudizhu.engine.game.GameState
import kotlinx.serialization.Serializable

/** Everything the app persists, stored as one JSON document so related updates are atomic. */
@Serializable
data class AppData(
    val schema: Int = SCHEMA,
    val profile: Profile = Profile(),
    val settings: Settings = Settings(),
    val stats: Stats = Stats(),
    val savedTable: SavedTable? = null,
    /** Seed of the last settled game; late saves of that game are ignored so it can't pay out twice. */
    val lastSettledSeed: Long? = null,
) {
    companion object {
        const val SCHEMA = 1
    }
}

@Serializable
data class Profile(
    val coins: Long = STARTING_COINS,
) {
    /** 救济金 is available whenever the player is nearly broke, as often as needed. */
    val canClaimRelief: Boolean get() = coins < RELIEF_THRESHOLD

    companion object {
        const val STARTING_COINS = 10_000L
        const val RELIEF_THRESHOLD = 1_000L
        const val RELIEF_AMOUNT = 3_000L
    }
}

enum class Speed(val zh: String, val factor: Double) {
    SLOW("慢", 1.5),
    NORMAL("中", 1.0),
    FAST("快", 0.55),
}

@Serializable
data class Settings(
    val sound: Boolean = true,
    val voice: Boolean = true,
    val cardCounter: Boolean = true,
    val speed: Speed = Speed.NORMAL,
)

@Serializable
data class Stats(
    val games: Int = 0,
    val wins: Int = 0,
    val landlordGames: Int = 0,
    val landlordWins: Int = 0,
    val farmerGames: Int = 0,
    val farmerWins: Int = 0,
    val bestWin: Long = 0,
    val springs: Int = 0,
) {
    /** Stats after the human (seat 0) finished a game with [result]. */
    fun record(result: GameResult, humanSeat: Int = 0): Stats {
        val landlord = result.landlord == humanSeat
        val won = result.landlordWon == landlord
        val gain = result.delta[humanSeat]
        return copy(
            games = games + 1,
            wins = wins + if (won) 1 else 0,
            landlordGames = landlordGames + if (landlord) 1 else 0,
            landlordWins = landlordWins + if (landlord && won) 1 else 0,
            farmerGames = farmerGames + if (!landlord) 1 else 0,
            farmerWins = farmerWins + if (!landlord && won) 1 else 0,
            bestWin = maxOf(bestWin, gain),
            springs = springs + if (won && (result.spring || result.antiSpring)) 1 else 0,
        )
    }
}

/** A computer opponent's identity and bankroll for one table session. */
@Serializable
data class Persona(val name: String, val avatar: String, val coins: Long)

/** An unfinished table, restored by 继续上一局. */
@Serializable
data class SavedTable(
    val room: Room,
    /** Opponents by seat; index 0 is unused (the human). */
    val personas: List<Persona>,
    val state: GameState,
)

enum class Room(val title: String, val baseScore: Long, val difficulty: Difficulty, val minCoins: Long, val blurb: String) {
    NOVICE("新手场", 100, Difficulty.EASY, 100, "对手出牌随意，适合熟悉规则"),
    NORMAL("普通场", 500, Difficulty.NORMAL, 5_000, "对手会记牌、会配合"),
    MASTER("高手场", 2_000, Difficulty.HARD, 20_000, "对手推演残局，步步紧逼"),
}
