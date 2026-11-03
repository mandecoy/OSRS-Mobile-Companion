package com.mandecoy.osrscompanion

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

private val MoneyStone = Color(0xff2e2924)
private val MoneyDark = Color(0xff201c18)
private val MoneyGold = Color(0xffd7a93f)
private val MoneyParchment = Color(0xffd7c7a4)
private val MoneyWhite = Color(0xfff4ead4)
private val MoneyGreen = Color(0xffa9d18e)
private val MoneyRed = Color(0xffe6a39a)

private object MoneyFavorites {
    private const val PREFS = "money_favorites_v1"
    fun all(context: android.content.Context): Set<String> =
        context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
            .getStringSet("saved", emptySet())?.toSet() ?: emptySet()

    fun toggle(context: android.content.Context, item: String) {
        val old = all(context).toMutableSet()
        if (!old.add(item)) old.remove(item)
        context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE).edit()
            .putStringSet("saved", old).apply()
    }
}

private val moneyCategories = listOf(
    "Jewellery", "Teleport Tablets", "Magic Processing", "Other Processing"
)

@Composable
fun MoneyMakingScreen(nav: NavHostController) {
    val context = LocalContext.current
    var view by remember { mutableStateOf("home") }
    var tab by remember { mutableStateOf("Top Profits") }
    var query by remember { mutableStateOf("") }
    var bankInput by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Jewellery") }
    var selectedRecipe by remember { mutableStateOf<MoneyEvaluation?>(null) }
    var selectedMethod by remember { mutableStateOf<WikiMoneyMethod?>(null) }
    var selectedShop by remember { mutableStateOf("") }
    var shopDetail by remember { mutableStateOf<WikiShopDetail?>(null) }
    var guideDescription by remember { mutableStateOf("") }
    var market by remember { mutableStateOf<MarketSnapshot?>(null) }
    var marketError by remember { mutableStateOf("") }
    var marketLoading by remember { mutableStateOf(true) }
    var marketRevision by remember { mutableIntStateOf(0) }
    var wikiRevision by remember { mutableIntStateOf(0) }
    var shopRevision by remember { mutableIntStateOf(0) }
    var methods by remember { mutableStateOf<List<WikiMoneyMethod>>(emptyList()) }
    var shopNames by remember { mutableStateOf<List<String>>(emptyList()) }
    var methodLoading by remember { mutableStateOf(true) }
    var shopLoading by remember { mutableStateOf(false) }
    var detailLoading by remember { mutableStateOf(false) }
    var wikiError by remember { mutableStateOf("") }
    var shopError by remember { mutableStateOf("") }
    var selectedCatalogCategory by remember { mutableStateOf("All") }
    var onlyReliable by remember { mutableStateOf(false) }
    var favoritesVersion by remember { mutableIntStateOf(0) }
    val favorites = remember(favoritesVersion) { MoneyFavorites.all(context) }
    val recipes = remember { MoneyRecipeCatalog.all() }

    LaunchedEffect(marketRevision) {
        marketLoading = true
        marketError = ""
        try {
            market = withContext(Dispatchers.IO) { MarketApi.loadSnapshot(context.applicationContext) }
        } catch (error: Exception) {
            marketError = error.message ?: "Unable to retrieve GE data"
        } finally {
            marketLoading = false
        }
    }
    LaunchedEffect(wikiRevision) {
        methodLoading = true
        wikiError = ""
        try {
            methods = withContext(Dispatchers.IO) {
                MoneyWikiApi.loadMethods(context.applicationContext, wikiRevision > 0)
            }
        } catch (error: Exception) {
            wikiError = error.message ?: "Unable to retrieve Wiki money-making methods"
        } finally {
            methodLoading = false
        }
    }
    LaunchedEffect(view, shopRevision) {
        if (view == "shops" && (shopNames.isEmpty() || shopRevision > 0)) {
            shopLoading = true
            shopError = ""
            try {
                shopNames = withContext(Dispatchers.IO) {
                    MoneyWikiApi.loadShops(context.applicationContext, shopRevision > 0)
                }
            } catch (error: Exception) {
                shopError = error.message ?: "Unable to retrieve shop directory"
            } finally {
                shopLoading = false
            }
        }
    }
    LaunchedEffect(selectedShop, shopRevision) {
        if (selectedShop.isNotBlank()) {
            detailLoading = true
            shopDetail = null
            shopError = ""
            try {
                shopDetail = withContext(Dispatchers.IO) {
                    MoneyWikiApi.loadShopDetail(context.applicationContext, selectedShop, shopRevision > 0)
                }
            } catch (error: Exception) {
                shopError = error.message ?: "Unable to retrieve shop inventory"
            } finally {
                detailLoading = false
            }
        }
    }
    LaunchedEffect(selectedMethod) {
        guideDescription = ""
        if (selectedMethod != null) {
            detailLoading = true
            try {
                guideDescription = withContext(Dispatchers.IO) {
                    MoneyWikiApi.loadMethodDescription(selectedMethod!!.page)
                }
            } catch (error: Exception) {
                guideDescription = "Could not retrieve this guide: " + (error.message ?: "network error")
            } finally {
                detailLoading = false
            }
        }
    }

    fun previous() {
        view = when (view) {
            "shop" -> "shops"
            "recipe" -> "recipes"
            "method" -> "methods"
            else -> "home"
        }
    }
    fun save(key: String) {
        MoneyFavorites.toggle(context, key)
        favoritesVersion++
    }
    fun selectMethod(method: WikiMoneyMethod) {
        selectedMethod = method
        view = "method"
    }
    fun selectRecipe(evaluation: MoneyEvaluation) {
        selectedRecipe = evaluation
        view = "recipe"
    }
    fun openWiki(page: String) {
        val uri = Uri.parse("https://oldschool.runescape.wiki/w/" + Uri.encode(page.replace(' ', '_')))
        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
    }
    BackHandler(view != "home") { previous() }

    val evaluated = remember(market) {
        market?.let { snapshot ->
            recipes.map { MoneyProfitCalculator.evaluate(it, snapshot) }
                .sortedWith(compareByDescending<MoneyEvaluation> { it.gpHour ?: Long.MIN_VALUE }
                    .thenByDescending { it.profit ?: Long.MIN_VALUE })
        }.orEmpty()
    }
    val bank = bankInput.replace(",", "").trim().toLongOrNull()?.coerceAtLeast(0L)
    val visibleMethods = methods.asSequence().filter {
        (selectedCatalogCategory == "All" || it.category == selectedCatalogCategory) &&
            (query.isBlank() || it.title.contains(query, true) ||
                it.skills.contains(query, true) || it.category.contains(query, true))
    }.sortedByDescending { it.wikiHourlyGp ?: -1 }.toList()
    val shownRecipes = evaluated.filter {
        it.recipe.category == category &&
            (query.isBlank() || it.recipe.name.contains(query, true) ||
                it.recipe.output.contains(query, true)) &&
            (bank == null || (it.inputCost != null && it.inputCost <= bank)) &&
            (!onlyReliable || (it.profit != null &&
                it.flags.none { flag -> flag.startsWith("Stale") ||
                    flag.startsWith("Low or unknown") || flag.startsWith("Missing") }))
    }
    Screen(nav, "Money Making") { pad ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().background(MoneyDark).padding(pad),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                if (view != "home") {
                    TextButton(onClick = { previous() }) {
                        Text("‹ Money Making / " + when (view) {
                            "shop" -> "Shops"
                            "recipe" -> category
                            "method" -> "Methods"
                            else -> "Home"
                        }, color = MoneyGold)
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("LIVE MONEY MAKING", color = MoneyGold, fontWeight = FontWeight.Bold)
                        Text(
                            when {
                                marketLoading -> "Refreshing GE prices..."
                                market != null -> "GE snapshot: " +
                                    DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(market!!.fetchedAt))
                                else -> "GE prices unavailable"
                            }, color = MoneyParchment, fontSize = 11.sp
                        )
                    }
                    OutlinedButton(onClick = {
                        marketRevision++
                        if (view == "methods") wikiRevision++
                        if (view == "shops" || view == "shop") shopRevision++
                    }) { Text("Refresh", color = MoneyGold, fontSize = 12.sp) }
                }
            }
            if (marketLoading) item { CircularProgressIndicator(color = MoneyGold) }
            if (marketError.isNotBlank()) item { MoneyNotice("GE error: $marketError") }
            if (view in listOf("home", "methods", "shops", "recipes", "saved")) {
                item {
                    OutlinedTextField(
                        value = query, onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth(), singleLine = true,
                        label = { Text("Search shops, methods or products") }
                    )
                }
            }
            when (view) {
                "home" -> {
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            listOf("Top Profits", "Hidden Gems", "All Methods").forEach { mode ->
                                OutlinedButton(onClick = {
                                    tab = mode
                                    if (mode == "All Methods") { query = ""; view = "methods" }
                                }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(5.dp)) {
                                    Text(mode, color = if (mode == tab) MoneyGold else MoneyParchment,
                                        fontSize = 10.sp)
                                }
                            }
                        }
                    }
                    item { Text("CATEGORIES", color = MoneyGold, fontWeight = FontWeight.Bold) }
                    item { MoneyCategoryTile("Shop Profits", "All shops and live GE comparison", "Shop.png") {
                        view = "shops"; query = ""
                    } }
                    moneyCategories.forEach { selected ->
                        item {
                            MoneyCategoryTile(selected, when (selected) {
                                "Jewellery" -> "Crafting, enchanting and complete chains"
                                "Teleport Tablets" -> "Rune costs and tablet margins"
                                "Magic Processing" -> "Tan Leather, Plank Make and spells"
                                else -> "Unfinished potions, grinding, keys and more"
                            }, when (selected) {
                                "Jewellery" -> "Gold_ring.png"
                                "Teleport Tablets" -> "Varrock_teleport.png"
                                "Magic Processing" -> "Nature_rune.png"
                                else -> "Crushed_nest.png"
                            }) { category = selected; query = ""; view = "recipes" }
                        }
                    }
                    item { MoneyCategoryTile("All Wiki Money Makers", "Processing, skilling, combat, collecting, recurring", "Coins_10000.png") {
                        view = "methods"; query = ""; selectedCatalogCategory = "All"
                    } }
                    item { MoneyCategoryTile("Saved Methods", "Your bookmarked shops, methods and recipes", "Book.png") {
                        view = "saved"; query = ""
                    } }
                    item { Text("BEST CALCULATED PROCESSING", color = MoneyGold, fontWeight = FontWeight.Bold) }
                    if (evaluated.isEmpty()) item {
                        MoneyNotice("Live calculations will appear when GE data has loaded.")
                    }
                    items(evaluated.filter {
                        it.profit != null && it.profit > 0 &&
                            it.gpHour != null && (tab != "Hidden Gems" ||
                                it.recipe.category == "Other Processing")
                    }.take(12), key = { "h" + it.recipe.name }) {
                        MoneyRecipeRow(it) { selectRecipe(it) }
                    }
                    item { Text("WIKI GP/HOUR ESTIMATES", color = MoneyGold, fontWeight = FontWeight.Bold) }
                    if (methodLoading) item { CircularProgressIndicator(color = MoneyGold) }
                    if (wikiError.isNotBlank()) item { MoneyNotice(wikiError) }
                    items(visibleMethods.filter {
                        tab != "Hidden Gems" || it.category in listOf("Processing","Skilling","Collecting")
                    }.take(15), key = { "wm" + it.page }) {
                        MoneyMethodRow(it) { selectMethod(it) }
                    }
                    item {
                        Text("Wiki GP/hour figures are estimates, not verified live recalculations. " +
                            "Recipe calculations use current GE trades, with taxes and risk flags.",
                            color = MoneyParchment, fontSize = 11.sp)
                    }
                }
                "methods" -> {
                    item { Text("WIKI MONEY-MAKING DIRECTORY", color = MoneyGold, fontWeight = FontWeight.Bold) }
                    item {
                        MoneyNotice("All methods display natively. Hourly estimates are Wiki-derived and can be outdated. " +
                            "See the selected method's description and requirements before spending GP.")
                    }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf("All", "Processing", "Skilling").forEach { filter ->
                                OutlinedButton(onClick = { selectedCatalogCategory = filter },
                                    modifier = Modifier.weight(1f), contentPadding = PaddingValues(3.dp)) {
                                    Text(filter, color = MoneyParchment, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf("Collecting", "Combat", "Recurring", "Free-to-play").forEach { filter ->
                                OutlinedButton(onClick = { selectedCatalogCategory = filter },
                                    modifier = Modifier.weight(1f), contentPadding = PaddingValues(2.dp)) {
                                    Text(filter, color = MoneyParchment, fontSize = 9.sp)
                                }
                            }
                        }
                    }
                    if (methodLoading) item { CircularProgressIndicator(color = MoneyGold) }
                    if (wikiError.isNotBlank()) item { MoneyNotice(wikiError) }
                    item { Text("${visibleMethods.size} matching methods", color = MoneyParchment, fontSize = 12.sp) }
                    items(visibleMethods, key = { it.category + ":" + it.page }) { method ->
                        MoneyMethodRow(method) { selectMethod(method) }
                    }
                }
                "shops" -> {
                    item { Text("ALL NPC SHOPS", color = MoneyGold, fontWeight = FontWeight.Bold) }
                    item { MoneyNotice("Complete Wiki shop-category index. Open any shop to see " +
                        "its stock and compare its listed base prices to current GE instant-sell quotes. " +
                        "Actual shop stock and prices depend on world and purchases.") }
                    if (shopLoading) item { CircularProgressIndicator(color = MoneyGold) }
                    if (shopError.isNotBlank()) item { MoneyNotice(shopError) }
                    val filtered = shopNames.filter { query.isBlank() || it.contains(query, true) }
                    item { Text("${filtered.size} matching shop pages", color = MoneyParchment, fontSize = 12.sp) }
                    items(filtered, key = { "shop" + it }) { shop ->
                        MoneyTextTile(shop, "View inventory →") {
                            selectedShop = shop
                            view = "shop"
                        }
                    }
                }
                "shop" -> {
                    item { Text(selectedShop, color = MoneyGold, fontWeight = FontWeight.Bold, fontSize = 19.sp) }
                    if (detailLoading) item { CircularProgressIndicator(color = MoneyGold) }
                    if (shopError.isNotBlank()) item { MoneyNotice(shopError) }
                    shopDetail?.let { detail ->
                        if (detail.location.isNotBlank()) item {
                            Text("Location: " + detail.location, color = MoneyParchment)
                        }
                        if (detail.notes.isNotBlank()) item { Text(detail.notes, color = MoneyParchment, fontSize = 12.sp) }
                        item { MoneyNotice("The shop price is the Wiki's listed base price, not live world stock. " +
                            "Profit is for ONE item at that quoted price; no assumed bulk margin.") }
                        val evals = market?.let { snap -> detail.stock.map { MoneyProfitCalculator.shop(it, snap) } }
                            ?: detail.stock.map { MoneyShopEvaluation(it, null, null, null, emptyList()) }
                        if (evals.isEmpty()) item {
                            MoneyNotice("No standard GP-denominated stock table was detected. " +
                                "This may be a specialty shop, a directory page, or a changed Wiki template.")
                        }
                        items(evals.sortedWith(compareByDescending<MoneyShopEvaluation> { it.netUnitProfit ?: Long.MIN_VALUE }),
                            key = { "stock" + it.row.item }) { value -> MoneyShopRow(value) }
                        item {
                            OutlinedButton(onClick = { save("shop|" + detail.title) }) {
                                Text(if ("shop|" + detail.title in favorites) "★ Saved shop" else "☆ Save shop")
                            }
                        }
                    }
                }
                "recipes" -> {
                    item { Text(category.uppercase(), color = MoneyGold, fontWeight = FontWeight.Bold) }
                    item {
                        OutlinedTextField(bankInput, { bankInput = it.filter { c -> c.isDigit() || c == ',' } },
                            modifier = Modifier.fillMaxWidth(), label = { Text("Available GP (optional)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true)
                    }
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Hide stale / low-volume quotes", color = MoneyParchment,
                                fontSize = 12.sp, modifier = Modifier.weight(1f))
                            Switch(checked = onlyReliable, onCheckedChange = { onlyReliable = it })
                        }
                    }
                    item {
                        MoneyNotice("Buy costs use recent GE high trades; outputs use low trades, " +
                            "minus GE tax. Estimated hourly rates depend on assumed processing speeds. " +
                            "Missing prices are not assigned zero.")
                    }
                    item { Text("${shownRecipes.size} matching recipes", color = MoneyParchment, fontSize = 12.sp) }
                    items(shownRecipes, key = { it.recipe.name }) { evaluation ->
                        MoneyRecipeRow(evaluation) { selectRecipe(evaluation) }
                    }
                }
                "recipe" -> {
                    selectedRecipe?.let { e ->
                        item { Text(e.recipe.name, color = MoneyGold, fontWeight = FontWeight.Bold, fontSize = 20.sp) }
                        item { MoneyNotice(e.recipe.requirements.ifBlank { "No specific requirements recorded" }) }
                        item { MoneyLabeledPrice("Materials and fees", e.inputCost) }
                        item { MoneyLabeledPrice("Sale value after tax", e.sellValue) }
                        item { MoneyLabeledPrice("Profit per batch", e.profit) }
                        item { MoneyLabeledPrice("Estimated GP/hour", e.gpHour) }
                        item {
                            Text("Ingredients", color = MoneyGold, fontWeight = FontWeight.Bold)
                        }
                        items(e.recipe.inputs) { ingredient ->
                            Text("${ingredient.quantity} × ${ingredient.item}", color = MoneyParchment)
                        }
                        if (e.recipe.coinFee != 0L) item {
                            Text("Coins fee: ${gp(e.recipe.coinFee)} GP", color = MoneyParchment)
                        }
                        if (e.maximumFourHourBatches != null) item {
                            Text("Upper bound based on ingredient GE buy limits: ${e.maximumFourHourBatches} batches / 4h",
                                color = MoneyParchment, fontSize = 12.sp)
                        }
                        item { Text(e.recipe.notes, color = MoneyParchment, fontSize = 12.sp) }
                        e.flags.forEach { flag -> item { MoneyNotice(flag) } }
                        item {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { save("recipe|" + e.recipe.name) }) {
                                    Text(if ("recipe|" + e.recipe.name in favorites) "★ Saved" else "☆ Save")
                                }
                                if (e.recipe.source.isNotBlank()) {
                                    TextButton(onClick = { openWiki(e.recipe.source) }) {
                                        Text("Source on Wiki", color = MoneyGold)
                                    }
                                }
                            }
                        }
                    }
                }
                "method" -> {
                    selectedMethod?.let { method ->
                        item { Text(method.title, color = MoneyGold, fontSize = 20.sp, fontWeight = FontWeight.Bold) }
                        item { MoneyLabeledPrice("Wiki-estimated GP/hour", method.wikiHourlyGp) }
                        item { Text("Category: " + method.category, color = MoneyParchment) }
                        item { Text("Skills: " + method.skills, color = MoneyParchment, fontSize = 12.sp) }
                        item { Text("Intensity: " + method.intensity, color = MoneyParchment, fontSize = 12.sp) }
                        item { MoneyNotice("This is a Wiki estimate, not a live-calculated profit. " +
                            "Check volume, expenses, skill requirements and setup before relying on it.") }
                        if (detailLoading) item { CircularProgressIndicator(color = MoneyGold) }
                        if (guideDescription.isNotBlank()) item {
                            Text(guideDescription, color = MoneyWhite, fontSize = 13.sp)
                        }
                        item {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { save("method|" + method.page) }) {
                                    Text(if ("method|" + method.page in favorites) "★ Saved" else "☆ Save")
                                }
                                TextButton(onClick = { openWiki(method.page) }) {
                                    Text("Source on Wiki", color = MoneyGold)
                                }
                            }
                        }
                    }
                }
                "saved" -> {
                    item { Text("SAVED METHODS & SHOPS", color = MoneyGold, fontWeight = FontWeight.Bold) }
                    if (favorites.isEmpty()) item { MoneyNotice("Save a shop, recipe or method to see it here.") }
                    items(favorites.sorted()) { saved ->
                        val parts = saved.split("|", limit = 2)
                        if (parts.size == 2) {
                            MoneyTextTile(parts[1], parts[0].replaceFirstChar { it.uppercase() }) {
                                when (parts[0]) {
                                    "shop" -> { selectedShop = parts[1]; view = "shop" }
                                    "method" -> {
                                        val method = methods.firstOrNull { it.page == parts[1] }
                                        if (method != null) selectMethod(method)
                                    }
                                    "recipe" -> {
                                        val evaluation = evaluated.firstOrNull { it.recipe.name == parts[1] }
                                        if (evaluation != null) {
                                            category = evaluation.recipe.category
                                            selectRecipe(evaluation)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MoneyCategoryTile(title: String, sub: String, iconFile: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MoneyStone),
        border = BorderStroke(1.dp, MoneyGold.copy(alpha = 0.5f))
    ) {
        Row(Modifier.fillMaxWidth().padding(11.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            WikiIcon(osrsWikiImage(iconFile), title, 35.dp)
            Column(Modifier.weight(1f)) {
                Text(title, color = MoneyWhite, fontWeight = FontWeight.Bold)
                Text(sub, color = MoneyParchment, fontSize = 11.sp)
            }
            Text("›", color = MoneyGold, fontSize = 23.sp)
        }
    }
}

@Composable
private fun MoneyTextTile(title: String, sub: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MoneyStone)
    ) {
        Row(Modifier.fillMaxWidth().padding(10.dp),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, color = MoneyWhite, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(sub, color = MoneyParchment, fontSize = 11.sp)
            }
            Text("›", color = MoneyGold, fontSize = 22.sp)
        }
    }
}

@Composable
private fun MoneyNotice(message: String) {
    Text(message, color = MoneyParchment, fontSize = 12.sp,
        modifier = Modifier.fillMaxWidth().background(MoneyStone, RoundedCornerShape(7.dp)).padding(9.dp))
}

@Composable
private fun MoneyMethodRow(method: WikiMoneyMethod, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MoneyStone)) {
        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {
                Text(method.title, color = MoneyWhite, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text(method.category + " · " + method.intensity,
                    color = MoneyParchment, fontSize = 11.sp)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(method.wikiHourlyGp?.let { gp(it) + "/hr" } ?: "Unpriced",
                    color = MoneyGold, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text("Wiki estimate", color = MoneyParchment, fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun MoneyRecipeRow(entry: MoneyEvaluation, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MoneyStone)) {
        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WikiIcon(osrsWikiImage(entry.recipe.output.replace(' ', '_') + ".png"),
                entry.recipe.output, 30.dp)
            Column(Modifier.weight(1f)) {
                Text(entry.recipe.name, color = MoneyWhite, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text(entry.recipe.category + " · " +
                    (if (entry.flags.any { it.contains("Stale") || it.contains("volume") }) "Check liquidity" else "GE quote"),
                    color = MoneyParchment, fontSize = 10.sp)
            }
            Column(horizontalAlignment = Alignment.End) {
                val profit = entry.profit
                Text(profit?.let { (if (it > 0) "+" else "") + gp(it) } ?: "N/A",
                    color = when {
                        profit == null -> MoneyParchment
                        profit > 0 -> MoneyGreen
                        else -> MoneyRed
                    }, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text(entry.gpHour?.let { gp(it) + "/hr" } ?: "per batch",
                    color = MoneyParchment, fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun MoneyShopRow(entry: MoneyShopEvaluation) {
    Card(colors = CardDefaults.cardColors(containerColor = MoneyStone),
        modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.CenterVertically) {
            WikiIcon(osrsWikiImage(entry.row.item.replace(' ', '_') + ".png"), entry.row.item, 28.dp)
            Column(Modifier.weight(1f)) {
                Text(entry.row.item, color = MoneyWhite, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text("Stock ${entry.row.stock} · Restock ${entry.row.restock}",
                    color = MoneyParchment, fontSize = 10.sp)
                Text("Shop: ${entry.row.basePurchasePrice?.let { gp(it) } ?: "unknown"} · " +
                    "GE sell: ${entry.geSellPrice?.let { gp(it.toLong()) } ?: "unknown"}",
                    color = MoneyParchment, fontSize = 11.sp)
                if (entry.flags.isNotEmpty()) Text(entry.flags.joinToString(" · "),
                    color = MoneyParchment, fontSize = 10.sp)
            }
            Text(entry.netUnitProfit?.let { (if (it > 0) "+" else "") + gp(it) } ?: "—",
                color = if ((entry.netUnitProfit ?: 0L) > 0) MoneyGreen else MoneyRed,
                fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
    }
}

@Composable
private fun MoneyLabeledPrice(label: String, number: Long?) {
    Row(Modifier.fillMaxWidth().background(MoneyStone, RoundedCornerShape(6.dp)).padding(10.dp),
        horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MoneyParchment, fontSize = 12.sp, modifier = Modifier.weight(1f))
        Text(number?.let { gp(it) + " GP" } ?: "Unavailable",
            color = if (number != null && number >= 0) MoneyGreen else MoneyParchment,
            fontWeight = FontWeight.Bold, fontSize = 12.sp)
    }
}
