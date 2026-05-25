package com.trackme.wearable.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

class HrZonesPageTest {

    // ---- hrToZone ------------------------------------------------------------

    @Test
    fun `hrToZone 94 returns zone 1`() {
        assertEquals(1, hrToZone(94))
    }

    @Test
    fun `hrToZone 95 returns zone 2`() {
        assertEquals(2, hrToZone(95))
    }

    @Test
    fun `hrToZone 113 returns zone 2`() {
        assertEquals(2, hrToZone(113))
    }

    @Test
    fun `hrToZone 114 returns zone 3`() {
        assertEquals(3, hrToZone(114))
    }

    @Test
    fun `hrToZone 133 returns zone 4`() {
        assertEquals(4, hrToZone(133))
    }

    @Test
    fun `hrToZone 152 returns zone 5`() {
        assertEquals(5, hrToZone(152))
    }

    @Test
    fun `hrToZone 0 returns zone 1`() {
        assertEquals(1, hrToZone(0))
    }

    @Test
    fun `hrToZone 200 returns zone 5`() {
        assertEquals(5, hrToZone(200))
    }

    // ---- zoneName ------------------------------------------------------------

    @Test
    fun `zoneName 1 returns Recovery`() {
        assertEquals("Zone 1 · Recovery", zoneName(1))
    }

    @Test
    fun `zoneName 2 returns Aerobic`() {
        assertEquals("Zone 2 · Aerobic", zoneName(2))
    }

    @Test
    fun `zoneName 3 returns Tempo`() {
        assertEquals("Zone 3 · Tempo", zoneName(3))
    }

    @Test
    fun `zoneName 4 returns Threshold`() {
        assertEquals("Zone 4 · Threshold", zoneName(4))
    }

    @Test
    fun `zoneName 5 returns Max`() {
        assertEquals("Zone 5 · Max", zoneName(5))
    }

    @Test
    fun `zoneName unknown returns Zone question mark`() {
        assertEquals("Zone ?", zoneName(99))
    }
}
