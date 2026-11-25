package com.barccelo.stickit

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.database.MatrixCursor
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.aureusapps.android.webpandroid.decoder.WebPDecoder
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/**
 * The WhatsApp pack API consumes original animated WebP files, NOT GIFs pasted
 * through Android's clipboard. Do not transcode animated stickers here.
 * A successful add still requires explicit confirmation inside WhatsApp.
 */
internal data class PreparedNativePack(
    val identifier: String,
    val name: String,
    val count: Int
)

internal object WhatsAppNativePacks {
    private const val DIR = "native_whatsapp_packs"
    private const val MANIFEST = "pack.json"
    private const val TRAY = "tray.png"
    private const val MAX_WEBP_BYTES = 500 * 1024L
    private const val MAX_PACKS = 10
    private val safeId = Regex("animated_[0-9a-f]{16}")

    fun authority(context: Context): String =
        "${context.packageName}.whatsappstickers"

    private fun root(context: Context): File =
        File(context.filesDir, DIR).apply { mkdirs() }

    private fun packId(name: String): String {
        val hex = MessageDigest.getInstance("SHA-256")
            .digest(name.lowercase().toByteArray(Charsets.UTF_8))
            .take(8).joinToString("") { "%02x".format(it) }
        return "animated_$hex"
    }

    fun addIntent(context: Context, pack: PreparedNativePack): Intent =
        Intent("com.whatsapp.intent.action.ENABLE_STICKER_PACK")
            .putExtra("sticker_pack_id", pack.identifier)
            .putExtra("sticker_pack_authority", authority(context))
            .putExtra("sticker_pack_name", pack.name)

    /**
     * Reject incompatible sources instead of clipping or stretching them.
     * libwebp's decoded bitmap is decoder-owned; never recycle it.
     */
    private fun isCompatible(context: Context, webp: File): Boolean {
        if (!webp.isFile || webp.length() !in 1..MAX_WEBP_BYTES) return false
        val signature = webp.inputStream().use { input ->
            val bytes = ByteArray(12)
            if (input.read(bytes) != 12) return false
            bytes
        }
        if (String(signature, 0, 4, Charsets.US_ASCII) != "RIFF" ||
            String(signature, 8, 4, Charsets.US_ASCII) != "WEBP") return false

        val decoder = WebPDecoder(context)
        return try {
            val uri = androidx.core.content.FileProvider.getUriForFile(
                context, "${context.packageName}.fileprovider", webp)
            decoder.setDataSource(uri)
            val info = decoder.decodeInfo()
            if (info.width != 512 || info.height != 512) return false
            var previous = 0
            var count = 0
            while (decoder.hasNextFrame()) {
                if (count >= 2000) return false
                val result = decoder.decodeNextFrame()
                if (result.frame == null ||
                    result.timestamp - previous < 8 ||
                    result.timestamp > 10_000) return false
                previous = result.timestamp
                count++
            }
            count >= 2 && previous <= 10_000
        } catch (_: Exception) {
            false
        } finally {
            decoder.release()
        }
    }

    fun prepare(context: Context, store: StickerStore, selected: Sticker): PreparedNativePack =
        prepareInternal(context, store, selected, includeLibrary = false)

    /**
     * Native sticker alternative to GIF clipboard paste: gather WhatsApp-ready
     * ORIGINAL animated WebPs from the entire Stickit library. WhatsApp needs
     * 3–30 stickers per pack, so they needn't all live in the same Stickit pack.
     * This is a stable, reusable WhatsApp pack rather than a new pack per tap.
     */
    fun prepareQuick(context: Context, store: StickerStore, selected: Sticker): PreparedNativePack =
        prepareInternal(context, store, selected, includeLibrary = true)

