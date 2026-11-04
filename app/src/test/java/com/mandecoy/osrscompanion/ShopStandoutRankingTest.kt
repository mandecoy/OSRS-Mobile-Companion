package com.mandecoy.osrscompanion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShopStandoutRankingTest {
    private fun coinShop(title: String, location: String, notes: String,
                         stock: List<WikiShopStock>): WikiShopDetail =
        WikiShopDetail(title, location, notes, stock, currency = "coins")

    private val now = 1_800_000_000_000L
    private val time = now / 1000L
    private val items = listOf(
        MarketItem(1, "One", 1000, null),
        MarketItem(2, "Two", 1000, null),
        MarketItem(3, "Three", 1000, null),
        MarketItem(4, "Fourth", 1000, null)
    )

    private fun snapshot(): MarketSnapshot = MarketSnapshot(
        items,
        mapOf(
            1 to LatestPrice(1500, 1000, time, time),
            2 to LatestPrice(750, 500, time, time),
            3 to LatestPrice(1000, 700, time, time),
            4 to LatestPrice(2200, 2000, time, time)
        ),
        items.associate { it.id to HourPrice(1500, 1000, 200, 200) },
        now
    )
    private fun row(shop: String, item: String, cost: Long, stock: String = "10"): WikiShopStock =
        WikiShopStock(item, stock, "1m", cost, 0, shop, currency = "coins")

    @Test fun highestSingleItemWinsEvenWhenOtherShopHasMoreProfitableItems() {
        val many = coinShop("Many Items Shop", "Varrock", "",
            listOf(row("Many Items Shop", "One", 500),
                row("Many Items Shop", "Two", 250),
                row("Many Items Shop", "Three", 400)))
        val big = coinShop("Big Standout Shop", "Lumbridge", "",
            listOf(row("Big Standout Shop", "Fourth", 200)))
        val ranked = ShopRankings.evaluate(listOf(many, big), snapshot(), now)
        assertEquals("Big Standout Shop", ranked[0].detail.title)
        assertEquals("Fourth", ranked[0].standout?.row?.item)
        assertEquals(1760L, ranked[0].standoutProfit)
        assertEquals(1, ranked[0].profitableCount)
        assertEquals("Many Items Shop", ranked[1].detail.title)
        assertEquals("One", ranked[1].standout?.row?.item)
        assertEquals(3, ranked[1].profitableCount)
        assertEquals(2, ranked[1].otherItems.size)
    }

    @Test fun emptyStockDoesNotCreateFakeStandout() {
        val zero = coinShop("Empty shop", "", "",
            listOf(row("Empty shop", "Fourth", 1, "0")))
        val ranked = ShopRankings.evaluate(listOf(zero), snapshot(), now)
        assertNull(ranked.single().standout)
        assertEquals(0, ranked.single().profitableCount)
        assertFalse(ShopRankings.isBuyableStock("0"))
        assertTrue(ShopRankings.isBuyableStock("∞"))
        assertTrue(ShopRankings.isBuyableStock("1,000"))
    }

    @Test fun lossMakersAndMissingQuotesAreNotCountedAsProfitable() {
        val bad = coinShop("Unprofitable", "", "",
            listOf(row("Unprofitable", "Two", 900)))
        val ranked = ShopRankings.evaluate(listOf(bad), snapshot(), now)
        assertEquals(0, ranked.single().profitableCount)
    }

    @Test fun sortedProfitIsAfterGrandExchangeTax() {
        val shop = coinShop("A", "", "",
            listOf(row("A", "One", 500)))
        val rank = ShopRankings.evaluate(listOf(shop), snapshot(), now)
        // GE low 1000 => 20 tax => 980 net, minus 500 from shop.
        assertEquals(480L, rank.single().standoutProfit)
    }

    @Test fun numuliteCannotBeChosenAsStandoutButOtherCoinItemsCan() {
        val snapshot = MarketSnapshot(
            items + MarketItem(5, "Numulite", 30000, null),
            snapshot().latest + (5 to LatestPrice(3000, 3000, time, time)),
            snapshot().hourly + (5 to HourPrice(3000, 3000, 1000, 1000)),
            now
        )
        val shop = coinShop("Ordinary Coin Shop", "Varrock", "",
            listOf(row("Ordinary Coin Shop", "Numulite", 1),
                row("Ordinary Coin Shop", "One", 500)))
        val rank = ShopRankings.evaluate(listOf(shop), snapshot, now).single()
        assertEquals("One", rank.standout?.row?.item)
        assertEquals(1, rank.profitableCount)
    }

    @Test fun tokkulShopsCannotBeRankedAsIfTheirPricesWereGp() {
        val tokkulShop = coinShop("TzHaar-Hur-Lek's Ore and Gem Store",
            "Mor Ul Rek", "Items can only be bought with Tokkul.",
            listOf(row("TzHaar-Hur-Lek's Ore and Gem Store", "Fourth", 1)))
        val genericName = coinShop("Special Currency Shop", "Mor Ul Rek",
            "Items are bought with Tokkul.",
            listOf(row("Special Currency Shop", "Fourth", 1)))
        val ranks = ShopRankings.evaluate(listOf(tokkulShop, genericName), snapshot(), now)
        assertTrue(ranks.all { it.standout == null })
        assertTrue(ranks.all { it.profitableCount == 0 })
        assertTrue(ShopRankings.isKnownSpecialCurrencyShop("TzHaar-Mej-Roh's Rune Store"))
    }

    @Test fun numuliteCurrencyShopsCannotAppearAsGpOpportunities() {
        val shop = coinShop("Fossil Island Currency Store",
            "Fossil Island", "Items are bought with numulites.",
            listOf(row("Fossil Island Currency Store", "Fourth", 1)))
        assertTrue(ShopRankings.isSpecialCurrencyShop(shop))
        assertNull(ShopRankings.evaluate(listOf(shop), snapshot(), now).single().standout)
        assertTrue(ShopRankings.isExcludedItem("Numulite"))
        assertTrue(ShopRankings.isExcludedItem("Tokkul"))
    }
}
