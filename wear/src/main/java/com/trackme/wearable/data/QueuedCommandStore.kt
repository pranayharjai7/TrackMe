package com.trackme.wearable.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.trackme.wearbridge.CommandReplayPlanner
import com.trackme.wearbridge.QueuedCommand
import com.trackme.wearbridge.WatchCommandPayload
import com.trackme.wearbridge.WearProtocol
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.commandQueueDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "trackme_wear_command_queue",
)

class QueuedCommandStore(context: Context) {
    private val dataStore = context.commandQueueDataStore
    private val queueKey = stringPreferencesKey("queued_commands_json")

    val queuedCount: Flow<Int> = dataStore.data.map { prefs ->
        WearProtocol.decodeQueuedCommands(prefs[queueKey]).size
    }

    suspend fun readQueue(): List<QueuedCommand> =
        WearProtocol.decodeQueuedCommands(dataStore.data.first()[queueKey])

    suspend fun enqueue(command: WatchCommandPayload, now: Long = System.currentTimeMillis()) {
        dataStore.edit { prefs ->
            val queue = WearProtocol.decodeQueuedCommands(prefs[queueKey])
            prefs[queueKey] = WearProtocol.encodeQueuedCommands(
                CommandReplayPlanner.enqueueUnique(queue, command, now)
            )
        }
    }

    suspend fun markDelivered(commandIds: Set<String>) {
        dataStore.edit { prefs ->
            val queue = WearProtocol.decodeQueuedCommands(prefs[queueKey])
            prefs[queueKey] = WearProtocol.encodeQueuedCommands(
                CommandReplayPlanner.markDelivered(queue, commandIds)
            )
        }
    }

    suspend fun markAttempted(commandIds: Set<String>) {
        dataStore.edit { prefs ->
            val queue = WearProtocol.decodeQueuedCommands(prefs[queueKey])
            prefs[queueKey] = WearProtocol.encodeQueuedCommands(
                CommandReplayPlanner.markAttempted(queue, commandIds)
            )
        }
    }
}
