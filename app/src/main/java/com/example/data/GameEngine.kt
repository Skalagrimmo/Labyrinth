package com.example.data

import kotlin.math.abs
import kotlin.random.Random

object GameEngine {

    // Procedural Maze Generator
    // Returns a 2D Array of CellType of size width x height
    fun generateMaze(width: Int = 10, height: Int = 10, layer: Int = 1): Array<Array<CellType>> {
        val grid = Array(height) { Array(width) { CellType.WALL } }

        // Start carve from (1, 1)
        val startX = 1
        val startY = 1
        grid[startY][startX] = CellType.SAFE_ZONE

        val visited = Array(height) { BooleanArray(width) { false } }
        visited[startY][startX] = true

        val stack = mutableListOf<Pair<Int, Int>>()
        stack.add(Pair(startX, startY))

        val random = Random(System.currentTimeMillis() + layer * 123)

        // Standard DFS Maze Carving (on odd indices to ensure walls exist)
        while (stack.isNotEmpty()) {
            val (cx, cy) = stack.last()
            val neighbors = mutableListOf<Triple<Int, Int, Pair<Int, Int>>>() // target x, target y, wall x & y

            // Directions of step size 2
            val dirs = listOf(
                Triple(0, -2, Pair(0, -1)),
                Triple(2, 0, Pair(1, 0)),
                Triple(0, 2, Pair(0, 1)),
                Triple(-2, 0, Pair(-1, 0))
            )

            for (d in dirs) {
                val nx = cx + d.first
                val ny = cy + d.second
                if (nx in 1 until width - 1 && ny in 1 until height - 1) {
                    if (!visited[ny][nx]) {
                        neighbors.add(Triple(nx, ny, Pair(cx + d.third.first, cy + d.third.second)))
                    }
                }
            }

            if (neighbors.isNotEmpty()) {
                val chosen = neighbors[random.nextInt(neighbors.size)]
                val nx = chosen.first
                val ny = chosen.second
                val wx = chosen.third.first
                val wy = chosen.third.second

                grid[wy][wx] = CellType.PATH
                grid[ny][nx] = CellType.PATH

                visited[ny][nx] = true
                stack.add(Pair(nx, ny))
            } else {
                stack.removeAt(stack.size - 1)
            }
        }

        // Place special nodes
        // Ensure starting area is clean
        grid[1][1] = CellType.SAFE_ZONE
        grid[1][2] = CellType.PATH
        grid[2][1] = CellType.PATH

        val openCells = mutableListOf<Pair<Int, Int>>()
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                if (grid[y][x] == CellType.PATH && (x > 2 || y > 2)) {
                    openCells.add(Pair(x, y))
                }
            }
        }

        openCells.shuffle(random)

        // 1. Exit Portal (as far away as possible)
        var exitPlaced = false
        var maxDist = -1
        var exitCell = Pair(width - 2, height - 2)

        for (cell in openCells) {
            val dist = abs(cell.first - 1) + abs(cell.second - 1)
            if (dist > maxDist) {
                maxDist = dist
                exitCell = cell
            }
        }
        grid[exitCell.second][exitCell.first] = CellType.ENCRYPTED_PORTAL
        openCells.remove(exitCell)

        // 2. Data Stores (Hacking terminals)
        val dataStoreCount = 2 + random.nextInt(2)
        for (i in 0 until minOf(dataStoreCount, openCells.size)) {
            val cell = openCells[i]
            grid[cell.second][cell.first] = CellType.DATA_STORE
        }
        openCells.removeAll(openCells.take(minOf(dataStoreCount, openCells.size)))

        // 3. Virus Nodes (Enemies)
        val virusCount = 3 + random.nextInt(3) + (layer / 2)
        for (i in 0 until minOf(virusCount, openCells.size)) {
            val cell = openCells[i]
            grid[cell.second][cell.first] = CellType.VIRUS_NODE
        }

        return grid
    }

    // Canvas-style 3D ASCII Perspective Wireframe Drawer
    fun render3DPerspective(
        grid: Array<Array<CellType>>,
        px: Int,
        py: Int,
        dir: Direction
    ): String {
        val canvas = CharCanvas(11, 31)

        // Define rendering coordinates for depths 0, 1, 2, 3
        val tl_c = intArrayOf(0, 6, 11, 13)
        val tl_r = intArrayOf(0, 2, 3, 4)
        val bl_c = intArrayOf(0, 6, 11, 13)
        val bl_r = intArrayOf(10, 8, 7, 6)

        val tr_c = intArrayOf(30, 24, 19, 17)
        val tr_r = intArrayOf(0, 2, 3, 4)
        val br_c = intArrayOf(30, 24, 19, 17)
        val br_r = intArrayOf(10, 8, 7, 6)

        // Check view distance up to 3 cells
        val width = grid[0].size
        val height = grid.size

        // Let's gather the layout of cells ahead of us
        val cellTypes = Array(4) { CellType.WALL }
        val cellCoords = Array(4) { Pair(-1, -1) }

        for (d in 0..3) {
            val cx = px + d * dir.dx
            val cy = py + d * dir.dy
            if (cx in 0 until width && cy in 0 until height) {
                cellTypes[d] = grid[cy][cx]
                cellCoords[d] = Pair(cx, cy)
            } else {
                cellTypes[d] = CellType.WALL
                cellCoords[d] = Pair(cx, cy)
            }
        }

        // We check side walls at each depth d=0, 1, 2
        val leftWallAt = BooleanArray(3) { true }
        val rightWallAt = BooleanArray(3) { true }

        val leftDir = dir.turnLeft()
        val rightDir = dir.turnRight()

        for (d in 0..2) {
            val cc = cellCoords[d]
            if (cc.first != -1) {
                // Left neighbor at depth d
                val lx = cc.first + leftDir.dx
                val ly = cc.second + leftDir.dy
                if (lx in 0 until width && ly in 0 until height) {
                    leftWallAt[d] = grid[ly][lx] == CellType.WALL
                }

                // Right neighbor at depth d
                val rx = cc.first + rightDir.dx
                val ry = cc.second + rightDir.dy
                if (rx in 0 until width && ry in 0 until height) {
                    rightWallAt[d] = grid[ry][rx] == CellType.WALL
                }
            }
        }

        // Find the first blocking wall straight ahead
        var maxVisibleDepth = 3
        for (d in 1..3) {
            if (cellTypes[d] == CellType.WALL) {
                maxVisibleDepth = d
                break
            }
        }

        // --- Helper: High-Density Box Drawing ---
        fun drawBox(rStart: Int, cStart: Int, rEnd: Int, cEnd: Int) {
            for (col in (cStart + 1) until cEnd) {
                canvas.set(rStart, col, '━')
                canvas.set(rEnd, col, '━')
            }
            for (row in (rStart + 1) until rEnd) {
                canvas.set(row, cStart, '┃')
                canvas.set(row, cEnd, '┃')
            }
            canvas.set(rStart, cStart, '┏')
            canvas.set(rStart, cEnd, '┓')
            canvas.set(rEnd, cStart, '┗')
            canvas.set(rEnd, cEnd, '┛')
        }

        // --- Helper: Procedural Wall Filling with Linear Interpolation ---
        fun fillLeftWall(d: Int, char: Char) {
            val startCol = tl_c[d]
            val endCol = tl_c[d+1]
            val rTopStart = tl_r[d]
            val rTopEnd = tl_r[d+1]
            val rBotStart = bl_r[d]
            val rBotEnd = bl_r[d+1]

            for (col in startCol..endCol) {
                val colWidth = endCol - startCol
                val ratio = if (colWidth > 0) (col - startCol).toFloat() / colWidth else 0f
                val rTop = Math.round(rTopStart + ratio * (rTopEnd - rTopStart))
                val rBot = Math.round(rBotStart + ratio * (rBotEnd - rBotStart))
                for (row in rTop..rBot) {
                    canvas.set(row, col, char)
                }
            }
        }

        fun fillRightWall(d: Int, char: Char) {
            val startCol = tr_c[d+1]
            val endCol = tr_c[d]
            val rTopStart = tr_r[d+1]
            val rTopEnd = tr_r[d]
            val rBotStart = br_r[d+1]
            val rBotEnd = br_r[d]

            for (col in startCol..endCol) {
                val colWidth = endCol - startCol
                val ratio = if (colWidth > 0) (col - startCol).toFloat() / colWidth else 0f
                val rTop = Math.round(rTopStart + ratio * (rTopEnd - rTopStart))
                val rBot = Math.round(rBotStart + ratio * (rBotEnd - rBotStart))
                for (row in rTop..rBot) {
                    canvas.set(row, col, char)
                }
            }
        }

        // --- 1. Procedural Floor and Ceiling Dot Grid Generation ---
        // Pre-calculate top and bottom row limits for each column to texture background empty space
        val rTopLimits = IntArray(31) { 5 }
        val rBotLimits = IntArray(31) { 5 }

        for (col in 0..30) {
            var found = false
            for (d in 0..2) {
                if (col >= tl_c[d] && col <= tl_c[d+1]) {
                    val ratio = (col - tl_c[d]).toFloat() / (tl_c[d+1] - tl_c[d])
                    rTopLimits[col] = Math.round(tl_r[d] + ratio * (tl_r[d+1] - tl_r[d]))
                    rBotLimits[col] = Math.round(bl_r[d] + ratio * (bl_r[d+1] - bl_r[d]))
                    found = true
                    break
                }
            }
            if (!found) {
                for (d in 0..2) {
                    if (col >= tr_c[d+1] && col <= tr_c[d]) {
                        val ratio = (col - tr_c[d+1]).toFloat() / (tr_c[d] - tr_c[d+1])
                        rTopLimits[col] = Math.round(tr_r[d+1] + ratio * (tr_r[d] - tr_r[d+1]))
                        rBotLimits[col] = Math.round(br_r[d+1] + ratio * (br_r[d] - br_r[d+1]))
                        found = true
                        break
                    }
                }
            }
            if (!found) {
                val d = maxVisibleDepth.coerceAtMost(3)
                rTopLimits[col] = tl_r[d]
                rBotLimits[col] = bl_r[d]
            }
        }

        // Fill procedural dot grids
        for (col in 1..29) {
            val tLimit = rTopLimits[col]
            val bLimit = rBotLimits[col]
            
            // Ceiling Dot Grid
            for (row in 1 until tLimit) {
                if ((col + row * 2) % 4 == 0) {
                    canvas.set(row, col, '·')
                }
            }
            
            // Floor Dot Grid (converging perspective-like texture)
            for (row in (bLimit + 1)..9) {
                if ((col - row) % 4 == 0) {
                    canvas.set(row, col, '·')
                }
            }
        }

        // --- 2. Draw Front-Facing Wall (at blocking depth) ---
        if (maxVisibleDepth <= 3) {
            val d = maxVisibleDepth
            val rStart = tl_r[d]
            val rEnd = bl_r[d]
            val cStart = tl_c[d]
            val cEnd = tr_c[d]
            val frontShade = when (d) {
                1 -> '█' // Closest: solid bulkhead
                2 -> '▓' // Medium: dark block
                3 -> '▒' // Far: medium block
                else -> '░'
            }
            for (row in rStart..rEnd) {
                for (col in cStart..cEnd) {
                    canvas.set(row, col, frontShade)
                }
            }
            // Draw neat high-density box boundary around it
            drawBox(rStart, cStart, rEnd, cEnd)

            // Centered Bulkhead details
            if (d == 1) {
                val label = "[ SYSTEM BLK ]"
                val colStart = 15 - label.length / 2
                for (i in label.indices) {
                    canvas.set(5, colStart + i, label[i])
                }
            } else if (d == 2) {
                val label = "LOCKED"
                val colStart = 15 - label.length / 2
                for (i in label.indices) {
                    canvas.set(5, colStart + i, label[i])
                }
            }
        } else {
            // Draw very distant horizon at depth 3
            drawBox(tl_r[3], tl_c[3], bl_r[3], br_c[3])
            canvas.set(5, 15, '·') // Faint horizon vanishing point
        }

        // --- 3. Draw Side Walls from Back to Front ---
        val leftRightShades = charArrayOf('▓', '▒', '░')
        for (d in (maxVisibleDepth - 1) downTo 0) {
            // Draw Left Wall at depth d
            if (leftWallAt[d]) {
                val shadeChar = leftRightShades[d.coerceIn(0, 2)]
                fillLeftWall(d, shadeChar)

                // Define perspective diagonals
                canvas.drawLine(tl_r[d], tl_c[d], tl_r[d+1], tl_c[d+1], '\\')
                canvas.drawLine(bl_r[d], bl_c[d], bl_r[d+1], bl_c[d+1], '/')
            } else {
                // Open branch side opening ceiling & floor lines
                for (col in tl_c[d]..tl_c[d+1]) {
                    canvas.set(tl_r[d+1], col, '━')
                    canvas.set(bl_r[d+1], col, '━')
                }
                // Vertical structural pillar
                for (row in tl_r[d+1]..bl_r[d+1]) {
                    canvas.set(row, tl_c[d+1], '┃')
                }
            }

            // Draw Right Wall at depth d
            if (rightWallAt[d]) {
                val shadeChar = leftRightShades[d.coerceIn(0, 2)]
                fillRightWall(d, shadeChar)

                canvas.drawLine(tr_r[d], tr_c[d], tr_r[d+1], tr_c[d+1], '/')
                canvas.drawLine(br_r[d], br_c[d], br_r[d+1], br_c[d+1], '\\')
            } else {
                // Open branch side opening ceiling & floor lines
                for (col in tr_c[d+1]..tr_c[d]) {
                    canvas.set(tr_r[d+1], col, '━')
                    canvas.set(br_r[d+1], col, '━')
                }
                // Vertical structural pillar
                for (row in tr_r[d+1]..br_r[d+1]) {
                    canvas.set(row, tr_c[d+1], '┃')
                }
            }
        }

        // --- 4. Overlay Central Special Assets ---
        val primaryNode = cellTypes[1]
        if (primaryNode == CellType.VIRUS_NODE) {
            // High-density Virus icon using crisp Unicode elements
            canvas.set(3, 13, '▲')
            canvas.set(3, 17, '▲')
            
            canvas.set(4, 11, '◀')
            canvas.set(4, 13, '█')
            canvas.set(4, 14, '▄')
            canvas.set(4, 15, '▄')
            canvas.set(4, 16, '█')
            canvas.set(4, 18, '▶')
            
            canvas.set(5, 12, '╱')
            canvas.set(5, 13, '█')
            canvas.set(5, 14, '▀')
            canvas.set(5, 15, '█')
            canvas.set(5, 16, '╲')
            
            val label = "[VIRUS]"
            val startCol = 15 - label.length / 2
            for (i in label.indices) {
                canvas.set(6, startCol + i, label[i])
            }
        } else if (primaryNode == CellType.DATA_STORE) {
            // High-density Data Store terminal icon
            canvas.set(3, 11, '╔')
            for (c in 12..18) canvas.set(3, c, '═')
            canvas.set(3, 19, '╗')
            
            canvas.set(4, 11, '║')
            canvas.set(4, 13, 'D')
            canvas.set(4, 14, 'A')
            canvas.set(4, 15, 'T')
            canvas.set(4, 16, 'A')
            canvas.set(4, 19, '║')
            
            canvas.set(5, 11, '╚')
            for (c in 12..18) canvas.set(5, c, '═')
            canvas.set(5, 19, '╝')
            
            for (c in 10..20) canvas.set(6, c, '▒')
        } else if (primaryNode == CellType.ENCRYPTED_PORTAL) {
            // High-density Encrypted Portal vortex icon
            canvas.set(3, 12, '◢')
            canvas.set(3, 13, '█')
            canvas.set(3, 14, '█')
            canvas.set(3, 15, '█')
            canvas.set(3, 16, '█')
            canvas.set(3, 17, '█')
            canvas.set(3, 18, '◣')
            
            canvas.set(4, 11, '█')
            canvas.set(4, 13, 'P')
            canvas.set(4, 14, 'O')
            canvas.set(4, 15, 'R')
            canvas.set(4, 16, 'T')
            canvas.set(4, 18, '█')
            
            canvas.set(5, 12, '◥')
            canvas.set(5, 13, '█')
            canvas.set(5, 14, '█')
            canvas.set(5, 15, '█')
            canvas.set(5, 16, '█')
            canvas.set(5, 17, '█')
            canvas.set(5, 18, '◤')
            
            for (c in 11..19) canvas.set(6, c, '▒')
        }

        // --- 5. Draw Outer Border Frame ---
        drawBox(0, 0, 10, 30)

        return canvas.render()
    }

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
            Program("firewall", "firewall.sh", "Harden defences. Restore 25 shield points.", ramCost = 2, shield = 25)
        )
        when (runnerClass) {
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
    fun spawnEnemy(layer: Int): Enemy {
        val names = listOf("Worm.exe", "Trojan.Horse", "LogicBomb.sh", "Spyware.dll", "Ransomware.crypt", "Rootkit.sys")
        val random = Random(System.currentTimeMillis())
        val name = names[random.nextInt(names.size)]

        val integrity = 40 + (layer * 15) + random.nextInt(15)
        val shield = 15 + (layer * 10) + random.nextInt(10)
        val damage = 8 + (layer * 4) + random.nextInt(5)
        val armor = layer + random.nextInt(2)
        val bounty = 50 + (layer * 25) + random.nextInt(30)

        val asciiArt = when (name) {
            "Worm.exe" -> "  ~o~~~~~o~~\n (  o  _  o )\n  ~~o~~~~~o~"
            "Trojan.Horse" -> "  ,_____\n /_ _ _ \\\n |o|   |o|\n |_______|"
            "LogicBomb.sh" -> "   _\\|/_\n  ( o_o )\n  (_____) "
            "Spyware.dll" -> "  /-------\\\n < (o) (o) >\n  \\_  ^  _/"
            "Ransomware.crypt" -> "  [Locked]\n  [ O_O  ]\n  [=====_]"
            else -> "   /\\_/\\\n  ( >.< )\n   =(I)="
        }

        return Enemy(
            id = "enemy_${System.currentTimeMillis()}",
            name = name,
            maxIntegrity = integrity,
            integrity = integrity,
            maxShield = shield,
            shield = shield,
            damage = damage,
            armor = armor,
            iconAscii = asciiArt,
            bountyCredits = bounty,
            description = "Active security daemon blocking transmission channels. Highly hostile."
        )
    }

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
        val targetSequence = path.map { grid[it.second][it.first] }

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
