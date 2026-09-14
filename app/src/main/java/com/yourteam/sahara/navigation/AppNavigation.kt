package com.yourteam.sahara.navigation

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import com.yourteam.sahara.voice.VoiceCommand
import com.yourteam.sahara.voice.VoiceState
import com.yourteam.sahara.ai.AdaptiveDifficultyEngine
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.yourteam.sahara.SaharaApplication
import com.yourteam.sahara.model.CognitiveActivityType
import com.yourteam.sahara.model.Difficulty
import com.yourteam.sahara.ui.components.SaharaBottomBar
import com.yourteam.sahara.ui.components.SaharaBottomTab
import com.yourteam.sahara.ui.screens.ActivityHistoryScreen
import com.yourteam.sahara.ui.screens.AttentionGameScreen
import com.yourteam.sahara.ui.screens.CaregiverHomeScreen
import com.yourteam.sahara.ui.screens.CognitiveTrendScreen
import com.yourteam.sahara.ui.screens.DailyRemindersScreen
import com.yourteam.sahara.ui.screens.HomeScreen
import com.yourteam.sahara.ui.screens.LanguageSelectionScreen
import com.yourteam.sahara.ui.screens.MemoryGameScreen
import com.yourteam.sahara.ui.screens.PatientDashboardScreen
import com.yourteam.sahara.ui.screens.PerformanceScreen
import com.yourteam.sahara.ui.screens.SequenceRecallScreen
import com.yourteam.sahara.ui.screens.VoiceScreen
import com.yourteam.sahara.viewmodel.AttentionGameViewModel
import com.yourteam.sahara.viewmodel.AttentionGameViewModelFactory
import com.yourteam.sahara.viewmodel.CaregiverViewModel
import com.yourteam.sahara.viewmodel.CaregiverViewModelFactory
import com.yourteam.sahara.viewmodel.HomeViewModel
import com.yourteam.sahara.viewmodel.HomeViewModelFactory
import com.yourteam.sahara.viewmodel.MemoryGameViewModel
import com.yourteam.sahara.viewmodel.MemoryGameViewModelFactory
import com.yourteam.sahara.viewmodel.SequenceRecallViewModel
import com.yourteam.sahara.viewmodel.SequenceRecallViewModelFactory

@Composable
fun SaharaNavHost(modifier: Modifier = Modifier) {
    val app = LocalContext.current.applicationContext as SaharaApplication
    com.yourteam.sahara.auth.AccountGate(app) { patientId, switchPatient, logout ->
        PatientNavHost(patientId, switchPatient, logout, modifier)
    }
}

