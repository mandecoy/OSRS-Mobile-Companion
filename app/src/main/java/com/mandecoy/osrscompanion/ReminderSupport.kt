package com.mandecoy.osrscompanion

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.time.Instant
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

object GameReminderStore {
    private const val PREFS = "game_reminders_v1"
    private const val TEARS_ENABLED = "tears_enabled"
    private const val TEARS_LAST_DONE_UTC_DAY = "tears_last_done_utc_day"
    private const val ZAFF_ENABLED = "zaff_enabled"

    fun tearsEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(TEARS_ENABLED, false)

    fun zaffEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(ZAFF_ENABLED, false)

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
    const val TYPE_TEARS = "tears"
    const val TYPE_ZAFF = "zaff"

    fun restore(context: Context) {
        if (GameReminderStore.tearsEnabled(context)) scheduleTears(context)
        if (GameReminderStore.zaffEnabled(context)) scheduleZaff(context)
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

    fun cancelTears(context: Context) = cancel(context, TEARS_REQUEST, TYPE_TEARS)
    fun cancelZaff(context: Context) = cancel(context, ZAFF_REQUEST, TYPE_ZAFF)

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
        }
    }
}
