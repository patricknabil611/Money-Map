package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/**
 * Standardized corner radii for P Money:
 * - Small (8.dp): Chips, tags, small indicator pills, and badges.
 * - Medium (12.dp): Buttons, text field inputs, and actionable controls.
 * - Large (16.dp): Cards, dialog containers, modal bottom sheets.
 */
object AppShapes {
  val Small = RoundedCornerShape(8.dp)
  val Chip = Small

  val Medium = RoundedCornerShape(12.dp)
  val Button = Medium
  val Input = Medium

  val Large = RoundedCornerShape(16.dp)
  val Card = Large
  val Sheet = Large
  val Dialog = Large
}
