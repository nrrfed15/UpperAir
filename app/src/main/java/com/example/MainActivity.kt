package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.UpperAirApp
import com.example.ui.UpperAirViewModel
import com.example.ui.theme.UpperAirTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as UpperAirApplication).container

        setContent {
            val vm: UpperAirViewModel = viewModel(
                factory = UpperAirViewModel.Factory(
                    repository = container.repository,
                    syncScheduler = container.syncScheduler
                )
            )
            val uiState by vm.uiState.collectAsStateWithLifecycle()

            UpperAirTheme(darkTheme = uiState.settings.darkTheme) {
                UpperAirApp(viewModel = vm)
            }
        }
    }
}