    private fun prepareInternal(context: Context, store: StickerStore,
        selected: Sticker, includeLibrary: Boolean): PreparedNativePack {
        val identifier = packId(if (includeLibrary) "Stickit · Animados" else selected.pack)
        val packName = if (includeLibrary) "Stickit · Animados"
            else "Stickit · ${selected.pack}".take(128)
        val root = root(context)
        val target = File(root, identifier)
        if (!target.isDirectory &&
            (root.listFiles()?.count { it.isDirectory && safeId.matches(it.name) } ?: 0) >= MAX_PACKS) {
            throw IllegalArgumentException("WhatsApp admite hasta 10 paquetes desde Stickit.")
        }
        val selectedGif = store.stickerFile(selected)
        val selectedWebp = File(selectedGif.parentFile,
            "${selectedGif.nameWithoutExtension}.source.webp")
        require(isCompatible(context, selectedWebp)) {
            "Este sticker no tiene un WebP animado original compatible. " +
                "WhatsApp exige 512×512, 500 KB como máximo, animación de hasta 10 s " +
                "y fotogramas de al menos 8 ms. Prueba con otro sticker importado de WhatsApp."
        }
        val candidatePool = store.all().filter { item ->
            item.id != selected.id &&
                (includeLibrary || item.pack.equals(selected.pack, ignoreCase = true))
        }
        val selectedFirst = listOf(selected) + candidatePool
        val compatible = ArrayList<Pair<Sticker, File>>()
        for (item in selectedFirst) {
            if (compatible.size >= 30) break
            val saved = store.stickerFile(item)
            val source = File(saved.parentFile, "${saved.nameWithoutExtension}.source.webp")
            if (isCompatible(context, source)) compatible.add(item to source)
        }
        require(compatible.size >= 3) {
            "Encontré ${compatible.size} WebP animado(s) compatibles " +
                (if (includeLibrary) "en toda tu biblioteca. " else "en este paquete. ") +
                "WhatsApp exige al menos 3 stickers animados originales por paquete. " +
                "Importa otros dos stickers animados de WhatsApp y vuelve a intentarlo."
        }
        require(compatible.first().first.id == selected.id) {
            "Este sticker original no cumple los límites nativos de WhatsApp."
        }

        val stage = File(root, ".${identifier}_stage")
        val old = File(root, ".${identifier}_old")
        stage.deleteRecursively()
        require(stage.mkdirs()) { "No se pudo preparar el paquete." }
        try {
            val entries = JSONArray()
            compatible.forEach { (item, source) ->
                val filename = "s${item.id}.webp"
                source.copyTo(File(stage, filename), overwrite = true)
                entries.put(JSONObject()
                    .put("file", filename)
                    .put("emoji", item.emojiList().take(3)
                        .ifEmpty { listOf("🙂") }.joinToString(","))
                    .put("description", item.name.take(255)))
            }
            val firstFile = File(stage, "s${compatible.first().first.id}.webp")
            val preview = ImageDecoder.decodeBitmap(ImageDecoder.createSource(firstFile)) {
                decoder, _, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.setTargetSize(96, 96)
            }
            try {
                File(stage, TRAY).outputStream().use {
                    require(preview.compress(Bitmap.CompressFormat.PNG, 100, it)) {
                        "No se pudo crear el ícono del paquete."
                    }
                }
            } finally { preview.recycle() }
            require(File(stage, TRAY).length() <= 50 * 1024L) {
                "El ícono del paquete supera 50 KB."
            }
            val manifest = JSONObject()
                .put("id", identifier)
                .put("name", packName)
                .put("version", System.currentTimeMillis().toString())
                .put("stickers", entries)
            File(stage, MANIFEST).writeText(manifest.toString(), Charsets.UTF_8)

            old.deleteRecursively()
            if (target.exists()) require(target.renameTo(old)) {
                "No se pudo actualizar el paquete anterior."
            }
            if (!stage.renameTo(target)) {
                if (old.exists()) old.renameTo(target)
                error("No se pudo guardar el paquete.")
            }
            old.deleteRecursively()
            return PreparedNativePack(identifier, packName, compatible.size)
        } finally {
            stage.deleteRecursively()
        }
    }

    fun list(context: Context): List<JSONObject> = root(context).listFiles()
        ?.filter { it.isDirectory && safeId.matches(it.name) }
        ?.mapNotNull { file -> read(context, file.name) } ?: emptyList()

    fun read(context: Context, id: String): JSONObject? {
        if (!safeId.matches(id)) return null
        val file = File(File(root(context), id), MANIFEST)
        return try {
            if (!file.isFile || file.length() > 100_000) null
            else JSONObject(file.readText()).takeIf { it.optString("id") == id }
        } catch (_: Exception) { null }
    }

