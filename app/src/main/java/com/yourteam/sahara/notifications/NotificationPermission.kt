package com.yourteam.sahara.notifications

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit

enum class NotificationAccess {
    GRANTED,
    /** Android 13+ and Sahara has never asked: explain, then show the system prompt once. */
    CAN_ASK,
    /** Asked before and refused, or turned off in settings: only offer the settings screen. */
    OPEN_SETTINGS
}

object NotificationPermission {
    private const val PREFS = "sahara_notification_prefs"
    private const val KEY_ASKED = "post_notifications_requested"

    /** Pure decision so it can be tested without a device. */
    fun resolve(sdkInt: Int, permissionGranted: Boolean, notificationsEnabled: Boolean, askedBefore: Boolean): NotificationAccess {
        val needsRuntimePermission = sdkInt >= Build.VERSION_CODES.TIRAMISU
        return when {
            notificationsEnabled && (!needsRuntimePermission || permissionGranted) -> NotificationAccess.GRANTED
            needsRuntimePermission && !permissionGranted && !askedBefore -> NotificationAccess.CAN_ASK
            else -> NotificationAccess.OPEN_SETTINGS
        }
    }

    fun access(context: Context): NotificationAccess = resolve(
        sdkInt = Build.VERSION.SDK_INT,
        permissionGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED,
        notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled(),
        askedBefore = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ASKED, false)
    )

    /** Recorded before the prompt is shown, so Sahara never shows the system prompt a second time. */
    fun markAsked(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putBoolean(KEY_ASKED, true) }
    }

    fun settingsIntent(context: Context): Intent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.fromParts("package", context.packageName, null))
        }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
