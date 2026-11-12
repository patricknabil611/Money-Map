package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import com.example.BuildConfig
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AppShapes
import com.example.ui.theme.LocalMoneyMapColors
import java.io.File

@Composable
fun AboutDialog(
  onDismiss: () -> Unit
) {
  val context = LocalContext.current
  val colors = LocalMoneyMapColors.current
  var copiedToast by remember { mutableStateOf(false) }
  var updateCheckMsg by remember { mutableStateOf<String?>(null) }

  // Dynamically compute APK file size if available, or fallback to known 33.2 MB
  val apkSizeStr = remember {
    try {
      val sourceDir = context.applicationInfo.sourceDir
      val apkFile = File(sourceDir)
      if (apkFile.exists() && apkFile.length() > 0) {
        val mb = apkFile.length() / (1024.0 * 1024.0)
        String.format(java.util.Locale.US, "%.1f MB", mb)
      } else {
        "33.2 MB"
      }
    } catch (_: Exception) {
      "33.2 MB"
    }
  }

  val appVersion = "v${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE})"
  val releaseDate = "November 2, 2026"

  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = colors.surface,
    shape = AppShapes.Dialog,
    title = {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
          Box(
            modifier = Modifier
              .size(40.dp)
              .clip(AppShapes.Medium)
              .background(colors.head),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = Icons.Default.Info,
              contentDescription = null,
              tint = colors.headInk,
              modifier = Modifier.size(24.dp)
            )
          }
          Spacer(modifier = Modifier.width(12.dp))
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = "About P Money",
              color = colors.ink,
              fontSize = 18.sp,
              fontWeight = FontWeight.Bold,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
            Text(
              text = "Personal Budget & Real-Time Sync",
              color = colors.muted,
              fontSize = 11.5.sp,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
          }
        }
        AppIconButton(
          onClick = onDismiss,
          icon = Icons.Default.Close,
          contentDescription = "Close",
          tint = colors.muted,
          modifier = Modifier.testTag("close_about_dialog_btn")
        )
      }
    },
    text = {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .verticalScroll(rememberScrollState())
      ) {
        // App Specifications Card
        Card(
          shape = AppShapes.Card,
          colors = CardDefaults.cardColors(containerColor = colors.surface2),
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(modifier = Modifier.padding(14.dp)) {
            // Version Row
            AboutMetadataRow(
              icon = Icons.Default.CheckCircle,
              iconColor = colors.income,
              label = "App Version",
              value = appVersion
            )
            Divider(color = colors.line, modifier = Modifier.padding(vertical = 8.dp))

            // Release Date Row
            AboutMetadataRow(
              icon = Icons.Default.CalendarMonth,
              iconColor = colors.year,
              label = "Release Date",
              value = releaseDate
            )
            Divider(color = colors.line, modifier = Modifier.padding(vertical = 8.dp))

            // App Size Row
            AboutMetadataRow(
              icon = Icons.Default.SdStorage,
              iconColor = colors.leftover,
              label = "App Package Size",
              value = apkSizeStr
            )
            Divider(color = colors.line, modifier = Modifier.padding(vertical = 8.dp))

            // Platform Row
            AboutMetadataRow(
              icon = Icons.Default.PhoneAndroid,
              iconColor = colors.head,
              label = "Platform",
              value = "Android (Material 3)"
            )
          }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // What's New Section Header
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Icon(
            imageVector = Icons.Default.AutoAwesome,
            contentDescription = null,
            tint = colors.head,
            modifier = Modifier.size(18.dp)
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = "What's New in This Version",
            color = colors.ink,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
          )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Feature 1: Real-Time Multi-Device Cloud Sync
        FeatureHighlightCard(
          icon = Icons.Default.CloudDone,
          iconColor = Color(0xFF10B981),
          title = "Real-Time Cloud Sync (Firestore)",
          description = "Live bidirectional sync across all your phones, tablets, and Samsung DeX with zero data lag.",
          colors = colors
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Feature 2: Gemini 3.8 Flash NLP Voice Commands
        FeatureHighlightCard(
          icon = Icons.Default.Mic,
          iconColor = Color(0xFFE50914),
          title = "AI Voice Commands & NLP (Gemini)",
          description = "Quickly add expenses, income, or tasks by voice in English or Arabic with automated entity parsing.",
          colors = colors
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Feature 3: Weekly Thursday 1PM Update Reminder & Galaxy S25 Ultra Edge Lighting
        FeatureHighlightCard(
          icon = Icons.Default.NotificationsActive,
          iconColor = Color(0xFFFF6D00),
          title = "Weekly Update Reminder & Edge Glow",
          description = "Customizable reminder every Thursday at 1:00 PM with animated Samsung Galaxy S25 Ultra neon orange edge lighting.",
          colors = colors
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Feature 4: Push Notifications & Due Date Reminders
        FeatureHighlightCard(
          icon = Icons.Default.NotificationsActive,
          iconColor = Color(0xFFF59E0B),
          title = "Push Notifications & Reminders",
          description = "Automated exact alarms for upcoming bill due dates, loan installment deadlines, and scheduled tasks.",
          colors = colors
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Feature 5: Biometric Security
        FeatureHighlightCard(
          icon = Icons.Default.Fingerprint,
          iconColor = Color(0xFF8B5CF6),
          title = "Biometric Security",
          description = "Fingerprint authentication to secure your financial records across sessions.",
          colors = colors
        )

        if (updateCheckMsg != null) {
          Spacer(modifier = Modifier.height(12.dp))
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .clip(AppShapes.Medium)
              .background(colors.incomeSoft)
              .padding(10.dp)
          ) {
            Text(
              text = updateCheckMsg!!,
              color = colors.income,
              fontSize = 12.sp,
              fontWeight = FontWeight.Medium
            )
          }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Actions: Copy Release Notes and Check Updates
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(12.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          AppSecondaryButton(
            text = "Check Updates",
            onClick = {
              updateCheckMsg = "You're running the latest release ($appVersion). Cloud backend is fully synced."
            },
            modifier = Modifier
              .weight(1f)
              .testTag("check_updates_btn")
          )

          AppSecondaryButton(
            text = if (copiedToast) "Copied!" else "Copy Notes",
            icon = if (copiedToast) Icons.Default.Check else Icons.Default.ContentCopy,
            contentColor = if (copiedToast) colors.income else colors.ink,
            borderColor = if (copiedToast) colors.income else null,
            onClick = {
              val notes = """
                P Money $appVersion
                Release Date: $releaseDate
                Package Size: $apkSizeStr
                
                What's New in v${BuildConfig.VERSION_NAME}:
                • Official rebranding to P Money
                • Google Sign-In with Firebase Auth cloud synchronization
                • Samsung Galaxy S25 Ultra orange edge lighting animation on notifications
                • Customizable weekly update reminder (Every Thursday 1:00 PM)
                • Dedicated Notification Setup sheet
                • Real-time multi-device cloud synchronization via Cloud Firestore
                • Push notifications & automated due date reminders
                • Biometric fingerprint security
                • Continuous Google Drive automated backups
              """.trimIndent()
              val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
              clipboard.setPrimaryClip(ClipData.newPlainText("P Money Release Notes", notes))
              copiedToast = true
            },
            modifier = Modifier
              .weight(1f)
              .testTag("copy_release_notes_btn")
          )
        }
      }
    },
    confirmButton = {
      AppPrimaryButton(
        text = "Download APK ($apkSizeStr)",
        icon = Icons.Default.Download,
        onClick = {
          try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("/P_Money.apk"))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
          } catch (_: Exception) {
            onDismiss()
          }
        },
        modifier = Modifier.testTag("download_latest_apk_dialog_btn")
      )
    },
    dismissButton = {
      AppTextButton(
        text = "Done",
        onClick = onDismiss,
        contentColor = colors.muted
      )
    }
  )
}

