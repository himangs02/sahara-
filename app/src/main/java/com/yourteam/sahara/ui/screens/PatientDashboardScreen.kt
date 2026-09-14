package com.yourteam.sahara.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingFlat
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Percent
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yourteam.sahara.R
import com.yourteam.sahara.ai.ActivityStatus
import com.yourteam.sahara.ai.ActivitySummary
import com.yourteam.sahara.ai.CaregiverDashboardAnalyzer
import com.yourteam.sahara.ai.ChangeStatus
import com.yourteam.sahara.ai.DashboardSummary
import com.yourteam.sahara.ai.TrendDirection
import com.yourteam.sahara.language.AppLanguage
import com.yourteam.sahara.model.GameResult
import com.yourteam.sahara.model.Patient
import com.yourteam.sahara.model.ReminderStatus
import com.yourteam.sahara.model.TodayReminder
import com.yourteam.sahara.ui.components.ActivitySessionCard
import com.yourteam.sahara.ui.components.SyncStatusCard
import com.yourteam.sahara.ui.components.currentLocale
import com.yourteam.sahara.ui.components.icon
import com.yourteam.sahara.ui.components.labelRes
import com.yourteam.sahara.ui.components.localizedName
import com.yourteam.sahara.ui.components.localizedTitle
import com.yourteam.sahara.ui.components.reminderTimeText
import com.yourteam.sahara.viewmodel.CaregiverViewModel
import java.text.SimpleDateFormat
import java.util.Date

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
    val reminders by caregiverViewModel.todayReminders.collectAsState()
    val summary = state.dashboard

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.patient_dashboard_title, state.patient.name), style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { paddingValues ->
        if (summary == null) {
            val loading = stringResource(R.string.dashboard_loading)
            Box(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(modifier = Modifier.semantics { contentDescription = loading })
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            PatientOverviewCard(
                patient = state.patient,
                summary = summary,
                reminders = reminders
            )

            SyncStatusCard(
                syncStatusInfo = state.syncStatus,
                onSyncNowClick = { context -> caregiverViewModel.triggerManualSync(context) }
            )

            ChangeNoticeCard(summary = summary)

            SectionHeader(stringResource(R.string.activity_performance))
            summary.activities.forEach { ActivityPerformanceCard(it) }

            SectionHeader(stringResource(R.string.todays_routine))
            TodaysRoutineCard(reminders = reminders, onManageClick = onViewRemindersClick)

            SectionHeader(stringResource(R.string.recent_sessions))
            RecentSessionsSection(
                sessions = state.recentActivities.take(RECENT_SESSION_PREVIEW),
                onViewHistoryClick = onViewHistoryClick,
                onViewTrendsClick = onViewTrendsClick
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

private const val RECENT_SESSION_PREVIEW = 3

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier
            .padding(top = 8.dp)
            .semantics { heading() }
    )
}

/** The stored patient language may be an English name ("Assamese") or a code; show it in its own script. */
private fun preferredLanguageName(stored: String): String =
    AppLanguage.fromStoredPreference(stored)?.displayName ?: stored

@Composable
private fun PatientOverviewCard(
    patient: Patient,
    summary: DashboardSummary,
    reminders: List<TodayReminder>
) {
    val reminderSummary = remember(reminders) { CaregiverDashboardAnalyzer.summarizeReminders(reminders) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.semantics(mergeDescendants = true) {}) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = patient.name,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = stringResource(R.string.patient_age_region, patient.age, patient.region),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = stringResource(R.string.preferred_language, preferredLanguageName(patient.language)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            ActivityStatusRow(summary)

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile(
                    icon = Icons.Default.PlayCircle,
                    value = summary.activitiesToday.toString(),
                    label = stringResource(R.string.stat_activities_today),
                    modifier = Modifier.weight(1f)
                )
                StatTile(
                    icon = Icons.Default.Percent,
                    value = summary.averageRecentAccuracy?.let { "${it.toInt()}%" } ?: stringResource(R.string.no_data),
                    label = stringResource(R.string.stat_recent_accuracy),
                    modifier = Modifier.weight(1f)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile(
                    icon = Icons.Default.Tune,
                    value = summary.recommendedDifficulty.localizedName(),
                    label = stringResource(R.string.stat_recommended_level),
                    detail = stringResource(summary.recommendedActivity.titleRes),
                    modifier = Modifier.weight(1f)
                )
                StatTile(
                    icon = Icons.Default.EventAvailable,
                    value = stringResource(R.string.reminders_done_count, reminderSummary.completed, reminderSummary.enabled),
                    label = stringResource(R.string.stat_reminders_today),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun ActivityStatusRow(summary: DashboardSummary) {
    val (icon, textRes) = when (summary.status) {
        ActivityStatus.ACTIVE_TODAY -> Icons.Default.CheckCircle to R.string.status_active_today
        ActivityStatus.ACTIVE_RECENTLY -> Icons.Default.CheckCircle to R.string.status_active_recently
        ActivityStatus.INACTIVE -> Icons.Default.Info to R.string.status_inactive
        ActivityStatus.NO_ACTIVITY_YET -> Icons.Default.HourglassEmpty to R.string.status_no_activity
    }
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {}
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(stringResource(textRes), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                summary.lastActivityAt?.let {
                    val format = SimpleDateFormat("MMM d, h:mm a", currentLocale())
                    Text(
                        stringResource(R.string.last_activity, format.format(Date(it))),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun StatTile(icon: ImageVector, value: String, label: String, modifier: Modifier = Modifier, detail: String? = null) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier
            .heightIn(min = 104.dp)
            .semantics(mergeDescendants = true) {}
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
private fun ChangeNoticeCard(summary: DashboardSummary) {
    val (container, onContainer, icon) = when (summary.overallChange) {
        ChangeStatus.NOTICEABLE_CHANGE -> Triple(MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer, Icons.Default.Info)
        ChangeStatus.NO_NOTABLE_CHANGE -> Triple(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer, Icons.Default.CheckCircle)
        ChangeStatus.NOT_ENOUGH_HISTORY -> Triple(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurface, Icons.Default.HourglassEmpty)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = container)
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = onContainer, modifier = Modifier.size(28.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    stringResource(R.string.change_card_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = onContainer,
                    modifier = Modifier.semantics { heading() }
                )
            }

            when (summary.overallChange) {
                ChangeStatus.NOT_ENOUGH_HISTORY -> {
                    Text(stringResource(R.string.change_not_enough), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = onContainer)
                    Text(stringResource(R.string.change_not_enough_detail), style = MaterialTheme.typography.bodyMedium, color = onContainer)
                }
                ChangeStatus.NO_NOTABLE_CHANGE -> {
                    Text(stringResource(R.string.change_none), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = onContainer)
                }
                ChangeStatus.NOTICEABLE_CHANGE -> {
                    Text(stringResource(R.string.change_noticeable), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = onContainer)
                    Text(stringResource(R.string.change_check_in), style = MaterialTheme.typography.bodyLarge, color = onContainer)
                    summary.changedActivities.forEach { activity ->
                        val baseline = activity.baseline ?: return@forEach
                        Text(
                            text = "• " + stringResource(
                                R.string.change_activity_detail,
                                stringResource(activity.activityType.titleRes),
                                baseline.recentAccuracy.toInt(),
                                (baseline.baselineAccuracy ?: baseline.recentAccuracy).toInt()
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = onContainer
                        )
                    }
                }
            }

            Text(stringResource(R.string.baseline_disclaimer), style = MaterialTheme.typography.bodySmall, color = onContainer)
        }
    }
}

@Composable
private fun ActivityPerformanceCard(summary: ActivitySummary) {
    val resources = LocalResources.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(summary.activityType.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(summary.activityType.titleRes),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.semantics { heading() }
                    )
                    Text(
                        resources.getQuantityString(R.plurals.sessions_count, summary.completedSessions, summary.completedSessions),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                    Text(
                        "${stringResource(R.string.level)}: ${summary.currentDifficulty.localizedName()}",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            val baseline = summary.baseline
            if (baseline == null) {
                Text(stringResource(R.string.no_sessions_yet), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                return@Column
            }

            if (summary.changeStatus == ChangeStatus.NOTICEABLE_CHANGE) {
                Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.tertiaryContainer) {
                    Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiaryContainer, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.noticeable_change_chip), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onTertiaryContainer)
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.semantics(mergeDescendants = true) {}) {
                    Text(
                        "${baseline.recentAccuracy.toInt()}%",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(stringResource(R.string.stat_recent_accuracy), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(modifier = Modifier.width(16.dp))
                AccuracyChart(
                    accuracies = summary.recentAccuracies,
                    baselineAccuracy = baseline.baselineAccuracy,
                    modifier = Modifier
                        .weight(1f)
                        .height(72.dp)
                )
            }

            TrendRow(stringResource(R.string.accuracy), summary.accuracyTrend, R.string.trend_higher, R.string.trend_lower, increaseIsImprovement = true)
            TrendRow(stringResource(R.string.mistakes), summary.mistakesTrend, R.string.trend_fewer, R.string.trend_more, increaseIsImprovement = false)
            if (baseline.recentTimeSeconds != null) {
                TrendRow(stringResource(R.string.time), summary.timeTrend, R.string.trend_faster, R.string.trend_slower, increaseIsImprovement = false)
            }

            summary.lastSessionAt?.let {
                val format = SimpleDateFormat("MMM d, h:mm a", currentLocale())
                Text(stringResource(R.string.last_played, format.format(Date(it))), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/**
 * One metric's direction. The arrow shows which way the value moved; the word says whether that
 * is better or worse, so the meaning never depends on colour.
 */
@Composable
private fun TrendRow(label: String, direction: TrendDirection, improvingRes: Int, decliningRes: Int, increaseIsImprovement: Boolean) {
    val (icon, textRes, tint) = when (direction) {
        TrendDirection.IMPROVING -> Triple(
            if (increaseIsImprovement) Icons.AutoMirrored.Filled.TrendingUp else Icons.AutoMirrored.Filled.TrendingDown,
            improvingRes, MaterialTheme.colorScheme.primary
        )
        TrendDirection.DECLINING -> Triple(
            if (increaseIsImprovement) Icons.AutoMirrored.Filled.TrendingDown else Icons.AutoMirrored.Filled.TrendingUp,
            decliningRes, MaterialTheme.colorScheme.error
        )
        TrendDirection.STABLE -> Triple(Icons.AutoMirrored.Filled.TrendingFlat, R.string.trend_steady, MaterialTheme.colorScheme.onSurfaceVariant)
        TrendDirection.NOT_ENOUGH_DATA -> Triple(Icons.Default.Remove, R.string.trend_not_enough, MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(stringResource(textRes), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
    }
}

/** Accuracy of recent sessions (0–100%) with the earlier average as a dashed line. */
@Composable
private fun AccuracyChart(accuracies: List<Float>, baselineAccuracy: Float?, modifier: Modifier = Modifier) {
    val description = stringResource(
        R.string.accuracy_chart_description,
        accuracies.size,
        accuracies.joinToString(", ") { "${it.toInt()}%" }
    )
    val lineColor = MaterialTheme.colorScheme.primary
    val baselineColor = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = MaterialTheme.colorScheme.surfaceVariant

    Canvas(modifier = modifier.semantics { contentDescription = description }) {
        val inset = 6.dp.toPx()
        val chartHeight = size.height - inset * 2
        fun y(value: Float) = inset + chartHeight * (1f - value.coerceIn(0f, 100f) / 100f)

        drawLine(gridColor, Offset(0f, y(0f)), Offset(size.width, y(0f)), strokeWidth = 2f)
        drawLine(gridColor, Offset(0f, y(100f)), Offset(size.width, y(100f)), strokeWidth = 2f)

        baselineAccuracy?.let {
            drawLine(
                color = baselineColor,
                start = Offset(0f, y(it)),
                end = Offset(size.width, y(it)),
                strokeWidth = 3f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f))
            )
        }

        if (accuracies.isEmpty()) return@Canvas
        val step = if (accuracies.size > 1) (size.width - inset * 2) / (accuracies.size - 1) else 0f
        val points = accuracies.mapIndexed { index, value ->
            Offset(if (accuracies.size > 1) inset + step * index else size.width / 2, y(value))
        }
        points.zipWithNext { a, b -> drawLine(lineColor, a, b, strokeWidth = 6f) }
        points.forEach { drawCircle(lineColor, radius = 7f, center = it) }
    }
}

@Composable
private fun TodaysRoutineCard(reminders: List<TodayReminder>, onManageClick: () -> Unit) {
    val reminderSummary = remember(reminders) { CaregiverDashboardAnalyzer.summarizeReminders(reminders) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                stringResource(R.string.routine_summary, reminderSummary.completed, reminderSummary.missed, reminderSummary.pending),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (reminders.isEmpty()) {
                Text(stringResource(R.string.no_reminders_today), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            reminders.forEach { item ->
                RoutineRow(item)
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            }

            Button(
                onClick = onManageClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(stringResource(R.string.manage_reminders))
            }
        }
    }
}

@Composable
private fun RoutineRow(item: TodayReminder) {
    val reminder = item.reminder
    val typeLabel = stringResource(reminder.type.labelRes)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(
                    if (reminder.enabled) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant
                )
                .semantics { contentDescription = typeLabel },
            contentAlignment = Alignment.Center
        ) {
            Icon(reminder.type.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(reminder.localizedTitle(), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Text(
                "${reminderTimeText(reminder.minuteOfDay)} • $typeLabel",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (reminder.description.isNotBlank()) {
                Text(reminder.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        val (chipRes, chipColor, chipText) = when {
            !reminder.enabled -> Triple(R.string.reminder_off, MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
            item.status == ReminderStatus.COMPLETED -> Triple(R.string.reminder_completed, MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
            item.status == ReminderStatus.MISSED -> Triple(R.string.reminder_missed, MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
            else -> Triple(R.string.reminder_pending, MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
        }
        Surface(shape = RoundedCornerShape(8.dp), color = chipColor) {
            Text(
                stringResource(chipRes),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = chipText,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
private fun RecentSessionsSection(sessions: List<GameResult>, onViewHistoryClick: () -> Unit, onViewTrendsClick: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (sessions.isEmpty()) {
            Text(stringResource(R.string.no_completed_activities), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        sessions.forEach { ActivitySessionCard(it) }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = onViewTrendsClick,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 52.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Timeline, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.trends))
            }
            Button(
                onClick = onViewHistoryClick,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 52.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(stringResource(R.string.full_history))
            }
        }
    }
}
