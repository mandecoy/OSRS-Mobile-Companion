package com.mandecoy.osrscompanion

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.BitmapFactory
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
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
        GameReminderScheduler.restore(this)
        PriceAlertStore.ensureScheduled(this)
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
    "quests" to WebTool("Quests", "https://oldschool.runescape.wiki/w/Quests", "Quest list, requirements, and guides."),
    "diaries" to WebTool("Achievement Diaries", "https://oldschool.runescape.wiki/w/Achievement_Diary", "Achievement Diary requirements and rewards."),
    "news" to WebTool("Old School News", "https://secure.runescape.com/m=news/archive?oldschool=1", "Official Old School RuneScape news archive."),
    "theoatrix_skills" to WebTool("Theoatrix Skill Guides", "https://www.theoatrix.net/skill-guides", "Skill levelling guides from Theoatrix."),
    "theoatrix_afk" to WebTool("Theoatrix AFK Methods", "https://www.theoatrix.net/osrs-guides/categories/most-afk-methods", "AFK methods and guides from Theoatrix."),
    "wiki_guides" to WebTool("OSRS Wiki Guides", "https://oldschool.runescape.wiki/w/Category:Guides", "RuneScape Wiki guides directory."),
    "clues" to WebTool("Clue Helper", "https://oldschool.runescape.wiki/w/Treasure_Trails/Guide", "Clue scroll reference hub.")
)

data class Tool(val title: String, val subtitle: String, val route: String, val icon: String)

private val Tools = listOf(
    Tool("Timers", "AFK + farming + reminders", "timers", osrsWikiImage("Watch.png")),
    Tool("Floating Companion", "Overlay + repeatable timer", "floating", osrsWikiImage("Magical_orb.png")),
    Tool("GE Tools", "Flips + market movers", "ge_tools", osrsWikiImage("Grand_Exchange_Clerk.png")),
    Tool("High Alch", "Live profitable alchs", "high_alch", osrsWikiImage("High_Level_Alchemy.png")),
    Tool("Portfolio", "Flips + watchlist + alerts", "portfolio", osrsWikiImage("Platinum_token.png")),
    Tool("Fairy Rings", "Code + location directory", "fairy_rings", osrsWikiImage("Fairy_ring.png")),
    Tool("Notepad", "Persistent local notes", "notepad", osrsWikiImage("Spell_scroll.png")),
    Tool("Puzzle Box", "Screenshot solver", "puzzle", osrsWikiImage("Puzzle_box_zulrah.png")),
    Tool("Light Box", "Master clue solver", "lightbox", osrsWikiImage("Light_box.png")),
    Tool("GE Prices", "RuneScape Wiki prices", "web/prices", osrsWikiImage("Coins_10000.png")),
    Tool("OSRS Wiki", "Search anything", "web/wiki", osrsWikiImage("Old School RuneScape Wiki logo (alternate).png")),
    Tool("Wise Old Man", "Player lookup", "web/wise", osrsWikiImage("Wise_Old_Man.png")),
    Tool("Shooting Stars", "OSRS Portal tracker", "web/stars", osrsWikiImage("Shooting_Star_Display.png")),
    Tool("Skill Calculators", "All skills", "web/skills", osrsWikiImage("Max_cape.png")),
    Tool("DPS Calculator", "Combat damage calculator", "web/dps", osrsWikiImage("Dragon_scimitar.png")),
    Tool("Quests", "Quest list + requirements", "web/quests", osrsWikiImage("Quest_point_cape.png")),
    Tool("Achievement Diaries", "Diary tasks + rewards", "web/diaries", osrsWikiImage("Achievement_diary_cape.png")),
    Tool("Clue Helper", "Treasure trail references", "web/clues", osrsWikiImage("Clue_scroll_(hard).png")),
    Tool("Guides", "Theoatrix + OSRS Wiki", "guides", osrsWikiImage("Book_of_knowledge.png")),
    Tool("Old School News", "Official Jagex news archive", "web/news", osrsWikiImage("Newspaper.png")),
    Tool("Quick Widgets", "Customize four shortcuts", "quick", osrsWikiImage("Wrench.png")),
    Tool("About", "Source + privacy", "about", osrsWikiImage("Book.png"))
)
data class QuickAction(
    val id: String,
    val label: String,
    val icon: String,
    val route: String? = null,
    val seconds: Long? = null
)

