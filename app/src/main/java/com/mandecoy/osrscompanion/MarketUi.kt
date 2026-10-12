package com.mandecoy.osrscompanion

import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import kotlin.math.max

private val MStoneDark = Color(0xff201c18)
private val MStone = Color(0xff2e2924)
private val MStoneLight = Color(0xff403931)
private val MGold = Color(0xffd7a93f)
private val MGoldDark = Color(0xff8b6722)
private val MParchment = Color(0xffd7c7a4)
private val MText = Color(0xfff4ead4)
private val MPositive = Color(0xffa9d18e)
private val MNegative = Color(0xffe6a39a)

private data class MarketLoadState(
    val loading: Boolean = true,
    val snapshot: MarketSnapshot? = null,
    val error: String? = null
)

@Composable
private fun rememberMarketSnapshot(): Pair<MarketLoadState, () -> Unit> {
    val context = LocalContext.current
    val handler = remember { Handler(Looper.getMainLooper()) }
    var state by remember { mutableStateOf(MarketLoadState()) }
    var generation by remember { mutableIntStateOf(0) }

    fun reload() {
        generation++
    }

    LaunchedEffect(generation) {
        state = MarketLoadState(loading = true)
        Thread {
            try {
                val snapshot = MarketApi.loadSnapshot(context.applicationContext)
                handler.post { state = MarketLoadState(loading = false, snapshot = snapshot) }
            } catch (error: Exception) {
                handler.post {
                    state = MarketLoadState(
                        loading = false,
                        error = error.message ?: "Unable to load GE prices."
                    )
                }
            }
        }.start()
    }

    return state to ::reload
}

private data class MarketRow(
    val item: MarketItem,
    val primary: String,
    val secondary: String,
    val value: Long = 0L,
    val positive: Boolean? = null
)

@Composable
fun GeToolsScreen(nav: NavHostController) {
    val (state, reload) = rememberMarketSnapshot()
    var mode by remember { mutableStateOf("Flip Finder") }
    var search by remember { mutableStateOf("") }
    var minVolumeInput by remember { mutableStateOf("100") }
    val modes = listOf("Flip Finder", "Most Traded", "Gainers", "Losers", "Death's Coffer")

    Screen(nav, "GE Tools") { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text(
                    "Live market data from the RuneScape Wiki price API. Bid/ask values are recent traded prices, not guaranteed future fills.",
                    color = MParchment,
                    fontSize = 12.sp
                )
            }
            item {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().height(94.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(modes.chunked(3)) { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            row.forEach { option ->
                                OutlinedButton(
                                    onClick = { mode = option },
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        option,
                                        fontSize = 10.sp,
                                        color = if (mode == option) MGold else MText,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = search,
                        onValueChange = { search = it.take(40) },
                        label = { Text("Search item") },
                        modifier = Modifier.weight(1f)
                    )
                    if (mode == "Flip Finder") {
                        OutlinedTextField(
                            value = minVolumeInput,
                            onValueChange = { minVolumeInput = it.filter(Char::isDigit).take(7) },
                            label = { Text("Min 1h vol") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.width(125.dp)
                        )
                    }
                }
            }
            item {
                Button(
                    onClick = reload,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MGold, contentColor = Color.Black)
                ) {
                    Text(if (state.loading) "Refreshing…" else "Refresh prices")
                }
            }

            if (state.loading) {
                item { Text("Loading live GE data…", color = MParchment) }
            } else if (state.error != null) {
                item {
                    ErrorCard(state.error ?: "Unable to load prices.")
                }
            } else {
                val snapshot = state.snapshot!!
                val minVolume = minVolumeInput.toLongOrNull() ?: 0L
                val query = search.trim()
                val rows = buildMarketRows(snapshot, mode, minVolume)
                    .filter {
                        query.isBlank() ||
                            it.item.name.contains(query, ignoreCase = true) ||
                            it.item.id.toString() == query
                    }
                    .take(100)
                item {
                    Text(
                        mode.uppercase() + " • " + rows.size + " shown",
                        color = MGold,
                        fontWeight = FontWeight.Bold
                    )
                }
                items(rows, key = { mode + "_" + it.item.id }) { row ->
                    MarketRowCard(row) { nav.navigate("market_item/" + row.item.id) }
                }
            }
        }
    }
}

