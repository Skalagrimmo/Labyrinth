package com.example.ui

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.random.Random
import java.util.concurrent.ConcurrentHashMap

class GameViewModel(application: Application) : AndroidViewModel(application) {

    private val soundManager = com.example.audio.CyberSoundEffectsManager.getInstance(application)

    private val _bgmEnabled = mutableStateOf(!soundManager.isMuted())
    val bgmEnabled: Boolean get() = _bgmEnabled.value
    private val _bgmVolume = mutableStateOf(soundManager.getBgmVolume())
    val bgmVolume: Float get() = _bgmVolume.value
    private val _sfxEnabled = mutableStateOf(!soundManager.isMuted())
    val sfxEnabled: Boolean get() = _sfxEnabled.value
    private val _vibrationEnabled = mutableStateOf(com.example.audio.CyberVibrationManager.isEnabled())
    val vibrationEnabled: Boolean get() = _vibrationEnabled.value

    private val database = GameDatabase.getDatabase(application)
    private val repository = GameRepository(
        database.runRecordDao(),
        database.characterProfileDao(),
        database.gameSaveProgressDao(),
        database.inventoryItemDao()
    )

    val runRecords: StateFlow<List<RunRecord>> = repository.allRunRecords
        .stateIn(scope = viewModelScope, started = SharingStarted.WhileSubscribed(5000), initialValue = emptyList())

    val characterProfiles: StateFlow<List<CharacterProfileEntity>> = repository.allCharacterProfiles
        .stateIn(scope = viewModelScope, started = SharingStarted.WhileSubscribed(5000), initialValue = emptyList())

    val savedGameProgress: StateFlow<GameSaveProgressEntity?> = repository.currentSaveProgress
        .stateIn(scope = viewModelScope, started = SharingStarted.WhileSubscribed(5000), initialValue = null)

    val roomInventoryItems: StateFlow<List<InventoryItemEntity>> = repository.currentInventoryItems
        .stateIn(scope = viewModelScope, started = SharingStarted.WhileSubscribed(5000), initialValue = emptyList())

    data class GameUiState(
        val screen: ActiveScreen = ActiveScreen.START_MENU,
        val runnerName: String = "",
        val runnerClass: NetrunnerClass = NetrunnerClass.CODE_SLASHER,
        val maxIntegrity: Int = 100,
        val integrity: Int = 100,
        val playerMaxShield: Int = 50,
        val playerShield: Int = 10,
        val maxRam: Int = 12,
        val ram: Int = 12,
        val ramRecoveryRate: Int = 2,
        val credits: Int = 100,
        val damageBonus: Int = 0,
        val defenseBonus: Int = 0,
        val characterLevel: Int = 1,
        val characterXp: Int = 0,
        val xpToNextLevel: Int = 100,
        val gridX: Int = 1,
        val gridY: Int = 1,
        val direction: Direction = Direction.EAST,
        val level: Int = 1,
        val maze: Array<Array<CellType>> = emptyArray(),
        val perspectiveText: String = "",
        val exploredCells: Set<Pair<Int, Int>> = emptySet(),
        val activeWeather: CyberWeather = CyberWeather.CLEAR,
        val weatherTurnsLeft: Int = 0,
        val stepsSinceLastEvent: Int = 0,
        val nextEventSteps: Int = 30,
        val predictedWeather: CyberWeather? = null,
        val originalMaze: Array<Array<CellType>>? = null,
        val installedCyberware: List<Cyberware> = emptyList(),
        val installedPrograms: List<Program> = emptyList(),
        val inventory: List<String> = listOf("NanoMed.sys", "RAMBoost.exe"),
        val isScanActive: Boolean = false,
        val scanTurnsLeft: Int = 0,
        val scannedEnemies: Set<Pair<Int, Int>> = emptySet(),
        val scannedLoot: Set<Pair<Int, Int>> = emptySet(),
        val scanTimestamp: Long = 0L,
        val activeEnemy: Enemy? = null,
        val enemyCombatAction: String = "",
        val gameState: GameState = GameState.EXPLORATION,
        val activePuzzle: HackingPuzzle? = null,
        val targetNodeX: Int = -1,
        val targetNodeY: Int = -1,
        val logFeed: List<LogMessage> = emptyList(),
        val nodesHackedCount: Int = 0,
        val totalCreditsEarned: Int = 100,
        val runOutcome: String = "",
        val combatTurn: CombatTurn = CombatTurn.PLAYER,
        val combatRound: Int = 1,
        val turnPhase: TurnPhase = TurnPhase.PLAYER_INPUT,
        val playerActionHistory: List<TurnActionRecord> = emptyList(),
        val enemyTurnHistory: List<TurnActionRecord> = emptyList(),
        val allTurnActions: List<TurnActionRecord> = emptyList(),
        val lastPlayerActionRecord: TurnActionRecord? = null,
        val lastEnemyActionRecord: TurnActionRecord? = null,
        val totalPlayerActionsCount: Int = 0,
        val totalEnemyTurnsCount: Int = 0,
        val activeCombatHack: CombatHackingPatternState? = null,
        val combatFlashEnemy: Boolean = false,
        val combatFlashPlayer: Boolean = false,
        val combatScreenShake: Boolean = false,
        val playerDamagePopup: String? = null,
        val enemyDamagePopup: String? = null,
        val showShieldEffect: Boolean = false,
        val showCombatBanner: String? = null,
        val isCombatInputEnabled: Boolean = true,
        val enemyAttackCharge: Float = 0f,
        val activeFirewallTimeLeft: Int = 0,
        val playerStatusEffects: List<ActiveStatusEffect> = emptyList(),
        val enemyStatusEffects: List<ActiveStatusEffect> = emptyList(),
        val defendCooldown: Int = 0,
        val attackCooldown: Int = 0,
        val programCooldowns: Map<String, Int> = emptyMap(),
        val currentZone: Zone = Zone.BUILDING,
        val buildingFloor: Int = 1,
        val collectorsLevel: Int = 1,
        val cityDistrictIndex: Int = 0,
        val hasElevatorKeycard: Boolean = false,
        val fadeAlpha: Float = 0f,
        val buildingFloors: Map<Int, Array<Array<CellType>>> = emptyMap(),
        val buildingExplored: Map<Int, Set<Pair<Int, Int>>> = emptyMap(),
        val collectorsLevels: Map<Int, Array<Array<CellType>>> = emptyMap(),
        val collectorsExplored: Map<Int, Set<Pair<Int, Int>>> = emptyMap(),
        val cityDistricts: Map<Int, Array<Array<CellType>>> = emptyMap(),
        val cityExplored: Map<Int, Set<Pair<Int, Int>>> = emptyMap(),
        val selectedCombatStyle: String = "Strike",
        val equippedWeaponName: String = "Sparksteel Dagger",
        val equippedArmorName: String = "Basic Firewall Mesh",
        val equippedUtilityName: String = "None",
        val equippedWeaponItem: GameItem? = null,
        val equippedArmorItem: GameItem? = null,
        val equippedUtilityItem: GameItem? = null,
        val selectedInventoryCategoryFilter: InventoryCategory? = null,
        val inventorySortOption: InventorySortOption = InventorySortOption.CATEGORY,
        val weaponSwingProgress: Float = 0f,
        val weaponSwingType: String = "Strike",
        val selectedStartingImplant: CyberwareImplant = CyberwareImplantRegistry.STARTER_IMPLANTS[0],
        val installedImplants: Map<ImplantBodySlot, CyberwareImplant?> = emptyMap(),
        val storedImplants: List<CyberwareImplant> = emptyList(),
        val showCyberwareInventoryOverlay: Boolean = false,
        val selectedOverlayTab: String = "EQUIPPED",
        val selectedOverlaySlotFilter: ImplantBodySlot? = null,
        val hasUsedEmergencyRebootThisRun: Boolean = false,
        val kineticShieldActiveThisCombat: Boolean = true,
        val naniteStepCounter: Int = 0,
        val svdagWorld: com.example.data.svdag.SparseVoxelDag? = null,
        val svdagWorldState: com.example.data.svdag.SvdagWorldState? = null,
        val useFpeInFppView: Boolean = false,
        val svdagStats: com.example.data.svdag.SvdagStats? = null,
        val svdagScaleDepth: Int = 7,
        val svdagLodLevel: Int = 0,
        val svdagScanSummary: com.example.data.svdag.SvdagScanSummary? = null,
        val svdagRippleState: com.example.data.svdag.SvdagRippleState? = null,
        val svdagIceEntities: List<com.example.data.svdag.IceEntity> = emptyList(),
        val svdagPlayerPos: Triple<Int, Int, Int> = Triple(2, 2, 3),
        val svdagPlayerHideStatus: com.example.data.svdag.PlayerHideStatus? = null,
        val svdagAutoPatrolActive: Boolean = false,
        val dataFragments: Int = 0,
        val totalDataFragmentsExtracted: Int = 0,
        val unlockedThemes: Set<String> = setOf("DEFAULT_CYBER"),
        val activeCosmeticTheme: String = "DEFAULT_CYBER",
        val unlockedPrompts: Set<String> = setOf("DEFAULT"),
        val activePromptStyle: String = "DEFAULT",
        val unlockedBuffs: Set<String> = emptySet(),
        val activeBuffs: Set<String> = emptySet(),
        val currentMultiFloorLevel: MultiFloorGridLevel? = null,
        val activeFloorIndex: Int = 0,
        val levelSeed: Long = 0L,
        val skillPoints: Int = 0,
        val unlockedSkills: Set<String> = emptySet(),
        val tutorialStep: Int = 0,
        val tutorialActive: Boolean = false,
        val tutorialSeen: Boolean = false,
        val selectedMutationTitle: String = "",
        val environmentalEventsEncountered: Int = 0
    )

