package com.troks.tomatorouters

import android.graphics.Color
import android.os.Bundle
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.ComponentActivity
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.troks.tomatorouters.ui.AppViewModel
import com.troks.tomatorouters.ui.LocalStrings
import com.troks.tomatorouters.ui.LoginScreen
import com.troks.tomatorouters.ui.MainScreen
import com.troks.tomatorouters.ui.TomatoTheme
import com.troks.tomatorouters.ui.isDark

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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

            TomatoTheme(dark = dark, dynamic = vm.dynamicColor, textScale = vm.textScale) {
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
                            MainScreen(api, vm)
                        }
                    }
                }
            }
        }
    }
}