private fun buildMarketRows(
    snapshot: MarketSnapshot,
    mode: String,
    minVolume: Long
): List<MarketRow> {
    val byId = snapshot.items.associateBy { it.id }

    return when (mode) {
        "Flip Finder" -> snapshot.latest.mapNotNull { (id, price) ->
            val item = byId[id] ?: return@mapNotNull null
            val ask = price.high ?: return@mapNotNull null
            val bid = price.low ?: return@mapNotNull null
            if (ask <= 0 || bid <= 0 || ask <= bid) return@mapNotNull null
            val hour = snapshot.hourly[id]
            val volume = hour?.volume ?: 0L
            if (volume < minVolume) return@mapNotNull null
            val profit = ask.toLong() - bid.toLong() - geTax(ask)
            if (profit <= 0L) return@mapNotNull null
            val roi = profit * 100.0 / bid.toDouble()
            MarketRow(
                item,
                primary = "+" + gp(profit) + " gp/flip",
                secondary = "Bid " + gp(bid.toLong()) + " • Ask " + gp(ask.toLong()) +
                    " • Tax " + gp(geTax(ask).toLong()) +
                    " • 1h vol " + volume + " • " + String.format("%.2f%%", roi),
                value = profit,
                positive = true
            )
        }.sortedByDescending { it.value }

        "Most Traded" -> snapshot.hourly.mapNotNull { (id, hour) ->
            val item = byId[id] ?: return@mapNotNull null
            val price = midpoint(snapshot.latest[id]) ?: return@mapNotNull null
            MarketRow(
                item,
                primary = hour.volume.toString() + " trades / 1h",
                secondary = "Recent price ~" + gp(price.toLong()) + " gp",
                value = hour.volume
            )
        }.sortedByDescending { it.value }

        "Gainers", "Losers" -> snapshot.latest.mapNotNull { (id, latest) ->
            val item = byId[id] ?: return@mapNotNull null
            val current = midpoint(latest) ?: return@mapNotNull null
            val hour = snapshot.hourly[id] ?: return@mapNotNull null
            val reference = when {
                hour.avgHigh != null && hour.avgLow != null ->
                    ((hour.avgHigh.toLong() + hour.avgLow.toLong()) / 2L).toInt()
                hour.avgHigh != null -> hour.avgHigh
                else -> hour.avgLow
            } ?: return@mapNotNull null
            if (reference <= 0) return@mapNotNull null
            val pct = (current - reference) * 100.0 / reference.toDouble()
            val wantsGain = mode == "Gainers"
            if ((wantsGain && pct <= 0) || (!wantsGain && pct >= 0)) return@mapNotNull null
            MarketRow(
                item,
                primary = (if (pct >= 0) "+" else "") + String.format("%.2f%%", pct),
                secondary = "Now ~" + gp(current.toLong()) + " • 1h avg ~" + gp(reference.toLong()) +
                    " • 1h vol " + hour.volume,
                value = kotlin.math.abs((pct * 10_000).toLong()),
                positive = pct >= 0
            )
        }.sortedByDescending { it.value }

        "Death's Coffer" -> snapshot.latest.mapNotNull { (id, latest) ->
            val item = byId[id] ?: return@mapNotNull null
            val market = midpoint(latest) ?: return@mapNotNull null
            if (market < 10_000) return@mapNotNull null
            val credit = (market * 105L) / 100L
            val bonus = credit - market
            MarketRow(
                item,
                primary = "~" + gp(credit) + " coffer value",
                secondary = "Market ~" + gp(market.toLong()) + " • 5% bonus ~+" + gp(bonus),
                value = bonus,
                positive = true
            )
        }.sortedByDescending { it.value }

        else -> emptyList()
    }
}

