package com.barccelo.stickit

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.media.MediaMetadataRetriever
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.min

object GifConverter {
    /**
     * Import media and validate the first decoded frame before adding it
     * to the library. Every saved item is a real GIF (not a renamed image).
     */
    fun import(context: Context, uri: Uri): File {
        val folder = File(context.filesDir, "stickers").apply { mkdirs() }
        val destination = File(folder, "${UUID.randomUUID()}.gif")
        try {
            val mime = context.contentResolver.getType(uri).orEmpty()
            val isGif = mime == "image/gif" || uri.toString().substringBefore('?').endsWith(".gif", true)
            if (isGif) {
                context.contentResolver.openInputStream(uri).use { input ->
                    requireNotNull(input) { "No se pudo leer el GIF" }
                    FileOutputStream(destination).use { output ->
                        // Limit even untrusted GIF files to a reasonable size while streaming.
                        val buffer = ByteArray(8192)
                        var total = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            total += read
                            require(total <= 20_000_000) { "GIF demasiado grande" }
                            output.write(buffer, 0, read)
                        }
                    }
                }
            } else if (mime.startsWith("video/")) {
                videoToGif(context, uri, destination)
            } else {
                imageToGif(context, uri, destination)
            }
            validate(destination)
            return destination
        } catch (error: Exception) {
            destination.delete()
            throw error
        }
    }

    private fun squarePixels(bitmap: Bitmap, size: Int = 256): IntArray {
        val square = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(square)
        canvas.drawColor(Color.TRANSPARENT, android.graphics.PorterDuff.Mode.CLEAR)
        val scale = min(size.toFloat() / bitmap.width, size.toFloat() / bitmap.height)
        val width = bitmap.width * scale
        val height = bitmap.height * scale
        canvas.drawBitmap(
            bitmap,
            Rect(0, 0, bitmap.width, bitmap.height),
            RectF((size - width) / 2, (size - height) / 2, (size + width) / 2, (size + height) / 2),
            Paint(Paint.FILTER_BITMAP_FLAG)
        )
        val pixels = IntArray(size * size)
        square.getPixels(pixels, 0, size, 0, 0, size, size)
        square.recycle()
        return pixels
    }

    private fun imageToGif(context: Context, uri: Uri, destination: File) {
        val image = ImageDecoder.decodeBitmap(
            ImageDecoder.createSource(context.contentResolver, uri)
        ) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val largest = maxOf(info.size.width, info.size.height)
            if (largest > 1024) decoder.setTargetSampleSize((largest / 1024).coerceAtLeast(1))
        }
        try {
            val pixels = squarePixels(image)
            FileOutputStream(destination).use { output ->
                val writer = GifWriter(output)
                writer.frame(pixels, 10)
                writer.frame(pixels, 10)
                writer.finish()
            }
        } finally {
            image.recycle()
        }
    }

    private fun videoToGif(context: Context, uri: Uri, destination: File) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 1500L
            val usable = min(duration, 2400L).coerceAtLeast(100L)
            val interval = 160L
            val frameCount = ((usable + interval - 1) / interval).toInt().coerceIn(1, 15)
            FileOutputStream(destination).use { output ->
                val gif = GifWriter(output)
                var wrote = 0
                for (index in 0 until frameCount) {
                    val bitmap = retriever.getFrameAtTime(index * interval * 1000,
                        MediaMetadataRetriever.OPTION_CLOSEST)
                    if (bitmap != null) {
                        try {
                            gif.frame(squarePixels(bitmap), 16)
                        } finally { bitmap.recycle() }
                        wrote++
                    }
                }
                require(wrote > 0) { "El vídeo no contiene fotogramas legibles" }
                gif.finish()
            }
        } finally {
            retriever.release()
        }
    }

    private fun validate(file: File) {
        require(file.length() > 40) { "El GIF está vacío" }
        val decoded = try {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, _, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } catch (error: Exception) {
            throw IllegalArgumentException("El GIF generado no se puede abrir", error)
        }
        try {
            val width = decoded.width
            val height = decoded.height
            require(width > 0 && height > 0) { "El GIF no contiene imagen" }
            val row = IntArray(width)
            var visible = false
            var y = 0
            while (y < height && !visible) {
                decoded.getPixels(row, 0, width, 0, y, width, 1)
                visible = row.any { (it ushr 24) >= 32 }
                y++
            }
            require(visible) { "El GIF aparece transparente o en blanco. No se guardó." }
        } finally {
            decoded.recycle()
        }
    }
}
