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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlin.random.Random

enum class CombatTurn {
    PLAYER,
    ENEMY,
    ANIMATING
}

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
        val selectedCombatStyle: String = "Slash", // "Slash", "Chop", "Thrust"
        val equippedWeaponName: String = "Sparksteel Dagger",
        val weaponSwingProgress: Float = 0f,
        val weaponSwingType: String = "" // "Slash", "Chop", "Thrust"
    )

    enum class ActiveScreen {
        START_MENU,
        CHARACTER_CREATION,
        EXPLORATION,
        COMBAT,
        HACKING_MINIGAME,
        UPGRADE_STORE,
        LEADERBOARD,
        GAME_OVER
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
    // State Modification & Character Creation
    // ----------------------------------------------------

    fun createCharacter(name: String, selectedClass: NetrunnerClass) {
        val cleanName = name.ifBlank { "Runner_${Random.nextInt(1000, 9999)}" }
        val baseProg = GameEngine.getStartingPrograms(selectedClass)
        val initialCredits = if (selectedClass == NetrunnerClass.SCRIPT_KIDDIE) 350 else 100
        val startInv = if (selectedClass == NetrunnerClass.SCRIPT_KIDDIE) {
            listOf("NanoMed.sys", "RAMBoost.exe", "Decryptor.pkg", "AntiShield.bin", "FirewallBuffer.pkg")
        } else {
            listOf("NanoMed.sys", "RAMBoost.exe")
        }

        val weaponName = when (selectedClass) {
            NetrunnerClass.CODE_SLASHER -> "Daedric Cyber-Katana"
            NetrunnerClass.CYBER_SHIELD -> "Aegis Shock-Mace"
            NetrunnerClass.SCRIPT_KIDDIE -> "Glass Cyber-Dagger"
            NetrunnerClass.BUFFER_OVERFLOW -> "Ebony Plasma-Staff"
        }

        _uiState.update { state ->
            state.copy(
                screen = ActiveScreen.EXPLORATION,
                runnerName = cleanName,
                runnerClass = selectedClass,
                maxIntegrity = selectedClass.baseIntegrity,
                integrity = selectedClass.baseIntegrity,
                playerMaxShield = if (selectedClass == NetrunnerClass.CYBER_SHIELD) 75 else 50,
                playerShield = if (selectedClass == NetrunnerClass.CYBER_SHIELD) 25 else 10,
                maxRam = selectedClass.baseRam,
                ram = selectedClass.baseRam,
                credits = initialCredits,
                totalCreditsEarned = initialCredits,
                installedPrograms = baseProg,
                inventory = startInv,
                level = 1,
                gridX = 1,
                gridY = 1,
                direction = Direction.EAST,
                nodesHackedCount = 0,
                equippedWeaponName = weaponName,
                logFeed = emptyList() // clear creation logs for clean game view
            )
        }

        // Persist new character profile to Room Database
        val profileEntity = CharacterProfileEntity(
            profileId = "profile_${cleanName.lowercase().replace(" ", "_")}",
            runnerName = cleanName,
            runnerClass = selectedClass.name,
            level = 1,
            credits = initialCredits,
            totalCreditsEarned = initialCredits,
            maxIntegrity = selectedClass.baseIntegrity,
            maxRam = selectedClass.baseRam,
            nodesHackedCount = 0
        )
        viewModelScope.launch {
            repository.saveProfile(profileEntity)
        }

        addLog("==========================================", LogType.SUCCESS)
        addLog("PROFILE SYNCHRONIZED: $cleanName [${selectedClass.title}]", LogType.SUCCESS)
        addLog("SPECIALIZATION: ${selectedClass.passiveDesc}", LogType.INFO)
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

            val perspective = withContext(Dispatchers.Default) {
                GameEngine.render3DPerspective(finalMaze, finalX, finalY, Direction.EAST)
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
            val perspective = withContext(Dispatchers.Default) {
                GameEngine.render3DPerspective(maze, 1, 1, Direction.EAST)
            }

            _uiState.update { state ->
                state.copy(
                    maze = maze,
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
                        maze = currentMaze
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
                        maze = currentMaze
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
            _uiState.update { it.copy(showCombatBanner = null, isCombatInputEnabled = true, gameState = GameState.PLAYER_TURN) }
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
        _uiState.update { it.copy(selectedCombatStyle = style) }
        addLog("COMBAT STANCE: Switched to $style stance.", LogType.INFO)
    }

    fun combatAttack() {
        if (!_uiState.value.isCombatInputEnabled) return
        val state = _uiState.value
        val enemy = state.activeEnemy ?: return

        // Check if player is stunned
        if (processPlayerTurnStatusEffects()) {
            addLog("⚡ TURN SKIPPED: Player is STUNNED!", LogType.ALERT)
            executeEnemyCombatTurnInline()
            return
        }

        // Start weapon swing animation in UI thread
        viewModelScope.launch {
            _uiState.update { it.copy(weaponSwingProgress = 0.2f, weaponSwingType = state.selectedCombatStyle) }
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
            addLog("> Swinging ${state.equippedWeaponName} (${state.selectedCombatStyle.uppercase()})...", LogType.INFO)

            // Hit chance calculation (Morrowind Dice Roll)
            val baseHitChance = when (state.selectedCombatStyle) {
                "Slash" -> 70
                "Chop" -> 55
                "Thrust" -> 85
                else -> 70
            }
            // Add level bonus & luck/agility-like RAM factor
            val hitBonus = (state.level * 2) + (state.ram * 1)
            val finalHitChance = (baseHitChance + hitBonus).coerceIn(20, 95)
            val roll = Random.nextInt(100)

            if (roll >= finalHitChance) {
                // MISS!
                addLog("⚔️ MISS! Your weapon swung wide. [Rolled: $roll vs Chance: $finalHitChance%]", LogType.ALERT)
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
            val baseDmg = when (state.selectedCombatStyle) {
                "Slash" -> 16
                "Chop" -> 24
                "Thrust" -> 11
                else -> 16
            }
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

            // Balcony Vantage: +25% attack damage bonus
            val standCell = state.maze.getOrNull(state.gridY)?.getOrNull(state.gridX)
            if (standCell == com.example.data.CellType.ELEVATED_BALCONY) {
                rawPlayerDamage = (rawPlayerDamage * 1.25f).toInt()
                addLog("✨ BALCONY VANTAGE ACTIVE: Swing amplified from balcony overlooking!", LogType.SUCCESS)
            }

            // Crit checks
            var isCrit = false
            val critRate = 10 + (state.ram * 2)
            val finalCritRate = if (state.runnerClass == NetrunnerClass.CODE_SLASHER) critRate + 25 else critRate
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
                _uiState.update { it.copy(showCombatBanner = "🏆 VICTORY", isCombatInputEnabled = false) }
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
            executeEnemyCombatTurnInline()
            return
        }

        _uiState.update { stateNow ->
            val shieldHeal = 15 + (stateNow.level * 3)
            val newShield = minOf(stateNow.playerMaxShield, stateNow.playerShield + shieldHeal)
            stateNow.copy(
                playerShield = newShield,
                activeFirewallTimeLeft = 1, // Firewall active for enemy's upcoming turn
                showShieldEffect = true
            )
        }
        applyStatusEffectToPlayer(com.example.data.StatusEffectType.FORTIFIED, turns = 1, source = "Defensive Firewall")
        addLog("🛡️ ACTIVE FIREWALL INITIATED: Damage incoming in the next turn reduced by 75%!", LogType.SUCCESS)

        viewModelScope.launch {
            kotlinx.coroutines.delay(600)
            _uiState.update { it.copy(showShieldEffect = false) }
            // Automatically run enemy turn on player action completion
            executeEnemyCombatTurnInline()
        }
    }

    fun combatHack() {
        if (!_uiState.value.isCombatInputEnabled) return
        val state = _uiState.value
        val enemy = state.activeEnemy ?: return

        if (processPlayerTurnStatusEffects()) {
            addLog("⚡ TURN SKIPPED: Player is STUNNED!", LogType.ALERT)
            executeEnemyCombatTurnInline()
            return
        }

        if (state.ram < 3) {
            addLog("HACK PROTOCOL ABORTED: Needs 3 MB RAM.", LogType.ERROR)
            return
        }

        _uiState.update { it.copy(
            ram = it.ram - 3
        ) }

        viewModelScope.launch {
            val hackDmg = 25 + (state.level * 4) + state.damageBonus
            val enemyRemIntegrity = maxOf(0, enemy.integrity - hackDmg)
            enemy.integrity = enemyRemIntegrity

            _uiState.update { it.copy(combatFlashEnemy = true, enemyDamagePopup = "-$hackDmg HP") }
            addLog("DIRECT SYSTEM EXPLOIT COMPILED: Bypassed firewall entirely!", LogType.SUCCESS)
            addLog("Dealt $hackDmg system-penetrating damage to ${enemy.name}.", LogType.SUCCESS)

            // Inflict Stunned status effect on enemy!
            applyStatusEffectToEnemy(com.example.data.StatusEffectType.STUNNED, turns = 1, source = "Direct Exploit")

            kotlinx.coroutines.delay(400)
            _uiState.update { it.copy(combatFlashEnemy = false, enemyDamagePopup = null) }

            if (enemy.integrity <= 0) {
                _uiState.update { it.copy(showCombatBanner = "🏆 VICTORY", isCombatInputEnabled = false) }
                kotlinx.coroutines.delay(1200)
                handleCombatVictoryInline(enemy)
                _uiState.update { it.copy(showCombatBanner = null, isCombatInputEnabled = true) }
            } else {
                // Automatically run enemy turn on player action completion
                executeEnemyCombatTurnInline()
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
        executeEnemyCombatTurnInline()
    }

    fun executeCombatProgramInline(program: Program) {
        if (!_uiState.value.isCombatInputEnabled) return
        val state = _uiState.value
        val enemy = state.activeEnemy ?: return

        if (processPlayerTurnStatusEffects()) {
            addLog("⚡ TURN SKIPPED: Player is STUNNED!", LogType.ALERT)
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
                _uiState.update { it.copy(showCombatBanner = "🏆 VICTORY", isCombatInputEnabled = false) }
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
            _uiState.update { it.copy(combatTurn = CombatTurn.ENEMY) }
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
                            _uiState.update { it.copy(showCombatBanner = "🏆 VICTORY", isCombatInputEnabled = false) }
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
                delay(600)
                _uiState.update { stateNow ->
                    stateNow.copy(
                        ram = minOf(stateNow.maxRam, stateNow.ram + stateNow.ramRecoveryRate),
                        combatTurn = CombatTurn.PLAYER,
                        isCombatInputEnabled = true,
                        gameState = GameState.PLAYER_TURN
                    )
                }
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

            val defenseModifier = state.defenseBonus
            val finalEnemyDmg = maxOf(1, baseEnemyDmg - defenseModifier)

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
                    playerDamagePopup = "-$finalEnemyDmg HP"
                )
            }

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

            _uiState.update { stateNow ->
                stateNow.copy(
                    ram = minOf(stateNow.maxRam, stateNow.ram + stateNow.ramRecoveryRate),
                    defenseBonus = 0
                )
            }

            if (newPlayerIntegrity <= 0) {
                _uiState.update { it.copy(showCombatBanner = "💀 DEFEAT") }
                kotlinx.coroutines.delay(1200)
                handleGameOver("Destroyed by security process ${enemy.name}")
                _uiState.update { it.copy(showCombatBanner = null) }
                return@launch
            }

            _uiState.update { it.copy(combatTurn = CombatTurn.PLAYER, isCombatInputEnabled = true, gameState = GameState.PLAYER_TURN) }
        }
    }

    private fun handleCombatVictoryInline(enemy: Enemy) {
        soundManager.playLootCollectionSound()
        val state = _uiState.value
        val baseBounty = enemy.bountyCredits

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
                cellAhead == CellType.ELEVATOR) {
                cellToInteractWith = cellAhead
            }
        }

        // 2. If no interactive cell is ahead, check if we are standing on one!
        if (cellToInteractWith == CellType.PATH) {
            val cellCurrent = state.maze[state.gridY][state.gridX]
            if (cellCurrent == CellType.DATA_STORE || cellCurrent == CellType.ENCRYPTED_PORTAL || 
                cellCurrent == CellType.VIRUS_NODE || cellCurrent == CellType.SECRET_CACHE ||
                cellCurrent == CellType.STAIRS_UP || cellCurrent == CellType.STAIRS_DOWN ||
                cellCurrent == CellType.ELEVATOR) {
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
        val state = _uiState.value
        val nodeType = if (state.targetNodeY in state.maze.indices && state.targetNodeX in state.maze[0].indices) {
            state.maze[state.targetNodeY][state.targetNodeX]
        } else {
            CellType.DATA_STORE
        }

        val isSecretCache = nodeType == CellType.SECRET_CACHE
        val baseBounty = if (isSecretCache) 300 + (state.level * 100) else 100 + (state.level * 50)
        val bountyCredits = baseBounty + Random.nextInt(50)

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
                hasElevatorKeycard = stateNow.hasElevatorKeycard || obtainedKeycard
            )
        }

        updatePerspective()
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
    // Inventory and Healing
    // ----------------------------------------------------

    fun useInventoryItem(itemName: String) {
        val state = _uiState.value
        if (!state.inventory.contains(itemName)) return

        val updatedInventory = state.inventory.toMutableList()
        updatedInventory.remove(itemName)

        var logText = ""

        _uiState.update { stateNow ->
            when (itemName) {
                "NanoMed.sys" -> {
                    val healed = minOf(stateNow.maxIntegrity - stateNow.integrity, 40)
                    logText = "COMPILED NanoMed.sys: Restored $healed% Integrity."
                    stateNow.copy(
                        integrity = stateNow.integrity + healed,
                        inventory = updatedInventory
                    )
                }
                "RAMBoost.exe" -> {
                    val boosted = minOf(stateNow.maxRam - stateNow.ram, 6)
                    logText = "COMPILED RAMBoost.exe: Allocated $boosted MB RAM immediately."
                    stateNow.copy(
                        ram = stateNow.ram + boosted,
                        inventory = updatedInventory
                    )
                }
                "Decryptor.pkg" -> {
                    // Gain massive credits or temporary stats
                    val gained = 150
                    logText = "EXTRACTED Decryptor.pkg: Discovered $gained encrypted credits."
                    stateNow.copy(
                        credits = stateNow.credits + gained,
                        totalCreditsEarned = stateNow.totalCreditsEarned + gained,
                        inventory = updatedInventory
                    )
                }
                "ChipsetMod.pkg" -> {
                    logText = "CRACKED ChipsetMod.pkg: Acquired standard overclock (+1 attack power)."
                    stateNow.copy(
                        damageBonus = stateNow.damageBonus + 1,
                        inventory = updatedInventory
                    )
                }
                "AntiShield.bin" -> {
                    logText = "COMPILED AntiShield.bin: Weaponized virus deals damage in future engagements."
                    stateNow.copy(
                        damageBonus = stateNow.damageBonus + 2,
                        inventory = updatedInventory
                    )
                }
                "FirewallBuffer.pkg" -> {
                    logText = "COMPILED FirewallBuffer.pkg: Increased passive shield protection (+2 defense bonus)."
                    stateNow.copy(
                        defenseBonus = stateNow.defenseBonus + 2,
                        inventory = updatedInventory
                    )
                }
                "GibsonForecast.sys" -> {
                    val stepsRemaining = (stateNow.nextEventSteps - stateNow.stepsSinceLastEvent).coerceAtLeast(1)
                    val nextWeather = stateNow.predictedWeather ?: com.example.data.CyberWeather.VALUES.filter { it != com.example.data.CyberWeather.CLEAR }.random()
                    logText = "COMPILED GibsonForecast.sys: Next sub-grid atmospheric event predicted: [${nextWeather.title}] in $stepsRemaining steps."
                    stateNow.copy(
                        predictedWeather = nextWeather,
                        inventory = updatedInventory
                    )
                }
                "CorrosiveAcid.sh" -> {
                    logText = "WEAPONIZED CorrosiveAcid.sh: Target enemy corrodes for 3 turns (10 DPS)."
                    stateNow.copy(inventory = updatedInventory)
                }
                "StunPulse.dll" -> {
                    logText = "DEPLOYED StunPulse.dll: Discharged EMP charge! Target enemy stunned for 2 turns."
                    stateNow.copy(inventory = updatedInventory)
                }
                "OverclockJuice.exe" -> {
                    logText = "INJECTED OverclockJuice.exe: System core overclocked (+50% damage for 3 turns)."
                    stateNow.copy(inventory = updatedInventory)
                }
                "AntiVirus.sys" -> {
                    logText = "EXECUTED AntiVirus.sys: Purged all system debuffs & fortified firewalls (-50% damage taken for 2 turns)."
                    stateNow.copy(
                        playerStatusEffects = stateNow.playerStatusEffects.filter { !it.type.isDebuff },
                        inventory = updatedInventory
                    )
                }
                else -> {
                    logText = "RUNNING Generic cyber utility: No system changes."
                    stateNow.copy(inventory = updatedInventory)
                }
            }
        }

        addLog(logText, LogType.SUCCESS)

        when (itemName) {
            "CorrosiveAcid.sh" -> applyStatusEffectToEnemy(com.example.data.StatusEffectType.POISONED, turns = 3, magnitude = 10, source = "CorrosiveAcid.sh")
            "StunPulse.dll" -> applyStatusEffectToEnemy(com.example.data.StatusEffectType.STUNNED, turns = 2, source = "StunPulse.dll")
            "OverclockJuice.exe" -> applyStatusEffectToPlayer(com.example.data.StatusEffectType.BUFFED, turns = 3, source = "OverclockJuice.exe")
            "AntiVirus.sys" -> applyStatusEffectToPlayer(com.example.data.StatusEffectType.FORTIFIED, turns = 2, source = "AntiVirus.sys")
        }

        // If used during combat, automatically conclude player's action and trigger enemy's turn
        val gs = _uiState.value.gameState
        if (gs == GameState.PLAYER_TURN || gs == GameState.COMBAT_START) {
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

        when (mainCommand) {
            "help", "?" -> {
                addLog("=== CYBER-TERMINAL COMMAND INTERPRETER ===", LogType.SUCCESS)
                addLog("NAVIGATION: 'forward'/'w'/'n', 'backward'/'s', 'left'/'a', 'right'/'d'", LogType.INFO)
                addLog("INTERACTION: 'interact'/'use'/'e' (activate console/portal/cache/elevator)", LogType.INFO)
                addLog("COMBAT ACTIONS: 'attack'/'hit', 'defend'/'block', 'flee'/'run'", LogType.INFO)
                addLog("COMBAT STANCE: 'style slash'/'chop'/'thrust', or 'stance <style>'", LogType.INFO)
                addLog("INVENTORY: 'use <item>' (e.g. 'use NanoMed.sys', 'use RAMBoost.exe')", LogType.INFO)
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
            "status", "stats", "info" -> {
                addLog("--- RUNNER INTEGRITY PROFILE ---", LogType.SUCCESS)
                addLog("NAME: ${state.runnerName.ifEmpty { "UNNAMED" }} | LEVEL: ${state.level}", LogType.INFO)
                addLog("INTEGRITY: ${state.integrity}/${state.maxIntegrity} | RAM: ${state.ram}/${state.maxRam}MB", LogType.INFO)
                addLog("CREDITS: ${state.credits}MB | DAMAGE BONUS: +${state.damageBonus}", LogType.INFO)
                addLog("WEAPON: ${state.equippedWeaponName} | STANCE: ${state.selectedCombatStyle}", LogType.INFO)
                addLog("ZONE: ${state.currentZone} | INVENTORY: ${state.inventory.joinToString(", ")}", LogType.INFO)
            }
            "attack", "hit", "fight", "swing", "slash", "chop", "thrust" -> {
                if (state.screen == ActiveScreen.COMBAT) {
                    val isAction = mainCommand in listOf("slash", "chop", "thrust")
                    if (isAction) {
                        val properStyle = mainCommand.replaceFirstChar { it.uppercase() }
                        setCombatStyle(properStyle)
                    }
                    combatAttack()
                } else {
                    addLog("ERROR: Combat actions are only valid during active hostile combat.", LogType.ERROR)
                }
            }
            "defend", "block", "shield" -> {
                if (state.screen == ActiveScreen.COMBAT) {
                    combatDefend()
                } else {
                    addLog("ERROR: Combat actions are only valid during active hostile combat.", LogType.ERROR)
                }
            }
            "flee", "run", "escape" -> {
                if (state.screen == ActiveScreen.COMBAT) {
                    fleeCombat()
                } else {
                    addLog("ERROR: Combat actions are only valid during active hostile combat.", LogType.ERROR)
                }
            }
            "stance", "style" -> {
                val style = parts.getOrNull(1)?.lowercase()
                if (style in listOf("slash", "chop", "thrust")) {
                    val properStyle = style!!.replaceFirstChar { it.uppercase() }
                    setCombatStyle(properStyle)
                } else {
                    addLog("ERROR: Style must be 'slash', 'chop', or 'thrust'.", LogType.ERROR)
                }
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
            ram = state.ram,
            maxRam = state.maxRam,
            credits = state.credits,
            gridX = state.gridX,
            gridY = state.gridY,
            direction = state.direction.name,
            currentZone = state.currentZone.name,
            buildingFloor = state.buildingFloor,
            collectorsLevel = state.collectorsLevel,
            cityDistrictIndex = state.cityDistrictIndex,
            hasElevatorKeycard = state.hasElevatorKeycard,
            activeWeather = state.activeWeather.name,
            nodesHackedCount = state.nodesHackedCount,
            totalCreditsEarned = state.totalCreditsEarned,
            inventoryCsv = state.inventory.joinToString(","),
            installedCyberwareCsv = state.installedCyberware.joinToString(",") { it.id },
            installedProgramsCsv = state.installedPrograms.joinToString(",") { it.id }
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
                    screen = ActiveScreen.EXPLORATION, // Enter game directly!
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

            addLog("📶 COGNITIVE RESTORE POINT ESTABLISHED.", LogType.SUCCESS)
            addLog("RE-LINKED AT GRID COORDINATES ($exploredCellsStr).", LogType.INFO)
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
