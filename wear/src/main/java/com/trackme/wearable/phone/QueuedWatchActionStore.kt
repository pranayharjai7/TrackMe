package com.trackme.wearable.phone

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.trackme.wearbridge.WatchActionPayload
import com.trackme.wearbridge.WatchActionReplayPlanner
import com.trackme.wearbridge.WearProtocol
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.watchActionDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "trackme_watch_action_queue",
)

class QueuedWatchActionStore(context: Context) {
    private val dataStore = context.watchActionDataStore
    private val queueKey = stringPreferencesKey("watch_actions_json")

    val queuedCount: Flow<Int> = dataStore.data.map { prefs ->
        WearProtocol.decodeWatchActions(prefs[queueKey]).size
    }

    suspend fun read(): List<WatchActionPayload> =
        WearProtocol.decodeWatchActions(dataStore.data.first()[queueKey])

    suspend fun enqueue(action: WatchActionPayload) {
        dataStore.edit { prefs ->
            val current = WearProtocol.decodeWatchActions(prefs[queueKey])
            prefs[queueKey] = WearProtocol.encodeWatchActions(
                WatchActionReplayPlanner.enqueueUnique(current, action)
            )
        }
    }

    suspend fun remove(actionIds: Set<String>) {
        dataStore.edit { prefs ->
            val current = WearProtocol.decodeWatchActions(prefs[queueKey])
            prefs[queueKey] = WearProtocol.encodeWatchActions(
                WatchActionReplayPlanner.markDelivered(current, actionIds)
            )
        }
    }
}
