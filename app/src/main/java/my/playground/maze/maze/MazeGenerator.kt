package my.playground.maze.maze

import my.playground.maze.model.Algorithm
import my.playground.maze.model.Cell
import my.playground.maze.model.Dir
import my.playground.maze.model.Maze
import my.playground.maze.model.Tile
import kotlin.random.Random

fun generateMaze(
    algorithm: Algorithm,
    width: Int,
    height: Int,
    rivers: Int,
    loopFraction: Float,
    random: Random = Random.Default,
): Maze {
    val grid = WallGrid(width, height)
    when (algorithm) {
        Algorithm.DFS -> grid.carveDfs(random)
        Algorithm.KRUSKAL -> grid.carveKruskal(random)
        Algorithm.PRIM -> grid.carvePrim(random)
    }
    if (loopFraction > 0f) grid.openLoops(loopFraction, random)

    var maze = grid.toMaze()
    val exit = farthestCell(maze, 0)
    maze = maze.withCell(0, maze.cells[0].copy(type = Tile.ENTRANCE))
    maze = maze.withCell(exit, maze.cells[exit].copy(type = Tile.EXIT))
    maze = placeRivers(maze, rivers, random)
    return maze
}

private class WallGrid(val width: Int, val height: Int) {
    val up = Array(height) { BooleanArray(width) { true } }
    val right = Array(height) { BooleanArray(width) { true } }
    val down = Array(height) { BooleanArray(width) { true } }
    val left = Array(height) { BooleanArray(width) { true } }

    fun open(c: Int, r: Int, dir: Dir) {
        val nc = c + dir.dx
        val nr = r + dir.dy
        when (dir) {
            Dir.UP -> {
                up[r][c] = false
                down[nr][nc] = false
            }
            Dir.RIGHT -> {
                right[r][c] = false
                left[nr][nc] = false
            }
            Dir.DOWN -> {
                down[r][c] = false
                up[nr][nc] = false
            }
            Dir.LEFT -> {
                left[r][c] = false
                right[nr][nc] = false
            }
        }
    }

    fun carveDfs(random: Random) {
        val seen = Array(height) { BooleanArray(width) }
        val stack = ArrayDeque<Pair<Int, Int>>()
        seen[0][0] = true
        stack.add(0 to 0)
        while (stack.isNotEmpty()) {
            val (c, r) = stack.last()
            val next = Dir.entries.filter { dir ->
                val nc = c + dir.dx
                val nr = r + dir.dy
                nc in 0 until width && nr in 0 until height && !seen[nr][nc]
            }
            if (next.isEmpty()) {
                stack.removeLast()
            } else {
                val dir = next[random.nextInt(next.size)]
                val nc = c + dir.dx
                val nr = r + dir.dy
                open(c, r, dir)
                seen[nr][nc] = true
                stack.add(nc to nr)
            }
        }
    }

    fun carveKruskal(random: Random) {
        val parents = IntArray(width * height) { it }
        fun find(i: Int): Int {
            var x = i
            while (parents[x] != x) {
                parents[x] = parents[parents[x]]
                x = parents[x]
            }
            return x
        }
        val edges = mutableListOf<Triple<Int, Int, Dir>>()
        for (r in 0 until height) {
            for (c in 0 until width) {
                if (c + 1 < width) edges += Triple(c, r, Dir.RIGHT)
                if (r + 1 < height) edges += Triple(c, r, Dir.DOWN)
            }
        }
        edges.shuffle(random)
        for ((c, r, dir) in edges) {
            val a = r * width + c
            val b = (r + dir.dy) * width + (c + dir.dx)
            val pa = find(a)
            val pb = find(b)
            if (pa != pb) {
                parents[pa] = pb
                open(c, r, dir)
            }
        }
    }

    fun carvePrim(random: Random) {
        val inside = Array(height) { BooleanArray(width) }
        val frontier = mutableListOf<Triple<Int, Int, Dir>>()
        fun grow(c: Int, r: Int) {
            inside[r][c] = true
            for (dir in Dir.entries) {
                val nc = c + dir.dx
                val nr = r + dir.dy
                if (nc in 0 until width && nr in 0 until height && !inside[nr][nc]) {
                    frontier += Triple(c, r, dir)
                }
            }
        }
        grow(0, 0)
        while (frontier.isNotEmpty()) {
            val pick = frontier.removeAt(random.nextInt(frontier.size))
            val (c, r, dir) = pick
            val nc = c + dir.dx
            val nr = r + dir.dy
            if (nc !in 0 until width || nr !in 0 until height || inside[nr][nc]) continue
            open(c, r, dir)
            grow(nc, nr)
        }
    }

