package com.mandecoy.osrscompanion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TitledNotepadDocumentTest {
    @Test
    fun oldNoteIsRetainedVerbatimAsFirstNamedSection() {
        val original = "Runes to buy\n- Law rune × 400\n\nClue step 🗝️"
        val migrated = NotepadDocument.migratedLegacy(original)
        assertEquals(1, migrated.size)
        assertEquals("My Notes", migrated.single().title)
        assertEquals(original, migrated.single().body)
        assertEquals("legacy-note", migrated.single().id)
    }

    @Test
    fun addingSeveralNotesPreservesEveryOtherSection() {
        val original = NotepadDocument.migratedLegacy("First note body")
        val withSecond = NotepadDocument.newSection(original, "second")
        val withThird = NotepadDocument.newSection(withSecond, "third")
        assertEquals(3, withThird.size)
        assertEquals("First note body", withThird[0].body)
        assertEquals("New Note", withThird[1].title)
        assertEquals("New Note", withThird[2].title)

        val renamed = NotepadDocument.title(withThird, "second", "Money making")
        val edited = NotepadDocument.body(renamed, "second", "Blood runes; 650 gp")
        assertEquals("Money making", edited[1].title)
        assertEquals("Blood runes; 650 gp", edited[1].body)
        assertEquals(original.single(), edited[0])
        assertEquals(withThird[2], edited[2])
    }

    @Test
    fun selectingAndDeletingNoteDoesNotEraseOtherNoteBodies() {
        val sections = listOf(
            NotepadSection("a", "Quests", "Rune Mysteries"),
            NotepadSection("b", "Flips", "Margin 1,500 gp"),
            NotepadSection("c", "Timers", "Herb run")
        )
        assertEquals("b", NotepadDocument.selected(sections, "b"))
        val remaining = NotepadDocument.remove(sections, "b")
        assertEquals(listOf("Quests", "Timers"), remaining.map { it.title })
        assertEquals(listOf("Rune Mysteries", "Herb run"), remaining.map { it.body })
        assertEquals("a", NotepadDocument.selected(remaining, "b"))
        assertNull(NotepadDocument.selected(emptyList(), "b"))
    }

    @Test
    fun emptyTitleRemainsEditableAndIsOnlyPlaceholderInTabs() {
        val note = listOf(NotepadSection("one", "Old", "Keep body"))
        val emptyTitle = NotepadDocument.title(note, "one", "")
        assertEquals("", emptyTitle.single().title)
        assertEquals("Untitled note", NotepadDocument.visibleTitle(emptyTitle.single()))
        assertEquals("Keep body", emptyTitle.single().body)
    }

    @Test
    fun newSectionRequiresUniqueId() {
        val notes = NotepadDocument.migratedLegacy("keep this")
        var refused = false
        try {
            NotepadDocument.newSection(notes, "legacy-note")
        } catch (_: IllegalArgumentException) {
            refused = true
        }
        assertTrue(refused)
        assertFalse(notes.single().body.isEmpty())
    }
}
