package com.mandecoy.osrscompanion

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

object GameReminderStore {
    private const val PREFS = "game_reminders_v1"
    private const val TEARS_ENABLED = "tears_enabled"
    private const val TEARS_LAST_DONE_UTC_DAY = "tears_last_done_utc_day"
    private const val TEARS_LAST_PRESSED_AT = "tears_done_pressed_at"
    private const val ZAFF_ENABLED = "zaff_enabled"
    private const val MISC_ENABLED = "misc_enabled"
    private const val MISC_LAST_DONE = "misc_last_done"
    private const val MISC_LAST_PRESSED_AT = "misc_done_pressed_at"
    private const val MISC_INTERVAL_DAYS = "misc_interval_days"
    private const val NMZ_ENABLED = "nmz_enabled"

    fun tearsEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(TEARS_ENABLED, false)

    fun zaffEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(ZAFF_ENABLED, false)

    fun miscellaniaEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(MISC_ENABLED, false)

    /** NMZ herb boxes (up to 15 daily) reset at 00:00 UTC. */
    fun nmzEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(NMZ_ENABLED, false)

    fun setNmzEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(NMZ_ENABLED, enabled).apply()
        if (enabled) GameReminderScheduler.scheduleNmz(context)
        else GameReminderScheduler.cancelNmz(context)
    }

    /** Defaults to the previous 24-hour reminder; accepts whole days, 1–7. */
    fun miscellaniaIntervalDays(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(MISC_INTERVAL_DAYS, 1).coerceIn(1, 7)

    fun setMiscellaniaIntervalDays(context: Context, days: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putInt(MISC_INTERVAL_DAYS, days.coerceIn(1, 7)).apply()
        if (miscellaniaEnabled(context)) GameReminderScheduler.scheduleMiscellania(context)
    }

    fun setTearsEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(TEARS_ENABLED, enabled).apply()
        if (enabled) GameReminderScheduler.scheduleTears(context)
        else GameReminderScheduler.cancelTears(context)
    }

    fun setZaffEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(ZAFF_ENABLED, enabled).apply()
        if (enabled) GameReminderScheduler.scheduleZaff(context)
        else GameReminderScheduler.cancelZaff(context)
    }

    fun setMiscellaniaEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(MISC_ENABLED, enabled).apply()
        if (enabled) {
            // The first time reminders are enabled is NOT a completed
            // Miscellania visit. Initialize the schedule without recording
            // a false "Done now" button press.
            if (miscellaniaLastDone(context) == null) {
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit().putLong(MISC_LAST_DONE, System.currentTimeMillis()).apply()
            }
            GameReminderScheduler.scheduleMiscellania(context)
        } else {
            GameReminderScheduler.cancelMiscellania(context)
        }
    }

    fun markMiscellaniaDoneNow(context: Context) {
        val pressedAt = System.currentTimeMillis()
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putLong(MISC_LAST_DONE, pressedAt)
            .putLong(MISC_LAST_PRESSED_AT, pressedAt)
            .apply()
        if (miscellaniaEnabled(context)) GameReminderScheduler.scheduleMiscellania(context)
    }

    /** Exact button press, not the automatic reminder scheduling baseline. */
    fun miscellaniaDonePressedAt(context: Context): Long? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong(MISC_LAST_PRESSED_AT, 0L).takeIf { it > 0L }

    fun miscellaniaLastDone(context: Context): Long? {
        val value = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong(MISC_LAST_DONE, 0L)
        return value.takeIf { it > 0L }
    }

    fun markTearsDoneToday(context: Context) {
        val pressedAt = System.currentTimeMillis()
        val utcDayStart = ZonedDateTime.now(ZoneOffset.UTC)
            .truncatedTo(ChronoUnit.DAYS)
            .toInstant()
            .toEpochMilli()
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putLong(TEARS_LAST_DONE_UTC_DAY, utcDayStart)
            .putLong(TEARS_LAST_PRESSED_AT, pressedAt)
            .apply()
        if (tearsEnabled(context)) GameReminderScheduler.scheduleTears(context)
    }

    /** Keep the exact click timestamp separate from UTC day scheduling. */
    fun tearsDonePressedAt(context: Context): Long? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong(TEARS_LAST_PRESSED_AT, 0L).takeIf { it > 0L }

    fun tearsLastDoneUtcDay(context: Context): Long? {
        val value = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong(TEARS_LAST_DONE_UTC_DAY, 0L)
        return value.takeIf { it > 0L }
    }
}

