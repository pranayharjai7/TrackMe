package com.trackme.wearable.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

class ReadinessFormatTest {

    @Test
    fun `readinessLabel maps score bands`() {
        assertEquals("optimal", readinessLabel(90))
        assertEquals("good", readinessLabel(70))
        assertEquals("moderate", readinessLabel(50))
        assertEquals("recover", readinessLabel(30))
    }
}
