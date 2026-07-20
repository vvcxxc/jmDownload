package com.jmdl

import android.app.Application
import com.jmdl.download.Notifications

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Notifications.ensureChannel(this)
    }
}
