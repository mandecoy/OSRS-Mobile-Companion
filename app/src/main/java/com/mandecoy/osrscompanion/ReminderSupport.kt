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
    private const val TEARS_STARTED_AT = "tears_reminder_enabled_at"
    private const val ZAFF_ENABLED = "zaff_enabled"
    private const val MISC_ENABLED = "misc_enabled"
    private const val MISC_STARTED_AT = "misc_reminder_enabled_at"
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

    /**
     * Enabling a reminder is its only setup action. Remember the start date
     * across app restarts; switching off and back on starts a fresh cycle.
     */
    fun setTearsEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val editor = prefs.edit().putBoolean(TEARS_ENABLED, enabled)
        if (enabled && !prefs.getBoolean(TEARS_ENABLED, false)) {
            editor.putLong(TEARS_STARTED_AT, System.currentTimeMillis())
        }
        editor.apply()
        if (enabled) GameReminderScheduler.scheduleTears(context)
        else GameReminderScheduler.cancelTears(context)
    }

    fun tearsStartedAt(context: Context): Long {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val stored = prefs.getLong(TEARS_STARTED_AT, 0L)
        if (stored > 0L) return stored
        // One-time transition for people upgrading while reminders are on:
        // never use an old Done date to create an unexpected overdue alarm.
        val now = System.currentTimeMillis()
        prefs.edit().putLong(TEARS_STARTED_AT, now).apply()
        return now
    }

    fun setZaffEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(ZAFF_ENABLED, enabled).apply()
        if (enabled) GameReminderScheduler.scheduleZaff(context)
        else GameReminderScheduler.cancelZaff(context)
    }

    fun setMiscellaniaEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val editor = prefs.edit().putBoolean(MISC_ENABLED, enabled)
        if (enabled && !prefs.getBoolean(MISC_ENABLED, false)) {
            editor.putLong(MISC_STARTED_AT, System.currentTimeMillis())
        }
        editor.apply()
        if (enabled) GameReminderScheduler.scheduleMiscellania(context)
        else GameReminderScheduler.cancelMiscellania(context)
    }

    fun miscellaniaStartedAt(context: Context): Long {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val stored = prefs.getLong(MISC_STARTED_AT, 0L)
        if (stored > 0L) return stored
        val now = System.currentTimeMillis()
        prefs.edit().putLong(MISC_STARTED_AT, now).apply()
        return now
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
        // Weekly cycle measured from the time the toggle was enabled.
        // Never rely on old Done timestamps or require a separate check-in.
        scheduleFromStart(
            context, TEARS_REQUEST, TYPE_TEARS,
            GameReminderStore.tearsStartedAt(context), 7L * 24L * 60L * 60L * 1000L
        )
    }

    fun scheduleZaff(context: Context) {
        val nowUtc = ZonedDateTime.now(ZoneOffset.UTC)
        var next = nowUtc.truncatedTo(ChronoUnit.DAYS).plusDays(1)
        if (!next.toInstant().isAfter(Instant.now())) next = next.plusDays(1)
        schedule(context, ZAFF_REQUEST, TYPE_ZAFF, next.toInstant().toEpochMilli())
    }

    fun scheduleMiscellania(context: Context) {
        // Fixed daily reminder (24h from activation). Just one on/off switch.
        scheduleFromStart(
            context, MISC_REQUEST, TYPE_MISC,
            GameReminderStore.miscellaniaStartedAt(context), 24L * 60L * 60L * 1000L
        )
    }

    private fun scheduleFromStart(
        context: Context,
        request: Int,
        type: String,
        startedAt: Long,
        intervalMs: Long
    ) {
        val now = System.currentTimeMillis()
        val elapsed = (now - startedAt).coerceAtLeast(0L)
        val cycles = elapsed / intervalMs + 1L
        val next = startedAt + cycles * intervalMs
        schedule(context, request, type, next)
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
                // Advance automatically; no Done action is needed.
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


@Composable
fun GameRemindersCard(context: Context) {
    var tearsEnabled by remember { mutableStateOf(GameReminderStore.tearsEnabled(context)) }
    var zaffEnabled by remember { mutableStateOf(GameReminderStore.zaffEnabled(context)) }
    var miscellaniaEnabled by remember { mutableStateOf(GameReminderStore.miscellaniaEnabled(context)) }
    var nmzEnabled by remember { mutableStateOf(GameReminderStore.nmzEnabled(context)) }
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
                        "Weekly reminder automatically repeats every 7 days while ON.",
                        color = Color(0xffd7c7a4),
                        fontSize = 11.sp
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
                        "Daily reminder every 24 hours while ON. No check-in required.",
                        color = Color(0xffd7c7a4),
                        fontSize = 11.sp
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

        }
    }
}