    private val _uiState = MutableStateFlow(GameUiState())
    val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()

    private val underlyingCellTypes = ConcurrentHashMap<String, CellType>()

    private val combatManager = CombatManager(
        _uiState = _uiState,
        soundManager = soundManager,
        scope = viewModelScope,
        onLog = ::addLog,
        onSave = ::saveGame,
        onAddExperience = ::addExperience,
        onVictoryCleanup = ::handleCombatVictoryCleanup,
        onGameOver = ::handleGameOver,
        onBossZoneTransition = { zone, floor -> explorationManager.loadOrCreateLevel(zone, floor) }
    )

    private val explorationManager = ExplorationManager(
        _uiState = _uiState,
        soundManager = soundManager,
        scope = viewModelScope,
        onLog = ::addLog,
        onTriggerCombat = ::triggerCombat,
        underlyingCellTypes = underlyingCellTypes,
        onHandleGameOver = ::handleGameOver,
        onStartHackingPuzzle = ::startHackingPuzzle,
        onAddExperience = ::addExperience
    )

    private val inventoryManager = InventoryManager(
        _uiState = _uiState,
        soundManager = soundManager,
        repository = repository,
        scope = viewModelScope,
        onLog = ::addLog,
        onAddExperience = ::addExperience,
        onSave = ::saveGame,
        onRecordPlayerAction = { _: CombatActionType, _: String, _: Int, _: String? -> },
        onCombatAction = { combatManager.onPlayerActionCompleted() },
        onApplyStatusEffectToPlayer = { type, turns, source -> combatManager.applyStatusEffectToPlayer(type, turns, 0, source) },
        onApplyStatusEffectToEnemy = { type, turns, source -> combatManager.applyStatusEffectToEnemy(type, turns, 0, source) }
    )

    private val persistenceManager = PersistenceManager(
        _uiState = _uiState,
        application = application,
        repository = repository,
        scope = viewModelScope,
        onLog = ::addLog,
        onRestoreComplete = ::updatePerspective
    )

    private val cosmeticVaultManager = CosmeticVaultManager(
        _uiState = _uiState,
        soundManager = soundManager,
        onLog = ::addLog
    )

    private val skillTreeManager = SkillTreeManager(
        _uiState = _uiState,
        onLog = ::addLog
    )

