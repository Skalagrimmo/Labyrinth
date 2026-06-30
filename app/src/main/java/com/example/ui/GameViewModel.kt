package com.example.ui

import android.app.Application
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

class GameViewModel(application: Application) : AndroidViewModel(application) {

    private val database = GameDatabase.getDatabase(application)
    private val repository = GameRepository(database.runRecordDao())

    // High scores stream
    val runRecords: StateFlow<List<RunRecord>> = repository.allRunRecords
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Primary Game UI State
    data class GameUiState(
        val screen: ActiveScreen = ActiveScreen.CHARACTER_CREATION,
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

        // Equipment & Abilities
        val installedCyberware: List<Cyberware> = emptyList(),
        val installedPrograms: List<Program> = emptyList(),
        val inventory: List<String> = listOf("NanoMed.sys", "RAMBoost.exe"),

        // Active combat
        val activeEnemy: Enemy? = null,
        val enemyCombatAction: String = "",

        // Active hacking puzzle
        val activePuzzle: HackingPuzzle? = null,
        val targetNodeX: Int = -1,
        val targetNodeY: Int = -1,

        // Logs
        val logFeed: List<LogMessage> = emptyList(),

        // Stats tracking for current run
        val nodesHackedCount: Int = 0,
        val totalCreditsEarned: Int = 100,
        val runOutcome: String = ""
    )

    enum class ActiveScreen {
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

    init {
        // Initialize with default logging
        addLog("DECENTRALIZED TERMINAL ESTABLISHED...", LogType.SUCCESS)
        addLog("CYBERSPACE INTRUSION PROTOCOL READY. SELECT PROFILE.", LogType.INFO)
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
                logFeed = emptyList() // clear creation logs for clean game view
            )
        }

        addLog("==========================================", LogType.SUCCESS)
        addLog("PROFILE SYNCHRONIZED: $cleanName [${selectedClass.title}]", LogType.SUCCESS)
        addLog("SPECIALIZATION: ${selectedClass.passiveDesc}", LogType.INFO)
        addLog("INITIALIZING CYBER-SECTOR GRID...", LogType.ALERT)

