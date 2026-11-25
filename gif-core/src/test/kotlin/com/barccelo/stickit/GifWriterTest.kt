package com.barccelo.stickit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.math.abs

class GifWriterTest {
    private fun generate(frame1: IntArray, frame2: IntArray): ByteArray {
        val bytes = ByteArrayOutputStream()
        GifWriter(bytes, 256).apply {
            frame(frame1, 12)
            frame(frame2, 12)
            finish()
        }
        return bytes.toByteArray()
    }

    private inline fun withDecoded(bytes: ByteArray, inspect: (javax.imageio.ImageReader) -> Unit) {
        val reader = ImageIO.getImageReadersByFormatName("gif").next()
        try {
            ImageIO.createImageInputStream(ByteArrayInputStream(bytes)).use { source ->
                reader.input = source
                inspect(reader)
            }
        } finally {
            reader.dispose()
        }
    }

    @Test fun visibleImageAndTransparencySurviveEncoding() {
        val image = IntArray(256 * 256) { 0 }
        for (y in 40 until 200) for (x in 40 until 200) {
            image[y * 256 + x] = 0xFF15C98C.toInt()
        }
        withDecoded(generate(image, image)) { reader ->
            assertEquals(2, reader.getNumImages(true))
            val frame = reader.read(0)
            assertEquals(256, frame.width)
            assertEquals(0, frame.getRGB(0, 0) ushr 24)
            assertEquals(255, frame.getRGB(120, 120) ushr 24)
            assertEquals(255, reader.read(1).getRGB(120, 120) ushr 24)
            assertColorClose(0xFF15C98C.toInt(), frame.getRGB(120, 120), 8)
        }
    }

    @Test fun saturatedSkinAndPastelColorsStayFaithful() {
        val colors = intArrayOf(
            0xFF141C38.toInt(), // Night blue
            0xFFE9B18B.toInt(), // Warm skin
            0xFFB37658.toInt(), // Brown
            0xFFE9DCD1.toInt(), // Cream
            0xFFDE9AC7.toInt(), // Pink
            0xFF79B6D9.toInt(), // Blue
            0xFF8A926A.toInt(), // Olive
            0xFF101010.toInt(), // Near-black
            0xFFF8F6EF.toInt(), // Warm white
            0xFFEF323B.toInt()  // Bright red
        )
        val image = IntArray(256 * 256) { i -> colors[((i % 256) / 24).coerceAtMost(colors.lastIndex)] }
        withDecoded(generate(image, image)) { reader ->
            assertEquals(2, reader.getNumImages(true))
            val frame = reader.read(0)
            colors.forEachIndexed { idx, expected ->
                val x = (idx * 24 + 10).coerceAtMost(255)
                assertColorClose(expected, frame.getRGB(x, 70), 8)
            }
        }
    }

