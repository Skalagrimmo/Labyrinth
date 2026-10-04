package com.example.data

import kotlin.math.abs
import kotlin.random.Random

object GameEngine {

    // Procedural Maze Generator
    // Returns a 2D Array of CellType of size width x height
    fun generateMaze(
        width: Int = 10,
        height: Int = 10,
        layer: Int = 1,
        seed: Long = System.currentTimeMillis() + layer * 123L
    ): Array<Array<CellType>> =
        MazeGenerator.generate(width = width, height = height, layer = layer, seed = seed)

    // Specialized procedural environments. Kept as compatibility facades during rebuild.
    fun generateBuildingFloor(
        floor: Int,
        seed: Long = System.currentTimeMillis()
    ): Array<Array<CellType>> =
        EnvironmentGenerator.generateBuildingFloor(floor = floor, seed = seed)

    fun generateCollectorTunnels(
        level: Int,
        seed: Long = System.currentTimeMillis()
    ): Array<Array<CellType>> =
        EnvironmentGenerator.generateCollectorTunnels(level = level, seed = seed)

    fun generateCitySector(
        districtIndex: Int,
        seed: Long = System.currentTimeMillis()
    ): Array<Array<CellType>> =
        EnvironmentGenerator.generateCitySector(districtIndex = districtIndex, seed = seed)

    private fun generateFallbackMaze(width: Int, height: Int): Array<Array<CellType>> {
        val grid = Array(height) { Array(width) { CellType.WALL } }
        val roomsCount = 4
        
        // Define centers of rooms along the diagonal
        val centers = mutableListOf<Pair<Int, Int>>()
        for (i in 0 until roomsCount) {
            val t = i.toFloat() / (roomsCount - 1)
            val cx = (1 + t * (width - 3)).toInt().coerceIn(1, width - 2)
            val cy = (1 + t * (height - 3)).toInt().coerceIn(1, height - 2)
            centers.add(Pair(cx, cy))
        }
        
        // Carve rooms (9x9 size to require 9 steps to cross)
        for ((cx, cy) in centers) {
            for (dy in -4..4) {
                for (dx in -4..4) {
                    val rx = cx + dx
                    val ry = cy + dy
                    if (rx in 1 until width - 1 && ry in 1 until height - 1) {
                        grid[ry][rx] = CellType.PATH
                    }
                }
            }
        }
        
        // Connect rooms linearly
        for (i in 0 until centers.size - 1) {
            val (x1, y1) = centers[i]
            val (x2, y2) = centers[i + 1]
            var cx = x1
            var cy = y1
            while (cx != x2) {
                if (cx in 1 until width - 1 && cy in 1 until height - 1) {
                    grid[cy][cx] = CellType.PATH
                }
                cx += if (x2 > cx) 1 else -1
            }
            while (cy != y2) {
                if (cx in 1 until width - 1 && cy in 1 until height - 1) {
                    grid[cy][cx] = CellType.PATH
                }
                cy += if (y2 > cy) 1 else -1
            }
        }
        
        // Place critical elements
        grid[1][1] = CellType.SAFE_ZONE
        
        val exitX = centers.last().first
        val exitY = centers.last().second
        grid[exitY][exitX] = CellType.ENCRYPTED_PORTAL
        
        // Place some items in other room centers
        if (centers.size > 2) {
            val (dx1, dy1) = centers[1]
            grid[dy1][dx1] = CellType.DATA_STORE
            
            val (dx2, dy2) = centers[2]
            grid[dy2][dx2] = CellType.VIRUS_NODE
        }
        
        return grid
    }

    // Canvas-style 3D ASCII Perspective Wireframe Drawer
    fun render3DPerspective(
        grid: Array<Array<CellType>>,
        px: Int,
        py: Int,
        dir: Direction,
        activeWeather: CyberWeather = CyberWeather.CLEAR
    ): String =
        AsciiPerspectiveRenderer.render(
            grid = grid,
            px = px,
            py = py,
            dir = dir,
            activeWeather = activeWeather
        )

