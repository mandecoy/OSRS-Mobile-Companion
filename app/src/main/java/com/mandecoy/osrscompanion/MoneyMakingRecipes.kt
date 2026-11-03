package com.mandecoy.osrscompanion

import kotlin.math.ceil
import kotlin.math.floor
import java.util.Locale

data class MoneyIngredient(val item: String, val quantity: Double = 1.0)
data class MoneyRecipe(
    val name: String,
    val category: String,
    val output: String,
    val inputs: List<MoneyIngredient>,
    val outputQty: Int = 1,
    val coinFee: Long = 0L,
    val actionsPerHour: Int? = null,
    val requirements: String = "",
    val notes: String = "",
    val source: String = ""
)

data class MoneyEvaluation(
    val recipe: MoneyRecipe,
    val inputCost: Long?,
    val sellValue: Long?,
    val profit: Long?,
    val gpHour: Long?,
    val flags: List<String>,
    val outputItem: MarketItem?,
    val maximumFourHourBatches: Long?
)

data class MoneyShopEvaluation(
    val row: WikiShopStock,
    val item: MarketItem?,
    val geSellPrice: Int?,
    val netUnitProfit: Long?,
    val flags: List<String>
)

object MoneyRecipeCatalog {
    fun all(): List<MoneyRecipe> {
        val recipes = mutableListOf<MoneyRecipe>()
        val gems = listOf("Sapphire", "Emerald", "Ruby", "Diamond", "Dragonstone", "Onyx")
        val types = listOf("ring", "necklace", "bracelet", "amulet")
        for (gem in gems) {
            for (kind in types) {
                val base = "$gem $kind"
                val ingredients = mutableListOf(MoneyIngredient("Gold bar"), MoneyIngredient(gem))
                if (kind == "amulet") ingredients += MoneyIngredient("Ball of wool")
                recipes += MoneyRecipe("Craft $base", "Jewellery", base, ingredients,
                    actionsPerHour = 900, requirements = "Crafting level varies by gem and jewellery type",
                    notes = "Amulets include a ball of wool for stringing. Crafting speed is a planning assumption.",
                    source = "Jewellery")
            }
        }
        data class EnchantTier(
            val gem: String, val required: String, val runes: List<MoneyIngredient>,
            val outputs: List<String>
        )
        val tiers = listOf(
            EnchantTier("Sapphire", "7 Magic", listOf(MoneyIngredient("Cosmic rune"), MoneyIngredient("Water rune")),
                listOf("Ring of recoil", "Games necklace(8)", "Bracelet of clay", "Amulet of magic")),
            EnchantTier("Emerald", "27 Magic", listOf(MoneyIngredient("Cosmic rune"), MoneyIngredient("Air rune", 3.0)),
                listOf("Ring of dueling(8)", "Binding necklace", "Castle wars bracelet(3)", "Amulet of defence")),
            EnchantTier("Ruby", "49 Magic", listOf(MoneyIngredient("Cosmic rune"), MoneyIngredient("Fire rune", 5.0)),
                listOf("Ring of forging", "Digsite pendant(5)", "Inoculation bracelet", "Amulet of strength")),
            EnchantTier("Diamond", "57 Magic", listOf(MoneyIngredient("Cosmic rune"), MoneyIngredient("Earth rune", 10.0)),
                listOf("Ring of life", "Phoenix necklace", "Forinthry bracelet(5)", "Amulet of power")),
            EnchantTier("Dragonstone", "68 Magic", listOf(MoneyIngredient("Cosmic rune"), MoneyIngredient("Water rune", 15.0), MoneyIngredient("Earth rune", 15.0)),
                listOf("Ring of wealth(5)", "Skills necklace(4)", "Combat bracelet(4)", "Amulet of glory(4)")),
            EnchantTier("Onyx", "87 Magic", listOf(MoneyIngredient("Cosmic rune"), MoneyIngredient("Fire rune", 20.0), MoneyIngredient("Earth rune", 20.0)),
                listOf("Ring of stone", "Berserker necklace", "Regen bracelet", "Amulet of fury"))
        )
        tiers.forEach { tier ->
            types.forEachIndexed { index, kind ->
                val input = tier.gem + " " + kind
                val output = tier.outputs[index]
                recipes += MoneyRecipe("Enchant $input", "Jewellery", output,
                    listOf(MoneyIngredient(input)) + tier.runes,
                    actionsPerHour = 1200, requirements = tier.required,
                    notes = "Assumes full-dose/full-charge output exists on the GE; confirm charge state.",
                    source = "Jewellery_Enchantments")
                val materials = mutableListOf(MoneyIngredient("Gold bar"), MoneyIngredient(tier.gem))
                if (kind == "amulet") materials += MoneyIngredient("Ball of wool")
                recipes += MoneyRecipe("Craft + enchant $input", "Jewellery", output,
                    materials + tier.runes, actionsPerHour = null,
                    requirements = "Crafting level varies; " + tier.required,
                    notes = "Full chain includes bar, gem, string (for amulets) and enchantment runes. Hourly speed not assumed.",
                    source = "Jewellery")
            }
        }

        val tablets = listOf(
            Triple("Varrock teleport", 25, listOf(MoneyIngredient("Air rune", 3.0), MoneyIngredient("Fire rune"), MoneyIngredient("Law rune"))),
            Triple("Lumbridge teleport", 31, listOf(MoneyIngredient("Air rune", 3.0), MoneyIngredient("Earth rune"), MoneyIngredient("Law rune"))),
            Triple("Falador teleport", 37, listOf(MoneyIngredient("Air rune", 3.0), MoneyIngredient("Water rune"), MoneyIngredient("Law rune"))),
            Triple("Camelot teleport", 45, listOf(MoneyIngredient("Air rune", 5.0), MoneyIngredient("Law rune"))),
            Triple("Ardougne teleport", 51, listOf(MoneyIngredient("Water rune", 2.0), MoneyIngredient("Law rune", 2.0))),
            Triple("Teleport to house", 40, listOf(MoneyIngredient("Air rune"), MoneyIngredient("Earth rune"), MoneyIngredient("Law rune")))
        )
        tablets.forEach { (name, level, runes) ->
            val output = if (name == "Teleport to house") "Teleport to house" else name
            recipes += MoneyRecipe("Create $name tablet", "Teleport Tablets", output,
                runes + MoneyIngredient("Soft clay"), actionsPerHour = 1000,
                requirements = "$level Magic; correct lectern and spellbook",
                notes = "Assumes no rune-saving staff, no butler cost, and 1 tablet per set of runes.",
                source = "Magic_tablet")
        }

        val hides = listOf(
            "Cowhide" to "Leather", "Green dragonhide" to "Green dragon leather",
            "Blue dragonhide" to "Blue dragon leather",
            "Red dragonhide" to "Red dragon leather",
            "Black dragonhide" to "Black dragon leather"
        )
        hides.forEach { (hide, leather) ->
            recipes += MoneyRecipe("Tan $hide (spell)", "Magic Processing", leather,
                listOf(MoneyIngredient(hide), MoneyIngredient("Nature rune", 0.2),
                    MoneyIngredient("Astral rune", 0.4), MoneyIngredient("Fire rune")),
                actionsPerHour = 8000, requirements = "78 Magic; Fremennik hard diary; Lunar spellbook",
                notes = "Rune cost is averaged over five hides per cast; 8,000 hides/hour is an efficient-casting estimate.",
                source = "Tan_Leather")
        }
        listOf(
            Triple("Logs", "Plank", 70L),
            Triple("Oak logs", "Oak plank", 175L),
            Triple("Teak logs", "Teak plank", 350L),
            Triple("Mahogany logs", "Mahogany plank", 1050L),
            Triple("Camphor logs", "Camphor plank", 1750L),
            Triple("Ironwood logs", "Ironwood plank", 3500L),
            Triple("Rosewood logs", "Rosewood plank", 5250L)
        ).forEach { (logs, plank, fee) ->
            recipes += MoneyRecipe("Plank Make: $logs", "Magic Processing", plank,
                listOf(MoneyIngredient(logs), MoneyIngredient("Nature rune"),
                    MoneyIngredient("Astral rune", 2.0), MoneyIngredient("Earth rune", 15.0)),
                coinFee = fee, actionsPerHour = 900,
                requirements = "86 Magic; Dream Mentor; Lunar spellbook",
                notes = "One plank per spell. Assumes no staff or sawmill voucher. Uses 70% sawmill fee.",
                source = "Plank_Make")
        }

        listOf(
            Triple("Crush bird nests", "Crushed nest", listOf(MoneyIngredient("Bird nest"))),
            Triple("Grind unicorn horns", "Unicorn horn dust", listOf(MoneyIngredient("Unicorn horn"))),
            Triple("Grind goat horns", "Goat horn dust", listOf(MoneyIngredient("Goat horn"))),
            Triple("Grind chocolate bars", "Chocolate dust", listOf(MoneyIngredient("Chocolate bar"))),
            Triple("Make crystal keys", "Crystal key", listOf(MoneyIngredient("Tooth half of key"), MoneyIngredient("Loop half of key"))),
            Triple("Make ultracompost", "Ultracompost", listOf(MoneyIngredient("Supercompost"), MoneyIngredient("Volcanic ash", 2.0)))
        ).forEach { (name, output, ingredients) ->
            recipes += MoneyRecipe(name, "Other Processing", output, ingredients,
                actionsPerHour = null,
                notes = "Profit per product only. Hourly rate depends on actual processing speed.",
                source = "Money_making_guide/Processing")
        }
        // Untradeable sap cannot be purchased, so only the material margin is
        // calculated; real profit/hour is shown in the Wiki method catalogue.
        listOf("Rune", "Amethyst", "Dragon").forEach { type ->
            recipes += MoneyRecipe("Bloodwood: seeking ${type.lowercase()} arrows",
                "Bloodwood & Skilling", "Seeking ${type.lowercase()} arrow",
                listOf(MoneyIngredient("Redwood logs", 1.0 / 105.0),
                    MoneyIngredient("Feather"), MoneyIngredient("$type arrow")),
                actionsPerHour = null,
                requirements = "77 Woodcutting, The Blood Moon Rises, bloodwood sap, Fletching",
                notes = "Uses 1 untradeable bucket of bloodwood sap per 105 arrows via redwood logs. " +
                    "Chopping and fletching time are not included: this is material margin per arrow, NOT GP/hour.",
                source = "Money_making_guide/Chopping_the_engorged_bloodwood_tree")
        }
        val herbs = listOf("Guam", "Marrentill", "Tarromin", "Harralander",
            "Ranarr weed", "Toadflax", "Irit leaf", "Avantoe", "Kwuarm", "Snapdragon",
            "Cadantine", "Lantadyme", "Dwarf weed", "Torstol")
        herbs.forEach { herb ->
            val cleanName = when (herb) {
                "Guam" -> "Guam leaf"
                else -> herb
            }
            val display = if (herb == "Guam") "Guam" else herb
            recipes += MoneyRecipe("Prepare $display potion (unf)", "Other Processing",
                "$display potion (unf)",
                listOf(MoneyIngredient(cleanName), MoneyIngredient("Vial of water")),
                actionsPerHour = 2200, requirements = "Herblore level varies by herb",
                notes = "Unfinished potion processing. Verify available market volume and skill level.",
                source = "Money_making_guide/Processing")
        }
        return recipes
    }
}

