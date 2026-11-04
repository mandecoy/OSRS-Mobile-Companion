package com.mandecoy.osrscompanion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MoneyMakingRecipeRegressionTest {
    private val recipes by lazy { MoneyRecipeCatalog.all() }

    @Test fun sapphireAmuletsAreCraftedUnstrungBeforeStringing() {
        val craft = recipes.single { it.name == "Craft Sapphire amulet (u)" }
        assertEquals("Sapphire amulet (u)", craft.output)
        assertEquals(listOf("Gold bar", "Sapphire"), craft.inputs.map { it.item })
        val string = recipes.single { it.name == "String Sapphire amulet" }
        assertEquals("Sapphire amulet", string.output)
        assertEquals(listOf("Sapphire amulet (u)", "Ball of wool"),
            string.inputs.map { it.item })
        val enchant = recipes.single { it.name == "Enchant Sapphire amulet" }
        assertEquals("Sapphire amulet", enchant.inputs.first().item)
    }

    @Test fun oneStepCraftEnchantedAmuletIncludesWoolAndRunes() {
        val combined = recipes.single { it.name == "Craft + enchant Sapphire amulet" }
        assertTrue(combined.inputs.any { it.item == "Ball of wool" })
        assertTrue(combined.inputs.any { it.item == "Cosmic rune" })
        assertEquals("Amulet of magic", combined.output)
    }

    @Test fun crushingBirdNestsUsesTradeableEmptyBirdNest() {
        val crush = recipes.single { it.name == "Crush empty bird nests" }
        assertEquals("Crushed nest", crush.output)
        assertEquals(listOf("Bird nest (empty)"), crush.inputs.map { it.item })
    }

    @Test fun allWikiMagicTabletRecipesAreAvailable() {
        val tablets = MagicTabletCatalog.all()
        assertEquals(46, tablets.size)
        assertEquals(46, tablets.map { it.output }.distinct().size)
        assertTrue(tablets.all { it.category == "Teleport Tablets" })
        val barrows = tablets.single { it.output == "Barrows teleport (tablet)" }
        assertTrue(barrows.inputs.any { it.item == "Blood rune" && it.quantity == 1.0 })
        assertTrue(barrows.inputs.any { it.item == "Law rune" && it.quantity == 2.0 })
        val varrock = tablets.single { it.output == "Varrock teleport (tablet)" }
        assertTrue(varrock.inputs.any { it.item == "Soft clay" && it.quantity == 1.0 })
        val ice = tablets.single { it.output == "Ice plateau teleport (tablet)" }
        assertTrue(ice.inputs.any { it.item == "Water rune" && it.quantity == 8.0 })
        val arceuus = tablets.single { it.output == "Arceuus library teleport (tablet)" }
        assertFalse(arceuus.verifiedMaterials)
        assertTrue(arceuus.inputs.any { it.item == "Dark essence block" })
    }

    @Test fun tabletUsesCanonicalGeNameAndShowsLiveQuote() {
        val recipe = MagicTabletCatalog.all().single {
            it.output == "Varrock teleport (tablet)"
        }
        val items = listOf(
            MarketItem(8007, "Varrock teleport (tablet)", 15000, null),
            MarketItem(556, "Air rune", 50000, null),
            MarketItem(554, "Fire rune", 50000, null),
            MarketItem(563, "Law rune", 18000, null),
            MarketItem(1761, "Soft clay", 13000, null)
        )
        val now = 1_800_000_000_000L
        val prices = mapOf(
            8007 to LatestPrice(560, 550, now / 1000L, now / 1000L),
            556 to LatestPrice(6, 5, now / 1000L, now / 1000L),
            554 to LatestPrice(6, 5, now / 1000L, now / 1000L),
            563 to LatestPrice(117, 115, now / 1000L, now / 1000L),
            1761 to LatestPrice(93, 91, now / 1000L, now / 1000L)
        )
        val snapshot = MarketSnapshot(items, prices, emptyMap(), now)
        val profit = MoneyProfitCalculator.evaluate(recipe, snapshot, now)
        assertEquals(8007, profit.outputItem?.id)
        assertEquals(550, profit.outputSellQuote)
        assertEquals(234L, profit.inputCost)
        assertEquals(539L, profit.sellValue)
        assertEquals(305L, profit.profit)
    }

    @Test fun missingOutputQuoteDoesNotHideKnownInputCosts() {
        val recipe = MagicTabletCatalog.all().single {
            it.output == "Varrock teleport (tablet)"
        }
        val now = 1_800_000_000_000L
        val snapshot = MarketSnapshot(
            listOf(
                MarketItem(8007, "Varrock teleport (tablet)", 15000, null),
                MarketItem(556, "Air rune", 50000, null),
                MarketItem(554, "Fire rune", 50000, null),
                MarketItem(563, "Law rune", 18000, null),
                MarketItem(1761, "Soft clay", 13000, null)
            ),
            mapOf(
                556 to LatestPrice(6, 5, now / 1000L, now / 1000L),
                554 to LatestPrice(6, 5, now / 1000L, now / 1000L),
                563 to LatestPrice(117, 115, now / 1000L, now / 1000L),
                1761 to LatestPrice(93, 91, now / 1000L, now / 1000L)
            ),
            emptyMap(), now
        )
        val eval = MoneyProfitCalculator.evaluate(recipe, snapshot, now)
        assertEquals(234L, eval.inputCost)
        assertNull(eval.profit)
    }

    @Test fun bloodwoodUsesGeneralSkillingCategory() {
        val entries = recipes.filter { it.name.startsWith("Bloodwood:") }
        assertTrue(entries.isNotEmpty())
        assertTrue(entries.all { it.category == "Gathering & Skilling" })
    }

    @Test fun wesleyNestCrushingIncludesServiceFee() {
        val wesley = recipes.single { it.name == "Crush bird nests at Wesley (Nardah)" }
        assertEquals(50L, wesley.coinFee)
        assertEquals("Bird nest (empty)", wesley.inputs.single().item)
        assertEquals("Crushed nest", wesley.output)
    }

    @Test fun pricesLinkUsesExactGeItemId() {
        assertEquals("https://prices.runescape.wiki/osrs/item/5075",
            MoneyMarketLinks.priceUrl(5075))
        assertEquals("https://static.runelite.net/cache/item/icon/5075.png",
            MoneyMarketLinks.iconUrl(MarketItem(5075, "Bird nest (empty)", null, null),
                "Bird nest (empty)"))
    }
}