private val QuickChoices = listOf(
    QuickAction("timers", "Timers", osrsWikiImage("Watch.png"), route = "timers"),
    QuickAction("floating", "Floating Companion", osrsWikiImage("Magical_orb.png"), route = "floating"),
    QuickAction("ge_tools", "GE Tools", osrsWikiImage("Grand_Exchange_Clerk.png"), route = "ge_tools"),
    QuickAction("high_alch", "High Alch", osrsWikiImage("High_Level_Alchemy.png"), route = "high_alch"),
    QuickAction("portfolio", "Portfolio", osrsWikiImage("Platinum_token.png"), route = "portfolio"),
    QuickAction("fairy_rings", "Fairy Rings", osrsWikiImage("Fairy_ring.png"), route = "fairy_rings"),
    QuickAction("notepad", "Notepad", osrsWikiImage("Spell_scroll.png"), route = "notepad"),
    QuickAction("herb", "Herb Run", osrsWikiImage("Ranarr_weed.png"), seconds = 80 * 60),
    QuickAction("bird", "Birdhouse", osrsWikiImage("Bird_house.png"), seconds = 50 * 60),
    QuickAction("puzzle", "Puzzle Box", osrsWikiImage("Puzzle_box_zulrah.png"), route = "puzzle"),
    QuickAction("light", "Light Box", osrsWikiImage("Light_box.png"), route = "lightbox"),
    QuickAction("prices", "GE Prices", osrsWikiImage("Coins_10000.png"), route = "web/prices"),
    QuickAction("wiki", "OSRS Wiki", osrsWikiImage("Old School RuneScape Wiki logo (alternate).png"), route = "web/wiki"),
    QuickAction("wise", "Wise Old Man", osrsWikiImage("Wise_Old_Man.png"), route = "web/wise"),
    QuickAction("stars", "Shooting Stars", osrsWikiImage("Shooting_Star_Display.png"), route = "web/stars"),
    QuickAction("skills", "Skill Calculators", osrsWikiImage("Max_cape.png"), route = "web/skills"),
    QuickAction("dps", "DPS Calculator", osrsWikiImage("Dragon_scimitar.png"), route = "web/dps"),
    QuickAction("quests", "Quests", osrsWikiImage("Quest_point_cape.png"), route = "web/quests"),
    QuickAction("diaries", "Achievement Diaries", osrsWikiImage("Achievement_diary_cape.png"), route = "web/diaries"),
    QuickAction("clues", "Clue Helper", osrsWikiImage("Clue_scroll_(hard).png"), route = "web/clues"),
    QuickAction("guides", "Guides", osrsWikiImage("Book_of_knowledge.png"), route = "guides"),
    QuickAction("news", "Old School News", osrsWikiImage("Newspaper.png"), route = "web/news")
)
object QuickActionStore {
    private const val PREFS = "quick_actions_v2"

    private val defaults = listOf(
        QuickChoices.first { it.id == "timers" },
        QuickChoices.first { it.id == "herb" },
        QuickChoices.first { it.id == "puzzle" },
        QuickChoices.first { it.id == "stars" }
    )

