package com.troks.tomatorouters

import android.graphics.Color
import android.os.Bundle
import android.os.SystemClock
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import android.content.Intent
import androidx.activity.viewModels
import com.troks.tomatorouters.ui.LogsDialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.troks.tomatorouters.data.LogAlerts
import kotlinx.coroutines.delay
import com.troks.tomatorouters.ui.AppViewModel
import com.troks.tomatorouters.ui.LocalStrings
import com.troks.tomatorouters.ui.LoginScreen
import com.troks.tomatorouters.ui.MainScreen
import com.troks.tomatorouters.ui.TomatoTheme
import com.troks.tomatorouters.ui.isDark

class MainActivity : FragmentActivity() {
    private val appVm: AppViewModel by viewModels()

    /** A tap on a log alert notification opens the system log (after login, if needed). */
    private fun handleIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(LogAlerts.EXTRA_OPEN_LOGS, false) == true) {
            intent.removeExtra(LogAlerts.EXTRA_OPEN_LOGS) // not again on rotation / recreation
            appVm.openLogs = true
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleIntent(intent)
        enableEdgeToEdge()
        setContent {
            val vm: AppViewModel = viewModel()
            val dark = isDark(vm.themeMode)

            // status/navigation bar icons follow the app's theme, not the system's
            LaunchedEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT)
                else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }

            TomatoTheme(dark = dark, dynamic = vm.dynamicColor, textScale = vm.textScale, palette = vm.palette) {
                val lang = vm.language
                // Text and layout direction follow the language picked in the app
                CompositionLocalProvider(
                    LocalStrings provides lang.strings,
                    LocalLayoutDirection provides lang.direction,
                ) {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        val api = vm.api
                        if (api == null) {
                            LoginScreen(vm)
                        } else {
                            // Back never logs out (that's the Logout button). Press it twice within
                            // 2 seconds to leave the app; the session stays open in the background.
                            var lastBack by remember { mutableLongStateOf(0L) }
                            BackHandler {
                                val now = SystemClock.uptimeMillis()
                                if (now - lastBack < 2000) {
                                    lastBack = 0L
                                    moveTaskToBack(true)
                                } else {
                                    lastBack = now
                                    Toast.makeText(this@MainActivity, lang.strings.pressBackToExit, Toast.LENGTH_SHORT).show()
                                }
                            }
                            // log alerts while the app is on screen, every 1 / 3 / 5 min
                            // (when it's closed, LogAlertWorker checks on its own schedule)
                            val alertsOn = vm.logAlertsOpen && !vm.isDemo
                            val everyMin = vm.logAlertOpenMinutes
                            LaunchedEffect(api, alertsOn, everyMin) {
                                if (alertsOn) lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                                    while (true) {
                                        runCatching { LogAlerts.check(applicationContext, api) }
                                        delay(everyMin * 60_000L)
                                    }
                                }
                            }
                            MainScreen(api, vm)
                            if (vm.openLogs) LogsDialog(api) { vm.openLogs = false }
                        }
                    }
                }
            }
        }
    }
}
