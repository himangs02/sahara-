package com.yourteam.sahara.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.SupervisorAccount
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.yourteam.sahara.ui.components.asString
import com.yourteam.sahara.ui.components.icon
import com.yourteam.sahara.ui.components.localizedName
import com.yourteam.sahara.ui.components.localizedTitle
import com.yourteam.sahara.ui.components.reminderTimeText
import com.yourteam.sahara.R
import com.yourteam.sahara.ai.AdaptiveRecommendation
import com.yourteam.sahara.language.AppLanguage
import com.yourteam.sahara.model.CognitiveActivityType
import com.yourteam.sahara.model.Difficulty
import com.yourteam.sahara.model.Reminder
import com.yourteam.sahara.model.ReminderStatus
import com.yourteam.sahara.model.TodayReminder
import com.yourteam.sahara.model.UiText
import com.yourteam.sahara.ui.theme.SaharaTheme
import com.yourteam.sahara.viewmodel.HomeViewModel
import com.yourteam.sahara.voice.VoiceManager
import com.yourteam.sahara.voice.VoiceState

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    homeViewModel: HomeViewModel,
    voiceManager: VoiceManager? = null,
    currentLanguage: AppLanguage = AppLanguage.ENGLISH,
    onStartActivityClick: (CognitiveActivityType, Difficulty) -> Unit = { _, _ -> },
    onViewProgressClick: () -> Unit = {},
    onCaregiverPortalClick: () -> Unit = {},
    onLanguageChange: (AppLanguage) -> Unit = {},
    onVoiceClick: (() -> Unit)? = null
) {
    val homeState by homeViewModel.state.collectAsState()
    val todayReminders by homeViewModel.todayReminders.collectAsState()
    var selectedWhyActivity by remember { mutableStateOf<Pair<CognitiveActivityType, AdaptiveRecommendation>?>(null) }

    val defaultTapText = stringResource(R.string.tap_to_speak)
    val voiceState by voiceManager?.voiceState?.collectAsState() ?: remember { mutableStateOf(VoiceState.IDLE) }
    val voiceStatusText by voiceManager?.statusMessage?.collectAsState() ?: remember { mutableStateOf(defaultTapText) }

    if (selectedWhyActivity != null) {
        val (activityType, rec) = selectedWhyActivity!!
        AlertDialog(
            onDismissRequest = { selectedWhyActivity = null },
            title = {
                Text(
                    text = "${stringResource(activityType.titleRes)} (${rec.recommendedDifficulty.localizedName()})",
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Column {
                    Text(
                        text = rec.reason.asString(),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.metrics),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = stringResource(R.string.accuracy_value, rec.recentAccuracy.toInt()))
                    Text(text = stringResource(R.string.mistakes_value, rec.recentMistakes))
                    Text(text = stringResource(R.string.sessions_value, rec.recentSessionsCount))
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedWhyActivity = null }) {
                    Text(stringResource(R.string.close), style = MaterialTheme.typography.labelLarge)
                }
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        HeaderSection(onCaregiverClick = onCaregiverPortalClick)
        
        // Language Selector Row
        LanguageSelectorRow(
            selectedLanguage = currentLanguage,
            onLanguageSelected = { lang ->
                onLanguageChange(lang)
            }
        )

        // Voice Section
        VoiceButtonSection(
            voiceState = voiceState,
            statusText = voiceStatusText,
            onClick = {
                if (onVoiceClick != null) {
                    onVoiceClick()
                } else if (voiceState == VoiceState.LISTENING) {
                    voiceManager?.stopListening()
                } else {
                    voiceManager?.startListening()
                }
            }
        )

        ProgressSection(onViewProgressClick)
        
        // Featured Recommended Activity Section
        TodayActivitySection(
            activityType = homeState.primaryActivity,
            recommendation = homeState.primaryRecommendation,
            onStartClick = {
                onStartActivityClick(homeState.primaryActivity, homeState.primaryRecommendation.recommendedDifficulty)
            },
            onWhyClick = {
                selectedWhyActivity = Pair(homeState.primaryActivity, homeState.primaryRecommendation)
            }
        )

        // Other Cognitive Activities Section
        OtherActivitiesSection(
            recommendationsMap = homeState.recommendationsMap,
            onStartClick = onStartActivityClick,
            onWhyClick = { type, rec ->
                selectedWhyActivity = Pair(type, rec)
            }
        )

        DailyCareSection(
            reminders = todayReminders,
            onDoneChange = homeViewModel::setReminderDone
        )
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun HeaderSection(onCaregiverClick: () -> Unit = {}) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = stringResource(R.string.good_morning),
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.welcome_to_sahara),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        OutlinedIconButton(
            onClick = onCaregiverClick,
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.SupervisorAccount,
                contentDescription = stringResource(R.string.caregiver_portal),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun LanguageSelectorRow(
    selectedLanguage: AppLanguage,
    onLanguageSelected: (AppLanguage) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "${stringResource(R.string.language)}:",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppLanguage.entries.forEach { lang ->
                FilterChip(
                    selected = lang == selectedLanguage,
                    onClick = { onLanguageSelected(lang) },
                    label = { Text(lang.displayName, style = MaterialTheme.typography.labelMedium) }
                )
            }
        }
    }
}

@Composable
private fun VoiceButtonSection(
    voiceState: VoiceState = VoiceState.IDLE,
    statusText: String = stringResource(R.string.tap_to_speak),
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(88.dp),
        shape = RoundedCornerShape(24.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (voiceState == VoiceState.LISTENING)
                MaterialTheme.colorScheme.errorContainer
            else
                MaterialTheme.colorScheme.primaryContainer,
            contentColor = if (voiceState == VoiceState.LISTENING)
                MaterialTheme.colorScheme.onErrorContainer
            else
                MaterialTheme.colorScheme.onPrimaryContainer
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Default.Mic,
                contentDescription = stringResource(R.string.tap_to_speak),
                modifier = Modifier.size(40.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = "🎙️ $statusText",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.ask_sahara),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun ProgressSection(onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        ),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = stringResource(R.string.my_progress),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.view_progress_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = stringResource(R.string.my_progress),
                tint = MaterialTheme.colorScheme.onTertiaryContainer
            )
        }
    }
}

