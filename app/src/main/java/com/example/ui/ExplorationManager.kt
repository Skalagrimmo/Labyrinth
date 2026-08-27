package com.example.ui

import com.example.audio.CyberSoundEffectsManager
import com.example.data.*
import com.example.data.svdag.SvdagIcePathfinder
import com.example.data.svdag.SvdagScannerService
import com.example.data.svdag.SvdagWorldBuilder
import com.example.data.svdag.VoxelType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

class ExplorationManager(
    private val _uiState: MutableStateFlow<GameViewModel.GameUiState>,
    private val soundManager: CyberSoundEffectsManager,
    private val scope: CoroutineScope,
    private val onLog: (String, LogType) -> Unit,
    private val onTriggerCombat: (Int, Int) -> Unit,
    private val underlyingCellTypes: ConcurrentHashMap<String, CellType>,
    private val onHandleGameOver: (String) -> Unit = {},
    private val onStartHackingPuzzle: (Int, Int, Int) -> Unit = { _, _, _ -> },
    private val onAddExperience: (Int) -> Unit = {}
) {
    private val uiState get() = _uiState.value
    private var aiTickCounter = 0

    // ----------------------------------------------------
    // Movement & Exploration Actions
    // ----------------------------------------------------

    fun moveForward() {
        if (uiState.screen != ActiveScreen.EXPLORATION || uiState.gameState != GameState.EXPLORATION) return

        val state = uiState
        var nextX = state.gridX + state.direction.dx
        var nextY = state.gridY + state.direction.dy

        if (state.activeWeather == CyberWeather.DATA_STORM) {
            if (Random.nextFloat() < 0.40f) {
                val scrambledDirs = Direction.VALUES.filter { it != state.direction }
                val scrambledDir = scrambledDirs.random()
                nextX = state.gridX + scrambledDir.dx
                nextY = state.gridY + scrambledDir.dy
                addLog("DATA STORM STATIC: Scrambled movement vector! Redirected forward path.", LogType.ERROR)
            }
        }

        if (isValidMove(nextX, nextY)) {
            val cell = state.maze[nextY][nextX]
            if (cell == CellType.VIRUS_NODE) {
                if (state.gameState != GameState.EXPLORATION) {
                    addLog("ACCESS DENIED: Cannot overlap active threat host. Use Attack program.", LogType.ERROR)
                } else {
                    onTriggerCombat(nextX, nextY)
                }
            } else {
                _uiState.update { it.copy(gridX = nextX, gridY = nextY) }
                updatePerspective()
                revealCellsAround(nextX, nextY)

                recoverRamOnMove()

                soundManager.playStepSound()
                addLog("MOVED FORWARD into sub-channel ($nextX, $nextY)")
                checkCellTriggers(nextX, nextY, cell)
                processWeatherOnStep()

                update()
            }
        } else {
            addLog("ACCESS DENIED: Physical Firewall Blocked.", LogType.ERROR)
        }
    }

    fun moveBackward() {
        if (uiState.screen != ActiveScreen.EXPLORATION || uiState.gameState != GameState.EXPLORATION) return

        val state = uiState
        var nextX = state.gridX - state.direction.dx
        var nextY = state.gridY - state.direction.dy

        if (state.activeWeather == CyberWeather.DATA_STORM) {
            if (Random.nextFloat() < 0.40f) {
                val scrambledDirs = Direction.VALUES
                val scrambledDir = scrambledDirs.random()
                nextX = state.gridX + scrambledDir.dx
                nextY = state.gridY + scrambledDir.dy
                addLog("DATA STORM STATIC: Scrambled movement vector! Redirected backward path.", LogType.ERROR)
            }
        }

        if (isValidMove(nextX, nextY)) {
            val cell = state.maze[nextY][nextX]
            if (cell == CellType.VIRUS_NODE) {
                if (state.gameState != GameState.EXPLORATION) {
                    addLog("ACCESS DENIED: Cannot overlap active threat host. Use Attack program.", LogType.ERROR)
                } else {
                    onTriggerCombat(nextX, nextY)
                }
            } else {
                _uiState.update { it.copy(gridX = nextX, gridY = nextY) }
                updatePerspective()
                revealCellsAround(nextX, nextY)
                recoverRamOnMove()
                soundManager.playStepSound()
                addLog("MOVED BACKWARD into sub-channel ($nextX, $nextY)")
                checkCellTriggers(nextX, nextY, cell)
                processWeatherOnStep()

                update()
            }
        } else {
            addLog("ACCESS DENIED: Solid Core Boundary.", LogType.ERROR)
        }
    }

    fun turnLeft() {
        if (uiState.screen != ActiveScreen.EXPLORATION || uiState.gameState != GameState.EXPLORATION) return
        _uiState.update { state ->
            val actualDir = if (state.activeWeather == CyberWeather.DATA_STORM && Random.nextFloat() < 0.4f) {
                addLog("DATA STORM STATIC: Rotation circuit scrambled!", LogType.ERROR)
                state.direction.turnRight()
            } else {
                state.direction.turnLeft()
            }
            state.copy(direction = actualDir)
        }
        updatePerspective()
        addLog("ROTATED VECTOR 90 LEFT.")
    }

    fun turnRight() {
        if (uiState.screen != ActiveScreen.EXPLORATION || uiState.gameState != GameState.EXPLORATION) return
        _uiState.update { state ->
            val actualDir = if (state.activeWeather == CyberWeather.DATA_STORM && Random.nextFloat() < 0.4f) {
                addLog("DATA STORM STATIC: Rotation circuit scrambled!", LogType.ERROR)
                state.direction.turnLeft()
            } else {
                state.direction.turnRight()
            }
            state.copy(direction = actualDir)
        }
        updatePerspective()
        addLog("ROTATED VECTOR 90 RIGHT.")
    }

    // ----------------------------------------------------
    // Map Scan
    // ----------------------------------------------------

    fun triggerMapScan() {
        val state = uiState
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
            svdagSummary = SvdagScannerService.performSvdagScan(currentDag, ox, oy, oz, radius = scanRadius)
            svdagRipple = SvdagScannerService.computeRippleState(
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
        addLog("SECTOR SCAN EXECUTED (-$ramCost RAM): Radius $scanRadius sonar pulse active!", LogType.SUCCESS)
        if (svdagSummary != null) {
            addLog("  SVDAG SCANNER: Found ${svdagSummary.interactiveCount} Interactive, ${svdagSummary.secretCount} Secrets, ${svdagSummary.alternativePathCount} Bypass Paths.", LogType.INFO)
        } else {
            addLog("  Revealed ${foundEnemies.size} HOSTILE SIGNALS and ${foundLoot.size} LOOT/CACHES on Map HUD.", LogType.INFO)
        }
    }

    // ----------------------------------------------------
    // RAM & Weather
    // ----------------------------------------------------

    private fun recoverRamOnMove() {
        _uiState.update { state ->
            val gained = if (Random.nextInt(100) < 40) 1 else 0
            val newRam = minOf(state.maxRam, state.ram + gained)
            state.copy(ram = newRam)
        }
    }

    fun processWeatherOnStep() {
        scope.launch(Dispatchers.Default) {
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
                        pendingLogs.add(Pair("RADAR SCAN EXPIRED: Active sonar sweep signal faded.", LogType.INFO))
                    }
                }

                if (weather != CyberWeather.CLEAR) {
                    turnsLeft--
                    if (turnsLeft <= 0) {
                        pendingLogs.add(Pair("WEATHER CLEAR: Environmental distortion dissipated. Bandwidth stabilized.", LogType.SUCCESS))
                        weather = CyberWeather.CLEAR
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
                    val possibleWeathers = CyberWeather.VALUES.filter { it != CyberWeather.CLEAR }
                    val newWeather = predicted ?: possibleWeathers.random()
                    predicted = null
                    weather = newWeather
                    turnsLeft = newWeather.effectDuration

                    pendingLogs.add(Pair("CYBER-GRID WEATHER ALTERATION: ${newWeather.title}!!", LogType.ALERT))
                    pendingLogs.add(Pair("${newWeather.description}", LogType.INFO))

                    when (newWeather) {
                        CyberWeather.FRAGMENTATION -> {
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
                        CyberWeather.ECHOES -> {
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
                        CyberWeather.COLD_SPOT -> {
                            pendingLogs.add(Pair("ALERT: System bus temperature critical low. Overclocking modules frozen.", LogType.ALERT))
                        }
                        CyberWeather.HOT_NODE -> {
                            pendingLogs.add(Pair("ALERT: High-voltage core packets discharging. Overclock active, but taking damage!", LogType.ALERT))
                        }
                        CyberWeather.DATA_STORM -> {
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
                        CyberWeather.HOT_NODE -> {
                            val damage = 2
                            integrity = (integrity - damage).coerceAtLeast(1)
                            pendingLogs.add(Pair("HOT NODE OVERHEAT: Core took $damage thermal damage.", LogType.ERROR))
                            if (Random.nextFloat() < 0.4f) {
                                ram = (ram + 1).coerceAtMost(maxRam)
                                pendingLogs.add(Pair("HOT NODE OVERCLOCK: Recovered 1 MB RAM.", LogType.SUCCESS))
                            }
                        }
                        CyberWeather.COLD_SPOT -> {
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

    // ----------------------------------------------------
    // Cell Triggers
    // ----------------------------------------------------

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
                    val healed = minOf(20, uiState.maxIntegrity - uiState.integrity)
                    val ramRestored = minOf(4, uiState.maxRam - uiState.ram)
                    if (healed > 0 || ramRestored > 0) {
                        val updatedMaze = uiState.maze.map { it.clone() }.toTypedArray()
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
                val updatedMaze = uiState.maze.map { it.clone() }.toTypedArray()
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
                    onTriggerCombat(x, y)
                }
            }
            else -> {}
        }
    }

    // ----------------------------------------------------
    // Update Tick
    // ----------------------------------------------------

    fun update() {
        val state = uiState
        if (state.screen != ActiveScreen.EXPLORATION) return
        if (state.gameState != GameState.EXPLORATION) return

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

        if (foundEnemy != null) {
            onTriggerCombat(foundEnemy.first, foundEnemy.second)
            return
        }

        aiTickCounter++
        if (aiTickCounter >= 15) {
            aiTickCounter = 0
            runEnemyAITick(playerX, playerY, maze, state)
        }
    }

    // ----------------------------------------------------
    // Enemy AI
    // ----------------------------------------------------

    private fun runEnemyAITick(
        playerX: Int,
        playerY: Int,
        maze: Array<Array<CellType>>,
        state: GameViewModel.GameUiState
    ) {
        val currentFloorKey = when (state.currentZone) {
            Zone.BUILDING -> "BUILDING_${state.buildingFloor}"
            Zone.COLLECTORS -> "COLLECTORS_${state.collectorsLevel}"
            Zone.CITY -> "CITY_${state.cityDistrictIndex}"
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

            if (currentUnderlyingType == CellType.ELEVATED_BALCONY && dist <= 3) {
                val shieldDamage = minOf(state.playerShield, Random.nextInt(2, 5))
                val integrityDamage = if (shieldDamage < 4) (Random.nextInt(2, 5) - shieldDamage).coerceAtLeast(0) else 0
                val totalDmg = shieldDamage + integrityDamage
                if (totalDmg > 0) {
                    _uiState.update { s ->
                        s.copy(
                            playerShield = maxOf(0, s.playerShield - shieldDamage),
                            integrity = maxOf(0, s.integrity - integrityDamage)
                        )
                    }
                    addLog("GALLERY SNIPER: Hostile process at ($ex, $ey) sniped you from the elevated gallery! Dealt $totalDmg static damage.", LogType.ALERT)
                    if (uiState.integrity <= 0) {
                        onHandleGameOver("Destroyed by remote gallery sniper")
                        return
                    }
                }
                continue
            }

            if (state.currentZone == Zone.BUILDING &&
                (currentUnderlyingType == CellType.ELEVATOR ||
                 currentUnderlyingType == CellType.STAIRS_UP ||
                 currentUnderlyingType == CellType.STAIRS_DOWN) &&
                Random.nextFloat() < 0.20f) {

                val destFloor = if (currentUnderlyingType == CellType.STAIRS_UP && state.buildingFloor < 4) {
                    state.buildingFloor + 1
                } else if (currentUnderlyingType == CellType.STAIRS_DOWN && state.buildingFloor > 1) {
                    state.buildingFloor - 1
                } else if (currentUnderlyingType == CellType.ELEVATOR) {
                    val otherFloors = (1..4).filter { it != state.buildingFloor }
                    otherFloors[Random.nextInt(otherFloors.size)]
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

                            addLog("SECTOR WARNING: Hostile process migrated through vertical shafts to FLOOR $destFloor!", LogType.ALERT)
                            mazeModified = true
                            continue
                        }
                    }
                }
            }

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
                    neighbors[Random.nextInt(neighbors.size)]
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
                        addLog("GRAVITY DASH: Security process at ($currentEx, $currentEy) charged down the gravity ramp!", LogType.ALERT)
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

    // ----------------------------------------------------
    // Special Node Interactions
    // ----------------------------------------------------

    fun interact() {
        if (uiState.screen != ActiveScreen.EXPLORATION || uiState.gameState != GameState.EXPLORATION) return

        val state = uiState
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
                addLog("PHASE MATRIX OVERRIDE: Discovered hidden illusory wall passage!", LogType.SUCCESS)
                val updatedMaze = state.maze.map { it.clone() }.toTypedArray()
                updatedMaze[interactY][interactX] = CellType.PATH
                _uiState.update { it.copy(maze = updatedMaze) }
                soundManager.playNodeBreachSound()
                onAddExperience(50)
                updatePerspective()
            }
            CellType.HACKABLE_TERMINAL -> {
                addLog("INITIATING OVERRIDE PROTOCOL ON SECURITY GATE TERMINAL...", LogType.ALERT)
                onStartHackingPuzzle(interactX, interactY, state.level + 1)
            }
            CellType.TERMINAL_DOOR -> {
                addLog("SECURITY GATE LOCKED: Hack adjacent terminal node to unlock gate bypass.", LogType.ERROR)
            }
            CellType.SCAN_CACHE -> {
                addLog("QUANTUM STEALTH CACHE ACCESSED!", LogType.SUCCESS)
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
                onAddExperience(75)
                updatePerspective()
            }
            CellType.ALTERNATIVE_VENT -> {
                addLog("ENTERED SUB-CONDUIT BYPASS VENT: Sliding through service duct...", LogType.INFO)
                soundManager.playStepSound()
            }
            CellType.DATA_STORE -> {
                addLog("INITIATING HANDSHAKE WITH DATA STORE CORE...", LogType.INFO)
                onStartHackingPuzzle(interactX, interactY, state.level)
            }
            CellType.SECRET_CACHE -> {
                addLog("INITIATING HANDSHAKE WITH CLASSIFIED CRYPT-CACHE...", LogType.SUCCESS)
                onStartHackingPuzzle(interactX, interactY, state.level + 1)
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
                    Zone.BUILDING -> {
                        addLog("⚠️ WARNING: HIGH-THREAT HOSTILE DETECTED AT SECTOR GATE!", LogType.ERROR)
                        addLog("FIREWALL SENTINEL: Ancient defensive sub-routine activated!", LogType.ERROR)
                        addLog("Defeat the guardian to proceed to the Collector sub-tunnels.", LogType.ALERT)
                        val boss = GameEngine.spawnBoss(BossType.FIREWALL_SENTINEL, stateNow.level)
                        _uiState.update { it.copy(activeEnemy = boss, gameState = GameState.COMBAT_START, combatTurn = CombatTurn.PLAYER, combatRound = 1, turnPhase = TurnPhase.PLAYER_INPUT, isCombatInputEnabled = true, playerActionHistory = emptyList(), enemyTurnHistory = emptyList(), allTurnActions = emptyList(), lastPlayerActionRecord = null, lastEnemyActionRecord = null, totalPlayerActionsCount = 0, totalEnemyTurnsCount = 0, playerStatusEffects = emptyList(), enemyStatusEffects = boss.statusEffects.toList(), kineticShieldActiveThisCombat = true, activeCombatHack = null) }
                    }
                    Zone.COLLECTORS -> {
                        addLog("⚠️ WARNING: SUPREME DAEMON DETECTED AT EXTRACTION POINT!", LogType.ERROR)
                        addLog("DAEMON OVERLORD: Ruler of the collector sub-grid has awakened!", LogType.ERROR)
                        addLog("Defeat the overlord to proceed to the Metro Core.", LogType.ALERT)
                        val boss = GameEngine.spawnBoss(BossType.DAEMON_OVERLORD, stateNow.level)
                        _uiState.update { it.copy(activeEnemy = boss, gameState = GameState.COMBAT_START, combatTurn = CombatTurn.PLAYER, combatRound = 1, turnPhase = TurnPhase.PLAYER_INPUT, isCombatInputEnabled = true, playerActionHistory = emptyList(), enemyTurnHistory = emptyList(), allTurnActions = emptyList(), lastPlayerActionRecord = null, lastEnemyActionRecord = null, totalPlayerActionsCount = 0, totalEnemyTurnsCount = 0, playerStatusEffects = emptyList(), enemyStatusEffects = boss.statusEffects.toList(), kineticShieldActiveThisCombat = true, activeCombatHack = null) }
                    }
                    Zone.CITY -> {
                        addLog("⚠️ CRITICAL: APEX SECURITY CONSTRUCT AWAKENED!", LogType.ERROR)
                        addLog("BLACK ICE COLOSSUS: The ultimate defense system of the Metro Core!", LogType.ERROR)
                        addLog("Defeat the colossus to complete the netrun.", LogType.ALERT)
                        val boss = GameEngine.spawnBoss(BossType.BLACK_ICE_COLOSSUS, stateNow.level)
                        _uiState.update { it.copy(activeEnemy = boss, gameState = GameState.COMBAT_START, combatTurn = CombatTurn.PLAYER, combatRound = 1, turnPhase = TurnPhase.PLAYER_INPUT, isCombatInputEnabled = true, playerActionHistory = emptyList(), enemyTurnHistory = emptyList(), allTurnActions = emptyList(), lastPlayerActionRecord = null, lastEnemyActionRecord = null, totalPlayerActionsCount = 0, totalEnemyTurnsCount = 0, playerStatusEffects = emptyList(), enemyStatusEffects = boss.statusEffects.toList(), kineticShieldActiveThisCombat = true, activeCombatHack = null) }
                    }
                }
            }
            CellType.VIRUS_NODE -> {
                addLog("FORCE-CONNECTING WITH ACTIVE THREAT...", LogType.ALERT)
                onTriggerCombat(interactX, interactY)
            }
            else -> {
                addLog("NO RESPONSE AT ADDR: ($interactX, $interactY). IS PATH EMPTY?", LogType.ERROR)
            }
        }
    }

    // ----------------------------------------------------
    // Core Helpers
    // ----------------------------------------------------

    private fun isValidMove(x: Int, y: Int): Boolean {
        val maze = uiState.maze
        if (y !in maze.indices || x !in maze[0].indices) return false
        return maze[y][x] != CellType.WALL
    }

    private fun updatePerspective() {
        val state = uiState
        val perspective = GameEngine.render3DPerspective(state.maze, state.gridX, state.gridY, state.direction, state.activeWeather)
        _uiState.update { it.copy(perspectiveText = perspective) }
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

    private fun addLog(message: String, type: LogType = LogType.INFO) {
        onLog(message, type)
    }

    // ----------------------------------------------------
    // Level / Floor Navigation
    // ----------------------------------------------------

    fun loadOrCreateLevel(
        targetZone: Zone,
        targetFloorOrLevel: Int,
        targetX: Int? = null,
        targetY: Int? = null,
        isAscending: Boolean = true,
        byElevator: Boolean = false
    ) {
        scope.launch {
            _uiState.update { it.copy(fadeAlpha = 1f) }
            delay(400)

            val state = _uiState.value

            val updatedBuildingFloors = state.buildingFloors.toMutableMap()
            val updatedBuildingExplored = state.buildingExplored.toMutableMap()
            val updatedCollectorsLevels = state.collectorsLevels.toMutableMap()
            val updatedCollectorsExplored = state.collectorsExplored.toMutableMap()
            val updatedCityDistricts = state.cityDistricts.toMutableMap()
            val updatedCityExplored = state.cityExplored.toMutableMap()

            when (state.currentZone) {
                Zone.BUILDING -> {
                    if (state.maze.isNotEmpty()) {
                        updatedBuildingFloors[state.buildingFloor] = state.maze
                        updatedBuildingExplored[state.buildingFloor] = state.exploredCells
                    }
                }
                Zone.COLLECTORS -> {
                    if (state.maze.isNotEmpty()) {
                        updatedCollectorsLevels[state.collectorsLevel] = state.maze
                        updatedCollectorsExplored[state.collectorsLevel] = state.exploredCells
                    }
                }
                Zone.CITY -> {
                    if (state.maze.isNotEmpty()) {
                        updatedCityDistricts[state.cityDistrictIndex] = state.maze
                        updatedCityExplored[state.cityDistrictIndex] = state.exploredCells
                    }
                }
            }

            var targetMaze: Array<Array<CellType>>? = null
            var targetExplored = emptySet<Pair<Int, Int>>()

            when (targetZone) {
                Zone.BUILDING -> {
                    targetMaze = updatedBuildingFloors[targetFloorOrLevel]
                    targetExplored = updatedBuildingExplored[targetFloorOrLevel] ?: emptySet()
                    if (targetMaze == null || targetMaze.isEmpty()) {
                        targetMaze = GameEngine.generateBuildingFloor(targetFloorOrLevel)
                    }
                }
                Zone.COLLECTORS -> {
                    targetMaze = updatedCollectorsLevels[targetFloorOrLevel]
                    targetExplored = updatedCollectorsExplored[targetFloorOrLevel] ?: emptySet()
                    if (targetMaze == null || targetMaze.isEmpty()) {
                        targetMaze = GameEngine.generateCollectorTunnels(targetFloorOrLevel)
                    }
                }
                Zone.CITY -> {
                    targetMaze = updatedCityDistricts[targetFloorOrLevel]
                    targetExplored = updatedCityExplored[targetFloorOrLevel] ?: emptySet()
                    if (targetMaze == null || targetMaze.isEmpty()) {
                        targetMaze = GameEngine.generateCitySector(targetFloorOrLevel)
                    }
                }
            }

            val finalMaze = targetMaze!!

            var finalX = 1
            var finalY = 1

            if (targetX != null && targetY != null) {
                finalX = targetX
                finalY = targetY
            } else if (byElevator) {
                val height = finalMaze.size
                val width = finalMaze[0].size
                val cx = width / 2
                val cy = height / 2
                if (finalMaze[cy][cx] == CellType.ELEVATOR) {
                    finalX = cx
                    finalY = cy
                } else {
                    finalX = 1
                    finalY = 1
                }
            } else {
                val searchType = if (isAscending) {
                    CellType.STAIRS_DOWN
                } else {
                    CellType.STAIRS_UP
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
                val svdag = SvdagWorldBuilder.buildSvdagFrom2DLevel(finalMaze, heightLevels = 16, targetDepth = 6)
                Pair(p, svdag)
            }

            _uiState.update { s ->
                s.copy(
                    currentZone = targetZone,
                    buildingFloor = if (targetZone == Zone.BUILDING) targetFloorOrLevel else s.buildingFloor,
                    collectorsLevel = if (targetZone == Zone.COLLECTORS) targetFloorOrLevel else s.collectorsLevel,
                    cityDistrictIndex = if (targetZone == Zone.CITY) targetFloorOrLevel else s.cityDistrictIndex,
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
                        Zone.BUILDING -> targetFloorOrLevel
                        Zone.COLLECTORS -> 4 + targetFloorOrLevel
                        Zone.CITY -> 6 + targetFloorOrLevel
                    }
                )
            }

            revealCellsAround(finalX, finalY)

            delay(100)
            _uiState.update { it.copy(fadeAlpha = 0f) }

            addLog("TRANSITIONED TO: ${targetZone.displayName}, " + when (targetZone) {
                Zone.BUILDING -> {
                    val theme = when (targetFloorOrLevel) {
                        1 -> "Residential"
                        2 -> "Office"
                        3 -> "Technical"
                        4 -> "Storage"
                        else -> "Unknown"
                    }
                    "Floor $targetFloorOrLevel: $theme"
                }
                Zone.COLLECTORS -> "Level $targetFloorOrLevel"
                Zone.CITY -> "Sector $targetFloorOrLevel"
            }, LogType.SUCCESS)
        }
    }

    fun ascendStairs() {
        val state = uiState
        when (state.currentZone) {
            Zone.BUILDING -> {
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
                    loadOrCreateLevel(Zone.BUILDING, targetFloor, isAscending = true)
                } else {
                    addLog("ROOF ARCHITECTURE SEALED. NO FURTHER ASCENSION POSSIBLE.", LogType.ERROR)
                }
            }
            Zone.COLLECTORS -> {
                if (state.collectorsLevel < 2) {
                    addLog("CLIMBING STEEP LADDER TUNNEL TO LEVEL ${state.collectorsLevel + 1}...", LogType.INFO)
                    loadOrCreateLevel(Zone.COLLECTORS, state.collectorsLevel + 1, isAscending = true)
                } else {
                    addLog("TUNNEL CEILING SEALED. PORTAL IS THE ONLY EXIT HERE.", LogType.ERROR)
                }
            }
            Zone.CITY -> {
                addLog("SKY-RISERS CAN ONLY BE ACCESSED VIA LOCAL PORTALS.", LogType.ERROR)
            }
        }
    }

    fun descendStairs() {
        val state = uiState
        when (state.currentZone) {
            Zone.BUILDING -> {
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
                    loadOrCreateLevel(Zone.BUILDING, targetFloor, isAscending = false)
                } else {
                    addLog("BASEMENT CONCRETE FLOOR SEALED. CANNOT DESCEND FURTHER.", LogType.ERROR)
                }
            }
            Zone.COLLECTORS -> {
                if (state.collectorsLevel > 1) {
                    addLog("CLIMBING DOWN TO LOWER DRAINAGE SECTOR ${state.collectorsLevel - 1}...", LogType.INFO)
                    loadOrCreateLevel(Zone.COLLECTORS, state.collectorsLevel - 1, isAscending = false)
                } else {
                    addLog("BOTTOM SEDIMENT LEVEL REACHED. NO FURTHER DESCENT.", LogType.ERROR)
                }
            }
            Zone.CITY -> {
                addLog("UNDERGROUND TUNNELS CANNOT BE ACCESSED DIRECTLY FROM THIS DISTRICT PLAZA.", LogType.ERROR)
            }
        }
    }

    fun interactWithElevator() {
        val state = uiState
        if (state.currentZone != Zone.BUILDING) {
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
        loadOrCreateLevel(Zone.BUILDING, nextFloor, byElevator = true)
    }

    private fun generateNewLevel() {
        scope.launch {
            val level = _uiState.value.level
            val seed = System.currentTimeMillis()
            val size = minOf(35 + ((level - 1) * 4), 55)
            val maze = withContext(Dispatchers.Default) {
                GameEngine.generateMaze(size, size, level)
            }
            val multiFloorLevel = withContext(Dispatchers.Default) {
                ProceduralMultiFloorLevelGenerator.generateMultiFloorLevel(
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
                    exploredCells = emptySet(),
                    levelSeed = seed
                )
            }
            revealCellsAround(1, 1)
        }
    }

    fun generateProceduralMultiFloorLevel(numFloors: Int = 4, width: Int = 14, height: Int = 14) {
        scope.launch {
            val levelNum = _uiState.value.level
            val multiFloor = withContext(Dispatchers.Default) {
                ProceduralMultiFloorLevelGenerator.generateMultiFloorLevel(
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
        val state = uiState
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

    fun navigateVerticalConnector(connector: VerticalConnector) {
        val state = uiState
        val currentFloorIndex = state.activeFloorIndex
        val level = state.currentMultiFloorLevel ?: return

        val sourceFloor = level.floors.getOrNull(currentFloorIndex) ?: return
        val targetGridFloor = level.floors.getOrNull(connector.targetFloorIndex) ?: return

        _uiState.update {
            it.copy(
                activeFloorIndex = connector.targetFloorIndex,
                maze = targetGridFloor.grid,
                gridX = connector.targetSpawnX.coerceIn(0, targetGridFloor.grid[0].size - 1),
                gridY = connector.targetSpawnY.coerceIn(0, targetGridFloor.grid.size - 1)
            )
        }
        updatePerspective()
        revealCellsAround(connector.targetSpawnX, connector.targetSpawnY)
        addLog("TRANSIT CONNECTED: ${connector.name} used. Transferred to ${targetGridFloor.floorName}.", LogType.SUCCESS)
    }

    // ----------------------------------------------------
    // SVDAG World Engine Operations
    // ----------------------------------------------------

    fun ensureSvdagInitialized(targetDepth: Int = 7) {
        if (uiState.svdagWorld == null || uiState.svdagWorld?.maxDepth != targetDepth) {
            val (dag, stats) = SvdagWorldBuilder.generateCyberspaceMegaSector(targetDepth)
            val initialIce = SvdagIcePathfinder.generateDefaultPatrolEntities(dag)
            val pPos = Triple(dag.gridSize / 2, dag.gridSize / 2, dag.gridSize / 2)
            val hideStatus = SvdagIcePathfinder.evaluatePlayerHidingStatus(pPos.first, pPos.second, pPos.third, dag)
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
        scope.launch(Dispatchers.Default) {
            val (dag, stats) = SvdagWorldBuilder.generateCyberspaceMegaSector(targetDepth, seed)
            val initialIce = SvdagIcePathfinder.generateDefaultPatrolEntities(dag)
            val pPos = Triple(dag.gridSize / 2, dag.gridSize / 2, dag.gridSize / 2)
            val hideStatus = SvdagIcePathfinder.evaluatePlayerHidingStatus(pPos.first, pPos.second, pPos.third, dag)
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
                addLog("SVDAG WORLD BUILDER: Generated ${dag.gridSize} Voxels (${stats.totalNodes} DAG Nodes)", LogType.SUCCESS)
                addLog("SVDAG DEDUPLICATION: ${String.format(java.util.Locale.US, "%.1f%%", stats.compressionRatio)} memory reduction.", LogType.INFO)
                addLog("SVDAG ICE SECURITY: Spawned ${initialIce.size} hallway patrol daemons with A* pathfinding.", LogType.ALERT)
            }
        }
    }

    fun moveSvdagPlayer(dx: Int, dy: Int, dz: Int) {
        val dag = uiState.svdagWorld ?: return
        val currentP = uiState.svdagPlayerPos
        val nx = (currentP.first + dx).coerceIn(0, dag.gridSize - 1)
        val ny = (currentP.second + dy).coerceIn(0, dag.gridSize - 1)
        val nz = (currentP.third + dz).coerceIn(0, dag.gridSize - 1)
        val newPos = Triple(nx, ny, nz)

        val hideStatus = SvdagIcePathfinder.evaluatePlayerHidingStatus(nx, ny, nz, dag, _uiState.value.maze)
        _uiState.update {
            it.copy(
                svdagPlayerPos = newPos,
                svdagPlayerHideStatus = hideStatus
            )
        }
        if (hideStatus.isHidden) {
            addLog("STEALTH EVASION: Player reached ($nx, $ny, $nz) - ${hideStatus.hideReason}", LogType.INFO)
        } else {
            addLog("PLAYER MOVED: Position ($nx, $ny, $nz) - EXPOSED IN HALLWAY", LogType.INFO)
        }
    }

    fun tickSvdagIceAI() {
        val dag = uiState.svdagWorld ?: return
        val currentIce = uiState.svdagIceEntities
        val pPos = uiState.svdagPlayerPos
        val maze = uiState.maze

        val updatedIceList = mutableListOf<com.example.data.svdag.IceEntity>()
        var playerIntercepted = false

        for (ice in currentIce) {
            val res = SvdagIcePathfinder.tickIceEntity(ice, pPos, dag, maze)
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

        val hideStatus = SvdagIcePathfinder.evaluatePlayerHidingStatus(pPos.first, pPos.second, pPos.third, dag, maze)

        _uiState.update {
            it.copy(
                svdagIceEntities = updatedIceList,
                svdagPlayerHideStatus = hideStatus
            )
        }

        if (playerIntercepted && !hideStatus.isHidden) {
            soundManager.playHackingErrorSound()
            addLog("CRITICAL SECURITY BREACH: ICE Security Patrol intercepted player in hallway!", LogType.ALERT)
            onTriggerCombat(pPos.first, pPos.second)
        }
    }

    fun modifySvdagVoxel(x: Int, y: Int, z: Int, type: VoxelType) {
        val currentDag = uiState.svdagWorld ?: return
        currentDag.setVoxel(x, y, z, type)
        val newStats = currentDag.getStats(lodLevel = uiState.svdagLodLevel)
        _uiState.update { it.copy(svdagStats = newStats) }
    }

    fun setSvdagLodLevel(lod: Int) {
        val activeLod = lod.coerceIn(0, 4)
        val dag = uiState.svdagWorld
        val newStats = dag?.getStats(lodLevel = activeLod) ?: uiState.svdagStats
        _uiState.update { it.copy(svdagLodLevel = activeLod, svdagStats = newStats) }
        val cellSize = 1 shl activeLod
        addLog("SVDAG LOD SYSTEM: Switched to Level of Detail $activeLod (${cellSize} Voxel Block Aggregation)", LogType.INFO)
    }

    fun triggerSvdagScan(originX: Int? = null, originY: Int? = null, originZ: Int? = null, radius: Int = 16) {
        val currentDag = uiState.svdagWorld ?: return
        val ox = originX ?: (currentDag.gridSize / 2)
        val oy = originY ?: (currentDag.gridSize / 2)
        val oz = originZ ?: (currentDag.gridSize / 2)

        val summary = SvdagScannerService.performSvdagScan(
            dag = currentDag,
            originX = ox,
            originY = oy,
            originZ = oz,
            radius = radius,
            activeIceEntities = uiState.svdagIceEntities
        )

        val now = System.currentTimeMillis()
        val rippleState = SvdagScannerService.computeRippleState(
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
        addLog("SVDAG SCANNER SERVICE EXECUTED: Radius $radius Voxels sonar sweep!", LogType.SUCCESS)
        addLog("  Detected ${summary.interactiveCount} Interactive Objects, ${summary.secretCount} Classified Secrets, ${summary.alternativePathCount} Bypass Vents.", LogType.INFO)
    }

    fun enterSvdagWorldInspector() {
        ensureSvdagInitialized(uiState.svdagScaleDepth)
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

    // ----------------------------------------------------
    // Terminal Command Interpreter (Exploration & SVDAG)
    // ----------------------------------------------------

    fun runTerminalCommand(parts: List<String>, state: GameViewModel.GameUiState): Boolean {
        val cmd = parts[0].lowercase()
        return when (cmd) {
            "w", "n", "north", "up", "forward", "move" -> {
                if (state.screen == ActiveScreen.EXPLORATION) {
                    moveForward()
                    true
                } else {
                    addLog("ERROR: Movement command only valid during active exploration.", LogType.ERROR)
                    true
                }
            }
            "s", "south", "back", "backward", "down" -> {
                if (state.screen == ActiveScreen.EXPLORATION) {
                    moveBackward()
                    true
                } else {
                    addLog("ERROR: Movement command only valid during active exploration.", LogType.ERROR)
                    true
                }
            }
            "a", "west", "left", "turnleft" -> {
                if (state.screen == ActiveScreen.EXPLORATION) {
                    turnLeft()
                    true
                } else {
                    addLog("ERROR: Turn command only valid during active exploration.", LogType.ERROR)
                    true
                }
            }
            "d", "east", "right", "turnright" -> {
                if (state.screen == ActiveScreen.EXPLORATION) {
                    turnRight()
                    true
                } else {
                    addLog("ERROR: Turn command only valid during active exploration.", LogType.ERROR)
                    true
                }
            }
            "e", "interact", "activate" -> {
                if (state.screen == ActiveScreen.EXPLORATION) {
                    interact()
                    true
                } else {
                    addLog("ERROR: Interaction command only valid during active exploration.", LogType.ERROR)
                    true
                }
            }
            "scan", "radar", "sonar" -> {
                if (state.screen == ActiveScreen.EXPLORATION) {
                    triggerMapScan()
                    true
                } else {
                    addLog("ERROR: Scan command only valid during active exploration.", LogType.ERROR)
                    true
                }
            }
            "ascend", "stairsup", "stairs_up" -> {
                if (state.screen == ActiveScreen.EXPLORATION) {
                    ascendStairs()
                    true
                } else false
            }
            "descend", "stairsdown", "stairs_down" -> {
                if (state.screen == ActiveScreen.EXPLORATION) {
                    descendStairs()
                    true
                } else false
            }
            "elevator" -> {
                if (state.screen == ActiveScreen.EXPLORATION) {
                    interactWithElevator()
                    true
                } else false
            }
            "svdag", "voxelworld" -> {
                when {
                    parts.getOrNull(1) == "enter" || parts.getOrNull(1) == "open" -> { enterSvdagWorldInspector(); true }
                    parts.getOrNull(1) == "exit" || parts.getOrNull(1) == "close" -> { exitSvdagWorldInspector(); true }
                    parts.getOrNull(1) == "regen" || parts.getOrNull(1) == "generate" -> {
                        val depth = parts.getOrNull(2)?.toIntOrNull() ?: 7
                        initOrRegenerateSvdag(depth)
                        true
                    }
                    parts.getOrNull(1) == "scan" -> {
                        val radius = parts.getOrNull(2)?.toIntOrNull() ?: 16
                        triggerSvdagScan(radius = radius)
                        true
                    }
                    parts.getOrNull(1) == "tick" || parts.getOrNull(1) == "ai" -> { tickSvdagIceAI(); true }
                    parts.getOrNull(1) == "lod" -> {
                        val lod = parts.getOrNull(2)?.toIntOrNull() ?: 0
                        setSvdagLodLevel(lod)
                        true
                    }
                    parts.getOrNull(1) == "move" && parts.size >= 5 -> {
                        val dx = parts.getOrNull(2)?.toIntOrNull() ?: 0
                        val dy = parts.getOrNull(3)?.toIntOrNull() ?: 0
                        val dz = parts.getOrNull(4)?.toIntOrNull() ?: 0
                        moveSvdagPlayer(dx, dy, dz)
                        true
                    }
                    else -> {
                        addLog("SVDAG: Usage - svdag <enter|exit|regen|scan|tick|lod|move> [args]", LogType.INFO)
                        true
                    }
                }
            }
            else -> false
        }
    }
}
