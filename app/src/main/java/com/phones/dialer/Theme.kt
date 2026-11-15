package com.phones.dialer

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val SamBg = Color(0xFF171719)
val SamNav = Color(0xFF2B2B2E)
val SamNavSel = Color(0xFF48484B)
val SamGreen = Color(0xFF12A860)
val SamRed = Color(0xFFFF3B30)
val SamGray = Color(0xFFB0B0B5)

@Composable
fun SamTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = SamGreen,
            background = SamBg,
            surface = SamBg,
            onBackground = Color.White,
            onSurface = Color.White
        ),
        content = content
    )
}
