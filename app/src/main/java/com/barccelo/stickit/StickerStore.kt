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
    SQLiteOpenHelper(appContext, "stickit.db", null, 2) {
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
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE stickers ADD COLUMN emojis TEXT NOT NULL DEFAULT ''")
        }
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
        return writableDatabase.insertOrThrow("stickers", null, row)
    }

    fun update(sticker: Sticker, name: String, tags: String, pack: String, emojis: String = sticker.emojis) {
        val values = ContentValues().apply {
            put("name", name.trim().ifBlank { "Sin título" })
            put("tags", sanitizeTags(tags))
            put("emojis", sanitizeEmojis(emojis))
            put("package_name", pack.trim().ifBlank { "General" })
        }
        writableDatabase.update("stickers", values, "id=?", arrayOf(sticker.id.toString()))
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
