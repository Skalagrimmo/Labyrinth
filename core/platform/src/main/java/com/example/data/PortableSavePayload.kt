package com.example.data

import org.json.JSONArray
import org.json.JSONObject

data class PortableSavePayload(
    val runnerName: String = "",
    val runnerClass: String = "CODE_SLASHER",
    val level: Int = 1,
    val integrity: Int = 100,
    val maxIntegrity: Int = 100,
    val playerShield: Int = 10,
    val playerMaxShield: Int = 50,
    val ram: Int = 12,
    val maxRam: Int = 12,
    val ramRecoveryRate: Int = 2,
    val credits: Int = 100,
    val damageBonus: Int = 0,
    val defenseBonus: Int = 0,
    val characterLevel: Int = 1,
    val characterXp: Int = 0,
    val xpToNextLevel: Int = 100,
    val gridX: Int = 1,
    val gridY: Int = 1,
    val direction: String = "EAST",
    val currentZone: String = "BUILDING",
    val buildingFloor: Int = 1,
    val collectorsLevel: Int = 1,
    val cityDistrictIndex: Int = 0,
    val hasElevatorKeycard: Boolean = false,
    val nodesHackedCount: Int = 0,
    val totalCreditsEarned: Int = 100,
    val dataFragments: Int = 0,
    val totalDataFragmentsExtracted: Int = 0,
    val skillPoints: Int = 0,
    val unlockedSkillsCsv: String = "",
    val tutorialStep: Int = 0,
    val tutorialActive: Boolean = false,
    val tutorialSeen: Boolean = false,
    val activeWeather: String = "CLEAR",
    val weatherTurnsLeft: Int = 0,
    val levelSeed: Long = 0L,
    val inventory: List<String> = emptyList(),
    val installedProgramIds: List<String> = emptyList(),
    val exploredCellsCsv: String = "",
    val mazeData: String = "",
    val originalMazeData: String = "",
    val buildingFloorsData: String = "",
    val buildingExploredData: String = "",
    val collectorsLevelsData: String = "",
    val collectorsExploredData: String = "",
    val cityDistrictsData: String = "",
    val cityExploredData: String = "",
    val installedImplantsCsv: String = "",
    val gameStateName: String = "EXPLORATION",
    val logFeedSerialized: String = ""
)

object PortableSaveJsonCodec {
    fun encode(payload: PortableSavePayload): String = JSONObject().apply {
        put("version", PortableSaveEnvelope.VERSION)
        put("runnerName", payload.runnerName); put("runnerClass", payload.runnerClass)
        put("level", payload.level); put("integrity", payload.integrity); put("maxIntegrity", payload.maxIntegrity)
        put("playerShield", payload.playerShield); put("playerMaxShield", payload.playerMaxShield)
        put("ram", payload.ram); put("maxRam", payload.maxRam); put("ramRecoveryRate", payload.ramRecoveryRate)
        put("credits", payload.credits); put("damageBonus", payload.damageBonus); put("defenseBonus", payload.defenseBonus)
        put("characterLevel", payload.characterLevel); put("characterXp", payload.characterXp); put("xpToNextLevel", payload.xpToNextLevel)
        put("gridX", payload.gridX); put("gridY", payload.gridY); put("direction", payload.direction); put("currentZone", payload.currentZone)
        put("buildingFloor", payload.buildingFloor); put("collectorsLevel", payload.collectorsLevel); put("cityDistrictIndex", payload.cityDistrictIndex)
        put("hasElevatorKeycard", payload.hasElevatorKeycard); put("nodesHackedCount", payload.nodesHackedCount)
        put("totalCreditsEarned", payload.totalCreditsEarned); put("dataFragments", payload.dataFragments)
        put("totalDataFragmentsExtracted", payload.totalDataFragmentsExtracted); put("skillPoints", payload.skillPoints)
        put("unlockedSkills", payload.unlockedSkillsCsv); put("tutorialStep", payload.tutorialStep)
        put("tutorialActive", payload.tutorialActive); put("tutorialSeen", payload.tutorialSeen)
        put("activeWeather", payload.activeWeather); put("weatherTurnsLeft", payload.weatherTurnsLeft); put("levelSeed", payload.levelSeed)
        put("inventory", JSONArray(payload.inventory)); put("installedPrograms", JSONArray(payload.installedProgramIds))
        put("exploredCellsCsv", payload.exploredCellsCsv); put("mazeData", payload.mazeData); put("originalMazeData", payload.originalMazeData)
        put("buildingFloorsData", payload.buildingFloorsData); put("buildingExploredData", payload.buildingExploredData)
        put("collectorsLevelsData", payload.collectorsLevelsData); put("collectorsExploredData", payload.collectorsExploredData)
        put("cityDistrictsData", payload.cityDistrictsData); put("cityExploredData", payload.cityExploredData)
        put("installedImplantsCsv", payload.installedImplantsCsv)
        if (payload.gameStateName != "EXPLORATION") put("gameStateName", payload.gameStateName)
        if (payload.logFeedSerialized.isNotEmpty()) put("logFeedSerialized", payload.logFeedSerialized)
    }.toString()

