package com.yourteam.sahara.ui.components

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.yourteam.sahara.R
import com.yourteam.sahara.notifications.NotificationAccess
import com.yourteam.sahara.notifications.NotificationPermission

/**
 * Explains why reminder notifications help before anything is requested. The system prompt is shown
 * at most once; after a refusal the card only offers the settings screen. Hidden once allowed.
 */
@Composable
fun NotificationPermissionCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var access by remember { mutableStateOf(NotificationPermission.access(context)) }

    // Re-check when returning from the system prompt or the settings screen.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) access = NotificationPermission.access(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        access = NotificationPermission.access(context)
    }

    if (access == NotificationAccess.GRANTED) return

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.NotificationsActive,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    stringResource(R.string.notification_permission_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.semantics { heading() }
                )
            }
            Text(
                stringResource(
                    if (access == NotificationAccess.CAN_ASK) R.string.notification_permission_body
                    else R.string.notification_permission_denied_body
                ),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
            Button(
                onClick = {
                    // CAN_ASK only occurs on Android 13+, where the runtime permission exists.
                    if (access == NotificationAccess.CAN_ASK && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        NotificationPermission.markAsked(context)
                        launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        context.startActivity(NotificationPermission.settingsIntent(context))
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(
                    stringResource(
                        if (access == NotificationAccess.CAN_ASK) R.string.notification_permission_allow
                        else R.string.notification_permission_open_settings
                    ),
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }
}