object GameReminderScheduler {
    private const val TEARS_REQUEST = 7301
    private const val ZAFF_REQUEST = 7302
    private const val MISC_REQUEST = 7303
    private const val NMZ_REQUEST = 7304
    const val TYPE_NMZ = "nmz"
    const val TYPE_TEARS = "tears"
    const val TYPE_ZAFF = "zaff"
    const val TYPE_MISC = "miscellania"

    fun restore(context: Context) {
        if (GameReminderStore.tearsEnabled(context)) scheduleTears(context)
        if (GameReminderStore.zaffEnabled(context)) scheduleZaff(context)
        if (GameReminderStore.miscellaniaEnabled(context)) scheduleMiscellania(context)
        if (GameReminderStore.nmzEnabled(context)) scheduleNmz(context)
    }

    fun scheduleTears(context: Context) {
        val now = System.currentTimeMillis()
        val baseDay = GameReminderStore.tearsLastDoneUtcDay(context)
            ?: ZonedDateTime.now(ZoneOffset.UTC)
                .truncatedTo(ChronoUnit.DAYS)
                .toInstant()
                .toEpochMilli()

        var next = baseDay + 7L * 24L * 60L * 60L * 1000L
        while (next <= now) next += 7L * 24L * 60L * 60L * 1000L
        schedule(context, TEARS_REQUEST, TYPE_TEARS, next)
    }

    fun scheduleZaff(context: Context) {
        val nowUtc = ZonedDateTime.now(ZoneOffset.UTC)
        var next = nowUtc.truncatedTo(ChronoUnit.DAYS).plusDays(1)
        if (!next.toInstant().isAfter(Instant.now())) next = next.plusDays(1)
        schedule(context, ZAFF_REQUEST, TYPE_ZAFF, next.toInstant().toEpochMilli())
    }

    fun scheduleMiscellania(context: Context) {
        val now = System.currentTimeMillis()
        val base = GameReminderStore.miscellaniaLastDone(context) ?: now
        val intervalMillis = GameReminderStore.miscellaniaIntervalDays(context) *
            24L * 60L * 60L * 1000L
        // Anchor to the player's last actual "Done now" check-in, never a
        // notification dispatch. If the device was off, skip missed repeats
        // instead of scheduling an immediate repeating notification storm.
        val elapsed = (now - base).coerceAtLeast(0L)
        val next = base + (elapsed / intervalMillis + 1L) * intervalMillis
        schedule(context, MISC_REQUEST, TYPE_MISC, next)
    }

    fun scheduleNmz(context: Context) {
        // Exactly one notification at each new UTC calendar day. A Done
        // press records collection but does not move the server's reset.
        val next = ZonedDateTime.now(ZoneOffset.UTC)
            .truncatedTo(ChronoUnit.DAYS).plusDays(1)
            .toInstant().toEpochMilli()
        schedule(context, NMZ_REQUEST, TYPE_NMZ, next)
    }

    fun cancelNmz(context: Context) = cancel(context, NMZ_REQUEST, TYPE_NMZ)
    fun cancelTears(context: Context) = cancel(context, TEARS_REQUEST, TYPE_TEARS)
    fun cancelZaff(context: Context) = cancel(context, ZAFF_REQUEST, TYPE_ZAFF)
    fun cancelMiscellania(context: Context) = cancel(context, MISC_REQUEST, TYPE_MISC)

