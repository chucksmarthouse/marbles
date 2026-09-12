package com.aboratech.marbles

/**
 * '#' = wall, '.' = open floor, 'S' = start, 'G' = goal. All rows must
 * be the same length. A serpentine single-path layout, guaranteed
 * solvable by construction. Holes are defined separately in
 * [GameView] as grid-vertex coordinates, not as grid characters here,
 * since they sit on wall corners/edges rather than at cell centers.
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

    fun forEachWallCell(action: (row: Int, col: Int) -> Unit) {
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                if (grid[r][c] == '#') action(r, c)
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
                "#.......#",
                "#.#######",
                "#.......#",
                "#######.#",
                "#.......#",
                "#.#######",
                "#.......#",
                "#######.#",
                "#.......#",
                "#.#######",
                "#......G#",
                "#########",
            )
        )
    }
}
