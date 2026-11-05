package com.mandecoy.osrscompanion

import android.app.AlarmManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
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
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * A local diagnostic log only. A successful post means the Android
 * NotificationManager accepted the notification, not that the user saw it.
 * Separate from activity activation times, so no timer cycle is reset.
 */
object ReminderDeliveryLog {
    private const val PREFS = "osrs_game_reminder_delivery_v1"
    data class Attempt(val firedAt: Long, val posted: Boolean)

    fun record(context: Context, type: String, posted: Boolean) {
        val now = System.currentTimeMillis()
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong(type + "_fired", now)
            .putBoolean(type + "_posted", posted)
            .apply()
    }

    fun last(context: Context, type: String): Attempt? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val fired = prefs.getLong(type + "_fired", 0L)
        if (fired <= 0L) return null
        return Attempt(fired, prefs.getBoolean(type + "_posted", false))
    }
}

private val alertDateFormatter = DateTimeFormatter.ofPattern("EEE, MMM d 'at' h:mm a")

private fun formattedReminderTime(at: Long): String =
    Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()).format(alertDateFormatter)

private fun openCompanionSettings(context: Context, notificationSettings: Boolean) {
    val intent = if (notificationSettings) {
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    } else {
        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
            Uri.parse("package:" + context.packageName))
    }
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:" + context.packageName)))
    } catch (_: SecurityException) {
        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:" + context.packageName)))
    }
}

@Composable
fun ReminderHealthPanel(
    context: Context,
    tearsEnabled: Boolean,
    miscellaniaEnabled: Boolean,
    miscellaniaDays: Int,
    tearsTime: LocalTime,
    miscellaniaTime: LocalTime
) {
    var result by remember { mutableStateOf("") }
    val reason = TimerNotifications.blockedReason(context)
    val alarms = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val precise = Build.VERSION.SDK_INT < 31 || alarms.canScheduleExactAlarms()
    val gold = Color(0xffd7a93f)
    val parchment = Color(0xffd7c7a4)
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xff24201c)),
        border = BorderStroke(1.dp, Color(0xff8b6722))
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("REMINDER STATUS & REPAIR", color = gold, fontWeight = FontWeight.Bold)
            if (reason != null) {
                Text("Notifications blocked: " + reason,
                    color = Color(0xffffb39c), fontSize = 12.sp)
                OutlinedButton(onClick = { openCompanionSettings(context, true) }) {
                    Text("Open notification settings", color = gold)
                }
            } else {
                Text("OSRS timer notification channel enabled.", color = parchment, fontSize = 12.sp)
            }
            if (!precise) {
                Text("Precise alarms disabled. Android can delay recurring reminders.",
                    color = Color(0xffffb39c), fontSize = 12.sp)
                OutlinedButton(onClick = { openCompanionSettings(context, false) }) {
                    Text("Open precise-alarm setting", color = gold)
                }
            }
            if (tearsEnabled) {
                // Parameters intentionally track edits to the saved times.
                Text("Tears (weekly, " + tearsTime.format(DateTimeFormatter.ofPattern("h:mm a")) +
                        "): next " + formattedReminderTime(GameReminderScheduler.nextTearsAt(context)),
                    color = parchment, fontSize = 12.sp)
                ReminderPreviousAttempt(context, GameReminderScheduler.TYPE_TEARS)
            } else {
                Text("Tears of Guthix: OFF", color = parchment, fontSize = 12.sp)
            }
            if (miscellaniaEnabled) {
                Text("Miscellania (every " + miscellaniaDays + " day(s), " +
                        miscellaniaTime.format(DateTimeFormatter.ofPattern("h:mm a")) +
                        "): next " + formattedReminderTime(GameReminderScheduler.nextMiscellaniaAt(context)),
                    color = parchment, fontSize = 12.sp)
                ReminderPreviousAttempt(context, GameReminderScheduler.TYPE_MISC)
            } else {
                Text("Manage Miscellania: OFF", color = parchment, fontSize = 12.sp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    result = runCatching {
                        GameReminderScheduler.restore(context)
                        "Enabled reminders re-armed without changing their dates."
                    }.getOrElse { "Unable to schedule reminders: " + (it.message ?: "unknown error") }
                }, modifier = Modifier.weight(1f)) {
                    Text("Repair schedules", fontSize = 12.sp, color = gold)
                }
                OutlinedButton(onClick = {
                    val posted = TimerNotifications.show(context, 7399, "OSRS reminder test")
                    result = if (posted) "Test alert sent to Android. Check your notification tray."
                        else "Test blocked: " + (TimerNotifications.blockedReason(context) ?: "check Android settings")
                }, modifier = Modifier.weight(1f)) {
                    Text("Test notification", fontSize = 12.sp, color = gold)
                }
            }
            if (result.isNotBlank()) Text(result, color = parchment, fontSize = 11.sp)
            Text(
                "If reminders still don't arrive, set OSRS Companion Tool's battery usage to " +
                    "Unrestricted in Android app settings. Deep sleeping and force-stopping can " +
                    "stop alarms until you open the app again.",
                color = parchment, fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun ReminderPreviousAttempt(context: Context, type: String) {
    val last = ReminderDeliveryLog.last(context, type) ?: return
    Text(
        "Last triggered: " + formattedReminderTime(last.firedAt) +
            if (last.posted) " (sent to Android)" else " (notification blocked)",
        color = Color(0xffd7c7a4), fontSize = 11.sp
    )
}