    private fun schedule(context: Context, requestCode: Int, type: String, atMillis: Long) {
        val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pending = pendingIntent(context, requestCode, type)
        if (android.os.Build.VERSION.SDK_INT >= 31 && !manager.canScheduleExactAlarms()) {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pending)
        } else {
            manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pending)
        }
    }

    private fun cancel(context: Context, requestCode: Int, type: String) {
        val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        manager.cancel(pendingIntent(context, requestCode, type))
    }

    private fun pendingIntent(context: Context, requestCode: Int, type: String): PendingIntent {
        val intent = Intent(context, GameReminderReceiver::class.java)
            .putExtra("type", type)
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}

class GameReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.getStringExtra("type")) {
            GameReminderScheduler.TYPE_TEARS -> {
                if (!GameReminderStore.tearsEnabled(context)) return
                TimerNotifications.show(
                    context,
                    7301,
                    "Tears of Guthix is ready"
                )
                GameReminderScheduler.scheduleTears(context)
            }
            GameReminderScheduler.TYPE_ZAFF -> {
                if (!GameReminderStore.zaffEnabled(context)) return
                TimerNotifications.show(
                    context,
                    7302,
                    "Zaff's battlestaves have reset"
                )
                GameReminderScheduler.scheduleZaff(context)
            }
            GameReminderScheduler.TYPE_MISC -> {
                if (!GameReminderStore.miscellaniaEnabled(context)) return
                TimerNotifications.show(
                    context,
                    7303,
                    "Manage Miscellania check-in"
                )
                // Receiving a notification does not mean the player checked in.
                GameReminderScheduler.scheduleMiscellania(context)
            }
            GameReminderScheduler.TYPE_NMZ -> {
                if (!GameReminderStore.nmzEnabled(context)) return
                TimerNotifications.show(
                    context, 7304, "NMZ Herb Boxes ready — collect up to 15 today"
                )
                // Schedule the next daily UTC reset independently of the
                // separate Miscellania and Tears Done buttons.
                GameReminderScheduler.scheduleNmz(context)
            }
        }
    }
}


/** Elapsed since the button was actually pressed, not the next alarm. */
internal fun lastDoneText(pressedAt: Long?, now: Long): String {
    if (pressedAt == null) return "Last done: not recorded yet"
    val minutes = ((now - pressedAt).coerceAtLeast(0L) / 60_000L)
    if (minutes == 0L) return "Last done: just now"
    val days = minutes / (24L * 60L)
    val hours = (minutes % (24L * 60L)) / 60L
    val mins = minutes % 60L
    val elapsed = when {
        days > 0L -> "${days}d ${hours}h"
        hours > 0L -> "${hours}h ${mins}m"
        else -> "${mins}m"
    }
    return "Last done: $elapsed ago"
}

