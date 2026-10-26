package com.mandecoy.osrscompanion

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class WikiFavorite(val id: String, val title: String, val url: String)

/** Local, privacy-friendly Wiki shortcuts. No server or RuneScape login. */
object WikiFavoriteStore {
    private const val PREFS = "wiki_favorites_v1"
    private const val KEY = "entries"

    fun isWikiUrl(url: String): Boolean {
        return try {
            val uri = Uri.parse(url.trim())
            val host = uri.host?.lowercase() ?: return false
            uri.scheme == "https" && (
                host == "oldschool.runescape.wiki" ||
                host == "runescape.wiki" ||
                host.endsWith(".oldschool.runescape.wiki")
            )
        } catch (_: Exception) { false }
    }

    fun all(context: Context): List<WikiFavorite> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, "[]") ?: "[]"
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { i ->
                val entry = array.optJSONObject(i) ?: return@mapNotNull null
                val id = entry.optString("id")
                val name = entry.optString("name")
                val url = entry.optString("url")
                if (id.isBlank() || !isWikiUrl(url)) null
                else WikiFavorite(id, name.ifBlank { "Wiki Favorite" }, url)
            }.take(100)
        } catch (_: Exception) { emptyList() }
    }

    fun find(context: Context, id: String): WikiFavorite? =
        all(context).firstOrNull { it.id == id }

    fun add(context: Context, title: String, url: String): WikiFavorite? {
        val cleanUrl = url.trim()
        if (!isWikiUrl(cleanUrl)) return null
        val saved = all(context)
        saved.firstOrNull { it.url == cleanUrl }?.let { return it }
        val label = title.trim().take(42).ifBlank {
            Uri.parse(cleanUrl).lastPathSegment?.replace('_', ' ')?.take(42) ?: "Wiki Favorite"
        }
        val favorite = WikiFavorite(UUID.randomUUID().toString().take(12), label, cleanUrl)
        save(context, saved + favorite)
        return favorite
    }

    fun remove(context: Context, id: String) {
        save(context, all(context).filterNot { it.id == id })
    }

    private fun save(context: Context, favorites: List<WikiFavorite>) {
        val array = JSONArray()
        favorites.take(100).forEach {
            array.put(JSONObject().put("id", it.id).put("name", it.title).put("url", it.url))
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY, array.toString()).apply()
    }
}
