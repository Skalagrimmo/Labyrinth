package com.example.data.svdag

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Host-JVM tests for FPE §15 eclectic render-mode selection.
 */
class FpeRenderModeTest {

    private fun state(
        weight: Float = 1f,
        probability: Float = 1f,
        formulaId: FormulaId = FormulaId.SOLID,
        damageMask: Int = 0
    ) = VoxelState(weight = weight, probability = probability, formulaId = formulaId, damageMask = damageMask)

    @Test
    fun `collapsed and empty matter render as void`() {
        assertEquals(FpeRenderMode.VOID, FpeRenderStylist.modeFor(VoxelType.EMPTY.defaultState()))
        assertEquals(FpeRenderMode.VOID, FpeRenderStylist.modeFor(state(weight = 0f)))
        assertEquals(FpeRenderMode.VOID, FpeRenderStylist.modeFor(state(weight = 1f, probability = 0f)))
    }

    @Test
    fun `pristine solid matter renders solid`() {
        assertEquals(FpeRenderMode.SOLID, FpeRenderStylist.modeFor(VoxelType.SOLID_WALL.defaultState()))
        assertEquals(FpeRenderMode.SOLID, FpeRenderStylist.modeFor(state(weight = 1f)))
    }

    @Test
    fun `heavy structural damage decays to ascii`() {
        // 7 of 8 integrity bits gone -> integrity 0.125 < decay threshold
        val damaged = state(weight = 1f, damageMask = 0b1111_1110)
        assertEquals(FpeRenderMode.ASCII_DECAY, FpeRenderStylist.modeFor(damaged))
    }

    @Test
    fun `moderate damage lowers solid to point cloud`() {
        // weight high but integrity halved (4 bits) -> too damaged for SOLID, not yet decay
        val st = state(weight = 1f, damageMask = 0b1111_0000)
        assertEquals(FpeRenderMode.POINT_CLOUD, FpeRenderStylist.modeFor(st))
    }

    @Test
    fun `near-total damage decays even with full weight`() {
        // damageMask 0xFF -> integrity 0 -> ASCII_DECAY regardless of weight
        val st = state(weight = 1f, damageMask = 0xFF)
        assertEquals(FpeRenderMode.ASCII_DECAY, FpeRenderStylist.modeFor(st))
    }

    @Test
    fun `low weight decays to ascii`() {
        val st = state(weight = 0.1f)
        assertEquals(FpeRenderMode.ASCII_DECAY, FpeRenderStylist.modeFor(st))
    }

    @Test
    fun `unstable middle weight is point cloud`() {
        val st = state(weight = 0.45f, damageMask = 0b1000) // integrity = 0.875
        assertEquals(FpeRenderMode.POINT_CLOUD, FpeRenderStylist.modeFor(st))
    }

    @Test
    fun `mode choice is a pure function of state - never random`() {
        val st = state(weight = 0.45f, damageMask = 0b1000)
        val first = FpeRenderStylist.modeFor(st)
        repeat(50) { assertEquals(first, FpeRenderStylist.modeFor(st)) }
    }

    @Test
    fun `weight exactly on the solid threshold stays solid when intact`() {
        val st = state(weight = FpeRenderStylist.SOLID_WEIGHT)
        assertEquals(FpeRenderMode.SOLID, FpeRenderStylist.modeFor(st))
    }

    // ---- opacity: probabilistic shaping of the chosen language (§15/§6) ----

    @Test
    fun `opacity is zero when not materialised or collapsed`() {
        val st = state(weight = 1f)
        assertEquals(0f, FpeRenderStylist.opacityFor(st, materialised = false))
        assertEquals(0f, FpeRenderStylist.opacityFor(VoxelType.EMPTY.defaultState(), materialised = true))
    }

    @Test
    fun `opacity scales with weight when materialised`() {
        val heavy = state(weight = 1f)
        val light = state(weight = 0.5f)
        val oHeavy = FpeRenderStylist.opacityFor(heavy, materialised = true)
        val oLight = FpeRenderStylist.opacityFor(light, materialised = true)
        assertTrue(oHeavy > oLight)
        assertTrue(oHeavy in 0f..1f)
    }

    @Test
    fun `decay tone is true only for the ascii language`() {
        assertTrue(FpeRenderStylist.isDecayTone(FpeRenderMode.ASCII_DECAY))
        assertFalse(FpeRenderStylist.isDecayTone(FpeRenderMode.SOLID))
        assertFalse(FpeRenderStylist.isDecayTone(FpeRenderMode.POINT_CLOUD))
        assertFalse(FpeRenderStylist.isDecayTone(FpeRenderMode.VOID))
    }
}
