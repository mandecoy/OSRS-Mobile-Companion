package com.mandecoy.osrscompanion

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController

private val QStone = Color(0xff2e2924)
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

val AllWidgetOptions = listOf(
    WidgetOption("custom", "Custom Timer", "Sleeping_cap.png", customTimer = true),
    WidgetOption("afk5", "AFK 5 Minutes", "Sleeping_cap.png", seconds = 300),
    WidgetOption("afk10", "AFK 10 Minutes", "Sleeping_cap.png", seconds = 600),
    WidgetOption("herb", "Herb Run", "Ranarr_weed.png", seconds = 80 * 60),
    WidgetOption("bird", "Birdhouse Run", "Bird_house.png", seconds = 50 * 60),
    WidgetOption("timers", "Timers", "Watch.png", route = "timers"),
    WidgetOption("puzzle", "Puzzle Box", "Puzzle_box.png", route = "puzzle"),
    WidgetOption("light", "Light Box", "Light_box.png", route = "web/light"),
    WidgetOption("prices", "Grand Exchange Prices", "Coins_10000.png", route = "web/prices"),
    WidgetOption("wiki", "OSRS Wiki", "Book.png", route = "web/wiki"),
    WidgetOption("wise", "Wise Old Man", "Wise_Old_Man.png", route = "web/wise"),
    WidgetOption("stars", "Shooting Stars", "Shooting_Star.png", route = "web/stars"),
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
                    "Each slot is separate. Pick any tool or timer from its menu.",
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
    var customMode by remember(action.id) { mutableStateOf(false) }
    var customName by remember(action.id) { mutableStateOf("Custom timer") }
    var minutes by remember(action.id) { mutableStateOf("5") }
    var seconds by remember(action.id) { mutableStateOf("0") }

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
                    onClick = { menuOpen = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Choose widget")
                }
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false }
                ) {
                    AllWidgetOptions.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.label) },
                            leadingIcon = {
                                WikiIcon(osrsWikiImage(option.iconFile), option.label, 28.dp)
                            },
                            onClick = {
                                menuOpen = false
                                if (option.customTimer) {
                                    customMode = true
                                } else {
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
                                    onChanged()
                                }
                            }
                        )
                    }
                }
            }

            if (customMode) {
                OutlinedTextField(
                    value = customName,
                    onValueChange = { customName = it.take(20) },
                    label = { Text("Timer name") },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = minutes,
                        onValueChange = { minutes = it.filter(Char::isDigit).take(2) },
                        label = { Text("Minutes") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = seconds,
                        onValueChange = { seconds = it.filter(Char::isDigit).take(2) },
                        label = { Text("Seconds") },
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
                                icon = osrsWikiImage("Sleeping_cap.png"),
                                seconds = total
                            )
                        )
                        customMode = false
                        onChanged()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = QGold, contentColor = Color.Black)
                ) {
                    Text("Save to Slot " + slot)
                }
            }
        }
    }
}
