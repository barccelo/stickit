package com.barccelo.stickit

import android.content.Context
import android.content.res.Configuration

/**
 * Three modes keep the visual system in sync across Compose, the floating
 * selector and the movable suggestion strip. Preferences stay local.
 */
object AppearanceSettings {
    const val SYSTEM = 0
    const val LIGHT = 1
    const val DARK = 2
    private const val PREFS = "stickit_appearance"

    fun mode(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt("theme_mode", SYSTEM).coerceIn(SYSTEM, DARK)

    fun save(context: Context, mode: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putInt("theme_mode", mode.coerceIn(SYSTEM, DARK)).apply()
    }

    fun isDark(context: Context): Boolean = when (mode(context)) {
        LIGHT -> false
        DARK -> true
        else -> (context.resources.configuration.uiMode and
            Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    }
}
