package com.mandecoy.osrscompanion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MoneyMakingTest {
    private val now = 1_800_000_000_000L
    private val stamp = now / 1000L
    private fun snap(withOutput: Boolean = true): MarketSnapshot {
        val items = listOf(MarketItem(1, "Material", 1000, null),
            MarketItem(2, "Finished product", 5000, null))
        val latest = mapOf(
            1 to LatestPrice(200, 190, stamp, stamp),
            2 to LatestPrice(1200, if (withOutput) 1000 else null, stamp,
                if (withOutput) stamp else null))
        val hourly = mapOf(1 to HourPrice(200, 190, 1000, 900),
            2 to HourPrice(1200, 1000, 1000, 900))
        return MarketSnapshot(items, latest, hourly, now)
    }

    @Test fun processingUsesConservativePricesAndTax() {
        val recipe = MoneyRecipe("Test process", "Other Processing",
            "Finished product", listOf(MoneyIngredient("Material", 3.0)),
            actionsPerHour = 100)
        val evaluated = MoneyProfitCalculator.evaluate(recipe, snap(), now)
        assertEquals(600L, evaluated.inputCost)
        assertEquals(980L, evaluated.sellValue)
        assertEquals(380L, evaluated.profit)
        assertEquals(38000L, evaluated.gpHour)
        assertEquals(333L, evaluated.maximumFourHourBatches)
    }

    @Test fun missingOutputQuoteDoesNotInventProfit() {
        val recipe = MoneyRecipe("Test", "Other Processing",
            "Finished product", listOf(MoneyIngredient("Material")))
        val evaluated = MoneyProfitCalculator.evaluate(recipe, snap(false), now)
        assertNull(evaluated.profit)
        assertTrue(evaluated.flags.any { it.startsWith("Missing GE price") })
    }

    @Test fun shopProfitIsPerUnitNotTotalStock() {
        val row = WikiShopStock("Finished product", "500", "1m",
            700, null, "Example Shop", currency = "coins")
        val result = MoneyProfitCalculator.shop(row, snap(), now)
        assertEquals(280L, result.netUnitProfit)
    }

    @Test fun detectsNormalWikiShopStockTable() {
        val html = """
            <div class="mw-parser-output"><p>A shop in Varrock selling supplies.</p>
            <table class="wikitable">
            <tr><th></th><th>Item</th><th>Number in stock</th>
            <th>Restock time</th><th>Price sold at</th><th>Price bought at</th><th>GE price</th></tr>
            <tr><td><a title="File:Rope.png">icon</a></td><td><a title="Rope">Rope</a></td>
            <td>20</td><td>1m</td><td>23</td><td>7</td><td>64</td></tr>
            </table></div>
        """.trimIndent()
        val detail = MoneyWikiApi.parseShop(html, "Example shop")
        assertEquals(1, detail.stock.size)
        assertEquals("Rope", detail.stock.first().item)
        assertEquals(23L, detail.stock.first().basePurchasePrice)
        assertEquals("20", detail.stock.first().stock)
    }

    @Test fun parsesNativeWikiMethodRankingTable() {
        val html = """
            <table><tr><th>Method</th><th>Hourly profit</th><th>Skills</th>
            <th>Category</th><th>Intensity</th><th>Members</th></tr>
            <tr><td><a href="/w/Money_making_guide/Casting_plank_make">Casting plank make</a></td>
            <td>510,720</td><td>86 Magic</td><td>Processing</td><td>Low</td><td>Yes</td></tr></table>
        """.trimIndent()
        val methods = MoneyWikiApi.parseMoneyTable(html, "Processing")
        assertEquals(1, methods.size)
        assertEquals(510720L, methods.single().wikiHourlyGp)
        assertEquals("Casting plank make", methods.single().title)
        assertEquals("Low", methods.single().intensity)
    }
}
