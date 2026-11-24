package com.barccelo.stickit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

class GifFrameTimingTest {
    private fun encoded(delays: IntArray): ByteArray {
        val out = ByteArrayOutputStream()
        val frame = IntArray(16 * 16) { 0xFFFFC66A.toInt() }
        val writer = GifWriter(out, 16)
        for (cs in delays) writer.frame(frame, cs)
        writer.finish()
        return out.toByteArray()
    }

    @Test fun readsActualDelaysFromGifFrames() {
        val raw = encoded(intArrayOf(2, 4, 11, 3))
        assertEquals(listOf(2, 4, 11, 3), GifFrameTiming.delaysCentiseconds(raw))
    }

    @Test fun detectsShortSourceDelaysThatCanBeClampedElsewhere() {
        val gif = encoded(intArrayOf(2, 2, 2))
        val bytes = gif.copyOf()
        // The GCE pattern for 2 cs is 21 F9 04 09 02 00 00 00.
        var patched = 0
        for (i in 0 until bytes.size - 7) {
            if ((bytes[i].toInt() and 255) == 0x21 &&
                (bytes[i + 1].toInt() and 255) == 0xF9 &&
                (bytes[i + 2].toInt() and 255) == 4 &&
                (bytes[i + 4].toInt() and 255) == 2) {
                bytes[i + 4] = 1
                patched++
            }
        }
        assertEquals(3, patched)
        assertEquals(listOf(1, 1, 1), GifFrameTiming.delaysCentiseconds(bytes))
        assertEquals(30, GifFrameTiming.effectiveDurationMs(listOf(1, 1, 1)))
    }

    @Test fun shortSourceFramesAreNeverStretchedToTwentyMilliseconds() {
        assertEquals(10, GifFrameTiming.sourceDelayMs(0))
        assertEquals(10, GifFrameTiming.sourceDelayMs(1))
        assertEquals(20, GifFrameTiming.sourceDelayMs(2))
        assertEquals(600, GifFrameTiming.effectiveDurationMs(List(60) { 1 }))
    }

    @Test fun fastSourceGifTakesSameTwentyFiveFpsVideoPath() {
        // A one-second GIF with 100 frames of 10 ms each used to become two
        // seconds when every short source frame was clamped to 20 ms.
        val raw = List(100) { 1 }
        val samples = GifFrameTiming.videoCompatibleTimeline(raw)
        val indices = GifFrameTiming.sourceIndicesFor(raw, samples)
        assertEquals(25, samples.size)
        assertEquals(100, samples.sumOf { it.delayCentiseconds })
        assertTrue(samples.all { it.delayCentiseconds == 4 })
        assertEquals(0, indices.first())
        assertEquals(96, indices.last())
        assertTrue(indices.toList().zipWithNext().all { (a, b) -> b > a })
    }

    @Test fun longerGifFollowsTheSameClipLimitAsVideo() {
        val raw = List(600) { 1 }
        val frames = GifFrameTiming.videoCompatibleTimeline(raw)
        assertEquals(60, frames.size)
        assertEquals(240, frames.sumOf { it.delayCentiseconds })
        val source = GifFrameTiming.sourceIndicesFor(raw, frames)
        assertEquals(236, source.last())
    }

    @Test fun mixedFrameDelaysKeepSourceFrameOrdering() {
        val raw = listOf(1, 1, 20, 1, 20)
        val frames = GifFrameTiming.videoCompatibleTimeline(raw)
        val indices = GifFrameTiming.sourceIndicesFor(raw, frames)
        assertEquals(GifFrameTiming.effectiveDurationMs(raw) / 10,
            frames.sumOf { it.delayCentiseconds })
        assertTrue(indices.toList().zipWithNext().all { (a, b) -> a <= b })
        assertTrue(indices.all { it in raw.indices })
    }

    @Test fun normalizationPreservesTimelineAndSafeDelays() {
        val samples = GifFrameTiming.samples(2400)
        assertEquals(60, samples.size)
        assertEquals(240, samples.sumOf { it.delayCentiseconds })
        assertTrue(samples.all { it.delayCentiseconds >= 2 })
        assertEquals(0, samples.first().timeMs)
    }

    @Test fun oddDurationsDoNotAccumulateExtraTime() {
        for (duration in listOf(75, 999, 1234, 2380, 4000, 9000)) {
            val samples = GifFrameTiming.samples(duration)
            val expected = ((duration + 5) / 10).coerceAtLeast(4)
            assertEquals(expected, samples.sumOf { it.delayCentiseconds })
            assertTrue(samples.zipWithNext().all { it.first.timeMs < it.second.timeMs })
        }
    }

    @Test fun rejectsNonGifEvenIfFileNameSaysGif() {
        val pngHeader = byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47)
        assertTrue(GifFrameTiming.delaysCentiseconds(pngHeader).isEmpty())
    }
}
