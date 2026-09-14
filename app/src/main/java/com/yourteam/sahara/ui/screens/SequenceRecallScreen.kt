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
import com.yourteam.sahara.model.SequencePhase
import com.yourteam.sahara.ui.components.ActivityResultView
import com.yourteam.sahara.ui.components.SequenceItemCard
import com.yourteam.sahara.viewmodel.SequenceRecallViewModel

@Composable
fun SequenceRecallScreen(
    onBackToHome: () -> Unit,
    viewModel: SequenceRecallViewModel = viewModel()
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
                activityName = stringResource(R.string.sequence_recall),
                result = viewModel.lastGameResult,
                onPlayAgain = { viewModel.startGame(state.difficulty) },
                onBackToHome = onBackToHome
            )
        } else {
            SequencePlayView(
                state = state,
                onReadyClick = { viewModel.startRecallPhase() },
                onChoiceClick = { viewModel.onChoiceClicked(it) },
                labelFor = { viewModel.labelFor(it) }
            )
        }
    }
}

@Composable
private fun SequencePlayView(
    state: com.yourteam.sahara.viewmodel.SequenceRecallState,
    onReadyClick: () -> Unit,
    onChoiceClick: (com.yourteam.sahara.model.CardIcon) -> Unit,
    labelFor: (com.yourteam.sahara.model.CardIcon) -> Int
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = stringResource(R.string.sequence_recall),
            style = MaterialTheme.typography.displayMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = if (state.phase == SequencePhase.MEMORIZE)
                stringResource(R.string.memorize_instruction)
            else
                stringResource(R.string.recall_instruction),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        if (state.phase == SequencePhase.MEMORIZE) {
            // Memorize View
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(R.string.memorize_sequence),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        state.targetSequence.forEach { icon ->
                            SequenceItemCard(
                                icon = icon,
                                modifier = Modifier.size(64.dp),
                                labelRes = labelFor(icon)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = onReadyClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(stringResource(R.string.ready), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        } else {
            // Recall View
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(R.string.your_answer),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        state.targetSequence.indices.forEach { index ->
                            val selectedIcon = state.userSequence.getOrNull(index)
                            if (selectedIcon != null) {
                                SequenceItemCard(
                                    icon = selectedIcon,
                                    modifier = Modifier.size(56.dp),
                                    isSelected = true,
                                    labelRes = labelFor(selectedIcon)
                                )
                            } else {
                                Card(
                                    modifier = Modifier
                                        .size(56.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                ) {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Text(
                                            text = "${index + 1}",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
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

            // Choice Grid
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(state.shuffledChoices) { choiceIcon ->
                    SequenceItemCard(
                        icon = choiceIcon,
                        onClick = { onChoiceClick(choiceIcon) },
                        labelRes = labelFor(choiceIcon)
                    )
                }
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
