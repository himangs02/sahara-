package com.yourteam.sahara.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.net.toUri

/**
 * AlarmManager-backed alarms. Each reminder/kind pair maps to one PendingIntent identified by its
 * data URI, so setting it again replaces the earlier alarm instead of adding a second one.
 */
class AndroidAlarmGateway(private val context: Context) : AlarmGateway {

    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    override fun schedule(reminderId: String, kind: AlarmKind, triggerAtMillis: Long) {
        val intent = pendingIntent(context, reminderId, kind, PendingIntent.FLAG_UPDATE_CURRENT) ?: return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, intent)
        } else {
            // Without the exact-alarm permission Android may deliver this a few minutes late,
            // but it still fires while the phone is idle.
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, intent)
        }
    }

    override fun cancel(reminderId: String, kind: AlarmKind) {
        pendingIntent(context, reminderId, kind, PendingIntent.FLAG_NO_CREATE)?.let {
            alarmManager.cancel(it)
            it.cancel()
        }
    }

    companion object {
        const val ACTION_ALARM = "com.yourteam.sahara.action.REMINDER_ALARM"
        const val EXTRA_REMINDER_ID = "reminder_id"
        const val EXTRA_KIND = "alarm_kind"

        fun alarmIntent(context: Context, reminderId: String, kind: AlarmKind): Intent =
            Intent(context, ReminderAlarmReceiver::class.java)
                .setAction(ACTION_ALARM)
                .setData("sahara-reminder://alarm/${kind.name.lowercase()}/${Uri.encode(reminderId)}".toUri())
                .putExtra(EXTRA_REMINDER_ID, reminderId)
                .putExtra(EXTRA_KIND, kind.name)

        private fun pendingIntent(context: Context, reminderId: String, kind: AlarmKind, flag: Int): PendingIntent? =
            PendingIntent.getBroadcast(context, 0, alarmIntent(context, reminderId, kind), flag or PendingIntent.FLAG_IMMUTABLE)

        /** True while an alarm of [kind] is pending for the reminder. */
        fun isScheduled(context: Context, reminderId: String, kind: AlarmKind): Boolean =
            pendingIntent(context, reminderId, kind, PendingIntent.FLAG_NO_CREATE) != null
    }
}
