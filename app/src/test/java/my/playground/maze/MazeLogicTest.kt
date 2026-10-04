package my.playground.maze

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import my.playground.maze.codec.DecodeOutcome
import my.playground.maze.codec.MazeCodec
import my.playground.maze.maze.generateMaze
import my.playground.maze.maze.validate
import my.playground.maze.model.Algorithm
import my.playground.maze.model.Dir
import my.playground.maze.model.Cell
import my.playground.maze.model.Maze
import my.playground.maze.model.Tile
import my.playground.maze.physics.MarbleSim
import my.playground.maze.ui.readQrPixels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class MazeLogicTest {
    @Test
    fun cellBytesMatchThePlanExamples() {
        assertEquals(0b0010_0111, Cell(Tile.ENTRANCE, 0b0111).encodeByte())
        assertEquals(0b0100_1011, Cell(Tile.EXIT, 0b1011).encodeByte())
        assertEquals(0b1000_0000, Cell(Tile.RIVER, 0).encodeByte())
        assertEquals(0b0000_0001, Cell(Tile.ROAD, 0b0001).encodeByte())
        assertNull(Cell.decodeByte(0b0011_0000))
        assertNull(Cell.decodeByte(0b0110_0000))
    }

    @Test
    fun generatedMazesPassValidation() {
        val random = Random(2)
        for (algorithm in Algorithm.entries) {
            for ((width, height) in listOf(4 to 4, 8 to 6, 20 to 40)) {
                val maze = generateMaze(algorithm, width, height, rivers = 2, loopFraction = 0.1f, random = random)
                val errors = validate(maze)
                assertTrue("${algorithm.name} ${width}x$height: ${errors.joinToString()}", errors.isEmpty())
            }
        }
    }

    @Test
    fun codecRoundTripAndCrc() {
        val maze = generateMaze(Algorithm.DFS, 6, 6, rivers = 1, loopFraction = 0f, random = Random(4))
        val bytes = MazeCodec.encode(maze)
        val decoded = MazeCodec.decode(bytes)
        assertTrue(decoded is DecodeOutcome.Success)
        assertEquals(maze, (decoded as DecodeOutcome.Success).maze)
        bytes[8] = (bytes[8].toInt() xor 0xFF).toByte()
        assertTrue(MazeCodec.decode(bytes) is DecodeOutcome.Failure)
    }

    @Test
    fun qrPixelsRoundTrip() {
        val maze = generateMaze(Algorithm.PRIM, 8, 6, rivers = 1, loopFraction = 0f, random = Random(7))
        val hints = mapOf(EncodeHintType.CHARACTER_SET to "ISO-8859-1", EncodeHintType.MARGIN to 1)
        val size = 360
        val matrix = QRCodeWriter().encode(MazeCodec.qrText(maze), BarcodeFormat.QR_CODE, size, size, hints)
        val pixels = IntArray(size * size) { index ->
            if (matrix.get(index % size, index / size)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
        }
        val decoded = MazeCodec.decodeQrText(readQrPixels(pixels, size, size)!!)
        assertTrue(decoded is DecodeOutcome.Success)
        assertEquals(maze, (decoded as DecodeOutcome.Success).maze)
    }

    @Test
    fun generatedRiversHaveNoInternalWalls() {
        val maze = generateMaze(Algorithm.PRIM, 12, 8, rivers = 6, loopFraction = 0f, random = Random(9))
        val rivers = maze.cells.indices.filter { maze.cells[it].type == Tile.RIVER }
        assertEquals(6, rivers.size)
        for (index in rivers) {
            val c = maze.col(index)
            val r = maze.row(index)
            for (dir in Dir.entries) {
                val nc = c + dir.dx
                val nr = r + dir.dy
                if (nc !in 0 until maze.width || nr !in 0 until maze.height) continue
                assertFalse(maze.playBlocked(c, r, dir))
            }
        }
        assertTrue(validate(maze).isEmpty())
    }

    @Test
    fun shareTextDropsWhitespace() {
        val spaced = "  abc\n def \r\n"
        assertEquals("abcdef", MazeCodec.normalizeShareText(spaced))
        assertEquals("", MazeCodec.normalizeShareText(" \n\t"))
    }

    @Test
    fun sealedEntranceIsRejected() {
        val maze = generateMaze(Algorithm.KRUSKAL, 5, 5, rivers = 0, loopFraction = 0f, random = Random(3))
        val entrance = maze.find(Tile.ENTRANCE)!!
        val sealed = maze.withCell(entrance, maze.cells[entrance].copy(walls = 0b1111))
        assertTrue(validate(sealed).any { it.contains("入口") })
    }

    @Test
    fun ballStaysInsideTheOuterWall() {
        val maze = generateMaze(Algorithm.DFS, 6, 6, rivers = 0, loopFraction = 0f, random = Random(1))
        val sim = MarbleSim(maze)
        sim.x = 0.25f
        sim.y = 0.5f
        sim.vx = -8f
        sim.vy = 0f
        repeat(30) { sim.step(1f / 60f) }
        assertTrue("x=${sim.x}", sim.x >= sim.radius - 0.02f)
        assertTrue(sim.x < maze.width)
    }
}
