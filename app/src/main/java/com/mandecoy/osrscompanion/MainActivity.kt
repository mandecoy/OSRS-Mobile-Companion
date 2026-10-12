package com.mandecoy.osrscompanion

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.delay
import java.util.concurrent.TimeUnit
import kotlin.math.max

private val StoneDark = Color(0xff201c18)
private val Stone = Color(0xff4b443c)
private val StoneLight = Color(0xff70665a)
private val Parchment = Color(0xffd7c7a4)
private val RuneGold = Color(0xffd7a93f)
private val RuneGoldDark = Color(0xff8b6722)
private val TextLight = Color(0xfff4ead4)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        TimerNotifications.ensureChannel(this)
        setContent { CompanionApp() }

        if (
            Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
        }
    }
}

data class WebTool(val title: String, val url: String, val subtitle: String)

private val WebTools = mapOf(
    "wiki" to WebTool("OSRS Wiki", "https://oldschool.runescape.wiki/", "Search the OSRS Wiki inside the app."),
    "prices" to WebTool("Grand Exchange Prices", "https://prices.runescape.wiki/osrs/", "Live RuneScape Wiki market tools."),
    "wise" to WebTool("Wise Old Man", "https://wiseoldman.net/", "Player lookup, XP and account tracking."),
    "stars" to WebTool("Shooting Stars", "https://osrsportal.com/shooting-stars-tracker", "OSRS Portal shooting-star tracker."),
    "skills" to WebTool("Skill Calculators", "https://oldschool.tools/calculators/skill", "Pick a skill, look up a player, and plan XP."),
    "dps" to WebTool("DPS Calculator", "https://tools.runescape.wiki/osrs-dps/", "Compare gear, weapons, attack styles, and damage output."),
    "puzzle" to WebTool("Puzzle Box Solver", "https://osrs-puzzle-box-solver.vercel.app/", "Upload your puzzle-box screenshot and follow the moves in groups of five."),
    "light" to WebTool("Light Box Solver", "https://osrs-lightbox-solver.fletom.com/", "Solve master-clue light boxes."),
    "quests" to WebTool("Quests", "https://oldschool.runescape.wiki/w/Quests", "Quest list, requirements, and guides."),\n    "diaries" to WebTool("Achievement Diaries", "https://oldschool.runescape.wiki/w/Achievement_Diary", "Achievement Diary requirements and rewards."),
    "clues" to WebTool("Clue Helper", "https://oldschool.runescape.wiki/w/Treasure_Trails/Guide", "Clue scroll reference hub.")
)

data class Tool(val title: String, val subtitle: String, val route: String, val icon: String)

private val Tools = listOf(
    Tool("Timers", "AFK + farming runs", "timers", "⏱️"),
    Tool("Puzzle Box", "Screenshot solver", "web/puzzle", "🧩"),
    Tool("Light Box", "Master clue solver", "web/light", "💡"),
    Tool("GE Prices", "Live market prices", "web/prices", "🪙"),
    Tool("OSRS Wiki", "Search anything", "web/wiki", "📖"),
    Tool("Wise Old Man", "Player lookup", "web/wise", "🧙"),
    Tool("Shooting Stars", "OSRS Portal tracker", "web/stars", "🌠"),
    Tool("Skill Calculators", "All skills", "web/skills", "📊"),
    Tool("DPS Calculator", "Combat damage calculator", "web/dps", "⚔️"),
    Tool("Quests", "Quest list + requirements", "web/quests", "📜"),\n    Tool("Achievement Diaries", "Diary tasks + rewards", "web/diaries", "🏆"),
    Tool("Clue Helper", "Treasure trail references", "web/clues", "🗺️"),
    Tool("Quick Widgets", "Customize four shortcuts", "quick", "⚙️"),
    Tool("About", "Source + privacy", "about", "ℹ️")
)

data class QuickAction(
    val id: String,
    val label: String,
    val icon: String,
    val route: String? = null,
    val seconds: Long? = null
)

private val QuickChoices = listOf(
    QuickAction("afk5", "AFK 5m", "💤", seconds = 300),
    QuickAction("afk10", "AFK 10m", "💤", seconds = 600),
    QuickAction("herb", "Herb", "🌿", seconds = 80 * 60),
    QuickAction("bird", "Birdhouse", "🐦", seconds = 50 * 60),
    QuickAction("puzzle", "Puzzle", "🧩", route = "web/puzzle"),
    QuickAction("stars", "Stars", "🌠", route = "web/stars"),
    QuickAction("wiki", "Wiki", "📖", route = "web/wiki"),
    QuickAction("dps", "DPS", "⚔️", route = "web/dps"),
    QuickAction("timers", "Timers", "⏱️", route = "timers")
)