object MoneyProfitCalculator {
    private val normalize = Regex("[^a-z0-9]")
    private fun nameKey(name: String): String = name.lowercase(Locale.ROOT)
        .replace(normalize, "")

    // Build this once per refreshed snapshot. A linear scan of the full GE
    // mapping for each input would block the Compose UI during ranking.
    private var indexedSnapshot: MarketSnapshot? = null
    private var indexedItems: Map<String, MarketItem> = emptyMap()
    fun lookup(snapshot: MarketSnapshot, name: String): MarketItem? = synchronized(this) {
        if (indexedSnapshot !== snapshot) {
            indexedItems = snapshot.items.associateBy { nameKey(it.name) }
            indexedSnapshot = snapshot
        }
        indexedItems[nameKey(name)]
    }

    /**
     * Conservative price convention: inputs use instant-buy high;
     * outputs use instant-sell low. Missing price means no margin.
     */
    fun evaluate(recipe: MoneyRecipe, snapshot: MarketSnapshot, now: Long = System.currentTimeMillis()): MoneyEvaluation {
        val output = lookup(snapshot, recipe.output)
        val outputPrice = output?.let { snapshot.latest[it.id] }
        val missing = mutableListOf<String>()
        var cost = recipe.coinFee.toDouble()
        val bought = mutableListOf<Pair<MarketItem, Double>>()
        recipe.inputs.forEach { part ->
            val item = lookup(snapshot, part.item)
            val price = item?.let { snapshot.latest[it.id] }
            if (item == null || price?.high == null) missing += part.item
            else {
                cost += price.high.toDouble() * part.quantity
                bought += item to part.quantity
            }
        }
        if (output == null || outputPrice?.low == null) missing += recipe.output
        val costGp = if (missing.isEmpty()) ceil(cost).toLong() else null
        val sellGp = if (outputPrice?.low != null && outputPrice.low > 0) {
            (outputPrice.low.toLong() - geTax(outputPrice.low).toLong()) * recipe.outputQty
        } else null
        val profit = if (costGp != null && sellGp != null) sellGp - costGp else null
        val flags = mutableListOf<String>()
        if (missing.isNotEmpty()) flags += "Missing GE price: " + missing.distinct().joinToString(", ")
        val traded = bought.map { it.first } + listOfNotNull(output)
        if (traded.any { item ->
            val data = snapshot.latest[item.id]
            val time = if (item.id == output?.id) data?.lowTime else data?.highTime
            time == null || now - time * 1000L > 6L * 60L * 60L * 1000L
        }) flags += "Stale trade"
        if (traded.any { item -> (snapshot.hourly[item.id]?.volume ?: 0L) < 50L }) flags += "Low or unknown volume"
        if (profit != null && profit <= 0L) flags += "Not profitable at quoted prices"
        if (recipe.actionsPerHour != null) flags += "Hourly rate assumes " + recipe.actionsPerHour + " actions/hour"
        val fourHour = bought.mapNotNull { (item, qty) ->
            item.limit?.takeIf { it > 0 }?.let { floor(it / qty).toLong() }
        }.minOrNull()
        return MoneyEvaluation(recipe, costGp, sellGp, profit,
            if (profit != null && recipe.actionsPerHour != null) profit * recipe.actionsPerHour else null,
            flags, output, fourHour)
    }

    fun shop(row: WikiShopStock, snapshot: MarketSnapshot, now: Long = System.currentTimeMillis()): MoneyShopEvaluation {
        val item = lookup(snapshot, row.item)
        val price = item?.let { snapshot.latest[it.id] }
        val flags = mutableListOf<String>()
        if (row.basePurchasePrice == null) flags += "Unknown or non-GP shop price"
        if (price?.low == null) flags += "No recent GE sell quote"
        if (price?.lowTime == null || now - price.lowTime * 1000L > 6L * 60L * 60L * 1000L)
            flags += "Stale GE quote"
        if (item != null && (snapshot.hourly[item.id]?.volume ?: 0L) < 50) flags += "Low or unknown volume"
        val profit = if (price?.low != null && row.basePurchasePrice != null)
            price.low.toLong() - geTax(price.low).toLong() - row.basePurchasePrice
        else null
        return MoneyShopEvaluation(row, item, price?.low, profit, flags)
    }
}
