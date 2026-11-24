package com.barccelo.stickit

import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * GIF uses 10 ms units. Sample frames along the real source timeline and
 * distribute rounding error between frames instead of altering total duration.
 */
object GifTimeline {
    data class Frame(val presentationTimeUs: Long, val delayCentiseconds: Int)

    fun samples(durationMs: Long, sourceFps: Double? = null): List<Frame> {
        require(durationMs > 0) { "Video duration must be positive" }
        val fps = sourceFps?.takeIf { it.isFinite() && it >= 2.0 }
            ?.coerceIn(2.0, 25.0) ?: 25.0
        val totalCentiseconds = ((durationMs + 5) / 10).coerceAtLeast(2)
        val requested = ceil(durationMs * fps / 1000.0).toInt()
        // No frame is shorter than 20 ms; GIF decoders often clamp those.
        val frameCount = requested.coerceIn(1, minOf(75, (totalCentiseconds / 2).toInt().coerceAtLeast(1)))
        return List(frameCount) { index ->
            val startCs = ((index * totalCentiseconds.toDouble()) / frameCount).roundToInt()
            val endCs = (((index + 1) * totalCentiseconds.toDouble()) / frameCount).roundToInt()
            val delay = endCs - startCs
            val sampleTimeUs = (index.toDouble() * durationMs / frameCount * 1000.0).toLong()
            Frame(sampleTimeUs, delay.coerceAtLeast(2))
        }
    }
}