@Composable
private fun TodayActivitySection(
    activityType: CognitiveActivityType,
    recommendation: AdaptiveRecommendation,
    onStartClick: () -> Unit,
    onWhyClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp)
        ) {
            Text(
                text = stringResource(R.string.todays_cognitive_activity),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = activityType.icon,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(activityType.titleRes),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.recommended_for_you),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = recommendation.recommendedDifficulty.localizedName(),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.tertiary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(
                            onClick = onWhyClick,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = stringResource(R.string.why_difficulty),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onStartClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp),
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.start_activity),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@Composable
private fun OtherActivitiesSection(
    recommendationsMap: Map<CognitiveActivityType, AdaptiveRecommendation>,
    onStartClick: (CognitiveActivityType, Difficulty) -> Unit,
    onWhyClick: (CognitiveActivityType, AdaptiveRecommendation) -> Unit
) {
    Column {
        Text(
            text = stringResource(R.string.other_activities),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            CognitiveActivityType.entries.forEach { activityType ->
                val rec = recommendationsMap[activityType] ?: AdaptiveRecommendation(
                    recommendedDifficulty = Difficulty.EASY,
                    performanceScore = 0.5f,
                    reason = UiText(R.string.reason_loading),
                    confidence = 0f
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = activityType.icon,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(28.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(activityType.titleRes),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = stringResource(activityType.descriptionRes),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "${stringResource(R.string.level)}: ${rec.recommendedDifficulty.localizedName()}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.tertiary,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    IconButton(
                                        onClick = { onWhyClick(activityType, rec) },
                                        modifier = Modifier.size(20.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Info,
                                            contentDescription = stringResource(R.string.why_difficulty),
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = { onStartClick(activityType, rec.recommendedDifficulty) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.start_activity),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DailyCareSection(
    reminders: List<TodayReminder>,
    onDoneChange: (Reminder, Boolean) -> Unit
) {
    Column {
        Text(
            text = stringResource(R.string.todays_care),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        if (reminders.isEmpty()) {
            Text(
                text = stringResource(R.string.no_reminders_today),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            reminders.forEach { item ->
                CareItem(item = item, onDoneChange = { done -> onDoneChange(item.reminder, done) })
            }
        }
    }
}

@Composable
private fun CareItem(
    item: TodayReminder,
    onDoneChange: (Boolean) -> Unit
) {
    val isCompleted = item.status == ReminderStatus.COMPLETED
    val isMissed = item.status == ReminderStatus.MISSED

    Card(
        // The whole card is one large checkbox so it is easy to tap.
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = isCompleted, role = Role.Checkbox, onValueChange = onDoneChange),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isCompleted)
                MaterialTheme.colorScheme.surfaceVariant
            else
                MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isCompleted) 0.dp else 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (isCompleted) MaterialTheme.colorScheme.surface
                        else MaterialTheme.colorScheme.primaryContainer
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = item.reminder.type.icon,
                    contentDescription = null,
                    tint = if (isCompleted) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.reminder.localizedTitle(),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (isMissed) {
                        "${reminderTimeText(item.reminder.minuteOfDay)} • ${stringResource(R.string.reminder_missed)}"
                    } else reminderTimeText(item.reminder.minuteOfDay),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isMissed) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (item.reminder.description.isNotBlank()) {
                    Text(
                        text = item.reminder.description,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Visible label as well as the icon, so the Done state never relies on shape or colour alone.
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = if (isCompleted) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = if (isCompleted) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(40.dp)
                )
                Text(
                    text = stringResource(if (isCompleted) R.string.reminder_completed else R.string.mark_done),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (isCompleted) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun HomeScreenPreview() {
    SaharaTheme {
        // Preview
    }
}
