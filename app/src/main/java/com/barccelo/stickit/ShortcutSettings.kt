package com.barccelo.stickit

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray

/**
 * All triggers are opt-in, device-local preferences. No keystrokes, messages,
 * contacts, chat contents or usage history are stored or transmitted.
 */
object ShortcutSettings {
    private const val FILE = "stickit_shortcuts"
    private const val SHORTCUTS = "shortcuts_v1"
    const val WHATSAPP = "com.whatsapp"
    const val WHATSAPP_BUSINESS = "com.whatsapp.w4b"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun phrases(context: Context): List<String> {
        val raw = prefs(context).getString(SHORTCUTS, null)
            ?: return listOf("STK")
        return try {
            val json = JSONArray(raw)
            (0 until json.length()).mapNotNull { i ->
                json.optString(i).trim().takeIf { it.length in 2..20 }
            }.distinctBy(String::lowercase).take(20)
        } catch (_: Exception) {
            listOf("STK")
        }
    }

    fun savePhrases(context: Context, phrases: List<String>) {
        val normalized = phrases.map { it.trim() }
            .filter { it.length in 2..20 }
            .distinctBy(String::lowercase)
            .take(20)
        val json = JSONArray()
        normalized.forEach(json::put)
        prefs(context).edit().putString(SHORTCUTS, json.toString()).apply()
    }

    fun suggestionsEnabled(context: Context): Boolean =
        prefs(context).getBoolean("suggestions", true)

    fun setSuggestionsEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean("suggestions", enabled).apply()
    }

    fun panelHeightDp(context: Context): Int =
        prefs(context).getInt("panel_height_dp", 340).coerceIn(230, 540)

    fun setPanelHeightDp(context: Context, height: Int) {
        prefs(context).edit().putInt("panel_height_dp", height.coerceIn(230, 540)).apply()
    }

    fun suggestX(context: Context): Int = prefs(context).getInt("suggest_x", 0)
    fun suggestY(context: Context): Int = prefs(context).getInt("suggest_y", 360)

    fun saveSuggestionPosition(context: Context, xDp: Int, yDp: Int) {
        prefs(context).edit().putInt("suggest_x", xDp.coerceIn(-300, 300))
            .putInt("suggest_y", yDp.coerceIn(0, 900)).apply()
    }

    fun isAllowedApp(packageName: CharSequence?): Boolean =
        packageName?.toString() == WHATSAPP ||
            packageName?.toString() == WHATSAPP_BUSINESS
}
