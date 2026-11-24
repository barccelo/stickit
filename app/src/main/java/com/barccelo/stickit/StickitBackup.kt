package com.barccelo.stickit

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * User-controlled, offline-only library backup. Never writes to WhatsApp's
 * private files or requests storage-wide permissions.
 *
 * The archive includes every GIF and its original GIF/WebP when available.
 * Restores MERGE, never erase the current library, and skip duplicate bytes.
 */
object StickitBackup {
    private const val MAX_ARCHIVE_BYTES = 250L * 1024 * 1024
    private const val MAX_ENTRY_BYTES = 25L * 1024 * 1024
    private const val MAX_ENTRIES = 2500
    private const val MANIFEST = "stickit-manifest.json"

    private fun sha(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { source ->
            val buffer = ByteArray(8192)
            while (true) {
                val n = source.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun export(context: Context, store: StickerStore, destination: Uri): Int {
        val stickers = store.all()
        val manifest = JSONObject()
        manifest.put("format", "stickit-library")
        manifest.put("version", 1)
        manifest.put("createdAt", System.currentTimeMillis())
        val items = JSONArray()
        val media = ArrayList<Pair<String, File>>()
        val folder = File(context.filesDir, "stickers")
        stickers.forEach { sticker ->
            val source = store.stickerFile(sticker)
            if (!source.isFile) return@forEach
            val id = UUID.randomUUID().toString()
            val row = JSONObject()
            row.put("id", id)
            row.put("name", sticker.name)
            row.put("tags", sticker.tags)
            row.put("pack", sticker.pack)
            row.put("favorite", sticker.favorite)
            row.put("media", "media/$id.gif")
            row.put("sha256", sha(source))
            row.put("created", sticker.createdAt)
            row.put("lastUsed", sticker.lastUsed)
            row.put("uses", sticker.uses)
            media.add("media/$id.gif" to source)
            val stem = File(sticker.fileName).nameWithoutExtension
            for (extension in listOf("source.gif", "source.webp", "unedited.gif")) {
                val original = File(folder, "${stem}.${extension}")
                if (original.isFile) {
                    val key = extension.replace(".", "_")
                    val archiveName = "originals/$id.$extension"
                    row.put(key, archiveName)
                    media.add(archiveName to original)
                }
            }
            items.put(row)
        }
        manifest.put("items", items)
        require(media.size <= MAX_ENTRIES) { "La biblioteca supera el límite de respaldo" }
        context.contentResolver.openOutputStream(destination, "w").use { raw ->
            requireNotNull(raw) { "No se pudo abrir el archivo de respaldo" }
            ZipOutputStream(raw.buffered()).use { zip ->
                zip.putNextEntry(ZipEntry(MANIFEST))
                zip.write(manifest.toString().toByteArray(Charsets.UTF_8))
                zip.closeEntry()
                media.forEach { (name, file) ->
                    require(file.length() <= MAX_ENTRY_BYTES) { "Archivo demasiado grande: $name" }
                    zip.putNextEntry(ZipEntry(name))
                    FileInputStream(file).use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        }
        return items.length()
    }

    fun restore(context: Context, store: StickerStore, source: Uri): Int {
        val temporary = File(context.cacheDir, "restore-" + UUID.randomUUID()).apply { mkdirs() }
        val extracted = HashMap<String, File>()
        var manifest: JSONObject? = null
        var sum = 0L
        var count = 0
        val newIds = mutableSetOf<Long>()
        try {
            context.contentResolver.openInputStream(source).use { raw ->
                requireNotNull(raw) { "No se puede abrir el respaldo" }
                ZipInputStream(raw.buffered()).use { zip ->
                    while (true) {
                        val entry = zip.nextEntry ?: break
                        count++
                        require(count <= MAX_ENTRIES) { "El respaldo tiene demasiados archivos" }
                        val name = entry.name
                        require(!entry.isDirectory) { "Entrada no válida: $name" }
                        require(name == MANIFEST ||
                            Regex("^(media|originals)/[a-zA-Z0-9-]{36}\\.(gif|webp|source\\.gif|source\\.webp|unedited\\.gif)$").matches(name)
                        ) { "El respaldo contiene una ruta no permitida" }
                        val dest = File(temporary, UUID.randomUUID().toString())
                        var bytes = 0L
                        FileOutputStream(dest).use { output ->
                            val buffer = ByteArray(8192)
                            while (true) {
                                val n = zip.read(buffer)
                                if (n < 0) break
                                bytes += n
                                sum += n
                                require(bytes <= MAX_ENTRY_BYTES && sum <= MAX_ARCHIVE_BYTES) {
                                    "El respaldo excede los límites de seguridad"
                                }
                                output.write(buffer, 0, n)
                            }
                        }
                        if (name == MANIFEST) {
                            require(bytes < 4_000_000L) { "Índice del respaldo demasiado grande" }
                            require(manifest == null) { "Índice duplicado" }
                            manifest = JSONObject(dest.readText(Charsets.UTF_8))
                            dest.delete()
                        } else {
                            require(extracted.put(name, dest) == null) { "Archivo duplicado en respaldo" }
                        }
                        zip.closeEntry()
                    }
                }
            }
            val index = requireNotNull(manifest) { "El archivo no es un respaldo de Stickit" }
            require(index.optString("format") == "stickit-library" && index.optInt("version") == 1) {
                "Versión de respaldo no compatible"
            }
            val items = index.getJSONArray("items")
            require(items.length() <= 1000) { "Demasiados stickers" }
            val existing = store.all().mapNotNull {
                store.stickerFile(it).takeIf(File::isFile)?.let(::sha)
            }.toMutableSet()
            val folder = File(context.filesDir, "stickers").apply { mkdirs() }
            var imported = 0
            for (i in 0 until items.length()) {
                val row = items.getJSONObject(i)
                val path = row.getString("media")
                val gif = extracted[path] ?: throw IllegalArgumentException("Falta $path")
                require(gif.length() >= 13 && gif.inputStream().use { src ->
                    val bytes = ByteArray(6)
                    src.read(bytes) == 6 && String(bytes, Charsets.US_ASCII).startsWith("GIF8")
                }) { "GIF inválido en respaldo" }
                val signature = sha(gif)
                require(signature == row.getString("sha256")) { "Integridad incorrecta: $path" }
                if (signature in existing) continue
                val destination = File(folder, UUID.randomUUID().toString() + ".gif")
                gif.copyTo(destination)
                try {
                    val id = store.add(
                        destination,
                        row.optString("name", "Sticker").take(150),
                        row.optString("tags").take(1500),
                        row.optString("pack", "General").take(150)
                    )
                    newIds.add(id)
                    if (row.optBoolean("favorite")) store.setFavorite(id, true)
                    store.restoreHistory(
                        id,
                        row.optLong("created", System.currentTimeMillis()),
                        row.optLong("lastUsed", 0L),
                        row.optInt("uses", 0)
                    )
                    val stem = destination.nameWithoutExtension
                    for ((key, extension) in listOf(
                        "source_gif" to "source.gif",
                        "source_webp" to "source.webp",
                        "unedited_gif" to "unedited.gif"
                    )) {
                        if (row.has(key)) {
                            val original = extracted[row.getString(key)] ?: error("Falta archivo de origen")
                            original.copyTo(File(folder, "${stem}.${extension}"))
                        }
                    }
                } catch (e: Exception) {
                    destination.delete()
                    throw e
                }
                existing.add(signature)
                imported++
            }
            return imported
        } catch (e: Exception) {
            // A corrupted archive must not leave a partly restored library.
            store.removeMany(newIds)
            throw e
        } finally {
            temporary.deleteRecursively()
        }
    }
}

/**
 * Import a user-selected ZIP or directory of images / WebP / GIF / videos.
 * WhatsApp does not expose its private installed pack database to third-party
 * apps. Only files explicitly shared/exported by the user are imported.
 */
object StickitPackImport {
    data class Report(val added: Int, val skipped: Int)
    private val extensions = setOf("gif", "webp", "png", "jpeg", "jpg", "mp4", "webm")

    fun zip(context: Context, store: StickerStore, uri: Uri, pack: String): Report {
        // Use random-access ZipFile. Skipped entries are never decompressed,
        // preventing a large unsupported ZIP member from exhausting resources.
        val archive = File(context.cacheDir, UUID.randomUUID().toString() + ".zip")
        try {
            context.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "No se pudo abrir el paquete" }
                FileOutputStream(archive).use { output ->
                    val buffer = ByteArray(8192)
                    var compressedSize = 0L
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        compressedSize += n
                        require(compressedSize <= 100_000_000L) { "El ZIP es demasiado grande" }
                        output.write(buffer, 0, n)
                    }
                }
            }
            var added = 0
            var skipped = 0
            var total = 0L
            java.util.zip.ZipFile(archive).use { zip ->
                val entries = zip.entries().toList()
                require(entries.size <= 500) { "El ZIP tiene demasiados archivos" }
                for (entry in entries) {
                    if (entry.isDirectory) continue
                    val filename = entry.name.substringAfterLast('/').take(120)
                    val extension = filename.substringAfterLast('.', "").lowercase()
                    if (extension !in extensions) { skipped++; continue }
                    if (entry.size > 20_000_000L) { skipped++; continue }
                    val tmp = File(context.cacheDir,
                        UUID.randomUUID().toString() + ".$"+"extension")
                    try {
                        zip.getInputStream(entry).use { input ->
                            FileOutputStream(tmp).use { output ->
                                var size = 0L
                                val bytes = ByteArray(8192)
                                while (true) {
                                    val n = input.read(bytes)
                                    if (n < 0) break
                                    size += n
                                    total += n
                                    require(size <= 20_000_000L && total <= 180_000_000L) {
                                        "El paquete supera el tamaño permitido"
                                    }
                                    output.write(bytes, 0, n)
                                }
                            }
                        }
                        try {
                            importOne(context, store, tmp, filename.substringBeforeLast('.'), pack)
                            added++
                        } catch (_: Exception) { skipped++ }
                    } finally {
                        tmp.delete()
                    }
                }
            }
            return Report(added, skipped)
        } finally {
            archive.delete()
        }
    }

    fun folder(context: Context, store: StickerStore, tree: Uri): Report {
        val root = DocumentFile.fromTreeUri(context, tree) ?: error("Carpeta no accesible")
        var added = 0
        var skipped = 0
        var inspected = 0
        fun walk(dir: DocumentFile, depth: Int, pack: String) {
            if (depth > 3) return
            for (child in dir.listFiles()) {
                inspected++
                require(inspected <= 500) { "Demasiados elementos en la carpeta" }
                if (child.isDirectory) { walk(child, depth + 1, child.name ?: pack); continue }
                val filename = child.name ?: "Sticker"
                val ext = filename.substringAfterLast('.', "").lowercase()
                if (!child.isFile || ext !in extensions || child.length() > 20_000_000L) {
                    skipped++; continue
                }
                try {
                    val file = GifConverter.import(context, child.uri)
                    try { store.add(file, filename.substringBeforeLast('.').take(150), "", pack); added++ }
                    catch (e: Exception) { file.delete(); throw e }
                } catch (_: Exception) { skipped++ }
            }
        }
        walk(root, 0, root.name ?: "Colección importada")
        return Report(added, skipped)
    }

    private fun importOne(context: Context, store: StickerStore, file: File, name: String, pack: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val gif = GifConverter.import(context, uri)
        try { store.add(gif, name.take(150), "", pack.take(150)) }
        catch (e: Exception) { gif.delete(); throw e }
    }
}
