package com.yourteam.sahara.ui.components

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.LocalDrink
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yourteam.sahara.R
import com.yourteam.sahara.model.BuiltInReminders
import com.yourteam.sahara.model.Reminder
import com.yourteam.sahara.model.ReminderStatus
import com.yourteam.sahara.model.ReminderType
import com.yourteam.sahara.model.TodayReminder
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/** Built-in reminders are translated until someone edits their title; user titles show exactly as entered. */
@Composable
fun Reminder.localizedTitle(): String =
    BuiltInReminders.localizedTitleRes(this)?.let { stringResource(it) } ?: title

/** Non-Compose version for notifications; [context] must already be in the UI language. */
fun Reminder.localizedTitle(context: Context): String =
    BuiltInReminders.localizedTitleRes(this)?.let { context.getString(it) } ?: title

@Composable
fun reminderTimeText(minuteOfDay: Int): String = formatReminderTime(LocalContext.current, minuteOfDay, currentLocale())

/** Formats a minute of the day using the device's 12/24-hour preference. */
fun formatReminderTime(context: Context, minuteOfDay: Int, locale: Locale): String {
    val pattern = if (DateFormat.is24HourFormat(context)) "H:mm" else "h:mm a"
    val time = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, minuteOfDay / 60)
        set(Calendar.MINUTE, minuteOfDay % 60)
    }.time
    return SimpleDateFormat(pattern, locale).format(time)
}

val ReminderType.icon: ImageVector
    get() = when (this) {
        ReminderType.MEDICINE -> Icons.Default.MedicalServices
        ReminderType.HYDRATION -> Icons.Default.LocalDrink
        ReminderType.COGNITIVE_ACTIVITY -> Icons.Default.Psychology
        ReminderType.APPOINTMENT -> Icons.Default.Event
        ReminderType.GENERAL -> Icons.Default.NotificationsActive
    }

val ReminderType.labelRes: Int
    get() = when (this) {
        ReminderType.MEDICINE -> R.string.type_medicine
        ReminderType.HYDRATION -> R.string.type_hydration
        ReminderType.COGNITIVE_ACTIVITY -> R.string.type_activity
        ReminderType.APPOINTMENT -> R.string.type_appointment
        ReminderType.GENERAL -> R.string.type_general
    }

/** Caregiver view of one reminder: tap to edit, mark today's completion, switch on or off. */
@Composable
fun SaharaReminderCard(
    item: TodayReminder,
    onDoneChange: (Boolean) -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val reminder = item.reminder
    val done = item.status == ReminderStatus.COMPLETED
    val muted = done || !reminder.enabled

    SaharaCard(modifier = modifier.clickable(onClick = onClick)) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val typeLabel = stringResource(reminder.type.labelRes)
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (muted) MaterialTheme.colorScheme.surfaceVariant
                            else MaterialTheme.colorScheme.primaryContainer
                        )
                        .semantics { contentDescription = typeLabel },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = reminder.type.icon,
                        contentDescription = null,
                        tint = if (muted) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = reminder.localizedTitle(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (muted) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = reminderTimeText(reminder.minuteOfDay),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (reminder.description.isNotBlank()) {
                        Text(
                            text = reminder.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val (chipText, chipBg, chipTextCol) = when {
                    !reminder.enabled -> Triple(
                        stringResource(R.string.reminder_off),
                        MaterialTheme.colorScheme.surfaceVariant,
                        MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    item.status == ReminderStatus.COMPLETED -> Triple(
                        stringResource(R.string.reminder_completed),
                        MaterialTheme.colorScheme.secondaryContainer,
                        MaterialTheme.colorScheme.primary
                    )
                    item.status == ReminderStatus.MISSED -> Triple(
                        stringResource(R.string.reminder_missed),
                        MaterialTheme.colorScheme.errorContainer,
                        MaterialTheme.colorScheme.onErrorContainer
                    )
                    else -> Triple(
                        stringResource(R.string.reminder_upcoming),
                        MaterialTheme.colorScheme.tertiaryContainer,
                        MaterialTheme.colorScheme.primary
                    )
                }
                SaharaStatusChip(text = chipText, backgroundColor = chipBg, textColor = chipTextCol)

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconToggleButton(
                        checked = done,
                        onCheckedChange = onDoneChange,
                        enabled = reminder.enabled,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = stringResource(R.string.toggle_reminder_status),
                            tint = if (done) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    val switchLabel = stringResource(R.string.toggle_reminder_enabled)
                    Switch(
                        checked = reminder.enabled,
                        onCheckedChange = onEnabledChange,
                        modifier = Modifier.semantics { contentDescription = switchLabel }
                    )
                }
            }
        }
    }
}

/** Add (initial == null) or edit a daily reminder. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderEditorDialog(
    initial: Reminder?,
    onSave: (Reminder) -> Unit,
    onDelete: (Reminder) -> Unit,
    onDismiss: () -> Unit
) {
    val originalTitle = initial?.localizedTitle() ?: ""
    var title by remember { mutableStateOf(originalTitle) }
    var description by remember { mutableStateOf(initial?.description ?: "") }
    var type by remember { mutableStateOf(initial?.type ?: ReminderType.MEDICINE) }
    var confirmDelete by remember { mutableStateOf(false) }
    val timeState = rememberTimePickerState(
        initialHour = (initial?.minuteOfDay ?: (9 * 60)) / 60,
        initialMinute = (initial?.minuteOfDay ?: 0) % 60,
        is24Hour = DateFormat.is24HourFormat(LocalContext.current)
    )

    if (confirmDelete && initial != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_reminder_confirm)) },
            text = { Text(originalTitle) },
            confirmButton = {
                TextButton(onClick = { onDelete(initial) }) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (initial == null) R.string.add_reminder else R.string.edit_reminder)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.reminder_title_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(R.string.reminder_description_label)) },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(stringResource(R.string.reminder_type_label), style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ReminderType.entries.forEach { option ->
                        FilterChip(
                            selected = option == type,
                            onClick = { type = option },
                            label = { Text(stringResource(option.labelRes)) },
                            leadingIcon = { Icon(option.icon, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        )
                    }
                }
                Text(stringResource(R.string.reminder_time_label), style = MaterialTheme.typography.labelLarge)
                TimeInput(state = timeState)
            }
        },
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank(),
                onClick = {
                    val base = initial ?: Reminder(title = "", minuteOfDay = 0)
                    onSave(
                        base.copy(
                            // Keep an untouched built-in title so it stays translated in every language.
                            title = if (initial != null && title == originalTitle) initial.title else title.trim(),
                            description = description.trim(),
                            type = type,
                            minuteOfDay = timeState.hour * 60 + timeState.minute
                        )
                    )
                }
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            Row {
                if (initial != null) {
                    TextButton(onClick = { confirmDelete = true }) {
                        Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        }
    )
}
