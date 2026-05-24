package com.trackme.wearable.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.trackme.wearbridge.HealthMetricSamplePayload
import com.trackme.wearbridge.WearProtocol
import kotlinx.coroutines.flow.first

private val Context.healthMetricDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "trackme_wear_health_metrics",
)

class HealthMetricSampleStore(context: Context) {
    private val dataStore = context.healthMetricDataStore
    private val samplesKey = stringPreferencesKey("queued_health_samples_json")

    suspend fun append(samples: List<HealthMetricSamplePayload>) {
        if (samples.isEmpty()) return
        dataStore.edit { prefs ->
            val current = WearProtocol.decodeHealthSamples(prefs[samplesKey])
            prefs[samplesKey] = WearProtocol.encodeHealthSamples(current + samples)
        }
    }

    suspend fun drain(): List<HealthMetricSamplePayload> {
        val current = WearProtocol.decodeHealthSamples(dataStore.data.first()[samplesKey])
        if (current.isEmpty()) return emptyList()
        dataStore.edit { prefs ->
            prefs[samplesKey] = WearProtocol.encodeHealthSamples(emptyList())
        }
        return current
    }

    suspend fun prepend(samples: List<HealthMetricSamplePayload>) {
        if (samples.isEmpty()) return
        dataStore.edit { prefs ->
            val current = WearProtocol.decodeHealthSamples(prefs[samplesKey])
            prefs[samplesKey] = WearProtocol.encodeHealthSamples(samples + current)
        }
    }
}
