package com.barccelo.stickit

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.media.MediaMetadataRetriever
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.min

object GifConverter {
    /**
     * All content is stored as .gif. Source is opened using Android's URI grants.
     * GIF inputs are kept byte-for-byte; still images become two-frame GIFs.
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
                        val copied = input.copyTo(output)
                        require(copied in 1..20_000_000) { "GIF demasiado grande" }
                    }
                }
            } else if (mime.startsWith("video/")) {
                videoToGif(context, uri, destination)
            } else {
                imageToGif(context, uri, destination)
            }
            require(destination.length() > 20) { "No se pudo generar el GIF" }
            return destination
        } catch (error: Exception) {
            destination.delete()
            throw error
        }
    }

    private fun imageToGif(context: Context, uri: Uri, destination: File) {
        val image = ImageDecoder.decodeBitmap(
            ImageDecoder.createSource(context.contentResolver, uri)
        ) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val largest = maxOf(info.size.width, info.size.height)
            if (largest > 1024) decoder.setTargetSampleSize((largest / 1024).coerceAtLeast(1))
        }
        FileOutputStream(destination).use { output ->
            val gif = GifWriter(output)
            gif.frame(image, 10)
            gif.frame(image, 10)
            gif.finish()
        }
        image.recycle()
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
                        gif.frame(bitmap, 16)
                        bitmap.recycle()
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
}