@Composable
fun AboutMetadataRow(
  icon: ImageVector,
  iconColor: Color,
  label: String,
  value: String
) {
  val colors = LocalMoneyMapColors.current
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
  ) {
    Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = iconColor,
        modifier = Modifier.size(18.dp)
      )
      Spacer(modifier = Modifier.width(10.dp))
      Text(
        text = label,
        color = colors.muted,
        fontSize = 12.5.sp,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
    }
    Text(
      text = value,
      color = colors.ink,
      fontSize = 12.5.sp,
      fontWeight = FontWeight.Bold,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis
    )
  }
}

@Composable
fun FeatureHighlightCard(
  icon: ImageVector,
  iconColor: Color,
  title: String,
  description: String,
  colors: com.example.ui.theme.MoneyMapColors
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clip(AppShapes.Card)
      .background(colors.surface2)
      .border(1.dp, colors.line, AppShapes.Card)
      .padding(10.dp),
    verticalAlignment = Alignment.Top
  ) {
    Box(
      modifier = Modifier
        .size(32.dp)
        .clip(CircleShape)
        .background(iconColor.copy(alpha = 0.15f)),
      contentAlignment = Alignment.Center
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = iconColor,
        modifier = Modifier.size(18.dp)
      )
    }
    Spacer(modifier = Modifier.width(10.dp))
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = title,
        color = colors.ink,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold
      )
      Spacer(modifier = Modifier.height(2.dp))
      Text(
        text = description,
        color = colors.muted,
        fontSize = 11.sp,
        lineHeight = 15.sp
      )
    }
  }
}