/** The existing app, scoped to one selected patient. Every repository here is built for that patient. */
@Composable
private fun PatientNavHost(selectedPatientId: String, switchPatient: () -> Unit, logout: () -> Unit, modifier: Modifier) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val app = context.applicationContext as SaharaApplication

    val gameRepository = androidx.compose.runtime.remember(selectedPatientId) { app.gameResultsFor(selectedPatientId) }
    val reminderRepository = androidx.compose.runtime.remember(selectedPatientId) { app.remindersFor(selectedPatientId) }
    val voiceManager = app.voiceManager
    val currentLanguage by app.languageManager.currentLanguage.collectAsState()

    // Keep recognition, status messages, and TTS on the selected UI language.
    LaunchedEffect(currentLanguage) {
        voiceManager.currentLanguage = currentLanguage.code
    }

    // Patient region (Stage 3D personalization) is read once per patient selection -- a one-shot
    // fetch, not an ongoing subscription, since CaregiverViewModel already owns the long-lived
    // collector on this same repository flow for the dashboard; a second indefinite subscriber
    // here is pure duplication. Deliberately NOT auto-applied to the app-wide UI language on
    // selection: the demo/seed patient's preference is Assamese, and this device may be shared by
    // caregivers whose own language differs from any one patient's -- the explicit
    // LanguageSelectionScreen remains the single, predictable way to change it.
    var patient by androidx.compose.runtime.remember(selectedPatientId) { androidx.compose.runtime.mutableStateOf<com.yourteam.sahara.model.Patient?>(null) }
    LaunchedEffect(selectedPatientId) {
        patient = app.protectedPatientRepository.getPatientById(selectedPatientId).firstOrNull()
    }

    // Stage 3D, Part 6/13: cultural content is a per-patient, caregiver-controlled local
    // preference -- never inferred, never shared between patient profiles on this device.
    // Edited from the patient's profile editor (AccountScreens.kt's PatientEditorDialog), so it
    // only needs to be read here, once per patient selection.
    val personalizationPreferences = androidx.compose.runtime.remember { com.yourteam.sahara.personalization.PersonalizationPreferences(context) }
    val culturalContentEnabled = androidx.compose.runtime.remember(selectedPatientId) {
        personalizationPreferences.isCulturalContentEnabled(selectedPatientId)
    }

    // Ask only when the user taps the microphone; buttons remain available.
    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) voiceManager.startListening() else voiceManager.permissionDenied()
    }

    val onVoiceClick: () -> Unit = {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (voiceManager.voiceState.value == VoiceState.LISTENING) {
            voiceManager.stopListening()
        } else if (!granted) {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        } else voiceManager.startListening()
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(voiceManager, lifecycleOwner, navController) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            voiceManager.commands.collect { command ->
                val route = navController.currentBackStackEntry?.destination?.route
                // Commands are available only in the elderly journey.
                if (route !in listOf("home", "voice", "performance", "more")) return@collect
                val type = when (command) {
                    VoiceCommand.START_MEMORY_GAME -> CognitiveActivityType.MEMORY_MATCH
                    VoiceCommand.START_ATTENTION_GAME -> CognitiveActivityType.ATTENTION_TAP
                    VoiceCommand.START_SEQUENCE_GAME -> CognitiveActivityType.SEQUENCE_RECALL
                    else -> null
                }
                val destination = if (type != null) {
                    val history = gameRepository.getAllGameResults().first()
                    val difficulty = AdaptiveDifficultyEngine().analyzeAndRecommend(history, type).recommendedDifficulty
                    when (type) {
                        CognitiveActivityType.MEMORY_MATCH -> "memory_game/${difficulty.name}"
                        CognitiveActivityType.ATTENTION_TAP -> "attention_game/${difficulty.name}"
                        CognitiveActivityType.SEQUENCE_RECALL -> "sequence_game/${difficulty.name}"
                    }
                } else when (command) {
                    VoiceCommand.SHOW_PROGRESS -> "performance"
                    VoiceCommand.GO_HOME -> "home"
                    else -> null
                }
                if (destination != null) navController.navigate(destination) {
                    popUpTo("home") { inclusive = false }
                    launchSingleTop = true
                }
            }
        }
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: "splash"
    LaunchedEffect(currentRoute) {
        if (voiceManager.voiceState.value == VoiceState.LISTENING) voiceManager.stopListening()
    }

    // Only show bottom navigation bar on main top-level elderly experience screens
    val mainBottomNavRoutes = listOf("home", "performance", "voice", "more")
    val showBottomBar = currentRoute in mainBottomNavRoutes

    Scaffold(
        modifier = modifier,
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBottomBar) {
                SaharaBottomBar(
                    currentRoute = currentRoute,
                    onTabSelected = { tab ->
                        if (tab.route != currentRoute) {
                            navController.navigate(tab.route) {
                                popUpTo("home") {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.padding(innerPadding)
        ) {
            composable("home") {
                val factory = HomeViewModelFactory(gameRepository, reminderRepository)
                val homeViewModel: HomeViewModel = viewModel(factory = factory)

                HomeScreen(
                    homeViewModel = homeViewModel,
                    voiceManager = voiceManager,
                    onVoiceClick = onVoiceClick,
                    currentLanguage = currentLanguage,
                    onStartActivityClick = { activityType, difficulty ->
                        val route = when (activityType) {
                            CognitiveActivityType.MEMORY_MATCH -> "memory_game/${difficulty.name}"
                            CognitiveActivityType.ATTENTION_TAP -> "attention_game/${difficulty.name}"
                            CognitiveActivityType.SEQUENCE_RECALL -> "sequence_game/${difficulty.name}"
                        }
                        navController.navigate(route)
                    },
                    onViewProgressClick = {
                        navController.navigate("performance")
                    },
                    onCaregiverPortalClick = {
                        navController.navigate("caregiver_home")
                    },
                    onLanguageChange = { lang ->
                        app.languageManager.setLanguage(context as? Activity, lang)
                    }
                )
            }

            composable("voice") {
                VoiceScreen(
                    voiceManager = voiceManager,
                    onVoiceClick = onVoiceClick,
                    onBackClick = {
                        navController.popBackStack()
                    }
                )
            }

            composable("memory_game/{difficulty}") { backStackEntry ->
                val difficultyStr = backStackEntry.arguments?.getString("difficulty") ?: Difficulty.EASY.name
                val difficulty = try {
                    Difficulty.valueOf(difficultyStr)
                } catch (_: Exception) {
                    Difficulty.EASY
                }

                val factory = MemoryGameViewModelFactory(gameRepository, difficulty, patient?.region, culturalContentEnabled)
                val viewModel: MemoryGameViewModel = viewModel(factory = factory)

                MemoryGameScreen(
                    onBackToHome = {
                        navController.popBackStack("home", inclusive = false)
                    },
                    viewModel = viewModel
                )
            }

            composable("attention_game/{difficulty}") { backStackEntry ->
                val difficultyStr = backStackEntry.arguments?.getString("difficulty") ?: Difficulty.EASY.name
                val difficulty = try {
                    Difficulty.valueOf(difficultyStr)
                } catch (_: Exception) {
                    Difficulty.EASY
                }

                val factory = AttentionGameViewModelFactory(gameRepository, difficulty, patient?.region, culturalContentEnabled)
                val viewModel: AttentionGameViewModel = viewModel(factory = factory)

                AttentionGameScreen(
                    onBackToHome = {
                        navController.popBackStack("home", inclusive = false)
                    },
                    viewModel = viewModel
                )
            }

            composable("sequence_game/{difficulty}") { backStackEntry ->
                val difficultyStr = backStackEntry.arguments?.getString("difficulty") ?: Difficulty.EASY.name
                val difficulty = try {
                    Difficulty.valueOf(difficultyStr)
                } catch (_: Exception) {
                    Difficulty.EASY
                }

                val factory = SequenceRecallViewModelFactory(gameRepository, difficulty, patient?.region, culturalContentEnabled)
                val viewModel: SequenceRecallViewModel = viewModel(factory = factory)

                SequenceRecallScreen(
                    onBackToHome = {
                        navController.popBackStack("home", inclusive = false)
                    },
                    viewModel = viewModel
                )
            }

            composable("performance") {
                PerformanceScreen(
                    repository = gameRepository,
                    onBackClick = {
                        navController.popBackStack()
                    }
                )
            }

            composable("more") {
                LanguageSelectionScreen(
                    currentLanguage = currentLanguage,
                    onLanguageSelected = { lang ->
                        app.languageManager.setLanguage(context as? Activity, lang)
                    },
                    onContinueClick = {
                        navController.navigate("home")
                    }
                )
            }

            // --- CAREGIVER MODE ROUTES ---
            // Reached only inside an authenticated session (see AccountGate), so no separate login step.

            composable("caregiver_home") {
                val factory = CaregiverViewModelFactory(gameRepository, app.protectedPatientRepository, reminderRepository, app.syncManager, selectedPatientId)
                val caregiverViewModel: CaregiverViewModel = viewModel(factory = factory)

                CaregiverHomeScreen(
                    caregiverViewModel = caregiverViewModel,
                    onSelectPatient = { patientId ->
                        navController.navigate("patient_dashboard/$patientId")
                    },
                    onBackToElderHome = {
                        navController.popBackStack("home", inclusive = false)
                    },
                    onSwitchPatient = switchPatient,
                    onLogout = logout
                )
            }

            composable("patient_dashboard/{patientId}") { backStackEntry ->
                val patientId = selectedPatientId
                val factory = CaregiverViewModelFactory(gameRepository, app.protectedPatientRepository, reminderRepository, app.syncManager, patientId)
                val caregiverViewModel: CaregiverViewModel = viewModel(factory = factory)

                PatientDashboardScreen(
                    caregiverViewModel = caregiverViewModel,
                    onViewTrendsClick = {
                        navController.navigate("cognitive_trends/$patientId")
                    },
                    onViewHistoryClick = {
                        navController.navigate("activity_history/$patientId")
                    },
                    onViewRemindersClick = {
                        navController.navigate("daily_reminders/$patientId")
                    },
                    onBackClick = {
                        navController.popBackStack()
                    }
                )
            }

            composable("cognitive_trends/{patientId}") { backStackEntry ->
                val patientId = selectedPatientId
                val factory = CaregiverViewModelFactory(gameRepository, app.protectedPatientRepository, reminderRepository, app.syncManager, patientId)
                val caregiverViewModel: CaregiverViewModel = viewModel(factory = factory)

                CognitiveTrendScreen(
                    caregiverViewModel = caregiverViewModel,
                    onBackClick = {
                        navController.popBackStack()
                    }
                )
            }

            composable("activity_history/{patientId}") { backStackEntry ->
                val patientId = selectedPatientId
                val factory = CaregiverViewModelFactory(gameRepository, app.protectedPatientRepository, reminderRepository, app.syncManager, patientId)
                val caregiverViewModel: CaregiverViewModel = viewModel(factory = factory)

                ActivityHistoryScreen(
                    caregiverViewModel = caregiverViewModel,
                    onBackClick = {
                        navController.popBackStack()
                    }
                )
            }

            composable("daily_reminders/{patientId}") { backStackEntry ->
                val patientId = selectedPatientId
                val factory = CaregiverViewModelFactory(gameRepository, app.protectedPatientRepository, reminderRepository, app.syncManager, patientId)
                val caregiverViewModel: CaregiverViewModel = viewModel(factory = factory)

                DailyRemindersScreen(
                    caregiverViewModel = caregiverViewModel,
                    onBackClick = {
                        navController.popBackStack()
                    }
                )
            }
        }
    }
}
