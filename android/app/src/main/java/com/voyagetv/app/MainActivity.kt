package com.voyagetv.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {

    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        askNotificationPermission()
        setContent {
            VoyageTheme {
                AppRoot(vm)
            }
        }
    }

    /** Android 13 起通知要授权，媒体会话的通知栏才会显示；不给也能播，只是没有通知。 */
    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT < 33) return
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) return
        val launcher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
        launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
