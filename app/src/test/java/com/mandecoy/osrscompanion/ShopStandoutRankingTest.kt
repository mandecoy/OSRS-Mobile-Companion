package com.mandecoy.osrscompanion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShopStandoutRankingTest {
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
        WikiShopStock(item, stock, "1m", cost, 0, shop)

    @Test fun highestSingleItemWinsEvenWhenOtherShopHasMoreProfitableItems() {
        val many = WikiShopDetail("Many Items Shop", "Varrock", "",
            listOf(row("Many Items Shop", "One", 500),
                row("Many Items Shop", "Two", 250),
                row("Many Items Shop", "Three", 400)))
        val big = WikiShopDetail("Big Standout Shop", "Lumbridge", "",
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
        val zero = WikiShopDetail("Empty shop", "", "",
            listOf(row("Empty shop", "Fourth", 1, "0")))
        val ranked = ShopRankings.evaluate(listOf(zero), snapshot(), now)
        assertNull(ranked.single().standout)
        assertEquals(0, ranked.single().profitableCount)
        assertFalse(ShopRankings.isBuyableStock("0"))
        assertTrue(ShopRankings.isBuyableStock("∞"))
        assertTrue(ShopRankings.isBuyableStock("1,000"))
    }

    @Test fun lossMakersAndMissingQuotesAreNotCountedAsProfitable() {
        val bad = WikiShopDetail("Unprofitable", "", "",
            listOf(row("Unprofitable", "Two", 900)))
        val ranked = ShopRankings.evaluate(listOf(bad), snapshot(), now)
        assertEquals(0, ranked.single().profitableCount)
    }

    @Test fun sortedProfitIsAfterGrandExchangeTax() {
        val shop = WikiShopDetail("A", "", "",
            listOf(row("A", "One", 500)))
        val rank = ShopRankings.evaluate(listOf(shop), snapshot(), now)
        // GE low 1000 => 20 tax => 980 net, minus 500 from shop.
        assertEquals(480L, rank.single().standoutProfit)
    }
}
