package com.jmdl.data

import android.content.Context

/** Tiny SharedPreferences wrapper for the single persisted setting: the SAF save-folder URI. */
class SettingsStore(context: Context) {
    private val prefs =
        context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var saveTreeUri: String?
        get() = prefs.getString(KEY_SAVE_URI, null)
        set(value) = prefs.edit().putString(KEY_SAVE_URI, value).apply()

    /** Reading mode for the viewer: true = 条漫 (vertical), false = 翻页 (paged). Global + persisted. */
    var webtoonMode: Boolean
        get() = prefs.getBoolean(KEY_WEBTOON, false)
        set(value) = prefs.edit().putBoolean(KEY_WEBTOON, value).apply()

    /** Custom home background color (ARGB Int), or null for none. */
    var bgColor: Int?
        get() = if (prefs.contains(KEY_BG_COLOR)) prefs.getInt(KEY_BG_COLOR, 0) else null
        set(value) {
            val e = prefs.edit()
            if (value == null) e.remove(KEY_BG_COLOR) else e.putInt(KEY_BG_COLOR, value)
            e.apply()
        }

    /** Custom home background image Uri, or null for none. */
    var bgImageUri: String?
        get() = prefs.getString(KEY_BG_IMAGE, null)
        set(value) = prefs.edit().putString(KEY_BG_IMAGE, value).apply()

    private companion object {
        const val KEY_SAVE_URI = "saveTreeUri"
        const val KEY_WEBTOON = "webtoonMode"
        const val KEY_BG_COLOR = "bgColor"
        const val KEY_BG_IMAGE = "bgImageUri"
    }
}
