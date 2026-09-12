package com.yourteam.sahara.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.LocalDrink
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yourteam.sahara.model.Reminder
import com.yourteam.sahara.model.ReminderStatus
import com.yourteam.sahara.model.ReminderType

@Composable
fun SaharaReminderCard(
    reminder: Reminder,
    onStatusToggle: (Reminder) -> Unit,
    modifier: Modifier = Modifier
) {
    val icon: ImageVector = when (reminder.type) {
        ReminderType.MEDICINE -> Icons.Default.MedicalServices
        ReminderType.HYDRATION -> Icons.Default.LocalDrink
        ReminderType.COGNITIVE_ACTIVITY -> Icons.Default.Psychology
        ReminderType.APPOINTMENT -> Icons.Default.Event
        ReminderType.GENERAL -> Icons.Default.Event
    }

    SaharaCard(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (reminder.status == ReminderStatus.COMPLETED)
                                MaterialTheme.colorScheme.surfaceVariant
                            else
                                MaterialTheme.colorScheme.primaryContainer
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = reminder.title,
                        tint = if (reminder.status == ReminderStatus.COMPLETED)
                            MaterialTheme.colorScheme.onSurfaceVariant
                        else
                            MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column {
                    Text(
                        text = reminder.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (reminder.status == ReminderStatus.COMPLETED)
                            MaterialTheme.colorScheme.onSurfaceVariant
                        else
                            MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = reminder.scheduledTime,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Status chip & toggle
            Row(verticalAlignment = Alignment.CenterVertically) {
                val chipText = when (reminder.status) {
                    ReminderStatus.UPCOMING -> "Upcoming"
                    ReminderStatus.COMPLETED -> "Completed"
                    ReminderStatus.MISSED -> "Missed"
                }
                val chipBg = when (reminder.status) {
                    ReminderStatus.UPCOMING -> MaterialTheme.colorScheme.tertiaryContainer
                    ReminderStatus.COMPLETED -> MaterialTheme.colorScheme.secondaryContainer
                    ReminderStatus.MISSED -> MaterialTheme.colorScheme.errorContainer
                }
                val chipTextCol = when (reminder.status) {
                    ReminderStatus.UPCOMING -> MaterialTheme.colorScheme.primary
                    ReminderStatus.COMPLETED -> MaterialTheme.colorScheme.primary
                    ReminderStatus.MISSED -> MaterialTheme.colorScheme.onErrorContainer
                }

                SaharaStatusChip(
                    text = chipText,
                    backgroundColor = chipBg,
                    textColor = chipTextCol
                )

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = {
                        val newStatus = if (reminder.status == ReminderStatus.COMPLETED)
                            ReminderStatus.UPCOMING
                        else
                            ReminderStatus.COMPLETED
                        onStatusToggle(reminder.copy(status = newStatus))
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Toggle status",
                        tint = if (reminder.status == ReminderStatus.COMPLETED)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
