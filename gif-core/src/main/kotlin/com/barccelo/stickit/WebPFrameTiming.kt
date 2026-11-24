package com.barccelo.stickit

/**
 * WhatsApp-friendly animated WebP -> GIF frame plan.
 *
 * libwebp exposes the END timestamp of each input frame (not its start).
 * GIF players do not agree on very short delays (20 ms or less); some
 * substantially lengthen those frames. Instead of exporting 20 ms frames,
 * resample visual frames onto a 25 fps-equivalent output timeline, preserving
 * original wall-clock duration. For longer animations, lower output fps to
 * remain below the 350-frame memory and file-size bound.
 */
object WebPFrameTiming {
    const val MAX_DURATION_MS = 30_000
    const val MAX_SOURCE_FRAMES = 5_000
    private const val MAX_OUTPUT_FRAMES = 350
    private const val MIN_SAFE_DELAY_CS = 4

    data class OutputFrame(val sourceIndex: Int, val delayCentiseconds: Int)

    fun plan(frameEndTimesMs: List<Int>): List<OutputFrame> {
        require(frameEndTimesMs.isNotEmpty()) { "El WebP no contiene fotogramas" }
        require(frameEndTimesMs.size <= MAX_SOURCE_FRAMES) {
            "La animación contiene demasiados fotogramas"
        }
        var previous = 0
        frameEndTimesMs.forEach { end ->
            require(end >= previous) { "Los tiempos de los fotogramas están desordenados" }
            previous = end
        }

        val duration = previous
        require(duration > 0) { "La animación no tiene duración válida" }
        require(duration <= MAX_DURATION_MS) {
            "La animación supera 30 segundos. No se recortará sin autorización."
        }

        // GIF stores centiseconds. Round the WHOLE animation once, to avoid
        // accumulating a rounding error at each individual input frame.
        // Very short animations require a single safe frame.
        val totalCs = ((duration.toLong() + 5L) / 10L).toInt()
            .coerceAtLeast(MIN_SAFE_DELAY_CS)
        val frameCount = (totalCs / MIN_SAFE_DELAY_CS)
            .coerceIn(1, MAX_OUTPUT_FRAMES)

        val output = ArrayList<OutputFrame>(frameCount)
        var sourceIndex = 0
        for (i in 0 until frameCount) {
            // Determine which input frame is visible at the corresponding
            // original timeline time (left-inclusive, right-exclusive).
            val inputTimeMs = (i.toLong() * duration / frameCount).toInt()
            while (sourceIndex < frameEndTimesMs.lastIndex &&
                frameEndTimesMs[sourceIndex] <= inputTimeMs) {
                sourceIndex++
            }
            val startCs = (i.toLong() * totalCs / frameCount).toInt()
            val endCs = ((i + 1L) * totalCs / frameCount).toInt()
            val delay = endCs - startCs
            require(delay >= MIN_SAFE_DELAY_CS) {
                "La temporización del GIF no es compatible"
            }
            output.add(OutputFrame(sourceIndex, delay))
        }
        check(output.sumOf { it.delayCentiseconds } == totalCs)
        return output
    }
}
