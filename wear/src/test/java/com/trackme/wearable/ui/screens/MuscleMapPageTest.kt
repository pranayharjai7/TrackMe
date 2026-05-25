package com.trackme.wearable.ui.screens

import com.trackme.wearable.designsystem.WearColors
import org.junit.Assert.assertEquals
import org.junit.Test

class MuscleMapPageTest {

    // ---- muscleColor --------------------------------------------------------

    @Test
    fun `muscleColor CHEST returns Signal`() {
        assertEquals(WearColors.Signal, muscleColor("CHEST"))
    }

    @Test
    fun `muscleColor TRICEPS returns Signal`() {
        assertEquals(WearColors.Signal, muscleColor("TRICEPS"))
    }

    @Test
    fun `muscleColor BACK returns Rest`() {
        assertEquals(WearColors.Rest, muscleColor("BACK"))
    }

    @Test
    fun `muscleColor LEGS returns Active`() {
        assertEquals(WearColors.Active, muscleColor("LEGS"))
    }

    @Test
    fun `muscleColor QUADS returns Active`() {
        assertEquals(WearColors.Active, muscleColor("QUADS"))
    }

    @Test
    fun `muscleColor CORE returns Summary`() {
        assertEquals(WearColors.Summary, muscleColor("CORE"))
    }

    @Test
    fun `muscleColor CARDIO returns Warning`() {
        assertEquals(WearColors.Warning, muscleColor("CARDIO"))
    }

    @Test
    fun `muscleColor null returns TextSecondary`() {
        assertEquals(WearColors.TextSecondary, muscleColor(null))
    }

    @Test
    fun `muscleColor unknown string returns TextSecondary`() {
        assertEquals(WearColors.TextSecondary, muscleColor("UNKNOWN"))
    }

    @Test
    fun `muscleColor lowercase chest is case insensitive`() {
        assertEquals(WearColors.Signal, muscleColor("chest"))
    }

    @Test
    fun `muscleColor for SHOULDERS returns Signal`() {
        assertEquals(WearColors.Signal, muscleColor("SHOULDERS"))
    }

    @Test
    fun `muscleColor for TRAPS returns Rest`() {
        assertEquals(WearColors.Rest, muscleColor("TRAPS"))
    }
}
