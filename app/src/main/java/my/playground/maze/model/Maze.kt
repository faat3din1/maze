package my.playground.maze.model

enum class Tile(val code: Int) {
    ROAD(0),
    ENTRANCE(1),
    EXIT(2),
    RIVER(4);

    companion object {
        fun fromCode(code: Int): Tile? = entries.firstOrNull { it.code == code }
    }
}

enum class Dir(val dx: Int, val dy: Int, val mask: Int) {
    UP(0, -1, 0b1000),
    RIGHT(1, 0, 0b0100),
    DOWN(0, 1, 0b0010),
    LEFT(-1, 0, 0b0001);

    fun opposite(): Dir = when (this) {
        UP -> DOWN
        RIGHT -> LEFT
        DOWN -> UP
        LEFT -> RIGHT
    }
}

enum class Algorithm { DFS, KRUSKAL, PRIM }

enum class Difficulty { EASY, NORMAL, HARD, CUSTOM }

/**
 * One cell is one byte: bits 7–5 type, bit 4 reserved 0, bits 3–0 walls in order 上右下左.
 * 1 means blocked.
 */
data class Cell(val type: Tile = Tile.ROAD, val walls: Int = 0) {
    fun blocked(dir: Dir): Boolean = (walls and dir.mask) != 0

    fun withWall(dir: Dir, blocked: Boolean): Cell {
        val next = if (blocked) walls or dir.mask else walls and dir.mask.inv()
        return copy(walls = next and 0b1111)
    }

    fun encodeByte(): Int = (type.code shl 5) or (walls and 0b1111)

    companion object {
        fun decodeByte(byte: Int): Cell? {
            if ((byte and 0b0001_0000) != 0) return null
            val type = Tile.fromCode((byte ushr 5) and 0b111) ?: return null
            return Cell(type, byte and 0b1111)
        }
    }
}

data class Maze(
    val width: Int,
    val height: Int,
    val cells: List<Cell>,
) {
    init {
        require(cells.size == width * height)
    }

    operator fun get(c: Int, r: Int): Cell = cells[r * width + c]

    fun index(c: Int, r: Int): Int = r * width + c

    fun col(index: Int): Int = index % width

    fun row(index: Int): Int = index / width

    fun find(type: Tile): Int? = cells.indexOfFirst { it.type == type }.takeIf { it >= 0 }

    fun editorBlocked(c: Int, r: Int, dir: Dir): Boolean {
        val own = this[c, r].blocked(dir)
        val nc = c + dir.dx
        val nr = r + dir.dy
        if (nc !in 0 until width || nr !in 0 until height) return own
        return own || this[nc, nr].blocked(dir.opposite())
    }

    /** Outer border is always blocked, even when the stored bit is 0. */
    fun playBlocked(c: Int, r: Int, dir: Dir): Boolean {
        val nc = c + dir.dx
        val nr = r + dir.dy
        if (nc !in 0 until width || nr !in 0 until height) return true
        return this[c, r].blocked(dir) || this[nc, nr].blocked(dir.opposite())
    }

    fun hasOpenSide(index: Int): Boolean {
        val c = col(index)
        val r = row(index)
        return Dir.entries.any { dir ->
            val nc = c + dir.dx
            val nr = r + dir.dy
            nc in 0 until width && nr in 0 until height && !playBlocked(c, r, dir)
        }
    }

    fun borderIsClosed(): Boolean {
        for (c in 0 until width) {
            if (!this[c, 0].blocked(Dir.UP)) return false
            if (!this[c, height - 1].blocked(Dir.DOWN)) return false
        }
        for (r in 0 until height) {
            if (!this[0, r].blocked(Dir.LEFT)) return false
            if (!this[width - 1, r].blocked(Dir.RIGHT)) return false
        }
        return true
    }

    fun withCell(index: Int, cell: Cell): Maze {
        val next = cells.toMutableList()
        next[index] = cell
        return copy(cells = next)
    }

    fun paintType(index: Int, type: Tile): Maze {
        val cleared = if (type == Tile.ENTRANCE || type == Tile.EXIT) {
            cells.map { if (it.type == type) it.copy(type = Tile.ROAD) else it }
        } else {
            cells
        }
        val next = cleared.toMutableList()
        next[index] = next[index].copy(type = type)
        return copy(cells = next)
    }

    fun setEdge(c: Int, r: Int, dir: Dir, blocked: Boolean): Maze {
        val next = cells.toMutableList()
        val i = index(c, r)
        next[i] = next[i].withWall(dir, blocked)
        val nc = c + dir.dx
        val nr = r + dir.dy
        if (nc in 0 until width && nr in 0 until height) {
            val j = index(nc, nr)
            next[j] = next[j].withWall(dir.opposite(), blocked)
        }
        return copy(cells = next)
    }

    fun toggleEdge(c: Int, r: Int, dir: Dir): Maze = setEdge(c, r, dir, !editorBlocked(c, r, dir))

    fun sealBorder(): Maze {
        var maze = this
        for (c in 0 until width) {
            maze = maze.setEdge(c, 0, Dir.UP, true)
            maze = maze.setEdge(c, height - 1, Dir.DOWN, true)
        }
        for (r in 0 until height) {
            maze = maze.setEdge(0, r, Dir.LEFT, true)
            maze = maze.setEdge(width - 1, r, Dir.RIGHT, true)
        }
        return maze
    }

    companion object {
        const val MIN_WIDTH = 4
        const val MAX_WIDTH = 20
        const val MIN_HEIGHT = 4
        const val MAX_HEIGHT = 40

        fun blank(width: Int, height: Int): Maze {
            return Maze(width, height, List(width * height) { Cell() })
        }
    }
}