object QuickActionStore {
    private const val PREFS = "quick_actions_v2"

    private val defaults = listOf(
        QuickChoices.first { it.id == "afk5" },
        QuickChoices.first { it.id == "herb" },
        QuickChoices.first { it.id == "puzzle" },
        QuickChoices.first { it.id == "stars" }
    )

    fun load(context: Context): List<QuickAction> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return (1..4).map { slot ->
            decode(prefs.getString("slot" + slot, null)) ?: defaults[slot - 1]
        }
    }

    fun save(context: Context, slot: Int, action: QuickAction) {
        if (slot !in 1..4) return
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString("slot" + slot, encode(action))
            .apply()
    }

    private fun encode(action: QuickAction): String {
        val safeLabel = action.label.replace("|", " ")
        return if (action.seconds != null) {
            "timer|" + safeLabel + "|" + action.seconds + "|" + action.icon
        } else {
            "route|" + safeLabel + "|" + (action.route ?: "timers") + "|" + action.icon
        }
    }

    private fun decode(raw: String?): QuickAction? {
        if (raw.isNullOrBlank()) return null
        val parts = raw.split("|")
        if (parts.size < 4) return null
        return when (parts[0]) {
            "timer" -> QuickAction(
                id = "custom_" + parts[1] + "_" + parts[2],
                label = parts[1],
                icon = parts[3],
                seconds = parts[2].toLongOrNull()
            )
            "route" -> QuickAction(
                id = "route_" + parts[2],
                label = parts[1],
                icon = parts[3],
                route = parts[2]
            )
            else -> null
        }
    }
}

@Composable
private fun CompanionApp() {
    val nav = rememberNavController()
    MaterialTheme(
        colorScheme = MaterialTheme.colorScheme.copy(
            primary = RuneGold,
            background = StoneDark,
            surface = Stone,
            onPrimary = Color.Black,
            onBackground = TextLight,
            onSurface = TextLight
        ),
        typography = MaterialTheme.typography.copy(
            bodyLarge = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Serif),
            bodyMedium = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Serif)
        )
    ) {
        NavHost(navController = nav, startDestination = "home") {
            composable("home") { HomeScreen(nav) }
            composable("timers") { TimersScreen(nav) }
            composable("quick") { QuickActionsScreen(nav) }
            composable("about") { AboutScreen(nav) }
            composable("web/{key}") { entry ->
                val key = entry.arguments?.getString("key") ?: "wiki"
                val tool = WebTools[key] ?: WebTools.getValue("wiki")
                if (key == "puzzle") {
                    PuzzleBrowserScreen(nav, tool)
                } else {
                    WebToolScreen(nav, tool)
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(nav: NavHostController) {
    val context = LocalContext.current
    var revision by remember { mutableIntStateOf(0) }
    val quick = remember(revision) { QuickActionStore.load(context) }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize().background(StoneDark),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item(span = { GridItemSpan(2) }) {
            Column(
                Modifier.fillMaxWidth().padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "OSRS Companion Tool",
                    color = RuneGold,
                    fontSize = 27.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Serif
                )
                Text("Free • open source • mobile first", color = Parchment)
            }
        }

        item(span = { GridItemSpan(2) }) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("QUICK WIDGETS", color = RuneGold, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton(onClick = { nav.navigate("quick") }) {
                    Text("Edit", color = Parchment)
                }
            }
        }

        item(span = { GridItemSpan(2) }) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                quick.forEach { action ->
                    QuickMiniCard(
                        action = action,
                        context = context,
                        nav = nav,
                        modifier = Modifier.weight(1f)
                    ) { revision++ }
                }
            }
        }

        item(span = { GridItemSpan(2) }) {
            Text("TOOLS", color = RuneGold, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
        }

        items(Tools) { tool ->
            StoneButton(tool) { nav.navigate(tool.route) }
        }
    }
}

