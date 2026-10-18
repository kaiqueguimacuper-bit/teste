package com.kaique.casahub

import android.os.Bundle
import androidx.activity.ComponentActivity
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
        enableEdgeToEdge()
        setContent {
            CasaHubTheme {
                CasaHubApp(vm.store)
            }
        }
    }
}