    init {
        cosmeticVaultManager.onRefreshPerspective = ::updatePerspective
        addLog("DECENTRALIZED TERMINAL ESTABLISHED...", LogType.SUCCESS)
        addLog("CYBERSPACE INTRUSION PROTOCOL READY. SELECT PROFILE.", LogType.INFO)
    }

    fun addLog(message: String, type: LogType = LogType.INFO) {
        _uiState.update { state ->
            val updatedFeed = state.logFeed.toMutableList()
            updatedFeed.add(0, LogMessage(message, type))
            if (updatedFeed.size > 40) updatedFeed.removeAt(updatedFeed.size - 1)
            state.copy(logFeed = updatedFeed)
        }
    }

    private fun updatePerspective() {
        val state = _uiState.value
        val perspective = GameEngine.render3DPerspective(state.maze, state.gridX, state.gridY, state.direction, state.activeWeather)
        _uiState.update { it.copy(perspectiveText = perspective) }
    }

    private fun addExperience(amount: Int) {
        if (amount <= 0) return
        var currentXp = _uiState.value.characterXp + amount
        var currentLvl = _uiState.value.characterLevel
        var reqXp = _uiState.value.xpToNextLevel
        var levelsGained = 0
        while (currentXp >= reqXp) { currentXp -= reqXp; currentLvl += 1; levelsGained += 1; reqXp = 100 + (currentLvl - 1) * 75 }
        if (levelsGained > 0) {
            val newMaxHp = _uiState.value.maxIntegrity + (15 * levelsGained)
            val newMaxShield = _uiState.value.playerMaxShield + (10 * levelsGained)
            val newDmgBonus = _uiState.value.damageBonus + (2 * levelsGained)
            val ramGain = levelsGained / 2 + (if (currentLvl % 2 == 0) 1 else 0)
            val newMaxRam = _uiState.value.maxRam + maxOf(0, ramGain)
            _uiState.update { it.copy(skillPoints = it.skillPoints + levelsGained) }
            _uiState.update { it.copy(characterLevel = currentLvl, characterXp = currentXp, xpToNextLevel = reqXp, maxIntegrity = newMaxHp, integrity = newMaxHp, playerMaxShield = newMaxShield, playerShield = newMaxShield, damageBonus = newDmgBonus, maxRam = newMaxRam, ram = newMaxRam) }
            soundManager.playLootCollectionSound()
            addLog("LEVEL UP! RECOGNIZED AS LEVEL $currentLvl NETRUNNER (+$levelsGained LVL)!", LogType.SUCCESS)
            addLog("SYSTEM UPGRADE: Max Integrity: $newMaxHp HP | Shield: $newMaxShield | Dmg: +$newDmgBonus | Max RAM: $newMaxRam MB", LogType.SUCCESS)
            addLog("SKILL POINTS AWARDED: +$levelsGained (total ${_uiState.value.skillPoints}). Type 'skilltree' to spend.", LogType.SUCCESS)
        } else {
            _uiState.update { it.copy(characterXp = currentXp, xpToNextLevel = reqXp) }
        }
        addLog("GAINED +$amount EXPERIENCE POINTS! (XP: $currentXp / $reqXp)", LogType.INFO)
    }

    private fun triggerCombat(targetX: Int, targetY: Int): Unit {
        combatManager.triggerCombat(targetX, targetY)
    }

    private fun handleCombatVictoryCleanup(enemy: Enemy) {
        val state = _uiState.value
        val updatedMaze = state.maze.map { it.clone() }.toTypedArray()
        if (state.targetNodeY in updatedMaze.indices && state.targetNodeX in updatedMaze[0].indices) {
            updatedMaze[state.targetNodeY][state.targetNodeX] = CellType.PATH
        }
        _uiState.update { it.copy(maze = updatedMaze) }
        updatePerspective()
    }

    private fun handleGameOver(cause: String) = persistenceManager.handleGameOver(cause)

    private fun startHackingPuzzle(tx: Int, ty: Int, difficulty: Int) {
        val puzzle = GameEngine.generateHackingPuzzle(difficulty)
        _uiState.update { it.copy(screen = ActiveScreen.HACKING_MINIGAME, activePuzzle = puzzle, targetNodeX = tx, targetNodeY = ty) }
        addLog("--- BREACH PROTOCOL INITIALIZED ---", LogType.ALERT)
        addLog("MATCH SEQUENCES USING HORIZONTAL/VERTICAL ALTERNATIONS.", LogType.INFO)
    }

    fun hackCell(row: Int, col: Int) {
        val state = _uiState.value; val puzzle = state.activePuzzle ?: return; if (puzzle.isSolved || puzzle.isFailed) return
        val isFirstMove = puzzle.selectedIndices.isEmpty()
        if (isFirstMove) { if (row != 0) { addLog("ERROR: INTRUSION MUST START ON MAIN ROW 0.", LogType.ERROR); return } }
        else {
            val lastMove = puzzle.selectedIndices.last(); val isHorizontalMove = puzzle.selectedIndices.size % 2 == 1
            if (isHorizontalMove) { if (col != lastMove.second) { addLog("SECURITY SYSTEM REJECT: MUST CHANGE VERTICALLY.", LogType.ERROR); return } }
            else { if (row != lastMove.first) { addLog("SECURITY SYSTEM REJECT: MUST CHANGE HORIZONTALLY.", LogType.ERROR); return } }
        }
        val newSelected = puzzle.selectedIndices.toMutableList(); newSelected.add(Pair(row, col))
        val codeSelected = puzzle.grid[row][col]; val newBuffer = puzzle.currentBuffer.toMutableList(); newBuffer.add(codeSelected)
        val nextIsHorizontal = newSelected.size % 2 == 1; val nextRowHighlight = if (nextIsHorizontal) null else row; val nextColHighlight = if (nextIsHorizontal) col else null
        val isSolved = isSubsequenceMatch(puzzle.targetSequence, newBuffer); val isFailed = !isSolved && newBuffer.size >= puzzle.bufferLimit
        val updatedPuzzle = puzzle.copy(selectedIndices = newSelected, currentBuffer = newBuffer, isSolved = isSolved, isFailed = isFailed, highlightedRow = nextRowHighlight, highlightedCol = nextColHighlight)
        _uiState.update { it.copy(activePuzzle = updatedPuzzle) }
        addLog("BUFFER COMMITTED: $codeSelected")
        if (isSolved) handleHackingSuccess() else if (isFailed) handleHackingFailure()
    }

