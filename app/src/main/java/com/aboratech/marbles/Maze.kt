package com.aboratech.marbles

/**
 * '#' = wall, '.' = open floor, 'S' = start, 'G' = goal, 'H' = hole
 * (falls back to start on contact). All rows must be the same length.
 * A serpentine single-path layout, guaranteed solvable by construction.
 */
class Maze(private val grid: List<String>) {
    val rows = grid.size
    val cols = grid[0].length

    init {
        require(grid.all { it.length == cols }) { "All maze rows must have equal length" }
    }

    fun isWall(row: Int, col: Int): Boolean {
        if (row < 0 || row >= rows || col < 0 || col >= cols) return true
        return grid[row][col] == '#'
    }

    fun forEachWallCell(action: (row: Int, col: Int) -> Unit) = forEachCell('#', action)

    fun forEachHoleCell(action: (row: Int, col: Int) -> Unit) = forEachCell('H', action)

    private fun forEachCell(target: Char, action: (row: Int, col: Int) -> Unit) {
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                if (grid[r][c] == target) action(r, c)
            }
        }
    }

    fun findCell(target: Char): Pair<Int, Int> {
        for (r in 0 until rows) {
            val c = grid[r].indexOf(target)
            if (c >= 0) return r to c
        }
        error("Maze has no '$target' cell")
    }

    fun startCell() = findCell('S')
    fun goalCell() = findCell('G')

    companion object {
        val DEFAULT = Maze(
            listOf(
                "#########",
                "#S......#",
                "#######.#",
                "#......H#",
                "#.#######",
                "#H......#",
                "#######.#",
                "#......H#",
                "#.#######",
                "#H.....H#",
                "#######.#",
                "#......H#",
                "#.#######",
                "#......G#",
                "#########",
            )
        )
    }
}
