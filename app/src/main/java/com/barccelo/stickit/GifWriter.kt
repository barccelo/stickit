package com.barccelo.stickit

import java.io.ByteArrayOutputStream
import java.io.OutputStream
import kotlin.math.min

/**
 * Small GIF89a encoder with 3/3/2-bit palette and alpha transparency.
 * Platform-neutral so we can verify the generated GIFs with ImageIO in CI.
 */
class GifWriter(private val stream: OutputStream, private val size: Int = 256) {
    init {
        require(size in 1..4096)
        ascii("GIF89a")
        short(size); short(size)
        byte(0xF7); byte(0); byte(0)
        for (index in 0..255) {
            byte((index shr 5) * 255 / 7)
            byte(((index shr 2) and 7) * 255 / 7)
            byte((index and 3) * 255 / 3)
        }
        byte(0x21); byte(0xFF); byte(11)
        ascii("NETSCAPE2.0")
        byte(3); byte(1); short(0); byte(0)
    }

    fun frame(argb: IntArray, delayCentiseconds: Int) {
        require(argb.size == size * size) { "Expected a square ARGB frame" }
        val pixels = ByteArray(argb.size)
        argb.forEachIndexed { i, color ->
            val alpha = (color ushr 24) and 0xFF
            val index = if (alpha < 128) 0 else {
                val r = (color ushr 16) and 0xFF
                val g = (color ushr 8) and 0xFF
                val b = color and 0xFF
                (r and 0xE0) or ((g ushr 3) and 0x1C) or (b ushr 6)
            }
            // 0 is the transparent palette slot; remap visible black to 1.
            pixels[i] = (if (alpha >= 128 && index == 0) 1 else index).toByte()
        }

        // Graphics control extension: disposal = restore to background, transparency index = 0.
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

    /**
     * GIF LZW: the decoder creates dictionary entries one emitted code behind
     * the encoder. Grow the packed code width only once nextCode exceeds 2^bits,
     * not when it is equal. The former implementation produced unreadable frames
     * when a sufficiently varied image crossed the 9-bit boundary.
     */
    private fun lzw(pixels: ByteArray): ByteArray {
        require(pixels.isNotEmpty())
        val out = ByteArrayOutputStream()
        val codes = BitWriter(out)
        val clear = 256
        val end = 257
        val dictionary = HashMap<Int, Int>(4096)
        var width = 9
        var nextCode = 258
        codes.write(clear, width)

        var prefix = pixels[0].toInt() and 255
        for (i in 1 until pixels.size) {
            val value = pixels[i].toInt() and 255
            val key = (prefix shl 8) or value
            val existing = dictionary[key]
            if (existing != null) {
                prefix = existing
            } else {
                codes.write(prefix, width)
                if (nextCode < 4096) {
                    dictionary[key] = nextCode++
                    if (nextCode > (1 shl width) && width < 12) width++
                } else {
                    codes.write(clear, width)
                    dictionary.clear()
                    width = 9
                    nextCode = 258
                }
                prefix = value
            }
        }
        codes.write(prefix, width)
        codes.write(end, width)
        codes.finish()
        return out.toByteArray()
    }

    private class BitWriter(private val out: OutputStream) {
        private var pending = 0
        private var bits = 0
        fun write(code: Int, width: Int) {
            pending = pending or (code shl bits)
            bits += width
            while (bits >= 8) {
                out.write(pending and 255)
                pending = pending ushr 8
                bits -= 8
            }
        }
        fun finish() { if (bits > 0) out.write(pending and 255) }
    }

    private fun ascii(value: String) = stream.write(value.toByteArray(Charsets.US_ASCII))
    private fun byte(value: Int) = stream.write(value and 255)
    private fun short(value: Int) { byte(value); byte(value ushr 8) }
}
