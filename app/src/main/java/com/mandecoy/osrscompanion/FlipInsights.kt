package com.mandecoy.osrscompanion

import kotlin.math.floor

/**
 * Historical ranges from the RuneScape Wiki 5-minute average trade buckets.
 *
 * IMPORTANT: avgLowPrice and avgHighPrice describe trades which already
 * happened. This is a planning estimate, NOT executable market depth, a
 * prediction, or a guaranteed filled Grand Exchange order.
 */
data class FlipInsight(
    val observedLow24h: Int,
    val observedHigh24h: Int,
    val suggestedBuy: Int,
    val suggestedSell: Int,
    val estimatedNetEach: Long,
    val estimatedNetAtLimit: Long?,
    val volume24h: Long,
    val observations: Int,
    val confidence: String
)

/** Linear-interpolated sample percentile; price outliers affect it less than extremes. */
private fun pricePercentile(values: List<Int>, fraction: Double): Int {
    val sorted = values.sorted()
    if (sorted.size == 1) return sorted.first()
    val spot = (sorted.size - 1) * fraction.coerceIn(0.0, 1.0)
    val left = floor(spot).toInt()
    val upper = (left + 1).coerceAtMost(sorted.lastIndex)
    return (sorted[left] + (sorted[upper] - sorted[left]) * (spot - left)).toInt()
}

/**
 * Robust buy/sell targets: 30th percentile of observed low-side trades and
 * 70th percentile of high-side trades. Extremes are informational only, not
 * targets. GE tax is charged ONLY on the sale, using the existing geTax().
 */
fun calculate24hFlipInsight(
    points: List<HistoryPoint>,
    buyLimit: Int?,
    nowEpochSeconds: Long = System.currentTimeMillis() / 1000L
): FlipInsight? {
    val recent = points.filter {
        it.timestamp > nowEpochSeconds - 24L * 3600L &&
            it.timestamp <= nowEpochSeconds + 300L
    }
    val lows = recent.mapNotNull { p ->
        p.avgLow?.takeIf { it > 0 && p.lowVolume > 0L }
    }
    val highs = recent.mapNotNull { p ->
        p.avgHigh?.takeIf { it > 0 && p.highVolume > 0L }
    }
    if (lows.size < 6 || highs.size < 6) return null
    val low = lows.minOrNull() ?: return null
    val high = highs.maxOrNull() ?: return null
    val buy = pricePercentile(lows, 0.30)
    val sell = pricePercentile(highs, 0.70)
    val net = sell.toLong() - buy.toLong() - geTax(sell).toLong()
    val volume = recent.sumOf { it.highVolume.coerceAtLeast(0L) + it.lowVolume.coerceAtLeast(0L) }
    val last = recent.maxOfOrNull { it.timestamp } ?: 0L
    val confidence = when {
        last < nowEpochSeconds - 3600L -> "Stale"
        lows.size < 24 || highs.size < 24 || volume < 100L -> "Thin data"
        volume < 1000L -> "Moderate"
        else -> "Active"
    }
    return FlipInsight(
        observedLow24h = low,
        observedHigh24h = high,
        suggestedBuy = buy,
        suggestedSell = sell,
        estimatedNetEach = net,
        estimatedNetAtLimit = buyLimit?.takeIf { it > 0 }?.let { net * it.toLong() },
        volume24h = volume,
        observations = minOf(lows.size, highs.size),
        confidence = confidence
    )
}
