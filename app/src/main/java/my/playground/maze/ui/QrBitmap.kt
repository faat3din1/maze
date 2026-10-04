package my.playground.maze.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.EncodeHintType
import com.google.zxing.ReaderException
import com.google.zxing.common.GlobalHistogramBinarizer
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.RGBLuminanceSource
import my.playground.maze.codec.DecodeOutcome
import my.playground.maze.codec.MazeCodec
import my.playground.maze.model.Maze

fun mazeQrBitmap(maze: Maze, size: Int = 720): Bitmap {
    val hints = mapOf(
        EncodeHintType.CHARACTER_SET to "ISO-8859-1",
        EncodeHintType.MARGIN to 1,
    )
    val matrix = QRCodeWriter().encode(MazeCodec.qrText(maze), BarcodeFormat.QR_CODE, size, size, hints)
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    for (y in 0 until size) {
        for (x in 0 until size) {
            bitmap.setPixel(x, y, if (matrix.get(x, y)) Color.BLACK else Color.WHITE)
        }
    }
    return bitmap
}

fun decodeQrImage(context: Context, uri: Uri): DecodeOutcome {
    val bitmap = loadScaledBitmap(context, uri) ?: return DecodeOutcome.Failure(listOf("讀不到圖片"))
    return try {
        val text = readQr(bitmap) ?: return DecodeOutcome.Failure(listOf("沒有掃到 QR"))
        MazeCodec.decodeQrText(text)
    } finally {
        bitmap.recycle()
    }
}

internal fun readQrPixels(pixels: IntArray, width: Int, height: Int): String? {
    val source = RGBLuminanceSource(width, height, pixels)
    val hints = mapOf(
        DecodeHintType.CHARACTER_SET to "ISO-8859-1",
        DecodeHintType.TRY_HARDER to true,
        DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
    )
    val reader = QRCodeReader()
    for (bitmap in listOf(BinaryBitmap(HybridBinarizer(source)), BinaryBitmap(GlobalHistogramBinarizer(source)))) {
        try {
            return reader.decode(bitmap, hints).text
        } catch (_: ReaderException) {
            reader.reset()
        }
    }
    return null
}

private fun readQr(bitmap: Bitmap): String? {
    val pixels = IntArray(bitmap.width * bitmap.height)
    bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
    return readQrPixels(pixels, bitmap.width, bitmap.height)
}

private fun loadScaledBitmap(context: Context, uri: Uri): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    val options = BitmapFactory.Options().apply { inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, 2048) }
    return context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
}

private fun sampleSize(width: Int, height: Int, maxSide: Int): Int {
    var sample = 1
    while (width / sample > maxSide || height / sample > maxSide) sample *= 2
    return sample
}
