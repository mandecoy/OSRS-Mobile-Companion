package com.mandecoy.osrscompanion

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject

data class TimerEntry(
    val id: Int,
    val label: String,
    val endAtMillis: Long,
    val durationSeconds: Long
) {
    companion object {
        fun create(label: String, seconds: Long): TimerEntry {
            val now = System.currentTimeMillis()
            val id = ((now xor label.hashCode().toLong()) and 0x7fffffff).toInt()
            return TimerEntry(id, label, now + seconds * 1000L, seconds)
        }
    }
}

object TimerStore {
    private const val PREFS = "osrs_timers"
    private const val KEY = "active"

    fun all(context: Context): List<TimerEntry> {
        return try {
            val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY, "[]") ?: "[]"
            val arr = JSONArray(raw)
            List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                TimerEntry(
                    o.getInt("id"),
                    o.getString("label"),
                    o.getLong("end"),
                    o.getLong("duration")
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun active(context: Context): List<TimerEntry> =
        all(context).filter { it.endAtMillis > System.currentTimeMillis() }
            .sortedBy { it.endAtMillis }

    fun add(context: Context, timer: TimerEntry) {
        save(context, all(context).filterNot { it.id == timer.id } + timer)
    }

    fun remove(context: Context, id: Int) {
        save(context, all(context).filterNot { it.id == id })
    }

    private fun save(context: Context, timers: List<TimerEntry>) {
        val arr = JSONArray()
        timers.forEach {
            arr.put(
                JSONObject()
                    .put("id", it.id)
                    .put("label", it.label)
                    .put("end", it.endAtMillis)
                    .put("duration", it.durationSeconds)
            )
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, arr.toString()).apply()
    }
}

object TimerScheduler {
    fun schedule(context: Context, timer: TimerEntry) {
        val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pending = pendingIntent(context, timer)
        if (Build.VERSION.SDK_INT >= 31 && !manager.canScheduleExactAlarms()) {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timer.endAtMillis, pending)
        } else {
            manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timer.endAtMillis, pending)
        }
    }

    fun cancel(context: Context, timer: TimerEntry) {
        val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        manager.cancel(pendingIntent(context, timer))
    }

    private fun pendingIntent(context: Context, timer: TimerEntry): PendingIntent {
        val intent = Intent(context, TimerAlarmReceiver::class.java)
            .putExtra("id", timer.id)
            .putExtra("label", timer.label)
        return PendingIntent.getBroadcast(
            context,
            timer.id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}

object TimerNotifications {
    private const val CHANNEL = "osrs_timers"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                CHANNEL,
                "OSRS Timers",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "AFK and farming timer alerts"
                enableVibration(true)
            }
            context.getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    fun show(context: Context, id: Int, label: String) {
        ensureChannel(context)
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val contentIntent = launch?.let {
            PendingIntent.getActivity(
                context,
                id,
                it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(label)
            .setContentText("Your OSRS timer is ready.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setVibrate(longArrayOf(0, 250, 150, 250))
            .setContentIntent(contentIntent)
            .build()

        if (Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            NotificationManagerCompat.from(context).notify(id, notification)
        }
    }
}

class TimerAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra("id", 0)
        val label = intent.getStringExtra("label") ?: "OSRS timer"
        TimerNotifications.show(context, id, label)
        TimerStore.remove(context, id)
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (
            intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_LOCKED_BOOT_COMPLETED
        ) {
            TimerStore.active(context).forEach { TimerScheduler.schedule(context, it) }
        }
    }
}
