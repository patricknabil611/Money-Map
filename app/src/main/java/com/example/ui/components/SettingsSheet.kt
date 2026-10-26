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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.fragment.app.FragmentActivity
import com.example.security.BiometricAuthManager
import com.example.ui.screens.PinEntryDialog
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.data.model.AppSettings
import com.example.ui.theme.LocalMoneyMapColors
import com.example.util.ScheduleEngine
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
  settings: AppSettings,
  onDismiss: () -> Unit,
  onUpdateRange: (String, String) -> Unit,
  onUpdateTheme: (String) -> Unit,
  onExportJson: () -> String,
  onExportJsonSuspend: suspend () -> String,
  onDriveBackupDone: () -> Unit,
  onImportJson: (String, (Boolean) -> Unit) -> Unit,
  onResetDefault: () -> Unit,
  onLockApp: () -> Unit = {}
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

  var isFingerprintAssigned by remember { mutableStateOf(BiometricAuthManager.isFingerprintAssigned(context)) }
  var isFingerprintEnabled by remember { mutableStateOf(BiometricAuthManager.isFingerprintEnabled(context)) }
  var hasPin by remember { mutableStateOf(BiometricAuthManager.hasBackupPin(context)) }
  var showPinDialog by remember { mutableStateOf(false) }

  LaunchedEffect(resetArmed) {
    if (resetArmed) {
      delay(3500)
      resetArmed = false
    }
  }

  // Google Drive CreateDocument launcher (Storage Access Framework directly to Drive)
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

  val lastBackupDateStr = remember(settings.lastDriveBackup) {
    if (settings.lastDriveBackup > 0) {
      val df = SimpleDateFormat("d MMM yyyy, HH:mm", Locale.US)
      df.format(Date(settings.lastDriveBackup))
    } else {
      "No backup yet"
    }
  }

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = colors.surface,
    shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 20.dp)
        .padding(bottom = 32.dp)
        .verticalScroll(rememberScrollState())
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = "Settings",
          fontSize = 22.sp,
          fontWeight = FontWeight.Bold,
          color = colors.ink
        )
        IconButton(onClick = onDismiss, modifier = Modifier.testTag("close_settings_btn")) {
          Icon(Icons.Default.Close, contentDescription = "Close", tint = colors.muted)
        }
      }

      Spacer(modifier = Modifier.height(14.dp))

      // Google Drive Backup Section
      Card(
        modifier = Modifier
          .fillMaxWidth()
          .padding(bottom = 18.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface2),
        border = CardDefaults.outlinedCardBorder()
      ) {
        Column(modifier = Modifier.padding(14.dp)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
              modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(colors.leftoverSoft),
              contentAlignment = Alignment.Center
            ) {
              Icon(Icons.Default.Cloud, contentDescription = "Google Drive", tint = colors.leftover, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
              Text("Google Drive Backup", fontWeight = FontWeight.Bold, color = colors.ink, fontSize = 15.sp)
              Text(settings.driveAccount, color = colors.muted, fontSize = 12.5.sp)
            }
          }

          Spacer(modifier = Modifier.height(8.dp))

          Text(
            text = "Last backed up: $lastBackupDateStr",
            color = if (settings.lastDriveBackup > 0) colors.income else colors.muted,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Medium
          )

          Spacer(modifier = Modifier.height(12.dp))

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Button(
              onClick = {
                driveSaveLauncher.launch("money-map-backup.json")
              },
              colors = ButtonDefaults.buttonColors(
                containerColor = colors.head,
                contentColor = colors.headInk
              ),
              shape = RoundedCornerShape(9.dp),
              modifier = Modifier
                .weight(1f)
                .testTag("backup_to_drive_btn")
            ) {
              Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(4.dp))
              Text("Save to Drive", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
            }

            OutlinedButton(
              onClick = {
                driveOpenLauncher.launch(arrayOf("application/json", "*/*"))
              },
              shape = RoundedCornerShape(9.dp),
              modifier = Modifier
                .weight(1f)
                .testTag("restore_from_drive_btn")
            ) {
              Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp), tint = colors.leftover)
              Spacer(modifier = Modifier.width(4.dp))
              Text("Restore", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = colors.leftover)
            }
          }
        }
      }

      // Fingerprint Security & App Lock
      Text("Fingerprint Lock", fontWeight = FontWeight.Bold, color = colors.ink, fontSize = 15.sp)
      Text(
        "Open Money Map with your fingerprint. Only 1 fingerprint is cryptographically assigned.",
        color = colors.muted,
        fontSize = 13.sp,
        modifier = Modifier.padding(bottom = 8.dp)
      )

      Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        modifier = Modifier
          .fillMaxWidth()
          .padding(bottom = 20.dp)
          .testTag("fingerprint_security_card")
      ) {
        Column(modifier = Modifier.padding(14.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
              Box(
                modifier = Modifier
                  .size(36.dp)
                  .clip(CircleShape)
                  .background(if (isFingerprintAssigned) colors.income.copy(alpha = 0.15f) else colors.head.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
              ) {
                Icon(
                  imageVector = Icons.Default.Fingerprint,
                  contentDescription = null,
                  tint = if (isFingerprintAssigned) colors.income else colors.head,
                  modifier = Modifier.size(20.dp)
                )
              }
              Spacer(modifier = Modifier.width(10.dp))
              Column {
                Text(
                  text = if (isFingerprintAssigned) "1 Fingerprint Assigned" else "No Fingerprint Assigned",
                  fontWeight = FontWeight.SemiBold,
                  fontSize = 14.sp,
                  color = colors.ink
                )
                Text(
                  text = if (isFingerprintAssigned) "KeyStore AES-256 bound" else "Tap below to assign",
                  fontSize = 11.5.sp,
                  color = colors.muted
                )
              }
            }

            if (isFingerprintAssigned) {
              Switch(
                checked = isFingerprintEnabled,
                onCheckedChange = { checked ->
                  isFingerprintEnabled = checked
                  BiometricAuthManager.setFingerprintEnabled(context, checked)
                },
                colors = SwitchDefaults.colors(
                  checkedThumbColor = colors.headInk,
                  checkedTrackColor = colors.head
                ),
                modifier = Modifier.testTag("fingerprint_lock_toggle")
              )
            }
          }

          Spacer(modifier = Modifier.height(12.dp))

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            val activity = context as? FragmentActivity
            Button(
              onClick = {
                if (activity != null) {
                  BiometricAuthManager.assignSingleFingerprint(
                    activity = activity,
                    onSuccess = {
                      isFingerprintAssigned = true
                      isFingerprintEnabled = true
                      infoMsg = "Fingerprint successfully bound as the single authorized key!"
                    },
                    onError = { err ->
                      errorMsg = err
                    }
                  )
                } else {
                  errorMsg = "Unable to access biometric scanner."
                }
              },
              colors = ButtonDefaults.buttonColors(
                containerColor = colors.head,
                contentColor = colors.headInk
              ),
              shape = RoundedCornerShape(9.dp),
              modifier = Modifier
                .weight(1f)
                .testTag("settings_assign_fingerprint_btn")
            ) {
              Icon(Icons.Default.Fingerprint, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(4.dp))
              Text(if (isFingerprintAssigned) "Re-assign" else "Assign Fingerprint", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }

            if (isFingerprintAssigned) {
              OutlinedButton(
                onClick = {
                  onDismiss()
                  onLockApp()
                },
                shape = RoundedCornerShape(9.dp),
                modifier = Modifier
                  .weight(1f)
                  .testTag("settings_lock_now_btn")
              ) {
                Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp), tint = colors.ink)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Lock App Now", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = colors.ink)
              }
            } else {
              OutlinedButton(
                onClick = { showPinDialog = true },
                shape = RoundedCornerShape(9.dp),
                modifier = Modifier
                  .weight(1f)
                  .testTag("settings_set_pin_btn")
              ) {
                Text(if (hasPin) "Change PIN" else "Set Backup PIN", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = colors.ink)
              }
            }
          }

          if (isFingerprintAssigned) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text(
                text = if (hasPin) "Backup PIN: Configured" else "Backup PIN: Not set",
                fontSize = 11.5.sp,
                color = colors.muted
              )
              Row {
                Text(
                  text = "Configure PIN",
                  fontSize = 11.5.sp,
                  fontWeight = FontWeight.SemiBold,
                  color = colors.head,
                  modifier = Modifier
                    .clickable { showPinDialog = true }
                    .padding(4.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                  text = "Remove Lock",
                  fontSize = 11.5.sp,
                  fontWeight = FontWeight.SemiBold,
                  color = colors.expense,
                  modifier = Modifier
                    .clickable {
                      BiometricAuthManager.unassignFingerprint(context)
                      isFingerprintAssigned = false
                      isFingerprintEnabled = false
                      infoMsg = "Fingerprint lock removed."
                    }
                    .padding(4.dp)
                )
              }
            }
          }
        }
      }

      // Home Grid Range
      Text("Home Grid Range", fontWeight = FontWeight.Bold, color = colors.ink, fontSize = 15.sp)
      Text(
        "Columns shown on Home between start and end month.",
        color = colors.muted,
        fontSize = 13.sp,
        modifier = Modifier.padding(bottom = 8.dp)
      )

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

      Button(
        onClick = {
          val diff = ScheduleEngine.mIdx(endMonth) - ScheduleEngine.mIdx(startMonth)
          if (diff < 0) {
            errorMsg = "Start month cannot be after end month."
            return@Button
          }
          if (diff > 47) {
            errorMsg = "Pick a range of up to 4 years."
            return@Button
          }
          errorMsg = null
          onUpdateRange(startMonth, endMonth)
        },
        colors = ButtonDefaults.buttonColors(
          containerColor = colors.head,
          contentColor = colors.headInk
        ),
        modifier = Modifier
          .fillMaxWidth()
          .padding(top = 10.dp)
          .testTag("save_range_btn")
      ) {
        Text("Save Grid Range")
      }

      Spacer(modifier = Modifier.height(20.dp))

      // Appearance / Theme
      Text("Appearance", fontWeight = FontWeight.Bold, color = colors.ink, fontSize = 15.sp)
      Spacer(modifier = Modifier.height(8.dp))

      Row(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(12.dp))
          .background(colors.surface2)
          .padding(4.dp)
      ) {
        listOf("system" to "System", "light" to "Light", "dark" to "Dark").forEach { (mode, label) ->
          val isSelected = settings.themeMode == mode
          Box(
            modifier = Modifier
              .weight(1f)
              .clip(RoundedCornerShape(9.dp))
              .background(if (isSelected) colors.surface else Color.Transparent)
              .clickable { onUpdateTheme(mode) }
              .padding(vertical = 10.dp),
            contentAlignment = Alignment.Center
          ) {
            Text(
              text = label,
              fontWeight = FontWeight.SemiBold,
              color = if (isSelected) colors.ink else colors.muted
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(22.dp))

      // Local File Backup & Restore
      Text("Manual File Backup (JSON)", fontWeight = FontWeight.Bold, color = colors.ink, fontSize = 15.sp)
      Text(
        "Export or import money-map-backup.json locally or share with other apps.",
        color = colors.muted,
        fontSize = 13.sp,
        modifier = Modifier.padding(bottom = 10.dp)
      )

      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        // Export JSON
        OutlinedButton(
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
        ) {
          Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text("Share JSON", color = colors.ink)
        }

        // Import JSON
        OutlinedButton(
          onClick = { filePickerLauncher.launch("*/*") },
          modifier = Modifier
            .weight(1f)
            .testTag("import_json_btn")
        ) {
          Icon(Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text("Import JSON", color = colors.ink)
        }
      }

      Spacer(modifier = Modifier.height(22.dp))

      // Reset to original data
      Text("Reset Data", fontWeight = FontWeight.Bold, color = colors.ink, fontSize = 15.sp)
      Spacer(modifier = Modifier.height(6.dp))

      Button(
        onClick = {
          if (resetArmed) {
            onResetDefault()
          } else {
            resetArmed = true
          }
        },
        colors = ButtonDefaults.buttonColors(
          containerColor = if (resetArmed) colors.expense else Color.Transparent,
          contentColor = if (resetArmed) Color.White else colors.expense
        ),
        border = if (!resetArmed) ButtonDefaults.outlinedButtonBorder else null,
        modifier = Modifier
          .fillMaxWidth()
          .testTag("reset_data_btn")
      ) {
        Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(if (resetArmed) "Tap again to reset all data" else "Reset to Patrick's initial budget")
      }

      infoMsg?.let { msg ->
        Text(
          text = msg,
          color = colors.income,
          fontSize = 13.5.sp,
          fontWeight = FontWeight.Medium,
          modifier = Modifier.padding(top = 10.dp)
        )
      }

      errorMsg?.let { err ->
        Text(
          text = err,
          color = colors.expense,
          fontSize = 13.5.sp,
          fontWeight = FontWeight.Medium,
          modifier = Modifier.padding(top = 10.dp)
        )
      }
    }
  }

  if (showPinDialog) {
    PinEntryDialog(
      title = if (hasPin) "Change Backup PIN" else "Set Backup PIN",
      subtitle = "Enter a 4-digit PIN for backup access",
      onDismiss = { showPinDialog = false },
      onSubmit = { enteredPin ->
        if (enteredPin.length == 4) {
          BiometricAuthManager.setBackupPin(context, enteredPin)
          hasPin = true
          showPinDialog = false
          infoMsg = "Backup PIN successfully configured!"
          true
        } else {
          false
        }
      }
    )
  }
}
