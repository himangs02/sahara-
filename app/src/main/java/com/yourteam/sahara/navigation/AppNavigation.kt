package com.yourteam.sahara.navigation

import android.app.Activity
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
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
import com.yourteam.sahara.ui.screens.CaregiverLoginScreen
import com.yourteam.sahara.ui.screens.CognitiveTrendScreen
import com.yourteam.sahara.ui.screens.DailyRemindersScreen
import com.yourteam.sahara.ui.screens.HomeScreen
import com.yourteam.sahara.ui.screens.LanguageSelectionScreen
import com.yourteam.sahara.ui.screens.MemoryGameScreen
import com.yourteam.sahara.ui.screens.PatientDashboardScreen
import com.yourteam.sahara.ui.screens.PerformanceScreen
import com.yourteam.sahara.ui.screens.SequenceRecallScreen
import com.yourteam.sahara.ui.screens.SplashScreen
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
    val navController = rememberNavController()
    val context = LocalContext.current
    val app = context.applicationContext as SaharaApplication

    val voiceManager = app.voiceManager
    val currentLanguage by app.languageManager.currentLanguage.collectAsState()

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: "splash"

    // Only show bottom navigation bar on main top-level elderly experience screens
    val mainBottomNavRoutes = listOf("home", "performance", "voice", "caregiver_login", "more")
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
            startDestination = "splash",
            modifier = Modifier.padding(innerPadding)
        ) {
            composable("splash") {
                SplashScreen(
                    onGetStartedClick = {
                        navController.navigate("language_selection")
                    }
                )
            }

            composable("language_selection") {
                LanguageSelectionScreen(
                    currentLanguage = currentLanguage,
                    onLanguageSelected = { lang ->
                        app.languageManager.setLanguage(context as? Activity, lang)
                    },
                    onContinueClick = {
                        navController.navigate("home") {
                            popUpTo("splash") { inclusive = true }
                        }
                    }
                )
            }

            composable("home") {
                val factory = HomeViewModelFactory(app.gameResultRepository)
                val homeViewModel: HomeViewModel = viewModel(factory = factory)

                HomeScreen(
                    homeViewModel = homeViewModel,
                    voiceManager = voiceManager,
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
                        navController.navigate("caregiver_login")
                    },
                    onLanguageChange = { lang ->
                        app.languageManager.setLanguage(context as? Activity, lang)
                    }
                )
            }

            composable("voice") {
                VoiceScreen(
                    voiceManager = voiceManager,
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

                val factory = MemoryGameViewModelFactory(app.gameResultRepository, difficulty)
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

                val factory = AttentionGameViewModelFactory(app.gameResultRepository, difficulty)
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

                val factory = SequenceRecallViewModelFactory(app.gameResultRepository, difficulty)
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
                    repository = app.gameResultRepository,
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

            composable("caregiver_login") {
                CaregiverLoginScreen(
                    onLoginSuccess = {
                        navController.navigate("caregiver_home") {
                            popUpTo("caregiver_login") { inclusive = true }
                        }
                    },
                    onBackToElderHome = {
                        navController.popBackStack("home", inclusive = false)
                    }
                )
            }

            composable("caregiver_home") {
                val factory = CaregiverViewModelFactory(app.gameResultRepository, app.patientRepository, app.reminderRepository, app.syncManager)
                val caregiverViewModel: CaregiverViewModel = viewModel(factory = factory)

                CaregiverHomeScreen(
                    caregiverViewModel = caregiverViewModel,
                    onSelectPatient = { patientId ->
                        navController.navigate("patient_dashboard/$patientId")
                    },
                    onLogout = {
                        navController.navigate("home") {
                            popUpTo("caregiver_home") { inclusive = true }
                        }
                    }
                )
            }

            composable("patient_dashboard/{patientId}") { backStackEntry ->
                val patientId = backStackEntry.arguments?.getString("patientId") ?: "patient_001"
                val factory = CaregiverViewModelFactory(app.gameResultRepository, app.patientRepository, app.reminderRepository, app.syncManager, patientId)
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
                val patientId = backStackEntry.arguments?.getString("patientId") ?: "patient_001"
                val factory = CaregiverViewModelFactory(app.gameResultRepository, app.patientRepository, app.reminderRepository, app.syncManager, patientId)
                val caregiverViewModel: CaregiverViewModel = viewModel(factory = factory)

                CognitiveTrendScreen(
                    caregiverViewModel = caregiverViewModel,
                    onBackClick = {
                        navController.popBackStack()
                    }
                )
            }

            composable("activity_history/{patientId}") { backStackEntry ->
                val patientId = backStackEntry.arguments?.getString("patientId") ?: "patient_001"
                val factory = CaregiverViewModelFactory(app.gameResultRepository, app.patientRepository, app.reminderRepository, app.syncManager, patientId)
                val caregiverViewModel: CaregiverViewModel = viewModel(factory = factory)

                ActivityHistoryScreen(
                    caregiverViewModel = caregiverViewModel,
                    onBackClick = {
                        navController.popBackStack()
                    }
                )
            }

            composable("daily_reminders/{patientId}") { backStackEntry ->
                val patientId = backStackEntry.arguments?.getString("patientId") ?: "patient_001"
                val factory = CaregiverViewModelFactory(app.gameResultRepository, app.patientRepository, app.reminderRepository, app.syncManager, patientId)
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
