package com.example.ui

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import com.example.data.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

class PersistenceManager(
    private val _uiState: MutableStateFlow<GameViewModel.GameUiState>,
    private val application: Application,
    private val repository: GameRepository,
    private val scope: CoroutineScope,
    private val onLog: (String, LogType) -> Unit,
    private val onRestoreComplete: () -> Unit
) {

    private val uiState get() = _uiState.value

    private var previousScreenBeforeMenu: ActiveScreen = ActiveScreen.CHARACTER_CREATION

    // ----------------------------------------------------
    // Serialization Helpers
    // ----------------------------------------------------

    private fun serializeMaze(maze: Array<Array<CellType>>): String {
        return maze.joinToString(";") { row ->
            row.joinToString(",") { it.name }
        }
    }

    private fun deserializeMaze(str: String): Array<Array<CellType>> {
        if (str.isEmpty()) return emptyArray()
        val rows = str.split(";")
        return rows.map { row ->
            row.split(",").map { cellName ->
                try {
                    CellType.valueOf(cellName)
                } catch (e: Exception) {
                    CellType.WALL
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

    private fun serializeFloors(floors: Map<Int, Array<Array<CellType>>>): String {
        return floors.map { (floor, maze) ->
            "$floor:${serializeMaze(maze)}"
        }.joinToString("|")
    }

    private fun deserializeFloors(str: String): Map<Int, Array<Array<CellType>>> {
        if (str.isEmpty()) return emptyMap()
        val map = mutableMapOf<Int, Array<Array<CellType>>>()
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

    // ----------------------------------------------------
    // Lookup Helpers
    // ----------------------------------------------------

    private fun getProgramById(id: String): Program {
        return when(id) {
            "ping" -> Program("ping", "ping.exe", "Scan enemy process. Deals 10 payload damage.", ramCost = 1, damage = 10)
            "firewall" -> Program("firewall", "firewall.sh", "Harden defences. Restore 25 shield points.", ramCost = 2, shield = 25)
            "kill9" -> Program("kill9", "kill-9.bin", "Force shutdown. Deals 35 heavy payload damage.", ramCost = 4, damage = 35)
            "sandbox" -> Program("sandbox", "sandbox.sys", "Isolate threats. Restore 40 Integrity.", ramCost = 3, heal = 40)
            "overflow" -> Program("overflow", "exploit.sh", "Pierces defenses, dealing 25 raw damage.", ramCost = 3, damage = 25, piercesDefense = true)
            "custom_payload" -> Program("custom_payload", "utility.exe", "Unpredictable script. Deals 20 damage, restores 15 Integrity.", ramCost = 2, damage = 20, heal = 15)
            "SentinelFirewallBreaker.exe" -> Program("SentinelFirewallBreaker.exe", "SentinelFirewallBreaker.exe", "Boss drop: Bypasses all armor. Deals 50 piercing damage.", ramCost = 5, damage = 50, piercesDefense = true)
            "DaemonSlayer.sys" -> Program("DaemonSlayer.sys", "DaemonSlayer.sys", "Boss drop: 60 damage, restores 20 RAM on use.", ramCost = 4, damage = 60, heal = 20)
            "ColossusBlade.exe" -> Program("ColossusBlade.exe", "ColossusBlade.exe", "Boss drop: 75 damage, stuns target for 2 turns.", ramCost = 6, damage = 75)
            else -> {
                // Fall back to mod-registered programs (ContentRegistry), then base slash.
                ContentRegistry.programSpecs().firstOrNull { it.id == id || it.name == id }
                    ?: Program("basic_slash", "Slasher.sys", "Deals baseline security breach damage.", 0, damage = 12)
            }
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

    // ----------------------------------------------------
    // Game Lifecycle Methods
    // ----------------------------------------------------

    fun handleGameOver(cause: String) {
        _uiState.update { it.copy(screen = ActiveScreen.GAME_OVER, runOutcome = cause) }
        onLog("==========================================", LogType.ERROR)
        onLog("SYSTEM CORE FAILURE! RETRANSMITTING DATA RECOVERY...", LogType.ERROR)
        onLog("CRITICAL COLLAPSE CAUSE: $cause", LogType.ERROR)

        val state = uiState
        val record = RunRecord(
            runnerName = state.runnerName,
            runnerClass = state.runnerClass.title,
            levelReached = state.level,
            nodesHacked = state.nodesHackedCount,
            creditsEarned = state.totalCreditsEarned,
            outcome = "DECEASED"
        )

        scope.launch {
            repository.insert(record)
        }
    }

    fun disconnectRunSuccessfully() {
        val state = uiState
        if (state.integrity <= 0) return

        onLog("VOLUNTARY EXTRACTION: UPLOADING RUN DATA...", LogType.SUCCESS)

        val record = RunRecord(
            runnerName = state.runnerName,
            runnerClass = state.runnerClass.title,
            levelReached = state.level,
            nodesHacked = state.nodesHackedCount,
            creditsEarned = state.totalCreditsEarned,
            outcome = "DISCONNECTED"
        )

        scope.launch {
            repository.insert(record)
        }

        _uiState.update { it.copy(screen = ActiveScreen.GAME_OVER, runOutcome = "Safe Connection Dissolution") }
    }

    fun restartGame() {
        _uiState.update { GameViewModel.GameUiState(screen = ActiveScreen.START_MENU) }
        onLog("REBOOTING TERMINAL CORE V8.91...", LogType.ALERT)
        onLog("SELECT ARCHETYPE PROFILE TO COMPILE.", LogType.INFO)
    }

    fun startNewRun() {
        _uiState.update { GameViewModel.GameUiState(screen = ActiveScreen.CHARACTER_CREATION) }
        onLog("ESTABLISHING SECURE CONNECTION...", LogType.SUCCESS)
        onLog("SELECT NETRUNNER ARCHETYPE PROFILE TO COMPILE.", LogType.INFO)
    }

    fun returnToStartMenu() {
        val currentScreen = uiState.screen
        if (currentScreen != ActiveScreen.START_MENU) {
            previousScreenBeforeMenu = currentScreen
        }
        _uiState.update { it.copy(screen = ActiveScreen.START_MENU) }
    }

    fun resumeGame() {
        _uiState.update { it.copy(screen = previousScreenBeforeMenu) }
        onRestoreComplete()
    }

    fun viewLeaderboard() {
        _uiState.update { it.copy(screen = ActiveScreen.LEADERBOARD) }
        onLog("BROADCASTING MAIN HISTORIC RECORDS DATABASE...", LogType.SUCCESS)
    }

    fun exitLeaderboard() {
        if (uiState.integrity <= 0) {
            _uiState.update { it.copy(screen = ActiveScreen.GAME_OVER) }
        } else if (uiState.runnerName.isEmpty()) {
            _uiState.update { it.copy(screen = ActiveScreen.START_MENU) }
        } else {
            _uiState.update { it.copy(screen = ActiveScreen.EXPLORATION) }
            onRestoreComplete()
        }
    }

    fun clearHighScores() {
        scope.launch {
            repository.clearAll()
            onLog("MAINFRAME LOGS PURGED SUCCESSFULLY.", LogType.ALERT)
        }
    }

    // ----------------------------------------------------
    // Save / Load
    // ----------------------------------------------------

    fun hasSavedGame(): Boolean {
        val sharedPrefs = application.getSharedPreferences("netcrawler_save_prefs", Context.MODE_PRIVATE)
        return sharedPrefs.getBoolean("has_saved_game", false)
    }

    fun saveGame() {
        val state = uiState
        if (state.runnerName.isEmpty()) return

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

        scope.launch {
            repository.saveProfile(profileEntity)
            repository.saveGameProgress(saveProgressEntity, inventoryEntities)
        }

        val sharedPrefs = application.getSharedPreferences("netcrawler_save_prefs", Context.MODE_PRIVATE)
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

            putString("inventory", state.inventory.joinToString(","))
            putString("installedCyberware", state.installedCyberware.joinToString(",") { it.id })
            putString("installedPrograms", state.installedPrograms.joinToString(",") { it.id })
            putString("installedImplantsCsv", state.installedImplants.entries.joinToString(",") { "${it.key.name}:${it.value?.id ?: ""}" })
            putString("storedImplantsCsv", state.storedImplants.joinToString(",") { it.id })
            putString("exploredCells", serializeExploredCells(state.exploredCells))

            putString("activeWeather", state.activeWeather.name)
            putInt("weatherTurnsLeft", state.weatherTurnsLeft)
            putInt("stepsSinceLastEvent", state.stepsSinceLastEvent)
            putInt("nextEventSteps", state.nextEventSteps)
            putString("predictedWeather", state.predictedWeather?.name ?: "")

            putInt("nodesHackedCount", state.nodesHackedCount)
            putInt("totalCreditsEarned", state.totalCreditsEarned)

            putString("maze", serializeMaze(state.maze))
            putString("originalMaze", state.originalMaze?.let { serializeMaze(it) } ?: "")
            putString("buildingFloors", serializeFloors(state.buildingFloors))
            putString("buildingExplored", serializeExploredMap(state.buildingExplored))
            putString("collectorsLevels", serializeFloors(state.collectorsLevels))
            putString("collectorsExplored", serializeExploredMap(state.collectorsExplored))
            putString("cityDistricts", serializeFloors(state.cityDistricts))
            putString("cityExplored", serializeExploredMap(state.cityExplored))

            putString("gameState", state.gameState.name)
            putString("logFeed", state.logFeed.joinToString("$$") { "${it.text}||${it.type.name}||${it.timestamp}" })

            apply()
        }
        onLog("COGNITIVE STATE PERSISTED TO ROOM DATABASE & CHIP STORAGE.", LogType.SUCCESS)
    }

    fun loadGame() {
        scope.launch {
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
                        Zone.valueOf(roomProgress.currentZone)
                    } catch (e: Exception) {
                        Zone.BUILDING
                    }

                    val activeWeather = try {
                        CyberWeather.valueOf(roomProgress.activeWeather)
                    } catch (e: Exception) {
                        CyberWeather.CLEAR
                    }

                    val predictedWeather = if (roomProgress.predictedWeather.isNotEmpty()) {
                        try {
                            CyberWeather.valueOf(roomProgress.predictedWeather)
                        } catch (e: Exception) {
                            null
                        }
                    } else null

                    val gameState = try {
                        GameState.valueOf(roomProgress.gameStateName)
                    } catch (e: Exception) {
                        GameState.EXPLORATION
                    }

                    val inventory = if (roomProgress.inventoryCsv.isEmpty()) emptyList() else roomProgress.inventoryCsv.split(",")
                    val installedCyberware = if (roomProgress.installedCyberwareCsv.isEmpty()) emptyList() else roomProgress.installedCyberwareCsv.split(",").map { getCyberwareById(it) }
                    val installedPrograms = if (roomProgress.installedProgramsCsv.isEmpty()) emptyList() else roomProgress.installedProgramsCsv.split(",").map { getProgramById(it) }

                    val installedImplantsMap = mutableMapOf<ImplantBodySlot, CyberwareImplant?>()
                    if (roomProgress.installedImplantsCsv.isNotEmpty()) {
                        roomProgress.installedImplantsCsv.split(",").forEach { entry ->
                            val parts = entry.split(":")
                            if (parts.size == 2) {
                                try {
                                    val slot = ImplantBodySlot.valueOf(parts[0])
                                    val implant = CyberwareImplantRegistry.getImplantById(parts[1])
                                    if (implant != null) {
                                        installedImplantsMap[slot] = implant
                                    }
                                } catch (e: Exception) {}
                            }
                        }
                    }

                    val logFeed = if (roomProgress.logFeedSerialized.isEmpty()) emptyList() else roomProgress.logFeedSerialized.split("$$").mapNotNull { line ->
                        val parts = line.split("||")
                        if (parts.size == 3) {
                            val text = parts[0]
                            val type = try { LogType.valueOf(parts[1]) } catch(e: Exception) { LogType.INFO }
                            val ts = parts[2].toLongOrNull() ?: System.currentTimeMillis()
                            LogMessage(text, type, ts)
                        } else null
                    }

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

                    onLog("ROOM DB: RESTORED RUNNER COGNITIVE CHIP FROM LOCAL SQLITE.", LogType.SUCCESS)
                    onLog("RE-LINKED AT GRID COORDINATES (${roomProgress.gridX}, ${roomProgress.gridY}).", LogType.INFO)
                    onRestoreComplete()
                    return@launch
                } catch (e: Exception) {
                    onLog("ROOM RESTORE ALERT: ${e.localizedMessage}, checking secondary storage...", LogType.ALERT)
                }
            }

            loadFromSharedPreferences()
        }
    }

    private fun loadFromSharedPreferences() {
        val sharedPrefs = application.getSharedPreferences("netcrawler_save_prefs", Context.MODE_PRIVATE)
        if (!sharedPrefs.getBoolean("has_saved_game", false)) {
            onLog("ERROR: NO RESTORE POINT FOUND.", LogType.ERROR)
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
                Zone.valueOf(sharedPrefs.getString("currentZone", "") ?: "BUILDING")
            } catch (e: Exception) {
                Zone.BUILDING
            }

            val activeWeather = try {
                CyberWeather.valueOf(sharedPrefs.getString("activeWeather", "") ?: "CLEAR")
            } catch (e: Exception) {
                CyberWeather.CLEAR
            }

            val predictedWeatherStr = sharedPrefs.getString("predictedWeather", "") ?: ""
            val predictedWeather = if (predictedWeatherStr.isNotEmpty()) {
                try {
                    CyberWeather.valueOf(predictedWeatherStr)
                } catch (e: Exception) {
                    null
                }
            } else null

            val gameState = try {
                GameState.valueOf(sharedPrefs.getString("gameState", "") ?: "EXPLORATION")
            } catch (e: Exception) {
                GameState.EXPLORATION
            }

            val invStr = sharedPrefs.getString("inventory", "") ?: ""
            val inventory = if (invStr.isEmpty()) emptyList() else invStr.split(",")

            val cyberStr = sharedPrefs.getString("installedCyberware", "") ?: ""
            val installedCyberware = if (cyberStr.isEmpty()) emptyList() else cyberStr.split(",").map { getCyberwareById(it) }

            val progStr = sharedPrefs.getString("installedPrograms", "") ?: ""
            val installedPrograms = if (progStr.isEmpty()) emptyList() else progStr.split(",").map { getProgramById(it) }

            val implantsStr = sharedPrefs.getString("installedImplantsCsv", "") ?: ""
            val installedImplantsMap = mutableMapOf<ImplantBodySlot, CyberwareImplant?>()
            if (implantsStr.isNotEmpty()) {
                implantsStr.split(",").forEach { entry ->
                    val parts = entry.split(":")
                    if (parts.size == 2) {
                        try {
                            val slot = ImplantBodySlot.valueOf(parts[0])
                            val implant = CyberwareImplantRegistry.getImplantById(parts[1])
                            if (implant != null) {
                                installedImplantsMap[slot] = implant
                            }
                        } catch (e: Exception) {}
                    }
                }
            }

            val storedImplantsStr = sharedPrefs.getString("storedImplantsCsv", "") ?: ""
            val storedImplantsList = if (storedImplantsStr.isEmpty()) emptyList() else storedImplantsStr.split(",").mapNotNull { CyberwareImplantRegistry.getImplantById(it) }

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

            onLog("COGNITIVE RESTORE POINT ESTABLISHED (SECONDARY CHIP).", LogType.SUCCESS)
            onRestoreComplete()

        } catch (e: Exception) {
            onLog("RESTORE ERROR: COMPILING CORRUPT SYSTEM CHIP - ${e.localizedMessage}", LogType.ERROR)
        }
    }

    // ----------------------------------------------------
    // Export / Import (Offline-first sharing)
    // ----------------------------------------------------

    fun exportSave(): String {
        val state = uiState
        val json = JSONObject()
        json.put("version", 1)
        json.put("runnerName", state.runnerName)
        json.put("runnerClass", state.runnerClass.name)
        json.put("level", state.level)
        json.put("integrity", state.integrity)
        json.put("maxIntegrity", state.maxIntegrity)
        json.put("playerShield", state.playerShield)
        json.put("playerMaxShield", state.playerMaxShield)
        json.put("ram", state.ram)
        json.put("maxRam", state.maxRam)
        json.put("ramRecoveryRate", state.ramRecoveryRate)
        json.put("credits", state.credits)
        json.put("damageBonus", state.damageBonus)
        json.put("defenseBonus", state.defenseBonus)
        json.put("characterLevel", state.characterLevel)
        json.put("characterXp", state.characterXp)
        json.put("xpToNextLevel", state.xpToNextLevel)
        json.put("gridX", state.gridX)
        json.put("gridY", state.gridY)
        json.put("direction", state.direction.name)
        json.put("currentZone", state.currentZone.name)
        json.put("buildingFloor", state.buildingFloor)
        json.put("collectorsLevel", state.collectorsLevel)
        json.put("cityDistrictIndex", state.cityDistrictIndex)
        json.put("hasElevatorKeycard", state.hasElevatorKeycard)
        json.put("nodesHackedCount", state.nodesHackedCount)
        json.put("totalCreditsEarned", state.totalCreditsEarned)
        json.put("dataFragments", state.dataFragments)
        json.put("totalDataFragmentsExtracted", state.totalDataFragmentsExtracted)
        json.put("activeWeather", state.activeWeather.name)
        json.put("weatherTurnsLeft", state.weatherTurnsLeft)
        json.put("levelSeed", state.levelSeed)
        json.put("inventory", JSONArray(state.inventory))
        json.put("installedPrograms", JSONArray(state.installedPrograms.map { it.id }))
        json.put("exploredCellsCsv", serializeExploredCells(state.exploredCells))
        json.put("mazeData", serializeMaze(state.maze))
        json.put("originalMazeData", state.originalMaze?.let { serializeMaze(it) } ?: "")
        json.put("buildingFloorsData", serializeFloors(state.buildingFloors))
        json.put("buildingExploredData", serializeExploredMap(state.buildingExplored))
        json.put("collectorsLevelsData", serializeFloors(state.collectorsLevels))
        json.put("collectorsExploredData", serializeExploredMap(state.collectorsExplored))
        json.put("cityDistrictsData", serializeFloors(state.cityDistricts))
        json.put("cityExploredData", serializeExploredMap(state.cityExplored))
        json.put("installedImplantsCsv", state.installedImplants.entries.joinToString(",") { "${it.key.name}:${it.value?.id ?: ""}" })

        val encoded = android.util.Base64.encodeToString(json.toString().toByteArray(), android.util.Base64.NO_WRAP)
        return "NETCRAWLER_SAVE_v1:$encoded"
    }

    fun importSave(encoded: String): Boolean {
        try {
            val stripped = encoded.removePrefix("NETCRAWLER_SAVE_v1:")
            val jsonStr = String(android.util.Base64.decode(stripped, android.util.Base64.NO_WRAP))
            val json = JSONObject(jsonStr)

            val maze = deserializeMaze(json.optString("mazeData", ""))
            if (maze.isEmpty()) { onLog("IMPORT FAILED: Invalid maze data.", LogType.ERROR); return false }

            val inventory = mutableListOf<String>()
            val invArr = json.optJSONArray("inventory")
            if (invArr != null) { for (i in 0 until invArr.length()) inventory.add(invArr.getString(i)) }

            val programs = mutableListOf<Program>()
            val progArr = json.optJSONArray("installedPrograms")
            if (progArr != null) { for (i in 0 until progArr.length()) programs.add(getProgramById(progArr.getString(i))) }

            val installedImplants = mutableMapOf<ImplantBodySlot, CyberwareImplant?>()
            val implCsv = json.optString("installedImplantsCsv", "")
            if (implCsv.isNotEmpty()) {
                implCsv.split(",").forEach { entry ->
                    val parts = entry.split(":", limit = 2)
                    if (parts.size == 2) {
                        val slot = try { ImplantBodySlot.valueOf(parts[0]) } catch (_: Exception) { null }
                        val implant = if (parts[1].isNotEmpty()) CyberwareImplantRegistry.STARTER_IMPLANTS.find { it.id == parts[1] } else null
                        if (slot != null) installedImplants[slot] = implant
                    }
                }
            }

            val logFeed = mutableListOf<LogMessage>()
            val ls = json.optString("logFeedSerialized", "")
            if (ls.isNotEmpty()) {
                ls.split("$$").forEach { entry ->
                    val parts = entry.split("||")
                    if (parts.size >= 2) {
                        val type = try { LogType.valueOf(parts[1]) } catch (_: Exception) { LogType.INFO }
                        logFeed.add(LogMessage(parts[0], type))
                    }
                }
            }

            val gameState = try { GameState.valueOf(json.optString("gameStateName", "EXPLORATION")) } catch (_: Exception) { GameState.EXPLORATION }

            _uiState.update {
                it.copy(
                    screen = ActiveScreen.EXPLORATION,
                    runnerName = json.optString("runnerName", ""),
                    runnerClass = try { NetrunnerClass.valueOf(json.optString("runnerClass", "CODE_SLASHER")) } catch (_: Exception) { NetrunnerClass.CODE_SLASHER },
                    level = json.optInt("level", 1),
                    integrity = json.optInt("integrity", 100),
                    maxIntegrity = json.optInt("maxIntegrity", 100),
                    playerShield = json.optInt("playerShield", 10),
                    playerMaxShield = json.optInt("playerMaxShield", 50),
                    ram = json.optInt("ram", 12),
                    maxRam = json.optInt("maxRam", 12),
                    ramRecoveryRate = json.optInt("ramRecoveryRate", 2),
                    credits = json.optInt("credits", 100),
                    damageBonus = json.optInt("damageBonus", 0),
                    defenseBonus = json.optInt("defenseBonus", 0),
                    characterLevel = json.optInt("characterLevel", 1),
                    characterXp = json.optInt("characterXp", 0),
                    xpToNextLevel = json.optInt("xpToNextLevel", 100),
                    gridX = json.optInt("gridX", 1),
                    gridY = json.optInt("gridY", 1),
                    direction = try { Direction.valueOf(json.optString("direction", "EAST")) } catch (_: Exception) { Direction.EAST },
                    currentZone = try { Zone.valueOf(json.optString("currentZone", "BUILDING")) } catch (_: Exception) { Zone.BUILDING },
                    buildingFloor = json.optInt("buildingFloor", 1),
                    collectorsLevel = json.optInt("collectorsLevel", 1),
                    cityDistrictIndex = json.optInt("cityDistrictIndex", 0),
                    hasElevatorKeycard = json.optBoolean("hasElevatorKeycard", false),
                    nodesHackedCount = json.optInt("nodesHackedCount", 0),
                    totalCreditsEarned = json.optInt("totalCreditsEarned", 100),
                    dataFragments = json.optInt("dataFragments", 0),
                    totalDataFragmentsExtracted = json.optInt("totalDataFragmentsExtracted", 0),
                    activeWeather = try { CyberWeather.valueOf(json.optString("activeWeather", "CLEAR")) } catch (_: Exception) { CyberWeather.CLEAR },
                    weatherTurnsLeft = json.optInt("weatherTurnsLeft", 0),
                    levelSeed = json.optLong("levelSeed", 0L),
                    inventory = inventory,
                    installedPrograms = programs,
                    installedImplants = installedImplants,
                    exploredCells = deserializeExploredCells(json.optString("exploredCellsCsv", "")),
                    maze = maze,
                    originalMaze = json.optString("originalMazeData", "").let { s -> if (s.isNotEmpty()) deserializeMaze(s) else null },
                    buildingFloors = deserializeFloors(json.optString("buildingFloorsData", "")),
                    buildingExplored = deserializeExploredMap(json.optString("buildingExploredData", "")),
                    collectorsLevels = deserializeFloors(json.optString("collectorsLevelsData", "")),
                    collectorsExplored = deserializeExploredMap(json.optString("collectorsExploredData", "")),
                    cityDistricts = deserializeFloors(json.optString("cityDistrictsData", "")),
                    cityExplored = deserializeExploredMap(json.optString("cityExploredData", "")),
                    gameState = gameState,
                    logFeed = logFeed
                )
            }

            onLog("SAVE DATA IMPORTED SUCCESSFULLY.", LogType.SUCCESS)
            onRestoreComplete()
            return true

        } catch (e: Exception) {
            onLog("IMPORT FAILED: Corrupt save data - ${e.localizedMessage}", LogType.ERROR)
            return false
        }
    }

    fun copyExportToClipboard() {
        val exported = exportSave()
        val clipboard = application.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Netcrawler Save", exported)
        clipboard.setPrimaryClip(clip)
        onLog("SAVE DATA COPIED TO CLIPBOARD. Share with friends!", LogType.SUCCESS)
    }

    fun importFromClipboard() {
        val clipboard = application.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip
        if (clip == null || clip.itemCount == 0) {
            onLog("CLIPBOARD EMPTY. Copy a save code first.", LogType.ERROR)
            return
        }
        val text = clip.getItemAt(0).text?.toString() ?: ""
        if (!text.startsWith("NETCRAWLER_SAVE_v1:")) {
            onLog("CLIPBOARD DOES NOT CONTAIN A VALID NETCRAWLER SAVE.", LogType.ERROR)
            return
        }
        importSave(text)
    }
}
