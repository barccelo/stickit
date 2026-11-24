package com.barccelo.stickit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GifTimelineTest {
    @Test fun highFrameRateKeepsExactlyTheOriginalDuration() {
        val frames = GifTimeline.samples(2400, 60.0)
        assertEquals(60, frames.size)
        assertEquals(240, frames.sumOf { it.delayCentiseconds })
        assertTrue(frames.all { it.delayCentiseconds == 4 })
        assertEquals(0L, frames.first().presentationTimeUs)
        assertEquals(2360000L, frames.last().presentationTimeUs)
    }

    @Test fun fractionalFramePeriodDistributesDelayWithoutSlowingAnimation() {
        val frames = GifTimeline.samples(1000, 15.0)
        assertEquals(15, frames.size)
        assertEquals(100, frames.sumOf { it.delayCentiseconds })
        assertTrue(frames.all { it.delayCentiseconds in 6..7 })
        assertTrue(frames.zipWithNext().all { (a, b) -> a.presentationTimeUs < b.presentationTimeUs })
    }

    @Test fun oneSecondNormalVideoHasSmoothTwentyFiveFps() {
        val frames = GifTimeline.samples(1000)
        assertEquals(25, frames.size)
        assertEquals(100, frames.sumOf { it.delayCentiseconds })
    }

    @Test fun veryShortVideoAlwaysHasLegalDurations() {
        val frames = GifTimeline.samples(75, 60.0)
        assertEquals(8, frames.sumOf { it.delayCentiseconds })
        assertTrue(frames.all { it.delayCentiseconds >= 2 })
    }

    @Test fun slowSourceIsNotArtificiallyAccelerated() {
        val frames = GifTimeline.samples(2400, 10.0)
        assertEquals(24, frames.size)
        assertEquals(240, frames.sumOf { it.delayCentiseconds })
    }
}
