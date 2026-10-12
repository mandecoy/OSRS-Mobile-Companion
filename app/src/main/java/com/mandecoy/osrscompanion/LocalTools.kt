package com.mandecoy.osrscompanion

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
    private const val HIDDEN_TOOLS = "hidden_tool_routes"
    private const val HIDDEN_QUICK = "hidden_quick_slots"

    val defaults = listOf(
        "timers", "floating", "ge_tools", "high_alch", "portfolio", "fairy_rings", "notepad",
        "puzzle", "lightbox", "guides", "web/prices", "web/wiki", "web/wise", "web/stars",
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
        "guides" to "Guides",
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

    // These flags hide home screen cards WITHOUT deleting their configurations
    // or removing them from the drag-and-drop reorder list.
    fun hiddenRoutes(context: Context): Set<String> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(HIDDEN_TOOLS, "")?.split("|")?.filter { it.isNotBlank() }
            ?.toSet() ?: emptySet()

    fun isToolVisible(context: Context, route: String): Boolean =
        route !in hiddenRoutes(context)

    fun setToolVisible(context: Context, route: String, visible: Boolean) {
        val hidden = hiddenRoutes(context).toMutableSet()
        if (visible) hidden.remove(route) else hidden.add(route)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(HIDDEN_TOOLS, hidden.joinToString("|")).apply()
    }

    fun hiddenQuickSlots(context: Context): Set<Int> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(HIDDEN_QUICK, "")?.split(",")?.mapNotNull { it.toIntOrNull() }
            ?.filter { it in 1..4 }?.toSet() ?: emptySet()

    fun isQuickVisible(context: Context, slot: Int): Boolean =
        slot !in hiddenQuickSlots(context)

    fun setQuickVisible(context: Context, slot: Int, visible: Boolean) {
        val hidden = hiddenQuickSlots(context).toMutableSet()
        if (visible) hidden.remove(slot) else hidden.add(slot)
        saveHiddenQuick(context, hidden)
    }

    fun swapQuickVisibility(context: Context, a: Int, b: Int) {
        if (a !in 1..4 || b !in 1..4 || a == b) return
        val hidden = hiddenQuickSlots(context).toMutableSet()
        val aHidden = a in hidden
        val bHidden = b in hidden
        if (aHidden != bHidden) {
            if (aHidden) {
                hidden.remove(a)
                hidden.add(b)
            } else {
                hidden.remove(b)
                hidden.add(a)
            }
            saveHiddenQuick(context, hidden)
        }
    }

    private fun saveHiddenQuick(context: Context, hidden: Set<Int>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(HIDDEN_QUICK, hidden.sorted().joinToString(",")).apply()
    }

    fun reset(context: Context) = save(context, defaults)

    fun saveOrder(context: Context, order: List<String>) = save(context, order)

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
    val order = remember {
        mutableStateListOf<String>().apply { addAll(ToolOrderStore.load(context)) }
    }
    var visibilityRevision by remember { mutableStateOf(0) }
    // Both hidden and visible cards stay in the editor for easy restoration.
    val hiddenTools = remember(visibilityRevision) { ToolOrderStore.hiddenRoutes(context) }
    val hiddenQuick = remember(visibilityRevision) { ToolOrderStore.hiddenQuickSlots(context) }
    val quickItems = remember {
        mutableStateListOf<QuickDragItem>().apply {
            QuickActionStore.load(context).forEachIndexed { index, action ->
                add(QuickDragItem(index, action))
            }
        }
    }

    fun moveQuick(token: Int, delta: Int): Boolean {
        val from = quickItems.indexOfFirst { it.token == token }
        if (from < 0) return false
        val to = from + delta
        if (to !in quickItems.indices) return false

        QuickActionStore.swap(context, from + 1, to + 1)
        // Hidden state follows the dragged shortcut, not its old slot number.
        ToolOrderStore.swapQuickVisibility(context, from + 1, to + 1)
        visibilityRevision++
        val item = quickItems.removeAt(from)
        quickItems.add(to, item)
        return true
    }

    fun moveTool(route: String, delta: Int): Boolean {
        val from = order.indexOf(route)
        if (from < 0) return false
        val to = from + delta
        if (to !in order.indices) return false

        val item = order.removeAt(from)
        order.add(to, item)
        ToolOrderStore.saveOrder(context, order)
        return true
    }

    Screen(nav, "Reorder Widgets") { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text(
                    "Hold and drag to reorder. Tap the eye icon to hide or show any Quick Widget or main tool on Home; hidden items stay here and keep their settings.",
                    color = LocalParchment
                )
            }

            item {
                Text("QUICK WIDGETS", color = LocalGold, fontWeight = FontWeight.Bold)
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    quickItems.forEach { item ->
                        key(item.token) {
                            val index = quickItems.indexOfFirst { it.token == item.token }
                            DraggableOrderCard(
                                title = item.action.label,
                                subtitle = "Slot " + (index + 1),
                                visible = index + 1 !in hiddenQuick,
                                onToggleVisible = {
                                    ToolOrderStore.setQuickVisible(
                                        context, index + 1, index + 1 in hiddenQuick
                                    )
                                    visibilityRevision++
                                },
                                onMove = { delta -> moveQuick(item.token, delta) }
                            )
                        }
                    }
                }
            }

            item {
                Text("MAIN TOOLS", color = LocalGold, fontWeight = FontWeight.Bold)
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    order.forEach { route ->
                        key(route) {
                            val index = order.indexOf(route)
                            DraggableOrderCard(
                                title = ToolOrderStore.title(route),
                                subtitle = null,
                                visible = route !in hiddenTools,
                                onToggleVisible = {
                                    ToolOrderStore.setToolVisible(context, route, route in hiddenTools)
                                    visibilityRevision++
                                },
                                onMove = { delta -> moveTool(route, delta) }
                            )
                        }
                    }
                }
            }

            item {
                Button(
                    onClick = {
                        order.clear()
                        order.addAll(ToolOrderStore.defaults)
                        ToolOrderStore.reset(context)
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

private data class QuickDragItem(
    val token: Int,
    val action: QuickAction
)

/**
 * Local vector eye icon, avoiding external image downloads or heavyweight
 * icon dependencies. A diagonal slash indicates hidden.
 */
@Composable
fun EyeVisibilityIcon(visible: Boolean, color: Color = LocalGold) {
    Canvas(modifier = Modifier.size(26.dp)) {
        val w = size.width
        val h = size.height
        val line = 2.1.dp.toPx()
        val eye = Path().apply {
            moveTo(w * 0.07f, h * 0.50f)
            cubicTo(w * 0.25f, h * 0.14f, w * 0.75f, h * 0.14f, w * 0.93f, h * 0.50f)
            cubicTo(w * 0.75f, h * 0.86f, w * 0.25f, h * 0.86f, w * 0.07f, h * 0.50f)
            close()
        }
        drawPath(eye, color, style = Stroke(width = line))
        drawCircle(color, radius = w * 0.12f, center = Offset(w * .50f, h * .50f))
        if (!visible) {
            drawLine(
                color = LocalStone,
                start = Offset(w * 0.06f, h * 0.94f),
                end = Offset(w * 0.94f, h * 0.06f),
                strokeWidth = line * 3f
            )
            drawLine(
                color = color,
                start = Offset(w * 0.06f, h * 0.94f),
                end = Offset(w * 0.94f, h * 0.06f),
                strokeWidth = line
            )
        }
    }
}

@Composable
private fun DraggableOrderCard(
    title: String,
    subtitle: String?,
    visible: Boolean,
    onToggleVisible: () -> Unit,
    onMove: (Int) -> Boolean
) {
    val density = LocalDensity.current
    val rowStepPx = with(density) { 72.dp.toPx() }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .graphicsLayer {
                translationY = dragOffset
                scaleX = if (dragging) 1.015f else 1f
                scaleY = if (dragging) 1.015f else 1f
                alpha = if (dragging) 0.92f else 1f
            }
            .pointerInput(title) {
                detectDragGesturesAfterLongPress(
                    onDragStart = {
                        dragging = true
                        dragOffset = 0f
                    },
                    onDragCancel = {
                        dragging = false
                        dragOffset = 0f
                    },
                    onDragEnd = {
                        dragging = false
                        dragOffset = 0f
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        dragOffset += dragAmount.y

                        while (dragOffset > rowStepPx / 2f) {
                            if (!onMove(1)) {
                                dragOffset = 0f
                                break
                            }
                            dragOffset -= rowStepPx
                        }

                        while (dragOffset < -rowStepPx / 2f) {
                            if (!onMove(-1)) {
                                dragOffset = 0f
                                break
                            }
                            dragOffset += rowStepPx
                        }
                    }
                )
            },
        colors = CardDefaults.cardColors(
            containerColor = if (dragging) Color(0xff403931) else LocalStone
        ),
        border = BorderStroke(
            if (dragging) 2.dp else 1.dp,
            if (dragging) LocalGold else LocalGoldDark
        )
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                if (subtitle != null) {
                    Text(subtitle, color = LocalGold, fontSize = 11.sp)
                }
                Text(
                    title,
                    color = if (visible) LocalText else LocalParchment.copy(alpha = 0.55f),
                    fontWeight = FontWeight.Bold
                )
            }
            IconButton(
                onClick = onToggleVisible,
                modifier = Modifier
                    .size(48.dp)
                    .semantics { contentDescription =
                        (if (visible) "Hide " else "Show ") + title }
            ) {
                EyeVisibilityIcon(visible, LocalGold)
            }
            Column(
                modifier = Modifier.height(64.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    "☰",
                    color = LocalGold,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                )
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