    private fun isSubsequenceMatch(target: List<String>, buffer: List<String>): Boolean {
        if (target.size > buffer.size) return false
        for (i in 0..buffer.size - target.size) { if (buffer.subList(i, i + target.size) == target) return true }
        return false
    }

    private fun handleHackingSuccess() {
        soundManager.playSecurityNodeHackSuccessSound(); val state = _uiState.value
        val nodeType = if (state.targetNodeY in state.maze.indices && state.targetNodeX in state.maze[0].indices) state.maze[state.targetNodeY][state.targetNodeX] else CellType.DATA_STORE
        val isSecretCache = nodeType == CellType.SECRET_CACHE
        var baseBounty = if (isSecretCache) 300 + (state.level * 100) else 100 + (state.level * 50)
        if (state.activeBuffs.contains("CREDIT_SIPHON")) baseBounty = (baseBounty * 1.25f).toInt()
        val bountyCredits = baseBounty + Random.nextInt(50)
        val fragmentsExtracted = if (isSecretCache) Random.nextInt(3, 6) else Random.nextInt(1, 3)
        val rewards = if (isSecretCache) listOf("SlasherMod.pkg", "AegisProtocol.sys", "OverflowExploit.exe", "Overclocker.sys", "HyperRAM.exe") else listOf("NanoMed.sys", "RAMBoost.exe", "Decryptor.pkg", "ChipsetMod.pkg")
        val randomReward = rewards[Random.nextInt(rewards.size)]
        val updatedInventory = state.inventory.toMutableList(); updatedInventory.add(randomReward)
        if (isSecretCache) { val extraItem = listOf("NanoMed.sys", "RAMBoost.exe").random(); updatedInventory.add(extraItem) }
        val updatedMaze = state.maze.map { it.clone() }.toTypedArray()
        if (state.targetNodeY in updatedMaze.indices && state.targetNodeX in updatedMaze[0].indices) updatedMaze[state.targetNodeY][state.targetNodeX] = CellType.PATH
        val isTerminal = nodeType == CellType.HACKABLE_TERMINAL
        if (isTerminal) { addLog("SECURITY TERMINAL OVERRIDDEN! Unlocking all sector gate barriers...", LogType.SUCCESS); for (y in updatedMaze.indices) for (x in updatedMaze[0].indices) if (updatedMaze[y][x] == CellType.TERMINAL_DOOR) updatedMaze[y][x] = CellType.PATH }
        val obtainedKeycard = state.currentZone == Zone.BUILDING && state.buildingFloor == 2 && isSecretCache && !state.hasElevatorKeycard
        _uiState.update { it.copy(screen = ActiveScreen.EXPLORATION, credits = it.credits + bountyCredits, totalCreditsEarned = it.totalCreditsEarned + bountyCredits, inventory = if (obtainedKeycard) updatedInventory + "Elevator Keycard" else updatedInventory, nodesHackedCount = it.nodesHackedCount + 1, maze = updatedMaze, activePuzzle = null, hasElevatorKeycard = it.hasElevatorKeycard || obtainedKeycard, dataFragments = it.dataFragments + fragmentsExtracted, totalDataFragmentsExtracted = it.totalDataFragmentsExtracted + fragmentsExtracted) }
        updatePerspective()
        addLog("DATA FRAGMENTS EXTRACTED: +$fragmentsExtracted [Total: ${_uiState.value.dataFragments}]", LogType.SUCCESS)
        if (obtainedKeycard) { addLog("SECURE KEYCARD RETRIEVED FROM CRYPT-CACHE!", LogType.SUCCESS); addLog("ELEVATOR LINK ONLINE!", LogType.SUCCESS) }
        if (isSecretCache) { addLog("CLASSIFIED VAULT INTRUSION SUCCEEDED!", LogType.SUCCESS); addLog("EXTRACTED ULTRA CREDITS: +$bountyCredits MB!", LogType.SUCCESS) }
        else { addLog("DECRYPTION CRACKED SUCCESSFULLY!", LogType.SUCCESS); addLog("RETRIEVED CREDITS: +$bountyCredits MB. UTILITY: $randomReward", LogType.SUCCESS) }
    }

    private fun handleHackingFailure() {
        soundManager.playSecurityNodeHackFailureSound(); val state = _uiState.value
        val penaltyDmg = 15 + (state.level * 5); val newIntegrity = maxOf(0, state.integrity - penaltyDmg)
        val updatedMaze = state.maze.map { it.clone() }.toTypedArray()
        if (state.targetNodeY in updatedMaze.indices && state.targetNodeX in updatedMaze[0].indices) updatedMaze[state.targetNodeY][state.targetNodeX] = CellType.PATH
        _uiState.update { it.copy(screen = if (newIntegrity <= 0) ActiveScreen.GAME_OVER else ActiveScreen.EXPLORATION, integrity = newIntegrity, maze = updatedMaze, activePuzzle = null) }
        updatePerspective(); addLog("BUFFER OVERFLOW: INTRUSION DETECTED!", LogType.ERROR); addLog("HARDWARE FEEDBACK DAMAGE: -$penaltyDmg INTEGRITY.", LogType.ERROR)
        if (newIntegrity <= 0) handleGameOver("Hacking Malware Core Injection Feedback")
    }

    fun exitHackingMinigame() { _uiState.update { it.copy(screen = ActiveScreen.EXPLORATION, activePuzzle = null) }; updatePerspective(); addLog("BREACH PROTOCOL DISCONNECTED.", LogType.ALERT) }

    fun saveGame() = persistenceManager.saveGame()
    fun loadGame() = persistenceManager.loadGame()
    fun hasSavedGame() = persistenceManager.hasSavedGame()