@Composable
private fun StoneButton(tool: Tool, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .height(116.dp)
            .border(2.dp, RuneGoldDark, RoundedCornerShape(8.dp))
            .background(
                Brush.verticalGradient(listOf(StoneLight, Stone, Color(0xff38322c))),
                RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(tool.icon, fontSize = 28.sp)
        Spacer(Modifier.height(4.dp))
        Text(tool.title, color = TextLight, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(3.dp))
        Text(tool.subtitle, color = Parchment, fontSize = 11.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun QuickMiniCard(
    action: QuickAction,
    context: Context,
    nav: NavHostController,
    modifier: Modifier = Modifier,
    onChange: () -> Unit
) {
    Card(
        modifier = modifier.height(76.dp).clickable {
            if (action.seconds != null) {
                val timer = TimerEntry.create(action.label, action.seconds)
                TimerStore.add(context, timer)
                TimerScheduler.schedule(context, timer)
            } else {
                nav.navigate(action.route ?: "timers")
            }
            onChange()
        },
        colors = CardDefaults.cardColors(containerColor = Color(0xff2e2924)),
        border = BorderStroke(1.dp, RuneGold)
    ) {
        Column(
            Modifier.fillMaxSize().padding(4.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(action.icon, fontSize = 20.sp)
            Text(
                action.label,
                color = RuneGold,
                fontWeight = FontWeight.Bold,
                fontSize = 9.sp,
                textAlign = TextAlign.Center,
                maxLines = 2
            )
        }
    }
}

data class FarmPreset(val label: String, val seconds: Long, val icon: String)

private val FarmPresets = listOf(
    FarmPreset("Herb run", 80 * 60, "🌿"),
    FarmPreset("Birdhouse run", 50 * 60, "🐦"),
    FarmPreset("Allotments", 40 * 60, "🌱"),
    FarmPreset("Hops", 40 * 60, "🌱"),
    FarmPreset("Bushes", 100 * 60, "🌿"),
    FarmPreset("Oak tree", 200 * 60, "🌳"),
    FarmPreset("Willow tree", 240 * 60, "🌳"),
    FarmPreset("Maple tree", 320 * 60, "🌳"),
    FarmPreset("Yew tree", 400 * 60, "🌳"),
    FarmPreset("Magic tree", 480 * 60, "🌳"),
    FarmPreset("Fruit tree", 960 * 60, "🍎"),
    FarmPreset("Teak tree", 4480 * 60, "🌳"),
    FarmPreset("Mahogany tree", 5120 * 60, "🌳"),
    FarmPreset("Cactus", 560 * 60, "🌵"),
    FarmPreset("Calquat", 1280 * 60, "🌳")
)

@Composable
private fun TimersScreen(nav: NavHostController) {
    val context = LocalContext.current
    var label by remember { mutableStateOf("AFK check") }
    var minutesInput by remember { mutableStateOf("5") }
    var secondsInput by remember { mutableStateOf("0") }
    var refresh by remember { mutableIntStateOf(0) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val active = remember(refresh, now / 1000) { TimerStore.active(context) }

    fun selectedSeconds(): Long {
        val minutes = (minutesInput.toLongOrNull() ?: 0L).coerceIn(0L, 30L)
        val seconds = (secondsInput.toLongOrNull() ?: 0L).coerceIn(0L, 59L)
        return (minutes * 60L + seconds).coerceIn(1L, 1800L)
    }

    fun pinCustom(slot: Int) {
        val total = selectedSeconds()
        val safeLabel = label.trim().ifBlank { "AFK " + formatSeconds(total) }
        QuickActionStore.save(
            context,
            slot,
            QuickAction(
                id = "custom_" + System.currentTimeMillis(),
                label = safeLabel,
                icon = "💤",
                seconds = total
            )
        )
    }

    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }

    Screen(nav, "Timers") { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item { ExactAlarmCard(context) }

            item {
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xff2e2924))) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("💤  CUSTOM / AFK TIMER", color = RuneGold, fontWeight = FontWeight.Bold)
                        Text(
                            "Choose minutes and seconds. The timer can notify even after you close the app.",
                            color = Parchment
                        )

                        OutlinedTextField(
                            value = label,
                            onValueChange = { label = it.take(24) },
                            label = { Text("Timer name") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedTextField(
                                value = minutesInput,
                                onValueChange = { minutesInput = it.filter(Char::isDigit).take(2) },
                                label = { Text("Minutes") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = secondsInput,
                                onValueChange = { secondsInput = it.filter(Char::isDigit).take(2) },
                                label = { Text("Seconds") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Text("Maximum: 30 minutes", color = StoneLight, fontSize = 11.sp)

                        Button(
                            onClick = {
                                val total = selectedSeconds()
                                val safeLabel = label.trim().ifBlank { "AFK check" }
                                val timer = TimerEntry.create(safeLabel, total)
                                TimerStore.add(context, timer)
                                TimerScheduler.schedule(context, timer)
                                refresh++
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = RuneGold, contentColor = Color.Black)
                        ) {
                            Text("Start " + formatSeconds(selectedSeconds()) + " timer")
                        }

                        Text("PIN THIS EXACT TIMER TO A QUICK WIDGET", color = Parchment, fontSize = 11.sp)
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            (1..4).forEach { slot ->
                                OutlinedButton(
                                    onClick = { pinCustom(slot) },
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp)
                                ) {
                                    Text("Slot " + slot, fontSize = 10.sp)
                                }
                            }
                        }
                    }
                }
            }

            item { Text("ACTIVE TIMERS", color = RuneGold, fontWeight = FontWeight.Bold) }
            if (active.isEmpty()) {
                item { Text("No active timers.", color = StoneLight) }
            } else {
                items(active.size) { index ->
                    val timer = active[index]
                    val left = max(0L, (timer.endAtMillis - now + 999L) / 1000L)
                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xff2e2924))) {
                        Row(
                            Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("💤 " + timer.label, color = TextLight, fontWeight = FontWeight.Bold)
                                Text(formatSeconds(left), color = RuneGold)
                            }
                            TextButton(onClick = {
                                TimerScheduler.cancel(context, timer)
                                TimerStore.remove(context, timer.id)
                                refresh++
                            }) { Text("Cancel") }
                        }
                    }
                }
            }

            item { Text("FARM & RUN PRESETS", color = RuneGold, fontWeight = FontWeight.Bold) }
            items(FarmPresets.size) { index ->
                val preset = FarmPresets[index]
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xff2e2924))) {
                    Row(
                        Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(preset.icon + " " + preset.label, color = TextLight, fontWeight = FontWeight.Bold)
                            Text(formatSeconds(preset.seconds), color = Parchment, fontSize = 12.sp)
                        }
                        Button(
                            onClick = {
                                val timer = TimerEntry.create(preset.label, preset.seconds)
                                TimerStore.add(context, timer)
                                TimerScheduler.schedule(context, timer)
                                refresh++
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = RuneGold, contentColor = Color.Black)
                        ) { Text("Start") }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExactAlarmCard(context: Context) {
    if (Build.VERSION.SDK_INT < 31) return
    val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    if (alarm.canScheduleExactAlarms()) return

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xff4a302a)),
        border = BorderStroke(1.dp, RuneGoldDark)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Enable precise alarms", color = RuneGold, fontWeight = FontWeight.Bold)
            Text("Android may delay short timers unless precise alarm access is enabled.", color = Parchment, fontSize = 12.sp)
            OutlinedButton(onClick = {
                val intent = Intent(
                    Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                    Uri.parse("package:" + context.packageName)
                )
                context.startActivity(intent)
            }) { Text("Open setting") }
        }
    }
}

