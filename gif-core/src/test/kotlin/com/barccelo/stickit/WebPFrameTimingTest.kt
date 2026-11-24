package com.barccelo.stickit

import org.junit.Assert.*
import org.junit.Test

class WebPFrameTimingTest {
    @Test fun preservesThreeSecondsAndOriginalVisualCadence() {
        val output = WebPFrameTiming.plan((1..75).map { it * 40 })
        assertEquals(75, output.size)
        assertEquals(300, output.sumOf { it.delayCentiseconds })
        assertTrue(output.all { it.delayCentiseconds == 4 })
        assertEquals(0, output.first().sourceIndex)
        assertEquals(74, output.last().sourceIndex)
    }

    @Test fun samplesFramesAccordingToWebpCumulativeEndTimestamp() {
        val output = WebPFrameTiming.plan(listOf(100, 150, 230, 330))
        assertEquals(8, output.size)
        assertEquals(33, output.sumOf { it.delayCentiseconds })
        assertEquals(listOf(0, 0, 0, 1, 2, 2, 3, 3),
            output.map { it.sourceIndex })
        assertTrue(output.all { it.delayCentiseconds >= 4 })
    }

    @Test fun tenMillisecondFramesNeverBecomeSlowTwentyMillisecondGifFrames() {
        val output = WebPFrameTiming.plan((1..300).map { it * 10 })
        assertEquals(300, output.sumOf { it.delayCentiseconds })
        assertEquals(75, output.size)
        assertTrue(output.all { it.delayCentiseconds == 4 })
        assertEquals(0, output.first().sourceIndex)
        assertEquals(296, output.last().sourceIndex)
    }

    @Test fun twentyMillisecondWebpFramesBecomeSafeFortyMillisecondFrames() {
        val output = WebPFrameTiming.plan((1..150).map { it * 20 })
        assertEquals(75, output.size)
        assertEquals(300, output.sumOf { it.delayCentiseconds })
        assertTrue(output.all { it.delayCentiseconds >= 4 })
        assertEquals(148, output.last().sourceIndex)
    }

    @Test fun lowFrameRateWebpRepeatsTheSameSourceFrameWithoutTruncating() {
        // Four 200-ms source frames must fill an 800-ms GIF at 25 fps.
        val output = WebPFrameTiming.plan(listOf(200, 400, 600, 800))
        assertEquals(20, output.size)
        assertEquals(80, output.sumOf { it.delayCentiseconds })
        assertEquals(5, output.count { it.sourceIndex == 0 })
        assertEquals(5, output.count { it.sourceIndex == 1 })
        assertEquals(5, output.count { it.sourceIndex == 2 })
        assertEquals(5, output.count { it.sourceIndex == 3 })
        assertTrue(output.all { it.delayCentiseconds == 4 })
    }

    @Test fun encodedDelaysMatchPlannedDurationForFastWebp() {
        val output = WebPFrameTiming.plan((1..100).map { it * 10 })
        val buffer = java.io.ByteArrayOutputStream()
        val gif = GifWriter(buffer, 8)
        val pixels = IntArray(64) { 0xFF112233.toInt() }
        output.forEach { gif.frame(pixels, it.delayCentiseconds) }
        gif.finish()
        val encoded = GifFrameTiming.delaysCentiseconds(buffer.toByteArray())
        assertEquals(output.map { it.delayCentiseconds }, encoded)
        assertEquals(100, encoded.sum())
        assertTrue(encoded.all { it >= 4 })
    }

    @Test fun irregularSourceDurationsRemainWithinOneGifTick() {
        val source = listOf(33, 66, 116, 170, 240, 315, 401, 515, 645, 765)
        val output = WebPFrameTiming.plan(source)
        assertEquals(77, output.sumOf { it.delayCentiseconds })
        assertEquals(19, output.size)
        assertTrue(output.all { it.delayCentiseconds >= 4 })
        assertTrue(output.zipWithNext().all { (a, b) ->
            b.sourceIndex >= a.sourceIndex
        })
        assertEquals(9, output.last().sourceIndex)
    }

    @Test fun retainsLongAnimationWithoutClippingAt2400Ms() {
        val output = WebPFrameTiming.plan((1..250).map { it * 40 })
        assertEquals(250, output.size)
        assertEquals(1_000, output.sumOf { it.delayCentiseconds })
        assertTrue(output.all { it.delayCentiseconds >= 4 })
    }

    @Test fun boundsLargeInputWithoutChangingOverallDuration() {
        val output = WebPFrameTiming.plan((1..1_000).map { it * 20 })
        assertEquals(350, output.size)
        assertEquals(2_000, output.sumOf { it.delayCentiseconds })
        assertTrue(output.all { it.delayCentiseconds >= 4 })
    }

    @Test fun shortAnimatedWebpHasSafeDelay() {
        val output = WebPFrameTiming.plan(listOf(10, 20, 35))
        assertEquals(1, output.size)
        assertEquals(4, output.first().delayCentiseconds)
    }

    @Test fun rejectsOversizedDurationRatherThanTruncating() {
        try {
            WebPFrameTiming.plan(listOf(40, 30_001))
            fail("Expected error")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("30 segundos"))
        }
    }

    @Test fun rejectsNonMonotonicEndTimestamps() {
        try {
            WebPFrameTiming.plan(listOf(40, 30, 70))
            fail("Expected error")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("desordenados"))
        }
    }
}
