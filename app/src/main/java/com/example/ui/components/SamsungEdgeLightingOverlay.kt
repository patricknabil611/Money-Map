package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun SamsungEdgeLightingOverlay(
  isActive: Boolean,
  title: String = "P Money • Update Reminder",
  message: String = "Tap to open Notification & Updates Setup",
  onDismiss: () -> Unit,
  onClick: () -> Unit
) {
  if (!isActive) return

  // Auto-dismiss after 4.2 seconds
  LaunchedEffect(isActive) {
    delay(4200)
    onDismiss()
  }

  val infiniteTransition = rememberInfiniteTransition(label = "edgeLighting")

  // Pulse opacity for glow
  val glowAlpha by infiniteTransition.animateFloat(
    initialValue = 0.5f,
    targetValue = 0.95f,
    animationSpec = infiniteRepeatable(
      animation = tween(650, easing = FastOutSlowInEasing),
      repeatMode = RepeatMode.Reverse
    ),
    label = "glowAlpha"
  )

  // Rotating angle for traveling laser comet around perimeter
  val sweepAngle by infiniteTransition.animateFloat(
    initialValue = 0f,
    targetValue = 360f,
    animationSpec = infiniteRepeatable(
      animation = tween(2200, easing = LinearEasing),
      repeatMode = RepeatMode.Restart
    ),
    label = "sweepAngle"
  )

  // Samsung Galaxy S25 Ultra Signature Neon Orange Palette
  val neonOrangePrimary = Color(0xFFFF6D00)
  val neonOrangeBright = Color(0xFFFF9100)
  val neonOrangeDeep = Color(0xFFFF3D00)
  val neonOrangeAmber = Color(0xFFFFAB00)

  Box(
    modifier = Modifier
      .fillMaxSize()
      .drawBehind {
        val cornerRadiusPx = 36.dp.toPx()
        val strokeWidthPx = 5.dp.toPx()

        // 1. Ambient Inner Halo Glow
        val haloBrush = Brush.radialGradient(
          colors = listOf(
            neonOrangePrimary.copy(alpha = glowAlpha * 0.35f),
            neonOrangeDeep.copy(alpha = glowAlpha * 0.15f),
            Color.Transparent
          ),
          center = Offset(size.width / 2f, size.height / 2f),
          radius = maxOf(size.width, size.height) * 0.75f
        )
        drawRoundRect(
          brush = haloBrush,
          size = size,
          cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx)
        )

        // 2. Continuous Samsung S25 Ultra Edge Lighting Glowing Border
        val borderColors = listOf(
          neonOrangeDeep,
          neonOrangeBright,
          neonOrangeAmber,
          neonOrangeBright,
          neonOrangeDeep
        )

        val sweepBrush = Brush.sweepGradient(
          colors = borderColors,
          center = Offset(size.width / 2f, size.height / 2f)
        )

        // Ambient border pass (wider, softer)
        drawRoundRect(
          brush = sweepBrush,
          topLeft = Offset(strokeWidthPx / 2, strokeWidthPx / 2),
          size = Size(size.width - strokeWidthPx, size.height - strokeWidthPx),
          cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx),
          style = Stroke(width = strokeWidthPx * 1.8f, cap = StrokeCap.Round),
          alpha = glowAlpha * 0.6f
        )

        // Sharp core laser border pass
        drawRoundRect(
          brush = sweepBrush,
          topLeft = Offset(strokeWidthPx / 2, strokeWidthPx / 2),
          size = Size(size.width - strokeWidthPx, size.height - strokeWidthPx),
          cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx),
          style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round),
          alpha = glowAlpha
        )
      }
      .clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        onClick = onClick
      )
  ) {
    // Samsung One UI "Brief Notification" Capsule
    AnimatedVisibility(
      visible = isActive,
      enter = slideInVertically(
        initialOffsetY = { -it },
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 400f)
      ) + fadeIn(),
      exit = slideOutVertically(
        targetOffsetY = { -it },
        animationSpec = tween(300)
      ) + fadeOut(),
      modifier = Modifier
        .align(Alignment.TopCenter)
        .statusBarsPadding()
        .padding(top = 10.dp, start = 16.dp, end = 16.dp)
    ) {
      Box(
        modifier = Modifier
          .widthIn(max = 520.dp)
          .fillMaxWidth()
          .clip(RoundedCornerShape(22.dp))
          .background(Color(0xE61A1A1A))
          .border(1.5.dp, neonOrangePrimary, RoundedCornerShape(22.dp))
          .clickable { onClick() }
          .padding(horizontal = 16.dp, vertical = 12.dp)
          .testTag("samsung_edge_lighting_brief_pill")
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Box(
              modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(neonOrangePrimary.copy(alpha = 0.2f)),
              contentAlignment = Alignment.Center
            ) {
              Icon(
                imageVector = Icons.Default.NotificationsActive,
                contentDescription = null,
                tint = neonOrangePrimary,
                modifier = Modifier.size(20.dp)
              )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                  text = title,
                  color = Color.White,
                  fontSize = 14.sp,
                  fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                  modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(neonOrangePrimary)
                    .padding(horizontal = 5.dp, vertical = 1.dp)
                ) {
                  Text(
                    text = "GALAXY S25",
                    color = Color.Black,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Black
                  )
                }
              }
              Text(
                text = message,
                color = Color(0xFFD1D5DB),
                fontSize = 12.sp
              )
            }
          }

          Box(
            modifier = Modifier
              .clip(RoundedCornerShape(10.dp))
              .background(neonOrangePrimary)
              .padding(horizontal = 10.dp, vertical = 6.dp)
          ) {
            Text(
              text = "Open",
              color = Color.Black,
              fontSize = 11.5.sp,
              fontWeight = FontWeight.Bold
            )
          }
        }
      }
    }
  }
}