@Composable
private fun QuickActionsScreen(nav: NavHostController) {
    val context = LocalContext.current
    var current by remember { mutableStateOf(QuickActionStore.load(context)) }
    var customLabel by remember { mutableStateOf("Custom timer") }
    var customMinutes by remember { mutableStateOf("5") }
    var customSeconds by remember { mutableStateOf("0") }

    fun customTimerAction(): QuickAction {
        val minutes = (customMinutes.toLongOrNull() ?: 0L).coerceIn(0L, 30L)
        val seconds = (customSeconds.toLongOrNull() ?: 0L).coerceIn(0L, 59L)
        val total = (minutes * 60L + seconds).coerceIn(1L, 1800L)
        val name = customLabel.trim().ifBlank { "Timer " + formatSeconds(total) }
        return QuickAction(
            id = "quick_custom_" + total + "_" + name,
            label = name,
            icon = "💤",
            seconds = total
        )
    }

    Screen(nav, "Edit Quick Widgets") { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    "Choose four tools or timers for the top row.",
                    color = Parchment
                )
            }

            item {
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xff2e2924))) {
                    Column(
                        Modifier.fillMaxWidth().padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("💤 CUSTOM TIMER WIDGET", color = RuneGold, fontWeight = FontWeight.Bold)
                        Text(
                            "Set the exact timer you want, then pin it to one of the four quick-widget slots.",
                            color = Parchment,
                            fontSize = 12.sp
                        )
                        OutlinedTextField(
                            value = customLabel,
                            onValueChange = { customLabel = it.take(20) },
                            label = { Text("Widget name") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(
                            Modifier.fillMaxWidth(),
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
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            (1..4).forEach { slot ->
                                Button(
                                    onClick = {
                                        QuickActionStore.save(context, slot, customTimerAction())
                                        current = QuickActionStore.load(context)
                                    },
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = RuneGold,
                                        contentColor = Color.Black
                                    )
                                ) {
                                    Text("Slot " + slot, fontSize = 10.sp)
                                }
                            }
                        }
                    }
                }
            }

            items(4) { index ->
                val slot = index + 1
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xff2e2924))) {
                    Column(
                        Modifier.fillMaxWidth().padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            "SLOT " + slot + " — " + current[index].icon + " " + current[index].label,
                            color = RuneGold,
                            fontWeight = FontWeight.Bold
                        )

                        QuickChoices.chunked(3).forEach { row ->
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                row.forEach { action ->
                                    OutlinedButton(
                                        onClick = {
                                            QuickActionStore.save(context, slot, action)
                                            current = QuickActionStore.load(context)
                                        },
                                        modifier = Modifier.weight(1f),
                                        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 3.dp)
                                    ) {
                                        Text(action.icon + " " + action.label, fontSize = 9.sp, maxLines = 1)
                                    }
                                }
                                repeat(3 - row.size) {
                                    Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PuzzleBrowserScreen(nav: NavHostController, tool: WebTool) {
    val context = LocalContext.current
    var launched by remember { mutableStateOf(false) }

    fun openSolver() {
        val uri = Uri.parse(tool.url)
        try {
            CustomTabsIntent.Builder()
                .setShowTitle(true)
                .build()
                .launchUrl(context, uri)
        } catch (_: Exception) {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri))
        }
    }

    LaunchedEffect(Unit) {
        if (!launched) {
            launched = true
            openSolver()
        }
    }

    Screen(nav, "Puzzle Box Solver") { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Upload a screenshot of your Puzzle Box. The solver opens in a browser tab so its puzzle image and arrows render correctly on Android.",
                color = Parchment
            )
            Button(
                onClick = { openSolver() },
                colors = ButtonDefaults.buttonColors(containerColor = RuneGold, contentColor = Color.Black)
            ) {
                Text("Open Puzzle Box Solver")
            }
            Text(
                "Choose 5 steps, make those five moves in OSRS, then return for the next five.",
                color = TextLight,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun WebToolScreen(nav: NavHostController, tool: WebTool) {
    var uploadCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uploadCallback?.onReceiveValue(uri?.let { arrayOf(it) })
        uploadCallback = null
    }

    Screen(nav, tool.title) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Text(
                tool.subtitle,
                color = Parchment,
                fontSize = 12.sp,
                modifier = Modifier.fillMaxWidth().background(Color(0xff2e2924)).padding(8.dp)
            )
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    WebView(context).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.allowContentAccess = true
                        settings.setSupportZoom(true)
                        webViewClient = WebViewClient()
                        webChromeClient = object : WebChromeClient() {
                            override fun onShowFileChooser(
                                webView: WebView?,
                                filePathCallback: ValueCallback<Array<Uri>>?,
                                fileChooserParams: FileChooserParams?
                            ): Boolean {
                                uploadCallback?.onReceiveValue(null)
                                uploadCallback = filePathCallback
                                picker.launch("image/*")
                                return true
                            }
                        }
                        loadUrl(tool.url)
                    }
                }
            )
        }
    }
}

