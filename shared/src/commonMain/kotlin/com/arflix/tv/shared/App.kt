package com.arflix.tv.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.arflix.tv.shared.theme.AppTheme
import com.arflix.tv.shared.theme.ArvioTypography

@Composable
fun App() {
    AppTheme {
        Box(
            modifier = Modifier.fillMaxSize().background(com.arflix.tv.shared.theme.BackgroundDark),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Hello from Compose Multiplatform!",
                style = ArvioTypography.titleLarge,
                color = com.arflix.tv.shared.theme.TextPrimary
            )
        }
    }
}
