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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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

object GameReminderStore {
    private const val PREFS = "game_reminders_v1"
    private const val TEARS_ENABLED = "tears_enabled"
    private const val TEARS_LAST_DONE_UTC_DAY = "tears_last_done_utc_day"
    private const val ZAFF_ENABLED = "zaff_enabled"
    private const val MISC_ENABLED = "misc_enabled"
    private const val MISC_LAST_DONE = "misc_last_done"

    fun tearsEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(TEARS_ENABLED, false)

    fun zaffEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(ZAFF_ENABLED, false)

    fun miscellaniaEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(MISC_ENABLED, false)

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
            if (miscellaniaLastDone(context) == null) markMiscellaniaDoneNow(context)
            else GameReminderScheduler.scheduleMiscellania(context)
        } else {
            GameReminderScheduler.cancelMiscellania(context)
        }
    }

    fun markMiscellaniaDoneNow(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putLong(MISC_LAST_DONE, System.currentTimeMillis()).apply()
        if (miscellaniaEnabled(context)) GameReminderScheduler.scheduleMiscellania(context)
    }

    fun miscellaniaLastDone(context: Context): Long? {
        val value = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong(MISC_LAST_DONE, 0L)
        return value.takeIf { it > 0L }
    }

    fun markTearsDoneToday(context: Context) {
        val utcDayStart = ZonedDateTime.now(ZoneOffset.UTC)
            .truncatedTo(ChronoUnit.DAYS)
            .toInstant()
            .toEpochMilli()
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putLong(TEARS_LAST_DONE_UTC_DAY, utcDayStart).apply()
        if (tearsEnabled(context)) GameReminderScheduler.scheduleTears(context)
    }

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
    const val TYPE_TEARS = "tears"
    const val TYPE_ZAFF = "zaff"
    const val TYPE_MISC = "miscellania"

    fun restore(context: Context) {
        if (GameReminderStore.tearsEnabled(context)) scheduleTears(context)
        if (GameReminderStore.zaffEnabled(context)) scheduleZaff(context)
        if (GameReminderStore.miscellaniaEnabled(context)) scheduleMiscellania(context)
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
        val base = GameReminderStore.miscellaniaLastDone(context) ?: System.currentTimeMillis()
        val next = base + 24L * 60L * 60L * 1000L
        schedule(context, MISC_REQUEST, TYPE_MISC, next)
    }

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
                GameReminderStore.markMiscellaniaDoneNow(context)
            }
        }
    }
}


@Composable
fun GameRemindersCard(context: Context) {
    var tearsEnabled by remember { mutableStateOf(GameReminderStore.tearsEnabled(context)) }
    var zaffEnabled by remember { mutableStateOf(GameReminderStore.zaffEnabled(context)) }
    var miscellaniaEnabled by remember { mutableStateOf(GameReminderStore.miscellaniaEnabled(context)) }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xff2e2924)),
        border = BorderStroke(1.dp, Color(0xff8b6722))
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("DAILY & WEEKLY REMINDERS", color = Color(0xffd7a93f), fontWeight = FontWeight.Bold)
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
                    onClick = { GameReminderStore.markTearsDoneToday(context) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Tears of Guthix — Done today")
                }
            }

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
                    Text("Manage Miscellania", color = Color(0xfff4ead4), fontWeight = FontWeight.Bold)
                    Text(
                        "24-hour reminder from your last check-in.",
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

            if (miscellaniaEnabled) {
                OutlinedButton(
                    onClick = { GameReminderStore.markMiscellaniaDoneNow(context) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Manage Miscellania — Done now")
                }
            }
        }
    }
}
