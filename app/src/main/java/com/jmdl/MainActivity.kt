package com.jmdl

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import com.jmdl.ui.JmApp
import com.jmdl.ui.TaskViewModel
import com.jmdl.ui.theme.JmTheme

class MainActivity : ComponentActivity() {

    private val vm: TaskViewModel by viewModels()

    private val notifPermLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* result ignored */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        maybeRequestNotifPermission()
        setContent {
            JmTheme {
                JmApp(vm)
            }
        }
    }

    private fun maybeRequestNotifPermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            val granted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (granted) return

            // Ask once on first launch. Re-prompting on every launch is noisy,
            // especially after the user has denied it.
            val prefs = getSharedPreferences("settings", MODE_PRIVATE)
            if (prefs.getBoolean(KEY_NOTIF_ASKED, false)) return
            prefs.edit().putBoolean(KEY_NOTIF_ASKED, true).apply()
            notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private companion object {
        const val KEY_NOTIF_ASKED = "notifPermissionAsked"
    }
}
