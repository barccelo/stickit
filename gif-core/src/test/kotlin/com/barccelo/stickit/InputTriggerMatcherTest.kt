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

    @Test fun ignoresNonMatchingTokens() {
        assertNull(InputTriggerMatcher.emoji("abc", listOf("😂")))
        assertNull(InputTriggerMatcher.shortcut("hello", listOf("stk")))
        assertNull(InputTriggerMatcher.shortcut("x", listOf("x")))
    }
}
