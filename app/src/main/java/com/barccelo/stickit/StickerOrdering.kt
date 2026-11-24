package com.barccelo.stickit

import android.content.Context

/**
 * One persistent order shared by the library and keyboard-sized selector.
 * Newly created stickers appear first by default, without prioritizing usage.
 */
object StickerOrdering {
    const val NEWEST = 0
    const val NAME = 1
    const val RECENT_USED = 2
    const val MOST_USED = 3
    const val OLDEST = 4
    val options = listOf("Más nuevos", "Nombre A–Z", "Usados recientemente",
        "Más utilizados", "Más antiguos")

    private const val PREFS = "stickit_order"
    private const val KEY = "order"

    fun current(context: Context): Int = context
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getInt(KEY, NEWEST).coerceIn(NEWEST, OLDEST)

    fun save(context: Context, mode: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putInt(KEY, mode.coerceIn(NEWEST, OLDEST)).apply()
    }

    fun sort(stickers: List<Sticker>, mode: Int): List<Sticker> = when (mode) {
        NAME -> stickers.sortedWith(compareBy<Sticker>(String.CASE_INSENSITIVE_ORDER) {
            it.name
        }.thenByDescending { it.createdAt }.thenByDescending { it.id })
        RECENT_USED -> stickers.sortedWith(compareByDescending<Sticker> { it.lastUsed }
            .thenByDescending { it.createdAt }.thenByDescending { it.id })
        MOST_USED -> stickers.sortedWith(compareByDescending<Sticker> { it.uses }
            .thenByDescending { it.lastUsed }.thenByDescending { it.id })
        OLDEST -> stickers.sortedWith(compareBy<Sticker> { it.createdAt }
            .thenBy { it.id })
        else -> stickers.sortedWith(compareByDescending<Sticker> { it.createdAt }
            .thenByDescending { it.id })
    }
}
