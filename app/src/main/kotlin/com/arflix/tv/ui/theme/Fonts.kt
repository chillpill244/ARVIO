package com.arflix.tv.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.arflix.tv.R

/**
 * Bundled Inter font family.
 *
 * Uses these files in `app/src/main/res/font/`:
 * - `Inter-VariableFont_opsz_wght.ttf` (normal)
 * - `Inter-Italic-VariableFont_opsz_wght.ttf` (italic)
 */
val InterFontFamily = FontFamily(
    Font(R.font.inter_variablefont_opsz_wght, weight = FontWeight.Normal),
    Font(R.font.inter_variablefont_opsz_wght, weight = FontWeight.Medium),
    Font(R.font.inter_variablefont_opsz_wght, weight = FontWeight.SemiBold),
    Font(R.font.inter_variablefont_opsz_wght, weight = FontWeight.Bold),
    Font(R.font.inter_variablefont_opsz_wght, weight = FontWeight.Black),
)

/**
 * Bundled JetBrains Sans font family — used for headings/titles (Nuvio-style).
 *
 * Uses these files in `app/src/main/res/font/`:
 * - `jetbrains_sans_regular.ttf`
 * - `jetbrains_sans_semibold.ttf`
 * - `jetbrains_sans_bold.ttf`
 */
val JetBrainsSansFontFamily = FontFamily(
    Font(R.font.jetbrains_sans_regular, weight = FontWeight.Normal),
    Font(R.font.jetbrains_sans_semibold, weight = FontWeight.SemiBold),
    Font(R.font.jetbrains_sans_bold, weight = FontWeight.Bold),
)

