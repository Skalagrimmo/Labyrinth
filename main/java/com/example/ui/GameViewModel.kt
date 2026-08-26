package com.example.ui

import android.app.Application
import android.content.Context
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlin.random.Random

enum class CombatTurn {
    PLAYER,
    ENEMY,
    ANIMATING
}

data class CombatHackingPatternState(
    val targetPattern: List<String>,
    val userSequence: List<String> = emptyList(),
    val availablePool: List<String>,
    val timeRemainingSeconds: Int = 12,
    val maxTimeSeconds: Int = 12,
    val attemptsRemaining: Int = 3,
    val maxAttempts: Int = 3,
    val enemyName: String,
    val potentialDamage: Int
)

class GameViewModel(application: Application) : AndroidViewModel(application) {

    private val soundManager = com.example.audio.CyberSoundEffectsManager.getInstance(application)

    private val database = GameDatabase.getDatabase(application)
    private val repository = GameRepository(
        database.runRecordDao(),
        database.characterProfileDao(),
        database.gameSaveProgressDao(),
        database.inventoryItemDao()
    )

    // High scores stream
    val runRecords: StateFlow<List<RunRecord>> = repository.allRunRecords
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Room Database character profiles, save progress, and inventory streams
    val characterProfiles: StateFlow<List<CharacterProfileEntity>> = repository.allCharacterProfiles
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val savedGameProgress: StateFlow<GameSaveProgressEntity?> = repository.currentSaveProgress
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val roomInventoryItems: StateFlow<List<InventoryItemEntity>> = repository.currentInventoryItems
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Primary Game UI State
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
        val defenseBonus: Int = 0, // In percentage shield reduction

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
        val activeWeather: com.example.data.CyberWeather = com.example.data.CyberWeather.CLEAR,
        val weatherTurnsLeft: Int = 0,
        val stepsSinceLastEvent: Int = 0,
        val nextEventSteps: Int = 30,
        val predictedWeather: com.example.data.CyberWeather? = null,
        val originalMaze: Array<Array<CellType>>? = null,

        // Equipment & Abilities
        val installedCyberware: List<Cyberware> = emptyList(),
        val installedPrograms: List<Program> = emptyList(),
        val inventory: List<String> = listOf("NanoMed.sys", "RAMBoost.exe"),

        // Map Scan Ability State
        val isScanActive: Boolean = false,
        val scanTurnsLeft: Int = 0,
        val scannedEnemies: Set<Pair<Int, Int>> = emptySet(),
        val scannedLoot: Set<Pair<Int, Int>> = emptySet(),
        val scanTimestamp: Long = 0L,

        // Active combat
        val activeEnemy: Enemy? = null,
        val enemyCombatAction: String = "",
        val gameState: GameState = GameState.EXPLORATION,

        // Active hacking puzzle
        val activePuzzle: HackingPuzzle? = null,
        val targetNodeX: Int = -1,
        val targetNodeY: Int = -1,

        // Logs
        val logFeed: List<LogMessage> = emptyList(),

        // Stats tracking for current run
        val nodesHackedCount: Int = 0,
        val totalCreditsEarned: Int = 100,
        val runOutcome: String = "",

        // Visual Turn-Based Combat State & Effects
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
        val showCombatBanner: String? = null, // e.g. "COMBAT STARTED", "VICTORY", "DEFEAT"
        val isCombatInputEnabled: Boolean = true,
        val enemyAttackCharge: Float = 0f,
        val activeFirewallTimeLeft: Int = 0,
        val playerStatusEffects: List<com.example.data.ActiveStatusEffect> = emptyList(),
        val enemyStatusEffects: List<com.example.data.ActiveStatusEffect> = emptyList(),
        val defendCooldown: Int = 0,
        val attackCooldown: Int = 0,
        val programCooldowns: Map<String, Int> = emptyMap(),

        // World Expansion State
        val currentZone: com.example.data.Zone = com.example.data.Zone.BUILDING,
        val buildingFloor: Int = 1,
        val collectorsLevel: Int = 1,
        val cityDistrictIndex: Int = 0,
        val hasElevatorKeycard: Boolean = false,
        val fadeAlpha: Float = 0f,
        val buildingFloors: Map<Int, Array<Array<com.example.data.CellType>>> = emptyMap(),
        val buildingExplored: Map<Int, Set<Pair<Int, Int>>> = emptyMap(),
        val collectorsLevels: Map<Int, Array<Array<com.example.data.CellType>>> = emptyMap(),
        val collectorsExplored: Map<Int, Set<Pair<Int, Int>>> = emptyMap(),
        val cityDistricts: Map<Int, Array<Array<com.example.data.CellType>>> = emptyMap(),
        val cityExplored: Map<Int, Set<Pair<Int, Int>>> = emptyMap(),

        // Morrowind Style System States
        val selectedCombatStyle: String = "Strike", // Single unified combat style
        val equippedWeaponName: String = "Sparksteel Dagger",
        val equippedArmorName: String = "Basic Firewall Mesh",
        val equippedUtilityName: String = "None",
        val equippedWeaponItem: com.example.data.GameItem? = null,
        val equippedArmorItem: com.example.data.GameItem? = null,
        val equippedUtilityItem: com.example.data.GameItem? = null,
        val selectedInventoryCategoryFilter: com.example.data.InventoryCategory? = null,
        val inventorySortOption: com.example.data.InventorySortOption = com.example.data.InventorySortOption.CATEGORY,
        val weaponSwingProgress: Float = 0f,
        val weaponSwingType: String = "Strike",

        // Cybernetic Implants System
        val selectedStartingImplant: com.example.data.CyberwareImplant = com.example.data.CyberwareImplantRegistry.STARTER_IMPLANTS[0],
        val installedImplants: Map<com.example.data.ImplantBodySlot, com.example.data.CyberwareImplant?> = emptyMap(),
        val storedImplants: List<com.example.data.CyberwareImplant> = emptyList(),
        val showCyberwareInventoryOverlay: Boolean = false,
        val selectedOverlayTab: String = "EQUIPPED", // "EQUIPPED", "STORED", "STATS"
        val selectedOverlaySlotFilter: com.example.data.ImplantBodySlot? = null,
        val hasUsedEmergencyRebootThisRun: Boolean = false,
        val kineticShieldActiveThisCombat: Boolean = true,
        val naniteStepCounter: Int = 0,

        // Sparse Voxel DAG (SVDAG) World Engine State
        val svdagWorld: com.example.data.svdag.SparseVoxelDag? = null,
        val svdagStats: com.example.data.svdag.SvdagStats? = null,
        val svdagScaleDepth: Int = 7,
        val svdagLodLevel: Int = 0,
        val svdagScanSummary: com.example.data.svdag.SvdagScanSummary? = null,
        val svdagRippleState: com.example.data.svdag.SvdagRippleState? = null,
        val svdagIceEntities: List<com.example.data.svdag.IceEntity> = emptyList(),
        val svdagPlayerPos: Triple<Int, Int, Int> = Triple(2, 2, 3),
        val svdagPlayerHideStatus: com.example.data.svdag.PlayerHideStatus? = null,
        val svdagAutoPatrolActive: Boolean = false,

        // Data Fragments & Cosmetic Customization Vault System
        val dataFragments: Int = 0,
        val totalDataFragmentsExtracted: Int = 0,
        val unlockedThemes: Set<String> = setOf("DEFAULT_CYBER"),
        val activeCosmeticTheme: String = "DEFAULT_CYBER",
        val unlockedPrompts: Set<String> = setOf("DEFAULT"),
        val activePromptStyle: String = "DEFAULT",
        val unlockedBuffs: Set<String> = emptySet(),
        val activeBuffs: Set<String> = emptySet(),

