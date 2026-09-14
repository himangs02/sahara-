package com.yourteam.sahara.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.yourteam.sahara.R
import com.yourteam.sahara.model.Reminder
import com.yourteam.sahara.ui.components.NotificationPermissionCard
import com.yourteam.sahara.ui.components.ReminderEditorDialog
import com.yourteam.sahara.ui.components.SaharaReminderCard
import com.yourteam.sahara.viewmodel.CaregiverViewModel

private sealed interface ReminderEditing {
    data object New : ReminderEditing
    data class Existing(val reminder: Reminder) : ReminderEditing
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyRemindersScreen(
    caregiverViewModel: CaregiverViewModel,
    onBackClick: () -> Unit
) {
    val reminders by caregiverViewModel.todayReminders.collectAsState()
    var editing by remember { mutableStateOf<ReminderEditing?>(null) }

    editing?.let { current ->
        ReminderEditorDialog(
            initial = (current as? ReminderEditing.Existing)?.reminder,
            onSave = { caregiverViewModel.saveReminder(it); editing = null },
            onDelete = { caregiverViewModel.deleteReminder(it); editing = null },
            onDismiss = { editing = null }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.daily_reminders), style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        floatingActionButton = {
            val addLabel = stringResource(R.string.add_reminder)
            ExtendedFloatingActionButton(
                onClick = { editing = ReminderEditing.New },
                // The FAB's animated label is not exposed to accessibility services, so name the button.
                modifier = Modifier.semantics { contentDescription = addLabel },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.add_reminder)) },
                containerColor = MaterialTheme.colorScheme.primary
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(paddingValues)
                .padding(20.dp)
        ) {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                // Leave room so the last card isn't hidden behind the Add button.
                contentPadding = PaddingValues(bottom = 88.dp)
            ) {
                item(key = "notification_permission") { NotificationPermissionCard() }
                if (reminders.isEmpty()) {
                    item(key = "empty") {
                        Text(
                            text = stringResource(R.string.no_reminders_today),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                items(reminders, key = { it.reminder.id }) { item ->
                    SaharaReminderCard(
                        item = item,
                        onDoneChange = { done -> caregiverViewModel.setReminderDone(item.reminder, done) },
                        onEnabledChange = { enabled -> caregiverViewModel.setReminderEnabled(item.reminder, enabled) },
                        onClick = { editing = ReminderEditing.Existing(item.reminder) }
                    )
                }
            }
        }
    }
}
