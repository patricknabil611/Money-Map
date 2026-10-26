package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

private val DarkColorScheme = darkColorScheme(
  primary = DarkMoneyMapColors.leftover,
  onPrimary = DarkMoneyMapColors.bg,
  primaryContainer = DarkMoneyMapColors.leftoverSoft,
  onPrimaryContainer = DarkMoneyMapColors.leftover,
  secondary = DarkMoneyMapColors.income,
  onSecondary = DarkMoneyMapColors.bg,
  secondaryContainer = DarkMoneyMapColors.incomeSoft,
  onSecondaryContainer = DarkMoneyMapColors.income,
  tertiary = DarkMoneyMapColors.year,
  onTertiary = DarkMoneyMapColors.bg,
  tertiaryContainer = DarkMoneyMapColors.yearSoft,
  onTertiaryContainer = DarkMoneyMapColors.year,
  background = DarkMoneyMapColors.bg,
  onBackground = DarkMoneyMapColors.ink,
  surface = DarkMoneyMapColors.surface,
  onSurface = DarkMoneyMapColors.ink,
  surfaceVariant = DarkMoneyMapColors.surface2,
  onSurfaceVariant = DarkMoneyMapColors.muted,
  outline = DarkMoneyMapColors.line,
  error = DarkMoneyMapColors.expense,
  errorContainer = DarkMoneyMapColors.expenseSoft,
  onError = DarkMoneyMapColors.bg,
  onErrorContainer = DarkMoneyMapColors.expense
)

private val LightColorScheme = lightColorScheme(
  primary = LightMoneyMapColors.leftover,
  onPrimary = LightMoneyMapColors.surface,
  primaryContainer = LightMoneyMapColors.leftoverSoft,
  onPrimaryContainer = LightMoneyMapColors.leftover,
  secondary = LightMoneyMapColors.income,
  onSecondary = LightMoneyMapColors.surface,
  secondaryContainer = LightMoneyMapColors.incomeSoft,
  onSecondaryContainer = LightMoneyMapColors.income,
  tertiary = LightMoneyMapColors.year,
  onTertiary = LightMoneyMapColors.surface,
  tertiaryContainer = LightMoneyMapColors.yearSoft,
  onTertiaryContainer = LightMoneyMapColors.year,
  background = LightMoneyMapColors.bg,
  onBackground = LightMoneyMapColors.ink,
  surface = LightMoneyMapColors.surface,
  onSurface = LightMoneyMapColors.ink,
  surfaceVariant = LightMoneyMapColors.surface2,
  onSurfaceVariant = LightMoneyMapColors.muted,
  outline = LightMoneyMapColors.line,
  error = LightMoneyMapColors.expense,
  errorContainer = LightMoneyMapColors.expenseSoft,
  onError = LightMoneyMapColors.surface,
  onErrorContainer = LightMoneyMapColors.expense
)

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  content: @Composable () -> Unit,
) {
  val customColors = if (darkTheme) DarkMoneyMapColors else LightMoneyMapColors
  val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

  CompositionLocalProvider(LocalMoneyMapColors provides customColors) {
    MaterialTheme(
      colorScheme = colorScheme,
      typography = Typography,
      content = content
    )
  }
}