    // Default starting cyberwares list
    fun getStoreCyberware(): List<Cyberware> {
        return listOf(
            Cyberware("cpu_oc", "CPU Overclocker", "+2 RAM Recovery Rate", 200, recoveryBonus = 2),
            Cyberware("mem_exp", "RAM Rig Extension", "+4 Max RAM Allocation", 250, ramBonus = 4),
            Cyberware("armor_plt", "Sub-Dermal Firewall", "+30 System Integrity", 180, integrityBonus = 30),
            Cyberware("dmg_mod", "Payload Amplifier", "+5 Attack Damage output", 300, damageBonus = 5),
            Cyberware("def_mod", "Defensive Buffer", "+10% Armor Defense", 220, defenseBonus = 2)
        )
    }

    // Starting programs list
    fun getStartingPrograms(runnerClass: NetrunnerClass): List<Program> {
        val base = mutableListOf(
            Program("ping", "ping.exe", "Scan enemy process. Deals 10 payload damage.", ramCost = 1, damage = 10),
            Program("firewall", "firewall.sh", "Harden defences. Restore 25 shield points.", ramCost = 2, shield = 25),
            Program("corrode", "acid_corrode.sh", "Inject malware. Deals 12 dmg + Corrodes target (8 DPS x 3 turns).", ramCost = 2, damage = 12, statusEffectToApply = StatusEffectType.POISONED, statusEffectTurns = 3, statusEffectMagnitude = 8),
            Program("stun_pulse", "stun_pulse.exe", "High-voltage surge. Deals 10 dmg + Stuns target for 1 turn.", ramCost = 3, damage = 10, statusEffectToApply = StatusEffectType.STUNNED, statusEffectTurns = 1),
            Program("overclock", "overclock.sys", "Overclock system core. Grants Overclocked buff (+50% attack dmg for 2 turns).", ramCost = 3, statusEffectToApply = StatusEffectType.BUFFED, statusEffectTurns = 2, statusEffectTargetSelf = true),
            Program("glitch", "glitch_payload.bin", "Scramble target sensors. Glitches target (-50% damage output for 2 turns).", ramCost = 2, statusEffectToApply = StatusEffectType.WEAKENED, statusEffectTurns = 2)
        )
        when (runnerClass) {
            NetrunnerClass.NETRUNNER -> {
                base.add(Program("overflow", "exploit.sh", "Pierces defenses, dealing 25 raw damage.", ramCost = 3, damage = 25, piercesDefense = true))
                base.add(Program("kill9", "kill-9.bin", "Force shutdown. Deals 35 heavy payload damage.", ramCost = 4, damage = 35))
            }
            NetrunnerClass.STREET_SAMURAI -> {
                base.add(Program("katana_strike", "katana_slash.exe", "Lethal blade strike. Deals 40 physical payload damage.", ramCost = 3, damage = 40))
            }
            NetrunnerClass.TECHIE -> {
                base.add(Program("sandbox", "sandbox.sys", "Isolate threats. Restore 40 Integrity.", ramCost = 3, heal = 40))
                base.add(Program("custom_payload", "utility.exe", "Unpredictable script. Deals 20 damage, restores 15 Integrity.", ramCost = 2, damage = 20, heal = 15))
            }
            NetrunnerClass.CODE_SLASHER -> {
                base.add(Program("kill9", "kill-9.bin", "Force shutdown. Deals 35 heavy payload damage.", ramCost = 4, damage = 35))
            }
            NetrunnerClass.CYBER_SHIELD -> {
                base.add(Program("sandbox", "sandbox.sys", "Isolate threats. Restore 40 Integrity.", ramCost = 3, heal = 40))
            }
            NetrunnerClass.BUFFER_OVERFLOW -> {
                base.add(Program("overflow", "exploit.sh", "Pierces defenses, dealing 25 raw damage.", ramCost = 3, damage = 25, piercesDefense = true))
            }
            NetrunnerClass.SCRIPT_KIDDIE -> {
                base.add(Program("custom_payload", "utility.exe", "Unpredictable script. Deals 20 damage, restores 15 Integrity.", ramCost = 2, damage = 20, heal = 15))
            }
        }
        return base
    }

