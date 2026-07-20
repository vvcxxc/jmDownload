package com.jmdl.data

import android.content.Context

/** Tiny SharedPreferences wrapper for the single persisted setting: the SAF save-folder URI. */
class SettingsStore(context: Context) {
    private val prefs =
        context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var saveTreeUri: String?
        get() = prefs.getString(KEY_SAVE_URI, null)
        set(value) = prefs.edit().putString(KEY_SAVE_URI, value).apply()

    private companion object {
        const val KEY_SAVE_URI = "saveTreeUri"
    }
}