    @Test fun complexPhotographicGradientPreservesColorBetterThanFixedRgb332() {
        val image = IntArray(256 * 256) { i ->
            val x = i % 256
            val y = i / 256
            val r = ((x * 3 + y * 2) / 5).coerceIn(0, 255)
            val g = ((x * 2 + y * 3) / 5).coerceIn(0, 255)
            val b = ((255 - x) * 2 + y * 3) / 5
            (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
        withDecoded(generate(image, image.reversedArray())) { reader ->
            assertEquals(2, reader.getNumImages(true))
            for (frameIndex in 0..1) {
                val frame = reader.read(frameIndex)
                val reference = if (frameIndex == 0) image else image.reversedArray()
                var sumError = 0L
                var samples = 0
                for (y in 0 until 256 step 8) for (x in 0 until 256 step 8) {
                    val expected = reference[y * 256 + x]
                    val actual = frame.getRGB(x, y)
                    assertEquals("Opaque source lost alpha", 255, actual ushr 24)
                    for (shift in listOf(16, 8, 0)) {
                        sumError += abs(((expected ushr shift) and 255) - ((actual ushr shift) and 255))
                        samples++
                    }
                }
                val meanError = sumError.toDouble() / samples
                assertTrue("Distorted frame $frameIndex: mean channel error $meanError", meanError < 15.0)
            }
        }
    }

    @Test fun colorfulHighDetailFrameDoesNotGoBlank() {
        val image = IntArray(256 * 256) { i ->
            val x = i % 256
            val y = i / 256
            val r = (x * 13 + y * 7) and 255
            val g = (x * 7 + y * 3) and 255
            val b = (x * 5 + y * 11) and 255
            (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
        withDecoded(generate(image, image.reversedArray())) { reader ->
            assertEquals(2, reader.getNumImages(true))
            for (frameIndex in 0..1) {
                val bitmap = reader.read(frameIndex)
                var nonTransparent = 0
                for (y in 0 until 256 step 16) for (x in 0 until 256 step 16)
                    if ((bitmap.getRGB(x, y) ushr 24) != 0) nonTransparent++
                assertTrue("GIF frame $frameIndex is blank", nonTransparent > 220)
            }
        }
    }

    @Test fun croppedFramesHaveSmallerRectanglesAndPreserveGifDuration() {
        val first = IntArray(160 * 160) { 0 }
        val second = IntArray(160 * 160) { 0 }
        for (y in 50 until 82) for (x in 40 until 72) first[y * 160 + x] =
            0xFFFF5533.toInt()
        for (y in 60 until 92) for (x in 50 until 82) second[y * 160 + x] =
            0xFFFF5533.toInt()
        val compact = ByteArrayOutputStream()
        val legacy = ByteArrayOutputStream()
        GifWriter(compact, 160, cropTransparentBounds = true).apply {
            frame(first, 4)
            frame(second, 4)
            finish()
        }
        GifWriter(legacy, 160).apply {
            frame(first, 4)
            frame(second, 4)
            finish()
        }
        val bytes = compact.toByteArray()
        assertTrue("Cropped frames must save bytes",
            bytes.size < legacy.size())
        assertEquals(listOf(4, 4), GifFrameTiming.delaysCentiseconds(bytes))
        assertEquals(160, (bytes[6].toInt() and 255))
        assertEquals(160, (bytes[8].toInt() and 255))
        withDecoded(bytes) { reader ->
            assertEquals(2, reader.getNumImages(true))
            assertEquals(32, reader.read(0).width)
            assertEquals(32, reader.read(0).height)
            assertEquals(32, reader.read(1).width)
            assertEquals(32, reader.read(1).height)
            assertEquals(255, reader.read(0).getRGB(10, 10) ushr 24)
        }
    }

    @Test fun blankFrameInOptimizedWriterDoesNotBreakPlayback() {
        val blank = IntArray(112 * 112)
        val filled = IntArray(112 * 112) { i ->
            if (i % 112 in 20 until 60 && i / 112 in 20 until 60)
                0xFF2277BB.toInt() else 0
        }
        val output = ByteArrayOutputStream()
        GifWriter(output, 112, cropTransparentBounds = true).apply {
            frame(filled, 4)
            frame(blank, 12)
            frame(filled, 4)
            finish()
        }
        val bytes = output.toByteArray()
        assertEquals(listOf(4, 12, 4), GifFrameTiming.delaysCentiseconds(bytes))
        withDecoded(bytes) { reader ->
            assertEquals(3, reader.getNumImages(true))
            assertEquals(1, reader.read(1).width)
            assertEquals(1, reader.read(1).height)
        }
    }

    private fun assertColorClose(expected: Int, actual: Int, maxError: Int) {
        for (shift in listOf(16, 8, 0)) {
            val e = (expected ushr shift) and 255
            val a = (actual ushr shift) and 255
            assertTrue("Color mismatch: expected $e, got $a for channel $shift", abs(e - a) <= maxError)
        }
    }
}
