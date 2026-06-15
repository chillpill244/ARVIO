package com.muvio.shared.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.rememberNavController
import com.muvio.shared.ui.navigation.AppNavHost
import com.muvio.shared.ui.theme.AppTheme

@Composable
fun App() {
    AppTheme {
        val navController = rememberNavController()
        AppNavHost(navController = navController)
    }
}
