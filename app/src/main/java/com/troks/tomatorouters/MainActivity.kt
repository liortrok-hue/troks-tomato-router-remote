package com.troks.tomatorouters

import android.graphics.Color
import android.os.Bundle
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
                            BackHandler { vm.logout() }
                            MainScreen(api, vm)
                        }
                    }
                }
            }
        }
    }
}