    fun createCharacter(name: String, selectedClass: NetrunnerClass, startingImplant: CyberwareImplant? = null, allocatedHpPoints: Int = 0, allocatedRamPoints: Int = 0, allocatedReflexPoints: Int = 0, allocatedArmorPoints: Int = 0, allocatedFundPoints: Int = 0, starterKit: String = "STANDARD", mutation: DigitalMutation? = null) {
        val cleanName = name.ifBlank { "Runner_${Random.nextInt(1000, 9999)}" }
        val baseProg = GameEngine.getStartingPrograms(selectedClass)
        var baseCredits = when (selectedClass) { NetrunnerClass.TECHIE, NetrunnerClass.SCRIPT_KIDDIE -> 300; NetrunnerClass.NETRUNNER -> 150; else -> 100 }
        baseCredits += (allocatedFundPoints * 50); if (starterKit == "SCAVENGER") baseCredits += 150
        val startInv = when (starterKit) { "HACKER" -> mutableListOf("NanoMed.sys", "RAMBoost.exe", "Decryptor.pkg", "AntiShield.bin", "FirewallBuffer.pkg"); "COMBAT" -> mutableListOf("NanoMed.sys", "NanoMed.sys", "RAMBoost.exe", "FirewallBuffer.pkg", "NanoShield.pkg"); "SCAVENGER" -> mutableListOf("NanoMed.sys", "RAMBoost.exe", "Decryptor.pkg", "EMPGrenade.bin"); else -> mutableListOf("NanoMed.sys", "RAMBoost.exe") }
        val weaponName = when (selectedClass) { NetrunnerClass.NETRUNNER -> "Militech Optical Cyberdeck Blade"; NetrunnerClass.STREET_SAMURAI -> "Mono-Molecular Cyber-Katana"; NetrunnerClass.TECHIE -> "Kiroshi Pulse-Solderer"; NetrunnerClass.CODE_SLASHER -> "Daedric Cyber-Katana"; NetrunnerClass.CYBER_SHIELD -> "Aegis Shock-Mace"; NetrunnerClass.SCRIPT_KIDDIE -> "Glass Cyber-Dagger"; NetrunnerClass.BUFFER_OVERFLOW -> "Ebony Plasma-Staff" }
        val chosenImplant = startingImplant ?: _uiState.value.selectedStartingImplant
        val initialImplantsMap = mapOf(chosenImplant.slot to chosenImplant)
        val starterStoredImplants = CyberwareImplantRegistry.STARTER_IMPLANTS.filter { it.slot != chosenImplant.slot }.take(2)
        val initMaxHp = (selectedClass.baseIntegrity + chosenImplant.integrityBonus + (allocatedHpPoints * 10))
        val initMaxRam = selectedClass.baseRam + chosenImplant.ramBonus + (allocatedRamPoints * 2)
        val initRecovery = (if (selectedClass == NetrunnerClass.NETRUNNER) 3 else 2) + chosenImplant.recoveryBonus
        val initDamage = chosenImplant.damageBonus + allocatedReflexPoints
        val initDefense = (if (selectedClass == NetrunnerClass.TECHIE) 5 else 0) + chosenImplant.defenseBonus + allocatedArmorPoints
        val startShieldMax = if (selectedClass == NetrunnerClass.STREET_SAMURAI || selectedClass == NetrunnerClass.CYBER_SHIELD) 75 else 50
        val startShieldCurrent = if (selectedClass == NetrunnerClass.STREET_SAMURAI || selectedClass == NetrunnerClass.CYBER_SHIELD) 25 else 10
        val mutationTitle = mutation?.title ?: ""
        val finalMaxHp = (initMaxHp * (mutation?.hpMult ?: 1f)).toInt() + (mutation?.integrityBonus ?: 0)
        val finalMaxRam = initMaxRam + (mutation?.ramMaxBonus ?: 0)
        val finalRecovery = initRecovery + (mutation?.ramRecoveryBonus ?: 0)
        val finalDamage = initDamage + (mutation?.dmgBonus ?: 0)
        val finalDefense = initDefense + (mutation?.defBonus ?: 0)
        val finalShieldMax = startShieldMax + (mutation?.shieldMaxBonus ?: 0)
        val finalCredits = baseCredits + (mutation?.creditBonus ?: 0)
        _uiState.update { it.copy(screen = ActiveScreen.EXPLORATION, runnerName = cleanName, runnerClass = selectedClass, selectedStartingImplant = chosenImplant, installedImplants = initialImplantsMap, storedImplants = starterStoredImplants, maxIntegrity = finalMaxHp, integrity = finalMaxHp, playerMaxShield = finalShieldMax, playerShield = startShieldCurrent, maxRam = finalMaxRam, ram = finalMaxRam, ramRecoveryRate = finalRecovery, damageBonus = finalDamage, defenseBonus = finalDefense, credits = finalCredits, totalCreditsEarned = finalCredits, installedPrograms = baseProg, inventory = startInv, level = 1, gridX = 1, gridY = 1, direction = Direction.EAST, nodesHackedCount = 0, equippedWeaponName = weaponName, hasUsedEmergencyRebootThisRun = false, kineticShieldActiveThisCombat = true, selectedMutationTitle = mutationTitle, logFeed = emptyList()) }
        if (!_uiState.value.tutorialSeen) {
            _uiState.update { it.copy(tutorialActive = true, tutorialStep = 0) }
        }
        val profileEntity = CharacterProfileEntity(profileId = "profile_${cleanName.lowercase().replace(" ", "_")}", runnerName = cleanName, runnerClass = selectedClass.name, level = 1, credits = baseCredits, totalCreditsEarned = baseCredits, maxIntegrity = initMaxHp, maxRam = initMaxRam, nodesHackedCount = 0)
        viewModelScope.launch { repository.saveProfile(profileEntity) }
        addLog("==========================================", LogType.SUCCESS); addLog("PROFILE SYNCHRONIZED: $cleanName [${selectedClass.title}]", LogType.SUCCESS); addLog("SPECIALIZATION: ${selectedClass.passiveDesc}", LogType.INFO); addLog("STARTER IMPLANT INJECTED: ${chosenImplant.name} [${chosenImplant.slot.displayName.uppercase()}]", LogType.SUCCESS)
        if (chosenImplant.passiveAbility != null) addLog("  IMPLANT PASSIVE: ${chosenImplant.passiveAbility.title} - ${chosenImplant.passiveAbility.description}", LogType.INFO)
        if (mutation != null) {
            addLog("MUTATION PROTOCOL INJECTED: ${mutation.icon} ${mutation.title}", LogType.SUCCESS)
            addLog("  EFFECT: ${mutation.effectSummary}", LogType.INFO)
        }
        addLog("INITIALIZING CYBER-SECTOR GRID...", LogType.ALERT)
        explorationManager.loadOrCreateLevel(Zone.BUILDING, 1, 1, 1)
    }

