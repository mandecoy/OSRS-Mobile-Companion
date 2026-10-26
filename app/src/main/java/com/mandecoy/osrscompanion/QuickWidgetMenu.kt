package com.mandecoy.osrscompanion

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController

private val QStone = Color(0xff2e2924)
private val QStoneLight = Color(0xff403931)
private val QGold = Color(0xffd7a93f)
private val QGoldDark = Color(0xff8b6722)
private val QParchment = Color(0xffd7c7a4)
private val QText = Color(0xfff4ead4)

data class WidgetOption(
    val id: String,
    val label: String,
    val iconFile: String,
    val route: String? = null,
    val seconds: Long? = null,
    val customTimer: Boolean = false
)

private val TimerWidgetOptions = listOf(
    WidgetOption("custom", "Custom Timer", "Watch.png", customTimer = true),
    WidgetOption("timers", "Open Timers", "Watch.png", route = "timers"),
    WidgetOption("reminder_nmz", "NMZ Herb Boxes", "Herb_box.png", route = "reminder/nmz")
)

private val ReminderWidgetOptions = listOf(
    WidgetOption("reminders", "Open Reminders", "Watch.png", route = "timers"),
    WidgetOption("reminder_tears", "Tears of Guthix (weekly)", "Tears_of_Guthix.png", route = "reminder/tears"),
    WidgetOption("reminder_zaff", "Zaff battlestaves (daily)", "Battlestaff.png", route = "reminder/zaff"),
    WidgetOption("reminder_misc", "Manage Miscellania (daily)", "Coffer.png", route = "reminder/miscellania"),
    WidgetOption("reminder_nmz", "NMZ Herb Boxes", "Herb_box.png", route = "reminder/nmz")
)

private val FarmWidgetOptions = listOf(
    WidgetOption("herb", "Herb Run", "Ranarr_weed.png", seconds = 80 * 60),
    WidgetOption("bird", "Birdhouse Run", "Bird_house.png", seconds = 50 * 60),
    WidgetOption("coral", "Coral", "Elkhorn_coral.png", seconds = 160 * 60),
    WidgetOption("allotments", "Allotments", "Watermelon.png", seconds = 40 * 60),
    WidgetOption("hops", "Hops", "Jute_fibre.png", seconds = 40 * 60),
    WidgetOption("bushes", "Bushes", "White_berries.png", seconds = 100 * 60),
    WidgetOption("oak", "Oak Tree", "Oak_tree_(grown).png", seconds = 200 * 60),
    WidgetOption("willow", "Willow Tree", "Willow_tree_(grown).png", seconds = 240 * 60),
    WidgetOption("maple", "Maple Tree", "Maple_Tree_(grown).png", seconds = 320 * 60),
    WidgetOption("yew", "Yew Tree", "Yew_tree_(grown).png", seconds = 400 * 60),
    WidgetOption("magic", "Magic Tree", "Magic_tree_(grown).png", seconds = 480 * 60),
    WidgetOption("fruit", "Fruit Tree", "Palm_tree_(grown).png", seconds = 960 * 60),
    WidgetOption("teak", "Teak Tree", "Teak_tree_(grown).png", seconds = 4480 * 60),
    WidgetOption("mahogany", "Mahogany Tree", "Mahogany_tree_(grown).png", seconds = 5120 * 60),
    WidgetOption("cactus", "Cactus", "Cactus_(grown).png", seconds = 560 * 60),
    WidgetOption("calquat", "Calquat", "Calquat_tree_(grown).png", seconds = 1280 * 60)
)

