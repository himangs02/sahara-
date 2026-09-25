package com.yourteam.sahara.auth

import androidx.activity.compose.LocalActivity
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import android.util.Log
import androidx.compose.ui.unit.dp
import com.yourteam.sahara.BuildConfig
import com.yourteam.sahara.R
import com.yourteam.sahara.SaharaApplication
import com.yourteam.sahara.data.remote.BackendAuthService
import com.yourteam.sahara.language.AppLanguage
import com.yourteam.sahara.model.Patient
import com.yourteam.sahara.personalization.NerRegions
import com.yourteam.sahara.personalization.PersonalizationPreferences
import kotlinx.coroutines.launch

/**
 * Shows login, patient selection, or the selected patient's app. The patient app is composed only for
 * [AccountDestination.PatientHome]; when the session ends it leaves composition together with its
 * navigation back stack, so back navigation can never return to an authenticated screen.
 */
@Composable
fun AccountGate(
    app: SaharaApplication,
    content: @Composable (patientId: String, onSwitchPatient: () -> Unit, onLogout: () -> Unit) -> Unit
) {
    val ready by app.accountsReady.collectAsState()
    var sessionLoaded by remember { mutableStateOf(false) }
    var session by remember { mutableStateOf<LocalSessionEntity?>(null) }
    LaunchedEffect(app) {
        app.authRepository.sessions.collect {
            session = it
            sessionLoaded = true
        }
    }

    // Keyed by caregiver so one caregiver's patient list can never appear for another.
    val caregiverId = session?.caregiverId
    val patients: List<Patient>? = if (caregiverId == null) null else key(caregiverId) {
        val flow = remember { app.authRepository.patients() }
        flow.collectAsState<List<Patient>, List<Patient>?>(initial = null).value
    }

    val scope = rememberCoroutineScope()
    val logout: () -> Unit = {
        scope.launch {
            app.voiceManager.stopListening()
            app.authRepository.logout()
        }
    }
    val switchPatient: () -> Unit = { scope.launch { runCatching { app.authRepository.selectPatient(null) } } }

    when (val destination = resolveAccountDestination(ready && sessionLoaded, session, patients)) {
        AccountDestination.Loading -> LoadingScreen()
        AccountDestination.Login -> LoginScreen(app)
        AccountDestination.PatientSelection -> PatientSelectionScreen(app, patients.orEmpty(), logout)
        is AccountDestination.PatientHome -> key(destination.patientId) {
            content(destination.patientId, switchPatient, logout)
        }
    }
}

@StringRes
fun AuthFailure.messageRes(): Int = when (this) {
    AuthFailure.MISSING_CREDENTIALS -> R.string.auth_error_missing_credentials
    AuthFailure.INVALID_CREDENTIALS -> R.string.auth_error_invalid_credentials
    AuthFailure.INVALID_LOGIN_FORMAT -> R.string.auth_error_invalid_login_format
    AuthFailure.WEAK_PASSWORD -> R.string.auth_error_weak_password
    AuthFailure.CONSENT_REQUIRED -> R.string.auth_error_consent_required
    AuthFailure.LOGIN_TAKEN -> R.string.auth_error_login_taken
    AuthFailure.INVALID_PATIENT_NAME -> R.string.auth_error_patient_name
    AuthFailure.INVALID_PATIENT_AGE -> R.string.auth_error_patient_age
    AuthFailure.INVALID_PATIENT_REGION -> R.string.auth_error_patient_region
    AuthFailure.UNSUPPORTED_LANGUAGE -> R.string.auth_error_unsupported_language
    AuthFailure.SESSION_REQUIRED -> R.string.auth_error_session
    AuthFailure.PATIENT_ACCESS_DENIED -> R.string.auth_error_access_denied
}

private fun AppLanguage.storageName(): String = when (this) {
    AppLanguage.ENGLISH -> "English"
    AppLanguage.HINDI -> "Hindi"
    AppLanguage.ASSAMESE -> "Assamese"
}

