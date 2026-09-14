package com.yourteam.sahara.ui.components

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yourteam.sahara.R
import com.yourteam.sahara.sync.SyncState
import com.yourteam.sahara.sync.SyncStatusInfo
import java.text.SimpleDateFormat
import java.util.Date


@Composable
fun SyncStatusCard(
    syncStatusInfo: SyncStatusInfo,
    onSyncNowClick: (Context) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val offlineMessage = stringResource(R.string.sync_offline_toast)
    val syncStartedMessage = stringResource(R.string.sync_started)

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = when (syncStatusInfo.state) {
                SyncState.SYNCED -> MaterialTheme.colorScheme.secondaryContainer
                SyncState.OFFLINE -> MaterialTheme.colorScheme.surfaceVariant
                SyncState.PENDING -> MaterialTheme.colorScheme.primaryContainer
                SyncState.SYNCING -> MaterialTheme.colorScheme.primaryContainer
                SyncState.FAILED -> MaterialTheme.colorScheme.errorContainer
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = when (syncStatusInfo.state) {
                        SyncState.SYNCED -> Icons.Default.CloudDone
                        SyncState.OFFLINE -> Icons.Default.CloudOff
                        SyncState.PENDING -> Icons.Default.Sync
                        SyncState.SYNCING -> Icons.Default.Sync
                        SyncState.FAILED -> Icons.Default.Warning
                    },
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = when (syncStatusInfo.state) {
                        SyncState.SYNCED -> MaterialTheme.colorScheme.onSecondaryContainer
                        SyncState.FAILED -> MaterialTheme.colorScheme.onErrorContainer
                        else -> MaterialTheme.colorScheme.primary
                    }
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = syncStatusText(syncStatusInfo),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (syncStatusInfo.lastSuccessfulSyncTime == 0L) {
                            stringResource(R.string.sync_never)
                        } else {
                            val format = SimpleDateFormat("MMM d, h:mm a", currentLocale())
                            stringResource(R.string.sync_last, format.format(Date(syncStatusInfo.lastSuccessfulSyncTime)))
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            OutlinedButton(
                onClick = {
                    if (syncStatusInfo.state == SyncState.OFFLINE) {
                        Toast.makeText(
                            context,
                            offlineMessage,
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        onSyncNowClick(context)
                        Toast.makeText(context, syncStartedMessage, Toast.LENGTH_SHORT).show()
                    }
                },
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(stringResource(R.string.sync_now), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun syncStatusText(info: SyncStatusInfo): String {
    val resources = LocalResources.current
    return when (info.state) {
        SyncState.SYNCED -> stringResource(R.string.sync_synced)
        SyncState.PENDING -> resources.getQuantityString(R.plurals.sync_pending, info.pendingCount, info.pendingCount)
        SyncState.SYNCING -> stringResource(R.string.sync_syncing)
        SyncState.OFFLINE -> if (info.pendingCount > 0) {
            resources.getQuantityString(R.plurals.sync_saved_locally, info.pendingCount, info.pendingCount)
        } else stringResource(R.string.sync_offline)
        SyncState.FAILED -> stringResource(R.string.sync_failed)
    }
}