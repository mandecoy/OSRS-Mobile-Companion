package com.mandecoy.osrscompanion

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/** Pre-indexed Wiki inventories, joined to live GE prices when displaying rankings. */
data class ShopCatalog(
    val shops: List<WikiShopDetail>,
    val generatedAt: String,
    val stockedPages: Int,
    val failedPages: Int,
    val isFallback: Boolean
)

object ShopCatalogApi {
    private const val CATALOG_URL =
        "https://raw.githubusercontent.com/mandecoy/OSRS-Mobile-Companion/main/docs/shop-catalog-v2.json"
    private const val USER_AGENT = "OSRS-Companion-Tool/0.7.3 (https://github.com/mandecoy/OSRS-Mobile-Companion)"
    private const val PREFS = "shop_catalog_index_v2"
    private const val RECHECK_MS = 4L * 60L * 60L * 1000L

    fun load(context: Context, force: Boolean = false): ShopCatalog {
        val file = File(context.filesDir, "shop-catalog-v2.json")
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val cached = if (file.isFile) runCatching {
            parse(file.readText(Charsets.UTF_8), isFallback = true)
        }.getOrNull() else null
        val fetchedAt = prefs.getLong("fetched", 0L)
        if (!force && cached != null &&
            System.currentTimeMillis() - fetchedAt in 0 until RECHECK_MS) return cached

        try {
            val connection = URL(CATALOG_URL).openConnection() as HttpURLConnection
            val text = try {
                connection.connectTimeout = 12_000
                connection.readTimeout = 35_000
                connection.setRequestProperty("User-Agent", USER_AGENT)
                connection.setRequestProperty("Accept", "application/json")
                if (connection.responseCode !in 200..299) {
                    throw IllegalStateException("Shop catalog HTTP " + connection.responseCode)
                }
                connection.inputStream.bufferedReader().use { it.readText() }
            } finally {
                connection.disconnect()
            }
            val catalog = parse(text)
            val temp = File(context.filesDir, "shop-catalog-v2.tmp")
            temp.writeText(text, Charsets.UTF_8)
            if (!temp.renameTo(file)) {
                file.writeText(text, Charsets.UTF_8)
                temp.delete()
            }
            prefs.edit().putLong("fetched", System.currentTimeMillis()).apply()
            return catalog
        } catch (error: Exception) {
            // Retain a complete previous catalog if remote data becomes unavailable.
            if (cached != null) return cached
            throw error
        }
    }

    internal fun parse(text: String, isFallback: Boolean = false): ShopCatalog {
        val json = JSONObject(text)
        require(json.optInt("schema") == 2) { "Unsupported shop index format" }
        val rows = json.getJSONArray("shops")
        require(rows.length() >= 400) { "Shop index is incomplete" }
        val shops = List(rows.length()) { i ->
            val o = rows.getJSONObject(i)
            val title = o.getString("title")
            val stockRows = o.optJSONArray("stock") ?: JSONArray()
            val inventory = List(stockRows.length()) { j ->
                val item = stockRows.getJSONObject(j)
                WikiShopStock(
                    item = item.getString("item"),
                    stock = item.optString("stock"),
                    restock = item.optString("restock"),
                    basePurchasePrice = if (item.isNull("purchase")) null
                        else item.optLong("purchase").takeIf { it >= 0L },
                    baseBuybackPrice = if (item.isNull("buyback")) null
                        else item.optLong("buyback").takeIf { it >= 0L },
                    shop = title,
                    currency = item.optString("currency", "unknown")
                )
            }
            WikiShopDetail(title, o.optString("location"),
                o.optString("notes"), inventory,
                currency = o.optString("currency", "unknown"))
        }
        return ShopCatalog(shops, json.optString("generatedAt"),
            json.optInt("stockedPages"), json.optInt("failedPages"), isFallback)
    }
}

/** Shop position comes from its single best GP/item opportunity, NOT a sum. */
data class RankedShop(
    val detail: WikiShopDetail,
    val standout: MoneyShopEvaluation?,
    val profitableItems: List<MoneyShopEvaluation>,
    val reliableItems: Int
) {
    val profitableCount: Int get() = profitableItems.size
    val standoutProfit: Long get() = standout?.netUnitProfit ?: Long.MIN_VALUE
    val otherItems: List<MoneyShopEvaluation>
        get() = profitableItems.filter { it.row.item != standout?.row?.item }.take(3)
}

object ShopRankings {
    /**
     * These shops charge Tokkul or other non-GP currency. Treating their
     * numeric stock prices as coins creates fabricated profit margins.
     */
    internal fun isKnownSpecialCurrencyShop(title: String): Boolean {
        val value = title.lowercase(Locale.ROOT)
        return value.startsWith("tzhaar-") ||
            value.contains("tokkul") ||
            value.contains("numulite")
    }

    internal fun isExcludedItem(itemName: String): Boolean {
        val value = itemName.trim().lowercase(Locale.ROOT)
        return value == "numulite" || value == "numulites" || value == "tokkul"
    }

    internal fun isSpecialCurrencyShop(shop: WikiShopDetail): Boolean {
        if (shop.currency != "coins") return true
        if (isKnownSpecialCurrencyShop(shop.title)) return true
        val notes = shop.notes.lowercase(Locale.ROOT)
        val location = shop.location.lowercase(Locale.ROOT)
        if (location.contains("mor ul rek") || location.contains("tzhaar city")) return true
        // Wiki descriptions often state the currency even when omitted in title.
        return notes.contains("sold for tokkul") ||
            notes.contains("bought with tokkul") ||
            notes.contains("using tokkul") ||
            notes.contains("only accept tokkul") ||
            notes.contains("with numulite") ||
            notes.contains("for numulites") ||
            notes.contains("using numulites") ||
            notes.contains("numulites as currency")
    }

    internal fun isBuyableStock(raw: String): Boolean {
        val text = raw.trim().lowercase(Locale.ROOT).replace(",", "")
        if (text.isEmpty() || text == "0" || text in setOf("none", "n/a", "-")) return false
        if (text in setOf("∞", "infinite", "unlimited")) return true
        return text.toLongOrNull()?.let { it > 0 } ?: false
    }

    fun evaluate(shops: List<WikiShopDetail>, snapshot: MarketSnapshot,
                 now: Long = System.currentTimeMillis()): List<RankedShop> =
        shops.map { shop ->
            val profitable = shop.stock.asSequence()
                .filter { !isSpecialCurrencyShop(shop) }
                .filter { isBuyableStock(it.stock) && it.currency == "coins" &&
                    !isExcludedItem(it.item) }
                .map { MoneyProfitCalculator.shop(it, snapshot, now) }
                .filter { (it.netUnitProfit ?: 0L) > 0L }
                .distinctBy { it.row.item.lowercase(Locale.ROOT) }
                .sortedByDescending { it.netUnitProfit ?: 0L }
                .toList()
            val reliable = profitable.count { opportunity ->
                opportunity.flags.none { it.contains("Stale") || it.contains("volume", true) }
            }
            RankedShop(shop, profitable.firstOrNull(), profitable, reliable)
        }.sortedWith(
            compareByDescending<RankedShop> { it.standoutProfit }
                .thenBy { it.detail.title.lowercase(Locale.ROOT) }
        )
}
