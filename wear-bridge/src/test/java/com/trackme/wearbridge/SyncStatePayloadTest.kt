package com.trackme.wearbridge

import org.junit.Assert.assertEquals
import org.junit.Test

class SyncStatePayloadTest {

    @Test
    fun wearOsVersion_roundTrips() {
        val payload = SyncStatePayload(
            sessionId = "s1",
            lastEventIndex = 4L,
            watchModel = "Galaxy Watch4 Classic",
            wearOsVersion = "13",
            batteryPercent = 88,
            timestamp = 100L,
        )
        val decoded = WearProtocol.decodeSyncState(WearProtocol.encodeSyncState(payload))
        assertEquals("13", decoded.wearOsVersion)
        assertEquals("Galaxy Watch4 Classic", decoded.watchModel)
    }
}
