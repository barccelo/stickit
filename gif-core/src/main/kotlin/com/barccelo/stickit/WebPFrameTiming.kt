package com.barccelo.stickit

/**
 * Convert libwebp cumulative end timestamps to a GIF playback timeline.
 *
 * Stickit copies GIF files into WhatsApp, which can handle a long/heavy GIF
 * differently from its native animated WebP stickers. Do not assume that
 * 25 FPS and hundreds of unique GIF frames are safe for every recipient.
 *
 * The default profile caps the number of frames at 60 (the successful short
 * export used this bound) while preserving the WHOLE animation duration.
 * Higher-fidelity and short reference profiles are available for testing.
 * No arbitrary playback-speed multiplier is applied.
 */
object WebPFrameTiming {
    const val MAX_DURATION_MS = 30_000
    const val MAX_SOURCE_FRAMES = 5_000
    const val COMPAT_FRAME_CAP = 60
    const val SMOOTH_FRAME_CAP = 350
    const val SHORT_REFERENCE_MS = 2_400
    private const val MIN_SAFE_DELAY_CS = 4

    data class OutputFrame(val sourceIndex: Int, val delayCentiseconds: Int)

    fun plan(
        frameEndTimesMs: List<Int>,
        maxOutputFrames: Int = COMPAT_FRAME_CAP,
        maxDurationMs: Int = MAX_DURATION_MS
    ): List<OutputFrame> {
        require(frameEndTimesMs.isNotEmpty()) { "El WebP no contiene fotogramas" }
        require(frameEndTimesMs.size <= MAX_SOURCE_FRAMES) {
            "La animación contiene demasiados fotogramas"
        }
        require(maxOutputFrames in 1..SMOOTH_FRAME_CAP)
        require(maxDurationMs in 40..MAX_DURATION_MS)
        var previous = 0
        frameEndTimesMs.forEach { end ->
            require(end >= previous) { "Los tiempos de los fotogramas están desordenados" }
            previous = end
        }
        require(previous > 0) { "La animación no tiene duración válida" }
        require(previous <= MAX_DURATION_MS) {
            "La animación supera 30 segundos. No se recortará sin autorización."
        }
        val duration = minOf(previous, maxDurationMs)
        // Round the full duration once; no per-frame rounding accumulation.
        val totalCs = ((duration.toLong() + 5L) / 10L).toInt()
            .coerceAtLeast(MIN_SAFE_DELAY_CS)
        val frameCount = (totalCs / MIN_SAFE_DELAY_CS)
            .coerceIn(1, maxOutputFrames)

        val plan = ArrayList<OutputFrame>(frameCount)
        var sourceIndex = 0
        for (i in 0 until frameCount) {
            // Timestamp belongs to original source playback, not to the
            // potentially shorter reference GIF's previous output frames.
            val timeMs = (i.toLong() * duration / frameCount).toInt()
            while (sourceIndex < frameEndTimesMs.lastIndex &&
                frameEndTimesMs[sourceIndex] <= timeMs) sourceIndex++

            val startCs = (i.toLong() * totalCs / frameCount).toInt()
            val endCs = ((i + 1L) * totalCs / frameCount).toInt()
            val delay = endCs - startCs
            require(delay >= MIN_SAFE_DELAY_CS)
            if (plan.lastOrNull()?.sourceIndex == sourceIndex) {
                // A frame can appear several times when source FPS < output
                // FPS. Encode it only once with combined delay; identical
                // GIF frames waste bytes and WhatsApp decoder work.
                val last = plan.last()
                plan[plan.lastIndex] = last.copy(
                    delayCentiseconds = last.delayCentiseconds + delay)
            } else {
                plan.add(OutputFrame(sourceIndex, delay))
            }
        }
        check(plan.isNotEmpty())
        check(plan.all { it.delayCentiseconds in MIN_SAFE_DELAY_CS..65535 })
        check(plan.sumOf { it.delayCentiseconds } == totalCs)
        return plan
    }
}