    // Generates a fully loaded enemy depending on the cyberspace layer
    /**
     * Enemy archetype catalog. Each archetype defines a distinct combat profile
     * (tank, glass-cannon, shield-heavy, healer, debuffer...) with stat multipliers,
     * optional starting status effects, and a unique ASCII portrait.
     */
    data class EnemyArchetype(
        val name: String,
        val description: String,
        val asciiArt: String,
        val minTier: Int,          // minimum depth/layer at which this enemy can appear
        val hpMult: Float = 1.0f,
        val shieldMult: Float = 1.0f,
        val dmgMult: Float = 1.0f,
        val armorBonus: Int = 0,
        val bountyMult: Float = 1.0f,
        val statusEffects: List<Pair<StatusEffectType, Int>> = emptyList() // (type, turns)
    )

    private val ENEMY_ARCHETYPES = listOf(
        // ---------- TIER 1 : Corporate Lobby Daemons (Building floors 1-2) ----------
        EnemyArchetype(
            name = "Worm.exe",
            description = "A fast-replicating data worm gnawing through transmission channels. Weak but persistent.",
            asciiArt = "  ~o~~~~~o~~\n (  o  _  o )\n  ~~o~~~~~o~",
            minTier = 1, dmgMult = 1.15f, bountyMult = 0.9f
        ),
        EnemyArchetype(
            name = "Spyware.dll",
            description = "Stealthy surveillance daemon that siphons RAM and exposes system weaknesses.",
            asciiArt = "  /-------\\\n < (o) (o) >\n  \\_  ^  _/",
            minTier = 1, shieldMult = 0.8f, dmgMult = 1.1f,
            statusEffects = listOf(StatusEffectType.WEAKENED to 2)
        ),
        EnemyArchetype(
            name = "Trojan.Horse",
            description = "A disguised intrusion script that appears harmless until deep inside the network.",
            asciiArt = "  ,_____\n /_ _ _ \\\n |o|   |o|\n |_______|",
            minTier = 1, dmgMult = 1.25f, armorBonus = 2
        ),
        EnemyArchetype(
            name = "ScriptKiddie.Bot",
            description = "An amateur automated script, clumsy but erratic and unpredictable.",
            asciiArt = "  (>_<)\n  (o o)\n  (_|_)",
            minTier = 1, hpMult = 0.8f, dmgMult = 1.0f, bountyMult = 1.1f
        ),

        // ---------- TIER 2 : Building Core / Reactor ----------
        EnemyArchetype(
            name = "LogicBomb.sh",
            description = "A dormant payload rigged to detonate with devastating cascading corruption.",
            asciiArt = "   _\\|/_\n  ( o_o )\n  (_____) ",
            minTier = 2, dmgMult = 1.4f,
            statusEffects = listOf(StatusEffectType.POISONED to 3)
        ),
        EnemyArchetype(
            name = "Ransomware.crypt",
            description = "Locks your core files and demands credits. Armor-tough encryption shell.",
            asciiArt = "  [Locked]\n  [ O_O  ]\n  [=====_]",
            minTier = 2, armorBonus = 3, hpMult = 1.2f, bountyMult = 1.2f
        ),
        EnemyArchetype(
            name = "Rootkit.sys",
            description = "Hides deep in the operating system and erodes your defenses from within.",
            asciiArt = "   /\\_/\\\n  ( >.< )\n   =(I)=",
            minTier = 2, shieldMult = 1.3f, dmgMult = 1.15f,
            statusEffects = listOf(StatusEffectType.WEAKENED to 1)
        ),
        EnemyArchetype(
            name = "Firewall Guardian",
            description = "A sentry program reinforced with heavy subdermal-plated shielding.",
            asciiArt = "  [GUARD]\n  |=o o=|\n  |_____|",
            minTier = 2, shieldMult = 1.6f, hpMult = 1.3f, armorBonus = 2, dmgMult = 0.9f
        ),

        // ---------- TIER 3 : Collector Sub-Grid ----------
        EnemyArchetype(
            name = "ZombieBot.bin",
            description = "A thrall daemon under external control, relentless but mentally fragmented.",
            asciiArt = "  [Z][Z]\n  ( x_x )\n  (_|_|_)",
            minTier = 3, hpMult = 1.4f, dmgMult = 1.2f,
            statusEffects = listOf(StatusEffectType.BUFFED to 2)
        ),
        EnemyArchetype(
            name = "VampirePacket.sys",
            description = "Drains your RAM reserves to fuel its own corrupted data stream.",
            asciiArt = "  \\\\____//\n   ( o_o )\n  __|___|__",
            minTier = 3, hpMult = 0.9f, dmgMult = 1.25f, shieldMult = 1.2f
        ),
        EnemyArchetype(
            name = "Adware Construct",
            description = "A bloated, loud daemon that hammers you with overwhelming corrupt spam.",
            asciiArt = "  [AD!]\n  ( O_o )\n  |>_<|",
            minTier = 3, hpMult = 1.3f, dmgMult = 1.35f, bountyMult = 1.3f
        ),
        EnemyArchetype(
            name = "Scav-Killer.exe",
            description = "A hardened hunter that preys on weaker black-ice, relentless and fast.",
            asciiArt = "   ,___,\n  < o o >\n   \\_|_/",
            minTier = 3, dmgMult = 1.5f, armorBonus = 2, hpMult = 1.1f
        ),

        // ---------- TIER 4 : City Metro Districts ----------
        EnemyArchetype(
            name = "Cryptolocker.Baron",
            description = "An elite ransomware general that enslaves your files for massive ransoms.",
            asciiArt = " [BARON]\n [  ]|[ ]\n |_____|",
            minTier = 4, hpMult = 1.6f, armorBonus = 4, bountyMult = 1.5f
        ),
        EnemyArchetype(
            name = "IceWyrm.sys",
            description = "A colossal serpentine ICE construct that coils and strikes with venomous payloads.",
            asciiArt = " ~~~~~~\n<((( )))>\n ~~~^~~~",
            minTier = 4, hpMult = 1.5f, dmgMult = 1.4f, shieldMult = 1.2f,
            statusEffects = listOf(StatusEffectType.POISONED to 4)
        ),
        EnemyArchetype(
            name = "Overlord Lieutenant",
            description = "A Daemon-Overlord sub-commandant that buffs any black-ice around it.",
            asciiArt = " [LIEUT]\n  ( O,O )\n  |_____|",
            minTier = 4, hpMult = 1.7f, shieldMult = 1.5f, dmgMult = 1.3f,
            statusEffects = listOf(StatusEffectType.BUFFED to 3)
        ),
        EnemyArchetype(
            name = "Synthwraith.exe",
            description = "A fragment of a long-dead netrunner, glitching between dimensions and stacking curses.",
            asciiArt = "  ( ~ ~ )\n  <  o  >\n   /|_|\\",
            minTier = 4, hpMult = 0.8f, dmgMult = 1.6f, shieldMult = 1.4f,
            statusEffects = listOf(StatusEffectType.WEAKENED to 3, StatusEffectType.POISONED to 2)
        ),
        EnemyArchetype(
            name = "BlackICE Berserker",
            description = "A rage-maddened ICE unit that forgoes defense for pure, overwhelming offense.",
            asciiArt = "  [ RAGE ]\n  ( >_< )\n  ((:=))",
            minTier = 4, dmgMult = 1.8f, hpMult = 1.2f, shieldMult = 0.6f,
            statusEffects = listOf(StatusEffectType.BUFFED to 2)
        )
    )

