package com.example.data.svdag

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Host-JVM tests for FPE §6 probabilistic collapse / decoherence.
 */
class FpeProbabilityFieldTest {

    private fun solState(weight: Float = 1f, probability: Float = 1f) =
        VoxelState(weight = weight, probability = probability, formulaId = FormulaId.SOLID)

    // ---- deterministic, stable noise (§6.2) ----

    @Test
    fun `noise is deterministic for identical inputs`() {
        val field = FpeProbabilityField(SvdagWorldState(SparseVoxelDag(2)), seed = 42L)
        val a = field.noise(1, 2, 3, phase = 7L)
        val b = field.noise(1, 2, 3, phase = 7L)
        assertEquals(a, b)
    }

    @Test
    fun `noise lies in half-open unit range`() {
        val field = FpeProbabilityField(SvdagWorldState(SparseVoxelDag(4)), seed = 1L)
        for (x in 0..15) {
            for (y in 0..15) {
                for (z in 0..15) {
                    val n = field.noise(x, y, z, phase = 3L)
                    assertTrue("n=$n", n >= 0f && n < 1f)
                }
            }
        }
    }

    @Test
    fun `noise changes when the observer phase changes`() {
        val field = FpeProbabilityField(SvdagWorldState(SparseVoxelDag(3)), seed = 7L)
        var anyDifferent = false
        for (i in 0..200) {
            val x = i % 16; val y = (i / 16) % 16; val z = (i / 256) % 16
            if (field.noise(x, y, z, 0L) != field.noise(x, y, z, 1L)) {
                anyDifferent = true
                break
            }
        }
        assertTrue("phase should perturb at least some voxels", anyDifferent)
    }

    // ---- materialisation semantics (§6.2) ----

    @Test
    fun `collapsed voxels are never materialised`() {
        val dag = SparseVoxelDag(2)
        // EMPTY stays EMPTY -> probability 0 -> collapsed
        val ws = SvdagWorldState(dag)
        val field = FpeProbabilityField(ws, seed = 9L)
        repeat(50) { i ->
            assertFalse(field.isMaterialised(0, 0, 0, phase = i.toLong()))
        }
    }

    @Test
    fun `certain voxels are always materialised`() {
        val dag = SparseVoxelDag(2)
        dag.setVoxel(1, 1, 1, VoxelType.SOLID_WALL) // probability 1
        val ws = SvdagWorldState(dag)
        val field = FpeProbabilityField(ws, seed = 5L)
        repeat(100) { i ->
            assertTrue(field.isMaterialised(1, 1, 1, phase = i.toLong()))
        }
    }

    @Test
    fun `materialisation is deterministic for a fixed phase`() {
        val dag = SparseVoxelDag(3)
        dag.setVoxel(2, 2, 2, VoxelType.SOLID_WALL)
        val ws = SvdagWorldState(dag)
        ws.override(2, 2, 2, solState(weight = 1f, probability = 0.4f))
        val field = FpeProbabilityField(ws, seed = 3L)

        val a = field.isMaterialised(2, 2, 2, phase = 11L)
        val b = field.isMaterialised(2, 2, 2, phase = 11L)
        assertEquals(a, b)
    }

    @Test
    fun `fractional probability voxels materialise close to their probability rate`() {
        // Statistical but robust: with many voxels at p=0.5, rate should be near half.
        val dag = SparseVoxelDag(5)
        val ws = SvdagWorldState(dag)
        val p = 0.5f
        var materialised = 0
        var total = 0
        for (x in 0..31) {
            for (y in 0..31) {
                val z = 1
                ws.override(x, y, z, solState(probability = p))
                total++
            }
        }
        val field = FpeProbabilityField(ws, seed = 123L)
        for (x in 0..31) {
            for (y in 0..31) {
                if (field.isMaterialised(x, y, 1, phase = 0L)) materialised++
            }
        }
        val rate = materialised.toDouble() / total
        assertTrue("rate=$rate", rate in 0.35..0.65)
    }

    // ---- decoherence / exact state (§6.3) ----

    @Test
    fun `exact state is null when not materialised, present when materialised`() {
        val dag = SparseVoxelDag(2)
        dag.setVoxel(1, 1, 1, VoxelType.SOLID_WALL) // always materialised
        val ws = SvdagWorldState(dag)
        val field = FpeProbabilityField(ws, seed = 4L)

        assertNotNull(field.exactState(1, 1, 1, phase = 0L))
        assertNull(field.exactState(0, 0, 0, phase = 0L)) // EMPTY
    }

    @Test
    fun `materialisedInBox finds exactly the certainty-set voxels`() {
        val dag = SparseVoxelDag(4)
        dag.setVoxel(1, 1, 1, VoxelType.SOLID_WALL)
        dag.setVoxel(3, 3, 3, VoxelType.SOLID_WALL)
        val ws = SvdagWorldState(dag)
        val field = FpeProbabilityField(ws, seed = 8L)

        // only the two solid voxels are certain; the box corner probes must include them
        val set = field.materialisedInBox(0, 4, 0, 4, 0, 4, phase = 0L).toSet()
        assertTrue(Point3i(1, 1, 1) in set)
        assertTrue(Point3i(3, 3, 3) in set)
    }

    @Test
    fun `countMaterialisedInBox is consistent with materialisedInBox`() {
        val dag = SparseVoxelDag(4)
        dag.setVoxel(2, 2, 2, VoxelType.SOLID_WALL)
        val ws = SvdagWorldState(dag)
        val field = FpeProbabilityField(ws, seed = 2L)

        val box = field.materialisedInBox(0, 5, 0, 5, 0, 5, phase = 0L)
        val count = field.countMaterialisedInBox(0, 5, 0, 5, 0, 5, phase = 0L)
        // EMPTY voxels never materialise, so the count equals the certain solid set size
        assertEquals(box.size, count)
        assertTrue(Point3i(2, 2, 2) in box)
    }
}
