package com.barccelo.stickit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

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

    @Test fun visibleImageAndTransparencySurviveEncoding() {
        val image = IntArray(256 * 256) { 0 }
        for (y in 40 until 200) for (x in 40 until 200) {
            image[y * 256 + x] = 0xFF15C98C.toInt()
        }
        val encoded = generate(image, image)
        val reader = ImageIO.getImageReadersByFormatName("gif").next()
        ImageIO.createImageInputStream(ByteArrayInputStream(encoded)).use { source ->
            reader.input = source
            assertEquals(2, reader.getNumImages(true))
            val frame = reader.read(0)
            assertEquals(256, frame.width)
            assertEquals(0, frame.getRGB(0, 0) ushr 24)
            assertEquals(255, frame.getRGB(120, 120) ushr 24)
            assertTrue((frame.getRGB(120, 120) and 0x00FFFFFF) != 0)
            assertEquals(255, reader.read(1).getRGB(120, 120) ushr 24)
        }
        reader.dispose()
    }

    @Test fun variablePaletteAndLargeFramesDoNotGoBlankAtCodeWidthBoundaries() {
        val image = IntArray(256 * 256) { i ->
            val x = i % 256
            val y = i / 256
            val r = (x * 13 + y * 7) and 255
            val g = (x * 7 + y * 3) and 255
            val b = (x * 5 + y * 11) and 255
            (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
        val encoded = generate(image, image.reversedArray())
        val reader = ImageIO.getImageReadersByFormatName("gif").next()
        ImageIO.createImageInputStream(ByteArrayInputStream(encoded)).use { source ->
            reader.input = source
            assertEquals(2, reader.getNumImages(true))
            for (frameIndex in 0..1) {
                val bitmap = reader.read(frameIndex)
                var nonTransparent = 0
                for (y in 0 until 256 step 16) for (x in 0 until 256 step 16) {
                    if ((bitmap.getRGB(x, y) ushr 24) != 0) nonTransparent++
                }
                assertTrue("GIF frame $frameIndex is blank", nonTransparent > 220)
            }
        }
        reader.dispose()
    }
}
