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

// Actual OSRS Cake of guidance item 7542 sprite, distributed by the public
// osrsbox-db inventory icons repository. Keep this small icon inside the APK:
// remote Wiki redirect failures must never replace Guides with launcher art.
private const val GUIDANCE_CAKE_SPRITE = "iVBORw0KGgoAAAANSUhEUgAAACQAAAAgCAYAAAB6kdqOAAAEMElEQVR4XuWW7UtUaRjG5/wHfZovfhH8IAiCCBKDBInIMDiIMYNiYmShCetKtshKLuK2ZliGijpmZs46bqZpmmWTjWLmG76spUnSm7T7j1zNdY+Pczw767rOGb90w4Uzx5m5f+e6X55jsXwfocF45RiCSSOrLCVZ9z7mEUrU6/HAk2iVv9TvnZ0if16OXE+yWPBbTU2soTQ8GXiAxz4fhrxe1CdYMTMxsadX/ucYTI5DSxDIZbXgbmsLbtfXxwpKk4TTz54iMDaGFyMj8A8Py7Xl2VfYWFnBh3fvsL60hPlAAC9HRwXc13UHHU23zIbSsL64iLWFeazOzWHl9SyWZmYwNzmJoVyHlOl1oQt/ns/HX1++YHNtVSDp2sSjob0SmthXGj5ubYnoAkUXfM4sjJxxYDI/F/OFbgFaKnLj750dbG9syE0sTE2Joyx1f/dd3GlpNg/q66dPArW1vo4HOXbRmCsbgV2gN8UFWD6XJ24ZQdjwLF91ZaUApcTHRwuliRPjLqeUiTAPgxpsK8PTe5cR8FVhYfgqVoMuKZDB3l4RB2C4rw/NDQ1w2u3mA/FOedfcN/qEbGI19uMPB0TPhgalj/j58tJSpFpPmAGkiQsjnnIM3O8RqcRjf/RLYibl1Kmxp0tT4+Mylfz/jbpauBITzAN60l0hZSAARTf4ng4QhGuAEATgKuAEsvF5rae9Xdx0JsQjLZkgUQFp8HuvSEK1eyg6QhheoxMccUIYRegaWxpKUpKQm5WK9NSE6IF4x1UnU+Uu6YASr/c4Mg9UU0Y6qoPfLck/hYKck7CfSooGSMPi9LTImxQnPaPez77wyx4yypsdAul2ZIgaT9tQVepA+blMFJ2xwZmRclQgTY6BSGIpmJjj39N4Ad03itF1/Tw814rQXleI1tqzaP6lQFT/kws15U5UFGeh2J0uZcuwJQqUMeO/RPgRgsfAzscP+Ly9LduZ25fHBmG4gwiiVySoxp/zUFuRIy6VnT29CxJy6BAuaftGmudP282bstDev30jZ5Ta0Co5xdd66aGaruaLS9Vl2ftADgXDA5Gjyonhac0p4ng7MjPliGCJ2CdMqhJSfE0pQCX1mYYq9/9xRYUmpaETb5eX5VRn83Kp2eKs8N66KGUx3r3qFUoBGiGPAMMIHQ0TbmeoXzY3pWfq0tMESO8KkxOG/aF64iAdAYYRBuJ5xccKlkgBKUcIwRKwL4w9cZCM2f4jQmfVaNePeNTxwz+AOKaEUSC/VuZGc+eHif1AA62X0He7RPrmykW77A6CcHy5U/QTY/wlkyIyEJceFxo3rNuRJiDcJzF2hxEGkkeMIJCaKq58LjRuWcIdAwwjDMQnwP6WUnGHk0VHCEIwHpAxLpUK3QPYbrnUzmHfqJ45JndUhPcGy6X2TqTxNn4zZhFOaMpiMzf0jhwnzDdowfkLljq+qQAAAABJRU5ErkJggg=="
private val guidanceCakeBitmap: Bitmap? by lazy {
    try {
        val bytes = android.util.Base64.decode(GUIDANCE_CAKE_SPRITE, android.util.Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    } catch (_: Exception) { null }
}

fun osrsWikiImage(fileName: String): String =
    "https://oldschool.runescape.wiki/w/Special:Redirect/file/" + Uri.encode(fileName)

fun iconUrlForAction(action: QuickAction): String {
    val label = action.label.lowercase()

    if (action.seconds != null) {
        return when {
            "herb" in label -> osrsWikiImage("Ranarr_weed.png")
            "bird" in label -> osrsWikiImage("Bird_house.png")
            "allot" in label -> osrsWikiImage("Watermelon.png")
            "hop" in label -> osrsWikiImage("Jute_fibre.png")
            "bush" in label -> osrsWikiImage("White_berries.png")
            "oak" in label -> osrsWikiImage("Oak_tree_(grown).png")
            "willow" in label -> osrsWikiImage("Willow_tree_(grown).png")
            "maple" in label -> osrsWikiImage("Maple_Tree_(grown).png")
            "yew" in label -> osrsWikiImage("Yew_tree_(grown).png")
            "magic" in label -> osrsWikiImage("Magic_tree_(grown).png")
            "fruit" in label -> osrsWikiImage("Palm_tree_(grown).png")
            "teak" in label -> osrsWikiImage("Teak_tree_(grown).png")
            "mahogany" in label -> osrsWikiImage("Mahogany_tree_(grown).png")
            "cactus" in label -> osrsWikiImage("Cactus_(grown).png")
            "calquat" in label -> osrsWikiImage("Calquat_tree_(grown).png")
            "coral" in label -> osrsWikiImage("Elkhorn_coral.png")
            else -> osrsWikiImage("Watch.png")
        }
    }

    return when (action.route) {
        "guides" -> osrsWikiImage("Cake_of_guidance.png")
        "puzzle", "web/puzzle" -> osrsWikiImage("Puzzle_box_zulrah.png")
        "lightbox", "web/light" -> osrsWikiImage("Light_box.png")
        "ge_tools" -> osrsWikiImage("Grand_Exchange_Clerk.png")
        "portfolio" -> osrsWikiImage("Platinum_token.png")
        "notepad" -> osrsWikiImage("Spell_scroll.png")
        "web/prices" -> osrsWikiImage("Coins_10000.png")
        "web/wiki" -> osrsWikiImage("Old School RuneScape Wiki logo (alternate).png")
        "web/wise" -> osrsWikiImage("Wise_Old_Man.png")
        "web/stars" -> osrsWikiImage("Shooting_Star_Display.png")
        "web/skills" -> osrsWikiImage("Max_cape.png")
        "web/dps" -> osrsWikiImage("Dragon_scimitar.png")
        "web/quests" -> osrsWikiImage("Quest_point_cape.png")
        "web/diaries" -> osrsWikiImage("Achievement_diary_cape.png")
        "web/clues" -> osrsWikiImage("Clue_scroll_(hard).png")
        "web/news" -> osrsWikiImage("Newspaper.png")
        "timers" -> osrsWikiImage("Watch.png")
        "floating" -> osrsWikiImage("Magical_orb.png")
        else -> if (action.icon.startsWith("http")) action.icon else osrsWikiImage("Book.png")
    }
}

@Composable
fun WikiIcon(
    url: String,
    contentDescription: String?,
    size: Dp = 34.dp
) {
    if (url.contains("Cake_of_guidance.png", ignoreCase = true)) {
        guidanceCakeBitmap?.let { cake ->
            Image(
                bitmap = cake.asImageBitmap(),
                contentDescription = contentDescription ?: "Cake of guidance",
                modifier = Modifier.size(size),
                contentScale = ContentScale.Fit
            )
            return
        }
    }
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
                        setRequestProperty("User-Agent", "OSRS-Companion-Tool/0.5.4")
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
