package com.example.ui

import com.example.data.LegacySaveStorage

/**
 * Encodes and decodes the original SharedPreferences save contract.
 *
 * The key names, separators and fallback values are intentionally kept compatible
 * with the pre-extraction save format.
 */
class LegacySaveStateCodec(
    private val storage: LegacySaveStorage,
    private val programLookup: (String) -> Program,
    private val cyberwareLookup: (String) -> Cyberware
) {

    fun save(state: GameViewModel.GameUiState) {
        storage.edit {
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
            putInt("skillPoints", state.skillPoints)
            putString("unlockedSkills", state.unlockedSkills.joinToString(","))
            putInt("tutorialStep", state.tutorialStep)
            putBoolean("tutorialActive", state.tutorialActive)
            putBoolean("tutorialSeen", state.tutorialSeen)
            putString("exploredCells", SaveDataCodec.serializeExploredCells(state.exploredCells))
            putString("activeWeather", state.activeWeather.name)
            putInt("weatherTurnsLeft", state.weatherTurnsLeft)
            putInt("stepsSinceLastEvent", state.stepsSinceLastEvent)
            putInt("nextEventSteps", state.nextEventSteps)
            putString("predictedWeather", state.predictedWeather?.name ?: "")
            putInt("nodesHackedCount", state.nodesHackedCount)
            putInt("totalCreditsEarned", state.totalCreditsEarned)
            putString("maze", SaveDataCodec.serializeMaze(state.maze))
            putString("originalMaze", state.originalMaze?.let(SaveDataCodec::serializeMaze) ?: "")
            putString("buildingFloors", SaveDataCodec.serializeFloors(state.buildingFloors))
            putString("buildingExplored", SaveDataCodec.serializeExploredMap(state.buildingExplored))
            putString("collectorsLevels", SaveDataCodec.serializeFloors(state.collectorsLevels))
            putString("collectorsExplored", SaveDataCodec.serializeExploredMap(state.collectorsExplored))
            putString("cityDistricts", SaveDataCodec.serializeFloors(state.cityDistricts))
            putString("cityExplored", SaveDataCodec.serializeExploredMap(state.cityExplored))
            putString("gameState", state.gameState.name)
            putString("logFeed", state.logFeed.joinToString("$$") { "${it.text}||${it.type.name}||${it.timestamp}" })
        }
    }

    fun load(current: GameViewModel.GameUiState): GameViewModel.GameUiState? {
        if (!storage.getBoolean("has_saved_game", false)) return null

        fun string(key: String, default: String = "") = storage.getString(key, default) ?: default
        fun enumValue(value: String, fallback: String): String = value.ifEmpty { fallback }

        val runnerClass = runCatching { NetrunnerClass.valueOf(enumValue(string("runnerClass"), "CODE_SLASHER")) }
            .getOrDefault(NetrunnerClass.CODE_SLASHER)
        val direction = runCatching { Direction.valueOf(enumValue(string("direction"), "EAST")) }
            .getOrDefault(Direction.EAST)
        val currentZone = runCatching { Zone.valueOf(enumValue(string("currentZone"), "BUILDING")) }
            .getOrDefault(Zone.BUILDING)
        val activeWeather = runCatching { CyberWeather.valueOf(enumValue(string("activeWeather"), "CLEAR")) }
            .getOrDefault(CyberWeather.CLEAR)
        val predictedWeather = string("predictedWeather").takeIf { it.isNotEmpty() }?.let {
            runCatching { CyberWeather.valueOf(it) }.getOrNull()
        }
        val gameState = runCatching { GameState.valueOf(enumValue(string("gameState"), "EXPLORATION")) }
            .getOrDefault(GameState.EXPLORATION)

        val inventoryString = string("inventory")
        val inventory = if (inventoryString.isEmpty()) emptyList() else inventoryString.split(",")
        val cyberwareString = string("installedCyberware")
        val installedCyberware = if (cyberwareString.isEmpty()) emptyList() else cyberwareString.split(",").map(cyberwareLookup)
        val programsString = string("installedPrograms")
        val installedPrograms = if (programsString.isEmpty()) emptyList() else programsString.split(",").map(programLookup)

        val installedImplants = mutableMapOf<ImplantBodySlot, CyberwareImplant?>()
        string("installedImplantsCsv").takeIf { it.isNotEmpty() }?.split(",")?.forEach { entry ->
            val parts = entry.split(":")
            if (parts.size == 2) {
                runCatching {
                    val slot = ImplantBodySlot.valueOf(parts[0])
                    val implant = CyberwareImplantRegistry.getImplantById(parts[1])
                    if (implant != null) installedImplants[slot] = implant
                }
            }
        }

        val storedImplantsString = string("storedImplantsCsv")
        val storedImplants = if (storedImplantsString.isEmpty()) {
            emptyList()
        } else {
            storedImplantsString.split(",").mapNotNull { CyberwareImplantRegistry.getImplantById(it) }
        }

        val logString = string("logFeed")
        val logFeed = if (logString.isEmpty()) emptyList() else logString.split("$$").mapNotNull { line ->
            val parts = line.split("||")
            if (parts.size != 3) return@mapNotNull null
            LogMessage(
                text = parts[0],
                type = runCatching { LogType.valueOf(parts[1]) }.getOrDefault(LogType.INFO),
                timestamp = parts[2].toLongOrNull() ?: System.currentTimeMillis()
            )
        }

        return current.copy(
            screen = ActiveScreen.EXPLORATION,
            runnerName = string("runnerName"),
            runnerClass = runnerClass,
            maxIntegrity = storage.getInt("maxIntegrity", 100),
            integrity = storage.getInt("integrity", 100),
            playerMaxShield = storage.getInt("playerMaxShield", 50),
            playerShield = storage.getInt("playerShield", 10),
            maxRam = storage.getInt("maxRam", 12),
            ram = storage.getInt("ram", 12),
            ramRecoveryRate = storage.getInt("ramRecoveryRate", 2),
            credits = storage.getInt("credits", 100),
            damageBonus = storage.getInt("damageBonus", 0),
            defenseBonus = storage.getInt("defenseBonus", 0),
            characterLevel = storage.getInt("characterLevel", 1),
            characterXp = storage.getInt("characterXp", 0),
            xpToNextLevel = storage.getInt("xpToNextLevel", 100),
            gridX = storage.getInt("gridX", 1),
            gridY = storage.getInt("gridY", 1),
            direction = direction,
            level = storage.getInt("level", 1),
            currentZone = currentZone,
            buildingFloor = storage.getInt("buildingFloor", 1),
            collectorsLevel = storage.getInt("collectorsLevel", 1),
            cityDistrictIndex = storage.getInt("cityDistrictIndex", 0),
            hasElevatorKeycard = storage.getBoolean("hasElevatorKeycard", false),
            inventory = inventory,
            installedCyberware = installedCyberware,
            installedPrograms = installedPrograms,
            installedImplants = installedImplants,
            storedImplants = storedImplants,
            exploredCells = SaveDataCodec.deserializeExploredCells(string("exploredCells")),
            activeWeather = activeWeather,
            weatherTurnsLeft = storage.getInt("weatherTurnsLeft", 0),
            stepsSinceLastEvent = storage.getInt("stepsSinceLastEvent", 0),
            nextEventSteps = storage.getInt("nextEventSteps", 30),
            predictedWeather = predictedWeather,
            nodesHackedCount = storage.getInt("nodesHackedCount", 0),
            totalCreditsEarned = storage.getInt("totalCreditsEarned", 100),
            skillPoints = storage.getInt("skillPoints", 0),
            unlockedSkills = string("unlockedSkills").split(",").filter { it.isNotBlank() }.toSet(),
            tutorialStep = storage.getInt("tutorialStep", 0),
            tutorialActive = storage.getBoolean("tutorialActive", false),
            tutorialSeen = storage.getBoolean("tutorialSeen", false),
            maze = SaveDataCodec.deserializeMaze(string("maze")),
            originalMaze = string("originalMaze").takeIf { it.isNotEmpty() }?.let(SaveDataCodec::deserializeMaze),
            buildingFloors = SaveDataCodec.deserializeFloors(string("buildingFloors")),
            buildingExplored = SaveDataCodec.deserializeExploredMap(string("buildingExplored")),
            collectorsLevels = SaveDataCodec.deserializeFloors(string("collectorsLevels")),
            collectorsExplored = SaveDataCodec.deserializeExploredMap(string("collectorsExplored")),
            cityDistricts = SaveDataCodec.deserializeFloors(string("cityDistricts")),
            cityExplored = SaveDataCodec.deserializeExploredMap(string("cityExplored")),
            gameState = gameState,
            logFeed = logFeed
        )
    }
}
