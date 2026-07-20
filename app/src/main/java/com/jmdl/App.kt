package com.jmdl

import android.app.Application
import com.jmdl.download.Notifications
import java.io.File

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Notifications.ensureChannel(this)
        // Clear any leftover viewer-extraction cache from a previous run.
        runCatching { File(cacheDir, "view").deleteRecursively() }
    }
}
