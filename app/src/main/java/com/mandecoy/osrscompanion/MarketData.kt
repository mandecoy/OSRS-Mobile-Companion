package com.mandecoy.osrscompanion

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit
import kotlin.math.floor
import kotlin.math.min

data class MarketItem(
    val id: Int,
    val name: String,
    val limit: Int?,
    val highAlch: Int?
)

data class LatestPrice(
    val high: Int?,
    val low: Int?,
    val highTime: Long?,
    val lowTime: Long?
)

data class HourPrice(
    val avgHigh: Int?,
    val avgLow: Int?,
    val highVolume: Long,
    val lowVolume: Long
) {
    val volume: Long get() = highVolume + lowVolume
}

data class MarketSnapshot(
    val items: List<MarketItem>,
    val latest: Map<Int, LatestPrice>,
    val hourly: Map<Int, HourPrice>,
    val fetchedAt: Long
)

data class HistoryPoint(
    val timestamp: Long,
    val avgHigh: Int?,
    val avgLow: Int?,
    val highVolume: Long,
    val lowVolume: Long
)

object MarketApi {
    private const val BASE = "https://prices.runescape.wiki/api/v1/osrs"
    private const val USER_AGENT =
        "OSRS-Companion-Tool/0.5.0 (+https://github.com/mandecoy/OSRS-Mobile-Companion)"
    private const val PREFS = "market_api_cache_v1"
    private const val MAPPING = "mapping_json"
    private const val MAPPING_TIME = "mapping_time"
    private const val MAPPING_MAX_AGE = 24L * 60L * 60L * 1000L

    fun loadSnapshot(context: Context, forceMapping: Boolean = false): MarketSnapshot {
        val mapping = loadMapping(context, forceMapping)
        val latest = parseLatest(fetchText(BASE + "/latest"))
        val hourly = try {
            parseHourly(fetchText(BASE + "/1h"))
        } catch (_: Exception) {
            emptyMap()
        }
        return MarketSnapshot(mapping, latest, hourly, System.currentTimeMillis())
    }

    fun loadLatestOnly(): Map<Int, LatestPrice> =
        parseLatest(fetchText(BASE + "/latest"))

    fun loadHistory(itemId: Int, timestep: String = "1h"): List<HistoryPoint> {
        val safeStep = if (timestep in listOf("5m", "1h", "6h", "24h")) timestep else "1h"
        val root = JSONObject(fetchText(BASE + "/timeseries?timestep=" + safeStep + "&id=" + itemId))
        val data = root.optJSONArray("data") ?: JSONArray()
        return List(data.length()) { index ->
            val row = data.getJSONObject(index)
            HistoryPoint(
                timestamp = row.optLong("timestamp", 0L),
                avgHigh = nullableInt(row, "avgHighPrice"),
                avgLow = nullableInt(row, "avgLowPrice"),
                highVolume = nullableLong(row, "highPriceVolume"),
                lowVolume = nullableLong(row, "lowPriceVolume")
            )
        }
    }

    private fun loadMapping(context: Context, force: Boolean): List<MarketItem> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val cached = prefs.getString(MAPPING, null)
        val cachedAt = prefs.getLong(MAPPING_TIME, 0L)
        val fresh = cached != null && System.currentTimeMillis() - cachedAt < MAPPING_MAX_AGE

        val raw = if (!force && fresh) {
            cached!!
        } else {
            try {
                fetchText(BASE + "/mapping").also {
                    prefs.edit()
                        .putString(MAPPING, it)
                        .putLong(MAPPING_TIME, System.currentTimeMillis())
                        .apply()
                }
            } catch (error: Exception) {
                cached ?: throw error
            }
        }
        return parseMapping(raw)
    }

    private fun parseMapping(raw: String): List<MarketItem> {
        val array = JSONArray(raw)
        return List(array.length()) { index ->
            val row = array.getJSONObject(index)
            MarketItem(
                id = row.getInt("id"),
                name = row.getString("name"),
                limit = nullableInt(row, "limit"),
                highAlch = nullableInt(row, "highalch")
            )
        }.sortedBy { it.name.lowercase() }
    }

    private fun parseLatest(raw: String): Map<Int, LatestPrice> {
        val data = JSONObject(raw).getJSONObject("data")
        val result = LinkedHashMap<Int, LatestPrice>()
        val keys = data.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val id = key.toIntOrNull() ?: continue
            val row = data.getJSONObject(key)
            result[id] = LatestPrice(
                high = nullableInt(row, "high"),
                low = nullableInt(row, "low"),
                highTime = nullableLongOrNull(row, "highTime"),
                lowTime = nullableLongOrNull(row, "lowTime")
            )
        }
        return result
    }

    private fun parseHourly(raw: String): Map<Int, HourPrice> {
        val data = JSONObject(raw).getJSONObject("data")
        val result = LinkedHashMap<Int, HourPrice>()
        val keys = data.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val id = key.toIntOrNull() ?: continue
            val row = data.getJSONObject(key)
            result[id] = HourPrice(
                avgHigh = nullableInt(row, "avgHighPrice"),
                avgLow = nullableInt(row, "avgLowPrice"),
                highVolume = nullableLong(row, "highPriceVolume"),
                lowVolume = nullableLong(row, "lowPriceVolume")
            )
        }
        return result
    }

    private fun fetchText(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 15_000
            connection.readTimeout = 20_000
            connection.setRequestProperty("User-Agent", USER_AGENT)
            connection.setRequestProperty("Accept", "application/json")
            val code = connection.responseCode
            if (code !in 200..299) {
                throw IllegalStateException("Price API returned HTTP " + code)
            }
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun nullableInt(obj: JSONObject, key: String): Int? =
        if (!obj.has(key) || obj.isNull(key)) null else obj.optInt(key)

    private fun nullableLong(obj: JSONObject, key: String): Long =
        if (!obj.has(key) || obj.isNull(key)) 0L else obj.optLong(key, 0L)

    private fun nullableLongOrNull(obj: JSONObject, key: String): Long? =
        if (!obj.has(key) || obj.isNull(key)) null else obj.optLong(key)
}

