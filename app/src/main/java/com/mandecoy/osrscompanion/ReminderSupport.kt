package com.mandecoy.osrscompanion

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.TimePickerDialog
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

object GameReminderStore {
    private const val PREFS = "game_reminders_v1"
    private const val TEARS_ENABLED = "tears_enabled"
    private const val TEARS_STARTED_AT = "tears_reminder_enabled_at"
    private const val ZAFF_ENABLED = "zaff_enabled"
    private const val MISC_ENABLED = "misc_enabled"
    private const val MISC_STARTED_AT = "misc_reminder_enabled_at"
    private const val MISC_INTERVAL_DAYS = "misc_interval_days"
    private const val NMZ_ENABLED = "nmz_enabled"
    private const val TEARS_HOUR = "tears_notification_hour"
    private const val TEARS_MINUTE = "tears_notification_minute"
    private const val MISC_HOUR = "misc_notification_hour"
    private const val MISC_MINUTE = "misc_notification_minute"

    /**
     * Preserve the old local hour/minute for existing enabled reminders.
     * New reminders default to 9 AM until the user picks a time.
     */
    private fun reminderTime(context: Context, hourKey: String, minuteKey: String, startKey: String): LocalTime {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val hour = prefs.getInt(hourKey, -1)
        val minute = prefs.getInt(minuteKey, -1)
        if (hour in 0..23 && minute in 0..59) return LocalTime.of(hour, minute)
        val startedAt = prefs.getLong(startKey, 0L)
        return if (startedAt > 0L) {
            Instant.ofEpochMilli(startedAt).atZone(ZoneId.systemDefault())
                .toLocalTime().withSecond(0).withNano(0)
        } else LocalTime.of(9, 0)
    }

    fun tearsReminderTime(context: Context): LocalTime =
        reminderTime(context, TEARS_HOUR, TEARS_MINUTE, TEARS_STARTED_AT)

    fun miscellaniaReminderTime(context: Context): LocalTime =
        reminderTime(context, MISC_HOUR, MISC_MINUTE, MISC_STARTED_AT)

    fun setTearsReminderTime(context: Context, time: LocalTime) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt(TEARS_HOUR, time.hour).putInt(TEARS_MINUTE, time.minute).apply()
        if (tearsEnabled(context)) GameReminderScheduler.scheduleTears(context)
    }

    fun setMiscellaniaReminderTime(context: Context, time: LocalTime) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt(MISC_HOUR, time.hour).putInt(MISC_MINUTE, time.minute).apply()
        if (miscellaniaEnabled(context)) GameReminderScheduler.scheduleMiscellania(context)
    }

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

    /** 1–7 day reminder preference survives upgrades from the older app. */
    fun miscellaniaIntervalDays(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(MISC_INTERVAL_DAYS, 1).coerceIn(1, 7)

    fun setMiscellaniaIntervalDays(context: Context, days: Int) {
        val selected = days.coerceIn(1, 7)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt(MISC_INTERVAL_DAYS, selected).apply()
        // Preserve the original activation date; changing the interval
        // simply reschedules the next automatic reminder.
        if (miscellaniaEnabled(context)) GameReminderScheduler.scheduleMiscellania(context)
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

/**
 * Calendar-day recurrence keeps the chosen local wall-clock time even when
 * daylight-saving time changes; fixed 24-hour durations would drift.
 */
internal object LocalReminderSchedule {
    fun nextTriggerMillis(
        startedAtMillis: Long,
        intervalDays: Int,
        time: LocalTime,
        nowMillis: Long,
        zone: ZoneId
    ): Long {
        require(intervalDays in 1..7)
        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        val firstDate = Instant.ofEpochMilli(startedAtMillis).atZone(zone)
            .toLocalDate().plusDays(intervalDays.toLong())
        val daysSinceFirst = ChronoUnit.DAYS.between(firstDate, today)
        val completedPeriods = (daysSinceFirst / intervalDays).coerceAtLeast(0L)
        var date = firstDate.plusDays(completedPeriods * intervalDays)
        var candidate = date.atTime(time).atZone(zone).toInstant()
        if (!candidate.isAfter(Instant.ofEpochMilli(nowMillis))) {
            date = date.plusDays(intervalDays.toLong())
            candidate = date.atTime(time).atZone(zone).toInstant()
        }
        return candidate.toEpochMilli()
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

    /** Next weekly Tears alarm, retaining the original activation day and local time. */
    fun nextTearsAt(context: Context, now: Long = System.currentTimeMillis()): Long =
        LocalReminderSchedule.nextTriggerMillis(
            GameReminderStore.tearsStartedAt(context), 7,
            GameReminderStore.tearsReminderTime(context),
            now, ZoneId.systemDefault()
        )

    fun scheduleTears(context: Context) {
        schedule(context, TEARS_REQUEST, TYPE_TEARS, nextTearsAt(context))
    }

    fun scheduleZaff(context: Context) {
        val nowUtc = ZonedDateTime.now(ZoneOffset.UTC)
        var next = nowUtc.truncatedTo(ChronoUnit.DAYS).plusDays(1)
        if (!next.toInstant().isAfter(Instant.now())) next = next.plusDays(1)
        schedule(context, ZAFF_REQUEST, TYPE_ZAFF, next.toInstant().toEpochMilli())
    }

    /** Next Miscellania alarm using the saved 1–7 day interval, never resetting it. */
    fun nextMiscellaniaAt(context: Context, now: Long = System.currentTimeMillis()): Long =
        LocalReminderSchedule.nextTriggerMillis(
            GameReminderStore.miscellaniaStartedAt(context),
            GameReminderStore.miscellaniaIntervalDays(context),
            GameReminderStore.miscellaniaReminderTime(context),
            now, ZoneId.systemDefault()
        )

    fun scheduleMiscellania(context: Context) {
        schedule(context, MISC_REQUEST, TYPE_MISC, nextMiscellaniaAt(context))
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
        // Android may revoke the exact-alarm permission (e.g. after a
        // device/app settings change) and cancel all previously exact alarms.
        // Never crash or silently drop recurring reminders in this case.
        val exactAllowed = android.os.Build.VERSION.SDK_INT < 31 ||
            manager.canScheduleExactAlarms()
        if (exactAllowed) {
            try {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pending)
            } catch (_: SecurityException) {
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pending)
            }
        } else {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pending)
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
                // First queue next week's alert. Posting can be blocked by
                // the OS/channel and must never break recurrence.
                GameReminderScheduler.scheduleTears(context)
                val posted = TimerNotifications.show(
                    context, 7301, "Tears of Guthix is ready"
                )
                ReminderDeliveryLog.record(context, GameReminderScheduler.TYPE_TEARS, posted)
            }
            GameReminderScheduler.TYPE_ZAFF -> {
                if (!GameReminderStore.zaffEnabled(context)) return
                GameReminderScheduler.scheduleZaff(context)
                TimerNotifications.show(context, 7302, "Zaff's battlestaves have reset")
            }
            GameReminderScheduler.TYPE_MISC -> {
                if (!GameReminderStore.miscellaniaEnabled(context)) return
                // Advance automatically; no Done action is needed.
                GameReminderScheduler.scheduleMiscellania(context)
                val posted = TimerNotifications.show(
                    context, 7303, "Manage Miscellania check-in"
                )
                ReminderDeliveryLog.record(context, GameReminderScheduler.TYPE_MISC, posted)
            }
            GameReminderScheduler.TYPE_NMZ -> {
                if (!GameReminderStore.nmzEnabled(context)) return
                // Schedule the next daily UTC reset independently of
                // the separate Miscellania and Tears reminders.
                GameReminderScheduler.scheduleNmz(context)
                TimerNotifications.show(
                    context, 7304, "NMZ Herb Boxes ready — collect up to 15 today"
                )
            }
        }
    }
}


