package com.example.ui

import com.example.audio.SilentAudioPlayer
import com.example.core.model.LogType
import com.example.data.InventoryItemRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-JVM characterization tests for the inventory's field acoustics
 * (d8 slice 8): compiling a utility and rummaging a cache surface as
 * callbacks, so the app layer can wire them into the stealth world clock.
 */
class InventoryManagerTest {

    private val logs = mutableListOf<Pair<String, LogType>>()
    private val compiled = mutableListOf<String>()
    private var scavenges = 0

    private fun newManager(
        inventory: List<String>,
        screen: ActiveScreen = ActiveScreen.EXPLORATION
    ): InventoryManager =
        InventoryManager(
            _uiState = MutableStateFlow(
                GameUiState(screen = screen, inventory = inventory, level = 1)
            ),
            soundManager = SilentAudioPlayer,
            inventoryRecorder = InventoryItemRecorder { _, _, _, _, _ -> },
            scope = CoroutineScope(Dispatchers.Unconfined),
            onLog = { message, type -> logs.add(message to type) },
            onAddExperience = {},
            onSave = {},
            onItemCompiled = { itemName -> compiled.add(itemName) },
            onCacheScavenged = { scavenges++ }
        )

    @Test
    fun compilingAUtilitySignalsTheItemFootprint() {
        val manager = newManager(inventory = listOf("NanoMed.sys"))
        manager.useInventoryItem("NanoMed.sys")
        assertEquals(listOf("NanoMed.sys"), compiled)
        assertTrue(logs.any { it.first.contains("COMPILED") })
    }

    @Test
    fun equippingStaysAcousticallySilent() {
        val manager = newManager(inventory = listOf("CyberBlade.exe"))
        manager.useInventoryItem("CyberBlade.exe")
        assertTrue(compiled.isEmpty())
        assertTrue(logs.any { it.first.contains("WEAPON EQUIPPED") })
    }

    @Test
    fun missingItemsCompileNothing() {
        val manager = newManager(inventory = emptyList())
        manager.useInventoryItem("NanoMed.sys")
        assertTrue(compiled.isEmpty())
    }

    @Test
    fun scavengingRattlesTheCache() {
        val manager = newManager(inventory = emptyList())
        manager.scavengeCurrentCell()
        assertEquals(1, scavenges)
        assertTrue(logs.any { it.first.contains("SCAVENGE SUCCESSFUL") })
    }

    @Test
    fun scavengeOutsideTheGridStaysSilent() {
        val manager = newManager(inventory = emptyList(), screen = ActiveScreen.UPGRADE_STORE)
        manager.scavengeCurrentCell()
        assertEquals(0, scavenges)
        assertTrue(logs.any { it.first.contains("SCAVENGE ERROR") })
    }
}
