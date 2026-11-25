package com.barccelo.stickit

import java.io.ByteArrayOutputStream
import java.io.OutputStream
import kotlin.math.min
import kotlin.math.sqrt

/**
 * GIF89a encoder with a separate adaptive 256-color palette for every frame.
 *
 * GIF is an indexed-color format. A fixed RGB332 palette severely distorts
 * photographs and skin tones; median-cut quantization preserves the image's
 * actual dominant colors. Index 0 is reserved for transparency.
 *
 * Platform-neutral: decoded output is checked by JVM tests using ImageIO.
 */
class GifWriter(private val stream: OutputStream, private val size: Int = 256,
    private val cropTransparentBounds: Boolean = false) {
    init {
        require(size in 1..4096)
        ascii("GIF89a")
        short(size); short(size)
        byte(0x70) // No global table; 8-bit color resolution. Each frame has its own palette.
        byte(0); byte(0)
        byte(0x21); byte(0xFF); byte(11)
        ascii("NETSCAPE2.0")
        byte(3); byte(1); short(0); byte(0)
    }

    fun frame(argb: IntArray, delayCentiseconds: Int) {
        require(argb.size == size * size) { "Expected a square ARGB frame" }
        // Crop blank borders WITHOUT altering the logical screen or disposal.
        // Each frame is independent: disposal=2 clears the previous rectangle.
        // This is safe for stickers with moving content and transparent areas.
        // Keep legacy full-frame output unchanged unless explicitly enabled.
        var left = size
        var top = size
        var right = -1
        var bottom = -1
        if (cropTransparentBounds) {
            for (y in 0 until size) for (x in 0 until size) {
                if ((argb[y * size + x] ushr 24) >= 128) {
                    if (x < left) left = x
                    if (x > right) right = x
                    if (y < top) top = y
                    if (y > bottom) bottom = y
                }
            }
        }
        if (!cropTransparentBounds || right < 0) {
            if (!cropTransparentBounds) {
                left = 0; top = 0; right = size - 1; bottom = size - 1
            } else {
                // GIF descriptors require at least one pixel.
                left = 0; top = 0; right = 0; bottom = 0
            }
        }
        val width = right - left + 1
        val height = bottom - top + 1
        val region = if (left == 0 && top == 0 &&
            width == size && height == size) argb else
            IntArray(width * height) { index ->
                val y = index / width
                val x = index % width
                argb[(y + top) * size + x + left]
            }
        val indexed = AdaptivePalette.quantize(region)

        // Disposal to background, transparency index 0.
        byte(0x21); byte(0xF9); byte(4); byte(0x09)
        short(delayCentiseconds.coerceIn(2, 65535))
        byte(0); byte(0)

        // Image descriptor with a per-frame 256-color local table (0x87).
        byte(0x2C); short(left); short(top); short(width); short(height); byte(0x87)
        for (color in indexed.palette) {
            byte(color ushr 16)
            byte(color ushr 8)
            byte(color)
        }
        byte(8)
        val compressed = lzw(indexed.pixels)
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

/**
 * Weighted median-cut over a 5/5/5 RGB histogram. The palette colors are
 * averages of the actual 8-bit source channels (not the 5-bit bucket centers).
 * A local palette prevents adjacent frames from sharing an unsuitable palette.
 */
internal object AdaptivePalette {
    data class Indexed(val pixels: ByteArray, val palette: IntArray)
    private const val BIN_COUNT = 32768
    private const val MAX_VISIBLE = 255

    private fun binOf(color: Int): Int {
        val r = (color ushr 19) and 31
        val g = (color ushr 11) and 31
        val b = (color ushr 3) and 31
        return (r shl 10) or (g shl 5) or b
    }

    private fun channel(bin: Int, component: Int): Int = when (component) {
        0 -> (bin ushr 10) and 31
        1 -> (bin ushr 5) and 31
        else -> bin and 31
    }

    fun quantize(argb: IntArray): Indexed {
        val counts = IntArray(BIN_COUNT)
        val redSums = LongArray(BIN_COUNT)
        val greenSums = LongArray(BIN_COUNT)
        val blueSums = LongArray(BIN_COUNT)
        for (pixel in argb) {
            if ((pixel ushr 24) < 128) continue
            val bin = binOf(pixel)
            counts[bin]++
            redSums[bin] += (pixel ushr 16) and 255
            greenSums[bin] += (pixel ushr 8) and 255
            blueSums[bin] += pixel and 255
        }

        val bins = ArrayList<Int>()
        for (key in counts.indices) if (counts[key] > 0) bins.add(key)
        val palettes = IntArray(256) // 0 = transparent.
        val indices = IntArray(BIN_COUNT)
        if (bins.isNotEmpty()) {
            val boxes = mutableListOf(bins.toIntArray())
            while (boxes.size < MAX_VISIBLE) {
                var selected = -1
                var selectedChannel = 0
                var bestScore = -1.0
                for (boxIndex in boxes.indices) {
                    val box = boxes[boxIndex]
                    if (box.size < 2) continue
                    val low = intArrayOf(31, 31, 31)
                    val high = intArrayOf(0, 0, 0)
                    var population = 0L
                    for (bin in box) {
                        population += counts[bin].toLong()
                        for (c in 0..2) {
                            val value = channel(bin, c)
                            if (value < low[c]) low[c] = value
                            if (value > high[c]) high[c] = value
                        }
                    }
                    for (c in 0..2) {
                        val spread = high[c] - low[c]
                        val weight = when (c) { 0 -> 0.90; 1 -> 1.0; else -> 0.78 }
                        val score = spread * weight * sqrt(population.toDouble())
                        if (score > bestScore) {
                            bestScore = score
                            selected = boxIndex
                            selectedChannel = c
                        }
                    }
                }
                if (selected == -1 || bestScore <= 0.0) break
                val sorted = boxes[selected].toList()
                    .sortedWith(compareBy<Int> { channel(it, selectedChannel) }.thenBy { it })
                val half = sorted.sumOf { counts[it].toLong() } / 2
                var accumulated = 0L
                var split = 1
                for (i in 0 until sorted.size - 1) {
                    accumulated += counts[sorted[i]]
                    split = i + 1
                    if (accumulated >= half) break
                }
                boxes[selected] = sorted.subList(0, split).toIntArray()
                boxes.add(sorted.subList(split, sorted.size).toIntArray())
            }
            boxes.forEachIndexed { boxIndex, box ->
                val paletteIndex = boxIndex + 1
                var total = 0L
                var rs = 0L
                var gs = 0L
                var bs = 0L
                for (bin in box) {
                    indices[bin] = paletteIndex
                    total += counts[bin].toLong()
                    rs += redSums[bin]
                    gs += greenSums[bin]
                    bs += blueSums[bin]
                }
                val r = ((rs + total / 2) / total).toInt().coerceIn(0, 255)
                val g = ((gs + total / 2) / total).toInt().coerceIn(0, 255)
                val b = ((bs + total / 2) / total).toInt().coerceIn(0, 255)
                palettes[paletteIndex] = (r shl 16) or (g shl 8) or b
            }
        }

        val pixels = ByteArray(argb.size)
        argb.forEachIndexed { offset, color ->
            pixels[offset] = if ((color ushr 24) < 128) 0
                else indices[binOf(color)].toByte()
        }
        return Indexed(pixels, palettes)
    }
}
