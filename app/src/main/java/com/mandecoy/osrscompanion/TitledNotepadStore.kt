package com.mandecoy.osrscompanion

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * A section is one independently titled, editable note. All data remains on
 * the device, with no network calls or change to the existing app settings.
 */
data class NotepadSection(
    val id: String,
    val title: String,
    val body: String
)

/** Pure mutations can be unit tested without Android or SharedPreferences. */
object NotepadDocument {
    fun migratedLegacy(body: String): List<NotepadSection> =
        listOf(NotepadSection("legacy-note", "My Notes", body))

    fun newSection(existing: List<NotepadSection>, id: String): List<NotepadSection> {
        require(id.isNotBlank() && existing.none { it.id == id })
        return existing + NotepadSection(id, "New Note", "")
    }

    fun title(existing: List<NotepadSection>, id: String, value: String): List<NotepadSection> =
        existing.map { if (it.id == id) it.copy(title = value.take(100)) else it }

    fun body(existing: List<NotepadSection>, id: String, value: String): List<NotepadSection> =
        existing.map { if (it.id == id) it.copy(body = value) else it }

    fun remove(existing: List<NotepadSection>, id: String): List<NotepadSection> =
        existing.filterNot { it.id == id }

    fun selected(sections: List<NotepadSection>, preferredId: String?): String? =
        sections.firstOrNull { it.id == preferredId }?.id ?: sections.firstOrNull()?.id

    fun visibleTitle(section: NotepadSection): String =
        section.title.trim().ifBlank { "Untitled note" }
}

object TitledNotepadStore {
    private const val PREFS = "persistent_notepad_v2"
    private const val JSON_KEY = "sections"
    private const val SELECTED_KEY = "selected_section"

    private fun encode(sections: List<NotepadSection>): String {
        val array = JSONArray()
        sections.forEach { section ->
            array.put(JSONObject()
                .put("id", section.id)
                .put("title", section.title)
                .put("body", section.body))
        }
        return array.toString()
    }

    internal fun decode(raw: String): List<NotepadSection> {
        val rows = JSONArray(raw)
        return buildList {
            val seen = mutableSetOf<String>()
            for (i in 0 until rows.length()) {
                val row = rows.optJSONObject(i) ?: continue
                val id = row.optString("id", "").trim()
                if (id.isBlank() || !seen.add(id)) continue
                add(NotepadSection(
                    id = id,
                    title = row.optString("title", ""),
                    body = row.optString("body", "")
                ))
            }
        }
    }

    fun load(context: Context): List<NotepadSection> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val stored = prefs.getString(JSON_KEY, null)
        if (stored != null) {
            // An intentionally empty list is not a request to reimport a
            // previously deleted legacy note. Recover from a backup if needed.
            val parsed = runCatching { decode(stored) }.getOrNull()
            if (parsed != null) return parsed
            val backup = prefs.getString("previous_good", null)
            return runCatching { decode(backup ?: "[]") }.getOrDefault(emptyList())
        }

        // One-time non-destructive migration. Do not remove the old value:
        // it remains a backup, and the new format never writes to v1.
        val legacy = context.getSharedPreferences("persistent_notepad_v1", Context.MODE_PRIVATE)
            .getString("note", null)
        val migrated = NotepadDocument.migratedLegacy(legacy ?: "")
        prefs.edit()
            .putString(JSON_KEY, encode(migrated))
            .putString(SELECTED_KEY, migrated.first().id)
            .apply()
        return migrated
    }

    fun selected(context: Context, sections: List<NotepadSection>): String? {
        val id = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(SELECTED_KEY, null)
        return NotepadDocument.selected(sections, id)
    }

    fun save(context: Context, sections: List<NotepadSection>, selectedId: String?) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val previous = prefs.getString(JSON_KEY, null)
        val editor = prefs.edit()
        // Keep an extra copy in case a future write is interrupted.
        if (previous != null && runCatching { decode(previous) }.isSuccess) {
            editor.putString("previous_good", previous)
        }
        editor.putString(JSON_KEY, encode(sections))
            .putString(SELECTED_KEY, NotepadDocument.selected(sections, selectedId))
            .apply()
    }
}
