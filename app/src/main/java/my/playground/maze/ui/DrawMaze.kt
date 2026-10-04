package my.playground.maze.ui

import android.graphics.Paint
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import my.playground.maze.model.Dir
import my.playground.maze.model.Maze
import my.playground.maze.model.Tile
import kotlin.math.min

enum class Look { STONE, SKETCH, PIXEL, EDITOR }

fun fitBoard(viewWidth: Float, viewHeight: Float, columns: Int, rows: Int, padding: Float = 24f): Rect {
    val availW = (viewWidth - padding * 2).coerceAtLeast(1f)
    val availH = (viewHeight - padding * 2).coerceAtLeast(1f)
    val cell = min(availW / columns, availH / rows)
    val boardW = cell * columns
    val boardH = cell * rows
    val left = (viewWidth - boardW) / 2f
    val top = (viewHeight - boardH) / 2f
    return Rect(left, top, left + boardW, top + boardH)
}

fun DrawScope.drawMazeScene(
    maze: Maze,
    look: Look,
    dest: Rect,
    ballX: Float? = null,
    ballY: Float? = null,
    ballRadiusCells: Float = 0.2f,
    solution: List<Int>? = null,
) {
    val cellW = dest.width / maze.width
    val cellH = dest.height / maze.height
    val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = min(cellW, cellH) * 0.38f
        color = if (look == Look.SKETCH) 0xFF2A2A2A.toInt() else 0xFF2B241C.toInt()
    }
    for (r in 0 until maze.height) {
        for (c in 0 until maze.width) {
            val cell = maze[c, r]
            val rect = Rect(
                dest.left + c * cellW,
                dest.top + r * cellH,
                dest.left + (c + 1) * cellW,
                dest.top + (r + 1) * cellH,
            )
            drawRect(floorColor(look, cell.type, c, r), rect.topLeft, rect.size)
            if (look == Look.EDITOR) {
                val mark = when (cell.type) {
                    Tile.ENTRANCE -> "入"
                    Tile.EXIT -> "出"
                    Tile.RIVER -> "河"
                    Tile.ROAD -> null
                }
                if (mark != null) drawCellMark(mark, rect.center, label)
            }
        }
    }
    val wallWidth = min(cellW, cellH) * if (look == Look.PIXEL) 0.16f else 0.1f
    val openWidth = min(cellW, cellH) * 0.035f
    for (r in 0..maze.height) {
        for (c in 0 until maze.width) {
            val blocked = if (look == Look.EDITOR) {
                if (r == 0) maze[c, 0].blocked(Dir.UP)
                else if (r == maze.height) maze[c, maze.height - 1].blocked(Dir.DOWN)
                else maze.editorBlocked(c, r, Dir.UP)
            } else {
                r == 0 || r == maze.height || maze.playBlocked(c, r, Dir.UP)
            }
            wall(
                Offset(dest.left + c * cellW, dest.top + r * cellH),
                Offset(dest.left + (c + 1) * cellW, dest.top + r * cellH),
                blocked,
                look,
                wallWidth,
                openWidth,
                c * 17 + r,
            )
        }
    }
    for (c in 0..maze.width) {
        for (r in 0 until maze.height) {
            val blocked = if (look == Look.EDITOR) {
                if (c == 0) maze[0, r].blocked(Dir.LEFT)
                else if (c == maze.width) maze[maze.width - 1, r].blocked(Dir.RIGHT)
                else maze.editorBlocked(c, r, Dir.LEFT)
            } else {
                c == 0 || c == maze.width || maze.playBlocked(c, r, Dir.LEFT)
            }
            wall(
                Offset(dest.left + c * cellW, dest.top + r * cellH),
                Offset(dest.left + c * cellW, dest.top + (r + 1) * cellH),
                blocked,
                look,
                wallWidth,
                openWidth,
                c * 31 + r * 3,
            )
        }
    }
    if (solution != null) drawSolutionPath(maze, dest, solution, ballX, ballY)
    if (look != Look.EDITOR) {
        val exit = maze.find(Tile.EXIT)
        if (exit != null) {
            val mark = Paint(label).apply { color = 0xFF2B241C.toInt() }
            val center = Offset(
                dest.left + (maze.col(exit) + 0.5f) * cellW,
                dest.top + (maze.row(exit) + 0.5f) * cellH,
            )
            drawCellMark("出", center, mark)
        }
    }
    if (ballX != null && ballY != null) {
        val center = Offset(dest.left + ballX * cellW, dest.top + ballY * cellH)
        val radius = ballRadiusCells * min(cellW, cellH)
        drawPoop(center, radius)
    }
}

