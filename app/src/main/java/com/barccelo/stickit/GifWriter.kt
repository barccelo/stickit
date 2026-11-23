package com.barccelo.stickit

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import kotlin.math.min

/**
 * Simple self-contained GIF89a encoder, intentionally avoiding external native binaries.
 * 3/3/2-bit global palette: adequate for an initial clipboard compatibility prototype.
 */
class GifWriter(private val stream: OutputStream, private val size: Int = 256) {
    init {
        writeAscii("GIF89a")
        short(size); short(size)
        byte(0xF7); byte(0); byte(0)
        for (index in 0..255) {
            val r = (index shr 5) * 255 / 7
            val g = ((index shr 2) and 7) * 255 / 7
            val b = (index and 3) * 255 / 3
            byte(r); byte(g); byte(b)
        }
        byte(0x21); byte(0xFF); byte(11)
        writeAscii("NETSCAPE2.0")
        byte(3); byte(1); short(0); byte(0)
    }

    fun frame(bitmap: Bitmap, delayCentiseconds: Int) {
        val square = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(square)
        canvas.drawColor(Color.TRANSPARENT)
        val scale = min(size.toFloat() / bitmap.width, size.toFloat() / bitmap.height)
        val width = bitmap.width * scale
        val height = bitmap.height * scale
        canvas.drawBitmap(
            bitmap,
            Rect(0, 0, bitmap.width, bitmap.height),
            RectF((size - width) / 2, (size - height) / 2, (size + width) / 2, (size + height) / 2),
            Paint(Paint.FILTER_BITMAP_FLAG)
        )
        val colors = IntArray(size * size)
        square.getPixels(colors, 0, size, 0, 0, size, size)
        val pixels = ByteArray(colors.size)
        colors.forEachIndexed { i, color ->
            val index = if (Color.alpha(color) < 128) 0
                else (Color.red(color) and 0xE0) or ((Color.green(color) shr 3) and 0x1C) or (Color.blue(color) shr 6)
            pixels[i] = (if (index == 0 && Color.alpha(color) >= 128) 1 else index).toByte()
        }
        square.recycle()

        byte(0x21); byte(0xF9); byte(4); byte(0x09)
        short(delayCentiseconds.coerceIn(2, 65535))
        byte(0); byte(0)
        byte(0x2C); short(0); short(0); short(size); short(size); byte(0)
        byte(8)
        val compressed = lzw(pixels)
        var offset = 0
        while (offset < compressed.size) {
            val count = min(255, compressed.size - offset)
            byte(count)
            stream.write(compressed, offset, count)
            offset += count
        }
        byte(0)
    }

    fun finish() {
        byte(0x3B)
        stream.flush()
    }

    private fun lzw(pixels: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        val codes = CodeBits(out)
        val clear = 256
        val end = 257
        val dictionary = HashMap<Int, Int>(4096)
        var sizeBits = 9
        var nextCode = 258
        codes.write(clear, sizeBits)
        var prefix = pixels[0].toInt() and 255
        for (i in 1 until pixels.size) {
            val pixel = pixels[i].toInt() and 255
            val key = (prefix shl 8) or pixel
            val existing = dictionary[key]
            if (existing != null) {
                prefix = existing
            } else {
                codes.write(prefix, sizeBits)
                if (nextCode < 4096) {
                    dictionary[key] = nextCode++
                    if (nextCode == (1 shl sizeBits) && sizeBits < 12) sizeBits++
                } else {
                    codes.write(clear, sizeBits)
                    dictionary.clear()
                    sizeBits = 9
                    nextCode = 258
                }
                prefix = pixel
            }
        }
        codes.write(prefix, sizeBits)
        codes.write(end, sizeBits)
        codes.finish()
        return out.toByteArray()
    }

    private class CodeBits(private val out: OutputStream) {
        private var accumulator = 0
        private var bits = 0
        fun write(code: Int, width: Int) {
            accumulator = accumulator or (code shl bits)
            bits += width
            while (bits >= 8) {
                out.write(accumulator and 255)
                accumulator = accumulator ushr 8
                bits -= 8
            }
        }
        fun finish() { if (bits > 0) out.write(accumulator and 255) }
    }

    private fun writeAscii(s: String) = stream.write(s.toByteArray(Charsets.US_ASCII))
    private fun byte(i: Int) = stream.write(i and 255)
    private fun short(i: Int) { byte(i); byte(i shr 8) }
}