@Composable
fun GameRemindersCard(context: Context) {
    var tearsEnabled by remember { mutableStateOf(GameReminderStore.tearsEnabled(context)) }
    var zaffEnabled by remember { mutableStateOf(GameReminderStore.zaffEnabled(context)) }
    var miscellaniaEnabled by remember { mutableStateOf(GameReminderStore.miscellaniaEnabled(context)) }
    var miscellaniaDays by remember { mutableIntStateOf(GameReminderStore.miscellaniaIntervalDays(context)) }
    var tearsLastPressed by remember { mutableStateOf(GameReminderStore.tearsDonePressedAt(context)) }
    var miscLastPressed by remember { mutableStateOf(GameReminderStore.miscellaniaDonePressedAt(context)) }
    var nmzEnabled by remember { mutableStateOf(GameReminderStore.nmzEnabled(context)) }
    var displayTime by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000L)
            displayTime = System.currentTimeMillis()
        }
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xff2e2924)),
        border = BorderStroke(1.dp, Color(0xff8b6722))
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("ACTIVITY & AFK REMINDERS", color = Color(0xffd7a93f), fontWeight = FontWeight.Bold)
            Text(
                "Optional local notifications. No server or account needed.",
                color = Color(0xffd7c7a4),
                fontSize = 12.sp
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Tears of Guthix", color = Color(0xfff4ead4), fontWeight = FontWeight.Bold)
                    Text(
                        "Weekly reminder. Tap “Done today” after you collect tears to anchor the next reminder.",
                        color = Color(0xffd7c7a4),
                        fontSize = 11.sp
                    )
                    Text(
                        lastDoneText(tearsLastPressed, displayTime),
                        color = Color(0xffd7a93f),
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
                Switch(
                    checked = tearsEnabled,
                    onCheckedChange = {
                        tearsEnabled = it
                        GameReminderStore.setTearsEnabled(context, it)
                    }
                )
            }

            if (tearsEnabled) {
                OutlinedButton(
                    onClick = {
                        GameReminderStore.markTearsDoneToday(context)
                        tearsLastPressed = GameReminderStore.tearsDonePressedAt(context)
                        displayTime = System.currentTimeMillis()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Tears of Guthix — Done today")
                }
            }

            Text(
                "DAILY COLLECTIONS • NMZ HERB BOXES + ZAFF",
                color = Color(0xffd7a93f),
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Zaff's battlestaves", color = Color(0xfff4ead4), fontWeight = FontWeight.Bold)
                    Text(
                        "Daily notification when the stock resets at 00:00 UTC.",
                        color = Color(0xffd7c7a4),
                        fontSize = 11.sp
                    )
                }
                Switch(
                    checked = zaffEnabled,
                    onCheckedChange = {
                        zaffEnabled = it
                        GameReminderStore.setZaffEnabled(context, it)
                    }
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Nightmare Zone (NMZ) — Herb Boxes",
                        color = Color(0xfff4ead4),
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Collect up to 15 herb boxes daily. Notification at the 00:00 UTC reset.",
                        color = Color(0xffd7c7a4),
                        fontSize = 11.sp
                    )
                }
                Switch(
                    checked = nmzEnabled,
                    onCheckedChange = {
                        nmzEnabled = it
                        GameReminderStore.setNmzEnabled(context, it)
                    }
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Manage Miscellania", color = Color(0xfff4ead4), fontWeight = FontWeight.Bold)
                    Text(
                        "Remind every " + miscellaniaDays +
                            if (miscellaniaDays == 1) " day since your last check-in." else " days since your last check-in.",
                        color = Color(0xffd7c7a4),
                        fontSize = 11.sp
                    )
                    Text(
                        lastDoneText(miscLastPressed, displayTime),
                        color = Color(0xffd7a93f),
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
                Switch(
                    checked = miscellaniaEnabled,
                    onCheckedChange = {
                        miscellaniaEnabled = it
                        GameReminderStore.setMiscellaniaEnabled(context, it)
                    }
                )
            }

            Text(
                "Manage Miscellania reminder interval: " + miscellaniaDays +
                    if (miscellaniaDays == 1) " day (24 hours)" else " days",
                color = Color(0xffd7c7a4),
                fontSize = 12.sp
            )
            Slider(
                value = miscellaniaDays.toFloat(),
                onValueChange = { fraction ->
                    val days = fraction.roundToInt().coerceIn(1, 7)
                    if (days != miscellaniaDays) {
                        miscellaniaDays = days
                        GameReminderStore.setMiscellaniaIntervalDays(context, days)
                    }
                },
                valueRange = 1f..7f,
                steps = 5,
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("1 day", color = Color(0xffd7c7a4), fontSize = 11.sp)
                Text("7 days", color = Color(0xffd7c7a4), fontSize = 11.sp)
            }

            if (miscellaniaEnabled) {
                OutlinedButton(
                    onClick = {
                        GameReminderStore.markMiscellaniaDoneNow(context)
                        miscLastPressed = GameReminderStore.miscellaniaDonePressedAt(context)
                        displayTime = System.currentTimeMillis()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Manage Miscellania — Done now")
                }
            }
        }
    }
}
