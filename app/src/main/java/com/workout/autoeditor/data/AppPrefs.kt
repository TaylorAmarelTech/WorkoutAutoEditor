package com.workout.autoeditor.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Single SharedPreferences-backed store for app config the user can change.
 * Values are read on every access so changes take effect immediately.
 */
class AppPrefs(ctx: Context) {

    private val prefs: SharedPreferences =
        ctx.getSharedPreferences("workout_auto_editor_prefs", Context.MODE_PRIVATE)

    var customModelUrl: String?
        get() = prefs.getString(KEY_MODEL_URL, null)?.takeIf { it.isNotBlank() }
        set(value) = prefs.edit().putString(KEY_MODEL_URL, value?.trim()).apply()

    var hfToken: String?
        get() = prefs.getString(KEY_HF_TOKEN, null)?.takeIf { it.isNotBlank() }
        set(value) = prefs.edit().putString(KEY_HF_TOKEN, value?.trim()).apply()

    /** True if the user explicitly chose to skip the model download. */
    var skippedModel: Boolean
        get() = prefs.getBoolean(KEY_SKIPPED_MODEL, false)
        set(value) = prefs.edit().putBoolean(KEY_SKIPPED_MODEL, value).apply()

    companion object {
        private const val KEY_MODEL_URL = "model_url"
        private const val KEY_HF_TOKEN = "hf_token"
        private const val KEY_SKIPPED_MODEL = "skipped_model"
    }
}
