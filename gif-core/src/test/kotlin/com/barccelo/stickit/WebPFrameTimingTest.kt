package com.barccelo.stickit

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream

class WebPFrameTimingTest {
    @Test fun fullLengthCompatibleProfileHasAtMostSixtyFrames() {
        val originalEndTimes = (1..250).map { it * 40 }
        val output = WebPFrameTiming.plan(originalEndTimes)
        assertTrue(output.size <= 60)
        assertEquals(1_000, output.sumOf { it.delayCentiseconds })
        assertEquals(0, output.first().sourceIndex)
        assertTrue(output.last().sourceIndex >= 240)
        assertTrue(output.all { it.delayCentiseconds >= 4 })
    }

    @Test fun smoothModeKeepsAllFramesWhenUnder350AndDurationIsSame() {
        val originalEndTimes = (1..250).map { it * 40 }
        val output = WebPFrameTiming.plan(originalEndTimes,
            WebPFrameTiming.SMOOTH_FRAME_CAP)
        assertEquals(250, output.size)
        assertEquals(1_000, output.sumOf { it.delayCentiseconds })
        assertEquals(0, output.first().sourceIndex)
        assertEquals(249, output.last().sourceIndex)
    }

    @Test fun shortReferenceMatchesPreviousWorkingClipButIsNeverDefault() {
        val input = (1..150).map { it * 40 }
        val clipped = WebPFrameTiming.plan(input,
            WebPFrameTiming.COMPAT_FRAME_CAP,
            WebPFrameTiming.SHORT_REFERENCE_MS)
        assertEquals(60, clipped.size)
        assertEquals(240, clipped.sumOf { it.delayCentiseconds })
        assertEquals(59, clipped.last().sourceIndex)
        val complete = WebPFrameTiming.plan(input)
        assertEquals(600, complete.sumOf { it.delayCentiseconds })
        assertTrue(complete.last().sourceIndex > 59)
    }

    @Test fun exactFortyMillisecondSourceIsUnchangedForShortAnimation() {
        val output = WebPFrameTiming.plan((1..60).map { it * 40 })
        assertEquals(60, output.size)
        assertEquals(240, output.sumOf { it.delayCentiseconds })
        assertTrue(output.all { it.delayCentiseconds == 4 })
        assertEquals(59, output.last().sourceIndex)
    }

    @Test fun repeatedFramesAreMergedWithoutStretchingDuration() {
        val output = WebPFrameTiming.plan(listOf(200, 400, 600, 800))
        assertEquals(4, output.size)
        assertEquals(listOf(0, 1, 2, 3), output.map { it.sourceIndex })
        assertEquals(listOf(20, 20, 20, 20),
            output.map { it.delayCentiseconds })
        assertEquals(80, output.sumOf { it.delayCentiseconds })
    }

    @Test fun tenMillisecondFramesKeepTimeWhileReducingLoad() {
        val output = WebPFrameTiming.plan((1..300).map { it * 10 })
        assertEquals(60, output.size)
        assertEquals(300, output.sumOf { it.delayCentiseconds })
        assertTrue(output.all { it.delayCentiseconds == 5 })
        assertEquals(0, output.first().sourceIndex)
        assertEquals(295, output.last().sourceIndex)
    }

    @Test fun irregularFrameDurationsRetainTotalPlaybackTime() {
        val source = listOf(33, 66, 116, 170, 240, 315, 401, 515, 645, 765)
        val output = WebPFrameTiming.plan(source)
        assertEquals(77, output.sumOf { it.delayCentiseconds })
        assertTrue(output.all { it.delayCentiseconds >= 4 })
        assertTrue(output.zipWithNext().all { (a, b) ->
            a.sourceIndex <= b.sourceIndex
        })
        assertEquals(9, output.last().sourceIndex)
    }

    @Test fun gifOutputContainsExactlyPlannedDelays() {
        val output = WebPFrameTiming.plan((1..100).map { it * 10 })
        val buffer = ByteArrayOutputStream()
        val writer = GifWriter(buffer, 8)
        val pixels = IntArray(64) { 0xFF112233.toInt() }
        output.forEach { writer.frame(pixels, it.delayCentiseconds) }
        writer.finish()
        val encoded = GifFrameTiming.delaysCentiseconds(buffer.toByteArray())
        assertEquals(output.map { it.delayCentiseconds }, encoded)
        assertEquals(100, encoded.sum())
    }

