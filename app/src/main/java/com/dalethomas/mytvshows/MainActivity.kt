package com.dalethomas.mytvshows

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Bundle
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme
import com.dalethomas.mytvshows.ui.MainScreen
import com.dalethomas.mytvshows.ui.MainViewModel

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyDemoFlag(intent)
        hideSystemBars()
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                MainScreen(vm)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        applyDemoFlag(intent)
    }

    /**
     * Debug builds only: `--ez demo_all_apps true` shows every service tile even if the
     * app is not installed (used to record the demo in an emulator).
     */
    private fun applyDemoFlag(intent: Intent?) {
        val debuggable = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        if (debuggable && intent?.getBooleanExtra("demo_all_apps", false) == true) vm.showAllApps.value = true
    }

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    override fun onResume() {
        super.onResume()
        vm.refreshIfStale()
    }
}
