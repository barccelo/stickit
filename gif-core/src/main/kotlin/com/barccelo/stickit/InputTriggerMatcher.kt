package com.barccelo.stickit

/**
 * Pure, locale-independent end-of-text matching. Nothing entered is stored.
 * The caller should invoke this only for explicitly allowed chat applications.
 */
object InputTriggerMatcher {
    fun shortcut(text: String, shortcuts: Collection<String>): String? {
        if (text.isEmpty()) return null
        return shortcuts.asSequence()
            .map { it.trim() }
            .filter { it.length in 2..20 }
            .sortedByDescending { it.length }
            .firstOrNull { trigger ->
                text.endsWith(trigger, ignoreCase = true) &&
                    (text.length == trigger.length ||
                        !text[text.length - trigger.length - 1].isLetterOrDigit())
            }
    }

    /** Word fragment for live name/tag suggestions, with a 3-letter minimum. */
    fun trailingWord(text: String): String? {
        val word = text.takeLast(40).takeLastWhile { it.isLetterOrDigit() }
        return word.takeIf { it.length in 3..30 }
    }

    fun emoji(text: String, associated: Collection<String>): String? {
        if (text.isEmpty()) return null
        val tail = text.takeLast(32)
        return associated.asSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() && it.length <= 24 }
            .distinct()
            .sortedByDescending { it.length }
            .firstOrNull(tail::endsWith)
    }
}
