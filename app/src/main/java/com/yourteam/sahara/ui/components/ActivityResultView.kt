package com.yourteam.sahara.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.yourteam.sahara.R
import com.yourteam.sahara.model.GameResult

@Composable
fun ActivityResultView(
    activityName: String,
    result: GameResult?,
    onPlayAgain: () -> Unit,
    onBackToHome: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.well_done),
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.activity_complete, activityName),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(48.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(modifier = Modifier.padding(32.dp)) {
                ResultStatRow(stringResource(R.string.rounds_pairs), "${result?.matchedPairs ?: 0} / ${result?.totalPairs ?: 0}")
                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
                ResultStatRow(stringResource(R.string.mistakes), "${result?.mistakes ?: 0}")
                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
                ResultStatRow(stringResource(R.string.time), formatTime(result?.completionTimeSeconds ?: 0))
                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
                ResultStatRow(stringResource(R.string.accuracy), "${result?.accuracy?.toInt() ?: 0}%")
            }
        }

        Spacer(modifier = Modifier.height(48.dp))

        Button(
            onClick = onPlayAgain,
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp),
            shape = RoundedCornerShape(20.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Text(stringResource(R.string.play_again), style = MaterialTheme.typography.labelLarge)
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedButton(
            onClick = onBackToHome,
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp),
            shape = RoundedCornerShape(20.dp),
            border = androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        ) {
            Text(stringResource(R.string.back_to_home), style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun ResultStatRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold
        )
    }
}

private fun formatTime(seconds: Long): String {
    val m = seconds / 60
    val s = seconds % 60
    return "${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}"
}