    fun asset(context: Context, id: String, filename: String): File? {
        val pack = read(context, id) ?: return null
        val allowed = filename == TRAY ||
            (0 until pack.getJSONArray("stickers").length()).any { index ->
                pack.getJSONArray("stickers").getJSONObject(index)
                    .optString("file") == filename
            }
        if (!allowed || filename.contains("/") || filename.contains("..")) return null
        return File(File(root(context), id), filename).takeIf { it.isFile }
    }
}

/** WhatsApp's documented ContentProvider metadata / sticker / asset endpoints. */
class WhatsAppNativePackProvider : ContentProvider() {
    private val infoColumns = arrayOf(
        "sticker_pack_identifier", "sticker_pack_name",
        "sticker_pack_publisher", "sticker_pack_icon",
        "android_play_store_link", "ios_app_download_link",
        "sticker_pack_publisher_email", "sticker_pack_publisher_website",
        "sticker_pack_privacy_policy_website", "sticker_pack_license_agreement_website",
        "image_data_version", "whatsapp_will_not_cache_stickers",
        "animated_sticker_pack"
    )
    private val stickerColumns = arrayOf(
        "sticker_file_name", "sticker_emoji", "sticker_accessibility_text")

    override fun onCreate(): Boolean = true

    override fun query(uri: Uri, projection: Array<out String>?,
        selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
        val ctx = requireNotNull(context)
        val parts = uri.pathSegments
        return when {
            parts.size == 1 && parts[0] == "metadata" -> {
                val cursor = MatrixCursor(infoColumns)
                WhatsAppNativePacks.list(ctx).forEach { putPack(cursor, it) }
                cursor
            }
            parts.size == 2 && parts[0] == "metadata" -> {
                val cursor = MatrixCursor(infoColumns)
                WhatsAppNativePacks.read(ctx, parts[1])?.let { putPack(cursor, it) }
                cursor
            }
            parts.size == 2 && parts[0] == "stickers" -> {
                val cursor = MatrixCursor(stickerColumns)
                val pack = WhatsAppNativePacks.read(ctx, parts[1])
                if (pack != null) {
                    val stickers = pack.getJSONArray("stickers")
                    for (index in 0 until stickers.length()) {
                        val item = stickers.getJSONObject(index)
                        cursor.addRow(arrayOf(
                            item.getString("file"), item.getString("emoji"),
                            item.optString("description")
                        ))
                    }
                }
                cursor
            }
            else -> throw IllegalArgumentException("Ruta desconocida: $uri")
        }
    }

    private fun putPack(cursor: MatrixCursor, pack: JSONObject) {
        cursor.addRow(arrayOf<Any?>(
            pack.getString("id"), pack.getString("name"), "Stickit",
            "tray.png", null, null, null, null, null, null,
            pack.getString("version"), 0, 1
        ))
    }

    override fun openAssetFile(uri: Uri, mode: String): AssetFileDescriptor? {
        require(mode == "r") { "Solo lectura" }
        val path = uri.pathSegments
        if (path.size != 3 || path[0] != "stickers_asset") return null
        val file = WhatsAppNativePacks.asset(requireNotNull(context),
            path[1], path[2]) ?: return null
        return AssetFileDescriptor(
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY),
            0, AssetFileDescriptor.UNKNOWN_LENGTH)
    }

    override fun getType(uri: Uri): String {
        val path = uri.pathSegments
        return when (path.firstOrNull()) {
            "metadata" -> if (path.size == 1)
                "vnd.android.cursor.dir/vnd.${WhatsAppNativePacks.authority(requireNotNull(context))}.metadata"
                else "vnd.android.cursor.item/vnd.${WhatsAppNativePacks.authority(requireNotNull(context))}.metadata"
            "stickers" -> "vnd.android.cursor.dir/vnd.${WhatsAppNativePacks.authority(requireNotNull(context))}.stickers"
            "stickers_asset" -> if (path.lastOrNull() == "tray.png") "image/png"
                else "image/webp"
            else -> throw IllegalArgumentException("Ruta desconocida: $uri")
        }
    }
    override fun insert(uri: Uri, values: ContentValues?): Uri? =
        throw UnsupportedOperationException("Solo lectura")
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException("Solo lectura")
    override fun update(uri: Uri, values: ContentValues?, selection: String?,
        selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException("Solo lectura")
}
