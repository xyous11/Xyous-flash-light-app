package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme =
  darkColorScheme(
    primary = FlashAmber,
    secondary = ElectricCyan,
    tertiary = CrimsonSos,
    background = SlateBack,
    surface = CardBack,
    onBackground = TextWhite,
    onSurface = TextWhite,
    surfaceVariant = SoftOffGrey
  )

@Composable
fun MyApplicationTheme(
  forceDark: Boolean = true, // Force dark mode to protect eyes in the dark
  content: @Composable () -> Unit,
) {
  val colorScheme = DarkColorScheme

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