    fun openLoops(fraction: Float, random: Random) {
        val closed = mutableListOf<Triple<Int, Int, Dir>>()
        for (r in 0 until height) {
            for (c in 0 until width) {
                if (c + 1 < width && right[r][c]) closed += Triple(c, r, Dir.RIGHT)
                if (r + 1 < height && down[r][c]) closed += Triple(c, r, Dir.DOWN)
            }
        }
        closed.shuffle(random)
        val count = (closed.size * fraction).toInt()
        for (i in 0 until count) {
            val (c, r, dir) = closed[i]
            open(c, r, dir)
        }
    }

    fun toMaze(): Maze {
        val cells = List(width * height) { index ->
            val c = index % width
            val r = index / width
            var walls = 0
            if (up[r][c]) walls = walls or Dir.UP.mask
            if (right[r][c]) walls = walls or Dir.RIGHT.mask
            if (down[r][c]) walls = walls or Dir.DOWN.mask
            if (left[r][c]) walls = walls or Dir.LEFT.mask
            Cell(Tile.ROAD, walls)
        }
        return Maze(width, height, cells)
    }
}

private fun farthestCell(maze: Maze, start: Int): Int {
    val dist = IntArray(maze.cells.size) { -1 }
    val queue = ArrayDeque<Int>()
    dist[start] = 0
    queue.add(start)
    while (queue.isNotEmpty()) {
        val i = queue.removeFirst()
        for (n in openNeighbors(maze, i)) {
            if (dist[n] >= 0) continue
            dist[n] = dist[i] + 1
            queue.add(n)
        }
    }
    var best = start
    for (i in dist.indices) {
        if (dist[i] > dist[best]) best = i
    }
    return best
}

internal fun openNeighbors(maze: Maze, index: Int): List<Int> {
    val c = maze.col(index)
    val r = maze.row(index)
    return Dir.entries.mapNotNull { dir ->
        val nc = c + dir.dx
        val nr = r + dir.dy
        if (nc !in 0 until maze.width || nr !in 0 until maze.height) return@mapNotNull null
        if (maze.playBlocked(c, r, dir)) null else maze.index(nc, nr)
    }
}

private fun placeRivers(maze: Maze, rivers: Int, random: Random): Maze {
    if (rivers <= 0) return maze
    val path = solutionPath(maze)?.toSet() ?: return maze
    val spots = maze.cells.indices.filter { it !in path }
    if (spots.isEmpty()) return maze
    var result = maze
    for (index in spots.shuffled(random).take(rivers.coerceAtMost(spots.size))) {
        result = result.withOpenRiver(index)
    }
    return result
}

private fun Maze.withOpenRiver(index: Int): Maze {
    val c = col(index)
    val r = row(index)
    var next = this
    var walls = cells[index].walls
    for (dir in Dir.entries) {
        val nc = c + dir.dx
        val nr = r + dir.dy
        if (nc !in 0 until width || nr !in 0 until height) continue
        walls = walls and dir.mask.inv()
        val neighbor = index(nc, nr)
        next = next.withCell(neighbor, next.cells[neighbor].withWall(dir.opposite(), blocked = false))
    }
    return next.withCell(index, Cell(Tile.RIVER, walls and 0b1111))
}

fun solutionPath(maze: Maze, start: Int = -1): List<Int>? {
    val origin = if (start >= 0) start else maze.find(Tile.ENTRANCE) ?: return null
    if (origin !in maze.cells.indices) return null
    val goal = maze.find(Tile.EXIT) ?: return null
    if (origin == goal) return listOf(goal)
    val prev = IntArray(maze.cells.size) { -1 }
    val queue = ArrayDeque<Int>()
    prev[origin] = origin
    queue.add(origin)
    while (queue.isNotEmpty()) {
        val i = queue.removeFirst()
        if (i == goal) break
        for (n in openNeighbors(maze, i)) {
            if (prev[n] >= 0 || maze.cells[n].type == Tile.RIVER) continue
            prev[n] = i
            queue.add(n)
        }
    }
    if (prev[goal] < 0) return null
    val path = ArrayDeque<Int>()
    var cursor = goal
    while (cursor != origin) {
        path.addFirst(cursor)
        cursor = prev[cursor]
    }
    path.addFirst(origin)
    return path.toList()
}
