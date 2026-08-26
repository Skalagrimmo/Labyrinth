package com.example.ui

import com.example.audio.CyberSoundEffectsManager
import com.example.data.CosmeticTheme
import com.example.data.LogType
import com.example.data.PerformanceBuff
import com.example.data.TerminalPromptStyle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

class CosmeticVaultManager(
    private val _uiState: MutableStateFlow<GameViewModel.GameUiState>,
    private val soundManager: CyberSoundEffectsManager,
    private val onLog: (String, LogType) -> Unit
) {
    private val uiState get() = _uiState.value

    var onRefreshPerspective: () -> Unit = {}

    private fun addLog(message: String, type: LogType = LogType.INFO) {
        onLog(message, type)
    }

    fun extractDataFragments(amount: Int, sourceDescription: String) {
        if (amount <= 0) return
        _uiState.update { s ->
            val updatedFrags = s.dataFragments + amount
            val updatedTotal = s.totalDataFragmentsExtracted + amount
            s.copy(
                dataFragments = updatedFrags,
                totalDataFragmentsExtracted = updatedTotal
            )
        }
        soundManager.playLootCollectionSound()
        addLog("DATA FRAGMENTS EXTRACTED: +$amount Fragments from $sourceDescription! [Total: ${uiState.dataFragments}]", LogType.SUCCESS)
    }

    fun unlockCosmeticTheme(themeId: String) {
        val theme = CosmeticTheme.fromId(themeId)
        val s = uiState
        if (s.unlockedThemes.contains(themeId)) {
            addLog("THEME ALREADY UNLOCKED: ${theme.title}", LogType.INFO)
            return
        }
        if (s.dataFragments < theme.cost) {
            soundManager.playHackingErrorSound()
            addLog("INSUFFICIENT DATA FRAGMENTS: Need ${theme.cost} Fragments (Have ${s.dataFragments})", LogType.ALERT)
            return
        }

        _uiState.update { stateNow ->
            stateNow.copy(
                dataFragments = stateNow.dataFragments - theme.cost,
                unlockedThemes = stateNow.unlockedThemes + themeId,
                activeCosmeticTheme = themeId
            )
        }
        soundManager.playHackingSuccessSound()
        addLog("COSMETIC THEME UNLOCKED & EQUIPPED: ${theme.title}!", LogType.SUCCESS)
    }

    fun setActiveTheme(themeId: String) {
        val s = uiState
        if (!s.unlockedThemes.contains(themeId)) {
            addLog("THEME LOCKED: Unlock first with Data Fragments.", LogType.ALERT)
            return
        }
        val theme = CosmeticTheme.fromId(themeId)
        _uiState.update { it.copy(activeCosmeticTheme = themeId) }
        soundManager.playTerminalKeyPressSound()
        addLog("TERMINAL COSMETIC THEME EQUIPPED: ${theme.title}", LogType.INFO)
    }

    fun unlockPromptStyle(promptId: String) {
        val prompt = TerminalPromptStyle.fromId(promptId)
        val s = uiState
        if (s.unlockedPrompts.contains(promptId)) {
            addLog("PROMPT ALREADY UNLOCKED: ${prompt.title}", LogType.INFO)
            return
        }
        if (s.dataFragments < prompt.cost) {
            soundManager.playHackingErrorSound()
            addLog("INSUFFICIENT DATA FRAGMENTS: Need ${prompt.cost} Fragments (Have ${s.dataFragments})", LogType.ALERT)
            return
        }

        _uiState.update { stateNow ->
            stateNow.copy(
                dataFragments = stateNow.dataFragments - prompt.cost,
                unlockedPrompts = stateNow.unlockedPrompts + promptId,
                activePromptStyle = promptId
            )
        }
        soundManager.playHackingSuccessSound()
        addLog("TERMINAL PROMPT UNLOCKED & EQUIPPED: ${prompt.title} (${prompt.promptString})!", LogType.SUCCESS)
    }

    fun setActivePromptStyle(promptId: String) {
        val s = uiState
        if (!s.unlockedPrompts.contains(promptId)) {
            addLog("PROMPT LOCKED: Unlock first with Data Fragments.", LogType.ALERT)
            return
        }
        val prompt = TerminalPromptStyle.fromId(promptId)
        _uiState.update { it.copy(activePromptStyle = promptId) }
        soundManager.playTerminalKeyPressSound()
        addLog("ACTIVE TERMINAL PROMPT SET: ${prompt.promptString}", LogType.INFO)
    }

    fun unlockPerformanceBuff(buffId: String) {
        val buff = PerformanceBuff.fromId(buffId) ?: return
        val s = uiState
        if (s.unlockedBuffs.contains(buffId)) {
            addLog("BUFF ALREADY UNLOCKED: ${buff.title}", LogType.INFO)
            return
        }
        if (s.dataFragments < buff.cost) {
            soundManager.playHackingErrorSound()
            addLog("INSUFFICIENT DATA FRAGMENTS: Need ${buff.cost} Fragments (Have ${s.dataFragments})", LogType.ALERT)
            return
        }

        _uiState.update { stateNow ->
            val updatedActive = stateNow.activeBuffs + buffId
            var updatedIntegrity = stateNow.integrity
            var updatedMaxIntegrity = stateNow.maxIntegrity

            if (buffId == "SHIELD_MATRIX") {
                updatedMaxIntegrity += 20
                updatedIntegrity += 20
            }

            stateNow.copy(
                dataFragments = stateNow.dataFragments - buff.cost,
                unlockedBuffs = stateNow.unlockedBuffs + buffId,
                activeBuffs = updatedActive,
                integrity = updatedIntegrity,
                maxIntegrity = updatedMaxIntegrity
            )
        }
        soundManager.playHackingSuccessSound()
        addLog("PERFORMANCE BUFF UNLOCKED & ACTIVATED: ${buff.title}! ${buff.description}", LogType.SUCCESS)
    }

    fun togglePerformanceBuff(buffId: String) {
        val s = uiState
        if (!s.unlockedBuffs.contains(buffId)) {
            addLog("BUFF LOCKED: Unlock first with Data Fragments.", LogType.ALERT)
            return
        }
        val buff = PerformanceBuff.fromId(buffId) ?: return
        val isCurrentlyActive = s.activeBuffs.contains(buffId)
        val newActiveSet = if (isCurrentlyActive) s.activeBuffs - buffId else s.activeBuffs + buffId

        _uiState.update { it.copy(activeBuffs = newActiveSet) }
        soundManager.playTerminalKeyPressSound()
        if (!isCurrentlyActive) {
            addLog("BUFF ACTIVATED: ${buff.title} (${buff.description})", LogType.SUCCESS)
        } else {
            addLog("BUFF DEACTIVATED: ${buff.title}", LogType.INFO)
        }
    }

    fun enterDataVaultScreen() {
        _uiState.update { it.copy(screen = ActiveScreen.DATA_FRAGMENTS_VAULT) }
        soundManager.playTerminalCommandSound()
        addLog("ACCESSING DATA FRAGMENT DECRYPTION VAULT...", LogType.SUCCESS)
    }

    fun exitDataVaultScreen() {
        if (uiState.runnerName.isEmpty()) {
            _uiState.update { it.copy(screen = ActiveScreen.START_MENU) }
        } else {
            _uiState.update { it.copy(screen = ActiveScreen.EXPLORATION) }
            onRefreshPerspective()
        }
    }

    fun runTerminalCommand(parts: List<String>, state: GameViewModel.GameUiState): Boolean {
        if (parts.isEmpty()) return false
        val cmd = parts[0].lowercase()
        if (cmd !in listOf("vault", "fragments", "frags", "datavault")) return false

        enterDataVaultScreen()
        return true
    }
}