    /**
     * Mod-registered enemy archetypes dynamically added at runtime.
     * Populated by [ContentRegistry] when a mod document is loaded.
     */
    val registeredEnemyArchetypes = mutableListOf<EnemyArchetype>()

    /** Registers additional enemy archetypes from mods. */
    fun registerEnemyArchetypes(archetypes: List<EnemyArchetype>) {
        registeredEnemyArchetypes.addAll(archetypes)
    }

    /**
     * Spawns a procedurally chosen enemy scaled to the current depth/layer.
     * Higher tiers (and tougher archetypes) unlock as the player descends.
     * Mod-registered archetypes (via [registerEnemyArchetypes]) are merged in.
     */
    fun spawnEnemy(layer: Int): Enemy =
        EnemyFactory.create(
            layer = layer,
            archetypes = ENEMY_ARCHETYPES + registeredEnemyArchetypes
        )

    fun spawnBoss(bossType: BossType, level: Int): Enemy =
        BossFactory.create(bossType = bossType, level = level)

    // Hacking puzzle matrix generator
    fun generateHackingPuzzle(difficulty: Int): HackingPuzzle {
        val random = Random(System.currentTimeMillis())
        val hexPool = listOf("1C", "E9", "55", "BD", "7A", "FF")
        val size = 5

        // Fill grid
        val grid = Array(size) { Array(size) { hexPool[random.nextInt(hexPool.size)] } }

        // Generate a valid solution of length (difficulty + 2)
        val solutionLength = difficulty + 2
        val path = mutableListOf<Pair<Int, Int>>()

        var curRow = 0
        var curCol = random.nextInt(size)
        path.add(Pair(curRow, curCol))

        var isHorizontal = false // Horizontal is next since we chose a column in row 0.

        for (step in 1 until solutionLength) {
            if (isHorizontal) {
                // Next step in the same row, select a column
                val availableCols = (0 until size).filter { col -> !path.contains(Pair(curRow, col)) }
                if (availableCols.isEmpty()) break
                curCol = availableCols[random.nextInt(availableCols.size)]
                path.add(Pair(curRow, curCol))
            } else {
                // Next step in the same column, select a row
                val availableRows = (0 until size).filter { row -> !path.contains(Pair(row, curCol)) }
                if (availableRows.isEmpty()) break
                curRow = availableRows[random.nextInt(availableRows.size)]
                path.add(Pair(curRow, curCol))
            }
            isHorizontal = !isHorizontal
        }

        // Target sequence is the characters at the path
        val targetSequence = path.map { grid[it.first][it.second] }

        return HackingPuzzle(
            grid = grid,
            targetSequence = targetSequence,
            bufferLimit = 5 + difficulty
        )
    }
}

