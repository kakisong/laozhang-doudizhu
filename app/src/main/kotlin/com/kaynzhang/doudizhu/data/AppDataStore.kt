package com.kaynzhang.doudizhu.data

import android.content.Context
import android.util.Log
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStoreFile
import com.kaynzhang.doudizhu.engine.game.GameResult
import com.kaynzhang.doudizhu.engine.game.GameState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream

/**
 * Single source of persisted truth. All writes go through [DataStore.updateData], so each is atomic;
 * table saves are conflated so a burst of moves costs one write.
 */
class AppDataStore(context: Context, private val scope: CoroutineScope) {

    private val store: DataStore<AppData> = DataStoreFactory.create(
        serializer = AppDataSerializer,
        corruptionHandler = ReplaceFileCorruptionHandler { AppData() },
        scope = scope,
        produceFile = { context.dataStoreFile("app_data.json") },
    )

    val data: Flow<AppData> = store.data

    private val pendingTable = MutableStateFlow<SavedTable?>(null)

    init {
        // StateFlow keeps only the newest pending table, so slow writes never queue up stale ones.
        scope.launch {
            pendingTable.filterNotNull().collect { table ->
                store.updateData { d ->
                    if (d.lastSettledSeed == table.state.seed) d else d.copy(savedTable = table)
                }
            }
        }
    }

    fun saveTable(table: SavedTable) {
        pendingTable.value = table
    }

    suspend fun clearTable() {
        pendingTable.value = null
        store.updateData { it.copy(savedTable = null) }
    }

    /** Credits the human, records stats and drops the saved table in one write. */
    suspend fun settle(state: GameState, result: GameResult) {
        store.updateData { d ->
            if (d.lastSettledSeed == state.seed) return@updateData d
            d.copy(
                profile = d.profile.copy(coins = (d.profile.coins + result.delta[0]).coerceAtLeast(0)),
                stats = d.stats.record(result),
                savedTable = null,
                lastSettledSeed = state.seed,
            )
        }
    }

    suspend fun updateSettings(transform: (Settings) -> Settings) {
        store.updateData { it.copy(settings = transform(it.settings)) }
    }

    /** Grants 救济金 when the player is nearly broke (no daily limit); returns true if coins were added. */
    suspend fun claimRelief(): Boolean {
        var granted = false
        store.updateData { d ->
            if (!d.profile.canClaimRelief) return@updateData d
            granted = true
            d.copy(profile = d.profile.copy(coins = d.profile.coins + Profile.RELIEF_AMOUNT))
        }
        return granted
    }

    suspend fun resetAll() {
        pendingTable.value = null
        store.updateData { AppData(settings = it.settings) }
    }
}

private object AppDataSerializer : Serializer<AppData> {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override val defaultValue = AppData()

    override suspend fun readFrom(input: InputStream): AppData {
        val text = input.readBytes().decodeToString()
        if (text.isBlank()) return defaultValue
        val data = try {
            json.decodeFromString(AppData.serializer(), text)
        } catch (e: SerializationException) {
            throw CorruptionException("unreadable app data", e)
        } catch (e: IllegalArgumentException) {
            throw CorruptionException("unreadable app data", e)
        }
        // A save from another schema can't be resumed safely; keep the profile, drop the table.
        return if (data.savedTable != null && data.savedTable.state.schema != GameState.SCHEMA) {
            Log.w("AppDataStore", "dropping saved table with schema ${data.savedTable.state.schema}")
            data.copy(savedTable = null)
        } else {
            data
        }
    }

    override suspend fun writeTo(t: AppData, output: OutputStream) {
        output.write(json.encodeToString(AppData.serializer(), t).encodeToByteArray())
    }
}
