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
    "puzzle" to WebTool("Puzzle Box Solver", "https://osrs-puzzle-box-solver.vercel.app/", "Upload your puzzle-box screenshot and follow the moves in groups of five."),
    "light" to WebTool("Light Box Solver", "https://osrs-lightbox-solver.fletom.com/", "Solve master-clue light boxes."),
    "quests" to WebTool("Quests & Diaries", "https://oldschool.runescape.wiki/w/Quest_List", "Quest and achievement-diary references."),
    "clues" to WebTool("Clue Helper", "https://oldschool.runescape.wiki/w/Treasure_Trails/Guide", "Clue scroll reference hub.")
)

data class Tool(val title: String, val subtitle: String, val route: String)

private val Tools = listOf(
    Tool("Timers", "AFK + farming runs", "timers"),
    Tool("Puzzle Box", "Screenshot solver", "web/puzzle"),
    Tool("Light Box", "Master clue solver", "web/light"),
    Tool("GE Prices", "Live market prices", "web/prices"),
    Tool("OSRS Wiki", "Search anything", "web/wiki"),
    Tool("Wise Old Man", "Player lookup", "web/wise"),
    Tool("Shooting Stars", "OSRS Portal tracker", "web/stars"),
    Tool("Skill Calculators", "All skills", "web/skills"),
    Tool("Quests & Diaries", "Guides + requirements", "web/quests"),
    Tool("Clue Helper", "Treasure trail references", "web/clues"),
    Tool("Quick Actions", "Customize two shortcuts", "quick"),
    Tool("About", "Source + privacy", "about")
)

data class QuickAction(
    val id: String,
    val label: String,
    val route: String? = null,
    val seconds: Long? = null
)

private val QuickChoices = listOf(
    QuickAction("afk5", "AFK 5 min", seconds = 300),
    QuickAction("afk10", "AFK 10 min", seconds = 600),
    QuickAction("herb", "Herb run", seconds = 80 * 60),
    QuickAction("bird", "Birdhouse", seconds = 50 * 60),
    QuickAction("puzzle", "Puzzle Box", route = "web/puzzle"),
    QuickAction("stars", "Shooting Stars", route = "web/stars"),
    QuickAction("wiki", "OSRS Wiki", route = "web/wiki"),
    QuickAction("timers", "Timers", route = "timers")
)

object QuickActionStore {
    private const val PREFS = "quick_actions"

    fun load(context: Context): Pair<QuickAction, QuickAction> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val first = QuickChoices.firstOrNull { it.id == prefs.getString("one", "afk5") } ?: QuickChoices[0]
        val second = QuickChoices.firstOrNull { it.id == prefs.getString("two", "herb") } ?: QuickChoices[2]
        return first to second
    }

    fun save(context: Context, slot: Int, action: QuickAction) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(if (slot == 1) "one" else "two", action.id).apply()
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
                if (key == "puzzle") PuzzleBrowserScreen(nav, tool) else WebToolScreen(nav, tool)
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
            Text("QUICK ACTIONS", color = RuneGold, fontWeight = FontWeight.Bold)
        }
        item { QuickCard(quick.first, context, nav) { revision++ } }
        item { QuickCard(quick.second, context, nav) { revision++ } }

        item(span = { GridItemSpan(2) }) {
            Text("TOOLS", color = RuneGold, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
        }
        items(Tools) { tool ->
            StoneButton(tool.title, tool.subtitle) { nav.navigate(tool.route) }
        }
    }
}