private val ToolWidgetOptions = listOf(
    WidgetOption("floating", "Floating Companion", "Magical_orb.png", route = "floating"),
    WidgetOption("ge_tools", "GE Tools", "Grand_Exchange_Clerk.png", route = "ge_tools"),
    WidgetOption("high_alch", "High Alch", "High_Level_Alchemy.png", route = "high_alch"),
    WidgetOption("portfolio", "Portfolio", "Platinum_token.png", route = "portfolio"),
    WidgetOption("fairy_rings", "Fairy Rings", "Fairy_ring.png", route = "fairy_rings"),
    WidgetOption("notepad", "Notepad", "Spell_scroll.png", route = "notepad"),
    WidgetOption("puzzle", "Puzzle Box", "Puzzle_box_zulrah.png", route = "puzzle"),
    WidgetOption("guides", "Guides", "Cake_of_guidance.png", route = "guides"),
    WidgetOption("light", "Light Box", "Light_box.png", route = "lightbox"),
    WidgetOption("prices", "Grand Exchange Prices", "Coins_10000.png", route = "web/prices"),
    WidgetOption("wiki", "OSRS Wiki", "Old School RuneScape Wiki logo (alternate).png", route = "web/wiki"),
    WidgetOption("wise", "Wise Old Man", "Wise_Old_Man.png", route = "web/wise"),
    WidgetOption("stars", "Shooting Stars", "Shooting_Star_Display.png", route = "web/stars"),
    WidgetOption("skills", "Skill Calculators", "Max_cape.png", route = "web/skills"),
    WidgetOption("dps", "DPS Calculator", "Dragon_scimitar.png", route = "web/dps"),
    WidgetOption("quests", "Quests", "Quest_point_cape.png", route = "web/quests"),
    WidgetOption("diaries", "Achievement Diaries", "Achievement_diary_cape.png", route = "web/diaries"),
    WidgetOption("clues", "Clue Helper", "Clue_scroll_(hard).png", route = "web/clues"),
    WidgetOption("news", "Old School News", "Newspaper.png", route = "web/news")
)