/** Stored patient language ("Assamese") shown in its own script. */
private fun languageDisplayName(stored: String): String =
    AppLanguage.entries.find { it.storageName() == stored }?.displayName ?: stored

@Composable
private fun LoadingScreen() {
    val description = stringResource(R.string.account_loading)
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(Modifier.semantics { contentDescription = description })
    }
}

@Composable
private fun ErrorText(@StringRes message: Int?) {
    if (message == null) return
    Text(
        stringResource(message),
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
    )
}

@Composable
private fun LanguageChips(selected: AppLanguage, onSelected: (AppLanguage) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.language), style = MaterialTheme.typography.titleMedium)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppLanguage.entries.forEach { language ->
                FilterChip(
                    selected = language == selected,
                    onClick = { onSelected(language) },
                    label = { Text(language.displayName, style = MaterialTheme.typography.titleMedium) },
                    modifier = Modifier.heightIn(min = 48.dp)
                )
            }
        }
    }
}

/** A consent statement the whole row of which is one large, labelled checkbox. */
@Composable
private fun ConsentRow(@StringRes text: Int, checked: Boolean, enabled: Boolean, onChecked: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(value = checked, enabled = enabled, role = Role.Checkbox, onValueChange = onChecked)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Checkbox(checked = checked, onCheckedChange = null, enabled = enabled)
        Spacer(Modifier.width(12.dp))
        Text(stringResource(text), style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun LoginScreen(app: SaharaApplication) {
    val activity = LocalActivity.current
    val currentLanguage by app.languageManager.currentLanguage.collectAsState()
    var register by rememberSaveable { mutableStateOf(false) }
    var login by rememberSaveable { mutableStateOf("") }
    // The password is deliberately kept out of saved instance state; a language change clears it.
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var consent by rememberSaveable { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<Int?>(null) }
    val scope = rememberCoroutineScope()

    val submit: () -> Unit = {
        if (!loading) {
            loading = true
            error = null
            val attemptedRegister = register
            scope.launch {
                try {
                    if (attemptedRegister) app.authRepository.register(login, password, consent)
                    else app.authRepository.login(login, password)
                    val backendEmail = login
                    val backendPassword = password
                    password = ""
                    // Stage 3 vertical slice: verify the same credentials against the real FastAPI
                    // backend in the background. Fire-and-forget -- BackendAuthService never
                    // throws, and this must never delay or fail the local login above (a caregiver
                    // with no network must keep working exactly as before Stage 3). Launched on
                    // the application-lifetime scope, not this composable's `scope`: a successful
                    // login immediately navigates away and disposes LoginScreen, which would
                    // cancel a screen-scoped coroutine before the network call could finish.
                    app.applicationScope.launch {
                        val outcome = if (attemptedRegister) {
                            app.backendAuthService.registerAndVerify(backendEmail, backendPassword)
                        } else {
                            val loginOutcome = app.backendAuthService.loginAndVerify(backendEmail, backendPassword)
                            if (loginOutcome is BackendAuthService.Outcome.Failed) {
                                val regOutcome = app.backendAuthService.registerAndVerify(backendEmail, backendPassword)
                                if (regOutcome is BackendAuthService.Outcome.Verified) regOutcome else loginOutcome
                            } else {
                                loginOutcome
                            }
                        }
                        when (outcome) {
                            is BackendAuthService.Outcome.Verified -> {
                                Log.i("SaharaBackendAuth", "Backend verified caregiver id=${outcome.user.id}")
                                // Stage 3B, Part 5: pull this caregiver's authorized patients from
                                // the backend and link them into the LOCAL session's Room data, so
                                // a patient created on another device appears here too.
                                val localCaregiverId = app.authRepository.restoreSession()?.caregiverId
                                if (localCaregiverId != null) {
                                    when (val pull = app.patientPullSyncService.pullPatientsFor(localCaregiverId)) {
                                        is com.yourteam.sahara.sync.PatientPullSyncService.Outcome.Success -> {
                                            Log.i("SaharaPatientSync", "Pulled ${pull.patientCount} patient(s) from backend")
                                            // Stage 3C, Part 7: once this caregiver's patients are linked
                                            // locally, pull each patient's reminder definitions too, then
                                            // bring local AlarmManager scheduling in line with them.
                                            when (val reminderPull = app.reminderPullSyncService.pullRemindersFor(localCaregiverId)) {
                                                is com.yourteam.sahara.sync.ReminderPullSyncService.Outcome.Success -> {
                                                    Log.i("SaharaReminderSync", "Pulled ${reminderPull.reminderCount} reminder(s) from backend")
                                                    app.reminderNotificationHandler.rescheduleAll()
                                                }
                                                is com.yourteam.sahara.sync.ReminderPullSyncService.Outcome.Failed ->
                                                    Log.w("SaharaReminderSync", "Reminder pull unavailable: ${reminderPull.error}")
                                            }
                                        }
                                        is com.yourteam.sahara.sync.PatientPullSyncService.Outcome.Failed ->
                                            Log.w("SaharaPatientSync", "Patient pull unavailable: ${pull.error}")
                                    }
                                }
                            }
                            is BackendAuthService.Outcome.Failed ->
                                Log.w("SaharaBackendAuth", "Backend unavailable: ${outcome.error}")
                        }
                    }
                } catch (e: AuthException) {
                    error = e.failure.messageRes()
                } catch (_: Exception) {
                    error = R.string.auth_error_unexpected
                } finally {
                    loading = false
                }
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            stringResource(R.string.app_name),
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            stringResource(if (register) R.string.register_title else R.string.login_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.semantics { heading() }
        )

        LanguageChips(currentLanguage) { app.languageManager.setLanguage(activity, it) }

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
                Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
                Spacer(Modifier.width(12.dp))
                Text(
                    stringResource(R.string.login_prototype_notice),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
        }

        OutlinedTextField(
            value = login,
            onValueChange = { login = it },
            label = { Text(stringResource(R.string.login_username_label)) },
            singleLine = true,
            enabled = !loading,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth()
        )
        val toggleLabel = stringResource(if (passwordVisible) R.string.hide_password else R.string.show_password)
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text(stringResource(R.string.login_password_label)) },
            singleLine = true,
            enabled = !loading,
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = toggleLabel
                    )
                }
            },
            modifier = Modifier.fillMaxWidth()
        )

        if (register) ConsentRow(R.string.consent_account, consent, !loading) { consent = it }

        ErrorText(error)

        val loadingLabel = stringResource(R.string.account_loading)
        Button(
            onClick = submit,
            enabled = !loading,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .size(24.dp)
                        .semantics { contentDescription = loadingLabel },
                    strokeWidth = 3.dp
                )
            } else {
                Text(
                    stringResource(if (register) R.string.register_button else R.string.login_button),
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
        TextButton(
            onClick = { register = !register; error = null; password = "" },
            enabled = !loading,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
        ) {
            Text(stringResource(if (register) R.string.switch_to_login else R.string.switch_to_register))
        }

        if (BuildConfig.DEBUG) {
            Text(
                stringResource(R.string.dev_demo_credentials, DemoIdentity.LOGIN, DemoIdentity.PASSWORD),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PatientSelectionScreen(app: SaharaApplication, patients: List<Patient>, onLogout: () -> Unit) {
    var editing by remember { mutableStateOf<Patient?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<Int?>(null) }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.your_patients),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.semantics { heading() }
                    )
                },
                actions = {
                    IconButton(onClick = onLogout) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = stringResource(R.string.log_out))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(stringResource(R.string.patients_subtitle), style = MaterialTheme.typography.bodyLarge)
            Button(
                onClick = { editing = null; showEditor = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.add_patient), style = MaterialTheme.typography.titleMedium)
            }

            if (patients.isEmpty()) {
                Text(stringResource(R.string.no_patients), style = MaterialTheme.typography.bodyLarge)
            }

            patients.forEach { patient ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(patient.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                Text(
                                    stringResource(R.string.patient_details, patient.age, patient.region, languageDisplayName(patient.language)),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Button(
                            onClick = {
                                scope.launch {
                                    error = try {
                                        app.authRepository.selectPatient(patient.id)
                                        null
                                    } catch (e: AuthException) {
                                        e.failure.messageRes()
                                    } catch (_: Exception) {
                                        R.string.auth_error_unexpected
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 56.dp),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text(stringResource(R.string.open_patient_named, patient.name), style = MaterialTheme.typography.titleMedium)
                        }
                        OutlinedButton(
                            onClick = { editing = patient; showEditor = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text(stringResource(R.string.edit_patient_named, patient.name))
                        }
                    }
                }
            }

            ErrorText(error)
            Spacer(Modifier.height(16.dp))
        }
    }

    if (showEditor) PatientEditorDialog(app, editing) { showEditor = false }
}

@Composable
private fun PatientEditorDialog(app: SaharaApplication, patient: Patient?, onClose: () -> Unit) {
    var name by rememberSaveable { mutableStateOf(patient?.name ?: "") }
    var age by rememberSaveable { mutableStateOf(patient?.age?.toString() ?: "") }
    var region by rememberSaveable { mutableStateOf(patient?.region ?: "") }
    var language by rememberSaveable { mutableStateOf(patient?.language ?: AppLanguage.ENGLISH.storageName()) }
    var consent by rememberSaveable { mutableStateOf(false) }
    var error by remember { mutableStateOf<Int?>(null) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    // Stage 3D, Part 6/13/14: per-patient, caregiver-controlled, local-only preference -- edited
    // here alongside the other patient profile fields rather than on the dashboard.
    val personalizationPreferences = remember { PersonalizationPreferences(app) }
    var culturalContentEnabled by rememberSaveable {
        mutableStateOf(patient?.let { personalizationPreferences.isCulturalContentEnabled(it.id) } ?: true)
    }

    AlertDialog(
        onDismissRequest = { if (!loading) onClose() },
        title = { Text(stringResource(if (patient == null) R.string.add_patient else R.string.edit_patient)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.patient_name_label)) },
                    singleLine = true,
                    enabled = !loading,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = age,
                    onValueChange = { input -> age = input.filter { it.isDigit() }.take(3) },
                    label = { Text(stringResource(R.string.patient_age_label)) },
                    singleLine = true,
                    enabled = !loading,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = region,
                    onValueChange = { region = it },
                    label = { Text(stringResource(R.string.patient_region_label)) },
                    singleLine = true,
                    enabled = !loading,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(stringResource(R.string.patient_language_label), style = MaterialTheme.typography.titleSmall)
                Column(Modifier.selectableGroup()) {
                    AppLanguage.entries.forEach { option ->
                        val stored = option.storageName()
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .selectable(selected = language == stored, enabled = !loading, role = Role.RadioButton) { language = stored },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = language == stored, onClick = null, enabled = !loading)
                            Spacer(Modifier.width(12.dp))
                            Text(option.displayName, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
                if (NerRegions.match(region)?.culturalContentPack != null) {
                    ConsentRow(R.string.cultural_content_description, culturalContentEnabled, !loading) { culturalContentEnabled = it }
                }
                if (patient == null) ConsentRow(R.string.consent_patient, consent, !loading) { consent = it }
                ErrorText(error)
                if (loading) {
                    val loadingLabel = stringResource(R.string.account_loading)
                    CircularProgressIndicator(Modifier.semantics { contentDescription = loadingLabel })
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !loading,
                onClick = {
                    loading = true
                    error = null
                    scope.launch {
                        try {
                            val saved = app.authRepository.savePatient(patient?.id, name, age.toIntOrNull() ?: -1, region, language, consent)
                            app.syncManager.enqueuePatientSync(saved.syncId)
                            personalizationPreferences.setCulturalContentEnabled(saved.id, culturalContentEnabled)
                            onClose()
                        } catch (e: AuthException) {
                            error = e.failure.messageRes()
                        } catch (_: Exception) {
                            error = R.string.auth_error_unexpected
                        } finally {
                            loading = false
                        }
                    }
                }
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            TextButton(enabled = !loading, onClick = onClose) { Text(stringResource(R.string.cancel)) }
        }
    )
}
