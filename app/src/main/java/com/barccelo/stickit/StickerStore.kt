package com.barccelo.stickit

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.core.content.FileProvider
import java.io.File

data class Sticker(
    val id: Long,
    val fileName: String,
    val name: String,
    val tags: String,
    val pack: String,
    val createdAt: Long,
    val lastUsed: Long,
    val uses: Int,
    val favorite: Boolean,
    val emojis: String = ""
) {
    fun emojiList(): List<String> = emojis.split(",").map { it.trim() }.filter(String::isNotEmpty)
    fun tagList() = tags.split(",").map { it.trim() }.filter { it.isNotBlank() }
    fun matches(query: String): Boolean {
        val q = query.trim()
        return q.isBlank() || listOf(name, tags, pack, emojis).any { it.contains(q, ignoreCase = true) }
    }
}

class StickerStore(private val appContext: Context) :
    SQLiteOpenHelper(appContext, "stickit.db", null, 3) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE stickers (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            filename TEXT NOT NULL UNIQUE,
            name TEXT NOT NULL,
            tags TEXT NOT NULL DEFAULT '',
            package_name TEXT NOT NULL DEFAULT 'General',
            created_at INTEGER NOT NULL,
            last_used INTEGER NOT NULL DEFAULT 0,
            use_count INTEGER NOT NULL DEFAULT 0,
            favorite INTEGER NOT NULL DEFAULT 0,
            emojis TEXT NOT NULL DEFAULT ''
        )""")
        createPackTable(db)
        db.execSQL("INSERT INTO packs (name, position) VALUES ('General', 0)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE stickers ADD COLUMN emojis TEXT NOT NULL DEFAULT ''")
        }
        if (oldVersion < 3) {
            createPackTable(db)
            db.execSQL("INSERT INTO packs (name, position) VALUES ('General', 0)")
            val legacy = mutableListOf<String>()
            db.rawQuery(
                "SELECT DISTINCT package_name FROM stickers ORDER BY MIN(created_at)",
                null
            ).use { cursor ->
                while (cursor.moveToNext()) legacy.add(cursor.getString(0))
            }
            legacy.forEach { ensurePack(db, it) }
        }
    }

    private fun createPackTable(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE IF NOT EXISTS packs (
            name TEXT PRIMARY KEY COLLATE NOCASE,
            position INTEGER NOT NULL
        )""")
    }

    private fun safePack(name: String): String =
        name.trim().take(120).ifBlank { "General" }

    private fun ensurePack(db: SQLiteDatabase, raw: String) {
        db.execSQL(
            """INSERT OR IGNORE INTO packs (name, position)
                VALUES (?, (SELECT COALESCE(MAX(position) + 1, 0) FROM packs))""",
            arrayOf(safePack(raw))
        )
    }

    fun packNames(): List<String> {
        val result = mutableListOf<String>()
        readableDatabase.rawQuery("SELECT name FROM packs ORDER BY position, name", null).use {
            while (it.moveToNext()) result.add(it.getString(0))
        }
        return result
    }

    fun createPack(name: String) {
        val normalized = safePack(name)
        require(name.isNotBlank()) { "Escribe el nombre del paquete" }
        val db = writableDatabase
        require(db.insertWithOnConflict("packs", null, ContentValues().apply {
            put("name", normalized)
            put("position", (packNames().size))
        }, SQLiteDatabase.CONFLICT_IGNORE) != -1L) {
            "Ya existe un paquete con ese nombre"
        }
    }

    fun renamePack(oldName: String, newName: String) {
        val next = safePack(newName)
        require(oldName != "General") { "General es el paquete predeterminado" }
        require(newName.isNotBlank()) { "Escribe el nuevo nombre" }
        val db = writableDatabase
        db.beginTransaction()
        try {
            require(db.updateWithOnConflict("packs", ContentValues().apply {
                put("name", next)
            }, "name=? COLLATE NOCASE", arrayOf(oldName),
                SQLiteDatabase.CONFLICT_ABORT) == 1) {
                "No se encontró el paquete"
            }
            db.update("stickers", ContentValues().apply {
                put("package_name", next)
            }, "package_name=? COLLATE NOCASE", arrayOf(oldName))
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    fun movePack(name: String, direction: Int) {
        if (direction == 0) return
        val names = packNames().toMutableList()
        val index = names.indexOfFirst { it.equals(name, ignoreCase = true) }
        val target = index + if (direction > 0) 1 else -1
        if (index !in names.indices || target !in names.indices) return
        java.util.Collections.swap(names, index, target)
        val db = writableDatabase
        db.beginTransaction()
        try {
            names.forEachIndexed { i, pack ->
                db.update("packs", ContentValues().apply { put("position", i) },
                    "name=? COLLATE NOCASE", arrayOf(pack))
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    fun deletePack(name: String, deleteStickers: Boolean) {
        require(!name.equals("General", true)) {
            "El paquete predeterminado no se puede eliminar"
        }
        if (deleteStickers) {
            removeMany(all().filter { it.pack.equals(name, true) }.map { it.id }.toSet())
        }
        val db = writableDatabase
        db.beginTransaction()
        try {
            if (!deleteStickers) db.update("stickers", ContentValues().apply {
                put("package_name", "General")
            }, "package_name=? COLLATE NOCASE", arrayOf(name))
            db.delete("packs", "name=? COLLATE NOCASE", arrayOf(name))
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }


    fun all(): List<Sticker> {
        val result = mutableListOf<Sticker>()
        readableDatabase.rawQuery("SELECT * FROM stickers ORDER BY created_at DESC", null).use { c ->
            while (c.moveToNext()) {
                result.add(Sticker(
                    c.getLong(c.getColumnIndexOrThrow("id")),
                    c.getString(c.getColumnIndexOrThrow("filename")),
                    c.getString(c.getColumnIndexOrThrow("name")),
                    c.getString(c.getColumnIndexOrThrow("tags")),
                    c.getString(c.getColumnIndexOrThrow("package_name")),
                    c.getLong(c.getColumnIndexOrThrow("created_at")),
                    c.getLong(c.getColumnIndexOrThrow("last_used")),
                    c.getInt(c.getColumnIndexOrThrow("use_count")),
                    c.getInt(c.getColumnIndexOrThrow("favorite")) == 1,
                    c.getString(c.getColumnIndexOrThrow("emojis"))
                ))
            }
        }
        return result
    }

    fun add(file: File, name: String, tags: String, pack: String, emojis: String = ""): Long {
        val row = ContentValues().apply {
            put("filename", file.name)
            put("name", name.ifBlank { "Sin título" })
            put("tags", sanitizeTags(tags))
            put("emojis", sanitizeEmojis(emojis))
            put("package_name", pack.ifBlank { "General" }.trim())
            put("created_at", System.currentTimeMillis())
        }
        val db = writableDatabase
        ensurePack(db, pack)
        return db.insertOrThrow("stickers", null, row)
    }

    fun update(sticker: Sticker, name: String, tags: String, pack: String, emojis: String = sticker.emojis) {
        val values = ContentValues().apply {
            put("name", name.trim().ifBlank { "Sin título" })
            put("tags", sanitizeTags(tags))
            put("emojis", sanitizeEmojis(emojis))
            put("package_name", pack.trim().ifBlank { "General" })
        }
        val db = writableDatabase
        ensurePack(db, pack)
        db.update("stickers", values, "id=?", arrayOf(sticker.id.toString()))
    }

    fun setFavorite(id: Long, favorite: Boolean) {
        writableDatabase.update("stickers", ContentValues().apply {
            put("favorite", if (favorite) 1 else 0)
        }, "id=?", arrayOf(id.toString()))
    }

    /**
     * Apply one action to multiple stickers in one database transaction.
     * Null leaves the existing field unchanged; tags are appended uniquely.
     */
    fun editMany(ids: Set<Long>, pack: String? = null, addTags: String? = null, favorite: Boolean? = null) {
        if (ids.isEmpty()) return
        val db = writableDatabase
        val normalizedTags = addTags.orEmpty().split(",").map { it.trim().lowercase() }
            .filter { it.isNotBlank() }
        if (pack != null) ensurePack(db, pack)
        db.beginTransaction()
        try {
            val all = all().associateBy { it.id }
            ids.forEach { id ->
                val sticker = all[id] ?: return@forEach
                val values = ContentValues()
                if (pack != null) values.put("package_name", pack.trim().ifBlank { "General" })
                if (favorite != null) values.put("favorite", if (favorite) 1 else 0)
                if (normalizedTags.isNotEmpty()) {
                    val combined = (sticker.tagList() + normalizedTags).distinct()
                    values.put("tags", combined.joinToString(", "))
                }
                if (values.size() > 0) db.update("stickers", values, "id=?", arrayOf(id.toString()))
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    fun removeMany(ids: Set<Long>) {
        if (ids.isEmpty()) return
        // First save filenames; physical media are only deleted after DB commit.
        val removed = all().filter { it.id in ids }
        val db = writableDatabase
        db.beginTransaction()
        try {
            removed.forEach { db.delete("stickers", "id=?", arrayOf(it.id.toString())) }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
        removed.forEach { sticker ->
            stickerFile(sticker).delete()
            val folder = File(appContext.filesDir, "stickers")
            val base = File(sticker.fileName).nameWithoutExtension
            File(folder, "${base}.source.gif").delete()
            File(folder, "${base}.source.webp").delete()
            File(folder, "${base}.unedited.gif").delete()
        }
    }

    fun restoreHistory(id: Long, createdAt: Long, lastUsed: Long, useCount: Int) {
        writableDatabase.update("stickers", ContentValues().apply {
            put("created_at", createdAt.coerceAtLeast(0L))
            put("last_used", lastUsed.coerceAtLeast(0L))
            put("use_count", useCount.coerceAtLeast(0))
        }, "id=?", arrayOf(id.toString()))
    }

    fun toggleFavorite(sticker: Sticker) {
        val values = ContentValues().apply { put("favorite", if (sticker.favorite) 0 else 1) }
        writableDatabase.update("stickers", values, "id=?", arrayOf(sticker.id.toString()))
    }

    fun markUsed(sticker: Sticker) {
        writableDatabase.execSQL(
            "UPDATE stickers SET last_used=?, use_count=use_count+1 WHERE id=?",
            arrayOf(System.currentTimeMillis(), sticker.id)
        )
    }

    fun remove(sticker: Sticker) {
        writableDatabase.delete("stickers", "id=?", arrayOf(sticker.id.toString()))
        stickerFile(sticker).delete()
        // Remove the original GIF backup created by the compatibility encoder.
        val folder = File(appContext.filesDir, "stickers")
        val base = File(sticker.fileName).nameWithoutExtension
        File(folder, "${base}.source.gif").delete()
        File(folder, "${base}.source.webp").delete()
        File(folder, "${base}.unedited.gif").delete()
    }

    fun stickerFile(sticker: Sticker): File = File(File(appContext.filesDir, "stickers"), sticker.fileName)

    private fun sanitizeEmojis(raw: String) = raw.split(",").map { it.trim() }
        .filter { it.isNotBlank() && it.length <= 24 }.distinct().take(30).joinToString(", ")

    private fun sanitizeTags(raw: String) =
        raw.split(",").map { it.trim().lowercase() }.filter { it.isNotBlank() }.distinct().joinToString(", ")
}

object ClipboardBridge {
    fun copyGif(context: Context, file: File) {
        require(file.exists() && file.extension.equals("gif", ignoreCase = true))
        val uri = FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", file
        )
        val clip = ClipData.newUri(context.contentResolver, "Stickit GIF", uri)
        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(clip)
    }
}
