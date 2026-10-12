package com.mandecoy.osrscompanion

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController

private val LocalStone = Color(0xff2e2924)
private val LocalGold = Color(0xffd7a93f)
private val LocalGoldDark = Color(0xff8b6722)
private val LocalParchment = Color(0xffd7c7a4)
private val LocalText = Color(0xfff4ead4)

object ToolOrderStore {
    private const val PREFS = "tool_layout_v1"
    private const val KEY = "route_order"

    val defaults = listOf(
        "timers", "floating", "ge_tools", "high_alch", "portfolio", "fairy_rings", "notepad",
        "puzzle", "lightbox", "web/prices", "web/wiki", "web/wise", "web/stars",
        "web/skills", "web/dps", "web/quests", "web/diaries", "web/clues", "web/news",
        "quick", "about"
    )

    private val titles = mapOf(
        "timers" to "Timers",
        "floating" to "Floating Companion",
        "ge_tools" to "GE Tools",
        "high_alch" to "High Alch",
        "portfolio" to "Portfolio",
        "fairy_rings" to "Fairy Rings",
        "notepad" to "Notepad",
        "puzzle" to "Puzzle Box",
        "lightbox" to "Light Box",
        "web/prices" to "Grand Exchange Prices",
        "web/wiki" to "OSRS Wiki",
        "web/wise" to "Wise Old Man",
        "web/stars" to "Shooting Stars",
        "web/skills" to "Skill Calculators",
        "web/dps" to "DPS Calculator",
        "web/quests" to "Quests",
        "web/diaries" to "Achievement Diaries",
        "web/clues" to "Clue Helper",
        "web/news" to "Old School News",
        "quick" to "Quick Widgets",
        "about" to "About"
    )

    fun title(route: String): String = titles[route] ?: route

    fun load(context: Context): List<String> {
        val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, null)
            ?.split("|")
            ?.filter { it.isNotBlank() }
            ?: emptyList()
        return (saved.filter { it in defaults } + defaults.filterNot { it in saved }).distinct()
    }

    fun ordered(context: Context, routes: List<String>): List<String> {
        val order = load(context)
        return routes.sortedBy { route ->
            val index = order.indexOf(route)
            if (index < 0) Int.MAX_VALUE else index
        }
    }

    fun move(context: Context, route: String, delta: Int) {
        val list = load(context).toMutableList()
        val index = list.indexOf(route)
        val target = (index + delta).coerceIn(0, list.lastIndex)
        if (index >= 0 && index != target) {
            val value = list.removeAt(index)
            list.add(target, value)
            save(context, list)
        }
    }

    fun reset(context: Context) = save(context, defaults)

    private fun save(context: Context, order: List<String>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, order.joinToString("|"))
            .apply()
    }
}

@Composable
fun LayoutEditorScreen(nav: NavHostController) {
    val context = LocalContext.current
    var revision by remember { mutableIntStateOf(0) }
    val order = remember(revision) { ToolOrderStore.load(context) }
    val quick = remember(revision) { QuickActionStore.load(context) }

    Screen(nav, "Reorder Widgets") { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text(
                    "Move tools into the order you want. Your layout is saved automatically on this device.",
                    color = LocalParchment
                )
            }
            item {
                Text("QUICK WIDGETS", color = LocalGold, fontWeight = FontWeight.Bold)
            }
            items(quick.size) { index ->
                val action = quick[index]
                Card(
                    colors = CardDefaults.cardColors(containerColor = LocalStone),
                    border = BorderStroke(1.dp, LocalGoldDark)
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Slot " + (index + 1), color = LocalGold, fontSize = 11.sp)
                            Text(action.label, color = LocalText, fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            onClick = {
                                if (index > 0) {
                                    QuickActionStore.swap(context, index + 1, index)
                                    revision++
                                }
                            },
                            enabled = index > 0
                        ) { Text("↑") }
                        OutlinedButton(
                            onClick = {
                                if (index < quick.lastIndex) {
                                    QuickActionStore.swap(context, index + 1, index + 2)
                                    revision++
                                }
                            },
                            enabled = index < quick.lastIndex
                        ) { Text("↓") }
                    }
                }
            }

            item {
                Text("MAIN TOOLS", color = LocalGold, fontWeight = FontWeight.Bold)
            }
            items(order, key = { it }) { route ->
                val index = order.indexOf(route)
                Card(
                    colors = CardDefaults.cardColors(containerColor = LocalStone),
                    border = BorderStroke(1.dp, LocalGoldDark)
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            ToolOrderStore.title(route),
                            color = LocalText,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedButton(
                            onClick = {
                                ToolOrderStore.move(context, route, -1)
                                revision++
                            },
                            enabled = index > 0
                        ) { Text("↑") }
                        OutlinedButton(
                            onClick = {
                                ToolOrderStore.move(context, route, 1)
                                revision++
                            },
                            enabled = index < order.lastIndex
                        ) { Text("↓") }
                    }
                }
            }

            item {
                Button(
                    onClick = {
                        ToolOrderStore.reset(context)
                        revision++
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = LocalGold,
                        contentColor = Color.Black
                    )
                ) {
                    Text("Reset default layout")
                }
            }
        }
    }
}

data class FairyRingEntry(val code: String, val location: String)

