package com.barccelo.stickit

import android.graphics.Bitmap
import com.bumptech.glide.gifdecoder.GifDecoder
import com.bumptech.glide.gifdecoder.GifHeaderParser
import com.bumptech.glide.gifdecoder.StandardGifDecoder
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer

/**
 * Normalize source GIF playback before sharing through WhatsApp's GIF paste
 * workflow. Source frame timing is read from actual GIF blocks, instead of
 * Glide's browser-compatible 100ms fallback for 0/10ms frames.
 */
internal object GifImportNormalizer {
    private class BitmapProvider : GifDecoder.BitmapProvider {
        override fun obtain(width: Int, height: Int, config: Bitmap.Config): Bitmap =
            Bitmap.createBitmap(width, height, config)

        override fun release(bitmap: Bitmap) {
            if (!bitmap.isRecycled) bitmap.recycle()
        }

        override fun obtainByteArray(size: Int): ByteArray = ByteArray(size)
        override fun release(bytes: ByteArray) = Unit
        override fun obtainIntArray(size: Int): IntArray = IntArray(size)
        override fun release(array: IntArray) = Unit
    }

    fun normalize(
        input: ByteArray,
        destination: File,
        squarePixels: (Bitmap) -> IntArray
    ) {
        val byteBuffer = ByteBuffer.wrap(input).asReadOnlyBuffer()
        val header = GifHeaderParser().setData(byteBuffer).parseHeader()
        require(header.frameCount > 0) { "El GIF no contiene fotogramas válidos" }

        val delaysCs = GifFrameTiming.delaysCentiseconds(input)
        require(delaysCs.size == header.frameCount) {
            "No se pudieron interpretar los tiempos del GIF"
        }
        val effectiveDelaysMs = delaysCs.map { it.coerceAtLeast(2) * 10 }
        val duration = GifFrameTiming.effectiveDurationMs(delaysCs)
        require(duration <= 10000) {
            "El GIF dura más de 10 segundos. Recórtalo antes de importarlo como sticker."
        }
        val samples = GifFrameTiming.samples(duration)
        val decoder = StandardGifDecoder(BitmapProvider(), header, ByteBuffer.wrap(input))
        try {
            var sourceStart = 0
            var sampleCursor = 0
            var wrote = 0
            FileOutputStream(destination).use { stream ->
                val writer = GifWriter(stream)
                for (frameIndex in 0 until header.frameCount) {
                    decoder.advance()
                    // Decoding every frame is required for disposal/composition,
                    // even if the frame is skipped by the target 25 FPS sampler.
                    val bitmap = decoder.nextFrame
                        ?: throw IllegalArgumentException("No se pudo decodificar un fotograma del GIF")
                    try {
                        val end = sourceStart + effectiveDelaysMs[frameIndex]
                        var first = sampleCursor
                        while (sampleCursor < samples.size && samples[sampleCursor].timeMs < end) {
                            sampleCursor++
                        }
                        if (sampleCursor > first) {
                            val pixels = squarePixels(bitmap)
                            while (first < sampleCursor) {
                                writer.frame(pixels, samples[first].delayCentiseconds)
                                wrote++
                                first++
                            }
                        }
                        sourceStart = end
                    } finally {
                        bitmap.recycle()
                    }
                }
                require(wrote == samples.size) { "No se generaron todos los fotogramas del GIF" }
                writer.finish()
            }
        } finally {
            decoder.clear()
        }
    }
}
