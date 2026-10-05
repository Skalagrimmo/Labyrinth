package com.example.ui

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Base64
import com.example.data.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class PersistenceManager(
    private val _uiState: MutableStateFlow<GameViewModel.GameUiState>,
    private val application: Application,
    private val repository: SaveRepository,
    private val scope: CoroutineScope,
    private val onLog: (String, LogType) -> Unit,
    private val onRestoreComplete: () -> Unit,
    private val legacySaveStorage: LegacySaveStorage = AndroidLegacySaveStorage(application)
) {

    private val legacySaveCodec = LegacySaveStateCodec(
        storage = legacySaveStorage,
        programLookup = ::getProgramById,
        cyberwareLookup = ::getCyberwareById
    )

    private val uiState get() = _uiState.value

    private var previousScreenBeforeMenu: ActiveScreen = ActiveScreen.CHARACTER_CREATION

    // ----------------------------------------------------
    // Serialization Helpers
    // ----------------------------------------------------

    private fun serializeMaze(maze: Array<Array<CellType>>): String = SaveDataCodec.serializeMaze(maze)
    private fun deserializeMaze(str: String): Array<Array<CellType>> = SaveDataCodec.deserializeMaze(str)
    private fun serializeExploredCells(cells: Set<Pair<Int, Int>>): String = SaveDataCodec.serializeExploredCells(cells)
    private fun deserializeExploredCells(str: String): Set<Pair<Int, Int>> = SaveDataCodec.deserializeExploredCells(str)
    private fun serializeFloors(floors: Map<Int, Array<Array<CellType>>>): String = SaveDataCodec.serializeFloors(floors)
    private fun deserializeFloors(str: String): Map<Int, Array<Array<CellType>>> = SaveDataCodec.deserializeFloors(str)
    private fun serializeExploredMap(explored: Map<Int, Set<Pair<Int, Int>>>): String = SaveDataCodec.serializeExploredMap(explored)
    private fun deserializeExploredMap(str: String): Map<Int, Set<Pair<Int, Int>>> = SaveDataCodec.deserializeExploredMap(str)

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

    fun viewSettings() {
        _uiState.update { it.copy(screen = ActiveScreen.SETTINGS) }
        onLog("OPENING SYSTEM CONFIGURATION PANEL...", LogType.SUCCESS)
    }

    fun exitSettings() {
        if (uiState.integrity <= 0) {
            _uiState.update { it.copy(screen = ActiveScreen.GAME_OVER) }
        } else if (uiState.runnerName.isEmpty()) {
            _uiState.update { it.copy(screen = ActiveScreen.START_MENU) }
        } else {
            _uiState.update { it.copy(screen = ActiveScreen.EXPLORATION) }
            onRestoreComplete()
        }
    }
    // ----------------------------------------------------
    // Save / Load
    // ----------------------------------------------------

    fun hasSavedGame(): Boolean =
        legacySaveStorage.getBoolean("has_saved_game", false)

    fun markTutorialSeen() {
        legacySaveStorage.edit {
            putBoolean("tutorial_seen", true)
        }
    }

    fun isTutorialSeen(): Boolean =
        legacySaveStorage.getBoolean("tutorial_seen", false)


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

        legacySaveCodec.save(state)
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

            loadFromLegacyStorage()
        }
    }

    private fun loadFromLegacyStorage() {
        if (!legacySaveStorage.getBoolean("has_saved_game", false)) {
            onLog("ERROR: NO RESTORE POINT FOUND.", LogType.ERROR)
            return
        }

        try {
            val restored = legacySaveCodec.load(uiState)
                ?: return
            _uiState.value = restored
            onLog("COGNITIVE RESTORE POINT ESTABLISHED (SECONDARY CHIP).", LogType.SUCCESS)
            onRestoreComplete()
        } catch (e: Exception) {
            onLog("RESTORE ERROR: COMPILING CORRUPT SYSTEM CHIP - ${e.localizedMessage}", LogType.ERROR)
        }
    }


    // ----------------------------------------------------
    // Export / Import (Offline-first sharing)
    // ----------------------------------------------------

    fun exportSave(): String =
        PortableSaveEnvelope.wrapBase64(
            Base64.encodeToString(
                PortableSaveJsonCodec.encode(PortableSaveStateMapper.toPayload(uiState)).toByteArray(Charsets.UTF_8),
                Base64.NO_WRAP
            )
        )

    fun importSave(encoded: String): Boolean {
        return try {
            val payload = PortableSaveJsonCodec.decode(
                String(
                    Base64.decode(PortableSaveEnvelope.unwrapBase64(encoded), Base64.DEFAULT),
                    Charsets.UTF_8
                )
            )
            val restored = PortableSaveStateMapper.restore(
                current = uiState,
                payload = payload,
                programLookup = ::getProgramById,
                implantLookup = { id -> CyberwareImplantRegistry.STARTER_IMPLANTS.find { it.id == id } }
            )
            _uiState.value = restored
            onLog("SAVE DATA IMPORTED SUCCESSFULLY.", LogType.SUCCESS)
            onRestoreComplete()
            true
        } catch (e: Exception) {
            onLog("IMPORT FAILED: Corrupt save data - ${e.localizedMessage}", LogType.ERROR)
            false
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
        if (!PortableSaveEnvelope.hasSupportedPrefix(text)) {
            onLog("CLIPBOARD DOES NOT CONTAIN A VALID NETCRAWLER SAVE.", LogType.ERROR)
            return
        }
        importSave(text)
    }
}
