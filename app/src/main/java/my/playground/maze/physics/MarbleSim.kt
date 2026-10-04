package my.playground.maze.physics

import my.playground.maze.model.Dir
import my.playground.maze.model.Maze
import my.playground.maze.model.Tile
import kotlin.math.exp
import kotlin.math.hypot

data class Segment(val x1: Float, val y1: Float, val x2: Float, val y2: Float)

fun Maze.wallSegments(): List<Segment> {
    val list = ArrayList<Segment>()
    for (r in 0..height) {
        for (c in 0 until width) {
            val blocked = when (r) {
                0, height -> true
                else -> playBlocked(c, r, Dir.UP)
            }
            if (blocked) list += Segment(c.toFloat(), r.toFloat(), c + 1f, r.toFloat())
        }
    }
    for (c in 0..width) {
        for (r in 0 until height) {
            val blocked = when (c) {
                0, width -> true
                else -> playBlocked(c, r, Dir.LEFT)
            }
            if (blocked) list += Segment(c.toFloat(), r.toFloat(), c.toFloat(), r + 1f)
        }
    }
    return list
}

class MarbleSim(val maze: Maze) {
    val radius = 0.2f
    private val segments = maze.wallSegments()
    private val entrance = maze.find(Tile.ENTRANCE) ?: 0

    var x: Float = maze.col(entrance) + 0.5f
    var y: Float = maze.row(entrance) + 0.5f
    var vx = 0f
    var vy = 0f

    @Volatile var ax = 0f
    @Volatile var ay = 0f

    var won = false
        private set
    var riverFlash = 0
        private set

    private var accumulator = 0f

    fun reset() {
        x = maze.col(entrance) + 0.5f
        y = maze.row(entrance) + 0.5f
        vx = 0f
        vy = 0f
        won = false
        accumulator = 0f
    }

    fun step(dt: Float) {
        if (won) return
        accumulator += dt.coerceIn(0f, 0.05f)
        val fixed = 1f / 120f
        var guard = 0
        while (accumulator >= fixed && guard < 10 && !won) {
            integrate(fixed)
            accumulator -= fixed
            guard++
        }
    }

    private fun integrate(dt: Float) {
        vx += ax * dt
        vy += ay * dt
        val damp = exp(-2.4f * dt)
        vx *= damp
        vy *= damp
        val speed = hypot(vx, vy)
        val maxSpeed = 5.5f
        if (speed > maxSpeed) {
            vx *= maxSpeed / speed
            vy *= maxSpeed / speed
        }
        x += vx * dt
        y += vy * dt
        for (pass in 0 until 6) {
            if (!separate()) break
        }
        x = x.coerceIn(radius, maze.width - radius)
        y = y.coerceIn(radius, maze.height - radius)
        val c = x.toInt().coerceIn(0, maze.width - 1)
        val r = y.toInt().coerceIn(0, maze.height - 1)
        when (maze[c, r].type) {
            Tile.EXIT -> {
                vx = 0f
                vy = 0f
                won = true
            }
            Tile.RIVER -> {
                riverFlash += 1
                x = maze.col(entrance) + 0.5f
                y = maze.row(entrance) + 0.5f
                vx = 0f
                vy = 0f
            }
            else -> Unit
        }
    }

    private fun separate(): Boolean {
        var hit = false
        for (segment in segments) {
            val (cx, cy) = closest(segment, x, y)
            var dx = x - cx
            var dy = y - cy
            var dist = hypot(dx, dy)
            if (dist < 1e-4f) {
                val nx = -(segment.y2 - segment.y1)
                val ny = segment.x2 - segment.x1
                val length = hypot(nx, ny).coerceAtLeast(1e-4f)
                dx = nx / length
                dy = ny / length
                dist = 0f
            } else {
                dx /= dist
                dy /= dist
            }
            if (dist >= radius) continue
            val push = radius - dist + 0.0015f
            x += dx * push
            y += dy * push
            val vn = vx * dx + vy * dy
            if (vn < 0f) {
                val restitution = if (vn < -0.7f) 0.55f else 0f
                vx -= (1f + restitution) * vn * dx
                vy -= (1f + restitution) * vn * dy
                if (restitution > 0f) {
                    val tx = -dy
                    val ty = dx
                    val vt = vx * tx + vy * ty
                    vx -= vt * 0.35f * tx
                    vy -= vt * 0.35f * ty
                }
            }
            hit = true
        }
        return hit
    }

    private fun closest(segment: Segment, px: Float, py: Float): Pair<Float, Float> {
        val dx = segment.x2 - segment.x1
        val dy = segment.y2 - segment.y1
        val len2 = dx * dx + dy * dy
        val t = if (len2 == 0f) 0f else (((px - segment.x1) * dx + (py - segment.y1) * dy) / len2).coerceIn(0f, 1f)
        return segment.x1 + t * dx to segment.y1 + t * dy
    }
}
