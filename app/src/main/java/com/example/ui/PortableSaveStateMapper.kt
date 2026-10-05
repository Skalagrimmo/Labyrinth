package com.example.ui

import com.example.data.*

/**
 * Boundary mapper between the Android/UI session state and the portable persistence model.
 * Lookup functions are injected so persistence stays compatible with built-in and mod content.
 */
object PortableSaveStateMapper {
    fun toPayload(state: GameViewModel.GameUiState): PortableSavePayload =
        PortableSavePayload(
            runnerName = state.runnerName, runnerClass = state.runnerClass.name, level = state.level,
            integrity = state.integrity, maxIntegrity = state.maxIntegrity, playerShield = state.playerShield,
            playerMaxShield = state.playerMaxShield, ram = state.ram, maxRam = state.maxRam,
            ramRecoveryRate = state.ramRecoveryRate, credits = state.credits, damageBonus = state.damageBonus,
            defenseBonus = state.defenseBonus, characterLevel = state.characterLevel, characterXp = state.characterXp,
            xpToNextLevel = state.xpToNextLevel, gridX = state.gridX, gridY = state.gridY,
            direction = state.direction.name, currentZone = state.currentZone.name, buildingFloor = state.buildingFloor,
            collectorsLevel = state.collectorsLevel, cityDistrictIndex = state.cityDistrictIndex,
            hasElevatorKeycard = state.hasElevatorKeycard, nodesHackedCount = state.nodesHackedCount,
            totalCreditsEarned = state.totalCreditsEarned, dataFragments = state.dataFragments,
            totalDataFragmentsExtracted = state.totalDataFragmentsExtracted, skillPoints = state.skillPoints,
            unlockedSkillsCsv = state.unlockedSkills.joinToString(","), tutorialStep = state.tutorialStep,
            tutorialActive = state.tutorialActive, tutorialSeen = state.tutorialSeen,
            activeWeather = state.activeWeather.name, weatherTurnsLeft = state.weatherTurnsLeft, levelSeed = state.levelSeed,
            inventory = state.inventory, installedProgramIds = state.installedPrograms.map { it.id },
            exploredCellsCsv = SaveDataCodec.serializeExploredCells(state.exploredCells),
            mazeData = SaveDataCodec.serializeMaze(state.maze),
            originalMazeData = state.originalMaze?.let(SaveDataCodec::serializeMaze) ?: "",
            buildingFloorsData = SaveDataCodec.serializeFloors(state.buildingFloors),
            buildingExploredData = SaveDataCodec.serializeExploredMap(state.buildingExplored),
            collectorsLevelsData = SaveDataCodec.serializeFloors(state.collectorsLevels),
            collectorsExploredData = SaveDataCodec.serializeExploredMap(state.collectorsExplored),
            cityDistrictsData = SaveDataCodec.serializeFloors(state.cityDistricts),
            cityExploredData = SaveDataCodec.serializeExploredMap(state.cityExplored),
            installedImplantsCsv = state.installedImplants.entries.joinToString(",") { "${it.key.name}:${it.value?.id ?: ""}" }
        )

    fun restore(
        current: GameViewModel.GameUiState,
        payload: PortableSavePayload,
        programLookup: (String) -> Program,
        implantLookup: (String) -> CyberwareImplant?
    ): GameViewModel.GameUiState {
        val maze = SaveDataCodec.deserializeMaze(payload.mazeData)
        require(maze.isNotEmpty()) { "Invalid maze data" }

        val implants = mutableMapOf<ImplantBodySlot, CyberwareImplant?>()
        if (payload.installedImplantsCsv.isNotEmpty()) {
            payload.installedImplantsCsv.split(",").forEach { entry ->
                val parts = entry.split(":", limit = 2)
                if (parts.size == 2) {
                    val slot = try { ImplantBodySlot.valueOf(parts[0]) } catch (_: Exception) { null }
                    if (slot != null) implants[slot] = if (parts[1].isNotEmpty()) implantLookup(parts[1]) else null
                }
            }
        }

        val logs = mutableListOf<LogMessage>()
        if (payload.logFeedSerialized.isNotEmpty()) {
            payload.logFeedSerialized.split("$$").forEach { entry ->
                val parts = entry.split("||")
                if (parts.size >= 2) {
                    val type = try { LogType.valueOf(parts[1]) } catch (_: Exception) { LogType.INFO }
                    logs += LogMessage(parts[0], type)
                }
            }
        }

        return current.copy(
            screen = ActiveScreen.EXPLORATION,
            runnerName = payload.runnerName,
            runnerClass = enumOr(payload.runnerClass, NetrunnerClass.CODE_SLASHER),
            level = payload.level, integrity = payload.integrity, maxIntegrity = payload.maxIntegrity,
            playerShield = payload.playerShield, playerMaxShield = payload.playerMaxShield,
            ram = payload.ram, maxRam = payload.maxRam, ramRecoveryRate = payload.ramRecoveryRate,
            credits = payload.credits, damageBonus = payload.damageBonus, defenseBonus = payload.defenseBonus,
            characterLevel = payload.characterLevel, characterXp = payload.characterXp, xpToNextLevel = payload.xpToNextLevel,
            gridX = payload.gridX, gridY = payload.gridY, direction = enumOr(payload.direction, Direction.EAST),
            currentZone = enumOr(payload.currentZone, Zone.BUILDING), buildingFloor = payload.buildingFloor,
            collectorsLevel = payload.collectorsLevel, cityDistrictIndex = payload.cityDistrictIndex,
            hasElevatorKeycard = payload.hasElevatorKeycard, nodesHackedCount = payload.nodesHackedCount,
            totalCreditsEarned = payload.totalCreditsEarned, dataFragments = payload.dataFragments,
            totalDataFragmentsExtracted = payload.totalDataFragmentsExtracted, skillPoints = payload.skillPoints,
            unlockedSkills = payload.unlockedSkillsCsv.split(",").filter { it.isNotBlank() }.toSet(),
            tutorialStep = payload.tutorialStep, tutorialActive = payload.tutorialActive, tutorialSeen = payload.tutorialSeen,
            activeWeather = enumOr(payload.activeWeather, CyberWeather.CLEAR), weatherTurnsLeft = payload.weatherTurnsLeft,
            levelSeed = payload.levelSeed, inventory = payload.inventory.toMutableList(),
            installedPrograms = payload.installedProgramIds.map(programLookup), installedImplants = implants,
            exploredCells = SaveDataCodec.deserializeExploredCells(payload.exploredCellsCsv), maze = maze,
            originalMaze = payload.originalMazeData.takeIf { it.isNotEmpty() }?.let(SaveDataCodec::deserializeMaze),
            buildingFloors = SaveDataCodec.deserializeFloors(payload.buildingFloorsData),
            buildingExplored = SaveDataCodec.deserializeExploredMap(payload.buildingExploredData),
            collectorsLevels = SaveDataCodec.deserializeFloors(payload.collectorsLevelsData),
            collectorsExplored = SaveDataCodec.deserializeExploredMap(payload.collectorsExploredData),
            cityDistricts = SaveDataCodec.deserializeFloors(payload.cityDistrictsData),
            cityExplored = SaveDataCodec.deserializeExploredMap(payload.cityExploredData),
            gameState = enumOr(payload.gameStateName, GameState.EXPLORATION), logFeed = logs
        )
    }

    private inline fun <reified T : Enum<T>> enumOr(value: String, fallback: T): T =
        enumValues<T>().firstOrNull { it.name == value } ?: fallback
}
