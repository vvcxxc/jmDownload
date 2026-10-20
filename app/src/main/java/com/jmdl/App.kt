package com.jmdl

import android.app.Application
import com.jmdl.download.Notifications
import com.jmdl.util.AppLogger

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        AppLogger.init(this)
        Notifications.ensureChannel(this)
        // The viewer cache is fingerprint-checked and pruned by extractForView()
        // when another book is opened, so it survives restarts without blocking
        // startup or racing with a fresh extraction.
    }
}