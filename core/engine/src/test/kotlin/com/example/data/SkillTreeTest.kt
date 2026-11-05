package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SkillTreeTest {

    @Test
    fun `every branch has nodes and a defined branch`() {
        assertTrue(SkillBranch.values().all { SkillTree.nodesFor(it).isNotEmpty() })
    }

    @Test
    fun `prerequisite chains are linked within each branch`() {
        for (branch in SkillBranch.values()) {
            val nodes = SkillTree.nodesFor(branch)
            for (i in nodes.indices) {
                val expectedPrereq = if (i == 0) null else nodes[i - 1].id
                assertEquals(expectedPrereq, SkillTree.prerequisiteOf(nodes[i]))
            }
        }
    }

    @Test
    fun `first node of each branch has no prerequisite`() {
        for (branch in SkillBranch.values()) {
            val first = SkillTree.nodesFor(branch).first()
            assertNull(SkillTree.prerequisiteOf(first))
        }
    }

    @Test
    fun `all node ids are unique`() {
        val ids = SkillTree.allNodes.map { it.id }
        assertEquals(ids.size, ids.distinct().size)
    }

    @Test
    fun `aggregated effects sum over unlocked nodes`() {
        // combat_dmg1 (+2 dmg), combat_def1 (+2 def), hacking_ram1 (+2 ram)
        val unlocked = setOf("combat_dmg1", "combat_def1", "hacking_ram1")
        val effects = SkillTree.combinedEffects(unlocked)
        assertEquals(2, effects.damage)
        assertEquals(2, effects.defense)
        assertEquals(2, effects.maxRam)
        assertEquals(0, effects.ramRecovery)
    }

    @Test
    fun `combined effects are zero for no unlocks`() {
        assertEquals(SkillTreeEffects(), SkillTree.combinedEffects(emptySet()))
    }
}
