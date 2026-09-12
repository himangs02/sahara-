package com.yourteam.sahara.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yourteam.sahara.ai.ActivityChangeBreakdown
import com.yourteam.sahara.model.CaregiverAlert
import com.yourteam.sahara.model.CaregiverInsight
import com.yourteam.sahara.model.CognitiveActivityType
import com.yourteam.sahara.viewmodel.CaregiverViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatientDashboardScreen(
    caregiverViewModel: CaregiverViewModel,
    onViewTrendsClick: () -> Unit,
    onViewHistoryClick: () -> Unit,
    onViewRemindersClick: () -> Unit,
    onBackClick: () -> Unit
) {
    val state by caregiverViewModel.state.collectAsState()
    var selectedWhyActivity by remember { mutableStateOf<CognitiveActivityType?>(null) }

    if (selectedWhyActivity != null) {
        val breakdown = caregiverViewModel.getWhyChangeBreakdown(state.recentActivities, selectedWhyActivity!!)
        WhyChangeDialog(
            breakdown = breakdown,
            onDismiss = { selectedWhyActivity = null }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("${state.patient.name}'s Dashboard", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(paddingValues)
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Patient Header Summary
            PatientSummaryHeader(
                name = state.patient.name,
                age = state.patient.age,
                region = state.patient.region,
                language = state.patient.language,
                overallEngagement = state.overallEngagement
            )

            // Synchronization Status Banner
            com.yourteam.sahara.ui.components.SyncStatusCard(
                syncStatusInfo = state.syncStatus,
                onSyncNowClick = { context -> caregiverViewModel.triggerManualSync(context) }
            )

            // Active Engagement Alerts
            if (state.alerts.isNotEmpty()) {
                AlertsSection(alerts = state.alerts)
            }

            // Activity Isolation Performance Cards
            ActivityPerformanceSection(
                memoryPerf = state.memoryPerformance,
                attentionPerf = state.attentionPerformance,
                sequencePerf = state.sequencePerformance,
                onWhyClick = { activityType -> selectedWhyActivity = activityType }
            )

            // Insights Section
            InsightsSection(insights = state.insights)

            // Adaptive Recommendations Section
            RecommendationsSection(recommendations = state.recommendationsMap)

            // Navigation Actions (Trends & History)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onViewTrendsClick,
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Timeline, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("TRENDS")
                }

                Button(
                    onClick = onViewHistoryClick,
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("FULL HISTORY")
                }
            }

            Button(
                onClick = onViewRemindersClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("MANAGE DAILY REMINDERS")
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun PatientSummaryHeader(
    name: String,
    age: Int,
    region: String,
    language: String,
    overallEngagement: Int
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = "$age yrs • $region ($language)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "Engagement",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = if (overallEngagement > 0) "$overallEngagement%" else "N/A",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun AlertsSection(alerts: List<CaregiverAlert>) {
    Column {
        Text(
            text = "⚠ Engagement Alerts",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        alerts.forEach { alert ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = alert.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            text = alert.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ActivityPerformanceSection(
    memoryPerf: Int,
    attentionPerf: Int,
    sequencePerf: Int,
    onWhyClick: (CognitiveActivityType) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Cognitive Activity Performance",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ActivityBarCard(
                activityType = CognitiveActivityType.MEMORY_MATCH,
                performance = memoryPerf,
                icon = Icons.Default.Spa,
                onWhyClick = { onWhyClick(CognitiveActivityType.MEMORY_MATCH) }
            )
            ActivityBarCard(
                activityType = CognitiveActivityType.ATTENTION_TAP,
                performance = attentionPerf,
                icon = Icons.Default.GridOn,
                onWhyClick = { onWhyClick(CognitiveActivityType.ATTENTION_TAP) }
            )
            ActivityBarCard(
                activityType = CognitiveActivityType.SEQUENCE_RECALL,
                performance = sequencePerf,
                icon = Icons.Default.ListAlt,
                onWhyClick = { onWhyClick(CognitiveActivityType.SEQUENCE_RECALL) }
            )
        }
    }
}

@Composable
private fun ActivityBarCard(
    activityType: CognitiveActivityType,
    performance: Int,
    icon: ImageVector,
    onWhyClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = activityType.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Text(
                    text = if (performance > 0) "$performance%" else "No data",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            LinearProgressIndicator(
                progress = { if (performance > 0) performance / 100f else 0f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            TextButton(
                onClick = onWhyClick,
                modifier = Modifier.align(Alignment.End)
            ) {
                Icon(Icons.Default.HelpOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("WHY DID PERFORMANCE CHANGE?", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun InsightsSection(insights: List<CaregiverInsight>) {
    Column {
        Text(
            text = "💡 Performance Insights",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        insights.forEach { insight ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.tertiaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lightbulb,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column {
                        Text(
                            text = insight.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = insight.summary,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (insight.observations.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            insight.observations.forEach { obs ->
                                Text(
                                    text = "• $obs",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecommendationsSection(
    recommendations: Map<CognitiveActivityType, com.yourteam.sahara.ai.AdaptiveRecommendation>
) {
    Column {
        Text(
            text = "Adaptive Activity Recommendations",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        recommendations.forEach { (type, rec) ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = type.displayName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Level: ${rec.recommendedDifficulty.name}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Text(
                        text = rec.reason,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(180.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun WhyChangeDialog(
    breakdown: ActivityChangeBreakdown,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Why did ${breakdown.activityType.displayName} change?",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column {
                Text(
                    text = breakdown.interpretation,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Performance Metrics Comparison:",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                MetricComparisonRow("Accuracy", "${breakdown.previousAccuracy.toInt()}%", "${breakdown.recentAccuracy.toInt()}%")
                MetricComparisonRow("Avg Response Time", "${breakdown.previousTimeSeconds}s", "${breakdown.recentTimeSeconds}s")
                MetricComparisonRow("Avg Mistakes", "%.1f".format(breakdown.previousMistakes), "%.1f".format(breakdown.recentMistakes))
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("GOT IT", style = MaterialTheme.typography.labelLarge)
            }
        }
    )
}

@Composable
private fun MetricComparisonRow(label: String, previous: String, recent: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = "$previous → $recent", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
    }
}