    fun load(context: Context): List<QuickAction> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return (1..4).map { slot ->
            val decoded = decode(prefs.getString("slot" + slot, null))
            when (decoded?.label) {
                "AFK 5m", "AFK 5 Minutes", "AFK 10m", "AFK 10 Minutes" ->
                    QuickChoices.first { it.id == "timers" }
                else -> decoded ?: defaults[slot - 1]
            }
        }
    }

    fun save(context: Context, slot: Int, action: QuickAction) {
        if (slot !in 1..4) return
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString("slot" + slot, encode(action))
            .apply()
    }

    fun swap(context: Context, firstSlot: Int, secondSlot: Int) {
        if (firstSlot !in 1..4 || secondSlot !in 1..4 || firstSlot == secondSlot) return
        val current = load(context)
        val editor = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        editor.putString("slot" + firstSlot, encode(current[secondSlot - 1]))
        editor.putString("slot" + secondSlot, encode(current[firstSlot - 1]))
        editor.apply()
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
            composable("floating") { FloatingToolsScreen(nav) }
            composable("ge_tools") { GeToolsScreen(nav) }
            composable("high_alch") { HighAlchScreen(nav) }
            composable("portfolio") { PortfolioScreen(nav) }
            composable("fairy_rings") { FairyRingDirectoryScreen(nav) }
            composable("notepad") { PersistentNotepadScreen(nav) }
            composable("layout") { LayoutEditorScreen(nav) }
            composable("market_item/{id}") { entry ->
                val id = entry.arguments?.getString("id")?.toIntOrNull() ?: -1
                MarketItemDetailScreen(nav, id)
            }
            composable("quick") { QuickWidgetMenuScreen(nav) }
            composable("puzzle") { NativePuzzleScreen(nav) }
            composable("guides") { GuidesScreen(nav) }
            composable("lightbox") { NativeLightBoxScreen(nav) }
            composable("about") { AboutScreen(nav) }
            composable("web/{key}") { entry ->
                val key = entry.arguments?.getString("key") ?: "wiki"
                val tool = WebTools[key] ?: WebTools.getValue("wiki")
                when (key) {
                    "puzzle" -> NativePuzzleScreen(nav)
                    "light" -> NativeLightBoxScreen(nav)
                    else -> WebToolScreen(nav, tool)
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
    val orderedTools = ToolOrderStore.ordered(context, Tools.map { it.route })
        .mapNotNull { route -> Tools.firstOrNull { it.route == route } }

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
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("TOOLS", color = RuneGold, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton(onClick = { nav.navigate("layout") }) {
                    Text("Reorder", color = Parchment)
                }
            }
        }

        items(orderedTools, key = { it.route }) { tool ->
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
        WikiIcon(tool.icon, tool.title, 38.dp)
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
            when (action.route) {
                "reminder/tears" -> GameReminderStore.setTearsEnabled(
                    context, !GameReminderStore.tearsEnabled(context)
                )
                "reminder/zaff" -> GameReminderStore.setZaffEnabled(
                    context, !GameReminderStore.zaffEnabled(context)
                )
                "reminder/miscellania" -> GameReminderStore.setMiscellaniaEnabled(
                    context, !GameReminderStore.miscellaniaEnabled(context)
                )
                else -> if (action.seconds != null) {
                    val timer = TimerEntry.create(action.label, action.seconds)
                    TimerStore.add(context, timer)
                    TimerScheduler.schedule(context, timer)
                } else {
                    nav.navigate(action.route ?: "timers")
                }
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
            WikiIcon(iconUrlForAction(action), action.label, 30.dp)
            Text(
                when (action.route) {
                    "reminder/tears" -> if (GameReminderStore.tearsEnabled(context)) "Tears: ON" else "Tears: OFF"
                    "reminder/zaff" -> if (GameReminderStore.zaffEnabled(context)) "Zaff: ON" else "Zaff: OFF"
                    "reminder/miscellania" -> if (GameReminderStore.miscellaniaEnabled(context)) "Misc: ON" else "Misc: OFF"
                    else -> action.label
                },
                color = RuneGold,
                fontWeight = FontWeight.Bold,
                fontSize = 9.sp,
                textAlign = TextAlign.Center,
                maxLines = 2
            )
        }
    }
}

data class FarmPreset(val label: String, val seconds: Long, val iconFile: String)

private val FarmPresets = listOf(
    FarmPreset("Herb run", 80 * 60, "Ranarr_weed.png"),
    FarmPreset("Birdhouse run", 50 * 60, "Bird_house.png"),
    FarmPreset("Coral", 160 * 60, "Elkhorn_coral.png"),
    FarmPreset("Allotments", 40 * 60, "Watermelon.png"),
    FarmPreset("Hops", 40 * 60, "Jute_fibre.png"),
    FarmPreset("Bushes", 100 * 60, "White_berries.png"),
    FarmPreset("Oak tree", 200 * 60, "Oak_tree_(grown).png"),
    FarmPreset("Willow tree", 240 * 60, "Willow_tree_(grown).png"),
    FarmPreset("Maple tree", 320 * 60, "Maple_Tree_(grown).png"),
    FarmPreset("Yew tree", 400 * 60, "Yew_tree_(grown).png"),
    FarmPreset("Magic tree", 480 * 60, "Magic_tree_(grown).png"),
    FarmPreset("Fruit tree", 960 * 60, "Palm_tree_(grown).png"),
    FarmPreset("Teak tree", 4480 * 60, "Teak_tree_(grown).png"),
    FarmPreset("Mahogany tree", 5120 * 60, "Mahogany_tree_(grown).png"),
    FarmPreset("Cactus", 560 * 60, "Cactus_(grown).png"),
    FarmPreset("Calquat", 1280 * 60, "Calquat_tree_(grown).png")
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
                icon = osrsWikiImage("Watch.png"),
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
            item { GameRemindersCard(context) }

            item {
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xff2e2924))) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("CUSTOM / AFK TIMER", color = RuneGold, fontWeight = FontWeight.Bold)
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
                                Text(timer.label, color = TextLight, fontWeight = FontWeight.Bold)
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
                        WikiIcon(
                            osrsWikiImage(preset.iconFile),
                            preset.label,
                            38.dp
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(preset.label, color = TextLight, fontWeight = FontWeight.Bold)
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
private fun NativePuzzleScreen(nav: NavHostController) {
    val context = LocalContext.current
    val handler = remember { Handler(Looper.getMainLooper()) }
    var analysis by remember { mutableStateOf<PuzzleAnalysis?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    // moveIndex is always the start of a five-move page.
    var moveIndex by remember { mutableIntStateOf(0) }
    var fixingGap by remember { mutableStateOf(false) }
    // Never present tap instructions as verified until the real screenshot has
    // been visually compared with the inferred picture.
    var boardVerified by remember { mutableStateOf(false) }
    var editingTiles by remember { mutableStateOf(false) }
    var selectedTilePosition by remember { mutableIntStateOf(-1) }
    var correctedBoard by remember { mutableStateOf(IntArray(25) { it }) }
    var previousCorrection by remember { mutableStateOf<IntArray?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        analysis = null
        error = null
        loading = true
        moveIndex = 0
        fixingGap = false
        boardVerified = false
        PuzzleBubbleStore.setVerified(context, false)
        editingTiles = false
        selectedTilePosition = -1

        Thread {
            val bitmap = try {
                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
            } catch (_: Exception) {
                null
            }

            val result = if (bitmap != null) {
                try { PuzzleEngine.analyse(context.applicationContext, bitmap)?.also {
                    PuzzleBubbleStore.save(context.applicationContext, it)
                } } catch (_: Exception) { null }
            } else null

            handler.post {
                loading = false
                if (result == null) {
                    error = "I couldn't reliably read that puzzle. Make sure the whole open 5×5 puzzle box is visible in the screenshot and try again."
                } else {
                    analysis = result
                    correctedBoard = result.board.copyOf()
                }
            }
        }.start()
    }

    Screen(nav, "Puzzle Box Solver") { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    "Upload the screenshot from OSRS. The app identifies it against the seven official puzzle images, reconstructs the board, and calculates a fast solution. Five upcoming taps are numbered directly on the puzzle. Tap 1–5 in order, then press Next 5. A non-focusing Puzzle Bubble can stay over OSRS.",
                    color = Parchment
                )
            }

            item {
                Button(
                    onClick = { picker.launch("image/*") },
                    enabled = !loading,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = RuneGold, contentColor = Color.Black)
                ) {
                    Text(if (loading) "Reading puzzle..." else "Upload Puzzle Screenshot")
                }
            }

            error?.let { message ->
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xff4a302a)),
                        border = BorderStroke(1.dp, RuneGoldDark)
                    ) {
                        Text(message, color = TextLight, modifier = Modifier.padding(12.dp))
                    }
                }
            }

            analysis?.let { solved ->
                val recognizedSolvable = !solved.inferredParityRepair &&
                    (solved.moves.isNotEmpty() || solved.board.indices.all { solved.board[it] == it })
                if (!boardVerified) {
                    item {
                        Text(
                            "VERIFY BEFORE TAPPING ANY TILES",
                            color = RuneGold, fontSize = 19.sp,
                            fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    item {
                        Text(
                            "Compare the original screenshot FIRST with the reconstructed picture BELOW. If any tile differs, use Fix ANY tile / EMPTY before displaying tap instructions.",
                            color = TextLight, fontSize = 13.sp
                        )
                    }
                    if (solved.inferredParityRepair) {
                        item {
                            Text(
                                "Recognition detected an IMPOSSIBLE tile arrangement (odd parity). No moves will be shown. Use Fix ANY tile / EMPTY to correct tile assignments, then recalculate.",
                                color = Color(0xffffaa80), fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    item { PuzzleRecognitionComparison(solved) }
                    item {
                        Button(
                            onClick = {
                                boardVerified = true
                                PuzzleBubbleStore.setVerified(context, true)
                                editingTiles = false
                                fixingGap = false
                                error = null
                                PuzzleBubbleStore.save(context.applicationContext, solved, 0)
                            },
                            enabled = !loading && recognizedSolvable,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = RuneGold, contentColor = Color.Black
                            )
                        ) {
                            Text(if (recognizedSolvable) "Yes, all 25 cells match — Show solution"
                                 else "Fix tile recognition before solving")
                        }
                    }
                    item {
                        OutlinedButton(
                            onClick = {
                                if (!editingTiles) {
                                    correctedBoard = solved.board.copyOf()
                                    previousCorrection = null
                                }
                                editingTiles = !editingTiles
                                fixingGap = false
                                selectedTilePosition = -1
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (editingTiles) "Close tile editor" else "Fix ANY tile / EMPTY (tap to swap)")
                        }
                    }
                    if (editingTiles) {
                        item {
                            PuzzleTileAssignmentPicker(
                                screenshot = solved.preview,
                                reference = solved.reference,
                                board = correctedBoard,
                                selectedPosition = selectedTilePosition,
                                onSelectPosition = { selectedTilePosition = it },
                                onSwapPosition = { other ->
                                    if (selectedTilePosition in 0..24 && other in 0..24 &&
                                        selectedTilePosition != other) {
                                        previousCorrection = correctedBoard.copyOf()
                                        correctedBoard = PuzzleTileMapping.swapPositions(
                                            correctedBoard, selectedTilePosition, other
                                        )
                                        selectedTilePosition = -1
                                    }
                                },
                                onChooseTile = { targetPosition ->
                                    if (selectedTilePosition in 0..24 && targetPosition in 0..24) {
                                        if (correctedBoard[selectedTilePosition] != targetPosition) {
                                            previousCorrection = correctedBoard.copyOf()
                                            correctedBoard = PuzzleTileMapping.assignTile(
                                                correctedBoard, selectedTilePosition, targetPosition
                                            )
                                        }
                                        selectedTilePosition = -1
                                    }
                                }
                            )
                        }
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        previousCorrection?.let {
                                            correctedBoard = it.copyOf()
                                            previousCorrection = null
                                            selectedTilePosition = -1
                                        }
                                    },
                                    enabled = previousCorrection != null,
                                    modifier = Modifier.weight(1f)
                                ) { Text("Undo last swap") }
                                OutlinedButton(
                                    onClick = {
                                        correctedBoard = solved.board.copyOf()
                                        previousCorrection = null
                                        selectedTilePosition = -1
                                    },
                                    modifier = Modifier.weight(1f)
                                ) { Text("Reset tiles") }
                            }
                        }
                        item {
                            Button(
                                onClick = {
                                    loading = true
                                    val board = correctedBoard.copyOf()
                                    Thread {
                                        val corrected = try {
                                            PuzzleEngine.solveCorrectedBoard(solved, board)
                                        } catch (_: Exception) { null }
                                        handler.post {
                                            loading = false
                                            if (corrected == null) {
                                                error = "The corrected 5×5 board is not a valid sliding-puzzle state yet. Check that you selected the real EMPTY square and corrected every mismatched tile. Your changes are still here; undo or keep editing."
                                            } else {
                                                analysis = corrected
                                                boardVerified = false
                                                PuzzleBubbleStore.setVerified(context, false)
                                                editingTiles = false
                                                moveIndex = 0
                                                error = null
                                                PuzzleBubbleStore.save(context.applicationContext, corrected, 0)
                                            }
                                        }
                                    }.start()
                                },
                                enabled = !loading,
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = RuneGold, contentColor = Color.Black
                                )
                            ) {
                                Text(if (loading) "Recalculating..." else "Recalculate using corrected tiles")
                            }
                        }
                    }
                    item {
                        OutlinedButton(
                            onClick = {
                                fixingGap = !fixingGap
                                editingTiles = false
                            },
                            enabled = !loading,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (fixingGap) "Cancel empty-square correction" else "Wrong EMPTY cell? Tap to correct")
                        }
                    }
                    if (fixingGap) {
                        item {
                            Text("Tap the actual empty square on your original OSRS screenshot:",
                                color = Parchment)
                        }
                        item {
                            EmptySpacePicker(solved.preview) { position ->
                                if (!loading) {
                                    loading = true
                                    fixingGap = false
                                    Thread {
                                        val corrected = try {
                                            PuzzleEngine.correctEmptyPosition(solved, position)
                                        } catch (_: Exception) { null }
                                        handler.post {
                                            loading = false
                                            if (corrected == null) {
                                                error = "Couldn't read a solvable board with that empty square. Try Edit recognized tile positions."
                                            } else {
                                                analysis = corrected
                                                correctedBoard = corrected.board.copyOf()
                                                boardVerified = false
                                                PuzzleBubbleStore.setVerified(context, false)
                                                moveIndex = 0
                                                error = null
                                            }
                                        }
                                    }.start()
                                }
                            }
                        }
                    }
                } else {
                item {
                    OutlinedButton(
                        onClick = {
                            boardVerified = false
                            PuzzleBubbleStore.setVerified(context, false)
                            editingTiles = false
                            fixingGap = false
                            moveIndex = 0
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Recheck picture tiles against original screenshot")
                    }
                }
                item {
                    AnnotatedPuzzlePreview(solved = solved, start = moveIndex)
                }

                if (solved.moves.isNotEmpty()) {
                    item {
                        Button(
                            onClick = {
                                if (Settings.canDrawOverlays(context)) {
                                    PuzzleBubbleStore.save(context, solved, moveIndex)
                                    ContextCompat.startForegroundService(
                                        context,
                                        Intent(context, FloatingOverlayService::class.java)
                                            .setAction(FloatingOverlayService.ACTION_SHOW_PUZZLE)
                                    )
                                } else {
                                    context.startActivity(
                                        Intent(
                                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                            Uri.parse("package:" + context.packageName)
                                        )
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xff4b443c), contentColor = TextLight
                            )
                        ) {
                            Text(
                                if (Settings.canDrawOverlays(context))
                                    "Show Puzzle Bubble (keep OSRS focus)"
                                else "Enable overlay permission for Puzzle Bubble"
                            )
                        }
                    }
                }

                item {
                    OutlinedButton(
                        onClick = { fixingGap = !fixingGap },
                        enabled = !loading,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (fixingGap) "Cancel empty-square correction" else "Wrong EMPTY square? Correct it")
                    }
                }
                if (fixingGap) {
                    item {
                        Text(
                            "Tap the REAL empty square in your original OSRS screenshot below. The app will reidentify all tiles and recalculate the solution.",
                            color = TextLight,
                            fontSize = 14.sp
                        )
                    }
                    item {
                        EmptySpacePicker(solved.preview) { position ->
                            if (!loading) {
                                loading = true
                                fixingGap = false
                                Thread {
                                    val corrected = try {
                                        PuzzleEngine.correctEmptyPosition(solved, position)?.also {
                                            PuzzleBubbleStore.save(context.applicationContext, it)
                                        }
                                    } catch (_: Exception) { null }
                                    handler.post {
                                        loading = false
                                        if (corrected == null) {
                                            error = "Couldn't solve with that empty square. Please upload a new screenshot."
                                        } else {
                                            error = null
                                            analysis = corrected
                                            correctedBoard = corrected.board.copyOf()
                                            boardVerified = false
                                            PuzzleBubbleStore.setVerified(context, false)
                                            moveIndex = 0
                                        }
                                    }
                                }.start()
                            }
                        }
                    }
                }

                if (solved.moves.size > 120) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xff4a302a)),
                            border = BorderStroke(1.dp, RuneGoldDark)
                        ) {
                            Text(
                                "This route is unusually long (" + solved.moves.size +
                                    " moves). The screenshot may have been read incorrectly. " +
                                    "Check the EMPTY square above before following these instructions.",
                                modifier = Modifier.padding(12.dp),
                                color = TextLight
                            )
                        }
                    }
                }

                item {
                    Text(
                        solved.puzzleName + " Puzzle Box",
                        color = RuneGold,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (solved.moves.isEmpty()) {
                    item {
                        Text(
                            "This puzzle is already solved.",
                            color = TextLight,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                } else if (moveIndex >= solved.moves.size) {
                    item {
                        Text(
                            "Solved state — the empty space should now be in the bottom-right corner.",
                            color = TextLight,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    item {
                        OutlinedButton(
                            onClick = {
                                moveIndex = ((solved.moves.size - 1).coerceAtLeast(0) / 5) * 5
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Previous 5")
                        }
                    }
                } else {
                    val batchStart = moveIndex
                    val batchEnd = minOf(solved.moves.size, batchStart + 5)
                    val batch = PuzzleEngine.moveHints(
                        solved.board,
                        solved.moves,
                        batchStart,
                        batchEnd - batchStart
                    )

                    item {
                        Text(
                            "Moves " + (batchStart + 1) + "–" + batchEnd + " of " + solved.moves.size,
                            color = TextLight,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    item {
                        Text(
                            "Tap the five tiles in order using the arrows and row/column labels below. All five gold numbers are shown on the puzzle. Press Next 5 after completing the entire group.",
                            color = Parchment,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    item {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            batch.forEachIndexed { index, hint ->
                                val absoluteMove = batchStart + index
                                Card(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(90.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color(0xff2e2924)),
                                    border = BorderStroke(1.dp, RuneGold)
                                ) {
                                    Column(
                                        Modifier.fillMaxSize(),
                                        verticalArrangement = Arrangement.Center,
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            (absoluteMove + 1).toString(),
                                            color = RuneGold,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            hint.arrow,
                                            color = RuneGold,
                                            fontSize = 30.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            "R" + (hint.position / 5 + 1) + " C" + (hint.position % 5 + 1),
                                            color = Parchment,
                                            fontSize = 10.sp
                                        )
                                    }
                                }
                            }
                        }
                    }

                    item {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    moveIndex = (moveIndex - 5).coerceAtLeast(0)
                                },
                                enabled = moveIndex > 0,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Previous 5")
                            }
                            Button(
                                onClick = {
                                    moveIndex = (moveIndex + 5).coerceAtMost(solved.moves.size)
                                },
                                enabled = moveIndex < solved.moves.size,
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = RuneGold,
                                    contentColor = Color.Black
                                )
                            ) {
                                Text(if (batchEnd == solved.moves.size) "Done" else "Next 5")
                            }
                        }
                    }

                    item {
                        Text(
                            "Each card is one tap in this five-move group. The five gold markers are simultaneously visible on the board; Previous 5 and Next 5 jump between groups. If the board doesn't match OSRS, correct the empty square or upload a fresh screenshot.",
                            color = StoneLight,
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                item {
                    TextButton(
                        onClick = {
                            analysis = null
                            error = null
                            moveIndex = 0
                            fixingGap = false
                            boardVerified = false
                            editingTiles = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Start over", color = Parchment)
                    }
                }
                } // end verified board instructions
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
private fun GuidesScreen(nav: NavHostController) {
    val guideItems = listOf(
        Triple("Theoatrix Skill Guides", "Levelling guides for OSRS skills", "theoatrix_skills"),
        Triple("Theoatrix AFK Methods", "Low-attention training methods", "theoatrix_afk"),
        Triple("OSRS Wiki Guides", "Community-maintained guides by topic", "wiki_guides")
    )
    Screen(nav, "Guides") { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text("Choose a guide collection. Guides open inside Companion.",
                    color = Parchment)
            }
            items(guideItems.size) { i ->
                val entry = guideItems[i]
                Card(
                    modifier = Modifier.fillMaxWidth()
                        .clickable { nav.navigate("web/" + entry.third) },
                    colors = CardDefaults.cardColors(containerColor = Stone),
                    border = BorderStroke(1.dp, RuneGoldDark)
                ) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text(entry.first, color = RuneGold, fontWeight = FontWeight.Bold,
                            fontSize = 18.sp)
                        Spacer(Modifier.height(5.dp))
                        Text(entry.second, color = Parchment, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun WebToolScreen(nav: NavHostController, tool: WebTool) {
    val context = LocalContext.current
    val supportsSavedName = tool.title in setOf(
        "Wise Old Man", "Skill Calculators", "Quests",
        "Achievement Diaries", "OSRS Wiki", "DPS Calculator"
    )
    var playerName by remember { mutableStateOf(SavedPlayerName.get(context)) }
    var page by remember { mutableStateOf<WebView?>(null) }
    var nameReaderStarted by remember { mutableStateOf(false) }
    var uploadCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uploadCallback?.onReceiveValue(uri?.let { arrayOf(it) })
        uploadCallback = null
    }

    Screen(nav, tool.title) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (supportsSavedName) {
                Row(
                    modifier = Modifier.fillMaxWidth().background(Color(0xff2e2924)).padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedTextField(
                        value = playerName,
                        onValueChange = {
                            playerName = it.take(12)
                            SavedPlayerName.save(context, playerName)
                        },
                        label = { Text("Saved OSRS username") },
                        placeholder = { Text("Player name") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("OSRS username", playerName))
                        },
                        enabled = playerName.isNotBlank()
                    ) {
                        Text("Copy", color = RuneGold)
                    }
                    TextButton(
                        onClick = { page?.let { SavedPlayerName.fillRelevantFields(it, it.url, playerName) } },
                        enabled = playerName.isNotBlank()
                    ) {
                        Text("Fill", color = RuneGold)
                    }
                }
                Text(
                    "Saved locally and shared across Wise Old Man, calculators, quests, diaries, Wiki, and DPS. Fill works when the website provides a recognizable player field.",
                    color = Parchment,
                    fontSize = 10.sp,
                    modifier = Modifier.fillMaxWidth().background(Color(0xff2e2924)).padding(horizontal = 10.dp, vertical = 2.dp)
                )
            } else {
                Text(
                    tool.subtitle,
                    color = Parchment,
                    fontSize = 12.sp,
                    modifier = Modifier.fillMaxWidth().background(Color(0xff2e2924)).padding(8.dp)
                )
            }
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    WebView(context).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.allowContentAccess = true
                        settings.setSupportZoom(true)
                        settings.builtInZoomControls = true
                        settings.displayZoomControls = false

                        if (tool.url.contains("secure.runescape.com")) {
                            // Jagex's legacy news archive is desktop-width and otherwise
                            // opens cropped/over-zoomed inside a phone WebView.
                            settings.useWideViewPort = true
                            settings.loadWithOverviewMode = true
                            settings.textZoom = 100
                        }

                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                if (supportsSavedName && view != null) {
                                    SavedPlayerName.fillRelevantFields(view, url, SavedPlayerName.get(context))
                                    // Some calculator sites render the player field after navigation.
                                    view.postDelayed({
                                        if (view.url == url) {
                                            SavedPlayerName.fillRelevantFields(
                                                view, url, SavedPlayerName.get(context)
                                            )
                                        }
                                    }, 1200L)
                                    if (!nameReaderStarted) {
                                        nameReaderStarted = true
                                        // Observe existing user/player fields in the embedded
                                        // site's DOM. Stop when the WebView is detached.
                                        view.postDelayed(object : Runnable {
                                            override fun run() {
                                                if (!view.isAttachedToWindow) return
                                                SavedPlayerName.readEnteredName(view, view.url) {
                                                    entered -> playerName = entered
                                                }
                                                view.postDelayed(this, 2500L)
                                            }
                                        }, 2500L)
                                    }
                                }
                            }
                        }
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
                        page = this
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
            Text("Version 0.5.9", color = Parchment)
            Text("Free and open source. Timers, reminders, portfolio entries, watchlists, notes, and layout settings stay on your device.", color = TextLight)
            Text("github.com/mandecoy/OSRS-Mobile-Companion", color = RuneGold)
            Text("Web tools are third-party services and remain subject to their own policies and availability.", color = Parchment, fontSize = 12.sp)
            Text("Not affiliated with, endorsed by, or sponsored by Jagex Ltd.", color = StoneLight, fontSize = 12.sp)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Screen(
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
