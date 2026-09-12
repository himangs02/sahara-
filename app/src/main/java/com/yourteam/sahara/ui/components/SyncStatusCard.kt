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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yourteam.sahara.sync.SyncState
import com.yourteam.sahara.sync.SyncStatusInfo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SyncStatusCard(
    syncStatusInfo: SyncStatusInfo,
    onSyncNowClick: (Context) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

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
                        text = syncStatusInfo.statusMessage,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = formatLastSyncTime(syncStatusInfo.lastSuccessfulSyncTime),
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
                            "You're offline. Your data is safely saved and will sync automatically when connection returns.",
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        onSyncNowClick(context)
                        Toast.makeText(context, "Synchronization initiated...", Toast.LENGTH_SHORT).show()
                    }
                },
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("SYNC NOW", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

private fun formatLastSyncTime(timestamp: Long): String {
    if (timestamp == 0L) return "Not synced yet"
    val sdf = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
    return "Last synced: ${sdf.format(Date(timestamp))}"
}