@Composable
fun GameRemindersCard(context: Context) {
    var tearsEnabled by remember { mutableStateOf(GameReminderStore.tearsEnabled(context)) }
    var tearsTime by remember { mutableStateOf(GameReminderStore.tearsReminderTime(context)) }
    var miscellaniaTime by remember { mutableStateOf(GameReminderStore.miscellaniaReminderTime(context)) }
    var zaffEnabled by remember { mutableStateOf(GameReminderStore.zaffEnabled(context)) }
    var miscellaniaEnabled by remember { mutableStateOf(GameReminderStore.miscellaniaEnabled(context)) }
    var miscellaniaDays by remember { mutableIntStateOf(GameReminderStore.miscellaniaIntervalDays(context)) }
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
                        "Weekly reminder repeats every 7 days at your chosen time while ON.",
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

            ReminderTimeInput(context, "Tears reminder time", tearsTime) {
                tearsTime = it
                GameReminderStore.setTearsReminderTime(context, it)
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
                        "Automatic reminder every " + miscellaniaDays +
                            if (miscellaniaDays == 1) " day while ON. No check-in needed."
                            else " days while ON. No check-in needed.",
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
            ReminderTimeInput(context, "Miscellania reminder time", miscellaniaTime) {
                miscellaniaTime = it
                GameReminderStore.setMiscellaniaReminderTime(context, it)
            }
            Text(
                "Manage Miscellania interval: " + miscellaniaDays +
                    (if (miscellaniaDays == 1) " day" else " days"),
                color = Color(0xffd7c7a4),
                fontSize = 12.sp
            )
            Slider(
                value = miscellaniaDays.toFloat(),
                onValueChange = { v ->
                    val chosen = v.roundToInt().coerceIn(1, 7)
                    if (chosen != miscellaniaDays) {
                        miscellaniaDays = chosen
                        GameReminderStore.setMiscellaniaIntervalDays(context, chosen)
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
            ReminderHealthPanel(context, tearsEnabled, miscellaniaEnabled, miscellaniaDays,
                tearsTime, miscellaniaTime)
        }
    }
}


/** Tap the time field to open the phone's AM/PM or 24-hour time picker. */
@Composable
private fun ReminderTimeInput(
    context: Context,
    label: String,
    time: LocalTime,
    onTimeSelected: (LocalTime) -> Unit
) {
    val is24Hour = android.text.format.DateFormat.is24HourFormat(context)
    val formatter = remember(is24Hour) {
        DateTimeFormatter.ofPattern(if (is24Hour) "HH:mm" else "h:mm a")
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = Color(0xffd7c7a4), fontSize = 12.sp, modifier = Modifier.weight(1f))
        OutlinedButton(
            onClick = {
                TimePickerDialog(
                    context,
                    { _, hour, minute -> onTimeSelected(LocalTime.of(hour, minute)) },
                    time.hour,
                    time.minute,
                    is24Hour
                ).show()
            }
        ) {
            Text(time.format(formatter))
        }
    }
}