    fun runTerminalCommand(commandText: String) {
        val trimmed = commandText.trim(); if (trimmed.isEmpty()) return
        addLog("> $trimmed", LogType.INFO)
        val parts = trimmed.split(Regex("\\s+")); val mainCommand = parts[0].lowercase(); val state = _uiState.value

        if (combatManager.runTerminalCommand(parts, state)) return
        if (explorationManager.runTerminalCommand(parts, state)) return
        if (inventoryManager.runTerminalCommand(parts, state)) return
        if (cosmeticVaultManager.runTerminalCommand(parts, state)) return
        if (skillTreeManager.runTerminalCommand(parts, state)) return

        when (mainCommand) {
            "help", "?" -> {
                addLog("=== CYBER-TERMINAL COMMAND INTERPRETER ===", LogType.SUCCESS)
                addLog("NAVIGATION: 'forward'/'w', 'backward'/'s', 'left'/'a', 'right'/'d'", LogType.INFO)
                addLog("INTERACTION: 'interact'/'e'", LogType.INFO)
                addLog("COMBAT: 'attack', 'defend', 'flee', 'scan'", LogType.INFO)
                addLog("INVENTORY: 'inventory', 'use <item>', 'equip <item>', 'unequip <slot>'", LogType.INFO)
                addLog("SYSTEM: 'status', 'save', 'load', 'menu', 'shop', 'clear'", LogType.INFO)
                addLog("SKILLS: 'skilltree', 'skill learn <HACKING|COMBAT|ENGINEERING> <#>', 'skill points'", LogType.INFO)
                addLog("TUTORIAL: 'tutorial next', 'tutorial skip'", LogType.INFO)
                addLog("HACKING: 'hack <row> <col>'", LogType.INFO)
                addLog("CRAFTING: 'craft' (list recipes), 'craft <n>' (combine at terminal)", LogType.INFO)
                addLog("SHARING: 'export' (copy save), 'import' (paste save), 'seed' (show level seed)", LogType.INFO)
            }
            "status", "stats", "info", "xp", "level", "lvl" -> {
                addLog("--- RUNNER INTEGRITY PROFILE ---", LogType.SUCCESS)
                addLog("NAME: ${state.runnerName.ifEmpty { "UNNAMED" }} | LEVEL: ${state.characterLevel} (DEPTH: ${state.level})", LogType.INFO)
                addLog("XP: ${state.characterXp} / ${state.xpToNextLevel} (${((state.characterXp.toFloat() / state.xpToNextLevel.coerceAtLeast(1)) * 100).toInt()}%)", LogType.SUCCESS)
                addLog("INTEGRITY: ${state.integrity}/${state.maxIntegrity} | RAM: ${state.ram}/${state.maxRam}MB | SHIELD: ${state.playerShield}/${state.playerMaxShield}", LogType.INFO)
                addLog("CREDITS: ${state.credits}MB | DMG: +${state.damageBonus} | WEAPON: ${state.equippedWeaponName}", LogType.INFO)
                addLog("MUTATION: ${state.selectedMutationTitle.ifEmpty { "NONE STABLE" }} | ENV EVENTS: ${state.environmentalEventsEncountered}", LogType.INFO)
            }
            "craft", "combine", "crafting" -> {
                val recipeIndex = parts.getOrNull(1)?.toIntOrNull()
                if (recipeIndex == null) {
                    addLog("=== TERMINAL CRAFTING PROTOCOL ===", LogType.SUCCESS)
                    addLog("Usage: 'craft <n>' where n is a recipe index.", LogType.INFO)
                    CraftingRecipes.RECIPES.forEachIndexed { idx, recipe ->
                        val ingredientText = recipe.ingredients.joinToString(" + ") { ingredient -> if (ingredient.second > 1) "${ingredient.first} x${ingredient.second}" else ingredient.first }
                        addLog("[$idx] ${recipe.name}: $ingredientText -> ${recipe.resultItemName}", LogType.SUCCESS)
                        addLog("      ${recipe.description}", LogType.INFO)
                    }
                } else {
                    val recipe = CraftingRecipes.find(recipeIndex)
                    if (recipe == null) {
                        addLog("ERROR: Unknown recipe index '$recipeIndex'. Type 'craft' to list recipes.", LogType.ERROR)
                    } else {
                        val missing = recipe.ingredients.firstOrNull { ingredient -> !hasItemInInventory(ingredient.first, ingredient.second) }
                        if (missing != null) {
                            addLog("CRAFT FAILED: Missing ingredient '${missing.first}' (x${missing.second}).", LogType.ERROR)
                        } else {
                            recipe.ingredients.forEach { ingredient -> removeItemFromInventory(ingredient.first, ingredient.second) }
                            addItemToInventory(recipe.resultItemName, 1)
                            val ingredientText = recipe.ingredients.joinToString(" + ") { ingredient -> if (ingredient.second > 1) "${ingredient.first} x${ingredient.second}" else ingredient.first }
                            addLog("CRAFT SUCCESSFUL: ${recipe.name} completed! [$ingredientText] -> ${recipe.resultItemName} added to inventory.", LogType.SUCCESS)
                            soundManager.playLootCollectionSound()
                        }
                    }
                }
            }
            "exit", "close" -> {
                when (state.screen) {
                    ActiveScreen.UPGRADE_STORE -> { _uiState.update { it.copy(screen = ActiveScreen.EXPLORATION) }; updatePerspective(); addLog("DISCONNECTED FROM SHOP SERVER.") }
                    ActiveScreen.LEADERBOARD -> persistenceManager.exitLeaderboard()
                    ActiveScreen.HACKING_MINIGAME -> exitHackingMinigame()
                    else -> addLog("ERROR: Nothing to exit.", LogType.ERROR)
                }
            }
            "save" -> saveGame()
            "load" -> loadGame()
            "export" -> { copyExportToClipboard() }
            "import" -> { importFromClipboard() }
            "seed" -> { addLog("LEVEL SEED: ${state.levelSeed}", LogType.INFO); addLog("Share this seed with friends to play the same dungeon!", LogType.INFO) }
            "menu" -> persistenceManager.returnToStartMenu()
            "clear" -> { _uiState.update { it.copy(logFeed = emptyList()) }; addLog("Log console cleared.", LogType.INFO) }
            "tutorial", "helpme", "assist" -> {
                when (parts.getOrNull(1)?.lowercase()) {
                    "next" -> tutorialAdvance()
                    "skip" -> tutorialSkip()
                    else -> onTutorialStepLog(_uiState.value.tutorialStep)
                }
            }
            else -> addLog("UNKNOWN COMMAND: '$trimmed'. Type 'help' for support.", LogType.ERROR)
        }
    }