@Composable
private fun AboutScreen(nav: NavHostController) {
    Screen(nav, "About") { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("OSRS Companion Tool", color = RuneGold, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text("Version 0.2.2", color = Parchment)
            Text("Free and open source. Timer and quick-widget data stays on your device.", color = TextLight)
            Text("github.com/mandecoy/OSRS-Mobile-Companion", color = RuneGold)
            Text("Web tools are third-party services and remain subject to their own policies and availability.", color = Parchment, fontSize = 12.sp)
            Text("Not affiliated with, endorsed by, or sponsored by Jagex Ltd.", color = StoneLight, fontSize = 12.sp)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Screen(
    nav: NavHostController,
    title: String,
    content: @Composable (PaddingValues) -> Unit
) {
    Scaffold(
        containerColor = StoneDark,
        topBar = {
            TopAppBar(
                title = { Text(title, color = RuneGold, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    TextButton(onClick = { nav.popBackStack() }) {
                        Text("‹ Back", color = TextLight)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xff171411))
            )
        },
        content = content
    )
}

private fun formatSeconds(total: Long): String {
    val days = TimeUnit.SECONDS.toDays(total)
    val hours = TimeUnit.SECONDS.toHours(total) % 24
    val minutes = TimeUnit.SECONDS.toMinutes(total) % 60
    val seconds = total % 60

    return buildString {
        if (days > 0) append(days).append("d ")
        if (hours > 0 || days > 0) append(hours).append("h ")
        if (minutes > 0 || hours > 0 || days > 0) append(minutes).append("m ")
        append(seconds).append("s")
    }.trim()
}
