package com.example.data.svdag

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Host-JVM tests for the live mutation path (§2/§7): World State overlay on a real DAG.
 */
class SvdagWorldStateTest {

    private fun solidDag(): SparseVoxelDag {
        val dag = SparseVoxelDag(2) // gridSize 4 -> coords 0..3
        dag.setVoxel(1, 1, 1, VoxelType.SOLID_WALL)
        return dag
    }

    @Test
    fun `reads fall through to the dag material default`() {
        val dag = solidDag()
        val world = SvdagWorldState(dag)

        val overSolid = world.stateAt(1, 1, 1)
        assertEquals(FormulaId.SOLID, overSolid.formulaId)
        assertEquals(1f, overSolid.weight)
        assertEquals(1f, overSolid.integrity)

        val empty = world.stateAt(0, 0, 0)
        assertTrue(empty.collapsed)
        assertEquals(0f, empty.weight)
    }

    @Test
    fun `player transform lowers weight and integrity in the overlay`() {
        val dag = solidDag()
        val world = SvdagWorldState(dag)

        world.update(1, 1, 1) { WorldRules.playerTransform(it, strength = 0.6f) }

        val damaged = world.stateAt(1, 1, 1)
        assertTrue("damaged weight should drop", damaged.weight < 1f)
        assertTrue("damaged integrity should drop", damaged.integrity < 1f)
        assertTrue("damage mask should gain bits", damaged.damageMask > 0)
        assertEquals(FormulaId.SOLID, damaged.formulaId) // rules immutable
    }

    @Test
    fun `overlay writes never touch the dag (dedup preserved)`() {
        val dag = solidDag()
        val world = SvdagWorldState(dag)

        world.update(1, 1, 1) { WorldRules.playerTransform(it, strength = 1f) }

        // The structure the overlay mutated must still read as SOLID_WALL from the DAG.
        assertEquals(VoxelType.SOLID_WALL, dag.getVoxel(1, 1, 1))
    }

    @Test
    fun `clear override restores the procedural default`() {
        val dag = solidDag()
        val world = SvdagWorldState(dag)

        world.update(1, 1, 1) { WorldRules.playerTransform(it, strength = 1f) }
        assertTrue(world.stateAt(1, 1, 1).integrity < 1f)

        world.clearOverride(1, 1, 1)
        val restored = world.stateAt(1, 1, 1)
        assertEquals(1f, restored.integrity)
        assertEquals(1f, restored.weight)
    }

    @Test
    fun `destroying a voxel drives it toward collapse`() {
        val dag = solidDag()
        val world = SvdagWorldState(dag)

        // Repeated player destruction (as the wired editor does on EMPTY) decays integrity.
        repeat(6) { world.update(1, 1, 1) { WorldRules.playerTransform(it, strength = 1f) } }
        assertTrue("repeated destruction should near-zero the integrity", world.stateAt(1, 1, 1).integrity <= 0.05f)
    }
}
