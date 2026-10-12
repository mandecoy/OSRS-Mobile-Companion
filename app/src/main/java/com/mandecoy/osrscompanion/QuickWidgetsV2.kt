package com.mandecoy.osrscompanion

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import coil.compose.AsyncImage

private val QuickStoneDark = Color(0xff201c18)
private val QuickCard = Color(0xff2e2924)
private val QuickGold = Color(0xffd7a93f)
private val QuickGoldDark = Color(0xff8b6722)
private val QuickParchment = Color(0xffd7c7a4)
private val QuickText = Color(0xfff4ead4)

fun osrsWikiImage(file: String): String =
    "https://oldschool.runescape.wiki/w/Special:Redirect/file/" + Uri.encode(file)

data class WidgetOption(
    val id: String,
    val label: String,
    val route: String? = null,
    val seconds: Long? = null,
    val wikiFile: String,
    val customTimer: Boolean = false
)

val WidgetOptions = listOf(
    WidgetOption("custom_timer", "Custom Timer", wikiFile = "Watch.png", customTimer = true),
    WidgetOption("afk5", "AFK 5 Minutes", seconds = 300, wikiFile = "Watch.png"),
    WidgetOption("afk10", "AFK 10 Minutes", seconds = 600, wikiFile = "Watch.png"),
    WidgetOption("herb", "Herb Run", seconds = 80 * 60, wikiFile = "Ranarr_weed.png"),
    WidgetOption("bird", "Birdhouse Run", seconds = 50 * 60, wikiFile = "Bird_house.png"),
    WidgetOption("timers", "Timers", route = "timers", wikiFile = "Watch.png"),
    WidgetOption("puzzle", "Puzzle Box", route = "puzzle", wikiFile = "Puzzle_box_zulrah.png"),
    WidgetOption("light", "Light Box", route = "web/light", wikiFile = "Light_box.png"),
    WidgetOption("prices", "Grand Exchange Prices", route = "web/prices", wikiFile = "Coins_10000.png"),
    WidgetOption("wiki", "OSRS Wiki", route = "web/wiki", wikiFile = "Book.png"),
    WidgetOption("wise", "Wise Old Man", route = "web/wise", wikiFile = "Wise_Old_Man_(2022_Birthday_event).png"),
    WidgetOption("stars", "Shooting Stars", route = "web/stars", wikiFile = "Shooting_Star_Display.png"),
    WidgetOption("skills", "Skill Calculators", route = "web/skills", wikiFile = "Skills_icon.png"),
    WidgetOption("dps", "DPS Calculator", route = "web/dps", wikiFile = "Attack_style_icon.png"),
    WidgetOption("quests", "Quests", route = "web/quests", wikiFile = "Quest_point_hood.png"),
    WidgetOption("diaries", "Achievement Diaries", route = "web/diaries", wikiFile = "Achievement_Diaries.png"),
    WidgetOption("clues", "Clue Helper", route = "web/clues", wikiFile = "Scroll_box_(hard).png"),
    WidgetOption("news", "Old School News", route = "web/news", wikiFile = "Newspaper.png")
)

fun imageForQuickAction(action: QuickAction): String {
    if (action.icon.startsWith("http")) return action.icon
    if (action.seconds != null) {
        val label = action.label.lowercase()
        return when {
            "herb" in label -> osrsWikiImage("Ranarr_weed.png")
            "bird" in label -> osrsWikiImage("Bird_house.png")
            else -> osrsWikiImage("Watch.png")
        }
    }
    return when (action.route) {
        "puzzle", "web/puzzle" -> osrsWikiImage("Puzzle_box_zulrah.png")
        "web/light" -> osrsWikiImage("Light_box.png")
        "web/prices" -> osrsWikiImage("Coins_10000.png")
        "web/wiki" -> osrsWikiImage("Book.png")
        "web/wise" -> osrsWikiImage("Wise_Old_Man_(2022_Birthday_event).png")
        "web/stars" -> osrsWikiImage("Shooting_Star_Display.png")
        "web/skills" -> osrsWikiImage("Skills_icon.png")
        "web/dps" -> osrsWikiImage("Attack_style_icon.png")
        "web/quests" -> osrsWikiImage("Quest_point_hood.png")
        "web/diaries" -> osrsWikiImage("Achievement_Diaries.png")
        "web/clues" -> osrsWikiImage("Scroll_box_(hard).png")
        "web/news" -> osrsWikiImage("Newspaper.png")
        else -> osrsWikiImage("Watch.png")
    }
}

