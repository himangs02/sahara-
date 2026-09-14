package com.yourteam.sahara.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yourteam.sahara.R
import com.yourteam.sahara.data.repository.GameResultRepository
import com.yourteam.sahara.model.CognitiveActivityType
import com.yourteam.sahara.model.Difficulty
import com.yourteam.sahara.ui.components.localizedName
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PerformanceScreen(repository: GameResultRepository, onBackClick: () -> Unit) {
    val history = remember(repository) { repository.getAllGameResults() }
    val results by history.collectAsState(initial = emptyList())
    val completed = results.filter { it.completed }.sortedByDescending { it.timestamp }
    val locale = LocalConfiguration.current.locales[0]
    val dateFormat = remember(locale) {
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, locale)
    }
    Scaffold(topBar = {
        TopAppBar(
            title = { Text(stringResource(R.string.my_progress)) },
            navigationIcon = {
                IconButton(onClick = onBackClick) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                }
            }
        )
    }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.activities_completed), style = MaterialTheme.typography.titleMedium)
                        Text(completed.size.toString(), style = MaterialTheme.typography.headlineLarge)
                        if (completed.isNotEmpty()) {
                            Text(stringResource(R.string.average_accuracy), style = MaterialTheme.typography.titleMedium)
                            Text("${completed.map { it.accuracy }.average().toInt()}%", style = MaterialTheme.typography.headlineMedium)
                        }
                    }
                }
            }
            item { Text(stringResource(R.string.recent_history), style = MaterialTheme.typography.titleLarge) }
            if (completed.isEmpty()) {
                item { Text(stringResource(R.string.no_history), style = MaterialTheme.typography.bodyLarge) }
            }
            items(completed) { result ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(CognitiveActivityType.fromId(result.gameType).titleRes), style = MaterialTheme.typography.titleMedium)
                        Text(dateFormat.format(Date(result.timestamp)))
                        Text(stringResource(R.string.accuracy_value, result.accuracy.toInt()))
                        Text(stringResource(R.string.mistakes_value, result.mistakes))
                        Text(stringResource(R.string.seconds_value, result.completionTimeSeconds))
                        val difficulty = Difficulty.entries.find { it.name == result.difficulty } ?: Difficulty.EASY
                        Text("${stringResource(R.string.level)}: ${difficulty.localizedName()}")
                    }
                }
            }
        }
    }
}
