package com.barccelo.stickit

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.RectF
import android.graphics.Rect
import com.bumptech.glide.gifdecoder.GifDecoder
import com.bumptech.glide.gifdecoder.GifHeaderParser
import com.bumptech.glide.gifdecoder.StandardGifDecoder
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlin.math.min

data class ArtworkEdits(
    val caption: String = "",
    val zoom: Float = 1f,
    val rotate: Int = 0,
    val mirror: Boolean = false,
    val captionSize: Float = 18f,
    val crop: StickerCropArea = StickerCropArea()
)

/**
 * Non-destructive GIF artwork editor. All frames receive the same crop,
 * transform, and caption. Preserve animation cadence and keep one undo copy.
 */
object StickerArtworkEditor {
    private class Provider : GifDecoder.BitmapProvider {
        override fun obtain(width: Int, height: Int, config: Bitmap.Config): Bitmap =
            Bitmap.createBitmap(width, height, config)
        override fun release(bitmap: Bitmap) { if (!bitmap.isRecycled) bitmap.recycle() }
        override fun obtainByteArray(size: Int) = ByteArray(size)
        override fun release(bytes: ByteArray) = Unit
        override fun obtainIntArray(size: Int) = IntArray(size)
        override fun release(array: IntArray) = Unit
    }

    fun apply(file: File, changes: ArtworkEdits) {
        require(changes.zoom in 1f..2.5f && changes.rotate % 90 == 0 &&
            changes.crop.valid())
        require(file.exists() && file.length() <= 25_000_000) { "Archivo GIF no válido" }
        // Opening the editor and pressing Save with no visual changes must
        // never decode/re-encode the file. This protects precise frame timing,
        // colors and the original source from unnecessary lossy generations.
        if (changes.caption.isBlank() && changes.zoom == 1f &&
            changes.rotate == 0 && !changes.mirror &&
            changes.crop == StickerCropArea()) return
        val input = file.readBytes()
        val header = GifHeaderParser().setData(ByteBuffer.wrap(input)).parseHeader()
        val delays = GifFrameTiming.delaysCentiseconds(input)
        require(header.numFrames in 1..350 && delays.size == header.numFrames) {
            "No se puede editar esta animación"
        }
        // Older saved GIFs may contain 20-ms frames. Do not propagate those
        // player-dependent delays when actually editing their pixels.
        // Retain the original cadence for files already using safe delays.
        val outputFrames = if (delays.all { it >= 4 }) {
            delays.mapIndexed { index, delay ->
                WebPFrameTiming.OutputFrame(index, delay)
            }
        } else {
            var endMs = 0L
            val ends = delays.map { delay ->
                endMs += GifFrameTiming.sourceDelayMs(delay).toLong()
                require(endMs <= WebPFrameTiming.MAX_DURATION_MS) {
                    "La animación editada supera 30 segundos"
                }
                endMs.toInt()
            }
            WebPFrameTiming.plan(ends)
        }
        val tmp = File(file.parentFile, file.nameWithoutExtension + ".editing.gif")
        val undo = File(file.parentFile, file.nameWithoutExtension + ".unedited.gif")
        val decoder = StandardGifDecoder(Provider(), header, ByteBuffer.wrap(input))
        try {
            FileOutputStream(tmp).use { output ->
                val writer = GifWriter(output)
                var outputIndex = 0
                for (i in 0 until header.numFrames) {
                    decoder.advance()
                    val frame = decoder.nextFrame ?: error("No se pudo leer el fotograma")
                    try {
                        if (outputIndex < outputFrames.size &&
                            outputFrames[outputIndex].sourceIndex == i) {
                            val pixels = render(frame, changes)
                            while (outputIndex < outputFrames.size &&
                                outputFrames[outputIndex].sourceIndex == i) {
                                writer.frame(pixels,
                                    outputFrames[outputIndex].delayCentiseconds)
                                outputIndex++
                            }
                        }
                    } finally { frame.recycle() }
                }
                require(outputIndex == outputFrames.size) {
                    "No se generaron todos los fotogramas de la edición"
                }
                writer.finish()
            }
            require(tmp.length() > 40)
            if (!undo.exists()) file.copyTo(undo)
            try {
                Files.move(tmp.toPath(), file.toPath(),
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            decoder.clear()
            tmp.delete()
        }
    }

    fun canUndo(file: File): Boolean =
        File(file.parentFile, file.nameWithoutExtension + ".unedited.gif").exists()

    fun undo(file: File) {
        val saved = File(file.parentFile, file.nameWithoutExtension + ".unedited.gif")
        require(saved.exists()) { "No existe una edición anterior" }
        try {
            Files.move(saved.toPath(), file.toPath(),
                StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
            Files.move(saved.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun render(input: Bitmap, settings: ArtworkEdits): IntArray {
        val size = 256
        val stage = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        try {
            val stageCanvas = Canvas(stage)
            stageCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
            stageCanvas.save()
            stageCanvas.translate(size / 2f, size / 2f)
            if (settings.mirror) stageCanvas.scale(-1f, 1f)
            stageCanvas.rotate(settings.rotate.toFloat())
            val fitted = min(size.toFloat() / input.width, size.toFloat() / input.height) *
                settings.zoom
            val w = input.width * fitted
            val h = input.height * fitted
            stageCanvas.drawBitmap(input, null,
                RectF(-w / 2, -h / 2, w / 2, h / 2),
                Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
            stageCanvas.restore()

            // A real crop: select a rectangle in the original square
            // composition, then fit the selected pixels to the sticker canvas.
            // Preserve aspect ratio and transparency instead of stretching.
            val region = settings.crop
            val left = (region.left * size).toInt().coerceIn(0, size - 1)
            val top = (region.top * size).toInt().coerceIn(0, size - 1)
            val right = kotlin.math.ceil(region.right * size).toInt()
                .coerceIn(left + 1, size)
            val bottom = kotlin.math.ceil(region.bottom * size).toInt()
                .coerceIn(top + 1, size)
            val sourceW = (right - left).toFloat()
            val sourceH = (bottom - top).toFloat()
            val fit = min(size / sourceW, size / sourceH)
            val outputW = sourceW * fit
            val outputH = sourceH * fit
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
            canvas.drawBitmap(stage, Rect(left, top, right, bottom),
                RectF((size - outputW) / 2, (size - outputH) / 2,
                    (size + outputW) / 2, (size + outputH) / 2),
                Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
            val caption = settings.caption.trim().take(100)
            if (caption.isNotEmpty()) {
                val sizeText = settings.captionSize.coerceIn(14f, 30f)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    textSize = sizeText
                    typeface = android.graphics.Typeface.create(
                        android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
                }
                val words = caption.split(' ')
                val lines = mutableListOf<String>()
                var current = ""
                words.forEach { word ->
                    val test = if (current.isEmpty()) word else "$current $word"
                    if (paint.measureText(test) > 222 && current.isNotBlank()) {
                        lines.add(current); current = word
                    } else current = test
                }
                if (current.isNotBlank()) lines.add(current)
                val display = lines.takeLast(3)
                val height = (display.size * (sizeText + 6) + 16).coerceAtMost(120f)
                val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(165, 0, 0, 0) }
                canvas.drawRoundRect(RectF(8f, 256f - height - 8f, 248f, 248f), 16f, 16f, bg)
                paint.color = Color.WHITE
                paint.setShadowLayer(1.5f, 0f, 1f, Color.BLACK)
                display.forEachIndexed { idx, line ->
                    val left = (256f - paint.measureText(line)) / 2
                    canvas.drawText(line, left, 256f - height + 15f + idx * (sizeText + 6), paint)
                }
            }
            return IntArray(size * size).also {
                bitmap.getPixels(it, 0, size, 0, 0, size, size)
            }
        } finally {
            stage.recycle()
            bitmap.recycle()
        }
    }
}
