package com.kaique.casahub

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.kaique.casahub.ui.CasaHubApp
import com.kaique.casahub.ui.HomeViewModel
import com.kaique.casahub.ui.theme.CasaHubTheme

class MainActivity : ComponentActivity() {
    private val vm: HomeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        // Atalhos para abrir uma tela direto (ex.: adb shell am start --ez demo true --es screen device:d3)
        if (intent?.getBooleanExtra("demo", false) == true && !vm.store.state.value.loggedIn) vm.store.startDemo()
        val screen = intent?.getStringExtra("screen")
        setContent {
            CasaHubTheme {
                CasaHubApp(vm.store, initialScreen = screen)
            }
        }
    }
}
