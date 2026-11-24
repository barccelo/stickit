package com.barccelo.stickit

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import java.lang.ref.WeakReference

/**
 * Optional keyboard shortcut detection, strictly limited to WhatsApp and
 * WhatsApp Business. It inspects only the transient suffix of an editable
 * field, never passwords, and never persists or transmits entered text.
 *
 * No key injection and no attempts to interact with Gboard internals.
 */
class StickerShortcutService : AccessibilityService() {
    companion object {
        private var activeRef: WeakReference<StickerShortcutService>? = null

        fun active(): StickerShortcutService? = activeRef?.get()

        fun isEnabled(context: Context): Boolean {
            val component = ComponentName(context, StickerShortcutService::class.java)
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ).orEmpty()
            return enabled.split(':').any {
                it.equals(component.flattenToString(), ignoreCase = true) ||
                    it.equals(component.flattenToShortString(), ignoreCase = true)
            }
        }
    }

    private lateinit var picker: StickerFloatingPicker
    private lateinit var store: StickerStore
    private var shortcutArmed = true
    private var catalogCache: List<Sticker> = emptyList()
    private var reloadAfter = 0L

    private fun catalog(): List<Sticker> {
        val now = android.os.SystemClock.elapsedRealtime()
        if (now >= reloadAfter) {
            catalogCache = store.all()
            reloadAfter = now + 3000L
        }
        return catalogCache
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        store = StickerStore(this)
        picker = StickerFloatingPicker(
            this, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, store
        )
        activeRef = WeakReference(this)
    }

    fun openPicker() {
        if (::picker.isInitialized) picker.showPicker()
    }

    fun togglePicker() {
        if (!::picker.isInitialized) return
        if (picker.isOpen) picker.hidePicker() else picker.showPicker()
    }

    fun closePicker() {
        if (::picker.isInitialized) picker.hidePicker()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || !::picker.isInitialized) return
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val top = rootInActiveWindow?.packageName
            if (top != null && !ShortcutSettings.isAllowedApp(top) &&
                top.toString() != packageName) {
                picker.hideSuggestions()
                shortcutArmed = true
            }
            return
        }
        if (event.eventType != AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED ||
            !ShortcutSettings.isAllowedApp(event.packageName)) return

        // Strictly read editable, non-password nodes only. Some WhatsApp
        // versions do not expose editor text: in that case, do nothing.
        if (event.isPassword) return
        val source = event.source ?: return
        if (!source.isEditable || source.isPassword) return
        val text = source.text?.toString() ?: return
        val suffix = text.takeLast(48)

        val trigger = InputTriggerMatcher.shortcut(suffix, ShortcutSettings.phrases(this))
        if (trigger != null) {
            picker.hideSuggestions()
            if (shortcutArmed) {
                shortcutArmed = false
                picker.showPicker()
            }
            return
        }
        shortcutArmed = true

        if (!ShortcutSettings.suggestionsEnabled(this)) {
            picker.hideSuggestions()
            return
        }

        val potentialWord = InputTriggerMatcher.trailingWord(suffix)
        // Avoid even querying the local library for single-character typing.
        if (potentialWord == null && suffix.lastOrNull()?.isLetterOrDigit() == true) {
            picker.hideSuggestions()
            return
        }
        val catalog = catalog()
        val linked = catalog.asSequence().flatMap { it.emojiList().asSequence() }
            .distinct().toList()
        val emoji = InputTriggerMatcher.emoji(suffix, linked)
        val word = if (emoji == null) potentialWord else null
        val matches = when {
            emoji != null -> catalog.filter { emoji in it.emojiList() }
            word != null -> catalog.filter { sticker ->
                sticker.tagList().any { it.startsWith(word, ignoreCase = true) } ||
                    sticker.name.split(" ", "-", "_").any {
                        it.startsWith(word, ignoreCase = true)
                    }
            }
            else -> emptyList()
        }.sortedWith(compareByDescending<Sticker> { it.favorite }
            .thenByDescending { it.lastUsed }
            .thenByDescending { it.uses })
            .take(5)
        if (matches.isEmpty()) picker.hideSuggestions()
        else picker.showSuggestions(emoji ?: word ?: "", matches)
    }

    override fun onInterrupt() {
        if (::picker.isInitialized) picker.dismiss()
    }

    override fun onDestroy() {
        if (::picker.isInitialized) picker.dismiss()
        if (activeRef?.get() === this) activeRef = null
        super.onDestroy()
    }
}
