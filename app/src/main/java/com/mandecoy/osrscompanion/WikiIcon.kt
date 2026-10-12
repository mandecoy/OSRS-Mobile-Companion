package com.mandecoy.osrscompanion

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

private val wikiIconCache = ConcurrentHashMap<String, Bitmap>()

fun osrsWikiImage(fileName: String): String =
    "https://oldschool.runescape.wiki/w/Special:Redirect/file/" + Uri.encode(fileName)

fun iconUrlForAction(action: QuickAction): String {
    if (action.icon.startsWith("http")) return action.icon
    val label = action.label.lowercase()
    if (action.seconds != null) {
        return when {
            "herb" in label -> osrsWikiImage("Ranarr_weed.png")
            "bird" in label -> osrsWikiImage("Bird_house.png")
            else -> osrsWikiImage("Sleeping_cap.png")
        }
    }
    return when (action.route) {
        "puzzle", "web/puzzle" -> osrsWikiImage("Puzzle_box.png")
        "web/light" -> osrsWikiImage("Light_box.png")
        "web/prices" -> osrsWikiImage("Coins_10000.png")
        "web/wiki" -> osrsWikiImage("Book.png")
        "web/wise" -> osrsWikiImage("Wise_Old_Man.png")
        "web/stars" -> osrsWikiImage("Shooting_Star.png")
        "web/skills" -> osrsWikiImage("Max_cape.png")
        "web/dps" -> osrsWikiImage("Dragon_scimitar.png")
        "web/quests" -> osrsWikiImage("Quest_point_cape.png")
        "web/diaries" -> osrsWikiImage("Achievement_diary_cape.png")
        "web/clues" -> osrsWikiImage("Clue_scroll_(hard).png")
        "web/news" -> osrsWikiImage("Newspaper.png")
        else -> osrsWikiImage("Book.png")
    }
}

@Composable
fun WikiIcon(
    url: String,
    contentDescription: String?,
    size: Dp = 34.dp
) {
    val bitmapState = produceState<Bitmap?>(
        initialValue = wikiIconCache[url],
        key1 = url
    ) {
        if (value != null) return@produceState
        value = withContext(Dispatchers.IO) {
            try {
                wikiIconCache[url] ?: run {
                    val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                        instanceFollowRedirects = true
                        connectTimeout = 8000
                        readTimeout = 10000
                        setRequestProperty("User-Agent", "OSRS-Companion-Tool/0.3.1")
                    }
                    val bitmap = connection.inputStream.use { BitmapFactory.decodeStream(it) }
                    connection.disconnect()
                    if (bitmap != null) wikiIconCache[url] = bitmap
                    bitmap
                }
            } catch (_: Exception) {
                null
            }
        }
    }

    val bitmap = bitmapState.value
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = contentDescription,
            modifier = Modifier.size(size),
            contentScale = ContentScale.Fit
        )
    } else {
        Image(
            painter = painterResource(R.mipmap.ic_launcher),
            contentDescription = contentDescription,
            modifier = Modifier.size(size),
            contentScale = ContentScale.Fit
        )
    }
}