fun DrawScope.drawSolutionPath(
    maze: Maze,
    dest: Rect,
    path: List<Int>,
    fromX: Float? = null,
    fromY: Float? = null,
) {
    val cellW = dest.width / maze.width
    val cellH = dest.height / maze.height
    val ahead = if (fromX != null && fromY != null) path.drop(1) else path
    val points = buildList {
        if (fromX != null && fromY != null) add(Offset(dest.left + fromX * cellW, dest.top + fromY * cellH))
        ahead.forEach { index ->
            add(Offset(dest.left + (maze.col(index) + 0.5f) * cellW, dest.top + (maze.row(index) + 0.5f) * cellH))
        }
    }
    if (points.size < 2) return
    val width = min(cellW, cellH) * 0.1f
    for (i in 0 until points.lastIndex) {
        drawLine(Color(0x99FFF6D0), points[i], points[i + 1], width, cap = StrokeCap.Round)
    }
}

private fun DrawScope.drawCellMark(text: String, center: Offset, paint: Paint) {
    drawContext.canvas.nativeCanvas.drawText(text, center.x, center.y + paint.textSize * 0.35f, paint)
}

private fun DrawScope.drawPoop(center: Offset, radius: Float) {
    val brown = Color(0xFF6B3F22)
    val dark = Color(0xFF4A2A14)
    drawCircle(Color(0x55000000), radius * 0.9f, center + Offset(radius * 0.12f, radius * 0.28f))
    drawCircle(brown, radius * 0.72f, center + Offset(0f, radius * 0.28f))
    drawCircle(brown, radius * 0.58f, center + Offset(-radius * 0.28f, radius * 0.02f))
    drawCircle(brown, radius * 0.5f, center + Offset(radius * 0.26f, -radius * 0.08f))
    drawCircle(brown, radius * 0.28f, center + Offset(radius * 0.02f, -radius * 0.48f))
    drawCircle(dark, radius * 0.1f, center + Offset(radius * 0.02f, -radius * 0.62f))
    val leftEye = center + Offset(-radius * 0.18f, radius * 0.12f)
    val rightEye = center + Offset(radius * 0.2f, radius * 0.06f)
    drawCircle(Color.White, radius * 0.16f, leftEye)
    drawCircle(Color.White, radius * 0.16f, rightEye)
    drawCircle(Color(0xFF2A2118), radius * 0.08f, leftEye + Offset(radius * 0.03f, 0f))
    drawCircle(Color(0xFF2A2118), radius * 0.08f, rightEye + Offset(radius * 0.03f, 0f))
}

private fun DrawScope.wall(
    start: Offset,
    end: Offset,
    blocked: Boolean,
    look: Look,
    wallWidth: Float,
    openWidth: Float,
    seed: Int,
) {
    if (!blocked && look != Look.EDITOR) return
    val color = if (blocked) wallColor(look) else Color(0xFFD9D3C7)
    val width = if (blocked) wallWidth else openWidth
    val cap = if (look == Look.PIXEL) StrokeCap.Square else StrokeCap.Round
    if (look == Look.SKETCH && blocked) {
        val jx = ((seed and 7) - 3) * 0.6f
        val jy = (((seed shr 3) and 7) - 3) * 0.6f
        drawLine(color, start + Offset(jx, jy), end + Offset(-jx, -jy), width, cap = cap)
        drawLine(color.copy(alpha = 0.45f), start + Offset(-jy, jx), end + Offset(jy * 0.5f, -jx), width * 0.55f, cap = cap)
    } else {
        drawLine(color, start, end, width, cap = cap)
    }
}

private fun floorColor(look: Look, type: Tile, c: Int, r: Int): Color {
    if (look == Look.EDITOR) {
        return when (type) {
            Tile.ROAD -> Color(0xFFF4F1EA)
            Tile.ENTRANCE -> Color(0xFF9CCC65)
            Tile.EXIT -> Color(0xFFFFD54F)
            Tile.RIVER -> Color(0xFF4FC3F7)
        }
    }
    val checker = (c + r) % 2 == 0
    val base = when (look) {
        Look.STONE -> if (checker) Color(0xFFD7CBB8) else Color(0xFFCBBFAE)
        Look.SKETCH -> if (checker) Color(0xFFF7F3E8) else Color(0xFFF3EEE3)
        Look.PIXEL -> if (checker) Color(0xFF3E6B4F) else Color(0xFF4E7B5A)
        Look.EDITOR -> Color(0xFFF4F1EA)
    }
    return when (type) {
        Tile.ENTRANCE -> if (look == Look.PIXEL) Color(0xFF6AB04C) else Color(0xFFC5D6B0)
        Tile.EXIT -> if (look == Look.PIXEL) Color(0xFFF9CA24) else Color(0xFFE6C07B)
        Tile.RIVER -> if (look == Look.PIXEL) Color(0xFF22A6B3) else Color(0xFF7EB6C9)
        Tile.ROAD -> base
    }
}

private fun wallColor(look: Look): Color = when (look) {
    Look.STONE -> Color(0xFF3E342C)
    Look.SKETCH -> Color(0xFF222222)
    Look.PIXEL -> Color(0xFF161616)
    Look.EDITOR -> Color(0xFF4E342E)
}

