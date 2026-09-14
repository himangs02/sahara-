package com.yourteam.sahara.personalization

import android.content.Context

/**
 * Whether cultural/familiar content is shown, kept PER PATIENT (Stage 3D, Part 6/13): two
 * patient profiles on the same device must never share this setting. Local-only by design
 * (Part 19/20) -- a personalization preference is not medical data, and this milestone doesn't
 * add a second synced-profile field for it; see the Stage 3D report for that tradeoff.
 */
class PersonalizationPreferences(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Defaults to on: familiar content is only ever shown when a region profile actually
     * has a pack, so this default never surprises a patient with no matching region. */
    fun isCulturalContentEnabled(patientId: String): Boolean = prefs.getBoolean(key(patientId), true)

    fun setCulturalContentEnabled(patientId: String, enabled: Boolean) {
        prefs.edit().putBoolean(key(patientId), enabled).apply()
    }

    private fun key(patientId: String) = "$KEY_PREFIX$patientId"

    private companion object {
        const val PREFS_NAME = "sahara_personalization_prefs"
        const val KEY_PREFIX = "cultural_content_enabled_"
    }
}
