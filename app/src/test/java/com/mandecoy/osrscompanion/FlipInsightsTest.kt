package com.mandecoy.osrscompanion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FlipInsightsTest {
    @Test fun stableMarketUsesTaxAndBuyLimit() {
        val now = 1_800_000_000L
        val history = (1..40).map { index ->
            HistoryPoint(
                timestamp = now - index * 300L,
                avgHigh = 110_000,
                avgLow = 100_000,
                highVolume = 100L,
                lowVolume = 100L
            )
        }
        val insight = calculate24hFlipInsight(history, 10, now)!!
        assertEquals(100_000, insight.observedLow24h)
        assertEquals(110_000, insight.observedHigh24h)
        assertEquals(100_000, insight.suggestedBuy)
        assertEquals(110_000, insight.suggestedSell)
        assertEquals(7_800L, insight.estimatedNetEach)
        assertEquals(78_000L, insight.estimatedNetAtLimit)
        assertEquals(8_000L, insight.volume24h)
    }

    @Test fun oldTradesDoNotCountAsCurrentOpportunities() {
        val now = 1_800_000_000L
        val stale = (1..50).map {
            HistoryPoint(now - 48 * 3600L - it * 300L,
                avgHigh = 120_000, avgLow = 90_000,
                highVolume = 10, lowVolume = 10)
        }
        assertNull(calculate24hFlipInsight(stale, 100, now))
    }

    @Test fun missingSellSideDataDoesNotProduceFakeFlip() {
        val now = 1_800_000_000L
        val oneSided = (1..50).map {
            HistoryPoint(now - it * 300L,
                avgHigh = null, avgLow = 1_000_000,
                highVolume = 0, lowVolume = 100)
        }
        assertNull(calculate24hFlipInsight(oneSided, 8, now))
    }

    @Test fun sparseMarketGetsThinDataWarning() {
        val now = 1_800_000_000L
        val lowVolume = (1..8).map {
            HistoryPoint(now - it * 300L,
                avgHigh = 10_000, avgLow = 8_000,
                highVolume = 1, lowVolume = 1)
        }
        assertEquals("Thin data",
            calculate24hFlipInsight(lowVolume, 10, now)?.confidence)
    }
}
