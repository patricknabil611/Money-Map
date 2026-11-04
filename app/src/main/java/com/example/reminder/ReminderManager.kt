package com.example.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R

object ReminderManager {

  const val CHANNEL_ID = "p_money_reminders_channel"
  const val CHANNEL_NAME = "P Money Reminders & Updates"
  const val CHANNEL_DESC = "Notifications for weekly app updates, payment due dates, and tasks"

  fun initChannel(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
      val existing = notificationManager.getNotificationChannel(CHANNEL_ID)
      if (existing == null) {
        val channel = NotificationChannel(
          CHANNEL_ID,
          CHANNEL_NAME,
          NotificationManager.IMPORTANCE_HIGH
        ).apply {
          description = CHANNEL_DESC
          enableLights(true)
          lightColor = Color.rgb(255, 109, 0) // Samsung Orange
          enableVibration(true)
        }
        notificationManager.createNotificationChannel(channel)
      }
    }
  }

  fun showNotification(
    context: Context,
    notificationId: Int,
    title: String,
    message: String,
    isTask: Boolean = false,
    openSection: String? = null,
    triggerEdgeLighting: Boolean = true
  ) {
    initChannel(context)

    val intent = Intent(context, MainActivity::class.java).apply {
      action = if (openSection == UpdateReminderManager.SECTION_NOTIFICATION_SETUP) {
        UpdateReminderManager.ACTION_OPEN_UPDATE_CONFIG
      } else {
        Intent.ACTION_VIEW
      }
      flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
      if (!openSection.isNullOrEmpty()) {
        putExtra(UpdateReminderManager.EXTRA_OPEN_SECTION, openSection)
      }
      putExtra(UpdateReminderManager.EXTRA_TRIGGER_EDGE_LIGHTING, triggerEdgeLighting)
    }

    val pendingIntent = PendingIntent.getActivity(
      context,
      notificationId,
      intent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val builder = NotificationCompat.Builder(context, CHANNEL_ID)
      .setSmallIcon(R.drawable.ic_notification)
      .setColor(Color.rgb(255, 109, 0)) // Vibrant Samsung Orange accent
      .setContentTitle(title)
      .setContentText(message)
      .setStyle(NotificationCompat.BigTextStyle().bigText(message))
      .setPriority(NotificationCompat.PRIORITY_HIGH)
      .setAutoCancel(true)
      .setContentIntent(pendingIntent)

    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    notificationManager.notify(notificationId, builder.build())
  }
}
