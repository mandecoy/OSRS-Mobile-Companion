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
        val barrows = tablets.single { it.output == "Barrows teleport" }
        assertTrue(barrows.inputs.any { it.item == "Blood rune" && it.quantity == 1.0 })
        assertTrue(barrows.inputs.any { it.item == "Law rune" && it.quantity == 2.0 })
        val varrock = tablets.single { it.output == "Varrock teleport" }
        assertTrue(varrock.inputs.any { it.item == "Soft clay" && it.quantity == 1.0 })
        val ice = tablets.single { it.output == "Ice plateau teleport" }
        assertTrue(ice.inputs.any { it.item == "Water rune" && it.quantity == 8.0 })
        val arceuus = tablets.single { it.output == "Arceuus library teleport" }
        assertFalse(arceuus.verifiedMaterials)
        assertTrue(arceuus.inputs.any { it.item == "Dark essence block" })
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
