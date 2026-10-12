package com.mandecoy.osrscompanion

import android.content.Context
import android.net.Uri
import android.webkit.WebView
import org.json.JSONObject

/** One locally stored OSRS character name shared by the relevant web tools. */
object SavedPlayerName {
    private const val FILE = "player_profile_v1"
    private const val FIELD = "rsn"

    fun get(context: Context): String =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getString(FIELD, "") ?: ""

    fun save(context: Context, name: String) {
        val clean = name.trim().take(12)
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit().putString(FIELD, clean).apply()
    }

    /**
     * External sites own their markup and can change it without notice. Only
     * fill explicitly player/username-labelled empty inputs on approved hosts.
     * Never submit a form or replace what the user is already typing.
     */
    fun fillRelevantFields(webView: WebView, pageUrl: String?, username: String) {
        val name = username.trim().take(12)
        if (name.isEmpty()) return
        val host = try { Uri.parse(pageUrl).host?.lowercase() ?: "" }
                   catch (_: Exception) { "" }
        val supported = listOf("wiseoldman.net", "oldschool.tools", "tools.runescape.wiki")
        if (supported.none { host == it || host.endsWith(".$it") }) return
        val safe = JSONObject.quote(name)
        val script = """
            (function () {
              const saved = $safe;
              const re = /(?:username|user.name|player.name|player|rsn|account.name|character.name)/i;
              const elements = document.querySelectorAll('input');
              let updated = 0;
              for (const el of elements) {
                const type = (el.getAttribute('type') || 'text').toLowerCase();
                if (type !== 'text' && type !== 'search') continue;
                if (el.value || el === document.activeElement) continue;
                const hint = [
                  el.name, el.id, el.placeholder, el.getAttribute('aria-label'),
                  el.getAttribute('data-testid')
                ].filter(Boolean).join(' ');
                if (!re.test(hint)) continue;
                const setter = Object.getOwnPropertyDescriptor(
                  window.HTMLInputElement.prototype, 'value'
                )?.set;
                if (setter) setter.call(el, saved);
                else el.value = saved;
                el.dispatchEvent(new Event('input', { bubbles: true }));
                el.dispatchEvent(new Event('change', { bubbles: true }));
                updated++;
              }
              return updated;
            })();
        """.trimIndent()
        webView.evaluateJavascript(script, null)
    }
}
