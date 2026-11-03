package com.mandecoy.osrscompanion

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

/**
 * Native Money Making sources. The Wiki is queried as structured MediaWiki
 * JSON/HTML, never opened in a WebView. Pages may change: parsing failures
 * produce an error or an explicit unknown price rather than a fabricated margin.
 */
data class WikiMoneyMethod(
    val title: String,
    val category: String,
    val wikiHourlyGp: Long?,
    val skills: String,
    val intensity: String,
    val members: Boolean,
    val page: String
)

data class WikiShopStock(
    val item: String,
    val stock: String,
    val restock: String,
    val basePurchasePrice: Long?,
    val baseBuybackPrice: Long?,
    val shop: String
)

data class WikiShopDetail(
    val title: String,
    val location: String,
    val notes: String,
    val stock: List<WikiShopStock>
)

object MoneyWikiApi {
    private const val BASE = "https://oldschool.runescape.wiki/api.php"
    private const val USER_AGENT = "OSRS-Companion-Tool/0.7.0 (https://github.com/mandecoy/OSRS-Mobile-Companion; Wiki Money Making integration)"
    private const val PREF = "money_wiki_cache_v1"
    private val categories = listOf("Processing", "Skilling", "Collecting", "Combat", "Recurring", "Free-to-play")

    fun allCategories(): List<String> = categories

    fun loadMethods(context: Context, refresh: Boolean = false): List<WikiMoneyMethod> {
        val cached = cacheRead(context, "method_list", 60L * 60L * 1000L, refresh)
        if (cached != null) return parseMethodCache(cached)
        val result = mutableListOf<WikiMoneyMethod>()
        val errors = mutableListOf<String>()
        categories.forEach { category ->
            try {
                val html = parsePage("Money_making_guide/" + category)
                result += parseMoneyTable(html, category)
            } catch (error: Exception) {
                errors += category + ": " + (error.message ?: "unavailable")
            }
        }
        if (result.isEmpty()) throw IllegalStateException("No Wiki methods could be loaded. " + errors.joinToString("; "))
        val deduped = result.distinctBy { it.page.lowercase(Locale.ROOT) }
        val array = JSONArray()
        deduped.forEach { method ->
            array.put(JSONObject().put("title", method.title).put("category", method.category)
                .put("profit", method.wikiHourlyGp).put("skills", method.skills)
                .put("intensity", method.intensity).put("members", method.members)
                .put("page", method.page))
        }
        if (errors.isEmpty()) cacheWrite(context, "method_list", array.toString())
        return deduped
    }

    fun loadShops(context: Context, refresh: Boolean = false): List<String> {
        val cached = cacheRead(context, "shop_directory", 24L * 60L * 60L * 1000L, refresh)
        if (cached != null) {
            val arr = JSONArray(cached)
            return List(arr.length()) { arr.getString(it) }
        }
        val titles = mutableListOf<String>()
        var cmcontinue: String? = null
        do {
            val args = mutableMapOf("action" to "query", "format" to "json",
                "list" to "categorymembers", "cmtitle" to "Category:Shops",
                "cmtype" to "page", "cmlimit" to "max")
            cmcontinue?.let { args["cmcontinue"] = it }
            val root = JSONObject(fetch(args))
            val rows = root.getJSONObject("query").getJSONArray("categorymembers")
            for (i in 0 until rows.length()) {
                val title = rows.getJSONObject(i).optString("title").trim()
                if (title.isNotEmpty()) titles.add(title)
            }
            cmcontinue = root.optJSONObject("continue")?.optString("cmcontinue")?.takeIf { it.isNotEmpty() }
        } while (cmcontinue != null)
        if (titles.isEmpty()) throw IllegalStateException("The OSRS Wiki shop directory returned no shops.")
        val sorted = titles.distinct().sortedWith(String.CASE_INSENSITIVE_ORDER)
        cacheWrite(context, "shop_directory", JSONArray(sorted).toString())
        return sorted
    }

