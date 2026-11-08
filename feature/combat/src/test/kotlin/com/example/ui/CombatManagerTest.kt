package com.example.ui

import com.example.audio.SilentAudioPlayer
import com.example.core.model.StatusEffectType
import com.example.data.GameState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * d8 slice 6: KOTOR combat openings - the hostile's starting status is
 * decided by how the fight began. A shadow strike freezes it for a
 * surprise round; a trace lock pre-loads its combat protocols; a
 * standard engagement opens flat.
 */
class CombatManagerTest {

    private val logs = mutableListOf<String>()

    private fun newCombatManager(): Pair<CombatManager, MutableStateFlow<GameUiState>> {
        val flow = MutableStateFlow(GameUiState())
        val manager = CombatManager(
            _uiState = flow,
            soundManager = SilentAudioPlayer,
            scope = CoroutineScope(Dispatchers.Unconfined),
            onLog = { message, _ -> logs.add(message) },
            onSave = {},
            onAddExperience = {},
            onVictoryCleanup = {},
            onGameOver = {}
        )
        return manager to flow
    }

    @Test
    fun shadowStrikeOpeningFreezesTheHostileForASurpriseRound() {
        val (manager, flow) = newCombatManager()

        manager.triggerCombat(3, 1, CombatOpening.AMBUSHED)

        val enemy = flow.value.activeEnemy
        assertTrue("enemy not spawned", enemy != null)
        assertEquals(GameState.COMBAT_START, flow.value.gameState)
        val stun = enemy!!.statusEffects.filter { it.type == StatusEffectType.STUNNED && it.sourceName == "Shadow Strike" }
        assertEquals(1, stun.size)
        assertEquals(1, stun.first().turnsRemaining)
        assertTrue(
            flow.value.enemyStatusEffects.any { it.type == StatusEffectType.STUNNED && it.sourceName == "Shadow Strike" }
        )
        assertTrue(logs.any { it.contains("SHADOW STRIKE") })
        assertFalse(logs.any { it.contains("TRACE LOCK") })
    }

    @Test
    fun traceLockOpeningPreLoadsHostileProtocols() {
        val (manager, flow) = newCombatManager()

        manager.triggerCombat(5, 2, CombatOpening.CAUGHT)

        val enemy = flow.value.activeEnemy
        assertTrue("enemy not spawned", enemy != null)
        val buff = enemy!!.statusEffects.filter { it.type == StatusEffectType.BUFFED && it.sourceName == "ICE Trace Lock" }
        assertEquals(1, buff.size)
        assertEquals(2, buff.first().turnsRemaining)
        assertTrue(logs.any { it.contains("TRACE LOCK") })
        assertFalse(logs.any { it.contains("SHADOW STRIKE") })
    }

    @Test
    fun standardOpeningStartsTheFightFlat() {
        val (manager, flow) = newCombatManager()

        manager.triggerCombat(2, 2)

        val enemy = flow.value.activeEnemy
        assertTrue("enemy not spawned", enemy != null)
        assertEquals(GameState.COMBAT_START, flow.value.gameState)
        assertFalse(
            enemy!!.statusEffects.any { it.sourceName == "Shadow Strike" || it.sourceName == "ICE Trace Lock" }
        )
        assertFalse(
            flow.value.enemyStatusEffects.any { it.sourceName == "Shadow Strike" || it.sourceName == "ICE Trace Lock" }
        )
        assertFalse(logs.any { it.contains("SHADOW STRIKE") || it.contains("TRACE LOCK") })
    }
}