@Composable
fun HighAlchScreen(nav: NavHostController) {
    val (state, reload) = rememberMarketSnapshot()
    var search by remember { mutableStateOf("") }
    var minVolumeInput by remember { mutableStateOf("100") }
    var saferOnly by remember { mutableStateOf(true) }

    Screen(nav, "High Alch") { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text(
                    "Uses recent instant-buy prices, High Alch values, Nature rune cost, buy limits, and recent volume.",
                    color = MParchment
                )
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = search,
                        onValueChange = { search = it.take(40) },
                        label = { Text("Search") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = minVolumeInput,
                        onValueChange = { minVolumeInput = it.filter(Char::isDigit).take(7) },
                        label = { Text("Min 1h vol") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.width(125.dp)
                    )
                }
            }
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text("Safer / liquid results", color = MText, fontWeight = FontWeight.Bold)
                        Text("Hides very low-volume items.", color = MParchment, fontSize = 11.sp)
                    }
                    Switch(checked = saferOnly, onCheckedChange = { saferOnly = it })
                }
            }
            item {
                Button(
                    onClick = reload,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MGold, contentColor = Color.Black)
                ) { Text(if (state.loading) "Refreshing…" else "Refresh prices") }
            }

            if (state.loading) {
                item { Text("Loading High Alch opportunities…", color = MParchment) }
            } else if (state.error != null) {
                item { ErrorCard(state.error ?: "Unable to load prices.") }
            } else {
                val snapshot = state.snapshot!!
                val naturePrice = snapshot.latest[561]?.high ?: snapshot.latest[561]?.low
                if (naturePrice == null) {
                    item { ErrorCard("Nature rune price is unavailable right now.") }
                } else {
                    val minVol = if (saferOnly) max(100L, minVolumeInput.toLongOrNull() ?: 0L)
                        else minVolumeInput.toLongOrNull() ?: 0L
                    val q = search.trim()
                    val rows = snapshot.items.mapNotNull { item ->
                        val alch = item.highAlch ?: return@mapNotNull null
                        val buy = snapshot.latest[item.id]?.high ?: return@mapNotNull null
                        val volume = snapshot.hourly[item.id]?.volume ?: 0L
                        if (volume < minVol) return@mapNotNull null
                        val profit = alch.toLong() - buy.toLong() - naturePrice.toLong()
                        if (profit <= 0L) return@mapNotNull null
                        MarketRow(
                            item,
                            primary = "+" + gp(profit) + " gp/alch",
                            secondary = "Buy " + gp(buy.toLong()) +
                                " • Alch " + gp(alch.toLong()) +
                                " • Nature " + gp(naturePrice.toLong()) +
                                " • Limit " + (item.limit?.toString() ?: "?") +
                                " • 1h vol " + volume,
                            value = profit,
                            positive = true
                        )
                    }.filter {
                        q.isBlank() || it.item.name.contains(q, ignoreCase = true)
                    }.sortedByDescending { it.value }.take(100)

                    item {
                        Text(
                            "Nature rune: ~" + gp(naturePrice.toLong()) + " gp • " + rows.size + " profitable results shown",
                            color = MGold,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    items(rows, key = { "alch_" + it.item.id }) { row ->
                        MarketRowCard(row) { nav.navigate("market_item/" + row.item.id) }
                    }
                }
            }
        }
    }
}