    fun loadShopDetail(context: Context, title: String, refresh: Boolean = false): WikiShopDetail {
        val key = "shop:" + title
        val cached = cacheRead(context, key, 24L * 60L * 60L * 1000L, refresh)
        if (cached != null) return parseShopCache(JSONObject(cached))
        val html = parsePage(title)
        val result = parseShop(html, title)
        val data = JSONObject().put("title", result.title).put("location", result.location)
            .put("notes", result.notes)
        val stocks = JSONArray()
        result.stock.forEach {
            stocks.put(JSONObject().put("item", it.item).put("stock", it.stock)
                .put("restock", it.restock).put("purchase", it.basePurchasePrice)
                .put("buyback", it.baseBuybackPrice))
        }
        data.put("items", stocks)
        cacheWrite(context, key, data.toString())
        return result
    }

    /** Guide details appear as native text, not embedded Wiki HTML. */
    fun loadMethodDescription(page: String): String {
        val html = parsePage(page)
        val doc = Jsoup.parse(html)
        val paragraphs = doc.select(".mw-parser-output > p, .mw-parser-output > ul > li")
            .map { it.text().trim() }
            .filter { it.length > 35 && !it.contains("This page has a money making guide") }
            .take(9)
        return paragraphs.joinToString("\n\n").ifBlank {
            "No guide description was available for this method."
        }
    }

    private fun parseMethodCache(raw: String): List<WikiMoneyMethod> {
        val rows = JSONArray(raw)
        return List(rows.length()) { i ->
            val o = rows.getJSONObject(i)
            WikiMoneyMethod(o.getString("title"), o.getString("category"),
                if (o.isNull("profit")) null else o.getLong("profit"),
                o.optString("skills"), o.optString("intensity"),
                o.optBoolean("members", true), o.getString("page"))
        }
    }

    internal fun parseMoneyTable(html: String, category: String): List<WikiMoneyMethod> {
        val document = Jsoup.parse(html)
        val table = document.select("table").firstOrNull {
            val labels = it.select("th").take(12).joinToString(" ") { h -> h.text().lowercase() }
            labels.contains("hourly profit") && labels.contains("method")
        } ?: return emptyList()
        val results = mutableListOf<WikiMoneyMethod>()
        table.select("tr").forEach { row ->
            val cells = row.children().filter { it.tagName() == "td" }
            if (cells.size < 2) return@forEach
            val link = cells[0].select("a[href]").firstOrNull {
                it.attr("href").contains("Money_making_guide/", ignoreCase = true)
            }
            val title = link?.text()?.trim()?.ifEmpty { null }
                ?: cells[0].text().trim()
            if (title.isEmpty() || title.equals("Method", true)) return@forEach
            val profit = parseGpNumber(cells[1].text())
            val page = link?.attr("href")?.let { href ->
                when {
                    href.startsWith("/w/") -> href.removePrefix("/w/").substringBefore("#")
                    href.contains("oldschool.runescape.wiki/w/") ->
                        href.substringAfter("oldschool.runescape.wiki/w/").substringBefore("#")
                    else -> ""
                }
            }?.let { java.net.URLDecoder.decode(it, "UTF-8") }
                ?.replace('_', ' ')
                ?.takeIf { it.startsWith("Money making guide/", ignoreCase = true) ||
                    it.startsWith("Money_making_guide/", ignoreCase = true) }
                ?: "Money_making_guide/" + title.replace(' ', '_')
            val skills = cells.getOrNull(2)?.text()?.take(220) ?: ""
            val intensity = cells.getOrNull(4)?.text()?.take(80)
                ?: cells.getOrNull(3)?.text()?.take(80) ?: ""
            val membershipText = cells.getOrNull(5)?.text()?.lowercase() ?: ""
            results.add(WikiMoneyMethod(title, category, profit, skills, intensity,
                category != "Free-to-play" && membershipText !in listOf("no", "0", "false"), page))
        }
        return results.distinctBy { it.page.lowercase(Locale.ROOT) }
    }

