package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AppShapes
import com.example.ui.theme.LocalMoneyMapColors

/**
 * Shared button styles for P Money.
 * - Standard 48.dp minimum height for touch accessibility.
 * - Uniform 12.dp corner radius across all buttons (AppShapes.Button).
 * - Consistent content padding (horizontal 16.dp).
 * - Standard 18.dp icon sizing with 8.dp horizontal spacing.
 * - Single-line text with ellipsis and SemiBold weight.
 */

@Composable
fun AppPrimaryButton(
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  text: String? = null,
  icon: ImageVector? = null,
  containerColor: Color? = null,
  contentColor: Color? = null,
  minHeight: Dp = 48.dp,
  contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
  content: (@Composable RowScope.() -> Unit)? = null
) {
  val colors = LocalMoneyMapColors.current
  val effectiveContainer = containerColor ?: colors.head
  val effectiveContent = contentColor ?: colors.headInk

  Button(
    onClick = onClick,
    enabled = enabled,
    shape = AppShapes.Button,
    colors = ButtonDefaults.buttonColors(
      containerColor = effectiveContainer,
      contentColor = effectiveContent,
      disabledContainerColor = colors.surface2,
      disabledContentColor = colors.muted
    ),
    contentPadding = contentPadding,
    modifier = modifier.heightIn(min = minHeight)
  ) {
    if (content != null) {
      content()
    } else {
      if (icon != null) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          modifier = Modifier.size(18.dp)
        )
        if (!text.isNullOrEmpty()) {
          Spacer(modifier = Modifier.width(8.dp))
        }
      }
      if (!text.isNullOrEmpty()) {
        Text(
          text = text,
          fontSize = 13.5.sp,
          fontWeight = FontWeight.SemiBold,
          textAlign = TextAlign.Center,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
      }
    }
  }
}

@Composable
fun AppSecondaryButton(
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  text: String? = null,
  icon: ImageVector? = null,
  containerColor: Color = Color.Transparent,
  contentColor: Color? = null,
  borderColor: Color? = null,
  border: BorderStroke? = null,
  minHeight: Dp = 48.dp,
  contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
  content: (@Composable RowScope.() -> Unit)? = null
) {
  val colors = LocalMoneyMapColors.current
  val effectiveContent = contentColor ?: colors.ink
  val effectiveBorder = border ?: BorderStroke(1.dp, borderColor ?: colors.line)

  OutlinedButton(
    onClick = onClick,
    enabled = enabled,
    shape = AppShapes.Button,
    border = effectiveBorder,
    colors = ButtonDefaults.outlinedButtonColors(
      containerColor = containerColor,
      contentColor = effectiveContent,
      disabledContainerColor = Color.Transparent,
      disabledContentColor = colors.muted
    ),
    contentPadding = contentPadding,
    modifier = modifier.heightIn(min = minHeight)
  ) {
    if (content != null) {
      content()
    } else {
      if (icon != null) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          modifier = Modifier.size(18.dp),
          tint = effectiveContent
        )
        if (!text.isNullOrEmpty()) {
          Spacer(modifier = Modifier.width(8.dp))
        }
      }
      if (!text.isNullOrEmpty()) {
        Text(
          text = text,
          color = effectiveContent,
          fontSize = 13.5.sp,
          fontWeight = FontWeight.SemiBold,
          textAlign = TextAlign.Center,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
      }
    }
  }
}

@Composable
fun AppTextButton(
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  text: String? = null,
  icon: ImageVector? = null,
  contentColor: Color? = null,
  minHeight: Dp = 48.dp,
  contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
  content: (@Composable RowScope.() -> Unit)? = null
) {
  val colors = LocalMoneyMapColors.current
  val effectiveContent = contentColor ?: colors.head

  TextButton(
    onClick = onClick,
    enabled = enabled,
    shape = AppShapes.Button,
    colors = ButtonDefaults.textButtonColors(
      contentColor = effectiveContent,
      disabledContentColor = colors.muted
    ),
    contentPadding = contentPadding,
    modifier = modifier.heightIn(min = minHeight)
  ) {
    if (content != null) {
      content()
    } else {
      if (icon != null) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          modifier = Modifier.size(18.dp)
        )
        if (!text.isNullOrEmpty()) {
          Spacer(modifier = Modifier.width(8.dp))
        }
      }
      if (!text.isNullOrEmpty()) {
        Text(
          text = text,
          fontSize = 13.5.sp,
          fontWeight = FontWeight.SemiBold,
          textAlign = TextAlign.Center,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
      }
    }
  }
}

@Composable
fun AppIconButton(
  onClick: () -> Unit,
  icon: ImageVector,
  contentDescription: String?,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  tint: Color? = null,
  containerColor: Color = Color.Transparent,
  shape: androidx.compose.ui.graphics.Shape = AppShapes.Button,
  iconSize: Dp = 18.dp
) {
  val colors = LocalMoneyMapColors.current
  val effectiveTint = tint ?: colors.ink

  Box(
    modifier = modifier
      .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
      .clip(shape)
      .background(containerColor)
      .clickable(
        enabled = enabled,
        role = Role.Button,
        onClick = onClick
      ),
    contentAlignment = Alignment.Center
  ) {
    Icon(
      imageVector = icon,
      contentDescription = contentDescription,
      tint = if (enabled) effectiveTint else colors.muted,
      modifier = Modifier.size(iconSize)
    )
  }
}
