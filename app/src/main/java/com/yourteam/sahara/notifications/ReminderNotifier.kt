package com.yourteam.sahara.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.yourteam.sahara.MainActivity
import com.yourteam.sahara.R
import com.yourteam.sahara.model.Reminder
import com.yourteam.sahara.model.ReminderType
import com.yourteam.sahara.ui.components.formatReminderTime
import com.yourteam.sahara.ui.components.labelRes
import com.yourteam.sahara.ui.components.localizedTitle
import java.util.Calendar
import java.util.Locale

/**
 * Posts reminder notifications in the app's selected language. The text only repeats what the
 * caregiver entered (title, note, type and time); Sahara never adds medical instructions.
 */
class ReminderNotifier(
    private val context: Context,
    private val languageCode: () -> String
) : ReminderNotificationDisplay {

    private fun localized(): Context {
        val config = Configuration(context.resources.configuration)
        config.setLocale(Locale.forLanguageTag(languageCode()))
        return context.createConfigurationContext(config)
    }

    /** One fixed channel; calling again only refreshes its name after a language change. */
    fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val strings = localized()
        val channel = NotificationChannel(CHANNEL_ID, strings.getString(R.string.notification_channel_name), NotificationManager.IMPORTANCE_HIGH)
        channel.description = strings.getString(R.string.notification_channel_description)
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    override fun show(reminder: Reminder) {
        val strings = localized()
        val time = formatReminderTime(strings, reminder.minuteOfDay, strings.resources.configuration.locales[0])
        val details = strings.getString(R.string.notification_details, strings.getString(reminder.type.labelRes), time)
        val body = reminder.description.ifBlank { details }

        val builder = baseBuilder(strings, reminder)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .addAction(doneAction(strings, reminder))
            .addAction(
                R.drawable.ic_stat_reminder,
                strings.getString(R.string.notification_action_snooze, ReminderScheduler.SNOOZE_MINUTES),
                actionIntent(reminder.id, ACTION_SNOOZE)
            )
        // The note is the main text when present, so keep type and time visible in the header.
        if (reminder.description.isNotBlank()) builder.setSubText(details)
        post(reminder.id, builder)
    }

    override fun showSnoozed(reminder: Reminder, untilMillis: Long) {
        val strings = localized()
        val until = Calendar.getInstance().apply { timeInMillis = untilMillis }
        val time = formatReminderTime(strings, until.get(Calendar.HOUR_OF_DAY) * 60 + until.get(Calendar.MINUTE), strings.resources.configuration.locales[0])
        val body = strings.getString(R.string.notification_snoozed, time)
        val builder = baseBuilder(strings, reminder)
            .setContentText(body)
            .setSilent(true)
            .addAction(doneAction(strings, reminder))
        post(reminder.id, builder)
    }

    override fun cancel(reminderId: String) {
        NotificationManagerCompat.from(context).cancel(notificationId(reminderId))
    }

    private fun baseBuilder(strings: Context, reminder: Reminder) =
        NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_reminder)
            .setContentTitle("${emoji(reminder.type)} ${reminder.localizedTitle(strings)}")
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent())

    private fun doneAction(strings: Context, reminder: Reminder) =
        NotificationCompat.Action(R.drawable.ic_stat_done, strings.getString(R.string.notification_action_done), actionIntent(reminder.id, ACTION_DONE))

    private fun post(reminderId: String, builder: NotificationCompat.Builder) {
        // Without permission Android drops the notification; the reminder still shows in the app.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        ensureChannel()
        NotificationManagerCompat.from(context).notify(notificationId(reminderId), builder.build())
    }

    private fun actionIntent(reminderId: String, action: String): PendingIntent {
        val intent = Intent(context, ReminderAlarmReceiver::class.java)
            .setAction(action)
            .setData("sahara-reminder://action/${Uri.encode(reminderId)}".toUri())
            .putExtra(AndroidAlarmGateway.EXTRA_REMINDER_ID, reminderId)
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun openAppIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    companion object {
        const val CHANNEL_ID = "sahara_reminders"
        const val ACTION_DONE = "com.yourteam.sahara.action.REMINDER_DONE"
        const val ACTION_SNOOZE = "com.yourteam.sahara.action.REMINDER_SNOOZE"

        /** Stable per reminder, so a snoozed or repeated reminder replaces its own notification. */
        fun notificationId(reminderId: String) = "reminder:$reminderId".hashCode()

        fun emoji(type: ReminderType) = when (type) {
            ReminderType.MEDICINE -> "💊"
            ReminderType.HYDRATION -> "💧"
            ReminderType.COGNITIVE_ACTIVITY -> "🧩"
            ReminderType.APPOINTMENT -> "📅"
            ReminderType.GENERAL -> "🔔"
        }
    }
}
