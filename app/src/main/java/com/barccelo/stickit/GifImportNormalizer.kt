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
 * workflow. We deliberately use the same 25 fps, 2.4 s timeline as the
 * successful video import path, without lossy GIF -> MP4 -> GIF conversion.
 * Source frames are selected using their raw GIF timing; exported frames
 * always receive safe 40 ms delays (or appropriately rounded equivalents).
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
        require(header.numFrames > 0) { "El GIF no contiene fotogramas válidos" }

        val delaysCs = GifFrameTiming.delaysCentiseconds(input)
        require(delaysCs.size == header.numFrames) {
            "No se pudieron interpretar los tiempos del GIF"
        }
        // Use the identical sample cadence and clip length as MP4 -> GIF.
        // Distinct source GIF delays are only used to locate the source image;
        // they are NEVER reused for output playback durations.
        val timeline = GifFrameTiming.videoCompatibleTimeline(delaysCs)
        val sourceIndices = GifFrameTiming.sourceIndicesFor(delaysCs, timeline)
        val decoder = StandardGifDecoder(BitmapProvider(), header, ByteBuffer.wrap(input))
        try {
            var sampleCursor = 0
            FileOutputStream(destination).use { stream ->
                val writer = GifWriter(stream)
                for (frameIndex in 0..sourceIndices.last()) {
                    decoder.advance()
                    // Decode intermediate source frames too, because disposal
                    // and partial-frame overlays depend on previous frames.
                    val bitmap = decoder.nextFrame
                        ?: throw IllegalArgumentException("No se pudo decodificar un fotograma del GIF")
                    try {
                        if (sampleCursor < sourceIndices.size &&
                            sourceIndices[sampleCursor] == frameIndex) {
                            val pixels = squarePixels(bitmap)
                            while (sampleCursor < sourceIndices.size &&
                                sourceIndices[sampleCursor] == frameIndex) {
                                writer.frame(pixels, timeline[sampleCursor].delayCentiseconds)
                                sampleCursor++
                            }
                        }
                    } finally {
                        bitmap.recycle()
                    }
                }
                require(sampleCursor == timeline.size) {
                    "No se generaron todos los fotogramas del GIF"
                }
                writer.finish()
            }
        } finally {
            decoder.clear()
        }
    }
}
