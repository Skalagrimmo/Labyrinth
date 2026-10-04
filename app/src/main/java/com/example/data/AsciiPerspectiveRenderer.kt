package com.example.data

import kotlin.math.abs

/**
 * Pure ASCII first-person renderer extracted from GameEngine.
 * It receives scene state and returns a frame without mutating gameplay state.
 */
object AsciiPerspectiveRenderer {
    fun render(
            grid: Array<Array<CellType>>,
            px: Int,
            py: Int,
            dir: Direction,
            activeWeather: CyberWeather = CyberWeather.CLEAR
        ): String {
            val canvas = PerspectiveCharCanvas(11, 31)
    
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
            if (grid.isEmpty() || grid[0].isEmpty()) {
                return ""
            }
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
            var maxVisibleDepth = if (activeWeather == CyberWeather.DATA_STORM) 1 else 3
            if (activeWeather != CyberWeather.DATA_STORM) {
                for (d in 1..3) {
                    if (cellTypes[d] == CellType.WALL) {
                        maxVisibleDepth = d
                        break
                    }
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
            } else if (primaryNode == CellType.SECRET_CACHE) {
                // Quantum Crypt-Cache floating cube icon
                canvas.set(3, 13, '╭')
                for (c in 14..16) canvas.set(3, c, '─')
                canvas.set(3, 17, '╮')
                
                canvas.set(4, 12, '│')
                canvas.set(4, 14, 'S')
                canvas.set(4, 15, 'E')
                canvas.set(4, 16, 'C')
                canvas.set(4, 18, '│')
                
                canvas.set(5, 13, '╰')
                for (c in 14..16) canvas.set(5, c, '─')
                canvas.set(5, 17, '╯')
                
                for (c in 12..18) canvas.set(6, c, '░')
            } else if (primaryNode == CellType.GRAND_HALL) {
                // Monumental pillars on the left and right sides
                for (r in 2..8) {
                    canvas.set(r, 9, '┃')
                    canvas.set(r, 10, '█')
                    canvas.set(r, 20, '█')
                    canvas.set(r, 21, '┃')
                }
                canvas.set(1, 9, '╔')
                canvas.set(1, 10, '╤')
                canvas.set(9, 9, '╚')
                canvas.set(9, 10, '╧')
                canvas.set(1, 20, '╤')
                canvas.set(1, 21, '╗')
                canvas.set(9, 20, '╧')
                canvas.set(9, 21, '╝')
    
                val label = "GRAND HALL"
                val colStart = 15 - label.length / 2
                for (i in label.indices) {
                    canvas.set(5, colStart + i, label[i])
                }
            } else if (primaryNode == CellType.DOME_CHAMBER) {
                // Curved arched rib lines of high-tech dome ceiling
                canvas.drawLine(1, 6, 3, 15, '╭')
                canvas.drawLine(1, 24, 3, 15, '╮')
                canvas.set(3, 15, '◎')
                canvas.drawLine(9, 6, 7, 15, '╰')
                canvas.drawLine(9, 24, 7, 15, '╯')
    
                val label = "DOME VAULT"
                val colStart = 15 - label.length / 2
                for (i in label.indices) {
                    canvas.set(5, colStart + i, label[i])
                }
            } else if (primaryNode == CellType.VENT_TUNNEL) {
                // Low-ceiling vent tunnel structure
                for (c in 6..24) {
                    canvas.set(2, c, '▄')
                    canvas.set(3, c, '█')
                }
                val label = "TUNNEL CONDUIT"
                val colStart = 15 - label.length / 2
                for (i in label.indices) {
                    canvas.set(5, colStart + i, label[i])
                }
            } else if (primaryNode == CellType.ELEVATED_BALCONY) {
                // Raised balcony handrails
                for (c in 7..23) {
                    canvas.set(6, c, '╦')
                    canvas.set(7, c, '║')
                    if (c % 2 == 0) canvas.set(8, c, '▒')
                }
                val label = "BALCONY LEDGE"
                val colStart = 15 - label.length / 2
                for (i in label.indices) {
                    canvas.set(4, colStart + i, label[i])
                }
            } else if (primaryNode == CellType.STAIRS_UP) {
                // Upward steps wireframe
                canvas.set(4, 11, '╭')
                for (c in 12..18) canvas.set(4, c, '─')
                canvas.set(4, 19, '╮')
                canvas.set(5, 10, '┌')
                for (c in 11..19) canvas.set(5, c, '─')
                canvas.set(5, 20, '┐')
                canvas.set(6, 9, '┌')
                for (c in 10..20) canvas.set(6, c, '─')
                canvas.set(6, 21, '┐')
                canvas.set(7, 8, '┌')
                for (c in 9..21) canvas.set(7, c, '─')
                canvas.set(7, 22, '┐')
    
                val label = "STAIRS UP"
                val colStart = 15 - label.length / 2
                for (i in label.indices) {
                    canvas.set(2, colStart + i, label[i])
                }
            } else if (primaryNode == CellType.STAIRS_DOWN) {
                // Downward descending steps wireframe
                canvas.set(8, 11, '╰')
                for (c in 12..18) canvas.set(8, c, '─')
                canvas.set(8, 19, '╯')
                canvas.set(7, 10, '└')
                for (c in 11..19) canvas.set(7, c, '─')
                canvas.set(7, 20, '┘')
                canvas.set(6, 9, '└')
                for (c in 10..20) canvas.set(6, c, '─')
                canvas.set(6, 21, '┘')
    
                val label = "STAIRS DOWN"
                val colStart = 15 - label.length / 2
                for (i in label.indices) {
                    canvas.set(4, colStart + i, label[i])
                }
            } else if (primaryNode == CellType.GRAVITY_SLOPE) {
                // Slanting slope lines
                canvas.drawLine(8, 10, 4, 20, '/')
                canvas.drawLine(9, 11, 5, 21, '/')
                canvas.drawLine(7, 9, 3, 19, '/')
    
                val label = "GRAVITY SLOPE"
                val colStart = 15 - label.length / 2
                for (i in label.indices) {
                    canvas.set(5, colStart + i, label[i])
                }
            }
    
            // --- 5. Draw Outer Border Frame ---
            drawBox(0, 0, 10, 30)
    
            return canvas.render()
        }
}

private class PerspectiveCharCanvas(val rows: Int, val cols: Int) {
    private val buffer = Array(rows) { CharArray(cols) { ' ' } }

    fun set(r: Int, c: Int, ch: Char) {
        if (r in 0 until rows && c in 0 until cols) buffer[r][c] = ch
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
            if (e2 > -dc) { err -= dc; r += sr }
            if (e2 < dr) { err += dr; c += sc }
        }
    }

    fun render(): String = buffer.joinToString("\n") { String(it) }
}
