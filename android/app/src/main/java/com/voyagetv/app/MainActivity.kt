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

import android.view.KeyEvent

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

    /** 设置里关掉「允许后台播放」时，退到后台就暂停，别在口袋里偷偷跑流量。 */
    override fun onStop() {
        super.onStop()
        if (!vm.settings.value.allowBackground) {
            vm.pauseForBackground()
        }
    }

    /**
     * 电视遥控器：正在播放时，上键 = 上一台、下键 = 下一台。
     *
     * 放在 dispatchKeyEvent 里，比放在页面里的 onKeyEvent 更早期，不会被 Compose 的焦点移动先吃掉。
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (vm.uiState.value.current != null &&
            event.action == KeyEvent.ACTION_DOWN &&
            event.repeatCount == 0
        ) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_UP -> {
                    vm.playPrevious()
                    return true
                }

                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    vm.playNext()
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
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