private val FairyRings = listOf(
    FairyRingEntry("AIQ", "Mudskipper Point"),
    FairyRingEntry("AIR", "Island south-east of Ardougne"),
    FairyRingEntry("AIS", "Auburn Valley"),
    FairyRingEntry("AJP", "Avium Savannah"),
    FairyRingEntry("AJQ", "Cave south of Dorgesh-Kaan"),
    FairyRingEntry("AJR", "Slayer cave"),
    FairyRingEntry("AJS", "Penguins near Miscellania"),
    FairyRingEntry("AKP", "Necropolis"),
    FairyRingEntry("AKQ", "Piscatoris Hunter area"),
    FairyRingEntry("AKR", "Hosidius Vinery"),
    FairyRingEntry("AKS", "Feldip Hunter area"),
    FairyRingEntry("ALP", "Island - Lighthouse"),
    FairyRingEntry("ALQ", "Haunted Woods east of Canifis"),
    FairyRingEntry("ALR", "Abyssal Area"),
    FairyRingEntry("ALS", "McGrubor's Wood"),
    FairyRingEntry("BIP", "Island south-west of Mort Myre"),
    FairyRingEntry("BIQ", "Kalphite Hive"),
    FairyRingEntry("BIS", "Ardougne Zoo - Unicorns"),
    FairyRingEntry("BJP", "Isle of Souls"),
    FairyRingEntry("BJR", "Realm of the Fisher King"),
    FairyRingEntry("BJS", "Near Zul-Andra"),
    FairyRingEntry("BKP", "South of Castle Wars"),
    FairyRingEntry("BKQ", "Enchanted Valley"),
    FairyRingEntry("BKR", "Mort Myre Swamp, south of Canifis"),
    FairyRingEntry("BKS", "Zanaris"),
    FairyRingEntry("BLP", "TzHaar area"),
    FairyRingEntry("BLQ", "Yu'biusk"),
    FairyRingEntry("BLR", "Legends' Guild"),
    FairyRingEntry("BLS", "South of Mount Quidamortem"),
    FairyRingEntry("CIP", "Miscellania"),
    FairyRingEntry("CIQ", "North-west of Yanille"),
    FairyRingEntry("CIR", "North-east of the Farming Guild"),
    FairyRingEntry("CIS", "North of the Arceuus Library"),
    FairyRingEntry("CJQ", "The Great Conch"),
    FairyRingEntry("CJR", "Sinclair Mansion"),
    FairyRingEntry("CKP", "Cosmic entity's plane"),
    FairyRingEntry("CKQ", "Aldarin"),
    FairyRingEntry("CKR", "South of Tai Bwo Wannai Village"),
    FairyRingEntry("CKS", "Canifis"),
    FairyRingEntry("CLP", "Island south of Draynor Village"),
    FairyRingEntry("CLR", "Ape Atoll"),
    FairyRingEntry("CLS", "Hazelmere's home"),
    FairyRingEntry("DIP", "Abyssal Nexus"),
    FairyRingEntry("DIQ", "Player-owned house"),
    FairyRingEntry("DIR", "Gorak's Plane"),
    FairyRingEntry("DIS", "Wizards' Tower"),
    FairyRingEntry("DJP", "Tower of Life"),
    FairyRingEntry("DJR", "Chasm of Fire"),
    FairyRingEntry("DKP", "South of Musa Point"),
    FairyRingEntry("DKR", "Edgeville / Grand Exchange"),
    FairyRingEntry("DKS", "Polar Hunter area"),
    FairyRingEntry("DLP", "Grimstone Dungeon"),
    FairyRingEntry("DLQ", "North of Nardah"),
    FairyRingEntry("DLR", "Poison Waste south of Isafdar"),
    FairyRingEntry("DLS", "Myreque hideout under The Hollows")
)

@Composable
fun FairyRingDirectoryScreen(nav: NavHostController) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(query) {
        val value = query.trim()
        if (value.isBlank()) FairyRings
        else FairyRings.filter {
            it.code.contains(value, ignoreCase = true) ||
                it.location.contains(value, ignoreCase = true)
        }
    }

    Screen(nav, "Fairy Ring Directory") { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it.take(40) },
                    label = { Text("Search code or location") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            items(filtered, key = { it.code }) { ring ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = LocalStone),
                    border = BorderStroke(1.dp, LocalGoldDark)
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            ring.code,
                            color = LocalGold,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                        Text(ring.location, color = LocalText, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
fun PersistentNotepadScreen(nav: NavHostController) {
    val context = LocalContext.current
    val prefs = remember {
        context.getSharedPreferences("persistent_notepad_v1", Context.MODE_PRIVATE)
    }
    var note by remember { mutableStateOf(prefs.getString("note", "") ?: "") }

    Screen(nav, "Persistent Notepad") { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                "Saved automatically on this device.",
                color = LocalParchment,
                fontSize = 12.sp
            )
            OutlinedTextField(
                value = note,
                onValueChange = {
                    note = it
                    prefs.edit().putString("note", it).apply()
                },
                label = { Text("Notes") },
                modifier = Modifier.fillMaxWidth().weight(1f)
            )
            TextButton(
                onClick = {
                    note = ""
                    prefs.edit().remove("note").apply()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Clear notes", color = LocalGold)
            }
        }
    }
}
