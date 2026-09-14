package com.yourteam.sahara

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.SystemClock
import android.service.notification.StatusBarNotification
import androidx.core.content.ContextCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.yourteam.sahara.language.AppLanguage
import com.yourteam.sahara.model.LocalClock
import com.yourteam.sahara.model.Reminder
import com.yourteam.sahara.notifications.AlarmKind
import com.yourteam.sahara.notifications.AndroidAlarmGateway
import com.yourteam.sahara.notifications.NotificationAccess
import com.yourteam.sahara.notifications.NotificationPermission
import com.yourteam.sahara.notifications.ReminderNotifier
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import java.util.Locale

/**
 * Real notifications on the device. Alarms are delivered by sending the same broadcast AlarmManager
 * would send, so the tests do not wait for wall-clock times. Methods run in name order because the
 * permission test needs the fresh-install state before later tests grant the permission.
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class ReminderNotificationDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context get() = instrumentation.targetContext
    private val app get() = context.applicationContext as SaharaApplication
    private val ui = DeviceUi(instrumentation)
    private val notificationManager get() = context.getSystemService(NotificationManager::class.java)
    private var scenario: ActivityScenario<MainActivity>? = null
    private lateinit var originalLanguage: AppLanguage
    private var originalHydration: Reminder? = null
    private val createdIds = mutableListOf<String>()

    @Before fun remember() {
        instrumentation.runOnMainSync {
            originalLanguage = app.languageManager.currentLanguage.value
            app.languageManager.setLanguage(null, AppLanguage.ENGLISH)
        }
        originalHydration = runBlocking { app.reminderRepository.getReminder("rem_002") }
    }

    @After fun cleanUp() {
        scenario?.close()
        runBlocking {
            createdIds.forEach { id -> app.reminderRepository.getReminder(id)?.let { app.reminderRepository.deleteReminder(it) } }
            originalHydration?.let { app.reminderRepository.insertReminder(it) }
        }
        notificationManager.cancelAll()
        instrumentation.runOnMainSync { app.languageManager.setLanguage(null, originalLanguage) }
    }

    private fun localized(language: AppLanguage): Context {
        val config = Configuration(context.resources.configuration).apply { setLocale(Locale.forLanguageTag(language.code)) }
        return context.createConfigurationContext(config)
    }

    private fun grantNotifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    /** A reminder due this minute, so a delivered alarm is on time. */
    private fun createDueReminder(title: String, description: String = "", enabled: Boolean = true): Reminder {
        val reminder = Reminder(
            id = "notification-test-${SystemClock.uptimeMillis()}",
            title = title,
            description = description,
            minuteOfDay = LocalClock.now().minuteOfDay,
            enabled = enabled
        )
        createdIds += reminder.id
        runBlocking { app.reminderRepository.insertReminder(reminder) }
        return reminder
    }

    private fun deliverAlarm(reminderId: String, kind: AlarmKind = AlarmKind.DAILY) {
        context.sendBroadcast(AndroidAlarmGateway.alarmIntent(context, reminderId, kind))
    }

    private fun findNotification(reminderId: String): StatusBarNotification? =
        notificationManager.activeNotifications.find { it.id == ReminderNotifier.notificationId(reminderId) }

    private fun waitForNotification(reminderId: String, matches: (Notification) -> Boolean = { true }): Notification {
        val deadline = SystemClock.uptimeMillis() + 8_000
        while (SystemClock.uptimeMillis() < deadline) {
            findNotification(reminderId)?.notification?.takeIf(matches)?.let { return it }
            SystemClock.sleep(100)
        }
        throw AssertionError("Notification not posted for $reminderId; active: ${notificationManager.activeNotifications.map { it.notification.extras.getCharSequence(Notification.EXTRA_TITLE) }}")
    }

    private fun waitUntil(description: String, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 8_000
        while (SystemClock.uptimeMillis() < deadline) {
            if (condition()) return
            SystemClock.sleep(100)
        }
        throw AssertionError("Timed out waiting for: $description")
    }

    private val Notification.title get() = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
    private val Notification.text get() = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
    private fun stored(id: String) = runBlocking { app.reminderRepository.getReminder(id) }

    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.TIRAMISU)
    @Test fun a_permissionIsExplainedFirstAndRequestedOnlyOnce() {
        assertEquals(
            "This test needs a fresh install without the notification permission",
            PackageManager.PERMISSION_DENIED,
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
        )
        val s = localized(AppLanguage.ENGLISH)
        TestSession.signInDemo(app)
        scenario = ActivityScenario.launch(MainActivity::class.java)
        ui.clickText(s.getString(R.string.nav_caregiver))
        ui.clickText(s.getString(R.string.view_dashboard))
        ui.scrollUntilVisible(s.getString(R.string.manage_reminders))
        ui.clickText(s.getString(R.string.manage_reminders))

        // The explanation comes before any system prompt.
        ui.waitForText(s.getString(R.string.notification_permission_title))
        ui.waitForText(s.getString(R.string.notification_permission_body))
        assertEquals(NotificationAccess.CAN_ASK, NotificationPermission.access(context))

        ui.clickText(s.getString(R.string.notification_permission_allow))
        ui.clickSystemButton("permission_deny_button", listOf("Don’t allow", "Don't allow"))

        // After a refusal Sahara never shows the system prompt again; it only offers settings.
        ui.waitForText(s.getString(R.string.notification_permission_denied_body))
        assertEquals(NotificationAccess.OPEN_SETTINGS, NotificationPermission.access(context))
        ui.clickText(s.getString(R.string.notification_permission_open_settings))
        ui.waitForPackage("com.android.settings")

        grantNotifications()
        instrumentation.uiAutomation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
        ui.waitForText(s.getString(R.string.add_reminder))
        ui.waitUntilGone(s.getString(R.string.notification_permission_title))
        assertEquals(NotificationAccess.GRANTED, NotificationPermission.access(context))
    }

    @Test fun b_alarmFollowsEnableEditAndDelete() {
        val repository = app.reminderRepository
        val later = (LocalClock.now().minuteOfDay + 30) % (24 * 60)
        val reminder = createDueReminder("Evening tea").copy(minuteOfDay = later)
        runBlocking { repository.insertReminder(reminder) }
        assertTrue(AndroidAlarmGateway.isScheduled(context, reminder.id, AlarmKind.DAILY))

        runBlocking { repository.setEnabled(reminder.id, false) }
        assertFalse(AndroidAlarmGateway.isScheduled(context, reminder.id, AlarmKind.DAILY))
        assertFalse(AndroidAlarmGateway.isScheduled(context, reminder.id, AlarmKind.SNOOZE))

        runBlocking { repository.setEnabled(reminder.id, true) }
        assertTrue(AndroidAlarmGateway.isScheduled(context, reminder.id, AlarmKind.DAILY))

        runBlocking {
            repository.insertReminder(stored(reminder.id)!!.copy(minuteOfDay = (later + 5) % (24 * 60)))
            app.reminderNotificationHandler.rescheduleAll()
        }
        assertTrue(AndroidAlarmGateway.isScheduled(context, reminder.id, AlarmKind.DAILY))

        runBlocking { repository.deleteReminder(stored(reminder.id)!!) }
        assertFalse(AndroidAlarmGateway.isScheduled(context, reminder.id, AlarmKind.DAILY))
    }

    @Test fun c_notificationShowsEnteredContentInTheSelectedLanguage() {
        grantNotifications()
        val en = localized(AppLanguage.ENGLISH)
        val tea = createDueReminder("Evening tea ☕", description = "With biscuits")

        deliverAlarm(tea.id)
        val english = waitForNotification(tea.id)
        assertEquals("🔔 Evening tea ☕", english.title)
        assertEquals("With biscuits", english.text)
        assertEquals(
            listOf(en.getString(R.string.notification_action_done), en.getString(R.string.notification_action_snooze, 10)),
            english.actions.map { it.title.toString() }
        )
        assertEquals(ReminderNotifier.CHANNEL_ID, english.channelId)

        instrumentation.runOnMainSync { app.languageManager.setLanguage(null, AppLanguage.HINDI) }
        val hi = localized(AppLanguage.HINDI)
        notificationManager.cancelAll()
        // Cancelling is asynchronous; make sure the English notification is gone before reposting.
        waitUntil("English notification removed") { findNotification(tea.id) == null }

        // User-entered text stays exactly as typed; the buttons follow the language.
        deliverAlarm(tea.id)
        val hindiDone = hi.getString(R.string.notification_action_done)
        val hindiUser = waitForNotification(tea.id) { it.actions?.firstOrNull()?.title?.toString() == hindiDone }
        assertEquals("🔔 Evening tea ☕", hindiUser.title)
        assertEquals(hi.getString(R.string.notification_action_snooze, 10), hindiUser.actions[1].title.toString())

        // An unedited built-in reminder is translated.
        val hydration = checkNotNull(originalHydration) { "Built-in reminder rem_002 missing" }
        runBlocking {
            app.reminderRepository.insertReminder(
                hydration.copy(minuteOfDay = LocalClock.now().minuteOfDay, enabled = true, lastCompletedEpochDay = Reminder.NOT_COMPLETED)
            )
        }
        deliverAlarm(hydration.id)
        val builtIn = waitForNotification(hydration.id)
        assertEquals("💧 ${hi.getString(R.string.hydration)}", builtIn.title)
        assertNotEquals(en.getString(R.string.hydration), hi.getString(R.string.hydration))
        assertEquals(hi.getString(R.string.notification_channel_name), notificationManager.getNotificationChannel(ReminderNotifier.CHANNEL_ID).name)
    }

    @Test fun d_doneActionCompletesTheReminderForToday() {
        grantNotifications()
        val reminder = createDueReminder("Walk in the garden")
        deliverAlarm(reminder.id)
        val notification = waitForNotification(reminder.id)

        notification.actions[0].actionIntent.send()
        waitUntil("reminder completed") { stored(reminder.id)?.isCompletedOn(LocalClock.now()) == true }
        waitUntil("notification dismissed") { findNotification(reminder.id) == null }
        assertTrue(AndroidAlarmGateway.isScheduled(context, reminder.id, AlarmKind.DAILY))
        assertFalse(AndroidAlarmGateway.isScheduled(context, reminder.id, AlarmKind.SNOOZE))

        // A second alarm today shows nothing, and completion is not duplicated.
        val completedDay = stored(reminder.id)!!.lastCompletedEpochDay
        deliverAlarm(reminder.id)
        SystemClock.sleep(1_500)
        assertNull(findNotification(reminder.id))
        assertEquals(completedDay, stored(reminder.id)!!.lastCompletedEpochDay)
    }

    @Test fun e_snoozeThenDoneCompletesTheOriginalReminder() {
        grantNotifications()
        val en = localized(AppLanguage.ENGLISH)
        val reminder = createDueReminder("Call my daughter")
        deliverAlarm(reminder.id)
        val first = waitForNotification(reminder.id)

        first.actions[1].actionIntent.send()
        val snoozed = waitForNotification(reminder.id) { it.actions?.size == 1 }
        val snoozedPrefix = en.getString(R.string.notification_snoozed, "").substringBefore(" ")
        assertTrue("Unexpected snoozed text: ${snoozed.text}", snoozed.text!!.startsWith(snoozedPrefix))
        assertTrue(AndroidAlarmGateway.isScheduled(context, reminder.id, AlarmKind.SNOOZE))
        assertFalse(stored(reminder.id)!!.isCompletedOn(LocalClock.now()))
        assertEquals(1, runBlocking { app.reminderRepository.getAllReminders() }.count { it.id == reminder.id })

        // The snooze alarm brings the full reminder back with both actions.
        deliverAlarm(reminder.id, AlarmKind.SNOOZE)
        val again = waitForNotification(reminder.id) { it.actions?.size == 2 }

        again.actions[0].actionIntent.send()
        waitUntil("reminder completed") { stored(reminder.id)?.isCompletedOn(LocalClock.now()) == true }
        waitUntil("snooze cancelled") { !AndroidAlarmGateway.isScheduled(context, reminder.id, AlarmKind.SNOOZE) }
        waitUntil("notification dismissed") { findNotification(reminder.id) == null }
    }

    @Test fun f_disabledReminderIsNotScheduledOrShown() {
        grantNotifications()
        val reminder = createDueReminder("Evening prayer")
        runBlocking { app.reminderRepository.setEnabled(reminder.id, false) }
        assertFalse(AndroidAlarmGateway.isScheduled(context, reminder.id, AlarmKind.DAILY))

        deliverAlarm(reminder.id)
        SystemClock.sleep(1_500)
        assertNull(findNotification(reminder.id))
        assertFalse(AndroidAlarmGateway.isScheduled(context, reminder.id, AlarmKind.DAILY))
    }
}
