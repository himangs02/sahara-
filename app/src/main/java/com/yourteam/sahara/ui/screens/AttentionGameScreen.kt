package com.yourteam.sahara.ui.screens

import androidx.compose.ui.res.stringResource
import com.yourteam.sahara.R

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yourteam.sahara.model.CognitiveActivityType
import com.yourteam.sahara.ui.components.ActivityResultView
import com.yourteam.sahara.ui.components.AttentionTarget
import com.yourteam.sahara.viewmodel.AttentionGameViewModel

@Composable
fun AttentionGameScreen(
    onBackToHome: () -> Unit,
    viewModel: AttentionGameViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp)
    ) {
        if (state.isGameComplete) {
            ActivityResultView(
                activityName = stringResource(R.string.attention_tap),
                result = viewModel.lastGameResult,
                onPlayAgain = { viewModel.startGame(state.difficulty) },
                onBackToHome = onBackToHome
            )
        } else {
            AttentionPlayView(
                state = state,
                onSymbolClick = { viewModel.onSymbolClicked(it) },
                labelFor = { viewModel.labelFor(it) }
            )
        }
    }
}

@Composable
private fun AttentionPlayView(
    state: com.yourteam.sahara.viewmodel.AttentionGameState,
    onSymbolClick: (com.yourteam.sahara.model.AttentionSymbolItem) -> Unit,
    labelFor: (com.yourteam.sahara.model.CardIcon) -> Int
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = stringResource(R.string.attention_tap),
            style = MaterialTheme.typography.displayMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.attention_instruction),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Target Card Banner
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = stringResource(R.string.find_target),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(12.dp))
                Icon(
                    imageVector = state.targetIcon.imageVector,
                    contentDescription = stringResource(labelFor(state.targetIcon)),
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        if (state.feedbackMessage != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.try_again),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Symbol Grid
        LazyVerticalGrid(
            columns = GridCells.Fixed(state.gridColumns),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(state.gridSymbols) { item ->
                AttentionTarget(
                    item = item,
                    onClick = { onSymbolClick(item) },
                    labelRes = labelFor(item.icon)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Bottom Stats Bar
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatItem(stringResource(R.string.round), "${state.currentRound} / ${state.totalRounds}")
                StatItem(stringResource(R.string.mistakes), "${state.mistakes}")
                StatItem(stringResource(R.string.time), formatTime(state.timeSeconds))
            }
        }
    }
}

@Composable
private fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
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