    internal fun parseShop(html: String, title: String): WikiShopDetail {
        val doc = Jsoup.parse(html)
        val infobox = doc.select("table.infobox, .infobox").firstOrNull()
        val location = infobox?.select("tr")?.firstOrNull {
            it.text().trim().startsWith("Location ", ignoreCase = true)
        }?.text()?.removePrefix("Location")?.trim() ?: ""
        val description = doc.select(".mw-parser-output > p").firstOrNull {
            it.text().length > 35
        }?.text()?.take(360) ?: ""
        val stock = mutableListOf<WikiShopStock>()
        doc.select("table").forEach { table ->
            val headings = table.select("th").take(10).joinToString(" ") { it.text().lowercase() }
            if (!headings.contains("stock") && !headings.contains("number in stock")) return@forEach
            if (!headings.contains("price") && !headings.contains("sold at")) return@forEach
            table.select("tr").forEach { row ->
                val cells = row.children().filter { it.tagName() == "td" }
                if (cells.size < 4) return@forEach
                // Stock templates usually use one icon column followed by Item,
                // Number in stock, Restock time, Price sold at, Price bought at.
                val itemIndex = if (cells.size >= 6) 1 else 0
                val nameCell = cells.getOrNull(itemIndex) ?: return@forEach
                val itemName = nameCell.select("a[title]").firstOrNull {
                    !it.attr("title").startsWith("File:")
                }?.attr("title")?.trim()?.ifBlank { null }
                    ?: nameCell.text().replace(Regex("\\[[^]]*]"), "").trim()
                if (itemName.isBlank() || itemName.equals("Item", true)) return@forEach
                val stockCell = cells.getOrNull(itemIndex + 1)
                val restockCell = cells.getOrNull(itemIndex + 2)
                val costCell = cells.getOrNull(itemIndex + 3)
                val buybackCell = cells.getOrNull(itemIndex + 4)
                val cost = costCell?.text()?.let { parseExactCoinNumber(it) }
                stock.add(WikiShopStock(itemName, stockCell?.text().orEmpty(),
                    restockCell?.text().orEmpty(), cost,
                    buybackCell?.text()?.let { parseExactCoinNumber(it) }, title))
            }
        }
        return WikiShopDetail(title, location, description, stock.distinctBy { it.item })
    }

    internal fun parseGpNumber(text: String): Long? {
        val match = Regex("([0-9][0-9,]*)").find(text.replace("\u00a0", " ")) ?: return null
        return match.groupValues[1].replace(",", "").toLongOrNull()
    }

    internal fun parseExactCoinNumber(text: String): Long? {
        val plain = text.trim().replace(",", "").replace("\u00a0", "")
        return plain.toLongOrNull()?.takeIf { it >= 0 }
    }

    private fun parseShopCache(obj: JSONObject): WikiShopDetail {
        val rows = obj.optJSONArray("items") ?: JSONArray()
        return WikiShopDetail(obj.getString("title"), obj.optString("location"),
            obj.optString("notes"), List(rows.length()) { i ->
                val o = rows.getJSONObject(i)
                WikiShopStock(o.getString("item"), o.optString("stock"), o.optString("restock"),
                    if (o.isNull("purchase")) null else o.getLong("purchase"),
                    if (o.isNull("buyback")) null else o.getLong("buyback"),
                    obj.getString("title"))
            })
    }

    private fun parsePage(page: String): String {
        val root = JSONObject(fetch(mapOf("action" to "parse", "format" to "json",
            "page" to page, "prop" to "text")))
        val parse = root.optJSONObject("parse")
            ?: throw IllegalStateException(root.optJSONObject("error")?.optString("info")
                ?: "Wiki article not available: " + page)
        return parse.getJSONObject("text").getString("*")
    }

    private fun fetch(params: Map<String, String>): String {
        val query = params.entries.joinToString("&") {
            URLEncoder.encode(it.key, "UTF-8") + "=" + URLEncoder.encode(it.value, "UTF-8")
        }
        val conn = URL(BASE + "?" + query).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 12_000
            conn.readTimeout = 25_000
            conn.setRequestProperty("User-Agent", USER_AGENT)
            conn.setRequestProperty("Accept", "application/json")
            if (conn.responseCode !in 200..299) throw IllegalStateException("Wiki HTTP " + conn.responseCode)
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private fun cacheRead(context: Context, key: String, maxAge: Long, force: Boolean): String? {
        if (force) return null
        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val age = System.currentTimeMillis() - prefs.getLong(key + ":time", 0L)
        return if (age in 0 until maxAge) prefs.getString(key, null) else null
    }

    private fun cacheWrite(context: Context, key: String, value: String) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
            .putString(key, value).putLong(key + ":time", System.currentTimeMillis()).apply()
    }
}
