package com.example.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ReminderNotificationReceiver : BroadcastReceiver() {

  override fun onReceive(context: Context, intent: Intent) {
    val action = intent.action
    val isBoot = Intent.ACTION_BOOT_COMPLETED == action || Intent.ACTION_MY_PACKAGE_REPLACED == action
    val isWeeklyAlarm = UpdateReminderManager.ACTION_WEEKLY_UPDATE_ALARM == action

    if (isBoot) {
      // Re-schedule weekly reminder on device restart or APK upgrade
      UpdateReminderManager.scheduleWeeklyUpdateReminder(context)
      return
    }

    val title = intent.getStringExtra("EXTRA_TITLE") ?: "P Money Update Reminder"
    val message = intent.getStringExtra("EXTRA_MESSAGE")
      ?: "Weekly update reminder! Tap to check for updates or download the latest P Money APK."
    val notificationId = intent.getIntExtra("EXTRA_ID", 9002)
    val openSection = intent.getStringExtra(UpdateReminderManager.EXTRA_OPEN_SECTION)
      ?: UpdateReminderManager.SECTION_NOTIFICATION_SETUP

    ReminderManager.showNotification(
      context = context,
      notificationId = notificationId,
      title = title,
      message = message,
      openSection = openSection,
      triggerEdgeLighting = true
    )

    // If it was a weekly alarm, schedule the next week's occurrence
    if (isWeeklyAlarm) {
      UpdateReminderManager.scheduleWeeklyUpdateReminder(context)
    }
  }
}
