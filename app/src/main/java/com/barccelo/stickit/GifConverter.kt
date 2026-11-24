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
            // Share providers may report application/octet-stream or hide the
            // extension. Detect the actual GIF/WebP signature first.
            val signature = context.contentResolver.openInputStream(uri).use { input ->
                val header = ByteArray(16)
                val n = input?.read(header) ?: 0
                header.copyOf(n.coerceAtLeast(0))
            }
            val isActualGif = signature.size >= 6 &&
                String(signature, 0, 6, Charsets.US_ASCII).let {
                    it == "GIF87a" || it == "GIF89a"
                }
            val isActualWebp = signature.size >= 12 &&
                String(signature, 0, 4, Charsets.US_ASCII) == "RIFF" &&
                String(signature, 8, 4, Charsets.US_ASCII) == "WEBP"
            val isGif = isActualGif
            val isWebp = isActualWebp
            if (isWebp) {
                val drawable = ImageDecoder.decodeDrawable(ImageDecoder.createSource(context.contentResolver, uri))
                if (drawable is android.graphics.drawable.AnimatedImageDrawable) {
                    WebPStickerImport.convert(context, uri, destination, ::squarePixels)
                } else {
                    imageToGif(context, uri, destination)
                }
                val original = File(folder, "${destination.nameWithoutExtension}.source.webp")
                context.contentResolver.openInputStream(uri).use { input ->
                    requireNotNull(input)
                    FileOutputStream(original).use { output ->
                        val buffer = ByteArray(8192)
                        var count = 0L
                        while (true) {
                            val size = input.read(buffer)
                            if (size < 0) break
                            count += size
                            require(count <= 20_000_000) { "WebP demasiado grande" }
                            output.write(buffer, 0, size)
                        }
                    }
                }
            } else if (isGif) {
                val original = context.contentResolver.openInputStream(uri).use { input ->
                    requireNotNull(input) { "No se pudo leer el GIF" }
                    val output = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        require(output.size().toLong() + read <= 20_000_000L) {
                            "GIF demasiado grande"
                        }
                        output.write(buffer, 0, read)
                    }
                    output.toByteArray()
                }
                if (GifFrameTiming.delaysCentiseconds(original).isNotEmpty()) {
                    GifImportNormalizer.normalize(original, destination, ::squarePixels)
                    // Keep the untouched GIF available if a future conversion
                    // algorithm is improved. It never replaces the export.
                    File(folder, "${destination.nameWithoutExtension}.source.gif").writeBytes(original)
                } else {
                    // Some providers label a PNG image as .gif. Detect content,
                    // rather than blindly copying the mislabeled file.
                    imageToGif(context, uri, destination)
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
            File(folder, "${destination.nameWithoutExtension}.source.gif").delete()
            File(folder, "${destination.nameWithoutExtension}.source.webp").delete()
            throw error
        }
    }

    /**
     * Rebuild a previously imported animated GIF from the retained original.
     * The database row and tags are preserved. The replacement is atomic:
     * a failed conversion leaves the existing sticker untouched.
     */
    fun reprocessStoredGif(file: File) {
        val backup = File(file.parentFile,
            "${file.nameWithoutExtension}.source.gif")
        require(backup.isFile) { "No se conserva el GIF original. Vuelve a importarlo." }
        val temporary = File(file.parentFile,
            "${file.nameWithoutExtension}.pending.gif")
        try {
            val bytes = backup.readBytes()
            require(bytes.size <= 20_000_000) { "El GIF original es demasiado grande" }
            GifImportNormalizer.normalize(bytes, temporary, ::squarePixels)
            validate(temporary)
            try {
                java.nio.file.Files.move(
                    temporary.toPath(), file.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE
                )
            } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                java.nio.file.Files.move(
                    temporary.toPath(), file.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING
                )
            }
        } finally {
            temporary.delete()
        }
    }

    /**
     * Rebuild an already imported WhatsApp animated WebP using the new,
     * source-faithful timing. The original .source.webp is untouched.
     * The database row, tags, pack and favorite remain unchanged.
     */
    fun reprocessStoredWebp(context: Context, file: File) {
        val source = File(file.parentFile,
            "${file.nameWithoutExtension}.source.webp")
        require(source.isFile) {
            "No se conserva el WebP original. Compártelo nuevamente desde WhatsApp."
        }
        require(source.length() in 1..20_000_000L) {
            "El WebP original es demasiado grande"
        }
        val temporary = File(file.parentFile,
            "${file.nameWithoutExtension}.pending.gif")
        try {
            val uri = androidx.core.content.FileProvider.getUriForFile(
                context, "${context.packageName}.fileprovider", source
            )
            WebPStickerImport.convert(context, uri, temporary, ::squarePixels)
            validate(temporary)
            try {
                java.nio.file.Files.move(
                    temporary.toPath(), file.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE
                )
            } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                java.nio.file.Files.move(
                    temporary.toPath(), file.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING
                )
            }
        } finally {
            temporary.delete()
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
            val knownFrames = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT
            )?.toLongOrNull()
            // Use the source frame rate when Android reports a reliable frame
            // count; otherwise sample at 25 FPS. Unlike the previous 6.25 FPS
            // export, this preserves motion and total animation duration.
            val sourceFps = knownFrames?.takeIf { it > 0L && duration > 0L }
                ?.let { it * 1000.0 / duration }
            val timeline = GifTimeline.samples(usable, sourceFps)
            FileOutputStream(destination).use { output ->
                val gif = GifWriter(output)
                var wrote = 0
                var lastPixels: IntArray? = null
                for (sample in timeline) {
                    val bitmap = retriever.getFrameAtTime(
                        sample.presentationTimeUs, MediaMetadataRetriever.OPTION_CLOSEST
                    )
                    val pixels = if (bitmap == null) lastPixels else try {
                        squarePixels(bitmap)
                    } finally {
                        bitmap.recycle()
                    }
                    if (pixels != null) {
                        gif.frame(pixels, sample.delayCentiseconds)
                        lastPixels = pixels
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