    @Test fun rejectsBadDurationRatherThanSilentlyCroppingInDefaultMode() {
        try {
            WebPFrameTiming.plan(listOf(40, 30_001))
            fail("Expected error")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("30 segundos"))
        }
    }

    @Test fun rejectsNonmonotonicSource() {
        try {
            WebPFrameTiming.plan(listOf(40, 30, 70))
            fail("Expected error")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("desordenados"))
        }
    }

    @Test fun sourceFrameCountIsBounded() {
        try {
            WebPFrameTiming.plan((1..5001).map { it * 5 })
            fail("Expected error")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("demasiados fotogramas"))
        }
    }













    @Test fun fullReferenceExtendsWorkingClipWithoutCuttingOrSpeeding() {
        val timestamps = (1..150).map { it * 40 } // 6 seconds, 25 FPS
        val oldShort = WebPFrameTiming.plan(timestamps,
            WebPFrameTiming.COMPAT_FRAME_CAP, WebPFrameTiming.SHORT_REFERENCE_MS)
        val result = WebPFrameTiming.fullAtReferenceCadence(timestamps)
        assertEquals(150, result.size)
        assertEquals(600, result.sumOf { it.delayCentiseconds })
        assertEquals(oldShort, result.take(oldShort.size))
        assertEquals(149, result.last().sourceIndex)
        assertTrue(result.all { it.delayCentiseconds == 4 })
    }

    @Test fun fullReferenceUsesDynamicFramesAndKeepsThirtySeconds() {
        val timestamps = (1..750).map { it * 40 }
        val result = WebPFrameTiming.fullAtReferenceCadence(timestamps)
        assertEquals(750, result.size)
        assertEquals(3_000, result.sumOf { it.delayCentiseconds })
        assertEquals(749, result.last().sourceIndex)
        assertTrue(result.all { it.delayCentiseconds == 4 })
    }

    @Test fun fullReferenceUsesOriginalTimeForSlowSourceFrames() {
        // Old experimental mode imposed 40 ms per SOURCE frame, accelerating it.
        val timestamps = (1..80).map { it * 100 } // 8 seconds at 10 FPS
        val result = WebPFrameTiming.fullAtReferenceCadence(timestamps)
        assertEquals(800, result.sumOf { it.delayCentiseconds })
        assertEquals(0, result.first().sourceIndex)
        assertEquals(79, result.last().sourceIndex)
        assertTrue(result.size <= 80)
        assertTrue(result.all { it.delayCentiseconds >= 4 })
    }

    @Test fun fullReferenceSamplesFastFramesButKeepsWholeDuration() {
        val timestamps = (1..1_000).map { it * 10 } // 10 seconds at 100 FPS
        val output = WebPFrameTiming.fullAtReferenceCadence(timestamps)
        val short = WebPFrameTiming.plan(timestamps,
            WebPFrameTiming.COMPAT_FRAME_CAP, WebPFrameTiming.SHORT_REFERENCE_MS)
        assertEquals(short, output.take(short.size))
        assertEquals(1_000, output.sumOf { it.delayCentiseconds })
        assertTrue(output.size > 200)
        assertTrue(output.size <= 250)
        assertTrue(output.zipWithNext().all { (a, b) ->
            a.sourceIndex < b.sourceIndex
        })
    }

    @Test fun fullReferenceDoesNotStretchFrameAcrossWindowBoundary() {
        val source = listOf(4_000, 4_500, 7_200) // frames span 2.4-s windows
        val output = WebPFrameTiming.fullAtReferenceCadence(source)
        assertEquals(listOf(0, 1, 2), output.map { it.sourceIndex })
        assertEquals(720, output.sumOf { it.delayCentiseconds })
        assertEquals(listOf(400, 50, 270), output.map { it.delayCentiseconds })
    }

    @Test fun fullReferenceKeepsNonMultipleOfWindowDuration() {
        val source = (1..61).map { it * 40 } + listOf(2_470)
        val output = WebPFrameTiming.fullAtReferenceCadence(source)
        assertEquals(247, output.sumOf { it.delayCentiseconds })
        assertTrue(output.zipWithNext().all { (a, b) -> a.sourceIndex < b.sourceIndex })
        assertEquals(source.lastIndex, output.last().sourceIndex)
    }

    @Test fun fullReferenceAppendsTinyTailWithoutInventingFortyMs() {
        val source = (1..60).map { it * 40 } + listOf(2_411)
        val output = WebPFrameTiming.fullAtReferenceCadence(source)
        assertEquals(241, output.sumOf { it.delayCentiseconds })
        assertEquals(60, output.size)
        assertEquals(5, output.last().delayCentiseconds)
    }

    @Test fun fullReferenceGifEncodesPlannedDelays() {
        val source = (1..180).map { it * 40 }
        val planned = WebPFrameTiming.fullAtReferenceCadence(source)
        val buffer = ByteArrayOutputStream()
        val writer = GifWriter(buffer, 8)
        val pixels = IntArray(64) { 0xFF112233.toInt() }
        planned.forEach { writer.frame(pixels, it.delayCentiseconds) }
        writer.finish()
        assertEquals(planned.map { it.delayCentiseconds },
            GifFrameTiming.delaysCentiseconds(buffer.toByteArray()))
    }


}