    fun moveForward() = explorationManager.moveForward()
    fun moveBackward() = explorationManager.moveBackward()
    fun turnLeft() = explorationManager.turnLeft()
    fun turnRight() = explorationManager.turnRight()
    fun triggerMapScan() = explorationManager.triggerMapScan()
    fun processWeatherOnStep() = explorationManager.processWeatherOnStep()
    fun interact() = explorationManager.interact()
    fun loadOrCreateLevel(targetZone: Zone, targetFloorOrLevel: Int, targetX: Int? = null, targetY: Int? = null, isAscending: Boolean = true, byElevator: Boolean = false) = explorationManager.loadOrCreateLevel(targetZone, targetFloorOrLevel, targetX, targetY, isAscending, byElevator)
    fun ascendStairs() = explorationManager.ascendStairs()
    fun descendStairs() = explorationManager.descendStairs()
    fun interactWithElevator() = explorationManager.interactWithElevator()
    fun generateProceduralMultiFloorLevel(numFloors: Int = 4, width: Int = 14, height: Int = 14) = explorationManager.generateProceduralMultiFloorLevel(numFloors, width, height)
    fun setActiveFloorIndex(floorIndex: Int) = explorationManager.setActiveFloorIndex(floorIndex)
    fun navigateVerticalConnector(connector: VerticalConnector) = explorationManager.navigateVerticalConnector(connector)
    fun ensureSvdagInitialized(targetDepth: Int = 7) = explorationManager.ensureSvdagInitialized(targetDepth)
    fun initOrRegenerateSvdag(targetDepth: Int = 7, seed: Long = System.currentTimeMillis()) = explorationManager.initOrRegenerateSvdag(targetDepth, seed)
    fun moveSvdagPlayer(dx: Int, dy: Int, dz: Int) = explorationManager.moveSvdagPlayer(dx, dy, dz)
    fun tickSvdagIceAI() = explorationManager.tickSvdagIceAI()
    fun modifySvdagVoxel(x: Int, y: Int, z: Int, type: com.example.data.svdag.VoxelType) = explorationManager.modifySvdagVoxel(x, y, z, type)
    fun setSvdagLodLevel(lod: Int) = explorationManager.setSvdagLodLevel(lod)
    fun triggerSvdagScan(originX: Int? = null, originY: Int? = null, originZ: Int? = null, radius: Int = 16) = explorationManager.triggerSvdagScan(originX, originY, originZ, radius)
    fun enterSvdagWorldInspector() = explorationManager.enterSvdagWorldInspector()
    fun exitSvdagWorldInspector() = explorationManager.exitSvdagWorldInspector()
    fun setUseFpeInFppView(enabled: Boolean) = explorationManager.setUseFpeInFppView(enabled)

    fun combatAttack() = combatManager.combatAttack()
    fun combatDefend() = combatManager.combatDefend()
    fun combatHack() = combatManager.combatHack()
    fun combatScan() = combatManager.combatScan()
    fun endTurn() = combatManager.endTurn()
    fun executeCombatProgram(program: Program) = combatManager.executeCombatProgram(program)
    fun fleeCombat() = combatManager.fleeCombat()
    fun selectCombatHackSymbol(symbol: String) = combatManager.selectCombatHackSymbol(symbol)
    fun clearCombatHackBuffer() = combatManager.clearCombatHackBuffer()
    fun abortCombatHack() = combatManager.abortCombatHack()
    fun applyStatusEffectToPlayer(type: StatusEffectType, turns: Int, magnitude: Int = 0, source: String = "") = combatManager.applyStatusEffectToPlayer(type, turns, magnitude, source)
    fun applyStatusEffectToEnemy(type: StatusEffectType, turns: Int, magnitude: Int = 0, source: String = "") = combatManager.applyStatusEffectToEnemy(type, turns, magnitude, source)
    fun setCombatStyle(style: String) { _uiState.update { it.copy(selectedCombatStyle = "Strike") }; addLog("COMBAT STANCE: Single unified Strike stance active.", LogType.INFO) }

