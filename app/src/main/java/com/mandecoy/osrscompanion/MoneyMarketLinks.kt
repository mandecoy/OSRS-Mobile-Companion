package com.mandecoy.osrscompanion

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The Wiki market mapping supplies exact item IDs and icon filenames.
 * Price links must use item IDs: a name-based URL breaks for charged items,
 * ambiguous bird nest variants and names containing parentheses.
 */
object MoneyMarketLinks {
    fun priceUrl(itemId: Int): String =
        "https://prices.runescape.wiki/osrs/item/" + itemId

    fun iconUrl(item: MarketItem?, name: String): String {
        if (item != null) {
            // Canonical item-ID icons avoid guessed, frequently incorrect
            // Wiki filenames, particularly for tablets and jewellery.
            return "https://static.runelite.net/cache/item/icon/${item.id}.png"
        }
        return osrsWikiImage(name.replace(' ', '_') + ".png")
    }

    fun openPrice(context: Context, item: MarketItem) {
        val url = Uri.parse(priceUrl(item.id))
        val intent = Intent(Intent.ACTION_VIEW, url)
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            // No browser available; do not crash a floating companion app.
        }
    }
}

@Composable
fun MoneyItemPriceLink(
    label: String,
    item: MarketItem?,
    iconSize: Dp = 28.dp,
    showIcon: Boolean = true
) {
    val context = LocalContext.current
    Row(
        modifier = if (item == null) Modifier
            else Modifier.clickable { MoneyMarketLinks.openPrice(context, item) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (showIcon) WikiIcon(MoneyMarketLinks.iconUrl(item, label), label, iconSize)
        Text(label, color = if (item == null) Color(0xffd7c7a4) else Color(0xffd7a93f),
            fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        if (item != null) Text("↗ Prices", color = Color(0xffd7a93f), fontSize = 10.sp)
    }
}
