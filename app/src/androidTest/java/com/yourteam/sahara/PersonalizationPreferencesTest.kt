package com.yourteam.sahara

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yourteam.sahara.personalization.PersonalizationPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Stage 3D: cultural-content preference must be stored per patient, never shared. */
@RunWith(AndroidJUnit4::class)
class PersonalizationPreferencesTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var preferences: PersonalizationPreferences

    @Before
    fun setUp() {
        preferences = PersonalizationPreferences(context)
    }

    @Test
    fun defaultsToEnabledForAPatientNeverConfiguredBefore() {
        assertTrue(preferences.isCulturalContentEnabled("patient_never_seen_before"))
    }

    @Test
    fun testM_changingOnePatientsPreferenceDoesNotChangeAnothers() {
        val patientA = "patient_A_${System.nanoTime()}"
        val patientB = "patient_B_${System.nanoTime()}"

        preferences.setCulturalContentEnabled(patientA, false)
        preferences.setCulturalContentEnabled(patientB, true)

        assertEquals(false, preferences.isCulturalContentEnabled(patientA))
        assertEquals(true, preferences.isCulturalContentEnabled(patientB))

        // Flip A again -- B must remain untouched.
        preferences.setCulturalContentEnabled(patientA, true)
        assertEquals(true, preferences.isCulturalContentEnabled(patientA))
        assertEquals(true, preferences.isCulturalContentEnabled(patientB))
    }

    @Test
    fun preferenceSurvivesANewPersonalizationPreferencesInstance() {
        val patientId = "patient_persist_${System.nanoTime()}"
        preferences.setCulturalContentEnabled(patientId, false)

        val reopened = PersonalizationPreferences(context)

        assertEquals(false, reopened.isCulturalContentEnabled(patientId))
    }
}