class CharCanvas(val rows: Int, val cols: Int) {
    private val buffer = Array(rows) { CharArray(cols) { ' ' } }

    fun set(r: Int, c: Int, ch: Char) {
        if (r in 0 until rows && c in 0 until cols) {
            buffer[r][c] = ch
        }
    }

    fun drawLine(r1: Int, c1: Int, r2: Int, c2: Int, ch: Char) {
        val dr = abs(r2 - r1)
        val dc = abs(c2 - c1)
        val sr = if (r1 < r2) 1 else -1
        val sc = if (c1 < c2) 1 else -1
        var err = dr - dc
        var r = r1
        var c = c1
        while (true) {
            set(r, c, ch)
            if (r == r2 && c == c2) break
            val e2 = 2 * err
            if (e2 > -dc) {
                err -= dc
                r += sr
            }
            if (e2 < dr) {
                err += dr
                c += sc
            }
        }
    }

    fun render(): String {
        return buffer.joinToString("\n") { String(it) }
    }
}

data class HackingPuzzle(
    val grid: Array<Array<String>>,
    val targetSequence: List<String>,
    val bufferLimit: Int,
    val selectedIndices: List<Pair<Int, Int>> = emptyList(),
    val currentBuffer: List<String> = emptyList(),
    var isSolved: Boolean = false,
    var isFailed: Boolean = false,
    var highlightedRow: Int? = 0, // Starts at row 0 highlighted
    var highlightedCol: Int? = null
)
