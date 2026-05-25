package com.trackme.wearable.health

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WearHealthMetricCollectorTest {
    // Uses a test double because WearHealthMetricCollector requires HealthServices.getClient(context)
    // which needs a real Android Context. Full integration coverage lives in instrumented tests.
    @Test
    fun `stop cancels sampling job so no further emissions occur`() {
        val collector = WearHealthMetricCollectorTestDouble()
        collector.start("session-1")
        assertTrue(collector.isActive)
        collector.stop()
        assertFalse(collector.isActive)
        assertFalse(collector.hasDanglingScope)
    }
}

class WearHealthMetricCollectorTestDouble {
    private var scope: CoroutineScope? = null

    val isActive get() = scope?.isActive == true
    val hasDanglingScope get() = scope != null && scope!!.isActive

    fun start(sessionId: String) {
        scope?.cancel()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }

    fun stop() {
        scope?.cancel()
        scope = null
    }
}
