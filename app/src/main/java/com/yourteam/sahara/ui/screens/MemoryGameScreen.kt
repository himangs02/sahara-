package com.yourteam.sahara.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yourteam.sahara.R
import com.yourteam.sahara.model.Difficulty
import com.yourteam.sahara.ui.components.MemoryCard
import com.yourteam.sahara.viewmodel.MemoryGameViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoryGameScreen(
    onBackToHome: () -> Unit,
    viewModel: MemoryGameViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.memory_match),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackToHome) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.startGame(state.difficulty) }) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.restart))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(paddingValues)
                .padding(horizontal = 24.dp, vertical = 12.dp)
        ) {
            if (state.isGameComplete) {
                GameResultView(
                    viewModel = viewModel,
                    onPlayAgain = { viewModel.startGame(Difficulty.EASY) },
                    onBackToHome = onBackToHome
                )
            } else {
                GamePlayView(
                    state = state,
                    onCardClick = { viewModel.onCardClicked(it) },
                    labelFor = { viewModel.labelFor(it) }
                )
            }
        }
    }
}

@Composable
private fun GamePlayView(
    state: com.yourteam.sahara.viewmodel.MemoryGameState,
    onCardClick: (com.yourteam.sahara.model.MemoryCardModel) -> Unit,
    labelFor: (com.yourteam.sahara.model.CardIcon) -> Int
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Level & Stars Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${stringResource(R.string.level)} ${if (state.difficulty == Difficulty.EASY) 1 else if (state.difficulty == Difficulty.MEDIUM) 2 else 3}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(3) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.find_matching_pictures),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Card Grid
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(state.cards) { card ->
                MemoryCard(
                    card = card,
                    onClick = { onCardClick(card) },
                    labelRes = labelFor(card.icon)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Stats Footer Bar
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            shape = RoundedCornerShape(20.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatItem(stringResource(R.string.pairs), "${state.pairsFound}/${state.difficulty.pairs}")
                StatItem(stringResource(R.string.mistakes), "${state.mistakes}")
                StatItem(stringResource(R.string.time), formatTime(state.timeSeconds))
            }
        }
    }
}

@Composable
private fun GameResultView(
    viewModel: MemoryGameViewModel,
    onPlayAgain: () -> Unit,
    onBackToHome: () -> Unit
) {
    val result = viewModel.lastGameResult

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.well_done),
            style = MaterialTheme.typography.displayMedium,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.activity_complete, stringResource(R.string.memory_match)),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                ResultStatRow(stringResource(R.string.pairs), "${result?.matchedPairs ?: 0} / ${result?.totalPairs ?: 0}")
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                ResultStatRow(stringResource(R.string.mistakes), "${result?.mistakes ?: 0}")
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                ResultStatRow(stringResource(R.string.time), formatTime(result?.completionTimeSeconds ?: 0))
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                ResultStatRow(stringResource(R.string.accuracy), "${result?.accuracy?.toInt() ?: 0}%")
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onPlayAgain,
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp),
            shape = RoundedCornerShape(30.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Text(stringResource(R.string.play_again), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = onBackToHome,
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp),
            shape = RoundedCornerShape(30.dp)
        ) {
            Text(stringResource(R.string.back_to_home), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold
        )
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
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
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
