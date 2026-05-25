package com.trackme.wearable.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

class MediaPageTest {

    // ---- volumeLabel --------------------------------------------------------

    @Test
    fun `volumeLabel returns formatted string`() {
        assertEquals("Vol: 8/15", volumeLabel(current = 8, max = 15))
    }

    @Test
    fun `volumeLabel zero current returns Vol 0 slash max`() {
        assertEquals("Vol: 0/15", volumeLabel(current = 0, max = 15))
    }

    @Test
    fun `volumeLabel max equals zero returns Vol 0 slash 0`() {
        assertEquals("Vol: 0/0", volumeLabel(current = 0, max = 0))
    }

    @Test
    fun `volumeLabel current equals max returns full label`() {
        assertEquals("Vol: 15/15", volumeLabel(current = 15, max = 15))
    }

    @Test
    fun `volumeLabel single digit values`() {
        assertEquals("Vol: 3/5", volumeLabel(current = 3, max = 5))
    }
}
