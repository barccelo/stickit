package com.barccelo.stickit

import org.junit.Assert.*
import org.junit.Test

class InputTriggerMatcherTest {
    @Test fun matchesMultipleShortcutsIgnoringCase() {
        val configured = listOf("STK", "/stickers", "peg")
        assertEquals("STK", InputTriggerMatcher.shortcut("Hola, stk", configured))
        assertEquals("/stickers", InputTriggerMatcher.shortcut("/stickers", configured))
        assertEquals("peg", InputTriggerMatcher.shortcut("vamos peg", configured))
    }

    @Test fun doesNotTriggerWhenEmbeddedInsideWords() {
        assertNull(InputTriggerMatcher.shortcut("rockstk", listOf("stk")))
        assertNull(InputTriggerMatcher.shortcut("estk", listOf("stk")))
        assertNull(InputTriggerMatcher.shortcut("stk después", listOf("stk")))
        assertEquals("stk", InputTriggerMatcher.shortcut("hola.stk", listOf("stk")))
    }

    @Test fun supportsMultiCodePointEmojiAndLongestMatch() {
        val entries = listOf("❤", "❤️", "👨‍👩‍👧", "😂")
        assertEquals("❤️", InputTriggerMatcher.emoji("hoy ❤️", entries))
        assertEquals("👨‍👩‍👧", InputTriggerMatcher.emoji("👨‍👩‍👧", entries))
        assertEquals("😂", InputTriggerMatcher.emoji("jeje😂", entries))
    }

    @Test fun wordFragmentsSuggestOnlyAfterThreeLetters() {
        assertNull(InputTriggerMatcher.trailingWord("Ho"))
        assertEquals("bravo", InputTriggerMatcher.trailingWord("qué bravo"))
        assertEquals("aleg", InputTriggerMatcher.trailingWord("¡aleg"))
        assertNull(InputTriggerMatcher.trailingWord("fin "))
    }

    @Test fun ignoresNonMatchingTokens() {
        assertNull(InputTriggerMatcher.emoji("abc", listOf("😂")))
        assertNull(InputTriggerMatcher.shortcut("hello", listOf("stk")))
        assertNull(InputTriggerMatcher.shortcut("x", listOf("x")))
    }

    @Test fun matchesMultiwordTagFromBeginningMiddleAndCompletePhrase() {
        val label = "brazos cruzados"
        assertTrue(InputTriggerMatcher.matchesLabel("brazos", label))
        assertTrue(InputTriggerMatcher.matchesLabel("cruzados", label))
        assertTrue(InputTriggerMatcher.matchesLabel("brazos cruzados", label))
        assertTrue(InputTriggerMatcher.matchesLabel("brazos cruz", label))
        assertEquals(listOf("brazos cruzados", "cruzados"),
            InputTriggerMatcher.trailingCandidates("mira brazos cruzados").drop(1).take(2))
    }

    @Test fun shortNameRequiresExactMatchAndAvoidsNoisyPrefixes() {
        assertTrue(InputTriggerMatcher.matchesLabel("Eu", "Eu"))
        assertTrue(InputTriggerMatcher.matchesLabel("e", "E"))
        assertFalse(InputTriggerMatcher.matchesLabel("Eu", "Euforia"))
        assertFalse(InputTriggerMatcher.matchesLabel("u", "Eu"))
        assertTrue(InputTriggerMatcher.trailingCandidates("hola Eu").contains("Eu"))
        assertTrue(InputTriggerMatcher.trailingCandidates("hola E").contains("E"))
        assertTrue(InputTriggerMatcher.trailingCandidates("hola ").isEmpty())
    }

    @Test fun longerTagsMatchAnywhereOnlyAtWordBoundary() {
        assertTrue(InputTriggerMatcher.matchesLabel("cruzados", "manos y brazos cruzados"))
        assertFalse(InputTriggerMatcher.matchesLabel("ruzados", "brazos cruzados"))
        assertTrue(InputTriggerMatcher.matchesLabel("hola mun", "hola mundo"))
    }
}