@Composable
fun PortfolioScreen(nav: NavHostController) {
    val context = LocalContext.current
    val (state, reload) = rememberMarketSnapshot()
    var revision by remember { mutableIntStateOf(0) }
    val positions = remember(revision) { PortfolioStore.positions(context) }
    val realized = remember(revision) { PortfolioStore.realized(context) }
    val watchIds = remember(revision) { WatchlistStore.ids(context) }
    val alerts = remember(revision) { PriceAlertStore.all(context) }
    var itemSearch by remember { mutableStateOf("") }

    Screen(nav, "Portfolio") { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text(
                    "Your trades, watchlist, and alert settings stay on this device. Live values refresh from public GE data.",
                    color = MParchment
                )
            }
            item {
                Button(
                    onClick = reload,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MGold, contentColor = Color.Black)
                ) { Text(if (state.loading) "Refreshing…" else "Refresh portfolio prices") }
            }
            if (state.error != null) {
                item { ErrorCard(state.error ?: "Unable to load market prices.") }
            }

            val latest = state.snapshot?.latest ?: emptyMap()
            val invested = positions.sumOf { it.quantity * it.averageBuyPrice }
            val currentValue = positions.sumOf { position ->
                val price = midpoint(latest[position.itemId])?.toLong() ?: position.averageBuyPrice
                position.quantity * (price - geTax(price.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()))
            }
            val unrealized = currentValue - invested

            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MStone),
                    border = BorderStroke(1.dp, MGoldDark)
                ) {
                    Column(
                        Modifier.fillMaxWidth().padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("PORTFOLIO SUMMARY", color = MGold, fontWeight = FontWeight.Bold)
                        Text("Invested: " + gp(invested) + " gp", color = MText)
                        Text("Estimated value: " + gp(currentValue) + " gp", color = MText)
                        Text(
                            "Unrealized: " + (if (unrealized >= 0) "+" else "") + gp(unrealized) + " gp",
                            color = if (unrealized >= 0) MPositive else MNegative,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Realized: " + (if (realized >= 0) "+" else "") + gp(realized) + " gp",
                            color = if (realized >= 0) MPositive else MNegative,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            item {
                Text("ADD / FIND ITEM", color = MGold, fontWeight = FontWeight.Bold)
            }
            item {
                OutlinedTextField(
                    value = itemSearch,
                    onValueChange = { itemSearch = it.take(40) },
                    label = { Text("Search item") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (itemSearch.length >= 2 && state.snapshot != null) {
                val choices = state.snapshot.items
                    .filter { it.name.contains(itemSearch, ignoreCase = true) }
                    .take(8)
                items(choices, key = { "choice_" + it.id }) { item ->
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable {
                            nav.navigate("market_item/" + item.id)
                        },
                        colors = CardDefaults.cardColors(containerColor = MStoneLight)
                    ) {
                        Text(item.name, color = MText, modifier = Modifier.padding(10.dp))
                    }
                }
            }

            item { Text("OPEN POSITIONS", color = MGold, fontWeight = FontWeight.Bold) }
            if (positions.isEmpty()) {
                item { Text("No open positions yet.", color = MParchment) }
            } else {
                items(positions, key = { "position_" + it.itemId }) { position ->
                    val live = midpoint(latest[position.itemId])?.toLong()
                    val pnl = live?.let {
                        (it - geTax(it.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()) - position.averageBuyPrice) *
                            position.quantity
                    }
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable {
                            nav.navigate("market_item/" + position.itemId)
                        },
                        colors = CardDefaults.cardColors(containerColor = MStone),
                        border = BorderStroke(1.dp, MGoldDark)
                    ) {
                        Column(Modifier.fillMaxWidth().padding(12.dp)) {
                            Text(position.itemName, color = MText, fontWeight = FontWeight.Bold)
                            Text(
                                position.quantity.toString() + " @ " + gp(position.averageBuyPrice) + " gp avg",
                                color = MParchment
                            )
                            if (live != null && pnl != null) {
                                Text(
                                    "Now ~" + gp(live) + " • P&L " + (if (pnl >= 0) "+" else "") + gp(pnl),
                                    color = if (pnl >= 0) MPositive else MNegative
                                )
                            }
                        }
                    }
                }
            }

            item { Text("WATCHLIST", color = MGold, fontWeight = FontWeight.Bold) }
            if (watchIds.isEmpty()) {
                item { Text("No watchlist items.", color = MParchment) }
            } else {
                val byId = state.snapshot?.items?.associateBy { it.id } ?: emptyMap()
                items(watchIds.toList(), key = { "watch_" + it }) { id ->
                    val item = byId[id]
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { nav.navigate("market_item/" + id) },
                        colors = CardDefaults.cardColors(containerColor = MStone)
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(item?.name ?: "Item #" + id, color = MText, fontWeight = FontWeight.Bold)
                            Text(midpoint(latest[id])?.let { gp(it.toLong()) + " gp" } ?: "—", color = MGold)
                        }
                    }
                }
            }

            item { Text("PRICE ALERTS", color = MGold, fontWeight = FontWeight.Bold) }
            if (alerts.isEmpty()) {
                item { Text("No active price alerts.", color = MParchment) }
            } else {
                items(alerts, key = { "alert_" + it.id }) { alert ->
                    Card(colors = CardDefaults.cardColors(containerColor = MStone)) {
                        Row(
                            Modifier.fillMaxWidth().padding(10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(alert.itemName, color = MText, fontWeight = FontWeight.Bold)
                                Text(
                                    (if (alert.direction == AlertDirection.ABOVE) "At or above " else "At or below ") +
                                        gp(alert.targetPrice) + " gp",
                                    color = MParchment
                                )
                            }
                            TextButton(onClick = {
                                PriceAlertStore.remove(context, alert.id)
                                revision++
                            }) {
                                Text("Remove", color = MGold)
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    "Background price alerts are checked periodically by Android and may be delayed by battery management.",
                    color = MParchment,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
fun MarketItemDetailScreen(nav: NavHostController, itemId: Int) {
    val context = LocalContext.current
    val handler = remember { Handler(Looper.getMainLooper()) }
    val (state, reload) = rememberMarketSnapshot()
    var history by remember(itemId) { mutableStateOf<List<HistoryPoint>>(emptyList()) }
    var historyError by remember(itemId) { mutableStateOf<String?>(null) }
    var qtyInput by remember { mutableStateOf("1") }
    var buyPriceInput by remember { mutableStateOf("") }
    var sellQtyInput by remember { mutableStateOf("1") }
    var sellPriceInput by remember { mutableStateOf("") }
    var alertPriceInput by remember { mutableStateOf("") }
    var alertAbove by remember { mutableStateOf(true) }
    var revision by remember { mutableIntStateOf(0) }

    LaunchedEffect(itemId) {
        Thread {
            try {
                val points = MarketApi.loadHistory(itemId, "1h").takeLast(168)
                handler.post {
                    history = points
                    historyError = null
                }
            } catch (error: Exception) {
                handler.post { historyError = error.message ?: "History unavailable." }
            }
        }.start()
    }

    val snapshot = state.snapshot
    val item = snapshot?.items?.firstOrNull { it.id == itemId }
    val latest = snapshot?.latest?.get(itemId)
    val hour = snapshot?.hourly?.get(itemId)
    val isWatched = remember(revision, itemId) { itemId in WatchlistStore.ids(context) }
    val position = remember(revision, itemId) {
        PortfolioStore.positions(context).firstOrNull { it.itemId == itemId }
    }

    Screen(nav, item?.name ?: "GE Item") { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (state.loading) {
                item { Text("Loading item data…", color = MParchment) }
            } else if (state.error != null || item == null) {
                item { ErrorCard(state.error ?: "Item not found.") }
                item {
                    Button(onClick = reload, modifier = Modifier.fillMaxWidth()) { Text("Retry") }
                }
            } else {
                val ask = latest?.high
                val bid = latest?.low
                val flipProfit = if (ask != null && bid != null) {
                    ask.toLong() - bid.toLong() - geTax(ask)
                } else null

                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MStone),
                        border = BorderStroke(1.dp, MGoldDark)
                    ) {
                        Column(
                            Modifier.fillMaxWidth().padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(item.name, color = MGold, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                            Text("Item ID " + item.id, color = MParchment, fontSize = 11.sp)
                            Text("Recent bid: " + (bid?.let { gp(it.toLong()) + " gp" } ?: "—"), color = MText)
                            Text("Recent ask: " + (ask?.let { gp(it.toLong()) + " gp" } ?: "—"), color = MText)
                            if (flipProfit != null) {
                                Text(
                                    "Estimated flip after tax: " + (if (flipProfit >= 0) "+" else "") + gp(flipProfit) + " gp",
                                    color = if (flipProfit >= 0) MPositive else MNegative
                                )
                            }
                            Text("1h volume: " + (hour?.volume?.toString() ?: "—"), color = MParchment)
                            Text("Buy limit: " + (item.limit?.toString() ?: "—"), color = MParchment)
                            if (item.highAlch != null) {
                                Text("High Alch: " + gp(item.highAlch.toLong()) + " gp", color = MParchment)
                            }
                        }
                    }
                }

                item {
                    OutlinedButton(
                        onClick = {
                            WatchlistStore.toggle(context, item.id)
                            revision++
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (isWatched) "Remove from watchlist" else "Add to watchlist")
                    }
                }

                item {
                    Text("7-DAY PRICE HISTORY", color = MGold, fontWeight = FontWeight.Bold)
                }
                item {
                    when {
                        history.isNotEmpty() -> MarketHistoryChart(history)
                        historyError != null -> Text(historyError ?: "", color = MParchment)
                        else -> Text("Loading chart…", color = MParchment)
                    }
                }

                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MStone),
                        border = BorderStroke(1.dp, MGoldDark)
                    ) {
                        Column(
                            Modifier.fillMaxWidth().padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("ADD BUY TO PORTFOLIO", color = MGold, fontWeight = FontWeight.Bold)
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = qtyInput,
                                    onValueChange = { qtyInput = it.filter(Char::isDigit).take(9) },
                                    label = { Text("Quantity") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = buyPriceInput,
                                    onValueChange = { buyPriceInput = it.filter(Char::isDigit).take(12) },
                                    label = { Text("Buy gp each") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            TextButton(onClick = {
                                buyPriceInput = (ask ?: bid ?: 0).takeIf { it > 0 }?.toString() ?: ""
                            }) {
                                Text("Use recent ask", color = MGold)
                            }
                            Button(
                                onClick = {
                                    val quantity = qtyInput.toLongOrNull() ?: 0L
                                    val price = buyPriceInput.toLongOrNull() ?: 0L
                                    PortfolioStore.addBuy(context, item, quantity, price)
                                    revision++
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = MGold, contentColor = Color.Black)
                            ) {
                                Text("Add purchase")
                            }
                        }
                    }
                }

                if (position != null) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MStone),
                            border = BorderStroke(1.dp, MGoldDark)
                        ) {
                            Column(
                                Modifier.fillMaxWidth().padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("RECORD SALE", color = MGold, fontWeight = FontWeight.Bold)
                                Text(
                                    "Holding " + position.quantity + " @ " + gp(position.averageBuyPrice) + " gp avg",
                                    color = MParchment
                                )
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedTextField(
                                        value = sellQtyInput,
                                        onValueChange = { sellQtyInput = it.filter(Char::isDigit).take(9) },
                                        label = { Text("Quantity") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.weight(1f)
                                    )
                                    OutlinedTextField(
                                        value = sellPriceInput,
                                        onValueChange = { sellPriceInput = it.filter(Char::isDigit).take(12) },
                                        label = { Text("Sell gp each") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                TextButton(onClick = {
                                    sellPriceInput = (bid ?: ask ?: 0).takeIf { it > 0 }?.toString() ?: ""
                                }) {
                                    Text("Use recent bid", color = MGold)
                                }
                                Button(
                                    onClick = {
                                        PortfolioStore.sell(
                                            context,
                                            item.id,
                                            sellQtyInput.toLongOrNull() ?: 0L,
                                            sellPriceInput.toLongOrNull() ?: 0L
                                        )
                                        revision++
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = MGold, contentColor = Color.Black)
                                ) { Text("Record sale after GE tax") }
                            }
                        }
                    }
                }

                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MStone),
                        border = BorderStroke(1.dp, MGoldDark)
                    ) {
                        Column(
                            Modifier.fillMaxWidth().padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("PRICE ALERT", color = MGold, fontWeight = FontWeight.Bold)
                            OutlinedTextField(
                                value = alertPriceInput,
                                onValueChange = { alertPriceInput = it.filter(Char::isDigit).take(12) },
                                label = { Text("Target gp") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { alertAbove = true },
                                    modifier = Modifier.weight(1f)
                                ) { Text(if (alertAbove) "✓ At/Above" else "At/Above") }
                                OutlinedButton(
                                    onClick = { alertAbove = false },
                                    modifier = Modifier.weight(1f)
                                ) { Text(if (!alertAbove) "✓ At/Below" else "At/Below") }
                            }
                            Button(
                                onClick = {
                                    PriceAlertStore.add(
                                        context,
                                        item,
                                        alertPriceInput.toLongOrNull() ?: 0L,
                                        if (alertAbove) AlertDirection.ABOVE else AlertDirection.BELOW
                                    )
                                    revision++
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = MGold, contentColor = Color.Black)
                            ) { Text("Save price alert") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MarketHistoryChart(points: List<HistoryPoint>) {
    val prices = points.mapNotNull { point ->
        when {
            point.avgHigh != null && point.avgLow != null ->
                (point.avgHigh.toFloat() + point.avgLow.toFloat()) / 2f
            point.avgHigh != null -> point.avgHigh.toFloat()
            point.avgLow != null -> point.avgLow.toFloat()
            else -> null
        }
    }
    if (prices.size < 2) {
        Text("Not enough historical trades for a chart.", color = MParchment)
        return
    }
    val minPrice = prices.minOrNull() ?: 0f
    val maxPrice = prices.maxOrNull() ?: minPrice + 1f
    val span = (maxPrice - minPrice).coerceAtLeast(1f)

    Card(
        colors = CardDefaults.cardColors(containerColor = MStone),
        border = BorderStroke(1.dp, MGoldDark)
    ) {
        Column(Modifier.fillMaxWidth().padding(10.dp)) {
            Text(
                "Low ~" + gp(minPrice.toLong()) + " • High ~" + gp(maxPrice.toLong()) + " gp",
                color = MParchment,
                fontSize = 11.sp
            )
            Canvas(
                modifier = Modifier.fillMaxWidth().height(150.dp).background(MStoneDark, RoundedCornerShape(6.dp))
            ) {
                val path = Path()
                prices.forEachIndexed { index, price ->
                    val x = if (prices.size == 1) 0f else size.width * index / (prices.size - 1).toFloat()
                    val y = size.height - ((price - minPrice) / span) * size.height
                    if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(path, color = MGold, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f))
            }
        }
    }
}

@Composable
private fun MarketRowCard(row: MarketRow, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MStone),
        border = BorderStroke(1.dp, MGoldDark)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(row.item.name, color = MText, fontWeight = FontWeight.Bold)
            Text(
                row.primary,
                color = when (row.positive) {
                    true -> MPositive
                    false -> MNegative
                    null -> MGold
                },
                fontWeight = FontWeight.Bold
            )
            Text(row.secondary, color = MParchment, fontSize = 11.sp)
        }
    }
}

@Composable
private fun ErrorCard(message: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xff4a302a)),
        border = BorderStroke(1.dp, MGoldDark)
    ) {
        Text(message, color = MText, modifier = Modifier.fillMaxWidth().padding(12.dp))
    }
}
