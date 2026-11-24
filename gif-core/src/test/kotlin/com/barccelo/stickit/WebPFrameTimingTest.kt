package com.barccelo.stickit

import org.junit.Assert.*
import org.junit.Test

class WebPFrameTimingTest {
    @Test fun preservesThreeSecondWebpRatherThanClippingAt2400ms() {
        val source = (1..75).map { it * 40 }
        val output = WebPFrameTiming.plan(source)
        assertEquals(75, output.size)
        assertEquals(300, output.sumOf { it.delayCentiseconds })
        assertEquals(0, output.first().sourceIndex)
        assertEquals(74, output.last().sourceIndex)
    }

    @Test fun usesEndTimestampsAsFrameDurationsNotStartTimes() {
        // First decoded frame is shown from 0 to 100 ms.
        val output = WebPFrameTiming.plan(listOf(100, 150, 230, 330))
        assertEquals(listOf(10, 5, 8, 10), output.map { it.delayCentiseconds })
        assertEquals(listOf(0, 1, 2, 3), output.map { it.sourceIndex })
    }

    @Test fun mergesTooShortFramesWithoutMakingPlaybackFaster() {
        val output = WebPFrameTiming.plan((1..300).map { it * 10 })
        assertEquals(300, output.sumOf { it.delayCentiseconds })
        assertEquals(150, output.size)
        assertTrue(output.all { it.delayCentiseconds >= 2 })
    }

    @Test fun keepsDurationAcrossIrregularFrameBoundaries() {
        val source = listOf(33, 66, 116, 170, 240, 315, 401, 515, 645, 765)
        val output = WebPFrameTiming.plan(source)
        assertEquals(77, output.sumOf { it.delayCentiseconds })
        assertEquals(source.size, output.size)
        assertEquals(listOf(3, 4, 5, 5, 7, 8, 8, 12, 13, 12),
            output.map { it.delayCentiseconds })
    }

    @Test fun doesNotSilentlyTrimLongerAnimatedStickers() {
        val output = WebPFrameTiming.plan((1..250).map { it * 40 })
        assertEquals(1_000, output.sumOf { it.delayCentiseconds })
        assertEquals(250, output.size)
    }

    @Test fun boundsWorkWithoutChangingOverallDuration() {
        val output = WebPFrameTiming.plan((1..1_000).map { it * 20 })
        assertTrue(output.size <= 350)
        assertEquals(2_000, output.sumOf { it.delayCentiseconds })
        assertTrue(output.all { it.delayCentiseconds >= 2 })
    }

    @Test fun rejectRatherThanSilentlyTrimMoreThanThirtySeconds() {
        try {
            WebPFrameTiming.plan(listOf(40, 30_001))
            fail("Expected rejection")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("30 segundos"))
        }
    }

    @Test fun nonMonotonicTimestampsMustNotProduceBrokenGif() {
        try {
            WebPFrameTiming.plan(listOf(40, 30, 70))
            fail("Expected rejection")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("desordenados"))
        }
    }
}
