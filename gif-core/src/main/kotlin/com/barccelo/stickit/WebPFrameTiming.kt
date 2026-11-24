package com.barccelo.stickit

import kotlin.math.ceil

/**
 * libwebp returns the END timestamp of each decoded WebP animation frame,
 * in milliseconds. These timestamps are cumulative, not presentation starts.
 *
 * Preserve the original duration. Round cumulative boundaries to GIF's
 * 10-millisecond units and only merge frames that a GIF player would clamp
 * (shorter than 20 ms). Long clips retain their duration while the number
 * of encoded frames stays bounded to protect memory, battery and file size.
 */
object WebPFrameTiming {
    const val MAX_DURATION_MS = 30_000
    const val MAX_SOURCE_FRAMES = 5_000
    private const val MAX_OUTPUT_FRAMES = 350

    data class OutputFrame(val sourceIndex: Int, val delayCentiseconds: Int)

    fun plan(frameEndTimesMs: List<Int>): List<OutputFrame> {
        require(frameEndTimesMs.isNotEmpty()) { "El WebP no contiene fotogramas" }
        require(frameEndTimesMs.size <= MAX_SOURCE_FRAMES) {
            "La animación contiene demasiados fotogramas"
        }
        var previous = 0
        frameEndTimesMs.forEach { end ->
            require(end >= previous) {
                "Los tiempos de los fotogramas están desordenados"
            }
            previous = end
        }
        val duration = frameEndTimesMs.last()
        require(duration > 0) { "La animación no tiene duración válida" }
        require(duration <= MAX_DURATION_MS) {
            "La animación supera 30 segundos. No se recortará sin autorización."
        }

        val totalCs = ((duration.toLong() + 5L) / 10L)
            .toInt().coerceAtLeast(2)
        // Small animations retain every original frame lasting >=20 ms.
        // Longer animations may combine additional frames while preserving
        // total playback time rather than silently truncating at 2.4 seconds.
        val minimumCs = maxOf(2, ceil(totalCs.toDouble() / MAX_OUTPUT_FRAMES).toInt())
        val output = ArrayList<OutputFrame>()
        var cutAtCs = 0
        var pendingSourceIndex = 0

        frameEndTimesMs.forEachIndexed { index, endMs ->
            val endCs = ((endMs.toLong() + 5L) / 10L).toInt()
            if (endCs - cutAtCs >= minimumCs) {
                output.add(OutputFrame(pendingSourceIndex, endCs - cutAtCs))
                cutAtCs = endCs
                pendingSourceIndex = index + 1
            }
        }
        val remainder = totalCs - cutAtCs
        if (output.isEmpty()) {
            output.add(OutputFrame(0, totalCs))
        } else if (remainder > 0) {
            val last = output.last()
            output[output.lastIndex] = last.copy(
                delayCentiseconds = last.delayCentiseconds + remainder
            )
        }
        require(output.size <= MAX_OUTPUT_FRAMES)
        require(output.all { it.delayCentiseconds in 2..65535 })
        require(output.sumOf { it.delayCentiseconds } == totalCs)
        return output
    }
}
