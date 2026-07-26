package com.jmdl

import android.app.Application
import com.jmdl.download.Notifications
import com.jmdl.util.AppLogger
import java.io.File

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        AppLogger.init(this)
        Notifications.ensureChannel(this)
        runCatching { File(cacheDir, "view").deleteRecursively() }
            .onFailure { AppLogger.w("Failed to clear view cache", it) }
    }
}