package com.barccelo.stickit

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.aureusapps.android.webpandroid.decoder.WebPDecoder
import java.io.File
import java.io.FileOutputStream

/**
 * Convert an animated WebP into a WhatsApp-compatible GIF without changing
 * the original animation speed or silently cutting off the last frames.
 *
 * libwebp reports the END timestamp of each frame. The older converter treated
 * these as presentation starts and also stopped decoding at 2,400 ms.
 *
 * We decode timestamps first, plan accurate cumulative GIF delays and then
 * decode the frames again. This keeps memory bounded: only one Android bitmap
 * and one 256x256 ARGB frame are held at a time, even for longer animations.
 */
internal object WebPStickerImport {
    fun convert(
        context: Context,
        uri: Uri,
        destination: File,
        square: (Bitmap) -> IntArray
    ) {
        val decoder = WebPDecoder(context)
        try {
            decoder.setDataSource(uri)
            val info = decoder.decodeInfo()
            require(info.width in 1..4096 && info.height in 1..4096) {
                "Dimensiones WebP no admitidas"
            }

            val frameEndTimes = ArrayList<Int>()
            while (decoder.hasNextFrame()) {
                require(frameEndTimes.size < WebPFrameTiming.MAX_SOURCE_FRAMES) {
                    "La animación contiene demasiados fotogramas"
                }
                val result = decoder.decodeNextFrame()
                val bitmap = result.frame
                    ?: throw IllegalArgumentException(
                        "No se pudo leer un fotograma del WebP")
                try {
                    frameEndTimes.add(result.timestamp)
                    require(result.timestamp <= WebPFrameTiming.MAX_DURATION_MS) {
                        "La animación supera 30 segundos. No se recortará."
                    }
                } finally {
                    if (!bitmap.isRecycled) bitmap.recycle()
                }
            }
            val frames = WebPFrameTiming.plan(frameEndTimes)
            decoder.reset()

            FileOutputStream(destination).use { output ->
                val gif = GifWriter(output)
                var sourceIndex = 0
                var written = 0

                while (decoder.hasNextFrame() && written < frames.size) {
                    val result = decoder.decodeNextFrame()
                    val bitmap = result.frame
                        ?: throw IllegalArgumentException(
                            "No se pudo leer un fotograma al convertir el WebP")
                    try {
                        if (sourceIndex == frames[written].sourceIndex) {
                            gif.frame(
                                square(bitmap),
                                frames[written].delayCentiseconds
                            )
                            written++
                        }
                    } finally {
                        if (!bitmap.isRecycled) bitmap.recycle()
                    }
                    sourceIndex++
                }
                require(written == frames.size) {
                    "No se pudo completar la animación WebP"
                }
                gif.finish()
            }
        } finally {
            decoder.release()
        }
    }
}
