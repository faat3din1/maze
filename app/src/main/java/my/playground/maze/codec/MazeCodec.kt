package my.playground.maze.codec

import my.playground.maze.maze.validate
import my.playground.maze.model.Cell
import my.playground.maze.model.Maze

sealed interface DecodeOutcome {
    data class Success(val maze: Maze) : DecodeOutcome
    data class Failure(val reasons: List<String>) : DecodeOutcome
}

object MazeCodec {
    private const val VERSION: Byte = 1

    fun encode(maze: Maze): ByteArray {
        val count = maze.width * maze.height
        val body = ByteArray(5 + count)
        body[0] = 'M'.code.toByte()
        body[1] = 'Z'.code.toByte()
        body[2] = VERSION
        body[3] = maze.width.toByte()
        body[4] = maze.height.toByte()
        for (i in 0 until count) {
            body[5 + i] = maze.cells[i].encodeByte().toByte()
        }
        val crc = crc16(body)
        return body + byteArrayOf((crc shr 8).toByte(), crc.toByte())
    }

    fun qrText(maze: Maze): String = String(encode(maze), Charsets.ISO_8859_1)

    fun normalizeShareText(raw: String): String = raw.replace(Regex("\\s"), "")

    fun decodeQrText(text: String): DecodeOutcome = decode(text.toByteArray(Charsets.ISO_8859_1))

    fun decode(bytes: ByteArray): DecodeOutcome {
        if (bytes.size < 7) return DecodeOutcome.Failure(listOf("QR 內容太短"))
        val body = bytes.copyOf(bytes.size - 2)
        val expected = crc16(body)
        val actual = ((bytes[bytes.size - 2].toInt() and 0xFF) shl 8) or (bytes[bytes.size - 1].toInt() and 0xFF)
        if (expected != actual) return DecodeOutcome.Failure(listOf("校驗失敗"))
        if (body[0] != 'M'.code.toByte() || body[1] != 'Z'.code.toByte()) {
            return DecodeOutcome.Failure(listOf("不是迷宮 QR"))
        }
        if (body[2] != VERSION) return DecodeOutcome.Failure(listOf("版本不支援"))
        val width = body[3].toInt() and 0xFF
        val height = body[4].toInt() and 0xFF
        if (width !in Maze.MIN_WIDTH..Maze.MAX_WIDTH || height !in Maze.MIN_HEIGHT..Maze.MAX_HEIGHT) {
            return DecodeOutcome.Failure(listOf("地圖尺寸不合法"))
        }
        if (body.size != 5 + width * height) return DecodeOutcome.Failure(listOf("地圖長度不符"))
        val cells = ArrayList<Cell>(width * height)
        for (i in 0 until width * height) {
            val cell = Cell.decodeByte(body[5 + i].toInt() and 0xFF)
                ?: return DecodeOutcome.Failure(listOf("第 ${i % width},${i / width} 格類型不合法"))
            cells += cell
        }
        val maze = Maze(width, height, cells)
        val errors = validate(maze)
        return if (errors.isEmpty()) DecodeOutcome.Success(maze) else DecodeOutcome.Failure(errors)
    }

    fun crc16(data: ByteArray): Int {
        var crc = 0xFFFF
        for (raw in data) {
            crc = crc xor ((raw.toInt() and 0xFF) shl 8)
            repeat(8) {
                crc = if ((crc and 0x8000) != 0) (crc shl 1) xor 0x1021 else crc shl 1
                crc = crc and 0xFFFF
            }
        }
        return crc
    }
}
