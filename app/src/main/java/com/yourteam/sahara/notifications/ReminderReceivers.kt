package com.yourteam.sahara.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.yourteam.sahara.SaharaApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

/** Runs [block] off the main thread while keeping the broadcast alive until it finishes. */
private fun BroadcastReceiver.runAsync(block: suspend () -> Unit) {
    val pending = goAsync()
    receiverScope.launch {
        try {
            block()
        } finally {
            pending.finish()
        }
    }
}

/** Alarm deliveries and the DONE / SNOOZE notification actions. Not exported: only this app sends these. */
class ReminderAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getStringExtra(AndroidAlarmGateway.EXTRA_REMINDER_ID) ?: return
        val handler = (context.applicationContext as SaharaApplication).reminderNotificationHandler
        when (intent.action) {
            AndroidAlarmGateway.ACTION_ALARM -> {
                val kind = AlarmKind.entries.find { it.name == intent.getStringExtra(AndroidAlarmGateway.EXTRA_KIND) } ?: return
                runAsync { handler.onAlarm(reminderId, kind) }
            }
            ReminderNotifier.ACTION_DONE -> runAsync { handler.onDone(reminderId) }
            ReminderNotifier.ACTION_SNOOZE -> runAsync { handler.onSnooze(reminderId) }
        }
    }
}

/**
 * Alarms are cleared by a reboot and may be wrong after the clock or time zone changes, so all
 * reminders are rescheduled. Only protected system broadcasts are listed, and rescheduling is
 * idempotent, so it is harmless if triggered.
 */
class ReminderRescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in HANDLED_ACTIONS) return
        val handler = (context.applicationContext as SaharaApplication).reminderNotificationHandler
        runAsync { handler.rescheduleAll() }
    }

    private companion object {
        val HANDLED_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED
        )
    }
}
