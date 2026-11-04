package com.example.data

import kotlin.random.Random

/** Pure generator for the breach-protocol style hacking puzzle. */
object HackingPuzzleGenerator {
    fun generate(difficulty: Int, random: Random = Random.Default): HackingPuzzle {
            
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