    fun getStructuredInventorySlots() = inventoryManager.getStructuredInventorySlots()
    fun addItemToInventory(itemName: String, quantity: Int = 1) = inventoryManager.addItemToInventory(itemName, quantity)
    fun removeItemFromInventory(itemName: String, quantity: Int = 1) = inventoryManager.removeItemFromInventory(itemName, quantity)
    fun hasItemInInventory(itemName: String, quantity: Int = 1) = inventoryManager.hasItemInInventory(itemName, quantity)
    fun equipItem(itemName: String) = inventoryManager.equipItem(itemName)
    fun unequipItemSlot(slot: EquipmentSlot) = inventoryManager.unequipItemSlot(slot)
    fun scavengeCurrentCell() = inventoryManager.scavengeCurrentCell()
    fun setInventoryCategoryFilter(category: InventoryCategory?) = inventoryManager.setInventoryCategoryFilter(category)
    fun setInventorySortOption(option: InventorySortOption) = inventoryManager.setInventorySortOption(option)
    fun discardInventoryItem(itemName: String) = inventoryManager.discardInventoryItem(itemName)
    fun useInventoryItem(itemName: String) = inventoryManager.useInventoryItem(itemName)
    fun enterShop() = inventoryManager.enterShop()
    fun exitShop() = inventoryManager.exitShop()
    fun purchaseCyberware(cyberware: Cyberware) = inventoryManager.purchaseCyberware(cyberware)
    fun purchaseConsumable(name: String, cost: Int) = inventoryManager.purchaseConsumable(name, cost)
    fun selectStartingImplant(implant: CyberwareImplant) = inventoryManager.selectStartingImplant(implant)
    fun openCyberwareClinic() = inventoryManager.openCyberwareClinic()
    fun closeCyberwareClinic() = inventoryManager.closeCyberwareClinic()

    private val tutorialStepCount = 5

    fun tutorialAdvance() {
        val s = _uiState.value
        if (!s.tutorialActive) return
        val step = s.tutorialStep + 1
        if (step >= tutorialStepCount) {
            finishTutorial()
        } else {
            _uiState.update { it.copy(tutorialStep = step) }
            onTutorialStepLog(step)
        }
    }

    fun tutorialSkip() = finishTutorial()

    private fun finishTutorial() {
        _uiState.update { it.copy(tutorialActive = false, tutorialSeen = true) }
        persistenceManager.markTutorialSeen()
        addLog("TUTORIAL COMPLETE. GOOD LUCK, NETRUNNER.", LogType.SUCCESS)
    }

    private fun onTutorialStepLog(step: Int) {
        when (step) {
            1 -> addLog("TUTORIAL 2/5: SWIPE to move. Drag left/right to turn, up/down to advance.", LogType.INFO)
            2 -> addLog("TUTORIAL 3/5: Stand next to a terminal and type 'hack <row> <col>' to interact.", LogType.INFO)
            3 -> addLog("TUTORIAL 4/5: Combat is turn-based. Use 'attack', 'defend', and items from the terminal.", LogType.INFO)
            4 -> addLog("TUTORIAL 5/5: Visit the Cyberware Clinic ('clinic') to equip implants for bonuses.", LogType.INFO)
        }
    }

    fun installImplant(implant: CyberwareImplant) = inventoryManager.installImplant(implant)
    fun uninstallImplant(slot: ImplantBodySlot) = inventoryManager.uninstallImplant(slot)
    fun toggleCyberwareInventoryOverlay(show: Boolean? = null) = inventoryManager.toggleCyberwareInventoryOverlay(show)
    fun setSelectedOverlayTab(tab: String) = inventoryManager.setSelectedOverlayTab(tab)
    fun setSelectedOverlaySlotFilter(slot: ImplantBodySlot?) = inventoryManager.setSelectedOverlaySlotFilter(slot)
    fun equipImplantFromInventory(implant: CyberwareImplant) = inventoryManager.equipImplantFromInventory(implant)
    fun unequipImplantToInventory(slot: ImplantBodySlot) = inventoryManager.unequipImplantToInventory(slot)
    fun scavengeSampleImplant() = inventoryManager.scavengeSampleImplant()

    fun extractDataFragments(amount: Int, sourceDescription: String) = cosmeticVaultManager.extractDataFragments(amount, sourceDescription)
    fun unlockCosmeticTheme(themeId: String) = cosmeticVaultManager.unlockCosmeticTheme(themeId)
    fun setActiveTheme(themeId: String) = cosmeticVaultManager.setActiveTheme(themeId)
    fun unlockPromptStyle(promptId: String) = cosmeticVaultManager.unlockPromptStyle(promptId)
    fun setActivePromptStyle(promptId: String) = cosmeticVaultManager.setActivePromptStyle(promptId)
    fun unlockPerformanceBuff(buffId: String) = cosmeticVaultManager.unlockPerformanceBuff(buffId)
    fun togglePerformanceBuff(buffId: String) = cosmeticVaultManager.togglePerformanceBuff(buffId)
    fun enterDataVaultScreen() = cosmeticVaultManager.enterDataVaultScreen()
    fun exitDataVaultScreen() = cosmeticVaultManager.exitDataVaultScreen()

    fun viewLeaderboard() = persistenceManager.viewLeaderboard()
    fun exitLeaderboard() = persistenceManager.exitLeaderboard()
    fun clearHighScores() = persistenceManager.clearHighScores()
    fun viewSettings() = persistenceManager.viewSettings()
    fun exitSettings() = persistenceManager.exitSettings()

    fun setBgmEnabled(enabled: Boolean) {
        _bgmEnabled.value = enabled
        soundManager.setBgmMuted(!enabled)
    }

    fun setBgmVolume(volume: Float) {
        _bgmVolume.value = volume
        soundManager.setBgmVolume(volume)
    }

    fun setSfxEnabled(enabled: Boolean) {
        _sfxEnabled.value = enabled
        soundManager.setSfxMuted(!enabled)
    }

    fun setVibrationEnabled(enabled: Boolean) {
        _vibrationEnabled.value = enabled
        com.example.audio.CyberVibrationManager.setEnabled(enabled)
    }
    fun disconnectRunSuccessfully() = persistenceManager.disconnectRunSuccessfully()
    fun restartGame() = persistenceManager.restartGame()
    fun startNewRun() = persistenceManager.startNewRun()
    fun returnToStartMenu() = persistenceManager.returnToStartMenu()
    fun resumeGame() = persistenceManager.resumeGame()

    fun exportSave() = persistenceManager.exportSave()
    fun importSave(encoded: String) = persistenceManager.importSave(encoded)
    fun copyExportToClipboard() = persistenceManager.copyExportToClipboard()
    fun importFromClipboard() = persistenceManager.importFromClipboard()
}