@Composable
private fun StoneButton(title: String, subtitle: String, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .height(104.dp)
            .border(2.dp, RuneGoldDark, RoundedCornerShape(8.dp))
            .background(
                Brush.verticalGradient(listOf(StoneLight, Stone, Color(0xff38322c))),
                RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(title, color = TextLight, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(5.dp))
        Text(subtitle, color = Parchment, fontSize = 11.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun QuickCard(action: QuickAction, context: Context, nav: NavHostController, onChange: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().height(80.dp).clickable {
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
            Modifier.fillMaxSize().padding(10.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(action.label, color = RuneGold, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text(if (action.seconds != null) "Tap to start" else "Tap to open", color = Parchment, fontSize = 11.sp)
        }
    }
}

data class FarmPreset(val label: String, val seconds: Long)

private val FarmPresets = listOf(
    FarmPreset("Herb run", 80 * 60),
    FarmPreset("Birdhouse run", 50 * 60),
    FarmPreset("Allotments", 40 * 60),
    FarmPreset("Hops", 40 * 60),
    FarmPreset("Bushes", 100 * 60),
    FarmPreset("Oak tree", 200 * 60),
    FarmPreset("Willow tree", 240 * 60),
    FarmPreset("Maple tree", 320 * 60),
    FarmPreset("Yew tree", 400 * 60),
    FarmPreset("Magic tree", 480 * 60),
    FarmPreset("Fruit tree", 960 * 60),
    FarmPreset("Teak tree", 4480 * 60),
    FarmPreset("Mahogany tree", 5120 * 60),
    FarmPreset("Cactus", 560 * 60),
    FarmPreset("Calquat", 1280 * 60)
)

@Composable
private fun TimersScreen(nav: NavHostController) {
    val context = LocalContext.current
    var input by remember { mutableStateOf("300") }
    var refresh by remember { mutableIntStateOf(0) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val active = remember(refresh, now / 1000) { TimerStore.active(context) }

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
            item {
                ExactAlarmCard(context)
            }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xff2e2924))) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("AFK CHECK", color = RuneGold, fontWeight = FontWeight.Bold)
                        Text("Set 1 second through 30 minutes. It can notify while the app is closed.", color = Parchment)
                        OutlinedTextField(
                            value = input,
                            onValueChange = { input = it.filter(Char::isDigit).take(4) },
                            label = { Text("Seconds (1–1800)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Button(
                            onClick = {
                                val seconds = (input.toLongOrNull() ?: 1L).coerceIn(1L, 1800L)
                                val timer = TimerEntry.create("AFK check", seconds)
                                TimerStore.add(context, timer)
                                TimerScheduler.schedule(context, timer)
                                refresh++
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = RuneGold, contentColor = Color.Black)
                        ) {
                            Text("Start AFK timer")
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
    var pair by remember { mutableStateOf(QuickActionStore.load(context)) }

    Screen(nav, "Quick Actions") { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("Pick the two one-tap shortcuts shown on the home screen.", color = Parchment)
            Text("SLOT 1", color = RuneGold, fontWeight = FontWeight.Bold)
            QuickChoices.forEach { action ->
                OutlinedButton(
                    onClick = {
                        QuickActionStore.save(context, 1, action)
                        pair = QuickActionStore.load(context)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (pair.first.id == action.id) "✓ " + action.label else action.label)
                }
            }
            Text("SLOT 2", color = RuneGold, fontWeight = FontWeight.Bold)
            QuickChoices.forEach { action ->
                OutlinedButton(
                    onClick = {
                        QuickActionStore.save(context, 2, action)
                        pair = QuickActionStore.load(context)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (pair.second.id == action.id) "✓ " + action.label else action.label)
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
                "The puzzle preview did not render reliably inside Android WebView. This opens the solver in a browser tab so the puzzle image, arrows, and 5-step mode can render correctly.",
                color = Parchment
            )
            Button(
                onClick = { openSolver() },
                colors = ButtonDefaults.buttonColors(containerColor = RuneGold, contentColor = Color.Black)
            ) {
                Text("Open Puzzle Box Solver")
            }
            Text(
                "Upload the screenshot, select 5 steps, make those five moves in OSRS, then return for the next five.",
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
            Text("Version 0.1.1", color = Parchment)
            Text("Free and open source. Timer and quick-action data stays on your device.", color = TextLight)
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
