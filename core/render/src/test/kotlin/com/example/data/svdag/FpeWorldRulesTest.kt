package com.example.data.svdag

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Host-JVM tests for the FPE World Rules / World State layer (Quantum.md §2, §4, §5, §7).
 */
class FpeWorldRulesTest {

    // ---- material -> formula binding (§5) ----

    @Test
    fun `empty voxel binds to void formula`() {
        assertEquals(FormulaId.VOID, VoxelType.EMPTY.defaultFormula())
        assertEquals(0f, VoxelType.EMPTY.defaultState().weight)
        assertTrue(VoxelType.EMPTY.defaultState().collapsed)
    }

    @Test
    fun `solid wall binds to solid formula and is stable`() {
        assertEquals(FormulaId.SOLID, VoxelType.SOLID_WALL.defaultFormula())
        val st = VoxelType.SOLID_WALL.defaultState()
        assertEquals(1f, st.weight)
        assertEquals(1f, st.probability)
        assertFalse(st.collapsed)
    }

    @Test
    fun `black ice binds to fire formula`() {
        assertEquals(FormulaId.FIRE, VoxelType.BLACK_ICE.defaultFormula())
    }

    // ---- SvdagWorldState overlay: fallthrough, override, dedup preservation (§7) ----

    @Test
    fun `world state falls through to dag material when no override`() {
        val dag = SparseVoxelDag(2)
        dag.setVoxel(1, 1, 1, VoxelType.SOLID_WALL)
        val ws = SvdagWorldState(dag)

        assertEquals(FormulaId.SOLID, ws.stateAt(1, 1, 1).formulaId)
        // untouched voxel stays EMPTY/void and collapsed
        assertTrue(ws.stateAt(0, 0, 0).collapsed)
    }

    @Test
    fun `override is isolated and does not mutate the dag node count`() {
        val dag = SparseVoxelDag(2)
        dag.setVoxel(1, 1, 1, VoxelType.SOLID_WALL)
        val nodesBefore = dag.getNodeCount()
        val ws = SvdagWorldState(dag)

        ws.override(1, 1, 1, VoxelState(weight = 0.2f, formulaId = FormulaId.DAMAGED, damageMask = 0x01))

        // world state reflects the override
        assertEquals(FormulaId.DAMAGED, ws.stateAt(1, 1, 1).formulaId)
        assertEquals(0.2f, ws.stateAt(1, 1, 1).weight, 1e-6f)
        // dag is untouched -> dedup/structure preserved (the core §7 property)
        assertEquals(nodesBefore, dag.getNodeCount())
        // underlying voxel type unchanged
        assertEquals(VoxelType.SOLID_WALL, dag.getVoxel(1, 1, 1))
    }

    @Test
    fun `clear override restores the procedural fallthrough`() {
        val dag = SparseVoxelDag(2)
        dag.setVoxel(3, 3, 3, VoxelType.BLACK_ICE)
        val ws = SvdagWorldState(dag)

        ws.override(3, 3, 3, VoxelState(weight = 0f, formulaId = FormulaId.FIRE))
        assertNotEquals(VoxelType.BLACK_ICE.defaultState(), ws.stateAt(3, 3, 3))

        ws.clearOverride(3, 3, 3)
        assertEquals(VoxelType.BLACK_ICE.defaultState(), ws.stateAt(3, 3, 3))
    }

    // ---- World Rules behaviour per formula (§5) ----

    @Test
    fun `solid formula is immutable under tick`() {
        val before = VoxelType.SOLID_WALL.defaultState()
        val (after, _) = WorldRules.tick(before, solidNeighbours = 4)
        assertEquals(before, after)
    }

    @Test
    fun `fire consumes weight over time`() {
        var st = VoxelType.BLACK_ICE.defaultState()
        repeat(10) { st = WorldRules.tick(st, solidNeighbours = 2).first }
        assertTrue(st.weight < 1f)
    }

    @Test
    fun `fire eventually stops emitting when weight collapses`() {
        var st = VoxelState(weight = 0.03f, formulaId = FormulaId.FIRE)
        val (_, impulse) = WorldRules.tick(st, solidNeighbours = 3)
        assertEquals(0f, impulse)
    }

    @Test
    fun `fluid emits impulse and loses a little weight`() {
        val st = VoxelState(weight = 1f, bleed = 0.5f, formulaId = FormulaId.FLUID)
        val (after, impulse) = WorldRules.tick(st, solidNeighbours = 1)
        assertTrue(impulse > 0f)
        assertTrue(after.weight < st.weight)
    }

    @Test
    fun `gas expands when there is room but not when sealed`() {
        val open = VoxelState(weight = 1f, formulaId = FormulaId.GAS)
        val sealed = VoxelState(weight = 1f, formulaId = FormulaId.GAS)
        // high solid neighbour count boxes the gas in
        assertEquals(0f, WorldRules.tick(sealed, solidNeighbours = 8).second)
        assertTrue(WorldRules.tick(open, solidNeighbours = 1).second > 0f)
    }

    @Test
    fun `hand-drawn line accumulates jitter`() {
        var st = VoxelType.DATA_CORE.defaultState()
        st = WorldRules.tick(st, solidNeighbours = 0).first
        assertTrue(st.jitter > 0f)
    }

    // ---- damage / integrity (§4, §8) ----

    @Test
    fun `applyImpulseDamage erodes one integrity bit per qualifying hit`() {
        var st = VoxelType.SOLID_WALL.defaultState()
        // weak impulse -> nothing
        assertEquals(st, WorldRules.applyImpulseDamage(st, impulse = 0.05f))
        // strong impulse -> one bit set
        st = WorldRules.applyImpulseDamage(st, impulse = 1f)
        assertEquals(1, st.damageMask)
        assertEquals(0.875f, st.integrity, 1e-6f)
    }

    @Test
    fun `fully damaged voxel stops accumulating damage`() {
        var st = VoxelState(formulaId = FormulaId.DAMAGED, damageMask = 0xFF)
        st = WorldRules.applyImpulseDamage(st, impulse = 1f)
        assertEquals(0xFF, st.damageMask)
        assertEquals(0f, st.integrity, 1e-6f)
    }

    @Test
    fun `integrity reflects number of cleared integrity bits`() {
        val st = VoxelState(formulaId = FormulaId.SOLID, damageMask = 0b0100)
        assertEquals(0.875f, st.integrity, 1e-6f)
        val half = VoxelState(formulaId = FormulaId.SOLID, damageMask = 0x0F)
        assertEquals(0.5f, half.integrity, 1e-6f)
    }

    // ---- player as local agent (§17) ----

    @Test
    fun `player transform lowers weight and durability but keeps the formula`() {
        val before = VoxelType.SOLID_WALL.defaultState()
        val after = WorldRules.playerTransform(before, strength = 0.6f)
        assertEquals(before.formulaId, after.formulaId) // rules never change
        assertTrue(after.weight < before.weight)
        assertTrue(after.damageMask > 0)
    }

    @Test
    fun `player transform lowers collapse probability so damaged matter flickers`() {
        val before = VoxelType.SOLID_WALL.defaultState()
        assertEquals(1f, before.probability)
        val after = WorldRules.playerTransform(before, strength = 0.8f)
        assertTrue("damage should drop probability below 1", after.probability < 1f)
        assertTrue("probability must stay in range", after.probability in 0f..1f)
    }

    @Test
    fun `formula table round-trips by id`() {
        for (f in FormulaId.entries) {
            assertEquals(f, FormulaId.fromId(f.id))
        }
    }
}