fun geTax(sellPrice: Int): Int {
    if (sellPrice < 50) return 0
    return min(5_000_000, floor(sellPrice * 0.02).toInt())
}

fun gp(value: Long): String {
    val abs = kotlin.math.abs(value)
    return when {
        abs >= 1_000_000_000L -> String.format("%.2fb", value / 1_000_000_000.0)
        abs >= 1_000_000L -> String.format("%.2fm", value / 1_000_000.0)
        abs >= 1_000L -> String.format("%.1fk", value / 1_000.0)
        else -> value.toString()
    }
}

fun midpoint(price: LatestPrice?): Int? {
    if (price == null) return null
    val high = price.high
    val low = price.low
    return when {
        high != null && low != null -> ((high.toLong() + low.toLong()) / 2L).toInt()
        high != null -> high
        low != null -> low
        else -> null
    }
}

data class PortfolioPosition(
    val itemId: Int,
    val itemName: String,
    val quantity: Long,
    val averageBuyPrice: Long
)

object PortfolioStore {
    private const val PREFS = "portfolio_v1"
    private const val POSITIONS = "positions"
    private const val REALIZED = "realized_gp"

    fun positions(context: Context): List<PortfolioPosition> {
        return try {
            val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(POSITIONS, "[]") ?: "[]"
            val array = JSONArray(raw)
            List(array.length()) { index ->
                val row = array.getJSONObject(index)
                PortfolioPosition(
                    itemId = row.getInt("itemId"),
                    itemName = row.getString("itemName"),
                    quantity = row.getLong("quantity"),
                    averageBuyPrice = row.getLong("averageBuyPrice")
                )
            }.filter { it.quantity > 0L }.sortedBy { it.itemName.lowercase() }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun realized(context: Context): Long =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(REALIZED, 0L)

    fun addBuy(context: Context, item: MarketItem, quantity: Long, priceEach: Long) {
        if (quantity <= 0L || priceEach <= 0L) return
        val list = positions(context).toMutableList()
        val index = list.indexOfFirst { it.itemId == item.id }
        if (index >= 0) {
            val old = list[index]
            val totalQty = old.quantity + quantity
            val totalCost = old.averageBuyPrice * old.quantity + priceEach * quantity
            list[index] = old.copy(
                quantity = totalQty,
                averageBuyPrice = if (totalQty > 0L) totalCost / totalQty else priceEach
            )
        } else {
            list += PortfolioPosition(item.id, item.name, quantity, priceEach)
        }
        savePositions(context, list)
    }

    fun sell(
        context: Context,
        itemId: Int,
        quantity: Long,
        sellPriceEach: Long
    ): Long? {
        if (quantity <= 0L || sellPriceEach <= 0L) return null
        val list = positions(context).toMutableList()
        val index = list.indexOfFirst { it.itemId == itemId }
        if (index < 0) return null
        val old = list[index]
        val qty = quantity.coerceAtMost(old.quantity)
        if (qty <= 0L) return null
        val taxEach = geTax(sellPriceEach.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()).toLong()
        val realized = (sellPriceEach - taxEach - old.averageBuyPrice) * qty
        val remaining = old.quantity - qty
        if (remaining <= 0L) list.removeAt(index)
        else list[index] = old.copy(quantity = remaining)
        savePositions(context, list)
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().putLong(REALIZED, prefs.getLong(REALIZED, 0L) + realized).apply()
        return realized
    }

    fun remove(context: Context, itemId: Int) {
        savePositions(context, positions(context).filterNot { it.itemId == itemId })
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }

    private fun savePositions(context: Context, positions: List<PortfolioPosition>) {
        val array = JSONArray()
        positions.forEach {
            array.put(
                JSONObject()
                    .put("itemId", it.itemId)
                    .put("itemName", it.itemName)
                    .put("quantity", it.quantity)
                    .put("averageBuyPrice", it.averageBuyPrice)
            )
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(POSITIONS, array.toString())
            .apply()
    }
}

object WatchlistStore {
    private const val PREFS = "market_watchlist_v1"
    private const val KEY = "ids"

    fun ids(context: Context): Set<Int> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet(KEY, emptySet()) ?: emptySet()
        return raw.mapNotNull { it.toIntOrNull() }.toSet()
    }

    fun toggle(context: Context, itemId: Int) {
        val next = ids(context).toMutableSet()
        if (!next.add(itemId)) next.remove(itemId)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putStringSet(KEY, next.map { it.toString() }.toSet())
            .apply()
    }
}

enum class AlertDirection { ABOVE, BELOW }

data class PriceAlert(
    val id: Int,
    val itemId: Int,
    val itemName: String,
    val targetPrice: Long,
    val direction: AlertDirection
)

object PriceAlertStore {
    private const val PREFS = "market_alerts_v1"
    private const val KEY = "alerts"
    private const val WORK = "osrs_market_alerts"

    fun all(context: Context): List<PriceAlert> {
        return try {
            val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY, "[]") ?: "[]"
            val array = JSONArray(raw)
            List(array.length()) { index ->
                val row = array.getJSONObject(index)
                PriceAlert(
                    id = row.getInt("id"),
                    itemId = row.getInt("itemId"),
                    itemName = row.getString("itemName"),
                    targetPrice = row.getLong("targetPrice"),
                    direction = AlertDirection.valueOf(row.getString("direction"))
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun add(
        context: Context,
        item: MarketItem,
        targetPrice: Long,
        direction: AlertDirection
    ) {
        if (targetPrice <= 0L) return
        val id = ((System.currentTimeMillis() xor item.id.toLong()) and 0x7fffffff).toInt()
        save(
            context,
            all(context) + PriceAlert(id, item.id, item.name, targetPrice, direction)
        )
        ensureScheduled(context)
    }

    fun remove(context: Context, id: Int) {
        val next = all(context).filterNot { it.id == id }
        save(context, next)
        if (next.isEmpty()) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK)
        }
    }

    fun ensureScheduled(context: Context) {
        if (all(context).isEmpty()) return
        val work = PeriodicWorkRequestBuilder<MarketAlertWorker>(15, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK,
            ExistingPeriodicWorkPolicy.UPDATE,
            work
        )
    }

    private fun save(context: Context, alerts: List<PriceAlert>) {
        val array = JSONArray()
        alerts.forEach {
            array.put(
                JSONObject()
                    .put("id", it.id)
                    .put("itemId", it.itemId)
                    .put("itemName", it.itemName)
                    .put("targetPrice", it.targetPrice)
                    .put("direction", it.direction.name)
            )
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, array.toString())
            .apply()
    }
}

class MarketAlertWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {
    override fun doWork(): Result {
        val alerts = PriceAlertStore.all(applicationContext)
        if (alerts.isEmpty()) return Result.success()

        val latest = try {
            MarketApi.loadLatestOnly()
        } catch (_: Exception) {
            return Result.retry()
        }

        alerts.forEach { alert ->
            val price = midpoint(latest[alert.itemId])?.toLong() ?: return@forEach
            val hit = when (alert.direction) {
                AlertDirection.ABOVE -> price >= alert.targetPrice
                AlertDirection.BELOW -> price <= alert.targetPrice
            }
            if (hit) {
                MarketNotifications.show(applicationContext, alert, price)
                PriceAlertStore.remove(applicationContext, alert.id)
            }
        }
        return Result.success()
    }
}

object MarketNotifications {
    private const val CHANNEL = "osrs_market_alerts"

    fun show(context: Context, alert: PriceAlert, price: Long) {
        ensureChannel(context)
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val pending = launch?.let {
            PendingIntent.getActivity(
                context,
                alert.id,
                it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
        val directionText =
            if (alert.direction == AlertDirection.ABOVE) "rose to " else "fell to "
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentTitle(alert.itemName + " price alert")
            .setContentText(directionText + gp(price) + " gp")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()

        if (
            Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            NotificationManagerCompat.from(context).notify(alert.id, notification)
        }
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                CHANNEL,
                "GE price alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Watchlist price target notifications"
            }
            context.getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }
}
