package com.barccelo.stickit

import android.content.Context
import android.net.Uri
import com.aureusapps.android.webpandroid.decoder.WebPDecoder
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min

/**
 * Decode native animated WebP frames into the same optimized GIF transport
 * used for WhatsApp paste. Retain the original WebP alongside the GIF.
 *
 * This avoids flattening a WhatsApp animated sticker to its first frame.
 */
internal object WebPStickerImport {
    fun convert(context: Context, uri: Uri, destination: File, square: (android.graphics.Bitmap) -> IntArray) {
        val decoder = WebPDecoder(context)
        try {
            decoder.setDataSource(uri)
            val info = decoder.decodeInfo()
            require(info.width in 1..4096 && info.height in 1..4096) { "Dimensiones WebP no admitidas" }
            val selectedFrames = ArrayList<Pair<Int, IntArray>>(62)
            var previousTime = -40
            var lastTime = 0
            var decoded = 0
            while (decoder.hasNextFrame()) {
                val result = decoder.decodeNextFrame()
                val bitmap = result.frame ?: break
                val time = result.timestamp.coerceAtLeast(0)
                if (time >= previousTime + 40 || selectedFrames.isEmpty()) {
                    selectedFrames.add(time to square(bitmap))
                    previousTime = time
                }
                lastTime = max(time, lastTime)
                decoded++
                if (lastTime >= 2400 || decoded >= 400) break
            }
            require(selectedFrames.isNotEmpty()) { "No se pudieron decodificar fotogramas WebP" }
            val impliedFrameMs = if (selectedFrames.size > 1) {
                (selectedFrames.last().first - selectedFrames[selectedFrames.lastIndex - 1].first)
                    .coerceIn(20, 100)
            } else 100
            val duration = (lastTime + impliedFrameMs).coerceIn(100, 2400)
            val timeline = GifTimeline.samples(duration.toLong(), 25.0)
            FileOutputStream(destination).use { output ->
                val gif = GifWriter(output)
                var cursor = 0
                for (sample in timeline) {
                    val target = (sample.presentationTimeUs / 1000).toInt()
                    while (cursor + 1 < selectedFrames.size && selectedFrames[cursor + 1].first <= target) cursor++
                    gif.frame(selectedFrames[cursor].second, sample.delayCentiseconds)
                }
                gif.finish()
            }
        } finally {
            decoder.release()
        }
    }
}