        generateNewLevel()
    }

    // Generates the maze grid and updates 1st person perspective
    private fun generateNewLevel() {
        val level = _uiState.value.level
        // Scale labyrinth size dynamically: 15x15 at layer 1, increasing up to 27x27 (always odd for perfect layout and size density)
        val size = minOf(15 + ((level - 1) * 2), 27)
        val maze = GameEngine.generateMaze(size, size, level)
        val perspective = GameEngine.render3DPerspective(maze, 1, 1, Direction.EAST)

        _uiState.update { state ->
            state.copy(
                maze = maze,
                gridX = 1,
                gridY = 1,
                direction = Direction.EAST,
                perspectiveText = perspective
            )
        }
        addLog("CYBERSPACE COGNITIVE NODE LAYER $level SECURED.", LogType.SUCCESS)
        addLog("PROCEED CAUTIOUSLY. ACTIVE DESTRUCTION VIRUSES RECONSTRUCTED.", LogType.ALERT)
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
        if (_uiState.value.screen != ActiveScreen.EXPLORATION) return

        val state = _uiState.value
        val nextX = state.gridX + state.direction.dx
        val nextY = state.gridY + state.direction.dy

        if (isValidMove(nextX, nextY)) {
            val cell = state.maze[nextY][nextX]
            if (cell == CellType.VIRUS_NODE) {
                // Intercepted by security! Trigger combat
                triggerCombat(nextX, nextY)
            } else {
                _uiState.update { it.copy(gridX = nextX, gridY = nextY) }
                updatePerspective()

                // Small chance of recovering 1 RAM during safe navigation
                recoverRamOnMove()

                addLog("MOVED FORWARD into sub-channel (${nextX}, ${nextY})")
                checkCellTriggers(nextX, nextY, cell)
            }
        } else {
            addLog("ACCESS DENIED: Physical Firewall Blocked.", LogType.ERROR)
        }
    }

    fun moveBackward() {
        if (_uiState.value.screen != ActiveScreen.EXPLORATION) return

        val state = _uiState.value
        val nextX = state.gridX - state.direction.dx
        val nextY = state.gridY - state.direction.dy

        if (isValidMove(nextX, nextY)) {
            val cell = state.maze[nextY][nextX]
            if (cell == CellType.VIRUS_NODE) {
                triggerCombat(nextX, nextY)
            } else {
                _uiState.update { it.copy(gridX = nextX, gridY = nextY) }
                updatePerspective()
                recoverRamOnMove()
                addLog("MOVED BACKWARD into sub-channel (${nextX}, ${nextY})")
                checkCellTriggers(nextX, nextY, cell)
            }
        } else {
            addLog("ACCESS DENIED: Solid Core Boundary.", LogType.ERROR)
        }
    }

    fun turnLeft() {
        if (_uiState.value.screen != ActiveScreen.EXPLORATION) return
        _uiState.update { state ->
            val newDir = state.direction.turnLeft()
            state.copy(direction = newDir)
        }
        updatePerspective()
        addLog("ROTATED VECTOR 90° LEFT.")
    }

    fun turnRight() {
        if (_uiState.value.screen != ActiveScreen.EXPLORATION) return
        _uiState.update { state ->
            val newDir = state.direction.turnRight()
            state.copy(direction = newDir)
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
                addLog("STAIRS UP: Ascending vertical metal steps connecting server core layers.", LogType.INFO)
            }
            CellType.STAIRS_DOWN -> {
                addLog("STAIRS_DOWN: Descending heavy-duty stairs leading into deeper hardware subsectors.", LogType.INFO)
            }
            CellType.GRAVITY_SLOPE -> {
                addLog("GRAVITY SLOPE: Scaling a steep gravity-modulated concourse incline.", LogType.INFO)
            }
            else -> {}
        }
    }

    private fun isValidMove(x: Int, y: Int): Boolean {
        val maze = _uiState.value.maze
        if (y !in maze.indices || x !in maze[0].indices) return false
        return maze[y][x] != CellType.WALL
    }

    private fun updatePerspective() {
        val state = _uiState.value
        val perspective = GameEngine.render3DPerspective(state.maze, state.gridX, state.gridY, state.direction)
        _uiState.update { it.copy(perspectiveText = perspective) }
    }

    // ----------------------------------------------------
    // Special Node Interactions
    // ----------------------------------------------------

    fun interact() {
        if (_uiState.value.screen != ActiveScreen.EXPLORATION) return

        val state = _uiState.value
        // 1. First, check if there is an interactive cell directly ahead of us
        val targetX = state.gridX + state.direction.dx
        val targetY = state.gridY + state.direction.dy

        var cellToInteractWith = CellType.PATH
        var interactX = targetX
        var interactY = targetY

        if (targetY in state.maze.indices && targetX in state.maze[0].indices) {
            val cellAhead = state.maze[targetY][targetX]
            if (cellAhead == CellType.DATA_STORE || cellAhead == CellType.ENCRYPTED_PORTAL || cellAhead == CellType.VIRUS_NODE || cellAhead == CellType.SECRET_CACHE) {
                cellToInteractWith = cellAhead
            }
        }

        // 2. If no interactive cell is ahead, check if we are standing on one!
        if (cellToInteractWith == CellType.PATH) {
            val cellCurrent = state.maze[state.gridY][state.gridX]
            if (cellCurrent == CellType.DATA_STORE || cellCurrent == CellType.ENCRYPTED_PORTAL || cellCurrent == CellType.VIRUS_NODE || cellCurrent == CellType.SECRET_CACHE) {
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
            CellType.ENCRYPTED_PORTAL -> {
                addLog("SUB-SECTOR DECRYPTION INITIALIZED...", LogType.SUCCESS)
                // Go to next cyberspace level!
                _uiState.update { stateNow ->
                    val nextLvl = stateNow.level + 1
                    stateNow.copy(
                        level = nextLvl,
                        credits = stateNow.credits + 150, // Bonus credits for sector completion
                        totalCreditsEarned = stateNow.totalCreditsEarned + 150
                    )
                }
                addLog("DECRYPTED AND TRANSFERRED TO CORE SECTOR ${_uiState.value.level}.", LogType.SUCCESS)
                generateNewLevel()
            }
            CellType.VIRUS_NODE -> {
                addLog("FORCE-CONNECTING WITH ACTIVE THREAT...", LogType.ALERT)
                triggerCombat(interactX, interactY)
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

        _uiState.update { stateNow ->
            stateNow.copy(
                screen = ActiveScreen.EXPLORATION,
                credits = stateNow.credits + bountyCredits,
                totalCreditsEarned = stateNow.totalCreditsEarned + bountyCredits,
                inventory = updatedInventory,
                nodesHackedCount = stateNow.nodesHackedCount + 1,
                maze = updatedMaze,
                activePuzzle = null
            )
        }

        updatePerspective()
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
                screen = ActiveScreen.COMBAT,
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
        if (state.screen != ActiveScreen.COMBAT) return

        // Backtrack player to starting safe point or previous cell
        // We can place them safely at (1, 1) or just escape with small penalty
        val penalty = 20
        val newCredits = maxOf(0, state.credits - penalty)

        _uiState.update { stateNow ->
            stateNow.copy(
                screen = ActiveScreen.EXPLORATION,
                credits = newCredits,
                activeEnemy = null,
                gridX = 1,
                gridY = 1 // Safe teleport back to sector start
            )
        }

        updatePerspective()
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
                else -> {
                    logText = "RUNNING Generic cyber utility: No system changes."
                    stateNow.copy(inventory = updatedInventory)
                }
            }
        }

        addLog(logText, LogType.SUCCESS)
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
        _uiState.update { GameUiState() }
        addLog("REBOOTING TERMINAL CORE V8.91...", LogType.ALERT)
        addLog("SELECT NETRUNNER ARCHETYPE PROFILE TO COMPILE.", LogType.INFO)
    }
}
