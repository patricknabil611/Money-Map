package com.example.ui.components

import android.content.Intent
import com.example.BuildConfig
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.reminder.UpdateReminderManager
import com.example.ui.theme.AppShapes
import com.example.ui.theme.LocalMoneyMapColors
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NotificationSetupSheet(
  onDismiss: () -> Unit,
  onTriggerEdgeLightingTest: () -> Unit
) {
  val context = LocalContext.current
  val colors = LocalMoneyMapColors.current
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

  var updateReminderEnabled by remember {
    mutableStateOf(UpdateReminderManager.isUpdateReminderEnabled(context))
  }
  var selectedDay by remember {
    mutableStateOf(UpdateReminderManager.getReminderDayOfWeek(context))
  }
  var selectedHour by remember {
    mutableStateOf(UpdateReminderManager.getReminderHour(context))
  }
  var selectedMinute by remember {
    mutableStateOf(UpdateReminderManager.getReminderMinute(context))
  }
  var edgeLightingEnabled by remember {
    mutableStateOf(UpdateReminderManager.isEdgeLightingEnabled(context))
  }
  var feedbackMsg by remember { mutableStateOf<String?>(null) }

  val neonOrange = Color(0xFFFF6D00)

  val daysList = listOf(
    Calendar.MONDAY to "Monday",
    Calendar.TUESDAY to "Tuesday",
    Calendar.WEDNESDAY to "Wednesday",
    Calendar.THURSDAY to "Thursday",
    Calendar.FRIDAY to "Friday",
    Calendar.SATURDAY to "Saturday",
    Calendar.SUNDAY to "Sunday"
  )

  val timePresets = listOf(
    Triple(9, 0, "9:00 AM"),
    Triple(13, 0, "1:00 PM (Default)"),
    Triple(15, 0, "3:00 PM"),
    Triple(18, 0, "6:00 PM"),
    Triple(20, 0, "8:00 PM")
  )

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = colors.surface,
    shape = AppShapes.Sheet
  ) {
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .navigationBarsPadding(),
      contentAlignment = Alignment.TopCenter
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .widthIn(max = 680.dp)
          .padding(horizontal = 16.dp)
          .padding(bottom = 24.dp)
          .verticalScroll(rememberScrollState())
      ) {
        // Header
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Box(
              modifier = Modifier
                .size(42.dp)
                .clip(AppShapes.Medium)
                .background(neonOrange.copy(alpha = 0.15f)),
              contentAlignment = Alignment.Center
            ) {
              Icon(
                imageVector = Icons.Default.NotificationsActive,
                contentDescription = null,
                tint = neonOrange,
                modifier = Modifier.size(24.dp)
              )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                  text = "Notification Setup",
                  fontSize = 20.sp,
                  fontWeight = FontWeight.Bold,
                  color = colors.ink
                )
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                  modifier = Modifier
                    .clip(AppShapes.Small)
                    .background(neonOrange)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                  Text(
                    text = "GALAXY S25",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.Black
                  )
                }
              }
              Text(
                text = "Customizable weekly update alerts & Samsung Edge Lighting",
                fontSize = 11.5.sp,
                color = colors.muted
              )
            }
          }

          AppIconButton(
            onClick = onDismiss,
            icon = Icons.Default.Close,
            contentDescription = "Close",
            tint = colors.ink,
            containerColor = colors.surface2,
            modifier = Modifier.testTag("close_notification_setup_btn")
          )
        }

        feedbackMsg?.let { msg ->
          Spacer(modifier = Modifier.height(6.dp))
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .clip(AppShapes.Medium)
              .background(colors.incomeSoft)
              .padding(horizontal = 12.dp, vertical = 8.dp)
          ) {
            Text(text = msg, color = colors.income, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
          }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Card 1: Weekly App Update Reminder
        Card(
          shape = AppShapes.Card,
          colors = CardDefaults.cardColors(containerColor = colors.surface2),
          border = CardDefaults.outlinedCardBorder(),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("update_reminder_config_card")
        ) {
          Column(modifier = Modifier.padding(16.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = "Weekly App Update Reminder",
                  fontWeight = FontWeight.Bold,
                  fontSize = 15.sp,
                  color = colors.ink
                )
                Text(
                  text = "Remind me to update P Money every week",
                  fontSize = 11.5.sp,
                  color = colors.muted
                )
              }
              Switch(
                checked = updateReminderEnabled,
                onCheckedChange = {
                  updateReminderEnabled = it
                  UpdateReminderManager.setUpdateReminderEnabled(context, it)
                  feedbackMsg = if (it) "Weekly update reminder enabled!" else "Weekly update reminder turned off."
                },
                colors = SwitchDefaults.colors(
                  checkedThumbColor = Color.White,
                  checkedTrackColor = neonOrange
                ),
                modifier = Modifier.testTag("update_reminder_switch")
              )
            }

            if (updateReminderEnabled) {
              Spacer(modifier = Modifier.height(14.dp))
              Divider(color = colors.line)
              Spacer(modifier = Modifier.height(12.dp))

              // Day of the Week Picker
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = neonOrange, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Select Reminder Day:", fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp, color = colors.ink)
              }

              Spacer(modifier = Modifier.height(8.dp))

              FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
              ) {
                daysList.forEach { (calDay, dayName) ->
                  val isSelected = selectedDay == calDay
                  Box(
                    modifier = Modifier
                      .clip(AppShapes.Small)
                      .background(if (isSelected) neonOrange else colors.surface)
                      .border(1.dp, if (isSelected) neonOrange else colors.line, AppShapes.Small)
                      .clickable {
                        selectedDay = calDay
                        UpdateReminderManager.setReminderDayOfWeek(context, calDay)
                        feedbackMsg = "Scheduled for every $dayName at ${selectedHour % 12.let { if (it == 0) 12 else it }}:${if (selectedMinute < 10) "0$selectedMinute" else "$selectedMinute"} ${if (selectedHour >= 12) "PM" else "AM"}"
                      }
                      .padding(horizontal = 10.dp, vertical = 6.dp)
                  ) {
                    Text(
                      text = if (calDay == Calendar.THURSDAY) "$dayName ★" else dayName,
                      color = if (isSelected) Color.Black else colors.ink,
                      fontSize = 11.5.sp,
                      fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    )
                  }
                }
              }

              Spacer(modifier = Modifier.height(14.dp))

              // Time of Day Picker
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AccessTime, contentDescription = null, tint = neonOrange, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Select Reminder Time:", fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp, color = colors.ink)
              }

              Spacer(modifier = Modifier.height(8.dp))

              FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
              ) {
                timePresets.forEach { (hour, min, label) ->
                  val isSelected = selectedHour == hour && selectedMinute == min
                  Box(
                    modifier = Modifier
                      .clip(AppShapes.Small)
                      .background(if (isSelected) neonOrange else colors.surface)
                      .border(1.dp, if (isSelected) neonOrange else colors.line, AppShapes.Small)
                      .clickable {
                        selectedHour = hour
                        selectedMinute = min
                        UpdateReminderManager.setReminderTime(context, hour, min)
                        feedbackMsg = "Reminder time updated to $label!"
                      }
                      .padding(horizontal = 10.dp, vertical = 6.dp)
                  ) {
                    Text(
                      text = label,
                      color = if (isSelected) Color.Black else colors.ink,
                      fontSize = 11.sp,
                      fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    )
                  }
                }
              }

              Spacer(modifier = Modifier.height(12.dp))

              // Next Trigger info badge
              Box(
                modifier = Modifier
                  .fillMaxWidth()
                  .clip(AppShapes.Medium)
                  .background(neonOrange.copy(alpha = 0.12f))
                  .border(1.dp, neonOrange.copy(alpha = 0.3f), AppShapes.Medium)
                  .padding(10.dp)
              ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Icon(Icons.Default.Check, contentDescription = null, tint = neonOrange, modifier = Modifier.size(16.dp))
                  Spacer(modifier = Modifier.width(8.dp))
                  Column {
                    Text(
                      text = "Next Scheduled Reminder:",
                      color = neonOrange,
                      fontSize = 11.sp,
                      fontWeight = FontWeight.Bold
                    )
                    Text(
                      text = UpdateReminderManager.getNextScheduledFormatted(context),
                      color = colors.ink,
                      fontSize = 12.5.sp,
                      fontWeight = FontWeight.SemiBold
                    )
                  }
                }
              }
            }
          }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Card 2: Samsung Galaxy S25 Ultra Edge Lighting Animation
        Card(
          shape = AppShapes.Card,
          colors = CardDefaults.cardColors(containerColor = colors.surface2),
          border = CardDefaults.outlinedCardBorder(),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("edge_lighting_card")
        ) {
          Column(modifier = Modifier.padding(16.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Box(
                  modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(neonOrange.copy(alpha = 0.2f)),
                  contentAlignment = Alignment.Center
                ) {
                  Icon(Icons.Default.PhoneAndroid, contentDescription = null, tint = neonOrange, modifier = Modifier.size(20.dp))
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                  Text(
                    text = "Orange Edge Lighting Animation",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = colors.ink
                  )
                  Text(
                    text = "Samsung Galaxy S25 Ultra style perimeter neon glow",
                    fontSize = 11.sp,
                    color = colors.muted
                  )
                }
              }
              Switch(
                checked = edgeLightingEnabled,
                onCheckedChange = {
                  edgeLightingEnabled = it
                  UpdateReminderManager.setEdgeLightingEnabled(context, it)
                },
                colors = SwitchDefaults.colors(
                  checkedThumbColor = Color.White,
                  checkedTrackColor = neonOrange
                )
              )
            }

            Spacer(modifier = Modifier.height(14.dp))

            AppSecondaryButton(
              text = "Preview Orange Edge Lighting & Notification",
              icon = Icons.Default.PlayArrow,
              contentColor = colors.ink,
              borderColor = colors.line,
              onClick = {
                onDismiss()
                onTriggerEdgeLightingTest()
                UpdateReminderManager.triggerTestNotification(context)
              },
              modifier = Modifier
                .fillMaxWidth()
                .testTag("test_edge_lighting_btn")
            )
          }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Card 3: Download & Update APK Action
        Card(
          shape = AppShapes.Card,
          colors = CardDefaults.cardColors(containerColor = colors.surface2),
          border = CardDefaults.outlinedCardBorder(),
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(modifier = Modifier.padding(16.dp)) {
            Text(
              text = "Direct APK Update (No USB required)",
              fontWeight = FontWeight.Bold,
              fontSize = 14.sp,
              color = colors.ink
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
              text = "Download the latest version directly to your Samsung phone:",
              fontSize = 11.5.sp,
              color = colors.muted
            )

            Spacer(modifier = Modifier.height(12.dp))

            AppPrimaryButton(
              text = "Download Latest P Money APK (v${BuildConfig.VERSION_NAME})",
              icon = Icons.Default.Download,
              containerColor = neonOrange,
              contentColor = Color.Black,
              onClick = {
                try {
                  val intent = Intent(Intent.ACTION_VIEW, Uri.parse("/P_Money.apk"))
                  intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                  context.startActivity(intent)
                } catch (_: Exception) {}
              },
              modifier = Modifier
                .fillMaxWidth()
                .testTag("download_latest_apk_setup_btn")
            )
          }
        }
      }
    }
  }
}
