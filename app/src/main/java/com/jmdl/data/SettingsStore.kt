package com.jmdl.data

import android.content.Context

/** Tiny SharedPreferences wrapper for app settings (save folder, theme, reader mode, etc.). */
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

    /** Custom theme / accent color (ARGB Int), default 0xFF2979FF (Material Blue). */
    var themeColor: Int
        get() = prefs.getInt(KEY_THEME_COLOR, 0xFF2979FF.toInt())
        set(value) = prefs.edit().putInt(KEY_THEME_COLOR, value).apply()

    /** Max concurrent downloads (1-5), default 3. */
    var maxConcurrent: Int
        get() = prefs.getInt(KEY_MAX_CONCURRENT, 3).coerceIn(1, 5)
        set(value) = prefs.edit().putInt(KEY_MAX_CONCURRENT, value.coerceIn(1, 5)).apply()

    private companion object {
        const val KEY_SAVE_URI = "saveTreeUri"
        const val KEY_WEBTOON = "webtoonMode"
        const val KEY_BG_COLOR = "bgColor"
        const val KEY_BG_IMAGE = "bgImageUri"
        const val KEY_THEME_COLOR = "themeColor"
        const val KEY_MAX_CONCURRENT = "maxConcurrent"
    }
}