@Composable
fun QuickWidgetMenuScreen(nav: NavHostController) {
    val context = LocalContext.current
    var revision by remember { mutableIntStateOf(0) }
    val slots = remember(revision) { QuickActionStore.load(context) }

    Screen(nav, "Edit Quick Widgets") { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    "Each slot is independent. Choose Timers (custom timers, Farming, Dailies/Weeklies) or Tools, then pin the shortcut you want.",
                    color = QParchment
                )
            }
            for (slot in 1..4) {
                item(key = "slot_" + slot) {
                    QuickSlotCard(
                        slot = slot,
                        action = slots[slot - 1],
                        onChanged = { revision++ }
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickSlotCard(
    slot: Int,
    action: QuickAction,
    onChanged: () -> Unit
) {
    val context = LocalContext.current
    var menuOpen by remember { mutableStateOf(false) }
    var menuPage by remember { mutableStateOf("root") }
    var customMode by remember(action.id) { mutableStateOf(false) }
    var customName by remember(action.id) { mutableStateOf("Custom timer") }
    var minutes by remember(action.id) { mutableStateOf("5") }
    var seconds by remember(action.id) { mutableStateOf("0") }

    fun saveOption(option: WidgetOption) {
        if (option.customTimer) {
            customMode = true
            menuOpen = false
            menuPage = "root"
            return
        }
        customMode = false
        QuickActionStore.save(
            context,
            slot,
            QuickAction(
                id = option.id,
                label = option.label,
                icon = osrsWikiImage(option.iconFile),
                route = option.route,
                seconds = option.seconds
            )
        )
        menuOpen = false
        menuPage = "root"
        onChanged()
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = QStone),
        border = BorderStroke(1.dp, QGoldDark)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                WikiIcon(iconUrlForAction(action), action.label, 44.dp)
                Column {
                    Text("SLOT " + slot, color = QGold, fontWeight = FontWeight.Bold)
                    Text(action.label, color = QText, fontSize = 13.sp)
                }
            }

            Column {
                OutlinedButton(
                    onClick = {
                        menuPage = "root"
                        menuOpen = true
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Choose widget", color = QText)
                }

                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = {
                        menuOpen = false
                        menuPage = "root"
                    },
                    modifier = Modifier
                        .widthIn(min = 285.dp, max = 350.dp)
                        .heightIn(max = 470.dp)
                        .background(QStone, RoundedCornerShape(8.dp)),
                    containerColor = QStone
                ) {
                    when (menuPage) {
                        "root" -> {
                            CategoryMenuItem("Timers", "Watch.png") { menuPage = "timer_categories" }
                            CategoryMenuItem("Tools", "Wrench.png") { menuPage = "tools" }
                        }
                        "timer_categories" -> {
                            BackMenuItem { menuPage = "root" }
                            CategoryMenuItem("Timers / AFK", "Watch.png") { menuPage = "timers" }
                            CategoryMenuItem("Farming", "Tool_leprechaun.png") { menuPage = "farm" }
                            CategoryMenuItem("Dailies / Weeklies", "Tears_of_Guthix.png") { menuPage = "reminders" }
                        }
                        "timers" -> {
                            BackMenuItem { menuPage = "timer_categories" }
                            TimerWidgetOptions.forEach { option ->
                                OptionMenuItem(option) { saveOption(option) }
                            }
                        }
                        "farm" -> {
                            BackMenuItem { menuPage = "timer_categories" }
                            FarmWidgetOptions.forEach { option ->
                                OptionMenuItem(option) { saveOption(option) }
                            }
                        }
                        "reminders" -> {
                            BackMenuItem { menuPage = "timer_categories" }
                            ReminderWidgetOptions.forEach { option ->
                                OptionMenuItem(option) { saveOption(option) }
                            }
                        }
                        "tools" -> {
                            BackMenuItem { menuPage = "root" }
                            ToolWidgetOptions.forEach { option ->
                                OptionMenuItem(option) { saveOption(option) }
                            }
                        }
                    }
                }
            }

            if (customMode) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = QStoneLight),
                    border = BorderStroke(1.dp, QGoldDark)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("CUSTOM TIMER", color = QGold, fontWeight = FontWeight.Bold)
                        OutlinedTextField(
                            value = customName,
                            onValueChange = { customName = it.take(20) },
                            label = { Text("Timer name", color = QParchment) },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = minutes,
                                onValueChange = { minutes = it.filter(Char::isDigit).take(2) },
                                label = { Text("Minutes", color = QParchment) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = seconds,
                                onValueChange = { seconds = it.filter(Char::isDigit).take(2) },
                                label = { Text("Seconds", color = QParchment) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Button(
                            onClick = {
                                val min = (minutes.toLongOrNull() ?: 0L).coerceIn(0, 30)
                                val sec = (seconds.toLongOrNull() ?: 0L).coerceIn(0, 59)
                                val total = (min * 60 + sec).coerceIn(1, 1800)
                                val name = customName.trim().ifBlank { "Custom timer" }
                                QuickActionStore.save(
                                    context,
                                    slot,
                                    QuickAction(
                                        id = "custom_" + slot + "_" + total,
                                        label = name,
                                        icon = osrsWikiImage("Watch.png"),
                                        seconds = total
                                    )
                                )
                                customMode = false
                                onChanged()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = QGold,
                                contentColor = Color.Black
                            )
                        ) {
                            Text("Save to Slot " + slot)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryMenuItem(label: String, iconFile: String, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label + "  ›", color = QGold, fontWeight = FontWeight.Bold) },
        leadingIcon = { WikiIcon(osrsWikiImage(iconFile), label, 28.dp) },
        onClick = onClick
    )
}

@Composable
private fun BackMenuItem(onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text("‹ Back", color = QGold, fontWeight = FontWeight.Bold) },
        onClick = onClick
    )
}

@Composable
private fun OptionMenuItem(option: WidgetOption, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(option.label, color = QText) },
        leadingIcon = { WikiIcon(osrsWikiImage(option.iconFile), option.label, 28.dp) },
        onClick = onClick
    )
}
