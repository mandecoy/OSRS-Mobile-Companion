package com.mandecoy.osrscompanion

import android.content.Context
import android.net.Uri
import android.webkit.WebView
import org.json.JSONObject

/** One locally stored OSRS character name shared by the relevant web tools. */
object SavedPlayerName {
    private const val FILE = "player_profile_v1"
    private const val FIELD = "rsn"

    // Keep the profile and whether its toolbar is shown independent.
    // Hidden means COLLAPSED UI only; the name still autofills supported sites.
    private const val TOOLBAR_VISIBLE = "username_toolbar_visible"

    fun isToolbarVisible(context: Context): Boolean =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getBoolean(TOOLBAR_VISIBLE, true)

    fun setToolbarVisible(context: Context, visible: Boolean) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit().putBoolean(TOOLBAR_VISIBLE, visible).apply()
    }

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
    /**
     * Also remember a player name entered directly into a supported site's own
     * username field. evaluateJavascript reads only labelled text/search fields;
     * no broad JavaScript bridge is exposed to third-party websites.
     */
    fun readEnteredName(webView: WebView, pageUrl: String?, onRead: (String) -> Unit) {
        val host = try { Uri.parse(pageUrl).host?.lowercase() ?: "" }
                   catch (_: Exception) { "" }
        val supported = listOf("wiseoldman.net", "oldschool.tools", "tools.runescape.wiki")
        if (supported.none { host == it || host.endsWith(".$it") }) return

        val script = """
            (function() {
              const re = /(?:username|user.name|player.name|player|rsn|account.name|character.name)/i;
              for (const el of document.querySelectorAll('input')) {
                const type = (el.getAttribute('type') || 'text').toLowerCase();
                if (type !== 'text' && type !== 'search') continue;
                const label = [el.name, el.id, el.placeholder,
                  el.getAttribute('aria-label'), el.getAttribute('data-testid')]
                  .filter(Boolean).join(' ');
                if (!re.test(label)) continue;
                const value = (el.value || '').trim();
                if (value.length >= 1 && value.length <= 12 &&
                    /^[a-zA-Z0-9 _-]+$/.test(value)) return value;
              }
              return null;
            })();
        """.trimIndent()

        webView.evaluateJavascript(script) { result ->
            val name = try {
                org.json.JSONTokener(result).nextValue() as? String
            } catch (_: Exception) { null }
            if (name != null && name.isNotBlank() && name != get(webView.context)) {
                save(webView.context, name)
                onRead(name)
            }
        }
    }

    /**
     * When the user taps Fill, prefer a focused editable field, followed by a
     * labelled player field and finally one unambiguous visible search/text
     * field. Never auto-submit and never inject on unrelated domains.
     *
     * Returns one of "filled", "already", "missing", "unsupported" so the
     * host app can explain why a site did or did not accept the username.
     */
    fun fillRelevantFields(
        webView: WebView,
        pageUrl: String?,
        username: String,
        manual: Boolean = false,
        onResult: ((String) -> Unit)? = null
    ) {
        val name = username.trim().take(12)
        if (name.isBlank()) {
            onResult?.invoke("empty")
            return
        }
        val host = try { Uri.parse(pageUrl).host?.lowercase() ?: "" }
                   catch (_: Exception) { "" }
        val supported = listOf(
            "wiseoldman.net", "oldschool.tools", "runescape.wiki",
            "theoatrix.net", "osrsportal.com"
        )
        if (supported.none { host == it || host.endsWith(".$it") }) {
            onResult?.invoke("unsupported")
            return
        }
        val script = """
            (function () {
              try {
                const saved = ${JSONObject.quote(name)};
                const manual = ${if (manual) "true" else "false"};
                const re = /(?:username|user.name|player.name|player|rsn|account.name|character.name|hiscores)/i;
                function visible(el) {
                  const style = window.getComputedStyle(el);
                  return style.visibility !== 'hidden' &&
                    style.display !== 'none' && el.getClientRects().length > 0;
                }
                function editable(el) {
                  if (!el || !visible(el) || el.disabled || el.readOnly) return false;
                  const kind = (el.tagName || '').toLowerCase();
                  if (kind === 'textarea') return true;
                  if (el.isContentEditable) return true;
                  if (kind !== 'input') return false;
                  const type = (el.getAttribute('type') || 'text').toLowerCase();
                  return type === 'text' || type === 'search' || type === 'url';
                }
                function hint(el) {
                  let label = [];
                  if (el.labels) label = Array.from(el.labels).map(x => x.textContent);
                  return [
                    el.name, el.id, el.placeholder, el.getAttribute('aria-label'),
                    el.getAttribute('data-testid'), el.getAttribute('autocomplete'),
                    ...label
                  ].filter(Boolean).join(' ');
                }
                const all = Array.from(document.querySelectorAll(
                    'input, textarea, [contenteditable="true"], [role="combobox"]'))
                  .filter(editable);
                const focused = document.activeElement;
                let target = null;
                if (manual && editable(focused)) target = focused;
                if (!target) target = all.find(el => re.test(hint(el)) && !el.value);
                if (!target && manual) {
                  const empty = all.filter(el => !(el.value || el.textContent || '').trim());
                  if (empty.length === 1) target = empty[0];
                }
                if (!target) return 'missing';
                if (!manual && (target.value || target.textContent || '').trim())
                  return 'already';
                if (target.isContentEditable) {
                  target.textContent = saved;
                  target.dispatchEvent(new InputEvent('input', {
                    bubbles: true, data: saved, inputType: 'insertText'
                  }));
                } else {
                  const proto = target.tagName.toLowerCase() === 'textarea'
                    ? window.HTMLTextAreaElement.prototype
                    : window.HTMLInputElement.prototype;
                  const setter = Object.getOwnPropertyDescriptor(proto, 'value')?.set;
                  if (setter) setter.call(target, saved);
                  else target.value = saved;
                  target.dispatchEvent(new Event('input', {bubbles: true}));
                  target.dispatchEvent(new Event('change', {bubbles: true}));
                }
                return 'filled';
              } catch (ex) {
                return 'error';
              }
            })();
        """.trimIndent()
        try {
            webView.evaluateJavascript(script) { result ->
                val status = try { org.json.JSONTokener(result).nextValue() as? String }
                             catch (_: Exception) { null }
                onResult?.invoke(status ?: "error")
            }
        } catch (_: Exception) {
            onResult?.invoke("error")
        }
    }
}
