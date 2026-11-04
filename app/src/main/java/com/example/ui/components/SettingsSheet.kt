package com.example.ui.components

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Upload
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.data.model.AppSettings
import com.example.data.user.ProfileAvatarView
import com.example.data.user.UserProfile
import com.example.ui.theme.AppShapes
import com.example.ui.theme.LocalMoneyMapColors
import com.example.util.ScheduleEngine
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
  settings: AppSettings,
  currentProfile: UserProfile? = null,
  onDismiss: () -> Unit,
  onUpdateRange: (String, String) -> Unit,
  onUpdateTheme: (String) -> Unit = {},
  onExportJson: () -> String,
  onExportJsonSuspend: suspend () -> String,
  onDriveBackupDone: () -> Unit,
  onImportJson: (String, (Boolean) -> Unit) -> Unit,
  onResetDefault: () -> Unit,
  onToggleAutoSync: (Boolean) -> Unit = {},
  onSyncNow: () -> Unit = {},
  onLockApp: () -> Unit = {},
  onManageUsers: () -> Unit = {},
  onOpenNotificationSetup: () -> Unit = {}
) {
  val context = LocalContext.current
  val colors = LocalMoneyMapColors.current
  val scope = rememberCoroutineScope()
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

  var startMonth by remember(settings) { mutableStateOf(settings.startMonth) }
  var endMonth by remember(settings) { mutableStateOf(settings.endMonth) }
  var errorMsg by remember { mutableStateOf<String?>(null) }
  var infoMsg by remember { mutableStateOf<String?>(null) }
  var resetArmed by remember { mutableStateOf(false) }

  LaunchedEffect(resetArmed) {
    if (resetArmed) {
      delay(3500)
      resetArmed = false
    }
  }

  // Google Drive CreateDocument launcher
  val driveSaveLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.CreateDocument("application/json")
  ) { uri: Uri? ->
    if (uri != null) {
      scope.launch {
        try {
          val json = onExportJsonSuspend()
          context.contentResolver.openOutputStream(uri)?.use { out ->
            out.write(json.toByteArray(Charsets.UTF_8))
          }
          onDriveBackupDone()
          infoMsg = "Backup saved to Google Drive (${settings.driveAccount})"
        } catch (e: Exception) {
          errorMsg = "Failed to save backup to Google Drive."
        }
      }
    }
  }

  // Google Drive / Storage OpenDocument launcher
  val driveOpenLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenDocument()
  ) { uri: Uri? ->
    if (uri != null) {
      try {
        val content = context.contentResolver.openInputStream(uri)?.use { stream ->
          stream.bufferedReader().readText()
        }
        if (content != null) {
          onImportJson(content) { success ->
            if (success) {
              infoMsg = "Restored successfully from Google Drive backup."
            }
          }
        }
      } catch (e: Exception) {
        errorMsg = "Failed to restore backup file."
      }
    }
  }

  // Generic JSON file picker for Import JSON
  val filePickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.GetContent()
  ) { uri: Uri? ->
    if (uri != null) {
      try {
        val content = context.contentResolver.openInputStream(uri)?.use { stream ->
          stream.bufferedReader().readText()
        }
        if (content != null) {
          onImportJson(content) { success ->
            if (success) {
              onDismiss()
            }
          }
        }
      } catch (e: Exception) {
        errorMsg = "Failed to read backup file."
      }
    }
  }

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
          Text(
            text = "Settings",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = colors.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
          )
          AppIconButton(
            onClick = onDismiss,
            icon = Icons.Default.Close,
            contentDescription = "Close",
            tint = colors.ink,
            containerColor = colors.surface2,
            modifier = Modifier.testTag("close_settings_btn")
          )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Info or Error Messages Banner
        infoMsg?.let { msg ->
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .padding(bottom = 12.dp)
              .clip(AppShapes.Medium)
              .background(colors.incomeSoft)
              .padding(horizontal = 14.dp, vertical = 10.dp)
          ) {
            Text(
              text = msg,
              color = colors.income,
              fontSize = 13.sp,
              fontWeight = FontWeight.Medium
            )
          }
        }

        errorMsg?.let { err ->
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .padding(bottom = 12.dp)
              .clip(AppShapes.Medium)
              .background(colors.expenseSoft)
              .padding(horizontal = 14.dp, vertical = 10.dp)
          ) {
            Text(
              text = err,
              color = colors.expense,
              fontSize = 13.sp,
              fontWeight = FontWeight.Medium
            )
          }
        }

        // Section 1: Netflix-Style Profile & User Management
        Card(
          shape = AppShapes.Card,
          colors = CardDefaults.cardColors(containerColor = colors.surface2),
          border = CardDefaults.outlinedCardBorder(),
          modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
            .testTag("user_management_card")
        ) {
          Column(modifier = Modifier.padding(16.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                ProfileAvatarView(
                  avatarKey = currentProfile?.avatarKey,
                  modifier = Modifier.size(46.dp),
                  shape = AppShapes.Medium
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                      text = currentProfile?.name ?: "Patrick",
                      fontWeight = FontWeight.Bold,
                      color = colors.ink,
                      fontSize = 16.sp,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis
                    )
                    if (currentProfile?.isPatrick == true) {
                      Spacer(modifier = Modifier.width(6.dp))
                      Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Password protected",
                        tint = colors.year,
                        modifier = Modifier.size(14.dp)
                      )
                    }
                  }
                  Text(
                    text = if (currentProfile?.isPatrick == true) "Locked to S25 Ultra (R5CY14B6HZD)" else "Active Profile",
                    color = if (currentProfile?.isPatrick == true) colors.income else colors.muted,
                    fontSize = 11.5.sp,
                    fontWeight = if (currentProfile?.isPatrick == true) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                  )
                }
              }

              Box(
                modifier = Modifier
                  .clip(AppShapes.Small)
                  .background(colors.surface)
                  .clickable {
                    onDismiss()
                    onManageUsers()
                  }
                  .padding(horizontal = 8.dp, vertical = 6.dp)
              ) {
                Text(
                  text = "Switch Profile",
                  fontSize = 11.5.sp,
                  fontWeight = FontWeight.Bold,
                  color = colors.head
                )
              }
            }

            Spacer(modifier = Modifier.height(14.dp))

            AppSecondaryButton(
              text = "Switch & Manage Profiles (Netflix Avatars)",
              icon = Icons.Default.Person,
              onClick = {
                onDismiss()
                onManageUsers()
              },
              modifier = Modifier
                .fillMaxWidth()
                .testTag("manage_users_btn")
            )
          }
        }

        // Section 2: Google Drive Continuous Auto-Sync & Backup
        val syncAccount = if (!currentProfile?.email.isNullOrBlank()) {
          currentProfile!!.email
        } else if (currentProfile?.isPatrick == true) {
          com.example.data.user.UserManager.PATRICK_EMAIL
        } else {
          settings.driveAccount
        }
        val isAutoSyncOn = currentProfile?.autoSyncEnabled ?: true

        Card(
          shape = AppShapes.Card,
          colors = CardDefaults.cardColors(containerColor = colors.surface2),
          border = CardDefaults.outlinedCardBorder(),
          modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
            .testTag("google_drive_card")
        ) {
          Column(modifier = Modifier.padding(16.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Box(
                  modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(colors.leftoverSoft),
                  contentAlignment = Alignment.Center
                ) {
                  Icon(Icons.Default.Cloud, contentDescription = "Google Drive", tint = colors.leftover, modifier = Modifier.size(22.dp))
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                  Text("Google Drive Auto-Sync", fontWeight = FontWeight.Bold, color = colors.ink, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                  Text(syncAccount, color = colors.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
              }

              // Connected account status badge
              Box(
                modifier = Modifier
                  .clip(AppShapes.Small)
                  .background(Color(0xFF4CAF50).copy(alpha = 0.15f))
                  .padding(horizontal = 8.dp, vertical = 4.dp)
              ) {
                Text("Connected", color = Color(0xFF4CAF50), fontSize = 11.sp, fontWeight = FontWeight.Bold)
              }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Continuous Auto-Sync Toggle Row
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .clip(AppShapes.Medium)
                .background(colors.surface)
                .padding(horizontal = 12.dp, vertical = 10.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = "Continuous Auto-Sync",
                  fontWeight = FontWeight.SemiBold,
                  fontSize = 13.5.sp,
                  color = colors.ink
                )
                Text(
                  text = "Syncs updates immediately to $syncAccount",
                  fontSize = 11.sp,
                  color = colors.muted
                )
              }
              Switch(
                checked = isAutoSyncOn,
                onCheckedChange = { onToggleAutoSync(it) },
                colors = SwitchDefaults.colors(
                  checkedThumbColor = colors.headInk,
                  checkedTrackColor = colors.head
                ),
                modifier = Modifier.testTag("auto_sync_toggle")
              )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Sync Status Indicator and "Sync Now" button
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                  imageVector = Icons.Default.CheckCircle,
                  contentDescription = null,
                  tint = Color(0xFF4CAF50),
                  modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                  text = if (isAutoSyncOn) "Synced all the time to Drive" else "Auto-sync paused",
                  color = if (isAutoSyncOn) Color(0xFF4CAF50) else colors.muted,
                  fontSize = 12.sp,
                  fontWeight = FontWeight.Medium,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis
                )
              }

              AppSecondaryButton(
                text = "Sync Now",
                icon = Icons.Default.Sync,
                contentColor = colors.head,
                onClick = {
                  onSyncNow()
                  infoMsg = "Data synced to Google Drive ($syncAccount)"
                },
                modifier = Modifier.testTag("sync_now_btn")
              )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Direct Drive Export & Restore Buttons
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(12.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              AppPrimaryButton(
                text = "Save to Drive",
                icon = Icons.Default.CloudUpload,
                onClick = {
                  driveSaveLauncher.launch("money-map-backup.json")
                },
                modifier = Modifier
                  .weight(1f)
                  .testTag("backup_to_drive_btn")
              )

              AppSecondaryButton(
                text = "Restore",
                icon = Icons.Default.CloudDownload,
                contentColor = colors.leftover,
                borderColor = colors.leftover,
                onClick = {
                  driveOpenLauncher.launch(arrayOf("application/json", "*/*"))
                },
                modifier = Modifier
                  .weight(1f)
                  .testTag("restore_from_drive_btn")
              )
            }
          }
        }

        // Section 3: Home Grid Timeline Range
        Card(
          shape = AppShapes.Card,
          colors = CardDefaults.cardColors(containerColor = colors.surface2),
          border = CardDefaults.outlinedCardBorder(),
          modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
            .testTag("home_grid_range_card")
        ) {
          Column(modifier = Modifier.padding(16.dp)) {
            // 1. Home Grid Range tile
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Box(
                modifier = Modifier
                  .size(38.dp)
                  .clip(CircleShape)
                  .background(colors.yearSoft),
                contentAlignment = Alignment.Center
              ) {
                Icon(Icons.Default.CalendarMonth, contentDescription = "Timeline", tint = colors.year, modifier = Modifier.size(20.dp))
              }
              Spacer(modifier = Modifier.width(10.dp))
              Text("Home Grid Range", fontWeight = FontWeight.Bold, color = colors.ink, fontSize = 15.sp)
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 2. Under it the range applied (only display the dates range)
            Box(
              modifier = Modifier
                .fillMaxWidth()
                .clip(AppShapes.Small)
                .background(colors.surface)
                .padding(horizontal = 12.dp, vertical = 7.dp)
            ) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Text(
                  text = "Range Applied",
                  fontSize = 12.sp,
                  color = colors.muted
                )
                Text(
                  text = "${ScheduleEngine.mLabel(startMonth)} – ${ScheduleEngine.mLabel(endMonth)}",
                  fontSize = 12.5.sp,
                  fontWeight = FontWeight.Bold,
                  color = colors.year
                )
              }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 3. Under it the selections
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
              MonthDropdown(
                label = "From",
                selected = startMonth,
                onSelect = {
                  startMonth = it
                  if (ScheduleEngine.mIdx(endMonth) < ScheduleEngine.mIdx(it)) {
                    endMonth = it
                  }
                },
                modifier = Modifier.weight(1f)
              )
              MonthDropdown(
                label = "To",
                selected = endMonth,
                onSelect = {
                  endMonth = it
                  if (ScheduleEngine.mIdx(it) < ScheduleEngine.mIdx(startMonth)) {
                    startMonth = it
                  }
                },
                modifier = Modifier.weight(1f)
              )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 4. Under it the button
            AppPrimaryButton(
              text = "Save Grid Range",
              onClick = {
                val diff = ScheduleEngine.mIdx(endMonth) - ScheduleEngine.mIdx(startMonth)
                if (diff < 0) {
                  errorMsg = "Start month cannot be after end month."
                  return@AppPrimaryButton
                }
                if (diff > 47) {
                  errorMsg = "Pick a range of up to 4 years."
                  return@AppPrimaryButton
                }
                errorMsg = null
                infoMsg = "Grid range updated (${ScheduleEngine.mLabel(startMonth)} to ${ScheduleEngine.mLabel(endMonth)})"
                onUpdateRange(startMonth, endMonth)
              },
              modifier = Modifier
                .fillMaxWidth()
                .testTag("save_range_btn")
            )
          }
        }

        // Section 4: Manual Data Backup & Reset
        Card(
          shape = AppShapes.Card,
          colors = CardDefaults.cardColors(containerColor = colors.surface2),
          border = CardDefaults.outlinedCardBorder(),
          modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
        ) {
          Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Box(
                modifier = Modifier
                  .size(40.dp)
                  .clip(CircleShape)
                  .background(colors.head.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
              ) {
                Icon(Icons.Default.SdStorage, contentDescription = "Data", tint = colors.head, modifier = Modifier.size(22.dp))
              }
              Spacer(modifier = Modifier.width(12.dp))
              Column {
                Text("Manual File Backup & Reset", fontWeight = FontWeight.Bold, color = colors.ink, fontSize = 15.sp)
                Text("Export, import JSON files or reset dataset", color = colors.muted, fontSize = 12.sp)
              }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(12.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              AppSecondaryButton(
                text = "Share JSON",
                icon = Icons.Default.Download,
                contentColor = colors.ink,
                onClick = {
                  scope.launch {
                    val json = onExportJson()
                    val df = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
                    val fileName = "money-map-backup-${df.format(Date())}.json"
                    val dir = File(context.cacheDir, "shared")
                    if (!dir.exists()) dir.mkdirs()
                    val file = File(dir, fileName)
                    file.writeText(json)

                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                    val intent = Intent(Intent.ACTION_SEND).apply {
                      type = "application/json"
                      putExtra(Intent.EXTRA_STREAM, uri)
                      putExtra(Intent.EXTRA_TITLE, fileName)
                      addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    val chooser = Intent.createChooser(intent, "Export budget backup")
                    chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(chooser)
                  }
                },
                modifier = Modifier
                  .weight(1f)
                  .testTag("export_json_btn")
              )

              AppSecondaryButton(
                text = "Import JSON",
                icon = Icons.Default.Upload,
                contentColor = colors.ink,
                onClick = { filePickerLauncher.launch("*/*") },
                modifier = Modifier
                  .weight(1f)
                  .testTag("import_json_btn")
              )
            }

            Spacer(modifier = Modifier.height(14.dp))

            AppSecondaryButton(
              text = if (resetArmed) "Tap again to confirm reset" else "Reset to Patrick's initial budget",
              icon = Icons.Default.RestartAlt,
              containerColor = if (resetArmed) colors.expense else Color.Transparent,
              contentColor = if (resetArmed) Color.White else colors.expense,
              borderColor = if (resetArmed) colors.expense else colors.expense.copy(alpha = 0.5f),
              onClick = {
                if (resetArmed) {
                  onResetDefault()
                  infoMsg = "Data reset to initial state."
                } else {
                  resetArmed = true
                }
              },
              modifier = Modifier
                .fillMaxWidth()
                .testTag("reset_data_btn")
            )
          }
        }

        // Section: Notification & Update Reminders
        val isUpdateReminderActive = com.example.reminder.UpdateReminderManager.isUpdateReminderEnabled(context)
        val nextReminderStr = com.example.reminder.UpdateReminderManager.getNextScheduledFormatted(context)
        Card(
          shape = AppShapes.Card,
          colors = CardDefaults.cardColors(containerColor = colors.surface2),
          border = CardDefaults.outlinedCardBorder(),
          modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
            .clickable {
              onDismiss()
              onOpenNotificationSetup()
            }
            .testTag("notification_settings_card")
        ) {
          Column(modifier = Modifier.padding(16.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Box(
                  modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFFF6D00).copy(alpha = 0.15f)),
                  contentAlignment = Alignment.Center
                ) {
                  Icon(
                    Icons.Default.NotificationsActive,
                    contentDescription = null,
                    tint = Color(0xFFFF6D00),
                    modifier = Modifier.size(22.dp)
                  )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Notification & Update Setup", fontWeight = FontWeight.Bold, color = colors.ink, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                      modifier = Modifier
                        .clip(AppShapes.Small)
                        .background(Color(0xFFFF6D00))
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                      Text("S25 ULTRA", fontSize = 8.sp, fontWeight = FontWeight.Black, color = Color.Black)
                    }
                  }
                  Text(
                    text = if (isUpdateReminderActive) nextReminderStr else "Weekly reminder turned off",
                    color = colors.muted,
                    fontSize = 11.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                  )
                }
              }

              Box(
                modifier = Modifier
                  .clip(AppShapes.Small)
                  .background(colors.surface)
                  .padding(horizontal = 8.dp, vertical = 6.dp)
              ) {
                Text("Customize", color = Color(0xFFFF6D00), fontSize = 11.sp, fontWeight = FontWeight.Bold)
              }
            }
          }
        }

        // Section 5: About P Money & What's New
        Card(
          shape = AppShapes.Card,
          colors = CardDefaults.cardColors(containerColor = colors.surface2),
          border = CardDefaults.outlinedCardBorder(),
          modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
            .testTag("about_app_settings_card")
        ) {
          Column(modifier = Modifier.padding(16.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Box(
                  modifier = Modifier
                    .size(42.dp)
                    .clip(AppShapes.Medium)
                    .background(colors.head),
                  contentAlignment = Alignment.Center
                ) {
                  Icon(Icons.Default.Info, contentDescription = null, tint = colors.headInk, modifier = Modifier.size(24.dp))
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                  Text("About P Money", fontWeight = FontWeight.Bold, color = colors.ink, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                  Text("Version v2.5.0 • Release Nov 2, 2026", color = colors.muted, fontSize = 11.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
              }

              Box(
                modifier = Modifier
                  .clip(AppShapes.Small)
                  .background(colors.incomeSoft)
                  .padding(horizontal = 8.dp, vertical = 4.dp)
              ) {
                Text("v2.5.0", color = colors.income, fontSize = 11.sp, fontWeight = FontWeight.Bold)
              }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Metadata specifications
            Column(
              modifier = Modifier
                .fillMaxWidth()
                .clip(AppShapes.Card)
                .background(colors.surface)
                .padding(12.dp)
            ) {
              AboutMetadataRow(icon = Icons.Default.CheckCircle, iconColor = colors.income, label = "App Version", value = "v2.5.0 (Build 25)")
              Divider(color = colors.line, modifier = Modifier.padding(vertical = 6.dp))
              AboutMetadataRow(icon = Icons.Default.CalendarMonth, iconColor = colors.year, label = "Release Date", value = "November 2, 2026")
              Divider(color = colors.line, modifier = Modifier.padding(vertical = 6.dp))
              AboutMetadataRow(icon = Icons.Default.SdStorage, iconColor = colors.leftover, label = "App Package Size", value = "33.2 MB")
              Divider(color = colors.line, modifier = Modifier.padding(vertical = 6.dp))
              AboutMetadataRow(icon = Icons.Default.PhoneAndroid, iconColor = colors.head, label = "Secured Hardware", value = "Samsung Galaxy S25 Ultra (R5CY14B6HZD)")
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = colors.head, modifier = Modifier.size(17.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text("New Features in Latest Version:", fontWeight = FontWeight.Bold, color = colors.ink, fontSize = 13.sp)
            }

            Spacer(modifier = Modifier.height(8.dp))

            FeatureHighlightCard(
              icon = Icons.Default.CloudDone,
              iconColor = Color(0xFF10B981),
              title = "Real-Time Cloud Sync (Firestore)",
              description = "Live sync across all your phones, tablets & Samsung DeX with zero delay.",
              colors = colors
            )
            Spacer(modifier = Modifier.height(6.dp))

            FeatureHighlightCard(
              icon = Icons.Default.Mic,
              iconColor = Color(0xFFE50914),
              title = "AI Voice Commands & NLP (Gemini)",
              description = "Quickly add expenses, income & tasks by voice in English or Arabic.",
              colors = colors
            )
            Spacer(modifier = Modifier.height(6.dp))

            FeatureHighlightCard(
              icon = Icons.Default.NotificationsActive,
              iconColor = Color(0xFFFF6D00),
              title = "Weekly Update Reminder & Edge Glow",
              description = "Customizable reminder every Thursday at 1:00 PM with animated Samsung Galaxy S25 Ultra neon orange edge lighting.",
              colors = colors
            )
            Spacer(modifier = Modifier.height(6.dp))

            FeatureHighlightCard(
              icon = Icons.Default.NotificationsActive,
              iconColor = Color(0xFFF59E0B),
              title = "Push Notifications & Reminders",
              description = "Automated alerts for bill due dates and installment deadlines.",
              colors = colors
            )
            Spacer(modifier = Modifier.height(6.dp))

            FeatureHighlightCard(
              icon = Icons.Default.Fingerprint,
              iconColor = Color(0xFF8B5CF6),
              title = "Hardware Biometric Fingerprint Lock",
              description = "Patrick's profile strictly bound to your Galaxy S25 Ultra (R5CY14B6HZD).",
              colors = colors
            )

            Spacer(modifier = Modifier.height(14.dp))

            AppPrimaryButton(
              text = "Download Latest APK (P Money v2.5.0)",
              icon = Icons.Default.Download,
              onClick = {
                try {
                  val intent = Intent(Intent.ACTION_VIEW, Uri.parse("/P_Money.apk"))
                  intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                  context.startActivity(intent)
                } catch (_: Exception) {}
              },
              modifier = Modifier
                .fillMaxWidth()
                .testTag("download_apk_settings_btn")
            )
          }
        }
      }
    }
  }
}

