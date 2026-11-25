package com.barccelo.stickit

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.aureusapps.android.webpandroid.decoder.WebPDecoder
import java.io.File
import java.io.FileOutputStream

/**
 * Full-duration animated WebP -> GIF conversion.
 *
 * IMPORTANT: WebPDecoder's JNI layer reuses a single bitmap_frame_ for all
 * decoded frames. The Bitmap returned from decodeNextFrame() is decoder-owned.
 * NEVER call Bitmap.recycle() on it: doing so invalidates native subsequent
 * frame copies and makes sharing animated stickers fail.
 *
 * The two passes extract exact cumulative end timestamps and then encode
 * frame content. No source frame is stored or recycled by this class.
 */
/**
 * Explicit A/B modes preserve the original source and allow the user to
 * compare WhatsApp's treatment of long GIFs. The short mode intentionally
 * clips and is only a diagnostic reference to the formerly working export.
 */
enum class WebPPlaybackProfile {
    WHATSAPP_COMPAT, HIGH_FPS, SHORT_REFERENCE, FULL_REFERENCE_RHYTHM,
    LIGHT_FULL, EXTRA_LIGHT_FULL
}

internal object WebPStickerImport {
    fun convert(
        context: Context,
        uri: Uri,
        destination: File,
        square: (Bitmap, Int) -> IntArray,
        profile: WebPPlaybackProfile = WebPPlaybackProfile.LIGHT_FULL
    ) {
        val decoder = WebPDecoder(context)
        try {
            decoder.setDataSource(uri)
            val info = decoder.decodeInfo()
            require(info.width in 1..4096 && info.height in 1..4096) {
                "Dimensiones WebP no admitidas"
            }

            // Pass 1: read full source timestamps (end-of-frame milliseconds).
            // Native decoder retains/reuses its Bitmap: do NOT recycle it.
            val frameEndTimes = ArrayList<Int>()
            while (decoder.hasNextFrame()) {
                require(frameEndTimes.size < WebPFrameTiming.MAX_SOURCE_FRAMES) {
                    "La animación contiene demasiados fotogramas"
                }
                val result = decoder.decodeNextFrame()
                requireNotNull(result.frame) {
                    "No se pudo leer un fotograma del WebP"
                }
                frameEndTimes.add(result.timestamp)
                require(result.timestamp <= WebPFrameTiming.MAX_DURATION_MS) {
                    "La animación supera 30 segundos. No se recortará."
                }
            }

            val frames = when (profile) {
                WebPPlaybackProfile.WHATSAPP_COMPAT ->
                    WebPFrameTiming.plan(frameEndTimes,
                        WebPFrameTiming.COMPAT_FRAME_CAP)
                WebPPlaybackProfile.HIGH_FPS ->
                    WebPFrameTiming.plan(frameEndTimes,
                        WebPFrameTiming.SMOOTH_FRAME_CAP)
                WebPPlaybackProfile.SHORT_REFERENCE ->
                    WebPFrameTiming.plan(frameEndTimes,
                        WebPFrameTiming.COMPAT_FRAME_CAP,
                        WebPFrameTiming.SHORT_REFERENCE_MS)
                WebPPlaybackProfile.FULL_REFERENCE_RHYTHM,
                WebPPlaybackProfile.LIGHT_FULL,
                WebPPlaybackProfile.EXTRA_LIGHT_FULL ->
                    WebPFrameTiming.fullAtReferenceCadence(frameEndTimes)
            }
            // Smaller frames lower GIF decode and transfer load without
            // changing the number, timestamps or duration of source samples.
            val isCompact = profile == WebPPlaybackProfile.LIGHT_FULL ||
                profile == WebPPlaybackProfile.EXTRA_LIGHT_FULL
            val duration = frameEndTimes.last()
            val size = when (profile) {
                WebPPlaybackProfile.LIGHT_FULL -> when {
                    duration <= 6_000 -> 160
                    duration <= 12_000 -> 144
                    else -> 128
                }
                WebPPlaybackProfile.EXTRA_LIGHT_FULL ->
                    if (duration <= 12_000) 112 else 96
                else -> 256
            }
            decoder.reset()

            // Pass 2: use the same decoder-owned reusable bitmap. The
            // square() converter makes and recycles only its OWN bitmap.
            FileOutputStream(destination).use { output ->
                val gif = GifWriter(output, size, cropTransparentBounds = isCompact)
                var sourceIndex = 0
                var written = 0
                while (decoder.hasNextFrame() && written < frames.size) {
                    val result = decoder.decodeNextFrame()
                    val bitmap = result.frame
                        ?: throw IllegalArgumentException(
                            "No se pudo leer un fotograma al convertir el WebP"
                        )
                    if (sourceIndex == frames[written].sourceIndex) {
                        // A low-fps WebP may require several 40-ms output
                        // frames to preserve the same wall-clock duration.
                        // Reuse the decoded pixels before advancing libwebp.
                        val pixels = square(bitmap, size)
                        while (written < frames.size &&
                            frames[written].sourceIndex == sourceIndex) {
                            gif.frame(pixels, frames[written].delayCentiseconds)
                            written++
                        }
                    }
                    sourceIndex++
                }
                require(written == frames.size) {
                    "No se pudo completar la animación WebP"
                }
                gif.finish()
            }
        } finally {
            // Frees native decoder and its owned bitmap/buffer once.
            decoder.release()
        }
    }
}
