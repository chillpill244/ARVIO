package com.arflix.tv.shared.theme
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontWeight
import com.arflix.tv.shared.R

actual val JetBrainsSansFontFamily = FontFamily(
    Font(R.font.jetbrains_sans_regular, FontWeight.Normal),
    Font(R.font.jetbrains_sans_semibold, FontWeight.SemiBold),
    Font(R.font.jetbrains_sans_bold, FontWeight.Bold)
)