        // Procedural Multi-Floor Grid Reachable Level System
        val currentMultiFloorLevel: com.example.data.MultiFloorGridLevel? = null,
        val activeFloorIndex: Int = 0
    )

    enum class ActiveScreen {
        START_MENU,
        CHARACTER_CREATION,
        EXPLORATION,
        COMBAT,
        HACKING_MINIGAME,
        UPGRADE_STORE,
        LEADERBOARD,
        GAME_OVER,
        CYBERWARE_CLINIC,
        SVDAG_WORLD_BUILDER,
        DATA_FRAGMENTS_VAULT
    }

    private val _uiState = MutableStateFlow(GameUiState())
    val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()

    private val underlyingCellTypes = java.util.concurrent.ConcurrentHashMap<String, com.example.data.CellType>()
    private var aiTickCounter = 0

    init {
        // Initialize with default logging
        addLog("DECENTRALIZED TERMINAL ESTABLISHED...", LogType.SUCCESS)
        addLog("CYBERSPACE INTRUSION PROTOCOL READY. SELECT PROFILE.", LogType.INFO)

        // Periodic real-time update loop disabled to go strictly turn-based and input-driven.
        /*
        viewModelScope.launch(Dispatchers.Default) {
            while (true) {
                update()
                kotlinx.coroutines.delay(100)
            }
        }
        */
    }

    // ----------------------------------------------------
    // Cybernetic Implants & Surgery System
    // ----------------------------------------------------

    fun selectStartingImplant(implant: com.example.data.CyberwareImplant) {
        _uiState.update { it.copy(selectedStartingImplant = implant) }
    }

    fun openCyberwareClinic() {
        _uiState.update { it.copy(screen = ActiveScreen.CYBERWARE_CLINIC) }
    }

    fun closeCyberwareClinic() {
        _uiState.update { it.copy(screen = ActiveScreen.EXPLORATION) }
    }

    fun installImplant(implant: com.example.data.CyberwareImplant): Boolean {
        val state = _uiState.value
        val currentSlotImplant = state.installedImplants[implant.slot]

        val newMaxHp = (state.maxIntegrity - (currentSlotImplant?.integrityBonus ?: 0) + implant.integrityBonus).coerceAtLeast(10)
        val newHp = (state.integrity + implant.integrityBonus).coerceIn(1, newMaxHp)
        val newMaxRam = (state.maxRam - (currentSlotImplant?.ramBonus ?: 0) + implant.ramBonus).coerceAtLeast(2)
        val newRam = (state.ram + implant.ramBonus).coerceIn(1, newMaxRam)
        val newRecovery = (state.ramRecoveryRate - (currentSlotImplant?.recoveryBonus ?: 0) + implant.recoveryBonus).coerceAtLeast(1)
        val newDmg = (state.damageBonus - (currentSlotImplant?.damageBonus ?: 0) + implant.damageBonus).coerceAtLeast(0)
        val newDef = (state.defenseBonus - (currentSlotImplant?.defenseBonus ?: 0) + implant.defenseBonus).coerceAtLeast(0)

        val updatedImplants = state.installedImplants.toMutableMap()
        updatedImplants[implant.slot] = implant

        _uiState.update {
            it.copy(
                installedImplants = updatedImplants,
                maxIntegrity = newMaxHp,
                integrity = newHp,
                maxRam = newMaxRam,
                ram = newRam,
                ramRecoveryRate = newRecovery,
                damageBonus = newDmg,
                defenseBonus = newDef
            )
        }

        addLog("${implant.icon} IMPLANT SURGERY SUCCESS: Installed ${implant.name} into [${implant.slot.displayName.uppercase()}].", LogType.SUCCESS)
        if (implant.passiveAbility != null) {
            addLog("  └ PASSIVE ABILITY ACTIVATED: ${implant.passiveAbility.title} - ${implant.passiveAbility.description}", LogType.INFO)
        }
        soundManager.playCyberwareInstallSound()
        saveGame()
        return true
    }

    fun uninstallImplant(slot: com.example.data.ImplantBodySlot): Boolean {
        val state = _uiState.value
        val implant = state.installedImplants[slot] ?: return false

        val newMaxHp = (state.maxIntegrity - implant.integrityBonus).coerceAtLeast(10)
        val newHp = state.integrity.coerceAtMost(newMaxHp)
        val newMaxRam = (state.maxRam - implant.ramBonus).coerceAtLeast(2)
        val newRam = state.ram.coerceAtMost(newMaxRam)
        val newRecovery = (state.ramRecoveryRate - implant.recoveryBonus).coerceAtLeast(1)
        val newDmg = (state.damageBonus - implant.damageBonus).coerceAtLeast(0)
        val newDef = (state.defenseBonus - implant.defenseBonus).coerceAtLeast(0)

        val updatedImplants = state.installedImplants.toMutableMap()
        updatedImplants.remove(slot)

        _uiState.update {
            it.copy(
                installedImplants = updatedImplants,
                maxIntegrity = newMaxHp,
                integrity = newHp,
                maxRam = newMaxRam,
                ram = newRam,
                ramRecoveryRate = newRecovery,
                damageBonus = newDmg,
                defenseBonus = newDef
            )
        }

        addLog("🔌 CYBERWARE REMOVED: Uninstalled ${implant.name} from [${slot.displayName.uppercase()}].", LogType.ALERT)
        saveGame()
        return true
    }

    fun toggleCyberwareInventoryOverlay(show: Boolean? = null) {
        _uiState.update { state ->
            val next = show ?: !state.showCyberwareInventoryOverlay
            state.copy(showCyberwareInventoryOverlay = next)
        }
    }

    fun setSelectedOverlayTab(tab: String) {
        _uiState.update { it.copy(selectedOverlayTab = tab) }
    }

    fun setSelectedOverlaySlotFilter(slot: com.example.data.ImplantBodySlot?) {
        _uiState.update { it.copy(selectedOverlaySlotFilter = slot) }
    }

    fun equipImplantFromInventory(implant: com.example.data.CyberwareImplant): Boolean {
        val state = _uiState.value
        val storedList = state.storedImplants.toMutableList()
        val index = storedList.indexOfFirst { it.id == implant.id }
        if (index != -1) {
            storedList.removeAt(index)
        }

        val currentSlotImplant = state.installedImplants[implant.slot]
        if (currentSlotImplant != null) {
            storedList.add(currentSlotImplant)
            addLog("🔄 SWAPPED: Returned ${currentSlotImplant.name} to storage core.", LogType.INFO)
        }

        val newMaxHp = (state.maxIntegrity - (currentSlotImplant?.integrityBonus ?: 0) + implant.integrityBonus).coerceAtLeast(10)
        val newHp = (state.integrity + implant.integrityBonus).coerceIn(1, newMaxHp)
        val newMaxRam = (state.maxRam - (currentSlotImplant?.ramBonus ?: 0) + implant.ramBonus).coerceAtLeast(2)
        val newRam = (state.ram + implant.ramBonus).coerceIn(1, newMaxRam)
        val newRecovery = (state.ramRecoveryRate - (currentSlotImplant?.recoveryBonus ?: 0) + implant.recoveryBonus).coerceAtLeast(1)
        val newDmg = (state.damageBonus - (currentSlotImplant?.damageBonus ?: 0) + implant.damageBonus).coerceAtLeast(0)
        val newDef = (state.defenseBonus - (currentSlotImplant?.defenseBonus ?: 0) + implant.defenseBonus).coerceAtLeast(0)

        val updatedInstalled = state.installedImplants.toMutableMap()
        updatedInstalled[implant.slot] = implant

        _uiState.update {
            it.copy(
                installedImplants = updatedInstalled,
                storedImplants = storedList,
                maxIntegrity = newMaxHp,
                integrity = newHp,
                maxRam = newMaxRam,
                ram = newRam,
                ramRecoveryRate = newRecovery,
                damageBonus = newDmg,
                defenseBonus = newDef
            )
        }

        addLog("🦾 EQUIPPED: Fitted ${implant.name} into [${implant.slot.displayName.uppercase()}].", LogType.SUCCESS)
        if (implant.passiveAbility != null) {
            addLog("  └ PASSIVE ONLINE: ${implant.passiveAbility.title} - ${implant.passiveAbility.description}", LogType.INFO)
        }
        soundManager.playCyberwareInstallSound()
        saveGame()
        return true
    }

    fun unequipImplantToInventory(slot: com.example.data.ImplantBodySlot): Boolean {
        val state = _uiState.value
        val implant = state.installedImplants[slot] ?: return false

        val newMaxHp = (state.maxIntegrity - implant.integrityBonus).coerceAtLeast(10)
        val newHp = state.integrity.coerceAtMost(newMaxHp)
        val newMaxRam = (state.maxRam - implant.ramBonus).coerceAtLeast(2)
        val newRam = state.ram.coerceAtMost(newMaxRam)
        val newRecovery = (state.ramRecoveryRate - implant.recoveryBonus).coerceAtLeast(1)
        val newDmg = (state.damageBonus - implant.damageBonus).coerceAtLeast(0)
        val newDef = (state.defenseBonus - implant.defenseBonus).coerceAtLeast(0)

        val updatedInstalled = state.installedImplants.toMutableMap()
        updatedInstalled.remove(slot)

        val updatedStored = state.storedImplants.toMutableList()
        updatedStored.add(implant)

        _uiState.update {
            it.copy(
                installedImplants = updatedInstalled,
                storedImplants = updatedStored,
                maxIntegrity = newMaxHp,
                integrity = newHp,
                maxRam = newMaxRam,
                ram = newRam,
                ramRecoveryRate = newRecovery,
                damageBonus = newDmg,
                defenseBonus = newDef
            )
        }

        addLog("📦 STORED: Unfitted ${implant.name} from [${slot.displayName.uppercase()}] to storage core.", LogType.ALERT)
        saveGame()
        return true
    }

    fun scavengeSampleImplant() {
        val all = com.example.data.CyberwareImplantRegistry.ALL_IMPLANTS
        val randomImplant = all.random()
        val updatedStored = _uiState.value.storedImplants + randomImplant
        _uiState.update { it.copy(storedImplants = updatedStored) }
        addLog("🎁 SCAVENGED CYBERWARE: Acquired ${randomImplant.name} [${randomImplant.rarity.displayName}].", LogType.SUCCESS)
        saveGame()
    }

    // ----------------------------------------------------
    // State Modification & Character Creation
    // ----------------------------------------------------

    fun createCharacter(
        name: String,
        selectedClass: NetrunnerClass,
        startingImplant: com.example.data.CyberwareImplant? = null,
        allocatedHpPoints: Int = 0,
        allocatedRamPoints: Int = 0,
        allocatedReflexPoints: Int = 0,
        allocatedArmorPoints: Int = 0,
        allocatedFundPoints: Int = 0,
        starterKit: String = "STANDARD"
    ) {
        val cleanName = name.ifBlank { "Runner_${Random.nextInt(1000, 9999)}" }
        val baseProg = GameEngine.getStartingPrograms(selectedClass)
        
        var baseCredits = when (selectedClass) {
            NetrunnerClass.TECHIE, NetrunnerClass.SCRIPT_KIDDIE -> 300
            NetrunnerClass.NETRUNNER -> 150
            NetrunnerClass.STREET_SAMURAI -> 100
            else -> 100
        }
        baseCredits += (allocatedFundPoints * 50)
        if (starterKit == "SCAVENGER") baseCredits += 150

        val startInv = when (starterKit) {
            "HACKER" -> mutableListOf("NanoMed.sys", "RAMBoost.exe", "Decryptor.pkg", "AntiShield.bin", "FirewallBuffer.pkg")
            "COMBAT" -> mutableListOf("NanoMed.sys", "NanoMed.sys", "RAMBoost.exe", "FirewallBuffer.pkg", "NanoShield.pkg")
            "SCAVENGER" -> mutableListOf("NanoMed.sys", "RAMBoost.exe", "Decryptor.pkg", "EMPGrenade.bin")
            else -> mutableListOf("NanoMed.sys", "RAMBoost.exe")
        }

        val weaponName = when (selectedClass) {
            NetrunnerClass.NETRUNNER -> "Militech Optical Cyberdeck Blade"
            NetrunnerClass.STREET_SAMURAI -> "Mono-Molecular Cyber-Katana"
            NetrunnerClass.TECHIE -> "Kiroshi Pulse-Solderer"
            NetrunnerClass.CODE_SLASHER -> "Daedric Cyber-Katana"
            NetrunnerClass.CYBER_SHIELD -> "Aegis Shock-Mace"
            NetrunnerClass.SCRIPT_KIDDIE -> "Glass Cyber-Dagger"
            NetrunnerClass.BUFFER_OVERFLOW -> "Ebony Plasma-Staff"
        }

        val chosenImplant = startingImplant ?: _uiState.value.selectedStartingImplant
        val initialImplantsMap = mapOf(chosenImplant.slot to chosenImplant)
        val starterStoredImplants = com.example.data.CyberwareImplantRegistry.STARTER_IMPLANTS.filter { it.slot != chosenImplant.slot }.take(2)

        val initMaxHp = selectedClass.baseIntegrity + chosenImplant.integrityBonus + (allocatedHpPoints * 10)
        val initMaxRam = selectedClass.baseRam + chosenImplant.ramBonus + (allocatedRamPoints * 2)
        val initRecovery = (if (selectedClass == NetrunnerClass.NETRUNNER) 3 else 2) + chosenImplant.recoveryBonus
        val initDamage = chosenImplant.damageBonus + allocatedReflexPoints
        val initDefense = (if (selectedClass == NetrunnerClass.TECHIE) 5 else 0) + chosenImplant.defenseBonus + allocatedArmorPoints

        val startShieldMax = if (selectedClass == NetrunnerClass.STREET_SAMURAI || selectedClass == NetrunnerClass.CYBER_SHIELD) 75 else 50
        val startShieldCurrent = if (selectedClass == NetrunnerClass.STREET_SAMURAI || selectedClass == NetrunnerClass.CYBER_SHIELD) 25 else 10

        _uiState.update { state ->
            state.copy(
                screen = ActiveScreen.EXPLORATION,
                runnerName = cleanName,
                runnerClass = selectedClass,
                selectedStartingImplant = chosenImplant,
                installedImplants = initialImplantsMap,
                storedImplants = starterStoredImplants,
                maxIntegrity = initMaxHp,
                integrity = initMaxHp,
                playerMaxShield = startShieldMax,
                playerShield = startShieldCurrent,
                maxRam = initMaxRam,
                ram = initMaxRam,
                ramRecoveryRate = initRecovery,
                damageBonus = initDamage,
                defenseBonus = initDefense,
                credits = baseCredits,
                totalCreditsEarned = baseCredits,
                installedPrograms = baseProg,
                inventory = startInv,
                level = 1,
                gridX = 1,
                gridY = 1,
                direction = Direction.EAST,
                nodesHackedCount = 0,
                equippedWeaponName = weaponName,
                hasUsedEmergencyRebootThisRun = false,
                kineticShieldActiveThisCombat = true,
                logFeed = emptyList() // clear creation logs for clean game view
            )
        }

        // Persist new character profile to Room Database
        val profileEntity = CharacterProfileEntity(
            profileId = "profile_${cleanName.lowercase().replace(" ", "_")}",
            runnerName = cleanName,
            runnerClass = selectedClass.name,
            level = 1,
            credits = baseCredits,
            totalCreditsEarned = baseCredits,
            maxIntegrity = initMaxHp,
            maxRam = initMaxRam,
            nodesHackedCount = 0
        )
        viewModelScope.launch {
            repository.saveProfile(profileEntity)
        }

        addLog("==========================================", LogType.SUCCESS)
        addLog("PROFILE SYNCHRONIZED: $cleanName [${selectedClass.title}]", LogType.SUCCESS)
        addLog("SPECIALIZATION: ${selectedClass.passiveDesc}", LogType.INFO)
        addLog("🔌 STARTER IMPLANT INJECTED: ${chosenImplant.name} [${chosenImplant.slot.displayName.uppercase()}]", LogType.SUCCESS)
        if (chosenImplant.passiveAbility != null) {
            addLog("  └ IMPLANT PASSIVE: ${chosenImplant.passiveAbility.title} - ${chosenImplant.passiveAbility.description}", LogType.INFO)
        }
        addLog("INITIALIZING CYBER-SECTOR GRID...", LogType.ALERT)

        loadOrCreateLevel(com.example.data.Zone.BUILDING, 1, 1, 1)
    }

    fun loadOrCreateLevel(
        targetZone: com.example.data.Zone,
        targetFloorOrLevel: Int,
        targetX: Int? = null,
        targetY: Int? = null,
        isAscending: Boolean = true,
        byElevator: Boolean = false
    ) {
        viewModelScope.launch {
            // Trigger Fade-out!
            _uiState.update { it.copy(fadeAlpha = 1f) }
            delay(400) // wait for fade transition

            val state = _uiState.value
            
            // 1. Cache current floor/level data first
            val updatedBuildingFloors = state.buildingFloors.toMutableMap()
            val updatedBuildingExplored = state.buildingExplored.toMutableMap()
            val updatedCollectorsLevels = state.collectorsLevels.toMutableMap()
            val updatedCollectorsExplored = state.collectorsExplored.toMutableMap()
            val updatedCityDistricts = state.cityDistricts.toMutableMap()
            val updatedCityExplored = state.cityExplored.toMutableMap()

            when (state.currentZone) {
                com.example.data.Zone.BUILDING -> {
                    if (state.maze.isNotEmpty()) {
                        updatedBuildingFloors[state.buildingFloor] = state.maze
                        updatedBuildingExplored[state.buildingFloor] = state.exploredCells
                    }
                }
                com.example.data.Zone.COLLECTORS -> {
                    if (state.maze.isNotEmpty()) {
                        updatedCollectorsLevels[state.collectorsLevel] = state.maze
                        updatedCollectorsExplored[state.collectorsLevel] = state.exploredCells
                    }
                }
                com.example.data.Zone.CITY -> {
                    if (state.maze.isNotEmpty()) {
                        updatedCityDistricts[state.cityDistrictIndex] = state.maze
                        updatedCityExplored[state.cityDistrictIndex] = state.exploredCells
                    }
                }
            }

            // 2. Fetch or Generate target floor/level
            var targetMaze: Array<Array<com.example.data.CellType>>? = null
            var targetExplored = emptySet<Pair<Int, Int>>()

            when (targetZone) {
                com.example.data.Zone.BUILDING -> {
                    targetMaze = updatedBuildingFloors[targetFloorOrLevel]
                    targetExplored = updatedBuildingExplored[targetFloorOrLevel] ?: emptySet()
                    if (targetMaze == null || targetMaze.isEmpty()) {
                        targetMaze = GameEngine.generateBuildingFloor(targetFloorOrLevel)
                    }
                }
                com.example.data.Zone.COLLECTORS -> {
                    targetMaze = updatedCollectorsLevels[targetFloorOrLevel]
                    targetExplored = updatedCollectorsExplored[targetFloorOrLevel] ?: emptySet()
                    if (targetMaze == null || targetMaze.isEmpty()) {
                        targetMaze = GameEngine.generateCollectorTunnels(targetFloorOrLevel)
                    }
                }
                com.example.data.Zone.CITY -> {
                    targetMaze = updatedCityDistricts[targetFloorOrLevel]
                    targetExplored = updatedCityExplored[targetFloorOrLevel] ?: emptySet()
                    if (targetMaze == null || targetMaze.isEmpty()) {
                        targetMaze = GameEngine.generateCitySector(targetFloorOrLevel)
                    }
                }
            }

            val finalMaze = targetMaze!!

            // 3. Coordinate positioning
            var finalX = 1
            var finalY = 1

            if (targetX != null && targetY != null) {
                finalX = targetX
                finalY = targetY
            } else if (byElevator) {
                // Find central ELEVATOR cell
                val height = finalMaze.size
                val width = finalMaze[0].size
                val cx = width / 2
                val cy = height / 2
                if (finalMaze[cy][cx] == com.example.data.CellType.ELEVATOR) {
                    finalX = cx
                    finalY = cy
                } else {
                    finalX = 1
                    finalY = 1
                }
            } else {
                // Find logical stairs on the target floor
                val searchType = if (isAscending) {
                    com.example.data.CellType.STAIRS_DOWN // climbing up -> emerge on stairs_down
                } else {
                    com.example.data.CellType.STAIRS_UP // climbing down -> emerge on stairs_up
                }

                var found = false
                for (y in finalMaze.indices) {
                    for (x in finalMaze[0].indices) {
                        if (finalMaze[y][x] == searchType) {
                            finalX = x
                            finalY = y
                            found = true
                            break
                        }
                    }
                    if (found) break
                }
                if (!found) {
                    finalX = 1
                    finalY = 1
                }
            }

            val (perspective, svdagData) = withContext(Dispatchers.Default) {
                val p = GameEngine.render3DPerspective(finalMaze, finalX, finalY, Direction.EAST)
                val svdag = com.example.data.svdag.SvdagWorldBuilder.buildSvdagFrom2DLevel(finalMaze, heightLevels = 16, targetDepth = 6)
                Pair(p, svdag)
            }

            // 4. Update state
            _uiState.update { s ->
                s.copy(
                    currentZone = targetZone,
                    buildingFloor = if (targetZone == com.example.data.Zone.BUILDING) targetFloorOrLevel else s.buildingFloor,
                    collectorsLevel = if (targetZone == com.example.data.Zone.COLLECTORS) targetFloorOrLevel else s.collectorsLevel,
                    cityDistrictIndex = if (targetZone == com.example.data.Zone.CITY) targetFloorOrLevel else s.cityDistrictIndex,
                    maze = finalMaze,
                    gridX = finalX,
                    gridY = finalY,
                    direction = Direction.EAST,
                    perspectiveText = perspective,
                    svdagWorld = svdagData.first,
                    svdagStats = svdagData.second,
                    svdagScaleDepth = 6,
                    exploredCells = targetExplored,
                    buildingFloors = updatedBuildingFloors,
                    buildingExplored = updatedBuildingExplored,
                    collectorsLevels = updatedCollectorsLevels,
                    collectorsExplored = updatedCollectorsExplored,
                    cityDistricts = updatedCityDistricts,
                    cityExplored = updatedCityExplored,
                    level = when (targetZone) {
                        com.example.data.Zone.BUILDING -> targetFloorOrLevel
                        com.example.data.Zone.COLLECTORS -> 4 + targetFloorOrLevel
                        com.example.data.Zone.CITY -> 6 + targetFloorOrLevel
                    }
                )
            }

            revealCellsAround(finalX, finalY)

            // Trigger Fade-in!
            delay(100)
            _uiState.update { it.copy(fadeAlpha = 0f) }
            
            addLog("TRANSITIONED TO: ${targetZone.displayName}, " + when (targetZone) {
                com.example.data.Zone.BUILDING -> {
                    val theme = when (targetFloorOrLevel) {
                        1 -> "Residential"
                        2 -> "Office"
                        3 -> "Technical"
                        4 -> "Storage"
                        else -> "Unknown"
                    }
                    "Floor $targetFloorOrLevel: $theme"
                }
                com.example.data.Zone.COLLECTORS -> "Level $targetFloorOrLevel"
                com.example.data.Zone.CITY -> "Sector $targetFloorOrLevel"
            }, LogType.SUCCESS)
        }
    }

    fun ascendStairs() {
        val state = _uiState.value
        when (state.currentZone) {
            com.example.data.Zone.BUILDING -> {
                if (state.buildingFloor < 4) {
                    val targetFloor = state.buildingFloor + 1
                    val theme = when (targetFloor) {
                        1 -> "Residential"
                        2 -> "Office"
                        3 -> "Technical"
                        4 -> "Storage"
                        else -> "Unknown"
                    }
                    addLog("CLIMBING UPWARD STAIRS TO FLOOR $targetFloor: $theme...", LogType.INFO)
                    loadOrCreateLevel(com.example.data.Zone.BUILDING, targetFloor, isAscending = true)
                } else {
                    addLog("ROOF ARCHITECTURE SEALED. NO FURTHER ASCENSION POSSIBLE.", LogType.ERROR)
                }
            }
            com.example.data.Zone.COLLECTORS -> {
                if (state.collectorsLevel < 2) {
                    addLog("CLIMBING STEEP LADDER TUNNEL TO LEVEL ${state.collectorsLevel + 1}...", LogType.INFO)
                    loadOrCreateLevel(com.example.data.Zone.COLLECTORS, state.collectorsLevel + 1, isAscending = true)
                } else {
                    addLog("TUNNEL CEILING SEALED. PORTAL IS THE ONLY EXIT HERE.", LogType.ERROR)
                }
            }
            com.example.data.Zone.CITY -> {
                addLog("SKY-RISERS CAN ONLY BE ACCESSED VIA LOCAL PORTALS.", LogType.ERROR)
            }
        }
    }

    fun descendStairs() {
        val state = _uiState.value
        when (state.currentZone) {
            com.example.data.Zone.BUILDING -> {
                if (state.buildingFloor > 1) {
                    val targetFloor = state.buildingFloor - 1
                    val theme = when (targetFloor) {
                        1 -> "Residential"
                        2 -> "Office"
                        3 -> "Technical"
                        4 -> "Storage"
                        else -> "Unknown"
                    }
                    addLog("DESCENDING HEAVY REINFORCED METAL STAIRWELL TO FLOOR $targetFloor: $theme...", LogType.INFO)
                    loadOrCreateLevel(com.example.data.Zone.BUILDING, targetFloor, isAscending = false)
                } else {
                    addLog("BASEMENT CONCRETE FLOOR SEALED. CANNOT DESCEND FURTHER.", LogType.ERROR)
                }
            }
            com.example.data.Zone.COLLECTORS -> {
                if (state.collectorsLevel > 1) {
                    addLog("CLIMBING DOWN TO LOWER DRAINAGE SECTOR ${state.collectorsLevel - 1}...", LogType.INFO)
                    loadOrCreateLevel(com.example.data.Zone.COLLECTORS, state.collectorsLevel - 1, isAscending = false)
                } else {
                    addLog("BOTTOM SEDIMENT LEVEL REACHED. NO FURTHER DESCENT.", LogType.ERROR)
                }
            }
            com.example.data.Zone.CITY -> {
                addLog("UNDERGROUND TUNNELS CANNOT BE ACCESSED DIRECTLY FROM THIS DISTRICT PLAZA.", LogType.ERROR)
            }
        }
    }

    fun interactWithElevator() {
        val state = _uiState.value
        if (state.currentZone != com.example.data.Zone.BUILDING) {
            addLog("ELEVATOR: Communication link offline outside the corporate tower.", LogType.ERROR)
            return
        }

        if (!state.hasElevatorKeycard) {
            addLog("ELEVATOR LINK ERROR: Secure Keycard required.", LogType.ERROR)
            addLog("SEARCH THE ROOMS ON FLOOR 2 FOR THE SECURE KEYCARD.", LogType.ALERT)
            return
        }

        val nextFloor = (state.buildingFloor % 4) + 1
        addLog("ELEVATOR: Keycard authenticated. Initiating fast vertical lift to FLOOR $nextFloor...", LogType.SUCCESS)
        loadOrCreateLevel(com.example.data.Zone.BUILDING, nextFloor, byElevator = true)
    }

    // Generates fallback procedural level
    private fun generateNewLevel() {
        viewModelScope.launch {
            val level = _uiState.value.level
            val size = minOf(35 + ((level - 1) * 4), 55)
            val maze = withContext(Dispatchers.Default) {
                GameEngine.generateMaze(size, size, level)
            }
            val multiFloorLevel = withContext(Dispatchers.Default) {
                com.example.data.ProceduralMultiFloorLevelGenerator.generateMultiFloorLevel(
                    levelNumber = level,
                    numFloors = 4,
                    widthPerFloor = 14,
                    heightPerFloor = 14
                )
            }
            val perspective = withContext(Dispatchers.Default) {
                GameEngine.render3DPerspective(maze, 1, 1, Direction.EAST)
            }

            _uiState.update { state ->
                state.copy(
                    maze = maze,
                    currentMultiFloorLevel = multiFloorLevel,
                    activeFloorIndex = 0,
                    gridX = 1,
                    gridY = 1,
                    direction = Direction.EAST,
                    perspectiveText = perspective,
                    exploredCells = emptySet()
                )
            }
            revealCellsAround(1, 1)
        }
    }

    fun generateProceduralMultiFloorLevel(numFloors: Int = 4, width: Int = 14, height: Int = 14) {
        viewModelScope.launch {
            val levelNum = _uiState.value.level
            val multiFloor = withContext(Dispatchers.Default) {
                com.example.data.ProceduralMultiFloorLevelGenerator.generateMultiFloorLevel(
                    levelNumber = levelNum,
                    numFloors = numFloors,
                    widthPerFloor = width,
                    heightPerFloor = height
                )
            }
            _uiState.update { state ->
                val activeGrid = multiFloor.floors.firstOrNull()?.grid ?: state.maze
                state.copy(
                    currentMultiFloorLevel = multiFloor,
                    activeFloorIndex = 0,
                    maze = activeGrid,
                    gridX = multiFloor.spawnPoint.second,
                    gridY = multiFloor.spawnPoint.third
                )
            }
            addLog("LEVEL GENERATION: Multi-Floor ${multiFloor.sectorName} constructed. Reachability 100% verified across ${multiFloor.floors.size} floors.", LogType.SUCCESS)
        }
    }

    fun setActiveFloorIndex(floorIndex: Int) {
        val state = _uiState.value
        val level = state.currentMultiFloorLevel ?: return
        val targetFloor = level.floors.getOrNull(floorIndex) ?: return

        _uiState.update {
            it.copy(
                activeFloorIndex = floorIndex,
                maze = targetFloor.grid
            )
        }
        addLog("SECTOR ELEVATOR: Navigated to ${targetFloor.floorName} [Security Level ${targetFloor.securityLevel}].", LogType.INFO)
    }

    fun navigateVerticalConnector(connector: com.example.data.VerticalConnector) {
        val state = _uiState.value
        val currentFloor = state.activeFloorIndex
        val targetFloorIdx = if (currentFloor == connector.fromFloor) connector.toFloor else connector.fromFloor
        val targetPos = if (currentFloor == connector.fromFloor) connector.toPos else connector.fromPos

        val level = state.currentMultiFloorLevel ?: return
        val targetGridFloor = level.floors.getOrNull(targetFloorIdx) ?: return

        _uiState.update {
            it.copy(
                activeFloorIndex = targetFloorIdx,
                maze = targetGridFloor.grid,
                gridX = targetPos.first,
                gridY = targetPos.second
            )
        }
        addLog("TRANSIT CONNECTED: ${connector.name} used. Transferred to ${targetGridFloor.floorName}.", LogType.SUCCESS)
    }

    fun revealCellsAround(x: Int, y: Int) {
        _uiState.update { state ->
            val updatedRevealed = state.exploredCells.toMutableSet()
            updatedRevealed.add(Pair(x, y))
            
            val radius = 3
            for (dy in -radius..radius) {
                for (dx in -radius..radius) {
                    val nx = x + dx
                    val ny = y + dy
                    if (ny in state.maze.indices && nx in state.maze[0].indices) {
                        if (dx * dx + dy * dy <= radius * radius + 1) {
                            updatedRevealed.add(Pair(nx, ny))
                        }
                    }
                }
            }
            state.copy(exploredCells = updatedRevealed)
        }
    }

    fun addLog(message: String, type: LogType = LogType.INFO) {
        _uiState.update { state ->
            val updatedFeed = state.logFeed.toMutableList()
            updatedFeed.add(0, LogMessage(message, type)) // Prepend to see latest on top
            // Limit to 40 logs to prevent memory clog
            if (updatedFeed.size > 40) {
                updatedFeed.removeAt(updatedFeed.size - 1)
            }
            state.copy(logFeed = updatedFeed)
        }
    }

    // ----------------------------------------------------
    // Movement & Exploration Actions
    // ----------------------------------------------------

    fun moveForward() {
        if (_uiState.value.screen != ActiveScreen.EXPLORATION || _uiState.value.gameState != GameState.EXPLORATION) return

        val state = _uiState.value
        var nextX = state.gridX + state.direction.dx
        var nextY = state.gridY + state.direction.dy

        if (state.activeWeather == com.example.data.CyberWeather.DATA_STORM) {
            if (Random.nextFloat() < 0.40f) {
                val scrambledDirs = Direction.VALUES.filter { it != state.direction }
                val scrambledDir = scrambledDirs.random()
                nextX = state.gridX + scrambledDir.dx
                nextY = state.gridY + scrambledDir.dy
                addLog("⚠️ DATA STORM STATIC: Scrambled movement vector! Redirected forward path.", LogType.ERROR)
            }
        }

        if (isValidMove(nextX, nextY)) {
            val cell = state.maze[nextY][nextX]
            if (cell == CellType.VIRUS_NODE) {
                if (state.gameState != GameState.EXPLORATION) {
                    addLog("ACCESS DENIED: Cannot overlap active threat host. Use Attack program.", LogType.ERROR)
                } else {
                    triggerCombatInline(nextX, nextY)
                }
            } else {
                _uiState.update { it.copy(gridX = nextX, gridY = nextY) }
                updatePerspective()
                revealCellsAround(nextX, nextY)

                // Small chance of recovering 1 RAM during safe navigation
                recoverRamOnMove()

                soundManager.playStepSound()
                addLog("MOVED FORWARD into sub-channel (${nextX}, ${nextY})")
                checkCellTriggers(nextX, nextY, cell)
                processWeatherOnStep()

                update()

                if (_uiState.value.gameState != GameState.EXPLORATION) {
                    executeEnemyCombatTurnInline()
                }
            }
        } else {
            addLog("ACCESS DENIED: Physical Firewall Blocked.", LogType.ERROR)
        }
    }

    fun moveBackward() {
        if (_uiState.value.screen != ActiveScreen.EXPLORATION || _uiState.value.gameState != GameState.EXPLORATION) return

        val state = _uiState.value
        var nextX = state.gridX - state.direction.dx
        var nextY = state.gridY - state.direction.dy

        if (state.activeWeather == com.example.data.CyberWeather.DATA_STORM) {
            if (Random.nextFloat() < 0.40f) {
                val scrambledDirs = Direction.VALUES
                val scrambledDir = scrambledDirs.random()
                nextX = state.gridX + scrambledDir.dx
                nextY = state.gridY + scrambledDir.dy
                addLog("⚠️ DATA STORM STATIC: Scrambled movement vector! Redirected backward path.", LogType.ERROR)
            }
        }

        if (isValidMove(nextX, nextY)) {
            val cell = state.maze[nextY][nextX]
            if (cell == CellType.VIRUS_NODE) {
                if (state.gameState != GameState.EXPLORATION) {
                    addLog("ACCESS DENIED: Cannot overlap active threat host. Use Attack program.", LogType.ERROR)
                } else {
                    triggerCombatInline(nextX, nextY)
                }
            } else {
                _uiState.update { it.copy(gridX = nextX, gridY = nextY) }
                updatePerspective()
                revealCellsAround(nextX, nextY)
                recoverRamOnMove()
                soundManager.playStepSound()
                addLog("MOVED BACKWARD into sub-channel (${nextX}, ${nextY})")
                checkCellTriggers(nextX, nextY, cell)
                processWeatherOnStep()

                update()

                if (_uiState.value.gameState != GameState.EXPLORATION) {
                    executeEnemyCombatTurnInline()
                }
            }
        } else {
            addLog("ACCESS DENIED: Solid Core Boundary.", LogType.ERROR)
        }
    }

    fun turnLeft() {
        if (_uiState.value.screen != ActiveScreen.EXPLORATION || _uiState.value.gameState != GameState.EXPLORATION) return
        _uiState.update { state ->
            val actualDir = if (state.activeWeather == com.example.data.CyberWeather.DATA_STORM && Random.nextFloat() < 0.4f) {
                addLog("⚠️ DATA STORM STATIC: Rotation circuit scrambled!", LogType.ERROR)
                state.direction.turnRight()
            } else {
                state.direction.turnLeft()
            }
            state.copy(direction = actualDir)
        }
        updatePerspective()
        addLog("ROTATED VECTOR 90° LEFT.")
    }

    fun turnRight() {
        if (_uiState.value.screen != ActiveScreen.EXPLORATION || _uiState.value.gameState != GameState.EXPLORATION) return
        _uiState.update { state ->
            val actualDir = if (state.activeWeather == com.example.data.CyberWeather.DATA_STORM && Random.nextFloat() < 0.4f) {
                addLog("⚠️ DATA STORM STATIC: Rotation circuit scrambled!", LogType.ERROR)
                state.direction.turnLeft()
            } else {
                state.direction.turnRight()
            }
            state.copy(direction = actualDir)
        }
        updatePerspective()
        addLog("ROTATED VECTOR 90° RIGHT.")
    }

    fun triggerMapScan() {
        val state = _uiState.value
        if (state.screen != ActiveScreen.EXPLORATION) {
            addLog("SCAN ERROR: Sector Logic Radar only available in exploration mode.", LogType.ERROR)
            return
        }

        val ramCost = 2
        if (state.ram < ramCost) {
            addLog("SCAN FAILED: Insufficient RAM (Requires $ramCost MB RAM).", LogType.ERROR)
            return
        }

        val px = state.gridX
        val py = state.gridY
        val maze = state.maze
        if (maze.isEmpty()) return

        val scanRadius = 8
        val foundEnemies = mutableSetOf<Pair<Int, Int>>()
        val foundLoot = mutableSetOf<Pair<Int, Int>>()
        val scannedCells = mutableSetOf<Pair<Int, Int>>()

        val rowCount = maze.size
        val colCount = maze[0].size

        for (dy in -scanRadius..scanRadius) {
            for (dx in -scanRadius..scanRadius) {
                val nx = px + dx
                val ny = py + dy
                if (nx in 0 until colCount && ny in 0 until rowCount) {
                    if (dx * dx + dy * dy <= scanRadius * scanRadius) {
                        scannedCells.add(Pair(nx, ny))
                        val cell = maze[ny][nx]
                        when (cell) {
                            CellType.VIRUS_NODE -> foundEnemies.add(Pair(nx, ny))
                            CellType.DATA_STORE, CellType.SECRET_CACHE, CellType.ENCRYPTED_PORTAL,
                            CellType.ELEVATOR, CellType.STAIRS_UP, CellType.STAIRS_DOWN -> foundLoot.add(Pair(nx, ny))
                            else -> {}
                        }
                    }
                }
            }
        }

        val updatedExplored = state.exploredCells + scannedCells
        val now = System.currentTimeMillis()

        var svdagSummary: com.example.data.svdag.SvdagScanSummary? = null
        var svdagRipple: com.example.data.svdag.SvdagRippleState? = null

        val currentDag = state.svdagWorld
        if (currentDag != null) {
            val ox = px.coerceIn(0, currentDag.gridSize - 1)
            val oy = py.coerceIn(0, currentDag.gridSize - 1)
            val oz = 1
            svdagSummary = com.example.data.svdag.SvdagScannerService.performSvdagScan(currentDag, ox, oy, oz, radius = scanRadius)
            svdagRipple = com.example.data.svdag.SvdagScannerService.computeRippleState(
                scanTimestamp = now,
                currentTimeMs = now,
                originX = ox.toFloat(),
                originY = oy.toFloat(),
                originZ = oz.toFloat(),
                maxRadius = scanRadius.toFloat(),
                detectedItems = svdagSummary.items
            )
        }

        _uiState.update {
            it.copy(
                ram = (it.ram - ramCost).coerceAtLeast(0),
                isScanActive = true,
                scanTurnsLeft = 6,
                scannedEnemies = foundEnemies,
                scannedLoot = foundLoot,
                exploredCells = updatedExplored,
                scanTimestamp = now,
                svdagScanSummary = svdagSummary,
                svdagRippleState = svdagRipple
            )
        }

        soundManager.playScannerPingSound()
        val detectedCount = if (svdagSummary != null) svdagSummary.interactiveCount else (foundEnemies.size + foundLoot.size)
        val hasSecretVaults = if (svdagSummary != null) svdagSummary.secretCount > 0 else foundLoot.any { pair ->
            maze[pair.second][pair.first] == CellType.SECRET_CACHE
        }
        val hasBypassPaths = if (svdagSummary != null) svdagSummary.alternativePathCount > 0 else false
        soundManager.playScannerDetectionSound(itemCount = detectedCount, hasSecrets = hasSecretVaults, hasBypass = hasBypassPaths)
        addLog("📡 SECTOR SCAN EXECUTED (-$ramCost RAM): Radius $scanRadius sonar pulse active!", LogType.SUCCESS)
        if (svdagSummary != null) {
            addLog("  ↳ SVDAG SCANNER: Found ${svdagSummary.interactiveCount} Interactive, ${svdagSummary.secretCount} Secrets, ${svdagSummary.alternativePathCount} Bypass Paths.", LogType.INFO)
        } else {
            addLog("  ↳ Revealed ${foundEnemies.size} HOSTILE SIGNALS and ${foundLoot.size} LOOT/CACHES on Map HUD.", LogType.INFO)
        }
    }

    private fun recoverRamOnMove() {
        _uiState.update { state ->
            val gained = if (Random.nextInt(100) < 40) 1 else 0
            val newRam = minOf(state.maxRam, state.ram + gained)
            state.copy(ram = newRam)
        }
    }

    fun processWeatherOnStep() {
        viewModelScope.launch(Dispatchers.Default) {
            val pendingLogs = mutableListOf<Pair<String, LogType>>()
            _uiState.update { state ->
                pendingLogs.clear()
                var weather = state.activeWeather
                var turnsLeft = state.weatherTurnsLeft
                var originalMaze = state.originalMaze
                var currentMaze = state.maze
                var scanTurns = state.scanTurnsLeft
                var scanActive = state.isScanActive

                if (scanTurns > 0) {
                    scanTurns--
                    if (scanTurns <= 0) {
                        scanActive = false
                        pendingLogs.add(Pair("📡 RADAR SCAN EXPIRED: Active sonar sweep signal faded.", LogType.INFO))
                    }
                }

                if (weather != com.example.data.CyberWeather.CLEAR) {
                    turnsLeft--
                    if (turnsLeft <= 0) {
                        pendingLogs.add(Pair("WEATHER CLEAR: Environmental distortion dissipated. Bandwidth stabilized.", LogType.SUCCESS))
                        weather = com.example.data.CyberWeather.CLEAR
                        if (originalMaze != null) {
                            currentMaze = originalMaze
                            originalMaze = null
                        }
                    }
                }

                var steps = state.stepsSinceLastEvent + 1
                val nextEvent = state.nextEventSteps
                var predicted = state.predictedWeather

                if (steps >= nextEvent) {
                    steps = 0
                    val newNextEventSteps = 30 + Random.nextInt(71)
                    val possibleWeathers = com.example.data.CyberWeather.VALUES.filter { it != com.example.data.CyberWeather.CLEAR }
                    val newWeather = predicted ?: possibleWeathers.random()
                    predicted = null
                    weather = newWeather
                    turnsLeft = newWeather.effectDuration

                    pendingLogs.add(Pair("⚠️ CYBER-GRID WEATHER ALTERATION: ${newWeather.title}!!", LogType.ALERT))
                    pendingLogs.add(Pair("${newWeather.description}", LogType.INFO))

                    when (newWeather) {
                        com.example.data.CyberWeather.FRAGMENTATION -> {
                            val backup = Array(currentMaze.size) { r -> currentMaze[r].copyOf() }
                            originalMaze = backup
                            
                            val mutableMaze = Array(currentMaze.size) { r -> currentMaze[r].copyOf() }
                            var mutatedCount = 0
                            for (attempt in 0..100) {
                                if (mutatedCount >= 10) break
                                val rx = 1 + Random.nextInt(mutableMaze[0].size - 2)
                                val ry = 1 + Random.nextInt(mutableMaze.size - 2)
                                if (rx == state.gridX && ry == state.gridY) continue
                                if (rx == 1 && ry == 1) continue
                                val originalCell = mutableMaze[ry][rx]
                                if (originalCell == CellType.PATH || originalCell == CellType.WALL) {
                                    mutableMaze[ry][rx] = if (originalCell == CellType.PATH) CellType.WALL else CellType.PATH
                                    mutatedCount++
                                }
                            }
                            currentMaze = mutableMaze
                            pendingLogs.add(Pair("DANGER: Memory Fragmentation is shifting firewall partitions dynamically!", LogType.ALERT))
                        }
                        com.example.data.CyberWeather.ECHOES -> {
                            val mutableMaze = Array(currentMaze.size) { r -> currentMaze[r].copyOf() }
                            var spawned = 0
                            for (attempt in 0..150) {
                                if (spawned >= 4) break
                                val rx = 1 + Random.nextInt(mutableMaze[0].size - 2)
                                val ry = 1 + Random.nextInt(mutableMaze.size - 2)
                                if (rx == state.gridX && ry == state.gridY) continue
                                if (rx == 1 && ry == 1) continue
                                if (mutableMaze[ry][rx] == CellType.PATH) {
                                    mutableMaze[ry][rx] = CellType.ECHO
                                    spawned++
                                }
                            }
                            currentMaze = mutableMaze
                            pendingLogs.add(Pair("ALERT: Sub-sector telemetry streams are bleeding. Ghost netrunner hosts detected.", LogType.ALERT))
                        }
                        com.example.data.CyberWeather.COLD_SPOT -> {
                            pendingLogs.add(Pair("ALERT: System bus temperature critical low. Overclocking modules frozen.", LogType.ALERT))
                        }
                        com.example.data.CyberWeather.HOT_NODE -> {
                            pendingLogs.add(Pair("ALERT: High-voltage core packets discharging. Overclock active, but taking damage!", LogType.ALERT))
                        }
                        com.example.data.CyberWeather.DATA_STORM -> {
                            pendingLogs.add(Pair("ALERT: Dense signal interference static detected. Direction controllers scrambled!", LogType.ALERT))
                        }
                        else -> {}
                    }

                    state.copy(
                        activeWeather = weather,
                        weatherTurnsLeft = turnsLeft,
                        stepsSinceLastEvent = steps,
                        nextEventSteps = newNextEventSteps,
                        predictedWeather = predicted,
                        originalMaze = originalMaze,
                        maze = currentMaze,
                        scanTurnsLeft = scanTurns,
                        isScanActive = scanActive
                    )
                } else {
                    var integrity = state.integrity
                    var ram = state.ram
                    val maxIntegrity = state.maxIntegrity
                    val maxRam = state.maxRam

                    when (weather) {
                        com.example.data.CyberWeather.HOT_NODE -> {
                            val damage = 2
                            integrity = (integrity - damage).coerceAtLeast(1)
                            pendingLogs.add(Pair("HOT NODE OVERHEAT: Core took $damage thermal damage.", LogType.ERROR))
                            if (Random.nextFloat() < 0.4f) {
                                ram = (ram + 1).coerceAtMost(maxRam)
                                pendingLogs.add(Pair("HOT NODE OVERCLOCK: Recovered 1 MB RAM.", LogType.SUCCESS))
                            }
                        }
                        com.example.data.CyberWeather.COLD_SPOT -> {
                            if (Random.nextFloat() < 0.5f) {
                                ram = (ram - 1).coerceAtLeast(0)
                                pendingLogs.add(Pair("COLD SPOT FREEZE: Sluggish bus drained 1 MB RAM.", LogType.ERROR))
                            }
                        }
                        else -> {}
                    }

                    state.copy(
                        activeWeather = weather,
                        weatherTurnsLeft = turnsLeft,
                        stepsSinceLastEvent = steps,
                        integrity = integrity,
                        ram = ram,
                        originalMaze = originalMaze,
                        maze = currentMaze,
                        scanTurnsLeft = scanTurns,
                        isScanActive = scanActive
                    )
                }
            }

            pendingLogs.forEach { (message, type) ->
                addLog(message, type)
            }
        }
    }

    private fun checkCellTriggers(x: Int, y: Int, cell: CellType) {
        when (cell) {
            CellType.DATA_STORE -> {
                addLog("DETECTED: Encrypted Data Store. Initiate [HACK DATA] bypass.", LogType.ALERT)
            }
            CellType.ENCRYPTED_PORTAL -> {
                addLog("PORTAL GATE IN SIGHT. Secure decryption required to cycle sectors.", LogType.SUCCESS)
            }
            CellType.SECRET_CACHE -> {
                addLog("ALERT: Quantum fluctuation detected! You are standing on a Classified Crypt-Cache!", LogType.SUCCESS)
                addLog("Aim ahead or position yourself to INTERACT [F] and extract rewards.", LogType.ALERT)
            }
            CellType.SAFE_ZONE -> {
                if (x != 1 || y != 1) {
                    val healed = minOf(20, _uiState.value.maxIntegrity - _uiState.value.integrity)
                    val ramRestored = minOf(4, _uiState.value.maxRam - _uiState.value.ram)
                    if (healed > 0 || ramRestored > 0) {
                        val updatedMaze = _uiState.value.maze.map { it.clone() }.toTypedArray()
                        updatedMaze[y][x] = CellType.PATH
                        _uiState.update { state ->
                            state.copy(
                                integrity = state.integrity + healed,
                                ram = state.ram + ramRestored,
                                maze = updatedMaze
                            )
                        }
                        updatePerspective()
                        addLog("SAFE HOOK: Connected to Access Point. Integrity +$healed%, RAM +$ramRestored MB.", LogType.SUCCESS)
                    } else {
                        addLog("SAFE HOOK: Subsystems already at peak efficiency. Access Point on standby.", LogType.INFO)
                    }
                } else {
                    addLog("CONNECTED TO SECTOR ACCESS POINT. Starting zone is fully secure.", LogType.INFO)
                }
            }
            CellType.GRAND_HALL -> {
                addLog("GRAND HALL: Entering a monumental core server chamber with towering concrete columns.", LogType.INFO)
            }
            CellType.DOME_CHAMBER -> {
                addLog("DOME VAULT: Stepping into a high-security spherical dome vault with dynamic holographic ribs.", LogType.INFO)
            }
            CellType.VENT_TUNNEL -> {
                addLog("VENT CONDUIT: Crouching through a low, heavily shielded utility ventilation shaft.", LogType.INFO)
            }
            CellType.ELEVATED_BALCONY -> {
                addLog("BALCONY LEDGE: Elevated high overlook platforms. You have an expanded line of sight!", LogType.INFO)
            }
            CellType.STAIRS_UP -> {
                addLog("STAIRS UP: Stand here and press INTERACT [F] to ascend.", LogType.INFO)
            }
            CellType.STAIRS_DOWN -> {
                addLog("STAIRS DOWN: Stand here and press INTERACT [F] to descend.", LogType.INFO)
            }
            CellType.ELEVATOR -> {
                addLog("ELEVATOR COLUMN: Stand here and press INTERACT [F] to activate vertical transport.", LogType.ALERT)
            }
            CellType.GRAVITY_SLOPE -> {
                addLog("GRAVITY SLOPE: Scaling a steep gravity-modulated concourse incline.", LogType.INFO)
            }
            CellType.ECHO -> {
                val r = Random.nextFloat()
                val updatedMaze = _uiState.value.maze.map { it.clone() }.toTypedArray()
                updatedMaze[y][x] = CellType.PATH

                _uiState.update { state -> state.copy(maze = updatedMaze) }
                updatePerspective()

                if (r < 0.45f) {
                    addLog("PHANTOM ECHO DISSIPATED: The telemetry ghost dissolved into cold code static...", LogType.INFO)
                } else if (r < 0.75f) {
                    val creditsGained = 40 + Random.nextInt(41)
                    _uiState.update { state ->
                        state.copy(
                            credits = state.credits + creditsGained,
                            totalCreditsEarned = state.totalCreditsEarned + creditsGained
                        )
                    }
                    addLog("PHANTOM ECHO DECRYPTED: Reclaimed $creditsGained credits from a decayed core cache!", LogType.SUCCESS)
                } else {
                    addLog("PHANTOM ECHO RE-ARMED: The decoy ghost hardened into an active Security Process!", LogType.ALERT)
                    triggerCombatInline(x, y)
                }
            }
            else -> {}
        }
    }

    fun update() {
        val state = _uiState.value
        if (state.screen != ActiveScreen.EXPLORATION) return
        if (state.gameState != GameState.EXPLORATION) {
            // Просто оновлюємо AI ворога, якщо він є
            if (state.activeEnemy != null) {
                runRealTimeCombatTick()
            }
            return
        }

        val playerX = state.gridX
        val playerY = state.gridY
        val maze = state.maze
        if (maze.isEmpty()) return

        var foundEnemy: Pair<Int, Int>? = null
        val radius = 2
        for (dy in -radius..radius) {
            for (dx in -radius..radius) {
                val nx = playerX + dx
                val ny = playerY + dy
                if (ny in maze.indices && nx in maze[0].indices) {
                    if (maze[ny][nx] == CellType.VIRUS_NODE) {
                        foundEnemy = Pair(nx, ny)
                        break
                    }
                }
            }
            if (foundEnemy != null) break
        }

        if (state.gameState == GameState.EXPLORATION) {
            if (foundEnemy != null) {
                triggerCombatInline(foundEnemy.first, foundEnemy.second)
                return
            }

            // --- ADVANCED VERTICALITY ENEMY AI TICK ---
            // Triggers every 1.5 seconds during exploration
            aiTickCounter++
            if (aiTickCounter >= 15) {
                aiTickCounter = 0
                runEnemyAITick(playerX, playerY, maze, state)
            }
        } else {
            if (state.activeEnemy == null) {
                _uiState.update { it.copy(gameState = GameState.EXPLORATION) }
                addLog("COMBAT CONFLICT RESOLVED: SYSTEM REVERTED TO EXPLORATION MODES.", LogType.SUCCESS)
            } else {
                val dist = Math.max(Math.abs(playerX - state.targetNodeX), Math.abs(playerY - state.targetNodeY))
                if (dist >= 3) {
                    _uiState.update { it.copy(gameState = GameState.EXPLORATION, activeEnemy = null) }
                    addLog("OUT OF RANGE. ESCAPED HOSTILE RADAR. BACK TO EXPLORATION.", LogType.ALERT)
                } else {
                    if (state.isCombatInputEnabled && state.gameState != GameState.COMBAT_START) {
                        runRealTimeCombatTick()
                    }
                }
            }
        }
    }

    private var realTimeTickCounter = 0

    private fun runRealTimeCombatTick() {
        val state = _uiState.value
        val enemy = state.activeEnemy ?: return

        // 1. Decrement player action cooldowns
        val newAttackCooldown = (state.attackCooldown - 1).coerceAtLeast(0)
        val newDefendCooldown = (state.defendCooldown - 1).coerceAtLeast(0)
        val newFirewallTimeLeft = (state.activeFirewallTimeLeft - 1).coerceAtLeast(0)

        val updatedProgCooldowns = state.programCooldowns.mapValues { it.value - 1 }
            .filterValues { it > 0 }

        // 2. RAM Recovery in real-time during combat!
        // Every 1.0 seconds (10 ticks), player recovers RAM recovery rate
        realTimeTickCounter++
        var newRam = state.ram
        if (realTimeTickCounter >= 10) {
            realTimeTickCounter = 0
            newRam = minOf(state.maxRam, state.ram + state.ramRecoveryRate)
        }

        // 3. Enemy Attack Charge Update
        // Enemy charges their attack in real-time. Charge rate depends on level/speed.
        val chargeDelta = 0.04f + (state.level * 0.005f)
        var newCharge = state.enemyAttackCharge + chargeDelta
        var enemyFired = false

        if (newCharge >= 1.0f) {
            newCharge = 0.0f
            enemyFired = true
        }

        _uiState.update { stateNow ->
            stateNow.copy(
                attackCooldown = newAttackCooldown,
                defendCooldown = newDefendCooldown,
                activeFirewallTimeLeft = newFirewallTimeLeft,
                programCooldowns = updatedProgCooldowns,
                ram = newRam,
                enemyAttackCharge = newCharge
            )
        }

        // 4. Handle Enemy Attack Execution
        if (enemyFired) {
            executeEnemyAttackRealTime(enemy)
        }
    }

    private fun executeEnemyAttackRealTime(enemy: Enemy) {
        val state = _uiState.value
        
        val actions = listOf(
            "Trojan injection stream",
            "Rootkit port scan exploit",
            "Distributed Denial-of-Service packets",
            "Logic logicbomb payload"
        )
        val selectedAction = actions[Random.nextInt(actions.size)]
        var baseEnemyDmg = enemy.damage + Random.nextInt(-2, 3)
        if (baseEnemyDmg < 2) baseEnemyDmg = 2

        // Gravity Slope check (Evasion boost reduces enemy hit intensity by 30%)
        val standCell = state.maze.getOrNull(state.gridY)?.getOrNull(state.gridX)
        if (standCell == com.example.data.CellType.GRAVITY_SLOPE) {
            baseEnemyDmg = (baseEnemyDmg * 0.70f).toInt().coerceAtLeast(1)
            addLog("✨ GRAVITY EVASION: Magnetic slope rapid momentum absorbed 30% of incoming packet force!", LogType.SUCCESS)
        }

        // Active Firewall Shield reduction: if active, reduce damage by 75%!
        val isFirewallActive = state.activeFirewallTimeLeft > 0
        val finalEnemyDmg = if (isFirewallActive) {
            val reduced = (baseEnemyDmg * 0.25f).toInt()
            addLog("🛡️ FIREWALL ACTIVE: Blocked 75% of incoming cyber payload!", LogType.SUCCESS)
            reduced.coerceAtLeast(1)
        } else {
            maxOf(1, baseEnemyDmg - state.defenseBonus)
        }

        val currentShield = state.playerShield
        val shieldDamage = minOf(currentShield, finalEnemyDmg)
        val remainingShield = currentShield - shieldDamage
        val integrityDamage = finalEnemyDmg - shieldDamage
        val newPlayerIntegrity = maxOf(0, state.integrity - integrityDamage)

        _uiState.update { stateNow ->
            stateNow.copy(
                integrity = newPlayerIntegrity,
                playerShield = remainingShield,
                enemyCombatAction = "${enemy.name} ran $selectedAction: Dealt $finalEnemyDmg damage. (Shield absorbed: $shieldDamage, Core hit: $integrityDamage)",
                combatFlashPlayer = true,
                combatScreenShake = true,
                playerDamagePopup = "-$finalEnemyDmg HP"
            )
        }

        addLog("💥 INCOMING THREAT: ${enemy.name} executes $selectedAction!", LogType.ERROR)
        if (shieldDamage > 0) {
            addLog("Player Shield absorbed $shieldDamage damage.", LogType.ALERT)
        }
        if (integrityDamage > 0) {
            addLog("System Integrity degraded by $integrityDamage%.", LogType.ERROR)
        }

        viewModelScope.launch {
            kotlinx.coroutines.delay(400)
            _uiState.update { it.copy(combatFlashPlayer = false, combatScreenShake = false, playerDamagePopup = null) }
        }

        if (newPlayerIntegrity <= 0) {
            _uiState.update { it.copy(showCombatBanner = "💀 DEFEAT") }
            viewModelScope.launch {
                kotlinx.coroutines.delay(1200)
                handleGameOver("Destroyed by security process ${enemy.name}")
                _uiState.update { it.copy(showCombatBanner = null) }
            }
        }
    }

    private fun runEnemyAITick(
        playerX: Int,
        playerY: Int,
        maze: Array<Array<CellType>>,
        state: GameUiState
    ) {
        val currentFloorKey = when (state.currentZone) {
            com.example.data.Zone.BUILDING -> "BUILDING_${state.buildingFloor}"
            com.example.data.Zone.COLLECTORS -> "COLLECTORS_${state.collectorsLevel}"
            com.example.data.Zone.CITY -> "CITY_${state.cityDistrictIndex}"
        }

        val height = maze.size
        val width = maze[0].size
        val clonedMaze = Array(height) { y -> Array(width) { x -> maze[y][x] } }

        val originalEnemyCoords = mutableListOf<Pair<Int, Int>>()
        for (y in 0 until height) {
            for (x in 0 until width) {
                if (clonedMaze[y][x] == CellType.VIRUS_NODE) {
                    originalEnemyCoords.add(Pair(x, y))
                }
            }
        }

        var mazeModified = false

        for (enemyCoord in originalEnemyCoords) {
            val ex = enemyCoord.first
            val ey = enemyCoord.second

            if (clonedMaze[ey][ex] != CellType.VIRUS_NODE) continue

            val dx = Math.abs(playerX - ex)
            val dy = Math.abs(playerY - ey)
            val dist = Math.max(dx, dy)

            val originalKey = "${currentFloorKey}_${ex}_${ey}"
            val currentUnderlyingType = underlyingCellTypes[originalKey] ?: CellType.PATH

            // 1. Balcony & Gallery Vantage Sniping AI
            if (currentUnderlyingType == CellType.ELEVATED_BALCONY && dist <= 3) {
                val shieldDamage = minOf(state.playerShield, kotlin.random.Random.nextInt(2, 5))
                val integrityDamage = if (shieldDamage < 4) (kotlin.random.Random.nextInt(2, 5) - shieldDamage).coerceAtLeast(0) else 0
                val totalDmg = shieldDamage + integrityDamage
                if (totalDmg > 0) {
                    _uiState.update { s ->
                        s.copy(
                            playerShield = maxOf(0, s.playerShield - shieldDamage),
                            integrity = maxOf(0, s.integrity - integrityDamage)
                        )
                    }
                    addLog("⚠️ GALLERY SNIPER: Hostile process at ($ex, $ey) sniped you from the elevated gallery! Dealt $totalDmg static damage.", LogType.ALERT)
                    if (_uiState.value.integrity <= 0) {
                        handleGameOver("Destroyed by remote gallery sniper")
                        return
                    }
                }
                continue // Remain in high vantage balcony for sniping
            }

            // 2. Vertical Elevator / Stairs Transiting AI
            if (state.currentZone == com.example.data.Zone.BUILDING &&
                (currentUnderlyingType == CellType.ELEVATOR ||
                 currentUnderlyingType == CellType.STAIRS_UP ||
                 currentUnderlyingType == CellType.STAIRS_DOWN) &&
                kotlin.random.Random.nextFloat() < 0.20f) {
                
                val destFloor = if (currentUnderlyingType == CellType.STAIRS_UP && state.buildingFloor < 4) {
                    state.buildingFloor + 1
                } else if (currentUnderlyingType == CellType.STAIRS_DOWN && state.buildingFloor > 1) {
                    state.buildingFloor - 1
                } else if (currentUnderlyingType == CellType.ELEVATOR) {
                    val otherFloors = (1..4).filter { it != state.buildingFloor }
                    otherFloors[kotlin.random.Random.nextInt(otherFloors.size)]
                } else {
                    null
                }

                if (destFloor != null) {
                    val destFloorMaze = state.buildingFloors[destFloor]
                    if (destFloorMaze != null && destFloorMaze.isNotEmpty()) {
                        var targetSpawn: Pair<Int, Int>? = null
                        for (ty in destFloorMaze.indices) {
                            for (tx in destFloorMaze[0].indices) {
                                if (destFloorMaze[ty][tx] == currentUnderlyingType) {
                                    for (sdir in listOf(Pair(-1,0), Pair(1,0), Pair(0,-1), Pair(0,1))) {
                                        val sx = tx + sdir.first
                                        val sy = ty + sdir.second
                                        if (sy in destFloorMaze.indices && sx in destFloorMaze[0].indices &&
                                            destFloorMaze[sy][sx] == CellType.PATH) {
                                            targetSpawn = Pair(sx, sy)
                                            break
                                        }
                                    }
                                }
                                if (targetSpawn != null) break
                            }
                            if (targetSpawn != null) break
                        }

                        if (targetSpawn != null) {
                            clonedMaze[ey][ex] = currentUnderlyingType
                            underlyingCellTypes.remove(originalKey)

                            val updatedDestMaze = Array(destFloorMaze.size) { y -> Array(destFloorMaze[0].size) { x -> destFloorMaze[y][x] } }
                            updatedDestMaze[targetSpawn.second][targetSpawn.first] = CellType.VIRUS_NODE
                            
                            val destKey = "BUILDING_${destFloor}_${targetSpawn.first}_${targetSpawn.second}"
                            underlyingCellTypes[destKey] = CellType.PATH

                            val updatedFloorsMap = state.buildingFloors.toMutableMap()
                            updatedFloorsMap[destFloor] = updatedDestMaze
                            _uiState.update { s -> s.copy(buildingFloors = updatedFloorsMap) }

                            addLog("⚠️ SECTOR WARNING: Hostile process migrated through vertical shafts to FLOOR $destFloor!", LogType.ALERT)
                            mazeModified = true
                            continue
                        }
                    }
                }
            }

            // 3. Movement pathfinding (normal patrol or gravity assist slope dash)
            val isOnSlope = currentUnderlyingType == CellType.GRAVITY_SLOPE
            val maxSteps = if (isOnSlope && dist <= 5) 2 else 1

            var currentEx = ex
            var currentEy = ey

            for (step in 1..maxSteps) {
                val neighbors = listOf(
                    Pair(currentEx - 1, currentEy),
                    Pair(currentEx + 1, currentEy),
                    Pair(currentEx, currentEy - 1),
                    Pair(currentEx, currentEy + 1)
                ).filter { (nx, ny) ->
                    ny in 0 until height && nx in 0 until width &&
                    clonedMaze[ny][nx] != CellType.WALL &&
                    clonedMaze[ny][nx] != CellType.VIRUS_NODE &&
                    clonedMaze[ny][nx] != CellType.DATA_STORE &&
                    clonedMaze[ny][nx] != CellType.SECRET_CACHE &&
                    clonedMaze[ny][nx] != CellType.SAFE_ZONE
                }

                if (neighbors.isEmpty()) break

                val nextPos = if (dist <= 4) {
                    neighbors.minByOrNull { (nx, ny) ->
                        val ndx = Math.abs(playerX - nx)
                        val ndy = Math.abs(playerY - ny)
                        Math.max(ndx, ndy)
                    }
                } else {
                    neighbors[kotlin.random.Random.nextInt(neighbors.size)]
                }

                if (nextPos != null) {
                    val nx = nextPos.first
                    val ny = nextPos.second

                    val destKey = "${currentFloorKey}_${nx}_${ny}"
                    if (!underlyingCellTypes.containsKey(destKey)) {
                        underlyingCellTypes[destKey] = clonedMaze[ny][nx]
                    }

                    val srcKey = "${currentFloorKey}_${currentEx}_${currentEy}"
                    val srcUnderlying = underlyingCellTypes[srcKey] ?: CellType.PATH

                    clonedMaze[currentEy][currentEx] = srcUnderlying
                    underlyingCellTypes.remove(srcKey)

                    clonedMaze[ny][nx] = CellType.VIRUS_NODE
                    
                    if (isOnSlope && step == 1) {
                        addLog("⚠️ GRAVITY DASH: Security process at ($currentEx, $currentEy) charged down the gravity ramp!", LogType.ALERT)
                    }

                    currentEx = nx
                    currentEy = ny
                    mazeModified = true
                } else {
                    break
                }
            }
        }

        if (mazeModified) {
            _uiState.update { s -> s.copy(maze = clonedMaze) }
            updatePerspective()
        }
    }

    fun triggerCombatInline(targetX: Int, targetY: Int) {
        if (_uiState.value.gameState != GameState.EXPLORATION) {
            addLog("⚠️ ALREADY IN COMBAT: Cannot initiate new engagement.", LogType.ALERT)
            return
        }
        val level = _uiState.value.level
        val enemy = GameEngine.spawnEnemy(level)

        _uiState.update { state ->
            val baseCombatShield = if (state.runnerClass == NetrunnerClass.CYBER_SHIELD) {
                minOf(state.playerMaxShield, state.playerShield + 30)
            } else {
                state.playerShield
            }
            state.copy(
                gameState = GameState.COMBAT_START,
                activeEnemy = enemy,
                playerShield = baseCombatShield,
                targetNodeX = targetX,
                targetNodeY = targetY,
                enemyCombatAction = "",
                combatTurn = CombatTurn.PLAYER,
                combatRound = 1,
                turnPhase = TurnPhase.PLAYER_INPUT,
                playerActionHistory = emptyList(),
                enemyTurnHistory = emptyList(),
                allTurnActions = emptyList(),
                lastPlayerActionRecord = null,
                lastEnemyActionRecord = null,
                totalPlayerActionsCount = 0,
                totalEnemyTurnsCount = 0,
                showCombatBanner = "⚔️ SYSTEM OVERLOAD INTRUSION",
                isCombatInputEnabled = false,
                combatFlashEnemy = false,
                combatFlashPlayer = false,
                combatScreenShake = false,
                playerDamagePopup = null,
                enemyDamagePopup = null,
                showShieldEffect = false,
                enemyAttackCharge = 0f,
                activeFirewallTimeLeft = 0,
                playerStatusEffects = emptyList(),
                enemyStatusEffects = enemy.statusEffects.toList(),
                defendCooldown = 0,
                attackCooldown = 0,
                programCooldowns = emptyMap()
            )
        }

        addLog("==========================================", LogType.ERROR)
        addLog("⚠️ SECURITY INTRUSION THREAT TRIGGERED: ${enemy.name}!", LogType.ERROR)
        addLog("DESCRIPTION: ${enemy.description}", LogType.ALERT)
        addLog("SYSTEM DETECTED RECALIBRATION: INITIATING REAL-TIME SHOCK COMBAT.", LogType.INFO)

        if (_uiState.value.runnerClass == NetrunnerClass.CYBER_SHIELD) {
            addLog("SENTINEL PROTOCOL: +30 Shield initialized.", LogType.SUCCESS)
        }

        viewModelScope.launch {
            kotlinx.coroutines.delay(1200)
            _uiState.update { it.copy(showCombatBanner = null, isCombatInputEnabled = true, gameState = GameState.PLAYER_TURN, turnPhase = TurnPhase.PLAYER_INPUT) }
        }
    }

    // ----------------------------------------------------
    // Turn-Based Game Loop & Action Tracking System
    // ----------------------------------------------------

    fun recordPlayerAction(
        actionType: CombatActionType,
        summary: String,
        damageDealt: Int = 0,
        shieldAbsorbed: Int = 0,
        healAmount: Int = 0,
        isCrit: Boolean = false,
        isMiss: Boolean = false,
        statusApplied: String? = null
    ): TurnActionRecord {
        val currentRound = _uiState.value.combatRound
        val record = TurnActionRecord(
            roundNumber = currentRound,
            actorName = if (_uiState.value.runnerName.isNotBlank()) _uiState.value.runnerName else "Player",
            isPlayer = true,
            actionType = actionType,
            summary = summary,
            damageDealt = damageDealt,
            shieldAbsorbed = shieldAbsorbed,
            healAmount = healAmount,
            isCrit = isCrit,
            isMiss = isMiss,
            statusApplied = statusApplied
        )

        _uiState.update { state ->
            val updatedPlayerHistory = state.playerActionHistory + record
            val updatedAllActions = state.allTurnActions + record
            state.copy(
                playerActionHistory = updatedPlayerHistory,
                allTurnActions = updatedAllActions,
                lastPlayerActionRecord = record,
                totalPlayerActionsCount = state.totalPlayerActionsCount + 1,
                turnPhase = TurnPhase.PLAYER_RESOLVING
            )
        }
        return record
    }

    fun recordEnemyAction(
        actionType: CombatActionType,
        summary: String,
        damageDealt: Int = 0,
        shieldAbsorbed: Int = 0,
        healAmount: Int = 0,
        isCrit: Boolean = false,
        isMiss: Boolean = false,
        statusApplied: String? = null
    ): TurnActionRecord {
        val currentRound = _uiState.value.combatRound
        val enemyName = _uiState.value.activeEnemy?.name ?: "Hostile Entity"
        val record = TurnActionRecord(
            roundNumber = currentRound,
            actorName = enemyName,
            isPlayer = false,
            actionType = actionType,
            summary = summary,
            damageDealt = damageDealt,
            shieldAbsorbed = shieldAbsorbed,
            healAmount = healAmount,
            isCrit = isCrit,
            isMiss = isMiss,
            statusApplied = statusApplied
        )

        _uiState.update { state ->
            val updatedEnemyHistory = state.enemyTurnHistory + record
            val updatedAllActions = state.allTurnActions + record
            state.copy(
                enemyTurnHistory = updatedEnemyHistory,
                allTurnActions = updatedAllActions,
                lastEnemyActionRecord = record,
                totalEnemyTurnsCount = state.totalEnemyTurnsCount + 1,
                turnPhase = TurnPhase.ENEMY_RESOLVING
            )
        }
        return record
    }

    fun processTurnMaintenance() {
        _uiState.update { state ->
            val nextRound = state.combatRound + 1
            val regenRam = minOf(state.maxRam, state.ram + state.ramRecoveryRate)
            state.copy(
                combatRound = nextRound,
                ram = regenRam,
                defenseBonus = 0,
                activeFirewallTimeLeft = 0,
                turnPhase = TurnPhase.PLAYER_INPUT,
                combatTurn = CombatTurn.PLAYER,
                isCombatInputEnabled = true,
                gameState = GameState.PLAYER_TURN
            )
        }
    }

    // ----------------------------------------------------
    // Status Effect System
    // ----------------------------------------------------

    fun applyStatusEffectToPlayer(type: com.example.data.StatusEffectType, turns: Int, magnitude: Int = 0, source: String = "") {
        val newEffect = com.example.data.ActiveStatusEffect(
            type = type,
            turnsRemaining = turns,
            magnitude = magnitude,
            sourceName = source
        )
        _uiState.update { state ->
            val updated = state.playerStatusEffects.filter { it.type != type }.toMutableList()
            updated.add(newEffect)
            state.copy(playerStatusEffects = updated)
        }
        addLog("${type.icon} STATUS EFFECT INFLICTED ON PLAYER: ${type.displayName} (${turns}t)! ${type.description}", LogType.ALERT)
    }

    fun applyStatusEffectToEnemy(type: com.example.data.StatusEffectType, turns: Int, magnitude: Int = 0, source: String = "") {
        val enemy = _uiState.value.activeEnemy ?: return
        val newEffect = com.example.data.ActiveStatusEffect(
            type = type,
            turnsRemaining = turns,
            magnitude = magnitude,
            sourceName = source
        )
        val updatedEffects = enemy.statusEffects.filter { it.type != type }.toMutableList()
        updatedEffects.add(newEffect)
        enemy.statusEffects = updatedEffects

        _uiState.update { state ->
            state.copy(enemyStatusEffects = updatedEffects.toList())
        }
        addLog("${type.icon} STATUS EFFECT APPLIED TO ${enemy.name.uppercase()}: ${type.displayName} (${turns}t)! ${type.description}", LogType.SUCCESS)
    }

    private fun processPlayerTurnStatusEffects(): Boolean {
        val state = _uiState.value
        val activeEffects = state.playerStatusEffects
        if (activeEffects.isEmpty()) return false

        var isPlayerStunned = false
        val remainingEffects = mutableListOf<com.example.data.ActiveStatusEffect>()

        for (effect in activeEffects) {
            when (effect.type) {
                com.example.data.StatusEffectType.POISONED -> {
                    val dotDamage = if (effect.magnitude > 0) effect.magnitude else 8
                    val newIntegrity = maxOf(0, state.integrity - dotDamage)
                    _uiState.update { it.copy(integrity = newIntegrity, playerDamagePopup = "-$dotDamage HP (Corroded)") }
                    addLog("🧪 CORROSION TICK: System integrity damaged by $dotDamage points!", LogType.ERROR)
                }
                com.example.data.StatusEffectType.STUNNED -> {
                    isPlayerStunned = true
                    addLog("⚡ SYSTEM STUNNED: Circuit overload paralyzes action controls!", LogType.ALERT)
                }
                else -> {}
            }

            val nextTurns = effect.turnsRemaining - 1
            if (nextTurns > 0) {
                remainingEffects.add(effect.copy(turnsRemaining = nextTurns))
            } else {
                addLog("✨ EXPIRED STATUS: ${effect.type.displayName} effect on player faded.", LogType.INFO)
            }
        }

        _uiState.update { it.copy(playerStatusEffects = remainingEffects) }
        return isPlayerStunned
    }

    fun setCombatStyle(style: String) {
        _uiState.update { it.copy(selectedCombatStyle = "Strike") }
        addLog("COMBAT STANCE: Single unified Strike stance active.", LogType.INFO)
    }

    fun combatAttack() {
        if (!_uiState.value.isCombatInputEnabled) return
        val state = _uiState.value
        val enemy = state.activeEnemy ?: return

        // Check if player is stunned
        if (processPlayerTurnStatusEffects()) {
            addLog("⚡ TURN SKIPPED: Player is STUNNED!", LogType.ALERT)
            recordPlayerAction(
                actionType = CombatActionType.PASS,
                summary = "Turn skipped due to STUN effect"
            )
            executeEnemyCombatTurnInline()
            return
        }

        // Start weapon swing animation in UI thread
        viewModelScope.launch {
            _uiState.update { it.copy(weaponSwingProgress = 0.2f, weaponSwingType = "Strike") }
            delay(60)
            _uiState.update { it.copy(weaponSwingProgress = 0.7f) }
            delay(60)
            _uiState.update { it.copy(weaponSwingProgress = 1.0f) }
            delay(100)
            _uiState.update { it.copy(weaponSwingProgress = 0.5f) }
            delay(60)
            _uiState.update { it.copy(weaponSwingProgress = 0f) }
        }

        viewModelScope.launch {
            addLog("> Striking with ${state.equippedWeaponName}...", LogType.INFO)

            // Hit chance calculation
            val baseHitChance = 75
            // Add level bonus & luck/agility-like RAM factor
            val hitBonus = (state.level * 2) + (state.ram * 1)
            val finalHitChance = (baseHitChance + hitBonus).coerceIn(20, 95)
            val roll = Random.nextInt(100)

            if (roll >= finalHitChance) {
                // MISS!
                addLog("⚔️ MISS! Your weapon swung wide. [Rolled: $roll vs Chance: $finalHitChance%]", LogType.ALERT)
                recordPlayerAction(
                    actionType = CombatActionType.STRIKE,
                    summary = "Strike swung wide and missed",
                    isMiss = true
                )
                _uiState.update { stateNow ->
                    stateNow.copy(
                        enemyDamagePopup = "MISS",
                        combatFlashEnemy = false
                    )
                }
                delay(400)
                _uiState.update { it.copy(enemyDamagePopup = null) }
                // Trigger enemy combat turn on player action completion
                executeEnemyCombatTurnInline()
                return@launch
            }

            // HIT! Calculate Damage
            val baseDmg = 18
            val statPower = (state.level * 2) + state.damageBonus
            var rawPlayerDamage = baseDmg + statPower

            // Apply Status Effect multipliers on Player
            val isOverclocked = state.playerStatusEffects.any { it.type == com.example.data.StatusEffectType.BUFFED }
            val isGlitched = state.playerStatusEffects.any { it.type == com.example.data.StatusEffectType.WEAKENED }
            if (isOverclocked) {
                rawPlayerDamage = (rawPlayerDamage * 1.5f).toInt()
                addLog("🔥 OVERCLOCKED: Attack payload amplified by 50%!", LogType.SUCCESS)
            }
            if (isGlitched) {
                rawPlayerDamage = (rawPlayerDamage * 0.5f).toInt()
                addLog("🌀 GLITCHED: Attack output reduced by 50%!", LogType.ALERT)
            }

            // Implant passive abilities
            val hasSynapticOverclock = state.installedImplants.values.any { it?.passiveAbility == com.example.data.ImplantAbility.SYNAPTIC_OVERCLOCK }
            if (hasSynapticOverclock && state.ram < 3) {
                rawPlayerDamage = (rawPlayerDamage * 1.25f).toInt()
                addLog("🔥 SYNAPTIC OVERCLOCK IMPLANT: +25% payload damage boosted by low RAM threshold!", LogType.SUCCESS)
            }

            // Balcony Vantage: +25% attack damage bonus
            val standCell = state.maze.getOrNull(state.gridY)?.getOrNull(state.gridX)
            if (standCell == com.example.data.CellType.ELEVATED_BALCONY) {
                rawPlayerDamage = (rawPlayerDamage * 1.25f).toInt()
                addLog("✨ BALCONY VANTAGE ACTIVE: Swing amplified from balcony overlooking!", LogType.SUCCESS)
            }

            // Crit checks
            var isCrit = false
            val critRate = 10 + (state.ram * 2)
            val hasCritTargeting = state.installedImplants.values.any { it?.passiveAbility == com.example.data.ImplantAbility.CRIT_TARGETING }
            val implantCritBonus = if (hasCritTargeting) 20 else 0
            val finalCritRate = (if (state.runnerClass == NetrunnerClass.CODE_SLASHER) critRate + 25 else critRate) + implantCritBonus
            if (Random.nextInt(100) < finalCritRate) {
                isCrit = true
                val critMultiplier = if (state.runnerClass == NetrunnerClass.CODE_SLASHER) 2.0f else 1.5f
                rawPlayerDamage = (rawPlayerDamage * critMultiplier).toInt()
            }

            val enemyArmor = enemy.armor
            val effectiveArmor = if (isCrit) (enemyArmor * 0.5f).toInt() else enemyArmor
            var finalDmg = maxOf(3, rawPlayerDamage - effectiveArmor)

            // Enemy Fortified status check
            val isEnemyFortified = enemy.statusEffects.any { it.type == com.example.data.StatusEffectType.FORTIFIED }
            if (isEnemyFortified) {
                finalDmg = (finalDmg * 0.5f).toInt().coerceAtLeast(1)
                addLog("🛡️ HOSTILE FORTIFIED: Damage absorbed by enemy defense grid (-50%).", LogType.ALERT)
            }

            val enemyRemShield = maxOf(0, enemy.shield - finalDmg)
            val shieldDmg = enemy.shield - enemyRemShield
            val bodyDmg = finalDmg - shieldDmg
            val enemyRemIntegrity = maxOf(0, enemy.integrity - bodyDmg)

            enemy.shield = enemyRemShield
            enemy.integrity = enemyRemIntegrity

            recordPlayerAction(
                actionType = CombatActionType.STRIKE,
                summary = "Strike dealt $finalDmg damage (Shield: -$shieldDmg, HP: -$bodyDmg)",
                damageDealt = finalDmg,
                shieldAbsorbed = shieldDmg,
                isCrit = isCrit
            )

            if (isCrit) {
                soundManager.playCombatCritSound()
                addLog("💥 CRITICAL HIT! Double damage bypassed ${effectiveArmor} hostile armor!", LogType.SUCCESS)
            } else {
                soundManager.playCombatHitSound()
            }

            addLog("⚔️ HIT! Dealt ${finalDmg} damage to ${enemy.name} (Shield: -${shieldDmg}, HP: -${bodyDmg}) [Roll: $roll vs Chance: $finalHitChance%]", LogType.SUCCESS)

            _uiState.update { stateNow ->
                stateNow.copy(
                    combatFlashEnemy = true,
                    enemyDamagePopup = "-$finalDmg HP"
                )
            }

            delay(400)
            _uiState.update { it.copy(combatFlashEnemy = false, enemyDamagePopup = null) }

            if (enemy.integrity <= 0) {
                _uiState.update { it.copy(showCombatBanner = "🏆 VICTORY", isCombatInputEnabled = false, turnPhase = TurnPhase.COMBAT_VICTORY) }
                delay(1200)
                handleCombatVictoryInline(enemy)
                _uiState.update { it.copy(showCombatBanner = null, isCombatInputEnabled = true) }
            } else {
                // Trigger enemy combat turn on player action completion
                executeEnemyCombatTurnInline()
            }
        }
    }

    fun combatDefend() {
        if (!_uiState.value.isCombatInputEnabled) return
        val state = _uiState.value

        if (processPlayerTurnStatusEffects()) {
            addLog("⚡ TURN SKIPPED: Player is STUNNED!", LogType.ALERT)
            recordPlayerAction(
                actionType = CombatActionType.PASS,
                summary = "Turn skipped due to STUN effect"
            )
            executeEnemyCombatTurnInline()
            return
        }

        val shieldHeal = 15 + (state.level * 3)
        _uiState.update { stateNow ->
            val newShield = minOf(stateNow.playerMaxShield, stateNow.playerShield + shieldHeal)
            stateNow.copy(
                playerShield = newShield,
                activeFirewallTimeLeft = 1, // Firewall active for enemy's upcoming turn
                showShieldEffect = true
            )
        }
        applyStatusEffectToPlayer(com.example.data.StatusEffectType.FORTIFIED, turns = 1, source = "Defensive Firewall")
        addLog("🛡️ ACTIVE FIREWALL INITIATED: Damage incoming in the next turn reduced by 75%!", LogType.SUCCESS)

        recordPlayerAction(
            actionType = CombatActionType.DEFEND,
            summary = "Active Firewall initiated (+$shieldHeal Shield, Fortified)",
            shieldAbsorbed = shieldHeal,
            statusApplied = "Fortified"
        )

        viewModelScope.launch {
            kotlinx.coroutines.delay(600)
            _uiState.update { it.copy(showShieldEffect = false) }
            // Automatically run enemy turn on player action completion
            executeEnemyCombatTurnInline()
        }
    }

    private var combatHackTimerJob: Job? = null

    fun combatHack() {
        if (!_uiState.value.isCombatInputEnabled) return
        val state = _uiState.value
        val enemy = state.activeEnemy ?: return

        if (state.activeCombatHack != null) return

        if (processPlayerTurnStatusEffects()) {
            addLog("⚡ TURN SKIPPED: Player is STUNNED!", LogType.ALERT)
            executeEnemyCombatTurnInline()
            return
        }

        if (state.ram < 3) {
            addLog("HACK PROTOCOL ABORTED: Needs 3 MB RAM.", LogType.ERROR)
            soundManager.playHackingErrorSound()
            return
        }

        _uiState.update { it.copy(ram = it.ram - 3) }

        // Generate target pattern and available symbol pool
        val symbolPool = listOf("1C", "E9", "55", "7A", "BD", "FF", "30", "A3", "2D", "0F")
        val patternLength = minOf(3 + (state.level / 2), 5)
        val shuffledPool = symbolPool.shuffled()
        val targetPattern = shuffledPool.take(patternLength)

        // Distractors + target pattern shuffled for selection keypad (8 choices)
        val keypadPool = (targetPattern + shuffledPool.drop(patternLength)).distinct().take(8).shuffled()

        val potentialDamage = 32 + (state.level * 5) + state.damageBonus
        val maxTime = maxOf(8, 14 - (state.level / 2))

        val hackState = CombatHackingPatternState(
            targetPattern = targetPattern,
            userSequence = emptyList(),
            availablePool = keypadPool,
            timeRemainingSeconds = maxTime,
            maxTimeSeconds = maxTime,
            attemptsRemaining = 3,
            maxAttempts = 3,
            enemyName = enemy.name,
            potentialDamage = potentialDamage
        )

        _uiState.update { it.copy(activeCombatHack = hackState) }

        soundManager.playNodeBreachSound()
        addLog("--- BREACH PROTOCOL INITIATED ---", LogType.ALERT)
        addLog("MATCH TARGET HEX SEQUENCE TO OVERRIDE ${enemy.name.uppercase()} FIREWALL!", LogType.INFO)

        startCombatHackTimer()
    }

    private fun startCombatHackTimer() {
        combatHackTimerJob?.cancel()
        combatHackTimerJob = viewModelScope.launch {
            while (isActive) {
                delay(1000)
                val currentHack = _uiState.value.activeCombatHack ?: break
                val newTime = currentHack.timeRemainingSeconds - 1
                if (newTime <= 0) {
                    _uiState.update { it.copy(activeCombatHack = currentHack.copy(timeRemainingSeconds = 0)) }
                    handleCombatHackFailure(currentHack, "EXPLOIT TIMED OUT! Firewall trace completed.")
                    break
                } else {
                    _uiState.update { it.copy(activeCombatHack = currentHack.copy(timeRemainingSeconds = newTime)) }
                }
            }
        }
    }

    fun selectCombatHackSymbol(symbol: String) {
        val currentHack = _uiState.value.activeCombatHack ?: return
        val updatedSeq = currentHack.userSequence + symbol.uppercase()
        val target = currentHack.targetPattern

        soundManager.playBufferShiftSound()

        // Check prefix match
        val isPrefixMatch = target.take(updatedSeq.size) == updatedSeq

        if (isPrefixMatch) {
            if (updatedSeq.size == target.size) {
                // COMPLETE PATTERN MATCHED!
                combatHackTimerJob?.cancel()
                _uiState.update { it.copy(activeCombatHack = currentHack.copy(userSequence = updatedSeq)) }
                handleCombatHackSuccess(currentHack)
            } else {
                // Partial prefix match
                _uiState.update { it.copy(activeCombatHack = currentHack.copy(userSequence = updatedSeq)) }
            }
        } else {
            // MISMATCH!
            soundManager.playHackingErrorSound()
            val newAttempts = currentHack.attemptsRemaining - 1
            addLog("SECURITY REJECT: Mismatched symbol '$symbol'! Attempts left: $newAttempts", LogType.ERROR)

            if (newAttempts <= 0) {
                combatHackTimerJob?.cancel()
                handleCombatHackFailure(currentHack, "BREACH COUNTERMEASURES TRIGGERED! Out of attempts.")
            } else {
                _uiState.update {
                    it.copy(
                        activeCombatHack = currentHack.copy(
                            userSequence = emptyList(),
                            attemptsRemaining = newAttempts
                        )
                    )
                }
            }
        }
    }

    fun clearCombatHackBuffer() {
        val currentHack = _uiState.value.activeCombatHack ?: return
        soundManager.playTerminalKeyPressSound()
        _uiState.update { it.copy(activeCombatHack = currentHack.copy(userSequence = emptyList())) }
        addLog("HACK BUFFER CLEARED.", LogType.INFO)
    }

    fun abortCombatHack() {
        val currentHack = _uiState.value.activeCombatHack ?: return
        combatHackTimerJob?.cancel()
        soundManager.playHackingErrorSound()
        handleCombatHackFailure(currentHack, "EXPLOIT ABORTED BY OPERATOR.")
    }

    private fun handleCombatHackSuccess(hackState: CombatHackingPatternState) {
        val enemy = _uiState.value.activeEnemy ?: return

        viewModelScope.launch {
            soundManager.playHackingSuccessSound()
            val hackDmg = hackState.potentialDamage
            val enemyRemIntegrity = maxOf(0, enemy.integrity - hackDmg)
            enemy.integrity = enemyRemIntegrity

            _uiState.update {
                it.copy(
                    activeCombatHack = null,
                    combatFlashEnemy = true,
                    enemyDamagePopup = "-$hackDmg HP (CRIT EXPLOIT)"
                )
            }

            addLog("PATTERNS MATCHED PERFECTLY! FIREWALL OVERRIDDEN!", LogType.SUCCESS)
            addLog("Dealt $hackDmg system-penetrating exploit damage & STUNNED ${enemy.name}!", LogType.SUCCESS)

            applyStatusEffectToEnemy(com.example.data.StatusEffectType.STUNNED, turns = 1, source = "Breach Protocol")

            recordPlayerAction(
                actionType = CombatActionType.QUICK_HACK,
                summary = "Breach exploit overrode firewall dealing $hackDmg damage & Stun",
                damageDealt = hackDmg,
                isCrit = true,
                statusApplied = "Stunned"
            )

            delay(500)
            _uiState.update { it.copy(combatFlashEnemy = false, enemyDamagePopup = null) }

            if (enemy.integrity <= 0) {
                _uiState.update { it.copy(showCombatBanner = "🏆 VICTORY", isCombatInputEnabled = false, turnPhase = TurnPhase.COMBAT_VICTORY) }
                delay(1200)
                handleCombatVictoryInline(enemy)
                _uiState.update { it.copy(showCombatBanner = null, isCombatInputEnabled = true) }
            } else {
                executeEnemyCombatTurnInline()
            }
        }
    }

    private fun handleCombatHackFailure(hackState: CombatHackingPatternState, reason: String) {
        val enemy = _uiState.value.activeEnemy

        viewModelScope.launch {
            soundManager.playHackingErrorSound()
            addLog(reason, LogType.ALERT)

            if (enemy != null) {
                val fallbackDmg = maxOf(5, hackState.potentialDamage / 3)
                enemy.integrity = maxOf(0, enemy.integrity - fallbackDmg)

                _uiState.update {
                    it.copy(
                        activeCombatHack = null,
                        combatFlashEnemy = true,
                        enemyDamagePopup = "-$fallbackDmg HP (PARTIAL)"
                    )
                }

                addLog("Partial feedback breach dealt $fallbackDmg damage to ${enemy.name}.", LogType.ALERT)

                recordPlayerAction(
                    actionType = CombatActionType.QUICK_HACK,
                    summary = "Partial exploit feedback dealt $fallbackDmg damage",
                    damageDealt = fallbackDmg
                )

                delay(500)
                _uiState.update { it.copy(combatFlashEnemy = false, enemyDamagePopup = null) }

                if (enemy.integrity <= 0) {
                    _uiState.update { it.copy(showCombatBanner = "🏆 VICTORY", isCombatInputEnabled = false, turnPhase = TurnPhase.COMBAT_VICTORY) }
                    delay(1200)
                    handleCombatVictoryInline(enemy)
                    _uiState.update { it.copy(showCombatBanner = null, isCombatInputEnabled = true) }
                } else {
                    executeEnemyCombatTurnInline()
                }
            } else {
                _uiState.update { it.copy(activeCombatHack = null) }
            }
        }
    }

    fun combatScan() {
        if (!_uiState.value.isCombatInputEnabled) return
        val state = _uiState.value
        val enemy = state.activeEnemy ?: return

        addLog("--- SCANNING TARGET PROCESS DATA ---", LogType.ALERT)
        addLog("NAME: ${enemy.name} | CLASS: Cyber-Entity Layer ${state.level}", LogType.INFO)
        addLog("FIREWALL SHELL: ${enemy.shield}/${enemy.maxShield} (Armor Rating: ${enemy.armor})", LogType.INFO)
        addLog("CORE DATA: ${enemy.integrity}/${enemy.maxIntegrity} | ATK MODULE: ${enemy.damage}", LogType.INFO)
        addLog("ANALYSIS COMPLETE: Signal feedback scrambled enemy telemetry! Target Glitched.", LogType.SUCCESS)

        applyStatusEffectToEnemy(com.example.data.StatusEffectType.WEAKENED, turns = 2, source = "Deep Telemetry Scan")

        recordPlayerAction(
            actionType = CombatActionType.SCAN,
            summary = "Deep Telemetry Scan weakened ${enemy.name}",
            statusApplied = "Weakened"
        )

        viewModelScope.launch {
            _uiState.update { stateNow ->
                stateNow.copy(
                    enemyCombatAction = "Scan complete. Hostile systems recalibrating.",
                    combatFlashEnemy = true
                )
            }
            kotlinx.coroutines.delay(400)
            _uiState.update { it.copy(combatFlashEnemy = false) }

            // Scanned enemy is weakened and then attacks
            executeEnemyCombatTurnInline(isScanStunned = true)
        }
    }

    fun endTurn() {
        if (!_uiState.value.isCombatInputEnabled) return
        val enemy = _uiState.value.activeEnemy ?: return
        addLog("PASSING TURN: Player manually terminated their phase.", LogType.INFO)
        recordPlayerAction(
            actionType = CombatActionType.PASS,
            summary = "Manually terminated turn phase"
        )
        executeEnemyCombatTurnInline()
    }

    fun executeCombatProgramInline(program: Program) {
        if (!_uiState.value.isCombatInputEnabled) return
        val state = _uiState.value
        val enemy = state.activeEnemy ?: return

        if (processPlayerTurnStatusEffects()) {
            addLog("⚡ TURN SKIPPED: Player is STUNNED!", LogType.ALERT)
            recordPlayerAction(
                actionType = CombatActionType.PASS,
                summary = "Turn skipped due to STUN effect"
            )
            executeEnemyCombatTurnInline()
            return
        }

        if (state.ram < program.ramCost) {
            addLog("INSUFFICIENT RAM: Requires ${program.ramCost}MB, but only ${state.ram}MB allocated.", LogType.ERROR)
            return
        }

        _uiState.update { stateNow ->
            stateNow.copy(
                ram = stateNow.ram - program.ramCost
            )
        }

        viewModelScope.launch {
            addLog("> RUNNING ${program.name}...", LogType.INFO)

            val baseDmg = program.damage
            val statPower = (state.level * 2) + state.damageBonus
            var rawPlayerDamage = baseDmg + statPower

            // Apply Status Effect multipliers on Player
            val isOverclocked = state.playerStatusEffects.any { it.type == com.example.data.StatusEffectType.BUFFED }
            val isGlitched = state.playerStatusEffects.any { it.type == com.example.data.StatusEffectType.WEAKENED }
            if (isOverclocked) {
                rawPlayerDamage = (rawPlayerDamage * 1.5f).toInt()
                addLog("🔥 OVERCLOCKED: Program damage amplified by 50%!", LogType.SUCCESS)
            }
            if (isGlitched) {
                rawPlayerDamage = (rawPlayerDamage * 0.5f).toInt()
                addLog("🌀 GLITCHED: Program payload reduced by 50%!", LogType.ALERT)
            }

            // Balcony Vantage: +25% attack damage bonus
            val standCell = state.maze.getOrNull(state.gridY)?.getOrNull(state.gridX)
            if (standCell == com.example.data.CellType.ELEVATED_BALCONY) {
                rawPlayerDamage = (rawPlayerDamage * 1.25f).toInt()
                addLog("✨ BALCONY VANTAGE ACTIVE: Attack payload magnified by 25% from high-level gallery overlook!", LogType.SUCCESS)
            }

            var isCrit = false
            val critRate = 10 + (state.ram * 2)
            val finalCritRate = if (state.runnerClass == NetrunnerClass.CODE_SLASHER) critRate + 25 else critRate

            if (Random.nextInt(100) < finalCritRate) {
                isCrit = true
                val critMultiplier = if (state.runnerClass == NetrunnerClass.CODE_SLASHER) 2.0f else 1.5f
                rawPlayerDamage = (rawPlayerDamage * critMultiplier).toInt()
            }

            if (state.runnerClass == NetrunnerClass.BUFFER_OVERFLOW) {
                val mult = 1.0f + (state.ram * 0.03f)
                rawPlayerDamage = (rawPlayerDamage * mult).toInt()
            }

            val enemyArmor = enemy.armor
            val effectiveArmor = if (isCrit) (enemyArmor * 0.5f).toInt() else enemyArmor
            var finalDmg = if (program.piercesDefense) {
                rawPlayerDamage
            } else {
                maxOf(2, rawPlayerDamage - effectiveArmor)
            }

            // Enemy Fortified check
            val isEnemyFortified = enemy.statusEffects.any { it.type == com.example.data.StatusEffectType.FORTIFIED }
            if (isEnemyFortified) {
                finalDmg = (finalDmg * 0.5f).toInt().coerceAtLeast(1)
                addLog("🛡️ HOSTILE FORTIFIED: Damage absorbed by enemy defense grid (-50%).", LogType.ALERT)
            }

            val enemyRemShield = maxOf(0, enemy.shield - finalDmg)
            val shieldDmg = enemy.shield - enemyRemShield
            val bodyDmg = finalDmg - shieldDmg
            val enemyRemIntegrity = maxOf(0, enemy.integrity - bodyDmg)

            enemy.shield = enemyRemShield
            enemy.integrity = enemyRemIntegrity

            recordPlayerAction(
                actionType = CombatActionType.PROGRAM,
                summary = "Executed ${program.name} dealing $finalDmg damage (Shield: -$shieldDmg, HP: -$bodyDmg)",
                damageDealt = finalDmg,
                shieldAbsorbed = shieldDmg,
                healAmount = program.heal,
                isCrit = isCrit,
                statusApplied = program.statusEffectToApply?.displayName
            )

            addLog("[CALC]: Base:${baseDmg} + Stats:${statPower} = Raw:${baseDmg + statPower}", LogType.INFO)
            if (isCrit) {
                addLog("CRITICAL HIT! [x${if (state.runnerClass == NetrunnerClass.CODE_SLASHER) "2.0" else "1.5"}] Armor bypassed: ${effectiveArmor}/${enemyArmor}", LogType.SUCCESS)
            }
            if (finalDmg > 0) {
                addLog("Dealt ${finalDmg} damage to ${enemy.name} (Shield: -${shieldDmg}, Core: -${bodyDmg}) [Hostile Armor: ${enemyArmor}]", LogType.SUCCESS)
            }

            if (program.heal > 0) {
                val healed = minOf(state.maxIntegrity - state.integrity, program.heal)
                _uiState.update { it.copy(integrity = it.integrity + healed) }
                addLog("System integrity patch compiled: +$healed% Integrity.", LogType.SUCCESS)
            }

            if (program.shield > 0) {
                val shieldHealed = minOf(state.playerMaxShield - state.playerShield, program.shield)
                _uiState.update { it.copy(playerShield = it.playerShield + shieldHealed) }
                addLog("Temporary firewalls reinforced: +$shieldHealed Shield Barrier.", LogType.SUCCESS)
            }

            // Apply Program Status Effect
            if (program.statusEffectToApply != null) {
                val turns = if (program.statusEffectTurns > 0) program.statusEffectTurns else 2
                if (program.statusEffectTargetSelf) {
                    applyStatusEffectToPlayer(
                        type = program.statusEffectToApply,
                        turns = turns,
                        magnitude = program.statusEffectMagnitude,
                        source = program.name
                    )
                } else {
                    applyStatusEffectToEnemy(
                        type = program.statusEffectToApply,
                        turns = turns,
                        magnitude = program.statusEffectMagnitude,
                        source = program.name
                    )
                }
            }

            val hasDmg = finalDmg > 0
            val hasHealOrShield = program.heal > 0 || program.shield > 0
            _uiState.update { stateNow ->
                stateNow.copy(
                    combatFlashEnemy = hasDmg,
                    enemyDamagePopup = if (hasDmg) "-$finalDmg HP" else null,
                    showShieldEffect = hasHealOrShield
                )
            }

            kotlinx.coroutines.delay(400)
            _uiState.update { it.copy(combatFlashEnemy = false, enemyDamagePopup = null, showShieldEffect = false) }

            if (enemy.integrity <= 0) {
                _uiState.update { it.copy(showCombatBanner = "🏆 VICTORY", isCombatInputEnabled = false, turnPhase = TurnPhase.COMBAT_VICTORY) }
                kotlinx.coroutines.delay(1200)
                handleCombatVictoryInline(enemy)
                _uiState.update { it.copy(showCombatBanner = null, isCombatInputEnabled = true) }
            } else {
                // Automatically run enemy combat turn on program execution completion
                executeEnemyCombatTurnInline()
            }
        }
    }

    private fun executeEnemyCombatTurnInline(isScanStunned: Boolean = false) {
        viewModelScope.launch {
            _uiState.update { it.copy(combatTurn = CombatTurn.ENEMY, turnPhase = TurnPhase.ENEMY_RESOLVING) }
            val state = _uiState.value
            val enemy = state.activeEnemy ?: return@launch

            // 1. Process Enemy Status Effects
            val activeEffects = enemy.statusEffects
            var isEnemyStunned = isScanStunned
            val remainingEnemyEffects = mutableListOf<com.example.data.ActiveStatusEffect>()

            for (effect in activeEffects) {
                when (effect.type) {
                    com.example.data.StatusEffectType.POISONED -> {
                        val dotDamage = if (effect.magnitude > 0) effect.magnitude else 8
                        val enemyRemIntegrity = maxOf(0, enemy.integrity - dotDamage)
                        enemy.integrity = enemyRemIntegrity
                        _uiState.update { stateNow ->
                            stateNow.copy(
                                enemyDamagePopup = "-$dotDamage HP (Corroded)",
                                combatFlashEnemy = true,
                                enemyStatusEffects = enemy.statusEffects.toList()
                            )
                        }
                        addLog("🧪 CORROSION TICK: ${enemy.name} took $dotDamage corrosion damage!", LogType.SUCCESS)
                        delay(300)
                        _uiState.update { it.copy(enemyDamagePopup = null, combatFlashEnemy = false) }

                        if (enemy.integrity <= 0) {
                            _uiState.update { it.copy(showCombatBanner = "🏆 VICTORY", isCombatInputEnabled = false, turnPhase = TurnPhase.COMBAT_VICTORY) }
                            delay(1000)
                            handleCombatVictoryInline(enemy)
                            _uiState.update { it.copy(showCombatBanner = null, isCombatInputEnabled = true) }
                            return@launch
                        }
                    }
                    com.example.data.StatusEffectType.STUNNED -> {
                        isEnemyStunned = true
                        addLog("⚡ HOSTILE STUNNED: ${enemy.name} circuit frozen by electrical charge!", LogType.SUCCESS)
                    }
                    else -> {}
                }

                val nextTurns = effect.turnsRemaining - 1
                if (nextTurns > 0) {
                    remainingEnemyEffects.add(effect.copy(turnsRemaining = nextTurns))
                } else {
                    addLog("✨ EXPIRED STATUS: ${effect.type.displayName} effect on ${enemy.name} faded.", LogType.INFO)
                }
            }

            enemy.statusEffects = remainingEnemyEffects
            _uiState.update { it.copy(enemyStatusEffects = remainingEnemyEffects.toList()) }

            if (isEnemyStunned) {
                addLog("⚡ ENEMY TURN SKIPPED: ${enemy.name} is paralyzed by STUN status!", LogType.SUCCESS)
                recordEnemyAction(
                    actionType = CombatActionType.PASS,
                    summary = "${enemy.name} was stunned and skipped turn"
                )
                delay(600)
                processTurnMaintenance()
                return@launch
            }

            // Calculate base enemy damage
            var baseEnemyDmg = enemy.damage + Random.nextInt(-2, 3)
            if (baseEnemyDmg < 2) baseEnemyDmg = 2

            val isEnemyBuffed = activeEffects.any { it.type == com.example.data.StatusEffectType.BUFFED }
            val isEnemyWeakened = activeEffects.any { it.type == com.example.data.StatusEffectType.WEAKENED }
            val isPlayerFortified = state.playerStatusEffects.any { it.type == com.example.data.StatusEffectType.FORTIFIED }

            if (isEnemyBuffed) {
                baseEnemyDmg = (baseEnemyDmg * 1.5f).toInt()
                addLog("🔥 HOSTILE OVERCLOCKED: Enemy damage boosted by 50%!", LogType.ERROR)
            }
            if (isEnemyWeakened) {
                baseEnemyDmg = (baseEnemyDmg * 0.5f).toInt().coerceAtLeast(1)
                addLog("🌀 HOSTILE GLITCHED: Enemy damage reduced by 50%!", LogType.SUCCESS)
            }
            if (isPlayerFortified) {
                baseEnemyDmg = (baseEnemyDmg * 0.5f).toInt().coerceAtLeast(1)
                addLog("🛡️ PLAYER FORTIFIED: Incoming attack damage halved by active barrier!", LogType.SUCCESS)
            }

            // Gravity Slope check (Evasion boost reduces enemy hit intensity by 30%)
            val standCell = state.maze.getOrNull(state.gridY)?.getOrNull(state.gridX)
            if (standCell == com.example.data.CellType.GRAVITY_SLOPE) {
                baseEnemyDmg = (baseEnemyDmg * 0.70f).toInt().coerceAtLeast(1)
                addLog("✨ GRAVITY EVASION: Magnetic slope rapid momentum absorbed 30% of incoming packet force!", LogType.SUCCESS)
            }

            var finalEnemyDmg = maxOf(1, baseEnemyDmg - state.defenseBonus)
            if (state.kineticShieldActiveThisCombat) {
                finalEnemyDmg = 0
                _uiState.update { it.copy(kineticShieldActiveThisCombat = false) }
                addLog("🛡️ KINETIC SHIELD IMPLANT: Subdermal kinetic barrier completely absorbed incoming attack!", LogType.SUCCESS)
            }

            val actions = listOf(
                "Trojan injection stream",
                "Rootkit port scan exploit",
                "Distributed Denial-of-Service packets",
                "Logic logicbomb payload"
            )
            val selectedAction = actions[Random.nextInt(actions.size)]

            val currentShield = state.playerShield
            val shieldDamage = minOf(currentShield, finalEnemyDmg)
            val remainingShield = currentShield - shieldDamage
            val integrityDamage = finalEnemyDmg - shieldDamage
            val newPlayerIntegrity = maxOf(0, state.integrity - integrityDamage)

            soundManager.playCombatHitSound()
            _uiState.update { stateNow ->
                stateNow.copy(
                    integrity = newPlayerIntegrity,
                    playerShield = remainingShield,
                    enemyCombatAction = "${enemy.name} ran $selectedAction: Dealt $finalEnemyDmg damage. (Shield absorbed: $shieldDamage, Core hit: $integrityDamage)",
                    combatFlashPlayer = true,
                    combatScreenShake = true,
                    playerDamagePopup = if (finalEnemyDmg > 0) "-$finalEnemyDmg HP" else "ABSORBED"
                )
            }

            recordEnemyAction(
                actionType = CombatActionType.STRIKE,
                summary = "${enemy.name} ran $selectedAction dealing $finalEnemyDmg damage",
                damageDealt = finalEnemyDmg,
                shieldAbsorbed = shieldDamage
            )

            addLog("${enemy.name} executes $selectedAction...", LogType.ERROR)
            if (shieldDamage > 0) {
                addLog("Player Shield absorbed $shieldDamage damage.", LogType.ALERT)
            }
            if (integrityDamage > 0) {
                addLog("System Integrity degraded by $integrityDamage%.", LogType.ERROR)
            }

            // 25% chance for enemy attack to inflict status effect on player
            if (Random.nextInt(100) < 25) {
                val debuff = listOf(
                    com.example.data.StatusEffectType.POISONED,
                    com.example.data.StatusEffectType.WEAKENED,
                    com.example.data.StatusEffectType.STUNNED
                ).random()
                val turns = if (debuff == com.example.data.StatusEffectType.STUNNED) 1 else 2
                applyStatusEffectToPlayer(debuff, turns = turns, magnitude = 8, source = enemy.name)
            }

            kotlinx.coroutines.delay(600)
            _uiState.update { it.copy(combatFlashPlayer = false, combatScreenShake = false, playerDamagePopup = null) }

            if (newPlayerIntegrity <= 0) {
                val hasEmergencyReboot = state.installedImplants.values.any { it?.passiveAbility == com.example.data.ImplantAbility.EMERGENCY_REBOOT }
                if (hasEmergencyReboot && !state.hasUsedEmergencyRebootThisRun) {
                    val revivedHp = (state.maxIntegrity * 0.25f).toInt().coerceAtLeast(15)
                    _uiState.update {
                        it.copy(
                            integrity = revivedHp,
                            hasUsedEmergencyRebootThisRun = true,
                            showCombatBanner = "⚡ EMERGENCY REBOOT"
                        )
                    }
                    addLog("⚡ EMERGENCY REBOOT ACTIVATED! Synthetic Heart Nanites restarted runner core at $revivedHp HP!", LogType.SUCCESS)
                    soundManager.playLootCollectionSound()
                    kotlinx.coroutines.delay(1000)
                    _uiState.update { it.copy(showCombatBanner = null) }
                    processTurnMaintenance()
                    return@launch
                }

                _uiState.update { it.copy(showCombatBanner = "💀 DEFEAT", turnPhase = TurnPhase.COMBAT_DEFEAT) }
                kotlinx.coroutines.delay(1200)
                handleGameOver("Destroyed by security process ${enemy.name}")
                _uiState.update { it.copy(showCombatBanner = null) }
                return@launch
            }

            processTurnMaintenance()
        }
    }

    private fun handleCombatVictoryInline(enemy: Enemy) {
        soundManager.playLootCollectionSound()
        val state = _uiState.value
        val baseBounty = enemy.bountyCredits

        val hasRamRecycler = state.installedImplants.values.any { it?.passiveAbility == com.example.data.ImplantAbility.RAM_RECYCLER }
        if (hasRamRecycler) {
            val recycledRam = (state.ram + 1).coerceAtMost(state.maxRam)
            _uiState.update { it.copy(ram = recycledRam) }
            addLog("🔋 RAM RECYCLER IMPLANT: Neutralized hostile process recycled +1 RAM.", LogType.SUCCESS)
        }

        val updatedMaze = state.maze.map { it.clone() }.toTypedArray()
        if (state.targetNodeY in updatedMaze.indices && state.targetNodeX in updatedMaze[0].indices) {
            updatedMaze[state.targetNodeY][state.targetNodeX] = CellType.PATH
        }

        val lootDrop = CombatLootDropSystem.generateLootDrop(
            enemyName = enemy.name,
            enemyLevel = state.level,
            baseBounty = baseBounty
        )

        val updatedInventory = state.inventory.toMutableList()
        updatedInventory.add(lootDrop.itemName)

        viewModelScope.launch {
            repository.insertInventoryItem(lootDrop.inventoryEntity)
        }

        _uiState.update { stateNow ->
            stateNow.copy(
                gameState = GameState.EXPLORATION,
                credits = stateNow.credits + lootDrop.totalCreditsEarned,
                totalCreditsEarned = stateNow.totalCreditsEarned + lootDrop.totalCreditsEarned,
                inventory = updatedInventory,
                maze = updatedMaze,
                activeEnemy = null
            )
        }

        updatePerspective()
        addLog("CRITICAL SUCCESS: PROCESS ${enemy.name} TERMINATED.", LogType.SUCCESS)
        addLog("Bounty extraction: +${lootDrop.totalCreditsEarned} MB credits compiled.", LogType.SUCCESS)
        addLog(lootDrop.logMessage, LogType.SUCCESS)
        addExperience(lootDrop.xpEarned)
    }

    // ----------------------------------------------------
    // Character Leveling & XP System
    // ----------------------------------------------------
    fun addExperience(amount: Int) {
        if (amount <= 0) return
        var currentXp = _uiState.value.characterXp + amount
        var currentLvl = _uiState.value.characterLevel
        var reqXp = _uiState.value.xpToNextLevel
        var levelsGained = 0

        while (currentXp >= reqXp) {
            currentXp -= reqXp
            currentLvl += 1
            levelsGained += 1
            reqXp = 100 + (currentLvl - 1) * 75
        }

        if (levelsGained > 0) {
            val newMaxHp = _uiState.value.maxIntegrity + (15 * levelsGained)
            val newMaxShield = _uiState.value.playerMaxShield + (10 * levelsGained)
            val newDmgBonus = _uiState.value.damageBonus + (2 * levelsGained)
            val ramGain = levelsGained / 2 + (if (currentLvl % 2 == 0) 1 else 0)
            val newMaxRam = _uiState.value.maxRam + maxOf(0, ramGain)

            _uiState.update { stateNow ->
                stateNow.copy(
                    characterLevel = currentLvl,
                    characterXp = currentXp,
                    xpToNextLevel = reqXp,
                    maxIntegrity = newMaxHp,
                    integrity = newMaxHp,
                    playerMaxShield = newMaxShield,
                    playerShield = newMaxShield,
                    damageBonus = newDmgBonus,
                    maxRam = newMaxRam,
                    ram = newMaxRam
                )
            }
            soundManager.playLootCollectionSound()
            addLog("🎉 LEVEL UP! RECOGNIZED AS LEVEL $currentLvl NETRUNNER (+${levelsGained} LVL)!", LogType.SUCCESS)
            addLog("⚡ SYSTEM UPGRADE: Max Integrity: $newMaxHp HP | Shield Capacity: $newMaxShield | Attack Bonus: +$newDmgBonus | Max RAM: $newMaxRam MB", LogType.SUCCESS)
            addLog("✨ Full integrity, shields, and memory buffers restored to capacity!", LogType.SUCCESS)
        } else {
            _uiState.update { stateNow ->
                stateNow.copy(
                    characterXp = currentXp,
                    xpToNextLevel = reqXp
                )
            }
        }
        addLog("✨ GAINED +$amount SYSTEM EXPERIENCE POINTS! (XP: $currentXp / $reqXp)", LogType.INFO)
    }

    private fun isValidMove(x: Int, y: Int): Boolean {
        val maze = _uiState.value.maze
        if (y !in maze.indices || x !in maze[0].indices) return false
        return maze[y][x] != CellType.WALL
    }

    private fun updatePerspective() {
        val state = _uiState.value
        val perspective = GameEngine.render3DPerspective(state.maze, state.gridX, state.gridY, state.direction, state.activeWeather)
        _uiState.update { it.copy(perspectiveText = perspective) }
    }

    // ----------------------------------------------------
    // Special Node Interactions
    // ----------------------------------------------------

    fun interact() {
        if (_uiState.value.screen != ActiveScreen.EXPLORATION || _uiState.value.gameState != GameState.EXPLORATION) return

        val state = _uiState.value
        // 1. First, check if there is an interactive cell directly ahead of us
        val targetX = state.gridX + state.direction.dx
        val targetY = state.gridY + state.direction.dy

        var cellToInteractWith = CellType.PATH
        var interactX = targetX
        var interactY = targetY

        if (targetY in state.maze.indices && targetX in state.maze[0].indices) {
            val cellAhead = state.maze[targetY][targetX]
            if (cellAhead == CellType.DATA_STORE || cellAhead == CellType.ENCRYPTED_PORTAL || 
                cellAhead == CellType.VIRUS_NODE || cellAhead == CellType.SECRET_CACHE ||
                cellAhead == CellType.STAIRS_UP || cellAhead == CellType.STAIRS_DOWN ||
                cellAhead == CellType.ELEVATOR || cellAhead == CellType.SECRET_WALL ||
                cellAhead == CellType.HACKABLE_TERMINAL || cellAhead == CellType.TERMINAL_DOOR ||
                cellAhead == CellType.SCAN_CACHE || cellAhead == CellType.ALTERNATIVE_VENT) {
                cellToInteractWith = cellAhead
            }
        }

        // 2. If no interactive cell is ahead, check if we are standing on one!
        if (cellToInteractWith == CellType.PATH) {
            val cellCurrent = state.maze[state.gridY][state.gridX]
            if (cellCurrent == CellType.DATA_STORE || cellCurrent == CellType.ENCRYPTED_PORTAL || 
                cellCurrent == CellType.VIRUS_NODE || cellCurrent == CellType.SECRET_CACHE ||
                cellCurrent == CellType.STAIRS_UP || cellCurrent == CellType.STAIRS_DOWN ||
                cellCurrent == CellType.ELEVATOR || cellCurrent == CellType.SECRET_WALL ||
                cellCurrent == CellType.HACKABLE_TERMINAL || cellCurrent == CellType.TERMINAL_DOOR ||
                cellCurrent == CellType.SCAN_CACHE || cellCurrent == CellType.ALTERNATIVE_VENT) {
                cellToInteractWith = cellCurrent
                interactX = state.gridX
                interactY = state.gridY
            }
        }

        if (cellToInteractWith == CellType.PATH) {
            addLog("NO INTERACTIVE INTERFACE DIRECTLY AHEAD OR UNDERFOOT.", LogType.ERROR)
            return
        }

        when (cellToInteractWith) {
            CellType.SECRET_WALL -> {
                addLog("🔓 PHASE MATRIX OVERRIDE: Discovered hidden illusory wall passage!", LogType.SUCCESS)
                val updatedMaze = state.maze.map { it.clone() }.toTypedArray()
                updatedMaze[interactY][interactX] = CellType.PATH
                _uiState.update { it.copy(maze = updatedMaze) }
                soundManager.playNodeBreachSound()
                addExperience(50)
                updatePerspective()
            }
            CellType.HACKABLE_TERMINAL -> {
                addLog("INITIATING OVERRIDE PROTOCOL ON SECURITY GATE TERMINAL...", LogType.ALERT)
                startHackingPuzzle(interactX, interactY, difficulty = state.level + 1)
            }
            CellType.TERMINAL_DOOR -> {
                addLog("🔒 SECURITY GATE LOCKED: Hack adjacent terminal node to unlock gate bypass.", LogType.ERROR)
            }
            CellType.SCAN_CACHE -> {
                addLog("✨ QUANTUM STEALTH CACHE ACCESSED!", LogType.SUCCESS)
                val rewards = listOf("QuantumSlasher.pkg", "AegisShield.sys", "HyperRAM.exe", "Overclock.pkg", "NaniteRegen.sys")
                val reward = rewards.random()
                val bonusCredits = 250 + Random.nextInt(100)
                val updatedInventory = state.inventory + reward
                val updatedMaze = state.maze.map { it.clone() }.toTypedArray()
                updatedMaze[interactY][interactX] = CellType.PATH
                _uiState.update { s ->
                    s.copy(
                        maze = updatedMaze,
                        credits = s.credits + bonusCredits,
                        totalCreditsEarned = s.totalCreditsEarned + bonusCredits,
                        inventory = updatedInventory
                    )
                }
                soundManager.playLootCollectionSound()
                addExperience(75)
                updatePerspective()
            }
            CellType.ALTERNATIVE_VENT -> {
                addLog("🌀 ENTERED SUB-CONDUIT BYPASS VENT: Sliding through service duct...", LogType.INFO)
                soundManager.playStepSound()
            }
            CellType.DATA_STORE -> {
                addLog("INITIATING HANDSHAKE WITH DATA STORE CORE...", LogType.INFO)
                startHackingPuzzle(interactX, interactY, difficulty = state.level)
            }
            CellType.SECRET_CACHE -> {
                addLog("INITIATING HANDSHAKE WITH CLASSIFIED CRYPT-CACHE...", LogType.SUCCESS)
                startHackingPuzzle(interactX, interactY, difficulty = state.level + 1)
            }
            CellType.STAIRS_UP -> {
                ascendStairs()
            }
            CellType.STAIRS_DOWN -> {
                descendStairs()
            }
            CellType.ELEVATOR -> {
                interactWithElevator()
            }
            CellType.ENCRYPTED_PORTAL -> {
                addLog("SUB-SECTOR DECRYPTION INITIALIZED...", LogType.SUCCESS)
                val stateNow = _uiState.value
                when (stateNow.currentZone) {
                    com.example.data.Zone.BUILDING -> {
                        addLog("EMERGING FROM BUILDING REACTOR CORE. ENTERING COLLECTOR SUB-TUNNELS...", LogType.ALERT)
                        _uiState.update { it.copy(credits = it.credits + 200, totalCreditsEarned = it.totalCreditsEarned + 200) }
                        loadOrCreateLevel(com.example.data.Zone.COLLECTORS, 1, isAscending = true)
                    }
                    com.example.data.Zone.COLLECTORS -> {
                        addLog("DRAINAGE SEQUENCE COMPLETE. EMERGING INTO METROPOLITAN CYBER-CITY MAIN GRID!", LogType.SUCCESS)
                        _uiState.update { it.copy(credits = it.credits + 300, totalCreditsEarned = it.totalCreditsEarned + 300) }
                        loadOrCreateLevel(com.example.data.Zone.CITY, 0, isAscending = true)
                    }
                    com.example.data.Zone.CITY -> {
                        addLog("ULTIMATE NETRUN-GATE PENETRATED! CYBERSPACE SECURED!", LogType.SUCCESS)
                        _uiState.update { s ->
                            s.copy(
                                screen = ActiveScreen.GAME_OVER,
                                gameState = GameState.COMBAT_END,
                                runOutcome = "CORE GRID TAKEOVER: SUCCESSFUL NETRUN"
                            )
                        }
                    }
                }
            }
            CellType.VIRUS_NODE -> {
                addLog("FORCE-CONNECTING WITH ACTIVE THREAT...", LogType.ALERT)
                triggerCombatInline(interactX, interactY)
            }
            else -> {
                addLog("NO RESPONSE AT ADDR: (${interactX}, ${interactY}). IS PATH EMPTY?", LogType.ERROR)
            }
        }
    }

    // ----------------------------------------------------
    // Terminal Hacking (Breach Protocol)
    // ----------------------------------------------------

    private fun startHackingPuzzle(tx: Int, ty: Int, difficulty: Int) {
        val puzzle = GameEngine.generateHackingPuzzle(difficulty)
        _uiState.update { state ->
            state.copy(
                screen = ActiveScreen.HACKING_MINIGAME,
                activePuzzle = puzzle,
                targetNodeX = tx,
                targetNodeY = ty
            )
        }
        addLog("--- BREACH PROTOCOL INITIALIZED ---", LogType.ALERT)
        addLog("MATCH SEQUENCES USING HORIZONTAL/VERTICAL ALTERNATIONS.", LogType.INFO)
    }

    fun hackCell(row: Int, col: Int) {
        val state = _uiState.value
        val puzzle = state.activePuzzle ?: return
        if (puzzle.isSolved || puzzle.isFailed) return

        // Validate selection alternates correctly
        val isFirstMove = puzzle.selectedIndices.isEmpty()
        if (isFirstMove) {
            if (row != 0) {
                addLog("ERROR: INTRUSION MUST START ON MAIN ROW 0.", LogType.ERROR)
                return
            }
        } else {
            // Check orientation rule
            val lastMove = puzzle.selectedIndices.last()
            val isHorizontalMove = puzzle.selectedIndices.size % 2 == 1

            if (isHorizontalMove) {
                // Must be vertical transition: same column as last move
                if (col != lastMove.second) {
                    addLog("SECURITY SYSTEM REJECT: MUST CHANGE VERTICALLY.", LogType.ERROR)
                    return
                }
            } else {
                // Must be horizontal transition: same row as last move
                if (row != lastMove.first) {
                    addLog("SECURITY SYSTEM REJECT: MUST CHANGE HORIZONTALLY.", LogType.ERROR)
                    return
                }
            }
        }

        // Add to selections
        val newSelected = puzzle.selectedIndices.toMutableList()
        newSelected.add(Pair(row, col))

        val codeSelected = puzzle.grid[row][col]
        val newBuffer = puzzle.currentBuffer.toMutableList()
        newBuffer.add(codeSelected)

        // Setup highlights for the NEXT move
        val nextIsHorizontal = newSelected.size % 2 == 1
        val nextRowHighlight = if (nextIsHorizontal) null else row
        val nextColHighlight = if (nextIsHorizontal) col else null

        // Check buffer correctness
        val isSolved = isSubsequenceMatch(puzzle.targetSequence, newBuffer)
        val isFailed = !isSolved && newBuffer.size >= puzzle.bufferLimit

        val updatedPuzzle = puzzle.copy(
            selectedIndices = newSelected,
            currentBuffer = newBuffer,
            isSolved = isSolved,
            isFailed = isFailed,
            highlightedRow = nextRowHighlight,
            highlightedCol = nextColHighlight
        )

        _uiState.update { it.copy(activePuzzle = updatedPuzzle) }
        addLog("BUFFER COMMITTED: $codeSelected")

        if (isSolved) {
            handleHackingSuccess()
        } else if (isFailed) {
            handleHackingFailure()
        }
    }

    private fun isSubsequenceMatch(target: List<String>, buffer: List<String>): Boolean {
        // Simple contiguous subsequence match
        if (target.size > buffer.size) return false
        for (i in 0..buffer.size - target.size) {
            val sub = buffer.subList(i, i + target.size)
            if (sub == target) return true
        }
        return false
    }

    private fun handleHackingSuccess() {
        soundManager.playSecurityNodeHackSuccessSound()
        val state = _uiState.value
        val nodeType = if (state.targetNodeY in state.maze.indices && state.targetNodeX in state.maze[0].indices) {
            state.maze[state.targetNodeY][state.targetNodeX]
        } else {
            CellType.DATA_STORE
        }

        val isSecretCache = nodeType == CellType.SECRET_CACHE
        var baseBounty = if (isSecretCache) 300 + (state.level * 100) else 100 + (state.level * 50)
        
        // Performance Buff: CREDIT_SIPHON (+25% bonus credits)
        if (state.activeBuffs.contains("CREDIT_SIPHON")) {
            baseBounty = (baseBounty * 1.25f).toInt()
        }
        val bountyCredits = baseBounty + Random.nextInt(50)

        // Data Fragments Extraction
        val fragmentsExtracted = if (isSecretCache) Random.nextInt(3, 6) else Random.nextInt(1, 3)

        val rewards = if (isSecretCache) {
            listOf("SlasherMod.pkg", "AegisProtocol.sys", "OverflowExploit.exe", "Overclocker.sys", "HyperRAM.exe")
        } else {
            listOf("NanoMed.sys", "RAMBoost.exe", "Decryptor.pkg", "ChipsetMod.pkg")
        }
        val randomReward = rewards[Random.nextInt(rewards.size)]

        val updatedInventory = state.inventory.toMutableList()
        updatedInventory.add(randomReward)
        if (isSecretCache) {
            val extraItem = listOf("NanoMed.sys", "RAMBoost.exe").random()
            updatedInventory.add(extraItem)
        }

        // Clear node from map
        val updatedMaze = state.maze.map { it.clone() }.toTypedArray()
        if (state.targetNodeY in updatedMaze.indices && state.targetNodeX in updatedMaze[0].indices) {
            updatedMaze[state.targetNodeY][state.targetNodeX] = CellType.PATH
        }

        val isTerminal = nodeType == CellType.HACKABLE_TERMINAL
        if (isTerminal) {
            addLog("🔑 SECURITY TERMINAL OVERRIDDEN! Unlocking all sector gate barriers...", LogType.SUCCESS)
            for (y in updatedMaze.indices) {
                for (x in updatedMaze[0].indices) {
                    if (updatedMaze[y][x] == CellType.TERMINAL_DOOR) {
                        updatedMaze[y][x] = CellType.PATH
                    }
                }
            }
        }

        val obtainedKeycard = state.currentZone == com.example.data.Zone.BUILDING && state.buildingFloor == 2 && isSecretCache && !state.hasElevatorKeycard

        _uiState.update { stateNow ->
            stateNow.copy(
                screen = ActiveScreen.EXPLORATION,
                credits = stateNow.credits + bountyCredits,
                totalCreditsEarned = stateNow.totalCreditsEarned + bountyCredits,
                inventory = if (obtainedKeycard) updatedInventory + "Elevator Keycard" else updatedInventory,
                nodesHackedCount = stateNow.nodesHackedCount + 1,
                maze = updatedMaze,
                activePuzzle = null,
                hasElevatorKeycard = stateNow.hasElevatorKeycard || obtainedKeycard,
                dataFragments = stateNow.dataFragments + fragmentsExtracted,
                totalDataFragmentsExtracted = stateNow.totalDataFragmentsExtracted + fragmentsExtracted
            )
        }

        updatePerspective()
        addLog("💾 DATA FRAGMENTS EXTRACTED: +$fragmentsExtracted Data Fragments retrieved! [Vault Total: ${_uiState.value.dataFragments}]", LogType.SUCCESS)
        if (obtainedKeycard) {
            addLog("🔑 SECURE KEYCARD RETRIEVED FROM CRYPT-CACHE!", LogType.SUCCESS)
            addLog("ELEVATOR LINK ONLINE: You can now access the Express Elevator shaft in the building center!", LogType.SUCCESS)
        }
        if (isSecretCache) {
            addLog("CLASSIFIED VAULT INTRUSION SUCCEEDED!", LogType.SUCCESS)
            addLog("EXTRACTED ULTRA CREDITS: +$bountyCredits MB!", LogType.SUCCESS)
            addLog("RETRIEVED ENHANCED UTILITIES: $randomReward and standard sub-routines.", LogType.SUCCESS)
        } else {
            addLog("DECRYPTION CRACKED SUCCESSFULLY!", LogType.SUCCESS)
            addLog("RETRIEVED CREDITS: +$bountyCredits MB. EXTRACTED UTILITY: $randomReward", LogType.SUCCESS)
        }
    }

    private fun handleHackingFailure() {
        soundManager.playSecurityNodeHackFailureSound()
        val state = _uiState.value
        // Damage integrity as security payload penalty
        val penaltyDmg = 15 + (state.level * 5)
        val newIntegrity = maxOf(0, state.integrity - penaltyDmg)

        // Reset the node to empty path so they don't get locked out forever but have taken damage
        val updatedMaze = state.maze.map { it.clone() }.toTypedArray()
        if (state.targetNodeY in updatedMaze.indices && state.targetNodeX in updatedMaze[0].indices) {
            updatedMaze[state.targetNodeY][state.targetNodeX] = CellType.PATH
        }

        _uiState.update { stateNow ->
            stateNow.copy(
                screen = if (newIntegrity <= 0) ActiveScreen.GAME_OVER else ActiveScreen.EXPLORATION,
                integrity = newIntegrity,
                maze = updatedMaze,
                activePuzzle = null
            )
        }

        updatePerspective()
        addLog("BUFFER OVERFLOW: INTRUSION DETECTED!", LogType.ERROR)
        addLog("HARDWARE FEEDBACK DAMAGE SUSPENDED: -$penaltyDmg INTEGRITY.", LogType.ERROR)

        if (newIntegrity <= 0) {
            handleGameOver("Hacking Malware Core Injection Feedback")
        }
    }

    fun exitHackingMinigame() {
        _uiState.update { it.copy(screen = ActiveScreen.EXPLORATION, activePuzzle = null) }
        updatePerspective()
        addLog("BREACH PROTOCOL DISCONNECTED. NODE REMAINED ENCRYPTED.", LogType.ALERT)
    }

    // ----------------------------------------------------
    // Turn-Based Combat (Cyberpunk Style)
    // ----------------------------------------------------

    private fun triggerCombat(targetX: Int, targetY: Int) {
        val level = _uiState.value.level
        val enemy = GameEngine.spawnEnemy(level)

        _uiState.update { state ->
            val baseCombatShield = if (state.runnerClass == NetrunnerClass.CYBER_SHIELD) {
                minOf(state.playerMaxShield, 25 + 30)
            } else {
                25 // Base starting combat shield barrier
            }
            state.copy(
                activeEnemy = enemy,
                playerShield = baseCombatShield,
                targetNodeX = targetX,
                targetNodeY = targetY,
                enemyCombatAction = ""
            )
        }

        addLog("==========================================", LogType.ERROR)
        addLog("THREAT INTERCEPTED: ${enemy.name}!", LogType.ERROR)
        addLog("DESCRIPTION: ${enemy.description}", LogType.ALERT)
        addLog("PREPARING PAYLOAD COMPILER...", LogType.INFO)

        // Class-specific combat passive activations
        if (_uiState.value.runnerClass == NetrunnerClass.CYBER_SHIELD) {
            addLog("SENTINEL PROTOCOL: Hardening system barriers. +30 Shield initialized.", LogType.SUCCESS)
        }
    }

    fun executeCombatProgram(program: Program) {
        val state = _uiState.value
        val enemy = state.activeEnemy ?: return

        if (state.ram < program.ramCost) {
            addLog("INSUFFICIENT RAM: Requires ${program.ramCost}MB, but only ${state.ram}MB allocated.", LogType.ERROR)
            return
        }

        addLog("> RUNNING ${program.name}...", LogType.INFO)

        // Subtract RAM cost
        _uiState.update { it.copy(ram = it.ram - program.ramCost) }

        // Process Player Action: Damage Calculation based on stats
        val baseDmg = program.damage
        val statPower = (state.level * 2) + state.damageBonus
        var rawPlayerDamage = baseDmg + statPower

        // Balcony Vantage: +25% attack damage bonus
        val standCell = state.maze.getOrNull(state.gridY)?.getOrNull(state.gridX)
        if (standCell == com.example.data.CellType.ELEVATED_BALCONY) {
            rawPlayerDamage = (rawPlayerDamage * 1.25f).toInt()
            addLog("✨ BALCONY VANTAGE ACTIVE: Attack payload magnified by 25% from high-level gallery overlook!", LogType.SUCCESS)
        }

        var isCrit = false

        // Crit rate calculation
        val critRate = 10 + (state.ram * 2)
        val finalCritRate = if (state.runnerClass == NetrunnerClass.CODE_SLASHER) critRate + 25 else critRate

        if (Random.nextInt(100) < finalCritRate) {
            isCrit = true
            val critMultiplier = if (state.runnerClass == NetrunnerClass.CODE_SLASHER) 2.0f else 1.5f
            rawPlayerDamage = (rawPlayerDamage * critMultiplier).toInt()
        }

        // Buffer Overflow passive: damage amplified by remaining RAM
        if (state.runnerClass == NetrunnerClass.BUFFER_OVERFLOW) {
            val mult = 1.0f + (state.ram * 0.03f)
            rawPlayerDamage = (rawPlayerDamage * mult).toInt()
        }

        // Apply Damage to Enemy considering enemy Armor defense
        val enemyArmor = enemy.armor
        val effectiveArmor = if (isCrit) (enemyArmor * 0.5f).toInt() else enemyArmor
        val finalDmg = if (program.piercesDefense) {
            rawPlayerDamage
        } else {
            maxOf(2, rawPlayerDamage - effectiveArmor)
        }

        val enemyRemShield = maxOf(0, enemy.shield - finalDmg)
        val shieldDmg = enemy.shield - enemyRemShield
        val bodyDmg = finalDmg - shieldDmg
        val enemyRemIntegrity = maxOf(0, enemy.integrity - bodyDmg)

        enemy.shield = enemyRemShield
        enemy.integrity = enemyRemIntegrity

        // Detail the calculation step-by-step in the log
        addLog("[CALC]: Base:${baseDmg} + Stats:${statPower} = Raw:${baseDmg + statPower}", LogType.INFO)
        if (isCrit) {
            addLog("CRITICAL HIT! [x${if (state.runnerClass == NetrunnerClass.CODE_SLASHER) "2.0" else "1.5"}] Armor bypassed: ${effectiveArmor}/${enemyArmor}", LogType.SUCCESS)
        }
        addLog("Dealt ${finalDmg} damage to ${enemy.name} (Shield: -${shieldDmg}, Core: -${bodyDmg}) [Hostile Armor: ${enemyArmor}]", LogType.SUCCESS)

        // Process Heal / Shield Restore
        if (program.heal > 0) {
            val healed = minOf(state.maxIntegrity - state.integrity, program.heal)
            _uiState.update { it.copy(integrity = it.integrity + healed) }
            addLog("System integrity patch compiled: +$healed% Integrity.", LogType.SUCCESS)
        }

        if (program.shield > 0) {
            val shieldHealed = minOf(state.playerMaxShield - state.playerShield, program.shield)
            _uiState.update { it.copy(playerShield = it.playerShield + shieldHealed) }
            addLog("Temporary firewalls reinforced: +$shieldHealed Shield Barrier.", LogType.SUCCESS)
        }

        // Check if enemy dead
        if (enemy.integrity <= 0) {
            handleCombatVictory(enemy)
            return
        }

        // Enemy Counter-Attack Turn
        executeEnemyCombatTurn(enemy)
    }

    private fun executeEnemyCombatTurn(enemy: Enemy) {
        val state = _uiState.value

        val actions = listOf(
            "Trojan injection stream",
            "Rootkit port scan exploit",
            "Distributed Denial-of-Service packets",
            "Logic logicbomb payload"
        )
        val selectedAction = actions[Random.nextInt(actions.size)]
        var baseEnemyDmg = enemy.damage + Random.nextInt(-2, 3)
        if (baseEnemyDmg < 2) baseEnemyDmg = 2

        // Player Defense reduction
        val defenseModifier = state.defenseBonus
        val finalEnemyDmg = maxOf(1, baseEnemyDmg - defenseModifier)

        // Player Shield absorbs first
        val currentShield = state.playerShield
        val shieldDamage = minOf(currentShield, finalEnemyDmg)
        val remainingShield = currentShield - shieldDamage
        val integrityDamage = finalEnemyDmg - shieldDamage
        val newPlayerIntegrity = maxOf(0, state.integrity - integrityDamage)

        _uiState.update { stateNow ->
            stateNow.copy(
                integrity = newPlayerIntegrity,
                playerShield = remainingShield,
                enemyCombatAction = "${enemy.name} ran $selectedAction: Dealt $finalEnemyDmg damage. (Shield absorbed: $shieldDamage, Core hit: $integrityDamage)"
            )
        }

        addLog("${enemy.name} executes $selectedAction...", LogType.ERROR)
        if (shieldDamage > 0) {
            addLog("Player Shield absorbed $shieldDamage damage.", LogType.ALERT)
        }
        if (integrityDamage > 0) {
            addLog("System Integrity degraded by $integrityDamage%.", LogType.ERROR)
        }

        // Recover RAM at end of combat turn
        _uiState.update { stateNow ->
            stateNow.copy(ram = minOf(stateNow.maxRam, stateNow.ram + stateNow.ramRecoveryRate))
        }

        // Check player death
        if (newPlayerIntegrity <= 0) {
            handleGameOver("Destroyed by security process ${enemy.name}")
        }
    }

    private fun handleCombatVictory(enemy: Enemy) {
        val state = _uiState.value
        val bounty = enemy.bountyCredits

        // Remove node from map
        val updatedMaze = state.maze.map { it.clone() }.toTypedArray()
        if (state.targetNodeY in updatedMaze.indices && state.targetNodeX in updatedMaze[0].indices) {
            updatedMaze[state.targetNodeY][state.targetNodeX] = CellType.PATH
        }

        // Loot chances
        val gotItem = Random.nextInt(100) < 60
        val rewards = listOf("NanoMed.sys", "RAMBoost.exe", "Decryptor.pkg")
        val lootItem = if (gotItem) rewards[Random.nextInt(rewards.size)] else null

        val updatedInventory = state.inventory.toMutableList()
        if (lootItem != null) {
            updatedInventory.add(lootItem)
        }

        _uiState.update { stateNow ->
            stateNow.copy(
                screen = ActiveScreen.EXPLORATION,
                credits = stateNow.credits + bounty,
                totalCreditsEarned = stateNow.totalCreditsEarned + bounty,
                inventory = updatedInventory,
                maze = updatedMaze,
                activeEnemy = null
            )
        }

        updatePerspective()
        addLog("CRITICAL SUCCESS: PROCESS ${enemy.name} TERMINATED.", LogType.SUCCESS)
        addLog("Bounty extraction: +$bounty MB credits compiled.", LogType.SUCCESS)
        if (lootItem != null) {
            addLog("Discovered discarded payload bundle: $lootItem.", LogType.SUCCESS)
        }
        addExperience(50 + (state.level * 25))
    }

    fun fleeCombat() {
        val state = _uiState.value
        if (state.gameState == GameState.EXPLORATION) return

        // Backtrack player to starting safe point or previous cell
        // We can place them safely at (1, 1) or just escape with small penalty
        val penalty = 20
        val newCredits = maxOf(0, state.credits - penalty)

        _uiState.update { stateNow ->
            stateNow.copy(
                screen = ActiveScreen.EXPLORATION,
                gameState = GameState.EXPLORATION,
                credits = newCredits,
                activeEnemy = null,
                gridX = 1,
                gridY = 1 // Safe teleport back to sector start
            )
        }

        updatePerspective()
        revealCellsAround(1, 1)
        addLog("EMERGENCY ESCAPE ROUTE FLOODED. RETREATED TO PORT SECURE.", LogType.ALERT)
        addLog("Bypassed connection telemetry fees: -$penalty Credits.", LogType.ALERT)
    }

    // ----------------------------------------------------
    // Inventory Architecture & Equipment System
    // ----------------------------------------------------

    fun getStructuredInventorySlots(): List<com.example.data.InventorySlot> {
        val state = _uiState.value
        val itemsMap = state.inventory.groupingBy { it }.eachCount()

        var slots = itemsMap.map { (name, count) ->
            val gameItem = com.example.data.GameItemRegistry.getItemByName(name)
            val isEquipped = when (gameItem.equipmentSlot) {
                com.example.data.EquipmentSlot.WEAPON -> state.equippedWeaponItem?.name == name || state.equippedWeaponName == name
                com.example.data.EquipmentSlot.ARMOR -> state.equippedArmorItem?.name == name || state.equippedArmorName == name
                com.example.data.EquipmentSlot.UTILITY, com.example.data.EquipmentSlot.CYBERWARE -> state.equippedUtilityItem?.name == name || state.equippedUtilityName == name
                null -> false
            }
            com.example.data.InventorySlot(
                item = gameItem,
                quantity = count,
                isEquipped = isEquipped
            )
        }

        // Apply Category Filter if set
        val categoryFilter = state.selectedInventoryCategoryFilter
        if (categoryFilter != null) {
            slots = slots.filter { it.item.category == categoryFilter }
        }

        // Apply Sorting
        return when (state.inventorySortOption) {
            com.example.data.InventorySortOption.NAME -> slots.sortedBy { it.item.name }
            com.example.data.InventorySortOption.CATEGORY -> slots.sortedWith(compareBy({ it.item.category.ordinal }, { it.item.name }))
            com.example.data.InventorySortOption.RARITY -> slots.sortedWith(compareByDescending<com.example.data.InventorySlot> { it.item.rarity.ordinal }.thenBy { it.item.name })
            com.example.data.InventorySortOption.QUANTITY -> slots.sortedByDescending { it.quantity }
        }
    }

    fun addItemToInventory(itemName: String, quantity: Int = 1): Boolean {
        if (quantity <= 0 || itemName.isBlank()) return false
        val state = _uiState.value
        val newItems = List(quantity) { itemName }
        val updatedInventory = state.inventory + newItems

        val itemData = com.example.data.GameItemRegistry.getItemByName(itemName)

        _uiState.update { stateNow ->
            stateNow.copy(inventory = updatedInventory)
        }

        soundManager.playLootCollectionSound()
        addLog("${itemData.icon} ACQUIRED [${itemData.rarity.displayName.uppercase()}]: $itemName x$quantity (${itemData.category.displayName}) - ${itemData.description}", LogType.SUCCESS)

        viewModelScope.launch(Dispatchers.IO) {
            val entity = com.example.data.InventoryItemEntity(
                saveSlotId = "current_save",
                itemName = itemName,
                itemType = itemData.category.name,
                quantity = quantity,
                description = itemData.description
            )
            repository.insertInventoryItem(entity)
        }

        saveGame()
        return true
    }

    fun removeItemFromInventory(itemName: String, quantity: Int = 1): Boolean {
        val state = _uiState.value
        val currentCount = state.inventory.count { it.equals(itemName, ignoreCase = true) }
        if (currentCount < quantity) return false

        val updatedInventory = state.inventory.toMutableList()
        var removed = 0
        val iterator = updatedInventory.iterator()
        while (iterator.hasNext() && removed < quantity) {
            if (iterator.next().equals(itemName, ignoreCase = true)) {
                iterator.remove()
                removed++
            }
        }

        _uiState.update { it.copy(inventory = updatedInventory) }
        saveGame()
        return true
    }

    fun hasItemInInventory(itemName: String, quantity: Int = 1): Boolean {
        val count = _uiState.value.inventory.count { it.equals(itemName, ignoreCase = true) }
        return count >= quantity
    }

    fun equipItem(itemName: String): Boolean {
        val state = _uiState.value
        val actualItemName = state.inventory.firstOrNull { it.equals(itemName, ignoreCase = true) }
        if (actualItemName == null) {
            addLog("EQUIP FAILED: Item '$itemName' not found in inventory core.", LogType.ERROR)
            return false
        }

        val gameItem = com.example.data.GameItemRegistry.getItemByName(actualItemName)
        if (!gameItem.isEquippable || gameItem.equipmentSlot == null) {
            addLog("EQUIP FAILED: '$actualItemName' is not an equippable weapon or armor module.", LogType.ERROR)
            return false
        }

        when (gameItem.equipmentSlot) {
            com.example.data.EquipmentSlot.WEAPON -> {
                val oldWeapon = state.equippedWeaponItem
                var dmgBonusAcc = state.damageBonus + gameItem.damageBonus
                if (oldWeapon != null) {
                    dmgBonusAcc -= oldWeapon.damageBonus
                }
                _uiState.update { stateNow ->
                    stateNow.copy(
                        equippedWeaponItem = gameItem,
                        equippedWeaponName = gameItem.name,
                        damageBonus = maxOf(0, dmgBonusAcc)
                    )
                }
                addLog("⚔️ WEAPON EQUIPPED: ${gameItem.name} (+${gameItem.damageBonus} Attack Bonus).", LogType.SUCCESS)
            }
            com.example.data.EquipmentSlot.ARMOR -> {
                val oldArmor = state.equippedArmorItem
                var defBonusAcc = state.defenseBonus + gameItem.defenseBonus
                var hpBonusAcc = state.maxIntegrity + gameItem.integrityBonus
                if (oldArmor != null) {
                    defBonusAcc -= oldArmor.defenseBonus
                    hpBonusAcc -= oldArmor.integrityBonus
                }
                _uiState.update { stateNow ->
                    stateNow.copy(
                        equippedArmorItem = gameItem,
                        equippedArmorName = gameItem.name,
                        defenseBonus = maxOf(0, defBonusAcc),
                        maxIntegrity = maxOf(50, hpBonusAcc),
                        integrity = minOf(stateNow.integrity, maxOf(50, hpBonusAcc))
                    )
                }
                addLog("🛡️ ARMOR EQUIPPED: ${gameItem.name} (+${gameItem.defenseBonus} Defense, +${gameItem.integrityBonus} Integrity).", LogType.SUCCESS)
            }
            com.example.data.EquipmentSlot.UTILITY, com.example.data.EquipmentSlot.CYBERWARE -> {
                val oldUtil = state.equippedUtilityItem
                var ramBonusAcc = state.maxRam + gameItem.ramBonus
                var dmgBonusAcc = state.damageBonus + gameItem.damageBonus
                if (oldUtil != null) {
                    ramBonusAcc -= oldUtil.ramBonus
                    dmgBonusAcc -= oldUtil.damageBonus
                }
                _uiState.update { stateNow ->
                    stateNow.copy(
                        equippedUtilityItem = gameItem,
                        equippedUtilityName = gameItem.name,
                        maxRam = maxOf(4, ramBonusAcc),
                        damageBonus = maxOf(0, dmgBonusAcc)
                    )
                }
                addLog("🔌 UTILITY MODULE MOUNTED: ${gameItem.name} (+${gameItem.ramBonus} RAM, +${gameItem.damageBonus} Dmg).", LogType.SUCCESS)
            }
        }
        return true
    }

    fun unequipItemSlot(slot: com.example.data.EquipmentSlot): Boolean {
        val state = _uiState.value
        when (slot) {
            com.example.data.EquipmentSlot.WEAPON -> {
                val weapon = state.equippedWeaponItem ?: return false
                val newDmg = maxOf(0, state.damageBonus - weapon.damageBonus)
                _uiState.update { it.copy(equippedWeaponItem = null, equippedWeaponName = "Sparksteel Dagger", damageBonus = newDmg) }
                addLog("⚔️ UNEQUIPPED WEAPON: ${weapon.name}.", LogType.INFO)
            }
            com.example.data.EquipmentSlot.ARMOR -> {
                val armor = state.equippedArmorItem ?: return false
                val newDef = maxOf(0, state.defenseBonus - armor.defenseBonus)
                val newHp = maxOf(50, state.maxIntegrity - armor.integrityBonus)
                _uiState.update { it.copy(equippedArmorItem = null, equippedArmorName = "Basic Firewall Mesh", defenseBonus = newDef, maxIntegrity = newHp, integrity = minOf(it.integrity, newHp)) }
                addLog("🛡️ UNEQUIPPED ARMOR: ${armor.name}.", LogType.INFO)
            }
            com.example.data.EquipmentSlot.UTILITY, com.example.data.EquipmentSlot.CYBERWARE -> {
                val util = state.equippedUtilityItem ?: return false
                val newRam = maxOf(4, state.maxRam - util.ramBonus)
                val newDmg = maxOf(0, state.damageBonus - util.damageBonus)
                _uiState.update { it.copy(equippedUtilityItem = null, equippedUtilityName = "None", maxRam = newRam, damageBonus = newDmg) }
                addLog("🔌 UNEQUIPPED UTILITY MODULE: ${util.name}.", LogType.INFO)
            }
        }
        return true
    }

    fun scavengeCurrentCell() {
        val state = _uiState.value
        if (state.screen != ActiveScreen.EXPLORATION) {
            addLog("SCAVENGE ERROR: Must be exploring grid sector to search cache.", LogType.ERROR)
            return
        }

        val dropItem = com.example.data.GameItemRegistry.getRandomExplorationDrop(state.level)
        val bonusCredits = (kotlin.random.Random.nextInt(20, 80) * (1f + state.level * 0.2f)).toInt()

        _uiState.update { stateNow ->
            stateNow.copy(
                credits = stateNow.credits + bonusCredits,
                totalCreditsEarned = stateNow.totalCreditsEarned + bonusCredits
            )
        }
        addItemToInventory(dropItem.name)
        addExperience(25 + state.level * 10)
        addLog("🔎 SCAVENGE SUCCESSFUL: Extracted +$bonusCredits Credits & found ${dropItem.icon} ${dropItem.name}!", LogType.SUCCESS)
    }

    fun setInventoryCategoryFilter(category: com.example.data.InventoryCategory?) {
        _uiState.update { it.copy(selectedInventoryCategoryFilter = category) }
        val catName = category?.displayName ?: "All Categories"
        addLog("FILTER APPLIED: Inventory viewing [$catName].", LogType.INFO)
    }

    fun setInventorySortOption(option: com.example.data.InventorySortOption) {
        _uiState.update { it.copy(inventorySortOption = option) }
        addLog("SORT APPLIED: Inventory ordered by [${option.displayName}].", LogType.INFO)
    }

    fun discardInventoryItem(itemName: String) {
        if (removeItemFromInventory(itemName, 1)) {
            addLog("🗑️ DISCARDED: 1x $itemName purged from memory bank.", LogType.INFO)
        } else {
            addLog("DISCARD FAILED: Item '$itemName' not found in inventory.", LogType.ERROR)
        }
    }

    fun useInventoryItem(itemName: String) {
        val state = _uiState.value
        val actualItemName = state.inventory.firstOrNull { it.equals(itemName, ignoreCase = true) }
        if (actualItemName == null) return

        val gameItem = com.example.data.GameItemRegistry.getItemByName(actualItemName)

        // If item is equipment, redirect to equip
        if (gameItem.isEquippable) {
            equipItem(actualItemName)
            return
        }

        val updatedInventory = state.inventory.toMutableList()
        updatedInventory.remove(actualItemName)

        var logText = ""

        _uiState.update { stateNow ->
            var newIntegrity = stateNow.integrity
            var newRam = stateNow.ram
            var newCredits = stateNow.credits
            var newTotCredits = stateNow.totalCreditsEarned
            var newDmg = stateNow.damageBonus
            var newDef = stateNow.defenseBonus
            var newPredWeather = stateNow.predictedWeather

            if (gameItem.healIntegrity > 0) {
                val healed = minOf(stateNow.maxIntegrity - stateNow.integrity, gameItem.healIntegrity)
                newIntegrity += healed
                logText += "Restored $healed HP Integrity. "
            }
            if (gameItem.restoreRam > 0) {
                val boosted = minOf(stateNow.maxRam - stateNow.ram, gameItem.restoreRam)
                newRam += boosted
                logText += "Allocated $boosted MB RAM. "
            }
            if (gameItem.grantCredits > 0) {
                newCredits += gameItem.grantCredits
                newTotCredits += gameItem.grantCredits
                logText += "Extracted +${gameItem.grantCredits} MB Credits. "
            }
            if (gameItem.damageBonus > 0) {
                newDmg += gameItem.damageBonus
                logText += "Overclocked Attack (+${gameItem.damageBonus} Dmg). "
            }
            if (gameItem.defenseBonus > 0) {
                newDef += gameItem.defenseBonus
                logText += "Fortified Defense (+${gameItem.defenseBonus} Def). "
            }

            when (actualItemName) {
                "GibsonForecast.sys" -> {
                    val stepsRemaining = (stateNow.nextEventSteps - stateNow.stepsSinceLastEvent).coerceAtLeast(1)
                    val nextWeather = stateNow.predictedWeather ?: com.example.data.CyberWeather.VALUES.filter { it != com.example.data.CyberWeather.CLEAR }.random()
                    newPredWeather = nextWeather
                    logText += "Next weather [${nextWeather.title}] in $stepsRemaining steps. "
                }
                "AntiVirus.sys" -> {
                    logText += "Purged all debuffs. "
                }
            }

            stateNow.copy(
                integrity = newIntegrity,
                ram = newRam,
                credits = newCredits,
                totalCreditsEarned = newTotCredits,
                damageBonus = newDmg,
                defenseBonus = newDef,
                predictedWeather = newPredWeather,
                inventory = updatedInventory,
                playerStatusEffects = if (actualItemName == "AntiVirus.sys") stateNow.playerStatusEffects.filter { !it.type.isDebuff } else stateNow.playerStatusEffects
            )
        }

        if (gameItem.grantXp > 0) {
            addExperience(gameItem.grantXp)
        }

        if (logText.isEmpty()) {
            logText = "COMPILED $actualItemName utility."
        } else {
            logText = "COMPILED $actualItemName: " + logText.trim()
        }

        addLog(logText, LogType.SUCCESS)

        if (gameItem.statusEffectToApply != null) {
            if (gameItem.targetSelf) {
                applyStatusEffectToPlayer(gameItem.statusEffectToApply, turns = gameItem.statusEffectTurns, source = actualItemName)
            } else {
                applyStatusEffectToEnemy(gameItem.statusEffectToApply, turns = gameItem.statusEffectTurns, magnitude = 10, source = actualItemName)
            }
        }

        val gs = _uiState.value.gameState
        if (gs == GameState.PLAYER_TURN || gs == GameState.COMBAT_START) {
            recordPlayerAction(
                actionType = CombatActionType.USE_ITEM,
                summary = "Compiled $actualItemName utility",
                healAmount = gameItem.healIntegrity,
                statusApplied = gameItem.statusEffectToApply?.displayName
            )
            executeEnemyCombatTurnInline()
        }
    }

    // ----------------------------------------------------
    // Shop & Upgrades Console
    // ----------------------------------------------------

    fun enterShop() {
        _uiState.update { it.copy(screen = ActiveScreen.UPGRADE_STORE) }
        addLog("CONNECTING TO BLACK-MARKET CYBERNET WORKSTATION...", LogType.INFO)
    }

    fun exitShop() {
        _uiState.update { it.copy(screen = ActiveScreen.EXPLORATION) }
        updatePerspective()
        addLog("DISCONNECTED FROM SHOP SERVER. TELEMETRY RESUMED.")
    }

    fun purchaseCyberware(cyberware: Cyberware) {
        val state = _uiState.value
        if (state.credits < cyberware.cost) {
            addLog("PURCHASE DECLINED: INSIGNIFICANT CREDITS BLOCK.", LogType.ERROR)
            return
        }

        // Check if already installed to avoid duplicates
        if (state.installedCyberware.any { it.id == cyberware.id }) {
            addLog("INSTALLATION BLOCKED: HARDWARE MODULE ALREADY MOUNTED.", LogType.ERROR)
            return
        }

        val updatedCyberware = state.installedCyberware.toMutableList()
        updatedCyberware.add(cyberware)

        _uiState.update { stateNow ->
            stateNow.copy(
                credits = stateNow.credits - cyberware.cost,
                installedCyberware = updatedCyberware,
                maxIntegrity = stateNow.maxIntegrity + cyberware.integrityBonus,
                integrity = stateNow.integrity + cyberware.integrityBonus,
                maxRam = stateNow.maxRam + cyberware.ramBonus,
                ram = stateNow.ram + cyberware.ramBonus,
                ramRecoveryRate = stateNow.ramRecoveryRate + cyberware.recoveryBonus,
                damageBonus = stateNow.damageBonus + cyberware.damageBonus,
                defenseBonus = stateNow.defenseBonus + cyberware.defenseBonus
            )
        }

        addLog("HARDWARE INTEGRATION SUCCESS: ${cyberware.name} INSTALLED.", LogType.SUCCESS)
        addLog("MODULE SPECS: ${cyberware.description}", LogType.INFO)
    }

    // Buy consumable utilities
    fun purchaseConsumable(name: String, cost: Int) {
        val state = _uiState.value
        if (state.credits < cost) {
            addLog("PURCHASE DECLINED: INSUFFICIENT MEMORY.", LogType.ERROR)
            return
        }

        val updatedInventory = state.inventory.toMutableList()
        updatedInventory.add(name)

        _uiState.update { stateNow ->
            stateNow.copy(
                credits = stateNow.credits - cost,
                inventory = updatedInventory
            )
        }
        addLog("DOWNLOAD COMPLETE: $name retrieved to virtual storage.", LogType.SUCCESS)
    }

    // ----------------------------------------------------
    // High Score / Database Operations
    // ----------------------------------------------------

    fun viewLeaderboard() {
        _uiState.update { it.copy(screen = ActiveScreen.LEADERBOARD) }
        addLog("BROADCASTING MAIN HISTORIC RECORDS DATABASE...", LogType.SUCCESS)
    }

    // ----------------------------------------------------
    // Sparse Voxel DAG (SVDAG) World Engine Operations
    // ----------------------------------------------------

    fun ensureSvdagInitialized(targetDepth: Int = 7) {
        if (_uiState.value.svdagWorld == null || _uiState.value.svdagWorld?.maxDepth != targetDepth) {
            val (dag, stats) = com.example.data.svdag.SvdagWorldBuilder.generateCyberspaceMegaSector(targetDepth)
            val initialIce = com.example.data.svdag.SvdagIcePathfinder.generateDefaultPatrolEntities(dag)
            val pPos = Triple(dag.gridSize / 2, dag.gridSize / 2, dag.gridSize / 2)
            val hideStatus = com.example.data.svdag.SvdagIcePathfinder.evaluatePlayerHidingStatus(pPos.first, pPos.second, pPos.third, dag)
            _uiState.update {
                it.copy(
                    svdagWorld = dag,
                    svdagStats = stats,
                    svdagScaleDepth = targetDepth,
                    svdagIceEntities = initialIce,
                    svdagPlayerPos = pPos,
                    svdagPlayerHideStatus = hideStatus
                )
            }
        }
    }

    fun initOrRegenerateSvdag(targetDepth: Int = 7, seed: Long = System.currentTimeMillis()) {
        viewModelScope.launch(Dispatchers.Default) {
            val (dag, stats) = com.example.data.svdag.SvdagWorldBuilder.generateCyberspaceMegaSector(targetDepth, seed)
            val initialIce = com.example.data.svdag.SvdagIcePathfinder.generateDefaultPatrolEntities(dag)
            val pPos = Triple(dag.gridSize / 2, dag.gridSize / 2, dag.gridSize / 2)
            val hideStatus = com.example.data.svdag.SvdagIcePathfinder.evaluatePlayerHidingStatus(pPos.first, pPos.second, pPos.third, dag)
            withContext(Dispatchers.Main) {
                _uiState.update {
                    it.copy(
                        svdagWorld = dag,
                        svdagStats = stats,
                        svdagScaleDepth = targetDepth,
                        svdagIceEntities = initialIce,
                        svdagPlayerPos = pPos,
                        svdagPlayerHideStatus = hideStatus
                    )
                }
                addLog("SVDAG WORLD BUILDER: Generated ${dag.gridSize}³ Voxels (${stats.totalNodes} DAG Nodes)", LogType.SUCCESS)
                addLog("SVDAG DEDUPLICATION: ${String.format(java.util.Locale.US, "%.1f%%", stats.compressionRatio)} memory reduction.", LogType.INFO)
                addLog("🛡️ SVDAG ICE SECURITY: Spawned ${initialIce.size} hallway patrol daemons with A* pathfinding.", LogType.ALERT)
            }
        }
    }

    fun moveSvdagPlayer(dx: Int, dy: Int, dz: Int) {
        val dag = _uiState.value.svdagWorld ?: return
        val currentP = _uiState.value.svdagPlayerPos
        val nx = (currentP.first + dx).coerceIn(0, dag.gridSize - 1)
        val ny = (currentP.second + dy).coerceIn(0, dag.gridSize - 1)
        val nz = (currentP.third + dz).coerceIn(0, dag.gridSize - 1)
        val newPos = Triple(nx, ny, nz)

        val hideStatus = com.example.data.svdag.SvdagIcePathfinder.evaluatePlayerHidingStatus(nx, ny, nz, dag, _uiState.value.maze)
        _uiState.update {
            it.copy(
                svdagPlayerPos = newPos,
                svdagPlayerHideStatus = hideStatus
            )
        }
        if (hideStatus.isHidden) {
            addLog("🙈 STEALTH EVASION: Player reached (${nx}, ${ny}, ${nz}) - ${hideStatus.hideReason}", LogType.INFO)
        } else {
            addLog("👟 PLAYER MOVED: Position (${nx}, ${ny}, ${nz}) - EXPOSED IN HALLWAY", LogType.INFO)
        }
    }

    fun tickSvdagIceAI() {
        val dag = _uiState.value.svdagWorld ?: return
        val currentIce = _uiState.value.svdagIceEntities
        val pPos = _uiState.value.svdagPlayerPos
        val maze = _uiState.value.maze

        val updatedIceList = mutableListOf<com.example.data.svdag.IceEntity>()
        var playerIntercepted = false

        for (ice in currentIce) {
            val res = com.example.data.svdag.SvdagIcePathfinder.tickIceEntity(ice, pPos, dag, maze)
            updatedIceList.add(res.updatedIce)
            if (res.actionMessage.isNotEmpty()) {
                val logType = when (res.updatedIce.alertLevel) {
                    com.example.data.svdag.IceAlertLevel.HUNTING -> LogType.ALERT
                    com.example.data.svdag.IceAlertLevel.SUSPICIOUS -> LogType.INFO
                    com.example.data.svdag.IceAlertLevel.PATROL -> LogType.INFO
                }
                addLog(res.actionMessage, logType)
            }
            if (res.interceptedPlayer) {
                playerIntercepted = true
            }
        }

        val hideStatus = com.example.data.svdag.SvdagIcePathfinder.evaluatePlayerHidingStatus(pPos.first, pPos.second, pPos.third, dag, maze)

        _uiState.update {
            it.copy(
                svdagIceEntities = updatedIceList,
                svdagPlayerHideStatus = hideStatus
            )
        }

        if (playerIntercepted && !hideStatus.isHidden) {
            soundManager.playHackingErrorSound()
            addLog("💥 CRITICAL SECURITY BREACH: ICE Security Patrol intercepted player in hallway!", LogType.ALERT)
            triggerCombatInline(pPos.first, pPos.second)
        }
    }

    fun modifySvdagVoxel(x: Int, y: Int, z: Int, type: com.example.data.svdag.VoxelType) {
        val currentDag = _uiState.value.svdagWorld ?: return
        currentDag.setVoxel(x, y, z, type)
        val newStats = currentDag.getStats(lodLevel = _uiState.value.svdagLodLevel)
        _uiState.update { it.copy(svdagStats = newStats) }
    }

    fun setSvdagLodLevel(lod: Int) {
        val activeLod = lod.coerceIn(0, 4)
        val dag = _uiState.value.svdagWorld
        val newStats = dag?.getStats(lodLevel = activeLod) ?: _uiState.value.svdagStats
        _uiState.update { it.copy(svdagLodLevel = activeLod, svdagStats = newStats) }
        val cellSize = 1 shl activeLod
        addLog("SVDAG LOD SYSTEM: Switched to Level of Detail $activeLod ($cellSize³ Voxel Block Aggregation)", LogType.INFO)
    }

    fun triggerSvdagScan(originX: Int? = null, originY: Int? = null, originZ: Int? = null, radius: Int = 16) {
        val currentDag = _uiState.value.svdagWorld ?: return
        val ox = originX ?: (currentDag.gridSize / 2)
        val oy = originY ?: (currentDag.gridSize / 2)
        val oz = originZ ?: (currentDag.gridSize / 2)

        val summary = com.example.data.svdag.SvdagScannerService.performSvdagScan(
            dag = currentDag,
            originX = ox,
            originY = oy,
            originZ = oz,
            radius = radius,
            activeIceEntities = _uiState.value.svdagIceEntities
        )

        val now = System.currentTimeMillis()
        val rippleState = com.example.data.svdag.SvdagScannerService.computeRippleState(
            scanTimestamp = now,
            currentTimeMs = now,
            originX = ox.toFloat(),
            originY = oy.toFloat(),
            originZ = oz.toFloat(),
            maxRadius = radius.toFloat(),
            detectedItems = summary.items
        )

        _uiState.update {
            it.copy(
                svdagScanSummary = summary,
                svdagRippleState = rippleState,
                scanTimestamp = now
            )
        }

        soundManager.playScannerPingSound()
        soundManager.playScannerDetectionSound(
            itemCount = summary.interactiveCount,
            hasSecrets = summary.secretCount > 0,
            hasBypass = summary.alternativePathCount > 0
        )
        addLog("📡 SVDAG SCANNER SERVICE EXECUTED: Radius $radius Voxels sonar sweep!", LogType.SUCCESS)
        addLog("  ↳ Detected ${summary.interactiveCount} Interactive Objects, ${summary.secretCount} Classified Secrets, ${summary.alternativePathCount} Bypass Vents.", LogType.INFO)
    }

    fun enterSvdagWorldInspector() {
        ensureSvdagInitialized(_uiState.value.svdagScaleDepth)
        _uiState.update { it.copy(screen = ActiveScreen.SVDAG_WORLD_BUILDER) }
        addLog("OPENING SVDAG HIGH-SCALE WORLD INSPECTOR...", LogType.SUCCESS)
    }

    fun exitSvdagWorldInspector() {
        if (_uiState.value.runnerName.isEmpty()) {
            _uiState.update { it.copy(screen = ActiveScreen.START_MENU) }
        } else {
            _uiState.update { it.copy(screen = ActiveScreen.EXPLORATION) }
            updatePerspective()
        }
    }

    fun exitLeaderboard() {
        if (_uiState.value.integrity <= 0) {
            _uiState.update { it.copy(screen = ActiveScreen.GAME_OVER) }
        } else if (_uiState.value.runnerName.isEmpty()) {
            _uiState.update { it.copy(screen = ActiveScreen.START_MENU) }
        } else {
            _uiState.update { it.copy(screen = ActiveScreen.EXPLORATION) }
            updatePerspective()
        }
    }

    fun clearHighScores() {
        viewModelScope.launch {
            repository.clearAll()
            addLog("MAINFRAME LOGS PURGED SUCCESSFULLY.", LogType.ALERT)
        }
    }

    // --- DATA FRAGMENTS & COSMETIC VAULT METHODS ---

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
        addLog("💾 DATA FRAGMENTS EXTRACTED: +$amount Fragments from $sourceDescription! [Total: ${_uiState.value.dataFragments}]", LogType.SUCCESS)
    }

    fun unlockCosmeticTheme(themeId: String) {
        val theme = com.example.data.CosmeticTheme.fromId(themeId)
        val s = _uiState.value
        if (s.unlockedThemes.contains(themeId)) {
            addLog("🎨 THEME ALREADY UNLOCKED: ${theme.title}", LogType.INFO)
            return
        }
        if (s.dataFragments < theme.cost) {
            soundManager.playHackingErrorSound()
            addLog("❌ INSUFFICIENT DATA FRAGMENTS: Need ${theme.cost} Fragments (Have ${s.dataFragments})", LogType.ALERT)
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
        addLog("🎨 COSMETIC THEME UNLOCKED & EQUIPPED: ${theme.title}!", LogType.SUCCESS)
    }

    fun setActiveTheme(themeId: String) {
        val s = _uiState.value
        if (!s.unlockedThemes.contains(themeId)) {
            addLog("🔒 THEME LOCKED: Unlock first with Data Fragments.", LogType.ALERT)
            return
        }
        val theme = com.example.data.CosmeticTheme.fromId(themeId)
        _uiState.update { it.copy(activeCosmeticTheme = themeId) }
        soundManager.playTerminalKeyPressSound()
        addLog("🎨 TERMINAL COSMETIC THEME EQUIPPED: ${theme.title}", LogType.INFO)
    }

    fun unlockPromptStyle(promptId: String) {
        val prompt = com.example.data.TerminalPromptStyle.fromId(promptId)
        val s = _uiState.value
        if (s.unlockedPrompts.contains(promptId)) {
            addLog("💻 PROMPT ALREADY UNLOCKED: ${prompt.title}", LogType.INFO)
            return
        }
        if (s.dataFragments < prompt.cost) {
            soundManager.playHackingErrorSound()
            addLog("❌ INSUFFICIENT DATA FRAGMENTS: Need ${prompt.cost} Fragments (Have ${s.dataFragments})", LogType.ALERT)
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
        addLog("💻 TERMINAL PROMPT UNLOCKED & EQUIPPED: ${prompt.title} (${prompt.promptString})!", LogType.SUCCESS)
    }

    fun setActivePromptStyle(promptId: String) {
        val s = _uiState.value
        if (!s.unlockedPrompts.contains(promptId)) {
            addLog("🔒 PROMPT LOCKED: Unlock first with Data Fragments.", LogType.ALERT)
            return
        }
        val prompt = com.example.data.TerminalPromptStyle.fromId(promptId)
        _uiState.update { it.copy(activePromptStyle = promptId) }
        soundManager.playTerminalKeyPressSound()
        addLog("💻 ACTIVE TERMINAL PROMPT SET: ${prompt.promptString}", LogType.INFO)
    }

    fun unlockPerformanceBuff(buffId: String) {
        val buff = com.example.data.PerformanceBuff.fromId(buffId) ?: return
        val s = _uiState.value
        if (s.unlockedBuffs.contains(buffId)) {
            addLog("⚡ BUFF ALREADY UNLOCKED: ${buff.title}", LogType.INFO)
            return
        }
        if (s.dataFragments < buff.cost) {
            soundManager.playHackingErrorSound()
            addLog("❌ INSUFFICIENT DATA FRAGMENTS: Need ${buff.cost} Fragments (Have ${s.dataFragments})", LogType.ALERT)
            return
        }

        _uiState.update { stateNow ->
            val updatedActive = stateNow.activeBuffs + buffId
            var updatedIntegrity = stateNow.integrity
            var updatedMaxIntegrity = stateNow.maxIntegrity

            // If thermal shield buff unlocked, apply integrity bonus
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
        addLog("⚡ PERFORMANCE BUFF UNLOCKED & ACTIVATED: ${buff.title}! ${buff.description}", LogType.SUCCESS)
    }

    fun togglePerformanceBuff(buffId: String) {
        val s = _uiState.value
        if (!s.unlockedBuffs.contains(buffId)) {
            addLog("🔒 BUFF LOCKED: Unlock first with Data Fragments.", LogType.ALERT)
            return
        }
        val buff = com.example.data.PerformanceBuff.fromId(buffId) ?: return
        val isCurrentlyActive = s.activeBuffs.contains(buffId)
        val newActiveSet = if (isCurrentlyActive) s.activeBuffs - buffId else s.activeBuffs + buffId

        _uiState.update { it.copy(activeBuffs = newActiveSet) }
        soundManager.playTerminalKeyPressSound()
        if (!isCurrentlyActive) {
            addLog("⚡ BUFF ACTIVATED: ${buff.title} (${buff.description})", LogType.SUCCESS)
        } else {
            addLog("🔌 BUFF DEACTIVATED: ${buff.title}", LogType.INFO)
        }
    }

    fun enterDataVaultScreen() {
        _uiState.update { it.copy(screen = ActiveScreen.DATA_FRAGMENTS_VAULT) }
        soundManager.playTerminalCommandSound()
        addLog("🔓 ACCESSING DATA FRAGMENT DECRYPTION VAULT...", LogType.SUCCESS)
    }

    fun exitDataVaultScreen() {
        if (_uiState.value.runnerName.isEmpty()) {
            _uiState.update { it.copy(screen = ActiveScreen.START_MENU) }
        } else {
            _uiState.update { it.copy(screen = ActiveScreen.EXPLORATION) }
            updatePerspective()
        }
    }

    private fun handleGameOver(cause: String) {
        _uiState.update { it.copy(screen = ActiveScreen.GAME_OVER, runOutcome = cause) }
        addLog("==========================================", LogType.ERROR)
        addLog("SYSTEM CORE FAILURE! RETRANSMITTING DATA RECOVERY...", LogType.ERROR)
        addLog("CRITICAL COLLAPSE CAUSE: $cause", LogType.ERROR)

        // Save run record to database
        val state = _uiState.value
        val record = RunRecord(
            runnerName = state.runnerName,
            runnerClass = state.runnerClass.title,
            levelReached = state.level,
            nodesHacked = state.nodesHackedCount,
            creditsEarned = state.totalCreditsEarned,
            outcome = "DECEASED"
        )

        viewModelScope.launch {
            repository.insert(record)
        }
    }

    fun disconnectRunSuccessfully() {
        val state = _uiState.value
        if (state.integrity <= 0) return

        addLog("VOLUNTARY EXTRACTION: UPLOADING RUN DATA...", LogType.SUCCESS)

        val record = RunRecord(
            runnerName = state.runnerName,
            runnerClass = state.runnerClass.title,
            levelReached = state.level,
            nodesHacked = state.nodesHackedCount,
            creditsEarned = state.totalCreditsEarned,
            outcome = "DISCONNECTED"
        )

        viewModelScope.launch {
            repository.insert(record)
        }

        _uiState.update { it.copy(screen = ActiveScreen.GAME_OVER, runOutcome = "Safe Connection Dissolution") }
    }

    fun restartGame() {
        _uiState.update { GameUiState(screen = ActiveScreen.START_MENU) }
        addLog("REBOOTING TERMINAL CORE V8.91...", LogType.ALERT)
        addLog("SELECT ARCHETYPE PROFILE TO COMPILE.", LogType.INFO)
    }

    fun startNewRun() {
        _uiState.update { GameUiState(screen = ActiveScreen.CHARACTER_CREATION) }
        addLog("ESTABLISHING SECURE CONNECTION...", LogType.SUCCESS)
        addLog("SELECT NETRUNNER ARCHETYPE PROFILE TO COMPILE.", LogType.INFO)
    }

    private var previousScreenBeforeMenu: ActiveScreen = ActiveScreen.CHARACTER_CREATION

    fun returnToStartMenu() {
        val currentScreen = _uiState.value.screen
        if (currentScreen != ActiveScreen.START_MENU) {
            previousScreenBeforeMenu = currentScreen
        }
        _uiState.update { it.copy(screen = ActiveScreen.START_MENU) }
    }

    fun resumeGame() {
        _uiState.update { it.copy(screen = previousScreenBeforeMenu) }
        updatePerspective()
    }

    fun runTerminalCommand(commandText: String) {
        val trimmed = commandText.trim()
        if (trimmed.isEmpty()) return

        // Print command in the log console
        addLog("> $trimmed", LogType.INFO)

        val parts = trimmed.split(Regex("\\s+"))
        val mainCommand = parts[0].lowercase()

        val state = _uiState.value

        // Intercept input when combat pattern matching minigame is active
        if (state.activeCombatHack != null) {
            when (mainCommand) {
                "clear" -> {
                    clearCombatHackBuffer()
                    return
                }
                "abort", "exit", "cancel" -> {
                    abortCombatHack()
                    return
                }
                else -> {
                    val symbolInput = if (mainCommand == "hack" && parts.size > 1) parts[1].uppercase() else mainCommand.uppercase()
                    if (state.activeCombatHack.availablePool.contains(symbolInput)) {
                        selectCombatHackSymbol(symbolInput)
                        return
                    } else {
                        addLog("HACK PATTERN COMMAND: Enter a valid symbol e.g. '${state.activeCombatHack.availablePool.first()}', 'clear', or 'abort'", LogType.ALERT)
                        return
                    }
                }
            }
        }

        when (mainCommand) {
            "help", "?" -> {
                addLog("=== CYBER-TERMINAL COMMAND INTERPRETER ===", LogType.SUCCESS)
                addLog("NAVIGATION: 'forward'/'w'/'n', 'backward'/'s', 'left'/'a', 'right'/'d'", LogType.INFO)
                addLog("INTERACTION: 'interact'/'use'/'e' (activate console/portal/cache/elevator)", LogType.INFO)
                addLog("COMBAT ACTIONS: 'attack'/'hit', 'defend'/'block', 'flee'/'run'", LogType.INFO)
                addLog("COMBAT STANCE: 'style slash'/'chop'/'thrust', or 'stance <style>'", LogType.INFO)
                addLog("INVENTORY: 'inventory'/'items', 'use <item>', 'equip <item>', 'unequip <slot>'", LogType.INFO)
                addLog("EXPLORATION ARCHITECTURE: 'scavenge'/'search', 'drop <item>', 'sort <option>', 'category <type>'", LogType.INFO)
                addLog("DATA VAULT & COSMETICS: 'vault' / 'fragments' / 'frags' (open Decryption Vault)", LogType.INFO)
                addLog("SYSTEM: 'status'/'stats', 'save', 'load', 'menu', 'shop', 'clear'", LogType.INFO)
                addLog("HACKING: 'hack <row> <col>' (e.g. 'hack 2 3')", LogType.INFO)
            }
            "w", "n", "north", "up", "forward", "move" -> {
                if (state.screen == ActiveScreen.EXPLORATION) {
                    moveForward()
                } else {
                    addLog("ERROR: Movement command only valid during active exploration.", LogType.ERROR)
                }
            }
            "s", "south", "back", "backward", "down" -> {
                if (state.screen == ActiveScreen.EXPLORATION) {
                    moveBackward()
                } else {
                    addLog("ERROR: Movement command only valid during active exploration.", LogType.ERROR)
                }
            }
            "a", "west", "left", "turnleft" -> {
                if (state.screen == ActiveScreen.EXPLORATION) {
                    turnLeft()
                } else {
                    addLog("ERROR: Turn command only valid during active exploration.", LogType.ERROR)
                }
            }
            "d", "east", "right", "turnright" -> {
                if (state.screen == ActiveScreen.EXPLORATION) {
                    turnRight()
                } else {
                    addLog("ERROR: Turn command only valid during active exploration.", LogType.ERROR)
                }
            }
            "e", "interact", "activate" -> {
                if (state.screen == ActiveScreen.EXPLORATION) {
                    interact()
                } else {
                    addLog("ERROR: Interaction command only valid during active exploration.", LogType.ERROR)
                }
            }
            "vault", "fragments", "frags", "datavault" -> {
                enterDataVaultScreen()
            }
            "status", "stats", "info", "xp", "level", "lvl" -> {
                addLog("--- RUNNER INTEGRITY PROFILE ---", LogType.SUCCESS)
                addLog("NAME: ${state.runnerName.ifEmpty { "UNNAMED" }} | NETRUNNER LEVEL: ${state.characterLevel} (ICE DEPTH: ${state.level})", LogType.INFO)
                addLog("EXPERIENCE: ${state.characterXp} / ${state.xpToNextLevel} XP (${((state.characterXp.toFloat() / state.xpToNextLevel.coerceAtLeast(1)) * 100).toInt()}% to Next Level)", LogType.SUCCESS)
                addLog("INTEGRITY: ${state.integrity}/${state.maxIntegrity} | RAM: ${state.ram}/${state.maxRam}MB | SHIELD: ${state.playerShield}/${state.playerMaxShield}", LogType.INFO)
                addLog("CREDITS: ${state.credits}MB | DAMAGE BONUS: +${state.damageBonus}", LogType.INFO)
                addLog("WEAPON: ${state.equippedWeaponName} | STANCE: ${state.selectedCombatStyle}", LogType.INFO)
                addLog("ZONE: ${state.currentZone} | INVENTORY: ${state.inventory.joinToString(", ")}", LogType.INFO)
            }
            "attack", "hit", "fight", "swing", "strike" -> {
                if (state.gameState != GameState.EXPLORATION) {
                    combatAttack()
                } else {
                    addLog("ERROR: Combat actions are only valid during active hostile combat.", LogType.ERROR)
                }
            }
            "defend", "block", "shield" -> {
                if (state.gameState != GameState.EXPLORATION) {
                    combatDefend()
                } else {
                    addLog("ERROR: Combat actions are only valid during active hostile combat.", LogType.ERROR)
                }
            }
            "flee", "run", "escape" -> {
                if (state.gameState != GameState.EXPLORATION) {
                    fleeCombat()
                } else {
                    addLog("ERROR: Combat actions are only valid during active hostile combat.", LogType.ERROR)
                }
            }
            "stance", "style" -> {
                addLog("COMBAT STANCE: Single unified Strike stance active.", LogType.INFO)
            }
            "use" -> {
                val itemName = parts.drop(1).joinToString(" ")
                if (itemName.isEmpty()) {
                    addLog("ERROR: Specify item name. E.g. 'use NanoMed.sys'.", LogType.ERROR)
                } else {
                    val matchingItem = state.inventory.firstOrNull { it.equals(itemName, ignoreCase = true) }
                    if (matchingItem != null) {
                        useInventoryItem(matchingItem)
                    } else {
                        addLog("ERROR: Item '$itemName' not found in inventory.", LogType.ERROR)
                    }
                }
            }
            "implants", "clinic", "cyberware", "cybernetics" -> {
                openCyberwareClinic()
                addLog("--- CYBERNETIC SURGERY CLINIC & IMPLANT MATRIX OPENED ---", LogType.SUCCESS)
                addLog("Type 'install <implantName>' or 'uninstall <slot>' or click UI surgery cards.", LogType.INFO)
            }
            "install", "implant" -> {
                val query = parts.drop(1).joinToString(" ")
                if (query.isEmpty()) {
                    addLog("ERROR: Specify implant name. E.g. 'install Subdermal Weave v1'.", LogType.ERROR)
                } else {
                    val matching = com.example.data.CyberwareImplantRegistry.ALL_IMPLANTS.firstOrNull { 
                        it.name.equals(query, ignoreCase = true) || it.name.contains(query, ignoreCase = true) 
                    }
                    if (matching != null) {
                        installImplant(matching)
                    } else {
                        addLog("ERROR: Implant '$query' not found in Cyberware Registry.", LogType.ERROR)
                    }
                }
            }
            "uninstall" -> {
                val slotArg = parts.getOrNull(1)?.lowercase()
                val slot = when (slotArg) {
                    "neural", "head", "cortex" -> com.example.data.ImplantBodySlot.NEURAL_CORTEX
                    "ocular", "eye", "eyes" -> com.example.data.ImplantBodySlot.OCULAR_ARRAY
                    "subdermal", "skin", "armor", "chassis" -> com.example.data.ImplantBodySlot.SUBDERMAL_CHASSIS
                    "heart", "bio", "pump" -> com.example.data.ImplantBodySlot.SYNTH_HEART
                    "limbs", "legs", "arms", "actuators" -> com.example.data.ImplantBodySlot.CYBER_ACTUATORS
                    else -> null
                }
                if (slot != null) {
                    uninstallImplant(slot)
                } else {
                    addLog("ERROR: Specify slot: 'neural', 'ocular', 'subdermal', 'heart', or 'limbs'.", LogType.ERROR)
                }
            }
            "inventory", "inv", "items" -> {
                addLog("=== RUNNER VIRTUAL STORAGE MANIFEST ===", LogType.SUCCESS)
                addLog("EQUIPPED WEAPON: ${state.equippedWeaponName} (+${state.equippedWeaponItem?.damageBonus ?: 0} Dmg)", LogType.INFO)
                addLog("EQUIPPED ARMOR: ${state.equippedArmorName} (+${state.equippedArmorItem?.defenseBonus ?: 0} Def)", LogType.INFO)
                addLog("EQUIPPED UTILITY: ${state.equippedUtilityName}", LogType.INFO)
                val slots = getStructuredInventorySlots()
                if (slots.isEmpty()) {
                    addLog("VIRTUAL STORAGE CORE IS EMPTY.", LogType.ALERT)
                } else {
                    slots.forEach { slot ->
                        val eqLabel = if (slot.isEquipped) " [EQUIPPED]" else ""
                        addLog("${slot.item.icon} ${slot.item.name} x${slot.quantity} [${slot.item.rarity.displayName.uppercase()}] (${slot.item.category.displayName})$eqLabel - ${slot.item.description}", LogType.INFO)
                    }
                }
            }
            "equip" -> {
                val itemName = parts.drop(1).joinToString(" ")
                if (itemName.isEmpty()) {
                    addLog("ERROR: Specify equipment name. E.g. 'equip CyberBlade.exe'.", LogType.ERROR)
                } else {
                    equipItem(itemName)
                }
            }
            "unequip" -> {
                val slotArg = parts.getOrNull(1)?.lowercase()
                val slot = when (slotArg) {
                    "weapon", "wpn" -> com.example.data.EquipmentSlot.WEAPON
                    "armor", "arm" -> com.example.data.EquipmentSlot.ARMOR
                    "utility", "util", "cyberware" -> com.example.data.EquipmentSlot.UTILITY
                    else -> null
                }
                if (slot != null) {
                    unequipItemSlot(slot)
                } else {
                    addLog("ERROR: Specify equipment slot to unequip: 'weapon', 'armor', or 'utility'.", LogType.ERROR)
                }
            }
            "scavenge", "search" -> {
                scavengeCurrentCell()
            }
            "drop", "discard" -> {
                val itemName = parts.drop(1).joinToString(" ")
                if (itemName.isEmpty()) {
                    addLog("ERROR: Specify item to discard. E.g. 'drop NanoMed.sys'.", LogType.ERROR)
                } else {
                    discardInventoryItem(itemName)
                }
            }
            "sort" -> {
                val sortArg = parts.getOrNull(1)?.lowercase()
                val option = when (sortArg) {
                    "name" -> com.example.data.InventorySortOption.NAME
                    "category", "cat" -> com.example.data.InventorySortOption.CATEGORY
                    "rarity", "rare" -> com.example.data.InventorySortOption.RARITY
                    "quantity", "qty", "count" -> com.example.data.InventorySortOption.QUANTITY
                    else -> null
                }
                if (option != null) {
                    setInventorySortOption(option)
                } else {
                    addLog("ERROR: Sort option must be 'name', 'category', 'rarity', or 'quantity'.", LogType.ERROR)
                }
            }
            "category" -> {
                val catArg = parts.getOrNull(1)?.lowercase()
                val category = when (catArg) {
                    "consumable", "med", "sys" -> com.example.data.InventoryCategory.CONSUMABLE
                    "equipment", "eq", "wpn", "arm" -> com.example.data.InventoryCategory.EQUIPMENT
                    "program", "prog" -> com.example.data.InventoryCategory.PROGRAM
                    "key", "keyitem" -> com.example.data.InventoryCategory.KEY_ITEM
                    "resource", "salvage" -> com.example.data.InventoryCategory.RESOURCE
                    "all", "clear", "reset" -> null
                    else -> null
                }
                setInventoryCategoryFilter(category)
            }
            "shop", "store", "buy" -> {
                if (state.screen == ActiveScreen.EXPLORATION) {
                    enterShop()
                } else {
                    addLog("ERROR: Shop only accessible during exploration.", LogType.ERROR)
                }
            }
            "exit", "close" -> {
                when (state.screen) {
                    ActiveScreen.UPGRADE_STORE -> exitShop()
                    ActiveScreen.LEADERBOARD -> exitLeaderboard()
                    ActiveScreen.HACKING_MINIGAME -> exitHackingMinigame()
                    else -> addLog("ERROR: Nothing to exit.", LogType.ERROR)
                }
            }
            "save" -> {
                saveGame()
            }
            "load" -> {
                loadGame()
            }
            "scan", "radar", "sonar" -> {
                triggerMapScan()
            }
            "menu" -> {
                returnToStartMenu()
            }
            "hack" -> {
                if (state.screen == ActiveScreen.HACKING_MINIGAME) {
                    val r = parts.getOrNull(1)?.toIntOrNull()
                    val c = parts.getOrNull(2)?.toIntOrNull()
                    if (r != null && c != null) {
                        hackCell(r, c)
                    } else {
                        addLog("HACK: Please specify cell indices. E.g. 'hack 2 3'", LogType.ALERT)
                    }
                } else {
                    addLog("ERROR: Decryption hacking minigame is not active.", LogType.ERROR)
                }
            }
            "clear" -> {
                _uiState.update { it.copy(logFeed = emptyList()) }
                addLog("Log console history cleared.", LogType.INFO)
            }
            else -> {
                addLog("UNKNOWN COMMAND: '$trimmed'. Type 'help' for support.", LogType.ERROR)
            }
        }
    }

    // ----------------------------------------------------
    // PERSISTENCE: Save / Load Game state via SharedPreferences
    // ----------------------------------------------------
    private fun serializeMaze(maze: Array<Array<com.example.data.CellType>>): String {
        return maze.joinToString(";") { row ->
            row.joinToString(",") { it.name }
        }
    }

    private fun deserializeMaze(str: String): Array<Array<com.example.data.CellType>> {
        if (str.isEmpty()) return emptyArray()
        val rows = str.split(";")
        return rows.map { row ->
            row.split(",").map { cellName ->
                try {
                    com.example.data.CellType.valueOf(cellName)
                } catch (e: Exception) {
                    com.example.data.CellType.WALL
                }
            }.toTypedArray()
        }.toTypedArray()
    }

    private fun serializeExploredCells(cells: Set<Pair<Int, Int>>): String {
        return cells.joinToString(";") { "${it.first},${it.second}" }
    }

    private fun deserializeExploredCells(str: String): Set<Pair<Int, Int>> {
        if (str.isEmpty()) return emptySet()
        return str.split(";").mapNotNull {
            val parts = it.split(",")
            if (parts.size == 2) {
                val first = parts[0].toIntOrNull()
                val second = parts[1].toIntOrNull()
                if (first != null && second != null) {
                    Pair(first, second)
                } else null
            } else null
        }.toSet()
    }

    private fun serializeFloors(floors: Map<Int, Array<Array<com.example.data.CellType>>>): String {
        return floors.map { (floor, maze) ->
            "$floor:${serializeMaze(maze)}"
        }.joinToString("|")
    }

    private fun deserializeFloors(str: String): Map<Int, Array<Array<com.example.data.CellType>>> {
        if (str.isEmpty()) return emptyMap()
        val map = mutableMapOf<Int, Array<Array<com.example.data.CellType>>>()
        str.split("|").forEach { entry ->
            val parts = entry.split(":", limit = 2)
            if (parts.size == 2) {
                val floor = parts[0].toIntOrNull()
                if (floor != null) {
                    map[floor] = deserializeMaze(parts[1])
                }
            }
        }
        return map
    }

    private fun serializeExploredMap(explored: Map<Int, Set<Pair<Int, Int>>>): String {
        return explored.map { (floor, cells) ->
            "$floor:${serializeExploredCells(cells)}"
        }.joinToString("|")
    }

    private fun deserializeExploredMap(str: String): Map<Int, Set<Pair<Int, Int>>> {
        if (str.isEmpty()) return emptyMap()
        val map = mutableMapOf<Int, Set<Pair<Int, Int>>>()
        str.split("|").forEach { entry ->
            val parts = entry.split(":", limit = 2)
            if (parts.size == 2) {
                val floor = parts[0].toIntOrNull()
                if (floor != null) {
                    map[floor] = deserializeExploredCells(parts[1])
                }
            }
        }
        return map
    }

    fun hasSavedGame(): Boolean {
        val sharedPrefs = getApplication<Application>().getSharedPreferences("netcrawler_save_prefs", Context.MODE_PRIVATE)
        return sharedPrefs.getBoolean("has_saved_game", false)
    }

    fun saveGame() {
        val state = _uiState.value
        if (state.runnerName.isEmpty()) return

        // 1. Room Database Persistence
        val profileEntity = CharacterProfileEntity(
            profileId = "profile_${state.runnerName.lowercase().replace(" ", "_")}",
            runnerName = state.runnerName,
            runnerClass = state.runnerClass.name,
            level = state.level,
            credits = state.credits,
            totalCreditsEarned = state.totalCreditsEarned,
            maxIntegrity = state.maxIntegrity,
            maxRam = state.maxRam,
            nodesHackedCount = state.nodesHackedCount
        )

        val saveProgressEntity = GameSaveProgressEntity(
            saveSlotId = "current_save",
            runnerName = state.runnerName,
            runnerClass = state.runnerClass.name,
            level = state.level,
            integrity = state.integrity,
            maxIntegrity = state.maxIntegrity,
            playerShield = state.playerShield,
            playerMaxShield = state.playerMaxShield,
            ram = state.ram,
            maxRam = state.maxRam,
            ramRecoveryRate = state.ramRecoveryRate,
            credits = state.credits,
            damageBonus = state.damageBonus,
            defenseBonus = state.defenseBonus,
            characterLevel = state.characterLevel,
            characterXp = state.characterXp,
            xpToNextLevel = state.xpToNextLevel,
            gridX = state.gridX,
            gridY = state.gridY,
            direction = state.direction.name,
            currentZone = state.currentZone.name,
            buildingFloor = state.buildingFloor,
            collectorsLevel = state.collectorsLevel,
            cityDistrictIndex = state.cityDistrictIndex,
            hasElevatorKeycard = state.hasElevatorKeycard,
            activeWeather = state.activeWeather.name,
            weatherTurnsLeft = state.weatherTurnsLeft,
            stepsSinceLastEvent = state.stepsSinceLastEvent,
            nextEventSteps = state.nextEventSteps,
            predictedWeather = state.predictedWeather?.name ?: "",
            nodesHackedCount = state.nodesHackedCount,
            totalCreditsEarned = state.totalCreditsEarned,
            inventoryCsv = state.inventory.joinToString(","),
            installedCyberwareCsv = state.installedCyberware.joinToString(",") { it.id },
            installedProgramsCsv = state.installedPrograms.joinToString(",") { it.id },
            installedImplantsCsv = state.installedImplants.entries.joinToString(",") { "${it.key.name}:${it.value?.id ?: ""}" },
            exploredCellsCsv = serializeExploredCells(state.exploredCells),
            mazeData = serializeMaze(state.maze),
            originalMazeData = state.originalMaze?.let { serializeMaze(it) } ?: "",
            buildingFloorsData = serializeFloors(state.buildingFloors),
            buildingExploredData = serializeExploredMap(state.buildingExplored),
            collectorsLevelsData = serializeFloors(state.collectorsLevels),
            collectorsExploredData = serializeExploredMap(state.collectorsExplored),
            cityDistrictsData = serializeFloors(state.cityDistricts),
            cityExploredData = serializeExploredMap(state.cityExplored),
            gameStateName = state.gameState.name,
            logFeedSerialized = state.logFeed.joinToString("$$") { "${it.text}||${it.type.name}||${it.timestamp}" }
        )

        val inventoryEntities = state.inventory.map { item ->
            InventoryItemEntity(
                saveSlotId = "current_save",
                itemName = item,
                itemType = when {
                    item.endsWith(".pkg") || item.endsWith(".bin") || item.endsWith(".exe") || item.endsWith(".sys") -> "PROGRAM/UTILITY"
                    item.lowercase().contains("keycard") -> "KEYCARD"
                    else -> "CONSUMABLE"
                },
                quantity = 1,
                description = "Netrunner Item Payload: $item"
            )
        }

        viewModelScope.launch {
            repository.saveProfile(profileEntity)
            repository.saveGameProgress(saveProgressEntity, inventoryEntities)
        }

        // 2. SharedPreferences backup
        val sharedPrefs = getApplication<Application>().getSharedPreferences("netcrawler_save_prefs", Context.MODE_PRIVATE)
        sharedPrefs.edit().apply {
            putBoolean("has_saved_game", true)
            putString("runnerName", state.runnerName)
            putString("runnerClass", state.runnerClass.name)
            putInt("maxIntegrity", state.maxIntegrity)
            putInt("integrity", state.integrity)
            putInt("playerMaxShield", state.playerMaxShield)
            putInt("playerShield", state.playerShield)
            putInt("maxRam", state.maxRam)
            putInt("ram", state.ram)
            putInt("ramRecoveryRate", state.ramRecoveryRate)
            putInt("credits", state.credits)
            putInt("damageBonus", state.damageBonus)
            putInt("defenseBonus", state.defenseBonus)
            putInt("characterLevel", state.characterLevel)
            putInt("characterXp", state.characterXp)
            putInt("xpToNextLevel", state.xpToNextLevel)
            putInt("gridX", state.gridX)
            putInt("gridY", state.gridY)
            putString("direction", state.direction.name)
            putInt("level", state.level)
            putString("currentZone", state.currentZone.name)
            putInt("buildingFloor", state.buildingFloor)
            putInt("collectorsLevel", state.collectorsLevel)
            putInt("cityDistrictIndex", state.cityDistrictIndex)
            putBoolean("hasElevatorKeycard", state.hasElevatorKeycard)

            // Collection fields
            putString("inventory", state.inventory.joinToString(","))
            putString("installedCyberware", state.installedCyberware.joinToString(",") { it.id })
            putString("installedPrograms", state.installedPrograms.joinToString(",") { it.id })
            putString("installedImplantsCsv", state.installedImplants.entries.joinToString(",") { "${it.key.name}:${it.value?.id ?: ""}" })
            putString("storedImplantsCsv", state.storedImplants.joinToString(",") { it.id })
            putString("exploredCells", serializeExploredCells(state.exploredCells))
            
            // Weather
            putString("activeWeather", state.activeWeather.name)
            putInt("weatherTurnsLeft", state.weatherTurnsLeft)
            putInt("stepsSinceLastEvent", state.stepsSinceLastEvent)
            putInt("nextEventSteps", state.nextEventSteps)
            putString("predictedWeather", state.predictedWeather?.name ?: "")

            // Stats
            putInt("nodesHackedCount", state.nodesHackedCount)
            putInt("totalCreditsEarned", state.totalCreditsEarned)

            // Map generation state caching
            putString("maze", serializeMaze(state.maze))
            putString("originalMaze", state.originalMaze?.let { serializeMaze(it) } ?: "")
            putString("buildingFloors", serializeFloors(state.buildingFloors))
            putString("buildingExplored", serializeExploredMap(state.buildingExplored))
            putString("collectorsLevels", serializeFloors(state.collectorsLevels))
            putString("collectorsExplored", serializeExploredMap(state.collectorsExplored))
            putString("cityDistricts", serializeFloors(state.cityDistricts))
            putString("cityExplored", serializeExploredMap(state.cityExplored))

            // Game state and logs
            putString("gameState", state.gameState.name)
            putString("logFeed", state.logFeed.joinToString("$$") { "${it.text}||${it.type.name}||${it.timestamp}" })

            apply()
        }
        addLog("💾 COGNITIVE STATE PERSISTED TO ROOM DATABASE & CHIP STORAGE.", LogType.SUCCESS)
    }

    fun loadGame() {
        viewModelScope.launch {
            val roomProgress = repository.getSaveProgressSync("current_save")
            if (roomProgress != null) {
                try {
                    val runnerClass = try {
                        NetrunnerClass.valueOf(roomProgress.runnerClass)
                    } catch (e: Exception) {
                        NetrunnerClass.CODE_SLASHER
                    }

                    val direction = try {
                        Direction.valueOf(roomProgress.direction)
                    } catch (e: Exception) {
                        Direction.EAST
                    }

                    val currentZone = try {
                        com.example.data.Zone.valueOf(roomProgress.currentZone)
                    } catch (e: Exception) {
                        com.example.data.Zone.BUILDING
                    }

                    val activeWeather = try {
                        com.example.data.CyberWeather.valueOf(roomProgress.activeWeather)
                    } catch (e: Exception) {
                        com.example.data.CyberWeather.CLEAR
                    }

                    val predictedWeather = if (roomProgress.predictedWeather.isNotEmpty()) {
                        try {
                            com.example.data.CyberWeather.valueOf(roomProgress.predictedWeather)
                        } catch (e: Exception) {
                            null
                        }
                    } else null

                    val gameState = try {
                        GameState.valueOf(roomProgress.gameStateName)
                    } catch (e: Exception) {
                        GameState.EXPLORATION
                    }

                    // Restore inventories from Room CSV / entities
                    val inventory = if (roomProgress.inventoryCsv.isEmpty()) emptyList() else roomProgress.inventoryCsv.split(",")
                    val installedCyberware = if (roomProgress.installedCyberwareCsv.isEmpty()) emptyList() else roomProgress.installedCyberwareCsv.split(",").map { getCyberwareById(it) }
                    val installedPrograms = if (roomProgress.installedProgramsCsv.isEmpty()) emptyList() else roomProgress.installedProgramsCsv.split(",").map { getProgramById(it) }

                    val installedImplantsMap = mutableMapOf<com.example.data.ImplantBodySlot, com.example.data.CyberwareImplant?>()
                    if (roomProgress.installedImplantsCsv.isNotEmpty()) {
                        roomProgress.installedImplantsCsv.split(",").forEach { entry ->
                            val parts = entry.split(":")
                            if (parts.size == 2) {
                                try {
                                    val slot = com.example.data.ImplantBodySlot.valueOf(parts[0])
                                    val implant = com.example.data.CyberwareImplantRegistry.getImplantById(parts[1])
                                    if (implant != null) {
                                        installedImplantsMap[slot] = implant
                                    }
                                } catch (e: Exception) {}
                            }
                        }
                    }

                    // Logs
                    val logFeed = if (roomProgress.logFeedSerialized.isEmpty()) emptyList() else roomProgress.logFeedSerialized.split("$$").mapNotNull { line ->
                        val parts = line.split("||")
                        if (parts.size == 3) {
                            val text = parts[0]
                            val type = try { LogType.valueOf(parts[1]) } catch(e: Exception) { LogType.INFO }
                            val ts = parts[2].toLongOrNull() ?: System.currentTimeMillis()
                            LogMessage(text, type, ts)
                        } else null
                    }

                    // Maps and Mazes
                    val maze = deserializeMaze(roomProgress.mazeData)
                    val originalMaze = if (roomProgress.originalMazeData.isEmpty()) null else deserializeMaze(roomProgress.originalMazeData)
                    val buildingFloors = deserializeFloors(roomProgress.buildingFloorsData)
                    val buildingExplored = deserializeExploredMap(roomProgress.buildingExploredData)
                    val collectorsLevels = deserializeFloors(roomProgress.collectorsLevelsData)
                    val collectorsExplored = deserializeExploredMap(roomProgress.collectorsExploredData)
                    val cityDistricts = deserializeFloors(roomProgress.cityDistrictsData)
                    val cityExplored = deserializeExploredMap(roomProgress.cityExploredData)
                    val exploredCells = deserializeExploredCells(roomProgress.exploredCellsCsv)

                    _uiState.update {
                        it.copy(
                            screen = ActiveScreen.EXPLORATION,
                            runnerName = roomProgress.runnerName,
                            runnerClass = runnerClass,
                            maxIntegrity = roomProgress.maxIntegrity,
                            integrity = roomProgress.integrity,
                            playerMaxShield = roomProgress.playerMaxShield,
                            playerShield = roomProgress.playerShield,
                            maxRam = roomProgress.maxRam,
                            ram = roomProgress.ram,
                            ramRecoveryRate = roomProgress.ramRecoveryRate,
                            credits = roomProgress.credits,
                            damageBonus = roomProgress.damageBonus,
                            defenseBonus = roomProgress.defenseBonus,
                            characterLevel = roomProgress.characterLevel,
                            characterXp = roomProgress.characterXp,
                            xpToNextLevel = roomProgress.xpToNextLevel,
                            gridX = roomProgress.gridX,
                            gridY = roomProgress.gridY,
                            direction = direction,
                            level = roomProgress.level,
                            currentZone = currentZone,
                            buildingFloor = roomProgress.buildingFloor,
                            collectorsLevel = roomProgress.collectorsLevel,
                            cityDistrictIndex = roomProgress.cityDistrictIndex,
                            hasElevatorKeycard = roomProgress.hasElevatorKeycard,
                            inventory = inventory,
                            installedCyberware = installedCyberware,
                            installedPrograms = installedPrograms,
                            installedImplants = installedImplantsMap,
                            exploredCells = exploredCells,
                            activeWeather = activeWeather,
                            weatherTurnsLeft = roomProgress.weatherTurnsLeft,
                            stepsSinceLastEvent = roomProgress.stepsSinceLastEvent,
                            nextEventSteps = roomProgress.nextEventSteps,
                            predictedWeather = predictedWeather,
                            nodesHackedCount = roomProgress.nodesHackedCount,
                            totalCreditsEarned = roomProgress.totalCreditsEarned,
                            maze = maze,
                            originalMaze = originalMaze,
                            buildingFloors = buildingFloors,
                            buildingExplored = buildingExplored,
                            collectorsLevels = collectorsLevels,
                            collectorsExplored = collectorsExplored,
                            cityDistricts = cityDistricts,
                            cityExplored = cityExplored,
                            gameState = gameState,
                            logFeed = logFeed
                        )
                    }

                    addLog("💾 ROOM DB: RESTORED RUNNER COGNITIVE CHIP FROM LOCAL SQLITE.", LogType.SUCCESS)
                    addLog("RE-LINKED AT GRID COORDINATES (${roomProgress.gridX}, ${roomProgress.gridY}).", LogType.INFO)
                    updatePerspective()
                    return@launch
                } catch (e: Exception) {
                    addLog("⚠️ ROOM RESTORE ALERT: ${e.localizedMessage}, checking secondary storage...", LogType.ALERT)
                }
            }

            // Fallback to SharedPreferences
            loadFromSharedPreferences()
        }
    }

    private fun loadFromSharedPreferences() {
        val sharedPrefs = getApplication<Application>().getSharedPreferences("netcrawler_save_prefs", Context.MODE_PRIVATE)
        if (!sharedPrefs.getBoolean("has_saved_game", false)) {
            addLog("⚠️ ERROR: NO RESTORE POINT FOUND.", LogType.ERROR)
            return
        }

        try {
            val runnerClass = try {
                NetrunnerClass.valueOf(sharedPrefs.getString("runnerClass", "") ?: "CODE_SLASHER")
            } catch (e: Exception) {
                NetrunnerClass.CODE_SLASHER
            }

            val direction = try {
                Direction.valueOf(sharedPrefs.getString("direction", "") ?: "EAST")
            } catch (e: Exception) {
                Direction.EAST
            }

            val currentZone = try {
                com.example.data.Zone.valueOf(sharedPrefs.getString("currentZone", "") ?: "BUILDING")
            } catch (e: Exception) {
                com.example.data.Zone.BUILDING
            }

            val activeWeather = try {
                com.example.data.CyberWeather.valueOf(sharedPrefs.getString("activeWeather", "") ?: "CLEAR")
            } catch (e: Exception) {
                com.example.data.CyberWeather.CLEAR
            }

            val predictedWeatherStr = sharedPrefs.getString("predictedWeather", "") ?: ""
            val predictedWeather = if (predictedWeatherStr.isNotEmpty()) {
                try {
                    com.example.data.CyberWeather.valueOf(predictedWeatherStr)
                } catch (e: Exception) {
                    null
                }
            } else null

            val gameState = try {
                GameState.valueOf(sharedPrefs.getString("gameState", "") ?: "EXPLORATION")
            } catch (e: Exception) {
                GameState.EXPLORATION
            }

            // Restore inventories
            val invStr = sharedPrefs.getString("inventory", "") ?: ""
            val inventory = if (invStr.isEmpty()) emptyList() else invStr.split(",")

            val cyberStr = sharedPrefs.getString("installedCyberware", "") ?: ""
            val installedCyberware = if (cyberStr.isEmpty()) emptyList() else cyberStr.split(",").map { getCyberwareById(it) }

            val progStr = sharedPrefs.getString("installedPrograms", "") ?: ""
            val installedPrograms = if (progStr.isEmpty()) emptyList() else progStr.split(",").map { getProgramById(it) }

            val implantsStr = sharedPrefs.getString("installedImplantsCsv", "") ?: ""
            val installedImplantsMap = mutableMapOf<com.example.data.ImplantBodySlot, com.example.data.CyberwareImplant?>()
            if (implantsStr.isNotEmpty()) {
                implantsStr.split(",").forEach { entry ->
                    val parts = entry.split(":")
                    if (parts.size == 2) {
                        try {
                            val slot = com.example.data.ImplantBodySlot.valueOf(parts[0])
                            val implant = com.example.data.CyberwareImplantRegistry.getImplantById(parts[1])
                            if (implant != null) {
                                installedImplantsMap[slot] = implant
                            }
                        } catch (e: Exception) {}
                    }
                }
            }

            val storedImplantsStr = sharedPrefs.getString("storedImplantsCsv", "") ?: ""
            val storedImplantsList = if (storedImplantsStr.isEmpty()) emptyList() else storedImplantsStr.split(",").mapNotNull { com.example.data.CyberwareImplantRegistry.getImplantById(it) }

            // Logs
            val logStr = sharedPrefs.getString("logFeed", "") ?: ""
            val logFeed = if (logStr.isEmpty()) emptyList() else logStr.split("$$").mapNotNull { line ->
                val parts = line.split("||")
                if (parts.size == 3) {
                    val text = parts[0]
                    val type = try { LogType.valueOf(parts[1]) } catch(e: Exception) { LogType.INFO }
                    val ts = parts[2].toLongOrNull() ?: System.currentTimeMillis()
                    LogMessage(text, type, ts)
                } else null
            }

            // Maze and Maps
            val mazeStr = sharedPrefs.getString("maze", "") ?: ""
            val maze = deserializeMaze(mazeStr)

            val originalMazeStr = sharedPrefs.getString("originalMaze", "") ?: ""
            val originalMaze = if (originalMazeStr.isEmpty()) null else deserializeMaze(originalMazeStr)

            val buildingFloorsStr = sharedPrefs.getString("buildingFloors", "") ?: ""
            val buildingFloors = deserializeFloors(buildingFloorsStr)

            val buildingExploredStr = sharedPrefs.getString("buildingExplored", "") ?: ""
            val buildingExplored = deserializeExploredMap(buildingExploredStr)

            val collectorsLevelsStr = sharedPrefs.getString("collectorsLevels", "") ?: ""
            val collectorsLevels = deserializeFloors(collectorsLevelsStr)

            val collectorsExploredStr = sharedPrefs.getString("collectorsExplored", "") ?: ""
            val collectorsExplored = deserializeExploredMap(collectorsExploredStr)

            val cityDistrictsStr = sharedPrefs.getString("cityDistricts", "") ?: ""
            val cityDistricts = deserializeFloors(cityDistrictsStr)

            val cityExploredStr = sharedPrefs.getString("cityExplored", "") ?: ""
            val cityExplored = deserializeExploredMap(cityExploredStr)

            val exploredCellsStr = sharedPrefs.getString("exploredCells", "") ?: ""
            val exploredCells = deserializeExploredCells(exploredCellsStr)

            _uiState.update {
                it.copy(
                    screen = ActiveScreen.EXPLORATION,
                    runnerName = sharedPrefs.getString("runnerName", "") ?: "",
                    runnerClass = runnerClass,
                    maxIntegrity = sharedPrefs.getInt("maxIntegrity", 100),
                    integrity = sharedPrefs.getInt("integrity", 100),
                    playerMaxShield = sharedPrefs.getInt("playerMaxShield", 50),
                    playerShield = sharedPrefs.getInt("playerShield", 10),
                    maxRam = sharedPrefs.getInt("maxRam", 12),
                    ram = sharedPrefs.getInt("ram", 12),
                    ramRecoveryRate = sharedPrefs.getInt("ramRecoveryRate", 2),
                    credits = sharedPrefs.getInt("credits", 100),
                    damageBonus = sharedPrefs.getInt("damageBonus", 0),
                    defenseBonus = sharedPrefs.getInt("defenseBonus", 0),
                    characterLevel = sharedPrefs.getInt("characterLevel", 1),
                    characterXp = sharedPrefs.getInt("characterXp", 0),
                    xpToNextLevel = sharedPrefs.getInt("xpToNextLevel", 100),
                    gridX = sharedPrefs.getInt("gridX", 1),
                    gridY = sharedPrefs.getInt("gridY", 1),
                    direction = direction,
                    level = sharedPrefs.getInt("level", 1),
                    currentZone = currentZone,
                    buildingFloor = sharedPrefs.getInt("buildingFloor", 1),
                    collectorsLevel = sharedPrefs.getInt("collectorsLevel", 1),
                    cityDistrictIndex = sharedPrefs.getInt("cityDistrictIndex", 0),
                    hasElevatorKeycard = sharedPrefs.getBoolean("hasElevatorKeycard", false),
                    inventory = inventory,
                    installedCyberware = installedCyberware,
                    installedPrograms = installedPrograms,
                    installedImplants = installedImplantsMap,
                    storedImplants = storedImplantsList,
                    exploredCells = exploredCells,
                    activeWeather = activeWeather,
                    weatherTurnsLeft = sharedPrefs.getInt("weatherTurnsLeft", 0),
                    stepsSinceLastEvent = sharedPrefs.getInt("stepsSinceLastEvent", 0),
                    nextEventSteps = sharedPrefs.getInt("nextEventSteps", 30),
                    predictedWeather = predictedWeather,
                    nodesHackedCount = sharedPrefs.getInt("nodesHackedCount", 0),
                    totalCreditsEarned = sharedPrefs.getInt("totalCreditsEarned", 100),
                    maze = maze,
                    originalMaze = originalMaze,
                    buildingFloors = buildingFloors,
                    buildingExplored = buildingExplored,
                    collectorsLevels = collectorsLevels,
                    collectorsExplored = collectorsExplored,
                    cityDistricts = cityDistricts,
                    cityExplored = cityExplored,
                    gameState = gameState,
                    logFeed = logFeed
                )
            }

            addLog("📶 COGNITIVE RESTORE POINT ESTABLISHED (SECONDARY CHIP).", LogType.SUCCESS)
            updatePerspective()

        } catch (e: Exception) {
            addLog("⚠️ RESTORE ERROR: COMPILING CORRUPT SYSTEM CHIP - ${e.localizedMessage}", LogType.ERROR)
        }
    }

    private fun getProgramById(id: String): Program {
        return when(id) {
            "ping" -> Program("ping", "ping.exe", "Scan enemy process. Deals 10 payload damage.", ramCost = 1, damage = 10)
            "firewall" -> Program("firewall", "firewall.sh", "Harden defences. Restore 25 shield points.", ramCost = 2, shield = 25)
            "kill9" -> Program("kill9", "kill-9.bin", "Force shutdown. Deals 35 heavy payload damage.", ramCost = 4, damage = 35)
            "sandbox" -> Program("sandbox", "sandbox.sys", "Isolate threats. Restore 40 Integrity.", ramCost = 3, heal = 40)
            "overflow" -> Program("overflow", "exploit.sh", "Pierces defenses, dealing 25 raw damage.", ramCost = 3, damage = 25, piercesDefense = true)
            "custom_payload" -> Program("custom_payload", "utility.exe", "Unpredictable script. Deals 20 damage, restores 15 Integrity.", ramCost = 2, damage = 20, heal = 15)
            else -> Program("basic_slash", "Slasher.sys", "Deals baseline security breach damage.", 0, damage = 12)
        }
    }

    private fun getCyberwareById(id: String): Cyberware {
        return when(id) {
            "cpu_oc" -> Cyberware("cpu_oc", "CPU Overclocker", "+2 RAM Recovery Rate", 200, recoveryBonus = 2)
            "mem_exp" -> Cyberware("mem_exp", "RAM Rig Extension", "+4 Max RAM Allocation", 250, ramBonus = 4)
            "armor_plt" -> Cyberware("armor_plt", "Sub-Dermal Firewall", "+30 System Integrity", 180, integrityBonus = 30)
            "dmg_mod" -> Cyberware("dmg_mod", "Payload Amplifier", "+5 Attack Damage output", 300, damageBonus = 5)
            "def_mod" -> Cyberware("def_mod", "Defensive Buffer", "+10% Armor Defense", 220, defenseBonus = 2)
            else -> Cyberware("cpu_oc", "CPU Overclocker", "+2 RAM Recovery Rate", 200, recoveryBonus = 2)
        }
    }
}
