package com.example.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)

@Immutable
data class MoneyMapColors(
  val bg: Color,
  val surface: Color,
  val surface2: Color,
  val ink: Color,
  val muted: Color,
  val line: Color,
  val income: Color,
  val incomeSoft: Color,
  val expense: Color,
  val expenseSoft: Color,
  val leftover: Color,
  val leftoverSoft: Color,
  val year: Color,
  val yearSoft: Color,
  val head: Color,
  val headInk: Color,
)

val LightMoneyMapColors = MoneyMapColors(
  bg = Color(0xFFECEFF3),
  surface = Color(0xFFFFFFFF),
  surface2 = Color(0xFFF4F6F9),
  ink = Color(0xFF000000),
  muted = Color(0xFF374151),
  line = Color(0xFFD7DCE4),
  income = Color(0xFF157552),
  incomeSoft = Color(0xFFDDF0E6),
  expense = Color(0xFFB9372F),
  expenseSoft = Color(0xFFF9E1DE),
  leftover = Color(0xFF2A50CC),
  leftoverSoft = Color(0xFFE1E8FB),
  year = Color(0xFF9A5B00),
  yearSoft = Color(0xFFFFEFCF),
  head = Color(0xFF172033),
  headInk = Color(0xFFFFFFFF)
)

val DarkMoneyMapColors = MoneyMapColors(
  bg = Color(0xFF0E131B),
  surface = Color(0xFF161D28),
  surface2 = Color(0xFF1C2533),
  ink = Color(0xFFE6EBF2),
  muted = Color(0xFF97A2B3),
  line = Color(0xFF2A3445),
  income = Color(0xFF55C792),
  incomeSoft = Color(0xFF143428),
  expense = Color(0xFFF27A71),
  expenseSoft = Color(0xFF3B1D1B),
  leftover = Color(0xFF86A2FF),
  leftoverSoft = Color(0xFF1C2645),
  year = Color(0xFFF2B947),
  yearSoft = Color(0xFF33270F),
  head = Color(0xFFE6EBF2),
  headInk = Color(0xFF0E131B)
)

val LocalMoneyMapColors = staticCompositionLocalOf { LightMoneyMapColors }