    fun decode(json: String): PortableSavePayload {
        val o = JSONObject(json)
        fun strings(key: String): List<String> {
            val a = o.optJSONArray(key) ?: return emptyList()
            return List(a.length()) { a.getString(it) }
        }
        return PortableSavePayload(
            runnerName=o.optString("runnerName",""), runnerClass=o.optString("runnerClass","CODE_SLASHER"),
            level=o.optInt("level",1), integrity=o.optInt("integrity",100), maxIntegrity=o.optInt("maxIntegrity",100),
            playerShield=o.optInt("playerShield",10), playerMaxShield=o.optInt("playerMaxShield",50),
            ram=o.optInt("ram",12), maxRam=o.optInt("maxRam",12), ramRecoveryRate=o.optInt("ramRecoveryRate",2),
            credits=o.optInt("credits",100), damageBonus=o.optInt("damageBonus",0), defenseBonus=o.optInt("defenseBonus",0),
            characterLevel=o.optInt("characterLevel",1), characterXp=o.optInt("characterXp",0), xpToNextLevel=o.optInt("xpToNextLevel",100),
            gridX=o.optInt("gridX",1), gridY=o.optInt("gridY",1), direction=o.optString("direction","EAST"), currentZone=o.optString("currentZone","BUILDING"),
            buildingFloor=o.optInt("buildingFloor",1), collectorsLevel=o.optInt("collectorsLevel",1), cityDistrictIndex=o.optInt("cityDistrictIndex",0),
            hasElevatorKeycard=o.optBoolean("hasElevatorKeycard",false), nodesHackedCount=o.optInt("nodesHackedCount",0),
            totalCreditsEarned=o.optInt("totalCreditsEarned",100), dataFragments=o.optInt("dataFragments",0),
            totalDataFragmentsExtracted=o.optInt("totalDataFragmentsExtracted",0), skillPoints=o.optInt("skillPoints",0),
            unlockedSkillsCsv=o.optString("unlockedSkills",""), tutorialStep=o.optInt("tutorialStep",0),
            tutorialActive=o.optBoolean("tutorialActive",false), tutorialSeen=o.optBoolean("tutorialSeen",false),
            activeWeather=o.optString("activeWeather","CLEAR"), weatherTurnsLeft=o.optInt("weatherTurnsLeft",0), levelSeed=o.optLong("levelSeed",0L),
            inventory=strings("inventory"), installedProgramIds=strings("installedPrograms"),
            exploredCellsCsv=o.optString("exploredCellsCsv",""), mazeData=o.optString("mazeData",""), originalMazeData=o.optString("originalMazeData",""),
            buildingFloorsData=o.optString("buildingFloorsData",""), buildingExploredData=o.optString("buildingExploredData",""),
            collectorsLevelsData=o.optString("collectorsLevelsData",""), collectorsExploredData=o.optString("collectorsExploredData",""),
            cityDistrictsData=o.optString("cityDistrictsData",""), cityExploredData=o.optString("cityExploredData",""),
            installedImplantsCsv=o.optString("installedImplantsCsv",""), gameStateName=o.optString("gameStateName","EXPLORATION"),
            logFeedSerialized=o.optString("logFeedSerialized","")
        )
    }
}
