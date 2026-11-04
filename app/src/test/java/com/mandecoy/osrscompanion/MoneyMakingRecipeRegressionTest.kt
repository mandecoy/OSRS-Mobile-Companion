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

    @Test fun pricesLinkUsesExactGeItemId() {
        assertEquals("https://prices.runescape.wiki/osrs/item/5075",
            MoneyMarketLinks.priceUrl(5075))
        assertEquals("https://static.runelite.net/cache/item/icon/5075.png",
            MoneyMarketLinks.iconUrl(MarketItem(5075, "Bird nest (empty)", null, null),
                "Bird nest (empty)"))
    }
}
