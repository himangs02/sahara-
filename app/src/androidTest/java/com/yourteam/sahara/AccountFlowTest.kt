package com.yourteam.sahara

import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yourteam.sahara.auth.DemoIdentity
import com.yourteam.sahara.language.AppLanguage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

/**
 * Drives the real login and patient-selection UI on the device: an unauthenticated user is gated to
 * login, a valid login reaches patient selection and then the patient home, and logout returns to
 * login without any way to navigate back into the authenticated screens.
 */
@RunWith(AndroidJUnit4::class)
class AccountFlowTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext.applicationContext as SaharaApplication
    private val ui = DeviceUi(instrumentation)
    private var scenario: ActivityScenario<MainActivity>? = null
    private lateinit var originalLanguage: AppLanguage

    private fun localized(language: AppLanguage): Context {
        val config = Configuration(instrumentation.targetContext.resources.configuration)
        config.setLocale(Locale.forLanguageTag(language.code))
        return instrumentation.targetContext.createConfigurationContext(config)
    }

    private fun startLoggedOut(language: AppLanguage) {
        instrumentation.runOnMainSync { app.languageManager.setLanguage(null, language) }
        // Make sure the demo caregiver exists, then end the session.
        TestSession.signInDemo(app)
        TestSession.signOut(app)
    }

    @Before fun rememberLanguage() {
        instrumentation.runOnMainSync { originalLanguage = app.languageManager.currentLanguage.value }
    }

    @After fun cleanUp() {
        scenario?.close()
        instrumentation.runOnMainSync { app.languageManager.setLanguage(null, originalLanguage) }
    }

    @Test fun loginSelectPatientAndLogout() {
        startLoggedOut(AppLanguage.ENGLISH)
        val s = localized(AppLanguage.ENGLISH)
        scenario = ActivityScenario.launch(MainActivity::class.java)

        // Unauthenticated users land on the login screen, not on any patient content.
        ui.waitForText(s.getString(R.string.login_title))
        ui.waitForText(s.getString(R.string.login_prototype_notice))
        assertNull("Home must not be reachable before login", ui.findText(s.getString(R.string.welcome_to_sahara)))

        // A wrong password shows an error and stays on login.
        ui.typeIntoFields(DemoIdentity.LOGIN, "not the password")
        ui.clickText(s.getString(R.string.login_button))
        ui.waitForText(s.getString(R.string.auth_error_invalid_credentials))
        assertNull(ui.findText(s.getString(R.string.your_patients)))

        // A valid login reaches patient selection, then the selected patient's home.
        ui.typeIntoFields(DemoIdentity.LOGIN, DemoIdentity.PASSWORD)
        ui.clickText(s.getString(R.string.login_button))
        ui.waitForText(s.getString(R.string.patients_subtitle))
        ui.clickText(s.getString(R.string.open_patient_named, "Kamala Devi"))
        ui.waitForText(s.getString(R.string.welcome_to_sahara))
        assertNull("The elderly home offers no logout control", ui.findText(s.getString(R.string.log_out)))

        // Log out from the caregiver area.
        ui.clickText(s.getString(R.string.nav_caregiver))
        ui.waitForText(s.getString(R.string.caregiver_dashboard))
        ui.clickText(s.getString(R.string.log_out))
        ui.waitForText(s.getString(R.string.login_title))
        assertNull(ui.findText(s.getString(R.string.welcome_to_sahara)))
        assertNull("Logout clears the stored session", runBlocking { app.authRepository.sessions.first() })

        // Pressing back after logout must not re-enter the authenticated screens.
        scenario!!.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        instrumentation.waitForIdleSync()
        assertNull("Back navigation must not restore the patient home", ui.findText(s.getString(R.string.welcome_to_sahara)))
        assertNull(ui.findText(s.getString(R.string.caregiver_dashboard)))
    }

    @Test fun sessionSurvivesActivityRestart() {
        startLoggedOut(AppLanguage.ENGLISH)
        val s = localized(AppLanguage.ENGLISH)
        TestSession.signInDemo(app)
        scenario = ActivityScenario.launch(MainActivity::class.java)
        ui.waitForText(s.getString(R.string.welcome_to_sahara))

        // Closing and reopening the Activity restores the local session without logging in again.
        scenario!!.close()
        scenario = ActivityScenario.launch(MainActivity::class.java)
        ui.waitForText(s.getString(R.string.welcome_to_sahara))
        assertNull(ui.findText(s.getString(R.string.login_title)))
    }

    @Test fun loginScreenFollowsSelectedLanguage() {
        startLoggedOut(AppLanguage.HINDI)
        val hi = localized(AppLanguage.HINDI)
        val en = localized(AppLanguage.ENGLISH)
        assertNotEquals(en.getString(R.string.login_title), hi.getString(R.string.login_title))

        scenario = ActivityScenario.launch(MainActivity::class.java)
        ui.waitForText(hi.getString(R.string.login_title))
        ui.waitForText(hi.getString(R.string.login_button))

        // Switching language from the login screen itself works before anyone signs in.
        ui.clickText(AppLanguage.ASSAMESE.displayName)
        val assamese = localized(AppLanguage.ASSAMESE)
        ui.waitForText(assamese.getString(R.string.login_title))
        instrumentation.runOnMainSync { assertEquals(AppLanguage.ASSAMESE, app.languageManager.currentLanguage.value) }
    }
}