@Composable
fun QuickWidgetsV2Screen(nav: NavHostController) {
    val context = LocalContext.current
    var revision by remember { mutableStateOf(0) }
    val current = remember(revision) { QuickActionStore.load(context) }

    Screen(nav, "Edit Quick Widgets") { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    "Each slot is independent. Open its dropdown and choose any tool, preset timer, or a custom timer.",
                    color = QuickParchment
                )
            }

            (1..4).forEach { slot ->
                item(key = "quick_slot_" + slot) {
                    QuickSlotEditor(
                        slot = slot,
                        current = current[slot - 1],
                        onSaved = { revision++ }
                    )
                }
            }

            item {
                Text(
                    "Widget artwork is loaded from Old School RuneScape Wiki media. If an image cannot load, the app icon is used as a fallback.",
                    color = Color(0xff70665a),
                    fontSize = 11.sp
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickSlotEditor(
    slot: Int,
    current: QuickAction,
    onSaved: () -> Unit
) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    var selected by remember(current.id) {
        mutableStateOf(
            WidgetOptions.firstOrNull { option ->
                option.route == current.route && option.seconds == current.seconds &&
                    !option.customTimer
            }
        )
    }
    var customMode by remember(current.id) {
        mutableStateOf(
            current.seconds != null &&
                selected == null &&
                !current.id.startsWith("afk") &&
                !current.id.startsWith("herb") &&
                !current.id.startsWith("bird")
        )
    }
    var customLabel by remember(current.id) { mutableStateOf(if (customMode) current.label else "Custom timer") }
    var customMinutes by remember(current.id) {
        mutableStateOf(if (customMode && current.seconds != null) (current.seconds / 60).toString() else "5")
    }
    var customSeconds by remember(current.id) {
        mutableStateOf(if (customMode && current.seconds != null) (current.seconds % 60).toString() else "0")
    }

    val displayLabel = if (customMode) "Custom Timer — " + current.label else (selected?.label ?: current.label)
    val previewUrl = if (customMode) osrsWikiImage("Watch.png")
        else selected?.let { osrsWikiImage(it.wikiFile) } ?: imageForQuickAction(current)

    Card(
        colors = CardDefaults.cardColors(containerColor = QuickCard),
        border = BorderStroke(1.dp, QuickGoldDark)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AsyncImage(
                    model = previewUrl,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    contentScale = ContentScale.Fit,
                    error = painterResource(R.mipmap.ic_launcher),
                    fallback = painterResource(R.mipmap.ic_launcher),
                    placeholder = painterResource(R.mipmap.ic_launcher)
                )
                Column {
                    Text("SLOT " + slot, color = QuickGold, fontWeight = FontWeight.Bold)
                    Text(displayLabel, color = QuickText, fontSize = 13.sp)
                }
            }

            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded }
            ) {
                OutlinedTextField(
                    value = if (customMode) "Custom Timer" else (selected?.label ?: current.label),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Choose widget") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    WidgetOptions.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.label) },
                            leadingIcon = {
                                AsyncImage(
                                    model = osrsWikiImage(option.wikiFile),
                                    contentDescription = null,
                                    modifier = Modifier.size(28.dp),
                                    contentScale = ContentScale.Fit,
                                    error = painterResource(R.mipmap.ic_launcher),
                                    fallback = painterResource(R.mipmap.ic_launcher)
                                )
                            },
                            onClick = {
                                expanded = false
                                if (option.customTimer) {
                                    customMode = true
                                    selected = option
                                } else {
                                    customMode = false
                                    selected = option
                                    QuickActionStore.save(
                                        context,
                                        slot,
                                        QuickAction(
                                            id = option.id,
                                            label = option.label,
                                            icon = osrsWikiImage(option.wikiFile),
                                            route = option.route,
                                            seconds = option.seconds
                                        )
                                    )
                                    onSaved()
                                }
                            }
                        )
                    }
                }
            }

            if (customMode) {
                OutlinedTextField(
                    value = customLabel,
                    onValueChange = { customLabel = it.take(20) },
                    label = { Text("Timer name") },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = customMinutes,
                        onValueChange = { customMinutes = it.filter(Char::isDigit).take(2) },
                        label = { Text("Minutes") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = customSeconds,
                        onValueChange = { customSeconds = it.filter(Char::isDigit).take(2) },
                        label = { Text("Seconds") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }
                Button(
                    onClick = {
                        val minutes = (customMinutes.toLongOrNull() ?: 0L).coerceIn(0L, 30L)
                        val seconds = (customSeconds.toLongOrNull() ?: 0L).coerceIn(0L, 59L)
                        val total = (minutes * 60L + seconds).coerceIn(1L, 1800L)
                        val name = customLabel.trim().ifBlank { "Custom timer" }
                        QuickActionStore.save(
                            context,
                            slot,
                            QuickAction(
                                id = "custom_slot_" + slot + "_" + total,
                                label = name,
                                icon = osrsWikiImage("Watch.png"),
                                seconds = total
                            )
                        )
                        onSaved()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = QuickGold,
                        contentColor = Color.Black
                    )
                ) {
                    Text("Save Custom Timer to Slot " + slot)
                }
            }
        }
    }
}
