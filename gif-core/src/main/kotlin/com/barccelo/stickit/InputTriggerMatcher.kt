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


    /**
     * Candidate suffixes longest first. Multiword tags match even when typed
     * after other words. Short 1–2 character names/tags are exact-only.
     */
    fun trailingCandidates(text: String): List<String> {
        if (text.isEmpty() || !text.last().isLetterOrDigit()) return emptyList()
        val tail = text.takeLast(100)
            .takeLastWhile { it.isLetterOrDigit() || it == ' ' || it == '-' || it == '_' }
            .trim()
        if (tail.isEmpty()) return emptyList()
        val words = tail.split(Regex("[\\s_-]+")).filter { it.isNotBlank() }
        return (minOf(words.size, 4) downTo 1).map { count ->
            words.takeLast(count).joinToString(" ")
        }.filter { it.length <= 60 }
    }

    fun matchesLabel(candidate: String, label: String): Boolean {
        val query = candidate.trim()
        if (query.isEmpty()) return false
        val normalized = label.trim()
        // A two-letter label such as "Eu" should match only "Eu", never
        // every label starting with E or containing "eu".
        if (query.length < 3) return normalized.equals(query, ignoreCase = true)
        val words = normalized.split(Regex("[^\\p{L}\\p{N}]+"))
            .filter { it.isNotBlank() }
        if (words.isEmpty()) return false
        val phrase = query.split(Regex("[\\s_-]+"))
            .filter { it.isNotBlank() }
        if (phrase.isEmpty()) return false
        if (phrase.size == 1) {
            return words.any { it.startsWith(phrase[0], ignoreCase = true) }
        }
        // Match complete phrases as well as prefixes of the last word,
        // e.g. "brazos cruz" -> "brazos cruzados".
        return (0..(words.size - phrase.size)).any { start ->
            phrase.indices.all { pos ->
                if (pos == phrase.lastIndex)
                    words[start + pos].startsWith(phrase[pos], ignoreCase = true)
                else words[start + pos].equals(phrase[pos], ignoreCase = true)
            }
        }
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
