package my.playground.maze.maze

import my.playground.maze.model.Maze
import my.playground.maze.model.Tile

fun validate(maze: Maze): List<String> {
    val errors = mutableListOf<String>()
    if (maze.width !in Maze.MIN_WIDTH..Maze.MAX_WIDTH) {
        errors += "寬度必須在 ${Maze.MIN_WIDTH} 到 ${Maze.MAX_WIDTH} 之間"
    }
    if (maze.height !in Maze.MIN_HEIGHT..Maze.MAX_HEIGHT) {
        errors += "高度必須在 ${Maze.MIN_HEIGHT} 到 ${Maze.MAX_HEIGHT} 之間"
    }
    val entrances = maze.cells.count { it.type == Tile.ENTRANCE }
    val exits = maze.cells.count { it.type == Tile.EXIT }
    if (entrances != 1) errors += "必須恰好有一個入口"
    if (exits != 1) errors += "必須恰好有一個出口"
    if (!maze.borderIsClosed()) errors += "外框未封閉"
    val entrance = maze.find(Tile.ENTRANCE)
    val exit = maze.find(Tile.EXIT)
    if (entrance != null && !maze.hasOpenSide(entrance)) errors += "入口被封死"
    if (exit != null && !maze.hasOpenSide(exit)) errors += "出口被封死"
    if (entrance != null && exit != null && !reachesExit(maze, entrance, exit)) {
        errors += "入口走不到出口"
    }
    return errors
}

private fun reachesExit(maze: Maze, start: Int, goal: Int): Boolean {
    val seen = BooleanArray(maze.cells.size)
    val queue = ArrayDeque<Int>()
    seen[start] = true
    queue.add(start)
    while (queue.isNotEmpty()) {
        val i = queue.removeFirst()
        if (i == goal) return true
        for (n in openNeighbors(maze, i)) {
            if (seen[n] || maze.cells[n].type == Tile.RIVER) continue
            seen[n] = true
            queue.add(n)
        }
    }
    return false
}
